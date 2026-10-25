package buildcraft.test.lib.world;

import buildcraft.builders.snapshot.BuildingExecutor;
import buildcraft.builders.snapshot.TemplateHandlerDefault;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.lib.misc.FakePlayerProvider;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BuilderGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 2, 3);
    private static final GameProfile OWNER = new GameProfile(UUID.fromString("b15a4551-04c5-4bbe-9205-fac846ad3401"), "BuilderTest");
    private static final GameProfile DENIED = new GameProfile(UUID.fromString("b15a4551-04c5-4bbe-9205-fac846ad3402"), "DeniedBuilder");
    static {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !player.getUuid().equals(DENIED.getId()));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placementNeedsEnergyAndConsumesOneItem(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.tick();
        context.assertTrue(builder.items == 64 && builder.reservations == 0 && !builder.isBuildFinished(),
            "Unpowered builders must leave materials in their inventory");
        builder.battery.addPower(100 * MjAPI.MJ, false);
        builder.tick();
        context.expectBlock(Blocks.STONE, POS);
        context.assertTrue(builder.items == 63 && builder.isBuildFinished(), "Place exactly one real block");
        for (int i = 0; i < 10; i++) builder.tick();
        context.assertTrue(builder.items == 63 && builder.reservations == 1, "Completed work must not consume twice");
        context.setBlockState(POS, Blocks.AIR);
        builder.tick();
        context.assertTrue(builder.items == 62 && builder.isBuildFinished(), "A changed target must be surveyed again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void queueSurveyDoesNotMissLateObstructions(TestContext context) {
        List<BlockPos> positions = new ArrayList<>();
        for (int y = 2; y < 6; y++) for (int z = 1; z < 4; z++) for (int x = 1; x < 4; x++)
            positions.add(new BlockPos(x, y, z));
        BlockPos obstruction = positions.get(35);
        context.setBlockState(obstruction, Blocks.DIRT);
        Harness builder = new Harness(context, positions);
        builder.battery.addPower(10_000 * MjAPI.MJ, false);
        builder.tick();
        context.assertTrue(builder.reservations == 0, "Survey must finish before placing blocks");
        for (int i = 0; i < 150 && !builder.isBuildFinished(); i++) builder.tick();
        context.assertTrue(builder.isBuildFinished() && builder.items == 28 && builder.drops.size() == 1,
            "A full placement queue must allow later break tasks through");
        context.assertTrue(builder.drops.get(0).isOf(Items.DIRT), "Excavated drops must not disappear");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cancellingRefundsPartialPowerOnce(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.battery.addPower(MjAPI.MJ, false);
        builder.tick();
        context.assertTrue(builder.battery.getStored() == 0 && builder.items == 64, "Partial work must reserve only MJ");
        builder.cancelAllTasks();
        builder.cancelAllTasks();
        context.assertTrue(builder.battery.getStored() == MjAPI.MJ && builder.items == 64, "Cancellation refund duplicated or lost");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reloadRetainsCreditWithoutEarlyMaterialReservation(TestContext context) {
        Harness first = new Harness(context, List.of(POS));
        first.battery.addPower(MjAPI.MJ, false);
        first.tick();
        NbtCompound saved = first.serializeNBT();
        Harness resumed = new Harness(context, List.of(POS));
        resumed.deserializeNBT(saved);
        resumed.deserializeNBT(saved);
        context.assertTrue(resumed.items == 64 && resumed.battery.getStored() == 0, "Reading state must not create resources");
        resumed.cancelAllTasks();
        context.assertTrue(resumed.battery.getStored() == MjAPI.MJ, "Saved MJ credit must survive one refund");
        resumed.tick();
        resumed.battery.addPower(100 * MjAPI.MJ, false);
        resumed.tick();
        context.assertTrue(resumed.items == 63 && resumed.isBuildFinished(), "Resumed build failed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void failedPlacementRefundsRemainingItemsAndPower(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.failPlacement = true;
        builder.battery.addPower(100 * MjAPI.MJ, false);
        for (int i = 0; i < 4; i++) builder.tick();
        context.assertTrue(builder.items == 64 && builder.battery.getStored() == 100 * MjAPI.MJ
            && !builder.isBuildFinished(), "Failed placements must return both the item and MJ");
        context.expectBlock(Blocks.AIR, POS);
        builder.failPlacement = false;
        builder.tick();
        context.assertTrue(builder.items == 63 && builder.isBuildFinished(), "Placement must retry after a failure");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void missingMaterialsAndDependencyWaitWithoutSpending(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.items = 0;
        builder.battery.addPower(100 * MjAPI.MJ, false);
        builder.tick();
        builder.items = 64;
        builder.ready = false;
        builder.tick();
        context.assertTrue(builder.reservations == 0 && builder.battery.getStored() == 100 * MjAPI.MJ,
            "Waiting for materials or a supporting block must not spend energy");
        builder.ready = true;
        builder.tick();
        context.assertTrue(builder.isBuildFinished() && builder.items == 63, "New resources must be noticed without a cache reset");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bedrockAndProtectedBlocksRemainIncomplete(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.battery.addPower(100 * MjAPI.MJ, false);
        context.setBlockState(POS, Blocks.BEDROCK);
        builder.tick();
        context.assertTrue(!builder.isBuildFinished() && builder.battery.getStored() == 100 * MjAPI.MJ,
            "An unbreakable obstruction must remain incomplete");
        context.setBlockState(POS, Blocks.DIRT);
        builder.owner = DENIED;
        builder.tick();
        context.expectBlock(Blocks.DIRT, POS);
        context.assertTrue(builder.items == 64 && builder.battery.getStored() == 100 * MjAPI.MJ && builder.drops.isEmpty(),
            "Protection cancellation must preserve blocks, MJ and materials");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyTemplateAndDisabledExcavationAreHandled(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        context.setBlockState(POS, Blocks.DIRT);
        builder.excavate = false;
        builder.battery.addPower(100 * MjAPI.MJ, false);
        builder.tick();
        context.assertTrue(!builder.isBuildFinished(), "Wrong occupied target must not be called finished");
        builder.air = true;
        builder.tick();
        context.assertTrue(builder.isBuildFinished(), "Clearance outside a non-excavating template is ignored");
        builder.excavate = true;
        builder.tick();
        context.expectBlock(Blocks.AIR, POS);
        context.assertTrue(builder.items == 64 && builder.drops.size() == 1 && builder.isBuildFinished(),
            "Air target should excavate without reserving a placement item");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unloadedTargetsAreNotForceLoaded(TestContext context) {
        Harness builder = new Harness(context, List.of(new BlockPos(100_000, 2, 100_000)));
        BlockPos remote = builder.positions.get(0);
        context.assertTrue(!context.getWorld().isChunkLoaded(remote), "Test target unexpectedly loaded");
        builder.battery.addPower(100 * MjAPI.MJ, false);
        for (int i = 0; i < 40; i++) builder.tick();
        context.assertTrue(!context.getWorld().isChunkLoaded(remote) && !builder.isBuildFinished()
            && builder.reservations == 0 && builder.battery.getStored() == 100 * MjAPI.MJ,
            "An unloaded target must wait without loading or spending");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedTasksAreBoundToPlanAndPosition(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.battery.addPower(MjAPI.MJ, false);
        builder.tick();
        NbtCompound saved = builder.serializeNBT();
        NbtList entries = saved.getList("work", 10);
        entries.add(entries.getCompound(0).copy());
        NbtCompound bad = entries.getCompound(0).copy();
        bad.putInt("index", 100);
        bad.putLong("power", Long.MAX_VALUE);
        entries.add(bad);
        Harness resumed = new Harness(context, List.of(POS));
        resumed.deserializeNBT(saved);
        resumed.cancelAllTasks();
        context.assertTrue(resumed.battery.getStored() == MjAPI.MJ, "Duplicate or out-of-range tasks created MJ");
        Harness relocated = new Harness(context, List.of(POS.up()));
        relocated.deserializeNBT(saved);
        relocated.cancelAllTasks();
        context.assertTrue(relocated.battery.getStored() == 0, "A different plan must reject old work");
        saved.getCompound("plan").putString("different", "plan");
        resumed.battery.extractAll();
        resumed.deserializeNBT(saved);
        resumed.cancelAllTasks();
        context.assertTrue(resumed.battery.getStored() == 0, "Changed identity must reject queued credit");
        context.complete();
    }

    /** Exercise the production executor and default native placement handler in a real ServerWorld. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unreadyTasksDoNotStarveLaterPlacements(TestContext context) {
        List<BlockPos> positions = new ArrayList<>();
        for (int y = 2; y < 4; y++) for (int z = 1; z < 4; z++) for (int x = 1; x < 4; x++)
            positions.add(new BlockPos(x, y, z));
        Harness builder = new Harness(context, positions);
        builder.unready.addAll(builder.positions.subList(0, 16));
        builder.battery.addPower(10_000 * MjAPI.MJ, false);
        for (int i = 0; i < 10; i++) builder.tick();
        context.assertTrue(builder.items == 62 && !builder.isBuildFinished(), "Blocked early tasks starved later ready blocks");
        builder.unready.clear();
        for (int i = 0; i < 30 && !builder.isBuildFinished(); i++) builder.tick();
        context.assertTrue(builder.items == 46 && builder.isBuildFinished(), "Tasks must resume after their dependency becomes ready");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void externalCompletionRefundsQueuedCredit(TestContext context) {
        Harness builder = new Harness(context, List.of(POS));
        builder.battery.addPower(MjAPI.MJ, false);
        builder.tick();
        context.setBlockState(POS, Blocks.STONE);
        builder.tick();
        builder.tick();
        context.assertTrue(builder.isBuildFinished() && builder.battery.getStored() == MjAPI.MJ
            && builder.reservations == 0, "Externally completed work must return credit exactly once");
        context.complete();
    }

    private static class Harness extends BuildingExecutor {
        final TestContext context;
        final List<BlockPos> positions;
        final MjBattery battery = new MjBattery(16_000 * MjAPI.MJ);
        final List<ItemStack> drops = new ArrayList<>();
        final List<BlockPos> unready = new ArrayList<>();
        int items = 64, reservations;
        boolean failPlacement, air, excavate = true, ready = true;
        GameProfile owner = OWNER;
        Harness(TestContext context, List<BlockPos> positions) {
            this.context = context;
            this.positions = positions.stream().map(context::getAbsolutePos).toList();
            updateSnapshot();
        }
        @Override protected World workWorld() { return context.getWorld(); }
        @Override protected MjBattery workBattery() { return battery; }
        @Override protected GameProfile workOwner() { return owner; }
        @Override protected BlockPos workOrigin() { return context.getAbsolutePos(new BlockPos(0, 2, 0)); }
        @Override protected boolean excavates() { return excavate; }
        @Override protected int workSize() { return positions.size(); }
        @Override protected BlockPos workPosition(int index) { return positions.get(index); }
        @Override protected NbtCompound workIdentity() {
            NbtCompound tag = new NbtCompound();
            tag.putLongArray("positions", positions.stream().mapToLong(BlockPos::asLong).toArray());
            return tag;
        }
        @Override protected boolean isAir(BlockPos pos) { return air; }
        @Override protected boolean isBlockCorrect(BlockPos pos) { return context.getWorld().getBlockState(pos).isOf(Blocks.STONE); }
        @Override protected boolean canPlace(BlockPos pos) { return context.getWorld().isAir(pos); }
        @Override protected boolean isReadyToPlace(BlockPos pos) { return ready && !unready.contains(pos); }
        @Override protected boolean hasEnoughToPlaceItems(BlockPos pos) { return items > 0; }
        @Override protected List<ItemStack> getToPlaceItems(BlockPos pos) {
            reservations++;
            if (items == 0) return null;
            items--;
            return List.of(new ItemStack(Items.STONE));
        }
        @Override protected boolean doPlaceTask(PlaceTask task) {
            if (failPlacement) return false;
            var player = FakePlayerProvider.INSTANCE.getFakePlayer(context.getWorld(), owner, workOrigin());
            ItemStack previous = player.getMainHandStack();
            player.setStackInHand(Hand.MAIN_HAND, task.required.get(0));
            try { return TemplateHandlerDefault.INSTANCE.handle(context.getWorld(), task.pos, player, task.required.get(0)); }
            finally { player.setStackInHand(Hand.MAIN_HAND, previous); }
        }
        @Override protected void cancelPlaceTask(PlaceTask task) { items += task.required.stream().mapToInt(ItemStack::getCount).sum(); }
        @Override protected void acceptDrops(List<ItemStack> drops) { this.drops.addAll(drops); }
        @Override protected void workChanged() { }
    }
}
