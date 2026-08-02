package ro.devze.octavo;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded virtual accessibility tree for the native reader surface. */
final class OctavoReaderAccessibilityProvider extends AccessibilityNodeProvider {
    static final int VIRTUAL_PAGE_CONTENT = 1;
    static final int VIRTUAL_PREVIOUS_PAGE = 2;
    static final int VIRTUAL_NEXT_PAGE = 3;
    static final int VIRTUAL_PROGRESS_STATUS = 4;

    static final int SEMANTIC_SNAPSHOT_VERSION = 1;
    static final int SEMANTIC_SNAPSHOT_HEADER_SIZE = 3;
    static final int SEMANTIC_SNAPSHOT_STRIDE = 11;
    static final int SEMANTIC_HEADER_VERSION = 0;
    static final int SEMANTIC_HEADER_COUNT = 1;
    static final int SEMANTIC_HEADER_STRIDE = 2;
    static final int SEMANTIC_RECORD_STABLE_ID = 0;
    static final int SEMANTIC_RECORD_CONTROL = 1;
    static final int SEMANTIC_RECORD_ROLE = 2;
    static final int SEMANTIC_RECORD_FLAGS = 3;
    static final int SEMANTIC_RECORD_LEFT = 4;
    static final int SEMANTIC_RECORD_TOP = 5;
    static final int SEMANTIC_RECORD_WIDTH = 6;
    static final int SEMANTIC_RECORD_HEIGHT = 7;
    static final int SEMANTIC_RECORD_RANGE_VALUE = 8;
    static final int SEMANTIC_RECORD_RANGE_MIN = 9;
    static final int SEMANTIC_RECORD_RANGE_MAX = 10;

    private static final int SEMANTIC_CONTROL_PREVIOUS_PAGE = 14;
    private static final int SEMANTIC_CONTROL_NEXT_PAGE = 15;
    private static final int SEMANTIC_CONTROL_PROGRESS = 16;
    private static final int SEMANTIC_ROLE_BUTTON = 4;
    private static final int SEMANTIC_ROLE_SLIDER = 8;
    private static final int SEMANTIC_ROLE_STATUS = 14;
    private static final long SEMANTIC_FLAG_ENABLED = 1L << 0;
    private static final long SEMANTIC_FLAG_FOCUSABLE = 1L << 1;
    private static final long SEMANTIC_FLAG_OFFSCREEN = 1L << 8;
    private static final int MAX_SEMANTIC_RECORDS = 384;

    private static final int INVALID_VIRTUAL_ID = Integer.MIN_VALUE;
    private static final int MAX_PAGE_TEXT_CHARACTERS = 4096;
    private static final int MAX_LABEL_CHARACTERS = 512;
    private static final int MAX_SEARCH_CHARACTERS = 256;
    private static final int MAX_SEARCH_RESULTS = 4;
    private static final int[] FOCUS_ORDER = {
        VIRTUAL_PAGE_CONTENT,
        VIRTUAL_PREVIOUS_PAGE,
        VIRTUAL_NEXT_PAGE,
        VIRTUAL_PROGRESS_STATUS
    };

    private final OctavoSurfaceView owner;
    private final AccessibilityManager accessibilityManager;
    private int accessibilityFocusedVirtualId = INVALID_VIRTUAL_ID;
    private int keyboardFocusedVirtualId = INVALID_VIRTUAL_ID;
    private int requestedKeyboardFocusVirtualId = INVALID_VIRTUAL_ID;
    private int hoveredVirtualId = INVALID_VIRTUAL_ID;
    private boolean pageMoveAwaitingPresentation;

