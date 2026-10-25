/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.io.IOException;

public interface IPayloadReceiver {
    MessageUpdateTile receivePayload(NetworkContext ctx, PacketBufferBC buffer) throws IOException;

    /** Client-to-server tile actions require an explicit permission check. */
    default boolean canReceivePayload(net.minecraft.entity.player.PlayerEntity player) { return false; }
}
