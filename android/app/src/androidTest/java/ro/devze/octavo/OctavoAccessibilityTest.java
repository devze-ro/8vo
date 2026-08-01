package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoAccessibilityTest {
    private static final int MAX_SEMANTIC_RECORDS = 384;
    private static final int MAX_ACCESSIBILITY_TEXT = 512;
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private static final int SEMANTIC_CONTROL_PREVIOUS_PAGE = 14;
    private static final int SEMANTIC_CONTROL_NEXT_PAGE = 15;
    private static final int SEMANTIC_CONTROL_PROGRESS = 16;
    private static final int SEMANTIC_ROLE_BUTTON = 4;
    private static final int SEMANTIC_ROLE_SLIDER = 8;
    private static final long SEMANTIC_FLAG_ENABLED = 1L << 0;
    private static final long SEMANTIC_FLAG_FOCUSABLE = 1L << 1;

    private static final int[] SEMANTIC_PAGE_STATE_FIELDS = {
        OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH,
        OctavoSurfaceView.STATE_PAGE_INDEX,
        OctavoSurfaceView.STATE_PAGE_COUNT,
        OctavoSurfaceView.STATE_SPINE_INDEX,
        OctavoSurfaceView.STATE_SECTION_COUNT,
        OctavoSurfaceView.STATE_PROGRESS_PAGE_INDEX,
        OctavoSurfaceView.STATE_PROGRESS_PAGE_COUNT,
        OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX,
        OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT,
        OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX,
        OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET,
        OctavoSurfaceView.STATE_PAGE_FIRST_BYTE,
        OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE
    };

    private interface StateCondition {
        boolean matches(long[] state);
    }

    @Before
    public void resetLibraryAndAppearance() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
    }

    @Test
    public void readerProviderAndAppearancePanelAreAccessibleAndGated() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            long[] initial = awaitPresentedFrame(scenario);
            assertTrue(initial[OctavoSurfaceView.STATE_PAGE_COUNT] >= 4);
            assertEquals(1, initial[OctavoSurfaceView.STATE_CHROME_VISIBLE]);

            SemanticEvidence semantics = semanticEvidence(scenario);
            assertSemanticPacket(semantics);

            assertChromeAccessibilityTree(scenario, true);

            long[] chromeHidden = clickContent(scenario);
            assertEquals(0,
                         chromeHidden[
                             OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertEquals(
                initial[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                chromeHidden[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertSemanticPageStateEquals(initial, chromeHidden);
            assertEquals(
                initial[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT],
                chromeHidden[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
            assertEquals(
                initial[OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT],
                chromeHidden[
                    OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT]);
            assertPageChromeState(scenario, false);
            assertChromeAccessibilityTree(scenario, false);
            try (ReaderNodes nodes = readerNodes(scenario)) {
                assertReaderNodes(nodes, semantics);
            }
            assertKeyboardFocusOrder(scenario);
            assertContentSearchIsBounded(scenario);

            long[] returned = assertGatedAccessibilityNavigation(
                scenario, chromeHidden);
            assertSemanticPageStateEquals(initial, returned);

            long[] chromeRestored = clickContent(scenario);
            assertEquals(1,
                         chromeRestored[
                             OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertEquals(
                initial[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 2,
                chromeRestored[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertSemanticPageStateEquals(initial, chromeRestored);
            assertPageChromeState(scenario, true);
            assertChromeAccessibilityTree(scenario, true);

            assertAppearancePanel(scenario);
        }
    }

    private static void openFixture(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<Boolean> opened = new AtomicReference<>(false);
        scenario.onActivity(activity -> {
            assertTrue(activity.libraryVisibleForTesting());
            opened.set(activity.openFixtureForTesting());
        });
        assertTrue(opened.get());
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface)));
        assertNotNull(result.get());
        return result.get();
    }

    private static long[] state(
        ActivityScenario<OctavoActivity> scenario) {
        long[] result = surface(scenario).nativeStateForTesting();
        assertNotNull(result);
        assertEquals(OctavoSurfaceView.STATE_FIELD_COUNT, result.length);
        return result;
    }

    private static long[] awaitState(
        ActivityScenario<OctavoActivity> scenario,
        StateCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 120; ++attempt) {
            long[] snapshot = state(scenario);
            if (condition.matches(snapshot)) {
                return snapshot;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] awaitPresentedFrame(
        ActivityScenario<OctavoActivity> scenario) {
        return awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_RESUMED] == 1
                && snapshot[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                && snapshot[OctavoSurfaceView.STATE_WIDTH] > 0
                && snapshot[OctavoSurfaceView.STATE_HEIGHT] > 0
                && snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && snapshot[
                    OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING] == 0,
            "8vo did not present the fixture for accessibility inspection");
    }

    private static SemanticEvidence semanticEvidence(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<SemanticEvidence> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            assertNotNull(view);
            long[] packed = view.accessibilitySemanticSnapshotForTesting();
            assertNotNull(packed);
            int count = packed.length
                        >= OctavoReaderAccessibilityProvider
                            .SEMANTIC_SNAPSHOT_HEADER_SIZE
                ? (int)packed[OctavoReaderAccessibilityProvider
                                  .SEMANTIC_HEADER_COUNT]
                : 0;
            String[] names = new String[Math.max(count, 0)];
            String[] values = new String[Math.max(count, 0)];
            for (int index = 0; index < count; ++index) {
                names[index] =
                    view.accessibilitySemanticNameForTesting(index);
                values[index] =
                    view.accessibilitySemanticValueForTesting(index);
            }
            result.set(new SemanticEvidence(packed, names, values));
        });
        assertNotNull(result.get());
        return result.get();
    }

    private static void assertSemanticPacket(SemanticEvidence evidence) {
        long[] packed = evidence.packed;
        assertTrue(packed.length
                   >= OctavoReaderAccessibilityProvider
                       .SEMANTIC_SNAPSHOT_HEADER_SIZE);
        assertEquals(
            OctavoReaderAccessibilityProvider.SEMANTIC_SNAPSHOT_VERSION,
            packed[OctavoReaderAccessibilityProvider
                       .SEMANTIC_HEADER_VERSION]);
        assertEquals(
            OctavoReaderAccessibilityProvider.SEMANTIC_SNAPSHOT_STRIDE,
            packed[OctavoReaderAccessibilityProvider
                       .SEMANTIC_HEADER_STRIDE]);
        int count = (int)packed[OctavoReaderAccessibilityProvider
                                    .SEMANTIC_HEADER_COUNT];
        assertTrue(count > 0);
        assertTrue(count <= MAX_SEMANTIC_RECORDS);
        assertEquals(
            OctavoReaderAccessibilityProvider.SEMANTIC_SNAPSHOT_HEADER_SIZE
                + count
                    * OctavoReaderAccessibilityProvider
                        .SEMANTIC_SNAPSHOT_STRIDE,
            packed.length);
        assertEquals(count, evidence.names.length);
        assertEquals(count, evidence.values.length);

        Set<Long> stableIds = new HashSet<>();
        int previous = -1;
        int next = -1;
        int progress = -1;
        for (int index = 0; index < count; ++index) {
            int base = evidence.base(index);
            long stableId = packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_STABLE_ID];
            assertTrue(stableId != 0);
            assertTrue("ReaderView semantic IDs must be unique",
                       stableIds.add(stableId));
            assertNotNull(evidence.names[index]);
            assertNotNull(evidence.values[index]);
            assertTrue(evidence.names[index].length()
                       <= MAX_ACCESSIBILITY_TEXT);
            assertTrue(evidence.values[index].length()
                       <= MAX_ACCESSIBILITY_TEXT);

            long width = packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_WIDTH];
            long height = packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_HEIGHT];
            assertTrue(width >= 0);
            assertTrue(height >= 0);

            int control = (int)packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_CONTROL];
            if (control == SEMANTIC_CONTROL_PREVIOUS_PAGE) {
                assertEquals(-1, previous);
                previous = index;
            } else if (control == SEMANTIC_CONTROL_NEXT_PAGE) {
                assertEquals(-1, next);
                next = index;
            } else if (control == SEMANTIC_CONTROL_PROGRESS) {
                assertEquals(-1, progress);
                progress = index;
            }
        }
        evidence.previousIndex = previous;
        evidence.nextIndex = next;
        evidence.progressIndex = progress;
        assertTrue(previous >= 0);
        assertTrue(next >= 0);
        assertTrue(progress >= 0);
        assertSemanticControl(evidence,
                              previous,
                              SEMANTIC_CONTROL_PREVIOUS_PAGE,
                              SEMANTIC_ROLE_BUTTON,
                              false);
        assertSemanticControl(evidence,
                              next,
                              SEMANTIC_CONTROL_NEXT_PAGE,
                              SEMANTIC_ROLE_BUTTON,
                              true);
        assertSemanticControl(evidence,
                              progress,
                              SEMANTIC_CONTROL_PROGRESS,
                              SEMANTIC_ROLE_SLIDER,
                              false);
    }

    private static void assertSemanticControl(SemanticEvidence evidence,
                                              int index,
                                              int expectedControl,
                                              int expectedRole,
                                              boolean expectedEnabled) {
        int base = evidence.base(index);
        assertEquals(expectedControl,
                     evidence.packed[
                         base + OctavoReaderAccessibilityProvider
                             .SEMANTIC_RECORD_CONTROL]);
        assertEquals(expectedRole,
                     evidence.packed[
                         base + OctavoReaderAccessibilityProvider
                             .SEMANTIC_RECORD_ROLE]);
        long flags = evidence.packed[
            base + OctavoReaderAccessibilityProvider
                .SEMANTIC_RECORD_FLAGS];
        assertEquals(expectedEnabled,
                     (flags & SEMANTIC_FLAG_ENABLED) != 0);
        assertTrue((flags & SEMANTIC_FLAG_FOCUSABLE) != 0);
        assertFalse(evidence.names[index].trim().isEmpty());
        assertTrue(evidence.packed[
                       base + OctavoReaderAccessibilityProvider
                           .SEMANTIC_RECORD_WIDTH] > 0);
        assertTrue(evidence.packed[
                       base + OctavoReaderAccessibilityProvider
                           .SEMANTIC_RECORD_HEIGHT] > 0);
        if (expectedControl == SEMANTIC_CONTROL_PROGRESS) {
            long value = evidence.packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_RANGE_VALUE];
            long minimum = evidence.packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_RANGE_MIN];
            long maximum = evidence.packed[
                base + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_RANGE_MAX];
            assertTrue(maximum >= minimum);
            assertTrue(value >= minimum);
            assertTrue(value <= maximum);
        }
    }

    private static void assertChromeAccessibilityTree(
        ActivityScenario<OctavoActivity> scenario,
        boolean chromeVisible) {
        SystemClock.sleep(OctavoDesignTokens.MOTION_FAST_MS + 80L);
        scenario.onActivity(activity -> {
            OctavoSurfaceView owner =
                (OctavoSurfaceView)activity.findViewById(
                    R.id.octavo_surface);
            assertNotNull(owner);
            AccessibilityNodeProvider provider =
                owner.getAccessibilityNodeProvider();
            assertNotNull(provider);
            AccessibilityNodeInfo host =
                provider.createAccessibilityNodeInfo(
                    AccessibilityNodeProvider.HOST_VIEW_ID);
            AccessibilityNodeInfo page =
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider
                        .VIRTUAL_PAGE_CONTENT);
            AccessibilityNodeInfo previous =
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider
                        .VIRTUAL_PREVIOUS_PAGE);
            AccessibilityNodeInfo next =
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider
                        .VIRTUAL_NEXT_PAGE);
            AccessibilityNodeInfo progress =
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider
                        .VIRTUAL_PROGRESS_STATUS);
            assertNotNull(host);
            assertNotNull(page);
            assertEquals(chromeVisible ? 1 : 4, host.getChildCount());
            if (chromeVisible) {
                assertNull(previous);
                assertNull(next);
                assertNull(progress);
            } else {
                assertNotNull(previous);
                assertNotNull(next);
                assertNotNull(progress);
            }

            View topChrome =
                activity.findViewById(
                    R.id.octavo_reader_top_chrome);
            View bottomChrome =
                activity.findViewById(
                    R.id.octavo_reader_bottom_chrome);
            assertNotNull(topChrome);
            assertNotNull(bottomChrome);
            assertEquals(
                chromeVisible ? View.VISIBLE : View.INVISIBLE,
                topChrome.getVisibility());
            assertEquals(
                chromeVisible ? View.VISIBLE : View.INVISIBLE,
                bottomChrome.getVisibility());
            assertEquals(
                chromeVisible
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                topChrome.getImportantForAccessibility());
            assertEquals(
                chromeVisible
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                bottomChrome.getImportantForAccessibility());

            int[] controlIds = {
                R.id.octavo_reader_library,
                R.id.octavo_reader_appearance,
                R.id.octavo_reader_previous,
                R.id.octavo_reader_progress,
                R.id.octavo_reader_next,
            };
            int minimumTouchPixels = Math.round(
                OctavoDesignTokens.TOUCH_TARGET_DP
                    * owner.getResources().getDisplayMetrics().density);
            for (int controlId : controlIds) {
                View control = activity.findViewById(controlId);
                assertNotNull(control);
                assertEquals(chromeVisible, control.isShown());
                if (chromeVisible) {
                    assertTrue(control.isImportantForAccessibility());
                    assertNotNull(control.getContentDescription());
                    assertFalse(
                        control.getContentDescription().toString()
                            .trim().isEmpty());
                    if (controlId != R.id.octavo_reader_progress) {
                        assertTrue(control.isClickable());
                        assertTrue(control.isFocusable());
                        assertTrue(control.getWidth()
                                   >= minimumTouchPixels);
                        assertTrue(control.getHeight()
                                   >= minimumTouchPixels);
                    }
                }
            }

            host.recycle();
            page.recycle();
            if (previous != null) {
                previous.recycle();
            }
            if (next != null) {
                next.recycle();
            }
            if (progress != null) {
                progress.recycle();
            }
        });
    }

    private static ReaderNodes readerNodes(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<ReaderNodes> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            assertNotNull(view);
            AccessibilityNodeProvider provider =
                view.getAccessibilityNodeProvider();
            assertNotNull(provider);
            result.set(new ReaderNodes(
                provider.createAccessibilityNodeInfo(
                    AccessibilityNodeProvider.HOST_VIEW_ID),
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT),
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PREVIOUS_PAGE),
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_NEXT_PAGE),
                provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PROGRESS_STATUS),
                view.getWidth(),
                view.getHeight(),
                Math.round(OctavoDesignTokens.TOUCH_TARGET_DP
                           * view.getResources()
                               .getDisplayMetrics().density),
                activity.getPackageName()));
        });
        assertNotNull(result.get());
        return result.get();
    }

    private static void assertReaderNodes(ReaderNodes nodes,
                                          SemanticEvidence semantics) {
        assertEquals(1,
                     OctavoReaderAccessibilityProvider
                         .VIRTUAL_PAGE_CONTENT);
        assertEquals(2,
                     OctavoReaderAccessibilityProvider
                         .VIRTUAL_PREVIOUS_PAGE);
        assertEquals(3,
                     OctavoReaderAccessibilityProvider.VIRTUAL_NEXT_PAGE);
        assertEquals(4,
                     OctavoReaderAccessibilityProvider
                         .VIRTUAL_PROGRESS_STATUS);

        assertNotNull(nodes.host);
        assertNotNull(nodes.page);
        assertNotNull(nodes.previous);
        assertNotNull(nodes.next);
        assertNotNull(nodes.progress);
        assertEquals(4, nodes.host.getChildCount());
        assertEquals(nodes.packageName, nodes.host.getPackageName());
        assertTrue(nodes.host.isScrollable());
        assertTrue(hasAction(nodes.host,
                             AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));

        assertEquals("android.widget.TextView",
                     nodes.page.getClassName());
        assertTrue(nodes.page.isFocusable());
        assertTrue(nodes.page.isClickable());
        assertTrue(nodes.page.isScrollable());
        assertTrue(hasAction(nodes.page,
                             AccessibilityNodeInfo.ACTION_CLICK));
        assertTrue(hasAction(nodes.page,
                             AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
        assertFalse(hasAction(nodes.page,
                              AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD));
        assertTrue(hasAction(
            nodes.page,
            AccessibilityNodeInfo.AccessibilityAction
                .ACTION_SHOW_ON_SCREEN.getId()));
        assertNotNull(nodes.page.getText());
        assertTrue(nodes.page.getText().length() > 0);
        assertTrue(nodes.page.getText().length() <= 4096);
        assertTrue(nodes.page.getText().toString().contains("Chapter One"));
        assertNotNull(nodes.page.getHintText());
        assertTrue(nodes.page.getHintText().toString()
                       .contains("reading controls"));

        assertEquals("android.widget.Button",
                     nodes.previous.getClassName());
        assertEquals("android.widget.Button",
                     nodes.next.getClassName());
        assertEquals("android.widget.SeekBar",
                     nodes.progress.getClassName());
        assertFalse(nodes.previous.isEnabled());
        assertTrue(nodes.next.isEnabled());
        assertTrue(nodes.previous.isFocusable());
        assertTrue(nodes.next.isFocusable());
        assertTrue(nodes.progress.isFocusable());
        assertFalse(hasAction(nodes.previous,
                              AccessibilityNodeInfo.ACTION_CLICK));
        assertTrue(hasAction(nodes.next,
                             AccessibilityNodeInfo.ACTION_CLICK));
        assertTrue(hasAction(nodes.next,
                             AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
        assertFalse(hasAction(nodes.progress,
                              AccessibilityNodeInfo.ACTION_CLICK));

        assertNodeIncludesSemanticText(
            nodes.previous, semantics, semantics.previousIndex);
        assertNodeIncludesSemanticText(
            nodes.next, semantics, semantics.nextIndex);
        assertNodeIncludesSemanticText(
            nodes.progress, semantics, semantics.progressIndex);

        AccessibilityNodeInfo.RangeInfo range =
            nodes.progress.getRangeInfo();
        assertNotNull(range);
        int progressBase = semantics.base(semantics.progressIndex);
        assertEquals(
            (float)semantics.packed[
                progressBase + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_RANGE_MIN],
            range.getMin(),
            0.01f);
        assertEquals(
            (float)semantics.packed[
                progressBase + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_RANGE_MAX],
            range.getMax(),
            0.01f);
        assertEquals(
            (float)semantics.packed[
                progressBase + OctavoReaderAccessibilityProvider
                    .SEMANTIC_RECORD_RANGE_VALUE],
            range.getCurrent(),
            0.01f);

        assertBoundsInside(nodes.page,
                           nodes.width,
                           nodes.height,
                           "page content");
        assertMinimumTouchBounds(nodes.previous,
                                 nodes,
                                 "previous page");
        assertMinimumTouchBounds(nodes.next, nodes, "next page");
        assertBoundsInside(nodes.progress,
                           nodes.width,
                           nodes.height,
                           "reading progress");
    }

    private static void assertNodeIncludesSemanticText(
        AccessibilityNodeInfo node,
        SemanticEvidence semantics,
        int index) {
        String spoken = (text(node.getText()) + " "
                         + text(node.getContentDescription()))
            .toLowerCase(Locale.ROOT);
        String name = semantics.names[index].trim()
            .toLowerCase(Locale.ROOT);
        String value = semantics.values[index].trim()
            .toLowerCase(Locale.ROOT);
        assertFalse(name.isEmpty());
        assertTrue(spoken.contains(name));
        if (!value.isEmpty()) {
            assertTrue(spoken.contains(value));
        }
    }

    private static void assertBoundsInside(AccessibilityNodeInfo node,
                                           int width,
                                           int height,
                                           String label) {
        Rect bounds = new Rect();
        node.getBoundsInParent(bounds);
        assertTrue(label + " bounds must be non-empty", !bounds.isEmpty());
        assertTrue(label + " bounds start outside the owner",
                   bounds.left >= 0 && bounds.top >= 0);
        assertTrue(label + " bounds exceed the owner",
                   bounds.right <= width && bounds.bottom <= height);
    }

    private static void assertMinimumTouchBounds(
        AccessibilityNodeInfo node,
        ReaderNodes nodes,
        String label) {
        assertBoundsInside(node, nodes.width, nodes.height, label);
        Rect bounds = new Rect();
        node.getBoundsInParent(bounds);
        assertTrue(label + " must be at least 48dp wide; was "
                       + bounds.width() + "px",
                   bounds.width() >= nodes.minimumTouchPixels);
        assertTrue(label + " must be at least 48dp tall; was "
                       + bounds.height() + "px",
                   bounds.height() >= nodes.minimumTouchPixels);
    }

    private static void assertKeyboardFocusOrder(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            AccessibilityNodeProvider provider =
                view.getAccessibilityNodeProvider();
            int[] order = {
                OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                OctavoReaderAccessibilityProvider.VIRTUAL_PREVIOUS_PAGE,
                OctavoReaderAccessibilityProvider.VIRTUAL_NEXT_PAGE,
                OctavoReaderAccessibilityProvider.VIRTUAL_PROGRESS_STATUS
            };
            for (int index = 0; index < order.length; ++index) {
                assertEquals(index + 1, order[index]);
                AccessibilityNodeInfo expected =
                    provider.createAccessibilityNodeInfo(order[index]);
                assertNotNull(expected);
                assertTrue(hasAction(expected,
                                     AccessibilityNodeInfo.ACTION_FOCUS));
                assertTrue(provider.performAction(
                    order[index], AccessibilityNodeInfo.ACTION_FOCUS,
                    (Bundle)null));
                AccessibilityNodeInfo focused = provider.findFocus(
                    AccessibilityNodeInfo.FOCUS_INPUT);
                assertNotNull(focused);
                assertEquals(expected.getClassName(), focused.getClassName());
                assertEquals(text(expected.getText()),
                             text(focused.getText()));
                expected.recycle();
                focused.recycle();
            }
            assertTrue(provider.performAction(
                OctavoReaderAccessibilityProvider.VIRTUAL_PROGRESS_STATUS,
                AccessibilityNodeInfo.ACTION_CLEAR_FOCUS,
                (Bundle)null));
            assertNull(provider.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT));
        });
        assertDeclaredTraversalOrderFromAutomation(scenario);
    }

    private static void assertDeclaredTraversalOrderFromAutomation(
        ActivityScenario<OctavoActivity> scenario) {
        android.app.UiAutomation automation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
        scenario.onActivity(activity -> {
            View surface =
                activity.findViewById(R.id.octavo_surface);
            assertNotNull(surface);
            surface.sendAccessibilityEvent(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        });
        AccessibilityNodeInfo root = null;
        for (int attempt = 0; attempt < 40 && root == null; ++attempt) {
            root = automation.getRootInActiveWindow();
            if (root == null) {
                SystemClock.sleep(50);
            }
        }
        assertNotNull(root);
        AccessibilityNodeInfo[] order = {
            findAccessibilityNode(
                root, "Chapter One", "android.widget.TextView"),
            findAccessibilityNode(
                root, "Previous page", "android.widget.Button"),
            findAccessibilityNode(
                root, "Next page", "android.widget.Button"),
            findAccessibilityNode(
                root, "Octavo Android Port 6", "android.widget.SeekBar"),
        };
        try {
            for (int index = 0; index < order.length; ++index) {
                assertNotNull(
                    "UiAutomation could not inspect traversal node "
                        + index,
                    order[index]);
                AccessibilityNodeInfo after =
                    order[index].getTraversalAfter();
                AccessibilityNodeInfo before =
                    order[index].getTraversalBefore();
                if (index == 0) {
                    assertNull(after);
                } else {
                    assertEquals(order[index - 1], after);
                }
                if (index + 1 == order.length) {
                    assertNull(before);
                } else {
                    assertEquals(order[index + 1], before);
                }
                if (after != null) {
                    after.recycle();
                }
                if (before != null) {
                    before.recycle();
                }
            }
        } finally {
            for (AccessibilityNodeInfo node : order) {
                if (node != null) {
                    node.recycle();
                }
            }
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo findAccessibilityNode(
        AccessibilityNodeInfo root,
        String query,
        String className) {
        List<AccessibilityNodeInfo> matches =
            root.findAccessibilityNodeInfosByText(query);
        AccessibilityNodeInfo result = null;
        for (AccessibilityNodeInfo candidate : matches) {
            if (result == null
                && className.contentEquals(candidate.getClassName())) {
                result = candidate;
            } else {
                candidate.recycle();
            }
        }
        return result;
    }

    private static void assertContentSearchIsBounded(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            List<AccessibilityNodeInfo> matches =
                view.getAccessibilityNodeProvider()
                    .findAccessibilityNodeInfosByText(
                        "Chapter One",
                        AccessibilityNodeProvider.HOST_VIEW_ID);
            assertNotNull(matches);
            assertFalse(matches.isEmpty());
            assertTrue(matches.size() <= 4);
            for (AccessibilityNodeInfo match : matches) {
                assertTrue(text(match.getText()).contains("Chapter One"));
                match.recycle();
            }
        });
    }

    private static long[] clickContent(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            assertTrue(view.getAccessibilityNodeProvider().performAction(
                OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                AccessibilityNodeInfo.ACTION_CLICK,
                (Bundle)null));
            result.set(view.nativeStateForTesting());
        });
        assertNotNull(result.get());
        return result.get();
    }

    private static void assertPageChromeState(
        ActivityScenario<OctavoActivity> scenario,
        boolean visible) {
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            AccessibilityNodeInfo page =
                view.getAccessibilityNodeProvider()
                    .createAccessibilityNodeInfo(
                        OctavoReaderAccessibilityProvider
                            .VIRTUAL_PAGE_CONTENT);
            assertNotNull(page);
            assertNotNull(page.getStateDescription());
            assertTrue(page.getStateDescription().toString()
                           .contains(visible ? "visible" : "hidden"));
            page.recycle();
            assertEquals(visible, activity.chromeVisibleForTesting());
        });
    }

    private static long[] assertGatedAccessibilityNavigation(
        ActivityScenario<OctavoActivity> scenario,
        long[] before) {
        AtomicReference<Boolean> firstAccepted =
            new AtomicReference<>(false);
        AtomicReference<Boolean> gatedAccepted =
            new AtomicReference<>(true);
        AtomicReference<long[]> gated = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            AccessibilityNodeProvider provider =
                view.getAccessibilityNodeProvider();
            firstAccepted.set(provider.performAction(
                OctavoReaderAccessibilityProvider.VIRTUAL_NEXT_PAGE,
                AccessibilityNodeInfo.ACTION_CLICK,
                (Bundle)null));
            gatedAccepted.set(provider.performAction(
                OctavoReaderAccessibilityProvider.VIRTUAL_NEXT_PAGE,
                AccessibilityNodeInfo.ACTION_CLICK,
                (Bundle)null));
            gated.set(view.nativeStateForTesting());
        });
        assertTrue(firstAccepted.get());
        assertFalse(gatedAccepted.get());
        assertNotNull(gated.get());
        assertEquals(1,
                     gated.get()[
                         OctavoSurfaceView
                             .STATE_PAGE_MOVE_PRESENTATION_PENDING]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 1,
            gated.get()[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT],
            gated.get()[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT],
            gated.get()[
                OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT] + 1,
            gated.get()[
                OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT]);
        assertEquals(before[OctavoSurfaceView.STATE_PAGE_INDEX],
                     gated.get()[OctavoSurfaceView.STATE_PAGE_INDEX]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET],
            gated.get()[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);

        long[] nextPresented = awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_PAGE_INDEX]
                    == before[OctavoSurfaceView.STATE_PAGE_INDEX] + 1
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                    == before[
                           OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                       + 1,
            "Accessibility next page was not successfully presented");
        assertEquals(
            before[OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT] + 1,
            nextPresented[
                OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT]);
        assertPresentedOffsetInsidePage(nextPresented);

        AtomicReference<Boolean> previousAccepted =
            new AtomicReference<>(false);
        AtomicReference<long[]> previousPending = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            previousAccepted.set(
                view.getAccessibilityNodeProvider().performAction(
                    OctavoReaderAccessibilityProvider
                        .VIRTUAL_PREVIOUS_PAGE,
                    AccessibilityNodeInfo.ACTION_CLICK,
                    (Bundle)null));
            previousPending.set(view.nativeStateForTesting());
        });
        assertTrue(previousAccepted.get());
        assertEquals(
            1,
            previousPending.get()[
                OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 2,
            previousPending.get()[
                OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT] + 1,
            previousPending.get()[
                OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT] + 2,
            previousPending.get()[
                OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT]);

        long[] returned = awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_PAGE_INDEX]
                    == before[OctavoSurfaceView.STATE_PAGE_INDEX]
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                    == before[
                           OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                       + 2,
            "Accessibility previous page was not successfully presented");
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 2,
            returned[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT],
            returned[OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT]);
        assertEquals(
            before[OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT] + 2,
            returned[OctavoSurfaceView.STATE_ACCESSIBILITY_ACTION_COUNT]);
        assertPresentedOffsetInsidePage(returned);
        return returned;
    }

    private static void assertPresentedOffsetInsidePage(long[] snapshot) {
        long first = snapshot[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE];
        long onePast =
            snapshot[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE];
        long presented =
            snapshot[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
        assertTrue(onePast > first);
        assertTrue(presented >= first);
        assertTrue(presented < onePast);
    }

    private static void assertSemanticPageStateEquals(long[] expected,
                                                      long[] actual) {
        for (int field : SEMANTIC_PAGE_STATE_FIELDS) {
            assertEquals("Semantic page field " + field + " changed",
                         expected[field],
                         actual[field]);
        }
    }

    private static void assertAppearancePanel(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoAppearance> requested = new AtomicReference<>();
        scenario.onActivity(activity -> {
            activity.openAppearancePanelForTesting();
            OctavoAppearancePanel panel =
                activity.appearancePanelForTesting();
            assertNotNull(panel);
            View overlay = activity.findViewById(
                R.id.octavo_appearance_overlay);
            View topChrome = activity.findViewById(
                R.id.octavo_reader_top_chrome);
            View bottomChrome = activity.findViewById(
                R.id.octavo_reader_bottom_chrome);
            assertNotNull(overlay);
            assertNotNull(topChrome);
            assertNotNull(bottomChrome);
            assertTrue(overlay.getElevation() > topChrome.getElevation());
            assertTrue(overlay.getElevation() > bottomChrome.getElevation());
            OctavoAppearance defaults = OctavoAppearance.defaults();
            assertEquals(defaults, panel.presentedAppearanceForTesting());
            assertEquals(defaults, panel.requestedAppearanceForTesting());
            String globalNote = panel.globalDefaultsNoteForTesting()
                .getText().toString().toLowerCase(Locale.ROOT);
            assertTrue(globalNote.contains("global defaults"));
            assertTrue(globalNote.contains("successfully shown"));

            int minimumTouchPixels = Math.round(
                OctavoDesignTokens.TOUCH_TARGET_DP
                    * panel.getResources().getDisplayMetrics().density);
            assertEquals(OctavoAppearance.THEME_COUNT,
                         panel.themeGroupForTesting().getChildCount());
            assertEquals(OctavoAppearance.FONT_FAMILY_COUNT,
                         panel.fontFamilyGroupForTesting().getChildCount());
            assertEquals(OctavoAppearance.fontSizesSp().length,
                         panel.fontSizeGroupForTesting().getChildCount());
            assertEquals(OctavoAppearance.lineSpacingsPermille().length,
                         panel.lineSpacingGroupForTesting().getChildCount());
            assertEquals(OctavoAppearance.MARGINS_COUNT,
                         panel.marginGroupForTesting().getChildCount());
            assertEquals(OctavoAppearance.ALIGNMENT_COUNT,
                         panel.alignmentGroupForTesting().getChildCount());
            assertEquals(OctavoAppearance.PUBLISHER_COLORS_COUNT,
                         panel.publisherColorGroupForTesting()
                             .getChildCount());

            assertOptions(panel.themeGroupForTesting(),
                          minimumTouchPixels);
            assertOptions(panel.fontFamilyGroupForTesting(),
                          minimumTouchPixels);
            assertOptions(panel.fontSizeGroupForTesting(),
                          minimumTouchPixels);
            assertOptions(panel.lineSpacingGroupForTesting(),
                          minimumTouchPixels);
            assertOptions(panel.marginGroupForTesting(),
                          minimumTouchPixels);
            assertOptions(panel.alignmentGroupForTesting(),
                          minimumTouchPixels);
            assertOptions(panel.publisherColorGroupForTesting(),
                          minimumTouchPixels);

            for (int theme = 0;
                 theme < OctavoAppearance.THEME_COUNT;
                 ++theme) {
                assertNotNull(panel.themeOptionForTesting(theme));
            }
            for (int family = 0;
                 family < OctavoAppearance.FONT_FAMILY_COUNT;
                 ++family) {
                assertNotNull(panel.fontFamilyOptionForTesting(family));
            }
            for (int value : OctavoAppearance.fontSizesSp()) {
                assertNotNull(panel.fontSizeOptionForTesting(value));
            }
            for (int value : OctavoAppearance.lineSpacingsPermille()) {
                assertNotNull(panel.lineSpacingOptionForTesting(value));
            }
            for (int margins = 0;
                 margins < OctavoAppearance.MARGINS_COUNT;
                 ++margins) {
                assertNotNull(panel.marginOptionForTesting(margins));
            }
            for (int alignment = 0;
                 alignment < OctavoAppearance.ALIGNMENT_COUNT;
                 ++alignment) {
                assertNotNull(panel.alignmentOptionForTesting(alignment));
            }
            for (int policy = 0;
                 policy < OctavoAppearance.PUBLISHER_COLORS_COUNT;
                 ++policy) {
                assertNotNull(panel.publisherColorOptionForTesting(policy));
            }

            assertTrue(panel.dismissButtonForTesting().getMinWidth()
                       >= minimumTouchPixels);
            assertTrue(panel.dismissButtonForTesting().getMinHeight()
                       >= minimumTouchPixels);
            assertTrue(panel.reducedMotionSwitchForTesting()
                           .getMinHeight()
                       >= minimumTouchPixels);
            assertNotNull(panel.reducedMotionSwitchForTesting()
                              .getContentDescription());
            assertFalse(panel.reducedMotionSwitchForTesting().isChecked());

            ColorDrawable initialBackground =
                (ColorDrawable)panel.getBackground();
            assertEquals(
                OctavoDesignTokens.forAppearance(defaults).settingsSurface,
                initialBackground.getColor());

            panel.reducedMotionSwitchForTesting().performClick();
            panel.themeOptionForTesting(
                OctavoAppearance.THEME_WARM_DARK).performClick();
            OctavoAppearance candidate =
                panel.requestedAppearanceForTesting();
            assertEquals(OctavoAppearance.THEME_WARM_DARK,
                         candidate.themeId());
            assertTrue(candidate.reducedMotion());
            assertEquals(defaults, panel.presentedAppearanceForTesting());
            assertEquals(
                OctavoDesignTokens.forAppearance(defaults).settingsSurface,
                ((ColorDrawable)panel.getBackground()).getColor());
            requested.set(candidate);
        });
        assertNotNull(requested.get());

        long[] presented = awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_THEME]
                    == OctavoAppearance.THEME_WARM_DARK
                && snapshot[OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    == snapshot[
                           OctavoSurfaceView
                               .STATE_APPEARANCE_PRESENTED_GENERATION]
                && snapshot[
                    OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING] == 0,
            "The accumulated appearance-panel request was not presented");
        assertEquals(
            nativePaletteHash(
                OctavoDesignTokens.forAppearance(requested.get())
                    .nativeUi0Colors()),
            presented[OctavoSurfaceView.STATE_PALETTE_HASH]);

        scenario.onActivity(activity -> {
            activity.flushAppearancePersistenceForTesting();
            OctavoAppearancePanel panel =
                activity.appearancePanelForTesting();
            assertNotNull(panel);
            assertTrue("Appearance panel lost its initial focus",
                       panel.isFocused());
            assertEquals("Appearance panel did not open at its title",
                         0,
                         panel.getScrollY());
            assertEquals(requested.get(), activity.appearanceForTesting());
            assertEquals(requested.get(),
                         panel.presentedAppearanceForTesting());
            assertEquals(requested.get(),
                         panel.requestedAppearanceForTesting());
            assertEquals(requested.get(),
                         activity.appearanceStoreForTesting().current());
            assertTrue(panel.reducedMotionSwitchForTesting().isChecked());
            assertTrue(panel.themeOptionForTesting(
                            OctavoAppearance.THEME_WARM_DARK)
                           .isChecked());
            assertEquals(
                OctavoDesignTokens.forAppearance(requested.get())
                    .settingsSurface,
                ((ColorDrawable)panel.getBackground()).getColor());

            activity.closeAppearancePanelForTesting();
            assertNull(activity.appearancePanelForTesting());
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                         view.getImportantForAccessibility());
        });
        SystemClock.sleep(100);
        scenario.onActivity(activity -> {
            View settings =
                activity.findViewById(R.id.octavo_reader_appearance);
            assertNotNull(settings);
            assertTrue(
                "Closing appearance did not restore keyboard focus "
                    + "to its invoking control",
                settings.isFocused());
        });
    }

    private static void assertOptions(RadioGroup group,
                                      int minimumTouchPixels) {
        assertNotNull(group.getContentDescription());
        assertFalse(group.getContentDescription().toString().trim().isEmpty());
        for (int index = 0; index < group.getChildCount(); ++index) {
            assertTrue(group.getChildAt(index) instanceof RadioButton);
            RadioButton option = (RadioButton)group.getChildAt(index);
            assertFalse(option.getText().toString().trim().isEmpty());
            assertTrue(option.isClickable());
            assertTrue(option.isFocusable());
            assertTrue(option.getMinHeight() >= minimumTouchPixels);
        }
    }

    private static boolean hasAction(AccessibilityNodeInfo node,
                                     int actionId) {
        for (AccessibilityNodeInfo.AccessibilityAction action
                 : node.getActionList()) {
            if (action.getId() == actionId) {
                return true;
            }
        }
        return false;
    }

    private static long nativePaletteHash(int[] colors) {
        long hash = FNV_OFFSET_BASIS;
        for (int color : colors) {
            hash = (hash ^ Integer.toUnsignedLong(color)) * FNV_PRIME;
        }
        return hash;
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static final class SemanticEvidence {
        final long[] packed;
        final String[] names;
        final String[] values;
        int previousIndex = -1;
        int nextIndex = -1;
        int progressIndex = -1;

        SemanticEvidence(long[] packed,
                         String[] names,
                         String[] values) {
            this.packed = packed;
            this.names = names;
            this.values = values;
        }

        int base(int index) {
            return OctavoReaderAccessibilityProvider
                       .SEMANTIC_SNAPSHOT_HEADER_SIZE
                + index
                    * OctavoReaderAccessibilityProvider
                        .SEMANTIC_SNAPSHOT_STRIDE;
        }
    }

    private static final class ReaderNodes implements AutoCloseable {
        final AccessibilityNodeInfo host;
        final AccessibilityNodeInfo page;
        final AccessibilityNodeInfo previous;
        final AccessibilityNodeInfo next;
        final AccessibilityNodeInfo progress;
        final int width;
        final int height;
        final int minimumTouchPixels;
        final String packageName;

        ReaderNodes(AccessibilityNodeInfo host,
                    AccessibilityNodeInfo page,
                    AccessibilityNodeInfo previous,
                    AccessibilityNodeInfo next,
                    AccessibilityNodeInfo progress,
                    int width,
                    int height,
                    int minimumTouchPixels,
                    String packageName) {
            this.host = host;
            this.page = page;
            this.previous = previous;
            this.next = next;
            this.progress = progress;
            this.width = width;
            this.height = height;
            this.minimumTouchPixels = minimumTouchPixels;
            this.packageName = packageName;
        }

        @Override
        public void close() {
            recycle(host);
            recycle(page);
            recycle(previous);
            recycle(next);
            recycle(progress);
        }

        private static void recycle(AccessibilityNodeInfo node) {
            if (node != null) {
                node.recycle();
            }
        }
    }
}
