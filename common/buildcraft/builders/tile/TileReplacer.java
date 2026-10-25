/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.tile;

import java.util.Date;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.item.*;
import buildcraft.builders.snapshot.*;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PropertyDelegate;

/** Replaces palette entries only after the new content-addressed blueprint is durable. */
public class TileReplacer extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity {
    public final ItemHandlerSimple invSnapshot=itemManager.addInvHandler("snapshot",1,
        (slot,stack)->stack.getItem() instanceof ItemSnapshot && ItemSnapshot.EnumItemSnapshotType.getFromStack(stack)==ItemSnapshot.EnumItemSnapshotType.BLUEPRINT_USED,
        ItemHandlerManager.EnumAccess.NONE);
    public final ItemHandlerSimple invSchematicFrom=itemManager.addInvHandler("schematicFrom",1,
        (slot,stack)->ItemSchematicSingle.isUsed(stack),ItemHandlerManager.EnumAccess.NONE);
    public final ItemHandlerSimple invSchematicTo=itemManager.addInvHandler("schematicTo",1,
        (slot,stack)->ItemSchematicSingle.isUsed(stack),ItemHandlerManager.EnumAccess.NONE);
    // 0 waiting, 1 replaced, 2 no match, 3 invalid input, 4 missing file, 5 save failed.
    private int status,replaced,retryTicks;
    public final PropertyDelegate properties=new PropertyDelegate(){
        public int size(){return 3;}
        public int get(int index){return switch(index){case 0->status;case 1->replaced&65535;case 2->replaced>>>16;default->0;};}
        public void set(int index,int value){switch(index){case 0->status=Math.max(0,Math.min(5,value));case 1->replaced=(replaced&0xffff0000)|(value&65535);case 2->replaced=(replaced&65535)|((value&65535)<<16);}}
    };
    public TileReplacer(net.minecraft.block.entity.BlockEntityType<?> type,net.minecraft.util.math.BlockPos pos,net.minecraft.block.BlockState state){
        super(type,pos,state);invSnapshot.setLimitedInsertor(1);invSchematicFrom.setLimitedInsertor(1);invSchematicTo.setLimitedInsertor(1);
    }
    public int getStatus(){return status;}
    public int getReplaced(){return replaced;}
    @Override protected void onSlotChange(Object handler,int slot,ItemStack before,ItemStack after){
        super.onSlotChange(handler,slot,before,after);status=0;replaced=0;retryTicks=0;
    }
    private void result(int next,int count){status=next;replaced=count;markDirty();}
    @Override public void tick(){
        if(!isStorageAvailable() || world.isClient || Transaction.getCurrentUnsafe()!=null)return;
        if(retryTicks>0){retryTicks--;return;}
        var input=invSnapshot.getStackInSlot(0);var source=invSchematicFrom.getStackInSlot(0);var target=invSchematicTo.getStackInSlot(0);
        if(input.isEmpty()||source.isEmpty()||target.isEmpty())return;
        if(status==2 || status==3)return;
        retryTicks=20;
        if(input.getCount()!=1 || source.getCount()!=1 || target.getCount()!=1
            || ItemSnapshot.EnumItemSnapshotType.getFromStack(input)!=ItemSnapshot.EnumItemSnapshotType.BLUEPRINT_USED){result(3,0);return;}
        var header=BCBuildersItems.snapshot.getHeader(input);
        if(header==null){result(3,0);return;}
        var snapshot=GlobalSavedDataSnapshots.get(world).getSnapshot(header.key);
        if(snapshot==null){result(4,0);return;}
        if(!(snapshot instanceof Blueprint blueprint)){result(3,0);return;}
        try{
            var from=ItemSchematicSingle.getSchematic(source);var to=ItemSchematicSingle.getSchematic(target);
            if(from==null || to==null || from.isAir() || to.isAir()){result(3,0);return;}
            int count=0;
            boolean[] matching=new boolean[blueprint.palette.size()];
            for(int i=0;i<matching.length;i++)matching[i]=blueprint.palette.get(i).equals(from);
            for(int index:blueprint.data)if(matching[index])count++;
            if(count==0 || from.equals(to)){result(2,0);return;}
            var copy=blueprint.copy();copy.replace(from,to);copy.computeKey();
            if(!GlobalSavedDataSnapshots.get(world).addSnapshot(copy)){result(5,0);return;}
            invSnapshot.setStackInSlot(0,BCBuildersItems.snapshot.getUsed(EnumSnapshotType.BLUEPRINT,
                new Snapshot.Header(copy.key,getOwner().getId(),new Date(),header.name)));
            invSchematicFrom.setStackInSlot(0,ItemStack.EMPTY);invSchematicTo.setStackInSlot(0,ItemStack.EMPTY);
            result(1,count);
        }catch(InvalidInputDataException invalid){result(3,0);}
    }
    @Override public void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putInt("replacementStatus",status);nbt.putInt("replacedBlocks",replaced);}
    @Override public void readFromNBT(NbtCompound nbt){super.readFromNBT(nbt);status=nbt.getInt("replacementStatus")==1?1:0;replaced=Math.max(0,Math.min(BuildingExecutor.MAX_VOLUME,nbt.getInt("replacedBlocks")));retryTicks=0;}
}
