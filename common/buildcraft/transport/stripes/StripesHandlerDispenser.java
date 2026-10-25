/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.stripes;

import buildcraft.api.transport.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Run registered vanilla/modded dispenser behaviours against a virtual dispenser inventory. */
public enum StripesHandlerDispenser implements IStripesHandlerItem {
    INSTANCE;
    @Override public boolean handle(World world,BlockPos pos,Direction direction,ItemStack stack,PlayerEntity player,IStripesActivator activator){
        if(!(world instanceof ServerWorld server) || !DispenserBlock.BEHAVIORS.containsKey(stack.getItem()))return false;
        var state=Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING,direction);
        var inventory=new DispenserBlockEntity(pos,state);inventory.setWorld(world);
        var pointer=new BlockPointer(){
            @Override public double getX(){return pos.getX()+.5;}
            @Override public double getY(){return pos.getY()+.5;}
            @Override public double getZ(){return pos.getZ()+.5;}
            @Override public BlockPos getPos(){return pos;}
            @Override public BlockState getBlockState(){return state;}
            @Override public ServerWorld getWorld(){return server;}
            @SuppressWarnings("unchecked") @Override public <T extends BlockEntity> T getBlockEntity(){return (T)inventory;}
        };
        player.setStackInHand(Hand.MAIN_HAND,DispenserBlock.BEHAVIORS.get(stack.getItem()).dispense(pointer,stack.copy()));
        // Bucket and bottle behaviours can leave an extra stack in their source inventory.
        for(int i=0;i<inventory.size();i++){
            var remainder=inventory.removeStack(i);if(remainder.isEmpty())continue;
            if(!player.getInventory().insertStack(remainder) && !remainder.isEmpty()){
                if(!activator.sendItem(remainder,direction))activator.dropItem(remainder,direction);
            }
        }
        return true;
    }
}
