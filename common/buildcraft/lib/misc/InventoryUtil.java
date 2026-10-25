/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nonnull;
import java.util.List;

import net.minecraft.util.collection.DefaultedList;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// STUB(R.Chen): the Forge IItemHandler-based dropAll overloads and the inventory-routing helpers
// (addToRandomInventory / IItemTransactor / ItemTransactorHelper) are dropped until the Transfer-API
// item layer is migrated. Only the BlockPos + DefaultedList drop path used by TileBC_Neptune is kept.
public class InventoryUtil {

    /** Auto-producing machines prefer item pipes, then sided inventories, and spill only the remainder. */
    public static void addToBestAcceptor(World world, BlockPos pos, net.minecraft.util.math.Direction ignore, ItemStack stack) {
        if (world.isClient || stack.isEmpty()) return;
        ItemStack remaining=stack.copy();
        var directions=net.minecraft.util.math.Direction.values();
        for (int i=directions.length-1;i>0;i--) {
            int j=world.random.nextInt(i+1);var side=directions[i];directions[i]=directions[j];directions[j]=side;
        }
        for (int pass=0;pass<2 && !remaining.isEmpty();pass++) for (var side:directions) {
            BlockPos target=pos.offset(side);
            if (side==ignore || !world.isChunkLoaded(target)) continue;
            boolean pipe=world.getBlockEntity(target) instanceof buildcraft.api.transport.pipe.IPipeHolder;
            if (pipe!=(pass==0)) continue;
            var storage=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(world,target,side.getOpposite());
            if (storage==null || !storage.supportsInsertion()) continue;
            try (var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long used=storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(remaining),remaining.getCount(),tx);
                if (used<0 || used>remaining.getCount()) throw new IllegalStateException("Invalid quarry item acceptance");
                tx.commit();remaining.decrement((int)used);
            }
            if (remaining.isEmpty()) return;
        }
        drop(world,pos,remaining);
    }

    public static void dropAll(World world, BlockPos pos, DefaultedList<ItemStack> toDrop) {
        for (ItemStack stack : toDrop) {
            if (stack == null) {
                throw new NullPointerException("Null stack!");
            }
            drop(world, pos, stack);
        }
    }

    public static void drop(World world, BlockPos pos, @Nonnull ItemStack stack) {
        drop(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
    }

    public static void drop(World world, double x, double y, double z, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        world.spawnEntity(new ItemEntity(world, x, y, z, stack));
    }
}
