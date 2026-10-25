/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.item;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.snapshot.Snapshot.Header;
import buildcraft.lib.item.ItemBC_Neptune;

/** Snapshot kinds are NBT data in 1.20.1; item damage is read only for legacy stacks. */
public class ItemSnapshot extends ItemBC_Neptune {
    public ItemSnapshot(String id) { super(new Item.Settings().maxCount(1), id); }
    public ItemStack getClean(EnumSnapshotType type) {
        ItemStack stack = new ItemStack(this);
        stack.getOrCreateNbt().putString("snapshotType", type.name());
        return stack;
    }
    public ItemStack getUsed(EnumSnapshotType type, Header header) {
        ItemStack stack = getClean(type);
        stack.getOrCreateNbt().put("header", header.createNbt());
        return stack;
    }
    public Header getHeader(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemSnapshot) || !EnumItemSnapshotType.getFromStack(stack).used) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("header", NbtElement.COMPOUND_TYPE)) return null;
        try { return new Header(nbt.getCompound("header")); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    @Override public Text getName(ItemStack stack) {
        return Text.translatable(EnumItemSnapshotType.getFromStack(stack).snapshotType == EnumSnapshotType.BLUEPRINT
            ? "item.blueprintItem.name" : "item.templateItem.name");
    }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        Header header = getHeader(stack);
        if (header == null) tooltip.add(Text.translatable("item.blueprint.blank"));
        else {
            tooltip.add(Text.literal(header.name));
            var owner = world == null ? null : header.getOwnerPlayer(world);
            if (owner != null) tooltip.add(Text.translatable("item.blueprint.author").append(" ").append(owner.getName()));
            if (context.isAdvanced()) {
                tooltip.add(Text.literal("Hash: " + header.key));
                tooltip.add(Text.literal("Date: " + header.created));
                tooltip.add(Text.literal("Owner UUID: " + header.owner));
            }
        }
        tooltip.add(Text.literal("Remake by FelixMantra"));
    }
    public enum EnumItemSnapshotType implements StringIdentifiable {
        TEMPLATE_CLEAN(EnumSnapshotType.TEMPLATE, false), TEMPLATE_USED(EnumSnapshotType.TEMPLATE, true),
        BLUEPRINT_CLEAN(EnumSnapshotType.BLUEPRINT, false), BLUEPRINT_USED(EnumSnapshotType.BLUEPRINT, true);
        public final EnumSnapshotType snapshotType;
        public final boolean used;
        EnumItemSnapshotType(EnumSnapshotType type, boolean used) { this.snapshotType = type; this.used = used; }
        public String getName() { return name().toLowerCase(Locale.ROOT); }
        @Override public String asString() { return getName(); }
        public static EnumItemSnapshotType get(EnumSnapshotType type, boolean used) {
            return type == EnumSnapshotType.BLUEPRINT ? used ? BLUEPRINT_USED : BLUEPRINT_CLEAN : used ? TEMPLATE_USED : TEMPLATE_CLEAN;
        }
        public static EnumItemSnapshotType getFromStack(ItemStack stack) {
            NbtCompound nbt = stack.getNbt();
            boolean header = nbt != null && nbt.contains("header", NbtElement.COMPOUND_TYPE);
            if (nbt != null && nbt.contains("snapshotType", NbtElement.STRING_TYPE)) {
                try { return get(EnumSnapshotType.valueOf(nbt.getString("snapshotType")), header); }
                catch (IllegalArgumentException invalid) { return TEMPLATE_CLEAN; }
            }
            var legacy = values()[Math.floorMod(stack.getDamage(), values().length)];
            return get(legacy.snapshotType, legacy.used || header);
        }
    }
}
