/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.builders.snapshot;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjBattery;

public abstract class SnapshotBuilder<T extends ITileForSnapshotBuilder> extends BuildingExecutor {
    protected final T tile;

    public SnapshotBuilder(T tile) { this.tile = tile; }

    protected abstract Snapshot.BuildingInfo getBuildingInfo();
    @Override protected World workWorld() { return tile.getWorldBC(); }
    @Override protected MjBattery workBattery() { return tile.getBattery(); }
    @Override protected GameProfile workOwner() { return tile.getOwner(); }
    @Override protected BlockPos workOrigin() { return tile.getBuilderPos(); }
    @Override protected boolean excavates() { return tile.canExcavate(); }
    @Override protected int workSize() {
        return getBuildingInfo() == null ? 0 : getBuildingInfo().getSnapshot().getDataSize();
    }
    @Override protected BlockPos workPosition(int index) {
        return getBuildingInfo().toWorld(getBuildingInfo().getSnapshot().indexToPos(index));
    }
    @Override protected NbtCompound workIdentity() {
        NbtCompound identity = new NbtCompound();
        Snapshot.BuildingInfo info = getBuildingInfo();
        if (info != null) {
            identity.putByteArray("hash", info.getSnapshot().key.hash);
            identity.putLong("size", info.getSnapshot().size.asLong());
            identity.putLong("offset", info.offsetPos.asLong());
            identity.putString("rotation", info.rotation.name());
        }
        return identity;
    }
    @Override protected void workChanged() {
        if (tile instanceof BlockEntity entity) entity.markDirty();
    }
    @Override protected void cancelPlaceTask(PlaceTask task) { acceptDrops(task.required); }
    @Override protected void acceptDrops(List<ItemStack> drops) {
        IItemTransactor inventory = tile instanceof ITileForTemplateBuilder template ? template.getInvResources()
            : tile instanceof ITileForBlueprintBuilder blueprint ? blueprint.getInvResources() : null;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemStack overflow = inventory == null ? stack : inventory.insert(stack, false, false);
            if (!overflow.isEmpty()) {
                BlockPos pos = tile.getBuilderPos();
                tile.getWorldBC().spawnEntity(new ItemEntity(tile.getWorldBC(), pos.getX() + .5,
                    pos.getY() + 1, pos.getZ() + .5, overflow.copy()));
            }
        }
    }
}
