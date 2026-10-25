/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;

import java.util.*;
import buildcraft.api.facades.*;
import buildcraft.lib.world.SingleBlockAccess;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.DyeColor;

/** Discovers states after all mod blocks are registered, without touching a client world. */
public enum FacadeStateManager implements IFacadeRegistry {
    INSTANCE;
    private Map<BlockState,FacadeBlockStateInfo> states=Map.of();
    private int registrySize=-1,revision=-1;
    private synchronized void initialize(){
        int size=Registries.BLOCK.size(),current=FacadeAPI.configurationRevision();
        if(size==registrySize && current==revision)return;
        var found=new LinkedHashMap<BlockState,FacadeBlockStateInfo>();
        var blocks=new ArrayList<Block>();Registries.BLOCK.forEach(blocks::add);
        blocks.sort(Comparator.comparing(b->Registries.BLOCK.getId(b).toString()));
        for(var block:blocks){
            if(FacadeAPI.isDisabled(block))continue;
            var valid=new LinkedHashMap<BlockState,ItemStack>();
            for(var state:block.getStateManager().getStates()){
                var view=new SingleBlockAccess(state);
                if(state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || state.getRenderType()!=BlockRenderType.MODEL || !state.isFullCube(view,SingleBlockAccess.POS))continue;
                var item=FacadeAPI.getMappedStack(state);
                if(item==null)item=new ItemStack(block.asItem());
                valid.put(state,item);
            }
            for(var entry:valid.entrySet()){
                var varying=new HashSet<Property<?>>();
                for(var other:valid.entrySet())if(ItemStack.canCombine(entry.getValue(),other.getValue()))
                    for(var property:entry.getKey().getProperties())
                        if(!entry.getKey().get(property).equals(other.getKey().get(property)))varying.add(property);
                found.put(entry.getKey(),new FacadeBlockStateInfo(entry.getKey(),entry.getValue(),varying));
            }
        }
        states=Collections.unmodifiableMap(found);registrySize=size;revision=current;
    }
    public FacadeBlockStateInfo find(BlockState state){initialize();return states.get(state);}
    public FacadeBlockStateInfo fallback(){
        var stone=find(Blocks.STONE.getDefaultState());
        return stone!=null?stone:new FacadeBlockStateInfo(Blocks.STONE.getDefaultState(),ItemStack.EMPTY,Set.of());
    }
    @Override public Collection<FacadeBlockStateInfo> getValidFacades(){initialize();return states.values();}
    @Override public FacadePhasedState createPhasedState(IFacadeState state,DyeColor colour){
        var info=find(state.getBlockState());if(info==null)throw new IllegalArgumentException("Unsupported facade state");
        return info.createPhased(colour);
    }
    @Override public FacadeInstance createPhasedFacade(IFacadePhasedState[] phases,boolean hollow){
        var copy=new FacadePhasedState[phases.length];
        for(int i=0;i<copy.length;i++)copy[i]=createPhasedState(phases[i].getState(),phases[i].getActiveColor());
        return new FacadeInstance(copy,hollow);
    }
}