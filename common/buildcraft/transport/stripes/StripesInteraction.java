/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Shared native loaded-world and protection checks for stripe operations. */
public final class StripesInteraction {
    private StripesInteraction(){}
    public static final class Denied extends RuntimeException { public Denied(){super(null,null,false,false);} }
    public static boolean loaded(World world,BlockPos pos){return world!=null && !world.isClient && world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos) && world.isChunkLoaded(pos);}
    public static BlockHitResult hit(BlockPos pos,Direction side){return new BlockHitResult(Vec3d.ofCenter(pos).add(side.getOffsetX()*.5,side.getOffsetY()*.5,side.getOffsetZ()*.5),side,pos,false);}
    public static ActionResult allow(PlayerEntity player,BlockPos pos,Direction side){
        var world=player.getWorld();if(!loaded(world,pos) || !world.canPlayerModifyAt(player,pos))throw new Denied();
        var result=UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit(pos,side));if(result==ActionResult.FAIL)throw new Denied();return result;
    }
    public static boolean useItemOnBlock(World world,PlayerEntity player,net.minecraft.item.ItemStack stack,BlockPos pos,Direction side){
        if(world!=player.getWorld())throw new Denied();
        if(allow(player,pos,side).isAccepted())return true;
        return stack.useOnBlock(new net.minecraft.item.ItemUsageContext(player,Hand.MAIN_HAND,hit(pos,side))).isAccepted();
    }
}
