# Fireclay: Final Design Document

*Lead designer decision for a Fabric mod on Minecraft Java 26.3 (Mojang mappings, Java 25, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18).*

---

## 0. Decision

**Winner: Fireclay** (23.5: fun 6, feasibility 9, craft 8.5). I kept the top total and did not override.

- **Why not Whalefall, the fun favourite.** Its whole payoff depends on two pipelines we can only validate at runtime on CI: a 20–50 block custom-model entity drawn with PIL-only art, and hand-written NBT jigsaw worldgen whose 26.3 schemas are unverified. A long chain of dependent high-risk systems sits on top of both. Fireclay's core is a deterministic simulation that server gametests can check exactly. That matters more than anything else in a CI-only workflow.
- **What Fireclay was missing.** The judges named three gaps:
  - a defining moment;
  - rewards that read as a grab-bag of utilities;
  - no networking, HUD or attachments.

  The grafts below target each one, and every graft must serve one fantasy.

**The fantasy, sharpened:** *Clay remembers the fire.* The glaze binds a mineral's nature into the clay, and the kiln seals it in:

| Glaze | Nature | What the piece does |
|---|---|---|
| Lapis | remembers | holds experience |
| Amethyst | listens | hears hostile mobs |
| Glow ink | keeps vigil | lights an area and wards mobs off |
| Blaze | holds heat | smelts what rests on it |
| Echo | holds a soul | holds your belongings when you die |
| Fireclay fired white-hot | holds a will | the Terracotta Sentinel |

Each reward is the same idea applied to a different mineral, so the rewards now share a theme.

### Graft ledger (accepted)

| Graft | From | Where it lands in Fireclay | Tier |
|---|---|---|---|
| A signature moment the player witnesses | Whalefall | The **Sentinel Awakening**: a 3 s cinematic with a synthesized rumble, rising embers and the figure standing up. Plus the **white-hot kiln**: light 15, chimney embers, a roar loop. | MUST |
| Synthesized signature audio | Whalefall | Bellows whoosh, kiln roar, **glaze ping** (the real tinkle of cooling glaze), wheel throw, awakening rumble | MUST |
| Redstone strength proportional to proximity, normalized | Whalefall Sky Chime + Fireclay comparator | The Sentry Chime outputs 1–15 by distance to the nearest hostile. The kiln comparator is window-relative. | MUST |
| An in-world guide in the mod's own voice | Escargo Postal Codex | **The Potter's Primer**, a generated written book with translatable pages, given once on first wheel placement | MUST |
| A discovery hook through loot injection | Escargo Lost Letter | Ancient **Kiln Shards** in trail-ruins archaeology loot. Ancient potters failed too. | SHOULD |
| Debug/op command doubling as a test hook; tuning gamerules | Mapwright | `/fireclay kiln <pos> heat\|info`, `fireclay:kiln_crack_grace` | MUST |
| One parameterized criterion behind the whole advancement tab | Agora/Mapwright | `fireclay:potter_action` | MUST |
| Identity on crafted goods | Agora sigils | The **potter's mark**: Masterworks are signed "Fired by Alex" | MUST (part of the masterwork component) |
| A small HUD readout driven by one S2C payload | Agora Rally HUD | The **Pyrometer**: look at a kiln to see a heat gauge without opening the GUI | SHOULD |
| Persistent player data via attachment | Whalefall kin / Escargo | `fireclay:potter_record`: primer flag, firings, masterworks, cracks | MUST |

**Rejected grafts:**
- Whalefall worldgen and sky entities: a second fantasy, plus high risk.
- Mapwright map painting: dilutes the theme.
- Escargo NPC letters: too much writing; off-theme.
- Agora GUIs: high risk.

---

## 1. Identity

| | |
|---|---|
| **Name** | Fireclay |
| **Mod id** | `fireclay` |
| **Tagline** | *Throw it, glaze it, fire it just right. Clay remembers the fire.* |

**Fantasy.** You are a potter, and heat is your magic. Raw clay is thrown on a wheel into greenware: tiles, bells, jars, urns and, at the top, a life-size effigy. You brush it with a mineral slip and fire it in a kiln whose single temperature you must steer.

- Choose fuel to set where the heat settles.
- Pump the bellows to push it up.
- Open the damper with redstone to pull it down.

Every firing has a heat window. Below it nothing happens. Inside it the piece cures. Stay above it for two seconds and the piece cracks with a decorated-pot shatter, but the shards become the fireclay that lines your next, hotter kiln. Hold the heat in the window's sweet spot and the piece comes out as a **Masterwork**: shining, stronger and signed with your name.

What comes out of the kiln is clay that remembers what it was fired with. Jars hold your experience. Bells hear monsters in the dark. Urns keep vigil and catch your soul's belongings when you die. Tiles hold heat and smelt what rests on them. At the end of the path, fireclay fired white-hot holds a will: you watch a **Terracotta Sentinel** crack, glow and stand up to guard your home.

---

## 2. Core loop and progression

### 2.1 The loop

```
dig clay ─▶ throw greenware on the wheel ─▶ mix a slip from a mineral ─▶ fire in the kiln
   ▲                                                                        │
   │            in window → functional ceramic (sweet spot → Masterwork)    │
   └── cracks → Kiln Shards → Grog → Fireclay → Kiln Lining (hotter kiln) ◀─┘
```

### 2.2 The heat model (signature mechanic, exact)

Each kiln simulates one float `H` (degrees, 20–cap) every server tick:

```
P = burning ? tier.power : 0
k = lined ? 0.0032 : 0.004          cap = lined ? 1000 : 750
if damperOpen: P *= 0.5; k *= 2
H = clamp(H + P - k*(H - 20), 20, cap)
```

Equilibrium is `20 + P/k`.

| Fuel tier (item tag) | Power P | Burn ticks | Unlined equilibrium | Lined equilibrium |
|---|---|---|---|---|
| `fireclay:kiln_fuel/kindling` (`minecraft:stick`, `#minecraft:saplings`, `#minecraft:wooden_slabs`) | 1.2 | 100 | 320 | 395 |
| `fireclay:kiln_fuel/wood` (`#minecraft:logs_that_burn`, `#minecraft:planks`) | 1.2 | 300 | 320 | 395 |
| `fireclay:kiln_fuel/coal` (`minecraft:coal`, `minecraft:charcoal`) | 2.0 | 1600 | 520 | 645 |
| `fireclay:kiln_fuel/coal_block` (`minecraft:coal_block`) | 2.4 | 16000 | 620 | 770 |
| `fireclay:kiln_fuel/lava` (`minecraft:lava_bucket`, leaves a `minecraft:bucket` in the fuel slot) | 2.6 | 20000 | 670 | 832 |
| `fireclay:kiln_fuel/blaze` (`minecraft:blaze_rod`) | 3.0 | 2400 | 770 → clamped to 750 | 957 |

Tags are checked in the order blaze, lava, coal_block, coal, wood, kindling. **The kiln deliberately ignores vanilla fuel values**, because 26.3 moved fuel to `DataComponents.COOKING_FUEL` and removed `FuelValueEvents`.

- **Fuel burning.** Fuel is consumed only while the greenware slot is non-empty. The output slot must also accept the current result, or no recipe matches. This works like a furnace. To preheat, players fire a cheap tile.
- **Damper.** It is open while redstone enters the kiln's **back or top face**: `level.hasSignal(pos.relative(d), d)` for `d ∈ {UP, facing.getOpposite()}`. Side faces stay free for bellows wiring. With the damper open, coal in an unlined kiln settles at 145 and blaze in a lined kiln at 254, so it cools fast.
- **Bellows.** One pump adds **+35 °** instantly (clamped to cap) and burns 20 ticks of fuel. It only works while the kiln is burning.
- **Firing window.** Progress (+1 per tick) advances only while `min ≤ H ≤ max`. Below `min`, progress pauses but is not lost.
- **Overheat.** While `H > max`, `over++`. When `H ≤ max`, `over = max(0, over − 2)`. When `over ≥ fireclay:kiln_crack_grace` (default 40), the piece **cracks**:
  - greenware and glaze are consumed;
  - **2–4 `fireclay:kiln_shard`** go to the output slot, or drop at the kiln mouth if the slot is blocked;
  - progress resets.
- **Sweet spot.** The middle 40% of the window: `[min + 0.3w, max − 0.3w]`. Count `sweet` ticks among in-window ticks. On completion, if the result is in `#fireclay:masterwork_eligible` and `sweet ≥ 0.9 × time`, the result gets `fireclay:masterwork`.
- **Comparator output** (window-relative, so one thermostat works for every recipe):
  - 0 means no recipe, or `H < min`;
  - 15 means `H > max`;
  - otherwise `1 + min(13, floor(14 × (H − min)/(max − min)))`. The centre reads 8, and the sweet spot reads about 5–10.
  - Call `updateNeighbourForOutputSignal` only when the value changes.
- **Visual heat stage.** Blockstate `heat`:
  - 0: H < 100, cold, light 0;
  - 1: 100–399, warm, light 7;
  - 2: 400–699, hot, light 11;
  - 3: ≥ 700, white-hot, light 15.

  `setBlock` runs only when the stage changes.

### 2.3 Firing recipes (`fireclay:firing`, data-driven)

