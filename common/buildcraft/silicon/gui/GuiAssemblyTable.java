/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.gui;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.gui.*;
import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.container.ContainerAssemblyTable;

/** Native input slots and paged, server-approved recipe toggles. Remake by FelixMantra. */
public final class GuiAssemblyTable extends GuiBC8<ContainerAssemblyTable> {
    private static final Identifier TEXTURE=new Identifier("buildcraftsilicon","textures/gui/assembly_table.png");
    private static final GuiIcon BACKGROUND=new GuiIcon(TEXTURE,0,0,176,207);
    private int page;
    public GuiAssemblyTable(ContainerAssemblyTable container,PlayerInventory inventory,Text title) {
        super(container,inventory,title);xSize=176;ySize=207;
    }
    private List<Map.Entry<Identifier,EnumAssemblyRecipeState>> choices() {
        var choices=new ArrayList<>(container.tile.recipesStates.entrySet());page=Math.min(page,Math.max(0,(choices.size()-1)/12));return choices;
    }
    @Override public void drawElementBackgrounds(DrawContext context,float delta) {
        BACKGROUND.drawAt(rootElement);
        int height=70*container.getProgress()/10000;
        context.fill(x+86,y+106-height,x+90,y+106,0xFFFF4040);
        var choices=choices();
        for(int i=0;i<12 && page*12+i<choices.size();i++) {
            var choice=choices.get(page*12+i);int px=x+116+i%3*18,py=y+36+i/3*18;
            int colour=switch(choice.getValue()) {
                case POSSIBLE -> 0;
                case SAVED -> 0xFFAA4040;
                case SAVED_ENOUGH -> 0xFFDAAD32;
                case SAVED_ENOUGH_ACTIVE -> 0xFF46DD65;
            };
            if(colour!=0) {context.fill(px-1,py-1,px+17,py+17,colour);context.fill(px+1,py+1,px+15,py+15,0xFF707070);}
            var output=container.tile.preview(choice.getKey());context.drawItem(output,px,py);context.drawItemInSlot(textRenderer,output,px,py);
        }
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta) {
        context.drawText(textRenderer,title,x+8,y+6,0x404040,false);
        context.drawText(textRenderer,(container.tile.power/MjAPI.MJ)+" / "+(container.tile.getTarget()/MjAPI.MJ)+" MJ",x+8,y+21,0x404040,false);
        int pages=Math.max(1,(choices().size()+11)/12);
        context.drawText(textRenderer,"< "+(page+1)+"/"+pages+" >",x+118,y+110,0x404040,false);
        context.fill(x,y+210,x+176,y+224,0xC0202020);
        String credit="Remake by FelixMantra";
        context.drawText(textRenderer,credit,x+(176-textRenderer.getWidth(credit))/2,y+213,0xFFF0CE,false);
        super.drawElementForegrounds(context,delta);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(button==0 && mx>=x+116 && mx<x+170 && my>=y+36 && my<y+108) {
            int slot=(int)(mx-x-116)/18+(int)(my-y-36)/18*3;
            var choices=choices();int index=page*12+slot;
            if(index<choices.size()) {
                var entry=choices.get(index);
                container.sendMessage(ContainerAssemblyTable.NET_SELECT,buffer->{buffer.writeIdentifier(entry.getKey());buffer.writeBoolean(entry.getValue()==EnumAssemblyRecipeState.POSSIBLE);});
            }
            return true;
        }
        if(button==0 && mx>=x+116 && mx<x+170 && my>=y+108 && my<y+121) {
            page=Math.max(0,page+(mx<x+143?-1:1));choices();return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double amount) {
        if(mx>=x+114 && mx<x+172 && my>=y+34 && my<y+121) {page=Math.max(0,page+(amount<0?1:-1));choices();return true;}
        return super.mouseScrolled(mx,my,amount);
    }
    @Override public void render(DrawContext context,int mx,int my,float delta) {
        super.render(context,mx,my,delta);
        if(mx>=x+116 && mx<x+170 && my>=y+36 && my<y+108) {
            int index=page*12+(mx-x-116)/18+(my-y-36)/18*3;var choices=choices();
            if(index<choices.size()) {
                var entry=choices.get(index);var output=container.tile.preview(entry.getKey());
                context.drawTooltip(textRenderer,List.of(output.getName(),Text.translatable(
                    entry.getValue()==EnumAssemblyRecipeState.POSSIBLE?"gui.buildcraftsilicon.assembly.select":"gui.buildcraftsilicon.assembly.remove")),Optional.empty(),mx,my);
            }
        }
    }
}
