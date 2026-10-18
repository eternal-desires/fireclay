# Fireclay implementation contract (binding for assets AND Java)

Design doc: `design.md` (same folder). This contract pins every name that must agree
between Java code and resources. When this file and design.md disagree, THIS FILE wins.

Paths below are relative to the repository root.
- Java main:   `src/main/java/io/github/eternaldesires/fireclay/...`   (package `io.github.eternaldesires.fireclay`)
- Java client: `src/client/java/io/github/eternaldesires/fireclay/client/...`
- Resources:   `src/main/resources/` (assets/fireclay/..., data/fireclay/..., data/minecraft/tags/...)
- Gametests:   `src/gametest/java/io/github/eternaldesires/fireclay/test/...` (+ `src/gametest/resources`)
- Generators:  `tools/*.py` (Python 3 + PIL + numpy; run from the repository root; they write into src/main/resources). `tools/palette.py` holds the shared palette. Sounds: `tools/gen_sounds.sh` (ffmpeg, libvorbis, mono).
- Every generator is deterministic (fixed random seeds) so re-running reproduces identical files.

Mod id `fireclay`. All ids below are in the `fireclay` namespace unless written otherwise.

## 1. Blocks (each has a BlockItem with the same id)

| id | Java class | Blockstate properties (exact names/values) | Collision/outline shape (Block.box units) | Render |
|---|---|---|---|---|
| `potters_wheel` | `PottersWheelBlock extends BaseEntityBlock` (BE `potters_wheel`) | `clay` int 0..4, `fireclay` boolean, `spinning` boolean | box(0,0,0,16,3,16) ∪ box(6,3,6,10,10,10) ∪ box(1,10,1,15,12,15) | noOcclusion |
| `kiln` | `KilnBlock extends BaseEntityBlock` (BE `kiln`) | `facing` horizontal (north/south/east/west; front = facing), `heat` int 0..3, `lined` boolean, `damper` boolean | full cube | solid; light 0/7/11/15 by heat |
| `bellows` | `BellowsBlock extends Block` | `facing` all 6 directions (= direction the nozzle points, i.e. toward the kiln), `powered` boolean, `pressed` boolean | full cube | noOcclusion |
| `fired_tiles` | `Block` | — | full | solid |
| `fired_tile_stairs` | `StairBlock` | vanilla stair props (`facing`,`half`,`shape`,`waterlogged`) | vanilla | solid |
| `fired_tile_slab` | `SlabBlock` | vanilla slab props (`type`,`waterlogged`) | vanilla | solid |
| `lumen_tiles` | `Block` (light 15) | — | full | solid |
| `storage_jar` | `StorageJarBlock extends BaseEntityBlock` (BE `storage_jar`) | — | box(3,0,3,13,13,13) | noOcclusion |
| `memory_jar` | `MemoryJarBlock extends BaseEntityBlock` (BE `memory_jar`) | `facing` horizontal (vein faces the player who placed it: placement facing = player's horizontal direction opposite), `fill` int 0..4, `masterwork` boolean | box(3,0,3,13,13,13) | noOcclusion; light = fill*2 |
| `sentry_chime` | `SentryChimeBlock extends FaceAttachedHorizontalDirectionalBlock` | `face` (floor/wall/ceiling), `facing` horizontal, `power` int 0..15, `masterwork` boolean | floor box(4,0,4,12,10,12); ceiling box(4,6,4,12,16,12); wall: the floor shape rotated so the mount touches the attached face (facing=north → box(4,4,6,12,12,16), south → box(4,4,0,12,12,10), east → box(0,4,4,10,12,12), west → box(6,4,4,16,12,12)) | noOcclusion |
| `vigil_urn` | `VigilUrnBlock extends Block` (light 15) | `masterwork` boolean | box(3,0,3,13,15,13) | noOcclusion |
| `ember_tile` | `EmberTileBlock extends Block` (light 7) | `masterwork` boolean | full | solid |
| `reliquary_urn` | `ReliquaryUrnBlock extends BaseEntityBlock` (BE `reliquary_urn`) | `filled` boolean, `masterwork` boolean | box(3,0,3,13,15,13) | noOcclusion |
| `fireclay_bricks` | `Block` | — | full | solid |
| `fireclay_brick_stairs` | `StairBlock` | vanilla | vanilla | solid |
| `fireclay_brick_slab` | `SlabBlock` | vanilla | vanilla | solid |
| `fireclay_brick_wall` | `WallBlock` | vanilla wall props | vanilla | solid |

All block textures are fully opaque → no render-layer registration needed. Masterwork never changes a block model (glint is item-only).

### 1.1 Block model geometry (models are built for the "default" orientation; blockstates rotate)

- `potters_wheel` (multipart): `block/potters_wheel_base` = base slab (0,0,0)-(16,3,16) [texture `potters_wheel_base` all faces], column (6,3,6)-(10,10,10) [`potters_wheel_column`], wheel head (1,10,1)-(15,12,15) [top: `potters_wheel_top`; sides+bottom: `potters_wheel_head_side`].
  `block/potters_wheel_head_spinning` = same head cuboid only with top `potters_wheel_top_spinning` (animated) — multipart: base without head part when spinning=false shows `potters_wheel_head`, when spinning=true shows `potters_wheel_head_spinning`. So split into `potters_wheel_base` (slab+column), `potters_wheel_head`, `potters_wheel_head_spinning`.
  Clay lumps on top of the head (y from 12): clay=1 (6,12,6)-(10,14,10); clay=2 (5.5,12,5.5)-(10.5,15,10.5); clay=3 (5,12,5)-(11,16,11); clay=4 (4.5,12,4.5)-(11.5,17,11.5). Models `potters_wheel_lump_1..4` (texture `wheel_clay`) and `potters_wheel_lump_fireclay_1..4` (texture `wheel_fireclay`). Multipart `when` uses `clay` and `fireclay`.
- `kiln`: full cube, built like `minecraft:block/orientable_with_bottom`: models `block/kiln_h{0..3}[_lined][_open]` (16 models) — `_lined` swaps front to `kiln_front_lined_h{n}`, `_open` swaps top to `kiln_top_open`. Textures: `kiln_side`, `kiln_bottom`, `kiln_top`, `kiln_top_open`, `kiln_front_h0..h3`, `kiln_front_lined_h0..h3`. Blockstate variants: all facing×heat×lined×damper (64) with y rotation north=0, east=90, south=180, west=270 (front texture on the north face of the model).
- `bellows` (model built with nozzle pointing NORTH = -Z): `block/bellows` unpressed: nozzle (6,6,0)-(10,10,3) [`bellows_nozzle`], front board (0,0,3)-(16,16,5) [`bellows_board`], leather body (2,2,5)-(14,14,14) [`bellows_leather`], back board (0,0,14)-(16,16,16) [`bellows_board`]. `block/bellows_pressed`: leather (2,2,5)-(14,14,9), back board (0,0,9)-(16,16,11). Blockstate: facing north y=0; east y=90; south y=180; west y=270; up x=270 (nozzle points up); down x=90. `powered` does not change the model.
- `storage_jar`: body (3,0,3)-(13,11,13) [sides `storage_jar_side`, bottom `storage_jar_top`], neck (5,11,5)-(11,12,11) [`storage_jar_side`], lid (4,12,4)-(12,13,12) [`storage_jar_lid`]. Use explicit per-face uv so the body side uses uv [3,5,13,16] etc. (any consistent choice).
- `memory_jar`: same geometry as storage jar; models `block/memory_jar_fill{0..4}` differ only by the NORTH face of the body (`memory_jar_front_fill{n}`); other sides `memory_jar_side`, neck `memory_jar_rim` (gold), lid `memory_jar_top`. Blockstate: facing (y rotation as kiln) × fill.
- `sentry_chime` (built for face=floor; the mount sits on the attached face, the bell mouth points away): mount (6,0,6)-(10,2,10) [`sentry_chime_mount`], bell body (5,2,5)-(11,9,11) [`sentry_chime`], lip (4,9,4)-(12,10,12) [`sentry_chime`]. `block/sentry_chime_powered` same with `sentry_chime_powered` texture. Blockstate rotations EXACTLY like vanilla `lever.json` (face=floor: y by facing north 0/east 90/south 180/west 270; wall: x=90 + same y; ceiling: x=180 with y north 180/east 270/south 0/west 90). power>0 → powered model.
- `vigil_urn`: body (4,0,4)-(12,12,12) [sides `vigil_urn_side`, north face `vigil_urn_front`, bottom/top `vigil_urn_top`], neck (5,12,5)-(11,15,11) [`vigil_urn_side`, top `vigil_urn_top`], handles (3,6,7)-(4,10,9) and (12,6,7)-(13,10,9) [`vigil_urn_side`]. No facing (front always north).
- `reliquary_urn`: same geometry as vigil urn; textures `reliquary_urn_side`, `reliquary_urn_top`, front `reliquary_urn_front` / `reliquary_urn_front_filled`; models `block/reliquary_urn` and `block/reliquary_urn_filled`.
- `fired_tiles`, `lumen_tiles`, `ember_tile`, `fireclay_bricks`: `minecraft:block/cube_all`. Stairs/slab/wall: vanilla parents (`minecraft:block/stairs`, `inner_stairs`, `outer_stairs`, `slab`, `slab_top`, `cube_all` for double slab, `template_wall_post`, `template_wall_side`, `template_wall_side_tall`, `wall_inventory`) with vanilla-equivalent blockstates.

### 1.2 Item model definitions for block items (`assets/fireclay/items/<id>.json`)
`{"model": {"type": "minecraft:model", "model": "<model>"}}` with model: potters_wheel → a composed `fireclay:item/potters_wheel` (parent `fireclay:block/potters_wheel_base` plus head elements, or a dedicated full model); kiln → `fireclay:block/kiln_h0`; bellows → `fireclay:block/bellows`; memory_jar → `fireclay:block/memory_jar_fill0`; sentry_chime → `fireclay:block/sentry_chime`; reliquary_urn → `fireclay:block/reliquary_urn`; walls → `fireclay:block/fireclay_brick_wall_inventory`; others → their block model.

## 2. Items

| id | Java | Notes |
|---|---|---|
| `unfired_tile`, `unfired_bell`, `unfired_jar`, `unfired_urn`, `unfired_effigy` | `GreenwareItem` (tooltip "firing notes") | tag `fireclay:greenware` |
| `lapis_slip`, `glowink_slip`, `amethyst_slip`, `blaze_slip`, `echo_slip` | `Item` | tag `fireclay:glazes` |
| `kiln_shard`, `grog`, `fireclay_ball`, `fireclay_brick` | `Item` | |
| `kiln_lining` | `Item` | used on an unlined kiln |
| `sentinel_effigy` | `SentinelEffigyItem` (stacksTo 1) | awakens the sentinel |
| `pyrometer` | `Item` (stacksTo 1) | HUD reading |

Textures `assets/fireclay/textures/item/<id>.png` (16×16, transparent bg, 1px outline `#2B1D17`); models `assets/fireclay/models/item/<id>.json` parent `minecraft:item/generated`, `layer0` = `fireclay:item/<id>`; definitions `assets/fireclay/items/<id>.json`.

## 3. Entity
`terracotta_sentinel` (`TerracottaSentinel extends PathfinderMob`), sized 0.7×2.0.
Textures: `assets/fireclay/textures/entity/terracotta_sentinel.png` and `terracotta_sentinel_masterwork.png`, 64×64.
Model `SentinelModel` LayerDefinition, texture size 64×64 (standard Minecraft box UV layout: for texOffs(u,v) and box size (w,h,d): top face at (u+d, v) w×d; bottom at (u+d+w, v) w×d; then at row v+d: right side (u, v+d) d×h, front (u+d, v+d) w×h, left (u+d+w, v+d) d×h, back (u+2d+w, v+d) w×h):
- `head`: texOffs(0,0), box(-4,-8,-4, 8,8,8), pivot (0,0,0)            → UV region (0,0)-(32,16)
- `crest` (child of head): texOffs(32,0), box(-1,-11,-3, 2,3,5)          → (32,0)-(46,8)
- `body`: texOffs(16,16), box(-4,0,-2, 8,12,4), pivot (0,0,0)            → (16,16)-(40,32)
- `waist` (child of body): texOffs(0,32), box(-4.5,10,-2.5, 9,3,5)       → (0,32)-(28,40)
- `right_arm`: texOffs(40,16), box(-3,-2,-2, 4,12,4), pivot (-5,2,0)      → (40,16)-(56,32)
- `left_arm`: texOffs(32,48), box(-1,-2,-2, 4,12,4), pivot (5,2,0)        → (32,48)-(48,64)
- `right_leg`: texOffs(0,16), box(-2,0,-2, 4,12,4), pivot (-1.9,12,0)     → (0,16)-(16,32)
- `left_leg`: texOffs(16,48), box(-2,0,-2, 4,12,4), pivot (1.9,12,0)      → (16,48)-(32,64)
(Model space: y grows downward; head pivot at y=0 is the neck; feet at y=24 → entity origin. Same convention as vanilla humanoid models.)

## 4. Other assets
- GUI: `assets/fireclay/textures/gui/container/kiln.png` 256×256. Panel 176×166 at (0,0). Flame sprite 14×14 at (176,0) (lit, drawn bottom-up). Arrow sprite 24×17 at (176,14) (filled, drawn left-to-right). Slot positions (top-left of the 16×16 item area; wells drawn at x-1,y-1 18×18): greenware (38,17), glaze (62,17), fuel (50,53), output (116,35) (output well 26×26 at (111,30)). Flame indicator drawn at (50,36). Arrow at (79,34). Heat gauge frame: a 12×62 frame at (151,13) enclosing a 10×60 dark well at (152,14) (filled at runtime). Player inventory 3 rows at y=84,102,120 and hotbar y=142, x=8+18*i. Title label at (8,6), inventory label at (8,72).
- Particle: `fireclay:kiln_ember` → `assets/fireclay/particles/kiln_ember.json` = `{"textures": ["fireclay:kiln_ember_0", "fireclay:kiln_ember_1", "fireclay:kiln_ember_2"]}`, textures `assets/fireclay/textures/particle/kiln_ember_{0,1,2}.png` 8×8.
- Mod icon: `assets/fireclay/icon.png` 128×128 (a glowing kiln mouth with a glazed jar silhouette).

## 5. Sounds (`assets/fireclay/sounds.json`, files under `assets/fireclay/sounds/`, mono OGG Vorbis 44.1 kHz)
| event id | files | subtitle key |
|---|---|---|
| `block.potters_wheel.throw` | `fireclay:block/potters_wheel/throw1..3` | `subtitles.fireclay.block.potters_wheel.throw` |
| `block.bellows.pump` | `fireclay:block/bellows/pump1..2` | `subtitles.fireclay.block.bellows.pump` |
| `block.kiln.roar` | `fireclay:block/kiln/roar1..2` | `subtitles.fireclay.block.kiln.roar` |
| `block.kiln.overheat` | `fireclay:block/kiln/overheat1..2` | `subtitles.fireclay.block.kiln.overheat` |
| `block.kiln.crack` | `fireclay:block/kiln/crack1..3` | `subtitles.fireclay.block.kiln.crack` |
| `block.kiln.ping` | `fireclay:block/kiln/ping1..3` | `subtitles.fireclay.block.kiln.ping` |
| `block.kiln.masterwork` | `fireclay:block/kiln/masterwork` | `subtitles.fireclay.block.kiln.masterwork` |
| `entity.terracotta_sentinel.awaken` | `fireclay:entity/terracotta_sentinel/awaken` | `subtitles.fireclay.entity.terracotta_sentinel.awaken` |
Java registers each as `SoundEvent.createVariableRangeEvent(Fireclay.id("<event id>"))` in `FireclaySounds`.

## 6. Other registry ids
- Block entity types: `kiln`, `potters_wheel`, `storage_jar`, `memory_jar`, `reliquary_urn`.
- Menu type: `kiln`.
- Recipe type + serializer: `firing` (JSON `"type": "fireclay:firing"`; fields `greenware` (Ingredient), optional `glaze` (Ingredient), `min_heat` int, `max_heat` int, `time` int, `result` (ItemStackTemplate / whatever the 26.3 recipe-result codec is)).
- Data components: `masterwork`, `stored_xp`, `sentinel_vitals`, `reliquary_charges`.
- Attachments: `potter_record` (player, persistent, copyOnDeath), `bake_progress` (ItemEntity, transient int).
- Payload: `kiln_reading` (S2C).
- Criterion trigger: `potter_action`.
- Particle type: `kiln_ember`.
- Game rules: `kiln_crack_grace` (int 40, 1..1200), `reliquary_urns` (bool true), `max_sentinels_per_player` (int 4).
- Creative tab: `fireclay`.
- Item tags: `greenware`, `glazes`, `kiln_fuels`, `kiln_fuel/kindling`, `kiln_fuel/wood`, `kiln_fuel/coal`, `kiln_fuel/coal_block`, `kiln_fuel/lava`, `kiln_fuel/blaze`, `masterwork_eligible`, `storage_jar_blacklist`.
- Entity type tags: `vigil_immune`, `sentinel_ignores`.
- Saved data: `fireclay_reliquaries`, `fireclay_sentinels`.
- HUD element id: `pyrometer`.

## 7. Translation keys (Java must use exactly these; lang/en_us.json must define them)
Blocks `block.fireclay.<id>`, items `item.fireclay.<id>`, entity `entity.fireclay.terracotta_sentinel`, creative tab `itemGroup.fireclay`, container title `container.fireclay.kiln`, subtitles (§5), advancements `advancements.fireclay.<id>.title|description`, game rules `gamerule.fireclay.<rule_id>` (whatever key format the 26.3 game rule API derives — check), primer pages `book.fireclay.primer.p1..p10`, tooltips/messages under `tooltip.fireclay.*`, `message.fireclay.*`, `command.fireclay.*`, `hud.fireclay.*`.
