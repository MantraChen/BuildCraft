package buildcraft.test.full;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.factory.BCFactoryBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Exercises native neighbor notifications against the actual packaged blocks. */
public final class ConnectedBlockGameTests implements FabricGameTest {
    static {
        // A sided Fabric provider with no block entity catches legacy capability-only lookups.
        ItemStorage.SIDED.registerForBlocks((world, pos, state, entity, side) ->
            side == Direction.WEST ? Storage.empty() : null, Blocks.DIAMOND_BLOCK);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void framesUpdateConnectionsAndCollisionWhenNeighborsChange(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(3, 3, 3));
        var frame = BCBuildersBlocks.frame;
        world.setBlockState(pos, frame.getDefaultState());
        var isolated = world.getBlockState(pos).getCollisionShape(world, pos).getBoundingBox();
        context.assertTrue(isolated.minX == 0.25 && isolated.maxX == 0.75
            && isolated.minY == 0.25 && isolated.maxY == 0.75
            && isolated.minZ == 0.25 && isolated.maxZ == 0.75, "Isolated frame has a full-block collision box");
        context.assertTrue(!world.getBlockState(pos).isOpaque(), "Frame occludes its hollow model");
        for (Direction side : Direction.values()) {
            var neighbor = pos.offset(side);
            world.setBlockState(neighbor, frame.getDefaultState());
            context.assertTrue(connected(world.getBlockState(pos), side), "Frame did not join " + side);
            context.assertTrue(connected(world.getBlockState(neighbor), side.getOpposite()), "New frame did not join existing frame");
            var bounds = world.getBlockState(pos).getCollisionShape(world, pos).getBoundingBox();
            context.assertTrue(side.getDirection() == Direction.AxisDirection.POSITIVE
                ? bounds.getMax(side.getAxis()) == 1 : bounds.getMin(side.getAxis()) == 0, "Connected arm has no collision");
            world.setBlockState(neighbor, Blocks.STONE.getDefaultState());
            context.assertTrue(!connected(world.getBlockState(pos), side), "Frame connected to a non-frame solid block");
            world.removeBlock(neighbor, false);
        }
        world.setBlockState(pos.east(), BCBuildersBlocks.quarry.getDefaultState());
        context.assertTrue(connected(world.getBlockState(pos), Direction.EAST), "Frame did not join a quarry");
        world.removeBlock(pos.east(), false);
        context.assertTrue(!connected(world.getBlockState(pos), Direction.EAST), "Frame kept a removed quarry arm");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void quarryUsesSidedNativeStorageAndRotatedConnectionFaces(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(3, 3, 3));
        var quarry = BCBuildersBlocks.quarry;
        for (Direction facing : Direction.Type.HORIZONTAL) {
            world.setBlockState(pos.east(), Blocks.DIAMOND_BLOCK.getDefaultState());
            world.setBlockState(pos.west(), Blocks.DIAMOND_BLOCK.getDefaultState());
            world.setBlockState(pos, quarry.getDefaultState().with(quarry.getFacingProperty(), facing));
            var tile = world.getBlockEntity(pos);
            Direction expected = switch (facing) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                default -> throw new AssertionError(facing);
            };
            for (Direction side : Direction.values()) context.assertTrue(
                connected(world.getBlockState(pos), side) == (side == expected), "Wrong quarry connector for " + facing + "/" + side);
            world.removeBlock(pos.east(), false);
            for (Direction side : Direction.values()) context.assertTrue(!connected(world.getBlockState(pos), side), "Quarry kept a removed storage connector");
            world.setBlockState(pos.east(), Blocks.BARREL.getDefaultState());
            context.assertTrue(connected(world.getBlockState(pos), expected), "Quarry failed to detect a newly placed inventory");
            context.assertTrue(world.getBlockEntity(pos) == tile, "Connection update replaced the quarry block entity");
            world.removeBlock(pos, false);
            world.removeBlock(pos.east(), false);
            world.removeBlock(pos.west(), false);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chuteExcludesItsMouthAndRefreshesConnectionsAfterRotation(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(3, 3, 3));
        var chute = BCFactoryBlocks.chute;
        for (Direction side : Direction.values()) world.setBlockState(pos.offset(side), Blocks.BARREL.getDefaultState());
        for (Direction facing : Direction.values()) {
            world.setBlockState(pos, chute.getDefaultState().with(chute.getFacingProperty(), facing));
            assertChuteConnections(context, world.getBlockState(pos), facing);
            var tile = world.getBlockEntity(pos);
            chute.attemptRotation(world, pos, world.getBlockState(pos), Direction.UP);
            var rotated = world.getBlockState(pos);
            var mouth = rotated.get(chute.getFacingProperty());
            context.assertTrue(mouth != facing, "Wrench did not rotate chute");
            assertChuteConnections(context, rotated, mouth);
            Direction removed = mouth.getOpposite();
            world.removeBlock(pos.offset(removed), false);
            context.assertTrue(!connected(world.getBlockState(pos), removed), "Chute kept a removed inventory connector");
            world.setBlockState(pos.offset(removed), Blocks.BARREL.getDefaultState());
            context.assertTrue(connected(world.getBlockState(pos), removed), "Chute did not reconnect to new inventory");
            context.assertTrue(world.getBlockEntity(pos) == tile, "Rotation replaced the chute inventory");
            world.removeBlock(pos, false);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void structureRotationAndMirroringKeepWorldRelativeArms(TestContext context) {
        for (var block : new net.minecraft.block.Block[] {BCBuildersBlocks.frame, BCFactoryBlocks.chute}) {
            BlockState state = block.getDefaultState();
            for (Direction side : Direction.values()) state = state.with(BuildCraftProperties.CONNECTED_MAP.get(side), side == Direction.EAST);
            var rotated = state.rotate(BlockRotation.CLOCKWISE_90);
            var mirrored = state.mirror(BlockMirror.FRONT_BACK);
            for (Direction side : Direction.values()) {
                context.assertTrue(connected(rotated, side) == (side == Direction.SOUTH), "Rotation lost an arm: " + block);
                context.assertTrue(connected(mirrored, side) == (side == Direction.WEST), "Mirror lost an arm: " + block);
            }
        }
        context.complete();
    }

    private static boolean connected(BlockState state, Direction side) { return state.get(BuildCraftProperties.CONNECTED_MAP.get(side)); }
    private static void assertChuteConnections(TestContext context, BlockState state, Direction mouth) {
        for (Direction side : Direction.values()) context.assertTrue(connected(state, side) == (side != mouth), "Wrong chute connector on " + side + " with mouth " + mouth);
    }
}
