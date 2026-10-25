/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.gui;

import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.container.ContainerReplacer;
import buildcraft.builders.client.render.SnapshotPreview;
import buildcraft.builders.snapshot.ClientSnapshots;
import buildcraft.lib.gui.GuiBC8;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class GuiReplacer extends GuiBC8<ContainerReplacer>{
    private final SnapshotPreview preview=new SnapshotPreview();
    public GuiReplacer(ContainerReplacer container,PlayerInventory inventory,Text title){super(container,inventory,title);xSize=256;ySize=222;}
    public GuiReplacer(ContainerReplacer container){this(container,container.player.getInventory(),Text.translatable("gui.buildcraftbuilders.replacer"));}
    @Override public void drawElementBackgrounds(DrawContext context,float delta){
        context.fill(x,y,x+xSize,y+ySize,0xff373737);context.fill(x+2,y+2,x+xSize-2,y+ySize-2,0xffc6c6c6);
        context.fill(x+7,y+18,x+249,y+86,0xff555555);
        var header=BCBuildersItems.snapshot.getHeader(container.tile.invSnapshot.getStackInSlot(0));
        preview.draw(context,header==null?null:ClientSnapshots.INSTANCE.getSnapshot(header.key),x+8,y+19,240,66);
        for(var slot:container.slots){context.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff373737);context.fill(x+slot.x,y+slot.y,x+slot.x+17,y+slot.y+17,0xffffffff);context.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff8b8b8b);}
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta){
        context.drawText(textRenderer,title,x+8,y+6,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.replacer.from"),x+91,y+87,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.replacer.to"),x+139,y+87,0x404040,false);
        context.drawText(textRenderer,"→",x+117,y+100,0x404040,false);
        var header=BCBuildersItems.snapshot.getHeader(container.tile.invSnapshot.getStackInSlot(0));
        if(header!=null)context.drawText(textRenderer,textRenderer.trimToWidth(header.name,61),x+28,y+100,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.replacer.status."+container.tile.getStatus(),container.tile.getReplaced()),x+8,y+117,0x404040,false);
        context.drawText(textRenderer,"Remake by FelixMantra",x+8,y+211,0x606060,false);
        super.drawElementForegrounds(context,delta);
    }
}
