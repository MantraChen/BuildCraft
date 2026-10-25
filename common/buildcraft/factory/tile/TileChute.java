/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.factory.tile;

import java.util.List;
import java.util.HashSet;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.Box;
import buildcraft.api.core.EnumPipePart;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilityHelper;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/** Native item collection and transactional output. Remake by FelixMantra. */
public class TileChute extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity, IDebuggable {
    private static final Identifier ADVANCEMENT_DID_INSERT = new Identifier("buildcraftfactory", "retired_hopper");

    private static final int PICKUP_MAX = 3;
    private static final int PICKUP_WORK = 100_000;
    private static final int GRAVITY_WORK = 1_000;

    public final ItemHandlerSimple inv = itemManager.addInvHandler("inv", 4, EnumAccess.INSERT, EnumPipePart.VALUES);
    private final Storage<ItemVariant> internalInventory = inv.asStorage(true, true);

    private final MjBattery battery = new MjBattery(1 * MjAPI.MJ);
    private int progress = 0;

    public TileChute(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        caps.addProvider(new MjCapabilityHelper(new MjBatteryReceiver(battery) {
            @Override public long receivePower(long amount, boolean simulate) {
                long remainder = super.receivePower(amount, simulate);
                if (!simulate && remainder < amount) markChunkDirty();
                return remainder;
            }
        }));
    }

    public static boolean hasInventoryAtPosition(BlockView world, BlockPos pos, Direction side) {
        return world instanceof net.minecraft.world.World level && level.isChunkLoaded(pos)
            && net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side.getOpposite()) != null;
    }

    private void pickupItems(Direction currentSide) {
        int remaining = PICKUP_MAX;
        Box mouth = switch (currentSide) {
            case DOWN -> new Box(0, -0.25, 0, 1, 0, 1);
            case UP -> new Box(0, 1, 0, 1, 1.25, 1);
            case NORTH -> new Box(0, 0, -0.25, 1, 1, 0);
            case SOUTH -> new Box(0, 0, 1, 1, 1, 1.25);
            case WEST -> new Box(-0.25, 0, 0, 0, 1, 1);
            case EAST -> new Box(1, 0, 0, 1.25, 1, 1);
        };
        for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, mouth.offset(pos), Entity::isAlive)) {
            // This view is used by one complete transfer. Simulation/rollback never changes
            // the entity; final commit publishes its remaining stack or removes it.
            var source = new SingleVariantStorage<ItemVariant>() {
                { variant = ItemVariant.of(entity.getStack()); amount = entity.getStack().getCount(); }
                @Override protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
                @Override protected long getCapacity(ItemVariant resource) { return resource.getItem().getMaxCount(); }
                @Override protected boolean canInsert(ItemVariant resource) { return false; }
                @Override public boolean supportsInsertion() { return false; }
                @Override protected void onFinalCommit() {
                    if (amount == 0) entity.discard();
                    else entity.setStack(variant.toStack((int) amount));
                }
            };
            remaining -= (int) StorageUtil.move(source, internalInventory, resource -> true, remaining, null);
            if (remaining == 0) break;
        }
    }

    private void putInNearInventories(Direction currentSide) {
        if (inv.stacks.stream().allMatch(stack -> stack.isEmpty())) return;
        Direction[] sides = Direction.values();
        for (int i = sides.length - 1; i > 0; i--) {
            int j = world.random.nextInt(i + 1);
            Direction swap = sides[i]; sides[i] = sides[j]; sides[j] = swap;
        }
        long moved = 0;
        for (Direction side : sides) if (side != currentSide && world.isChunkLoaded(pos.offset(side))) {
            var destination = ItemStorage.SIDED.find(world, pos.offset(side), side.getOpposite());
            moved += StorageUtil.move(internalInventory, destination, resource -> true, 1, null);
        }
        var visited = new HashSet<Entity>();
        for (Direction side : sides) if (side != currentSide && world.isChunkLoaded(pos.offset(side))) {
            for (Entity entity : world.getOtherEntities(null, new Box(pos.offset(side)),
                    entity -> entity.isAlive() && !(entity instanceof LivingEntity) && entity instanceof Inventory)) {
                // A large minecart/boat can overlap several faces; visit it once per tick.
                if (!visited.add(entity)) continue;
                moved += StorageUtil.move(internalInventory, InventoryStorage.of((Inventory) entity, side.getOpposite()),
                    resource -> true, 1, null);
            }
        }
        if (moved > 0 && hasOwner() && world instanceof net.minecraft.server.world.ServerWorld server) {
            var player = server.getServer().getPlayerManager().getPlayer(getOwner().getId());
            if (player != null) buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_DID_INSERT);
        }
    }

    /** Compatibility factory; native registration also delegates through TickingBlockEntity. */
    public static <T extends TileChute> BlockEntityTicker<T> ticker() {
        return (world, pos, state, be) -> be.tick();
    }

    public void tick() {
        if (world == null || world.isClient || isRemoved()
            || !(world.getBlockState(pos).getBlock() instanceof buildcraft.factory.block.BlockChute)) {
            return;
        }

        long energyBefore = battery.getStored();
        int workBefore = progress;
        battery.tick(getWorld(), getPos());

        Direction currentSide = world.getBlockState(pos).get(BlockBCBase_Neptune.BLOCK_FACING_6);

        if (currentSide == Direction.UP) {
            progress = Math.min(PICKUP_WORK, progress + GRAVITY_WORK);
        }
        progress += (int) battery.extractPower(0, PICKUP_WORK - progress);

        if (progress >= PICKUP_WORK) {
            progress = 0;
            pickupItems(currentSide);
        }

        putInNearInventories(currentSide);
        if (workBefore != progress || energyBefore != battery.getStored()) markChunkDirty();
    }

    @Override
    public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        progress = Math.max(0, Math.min(PICKUP_WORK, nbt.getInt("progress")));
        battery.readFromNbt(nbt.getCompound("battery"));
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("progress", progress);
        nbt.put("battery", battery.writeToNbt());
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("progress = " + progress);
    }
}
