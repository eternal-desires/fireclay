#!/usr/bin/env python3
"""Generates Fireclay's data pack JSON under src/main/resources/data/.

Run from the mod root (fireclay/): python3 tools/gen_data.py

Writes firing, crafting, cooking and stonecutting recipes, block loot tables, item, block and
entity-type tags, and the advancement tab. Every structure is copied from the vanilla 26.3 data
(e.g. loot pools use "condition"/"modifier", shaped keys are plain ingredient strings) and every
fireclay id is cross-checked against the Java registries before anything is written.

Deterministic: the output depends only on this file and the Java sources it cross-checks.
"""

import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "data"
JAVA = ROOT / "src" / "main" / "java" / "io" / "github" / "eternaldesires" / "fireclay"
NS = "fireclay"

# Generated subtrees. They are wiped and rewritten on every run so stale files never linger.
OWNED_DIRS = [
    DATA / NS / "recipe",
    DATA / NS / "loot_table",
    DATA / NS / "tags",
    DATA / NS / "advancement",
    DATA / "minecraft" / "tags",
]

written: list[Path] = []


def fc(path: str) -> str:
    return f"{NS}:{path}"


def mc(path: str) -> str:
    return f"minecraft:{path}"


def write(rel: str, obj) -> None:
    path = DATA / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    written.append(path)


# ---------------------------------------------------------------------------
# Registry cross-check: ids registered in Java
# ---------------------------------------------------------------------------

def java_ids(file: str) -> set[str]:
    text = (JAVA / "registry" / file).read_text(encoding="utf-8")
    return set(re.findall(r'register\(\s*"([a-z0-9_/]+)"', text)) | set(re.findall(r'simple\(\s*"([a-z0-9_/]+)"', text))


BLOCKS = java_ids("FireclayBlocks.java")
# Every block has a block item with the same id (FireclayItems#block).
ITEMS = java_ids("FireclayItems.java") | BLOCKS

if not BLOCKS or not ITEMS - BLOCKS:
    sys.exit("gen_data: could not read block/item ids from the Java registries")


def check_ref(ref: str, where: str, registry: set[str]) -> None:
    """Fails on any fireclay:<id> (not a tag) that Java does not register."""
    if ref.startswith("#"):
        return
    ns, _, path = ref.partition(":")
    if ns == NS and path not in registry:
        sys.exit(f"gen_data: {where} references unknown {ref}")


def item(ref: str, where: str) -> str:
    check_ref(ref, where, ITEMS)
    return ref


# ---------------------------------------------------------------------------
# Recipes
# ---------------------------------------------------------------------------

def result(ref: str, count: int = 1, where: str = "recipe") -> dict:
    out = {"id": item(ref, where)}
    if count != 1:
        out = {"count": count, "id": out["id"]}
    return out


def ingredient(value, where: str):
    """A 26.3 ingredient: an id, a #tag, or a list of ids."""
    if isinstance(value, list):
        return [item(v, where) for v in value]
    return item(value, where)


def shaped(name: str, pattern: list[str], key: dict, out: str, count: int = 1, category: str | None = None) -> None:
    where = f"recipe {name}"
    recipe = {"type": mc("crafting_shaped")}
    if category:
        recipe["category"] = category
    recipe["key"] = {k: ingredient(v, where) for k, v in sorted(key.items())}
    recipe["pattern"] = pattern
    recipe["result"] = result(out, count, where)
    used = {c for row in pattern for c in row if c != " "}
    if used != set(key):
        sys.exit(f"gen_data: {where} pattern/key mismatch {used} vs {set(key)}")
    write(f"{NS}/recipe/{name}.json", recipe)


