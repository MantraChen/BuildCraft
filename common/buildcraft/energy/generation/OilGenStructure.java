/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native, chunk-clipped oil deposits. Remake by FelixMantra.
 */
package buildcraft.energy.generation;

import java.util.function.IntBinaryOperator;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.*;
import net.minecraft.world.StructureWorldAccess;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.block.BlockSpring;
import buildcraft.api.enums.EnumSpring;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.tile.TileSpringOil;

/** Immutable deposit geometry. No neighboring chunk is read or written when applying a slice. */
public final class OilGenStructure {
    public final OilGenerator.Type type;
    public final BlockPos center;
    public final int bottom, top, reservoirRadius, tendrilRadius, depth, spoutHeight;
    public final boolean spout;
    private final boolean[][] pattern;

    OilGenStructure(OilGenerator.Type type, BlockPos center, int bottom, int top, int reservoirRadius,
            int tendrilRadius, boolean[][] pattern, int depth, boolean spout, int spoutHeight) {
        this.type=type; this.center=center.toImmutable(); this.bottom=bottom; this.top=top;
        this.reservoirRadius=reservoirRadius; this.tendrilRadius=tendrilRadius; this.depth=depth;
        this.spout=spout; this.spoutHeight=spoutHeight;
        this.pattern=new boolean[pattern.length][];
        for(int i=0;i<pattern.length;i++) this.pattern[i]=pattern[i].clone();
    }
    public boolean hasSurfaceOil(int x,int z) {
        int px=x-center.getX()+tendrilRadius,pz=z-center.getZ()+tendrilRadius;
        return px>=0 && pz>=0 && px<pattern.length && pz<pattern[px].length && pattern[px][pz];
    }
    public BlockPos springPos() { return new BlockPos(center.getX(),bottom,center.getZ()); }
    /** Original source estimate used by the oil spring's near-exhaustion advancement. */
    public int estimatedSources(int terrainHeight) {
        int count=0;
        for(boolean[] row:pattern) for(boolean value:row) if(value) count+=depth;
        for(int x=-reservoirRadius;x<=reservoirRadius;x++) for(int z=-reservoirRadius;z<=reservoirRadius;z++)
            for(int y=-reservoirRadius;y<=reservoirRadius;y++)
                if(reservoirRadius>0 && x*x+y*y+z*z<=reservoirRadius*reservoirRadius)count++;
        int shaftRadius=type==OilGenerator.Type.LARGE?1:0;
        if(spout) count += (shaftRadius==1?5:1)*Math.max(0,Math.min(top-1,terrainHeight+spoutHeight)-center.getY()+1)
            + (shaftRadius==1?Math.max(0,Math.min(spoutHeight,top-1-terrainHeight-spoutHeight)):0);
        if(type==OilGenerator.Type.LARGE) count += 5*Math.max(0,center.getY()-bottom);
        return count;
    }
    public int generate(StructureWorldAccess world, ChunkPos chunk, IntBinaryOperator terrainHeight) {
        int extent=Math.max(tendrilRadius,reservoirRadius);
        int minX=Math.max(chunk.getStartX(),center.getX()-extent);
        int maxX=Math.min(chunk.getEndX(),center.getX()+extent);
        int minZ=Math.max(chunk.getStartZ(),center.getZ()-extent);
        int maxZ=Math.min(chunk.getEndZ(),center.getZ()+extent);
        if(minX>maxX || minZ>maxZ)return 0;
        int ground=Math.max(bottom+1,Math.min(top-1,terrainHeight.applyAsInt(center.getX(),center.getZ())));
        int changed=0;
        BlockPos.Mutable at=new BlockPos.Mutable();
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            int dx=x-center.getX(),dz=z-center.getZ(),radial=dx*dx+dz*dz;
            if(hasSurfaceOil(x,z)) {
                int surface=Math.max(bottom+1,Math.min(top-1,terrainHeight.applyAsInt(x,z)));
                // Match the native terrain, not the height of another feature or an earlier oil slice.
                for(int dy=0;dy<depth;dy++) changed+=oil(world,at.set(x,surface-dy,z),false);
            }
            if(reservoirRadius>0 && radial<=reservoirRadius*reservoirRadius) {
                int dy=(int)Math.floor(Math.sqrt(reservoirRadius*reservoirRadius-radial));
                for(int y=center.getY()-dy;y<=center.getY()+dy;y++) changed+=oil(world,at.set(x,y,z),false);
            }
            int shaftRadius=type==OilGenerator.Type.LARGE?1:0;
            if(type==OilGenerator.Type.LARGE && radial<=1)
                for(int y=bottom+1;y<=center.getY();y++) changed+=oil(world,at.set(x,y,z),true);
            if(spout && radial<=shaftRadius*shaftRadius) {
                int end=ground+spoutHeight+(shaftRadius==1 && radial==0?spoutHeight:0);
                for(int y=center.getY();y<=Math.min(top-1,end);y++) changed+=oil(world,at.set(x,y,z),false);
            }
        }
        if(type==OilGenerator.Type.LARGE && chunk.equals(new ChunkPos(center)) && BCCoreBlocks.spring!=null) {
            BlockPos atSpring=springPos();
            if(world.isValidForSetBlock(atSpring)) {
                var existing=world.getBlockState(atSpring);
                // Reapplying a feature never destroys a machine or resets existing pump credit.
                if(!existing.hasBlockEntity() && (natural(existing) || existing.isOf(Blocks.BEDROCK))) {
                    world.setBlockState(atSpring,BCCoreBlocks.spring.getDefaultState().with(BlockSpring.SPRING_TYPE,EnumSpring.OIL),Block.NOTIFY_LISTENERS);
                    if(world.getBlockEntity(atSpring) instanceof TileSpringOil spring) {
                        spring.totalSources=estimatedSources(ground);spring.markDirty();
                    }
                    changed++;
                }
            }
        }
        return changed;
    }
    private int oil(StructureWorldAccess world,BlockPos at,boolean boreBedrock) {
        if(at.getY()<=bottom || at.getY()>=top || !world.isValidForSetBlock(at))return 0;
        var current=world.getBlockState(at);
        var oil=BCEnergyFluids.crudeOil[0].getBlock().getDefaultState();
        if(current.equals(oil) || current.hasBlockEntity() || !natural(current) && !(boreBedrock && current.isOf(Blocks.BEDROCK)))return 0;
        if(!world.setBlockState(at,oil,Block.NOTIFY_LISTENERS))return 0;
        world.scheduleFluidTick(at,BCEnergyFluids.crudeOil[0],BCEnergyFluids.crudeOil[0].getTickRate(world));
        return 1;
    }
    private static boolean natural(BlockState state) {
        return state.isAir() || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.BASE_STONE_NETHER)
            || state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.TERRACOTTA)
            || state.isIn(BlockTags.SNOW) || state.isIn(BlockTags.ICE) || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.CLAY) || state.isOf(Blocks.END_STONE) || state.isOf(Blocks.CALCITE)
            || state.isOf(Blocks.TUFF) || state.getBlock() instanceof FluidBlock && state.getFluidState().isIn(FluidTags.WATER)
            || state.isOf(BCEnergyFluids.crudeOil[0].getBlock());
    }
}
