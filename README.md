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
