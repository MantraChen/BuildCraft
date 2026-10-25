/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;
import buildcraft.api.transport.*;
import buildcraft.lib.misc.BlockUtil;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
public enum StripesHandlerShears implements IStripesHandlerItem {
    INSTANCE;
    @Override public boolean handle(World world,BlockPos pos,Direction side,ItemStack stack,PlayerEntity player,IStripesActivator activator){
        if(!(stack.getItem() instanceof ShearsItem) || !(world instanceof ServerWorld server))return false;
        var target=pos.offset(side);if(!StripesInteraction.loaded(world,target))return false;var state=world.getBlockState(target);
        if(!(state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.VINE) || state.isOf(Blocks.GLOW_LICHEN) || state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.DEAD_BUSH) || state.isOf(Blocks.COBWEB)))return false;
        var drops=BlockUtil.breakBlockAndGetDrops(server,target,stack,player.getGameProfile(),false);
        if(drops.isEmpty())throw new StripesInteraction.Denied();
        stack.damage(1,player,p->p.sendToolBreakStatus(Hand.MAIN_HAND));
        for(var drop:drops.get())if(!activator.sendItem(drop,side))activator.dropItem(drop,side);
        return true;
    }
}
