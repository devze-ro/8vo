package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

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
public final class OctavoAppearancePortableTest {
    @Test
    public void canonicalWireHasExactBoundsAndRoundTripsEverySemantic()
        throws IOException {
        byte[] empty = OctavoAppearancePortable.encode(
            new OctavoAppearancePortable.Snapshot(
                Collections.emptyList()));
        assertEquals(20, empty.length);
        assertEquals(OctavoAppearancePortable.minimumV1Bytes(),
                     empty.length);

        ArrayList<OctavoAppearancePortable.Lane> lanes =
            new ArrayList<>();
        for (int index = 15; index >= 0; --index) {
            lanes.add(new OctavoAppearancePortable.Lane(
                device(100 + index),
                Long.MAX_VALUE - index,
                profile(index)));
        }
        OctavoAppearancePortable.Snapshot snapshot =
            new OctavoAppearancePortable.Snapshot(lanes);
        byte[] bytes = OctavoAppearancePortable.encode(snapshot);

        assertEquals(1172, bytes.length);
        assertEquals(72, OctavoAppearancePortable.laneBytes());
        assertEquals(OctavoAppearancePortable.maximumV1Bytes(),
                     bytes.length);
        assertEquals(OctavoAppearancePortable.magicForTesting(),
                     readInt(bytes, 0));
        assertEquals(OctavoAppearancePortable.versionForTesting(),
                     readInt(bytes, 4));
        assertEquals(8, readInt(bytes, 8));
        assertEquals(16, readInt(bytes, 12));

        OctavoAppearancePortable.DecodeResult decoded =
            OctavoAppearancePortable.decode(bytes);
        assertEquals(OctavoAppearancePortable.DecodeStatus.READY,
                     decoded.status);
        assertEquals(device(100),
                     decoded.snapshot().lanes().get(0).deviceId);
        assertEquals(device(115),
                     decoded.snapshot().lanes().get(15).deviceId);
        assertArrayEquals(bytes,
                          OctavoAppearancePortable.encode(
                              decoded.snapshot()));
        assertEquals(decoded.snapshot().lanes(),
                     OctavoAppearancePortable.reviewOrder(lanes));

        OctavoAppearance defaults = OctavoAppearance.defaults();
        for (int theme = 0; theme < 6; ++theme) {
            assertAppearanceRoundTrip(defaults.withTheme(theme));
        }
        for (int font = 0; font < 2; ++font) {
            assertAppearanceRoundTrip(defaults.withFontFamily(font));
        }
        for (int size : OctavoAppearance.fontSizesSp()) {
            assertAppearanceRoundTrip(defaults.withFontSizeSp(size));
        }
        for (int spacing : OctavoAppearance.lineSpacingsPermille()) {
            assertAppearanceRoundTrip(
                defaults.withLineSpacingPermille(spacing));
        }
        for (int width = 0; width < 3; ++width) {
            assertAppearanceRoundTrip(defaults.withMargins(width));
        }
        for (int alignment = 0; alignment < 2; ++alignment) {
            assertAppearanceRoundTrip(
                defaults.withAlignment(alignment));
        }
        for (int colors = 0; colors < 2; ++colors) {
            assertAppearanceRoundTrip(
                defaults.withPublisherColors(colors));
        }
        assertAppearanceRoundTrip(defaults.withReducedMotion(false));
        assertAppearanceRoundTrip(defaults.withReducedMotion(true));
    }

