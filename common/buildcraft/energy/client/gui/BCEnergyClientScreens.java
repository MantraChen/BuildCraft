package buildcraft.energy.client.gui;

import net.fabricmc.api.*;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import buildcraft.energy.BCEnergyScreens;
import buildcraft.energy.container.*;
import buildcraft.lib.gui.ContainerBC_Neptune;

@Environment(EnvType.CLIENT)
public final class BCEnergyClientScreens {
    private BCEnergyClientScreens() {}
    public static void register() {
        HandledScreens.<ContainerBC_Neptune, GuiDynamoMJ>register(BCEnergyScreens.DYNAMO,
            (handler, inventory, title) -> new GuiDynamoMJ((ContainerDynamoMJ) handler, inventory, title));
        HandledScreens.<ContainerBC_Neptune, GuiEngineStone_BC8>register(BCEnergyScreens.STONE,
            (handler, inventory, title) -> new GuiEngineStone_BC8((ContainerEngineStone_BC8) handler, inventory, title));
        HandledScreens.<ContainerBC_Neptune, GuiEngineIron_BC8>register(BCEnergyScreens.IRON,
            (handler, inventory, title) -> new GuiEngineIron_BC8((ContainerEngineIron_BC8) handler, inventory, title));
    }
}
