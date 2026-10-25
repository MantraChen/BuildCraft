/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.core.item;
import java.util.*;
import buildcraft.api.blocks.CustomPaintHelper;
import buildcraft.lib.item.ItemBC_Neptune;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

public class ItemPaintbrush_BC8 extends ItemBC_Neptune {
    public static final int MAX_USES=64;
    public ItemPaintbrush_BC8(Settings settings,String id){super(settings.maxCount(1),id);}
    public static DyeColor getColour(ItemStack stack){
        int meta=stack.getDamage();return stack.getItem() instanceof ItemPaintbrush_BC8 && meta>0&&meta<=16&&usesLeft(stack)>0?DyeColor.byId(meta-1):null;
    }
    public static int usesLeft(ItemStack stack){int meta=stack.getDamage();return meta>0&&meta<=16?Math.max(0,MAX_USES-Math.max(0,stack.hasNbt()?stack.getNbt().getInt("damage"):0)):0;}
    public static ItemStack coloured(ItemStack stack,DyeColor colour){var result=stack.copyWithCount(1);result.removeSubNbt("damage");result.setDamage(colour==null?0:colour.getId()+1);return result;}
    public static void registerPainting(){CustomPaintHelper.INSTANCE.registerHandlerForAll((world,pos,state,hit,side,colour)->{
        if(state.getBlock() instanceof buildcraft.transport.block.BlockPipeHolder pipe){
            var holder=buildcraft.transport.block.BlockPipeHolder.getPipe(world,pos);
            if(holder==null||holder.getPipe()==null||!holder.getPipe().definition.canBeColoured||holder.getPipe().getColour()==colour)return ActionResult.FAIL;
            return world.isClient?ActionResult.SUCCESS:pipe.attemptPaint(world,pos,state,hit,side,colour);
        }
        if(colour==null)return ActionResult.PASS;
        var id=Registries.BLOCK.getId(state.getBlock());if(!id.getNamespace().equals("minecraft"))return ActionResult.PASS;
        String suffix=null;for(var kind:List.of("wool","carpet","terracotta","stained_glass","stained_glass_pane","concrete","concrete_powder")){
            for(var existing:DyeColor.values())if(id.getPath().equals(existing.getName()+"_"+kind)){suffix=kind;break;}
            if(suffix!=null)break;
        }
        if(id.getPath().equals("glass"))suffix="stained_glass";else if(id.getPath().equals("glass_pane"))suffix="stained_glass_pane";else if(id.getPath().equals("terracotta"))suffix="terracotta";
        if(suffix==null)return ActionResult.PASS;Block target=Registries.BLOCK.get(new Identifier("minecraft",colour.getName()+"_"+suffix));
        if(target==state.getBlock())return ActionResult.FAIL;var next=target.getStateWithProperties(state);
        return world.isClient||world.setBlockState(pos,next,Block.NOTIFY_ALL)?ActionResult.SUCCESS:ActionResult.FAIL;
    });}
    @Override public ActionResult useOnBlock(ItemUsageContext c){var result=useFirst(c.getPlayer(),c.getWorld(),c.getHand(),new BlockHitResult(c.getHitPos(),c.getSide(),c.getBlockPos(),false));return result==ActionResult.PASS?ActionResult.FAIL:result;}
    public static ActionResult useFirst(PlayerEntity player,World world,Hand hand,BlockHitResult hit){
        if(player==null||!(player.getStackInHand(hand).getItem() instanceof ItemPaintbrush_BC8))return ActionResult.PASS;
        var stack=player.getStackInHand(hand);var at=hit.getBlockPos();
        if(player.isSpectator()||!player.canModifyBlocks()||!world.isChunkLoaded(at)||!world.canPlayerModifyAt(player,at)||Transaction.getCurrentUnsafe()!=null||player.getItemCooldownManager().isCoolingDown(stack.getItem()))return ActionResult.FAIL;
        var colour=getColour(stack);var original=stack.copy();var result=CustomPaintHelper.INSTANCE.attemptPaintBlock(world,at,world.getBlockState(at),hit.getPos(),hit.getSide(),colour);
        if(!result.isAccepted())return ActionResult.PASS;
        if(!world.isClient&&ItemStack.areEqual(original,player.getStackInHand(hand))){
            if(colour!=null){int left=usesLeft(stack)-1;if(left<=0){stack.setDamage(0);stack.removeSubNbt("damage");}else stack.getOrCreateNbt().putInt("damage",MAX_USES-left);}
            player.getInventory().markDirty();world.playSound(null,at,net.minecraft.sound.SoundEvents.ITEM_DYE_USE,net.minecraft.sound.SoundCategory.BLOCKS,.6f,1f);
        }
        return ActionResult.success(world.isClient);
    }
    @Override public boolean isItemBarVisible(ItemStack stack){return getColour(stack)!=null&&usesLeft(stack)<MAX_USES;}
    @Override public int getItemBarStep(ItemStack stack){return Math.round(13f*usesLeft(stack)/MAX_USES);}
    @Override public int getItemBarColor(ItemStack stack){var color=getColour(stack);return color==null?0xffffff:color.getSignColor();}
    @Override public Text getName(ItemStack stack){var color=getColour(stack);return color==null?super.getName(stack):Text.translatable("item.buildcraftcore.paintbrush.coloured",Text.translatable("color.minecraft."+color.getName()));}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext c){if(getColour(stack)!=null)lines.add(Text.translatable("item.buildcraftcore.paintbrush.uses",usesLeft(stack),MAX_USES));lines.add(Text.translatable("item.buildcraftcore.paintbrush.help"));lines.add(Text.literal("Remake by FelixMantra"));}
}
