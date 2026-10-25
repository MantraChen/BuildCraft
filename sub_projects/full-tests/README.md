# Complete packaged-mod smoke tests

Remake by FelixMantra.

This independent Loom project loads the complete distributable produced by the root
`remapJar` task. It does not replace production classes or BuildCraft entrypoints.

From the repository root on Windows:

```powershell
./scripts/full-smoke.ps1
# Also start a real GPU client, inspect baked resources, and exit automatically:
./scripts/full-smoke.ps1 -Client
# Also verify real client/server breaking and prediction in a fresh flat world:
./scripts/full-smoke.ps1 -Client -Interactions
# Also verify quarry placement, excavation and a complete world restart:
./scripts/full-smoke.ps1 -Quarry
# Also verify mining-well animation, saved work and engine-driven excavation:
./scripts/full-smoke.ps1 -MiningWell
# Also verify pump placement, saved paid work and finite oil delivery through pipes:
./scripts/full-smoke.ps1 -Pump
# Also verify automatic crafting, native GUI editing, recipe search and saved progress:
./scripts/full-smoke.ps1 -AutoCraft
# Also verify distillation, bucket GUI packets, saved MJ and exact oil products:
./scripts/full-smoke.ps1 -Distiller
# Also verify multiblock heat exchange, saved warmup, warm-oil refining and reversal:
./scripts/full-smoke.ps1 -HeatExchange
# Also verify natural oil in a normal world, both biomes, pumping and save/reload:
./scripts/full-smoke.ps1 -OilGeneration
# Also verify laser assembly, real recipe clicks, saved MJ and engine-driven production:
./scripts/full-smoke.ps1 -Assembly
```

The script copies the jar to `verification/buildcraft-under-test.jar`. Because Loom
strips nested jars while remapping a development dependency, the script also extracts
the dependencies bundled in that exact artifact into `verification/full-test-libs/`.
The isolated test config enables the optional MJ Dynamo and RF engine to include their
registered states; it does not modify the normal client config.
The optional interaction client creates its own world under
`verification/full-client-interaction-run`, attacks a wire and gate through the native
client interaction manager, verifies the same pipe block entity survives server replies,
then verifies that targeting the pipe centre still removes the whole block on both sides.
This covers a prediction bug that server-only tests cannot detect: vanilla previously
predicted air for an attachment break, losing the pipe before its incremental update.
The same client then transfers 81,037 droplets of tagged water through wooden and stone
pipes between native tanks. It requires a renderable client fluid with the original NBT,
exact delivery on the integrated server and no remaining client fluid after emptying.
It then renders lava, oil, gaseous fuel and water shards in the player's hand/hotbar,
checks receipt of the custom upgrade recipe and captures `screenshots/fluid-shards-smoke.png`
inside the isolated interaction run directory. It then runs a creative engine through
wooden/stone MJ pipes into a chute, checks real machine work, native client animation
and MJ recipe sync, and captures `screenshots/mj-pipes-smoke.png`. After switching the
engine off, it verifies both pipe animations clear. It then runs a native MJ Dynamo
through two RF pipes and an RF engine into a chute, checks real item collection,
RF animation/recipe synchronization, and verifies stopped flow clears. It opens the
RF engine through a real client interaction, checks synchronized RF balance and
shift-click gear limits, and captures `rf-pipes-smoke.png` and
`rf-engine-screen-smoke.png` before saving and exiting.

The quarry client uses `verification/quarry-client-run/`. It places a quarry through
native item interaction beside connected volume markers, verifies its advancement,
saves a partially paid frame task, moves the player away and reopens the same world.
It requires the distant quarry to load from its saved tickets, preserve paid work,
animate its drill and deliver exactly four diamond blocks and four gold blocks.
It captures `screenshots/quarry-smoke.png` before saving and exiting.

The mining-well client uses `verification/mining-well-client-run/`. It places a well
through native item interaction, observes its extended shaft/LED, saves 40 MJ of paid
work and reopens the same world. A creative engine then drives wooden/stone MJ pipes
to finish mining, with exact diamond/gold-block output and automatic shaft retraction.
It checks native removal and stale energy rejection, capturing
`screenshots/mining-well-active.png` and `screenshots/mining-well-complete.png`.