def shapeless(name: str, ingredients: list, out: str, count: int = 1, category: str | None = None) -> None:
    where = f"recipe {name}"
    recipe = {"type": mc("crafting_shapeless")}
    if category:
        recipe["category"] = category
    recipe["ingredients"] = [ingredient(v, where) for v in ingredients]
    recipe["result"] = result(out, count, where)
    if not 1 <= len(ingredients) <= 9:
        sys.exit(f"gen_data: {where} has {len(ingredients)} ingredients")
    write(f"{NS}/recipe/{name}.json", recipe)


def cooking(name: str, kind: str, ing: str, out: str, time: int, xp: float) -> None:
    where = f"recipe {name}"
    write(f"{NS}/recipe/{name}.json", {
        "type": mc(kind),
        "cookingtime": time,
        "experience": xp,
        "ingredient": ingredient(ing, where),
        "result": result(out, 1, where),
    })


def stonecutting(name: str, ing: str, out: str, count: int = 1) -> None:
    where = f"recipe {name}"
    write(f"{NS}/recipe/{name}.json", {
        "type": mc("stonecutting"),
        "ingredient": ingredient(ing, where),
        "result": result(out, count, where),
    })


# Firing recipes (design §2.3). Shape = FiringRecipe.CODEC: greenware, optional glaze,
# min_heat, max_heat, time (positive), result (ItemStackTemplate).
FIRING = [
    # name, greenware, glaze, result, count, min, max, time
    ("fired_tiles", "unfired_tile", None, "fired_tiles", 4, 300, 600, 200),
    ("storage_jar", "unfired_jar", None, "storage_jar", 1, 350, 600, 300),
    ("lumen_tiles", "unfired_tile", "glowink_slip", "lumen_tiles", 4, 480, 640, 300),
    ("memory_jar", "unfired_jar", "lapis_slip", "memory_jar", 1, 540, 680, 400),
    ("sentry_chime", "unfired_bell", "amethyst_slip", "sentry_chime", 1, 600, 720, 400),
    ("vigil_urn", "unfired_urn", "glowink_slip", "vigil_urn", 1, 620, 740, 500),
    ("ember_tile", "unfired_tile", "blaze_slip", "ember_tile", 2, 760, 880, 400),
    ("reliquary_urn", "unfired_urn", "echo_slip", "reliquary_urn", 1, 820, 900, 600),
    ("sentinel_effigy", "unfired_effigy", "blaze_slip", "sentinel_effigy", 1, 860, 940, 800),
]


