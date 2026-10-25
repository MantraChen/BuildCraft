package buildcraft.test.lib.world;

import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.TickingBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercises production factories through the real world's block-entity lifecycle. */
public class BlockEntityRegistryGameTests implements FabricGameTest {
    private static final BooleanProperty PASSIVE = BooleanProperty.of("passive");
    private static final Block BLOCK = Registry.register(Registries.BLOCK, id("factory"), new FactoryBlock());
    private static final BlockEntityType<CounterTile> COUNTER = BlockEntityRegistry.register(
        id("counter"), CounterTile.class, CounterTile::new, BLOCK);
    private static final BlockEntityType<PassiveTile> STATIC = BlockEntityRegistry.register(
        id("passive"), PassiveTile.class, PassiveTile::new, BLOCK);
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static Identifier id(String path) { return new Identifier("buildcraft_port_tests", path); }
    public static void initialize() {}

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placementUsesRealTypePositionAndState(TestContext test) {
        test.setBlockState(POS, BLOCK);
        BlockEntity tile = test.getBlockEntity(POS);
        test.assertTrue(tile instanceof CounterTile && tile.getType() == COUNTER,
            "Placed tile must carry its own registered type");
        test.assertTrue(tile.getPos().equals(test.getAbsolutePos(POS)), "Placed tile must use actual world coordinates");
        test.assertTrue(tile.getCachedState().equals(BLOCK.getDefaultState()), "Factory must retain the placed state");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void variantChoosesItsOwnFactory(TestContext test) {
        BlockState state = BLOCK.getDefaultState().with(PASSIVE, true);
        test.setBlockState(POS, state);
        BlockEntity tile = test.getBlockEntity(POS);
        test.assertTrue(tile instanceof PassiveTile && tile.getType() == STATIC && tile.getCachedState().equals(state),
            "A second variant of the same block must receive its own entity type");
        test.assertTrue(BlockEntityRegistry.ticker(STATIC) == null, "Passive tiles must not get a ticker");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void worldTicksRegisteredMachines(TestContext test) {
        test.setBlockState(POS, BLOCK);
        CounterTile tile = (CounterTile) test.getBlockEntity(POS);
        test.runAtTick(5, () -> {
            test.assertTrue(tile.ticks > 0, "World must automatically execute the registered machine's ticker");
            test.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedEntityReconstructsThroughRegisteredFactory(TestContext test) {
        test.setBlockState(POS, BLOCK);
        CounterTile tile = (CounterTile) test.getBlockEntity(POS);
        tile.ticks = 123;
        NbtCompound saved = tile.createNbtWithIdentifyingData();
        test.assertTrue(saved.getString("id").equals(id("counter").toString()), "Save must identify the real mod type");
        BlockEntity restored = BlockEntity.createFromNbt(tile.getPos(), tile.getCachedState(), saved);
        test.assertTrue(restored instanceof CounterTile counter && counter.ticks == 123
            && restored.getType() == COUNTER && restored.getPos().equals(tile.getPos()),
            "Vanilla NBT loading must recreate the type, position and machine data");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unsupportedStatesAndUnknownClassesFailExplicitly(TestContext test) {
        try {
            BlockEntityRegistry.create(CounterTile.class, BlockPos.ORIGIN, Blocks.STONE.getDefaultState());
            throw new AssertionError("Expected unsupported state rejection");
        } catch (IllegalArgumentException expected) {}
        try {
            BlockEntityRegistry.type(DisabledTile.class);
            throw new AssertionError("Expected unknown type rejection");
        } catch (IllegalStateException expected) {}
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void disabledBlocksDoNotRegisterTypesOrInvokeFactories(TestContext test) {
        BlockEntityType<DisabledTile> disabled = BlockEntityRegistry.register(id("disabled"), DisabledTile.class,
            (type, pos, state) -> { throw new AssertionError("Disabled factory invoked"); }, (Block) null);
        test.assertTrue(disabled == null && !Registries.BLOCK_ENTITY_TYPE.containsId(id("disabled")),
            "A disabled block must not leave an entity type in the registry");
        try {
            BlockEntityRegistry.register(id("counter_duplicate"), CounterTile.class, CounterTile::new, BLOCK);
            throw new AssertionError("Expected duplicate class rejection");
        } catch (IllegalArgumentException expected) {}
        test.complete();
    }

    private static final class FactoryBlock extends BlockWithEntity {
        FactoryBlock() {
            super(AbstractBlock.Settings.create());
            setDefaultState(getDefaultState().with(PASSIVE, false));
        }
        @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(PASSIVE); }
        @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return state.get(PASSIVE) ? BlockEntityRegistry.create(PassiveTile.class, pos, state)
                : BlockEntityRegistry.create(CounterTile.class, pos, state);
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            return BlockEntityRegistry.ticker(type);
        }
    }

    private static final class CounterTile extends BlockEntity implements TickingBlockEntity {
        int ticks;
        CounterTile(BlockEntityType<CounterTile> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public void tick() { ticks++; markDirty(); }
        @Override public void readNbt(NbtCompound nbt) { super.readNbt(nbt); ticks = nbt.getInt("ticks"); }
        @Override protected void writeNbt(NbtCompound nbt) { super.writeNbt(nbt); nbt.putInt("ticks", ticks); }
    }
    private static final class PassiveTile extends BlockEntity {
        PassiveTile(BlockEntityType<PassiveTile> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    }
    private static final class DisabledTile extends BlockEntity {
        DisabledTile(BlockEntityType<DisabledTile> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    }
}
