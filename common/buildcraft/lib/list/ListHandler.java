/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DefaultedList;
import buildcraft.api.lists.ListMatchHandler;
import buildcraft.api.lists.ListRegistry;

public final class ListHandler {
    public static final int WIDTH = 9;
    public static final int HEIGHT = 2;

    public static class Line {
        public final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(WIDTH, ItemStack.EMPTY);
        public boolean precise, byType, byMaterial;

        public boolean isDefault() { return !precise && !byType && !byMaterial && !hasItems(); }
        public boolean hasItems() { return stacks.stream().anyMatch(stack -> !stack.isEmpty()); }
        public boolean isOneStackMode() { return byType || byMaterial; }

        public boolean getOption(int id) {
            return switch (id) {
                case 0 -> precise;
                case 1 -> byType;
                case 2 -> byMaterial;
                default -> throw new IllegalArgumentException("Unknown list option: " + id);
            };
        }

        public void toggleOption(int id) {
            getOption(id); // Validate before changing anything.
            if (!byType && !byMaterial && id != 0) {
                for (int slot = 1; slot < WIDTH; slot++) stacks.set(slot, ItemStack.EMPTY);
            }
            switch (id) {
                case 0 -> precise = !precise;
                case 1 -> byType = !byType;
                case 2 -> byMaterial = !byMaterial;
            }
        }

        public ListMatchHandler.Type getSortingType() {
            return byType ? (byMaterial ? ListMatchHandler.Type.CLASS : ListMatchHandler.Type.TYPE)
                : ListMatchHandler.Type.MATERIAL;
        }

        public boolean matches(ItemStack target) {
            if (target.isEmpty()) return false;
            if (isOneStackMode()) {
                ItemStack source = stacks.get(0);
                if (source.isEmpty()) return false;
                ListMatchHandler.Type type = getSortingType();
                boolean handled = false;
                for (ListMatchHandler handler : ListRegistry.getHandlers()) {
                    if (!handler.isValidSource(type, source)) continue;
                    handled = true;
                    if (handler.matches(type, source, target, precise)) return true;
                }
                return !handled && type == ListMatchHandler.Type.TYPE && sameItem(source, target, precise);
            }
            return stacks.stream().anyMatch(source -> sameItem(source, target, precise));
        }

        private static boolean sameItem(ItemStack source, ItemStack target, boolean precise) {
            return !source.isEmpty() && source.isOf(target.getItem())
                && (!precise || ItemStack.canCombine(source, target));
        }

        public void setStack(int slot, ItemStack stack) {
            if (slot < 0 || slot >= WIDTH) throw new IndexOutOfBoundsException(slot);
            if (slot != 0 && isOneStackMode()) return;
            ItemStack copy = stack.copy();
            if (!copy.isEmpty()) copy.setCount(1);
            stacks.set(slot, copy);
        }

        public ItemStack getStack(int slot) {
            return slot < 0 || slot >= WIDTH ? ItemStack.EMPTY : stacks.get(slot);
        }

        public static Line fromNBT(NbtCompound data) {
            Line line = new Line();
            if (data == null) return line;
            NbtList saved = data.getList("st", NbtElement.COMPOUND_TYPE);
            for (int slot = 0; slot < Math.min(saved.size(), WIDTH); slot++) {
                line.setStack(slot, ItemStack.fromNbt(saved.getCompound(slot)));
            }
            line.precise = data.getBoolean("Fp");
            line.byType = data.getBoolean("Ft");
            line.byMaterial = data.getBoolean("Fm");
            return line;
        }

        public NbtCompound toNBT() {
            NbtCompound data = new NbtCompound();
            NbtList saved = new NbtList();
            for (ItemStack stack : stacks) saved.add(stack.writeNbt(new NbtCompound()));
            data.put("st", saved);
            data.putBoolean("Fp", precise);
            data.putBoolean("Ft", byType);
            data.putBoolean("Fm", byMaterial);
            return data;
        }

        @Environment(EnvType.CLIENT)
        public DefaultedList<ItemStack> getExamples() {
            DefaultedList<ItemStack> examples = DefaultedList.of();
            ItemStack source = stacks.get(0);
            if (source.isEmpty()) return examples;
            List<ListMatchHandler> search = new ArrayList<>();
            for (ListMatchHandler handler : ListRegistry.getHandlers()) {
                if (!handler.isValidSource(getSortingType(), source)) continue;
                DefaultedList<ItemStack> custom = handler.getClientExamples(getSortingType(), source);
                if (custom == null) search.add(handler);
                else examples.addAll(custom);
            }
            for (Item item : Registries.ITEM) {
                ItemStack candidate = item.getDefaultStack();
                if (!candidate.isEmpty() && search.stream().anyMatch(
                    handler -> handler.matches(getSortingType(), source, candidate, false))) examples.add(candidate);
            }
            Collections.shuffle(examples);
            return examples;
        }
    }

    public static Line[] getLines(ItemStack stack) {
        NbtCompound data = stack.getNbt();
        NbtList saved = data != null && data.getBoolean("written")
            ? data.getList("lines", NbtElement.COMPOUND_TYPE) : new NbtList();
        Line[] lines = new Line[HEIGHT];
        for (int row = 0; row < HEIGHT; row++) {
            lines[row] = row < saved.size() ? Line.fromNBT(saved.getCompound(row)) : new Line();
        }
        return lines;
    }

    public static void saveLines(ItemStack stack, Line[] lines) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot write a list to an empty stack");
        if (lines.length != HEIGHT) throw new IllegalArgumentException("A list must have " + HEIGHT + " rows");
        boolean hasData = java.util.Arrays.stream(lines).anyMatch(line -> !line.isDefault());
        if (hasData) {
            NbtCompound data = stack.getOrCreateNbt();
            data.putBoolean("written", true);
            NbtList saved = new NbtList();
            for (Line line : lines) saved.add(line.toNBT());
            data.put("lines", saved);
        } else if (stack.hasNbt()) {
            NbtCompound data = stack.getNbt();
            data.remove("written");
            data.remove("lines");
            if (data.isEmpty()) stack.setNbt(null);
        }
    }

    public static boolean hasItems(ItemStack stack) {
        return java.util.Arrays.stream(getLines(stack)).anyMatch(Line::hasItems);
    }

    public static boolean isDefault(ItemStack stack) {
        return java.util.Arrays.stream(getLines(stack)).allMatch(Line::isDefault);
    }

    public static boolean matches(@Nonnull ItemStack filter, @Nonnull ItemStack target) {
        return java.util.Arrays.stream(getLines(filter)).anyMatch(line -> line.matches(target));
    }

    private ListHandler() {}
}
