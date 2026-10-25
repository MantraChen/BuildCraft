/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import buildcraft.api.core.EnumPipePart;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Named, persistent slot groups with direction-aware Fabric Transfer API views. */
public class ItemHandlerManager {

    public final StackChangeCallback callback;
    private final Map<String, ItemHandlerSimple> handlers = new LinkedHashMap<>();
    private final List<ItemHandlerSimple> handlersToDrop = new ArrayList<>();
    private final Map<EnumPipePart, List<Storage<ItemVariant>>> sideViews = new EnumMap<>(EnumPipePart.class);

    public enum EnumAccess {
        NONE, INSERT, EXTRACT, BOTH, PHANTOM
    }

    public ItemHandlerManager(StackChangeCallback defaultCallback) {
        this.callback = defaultCallback;
        for (EnumPipePart part : EnumPipePart.VALUES) sideViews.put(part, new ArrayList<>());
    }

    public ItemHandlerSimple addInvHandler(String name, int size, EnumAccess access, EnumPipePart... parts) {
        return addInvHandler(name, new ItemHandlerSimple(size, callback), access, parts);
    }

    public ItemHandlerSimple addInvHandler(String name, int size, StackInsertionChecker checker,
        EnumAccess access, EnumPipePart... parts) {
        return addInvHandler(name, size, checker, StackInsertionFunction.getDefaultInserter(), access, parts);
    }

    public ItemHandlerSimple addInvHandler(String name, int size, StackInsertionChecker checker,
        StackInsertionFunction inserter, EnumAccess access, EnumPipePart... parts) {
        return addInvHandler(name, new ItemHandlerSimple(size, checker, inserter, callback), access, parts);
    }

    public <T extends ItemHandlerSimple> T addInvHandler(String name, T handler, EnumAccess access,
        EnumPipePart... parts) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(access, "access");
        if (handlers.containsKey(name) || handlers.containsValue(handler)) {
            throw new IllegalArgumentException("Inventory group is already registered: " + name);
        }
        if (parts == null) parts = new EnumPipePart[0];
        if ((access == EnumAccess.NONE || access == EnumAccess.PHANTOM) && parts.length > 0) {
            throw new IllegalArgumentException("Private and phantom inventories cannot be exposed on a face");
        }
        handlers.put(name, handler);
        if (access != EnumAccess.PHANTOM) handlersToDrop.add(handler);
        EnumSet<EnumPipePart> visited = EnumSet.noneOf(EnumPipePart.class);
        Storage<ItemVariant> storage = handler.asStorage(
            access == EnumAccess.INSERT || access == EnumAccess.BOTH,
            access == EnumAccess.EXTRACT || access == EnumAccess.BOTH);
        for (EnumPipePart part : parts) {
            if (part == null) part = EnumPipePart.CENTER;
            if (visited.add(part)) sideViews.get(part).add(storage);
        }
        return handler;
    }

    /** A null side denotes the center view, matching the original slot-group API. */
    public Storage<ItemVariant> getStorage(Direction side) {
        List<Storage<ItemVariant>> views = sideViews.get(EnumPipePart.fromFacing(side));
        return views.isEmpty() ? null : new CombinedStorage<>(views);
    }

    public void addDrops(DefaultedList<ItemStack> toDrop) {
        for (ItemHandlerSimple handler : handlersToDrop) {
            for (ItemStack stack : handler.stacks) {
                if (!stack.isEmpty()) toDrop.add(stack.copy());
            }
        }
    }

    public NbtCompound serializeNBT() {
        NbtCompound nbt = new NbtCompound();
        handlers.forEach((name, handler) -> nbt.put(name, handler.serializeNBT()));
        return nbt;
    }

    /** Forge-compat alias for {@link #serializeNBT()}. */
    public NbtCompound createNbt() {
        return serializeNBT();
    }

    public void deserializeNBT(NbtCompound nbt) {
        handlers.forEach((name, handler) -> handler.deserializeNBT(nbt.getCompound(name)));
    }
}
