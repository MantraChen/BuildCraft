package buildcraft.test.lib.net;

import buildcraft.robotics.zone.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZoneMapPacketTest {
    @Test public void mapPreservesNegativeAndZeroHeightsSeparatelyFromMissingCells() {
        var key = new ZonePlannerMapChunkKey(new ChunkPos(-3, 7), new Identifier("test", "custom_dimension"), -1);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            key.write(buffer);
            for (int i = 0; i < 256; i++) {
                buffer.writeBoolean(i < 3);
                if (i < 3) { buffer.writeInt(i == 0 ? -64 : i == 1 ? 0 : 319); buffer.writeInt(0x123456); }
            }
            var response = MessageZoneMapResponse.TYPE.read(buffer);
            assertEquals(key, response.key());
            assertEquals(-64, response.data().getData(0, 0).posY);
            assertEquals(0, response.data().getData(0, 1).posY);
            assertEquals(319, response.data().getData(0, 2).posY);
            assertNull(response.data().getData(0, 3));
            buffer.clear(); response.write(buffer);
            var copy = MessageZoneMapResponse.TYPE.read(buffer);
            assertEquals(0x123456, copy.data().getColour(0, 0));
            assertEquals(-64, copy.data().getData(0, 0).posY);
        } finally { buffer.release(); }
    }
    @Test public void dimensionsArePartOfIdentityAndCoordinatesAreBounded() {
        var key = new ZonePlannerMapChunkKey(new ChunkPos(1, 2), new Identifier("minecraft", "overworld"), 0);
        assertNotEquals(key, new ZonePlannerMapChunkKey(new ChunkPos(1, 2), new Identifier("minecraft", "the_nether"), 0));
        assertThrows(IllegalArgumentException.class, () -> new ZonePlannerMapChunkKey(new ChunkPos(Integer.MIN_VALUE, 0), key.dimension, 0));
        assertThrows(IllegalArgumentException.class, () -> new ZonePlannerMapChunkKey(key.chunkPos, key.dimension, Integer.MAX_VALUE));
    }
}
