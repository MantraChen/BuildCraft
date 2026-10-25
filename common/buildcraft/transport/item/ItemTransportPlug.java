/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.item;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.plug.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;

/** Native blocker/adaptor placement with the original eligibility rules. */
public final class ItemTransportPlug extends Item implements IItemPluggable {
    public final boolean powerAdaptor;
    public ItemTransportPlug(Settings settings,boolean powerAdaptor){super(settings);this.powerAdaptor=powerAdaptor;}
    @Override public PipePluggable onPlace(ItemStack stack,IPipeHolder holder,Direction side,PlayerEntity player,Hand hand){
        if(holder.getPipe()==null || holder.getPluggable(side)!=null || !holder.canPlayerInteract(player)
            || powerAdaptor && !(holder.getPipe().getBehaviour().getMjConnector(side) instanceof IMjRedstoneReceiver))return null;
        holder.getPipeWorld().playSound(null,holder.getPipePos(),net.minecraft.sound.SoundEvents.BLOCK_STONE_PLACE,net.minecraft.sound.SoundCategory.BLOCKS,1,.8f);
        return powerAdaptor?new PluggablePowerAdaptor(BCTransportPlugs.powerAdaptor,holder,side):new PluggableBlocker(BCTransportPlugs.blocker,holder,side);
    }
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){
        lines.add(Text.translatable(getTranslationKey()+".tip"));lines.add(Text.literal("Remake by FelixMantra"));
    }
}
