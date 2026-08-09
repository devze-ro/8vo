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

/** Canonical, bounded O1PF global-appearance bytes. */
final class OctavoAppearancePortable {
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

    /**
     * The values below are O1PF semantic identifiers. Mapping is explicit
     * even where an Android value currently has the same integer.
     */
    static final class Profile {
        static final int THEME_PAPER = 0;
        static final int THEME_SEPIA = 1;
        static final int THEME_DUSK = 2;
        static final int THEME_WARM_DARK = 3;
        static final int THEME_OLED = 4;
        static final int THEME_HIGH_CONTRAST = 5;

        static final int FONT_LITERARY = 0;
        static final int FONT_CLEAR = 1;

        static final int SIZE_COMPACT = 0;
        static final int SIZE_STANDARD = 1;
        static final int SIZE_COMFORTABLE = 2;
        static final int SIZE_LARGE = 3;
        static final int SIZE_LARGER = 4;
        static final int SIZE_LARGEST = 5;

        static final int SPACING_COMPACT = 0;
        static final int SPACING_CLASSIC = 1;
        static final int SPACING_COMFORTABLE = 2;
        static final int SPACING_SPACIOUS = 3;

        static final int WIDTH_WIDE = 0;
        static final int WIDTH_BALANCED = 1;
        static final int WIDTH_FOCUSED = 2;

        static final int ALIGNMENT_PUBLISHER = 0;
        static final int ALIGNMENT_RAGGED_RIGHT = 1;

        static final int COLORS_THEME_SAFE = 0;
        static final int COLORS_ALLOW_PUBLISHER = 1;

        static final int MOTION_OFF = 0;
        static final int MOTION_ON = 1;

        final int theme;
        final int fontIntent;
        final int textSizeTier;
        final int lineSpacingTier;
        final int width;
        final int alignment;
        final int publisherColors;
        final int reducedMotion;

        Profile(int theme,
                int fontIntent,
                int textSizeTier,
                int lineSpacingTier,
                int width,
                int alignment,
                int publisherColors,
                int reducedMotion) {
            if (!inRange(theme, 6)
                || !inRange(fontIntent, 2)
                || !inRange(textSizeTier, 6)
                || !inRange(lineSpacingTier, 4)
                || !inRange(width, 3)
                || !inRange(alignment, 2)
                || !inRange(publisherColors, 2)
                || !inRange(reducedMotion, 2)) {
                throw new IllegalArgumentException(
                    "Invalid portable appearance profile");
            }
            this.theme = theme;
            this.fontIntent = fontIntent;
            this.textSizeTier = textSizeTier;
            this.lineSpacingTier = lineSpacingTier;
            this.width = width;
            this.alignment = alignment;
            this.publisherColors = publisherColors;
            this.reducedMotion = reducedMotion;
        }

        static Profile fromAppearance(OctavoAppearance appearance) {
            if (appearance == null) {
                throw new IllegalArgumentException("Missing appearance");
            }
            return new Profile(
                portableTheme(appearance.themeId()),
                portableFont(appearance.fontFamilyId()),
                portableSize(appearance.fontSizeSp()),
                portableSpacing(appearance.lineSpacingPermille()),
                portableWidth(appearance.marginsId()),
                portableAlignment(appearance.alignmentId()),
                portableColors(appearance.publisherColorsId()),
                appearance.reducedMotion() ? MOTION_ON : MOTION_OFF);
        }

        OctavoAppearance toAppearance() {
            return OctavoAppearance.create(
                androidTheme(theme),
                androidFont(fontIntent),
                androidSize(textSizeTier),
                androidSpacing(lineSpacingTier),
                androidWidth(width),
                androidAlignment(alignment),
                androidColors(publisherColors),
                reducedMotion == MOTION_ON);
        }

