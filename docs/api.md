# Fireclay cross-feature Java API (binding)

Written by the foundation agent. This is the single source of truth for the parallel feature agents.
Priority on conflict: `contract.md` (ids, names, blockstates, geometry, keys) > this file (Java API) > `design.md`.

All main-side classes are in `fireclay/src/main/java/io/github/eternaldesires/fireclay/`, all client classes in
`fireclay/src/client/java/io/github/eternaldesires/fireclay/client/`. Package names below are relative to
`io.github.eternaldesires.fireclay`.

Rules for everyone:
- **Keep every public signature listed here.** You may add members, fields, overrides and private helpers to the
  files you own; you may not rename, remove or change the parameter/return types of a listed member.
- **Never edit a file you do not own.** If you need something new from another feature, implement it on your side
  (e.g. read the other block's blockstate) instead of editing their class. Foundation files (`registry/**`,
  `component/**`, `attachment/**`, `network/**`, `advancement/**`, `Fireclay.java`, `FireclayClient.java`) are
  frozen; if one is wrong, say so in your summary rather than editing it.
- Put your event registrations in your feature hook (§9). Nobody edits `Fireclay.java` or `FireclayClient.java`.
- Every vanilla/Fabric symbol must be checked against the 26.3 javap dump / fabric-api sources (see the task).

---

## 1. File ownership

| Owner | Files (create or replace freely; keep the listed API) |
|---|---|
| foundation (frozen) | `Fireclay.java`; `registry/*` (15 classes); `component/{Masterwork,StoredXp,SentinelVitals,ReliquaryCharges}`; `attachment/PotterRecord`; `network/KilnReadingPayload`; `advancement/PotterActionTrigger`; client `FireclayClient` |
| kiln | `kiln/{KilnBlock,KilnBlockEntity,KilnMenu,FiringRecipe,FiringInput,KilnFeature}`; client `client/screen/KilnScreen`, `client/KilnClient` |
| workshop | `block/{PottersWheelBlock,PottersWheelBlockEntity,BellowsBlock,WorkshopFeature}`, `item/GreenwareItem`, `primer/PottersPrimer` |
| ceramics | `block/{StorageJarBlock,StorageJarBlockEntity,MemoryJarBlock,MemoryJarBlockEntity,SentryChimeBlock,VigilUrnBlock,EmberTileBlock,ReliquaryUrnBlock,ReliquaryUrnBlockEntity,CeramicsFeature}`, `item/MasterworkBlockItem`; new SavedData `fireclay_reliquaries` class in `block/` (your choice of name) |
| sentinel | `entity/{TerracottaSentinel,SentinelFeature}`, `item/SentinelEffigyItem`; client `client/entity/{SentinelModel,SentinelRenderer,SentinelRenderState}`, `client/SentinelClient`; new goal classes and the SavedData `fireclay_sentinels` in `entity/` |
| systems | `command/{FireclayCommand,SystemsFeature}`; client `client/hud/PyrometerHud`, `client/particle/KilnEmberParticle`, `client/SystemsClient` |

New classes you add go into your own package (`kiln`, `block`, `item`, `entity`, `command`, `primer`, and the client
subpackages above). Pyrometer is a plain `Item` (no `PyrometerItem` class); its behaviour is the systems feature's
server-tick sender. No mixins are used by the foundation; adding one needs `fireclay.mixins.json` plus a
`fabric.mod.json` entry, which is a shared-file edit — avoid it.

---

## 2. Registries (`registry/`, all frozen, all fully implemented)

Static fields register on class load; `Fireclay#onInitialize` calls each `init()` in this order:
`FireclayComponents, FireclaySounds, FireclayParticles, FireclayBlocks, FireclayItems, FireclayBlockEntities,
FireclayMenus, FireclayRecipes, FireclayEntities, FireclayAttachments, FireclayCriteria, FireclayGameRules,
FireclayNetworking, FireclayCreativeTab`, then the feature hooks `KilnFeature, WorkshopFeature, CeramicsFeature,
SentinelFeature, SystemsFeature` (each `public static void register()`).

### `FireclayBlocks` (contract §1)
`POTTERS_WHEEL: PottersWheelBlock`, `KILN: KilnBlock`, `BELLOWS: BellowsBlock`, `FIRED_TILES: Block`,
`FIRED_TILE_STAIRS: StairBlock`, `FIRED_TILE_SLAB: SlabBlock`, `LUMEN_TILES: Block`, `STORAGE_JAR: StorageJarBlock`,
`MEMORY_JAR: MemoryJarBlock`, `SENTRY_CHIME: SentryChimeBlock`, `VIGIL_URN: VigilUrnBlock`, `EMBER_TILE: EmberTileBlock`,
`RELIQUARY_URN: ReliquaryUrnBlock`, `FIRECLAY_BRICKS: Block`, `FIRECLAY_BRICK_STAIRS: StairBlock`,
`FIRECLAY_BRICK_SLAB: SlabBlock`, `FIRECLAY_BRICK_WALL: WallBlock`.

