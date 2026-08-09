package ro.devze.octavo;

import android.view.Surface;

final class OctavoNative {
    static {
        System.loadLibrary("octavo");
    }

    static final int TOUCH_HANDLED = 1;
    static final int TOUCH_PRESENT_REQUESTED = 2;
    static final int TOUCH_CHROME_REQUESTED = 4;
    static final int NAVIGATION_READY = 1;
    static final int NAVIGATION_PREVIOUS = 2;
    static final int NAVIGATION_NEXT = 4;
    static final int NAVIGATION_ACCEPTED = 1;
    static final int NAVIGATION_ALREADY_PRESENTED = 2;
    static final int NAVIGATION_INVALID = -1;
    static final int NAVIGATION_UNAVAILABLE = -2;
    static final int NAVIGATION_BUSY = -3;
    static final int NAVIGATION_FAILED = -4;
    static final int POSITION_QUALIFICATION_FIELD_COUNT = 7;
    static final int POSITION_QUALIFICATION_STATUS = 0;
    static final int POSITION_QUALIFICATION_SPINE_INDEX = 1;
    static final int POSITION_QUALIFICATION_BYTE_OFFSET = 2;
    static final int POSITION_QUALIFICATION_LOCATION_AVAILABLE = 3;
    static final int POSITION_QUALIFICATION_LOCATION_INDEX = 4;
    static final int POSITION_QUALIFICATION_LOCATION_COUNT = 5;
    static final int POSITION_QUALIFICATION_PERCENT = 6;
    static final int SELECTION_ACCEPTED = 1;
    static final int SELECTION_ALREADY_PRESENTED = 2;
    static final int SELECTION_INVALID = -1;
    static final int SELECTION_UNAVAILABLE = -2;
    static final int SELECTION_BUSY = -3;
    static final int SELECTION_FAILED = -4;
    static final int SELECTION_BOUNDARY = -5;
    static final int SELECTION_LIMIT = -6;
    static final int SELECTION_HANDLE_START = 1;
    static final int SELECTION_HANDLE_END = 2;
    static final int SELECTION_HANDLE_SHIFT = 8;
    static final int HIGHLIGHT_ACCEPTED = 1;
    static final int HIGHLIGHT_ALREADY_PRESENTED = 2;
    static final int HIGHLIGHT_INVALID = -1;
    static final int HIGHLIGHT_BUSY = -3;
    static final int HIGHLIGHT_FAILED = -4;
    static final int NOTE_MARKER_ACCEPTED = 1;
    static final int NOTE_MARKER_ALREADY_PRESENTED = 2;
    static final int NOTE_MARKER_INVALID = -1;
    static final int NOTE_MARKER_BUSY = -3;
    static final int NOTE_MARKER_FAILED = -4;

    private OctavoNative() {
    }

