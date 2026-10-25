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

Results below record successive migration milestones. The latest native-guide and
atlas milestone resolves the recipe/animation/mipmap warnings mentioned in older
entries; unrelated development-client warnings and unsupported features are listed
separately there.

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
  **513 full-mod GameTests pass**. All **849 non-null registered block-entity states**
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
  Native stripes work and obsidian collection are verified below. Registration of a
  pipe type alone does not establish its special behavior. Pluggable render APIs remain client-only
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
  The later native architect and builder milestones below validate these machine,
  UI and path integrations in the packaged mod and a complete client world.
  The native entity milestone below also restores rule-selected entity reconstruction;
  plans finish only when both block and entity work have been reconciled.
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
  incoming settings are fully decoded and normalized before applying them. Full-mod
  filler integration is covered in the later native filler milestone below.
- Fixed recursive template/blueprint serialization, false-valued XZ/XY fill operations,
  snapshot dimension/facing/palette validation, repeated blueprint entity reads, missing
  filler facing/hash, and statement parameter-zero/max-count handling. Five snapshot
  tests exercise the actual serialization, hashing, copying, rotation and malformed inputs.
  Build volumes are currently bounded to 1,048,576 positions. Native entity reconstruction
  and filler/builder work visuals are restored and validated in later milestones below.
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
- The unregistered experimental fluid workbench still requires separate integration.
  Silicon's laser-powered advanced crafting table is restored in the milestone below.

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

### Native laser assembly and silicon production

- Restored the six-direction laser cone (six blocks) with air-only line of sight,
  a bounded 1,024 MJ battery, the original ramp up to 4 MJ/t, native MJ discovery
  and exact unaccepted-energy refunds. Target validation runs before each transfer;
  obstructions, rotation, removal and completed work stop the beam. Battery saves
  survive reload while targets are reacquired. Native vertex rendering uses the
  original power-coloured laser textures.
- The assembly table now uses twelve native input slots, native inventory transfers
  and a real screen with paged recipe choices, selection colours and synchronized
  MJ progress. Selected recipes rotate through available inputs and remain queued
  when ingredients run out. Generic paid MJ survives selection/input changes and
  saves. Products prefer item pipes, then inventories, then spill; each completed
  craft pays its exact cost and returns native container remainders.
- Registered `buildcraftsilicon:assembly` as a native data-pack recipe type. The
  shipped 45 recipes cover five chipset variants, six base/18 upgraded gates and
  sixteen wire colours, preserving original costs and output counts. Counted
  ingredients support optional exact SNBT; capacity matching handles overlapping
  ingredients without double spending. Recipe synchronization retains full NBT.
  Replacement/removal resolves against the current native recipe manager.
- Native chipsets have distinct NBT, names, models and creative variants; their
  tooltip and assembly screen include `Remake by FelixMantra`. Lasers and assembly
  tables have survival recipes, and successful assembly grants a native advancement.
  A normal crafting table can make basic gates/iron upgrades and flip all 24 AND/OR
  variants while retaining custom names. Tables use their original nine-pixel shape.
- Twenty-one additional tests bring the packaged suite to **239 passing tests**,
  covering the recipes, transactions, output paths, queue/reload behavior, lasers,
  malformed payloads, screen distance checks, survival crafting and owner advancement.
- The real client fixture passes native placement, shift-click input, actual recipe
  toggle packets and five distinct chipset models. A complete world restart retains
  2,500 MJ, ingredients and the selected recipe. Four creative engines feed wooden/
  stone MJ pipes and lasers, producing exactly one red chipset, eight blue wires and
  one iron AND gate at the original costs. Completion clears every beam and retains
  missing-input selections; the owner advancement and stale laser rejection pass.
  Five screenshots were visually inspected under
  `verification/assembly-client-run/screenshots/`, including the original table GUI,
  visible laser production, queued recipes and actual output barrel. The fixture
  exits cleanly with zero failures.
- Remaining silicon pluggables and legacy
  assembly-script/guide integration are still pending. Native data packs are the
  supported recipe source for the restored assembly table.

### Native advanced crafting table

- Restored the original nine-slot phantom template, fifteen material slots and nine
  outputs using native `WorkbenchCrafting` transactions. Every craft costs exactly
  500 MJ from lasers; there is no passive crafting or direct MJ input. Missing
  ingredients or insufficient output space reject work without partially consuming
  materials, producing output or wasting the stored balance. Template changes keep
  generic paid laser work, and NBT loading preserves it before world attachment.
- All faces accept materials and expose only real outputs for extraction. Templates
  remain private and never drop. Vanilla dynamic recipes use exact item/NBT samples;
  recipe remainders return to materials, with committed overflow routed to adjacent
  pipes/inventories. Runtime recipe replacement/removal uses the new recipe result.
- The native screen retains the original layout, progress bar and output preview,
  adds `Remake by FelixMantra` and supports searchable unlocked recipe selection.
  Ghost edits keep the cursor stack; preview clicks, ghost extraction and malformed
  bulk updates cannot manufacture items. Server checks validate distance and recipe
  unlocks. Native recipe synchronization preserves the red-chipset NBT ingredient
  in the table's survival recipe.
- Sixteen additional packaged-mod tests bring the suite to **255 passing tests**,
  covering energy/input/output boundaries, remainders, save/reload, dynamic recipes,
  template packets, normal crafting, native laser and wooden-pipe operation, removal
  and oversized saved balances.
- A real client places the table, edits both ghost inputs with native clicks and
  verifies neither template nor preview produces free items. A complete restart
  retains 250 MJ and eight planks. An engine/MJ-pipe/laser chain crafts sixteen
  sticks, then native recipe search fills a chest pattern and consumes another eight
  planks for exactly one chest. Output shift-click, stopped beams, stale storage and
  removal with no ghost drops all pass. The original screen, progress and recipe
  browser were visually checked under
  `verification/advanced-crafting-client-run/screenshots/`; the final client run
  exits cleanly with zero failures.

### Native integration table

- The original central slot, eight component slots and one real output are restored.
  Materials and output commit in one Fabric transaction; the captured recipe cost
  is charged exactly once after commit, including when the final inputs disappear.
  Full or incompatible output rejects further laser work and retains paid energy.
  Center/component container remainders export only after a successful craft.
- All faces keep the original bidirectional access to inputs. Output is now
  extract-only and offered first. An unfiltered extracting pipe can still withdraw
  inputs after output runs out; use filters when automating this table.
- Recipes can come from native data packs or `BuildcraftRecipeRegistry.integrationRecipes`.
  Data-pack recipes take precedence, matching is deterministic, and replacement or
  removal resolves the current definitions. The public Java API supports dynamic
  output-dependent requirements/costs with defensive input/output copies. Counted
  matching supports split stacks and overlapping ingredients without double use.
- The native screen preserves the original texture, protected preview and progress
  bar, adds `Remake by FelixMantra`, and routes recognized central items correctly
  during shift-click. Its survival recipe requires the iron chipset's NBT. Saved
  slots and generic paid work restore before world attachment; removed tables and
  cached storage handles reject further work.
- Sixteen additional packaged-mod tests bring the suite to **271 passing tests**.
  They cover exact charging, backpressure, NBT copying/merging, overlapping material
  allocation, packet bounds, recipe replacement/removal, public/dynamic Java
  recipes, all-face storage rollback, save/reload, native screen permissions,
  container remainders, native lasers, survival crafting and exact removal drops.