    OctavoReaderAccessibilityProvider(OctavoSurfaceView owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Reader accessibility owner is required");
        }
        this.owner = owner;
        accessibilityManager = (AccessibilityManager)owner.getContext()
            .getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        ReaderSnapshot snapshot = readSnapshot();
        if (virtualViewId == HOST_VIEW_ID) {
            return createHostNode(snapshot);
        }
        return isVirtualChild(virtualViewId)
            ? createVirtualNode(virtualViewId, snapshot)
            : null;
    }

    @Override
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(
        String searched,
        int virtualViewId) {
        String query = boundedText(searched, MAX_SEARCH_CHARACTERS)
            .trim()
            .toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        ReaderSnapshot snapshot = readSnapshot();
        ArrayList<AccessibilityNodeInfo> result =
            new ArrayList<>(MAX_SEARCH_RESULTS);
        if (virtualViewId == HOST_VIEW_ID) {
            for (int id : FOCUS_ORDER) {
                addSearchResult(result, id, snapshot, query);
            }
        } else if (isVirtualChild(virtualViewId)) {
            addSearchResult(result, virtualViewId, snapshot, query);
        }
        return result;
    }

    @Override
    public AccessibilityNodeInfo findFocus(int focus) {
        int virtualViewId = INVALID_VIRTUAL_ID;
        if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) {
            virtualViewId = accessibilityFocusedVirtualId;
        } else if (focus == AccessibilityNodeInfo.FOCUS_INPUT) {
            virtualViewId = keyboardFocusedVirtualId;
        }
        return isVirtualChild(virtualViewId)
            ? createAccessibilityNodeInfo(virtualViewId)
            : null;
    }

    @Override
    public boolean performAction(int virtualViewId,
                                 int action,
                                 Bundle arguments) {
        if (virtualViewId == HOST_VIEW_ID) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                return requestPageMove(VIRTUAL_PAGE_CONTENT, -1);
            }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                return requestPageMove(VIRTUAL_PAGE_CONTENT, 1);
            }
            return owner.performAccessibilityAction(action, arguments);
        }
        if (!isVirtualChild(virtualViewId)) {
            return false;
        }
        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
            return requestAccessibilityFocus(virtualViewId);
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            return clearAccessibilityFocus(virtualViewId, true);
        }
        if (action == AccessibilityNodeInfo.ACTION_FOCUS) {
            return requestKeyboardFocus(virtualViewId);
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_FOCUS) {
            return clearKeyboardFocus(virtualViewId);
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction
                          .ACTION_SHOW_ON_SCREEN.getId()) {
            NodeSpec spec = nodeSpec(virtualViewId, readSnapshot());
            if (spec == null || !spec.visible) {
                return false;
            }
            owner.requestRectangleOnScreen(new Rect(spec.bounds), false);
            return true;
        }
        if (virtualViewId == VIRTUAL_PAGE_CONTENT) {
            if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                if (!owner.isEnabled()) {
                    return false;
                }
                if (!owner.toggleChromeForAccessibility()) {
                    return false;
                }
                sendVirtualEvent(virtualViewId,
                                 AccessibilityEvent.TYPE_VIEW_CLICKED);
                onChromeVisibilityChanged();
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                return requestPageMove(virtualViewId, -1);
            }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                return requestPageMove(virtualViewId, 1);
            }
        } else if (virtualViewId == VIRTUAL_PREVIOUS_PAGE
                   && (action == AccessibilityNodeInfo.ACTION_CLICK
                       || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            return requestPageMove(virtualViewId, -1);
        } else if (virtualViewId == VIRTUAL_NEXT_PAGE
                   && (action == AccessibilityNodeInfo.ACTION_CLICK
                       || action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return requestPageMove(virtualViewId, 1);
        }
        return false;
    }

    boolean dispatchHoverEvent(MotionEvent event) {
        if (event == null
            || accessibilityManager == null
            || !accessibilityManager.isEnabled()
            || !accessibilityManager.isTouchExplorationEnabled()) {
            hoveredVirtualId = INVALID_VIRTUAL_ID;
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER
            || action == MotionEvent.ACTION_HOVER_MOVE) {
            int target = virtualViewAt(event.getX(), event.getY());
            boolean handled = target != INVALID_VIRTUAL_ID
                || hoveredVirtualId != INVALID_VIRTUAL_ID;
            updateHoveredVirtualId(target);
            return handled;
        }
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            boolean handled = hoveredVirtualId != INVALID_VIRTUAL_ID;
            updateHoveredVirtualId(INVALID_VIRTUAL_ID);
            return handled;
        }
        return false;
    }

    /** Call only after a frame was successfully posted to the native window. */
    void onReaderPresentationChanged() {
        if (pageMoveAwaitingPresentation) {
            pageMoveAwaitingPresentation = false;
            sendVirtualEvent(VIRTUAL_PAGE_CONTENT,
                             AccessibilityEvent.TYPE_VIEW_SCROLLED);
        }
        reconcileKeyboardFocus(false, INVALID_VIRTUAL_ID);
        sendSubtreeChanged();
    }

    void onChromeVisibilityChanged() {
        if (owner.chromeVisibleForAccessibility()) {
            if (accessibilityFocusedVirtualId != VIRTUAL_PAGE_CONTENT) {
                int previous = accessibilityFocusedVirtualId;
                accessibilityFocusedVirtualId = INVALID_VIRTUAL_ID;
                if (isVirtualChild(previous)) {
                    sendVirtualEvent(
                        previous,
                        AccessibilityEvent
                            .TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                }
            }
            if (hoveredVirtualId != VIRTUAL_PAGE_CONTENT) {
                int previous = hoveredVirtualId;
                hoveredVirtualId = INVALID_VIRTUAL_ID;
                if (isVirtualChild(previous)) {
                    sendVirtualEvent(
                        previous,
                        AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                }
            }
        }
        reconcileKeyboardFocus(false, INVALID_VIRTUAL_ID);
        owner.invalidate();
        sendSubtreeChanged();
    }

    void onOwnerFocusChanged(boolean hasFocus, int direction) {
        if (hasFocus) {
            reconcileKeyboardFocus(
                direction == View.FOCUS_BACKWARD,
                requestedKeyboardFocusVirtualId);
        } else if (keyboardFocusedVirtualId != INVALID_VIRTUAL_ID) {
            clearKeyboardFocusState();
        }
    }

    boolean moveKeyboardFocus(boolean backward) {
        if (!owner.isFocused()) {
            return false;
        }
        ReaderSnapshot snapshot = readSnapshot();
        int index = focusOrderIndex(keyboardFocusedVirtualId);
        int step = backward ? -1 : 1;
        if (index < 0) {
            int target = boundaryKeyboardFocus(backward, snapshot);
            return isVirtualChild(target)
                && setKeyboardFocus(target);
        }
        for (int next = index + step;
             index >= 0 && next >= 0 && next < FOCUS_ORDER.length;
             next += step) {
            int candidate = FOCUS_ORDER[next];
            if (isKeyboardFocusCandidate(candidate, snapshot)) {
                return setKeyboardFocus(candidate);
            }
        }
        return false;
    }

    boolean activateKeyboardFocus() {
        if (!owner.isFocused()) {
            return false;
        }
        ReaderSnapshot snapshot = readSnapshot();
        int target = keyboardFocusedVirtualId;
        NodeSpec spec = nodeSpec(target, snapshot);
        if (!isKeyboardFocusCandidate(target, snapshot)
            || spec == null
            || !spec.clickable) {
            return false;
        }
        return performAction(
            target, AccessibilityNodeInfo.ACTION_CLICK, (Bundle)null);
    }

    int keyboardFocusedVirtualIdForTesting() {
        return keyboardFocusedVirtualId;
    }

    void clearAccessibilityState() {
        accessibilityFocusedVirtualId = INVALID_VIRTUAL_ID;
        keyboardFocusedVirtualId = INVALID_VIRTUAL_ID;
        requestedKeyboardFocusVirtualId = INVALID_VIRTUAL_ID;
        hoveredVirtualId = INVALID_VIRTUAL_ID;
        pageMoveAwaitingPresentation = false;
    }

    private AccessibilityNodeInfo createHostNode(ReaderSnapshot snapshot) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(owner);
        owner.onInitializeAccessibilityNodeInfo(info);
        info.setSource(owner);
        info.setPackageName(owner.getContext().getPackageName());
        info.setClassName(owner.getAccessibilityClassName());
        info.setContentDescription(null);
        info.setFocusable(false);
        info.setFocused(false);
        info.removeAction(AccessibilityNodeInfo.ACTION_FOCUS);
        info.removeAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
        info.setClickable(false);
        info.setScrollable(snapshot.canPrevious || snapshot.canNext);
        if (Build.VERSION.SDK_INT >= 28) {
            info.setScreenReaderFocusable(false);
        }
        if (snapshot.canPrevious) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
        if (snapshot.canNext) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }
        for (int id : FOCUS_ORDER) {
            if (id == VIRTUAL_PAGE_CONTENT
                || !owner.chromeVisibleForAccessibility()) {
                info.addChild(owner, id);
            }
        }
        return info;
    }

    private AccessibilityNodeInfo createVirtualNode(int virtualViewId,
                                                     ReaderSnapshot snapshot) {
        NodeSpec spec = nodeSpec(virtualViewId, snapshot);
        if (spec == null) {
            return null;
        }
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setSource(owner, virtualViewId);
        info.setParent(owner);
        info.setPackageName(owner.getContext().getPackageName());
        info.setClassName(spec.className);
        info.setBoundsInParent(spec.bounds);
        Rect screenBounds = boundsInScreen(spec.bounds);
        info.setBoundsInScreen(screenBounds);
        info.setVisibleToUser(spec.visible && isVisibleToUser(screenBounds));
        info.setEnabled(spec.enabled);
        info.setFocusable(spec.focusable);
        info.setFocused(keyboardFocusedVirtualId == virtualViewId);
        info.setAccessibilityFocused(
            accessibilityFocusedVirtualId == virtualViewId);
        info.setClickable(spec.clickable);
        info.setScrollable(spec.canScrollBackward || spec.canScrollForward);
        if (Build.VERSION.SDK_INT >= 28) {
            info.setScreenReaderFocusable(spec.focusable);
        }
        if (!isEmpty(spec.text)) {
            info.setText(spec.text);
        }
        if (!isEmpty(spec.contentDescription)) {
            info.setContentDescription(spec.contentDescription);
        }
        if (!isEmpty(spec.hint)) {
            info.setHintText(spec.hint);
        }
        if (!isEmpty(spec.stateDescription) && Build.VERSION.SDK_INT >= 30) {
            info.setStateDescription(spec.stateDescription);
        }
        if (spec.liveRegion) {
            info.setLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
        if (spec.rangeMax >= spec.rangeMin) {
            float minimum = (float)spec.rangeMin;
            float maximum = (float)spec.rangeMax;
            float value = (float)Math.min(
                Math.max(spec.rangeValue, spec.rangeMin), spec.rangeMax);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                minimum,
                maximum,
                value));
        }
        addFocusActions(info, virtualViewId, spec.focusable);
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN);
        if (spec.clickable) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                spec.clickActionLabel));
        }
        if (spec.canScrollBackward) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
        if (spec.canScrollForward) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }
        addTraversalOrder(info, virtualViewId);
        return info;
    }

    private void addFocusActions(AccessibilityNodeInfo info,
                                 int virtualViewId,
                                 boolean focusable) {
        if (focusable) {
            info.addAction(keyboardFocusedVirtualId == virtualViewId
                               ? AccessibilityNodeInfo.AccessibilityAction
                                   .ACTION_CLEAR_FOCUS
                               : AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS);
        }
        info.addAction(accessibilityFocusedVirtualId == virtualViewId
                           ? AccessibilityNodeInfo.AccessibilityAction
                               .ACTION_CLEAR_ACCESSIBILITY_FOCUS
                           : AccessibilityNodeInfo.AccessibilityAction
                               .ACTION_ACCESSIBILITY_FOCUS);
    }

    private void addTraversalOrder(AccessibilityNodeInfo info,
                                   int virtualViewId) {
        if (owner.chromeVisibleForAccessibility()) {
            return;
        }
        int index = focusOrderIndex(virtualViewId);
        if (index > 0) {
            info.setTraversalAfter(owner, FOCUS_ORDER[index - 1]);
        }
        if (index >= 0 && index + 1 < FOCUS_ORDER.length) {
            info.setTraversalBefore(owner, FOCUS_ORDER[index + 1]);
        }
    }

    private void addSearchResult(ArrayList<AccessibilityNodeInfo> result,
                                 int virtualViewId,
                                 ReaderSnapshot snapshot,
                                 String query) {
        if (result.size() >= MAX_SEARCH_RESULTS) {
            return;
        }
        NodeSpec spec = nodeSpec(virtualViewId, snapshot);
        if (spec != null && specMatches(spec, query)) {
            AccessibilityNodeInfo node = createVirtualNode(virtualViewId, snapshot);
            if (node != null) {
                result.add(node);
            }
        }
    }

    private static boolean specMatches(NodeSpec spec, String query) {
        return textContains(spec.text, query)
            || textContains(spec.contentDescription, query)
            || textContains(spec.hint, query)
            || textContains(spec.stateDescription, query)
            || textContains(spec.clickActionLabel, query);
    }

    private static boolean textContains(CharSequence text, String query) {
        return text != null
            && text.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean requestAccessibilityFocus(int virtualViewId) {
        NodeSpec spec = nodeSpec(virtualViewId, readSnapshot());
        if (accessibilityManager == null
            || !accessibilityManager.isEnabled()
            || spec == null
            || !spec.focusable
            || !spec.visible
            || accessibilityFocusedVirtualId == virtualViewId) {
            return false;
        }
        int previous = accessibilityFocusedVirtualId;
        accessibilityFocusedVirtualId = virtualViewId;
        if (isVirtualChild(previous)) {
            sendVirtualEvent(
                previous,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        }
        sendVirtualEvent(virtualViewId,
                         AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        owner.invalidate();
        return true;
    }

    private boolean clearAccessibilityFocus(int virtualViewId,
                                            boolean sendEvent) {
        if (accessibilityFocusedVirtualId != virtualViewId) {
            return false;
        }
        accessibilityFocusedVirtualId = INVALID_VIRTUAL_ID;
        if (sendEvent) {
            sendVirtualEvent(
                virtualViewId,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        }
        owner.invalidate();
        return true;
    }

    private boolean requestKeyboardFocus(int virtualViewId) {
        NodeSpec spec = nodeSpec(virtualViewId, readSnapshot());
        if (spec == null || !spec.focusable
            || keyboardFocusedVirtualId == virtualViewId) {
            return false;
        }
        if (!owner.isFocused()) {
            requestedKeyboardFocusVirtualId = virtualViewId;
            boolean focused = owner.requestFocus();
            requestedKeyboardFocusVirtualId = INVALID_VIRTUAL_ID;
            if (!focused) {
                return false;
            }
            if (keyboardFocusedVirtualId == virtualViewId) {
                return true;
            }
        }
        return setKeyboardFocus(virtualViewId);
    }

    private boolean setKeyboardFocus(int virtualViewId) {
        if (keyboardFocusedVirtualId == virtualViewId) {
            return false;
        }
        keyboardFocusedVirtualId = virtualViewId;
        sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_FOCUSED);
        owner.invalidate();
        return true;
    }

    private boolean clearKeyboardFocus(int virtualViewId) {
        if (keyboardFocusedVirtualId != virtualViewId) {
            return false;
        }
        keyboardFocusedVirtualId = INVALID_VIRTUAL_ID;
        owner.invalidate();
        sendSubtreeChanged();
        return true;
    }

    private boolean requestPageMove(int eventSource, int direction) {
        ReaderSnapshot snapshot = readSnapshot();
        NodeSpec source = nodeSpec(eventSource, snapshot);
        boolean sourceAvailable = source != null
            && source.visible
            && source.enabled;
        if ((direction < 0 && !snapshot.canPrevious)
            || (direction > 0 && !snapshot.canNext)
            || (direction != -1 && direction != 1)
            || !sourceAvailable) {
            return false;
        }
        boolean wasAwaitingPresentation = pageMoveAwaitingPresentation;
        pageMoveAwaitingPresentation = true;
        if (!owner.movePageForAccessibility(direction)) {
            pageMoveAwaitingPresentation = wasAwaitingPresentation;
            return false;
        }
        if (eventSource != VIRTUAL_PAGE_CONTENT) {
            sendVirtualEvent(eventSource, AccessibilityEvent.TYPE_VIEW_CLICKED);
        }
        return true;
    }

    private void updateHoveredVirtualId(int virtualViewId) {
        if (hoveredVirtualId == virtualViewId) {
            return;
        }
        int previous = hoveredVirtualId;
        hoveredVirtualId = virtualViewId;
        if (isVirtualChild(previous)) {
            sendVirtualEvent(previous, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        }
        if (isVirtualChild(virtualViewId)) {
            sendVirtualEvent(virtualViewId,
                             AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
    }

    private int virtualViewAt(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return INVALID_VIRTUAL_ID;
        }
        int pointX = (int)x;
        int pointY = (int)y;
        ReaderSnapshot snapshot = readSnapshot();
        if (visibleNodeContains(VIRTUAL_PROGRESS_STATUS,
                                snapshot,
                                pointX,
                                pointY)) {
            return VIRTUAL_PROGRESS_STATUS;
        }
        if (visibleNodeContains(VIRTUAL_PREVIOUS_PAGE,
                                snapshot,
                                pointX,
                                pointY)) {
            return VIRTUAL_PREVIOUS_PAGE;
        }
        if (visibleNodeContains(VIRTUAL_NEXT_PAGE,
                                snapshot,
                                pointX,
                                pointY)) {
            return VIRTUAL_NEXT_PAGE;
        }
        if (visibleNodeContains(VIRTUAL_PAGE_CONTENT,
                                snapshot,
                                pointX,
                                pointY)) {
            return VIRTUAL_PAGE_CONTENT;
        }
        return INVALID_VIRTUAL_ID;
    }

    private boolean visibleNodeContains(int virtualViewId,
                                        ReaderSnapshot snapshot,
                                        int x,
                                        int y) {
        NodeSpec spec = nodeSpec(virtualViewId, snapshot);
        return spec != null && spec.visible && spec.bounds.contains(x, y);
    }

    private void sendVirtualEvent(int virtualViewId, int eventType) {
        if (accessibilityManager == null
            || !accessibilityManager.isEnabled()
            || !isVirtualChild(virtualViewId)) {
            return;
        }
        ViewParent parent = owner.getParent();
        if (parent == null) {
            return;
        }
        ReaderSnapshot snapshot = readSnapshot();
        NodeSpec spec = nodeSpec(virtualViewId, snapshot);
        boolean staleClear = spec == null
            && (eventType == AccessibilityEvent
                    .TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                || eventType == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        if (spec == null && !staleClear) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setSource(owner, virtualViewId);
        event.setPackageName(owner.getContext().getPackageName());
        event.setClassName(
            spec == null ? "android.view.View" : spec.className);
        event.setEnabled(spec != null && spec.enabled);
        event.setScrollable(
            spec != null
                && (spec.canScrollBackward || spec.canScrollForward));
        if (spec != null) {
            if (!isEmpty(spec.text)) {
                event.getText().add(spec.text);
            }
            if (!isEmpty(spec.contentDescription)) {
                event.setContentDescription(spec.contentDescription);
            }
        }
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            event.setItemCount(saturatedInt(snapshot.progressCount));
            int index = saturatedInt(Math.max(snapshot.progressValue, 0));
            event.setFromIndex(index);
            event.setToIndex(index);
        }
        parent.requestSendAccessibilityEvent(owner, event);
    }

    private void sendSubtreeChanged() {
        if (accessibilityManager == null || !accessibilityManager.isEnabled()) {
            return;
        }
        ViewParent parent = owner.getParent();
        if (parent == null) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setSource(owner);
        event.setPackageName(owner.getContext().getPackageName());
        event.setClassName(owner.getAccessibilityClassName());
        event.setContentChangeTypes(
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        parent.requestSendAccessibilityEvent(owner, event);
    }

    private Rect boundsInScreen(Rect parentBounds) {
        int[] parentLocation = new int[2];
        ViewParent parent = owner.getParent();
        if (!(parent instanceof View)) {
            owner.getLocationOnScreen(parentLocation);
            Rect fallback = new Rect(parentBounds);
            fallback.offset(parentLocation[0], parentLocation[1]);
            return fallback;
        }
        ((View)parent).getLocationOnScreen(parentLocation);
        float originX = parentLocation[0] + owner.getLeft()
            + owner.getTranslationX();
        float originY = parentLocation[1] + owner.getTop()
            + owner.getTranslationY();
        float pivotX = owner.getPivotX();
        float pivotY = owner.getPivotY();
        float scaleX = owner.getScaleX();
        float scaleY = owner.getScaleY();
        float left = originX + pivotX
            + (parentBounds.left - pivotX) * scaleX;
        float top = originY + pivotY
            + (parentBounds.top - pivotY) * scaleY;
        float right = originX + pivotX
            + (parentBounds.right - pivotX) * scaleX;
        float bottom = originY + pivotY
            + (parentBounds.bottom - pivotY) * scaleY;
        return new Rect(
            (int)Math.floor(Math.min(left, right)),
            (int)Math.floor(Math.min(top, bottom)),
            (int)Math.ceil(Math.max(left, right)),
            (int)Math.ceil(Math.max(top, bottom)));
    }

    private boolean isVisibleToUser(Rect screenBounds) {
        if (screenBounds.isEmpty()
            || !owner.isShown()
            || owner.getWindowVisibility() != View.VISIBLE) {
            return false;
        }
        Rect visible = new Rect();
        return owner.getGlobalVisibleRect(visible) && visible.intersect(screenBounds);
    }

    private NodeSpec nodeSpec(int virtualViewId, ReaderSnapshot snapshot) {
        if (virtualViewId == VIRTUAL_PAGE_CONTENT) {
            String text = snapshot.ready
                ? boundedText(owner.visibleTextForTesting(),
                              MAX_PAGE_TEXT_CHARACTERS)
                : "";
            boolean chromeVisible = owner.chromeVisibleForAccessibility();
            String hint = chromeVisible
                ? "Double-tap to hide reading controls"
                : "Double-tap to show reading controls";
            return new NodeSpec(snapshot.pageBounds,
                                "android.widget.TextView",
                                text,
                                text.isEmpty() ? "Book page" : null,
                                hint,
                                chromeVisible
                                    ? "Reading controls visible"
                                    : "Reading controls hidden",
                                snapshot.ready && owner.isEnabled(),
                                snapshot.ready,
                                true,
                                snapshot.ready,
                                hint,
                                snapshot.ready && snapshot.canPrevious,
                                snapshot.ready && snapshot.canNext,
                                false,
                                0,
                                1,
                                0);
        }
        if (owner.chromeVisibleForAccessibility()) {
            return null;
        }
        SemanticRecord semantic = snapshot.semanticForVirtualId(virtualViewId);
        if (semantic != null) {
            return semanticNodeSpec(virtualViewId, semantic, snapshot);
        }
        return fallbackControlSpec(virtualViewId, snapshot);
    }

    private NodeSpec semanticNodeSpec(int virtualViewId,
                                      SemanticRecord semantic,
                                      ReaderSnapshot snapshot) {
        Rect fallbackBounds = virtualViewId == VIRTUAL_PREVIOUS_PAGE
            ? snapshot.previousBounds
            : virtualViewId == VIRTUAL_NEXT_PAGE
                ? snapshot.nextBounds
                : snapshot.progressBounds;
        Rect bounds = semantic.bounds.isEmpty()
            ? fallbackBounds
            : semantic.bounds;
        boolean previous = virtualViewId == VIRTUAL_PREVIOUS_PAGE;
        boolean next = virtualViewId == VIRTUAL_NEXT_PAGE;
        boolean progress = virtualViewId == VIRTUAL_PROGRESS_STATUS;
        String fallbackName = previous
            ? "Previous page"
            : next ? "Next page" : "Reading progress";
        String name = semantic.name.isEmpty() ? fallbackName : semantic.name;
        String value = semantic.value;
        String spoken = value.isEmpty() ? name : name + ". " + value;
        String className = semantic.role == SEMANTIC_ROLE_SLIDER
            ? "android.widget.SeekBar"
            : semantic.role == SEMANTIC_ROLE_STATUS
                ? "android.widget.TextView"
                : "android.widget.Button";
        boolean enabled = semantic.enabled();
        boolean visible = !semantic.offscreen();
        boolean clickable = (previous || next) && enabled && visible;
        long rangeMin = progress ? semantic.rangeMin : 1;
        long rangeMax = progress ? semantic.rangeMax : 0;
        return new NodeSpec(bounds,
                            className,
                            value.isEmpty() ? name : value,
                            spoken,
                            null,
                            progress ? value : null,
                            enabled,
                            semantic.focusable() && visible,
                            visible,
                            clickable,
                            clickable ? name : null,
                            previous && enabled,
                            next && enabled,
                            progress,
                            semantic.rangeValue,
                            rangeMin,
                            rangeMax);
    }

    private NodeSpec fallbackControlSpec(int virtualViewId,
                                         ReaderSnapshot snapshot) {
        if (virtualViewId == VIRTUAL_PREVIOUS_PAGE) {
            return new NodeSpec(snapshot.previousBounds,
                                "android.widget.Button",
                                "Previous page",
                                null,
                                null,
                                snapshot.canPrevious ? null : "Beginning of book",
                                snapshot.canPrevious,
                                true,
                                true,
                                snapshot.canPrevious,
                                "Previous page",
                                snapshot.canPrevious,
                                false,
                                false,
                                0,
                                1,
                                0);
        }
        if (virtualViewId == VIRTUAL_NEXT_PAGE) {
            return new NodeSpec(snapshot.nextBounds,
                                "android.widget.Button",
                                "Next page",
                                null,
                                null,
                                snapshot.canNext ? null : "End of book",
                                snapshot.canNext,
                                true,
                                true,
                                snapshot.canNext,
                                "Next page",
                                false,
                                snapshot.canNext,
                                false,
                                0,
                                1,
                                0);
        }
        if (virtualViewId == VIRTUAL_PROGRESS_STATUS) {
            String progress = snapshot.ready
                ? boundedText(owner.progressLabelForTesting(),
                              MAX_LABEL_CHARACTERS)
                : "Page presentation pending";
            if (progress.isEmpty()) {
                progress = fallbackProgressLabel(snapshot);
            }
            return new NodeSpec(snapshot.progressBounds,
                                "android.widget.ProgressBar",
                                progress,
                                "Reading progress. " + progress,
                                null,
                                progress,
                                snapshot.ready,
                                snapshot.ready,
                                true,
                                false,
                                null,
                                false,
                                false,
                                true,
                                snapshot.progressValue,
                                0,
                                Math.max(snapshot.progressCount - 1, 0));
        }
        return null;
    }

    private ReaderSnapshot readSnapshot() {
        long[] state = owner.nativeStateForTesting();
        Rect ownerBounds = new Rect(0,
                                    0,
                                    Math.max(owner.getWidth(), 0),
                                    Math.max(owner.getHeight(), 0));
        Rect pageBounds = rectFromState(state,
                                        OctavoSurfaceView.STATE_PAGE_SURFACE_X,
                                        OctavoSurfaceView.STATE_PAGE_SURFACE_Y,
                                        OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH,
                                        OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT,
                                        ownerBounds);
        if (pageBounds.isEmpty()) {
            pageBounds.set(ownerBounds);
        }
        int minimumTarget = Math.max(
            1,
            Math.round(48.0f
                       * owner.getResources().getDisplayMetrics().density));
        int progressHeight = Math.min(pageBounds.height(), minimumTarget);
        Rect fallbackProgress = new Rect(
            pageBounds.left,
            Math.max(pageBounds.top, pageBounds.bottom - progressHeight),
            pageBounds.right,
            pageBounds.bottom);
        int bodyBottom = fallbackProgress.top > pageBounds.top
            ? fallbackProgress.top
            : pageBounds.bottom;
        int edgeWidth = Math.min(
            Math.max(pageBounds.width() / 3, minimumTarget),
            Math.max(pageBounds.width() / 2, 1));
        Rect fallbackPrevious = new Rect(
            pageBounds.left,
            pageBounds.top,
            Math.min(pageBounds.right, pageBounds.left + edgeWidth),
            bodyBottom);
        Rect fallbackNext = new Rect(
            Math.max(pageBounds.left, pageBounds.right - edgeWidth),
            pageBounds.top,
            pageBounds.right,
            bodyBottom);

        boolean presentationPending =
            stateValue(
                state,
                OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING) == 1
            || stateValue(
                state,
                OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING) == 1
            || stateValue(
                state,
                OctavoSurfaceView.STATE_APPEARANCE_GENERATION)
               != stateValue(
                   state,
                   OctavoSurfaceView.STATE_APPEARANCE_PRESENTED_GENERATION);
        int navigation = owner.navigationAvailability();
        boolean ready = !presentationPending
            && stateValue(
                state,
                OctavoSurfaceView.STATE_READER_FRAME_READY) == 1
            && stateValue(
                state,
                OctavoSurfaceView.STATE_DOCUMENT_OPEN) == 1
            && (navigation & OctavoNative.NAVIGATION_READY) != 0;
        long pageIndex = stateValue(state, OctavoSurfaceView.STATE_PAGE_INDEX);
        long pageCount = stateValue(state, OctavoSurfaceView.STATE_PAGE_COUNT);
        long spineIndex = stateValue(
            state, OctavoSurfaceView.STATE_SPINE_INDEX);
        long sectionCount = stateValue(
            state, OctavoSurfaceView.STATE_SECTION_COUNT);
        boolean stateCanPrevious = ready
            && (navigation & OctavoNative.NAVIGATION_PREVIOUS) != 0;
        boolean stateCanNext = ready
            && (navigation & OctavoNative.NAVIGATION_NEXT) != 0;

        SemanticControls semantics = readSemanticControls(ownerBounds);
        boolean canPrevious = semantics.previous != null
            ? semantics.previous.enabled()
            : stateCanPrevious;
        boolean canNext = semantics.next != null
            ? semantics.next.enabled()
            : stateCanNext;
        Rect previousBounds = semantics.previous != null
            && !semantics.previous.bounds.isEmpty()
                ? semantics.previous.bounds
                : fallbackPrevious;
        Rect nextBounds = semantics.next != null
            && !semantics.next.bounds.isEmpty()
                ? semantics.next.bounds
                : fallbackNext;
        Rect progressBounds = semantics.progress != null
            && !semantics.progress.bounds.isEmpty()
                ? semantics.progress.bounds
                : fallbackProgress;

        long locationIndex = stateValue(
            state, OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX);
        long locationCount = stateValue(
            state, OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT);
        long progressValue = locationCount > 0
            ? Math.max(locationIndex, 0)
            : Math.max(pageIndex - 1, 0);
        long progressCount = locationCount > 0
            ? locationCount
            : Math.max(pageCount, 0);
        if (semantics.progress != null
            && semantics.progress.rangeMax >= semantics.progress.rangeMin) {
            progressValue = semantics.progress.rangeValue;
            progressCount = inclusiveRangeCount(
                semantics.progress.rangeMin, semantics.progress.rangeMax);
        }
        return new ReaderSnapshot(pageBounds,
                                  previousBounds,
                                  nextBounds,
                                  progressBounds,
                                  ready,
                                  canPrevious,
                                  canNext,
                                  pageIndex,
                                  pageCount,
                                  spineIndex,
                                  sectionCount,
                                  progressValue,
                                  progressCount,
                                  semantics);
    }

    private SemanticControls readSemanticControls(Rect clip) {
        long[] packed = owner.accessibilitySemanticSnapshotForTesting();
        if (packed == null || packed.length < SEMANTIC_SNAPSHOT_HEADER_SIZE
            || packed[SEMANTIC_HEADER_VERSION] != SEMANTIC_SNAPSHOT_VERSION
            || packed[SEMANTIC_HEADER_STRIDE] != SEMANTIC_SNAPSHOT_STRIDE) {
            return SemanticControls.EMPTY;
        }
        long rawCount = packed[SEMANTIC_HEADER_COUNT];
        if (rawCount < 0 || rawCount > MAX_SEMANTIC_RECORDS) {
            return SemanticControls.EMPTY;
        }
        int count = (int)rawCount;
        long expected = SEMANTIC_SNAPSHOT_HEADER_SIZE
            + (long)count * SEMANTIC_SNAPSHOT_STRIDE;
        if (expected != packed.length) {
            return SemanticControls.EMPTY;
        }

        SemanticRecord previous = null;
        SemanticRecord next = null;
        SemanticRecord progress = null;
        for (int index = 0; index < count; ++index) {
            int base = SEMANTIC_SNAPSHOT_HEADER_SIZE
                + index * SEMANTIC_SNAPSHOT_STRIDE;
            long stableId = packed[base + SEMANTIC_RECORD_STABLE_ID];
            int control = saturatedInt(
                packed[base + SEMANTIC_RECORD_CONTROL]);
            if (stableId == 0
                || (control != SEMANTIC_CONTROL_PREVIOUS_PAGE
                    && control != SEMANTIC_CONTROL_NEXT_PAGE
                    && control != SEMANTIC_CONTROL_PROGRESS)) {
                continue;
            }
            SemanticRecord record = new SemanticRecord(
                stableId,
                control,
                saturatedInt(packed[base + SEMANTIC_RECORD_ROLE]),
                packed[base + SEMANTIC_RECORD_FLAGS],
                rectFromValues(packed[base + SEMANTIC_RECORD_LEFT],
                               packed[base + SEMANTIC_RECORD_TOP],
                               packed[base + SEMANTIC_RECORD_WIDTH],
                               packed[base + SEMANTIC_RECORD_HEIGHT],
                               clip),
                packed[base + SEMANTIC_RECORD_RANGE_VALUE],
                packed[base + SEMANTIC_RECORD_RANGE_MIN],
                packed[base + SEMANTIC_RECORD_RANGE_MAX],
                boundedText(owner.accessibilitySemanticNameForTesting(index),
                            MAX_LABEL_CHARACTERS),
                boundedText(owner.accessibilitySemanticValueForTesting(index),
                            MAX_LABEL_CHARACTERS));
            if (control == SEMANTIC_CONTROL_PREVIOUS_PAGE && previous == null) {
                previous = record;
            } else if (control == SEMANTIC_CONTROL_NEXT_PAGE && next == null) {
                next = record;
            } else if (control == SEMANTIC_CONTROL_PROGRESS && progress == null) {
                progress = record;
            }
        }
        return new SemanticControls(previous, next, progress);
    }

    private static Rect rectFromState(long[] state,
                                      int xIndex,
                                      int yIndex,
                                      int widthIndex,
                                      int heightIndex,
                                      Rect clip) {
        return rectFromValues(stateValue(state, xIndex),
                              stateValue(state, yIndex),
                              stateValue(state, widthIndex),
                              stateValue(state, heightIndex),
                              clip);
    }

    private static Rect rectFromValues(long x,
                                       long y,
                                       long width,
                                       long height,
                                       Rect clip) {
        if (width <= 0 || height <= 0 || clip.isEmpty()) {
            return new Rect();
        }
        int left = saturatedInt(Math.max(x, clip.left));
        int top = saturatedInt(Math.max(y, clip.top));
        int right = saturatedInt(Math.min(saturatedAdd(x, width), clip.right));
        int bottom = saturatedInt(Math.min(saturatedAdd(y, height), clip.bottom));
        return right > left && bottom > top
            ? new Rect(left, top, right, bottom)
            : new Rect();
    }

    private static long stateValue(long[] state, int index) {
        return state != null && index >= 0 && index < state.length
            ? state[index]
            : 0;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static long inclusiveRangeCount(long minimum, long maximum) {
        if (maximum < minimum) {
            return 0;
        }
        if (minimum < 0 && maximum > Long.MAX_VALUE + minimum) {
            return Long.MAX_VALUE;
        }
        return saturatedAdd(maximum - minimum, 1);
    }

    private static int saturatedInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int)value;
    }

    private static String fallbackProgressLabel(ReaderSnapshot snapshot) {
        if (snapshot.pageIndex > 0 && snapshot.pageCount > 0) {
            return String.format(Locale.ROOT,
                                 "Page %d of %d",
                                 snapshot.pageIndex,
                                 snapshot.pageCount);
        }
        if (snapshot.sectionCount > 0 && snapshot.spineIndex >= 0) {
            return String.format(Locale.ROOT,
                                 "Section %d of %d",
                                 snapshot.spineIndex + 1,
                                 snapshot.sectionCount);
        }
        return snapshot.ready ? "Reading progress" : "Book page loading";
    }

    private static String boundedText(String value, int maximumCharacters) {
        if (value == null || value.isEmpty() || maximumCharacters <= 0) {
            return "";
        }
        if (value.length() <= maximumCharacters) {
            return value;
        }
        int end = maximumCharacters;
        if (Character.isHighSurrogate(value.charAt(end - 1))
            && end < value.length()
            && Character.isLowSurrogate(value.charAt(end))) {
            end -= 1;
        }
        return value.substring(0, end);
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private static boolean isVirtualChild(int virtualViewId) {
        return virtualViewId >= VIRTUAL_PAGE_CONTENT
            && virtualViewId <= VIRTUAL_PROGRESS_STATUS;
    }

    private static int focusOrderIndex(int virtualViewId) {
        for (int index = 0; index < FOCUS_ORDER.length; ++index) {
            if (FOCUS_ORDER[index] == virtualViewId) {
                return index;
            }
        }
        return -1;
    }

    private boolean isKeyboardFocusCandidate(int virtualViewId,
                                             ReaderSnapshot snapshot) {
        if (!isVirtualChild(virtualViewId)) {
            return false;
        }
        NodeSpec spec = nodeSpec(virtualViewId, snapshot);
        return spec != null
            && spec.visible
            && spec.focusable
            && (virtualViewId == VIRTUAL_PROGRESS_STATUS
                || (spec.enabled && spec.clickable));
    }

    private void reconcileKeyboardFocus(boolean backward,
                                        int requestedVirtualId) {
        if (!owner.isFocused()) {
            if (keyboardFocusedVirtualId != INVALID_VIRTUAL_ID) {
                clearKeyboardFocusState();
            }
            return;
        }
        ReaderSnapshot snapshot = readSnapshot();
        int target;
        if (isKeyboardFocusCandidate(requestedVirtualId, snapshot)) {
            target = requestedVirtualId;
        } else if (isKeyboardFocusCandidate(
                       keyboardFocusedVirtualId, snapshot)) {
            target = keyboardFocusedVirtualId;
        } else {
            target = boundaryKeyboardFocus(backward, snapshot);
        }
        if (target == keyboardFocusedVirtualId) {
            return;
        }
        if (isVirtualChild(target)) {
            setKeyboardFocus(target);
        } else if (keyboardFocusedVirtualId != INVALID_VIRTUAL_ID) {
            clearKeyboardFocusState();
        }
    }

    private void clearKeyboardFocusState() {
        keyboardFocusedVirtualId = INVALID_VIRTUAL_ID;
        owner.invalidate();
        sendSubtreeChanged();
    }

    void onChromeCompositionSettled() {
        owner.invalidate();
        sendSubtreeChanged();
    }

    private int boundaryKeyboardFocus(boolean backward,
                                      ReaderSnapshot snapshot) {
        int index = backward ? FOCUS_ORDER.length - 1 : 0;
        int step = backward ? -1 : 1;
        for (; index >= 0 && index < FOCUS_ORDER.length; index += step) {
            if (isKeyboardFocusCandidate(FOCUS_ORDER[index], snapshot)) {
                return FOCUS_ORDER[index];
            }
        }
        return INVALID_VIRTUAL_ID;
    }

    private static final class NodeSpec {
        final Rect bounds;
        final String className;
        final CharSequence text;
        final CharSequence contentDescription;
        final CharSequence hint;
        final CharSequence stateDescription;
        final boolean enabled;
        final boolean focusable;
        final boolean visible;
        final boolean clickable;
        final CharSequence clickActionLabel;
        final boolean canScrollBackward;
        final boolean canScrollForward;
        final boolean liveRegion;
        final long rangeValue;
        final long rangeMin;
        final long rangeMax;

        NodeSpec(Rect bounds,
                 String className,
                 CharSequence text,
                 CharSequence contentDescription,
                 CharSequence hint,
                 CharSequence stateDescription,
                 boolean enabled,
                 boolean focusable,
                 boolean visible,
                 boolean clickable,
                 CharSequence clickActionLabel,
                 boolean canScrollBackward,
                 boolean canScrollForward,
                 boolean liveRegion,
                 long rangeValue,
                 long rangeMin,
                 long rangeMax) {
            this.bounds = new Rect(bounds);
            this.className = className;
            this.text = text;
            this.contentDescription = contentDescription;
            this.hint = hint;
            this.stateDescription = stateDescription;
            this.enabled = enabled;
            this.focusable = focusable;
            this.visible = visible;
            this.clickable = clickable;
            this.clickActionLabel = clickActionLabel;
            this.canScrollBackward = canScrollBackward;
            this.canScrollForward = canScrollForward;
            this.liveRegion = liveRegion;
            this.rangeValue = rangeValue;
            this.rangeMin = rangeMin;
            this.rangeMax = rangeMax;
        }
    }

    private static final class SemanticRecord {
        final long stableId;
        final int control;
        final int role;
        final long flags;
        final Rect bounds;
        final long rangeValue;
        final long rangeMin;
        final long rangeMax;
        final String name;
        final String value;

        SemanticRecord(long stableId,
                       int control,
                       int role,
                       long flags,
                       Rect bounds,
                       long rangeValue,
                       long rangeMin,
                       long rangeMax,
                       String name,
                       String value) {
            this.stableId = stableId;
            this.control = control;
            this.role = role;
            this.flags = flags;
            this.bounds = new Rect(bounds);
            this.rangeValue = rangeValue;
            this.rangeMin = rangeMin;
            this.rangeMax = rangeMax;
            this.name = name;
            this.value = value;
        }

        boolean enabled() {
            return (flags & SEMANTIC_FLAG_ENABLED) != 0;
        }

        boolean focusable() {
            return (flags & SEMANTIC_FLAG_FOCUSABLE) != 0;
        }

        boolean offscreen() {
            return (flags & SEMANTIC_FLAG_OFFSCREEN) != 0;
        }
    }

    private static final class SemanticControls {
        static final SemanticControls EMPTY =
            new SemanticControls(null, null, null);

        final SemanticRecord previous;
        final SemanticRecord next;
        final SemanticRecord progress;

        SemanticControls(SemanticRecord previous,
                         SemanticRecord next,
                         SemanticRecord progress) {
            this.previous = previous;
            this.next = next;
            this.progress = progress;
        }

        SemanticRecord forVirtualId(int virtualViewId) {
            if (virtualViewId == VIRTUAL_PREVIOUS_PAGE) {
                return previous;
            }
            if (virtualViewId == VIRTUAL_NEXT_PAGE) {
                return next;
            }
            if (virtualViewId == VIRTUAL_PROGRESS_STATUS) {
                return progress;
            }
            return null;
        }
    }

    private static final class ReaderSnapshot {
        final Rect pageBounds;
        final Rect previousBounds;
        final Rect nextBounds;
        final Rect progressBounds;
        final boolean ready;
        final boolean canPrevious;
        final boolean canNext;
        final long pageIndex;
        final long pageCount;
        final long spineIndex;
        final long sectionCount;
        final long progressValue;
        final long progressCount;
        final SemanticControls semantics;

        ReaderSnapshot(Rect pageBounds,
                       Rect previousBounds,
                       Rect nextBounds,
                       Rect progressBounds,
                       boolean ready,
                       boolean canPrevious,
                       boolean canNext,
                       long pageIndex,
                       long pageCount,
                       long spineIndex,
                       long sectionCount,
                       long progressValue,
                       long progressCount,
                       SemanticControls semantics) {
            this.pageBounds = new Rect(pageBounds);
            this.previousBounds = new Rect(previousBounds);
            this.nextBounds = new Rect(nextBounds);
            this.progressBounds = new Rect(progressBounds);
            this.ready = ready;
            this.canPrevious = canPrevious;
            this.canNext = canNext;
            this.pageIndex = pageIndex;
            this.pageCount = pageCount;
            this.spineIndex = spineIndex;
            this.sectionCount = sectionCount;
            this.progressValue = progressValue;
            this.progressCount = progressCount;
            this.semantics = semantics;
        }

        SemanticRecord semanticForVirtualId(int virtualViewId) {
            return semantics.forVirtualId(virtualViewId);
        }
    }
}
