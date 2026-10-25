# Complete packaged-mod smoke tests

Remake by FelixMantra.

This independent Loom project loads the complete distributable produced by the root
`remapJar` task. It does not replace production classes or BuildCraft entrypoints.

From the repository root on Windows:

```powershell
./scripts/full-smoke.ps1
# Also start a real GPU client, inspect baked resources, and exit automatically:
./scripts/full-smoke.ps1 -Client
```

The script copies the jar to `verification/buildcraft-under-test.jar`. Because Loom
strips nested jars while remapping a development dependency, the script also extracts
the dependencies bundled in that exact artifact into `verification/full-test-libs/`.
The isolated test config enables the optional MJ Dynamo and RF engine to include their
registered states; it does not modify the normal client config.

The 40 server tests currently verify:

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

The optional client run uses an isolated `verification/full-client-run/` directory.
After reaching the title screen, it checks all 928 registered visible block states,
127 default item models and 158 explicit item/pipe variants. It rejects missing models,
particle sprites and quad textures, and checks the expected variant textures for map
locations, decorated blocks, lists, snapshots and single-block schematics. It also
checks that the chute, tank and gear/wrench crafting chain have translated names and
that registry-reload command text remains available. All 46 pipe models have nonempty
geometry, valid textures and vertices within block bounds, both plain and with red/blue
dye. Pipe item names are localized. It requires
a working graphical desktop and GPU; it is not a headless test. Run it separately with
`./scripts/gradle.ps1 -p sub_projects/full-tests runFullClientSmoke` after preparing the
artifact/config through `full-smoke.ps1 -Client`.

Reports are written to `verification/full-gametest-results.xml`. Test worlds/configs
are under `verification/full-gametest-run/`. These checks establish full common-side
initialization and the specific behaviors above, not working gameplay for every
machine, pipe behavior or the RF engine. The client check verifies resource baking and item
selection, not every renderer, machine animation, UI or gameplay interaction. Legacy
guide links and some texture-animation/mipmap warnings remain.