- No built-in integration recipes existed in the original baseline. The chipset
  upgrade under `sub_projects/full-tests/src/main/resources/data/buildcraft_full_tests/`
  is fixture-only and is not included in the distributable. Add-ons/data packs must
  provide recipes for this machine.
- The real client fixture passes native placement, center/component shift-clicks,
  protected preview and blocked-output recovery. A complete restart retains 250 MJ,
  both central items and their custom name/configuration. An engine/MJ-pipe/laser
  chain produces exactly two upgraded chipsets using the fixture data pack, charges
  every craft and stops its beam. Output extraction, stale storage rejection and
  native breaking also pass. Four screenshots were visually inspected under
  `verification/integration-client-run/screenshots/`; the client exits cleanly with
  zero fixture failures. Existing guide-link warnings remain outside this milestone.

The native recipe type is `buildcraftsilicon:integration`. Put a recipe under your
data pack's `data/<namespace>/recipes/<name>.json`, for example:

```json
{
  "type": "buildcraftsilicon:integration",
  "mj": 500,
  "center": {"ingredient": {"item": "minecraft:emerald"}, "count": 1},
  "components": [
    {"ingredient": {"item": "minecraft:iron_ingot"}, "count": 2}
  ],
  "result": {"item": "minecraft:diamond", "count": 1},
  "copy_center_nbt": false,
  "allow_extra": false
}
```

`mj` is an integer from 1 to 1,000,000. The center and up to eight component
definitions accept native ingredients, counts from 1 to 64, and an optional
`nbt` SNBT string requiring exact NBT equality. Fabric custom NBT ingredients can
instead match a subset of fields. The result may also contain an SNBT `nbt`
string. With `copy_center_nbt: true`, the consumed center's NBT is copied and the
result's declared NBT overrides matching keys. By default unrelated components
prevent a match; surplus amounts of required materials are allowed. Set
`allow_extra: true` to leave unrelated materials in place. Neither flag defaults
to true. Packet synchronization retains these counts, flags and NBT.

### Native pulsar, timer and light-sensor attachments

- All three original attachments are registered with native placement on all six
  pipe faces, collision/connection blocking, picking, drops, NBT and client models.
  Pulsars attach only to pipe behaviors accepting redstone-engine pulses. Native
  tooltips include `Remake by FelixMantra`.
- Attachment textures are included in the native block atlas. Pulsar stroke
  expressions refresh for each rendered phase, and their variable nodes are rebuilt
  after model reloads; static plate faces retain the original texture coordinates.
- Pulsars reproduce the original 20-tick cycle, manual toggle, ten-tick renewable
  gate lease and queued single requests. A single item request uses the configured
  item extraction cost; a fluid request requires one whole bucket. Failed simulation
  retains the request and changes neither the source nor the pipe. Queue/stage data
  is bounded, saved and synchronized; stale/removed attachments reject work.
- Gate choices expose constant/single actions on the pulsar's face, bright/dark
  conditions on the sensor's face, and global 5/10/15-second timer conditions.
  Sensor readings include day/night darkness; missing hardware disables a saved
  condition and stale screen selections are rejected. Each gate row now owns its
  own edge latch, and single-action latches persist across saves to prevent another
  extraction when a still-high input is reloaded.
- Restored the original three assembly recipes (1,000 MJ for a pulsar, 500 MJ each
  for a timer or sensor), bringing the native assembly list to 48. The pulsar's
  ingredient uses the same redstone-engine variant resolution as native placement,
  including named/default/legacy stacks; creative and other variants do not match.
  Native recipe packets preserve this custom ingredient.
- The original `pipes.mjPerItem` and `pipes.mjPerMillibucket` settings now drive
  both wooden extraction and single pulses. Native property bounds and world-restart
  metadata are restored; large energy offers use bounded item/fluid arithmetic.
- Fifteen additional packaged tests bring the suite to **286 passing tests**:
  native placement on every face, unsupported/occupied/distant placement, manual
  cadence, failed-request retries, exact buckets/droplets, configured costs,
  constant leases and single edges, independent gate rows, saved latches/queues,
  hardware-dependent choices, real world timer periods, daylight/night readings,
  bounded NBT/packets, assembly recipes, live item delivery and exact removal drops.
- The real Fabric client fixture also passes with `failures=0`: six-facing models
  and animation before/after resource reload, manual toggles, searched native gate
  choices, pending-request recovery across a full world restart, six exact delivered
  diamonds, and removal of the sensor/pulsar while preserving the pipe and timer.
  Six screenshots were inspected for attachment models and native gate states.
  Reproduce with `./scripts/full-smoke.ps1 -ControlPlugs`; local evidence is in
  `verification/control-plugs-tests-01.log`, `verification/control-plugs-client-05.log`
  and `verification/control-plugs-client-run/screenshots/`.

### Native transport blockers and power adaptors

- Restored both transport attachment items, original six-facing shapes/textures,
  picking, placement/counts, NBT loading, native recipes and drops. Their tooltips
  include `Remake by FelixMantra`. One structure pipe makes four blockers; six
  structure pipes, a gold ingot, a stone gear and redstone make four adaptors.
  Stone gears use the additive `c:stone_gears` tag. The original blocker advancement
  is available again, and the guide now resolves both registered item pages.
- Blockers close item, fluid, MJ and RF connections. Adaptors keep material passage
  blocked and expose only the extraction behavior's native MJ input. Adjacent MJ/RF
  pipes treat the adaptor as a machine endpoint; unrelated pipe families never join.
  Removing/replacing the attachment or its block invalidates cached input handles.
- RF input is enabled by `MJ_AUTOCONVERT_RF` or `DISPLAY_RF`, uses the configured
  `mjPerRf` and extraction costs, and retains exact micro-MJ rounding credit in NBT.
  It never stores a bulk RF battery or consumes RF for an unavailable extraction.
  Oversized offers are bounded before multiplication; loaded credit is clamped.
- Fabric transactions cover the source inventory, deferred travelling items,
  fluid pipe sections/tanks, conversion credit and diamond-wood/Emzuli cursors.
  Nested commits followed by outer abort restore all of them. Native wooden-family
  receivers implement `TransactionalMjReceiver`; add-ons can implement that contract
  to expose RF safely through an adaptor. MJ forwarding retains its native interface.
- Fourteen packaged tests bring the suite to **300 passing tests**, covering native
  placement/eligibility, blocked lookups, MJ simulation and stale handles, nested RF
  aborts, saved rounding credit, mode changes, extreme offers, fluid droplets,
  ordered filters/paint, connection types, real MJ-powered item delivery, recipes,
  bounded legacy NBT and exact attachment drops.
- The real client fixture passes with `failures=0`, including model/resource reload,
  native placement and breaking, fractional-credit recovery after closing/reopening
  the world, and a Dynamo feeding RF pipes, an adaptor and an item pipeline. Exactly
  four diamonds arrive; removing the adaptor stops extraction and removing the
  blocker reconnects the item pipe. Four screenshots were inspected. Reproduce with
  `./scripts/full-smoke.ps1 -TransportPlugs`; local evidence is in
  `verification/transport-plugs-tests-01.log`, `verification/transport-plugs-client-03.log`
  and `verification/transport-plugs-client-run/screenshots/`. The isolated client
  config enables automatic RF conversion and a 0.15 MJ/item cost to exercise rounding.

