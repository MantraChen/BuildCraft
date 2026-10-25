/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.item;

import java.util.List;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.*;
import buildcraft.builders.snapshot.*;
import buildcraft.lib.item.ItemBC_Neptune;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** A reusable native single-block capture. Placement pays the saved contents as exact items. */
public class ItemSchematicSingle extends ItemBC_Neptune {
    public static final int DAMAGE_CLEAN=0, DAMAGE_USED=1;
    public static final String NBT_KEY="schematic";
    public ItemSchematicSingle(Settings settings,String id){super(settings.maxCount(16),id);}
    public static boolean isUsed(ItemStack stack){return stack.getItem() instanceof ItemSchematicSingle &&
        (stack.getDamage()==DAMAGE_USED || stack.getNbt()!=null && stack.getNbt().contains(NBT_KEY,NbtElement.COMPOUND_TYPE));}
    public static ISchematicBlock getSchematic(ItemStack stack)throws InvalidInputDataException{
        if(!(stack.getItem() instanceof ItemSchematicSingle) || !isUsed(stack))return null;
        if(stack.getNbt()==null || !stack.getNbt().contains(NBT_KEY,NbtElement.COMPOUND_TYPE))throw new InvalidInputDataException("Missing schematic data");
        return SchematicBlockManager.readFromNBT(stack.getNbt().getCompound(NBT_KEY));
    }
    public static ISchematicBlock getSchematicSafe(ItemStack stack){try{return getSchematic(stack);}catch(InvalidInputDataException invalid){return null;}}
    private static void clear(ItemStack stack){stack.removeSubNbt(NBT_KEY);stack.removeSubNbt("Damage");}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
        var stack=player.getStackInHand(hand);
        if(!player.isSneaking() || player.isSpectator())return TypedActionResult.pass(stack);
        if(!world.isClient)clear(stack);
        return TypedActionResult.success(stack,world.isClient);
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context){
        return useFirst(context.getPlayer(),context.getWorld(),context.getHand(),
            new BlockHitResult(context.getHitPos(),context.getSide(),context.getBlockPos(),false));
    }
    /** Called after Fabric's interaction event, before a chest or other block opens its screen. */
    public static ActionResult useFirst(PlayerEntity player,World world,Hand hand,BlockHitResult hit){
        if(player==null || !(player.getStackInHand(hand).getItem() instanceof ItemSchematicSingle))return ActionResult.PASS;
        var stack=player.getStackInHand(hand);var at=hit.getBlockPos();
        if(player.isSpectator() || !player.canModifyBlocks() || player.getItemCooldownManager().isCoolingDown(stack.getItem())
            || !world.isChunkLoaded(at) || !world.canPlayerModifyAt(player,at))return ActionResult.FAIL;
        if(world.isClient)return ActionResult.SUCCESS;
        if(Transaction.getCurrentUnsafe()!=null)return ActionResult.FAIL;
        if(player.isSneaking()){clear(stack);player.getInventory().markDirty();return ActionResult.SUCCESS;}
        try{
            if(!isUsed(stack)){
                var state=world.getBlockState(at);
                var schematic=SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(world,at,at,state,state.getBlock()));
                if(schematic.isAir())return ActionResult.FAIL;
                var captured=stack.copyWithCount(1);captured.getOrCreateNbt().put(NBT_KEY,SchematicBlockManager.writeToNBT(schematic));captured.removeSubNbt("Damage");
                stack.decrement(1);
                if(stack.isEmpty())player.setStackInHand(hand,captured);else player.getInventory().offerOrDrop(captured);
                player.getInventory().markDirty();return ActionResult.SUCCESS;
            }
            var schematic=getSchematic(stack);if(schematic==null || schematic.isAir())return ActionResult.FAIL;
            var target=world.getBlockState(at).isReplaceable()?at:at.offset(hit.getSide());
            if(!world.isInBuildLimit(target)||!world.getWorldBorder().contains(target)||!world.isChunkLoaded(target)
                ||!world.canPlayerModifyAt(player,target))return ActionResult.FAIL;
            var original=stack.copy();
            if(!target.equals(at) && UseBlockCallback.EVENT.invoker().interact(player,world,hand,
                new BlockHitResult(Vec3d.ofCenter(target),hit.getSide(),target,false))!=ActionResult.PASS)return ActionResult.FAIL;
            if(!ItemStack.areEqual(original,player.getStackInHand(hand)) || !world.isChunkLoaded(target)
                || !world.canPlayerModifyAt(player,target))return ActionResult.FAIL;
            if(!schematic.computeRequiredFluids().isEmpty()){player.sendMessage(Text.translatable("item.buildcraftbuilders.schematic.fluids"),true);return ActionResult.FAIL;}
            var previous=world.getBlockState(target);
            if(!previous.isReplaceable() || world.getBlockEntity(target)!=null || schematic.isBuilt(world,target))return ActionResult.FAIL;
            if(schematic instanceof SchematicBlockDefault block && !world.canPlace(block.placementState(),target,ShapeContext.absent()))return ActionResult.FAIL;
            boolean built=BlueprintMaterials.place(InventoryStorage.of(player.getInventory(),null),Storage.empty(),
                schematic.computeRequiredItems(),List.of(),()->{
                    boolean cleared=!previous.isAir();
                    if(cleared && !world.setBlockState(target,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS))return false;
                    boolean placed=false;
                    try{placed=schematic.build(world,target);return placed;}
                    finally{if(!placed && cleared)world.setBlockState(target,previous,Block.NOTIFY_ALL);}
                });
            if(!built){player.sendMessage(Text.translatable("item.buildcraftbuilders.schematic.missing"),true);return ActionResult.FAIL;}
            var sound=world.getBlockState(target).getSoundGroup();world.playSound(null,target,sound.getPlaceSound(),net.minecraft.sound.SoundCategory.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8f);
            player.getInventory().markDirty();return ActionResult.SUCCESS;
        }catch(InvalidInputDataException invalid){player.sendMessage(Text.translatable("item.buildcraftbuilders.schematic.invalid"),true);return ActionResult.FAIL;}
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> tooltip,TooltipContext context){
        var schematic=getSchematicSafe(stack);
        tooltip.add(Text.translatable("item.buildcraftbuilders.schematic."+(isUsed(stack)?schematic==null?"invalid":"used":"blank")));
        if(schematic!=null)for(var item:schematic.computeRequiredItems().stream().limit(8).toList())tooltip.add(Text.literal(item.getCount()+" × ").append(item.getName()));
        tooltip.add(Text.translatable("item.buildcraftbuilders.schematic.clear"));tooltip.add(Text.literal("Remake by FelixMantra"));
    }
}
