#!/usr/bin/env python3
"""Generates assets/fireclay/lang/en_us.json.

Run from the mod root (fireclay/): python3 tools/gen_lang.py

Besides writing the file it checks that every translation key the mod can ask for is defined:
string literals in all Java sources (main + client + gametest), descriptionId-derived names of every
registered block, item and entity, greenware firing notes, game rules, sound subtitles and the
advancements produced by gen_data.py. A missing key fails the run. It also checks that each Primer
page fits a vanilla book page (114 px wide, 14 lines) using the default font's glyph advances.
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_data  # noqa: E402  (advancement English and the registry id readers)

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
JAVA = gen_data.JAVA
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "fireclay"
OUT = ASSETS / "lang" / "en_us.json"
NS = "fireclay"

# ---------------------------------------------------------------------------
# Names
# ---------------------------------------------------------------------------

BLOCK_NAMES = {
    "potters_wheel": "Potter's Wheel",
    "kiln": "Kiln",
    "bellows": "Bellows",
    "fired_tiles": "Fired Tiles",
    "fired_tile_stairs": "Fired Tile Stairs",
    "fired_tile_slab": "Fired Tile Slab",
    "lumen_tiles": "Lumen Tiles",
    "storage_jar": "Storage Jar",
    "memory_jar": "Memory Jar",
    "sentry_chime": "Sentry Chime",
    "vigil_urn": "Vigil Urn",
    "ember_tile": "Ember Tile",
    "reliquary_urn": "Reliquary Urn",
    "fireclay_bricks": "Fireclay Bricks",
    "fireclay_brick_stairs": "Fireclay Brick Stairs",
    "fireclay_brick_slab": "Fireclay Brick Slab",
    "fireclay_brick_wall": "Fireclay Brick Wall",
}

ITEM_NAMES = {
    "unfired_tile": "Unfired Tile",
    "unfired_bell": "Unfired Bell",
    "unfired_jar": "Unfired Jar",
    "unfired_urn": "Unfired Urn",
    "unfired_effigy": "Unfired Effigy",
    "lapis_slip": "Lapis Slip",
    "glowink_slip": "Glow Ink Slip",
    "amethyst_slip": "Amethyst Slip",
    "blaze_slip": "Blaze Slip",
    "echo_slip": "Echo Slip",
    "kiln_shard": "Kiln Shard",
    "grog": "Grog",
    "fireclay_ball": "Fireclay Ball",
    "fireclay_brick": "Fireclay Brick",
    "kiln_lining": "Kiln Lining",
    "sentinel_effigy": "Sentinel Effigy",
    "pyrometer": "Pyrometer",
}

ENTITY_NAMES = {
    "terracotta_sentinel": "Terracotta Sentinel",
}

GREENWARE_NOTES = {
    "unfired_tile": "Firing notes: bare 300–600°. Glow ink 480–640°: lumen. Blaze 760–880°: ember (lined).",
    "unfired_bell": "Firing notes: amethyst 600–720°.",
    "unfired_jar": "Firing notes: bare 350–600°: storage. Lapis 540–680°: memory.",
    "unfired_urn": "Firing notes: glow ink 620–740°: vigil. Echo 820–900°: reliquary (lined).",
    "unfired_effigy": "Firing notes: blaze 860–940°, 40 s, lined. Fire it well.",
}

GAME_RULES = {
    "kiln_crack_grace": ("Kiln crack grace",
                         "Ticks a firing may stay above its heat window before the piece cracks"),
    "reliquary_urns": ("Reliquary Urns",
                       "Whether a bound Reliquary Urn catches its owner's belongings on death"),
    "max_sentinels_per_player": ("Max Sentinels per player",
                                 "How many Terracotta Sentinels one player may keep awake"),
}

SUBTITLES = {
    "block.potters_wheel.throw": "Clay slaps on the wheel",
    "block.bellows.pump": "Bellows whoosh",
    "block.kiln.roar": "Kiln roars",
    "block.kiln.overheat": "Kiln crackles",
    "block.kiln.crack": "Greenware cracks",
    "block.kiln.ping": "Glaze pings",
    "block.kiln.masterwork": "Masterwork rings",
    "entity.terracotta_sentinel.awaken": "Sentinel awakens",
}

# ---------------------------------------------------------------------------
# The Potter's Primer (book.fireclay.primer.p1..p10)
# ---------------------------------------------------------------------------

PRIMER = [
    # 1
    "Clay remembers the fire.\n\n"
    "Throw it, glaze it, fire it just right, and the clay keeps what the fire gave it.\n\n"
    "This is the craft of the Fireclay Guild. Read it by the kiln.",
    # 2
    "The Wheel\n\n"
    "Load clay balls, up to eight. Sneak with an empty hand to pick a shape: tile 1, bell 3, "
    "jar 4, urn 6, effigy 8 (fireclay only).\n\n"
    "Five throws with an empty hand and the piece pops off.",
    # 3
    "The Kiln\n\n"
    "Greenware, glaze, fuel. Heat settles where the fuel wants: wood 320°, coal 520°, "
    "coal block 620°, lava 670°, blaze 750° and up.\n\n"
    "Fuel burns only while a piece fires.",
    # 4
    "The Window\n\n"
    "Each piece cures only inside its heat window. Below it, progress waits.\n\n"
    "Two seconds above it and the piece cracks. Keep the shards: every good potter has a pile.",
    # 5
    "Bellows & Damper\n\n"
    "Face a bellows at a burning kiln: each pump adds 35°.\n\n"
    "Redstone into the back or top opens the damper. The fire halves and the kiln cools twice "
    "as fast.",
    # 6
    "The Sweet Spot\n\n"
    "The middle 40%% of the window. Hold it for nine tenths of a glazed firing and it comes "
    "out a Masterwork, signed with your name.\n\n"
    "Chimes, vigils and ember tiles keep the shine but forget the name.",
    # 7
    "Comparators\n\n"
    "A kiln reads 0 below the window, 1 to 14 inside it, 8 at its heart and 15 when too hot.\n\n"
    "Wire it to the damper and one thermostat fits every recipe.",
    # 8
    "Glazes\n\n"
    "Mix clay, water and a mineral.\n\n"
    "Lapis remembers.\nAmethyst listens.\nGlow ink keeps vigil.\nBlaze holds heat.\n"
    "Echo holds a soul.\n\nThe kiln seals it in.",
    # 9
    "Fireclay\n\n"
    "Shards become grog. Grog, clay and sand make fireclay; bake it to bricks and line the kiln.\n\n"
    "A lined kiln reaches 1000°. Coal runs hot in it: fire tiles on wood.",
    # 10
    "The Effigy\n\n"
    "Eight fireclay, a blaze glaze, a lined kiln held at 860–940° for forty seconds.\n\n"
    "Fire it white and true, and it will stand guard.",
]

# Glyph advances (px, including the 1 px gap) of the vanilla default font; anything else counts as 6.
_ADV = {c: 6 for c in "abcdeghjmnopqrsuvwxyzABCDEFGHJKLMNOPQRSTUVWXYZ0123456789&?#$%^_+=-/\\"}
_ADV.update({" ": 4, "i": 2, "l": 3, "t": 4, "f": 5, "k": 5, "I": 4, ".": 2, ",": 2, ":": 2, ";": 2, "!": 2,
             "'": 2, "|": 2, "(": 4, ")": 4, "[": 4, "]": 4, "\"": 4, "*": 4, "<": 5, ">": 5, "°": 5, "–": 8,
             "·": 2, "@": 7, "~": 7})
PAGE_WIDTH = 114
PAGE_LINES = 14


def page_lines(text: str) -> int:
    """Greedy word wrap the way the book screen does, counting lines."""
    lines = 0
    for para in text.split("\n"):
        width = 0
        lines += 1
        for word in para.split(" "):
            w = sum(_ADV.get(c, 6) for c in word)
            if width and width + 4 + w > PAGE_WIDTH:
                lines += 1
                width = w
            else:
                width = width + (4 if width else 0) + w
    return lines


# ---------------------------------------------------------------------------
# Everything else (tooltips, messages, commands, HUD, screen labels)
# ---------------------------------------------------------------------------

MISC = {
    "itemGroup.fireclay": "Fireclay",
    "container.fireclay.kiln": "Kiln",
    "container.fireclay.kiln.heat": "%s°",

    "tooltip.fireclay.masterwork": "★ Masterwork",
    "tooltip.fireclay.masterwork.potter": "Fired by %s",
    "tooltip.fireclay.stored_xp": "Holds %s XP",
    "tooltip.fireclay.sentinel_vitals": "Health %s / %s",
    "tooltip.fireclay.reliquary_charges": "Holds %s more passings",

    "message.fireclay.wheel.no_mix": "Don't mix clay and fireclay on one wheel",
    "message.fireclay.wheel.full": "The wheel holds at most %s balls",
    "message.fireclay.wheel.shaping": "Shaping: %s · %s clay (%s loaded)",
    "message.fireclay.wheel.needs": "Needs %s clay",
    "message.fireclay.wheel.needs_fireclay": "%s needs fireclay",
    "message.fireclay.wheel.throw": "Throw %s / %s",
    "message.fireclay.memory_jar.full": "The jar can remember no more",
    "message.fireclay.reliquary.bound": "The urn will remember you",
    "message.fireclay.reliquary.other_owner": "This urn keeps another's memory",
    "message.fireclay.reliquary.occupied": "Another urn still holds your belongings",
    "message.fireclay.reliquary.rest": "Your belongings rest in the Reliquary Urn at %s, %s, %s",
    "message.fireclay.sentinel.no_room": "There is no room for a Sentinel to stand here",
    "message.fireclay.sentinel.limit": "You can keep at most %s Sentinels awake",

    "command.fireclay.not_a_kiln": "There is no kiln at %s",
    "command.fireclay.kiln.info": "Kiln at %s: %s° of %s° (%s) · fuel %s/%s · damper %s · comparator %s",
    "command.fireclay.kiln.info.lined": "lined",
    "command.fireclay.kiln.info.unlined": "unlined",
    "command.fireclay.kiln.info.damper_open": "open",
    "command.fireclay.kiln.info.damper_closed": "closed",
    "command.fireclay.kiln.info.recipe": "Firing window %s–%s° · %s/%s ticks · %s%% sweet · overheat %s",
    "command.fireclay.kiln.info.no_recipe": "No firing: load greenware and a matching glaze",
    "command.fireclay.kiln.heat": "Set the kiln at %s to %s°",
    "command.fireclay.primer.single": "Gave The Potter's Primer to %s",
    "command.fireclay.primer.multiple": "Gave The Potter's Primer to %s players",
    "command.fireclay.record": "%s: %s firings, %s masterworks, %s cracks · primer %s",
    "command.fireclay.record.primer_yes": "received",
    "command.fireclay.record.primer_no": "not yet received",

    "hud.fireclay.pyrometer": "%s° · %s%% fired",
    "hud.fireclay.pyrometer.idle": "%s°",
}


# ---------------------------------------------------------------------------
# Key discovery
# ---------------------------------------------------------------------------

KEY_PREFIXES = ("block", "item", "entity", "itemGroup", "container", "subtitles", "advancements", "gamerule",
                "book", "tooltip", "message", "command", "hud")
LITERAL = re.compile(r'"((?:%s)\.%s(?:\.[A-Za-z0-9_]+)*\.?)"(\s*\+)?' % ("|".join(KEY_PREFIXES), NS))


def java_literals() -> tuple[set[str], set[str]]:
    """(exact keys, key prefixes that Java completes with a concatenation)."""
    exact, prefixes = set(), set()
    for path in sorted(SRC.rglob("*.java")):
        for match in LITERAL.finditer(path.read_text(encoding="utf-8")):
            key, concat = match.group(1), match.group(2)
            if concat or key.endswith("."):
                prefixes.add(key)
            else:
                exact.add(key)
    return exact, prefixes


def java_ids(rel: str) -> set[str]:
    text = (JAVA / rel).read_text(encoding="utf-8")
    return set(re.findall(r'Fireclay\.id\(\s*"([a-z0-9_/.]+)"', text))


def build() -> dict[str, str]:
    lang: dict[str, str] = {}
    lang.update({f"block.{NS}.{k}": v for k, v in BLOCK_NAMES.items()})
    lang.update({f"item.{NS}.{k}": v for k, v in ITEM_NAMES.items()})
    lang.update({f"entity.{NS}.{k}": v for k, v in ENTITY_NAMES.items()})
    lang.update({f"tooltip.{NS}.{k}.notes": v for k, v in GREENWARE_NOTES.items()})
    for rule, (name, desc) in GAME_RULES.items():
        lang[f"gamerule.{NS}.{rule}"] = name
        lang[f"gamerule.{NS}.{rule}.description"] = desc
    lang.update({f"subtitles.{NS}.{k}": v for k, v in SUBTITLES.items()})
    lang.update(gen_data.advancement_lang())
    lang.update({f"book.{NS}.primer.p{i}": text for i, text in enumerate(PRIMER, 1)})
    lang.update(MISC)
    return dict(sorted(lang.items()))


def required_keys() -> tuple[set[str], set[str]]:
    exact, prefixes = java_literals()
    need = set(exact)
    need |= {f"block.{NS}.{b}" for b in gen_data.BLOCKS}
    need |= {f"item.{NS}.{i}" for i in gen_data.ITEMS - gen_data.BLOCKS}
    need |= {f"entity.{NS}.{e}" for e in java_ids("registry/FireclayEntities.java")}
    greenware = re.findall(r'GreenwareItem\s+\w+\s*=\s*register\(\s*"([a-z0-9_]+)"',
                           (JAVA / "registry" / "FireclayItems.java").read_text(encoding="utf-8"))
    need |= {f"tooltip.{NS}.{g}.notes" for g in greenware}
    for rule in java_ids("registry/FireclayGameRules.java"):
        need |= {f"gamerule.{NS}.{rule}", f"gamerule.{NS}.{rule}.description"}
    sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    need |= {e["subtitle"] for e in sounds.values() if "subtitle" in e}
    for adv in sorted((gen_data.DATA / NS / "advancement").glob("*.json")):
        display = json.loads(adv.read_text(encoding="utf-8")).get("display", {})
        need |= {display[f]["translate"] for f in ("title", "description") if f in display}
    primer = (JAVA / "primer" / "PottersPrimer.java").read_text(encoding="utf-8")
    pages = int(re.search(r"PAGES\s*=\s*(\d+)", primer).group(1))
    need |= {f"book.{NS}.primer.p{i}" for i in range(1, pages + 1)}
    return need, prefixes


def main() -> None:
    lang = build()
    need, prefixes = required_keys()

    errors = [f"missing key: {k}" for k in sorted(need - lang.keys())]
    errors += [f"no key completes the Java prefix: {p}" for p in sorted(prefixes)
               if not any(k.startswith(p) for k in lang)]
    for i, text in enumerate(PRIMER, 1):
        lines = page_lines(text)
        if lines > PAGE_LINES:
            errors.append(f"primer page {i} needs {lines} lines (max {PAGE_LINES})")
    for key, value in lang.items():
        # Every %s placeholder must be well formed; a literal percent is %%.
        if re.search(r"%(?!s|%|\d+\$s)", value.replace("%%", "")):
            errors.append(f"bad format specifier in {key}: {value!r}")
    if errors:
        sys.exit("gen_lang: FAILED\n  " + "\n  ".join(errors))

    unused = sorted(k for k in lang.keys() - need if not any(k.startswith(p) for p in prefixes))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    json.loads(OUT.read_text(encoding="utf-8"))
    print(f"gen_lang: wrote {len(lang)} keys to {OUT.relative_to(ROOT)} "
          f"({len(need)} required, all present; primer pages "
          f"{', '.join(str(page_lines(t)) for t in PRIMER)} lines)")
    if unused:
        print("gen_lang: defined but not referenced (harmless): " + ", ".join(unused))


if __name__ == "__main__":
    main()
