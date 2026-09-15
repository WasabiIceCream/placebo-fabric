# Placebo (Fabric port)

Unofficial Fabric 26.1.2 port of [Placebo](https://github.com/Shadows-of-Fire/Placebo),
the shared library mod Apotheosis is built on — ported only as far as needed to
support the Apotheosis Adventure-module Fabric port (`mod-dev/apotheosis-fabric/`).
MIT licensed upstream — see `LICENSE`.

Full plan/rationale: `/home/wasabi/.claude/plans/spicy-meandering-fern.md`.
Live progress tracking: `TODO.md` at the repo root.

## Status (2026-09-07, updated)

**Milestone: every Placebo package/class the in-scope Apotheosis Adventure-module
files actually `import` is now ported and compiling.** Confirmed by grepping
Apotheosis's `attachments`/`tiers`/`affix`/`socket`/`loot`/`item` packages for
`import dev.shadowsoffire.placebo.*` and checking each one off — see the list
in `TODO.md`. 62 files compiled, `./gradlew compileJava` succeeds.

Two things Adventure-module code references are deliberately still in
`not-yet-ported/`, both because they need a mixin whose exact shape can only be
validated once the real call site is ported (client-side GUI mixins can't be
compile-checked the way the rest of this port has been — there's no way to
launch and test the client from here):
- `tabs/TabFillingRegistry` (`ITabFiller` itself IS ported — only the registry
  that cross-cuttingly injects a filler into *other* tabs needs the mixin).
  No Fabric API event exists for "a tab is building its contents" at all
  (checked directly against the installed `fabric-item-api-v1` jar — nothing
  there), unlike NeoForge's `BuildCreativeModeTabContentsEvent`. Only 2 items
  in scope (`GemItem`, `PotionCharmItem`) use this, for cross-tab convenience,
  not core functionality — safe to defer until `socket`/`item` are reached.
- `util/DrawsOnLeft` — cosmetic left-edge tooltip helper for the reforging
  menu's GUI, needs an `AbstractContainerScreenMixin` (also not yet written)
  plus accessor mixins for `Screen.font`/`AbstractContainerScreen.getLeftPos()`
  (both protected in vanilla). Deferred until `affix/reforging` is reached.

## Status (2026-09-07, earlier this session)

`./gradlew compileJava` succeeds — 53 files ported. Ported and compiling:
`dynreg` (15 files, the datapack-driven dynamic registry system — fully done),
`network` (3 files, redesigned around Fabric's per-side networking context
types), `codec` (4 files, verbatim), `registry` (`DeferredHelper.java`, the
general-purpose immediate-registration facade `Apoth.java`/other central
registry classes will call into — see below), `block_entity` (3 files,
`TickingBlockEntityType` redesigned as a standalone companion object rather
than a `BlockEntityType` subclass — see below), `cap` (`InternalItemHandler`,
redesigned against Fabric's Transfer API), most of `json`/`util`, and 4 of
`menu`'s files (the 2 needing `PlaceboContainerMenu`/`SimpleDataSlots`/`MenuUtil`
were pulled back out — see `not-yet-ported/`).

6 files sit in `not-yet-ported/` (`Offset`, `PlaceboUtil`, `WeightedItemStack`,
`DrawsOnLeft`, `BlockEntityMenu`, `SimplerMenuProvider`) — not broken, just
pulled out of the compile tree because they depend on Placebo packages/classes
not reached yet (`config`, `systems.gear`, `mixin.client`, `menu.MenuUtil`,
`menu.PlaceboContainerMenu`, `menu.SimpleDataSlots`). The whole `config`
package (6 files) was also moved to `not-yet-ported/config/` — nothing else in
the ported tree depends on it, and its one file (`ConfigCategory`) needs the
1484-line `Configuration.java`, not yet ported. Move things back once their
dependency packages exist.

Not yet ported at all: `config` (moved to `not-yet-ported/`), `payloads`,
`screen`'s dependents, `systems`, `tabs`, `events`, the rest of `menu`, and
most of Placebo's own mixins (only `TagEntryAccessor` exists so far).

### `DeferredHelper` — a real simplification, not just a port

Placebo's original `DeferredHelper` stages every registration behind
NeoForge's `RegisterEvent` (fired once per registry in a fixed dependency
order) via an internal queue, because NeoForge registries must not be touched
outside that window. Fabric registries don't have that restriction — this port
registers everything **immediately**, at call time, via
`Registry.registerForHolder`/`Registry.register`. No staging queue exists in
this version at all. Confirmed via `javap` that Apotheosis's own field
declarations (e.g. `Apoth.java`) use vanilla `Holder<Block>`/`Holder<Item>`
types directly, not NeoForge's `DeferredBlock`/`DeferredItem` — so
`registerForHolder`'s `Holder.Reference<T>` return is a drop-in match with no
wrapper class needed downstream.