### Native lenses and colour filters

- Restored the original 34 variants: sixteen coloured lenses/filters and a clear
  version of each. They attach only to item pipes, preserve open connections, and
  support native placement, picking, drops, creative entries and EN/ZH tooltips.
  The tooltips include `Remake by FelixMantra`.
- Regular lenses paint cargo only when it crosses their face, on entry or exit;
  a clear lens removes paint. Filters never paint: matching cargo gets priority,
  other painted cargo is refused, and uncoloured cargo may use a coloured filter
  as a lower-priority route. Clear filters prefer uncoloured cargo and reject paint.
- Added 34 assembly recipes at the original 500 MJ cost. Each uses glass of its
  colour, plus iron bars for filters; clear variants use plain glass. The complete
  native assembly list now contains 82 recipes.
- NBT and bounded creation packets retain colour/filter state. Legacy item damage
  uses the original reversed dye order; legacy block colour IDs and the old
  `SILVER` enum name restore the correct colour. Detached/stale attachments cannot
  keep painting or filtering, and native transactional insertion publishes painted
  cargo only after its final commit.
- Original frame/glass models use separate native cutout/translucent buffers.
  Six-face geometry and tint caches rebuild after atlas reload. At a shared surface,
  attachment raycasts win ties with a pipe connection, so clicking a connected lens
  picks/removes the lens instead of destroying the whole pipe.
- Sixteen packaged tests cover all variants/facings, simulation, routing priorities,
  transaction rollback, stale references, save/packet compatibility, live three-way
  delivery, clear-lens passage through a neighbouring filter, native breaking,
  exact drops and recipe costs. The suite now contains 316 required tests.
  Reproduce with `./scripts/full-smoke.ps1 -Lenses`.
- The client fixture additionally checks every variant/facing before and after
  resource reload, native input painting, three branch destinations, attachment
  removal and a complete world restart with deliberately slow in-transit cargo.
  It requires exactly four red-routed diamonds, three blue-routed emeralds, two
  uncoloured gold ingots and two cleared diamonds, with no quantity changes.
- Verified results: `verification/lenses-tests-11.log` reports all 316 required
  tests passing; `verification/lenses-client-11.log` finishes with `failures=0`.
  The client also verifies all 34 creative-search entries. Four screenshots in
  `verification/lenses-client-run/screenshots/` cover the live pipeline, restored
  clear lens and inventory variants with the remake credit. Reused-world chute
  fixtures now empty/remove stale inventory entities before setup, preventing
  previous runs' minecarts or their spilled contents from affecting exact counts.

### Development-only placeholders in the original baseline

The charging table is not an unfinished Fabric migration of a working machine:
`archive/pr-4754~60` already contains only a `getTarget() == 0` implementation,
and registers its block only when `BCLib.DEV` is enabled. Its original tooltip
explicitly says nothing can be charged. Earlier notes listing it as a required
restoration were inaccurate. It remains a development-only placeholder, rather
than being advertised as a functioning production table. A new charging system
would be a separately designed feature, not a restoration of this baseline.

Remaining work includes remaining resource/model/recipe migration and other pluggable
items and placement, remaining machine screens, robotics, fluid/energy
integration, larger quarry/third-party compatibility scenarios, and broader connected-client/save-reload gameplay
validation. Compile/package/startup success does not establish that every machine is
ready for a survival world.

Remake by FelixMantra.

### Native facades

- Restored full-state basic and phased facades, native item/attachment registration,
  picking, survival drops, permissions and placement on item/fluid/MJ/RF/structure
  pipes. Solid plates block connections; hollow plates allow them. Native dynamic
  support shapes follow solid facades, and the explosion calculation uses the
  represented block's blast resistance.
- The registry discovers 2,113 eligible block states in the current mod set, with
  2,102 visible/craftable states. Liquids, block entities, invisible models and
  non-full shapes are excluded. Native `FacadeAPI.disableBlock` and
  `mapStateToStack` replace the old no-op Forge IMC calls. Mappings copy item NBT;
  empty mappings hide states from crafting and creative entries. Call these hooks
  during common initialization, before recipe reload.
- Generated native assembly recipes retain the original three structure pipes +
  one represented block, 64 MJ and six-output cost. Both forms produce 4,204
  generated recipes, in addition to the existing 82 assembly recipes. All generated
  recipes round-trip over the native recipe serializer (670,824 payload bytes for
  the facade recipes in this fixture). Explicit data-pack entries win over defaults.
  For example, `buildcraftsilicon:assembly/facades/minecraft/stone/solid` is the
  plain stone recipe; property variants use a stable SHA-256 property suffix.
- A one-item special crafting recipe toggles solid/hollow without losing phases,
  properties, custom names or unrelated item NBT. Assembly previews now show state
  property tooltips so log axes and other variants can be distinguished.
- Expanded the bounded recipe display from 512 to 4,096 entries while retaining
  the 512-selection limit. Assembly caches the native recipe-map generation and
  copied inventory values, so unchanged tables do not scan thousands of recipes
  each tick. Reloads, recipe replacement and in-place stack changes invalidate it.
- The original phased implementation stored colours but never changed its active
  world state. It now selects the first powered colour in stored order, or the
  default state (first state if no default exists). Up to seventeen unique phases
  are retained and synchronized. The original stone / red oak planks / cyan oak
  log creative example is available; the original baseline supplied no phased
  assembly recipe. Add-ons can create phased items through the public facade API.
- Ported the original beveled 2/16-thick surfaces and 8/16 hollow opening. Rendering
  uses native per-face models, preserves original UV rotation, applies biome tint,
  separates translucent glass and invalidates cached geometry on resource reload.
  GUI item transforms fit within inventory slots. Both languages and tooltips
  include the facade names, state details and `Remake by FelixMantra`.
  Solid world facades retain the original pipe cap behind the plate, including
  when the represented material is transparent glass.
- Legacy pre-wrapper saves and `SILVER` colour names are accepted. Invalid phase
  counts, duplicate colours and packet indices are bounded/rejected; invalid saved
  facades recover to stone. This is not a general converter for renamed Minecraft
  blocks in arbitrary 1.12 worlds.
- Sixteen new tests bring the complete artifact suite to 332 required GameTests.
  The client fixture checks all 4,205 creative variants, seventeen representative
  block states in both forms on six faces, UV orientation, hole geometry, sprites,
  biome tint presence, inventory bounds and resource reload. It places attachments
  through the native interaction manager, routes exactly three diamonds through a
  hollow facade, synchronizes wire-driven phase changes, restarts the full world,
  checks persistence and removes one facade without destroying the pipe.
- Final verification: `verification/facades-full-7.log` records all 332 required
  server tests passing and the real client fixture finishing with `failures=0`.
  The four reviewed screenshots under `verification/facades-client-run/screenshots/`
  show transparent/active facades, the hollow log opening, the default phase and
  correctly sized inventory variants with the remake credit.

### Native gate copier

- Registered `buildcraftsilicon:gate_copier` as a single-stack native tool with
  the original empty/full models, translated usage/status messages and
  `Remake by FelixMantra` in its tooltip. Assembly consumes one BuildCraft wrench,
  one iron chipset and exactly 500 MJ; this brings the fixed assembly list to 83
  recipes, alongside the generated facade recipes.