def gen_recipes() -> None:
    for name, green, glaze, out, count, lo, hi, time in FIRING:
        where = f"firing recipe {name}"
        recipe = {"type": fc("firing"), "greenware": item(fc(green), where)}
        if glaze:
            recipe["glaze"] = item(fc(glaze), where)
        recipe["min_heat"] = lo
        recipe["max_heat"] = hi
        recipe["time"] = time
        recipe["result"] = result(fc(out), count, where)
        if not (20 <= lo < hi <= 1000 and time > 0):
            sys.exit(f"gen_data: bad window in {where}")
        write(f"{NS}/recipe/firing/{name}.json", recipe)

    planks = "#" + mc("planks")
    # Workshop
    shaped("potters_wheel", [" S ", "PTP", "PPP"],
           {"S": mc("smooth_stone_slab"), "T": mc("stick"), "P": planks}, fc("potters_wheel"))
    shaped("kiln", ["BIB", "BFB", "BIB"],
           {"B": mc("brick"), "I": mc("iron_bars"), "F": mc("furnace")}, fc("kiln"))
    shaped("bellows", ["PPP", "LLL", "PNP"],
           {"P": planks, "L": mc("leather"), "N": mc("iron_ingot")}, fc("bellows"))
    shaped("pyrometer", [" G ", "CRC", " C "],
           {"G": mc("glass_pane"), "C": mc("copper_ingot"), "R": mc("redstone")}, fc("pyrometer"))

    # Slips: clay + water (the bucket comes back as its crafting remainder) + mineral.
    for slip, mineral, minerals, count in [
        ("lapis_slip", "lapis_lazuli", 2, 4),
        ("glowink_slip", "glow_ink_sac", 2, 4),
        ("amethyst_slip", "amethyst_shard", 2, 4),
        ("blaze_slip", "blaze_powder", 2, 4),
        ("echo_slip", "echo_shard", 1, 2),
    ]:
        shapeless(slip, [mc("clay_ball"), mc("water_bucket")] + [mc(mineral)] * minerals, fc(slip), count)

    # The fireclay chain
    shapeless("grog_from_kiln_shards", [fc("kiln_shard")] * 4, fc("grog"), 2)
    shapeless("grog_from_decorated_pot", [mc("decorated_pot")], fc("grog"), 2)
    shapeless("fireclay_ball", [mc("clay_ball"), mc("clay_ball"), fc("grog"), [mc("sand"), mc("red_sand")]],
              fc("fireclay_ball"), 3)
    cooking("fireclay_brick", "smelting", fc("fireclay_ball"), fc("fireclay_brick"), 200, 0.3)
    cooking("fireclay_brick_from_blasting", "blasting", fc("fireclay_ball"), fc("fireclay_brick"), 100, 0.3)
    shaped("kiln_lining", ["BBB", "BZB", "BBB"],
           {"B": fc("fireclay_brick"), "Z": mc("blaze_powder")}, fc("kiln_lining"))

    # Squashing greenware back into clay
    for piece, ball, count in [
        ("unfired_tile", mc("clay_ball"), 1),
        ("unfired_bell", mc("clay_ball"), 3),
        ("unfired_jar", mc("clay_ball"), 4),
        ("unfired_urn", mc("clay_ball"), 6),
        ("unfired_effigy", fc("fireclay_ball"), 8),
    ]:
        shapeless(f"{ball.split(':')[1]}_from_{piece}", [fc(piece)], ball, count)

    # Building blocks: stairs/slab (+ wall) by crafting and stonecutting, vanilla brick patterns.
    def family(base: str, stairs: str, slab: str, wall: str | None) -> None:
        b = fc(base)
        shaped(stairs, ["#  ", "## ", "###"], {"#": b}, fc(stairs), 4, "building")
        shaped(slab, ["###"], {"#": b}, fc(slab), 6, "building")
        stonecutting(f"{stairs}_from_{base}_stonecutting", b, fc(stairs))
        stonecutting(f"{slab}_from_{base}_stonecutting", b, fc(slab), 2)
        if wall:
            shaped(wall, ["###", "###"], {"#": b}, fc(wall), 6, "misc")
            stonecutting(f"{wall}_from_{base}_stonecutting", b, fc(wall))

    family("fired_tiles", "fired_tile_stairs", "fired_tile_slab", None)
    shaped("fireclay_bricks", ["##", "##"], {"#": fc("fireclay_brick")}, fc("fireclay_bricks"), 4, "building")
    family("fireclay_bricks", "fireclay_brick_stairs", "fireclay_brick_slab", "fireclay_brick_wall")


# ---------------------------------------------------------------------------
# Block loot tables
# ---------------------------------------------------------------------------

SLABS = {"fired_tile_slab", "fireclay_brick_slab"}
# Block entities whose contents ride on the dropped item (shulker-box style). Java getDrops copies the
# components (CeramicDrops.withBlockEntityComponents); the table must not lose the item to an explosion,
# or the contents would vanish with it.
KEEPS_CONTENTS = {"storage_jar", "memory_jar", "reliquary_urn"}


def self_drop(block: str, survives_explosion: bool) -> dict:
    pool = {}
    if survives_explosion:
        pool["condition"] = {"type": mc("survives_explosion")}
    pool["entries"] = [{"type": mc("item"), "name": fc(block)}]
    pool["rolls"] = 1
    return {"type": mc("block"), "pools": [pool], "random_sequence": fc(f"blocks/{block}")}


