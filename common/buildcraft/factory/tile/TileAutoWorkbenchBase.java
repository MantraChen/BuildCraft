/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native automatic crafting. Remake by FelixMantra.
 */
package buildcraft.factory.tile;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.*;
import buildcraft.api.tiles.*;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.craft.*;
import buildcraft.lib.tile.item.*;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;

/** Passive ten-second crafting accelerated by up to 40 MJ of external power. */
public abstract class TileAutoWorkbenchBase extends TileBC_Neptune
        implements buildcraft.lib.tile.TickingBlockEntity,IHasWork,IMjRedstoneReceiver,IAutoCraft,IDebuggable {
    public static final IdAllocator IDS=TileBC_Neptune.IDS.makeChild("autoWorkbench");
    public static final long POWER_REQUIRED=40*MjAPI.MJ;
    private static final long POWER_GEN_PASSIVE=MjAPI.MJ/5,POWER_LOST=2*MjAPI.MJ;
    public final ItemHandlerSimple invBlueprint,invMaterialFilter,invResult;
    public final ItemHandlerFiltered invMaterials;
    private final WorkbenchCrafting crafting;
    private long powerStored;
    private long recipeRevision=-1;
    private boolean restoringPayment;

    public TileAutoWorkbenchBase(BlockEntityType<?> type,BlockPos pos,BlockState state,int width,int height) {
        super(type,pos,state);
        int slots=width*height;
        invBlueprint=itemManager.addInvHandler("blueprint",slots,EnumAccess.PHANTOM);
        invBlueprint.setLimitedInsertor(1);
        invMaterialFilter=itemManager.addInvHandler("material_filter",slots,EnumAccess.PHANTOM);
        invMaterialFilter.setLimitedInsertor(1);
        invMaterials=new ItemHandlerFiltered(invMaterialFilter,true);
        invMaterials.setCallback(itemManager.callback);
        itemManager.addInvHandler("materials",invMaterials,EnumAccess.INSERT,EnumPipePart.VALUES);
        invResult=itemManager.addInvHandler("result",1,EnumAccess.EXTRACT,EnumPipePart.VALUES);
        crafting=new WorkbenchCrafting(width,height,this,invBlueprint,invMaterials,invResult);
        caps.addProvider(new MjCapabilityHelper(this));
    }

    @Override protected void onSlotChange(Object handler,int slot,ItemStack before,ItemStack after) {
        super.onSlotChange(handler,slot,before,after);
        if(crafting!=null && handler==invBlueprint && !ItemStack.areEqual(before,after)) {
            powerStored=0;restoringPayment=false;crafting.markBlueprintDirty();
            if(world!=null && !world.isClient)refreshRecipe();
        }
    }
    private void refreshRecipe() {
        crafting.tick();
        if(world!=null && recipeRevision!=crafting.getRevision()) {
            recipeRevision=crafting.getRevision();
            if(!restoringPayment)powerStored=0;
            restoringPayment=false;createFilters();
        }
    }
    public static <T extends TileAutoWorkbenchBase> BlockEntityTicker<T> ticker() {
        return (world,pos,state,tile)->tile.tick();
    }
    @Override public void tick() {
        if(!isStorageAvailable() || world.isClient)return;
        long before=powerStored;
        refreshRecipe();
        if(crafting.canCraft()) {
            powerStored=Math.min(POWER_REQUIRED,powerStored+POWER_GEN_PASSIVE);
            if(powerStored>=POWER_REQUIRED && crafting.craft()) {
                powerStored=0;
                if(getOwner()!=null)AdvancementUtil.unlockAdvancement((ServerWorld)world,getOwner().getId(),
                    new Identifier("buildcraftfactory:lazy_crafting"));
            }
        } else powerStored=Math.max(0,powerStored-POWER_LOST);
        if(before!=powerStored)markChunkDirty();
    }
    public long getStoredPower(){return powerStored;}
    public int getProgressScaled(){return (int)(powerStored*10000/POWER_REQUIRED);}
    public ItemStack getRecipePreview(){if(world!=null && !world.isClient)refreshRecipe();return crafting.getResult();}
    @Override public ItemStack getCurrentRecipeOutput(){return getRecipePreview();}
    @Override public ItemHandlerSimple getInvBlueprint(){return invBlueprint;}
    @Override public boolean hasWork(){return isStorageAvailable() && !world.isClient && crafting.canCraft();}
    @Override public IdAllocator getIdAllocator(){return IDS;}

    /** Allocate buffer slots in proportion to exact recipe demand and each ingredient's stack limit. */
    private void createFilters() {
        int size=invBlueprint.getSlots();
        List<ItemStack> unique=new ArrayList<>();int[] required=new int[size],allocation=new int[size];
        if(!crafting.getResult().isEmpty())for(int slot=0;slot<size;slot++) {
            var stack=invBlueprint.getStackInSlot(slot);if(stack.isEmpty())continue;
            int index=0;while(index<unique.size() && !ItemStack.canCombine(stack,unique.get(index)))index++;
            if(index==unique.size()){unique.add(stack.copyWithCount(1));allocation[index]=1;}
            required[index]++;
        }
        if(!unique.isEmpty())for(int left=size-unique.size();left>0;left--) {
            int smallest=0;
            for(int i=1;i<unique.size();i++)
                if(unique.get(i).getMaxCount()*allocation[i]/required[i]<unique.get(smallest).getMaxCount()*allocation[smallest]/required[smallest])smallest=i;
            allocation[smallest]++;
        }
        int slot=0;
        for(int i=0;i<unique.size();i++)for(int count=0;count<allocation[i];count++)
            invMaterialFilter.setStackInSlot(slot++,unique.get(i).copy());
        while(slot<size)invMaterialFilter.setStackInSlot(slot++,ItemStack.EMPTY);
    }

    @Override public boolean canConnect(IMjConnector other){return true;}
    @Override public boolean canReceive(){return isStorageAvailable() && !world.isClient;}
    @Override public long getPowerRequested(){return canReceive()?Math.max(0,POWER_REQUIRED-powerStored):0;}
    @Override public long receivePower(long amount,boolean simulate) {
        if(amount<0 || !canReceive())return amount;
        long accepted=Math.min(amount,getPowerRequested());
        if(!simulate && accepted>0){powerStored+=accepted;markChunkDirty();}
        return amount-accepted;
    }
    @Override public void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putLong("powerStored",powerStored);}
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        crafting.markBlueprintDirty();
        powerStored=Math.max(0,Math.min(POWER_REQUIRED,nbt.getLong("powerStored")));
        // Chunk loading reads NBT before attaching the world. The first recipe refresh must retain paid work.
        restoringPayment=true;recipeRevision=-1;
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side) {
        left.add("crafting progress = "+powerStored+" / "+POWER_REQUIRED+" micro-MJ");
        left.add("recipe = "+getRecipePreview());
    }
}
