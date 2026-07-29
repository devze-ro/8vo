package ro.devze.octavo;

import android.content.Context;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowInsets;

final class OctavoSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    static final int STATE_RESUMED = 0;
    static final int STATE_HAS_SURFACE = 1;
    static final int STATE_WIDTH = 2;
    static final int STATE_HEIGHT = 3;
    static final int STATE_SURFACE_GENERATION = 4;
    static final int STATE_SURFACE_DESTROY_COUNT = 5;
    static final int STATE_RESUME_COUNT = 6;
    static final int STATE_PAUSE_COUNT = 7;
    static final int STATE_FRAME_COUNT = 8;
    static final int STATE_RENDER_FAILURE_COUNT = 9;
    static final int STATE_TOUCH_COUNT = 10;
    static final int STATE_LIFECYCLE_GENERATION = 11;
    static final int STATE_READER_INITIALIZED = 12;
    static final int STATE_DOCUMENT_OPEN = 13;
    static final int STATE_READER_FRAME_READY = 14;
    static final int STATE_VISIBLE_TEXT_SIZE = 15;
    static final int STATE_VISIBLE_TEXT_HASH = 16;
    static final int STATE_PAGE_INDEX = 17;
    static final int STATE_PAGE_COUNT = 18;
    static final int STATE_READER_VIEW_READY = 19;
    static final int STATE_READER_VIEW_ERRORS = 20;
    static final int STATE_READER_VIEW_DRAW_COUNT = 21;
    static final int STATE_PAGE_SURFACE_X = 22;
    static final int STATE_PAGE_SURFACE_Y = 23;
    static final int STATE_PAGE_SURFACE_WIDTH = 24;
    static final int STATE_PAGE_SURFACE_HEIGHT = 25;
    static final int STATE_PROGRESS_PAGE_INDEX = 26;
    static final int STATE_PROGRESS_PAGE_COUNT = 27;
    static final int STATE_TAP_INTENT_COUNT = 28;
    static final int STATE_PAGE_MOVE_SUCCESS_COUNT = 29;
    static final int STATE_PAGE_MOVE_PRESENTED_COUNT = 30;
    static final int STATE_PAGE_MOVE_BOUNDARY_COUNT = 31;
    static final int STATE_PAGE_MOVE_GATE_BLOCK_COUNT = 32;
    static final int STATE_PAGE_MOVE_PRESENTATION_PENDING = 33;
    static final int STATE_NAVIGATION_FAILURE_COUNT = 34;
    static final int STATE_FIELD_COUNT = 35;

    private long nativeHandle;
    private boolean hostResumed;
    private boolean presentationPosted;
    private final Runnable presentPage = () -> {
        presentationPosted = false;
        if (nativeHandle != 0) {
            OctavoNative.present(nativeHandle);
        }
    };

    OctavoSurfaceView(Context context, String fixturePath) {
        super(context);
        nativeHandle = OctavoNative.create(context.getFilesDir().getAbsolutePath(),
                                           context.getCacheDir().getAbsolutePath(),
                                           fixturePath);
        if (nativeHandle == 0) {
            throw new IllegalStateException("Unable to create the 8vo native application state");
        }

        setId(R.id.octavo_surface);
        setContentDescription("8vo reader surface");
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnApplyWindowInsetsListener((view, insets) -> {
            if (nativeHandle != 0) {
                OctavoNative.windowInsets(nativeHandle,
                                          insets.getSystemWindowInsetLeft(),
                                          insets.getSystemWindowInsetTop(),
                                          insets.getSystemWindowInsetRight(),
                                          insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        getHolder().addCallback(this);
        requestApplyInsets();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceCreated(nativeHandle, holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceChanged(nativeHandle, format, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceDestroyed(nativeHandle);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (nativeHandle == 0) {
            return false;
        }
        int result = OctavoNative.touch(nativeHandle,
                                        event.getActionMasked(),
                                        event.getX(),
                                        event.getY(),
                                        event.getEventTime());
        if ((result & OctavoNative.TOUCH_PRESENT_REQUESTED) != 0) {
            requestNativePresentation();
        }
        return (result & OctavoNative.TOUCH_HANDLED) != 0;
    }

    private void requestNativePresentation() {
        if (nativeHandle == 0 || presentationPosted) {
            return;
        }
        presentationPosted = true;
        if (!post(presentPage)) {
            presentationPosted = false;
            OctavoNative.present(nativeHandle);
        }
    }

    void hostResumed() {
        if (nativeHandle != 0 && !hostResumed) {
            OctavoNative.hostResumed(nativeHandle);
            hostResumed = true;
        }
    }

    void hostPaused() {
        if (nativeHandle != 0 && hostResumed) {
            OctavoNative.hostPaused(nativeHandle);
            hostResumed = false;
        }
    }

    long[] nativeStateForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.state(nativeHandle);
    }

    String filesPathForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.filesPath(nativeHandle);
    }

    String cachePathForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.cachePath(nativeHandle);
    }

    String fixturePathForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.fixturePath(nativeHandle);
    }

    String visibleTextForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.visibleText(nativeHandle);
    }

    void replaceNativeSurfaceForTesting() {
        if (nativeHandle == 0) {
            return;
        }
        SurfaceHolder holder = getHolder();
        surfaceDestroyed(holder);
        surfaceCreated(holder);
        surfaceChanged(holder, 0, getWidth(), getHeight());
    }

    void release() {
        if (nativeHandle != 0) {
            hostPaused();
            removeCallbacks(presentPage);
            presentationPosted = false;
            getHolder().removeCallback(this);
            setOnApplyWindowInsetsListener(null);
            OctavoNative.destroy(nativeHandle);
            nativeHandle = 0;
        }
    }
}