def slab_drop(block: str) -> dict:
    return {
        "type": mc("block"),
        "pools": [{
            "entries": [{
                "type": mc("item"),
                "modifier": [
                    {
                        "type": mc("set_count"),
                        "condition": {"type": mc("match_block"), "blocks": fc(block), "state": {"type": "double"}},
                        "count": 2,
                    },
                    {"type": mc("explosion_decay")},
                ],
                "name": fc(block),
            }],
            "rolls": 1,
        }],
        "random_sequence": fc(f"blocks/{block}"),
    }


def gen_loot() -> None:
    for block in sorted(BLOCKS):
        if block in SLABS:
            table = slab_drop(block)
        else:
            # The kiln adds its lining in Java getDrops; everything else is a plain self drop.
            table = self_drop(block, block not in KEEPS_CONTENTS)
        write(f"{NS}/loot_table/blocks/{block}.json", table)


# ---------------------------------------------------------------------------
# Tags
# ---------------------------------------------------------------------------

def tag(rel: str, values: list[str], registry: set[str]) -> None:
    for v in values:
        check_ref(v, f"tag {rel}", registry)
    write(rel, {"values": values})


GREENWARE = ["unfired_tile", "unfired_bell", "unfired_jar", "unfired_urn", "unfired_effigy"]
GLAZES = ["lapis_slip", "glowink_slip", "amethyst_slip", "blaze_slip", "echo_slip"]
FUEL_TIERS = {
    "kindling": [mc("stick"), "#" + mc("saplings"), "#" + mc("wooden_slabs")],
    "wood": ["#" + mc("logs_that_burn"), "#" + mc("planks")],
    "coal": [mc("coal"), mc("charcoal")],
    "coal_block": [mc("coal_block")],
    "lava": [mc("lava_bucket")],
    "blaze": [mc("blaze_rod")],
}
PICKAXE = ["kiln", "fired_tiles", "fired_tile_stairs", "fired_tile_slab", "lumen_tiles", "storage_jar", "memory_jar",
           "sentry_chime", "vigil_urn", "ember_tile", "reliquary_urn", "fireclay_bricks", "fireclay_brick_stairs",
           "fireclay_brick_slab", "fireclay_brick_wall"]
AXE = ["potters_wheel", "bellows"]
STAIRS = ["fired_tile_stairs", "fireclay_brick_stairs"]
SLAB_LIST = ["fired_tile_slab", "fireclay_brick_slab"]
WALLS = ["fireclay_brick_wall"]


def gen_tags() -> None:
    it = f"{NS}/tags/item"
    tag(f"{it}/greenware.json", [fc(i) for i in GREENWARE], ITEMS)
    tag(f"{it}/glazes.json", [fc(i) for i in GLAZES], ITEMS)
    for tier, values in FUEL_TIERS.items():
        tag(f"{it}/kiln_fuel/{tier}.json", values, ITEMS)
    tag(f"{it}/kiln_fuels.json", ["#" + fc(f"kiln_fuel/{t}") for t in FUEL_TIERS], ITEMS)
    tag(f"{it}/masterwork_eligible.json",
        [fc(i) for i in ["memory_jar", "sentry_chime", "vigil_urn", "ember_tile", "reliquary_urn", "sentinel_effigy"]],
        ITEMS)
    tag(f"{it}/storage_jar_blacklist.json", ["#" + mc("shulker_boxes"), fc("storage_jar")], ITEMS)

    et = f"{NS}/tags/entity_type"
    tag(f"{et}/vigil_immune.json", [mc("ender_dragon"), mc("wither"), mc("warden"), mc("elder_guardian")], set())
    tag(f"{et}/sentinel_ignores.json", [mc("creeper")], set())

    if set(PICKAXE) | set(AXE) != BLOCKS:
        sys.exit(f"gen_data: mineable tags miss {BLOCKS - set(PICKAXE) - set(AXE)}")
    bt = "minecraft/tags/block"
    tag(f"{bt}/mineable/pickaxe.json", [fc(b) for b in PICKAXE], BLOCKS)
    tag(f"{bt}/mineable/axe.json", [fc(b) for b in AXE], BLOCKS)
    for name, values in [("stairs", STAIRS), ("slabs", SLAB_LIST), ("walls", WALLS)]:
        tag(f"{bt}/{name}.json", [fc(b) for b in values], BLOCKS)
        tag(f"minecraft/tags/item/{name}.json", [fc(b) for b in values], ITEMS)


