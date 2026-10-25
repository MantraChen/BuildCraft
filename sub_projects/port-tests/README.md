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

Current coverage: 55 tests for historical bit packing, transactional inventory transfers,
machine inventory persistence/drops/access restrictions, strict NBT ingredients, native
resource-pack projection, list filters, damage/NBT matching, MJ conservation and connection
policy. Tests run from the dedicated-server initialization entrypoint,
after all common initializers (including Fabric's ingredient serializers) have completed.

For the same 55 regression tests followed by 8 tests in a real Minecraft world:

```powershell
./scripts/gradle.ps1 -p sub_projects/port-tests runGameTest --console=plain
```

The headless GameTest server starts a test world, verifies native block harvesting and
fluid detection, then saves and exits. Coverage includes ore loot, Silk Touch, chest
contents/NBT, protection-event cancellation, unbreakable blocks, inadequate tools,
collection of nearby items, and owner/held-tool preservation. Reports are written to
`verification/gametest-results.xml`. This still loads selected production classes;
full BuildCraft registration, rendering, machines and transport require separate checks.

Remake by FelixMantra.
