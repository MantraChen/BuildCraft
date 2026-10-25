package buildcraft.test.lib.builders;

import java.util.BitSet;
import buildcraft.builders.snapshot.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.Test;
import static org.junit.Assert.*;

public class SnapshotPacketTest {
    private Template template() {
        Template value = new Template(); value.size = new BlockPos(2, 1, 1); value.offset = BlockPos.ORIGIN;
        value.facing = Direction.NORTH; value.data = new BitSet(2); value.data.set(1); value.computeKey(); return value;
    }
    @Test public void responseRoundTripsAndUnavailableReplyIsExplicit() throws Exception {
        Template value = template();
        for (Snapshot content : new Snapshot[] { value, null }) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                MessageSnapshotResponse.of(value.key, content).write(buffer);
                var decoded = MessageSnapshotResponse.TYPE.read(buffer);
                assertEquals(value.key, decoded.key);
                if (content == null) assertNull(decoded.snapshot());
                else assertEquals(value.data, ((Template) decoded.snapshot()).data);
            } finally { buffer.release(); }
        }
    }
    @Test public void changedContentAndInvalidPacketLengthsAreRejected() throws Exception {
        Template value = template();
        NbtCompound tag = Snapshot.writeToNBT(value); tag.putString("facing", "SOUTH");
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeByteArray(value.key.hash); buffer.writeNbt(tag);
            var decoded = MessageSnapshotResponse.TYPE.read(buffer);
            assertThrows(IllegalArgumentException.class, decoded::snapshot);
            buffer.clear(); buffer.writeByteArray(new byte[33]); buffer.writeNbt(null);
            assertThrows(RuntimeException.class, () -> MessageSnapshotResponse.TYPE.read(buffer));
            buffer.clear(); MessageSnapshotResponse.of(value.key, value).write(buffer); buffer.writeByte(1);
            assertThrows(RuntimeException.class, () -> MessageSnapshotResponse.TYPE.read(buffer));
        } finally { buffer.release(); }
    }
}