| # | Greenware | Glaze | Result | min | max | time (ticks) | Needs lining | Masterwork-eligible |
|---|---|---|---|---|---|---|---|---|
| 1 | `fireclay:unfired_tile` | — | 4× `fireclay:fired_tiles` | 300 | 600 | 200 | | no |
| 2 | `fireclay:unfired_jar` | — | `fireclay:storage_jar` | 350 | 600 | 300 | | no |
| 3 | `fireclay:unfired_tile` | `fireclay:glowink_slip` | 4× `fireclay:lumen_tiles` | 480 | 640 | 300 | | no |
| 4 | `fireclay:unfired_jar` | `fireclay:lapis_slip` | `fireclay:memory_jar` | 540 | 680 | 400 | | yes |
| 5 | `fireclay:unfired_bell` | `fireclay:amethyst_slip` | `fireclay:sentry_chime` | 600 | 720 | 400 | | yes |
| 6 | `fireclay:unfired_urn` | `fireclay:glowink_slip` | `fireclay:vigil_urn` | 620 | 740 | 500 | | yes |
| 7 | `fireclay:unfired_tile` | `fireclay:blaze_slip` | 2× `fireclay:ember_tile` | 760 | 880 | 400 | yes (implicit: above 750) | yes (SHOULD) |
| 8 | `fireclay:unfired_urn` | `fireclay:echo_slip` | `fireclay:reliquary_urn` | 820 | 900 | 600 | yes | yes (SHOULD) |
| 9 | `fireclay:unfired_effigy` | `fireclay:blaze_slip` | `fireclay:sentinel_effigy` | 860 | 940 | 800 | yes | yes |

**Matching.** Input is a record `FiringInput(greenware, glaze)` implementing `RecipeInput`.
- A recipe with a glaze matches when both ingredients match.
- A no-glaze recipe matches **only if the glaze slot is empty**.
- A wrong glaze means no recipe, comparator 0, and nothing happens.
- If the recipe id changes mid-firing (for example, a slot is swapped), reset `progress`, `sweet` and `over`.

**JSON shape.** Codec on the `UpgradingRecipe` pattern:
```json
{ "type": "fireclay:firing", "greenware": "fireclay:unfired_jar", "glaze": "fireclay:lapis_slip",
  "min_heat": 540, "max_heat": 680, "time": 400, "result": { "id": "fireclay:memory_jar" } }
```
`glaze` is `optionalFieldOf`. `result` is `ItemStackTemplate.CODEC`.

**Designed skill curve:**
- Early pieces fire on coal (520) with no help.
- Glazes sit above coal, so you must pump, or use coal block or lava. A coal block settles at 620, inside the Memory Jar's sweet spot (582–638), but you must pump through the lower band quickly to reach 90% sweet.
- A lined kiln makes coal too hot for tiles (645 > 600), so you fire tiles with wood (395).
- The Effigy's sweet band (884–916) is narrower than one pump (35°). With lava you need precise manual timing. With blaze plus a comparator-driven damper, a thermostat holds it easily. Manual play is the hard route; redstone is the reliable one.

### 2.4 Crafting recipes (vanilla JSON, Python-generated)

| Result | Type | Pattern / ingredients |
|---|---|---|
| `fireclay:potters_wheel` | shaped | `" S "`,`"PTP"`,`"PPP"`; S=`minecraft:smooth_stone_slab`, T=`minecraft:stick`, P=`#minecraft:planks` |
| `fireclay:kiln` | shaped | `"BIB"`,`"BFB"`,`"BIB"`; B=`minecraft:brick`, I=`minecraft:iron_bars`, F=`minecraft:furnace` |
| `fireclay:bellows` | shaped | `"PPP"`,`"LLL"`,`"PNP"`; P=`#minecraft:planks`, L=`minecraft:leather`, N=`minecraft:iron_ingot` |
| 4× `fireclay:lapis_slip` | shapeless | `minecraft:clay_ball`, `minecraft:water_bucket` (returns a bucket), 2× `minecraft:lapis_lazuli` |
| 4× `fireclay:glowink_slip` | shapeless | clay_ball, water_bucket, 2× `minecraft:glow_ink_sac` |
| 4× `fireclay:amethyst_slip` | shapeless | clay_ball, water_bucket, 2× `minecraft:amethyst_shard` |
| 4× `fireclay:blaze_slip` | shapeless | clay_ball, water_bucket, 2× `minecraft:blaze_powder` |
| 2× `fireclay:echo_slip` | shapeless | clay_ball, water_bucket, 1× `minecraft:echo_shard` |
| 2× `fireclay:grog` | shapeless | 4× `fireclay:kiln_shard` |
| 2× `fireclay:grog` | shapeless | `minecraft:decorated_pot` |
| 3× `fireclay:fireclay_ball` | shapeless | 2× `minecraft:clay_ball`, `fireclay:grog`, [`minecraft:sand` or `minecraft:red_sand`] |
| `fireclay:fireclay_brick` | smelting (200 t, 0.3 xp) and blasting (100 t) | `fireclay:fireclay_ball` |
| `fireclay:kiln_lining` | shaped | `"BBB"`,`"BZB"`,`"BBB"`; B=`fireclay:fireclay_brick`, Z=`minecraft:blaze_powder` |
| 4× `fireclay:fired_tile_stairs` | shaped (stairs) and stonecutting 1:1 | `fireclay:fired_tiles` |
| 6× `fireclay:fired_tile_slab` | shaped and stonecutting 1:2 | `fireclay:fired_tiles` |
| clay back ("squash") | shapeless | unfired_tile → 1 clay_ball; unfired_bell → 3; unfired_jar → 4; unfired_urn → 6; unfired_effigy → 8 `fireclay:fireclay_ball` |
| `fireclay:pyrometer` (SHOULD) | shaped | `" G "`,`"CRC"`,`" C "`; G=`minecraft:glass_pane`, C=`minecraft:copper_ingot`, R=`minecraft:redstone` |
| 4× `fireclay:fireclay_bricks` (SHOULD) | shaped 2×2 | `fireclay:fireclay_brick`; plus stairs, slab and wall by crafting and stonecutting |

### 2.5 Progression stages

1. **Earthenware (first in-game day).**
   - Craft the Wheel, Kiln and Bellows. The first wheel placement gives *The Potter's Primer*.
   - Throw tiles and jars and fire them on coal.
   - Rewards: Storage Jars (portable 9-slot storage) and Fired Tiles. Probably a first crack, which teaches "keep the shards".
   - Advancements: *Wet Hands*, *Trial by Fire*, *Every Pot Has Its Crack*.
2. **Glazes (early-mid: lapis, glow squids, amethyst geodes).**
   - Windows climb above coal's equilibrium, so you hand-pump the bellows or switch to coal blocks or lava and watch for overshoot.
   - Rewards: Memory Jar, Lumen Tiles, Sentry Chime, Vigil Urn.
   - Masterworks reward good heat control. Redstone players build one comparator thermostat that works for every later recipe.
   - Advancements: *Hot Air*, *Sweet Spot*, *Thermostat*.
3. **Fireclay (first Nether trip).**
   - Shards → grog → fireclay → fireclay bricks, plus blaze powder → Kiln Lining. The cap rises to 1000.
   - Blaze rods now overshoot, so the damper becomes essential.
   - Rewards: Ember Tiles (fuel-free smelting floors). *White Heat* at 950°.
4. **High Fire (late: Ancient City echo shards, blaze).**
   - Reliquary Urns (death insurance).
   - The capstone: an 8-fireclay Effigy fired for 40 s in the 860–940 window. Use it and watch the **Sentinel Awakening**. A Masterwork Sentinel is the endgame flex.

---

## 3. Full content list

All JSON (blockstates, models, item model definitions, lang, sounds.json, recipes, loot tables, tags, advancements) is **generated by Python scripts** (`tools/gen_assets.py`, `tools/gen_data.py`) next to the PIL texture generator. Fabric's datagen API changed heavily from 26.2 to 26.3 (`FabricRecipeProvider` now takes `BootstrapContext<Recipe<?>>`, loot sub-providers were reworked), and the docs reference is 26.2. Registration conventions follow 26.3:
- `BlockBehaviour.Properties.setId(ResourceKey)` and `Item.Properties.setId(...)`;
- `Registry.register(BuiltInRegistries.X, …)`;
- block-backed items use `useBlockDescriptionPrefix()`.

### 3.1 Palette (shared hex)

| Material | Base | Shadow | Deep | Highlight | Accent |
|---|---|---|---|---|---|
| Raw clay | `#A4A9B8` | `#7E8496` | `#5F6577` | `#C5CAD6` | wet sheen `#DDE2EC` |
| Fireclay (buff) | `#D9C49A` | `#B39D72` | `#8F7A52` | `#EDE0BE` | grog specks `#8A6E4B` |
| Fired terracotta | `#B5603A` | `#8E4527` | `#6B3420` | `#D07A4F` | grout `#5A3A2C` |
| Kiln brick | `#9A4B35` | `#7C3A28` | soot `#4A2A20` | `#B4644A` | mortar `#B7A99A` |
| Lapis glaze | `#2F5BD3` | `#1F3FA8` | `#142A73` | `#5C86F0` | `#A9C2FF` |
| Glow-ink glaze | `#3FD6C4` | `#2A9E92` | `#1B6B63` | `#8EF5E6` | `#D8FFF8` |
| Amethyst glaze | `#8D5CC9` | `#6A3FA0` | `#4A2A73` | `#B98AE8` | `#E2C9FF` |
| Blaze glaze | `#E87A1E` | `#C2410C` | `#7A2808` | `#FFB347` | `#FFE08A` |
| Echo glaze | `#136E80` | `#0E4A5A` | `#052C36` | `#29DFEB` | `#A6FBFF` |
| Heat glow | warm `#7A2E12` | hot `#E0601A` | `#FF8C2A` | white-hot `#FFE9A8` | core `#FFF8E0` |
| Masterwork gold | `#F2C94C` | `#C99A2E` | | `#FFF0A8` | |
| Iron / copper | iron `#A8A8A8` / `#6E6E6E` | copper `#C36A35` / `#8B4A26` | | `#E3895B` | glass `#CFE8F0` |
| Oak / leather | oak `#A8834F` / `#8A6A3B` | leather `#7A4A2B` / `#5E3720` | | `#9C6640` | |

