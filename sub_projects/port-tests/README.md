# Fabric regression fixture

Runs selected migrated production classes and their JUnit tests under Fabric Loader,
including Minecraft's required transformations and access wideners.

From the repository root on Windows:

```powershell
./scripts/gradle.ps1 -p sub_projects/port-tests runServer --console=plain
```

The dedicated JVM exits after printing `BUILDCRAFT PORT TESTS`. It does not create a
world or launch the full BuildCraft mod. This fixture is **not** evidence of full-mod
compilation or startup; the root `build` and real client/server checks remain required.

Current coverage: 34 tests for historical bit packing, transactional inventory transfers,
machine inventory persistence/drops/access restrictions, strict NBT ingredients and native
resource-pack projection. Tests run from the dedicated-server initialization entrypoint,
after all common initializers (including Fabric's ingredient serializers) have completed.

Remake by FelixMantra.
