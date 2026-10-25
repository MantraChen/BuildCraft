/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra.
 */
package buildcraft.silicon.item;

import buildcraft.silicon.gate.GateConfigCopier;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.tile.TilePipeHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

public final class ItemGateCopier extends Item {
    public static final String NBT_DATA="gate_data";
    public ItemGateCopier(Settings settings){super(settings.maxCount(1));}
    public static boolean hasCopiedGateData(ItemStack stack){return stack.hasNbt() && stack.getNbt().contains(NBT_DATA,NbtElement.COMPOUND_TYPE);}
    public static NbtCompound getCopiedGateData(ItemStack stack){return hasCopiedGateData(stack)?stack.getNbt().getCompound(NBT_DATA).copy():null;}
    public static void setCopiedGateData(ItemStack stack,NbtCompound data){stack.getOrCreateNbt().put(NBT_DATA,data.copy());}
    private static void changed(PlayerEntity player){player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();}
    private static void message(PlayerEntity player,String name){player.sendMessage(Text.translatable("message.buildcraftsilicon.gate_copier."+name),true);}
    public ActionResult clear(ItemStack stack,World world,PlayerEntity player){
        if(!hasCopiedGateData(stack) || player.isSpectator())return ActionResult.PASS;
        if(!world.isClient){
            var nbt=stack.getNbt();nbt.remove(NBT_DATA);if(nbt.isEmpty())stack.setNbt(null);changed(player);message(player,"cleared");
        }
        return ActionResult.success(world.isClient);
    }
    /** Called from the exact gate attachment selected by the native pipe ray trace. */
    public ActionResult interact(ItemStack stack,PlayerEntity player,PluggableGate gate){
        if(!gate.holder.canPlayerInteract(player) || gate.holder.getPluggable(gate.side)!=gate)return ActionResult.FAIL;
        var world=gate.holder.getPipeWorld();
        if(player.isSneaking())return clear(stack,world,player);
        if(world.isClient)return ActionResult.SUCCESS;
        var data=getCopiedGateData(stack);
        if(data==null){
            setCopiedGateData(stack,gate.logic.writeConfigData());changed(player);message(player,"copied");
        }else{
            var result=GateConfigCopier.paste(gate.logic,data);
            if(!result.applied()){message(player,"invalid");return ActionResult.FAIL;}
            message(player,"pasted");
            for(var warning:GateConfigCopier.Warning.values())if(result.warnings().contains(warning))
                player.sendMessage(Text.translatable("message.buildcraftsilicon.gate_copier.warning."+warning.name().toLowerCase(java.util.Locale.ROOT)).formatted(Formatting.GOLD),false);
        }
        return ActionResult.CONSUME;
    }
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
        var stack=player.getStackInHand(hand);
        return new TypedActionResult<>(player.isSneaking()?clear(stack,world,player):ActionResult.PASS,stack);
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context){
        var player=context.getPlayer();if(player==null)return ActionResult.PASS;
        if(player.isSneaking())return clear(context.getStack(),context.getWorld(),player);
        if(!(context.getWorld().getBlockEntity(context.getBlockPos()) instanceof TilePipeHolder tile))return ActionResult.PASS;
        if(!tile.canPlayerInteract(player))return ActionResult.FAIL;
        var trace=BCTransportBlocks.pipeHolder.rayTrace(context.getWorld(),context.getBlockPos(),player);
        if(trace!=null && trace.subHit>6 && trace.subHit<=12 && tile.getPluggable(BlockPipeHolder.getPartSideHit(trace)) instanceof PluggableGate gate)
            return interact(context.getStack(),player,gate);
        if(!context.getWorld().isClient)message(player,"no_gate");return ActionResult.FAIL;
    }
    @Override public void appendTooltip(ItemStack stack,World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){
        lines.add(Text.translatable("item.buildcraftsilicon.gate_copier."+ (hasCopiedGateData(stack)?"loaded":"empty")));
        lines.add(Text.translatable("item.buildcraftsilicon.gate_copier.tip"));
        lines.add(Text.literal("Remake by FelixMantra").formatted(Formatting.GOLD));
    }
}