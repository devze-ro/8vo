package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.PixelCopy;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoNavigationTest {
    @Before
    public void clearPort6Library() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
    }

    private static void openFixture(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            assertTrue(activity.libraryVisibleForTesting());
            assertTrue(activity.openFixtureForTesting());
        });
    }
    private interface StateCondition {
        boolean matches(long[] snapshot);
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface)));
        assertNotNull(result.get());
        return result.get();
    }

    private static long[] state(ActivityScenario<OctavoActivity> scenario) {
        long[] result = surface(scenario).nativeStateForTesting();
        assertNotNull(result);
        assertEquals(OctavoSurfaceView.STATE_FIELD_COUNT, result.length);
        return result;
    }

    private static long[] awaitState(
        ActivityScenario<OctavoActivity> scenario,
        StateCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 100; ++attempt) {
            long[] snapshot = state(scenario);
            if (condition.matches(snapshot)) {
                return snapshot;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] awaitInitialPage(
        ActivityScenario<OctavoActivity> scenario) {
        return awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_RESUMED] == 1
                && snapshot[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                && snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
                && snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] == 1
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && snapshot[
                    OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING] == 0,
            "8vo did not present the initial Port 6 page");
    }

    private static long[] awaitPage(
        ActivityScenario<OctavoActivity> scenario,
        long pageIndex) {
        return awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] == pageIndex
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0,
            "8vo did not present page " + pageIndex);
    }

    private static long[] awaitLocationCacheComplete(
        ActivityScenario<OctavoActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicReference<long[]> snapshot = new AtomicReference<>();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                snapshot.set(
                    view == null
                        ? null : view.locationCacheStateForTesting());
            });
            long[] current = snapshot.get();
            if (current != null && current.length == 10
                && current[0] == 1 && current[1] == 1) {
                InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();
                return state(scenario);
            }
            SystemClock.sleep(20);
        }
        fail("8vo did not complete bounded deferred location warming");
        return new long[0];
    }

    private static String visibleText(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                .visibleTextForTesting()));
        assertNotNull(result.get());
        return result.get();
    }

    private static String progressLabel(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                .progressLabelForTesting()));
        assertNotNull(result.get());
        return result.get();
    }

    private static void dispatchTap(OctavoSurfaceView surface,
                                    boolean next,
                                    long eventTimeMillis) {
        float x = surface.getWidth() * (next ? 5.0f / 6.0f : 1.0f / 6.0f);
        float y = surface.getHeight() / 2.0f;
        MotionEvent down = MotionEvent.obtain(eventTimeMillis,
                                              eventTimeMillis,
                                              MotionEvent.ACTION_DOWN,
                                              x,
                                              y,
                                              0);
        MotionEvent up = MotionEvent.obtain(eventTimeMillis,
                                            eventTimeMillis + 1,
                                            MotionEvent.ACTION_UP,
                                            x,
                                            y,
                                            0);
        try {
            assertTrue(surface.dispatchTouchEvent(down));
            assertTrue(surface.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static void tap(ActivityScenario<OctavoActivity> scenario,
                            boolean next) {
        scenario.onActivity(activity -> dispatchTap(
            (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface),
            next,
            SystemClock.uptimeMillis()));
    }

    private static Bitmap copyFrame(OctavoSurfaceView surface)
        throws InterruptedException {
        int width = surface.getWidth();
        int height = surface.getHeight();
        assertTrue(width > 0);
        assertTrue(height > 0);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        HandlerThread copyThread = new HandlerThread("8vo-navigation-pixel-copy");
        copyThread.start();
        try {
            for (int attempt = 0; attempt < 10; ++attempt) {
                CountDownLatch copied = new CountDownLatch(1);
                AtomicInteger result = new AtomicInteger(PixelCopy.ERROR_UNKNOWN);
                PixelCopy.request(surface,
                                  bitmap,
                                  copyResult -> {
                                      result.set(copyResult);
                                      copied.countDown();
                                  },
                                  new Handler(copyThread.getLooper()));
                assertTrue(copied.await(5, TimeUnit.SECONDS));
                if (result.get() == PixelCopy.SUCCESS) {
                    return bitmap;
                }
                SystemClock.sleep(100);
            }
            fail("PixelCopy could not read the Port 6 frame");
            bitmap.recycle();
            return null;
        } finally {
            copyThread.quitSafely();
        }
    }

    private static void assertPageAndProgress(long[] snapshot,
                                              long pageIndex,
                                              long pageCount) {
        assertEquals(pageIndex,
                     snapshot[OctavoSurfaceView.STATE_PAGE_INDEX]);
        assertEquals(pageCount,
                     snapshot[OctavoSurfaceView.STATE_PAGE_COUNT]);
        assertEquals(pageIndex - 1,
                     snapshot[OctavoSurfaceView.STATE_PROGRESS_PAGE_INDEX]);
        assertEquals(pageCount,
                     snapshot[OctavoSurfaceView.STATE_PROGRESS_PAGE_COUNT]);
    }

    private static void assertNavigationHealthy(long[] snapshot) {
        assertEquals(0,
                     snapshot[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
        assertEquals(0,
                     snapshot[OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT]);
        assertEquals(0,
                     snapshot[
                         OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]);
        assertEquals(0,
                     snapshot[
                         OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING]);
        assertEquals(snapshot[
                         OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT],
                     snapshot[
                         OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
        assertEquals(0,
                     snapshot[OctavoSurfaceView.STATE_READER_VIEW_ERRORS]);
        assertTrue(snapshot[
                       OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT] > 0);
        assertTrue(snapshot[
                       OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX] >= 0);
        assertTrue(snapshot[
                       OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                   < snapshot[
                       OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT]);
    }

    @Test
    public void adjacentPagesChangeTextPixelsProgressAndRespectBoundaries()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            long[] initial = awaitInitialPage(scenario);
            initial = awaitLocationCacheComplete(scenario);
            long pageCount = initial[OctavoSurfaceView.STATE_PAGE_COUNT];
            assertTrue("Port 7 fixture needs at least four pages", pageCount >= 4);
            assertTrue(
                "Port 7 default pagination exceeded its borderless full-viewport "
                    + "regression bound",
                pageCount <= 64);
            assertPageAndProgress(initial, 1, pageCount);
            assertNavigationHealthy(initial);

            String initialText = visibleText(scenario);
            Bitmap initialPixels = copyFrame(surface(scenario));
            assertNotNull(initialPixels);
            try {
                tap(scenario, false);
                long[] beginningBoundary = state(scenario);
                assertPageAndProgress(beginningBoundary, 1, pageCount);
                assertEquals(initial[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH],
                             beginningBoundary[
                                 OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
                assertEquals(initial[OctavoSurfaceView.STATE_FRAME_COUNT],
                             beginningBoundary[
                                 OctavoSurfaceView.STATE_FRAME_COUNT]);
                assertEquals(
                    initial[OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT] + 1,
                    beginningBoundary[
                        OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT]);
                assertNavigationHealthy(beginningBoundary);

                tap(scenario, true);
                long[] next = awaitPage(scenario, 2);
                assertPageAndProgress(next, 2, pageCount);
                assertTrue(next[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                           != initial[
                               OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
                assertTrue(!visibleText(scenario).equals(initialText));
                assertEquals(
                    initial[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 1,
                    next[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
                assertEquals(
                    initial[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT] + 1,
                    next[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
                Bitmap nextPixels = copyFrame(surface(scenario));
                assertNotNull(nextPixels);
                try {
                    assertTrue("Next page pixels did not change",
                               !initialPixels.sameAs(nextPixels));
                } finally {
                    nextPixels.recycle();
                }
                assertNavigationHealthy(next);

                tap(scenario, false);
                long[] restored = awaitPage(scenario, 1);
                assertPageAndProgress(restored, 1, pageCount);
                assertEquals(initial[
                                 OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH],
                             restored[
                                 OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
                assertEquals(initialText, visibleText(scenario));
                Bitmap restoredPixels = copyFrame(surface(scenario));
                assertNotNull(restoredPixels);
                try {
                    assertTrue("Previous page pixels were not restored",
                               initialPixels.sameAs(restoredPixels));
                } finally {
                    restoredPixels.recycle();
                }
                assertNavigationHealthy(restored);

                long[] atEnd = restored;
                long expectedSuccess =
                    restored[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT];
                long startingBoundary =
                    restored[OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT];
                boolean reachedEnd = false;
                for (int attempt = 0; attempt < 512; ++attempt) {
                    tap(scenario, true);
                    long successBefore = expectedSuccess;
                    long[] after = awaitState(
                        scenario,
                        snapshot ->
                            snapshot[
                                OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT]
                                > startingBoundary
                            || (snapshot[
                                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                                    > successBefore
                                && snapshot[
                                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                                    == 0),
                        "8vo did not resolve the next-page intent");
                    if (after[OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT]
                        > startingBoundary) {
                        reachedEnd = true;
                        break;
                    }
                    assertEquals(expectedSuccess + 1,
                                 after[
                                     OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
                    expectedSuccess += 1;
                    atEnd = after;
                    assertNavigationHealthy(atEnd);
                }
                assertTrue("Port 6 did not reach the end-of-book boundary",
                           reachedEnd);
                long endHash =
                    atEnd[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH];
                long endFrame =
                    atEnd[OctavoSurfaceView.STATE_FRAME_COUNT];
                long endSuccess =
                    atEnd[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT];
                long endPresented =
                    atEnd[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT];
                long endBoundaries =
                    atEnd[OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT];

                long[] endBoundary = state(scenario);
                assertEquals(endHash,
                             endBoundary[
                                 OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
                assertEquals(endFrame,
                             endBoundary[OctavoSurfaceView.STATE_FRAME_COUNT]);
                assertEquals(endSuccess,
                             endBoundary[
                                 OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
                assertEquals(
                    endPresented,
                    endBoundary[
                        OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
                assertEquals(
                    endBoundaries + 1,
                    endBoundary[
                        OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT]);
                assertNavigationHealthy(endBoundary);
            } finally {
                initialPixels.recycle();
            }
        }
    }

    @Test
    public void crossSectionProgressIdentifiesForwardAndBackwardAdjacency()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            long[] initial = awaitInitialPage(scenario);
            long firstSectionPageCount =
                initial[OctavoSurfaceView.STATE_PAGE_COUNT];
            long sectionCount = initial[OctavoSurfaceView.STATE_SECTION_COUNT];
            assertEquals(0, initial[OctavoSurfaceView.STATE_SPINE_INDEX]);
            assertEquals(4, sectionCount);
            assertTrue(progressLabel(scenario).startsWith(
                "Section 1 of 4 | Page 1 of "));
            assertNavigationHealthy(initial);

            for (long targetPage = 2;
                 targetPage <= firstSectionPageCount;
                 ++targetPage) {
                tap(scenario, true);
                long expectedPage = targetPage;
                awaitState(
                    scenario,
                    snapshot ->
                        snapshot[OctavoSurfaceView.STATE_SPINE_INDEX] == 0
                        && snapshot[OctavoSurfaceView.STATE_PAGE_INDEX]
                            == expectedPage
                        && snapshot[
                            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                            == 0,
                    "8vo did not reach the expected first-section page");
            }

            long[] firstSectionEnd = state(scenario);
            String firstSectionEndLabel = progressLabel(scenario);
            String firstSectionEndText = visibleText(scenario);
            Bitmap firstSectionEndPixels = copyFrame(surface(scenario));
            assertNotNull(firstSectionEndPixels);
            try {
                assertEquals(firstSectionPageCount,
                             firstSectionEnd[
                                 OctavoSurfaceView.STATE_PAGE_INDEX]);
                assertTrue(firstSectionEndLabel.startsWith(
                    "Section 1 of 4 | Page " + firstSectionPageCount
                    + " of " + firstSectionPageCount + " | "));

                tap(scenario, true);
                long[] secondSectionStart = awaitState(
                    scenario,
                    snapshot ->
                        snapshot[OctavoSurfaceView.STATE_SPINE_INDEX] == 1
                        && snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] == 1
                        && snapshot[
                            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                            == 0,
                    "8vo did not present the adjacent second section");
                assertEquals(sectionCount,
                             secondSectionStart[
                                 OctavoSurfaceView.STATE_SECTION_COUNT]);
                assertTrue(secondSectionStart[
                               OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                           > firstSectionEnd[
                               OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]);
                assertEquals(firstSectionEnd[
                                 OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT],
                             secondSectionStart[
                                 OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT]);
                String secondSectionLabel = progressLabel(scenario);
                assertTrue(secondSectionLabel.startsWith(
                    "Section 2 of 4 | Page 1 | "));
                assertTrue(!visibleText(scenario).equals(firstSectionEndText));
                Bitmap secondSectionPixels = copyFrame(surface(scenario));
                assertNotNull(secondSectionPixels);
                try {
                    assertTrue("Cross-section pixels did not change",
                               !firstSectionEndPixels.sameAs(
                                   secondSectionPixels));
                } finally {
                    secondSectionPixels.recycle();
                }
                assertNavigationHealthy(secondSectionStart);

                tap(scenario, false);
                long[] restored = awaitState(
                    scenario,
                    snapshot ->
                        snapshot[OctavoSurfaceView.STATE_SPINE_INDEX] == 0
                        && snapshot[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                            == firstSectionEnd[
                                OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                        && snapshot[
                            OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                            == firstSectionEnd[
                                OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                        && snapshot[
                            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                            == 0,
                    "8vo did not restore the adjacent first section");
                assertTrue(restored[OctavoSurfaceView.STATE_PAGE_INDEX] == 0
                           || restored[OctavoSurfaceView.STATE_PAGE_INDEX]
                               == firstSectionPageCount);
                String restoredLabel = progressLabel(scenario);
                assertTrue(restoredLabel.startsWith("Section 1 of 4 | "));
                assertTrue(!restoredLabel.contains("Page 0"));
                assertTrue(!restoredLabel.equals(secondSectionLabel));
                assertEquals(firstSectionEndText, visibleText(scenario));
                assertNavigationHealthy(restored);
            } finally {
                firstSectionEndPixels.recycle();
            }
        }
    }

    @Test
    public void rapidTapsWaitForPresentationAcrossHostReplacement()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            long[] initial = awaitInitialPage(scenario);
            long initialHash =
                initial[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH];
            AtomicReference<long[]> gated = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view =
                    (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
                long now = SystemClock.uptimeMillis();
                dispatchTap(view, true, now);
                dispatchTap(view, true, now + 2);
                gated.set(view.nativeStateForTesting());
            });

            assertNotNull(gated.get());
            assertEquals(1,
                         gated.get()[
                             OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]);
            assertEquals(
                initial[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 1,
                gated.get()[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
            assertEquals(
                initial[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT],
                gated.get()[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
            assertEquals(
                initial[OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT] + 1,
                gated.get()[OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT]);
            assertEquals(1, gated.get()[OctavoSurfaceView.STATE_PAGE_INDEX]);

            long[] pageTwo = awaitPage(scenario, 2);
            assertEquals(2, pageTwo[OctavoSurfaceView.STATE_PAGE_INDEX]);
            assertTrue(pageTwo[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                       != initialHash);
            assertNavigationHealthy(pageTwo);

            scenario.moveToState(Lifecycle.State.CREATED);
            long[] paused = state(scenario);
            assertEquals(0, paused[OctavoSurfaceView.STATE_RESUMED]);
            assertEquals(2, paused[OctavoSurfaceView.STATE_PAGE_INDEX]);
            scenario.moveToState(Lifecycle.State.RESUMED);
            long[] resumed = awaitPage(scenario, 2);
            assertEquals(pageTwo[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH],
                         resumed[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertNavigationHealthy(resumed);

            long surfaceGeneration =
                resumed[OctavoSurfaceView.STATE_SURFACE_GENERATION];
            long surfaceDestroyCount =
                resumed[OctavoSurfaceView.STATE_SURFACE_DESTROY_COUNT];
            scenario.onActivity(activity ->
                ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                    .replaceNativeSurfaceForTesting());
            long[] replaced = awaitState(
                scenario,
                snapshot ->
                    snapshot[OctavoSurfaceView.STATE_SURFACE_GENERATION]
                        > surfaceGeneration
                    && snapshot[
                        OctavoSurfaceView.STATE_SURFACE_DESTROY_COUNT]
                        > surfaceDestroyCount
                    && snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] == 2,
                "8vo did not replace and re-present the native surface");
            assertEquals(resumed[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH],
                         replaced[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertNavigationHealthy(replaced);

            scenario.recreate();
            long[] recreated = awaitState(
                scenario,
                snapshot ->
                    snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && snapshot[
                        OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                    && snapshot[
                        OctavoSurfaceView.STATE_RESTORE_SUCCEEDED] == 1
                    && snapshot[OctavoSurfaceView.STATE_PAGE_INDEX]
                        == pageTwo[OctavoSurfaceView.STATE_PAGE_INDEX]
                    && snapshot[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                        == pageTwo[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                    && snapshot[
                        OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                        == pageTwo[
                            OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                    && snapshot[
                        OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                        == 0,
                "8vo did not exactly restore the presented page and its "
                    + "deferred progress metadata");
            assertEquals(1, recreated[OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertEquals(1, recreated[OctavoSurfaceView.STATE_RESTORE_ATTEMPTED]);
            assertEquals(1, recreated[OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(0, recreated[OctavoSurfaceView.STATE_RESTORE_FAILURE_COUNT]);
            assertEquals(pageTwo[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH],
                         recreated[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertEquals(pageTwo[OctavoSurfaceView.STATE_PAGE_INDEX],
                         recreated[OctavoSurfaceView.STATE_PAGE_INDEX]);
            assertEquals(pageTwo[OctavoSurfaceView.STATE_PAGE_COUNT],
                         recreated[OctavoSurfaceView.STATE_PAGE_COUNT]);
            assertEquals(pageTwo[OctavoSurfaceView.STATE_SPINE_INDEX],
                         recreated[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
            assertEquals(
                pageTwo[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET],
                recreated[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
            assertEquals(
                pageTwo[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX],
                recreated[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]);
            assertNavigationHealthy(recreated);

            long recreatedHash =
                recreated[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH];
            long recreatedLocation =
                recreated[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX];
            tap(scenario, true);
            long[] recreatedNext = awaitState(
                scenario,
                snapshot ->
                    snapshot[
                        OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                        > recreated[
                            OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                    && snapshot[
                        OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                        == 0,
                "8vo did not present the page after the restored location");
            assertEquals(
                recreated[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 1,
                recreatedNext[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
            assertEquals(
                recreated[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT] + 1,
                recreatedNext[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
            assertTrue(recreatedNext[
                           OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                       != recreatedHash);
            assertTrue(recreatedNext[
                           OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                       > recreatedLocation);
            assertNavigationHealthy(recreatedNext);
        }
    }
}
