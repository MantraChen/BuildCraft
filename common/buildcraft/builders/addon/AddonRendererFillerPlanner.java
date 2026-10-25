/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.addon;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;
import buildcraft.builders.snapshot.Template;
import buildcraft.core.client.render.VolumeRender;
import buildcraft.core.marker.volume.IFastAddonRenderer;
import buildcraft.lib.client.render.DetachedRenderer;

/** Exposed preview faces are cached; internal faces do not multiply work for solid large plans. */
public class AddonRendererFillerPlanner implements IFastAddonRenderer<AddonFillerPlanner> {
    private record Face(BlockPos pos,Direction side){}
    private record Mesh(Template.BuildingInfo info,long time,int chunkX,int chunkZ,int range,List<Face> faces){}
    private static final Map<AddonFillerPlanner,Mesh> CACHE=new WeakHashMap<>();
    @Override public void renderAddonFast(AddonFillerPlanner addon,PlayerEntity player,float delta,BufferBuilder ignored){
        var info=addon.buildingInfo;if(info==null)return;
        var client=MinecraftClient.getInstance();int range=client.options.getViewDistance().getValue()*16;
        int chunkX=player.getBlockX()>>4,chunkZ=player.getBlockZ()>>4;long time=player.getWorld().getTime()/5;
        var mesh=CACHE.get(addon);
        if(mesh==null||mesh.info!=info||mesh.time!=time||mesh.chunkX!=chunkX||mesh.chunkZ!=chunkZ||mesh.range!=range){
            var plan=info.getSnapshot();var air=new BitSet(plan.getDataSize());
            for(int i=plan.data.nextSetBit(0);i>=0;i=plan.data.nextSetBit(i+1)){
                var at=info.toWorld(plan.indexToPos(i));
                if(Math.abs(at.getX()-player.getBlockX())<=range&&Math.abs(at.getZ()-player.getBlockZ())<=range&&player.getWorld().isChunkLoaded(at)&&player.getWorld().isAir(at))air.set(i);
            }
            List<Face> faces=new ArrayList<>();
            for(int i=air.nextSetBit(0);i>=0;i=air.nextSetBit(i+1)){
                var local=plan.indexToPos(i);var at=info.toWorld(local);
                for(var side:Direction.values()){
                    var neighbour=local.offset(side);
                    boolean inside=neighbour.getX()>=0&&neighbour.getY()>=0&&neighbour.getZ()>=0&&neighbour.getX()<plan.size.getX()&&neighbour.getY()<plan.size.getY()&&neighbour.getZ()<plan.size.getZ();
                    if(!inside||!air.get(plan.posToIndex(neighbour)))faces.add(new Face(at,side));
                }
            }
            mesh=new Mesh(info,time,chunkX,chunkZ,range,List.copyOf(faces));CACHE.put(addon,mesh);
        }
        var camera=DetachedRenderer.context().camera().getPos();
        var faces=new ArrayList<>(mesh.faces);faces.sort(Comparator.<Face>comparingDouble(face->camera.squaredDistanceTo(Vec3d.ofCenter(face.pos))).reversed());
        var sprite=VolumeRender.white();var frustum=DetachedRenderer.context().frustum();
        for(var face:faces){var bounds=new Box(face.pos).expand(-.025);if(frustum==null||frustum.isVisible(bounds))VolumeRender.face(bounds,face.side,sprite,.45f);}
    }
}