Properties set at registration (do not re-specify in the block class):

| Block | strength | sound | tool required | light | noOcclusion |
|---|---|---|---|---|---|
| potters_wheel | 2.0 | WOOD | no (axe tag) | – | yes |
| kiln | 3.5 | STONE | yes (pickaxe) | `KilnBlock::lightLevel` → 0/7/11/15 by `heat` | no |
| bellows | 1.5 | WOOD | no (axe tag) | – | yes |
| fired_tiles, stairs, slab, lumen_tiles, ember_tile | 1.5 / 6.0 | STONE | yes | lumen 15, ember 7 | no |
| storage_jar, memory_jar, vigil_urn | 1.0 | DECORATED_POT | no | memory `MemoryJarBlock::lightLevel` = fill×2; vigil 15 | yes |
| sentry_chime | 1.0 | AMETHYST | no | – | yes |
| reliquary_urn | 2.0 | DECORATED_POT | no | – | yes |
| fireclay_bricks family | 2.0 / 6.0 | STONE | yes | – | no (wall also `forceSolidOn`) |

The `mineable/pickaxe|axe` block tags are data JSON (design §3.17).

### `FireclayItems` (contract §2)
Block items (same id; `useBlockDescriptionPrefix`, so the name key is `block.fireclay.<id>`): `POTTERS_WHEEL, KILN,
BELLOWS, FIRED_TILES, FIRED_TILE_STAIRS, FIRED_TILE_SLAB, LUMEN_TILES, FIRECLAY_BRICKS, FIRECLAY_BRICK_STAIRS,
FIRECLAY_BRICK_SLAB, FIRECLAY_BRICK_WALL` (`BlockItem`), `STORAGE_JAR` (`BlockItem`, stacksTo 1),
`MEMORY_JAR` (`MasterworkBlockItem`, stacksTo 1), `SENTRY_CHIME`, `VIGIL_URN`, `EMBER_TILE` (`MasterworkBlockItem`),
`RELIQUARY_URN` (`MasterworkBlockItem`, stacksTo 1, UNCOMMON).
Items (`item.fireclay.<id>`): `UNFIRED_TILE, UNFIRED_BELL, UNFIRED_JAR, UNFIRED_URN, UNFIRED_EFFIGY` (`GreenwareItem`);
`LAPIS_SLIP, GLOWINK_SLIP, AMETHYST_SLIP, BLAZE_SLIP, ECHO_SLIP, KILN_SHARD, GROG, FIRECLAY_BALL, FIRECLAY_BRICK,
KILN_LINING` (`Item`); `SENTINEL_EFFIGY` (`SentinelEffigyItem`, stacksTo 1, RARE); `PYROMETER` (`Item`, stacksTo 1).

Jars and urns stack to 1 because they carry contents/XP/charges as components (stacking would duplicate them).

### `FireclayBlockEntities` (contract §6)
`KILN: BlockEntityType<KilnBlockEntity>`, `POTTERS_WHEEL: <PottersWheelBlockEntity>`, `STORAGE_JAR: <StorageJarBlockEntity>`,
`MEMORY_JAR: <MemoryJarBlockEntity>`, `RELIQUARY_URN: <ReliquaryUrnBlockEntity>`. Built with
`FabricBlockEntityTypeBuilder` (the vanilla constructor is only widened for Fabric API itself). Each BE class must keep
the constructor `(BlockPos, BlockState)`.

### `FireclayMenus`
`KILN: MenuType<KilnMenu>` = `new MenuType<>(KilnMenu::new, FeatureFlags.VANILLA_SET)` → `KilnMenu` must keep the
public client constructor `(int containerId, Inventory inventory)`.

### `FireclayRecipes`
`FIRING: RecipeType<FiringRecipe>` and `FIRING_SERIALIZER: RecipeSerializer<FiringRecipe>` =
`new RecipeSerializer<>(FiringRecipe.CODEC, FiringRecipe.STREAM_CODEC)` (in 26.3 `RecipeSerializer` is a final record
`(MapCodec<T>, StreamCodec<RegistryFriendlyByteBuf, T>)`, so there is no serializer class).
Lookup (server only; recipes are not synced): `serverLevel.recipeAccess().getRecipeFor(FireclayRecipes.FIRING, input, level)`
→ `Optional<RecipeHolder<FiringRecipe>>`; `holder.id()` is a `ResourceKey<Recipe<?>>`; `recipeAccess().byKey(key)`.
Or `RecipeManager.createCheck(FireclayRecipes.FIRING)` (`RecipeManager.CachedCheck`).

