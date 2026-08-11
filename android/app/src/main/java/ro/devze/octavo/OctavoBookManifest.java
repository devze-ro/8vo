package ro.devze.octavo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/** Canonical, bounded O1BM manifest for one exact managed EPUB. */
final class OctavoBookManifest {
    enum DecodeStatus {
        READY,
        FUTURE_VERSION,
        INVALID,
        LIMIT
    }

    static final class DecodeResult {
        final DecodeStatus status;
        private final OctavoBookManifest manifest;
        private final byte[] preservedBytes;

        private DecodeResult(DecodeStatus status,
                             OctavoBookManifest manifest,
                             byte[] preservedBytes) {
            this.status = status;
            this.manifest = manifest;
            this.preservedBytes = preservedBytes == null
                ? null : preservedBytes.clone();
        }

        OctavoBookManifest manifest() {
            return manifest;
        }

        byte[] preservedBytes() {
            return preservedBytes == null ? null : preservedBytes.clone();
        }
    }

    private static final int MAGIC = 0x4F31424D; // "O1BM"
    private static final int VERSION = 1;
    private static final int FIELD_COUNT = 4;
    private static final int DIGEST_ASCII_BYTES = 64;
    private static final long MAX_DOCUMENT_BYTES = 512L * 1024L * 1024L;
    private static final int CHUNK_SIZE = 4 * 1024 * 1024;
    private static final int MAX_CHUNKS = 128;
    private static final int SHA256_BYTES = 32;
    private static final int FIXED_BYTES = 96;
    private static final int MAXIMUM_V1_BYTES =
        FIXED_BYTES + SHA256_BYTES * MAX_CHUNKS;
    private static final int MAXIMUM_FUTURE_BYTES = 65_536;

    final String digest;
    final long byteCount;
    final int chunkSize;
    final int chunkCount;
    private final byte[][] chunkHashes;

    private OctavoBookManifest(String digest,
                               long byteCount,
                               byte[][] chunkHashes) {
        if (!validDigest(digest)
            || byteCount <= 0
            || byteCount > MAX_DOCUMENT_BYTES
            || chunkHashes == null
            || chunkHashes.length != expectedChunkCount(byteCount)
            || chunkHashes.length <= 0
            || chunkHashes.length > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid O1BM manifest");
        }
        this.digest = digest;
        this.byteCount = byteCount;
        chunkSize = CHUNK_SIZE;
        chunkCount = chunkHashes.length;
        this.chunkHashes = new byte[chunkHashes.length][];
        for (int index = 0; index < chunkHashes.length; ++index) {
            if (chunkHashes[index] == null
                || chunkHashes[index].length != SHA256_BYTES) {
                throw new IllegalArgumentException(
                    "Invalid O1BM chunk hash");
            }
            this.chunkHashes[index] = chunkHashes[index].clone();
        }
    }

