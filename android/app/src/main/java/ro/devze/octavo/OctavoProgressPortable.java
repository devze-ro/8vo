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

/** Canonical, bounded O1PC global progress-display choice bytes. */
final class OctavoProgressPortable {
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

    static final class Choice {
        static final int CHAPTER = 0;
        static final int PAGE = 1;
        static final int LOCATION = 2;
        static final int PERCENTAGE = 3;

        final int semanticId;

        Choice(int semanticId) {
            if (semanticId < CHAPTER || semanticId > PERCENTAGE) {
                throw new IllegalArgumentException(
                    "Invalid portable progress choice");
            }
            this.semanticId = semanticId;
        }

        static Choice fromDisplay(OctavoProgressDisplay display) {
            if (display == null) {
                throw new IllegalArgumentException(
                    "Missing progress display");
            }
            switch (display) {
                case CHAPTER: return new Choice(CHAPTER);
                case PAGE: return new Choice(PAGE);
                case LOCATION: return new Choice(LOCATION);
                case PERCENTAGE: return new Choice(PERCENTAGE);
                default:
                    throw new IllegalArgumentException(
                        "Unknown progress display");
            }
        }

        OctavoProgressDisplay toDisplay() {
            switch (semanticId) {
                case CHAPTER: return OctavoProgressDisplay.CHAPTER;
                case PAGE: return OctavoProgressDisplay.PAGE;
                case LOCATION: return OctavoProgressDisplay.LOCATION;
                case PERCENTAGE: return OctavoProgressDisplay.PERCENTAGE;
                default:
                    throw new IllegalStateException(
                        "Unknown portable progress choice");
            }
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Choice
                && semanticId == ((Choice)object).semanticId;
        }

        @Override
        public int hashCode() {
            return semanticId;
        }
    }

    static final class Lane {
        final String deviceId;
        final long sequence;
        final Choice choice;

        Lane(String deviceId, long sequence, Choice choice) {
            if (!validDeviceId(deviceId) || sequence <= 0
                || choice == null) {
                throw new IllegalArgumentException(
                    "Invalid portable progress lane");
            }
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.choice = choice;
        }

        boolean sameChoice(Lane other) {
            return other != null && choice.equals(other.choice);
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
                && choice.equals(other.choice);
        }

        @Override
        public int hashCode() {
            int result = deviceId.hashCode();
            result = 31 * result + Long.hashCode(sequence);
            return 31 * result + choice.hashCode();
        }
    }

    static final class Snapshot {
        private final TreeMap<String, Lane> lanes;

