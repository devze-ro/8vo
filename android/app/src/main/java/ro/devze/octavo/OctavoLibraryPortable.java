package ro.devze.octavo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Canonical, bounded O1LC add-only managed-EPUB identity catalog. */
final class OctavoLibraryPortable {
    enum DecodeStatus {
        READY,
        FUTURE_VERSION,
        INVALID,
        LIMIT
    }

    enum MergeStatus {
        MERGED,
        UNCHANGED,
        EQUIVOCATION,
        LIMIT,
        INVALID
    }

    static final class Descriptor {
        static final int EPUB = 1;

        final String digest;
        final long byteCount;
        final int kind;

        Descriptor(String digest, long byteCount) {
            this(digest, byteCount, EPUB);
        }

        Descriptor(String digest, long byteCount, int kind) {
            if (!validDigest(digest) || byteCount <= 0
                || byteCount > MAX_DOCUMENT_BYTES || kind != EPUB) {
                throw new IllegalArgumentException(
                    "Invalid portable Library descriptor");
            }
            this.digest = digest;
            this.byteCount = byteCount;
            this.kind = kind;
        }

        boolean sameIdentity(Descriptor other) {
            return other != null && digest.equals(other.digest)
                && byteCount == other.byteCount && kind == other.kind;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Descriptor
                && sameIdentity((Descriptor)object);
        }

        @Override
        public int hashCode() {
            int result = digest.hashCode();
            result = 31 * result + Long.hashCode(byteCount);
            result = 31 * result + kind;
            return result;
        }
    }

    static final class Snapshot {
        private final TreeMap<String, Descriptor> descriptors;

        Snapshot(Collection<Descriptor> source) {
            if (source == null || source.size() > MAX_RECORDS) {
                throw new IllegalArgumentException(
                    "Invalid O1LC snapshot");
            }
            TreeMap<String, Descriptor> copied = new TreeMap<>();
            for (Descriptor descriptor : source) {
                if (descriptor == null
                    || copied.put(descriptor.digest, descriptor) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate O1LC digest");
                }
                if (copied.size() > MAX_RECORDS) {
                    throw new IllegalArgumentException(
                        "O1LC snapshot exceeds its record bound");
                }
            }
            descriptors = copied;
        }

        List<Descriptor> descriptors() {
            return Collections.unmodifiableList(
                new ArrayList<>(descriptors.values()));
        }

        Descriptor descriptor(String digest) {
            return descriptors.get(digest);
        }

        int descriptorCount() {
            return descriptors.size();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Snapshot
                && descriptors.equals(((Snapshot)object).descriptors);
        }

        @Override
        public int hashCode() {
            return descriptors.hashCode();
        }
    }

    static final class DecodeResult {
        final DecodeStatus status;
        private final Snapshot snapshot;
        private final byte[] preservedBytes;

        private DecodeResult(DecodeStatus status,
                             Snapshot snapshot,
                             byte[] preservedBytes) {
            this.status = status;
            this.snapshot = snapshot;
            this.preservedBytes = preservedBytes == null
                ? null : preservedBytes.clone();
        }

        Snapshot snapshot() {
            return snapshot;
        }

        byte[] preservedBytes() {
            return preservedBytes == null ? null : preservedBytes.clone();
        }
    }

    static final class MergeResult {
        final MergeStatus status;
        final Snapshot snapshot;

        private MergeResult(MergeStatus status, Snapshot snapshot) {
            this.status = status;
            this.snapshot = snapshot;
        }
    }

    private static final int MAGIC = 0x4F314C43; // "O1LC"
    private static final int VERSION = 1;
    private static final int RECORD_FIELD_COUNT = 3;
    private static final int MAX_RECORDS = 63;
    private static final int DIGEST_BYTES = 64;
    private static final int RECORD_BYTES = 76;
    private static final int MINIMUM_V1_BYTES = 20;
    private static final int MAXIMUM_V1_BYTES = 4_808;
    private static final int MAXIMUM_FUTURE_BYTES = 65_536;
    private static final long MAX_DOCUMENT_BYTES =
        512L * 1024L * 1024L;

    private OctavoLibraryPortable() {
    }

