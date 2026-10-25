/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import buildcraft.api.core.IFakePlayerProvider;

/** Uses Fabric's connection-safe fake players and weak-value world/profile cache. */
public enum FakePlayerProvider implements IFakePlayerProvider {
    INSTANCE;

    /** The default {@link GameProfile} to use if a tile entity cannot determine its real owner. */
    @Deprecated
    public static final GameProfile NULL_PROFILE;

    static {
        UUID id = UUID.nameUUIDFromBytes("buildcraft.core".getBytes(StandardCharsets.UTF_8));
        NULL_PROFILE = new GameProfile(id, "[BuildCraft]");
    }

    @Override
    public FakePlayer getBuildCraftPlayer(ServerWorld world) {
        return getFakePlayer(world, NULL_PROFILE);
    }

    @Override
    public FakePlayer getFakePlayer(ServerWorld world, GameProfile owner) {
        GameProfile profile = owner == null || owner.getId() == null ? NULL_PROFILE : owner;
        // Minecraft probes legacy statistics filenames using the profile name. Keep it
        // filesystem-safe on Windows while retaining the real owner's UUID for protection mods.
        GameProfile machineProfile = new GameProfile(profile.getId(), "[BuildCraft]");
        FakePlayer player = FakePlayer.get(world, machineProfile);
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    @Override
    public FakePlayer getFakePlayer(ServerWorld world, GameProfile owner, BlockPos pos) {
        FakePlayer player = getFakePlayer(world, owner);
        player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        return player;
    }
}