The pump client uses `verification/pump-client-run/`. It places a pump above nine
finite oil sources, observes the shaft/LED, saves 4 MJ of paid work and reopens the
world. A creative engine and MJ pipes power collection; a stone fluid pipe delivers
exactly nine buckets to a tank. The fixture allows the pipe's actual 20 mB/t rate,
checks localized remake credit and owner advancements, then verifies retraction,
empty client fluid state, native removal and stale energy rejection. Screenshots are
`screenshots/pump-active.png` and `screenshots/pump-complete.png`.

The automatic-workbench client uses `verification/autocraft-client-run/`. Native
placement opens the registered screen; real click/shift-click packets set a ghost
pattern and transfer eight logs without consuming phantom items. The client observes
filters, preview and passive progress, then saves and reopens the world. Engine/MJ
pipes finish the planks, and the searchable unlocked-recipe browser selects sticks.
The result must be exactly 64 sticks, with native owner advancement and removal drops.
Screenshots are `screenshots/autocraft-progress.png` and `screenshots/autocraft-recipes.png`.

The distiller client uses `verification/distiller-client-run/`. It places the machine,
shift-clicks an oil bucket through the native screen and reopens a world with exactly
4 MJ of paid progress. A live engine and MJ pipes refine 1,000 mB oil into 2,000 mB
gas and 375 mB heavy oil. The client withdraws one gas bucket through the tank widget,
rejects reverse output filling, verifies the advancement and recovers remaining fluid
on removal. It captures `distiller-progress.png`, `distiller-refined.png` and
`distiller-world.png` under that run's `screenshots/` directory.

The heat-exchanger client uses `verification/heat-exchange-client-run/`. It places a
three-block structure, loads survival oil/lava buckets, and reopens saved warmup.
Exactly one bucket of warm oil reaches a native tank, is collected through a real
interaction, and enters a distiller powered by an engine and MJ pipes. The result is
1,250 mB warm mixed-light fuel and 250 mB warm dense oil. Native wrench reversal,
stale-port rejection, middle-block removal and exact product recovery must pass.
Screenshots are `heat-warming.png`, `heat-running.png`, `heat-refinery.png` and
`heat-reversed.png` under that run's `screenshots/` directory.

The oil-generation client uses `verification/oil-generation-client-run/`. It creates
normal terrain with seed 4754 and unchanged generation probabilities. It locates a
planned large deposit, requires the actual chunk decorator to have created its oil
reservoir/spout/spring, and collects oil through a native engine, pump and pipes.
The survival player withdraws an oil bucket, locates both oil biomes through the
native biome search, checks desert sand, and reopens the entire save. Screenshots
are `natural-oil-field.png`, `natural-oil-pumping.png`, `oil-desert-biome.png` and
`oil-ocean-biome.png` under that run's `screenshots/` directory.

The assembly client uses `verification/assembly-client-run/`. It places a native
table, shift-clicks real materials, toggles recipe previews through the real GUI,
checks all five chipset models and reopens a save with 2,500 MJ paid work. Four
creative engines feed wooden/stone MJ pipes and directional lasers. The fixture
requires exactly one red chipset, eight blue wires and an iron AND gate, along
with stopped beams, saved missing-input selections and the owner advancement.
Screenshots include partial work, live lasers, queued recipes and the output barrel.

The 239 server tests currently verify:

- Twenty-one assembly tests cover all 45 native recipes and packet synchronization,
  counted/overlapping/exact-NBT ingredients, original costs and variants, round-robin
  selections, paid work across missing inputs/deselection/reload, sided transactions,
  pipe-priority output, full-inventory spills, exact removal and bucket remainders.
  Six laser facings, cone/range limits, glass obstruction, rotation/target removal,
  bounded/simulated MJ, low-battery ramping, fractional final transfer, stale handles,
  malformed NBT/packets, data-pack replacement/removal and protected GUI choices are
  checked. Native crafting checks basic gates, iron upgrades and all 24 logic flips.

