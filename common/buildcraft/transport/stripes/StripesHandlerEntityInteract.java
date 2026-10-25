/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;
import buildcraft.api.transport.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public enum StripesHandlerEntityInteract implements IStripesHandlerItem {
    INSTANCE;
    @Override public boolean handle(World world,BlockPos pos,Direction side,ItemStack stack,PlayerEntity player,IStripesActivator activator){
        for(var entity:world.getEntitiesByClass(LivingEntity.class,new Box(pos.offset(side)),e->e.isAlive() && e!=player)){
            var result=UseEntityCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,entity,null);
            if(result==ActionResult.FAIL)throw new StripesInteraction.Denied();
            if(result.isAccepted() || player.interact(entity,Hand.MAIN_HAND).isAccepted())return true;
        }
        return false;
    }
}
