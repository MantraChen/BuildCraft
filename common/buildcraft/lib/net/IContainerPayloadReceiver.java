package buildcraft.lib.net;

import java.io.IOException;

/** Implemented by screen handlers that accept BuildCraft widget messages. */
public interface IContainerPayloadReceiver {
    void readMessage(int id, PacketBufferBC buffer, boolean isClient, Object context) throws IOException;
}