- An empty copier captures statements, parameters, absolute sides and group
  connections. A filled copier pastes those settings while retaining the target's
  material, modifier and AND/OR logic. Smaller targets receive only fitting rows
  and parameters with explicit warnings. A copied blank gate clears destination
  settings. Main-hand priority and offhand use match the original interaction.
- Copying excludes live wire broadcasts, activation flags and single-action
  latches. Pasted actions start with their own runtime state. Item/NBT copies do
  not alias source or destination parameters. Sneak-use clears only `gate_data`,
  retaining custom names and other item tags.
- Paste decodes and validates every fitting row before changing the live gate;
  malformed readers or variants leave the destination untouched. Missing add-on
  statements/parameter types are removed or defaulted with a warning. Detached
  gates, spectators, distant players and native interaction denial cannot edit
  the gate. Existing gate screens receive row/group updates without being replaced.
- Fixed legacy string statement loading to construct the full statement wrapper
  without mutating the saved tag. This handles the original string representation,
  not a general conversion of arbitrary renamed third-party statements.
- The save/reload regression exposed missing registration of wire action and
  trigger parameter readers. Both NBT and packet readers are now registered;
  all 16 colours, trigger polarities and empty parameters round-trip. Named item
  samples, gate-side-only redstone parameters and wire parameters survive copying
  and persisted tile reload.
- Fourteen new GameTests bring the complete packaged-mod suite to 346 passing
  tests. They cover both hands on all six faces, capacity/logic warnings, clearing,
  invalid/missing data, access checks, independent single-action latches, saves,
  all wire parameter encodings and the real assembly recipe.
- Final validation: `verification/gate-copier-final-7.log` records all 346 required
  server tests passing and the real client fixture completing with `failures=0`.
  The client uses native copy/paste and sneak-use packets, verifies empty/full
  models across resource reload, updates an already open gate screen and restarts
  the complete saved world. Four reviewed screenshots show the filled/cleared
  tools and matching pasted/live gate screens. Empty optional parameters now
  render as empty cells instead of misleading question marks.
  Machine-recovery test setup also clears old item entities from its expanded
  counting volume, retaining the original exact drop assertions on reused worlds.
  Existing guide recipe and texture-animation/mipmap warnings are unchanged.


### Native flood gate

- Replaced the empty flood-gate tick with a native two-bucket tank and connected
  world filling. All six faces expose Fabric transactional storage; five outlets
  start open, while the top has no outlet into the world. Wrenches toggle outlets
  through native interaction packets and update the original 32 block models.
  Closing an outlet does not disconnect its fluid input.
- Retained the original no-MJ, one-bucket-per-sixteen-active-ticks behavior,
  nearest-first search, 64-block radius, 4,096 placement candidates and idle
  backoff. Search work yields after 1,024 positions per tick and never loads an
  absent chunk. Liquid searches downward and horizontally; lighter-than-air
  fluids search upward and horizontally. Existing matching sources are traversed
  without charging a bucket; air and matching flowing fluid can be filled.
  Solid/waterlogged blocks, plants and different fluids are preserved.
- Every placement rechecks the connected path, open outlet, live block entity,
  current target and native owner protection. A blocked or closed path cannot
  produce detached fluid at a stale queued target. Native bucket placement
  preserves dimension behavior, including Nether water evaporation. A successful
  placement extracts exactly one bucket; fractional/tagged buffered fluid is
  retained, and registered tank recovery handles removal without stale access.
- Outlet state and exact tank contents persist. Legacy byte-array outlet masks
  are accepted without enabling the top outlet; derived searches rebuild after
  loading. Client snapshots validate the mask, fluid and bounded amount before
  applying them. Purely visual outlet changes avoid neighbor-shape updates that
  would otherwise force an adjacent chunk to load.
- Restored the original iron/bar/gear/tank crafting recipe, recipe-book unlock,
  flooding advancement and missing fluid-storage parent advancement. Manual
  tank insertion awards the parent before column balancing; successful flooding
  awards its online owner through the native server-world overload. English and
  Chinese usage text and the tooltip include `Remake by FelixMantra`.
- Twenty additional GameTests bring the packaged-mod suite to 366 passing tests
  in `verification/flood-gate-tests-4.log`. Coverage includes transactional and
  tagged remainders, protected/removed machines, path changes, bounded/unloaded
  searches, gas/liquid direction, legacy saves, malformed snapshots, Nether
  evaporation, crafting, native buckets, wooden-pipe extraction and advancements.
- Real-client validation in `verification/flood-gate-client-3.log` finishes with
  `failures=0` and a clean shutdown. It verifies all 32 models on six faces across
  resource reload, real placement/bucket/wrench/break packets, two full saved-world
  restarts, an engine driving wooden/stone fluid pipes, exactly three water sources
  and recovery of the final 37 droplets. Three reviewed screenshots show closed
  buffered outlets, the filled channel and the final closed state. Removal checks
  allow vanilla water to enter the vacated block and carry the recovered shard;
  teleport acknowledgements complete before subsequent native interactions.
  Existing guide recipe and texture-animation/mipmap warnings remain.

### Native obsidian item collection

- Replaced the empty obsidian behavior with native entity collection and MJ
  lookup on all six faces. Exactly one connected output defines the opposite
  collection mouth; ambiguous/disconnected pipes, blocked mouths and removed
  holders refuse work. Two obsidian pipes do not connect directly.
- Native entity collisions collect without MJ. Powered scans retain the original
  four forward planes, widening laterally with distance, and process one matching
  item type per call. Each item costs 0.5 MJ plus 0.25 MJ per metre of Euclidean
  distance from the pipe center, with a minimum distance of one metre. An empty
  scan retains its original 1 MJ cost, capped by positive offered power. Nonpositive
  offers are returned unchanged, and a scan never spends more than it is offered.
- Restored the entity transactor resolver for dropped stacks, native persistent
  projectiles and non-living inventory entities. Tipped/spectral arrows use their
  native pickup stack; tridents retain damage, enchantments and names. Disallowed
  and creative-only projectiles, living inventories and removed entities are
  excluded. Inventory entities use Fabric's sided transactional adapter.
- Loose stacks and projectiles share reservations across lookups. Nested aborts
  restore both source contents and pending pipe inserts; entity removal waits for
  final commit. Pipe removal during a pending extraction recovers the committed
  stack at the original position. Native power adaptors include item work and
  fractional RF credit in the same transaction. Existing standalone legacy
  transactors remain unchanged.
- Own pipe drops wait twenty ticks before recollection. Loading a saved collector
  applies the same delay to collision and powered paths, while native travelling
  item saves retain their exact items. Stale MJ references stop requesting or
  accepting energy. The existing eight-pipe obsidian/glass recipe is retained;
  English/Chinese usage text and `Remake by FelixMantra` appear in its tooltip.
- Seventeen new tests bring the complete packaged-mod suite to 383 passing tests
  in `verification/obsidian-final-4.log`, including all six orientations, range,
  energy accounting, source/NBT preservation, transaction rollback, real barrel
  delivery, RF conversion, cooldowns, native projectile types and natural falling
  items triggering vanilla collision callbacks.
