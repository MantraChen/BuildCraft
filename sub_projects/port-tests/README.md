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

Current coverage: 88 tests for historical bit packing, transactional inventory transfers,
machine inventory persistence/drops/access restrictions, strict NBT ingredients, native
resource-pack projection, list filters, damage/NBT matching, MJ conservation and connection
policy, persistent configuration (legacy files, reloads, migrations, defaults and bounds),
packet framing/ownership/size limits, delayed task ordering, and item/fluid cache round trips,
bounded requests, defensive copies and session reset.
Tests run from the dedicated-server initialization entrypoint,
after all common initializers (including Fabric's ingredient serializers) have completed.

For the regression tests followed by 14 tests in a real Minecraft world:

```powershell
./scripts/gradle.ps1 -p sub_projects/port-tests runGameTest --console=plain
```

The headless GameTest server starts a test world, verifies native block harvesting and
fluid detection, then saves and exits. Coverage includes ore loot, Silk Touch, chest
contents/NBT, protection-event cancellation, unbreakable blocks, inadequate tools,
collection of nearby items, and owner/held-tool preservation. Network tests invoke the
production router with fixture tiles/screens to check player context, replies, distance,
spectators, access denial, unloaded chunks, stale screen IDs and buffer release on failure.
These are routing tests, not a connected-client end-to-end test. Reports are written to
`verification/gametest-results.xml`. This still loads selected production classes;
full BuildCraft registration, rendering, machines and transport require separate checks.

Remake by FelixMantra.
