/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.gui;

import buildcraft.builders.container.ContainerArchitectTable;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.lib.gui.GuiBC8;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiArchitectTable extends GuiBC8<ContainerArchitectTable> {
    private static final Identifier BACKGROUND=new Identifier("buildcraftbuilders:textures/gui/architect.png");
    private TextFieldWidget nameField;
    public GuiArchitectTable(ContainerArchitectTable container, PlayerInventory inventory, Text title) {
        super(container,inventory,title); xSize=256; ySize=182;
    }
    public GuiArchitectTable(ContainerArchitectTable container) {
        this(container,container.player.getInventory(),Text.translatable("gui.buildcraftbuilders.architect"));
    }
    @Override protected void init() {
        String editing=nameField==null?container.tile.name:nameField.getText();
        super.init();
        nameField=addDrawableChild(new TextFieldWidget(textRenderer,x+8,y+17,240,14,Text.translatable("gui.buildcraftbuilders.snapshot_name")));
        nameField.setMaxLength(TileArchitectTable.MAX_NAME_LENGTH); nameField.setText(editing);
        nameField.setChangedListener(container::sendNameToServer);
    }
    @Override protected void handledScreenTick() {
        super.handledScreenTick(); nameField.tick();
        if (!nameField.isFocused() && !nameField.getText().equals(container.tile.name)) {
            nameField.setChangedListener(value -> {}); nameField.setText(container.tile.name);
            nameField.setChangedListener(container::sendNameToServer);
        }
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (nameField.isFocused() && key!=256) { nameField.keyPressed(key,scan,modifiers); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void drawElementBackgrounds(DrawContext context,float delta) {
        context.drawTexture(BACKGROUND,x,y,0,0,256,166);
        context.fill(x+4,y+165,x+252,y+182,0xffc6c6c6);
        context.fill(x+8,y+72,x+248,y+77,0xff555555);
        int total=container.tile.getScanTotal();
        int width=total==0?0:(int)(240L*container.tile.getScanned()/total);
        context.fill(x+8,y+72,x+8+width,y+77,0xff3eae69);
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta) {
        context.drawText(textRenderer,title,x+8,y+5,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.snapshot_input"),x+8,y+40,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.scan_status."+container.tile.getScanStatus(),
            container.tile.getScanned(),container.tile.getScanTotal()),x+8,y+60,0x404040,false);
        context.drawText(textRenderer,"Remake by FelixMantra",x+8,y+170,0x606060,false);
        super.drawElementForegrounds(context,delta);
    }
}
