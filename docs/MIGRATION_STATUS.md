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
  fixes it still **fails with 47 errors**. No full BuildCraft client or dedicated-server
  startup has been claimed.
- Fabric regression fixture: **137 tests passed**, zero failures/ignored tests. This
  loads selected production classes under the real Fabric class loader and runs after
  Fabric's common initialization. It exits before starting a world and does not load
  the full BuildCraft mod. See [the fixture](../sub_projects/port-tests/README.md).
- Headless Fabric GameTest: **60 world tests passed** after 137 regression tests.
  A real Minecraft test server loaded a world, exercised selected production mining
  helpers, and shut down cleanly. This is a subsystem fixture, not full BuildCraft startup.
- Runtime packaging configuration now includes the expression library and Team Reborn
  Energy; final packaged-jar validation remains blocked on full compilation.
- Client model/GUI fixture: **27 tests passed**, plus the expression library's 13 tests.
  Real engine JSON and Minecraft sprite/vertex classes are used without a GL context.
  All five engine variants bake across every power stage and facing; referenced textures
  exist. Tests cover piston movement, stage emission, alias UVs, parent cycles/reuse,
  reload invalidation, signed packed normals, matrix transforms and overlay/light output.
  These tests do not establish that a complete client can start or display the models.
  Three fluid-render tests check RGB-to-ABGR coloring/alpha, all six animated heat
  templates and gas geometry anchored to the top of a block.

## Repair progress

- Restored bundled schematic compatibility rules and native server-data additions at
  `data/<namespace>/buildcraft/builders/rules/*.json`. Rules publish only after a complete
  successful load, support block-state/tag selectors and retain declared rule order.
  Nested inventory paths now traverse lists, missing item/fluid/list paths are empty,
  and missing NBT cannot accidentally match a legacy inequality selector.
- Default block schematics now capture, rotate, serialize, restore and compare native
  states and block entities; fluid schematics distinguish sources from natural flow and
  account for buckets/waterlogging. Native material rules cover container contents,
  modern inventories, slab/snow counts, upper halves and potted plants. Banner capture
  uses native colour/pattern data. Furnace/brewing work credit and furnace XP are cleared
  when restoring a machine; unopened structure loot is not generated again.
  Ten new GameTests cover these foundations, malformed block-entity rejection and all
  vanilla default-state rule lookups. That registry test checks metadata/cost evaluation,
  not placement of every vanilla block/state. Multi-block construction, entity capture,
  blueprint item/fluid transactions and the builder machine remain unfinished.
- Restored the template work executor with bounded surveys/queues, MJ credit, live material
  checks, break-before-place ordering, native owner-aware harvesting/placement, cancellation
  refunds, saved work identity and chunk-loaded checks. Queued jobs reserve no items.
  Twelve real-world tests cover placement, external changes, partial energy/reload,
  failed placement refunds, protected/unbreakable targets, missing resources, unloaded
  chunks and starvation by early tasks waiting for dependencies. A regression caught
  and fixed a queue that continually re-admitted unready work ahead of ready blocks.
- Reconnected the filler to its native item storage, MJ receiver, region markers/planner
  locks, pattern/inversion state, tick control and NBT. Added its native screen handler,
  shape/parameter/inversion/excavation controls, aligned material slots, translated labels,
  remake footer and native region outline. Locked settings packets cannot change inversion;
  incoming settings are fully decoded and normalized before applying them. Full client,
  actual filler block/marker integration and its screen still await whole-mod validation.
- Fixed recursive template/blueprint serialization, false-valued XZ/XY fill operations,
  snapshot dimension/facing/palette validation, repeated blueprint entity reads, missing
  filler facing/hash, and statement parameter-zero/max-count handling. Five snapshot
  tests exercise the actual serialization, hashing, copying, rotation and malformed inputs.
  Build volumes are currently bounded to 1,048,576 positions. Blueprint placement and
  entity/fluid construction remain unfinished; the restored executor does not establish
  support for those features. Filler work animation is not yet restored (region outline only).
- Registered the native Brigadier `/buildcraft` command tree: installed version and
  remake credit, a link to this repository's real commit history, and operator-only
  data-registry reload with error reporting. Native client resource access now supplies
  roaming config overrides, fixes their invalid namespace and invalidates caches on
  resource reload. The client-world supplier is now actually installed.
- Replaced the empty OreDictionary-backed list handler/cache with live item-tag matching.
  Material, form and exact category match across `c:iron_ingots`, `forge:ingots/iron`
  and vanilla ore tags. Broad form/gameplay tags cannot invent a material match. Three
  tests exercise real registry tag binding, reloads and generated examples. List handlers
  are now installed by the Fabric initializer; tool-class and fluid-list handlers still
  need their own functional migration. List tooltip text uses the native client callback.
- Debug requests/responses are immutable Fabric packets with bounded line counts/lengths,
  dimension and position correlation, and strict framing. Server queries enforce nearby,
  loaded, editable targets and tile access; four codec tests and two world tests pass.
  Client F3 polling, stale-text clearing, native debug-HUD hooks and optional render-update
  tracing are wired. Full connected-client display and client mixin startup are not yet
  verified, while full compilation remains blocked elsewhere.
