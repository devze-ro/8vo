package ro.devze.octavo;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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
    static final int STATE_SPINE_INDEX = 35;
    static final int STATE_SECTION_COUNT = 36;
    static final int STATE_PROGRESS_LOCATION_INDEX = 37;
    static final int STATE_PROGRESS_LOCATION_COUNT = 38;
    static final int STATE_TYPOGRAPHY_READY = 39;
    static final int STATE_TYPOGRAPHY_TEXT_PX = 40;
    static final int STATE_TYPOGRAPHY_LINE_ADVANCE_PX = 41;
    static final int STATE_TYPOGRAPHY_NARROW_ADVANCE_PX = 42;
    static final int STATE_TYPOGRAPHY_WIDE_ADVANCE_PX = 43;
    static final int STATE_TYPOGRAPHY_STYLE_COUNT = 44;
    static final int STATE_TYPOGRAPHY_RASTERIZED_GLYPH_COUNT = 45;
    static final int STATE_TYPOGRAPHY_REGULAR_GLYPH_COUNT = 46;
    static final int STATE_TYPOGRAPHY_BOLD_GLYPH_COUNT = 47;
    static final int STATE_TYPOGRAPHY_ITALIC_GLYPH_COUNT = 48;
    static final int STATE_TYPOGRAPHY_BOLD_ITALIC_GLYPH_COUNT = 49;
    static final int STATE_RESTORE_REQUESTED = 50;
    static final int STATE_RESTORE_ATTEMPTED = 51;
    static final int STATE_RESTORE_SUCCEEDED = 52;
    static final int STATE_RESTORE_FAILURE_COUNT = 53;
    static final int STATE_PRESENTED_SPINE_INDEX = 54;
    static final int STATE_PRESENTED_BYTE_OFFSET = 55;
    static final int STATE_DOCUMENT_OPEN_SUCCESS_COUNT = 56;
    static final int STATE_DOCUMENT_OPEN_FAILURE_COUNT = 57;
    static final int STATE_DOCUMENT_GENERATION = 58;
    static final int STATE_FIELD_COUNT = 59;

    private long nativeHandle;
    private boolean hostResumed;
    private boolean presentationPosted;
    private boolean persistencePosted;
    private boolean pendingPosition;
    private long pendingSpineIndex;
    private long pendingByteOffset;
    private final OctavoDocumentStore documentStore;
    private final OctavoDocumentStore.Document document;
    private final Runnable presentPage = () -> {
        presentationPosted = false;
        if (nativeHandle != 0 && OctavoNative.present(nativeHandle)) {
            capturePresentedPosition();
        }
    };
    private final Runnable persistPosition = () -> {
        persistencePosted = false;
        flushPresentedPosition();
    };

    OctavoSurfaceView(Context context,
                      OctavoDocumentStore documentStore,
                      OctavoDocumentStore.Session session) {
        super(context);
        this.documentStore = documentStore;
        document = session.document;
        OctavoTypography typography = OctavoTypography.create(context);
        nativeHandle = OctavoNative.create(
            context.getFilesDir().getAbsolutePath(),
            context.getCacheDir().getAbsolutePath(),
            document.file.getAbsolutePath(),
            session.spineIndex,
            session.byteOffset,
            session.hasPosition,
            typography.metrics,
            typography.alpha);
        if (nativeHandle == 0) {
            throw new IllegalStateException(
                "Unable to create the 8vo native application state");
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
                capturePresentedPosition();
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
            capturePresentedPosition();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format,
                               int width,
                               int height) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceChanged(nativeHandle, format, width, height);
            capturePresentedPosition();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (nativeHandle != 0) {
            capturePresentedPosition();
            flushPresentedPosition();
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
            if (OctavoNative.present(nativeHandle)) {
                capturePresentedPosition();
            }
        }
    }

    private void capturePresentedPosition() {
        if (nativeHandle == 0) {
            return;
        }
        long[] position = OctavoNative.readingPosition(nativeHandle);
        if (position == null
            || position.length != 3
            || position[0] != 1) {
            return;
        }
        pendingPosition = true;
        pendingSpineIndex = position[1];
        pendingByteOffset = position[2];
        removeCallbacks(persistPosition);
        persistencePosted = postDelayed(persistPosition, 350);
        if (!persistencePosted) {
            flushPresentedPosition();
        }
    }

    private void flushPresentedPosition() {
        removeCallbacks(persistPosition);
        persistencePosted = false;
        if (!pendingPosition) {
            return;
        }
        if (documentStore.savePresented(document,
                                        pendingSpineIndex,
                                        pendingByteOffset)) {
            pendingPosition = false;
        } else {
            Log.e("8vo", "Unable to persist the presented Port 5 location");
        }
    }

    void hostResumed() {
        if (nativeHandle != 0 && !hostResumed) {
            OctavoNative.hostResumed(nativeHandle);
            hostResumed = true;
            capturePresentedPosition();
        }
    }

    void hostPaused() {
        if (nativeHandle != 0 && hostResumed) {
            capturePresentedPosition();
            flushPresentedPosition();
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

    String documentPathForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.documentPath(nativeHandle);
    }

    String documentTitleForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.documentTitle(nativeHandle);
    }

    String documentKeyForTesting() {
        return document.key;
    }

    boolean importedDocumentForTesting() {
        return document.imported;
    }

    long[] readingPositionForTesting() {
        return nativeHandle == 0
            ? null
            : OctavoNative.readingPosition(nativeHandle);
    }

    String visibleTextForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.visibleText(nativeHandle);
    }

    String progressLabelForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.progressLabel(nativeHandle);
    }

    void flushPersistenceForTesting() {
        capturePresentedPosition();
        flushPresentedPosition();
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
            flushPresentedPosition();
            removeCallbacks(presentPage);
            removeCallbacks(persistPosition);
            presentationPosted = false;
            persistencePosted = false;
            getHolder().removeCallback(this);
            setOnApplyWindowInsetsListener(null);
            OctavoNative.destroy(nativeHandle);
            nativeHandle = 0;
        }
    }
}
