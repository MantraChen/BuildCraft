/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.client;

import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.lib.client.model.ModelItemSimple;

/** Clips the actual animated fluid sprite to the original shard mask, including precoloured oil/lava. */
@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public final class ModelFluidShard extends ModelItemSimple implements FabricBakedModel {
    private final List<BakedQuad> base;
    private final Sprite mask, particle;
    private final Map<FluidVariant,List<BakedQuad>> cache=new LinkedHashMap<>(32,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<FluidVariant,List<BakedQuad>> entry) { return size()>64; }
    };

    public ModelFluidShard(BakedModel fallback, Sprite mask) {
        super(List.of(),fallback.getTransformation(),false);
        base=fallback.getQuads(null,null,Random.create(0)).stream().filter(quad->quad.getColorIndex()==0).toList();
        this.mask=mask; particle=fallback.getParticleSprite();
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) { return face==null?base:List.of(); }
    @Override public Sprite getParticleSprite() { return particle; }
    @Override public boolean isVanillaAdapter() { return false; }
    @Override public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> random, RenderContext context) {}
    @Override public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) {
        var renderer=RendererAccess.INSTANCE.getRenderer(); var emitter=context.getEmitter();
        var cutout=renderer.materialFinder().blendMode(BlendMode.CUTOUT).find();
        for(var quad:base) emitter.fromVanilla(quad,cutout,null).emit();
        var fluid=ItemFragileFluidContainer.getFluid(stack);
        if(fluid.isEmpty()) return;
        var material=renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT)
            .emissive(fluid.getFluid().getDefaultState().getBlockState().getLuminance()>0).find();
        for(var quad:getFluidQuads(fluid.getFluidVariant())) emitter.fromVanilla(quad,material,null).emit();
    }

    public synchronized List<BakedQuad> getFluidQuads(FluidVariant fluid) { return cache.computeIfAbsent(fluid,this::bakeFluid); }
    private List<BakedQuad> bakeFluid(FluidVariant fluid) {
        var sprites=FluidVariantRendering.getSprites(fluid);
        if(sprites==null || sprites.length==0 || sprites[0]==null) return List.of();
        var sprite=sprites[0]; var quads=new ArrayList<BakedQuad>();
        int width=mask.getContents().getWidth(),height=mask.getContents().getHeight();
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            if(!opaque(x,y)) continue;
            float x0=x/(float)width,x1=(x+1)/(float)width,y0=1-(y+1)/(float)height,y1=1-y/(float)height;
            float z0=7.49f/16,z1=8.51f/16;
            quad(quads,sprite,Direction.NORTH,new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}});
            quad(quads,sprite,Direction.SOUTH,new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}});
            if(!opaque(x-1,y)) quad(quads,sprite,Direction.WEST,new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}});
            if(!opaque(x+1,y)) quad(quads,sprite,Direction.EAST,new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}});
            if(!opaque(x,y-1)) quad(quads,sprite,Direction.UP,new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}});
            if(!opaque(x,y+1)) quad(quads,sprite,Direction.DOWN,new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}});
        }
        return List.copyOf(quads);
    }
    private boolean opaque(int x,int y) {
        return x>=0 && y>=0 && x<mask.getContents().getWidth() && y<mask.getContents().getHeight()
            && !mask.getContents().isPixelTransparent(0,x,y);
    }
    private static void quad(List<BakedQuad> quads,Sprite sprite,Direction face,float[][] points) {
        int[] data=new int[32];
        int normal=(face.getOffsetX()*127 & 255) | (face.getOffsetY()*127 & 255)<<8 | (face.getOffsetZ()*127 & 255)<<16;
        for(int i=0;i<4;i++) {
            float[] p=points[i]; int offset=i*8;
            data[offset]=Float.floatToRawIntBits(p[0]); data[offset+1]=Float.floatToRawIntBits(p[1]); data[offset+2]=Float.floatToRawIntBits(p[2]);
            data[offset+3]=-1; data[offset+4]=Float.floatToRawIntBits(sprite.getFrameU(p[0]*16));
            data[offset+5]=Float.floatToRawIntBits(sprite.getFrameV((1-p[1])*16)); data[offset+7]=normal;
        }
        quads.add(new BakedQuad(data,1,face,sprite,false));
    }
}
