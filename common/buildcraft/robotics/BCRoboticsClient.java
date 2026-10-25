/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.robotics;
public final class BCRoboticsClient {
    public static void register(){
        net.minecraft.client.gui.screen.ingame.HandledScreens.<buildcraft.lib.gui.ContainerBC_Neptune,buildcraft.robotics.gui.GuiZonePlanner>register(BCRoboticsScreens.ZONE,(h,inv,title)->new buildcraft.robotics.gui.GuiZonePlanner((buildcraft.robotics.container.ContainerZonePlanner)h,inv,title));
        if(BCRoboticsBlocks.zonePlanner!=null)net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(buildcraft.lib.registry.BlockEntityRegistry.type(buildcraft.robotics.tile.TileZonePlanner.class),context->new buildcraft.robotics.client.render.RenderZonePlanner());
    }
}
