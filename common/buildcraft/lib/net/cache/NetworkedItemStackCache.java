/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net.cache;

import java.io.IOException;
import java.util.Objects;

import net.minecraft.item.ItemStack;

import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.net.PacketBufferBC;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

public class NetworkedItemStackCache extends NetworkedObjectCache<ItemStack> {

    public NetworkedItemStackCache() {
        super(StackUtil.EMPTY);
    }

    @Override
    protected Object2IntMap<ItemStack> createObject2IntMap() {
        return new Object2IntOpenCustomHashMap<>(new Hash.Strategy<ItemStack>() {
            @Override
            public int hashCode(ItemStack stack) {
                return stack == null || stack.isEmpty() ? 0 : Objects.hash(stack.getItem(), stack.getNbt());
            }

            @Override
            public boolean equals(ItemStack first, ItemStack second) {
                if (first == null || second == null) return first == second;
                if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
                return ItemStack.canCombine(first, second);
            }
        });
    }

    @Override
    protected ItemStack copyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copyWithCount(1);
    }

    @Override
    protected void writeObject(ItemStack stack, PacketBufferBC buffer) {
        buffer.writeItemStack(copyOf(stack));
    }

    @Override
    protected ItemStack readObject(PacketBufferBC buffer) throws IOException {
        return buffer.readItemStack();
    }
}
