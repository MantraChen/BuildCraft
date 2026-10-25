/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;
import buildcraft.api.crops.CropManager;
import buildcraft.api.transport.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
public enum StripesHandlerPlant implements IStripesHandlerItem {
    INSTANCE;
    @Override public boolean handle(World world,BlockPos pos,Direction side,ItemStack stack,PlayerEntity player,IStripesActivator activator){
        var target=pos.offset(side);
        if(CropManager.getDefaultHandler()!=null && CropManager.isSeed(stack)){
            StripesInteraction.allow(player,target,side.getOpposite());
            if(CropManager.plantCrop(world,player,stack,target.down()) || CropManager.plantCrop(world,player,stack,target))return true;
        }
        if(!(stack.getItem() instanceof BlockItem item))return false;var block=item.getBlock();
        if(!(block instanceof PlantBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock || block instanceof BambooBlock))return false;
        return StripesInteraction.useItemOnBlock(world,player,stack,target.down(),Direction.UP) || StripesInteraction.useItemOnBlock(world,player,stack,target,Direction.UP);
    }
}
