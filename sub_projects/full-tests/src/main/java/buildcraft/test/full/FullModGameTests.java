package buildcraft.test.full;

import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Loads the complete packaged mod; no production classes or entrypoints are replaced. */
public class FullModGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void decoratedBlocksKeepTheirVariantWhenPickedAndPlaced(TestContext context) {
        var block = buildcraft.core.BCCoreBlocks.decorated;
        var property = buildcraft.core.block.BlockDecoration.DECORATED_TYPE;
        var player = context.createMockCreativeServerPlayerInWorld();
        var local = new BlockPos(2, 2, 2);
        var pos = context.getAbsolutePos(local);
        for (var type : buildcraft.api.enums.EnumDecoratedBlock.VALUES) {
            var state = block.getDefaultState().with(property, type);
            context.setBlockState(local, state);
            var picked = block.getPickStack(context.getWorld(), pos, state);
            var restored = net.minecraft.item.ItemStack.fromNbt(picked.writeNbt(new net.minecraft.nbt.NbtCompound()));
            var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos),
                net.minecraft.util.math.Direction.UP, pos, false);
            var placement = new net.minecraft.item.ItemPlacementContext(player, net.minecraft.util.Hand.MAIN_HAND, restored, hit);
            var placed = block.getPlacementState(placement);
            context.assertTrue(placed.get(property) == type, "Pick/save/place lost decoration " + type);
            context.assertTrue(placed.getLuminance() == type.lightValue, "Decoration lost its light value");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void markersAttachToEachFaceAndDropWhenSupportDisappears(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        BlockPos local = new BlockPos(3, 3, 3);
        BlockPos pos = context.getAbsolutePos(local);
        for (String name : new String[] {"buildcraftcore:marker_volume", "buildcraftcore:marker_path"}) {
            var block = (buildcraft.lib.block.BlockMarkerBase) Registries.BLOCK.get(new Identifier(name));
            for (var face : net.minecraft.util.math.Direction.values()) {
                BlockPos support = pos.offset(face.getOpposite());
                context.getWorld().setBlockState(support, Blocks.STONE.getDefaultState());
                var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(support), face, support, false);
                var placement = new net.minecraft.item.ItemPlacementContext(player, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.item.ItemStack(block), hit);
                var state = block.getPlacementState(placement);
                context.assertTrue(state != null && state.get(buildcraft.lib.block.BlockBCBase_Neptune.BLOCK_FACING_6) == face,
                    "Marker does not face away from its support: " + face);
                context.getWorld().setBlockState(pos, state);
                var box = state.getOutlineShape(context.getWorld(), pos).getBoundingBox();
                context.assertTrue(box.maxX - box.minX < 1 && box.maxY - box.minY < 1 && box.maxZ - box.minZ < 1,
                    "Marker still has a full-block selection box");
                context.getWorld().setBlockState(support, Blocks.AIR.getDefaultState());
                context.assertTrue(context.getWorld().getBlockState(pos).isAir(), "Unsupported marker remained in world");
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void animatedEnginesDoNotBlockTheirOwnLighting(TestContext context) {
        for (String name : new String[] {"buildcraftcore:engine", "buildcraftenergy:mj_dynamo"}) {
            var block = Registries.BLOCK.get(new Identifier(name));
            context.assertTrue(block != Blocks.AIR, "Missing animated block " + name);
            for (var state : block.getStateManager().getStates()) {
                context.assertTrue(!state.isOpaque(), "Animated engine occludes its own renderer: " + state);
                context.assertTrue(state.getOpacity(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1))) == 0,
                    "Animated engine blocks skylight: " + state);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void registeredBlockEntitiesConstructAndSerialize(TestContext context) {
        var failures = new ArrayList<String>();
        int[] count = {0};
        for (var block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            if (!id.getNamespace().startsWith("buildcraft") || !(block instanceof BlockEntityProvider provider)) continue;
            // Every state matters for engines and multiblocks that select a different tile type.
            for (var state : block.getStateManager().getStates()) {
                try {
                    var tile = provider.createBlockEntity(context.getAbsolutePos(new BlockPos(1, 2, 1)), state);
                    // Water springs use scheduled block ticks and intentionally have no entity.
                    if (tile == null && block instanceof buildcraft.core.block.BlockSpring
                        && state.get(buildcraft.core.block.BlockSpring.SPRING_TYPE) == buildcraft.api.enums.EnumSpring.WATER) continue;
                    if (tile == null || !tile.getType().supports(state)) throw new IllegalStateException("Missing or incompatible entity type");
                    tile.setWorld(context.getWorld());
                    var saved = tile.createNbt();
                    tile.readNbt(saved);
                    count[0]++;
                } catch (Exception | LinkageError failure) {
                    failures.add(state + ": " + failure);
                }
            }
        }
        System.out.println("BUILDCRAFT COMPLETE MOD: checked " + count[0] + " block entity states; failures=" + failures.size());
        failures.forEach(System.err::println);
        context.assertTrue(count[0] > 20, "Expected complete BuildCraft block registry");
        context.assertTrue(failures.isEmpty(), failures.isEmpty() ? "" : failures.get(0));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dynamoNativeOutputIsDirectionalAndTransactional(TestContext context) {
        var block = Registries.BLOCK.get(new Identifier("buildcraftenergy", "mj_dynamo"));
        context.assertTrue(block != Blocks.AIR, "The smoke test enables the optional dynamo");
        BlockPos pos = new BlockPos(4, 2, 2);
        context.setBlockState(pos, block.getDefaultState().with(buildcraft.lib.block.BlockBCBase_Neptune.BLOCK_FACING_6,
            net.minecraft.util.math.Direction.EAST));
        var tile = (buildcraft.energy.tile.TileDynamoMJ) context.getBlockEntity(pos);
        long ratio = buildcraft.lib.BCLibConfig.mjRfConversion.mjPerRf;
        tile.battery.addPower(ratio * 3 + 7, false);
        var output = team.reborn.energy.api.EnergyStorage.SIDED.find(context.getWorld(),
            context.getAbsolutePos(pos), net.minecraft.util.math.Direction.EAST);
        context.assertTrue(output != null, "Dynamo output lookup missing");
        context.assertTrue(team.reborn.energy.api.EnergyStorage.SIDED.find(context.getWorld(),
            context.getAbsolutePos(pos), net.minecraft.util.math.Direction.WEST) == null, "Energy exposed on input face");
        try (var simulated = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            context.assertTrue(output.extract(10, simulated) == 3, "Wrong simulated energy conversion");
        }
        context.assertTrue(tile.battery.getStored() == ratio * 3 + 7, "Simulation consumed MJ");
        try (var committed = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            context.assertTrue(output.extract(10, committed) == 3, "Wrong committed conversion"); committed.commit();
        }
        context.assertTrue(tile.battery.getStored() == 7, "Fractional MJ lost or duplicated");
        context.waitAndRun(5, () -> {
            context.assertTrue(tile.battery.getStored() == 7, "Idle ticks lost fractional MJ");
            tile.readNbt(tile.createNbt());
            context.assertTrue(tile.battery.getStored() == 7, "Saved dynamo lost fractional MJ");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void factoryTankSurvivesPlacementAndWorldTick(TestContext context) {
        var block = Registries.BLOCK.get(new Identifier("buildcraftfactory", "tank"));
        context.assertTrue(block != Blocks.AIR, "Factory module is not registered");
        BlockPos pos = new BlockPos(2, 2, 2);
        context.setBlockState(pos, block);
        context.waitAndRun(5, () -> {
            context.assertTrue(context.getBlockEntity(pos) != null, "Placed tank has no block entity");
            context.getBlockEntity(pos).createNbt();
            context.complete();
        });
    }
}