    static byte[] encode(Snapshot snapshot) throws IOException {
        if (snapshot == null
            || snapshot.descriptorCount() > MAX_RECORDS) {
            throw new IOException("Invalid O1LC snapshot");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(RECORD_FIELD_COUNT);
            output.writeInt(snapshot.descriptorCount());
            String previous = null;
            for (Descriptor descriptor : snapshot.descriptors()) {
                if (descriptor == null
                    || !validDigest(descriptor.digest)
                    || descriptor.byteCount <= 0
                    || descriptor.byteCount > MAX_DOCUMENT_BYTES
                    || descriptor.kind != Descriptor.EPUB
                    || (previous != null
                        && previous.compareTo(descriptor.digest) >= 0)) {
                    throw new IOException(
                        "Noncanonical O1LC descriptor");
                }
                writeDigest(output, descriptor.digest);
                output.writeLong(descriptor.byteCount);
                output.writeInt(descriptor.kind);
                previous = descriptor.digest;
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
        int expected = MINIMUM_V1_BYTES
            + RECORD_BYTES * snapshot.descriptorCount();
        if (result.length != expected
            || result.length > MAXIMUM_V1_BYTES) {
            throw new IOException("O1LC snapshot exceeds its bound");
        }
        return result;
    }

    static DecodeResult decode(byte[] bytes) {
        if (bytes == null || bytes.length < 2 * Integer.BYTES) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
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
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
        if (bytes.length > MAXIMUM_V1_BYTES) {
            return new DecodeResult(DecodeStatus.LIMIT, null, null);
        }
        if (bytes.length < MINIMUM_V1_BYTES) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
        try {
            int fieldCount = readInt(bytes, 2 * Integer.BYTES);
            int recordCount = readInt(bytes, 3 * Integer.BYTES);
            if (fieldCount != RECORD_FIELD_COUNT || recordCount < 0) {
                throw new IOException("Invalid O1LC header");
            }
            if (recordCount > MAX_RECORDS) {
                return new DecodeResult(DecodeStatus.LIMIT, null, null);
            }
            int expectedLength = MINIMUM_V1_BYTES
                + RECORD_BYTES * recordCount;
            if (bytes.length != expectedLength) {
                throw new IOException("Invalid O1LC length");
            }
            int payloadLength = bytes.length - Integer.BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(bytes, 0, payloadLength);
            if (readInt(bytes, payloadLength)
                != (int)checksum.getValue()) {
                throw new IOException("Invalid O1LC checksum");
            }
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != MAGIC
                || input.readInt() != VERSION
                || input.readInt() != RECORD_FIELD_COUNT
                || input.readInt() != recordCount) {
                throw new IOException("Invalid O1LC header");
            }
            ArrayList<Descriptor> descriptors =
                new ArrayList<>(recordCount);
            String previous = null;
            for (int index = 0; index < recordCount; ++index) {
                String digest = readDigest(input);
                long byteCount = input.readLong();
                int kind = input.readInt();
                Descriptor descriptor =
                    new Descriptor(digest, byteCount, kind);
                if (previous != null
                    && previous.compareTo(digest) >= 0) {
                    throw new IOException(
                        "Noncanonical O1LC descriptor order");
                }
                descriptors.add(descriptor);
                previous = digest;
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1LC payload");
            }
            return new DecodeResult(
                DecodeStatus.READY, new Snapshot(descriptors), null);
        } catch (EOFException exception) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        } catch (IOException | RuntimeException exception) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
    }

    static MergeResult merge(Snapshot local, Snapshot remote) {
        if (local == null || remote == null) {
            return new MergeResult(MergeStatus.INVALID, local);
        }
        TreeMap<String, Descriptor> localByDigest = new TreeMap<>();
        for (Descriptor descriptor : local.descriptors()) {
            localByDigest.put(descriptor.digest, descriptor);
        }
        for (Descriptor incoming : remote.descriptors()) {
            Descriptor existing = localByDigest.get(incoming.digest);
            if (existing != null && !existing.sameIdentity(incoming)) {
                return new MergeResult(
                    MergeStatus.EQUIVOCATION, local);
            }
        }
        int unionCount = localByDigest.size();
        for (Descriptor incoming : remote.descriptors()) {
            if (!localByDigest.containsKey(incoming.digest)) {
                ++unionCount;
            }
        }
        if (unionCount > MAX_RECORDS) {
            return new MergeResult(MergeStatus.LIMIT, local);
        }
        if (unionCount == localByDigest.size()) {
            return new MergeResult(MergeStatus.UNCHANGED, local);
        }
        TreeMap<String, Descriptor> joined =
            new TreeMap<>(localByDigest);
        for (Descriptor incoming : remote.descriptors()) {
            if (!joined.containsKey(incoming.digest)) {
                joined.put(incoming.digest, incoming);
            }
        }
        return new MergeResult(
            MergeStatus.MERGED, new Snapshot(joined.values()));
    }

    static byte[] simulatedRemoteBytes(
        Collection<Descriptor> descriptors) throws IOException {
        return encode(new Snapshot(descriptors));
    }

    static boolean validDigest(String value) {
        if (value == null || value.length() != DIGEST_BYTES) {
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

    static int minimumV1Bytes() {
        return MINIMUM_V1_BYTES;
    }

    static int recordBytes() {
        return RECORD_BYTES;
    }

    static int maximumV1Bytes() {
        return MAXIMUM_V1_BYTES;
    }

    static int maximumFutureBytes() {
        return MAXIMUM_FUTURE_BYTES;
    }

    static int maximumRecordCount() {
        return MAX_RECORDS;
    }

    static long maximumDocumentBytes() {
        return MAX_DOCUMENT_BYTES;
    }

    static int magicForTesting() {
        return MAGIC;
    }

    static int versionForTesting() {
        return VERSION;
    }

    private static void writeDigest(DataOutputStream output,
                                    String digest) throws IOException {
        byte[] bytes = digest.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != DIGEST_BYTES || !validDigest(digest)) {
            throw new IOException("Invalid O1LC digest");
        }
        output.write(bytes);
    }

    private static String readDigest(DataInputStream input)
        throws IOException {
        byte[] bytes = new byte[DIGEST_BYTES];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (!validDigest(result)) {
            throw new IOException("Invalid O1LC digest");
        }
        return result;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }
}