# ---------------------------------------------------------------------------
# Advancements (design §3.14). Criterion conditions follow PotterActionTrigger.TriggerInstance.CODEC:
# {"action": str, "item": ItemPredicate (optional), "masterwork": bool (optional)}.
# ---------------------------------------------------------------------------

ACTIONS = {"threw", "fired", "cracked", "pumped", "masterwork", "thermostat", "lined", "white_heat", "awakened",
           "pyrometer_read", "ember_baked", "reliquary_saved", "hundred_fires"}

# id, parent, icon, frame, action, item filter, masterwork filter, hidden, xp reward, English title, description
ADVANCEMENTS = [
    ("root", None, "potters_wheel", "task", "threw", None, None, False, 0,
     "Wet Hands", "Throw a piece on the Potter's Wheel"),
    ("trial_by_fire", "root", "kiln", "task", "fired", None, None, False, 0,
     "Trial by Fire", "Fire anything in a Kiln. Coal settles near 520°"),
    ("every_pot_has_its_crack", "trial_by_fire", "kiln_shard", "task", "cracked", None, None, False, 0,
     "Every Pot Has Its Crack", "Overheat a firing. Keep the shards"),
    ("hot_air", "trial_by_fire", "bellows", "task", "pumped", None, None, False, 0,
     "Hot Air", "Pump a Bellows into a burning Kiln"),
    ("sweet_spot", "hot_air", "memory_jar*", "goal", "masterwork", None, None, False, 0,
     "Sweet Spot", "Fire a Masterwork"),
    ("thermostat", "sweet_spot", "sentry_chime*", "challenge", "thermostat", None, None, False, 100,
     "Thermostat", "Fire a Masterwork without touching the bellows by hand"),
    ("lapis_remembers", "trial_by_fire", "memory_jar", "task", "fired", "memory_jar", None, False, 0,
     "Lapis Remembers", "Fire a Memory Jar"),
    ("amethyst_listens", "trial_by_fire", "sentry_chime", "task", "fired", "sentry_chime", None, False, 0,
     "Amethyst Listens", "Fire a Sentry Chime"),
    ("glow_keeps_vigil", "trial_by_fire", "vigil_urn", "task", "fired", "vigil_urn", None, False, 0,
     "Glow Keeps Vigil", "Fire a Vigil Urn"),
    ("lined_with_failure", "every_pot_has_its_crack", "kiln_lining", "goal", "lined", None, None, False, 0,
     "Lined with Failure", "Line a Kiln with bricks made from your cracks"),
    ("white_heat", "lined_with_failure", "fireclay_bricks", "task", "white_heat", None, None, False, 0,
     "White Heat", "Reach 950° in a lined Kiln"),
    ("terracotta_army", "white_heat", "sentinel_effigy", "goal", "awakened", None, None, False, 0,
     "Terracotta Army", "Awaken a Terracotta Sentinel"),
    ("masterpiece_guardian", "terracotta_army", "sentinel_effigy*", "challenge", "awakened", None, True, True, 200,
     "Masterpiece Guardian", "Awaken a Masterwork Sentinel"),
    ("blaze_holds_heat", "lined_with_failure", "ember_tile", "task", "ember_baked", None, None, False, 0,
     "Blaze Holds Heat", "Bake something on an Ember Tile"),
    ("echo_holds_a_soul", "lined_with_failure", "reliquary_urn", "task", "fired", "reliquary_urn", None, False, 0,
     "Echo Holds a Soul", "Fire a Reliquary Urn"),
    ("memento_mori", "echo_holds_a_soul", "echo_slip", "goal", "reliquary_saved", None, None, False, 0,
     "Memento Mori", "Be saved by your Reliquary Urn"),
    ("read_the_fire", "hot_air", "pyrometer", "task", "pyrometer_read", None, None, False, 0,
     "Read the Fire", "Read a Kiln with a Pyrometer"),
    ("hundred_fires", "sweet_spot", "fired_tiles", "challenge", "hundred_fires", None, None, False, 100,
     "A Hundred Fires", "Complete 100 firings"),
]


