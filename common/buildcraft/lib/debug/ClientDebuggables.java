/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.debug;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.item.ItemDebugger;

public class ClientDebuggables {
    public static final List<String> SERVER_LEFT = new ArrayList<>();
    public static final List<String> SERVER_RIGHT = new ArrayList<>();
    private static BlockPos requestedPos;
    private static net.minecraft.util.Identifier requestedDimension;
    private static int ticks;

    public static void clear() {
        requestedPos = null; requestedDimension = null; ticks = 0;
        SERVER_LEFT.clear(); SERVER_RIGHT.clear();
    }

    public static void tick(MinecraftClient client) {
        if (getDebuggableObject(client.crosshairTarget) == null || client.getNetworkHandler() == null
            || !(client.crosshairTarget instanceof BlockHitResult hit)) { clear(); return; }
        var dimension = client.world.getRegistryKey().getValue();
        if (!hit.getBlockPos().equals(requestedPos) || !dimension.equals(requestedDimension)) {
            clear(); requestedPos = hit.getBlockPos().toImmutable(); requestedDimension = dimension;
        }
        if (ticks++ % 5 == 0) buildcraft.lib.net.MessageManager.sendToServer(
            new buildcraft.lib.net.MessageDebugRequest(dimension, requestedPos, hit.getSide()));
    }

    public static void accept(buildcraft.lib.net.MessageDebugResponse response) {
        if (!response.pos().equals(requestedPos) || !response.dimension().equals(requestedDimension)) return;
        SERVER_LEFT.clear(); SERVER_LEFT.addAll(response.left());
        SERVER_RIGHT.clear(); SERVER_RIGHT.addAll(response.right());
    }

    public static List<String> appendDebugText(List<String> vanilla, boolean rightSide) {
        MinecraftClient client = MinecraftClient.getInstance();
        IDebuggable debug = getDebuggableObject(client.crosshairTarget);
        if (debug == null) return vanilla;
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        debug.getClientDebugInfo(left, right, client.crosshairTarget instanceof BlockHitResult hit ? hit.getSide() : null);
        if (client.world.getRegistryKey().getValue().equals(requestedDimension)
            && client.crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(requestedPos)) {
            left.addAll(SERVER_LEFT); right.addAll(SERVER_RIGHT);
        }
        List<String> extra = rightSide ? right : left;
        if (extra.isEmpty()) return vanilla;
        List<String> result = new ArrayList<>(vanilla); result.add(""); result.add("BuildCraft"); result.addAll(extra);
        return result;
    }

    @Nullable
    public static IDebuggable getDebuggableObject(HitResult mouseOver) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.hasReducedDebugInfo() ||
            !mc.options.debugEnabled ||
            !ItemDebugger.isShowDebugInfo(mc.player)) {
            return null;
        }
        if (mouseOver == null) {
            return null;
        }
        ClientWorld world = mc.world;
        if (world == null) {
            return null;
        }
        if (mouseOver instanceof BlockHitResult && mouseOver.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) mouseOver).getBlockPos();
            BlockEntity tile = world.getBlockEntity(pos);
            if (tile instanceof IDebuggable) {
                return (IDebuggable) tile;
            }
        } else if (mouseOver instanceof EntityHitResult) {
            Entity entity = ((EntityHitResult) mouseOver).getEntity();
            if (entity instanceof IDebuggable) {
                return (IDebuggable) entity;
            }
        }
        return null;
    }
}
