/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.silicon.*;
public final class PluggableTimer extends PluggableSidedPlate {
    public PluggableTimer(PluggableDefinition def,IPipeHolder holder,Direction side){super(def,holder,side);}
    @Override public ItemStack getPickStack(){return new ItemStack(BCSiliconItems.plugTimer);}
    @PipeEventHandler public void addTriggers(PipeEventStatement.AddTriggerInternal event) {
        if(isAttached()){event.triggers.add(BCSiliconStatements.TRIGGER_TIMER_SHORT);event.triggers.add(BCSiliconStatements.TRIGGER_TIMER_MEDIUM);event.triggers.add(BCSiliconStatements.TRIGGER_TIMER_LONG);}
    }
}
