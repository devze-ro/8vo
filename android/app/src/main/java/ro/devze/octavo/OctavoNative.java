package ro.devze.octavo;

import android.view.Surface;

final class OctavoNative {
    static {
        System.loadLibrary("octavo");
    }

    private OctavoNative() {
    }

    static native String version();
    static native String platform();
    static native String groundVersion();
    static native String readerVersion();
    static native String uiVersion();
    static native String readerViewVersion();
    static native long create(String filesPath, String cachePath, String fixturePath);
    static native void destroy(long handle);
    static native void hostResumed(long handle);
    static native void hostPaused(long handle);
    static native void surfaceCreated(long handle, Surface surface);
    static native void surfaceChanged(long handle, int format, int width, int height);
    static native void surfaceDestroyed(long handle);
    static native void windowInsets(long handle,
                                    int left,
                                    int top,
                                    int right,
                                    int bottom);
    static native long[] state(long handle);
    static native String filesPath(long handle);
    static native String cachePath(long handle);
    static native String fixturePath(long handle);
    static native String visibleText(long handle);
    static native int clearColorArgb();
    static native boolean touch(long handle,
                                int action,
                                float x,
                                float y,
                                long eventTimeMillis);
}
