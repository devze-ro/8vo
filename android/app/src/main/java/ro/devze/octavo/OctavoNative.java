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
    static native long create(String filesPath, String cachePath);
    static native void destroy(long handle);
    static native void hostResumed(long handle);
    static native void hostPaused(long handle);
    static native void surfaceCreated(long handle, Surface surface);
    static native void surfaceChanged(long handle, int format, int width, int height);
    static native void surfaceDestroyed(long handle);
    static native long[] state(long handle);
    static native String filesPath(long handle);
    static native String cachePath(long handle);
    static native int clearColorArgb();
    static native boolean touch(long handle,
                                int action,
                                float x,
                                float y,
                                long eventTimeMillis);
}