- Projectile recovery now handles native persistent projectiles, honors item filters and
  pickup permissions, and calls the actual virtual pickup-stack method through a shared
  access widener. Three real-world tests cover tipped/spectral arrows, trident damage,
  enchantments/names, simulation, denial and repeated extraction. The obsolete EntityArrow
  shim is removed. This tests the extraction service; obsidian pipe collection still needs
  transport integration verification.
- Corrected quarry frame cleanup, architect entity queries, exact-NBT statement loading,
  gate packet-side comparison and pipe texture-builder chaining against the native API.
- Restored the nine original cold combustion fuels and solid coolant values. Combustion
  now accounts for fractional droplets, fuel work, MJ and residue across ticks and saves;
  blocked residue outputs roll back fuel consumption. Cooling respects redstone cooldown,
  temperature targets and the per-tick coolant limit. Automation can insert fuel/coolant
  and extract residue without bypassing restrictions through storage views. Ice insertion
  commits only if all coolant fits. Fifteen regression tests cover these rules and native
  solid-fuel lookup, actual machine-slot removal, insertion limits and extraction denial.
- Stirling engines use Fabric's fuel registry and update their power controller once per
  tick, instead of mutating it from display getters. Empty fuel containers remain removable
  without opening the fuel slot to arbitrary items. Engine MJ transfers clamp requests and
  receiver leftovers, reject negative extraction and avoid addition overflow. Saved numeric
  state is bounded and redstone state is refreshed on load. Full engine block ticking,
  heat behavior, MJ chaining and interaction still require full-mod world verification.
- Stirling/combustion screens use native extended handlers, actual sync IDs and block
  positions, tank widgets and slot inventories. Shift-clicks respect insertion/extraction
  and phantom-slot rules. Native backgrounds, fuel gauge, engine/ownership ledger icons
  and labels are restored. Atlas sources now include GUI icons, and atlas holders bind
  their texture before drawing. Screen opening and GPU output remain unverified.
- `processResources` converts all 32 legacy language files from the main, localization
  and guide trees into lower-case native JSON paths, preserving translation keys and
  UTF-8 values. The output excludes obsolete `.lang` files; authored modern JSON remains
  separate. Shared localization now reads Minecraft's language table, and engine screen
  titles have English/Chinese translations. Resource processing passes independently
  of the remaining compilation errors.
- Replaced the tank's zero-return Fabric adapter and broken Forge storage shim with
  one transaction-aware droplet store. Legacy mB constructors/methods convert exactly
  once; partial mB quantities survive native transfers and saves. Variant/NBT isolation,
  capacity bounds, simulation, nested rollback, commit callbacks and first-fluid locks
  are covered. Imported Forge saves use mB; earlier Fabric saves retain droplets.
- Bucket exchanges use real Fabric item contexts, committing container replacement,
  overflow and fluid changes together. Fourteen new regression tests include insufficient
  capacity, stacked-bucket overflow rejection, outer abort, column conservation and
  incompatible-fluid barriers and all 30 oil/fuel bucket variants. Two additional world
  tests verify survival inventory overflow and creative container preservation without
  duplicate returned buckets. Machine tank managers persist named tanks and expose
  native fluid storage; the actual tank tile now registers its storage, settles connected
  vertical tanks, updates comparators and synchronizes exact fluid amounts.
- Tank screens now register a real extended screen handler with the server's sync ID
  and block position, validate use, and support cursor clicks and inventory shift-clicks.
  Shared tank widgets/help metadata no longer carry client-only class annotations.
  GUI fill textures, translucent world cuboids, native tank blockstates, crafting, loot
  and English/Chinese names are wired. Three geometry tests cover outward winding,
  gas/liquid fill bounds and face masks. Actual tank placement, screen opening, multiplayer
  synchronization, world save/reload and GPU output still require full-mod startup;
  the subsystem tests do not establish these integration results. Other machine screens,
  refinery simulation and sided machine-specific fluid policies remain unfinished.
- Restored all ten oil/fuel families at three heat levels as native source/flowing
  pairs, world blocks and buckets, registered before the oil spring uses them.
  Flow retains variable quanta, viscosity-dependent tick rates, gas rising, liquid
  falling, density-based water displacement, collision barriers and finite sources.
  Six world tests cover the registrations/attributes, every bucket's placement and
  source-only collection, gas/liquid gravity, 12-quanta spread limits, source removal
  and water/solid barriers. Legacy unnamespaced fluid names resolve for saves/scripts.
- Fluid atlases now generate colored animated sprites from the original heat templates
  at resource reload, while allowing direct texture overrides. Native liquid and gas
  render handlers, bucket models and English/Chinese names are supplied. Gas geometry
  fills from the ceiling rather than the floor. Atlas-source registration uses a client
  Mixin invoker; its application and visual output still require the full client launch.
  Engine fuel/coolant use, refinery recipes, swimming/breathing in custom fluids,
  world generation and multiplayer behavior remain outstanding integration work.