- The real client completes in `verification/obsidian-client-5.log` with
  `failures=0` and a clean shutdown. Native placement, natural item collision,
  resource reload, a full restart while items are in transit, engine collection
  from four entity types and native collector breaking are verified. The barrel
  receives exactly seven diamonds, two emeralds, one spectral arrow and one
  damaged/enchanted/named trident. Five reviewed screenshots show placement,
  the travelling item, the collection scene and the native delivery inventory.
  Delivery checks wait for actual arrival within a bounded timeout, accounting
  for stone-pipe deceleration. The original purple obsidian texture is retained.

### Native stripes mining, item use and pipe relocation

- Restored the stripes working face, native gate choices, wrench rotation and the
  original gold direction rod on all six faces. Atlas reload replaces cached
  geometry. The item has English/Chinese instructions and “Remake by FelixMantra”.
- Mining consumes up to 10 MJ per tick from a hard-bounded 256 MJ transactional
  battery. Native hardness determines the total cost; a diamond-pickaxe fake
  player harvests through native break protection and loot. Long-precision paid
  progress survives saves, remains paid while protection refuses the break, and
  resets when the target or direction changes. Unbreakable targets consume no MJ.
- Incoming items use native block placement, hoes, crop planting, shears, living
  entity interactions and registered dispenser behaviours. Denied block/entity
  callbacks stop handler fallthrough and return the tool/item. Fake-player
  inventory and selected slot are restored; normal item NBT and tool wear persist.
  A virtual dispenser inventory safely receives extra bucket/bottle outputs.
- Item pipes request an end-of-world-tick extension. One pipe becomes the base
  and the complete head moves forward; void item pipes request retraction and
  are returned as catalysts. Retraction returns the removed pipe's colour,
  contents, plugs and wires, and refuses branches and machine endpoints.
  It can follow an eligible perpendicular pipe when the backward path is invalid.
- Movement preserves owner identity, head parts, battery and travelling items.
  Fresh holders invalidate cached old storage/MJ interfaces. Both affected cells
  pass native use/break checks, followed by state, identity and NBT revalidation;
  obstructed, protected, incompatible and duplicate requests return their items.
  Partial relocation exceptions restore both snapshots before refunding.
- The first real-client run found a timing bug: a fresh holder loaded saved
  relative travel times, accepted a new return stack using absolute world time,
  then added world time to both on its first tick. This could delay returned items
  by the age of the world. Item flow now resolves saved timings before admitting
  new real or phantom items. Tests cover insertion before the first loaded tick
  and actual world-tick delivery after relocation.
- The legacy block-handler registry, including minecart dismantling, was not
  invoked by the original baseline stripes behavior and is not advertised as
  restored here. Native item-on-entity interactions are covered. Other unfinished
  machines and legacy guide/asset warnings remain outside this milestone.
- Twenty-seven new tests bring the packaged-mod suite to 410 passing GameTests
  in `verification/stripes-final-4.log`. The wire graph is checked after its normal
  first-tick registration. An existing assembly spill fixture now isolates its
  adjacent cells and counts newly spawned products, avoiding leftovers from earlier
  test batches. No assembly production behavior was changed.
- The connected client completes in the same log with `failures=0` and a clean
  shutdown in 1 minute 27 seconds. It validates a full world restart, engine-driven
  mining, block and hoe use through travelling items, extension/retraction,
  exact returned pipe/catalyst counts, wrench packets and the native barrel UI.
  Seven reviewed screenshots are in `verification/stripes-client-run/screenshots/`.
  Existing guide recipe and texture-animation/mipmap warnings are unchanged.

### Native filler construction

- Restored the survival recipe from `BuildCraftBuilders.java` at historical commit
  `fd4959d1f7e8ede210557714dc98da715ac0b7f3`: black dye, volume marker, yellow dye,
  crafting table, two gold gears and a chest. A native recipe advancement unlocks it.
  The item includes English/Chinese operating instructions and “Remake by FelixMantra”.
- Connected volume markers select the machine's region. Disconnected markers are
  rejected; loaded marker identities and native break permissions are checked before
  the complete connection is consumed. Linked planner volumes retain their locks and
  saved work. Removing a planner cancels/refunds its work rather than using a stale plan.
- The native MJ input has a 16,000 MJ capacity, supports simulation and nested
  transactions, and becomes unavailable when its block entity is replaced or removed.
  Work never executes inside an open transfer transaction. Refunding previously paid
  tasks can temporarily exceed the input capacity; this credit is preserved across
  saves and prevents additional input until the buffer has room again.
- Fixed a shared executor reload bug that capped paid breaking work at the historical
  16 MJ cost despite native block hardness requiring more. Continuous unchanged gate
  actions now renew the settings lock without cancelling paid tasks every tick.
  Native placement invokes Fabric's use callback before consuming the material.
- The screen uses native slots and shape/parameter/inversion/excavation controls;
  stale screens and gate-locked changes are rejected. Status/work packets decode into
  temporary values, validate counts and progress, and only then replace client state.
  At most sixteen actual paid jobs drive the restored robot, mining beams and moving
  material previews. Paused/idle work clears, and old client work expires.
- Twenty-one packaged-mod tests cover all nineteen registered patterns, shell/frame counts,
  inversion, stair parameters, exact materials/loot, block protection, OFF/ON/LOOP,
  native item pipe supply, marker adoption, planner lifecycle, transactional MJ,
  partial work reload, recipes, GUI restrictions and malformed render packets.
  All 431 required GameTests pass in `verification/filler-full-6.log`.
- The first connected GUI check caught a packet boundary error: the base GUI packet
  embeds render data before owner/delta fields. Validation now allows that enclosing
  structure while retaining strict boundaries on standalone status/work messages.
  A regression round-trips the complete GUI envelope, including the owner profile.
- The dedicated-server planner test exposed an unguarded client renderer override;
  it now carries the same client-only annotation as the base method. Planner editing
  UI, builder work visuals and other unfinished machines remain separate migration
  work. Disabled historical horizon/flatten patterns have not been advertised as restored.
- The connected client finishes with `failures=0` and a clean shutdown in 54 seconds
  in `verification/filler-client-7.log`. It opens the real screen, cycles patterns,
  quick-moves nine materials, reloads resources, exits/reopens the world with 5 MJ
  of paid placement, fills the volume through an engine and native power pipes,
  and clears a fixture containing eight stone blocks and one obsidian block into
  exactly eight cobblestone and one obsidian. Six screenshots cover the screen,
  volume, material preview, robot/beam and resulting inventory.
- Resource reload exposed missing robot/pattern atlas entries; both are now included.
  The screen footer is visible at the default window size. The artifact used by the
  passing client differs from the 431-test server artifact only in the block atlas
  definition and client-only `GuiFiller.class`; all common/server classes are identical.
  Existing guide recipe and texture-animation/mipmap warnings remain.
- Shared construction regressions also pass in `verification/filler-shared-regressions.log`:
  148 initialization/unit tests and 69 real-world GameTests, including template and
  blueprint executors, material/MJ conservation, ownership and unloaded chunks.

### Native architect capture and snapshot crafting

- Restored shaped survival recipes for blank blueprints (paper/lapis), templates
  (paper/black dye), the architect table and builder. A native recipe serializer
  preserves blueprint/template NBT through recipe synchronization. The architect
  recipe accepts a blank blueprint and rejects templates or snapshots with extra NBT.
  Four recipe advancements unlock these recipes; successful capture grants the
  architect advancement. English/Chinese help and “Remake by FelixMantra” appear
  on the architect item and native screen.