def icon(spec: str) -> dict:
    """An ItemStackTemplate; a trailing * adds the enchantment glint (Masterworks shine)."""
    glint = spec.endswith("*")
    out = {"id": item(fc(spec.rstrip("*")), f"advancement icon {spec}")}
    if glint:
        out["components"] = {mc("enchantment_glint_override"): True}
    return out


def gen_advancements() -> None:
    ids = {a[0] for a in ADVANCEMENTS}
    for adv_id, parent, ico, frame, action, item_filter, masterwork, hidden, xp, _title, _desc in ADVANCEMENTS:
        if action not in ACTIONS:
            sys.exit(f"gen_data: advancement {adv_id} uses unknown action {action}")
        if parent and parent not in ids:
            sys.exit(f"gen_data: advancement {adv_id} has unknown parent {parent}")
        conditions = {"action": action}
        if item_filter:
            conditions["item"] = {"items": item(fc(item_filter), f"advancement {adv_id}")}
        if masterwork is not None:
            conditions["masterwork"] = masterwork
        criterion = action if not item_filter else f"{action}_{item_filter}"

        adv = {}
        if parent:
            adv["parent"] = fc(parent)
        adv["criteria"] = {criterion: {"conditions": conditions, "trigger": fc("potter_action")}}
        display = {}
        if parent is None:
            display["announce_to_chat"] = False
            display["background"] = mc("block/terracotta")
        display["description"] = {"translate": f"advancements.{NS}.{adv_id}.description"}
        if frame != "task":
            display["frame"] = frame
        if hidden:
            display["hidden"] = True
        display["icon"] = icon(ico)
        if parent is None:
            display["show_toast"] = False
        display["title"] = {"translate": f"advancements.{NS}.{adv_id}.title"}
        adv["display"] = display
        adv["requirements"] = [[criterion]]
        if xp:
            adv["rewards"] = {"experience": xp}
        write(f"{NS}/advancement/{adv_id}.json", adv)


# Used by gen_lang.py so advancement English lives next to the advancement definitions.
def advancement_lang() -> dict[str, str]:
    out = {}
    for adv_id, *_rest, title, desc in ADVANCEMENTS:
        out[f"advancements.{NS}.{adv_id}.title"] = title
        out[f"advancements.{NS}.{adv_id}.description"] = desc
    return out


# ---------------------------------------------------------------------------

def main() -> None:
    for d in OWNED_DIRS:
        if d.exists():
            shutil.rmtree(d)
    gen_recipes()
    gen_loot()
    gen_tags()
    gen_advancements()

    # Every file must round-trip through the JSON parser.
    for path in written:
        json.loads(path.read_text(encoding="utf-8"))

    def count(sub: str) -> int:
        return sum(1 for p in written if sub in p.relative_to(DATA).as_posix())

    print(f"gen_data: wrote {len(written)} files "
          f"(firing {count('recipe/firing/')}, recipes {count('/recipe/') - count('recipe/firing/')}, "
          f"loot {count('loot_table/')}, tags {count('/tags/')}, advancements {count('advancement/')})")


if __name__ == "__main__":
    main()