**Texture rules:**
- All **block** textures are fully opaque (alpha 255). Silhouettes come from cuboid model elements, so no render-layer registration is needed.
- Non-full blocks set `noOcclusion()`.
- Items are 16×16 with a transparent background and a 1 px darker outline (`#2B1D17` at 100% on the outer edge).

### 3.2 Blocks

| Registry path | Class / properties | Behavior spec | Texture / model |
|---|---|---|---|
| `fireclay:potters_wheel` | `BaseEntityBlock` + BE `fireclay:potters_wheel`. States `clay` 0..4 (lump size), `fireclay` bool, `spinning` bool. Strength 2.0, axe. `noOcclusion` | **Use with `minecraft:clay_ball`:** load 1 (max 8). Refused if fireclay is loaded: actionbar `fireclay.wheel.no_mix`. **Use with `fireclay:fireclay_ball`:** same, sets fireclay. **Sneak + empty hand:** cycle shape Tile(1)/Bell(3)/Jar(4)/Urn(6)/Effigy(8, fireclay only); actionbar "Shaping: Unfired Jar · 4 clay (3 loaded)"; resets throws. **Empty hand:** a *throw*. Ignored while `spinning`. If loaded < cost, actionbar "Needs 4 clay". Otherwise throws++, `spinning=true` with `scheduleTick(10)`, 8× `ItemParticleOption(ITEM, clay_ball)` and sound `block.potters_wheel.throw`. **5th throw:** subtract cost, throws=0, pop the greenware item 0.6 above the wheel, criterion `threw`. **Breaking** drops loaded balls plus the wheel. **First placement** by a player whose `potter_record.primerGiven == false` gives the Primer. `lump = ceil(count/2)` | Multipart: base = oak legs and frame, plus a stone wheel head (cuboid 14×2×14 at y=10). Head top `potters_wheel_top` (`#A0A0A0`, concentric rings `#7F7F7F`, spoke mark `#5E5E5E`); a `spinning` variant uses `potters_wheel_top_spinning` with a 4-frame `.mcmeta` (frametime 2), spoke rotated 90° per frame. Lump parts: cuboid sizes 4×2×4, 5×3×5, 6×4×6, 7×5×7, textured `wheel_clay` (raw clay) or `wheel_fireclay` |
| `fireclay:kiln` | `BaseEntityBlock` + BE `fireclay:kiln`, `WorldlyContainer`. States `facing` (horizontal), `heat` 0..3, `lined` bool, `damper` bool. Strength 3.5, pickaxe, requires tool. Light per heat 0/7/11/15 | The heat model in §2.2. **Use:** open the menu and set *stoker* = player. **Use with `fireclay:kiln_lining`** while unlined: `lined=true`, consume, criterion `lined`. **Hoppers:** UP face inserts greenware (`#fireclay:greenware`); side faces insert glaze (`#fireclay:glazes`) else fuel (`#fireclay:kiln_fuels`); DOWN extracts the output slot and an empty bucket from the fuel slot. **Ambience:** every 40 t at heat ≥ 2, sound `block.kiln.roar` (volume 0.4 + 0.2×stage); while `over > 0`, every 10 t `block.kiln.overheat` plus 3× `LAVA`. `animateTick` (client): heat 1+ `SMOKE` from the top vent, heat 2+ `FLAME` at the mouth, heat 3 `LAVA` sparks. **Drops:** kiln, plus `kiln_lining` if lined (Java `getDrops` post-processing), plus contents (`Containers.dropContents`) | **Side:** brick with mortar lines and soot gradient toward the top. **Top:** brick with a 4×4 vent; `damper=true` shows the vent open (`#1E1410` hole with `#E0601A` glow ring). **Front (8 textures = 4 heat × lined):** arched 6×6 mouth. Heat 0 is dark `#2B1D17`; heat 1 dull red `#7A2E12`; heat 2 orange `#E0601A`/`#FF8C2A`; heat 3 white-hot `#FFE9A8` with `#FFF8E0` core. Lined variants add a 1 px buff fireclay rim `#D9C49A` around the mouth |
| `fireclay:bellows` | `Block`. States `facing` (all 6), `powered`, `pressed`. Strength 1.5, axe | **Pump** on use (any hand, not sneaking) or on a rising redstone edge (`neighborChanged`: powered && !state.powered). Refused while `pressed`. Sets `pressed=true` with `scheduleTick(8)`, 6× `CLOUD` at the nozzle, sound `block.bellows.pump`. If the faced block is a Kiln, call `kiln.pump(player or null)`: +35 °C and −20 burn ticks if burning, else an ash puff. A manual pump records the stoker and `manualPumps++`; a redstone pump counts `redstonePumps++`. Criterion `pumped` when manual | **Model:** front plank board 16×16×2, leather pleated body, back board; `pressed` compresses depth 16→10. Nozzle cuboid 4×4×3 on the facing side. Leather pleats are vertical zigzags `#7A4A2B`/`#5E3720`; boards oak; nozzle iron |
| `fireclay:fired_tiles` | `Block`, strength 1.5, pickaxe | Decorative | 4 tiles of 7×7 with 1 px grout `#5A3A2C`. Per-tile hues `#B5603A`, `#BE6A41`, `#AD5935`, `#B86440`, with 2–3 random 1 px `#D07A4F` glints |
| `fireclay:fired_tile_stairs` / `fireclay:fired_tile_slab` | `StairBlock` / `SlabBlock` | Vanilla behavior | Reuse the tile texture; vanilla stairs/slab parent models |
| `fireclay:lumen_tiles` | `Block`, light 15, pickaxe | Decorative light | Pale tile `#E8F4F0` with glow-ink crackle lines `#8EF5E6`/`#3FD6C4` and grout `#9FC9C1` |
| `fireclay:storage_jar` | BE `fireclay:storage_jar` (extends `BaseContainerBlockEntity`, 9 slots). Strength 1.0, pickaxe; shape box 3..13 × 0..13 × 3..13 | **Use:** opens a vanilla dispenser 3×3 menu (`new DispenserMenu(id, inv, be)`). Rejects items in `#fireclay:storage_jar_blacklist` (shulker boxes and storage jars). **Comparator:** `AbstractContainerMenu.getRedstoneSignalFromContainer`. **Keeps contents when broken:** `getDrops` post-processing writes `DataComponents.CONTAINER` (`ItemContainerContents.fromItems`); `setPlacedBy` reads it back. Custom name is kept the same way | **Model:** body 10×11×10, neck 6×1×6, lid 8×1×8. Terracotta with lighter throwing rings `#D07A4F` every 3 px; lid `#8E4527` |
| `fireclay:memory_jar` | BE `fireclay:memory_jar`. States `fill` 0..4, `masterwork`. Light = fill×2 | Capacity 1395 XP (the total to reach level 30), or **4185** as a Masterwork. **Empty hand: deposit.** `partial = floor(progress × need(L))`; amount = partial if > 0, else need(L−1); clamp to free space; `player.giveExperiencePoints(-amount)`; sound `EXPERIENCE_ORB_PICKUP` pitch 0.6–0.8. **Sneak + empty hand: withdraw** `min(stored, need(L) − partial)`, pitch 1.0–1.2. `need(L)` is vanilla's `L≥30: 112+9(L−30); L≥15: 37+5(L−15); else 7+2L`. Fill = `ceil(4×xp/cap)`. Comparator `round(15×xp/cap)`. Breaking keeps `fireclay:stored_xp` and masterwork (via `getDrops` + `setPlacedBy`). Actionbar "The jar can remember no more" when full | Same jar silhouette in lapis glaze, a gold rim `#E5B83A` at the neck, and a vertical 2 px "memory vein" on the front lit up to fill height in `#7FD3FF` (5 front textures, fill 0–4) |
| `fireclay:sentry_chime` | `FaceAttachedHorizontalDirectionalBlock`-style. States `face`, `facing`, `power` 0..15, `masterwork`. No BE. Strength 1.0, pickaxe | `onPlace` schedules a tick every **10 t**. Scan an AABB of radius R = 12 (20 Masterwork) for living `Mob` that is `instanceof Enemy`. Nearest distance d gives `power = d ≤ 2 ? 15 : clamp(ceil(15×(1 − (d−2)/(R−2))), 1, 15)`, or 0 if none. `isSignalSource=true`; `getSignal = power`; `getDirectSignal = power` toward the attached face (strong, like a lever). Detected mobs get `GLOWING` for 60 t. While power > 0, ring every 40 t: `AMETHYST_BLOCK_RESONATE` pitch 0.8 + power/30, plus 3× `NOTE`. Masterwork state from the item on placement (`getStateForPlacement`) | Bell cuboid (6×7×6 body, 8×1×8 lip, plus a 2×2×2 hanger for ceiling/wall). Amethyst glaze; powered variant uses highlight `#E2C9FF` streaks |
| `fireclay:vigil_urn` | `Block`. States `masterwork`. Light 15. Strength 1.0, pickaxe; shape 4..12 × 0..15 | Tick every **10 t**. Enemies within a sphere of radius 8 (12 Masterwork), not in `#fireclay:vigil_immune` (`ender_dragon`, `wither`, `warden`, `elder_guardian`): add a horizontal outward impulse of 0.6 plus 0.1 upward, `hurtMarked=true`; `Creeper#setSwellDir(-1)`. On a push (40 t cooldown), soft `AMETHYST_BLOCK_CHIME`. `animateTick`: occasional `GLOW` ring | Tall urn (8×12×8 body, 6×3×6 neck, two 1×4×2 handles). Glow-ink glaze with a front "eye" motif `#D8FFF8` |
| `fireclay:ember_tile` (SHOULD) | `Block`. States `masterwork`. Light 7. Strength 1.5, pickaxe | `stepOn`: like magma. Not stepping carefully, not fire-immune, LivingEntity: `hurtServer(level, damageSources().hotFloor(), 1.0f)`. Tick every **20 t**: each `ItemEntity` in the block above (height 0.5) gains +20 on attachment `fireclay:bake_progress`. At ≥ 200 (100 Masterwork), smelt `min(8, count)` items via `RecipeManager.createCheck(RecipeType.SMELTING)` + `SingleRecipeInput` + `assemble`. Spawn the results, shrink the source, reset progress, 4× `SMOKE` and `FIRE_EXTINGUISH` at 0.4 volume. Unsmeltable items are ignored. Criterion `ember_baked` for players within 8 | Tile base `#C2410C` with glowing crack network `#FFB347`/`#FFE08A`, 1 px grout `#7A2808` |
| `fireclay:reliquary_urn` (SHOULD) | BE `fireclay:reliquary_urn` (owner UUID+name, charges). States `filled`, `masterwork`. Strength 2.0, pickaxe | See §3.11 | Echo-glaze urn (same silhouette as Vigil) with a cyan soul swirl `#29DFEB`; `filled` variant brightens the swirl to `#A6FBFF`. `animateTick` while filled: `SCULK_SOUL` |
| `fireclay:fireclay_bricks` (+ `_stairs`, `_slab`, `_wall`) (SHOULD) | `Block` family, strength 2.0, pickaxe | Decorative | Buff bricks `#D9C49A`/`#C8B184`, mortar `#9E8A66`, grog specks |

