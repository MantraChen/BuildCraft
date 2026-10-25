/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.robotics.zone;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
/** Native height-field quads, perspective projection and picking share the same sampled surfaces. */
public enum ZonePlannerMapRenderer {
    INSTANCE;
    private record Cell(int x,int y,int z,int colour,int step){}
    private final List<Cell> cells=new ArrayList<>();private long generation=-1;private int cachedX,cachedZ,cachedLevel,cachedStep;
    private double pickCenterX,pickCenterZ,pickCamera,pickFocal,pickScreenX,pickScreenY;
    private int pickX,pickY,pickWidth,pickHeight;
    public boolean hasTerrain(){return !cells.isEmpty();}
    /** Resolve the event's current coordinates, rather than a previous render frame's mouse. */
    public BlockPos pick(double mx,double my){
        if(mx<pickX||my<pickY||mx>=pickX+pickWidth||my>=pickY+pickHeight)return null;
        for(int i=cells.size()-1;i>=0;i--){var cell=cells.get(i);double depth=pickCamera-cell.y;if(depth<=2)continue;double scale=pickFocal/depth;
            double wx=pickCenterX+(mx-pickScreenX)/scale,wz=pickCenterZ+(my-pickScreenY)/scale;
            if(wx>=cell.x&&wx<cell.x+cell.step&&wz>=cell.z&&wz<cell.z+cell.step)return new BlockPos((int)Math.floor(wx),cell.y-1,(int)Math.floor(wz));
        }return null;
    }
    public BlockPos draw(DrawContext context,World world,double centerX,double centerZ,double distance,int level,ZonePlan[] layers,int selected,BlockPos origin,BlockPos start,BlockPos end,boolean paint,
        int vx,int vy,int width,int height,double mouseX,double mouseY){
        int centerChunkX=Math.floorDiv((int)Math.floor(centerX),16),centerChunkZ=Math.floorDiv((int)Math.floor(centerZ),16),step=distance>256?4:distance>128?2:1;
        var chunks=new ArrayList<ZonePlannerMapChunkKey>();for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++)chunks.add(new ZonePlannerMapChunkKey(new ChunkPos(centerChunkX+dx,centerChunkZ+dz),world.getRegistryKey().getValue(),level));
        chunks.sort(Comparator.comparingInt(k->Math.abs(k.chunkPos.x-centerChunkX)+Math.abs(k.chunkPos.z-centerChunkZ)));
        var cache=ZonePlannerMapDataClient.INSTANCE;for(var key:chunks)cache.getChunk(world,key);
        if(generation!=cache.generation||cachedX!=centerChunkX||cachedZ!=centerChunkZ||cachedLevel!=level||cachedStep!=step){
            cells.clear();for(var key:chunks){var chunk=cache.getChunk(world,key);if(chunk==null)continue;
                for(int x=0;x<16;x+=step)for(int z=0;z<16;z+=step){var data=chunk.getData(x,z);if(data!=null)cells.add(new Cell(key.chunkPos.getStartX()+x,data.posY+1,key.chunkPos.getStartZ()+z,data.colour,step));}
            }
            cells.sort(Comparator.comparingInt(Cell::y));generation=cache.generation;cachedX=centerChunkX;cachedZ=centerChunkZ;cachedLevel=level;cachedStep=step;
        }
        var centerData=cache.getChunk(world,new ZonePlannerMapChunkKey(new ChunkPos(centerChunkX,centerChunkZ),world.getRegistryKey().getValue(),level));var center=centerData==null?null:centerData.getData((int)Math.floor(centerX),(int)Math.floor(centerZ));
        double camera=(center==null?world.getBottomY()+4:center.posY+1)+distance;
        double focal=height/(2*Math.tan(Math.toRadians(35))),sx=vx+width/2d,sy=vy+height/2d;
        pickCenterX=centerX;pickCenterZ=centerZ;pickCamera=camera;pickFocal=focal;pickScreenX=sx;pickScreenY=sy;pickX=vx;pickY=vy;pickWidth=width;pickHeight=height;BlockPos hover=pick(mouseX,mouseY);
        context.enableScissor(vx,vy,vx+width,vy+height);var matrix=context.getMatrices().peek().getPositionMatrix();var vertices=context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        for(var cell:cells){double depth=camera-cell.y;if(depth<=2)continue;double scale=focal/depth;
            float x1=(float)(sx+(cell.x-centerX)*scale),x2=(float)(sx+(cell.x+cell.step-centerX)*scale),z1=(float)(sy+(cell.z-centerZ)*scale),z2=(float)(sy+(cell.z+cell.step-centerZ)*scale);
            if(x2<vx||x1>vx+width||z2<vy||z1>vy+height)continue;
            int colour=cell.colour;for(int i=0;i<16;i++)if((selected<0||i==selected)&&layers[i].get(cell.x-origin.getX(),cell.z-origin.getZ()))colour=blend(colour,net.minecraft.util.DyeColor.byId(i).getSignColor());
            if(selected>=0&&start!=null&&end!=null&&cell.x>=Math.min(start.getX(),end.getX())&&cell.x<=Math.max(start.getX(),end.getX())&&cell.z>=Math.min(start.getZ(),end.getZ())&&cell.z<=Math.max(start.getZ(),end.getZ()))colour=paint?blend(colour,net.minecraft.util.DyeColor.byId(selected).getSignColor()):blend(colour,0xffffff);
            if(hover!=null&&hover.getY()==cell.y-1&&hover.getX()>=cell.x&&hover.getX()<cell.x+cell.step&&hover.getZ()>=cell.z&&hover.getZ()<cell.z+cell.step)colour=blend(colour,0xffffff);
            float bottomScale=(float)(focal/(camera-world.getBottomY()));float bx1=(float)(sx+(cell.x-centerX)*bottomScale),bx2=(float)(sx+(cell.x+cell.step-centerX)*bottomScale),bz1=(float)(sy+(cell.z-centerZ)*bottomScale),bz2=(float)(sy+(cell.z+cell.step-centerZ)*bottomScale);
            float d=80+(cell.y-world.getBottomY())*.1f;
            wall(vertices,matrix,x1,z1,x2,z1,bx2,bz1,bx1,bz1,d,shade(colour,.65f));wall(vertices,matrix,x2,z2,x1,z2,bx1,bz2,bx2,bz2,d,shade(colour,.65f));
            wall(vertices,matrix,x1,z2,x1,z1,bx1,bz1,bx1,bz2,d,shade(colour,.8f));wall(vertices,matrix,x2,z1,x2,z2,bx2,bz2,bx2,bz1,d,shade(colour,.8f));
            quad(vertices,matrix,x1,z1,x1,z2,x2,z2,x2,z1,d+.01f,colour);
        }
        context.draw();context.disableScissor();return hover;
    }
    private static void wall(VertexConsumer v,org.joml.Matrix4f m,float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,float z,int colour){
        int rgba=0xff000000|colour;v.vertex(m,ax,ay,z).color(rgba).next();v.vertex(m,bx,by,z).color(rgba).next();v.vertex(m,cx,cy,80).color(rgba).next();v.vertex(m,dx,dy,80).color(rgba).next();
    }
    private static int shade(int c,float value){return (int)(((c>>16)&255)*value)<<16|(int)(((c>>8)&255)*value)<<8|(int)((c&255)*value);}
    private static int blend(int a,int b){return (((a&0xfefefe)+(b&0xfefefe))>>1)&0xffffff;}
    private static void quad(VertexConsumer v,org.joml.Matrix4f m,float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,float z,int colour){int rgba=0xff000000|colour;v.vertex(m,ax,ay,z).color(rgba).next();v.vertex(m,bx,by,z).color(rgba).next();v.vertex(m,cx,cy,z).color(rgba).next();v.vertex(m,dx,dy,z).color(rgba).next();}
}
