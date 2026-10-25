/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.container;

import java.io.IOException;
import java.util.stream.IntStream;

import net.minecraft.entity.player.PlayerEntity;


import net.fabricmc.api.EnvType;

import buildcraft.api.filler.IFillerPattern;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.FullStatement;

import buildcraft.builders.filler.FillerType;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;

public interface IContainerFilling {
    PlayerEntity getPlayer();

    void sendMessage(int id, IPayloadWriter writer);

    FullStatement<IFillerPattern> getPatternStatementClient();

    FullStatement<IFillerPattern> getPatternStatement();

    boolean isInverted();

    void setInverted(boolean value);

    default boolean isLocked() {
        return false;
    }

    void valuesChanged();

    default void init() {
        if (!getPlayer().getWorld().isClient) {
            MessageUtil.doDelayedServer(getPlayer().getServer(), () -> {
                if (getPlayer().currentScreenHandler == this) sendData();
            });
        }
    }

    default void sendData() {
        sendMessage(ContainerBC_Neptune.NET_DATA, buffer -> {
            (getPlayer().getWorld().isClient
                ? getPatternStatementClient()
                : getPatternStatement()).writeToBuffer(buffer);
            buffer.writeBoolean(isInverted());
        });
    }

    default void onStatementChange() {
        sendData();
    }

    default void sendInverted(boolean value) {
        setInverted(value);
        sendData();
    }

    default void readMessage(int id, PacketBufferBC buffer, boolean isClient, Object ctx) throws IOException {
        if (id != ContainerBC_Neptune.NET_DATA) return;
        FullStatement<IFillerPattern> incoming = new FullStatement<>(FillerType.INSTANCE, 4, null);
        incoming.readFromBuffer(buffer);
        boolean inverted = buffer.readBoolean();
        if (buffer.isReadable()) throw new IOException("Trailing filler settings data");
        if (!isClient && isLocked()) {
            sendData();
            return;
        }
        // Decode the entire message before changing machine settings; normalize stale or invalid parameters.
        FullStatement<IFillerPattern> target = getPatternStatement();
        target.set(incoming.get());
        for (int i = 0; i < 4; i++) {
            var parameter = incoming.get(i);
            if (target.canSet(i, parameter)) target.set(i, parameter);
        }
        setInverted(inverted);
        if (isClient) {
            getPatternStatementClient().set(target.get(), target.getParameters());
        }
        valuesChanged();
        if (!isClient) sendData();
    }
}
