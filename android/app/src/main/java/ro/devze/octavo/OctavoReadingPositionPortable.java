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
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Canonical, bounded O1RP reading-position bytes. */
final class OctavoReadingPositionPortable {
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
        WRONG_BOOK,
        LIMIT,
        INVALID
    }

    static final class Lane {
        final String deviceId;
        final long sequence;
        final long spineIndex;
        final long byteOffset;

        Lane(String deviceId,
             long sequence,
             long spineIndex,
             long byteOffset) {
            if (!validDeviceId(deviceId)
                || sequence <= 0
                || !validAnchor(spineIndex, byteOffset)) {
                throw new IllegalArgumentException("Invalid position lane");
            }
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
        }

        boolean samePosition(Lane other) {
            return other != null
                && spineIndex == other.spineIndex
                && byteOffset == other.byteOffset;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Lane)) {
                return false;
            }
            Lane other = (Lane)object;
            return deviceId.equals(other.deviceId)
                && sequence == other.sequence
                && samePosition(other);
        }

        @Override
        public int hashCode() {
            int result = deviceId.hashCode();
            result = 31 * result + Long.hashCode(sequence);
            result = 31 * result + Long.hashCode(spineIndex);
            return 31 * result + Long.hashCode(byteOffset);
        }
    }

    static final class Snapshot {
        final String bookDigest;
        private final TreeMap<String, Lane> lanes;

        Snapshot(String bookDigest, Collection<Lane> source) {
            if (!validBookDigest(bookDigest) || source == null
                || source.size() > MAX_LANES) {
                throw new IllegalArgumentException("Invalid position snapshot");
            }
            TreeMap<String, Lane> copied = new TreeMap<>();
            for (Lane lane : source) {
                if (lane == null || copied.put(lane.deviceId, lane) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate position lane");
                }
            }
            this.bookDigest = bookDigest;
            lanes = copied;
        }

        List<Lane> lanes() {
            return Collections.unmodifiableList(
                new ArrayList<>(lanes.values()));
        }

        Lane lane(String deviceId) {
            return lanes.get(deviceId);
        }

        int laneCount() {
            return lanes.size();
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

    private static final int MAGIC = 0x4F315250; // "O1RP"
    private static final int VERSION = 1;
    private static final int HEADER_FIELD_COUNT = 1;
    private static final int MAX_LANES = 16;
    private static final int DEVICE_ID_BYTES = 32;
    private static final int BOOK_DIGEST_BYTES = 64;
    private static final long MAX_SPINE_INDEX = 0xffffffffL;
    private static final int MAX_V1_BYTES = 1048;
    private static final int MAX_FUTURE_INPUT_BYTES = 128 * 1024;

    private static final Comparator<Lane> REVIEW_ORDER =
        Comparator.comparingLong((Lane lane) -> lane.spineIndex)
            .thenComparingLong(lane -> lane.byteOffset)
            .reversed()
            .thenComparing(lane -> lane.deviceId);

    private OctavoReadingPositionPortable() {
    }

    static byte[] encode(Snapshot snapshot) throws IOException {
        if (snapshot == null || !validBookDigest(snapshot.bookDigest)
            || snapshot.laneCount() > MAX_LANES) {
            throw new IOException("Invalid O1RP snapshot");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(HEADER_FIELD_COUNT);
            writeAscii(output, snapshot.bookDigest, BOOK_DIGEST_BYTES);
            output.writeInt(snapshot.laneCount());
            String previous = null;
            for (Lane lane : snapshot.lanes()) {
                if (lane == null || !validDeviceId(lane.deviceId)
                    || lane.sequence <= 0
                    || !validAnchor(lane.spineIndex, lane.byteOffset)
                    || (previous != null
                        && previous.compareTo(lane.deviceId) >= 0)) {
                    throw new IOException("Noncanonical O1RP lane");
                }
                writeAscii(output, lane.deviceId, DEVICE_ID_BYTES);
                output.writeLong(lane.sequence);
                output.writeLong(lane.spineIndex);
                output.writeLong(lane.byteOffset);
                previous = lane.deviceId;
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
        if (result.length <= 0 || result.length > MAX_V1_BYTES) {
            throw new IOException("O1RP snapshot exceeds its bound");
        }
        return result;
    }

    static DecodeResult decode(byte[] bytes) {
        if (bytes == null || bytes.length < 2 * Integer.BYTES) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
        if (bytes.length > MAX_FUTURE_INPUT_BYTES) {
            return new DecodeResult(DecodeStatus.LIMIT, null, null);
        }
        int magic = readInt(bytes, 0);
        int version = readInt(bytes, Integer.BYTES);
        if (magic == MAGIC && version > VERSION) {
            return new DecodeResult(
                DecodeStatus.FUTURE_VERSION, null, bytes);
        }
        if (magic != MAGIC || version != VERSION) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
        if (bytes.length > MAX_V1_BYTES) {
            return new DecodeResult(DecodeStatus.LIMIT, null, null);
        }
        if (bytes.length < minimumV1Bytes()) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
        try {
            int payloadLength = bytes.length - Integer.BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(bytes, 0, payloadLength);
            if (readInt(bytes, payloadLength)
                != (int)checksum.getValue()) {
                throw new IOException("Invalid O1RP checksum");
            }
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != MAGIC
                || input.readInt() != VERSION
                || input.readInt() != HEADER_FIELD_COUNT) {
                throw new IOException("Invalid O1RP header");
            }
            String bookDigest = readAscii(
                input, BOOK_DIGEST_BYTES, true);
            int laneCount = input.readInt();
            if (laneCount < 0) {
                return new DecodeResult(DecodeStatus.INVALID, null, null);
            }
            if (laneCount > MAX_LANES) {
                return new DecodeResult(DecodeStatus.LIMIT, null, null);
            }
            ArrayList<Lane> lanes = new ArrayList<>(laneCount);
            String previous = null;
            for (int index = 0; index < laneCount; ++index) {
                String deviceId = readAscii(
                    input, DEVICE_ID_BYTES, false);
                long sequence = input.readLong();
                long spineIndex = input.readLong();
                long byteOffset = input.readLong();
                if (sequence <= 0
                    || !validAnchor(spineIndex, byteOffset)
                    || (previous != null
                        && previous.compareTo(deviceId) >= 0)) {
                    throw new IOException("Invalid O1RP lane");
                }
                lanes.add(new Lane(
                    deviceId, sequence, spineIndex, byteOffset));
                previous = deviceId;
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1RP payload");
            }
            return new DecodeResult(
                DecodeStatus.READY,
                new Snapshot(bookDigest, lanes),
                null);
        } catch (EOFException exception) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        } catch (IOException | RuntimeException exception) {
            return new DecodeResult(DecodeStatus.INVALID, null, null);
        }
    }

    static MergeResult merge(Snapshot local, Snapshot remote) {
        if (local == null || remote == null
            || !validBookDigest(local.bookDigest)
            || !validBookDigest(remote.bookDigest)) {
            return new MergeResult(MergeStatus.INVALID, local);
        }
        if (!local.bookDigest.equals(remote.bookDigest)) {
            return new MergeResult(MergeStatus.WRONG_BOOK, local);
        }
        TreeMap<String, Lane> joined = new TreeMap<>();
        for (Lane lane : local.lanes()) {
            joined.put(lane.deviceId, lane);
        }
        boolean changed = false;
        for (Lane incoming : remote.lanes()) {
            Lane existing = joined.get(incoming.deviceId);
            if (existing == null) {
                if (joined.size() >= MAX_LANES) {
                    return new MergeResult(MergeStatus.LIMIT, local);
                }
                joined.put(incoming.deviceId, incoming);
                changed = true;
            } else if (incoming.sequence > existing.sequence) {
                joined.put(incoming.deviceId, incoming);
                changed = true;
            } else if (incoming.sequence == existing.sequence
                       && !incoming.samePosition(existing)) {
                return new MergeResult(MergeStatus.EQUIVOCATION, local);
            }
        }
        if (!changed) {
            return new MergeResult(MergeStatus.UNCHANGED, local);
        }
        return new MergeResult(
            MergeStatus.MERGED,
            new Snapshot(local.bookDigest, joined.values()));
    }

    static List<Lane> reviewOrder(Collection<Lane> lanes) {
        if (lanes == null || lanes.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Lane> ordered = new ArrayList<>(lanes);
        ordered.sort(REVIEW_ORDER);
        return Collections.unmodifiableList(ordered);
    }

    static byte[] simulatedRemoteBytes(String bookDigest,
                                       String deviceId,
                                       long sequence,
                                       long spineIndex,
                                       long byteOffset)
        throws IOException {
        return encode(new Snapshot(
            bookDigest,
            Collections.singletonList(new Lane(
                deviceId, sequence, spineIndex, byteOffset))));
    }

    static boolean validBookDigest(String value) {
        return validLowerHex(value, BOOK_DIGEST_BYTES);
    }

    static boolean validDeviceId(String value) {
        return validLowerHex(value, DEVICE_ID_BYTES);
    }

    static boolean validAnchor(long spineIndex, long byteOffset) {
        return spineIndex >= 0 && spineIndex <= MAX_SPINE_INDEX
            && byteOffset >= 0;
    }

    static int maximumLaneCount() {
        return MAX_LANES;
    }

    static int maximumV1Bytes() {
        return MAX_V1_BYTES;
    }

    static int magicForTesting() {
        return MAGIC;
    }

    static int versionForTesting() {
        return VERSION;
    }

    private static int minimumV1Bytes() {
        return 3 * Integer.BYTES
            + Integer.BYTES + BOOK_DIGEST_BYTES
            + Integer.BYTES
            + Integer.BYTES;
    }

    private static void writeAscii(DataOutputStream output,
                                   String value,
                                   int expectedBytes)
        throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != expectedBytes) {
            throw new IOException("Invalid canonical text length");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readAscii(DataInputStream input,
                                    int expectedBytes,
                                    boolean digest)
        throws IOException {
        int length = input.readInt();
        if (length != expectedBytes) {
            throw new IOException("Invalid canonical text length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (!((unsigned >= '0' && unsigned <= '9')
                  || (unsigned >= 'a' && unsigned <= 'f'))) {
                throw new IOException("Noncanonical hexadecimal text");
            }
        }
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (digest ? !validBookDigest(result) : !validDeviceId(result)) {
            throw new IOException("Invalid canonical hexadecimal text");
        }
        return result;
    }

    private static boolean validLowerHex(String value, int length) {
        if (value == null || value.length() != length) {
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

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }
}
