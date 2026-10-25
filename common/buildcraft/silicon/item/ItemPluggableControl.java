/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.item;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;

/** Native placement for the three small pipe-control attachments. Remake by FelixMantra. */
public class ItemPluggableControl extends Item implements IItemPluggable {
    public enum Kind { PULSAR, LIGHT_SENSOR, TIMER }
    public final Kind kind;
    public ItemPluggableControl(Settings settings,Kind kind){super(settings);this.kind=kind;}
    @Override public PipePluggable onPlace(ItemStack stack,IPipeHolder holder,Direction side,PlayerEntity player,Hand hand) {
        if(holder.getPipe()==null || !holder.canPlayerInteract(player) || holder.getPluggable(side)!=null
            || kind==Kind.PULSAR && !(holder.getPipe().getBehaviour() instanceof IMjRedstoneReceiver))return null;
        holder.getPipeWorld().playSound(null,holder.getPipePos(),net.minecraft.sound.SoundEvents.BLOCK_STONE_PLACE,net.minecraft.sound.SoundCategory.BLOCKS,1,.8f);
        return switch(kind) {
            case PULSAR->new PluggablePulsar(BCSiliconPlugs.pulsar,holder,side);
            case LIGHT_SENSOR->new PluggableLightSensor(BCSiliconPlugs.lightSensor,holder,side);
            case TIMER->new PluggableTimer(BCSiliconPlugs.timer,holder,side);
        };
    }
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context) {
        lines.add(Text.translatable(getTranslationKey()+".tip"));lines.add(Text.literal("Remake by FelixMantra"));
    }
}
