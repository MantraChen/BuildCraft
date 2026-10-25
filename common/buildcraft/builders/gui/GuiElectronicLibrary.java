/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.gui;

import java.util.*;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.snapshot.*;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class GuiElectronicLibrary extends GuiBC8<ContainerElectronicLibrary> {
    private TextFieldWidget search;
    private ButtonWidget previous,next,delete;
    private List<Snapshot.Key> entries=List.of();
    private int page,ticks;
    private boolean deleteFailed;
    public GuiElectronicLibrary(ContainerElectronicLibrary container,PlayerInventory inventory,Text title){super(container,inventory,title);xSize=244;ySize=234;}
    public GuiElectronicLibrary(ContainerElectronicLibrary container){this(container,container.player.getInventory(),Text.translatable("gui.buildcraftbuilders.library"));}
    public static String name(Snapshot.Key key){return key.header==null?key.toString().substring(0,12):key.header.name;}
    @Override protected void init(){
        String query=search==null?"":search.getText();super.init();
        search=addDrawableChild(new TextFieldWidget(textRenderer,x+8,y+20,148,16,Text.translatable("gui.buildcraftbuilders.library.search")));
        search.setMaxLength(256);search.setText(query);search.setPlaceholder(Text.translatable("gui.buildcraftbuilders.library.search"));
        search.setChangedListener(value->{page=0;refresh();});
        previous=addDrawableChild(ButtonWidget.builder(Text.literal("<"),button->{page--;refresh();}).dimensions(x+8,y+109,20,16).build());
        next=addDrawableChild(ButtonWidget.builder(Text.literal(">"),button->{page++;refresh();}).dimensions(x+136,y+109,20,16).build());
        delete=addDrawableChild(ButtonWidget.builder(Text.translatable("gui.buildcraftbuilders.library.delete"),button->{
            var key=container.selection();if(key==null)return;
            deleteFailed=!GlobalSavedDataSnapshots.get(NetSide.CLIENT).removeSnapshot(key);
            if(!deleteFailed)container.select(null);refresh();
        }).dimensions(x+170,y+109,66,16).build());
        refresh();
    }
    private void refresh(){
        String query=search.getText().toLowerCase(Locale.ROOT);
        entries=GlobalSavedDataSnapshots.get(NetSide.CLIENT).getList().stream().filter(key->name(key).toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing(GuiElectronicLibrary::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Snapshot.Key::toString)).toList();
        page=Math.max(0,Math.min(page,Math.max(0,(entries.size()-1)/5)));
        previous.active=page>0;next.active=(page+1)*5<entries.size();delete.active=container.selection()!=null;
    }
    @Override protected void handledScreenTick(){super.handledScreenTick();search.tick();container.clientTick();if(++ticks%20==0)refresh();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(search.isFocused() && key!=256){search.keyPressed(key,scan,modifiers);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button==0 && mouseX>=x+8 && mouseX<x+156 && mouseY>=y+42 && mouseY<y+102){
            int index=page*5+(int)(mouseY-y-42)/12;
            if(index<entries.size()){container.select(entries.get(index));deleteFailed=false;refresh();}return true;
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void drawElementBackgrounds(DrawContext context,float delta){
        context.fill(x,y,x+xSize,y+ySize,0xff373737);context.fill(x+2,y+2,x+xSize-2,y+ySize-2,0xffc6c6c6);
        context.fill(x+7,y+41,x+157,y+103,0xff373737);
        for(int row=0;row<5 && page*5+row<entries.size();row++){
            var key=entries.get(page*5+row);var selected=container.selection();
            if(selected!=null && Arrays.equals(selected.hash,key.hash))context.fill(x+8,y+42+row*12,x+156,y+54+row*12,0xff407759);
        }
        for(var slot:container.slots){context.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff373737);context.fill(x+slot.x,y+slot.y,x+slot.x+17,y+slot.y+17,0xffffffff);context.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff8b8b8b);}
        int progress=Math.max(0,Math.min(1000,container.progress()));
        context.fill(x+170,y+101,x+236,y+105,0xff555555);context.fill(x+170,y+101,x+170+66*progress/1000,y+105,0xff3eae69);
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta){
        context.drawText(textRenderer,title,x+8,y+6,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.library.export"),x+171,y+26,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.library.import"),x+171,y+70,0x404040,false);
        context.drawText(textRenderer,"←",x+195,y+43,0x404040,false);context.drawText(textRenderer,"→",x+195,y+87,0x404040,false);
        for(int row=0;row<5 && page*5+row<entries.size();row++)
            context.drawText(textRenderer,textRenderer.trimToWidth(name(entries.get(page*5+row)),140),x+11,y+44+row*12,0xffffff,false);
        String pages=(page+1)+" / "+Math.max(1,(entries.size()+4)/5);
        context.drawText(textRenderer,pages,x+82-textRenderer.getWidth(pages)/2,y+113,0x404040,false);
        context.drawText(textRenderer,Text.translatable("gui.buildcraftbuilders.library.status."+(deleteFailed?10:container.status())),x+8,y+128,0x404040,false);
        context.drawText(textRenderer,"Remake by FelixMantra",x+8,y+223,0x606060,false);
        super.drawElementForegrounds(context,delta);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        super.render(context,mouseX,mouseY,delta);
        if(mouseX>=x+8 && mouseX<x+156 && mouseY>=y+42 && mouseY<y+102){
            int index=page*5+(mouseY-y-42)/12;
            if(index<entries.size()){
                var key=entries.get(index);
                context.drawTooltip(textRenderer,List.of(Text.literal(name(key)),Text.literal(key.toString().substring(0,16))),mouseX,mouseY);
            }
        }
    }
}