### `FireclayComponents` (contract §6)
`MASTERWORK: DataComponentType<Masterwork>`, `STORED_XP: <StoredXp>`, `SENTINEL_VITALS: <SentinelVitals>`,
`RELIQUARY_CHARGES: <ReliquaryCharges>`. All persistent + network-synced. Their tooltips are already registered
(`ItemComponentTooltipProviderRegistry.addLast`, common side) — do not add tooltip code for these components.

### `FireclayAttachments` (contract §6)
- `POTTER_RECORD: AttachmentType<PotterRecord>` — players, persistent (`PotterRecord.CODEC`), `copyOnDeath`, initializer
  `PotterRecord.EMPTY`. Not synced to clients (server-side only). Use the helpers in §3.3.
- `BAKE_PROGRESS: AttachmentType<Integer>` — transient (not saved), for `ItemEntity`s on an Ember Tile. Fabric
  `AttachmentTarget` methods on any `Entity`: `getAttachedOrElse(type, 0)`, `setAttached(type, value)`,
  `removeAttached(type)`.

### `FireclaySounds` (contract §5) — all `SoundEvent`
`POTTERS_WHEEL_THROW` (block.potters_wheel.throw), `BELLOWS_PUMP` (block.bellows.pump), `KILN_ROAR`, `KILN_OVERHEAT`,
`KILN_CRACK`, `KILN_PING`, `KILN_MASTERWORK` (block.kiln.*), `SENTINEL_AWAKEN` (entity.terracotta_sentinel.awaken).
Play server-side with `level.playSound(null, pos, FireclaySounds.X, SoundSource.BLOCKS, volume, pitch)`.

### `FireclayParticles`
`KILN_EMBER: SimpleParticleType` (registered on both sides). Server: `serverLevel.sendParticles(FireclayParticles.KILN_EMBER,
x, y, z, count, dx, dy, dz, speed)`; client `animateTick`: `level.addParticle(FireclayParticles.KILN_EMBER, x, y, z, vx, vy, vz)`.

### `FireclayEntities` (contract §3)
`TERRACOTTA_SENTINEL_KEY: ResourceKey<EntityType<?>>`, `TERRACOTTA_SENTINEL: EntityType<TerracottaSentinel>`
(`MobCategory.MISC`, 0.7×2.0, fireImmune, tracking range 10). Default attributes from
`TerracottaSentinel.createAttributes()` are registered in `init()` (keep that static method).

### `FireclayCriteria`
`POTTER_ACTION: PotterActionTrigger` (registered in `BuiltInRegistries.TRIGGER_TYPES` as `fireclay:potter_action`).

### `FireclayGameRules` (all `GameRuleCategory.MISC`)
| Field | Id | Type / default | Helper |
|---|---|---|---|
| `KILN_CRACK_GRACE` | `fireclay:kiln_crack_grace` | `GameRule<Integer>` 40, range 1..1200 | `FireclayGameRules.crackGrace(ServerLevel): int` |
| `RELIQUARY_URNS` | `fireclay:reliquary_urns` | `GameRule<Boolean>` true | `reliquaryUrns(ServerLevel): boolean` |
| `MAX_SENTINELS_PER_PLAYER` | `fireclay:max_sentinels_per_player` | `GameRule<Integer>` 4, min 0 | `maxSentinelsPerPlayer(ServerLevel): int` |

Set in tests: `level.getGameRules().set(FireclayGameRules.KILN_CRACK_GRACE, 400, level.getServer())`.

### `FireclayTags`
`FireclayTags.Items.{GREENWARE, GLAZES, KILN_FUELS, KILN_FUEL_KINDLING, KILN_FUEL_WOOD, KILN_FUEL_COAL,
KILN_FUEL_COAL_BLOCK, KILN_FUEL_LAVA, KILN_FUEL_BLAZE, MASTERWORK_ELIGIBLE, STORAGE_JAR_BLACKLIST}` (`TagKey<Item>`,
ids `fireclay:greenware`, …, `fireclay:kiln_fuel/blaze`, …) and `FireclayTags.EntityTypes.{VIGIL_IMMUNE,
SENTINEL_IGNORES}` (`TagKey<EntityType<?>>`). Test with `stack.is(tag)` / `entity.is(tag)` (`EntityType` has no `is(TagKey)`
in 26.3; `Entity` implements `TypedInstance`). Note the nested class names shadow vanilla `Items`/`EntityTypes`: always
write `FireclayTags.Items.GREENWARE`.

