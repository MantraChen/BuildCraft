/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.energy.tile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import team.reborn.energy.api.EnergyStorage;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IItemPipe;

import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.tile.item.StackInsertionFunction;

import buildcraft.energy.BCEnergyGuis;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;

// Remake by FelixMantra: native transactional RF input and configurable MJ conversion.
@SuppressWarnings("UnstableApiUsage")
public class TileEngineRF extends TileEngineBase_BC8 {
    public static final int MAX_RF = 10_000;
    public static final double HEAT_RATE = 0.06;
    public static final double COOLDOWN_RATE = 0.01;

    public static final Map<Item, Long> RF_UPGRADE = new LinkedHashMap<>();

    int currentRF;
    public final ItemHandlerSimple invUpgrades;

    /** Native RF input; simulations and nested transactions restore the entire RF balance. */
    public final EnergyStorage rfEnergyStorage = new RfInput();
    private final class RfInput extends SnapshotParticipant<Integer> implements EnergyStorage {
        @Override protected Integer createSnapshot() { return currentRF; }
        @Override protected void readSnapshot(Integer value) { currentRF = value; }
        @Override protected void onFinalCommit() { markDirty(); }
        @Override
        public boolean supportsInsertion() { return isStorageAvailable(); }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            if (maxAmount < 0) throw new IllegalArgumentException("Negative RF insertion");
            if (!isStorageAvailable() || world.isClient) return 0;
            long max = Math.min((long) (MAX_RF - currentRF), maxAmount);
            if (max <= 0) return 0;
            updateSnapshots(transaction);
            currentRF += (int) max;
            return max;
        }

        @Override
        public boolean supportsExtraction() { return false; }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            if (maxAmount < 0) throw new IllegalArgumentException("Negative RF extraction");
            return 0;
        }

        @Override
        public long getAmount() { return isStorageAvailable() ? currentRF : 0; }

        @Override
        public long getCapacity() { return isStorageAvailable() ? MAX_RF : 0; }
    }

    public TileEngineRF(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        invUpgrades = itemManager.addInvHandler("upgrades", 4, this::isValidUpgrade,
            StackInsertionFunction.getInsertionFunction(1), EnumAccess.NONE);
        invUpgrades.setLimitedInsertor(1);
    }

    // BlockEntity overrides

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("currentRF", currentRF);
    }

    @Override
    public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        currentRF = (int) Math.max(0, Math.min(MAX_RF, nbt.getLong("currentRF")));
    }

    @Override
    public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side == NetSide.CLIENT) {
            if (id == NET_GUI_DATA || id == NET_GUI_TICK) {
                int amount = buffer.readInt();
                if (amount < 0 || amount > MAX_RF) throw new IOException("Invalid RF engine balance");
                currentRF = amount;
            }
        }
    }

    @Override
    public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER) {
            if (id == NET_GUI_DATA || id == NET_GUI_TICK) {
                buffer.writeInt(currentRF);
            }
        }
    }

    protected boolean isValidUpgrade(int slot, ItemStack stack) {
        Item item = stack.getItem();
        return RF_UPGRADE.containsKey(item);
    }

    // TileEngineBase overrides

    @Override
    public boolean onActivated(PlayerEntity player, Hand hand, Direction side, float hitX, float hitY, float hitZ) {
        ItemStack current = player.getStackInHand(hand).copy();
        if (super.onActivated(player, hand, side, hitX, hitY, hitZ)) {
            return true;
        }
        if (!current.isEmpty()) {
            if (current.getItem() instanceof IItemPipe) {
                return false;
            }
        }
        if (!world.isClient) {
            BCEnergyGuis.ENGINE_RF.openGUI(player, getPos());
        }
        return true;
    }

    @Override
    public double getPistonSpeed() {
        switch (getPowerStage()) {
            case BLUE:
                return 0.04;
            case GREEN:
                return 0.05;
            case YELLOW:
                return 0.06;
            case RED:
                return 0.07;
            default:
                return 0;
        }
    }

    @Nonnull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return currentRF > 0 && isRedstonePowered;
    }

    public long getMjPerTick() {
        long value = MjAPI.MJ * 4;
        for (int slot = 0; slot < invUpgrades.getSlots(); slot++) {
            ItemStack stack = invUpgrades.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Long add = RF_UPGRADE.get(stack.getItem());
            if (add != null) {
                value += add;
            }
        }
        return value;
    }

    public int getRfConsumptionRate() {
        final long mjPerTick = getMjPerTick();
        long mjPerRf = buildcraft.lib.BCLibConfig.mjRfConversion.mjPerRf;
        return (int) (mjPerTick / mjPerRf);
    }

    @Override
    protected void burn() {
        if (currentRF <= 0) {
            return;
        }

        if (isRedstonePowered) {
            long mjPerRf = buildcraft.lib.BCLibConfig.mjRfConversion.mjPerRf;
            int maxRf = getRfConsumptionRate();

            int rfConsumed = (int) Math.min(Math.min(currentRF, maxRf), Math.max(0, getMaxPower() - power) / mjPerRf);
            if (rfConsumed <= 0) return;
            long mjGenerated = rfConsumed * mjPerRf;

            currentOutput = mjGenerated;
            addPower(mjGenerated);
            currentRF -= rfConsumed;
            markDirty();
            heat += HEAT_RATE;
            if (heat >= 200) {
                heat = 200;
            }
        }
    }

    @Override
    public void updateHeatLevel() {
        if (heat > MIN_HEAT) {
            heat -= COOLDOWN_RATE;
        }

        if (heat <= MIN_HEAT) {
            heat = MIN_HEAT;
        }

        getPowerStage();
    }

    @Override
    public long getMaxPower() {
        return 1000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerReceived() {
        return 200 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 500 * MjAPI.MJ;
    }

    @Override
    public float explosionRange() {
        return 4;
    }

    @Override
    protected int getMaxChainLength() {
        return 4;
    }

    @Override
    public long getCurrentOutput() {
        return currentOutput;
    }

    public int getCurrentRF() {
        return currentRF;
    }
}
