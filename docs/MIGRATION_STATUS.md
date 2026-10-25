# Fabric 1.20.1 migration assessment

Assessment started on 2026-10-25 against PR #4754 head
`2051fb41455312a2e6c5ffeb972c6639e2a32bb9`.

## Recommendation

Continue in this repository and preserve its history. Reuse the expression library,
assets, algorithms and already migrated API definitions. Restore Fabric integrations
by subsystem instead of throwing away all prior work or adding more no-op shims.

The imported code is not yet a playable Fabric port. The latest original commit
message reports 358 remaining full-compilation errors. The local full compilation
on JDK 17.0.20.1 / Gradle 8.14.5 reported **357 errors** after dependency resolution.
A source scan found 1,265 Java files under
`common`, and 692 files across `common` and `BuildCraftAPI/api` matching `STUB` or
`TODO(R.Chen)`. These are file counts, not a precise count of broken features.

## Concrete gaps in the imported revision

- `build.gradle` has a selected `libLeaf` source set and a separate full `main` source
  set. Passing `compileLibLeaf` alone cannot establish that the whole mod builds.
- `BCLibInitializer` and `BCCoreInitializer` contain no-op registration/lifecycle
  paths. The log message “Initialization complete” does not establish registration.
- `BCTransportBlocks.pipeHolderTile` is unassigned, while the client initializer
  immediately tries to register its renderer. The referenced common-side
  `BCTransportInitializer` does not exist and is absent from mod entrypoints.
- `BCNetworkManager` logs tile-update packets but does not apply them.
- The source tree contains 65 classes under `net.minecraft` and 95 under
  `net.minecraftforge`, including compatibility shims with unfinished behavior.
- `TileQuarry` defers output storage, frame placement and several world integration
  paths. Merely making this class compile would not validate mining behavior.
- The imported `BlockUtil.breakBlockAndGetDrops` removed blocks and returned no drops.
  The helper is now repaired and tested below; full quarry operation remains unverified.
- Resources still include old `.lang` files and historical model/recipe formats.
- Expression and energy dependencies need verification in the distributable jar,
  not just the development classpath.

## Validation sequence

1. Reproduce the full build and expression tests with the declared toolchain.
2. Fix compilation with real behavior and retain regression coverage for data/energy
   conservation, transaction rollback, serialization and world loading.
3. Restore registry and common/client separation, then verify dedicated-server and
   client startup.
4. Exercise block placement, save/reload, item/fluid transport, power, engines and
   quarry operation in a world before calling those features supported.

No “zero bugs” guarantee is made. Record observed test results and unsupported
features explicitly as work proceeds.

## Verified results

- Original-history import: all 60 original migration commits now count as GitHub
  contributions; see [HISTORY.md](HISTORY.md).
- Gradle wrapper: regenerated with Gradle 8.14.5, pinned to the official distribution
  SHA-256, and launched successfully on Windows with JDK 17.
- Expression library: **13 tests passed**, zero failures/errors (4 dependency tests
  and 9 expression tests). The generator produced 100 Java source files.
- Full `compileJava`: the imported source produced **357 errors**; after the current
  fixes it still **fails with 196 errors**. No full BuildCraft client or dedicated-server
  startup has been claimed.
- Fabric regression fixture: **79 tests passed**, zero failures/ignored tests. This
  loads selected production classes under the real Fabric class loader and runs after
  Fabric's common initialization. It exits before starting a world and does not load
  the full BuildCraft mod. See [the fixture](../sub_projects/port-tests/README.md).
- Headless Fabric GameTest: **14 world tests passed** after 76 regression tests; the
  subsequent standalone regression run also passed three new tick-queue tests.
  A real Minecraft test server loaded a world, exercised selected production mining
  helpers, and shut down cleanly. This is a subsystem fixture, not full BuildCraft startup.
- Runtime packaging configuration now includes the expression library and Team Reborn
  Energy; final packaged-jar validation remains blocked on full compilation.

## Repair progress

- Item insertion/extraction now uses Fabric transactions, including all-or-none
  insertion, simulation, batch capacity accounting and minimum extraction across slots.
