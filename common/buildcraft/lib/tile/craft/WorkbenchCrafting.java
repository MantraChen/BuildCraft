/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native transactional crafting. Remake by FelixMantra.
 */
package buildcraft.lib.tile.craft;

import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/** The ghost pattern selects exact item/NBT variants; real ingredients and output move atomically. */
public final class WorkbenchCrafting {
    private final TileBC_Neptune tile;
    private final ItemHandlerSimple blueprint,materials,result;
    private final Storage<ItemVariant> materialStorage,resultStorage;
    private final int width,height;
    private boolean dirty=true;
    private long lastCheck,revision;
    private CraftingRecipe recipe;
    private ItemStack assumedResult=ItemStack.EMPTY;

    public WorkbenchCrafting(int width,int height,TileBC_Neptune tile,ItemHandlerSimple blueprint,
            ItemHandlerSimple materials,ItemHandlerSimple result) {
        if(width<1 || height<1 || width*height>9 || blueprint.getSlots()!=width*height)
            throw new IllegalArgumentException("Invalid automatic crafting grid");
        this.width=width;this.height=height;this.tile=tile;this.blueprint=blueprint;this.materials=materials;this.result=result;
        materialStorage=materials.asStorage(true,true);resultStorage=result.asStorage(true,true);
    }

    public static final class Grid extends SimpleInventory implements RecipeInputInventory {
        private final int width,height;
        public Grid(int width,int height) {super(width*height);this.width=width;this.height=height;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
        @Override public List<ItemStack> getInputStacks(){return java.util.stream.IntStream.range(0,size()).mapToObj(this::getStack).toList();}
    }
    private Grid grid() {
        var grid=new Grid(width,height);
        for(int slot=0;slot<blueprint.getSlots();slot++)grid.setStack(slot,blueprint.getStackInSlot(slot).copyWithCount(1));
        return grid;
    }

    /** Recipe identity detects replacement/removal immediately; invalid patterns retry after data-pack additions. */
    public boolean tick() {
        if(tile.getWorld()==null)return false;
        var world=tile.getWorld();var manager=world.getRecipeManager();
        if(!dirty && world.getTime()-lastCheck<20
                && (recipe==null || manager.get(recipe.getId()).orElse(null)==recipe))return false;
        var input=grid();
        var next=manager.getFirstMatch(RecipeType.CRAFTING,input,world).orElse(null);
        var output=next==null?ItemStack.EMPTY:next.craft(input,world.getRegistryManager()).copy();
        boolean changed=dirty || recipe!=next || !ItemStack.areEqual(assumedResult,output);
        recipe=next;assumedResult=output;dirty=false;lastCheck=world.getTime();
        if(changed)revision++;
        return changed;
    }
    public long getRevision(){return revision;}
    public void markBlueprintDirty(){dirty=true;}
    public ItemStack getResult(){tick();return assumedResult.copy();}
    public boolean hasRequirements(){return attempt(false,false);}
    public boolean canCraft(){return attempt(false,true);}
    public boolean craft(){return attempt(true,true);}

    private boolean attempt(boolean commit,boolean outputRequired) {
        if(!tile.isStorageAvailable() || tile.getWorld().isClient)return false;
        tick();
        if(recipe==null || assumedResult.isEmpty())return false;
        Grid input=grid();
        // Dynamic recipes must see matches before craft, and never receive mutable inventory references.
        if(!recipe.matches(input,tile.getWorld()))return false;
        ItemStack product=recipe.craft(input,tile.getWorld().getRegistryManager()).copy();
        if(product.isEmpty())return false;
        var remainder=recipe.getRemainder(input);
        if(remainder.size()!=input.size())return false;
        List<ItemStack> overflow=new ArrayList<>();
        try(var transaction=Transaction.openOuter()) {
            for(int slot=0;slot<input.size();slot++) {
                ItemStack ingredient=input.getStack(slot);
                if(!ingredient.isEmpty() && materialStorage.extract(ItemVariant.of(ingredient),1,transaction)!=1)return false;
            }
            if(outputRequired && resultStorage.insert(ItemVariant.of(product),product.getCount(),transaction)!=product.getCount())return false;
            if(commit) {
                for(ItemStack remaining:remainder)if(!remaining.isEmpty()) {
                    long accepted=materialStorage.insert(ItemVariant.of(remaining),remaining.getCount(),transaction);
                    if(accepted<remaining.getCount())overflow.add(remaining.copyWithCount((int)(remaining.getCount()-accepted)));
                }
                transaction.commit();
            }
        }
        // Only actual committed crafts may export buckets or other recipe remainders.
        if(commit)for(ItemStack stack:overflow)InventoryUtil.addToBestAcceptor(tile.getWorld(),tile.getPos(),null,stack);
        return true;
    }
}
