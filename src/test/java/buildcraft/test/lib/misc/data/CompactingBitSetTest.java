/*
 * Copyright (c) 2026 FelixMantra
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * https://mozilla.org/MPL/2.0/
 */
package buildcraft.test.lib.misc.data;

import java.util.Random;

import org.junit.Test;

import buildcraft.lib.misc.data.CompactingBitSet;
import buildcraft.lib.misc.data.DecompactingBitSet;

import static org.junit.Assert.*;

public class CompactingBitSetTest {
    @Test
    public void preservesHistoricalWireBitOrderAcrossByteBoundary() {
        CompactingBitSet writer = new CompactingBitSet(3);
        writer.append(5);
        writer.append(2);
        writer.append(7);
        assertArrayEquals(new byte[] {(byte) 0xd5, 1}, writer.getBytes());

        DecompactingBitSet reader = new DecompactingBitSet(3, new byte[] {(byte) 0xd5, 1});
        assertEquals(5, reader.next());
        assertEquals(2, reader.next());
        assertEquals(7, reader.next());
    }

    @Test
    public void preservesFullWidthSignedIntegers() {
        CompactingBitSet writer = new CompactingBitSet(32);
        int[] values = {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 0x12345678};
        for (int value : values) writer.append(value);
        DecompactingBitSet reader = new DecompactingBitSet(32, writer.getBytes());
        for (int value : values) assertEquals(value, reader.next());
    }

    @Test
    public void roundTripsEverySupportedWidth() {
        Random random = new Random(4754);
        for (int width = 1; width <= 32; width++) {
            int mask = width == 32 ? -1 : (int) ((1L << width) - 1);
            CompactingBitSet writer = new CompactingBitSet(width);
            int[] values = new int[257];
            writer.ensureCapacityValues(values.length);
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextInt() & mask;
                writer.append(values[i]);
            }
            assertEquals((values.length * width + 7) / 8, writer.getBytes().length);
            DecompactingBitSet reader = new DecompactingBitSet(width, writer.getBytes());
            for (int value : values) assertEquals("width=" + width, value, reader.next());
        }
    }

    @Test
    public void returnedBytesAreIndependentSnapshots() {
        CompactingBitSet writer = new CompactingBitSet(1);
        assertArrayEquals(new byte[0], writer.getBytes());
        writer.append(1);
        byte[] first = writer.getBytes();
        writer.append(1);
        assertArrayEquals(new byte[] {1}, first);
        first[0] = 0;
        assertArrayEquals(new byte[] {3}, writer.getBytes());
    }

    @Test
    public void readerOwnsItsInputSnapshot() {
        byte[] source = {(byte) 0xff};
        DecompactingBitSet reader = new DecompactingBitSet(8, source);
        source[0] = 0;
        assertEquals(255, reader.next());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void truncatedInputDoesNotSilentlyDecodeAsZero() {
        new DecompactingBitSet(9, new byte[] {0}).next();
    }
}
