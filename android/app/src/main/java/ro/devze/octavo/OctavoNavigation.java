package ro.devze.octavo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, validated host model for the bounded structural-navigation
 * packet exposed by the native reader.
 */
final class OctavoNavigation {
    static final int VERSION = 1;
    static final int HEADER_COUNT = 16;
    static final int ROW_STRIDE = 10;
    static final int MAX_ROWS = 64;

    static final int FLAG_READY = 1;
    static final int FLAG_FALLBACK = 1 << 1;
    static final int FLAG_TRUNCATED = 1 << 2;
    static final int FLAG_LOCATION_READY = 1 << 3;
    static final int FLAG_PAGE_MEANINGFUL = 1 << 4;
    static final int FLAG_PENDING = 1 << 5;

    static final int ROW_FLAG_CURRENT = 1;
    static final int ROW_FLAG_DESTINATION_VALID = 1 << 1;
    static final int ROW_FLAG_PROGRESS_AVAILABLE = 1 << 2;
    static final int ROW_FLAG_SYNTHETIC = 1 << 3;

    private static final int FLAG_MASK = FLAG_READY | FLAG_FALLBACK
        | FLAG_TRUNCATED | FLAG_LOCATION_READY | FLAG_PAGE_MEANINGFUL
        | FLAG_PENDING;
    private static final int ROW_FLAG_MASK = ROW_FLAG_CURRENT
        | ROW_FLAG_DESTINATION_VALID | ROW_FLAG_PROGRESS_AVAILABLE
        | ROW_FLAG_SYNTHETIC;
    static final int MAX_DEPTH = 64;
    private static final int MAX_LABEL_CHARS = 512;

    private final int flags;
    private final int totalCount;
    private final int currentRow;
    private final int historyBack;
    private final int historyForward;
    private final long transactionGeneration;
    private final long presentedGeneration;
    private final long currentLocation;
    private final long locationCount;
    private final int percent;
    private final long pageIndex;
    private final long pageCount;
    private final List<Row> rows;

    private OctavoNavigation(int flags,
                             int totalCount,
                             int currentRow,
                             int historyBack,
                             int historyForward,
                             long transactionGeneration,
                             long presentedGeneration,
                             long currentLocation,
                             long locationCount,
                             int percent,
                             long pageIndex,
                             long pageCount,
                             List<Row> rows) {
        this.flags = flags;
        this.totalCount = totalCount;
        this.currentRow = currentRow;
        this.historyBack = historyBack;
        this.historyForward = historyForward;
        this.transactionGeneration = transactionGeneration;
        this.presentedGeneration = presentedGeneration;
        this.currentLocation = currentLocation;
        this.locationCount = locationCount;
        this.percent = percent;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.rows = Collections.unmodifiableList(rows);
    }