- The architect opens a real native screen with input/output slots, a bounded name
  field and scan status/progress. Inventory hotkeys do not close a focused name
  field. Name messages check their boundary and current screen access. Rendering
  packets decode into temporary values, including the embedded GUI/owner envelope.
- Marker adoption now checks connected selection, volume/build-height/world-border
  limits and all native marker break permissions before consuming the connection.
  The protection check is shared with the filler. Connected volume boxes are locked
  for reading. Removed block entities and open transfer transactions cannot scan.
- Blueprint capture observes at most 300 blocks per tick; templates observe at most
  900. Unloaded targets pause without force-loading. Each capture owns its palette,
  block data and input fingerprint; changing input discards partial observations.
  Palette lookup uses a map. At most sixteen scan highlights and one status update
  are sent per tick, replacing the old per-block render-state broadcasts.
- Snapshot files are written through a temporary file, flushed to disk and moved
  into place before an input item is consumed. A failed write keeps the input and
  retries, with visible failure status. Compression closes its output, so a wrapper
  preserves the file descriptor until fsync. Unreadable existing snapshots cannot
  produce a dangling output header. Reload deliberately restarts partial observation
  rather than restoring an iterator without its corresponding scanned data.
- Thirteen packaged-mod GameTests cover marker adoption/protection, owner/name saves,
  repeated palette isolation, changing snapshot type, restart/stale objects,
  unloaded/oversized areas, transaction rollback and sided automation, failed-file
  recovery (including a blocked snapshot directory), native quick-move, malformed messages, recipe consumption/NBT sync,
  and exact blueprint/template reconstruction by the existing native builder.
  All 444 required GameTests pass in `verification/architect-full-client-8.log`.
  The new tests use a separate elevation to avoid connecting their marker fixtures
  to older filler tests; the complete filler suite also passes.
- At this milestone, entity schematics, complete builder path/work rendering,
  library/replacer screens and the broader migration backlog remained unfinished. It validates
  the block capture/reconstruction workflow; it does not mark the entire port complete.
- The final connected client also passes with `failures=0` and a clean 44-second
  exit in `verification/architect-full-client-8.log`, using the same packaged jar
  as the 444-test server run. It connects/consumes native markers through real
  interaction packets, names and scans a blueprint, quick-moves its output,
  exits/reopens the complete world and feeds the saved plan through the native
  builder screen. Eight stone blocks and one horizontal oak log are reproduced
  with exact item consumption and distance-based MJ cost. Four reviewed screenshots
  cover the architect UI, scan area, builder UI and reconstructed block states.
  Existing guide recipe and texture-animation/mipmap warnings remain.

### Native builder paths, transactional power and work rendering

- Builders adopt an ordered path only after checking every connected marker's
  load state, world border and native owner break permission. Reversing paths,
  closing loops and connecting a new marker to an existing endpoint preserve
  order. Paths remain bounded to 256 markers and 65,536 expanded positions.
  Invalid saved paths stay disabled even after another save/reload.
- Path and volume marker connections now use the native world's persistent state
  manager. The reader bounds lists and rejects missing or overlapping connections.
  The native marker connector's two advancements are restored in data resources;
  finishing a builder path grants the paving advancement through the actual server.
- The builder and filler share capacity-bounded, transactional MJ reception.
  Simulation, nested rollback and stale handles preserve energy. Cancelled paid
  work refunds remain stored even above the input capacity. Builders pause during
  open item/fluid transactions and expose their snapshot/material slots to native
  sided insertion and extraction.
- Blueprint placement checks the native use-block protection event with the machine
  owner's fake player before spending its reserved item/fluid inputs. Denied work
  returns its MJ and resumes when allowed. Plan type must match the snapshot item.
- Native status packets carry bounded path progress, tank contents and material
  counts. Work packets show at most sixteen paid jobs, using the original robot,
  material movement, mining beams and directional path lasers. The builder screen
  displays path progress; bilingual operating help and the item/screen credit retain
  “Remake by FelixMantra”.
- Sixteen additional packaged-mod tests cover MJ rollback/capacity/refunds, paid
  reloads, native item/fluid automation, protection, bent/reversed/looped paths,
  exact material/energy costs, persistent marker files, malformed saved paths and
  messages, work previews and the native GUI envelope. Elevated test fixtures
  explicitly clear their targets so repeated runs do not reuse prior placed blocks.
- At this milestone, entity reconstruction, library/replacer screens and the remaining
  migration backlog were still unfinished. The work continues the existing implementation.
- The final packaged server passes all 460 required tests in
  `verification/builder-full-client-6.log`. The same jar passes the native client
  with `failures=0` and a clean one-minute exit. The client uses actual connector,
  reversal, block-placement and inventory packets, reconnects the complete world
  twice, resumes the second path cell with exactly 5 MJ paid, and finishes through
  a creative engine plus wooden/stone power pipes. Replacing the blueprint with
  a clearing template returns exactly one obsidian and five cobblestone.
- The client test also caught and fixed tank status decoding: PacketBufferBC packs
  booleans into earlier bytes, so a raw subsection loses its bit cursor. Validated
  fluid fields are now re-encoded before updating client caches. Mixed water/lava/
  empty tanks synchronize and survive restart. Six screenshots capture markers,
  path progress, paid work, completed construction, mining and the final inventory;
  the GUI, work and loot screenshots were reviewed. Existing guide-recipe and
  texture-animation/mipmap warnings remain outside this milestone.
- Shared construction regression also passes: 148 unit tests and 69 subsystem world
  tests in `verification/builder-shared-tests.log`.

### Native blueprint entity capture and reconstruction

- Architect tables now capture eligible entities whose centres lie inside the scan
  volume. Capture and reconstruction wait for both block chunks and native entity
  sections to load. Plans contain at most 4,096 entities; invalid lists, positions,
  angles, factory identifiers and disallowed entity types are rejected on read.
- Entity schematics preserve native equipment, displayed items, poses, painting
  variants and cargo. All four horizontal rotations transform position, yaw and
  attachment direction; item frames support all six native faces. New construction
  creates fresh UUIDs, while matching existing entities retain their identities.
  Motion, fire, passengers, leash and unopened loot-table references are not copied.
  Players, unlisted mobs, loose items and riding groups are excluded from capture.
- Updated rules cover all nine 1.20.1 boat/chest-boat wood types, including bamboo
  rafts, distinct glow frames and the native end-crystal identifier. Furnace-cart
  fuel/push and TNT-cart fuse are reset rather than copying active runtime state.
  Native attachment, rail, crystal support, collision and world-border checks gate
  placement. Rules still determine item and fluid requirements.
- The builder reconciles entities one-to-one after block surveying, reports missing
  exact-NBT materials and reserves all placement items/fluids in one transaction.
  It preserves the original full-battery requirement: the entity stage requires
  16,000 MJ and does not deduct additional MJ. At most sixteen entity removals or
  placements are attempted per tick. Owner-aware native use/attack protection and
  post-callback plan checks prevent denied or stale operations from spending inputs.
  Excavation removes only eligible extras and returns their required items; turning
  excavation off preserves extras. Custom entity rules with recoverable fluids are
  not yet supported for removal and leave that work pending.
