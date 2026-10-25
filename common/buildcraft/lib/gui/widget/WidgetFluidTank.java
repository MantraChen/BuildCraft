/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.gui.widget;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import buildcraft.lib.fluid.Tank;
import buildcraft.lib.gui.*;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.net.*;

/** Tank binding and click messages exist on both physical sides. Drawing is client-only. */
public class WidgetFluidTank extends Widget_Neptune<ContainerBC_Neptune> {
    public final Tank tank;
    public WidgetFluidTank(ContainerBC_Neptune container, Tank tank) { super(container); this.tank = tank; }
    @Override public MessageUpdateTile handleWidgetDataServer(Object ctx, PacketBufferBC buffer) throws IOException {
        int button = buffer.readUnsignedByte();
        if (buffer.isReadable() || button > 1) throw new IOException("Invalid tank click");
        if (container.canUse(container.player)) tank.onGuiClicked(container);
        return null;
    }
    @Environment(EnvType.CLIENT)
    public IGuiElement createGuiElement(BuildCraftGui gui, IGuiArea area, GuiIcon overlay) {
        return new TankElement(gui, area, overlay);
    }
    @Environment(EnvType.CLIENT)
    private final class TankElement extends GuiElementSimple implements IInteractionElement {
        private final GuiIcon overlay;
        TankElement(BuildCraftGui gui, IGuiArea area, GuiIcon overlay) { super(gui, area); this.overlay = overlay; }
        @Override public void drawBackground(net.minecraft.client.gui.DrawContext context, float partialTicks) {
            buildcraft.lib.misc.GuiUtil.drawFluid(this, tank);
            if (overlay != null) overlay.drawAt(this);
        }
        @Override public void onMouseClicked(int button) {
            if (button >= 0 && button <= 1 && contains(gui.mouse)) sendWidgetData(buffer -> buffer.writeByte(button));
        }
        @Override public void addToolTips(java.util.List<buildcraft.lib.gui.elem.ToolTip> tips) {
            if (contains(gui.mouse)) { tank.getToolTip().refresh(); tips.add(tank.getToolTip()); }
        }
        @Override public void addHelpElements(java.util.List<buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition> elements) {
            elements.add(new buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition(tank.helpInfo, this));
        }
    }
}
