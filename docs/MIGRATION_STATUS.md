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
- Full `compileJava`: the imported source produced **357 errors**; the complete
  production tree now **compiles successfully**. The selected `libLeaf` source set
  has been removed and its task is a compatibility alias for full compilation.
- Fabric regression fixture: **148 tests passed**, zero failures/ignored tests. This
  loads selected production classes under the real Fabric class loader and runs after
  Fabric's common initialization. It exits before starting a world and does not load
  the full BuildCraft mod. See [the fixture](../sub_projects/port-tests/README.md).
- Headless Fabric GameTest: **69 world tests passed** after 148 regression tests.
  A real Minecraft test server loaded a world, exercised selected production mining
  helpers, and shut down cleanly. This is a subsystem fixture, not full BuildCraft startup.
- `remapJar` succeeds and bundles the expression library and Team Reborn Energy.
  A separate fixture loads the actual remapped artifact and its bundled dependencies;
  **47 full-mod GameTests pass**. All **849 non-null registered block-entity states**
  construct with compatible types and round-trip NBT. Native tank placement/ticks and
  optional MJ Dynamo sided lookup, simulation, commit and fractional MJ persistence
  pass in a real headless Minecraft world. Animated engines also retain non-opaque
  native states so they do not block their own skylight. Both marker kinds attach to all
  six supporting faces, and decorative variants survive picking, saving and placing.
  Frame/quarry/chute native connections, partial frame collision, wrench rotation,
  structure rotation/mirroring and block-entity preservation also pass.
  Chute pickup/output, capacity and NBT conservation, minecart destinations, insert-only
  external storage, save reconstruction, drops, owner advancement, native screens and
  the gear/wrench/chute crafting chain are covered by ten additional world tests.
  Nine pipe tests cover all 46 native pipe items, placement/picking, dye/material
  connections, cross-face transaction rollback, chute-to-pipe delivery, saved travelling
  stacks, MJ-powered wooden extraction, wrench use, flow packets and survival recipes.
  Seven further tests cover native diamond filter editing, phantom-item isolation,
  filter persistence/drops, weighted routing, owner advancement, colour routing and
  wrench-controlled delivery/settings.
  Four wooden-diamond tests cover exact-NBT whitelist/blacklist matching, one-item
  MJ pulses, side-effect-free simulation, ordered round-robin starvation, native screen
  controls, saved cursors/travelling items, payloads and malformed saved indexes.
  Four Emzuli tests attach actual gate logic, select extraction presets, deliver painted
  items through Daizuli outputs after save/reload, expire inactive/removed gates, keep
  power queries side-effect-free, validate native paint controls and round-trip live
  gate/preset packets. Three further gate tests cover 25 variants on all six faces,
  variant drops/picking and reload, native statement/parameter edits, AND groups,
  Emzuli activation, redstone retraction, stale/distant access and bounded edit packets.
  This does not establish complete machine
  behavior. See [the full-mod fixture](../sub_projects/full-tests/README.md).
  Root `build` still includes historical Forge tests that need migration.
- Full development client: created and entered a local creative superflat world,
  ran `/buildcraft version` (including `Remake by FelixMantra`), placed a tank and
  Stirling engine, opened its native screen, and saved/exited cleanly. A fresh client
  reloaded that world. Newly placed engines show their base/piston textures after the
  native opacity fix; a block saved with the previous opaque settings retained its old
  light cache until removed. After the resource migration, that saved world loaded
  again; a newly placed quarry displayed its authored textures, and a volume marker
  rendered on its upper face. The world saved and the client exited cleanly.
  The chute screen opens with its authored background and translated title. Eight
  diamonds placed through that screen reached the barrel below; five more collected
  from the mouth increased the barrel contents to exactly 13. The owner advancement
  appeared, and an F3+T resource reload retained the working screen and world rendering.
  That world saved successfully and the client exited with code zero.
  Plain and blue-dyed stone-pipe circuits now render in that world, with diamonds
  visibly circulating through the native item renderer. F3+T resource reload and
  saving/re-entering the world both retain the pipe models and moving contents.
  The diamond pipe shows its six coloured outputs and native filter screen. Editing
  an east-side filter to weight two leaves all seven real diamonds in the hotbar;
  closing/reopening, resource reload and saving/re-entering preserve that setting.
  The wooden diamond screen also opens with its original textures. Whitelist,
  blacklist and round-robin buttons synchronize, and the active-slot indicator follows
  a ghost sample in the third slot. F3+T and saving/re-entering retain the round-robin
  setting and sample while all seven real diamonds remain in the player's hotbar.
  In a scene with a preconfigured attached gate, the Emzuli screen shows the enabled
  preset and current-slot arrow. Editing a diamond sample preserves all seven real
  diamonds; left/right/middle paint controls select, reverse and clear colours. White
  and black settings on different presets, activation and the sample survive F3+T
  and saving/re-entering. This validates Emzuli's screen, not player gate configuration.
  The new gate configuration screen has server/packet tests and packaged GPU geometry
  checks, but its manual client interaction check remains pending: the Windows input
  helper returned `foreground window did not report a process id` after client startup.