    /**
     * Returns a validated snapshot or {@code null}. Invalid native data is
     * never partially exposed to the Android hierarchy.
     */
    static OctavoNavigation fromNativePacket(long[] packet,
                                              String[] labels) {
        if (packet == null || labels == null || packet.length < HEADER_COUNT
            || packet[0] != VERSION || packet[1] != HEADER_COUNT
            || packet[2] != ROW_STRIDE) {
            return null;
        }

        Integer flags = boundedInt(packet[3], 0, FLAG_MASK);
        Integer rowCount = boundedInt(packet[4], 0, MAX_ROWS);
        Integer totalCount = boundedInt(packet[5], 0, Integer.MAX_VALUE);
        if (flags == null || rowCount == null || totalCount == null
            || (flags & ~FLAG_MASK) != 0 || totalCount < rowCount
            || labels.length != rowCount
            || packet.length != HEADER_COUNT + rowCount * ROW_STRIDE) {
            return null;
        }

        Integer currentRow = boundedInt(packet[6], -1,
                                        Math.max(rowCount - 1, -1));
        Integer historyBack = boundedInt(packet[7], 0, Integer.MAX_VALUE);
        Integer historyForward = boundedInt(packet[8], 0,
                                             Integer.MAX_VALUE);
        long transactionGeneration = packet[9];
        long presentedGeneration = packet[10];
        long currentLocation = packet[11];
        long locationCount = packet[12];
        Integer percent = boundedInt(packet[13], 0, 100);
        long pageIndex = packet[14];
        long pageCount = packet[15];
        if (currentRow == null || historyBack == null
            || historyForward == null || percent == null
            || transactionGeneration < 0 || presentedGeneration < 0
            || presentedGeneration > transactionGeneration
            || currentLocation < 0 || locationCount < 0
            || pageIndex < 0 || pageCount < 0
            || !validProgress(flags, currentLocation, locationCount,
                              pageIndex, pageCount)) {
            return null;
        }

        List<Row> parsedRows = new ArrayList<>(rowCount);
        Set<Integer> navIndices = new HashSet<>();
        int previousDepth = 0;
        int currentCount = 0;
        int currentIndex = -1;
        for (int index = 0; index < rowCount; ++index) {
            String label = labels[index];
            if (label == null || label.trim().isEmpty()
                || label.length() > MAX_LABEL_CHARS) {
                return null;
            }

            int offset = HEADER_COUNT + index * ROW_STRIDE;
            Integer navIndex = boundedInt(packet[offset], 0,
                                          Integer.MAX_VALUE);
            Integer depth = boundedInt(packet[offset + 1], 0, MAX_DEPTH);
            Integer rowFlags = boundedInt(packet[offset + 2], 0,
                                          ROW_FLAG_MASK);
            long targetSpine = packet[offset + 3];
            long targetByte = packet[offset + 4];
            long rowLocation = packet[offset + 5];
            long rowLocationCount = packet[offset + 6];
            Integer rowPercent = boundedInt(packet[offset + 7], 0, 100);
            long rowPageIndex = packet[offset + 8];
            long rowPageCount = packet[offset + 9];
            if (navIndex == null || depth == null || rowFlags == null
                || (rowFlags & ~ROW_FLAG_MASK) != 0
                || targetSpine < 0 || targetByte < 0 || rowLocation < 0
                || rowLocationCount < 0 || rowPercent == null
                || rowPageIndex < 0 || rowPageCount < 0
                || (index == 0 && depth != 0)
                || (index > 0 && depth > previousDepth + 1)
                || !navIndices.add(navIndex)
                || !validRowProgress(rowFlags, rowLocation,
                                     rowLocationCount, rowPageIndex,
                                     rowPageCount)) {
                return null;
            }
            if ((rowFlags & ROW_FLAG_CURRENT) != 0) {
                ++currentCount;
                currentIndex = index;
            }
            parsedRows.add(new Row(navIndex,
                                   depth,
                                   rowFlags,
                                   targetSpine,
                                   targetByte,
                                   rowLocation,
                                   rowLocationCount,
                                   rowPercent,
                                   rowPageIndex,
                                   rowPageCount,
                                   label));
            previousDepth = depth;
        }

        if ((currentRow == -1 && currentCount != 0)
            || (currentRow >= 0
                && (currentCount != 1 || currentIndex != currentRow))) {
            return null;
        }

        return new OctavoNavigation(flags,
                                    totalCount,
                                    currentRow,
                                    historyBack,
                                    historyForward,
                                    transactionGeneration,
                                    presentedGeneration,
                                    currentLocation,
                                    locationCount,
                                    percent,
                                    pageIndex,
                                    pageCount,
                                    parsedRows);
    }

    private static boolean validProgress(int flags,
                                         long location,
                                         long locationCount,
                                         long pageIndex,
                                         long pageCount) {
        boolean locationReady = (flags & FLAG_LOCATION_READY) != 0;
        if (locationReady
            ? locationCount == 0 || location == 0 || location > locationCount
            : location != 0 || locationCount != 0) {
            return false;
        }
        boolean pageMeaningful = (flags & FLAG_PAGE_MEANINGFUL) != 0;
        return pageMeaningful
            ? pageCount > 0 && pageIndex > 0 && pageIndex <= pageCount
            : pageIndex == 0 && pageCount == 0;
    }

