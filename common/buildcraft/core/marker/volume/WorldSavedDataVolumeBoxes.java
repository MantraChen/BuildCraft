/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

// Yarn 1.20.1 renames:
//   WorldSavedData          → PersistentState
//   NbtCompound          → NbtCompound
//   PlayerEntity            → PlayerEntity
//   Box           → net.minecraft.util.math.Box
//   World#isRemote          → World#isClient
//   new BlockPos(Vec3d)     → BlockPos.ofFloored(Vec3d)
//   player.getEyePos()      replaces getPositionVector().add(0, getEyeHeight(), 0)
//   player.getRotationVec(1.0f)     → player.getRotationVector()
//   Vec3d.multiply()           → Vec3d.multiply()
//   MapStorage.getOrLoadData → ServerWorld.getPersistentStateManager()
//                              .getOrCreate(Function<NbtCompound,T>, Supplier<T>, String)
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.net.BCNetworkManager;

public class WorldSavedDataVolumeBoxes extends PersistentState {
    private static final String DATA_NAME = "buildcraft_volume_boxes";

    public final World world;
    public World getWorld() { return world; }
    public final List<VolumeBox> volumeBoxes = new ArrayList<>();
    // Preserve unsupported legacy/corrupt entries for repair instead of silently deleting user data.
    private final List<NbtCompound> unreadEntries=new ArrayList<>();

    public WorldSavedDataVolumeBoxes(World world) {
        this.world = world;
    }

    public VolumeBox getVolumeBoxAt(BlockPos pos) {
        return volumeBoxes.stream().filter(vb -> vb.box.contains(pos)).findFirst().orElse(null);
    }

    public void addVolumeBox(BlockPos pos) {
        if(volumeBoxes.size()>=MessageVolumeBoxes.MAX_BOXES)throw new IllegalStateException("Volume box limit reached");
        volumeBoxes.add(new VolumeBox(world, pos));
    }

    public VolumeBox getVolumeBoxFromId(UUID id) {
        return volumeBoxes.stream().filter(vb -> vb.id.equals(id)).findFirst().orElse(null);
    }

    public VolumeBox getCurrentEditing(PlayerEntity player) {
        return volumeBoxes.stream().filter(vb -> vb.isEditingBy(player)).findFirst().orElse(null);
    }

    public void tick() {
        AtomicBoolean dirty = new AtomicBoolean(false);
        volumeBoxes.stream().filter(VolumeBox::isEditing).forEach(vb -> {
            PlayerEntity player = vb.getPlayer(world);
            if (player == null) {
                vb.pauseEditing();
                dirty.set(true);
            } else if(player.isSpectator()){
                vb.cancelEditing();dirty.set(true);
            } else {
                // player.getEyePos() replaces getPositionVector().add(0, getEyeHeight(), 0)
                BlockPos lookingAt = BlockPos.ofFloored(
                    player.getEyePos()
                        .add(player.getRotationVector().multiply(vb.getDist()))
                );
                var next=new buildcraft.lib.misc.data.Box(vb.getHeld(),lookingAt);
                if(vb.getLockTargetsStream().anyMatch(Lock.Target.TargetResize.class::isInstance)){
                    vb.cancelEditing();dirty.set(true);return;
                }
                if(!VolumeBox.validBounds(world,next)||!vb.canEdit(player,lookingAt))return;
                if (!vb.box.getBoundingBox().equals(next.getBoundingBox())) {
                    vb.box=next;
                    vb.addons.values().forEach(Addon::onVolumeBoxSizeChange);
                    dirty.set(true);
                }
            }
        });
        for (VolumeBox vb : volumeBoxes) {
            List<Lock> locksToRemove = new ArrayList<>(vb.locks).stream()
                .filter(lock -> !lock.cause.stillWorks(world))
                .collect(Collectors.toList());
            if (!locksToRemove.isEmpty()) {
                vb.locks.removeAll(locksToRemove);
                dirty.set(true);
            }
        }
        if (dirty.get()) {
            markDirty();
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world instanceof ServerWorld serverWorld) {
            MessageVolumeBoxes.send(serverWorld,net.fabricmc.fabric.api.networking.v1.PlayerLookup.world(serverWorld));
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put("volumeBoxes", NBTUtilBC.writeCompoundList(java.util.stream.Stream.concat(volumeBoxes.stream().map(VolumeBox::writeToNBT),unreadEntries.stream().map(NbtCompound::copy))));
        return nbt;
    }

    public static WorldSavedDataVolumeBoxes fromNbt(NbtCompound nbt, World world) {
        WorldSavedDataVolumeBoxes instance = new WorldSavedDataVolumeBoxes(world);
        NBTUtilBC.readCompoundList(nbt.get("volumeBoxes")).forEach(tag->{
            try{
                if(instance.volumeBoxes.size()>=MessageVolumeBoxes.MAX_BOXES)throw new IllegalArgumentException("Volume box limit reached");
                var volume=new VolumeBox(world,tag);
                if(instance.getVolumeBoxFromId(volume.id)!=null)throw new IllegalArgumentException("Duplicate volume id");
                instance.volumeBoxes.add(volume);
            }catch(RuntimeException invalid){instance.unreadEntries.add(tag.copy());buildcraft.BuildCraftFabric.LOGGER.warn("Retained unreadable legacy volume {}: {}",tag.get("id"),invalid.getMessage());}
        });
        return instance;
    }

    public static WorldSavedDataVolumeBoxes get(World world) {
        if (world.isClient) {
            throw new IllegalArgumentException("Tried to create a world saved data instance on the client!");
        }
        ServerWorld serverWorld = (ServerWorld) world;
        return serverWorld.getPersistentStateManager().getOrCreate(
            nbt -> WorldSavedDataVolumeBoxes.fromNbt(nbt, world),
            () -> new WorldSavedDataVolumeBoxes(world),
            DATA_NAME
        );
    }
}
