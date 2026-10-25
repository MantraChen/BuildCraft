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
  The helper and the quarry excavation loop are now repaired and tested below.
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
  **218 full-mod GameTests pass**. All **849 non-null registered block-entity states**
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
  Eight further tests cover gate-controlled direction/paint with actual deliveries and
  save/reload, stale output rejection, seven MJ/RF limits for all four limiter pipes,
  matching travelling-item samples, and native sided inventory/fluid conditions.
  Opening a gate beside a barrel or tank no longer initializes null Forge capabilities.
  Empty/full/contains/space and 25/50/75% thresholds use Fabric storage views, including
  furnace face restrictions and insertion-only/read-only fluid providers. Sample/NBT
  matching and aborted insertion probes leave inventory, tanks and sample buckets intact.
  Six wire tests cover all 16 colours at eight corners, survival counts/drops, native
  extension to an adjacent pipe and the restored connection advancement. Actual world
  ticks propagate gate signals, retract power on a split, reconnect after repair and
  reload, and remove stale emitters. Colours and disconnected pipe dyes remain isolated;
  multiple emitters combine, and chunk lifecycle hooks rebuild derived networks.
  Bounded, atomic wire snapshots include power state; malformed NBT/packets and server-side
  injection are rejected. Native partial breaking drops only the selected wire/gate,
  preserves the pipe, honours Fabric protection cancellation and suppresses creative drops.
  Eleven fluid tests cover native sided transactions, cross-face rollback and rate
  budgets, exact droplet/NBT conservation, MJ extraction, native save/reconstruction,
  full/partial/replaced sinks, iron direction, clay priority and delayed void consumption.
  Forced extraction returns the actual amount; earlier Fabric mB saves convert once,
  while malformed sections and snapshots are bounded. Diamond and wooden-diamond
  fluid filters use non-consuming native item providers, and material rates are restored.
  Seven recovery/crafting tests cover exact half-bucket shards, native item storage,
  nested rollback and replacement failure, legacy NBT, actual survival breaking and
  player pickup/tank use, creative loot suppression, exact-NBT sample filters,
  all 374 upgrade/undo/dye combinations, sealant ingredient consumption and bucket
  remainders, and native recipe packet round trips.
  Seven machine-recovery tests cover real survival/creative/explosion removal, exact
  tank shards and combustion-engine fuel/coolant/residue after NBT reload, named solid
  fuel, retained automation views, replacement isolation, repeated removal and the
  `doTileDrops` rule. Twenty new native block loot tables restore machine/marker items
  and preserve every engine/decoration subtype. Neighbor state changes and temporary
  block-entity detachment retain contents without producing drops. This applies to
  registered machine inventories/tanks, including the restored refinery and heat
  exchanger; optional development/configuration-only block loot remains pending.
  Eleven MJ tests restore all nine material rates, exact factory loss units, bounded
  native input/simulation, delayed demand routing, sided providers without block
  entities, NBT reconstruction, partial/full/replaced sinks, weighted branches,
  actual gate stop/rate/resume, passive extraction, malformed snapshots and loss modes.
  A real creative engine powers a chute through wooden/stone pipes. All 306 native
  upgrade/undo/dye combinations preserve colours and consume exactly one ingredient.
  Fourteen RF/network tests cover all nine materials, native nested/cross-face rollback,
  per-tick input/output budgets, no-block-entity providers, reload, backpressure,
  replacement sinks, weighted demand, gate stop/resume and sided passive extraction.
  RF engine simulation is transactional; configured conversion, exact-fit MJ capacity,
  bounded NBT and stale engine/Dynamo handles are verified. The native RF/Dynamo
  upgrade screens enforce one gear per slot and reject distant access. A native
  Dynamo-to-pipes-to-RF-engine round trip preserves fractional MJ, and the same chain
  powers real chute collection. Nine RF upgrade/undo pairs cover 306 dye combinations.
  Tile messages carry their native block-entity type on a versioned channel. A machine
  replacement ignores a mismatched packet instead of parsing the previous machine's
  payload and disconnecting; a regression reproduces the RF-engine/chute replacement.
  A connected GPU client completes Dynamo/RF pipes/RF engine/chute work, receives
  native flow animations and recipes, and clears both pipe animations after stopping.
  Its native RF screen synchronizes the energy balance and two shift-clicked gears;
  screenshots verify the original textures and visible remake attribution.
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
  The native gate screen now opens in that saved world, displays condition/action icons,
  and searches/selects actions. Changing a source gate from red wire output to redstone
  output darkens the wire and switches off a distant lamp; restoring red wire output
  lights both again. The condition selector shows the available localized conditions.
  Manual wire breaking exposed a client-prediction disconnect missed by server tests:
  vanilla removed the entire client block entity before attachment updates arrived.
  The client now retains the pipe while waiting for the server's attachment edit.