- Reloading a completed builder now surveys its current plan again to reconstruct
  derived material/entity counters. Waiting for entity sections is essential here:
  block chunks can be available before their saved entities. The architect's native
  advancement also uses the actual server and its restored `code_trigger` criterion.
- Thirteen additional packaged-mod tests cover eligibility, rotations, exact item
  data, painting/frame support, boat costs, minecart cargo/rails, end crystals,
  missing-material rollback, the full-battery threshold, existing-entity reuse,
  reloads, native protection, excavation, malformed input and actual architect capture.
  All 473 required tests pass in `verification/entities-full-client-3.log`.
- The same packaged jar passes the native client with `failures=0` and a clean
  one-minute exit in that log. Actual marker/architect/inventory interactions capture
  25 blocks plus an armor stand, painting, item frame and glow frame. A rotated build
  waits for a missing named helmet, resumes after supplying it, and preserves all
  reconstructed UUIDs across three complete world restarts. Equipment/pose, displayed
  items, material consumption, final client rendering and empty remaining requirements
  are checked. Six screenshots were reviewed, including the remake credit in both GUIs.
- The first client run exposed stale completed-plan counters after reload; the final
  run verifies their repair. An earlier full run failed the existing obsidian minecart
  extraction test; diagnostics were improved and both subsequent full runs passed.
  No obsidian production fix or confirmed root cause is claimed for that transient failure.
  Shared regression also passes: 148 unit tests and 69 world tests in
  `verification/entities-shared-tests.log`.
- At this milestone, riding relationships, broader modded-entity compatibility,
  library/replacer screens and the remaining migration backlog still needed work. Existing guide-recipe and
  texture-animation/mipmap warnings remain; this milestone does not declare the entire
  port complete or free of issues.

### Native single-block samples and blueprint replacer

- Single schematics now capture native block state and block-entity data before the
  block's normal interaction opens a screen. Client/server hooks run after Fabric's
  use-block event, so a denied interaction cannot capture a chest or place a sample.
  Capture converts one blank from the held stack, preserving the remainder. Blank
  samples retain the original limit of sixteen, while recorded samples stack to one.
  Sneak-use clears only schematic/legacy damage data and retains unrelated item data.
- Recorded samples are reusable placement tools. The player pays the exact required
  items, including saved inventory contents, in a native storage transaction. Missing
  named items, denied placement, a changed held item, unsupported fluids, failed block
  support and entity collisions preserve materials and the target. A replaceable
  target is restored when construction fails. Fluid requirements still use a builder,
  matching the original single-schematic restriction. Native tooltips and a material
  HUD show the saved requirements, with the remake credit retained in the tooltip.
- The replacer has a registered native screen, validated one-item input slots,
  shift-click transfer, owner/distance/stale-tile checks and synchronized status/counts.
  Successful replacement preserves the name, assigns the machine owner and a new date,
  and points the output at the newly saved content hash. The old blueprint file and
  its unrelated states/entities remain unchanged. Two samples are consumed only after
  durable saving succeeds. No-op, malformed input, unavailable files and save failures
  retain all inputs; a failed save retries. Work pauses inside open storage transactions.
- Matching is computed once per palette entry before counting block positions. The
  result count is split across two native 16-bit screen properties so large plans do
  not wrap at 65,536. Completed status and count survive block-entity reloads.
- The native GUI renders a cached, bounded preview of up to 4,096 block models from
  the authorized snapshot response. It displays input roles, the blueprint name,
  replacement feedback and “Remake by FelixMantra”. Preview rendering never places
  blocks into a fake or real world. The single-schematic recipe uses two paper and
  one lapis for four blanks. A new native replacer recipe uses four iron ingots, two
  redstone, two gold gears and a crafting table; both recipes have recipe-book unlocks.
- At that milestone, library transfer/browsing, the filler planner, riding relationships
  and the remaining migration backlog were still separate work. A block-model preview does not claim full
  block-entity/entity rendering or unrestricted compatibility with every modded schematic.
- Fifteen additional packaged-mod tests cover native use-first chest capture, split
  blanks, legacy/native clearing and stack limits, exact chest-content payment,
  rollback, native protection, reentrant held-item changes, failed support, fluids,
  collision, malformed inputs, palette/state/entity preservation, failed-file retry,
  no-op correction, screen filters/access, reloads, stale tiles and transactions,
  native builder reconstruction and survival crafting. A 100,000-block replacement
  also verifies the signed low/high property halves and persisted full data length.
  All 488 required GameTests pass in `verification/replacer-full-client-5.log`.
- That same jar passes the native client with `failures=0` and a clean 69-second
  exit in the same log. Actual interaction packets capture a chest without opening
  it, reject a missing named diamond, then copy its contents with exact payment.
  Native sample capture, replacer placement and quick-move inputs reproduce the
  partial-input workflow across a complete world restart. The changed preview and
  count synchronize; collecting the new blueprint and restarting again preserves
  its file. A native builder then constructs three bricks and one unchanged log,
  consuming exactly their materials. All five screenshots were reviewed: the
  material HUD, original/changed previews, builder inventory and finished world.
- Existing guide-recipe and texture-animation/mipmap warnings remain outside this
  milestone. The incomplete-port status remains in effect.
- Shared construction regression also passes: 148 unit tests and 69 world tests in
  `verification/replacer-shared-tests.log`.


### Native electronic library and cross-world local collections

- The electronic library now has four native one-item slots, a registered screen,
  sided insertion/extraction, saved inventories, native quick-move and owner/distance/
  stale-tile checks. Recorded media enters the upper input and returns from the upper
  output after saving to the player's local collection. The lower input accepts any
  blueprint/template media, as in the original library, and produces the selected
  collection entry only after saving its complete content in the current server.
- Collections live in the client's existing `snapshots-client` directory and travel
  across worlds and servers. Local entries retain their original name, owner UUID
  and creation date. Cache and file lookup use the content hash independently of
  metadata; metadata updates are durable. Local deletion clears the local cache/file
  without removing a server copy. The screen includes search, five-entry pages,
  progress/status, native slot transfer and “Remake by FelixMantra”. Its full layout
  fits the default 854×480 window at automatic GUI scale.
- Full snapshots use a separate bounded transfer from the small preview protocol.
  Thirty-KiB chunks leave room for the native screen envelope under Minecraft's
  32,767-byte upload limit. Compressed content is limited to 16 MiB and expanded NBT
  to 64 MiB, with an additional NBT allocation budget. Ordered chunks, content hashes,
  canonical metadata, exact packet lengths, direction, per-screen transfer tokens,
  current inventory versions and screen access are checked. Each machine leases its
  input to one viewer while keeping that viewer's selection separate from others.
- The original fifty-tick preparation remains. Idle and overall timeouts, closing the
  screen, replacing input, stale tiles, blocked output and foreign inventory
  transactions cannot consume media. Files are written atomically and synchronized
  before success is acknowledged; save failures retain input and retry. Runtime
  transfer buffers are discarded on cancellation; saved media remains in the machine.
- A new native survival recipe uses four iron ingots, redstone, two gold gears, a book
  and a chest, with a recipe-book unlock. Existing original authorship is preserved.
