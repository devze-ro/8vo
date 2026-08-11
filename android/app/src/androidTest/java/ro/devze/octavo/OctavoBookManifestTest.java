package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoBookManifestTest {
    @Test
    public void oneChunkEncodingIsExactCanonicalAndCallerOwned()
        throws IOException {
        byte[] epub = bytes(73, 7);
        TrackingInput input = new TrackingInput(epub);
        OctavoBookManifest manifest = OctavoBookManifest.build(input);
        assertFalse(input.closed);
        assertEquals(hex(sha256(epub)), manifest.digest);
        assertEquals(epub.length, manifest.byteCount);
        assertEquals(4_194_304, manifest.chunkSize);
        assertEquals(1, manifest.chunkCount);
        assertEquals(epub.length, manifest.expectedChunkLength(0));
        assertArrayEquals(sha256(epub), manifest.chunkHash(0));

        byte[] encoded = manifest.encode();
        assertEquals(96 + 32, encoded.length);
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(encoded);
        assertEquals(OctavoBookManifest.DecodeStatus.READY,
                     decoded.status);
        assertTrue(manifest.sameIdentity(decoded.manifest()));
        assertArrayEquals(encoded, decoded.manifest().encode());
        assertNull(decoded.preservedBytes());

        byte[] returnedHash = decoded.manifest().chunkHash(0);
        returnedHash[0] ^= 0x55;
        assertArrayEquals(sha256(epub),
                          decoded.manifest().chunkHash(0));
    }

    @Test
    public void chunkBoundaryAndFinalChunkLengthsAreExact()
        throws IOException {
        int chunkSize = OctavoBookManifest.fixedChunkSize();
        byte[] epub = bytes(chunkSize + 19, 31);
        OctavoBookManifest manifest = OctavoBookManifest.build(
            new ByteArrayInputStream(epub));
        assertEquals(2, manifest.chunkCount);
        assertEquals(chunkSize, manifest.expectedChunkLength(0));
        assertEquals(19, manifest.expectedChunkLength(1));
        assertArrayEquals(
            sha256(Arrays.copyOfRange(epub, 0, chunkSize)),
            manifest.chunkHash(0));
        assertArrayEquals(
            sha256(Arrays.copyOfRange(epub, chunkSize, epub.length)),
            manifest.chunkHash(1));
        assertEquals(96 + 2 * 32, manifest.encode().length);
    }

    @Test
    public void malformedAndNoncanonicalV1BytesAreRejected()
        throws IOException {
        byte[] encoded = OctavoBookManifest.build(
            new ByteArrayInputStream(bytes(101, 9))).encode();

        byte[] corrupt = encoded.clone();
        corrupt[corrupt.length - 1] ^= 1;
        assertEquals(OctavoBookManifest.DecodeStatus.INVALID,
                     OctavoBookManifest.decode(corrupt).status);

        byte[] uppercaseDigest = encoded.clone();
        int digestOffset = 3 * Integer.BYTES;
        for (int index = digestOffset;
             index < digestOffset + 64; ++index) {
            if (uppercaseDigest[index] >= 'a'
                && uppercaseDigest[index] <= 'f') {
                uppercaseDigest[index] =
                    (byte)Character.toUpperCase(uppercaseDigest[index]);
                break;
            }
        }
        repairChecksum(uppercaseDigest);
        assertEquals(OctavoBookManifest.DecodeStatus.INVALID,
                     OctavoBookManifest.decode(uppercaseDigest).status);

        byte[] wrongChunkSize = encoded.clone();
        writeInt(wrongChunkSize, 84, 1024);
        repairChecksum(wrongChunkSize);
        assertEquals(OctavoBookManifest.DecodeStatus.INVALID,
                     OctavoBookManifest.decode(wrongChunkSize).status);

        byte[] wrongCount = encoded.clone();
        writeInt(wrongCount, 88, 2);
        repairChecksum(wrongCount);
        assertEquals(OctavoBookManifest.DecodeStatus.INVALID,
                     OctavoBookManifest.decode(wrongCount).status);

        try {
            OctavoBookManifest.build(new ByteArrayInputStream(new byte[0]));
            fail("Empty EPUB must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
    }

    @Test
    public void recognizableFutureBytesArePreservedOnlyWithinBound() {
        byte[] future = new byte[65_536];
        writeInt(future, 0, OctavoBookManifest.magicForTesting());
        writeInt(future, 4, OctavoBookManifest.versionForTesting() + 1);
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(future);
        assertEquals(OctavoBookManifest.DecodeStatus.FUTURE_VERSION,
                     decoded.status);
        assertArrayEquals(future, decoded.preservedBytes());
        byte[] returned = decoded.preservedBytes();
        returned[10] = 42;
        assertArrayEquals(future, decoded.preservedBytes());

        byte[] oversized = new byte[65_537];
        writeInt(oversized, 0, OctavoBookManifest.magicForTesting());
        writeInt(oversized, 4,
                 OctavoBookManifest.versionForTesting() + 1);
        assertEquals(OctavoBookManifest.DecodeStatus.LIMIT,
                     OctavoBookManifest.decode(oversized).status);
    }

    private static final class TrackingInput extends InputStream {
        private final ByteArrayInputStream delegate;
        boolean closed;

        TrackingInput(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static byte[] bytes(int count, int seed) {
        byte[] result = new byte[count];
        int value = seed;
        for (int index = 0; index < count; ++index) {
            value = value * 1103515245 + 12345;
            result[index] = (byte)(value >>> 16);
        }
        return result;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void repairChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        writeInt(bytes, bytes.length - Integer.BYTES,
                 (int)checksum.getValue());
    }
}
