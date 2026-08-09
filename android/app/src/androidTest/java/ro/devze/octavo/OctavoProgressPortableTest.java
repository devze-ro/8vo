package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoProgressPortableTest {
    @Test
    public void canonicalWireHasExactBoundsAndExplicitSemantics()
        throws IOException {
        byte[] empty = OctavoProgressPortable.encode(
            new OctavoProgressPortable.Snapshot(
                Collections.emptyList()));
        assertEquals(20, empty.length);
        assertEquals(OctavoProgressPortable.minimumV1Bytes(),
                     empty.length);

        ArrayList<OctavoProgressPortable.Lane> lanes =
            new ArrayList<>();
        for (int index = 15; index >= 0; --index) {
            lanes.add(new OctavoProgressPortable.Lane(
                device(100 + index), Long.MAX_VALUE - index,
                choice(index % 4)));
        }
        byte[] bytes = OctavoProgressPortable.encode(
            new OctavoProgressPortable.Snapshot(lanes));
        assertEquals(724, bytes.length);
        assertEquals(44, OctavoProgressPortable.laneBytes());
        assertEquals(OctavoProgressPortable.maximumV1Bytes(),
                     bytes.length);
        assertEquals(OctavoProgressPortable.magicForTesting(),
                     readInt(bytes, 0));
        assertEquals(OctavoProgressPortable.versionForTesting(),
                     readInt(bytes, 4));
        assertEquals(1, readInt(bytes, 8));
        assertEquals(16, readInt(bytes, 12));

        OctavoProgressPortable.DecodeResult decoded =
            OctavoProgressPortable.decode(bytes);
        assertEquals(OctavoProgressPortable.DecodeStatus.READY,
                     decoded.status);
        assertEquals(device(100),
                     decoded.snapshot().lanes().get(0).deviceId);
        assertEquals(device(115),
                     decoded.snapshot().lanes().get(15).deviceId);
        assertArrayEquals(bytes,
                          OctavoProgressPortable.encode(
                              decoded.snapshot()));

        OctavoProgressDisplay[] displays = {
            OctavoProgressDisplay.CHAPTER,
            OctavoProgressDisplay.PAGE,
            OctavoProgressDisplay.LOCATION,
            OctavoProgressDisplay.PERCENTAGE
        };
        for (int index = 0; index < displays.length; ++index) {
            OctavoProgressPortable.Choice value =
                OctavoProgressPortable.Choice.fromDisplay(
                    displays[index]);
            assertEquals(index, value.semanticId);
            assertEquals(displays[index], value.toDisplay());
        }
    }

    @Test
    public void malformedFutureAndOverboundInputsAreDistinguished()
        throws IOException {
        byte[] valid = OctavoProgressPortable.simulatedRemoteBytes(
            device(1), 1, OctavoProgressDisplay.LOCATION);
        assertEquals(64, valid.length);

        byte[] checksum = valid.clone();
        checksum[checksum.length - 1] ^= 1;
        assertDecode(checksum,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] fieldCount = valid.clone();
        writeInt(fieldCount, 8, 2);
        repairChecksum(fieldCount);
        assertDecode(fieldCount,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] unknownChoice = valid.clone();
        writeInt(unknownChoice, 56, 4);
        repairChecksum(unknownChoice);
        assertDecode(unknownChoice,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] zeroSequence = valid.clone();
        writeLong(zeroSequence, 48, 0);
        repairChecksum(zeroSequence);
        assertDecode(zeroSequence,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] negativeCount = valid.clone();
        writeInt(negativeCount, 12, -1);
        repairChecksum(negativeCount);
        assertDecode(negativeCount,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] tooMany = valid.clone();
        writeInt(tooMany, 12, 17);
        repairChecksum(tooMany);
        assertDecode(tooMany,
                     OctavoProgressPortable.DecodeStatus.LIMIT);

        byte[] two = bytes(lane(1, 1, 0), lane(2, 1, 1));
        System.arraycopy(two, 16, two, 60, 32);
        repairChecksum(two);
        assertDecode(two,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] reversed = bytes(lane(1, 1, 0), lane(2, 1, 1));
        byte[] first = Arrays.copyOfRange(reversed, 16, 60);
        System.arraycopy(reversed, 60, reversed, 16, 44);
        System.arraycopy(first, 0, reversed, 60, 44);
        repairChecksum(reversed);
        assertDecode(reversed,
                     OctavoProgressPortable.DecodeStatus.INVALID);

        byte[] futureMinimum = futureBytes(8, 2);
        OctavoProgressPortable.DecodeResult future =
            OctavoProgressPortable.decode(futureMinimum);
        assertEquals(OctavoProgressPortable.DecodeStatus.FUTURE_VERSION,
                     future.status);
        assertArrayEquals(futureMinimum, future.preservedBytes());

        byte[] futureMaximum = futureBytes(65_536, 0x80000000);
        assertDecode(futureMaximum,
                     OctavoProgressPortable.DecodeStatus.FUTURE_VERSION);
        assertDecode(Arrays.copyOf(futureMaximum, 65_537),
                     OctavoProgressPortable.DecodeStatus.LIMIT);

        byte[] futureUnsignedMaximum = futureBytes(8, 0xffffffff);
        assertDecode(futureUnsignedMaximum,
                     OctavoProgressPortable.DecodeStatus.FUTURE_VERSION);

        byte[] v1Over = new byte[725];
        writeInt(v1Over, 0,
                 OctavoProgressPortable.magicForTesting());
        writeInt(v1Over, 4,
                 OctavoProgressPortable.versionForTesting());
        assertDecode(v1Over,
                     OctavoProgressPortable.DecodeStatus.LIMIT);
        assertDecode(null, OctavoProgressPortable.DecodeStatus.INVALID);
        assertDecode(new byte[7],
                     OctavoProgressPortable.DecodeStatus.INVALID);
    }

    @Test
    public void laneJoinIsClockFreeDeterministicAndRejectsEquivocation() {
        OctavoProgressPortable.Lane a1 = lane(1, 1, 0);
        OctavoProgressPortable.Lane a2 = lane(1, 2, 1);
        OctavoProgressPortable.Lane b1 = lane(2, 1, 2);
        OctavoProgressPortable.Lane c1 = lane(3, 1, 3);
        OctavoProgressPortable.Snapshot a = snapshot(a1);
        OctavoProgressPortable.Snapshot b = snapshot(b1);
        OctavoProgressPortable.Snapshot c = snapshot(a2, c1);

        OctavoProgressPortable.Snapshot ab = merged(a, b);
        assertEquals(ab, merged(b, a));
        assertEquals(merged(ab, c), merged(a, merged(b, c)));
        OctavoProgressPortable.MergeResult idempotent =
            OctavoProgressPortable.merge(a, a);
        assertEquals(OctavoProgressPortable.MergeStatus.UNCHANGED,
                     idempotent.status);
        assertEquals(a, idempotent.snapshot);
        assertEquals(a2, merged(a, snapshot(a2)).lane(device(1)));

        OctavoProgressPortable.MergeResult stale =
            OctavoProgressPortable.merge(snapshot(a2), a);
        assertEquals(OctavoProgressPortable.MergeStatus.UNCHANGED,
                     stale.status);
        assertEquals(snapshot(a2), stale.snapshot);

        OctavoProgressPortable.MergeResult equivocal =
            OctavoProgressPortable.merge(
                a, snapshot(lane(1, 1, 3)));
        assertEquals(OctavoProgressPortable.MergeStatus.EQUIVOCATION,
                     equivocal.status);
        assertEquals(a, equivocal.snapshot);

        ArrayList<OctavoProgressPortable.Lane> full =
            new ArrayList<>();
        for (int index = 0; index < 16; ++index) {
            full.add(lane(100 + index, 1, index % 4));
        }
        OctavoProgressPortable.Snapshot fullSnapshot =
            new OctavoProgressPortable.Snapshot(full);
        OctavoProgressPortable.MergeResult limited =
            OctavoProgressPortable.merge(
                fullSnapshot, snapshot(lane(999, 1, 0)));
        assertEquals(OctavoProgressPortable.MergeStatus.LIMIT,
                     limited.status);
        assertEquals(fullSnapshot, limited.snapshot);
    }

    private static OctavoProgressPortable.Snapshot merged(
        OctavoProgressPortable.Snapshot left,
        OctavoProgressPortable.Snapshot right) {
        OctavoProgressPortable.MergeResult result =
            OctavoProgressPortable.merge(left, right);
        assertEquals(OctavoProgressPortable.MergeStatus.MERGED,
                     result.status);
        return result.snapshot;
    }

    private static OctavoProgressPortable.Snapshot snapshot(
        OctavoProgressPortable.Lane... lanes) {
        return new OctavoProgressPortable.Snapshot(
            Arrays.asList(lanes));
    }

    private static byte[] bytes(OctavoProgressPortable.Lane... lanes)
        throws IOException {
        return OctavoProgressPortable.encode(snapshot(lanes));
    }

    private static OctavoProgressPortable.Lane lane(
        int device, long sequence, int semanticId) {
        return new OctavoProgressPortable.Lane(
            device(device), sequence, choice(semanticId));
    }

    private static OctavoProgressPortable.Choice choice(int semanticId) {
        return new OctavoProgressPortable.Choice(semanticId);
    }

    private static String device(int value) {
        return String.format(java.util.Locale.US, "%032x", value);
    }

    private static byte[] futureBytes(int length, int version) {
        byte[] bytes = new byte[length];
        writeInt(bytes, 0, OctavoProgressPortable.magicForTesting());
        writeInt(bytes, 4, version);
        return bytes;
    }

    private static void assertDecode(
        byte[] bytes,
        OctavoProgressPortable.DecodeStatus expected) {
        assertEquals(expected, OctavoProgressPortable.decode(bytes).status);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int index = 0; index < Long.BYTES; ++index) {
            bytes[offset + index] =
                (byte)(value >>> (56 - 8 * index));
        }
    }

    private static void repairChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        writeInt(bytes, bytes.length - Integer.BYTES,
                 (int)checksum.getValue());
    }
}
