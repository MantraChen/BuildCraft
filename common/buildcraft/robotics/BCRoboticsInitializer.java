/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.robotics;
public final class BCRoboticsInitializer implements net.fabricmc.api.ModInitializer {
    @Override public void onInitialize(){
        buildcraft.lib.registry.RegistryConfig.useOtherModConfigFor("buildcraftrobotics","buildcraftcore");
        BCRoboticsTags.register();BCRoboticsBlocks.preInit();BCRoboticsScreens.register();
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.FUNCTIONAL).register(entries->{
            if(BCRoboticsBlocks.zonePlanner!=null)entries.add(BCRoboticsBlocks.zonePlanner);
            var brush=buildcraft.core.BCCoreItems.paintbrush;if(brush!=null){entries.add(brush);for(var colour:net.minecraft.util.DyeColor.values())entries.add(buildcraft.core.item.ItemPaintbrush_BC8.coloured(new net.minecraft.item.ItemStack(brush),colour));}
        });
    }
}
