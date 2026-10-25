/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.core.item;

import java.util.*;
import buildcraft.api.core.*;
import buildcraft.api.items.IMapLocation;
import buildcraft.lib.item.ItemBC_Neptune;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;
import buildcraft.robotics.zone.ZonePlan;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Native reusable location records, retaining the original metadata and NBT format. */
public class ItemMapLocation extends ItemBC_Neptune implements IMapLocation {
    public static final int MAX_PATH_POINTS = 4096;
    private static final String[] STORAGE_TAGS = "x,y,z,side,xMin,xMax,yMin,yMax,zMin,zMax,path,chunkMapping,dimension".split(",");
    public ItemMapLocation(String id) { super(new Settings().maxCount(16), id); }
    public static boolean isRecorded(ItemStack stack) {
        return stack.getItem() instanceof ItemMapLocation && (MapLocationType.getFromStack(stack) != MapLocationType.CLEAN
            || stack.hasNbt() && Arrays.stream(STORAGE_TAGS).anyMatch(stack.getNbt()::contains));
    }
    private static NbtCompound data(ItemStack stack) { return stack.hasNbt() ? stack.getNbt() : new NbtCompound(); }
    private static void clearData(ItemStack stack, boolean name) {
        for (var key : STORAGE_TAGS) stack.removeSubNbt(key);
        stack.removeSubNbt("Damage");
        if (name) stack.removeSubNbt("name");
    }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        var stack = player.getStackInHand(hand);
        if (!player.isSneaking() || player.isSpectator() || Transaction.getCurrentUnsafe() != null) return TypedActionResult.pass(stack);
        if (!world.isClient) { clearData(stack, true); player.getInventory().markDirty(); }
        return TypedActionResult.success(stack, world.isClient);
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        return useFirst(context.getPlayer(), context.getWorld(), context.getHand(),
            new BlockHitResult(context.getHitPos(), context.getSide(), context.getBlockPos(), false));
    }
    /** Runs after Fabric protection callbacks and before the clicked block's interaction. */
    public static ActionResult useFirst(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (player == null || !(player.getStackInHand(hand).getItem() instanceof ItemMapLocation)) return ActionResult.PASS;
        var stack = player.getStackInHand(hand); var pos = hit.getBlockPos();
        if (player.isSpectator() || !player.canModifyBlocks() || player.getItemCooldownManager().isCoolingDown(stack.getItem())
            || !world.isChunkLoaded(pos) || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)
            || !world.canPlayerModifyAt(player, pos) || Transaction.getCurrentUnsafe() != null) return ActionResult.FAIL;
        if (!player.isSneaking() && isRecorded(stack)) return ActionResult.FAIL;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player.isSneaking()) { clearData(stack, true); player.getInventory().markDirty(); return ActionResult.SUCCESS; }
        var original = stack.copy(); var recorded = stack.copyWithCount(1);
        clearData(recorded, false); var nbt = recorded.getOrCreateNbt(); var tile = world.getBlockEntity(pos);
        if (tile instanceof IPathProvider provider) {
            var path = provider.getPath();
            if (path == null || path.isEmpty() || path.size() > MAX_PATH_POINTS || path.stream().anyMatch(p -> !validPosition(p))) return ActionResult.FAIL;
            path = List.copyOf(path);
            var list = new NbtList(); path.forEach(p -> list.add(NBTUtilBC.writeBlockPos(p))); nbt.put("path", list);
            (path.size() > 1 && path.get(0).equals(path.get(path.size() - 1)) ? MapLocationType.PATH_REPEATING : MapLocationType.PATH).setToStack(recorded);
        } else if (tile instanceof IAreaProvider provider) {
            var min = provider.min(); var max = provider.max();
            if (!validPosition(min) || !validPosition(max) || min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) return ActionResult.FAIL;
            nbt.putInt("xMin", min.getX()); nbt.putInt("yMin", min.getY()); nbt.putInt("zMin", min.getZ());
            nbt.putInt("xMax", max.getX()); nbt.putInt("yMax", max.getY()); nbt.putInt("zMax", max.getZ());
            MapLocationType.AREA.setToStack(recorded);
        } else {
            nbt.putInt("x", pos.getX()); nbt.putInt("y", pos.getY()); nbt.putInt("z", pos.getZ()); nbt.putByte("side", (byte) hit.getSide().getId());
            MapLocationType.SPOT.setToStack(recorded);
        }
        nbt.putString("dimension", world.getRegistryKey().getValue().toString());
        if (!ItemStack.areEqual(original, player.getStackInHand(hand))) return ActionResult.FAIL;
        stack.decrement(1);
        if (stack.isEmpty()) player.setStackInHand(hand, recorded); else player.getInventory().offerOrDrop(recorded);
        player.getInventory().markDirty(); return ActionResult.SUCCESS;
    }
    private static boolean validPosition(BlockPos p) {
        return p != null && Math.abs((long) p.getX()) <= 30_000_000 && Math.abs((long) p.getZ()) <= 30_000_000 && p.getY() >= -2032 && p.getY() <= 2031;
    }
    private static BlockPos position(NbtCompound tag, String suffix) {
        if (!tag.contains("x" + suffix, NbtElement.INT_TYPE) || !tag.contains("y" + suffix, NbtElement.INT_TYPE) || !tag.contains("z" + suffix, NbtElement.INT_TYPE)) return null;
        var p = new BlockPos(tag.getInt("x" + suffix), tag.getInt("y" + suffix), tag.getInt("z" + suffix));
        return validPosition(p) ? p : null;
    }
    public static Identifier getDimension(ItemStack stack) { return Identifier.tryParse(data(stack).getString("dimension")); }
    public static IBox getAreaBox(ItemStack stack) {
        var tag = data(stack); var min = position(tag, "Min"); var max = position(tag, "Max");
        if (min == null || max == null || min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) return null;
        return new Box(min, max);
    }
    public static IBox getPointBox(ItemStack stack) {
        var pos = position(data(stack), ""); return pos == null ? null : new Box(pos, pos);
    }
    public static Direction getPointFace(ItemStack stack) {
        var tag = data(stack); int id = tag.getByte("side");
        return tag.contains("side", NbtElement.BYTE_TYPE) && id >= 0 && id < 6 ? Direction.byId(id) : null;
    }
    @Override public IBox getBox(ItemStack stack) { return switch (MapLocationType.getFromStack(stack)) {
        case AREA -> getAreaBox(stack); case SPOT -> getPointBox(stack); default -> null;
    }; }
    @Override public BlockPos getPoint(ItemStack stack) { return MapLocationType.getFromStack(stack) == MapLocationType.SPOT ? position(data(stack), "") : null; }
    @Override public Direction getPointSide(ItemStack stack) { return MapLocationType.getFromStack(stack) == MapLocationType.SPOT ? getPointFace(stack) : null; }
    @Override public IZone getZone(ItemStack stack) {
        if (MapLocationType.getFromStack(stack) != MapLocationType.ZONE) return getBox(stack);
        if (!data(stack).contains("chunkMapping", NbtElement.LIST_TYPE)) return null;
        try { var plan = new ZonePlan(); plan.readFromNBT(data(stack)); return plan; } catch (IllegalArgumentException invalid) { return null; }
    }
    @Override public List<BlockPos> getPath(ItemStack stack) {
        var type = MapLocationType.getFromStack(stack);
        if (type == MapLocationType.SPOT) { var pos = getPoint(stack); return pos == null ? null : List.of(pos); }
        if (type != MapLocationType.PATH && type != MapLocationType.PATH_REPEATING) return null;
        if (!(data(stack).get("path") instanceof NbtList list) || list.isEmpty() || list.size() > MAX_PATH_POINTS) return null;
        var result = new ArrayList<BlockPos>();
        for (var entry : list) {
            if (!(entry instanceof NbtIntArray a) || a.getIntArray().length != 3) return null;
            var p = NBTUtilBC.readBlockPos(entry); if (!validPosition(p)) return null; result.add(p);
        }
        if (type == MapLocationType.PATH_REPEATING && (result.size() < 2 || !result.get(0).equals(result.get(result.size() - 1)))) return null;
        return List.copyOf(result);
    }
    public static void setZone(ItemStack stack, ZonePlan plan) {
        var tag = new NbtCompound(); plan.writeToNBT(tag);
        var checked = new ZonePlan(); checked.readFromNBT(tag);
        clearData(stack, false); stack.getOrCreateNbt().put("chunkMapping", tag.get("chunkMapping")); MapLocationType.ZONE.setToStack(stack);
    }
    @Override public String getLabel(ItemStack stack) { return data(stack).getString("name"); }
    @Override public boolean setName(ItemStack stack, String name) { if (name == null || name.length() > 128) return false; stack.getOrCreateNbt().putString("name", name); return true; }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        var type = MapLocationType.getFromStack(stack);
        tooltip.add(Text.translatable("item.buildcraftcore.map_location.type." + type.name().toLowerCase(Locale.ROOT)));
        String name = getLabel(stack); if (!name.isEmpty()) tooltip.add(Text.literal(name));
        if (type == MapLocationType.SPOT) { var p = getPoint(stack); var side = getPointSide(stack); if (p != null && side != null) tooltip.add(Text.literal(p.toShortString() + " / " + side.getName())); }
        if (type == MapLocationType.AREA) { var box = getBox(stack); if (box != null) tooltip.add(Text.literal(box.min().toShortString() + " → " + box.max().toShortString())); }
        if (type == MapLocationType.PATH || type == MapLocationType.PATH_REPEATING) { var path = getPath(stack); if (path != null) tooltip.add(Text.translatable("item.buildcraftcore.map_location.points", path.size())); }
        var dimension = getDimension(stack); if (dimension != null) tooltip.add(Text.literal(dimension.toString()));
        tooltip.add(Text.translatable("item.buildcraftcore.map_location." + (isRecorded(stack) ? "clear" : "capture")));
        tooltip.add(Text.literal("Remake by FelixMantra"));
    }
}
