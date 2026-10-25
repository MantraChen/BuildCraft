/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
// TODO(R.Chen): blocked by api.blocks.CustomRotationHelper, api.tools.IToolWrench, lib.misc.SoundUtil (not yet migrated)
package buildcraft.core.item;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import buildcraft.api.blocks.CustomRotationHelper;
import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.item.ItemBC_Neptune;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.SoundUtil;

public class ItemWrench_Neptune extends ItemBC_Neptune implements IToolWrench {
    private static final Identifier ADVANCEMENT = new Identifier("buildcraftcore:wrenched");

    public ItemWrench_Neptune(String id) {
        super(new Settings().maxCount(1), id);
    }

    @Override
    public boolean canWrench(PlayerEntity player, Hand hand, ItemStack wrench, HitResult rayTrace) {
        return true;
    }

    @Override
    public void wrenchUsed(PlayerEntity player, Hand hand, ItemStack wrench, HitResult rayTrace) {
        AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
        player.swingHand(hand);
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean doesSneakBypassUse(ItemStack stack, BlockView world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Hand hand = context.getHand();
        Direction side = context.getSide();
        if (!world.canPlayerModifyAt(player, pos)) return ActionResult.FAIL;
        if (world.getBlockEntity(pos) instanceof buildcraft.lib.tile.TileBC_Neptune tile && !tile.canInteractWith(player)) {
            return ActionResult.FAIL;
        }
        BlockState state = world.getBlockState(pos);
        ActionResult result = world.getBlockEntity(pos) instanceof buildcraft.lib.tile.TileBC_Neptune tile
            ? tile.attemptRotation(player, side) : ActionResult.PASS;
        if (result == ActionResult.PASS) result = CustomRotationHelper.INSTANCE.attemptRotateBlock(world, pos, state, side);
        if (result.isAccepted()) {
            wrenchUsed(player, hand, context.getStack(), new BlockHitResult(context.getHitPos(), side, pos, false));
        }
        SoundUtil.playSlideSound(world, pos, state, result);
        return result;
    }
}
