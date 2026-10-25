/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import buildcraft.api.schematics.ISchematicBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-side blueprint execution using the same bounded work queue as the filler. */
public class BlueprintBuilder extends SnapshotBuilder<ITileForBlueprintBuilder> {
    public record Material(ItemStack stack, long count) {}
    private final Map<ItemVariant, Long> remainingItems = new LinkedHashMap<>();
    private boolean[] needed = new boolean[0];
    public BlueprintBuilder(ITileForBlueprintBuilder tile) { super(tile); }
    @Override protected Blueprint.BuildingInfo getBuildingInfo() { return tile.getBlueprintBuildingInfo(); }

    private int index(BlockPos pos) {
        return getBuildingInfo().getSnapshot().posToIndex(getBuildingInfo().fromWorld(pos));
    }
    private ISchematicBlock schematic(BlockPos pos) {
        var info = getBuildingInfo();
        return info == null || !info.box.contains(pos) ? null
            : info.rotatedPalette.get(info.getSnapshot().data[index(pos)]);
    }
    @Override protected boolean isAir(BlockPos pos) {
        var schematic = schematic(pos);
        return schematic == null || schematic.isAir();
    }
    @Override protected boolean isBlockCorrect(BlockPos pos) {
        var schematic = schematic(pos);
        return schematic != null && schematic.isBuilt(workWorld(), pos);
    }
    @Override protected boolean canPlace(BlockPos pos) {
        var schematic = schematic(pos);
        return schematic != null && !schematic.isAir() && schematic.canBuild(workWorld(), pos);
    }
    @Override protected boolean isReadyToPlace(BlockPos pos) {
        var schematic = schematic(pos);
        if (schematic == null) return false;
        for (BlockPos offset : schematic.getRequiredBlockOffsets()) {
            BlockPos required = pos.add(offset);
            if (!workWorld().isInBuildLimit(required) || !workWorld().isChunkLoaded(required)) return false;
            var dependency = schematic(required);
            if (dependency != null && !dependency.isBuilt(workWorld(), required)) return false;
        }
        return schematic.isReadyToBuild(workWorld(), pos);
    }
    @Override protected boolean hasEnoughToPlaceItems(BlockPos pos) {
        var info = getBuildingInfo();
        int index = index(pos);
        return BlueprintMaterials.available(tile.getResourceStorage(), tile.getTankManager(),
            info.toPlaceRequiredItems[index], info.toPlaceRequiredFluids[index]);
    }
    @Override protected boolean executePlacement(BlockPos pos) {
        var info = getBuildingInfo();
        int index = index(pos);
        return BlueprintMaterials.place(tile.getResourceStorage(), tile.getTankManager(),
            info.toPlaceRequiredItems[index], info.toPlaceRequiredFluids[index],
            () -> {
                if (!(workWorld() instanceof net.minecraft.server.world.ServerWorld world)
                    || !canPlace(pos) || !isReadyToPlace(pos)) return false;
                var player=buildcraft.lib.misc.FakePlayerProvider.INSTANCE.getFakePlayer(world,workOwner(),workOrigin());
                if (!world.canPlayerModifyAt(player,pos)) return false;
                var before=world.getBlockState(pos);
                var held=player.getMainHandStack();
                var required=info.toPlaceRequiredItems[index];
                var hand=required==null || required.isEmpty()?ItemStack.EMPTY:required.get(0).copy();
                if (!hand.isEmpty()) hand.setCount(1);
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,hand);
                try {
                    var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofBottomCenter(pos),
                        net.minecraft.util.math.Direction.UP,pos,false);
                    var result=net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player,world,net.minecraft.util.Hand.MAIN_HAND,hit);
                    if (result==net.minecraft.util.ActionResult.FAIL) return false;
                    if (getBuildingInfo()!=info || !world.isChunkLoaded(pos) || !world.canPlayerModifyAt(player,pos)) return false;
                    if (result.isAccepted()) return schematic(pos).isBuilt(world,pos);
                    return world.getBlockState(pos)==before && canPlace(pos) && isReadyToPlace(pos) && schematic(pos).build(world,pos);
                } finally {player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,held);}
            });
    }
    @Override public ItemStack previewItem(BlockPos pos) {
        var info=getBuildingInfo();
        if (info==null || !info.box.contains(pos)) return ItemStack.EMPTY;
        var required=info.toPlaceRequiredItems[index(pos)];
        return required==null?ItemStack.EMPTY:required.stream().filter(stack -> !stack.isEmpty())
            .findFirst().map(stack -> new ItemStack(stack.getItem())).orElse(ItemStack.EMPTY);
    }
    @Override protected boolean additionalWorkFinished() {
        // Entity reconstruction is not ported yet: never silently report a partial blueprint as finished.
        return getBuildingInfo() != null && getBuildingInfo().entities.isEmpty();
    }
    public int pendingEntities() { return getBuildingInfo() == null ? 0 : getBuildingInfo().entities.size(); }
    @Override protected void workReset() {
        remainingItems.clear();
        needed = new boolean[workSize()];
        Arrays.fill(needed, true);
        for (int i = 0; i < needed.length; i++) changeMaterials(i, 1);
    }
    @Override protected void workStateChanged(int index, boolean correct) {
        if (needed[index] == !correct) return;
        needed[index] = !correct;
        changeMaterials(index, correct ? -1 : 1);
    }
    private void changeMaterials(int index, int direction) {
        var stacks = getBuildingInfo().toPlaceRequiredItems[index];
        if (stacks == null) return;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            var variant = ItemVariant.of(stack);
            long count = remainingItems.getOrDefault(variant, 0L) + (long) direction * stack.getCount();
            if (count <= 0) remainingItems.remove(variant);
            else remainingItems.put(variant, count);
        }
    }
    /** Bounded UI view; these are display entries, never inventory slots. */
    public List<Material> getRemainingMaterials(int limit) {
        List<Material> result = new ArrayList<>();
        for (var entry : remainingItems.entrySet()) {
            if (result.size() >= limit) break;
            result.add(new Material(entry.getKey().toStack(), entry.getValue()));
        }
        return List.copyOf(result);
    }
}