    static native String version();
    static native String platform();
    static native String groundVersion();
    static native String readerVersion();
    static native String uiVersion();
    static native int[] ui0AndroidThemeSnapshot(boolean darkAppearance,
                                                int[] colors);
    static native String readerViewVersion();
    static native long create(String filesPath,
                              String cachePath,
                              String documentPath,
                              long resumeSpineIndex,
                              long resumeByteOffset,
                              boolean resumeRequested,
                              boolean strictResume,
                              boolean chromeVisible,
                              int[] appearanceConfig,
                              int[] appearanceColors,
                              int[] highlightColors,
                              int[] typographyMetrics,
                              byte[] typographyAlpha,
                              long readerEntryStartedMillis);
    static native int applyAppearance(long handle,
                                      int[] appearanceConfig,
                                      int[] appearanceColors,
                                      int[] highlightColors,
                                      int[] typographyMetrics,
                                      byte[] typographyAlpha);
    static native void destroy(long handle);
    static native void hostResumed(long handle);
    static native void hostPaused(long handle);
    static native boolean surfaceCreated(long handle, Surface surface);
    static native void surfaceChanged(long handle,
                                      int format,
                                      int width,
                                      int height);
    static native void surfaceDestroyed(long handle);
    static native void windowInsets(long handle,
                                    int left,
                                    int top,
                                    int right,
                                    int bottom);
    static native boolean readerChromeInsets(long handle,
                                             int top,
                                             int bottom);
    static native boolean forcePresentFailuresForTesting(long handle,
                                                         int count);
    static native boolean forcePrePresentFailuresForTesting(long handle,
                                                            int count);
    static native boolean forceLocationWarmFailuresForTesting(long handle,
                                                              int count);
    static native boolean forceSurfaceAcquisitionFailuresForTesting(
        long handle, int count);
    static native String utf8RoundTripForTesting();
    static native long[] frameImagesSnapshot(long handle);
    static native byte[] frameImageEncodedBytes(long handle,
                                                int imageIndex,
                                                long byteLimit);
    static native boolean setFrameImageDecodeResult(long handle,
                                                    int imageIndex,
                                                    int status,
                                                    int width,
                                                    int height,
                                                    int[] argbPixels);
    static native boolean clearFrameImageCacheForTesting(long handle);
    static native boolean frameImageResourceCachedForTesting(
        long handle,
        long resourceIndex);
    static native boolean frameImageCurrentFramePinningForTesting(
        long handle,
        long resourceIndex);
    static native boolean present(long handle);
    static native int navigationAvailability(long handle);
    static native boolean setChromeVisible(long handle, boolean visible);
    static native int movePage(long handle, int direction);
    static native int accessibilityMovePage(long handle, int direction);
    static native int warmLocationCacheStep(long handle);
    static native long[] locationCacheState(long handle);
    static native long[] preparedStaticFrameStateForTesting(long handle);
    static native long[] navigationState(long handle);
    static native long[] contentsSnapshot(long handle);
    static native String contentsLabel(long handle, int rowIndex);
    static native int navigateToContents(long handle, int navIndex);
    static native int navigateToChapter(long handle, long oneBasedChapter);
    static native int navigateToLocation(long handle, long oneBasedLocation);
    static native int navigateToAnnotation(long handle,
                                           long spineIndex,
                                           long byteOffset);
    static native long[] qualifySyncedPosition(long handle,
                                               long spineIndex,
                                               long byteOffset);
    static native int navigateToSyncedPosition(long handle,
                                               long spineIndex,
                                               long byteOffset);
    static native int navigateToPage(long handle, long oneBasedPage);
    static native int navigateToPercent(long handle, int percent);
    static native int moveHistory(long handle, boolean forward);
    static native int setProgressDisplayMode(long handle, int mode);
    static native int cancelPendingNavigation(long handle);
    static native int commitSearch(long handle, byte[] queryUtf8);
    static native int clearSearch(long handle);
    static native long[] searchSnapshot(long handle);
    static native String searchQuery(long handle);
    static native boolean searchMutationPending(long handle);
    static native String searchSection(long handle, int rowIndex);
    static native String searchSnippet(long handle, int rowIndex);
    static native int navigateToSearchResult(long handle, int resultIndex);
    static native int moveSearchResult(long handle, int direction);
    static native int beginSelection(long handle, float x, float y);
    static native int beginSelectionForAccessibility(long handle);
    static native int updateSelection(long handle,
                                      int handleKind,
                                      float x,
                                      float y);
    static native int extendSelectionAcrossPage(long handle,
                                                int handleKind,
                                                int direction,
                                                float x);
    static native int clearSelection(long handle);
    static native long[] selectionSnapshot(long handle);
    static native byte[] selectedTextUtf8(long handle);
    static native long[] selectedRange(long handle);
    static native int replaceHighlights(long handle, long[] packet);
    static native long[] highlightSnapshot(long handle);
    static native int replaceNoteMarkers(long handle, long[] packet);
    static native long[] noteMarkerSnapshot(long handle);
    static native int noteMarkerHitTest(long handle,
                                        float x,
                                        float y,
                                        float minimumTouchRadiusPx);
    static native long[] state(long handle);
    static native long[] accessibilitySemanticSnapshot(long handle);
    static native String accessibilitySemanticName(long handle,
                                                   int recordIndex);
    static native String accessibilitySemanticValue(long handle,
                                                    int recordIndex);
    static native long[] readingPosition(long handle);
    static native String filesPath(long handle);
    static native String cachePath(long handle);
    static native String documentPath(long handle);
    static native String documentTitle(long handle);
    static native String visibleText(long handle);
    static native String progressLabel(long handle);
    static native int clearColorArgb();
    static native int touch(long handle,
                            int action,
                            float x,
                            float y,
                            long eventTimeMillis);
}
