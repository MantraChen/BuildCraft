// Legacy callers share the native frame owned by the BuildCraft screen.
package net.minecraft.client.gui;

public class Gui {
    public static void drawRect(int left, int top, int right, int bottom, int color) {
        buildcraft.lib.gui.GuiDrawContext.current().fill(left, top, right, bottom, color);
    }
    public static void drawTexturedModalRect(double x, double y, int u, int v, int width, int height) {
        // TODO(R.Chen): use DrawContext.drawTexture()
    }
}
