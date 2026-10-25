# BuildCraft Fabric — Minecraft 1.20.1

**Remake by FelixMantra** (R.Chen / [MantraChen](https://github.com/MantraChen)).
Originally created by SpaceToad and the BuildCraft team. Original authorship and licenses are preserved.

This independent repository continues the work in
[BuildCraft/BuildCraft PR #4754](https://github.com/BuildCraft/BuildCraft/pull/4754).
The original 60 migration commits are ancestors of `main`, with their original hashes,
authors and dates. `archive/pr-4754` preserves the exact imported revision
`2051fb41455312a2e6c5ffeb972c6639e2a32bb9`.

**Status: incomplete experimental port.** The complete production source compiles and
the packaged mod starts a Fabric GameTest world. Rendering, transport and machine
integration still need work and gameplay validation. See [the migration assessment](docs/MIGRATION_STATUS.md)
and [the history/contributions record](docs/HISTORY.md).

## Development

Use JDK 17 and the checked-in Gradle wrapper. Initialize submodules before building:

```sh
git clone --recurse-submodules https://github.com/MantraChen/BuildCraft-Fabric.git
cd BuildCraft-Fabric
./gradlew remapJar
```

On Windows, use `./gradlew.bat`, or `./scripts/gradle.ps1 remapJar` to discover a JDK 17
extracted under `.tools/` and Git installed in its standard Windows location.
The helper changes environment variables only for its own invocation.
`./gradlew compileLibLeaf` is now an alias for the complete `compileJava` task;
`./gradlew :sub_projects:expression:test` checks the expression library.
The target is Minecraft 1.20.1, Yarn mappings and Fabric Loader/API. The distributable
is written to `build/libs/` and includes the expression and energy libraries.
The historical root Forge tests are not yet migrated, so root `build` is not the
supported validation entrypoint. Use the fixtures below.

`./scripts/full-smoke.ps1` builds the complete jar and loads that exact artifact in a
separate Fabric GameTest project. It verifies the registered block entities, native
tank placement, transactional MJ Dynamo output and chute collection, output, screens,
save data and crafting. It also verifies pipe placement, transactional item input,
wooden MJ extraction, stone-pipe delivery and travelling-item save data.
Diamond filter screens, weighted routing, colour routing and iron-pipe direction
changes are covered as well. Wooden diamond pipes support whitelist, blacklist and
ordered round-robin item extraction, with native ghost filters and persisted settings.
Emzuli presets, paint and actual gate-driven item routing are covered by the fixture.
The 25 gate variants can be placed on all six faces and configured through a native
screen with searchable conditions/actions, parameters and AND/OR group connections.
Gates control pipe direction, item paint and MJ/RF limits. Inventory and fluid conditions
use sided Fabric storage, including non-consuming sample and capacity checks.
All 16 wire colours place on pipe corners, connect matching networks and carry gate
signals. Breaking an attachment preserves the pipe and honours native break protection.
Gate/wire crafting and the remaining pluggables still need restoration.
Fluid pipes now expose native transactional storage, retain exact droplets/NBT through
save/reload and deliver MJ-extracted tank contents. Material rates, iron direction,
clay priority, void consumption and diamond/wooden-diamond fluid filters are restored.
Their client renderer uses native animated fluid sprites and synchronized fill levels.
Sealant crafts from green dye/slime or a residue bucket. All 11 fluid pipe upgrades
and undo recipes preserve dye. Breaking a fluid pipe yields half-bucket shards that
retain exact amounts/NBT and empty into native tanks; emptied shards disappear.
Shard icons clip the actual animated fluid texture to their original shape.
Machine removal now recovers registered tanks as fluid shards and clears dropped
inventories once. Cached automation references stop working after removal. Native
loot restores 20 additional block items, preserving all engine/decoration variants;
creative removal keeps machine contents, while `doTileDrops=false` suppresses them.
All nine MJ pipe materials now transport native micro-MJ with bounded per-face input
and output, delayed demand routing, backpressure and persistent buffers. Wooden inputs
accept engines/passive providers; iron/diamond gate limits stop and resume delivery.
MJ throughput and optional loss settings use the native configuration. Glowstone
upgrades/undo preserve dye. The connected-client fixture powers a chute through two
pipes and verifies animated energy, recipe sync and stopped-flow clearing.
All nine RF pipe materials expose Team Reborn energy with nested transaction rollback,
sided input, per-tick budgets, persistent buffers and gate-controlled limits. Redstone
upgrades MJ pipes to RF; undo and dye preservation work through native crafting.
The optional RF engine converts using the configured ratio, accepts iron/gold gear
upgrades, and has a native screen marked **Remake by FelixMantra**. Enable
`enableMjDynamo` and `enableRfEngine` in the generated BuildCraft config to use both
converters; they remain disabled by default. The fixture enables them and verifies
exact MJ/RF conservation, real chute work and removal of stale energy interfaces.
Quarries now build and repair frames, excavate to the world's bottom, respect native
break protection and send mined loot to pipes or nearby inventories. Partially paid
tasks and energy survive saving. Each quarry owns persistent native chunk tickets,
independent of other quarries and vanilla forced chunks. The original gear/pickaxe
recipe and localized placement/completion advancements are restored.
Mining wells now excavate a vertical column using native loot and sided item output.
Their shafts extend, repair and retract, honour mining/interaction protection, and
stop at bedrock or viscous fluids. Redstone pauses mining. Paid work uses 64-bit MJ
values and survives reloading; native engine/pipe power and the survival recipe work.
Pumps now collect connected water, lava, oil and gas sources for 10 MJ per bucket,
search large reservoirs across ticks, and output exact fluid amounts into native pipes
or tanks. Renewable water respects real source neighbors, support, config and the
native game rule. Protected cells, waterlogged hosts, full/incompatible tanks and saved
partial work are handled without duplicating fluid. Manual buckets, removal recovery,
the survival recipe and owner advancements are restored.
Automatic item workbenches now craft from a ghost pattern using native recipes and
atomic item transfers. They complete a passive craft in ten seconds, or use up to
40 MJ to accelerate it. Exact item/NBT filters, dynamic results, recipe remainders,
saved progress, output backpressure and native pipe extraction are covered. Their
native screen includes progress, material filters and searchable unlocked recipes.
See [the full-mod fixture](sub_projects/full-tests/README.md).
Add `-Client` to also launch a real GPU client and check all registered block/item
models plus the restored item variants, then exit automatically.
Add `-Interactions` to create an isolated flat world and exercise real client/server
wire, gate and whole-pipe breaking, including vanilla prediction acknowledgements,
then tank-to-pipe fluid transport, native fluid-cache replies and empty-state synchronization.
Add `-Quarry` to verify native quarry placement, client animation, saving/reopening a
world and automatic loading of a distant quarry, then inspect its exact mined output.
Add `-MiningWell` to inspect its visible shaft and LED state, reopen partially paid
work, and finish excavation using an engine and MJ pipes before testing removal.
Add `-Pump` to reopen partially paid pumping work, collect nine finite oil sources
using an engine and pipes, and verify exact delivery, client state and advancements.
Add `-AutoCraft` to exercise real ghost-slot and shift-click packets, save partial
work, and use an engine plus the recipe browser to turn eight logs into 64 sticks.
Add `-Distiller` to exercise native oil-bucket loading, saved MJ progress, live engine
and pipe supply, exact refining products, tank GUI extraction and fluid rendering.
Distillers restore all ten original temperature-specific recipes and sided ports:
horizontal input, gas above and liquid below. Each batch commits all three tanks
together. The native screen shows tank contents/MJ progress and **Remake by FelixMantra**.
Add `-HeatExchange` to place a native three-block exchanger, save/reopen its warmup,
heat one oil bucket with lava, feed the warm oil into a powered distiller, and verify
the exact products, wrench reversal and fluid recovery. Exchangers support three to
five blocks, all 42 original heating/cooling recipes, four transactional tanks and
automatic output. Their tooltip and advancement include **Remake by FelixMantra**.
Add `-OilGeneration` to create a normal seeded world, find a naturally generated oil
well, collect oil through an engine/pump/pipe network and a survival bucket, locate
both oil-field biomes and reopen the save. Underground reservoirs, terrain-following
oil deposits, spouts and world-bottom oil springs now generate in new chunks.
Desert/ocean oil fields use native climate selection; desert fields retain sand and
sandstone. Flat worlds and the Nether/End are excluded by default. The existing
`worldgen.oil` configuration controls rates, spouts and biome/dimension blacklists;
`excludedDimensionNames` accepts identifiers for custom dimensions alongside legacy
dimension IDs. Restart Minecraft after changing generation settings. Already generated
chunks are preserved. With commands enabled, use
`/locate biome buildcraftenergy:oil_desert` or `/locate biome buildcraftenergy:oil_ocean`.
Use `./gradlew runClient` for a development client. Finish compilation before starting
manual tests; recompiling its class directories while the game runs can cause missing-class errors.

Focused regression tests run with
`./scripts/gradle.ps1 -p sub_projects/port-tests runServer --console=plain` on Windows,
or `./gradlew -p sub_projects/port-tests runServer --console=plain` elsewhere. This
dedicated test fixture exits before world startup; it is not the full mod.
Use `runGameTest` in place of `runServer` to also run mining tests in a real headless
Minecraft test world. See [the fixture documentation](sub_projects/port-tests/README.md)
for the tested scope and report location.

Report issues with this remake in
[MantraChen/BuildCraft-Fabric](https://github.com/MantraChen/BuildCraft-Fabric/issues).

## Original upstream documentation

The following documentation describes upstream BuildCraft, including its historical
Forge build. Use the Fabric development instructions above for this repository.

## Welcome to BuildCraft on GitHub

### Reporting an issue

Please open an issue for a bug report only if:

* you are sure the bug is caused by BuildCraft and not by any other mod,
* you have at least one of the following:
  * a crash report, 
  * means of reproducing the bug in question,
  * screenshots/videos/etc. to demonstrate the bug.

**If you are not sure if a bug report is valid, please use the "Ask Help!" subforum.**

Please only use **official BuildCraft releases** for any kind of bug reports unless otherwise told to do by the BuildCraft team. Custom builds (for instance from Jenkins) are unsupported, often buggy and will **not** get any support from the developers.

Please check if the bug has been reported beforehand. Also, provide the version of BuildCraft used - if it's a version compiled from source, link to the commit/tree you complied from.

Please mention if you are using MCPC+, Cauldron, OptiFine, FastCraft or any other mods which optimize or otherwise severely modify the functioning of the Minecraft engine. That is very helpful when trying to reproduce a bug.

Please do not open issues for features unless you are a member of the BuildCraft team. For that, use the "Feature Requests" subforum.

BuildCraft, being an open-source project, gives you the right to submit a pull request if a particular fix or feature is important to you. However, if the change in question is major, please contact the team beforehand - we wish to prevent wasted effort.

### Contributing

If you wish to submit a pull request to fix bugs or broken behaviour feel free to do so. If you would like to add 
features or change existing behaviour or balance, please discuss it on discord before submitting a PR (https://discord.gg/v4geqgA).

Do not submit pull requests which solely "fix" formatting. As these kinds of changes are usually very intrusive in commit history and everyone has their own idea what "proper formatting" is, they should be done by one of the main contributors. 
Please only submit "code cleanup", if the changes actually have a substantial impact on readability.

PR implementing new features or changing large portions of code are helpful. But if you're doing such a change and if it gets accepted, please don't "fire and forget". Complex changes are introducing bugs, and as thorough as testing and peer review may be, there will be bugs. Please carry on playing your changes after initial commit and fix residual issues. It is extremely frustrating for others to spend days fixing regressions introduced by unmaintained submissions.

#### Frequently reported

* java.lang.AbstractMethodError, java.lang.NoSuchMethodException
  * A mod has not updated to the current BuildCraft API
  * You are not using the correct version of BuildCraft for your Forge/Minecraft versions
  * You are using the dev version on a normal game instance (or vice versa)
* Render issue (Quarry causes flickering) - Try without OptiFine first! This is a known issue with some versions of OptiFine.

### Compiling and packaging BuildCraft
1. Ensure that `Java` (found [here](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)), `Git` (found [here](http://git-scm.com/)) are installed correctly on your system.
 * Optional: Install `Gradle` (found [here](http://www.gradle.org/downloads)). You probably want to install version 4.3.1.
2. Create a base directory for the build
3. Clone the BuildCraft repository into 'baseDir/BuildCraft/'
4. Clone (and update) the submodules into 'baseDir/BuildCraft with 'git submodule init' and 'git submodule update'
5. Navigate to basedir/BuildCraft in a shell and run one of two commands:
    * `./gradlew setupCIWorkspace build` to just build a current jar (this may take a while).
    * `./gradlew setupDecompWorkspace` to setup a complete development environment.
    * With `Gradle` installed: use `gradle` instead of `./gradlew`
    * On Windows: use `gradlew.bat` instead of `./gradlew`
6. The compiles and obfuscated module jars will be in 'baseDir/BuildCraft/build/libs/&lt;build number&gt;/modules'

Your directory structure should look like this before running gradle:
***

    baseDir
    \- BuildCraft
     |- buildcraft_resources
     |- common
     |- ...
     \- BuildCraftAPI
      |- api
      |- ...
     \- BuildCraft-Localization
      |- lang
      |- ...

***

And like this after running gradle:
***

    basedir
    \- BuildCraft
     |- .gradle
     |- build
     |- buildcraft_resources
     |- common
     |- ...
     \- BuildCraftAPI
      |- api
      |- ...
     \- BuildCraft-Localization
      |- lang
      |- ...

***

### Localizations

Localizations can be submitted [here](https://github.com/BuildCraft/BuildCraft-Localization). Localization PRs against
this repository will have to be rejected.

### Depending on BuildCraft

Instructions for depending on BC 7.1.x can be found [here](https://github.com/BuildCraft/BuildCraft/blob/7.1.x/README.md) (for 1.7.10).

8.0.x hasn't been finished yet, so there are no instructions for depending on it :(

The following instructions are for BC 7.99.12 (1.12.2):

Add the following to your build.gradle file:
```
repositories {
    maven {
        name "BuildCraft"
        url = "https://mod-buildcraft.com/maven"
    }
}
````

If you want to depend on JUST the API then do this:
````
dependencies {
    deobfCompile "com.mod-buildcraft:buildcraft-api:7.99.12"
}
````

If you want to depend on JUST the lib then do this:
````
dependencies {
    deobfCompile "com.mod-buildcraft:buildcraft-lib:7.99.12"
}
````

If you want to depend on the whole of buildcraft do this:
```
dependencies {
    deobfCompile "com.mod-buildcraft:buildcraft:7.99.12"
}
```
Where `7.99.12` is the desired version of BuildCraft.
