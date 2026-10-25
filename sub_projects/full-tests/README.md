# Complete packaged-mod smoke tests

Remake by FelixMantra.

This independent Loom project loads the complete distributable produced by the root
`remapJar` task. It does not replace production classes or BuildCraft entrypoints.

From the repository root on Windows:

```powershell
./scripts/full-smoke.ps1
```

The script copies the jar to `verification/buildcraft-under-test.jar`. Because Loom
strips nested jars while remapping a development dependency, the script also extracts
the dependencies bundled in that exact artifact into `verification/full-test-libs/`.
The isolated test config enables the optional MJ Dynamo and RF engine to include their
registered states; it does not modify the normal client config.

The four tests currently verify:

- Construction, native entity-type compatibility and NBT round trips for all 839
  non-null BuildCraft block-entity states. Water springs intentionally use scheduled
  block ticks without a block entity.
- Tank placement, five world ticks and NBT serialization.
- Native sided Dynamo energy lookup, transaction simulation/commit, exact conversion
  and preservation of fractional MJ through world ticks and NBT reload.
- Native engine/Dynamo opacity settings, preventing animated models from blocking
  their own skylight.

Reports are written to `verification/full-gametest-results.xml`. Test worlds/configs
are under `verification/full-gametest-run/`. These checks establish full common-side
initialization and the specific behaviors above, not working gameplay for every
machine, pipes or the RF engine. Client rendering and UI need separate validation.
