/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native laser-powered crafting. Remake by FelixMantra.
 */
package buildcraft.silicon.tile;

import java.io.IOException;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.craft.*;
import buildcraft.lib.tile.item.*;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;

/** Nine ghost inputs, fifteen material slots and nine outputs; exactly 500 MJ per craft. */
public final class TileAdvancedCraftingTable extends TileLaserTableBase implements IAutoCraft {
    public static final IdAllocator IDS=TileBC_Neptune.IDS.makeChild("advancedCraftingTable");
    public static final long POWER_REQUIRED=500*MjAPI.MJ;
    public final ItemHandlerSimple invBlueprint,invMaterials,invResults;
    private final WorkbenchCrafting crafting;
    public ItemStack resultClient=ItemStack.EMPTY;
    private long previewRevision=-1;

    public TileAdvancedCraftingTable(BlockEntityType<?> type,BlockPos pos,BlockState state) {
        super(type,pos,state);
        invBlueprint=itemManager.addInvHandler("blueprint",9,EnumAccess.PHANTOM);
        invBlueprint.setLimitedInsertor(1);
        invMaterials=itemManager.addInvHandler("materials",15,EnumAccess.INSERT,EnumPipePart.VALUES);
        invResults=itemManager.addInvHandler("result",9,EnumAccess.EXTRACT,EnumPipePart.VALUES);
        crafting=new WorkbenchCrafting(3,3,this,invBlueprint,invMaterials,invResults);
    }
    @Override public IdAllocator getIdAllocator(){return IDS;}
    @Override protected void onSlotChange(Object handler,int slot,ItemStack before,ItemStack after) {
        super.onSlotChange(handler,slot,before,after);
        if(crafting!=null && handler==invBlueprint && !ItemStack.areEqual(before,after))crafting.markBlueprintDirty();
    }
    @Override public long getTarget() {
        if(world==null)return 0;
        return world.isClient || crafting.canCraft()?POWER_REQUIRED:0;
    }
    @Override public void tick() {
        super.tick();
        if(!isStorageAvailable() || world.isClient)return;
        crafting.tick();
        if(power>=POWER_REQUIRED && crafting.craft()){power-=POWER_REQUIRED;markDirty();}
        if(previewRevision!=crafting.getRevision()) {
            previewRevision=crafting.getRevision();sendNetworkGuiUpdate(NET_GUI_DATA);
        }
    }
    public ItemStack getRecipePreview(){return world!=null && world.isClient?resultClient.copy():crafting.getResult();}
    @Override public ItemStack getCurrentRecipeOutput(){return getRecipePreview();}
    @Override public ItemHandlerSimple getInvBlueprint(){return invBlueprint;}
    @Override public boolean onActivated(PlayerEntity player,Hand hand,Direction face,float x,float y,float z) {
        if(!canInteractWith(player) || player.getStackInHand(hand).getItem() instanceof buildcraft.api.tools.IToolWrench)return false;
        if(!world.isClient)buildcraft.silicon.BCSiliconScreens.openAdvanced(player,this);
        return true;
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);power=Math.min(POWER_REQUIRED,power);crafting.markBlueprintDirty();previewRevision=-1;
    }
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side) {
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && id==NET_GUI_DATA)buffer.writeItemStack(getRecipePreview());
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object context) throws IOException {
        super.readPayload(id,buffer,side,context);
        if(side==NetSide.CLIENT && id==NET_GUI_DATA)resultClient=buffer.readItemStack();
    }
}
