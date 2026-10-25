/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.item;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Hand;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;

import buildcraft.lib.item.ItemBC_Neptune;

import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockWaterGel;
import buildcraft.factory.block.BlockWaterGel.GelStage;

public class ItemWaterGel extends ItemBC_Neptune {

    public ItemWaterGel(String id) {
        super(new Settings().maxCount(16), id);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(7));
        net.minecraft.util.hit.BlockHitResult ray = world.raycast(new RaycastContext(start, end,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, player));

        if (ray.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.fail(stack);
        }
        net.minecraft.util.math.BlockPos rayPos = ray.getBlockPos();
        if (!world.canPlayerModifyAt(player, rayPos) || !player.canPlaceOn(rayPos, ray.getSide(), stack)) {
            return TypedActionResult.fail(stack);
        }

        Block b = world.getBlockState(rayPos).getBlock();
        if (b != Blocks.WATER) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            if (!world.setBlockState(rayPos, BCFactoryBlocks.waterGel.getDefaultState()
                .with(BlockWaterGel.PROP_STAGE, GelStage.SPREAD_0))) return TypedActionResult.fail(stack);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL,
                0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            world.scheduleBlockTick(rayPos, BCFactoryBlocks.waterGel, 200);

            // TODO: Snowball stuff

            // EntitySnowball entitysnowball = new EntitySnowball(world, player);
            // entitysnowball.setHeadingFromThrower(player, player.rotationPitch, player.rotationYaw, 0.0F, 1.5F, 1.0F);
            // world.spawnEntityInWorld(entitysnowball);
        }

        // player.addStat(StatList.getObjectUseStats(this));
        return TypedActionResult.success(stack, world.isClient);
    }

}
