/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.container;

import java.io.IOException;
import java.util.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.*;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.*;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.factory.tile.TileAutoWorkbenchItems;

/** Native ghost-pattern editing, read-only derived filters and server-approved recipe selection. */
public class ContainerAutoCraftItems extends ContainerBCTile<TileAutoWorkbenchItems> {
    private static final IdAllocator IDS=buildcraft.lib.gui.ContainerBC_Neptune.IDS.makeChild("autoWorkbench");
    public static final int NET_SELECT_RECIPE=IDS.allocId("SELECT_RECIPE");
    public static final int OUTPUT=0,BLUEPRINT_START=1,PREVIEW=28,PLAYER_START=29;
    public final SlotBase[] materialSlots=new SlotBase[9];
    private final SimpleInventory preview=new SimpleInventory(1);
    private int progress;

    public ContainerAutoCraftItems(PlayerEntity player,TileAutoWorkbenchItems tile){this(player,0,tile);}
    public ContainerAutoCraftItems(PlayerEntity player,int syncId,TileAutoWorkbenchItems tile) {
        super(buildcraft.factory.BCFactoryScreens.AUTO_WORKBENCH_ITEMS,player,syncId,tile);
        addSlot(new SlotOutput(tile.invResult,0,124,35));
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)
            addSlot(new SlotPhantom(tile.invBlueprint,col+row*3,30+col*18,17+row*18,false));
        for(int slot=0;slot<9;slot++) {
            addSlot(new SlotPhantom(tile.invMaterialFilter,slot,-1000000,-1000000,false));
            addSlot(materialSlots[slot]=new SlotBase(tile.invMaterials,slot,8+slot*18,84));
        }
        addSlot(new Slot(preview,0,93,27) {
            @Override public boolean canInsert(ItemStack stack){return false;}
            @Override public boolean canTakeItems(PlayerEntity user){return false;}
            @Override public ItemStack takeStack(int amount){return ItemStack.EMPTY;}
        });
        addFullPlayerInventory(115);
        addProperties(new PropertyDelegate() {
            public int size(){return 1;}
            public int get(int index){return tile.getWorld().isClient?progress:tile.getProgressScaled();}
            public void set(int index,int value){progress=Math.max(0,Math.min(10000,value));}
        });
        if(!tile.getWorld().isClient)preview.setStack(0,tile.getRecipePreview());
    }
    @Override public IdAllocator getIdAllocator(){return IDS;}
    public float getProgress(){return (tile.getWorld().isClient?progress:tile.getProgressScaled())/10000f;}
    public static int materialIndex(int slot){return 11+slot*2;}
    private static boolean protectedSlot(int index){return index==PREVIEW || index>=10 && index<PREVIEW && (index&1)==0;}

    @Override public void onSlotClick(int index,int button,SlotActionType type,PlayerEntity user) {
        if(protectedSlot(index))return;
        super.onSlotClick(index,button,type,user);
    }
    @Override public void sendContentUpdates() {
        if(tile!=null && !tile.getWorld().isClient)preview.setStack(0,tile.getRecipePreview());
        super.sendContentUpdates();
    }
    public void selectRecipe(Identifier id) {
        if(!canUse(player) || !(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer))return;
        var found=tile.getWorld().getRecipeManager().get(id).orElse(null);
        if(!(found instanceof CraftingRecipe recipe) || !recipe.fits(3,3) || recipe.isIgnoredInRecipeBook()
                || recipe.getIngredients().isEmpty() || !player.isCreative() && !serverPlayer.getRecipeBook().contains(recipe))return;
        int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
        int height=recipe instanceof ShapedRecipe shaped?shaped.getHeight():3;
        int offsetX=width==1?1:0,offsetY=height==1?1:0;
        var ingredients=recipe.getIngredients();
        List<ItemStack> pattern=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));
        for(int row=0;row<height;row++)for(int col=0;col<width;col++) {
            int index=col+row*width;if(index>=ingredients.size())continue;
            var ingredient=ingredients.get(index);
            ItemStack sample=ItemStack.EMPTY;
            for(var stack:tile.invMaterials.stacks)if(!stack.isEmpty() && ingredient.test(stack)){sample=stack;break;}
            if(sample.isEmpty())for(int slot=0;slot<player.getInventory().size();slot++) {
                var stack=player.getInventory().getStack(slot);
                if(!stack.isEmpty() && ingredient.test(stack)){sample=stack;break;}
            }
            if(sample.isEmpty() && ingredient.getMatchingStacks().length>0)sample=ingredient.getMatchingStacks()[0];
            pattern.set(col+offsetX+(row+offsetY)*3,sample.copyWithCount(1));
        }
        for(int slot=0;slot<9;slot++)tile.invBlueprint.setStackInSlot(slot,pattern.get(slot));
        sendContentUpdates();
    }
    @Override public void readMessage(int id,PacketBufferBC buffer,boolean client,Object context) throws IOException {
        if(client)return;
        if(!canUse(player))return;
        if(id==NET_SELECT_RECIPE) {
            Identifier recipe=buffer.readIdentifier();
            if(buffer.isReadable())throw new IOException("Unexpected recipe-selection payload");
            selectRecipe(recipe);return;
        }
        if(id!=NET_SET_PHANTOM && id!=NET_SET_PHANTOM_MULTI){super.readMessage(id,buffer,false,context);return;}
        int count=id==NET_SET_PHANTOM?1:buffer.readUnsignedByte();
        if(count>9)throw new IOException("Too many blueprint slots");
        Map<Integer,ItemStack> updates=new LinkedHashMap<>();
        for(int i=0;i<count;i++) {
            int slot=buffer.readVarInt();ItemStack stack=buffer.readItemStack();
            if(slot<BLUEPRINT_START || slot>=BLUEPRINT_START+9 || updates.containsKey(slot))
                throw new IOException("Invalid blueprint slot");
            updates.put(slot,stack.copyWithCount(1));
        }
        if(buffer.isReadable())throw new IOException("Unexpected blueprint payload");
        // Validate the whole packet before changing any phantom state.
        updates.forEach((slot,stack)->tile.invBlueprint.setStackInSlot(slot-BLUEPRINT_START,stack));
        sendContentUpdates();
    }
}
