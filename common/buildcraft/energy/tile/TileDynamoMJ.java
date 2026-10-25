/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.energy.tile;

import java.io.IOException;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import team.reborn.energy.api.EnergyStorage;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyGuis;
import buildcraft.energy.DynamoEnergyStorage;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.engine.IEngineLikeForLedger;
import buildcraft.lib.misc.data.ModelVariableData;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.TickingBlockEntity;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.tile.item.StackInsertionFunction;

/** MJ input, directional Team Reborn energy output. Remake by FelixMantra. */
public class TileDynamoMJ extends TileBC_Neptune implements IEngineLikeForLedger, TickingBlockEntity {
    public final MjBattery battery = new MjBattery(1000 * MjAPI.MJ);
    public final ItemHandlerSimple invUpgrades;
    public final ModelVariableData clientModelData = new ModelVariableData();
    private long currentOutput, clientStored;
    public final DynamoEnergyStorage energyStorage = new DynamoEnergyStorage(battery,
        () -> BCLibConfig.mjRfConversion.mjPerRf, this::outputLimit,
        () -> world == null ? 0 : world.getTime(), () -> {
            currentOutput = energyStorageUsed(); markDirty();
        }, () -> isStorageAvailable() && !world.isClient);
    private long energyStorageUsed() { return energyStorage.usedThisTick() / BCLibConfig.mjRfConversion.mjPerRf; }
    public TileDynamoMJ(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        invUpgrades = itemManager.addInvHandler("upgrades", 4, (slot, stack) ->
            stack.isOf(BCCoreItems.gearIron) || stack.isOf(BCCoreItems.gearGold),
            StackInsertionFunction.getInsertionFunction(1), EnumAccess.NONE);
        invUpgrades.setLimitedInsertor(1);
        caps.addProvider(new MjCapabilityHelper(new MjBatteryReceiver(battery)));
    }
    public Direction facing() { return getCachedState().get(BlockBCBase_Neptune.BLOCK_FACING_6); }
    public long outputLimit() {
        long limit = 4 * MjAPI.MJ;
        for (int i = 0; i < invUpgrades.getSlots(); i++) {
            ItemStack stack = invUpgrades.getStackInSlot(i);
            if (stack.isOf(BCCoreItems.gearIron)) limit += MjAPI.MJ;
            else if (stack.isOf(BCCoreItems.gearGold)) limit += 4 * MjAPI.MJ;
        }
        return limit;
    }
    @Override public void tick() {
        if (world == null) return;
        if (world.isClient) { clientModelData.tick(); return; }
        battery.tick(world, pos);
        currentOutput = energyStorageUsed();
        BlockPos targetPos = pos.offset(facing());
        if (world.isInBuildLimit(targetPos) && world.isChunkLoaded(targetPos)) {
            EnergyStorage target = EnergyStorage.SIDED.find(world, targetPos, facing().getOpposite());
            if (target != null && target != energyStorage && target.supportsInsertion()) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long offered;
                    try (Transaction simulation = transaction.openNested()) {
                        offered = energyStorage.extract(Long.MAX_VALUE, simulation);
                    }
                    long accepted = target.insert(offered, transaction);
                    if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid energy acceptance");
                    if (energyStorage.extract(accepted, transaction) == accepted) transaction.commit();
                }
            }
        }
        // Received MJ also needs persistence even when there is no output receiver.
        if (clientStored != battery.getStored()) { clientStored = battery.getStored(); markDirty(); }
        if (world.getTime() % 10 == 0) sendNetworkUpdate(NET_RENDER_DATA);
    }
    @Override public boolean onActivated(PlayerEntity player, Hand hand, Direction side, float x, float y, float z) {
        if (!canInteractWith(player)) return false;
        if (!world.isClient) BCEnergyGuis.DYNAMO_MJ.openGUI(player, pos);
        return true;
    }
    @Override public void writeNbt(NbtCompound nbt) { super.writeNbt(nbt); nbt.put("battery", battery.writeToNbt()); }
    @Override public void readFromNBT(NbtCompound nbt) { super.readFromNBT(nbt); battery.readFromNbt(nbt.getCompound("battery")); }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            buffer.writeLong(battery.getStored()); buffer.writeLong(currentOutput);
        }
    }
    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object context) throws IOException {
        super.readPayload(id, buffer, side, context);
        if (side == NetSide.CLIENT && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            clientStored = Math.max(0, buffer.readLong()); currentOutput = Math.max(0, buffer.readLong());
        }
    }
    public long getCurrentOutput() { return currentOutput; }
    @Override public EnumPowerStage getPowerStage() { return isEngineOn() ? EnumPowerStage.GREEN : EnumPowerStage.BLUE; }
    @Override public boolean isEngineOn() { return currentOutput > 0; }
    @Override public long getCurrentMjOutput() { return currentOutput * BCLibConfig.mjRfConversion.mjPerRf; }
    @Override public long getMjStored() { return world != null && world.isClient ? clientStored : battery.getStored(); }
    @Override public double getHeat() { return 0; } // No fuel combustion in the electrical converter.
    public double progress(float delta) { return isEngineOn() && world != null ? ((world.getTime() % 20) + delta) / 20.0 : 0.2; }
}
