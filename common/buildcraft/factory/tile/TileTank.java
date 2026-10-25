/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.tile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.lib.fluid.Tank;
import buildcraft.lib.fluid.FluidTankColumn;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.TickingBlockEntity;

public class TileTank extends TileBC_Neptune implements TickingBlockEntity, IDebuggable {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("tank");
    public static final int NET_FLUID_DELTA = IDS.allocId("FLUID_DELTA");
    public final Tank tank;
    private int lastComparatorLevel = -1;
    private boolean contentsDirty = true;

    public TileTank(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tank = tankManager.add(new Tank("tank", 16000, this));
    }
    @Override public IdAllocator getIdAllocator() { return IDS; }
    public int getComparatorLevel() {
        long amount = tank.getStorage().getAmount(), capacity = tank.getStorage().getCapacity();
        return amount == 0 ? 0 : 1 + (int) (14 * amount / capacity);
    }
    @Override public void markDirty() { super.markDirty(); contentsDirty = true; }
    @Override public void onNeighbourBlockChanged(net.minecraft.block.Block block, BlockPos neighbor) {
        super.onNeighbourBlockChanged(block, neighbor); contentsDirty = true;
    }
    @Override public void onLoad() { super.onLoad(); contentsDirty = true; }
    @Override public boolean onActivated(PlayerEntity player, Hand hand, Direction facing, float x, float y, float z) {
        if (!canInteractWith(player)) return false;
        long before=tank.getStorage().getAmount();
        if (super.onActivated(player, hand, facing, x, y, z)) {
            if (!world.isClient) {
                if(tank.getStorage().getAmount()>before)buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(player,new net.minecraft.util.Identifier("buildcraftfactory","fluid_storage"));
                balanceTankFluids();
            }
            return true;
        }
        if (!world.isClient) buildcraft.factory.BCFactoryScreens.openTank(player, this);
        return true;
    }
    public void balanceTankFluids() {
        if (world == null || world.isClient || isRemoved()) return;
        BlockPos bottom = pos;
        while (world.isInBuildLimit(bottom.down()) && world.getBlockEntity(bottom.down()) instanceof TileTank) bottom = bottom.down();
        List<TileTank> column = new ArrayList<>();
        for (BlockPos scan = bottom; world.isInBuildLimit(scan) && world.getBlockEntity(scan) instanceof TileTank other; scan = scan.up()) {
            column.add(other);
        }
        FluidTankColumn.settle(column.stream().map(tile -> tile.tank.getStorage()).toList());
        for (TileTank tile : column) tile.updateContents();
    }
    private void updateContents() {
        if (!contentsDirty) return;
        contentsDirty = false;
        int comparator = getComparatorLevel();
        if (comparator != lastComparatorLevel) {
            lastComparatorLevel = comparator;
            world.updateComparators(pos, getCachedState().getBlock());
        }
        sendNetworkUpdate(NET_RENDER_DATA);
    }
    @Override public void tick() {
        if (world != null && !world.isClient && contentsDirty) balanceTankFluids();
    }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER && id == NET_RENDER_DATA) tank.writeToBuffer(buffer);
    }
    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side == NetSide.CLIENT && id == NET_RENDER_DATA) tank.readFromBuffer(buffer);
    }
    @Override public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("fluid = " + tank.getDebugString());
    }
}