### `FireclayCreativeTab`
`KEY`, `TAB` (`fireclay:fireclay`, title `itemGroup.fireclay`, icon kiln). Already lists every item plus a Primer
copy; do not add items elsewhere.

### `FireclayNetworking`
Registers `KilnReadingPayload` with `PayloadTypeRegistry.clientboundPlay()` (common side). Server send:
`ServerPlayNetworking.send(serverPlayer, kiln.reading())`.

---

## 3. Shared data types (frozen)

### 3.1 Components (`component/`)
All are records implementing `net.minecraft.world.item.component.TooltipProvider`, each with `CODEC` and `STREAM_CODEC`.

| Record | JSON form | Helpers | Tooltip key(s) |
|---|---|---|---|
| `Masterwork(Optional<String> potter)` | `{"potter": "Alex"}` or `{}` | `Masterwork.UNSIGNED`; `Masterwork.signedBy(@Nullable String name)` (null/empty → UNSIGNED); `Masterwork.isMasterwork(ItemStack): boolean`; `Masterwork.get(ItemStack): Optional<Masterwork>` | `tooltip.fireclay.masterwork` (gold, "★ Masterwork"); `tooltip.fireclay.masterwork.potter` (%s = name; gray italic, "Fired by %s") |
| `StoredXp(int points)` | bare int, e.g. `300` | `StoredXp.EMPTY` | `tooltip.fireclay.stored_xp` (%s = points; "Holds %s XP") |
| `SentinelVitals(float health)` | bare float, e.g. `34.0` | `BASE_MAX_HEALTH = 60`, `MASTERWORK_MAX_HEALTH = 90`, `SentinelVitals.maxHealth(boolean masterwork): float` | `tooltip.fireclay.sentinel_vitals` (%s = current, %s = max; "Health %s / %s") |
| `ReliquaryCharges(int charges)` | bare positive int | – | `tooltip.fireclay.reliquary_charges` (%s; "Holds %s more passings") |

Usage: `stack.set(FireclayComponents.MASTERWORK, Masterwork.signedBy(kiln.getStokerName()))`,
`stack.get(FireclayComponents.STORED_XP)` (nullable), `stack.getOrDefault(FireclayComponents.STORED_XP, StoredXp.EMPTY)`.
Blockstate-only pieces (chime, vigil, ember) re-apply `Masterwork.UNSIGNED` on drop.
`MasterworkBlockItem#isFoil` and `SentinelEffigyItem#isFoil` already return `Masterwork.isMasterwork(stack) || super`.

### 3.2 Kiln reading payload (`network/KilnReadingPayload`)
`record KilnReadingPayload(BlockPos pos, int heat, int cap, int window, int progress, int flags) implements CustomPacketPayload`
- `TYPE` (`fireclay:kiln_reading`), `STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, KilnReadingPayload>` (6 fields).
- `window = min << 16 | max`, `progress = progress << 16 | total`; both 0 without a recipe.
- `KilnReadingPayload.of(pos, heat, cap, windowMin, windowMax, progress, total, flags)` packs; accessors
  `hasRecipe()`, `windowMin()`, `windowMax()`, `progressTicks()`, `totalTicks()`, `hasFlag(int)`.
- `flags` uses the `KilnMenu.FLAG_*` bits (§4.3).
- G23 round trip: `new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess())`, `STREAM_CODEC.encode/decode`.

### 3.3 Potter record (`attachment/PotterRecord`)
`record PotterRecord(boolean primerGiven, int firings, int masterworks, int cracks)`; `EMPTY`; `CODEC`
(`primer_given`, `firings`, `masterworks`, `cracks`); withers `withPrimerGiven(boolean)`, `withFirings(int)`,
`withMasterworks(int)`, `withCracks(int)`. Static helpers (server side):
- `PotterRecord.get(Player): PotterRecord` (never null)
- `PotterRecord.update(Player, UnaryOperator<PotterRecord>): PotterRecord` (returns the new value)
- `PotterRecord.markPrimerGiven(Player)`
- `PotterRecord.recordFiring(ServerPlayer)` — +1 firing; at exactly 100 it fires `hundred_fires` itself
- `PotterRecord.recordMasterwork(ServerPlayer)`, `PotterRecord.recordCrack(ServerPlayer)`

The kiln calls `recordFiring` on every completion, `recordMasterwork` on masterwork completions and `recordCrack` on
cracks, for the stoker (if online).

