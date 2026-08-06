package ro.devze.octavo;

import java.util.Arrays;

/** Bounded, validated Java copy of Reader0's Android search projection. */
final class OctavoSearch {
    static final int VERSION = 1;
    static final int HEADER_COUNT = 10;
    static final int ROW_STRIDE = 5;
    static final int MAX_ROWS = 64;
    static final int MAX_QUERY_UTF8_BYTES = 127;

    private static final int FLAG_READY = 1;
    private static final int FLAG_TRUNCATED = 1 << 1;
    private static final int FLAG_PENDING = 1 << 2;
    private static final int KNOWN_FLAGS =
        FLAG_READY | FLAG_TRUNCATED | FLAG_PENDING;

    static final class Row {
        private final int index;
        private final int spineIndex;
        private final long startByte;
        private final long endByte;
        private final int matchStart;
        private final int matchSize;
        private final String section;
        private final String snippet;

        Row(int index,
            int spineIndex,
            long startByte,
            long endByte,
            int matchStart,
            int matchSize,
            String section,
            String snippet) {
            this.index = index;
            this.spineIndex = spineIndex;
            this.startByte = startByte;
            this.endByte = endByte;
            this.matchStart = matchStart;
            this.matchSize = matchSize;
            this.section = section;
            this.snippet = snippet;
        }

        int index() { return index; }
        int spineIndex() { return spineIndex; }
        long startByte() { return startByte; }
        long endByte() { return endByte; }
        int matchStart() { return matchStart; }
        int matchSize() { return matchSize; }
        String section() { return section; }
        String snippet() { return snippet; }
    }

    private final int flags;
    private final int totalCount;
    private final int activeIndex;
    private final long generation;
    private final String query;
    private final Row[] rows;

    private OctavoSearch(int flags,
                         int totalCount,
                         int activeIndex,
                         long generation,
                         String query,
                         Row[] rows) {
        this.flags = flags;
        this.totalCount = totalCount;
        this.activeIndex = activeIndex;
        this.generation = generation;
        this.query = query;
        this.rows = rows;
    }

    static OctavoSearch fromNativePacket(long[] packet,
                                         String query,
                                         String[] sections,
                                         String[] snippets) {
        byte[] queryUtf8 = query == null ? null
            : query.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (packet == null || packet.length < HEADER_COUNT
            || packet[0] != VERSION || packet[1] != HEADER_COUNT
            || packet[2] != ROW_STRIDE || packet[3] < 0
            || (packet[3] & ~KNOWN_FLAGS) != 0 || packet[4] < 0
            || packet[4] > MAX_ROWS || packet[5] < packet[4]
            || packet[5] > Integer.MAX_VALUE || packet[6] < -1
            || packet[6] >= packet[4]
            || packet[8] < 0 || packet[8] > MAX_QUERY_UTF8_BYTES
            || packet[9] < 0 || packet[9] > 1
            || packet.length != HEADER_COUNT + packet[4] * ROW_STRIDE) {
            return null;
        }
        int rowCount = (int)packet[4];
        boolean expectedTruncated = packet[9] != 0
            || packet[5] > rowCount;
        if (queryUtf8 == null || query.indexOf('\0') >= 0
            || queryUtf8.length != packet[8]
            || ((packet[3] & FLAG_TRUNCATED) != 0) != expectedTruncated
            || sections == null || snippets == null
            || sections.length != rowCount || snippets.length != rowCount) {
            return null;
        }
        Row[] rows = new Row[rowCount];
        for (int index = 0; index < rowCount; ++index) {
            if (sections[index] == null || snippets[index] == null) {
                return null;
            }
            int at = HEADER_COUNT + index * ROW_STRIDE;
            long spine = packet[at];
            long start = packet[at + 1];
            long end = packet[at + 2];
            long matchStart = packet[at + 3];
            long matchSize = packet[at + 4];
            if (spine < 0 || spine > Integer.MAX_VALUE || start < 0
                || end < start || matchStart < 0 || matchSize < 0
                || matchStart + matchSize < matchStart
                || matchStart + matchSize
                    > snippets[index].getBytes(
                        java.nio.charset.StandardCharsets.UTF_8).length) {
                return null;
            }
            rows[index] = new Row(index,
                                  (int)spine,
                                  start,
                                  end,
                                  (int)matchStart,
                                  (int)matchSize,
                                  sections[index],
                                  snippets[index]);
        }
        return new OctavoSearch((int)packet[3],
                                (int)packet[5],
                                (int)packet[6],
                                packet[7],
                                query,
                                rows);
    }

    boolean isReady() { return (flags & FLAG_READY) != 0; }
    boolean isTruncated() { return (flags & FLAG_TRUNCATED) != 0; }
    boolean isPending() { return (flags & FLAG_PENDING) != 0; }
    int rowCount() { return rows.length; }
    int totalCount() { return totalCount; }
    int activeIndex() { return activeIndex; }
    long generation() { return generation; }
    String query() { return query; }
    Row row(int index) { return rows[index]; }

    Row[] rowsForTesting() { return Arrays.copyOf(rows, rows.length); }
}
