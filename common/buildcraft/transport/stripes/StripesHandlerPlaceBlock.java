/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;
import buildcraft.api.transport.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
public enum StripesHandlerPlaceBlock implements IStripesHandlerItem {
    INSTANCE;
    @Override public boolean handle(World world,BlockPos pos,Direction side,ItemStack stack,PlayerEntity player,IStripesActivator activator){
        if(!(stack.getItem() instanceof BlockItem) || !StripesInteraction.loaded(world,pos.offset(side)) || !world.getBlockState(pos.offset(side)).isReplaceable())return false;
        return StripesInteraction.useItemOnBlock(world,player,stack,pos,side);
    }
}
