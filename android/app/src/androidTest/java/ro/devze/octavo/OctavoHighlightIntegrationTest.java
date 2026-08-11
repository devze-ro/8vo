package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoHighlightIntegrationTest {
    private static final long WAIT_MILLIS = 12_000;

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    @Before
    public void clearDurableState() {
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
    }

    @Test
    public void selectionCreatesRendersRecolorsListsRestartsAndRemoves()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection selected = awaitSelection(
                scenario, true,
                "Accessible selection was not presented");

            AtomicReference<long[]> range = new AtomicReference<>();
            AtomicReference<String> selectedText = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                range.set(view.selectedRangeForTesting());
                selectedText.set(view.selectedTextForTesting());
                assertValidRange(range.get());
                assertEquals(selected.startByte, range.get()[3]);
                assertEquals(selected.endByte, range.get()[4]);
                assertHighlightActions(view);
                AccessibilityNodeProvider provider =
                    view.getAccessibilityNodeProvider();
                assertNotNull(provider);
                assertTrue(provider.performAction(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                    R.id.octavo_action_highlight_yellow,
                    null));
            });

            awaitActivity(
                scenario,
                activity -> {
                    List<OctavoAnnotationStore.Highlight> highlights =
                        activity.annotationStoreForTesting().highlights(
                            activity.activeBookKeyForTesting());
                    return highlights.size() == 1
                        && highlights.get(0).color
                            == OctavoAnnotationStore.HighlightColor.YELLOW
                        && highlightPresented(surface(activity), 1)
                        && !selection(surface(activity)).active;
                },
                "The durable yellow highlight was not presented");
            scenario.onActivity(activity -> {
                OctavoAnnotationStore.Highlight highlight =
                    activity.annotationStoreForTesting().highlights(
                        activity.activeBookKeyForTesting()).get(0);
                assertEquals(range.get()[2], highlight.spineIndex);
                assertEquals(range.get()[3], highlight.byteStart);
                assertEquals(range.get()[4], highlight.byteEnd);
                assertEquals(normalizedExcerpt(selectedText.get()),
                             highlight.excerpt);
                assertHighlightSnapshot(
                    surface(activity).highlightSnapshotForTesting(),
                    range.get(), 0);
                activity.openBookmarksPanelForTesting();
            });
            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .firstHighlightColorForTesting(1) != null,
                "The highlight workspace row was not available");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertEquals(1, panel.highlightsForTesting().size());
                assertNotNull(panel.firstHighlightGoToForTesting());
                assertNotNull(panel.firstHighlightRemoveForTesting());
                assertEquals("Set highlight color to Pink",
                    panel.firstHighlightColorForTesting(1)
                        .getContentDescription().toString());
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                    panel.statusForTesting().getAccessibilityLiveRegion());
                panel.firstHighlightColorForTesting(1).performClick();
            });
            awaitActivity(
                scenario,
                activity -> activity.annotationStoreForTesting().highlights(
                        activity.activeBookKeyForTesting()).get(0).color
                        == OctavoAnnotationStore.HighlightColor.PINK
                    && highlightPresented(surface(activity), 1),
                "The durable pink recolor was not presented");
            scenario.onActivity(OctavoActivity::closeBookmarksPanelForTesting);

            Bitmap pinkFrame = copyFrame(surface(scenario));
            int expectedPink = OctavoDesignTokens.forAppearance(
                OctavoAppearance.defaults()).annotationHighlightColor(
                    OctavoAnnotationStore.HighlightColor.PINK);
            assertTrue("The named pink highlight was absent from the frame",
                       countPixelsNear(pinkFrame, expectedPink, 6) > 20);
            pinkFrame.recycle();

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals(1, activity.annotationStoreForTesting()
                    .highlights(activity.activeBookKeyForTesting()).size());
                assertHighlightSnapshot(
                    surface(activity).highlightSnapshotForTesting(),
                    range.get(), 1);
                activity.openBookmarksPanelForTesting();
            });
            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .firstHighlightRemoveForTesting() != null,
                "The restarted highlight row was not available");
            scenario.onActivity(activity -> activity
                .bookmarksPanelForTesting()
                .firstHighlightRemoveForTesting().performClick());
            awaitActivity(
                scenario,
                activity -> activity.annotationStoreForTesting().highlights(
                        activity.activeBookKeyForTesting()).isEmpty()
                    && highlightPresented(surface(activity), 0),
                "The durable highlight removal was not presented");
        }
    }

    @Test
    public void storageFailureKeepsSelectionAndFrameFailureRecoversOnReopen() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            OctavoSelection selected = awaitSelection(
                scenario, true, "Selection was not presented");
            AtomicReference<long[]> range = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                range.set(view.selectedRangeForTesting());
                activity.annotationStoreForTesting()
                    .failNextPublishForTesting();
                assertFalse(view.highlightSelectionForAccessibility(
                    OctavoAnnotationStore.HighlightColor.BLUE));
                assertTrue(activity.annotationStoreForTesting().highlights(
                    activity.activeBookKeyForTesting()).isEmpty());
                assertEquals(0, view.highlightSnapshotForTesting()[2]);
            });
            OctavoSelection retained = selection(scenario);
            assertTrue(retained.active);
            assertFalse(retained.pending);
            assertEquals(selected.startByte, retained.startByte);
            assertEquals(selected.endByte, retained.endByte);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(8));
                assertTrue(view.highlightSelectionForAccessibility(
                    OctavoAnnotationStore.HighlightColor.BLUE));
            });
            awaitActivity(
                scenario,
                activity -> activity.annotationStoreForTesting().highlights(
                        activity.activeBookKeyForTesting()).size() == 1
                    && surface(activity)
                        .presentationFailureNotifiedForTesting(),
                "The durable highlight did not expose presentation failure");
            scenario.onActivity(activity -> {
                OctavoSelection current = selection(surface(activity));
                assertTrue(current.active);
                assertFalse(current.pending);
                long[] snapshot =
                    surface(activity).highlightSnapshotForTesting();
                assertTrue(snapshot[3] > snapshot[4]);
                assertEquals(1, snapshot[5]);
                assertTrue(activity.lastOpenErrorForTesting()
                    .contains("Highlight saved"));
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals(1, activity.annotationStoreForTesting()
                    .highlights(activity.activeBookKeyForTesting()).size());
                assertHighlightSnapshot(
                    surface(activity).highlightSnapshotForTesting(),
                    range.get(), 2);
                OctavoSelection current = selection(surface(activity));
                assertFalse(current.active);
                assertFalse(current.pending);
            });
        }
    }

    @Test
    public void queuedProjectionDrainsAfterTheCurrentHighlightPresents() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            awaitSelection(scenario, true, "Selection was not presented");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long[] range = view.selectedRangeForTesting();
                assertValidRange(range);
                String digest = activity.activeBookKeyForTesting();
                OctavoAnnotationStore.Highlight first =
                    new OctavoAnnotationStore.Highlight(
                        "00000000000000000000000000000001",
                        digest,
                        range[2],
                        range[3],
                        range[4],
                        OctavoAnnotationStore.HighlightColor.YELLOW,
                        "first",
                        false);
                OctavoAnnotationStore.Highlight second =
                    new OctavoAnnotationStore.Highlight(
                        "00000000000000000000000000000002",
                        digest,
                        range[2],
                        range[3],
                        range[4],
                        OctavoAnnotationStore.HighlightColor.BLUE,
                        "second",
                        false);
                assertTrue(view.forcePresentFailuresForTesting(1));
                assertTrue(view.replaceHighlights(
                    Arrays.asList(first), false, null));
                assertTrue(view.replaceHighlights(
                    Arrays.asList(first, second), false, null));
            });

            awaitActivity(
                scenario,
                activity -> highlightPresented(surface(activity), 2),
                "The queued highlight projection did not present");
            scenario.onActivity(activity -> {
                long[] snapshot =
                    surface(activity).highlightSnapshotForTesting();
                assertNotNull(snapshot);
                assertEquals(15, snapshot.length);
                assertEquals(2, snapshot[2]);
                assertEquals(snapshot[3], snapshot[4]);
                assertEquals(0, snapshot[5]);
            });
        }
    }

    private static void assertHighlightActions(OctavoSurfaceView view) {
        AccessibilityNodeInfo page = view.getAccessibilityNodeProvider()
            .createAccessibilityNodeInfo(
                OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT);
        assertNotNull(page);
        assertTrue(hasAction(page, R.id.octavo_action_highlight_yellow));
        assertTrue(hasAction(page, R.id.octavo_action_highlight_pink));
        assertTrue(hasAction(page, R.id.octavo_action_highlight_blue));
        assertTrue(hasAction(page, R.id.octavo_action_highlight_orange));
        page.recycle();
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

    private static boolean highlightPresented(OctavoSurfaceView view,
                                              int count) {
        long[] snapshot = view.highlightSnapshotForTesting();
        return snapshot != null && snapshot.length >= 7
            && snapshot[2] == count && snapshot[3] == snapshot[4]
            && snapshot[5] == 0;
    }

    private static void assertHighlightSnapshot(long[] snapshot,
                                                long[] range,
                                                int color) {
        assertNotNull(snapshot);
        assertEquals(11, snapshot.length);
        assertEquals(1, snapshot[0]);
        assertEquals(1, snapshot[2]);
        assertEquals(snapshot[3], snapshot[4]);
        assertEquals(0, snapshot[5]);
        assertEquals(4, snapshot[6]);
        assertEquals(range[2], snapshot[7]);
        assertEquals(range[3], snapshot[8]);
        assertEquals(range[4], snapshot[9]);
        assertEquals(color, snapshot[10]);
    }

    private static String normalizedExcerpt(String value) {
        String normalized = value == null
            ? "" : value.replaceAll("\\s+", " ").trim();
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 512) {
            return normalized;
        }
        int end = normalized.length();
        while (end > 0 && normalized.substring(0, end)
                .getBytes(StandardCharsets.UTF_8).length > 512) {
            end -= 1;
        }
        return normalized.substring(0, end);
    }

    private static void assertValidRange(long[] range) {
        assertNotNull(range);
        assertEquals(5, range.length);
        assertEquals(1, range[0]);
        assertEquals(5, range[1]);
        assertTrue(range[2] >= 0);
        assertTrue(range[3] >= 0);
        assertTrue(range[4] > range[3]);
    }

    private static OctavoSelection awaitSelection(
        ActivityScenario<OctavoActivity> scenario,
        boolean active,
        String failureMessage) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSelection value = selection(surface(activity));
                return value != null && value.active == active
                    && !value.pending;
            },
            failureMessage);
        AtomicReference<OctavoSelection> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            selection(surface(activity))));
        assertNotNull(result.get());
        return result.get();
    }

    private static OctavoSelection selection(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSelection> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            selection(surface(activity))));
        assertNotNull(result.get());
        return result.get();
    }

    private static OctavoSelection selection(OctavoSurfaceView view) {
        return OctavoSelection.fromNative(view.selectionPacketForTesting());
    }

    private static void openFixture(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            assertTrue(activity.libraryVisibleForTesting());
            assertTrue(activity.openFixtureForTesting());
        });
    }

    private static OctavoSurfaceView surface(OctavoActivity activity) {
        OctavoSurfaceView view = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        assertNotNull(view);
        return view;
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(surface(activity)));
        assertNotNull(result.get());
        return result.get();
    }

    private static void awaitReaderReady(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                if (view == null) {
                    return false;
                }
                long[] state = view.nativeStateForTesting();
                return state != null
                    && state.length == OctavoSurfaceView.STATE_FIELD_COUNT
                    && state[OctavoSurfaceView.STATE_RESUMED] == 1
                    && state[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && state[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                    && state[OctavoSurfaceView
                        .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                    && state[OctavoSurfaceView
                        .STATE_REFLOW_PRESENTATION_PENDING] == 0
                    && state[OctavoSurfaceView
                        .STATE_HOST_PRESENTATION_PENDING] == 0;
            },
            "8vo did not present a settled reader frame");
    }

    private static Bitmap copyFrame(OctavoSurfaceView surface)
        throws InterruptedException {
        int width = surface.getWidth();
        int height = surface.getHeight();
        assertTrue(width > 0);
        assertTrue(height > 0);
        Bitmap bitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888);
        HandlerThread copyThread = new HandlerThread(
            "8vo-highlight-pixel-copy");
        copyThread.start();
        try {
            for (int attempt = 0; attempt < 10; ++attempt) {
                CountDownLatch copied = new CountDownLatch(1);
                AtomicInteger result = new AtomicInteger(
                    PixelCopy.ERROR_UNKNOWN);
                PixelCopy.request(surface, bitmap, copyResult -> {
                    result.set(copyResult);
                    copied.countDown();
                }, new Handler(copyThread.getLooper()));
                assertTrue(copied.await(5, TimeUnit.SECONDS));
                if (result.get() == PixelCopy.SUCCESS) {
                    return bitmap;
                }
                SystemClock.sleep(100);
            }
            fail("PixelCopy could not read the highlighted frame");
            bitmap.recycle();
            return null;
        } finally {
            copyThread.quitSafely();
        }
    }

    private static int countPixelsNear(Bitmap bitmap,
                                       int expected,
                                       int tolerance) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); ++y) {
            for (int x = 0; x < bitmap.getWidth(); ++x) {
                int pixel = bitmap.getPixel(x, y);
                int distance = Math.abs(Color.red(pixel) - Color.red(expected))
                    + Math.abs(Color.green(pixel) - Color.green(expected))
                    + Math.abs(Color.blue(pixel) - Color.blue(expected));
                if (distance <= tolerance) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private static void awaitActivity(
        ActivityScenario<OctavoActivity> scenario,
        ActivityCondition condition,
        String failureMessage) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MILLIS;
        AtomicReference<Boolean> matched = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> matched.set(
                condition.matches(activity)));
            if (matched.get()) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(25);
        }
        fail(failureMessage);
    }
}
