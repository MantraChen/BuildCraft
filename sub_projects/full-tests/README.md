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

The twenty server tests currently verify:

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

The optional client run uses an isolated `verification/full-client-run/` directory.
After reaching the title screen, it checks all 928 registered visible block states,
81 default item models and 20 explicit item variants. It rejects missing models,
particle sprites and quad textures, and checks the expected variant textures for map
locations, decorated blocks, lists, snapshots and single-block schematics. It also
checks that the chute, tank and gear/wrench crafting chain have translated names and
that registry-reload command text remains available. It requires
a working graphical desktop and GPU; it is not a headless test. Run it separately with
`./scripts/gradle.ps1 -p sub_projects/full-tests runFullClientSmoke` after preparing the
artifact/config through `full-smoke.ps1 -Client`.

Reports are written to `verification/full-gametest-results.xml`. Test worlds/configs
are under `verification/full-gametest-run/`. These checks establish full common-side
initialization and the specific behaviors above, not working gameplay for every
machine, pipes or the RF engine. The client check verifies resource baking and item
selection, not every renderer, machine animation, UI or gameplay interaction. Legacy
guide links and some texture-animation/mipmap warnings remain.