    @Test
    public void malformedFutureAndOverboundInputsAreDistinguished()
        throws IOException {
        byte[] valid = OctavoAppearancePortable.simulatedRemoteBytes(
            device(1), 1, OctavoAppearance.defaults());
        assertEquals(92, valid.length);

        byte[] checksum = valid.clone();
        checksum[checksum.length - 1] ^= 1;
        assertDecode(checksum,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] fieldCount = valid.clone();
        writeInt(fieldCount, 8, 7);
        repairChecksum(fieldCount);
        assertDecode(fieldCount,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] unknownTheme = valid.clone();
        writeInt(unknownTheme, 56, 6);
        repairChecksum(unknownTheme);
        assertDecode(unknownTheme,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] zeroSequence = valid.clone();
        writeLong(zeroSequence, 48, 0);
        repairChecksum(zeroSequence);
        assertDecode(zeroSequence,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] negativeCount = valid.clone();
        writeInt(negativeCount, 12, -1);
        repairChecksum(negativeCount);
        assertDecode(negativeCount,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] tooMany = Arrays.copyOf(valid, valid.length);
        writeInt(tooMany, 12, 17);
        repairChecksum(tooMany);
        assertDecode(tooMany,
                     OctavoAppearancePortable.DecodeStatus.LIMIT);

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        repairChecksum(trailing);
        assertDecode(trailing,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] two = OctavoAppearancePortable.encode(
            new OctavoAppearancePortable.Snapshot(Arrays.asList(
                new OctavoAppearancePortable.Lane(
                    device(1), 1, profile(1)),
                new OctavoAppearancePortable.Lane(
                    device(2), 1, profile(2)))));
        System.arraycopy(two, 16, two, 88, 32);
        repairChecksum(two);
        assertDecode(two,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] reversed = OctavoAppearancePortable.encode(
            new OctavoAppearancePortable.Snapshot(Arrays.asList(
                new OctavoAppearancePortable.Lane(
                    device(1), 1, profile(1)),
                new OctavoAppearancePortable.Lane(
                    device(2), 1, profile(2)))));
        byte[] first = Arrays.copyOfRange(reversed, 16, 88);
        System.arraycopy(reversed, 88, reversed, 16, 72);
        System.arraycopy(first, 0, reversed, 88, 72);
        repairChecksum(reversed);
        assertDecode(reversed,
                     OctavoAppearancePortable.DecodeStatus.INVALID);

        byte[] futureMinimum = new byte[8];
        writeInt(futureMinimum, 0,
                 OctavoAppearancePortable.magicForTesting());
        writeInt(futureMinimum, 4, 2);
        OctavoAppearancePortable.DecodeResult future =
            OctavoAppearancePortable.decode(futureMinimum);
        assertEquals(OctavoAppearancePortable.DecodeStatus.FUTURE_VERSION,
                     future.status);
        assertArrayEquals(futureMinimum, future.preservedBytes());

        byte[] futureMaximum = new byte[65_536];
        writeInt(futureMaximum, 0,
                 OctavoAppearancePortable.magicForTesting());
        writeInt(futureMaximum, 4, 0x80000000);
        assertDecode(futureMaximum,
                     OctavoAppearancePortable.DecodeStatus.FUTURE_VERSION);
        byte[] futureOver = Arrays.copyOf(futureMaximum, 65_537);
        assertDecode(futureOver,
                     OctavoAppearancePortable.DecodeStatus.LIMIT);

        byte[] v1Over = new byte[1173];
        writeInt(v1Over, 0,
                 OctavoAppearancePortable.magicForTesting());
        writeInt(v1Over, 4,
                 OctavoAppearancePortable.versionForTesting());
        assertDecode(v1Over,
                     OctavoAppearancePortable.DecodeStatus.LIMIT);
        assertDecode(null, OctavoAppearancePortable.DecodeStatus.INVALID);
        assertDecode(new byte[7],
                     OctavoAppearancePortable.DecodeStatus.INVALID);
    }