        int differenceCount(Profile other) {
            if (other == null) {
                return PROFILE_FIELD_COUNT;
            }
            int result = 0;
            result += theme == other.theme ? 0 : 1;
            result += fontIntent == other.fontIntent ? 0 : 1;
            result += textSizeTier == other.textSizeTier ? 0 : 1;
            result += lineSpacingTier == other.lineSpacingTier ? 0 : 1;
            result += width == other.width ? 0 : 1;
            result += alignment == other.alignment ? 0 : 1;
            result += publisherColors == other.publisherColors ? 0 : 1;
            result += reducedMotion == other.reducedMotion ? 0 : 1;
            return result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Profile)) {
                return false;
            }
            Profile other = (Profile)object;
            return theme == other.theme
                && fontIntent == other.fontIntent
                && textSizeTier == other.textSizeTier
                && lineSpacingTier == other.lineSpacingTier
                && width == other.width
                && alignment == other.alignment
                && publisherColors == other.publisherColors
                && reducedMotion == other.reducedMotion;
        }

        @Override
        public int hashCode() {
            int result = theme;
            result = 31 * result + fontIntent;
            result = 31 * result + textSizeTier;
            result = 31 * result + lineSpacingTier;
            result = 31 * result + width;
            result = 31 * result + alignment;
            result = 31 * result + publisherColors;
            return 31 * result + reducedMotion;
        }

        private static int portableTheme(int value) {
            switch (value) {
                case OctavoAppearance.THEME_PAPER: return THEME_PAPER;
                case OctavoAppearance.THEME_SEPIA: return THEME_SEPIA;
                case OctavoAppearance.THEME_DUSK: return THEME_DUSK;
                case OctavoAppearance.THEME_WARM_DARK:
                    return THEME_WARM_DARK;
                case OctavoAppearance.THEME_OLED: return THEME_OLED;
                case OctavoAppearance.THEME_HIGH_CONTRAST:
                    return THEME_HIGH_CONTRAST;
                default: throw new IllegalArgumentException("Unknown theme");
            }
        }

        private static int androidTheme(int value) {
            switch (value) {
                case THEME_PAPER: return OctavoAppearance.THEME_PAPER;
                case THEME_SEPIA: return OctavoAppearance.THEME_SEPIA;
                case THEME_DUSK: return OctavoAppearance.THEME_DUSK;
                case THEME_WARM_DARK:
                    return OctavoAppearance.THEME_WARM_DARK;
                case THEME_OLED: return OctavoAppearance.THEME_OLED;
                case THEME_HIGH_CONTRAST:
                    return OctavoAppearance.THEME_HIGH_CONTRAST;
                default: throw new IllegalArgumentException("Unknown theme");
            }
        }

        private static int portableFont(int value) {
            switch (value) {
                case OctavoAppearance.FONT_FAMILY_LITERARY:
                    return FONT_LITERARY;
                case OctavoAppearance.FONT_FAMILY_CLEAR: return FONT_CLEAR;
                default: throw new IllegalArgumentException("Unknown font");
            }
        }

        private static int androidFont(int value) {
            switch (value) {
                case FONT_LITERARY:
                    return OctavoAppearance.FONT_FAMILY_LITERARY;
                case FONT_CLEAR: return OctavoAppearance.FONT_FAMILY_CLEAR;
                default: throw new IllegalArgumentException("Unknown font");
            }
        }

        private static int portableSize(int value) {
            switch (value) {
                case 14: return SIZE_COMPACT;
                case 16: return SIZE_STANDARD;
                case 18: return SIZE_COMFORTABLE;
                case 21: return SIZE_LARGE;
                case 24: return SIZE_LARGER;
                case 28: return SIZE_LARGEST;
                default: throw new IllegalArgumentException("Unknown size");
            }
        }

        private static int androidSize(int value) {
            switch (value) {
                case SIZE_COMPACT: return 14;
                case SIZE_STANDARD: return 16;
                case SIZE_COMFORTABLE: return 18;
                case SIZE_LARGE: return 21;
                case SIZE_LARGER: return 24;
                case SIZE_LARGEST: return 28;
                default: throw new IllegalArgumentException("Unknown size");
            }
        }

        private static int portableSpacing(int value) {
            switch (value) {
                case 1150: return SPACING_COMPACT;
                case 1250: return SPACING_CLASSIC;
                case 1300: return SPACING_COMFORTABLE;
                case 1500: return SPACING_SPACIOUS;
                default:
                    throw new IllegalArgumentException("Unknown spacing");
            }
        }

        private static int androidSpacing(int value) {
            switch (value) {
                case SPACING_COMPACT: return 1150;
                case SPACING_CLASSIC: return 1250;
                case SPACING_COMFORTABLE: return 1300;
                case SPACING_SPACIOUS: return 1500;
                default:
                    throw new IllegalArgumentException("Unknown spacing");
            }
        }

        private static int portableWidth(int value) {
            switch (value) {
                case OctavoAppearance.MARGINS_WIDE: return WIDTH_WIDE;
                case OctavoAppearance.MARGINS_BALANCED:
                    return WIDTH_BALANCED;
                case OctavoAppearance.MARGINS_FOCUSED:
                    return WIDTH_FOCUSED;
                default: throw new IllegalArgumentException("Unknown width");
            }
        }

        private static int androidWidth(int value) {
            switch (value) {
                case WIDTH_WIDE: return OctavoAppearance.MARGINS_WIDE;
                case WIDTH_BALANCED:
                    return OctavoAppearance.MARGINS_BALANCED;
                case WIDTH_FOCUSED:
                    return OctavoAppearance.MARGINS_FOCUSED;
                default: throw new IllegalArgumentException("Unknown width");
            }
        }

        private static int portableAlignment(int value) {
            switch (value) {
                case OctavoAppearance.ALIGNMENT_PUBLISHER:
                    return ALIGNMENT_PUBLISHER;
                case OctavoAppearance.ALIGNMENT_RAGGED_RIGHT:
                    return ALIGNMENT_RAGGED_RIGHT;
                default:
                    throw new IllegalArgumentException("Unknown alignment");
            }
        }

        private static int androidAlignment(int value) {
            switch (value) {
                case ALIGNMENT_PUBLISHER:
                    return OctavoAppearance.ALIGNMENT_PUBLISHER;
                case ALIGNMENT_RAGGED_RIGHT:
                    return OctavoAppearance.ALIGNMENT_RAGGED_RIGHT;
                default:
                    throw new IllegalArgumentException("Unknown alignment");
            }
        }

        private static int portableColors(int value) {
            switch (value) {
                case OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE:
                    return COLORS_THEME_SAFE;
                case OctavoAppearance.PUBLISHER_COLORS_ALLOW:
                    return COLORS_ALLOW_PUBLISHER;
                default: throw new IllegalArgumentException("Unknown colors");
            }
        }

        private static int androidColors(int value) {
            switch (value) {
                case COLORS_THEME_SAFE:
                    return OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE;
                case COLORS_ALLOW_PUBLISHER:
                    return OctavoAppearance.PUBLISHER_COLORS_ALLOW;
                default: throw new IllegalArgumentException("Unknown colors");
            }
        }

        private static boolean inRange(int value, int onePastLast) {
            return value >= 0 && value < onePastLast;
        }
    }

    static final class Lane {
        final String deviceId;
        final long sequence;
        final Profile profile;

        Lane(String deviceId, long sequence, Profile profile) {
            if (!validDeviceId(deviceId) || sequence <= 0
                || profile == null) {
                throw new IllegalArgumentException(
                    "Invalid portable appearance lane");
            }
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.profile = profile;
        }

        boolean sameProfile(Lane other) {
            return other != null && profile.equals(other.profile);
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
                && profile.equals(other.profile);
        }

        @Override
        public int hashCode() {
            int result = deviceId.hashCode();
            result = 31 * result + Long.hashCode(sequence);
            return 31 * result + profile.hashCode();
        }
    }

    static final class Snapshot {
        private final TreeMap<String, Lane> lanes;

        Snapshot(Collection<Lane> source) {
            if (source == null || source.size() > MAX_LANES) {
                throw new IllegalArgumentException("Invalid O1PF snapshot");
            }
            TreeMap<String, Lane> copied = new TreeMap<>();
            for (Lane lane : source) {
                if (lane == null || copied.put(lane.deviceId, lane) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate O1PF lane");
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

    private static final int MAGIC = 0x4F315046; // "O1PF"
    private static final int VERSION = 1;
    private static final int PROFILE_FIELD_COUNT = 8;
    private static final int MAX_LANES = 16;
    private static final int DEVICE_ID_BYTES = 32;
    private static final int MINIMUM_V1_BYTES = 20;
    private static final int LANE_BYTES = 72;
    private static final int MAXIMUM_V1_BYTES = 1172;
    private static final int MAXIMUM_FUTURE_BYTES = 65_536;

    private OctavoAppearancePortable() {
    }

    static byte[] encode(Snapshot snapshot) throws IOException {
        if (snapshot == null || snapshot.laneCount() > MAX_LANES) {
            throw new IOException("Invalid O1PF snapshot");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(PROFILE_FIELD_COUNT);
            output.writeInt(snapshot.laneCount());
            String previous = null;
            for (Lane lane : snapshot.lanes()) {
                if (lane == null || !validDeviceId(lane.deviceId)
                    || lane.sequence <= 0 || lane.profile == null
                    || (previous != null
                        && previous.compareTo(lane.deviceId) >= 0)) {
                    throw new IOException("Noncanonical O1PF lane");
                }
                writeDeviceId(output, lane.deviceId);
                output.writeLong(lane.sequence);
                writeProfile(output, lane.profile);
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
            throw new IOException("O1PF snapshot exceeds its bound");
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
            if (fieldCount != PROFILE_FIELD_COUNT || laneCount < 0) {
                throw new IOException("Invalid O1PF header");
            }
            if (laneCount > MAX_LANES) {
                return new DecodeResult(DecodeStatus.LIMIT, null, null);
            }
            int expectedLength = MINIMUM_V1_BYTES
                + LANE_BYTES * laneCount;
            if (bytes.length != expectedLength) {
                throw new IOException("Invalid O1PF length");
            }
            int payloadLength = bytes.length - Integer.BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(bytes, 0, payloadLength);
            if (readInt(bytes, payloadLength)
                != (int)checksum.getValue()) {
                throw new IOException("Invalid O1PF checksum");
            }
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != MAGIC
                || input.readInt() != VERSION
                || input.readInt() != PROFILE_FIELD_COUNT
                || input.readInt() != laneCount) {
                throw new IOException("Invalid O1PF header");
            }
            ArrayList<Lane> lanes = new ArrayList<>(laneCount);
            String previous = null;
            for (int index = 0; index < laneCount; ++index) {
                String deviceId = readDeviceId(input);
                long sequence = input.readLong();
                Profile profile = readProfile(input);
                if (sequence <= 0
                    || (previous != null
                        && previous.compareTo(deviceId) >= 0)) {
                    throw new IOException("Invalid O1PF lane");
                }
                lanes.add(new Lane(deviceId, sequence, profile));
                previous = deviceId;
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1PF payload");
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
                       && !incoming.sameProfile(existing)) {
                return new MergeResult(MergeStatus.EQUIVOCATION, local);
            }
        }
        return changed
            ? new MergeResult(
                MergeStatus.MERGED, new Snapshot(joined.values()))
            : new MergeResult(MergeStatus.UNCHANGED, local);
    }

    static List<Lane> reviewOrder(Collection<Lane> lanes) {
        if (lanes == null || lanes.isEmpty()) {
            return Collections.emptyList();
        }
        TreeMap<String, Lane> sorted = new TreeMap<>();
        for (Lane lane : lanes) {
            if (lane == null || sorted.put(lane.deviceId, lane) != null) {
                throw new IllegalArgumentException("Invalid review lanes");
            }
        }
        return Collections.unmodifiableList(
            new ArrayList<>(sorted.values()));
    }

    static byte[] simulatedRemoteBytes(String deviceId,
                                       long sequence,
                                       OctavoAppearance appearance)
        throws IOException {
        return encode(new Snapshot(Collections.singletonList(
            new Lane(deviceId, sequence,
                     Profile.fromAppearance(appearance)))));
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
            throw new IOException("Invalid O1PF device identity");
        }
        output.write(bytes);
    }

    private static String readDeviceId(DataInputStream input)
        throws IOException {
        byte[] bytes = new byte[DEVICE_ID_BYTES];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (!validDeviceId(result)) {
            throw new IOException("Invalid O1PF device identity");
        }
        return result;
    }

    private static void writeProfile(DataOutputStream output,
                                     Profile profile)
        throws IOException {
        if (profile == null) {
            throw new IOException("Missing O1PF profile");
        }
        output.writeInt(profile.theme);
        output.writeInt(profile.fontIntent);
        output.writeInt(profile.textSizeTier);
        output.writeInt(profile.lineSpacingTier);
        output.writeInt(profile.width);
        output.writeInt(profile.alignment);
        output.writeInt(profile.publisherColors);
        output.writeInt(profile.reducedMotion);
    }

    private static Profile readProfile(DataInputStream input)
        throws IOException {
        try {
            return new Profile(
                input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid O1PF profile", exception);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }
}