- Packaged GPU client: **928 visible block states, 128 items and 183 item variants pass**
  missing-model, particle/quad-texture and expected-variant checks. It reaches the title
  screen and exits cleanly. The latest run has no invalid resource paths, JSON-model
  parse errors or missing blockstate variants. Unavailable guide links, legacy fluid
  animation frames and atlas mipmap warnings remain. This is not complete playability
  or a check of every in-world renderer and UI.
- Client model/GUI fixture: **32 tests passed**, plus the expression library's 13 tests.
  Real engine JSON and Minecraft sprite/vertex classes are used without a GL context.
  All six engine/dynamo variants bake across every power stage and facing; referenced textures
  exist. Tests cover piston movement, stage emission, alias UVs, parent cycles/reuse,
  reload invalidation, signed packed normals, matrix transforms and overlay/light output.
  These tests do not establish that a complete client can start or display the models.
  Three fluid-render tests check RGB-to-ABGR coloring/alpha, all six animated heat
  templates and gas geometry anchored to the top of a block.

## Repair progress

- Converted registered legacy Forge blockstates into native variants/multipart models,
  retained the authored textures and restored inventory aliases. Resource paths are
  lowercase; 18 expression models now live under `bc_models/` so vanilla does not try
  to parse their dynamic expressions. Native predicates select map/list/decorative/
  schematic/snapshot item variants using values within the required 0–1 range.
- Markers now have native facing, support checks, narrow selection shapes and cutout
  rendering. Decorated blocks preserve their type in native `BlockStateTag` item data.
- Frame/chute/quarry connections now follow native placement and neighbor updates.
  Quarries/chutes query sided Fabric item storage, including providers without block
  entities. Frame collision follows its connected arms; frame/chute rotation and
  mirroring preserve those arms. Chute rotation refreshes connections without replacing
  its inventory. Complete quarry mining remains open.
- Chutes now collect at most three items per work cycle from the quarter-block mouth
  region, using gravity when facing up or MJ on any face. Transactional output moves
  one item per destination per tick, excludes the mouth and visits overlapping inventory
  entities once. External item storage permits insertion only. Inventory, partial work
  and MJ persist; native loot drops the block and contents without duplication.
  The native four-slot screen supports manual transfers and shift-clicks, validates
  distance/use and leaves wrench rotation accessible. Native wood/stone gears, wrench
  and chute recipes, owner advancement and English/Chinese names are restored.
- Restored the common transport entrypoint, native pipe-holder block/entity and all
  46 definition-specific pipe items. Native placement, picking and drops preserve dye;
  17 structure/item pipe recipes and the original placement advancement are available.
  Hollow pipe geometry follows immutable render attachments, separates stained glass
  from cutout layers and rebuilds its bounded cache with each resource reload.
  Native transactional item insertion feeds the original item routing logic. Wooden
  MJ extraction, stone delivery, saved travel timing and server-authoritative item
  animation snapshots are restored. Diamond item pipes now save their 54 filter samples,
  expose the native filter screen, display six coloured direction textures and preserve
  weighted routing. Lapis and Daizuli wrench controls select item colours; iron-pipe
  rotation changes delivery direction. Power/RF limiter controls require a wrench.
  Wooden diamond item pipes reuse native MJ extraction, retain nine ghost samples
  and implement whitelist, blacklist and ordered round-robin modes. Simulated queries
  never advance the cursor; missing items wait at the current filter. Each pulse
  extracts one item and returns unused MJ. Native mode buttons validate screen access,
  and mode/cursor state persists and synchronizes through native screen properties.
  Emzuli item pipes now preserve four filters/paint colours, honor gate extraction
  presets with two-tick expiry and rotate only after successful extraction. Native
  paint controls support next/previous/clear without materializing ghost items.
  Gate/pulsar definitions and transport statement providers are registered; the
  extraction preset icons use their actual authored textures in the block atlas.
  Core statements now register during normal startup, before saved gates are loaded;
  tests resolve trigger IDs from the registry instead of initializing them implicitly.
  Gate items now register with 25 original NBT variants, six oriented collision boxes,
  survival placement consumption, variant drops and creative picking. Their authored
  plate/logic/modifier/illuminated overlay models render on all six faces and invalidate
  cached quads on resource reload. Native gate screens offer searchable statements,
  server-owned sample/enum parameters, linked AND/OR groups and live activation state.
  Edits use the existing bounded container channel (vanilla button packets only hold a
  signed byte), validate current gate identity/permissions and available statements,
  and mark the tile dirty. Malformed clay gate modifiers normalize to one basic slot.
  Full fluid/power/RF transport, obsidian/stripes behavior
  and remaining pluggable/wire gameplay still require integration. Gate crafting and
  inventory/fluid gate statements remain unfinished. Registration of a pipe type
  does not establish its special behavior. Pluggable render APIs remain client-only
  so dedicated servers can load all pipe definitions.