    @Test
    public void laneJoinIsClockFreeDeterministicAndRejectsEquivocation() {
        OctavoAppearancePortable.Lane a1 = lane(1, 1, 1);
        OctavoAppearancePortable.Lane a2 = lane(1, 2, 2);
        OctavoAppearancePortable.Lane b1 = lane(2, 1, 3);
        OctavoAppearancePortable.Lane c1 = lane(3, 1, 4);
        OctavoAppearancePortable.Snapshot a = snapshot(a1);
        OctavoAppearancePortable.Snapshot b = snapshot(b1);
        OctavoAppearancePortable.Snapshot c = snapshot(a2, c1);

        OctavoAppearancePortable.Snapshot ab = merged(a, b);
        assertEquals(ab, merged(b, a));
        assertEquals(merged(ab, c), merged(a, merged(b, c)));
        OctavoAppearancePortable.MergeResult idempotent =
            OctavoAppearancePortable.merge(a, a);
        assertEquals(OctavoAppearancePortable.MergeStatus.UNCHANGED,
                     idempotent.status);
        assertEquals(a, idempotent.snapshot);
        assertEquals(a2, merged(a, snapshot(a2)).lane(device(1)));

        OctavoAppearancePortable.MergeResult stale =
            OctavoAppearancePortable.merge(snapshot(a2), a);
        assertEquals(OctavoAppearancePortable.MergeStatus.UNCHANGED,
                     stale.status);
        assertEquals(snapshot(a2), stale.snapshot);

        OctavoAppearancePortable.MergeResult equivocal =
            OctavoAppearancePortable.merge(
                a, snapshot(new OctavoAppearancePortable.Lane(
                    device(1), 1, profile(9))));
        assertEquals(OctavoAppearancePortable.MergeStatus.EQUIVOCATION,
                     equivocal.status);
        assertEquals(a, equivocal.snapshot);

        ArrayList<OctavoAppearancePortable.Lane> full =
            new ArrayList<>();
        for (int index = 0; index < 16; ++index) {
            full.add(lane(100 + index, 1, index));
        }
        OctavoAppearancePortable.Snapshot fullSnapshot =
            new OctavoAppearancePortable.Snapshot(full);
        OctavoAppearancePortable.MergeResult limited =
            OctavoAppearancePortable.merge(
                fullSnapshot, snapshot(lane(999, 1, 0)));
        assertEquals(OctavoAppearancePortable.MergeStatus.LIMIT,
                     limited.status);
        assertEquals(fullSnapshot, limited.snapshot);
    }

    @Test
    public void simulatedRemoteBytesAreDeterministicAndFixtureFree()
        throws IOException {
        OctavoAppearance appearance = OctavoAppearance.create(
            OctavoAppearance.THEME_WARM_DARK,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            24,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);
        byte[] first = OctavoAppearancePortable.simulatedRemoteBytes(
            device(77), 9, appearance);
        byte[] second = OctavoAppearancePortable.simulatedRemoteBytes(
            device(77), 9, appearance);
        assertArrayEquals(first, second);
        OctavoAppearancePortable.Lane lane =
            OctavoAppearancePortable.decode(first)
                .snapshot().lane(device(77));
        assertEquals(appearance, lane.profile.toAppearance());
        assertEquals(9, lane.sequence);
        assertNotEquals(device(78), lane.deviceId);
        assertNull(OctavoAppearancePortable.decode(first).preservedBytes());
    }

    private static OctavoAppearancePortable.Snapshot snapshot(
        OctavoAppearancePortable.Lane... lanes) {
        return new OctavoAppearancePortable.Snapshot(Arrays.asList(lanes));
    }

    private static OctavoAppearancePortable.Snapshot merged(
        OctavoAppearancePortable.Snapshot left,
        OctavoAppearancePortable.Snapshot right) {
        OctavoAppearancePortable.MergeResult result =
            OctavoAppearancePortable.merge(left, right);
        assertEquals(OctavoAppearancePortable.MergeStatus.MERGED,
                     result.status);
        return result.snapshot;
    }

    private static OctavoAppearancePortable.Lane lane(int device,
                                                       long sequence,
                                                       int seed) {
        return new OctavoAppearancePortable.Lane(
            device(device), sequence, profile(seed));
    }

    private static OctavoAppearancePortable.Profile profile(int seed) {
        return new OctavoAppearancePortable.Profile(
            seed % 6,
            seed % 2,
            (seed / 2) % 6,
            (seed / 3) % 4,
            (seed / 4) % 3,
            (seed / 5) % 2,
            (seed / 6) % 2,
            (seed / 7) % 2);
    }

    private static void assertAppearanceRoundTrip(
        OctavoAppearance appearance) {
        assertEquals(
            appearance,
            OctavoAppearancePortable.Profile.fromAppearance(appearance)
                .toAppearance());
    }

    private static void assertDecode(
        byte[] bytes,
        OctavoAppearancePortable.DecodeStatus status) {
        assertEquals(status, OctavoAppearancePortable.decode(bytes).status);
    }

    private static String device(int value) {
        return String.format(Locale.US, "%032x", value);
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
        for (int index = 7; index >= 0; --index) {
            bytes[offset + index] = (byte)value;
            value >>>= 8;
        }
    }

    private static void repairChecksum(byte[] bytes) {
        int checksumOffset = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, checksumOffset);
        writeInt(bytes, checksumOffset, (int)checksum.getValue());
    }
}
