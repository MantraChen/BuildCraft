/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.gate;

import java.util.*;
import buildcraft.lib.statement.*;
import net.minecraft.nbt.*;

/** Copies configuration, never live wire outputs, activation state or single-action latches. */
public final class GateConfigCopier {
    public enum Warning { LOGIC, SLOTS, TRIGGER_PARAMETERS, ACTION_PARAMETERS, MISSING_DATA }
    public record Result(boolean applied,Set<Warning> warnings){
        public Result {warnings=Set.copyOf(warnings);}
    }
    private GateConfigCopier(){}
    private record Row(FullStatement<TriggerWrapper> trigger,FullStatement<ActionWrapper> action){}
    public static Result paste(GateLogic target,NbtCompound saved){
        var holder=target.getPipeHolder();
        if(holder.getPipeWorld()==null || holder.getPipeWorld().isClient || holder.getPipeTile().isRemoved()
            || !holder.getPipeWorld().isChunkLoaded(holder.getPipePos())
            || holder.getPipeWorld().getBlockEntity(holder.getPipePos())!=holder.getPipeTile()
            || holder.getPluggable(target.getSide())!=target.pluggable)return new Result(false,Set.of());
        var warnings=EnumSet.noneOf(Warning.class);var rows=new ArrayList<Row>();
        boolean[] connections=new boolean[target.connections.length];
        try{
            if(!saved.contains("variant",NbtElement.COMPOUND_TYPE))return new Result(false,Set.of());
            var v=saved.getCompound("variant");
            if(!ordinal(v,"logic",EnumGateLogic.values().length)||!ordinal(v,"material",EnumGateMaterial.values().length)
                ||!ordinal(v,"modifier",EnumGateModifier.values().length))return new Result(false,Set.of());
            var source=new GateVariant(v);
            if(source.logic!=target.variant.logic)warnings.add(Warning.LOGIC);
            if(source.numSlots>target.variant.numSlots)warnings.add(Warning.SLOTS);
            if(source.numTriggerArgs>target.variant.numTriggerArgs)warnings.add(Warning.TRIGGER_PARAMETERS);
            if(source.numActionArgs>target.variant.numActionArgs)warnings.add(Warning.ACTION_PARAMETERS);
            for(int i=0;i<target.statements.length;i++){
                var trigger=new FullStatement<>(TriggerType.INSTANCE,target.variant.numTriggerArgs,null);
                var action=new FullStatement<>(ActionType.INSTANCE,target.variant.numActionArgs,null);
                if(i<source.numSlots){
                    read(trigger,GateLogic.configStatement(saved,"trigger["+i+"]"),warnings);
                    read(action,GateLogic.configStatement(saved,"action["+i+"]"),warnings);
                }
                rows.add(new Row(trigger,action));
            }
            int bits=saved.getShort("connections");
            for(int i=0;i<connections.length && i<source.numSlots-1;i++)connections[i]=(bits&(1<<i))!=0;
        }catch(RuntimeException malformed){
            // Nothing has touched the live gate yet, including mod-supplied parameter readers.
            return new Result(false,Set.of());
        }
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);var to=target.statements[i];
            to.trigger.takePreparedConfiguration(row.trigger);to.action.takePreparedConfiguration(row.action);
        }
        System.arraycopy(connections,0,target.connections,0,connections.length);
        holder.getPipeTile().markDirty();
        for(int i=0;i<rows.size();i++){target.sendStatementUpdate(false,i);target.sendStatementUpdate(true,i);}
        target.sendResolveData();return new Result(true,warnings);
    }
    private static boolean ordinal(NbtCompound nbt,String key,int limit){
        return nbt.contains(key,NbtElement.NUMBER_TYPE) && nbt.getInt(key)>=0 && nbt.getInt(key)<limit;
    }
    private static <S extends StatementWrapper> void read(FullStatement<S> full,NbtCompound nbt,Set<Warning> warnings){
        full.readFromNbt(nbt);var statement=full.get();
        if(statement==null){if(!nbt.isEmpty())warnings.add(Warning.MISSING_DATA);return;}
        if(!full.canSet(statement)){full.set(null);warnings.add(Warning.MISSING_DATA);return;}
        for(int p=0;p<full.getParamCount();p++){
            if(p>=statement.maxParameters()){full.set(p,null);continue;}
            if(nbt.contains(Integer.toString(p)) && (full.get(p)==null || !full.canSet(p,full.get(p)))){
                full.set(p,statement.createParameter(p));warnings.add(Warning.MISSING_DATA);
            }
        }
    }
}
