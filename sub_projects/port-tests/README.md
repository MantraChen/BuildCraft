# Fabric regression fixture

Runs selected migrated production classes and their JUnit tests under Fabric Loader,
including Minecraft's required transformations and access wideners.

From the repository root on Windows:

```powershell
./scripts/gradle.ps1 -p sub_projects/port-tests runServer --console=plain
```

The dedicated JVM exits after printing `BUILDCRAFT PORT TESTS`. It does not create a
world or launch the full BuildCraft mod. This fixture is **not** evidence of full-mod
compilation or startup; the root `remapJar`, full-mod fixture and real client checks
cover those separately.

Current coverage: 148 tests for historical bit packing, transactional inventory transfers,
machine inventory persistence/drops/access restrictions, strict NBT ingredients, native
resource-pack projection, list filters, damage/NBT matching, MJ conservation and connection
policy, persistent configuration (legacy files, reloads, migrations, defaults and bounds),
packet framing/ownership/size limits, delayed task ordering, and item/fluid cache round trips,
bounded requests, defensive copies, session reset, marker message codecs and repeatable
native registry metadata, and guide stack literals with native registry validation,
embedded SNBT commas and damage/NBT preservation. Fourteen fluid-storage regressions
cover exact droplet/mB conversion, old saves, capacity bounds, fluid/NBT isolation,
nested rollback, first-fluid lock rollback, defensive snapshots, native bucket exchanges,
insufficient capacity, stacked-bucket overflow rejection, column settling/barriers and
round-trip exchanges for all 30 production oil/fuel buckets.
Fifteen engine/inventory tests cover the original nine cold fuels, exact energy/residue
yields, fractional fuel work across reloads and variant changes, blocked residue rollback,
redstone cooldown, coolant limits, automation view permissions, whole-item ice conversion,
vanilla/modded solid fuels and real native slot removal/insertion/extraction restrictions.
Four debug-codec tests cover copied request positions, immutable/repeated response encoding,
malformed counts/faces/framing and bounded text. Three tag-list tests bind actual registry
tags to check material/form/class equivalence, broad-tag exclusions and live reloads.
Tests run from the dedicated-server initialization entrypoint,
after all common initializers (including Fabric's ingredient serializers) have completed.

Five snapshot regressions cover template/blueprint NBT and stable hashes, copy isolation,
rotated coordinate transforms, false-valued plane edits, bounded dimensions/facing/data,
legacy blueprint lists and invalid palette indices.

Eight further regressions cover Dynamo energy conversion, per-tick quotas and nested
transaction rollback; snapshot response framing, NBT payloads and content hashes;
and dimension-aware zone-map serialization including negative/zero heights.

For the regression tests followed by 69 tests in a real Minecraft world:

```powershell
./scripts/gradle.ps1 -p sub_projects/port-tests runGameTest --console=plain
```

The headless GameTest server starts a test world, verifies native block harvesting and
fluid detection, then saves and exits. Coverage includes ore loot, Silk Touch, chest
contents/NBT, protection-event cancellation, unbreakable blocks, inadequate tools,
collection of nearby items, and owner/held-tool preservation. Network tests invoke the
production router with fixture tiles/screens to check player context, replies, distance,
spectators, access denial, unloaded chunks, stale screen IDs and buffer release on failure.
These are routing tests, not a connected-client end-to-end test. Entity factory tests
exercise the production registry with fixture tiles: placed coordinates/state/type,
multiple types for one block, native ticking, NBT reconstruction, unsupported states,
disabled blocks and duplicate registration. Machine-service tests verify native work,
control, area and heat access, sided providers, and rejection of removed/unattached/unloaded
tiles without loading chunks. Six fluid tests exercise all 30 production oil/fuel
variants and native buckets, source-only collection, real scheduled gravity ticks,
gas rising, spread beyond vanilla's eight levels, finite source removal, density-based
water displacement and collision barriers. The 20×8×8 empty fluid template reserves
each test's channel independently. Reports are written to
`verification/gametest-results.xml`. Two tank interaction tests use real server players
to exercise survival overflow and creative container preservation/return-item deduplication.
Two debug-query tests verify dimension, distance, permission and unloaded-chunk rejection.
Three projectile tests exercise the production access widener and extraction service with
tipped/spectral arrows and a named, damaged, enchanted trident, including simulation,
filter/pickup denial and protection against repeated extraction.
Twelve building tests exercise the production work executor and native template placement:
MJ/material conservation, no early item reservations, cancellation/reload, failed placement,
dependency starvation, external completion, queue saturation, excavation ordering/drops,
unbreakable/protected targets and unloaded chunks. These tests use a small fixture host;
they do not instantiate the complete filler/blueprint machine or its client screen.
Ten schematic tests load the real bundled rules and exercise stair rotation/NBT,
container contents and native block-entity restoration, modern inventory costs,
furnace work/XP clearing, multi-item counts, waterlogging/source fluids, malformed tile
types, nested paths and piston-head omission. A metadata pass checks rule evaluation
for every vanilla block's default state; it does not claim every state can be built.
Nine blueprint-builder tests exercise the real snapshot builders and native material
transactions: shared item/fluid rollback, repeated requirements, exact NBT, split storage,
sub-mB residue, paid waterlogging and chest contents, dependencies, saved MJ credit,
invalid block-entity rejection, live material totals and template selection past non-block
items. Three item tests cover native snapshot kinds/headers, legacy damage and header-only
items, stack NBT round trips and malformed headers. Machine block integration, paths and
screens still require whole-mod tests.
The fixture now includes the expression dependency used by the production rule JSON
utilities. Run it sequentially with root/model builds because expression generation
shares its output directory.
This still loads selected production classes;
full BuildCraft registration, rendering, machines and transport require separate checks.

Remake by FelixMantra.
