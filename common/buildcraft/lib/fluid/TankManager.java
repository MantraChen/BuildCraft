/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.fluid;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import buildcraft.lib.net.PacketBufferBC;

/** Named, persistent machine tanks, exposed together through Fabric's fluid lookup. */
public class TankManager extends CombinedStorage<FluidVariant, Tank> {
    public TankManager() { super(new ArrayList<>()); }
    public <T extends Tank> T add(T tank) {
        if (parts.stream().anyMatch(old -> old.getTankName().equals(tank.getTankName()))) {
            throw new IllegalArgumentException("Duplicate tank: " + tank.getTankName());
        }
        parts.add(tank);
        return tank;
    }
    public void addAll(Tank... tanks) { for (Tank tank : tanks) add(tank); }
    public List<Tank> getTanks() { return List.copyOf(parts); }
    public boolean isEmpty() { return parts.isEmpty(); }
    /** Breaking a machine recovers even input-only and output-only tanks. */
    public void addDrops(net.minecraft.util.collection.DefaultedList<net.minecraft.item.ItemStack> drops) {
        for (Tank tank : parts) buildcraft.api.items.FluidItemDrops.addFluidDrops(drops, tank.getFluid());
    }
    /** Removal is a world lifecycle operation, independent of sided extraction policy. */
    public void clearForRemoval() {
        for (Tank tank : parts) tank.getStorage().readNbt(new NbtCompound());
    }
    public boolean onActivated(PlayerEntity player, BlockPos pos, Hand hand) {
        if (parts.isEmpty()) return false;
        var context = ContainerItemContext.forPlayerInteraction(player, hand);
        // Consume the client interaction too, so a bucket cannot place fluid through the tank.
        if (player.getWorld().isClient) return context.find(FluidStorage.ITEM) != null;
        return FluidTransfers.interact(this, context, player);
    }
    public NbtCompound createNbt() { return serializeNBT(); }
    public NbtCompound serializeNBT() {
        NbtCompound nbt = new NbtCompound();
        for (Tank tank : parts) nbt.put(tank.getTankName(), tank.serializeNBT());
        return nbt;
    }
    public void deserializeNBT(NbtCompound nbt) {
        for (Tank tank : parts) tank.readFromNBT(nbt.getCompound(tank.getTankName()));
    }
    public void writeToBuffer(PacketBufferBC buffer) { for (Tank tank : parts) tank.writeToBuffer(buffer); }
    public void readFromBuffer(PacketBufferBC buffer) { for (Tank tank : parts) tank.readFromBuffer(buffer); }
}
