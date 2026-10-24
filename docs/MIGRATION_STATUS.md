# Fabric 1.20.1 migration assessment

Assessment started on 2026-10-25 against PR #4754 head
`2051fb41455312a2e6c5ffeb972c6639e2a32bb9`.

## Recommendation

Continue in this repository and preserve its history. Reuse the expression library,
assets, algorithms and already migrated API definitions. Restore Fabric integrations
by subsystem instead of throwing away all prior work or adding more no-op shims.

The imported code is not yet a playable Fabric port. The latest original commit
message reports 358 remaining full-compilation errors. The local full compilation
on JDK 17.0.20.1 / Gradle 8.14.5 reported **357 errors** after dependency resolution.
A source scan found 1,265 Java files under
`common`, and 692 files across `common` and `BuildCraftAPI/api` matching `STUB` or
`TODO(R.Chen)`. These are file counts, not a precise count of broken features.

## Concrete gaps in the imported revision

- `build.gradle` has a selected `libLeaf` source set and a separate full `main` source
  set. Passing `compileLibLeaf` alone cannot establish that the whole mod builds.
- `BCLibInitializer` and `BCCoreInitializer` contain no-op registration/lifecycle
  paths. The log message “Initialization complete” does not establish registration.
- `BCTransportBlocks.pipeHolderTile` is unassigned, while the client initializer
  immediately tries to register its renderer. The referenced common-side
  `BCTransportInitializer` does not exist and is absent from mod entrypoints.
- `BCNetworkManager` logs tile-update packets but does not apply them.
- The source tree contains 65 classes under `net.minecraft` and 95 under
  `net.minecraftforge`, including compatibility shims with unfinished behavior.
- `TileQuarry` defers output storage, frame placement and several world integration
  paths. Merely making this class compile would not validate mining behavior.
- `BlockUtil.breakBlockAndGetDrops` removes a block and returns no drops. This must
  be repaired and tested before enabling a quarry in a real world.
- Resources still include old `.lang` files and historical model/recipe formats.
- Expression and energy dependencies need verification in the distributable jar,
  not just the development classpath.

## Validation sequence

1. Reproduce the full build and expression tests with the declared toolchain.
2. Fix compilation with real behavior and retain regression coverage for data/energy
   conservation, transaction rollback, serialization and world loading.
3. Restore registry and common/client separation, then verify dedicated-server and
   client startup.
4. Exercise block placement, save/reload, item/fluid transport, power, engines and
   quarry operation in a world before calling those features supported.

No “zero bugs” guarantee is made. Record observed test results and unsupported
features explicitly as work proceeds.

## Verified results

- Original-history import: all 60 original migration commits now count as GitHub
  contributions; see [HISTORY.md](HISTORY.md).
- Gradle wrapper: regenerated with Gradle 8.14.5, pinned to the official distribution
  SHA-256, and launched successfully on Windows with JDK 17.
- Expression library: **13 tests passed**, zero failures/errors (4 dependency tests
  and 9 expression tests). The generator produced 100 Java source files.
- Full `compileJava`: **fails with 357 errors** in the imported source. No client or
  dedicated-server startup has been claimed.
- Runtime packaging configuration now includes the expression library and Team Reborn
  Energy; final packaged-jar validation remains blocked on full compilation.

Remake by FelixMantra.
