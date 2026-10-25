/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.tile;
import java.util.LinkedHashSet;
import java.util.Set;
import buildcraft.api.core.EnumPipePart;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;

/** The machine owns media; each open player's screen owns their local collection and transfer. */
public class TileElectronicLibrary extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity {
    public final ItemHandlerSimple invDownIn=itemManager.addInvHandler("downIn",1,
        (slot,stack)->stack.getItem() instanceof ItemSnapshot && ItemSnapshot.EnumItemSnapshotType.getFromStack(stack).used,
        ItemHandlerManager.EnumAccess.INSERT,EnumPipePart.VALUES);
    public final ItemHandlerSimple invDownOut=itemManager.addInvHandler("downOut",1,ItemHandlerManager.EnumAccess.EXTRACT,EnumPipePart.VALUES);
    public final ItemHandlerSimple invUpIn=itemManager.addInvHandler("upIn",1,
        (slot,stack)->stack.getItem() instanceof ItemSnapshot,ItemHandlerManager.EnumAccess.INSERT,EnumPipePart.VALUES);
    public final ItemHandlerSimple invUpOut=itemManager.addInvHandler("upOut",1,ItemHandlerManager.EnumAccess.EXTRACT,EnumPipePart.VALUES);
    private final Set<ContainerElectronicLibrary> viewers=new LinkedHashSet<>();
    private ContainerElectronicLibrary active;
    private long inventoryEpoch;
    public TileElectronicLibrary(net.minecraft.block.entity.BlockEntityType<?> type,net.minecraft.util.math.BlockPos pos,net.minecraft.block.BlockState state){
        super(type,pos,state);
        for(var inventory:new ItemHandlerSimple[]{invDownIn,invDownOut,invUpIn,invUpOut})inventory.setLimitedInsertor(1);
    }
    public void opened(ContainerElectronicLibrary viewer){viewers.add(viewer);}
    public void closed(ContainerElectronicLibrary viewer){viewers.remove(viewer);release(viewer);}
    public boolean claim(ContainerElectronicLibrary viewer){if(active!=null && active!=viewer)return false;active=viewer;return true;}
    public boolean owns(ContainerElectronicLibrary viewer){return active==viewer;}
    public void release(ContainerElectronicLibrary viewer){if(active==viewer)active=null;}
    public long inventoryEpoch(){return inventoryEpoch;}
    @Override protected void onSlotChange(Object handler,int slot,ItemStack before,ItemStack after){
        super.onSlotChange(handler,slot,before,after);inventoryEpoch++;
    }
    @Override public void tick(){
        if(world==null || world.isClient || Transaction.getCurrentUnsafe()!=null)return;
        for(var viewer:java.util.List.copyOf(viewers)){
            if(!isStorageAvailable() || !viewer.isOpen()){viewer.cancel();closed(viewer);}
            else viewer.serverTick();
        }
    }
}
