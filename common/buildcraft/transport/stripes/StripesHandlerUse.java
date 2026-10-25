/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;
import java.util.*;
import buildcraft.api.transport.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
public enum StripesHandlerUse implements IStripesHandlerItem {
    INSTANCE;
    public static final List<Item> ITEMS=new ArrayList<>();
    @Override public boolean handle(World world,BlockPos pos,Direction side,ItemStack stack,PlayerEntity player,IStripesActivator activator){
        return ITEMS.contains(stack.getItem()) && StripesInteraction.useItemOnBlock(world,player,stack,pos.offset(side),side.getOpposite());
    }
}
