/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.gui;

import net.fabricmc.api.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.silicon.container.ContainerIntegrationTable;

/** Native integration table screen. Remake by FelixMantra. */
@Environment(EnvType.CLIENT)
public final class GuiIntegrationTable extends GuiBC8<ContainerIntegrationTable> {
    private static final Identifier TEXTURE=new Identifier("buildcraftsilicon:textures/gui/integration_table.png");
    public GuiIntegrationTable(ContainerIntegrationTable container,PlayerInventory inventory,Text title) {
        super(container,inventory,title);xSize=176;ySize=191;
    }
    @Override protected boolean shouldAddHelpLedger(){return false;}
    @Override public void drawElementBackgrounds(DrawContext context,float delta) {
        context.drawTexture(TEXTURE,x,y,0,0,176,191);
        long cost=container.tile.costClient;
        int filled=cost==0?0:(int)Math.min(70,container.tile.power*70/cost);
        context.fill(x+164,y+92-filled,x+168,y+92,0xFFFF4040);
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta) {
        context.drawText(textRenderer,title,x+(176-textRenderer.getWidth(title))/2,y+6,0x404040,false);
        String credit="Remake by FelixMantra";
        context.drawText(textRenderer,credit,x+(176-textRenderer.getWidth(credit))/2,y+97,0x404040,false);
        super.drawElementForegrounds(context,delta);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        if(mouseX>=x+160 && mouseX<x+171 && mouseY>=y+20 && mouseY<y+94) {
            String state=switch(container.tile.statusClient){case 1->"working";case 2->"ready";case 3->"blocked";default->"no_recipe";};
            context.drawTooltip(textRenderer,java.util.List.of(
                Text.literal((container.tile.power/MjAPI.MJ)+"/"+(container.tile.costClient/MjAPI.MJ)+" MJ"),
                Text.translatable("gui.buildcraftsilicon.integration."+state)),mouseX,mouseY);
        }
    }
}