        Snapshot(Collection<Lane> source) {
            if (source == null || source.size() > MAX_LANES) {
                throw new IllegalArgumentException(
                    "Invalid O1PC snapshot");
            }
            TreeMap<String, Lane> copied = new TreeMap<>();
            for (Lane lane : source) {
                if (lane == null || copied.put(lane.deviceId, lane) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate O1PC lane");
                }
            }
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

        @Override
        public boolean equals(Object object) {
            return object instanceof Snapshot
                && lanes.equals(((Snapshot)object).lanes);
        }

        @Override
        public int hashCode() {
            return lanes.hashCode();
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

    private static final int MAGIC = 0x4F315043; // "O1PC"
    private static final int VERSION = 1;
    private static final int CHOICE_FIELD_COUNT = 1;
    private static final int MAX_LANES = 16;
    private static final int DEVICE_ID_BYTES = 32;
    private static final int MINIMUM_V1_BYTES = 20;
    private static final int LANE_BYTES = 44;
    private static final int MAXIMUM_V1_BYTES = 724;
    private static final int MAXIMUM_FUTURE_BYTES = 65_536;

    private OctavoProgressPortable() {
    }

    static byte[] encode(Snapshot snapshot) throws IOException {
        if (snapshot == null || snapshot.laneCount() > MAX_LANES) {
            throw new IOException("Invalid O1PC snapshot");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(CHOICE_FIELD_COUNT);
            output.writeInt(snapshot.laneCount());
            String previous = null;
            for (Lane lane : snapshot.lanes()) {
                if (lane == null || !validDeviceId(lane.deviceId)
                    || lane.sequence <= 0 || lane.choice == null
                    || (previous != null
                        && previous.compareTo(lane.deviceId) >= 0)) {
                    throw new IOException("Noncanonical O1PC lane");
                }
                writeDeviceId(output, lane.deviceId);
                output.writeLong(lane.sequence);
                output.writeInt(lane.choice.semanticId);
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
        int expected = MINIMUM_V1_BYTES
            + LANE_BYTES * snapshot.laneCount();
        if (result.length != expected
            || result.length > MAXIMUM_V1_BYTES) {
            throw new IOException("O1PC snapshot exceeds its bound");
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
            int laneCount = readInt(bytes, 3 * Integer.BYTES);
            if (fieldCount != CHOICE_FIELD_COUNT || laneCount < 0) {
                throw new IOException("Invalid O1PC header");
            }
            if (laneCount > MAX_LANES) {
                return new DecodeResult(DecodeStatus.LIMIT, null, null);
            }
            int expectedLength = MINIMUM_V1_BYTES
                + LANE_BYTES * laneCount;
            if (bytes.length != expectedLength) {
                throw new IOException("Invalid O1PC length");
            }
            int payloadLength = bytes.length - Integer.BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(bytes, 0, payloadLength);
            if (readInt(bytes, payloadLength)
                != (int)checksum.getValue()) {
                throw new IOException("Invalid O1PC checksum");
            }
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != MAGIC
                || input.readInt() != VERSION
                || input.readInt() != CHOICE_FIELD_COUNT
                || input.readInt() != laneCount) {
                throw new IOException("Invalid O1PC header");
            }
            ArrayList<Lane> lanes = new ArrayList<>(laneCount);
            String previous = null;
            for (int index = 0; index < laneCount; ++index) {
                String deviceId = readDeviceId(input);
                long sequence = input.readLong();
                Choice choice = new Choice(input.readInt());
                if (sequence <= 0
                    || (previous != null
                        && previous.compareTo(deviceId) >= 0)) {
                    throw new IOException("Invalid O1PC lane");
                }
                lanes.add(new Lane(deviceId, sequence, choice));
                previous = deviceId;
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1PC payload");
            }
            return new DecodeResult(
                DecodeStatus.READY, new Snapshot(lanes), null);
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
                       && !incoming.sameChoice(existing)) {
                return new MergeResult(MergeStatus.EQUIVOCATION, local);
            }
        }
        if (!changed) {
            return new MergeResult(MergeStatus.UNCHANGED, local);
        }
        return new MergeResult(
            MergeStatus.MERGED, new Snapshot(joined.values()));
    }

    static byte[] simulatedRemoteBytes(String deviceId,
                                       long sequence,
                                       OctavoProgressDisplay display)
        throws IOException {
        return encode(new Snapshot(Collections.singletonList(
            new Lane(deviceId, sequence, Choice.fromDisplay(display)))));
    }

    static boolean validDeviceId(String value) {
        if (value == null || value.length() != DEVICE_ID_BYTES) {
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

    static int laneBytes() {
        return LANE_BYTES;
    }

    static int maximumV1Bytes() {
        return MAXIMUM_V1_BYTES;
    }

    static int maximumFutureBytes() {
        return MAXIMUM_FUTURE_BYTES;
    }

    static int maximumLaneCount() {
        return MAX_LANES;
    }

    static int magicForTesting() {
        return MAGIC;
    }

    static int versionForTesting() {
        return VERSION;
    }

    private static void writeDeviceId(DataOutputStream output,
                                      String deviceId)
        throws IOException {
        byte[] bytes = deviceId.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != DEVICE_ID_BYTES || !validDeviceId(deviceId)) {
            throw new IOException("Invalid O1PC device identity");
        }
        output.write(bytes);
    }

    private static String readDeviceId(DataInputStream input)
        throws IOException {
        byte[] bytes = new byte[DEVICE_ID_BYTES];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (!validDeviceId(result)) {
            throw new IOException("Invalid O1PC device identity");
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
