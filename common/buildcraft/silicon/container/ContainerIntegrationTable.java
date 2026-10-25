/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.container;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.*;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.*;
import buildcraft.silicon.tile.TileIntegrationTable;

/** Nine material slots, protected preview and one real output. */
public final class ContainerIntegrationTable extends ContainerBCTile<TileIntegrationTable> {
    public static final int CENTER=4,PREVIEW=9,OUTPUT=10,PLAYER_START=11;
    private final SimpleInventory preview=new SimpleInventory(1);
    public ContainerIntegrationTable(PlayerEntity player,int syncId,TileIntegrationTable tile) {
        super(buildcraft.silicon.BCSiliconScreens.INTEGRATION,player,syncId,tile);
        int index=0;
        for(int row=0;row<3;row++)for(int col=0;col<3;col++) {
            boolean center=row==1 && col==1;
            addSlot(new SlotBase(center?tile.invTarget:tile.invToIntegrate,center?0:index++,19+col*25,24+row*25));
        }
        addSlot(new Slot(preview,0,101,36) {
            @Override public boolean canInsert(ItemStack stack){return false;}
            @Override public boolean canTakeItems(PlayerEntity player){return false;}
            @Override public ItemStack takeStack(int count){return ItemStack.EMPTY;}
        });
        addSlot(new SlotOutput(tile.invResult,0,138,49));addFullPlayerInventory(109);
        if(!player.getWorld().isClient)preview.setStack(0,tile.getRecipePreview());
    }
    @Override public void sendContentUpdates() {
        if(tile!=null && !player.getWorld().isClient)preview.setStack(0,tile.getRecipePreview());
        super.sendContentUpdates();
    }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity user) {
        if(slot!=PREVIEW)super.onSlotClick(slot,button,action,user);
    }
    @Override public ItemStack quickMove(PlayerEntity user,int index) {
        if(index<PLAYER_START)return super.quickMove(user,index);
        if(!canUse(user) || index>=slots.size())return ItemStack.EMPTY;
        var source=getSlot(index);if(!source.hasStack() || !source.canTakeItems(user))return ItemStack.EMPTY;
        var stack=source.getStack();var original=stack.copy();
        boolean center=tile.acceptsCenter(stack);
        for(int pass=0;pass<2 && !stack.isEmpty();pass++)for(int slot=0;slot<9 && !stack.isEmpty();slot++) {
            if((slot==CENTER)!=center)continue;
            var target=getSlot(slot);if(target.hasStack()==(pass==0))target.insertStack(stack);
        }
        int moved=original.getCount()-stack.getCount();if(moved==0)return ItemStack.EMPTY;
        if(stack.isEmpty())source.setStack(ItemStack.EMPTY);else source.markDirty();
        source.onTakeItem(user,original.copyWithCount(moved));return original;
    }
}
