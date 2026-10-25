/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;

import buildcraft.lib.compat.FluidStackBC;

public class SoundUtil {

    public static void playBucketEmpty(World world, Vec3d pos, FluidStackBC fluid) {
        SoundEvent sound = FluidVariantAttributes.getEmptySound(fluid.getFluidVariant());
        if (sound != null) world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, 1, 1);
    }

    public static void playBucketFill(World world, Vec3d pos, FluidStackBC fluid) {
        SoundEvent sound = FluidVariantAttributes.getFillSound(fluid.getFluidVariant());
        if (sound != null) world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, 1, 1);
    }

    public static void playBlockPlace(World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.BlockState state) {
        BlockSoundGroup sounds = state.getSoundGroup();
        world.playSound(null, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS,
            (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
    }

    public static void playBlockPlace(World world, BlockPos pos) {
        playBlockPlace(world, pos, world.getBlockState(pos));
    }

    public static void playSlideSound(World world, BlockPos pos, BlockState state, ActionResult result) {
        if (result == ActionResult.PASS) return;
        BlockSoundGroup sounds = state.getSoundGroup();
        SoundEvent sound = result.isAccepted() ? SoundEvents.BLOCK_PISTON_CONTRACT : SoundEvents.BLOCK_PISTON_EXTEND;
        world.playSound(null, pos, sound, SoundCategory.BLOCKS,
            (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
    }
}