Every block above has a `BlockItem`. The Memory Jar, Sentry Chime, Vigil Urn, Ember Tile and Reliquary use `MasterworkBlockItem`, which overrides `isFoil` to `stack.has(MASTERWORK) || super`. The glint comes from the component, so the kiln never sets `enchantment_glint_override`.

### 3.3 Items

| Registry path | Behavior | Texture (16×16) |
|---|---|---|
| `fireclay:unfired_tile` | In `#fireclay:greenware`. Tooltip "Firing notes" (lang): "Bare: 300–600°. Glow ink: lumen. Blaze: ember (lined)." | A square slab seen from above in raw clay, 12×12 inset, thumbprint `#7E8496` |
| `fireclay:unfired_bell` | Greenware; notes "Amethyst: 600–720°" | Bell silhouette, raw clay, wet sheen highlight |
| `fireclay:unfired_jar` | Greenware; notes "Bare: storage. Lapis: memory, 540–680°" | Jar with neck and throwing rings (`#C5CAD6` stripes) |
| `fireclay:unfired_urn` | Greenware; notes "Glow ink: vigil. Echo: reliquary (lined)" | Tall urn with handles |
| `fireclay:unfired_effigy` | Greenware (fireclay only); notes "Blaze: 860–940°, 40 s, lined. Fire it well." | Small humanoid with head crest in buff fireclay, grog specks |
| `fireclay:lapis_slip`, `fireclay:glowink_slip`, `fireclay:amethyst_slip`, `fireclay:blaze_slip`, `fireclay:echo_slip` | In `#fireclay:glazes` | One template: a squat clay bowl (rim `#7E8496`, body `#A4A9B8`) filled with the glaze color (base/highlight from the palette, a 2 px specular dot). Echo adds 2 sparkle pixels `#A6FBFF` |
| `fireclay:kiln_shard` | Crafting material | Three jagged terracotta shards; each has a 1–2 px glaze remnant (lapis, amethyst, blaze colors) |
| `fireclay:grog` | Material | Granule pile: `#B5603A`, `#8E4527`, cream specks `#EDE0BE` |
| `fireclay:fireclay_ball` | Material; throwable on the wheel | Vanilla clay-ball shape in the buff palette with grog specks |
| `fireclay:fireclay_brick` | Material | Vanilla brick-item shape in buff `#D9C49A`/`#B39D72` |
| `fireclay:kiln_lining` | Use on an unlined Kiln: `lined=true` | A 3×3 grid of buff bricks with a central blaze seam `#E87A1E` |
| `fireclay:sentinel_effigy` | `useOn`: awaken a Sentinel (§3.6). Stack size 1. Tooltip shows health if `fireclay:sentinel_vitals` is present | Terracotta figure with orange glaze shoulders `#E8902A` and a glowing chest pixel `#FFC24A`. Masterwork glint from the component (custom `isFoil`) |
| `fireclay:pyrometer` (SHOULD) | While in either hand, the server sends kiln readings (§3.9). Stack size 1 | Copper dial with glass face `#CFE8F0` and red needle `#D02020` |
| *The Potter's Primer* | **Not a new item.** A `minecraft:written_book` built in Java with `WRITTEN_BOOK_CONTENT`: title "The Potter's Primer" (raw string ≤ 32 chars), author "The Fireclay Guild", 10 pages of `Component.translatable("book.fireclay.primer.pN")`. Given on first wheel placement and by `/fireclay primer` | Vanilla |

**Primer pages (lang, en_us), page outline:**
1. *Clay remembers the fire.*
2. The Wheel (load, choose, five throws).
3. The Kiln (four slots; heat settles where the fuel wants: wood 320, coal 520, coal block 620, lava 670, blaze 750+).
4. The Window (progress only inside; two seconds over and it cracks; keep the shards).
5. Bellows +35° and the Damper (redstone into the back or top halves the fire and doubles the cooling).
6. The Sweet Spot (middle 40%, held for 90% of the firing, makes a Masterwork that bears your mark).
7. Comparators (0 below, 1–14 inside, 8 at centre, 15 over; one thermostat fits every recipe).
8. Glazes (lapis remembers, amethyst listens, glow keeps vigil, blaze holds heat, echo holds a soul).
9. Fireclay (shards → grog → fireclay → lining; a lined kiln reaches 1000°, and coal runs hot, so fire tiles with wood).
10. The Effigy (*"Fire it white and true, and it will stand guard."*).

### 3.4 Block entities

| Registry path | Stored fields (ValueOutput/ValueInput) | Ticker |
|---|---|---|
| `fireclay:kiln` | items[4] (`ContainerHelper.saveAllItems`); `heat` (float as int×10); `burn`, `burnMax`; `power` (×100); `progress`, `sweet`, `over`; `recipeId` (Optional); `manualPumps`, `redstonePumps`, `damperToggles`; `stoker` (UUID via `UUIDUtil.CODEC` + name string); `lastSignal` | Server, every tick. Pseudo-code is in §2.2 and §3.4.1 |
| `fireclay:potters_wheel` | `clayType` (NONE/CLAY/FIRECLAY), `count` 0..8, `shape` 0..4, `throws` 0..4 | None (scheduled tick on the block clears `spinning`) |
| `fireclay:storage_jar` | items[9], custom name | None |
| `fireclay:memory_jar` | `xp` int, `masterwork` Optional | None |
| `fireclay:reliquary_urn` (SHOULD) | `owner` UUID, `ownerName`, `charges` (1 or 2), `masterwork` | Server, every 40 t: sync `filled` with SavedData |

#### 3.4.1 Kiln completion and crack (exact)

```
complete():
  out = recipe.assemble(input)
  if out ∈ #fireclay:masterwork_eligible && sweet >= 0.9*time:
      out.set(MASTERWORK, new Masterwork(Optional.ofNullable(stokerName)))
      sound block.kiln.masterwork, 12× WAX_ON burst at mouth
      criterion masterwork → stoker
      if manualPumps == 0 && (redstonePumps + damperToggles) > 0:
          criterion thermostat → all players ≤ 16 blocks
  insert out into slot 3 (merge if same item+components)
  shrink greenware 1; if recipe.glaze present: shrink glaze 1
  sound block.kiln.ping (vol 1.0 if glazed, 0.5 bare); criterion fired(item) → stoker; record.firings++
  reset progress/sweet/over/pump counters
crack():
  shrink greenware 1 and glaze 1 (if any); shards = 2 + rand(3) → slot 3 else drop at mouth
  sound block.kiln.crack; 10× ItemParticleOption(kiln_shard) + 6× SMOKE
  criterion cracked → stoker; record.cracks++; reset as above
heat >= 950 (first time per firing): criterion white_heat → stoker
```

The output can accept the result only if slot 3 is empty, or holds the same item with the same components and has room. If it cannot, progress halts (as in a furnace) and fuel does not ignite.

### 3.5 Menu and screen

- **`fireclay:kiln` MenuType.** `new MenuType<>(KilnMenu::new, FeatureFlagSet.of())`, as in the `ModMenuTypes` reference.
- **Slots.** 0 greenware (38,17), 1 glaze (62,17), 2 fuel (50,53), 3 output (116,35, take-only), plus the player inventory at (8,84).
- **`quickMoveStack`** routes by tag: greenware → 0, glazes → 1, kiln fuels → 2.
- **ContainerData** has 9 ints, all kept ≤ 32767:
  `0 heat, 1 cap, 2 windowMin, 3 windowMax, 4 progress, 5 total, 6 burn, 7 burnMax, 8 flags`
  Flag bits: 1 over, 2 inSweet, 4 damper, 8 lined, 16 masterworkOnTrack (`sweet ≥ 0.9×progress`).
- **Recipes are not synced to the client in 26.x.** The window reaches the screen only through ContainerData.
- **`KilnScreen extends AbstractContainerScreen<KilnMenu>`**, `extractBackground(GuiGraphicsExtractor, …)` (UpgradingScreen pattern):
  - `blit(RenderPipelines.GUI_TEXTURED, fireclay:textures/gui/container/kiln.png, …)`, a 256×256 sheet with a 176×166 panel.
  - **Flame** sprite (14×14 at 176,0) drawn at (50,36), clipped by `burn/burnMax`.
  - **Arrow** (24×17 at 176,14) drawn at (79,34), clipped by `progress/total`.
  - **Gauge.** A vertical 10×60 bar at (152,14) on scale 0–1000. Background `#2A1A14`. Area above the cap hatched `#3A2A24`/`#2A1A14`. Window band `fill` `#3FA34D` (alpha 0xC0). Sweet band `#8CE07A`. Needle: a white 2 px line plus a 3 px triangle at x=148. When `over`, the border flashes `#E0401A` (`Util.getMillis()/150 % 2`). A gold 5×5 star at (150,76) when masterworkOnTrack.
  - **Labels.** `text(...)` shows "612°" under the gauge.