- Machine inventory groups retain named NBT data, preserve phantom filters, exclude
  phantom items from drops, and expose configured faces and input/output permissions.
  Cancelled transfers restore inventory contents and do not emit change callbacks.
- Exact-NBT ingredients use Fabric's serializable ingredient API, with tests for
  strict matching, input mutation isolation and JSON round trips.
- List filters now match items and registered type/material handlers, preserve their
  options across NBT round trips, and retain user labels when cleared. Precise matching
  includes damage and NBT; independent damage/NBT flags are covered by regression tests.
- Item interactions use native 1.20.1 entrypoints and settings. The API's user label
  getter is now `INamedItem.getLabel`, avoiding a collision with Minecraft's Text-valued
  `Item.getName`. `IngredientStack.of` accepts native items, stacks, ingredients and
  item IDs or `#namespace:tag` IDs, and rejects unknown items instead of silently using
  an empty ingredient. API changes are kept on the `remake/1.20.1` branch of BuildCraftAPI;
  the original PR's API commit remains intact.
- Registry reloads have Fabric resource listeners and lifecycle callbacks. Scripts can
  read mod roots, native resource/data packs and config files. Temporary pack projections
  are cleaned up without closing Loader-owned file systems. Pack projection tests pass;
  full script execution and registry lifecycle still require integration validation.
- Compact bit packing preserves historical bytes and supports signed 32-bit round trips.
  Snapshot readers, resource metadata, item caches and several mapped game APIs were
  corrected. Unused broken Forge shims were removed where native APIs replaced them.
- MJ connectors now use a sided Fabric `BlockApiLookup`. Engines, battery-backed machines,
  power-pipe sections and wooden extraction behaviours expose their actual MJ services;
  pipe pluggables retain connection-blocking precedence. Simulated transfers, return of
  excess power, pulse-receiver restrictions, NBT/packet persistence and numeric bounds
  pass regression tests. The native lookup path and transport in a world still require
  integration validation. Passive extraction and the Team Reborn conversion bridge remain
  unfinished; micro-MJ units are not treated as interchangeable with external energy units.
- Block harvesting now uses Fabric's native fake player and Minecraft's interaction manager,
  retaining the owner's UUID and invoking Fabric block-break protection callbacks. World
  tests verify ore loot, Silk Touch, chest contents/NBT, cancellation, unbreakable blocks,
  insufficient tools, preservation of unrelated dropped items and collection without
  duplication. The `grabAll` flag again means collecting existing nearby items. Temporary
  tools are restored, and machine profile names are safe on Windows. Native fluid probes
  now recognize flowing water and waterlogged blocks.
- Core, energy and builder configuration entrypoints now read and save settings. The
  no-op Forge configuration shims were replaced by BuildCraft-owned typed configuration
  classes, with atomic saves, legacy `.cfg` input, list/string preservation, schema bounds,
  default recovery, key migration, restart metadata and in-place reloads. Thirteen regression
  tests cover these paths. Object settings resolve intended namespaced IDs before registration;
  biome-name validation now uses the server's loaded dynamic registry. Disabled-object
  registration and complete startup still need integration validation.
- Tile and container packets now use registered Fabric typed receivers on both sides,
  route payloads on the game thread and return widget replies. Payloads own immutable
  bytes, preserve readable offsets and enforce framing/size bounds; temporary buffers
  are released on success and failure. Screen sync IDs, usability, spectators, tile
  interaction permission, distance and loaded chunks are checked before routing.
  Eight codec tests and six world-routing tests pass. These invoke production routing
  with fixture block entities/screens; a real client connection and actual machine GUI
  remain untested. Three more tests cover delayed tick tasks and queue isolation.
- Vanilla tile NBT now separates render-only updates from persistent loading, and
  neighbour/redstone callbacks use native 1.20.1 signatures. Full machine save/reload
  and renderer validation remain outstanding.

Remaining work includes full compilation, block/entity and screen registration, network
cache/marker/debug messages, renderers, fluid/energy integration, full quarry operation, packaged
dependencies, and actual client/server/world validation. The working fixture does not
establish that any machine is ready for a survival world.

Remake by FelixMantra.
