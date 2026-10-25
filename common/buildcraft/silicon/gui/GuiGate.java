/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native gate configuration. Remake by FelixMantra.
 */
package buildcraft.silicon.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import buildcraft.api.statements.*;
import buildcraft.lib.gui.GuiDrawContext;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.statement.*;
import buildcraft.silicon.container.ContainerGate;

public final class GuiGate extends HandledScreen<ContainerGate> {
    private static final int PAGE_SIZE = 6;
    private int selectedRow = -1, page;
    private boolean selectedAction;
    private String query = "";
    private final List<ClickableWidget> selector = new ArrayList<>();
    private final List<Integer> filtered = new ArrayList<>();
    private TextFieldWidget search;

    public GuiGate(ContainerGate handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = handler.panelWidth;
        backgroundHeight = Math.max(206, handler.inventoryY + 82);
        playerInventoryTitleX = (backgroundWidth - 162) / 2;
        playerInventoryTitleY = handler.inventoryY - 11;
    }
    @Override protected void init() {
        super.init();
        if (selectedRow >= 0) rebuildSelector();
    }
    private int rowX(int row) { return 8 + row / handler.rows * handler.columnWidth; }
    private int rowY(int row) { return 30 + row % handler.rows * 22; }
    private int actionX(int row) { return rowX(row) + 18 * (1 + handler.gate.logic.variant.numTriggerArgs) + 12; }
    private void send(int id) {
        // Vanilla ButtonClickC2SPacket only carries a signed byte; gate choices need a bounded VarInt.
        handler.sendMessage(ContainerGate.NET_EDIT, buffer -> buffer.writeVarInt(id));
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        panel(context);
        if (selectedRow >= 0) return;
        for (var slot : handler.slots) slot(context, x + slot.x - 1, y + slot.y - 1, null, false);
        for (int row = 0; row < handler.gate.logic.statements.length; row++) {
            for (int a = 0; a < 2; a++) {
                boolean action = a == 1;
                var full = handler.statement(row, action);
                int sx = x + (action ? actionX(row) : rowX(row)), sy = y + rowY(row);
                slot(context, sx, sy, full.get(), action ? handler.gate.logic.actionOn[row] : handler.gate.logic.triggerOn[row]);
                for (int p = 0; p < full.getParamCount(); p++) {
                    slot(context, sx + 18 * (p + 1), sy, full.get(p), false);
                    if (full.get() == null || p >= full.get().maxParameters()) context.fill(sx + 18*(p+1)+1, sy+1, sx+18*(p+2)-1, sy+17, 0x99777777);
                }
            }
            if (row < handler.gate.logic.connections.length) {
                int sx = x + actionX(row) - 11, sy = y + rowY(row) + 10;
                context.fill(sx, sy, sx + 9, sy + 11, handler.gate.logic.connections[row] ? 0xff42a845 : 0xff606060);
                context.drawText(textRenderer, handler.gate.logic.connections[row] ? "|" : "·", sx+2, sy+1, 0xffffffff, false);
            }
        }
    }
    private void panel(DrawContext context) {
        context.fill(x, y, x+backgroundWidth, y+backgroundHeight, 0xff373737);
        context.fill(x+1, y+1, x+backgroundWidth-1, y+backgroundHeight-1, 0xffffffff);
        context.fill(x+3, y+3, x+backgroundWidth-3, y+backgroundHeight-3, 0xffc6c6c6);
    }
    private void slot(DrawContext context, int sx, int sy, IGuiSlot value, boolean active) {
        context.fill(sx, sy, sx+18, sy+18, active ? 0xff36aa43 : 0xff373737);
        context.fill(sx+1, sy+1, sx+17, sy+17, 0xff8b8b8b);
        if (value != null && value.getSprite() != null) GuiIcon.draw(value.getSprite(), sx+1, sy+1, sx+17, sy+17);
        if (value instanceof IStatementParameter param && !param.getItemStack().isEmpty()) {
            context.drawItem(param.getItemStack(), sx+1, sy+1);
            context.drawItemInSlot(textRenderer, param.getItemStack(), sx+1, sy+1);
        } else if (value != null && value.getSprite() == null) context.drawText(textRenderer, "?", sx+6, sy+5, 0xffffffff, false);
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        if (selectedRow >= 0) return;
        context.drawText(textRenderer, title, 8, 6, 0xff404040, false);
        context.drawText(textRenderer, Text.translatable("gui.buildcraftsilicon.gate.columns"), 8, 18, 0xff404040, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xff404040, false);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        try (var scope = GuiDrawContext.use(context)) {
            super.render(context, mouseX, mouseY, delta);
            if (selectedRow >= 0) {
                // Cover the inventory and cursor rendering while the statement picker owns mouse input.
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 400);
                panel(context);
                context.drawText(textRenderer, Text.translatable(selectedAction ? "gui.buildcraftsilicon.gate.select_action" : "gui.buildcraftsilicon.gate.select_trigger"), x+8, y+8, 0xff404040, false);
                for (var widget : selector) widget.render(context, mouseX, mouseY, delta);
                context.getMatrices().pop();
                return;
            }
            drawMouseoverTooltip(context, mouseX, mouseY);
            for (int row = 0; row < handler.gate.logic.statements.length; row++) {
                for (int a = 0; a < 2; a++) {
                    boolean action = a == 1;
                    var full = handler.statement(row, action);
                    int sx = x + (action ? actionX(row) : rowX(row)), sy = y + rowY(row);
                    for (int p = -1; p < full.getParamCount(); p++) if (inside(mouseX, mouseY, sx+18*(p+1), sy, 18, 18)) {
                        IGuiSlot value = p < 0 ? full.get() : full.get(p);
                        var lines = new ArrayList<Text>();
                        if (value != null) value.getTooltip().forEach(line -> lines.add(Text.literal(line)));
                        lines.add(Text.translatable(p < 0 ? "gui.buildcraftsilicon.gate.statement_help" : "gui.buildcraftsilicon.gate.parameter_help"));
                        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
                    }
                }
                if (row < handler.gate.logic.connections.length && inside(mouseX, mouseY, x+actionX(row)-11, y+rowY(row)+10, 9, 11))
                    context.drawTooltip(textRenderer, Text.translatable("gui.buildcraftsilicon.gate.link", Text.translatable("gate.logic."+handler.gate.logic.variant.logic.tag)), mouseX, mouseY);
            }
        }
    }
    private static boolean inside(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x+width && my >= y && my < y+height;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (selectedRow >= 0) {
            for (var widget : selector) if (widget.mouseClicked(mx, my, button)) { setFocused(widget); return true; }
            return true;
        }
        for (int row = 0; row < handler.gate.logic.statements.length; row++) {
            if (button == 0 && row < handler.gate.logic.connections.length && inside(mx, my, x+actionX(row)-11, y+rowY(row)+10, 9, 11)) { send(row); return true; }
            for (int a = 0; a < 2; a++) {
                boolean action = a == 1;
                var full = handler.statement(row, action);
                int sx = x+(action ? actionX(row) : rowX(row)), sy = y+rowY(row);
                if (inside(mx, my, sx, sy, 18, 18)) {
                    if (button == 0) { selectedRow = row; selectedAction = action; query = ""; page = 0; rebuildSelector(); }
                    else if (button == 1) send(ContainerGate.selectionId(row, action, -1));
                    return true;
                }
                for (int p = 0; p < full.getParamCount(); p++) if (inside(mx, my, sx+18*(p+1), sy, 18, 18)) {
                    if (button >= 0 && button <= 2) send(ContainerGate.parameterId(row, action, p, button, hasShiftDown()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    private void closeSelector() { selectedRow = -1; selector.clear(); clearChildren(); setFocused(null); }
    private <T extends ClickableWidget> T selectWidget(T widget) { selector.add(widget); return addSelectableChild(widget); }
    private void rebuildSelector() {
        selector.clear(); clearChildren();
        search = selectWidget(new TextFieldWidget(textRenderer, x+8, y+23, backgroundWidth-16, 18, Text.translatable("gui.buildcraftsilicon.gate.search")));
        search.setText(query);
        search.setChangedListener(value -> { query = value; page = 0; rebuildChoices(); });
        rebuildChoices();
        setFocused(search);
    }
    private void rebuildChoices() {
        // Keep the search field and its focus while replacing page buttons.
        selector.removeIf(widget -> widget != search);
        clearChildren(); addSelectableChild(search);
        filtered.clear();
        var choices = handler.choices(selectedAction);
        for (int i = 0; i < choices.size(); i++) if (label(choices.get(i)).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) filtered.add(i);
        page = Math.min(page, Math.max(0, (filtered.size()-1)/PAGE_SIZE));
        for (int i = page*PAGE_SIZE; i < Math.min(filtered.size(), (page+1)*PAGE_SIZE); i++) {
            int choice = filtered.get(i);
            var statement = choices.get(choice);
            var text = label(statement);
            var button = ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(text, backgroundWidth-28)), b -> {
                send(ContainerGate.selectionId(selectedRow, selectedAction, choice)); closeSelector();
            }).dimensions(x+8, y+46+(i%PAGE_SIZE)*20, backgroundWidth-16, 20).build();
            button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(text)));
            selectWidget(button);
        }
        selectWidget(ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuildChoices(); }).dimensions(x+8, y+171, 20, 20).build()).active = page > 0;
        selectWidget(ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuildChoices(); }).dimensions(x+backgroundWidth-28, y+171, 20, 20).build()).active = (page+1)*PAGE_SIZE < filtered.size();
        selectWidget(ButtonWidget.builder(Text.translatable("gui.back"), b -> closeSelector()).dimensions(x+34, y+171, backgroundWidth-68, 20).build());
    }
    private static String label(StatementWrapper statement) {
        String label = statement.getDescription();
        if (label != null && net.minecraft.util.Language.getInstance().hasTranslation(label)) label = Text.translatable(label).getString();
        if (label == null || label.isBlank()) label = statement.getUniqueTag();
        if (statement.sourcePart.face != null) label += " [" + Text.translatable("gate.side."+statement.sourcePart.face.asString()).getString() + "]";
        return label;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (selectedRow >= 0) {
            if (key == 256) { closeSelector(); return true; }
            search.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char chr, int modifiers) {
        return selectedRow >= 0 ? search.charTyped(chr, modifiers) : super.charTyped(chr, modifiers);
    }
}