- Thirteen additional packaged-mod tests cover a maximum-volume multipart template,
  chest block data and native entities, malformed chunks/hash/header/lengths, local
  metadata updates/deletion, complete native upload, failed-save retry, exact export
  acknowledgement, changed/closed inputs, multiple viewers, timeouts, sided storage,
  inventory reload, stale/distant screens, crafting and trailing packet rejection.
  Serverbound test messages also pass the real complete-packet size validator.
  Reused test worlds reset the library fixture; the architect blocked-directory
  fixture now uses a separate path instead of assuming no other local store exists.
- All **501** required GameTests and the real client pass against the packaged jar in
  `verification/library-full-client-6.log`. The client exits cleanly in 77 seconds with
  `failures=0`. A chest with 110,000 bytes of exact additional item data is exported
  through actual screen packets, first with a deliberately blocked local file. After
  successful retry, paging/search and metadata are checked. The server fixture file
  is removed and a different world is created; the local collection restores the
  complete blueprint there. Local deletion preserves that world copy. After another
  complete restart, native builder interaction consumes exact materials and recreates
  the chest with identical contents. All seven final screenshots were reviewed in
  `verification/library-client-run/screenshots`.
- At the library milestone, the filler planner, riding relationships, broader
  modded-schematic compatibility and remaining migration work were still unfinished. Existing guide-recipe and
  texture-animation/mipmap warnings remain. This milestone verifies the library
  workflow and does not declare the entire port complete.


### Native editable volumes and filler planner

- Replaced the inactive legacy volume events with Fabric world ticks, login and
  dimension-change synchronization. A volume retains its UUID, bounds, editing
  state, addons and machine locks in native persistent state. Unsupported legacy
  entries are retained verbatim for repair instead of being silently erased.
- Volume-box and planner items remain reusable tools, matching the original
  implementation. The connector starts a drag at a corner block, confirms with a
  click and cancels with sneak-click. Completing a drag takes priority over an
  addon beneath the crosshair, so cancellation cannot accidentally remove it.
  Addons attach to outer corner nodes; the connector opens or sneak-removes them.
  Spectators, distant players, other active editors, protected positions and
  foreign Fabric transactions cannot mutate the selected volume. Machine locks
  separately guard resizing, removal and the addon, and unloaded machine chunks
  retain their locks without forced loads.
- Immutable, dimension-scoped batches use bounded NBT data and retain the live
  client collection until a complete ordered batch arrives. World changes clear
  the previous collection. Existing addon objects are updated in place so open
  screens remain attached to their actual volume. Native screens carry explicit
  volume/addon identity instead of repeating a ray selection during opening.
- The native planner screen cycles all 19 enabled patterns, four parameter slots
  where applicable, and inversion. It displays planned block count and
  `Remake by FelixMantra`; English and Chinese labels are included. Stale screens
  close or reject writes, and machines cannot be reconfigured through a stale plan.
  Historical disabled horizon/flatten patterns remain disabled.
- Native world rendering shows volume lasers, the addon and translucent planned
  blocks. It draws exposed ghost faces in loaded chunks within render distance,
  with frustum checks and cached meshes; it does not place blocks. A confirmed
  volume can be adopted by a native filler, which consumes real materials and MJ
  and locks the plan until the machine is removed. Active drags cannot be adopted.
- New shapeless survival recipes and unlock advancements provide the tools:
  glass/lapis/area marker for the volume box, stick/redstone/lapis for the marker
  connector, and paper/gold gear/compass for the planner. These are newly supplied
  native recipes, not a claim that these recipes existed in the old branch.
- Twelve additional packaged-mod GameTests cover reusable tools, singleton addon
  placement, world-tick dragging and cancellation, explicit screen identity,
  pattern/inversion updates, stale and locked screens, persistent editing/addon
  identity, invalid-bound preservation, multipart network limits, malformed
  packet rollback, transaction/spectator restrictions, real filler payment/unlock
  and recipe registration. The creative GameTest mock hard-codes its mode, so the
  spectator check uses an actual server player with spectator mode instead. Reused world fixtures clear their planned construction
  cells before checking exact material payment.
- The complete fixture passes **513 GameTests** and the connected planner client
  against the packaged artifact in `verification/planner-full-client-5.log`.
  The client uses actual item/screen packets to create and drag a volume, attach
  the planner, switch patterns, change direction and invert a hollow box. It
  verifies dimension isolation and a complete saved-world restart, places a
  linked filler, quick-moves 27 stone, supplies MJ, checks all 27 placed blocks
  and exact material payment, rejects editing while locked and reopens the plan
  after machine removal. Eight screenshots in
  `verification/planner-client-run/screenshots` were visually reviewed.
- This fixture supplies MJ directly to isolate planner adoption; engine/pipe energy
  supply was exercised by the existing filler client milestone. Large multi-user
  collections and worst-case million-cell preview performance are not claimed
  validated here. Riding groups, robotics, broader modded-schematic compatibility
  and remaining resource/integration work still prevent declaring the entire port
  complete. Existing guide-recipe and texture-animation/mipmap warnings remain.

### Native assembly guide, synchronized recipes and legacy atlas sprites

- XML recipe tags now honor an explicit recipe type, return no recipe when that
  type has no match, and use a deterministic fallback order when omitted. The old
  reversed condition produced `Unknown recipe type null` and ignored explicit types.
- Assembly pages read synchronized native recipes, including facade recipes and
  server data packs. Exact NBT ingredients, input/output counts and MJ costs are
  retained. Recipes differing in counts or energy remain distinct. Public legacy
  assembly-registry entries remain available, with native recipe IDs taking priority.
- The reader expands its original two-column ingredient panel to four columns for
  seven to twelve inputs. Rendering and clickable item areas use the same layout.
  The existing native guide item and `Remake by FelixMantra` footer are preserved.
- Recipe-map replacement after server synchronization invalidates guide contents
  and generated pages. An already open page refreshes when a data pack adds,
  changes or removes its recipes; resource reload and a complete world restart
  preserve the synchronized data.
- A native atlas source adapts the original white sprite and both eight-pixel pipe
  ends at load time using exact nearest-neighbor pixel replication. It packs only
  the twenty referenced frames of the original oil, fuel and red-plasma strips,
  preserving their frame order, durations and interpolation. Original PNG assets
  are unchanged. Highest-priority resource-pack overrides are read through the
  native resource manager, and removing an override restores the original pixels.
- All **513 required GameTests** pass against the packaged artifact in
  `verification/guide-full-client-2.log`. The same run validates **928 block states,
  139 registered items and 232 item variants**, with zero model failures. The guide
  client verifies native item opening, typed/untyped selectors, twelve counted
  inputs, exact NBT, distinct costs, the twelfth slot's navigation, live recipe
  replacement/removal, resource-pack override/removal, global mipmap level four
  and a complete saved-world restart. It reports `failures=0` and exits cleanly;
  the guide-client Gradle invocation takes 54 seconds.
- All seven final screenshots in
  `verification/guide-resource-client-run/screenshots` were visually reviewed.
  Five new texture tests compare original RGBA pixels, explicit/implicit animation
  sequences, durations and interpolation, reject invalid frames and generate four
  mipmap levels for the real fluid assets. The model/texture suite now has **37
  passing tests**, alongside **13 expression tests**.
- The former unknown-recipe, unused-fluid-frame and mipmap-downgrade warnings are
  absent from this validated run. Offline development authentication/Realms,
  vanilla missing-sound and shader warnings are still present. Robotics, riding
  groups, broader modded-schematic compatibility and the remaining migration work
  are not covered by this milestone; the port remains experimental.
