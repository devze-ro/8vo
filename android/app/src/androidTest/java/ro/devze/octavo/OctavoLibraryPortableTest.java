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
import java.util.Locale;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibraryPortableTest {
    @Test
    public void canonicalWireIsTitleFreeSortedAndExactlyBounded()
        throws IOException {
        byte[] empty = OctavoLibraryPortable.encode(
            snapshot());
        assertEquals(20, empty.length);
        assertEquals(OctavoLibraryPortable.minimumV1Bytes(),
                     empty.length);

        ArrayList<OctavoLibraryPortable.Descriptor> descriptors =
            new ArrayList<>();
        for (int index = 62; index >= 0; --index) {
            descriptors.add(descriptor(100 + index, index + 1));
        }
        byte[] bytes = OctavoLibraryPortable.encode(
            new OctavoLibraryPortable.Snapshot(descriptors));
        assertEquals(4_808, bytes.length);
        assertEquals(76, OctavoLibraryPortable.recordBytes());
        assertEquals(OctavoLibraryPortable.maximumV1Bytes(),
                     bytes.length);
        assertEquals(OctavoLibraryPortable.magicForTesting(),
                     readInt(bytes, 0));
        assertEquals(OctavoLibraryPortable.versionForTesting(),
                     readInt(bytes, 4));
        assertEquals(3, readInt(bytes, 8));
        assertEquals(63, readInt(bytes, 12));

        OctavoLibraryPortable.DecodeResult decoded =
            OctavoLibraryPortable.decode(bytes);
        assertEquals(OctavoLibraryPortable.DecodeStatus.READY,
                     decoded.status);
        assertEquals(digest(100), decoded.snapshot()
            .descriptors().get(0).digest);
        assertEquals(digest(162), decoded.snapshot()
            .descriptors().get(62).digest);
        assertArrayEquals(bytes,
                          OctavoLibraryPortable.encode(
                              decoded.snapshot()));
    }

    @Test
    public void malformedFutureAndOverboundInputsAreDistinguished()
        throws IOException {
        byte[] valid = bytes(descriptor(1, 123));
        assertEquals(96, valid.length);

        byte[] checksum = valid.clone();
        checksum[checksum.length - 1] ^= 1;
        assertDecode(checksum,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] fieldCount = valid.clone();
        writeInt(fieldCount, 8, 2);
        repairChecksum(fieldCount);
        assertDecode(fieldCount,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] uppercaseDigest = valid.clone();
        uppercaseDigest[16] = 'A';
        repairChecksum(uppercaseDigest);
        assertDecode(uppercaseDigest,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] zeroLength = valid.clone();
        writeLong(zeroLength, 80, 0);
        repairChecksum(zeroLength);
        assertDecode(zeroLength,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] excessiveLength = valid.clone();
        writeLong(excessiveLength, 80,
                  OctavoLibraryPortable.maximumDocumentBytes() + 1);
        repairChecksum(excessiveLength);
        assertDecode(excessiveLength,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] wrongKind = valid.clone();
        writeInt(wrongKind, 88, 2);
        repairChecksum(wrongKind);
        assertDecode(wrongKind,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] negativeCount = valid.clone();
        writeInt(negativeCount, 12, -1);
        repairChecksum(negativeCount);
        assertDecode(negativeCount,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] tooMany = valid.clone();
        writeInt(tooMany, 12, 64);
        repairChecksum(tooMany);
        assertDecode(tooMany,
                     OctavoLibraryPortable.DecodeStatus.LIMIT);

        byte[] duplicate = bytes(
            descriptor(1, 123), descriptor(2, 456));
        System.arraycopy(duplicate, 16, duplicate, 92, 64);
        repairChecksum(duplicate);
        assertDecode(duplicate,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] reversed = bytes(
            descriptor(1, 123), descriptor(2, 456));
        byte[] first = Arrays.copyOfRange(reversed, 16, 92);
        System.arraycopy(reversed, 92, reversed, 16, 76);
        System.arraycopy(first, 0, reversed, 92, 76);
        repairChecksum(reversed);
        assertDecode(reversed,
                     OctavoLibraryPortable.DecodeStatus.INVALID);

        byte[] futureMinimum = futureBytes(8, 2);
        OctavoLibraryPortable.DecodeResult future =
            OctavoLibraryPortable.decode(futureMinimum);
        assertEquals(OctavoLibraryPortable.DecodeStatus.FUTURE_VERSION,
                     future.status);
        assertArrayEquals(futureMinimum, future.preservedBytes());
        assertDecode(futureBytes(65_536, 0x80000000),
                     OctavoLibraryPortable.DecodeStatus.FUTURE_VERSION);
        assertDecode(futureBytes(65_537, 0xffffffff),
                     OctavoLibraryPortable.DecodeStatus.LIMIT);

        byte[] v1Over = new byte[4_809];
        writeInt(v1Over, 0,
                 OctavoLibraryPortable.magicForTesting());
        writeInt(v1Over, 4,
                 OctavoLibraryPortable.versionForTesting());
        assertDecode(v1Over,
                     OctavoLibraryPortable.DecodeStatus.LIMIT);
        assertDecode(null, OctavoLibraryPortable.DecodeStatus.INVALID);
        assertDecode(new byte[7],
                     OctavoLibraryPortable.DecodeStatus.INVALID);
    }

    @Test
    public void addOnlySetJoinIsCommutativeAssociativeAndIdempotent() {
        OctavoLibraryPortable.Descriptor one = descriptor(1, 100);
        OctavoLibraryPortable.Descriptor two = descriptor(2, 200);
        OctavoLibraryPortable.Descriptor three = descriptor(3, 300);
        OctavoLibraryPortable.Snapshot a = snapshot(one);
        OctavoLibraryPortable.Snapshot b = snapshot(two);
        OctavoLibraryPortable.Snapshot c = snapshot(three);

        OctavoLibraryPortable.Snapshot ab = merged(a, b);
        assertEquals(ab, merged(b, a));
        assertEquals(merged(ab, c), merged(a, merged(b, c)));

        OctavoLibraryPortable.MergeResult idempotent =
            OctavoLibraryPortable.merge(a, a);
        assertEquals(OctavoLibraryPortable.MergeStatus.UNCHANGED,
                     idempotent.status);
        assertEquals(a, idempotent.snapshot);

        OctavoLibraryPortable.MergeResult equivocal =
            OctavoLibraryPortable.merge(
                a, snapshot(descriptor(1, 101)));
        assertEquals(OctavoLibraryPortable.MergeStatus.EQUIVOCATION,
                     equivocal.status);
        assertEquals(a, equivocal.snapshot);
    }

    @Test
    public void equivocationPrecedesUnionLimitInBothOperandOrders() {
        ArrayList<OctavoLibraryPortable.Descriptor> full =
            new ArrayList<>();
        for (int index = 100; index < 163; ++index) {
            full.add(descriptor(index, index));
        }
        OctavoLibraryPortable.Snapshot fullSnapshot =
            new OctavoLibraryPortable.Snapshot(full);
        OctavoLibraryPortable.Snapshot newThenEquivocal = snapshot(
            descriptor(1, 1), descriptor(162, 999));

        OctavoLibraryPortable.MergeResult forward =
            OctavoLibraryPortable.merge(
                fullSnapshot, newThenEquivocal);
        OctavoLibraryPortable.MergeResult reverse =
            OctavoLibraryPortable.merge(
                newThenEquivocal, fullSnapshot);
        assertEquals(OctavoLibraryPortable.MergeStatus.EQUIVOCATION,
                     forward.status);
        assertEquals(OctavoLibraryPortable.MergeStatus.EQUIVOCATION,
                     reverse.status);
        assertEquals(fullSnapshot, forward.snapshot);
        assertEquals(newThenEquivocal, reverse.snapshot);

        OctavoLibraryPortable.MergeResult limited =
            OctavoLibraryPortable.merge(
                fullSnapshot, snapshot(descriptor(999, 1)));
        assertEquals(OctavoLibraryPortable.MergeStatus.LIMIT,
                     limited.status);
        assertEquals(fullSnapshot, limited.snapshot);
    }

    private static OctavoLibraryPortable.Snapshot merged(
        OctavoLibraryPortable.Snapshot left,
        OctavoLibraryPortable.Snapshot right) {
        OctavoLibraryPortable.MergeResult result =
            OctavoLibraryPortable.merge(left, right);
        assertEquals(OctavoLibraryPortable.MergeStatus.MERGED,
                     result.status);
        return result.snapshot;
    }

    private static OctavoLibraryPortable.Snapshot snapshot(
        OctavoLibraryPortable.Descriptor... descriptors) {
        return new OctavoLibraryPortable.Snapshot(
            Arrays.asList(descriptors));
    }

    private static byte[] bytes(
        OctavoLibraryPortable.Descriptor... descriptors)
        throws IOException {
        return OctavoLibraryPortable.encode(snapshot(descriptors));
    }

    private static OctavoLibraryPortable.Descriptor descriptor(
        int digest, long byteCount) {
        return new OctavoLibraryPortable.Descriptor(
            digest(digest), byteCount);
    }

    private static String digest(int value) {
        return String.format(Locale.US, "%064x", value);
    }

    private static byte[] futureBytes(int length, int version) {
        byte[] bytes = new byte[length];
        writeInt(bytes, 0, OctavoLibraryPortable.magicForTesting());
        writeInt(bytes, 4, version);
        return bytes;
    }

    private static void assertDecode(
        byte[] bytes,
        OctavoLibraryPortable.DecodeStatus expected) {
        assertEquals(expected, OctavoLibraryPortable.decode(bytes).status);
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
