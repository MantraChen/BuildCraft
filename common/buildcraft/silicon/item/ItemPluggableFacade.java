/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.item;

import buildcraft.api.facades.*;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Direction;

public final class ItemPluggableFacade extends Item implements IItemPluggable,IFacadeItem {
    public ItemPluggableFacade(Settings settings){super(settings);}
    public static FacadeInstance getStates(ItemStack stack){return FacadeInstance.fromContainer(stack.getNbt());}
    @Override public ItemStack getDefaultStack(){return createFacadeStack(FacadeInstance.createSingle(FacadeStateManager.INSTANCE.fallback(),false));}
    @Override public ItemStack getFacadeForBlock(BlockState state){
        var info=FacadeStateManager.INSTANCE.find(state);return info==null?ItemStack.EMPTY:createFacadeStack(FacadeInstance.createSingle(info,false));
    }
    @Override public ItemStack createFacadeStack(IFacade facade){
        var nativeFacade=facade instanceof FacadeInstance own?own:FacadeStateManager.INSTANCE.createPhasedFacade(facade.getPhasedStates(),facade.isHollow());
        var stack=new ItemStack(this);stack.getOrCreateNbt().put("facade",nativeFacade.writeToNbt());return stack;
    }
    /** The original creative example; custom phased facades remain available through FacadeAPI. */
    public static FacadeInstance phasedExample(){
        var manager=FacadeStateManager.INSTANCE;
        var stone=manager.find(net.minecraft.block.Blocks.STONE.getDefaultState());
        var planks=manager.find(net.minecraft.block.Blocks.OAK_PLANKS.getDefaultState());
        var log=manager.find(net.minecraft.block.Blocks.OAK_LOG.getDefaultState());
        return stone==null || planks==null || log==null?null:new FacadeInstance(new FacadePhasedState[]{stone.createPhased(null),planks.createPhased(DyeColor.RED),log.createPhased(DyeColor.CYAN)},false);
    }
    @Override public FacadeInstance getFacade(ItemStack stack){return stack.isOf(this)?getStates(stack):null;}
    @Override public Text getName(ItemStack stack){
        var facade=getStates(stack);var state=facade.state(0).stateInfo;
        return Text.translatable(facade.size()>1?"item.buildcraftsilicon.plug_facade.phased":"item.buildcraftsilicon.plug_facade.basic",state.state.getBlock().getName());
    }
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){
        var facade=getStates(stack);
        lines.add(Text.translatable(facade.isHollow?"item.buildcraftsilicon.plug_facade.hollow":"item.buildcraftsilicon.plug_facade.solid"));
        for(var phase:facade.getPhasedStates()){
            if(facade.size()>1)lines.add(Text.translatable("item.buildcraftsilicon.plug_facade.phase",
                Text.translatable(phase.activeColour==null?"item.buildcraftsilicon.plug_facade.default":"color.minecraft."+phase.activeColour.getName()),phase.stateInfo.state.getBlock().getName()));
            if(context.isAdvanced())lines.add(Text.literal(phase.stateInfo.state.toString()));
            else if(!phase.stateInfo.varyingProperties.isEmpty())lines.add(Text.literal(phase.stateInfo.state.getEntries().entrySet().stream()
                .filter(e->phase.stateInfo.varyingProperties.contains(e.getKey())).sorted(java.util.Comparator.comparing(e->e.getKey().getName()))
                .map(e->e.getKey().getName()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(", "))));
        }
        lines.add(Text.literal("Remake by FelixMantra"));
    }
    @Override public PipePluggable onPlace(ItemStack stack,IPipeHolder holder,Direction side,PlayerEntity player,Hand hand){
        if(holder.getPipe()==null || !holder.canPlayerInteract(player) || holder.getPluggable(side)!=null)return null;
        var data=getStates(stack);var sound=data.state(0).stateInfo.state.getSoundGroup();
        holder.getPipeWorld().playSound(null,holder.getPipePos(),sound.getPlaceSound(),net.minecraft.sound.SoundCategory.BLOCKS,1,.8f);
        return new PluggableFacade(BCSiliconPlugs.facade,holder,side,data);
    }
}