### 3.4 Criterion (`advancement/PotterActionTrigger`)
`class PotterActionTrigger extends SimpleCriterionTrigger<PotterActionTrigger.TriggerInstance>`
- `trigger(ServerPlayer player, String action)` (item EMPTY, masterwork false)
- `trigger(ServerPlayer player, String action, ItemStack item, boolean masterwork)`
- `record TriggerInstance(Optional<Holder<LootItemCondition>> player, String action, Optional<ItemPredicate> item, Optional<Boolean> masterwork)`

JSON conditions (only `action` is required; an `item` predicate never matches an empty stack):
```json
"criteria": {
  "fired_memory_jar": {
    "trigger": "fireclay:potter_action",
    "conditions": { "action": "fired", "item": { "items": "fireclay:memory_jar" } }
  }
}
"conditions": { "action": "awakened", "masterwork": true }
```

Action constants (`PotterActionTrigger.X`) and who fires them, to whom:

| Constant | String | Fired by | Player | item / masterwork args | Advancements |
|---|---|---|---|---|---|
| `THREW` | `threw` | workshop: wheel completes a piece (5th throw) | thrower | the greenware stack / false | root (Wet Hands) |
| `FIRED` | `fired` | kiln: completion | stoker | the output stack / its masterwork flag | trial_by_fire; lapis_remembers (memory_jar), amethyst_listens (sentry_chime), glow_keeps_vigil (vigil_urn), echo_holds_a_soul (reliquary_urn) |
| `CRACKED` | `cracked` | kiln: crack | stoker | – | every_pot_has_its_crack |
| `PUMPED` | `pumped` | workshop: manual bellows pump when `kiln.pump(...)` returned true | pumping player | – | hot_air |
| `MASTERWORK` | `masterwork` | kiln: masterwork completion | stoker | output / true | sweet_spot |
| `THERMOSTAT` | `thermostat` | kiln: masterwork with manualPumps == 0 and redstone pumps + damper toggles > 0 | all players ≤ 16 blocks | output / true | thermostat |
| `LINED` | `lined` | kiln: lining installed | player | – | lined_with_failure |
| `WHITE_HEAT` | `white_heat` | kiln: heat ≥ 950, first time per firing | stoker | – | white_heat |
| `AWAKENED` | `awakened` | sentinel: awakening finishes (or starts; pick one and keep it) | owner | effigy stack / masterwork | terracotta_army; masterpiece_guardian (`masterwork: true`) |
| `PYROMETER_READ` | `pyrometer_read` | systems: every reading sent (granting is once) | reader | – | read_the_fire |
| `EMBER_BAKED` | `ember_baked` | ceramics: ember tile smelts | players ≤ 8 blocks | result stack / false | blaze_holds_heat |
| `RELIQUARY_SAVED` | `reliquary_saved` | ceramics: death captured (fire on respawn) | owner | – | memento_mori |
| `HUNDRED_FIRES` | `hundred_fires` | `PotterRecord.recordFiring` (automatic) | stoker | – | hundred_fires |

Fire through `FireclayCriteria.POTTER_ACTION.trigger(...)`. Players within a radius:
`PlayerLookup.around(serverLevel, Vec3.atCenterOf(pos), radius)` (fabric-networking-api-v1).

---

## 4. Kiln API (owner: kiln)

### 4.1 `kiln.KilnBlock extends BaseEntityBlock`
- `FACING = BlockStateProperties.HORIZONTAL_FACING` (front/mouth), `HEAT: IntegerProperty 0..3`, `LINED`, `DAMPER: BooleanProperty`.
- `static int lightLevel(BlockState)` → 0/7/11/15 by HEAT (used by FireclayBlocks).
- Placement: FACING = player's horizontal direction opposite (mouth faces the player).
- `getTicker` returns `KilnBlockEntity::serverTick` on the server only.
- Other features read state only: `state.is(FireclayBlocks.KILN)`, `state.getValue(KilnBlock.LINED)` etc.

### 4.2 `kiln.KilnBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer`
Constants: `SLOT_GREENWARE = 0, SLOT_GLAZE = 1, SLOT_FUEL = 2, SLOT_OUTPUT = 3, SLOT_COUNT = 4`,
`AMBIENT_HEAT = 20.0`, `CAP_UNLINED = 750`, `CAP_LINED = 1000`, `PUMP_HEAT = 35.0`, `PUMP_BURN_COST = 20`.

