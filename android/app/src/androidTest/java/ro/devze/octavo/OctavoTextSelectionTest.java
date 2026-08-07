package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoTextSelectionTest {
    private static final long WAIT_MILLIS = 12_000;

    private interface SelectionCondition {
        boolean matches(OctavoSelection selection);
    }

    @Before
    public void clearDurableTestState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        ClipboardManager clipboard = (ClipboardManager)context
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.clearPrimaryClip();
        }
    }

    @Test
    public void selectionCopyFailureAndRecreationRemainBounded() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);

            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection selected = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.generation == value.presentedGeneration,
                "Accessible word selection was not presented");
            assertTrue(selected.endByte > selected.startByte);
            assertTrue(selected.startRowHeight > 0);
            assertTrue(selected.endRowHeight > 0);

            AtomicReference<String> selectedText = new AtomicReference<>();
            scenario.onActivity(activity -> selectedText.set(
                surface(activity).selectedTextForTesting()));
            assertNotNull(selectedText.get());
            assertFalse(selectedText.get().isEmpty());
            assertTrue(selectedText.get().getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length <= 4096);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                view.forceClipboardFailureForTesting(true);
                assertFalse(view.copySelectionForTesting());
            });
            OctavoSelection afterFailure = selection(scenario);
            assertTrue(afterFailure.active);
            assertFalse(afterFailure.pending);
            assertEquals(selected.startByte, afterFailure.startByte);
            assertEquals(selected.endByte, afterFailure.endByte);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                view.forceClipboardFailureForTesting(false);
                assertTrue(view.copySelectionForTesting());
            });
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Copied selection was not cleared after presentation");
            ClipboardManager clipboard = (ClipboardManager)
                ApplicationProvider.getApplicationContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard);
            ClipData clip = clipboard.getPrimaryClip();
            assertNotNull(clip);
            assertTrue(clip.getItemCount() > 0);
            assertEquals(selectedText.get(),
                         clip.getItemAt(0).coerceToText(
                             ApplicationProvider.getApplicationContext())
                             .toString());

            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Selection was not restored before recreation probe");
            scenario.recreate();
            awaitReaderReady(scenario);
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Activity recreation retained transient selected text");
        }
    }

    @Test
    public void handleMutationRollsBackOnPresentationFailureAndPageMoveClears() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection origin = awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Baseline selection was not presented");

            AtomicInteger update = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(8));
                float targetX = (origin.startX + origin.endX) / 2.0f;
                float targetY = origin.startY
                    - Math.max(origin.startRowHeight / 2.0f, 1.0f);
                update.set(view.updateSelectionForTesting(
                    OctavoNative.SELECTION_HANDLE_START,
                    targetX,
                    targetY));
            });
            assertTrue(update.get() > 0);
            assertEquals(
                OctavoNative.SELECTION_ACCEPTED,
                update.get() & 0xff);
            OctavoSelection rolledBack = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startByte == origin.startByte
                    && value.endByte == origin.endByte,
                "Failed selection presentation did not restore its prior range");
            assertTrue(rolledBack.failureCount > origin.failureCount);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(0));
                assertTrue(view.presentPreparedFrameForTesting());
            });
            awaitReaderReady(scenario);

            AtomicBoolean moved = new AtomicBoolean();
            scenario.onActivity(activity -> moved.set(
                surface(activity).movePageForAccessibility(1)));
            assertTrue(moved.get());
            awaitReaderReady(scenario);
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Page movement retained transient selected text");
        }
    }

    @Test
    public void virtualPagePublishesSelectCopyAndClearActions() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                AccessibilityNodeProvider provider =
                    view.getAccessibilityNodeProvider();
                assertNotNull(provider);
                AccessibilityNodeInfo page = provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT);
                assertNotNull(page);
                assertTrue(hasAction(page, R.id.octavo_action_select_text));
                assertTrue(provider.performAction(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                    R.id.octavo_action_select_text,
                    null));
                page.recycle();
            });
            awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Virtual-page Select text action was not presented");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                AccessibilityNodeProvider provider =
                    view.getAccessibilityNodeProvider();
                AccessibilityNodeInfo page = provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT);
                assertNotNull(page);
                assertTrue(hasAction(
                    page, R.id.octavo_action_copy_selected_text));
                assertTrue(hasAction(page, R.id.octavo_action_clear_selection));
                assertTrue(provider.performAction(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                    R.id.octavo_action_clear_selection,
                    null));
                page.recycle();
            });
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Virtual-page clear action retained the selection");
        }
    }

    @Test
    public void systemBackDismissesSelectionBeforeLeavingReader() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Selection was not presented before the Back probe");

            InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            scenario.onActivity(activity -> assertFalse(
                "Back left the reader while text was selected",
                activity.libraryVisibleForTesting()));
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Back did not dismiss the selected text");
            scenario.onActivity(activity -> assertFalse(
                activity.libraryVisibleForTesting()));
        }
    }

    @Test
    public void longPressUsesRenderedGeometryAndOutsideTapDismisses() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection geometry = awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Geometry seed selection was not presented");
            scenario.onActivity(activity -> assertTrue(
                surface(activity).clearSelectionForTesting()));
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Geometry seed selection was not cleared");

            /*
             * Exercise the trailing-caret resolution used by a press on the
             * right half of the last glyph, not only the middle of the word.
             */
            float x = geometry.endX - 1.0f;
            float y = geometry.startY
                - Math.max(geometry.startRowHeight / 2.0f, 1.0f);
            AtomicLong downTime = new AtomicLong();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long now = SystemClock.uptimeMillis();
                downTime.set(now);
                MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                assertTrue(view.dispatchTouchEvent(down));
                down.recycle();
            });
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long now = SystemClock.uptimeMillis();
                MotionEvent up = MotionEvent.obtain(
                    downTime.get(), now, MotionEvent.ACTION_UP, x, y, 0);
                assertTrue(view.dispatchTouchEvent(up));
                up.recycle();
            });
            OctavoSelection selected = awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Long press did not publish a rendered-text selection");
            assertEquals(geometry.startByte, selected.startByte);
            assertEquals(geometry.endByte, selected.endByte);

            AtomicLong tapTime = new AtomicLong();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long[] state = view.nativeStateForTesting();
                assertNotNull(state);
                float outsideX = state[OctavoSurfaceView.STATE_CONTENT_X]
                    + state[OctavoSurfaceView.STATE_CONTENT_WIDTH] - 1.0f;
                float outsideY = state[OctavoSurfaceView.STATE_CONTENT_Y]
                    + state[OctavoSurfaceView.STATE_CONTENT_HEIGHT] - 1.0f;
                long now = SystemClock.uptimeMillis();
                tapTime.set(now);
                MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN,
                    outsideX, outsideY, 0);
                assertTrue(view.dispatchTouchEvent(down));
                down.recycle();
                MotionEvent up = MotionEvent.obtain(
                    now, now + 1, MotionEvent.ACTION_UP,
                    outsideX, outsideY, 0);
                assertTrue(view.dispatchTouchEvent(up));
                up.recycle();
            });
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Tap outside the handles did not dismiss the selection");
            assertTrue(tapTime.get() > 0);
        }
    }

    private static boolean hasAction(AccessibilityNodeInfo node, int id) {
        for (AccessibilityNodeInfo.AccessibilityAction action
                 : node.getActionList()) {
            if (action.getId() == id) {
                return true;
            }
        }
        return false;
    }

    private static void openFixture(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicBoolean opened = new AtomicBoolean();
        scenario.onActivity(activity -> opened.set(
            activity.openFixtureForTesting()));
        assertTrue(opened.get());
    }

    private static OctavoSurfaceView surface(OctavoActivity activity) {
        OctavoSurfaceView result = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        assertNotNull(result);
        return result;
    }

    private static void awaitReaderReady(
        ActivityScenario<OctavoActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MILLIS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<long[]> state = new AtomicReference<>();
            scenario.onActivity(activity -> state.set(
                surface(activity).nativeStateForTesting()));
            long[] value = state.get();
            if (value != null
                && value.length == OctavoSurfaceView.STATE_FIELD_COUNT
                && value[OctavoSurfaceView.STATE_RESUMED] == 1
                && value[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                && value[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                && value[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && value[OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING] == 0) {
                return;
            }
            SystemClock.sleep(25);
        }
        fail("Reader did not reach a stable presented frame");
    }

    private static OctavoSelection awaitSelection(
        ActivityScenario<OctavoActivity> scenario,
        SelectionCondition condition,
        String failure) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MILLIS;
        while (SystemClock.uptimeMillis() < deadline) {
            OctavoSelection value = selection(scenario);
            if (value != null && condition.matches(value)) {
                return value;
            }
            SystemClock.sleep(25);
        }
        fail(failure);
        return null;
    }

    private static OctavoSelection selection(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> packet = new AtomicReference<>();
        scenario.onActivity(activity -> packet.set(
            surface(activity).selectionPacketForTesting()));
        OctavoSelection result = OctavoSelection.fromNative(packet.get());
        assertNotNull(result);
        return result;
    }
}
