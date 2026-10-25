/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.statements.*;
import buildcraft.core.BCCoreStatements;
import buildcraft.core.statements.*;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.fluid.FluidStorageAccess;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Opens actual gates beside native inventories and tanks, with no Forge capability objects. */
public final class GateStorageGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3,3,3);
    private static final Map<BlockEntity, SingleFluidStorage> SIDED_TANKS = new WeakHashMap<>();
    static {
        FluidStorage.SIDED.registerForBlocks((world,pos,state,tile,side) -> {
            if (tile == null || side != Direction.WEST && side != Direction.NORTH) return null;
            var tank = SIDED_TANKS.computeIfAbsent(tile, ignored -> SingleFluidStorage.withFixedCapacity(FluidConstants.BUCKET, () -> {}));
            return new FluidStorageAccess(tank, side == Direction.WEST, false);
        }, Blocks.ENDER_CHEST);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeInventoryConditionsAndThresholdsNeverConsumeItems(TestContext c) {
        c.setBlockState(POS.east(), Blocks.BARREL);
        var barrel = c.getBlockEntity(POS.east()); var inv = (Inventory) barrel;
        var gate = openGate(c);
        c.assertTrue(gate.triggers.stream().filter(t -> t.delegate instanceof TriggerInventory || t.delegate instanceof TriggerInventoryLevel).count() == 7,
            "Native barrel did not expose all seven inventory conditions");
        check(c, BCCoreStatements.TRIGGER_INVENTORY_EMPTY, barrel, null, true);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_SPACE, barrel, null, true);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_CONTAINS, barrel, null, false);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_FULL, barrel, null, false);
        for (int slot=0; slot<inv.size(); slot++) inv.setStack(slot,new ItemStack(Items.DIAMOND,32));
        check(c, BCCoreStatements.TRIGGER_INVENTORY_BELOW_25, barrel, null, false);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_BELOW_50, barrel, null, false);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_BELOW_75, barrel, null, true);
        var sample = sample(new ItemStack(Items.DIAMOND,7));
        var before = barrel.createNbt();
        check(c, BCCoreStatements.TRIGGER_INVENTORY_SPACE, barrel, sample, true);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_BELOW_75, barrel, sample, true);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_SPACE, barrel, sample(new ItemStack(Items.EMERALD)), false);
        var named = new ItemStack(Items.DIAMOND); named.setCustomName(net.minecraft.text.Text.literal("different NBT"));
        check(c, BCCoreStatements.TRIGGER_INVENTORY_CONTAINS, barrel, sample(named), false);
        c.assertTrue(before.equals(barrel.createNbt()) && sample[0].getItemStack().getCount()==7, "Condition query mutated inventory or sample");
        for (int slot=0; slot<inv.size(); slot++) inv.setStack(slot,new ItemStack(Items.DIAMOND,64));
        check(c, BCCoreStatements.TRIGGER_INVENTORY_FULL, barrel, null, true);
        check(c, BCCoreStatements.TRIGGER_INVENTORY_SPACE, barrel, sample, false);
        close(gate); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void furnaceConditionsRespectTheQueriedFace(TestContext c) {
        c.setBlockState(POS.east(), Blocks.FURNACE);
        var furnace=c.getBlockEntity(POS.east()); var inv=(Inventory)furnace;
        inv.setStack(0,new ItemStack(Items.RAW_IRON,32));
        inv.setStack(1,new ItemStack(Items.COAL,16));
        inv.setStack(2,new ItemStack(Items.IRON_INGOT,8));
        var before=furnace.createNbt();
        var contains=BCCoreStatements.TRIGGER_INVENTORY_CONTAINS;
        c.assertTrue(contains.isTriggerActive(furnace,Direction.DOWN,null,sample(new ItemStack(Items.RAW_IRON))),"Top input is invisible");
        c.assertTrue(!contains.isTriggerActive(furnace,Direction.EAST,null,sample(new ItemStack(Items.RAW_IRON))),"Side query leaked top input");
        check(c,contains,furnace,sample(new ItemStack(Items.COAL)),true);
        check(c,BCCoreStatements.TRIGGER_INVENTORY_SPACE,furnace,sample(new ItemStack(Items.DIAMOND)),false);
        check(c,BCCoreStatements.TRIGGER_INVENTORY_SPACE,furnace,sample(new ItemStack(Items.COAL)),true);
        check(c,BCCoreStatements.TRIGGER_INVENTORY_BELOW_50,furnace,sample(new ItemStack(Items.COAL)),true);
        c.assertTrue(before.equals(furnace.createNbt()),"Furnace simulation changed fuel or smelting state");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fluidConditionsUseDropletsAndPreserveTankAndBucket(TestContext c) {
        c.setBlockState(POS.east(),BCFactoryBlocks.tank);
        var tile=c.getBlockEntity(POS.east());
        var storage=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.WEST);
        var gate=openGate(c);
        c.assertTrue(gate.triggers.stream().filter(t->t.delegate instanceof TriggerFluidContainer || t.delegate instanceof TriggerFluidContainerLevel).count()==7,
            "Native tank did not expose all seven fluid conditions");
        var water=sample(new ItemStack(Items.WATER_BUCKET)); var lava=sample(new ItemStack(Items.LAVA_BUCKET));
        check(c,BCCoreStatements.TRIGGER_FLUID_EMPTY,tile,null,true);
        check(c,BCCoreStatements.TRIGGER_FLUID_BELOW_25,tile,water,true);
        for (int buckets : new int[]{4,4,4,4}) {
            try(var tx=Transaction.openOuter()) { c.assertTrue(storage.insert(FluidVariant.of(Fluids.WATER),buckets*FluidConstants.BUCKET,tx)==buckets*FluidConstants.BUCKET,"Tank insert failed"); tx.commit(); }
            var before=tile.createNbt();
            long amount=storage.iterator().next().getAmount();
            check(c,BCCoreStatements.TRIGGER_FLUID_CONTAINS,tile,water,true);
            check(c,BCCoreStatements.TRIGGER_FLUID_CONTAINS,tile,lava,false);
            check(c,BCCoreStatements.TRIGGER_FLUID_SPACE,tile,lava,false);
            check(c,BCCoreStatements.TRIGGER_FLUID_EMPTY,tile,null,false);
            check(c,BCCoreStatements.TRIGGER_FLUID_SPACE,tile,water,amount<16*FluidConstants.BUCKET);
            check(c,BCCoreStatements.TRIGGER_FLUID_FULL,tile,null,amount==16*FluidConstants.BUCKET);
            check(c,BCCoreStatements.TRIGGER_FLUID_BELOW_25,tile,water,amount<4*FluidConstants.BUCKET);
            check(c,BCCoreStatements.TRIGGER_FLUID_BELOW_50,tile,water,amount<8*FluidConstants.BUCKET);
            check(c,BCCoreStatements.TRIGGER_FLUID_BELOW_75,tile,water,amount<12*FluidConstants.BUCKET);
            c.assertTrue(before.equals(tile.createNbt()) && water[0].getItemStack().isOf(Items.WATER_BUCKET),"Fluid query drained tank or sample bucket");
        }
        close(gate); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void insertionOnlySidedFluidStorageCanStillBeObserved(TestContext c) {
        c.setBlockState(POS.east(),Blocks.ENDER_CHEST);
        var tile=c.getBlockEntity(POS.east()); var gate=openGate(c);
        c.assertTrue(gate.triggers.stream().anyMatch(t->t.delegate==BCCoreStatements.TRIGGER_FLUID_CONTAINS),"External Fabric registration was ignored");
        var storage=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.WEST);
        try(var tx=Transaction.openOuter()) { storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET/2,tx); tx.commit(); }
        c.assertTrue(BCCoreStatements.TRIGGER_FLUID_BELOW_75.isTriggerActive(tile,Direction.SOUTH,null,sample(new ItemStack(Items.WATER_BUCKET))),
            "Read-only side lost the actual tank capacity");
        c.assertTrue(!BCCoreStatements.TRIGGER_FLUID_FULL.isTriggerActive(tile,Direction.SOUTH,null,null),"Half-full read-only tank reported full");
        try(var tx=Transaction.openOuter()) { storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET/2,tx); tx.commit(); }
        check(c,BCCoreStatements.TRIGGER_FLUID_CONTAINS,tile,null,true);
        check(c,BCCoreStatements.TRIGGER_FLUID_EMPTY,tile,null,false);
        check(c,BCCoreStatements.TRIGGER_FLUID_FULL,tile,null,true);
        c.assertTrue(!BCCoreStatements.TRIGGER_FLUID_CONTAINS.isTriggerActive(tile,Direction.WEST,null,null),"Lookup ignored storage face restrictions");
        c.assertTrue(SIDED_TANKS.get(tile).getAmount()==FluidConstants.BUCKET,"Reading insertion-only tank lost fluid");
        close(gate); c.complete();
    }

    private static IStatementParameter[] sample(ItemStack stack) {
        return new IStatementParameter[]{new StatementParameterItemStack(stack)};
    }
    private static void check(TestContext c,ITriggerExternal trigger,BlockEntity tile,IStatementParameter[] params,boolean expected) {
        c.assertTrue(trigger.isTriggerActive(tile,Direction.EAST,null,params)==expected,"Wrong result for "+trigger.getUniqueTag());
    }
    private static ContainerGate openGate(TestContext c) {
        c.setBlockState(POS,BCTransportBlocks.pipeHolder); var pipe=(TilePipeHolder)c.getBlockEntity(POS);
        pipe.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone)); pipe.tick();
        var gate=new PluggableGate(BCSiliconPlugs.gate,pipe,Direction.UP,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.IRON,EnumGateModifier.LAPIS));
        pipe.replacePluggable(Direction.UP,gate);
        var player=c.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(pipe.getPos(),0,0);
        BCSiliconScreens.open(player,gate); return (ContainerGate)player.currentScreenHandler;
    }
    private static void close(ContainerGate gate) { ((net.minecraft.server.network.ServerPlayerEntity)gate.player).closeHandledScreen(); }
}