| Member | Used by | Contract |
|---|---|---|
| `KilnBlockEntity(BlockPos, BlockState)` | registry | |
| `static void serverTick(Level, BlockPos, BlockState, KilnBlockEntity)` | ticker, gametests (called in a loop) | one simulation tick (§2.2 heat model) |
| `boolean pump(ServerLevel level, @Nullable Player stoker)` | workshop (bellows) | while burning: +35 (clamp cap), −20 burn, counts a manual pump (and records `stoker`) or a redstone pump (`stoker == null`), returns true; not burning: ash puff, returns false. The bellows handles its own sound/particles/`pressed`/criterion |
| `double getHeat()` / `void setHeat(double)` | systems (command), tests, screenshots | setHeat clamps to [20, cap] and marks dirty |
| `int getCap()`, `boolean isLined()`, `boolean isDamperOpen()` | command, payload | lined/damper read from the blockstate |
| `boolean isBurning()`, `int getBurn()`, `int getBurnMax()` | command, tests | |
| `boolean hasRecipe()`, `int getWindowMin()`, `int getWindowMax()`, `int getTotal()` | command, payload | 0s without a recipe; server-side cached from the matched recipe |
| `int getProgress()`, `int getSweet()`, `int getOver()` | command, tests | |
| `int getComparatorSignal()` | KilnBlock comparator, command, G9 | window-relative 0..15 |
| `int getFlags()` | menu, payload | `KilnMenu.FLAG_*` bits |
| `void setStoker(Player)`, `@Nullable String getStokerName()` | kiln (on use), workshop (manual pump via `pump`) | |
| `ContainerData getContainerData()` | menu | 9 ints, §4.3 |
| `KilnReadingPayload reading()` | systems (pyrometer sender) | |
| `Container` methods (`getItem/setItem/removeItem…`) | tests, hoppers | slot indices above |

### 4.3 `kiln.KilnMenu extends AbstractContainerMenu`
- Constructors: client `(int containerId, Inventory)`; server `(int containerId, Inventory, Container kiln, ContainerData data)`.
- Slots: menu 0 greenware (38,17), 1 glaze (62,17), 2 fuel (50,53), 3 output (116,35); then player inventory at (8,84)
  (`INVENTORY_START = 4`, `INVENTORY_END = 40`).
- ContainerData indices: `DATA_HEAT 0, DATA_CAP 1, DATA_WINDOW_MIN 2, DATA_WINDOW_MAX 3, DATA_PROGRESS 4, DATA_TOTAL 5,
  DATA_BURN 6, DATA_BURN_MAX 7, DATA_FLAGS 8` (`DATA_COUNT = 9`). Values ≤ 32767 (synced as shorts).
- Flag bits: `FLAG_OVER 1, FLAG_IN_SWEET 2, FLAG_DAMPER 4, FLAG_LINED 8, FLAG_MASTERWORK_ON_TRACK 16`.
- Getters for the screen: `getHeat(), getCap(), getWindowMin(), getWindowMax(), hasRecipe(), getProgress(), getTotal(),
  getBurn(), getBurnMax(), getFlags(), hasFlag(int), getData(int)`.

### 4.4 Recipe
- `kiln.FiringInput(ItemStack greenware, ItemStack glaze) implements RecipeInput` (fully implemented).
- `kiln.FiringRecipe implements Recipe<FiringInput>` (fully implemented): `greenware(): Ingredient`,
  `glaze(): Optional<Ingredient>`, `minHeat()`, `maxHeat()`, `time()`, `result(): ItemStackTemplate`, `sweetMin()`,
  `sweetMax()`, `CODEC: MapCodec`, `STREAM_CODEC`. `matches` implements the glaze rule (bare recipes need an empty glaze
  slot). `assemble(input)` → `result.create()`.
- JSON: `{"type": "fireclay:firing", "greenware": "fireclay:unfired_jar", "glaze": "fireclay:lapis_slip", "min_heat": 540,
  "max_heat": 680, "time": 400, "result": {"id": "fireclay:memory_jar"}}` (`glaze` optional; `result` may add `"count": 4`).

### 4.5 Client
`client.screen.KilnScreen extends AbstractContainerScreen<KilnMenu>`, constructor `(KilnMenu, Inventory, Component)`,
`TEXTURE = fireclay:textures/gui/container/kiln.png`. Registered by `client.KilnClient.register()` with
`MenuScreens.register(FireclayMenus.KILN, KilnScreen::new)`. Draw in `extractBackground(GuiGraphicsExtractor, int, int, float)`
(do not call it yourself; `imageWidth/imageHeight` are final, default 176×166).

---