- **Client registration:** `MenuScreens.register` (ExampleModScreens pattern).
- **GUI texture (PIL).** Vanilla-style panel `#C6C6C6` with bevels `#FFFFFF`/`#555555`, slot wells `#8B8B8B`, a small kiln-mouth illustration behind the fuel slot, and the gauge frame.

### 3.6 Entity: `fireclay:terracotta_sentinel` (the one custom model)

- **Class.** `TerracottaSentinel extends PathfinderMob` (MiniGolem pattern).
- **EntityType.** `EntityType.Builder.of(TerracottaSentinel::new, MobCategory.MISC).sized(0.7f, 2.0f).fireImmune().clientTrackingRange(10)`.
- **Persistence.** `removeWhenFarAway → false`; `requiresCustomPersistence → true`.
- **Attributes** (`FabricDefaultAttributeRegistry`):
  - MAX_HEALTH 60 (Masterwork: base set to 90 on awaken);
  - ARMOR 10;
  - ATTACK_DAMAGE 8;
  - KNOCKBACK_RESISTANCE 0.8;
  - MOVEMENT_SPEED 0.25;
  - FOLLOW_RANGE 24.
- **Synced data.** `AWAKEN` int (60 → 0), `MASTERWORK` bool.
- **Saved data.** owner UUID, post BlockPos, Masterwork record (Optional), awaken.
- **Awakening (the signature moment).** Spawned by `sentinel_effigy.useOn` at `clicked.relative(face)`, if `level.noCollision` for the bounding box.
  - Facing: yaw toward the player.
  - Health comes from `fireclay:sentinel_vitals`, if present.
  - During the 60 t: no AI (goals check `awaken > 0`), invulnerable.
  - Each tick: 2× `LAVA` + 3× `SMALL_FLAME` rising around the feet; at t=60 sound `entity.terracotta_sentinel.awaken`; at t=20, 1× `block.kiln.crack` at pitch 1.4.
  - At t=0: a ring of 24 `FLAME` + 6 `CAMPFIRE_COSY_SMOKE`.
  - Criterion `awakened` (masterwork flag) to the owner.
  - Model pose: body pitch `0.6×p`, head pitch `0.5×p`, arms crossed at the chest blending to idle, where `p = awaken/60`.
- **Goals:**
  - `FloatGoal` (0);
  - `MeleeAttackGoal(1.0, false)` (1);
  - custom `ReturnToPostGoal` (2): if `distSqr(post) > 12²`, `navigation.moveTo(post, 1.0)`;
  - `WaterAvoidingRandomStrollGoal(0.6)` (4), restricted by the post goal;
  - `LookAtPlayerGoal(Player, 8)` (5);
  - `RandomLookAroundGoal` (6).
- **Targets:**
  - `HurtByTargetGoal` (1), with `canAttack` false for every `Player`;
  - `NearestAttackableTargetGoal<Mob>` (2): target `instanceof Enemy`, not `#fireclay:sentinel_ignores` (creeper), within 16 of the post.
- **Attack.** On a successful `doHurtTarget`, `target.igniteForSeconds(4)`. Arm-swing animation through `attackAnim`, both arms raising like an iron golem.
- **Interaction (`mobInteract`), owner only:**
  - `minecraft:clay_ball` heals 10 HP, consumes 1 and shows 4× `HAPPY_VILLAGER`;
  - sneak + empty hand returns it as a `fireclay:sentinel_effigy` with `sentinel_vitals(health)` and the masterwork record, then discards the entity.
- **Masterwork regen.** +1 HP every 40 t if not hurt in the last 100 t.
- **Sounds.** Hurt `DECORATED_POT_HIT`, death `DECORATED_POT_SHATTER`, step `STONE_STEP` (vanilla `SoundEvents`).
- **Death drop** (Java `dropCustomDeathLoot`): 4–8 `fireclay:kiln_shard`.
- **Renderer (client).**
  - `TerracottaSentinelRenderer extends MobRenderer<TerracottaSentinel, SentinelRenderState, SentinelModel>`, shadow 0.5.
  - `SentinelRenderState extends LivingEntityRenderState { float awaken; boolean masterwork; float attackAnim; }`, filled in `extractRenderState`.
  - Texture: `textures/entity/terracotta_sentinel.png`, or `_masterwork.png` when `masterwork`.
  - Registered with `EntityRenderers.register` plus the model-layer registration (exactly as in `ExampleModCustomEntityClient`).
- **Model** (`LayerDefinition` 64×64, hand-built humanoid; no `HumanoidModel` dependency):

  | Part | Box | UV |
  |---|---|---|
  | head | 8×8×8 | (0,0) |
  | crest (topknot) | 2×3×5 on top of the head | (32,0) |
  | body | 8×12×4 | (16,16) |
  | arms | 4×12×4 | (40,16), mirrored (32,48) |
  | legs | 4×12×4 | (0,16), mirrored (16,48) |
  | waist plate | 9×3×5 | (0,32) |

  `setupAnim`: walk `cos(walkPos×0.6662)×1.4×walkSpeed` on the legs and opposite arms, attack swing, awakening pose.
- **Skin (PIL).**
  - terracotta base `#B5603A` with lamellar plate rows (outlines `#8E4527`, highlights `#D07A4F`);
  - orange glaze on shoulders and belt `#E8902A`;
  - eyes 2×1 `#FFE9A8`;
  - chest "kiln mouth" 4×3 `#FFC24A` with core `#FFF8E0`;
  - hairline cracks `#3D1E12`.
  - The Masterwork skin adds gold leaf `#F2C94C`/`#FFF0A8` on the crest, plate edges and belt.
- **Fallback if rendering blocks CI.** The Effigy places a 2-tall decorative `fireclay:terracotta_effigy` block instead. The progression is unchanged; the guard role is lost.

### 3.7 Data components

| Registry path | Type and codecs | Tooltip (via `ItemComponentTooltipProviderRegistry.addLast`) | Used by |
|---|---|---|---|
| `fireclay:masterwork` | `record Masterwork(Optional<String> potter) implements TooltipProvider`. Codec `RecordCodecBuilder` with `Codec.STRING.optionalFieldOf("potter")`. StreamCodec `ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8)` mapped | "★ Masterwork" (gold), "Fired by Alex" (gray italic) | Kiln output, Memory Jar, Chime, Urns, Ember Tile, Effigy and Sentinel |
| `fireclay:stored_xp` | `record StoredXp(int points)`, `ExtraCodecs.NON_NEGATIVE_INT` / `VAR_INT` | "Holds 812 XP" | Memory Jar item |
| `fireclay:sentinel_vitals` | `record SentinelVitals(float health)`, `Codec.FLOAT` / `ByteBufCodecs.FLOAT` | "Health 34 / 60" | Picked-up effigy |
| `fireclay:reliquary_charges` (SHOULD) | `Integer` (1–2) | "Holds 2 more passings" | Reliquary item |

Masterwork effects (read from the BE field or the blockstate `masterwork`):
- Memory Jar capacity ×3 (4185).
- Chime radius 12 → 20.
- Vigil radius 8 → 12.
- Ember bake 200 → 100 t.
- Reliquary charges 1 → 2.
- Sentinel 90 HP plus regen.

Blockstate-only blocks (Chime, Vigil, Ember) re-apply `Masterwork(Optional.empty())` on drop. The potter's name survives only on BE-backed pieces and the Sentinel; this is documented in the Primer.

### 3.8 Attachments

| Registry path | Target | Spec |
|---|---|---|
| `fireclay:potter_record` | Player. `persistent(PotterRecord.CODEC).copyOnDeath()` | `record PotterRecord(boolean primerGiven, int firings, int masterworks, int cracks)`. Updated on kiln events for the stoker. At 100 firings, criterion `hundred_fires` (SHOULD) |
| `fireclay:bake_progress` (SHOULD) | ItemEntity, transient `Integer` | Ember Tile progress; removed after smelting |

### 3.9 Network payload and HUD (SHOULD)

- **Payload `fireclay:kiln_reading` (S2C).**
  - Fields: `record KilnReading(BlockPos pos, int heat, int cap, int window /*min<<16|max*/, int progress /*progress<<16|total*/, int flags)`.
  - `StreamCodec.composite` with 6 fields (keep arity ≤ 6).
  - Registered with `PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)`; client receiver `ClientPlayNetworking.registerGlobalReceiver`.
- **Server sender.** `ServerTickEvents.END_SERVER_TICK`, every 4 ticks, for each player holding `fireclay:pyrometer` in either hand:
  - raycast with `level.clip(new ClipContext(eye, eye + look×6, OUTLINE, NONE, player))`;
  - if the hit block entity is a Kiln, send a reading;
  - criterion `pyrometer_read` on the first reading.
- **HUD element `fireclay:pyrometer`.** `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, …)`.
  - Drawn only if the last reading is ≤ 10 client ticks old and a pyrometer is held.
  - Horizontal 120×6 bar centred at `screenH − 62`. Scale 0–1000: background `#2A1A14`, window `#3FA34D`, sweet `#8CE07A`, needle white, red border flash when over.
  - Above it, `text()`: "612° · 63% fired" (white with shadow).

### 3.10 Sounds (`assets/fireclay/sounds.json`; mono OGG Vorbis; subtitles in lang)

