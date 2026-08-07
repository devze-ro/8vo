package ro.devze.octavo;

/** Validated bounded snapshot of the Reader0-owned native selection. */
final class OctavoSelection {
    static final int VERSION = 1;
    static final int FIELD_COUNT = 14;
    static final int FLAG_READY = 1;
    static final int FLAG_ACTIVE = 1 << 1;
    static final int FLAG_PENDING = 1 << 2;

    final boolean ready;
    final boolean active;
    final boolean pending;
    final long startByte;
    final long endByte;
    final int startX;
    final int startY;
    final int endX;
    final int endY;
    final int startRowHeight;
    final int endRowHeight;
    final long generation;
    final long presentedGeneration;
    final long failureCount;

    private OctavoSelection(long[] values) {
        int flags = (int)values[2];
        ready = (flags & FLAG_READY) != 0;
        active = (flags & FLAG_ACTIVE) != 0;
        pending = (flags & FLAG_PENDING) != 0;
        startByte = values[3];
        endByte = values[4];
        startX = boundedInt(values[5]);
        startY = boundedInt(values[6]);
        endX = boundedInt(values[7]);
        endY = boundedInt(values[8]);
        startRowHeight = boundedInt(values[9]);
        endRowHeight = boundedInt(values[10]);
        generation = values[11];
        presentedGeneration = values[12];
        failureCount = values[13];
    }

    static OctavoSelection fromNative(long[] values) {
        if (values == null || values.length != FIELD_COUNT
            || values[0] != VERSION || values[1] != FIELD_COUNT
            || values[2] < 0 || values[2] > 7
            || values[3] < 0 || values[4] < 0
            || values[3] > values[4] || values[9] < 0 || values[10] < 0
            || values[11] < 0 || values[12] < 0 || values[13] < 0) {
            return null;
        }
        OctavoSelection result = new OctavoSelection(values);
        if (result.active
            && (!result.ready || result.endByte <= result.startByte
                || result.startRowHeight <= 0 || result.endRowHeight <= 0)) {
            return null;
        }
        if (result.pending && !result.ready) {
            return null;
        }
        return result;
    }

    private static int boundedInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int)value;
    }
}
