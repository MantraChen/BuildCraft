/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra.
 */
package buildcraft.silicon.item;

import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.PluggableLens;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Direction;

/** The original 34 lens/filter variants, now stored in item NBT. */
public final class ItemPluggableLens extends Item implements IItemPluggable {
    public record LensData(DyeColor colour,boolean isFilter){}
    public ItemPluggableLens(Settings settings){super(settings);}
    public static LensData getData(ItemStack stack){
        var nbt=stack.getNbt();
        if(nbt!=null && nbt.contains("lens",NbtElement.COMPOUND_TYPE)){
            var lens=nbt.getCompound("lens");return new LensData(NBTUtilBC.readEnum(lens.get("colour"),DyeColor.class),lens.getBoolean("f"));
        }
        // Forge item dye damage was black=0/white=15, opposite to the block colour id.
        if(nbt!=null && nbt.contains("Damage",NbtElement.NUMBER_TYPE)){
            int damage=nbt.getInt("Damage");
            if(damage>=0 && damage<32)return new LensData(DyeColor.byId(15-(damage&15)),damage>=16);
            if(damage==33)return new LensData(null,true);
        }
        return new LensData(null,false);
    }
    public ItemStack getStack(DyeColor colour,boolean filter){
        var stack=new ItemStack(this);var nbt=new NbtCompound();
        nbt.put("colour",NBTUtilBC.writeEnum(colour));nbt.putBoolean("f",filter);stack.getOrCreateNbt().put("lens",nbt);return stack;
    }
    public static java.util.List<LensData> variants(){
        var result=new java.util.ArrayList<LensData>();
        for(boolean filter:new boolean[]{false,true}){
            for(var colour:DyeColor.values())result.add(new LensData(colour,filter));
            result.add(new LensData(null,filter));
        }
        return java.util.List.copyOf(result);
    }
    @Override public Text getName(ItemStack stack){
        var data=getData(stack);var colour=Text.translatable(data.colour()==null?"item.buildcraftsilicon.plug_lens.clear":"color.minecraft."+data.colour().getName());
        return Text.translatable(data.isFilter()?"item.buildcraftsilicon.plug_lens.filter":"item.buildcraftsilicon.plug_lens.coloured",colour);
    }
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){
        lines.add(Text.translatable(getData(stack).isFilter()?"item.buildcraftsilicon.plug_lens.filter.tip":"item.buildcraftsilicon.plug_lens.tip"));
        lines.add(Text.literal("Remake by FelixMantra"));
    }
    @Override public PipePluggable onPlace(ItemStack stack,IPipeHolder holder,Direction side,PlayerEntity player,Hand hand){
        if(holder.getPipe()==null || !(holder.getPipe().getFlow() instanceof IFlowItems)
            || !holder.canPlayerInteract(player) || holder.getPluggable(side)!=null)return null;
        var data=getData(stack);
        holder.getPipeWorld().playSound(null,holder.getPipePos(),net.minecraft.sound.SoundEvents.BLOCK_GLASS_PLACE,net.minecraft.sound.SoundCategory.BLOCKS,1,.8f);
        return new PluggableLens(BCSiliconPlugs.lens,holder,side,data.colour(),data.isFilter());
    }
}