Not ported (need bespoke Fabric-specific redesigns, deferred until their real
call sites are reached in the Adventure-module port): `attachment()` (replaced
at call sites by Cardinal Components API, not something this helper should
paper over), `ingredient()` (NeoForge's `ICustomIngredient`/`IngredientType`
have no Fabric equivalent registry), `dataMap()` (NeoForge Data Maps, no
Fabric equivalent), `lootModifier()` (NeoForge's `IGlobalLootModifier` codec
registry — the Loot Table Modifier mod's API is a different shape entirely),
and `registry()` (custom dynamic-registry creation — Apotheosis's actual
custom registries in scope go through `dynreg.DynamicRegistry` instead).

### Two real vanilla-API surprises found while writing `DeferredHelper`

- **`BlockEntityType`'s constructor is now private** in 26.1.2 — it can only be
  built via Fabric's `FabricBlockEntityTypeBuilder`. This makes subclassing it
  (which the original `TickingBlockEntityType` did, to carry a `TickSide`)
  impossible. Redesigned `TickingBlockEntityType` as a standalone companion
  object keyed by identity against the real `BlockEntityType`, registered
  alongside it in `DeferredHelper.tickingBlockEntity()`, and looked up in
  `TickingEntityBlock.getTicker()` instead of an `instanceof` check.
- **No Fabric API equivalent for NeoForge's `IContainerFactory`-based
  `MenuType`** (used to send extra data like a `BlockPos` to the client when
  opening a menu) — Fabric API instead has its own, differently-shaped
  `net.fabricmc.fabric.api.menu.v1.ExtendedMenuType`/`ExtendedMenuProvider`,
  which carries the extra data through its own `StreamCodec` rather than
  reading it off the same buffer as the menu id. `DeferredHelper.menuWithPos()`
  uses this instead of the original's `MenuUtil.PosFactory`/`IContainerFactory`
  pattern (that whole `MenuUtil` class is itself not yet ported — see above).

`Placebo.java`/`PlaceboClient.java` are intentionally partial entrypoints — they
wire up only what's been ported (dynamic-registry sync payloads), not the full
original feature set. Expand them as more of Placebo gets ported.

## Real NeoForge → Fabric decisions made so far

- **Reload listeners**: `DynamicRegistry`/`DynamicTagManager` implement Fabric's
  `IdentifiableResourceReloadListener` and register directly via
  `ResourceManagerHelper`, instead of NeoForge's `AddServerReloadListenersEvent`
  subscriber pattern. Dependency ordering uses `getFabricDependencies()` instead of
  `event.addDependency(...)`.
- **Datapack sync**: no 1:1 Fabric equivalent for NeoForge's `OnDatapackSyncEvent`.
  Split into `ServerPlayConnectionEvents.JOIN` (sync to the joining player) +
  `ServerLifecycleEvents.END_DATA_PACK_RELOAD` (sync to everyone after `/reload`),
  both funneling into the same `DynamicRegistry.sync(server, player)` method.
- **Networking**: redesigned `PayloadProvider`/`PayloadHelper` around Fabric's
  separate `ClientPlayNetworking.Context`/`ServerPlayNetworking.Context` types
  instead of NeoForge's unified `IPayloadContext`. Client-side receiver
  registration is deferred to `PlaceboClient.onInitializeClient()` (via
  `PayloadHelper.registerClientHandlers()`) since `ClientPlayNetworking` is a
  client-only class that can't be referenced from common code.
- **Per-entry conditional JSON loading** (NeoForge's `"neoforge:conditions"`):
  no Fabric equivalent for a custom-scanned directory (Fabric's Resource
  Conditions API only auto-applies to vanilla resource types). Stubbed
  always-true in `JsonUtil.checkConditions` — see the loose-end note in `TODO.md`.
- **`TagEntry.isTag()`/`.getId()`**: NeoForge patches vanilla's `TagEntry` with
  these as public getters; plain Fabric doesn't have the patch. Added a mixin
  accessor (`mixin/TagEntryAccessor.java`) instead.

## 2026-09-15: real `RegistryAccess` now reaches the very first boot-time reload

Found by reading a live production `fabric 26.1/` boot log (`apotheosis` affix/gem
parse errors) and tracing them back here. `DynamicRegistry.apply()`'s own javadoc
already documented the gap precisely: it sourced `RegistryAccess` from a
`MinecraftServer` captured by `ServerLifecycleEvents.SERVER_STARTING`/
`START_DATA_PACK_RELOAD`, but **both of those fire after the very first datapack
reload at boot has already completed** — so that first reload (the one that loads
every `apotheosis:affixes`/`gems` file) always fell back to plain `JsonOps`,
breaking every field needing real dynamic-registry context (enchantments, etc.)
with "Can't access registry" / silently-wrong tag resolution.

Fixed with a mixin (`mixin/ReloadableServerResourcesMixin`) injecting into the
static `ReloadableServerResources.loadResources(ResourceManager,
LayeredRegistryAccess<RegistryLayer>, ...)` — the exact vanilla factory that
builds the reload-listener list `DynamicRegistry.apply()` runs inside of, for
every reload including boot. It already receives the populated
`LayeredRegistryAccess` as a parameter before any listener runs, so capturing
`registryAccess.compositeAccess()` there and feeding it to `DynamicRegistry`
(via a new `setRegistryAccess()`, replacing the old lifecycle-event capture)
closes the boot-time gap entirely. Confirmed on a local `fabric 26.1/` boot test:
`apotheosis:gems` went from 2/4 to 3/4, `apotheosis:affixes` from ~62/70 to 64/70
— specifically the two `Registries.ENCHANTMENT`-holder fields
(`breaker/enchantment/prosperous`, `ranged/enchantment/prosperous`) and one gem
(`overworld/earth`) that were failing on "Can't access registry" now decode
correctly.

**First mixin attempt failed to apply** (`InvalidInjectionException:
CallbackInfoReturnable is required`) — `loadResources` returns a
`CompletableFuture`, so a static-method `@Inject` needs
`CallbackInfoReturnable<CompletableFuture<...>>`, not plain `CallbackInfo`, even
at `@At("HEAD")` with no intent to return early. Worth remembering for any future
mixin into a method with a non-void return type.

**New, separate bug surfaced by this fix, still open**: 4 affixes
(`stoneforming`, `sandforming`, `leafforming`, `gardening`) now fail with
`Missing tag: 'apotheosis:xxx_candidates' in 'minecraft:block'` instead of the
old silent/garbled failure — and the tag JSON files genuinely exist at
`apotheosis-fabric`'s `data/apotheosis/tags/block/*_candidates.json`. This isn't
a `DynamicRegistry` codec bug; it's a boot-sequencing one: static-registry
(`minecraft:block`) tags are parsed via `TagLoader.loadTagsForExistingRegistries`
into a `List<Registry.PendingTags<?>>` that gets passed *into*
`loadResources(...)` as an argument, but binding them into `BuiltInRegistries`
appears to happen only via `ReloadableServerResources.updateComponentsAndStaticRegistryTags()`,
which — going by this same failure — runs *after* `loadResources`'s reload
listeners (ours included) have already executed. So by the time our codec asks
`RegistryOps` to resolve `#apotheosis:xxx_candidates` against `Registries.BLOCK`,
the tag isn't bound yet. Not fixed here — would need either binding those pending
tags earlier (risky, vanilla-behavior-changing) or having `DynamicRegistry` re-run
affected entries after static tags bind. Two other pre-existing, unrelated gaps
remain untouched: `armor/attribute/{aquatic,unbound}` reference genuine
NeoForge-only attributes (`neoforge:swim_speed`/`creative_flight`) never
registered on Fabric, and `the_end/endersurge` uses a NeoForge-only
`{"type": "neoforge:any"}` holder-set wildcard this port's `GemClass` codec has
no equivalent for — see `apotheosis-fabric/DEVLOG.md`'s own note on both.

## 2026-09-15 (same day, second pass): the tag-binding-order bug above is fixed too

Root-caused by decompiling `WorldLoader.load`/`ReloadableServerResources`/
`ReloadableServerRegistries` with Vineflower (against the exact merged jar this
project builds against) instead of guessing from bytecode signatures. Confirmed
the two-phase design exactly as suspected above: `WorldLoader.load` computes
static-registry tags early into a `List<Registry.PendingTags<?>>`, hands it into
`ReloadableServerResources.loadResources(...)`, and only actually *applies*
those tags into the live `BuiltInRegistries` objects afterward, via
`.thenApplyAsync(managers -> { managers.updateComponentsAndStaticRegistryTags();
... })` — strictly after `loadResources()`'s own `CompletableFuture` (and thus
every reload listener inside it, `DynamicRegistry.apply()` included) has already
resolved. Reading the live `RegistryAccess` mid-listener, as the previous fix
did, was therefore structurally never going to see current tag content for
static registries — this wasn't a boot-race to win, it was the wrong data
source entirely.

The fix: vanilla's own recipes/advancements/loot-table loaders don't hit this at
all, because they don't decode against the live `RegistryAccess` either — they
use a separate, already-current `HolderLookup.Provider`
(`ReloadableServerRegistries.LoadResult#lookupWithUpdatedTags()`, built via
`TagLoader.buildUpdatedLookups(...)`) that's threaded straight into
`ReloadableServerResources`'s own constructor as its `loadingContext` parameter.
Moved the mixin from `loadResources`'s static-method parameters to
`ReloadableServerResources`'s constructor (`@Inject(method = "<init>", at =
@At("RETURN"))`), capturing that same `loadingContext` instead of
`registryAccess.compositeAccess()`. `DynamicRegistry` now builds its `RegistryOps`
via `HolderLookup.Provider#createSerializationContext(JsonOps.INSTANCE)` on the
captured lookup rather than `RegistryOps.create(JsonOps.INSTANCE,
RegistryAccess)` — same effect for enchantment-holder fields, but now also
correct for tag references since the captured provider already has current tag
content.

Confirmed on a local `fabric 26.1/` boot test: `apotheosis:affixes` went from
64/70 to **68/70** — all four `Missing tag` failures
(`stoneforming`/`sandforming`/`leafforming`/`gardening`) are gone.
`apotheosis:gems` stayed at 3/4 (unchanged, as expected — `endersurge`'s failure
was never a tag-order issue). Only the two genuinely out-of-scope gaps remain:
`armor/attribute/{aquatic,unbound}` (real NeoForge-only attributes) and
`the_end/endersurge` (NeoForge-only holder-set wildcard) — both noted above and
in `apotheosis-fabric/DEVLOG.md`.