- Packaged client/server interaction test: a fresh flat world receives native client
  attacks for a wire, gate and pipe centre. Both attachment removals retain the same
  client block entity through server replies; the centre still removes the entire pipe
  on both sides. The test passes and the integrated server saves/exits cleanly.
  Run it with `./scripts/full-smoke.ps1 -Client -Interactions`.
- Packaged GPU client: **928 visible block states, 130 items and 232 item variants pass**
  missing-model, particle/quad-texture and expected-variant checks. It reaches the title
  screen with all **30 core and 105 transport statements** resolving their icons and
  localized descriptions. Direction, colour, wire and limit icons use the original assets.
  All 16 wire item variants select their authored textures. Wire rendering checks all
  eight corners and 36 connection shapes in lit/unlit states for every colour (1,408
  combinations), including atlas UV bounds, full-bright power state and matrix transforms.
  It exits cleanly. The latest run has no invalid resource paths, JSON-model
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
  its inventory. Quarry excavation is now covered by the additional tests below.
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
  Native wires now retain 16 colours in items/tile NBT, derive loaded-only networks,
  combine gate emitters and sync bounded per-tile power snapshots. Partial breaking
  preserves the pipe and honours Fabric protection, with client prediction retaining
  the existing block entity until the server confirms attachment changes.
  Native fluid transport now preserves seven delayed sections while using exact droplets
  and one shared transaction snapshot for fluid identity, amounts and travel state.
  Native storage queries work without a neighboring block entity and respect blocked
  attachments. The client receives typed fluid-cache replies and renders native flowing
  sprites through the translucent vertex-consumer path, including lava luminosity.
  The real GPU fixture checks 640 connection/fill/fluid combinations; a connected client
  receives tagged fluid, observes its passage and clears it after exact 81,037-droplet
  delivery. Sealant and all 11 dye-preserving fluid upgrade/undo pairs now craft through
  native recipes; cold oil residue returns its bucket. Breaking a fluid pipe drops exact
  half-bucket shards with native extraction-only item storage, preserving custom names
  on partial use and destroying the item only when empty. Native tank interactions and
  gate/pipe samples can use them. Their bounded, reload-local model cache clips actual
  animated fluid sprites to the authored mask, so precoloured lava and oil remain correct;
  all 32 fluid variants have GPU coverage. A connected client receives the upgrade
  serializer/recipes and renders four distinct fluid shards in hand/hotbar; its saved
  screenshot confirms lava, oil, gaseous fuel and water remain visually distinct.
  Registered machine tanks now recover through native fluid shards on removal.
  Native MJ pipes preserve energy across save/reload, limit both input and output per
  face/tick, and hold undelivered energy under backpressure or disabled gate limits.
  Material throughput and optional loss modes use native configuration; the API's
  resistance/absolute-loss argument swap is corrected. Nine glowstone upgrade/undo
  recipe pairs preserve dye. Native rendering applies world matrices and clips moving
  UVs at atlas boundaries; 768 GPU cases cover load, connection masks and scroll phases.
  A connected client observes a real engine powering a chute, receives both pipe
  animations and recipes, then verifies zero animation after the engine is switched off.
  RF pipes now use native Team Reborn transactions for input, passive extraction,
  demand probes and actual delivery. Rollback restores buffers and tick quotas together.
  Native RF animation shares the matrix/atlas-safe geometry path, with 768 additional
  GPU cases. Redstone converts all nine MJ pipe materials to RF and undo preserves dye.
  Obsidian/stripes behavior, gate/wire crafting
  and remaining pluggables still require integration. Registration of a pipe type
  does not establish its special behavior. Pluggable render APIs remain client-only
  so dedicated servers can load all pipe definitions.
