package buildcraft.test.lib.engine;

import static org.junit.Assert.*;
import org.junit.Test;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.engine.*;
import buildcraft.lib.fluid.*;

public class EngineFuelTest {
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER), LAVA = FluidVariant.of(Fluids.LAVA);
    private static FluidTankStorage tank(long amount) { return new FluidTankStorage(amount, () -> {}); }
    private static void fill(FluidTankStorage tank, FluidVariant fluid, long amount) {
        try (Transaction tx = Transaction.openOuter()) { assertEquals(amount, tank.insert(fluid, amount, tx)); tx.commit(); }
    }
    private static FuelRegistry fuel(long power, int ticks, long residue) {
        FuelRegistry registry = new FuelRegistry();
        if (residue == 0) registry.addFuel(Fluids.WATER, power, ticks);
        else registry.addDirtyFuel(Fluids.WATER, power, ticks, FluidStackBC.of(LAVA, residue));
        return registry;
    }
    private static long burnAll(CombustionEngineLogic logic, FluidTankStorage input, FluidTankStorage output) {
        long power = 0; int limit = 100_000;
        while (logic.hasFuel(input) && limit-- > 0) power += logic.burn(input, output, true);
        assertTrue("Engine must finish a finite amount of fuel", limit > 0); return power;
    }
    @Test public void originalNineColdFuelsKeepTheirEnergyAndResidueRatios() {
        FuelRegistry fuels = new FuelRegistry(); CoolantRegistry coolants = new CoolantRegistry();
        buildcraft.energy.BCEnergyFuels.registerDefaults(fuels, coolants);
        assertEquals(9, fuels.getFuels().size());
        var oil = fuels.getFuel(new FluidStackBC(buildcraft.energy.BCEnergyFluids.crudeOil[0], 1));
        assertEquals(3 * MjAPI.MJ, oil.getPowerPerCycle()); assertEquals(10000, oil.getTotalBurningTime());
        assertEquals(125 * 81L, ((buildcraft.api.fuels.IFuelManager.IDirtyFuel) oil).getResidue().amount);
        assertNull(fuels.getFuel(new FluidStackBC(buildcraft.energy.BCEnergyFluids.crudeOil[1], 1)));
        assertEquals(.0023f, coolants.getDegreesPerMb(new FluidStackBC(Fluids.WATER, 1), 120), 0);
    }
    @Test public void oneBucketProducesExactlyItsRecipeEnergyAndResidue() {
        var input=tank(BUCKET); var output=tank(BUCKET); fill(input,WATER,BUCKET);
        var logic=new CombustionEngineLogic(fuel(1234567,1234,101),new CoolantRegistry());
        assertEquals(1234567L*1234,burnAll(logic,input,output));
        assertEquals(101,output.getAmount()); assertEquals(LAVA,output.getResource()); assertEquals(0,input.getAmount());
    }
    @Test public void fractionalWorkCarriesAcrossDifferentFuelsWithoutFreePower() {
        var registry=fuel(13,7,0); registry.addFuel(Fluids.LAVA,17,11);
        var input=tank(BUCKET); var output=tank(BUCKET); var logic=new CombustionEngineLogic(registry,new CoolantRegistry());
        fill(input,WATER,999); long power=burnAll(logic,input,output);
        fill(input,LAVA,777); power+=burnAll(logic,input,output);
        assertEquals((999L*13*7+777L*17*11)/BUCKET,power);
    }
    @Test public void savedPartialTickAndResidueFractionsResumeExactly() {
        var registry=fuel(89,5000,123); var input=tank(BUCKET); var output=tank(BUCKET); fill(input,WATER,BUCKET);
        var logic=new CombustionEngineLogic(registry,new CoolantRegistry());
        long energy=0; for(int i=0;i<123;i++) energy+=logic.burn(input,output,true);
        var restored=new CombustionEngineLogic(registry,new CoolantRegistry()); restored.readNbt(logic.writeNbt());
        var in2=tank(BUCKET); in2.readNbt(input.writeNbt(new net.minecraft.nbt.NbtCompound()));
        var out2=tank(BUCKET); out2.readNbt(output.writeNbt(new net.minecraft.nbt.NbtCompound()));
        energy+=burnAll(restored,in2,out2); assertEquals(89L*5000,energy); assertEquals(123,out2.getAmount());
    }
    @Test public void fullResidueTankStopsFuelConsumptionAndCanResume() {
        var input=tank(BUCKET); var output=tank(BUCKET); fill(input,WATER,BUCKET); fill(output,LAVA,BUCKET);
        var logic=new CombustionEngineLogic(fuel(100,1000,BUCKET),new CoolantRegistry());
        assertEquals(0,logic.burn(input,output,true)); assertEquals(BUCKET,input.getAmount());
        try(var tx=Transaction.openOuter()){ output.extract(LAVA,BUCKET,tx); tx.commit(); }
        assertEquals(100000,burnAll(logic,input,output)); assertEquals(BUCKET,output.getAmount());
    }
    @Test public void incompatibleResidueRollsBackFuelInsteadOfMixing() {
        var input=tank(BUCKET); var output=tank(BUCKET); fill(input,WATER,BUCKET); fill(output,WATER,1);
        var logic=new CombustionEngineLogic(fuel(100,1000,BUCKET),new CoolantRegistry());
        assertEquals(0,logic.burn(input,output,true)); assertEquals(BUCKET,input.getAmount()); assertEquals(1,output.getAmount());
    }
    @Test public void redstoneOffRequiresCoolingAndPenaltyBeforeRestart() {
        var input=tank(BUCKET); var output=tank(BUCKET); var coolant=tank(BUCKET); fill(input,WATER,BUCKET);
        var logic=new CombustionEngineLogic(fuel(100,1000,0),new CoolantRegistry());
        logic.cool(20,true,coolant); assertEquals(20,logic.cool(20,false,coolant),0);
        assertEquals(9,logic.getPenaltyCooling());
        for(int i=0;i<9;i++){ assertEquals(0,logic.burn(input,output,true)); logic.cool(20,true,coolant); }
        assertEquals(BUCKET,input.getAmount()); assertEquals(100,logic.burn(input,output,true));
    }
    @Test public void coolingConsumesOnlyNeededDropletsAndRespectsPerTickLimit() {
        var coolants=new CoolantRegistry(); coolants.addCoolant(Fluids.WATER,.0023f);
        var coolant=tank(BUCKET); fill(coolant,WATER,BUCKET);
        var logic=new CombustionEngineLogic(fuel(100,1000,0),coolants);
        assertEquals(100,logic.cool(100.001,true,coolant),1e-9); assertTrue(BUCKET-coolant.getAmount() < 81);
        long before=coolant.getAmount(); double heat=logic.cool(200,true,coolant);
        assertEquals(40*81,before-coolant.getAmount()); assertTrue(heat>199 && heat<200);
    }
    @Test public void invalidCoolantCannotCoolOrDisappear() {
        var coolants=new CoolantRegistry(); coolants.addCoolant(Fluids.WATER,.0023f);
        var coolant=tank(BUCKET); fill(coolant,LAVA,BUCKET);
        assertEquals(200,new CombustionEngineLogic(fuel(100,1000,0),coolants).cool(200,true,coolant),0);
        assertEquals(BUCKET,coolant.getAmount());
    }
    @Test public void automationPermissionsApplyToIteratorViewsToo() {
        var tank=tank(BUCKET); fill(tank,WATER,100);
        var input=new FluidStorageAccess(tank,true,false); var output=new FluidStorageAccess(tank,false,true);
        try(var tx=Transaction.openOuter()) {
            assertEquals(0,input.extract(WATER,100,tx)); assertEquals(0,input.iterator().next().extract(WATER,100,tx));
            assertEquals(0,output.insert(WATER,100,tx)); assertEquals(100,output.iterator().next().extract(WATER,100,tx));
        }
        assertEquals(100,tank.getAmount());
    }
    @Test public void iceConvertsOnceAndOnlyWhenCompleteVolumeFits() {
        var coolants=new CoolantRegistry(); coolants.addSolidCoolant(new ItemStack(Items.ICE),new FluidStackBC(Fluids.WATER,1000),1.5f);
        var inv=new SimpleInventory(new ItemStack(Items.ICE,2));
        var context=ContainerItemContext.ofSingleSlot(InventoryStorage.of(inv,null).getSlot(0));
        var small=tank(BUCKET); var large=tank(2*BUCKET);
        try(var tx=Transaction.openOuter()){ assertFalse(SolidCoolantTransfer.exchange(coolants,small,context,tx)); tx.commit(); }
        assertEquals(2,inv.getStack(0).getCount()); assertEquals(0,small.getAmount());
        try(var tx=Transaction.openOuter()){ assertTrue(SolidCoolantTransfer.exchange(coolants,large,context,tx)); tx.commit(); }
        assertEquals(1,inv.getStack(0).getCount()); assertEquals(BUCKET*3/2,large.getAmount());
    }
    @Test public void fuelRegistryKeepsVanillaDurationsAndFabricOverrides() {
        assertEquals(1600,SolidFuelBurner.burnTime(new ItemStack(Items.COAL)));
        assertEquals(20000,SolidFuelBurner.burnTime(new ItemStack(Items.LAVA_BUCKET)));
        assertEquals(0,SolidFuelBurner.burnTime(new ItemStack(Items.STONE)));
        var registry=net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE;
        registry.add(Items.PAPER,600);
        try { assertEquals(600,SolidFuelBurner.burnTime(new ItemStack(Items.PAPER))); }
        finally { registry.remove(Items.PAPER); }
    }
    @Test public void nativeSlotRemovalChangesActualMachineInventory() {
        var handler=new buildcraft.lib.tile.item.ItemHandlerSimple(1);
        handler.setStackInSlot(0,new ItemStack(Items.COAL,10));
        var slot=new buildcraft.lib.gui.slot.SlotBase(handler,0,0,0);
        assertEquals(3,slot.takeStack(3).getCount()); assertEquals(7,handler.getStackInSlot(0).getCount());
        handler.setLimitedInsertor(4); assertEquals(4,slot.getMaxItemCount());
        slot.setStack(ItemStack.EMPTY); assertTrue(handler.getStackInSlot(0).isEmpty());
    }
    @Test public void nativeSlotMergeRespectsFuelFilterAndPerSlotLimit() {
        var handler = new buildcraft.lib.tile.item.ItemHandlerSimple(1, 4);
        handler.setChecker((index, stack) -> SolidFuelBurner.burnTime(stack) > 0);
        var slot = new buildcraft.lib.gui.slot.SlotBase(handler, 0, 0, 0);
        handler.setStackInSlot(0, new ItemStack(Items.COAL, 2));
        var input = new ItemStack(Items.COAL, 10);
        slot.insertStack(input);
        assertEquals(8, input.getCount());
        assertEquals(4, handler.getStackInSlot(0).getCount());
        // A bucket left by burning lava can be removed, but cannot be merged by an external click.
        handler.setStackInSlot(0, new ItemStack(Items.BUCKET));
        var bucket = new ItemStack(Items.BUCKET);
        slot.insertStack(bucket);
        assertEquals(1, bucket.getCount()); assertEquals(1, handler.getStackInSlot(0).getCount());
        assertTrue(slot.canTakeItems(null));
    }
    @Test public void nativeSlotDoesNotAdvertiseExtractionFromARestrictedHandler() {
        var handler = new buildcraft.lib.tile.item.ItemHandlerSimple(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        };
        handler.setStackInSlot(0, new ItemStack(Items.COAL, 10));
        var slot = new buildcraft.lib.gui.slot.SlotBase(handler, 0, 0, 0);
        assertFalse(slot.canTakeItems(null)); assertTrue(slot.takeStack(1).isEmpty());
        assertEquals(10, handler.getStackInSlot(0).getCount());
    }
}
