/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.core.item;

import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import buildcraft.lib.item.ItemBC_Neptune;

import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes;

public class ItemVolumeBox extends ItemBC_Neptune {
    public ItemVolumeBox(String id) {
        super(id);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockPos pos = ctx.getBlockPos();
        Direction facing = ctx.getSide();
        BlockPos offset = pos.offset(facing);

        WorldSavedDataVolumeBoxes volumeBoxes = WorldSavedDataVolumeBoxes.get(world);
        if(ctx.getPlayer()==null||ctx.getPlayer().isSpectator()||!world.isInBuildLimit(offset)
            ||!world.getWorldBorder().contains(offset)||!world.isChunkLoaded(offset)||!world.canPlayerModifyAt(ctx.getPlayer(),offset)
            ||ctx.getPlayer().squaredDistanceTo(offset.getX()+.5,offset.getY()+.5,offset.getZ()+.5)>64
            ||volumeBoxes.volumeBoxes.size()>=buildcraft.core.marker.volume.MessageVolumeBoxes.MAX_BOXES
            ||net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getCurrentUnsafe()!=null)return ActionResult.FAIL;
        VolumeBox current = volumeBoxes.getVolumeBoxAt(offset);

        if (current == null) {
            volumeBoxes.addVolumeBox(offset);
            volumeBoxes.markDirty();
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }
}