- Separated client packet callbacks from common network initialization. Removed source
  classes shadowing Minecraft's native `Chunk` and LWJGL's `BufferUtils`; the latter
  prevented a real client from initializing LWJGL 3.
- Added the native MJ Dynamo energy bridge with exact MJ conversion, per-tick output
  allowance, nested transaction rollback, directional output, upgrade inventory,
  persistence, native screen and animated model. It remains optional and disabled by
  default. The RF engine now has native transactional energy input, configurable
  RF-to-MJ conversion and original iron/gold gear upgrades. Exact-fit conversion does
  not stall; full engines retain RF. Both converter screens enforce one gear per slot,
  and retained native energy handles stop working after removal. RF engine activation
  opens its native screen with synchronized RF balance and remake attribution.
  Survival recipes for the optional converters still need restoration.
- Restored native snapshot request/response packets with ownership checks, bounded
  payloads, content-hash verification and bounded client caches. Zone-map packets now
  carry dimension identifiers, preserve negative/zero heights, reject unloaded or
  out-of-range requests and limit request/cache growth. Their preview/planner GUI
  integration remains unfinished.
- Heat-exchanger models now use a native baked-model wrapper with a reload-aware state
  cache. The auto-workbench recipe preview uses the real crafting recipe manager.
  Actual item auto-workbench crafting and its native screen are now restored;
  heat-exchanger multiblock operation and fluid rendering are restored below.

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
  clear on world changes/disconnect. Quarry appearance/drill movement and mining-well
  shaft/LED appearance and pump collection have now been observed in the real client; other integrations
  still need validation.
- Mining tubes now use native shape/hardness settings, bounded vertical owner lookup and
  Fabric's cancellable block-break event to protect a connected miner's tube in creative
  mode. Mining-well shaft placement, repair, retraction and this callback now pass
  full-mod world tests; native pump collection now passes world/client checks too.
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
  Later packaged-mod milestones below cover engine fluid handling, refinery recipes
  and native oil generation. Swimming/breathing and broader multiplayer behavior still
  need their own gameplay validation.
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
  Inventory/fluid gate statements now use sided Fabric storage and pass full-mod world
  tests described above. Other machines still need their work predicates restored.

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

### Native quarry excavation

- Restored marker-selected and default quarry bounds, actual frame placement through
  native player interaction, clearing, paid drill movement, hardness-based mining and
  pipe-first output through Fabric item storage. Overflow drops exactly once. Water
  columns remain passable; bedrock and native mining protection remain respected.
- Mining reaches the world's actual bottom instead of stopping at Y=0. Legacy saved
  quarries extend their old zero-depth bounds when loaded. Frame scanning and empty
  mining searches have per-tick limits and a bounded pending queue.
- Partial tasks serialize without recursion and restore their type, paid work and
  battery. Invalid saved bounds/tasks and malformed render packets are rejected.
  Native world updates repair missing frames and revisit newly placed mining blocks.
  Moving rails/supports participate in native collision queries on both sides.
- Quarry-owned chunk tickets are scoped by world and owner, persist across world
  restarts, respect NONE/STRICT_TILES settings, and do not alter vanilla forced chunks.
  Removing a quarry releases only its own tickets and frames; stale machine handles
  cannot detach a replacement or accept energy after removal.
- Restored the original survival recipe and localized placement/completion
  advancements, with **Remake by FelixMantra** attribution. Fifteen additional
  packaged-mod world tests cover these paths; the full suite now passes 126 tests,
  including actual engine → wooden/stone MJ pipes → quarry excavation.
  The focused fixture still passes 148 regression tests and 69 world tests.
- The real client places the quarry using a block item, obtains its placement
  advancement, saves partial work and reopens the same world with the player far away.
  Saved tickets load the quarry without a nearby player; paid work plus remaining
  energy is conserved as the machine continues. The GPU displays moving rails/drill,
  and native excavation delivers exactly four diamond blocks and four gold blocks.
  This run passes and saves/exits cleanly; its screenshot is
  `verification/quarry-client-run/screenshots/quarry-smoke.png`.

### Native mining wells

- Replaced the mining-well no-op loop with vertical excavation, hardness-based MJ
  consumption, native protected drops, pipe-first sided output and exact overflow.
  Water columns are passable; bedrock and viscous fluids stop the shaft. The original
  iron/gear/pickaxe recipe and native recipe-book unlock are restored.