| Sound event | Source |
|---|---|
| `fireclay:block.potters_wheel.throw` | ffmpeg ×3 variants: 0.18 s pink noise lowpass 900 Hz plus a 90 Hz sine thump with exponential decay (wet slap) |
| `fireclay:block.bellows.pump` | ffmpeg: `anoisesrc=color=pink:d=0.5`, `bandpass=f=700:w=900`, fade in 0.08 s, fade out from 0.2 s |
| `fireclay:block.kiln.roar` | ffmpeg: 3 s brown noise, lowpass 300 Hz, `tremolo=f=3:d=0.4`, 0.3 s edge fades |
| `fireclay:block.kiln.overheat` | ffmpeg: 0.6 s random clicks (`aevalsrc` impulses) plus a hiss band 3–6 kHz |
| `fireclay:block.kiln.crack` | sounds.json references vanilla files `minecraft:block/decorated_pot/shatter1..5`, pitch 0.8 |
| `fireclay:block.kiln.ping` | ffmpeg: 4–5 sine pings at 2.6–4.2 kHz, each 0.35 s exp decay, `adelay` at random 0–1.1 s, `amix` |
| `fireclay:block.kiln.masterwork` | ffmpeg: arpeggio 660/830/990 Hz with sine + 2nd harmonic, 0.25 s apart, 1.2 s tail |
| `fireclay:entity.terracotta_sentinel.awaken` | ffmpeg: 3 s 40→90 Hz sine sweep plus brown-noise grind lowpass 200, ending with an 880+1320 Hz chime |

Vanilla events are used directly elsewhere: `EXPERIENCE_ORB_PICKUP`, `AMETHYST_BLOCK_RESONATE`/`CHIME`, `FIRE_EXTINGUISH`, `DECORATED_POT_HIT`/`SHATTER`, `STONE_STEP`.

### 3.11 Reliquary Urn mechanics (SHOULD)

- **Bind.** Empty-hand use on an unbound urn, or one bound to you, binds it. It replaces your previous binding in SavedData `fireclay_reliquaries` (`SavedDataType` + Codec: `Map<UUID, Binding(GlobalPos urn, List<ItemStack> held)>`). Shows 20× `SCULK_SOUL`. An urn bound to someone else gives the actionbar message "This urn keeps another's memory".
- **On death.** Handled in `ServerLivingEntityEvents.ALLOW_DEATH` for `ServerPlayer`, which always returns true. The urn activates only if all of these hold:
  - gamerule `fireclay:reliquary_urns` is true;
  - `KEEP_INVENTORY` is false;
  - the player has a binding;
  - neither hand holds an item with `DataComponents.DEATH_PROTECTION` (this event fires before the totem check).

  When it activates, move every inventory slot (`0..getContainerSize()`) into `held`, except items with the `PREVENT_EQUIPMENT_DROP` enchantment effect. XP drops normally. On `ServerPlayerEvents.AFTER_RESPAWN`, chat: "Your belongings rest in the Reliquary Urn at x, y, z". Criterion `reliquary_saved`.
- **Retrieve.** The owner uses the urn: items go to the inventory, and overflow drops at the urn. `charges--`. At 0, the urn is removed and drops 2–4 kiln shards with a crack sound.
- **Break.** A filled urn spills its held items at the urn and clears the entry. A bound, empty urn drops itself with `reliquary_charges` and unbinds.

### 3.12 Particles

- **MUST, vanilla only:** `SMOKE`, `LARGE_SMOKE`, `FLAME`, `SMALL_FLAME`, `LAVA`, `CLOUD`, `NOTE`, `GLOW`, `WAX_ON`, `HAPPY_VILLAGER`, `CAMPFIRE_COSY_SMOKE`, `SCULK_SOUL`, and `ItemParticleOption(ParticleTypes.ITEM, clay_ball / kiln_shard)`.
- **SHOULD: `fireclay:kiln_ember`.**
  - Registered with `FabricParticleTypes.simple()`; client `ParticleProviderRegistry.getInstance().register(...)` with a small `TextureSheetParticle`-style class.
  - 3-frame 8×8 sprite: `#FFE08A` core, `#FF8C2A` mid, `#C2410C` edge.
  - Lifetime 20–40 t, rising 0.02/t with slight jitter, fading.
  - Emitted from the kiln vent at heat ≥ 2 and from the Sentinel chest during awakening.
  - Fallback: `LAVA`.

### 3.13 Effects and enchantments

**None.** This is deliberate: Fireclay's power lives in crafted objects, not in buffs. The only status effect applied is vanilla `GLOWING` (Sentry Chime), and Sentinel hits set targets on fire.

### 3.14 Advancement criterion and advancements

- **Criterion `fireclay:potter_action`.** A `SimpleCriterionTrigger` with `TriggerInstance(Optional<ContextAwarePredicate> player, String action, Optional<ItemPredicate> item, Optional<Boolean> masterwork)`, following the `ParameterizedUseToolCriterion` pattern.
- **Action strings:** `threw`, `fired`, `cracked`, `pumped`, `masterwork`, `thermostat`, `lined`, `white_heat`, `awakened`, `pyrometer_read`, `ember_baked`, `reliquary_saved`, `hundred_fires`.
- **Generated advancement JSON** follows the 26.3 sample format (see §6). Tab background: `minecraft:textures/block/terracotta.png`, or whatever the vanilla dump shows.

| Id | Title / description | Trigger | Parent | Frame |
|---|---|---|---|---|
| `fireclay:root` | **Wet Hands**: Throw a piece on the Potter's Wheel | `threw` | — | task |
| `fireclay:trial_by_fire` | **Trial by Fire**: Fire anything in a Kiln. Coal settles near 520°. | `fired` | root | task |
| `fireclay:every_pot_has_its_crack` | **Every Pot Has Its Crack**: Overheat a firing. Keep the shards. | `cracked` | trial_by_fire | task |
| `fireclay:hot_air` | **Hot Air**: Pump a Bellows into a burning Kiln | `pumped` | trial_by_fire | task |
| `fireclay:sweet_spot` | **Sweet Spot**: Fire a Masterwork | `masterwork` | hot_air | goal |
| `fireclay:thermostat` | **Thermostat**: Fire a Masterwork without touching the bellows by hand | `thermostat` | sweet_spot | challenge |
| `fireclay:lapis_remembers` | **Lapis Remembers**: Fire a Memory Jar | `fired` + item `fireclay:memory_jar` | trial_by_fire | task |
| `fireclay:amethyst_listens` | **Amethyst Listens**: Fire a Sentry Chime | `fired` + item | trial_by_fire | task |
| `fireclay:glow_keeps_vigil` | **Glow Keeps Vigil**: Fire a Vigil Urn | `fired` + item | trial_by_fire | task |
| `fireclay:lined_with_failure` | **Lined with Failure**: Line a Kiln with bricks made from your cracks | `lined` | every_pot_has_its_crack | goal |
| `fireclay:white_heat` | **White Heat**: Reach 950° | `white_heat` | lined_with_failure | task |
| `fireclay:terracotta_army` | **Terracotta Army**: Awaken a Terracotta Sentinel | `awakened` | white_heat | goal |
| `fireclay:masterpiece_guardian` | **Masterpiece Guardian** (hidden): Awaken a Masterwork Sentinel | `awakened`, masterwork=true | terracotta_army | challenge |
| `fireclay:blaze_holds_heat` (SHOULD) | **Blaze Holds Heat**: Bake something on an Ember Tile | `ember_baked` | lined_with_failure | task |
| `fireclay:echo_holds_a_soul` (SHOULD) | **Echo Holds a Soul**: Fire a Reliquary Urn | `fired` + item | lined_with_failure | task |
| `fireclay:memento_mori` (SHOULD) | **Memento Mori**: Be saved by your Reliquary Urn | `reliquary_saved` | echo_holds_a_soul | goal |
| `fireclay:read_the_fire` (SHOULD) | **Read the Fire**: Read a Kiln with a Pyrometer | `pyrometer_read` | hot_air | task |
| `fireclay:hundred_fires` (SHOULD) | **A Hundred Fires**: Complete 100 firings | `hundred_fires` | sweet_spot | challenge |

### 3.15 Game rules (`GameRuleBuilder`, category MISC)

| Id | Type / default | Effect |
|---|---|---|
| `fireclay:kiln_crack_grace` | int, 40 (range 1–1200) | Ticks over `max_heat` before a crack |
| `fireclay:reliquary_urns` (SHOULD) | bool, true | Enables death capture |
| `fireclay:max_sentinels_per_player` (SHOULD) | int, 4 | Awakening is refused past the cap. Tracked in SavedData `fireclay_sentinels` (owner → set of UUIDs; added on awaken, removed on death or pickup) |

### 3.16 Commands (`CommandRegistrationCallback`; `requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))`)

- `/fireclay kiln <pos> info` prints heat, cap, recipe window, progress/time, sweet %, burn, damper and comparator.
- `/fireclay kiln <pos> heat <0..1000>` sets H. Used by tests, client screenshots and creative builders.
- `/fireclay primer [targets]` gives the Primer.
- `/fireclay record [player]` prints the potter record.

### 3.17 Tags

- **Items:**
  - `fireclay:greenware`, `fireclay:glazes`;
  - `fireclay:kiln_fuels` (union), plus `fireclay:kiln_fuel/{kindling,wood,coal,coal_block,lava,blaze}`;
  - `fireclay:masterwork_eligible` (memory_jar, sentry_chime, vigil_urn, ember_tile, reliquary_urn, sentinel_effigy);
  - `fireclay:storage_jar_blacklist` (`#minecraft:shulker_boxes`, `fireclay:storage_jar`).
