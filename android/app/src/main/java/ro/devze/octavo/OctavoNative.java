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
    static native long create();
    static native void destroy(long handle);
    static native void surfaceCreated(long handle, Surface surface);
    static native void surfaceChanged(long handle, int format, int width, int height);
    static native void surfaceDestroyed(long handle);
    static native boolean touch(long handle,
                                int action,
                                float x,
                                float y,
                                long eventTimeMillis);
}