    static OctavoBookManifest build(InputStream callerOwnedInput)
        throws IOException {
        if (callerOwnedInput == null) {
            throw new IOException("Missing EPUB input");
        }
        MessageDigest full = sha256Digest();
        MessageDigest chunk = sha256Digest();
        ArrayList<byte[]> hashes = new ArrayList<>();
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int chunkBytes = 0;
        while (true) {
            int count = callerOwnedInput.read(buffer);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int value = callerOwnedInput.read();
                if (value < 0) {
                    break;
                }
                buffer[0] = (byte)value;
                count = 1;
            }
            if (total > MAX_DOCUMENT_BYTES - count) {
                throw new IOException("EPUB exceeds the 512 MiB cap");
            }
            total += count;
            full.update(buffer, 0, count);
            int offset = 0;
            while (offset < count) {
                int consumed = Math.min(
                    count - offset, CHUNK_SIZE - chunkBytes);
                chunk.update(buffer, offset, consumed);
                chunkBytes += consumed;
                offset += consumed;
                if (chunkBytes == CHUNK_SIZE) {
                    hashes.add(chunk.digest());
                    chunk = sha256Digest();
                    chunkBytes = 0;
                }
            }
        }
        if (total <= 0) {
            throw new IOException("EPUB is empty");
        }
        if (chunkBytes > 0) {
            hashes.add(chunk.digest());
        }
        byte[][] hashArray = hashes.toArray(new byte[0][]);
        return new OctavoBookManifest(hex(full.digest()), total, hashArray);
    }

    static OctavoBookManifest build(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Managed EPUB is unavailable");
        }
        long initialLength = file.length();
        if (initialLength <= 0 || initialLength > MAX_DOCUMENT_BYTES) {
            throw new IOException("Managed EPUB length is invalid");
        }
        OctavoBookManifest result;
        try (FileInputStream input = new FileInputStream(file)) {
            result = build(input);
        }
        if (!file.isFile() || file.length() != result.byteCount
            || initialLength != result.byteCount) {
            throw new IOException("Managed EPUB changed while hashing");
        }
        return result;
    }

    byte[] encode() throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream(
            FIXED_BYTES - Integer.BYTES + SHA256_BYTES * chunkCount);
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(FIELD_COUNT);
            output.write(digest.getBytes(StandardCharsets.US_ASCII));
            output.writeLong(byteCount);
            output.writeInt(CHUNK_SIZE);
            output.writeInt(chunkCount);
            for (byte[] hash : chunkHashes) {
                output.write(hash);
            }
            output.flush();
        }
        byte[] payload = payloadBytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(payload, 0, payload.length);
        ByteArrayOutputStream resultBytes =
            new ByteArrayOutputStream(payload.length + Integer.BYTES);
        try (DataOutputStream output =
                 new DataOutputStream(resultBytes)) {
            output.write(payload);
            output.writeInt((int)checksum.getValue());
            output.flush();
        }
        byte[] result = resultBytes.toByteArray();
        if (result.length != exactEncodedLength(chunkCount)) {
            throw new IOException("Invalid O1BM encoded length");
        }
        return result;
    }

    static DecodeResult decode(byte[] bytes) {
        if (bytes == null || bytes.length < 2 * Integer.BYTES) {
            return invalid();
        }
        int magic = readInt(bytes, 0);
        int version = readInt(bytes, Integer.BYTES);
        if (magic == MAGIC
            && Integer.compareUnsigned(version, VERSION) > 0) {
            if (bytes.length > MAXIMUM_FUTURE_BYTES) {
                return new DecodeResult(DecodeStatus.LIMIT, null, null);
            }
            return new DecodeResult(
                DecodeStatus.FUTURE_VERSION, null, bytes);
        }
        if (magic != MAGIC || version != VERSION) {
            return invalid();
        }
        if (bytes.length > MAXIMUM_V1_BYTES) {
            return new DecodeResult(DecodeStatus.LIMIT, null, null);
        }
        if (bytes.length < exactEncodedLength(1)) {
            return invalid();
        }
        try {
            int fieldCount = readInt(bytes, 2 * Integer.BYTES);
            long byteCount = readLong(
                bytes, 3 * Integer.BYTES + DIGEST_ASCII_BYTES);
            int chunkSize = readInt(
                bytes, 3 * Integer.BYTES + DIGEST_ASCII_BYTES
                       + Long.BYTES);
            int chunkCount = readInt(
                bytes, 3 * Integer.BYTES + DIGEST_ASCII_BYTES
                       + Long.BYTES + Integer.BYTES);
            if (fieldCount != FIELD_COUNT
                || byteCount <= 0
                || byteCount > MAX_DOCUMENT_BYTES
                || chunkSize != CHUNK_SIZE
                || chunkCount <= 0
                || chunkCount > MAX_CHUNKS
                || chunkCount != expectedChunkCount(byteCount)
                || bytes.length != exactEncodedLength(chunkCount)) {
                throw new IOException("Invalid O1BM shape");
            }
            int payloadLength = bytes.length - Integer.BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(bytes, 0, payloadLength);
            if (readInt(bytes, payloadLength)
                != (int)checksum.getValue()) {
                throw new IOException("Invalid O1BM checksum");
            }
            DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, payloadLength));
            if (input.readInt() != MAGIC
                || input.readInt() != VERSION
                || input.readInt() != FIELD_COUNT) {
                throw new IOException("Invalid O1BM header");
            }
            byte[] digestAscii = new byte[DIGEST_ASCII_BYTES];
            input.readFully(digestAscii);
            String digest = new String(
                digestAscii, StandardCharsets.US_ASCII);
            if (!validDigest(digest)
                || input.readLong() != byteCount
                || input.readInt() != CHUNK_SIZE
                || input.readInt() != chunkCount) {
                throw new IOException("Invalid O1BM fields");
            }
            byte[][] hashes = new byte[chunkCount][];
            for (int index = 0; index < chunkCount; ++index) {
                hashes[index] = new byte[SHA256_BYTES];
                input.readFully(hashes[index]);
            }
            if (input.available() != 0) {
                throw new IOException("Trailing O1BM payload");
            }
            OctavoBookManifest manifest =
                new OctavoBookManifest(digest, byteCount, hashes);
            if (!Arrays.equals(bytes, manifest.encode())) {
                throw new IOException("Noncanonical O1BM bytes");
            }
            return new DecodeResult(
                DecodeStatus.READY, manifest, null);
        } catch (EOFException exception) {
            return invalid();
        } catch (IOException | RuntimeException exception) {
            return invalid();
        }
    }

    byte[] chunkHash(int index) {
        if (index < 0 || index >= chunkCount) {
            throw new IllegalArgumentException("Invalid chunk index");
        }
        return chunkHashes[index].clone();
    }

    List<byte[]> chunkHashes() {
        ArrayList<byte[]> result = new ArrayList<>(chunkCount);
        for (byte[] hash : chunkHashes) {
            result.add(hash.clone());
        }
        return Collections.unmodifiableList(result);
    }

    int expectedChunkLength(int index) {
        if (index < 0 || index >= chunkCount) {
            throw new IllegalArgumentException("Invalid chunk index");
        }
        if (index + 1 < chunkCount) {
            return CHUNK_SIZE;
        }
        long preceding = (long)index * CHUNK_SIZE;
        return (int)(byteCount - preceding);
    }

    boolean sameIdentity(OctavoBookManifest other) {
        return other != null
            && digest.equals(other.digest)
            && byteCount == other.byteCount
            && chunkCount == other.chunkCount
            && Arrays.deepEquals(chunkHashes, other.chunkHashes);
    }

    static boolean validDigest(String value) {
        if (value == null || value.length() != DIGEST_ASCII_BYTES) {
            return false;
        }
        for (int index = 0; index < value.length(); ++index) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                  || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    static int exactEncodedLength(int chunkCount) {
        if (chunkCount < 0 || chunkCount > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid O1BM chunk count");
        }
        return FIXED_BYTES + SHA256_BYTES * chunkCount;
    }

    static int maximumFutureBytes() {
        return MAXIMUM_FUTURE_BYTES;
    }

    static int maximumV1Bytes() {
        return MAXIMUM_V1_BYTES;
    }

    static long maximumDocumentBytes() {
        return MAX_DOCUMENT_BYTES;
    }

    static int fixedChunkSize() {
        return CHUNK_SIZE;
    }

    static int maximumChunkCount() {
        return MAX_CHUNKS;
    }

    static int magicForTesting() {
        return MAGIC;
    }

    static int versionForTesting() {
        return VERSION;
    }

    private static DecodeResult invalid() {
        return new DecodeResult(DecodeStatus.INVALID, null, null);
    }

    private static int expectedChunkCount(long byteCount) {
        if (byteCount <= 0 || byteCount > MAX_DOCUMENT_BYTES) {
            return 0;
        }
        return (int)((byteCount + CHUNK_SIZE - 1) / CHUNK_SIZE);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; ++index) {
            int value = bytes[index] & 0xff;
            result[2 * index] = digits[value >>> 4];
            result[2 * index + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }

    private static long readLong(byte[] bytes, int offset) {
        long result = 0;
        for (int index = 0; index < Long.BYTES; ++index) {
            result = (result << 8) | (bytes[offset + index] & 0xffL);
        }
        return result;
    }
}