## 5. Workshop API (owner: workshop)
- `block.PottersWheelBlock extends BaseEntityBlock`: `CLAY: IntegerProperty 0..4`, `FIRECLAY`, `SPINNING: BooleanProperty`.
- `block.PottersWheelBlockEntity extends BlockEntity` `(BlockPos, BlockState)`.
- `block.BellowsBlock extends Block`: `FACING = BlockStateProperties.FACING` (nozzle direction), `POWERED =
  BlockStateProperties.POWERED`, `PRESSED: BooleanProperty`. Faced kiln: `level.getBlockEntity(pos.relative(state.getValue(FACING)))
  instanceof KilnBlockEntity kiln` → `kiln.pump(serverLevel, playerOrNull)`.
- `item.GreenwareItem extends Item` `(Item.Properties)`; firing-notes tooltip keys `tooltip.fireclay.<id>.notes`
  (`unfired_tile`, `unfired_bell`, `unfired_jar`, `unfired_urn`, `unfired_effigy`).
- `primer.PottersPrimer` (implemented): `TITLE`, `AUTHOR`, `PAGES = 10`; `static ItemStack create()` (a
  `minecraft:written_book` with `WRITTEN_BOOK_CONTENT`, pages `book.fireclay.primer.p1..p10`);
  `static void giveTo(Player)` (inventory or drop, then `PotterRecord.markPrimerGiven`). The wheel calls `giveTo` on first
  placement when `!PotterRecord.get(player).primerGiven()`; `/fireclay primer` calls `giveTo` unconditionally.

## 6. Ceramics API (owner: ceramics)
- `block.StorageJarBlock extends BaseEntityBlock`; `SHAPE` = box(3,0,3,13,13,13) (shared by the memory jar).
- `block.StorageJarBlockEntity extends BaseContainerBlockEntity`; `SLOT_COUNT = 9`; menu `new DispenserMenu(id, inv, this)`.
- `block.MemoryJarBlock extends BaseEntityBlock`: `FACING` (horizontal), `FILL: IntegerProperty 0..4`, `MASTERWORK`;
  `static int lightLevel(BlockState)` = fill×2.
- `block.MemoryJarBlockEntity extends BlockEntity`: `CAPACITY = 1395`, `MASTERWORK_CAPACITY = 4185`, `int getXp()`,
  `void setXp(int)` (clamps), `boolean isMasterwork()`, `int getCapacity()` — used by G13/G14.
- `block.SentryChimeBlock extends FaceAttachedHorizontalDirectionalBlock`: `FACE`, `FACING` (inherited), `POWER =
  BlockStateProperties.POWER`, `MASTERWORK`.
- `block.VigilUrnBlock extends Block`: `MASTERWORK`; `SHAPE` = box(3,0,3,13,15,13) (shared by the reliquary urn).
- `block.EmberTileBlock extends Block`: `MASTERWORK`.
- `block.ReliquaryUrnBlock extends BaseEntityBlock`: `FILLED`, `MASTERWORK`; `block.ReliquaryUrnBlockEntity extends BlockEntity`.
- `item.MasterworkBlockItem extends BlockItem` `(Block, Item.Properties)`, `isFoil` implemented.
- Every `MASTERWORK` property is set from the item in `getStateForPlacement` via `Masterwork.isMasterwork(context.getItemInHand())`.

## 7. Sentinel API (owner: sentinel)
- `entity.TerracottaSentinel extends PathfinderMob`: constructor `(EntityType<? extends TerracottaSentinel>, Level)`;
  `AWAKEN_TICKS = 60`; `static AttributeSupplier.Builder createAttributes()`; `int getAwaken()` (60 → 0),
  `boolean isMasterwork()`, `int getAttackAnimationTick()`.
- `item.SentinelEffigyItem extends Item` `(Item.Properties)`; `isFoil` implemented.
- Client: `client.entity.SentinelRenderState extends LivingEntityRenderState` (`awaken`, `masterwork`, `attackAnim`);
  `client.entity.SentinelModel extends EntityModel<SentinelRenderState>` with `LAYER = new ModelLayerLocation(fireclay:terracotta_sentinel, "main")`
  and `static LayerDefinition createBodyLayer()` (contract §3 geometry, already written);
  `client.entity.SentinelRenderer extends MobRenderer<TerracottaSentinel, SentinelRenderState, SentinelModel>`
  `(EntityRendererProvider.Context)`, textures `TEXTURE`, `TEXTURE_MASTERWORK`.
  `client.SentinelClient.register()` does `ModelLayerRegistry.registerModelLayer(SentinelModel.LAYER, SentinelModel::createBodyLayer)`
  and `EntityRenderers.register(FireclayEntities.TERRACOTTA_SENTINEL, SentinelRenderer::new)`.

## 8. Systems API (owner: systems)
- `command.FireclayCommand.register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)`,
  wired by `SystemsFeature.register()` through `CommandRegistrationCallback.EVENT`. Root `/fireclay` requires
  `source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)`.
