/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import buildcraft.api.template.ITemplateHandler;

public enum TemplateHandlerDefault implements ITemplateHandler {
    INSTANCE;

    @Override
    public boolean handle(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
        if (world.isClient || !(stack.getItem() instanceof net.minecraft.item.BlockItem)
            || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos) || !world.isChunkLoaded(pos)
            || !world.isAir(pos) || !world.canPlayerModifyAt(player, pos)) return false;
        BlockHitResult hit = new BlockHitResult(Vec3d.ofBottomCenter(pos), Direction.UP, pos, false);
        ActionResult result = stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        return result.isAccepted() && !world.isAir(pos);
    }
}
