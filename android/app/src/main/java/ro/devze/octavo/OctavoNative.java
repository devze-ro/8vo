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

    private OctavoNative() {
    }

    static native String version();
    static native String platform();
    static native String groundVersion();
    static native String readerVersion();
    static native String uiVersion();
    static native String readerViewVersion();
    static native long create(String filesPath,
                              String cachePath,
                              String documentPath,
                              long resumeSpineIndex,
                              long resumeByteOffset,
                              boolean resumeRequested,
                              boolean chromeVisible,
                              int[] appearanceConfig,
                              int[] appearanceColors,
                              int[] typographyMetrics,
                              byte[] typographyAlpha,
                              long readerEntryStartedMillis);
    static native int applyAppearance(long handle,
                                      int[] appearanceConfig,
                                      int[] appearanceColors,
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
    static native boolean present(long handle);
    static native int navigationAvailability(long handle);
    static native boolean setChromeVisible(long handle, boolean visible);
    static native int movePage(long handle, int direction);
    static native int accessibilityMovePage(long handle, int direction);
    static native int warmLocationCacheStep(long handle);
    static native long[] locationCacheState(long handle);
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