- **Entity types:** `fireclay:vigil_immune`, `fireclay:sentinel_ignores` (`minecraft:creeper`).
- **Blocks:**
  - `minecraft:mineable/pickaxe`: kiln, tiles, jars, urns, chime, ember, fireclay bricks;
  - `minecraft:mineable/axe`: wheel, bellows;
  - `minecraft:stairs`, `minecraft:slabs`, `minecraft:walls`.

### 3.18 Creative tab and loot

- **Creative tab** `fireclay:fireclay`: `FabricCreativeModeTab.builder()`, icon `fireclay:kiln`, every item in progression order.
- **Block loot tables.** One uniform "drop self, survives explosion" template copied from the vanilla dump. Slabs use the vanilla slab template. Component-bearing drops (jar contents, XP, masterwork, lining) are added in Java `getDrops` post-processing, using `LootContextParams.BLOCK_ENTITY`. This keeps 26.x loot-condition schemas out of our JSON.
- **SHOULD: archaeology injection.** `LootTableEvents.MODIFY` on `minecraft:archaeology/trail_ruins_common` adds one `fireclay:kiln_shard` entry, weight 2.

---

## 4. Scope tiers

### MUST (v1 core, about 70%)

- **Blocks:** Potter's Wheel (BE), Kiln (BE, WorldlyContainer, menu, screen, heat model, damper, comparator, lining), Bellows, Fired Tiles + stairs + slab, Lumen Tiles, Storage Jar (BE), Memory Jar (BE), Sentry Chime, Vigil Urn.
- **Items:** 5 greenware, 5 slips, kiln shard, grog, fireclay ball, fireclay brick, kiln lining, sentinel effigy. The Primer (vanilla written book).
- **Recipe type** `fireclay:firing` (serializer + 7 recipes: #1–6 and #9) and all crafting, smelting and stonecutting JSON from §2.4 (except the pyrometer and fireclay bricks).
- **Entity:** Terracotta Sentinel with the awakening sequence, renderer, model and 2 skins.
- **Components:** `masterwork` (with potter's mark), `stored_xp`, `sentinel_vitals`. `MasterworkBlockItem` glint.
- **Attachment:** `potter_record`. Primer on first wheel placement.
- **Criterion** `potter_action` and the 13 MUST advancements.
- **Gamerule** `kiln_crack_grace`. The `/fireclay` command.
- **Sounds:** wheel throw, bellows pump, kiln roar, overheat, crack, ping, masterwork, awaken.
- **Assets:** all PIL textures, GUI, 2 sentinel skins, generated JSON, en_us lang.
- **Tests:** server gametests G1–G16 and client gametest C1–C3 (§5).

### SHOULD

- Ember Tile with recipe #7, `bake_progress` attachment and Blaze Holds Heat.
- Reliquary Urn with recipe #8, SavedData, ALLOW_DEATH, charges component, gamerule and two advancements.
- Pyrometer with the `kiln_reading` payload, HUD element and Read the Fire.
- `kiln_ember` custom particle.
- Fireclay Bricks family.
- Trail-ruins shard injection.
- `max_sentinels_per_player` gamerule.
- A Hundred Fires.
- Tests G17–G22.

### COULD

- **Potter's Wheel BER** (the single allowed BER, Counter BER pattern): renders the shaped greenware growing per throw on the spinning head.
- **Raku Lustre Tiles:** `fireclay:copper_slip` (copper ingot). A firing at 700–750 gives one of 6 random iridescent tile variants (blockstate `lustre` 0..5).
- **Bellows on vanilla furnaces:** +20 cook-progress ticks per pump.
- **Sentinel accents:** using glazed terracotta on a Sentinel recolors its glaze accents (16 PIL accent skins). An emissive chest layer.
- **Gauge hover tooltip** in the Kiln screen.
- **Sentinel Recall:** use an Unfired-Bell-based "Sentinel Bell" to send all your sentinels back to their posts.

**Budget check.** One custom-model entity (Sentinel). Zero BERs in MUST/SHOULD (one in COULD). One menu and screen. One HUD element. One payload.

---

## 5. Gametest plan

Infrastructure:
- Loom `fabricApi { configureTests { enableGameTests = true; enableClientGameTests = true } }`.
- Server tests use `@GameTest(maxTicks = N)` from `fabric-gametest-api-v1` (default structure `fabric-gametest-api-v1:empty`; **the default maxTicks is 20, so set it explicitly**).
- Kiln tests are **deterministic**. Following `FurnaceGameTest`, they call `KilnBlockEntity.serverTick(level, absPos, state, be)` in a loop inside the test method and then assert. No waiting is needed.
- Helpers: `helper.setBlock`, `helper.getBlockEntity(pos, Class)`, `helper.makeMockPlayer(GameType.SURVIVAL)`, `helper.useBlock(pos, player)`, `helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos)`, `helper.assertValueEqual`, `helper.assertItemEntityPresent`, `helper.succeed()`.

### Server gametests (MUST)

| # | Name | Setup → assertion |
|---|---|---|
| G1 | `registry_contents_loaded` | `recipeAccess().byKey()` finds all MUST firing, crafting, smelting and stonecutting recipe ids. All MUST block and item ids are registered. Catches JSON schema errors that only show up in logs. |
| G2 | `kiln_coal_equilibrium` | Unlined kiln, 8 coal, 64 unfired tiles (keeps burning). Tick 3000 → `|H − 520| ≤ 8`. |
| G3 | `kiln_lined_wood_equilibrium` | `lined=true`, oak logs → after 3000 t, `|H − 395| ≤ 8`. |
| G4 | `kiln_fires_tiles` | Unlined, coal, 1 unfired tile. Tick 1500 → slot 3 holds 4 `fired_tiles`; slot 0 is empty. |
| G5 | `kiln_cracks_when_over` | Lined, blaze rods, 1 unfired tile, H preset to 700 → within 200 t, slot 3 holds 2–4 kiln shards and no tiles. |
| G6 | `kiln_crack_grace_gamerule` | Same as G5 with the gamerule at 400 → no crack after 100 t of being over. |
| G7 | `kiln_damper_cools` | H=700, coal burning, redstone block behind the kiln → after 125 t, H < 450; `damper=true`. Same with the redstone block on a side face → `damper=false`. |
| G8 | `bellows_pumps_kiln` | Bellows facing a burning kiln. Call `pump` → H +35 (±1), burn −20. A second pump within 8 t is refused. `powered` rising edge pumps once. |
| G9 | `comparator_is_window_relative` | Memory-jar recipe loaded (jar + lapis slip). H=539 → 0. H=610 → 8. H=681 → 15. |
| G10 | `masterwork_on_center` | Memory-jar recipe, coal block burning, H preset to 620. Tick 600 → output has `fireclay:masterwork`; `isFoil` is true. |
| G11 | `no_masterwork_off_center` | Same, but lava with H preset to 670 (in window, out of sweet) → output has no masterwork component. |
| G12 | `wheel_throws_jar` | Mock player loads 4 clay balls with `useBlock`, sneak-cycles to Jar, then makes 5 throws (between throws, run the block's scheduled tick or `runAfterDelay(11)`) → an `unfired_jar` item entity is above the wheel; BE count is 0. |
| G13 | `memory_jar_deposit_withdraw` | Mock player at level 10, progress 0. Deposit → BE xp = 25, player level 9. Withdraw → level 10, BE xp 0. |
| G14 | `jars_keep_contents_when_broken` | Storage jar with 5 diamonds and a memory jar with 300 xp; `destroyBlock(drop=true)` → item entities carry `CONTAINER` (5 diamonds) and `stored_xp=300`. Re-placing via `setPlacedBy` restores them. |
| G15 | `sentry_chime_detects_and_scales` | Chime at the origin and a no-AI zombie at distance 3. After 12 t: power ≥ 12 and the zombie has GLOWING. Teleport it to distance 10 → power between 1 and 5. Remove it → 0. |
| G16 | `vigil_urn_pushes_and_defuses` | Zombie at distance 2 → after 20 t its distance is > 3. A creeper with swell set → `getSwellDir() == -1`. A warden-tagged entity is not pushed (tag check unit). |
| G17 | `kiln_hopper_io` | Top hopper with 1 unfired tile, side hopper with coal, bottom hopper below. Run until the bottom hopper contains `fired_tiles`. |
| G18 | `sentinel_awakens_and_guards` | Use the effigy with a mock player → entity present with `AWAKEN=60`, invulnerable. After 80 t, spawn a zombie 4 blocks away; `succeedWhen` the zombie is dead or `sentinel.getTarget() == zombie` (maxTicks 400). The mock player is never targeted. |
| G19 | `sentinel_effigy_roundtrip` | Damage the Sentinel to 34 HP. Owner sneak + empty hand → effigy item with `sentinel_vitals.health == 34`; the entity is gone. |
| G20 | `kiln_lining_installs_and_drops` | Use the lining on a kiln → `lined=true`. Destroy → drops `kiln` + `kiln_lining`. |

### Server gametests (SHOULD)

| # | Name | Setup → assertion |
|---|---|---|
| G21 | `ember_tile_bakes_raw_iron` | 8 `raw_iron` item entity on the tile; run 220 t → 8 `iron_ingot`. |
| G22 | `reliquary_captures_inventory` | Mock player bound to an urn, holding 3 stacks; kill with `hurtServer(generic kill)` → no item entities nearby; SavedData holds 3 stacks. Retrieve → inventory restored. A totem in hand → the urn is skipped. |
| G23 | `pyrometer_payload_codec` | Encode/decode `KilnReading` round-trip on a `RegistryFriendlyByteBuf`. |

### Client gametest (MUST): `FireclayClientGameTest implements FabricClientGameTest`

```
try (TestSingleplayerContext sp = context.worldBuilder().create()) {
  sp.getClientLevel().waitForChunksRender();
  srv = sp.getServer();
  srv.runCommand("gamemode creative @a"); srv.runCommand("time set 1000"); srv.runCommand("gamerule doDaylightCycle false");
  // C1 showcase: a 9x5 fired_tiles floor at ~ ~-1 ~; lumen_tiles border
  setblock kiln[facing=south,heat=3,lined=true], bellows[facing=east] on its west, potters_wheel[clay=4,fireclay=true],
  storage_jar, memory_jar[fill=3], sentry_chime[face=floor,power=0], vigil_urn, ember_tile (if present)
  summon fireclay:terracotta_sentinel ~2 ~ ~3 {NoAI:1b}; a second one with masterwork via /data
  tp @p <camera pos> <yaw> <pitch>; context.waitTicks(40); context.takeScreenshot("fireclay-showcase-day");
  // C2 night glow: time set 18000; waitTicks(20); takeScreenshot("fireclay-showcase-night")   (white-hot kiln light 15)
  // C3 kiln screen: runOnServer → place unfired_jar + lapis_slip + coal_block, /fireclay kiln <pos> heat 610,
  //    then server-side player.openMenu(kilnBE); context.waitForScreen(KilnScreen.class); waitTicks(10);
  //    takeScreenshot("fireclay-kiln-screen")  (band, sweet band, needle, flame, arrow visible)
  // (SHOULD) C4 pyrometer HUD: close screen, give pyrometer, look at kiln, waitTicks(10), takeScreenshot("fireclay-pyrometer-hud")
  // (SHOULD) C5 awakening: summon via effigy use at 30 ticks into the 60-tick sequence → takeScreenshot("fireclay-awakening")
}
```

Screenshots are uploaded as CI artifacts for visual review. Pass/fail is that the test runs without exceptions; pixel diffs are not used.

---

## 6. Risk register (26.x API changes; read before coding)

| # | Area | Risk | Mitigation / reference |
|---|---|---|---|
| R1 | **Data JSON formats** | 26.x changed loot pools (`"condition": "minecraft:tool/can_silk_touch"` references), predicate objects (`"type": "minecraft:match_block"`), advancement `background` paths, and recipe `result` (`ItemStackTemplate`). Bad JSON fails only at runtime, in logs. | **First CI job:** a Gradle task that extracts vanilla files from the Loom-merged Minecraft jar and prints them to the log as an artifact: `loot_table/blocks/{furnace,shulker_box,oak_slab}.json`, `recipe/{furnace,brick,stone_bricks,stone_brick_stairs_from_stonecutting,decorated_pot}.json`, `advancement/story/root.json`, `sounds.json` entries for `block.decorated_pot`. Python generators copy their structure exactly. G1 asserts everything loaded. 26.3 samples are in `fabric-api/fabric-data-generation-api-v1/src/testmod/generated/`. |
| R2 | **Datagen API churn 26.2→26.3** | `FabricRecipeProvider` now takes `BootstrapContext<Recipe<?>>`; loot sub-providers were reworked; the docs reference is 26.2. | Use Python JSON generation instead of datagen. |
| R3 | **Fuel** | 26.3 moved fuel to `DataComponents.COOKING_FUEL` (`CookingFuel(ContextIntProvider, …)`) and removed `FuelValueEvents`. `level.fuelValues()` may be gone. | The kiln uses its own tag-tier table (§2.2) and never reads vanilla fuel. Lava's bucket remainder is handled manually. |
| R4 | **Vanilla constant renames** | `EntityTypes.CREEPER` / `EntityTypes.SALMON` (not `EntityType.X`) and `Blocks.WOOL.black()` style families appear in 26.3 testmods. Other constants may have moved. | Prefer `instanceof` checks and tags over constants. Fix from compile errors. Only stable items (`Items.CLAY_BALL`, etc.) are referenced in Java. |
| R5 | **Custom recipe type** | The `Recipe` interface (`placementInfo`, `recipeBookCategory`, `isSpecial`, `showNotification`, `group`, `assemble(input)` with one argument), `ItemStackTemplate`, `RecipeSerializer` registration form, `Ingredient.CONTENTS_STREAM_CODEC`. Recipes are not synced to the client. | Copy the `UpgradingRecipe` / `ExampleModRecipes` reference. Never query recipes on the client; the window is sent through ContainerData. Lookup via `level.recipeAccess().getRecipeFor(TYPE, input, level)` (UpgradingMenu). |
| R6 | **Menu / screen** | `AbstractContainerScreen.extractBackground(GuiGraphicsExtractor, …)`; drawing method names are `blit(RenderPipelines.GUI_TEXTURED, …)`, `fill`, `fillGradient`, `text`, `outline`. Label and tooltip method names. `DataSlot` values sync as shorts. | Copy the UpgradingScreen and DrawContextExampleScreen references. Keep values ≤ 32767 (max burn 20000). |
| R7 | **Entity pipeline** | `MobRenderer<E, S, M>` + `createRenderState` + `extractRenderState(entity, state, partial)`, `LayerDefinition`/model-layer registration, `EntityRenderers.register`. Goal API changes: `NearestAttackableTargetGoal` selector `(LivingEntity, ServerLevel)`, `restrictTo` possibly renamed (`setHomeTo`), `hurtServer(ServerLevel, …)`, `doHurtTarget(ServerLevel, Entity)`. | Copy MiniGolem exactly. Use the custom `ReturnToPostGoal` instead of the restriction API. Procedural `setupAnim` only (no keyframe definitions). Statue-block fallback documented (§3.6). |
| R8 | **Block entity ↔ item components** | `applyImplicitComponents`/`collectImplicitComponents` have no local reference and may be renamed. | Avoided: components are read in `setPlacedBy(…, ItemStack)` and written in `getDrops` post-processing (`LootContextParams.BLOCK_ENTITY`). Verify that `getDrops(BlockState, LootParams.Builder)` is still overridable; fallback `playerWillDestroy` plus manual drop. |
| R9 | **Container drops on removal** | 26.x moved removal side effects (`onRemove` → `affectNeighborsAfterRemoval` / `preRemoveSideEffects`). | Check `DirtChestBlock`/`DuplicatorBlock` in the reference for the 26.x hook. G14 and G20 cover it. |
| R10 | **Save API** | `ValueOutput`/`ValueInput` (`putInt`, `store(key, codec, value)`, `read(key, codec)`), `ContainerHelper.saveAllItems(ValueOutput, items)`. | References: DirtChestBlockEntity, CounterBlockEntity, MiniGolem. |
| R11 | **Networking / HUD (SHOULD)** | `PayloadTypeRegistry.clientboundPlay()` (renamed from `playS2C`); `StreamCodec.composite` arity. `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id, (GuiGraphicsExtractor, DeltaTracker) -> …)`. | Confirmed in `ExampleModNetworkingBasic` and `HudRenderingEntrypoint`. Keep the codec at 6 fields. |
| R12 | **Death interception (SHOULD)** | `ServerLivingEntityEvents.ALLOW_DEATH` fires **before** the totem check (`ServerPlayerEvents.ALLOW_DEATH` is deprecated in 26.3). Inventory layout includes equipment slots. | Skip when either hand has `DEATH_PROTECTION`. Iterate `getContainerSize()`. Fallback: a single HEAD inject into `Player.dropEquipment(ServerLevel)` (verify the signature from CI errors). G22. |
| R13 | **Smelting lookup (SHOULD)** | Signature drift. | Confirmed in the 26.3 LootTest: `RecipeManager.createCheck(RecipeType.SMELTING)`, `SingleRecipeInput`, `getRecipeFor(input, level)`, `recipe.assemble(input)`. |
| R14 | **Assets pipeline** | 26.x needs `assets/fireclay/items/<id>.json` item model definitions; multipart blockstates; `.mcmeta` animation; sounds.json referencing vanilla OGG paths. | All Python-generated. Opaque block textures avoid render-layer registration. If a vanilla sound path is wrong, fall back to `SoundEvents` constants. |
| R15 | **Tooltips for custom components** | Custom component tooltips must be registered. | `ItemComponentTooltipProviderRegistry.addLast(type)` (fabric-item-api-v1, 26.3) with `TooltipProvider` records (`ComponentWithTooltip` reference). |
| R16 | **Gametest specifics** | Default `maxTicks=20`. `makeMockPlayer` behavior for XP and death. Client tests: `worldBuilder().create()`, `getServer().runCommand`, `runOnServer`, `waitForScreen`, `takeScreenshot`. | Confirmed in the 26.3 client-gametest API source. Set `maxTicks` per test. Drive the kiln synchronously. |
| R17 | **Permissions / commands / gamerules / creative tab** | Minor. | Confirmed: `source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)`, `GameRuleBuilder.forInteger/forBoolean(...).buildAndRegister(id)`, `FabricCreativeModeTab.builder()`. |
| R18 | **Particles (SHOULD)** | Custom particle class API. | `FabricParticleTypes.simple()` + `ParticleProviderRegistry.getInstance().register(type, PendingParticleProvider)` (26.3 testmod `ParticleGroupRegistryTests`). Fallback to vanilla `LAVA`. |
| R19 | **Loot injection (SHOULD)** | `LootTableEvents.MODIFY` changed in 26.3. | Lambda `(key, tableBuilder, source, provider)`, as in the 26.3 LootTest. Cuttable. |

**Build order:**
1. Python asset/data generators plus the vanilla-dump CI step.
2. Items, tags, tiles.
3. Kiln BE and heat model with G2–G11.
4. Menu and screen, C3.
5. Bellows and damper.
6. Wheel, G12.
7. Jars, chime, urn, G13–G16.
8. Lining, fireclay chain.
9. Sentinel entity and renderer, C1/C2, G18–G19.
10. Advancements, Primer, command.
11. SHOULD tier.

**Cut lines if CI iteration runs long:**
- Custom particle, then the Pyrometer HUD, then the Reliquary, then the Ember Tile.
- If the Sentinel renderer fails repeatedly, ship the statue-block fallback.