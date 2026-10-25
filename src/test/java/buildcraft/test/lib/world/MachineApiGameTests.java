package buildcraft.test.lib.world;

import buildcraft.api.tiles.*;
import buildcraft.lib.registry.BlockEntityRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class MachineApiGameTests implements FabricGameTest {
    private static final Block BLOCK = Registry.register(Registries.BLOCK,
        new Identifier("buildcraft_port_tests", "machine_api"), new MachineBlock());
    private static final BlockEntityType<MachineTile> TYPE = BlockEntityRegistry.register(
        new Identifier("buildcraft_port_tests", "machine_api"), MachineTile.class, MachineTile::new, BLOCK);
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    public static void initialize() {
        // Native registrations can expose services on blocks without entity interfaces.
        TilesAPI.HAS_WORK.registerForBlocks((world, pos, state, tile, side) -> side == Direction.WEST ? () -> true : null,
            Blocks.EMERALD_BLOCK);
    }
    private MachineTile place(TestContext test) {
        test.setBlockState(POS, BLOCK);
        return (MachineTile) test.getBlockEntity(POS);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void workAndControlShareTheActualMachine(TestContext test) {
        MachineTile tile = place(test);
        IHasWork work = TilesAPI.find(TilesAPI.HAS_WORK, tile, Direction.EAST);
        IControllable control = TilesAPI.find(TilesAPI.CONTROLLABLE, tile, Direction.WEST);
        test.assertTrue(work == tile && control == tile && work.hasWork(), "Lookups must return the actual machine");
        control.setControlMode(IControllable.Mode.OFF);
        test.assertTrue(!work.hasWork(), "Work status must reflect current machine state");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void areaAndHeatInterfacesResolveNatively(TestContext test) {
        MachineTile tile = place(test);
        ITileAreaProvider area = TilesAPI.find(TilesAPI.AREA_PROVIDER, tile, null);
        IHeatable heat = TilesAPI.find(TilesAPI.HEATABLE, tile, Direction.UP);
        test.assertTrue(area == tile && area.min().equals(tile.getPos()) && area.isValidFromLocation(tile.getPos().west()),
            "Area provider must retain world position and validation behavior");
        heat.setHeatValue(42);
        test.assertTrue(heat == tile && tile.getCurrentHeatValue() == 42, "Heat interface must act on the tile");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removedAndUnattachedTilesExposeNoService(TestContext test) {
        MachineTile tile = place(test);
        MachineTile unattached = new MachineTile(TYPE, tile.getPos(), tile.getCachedState());
        unattached.setWorld(test.getWorld());
        test.assertTrue(TilesAPI.find(TilesAPI.HAS_WORK, unattached, null) == null,
            "An entity with matching coordinates but no world membership must be rejected");
        tile.markRemoved();
        test.assertTrue(TilesAPI.find(TilesAPI.HAS_WORK, tile, null) == null, "Removed tiles must not expose live services");
        test.assertTrue(TilesAPI.find(TilesAPI.HAS_WORK, null, null) == null, "Missing tiles must be supported");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lookupDoesNotLoadAnUnloadedChunk(TestContext test) {
        BlockPos far = new BlockPos(1500000, 100, 1500000);
        MachineTile tile = new MachineTile(TYPE, far, BLOCK.getDefaultState());
        test.assertTrue(TilesAPI.find(TilesAPI.HAS_WORK, tile, null) == null, "A tile without a world exposes no service");
        tile.setWorld(test.getWorld());
        test.assertTrue(!test.getWorld().isChunkLoaded(far), "Target must begin unloaded");
        test.assertTrue(TilesAPI.find(TilesAPI.HAS_WORK, tile, null) == null, "Unloaded tiles must not be queried");
        test.assertTrue(!test.getWorld().isChunkLoaded(far), "Service lookup must not load chunks");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockProvidersRespectTheRequestedSide(TestContext test) {
        test.setBlockState(POS, Blocks.EMERALD_BLOCK);
        BlockPos absolute = test.getAbsolutePos(POS);
        IHasWork west = TilesAPI.HAS_WORK.find(test.getWorld(), absolute, Direction.WEST);
        test.assertTrue(west != null && west.hasWork(), "Native block providers must be discoverable");
        test.assertTrue(TilesAPI.HAS_WORK.find(test.getWorld(), absolute, Direction.EAST) == null,
            "Unavailable sides must not receive the service");
        test.complete();
    }

    private static final class MachineBlock extends BlockWithEntity {
        MachineBlock() { super(AbstractBlock.Settings.create()); }
        @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return BlockEntityRegistry.create(MachineTile.class, pos, state);
        }
    }
    private static final class MachineTile extends BlockEntity implements IHasWork, IControllable, ITileAreaProvider, IHeatable {
        private Mode mode = Mode.ON;
        private double heat;
        MachineTile(BlockEntityType<MachineTile> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public boolean hasWork() { return mode == Mode.ON; }
        @Override public Mode getControlMode() { return mode; }
        @Override public void setControlMode(Mode mode) { this.mode = mode; }
        @Override public BlockPos min() { return pos; }
        @Override public BlockPos max() { return pos.add(3, 3, 3); }
        @Override public boolean isValidFromLocation(BlockPos from) { return from.equals(pos.west()); }
        @Override public void removeFromWorld() { world.removeBlock(pos, false); }
        @Override public double getMinHeatValue() { return 0; }
        @Override public double getIdealHeatValue() { return 50; }
        @Override public double getMaxHeatValue() { return 100; }
        @Override public double getCurrentHeatValue() { return heat; }
        @Override public double setHeatValue(double value) { return heat = Math.max(0, Math.min(100, value)); }
    }
}
