/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.inventory;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

/** Native entity extraction with shared reservations and rollback. Remake by FelixMantra. */
final class EntityItemExtraction extends SnapshotParticipant<EntityItemExtraction.State> implements IItemTransactor.IItemExtractable {
    private static final WeakHashMap<Entity,EntityItemExtraction> EXTRACTORS=new WeakHashMap<>();
    private final WeakReference<Entity> reference;
    private boolean reserved;
    record State(ItemStack stack,boolean reserved) {}
    private EntityItemExtraction(Entity entity){reference=new WeakReference<>(entity);}

    static IItemTransactor forEntity(Entity entity,Direction side){
        if(!available(entity))return NoSpaceTransactor.INSTANCE;
        if(entity instanceof ItemEntity || entity instanceof PersistentProjectileEntity)
            return EXTRACTORS.computeIfAbsent(entity,EntityItemExtraction::new);
        if(entity instanceof Inventory inventory){
            var delegate=new StorageItemTransactor(InventoryStorage.of(inventory,side));
            return (IItemTransactor.IItemExtractable)(filter,min,max,simulate)->available(entity)?delegate.extract(filter,min,max,simulate):ItemStack.EMPTY;
        }
        return NoSpaceTransactor.INSTANCE;
    }
    private static boolean available(Entity entity){return entity!=null && !entity.isRemoved() && !entity.getWorld().isClient && !(entity instanceof LivingEntity);}
    @Override public ItemStack extract(IStackFilter filter,int min,int max,boolean simulate){
        var entity=reference.get();int minimum=Math.max(1,min);
        if(!available(entity) || reserved || max<minimum)return ItemStack.EMPTY;
        ItemStack stack;
        if(entity instanceof ItemEntity item)stack=item.getStack().copy();
        else if(entity instanceof PersistentProjectileEntity projectile && projectile.pickupType==PersistentProjectileEntity.PickupPermission.ALLOWED)stack=projectile.asItemStack().copy();
        else return ItemStack.EMPTY;
        if(stack.isEmpty() || stack.getCount()<minimum || filter!=null && !filter.matches(stack.copy()))return ItemStack.EMPTY;
        var result=stack.split(max);
        if(!simulate)try(var transaction=Transaction.openNested(Transaction.getCurrentUnsafe())){
            updateSnapshots(transaction);
            if(entity instanceof ItemEntity item)item.setStack(stack);
            else reserved=true;
            transaction.commit();
        }
        return result;
    }
    @Override protected State createSnapshot(){var entity=reference.get();return new State(entity instanceof ItemEntity item?item.getStack().copy():ItemStack.EMPTY,reserved);}
    @Override protected void readSnapshot(State state){var entity=reference.get();reserved=state.reserved();if(entity instanceof ItemEntity item && !item.isRemoved())item.setStack(state.stack().copy());}
    @Override protected void onFinalCommit(){
        var entity=reference.get();
        if(entity!=null && (reserved || entity instanceof ItemEntity item && item.getStack().isEmpty()))entity.discard();
        reserved=false;
    }
}
