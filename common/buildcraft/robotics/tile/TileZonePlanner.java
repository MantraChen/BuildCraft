/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.robotics.tile;
import java.util.*;
import buildcraft.api.items.IMapLocation.MapLocationType;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.*;
import buildcraft.lib.tile.*;
import buildcraft.lib.tile.item.*;
import buildcraft.robotics.zone.ZonePlan;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.DyeColor;

public class TileZonePlanner extends TileBC_Neptune implements TickingBlockEntity {
    public final ItemHandlerSimple invPaintbrushes=brushes("paintbrushes",16),invInputPaintbrush=brushes("inputPaintbrush",1),invOutputPaintbrush=brushes("outputPaintbrush",1);
    public final ItemHandlerSimple invInputMapLocation=itemManager.addInvHandler("inputMapLocation",1,(slot,s)->s.getItem() instanceof ItemMapLocation&&MapLocationType.getFromStack(s)==MapLocationType.ZONE,ItemHandlerManager.EnumAccess.NONE);
    public final ItemHandlerSimple invInputResult=itemManager.addInvHandler("inputResult",1,ItemHandlerManager.EnumAccess.NONE);
    public final ItemHandlerSimple invOutputMapLocation=itemManager.addInvHandler("outputMapLocation",1,(slot,s)->s.getItem() instanceof ItemMapLocation,ItemHandlerManager.EnumAccess.NONE);
    public final ItemHandlerSimple invOutputResult=itemManager.addInvHandler("outputResult",1,ItemHandlerManager.EnumAccess.NONE);
    public final ZonePlan[] layers=new ZonePlan[16];
    private final NbtCompound[] unreadable=new NbtCompound[16];
    public final long[] versions=new long[16];
    private int progressInput,progressOutput,status;
    public final PropertyDelegate properties=new PropertyDelegate(){public int size(){return 3;}public int get(int i){return i==0?progressInput:i==1?progressOutput:status;}public void set(int i,int v){if(i==0)progressInput=v;else if(i==1)progressOutput=v;else status=v;}};
    private ItemHandlerSimple brushes(String name,int size){return itemManager.addInvHandler(name,size,(slot,s)->s.getItem() instanceof ItemPaintbrush_BC8,ItemHandlerManager.EnumAccess.NONE);}
    public TileZonePlanner(net.minecraft.block.entity.BlockEntityType<?> type,net.minecraft.util.math.BlockPos pos,net.minecraft.block.BlockState state){
        super(type,pos,state);for(int i=0;i<16;i++)layers[i]=new ZonePlan();
        for(var inv:List.of(invPaintbrushes,invInputPaintbrush,invOutputPaintbrush,invInputMapLocation,invInputResult,invOutputMapLocation,invOutputResult))inv.setLimitedInsertor(1);
    }
    public boolean hasUnreadableLayer(int i){return unreadable[i]!=null;}
    public int progress(boolean output){return output?progressOutput:progressInput;}
    public int status(){return status;}
    public void replaceLayer(int index,ZonePlan plan){layers[index]=new ZonePlan(plan);unreadable[index]=null;versions[index]++;markDirty();}
    @Override protected void onSlotChange(Object handler,int slot,ItemStack before,ItemStack after){
        super.onSlotChange(handler,slot,before,after);if(handler==invInputPaintbrush||handler==invInputMapLocation||handler==invInputResult)progressInput=0;
        if(handler==invOutputPaintbrush||handler==invOutputMapLocation||handler==invOutputResult)progressOutput=0;status=0;
    }
    @Override public void tick(){
        if(!isStorageAvailable()||world.isClient||Transaction.getCurrentUnsafe()!=null)return;status=0;
        var in=invInputMapLocation.getStackInSlot(0);DyeColor inputColour=ItemPaintbrush_BC8.getColour(invInputPaintbrush.getStackInSlot(0));
        if(inputColour!=null&&in.getItem() instanceof ItemMapLocation&&in.getCount()==1&&invInputResult.getStackInSlot(0).isEmpty()){
            var dimension=ItemMapLocation.getDimension(in);var zone=BCCoreItems.mapLocation.getZone(in);
            if(!(zone instanceof ZonePlan)||dimension!=null&&!dimension.equals(world.getRegistryKey().getValue())){progressInput=0;status=1;}
            else if(++progressInput>=200){
                try{var shifted=((ZonePlan)zone).getWithOffset(-pos.getX(),-pos.getZ());var blank=ItemMapLocation.clearedCopy(in);replaceLayer(inputColour.getId(),shifted);invInputMapLocation.setStackInSlot(0,ItemStack.EMPTY);invInputResult.setStackInSlot(0,blank);progressInput=0;}
                catch(ArithmeticException|IllegalArgumentException invalid){status=1;progressInput=0;}
            }
        }else progressInput=0;
        var out=invOutputMapLocation.getStackInSlot(0);DyeColor outputColour=ItemPaintbrush_BC8.getColour(invOutputPaintbrush.getStackInSlot(0));
        if(outputColour!=null&&out.getItem() instanceof ItemMapLocation&&out.getCount()==1&&invOutputResult.getStackInSlot(0).isEmpty()){
            if(unreadable[outputColour.getId()]!=null){progressOutput=0;status=2;}
            else if(++progressOutput>=200){
                try{var result=out.copyWithCount(1);ItemMapLocation.setZone(result,layers[outputColour.getId()].getWithOffset(pos.getX(),pos.getZ()));result.getOrCreateNbt().putString("dimension",world.getRegistryKey().getValue().toString());invOutputMapLocation.setStackInSlot(0,ItemStack.EMPTY);invOutputResult.setStackInSlot(0,result);progressOutput=0;}
                catch(ArithmeticException|IllegalArgumentException invalid){status=2;progressOutput=0;}
            }
        }else progressOutput=0;
        if(progressInput>0||progressOutput>0)markDirty();
    }
    @Override public void writeNbt(NbtCompound nbt){
        super.writeNbt(nbt);for(int i=0;i<16;i++){var tag=new NbtCompound();if(unreadable[i]!=null)tag=unreadable[i].copy();else layers[i].writeToNBT(tag);nbt.put("layer_"+i,tag);}
        nbt.putInt("progressInput",progressInput);nbt.putInt("progressOutput",progressOutput);
    }
    @Override public void readFromNBT(NbtCompound nbt){
        super.readFromNBT(nbt);for(int i=0;i<16;i++){var tag=nbt.getCompound("layer_"+i);layers[i]=new ZonePlan();unreadable[i]=null;
            try{layers[i].readFromNBT(tag);}catch(IllegalArgumentException invalid){unreadable[i]=tag.copy();buildcraft.api.core.BCLog.logger.warn("Preserving unreadable zone planner layer {} at {}",i,pos);}versions[i]++;
        }
        progressInput=Math.max(0,Math.min(199,nbt.getInt("progressInput")));progressOutput=Math.max(0,Math.min(199,nbt.getInt("progressOutput")));
    }
}
