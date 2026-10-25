# Client model regression fixture

Run from the repository root with Java 17:

```powershell
.\scripts\gradle.ps1 -p sub_projects/model-tests test --console=plain
```

This independent Loom project compiles selected **production** geometry/model classes
against Minecraft 1.20.1 and Yarn. It executes JUnit without starting Minecraft or an
OpenGL context, and includes the original expression project. The root build still
compiles the entire mod; this fixture does not bypass failures in that build.

The 14 client model tests cover all five engine assets across each facing and power
stage, piston geometry, existing texture files, emission, LED coloring, texture UV
aliases, recursive/shared parents, resource reload invalidation, all direction-to-
direction rotations, signed packed normals and transformed entity vertex output.
Fluid tests cover RGB/ABGR/alpha conversion, all six original animated heat textures,
and geometry for gas filling from a block's ceiling. The expression dependency
contributes 13 additional tests.

Ten more GUI tests cover native frame-scope nesting, icon
sizes/offsets, matrix-transformed sprite quads, nine-slice geometry for small panels,
dynamic texture ARGB pixels and disposal, smooth-font rasterization, and formatting-
aware Unicode wrapping at narrow widths. Registry-dependent guide stack parsing runs
in `port-tests` under Fabric's transformed class loader instead of plain JUnit.

Three tank-render geometry tests bring the client total to 27: liquid and gas fill
bounds, outward face winding, UV bounds, empty geometry and face-mask selection.
Run this fixture and the root Gradle build sequentially: both use the expression
project's generated source directory.

Reports are written to `build/reports/tests/test/index.html`. Successful tests do not
verify native renderer registration at runtime, GPU output, resource pack reloads in
a running client, or a playable BuildCraft mod. Those require the full build and an
actual client launch.