- Eighteen oil-generation tests cover native feature/data-pack/biome registration,
  climate reachability and toggles, actual surface predicates, deterministic seeds
  and negative coordinates, original deposit types, invalid/high finite rates, biome
  bonuses/blacklists, native and legacy dimensions, flat-world exclusion, deep oil
  and saved springs, guarded cross-chunk accesses/order, terrain-following lakes,
  protected containers/blocks, spout disabling and world-height bounds.

- Twenty-one heat-exchanger tests cover all 42 heating/cooling recipes, three-to-five
  block rates/warmup, temperature rejection, atomic blocked outputs, exact droplets,
  water/lava consumption, sided/NBT filters, saved tanks/warmup, detached and oversized
  structures, endpoint extension, native wrench reversal, dormant-section merging,
  exact recovery, automatic outputs, survival buckets/crafting, owner advancement,
  atomic snapshots and runtime recipe replacement/removal.

- Thirteen distiller tests cover all ten original recipes, exact ratios/MJ costs,
  fractional shortage and blocked-output rollback, sided/filtered access, bounded
  power and rate, saved partial work, recipe replacement/removal refunds, stale
  references, native survival bucket GUI/crafting, exact removal recovery, powered
  wooden fluid-pipe extraction, owner advancement and atomic tank snapshot decoding.

- Twelve automatic-workbench tests verify 200-tick passive crafting, bounded/simulated
  MJ input, complete rollback on missing ingredients or full output, exact-NBT dynamic
  repairs, cake bucket remainders, sided filters, native recipe replacement/removal,
  paid-work reload, stale handles, protected ghost/derived slots and atomic packets,
  wooden-pipe extraction, the survival recipe and owner advancement.
- Sixteen pump tests cover finite/renewable/flowing/unsupported water, source-conversion
  rules and live config, exact MJ costs, native oil/gas and waterlogged drains, protected
  shafts/sources, disconnected paths, redstone, partial-work reload, old storage handles,
  fractional/full/incompatible tanks, native fluid-pipe output and survival recipes.
  They also exercise a 2,206-cell reservoir across search ticks, real bucket interaction,
  exact removal shards, malformed network packets and world-bottom oil-spring credit.
- Construction, native entity-type compatibility and NBT round trips for all 849
  non-null BuildCraft block-entity states. Water springs intentionally use scheduled
  block ticks without a block entity.
- Tank placement, five world ticks and NBT serialization.
- Native sided Dynamo energy lookup, transaction simulation/commit, exact conversion
  and preservation of fractional MJ through world ticks and NBT reload.
- Native engine/Dynamo opacity settings, preventing animated models from blocking
  their own skylight.
- Both marker kinds attach to all six faces, have narrow selection boxes and disappear
  when the supporting block is removed.
- All six decorative block variants retain their kind and light level through
  pick-block, item NBT serialization and placement.
- Frames connect to neighboring frames/quarries, disconnect after removal and use
  partial collision shapes matching each arm.
- Quarries map local connector faces to all four horizontal orientations and use
  sided native item storage, including a test provider without a block entity.
- Chutes detect neighboring inventories, exclude their mouth and refresh connections
  after wrench rotation/removal without replacing the existing block entity.
- Native structure rotation and mirroring preserve frame/chute connection directions.
- Chutes collect at most three items from the correct quarter-block mouth region,
  with upward gravity work and MJ on all six faces; full/partial inventories preserve
  item counts and NBT, and energy simulation does not charge the battery.
- Output moves one item per adjacent inventory, excludes the mouth and visits a
  minecart overlapping several faces only once per tick.
- All six external storage views permit insertion but reject extraction, transactions
  roll back correctly, and breaking a chute drops its block and contents once.
- Native block-entity reconstruction retains inventory, MJ and partial gravity work;
  invalid saved progress is clamped safely.
- Successful output grants the owner's advancement. The native four-slot screen opens
  with the correct handler and sync ID, supports shift-clicks and rejects distant use;
  a wrench rotates the chute without opening the screen.
