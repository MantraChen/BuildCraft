/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.gui;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.container.ContainerFillerPlanner;
import buildcraft.lib.gui.GuiBC8;

public class GuiFillerPlanner extends GuiBC8<ContainerFillerPlanner> {
    private ButtonWidget pattern, invert;
    private final ButtonWidget[] parameters = new ButtonWidget[4];
    public GuiFillerPlanner(ContainerFillerPlanner container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = 176; ySize = 116;
    }
    public GuiFillerPlanner(ContainerFillerPlanner container) {
        this(container, container.player.getInventory(), Text.translatable("gui.buildcraftbuilders.filler_planner"));
    }
    @Override protected void init() {
        super.init();
        pattern = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            var choices = List.of(BCBuildersStatements.PATTERNS);
            var statement = container.getPatternStatementClient();
            int current = choices.indexOf(statement.get());
            statement.set(choices.get(Math.floorMod(current + (Screen.hasShiftDown() ? -1 : 1), choices.size())));
            container.onStatementChange();
            refreshLabels();
        }).dimensions(x+8, y+20, 160, 18).build());
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            parameters[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                var statement = container.getPatternStatementClient();
                IStatementParameter parameter = statement.get(slot);
                if (parameter == null) return;
                var choices = Arrays.stream(parameter.getPossible(container.addon)).filter(p -> p != null).toList();
                if (choices.isEmpty()) return;
                int index = choices.indexOf(parameter);
                statement.set(slot, choices.get(Math.floorMod(index + (Screen.hasShiftDown() ? -1 : 1), choices.size())));
                container.onStatementChange();
                refreshLabels();
            }).dimensions(x+8+i*40, y+42, 38, 18).build());
        }
        invert = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            container.sendInverted(!container.isInverted()); refreshLabels();
        }).dimensions(x+8, y+63, 160, 18).build());
        refreshLabels();
    }
    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if(!container.canUse(container.player)){client.player.closeHandledScreen();return;}
        container.getPatternStatementClient().set(container.addon.patternStatement.get(),container.addon.patternStatement.getParameters());
        refreshLabels();
    }
    private void refreshLabels() {
        if (pattern == null) return;
        var statement = container.getPatternStatementClient();
        pattern.setMessage(Text.literal(statement.get().getDescription()));
        pattern.active = invert.active = !container.isLocked();
        invert.setMessage(Text.translatable("gui.buildcraftbuilders.invert", enabled(container.isInverted())));
        for (int i = 0; i < 4; i++) {
            var parameter = statement.get(i);
            parameters[i].active = parameter != null && !container.isLocked();
            Text label = parameter == null ? Text.literal("—") : Text.literal(parameter.getDescription());
            parameters[i].setMessage(label);
            parameters[i].setTooltip(parameter == null ? null : Tooltip.of(label));
        }
    }
    private static Text enabled(boolean value) { return Text.translatable(value ? "options.on" : "options.off"); }
    @Override public void drawElementBackgrounds(DrawContext context, float delta) {
        context.fill(x,y,x+xSize,y+ySize,0xff373737);
        context.fill(x+2,y+2,x+xSize-2,y+ySize-2,0xffc6c6c6);
        super.drawElementBackgrounds(context, delta);
    }
    @Override public void drawElementForegrounds(DrawContext context, float delta) {
        context.drawText(textRenderer, title, x+8, y+6, 0x404040, false);
        var info=container.addon.buildingInfo;
        context.drawText(textRenderer,Text.translatable(container.isLocked()?"gui.buildcraftbuilders.planner.locked":"gui.buildcraftbuilders.planner.blocks",
            info==null?0:info.getSnapshot().data.cardinality()),x+8,y+88,0x404040,false);
        context.drawText(textRenderer, "Remake by FelixMantra", x+8, y+104, 0x606060, false);
        super.drawElementForegrounds(context, delta);
    }
}
