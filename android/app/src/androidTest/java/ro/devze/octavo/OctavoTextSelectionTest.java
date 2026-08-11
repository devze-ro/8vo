package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

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
        OctavoLibrarySyncStore.clearForTesting(context);
        OctavoBookTransferStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
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
    public void selectionContinuesForwardAndBackwardAcrossPresentedPages() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection origin = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startVisible && value.endVisible,
                "Baseline selection did not expose both page-local handles");
            long[] originState = state(scenario);

            AtomicInteger forward = new AtomicInteger();
            AtomicInteger gated = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                forward.set(view.extendSelectionAcrossPageForTesting(
                    OctavoNative.SELECTION_HANDLE_END, 1, origin.endX));
                gated.set(view.extendSelectionAcrossPageForTesting(
                    OctavoNative.SELECTION_HANDLE_END, 1, origin.endX));
            });
            assertEquals(
                OctavoNative.SELECTION_ACCEPTED,
                forward.get() & 0xff);
            assertEquals(OctavoNative.SELECTION_BUSY, gated.get());

            OctavoSelection forwardSelection = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startByte == origin.startByte
                    && value.endByte > origin.endByte
                    && !value.startVisible && value.endVisible,
                "Forward page continuation did not retain the off-page anchor");
            long[] forwardState = state(scenario);
            assertEquals(
                originState[OctavoSurfaceView.STATE_SPINE_INDEX],
                forwardState[OctavoSurfaceView.STATE_SPINE_INDEX]);
            assertTrue(
                forwardState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                    > originState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]);

            AtomicReference<String> crossPageText = new AtomicReference<>();
            scenario.onActivity(activity -> crossPageText.set(
                surface(activity).selectedTextForTesting()));
            assertNotNull(crossPageText.get());
            assertTrue(crossPageText.get().getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length
                > origin.endByte - origin.startByte);

            AtomicInteger backward = new AtomicInteger();
            scenario.onActivity(activity -> backward.set(
                surface(activity).extendSelectionAcrossPageForTesting(
                    OctavoNative.SELECTION_HANDLE_END,
                    -1,
                    forwardSelection.endX)));
            assertEquals(
                OctavoNative.SELECTION_ACCEPTED,
                backward.get() & 0xff);
            OctavoSelection contracted = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startByte == origin.startByte
                    && value.endByte < forwardSelection.endByte
                    && value.startVisible && value.endVisible,
                "Backward page continuation did not restore visible endpoints");
            assertTrue(contracted.endByte > contracted.startByte);
            long[] restoredState = state(scenario);
            assertEquals(
                originState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE],
                restoredState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]);
            assertEquals(
                originState[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE],
                restoredState[
                    OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE]);

            AtomicInteger reversed = new AtomicInteger();
            scenario.onActivity(activity -> reversed.set(
                surface(activity).extendSelectionAcrossPageForTesting(
                    OctavoNative.SELECTION_HANDLE_START,
                    1,
                    contracted.startX)));
            assertEquals(
                OctavoNative.SELECTION_ACCEPTED,
                reversed.get() & 0xff);
            assertEquals(
                OctavoNative.SELECTION_HANDLE_END,
                (reversed.get() >>> OctavoNative.SELECTION_HANDLE_SHIFT)
                    & 0xff);
            awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startByte == contracted.endByte
                    && value.endByte > contracted.endByte
                    && !value.startVisible && value.endVisible,
                "Cross-page handle crossing did not reverse the active handle");
        }
    }

    @Test
    public void failedCrossPagePresentationRestoresExactPageAndSelection() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection origin = awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Rollback selection seed was not presented");
            long[] originState = state(scenario);

            AtomicInteger extension = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(8));
                extension.set(view.extendSelectionAcrossPageForTesting(
                    OctavoNative.SELECTION_HANDLE_END, 1, origin.endX));
            });
            assertEquals(
                OctavoNative.SELECTION_ACCEPTED,
                extension.get() & 0xff);
            OctavoSelection restored = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startByte == origin.startByte
                    && value.endByte == origin.endByte,
                "Failed cross-page presentation did not restore its range");
            assertTrue(restored.failureCount > origin.failureCount);
            long[] restoredState = state(scenario);
            assertEquals(
                originState[OctavoSurfaceView.STATE_SPINE_INDEX],
                restoredState[OctavoSurfaceView.STATE_SPINE_INDEX]);
            assertEquals(
                originState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE],
                restoredState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]);
            assertEquals(
                originState[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE],
                restoredState[
                    OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE]);
            assertEquals(
                0,
                restoredState[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(0));
                assertTrue(view.presentPreparedFrameForTesting());
            });
            awaitReaderReady(scenario);
        }
    }

    @Test
    public void heldHandleBeyondContentEdgeTurnsThePage() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection origin = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.endVisible,
                "Edge-dwell selection seed was not presented");
            long[] originState = state(scenario);
            float outsideY = originState[OctavoSurfaceView.STATE_CONTENT_Y]
                + originState[OctavoSurfaceView.STATE_CONTENT_HEIGHT] + 1.0f;
            AtomicLong downTime = new AtomicLong();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long now = SystemClock.uptimeMillis();
                downTime.set(now);
                MotionEvent down = MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_DOWN,
                    origin.endX,
                    origin.endY,
                    0);
                assertTrue(view.dispatchTouchEvent(down));
                down.recycle();
                MotionEvent move = MotionEvent.obtain(
                    now,
                    now + 1,
                    MotionEvent.ACTION_MOVE,
                    origin.endX,
                    outsideY,
                    0);
                assertTrue(view.dispatchTouchEvent(move));
                move.recycle();
            });

            OctavoSelection extended = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.startByte == origin.startByte
                    && value.endByte > origin.endByte
                    && !value.startVisible && value.endVisible,
                "The held selection handle did not continue to the next page");
            long[] extendedState = state(scenario);
            assertEquals(
                originState[OctavoSurfaceView.STATE_SPINE_INDEX],
                extendedState[OctavoSurfaceView.STATE_SPINE_INDEX]);
            assertTrue(
                extendedState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                    > originState[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]);

            OctavoSelection repeated = awaitSelection(
                scenario,
                value -> {
                    long[] current = state(scenario);
                    return value.active && !value.pending
                        && value.startByte == origin.startByte
                        && value.endByte > extended.endByte
                        && current[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                            > extendedState[
                                OctavoSurfaceView.STATE_PAGE_FIRST_BYTE];
                },
                "The held selection handle did not repeat after presentation");
            assertFalse(repeated.startVisible);
            assertTrue(repeated.endVisible);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long now = SystemClock.uptimeMillis();
                MotionEvent up = MotionEvent.obtain(
                    downTime.get(),
                    now,
                    MotionEvent.ACTION_UP,
                    repeated.endX,
                    repeated.endY,
                    0);
                assertTrue(view.dispatchTouchEvent(up));
                up.recycle();
            });
        }
    }

    @Test
    public void handleDragMagnifierTracksAndDismissesAtLifecycleBoundaries() {
        assumeTrue(android.os.Build.VERSION.SDK_INT
            >= android.os.Build.VERSION_CODES.P);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection selection = awaitSelection(
                scenario,
                value -> value.active && !value.pending && value.endVisible,
                "Magnifier selection seed was not presented");

            AtomicLong downTime = new AtomicLong();
            AtomicInteger showsBeforeBurst = new AtomicInteger();
            AtomicInteger updatesBeforeBurst = new AtomicInteger();
            AtomicReference<float[]> sourceBeforeBurst =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long now = SystemClock.uptimeMillis();
                downTime.set(now);
                MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN,
                    selection.endX, selection.endY, 0);
                assertTrue(view.dispatchTouchEvent(down));
                down.recycle();
                assertTrue(view.selectionMagnifierVisibleForTesting());
                assertTrue(view.selectionMagnifierShowCountForTesting() >= 1);
                assertEquals(
                    selection.endY - selection.endRowHeight / 2.0f,
                    view.selectionMagnifierSourceYForTesting(),
                    0.5f);
                showsBeforeBurst.set(
                    view.selectionMagnifierShowCountForTesting());
                updatesBeforeBurst.set(
                    view.selectionMagnifierUpdateCountForTesting());
                sourceBeforeBurst.set(new float[] {
                    view.selectionMagnifierSourceXForTesting(),
                    view.selectionMagnifierSourceYForTesting()
                });
                if (android.os.Build.VERSION.SDK_INT
                    >= android.os.Build.VERSION_CODES.Q) {
                    assertTrue(Math.abs(
                        view.selectionMagnifierDisplayYForTesting()
                            - selection.endY)
                        >= view.selectionMagnifierClearanceForTesting());
                }

                long[] readerState = view.nativeStateForTesting();
                assertNotNull(readerState);
                float targetX = readerState[
                    OctavoSurfaceView.STATE_CONTENT_X]
                    + readerState[OctavoSurfaceView.STATE_CONTENT_WIDTH]
                        / 2.0f;
                float targetY = readerState[
                    OctavoSurfaceView.STATE_CONTENT_Y]
                    + readerState[OctavoSurfaceView.STATE_CONTENT_HEIGHT]
                        / 2.0f;
                for (int index = 0; index < 24; index += 1) {
                    MotionEvent move = MotionEvent.obtain(
                        now,
                        now + index + 1,
                        MotionEvent.ACTION_MOVE,
                        targetX,
                        targetY,
                        0);
                    assertTrue(view.dispatchTouchEvent(move));
                    move.recycle();
                }
                assertTrue(view.selectionMagnifierVisibleForTesting());
                assertEquals(
                    showsBeforeBurst.get(),
                    view.selectionMagnifierShowCountForTesting());
            });
            awaitSelection(
                scenario,
                value -> value.active && !value.pending,
                "Burst drag selection was not presented");
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.selectionMagnifierVisibleForTesting());
                int refreshes =
                    view.selectionMagnifierShowCountForTesting()
                        - showsBeforeBurst.get()
                    + view.selectionMagnifierUpdateCountForTesting()
                        - updatesBeforeBurst.get();
                assertEquals(1, refreshes);
                float[] beforeSource = sourceBeforeBurst.get();
                assertNotNull(beforeSource);
                assertTrue(
                    Math.abs(view.selectionMagnifierSourceXForTesting()
                        - beforeSource[0]) >= 0.5f
                    || Math.abs(view.selectionMagnifierSourceYForTesting()
                        - beforeSource[1]) >= 0.5f);
                OctavoSelection presented = OctavoSelection.fromNative(
                    view.selectionPacketForTesting());
                assertNotNull(presented);
                assertTrue(presented.endVisible);
                assertEquals(
                    presented.endX,
                    view.selectionMagnifierSourceXForTesting(),
                    0.5f);
                assertEquals(
                    presented.endY - presented.endRowHeight / 2.0f,
                    view.selectionMagnifierSourceYForTesting(),
                    0.5f);

                long now = SystemClock.uptimeMillis();
                MotionEvent cancel = MotionEvent.obtain(
                    downTime.get(), now, MotionEvent.ACTION_CANCEL,
                    selection.endX, selection.endY, 0);
                assertTrue(view.dispatchTouchEvent(cancel));
                cancel.recycle();
                assertFalse(view.selectionMagnifierVisibleForTesting());
            });

            OctavoSelection retained = awaitSelection(
                scenario,
                value -> value.active && !value.pending && value.endVisible,
                "Cancelled magnifier drag did not retain the selection");
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN,
                    retained.endX, retained.endY, 0);
                assertTrue(view.dispatchTouchEvent(down));
                down.recycle();
                assertTrue(view.selectionMagnifierVisibleForTesting());
                view.replaceNativeSurfaceForTesting();
                assertFalse(view.selectionMagnifierVisibleForTesting());
            });
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).clearSelectionForTesting()));
            awaitSelection(
                scenario,
                value -> !value.active && !value.pending,
                "Magnifier lifecycle test did not clear its selection");
        }
    }

    @Test
    public void chapterBoundaryKeepsTheLastPresentedSelection() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            long[] lastPage = moveToLastPageOfCurrentSpine(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection origin = awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && value.endVisible,
                "Chapter-boundary selection seed was not presented");

            AtomicInteger boundary = new AtomicInteger();
            scenario.onActivity(activity -> boundary.set(
                surface(activity).extendSelectionAcrossPageForTesting(
                    OctavoNative.SELECTION_HANDLE_END, 1, origin.endX)));
            assertEquals(OctavoNative.SELECTION_BOUNDARY, boundary.get());
            OctavoSelection retained = selection(scenario);
            assertTrue(retained.active);
            assertFalse(retained.pending);
            assertEquals(origin.startByte, retained.startByte);
            assertEquals(origin.endByte, retained.endByte);
            long[] after = state(scenario);
            assertEquals(
                lastPage[OctavoSurfaceView.STATE_SPINE_INDEX],
                after[OctavoSurfaceView.STATE_SPINE_INDEX]);
            assertEquals(
                lastPage[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE],
                after[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]);
            assertEquals(
                0,
                after[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]);
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
                assertTrue(hasAction(
                    page, R.id.octavo_action_extend_selection_previous));
                assertTrue(hasAction(
                    page, R.id.octavo_action_extend_selection_next));
                assertTrue(provider.performAction(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                    R.id.octavo_action_extend_selection_next,
                    null));
                page.recycle();
            });
            awaitSelection(
                scenario,
                value -> value.active && !value.pending
                    && !value.startVisible && value.endVisible,
                "Virtual-page extension did not continue onto the next page");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                AccessibilityNodeProvider provider =
                    view.getAccessibilityNodeProvider();
                AccessibilityNodeInfo page = provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT);
                assertNotNull(page);
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

    private static long[] moveToLastPageOfCurrentSpine(
        ActivityScenario<OctavoActivity> scenario) {
        long[] initial = state(scenario);
        long spine = initial[OctavoSurfaceView.STATE_SPINE_INDEX];
        long[] previous = initial;
        long declaredPageCount =
            initial[OctavoSurfaceView.STATE_PAGE_COUNT];
        assertTrue(declaredPageCount > 0 && declaredPageCount <= 512);
        for (int attempt = 0; attempt <= declaredPageCount; ++attempt) {
            AtomicBoolean moved = new AtomicBoolean();
            scenario.onActivity(activity -> moved.set(
                surface(activity).movePageForAccessibility(1)));
            if (!moved.get()) {
                return previous;
            }
            awaitReaderReady(scenario);
            long[] current = state(scenario);
            if (current[OctavoSurfaceView.STATE_SPINE_INDEX] != spine) {
                AtomicBoolean restored = new AtomicBoolean();
                scenario.onActivity(activity -> restored.set(
                    surface(activity).movePageForAccessibility(-1)));
                assertTrue(restored.get());
                awaitReaderReady(scenario);
                long[] result = state(scenario);
                assertEquals(
                    spine,
                    result[OctavoSurfaceView.STATE_SPINE_INDEX]);
                return result;
            }
            previous = current;
        }
        fail("Fixture spine exceeded its Reader0-reported page bound");
        return null;
    }

    private static long[] state(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).nativeStateForTesting()));
        assertNotNull(result.get());
        assertEquals(OctavoSurfaceView.STATE_FIELD_COUNT, result.get().length);
        return result.get();
    }
}