- A real crafting screen/recipe manager crafts wooden and stone gears, the wrench and
  chute from the restored native recipes.
- All 46 pipe items place through native survival use, consume one item and preserve
  their definition/dye through pick-block and unattached block-entity NBT loading.
- Pipe connections respect dye and cobblestone/stone boundaries. Native insertion
  shares transaction rollback across faces, preserves item NBT and rejects stale views.
- A chute sends nine named items through two stone pipes into a barrel without loss
  or duplication. Travelling stacks survive native save/reconstruction and delivery.
- Wooden pipes receive native MJ, simulate extraction without mutations, extract the
  corresponding item count and deliver it through a stone pipe. Saved extraction
  direction loads before a world exists; wrench rotation rejects distant players.
- Authoritative item-flow snapshots include their message discriminator, preserve
  directions, clear old client contents and reject oversized snapshots.
- Native recipes return eight basic transport pipes; all 17 structure/item recipes
  are loaded, and placement awards the pipe advancement.
- The native diamond filter screen has 54 ghost slots and 36 player slots. Filter
  copies preserve NBT and adjustable weights without consuming cursor items; shift,
  swap, clone, throw, drag and pickup-all cannot turn filters into real inventory.
  Distant players and replaced pipe instances lose editing access.
- All 54 filters survive native unattached NBT loading. Breaking the pipe drops its
  real contents once and never drops filter samples. Matching items split 2:1 by
  configured weight; unmatched items use the unfiltered side. Filling a filter row
  awards the owner advancement and unlocks the restored list recipe.
- Lapis/Daizuli colour selection and power/RF limiter settings require a wrench and
  survive NBT loading. Sneaking reverses colour selection. Actual lapis-to-Daizuli
  transport separates matching/nonmatching colours, and iron-pipe wrench rotation
  redirects subsequent deliveries to the other connected output.
- Wooden diamond pipes match whitelist/blacklist samples including NBT, allow all
  items for an empty whitelist, and extract exactly one item per sufficiently powered
  pulse. Simulated requests change neither inventory nor cursor. Ordered round robin
  waits for missing items, skips empty filter slots and resumes after save/reload
  without losing travelling stacks. Native mode buttons reject invalid IDs, distant
  players and replaced pipes; payloads and malformed saved indexes are covered.
- Actual attached gate logic enables Emzuli presets through registered action providers
  and the pipe event bus. Extraction preserves stack NBT and applies preset colours;
  a Daizuli pipe delivers each colour to its configured output after save/reload.
  Power queries do not advance the cursor or mutate inventories. Missing items wait,
  disabled presets expire, and removing the gate stops extraction. Native paint
  controls preserve all four samples/colours and reject invalid, distant and stale
  updates. Gate creation and Emzuli state packets preserve live configuration.
- All 25 gate variants place through native block interaction on all six faces,
  consume one survival item, retain variant drops/pick stacks and reload before world
  attachment. Malformed clay modifiers cannot create a zero-slot gate. Native screen
  edits travel through bounded container packets, select server-approved statements,
  link AND conditions and activate Emzuli extraction presets. Parameters cycle without
  consuming samples, side-only redstone outputs retract after clearing the action,
  and distant/replaced-gate screens cannot edit settings. Opening payloads preserve
  candidates, parameters and activation state.
- Gate actions repaint Lapis/Daizuli traffic and change the selected output; real items
  reach the expected barrels before and after pipe reconstruction. Iron gates offer
  connected outputs and reject stale choices after a neighbor disappears. All four
  MJ/RF limiter pipe types accept seven limits, reconfigure flow and persist/sync them.
  Item conditions copy an NBT-sensitive sample without consuming it and drive redstone
  as matching traffic enters; mismatches deactivate it and clearing the sample matches all.
- Gates open beside native barrels and tanks without Forge capability initialization.
  Inventory/fluid empty/full/contains/space and 25/50/75% thresholds read actual Fabric
  storage, preserve contents during simulation and respect furnace faces, item NBT,
  water/lava samples and externally registered sided tanks. Insertion-only tanks remain
  observable; a read-only face retains real capacity instead of appearing full.