- Separated client packet callbacks from common network initialization. Removed source
  classes shadowing Minecraft's native `Chunk` and LWJGL's `BufferUtils`; the latter
  prevented a real client from initializing LWJGL 3.
- Added the native MJ Dynamo energy bridge with exact MJ conversion, per-tick output
  allowance, nested transaction rollback, directional output, upgrade inventory,
  persistence, native screen and animated model. It remains optional and disabled by
  default. The separate RF engine is still incomplete.
- Restored native snapshot request/response packets with ownership checks, bounded
  payloads, content-hash verification and bounded client caches. Zone-map packets now
  carry dimension identifiers, preserve negative/zero heights, reject unloaded or
  out-of-range requests and limit request/cache growth. Their preview/planner GUI
  integration remains unfinished.
- Heat-exchanger models now use a native baked-model wrapper with a reload-aware state
  cache. The auto-workbench recipe preview uses the real crafting recipe manager.
  Heat-exchanger multiblock operation and actual auto-workbench crafting are still open.

- The laser path now emits native vertex consumers with real original texture rows,
  atlas-backed marker/power/stripe types, sampled light and world-to-local coordinates.
  Short beams and fractional final segments stop at their actual endpoint; long meshes
  have a bounded segment count. Five geometry tests cover every axis, diagonals, normals,
  winding, UVs, double-sided faces, invalid/zero-length inputs and far-world precision.
  The block atlas now includes the original animated laser directory.
- Quarry rails, vertical support, animated drill travel and powered clearing beam now
  use a registered native block-entity renderer. Mining-well and pump renderers share
  the native pipe mesh and charge/work LED geometry. Volume marker signals and cached
  path/volume connections render through a scoped world-render callback. Architect scan
  surfaces use translucent native buffers, clipped bounds and fading ticks; scan caches
  clear on world changes/disconnect. These registrations and geometry have not yet been
  observed in a running full client; GPU appearance and machine integration remain open.
- Mining tubes now use native shape/hardness settings, bounded vertical owner lookup and
  Fabric's cancellable block-break event to protect a connected miner's tube in creative
  mode. Actual miner placement/retraction and this callback await full-world validation.
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
  not placement of every vanilla block/state. Multi-block construction and entity capture
  still need further work and whole-mod validation.
- Blueprints now use native item/fluid storage in one transaction: simulation, missing
  duplicate requirements, exact-NBT matching and rejected placement return all resources.
  Successful placement commits both resources together. The real blueprint builder uses
  rotated schematics, support dependencies and the bounded MJ work queue. Remaining item
  totals update with surveyed world changes; the screen displays up to 24 item variants.
  Nine new world tests cover split slots/tanks, sub-mB residue, rollback/exceptions,
  waterlogged stairs, paid chest contents, support order, queued-energy reload, rejected
  block-entity placement, existing/missing blocks and template selection past non-block items.
- The builder machine now has a snapshot slot, 27 resource slots, four native tanks,
  MJ reception, server ticking, bounded path expansion, persistent path progress and
  queue/battery NBT. Selecting another plan refunds queued credit before the next save.
  Native screen registration, tank interaction widgets, material display, excavation
  control, snapshot block-state changes and the working-volume outline are connected.
  These machine/UI/path integrations are not yet validated in a full BuildCraft world.
  Entity reconstruction remains unimplemented, and blueprints containing entities are
  deliberately not reported as finished after only their blocks have been built.
- Snapshot items now encode blueprint/template and used/blank kinds in NBT, retain
  legacy damage/header-only reads, use native names/tooltips and have item-model predicates.
  Three real-Fabric item tests cover stack serialization, old formats and malformed input;
  they caught a missing-owner UUID crash. The item tooltip and builder screen include
  **Remake by FelixMantra**. Actual item-model and screen rendering await the full client.
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
  Build volumes are currently bounded to 1,048,576 positions. Entity reconstruction
  remains unfinished. Filler/builder work animation is not yet restored (region outlines only).
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
  reload, GPU rendering and live guide input remain unverified. The Stirling engine
  screen has now been opened in the full client; other screens still need validation.

Remaining work includes remaining resource/model/recipe migration, gate crafting and other pluggable/wire
items and placement, remaining machine screens, robotics/volume-planner integration, fluid/energy
integration, full quarry operation, and broader connected-client/save-reload gameplay
validation. Compile/package/startup success does not establish that every machine is
ready for a survival world.

Remake by FelixMantra.