- Native world changes are indexed by exact column and immediately retarget the
  machine. Redstone pauses work without consuming ordinary stored power. Depth
  configuration uses the actual world height and can change while the miner runs.
- Paid work uses 64-bit values and saves the target block state. Reloading preserves
  progress beyond the old integer range; changing targets cannot reuse that work.
  Native break protection retains paid work for a later permitted retry. Invalid
  saved positions and malformed render/length packets do not corrupt machine state.
- Shafts place actual tube blocks only in passable cells and honor native interaction
  protection. Repair/retraction preserves new solid obstructions and removes the old
  lower tubes. Connected tubes reject creative breaking; detached tubes can be removed.
  Removing the miner cleans its shaft and invalidates cached energy endpoints.
- Twelve additional packaged-mod tests pass, bringing the suite to **138**. Coverage
  includes native engine → MJ pipes → mining-well excavation, live pipe output,
  full-inventory overflow, partial/reloaded work, protected cells, redstone, depth
  changes and actual survival crafting.
- The real client passes native placement, visible shaft extension and LED state,
  saving/reopening 40 MJ of partial work, then excavation powered only by an engine
  and wooden/stone MJ pipes. It observes both longer and fully retracted shafts,
  checks exact diamond/gold-block output, and removes the miner through native player
  breaking. Both active/completed screenshots were inspected; the client saves/exits
  cleanly. Images are in `verification/mining-well-client-run/screenshots/`.

### Native pumps

- Replaced the pump placeholder with a 16-bucket persistent native tank and an
  incremental connected-fluid search limited to 2,048 visited cells per tick. Search
  respects loaded chunks, world height, shaft depth and radius; normal fluids search
  upward/horizontally and gases downward/horizontally. Each drain rechecks the source,
  connected path and shaft before spending previously unpaid work.
- Collection costs 10 MJ per bucket. Source identity and partial payment survive a
  fresh search after reload. Redstone pauses collection; buffered output still flows.
  Output uses Fabric transactions, supports exact fractional extraction and rejects
  outside insertion. Full or incompatible tanks preserve source blocks and energy.
- Native drainable blocks provide water, lava and BuildCraft oil/gas; waterlogged
  hosts remain intact. The machine owner is checked through native interaction
  protection. Renewable water requires two real neighboring sources, native support,
  the water-source-conversion rule and the consume-water setting. Flow alone cannot
  generate buckets. Finite drains reserve a whole bucket before changing the world.
- Shared miner synchronization parses pump extensions before applying common state;
  truncated tank/LED packets leave energy and shaft state intact. Stable shafts no
  longer dirty their block entity every tick; tank updates follow actual fluid changes.
- Restored the iron/gear/tank/bucket recipe and native recipe-book unlock. Draining,
  oil-platform and Black Gold advancements resolve the owner on the machine's server.
  Modern world-bottom and legacy Y=0 oil-spring markers track pumped sources. The
  localized pumping advancement includes **Remake by FelixMantra**.
- Sixteen additional packaged-mod tests bring the suite to **154**. They include a
  2,206-cell reservoir, manual buckets, exact removal shards, native pipe delivery,
  protection, world-bottom oil credit and malformed network data.
- The real client verifies native placement, shaft/LED animation, full world restart
  with 4 MJ of paid work, engine/MJ-pipe power, and exactly nine finite oil buckets
  delivered through a stone fluid pipe. Owner advancements, client empty state,
  retraction and native removal are checked. Screenshots are under
  `verification/pump-client-run/screenshots/`.

### Native automatic item workbenches

- Restored native crafting from exact item/NBT ghost patterns. A complete ingredient
  extraction and result insertion share one Fabric transaction; missing ingredients
  or insufficient output space roll back the whole operation. Native dynamic recipes
  calculate the actual result, including repaired-item durability. Recipe remainders
  return to compatible material slots or leave through native adjacent output/spill.
- Original passive crafting and external MJ now share the same saved progress:
  0.2 MJ/tick, 40 MJ per craft, and the original 2 MJ/tick decay while unable to craft.
  Input is bounded and supports simulation; removed machines reject cached energy
  and item references. Recipe editing/replacement resets unrelated progress, while
  loading NBT before world attachment preserves the original pattern and payment.
