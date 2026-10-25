/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy.tile;

import java.io.IOException;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import buildcraft.api.fuels.*;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.lib.engine.*;
import buildcraft.lib.fluid.*;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.energy.BCEnergyGuis;

public class TileEngineIron_BC8 extends TileEngineBase_BC8 {
    public static final int MAX_FLUID = 10_000;
    public static final double COOLDOWN_RATE = .05;
    public static final int MAX_COOLANT_PER_TICK = 40;
    public final Tank tankFuel = new Tank("fuel", MAX_FLUID, this, fluid -> BuildcraftFuelRegistry.fuel.getFuel(fluid) != null);
    public final CoolantTank tankCoolant = new CoolantTank("coolant", MAX_FLUID, this);
    public final Tank tankResidue = new Tank("residue", MAX_FLUID, this, fluid ->
        BuildcraftFuelRegistry.fuel.getFuels().stream().anyMatch(fuel -> fuel instanceof IFuelManager.IDirtyFuel dirty
            && dirty.getResidue().isFluidEqual(fluid)));
    private final CombustionEngineLogic combustion = new CombustionEngineLogic(BuildcraftFuelRegistry.fuel, BuildcraftFuelRegistry.coolant);
    private final Storage<FluidVariant> fluidStorage = new CombinedStorage<>(List.of(
        new FluidStorageAccess(tankFuel, true, false), new FluidStorageAccess(tankCoolant, true, false),
        new FluidStorageAccess(tankResidue, false, true)));

    public TileEngineIron_BC8(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankManager.addAll(tankFuel, tankCoolant, tankResidue);
    }
    @Override public Storage<FluidVariant> getFluidStorage(Direction side) { return fluidStorage; }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt); nbt.put("combustion", combustion.writeNbt());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        NbtCompound state = nbt.getCompound("combustion");
        if (!nbt.contains("combustion")) state.putInt("penaltyCooling", nbt.getInt("penaltyCooling"));
        combustion.readNbt(state);
    }
    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side == NetSide.CLIENT && (id == NET_GUI_DATA || id == NET_GUI_TICK)) tankManager.readFromBuffer(buffer);
    }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER && (id == NET_GUI_DATA || id == NET_GUI_TICK)) tankManager.writeToBuffer(buffer);
    }
    @Override public boolean onActivated(PlayerEntity player, Hand hand, Direction side, float x, float y, float z) {
        if (!canInteractWith(player)) return false;
        if (tankCoolant.interactHand(player, hand) || super.onActivated(player, hand, side, x, y, z)) return true;
        if (buildcraft.lib.misc.EntityUtil.getWrenchHand(player) != null || player.getStackInHand(hand).getItem() instanceof IItemPipe) return false;
        if (!world.isClient) BCEnergyGuis.ENGINE_IRON.openGUI(player, getPos());
        return true;
    }
    @Override public double getPistonSpeed() {
        return switch (getPowerStage()) { case BLUE -> .04; case GREEN -> .05; case YELLOW -> .06; case RED -> .07; default -> 0; };
    }
    @Override protected IMjConnector createConnector() { return new EngineConnector(false); }
    @Override public boolean isBurning() { return isRedstonePowered && isActive() && combustion.hasFuel(tankFuel.getStorage()); }
    @Override protected void burn() {
        currentOutput = combustion.burn(tankFuel.getStorage(), tankResidue.getStorage(), isRedstonePowered);
        if (currentOutput > 0) {
            addPower(currentOutput);
            heat += currentOutput * HEAT_PER_MJ / MjAPI.MJ;
        }
    }
    @Override public void updateHeatLevel() { heat = combustion.cool(heat, isRedstonePowered, tankCoolant.getStorage()); }
    @Override public boolean isActive() { return combustion.getPenaltyCooling() == 0; }
    @Override public long getMaxPower() { return 10_000 * MjAPI.MJ; }
    @Override public long maxPowerReceived() { return 2_000 * MjAPI.MJ; }
    @Override public long maxPowerExtracted() { return 500 * MjAPI.MJ; }
    @Override public float explosionRange() { return 4; }
    @Override protected int getMaxChainLength() { return 4; }
    @Override public long getCurrentOutput() { return currentOutput; }
}