- All 16 wire colours place on eight corners, consume one survival item, preserve NBT
  and drop once with the pipe. Clicking an existing corner extends to the adjacent pipe;
  occupied/distant targets are rejected and a valid connection awards its advancement.
  Real world ticks carry gate signals over a three-pipe network, retract after a split,
  and recover after rejoining and native tile reconstruction. Removing an emitter or
  unloading its holder retracts power; multiple emitters combine without mixing colours.
  Pipe dye boundaries disconnect/reconnect wires. Derived world graphs never resurrect
  saved stale emitters. Ten-byte maximum tile snapshots include all eight corners and
  power bits, reject duplicates/invalid parts atomically and cannot be applied server-side.
  Native player breaking removes only the targeted wire/gate, respects Fabric protection
  cancellation and drops nothing in creative mode.

- Native fluid input shares identity, per-tick budgets and nested rollback across faces,
  rejects stale views and supports providers without block entities. MJ extraction,
  simulation, fractional droplets, native save/reconstruction and actual tank-to-pipe
  delivery preserve amounts/NBT. Partial/full receivers and replaced sinks do not lose
  fluid; iron direction, clay priority and delayed void consumption use real world ticks.
- Forced extraction reports exact amounts without double-draining. The earlier Fabric
  millibucket save format converts once; malformed saved sections and fluid packets
  are bounded. Diamond bucket samples prioritize matching outputs and permit fallback
  around full tanks; wooden-diamond whitelist/blacklist queries leave samples intact.
  Material rates and the shorter gold-pipe delay retain their original differences.
- Fluid shards split exact drops into half-bucket containers, preserve NBT, reject
  refilling and consume the item only when empty. Partial extraction, nested rollback,
  failed item replacement, stale views and malformed/legacy saved amounts are covered.
  Actual survival breaking drops the pipe plus exactly three shards for 81,037 droplets;
  native player pickup and tank use recover the entire amount, while creative breaking
  creates no fluid loot. Recovered shards can filter exact-NBT fluids without consumption.
- All 11 fluid-pipe upgrade/undo pairs preserve plain pipes and all 16 dyes through
  actual crafting-result clicks (374 combinations), consuming the expected ingredients.
  Sealant recipes accept green dye, slime or a cold residue bucket and retain the empty
  bucket. Native recipe packet round trips preserve coloured crafting outputs.
- Machine removal recovers exact tank shards and combustion-engine fuel, coolant and
  residue after native NBT reload. Named Stirling fuel survives breaking. Retained item
  and fluid storage views become empty and reject transfers after removal, including
  after another tank is placed at the same position. Repeated removal cannot duplicate
  drops. Creative removal preserves contents without a block item; `doTileDrops=false`
  suppresses all removal loot. Native explosions recover fluid once. Actual survival
  breaking checks all engine/decoration variants and 20 machine/marker block items.
  Neighbor updates and temporary detachment preserve contents and rollback behavior.
- Native MJ pipes preserve nine material rates and correct absolute/percentage loss
  units. Bounded simulation, input overflow, removal, provider sidedness without block
  entities, persistent incoming buffers, exact multi-pipe delivery, backpressure,
  replacement sinks, weighted branches and real gate stop/limit/resume are covered.
  Passive extraction simulates before committing and retains exact amounts. Malformed
  saved energy is bounded; client packets validate atomically. All three configured
  loss modes charge once per world tick. A native engine powers actual chute pickup.
  Nine glowstone upgrade/undo pairs cover 306 dye/type combinations in native crafting.

- Native RF material rates, family separation, transactional demand probes and nested
  cross-face rollback conserve energy and tick quotas. No-block-entity providers,
  reload, partial/full/replaced sinks, weighted branches, iron gate stop/resume and
  explicit sided passive extraction run in a real world. Malformed NBT and client
  snapshots are bounded and atomic. Nine redstone upgrade/undo pairs preserve all
  306 dye/type combinations through native crafting.