    private static boolean validRowProgress(int flags,
                                            long location,
                                            long locationCount,
                                            long pageIndex,
                                            long pageCount) {
        boolean available = (flags & ROW_FLAG_PROGRESS_AVAILABLE) != 0;
        return available
            ? locationCount > 0 && location > 0 && location <= locationCount
                && ((pageIndex == 0 && pageCount == 0)
                    || (pageCount > 0 && pageIndex > 0
                        && pageIndex <= pageCount))
            : location == 0 && locationCount == 0
                && pageIndex == 0 && pageCount == 0;
    }

    private static Integer boundedInt(long value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            return null;
        }
        return (int)value;
    }

    boolean isReady() {
        return (flags & FLAG_READY) != 0;
    }

    boolean isFallback() {
        return (flags & FLAG_FALLBACK) != 0;
    }

    boolean isTruncated() {
        return (flags & FLAG_TRUNCATED) != 0;
    }

    boolean isLocationReady() {
        return (flags & FLAG_LOCATION_READY) != 0;
    }

    boolean isPageMeaningful() {
        return (flags & FLAG_PAGE_MEANINGFUL) != 0;
    }

    boolean isPending() {
        return (flags & FLAG_PENDING) != 0;
    }

    int totalCount() {
        return totalCount;
    }

    int currentRow() {
        return currentRow;
    }

    int historyBack() {
        return historyBack;
    }

    int historyForward() {
        return historyForward;
    }

    long transactionGeneration() {
        return transactionGeneration;
    }

    long presentedGeneration() {
        return presentedGeneration;
    }

    long currentLocation() {
        return currentLocation;
    }

    long locationCount() {
        return locationCount;
    }

    int percent() {
        return percent;
    }

    long pageIndex() {
        return pageIndex;
    }

    long pageCount() {
        return pageCount;
    }

    int rowCount() {
        return rows.size();
    }

    Row row(int index) {
        return rows.get(index);
    }

    List<Row> rows() {
        return rows;
    }

    static final class Row {
        private final int navIndex;
        private final int depth;
        private final int flags;
        private final long targetSpine;
        private final long targetByte;
        private final long locationIndex;
        private final long locationCount;
        private final int percent;
        private final long pageIndex;
        private final long pageCount;
        private final String label;

        private Row(int navIndex,
                    int depth,
                    int flags,
                    long targetSpine,
                    long targetByte,
                    long locationIndex,
                    long locationCount,
                    int percent,
                    long pageIndex,
                    long pageCount,
                    String label) {
            this.navIndex = navIndex;
            this.depth = depth;
            this.flags = flags;
            this.targetSpine = targetSpine;
            this.targetByte = targetByte;
            this.locationIndex = locationIndex;
            this.locationCount = locationCount;
            this.percent = percent;
            this.pageIndex = pageIndex;
            this.pageCount = pageCount;
            this.label = label;
        }

        int navIndex() {
            return navIndex;
        }

        int depth() {
            return depth;
        }

        boolean isCurrent() {
            return (flags & ROW_FLAG_CURRENT) != 0;
        }

        boolean isDestinationValid() {
            return (flags & ROW_FLAG_DESTINATION_VALID) != 0;
        }

        boolean isProgressAvailable() {
            return (flags & ROW_FLAG_PROGRESS_AVAILABLE) != 0;
        }

        boolean isSynthetic() {
            return (flags & ROW_FLAG_SYNTHETIC) != 0;
        }

        long targetSpine() {
            return targetSpine;
        }

        long targetByte() {
            return targetByte;
        }

        long locationIndex() {
            return locationIndex;
        }

        long locationCount() {
            return locationCount;
        }

        int percent() {
            return percent;
        }

        long pageIndex() {
            return pageIndex;
        }

        long pageCount() {
            return pageCount;
        }

        String label() {
            return label;
        }
    }
}
