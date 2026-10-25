/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.api.enums.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.*;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.tile.*;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileTank;
import buildcraft.silicon.BCSiliconBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.explosion.Explosion;

/** Packaged-world removal tests: actual loot, exact recovery, and cached automation references. */
public final class MachineRecoveryGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 3, 3);
    @Override public void invokeTestMethod(TestContext context, java.lang.reflect.Method method) {
        // The exact drop query extends beyond the template. Clear previous saved-world
        // residue before any setup; newly produced drops must still match one-for-one.
        var residue = drops(context, context.getAbsolutePos(POS));
        if (!residue.isEmpty()) System.out.println("MACHINE LOOT FIXTURE " + method.getName() + " clearing previous drops: " + residue.stream().map(ItemEntity::getStack).toList());
        residue.forEach(ItemEntity::discard);
        FabricGameTest.super.invokeTestMethod(context, method);
    }
    private static FluidVariant taggedWater() {
        var tag = new NbtCompound(); tag.putString("batch", "Remake by FelixMantra");
        return FluidVariant.of(Fluids.WATER, tag);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalTankRemovalRecoversExactContentsOnceAndInvalidatesRetainedViews(TestContext c) {
        c.setBlockState(POS, BCFactoryBlocks.tank);
        var tile = (TileTank)c.getBlockEntity(POS); var fluid = taggedWater();
        var storage = FluidStorage.SIDED.find(c.getWorld(), tile.getPos(), Direction.UP);
        fill(c, storage, fluid, 81037); var view = storage.iterator().next();
        var preview = DefaultedList.<ItemStack>of(); tile.addDrops(preview, 0); tile.addDrops(preview, 0);
        c.assertTrue(view.getAmount() == 81037, "Loot previews drained the live tank");
        breakWith(c, player(c, GameMode.SURVIVAL), tile.getPos());
        c.assertTrue(amount(c, tile.getPos(), fluid) == 81037 && count(c, tile.getPos(), BCFactoryBlocks.tank.asItem()) == 1,
            "Tank break lost contents or block");
        c.assertTrue(tile.tank.getStorage().getAmount() == 0 && view.isResourceBlank() && view.getResource().isBlank()
            && view.getAmount() == 0 && view.getCapacity() == 0, "Removed tank kept a live inventory view");
        c.setBlockState(POS, BCFactoryBlocks.tank); var replacement = FluidStorage.SIDED.find(c.getWorld(), tile.getPos(), Direction.UP);
        fill(c, replacement, fluid, 23);
        try (var tx = Transaction.openOuter()) {
            c.assertTrue(storage.insert(fluid, 1, tx) == 0 && storage.extract(fluid, 1, tx) == 0 && view.extract(fluid, 1, tx) == 0,
                "Stale storage altered a removed or replacement tank"); tx.commit();
        }
        tile.onRemove();
        c.assertTrue(amount(c, tile.getPos(), fluid) == 81037 && total(replacement) == 23,
            "Repeated removal duplicated loot or touched the new tank");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void combustionEngineRecoversFuelCoolantAndResidueAcrossNbtReload(TestContext c) {
        c.setBlockState(POS, BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE, EnumEngineType.IRON));
        var tile = (TileEngineIron_BC8)c.getBlockEntity(POS);
        var fuel = FluidVariant.of(BCEnergyFluids.crudeOil[0]); var water = FluidVariant.of(Fluids.WATER);
        var residue = FluidVariant.of(BCEnergyFluids.oilResidue[0]);
        fill(c, tile.tankFuel, fuel, 81037); fill(c, tile.tankCoolant, water, 37); fill(c, tile.tankResidue, residue, 40501);
        var saved = tile.createNbt();
        tile.tankFuel.getStorage().readNbt(new NbtCompound());
        tile.tankCoolant.getStorage().readNbt(new NbtCompound());
        tile.tankResidue.getStorage().readNbt(new NbtCompound());
        tile.readNbt(saved);
        var storage = FluidStorage.SIDED.find(c.getWorld(), tile.getPos(), Direction.NORTH);
        try (var tx = Transaction.openOuter()) {
            c.assertTrue(storage.extract(fuel, 1, tx) == 0 && storage.extract(water, 1, tx) == 0
                && storage.insert(residue, 1, tx) == 0, "Engine sided policy was lost");
        }
        breakWith(c, player(c, GameMode.SURVIVAL), tile.getPos());
        c.assertTrue(amount(c, tile.getPos(), fuel) == 81037 && amount(c, tile.getPos(), water) == 37
            && amount(c, tile.getPos(), residue) == 40501, "Removal omitted a restricted tank or changed NBT/amounts");
        var engine = drops(c, tile.getPos()).stream().map(ItemEntity::getStack).filter(s -> s.isOf(BCCoreBlocks.engine.asItem())).findFirst().orElseThrow();
        c.assertTrue(BCCoreBlocks.engine.getEngineType(engine) == EnumEngineType.IRON, "Combustion engine dropped a different engine type");
        c.assertTrue(tile.tankFuel.getStorage().getAmount() == 0 && tile.tankCoolant.getStorage().getAmount() == 0
            && tile.tankResidue.getStorage().getAmount() == 0, "Restricted tanks retained their dropped contents");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stoneEngineRecoversNamedFuelAndInvalidatesCachedItemViews(TestContext c) {
        c.setBlockState(POS, BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE, EnumEngineType.STONE));
        var tile = (TileEngineStone_BC8)c.getBlockEntity(POS); var fuel = new ItemStack(Items.COAL, 7);
        fuel.setCustomName(Text.literal("Saved fuel")); tile.invFuel.setStackInSlot(0, fuel.copy());
        var storage = ItemStorage.SIDED.find(c.getWorld(), tile.getPos(), Direction.NORTH); var view = storage.iterator().next();
        breakWith(c, player(c, GameMode.SURVIVAL), tile.getPos()); tile.onRemove();
        var found = drops(c, tile.getPos()).stream().map(ItemEntity::getStack).filter(s -> s.isOf(Items.COAL)).toList();
        c.assertTrue(found.size() == 1 && ItemStack.areEqual(found.get(0), fuel) && tile.invFuel.getStackInSlot(0).isEmpty(), "Fuel recovery lost NBT or duplicated contents");
        try (var tx = Transaction.openOuter()) {
            var variant = ItemVariant.of(fuel);
            c.assertTrue(storage.insert(variant, 1, tx) == 0 && storage.extract(variant, 1, tx) == 0
                && view.extract(variant, 1, tx) == 0 && view.getAmount() == 0 && view.getResource().isBlank(), "Cached item storage remained usable"); tx.commit();
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeRemovalPreservesMachineContentsAndTileDropsRuleSuppressesAllLoot(TestContext c) {
        var player = player(c, GameMode.CREATIVE); var fluid = taggedWater(); var world = c.getWorld();
        c.setBlockState(POS, BCFactoryBlocks.tank); var pos = c.getAbsolutePos(POS);
        fill(c, FluidStorage.SIDED.find(world, pos, Direction.UP), fluid, 19); breakWith(c, player, pos);
        c.assertTrue(amount(c, pos, fluid) == 19 && count(c, pos, BCFactoryBlocks.tank.asItem()) == 0,
            "Creative removal lost machine contents or produced a block item");
        drops(c, pos).forEach(ItemEntity::discard);
        var rule = world.getGameRules().get(GameRules.DO_TILE_DROPS); boolean previous = rule.get();
        try {
            rule.set(false, world.getServer()); c.setBlockState(POS, BCFactoryBlocks.tank);
            var tile = (TileTank)c.getBlockEntity(POS); fill(c, tile.tank, fluid, 29);
            player.changeGameMode(GameMode.SURVIVAL); breakWith(c, player, pos); tile.onRemove();
            c.assertTrue(drops(c, pos).isEmpty() && tile.tank.getStorage().getAmount() == 0, "doTileDrops=false leaked machine loot");
        } finally { rule.set(previous, world.getServer()); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void explosionRemovalRecoversFluidOnlyOnce(TestContext c) {
        c.setBlockState(POS, BCFactoryBlocks.tank); var tile = (TileTank)c.getBlockEntity(POS); var fluid = taggedWater();
        fill(c, tile.tank, fluid, 40501); var pos = tile.getPos();
        var explosion = new Explosion(c.getWorld(), null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
            2, false, Explosion.DestructionType.DESTROY);
        explosion.getAffectedBlocks().add(pos); explosion.affectWorld(false); tile.onRemove();
        c.assertTrue(c.getWorld().isAir(pos) && amount(c, pos, fluid) == 40501, "Explosion lost or duplicated recovered fluid");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeBlockLootPreservesAllEngineAndDecorationVariantsAndMachineItems(TestContext c) {
        var pos = c.getAbsolutePos(POS); var player = player(c, GameMode.SURVIVAL);
        for (var type : EnumEngineType.values()) {
            c.setBlockState(POS, BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE, type));
            breakWith(c, player, pos);
            var items = drops(c, pos);
            c.assertTrue(items.size() == 1 && BCCoreBlocks.engine.getEngineType(items.get(0).getStack()) == type, "Engine loot lost variant " + type);
            items.forEach(ItemEntity::discard);
        }
        for (var type : EnumDecoratedBlock.values()) {
            c.setBlockState(POS, BCCoreBlocks.decorated.getDefaultState().with(BuildCraftProperties.DECORATED_BLOCK, type));
            breakWith(c, player, pos); var items = drops(c, pos);
            c.assertTrue(items.size() == 1 && buildcraft.core.item.ItemBlockDecorated.getVariant(items.get(0).getStack()) == type, "Decoration loot lost variant " + type);
            items.forEach(ItemEntity::discard);
        }
        c.setBlockState(POS.down(), Blocks.STONE);
        for (var block : new Block[]{BCCoreBlocks.markerVolume, BCCoreBlocks.markerPath,
            BCFactoryBlocks.autoWorkbenchItems, BCFactoryBlocks.miningWell, BCFactoryBlocks.pump, BCFactoryBlocks.floodGate,
            BCFactoryBlocks.distiller, BCFactoryBlocks.heatExchange, BCFactoryBlocks.tank, BCFactoryBlocks.chute,
            BCBuildersBlocks.filler, BCBuildersBlocks.builder, BCBuildersBlocks.architect, BCBuildersBlocks.library,
            BCBuildersBlocks.replacer, BCBuildersBlocks.quarry, BCSiliconBlocks.laser, BCSiliconBlocks.assemblyTable,
            BCSiliconBlocks.advancedCraftingTable, BCSiliconBlocks.integrationTable}) {
            c.setBlockState(POS, block); breakWith(c, player, pos);
            c.assertTrue(count(c, pos, block.asItem()) == 1, "Native survival loot missing: " + net.minecraft.registry.Registries.BLOCK.getId(block));
            drops(c, pos).forEach(ItemEntity::discard);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stateUpdatesAndTemporaryRemovalDoNotDropOrLoseMachineContents(TestContext c) {
        c.setBlockState(POS, BCFactoryBlocks.tank); var tile = (TileTank)c.getBlockEntity(POS); var fluid = taggedWater();
        var storage = FluidStorage.SIDED.find(c.getWorld(), tile.getPos(), Direction.NORTH);
        fill(c, storage, fluid, 81037); var view = storage.iterator().next();
        tile.markRemoved();
        try (var tx = Transaction.openOuter()) { c.assertTrue(view.extract(fluid, 1, tx) == 0 && storage.insert(fluid, 1, tx) == 0, "Detached tile remained accessible"); }
        tile.cancelRemoval();
        c.setBlockState(POS.up(), BCFactoryBlocks.tank);
        c.assertTrue(c.getBlockEntity(POS) == tile && total(storage) == 81037 && drops(c, tile.getPos()).isEmpty(), "Neighbor/lifecycle state change lost contents or dropped loot");
        try (var tx = Transaction.openOuter()) { c.assertTrue(view.extract(fluid, 1, tx) == 1, "Reattached live storage stopped working"); }
        c.assertTrue(total(storage) == 81037, "Rollback after reattachment lost fluid"); c.complete();
    }

    private static ServerPlayerEntity player(TestContext c, GameMode mode) {
        var player = c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(mode);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        var center = Vec3d.ofCenter(c.getAbsolutePos(POS)); player.refreshPositionAndAngles(center.x, center.y, center.z - 2, 0, 0); return player;
    }
    private static void breakWith(TestContext c, ServerPlayerEntity player, BlockPos pos) {
        c.assertTrue(player.interactionManager.tryBreakBlock(pos) && c.getWorld().isAir(pos), "Native player break failed at " + pos);
    }
    private static void fill(TestContext c, Storage<FluidVariant> storage, FluidVariant fluid, long amount) {
        c.assertTrue(storage != null, "Missing fluid storage");
        try (var tx = Transaction.openOuter()) { c.assertTrue(storage.insert(fluid, amount, tx) == amount, "Fixture insertion refused " + fluid); tx.commit(); }
    }
    private static long total(Storage<FluidVariant> storage) { long total = 0; for (var view : storage) total += view.getAmount(); return total; }
    private static java.util.List<ItemEntity> drops(TestContext c, BlockPos pos) { return c.getWorld().getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1), e -> true); }
    private static int count(TestContext c, BlockPos pos, Item item) { return drops(c, pos).stream().map(ItemEntity::getStack).filter(s -> s.isOf(item)).mapToInt(ItemStack::getCount).sum(); }
    private static long amount(TestContext c, BlockPos pos, FluidVariant fluid) {
        return drops(c, pos).stream().map(e -> ItemFragileFluidContainer.getFluid(e.getStack()))
            .filter(f -> f.getFluidVariant().equals(fluid)).mapToLong(f -> f.getAmount()).sum();
    }
}
