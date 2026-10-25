/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native persistent quarry tickets. Remake by FelixMantra. */
package buildcraft.lib.chunkload;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.world.*;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.chunkload.IChunkLoadingTile.LoadType;

/** Independent native tickets never add/remove the administrator's vanilla forced chunks. */
public final class ChunkLoaderManager {
    private static final String SAVE_ID="buildcraft_chunk_loaders";
    private static final ChunkTicketType<BlockPos> TICKET=ChunkTicketType.create("buildcraft_machine",Comparator.comparingLong(BlockPos::asLong));
    private static final Map<ServerWorld,State> WORLDS=new WeakHashMap<>();
    private static boolean registered;
    private record Lease(WeakReference<BlockEntity> tile,Set<ChunkPos> chunks) {}
    public static final class State extends PersistentState {
        private final Map<BlockPos,LoadType> saved=new HashMap<>();
        private final Map<BlockPos,Lease> active=new HashMap<>();
        private final Set<BlockPos> pending=new HashSet<>();
        private BCLibConfig.ChunkLoaderLevel level;
        private int startupTicks;
        private static State read(NbtCompound nbt) {
            var state=new State();
            for(var element:nbt.getList("loaders",NbtElement.COMPOUND_TYPE)) {
                var entry=(NbtCompound)element;int type=entry.getByte("type");
                if(type>=0 && type<LoadType.values().length)state.saved.put(BlockPos.fromLong(entry.getLong("pos")),LoadType.values()[type]);
            }
            return state;
        }
        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            var list=new NbtList();
            for(var entry:saved.entrySet()) {var tag=new NbtCompound();tag.putLong("pos",entry.getKey().asLong());tag.putByte("type",(byte)entry.getValue().ordinal());list.add(tag);}
            nbt.put("loaders",list);return nbt;
        }
    }
    public static void register() {
        if(registered)return;registered=true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server,world)->restoreWorld(world));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server,world)->unloadWorld(world));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(ChunkLoaderManager::tick);
    }
    private static State state(ServerWorld world) {
        return WORLDS.computeIfAbsent(world,w->w.getPersistentStateManager().getOrCreate(State::read,State::new,SAVE_ID));
    }
    public static void restoreWorld(ServerWorld world) {
        State state=state(world);state.level=BCLibConfig.chunkLoadingLevel;state.startupTicks=0;
        for(var entry:List.copyOf(state.saved.entrySet())) {
            BlockPos pos=entry.getKey();
            if(!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) {
                state.saved.remove(pos);state.markDirty();continue;
            }
            if(!state.level.canLoad(entry.getValue()) || state.active.containsKey(pos))continue;
            var chunk=new ChunkPos(pos);world.getChunkManager().addTicket(TICKET,chunk,2,pos);
            state.active.put(pos,new Lease(new WeakReference<>(null),Set.of(chunk)));state.pending.add(pos);
        }
    }
    public static void unloadWorld(ServerWorld world) {
        State state=WORLDS.remove(world);if(state==null)return;
        for(var entry:state.active.entrySet())remove(world,entry.getKey(),entry.getValue());
        state.active.clear();state.pending.clear();
    }
    private static void tick(ServerWorld world) {
        State state=state(world);
        if(state.level!=BCLibConfig.chunkLoadingLevel) {
            var tiles=new ArrayList<BlockEntity>();
            for(var lease:state.active.values())if(lease.tile.get()!=null)tiles.add(lease.tile.get());
            for(var entry:state.active.entrySet())remove(world,entry.getKey(),entry.getValue());
            state.active.clear();state.pending.clear();restoreWorld(world);
            for(var tile:tiles)if(tile instanceof IChunkLoadingTile loading && !tile.isRemoved())update(tile,loading,world);
        }
        if(++state.startupTicks<10)return;
        for(var pos:List.copyOf(state.pending)) {
            if(!world.isChunkLoaded(pos))continue;
            var tile=world.getBlockEntity(pos);
            if(tile instanceof IChunkLoadingTile loading && !tile.isRemoved())update(tile,loading,world);
            else {
                var lease=state.active.remove(pos);if(lease!=null)remove(world,pos,lease);
                state.saved.remove(pos);state.pending.remove(pos);state.markDirty();
            }
        }
    }
    public static <T extends BlockEntity & IChunkLoadingTile> void loadChunksForTile(T tile) {
        if(tile.getWorld() instanceof ServerWorld world && !tile.isRemoved())update(tile,tile,world);
    }
    private static void update(BlockEntity tile,IChunkLoadingTile loading,ServerWorld world) {
        State state=state(world);BlockPos pos=tile.getPos().toImmutable();LoadType type=loading.getLoadType();
        if(type==null) {forget(tile,world);return;}
        if(state.saved.put(pos,type)!=type)state.markDirty();
        state.pending.remove(pos);
        if(!BCLibConfig.chunkLoadingLevel.canLoad(type)) {release(tile,world);return;}
        var wanted=new HashSet<ChunkPos>();
        var additional=loading.getChunksToLoad();if(additional!=null)wanted.addAll(additional);
        wanted.add(new ChunkPos(pos));
        var previous=state.active.get(pos);var before=previous==null?Set.<ChunkPos>of():previous.chunks;
        for(var chunk:before)if(!wanted.contains(chunk))world.getChunkManager().removeTicket(TICKET,chunk,2,pos);
        for(var chunk:wanted)if(!before.contains(chunk))world.getChunkManager().addTicket(TICKET,chunk,2,pos);
        state.active.put(pos,new Lease(new WeakReference<>(tile),Set.copyOf(wanted)));
    }
    private static void remove(ServerWorld world,BlockPos owner,Lease lease) {
        for(var chunk:lease.chunks)world.getChunkManager().removeTicket(TICKET,chunk,2,owner);
    }
    private static void release(BlockEntity tile,ServerWorld world) {
        State state=state(world);var lease=state.active.get(tile.getPos());
        if(lease==null || lease.tile.get()!=null && lease.tile.get()!=tile)return;
        state.active.remove(tile.getPos());state.pending.remove(tile.getPos());remove(world,tile.getPos(),lease);
    }
    /** Detachment removes runtime tickets but preserves the loader for the next world start. */
    public static <T extends BlockEntity & IChunkLoadingTile> void releaseChunksFor(T tile) {
        if(tile.getWorld() instanceof ServerWorld world)release(tile,world);
    }
    /** Actual block removal also removes its saved registration. */
    public static <T extends BlockEntity & IChunkLoadingTile> void forget(T tile) {
        if(tile.getWorld() instanceof ServerWorld world)forget(tile,world);
    }
    private static void forget(BlockEntity tile,ServerWorld world) {
        State state=state(world);var lease=state.active.get(tile.getPos());
        if(lease!=null && lease.tile.get()!=null && lease.tile.get()!=tile)return;
        release(tile,world);if(state.saved.remove(tile.getPos())!=null)state.markDirty();
    }
    public static <T extends BlockEntity & IChunkLoadingTile> Set<ChunkPos> getChunksToLoad(T tile) {
        var chunks=new HashSet<ChunkPos>();if(tile.getChunksToLoad()!=null)chunks.addAll(tile.getChunksToLoad());
        chunks.add(new ChunkPos(tile.getPos()));return chunks;
    }
    public static Set<ChunkPos> getActiveChunks(ServerWorld world,BlockPos owner) {
        var lease=state(world).active.get(owner);return lease==null?Set.of():lease.chunks;
    }
}