- Restored proportional material filters and all-sided insert-only materials /
  extract-only output. Real materials remain accessible in the GUI after a pattern
  change. Phantom pattern/filter stacks cannot be extracted or dropped on removal.
- The registered native screen synchronizes preview, materials, filters and bounded
  progress properties. It provides searchable unlocked recipes and a visible
  **Remake by FelixMantra** credit. Recipe IDs are validated on the server; derived
  filters/preview reject editing, and phantom packets validate fully before applying.
- Restored the two-stone-gears/crafting-table recipe, recipe-book unlock and native
  owner advancement. Twelve added world tests pass, bringing the complete suite to
  **166**. A previous oil-spring fixture now replaces its below-structure marker so
  repeated runs cannot retain another run's pumped-source counter.
- The real client places the workbench, uses native ghost/shift clicks, observes
  passive progress, reopens paid work, and connects an engine through MJ pipes.
  Searchable recipe selection produces exactly eight logs → 32 planks → 64 sticks;
  owner advancement and removal preserve only the real output. Screenshots are in
  `verification/autocraft-client-run/screenshots/`.
- The unregistered experimental fluid workbench and silicon advanced crafting table
  still require their separate integrations; this milestone restores the item machine.

### Native oil distillation

- Restored all ten original distillation recipes, including temperature-specific
  products and common-factor-reduced mB/MJ ratios. Native amounts remain exact
  droplets. The refinery registry now initializes before energy recipes.
- Horizontal ports accept distillable input only; the top extracts gas and the bottom
  extracts liquid. A complete batch extracts input and inserts both products in one
  transaction. Missing input, incompatible output or fractional overflow changes no
  materials. Wooden fluid pipes can extract the configured output port with MJ.
- Restored the original 1024 MJ reservoir and its throughput ramp up to 6 MJ/tick.
  Reservations count against capacity and survive world attachment/save reload.
  Blocked, removed or changed recipes refund reserved energy without overflow;
  stale interfaces reject removed/replaced machines.
- Registered the native tank screen, progress/reservoir properties, server-side
  bucket/shift-click interactions, facing-aware fluid renderer, survival recipe,
  recipe-book unlock and owner advancement. The GUI and advancement include
  **Remake by FelixMantra**. Output widgets allow withdrawal only, and all three
  network tank snapshots validate before any visible fluid state changes.
- Thirteen new tests bring the packaged-world suite to **179 passing tests**.
  They include real survival-player bucket consumption, both blocked outputs,
  exact-NBT rejection, bounded/simulated MJ, all ten recipes, paid-work reload,
  runtime recipe changes, wooden-pipe extraction, exact shard recovery and a
  truncated synchronization packet.
- Heat exchangers now support the temperature-changing refinery chain described below.
- The real client places the distiller and loads a bucket through native shift-click,
  preserves exactly 4 MJ through a complete world restart, then uses a live engine/MJ
  network to refine 1,000 mB oil into 2,000 mB gas and 375 mB heavy oil. Native GUI
  extraction returns one gas bucket, rejects reverse filling and synchronizes all
  tank levels. Removal recovers exactly the remaining 1,375 mB as shards. Fluid
  rendering and the progress/refined screens were visually inspected; screenshots
  are in `verification/distiller-client-run/screenshots/`.

### Native multiblock heat exchange

- Restored three-to-five-block structures with four 2,000 mB tanks, the original
  120-tick warmup and 5/10/20 mB per tick rates. All 42 original heating/cooling
  recipes are registered, including water/lava consumption and all ten oil/fuel
  families. Both inputs and products commit in one native fluid transaction, with
  exact fractional droplets and no loss when an output is full or incompatible.
- Cold input enters below the starting block and heated output leaves above the
  ending block. Hot input and cooled output use the two outer horizontal ends.
  Output tanks reject insertion and automatically transfer to neighboring storage;
  native survival bucket interactions can also recover dormant contents.
- Structure scans never load chunks. Incomplete, oversized or detached structures
  stop, and retained storage handles validate every member. Extension and native
  wrench reversal move tank ownership without duplicating contents. Merged dormant
  sections keep compatible fluid and recover excess/incompatible fluid as exact
  shards. Every affected block receives the wrench permission check.
