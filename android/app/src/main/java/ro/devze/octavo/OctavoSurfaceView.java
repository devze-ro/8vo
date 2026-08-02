package ro.devze.octavo;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;

final class OctavoSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    interface Listener {
        void onChromeVisibilityChanged(boolean visible);
        void onAppearancePresented(OctavoAppearance appearance);
        void onAppearanceFailure();
        void onReaderSurfaceFailure();
        void onPresentationRetriesExhausted(
            boolean appearanceStillAwaiting);
        void onAppearanceRequestsSettled(OctavoAppearance appearance);
        void onReaderPresentationChanged(String progressLabel);
    }

    private static final long APPEARANCE_COALESCE_MILLIS = 90;
    private static final int MAX_PRESENTATION_RETRIES = 4;
    private static final long PRESENTATION_RETRY_MILLIS = 32;
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
    static final int STATE_FIELD_COUNT = 89;

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
    private OctavoAppearance presentedAppearance;
    private OctavoAppearance requestedAppearance;
    private OctavoAppearance nativeAppearanceAwaitingPresentation;
    private boolean appearanceApplyPosted;
    private boolean forceAppearanceRequest;
    private int presentationRetryCount;
    private boolean presentationFailureNotified;
    private boolean chromeVisible;
    private long lastNotifiedFrameCount;
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

    OctavoSurfaceView(Context context,
                      OctavoLibraryStore libraryStore,
                      OctavoLibraryStore.Session session,
                      OctavoAppearance appearance,
                      boolean chromeVisible,
                      Listener listener) {
        super(context);
        this.libraryStore = libraryStore;
        this.listener = listener;
        book = session.book;
        presentedAppearance = appearance == null
            ? OctavoAppearance.defaults() : appearance;
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
            typography.alpha);
        if (nativeHandle == 0) {
            throw new IllegalStateException(
                "Unable to create the 8vo native application state");
        }

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
        if (nativeHandle != 0) {
            capturePresentedPosition();
            flushPresentedPosition();
            OctavoNative.surfaceDestroyed(nativeHandle);
            removeCallbacks(presentPage);
            presentationPosted = false;
            resetPresentationRetries();
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
        if ((result & OctavoNative.TOUCH_CHROME_REQUESTED) != 0) {
            updateChromeVisibility(!chromeVisible, true);
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
        if (!appearanceStillAwaiting) {
            removeCallbacks(applyRequestedAppearance);
            appearanceApplyPosted = false;
            requestedAppearance = null;
            forceAppearanceRequest = false;
        }
        if (listener != null) {
            listener.onPresentationRetriesExhausted(
                appearanceStillAwaiting);
        }
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

    private static boolean presentationPending(long[] state) {
        return validState(state)
            && (state[STATE_PAGE_MOVE_PRESENTATION_PENDING] != 0
                || state[STATE_REFLOW_PRESENTATION_PENDING] != 0
                || state[STATE_HOST_PRESENTATION_PENDING] != 0
                || state[STATE_APPEARANCE_GENERATION]
                   != state[STATE_APPEARANCE_PRESENTED_GENERATION]);
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

    void hostResumed() {
        if (nativeHandle != 0 && !hostResumed) {
            resetPresentationRetries();
            OctavoNative.hostResumed(nativeHandle);
            hostResumed = true;
            notifyNativePresentationIfChanged();
            requestNativePresentation();
            scheduleAppearanceDrain(0);
        }
    }

    void hostPaused() {
        if (nativeHandle != 0 && hostResumed) {
            capturePresentedPosition();
            flushPresentedPosition();
            removeCallbacks(presentPage);
            presentationPosted = false;
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
            presentationPosted = false;
            persistencePosted = false;
            appearanceApplyPosted = false;
            resetPresentationRetries();
            requestedAppearance = null;
            nativeAppearanceAwaitingPresentation = null;
            accessibilityProvider.clearAccessibilityState();
            getHolder().removeCallback(this);
            setOnApplyWindowInsetsListener(null);
            OctavoNative.destroy(nativeHandle);
            nativeHandle = 0;
        }
    }
}
