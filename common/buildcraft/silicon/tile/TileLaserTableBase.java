/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.tile;

import java.io.IOException;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import buildcraft.api.mj.*;
import buildcraft.api.tiles.*;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.*;
import buildcraft.silicon.recipe.NativeAssemblyRecipe;

/** Bounded native laser work balance, retained while ingredients or selections change. */
public abstract class TileLaserTableBase extends TileBC_Neptune implements TickingBlockEntity,ILaserTarget,IDebuggable,IHasWork {
    private final AverageLong average=new AverageLong(120);
    public long avgPowerClient,power;
    protected TileLaserTableBase(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public abstract long getTarget();
    @Override public boolean hasWork(){return !isInvalidTarget() && getTarget()>0;}
    public static <T extends TileLaserTableBase> BlockEntityTicker<T> ticker(){return (world,pos,state,tile)->tile.tick();}
    @Override public void tick(){if(world!=null && !world.isClient)average.tick();}
    @Override public long getRequiredLaserPower(){
        return isInvalidTarget()?0:Math.max(0,Math.min(NativeAssemblyRecipe.MAX_COST,getTarget())-power);
    }
    @Override public long receiveLaserPower(long amount) {
        if(amount<0)throw new IllegalArgumentException("Negative table energy");
        long accepted=Math.min(amount,getRequiredLaserPower());
        if(accepted>0){power+=accepted;average.push(accepted);markDirty();}
        return amount-accepted;
    }
    @Override public boolean isInvalidTarget(){return !isStorageAvailable() || world==null || world.isClient;}
    @Override public void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putLong("power",power);}
    @Override public void readFromNBT(NbtCompound nbt){super.readFromNBT(nbt);power=Math.max(0,Math.min(NativeAssemblyRecipe.MAX_COST,nbt.getLong("power")));}
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side) {
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && id==NET_GUI_TICK) {
            buffer.writeLong(power);buffer.writeLong(Math.max(0,Math.min(NativeAssemblyRecipe.MAX_COST,Math.round(average.getAverage()))));
        }
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object ctx) throws IOException {
        super.readPayload(id,buffer,side,ctx);
        if(side==NetSide.CLIENT && id==NET_GUI_TICK) {
            long value=buffer.readLong(),avg=buffer.readLong();
            if(value<0 || value>NativeAssemblyRecipe.MAX_COST || avg<0 || avg>NativeAssemblyRecipe.MAX_COST)throw new IOException("Invalid laser table power");
            power=value;avgPowerClient=avg;
        }
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side){left.add("power = "+power);left.add("target = "+getTarget());}
    protected boolean extract(buildcraft.lib.tile.item.ItemHandlerSimple inv,java.util.Collection<?> items,boolean simulate,boolean precise) {
        // Legacy advanced-crafting/integration callers are restored with their own native recipe implementations.
        return false;
    }
}
