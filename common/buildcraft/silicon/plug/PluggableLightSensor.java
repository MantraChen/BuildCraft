/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.silicon.*;
public final class PluggableLightSensor extends PluggableSidedPlate {
    public PluggableLightSensor(PluggableDefinition def,IPipeHolder holder,Direction side){super(def,holder,side);}
    @Override public ItemStack getPickStack(){return new ItemStack(BCSiliconItems.plugLightSensor);}
    @PipeEventHandler public void addTriggers(PipeEventStatement.AddTriggerInternalSided event) {
        if(event.side==side && isAttached()){event.triggers.add(BCSiliconStatements.TRIGGER_LIGHT_LOW);event.triggers.add(BCSiliconStatements.TRIGGER_LIGHT_HIGH);}
    }
}