- Restored the original variable-model parser, expression-driven cuboids, nested models,
  texture aliases and transformation rules against native Minecraft/JOML APIs. Parent
  resources close reliably and recursive references fail with a useful error. Model
  reload invalidates per-engine animation state before it is next used.
- Engine block-entity rendering now uses `BlockEntityRenderer`, `MatrixStack` and
  `VertexConsumer`, including texture, color, normals, emission and overlays. Five
  engine renderers register through native factories; the shared engine item selects
  its model using the stack's existing variant data. Atlas sources include the legacy
  `blocks`/`items` directories, and engine blockstates use the native variants format.
  The MJ dynamo's unfinished simulation/rendering, other machine renderers and actual
  client startup are still outstanding. Static legacy model holders and partial-UV
  texture expansion are also still incomplete.
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
- Item/fluid network caches now allocate real IDs, copy mutable stacks, preserve NBT,
  batch unresolved IDs into bounded Fabric requests, apply typed responses and invalidate
  suppliers on disconnect. Server cache IDs reset between server sessions, and unresolved
  fluid renders stay blank. Nine regression tests cover round trips, deduplication, batch
  limits, malformed packets, failed sends and session isolation. Live pipe/tank rendering
  still requires the full connected client.
- Marker messages now preserve dimension identifiers and ordered positions, use typed
  client receivers and broadcast within the actual server world. World caches use object
  identity, clear on unload/disconnect and resync on player join/dimension change. Four
  codec tests cover flags, custom dimensions, immutability and invalid lengths. Native
  block-entity load/unload callbacks now dispatch to tiles; load is deferred one tick
  so it can use saved NBT. Actual marker connections and tile reloads still await full-mod
  world validation.
- Static blocks/items now register directly in the native registries. Optional-object
  configuration is checked before invoking constructors, avoiding unregistered intrusive
  holders for disabled blocks. Core, factory, energy, builder and silicon initializers
  now call their block/entity registration paths; shared tag metadata can initialize
  independently of legacy Forge entrypoints. One regression test verifies repeated
  initialization preserves IDs, namespaces and historical aliases. Disabled-object
  configuration still needs a full-mod startup check.
- Block entities use explicit factories with their registered type, placed position and
  state; the old sign/ORIGIN placeholder constructor has been removed. Machine tick
  methods are connected through native block-entity tickers. Six real-world tests cover
  placement, different entity types for one block, automatic ticking, vanilla NBT
  reconstruction, invalid states and disabled/duplicate type registration. These test
  the production registry with fixture tiles, not complete BuildCraft machines.
- Engine stacks now encode their variant in `BlockStateTag`; placement selects that
  variant before entity construction, and pick-block retains it. Oil spring entities
  now receive actual positions/states and persist progress through native NBT callbacks.
  Engine drops, creative groups, full machine reloads, fluid registration and connected
  client rendering remain to be validated.
- Machine work/control/heat/area services now use native Fabric `BlockApiLookup` instead
  of Forge capability tokens. Direct interface implementations expose their services;
  sided block providers can register normally. Lookups reject removed, unattached and
  unloaded entities without loading chunks. Five world tests verify actual object/state,
  area/heat access, sided providers and lifecycle rejection. Miner and laser-table work
  predicates are exposed again, and machine statement callers use the native lookup.
  Full gate integration remains unfinished: inventory/fluid statements still use old
  capability paths, and other machines still need their work predicates restored.

- The guide now has native `Screen` rendering, two-page navigation, history, search,
  item links/tooltips, chapters, images and crafting/smelting/assembly recipe layouts.
  Font/icon/item rendering uses the current `DrawContext`; small nine-slice panels
  retain ordered borders and the actual sprite texture. GUI elements tick at game
  tick rate instead of frame rate, and foreground coordinates avoid a double window offset.
  Optional smooth-font textures have bounded caching and explicit release. Ten client
  tests cover frame-scope restoration, transformed geometry, UVs, small panels, native
  pixel color/alpha, smooth-font rasterization, formatting and Unicode wrapping.
- Client resource reloads now rebuild guide contents/search after scripts, language
  and models, with English fallback. Right-clicking the guide opens the reader; the
  reader includes **Remake by FelixMantra**. The resource task no longer excludes guide
  Markdown: all **111 page bodies** are copied. Recipe lookup reads the connected
  world's native recipe manager and generated pages resolve recipes when opened.
  Three Fabric tests cover stack literals, embedded SNBT commas, invalid IDs/counts
  and damageable-item NBT. The original guide assets still need an in-game content audit
  (including legacy engine metadata and untranslated strings). Full guide resource
  reload, GPU rendering, live input and actual machine screens remain unverified until
  the complete mod builds and launches; compiling the restored guide is not startup validation.

Remaining work includes full compilation, transport/item and machine-screen registration, network
debug/robotics messages, renderers, fluid/energy integration, full quarry operation, packaged
dependencies, and actual client/server/world validation. The working fixture does not
establish that any machine is ready for a survival world.

Remake by FelixMantra.
