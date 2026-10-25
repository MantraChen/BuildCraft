/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

import net.fabricmc.fabric.api.entity.FakePlayer;

import buildcraft.api.template.TemplateApi;
import buildcraft.lib.misc.FakePlayerProvider;

public class TemplateBuilder extends SnapshotBuilder<ITileForTemplateBuilder> {
    public TemplateBuilder(ITileForTemplateBuilder tile) {
        super(tile);
    }

    @Override
    protected Template.BuildingInfo getBuildingInfo() {
        return tile.getTemplateBuildingInfo();
    }

    @Override
    protected boolean isAir(BlockPos blockPos) {
        return !getBuildingInfo().box.contains(blockPos) ||
            !getBuildingInfo().getSnapshot().data.get(
                getBuildingInfo().getSnapshot().posToIndex(
                    getBuildingInfo().fromWorld(blockPos)
                )
            );
    }

    @Override
    protected boolean canPlace(BlockPos blockPos) {
        return tile.getWorldBC().isAir(blockPos);
    }

    @Override
    protected boolean isReadyToPlace(BlockPos blockPos) {
        return true;
    }

    @Override
    protected boolean hasEnoughToPlaceItems(BlockPos blockPos) {
        return !tile.getInvResources().extract(stack -> stack.getItem() instanceof net.minecraft.item.BlockItem, 1, 1, true).isEmpty();
    }

    protected List<ItemStack> getToPlaceItems(BlockPos blockPos) {
        ItemStack stack = tile.getInvResources().extract(value -> value.getItem() instanceof net.minecraft.item.BlockItem, 1, 1, false);
        return stack.isEmpty() ? null : Collections.singletonList(stack);
    }

    protected boolean doPlaceTask(PlaceTask placeTask) {
        FakePlayer fakePlayer = FakePlayerProvider.INSTANCE.getFakePlayer(
            (ServerWorld) tile.getWorldBC(),
            tile.getOwner(),
            tile.getBuilderPos()
        );
        ItemStack previous = fakePlayer.getMainHandStack();
        fakePlayer.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, placeTask.required.get(0));
        try {
            boolean placed = TemplateApi.templateRegistry.handle(tile.getWorldBC(), placeTask.pos,
                fakePlayer, placeTask.required.get(0));
            // Handlers may replace the held stack with a container remainder.
            placeTask.required.set(0, fakePlayer.getMainHandStack());
            if (placed) acceptDrops(placeTask.required);
            return placed;
        } finally {
            fakePlayer.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, previous);
        }
    }

    @Override
    protected boolean executePlacement(BlockPos pos) {
        List<ItemStack> items = getToPlaceItems(pos);
        if (items == null) return false;
        PlaceTask task = new PlaceTask(pos, items);
        boolean placed = doPlaceTask(task);
        if (!placed) acceptDrops(task.required);
        return placed;
    }

    @Override
    protected boolean isBlockCorrect(BlockPos blockPos) {
        return !isAir(blockPos) && !tile.getWorldBC().isAir(blockPos);
    }
}
