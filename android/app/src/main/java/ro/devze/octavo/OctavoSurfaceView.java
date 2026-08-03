package ro.devze.octavo;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;

import java.util.Arrays;

final class OctavoSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    interface Listener {
        void onChromeVisibilityChanged(boolean visible);
        void onAppearancePresented(OctavoAppearance appearance);
        void onAppearanceFailure();
        void onReaderSurfaceFailure();
        void onReaderLocationSummaryFailure();
        void onPresentationRetriesExhausted(
            boolean appearanceStillAwaiting);
        void onAppearanceRequestsSettled(OctavoAppearance appearance);
        void onReaderPresentationChanged(String progressLabel);
        void onNavigationStateChanged();
        void onStructuralNavigationPresented(long generation);
        void onProgressDisplayPresented(OctavoProgressDisplay display,
                                        long generation);
        void onNavigationRequestFailure(String message);
    }

    private static final long APPEARANCE_COALESCE_MILLIS = 90;
    private static final int MAX_PRESENTATION_RETRIES = 4;
    private static final long PRESENTATION_RETRY_MILLIS = 32;
    private static final long LOCATION_WARM_INITIAL_DELAY_MILLIS = 120;
    private static final long LOCATION_WARM_STEP_DELAY_MILLIS = 32;
    private static final int LOCATION_WARM_MORE = 1;
    private static final int LOCATION_WARM_DEFERRED = 2;
    private static final int LOCATION_WARM_COMPLETED_REFRESH = 3;
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
    static final int STATE_APPEARANCE_GENERATION = 59;
    static final int STATE_APPEARANCE_PRESENTED_GENERATION = 60;
    static final int STATE_APPEARANCE_APPLY_COUNT = 61;
    static final int STATE_APPEARANCE_GATE_BLOCK_COUNT = 62;
    static final int STATE_APPEARANCE_FAILURE_COUNT = 63;
    static final int STATE_REFLOW_REQUEST_COUNT = 64;
    static final int STATE_REFLOW_SUCCESS_COUNT = 65;
    static final int STATE_REFLOW_FAILURE_COUNT = 66;
    static final int STATE_ACCESSIBILITY_ACTION_COUNT = 67;
    static final int STATE_THEME = 68;
    static final int STATE_FONT_FAMILY = 69;
    static final int STATE_FONT_SIZE_SP = 70;
    static final int STATE_LINE_SPACING_PERMILLE = 71;
    static final int STATE_MARGIN = 72;
    static final int STATE_ALIGNMENT = 73;
    static final int STATE_PUBLISHER_COLORS = 74;
    static final int STATE_PALETTE_HASH = 75;
    static final int STATE_PAGE_FIRST_BYTE = 76;
    static final int STATE_PAGE_ONE_PAST_LAST_BYTE = 77;
    static final int STATE_CHROME_VISIBLE = 78;
    static final int STATE_CHROME_TOGGLE_COUNT = 79;
    static final int STATE_REFLOW_PRESENTATION_PENDING = 80;
    static final int STATE_CONTENT_X = 81;
    static final int STATE_CONTENT_Y = 82;
    static final int STATE_CONTENT_WIDTH = 83;
    static final int STATE_CONTENT_HEIGHT = 84;
    static final int STATE_READER_CHROME_INSET_TOP = 85;
    static final int STATE_READER_CHROME_INSET_BOTTOM = 86;
    static final int STATE_REDUCED_MOTION = 87;
    static final int STATE_HOST_PRESENTATION_PENDING = 88;
    static final int STATE_JUSTIFICATION_PLAN_COUNT = 89;
    static final int STATE_JUSTIFICATION_ACTIVE_ROW_COUNT = 90;
    static final int STATE_JUSTIFICATION_APPLIED_EXTRA_PX = 91;
    static final int STATE_JUSTIFICATION_SEMANTIC_HASH = 92;
    static final int STATE_READER_ENTRY_STARTED_MILLIS = 93;
    static final int STATE_FIRST_FRAME_ELAPSED_MILLIS = 94;
    static final int STATE_TYPOGRAPHY_MISSING_GLYPH_COUNT = 95;
    static final int STATE_FIELD_COUNT = 96;
    static final int NAVIGATION_STATE_VERSION = 0;
    static final int NAVIGATION_STATE_PENDING = 1;
    static final int NAVIGATION_STATE_SEMANTIC_GENERATION = 2;
    static final int NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION = 3;
    static final int NAVIGATION_STATE_HISTORY_BACK_COUNT = 4;
    static final int NAVIGATION_STATE_HISTORY_FORWARD_COUNT = 5;
    static final int NAVIGATION_STATE_PROGRESS_GENERATION = 6;
    static final int NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION = 7;
    static final int NAVIGATION_STATE_PROGRESS_REQUESTED_MODE = 8;
    static final int NAVIGATION_STATE_PROGRESS_PRESENTED_MODE = 9;
    static final int NAVIGATION_STATE_FIELD_COUNT = 10;

    private long nativeHandle;
    private boolean hostResumed;
    private boolean presentationPosted;
    private boolean persistencePosted;
    private boolean pendingPosition;
    private long pendingSpineIndex;
    private long pendingByteOffset;
    private final OctavoLibraryStore libraryStore;
    private final OctavoLibraryStore.Book book;
    private final Listener listener;
    private final OctavoReaderAccessibilityProvider accessibilityProvider;
    private final int swipeMinimumDistancePx;
    private OctavoAppearance presentedAppearance;
    private OctavoAppearance requestedAppearance;
    private OctavoAppearance nativeAppearanceAwaitingPresentation;
    private boolean appearanceApplyPosted;
    private boolean forceAppearanceRequest;
    private int presentationRetryCount;
    private boolean presentationFailureNotified;
    private boolean locationWarmPosted;
    private boolean locationWarmComplete;
    private boolean chromeCompositionTransitioning;
    private long chromeCompositionGeneration;
    private long touchCompositionGeneration;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float gestureDownX;
    private float gestureDownY;
    private boolean gestureCommitted;
    private boolean gestureCancelled;
    private boolean chromeVisible;
    private long lastNotifiedFrameCount;
    private long[] cachedNavigationState;
    private long lastNotifiedSemanticNavigationGeneration;
    private long lastNotifiedProgressDisplayGeneration;
    private OctavoProgressDisplay presentedProgressDisplay;
    private int systemInsetLeft;
    private int systemInsetTop;
    private int systemInsetRight;
    private int systemInsetBottom;
    private int readerChromeInsetTop;
    private int readerChromeInsetBottom;
    private int keyboardBackwardFocusId = View.NO_ID;
    private int keyboardForwardFocusId = View.NO_ID;
    private final Runnable presentPage = this::runNativePresentation;
    private final Runnable persistPosition = () -> {
        persistencePosted = false;
        flushPresentedPosition();
    };
    private final Runnable applyRequestedAppearance = () -> {
        appearanceApplyPosted = false;
        drainAppearanceRequest();
    };
    private final Runnable warmLocationCache = () -> {
        locationWarmPosted = false;
        if (nativeHandle == 0 || !hostResumed || locationWarmComplete) {
            return;
        }
        int result = OctavoNative.warmLocationCacheStep(nativeHandle);
        if (result == LOCATION_WARM_COMPLETED_REFRESH) {
            locationWarmComplete = true;
            requestNativePresentation();
        } else if (result == LOCATION_WARM_MORE) {
            scheduleLocationWarm(LOCATION_WARM_STEP_DELAY_MILLIS);
        } else if (result == LOCATION_WARM_DEFERRED) {
            /*
             * Presentation retries are bounded. Once their visible terminal
             * failure has fired, do not turn deferred metadata work into a
             * permanent 32ms UI-thread poll. A later successful presentation
             * resets the gate and schedules warming again.
             */
            scheduleLocationWarm(LOCATION_WARM_STEP_DELAY_MILLIS);
        } else {
            locationWarmComplete = true;
            if (result < 0) {
                Log.w("8vo", "Unable to warm deferred reader locations");
                notifyReaderLocationSummaryFailure();
            }
        }
    };
    private final Runnable finishChromeCompositionTask =
        this::finishChromeCompositionTransition;

    private void finishChromeCompositionTransition() {
        chromeCompositionTransitioning = false;
        accessibilityProvider.onChromeCompositionSettled();
    }

    private void notifyReaderLocationSummaryFailure() {
        if (listener != null) {
            listener.onReaderLocationSummaryFailure();
        }
    }

    private void initializeProgressDisplay() {
        int result = OctavoNative.setProgressDisplayMode(
            nativeHandle, presentedProgressDisplay.nativeId());
        long[] state = OctavoNative.navigationState(nativeHandle);
        OctavoProgressDisplay nativePresented =
            validNavigationState(state)
                ? OctavoProgressDisplay.fromNativeId(
                    (int)state[
                        NAVIGATION_STATE_PROGRESS_PRESENTED_MODE])
                : null;
        boolean initialized =
            result > 0
            && nativePresented == presentedProgressDisplay
            && state[NAVIGATION_STATE_PROGRESS_REQUESTED_MODE]
                == presentedProgressDisplay.nativeId()
            && state[NAVIGATION_STATE_PROGRESS_GENERATION]
                == state[
                    NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION];
        if (!initialized) {
            OctavoNative.destroy(nativeHandle);
            nativeHandle = 0;
            throw new IllegalStateException(
                "Unable to initialize the Port 8 progress display");
        }
        cachedNavigationState = Arrays.copyOf(
            state, NAVIGATION_STATE_FIELD_COUNT);
        lastNotifiedSemanticNavigationGeneration =
            state[
                NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION];
        lastNotifiedProgressDisplayGeneration =
            state[
                NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION];
        presentedProgressDisplay = nativePresented;
    }

    OctavoSurfaceView(Context context,
                      OctavoLibraryStore libraryStore,
                      OctavoLibraryStore.Session session,
                      OctavoAppearance appearance,
                      OctavoProgressDisplay progressDisplay,
                      boolean chromeVisible,
                      long readerEntryStartedMillis,
                      Listener listener) {
        super(context);
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        int minimumDp = Math.round(
            48.0f * context.getResources().getDisplayMetrics().density);
        swipeMinimumDistancePx = Math.max(touchSlop * 4, minimumDp);
        this.libraryStore = libraryStore;
        this.listener = listener;
        book = session.book;
        presentedAppearance = appearance == null
            ? OctavoAppearance.defaults() : appearance;
        presentedProgressDisplay = progressDisplay == null
            ? OctavoProgressDisplay.defaults() : progressDisplay;
        this.chromeVisible = chromeVisible;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(presentedAppearance);
        OctavoTypography typography =
            OctavoTypography.create(context, presentedAppearance);
        nativeHandle = OctavoNative.create(
            context.getFilesDir().getAbsolutePath(),
            context.getCacheDir().getAbsolutePath(),
            book.file.getAbsolutePath(),
            session.spineIndex,
            session.byteOffset,
            session.hasPosition,
            chromeVisible,
            presentedAppearance.nativeConfig(),
            tokens.nativeUi0Colors(),
            typography.metrics,
            typography.alpha,
            readerEntryStartedMillis);
        if (nativeHandle == 0) {
            throw new IllegalStateException(
                "Unable to create the 8vo native application state");
        }
        initializeProgressDisplay();

        setId(R.id.octavo_surface);
        // Surface pixels live in a separate compositor layer. An opaque View
        // background would cover the successfully posted native reader.
        setBackground(null);
        setContentDescription("8vo reader surface");
        setFocusable(true);
        setFocusableInTouchMode(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        accessibilityProvider =
            new OctavoReaderAccessibilityProvider(this);
        setOnApplyWindowInsetsListener((view, insets) -> {
            systemInsetLeft = insets.getSystemWindowInsetLeft();
            systemInsetTop = insets.getSystemWindowInsetTop();
            systemInsetRight = insets.getSystemWindowInsetRight();
            systemInsetBottom = insets.getSystemWindowInsetBottom();
            resetPresentationRetries();
            OctavoNative.windowInsets(
                nativeHandle,
                systemInsetLeft,
                systemInsetTop,
                systemInsetRight,
                systemInsetBottom);
            notifyNativePresentationIfChanged();
            requestNativePresentation();
            return insets;
        });
        getHolder().addCallback(this);
        requestApplyInsets();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (nativeHandle != 0) {
            resetPresentationRetries();
            if (!OctavoNative.surfaceCreated(
                    nativeHandle, holder.getSurface())) {
                if (listener != null) {
                    listener.onReaderSurfaceFailure();
                }
                return;
            }
            notifyNativePresentationIfChanged();
            requestNativePresentation();
            scheduleAppearanceDrain(0);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format,
                               int width,
                               int height) {
        clearGestureTracking();
        if (nativeHandle != 0) {
            resetPresentationRetries();
            OctavoNative.surfaceChanged(nativeHandle, format, width, height);
            notifyNativePresentationIfChanged();
            requestNativePresentation();
            scheduleAppearanceDrain(0);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        clearGestureTracking();
        if (nativeHandle != 0) {
            capturePresentedPosition();
            flushPresentedPosition();
            OctavoNative.surfaceDestroyed(nativeHandle);
            refreshNavigationState(true);
            removeCallbacks(presentPage);
            removeCallbacks(warmLocationCache);
            removeCallbacks(finishChromeCompositionTask);
            presentationPosted = false;
            locationWarmPosted = false;
            chromeCompositionTransitioning = false;
            resetPresentationRetries();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (nativeHandle == 0 || event == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (chromeCompositionTransitioning) {
            cancelNativeTouch(event);
            clearGestureTracking();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            activePointerId = event.getPointerId(0);
            gestureDownX = event.getX(0);
            gestureDownY = event.getY(0);
            gestureCommitted = false;
            gestureCancelled = false;
            touchCompositionGeneration = chromeCompositionGeneration;
            return dispatchNativeTouch(
                MotionEvent.ACTION_DOWN,
                gestureDownX,
                gestureDownY,
                event.getEventTime());
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            cancelNativeTouch(event);
            gestureCancelled = true;
            return true;
        }
        int pointerIndex = event.findPointerIndex(activePointerId);
        if (pointerIndex < 0) {
            cancelNativeTouch(event);
            clearGestureTracking();
            return true;
        }
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);
        if (touchCompositionGeneration != chromeCompositionGeneration) {
            cancelNativeTouch(event);
            clearGestureTracking();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (gestureCancelled || gestureCommitted) {
                return true;
            }
            float deltaX = x - gestureDownX;
            float deltaY = y - gestureDownY;
            float absoluteX = Math.abs(deltaX);
            float absoluteY = Math.abs(deltaY);
            if (absoluteX >= swipeMinimumDistancePx
                && absoluteX > absoluteY * 1.25f) {
                cancelNativeTouch(event);
                gestureCommitted = true;
                requestPageMove(deltaX < 0.0f ? 1 : -1);
                return true;
            }
            if (absoluteY >= swipeMinimumDistancePx
                && absoluteY > absoluteX) {
                cancelNativeTouch(event);
                gestureCancelled = true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (gestureCancelled || gestureCommitted) {
                clearGestureTracking();
                return true;
            }
            boolean handled = dispatchNativeTouch(
                MotionEvent.ACTION_UP, x, y, event.getEventTime());
            clearGestureTracking();
            return handled;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelNativeTouch(event);
            clearGestureTracking();
            return true;
        }
        return true;
    }

    private boolean dispatchNativeTouch(int action,
                                        float x,
                                        float y,
                                        long eventTimeMillis) {
        int result = OctavoNative.touch(
            nativeHandle, action, x, y, eventTimeMillis);
        if ((result & OctavoNative.TOUCH_PRESENT_REQUESTED) != 0) {
            refreshNavigationState(true);
            requestNativePresentation();
        }
        if ((result & OctavoNative.TOUCH_CHROME_REQUESTED) != 0) {
            updateChromeVisibility(!chromeVisible, true);
        }
        return (result & OctavoNative.TOUCH_HANDLED) != 0;
    }

    private void cancelNativeTouch(MotionEvent event) {
        if (nativeHandle == 0) {
            return;
        }
        float x = event == null || event.getPointerCount() == 0
            ? 0.0f : event.getX(0);
        float y = event == null || event.getPointerCount() == 0
            ? 0.0f : event.getY(0);
        long eventTime = event == null
            ? android.os.SystemClock.uptimeMillis() : event.getEventTime();
        OctavoNative.touch(
            nativeHandle, MotionEvent.ACTION_CANCEL, x, y, eventTime);
    }

    private void clearGestureTracking() {
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        gestureCommitted = false;
        gestureCancelled = false;
    }

    private boolean requestPageMove(int direction) {
        if (nativeHandle == 0) {
            return false;
        }
        int result = OctavoNative.movePage(nativeHandle, direction);
        if ((result & OctavoNative.TOUCH_PRESENT_REQUESTED) != 0) {
            refreshNavigationState(true);
            requestNativePresentation();
        }
        return (result & OctavoNative.TOUCH_HANDLED) != 0;
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return accessibilityProvider;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityProvider.dispatchHoverEvent(event)
            || super.dispatchHoverEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus,
                                  int direction,
                                  Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibilityProvider.onOwnerFocusChanged(gainFocus, direction);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_TAB
            && event.getRepeatCount() == 0
            && (event.hasNoModifiers()
                || event.hasModifiers(KeyEvent.META_SHIFT_ON))) {
            boolean backward = event.isShiftPressed();
            if (accessibilityProvider.moveKeyboardFocus(backward)
                || !chromeVisible
                || moveKeyboardFocusOutsideReader(backward)) {
                return true;
            }
        }
        if (event.getRepeatCount() == 0 && event.hasNoModifiers()) {
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return requestPageMove(-1);
            }
            if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return requestPageMove(1);
            }
        }
        if ((keyCode == KeyEvent.KEYCODE_ENTER
             || keyCode == KeyEvent.KEYCODE_SPACE
             || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            && event.getRepeatCount() == 0
            && event.hasNoModifiers()
            && accessibilityProvider.activateKeyboardFocus()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    boolean setChromeVisible(boolean visible) {
        return updateChromeVisibility(visible, true);
    }

    void setKeyboardBoundaryFocusIds(int backwardId, int forwardId) {
        keyboardBackwardFocusId = backwardId;
        keyboardForwardFocusId = forwardId;
    }

    private boolean moveKeyboardFocusOutsideReader(boolean backward) {
        int targetId =
            backward ? keyboardBackwardFocusId : keyboardForwardFocusId;
        View target = targetId == View.NO_ID
            ? null : getRootView().findViewById(targetId);
        return target != null
            && target.isShown()
            && target.isEnabled()
            && target.isFocusable()
            && target.requestFocus(
                backward ? View.FOCUS_BACKWARD : View.FOCUS_FORWARD);
    }

    private boolean updateChromeVisibility(boolean visible,
                                           boolean notifyAccessibility) {
        if (nativeHandle == 0) {
            return false;
        }
        if (chromeVisible == visible) {
            return true;
        }
        if (!OctavoNative.setChromeVisible(nativeHandle, visible)) {
            notifyNativePresentationIfChanged();
            requestPendingNativePresentation();
            return false;
        }
        chromeVisible = visible;
        if (listener != null) {
            listener.onChromeVisibilityChanged(visible);
        }
        if (notifyAccessibility) {
            accessibilityProvider.onChromeVisibilityChanged();
        }
        notifyNativePresentationIfChanged();
        requestPendingNativePresentation();
        return true;
    }

    boolean toggleChromeForAccessibility() {
        return updateChromeVisibility(!chromeVisible, false);
    }

    boolean chromeVisibleForAccessibility() {
        return chromeVisible;
    }

    void beginChromeCompositionTransition(int durationMillis) {
        removeCallbacks(finishChromeCompositionTask);
        clearGestureTracking();
        chromeCompositionGeneration += 1;
        if (nativeHandle != 0) {
            OctavoNative.touch(
                nativeHandle,
                MotionEvent.ACTION_CANCEL,
                0.0f,
                0.0f,
                android.os.SystemClock.uptimeMillis());
        }
        chromeCompositionTransitioning = durationMillis > 0;
        if (chromeCompositionTransitioning
            && postDelayed(finishChromeCompositionTask, durationMillis)) {
            return;
        }
        chromeCompositionTransitioning = false;
        accessibilityProvider.onChromeCompositionSettled();
    }

    boolean setReaderChromeInsets(int top, int bottom) {
        if (top < 0 || bottom < 0
            || nativeHandle == 0) {
            return false;
        }
        if (readerChromeInsetTop == top
            && readerChromeInsetBottom == bottom) {
            if (!presentationFailureNotified) {
                requestPendingNativePresentation();
            }
            return true;
        }
        resetPresentationRetries();
        if (!OctavoNative.readerChromeInsets(nativeHandle, top, bottom)) {
            notifyNativePresentationIfChanged();
            requestPendingNativePresentation();
            return false;
        }
        readerChromeInsetTop = top;
        readerChromeInsetBottom = bottom;
        notifyNativePresentationIfChanged();
        requestPendingNativePresentation();
        return true;
    }

    private void requestPendingNativePresentation() {
        refreshNavigationState(true);
        long[] state = OctavoNative.state(nativeHandle);
        if (validState(state)
            && state[STATE_RESUMED] != 0
            && state[STATE_HAS_SURFACE] != 0
            && presentationPending(state)) {
            requestNativePresentation();
        }
    }

    boolean movePageForAccessibility(int direction) {
        if (nativeHandle == 0) {
            return false;
        }
        int result =
            OctavoNative.accessibilityMovePage(nativeHandle, direction);
        if ((result & OctavoNative.TOUCH_PRESENT_REQUESTED) != 0) {
            refreshNavigationState(true);
            requestNativePresentation();
        }
        return (result & OctavoNative.TOUCH_HANDLED) != 0;
    }

    boolean canMovePrevious() {
        return (navigationAvailability()
                & OctavoNative.NAVIGATION_PREVIOUS) != 0;
    }

    boolean canMoveNext() {
        return (navigationAvailability()
                & OctavoNative.NAVIGATION_NEXT) != 0;
    }

    int navigationAvailability() {
        return nativeHandle == 0
            ? 0 : OctavoNative.navigationAvailability(nativeHandle);
    }

    OctavoNavigation navigationSnapshot() {
        if (nativeHandle == 0) {
            notifyNavigationRequestFailure(
                "Reader navigation is not available.");
            return null;
        }
        long[] packet = OctavoNative.contentsSnapshot(nativeHandle);
        if (packet == null || packet.length < OctavoNavigation.HEADER_COUNT
            || packet[0] != OctavoNavigation.VERSION
            || packet[1] != OctavoNavigation.HEADER_COUNT
            || packet[2] != OctavoNavigation.ROW_STRIDE
            || packet[4] < 0
            || packet[4] > OctavoNavigation.MAX_ROWS
            || packet.length != OctavoNavigation.HEADER_COUNT
                + packet[4] * OctavoNavigation.ROW_STRIDE) {
            notifyNavigationRequestFailure(
                "Reader navigation information is unavailable.");
            return null;
        }

        int rowCount = (int)packet[4];
        String[] labels = new String[rowCount];
        for (int row = 0; row < rowCount; ++row) {
            labels[row] = OctavoNative.contentsLabel(nativeHandle, row);
        }
        OctavoNavigation snapshot =
            OctavoNavigation.fromNativePacket(packet, labels);
        if (snapshot == null) {
            notifyNavigationRequestFailure(
                "Reader navigation information is invalid.");
        }
        return snapshot;
    }

    int requestContentsNavigation(int navIndex) {
        if (nativeHandle == 0 || navIndex < 0) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return finishNavigationRequest(
            OctavoNative.navigateToContents(nativeHandle, navIndex));
    }

    int requestChapterNavigation(long oneBasedChapter) {
        if (nativeHandle == 0 || oneBasedChapter <= 0
            || oneBasedChapter > Integer.MAX_VALUE) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return requestContentsNavigation((int)oneBasedChapter - 1);
    }

    int requestLocationNavigation(long oneBasedLocation) {
        if (nativeHandle == 0 || oneBasedLocation <= 0) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return finishNavigationRequest(
            OctavoNative.navigateToLocation(
                nativeHandle, oneBasedLocation));
    }

    int requestPageNavigation(long oneBasedPage) {
        if (nativeHandle == 0 || oneBasedPage <= 0) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return finishNavigationRequest(
            OctavoNative.navigateToPage(nativeHandle, oneBasedPage));
    }

    int requestPercentageNavigation(int percent) {
        if (nativeHandle == 0 || percent < 0 || percent > 100) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return finishNavigationRequest(
            OctavoNative.navigateToPercent(nativeHandle, percent));
    }

    int requestHistoryNavigation(boolean forward) {
        if (nativeHandle == 0) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return finishNavigationRequest(
            OctavoNative.moveHistory(nativeHandle, forward));
    }

    int requestProgressDisplay(OctavoProgressDisplay display) {
        if (nativeHandle == 0 || display == null) {
            return finishNavigationRequest(
                OctavoNative.NAVIGATION_INVALID);
        }
        return finishNavigationRequest(
            OctavoNative.setProgressDisplayMode(
                nativeHandle, display.nativeId()));
    }

    boolean hasNavigationPending() {
        return validNavigationState(cachedNavigationState)
            && cachedNavigationState[NAVIGATION_STATE_PENDING] != 0;
    }

    boolean canReturnInHistory() {
        return validNavigationState(cachedNavigationState)
            && cachedNavigationState[
                NAVIGATION_STATE_HISTORY_BACK_COUNT] > 0;
    }

    boolean canMoveForwardInHistory() {
        return validNavigationState(cachedNavigationState)
            && cachedNavigationState[
                NAVIGATION_STATE_HISTORY_FORWARD_COUNT] > 0;
    }

    OctavoProgressDisplay presentedProgressDisplay() {
        return presentedProgressDisplay;
    }

    private int finishNavigationRequest(int result) {
        refreshNavigationState(true);
        if (result == OctavoNative.NAVIGATION_ACCEPTED) {
            resetPresentationRetries();
            requestNativePresentation();
        } else if (result < 0) {
            notifyNavigationRequestFailure(
                navigationFailureMessage(result));
        }
        return result;
    }

    private void notifyNavigationRequestFailure(String message) {
        if (listener != null) {
            listener.onNavigationRequestFailure(message);
        }
    }

    private static String navigationFailureMessage(int result) {
        if (result == OctavoNative.NAVIGATION_INVALID) {
            return "That navigation destination is invalid.";
        }
        if (result == OctavoNative.NAVIGATION_UNAVAILABLE) {
            return "That navigation destination is unavailable in this book.";
        }
        if (result == OctavoNative.NAVIGATION_BUSY) {
            return "Reader navigation is waiting for the current page.";
        }
        return "Reader navigation could not complete that request.";
    }

    boolean hasPendingAppearanceRequest() {
        return requestedAppearance != null
            || nativeAppearanceAwaitingPresentation != null
            || appearanceApplyPosted;
    }

    boolean hasAppearanceAwaitingPresentation() {
        return nativeAppearanceAwaitingPresentation != null;
    }

    long[] accessibilitySemanticSnapshotForTesting() {
        return nativeHandle == 0
            ? null
            : OctavoNative.accessibilitySemanticSnapshot(nativeHandle);
    }

    String accessibilitySemanticNameForTesting(int recordIndex) {
        return nativeHandle == 0
            ? null
            : OctavoNative.accessibilitySemanticName(
                nativeHandle, recordIndex);
    }

    String accessibilitySemanticValueForTesting(int recordIndex) {
        return nativeHandle == 0
            ? null
            : OctavoNative.accessibilitySemanticValue(
                nativeHandle, recordIndex);
    }

    private void requestNativePresentation() {
        if (nativeHandle == 0 || presentationPosted) {
            return;
        }
        presentationPosted = true;
        if (!post(presentPage)) {
            presentationPosted = false;
            runNativePresentation();
        }
    }

    private void runNativePresentation() {
        presentationPosted = false;
        if (nativeHandle == 0) {
            return;
        }
        if (OctavoNative.present(nativeHandle)) {
            resetPresentationRetries();
            notifyNativePresentationIfChanged();
            return;
        }

        long[] state = OctavoNative.state(nativeHandle);
        boolean retryable = validState(state)
            && state[STATE_RESUMED] != 0
            && state[STATE_HAS_SURFACE] != 0;
        if (retryable
            && presentationRetryCount < MAX_PRESENTATION_RETRIES) {
            presentationRetryCount += 1;
            presentationPosted =
                postDelayed(presentPage, PRESENTATION_RETRY_MILLIS);
            if (presentationPosted) {
                return;
            }
        }
        if (retryable && !presentationFailureNotified) {
            presentationFailureNotified = true;
            notifyPresentationRetriesExhausted();
        }
    }

    private void resetPresentationRetries() {
        presentationRetryCount = 0;
        presentationFailureNotified = false;
    }

    private void notifyPresentationRetriesExhausted() {
        boolean appearanceStillAwaiting =
            nativeAppearanceAwaitingPresentation != null;
        long[] navigation = refreshNavigationState(true);
        boolean navigationStillAwaiting =
            validNavigationState(navigation)
            && navigation[NAVIGATION_STATE_PENDING] != 0
            && (navigation[
                    NAVIGATION_STATE_SEMANTIC_GENERATION]
                    != navigation[
                        NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                || navigation[
                    NAVIGATION_STATE_PROGRESS_GENERATION]
                    != navigation[
                        NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]);
        int navigationCancellation = 0;
        if (navigationStillAwaiting) {
            navigationCancellation = nativeHandle == 0
                ? -1
                : OctavoNative.cancelPendingNavigation(nativeHandle);
            refreshNavigationState(true);
            if (navigationCancellation > 0) {
                /*
                 * Keep the exhausted retry gate raised. This schedules one
                 * page-coloured recovery presentation; a second failure can
                 * neither restart the retry loop nor publish a second
                 * terminal callback.
                 */
                requestNativePresentation();
            }
        }
        if (!appearanceStillAwaiting) {
            removeCallbacks(applyRequestedAppearance);
            appearanceApplyPosted = false;
            requestedAppearance = null;
            forceAppearanceRequest = false;
        }
        boolean navigationOwnsFailure =
            navigationStillAwaiting && !appearanceStillAwaiting;
        if (listener != null && !navigationOwnsFailure) {
            listener.onPresentationRetriesExhausted(
                appearanceStillAwaiting);
        }
        if (navigationOwnsFailure) {
            notifyNavigationRequestFailure(
                navigationPresentationFailureMessage(
                    navigationCancellation));
        }
    }

    static String navigationPresentationFailureMessage(
        int cancellationResult) {
        return cancellationResult < 0
            ? "Navigation failed and the last presented page could not "
                + "be restored. Reopen the book."
            : "Navigation was not committed because the page could not "
                + "be presented.";
    }

    void requestAppearance(OctavoAppearance appearance) {
        if (nativeHandle == 0 || appearance == null) {
            return;
        }
        resetPresentationRetries();
        requestPendingNativePresentation();
        requestedAppearance = appearance;
        forceAppearanceRequest = false;
        removeCallbacks(applyRequestedAppearance);
        appearanceApplyPosted =
            postDelayed(applyRequestedAppearance,
                        APPEARANCE_COALESCE_MILLIS);
        if (!appearanceApplyPosted) {
            drainAppearanceRequest();
        }
    }

    private void drainAppearanceRequest() {
        if (presentationPosted) {
            scheduleAppearanceDrain(64);
            return;
        }
        if (nativeHandle == 0 || requestedAppearance == null
            || nativeAppearanceAwaitingPresentation != null) {
            return;
        }
        OctavoAppearance candidate = requestedAppearance;
        if (candidate.equals(presentedAppearance)
            && !forceAppearanceRequest) {
            requestedAppearance = null;
            if (listener != null) {
                listener.onAppearanceRequestsSettled(
                    presentedAppearance);
            }
            return;
        }

        long[] before = OctavoNative.state(nativeHandle);
        if (!validState(before)
            || before[STATE_RESUMED] != 1
            || before[STATE_HAS_SURFACE] != 1) {
            return;
        }
        if (presentationPending(before)) {
            requestNativePresentation();
            return;
        }
        long beforeGeneration = validState(before)
            ? before[STATE_APPEARANCE_GENERATION] : -1;
        int result;
        try {
            OctavoTypography typography =
                OctavoTypography.create(getContext(), candidate);
            OctavoDesignTokens tokens =
                OctavoDesignTokens.forAppearance(candidate);
            result = OctavoNative.applyAppearance(
                nativeHandle,
                candidate.nativeConfig(),
                tokens.nativeUi0Colors(),
                typography.metrics,
                typography.alpha);
        } catch (RuntimeException exception) {
            requestedAppearance = null;
            forceAppearanceRequest = false;
            notifyAppearanceFailure();
            return;
        }

        long[] after = OctavoNative.state(nativeHandle);
        if (result == 0) {
            if (candidate.equals(requestedAppearance)) {
                requestedAppearance = null;
                forceAppearanceRequest = false;
            }
            notifyNativePresentationIfChanged();
            notifyAppearanceFailure();
            long[] recovery = OctavoNative.state(nativeHandle);
            if (presentationPending(recovery)) {
                requestNativePresentation();
            }
            scheduleAppearanceDrain(0);
            return;
        }
        if (validState(after)
            && after[STATE_APPEARANCE_GENERATION] > beforeGeneration) {
            nativeAppearanceAwaitingPresentation = candidate;
            if (candidate.equals(requestedAppearance)) {
                requestedAppearance = null;
                forceAppearanceRequest = false;
            }
            notifyNativePresentationIfChanged();
            return;
        }
        requestNativePresentation();
    }

    void reapplyAppearance() {
        if (nativeHandle == 0) {
            return;
        }
        resetPresentationRetries();
        if (nativeAppearanceAwaitingPresentation != null) {
            return;
        }
        if (requestedAppearance != null) {
            removeCallbacks(applyRequestedAppearance);
            appearanceApplyPosted = post(applyRequestedAppearance);
            if (!appearanceApplyPosted) {
                drainAppearanceRequest();
            }
            return;
        }
        requestedAppearance = presentedAppearance;
        forceAppearanceRequest = true;
        removeCallbacks(applyRequestedAppearance);
        appearanceApplyPosted = post(applyRequestedAppearance);
        if (!appearanceApplyPosted) {
            drainAppearanceRequest();
        }
    }

    private void scheduleAppearanceDrain(long delayMillis) {
        if (nativeHandle == 0 || requestedAppearance == null
            || appearanceApplyPosted) {
            return;
        }
        appearanceApplyPosted =
            postDelayed(applyRequestedAppearance, delayMillis);
    }

    private void notifyNativePresentationIfChanged() {
        if (nativeHandle == 0) {
            return;
        }
        long[] state = OctavoNative.state(nativeHandle);
        if (!validState(state)) {
            return;
        }
        refreshNavigationState(true);
        reconcilePresentedAppearance(state);
        long frameCount = state[STATE_FRAME_COUNT];
        if (frameCount <= 0 || frameCount == lastNotifiedFrameCount) {
            return;
        }
        lastNotifiedFrameCount = frameCount;
        resetPresentationRetries();
        capturePresentedPosition();
        accessibilityProvider.onReaderPresentationChanged();
        if (listener != null) {
            listener.onReaderPresentationChanged(
                OctavoNative.progressLabel(nativeHandle));
        }
        scheduleLocationWarm(LOCATION_WARM_INITIAL_DELAY_MILLIS);
        if (nativeAppearanceAwaitingPresentation == null) {
            scheduleAppearanceDrain(0);
        }
    }

    private void reconcilePresentedAppearance(long[] state) {
        OctavoAppearance candidate =
            nativeAppearanceAwaitingPresentation;
        if (candidate == null
            || state[STATE_APPEARANCE_GENERATION]
               != state[STATE_APPEARANCE_PRESENTED_GENERATION]
            || !appearanceMatchesState(candidate, state)) {
            return;
        }
        presentedAppearance = candidate;
        nativeAppearanceAwaitingPresentation = null;
        if (listener != null) {
            listener.onAppearancePresented(candidate);
        }
    }

    private static boolean appearanceMatchesState(
        OctavoAppearance appearance,
        long[] state) {
        return validState(state)
            && state[STATE_THEME] == appearance.themeId()
            && state[STATE_FONT_FAMILY] == appearance.fontFamilyId()
            && state[STATE_FONT_SIZE_SP] == appearance.fontSizeSp()
            && state[STATE_LINE_SPACING_PERMILLE]
               == appearance.lineSpacingPermille()
            && state[STATE_MARGIN] == appearance.marginsId()
            && state[STATE_ALIGNMENT] == appearance.alignmentId()
            && state[STATE_PUBLISHER_COLORS]
               == appearance.publisherColorsId()
            && state[STATE_REDUCED_MOTION]
               == (appearance.reducedMotion() ? 1 : 0);
    }

    private static boolean validState(long[] state) {
        return state != null && state.length == STATE_FIELD_COUNT;
    }

    private long[] refreshNavigationState(boolean notifyChange) {
        if (nativeHandle == 0) {
            return cachedNavigationState;
        }
        long[] updated = OctavoNative.navigationState(nativeHandle);
        if (!validNavigationState(updated)) {
            Log.e("8vo", "Reader returned an invalid Port 8 navigation state");
            return cachedNavigationState;
        }
        boolean changed = !Arrays.equals(cachedNavigationState, updated);
        cachedNavigationState = Arrays.copyOf(
            updated, NAVIGATION_STATE_FIELD_COUNT);
        if (changed && notifyChange && listener != null) {
            listener.onNavigationStateChanged();
        }
        reconcilePresentedNavigation(cachedNavigationState);
        return cachedNavigationState;
    }

    private void reconcilePresentedNavigation(long[] state) {
        long semanticGeneration =
            state[NAVIGATION_STATE_SEMANTIC_GENERATION];
        long semanticPresented =
            state[
                NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION];
        if (semanticGeneration == semanticPresented
            && semanticPresented
                > lastNotifiedSemanticNavigationGeneration) {
            lastNotifiedSemanticNavigationGeneration =
                semanticPresented;
            if (listener != null) {
                listener.onStructuralNavigationPresented(
                    semanticPresented);
            }
        }

        long progressGeneration =
            state[NAVIGATION_STATE_PROGRESS_GENERATION];
        long progressPresented =
            state[
                NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION];
        if (progressGeneration == progressPresented
            && progressPresented
                > lastNotifiedProgressDisplayGeneration) {
            OctavoProgressDisplay display =
                OctavoProgressDisplay.fromNativeId(
                    (int)state[
                        NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]);
            lastNotifiedProgressDisplayGeneration =
                progressPresented;
            presentedProgressDisplay = display;
            if (listener != null) {
                listener.onProgressDisplayPresented(
                    display, progressPresented);
            }
        }
    }

    private static boolean validNavigationState(long[] state) {
        if (state == null
            || state.length != NAVIGATION_STATE_FIELD_COUNT
            || state[NAVIGATION_STATE_VERSION] != 1
            || state[NAVIGATION_STATE_PENDING] < 0
            || state[NAVIGATION_STATE_PENDING] > 1
            || state[NAVIGATION_STATE_SEMANTIC_GENERATION] < 0
            || state[
                NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION] < 0
            || state[
                NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                > state[NAVIGATION_STATE_SEMANTIC_GENERATION]
            || state[NAVIGATION_STATE_HISTORY_BACK_COUNT] < 0
            || state[NAVIGATION_STATE_HISTORY_FORWARD_COUNT] < 0
            || state[NAVIGATION_STATE_PROGRESS_GENERATION] < 0
            || state[
                NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION] < 0
            || state[
                NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                > state[NAVIGATION_STATE_PROGRESS_GENERATION]) {
            return false;
        }
        long requestedMode =
            state[NAVIGATION_STATE_PROGRESS_REQUESTED_MODE];
        long presentedMode =
            state[NAVIGATION_STATE_PROGRESS_PRESENTED_MODE];
        return requestedMode >= 0 && requestedMode <= 3
            && presentedMode >= 0 && presentedMode <= 3;
    }

    private boolean presentationPending(long[] state) {
        return (validState(state)
                && (state[STATE_PAGE_MOVE_PRESENTATION_PENDING] != 0
                    || state[STATE_REFLOW_PRESENTATION_PENDING] != 0
                    || state[STATE_HOST_PRESENTATION_PENDING] != 0
                    || state[STATE_APPEARANCE_GENERATION]
                       != state[
                           STATE_APPEARANCE_PRESENTED_GENERATION]))
            || hasNavigationPending();
    }

    private void notifyAppearanceFailure() {
        if (listener != null) {
            listener.onAppearanceFailure();
        }
    }

    private void synchronizeChromeFromNative() {
        long[] state = nativeHandle == 0
            ? null : OctavoNative.state(nativeHandle);
        if (!validState(state)) {
            return;
        }
        chromeVisible = state[STATE_CHROME_VISIBLE] != 0;
        if (listener != null) {
            listener.onChromeVisibilityChanged(chromeVisible);
        }
        accessibilityProvider.onChromeVisibilityChanged();
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
        if (libraryStore.savePresented(book,
                                        pendingSpineIndex,
                                        pendingByteOffset)) {
            pendingPosition = false;
        } else {
            Log.e("8vo", "Unable to persist the presented Port 7 location");
        }
    }

    private void scheduleLocationWarm(long delayMillis) {
        if (nativeHandle == 0 || !hostResumed || locationWarmComplete
            || locationWarmPosted || lastNotifiedFrameCount <= 0
            || presentationFailureNotified) {
            return;
        }
        locationWarmPosted = postDelayed(
            warmLocationCache, Math.max(delayMillis, 0));
    }

    void hostResumed() {
        if (nativeHandle != 0 && !hostResumed) {
            resetPresentationRetries();
            OctavoNative.hostResumed(nativeHandle);
            hostResumed = true;
            notifyNativePresentationIfChanged();
            requestNativePresentation();
            scheduleAppearanceDrain(0);
            scheduleLocationWarm(LOCATION_WARM_INITIAL_DELAY_MILLIS);
        }
    }

    void hostPaused() {
        clearGestureTracking();
        if (nativeHandle != 0 && hostResumed) {
            capturePresentedPosition();
            flushPresentedPosition();
            removeCallbacks(presentPage);
            removeCallbacks(warmLocationCache);
            removeCallbacks(finishChromeCompositionTask);
            presentationPosted = false;
            locationWarmPosted = false;
            chromeCompositionTransitioning = false;
            OctavoNative.hostPaused(nativeHandle);
            refreshNavigationState(true);
            hostResumed = false;
        }
    }

    long[] nativeStateForTesting() {
        return nativeHandle == 0 ? null : OctavoNative.state(nativeHandle);
    }

    long[] navigationStateForTesting() {
        return cachedNavigationState == null
            ? null
            : Arrays.copyOf(
                cachedNavigationState,
                NAVIGATION_STATE_FIELD_COUNT);
    }

    OctavoNavigation navigationSnapshotForTesting() {
        return navigationSnapshot();
    }

    long[] locationCacheStateForTesting() {
        return nativeHandle == 0
            ? null : OctavoNative.locationCacheState(nativeHandle);
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
        return book.key;
    }

    boolean importedDocumentForTesting() {
        return book.imported;
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

    OctavoAppearance presentedAppearanceForTesting() {
        return presentedAppearance;
    }

    boolean chromeVisibleForTesting() {
        return chromeVisible;
    }

    void requestAppearanceForTesting(OctavoAppearance appearance) {
        requestAppearance(appearance);
    }

    boolean forcePresentFailuresForTesting(int count) {
        return nativeHandle != 0
            && OctavoNative.forcePresentFailuresForTesting(
                nativeHandle, count);
    }

    boolean forcePrePresentFailuresForTesting(int count) {
        return nativeHandle != 0
            && OctavoNative.forcePrePresentFailuresForTesting(
                nativeHandle, count);
    }

    boolean forceLocationWarmFailuresForTesting(int count) {
        return nativeHandle != 0
            && OctavoNative.forceLocationWarmFailuresForTesting(
                nativeHandle, count);
    }

    boolean locationWarmPostedForTesting() {
        return locationWarmPosted;
    }

    boolean presentationFailureNotifiedForTesting() {
        return presentationFailureNotified;
    }

    boolean forceSurfaceAcquisitionFailuresForTesting(int count) {
        return nativeHandle != 0
            && OctavoNative.forceSurfaceAcquisitionFailuresForTesting(
                nativeHandle, count);
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
            removeCallbacks(applyRequestedAppearance);
            removeCallbacks(warmLocationCache);
            removeCallbacks(finishChromeCompositionTask);
            presentationPosted = false;
            persistencePosted = false;
            appearanceApplyPosted = false;
            locationWarmPosted = false;
            locationWarmComplete = true;
            chromeCompositionTransitioning = false;
            resetPresentationRetries();
            requestedAppearance = null;
            nativeAppearanceAwaitingPresentation = null;
            accessibilityProvider.clearAccessibilityState();
            getHolder().removeCallback(this);
            setOnApplyWindowInsetsListener(null);
            OctavoNative.destroy(nativeHandle);
            nativeHandle = 0;
            cachedNavigationState = null;
        }
    }
}
