/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import java.util.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import buildcraft.api.schematics.*;
import buildcraft.lib.misc.FakePlayerProvider;

/** Reconcile rule-selected entities after native block work, reserving their full material cost atomically. */
final class BlueprintEntities {
    private final BlueprintBuilder builder;
    private List<ISchematicEntity> missing=List.of();
    private boolean finished;
    private int pending;
    BlueprintEntities(BlueprintBuilder builder){this.builder=builder;}
    void reset(){var info=builder.getBuildingInfo();missing=info==null?List.of():List.copyOf(info.entities);pending=missing.size();finished=info==null;}
    boolean isFinished(){return finished;}
    int pending(){return pending;}
    List<ItemStack> materials(){var info=builder.getBuildingInfo();return info==null?List.of():missing.stream().flatMap(s->info.entitiesRequiredItems.get(s).stream()).toList();}
    private boolean live(Blueprint.BuildingInfo info){
        return builder.getBuildingInfo()==info && builder.tile.isBlueprintWorkValid() && (!(builder.tile instanceof net.minecraft.block.entity.BlockEntity tile)
            || !tile.isRemoved() && tile.getWorld().getBlockEntity(tile.getPos())==tile);
    }
    private static boolean matches(ISchematicEntity schematic,Entity entity,BlockPos base){
        if(schematic instanceof SchematicEntityDefault normal)return normal.matches(entity,base);
        if(entity.getPos().squaredDistanceTo(schematic.getPos().add(Vec3d.of(base)))>.01)return false;
        var actual=SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(entity.getWorld(),base,entity));
        return actual!=null && actual.getClass()==schematic.getClass() && actual.serializeNBT().equals(schematic.serializeNBT());
    }
    void tick(boolean blocksFinished){
        var info=builder.getBuildingInfo();if(info==null || !(builder.workWorld() instanceof ServerWorld world) || Transaction.getCurrentUnsafe()!=null)return;
        finished=false;
        var area=info.box.getBoundingBox();
        for(int x=info.box.min().getX()>>4;x<=info.box.max().getX()>>4;x++)for(int z=info.box.min().getZ()>>4;z<=info.box.max().getZ()>>4;z++)if(!world.isChunkLoaded(x,z) || !world.isChunkLoaded(ChunkPos.toLong(x,z)))return;
        var existing=world.getEntitiesByClass(Entity.class,area,e->!e.isRemoved() && area.contains(e.getPos()));
        var claimed=new HashSet<UUID>();var absent=new ArrayList<ISchematicEntity>();
        for(var desired:info.entities){var found=existing.stream().filter(e->!claimed.contains(e.getUuid()) && matches(desired,e,info.offsetPos)).findFirst();
            if(found.isPresent())claimed.add(found.get().getUuid());else absent.add(desired);}
        missing=List.copyOf(absent);pending=absent.size();
        var extras=new LinkedHashMap<Entity,ISchematicEntity>();
        if(builder.excavates())for(var entity:existing)if(!claimed.contains(entity.getUuid())){
            var captured=SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(world,info.offsetPos,entity));if(captured!=null)extras.put(entity,captured);
        }
        pending+=extras.size();
        if(pending==0){finished=blocksFinished;return;}
        // The original entity stage requires a full battery; unlike block jobs it owns no partial MJ credit.
        if(builder.workBattery().getStored()<builder.workBattery().getCapacity())return;
        var player=FakePlayerProvider.INSTANCE.getFakePlayer(world,builder.workOwner(),builder.workOrigin());int budget=16;
        for(var entry:extras.entrySet()){
            if(budget--<=0)break;var entity=entry.getKey();var captured=entry.getValue();
            if(!live(info) || !SchematicEntityDefault.accessible(world,entity) || !world.canPlayerModifyAt(player,entity.getBlockPos()))continue;
            var before=captured.serializeNBT();
            if(AttackEntityCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,entity,new EntityHitResult(entity))!=ActionResult.PASS)continue;
            if(!live(info) || entity.isRemoved() || !SchematicEntityDefault.accessible(world,entity) || !world.canPlayerModifyAt(player,entity.getBlockPos()))continue;
            var after=SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(world,info.offsetPos,entity));
            if(after==null || !before.equals(after.serializeNBT()) || !after.computeRequiredFluids().isEmpty())continue;
            var recovered=after.computeRequiredItems();entity.discard();builder.acceptDrops(recovered);pending--;builder.workChanged();
        }
        if(!blocksFinished || pending>absent.size())return;
        var stillMissing=new ArrayList<ISchematicEntity>();
        for(var desired:absent){
            if(budget--<=0 || !live(info)){stillMissing.add(desired);continue;}
            var target=BlockPos.ofFloored(desired.getPos().add(Vec3d.of(info.offsetPos)));
            if(!world.isInBuildLimit(target)||!world.getWorldBorder().contains(target)||!world.isChunkLoaded(target)||!world.canPlayerModifyAt(player,target)){stillMissing.add(desired);continue;}
            var required=info.entitiesRequiredItems.get(desired);var fluids=info.entitiesRequiredFluids.get(desired);
            boolean placed=BlueprintMaterials.place(builder.tile.getResourceStorage(),builder.tile.getTankManager(),required,fluids,()->{
                var held=player.getMainHandStack();var sample=required.stream().filter(s->!s.isEmpty()).findFirst().map(ItemStack::copy).orElse(ItemStack.EMPTY);
                if(!sample.isEmpty())sample.setCount(1);player.setStackInHand(Hand.MAIN_HAND,sample);
                try{
                    var hit=new BlockHitResult(Vec3d.ofBottomCenter(target),Direction.UP,target,false);
                    if(UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit)!=ActionResult.PASS)return false;
                    if(!live(info)||!world.isChunkLoaded(target)||!world.canPlayerModifyAt(player,target))return false;
                    return desired.build(world,info.offsetPos)!=null;
                }finally{player.setStackInHand(Hand.MAIN_HAND,held);}
            });
            if(placed){pending--;builder.workChanged();}else stillMissing.add(desired);
        }
        missing=List.copyOf(stillMissing);finished=pending==0 && live(info);
    }
}