- RF engine input supports nested rollback and configurable RF-to-MJ conversion,
  including an exact-fit full battery. Iron/gold upgrades restore their original rates.
  Native RF/Dynamo screens have the correct handler types and sync IDs, enforce
  one gear per slot and reject distant use. Removed engine/Dynamo energy handles
  reject transfers. A Dynamo/RF-pipe/engine round trip preserves fractional MJ;
  the real chain also powers chute collection.
- Tile updates carry their native block-entity type on the versioned channel. Replacing
  an RF engine with a chute ignores mismatched data without applying it or producing
  a reply. The connected client replaces the chute with an RF engine in the same tick,
  covering the block-update/custom-packet ordering that previously caused a disconnect.

- Fifteen quarry tests cover four placement directions, native marker adoption,
  frame building/repair, modern mining depth and old Y=0 saves, partial/completed task
  reload, protected blocks, water/bedrock columns, pipe-first output and full-inventory
  overflow. They also check native arm collision/removal, shared/stale chunk ownership,
  chunk-loading configuration, preservation of vanilla forced chunks, hardness costs,
  malformed saved tasks/render packets and actual survival crafting/advancements.
  A creative engine drives excavation through wooden/stone MJ pipes without directly
  injecting energy into the quarry battery.

- Twelve mining-well tests cover native loot and exact hardness costs, tube placement,
  repair/retraction, connected/disconnected creative breaking, 64-bit paid work and
  reload, native break/interaction protection, redstone pause, water/lava/bedrock,
  pipe output and full-inventory overflow, live depth/target changes, malformed
  saved/render state, real engine/MJ-pipe input and original survival crafting.

The optional client run uses an isolated `verification/full-client-run/` directory.
MJ and RF rendering each check 768 load, connection and scrolling-texture combinations, including
matrix transforms, full-bright lighting and wrapped UVs staying inside the atlas sprite.
After reaching the title screen, it checks all 928 registered visible block states,
130 default item models and 232 explicit item/pipe/gate/wire/shard variants. It rejects missing models,
particle sprites and quad textures, and checks the expected variant textures for map
locations, decorated blocks, lists, snapshots and single-block schematics. It also
checks that the chute, tank and gear/wrench crafting chain have translated names and
that registry-reload command text remains available. All 46 pipe models have nonempty
geometry, valid textures and vertices within block bounds, both plain and with red/blue
dye. Pipe item names are localized, and all four gate extraction-preset sprites exist
in the stitched block atlas. All 25 gates have nonempty textured geometry on six faces
in both lit and unlit states (300 combinations), within native block bounds. It requires
all 30 core and 105 transport statements to have valid atlas icons and localized
descriptions, including direction, paint, wire and power-limit choices. It requires
all 16 wire item textures to match their NBT colour. The actual wire renderer emits all
eight corners and 36 connection shapes, lit/unlit, for every colour (1,408 combinations),
with bounded atlas UVs, correct light and transformed coordinates. The production fluid
pipe renderer is checked for water/lava at five fill levels across all 64 connection
masks (640 combinations), including translucent layers, atlas UVs, transformed bounds
and lava light. All 32 water/lava/oil/fuel shard variants must use their actual fluid atlas sprite,
preserve the original pixel mask without holes, keep UVs and vertices bounded, leave
the glass untinted and show translated fluid names/amount tooltips. It requires
a working graphical desktop and GPU; it is not a headless test. Run it separately with
`./scripts/gradle.ps1 -p sub_projects/full-tests runFullClientSmoke` after preparing the
artifact/config through `full-smoke.ps1 -Client`.

Reports are written to `verification/full-gametest-results.xml`. Test worlds/configs
are under `verification/full-gametest-run/`. These checks establish full common-side
initialization and the specific behaviors above, not working gameplay for every
machine or pipe behavior. The client check verifies resource baking and item
selection, not every renderer, machine animation, UI or gameplay interaction. Legacy
guide links and some texture-animation/mipmap warnings remain.
