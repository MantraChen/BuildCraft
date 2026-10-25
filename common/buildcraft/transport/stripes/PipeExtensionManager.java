/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric relocation, preserving ownership, flow and mounted parts. Remake by FelixMantra. */
package buildcraft.transport.stripes;

import java.util.*;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.misc.*;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.transport.*;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Relocate only after pipe flow iteration finishes, invalidating every old storage view. */
public final class PipeExtensionManager implements IPipeExtensionManager {
    public static final PipeExtensionManager INSTANCE=new PipeExtensionManager();
    private final Set<PipeDefinition> retractionPipes=new HashSet<>();
    private final Map<ServerWorld,Map<BlockPos,Request>> pending=new WeakHashMap<>();
    private record Request(BlockPos pos,Direction direction,PipeBehaviourStripes head,ItemStack stack){}
    private record Snapshot(BlockPos pos,BlockState state,BlockEntity tile,NbtCompound data){
        static Snapshot take(ServerWorld world,BlockPos pos){var tile=world.getBlockEntity(pos);return new Snapshot(pos,world.getBlockState(pos),tile,tile==null?null:tile.createNbtWithIdentifyingData());}
        boolean unchanged(ServerWorld world){return world.getBlockState(pos)==state && world.getBlockEntity(pos)==tile && (tile==null || data.equals(tile.createNbtWithIdentifyingData()));}
    }
    @Override public void registerRetractionPipe(PipeDefinition definition){retractionPipes.add(definition);}
    @Override public boolean requestPipeExtension(World world,BlockPos pos,Direction direction,IStripesActivator activator,ItemStack stack){
        if(!(world instanceof ServerWorld server) || direction==null || Transaction.getCurrentUnsafe()!=null || stack.isEmpty()
            || !(stack.getItem() instanceof IItemPipe item) || item.getDefinition().flowType!=PipeApi.flowItems
            || !(activator instanceof PipeBehaviourStripes head) || !head.isLive()
            || world.getBlockEntity(pos)!=head.pipe.getHolder().getPipeTile())return false;
        return pending.computeIfAbsent(server,w->new LinkedHashMap<>()).putIfAbsent(pos.toImmutable(),new Request(pos.toImmutable(),direction,head,stack.copy()))==null;
    }
    public void tick(ServerWorld world){
        var requests=pending.remove(world);if(requests==null)return;
        for(var request:requests.values())if(!move(world,request))refund(world,request);
    }
    private void refund(ServerWorld world,Request request){
        if(!request.head.sendItem(request.stack,request.direction))InventoryUtil.drop(world,request.pos,request.stack.copy());
    }
    private boolean retractable(ServerWorld world,Request request,Direction side){
        var pos=request.pos.offset(side);if(!StripesInteraction.loaded(world,pos) || !(world.getBlockEntity(pos) instanceof TilePipeHolder tile)
            || tile.getPipe()==null || tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes)return false;
        var pipe=tile.getPipe();pipe.refreshConnections();if(pipe.getDefinition().flowType!=PipeApi.flowItems || !pipe.isConnected(side.getOpposite()))return false;
        int others=0;
        for(var face:Direction.values()){
            if(pipe.getConnectedType(face)==IPipe.ConnectedType.TILE)return false;
            if(face!=side.getOpposite() && pipe.isConnected(face))others++;
        }
        return others<=1;
    }
    private boolean move(ServerWorld world,Request request){
        if(!request.head.isLive() || request.head.direction!=request.direction || !StripesInteraction.loaded(world,request.pos))return false;
        var old=(TilePipeHolder)world.getBlockEntity(request.pos);
        boolean retract=retractionPipes.contains(((IItemPipe)request.stack.getItem()).getDefinition());
        Direction movement=request.direction;
        if(retract){
            movement=request.direction.getOpposite();
            if(!retractable(world,request,movement)){
                movement=null;for(var side:Direction.values())if(side.getAxis()!=request.direction.getAxis() && retractable(world,request,side)){movement=side;break;}
            }
            if(movement==null)return false;
        }
        var destination=request.pos.offset(movement);if(!StripesInteraction.loaded(world,destination))return false;
        if(!retract && (!world.getBlockState(destination).isReplaceable() || world.getBlockEntity(destination)!=null))return false;
        var source=Snapshot.take(world,request.pos);var target=Snapshot.take(world,destination);
        var player=FakePlayerProvider.INSTANCE.getFakePlayer(world,old.getOwner(),request.pos);var previous=player.getMainHandStack();
        List<ItemStack> drops=List.of();
        try{
            player.setStackInHand(Hand.MAIN_HAND,request.stack.copy());
            if(!allowed(world,source,player,movement) || !allowed(world,target,player,movement.getOpposite()))return false;
            if(!source.unchanged(world) || !target.unchanged(world) || !request.head.isLive() || request.head.direction!=request.direction)return false;
            if(retract){
                if(!retractable(world,request,movement))return false;
                drops=Block.getDroppedStacks(target.state,world,destination,target.tile,player,player.getMainHandStack());
            }
            // The old head's NBT includes all in-flight items and plugs. Never reuse removed holders.
            var moved=relocatedData(source.data,destination);
            var behavior=moved.getCompound("pipe").getCompound("beh");
            behavior.remove("workPos");behavior.remove("workState");behavior.remove("progress");
            behavior.put("direction",NBTUtilBC.writeEnum(retract?movement.getOpposite():request.direction));
            NbtCompound base=null;
            if(!retract){
                var dummy=new TilePipeHolder(old.getType(),request.pos,source.state);
                // Construct the new pipe without placement events or intermediate packets.
                var newPipe=new Pipe(dummy,((IItemPipe)request.stack.getItem()).getDefinition());
                newPipe.setColour(buildcraft.transport.item.ItemPipeHolder.getColour(request.stack));
                var backPlug=old.getPluggable(movement.getOpposite());
                if(!Pipe.canPipesConnect(movement,newPipe,old.getPipe()) || backPlug!=null && backPlug.isBlocking())return false;
                base=new NbtCompound();base.putString("id",source.data.getString("id"));base.put("pipe",newPipe.writeToNbt());
                if(source.data.contains("owner"))base.put("owner",source.data.getCompound("owner").copy());
            }
            try{
                install(world,request.pos,retract?Blocks.AIR.getDefaultState():source.state,base);
                install(world,destination,source.state,moved);
            }catch(RuntimeException failure){
                install(world,source.pos,source.state,source.data);install(world,target.pos,target.state,target.data);
                notify(world,source.pos);notify(world,target.pos);
                buildcraft.BuildCraftFabric.LOGGER.error("Stripes relocation rolled back at {}",request.pos,failure);return false;
            }
            notify(world,request.pos);notify(world,destination);
            var head=(PipeBehaviourStripes)((TilePipeHolder)world.getBlockEntity(destination)).getPipe().getBehaviour();
            var remainder=request.stack.copy();if(!retract)remainder.decrement(1);
            if(!remainder.isEmpty() && !head.sendItem(remainder,head.direction))head.dropItem(remainder,head.direction);
            for(var drop:drops)if(!head.sendItem(drop,head.direction))head.dropItem(drop,head.direction);
            return true;
        }catch(StripesInteraction.Denied denied){return false;}
        finally{player.setStackInHand(Hand.MAIN_HAND,previous);}
    }
    private boolean allowed(ServerWorld world,Snapshot snapshot,ServerPlayerEntity player,Direction side){
        if(!world.canPlayerModifyAt(player,snapshot.pos))return false;
        if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,snapshot.pos,snapshot.state,snapshot.tile))return false;
        // SUCCESS belongs to the callback; do not perform a second mutation after it handles use.
        return !StripesInteraction.allow(player,snapshot.pos,side).isAccepted();
    }
    private static NbtCompound relocatedData(NbtCompound data,BlockPos pos){
        var copy=data.copy();copy.putInt("x",pos.getX());copy.putInt("y",pos.getY());copy.putInt("z",pos.getZ());return copy;
    }
    private static void install(ServerWorld world,BlockPos pos,BlockState state,NbtCompound data){
        world.removeBlockEntity(pos);
        if(world.getBlockState(pos)!=state && !world.setBlockState(pos,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE))throw new IllegalStateException("Cannot relocate stripes at "+pos);
        if(data!=null){
            var tile=BlockEntity.createFromNbt(pos,state,relocatedData(data,pos));
            if(tile==null)throw new IllegalStateException("Cannot restore block entity at "+pos);
            world.addBlockEntity(tile);if(tile instanceof TileBC_Neptune nativeTile)nativeTile.onLoad();tile.markDirty();
        }
    }
    private static void notify(ServerWorld world,BlockPos pos){
        var state=world.getBlockState(pos);world.updateListeners(pos,state,state,Block.NOTIFY_LISTENERS);world.updateNeighbors(pos,state.getBlock());
        if(world.getBlockEntity(pos) instanceof TilePipeHolder tile){tile.getPipe().markForUpdate();tile.sendNetworkUpdate(TileBC_Neptune.NET_RENDER_DATA);}
        buildcraft.transport.wire.WorldSavedDataWireSystems.get(world).markStructureChanged();
    }
}
