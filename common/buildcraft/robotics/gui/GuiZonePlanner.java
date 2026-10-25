/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.robotics.gui;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.zone.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
public class GuiZonePlanner extends GuiBC8<ContainerZonePlanner> {
    private static final Identifier TEXTURE=new Identifier("buildcraftrobotics:textures/gui/zone_planner.png");
    public double centerX,centerZ,distance=96;public int level;public BlockPos hovered;
    private BlockPos start,end;private boolean panning,paint;private int brush=-1;
    public GuiZonePlanner(ContainerZonePlanner container){this(container,container.player.getInventory(),Text.translatable("block.buildcraftrobotics.zone_planner"));}
    public GuiZonePlanner(ContainerZonePlanner container,PlayerInventory inventory,Text title){
        super(container,inventory,title);xSize=256;ySize=228;centerX=container.tile.getPos().getX()+.5;centerZ=container.tile.getPos().getZ()+.5;
        var world=container.player.getWorld();var pos=container.player.getBlockPos();int ceiling=pos.getY()+1;
        while(ceiling<world.getTopY()-1&&!world.getBlockState(new BlockPos(pos.getX(),ceiling,pos.getZ())).isSideSolidFullSquare(world,new BlockPos(pos.getX(),ceiling,pos.getZ()),Direction.DOWN))ceiling++;
        level=Math.floorDiv(ceiling,ZonePlannerMapChunkKey.LEVEL_HEIGHT)+1;ZonePlannerMapDataClient.INSTANCE.clear();
    }
    private boolean inside(double mx,double my){return mx>=x+8&&mx<x+221&&my>=y+9&&my<y+109;}
    private int colour(){var c=ItemPaintbrush_BC8.getColour(container.getCursorStack());return c==null?-1:c.getId();}
    @Override public void drawElementBackgrounds(DrawContext context,float delta){
        context.drawTexture(TEXTURE,x,y,0,0,256,228);context.fill(x+8,y+9,x+221,y+109,0xff252d35);
        int input=container.tile.progress(false)*28/200,output=container.tile.progress(true)*28/200;
        if(input>0)context.drawTexture(TEXTURE,x+44,y+128,9,228,input,9);if(output>0)context.drawTexture(TEXTURE,x+236,y+45,0,228,9,output);
        hovered=ZonePlannerMapRenderer.INSTANCE.draw(context,client.world,centerX,centerZ,distance,level,container.tile.layers,colour(),container.tile.getPos(),start,end,paint,x+8,y+9,213,100,mouse.getX(),mouse.getY());
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta){
        String label=hovered==null?Text.translatable(ZonePlannerMapRenderer.INSTANCE.hasTerrain()?"block.buildcraftrobotics.zone_planner":"gui.buildcraftrobotics.zone.loading").getString():hovered.toShortString();
        context.drawText(textRenderer,textRenderer.trimToWidth(label,195),x+24,y+111,0x404040,false);
        context.drawText(textRenderer,container.tile.status()==0?Text.literal("Remake by FelixMantra"):Text.translatable("gui.buildcraftrobotics.zone.status."+container.tile.status()),x+98,y+127,0x404040,false);
        if(inside(mouse.getX(),mouse.getY())){
            var selected=colour();var text=selected<0?Text.translatable("gui.buildcraftrobotics.zone.pan"):Text.translatable("gui.buildcraftrobotics.zone.paint",Text.translatable("color.minecraft."+net.minecraft.util.DyeColor.byId(selected).getName()));
            // Keep instructions in the free strip above the player inventory, including small windows.
            context.drawText(textRenderer,textRenderer.trimToWidth(text.getString(),160),x+88,y+136,0x404040,false);
        }
        super.drawElementForegrounds(context,delta);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(inside(mx,my)){
            hovered=ZonePlannerMapRenderer.INSTANCE.pick(mx,my);brush=colour();panning=container.getCursorStack().isEmpty()&&button==0;
            if(brush>=0&&container.ready[brush]&&hovered!=null&&(button==0||button==1)){start=hovered;end=hovered;paint=button==0;}
            return true;
        }return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(panning){double scale=2*distance*Math.tan(Math.toRadians(35))/100;centerX=Math.max(-29_999_000,Math.min(29_999_000,centerX-dx*scale));centerZ=Math.max(-29_999_000,Math.min(29_999_000,centerZ-dy*scale));return true;}
        if(start!=null){var selected=ZonePlannerMapRenderer.INSTANCE.pick(mx,my);if(selected!=null)end=selected;return true;}return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(start!=null){var selected=ZonePlannerMapRenderer.INSTANCE.pick(mx,my);if(selected!=null)end=selected;if(end!=null&&colour()==brush)container.paint(brush,start.getX(),start.getZ(),end.getX(),end.getZ(),paint);start=null;end=null;return true;}
        if(panning){panning=false;return true;}return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(inside(mx,my)){distance=Math.max(16,Math.min(512,distance*Math.pow(.85,amount)));return true;}return super.mouseScrolled(mx,my,amount);}
}
