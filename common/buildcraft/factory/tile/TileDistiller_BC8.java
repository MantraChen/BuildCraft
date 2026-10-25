/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native transactional distillation. Remake by FelixMantra.
 */
package buildcraft.factory.tile;

import java.io.IOException;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import buildcraft.api.mj.*;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.tiles.*;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.*;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.*;

/** Horizontal input, gas above and liquid below; all three changes share one transaction. */
public class TileDistiller_BC8 extends TileBC_Neptune
        implements TickingBlockEntity, IDebuggable, IHasWork, IMjReceiver, IMjReadable {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("Distiller");
    public static final long MAX_MJ_PER_TICK = 6 * MjAPI.MJ, CAPACITY = 1024 * MjAPI.MJ;
    public final DistilleryTank tankIn, tankGasOut, tankLiquidOut;
    private final Storage<FluidVariant> input, gasOutput, liquidOutput, combined;
    private long storedPower, distillPower;
    private NbtCompound paidRecipe = new NbtCompound();
    private boolean changed = true;
    public boolean isActive;

    public TileDistiller_BC8(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankIn = tankManager.add(new DistilleryTank("tankIn", false));
        tankIn.setFilter(fluid -> recipeFor(fluid) != null);
        tankGasOut = tankManager.add(new DistilleryTank("tankGasOut", true));
        tankLiquidOut = tankManager.add(new DistilleryTank("tankLiquidOut", true));
        input = new FluidStorageAccess(tankIn.getStorage(), true, false);
        gasOutput = new FluidStorageAccess(tankGasOut.getStorage(), false, true);
        liquidOutput = new FluidStorageAccess(tankLiquidOut.getStorage(), false, true);
        combined = new CombinedStorage<>(List.of(input, gasOutput, liquidOutput));
        caps.addProvider(new MjCapabilityHelper(this));
    }

    /** Manual access can recover input, but cannot fill an output tank. */
    public final class DistilleryTank extends Tank {
        private final boolean output;
        private FluidStackBC visible = FluidStackBC.EMPTY;
        DistilleryTank(String name, boolean output) { super(name, 4000, TileDistiller_BC8.this); this.output = output; }
        @Override protected boolean interact(ContainerItemContext context, PlayerEntity player) {
            return FluidTransfers.interact(new FluidStorageAccess(getStorage(), !output, true), context, player);
        }
        @Override protected void onContentsChanged() { super.onContentsChanged(); changed = true; }
        @Override public FluidStackBC getFluidForRender() { return visible.isEmpty() ? null : visible; }
        @Override public int getClientAmount() { return (int) visible.getAmountMB(); }
    }

    private static IDistillationRecipe recipeFor(FluidStackBC input) {
        var manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager == null || input == null || input.isEmpty()) return null;
        var recipe = manager.getDistillationRegistry().getRecipeForInput(input);
        return valid(recipe) ? recipe : null;
    }
    private static boolean valid(IDistillationRecipe recipe) {
        return recipe != null && recipe.powerRequired() > 0 && recipe.powerRequired() <= CAPACITY
            && valid(recipe.in()) && valid(recipe.outGas()) && valid(recipe.outLiquid());
    }
    private static boolean valid(FluidStackBC fluid) {
        return fluid != null && !fluid.isEmpty() && fluid.amount <= 4 * FluidConstants.BUCKET;
    }
    private static NbtCompound signature(IDistillationRecipe recipe) {
        NbtCompound tag = new NbtCompound();
        tag.put("input", recipe.in().toNbt());
        tag.put("gas", recipe.outGas().toNbt());
        tag.put("liquid", recipe.outLiquid().toNbt());
        tag.putLong("power", recipe.powerRequired());
        return tag;
    }
    private boolean process(IDistillationRecipe recipe, boolean commit) {
        if (!valid(recipe)) return false;
        try (Transaction tx = Transaction.openOuter()) {
            FluidStackBC in = recipe.in(), gas = recipe.outGas(), liquid = recipe.outLiquid();
            if (tankIn.getStorage().extract(in.getFluidVariant(), in.amount, tx) != in.amount
                || tankGasOut.getStorage().insert(gas.getFluidVariant(), gas.amount, tx) != gas.amount
                || tankLiquidOut.getStorage().insert(liquid.getFluidVariant(), liquid.amount, tx) != liquid.amount)
                return false;
            if (commit) tx.commit();
            return true;
        }
    }
    private void refund() {
        if (distillPower > 0) { storedPower += distillPower; distillPower = 0; markDirty(); }
        paidRecipe = new NbtCompound();
    }
    @Override public void tick() {
        if (world == null || world.isClient || !isStorageAvailable()) return;
        boolean wasActive = isActive;
        isActive = false;
        var recipe = recipeFor(tankIn.getFluid());
        if (recipe == null || !process(recipe, false)) {
            refund();
        } else {
            NbtCompound signature = signature(recipe);
            if (!paidRecipe.equals(signature) || distillPower > recipe.powerRequired()) { refund(); paidRecipe = signature; }
            // Original throughput ramps up with the reservoir, reaching 6 MJ/t at half capacity.
            long max = Math.min(MAX_MJ_PER_TICK,
                MAX_MJ_PER_TICK * (storedPower + MAX_MJ_PER_TICK) / (CAPACITY / 2));
            long consumed = Math.min(storedPower, Math.min(max, recipe.powerRequired() - distillPower));
            storedPower -= consumed;
            distillPower += consumed;
            isActive = consumed > 0;
            if (distillPower == recipe.powerRequired() && process(recipe, true)) {
                distillPower = 0;
                paidRecipe = new NbtCompound();
                isActive = true;
                AdvancementUtil.unlockAdvancement((ServerWorld) world, getOwner().getId(),
                    new Identifier("buildcraftfactory", "refining"));
            }
            if (consumed > 0 || isActive) markDirty();
        }
        changed |= wasActive != isActive;
        if (changed && world.getTime() % 10 == 0) { changed = false; sendNetworkUpdate(NET_RENDER_DATA); }
    }
    @Override public boolean hasWork() {
        return isStorageAvailable() && world != null && !world.isClient && process(recipeFor(tankIn.getFluid()), false);
    }
    @Override public Storage<FluidVariant> getFluidStorage(Direction side) {
        return side == null ? combined : side == Direction.UP ? gasOutput : side == Direction.DOWN ? liquidOutput : input;
    }
    @Override public long getPowerRequested() { return isStorageAvailable() ? CAPACITY - storedPower - distillPower : 0; }
    @Override public long receivePower(long power, boolean simulate) {
        if (power < 0) throw new IllegalArgumentException("Negative MJ");
        if (!isStorageAvailable() || world == null || world.isClient) return power;
        long accepted = Math.min(power, getPowerRequested());
        if (!simulate && accepted > 0) { storedPower += accepted; markDirty(); }
        return power - accepted;
    }
    @Override public boolean canConnect(IMjConnector other) { return isStorageAvailable(); }
    @Override public long getStored() { return isStorageAvailable() ? storedPower + distillPower : 0; }
    @Override public long getCapacity() { return CAPACITY; }
    public long getStoredPower() { return storedPower; }
    public long getDistillPower() { return distillPower; }
    public int getProgressScaled() {
        var recipe = recipeFor(tankIn.getFluid());
        return recipe == null ? 0 : (int) Math.min(10000, distillPower * 10000 / recipe.powerRequired());
    }
    public boolean isActive() { return isActive; }
    @Override public boolean onActivated(PlayerEntity player, Hand hand, Direction facing, float x, float y, float z) {
        if (!canInteractWith(player)) return false;
        if (!player.getStackInHand(hand).isEmpty()) {
            var context = ContainerItemContext.ofPlayerHand(player, hand);
            var access = facing == Direction.UP ? gasOutput : facing == Direction.DOWN ? liquidOutput : input;
            if (world.isClient && context.find(FluidStorage.ITEM) != null) return true;
            if (FluidTransfers.interact(access, player.isCreative()
                ? ContainerItemContext.forCreativeInteraction(player, player.getStackInHand(hand)) : context, player)) return true;
        }
        if (!world.isClient) buildcraft.factory.BCFactoryScreens.openDistiller(player, this);
        return true;
    }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtCompound battery = new NbtCompound(); battery.putLong("stored", storedPower);
        nbt.put("battery", battery); nbt.putLong("distillPower", distillPower);
        nbt.put("paidRecipe", paidRecipe.copy());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        storedPower = Math.max(0, Math.min(CAPACITY, nbt.getCompound("battery").getLong("stored")));
        distillPower = Math.max(0, Math.min(CAPACITY - storedPower, nbt.getLong("distillPower")));
        paidRecipe = nbt.getCompound("paidRecipe").copy();
        changed = true;
    }
    @Override public IdAllocator getIdAllocator() { return IDS; }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            for (Tank tank : List.of(tankIn, tankGasOut, tankLiquidOut)) {
                tank.getStorage().getResource().toPacket(buffer);
                buffer.writeLong(tank.getStorage().getAmount());
            }
            buffer.writeBoolean(isActive);
        }
    }
    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side == NetSide.CLIENT && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            FluidStackBC[] values = new FluidStackBC[3];
            for (int i = 0; i < 3; i++) {
                FluidVariant fluid = FluidVariant.fromPacket(buffer);
                long amount = buffer.readLong();
                if (amount < 0 || amount > 4 * FluidConstants.BUCKET || fluid.isBlank() != (amount == 0))
                    throw new IOException("Invalid distillery tank");
                values[i] = FluidStackBC.of(fluid, amount);
            }
            boolean active = buffer.readBoolean();
            tankIn.visible = values[0]; tankGasOut.visible = values[1]; tankLiquidOut.visible = values[2];
            isActive = active;
        }
    }
    @Override public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("Input = " + tankIn.getDebugString());
        left.add("Gas = " + tankGasOut.getDebugString());
        left.add("Liquid = " + tankLiquidOut.getDebugString());
        left.add("MJ reservoir = " + storedPower + "; paid = " + distillPower);
    }
}