- `client.hud.PyrometerHud`: `ID = fireclay:pyrometer`; `static void onReading(KilnReadingPayload, ClientPlayNetworking.Context)`;
  `static void extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.
- `client.particle.KilnEmberParticle extends SingleQuadParticle`, `record Provider(SpriteSet) implements ParticleProvider<SimpleParticleType>`.
- `client.SystemsClient.register()` already registers: the particle provider
  (`ParticleProviderRegistry.getInstance().register(FireclayParticles.KILN_EMBER, KilnEmberParticle.Provider::new)`),
  the payload receiver (`ClientPlayNetworking.registerGlobalReceiver(KilnReadingPayload.TYPE, PyrometerHud::onReading)`)
  and the HUD element (`HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, PyrometerHud.ID, PyrometerHud::extractRenderState)`).
- The pyrometer sender (`ServerTickEvents.END_SERVER_TICK`), loot injection and any other systems events go in `SystemsFeature.register()`.

## 9. Feature hooks
Main (called from `Fireclay#onInitialize`, after all registries): `kiln.KilnFeature.register()`,
`block.WorkshopFeature.register()`, `block.CeramicsFeature.register()`, `entity.SentinelFeature.register()`,
`command.SystemsFeature.register()`.
Client (called from `FireclayClient#onInitializeClient`): `client.KilnClient.register()`, `client.SentinelClient.register()`,
`client.SystemsClient.register()`. Client code may reference main classes; main code must never reference client classes.

---

## 10. Translation keys used by Java

Keys the foundation/stub code already uses (lang must define them):

| Key | Where | English |
|---|---|---|
| `block.fireclay.<id>` (all 17 blocks) | block + block item names | contract §1 |
| `item.fireclay.<id>` (all 17 items) | item names | contract §2 |
| `entity.fireclay.terracotta_sentinel` | entity name | Terracotta Sentinel |
| `itemGroup.fireclay` | creative tab | Fireclay |
| `container.fireclay.kiln` | kiln title (`KilnBlockEntity#getDefaultName`) | Kiln |
| `block.fireclay.storage_jar` | storage jar container title | Storage Jar |
| `tooltip.fireclay.masterwork` | Masterwork | ★ Masterwork |
| `tooltip.fireclay.masterwork.potter` | Masterwork (%s) | Fired by %s |
| `tooltip.fireclay.stored_xp` | StoredXp (%s) | Holds %s XP |
| `tooltip.fireclay.sentinel_vitals` | SentinelVitals (%s, %s) | Health %s / %s |
| `tooltip.fireclay.reliquary_charges` | ReliquaryCharges (%s) | Holds %s more passings |
| `book.fireclay.primer.p1` … `p10` | PottersPrimer pages | design §3.3 outline |
| `gamerule.fireclay.kiln_crack_grace` (+ `.description`) | game rule screen | Kiln crack grace (ticks) |
| `gamerule.fireclay.reliquary_urns` (+ `.description`) | | Reliquary Urns catch belongings |
| `gamerule.fireclay.max_sentinels_per_player` (+ `.description`) | | Max Sentinels per player |
| `subtitles.fireclay.*` | sounds.json (contract §5) | |
| `advancements.fireclay.<id>.title|description` | advancement JSON | design §3.14 |

The game-rule key is `GameRule#getDescriptionId()`; vanilla 26.3 uses `gamerule.<namespace>.<path>` for namespaced rules
(e.g. `gamerule.minecraft.raids` in the vanilla lang), so the keys above follow that.

Keys reserved for the features (add to lang when you use them; stay inside these prefixes):
- workshop: `tooltip.fireclay.<greenware id>.notes`; wheel actionbar `message.fireclay.wheel.*` (e.g. `no_mix`, `shaping`
  (%s shape, %s cost, %s loaded), `needs` (%s)); greenware shape names may reuse `item.fireclay.unfired_*`.
- ceramics: `message.fireclay.memory_jar.full`, `message.fireclay.reliquary.*` (`bound`, `other_owner`, `rest` (%s %s %s)).
- sentinel: `message.fireclay.sentinel.*` (e.g. `limit` (%s)).
- systems: `command.fireclay.*` (e.g. `not_a_kiln`, `kiln.info`, `kiln.heat`, `primer`, `record`), `hud.fireclay.*`
  (e.g. `hud.fireclay.pyrometer` "%s° · %s%% fired").
- kiln: `container.fireclay.kiln.*` for any extra labels.

Every agent lists the exact keys it added in its summary so the lang file can be completed.
