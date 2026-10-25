package buildcraft.test.lib.world;

import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.*;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.snapshot.*;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.FluidTankStorage;
import buildcraft.lib.inventory.StorageItemTransactor;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class BlueprintBuilderGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1), TARGET = new BlockPos(4, 2, 4);
    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER);
    private static final List<FluidStackBC> ONE_BUCKET = List.of(FluidStackBC.of(Fluids.WATER, FluidConstants.BUCKET));

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shortageAcrossDuplicateItemsAndFluidsRollsBack(TestContext context) {
        var items = new ItemHandlerSimple(3);
        items.setStackInSlot(0, new ItemStack(Items.STONE, 3));
        var tank = tank(FluidConstants.BUCKET);
        var storage = items.asStorage(true, true);
        var duplicate = List.of(new ItemStack(Items.STONE, 2), new ItemStack(Items.STONE, 2));
        AtomicInteger placed = new AtomicInteger();
        context.assertTrue(!BlueprintMaterials.available(storage, tank, duplicate, ONE_BUCKET), "Duplicate items were counted twice");
        context.assertTrue(!BlueprintMaterials.place(storage, tank, duplicate, ONE_BUCKET, () -> { placed.incrementAndGet(); return true; }), "Short item reservation succeeded");
        var duplicateFluids = List.of(ONE_BUCKET.get(0), ONE_BUCKET.get(0));
        context.assertTrue(!BlueprintMaterials.place(storage, tank, List.of(new ItemStack(Items.STONE, 3)), duplicateFluids,
            () -> { placed.incrementAndGet(); return true; }), "Short fluid reservation succeeded");
        context.assertTrue(items.getStackInSlot(0).getCount() == 3 && tank.getAmount() == FluidConstants.BUCKET && placed.get() == 0,
            "Failed reservation consumed resources or ran placement");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void simulationFailureAndExceptionsReturnExactResources(TestContext context) {
        var items = new ItemHandlerSimple(2);
        ItemStack named = new ItemStack(Items.DIAMOND, 7);
        named.getOrCreateNbt().putString("fixture", "exact");
        items.setStackInSlot(0, named.copy());
        var storage = items.asStorage(true, true);
        var tank = tank(FluidConstants.BUCKET);
        var required = List.of(named.copy());
        context.assertTrue(BlueprintMaterials.available(storage, tank, required, ONE_BUCKET), "Available exact-NBT item rejected");
        context.assertTrue(!BlueprintMaterials.available(storage, tank, List.of(new ItemStack(Items.DIAMOND)), ONE_BUCKET), "NBT mismatch accepted");
        context.assertTrue(!BlueprintMaterials.place(storage, tank, required, ONE_BUCKET, () -> false), "Rejected placement succeeded");
        try {
            BlueprintMaterials.place(storage, tank, required, ONE_BUCKET, () -> { throw new IllegalStateException("fixture"); });
            throw new AssertionError("Expected callback exception");
        } catch (IllegalStateException expected) { }
        context.assertTrue(ItemStack.areEqual(named, items.getStackInSlot(0)) && tank.getAmount() == FluidConstants.BUCKET,
            "Simulation/failure/exception changed material amounts or NBT");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void splitSlotsAndTanksPayExactlyOnce(TestContext context) {
        var items = new ItemHandlerSimple(2);
        items.setStackInSlot(0, new ItemStack(Items.STONE, 2)); items.setStackInSlot(1, new ItemStack(Items.STONE, 3));
        var first = tank(FluidConstants.BUCKET / 2);
        var second = tank(FluidConstants.BUCKET / 2 + 1);
        var tanks = new CombinedStorage<FluidVariant, FluidTankStorage>(List.of(first, second));
        var required = List.of(new ItemStack(Items.STONE, 5));
        AtomicInteger placed = new AtomicInteger();
        context.assertTrue(BlueprintMaterials.place(items.asStorage(true, true), tanks, required, ONE_BUCKET,
            () -> { placed.incrementAndGet(); return true; }), "Split resources were not combined");
        context.assertTrue(placed.get() == 1 && items.getStackInSlot(0).isEmpty() && items.getStackInSlot(1).isEmpty()
            && first.getAmount() + second.getAmount() == 1, "Resources were not spent exactly");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waterloggedPlacementWaitsForBothResources(TestContext context) {
        context.setBlockState(SOURCE, Blocks.OAK_STAIRS.getDefaultState().with(Properties.WATERLOGGED, true));
        Harness host = capture(context, 1);
        context.assertTrue(host.builder.getRemainingMaterials(24).size() == 1
            && host.builder.getRemainingMaterials(24).get(0).stack().isOf(Items.OAK_STAIRS), "Initial material display is missing");
        host.items.setStackInSlot(0, new ItemStack(Items.OAK_STAIRS));
        host.battery.addPower(1000 * MjAPI.MJ, false);
        host.run(10);
        context.assertTrue(context.getBlockState(TARGET).isAir() && host.items.getStackInSlot(0).getCount() == 1
            && host.battery.getStored() == 1000 * MjAPI.MJ, "Missing water still spent item/MJ");
        fill(host.tank, FluidConstants.BUCKET);
        host.run(10);
        context.assertTrue(host.builder.isBuildFinished() && context.getBlockState(TARGET).get(Properties.WATERLOGGED)
            && host.items.getStackInSlot(0).isEmpty() && host.tank.getAmount() == 0, "Waterlogged blueprint was not paid and built");
        long energy = host.battery.getStored();
        context.assertTrue(host.builder.getRemainingMaterials(24).isEmpty(), "Finished blueprint still displays spent materials");
        host.run(10);
        context.assertTrue(host.battery.getStored() == energy, "Finished blueprint spent twice");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blueprintChestContentsArePaidBeforeRestoration(TestContext context) {
        context.setBlockState(SOURCE, Blocks.CHEST);
        ItemStack treasure = new ItemStack(Items.EMERALD, 4);
        treasure.getOrCreateNbt().putString("source", "blueprint");
        ((ChestBlockEntity) context.getBlockEntity(SOURCE)).setStack(0, treasure.copy());
        Harness host = capture(context, 1);
        host.items.setStackInSlot(0, new ItemStack(Items.CHEST));
        host.battery.addPower(1000 * MjAPI.MJ, false);
        host.run(10);
        context.assertTrue(context.getBlockState(TARGET).isAir(), "Chest appeared without paying its contents");
        host.items.setStackInSlot(1, treasure.copy());
        host.run(10);
        context.assertTrue(host.builder.isBuildFinished() && host.items.getStackInSlot(0).isEmpty() && host.items.getStackInSlot(1).isEmpty()
            && ItemStack.areEqual(treasure, ((ChestBlockEntity) context.getBlockEntity(TARGET)).getStack(0)), "Contents were lost/duplicated during paid placement");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dependenciesAndSavedCreditResume(TestContext context) {
        context.setBlockState(SOURCE, Blocks.STONE);
        context.setBlockState(SOURCE.up(), Blocks.TORCH);
        Harness host = capture(context, 2);
        host.items.setStackInSlot(0, new ItemStack(Items.STONE));
        host.items.setStackInSlot(1, new ItemStack(Items.TORCH));
        host.battery.addPower(MjAPI.MJ, false);
        host.run(1);
        NbtCompound saved = host.builder.serializeNBT();
        context.assertTrue(host.items.getStackInSlot(0).getCount() == 1 && saved.getList("work", 10).size() > 0,
            "Queued work consumed materials or was not saved");
        host.builder = new BlueprintBuilder(host);
        host.builder.deserializeNBT(saved);
        host.builder.deserializeNBT(saved);
        host.battery.addPower(1000 * MjAPI.MJ, false);
        host.run(20);
        context.assertTrue(host.builder.isBuildFinished() && context.getBlockState(TARGET).isOf(Blocks.STONE)
            && context.getBlockState(TARGET.up()).isOf(Blocks.TORCH) && host.items.getStackInSlot(1).isEmpty(), "Dependency/credit did not resume correctly");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void existingBlocksRemoveRequirementsAndMissingBlocksRestoreThem(TestContext context) {
        context.setBlockState(SOURCE, Blocks.STONE);
        Harness host = capture(context, 1);
        context.setBlockState(TARGET, Blocks.STONE);
        host.run(2);
        context.assertTrue(host.builder.isBuildFinished() && host.builder.getRemainingMaterials(24).isEmpty(), "Existing blocks still require materials");
        context.setBlockState(TARGET, Blocks.AIR);
        host.run(2);
        context.assertTrue(!host.builder.isBuildFinished() && host.builder.getRemainingMaterials(24).size() == 1
            && host.builder.getRemainingMaterials(24).get(0).count() == 1, "Removed block did not restore exactly one requirement");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void invalidBlockEntityPlacementRefundsMaterialsAndEnergy(TestContext context) throws Exception {
        context.setBlockState(SOURCE, Blocks.CHEST);
        Harness host = capture(context, 1);
        var schematic = host.info.rotatedPalette.get(0);
        NbtCompound invalid = schematic.serializeNBT();
        invalid.getCompound("tileNbt").putString("id", "minecraft:sign");
        schematic.deserializeNBT(invalid);
        host.items.setStackInSlot(0, new ItemStack(Items.CHEST));
        host.battery.addPower(1000 * MjAPI.MJ, false);
        host.run(3);
        context.assertTrue(!host.builder.isBuildFinished() && context.getBlockState(TARGET).isAir()
            && host.items.getStackInSlot(0).getCount() == 1 && host.battery.getStored() == 1000 * MjAPI.MJ,
            "Invalid schematic consumed resources or left a partial block");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void templateSkipsNonBlockBlueprintMaterials(TestContext context) {
        context.setBlockState(SOURCE, Blocks.STONE);
        Harness host = capture(context, 1);
        Template template = new Template();
        template.size = new BlockPos(1, 1, 1); template.offset = BlockPos.ORIGIN; template.facing = Direction.NORTH;
        template.data = new java.util.BitSet(1); template.data.set(0); template.computeKey();
        host.templateInfo = template.new BuildingInfo(context.getAbsolutePos(TARGET), BlockRotation.NONE);
        buildcraft.api.template.TemplateApi.templateRegistry = TemplateRegistry.INSTANCE;
        TemplateRegistry.INSTANCE.addHandler(TemplateHandlerDefault.INSTANCE);
        var builder = new TemplateBuilder(host);
        builder.updateSnapshot();
        host.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 4));
        host.items.setStackInSlot(1, new ItemStack(Items.STONE));
        host.battery.addPower(1000 * MjAPI.MJ, false);
        for (int i = 0; i < 4; i++) builder.tick();
        context.assertTrue(builder.isBuildFinished() && context.getBlockState(TARGET).isOf(Blocks.STONE)
            && host.items.getStackInSlot(0).getCount() == 4 && host.items.getStackInSlot(1).isEmpty(), "Template stalled on non-block materials");
        context.complete();
    }
    private static FluidTankStorage tank(long amount) {
        var tank = new FluidTankStorage(4 * FluidConstants.BUCKET, () -> {});
        fill(tank, amount); return tank;
    }
    private static void fill(FluidTankStorage tank, long amount) {
        try (Transaction transaction = Transaction.openOuter()) { tank.insert(WATER, amount, transaction); transaction.commit(); }
    }
    private static Harness capture(TestContext context, int height) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, height, 1); blueprint.offset = BlockPos.ORIGIN; blueprint.facing = Direction.NORTH;
        blueprint.data = new int[height];
        for (int y = 0; y < height; y++) {
            BlockPos source = context.getAbsolutePos(SOURCE.up(y));
            BlockState state = context.getWorld().getBlockState(source);
            var schematic = new SchematicBlockDefault();
            schematic.init(new SchematicBlockContext(context.getWorld(), source, source, state, state.getBlock()));
            blueprint.palette.add(schematic); blueprint.data[y] = y;
        }
        blueprint.computeKey();
        return new Harness(context, blueprint);
    }
    private static final class Harness implements ITileForBlueprintBuilder, ITileForTemplateBuilder {
        final TestContext context;
        final Blueprint.BuildingInfo info;
        Template.BuildingInfo templateInfo;
        final ItemHandlerSimple items = new ItemHandlerSimple(27);
        final Storage<ItemVariant> storage = items.asStorage(true, true);
        final FluidTankStorage tank = tank(0);
        final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
        BlueprintBuilder builder = new BlueprintBuilder(this);
        Harness(TestContext context, Blueprint blueprint) {
            this.context = context;
            info = blueprint.new BuildingInfo(context.getAbsolutePos(TARGET), BlockRotation.NONE);
            builder.updateSnapshot();
        }
        void run(int ticks) { for (int i = 0; i < ticks; i++) builder.tick(); }
        public World getWorldBC() { return context.getWorld(); }
        public BlockPos getBuilderPos() { return context.getAbsolutePos(BlockPos.ORIGIN.up()); }
        public GameProfile getOwner() { return new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000075"), "BlueprintFixture"); }
        public MjBattery getBattery() { return battery; }
        public boolean canExcavate() { return true; }
        public SnapshotBuilder<?> getBuilder() { return builder; }
        public Blueprint.BuildingInfo getBlueprintBuildingInfo() { return info; }
        public Template.BuildingInfo getTemplateBuildingInfo() { return templateInfo; }
        public IItemTransactor getInvResources() { return new StorageItemTransactor(storage); }
        public Storage<ItemVariant> getResourceStorage() { return storage; }
        public Storage<FluidVariant> getTankManager() { return tank; }
    }
}