- Tank contents and warmup survive a complete world restart. Native client snapshots
  validate before changing visible state. Restored fluid levels, counterflow
  animation, water/lava particles, survival crafting and owner advancement; the
  tooltip and advancement include **Remake by FelixMantra**.
- Twenty-one new world tests bring the complete packaged-mod suite to **200 passing
  tests**. They exercise every thermal conversion, all three rates, output blocking,
  fractional capacities, exact-NBT filters, rollback, save/reload, topology changes,
  stale views, native wrench/buckets, automatic output, shard recovery, malformed
  snapshots and runtime recipe replacement/removal.
- The real client places the structure, loads oil/lava through native survival
  interactions and reopens saved warmup. It delivers exactly 1,000 mB warm oil to a
  tank, withdraws the bucket and refines it using a live engine and MJ pipes into
  1,250 mB warm mixed-light fuel and 250 mB warm dense oil. It verifies native
  advancements, reverses the whole structure with a wrench, rejects the old port,
  invalidates a broken structure and recovers all 1,500 mB of distiller products.
  The client exits cleanly with zero fixture failures. Running, refinery and reversed
  screenshots were visually inspected in
  `verification/heat-exchange-client-run/screenshots/`.

### Native oil fields and world generation

- Replaced the deferred generator with a registered native feature, configured/placed
  feature resources and Fabric biome decoration. Seed-derived deposits retain the
  three original sizes, underground spherical reservoirs, terrain-following tendrils,
  optional tapered spouts and large-well oil springs. Reservoir depth and springs
  follow the actual world bottom, including negative Y in 1.20.1.
- Each pass writes only its decorating chunk. Neighboring candidates are planned from
  the seed and native noise/height generator without loading their chunks. Tests guard
  every block access and compare opposite chunk-generation orders. Replacement keeps
  block entities, constructed blocks, waterlogged hosts and bedrock outside the large
  well's connecting bore. Native fluid ticks make the generated oil usable normally.
- Generation honors the global/oil switches, probability/rate settings, spout ranges,
  biome bonuses and both blacklist/whitelist modes. Native `excludedDimensionNames`
  combines with legacy IDs (`0`, `-1`, `1`) without assigning a vanilla ID to custom
  dimensions. Flat worlds are skipped; the central End island retains its exclusion
  even if the End is explicitly enabled. There is no retroactive world modification.
- Registered desert/ocean oil biomes as native data-pack resources and removed the
  obsolete Forge GenLayer classes. Small humidity/erosion partitions in the native
  Overworld climate provide seed-dependent oil fields while retaining the remaining
  climate parameters. All vanilla ocean variants are eligible; the Nether preset is
  unchanged. Config toggles control selection, while biome definitions remain present
  so existing saves can load. Native desert surface predicates include oil deserts.
- Eighteen new world tests bring the packaged suite to **218 tests**. Coverage includes
  actual feature/biome registration, reachable climate entries, biome toggles/surface
  predicates, deterministic seeds/negative coordinates, all deposit sizes, native and
  legacy dimension lists, finite/invalid rates, terrain heights, protected blocks,
  saved spring metadata and bounded spouts. Default unknown-oil-biome warnings are gone.
- A real normal-terrain client with seed 4754 and unchanged generation probabilities
  finds a naturally decorated large well at `(33, -43, -181)`, with a spout reaching
  Y=107 and a saved spring estimate of 5,133 sources. A live engine/pump/pipe network
  collects the generated oil, and a real survival interaction withdraws an oil bucket.
  Native biome search locates the desert field near `(1088, 64, 960)` and ocean field
  near `(-896, 64, -1472)`; the desert has actual sand. A complete world restart retains
  the deep reservoir, spring metadata and collected oil. The client exits with zero
  fixture failures. All four screenshots were visually inspected in
  `verification/oil-generation-client-run/screenshots/`.

Remaining work includes remaining resource/model/recipe migration, gate/wire crafting and other pluggable
items and placement, remaining machine screens, robotics/volume-planner integration, fluid/energy
integration, larger quarry/third-party compatibility scenarios, and broader connected-client/save-reload gameplay
validation. Compile/package/startup success does not establish that every machine is
ready for a survival world.

Remake by FelixMantra.
