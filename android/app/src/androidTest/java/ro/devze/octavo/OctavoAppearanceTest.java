package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.Lifecycle;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public final class OctavoAppearanceTest {
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final int LITERARY_LEFT_PROBE = 0xFFDC1414;
    private static final int LITERARY_RIGHT_PROBE = 0xFF143CDC;
    private static final int CLEAR_LEFT_PROBE = 0xFF00AAB8;
    private static final int CLEAR_RIGHT_PROBE = 0xFFC88A00;

    private interface StateCondition {
        boolean matches(long[] state);
    }

    @Before
    public void resetPort6LibraryAndPort7Appearance() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
    }

    @Test
    public void allThemesReachNativeStateAndExactPagePixels()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            long[] initial = awaitInitialPage(scenario);
            awaitLocationCacheComplete(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            initial = state(scenario);

            assertEquals(
                0, initial[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            ChromeOcclusion chrome = chromeOcclusion(scenario);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] chromeShown = awaitReaderComposition(
                scenario, chrome, true);
            assertChromeNativeStateUnchanged(initial, chromeShown);
            assertEquals(
                initial[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                chromeShown[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] chromeFree = awaitReaderComposition(
                scenario, chrome, false);
            assertChromeNativeStateUnchanged(initial, chromeFree);
            assertChromeNativeStateUnchanged(chromeShown, chromeFree);
            assertEquals(
                chromeShown[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                chromeFree[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);

            OctavoAppearance expected = OctavoAppearance.defaults();
            for (int theme = 0;
                 theme < OctavoAppearance.THEME_COUNT;
                 ++theme) {
                expected = expected.withTheme(theme);
                long[] presented = theme == OctavoAppearance.THEME_PAPER
                    ? awaitAppearance(scenario, expected, 1)
                    : requestAndAwaitAppearance(scenario, expected);
                assertNativeAppearance(presented, expected);
                assertHostAppearance(scenario, expected);

                Bitmap frame = copyFrame(surface(scenario));
                assertNotNull(frame);
                try {
                    boolean darkBlankRegions =
                        theme == OctavoAppearance.THEME_DUSK
                        || theme == OctavoAppearance.THEME_WARM_DARK
                        || theme == OctavoAppearance.THEME_OLED;
                    assertExactPageAndBackgroundPixels(
                        presented,
                        frame,
                        OctavoDesignTokens.forAppearance(expected),
                        darkBlankRegions);
                    assertContrastingReaderInk(
                        presented,
                        frame,
                        OctavoDesignTokens.forAppearance(expected));
                } finally {
                    frame.recycle();
                }
            }
        }
    }

    @Test
    public void persistedNightAppearanceColdOpenRetainsReadableInk()
        throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance expected = OctavoAppearance.defaults()
            .withTheme(OctavoAppearance.THEME_WARM_DARK);
        assertTrue(new OctavoAppearanceStore(context).save(expected));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertNoBrightComposedFrames(
                scenario, "cold library launch", 4);
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
                assertNotNull(
                    "Reader entry did not install its target-theme cover",
                    activity.findViewById(
                        R.id.octavo_reader_entry_cover));
            });
            assertNoBrightComposedFrames(
                scenario, "reader entry", 4);
            long[] presented = awaitInitialPage(scenario);
            awaitReaderEntryCoverRemoved(scenario);
            assertNativeAppearance(presented, expected);
            assertHostAppearance(scenario, expected);
            assertTrue(
                "Persisted night appearance rasterized no body glyphs: "
                    + presented[
                        OctavoSurfaceView
                            .STATE_TYPOGRAPHY_RASTERIZED_GLYPH_COUNT],
                presented[
                    OctavoSurfaceView
                        .STATE_TYPOGRAPHY_RASTERIZED_GLYPH_COUNT] > 100);

            Bitmap frame = copyFrame(surface(scenario));
            assertNotNull(frame);
            try {
                assertContrastingReaderInk(
                    presented,
                    frame,
                    OctavoDesignTokens.forAppearance(expected));
                assertReaderInkHasUnclippedVerticalEdges(
                    presented,
                    frame,
                    OctavoDesignTokens.forAppearance(expected));
            } finally {
                frame.recycle();
            }

            AtomicReference<int[]> surfaceLocation =
                new AtomicReference<>();
            ChromeOcclusion nightChrome = chromeOcclusion(scenario);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] nightIdentity = awaitReaderComposition(
                scenario, nightChrome, false);
            assertChromeNativeStateUnchanged(presented, nightIdentity);
            scenario.onActivity(activity -> {
                int[] location = new int[2];
                ((OctavoSurfaceView)activity.findViewById(
                    R.id.octavo_surface)).getLocationOnScreen(location);
                surfaceLocation.set(location);
            });
            Bitmap composed =
                InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().takeScreenshot();
            assertNotNull(composed);
            assertNotNull(surfaceLocation.get());
            try {
                assertContrastingReaderInk(
                    presented,
                    composed,
                    OctavoDesignTokens.forAppearance(expected),
                    surfaceLocation.get()[0],
                    surfaceLocation.get()[1]);
            } finally {
                composed.recycle();
            }

            scenario.onActivity(
                OctavoActivity::openAppearancePanelForTesting);
            assertNoBrightComposedFrames(
                scenario, "appearance panel opening", 4);
            scenario.onActivity(
                OctavoActivity::closeAppearancePanelForTesting);
            assertNoBrightComposedFrames(
                scenario, "appearance panel closing", 4);

            long anchorSpine =
                presented[
                    OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                presented[
                    OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            scenario.onActivity(OctavoActivity::closeBookForTesting);
            assertNoBrightComposedFrames(
                scenario, "reader exit", 4);
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
            });
            assertNoBrightComposedFrames(
                scenario, "reader re-entry", 4);
            long[] reopened = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_RESTORE_SUCCEEDED] == 1
                    && isPresentedAtAnchor(
                        current, anchorSpine, anchorOffset),
                "8vo did not restore the sampled night-reading anchor");
            assertExactAPage(presented, reopened);
        }
    }

    @Test
    public void remainingLocationMetadataWarmsAfterFirstPresentedPage()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            long beforeOpenMillis = SystemClock.uptimeMillis();
            openFixture(scenario);
            long[] firstPage = awaitInitialPage(scenario);
            long observedMillis = SystemClock.uptimeMillis();
            long entryStartedMillis = firstPage[
                OctavoSurfaceView.STATE_READER_ENTRY_STARTED_MILLIS];
            long firstFrameMillis = firstPage[
                OctavoSurfaceView.STATE_FIRST_FRAME_ELAPSED_MILLIS];
            assertTrue(
                firstPage[OctavoSurfaceView.STATE_FRAME_COUNT] > 0);
            assertTrue(
                "Reader-entry evidence predates showReader",
                entryStartedMillis >= beforeOpenMillis);
            assertTrue(
                "Reader-entry evidence is in the future",
                entryStartedMillis <= observedMillis);
            assertTrue(
                "showReader-to-accepted-post evidence was not published",
                firstFrameMillis > 0);
            assertTrue(
                "Accepted-post evidence exceeds the observation window",
                firstFrameMillis
                    <= observedMillis - entryStartedMillis);
            assertTrue(
                "Optimized debug showReader-to-accepted-post exceeded "
                    + "1500 ms: " + firstFrameMillis,
                firstFrameMillis < 1500);

            long[] cache = awaitLocationCacheComplete(scenario);
            assertEquals(1, cache[0]);
            assertEquals(1, cache[1]);
            assertTrue(cache[3] > 0);
            assertEquals(cache[3], cache[2]);
            assertTrue(cache[4] > 0);
            assertTrue(cache[5] > 0);
            assertTrue(cache[5] <= cache[3]);
            assertEquals(cache[5], cache[6]);
            assertEquals(0, cache[8]);
            assertTrue(
                "Deferred location work preceded the first posted frame",
                cache[9] > 0);
        }

        final String locationFailure =
            "Whole-book progress is unavailable; you can keep reading";
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(
                    view.forceLocationWarmFailuresForTesting(1));
            });
            awaitInitialPage(scenario);
            long[] failed = awaitLocationCacheFailure(scenario);
            assertEquals(1, failed[5]);
            assertEquals(0, failed[6]);
            assertEquals(1, failed[8]);
            assertTrue(failed[9] > 0);
            scenario.onActivity(activity -> {
                assertEquals(
                    locationFailure, activity.lastOpenErrorForTesting());
                TextView banner = (TextView)activity.findViewById(
                    R.id.octavo_reader_failure);
                assertNotNull(banner);
                assertEquals(locationFailure, banner.getText().toString());
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertFalse(view.locationWarmPostedForTesting());
            });
            SystemClock.sleep(200);
            long[] stable = locationCacheState(scenario);
            assertEquals(failed[5], stable[5]);
            assertEquals(failed[7], stable[7]);
            assertEquals(failed[8], stable[8]);
        }

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            long[] firstPage = awaitInitialPage(scenario);
            long[] beforeCache = locationCacheState(scenario);
            assertEquals(
                "The fixture location cache completed before its deferred "
                    + "failure probe",
                0,
                beforeCache[0]);
            OctavoAppearance pending =
                OctavoAppearance.defaults().withTheme(
                    OctavoAppearance.THEME_SEPIA);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.forcePrePresentFailuresForTesting(8));
                activity.requestAppearanceForTesting(pending);
            });
            long[] exhausted = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        >= firstPage[
                            OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT] + 5
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        == firstPage[OctavoSurfaceView.STATE_FRAME_COUNT],
                "8vo did not reach the deferred-metadata presentation "
                    + "terminal");
            assertEquals(
                firstPage[OctavoSurfaceView.STATE_FRAME_COUNT],
                exhausted[OctavoSurfaceView.STATE_FRAME_COUNT]);
            SystemClock.sleep(100);
            long[] stopped = locationCacheState(scenario);
            assertTrue(
                "Deferred metadata never observed the pending presentation",
                stopped[7] > beforeCache[7]);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(
                    view.presentationFailureNotifiedForTesting());
                assertFalse(view.locationWarmPostedForTesting());
            });
            SystemClock.sleep(200);
            long[] stillStopped = locationCacheState(scenario);
            assertEquals(stopped[5], stillStopped[5]);
            assertEquals(stopped[7], stillStopped[7]);
            assertEquals(stopped[8], stillStopped[8]);
        }
    }

    @Test
    public void actualResumeGestureKeepsReaderEntryChromeHidden()
        throws InterruptedException {
        AtomicReference<Boolean> entryCoverObserved =
            new AtomicReference<>(false);
        AtomicReference<Boolean> entryHostChromeHidden =
            new AtomicReference<>(false);
        AtomicReference<long[]> entryChromeState = new AtomicReference<>();
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            scenario.onActivity(OctavoActivity::closeBookForTesting);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                ViewGroup content = activity.findViewById(
                    android.R.id.content);
                assertNotNull(content);
                content.setOnHierarchyChangeListener(
                    new ViewGroup.OnHierarchyChangeListener() {
                        @Override
                        public void onChildViewAdded(
                            View parent, View child) {
                            if (child.findViewById(
                                    R.id.octavo_reader_entry_cover)
                                == null) {
                                return;
                            }
                            entryCoverObserved.set(true);
                            OctavoSurfaceView reader =
                                (OctavoSurfaceView)child.findViewById(
                                    R.id.octavo_surface);
                            View top = child.findViewById(
                                R.id.octavo_reader_top_chrome);
                            View bottom = child.findViewById(
                                R.id.octavo_reader_bottom_chrome);
                            entryHostChromeHidden.set(
                                reader != null
                                && top != null
                                && bottom != null
                                && !activity.chromeVisibleForTesting()
                                && !reader.chromeVisibleForTesting()
                                && chromeViewIsExactlyHidden(top)
                                && chromeViewIsExactlyHidden(bottom));
                            entryChromeState.set(
                                reader == null
                                    ? null
                                    : reader.nativeStateForTesting());
                        }

                        @Override
                        public void onChildViewRemoved(
                            View parent, View child) {}
                    });
                View resume = findClickableText(
                    activity.findViewById(android.R.id.content),
                    activity.getString(R.string.resume));
                assertNotNull(
                    "The sample Resume button was unavailable", resume);
                assertTrue(resume.isShown());

                float x = resume.getWidth() / 2.0f;
                float y = resume.getHeight() / 2.0f;
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(
                    now, now + 20, MotionEvent.ACTION_UP, x, y, 0);
                try {
                    assertTrue(resume.dispatchTouchEvent(down));
                    assertTrue(resume.dispatchTouchEvent(up));
                } finally {
                    down.recycle();
                    up.recycle();
                }
            });

            long[] firstAccepted = awaitInitialPage(scenario);
            assertTrue(
                "Reader entry never installed its page-colored cover",
                Boolean.TRUE.equals(entryCoverObserved.get()));
            assertTrue(
                "Host chrome was visible under the reader-entry cover",
                Boolean.TRUE.equals(entryHostChromeHidden.get()));
            assertNotNull(entryChromeState.get());
            assertEquals(
                0,
                entryChromeState.get()[
                    OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertEquals(
                0,
                entryChromeState.get()[
                    OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertEquals(
                0,
                firstAccepted[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertEquals(
                0,
                firstAccepted[
                    OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            scenario.onActivity(activity -> {
                ViewGroup content = activity.findViewById(
                    android.R.id.content);
                assertNotNull(content);
                content.setOnHierarchyChangeListener(null);
                assertReaderEntryChromeHidden(activity);
            });

            awaitReaderEntryCoverRemoved(scenario);
            scenario.onActivity(activity -> {
                assertNull(activity.findViewById(
                    R.id.octavo_reader_entry_cover));
                assertReaderEntryChromeHidden(activity);
            });

            ChromeOcclusion chrome = chromeOcclusion(scenario);
            long[] settled = awaitReaderComposition(
                scenario, chrome, false);
            assertEquals(
                0, settled[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertEquals(
                0,
                settled[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            scenario.onActivity(
                OctavoAppearanceTest::assertReaderEntryChromeHidden);
        }
    }
    @Test
    public void legacyAppearanceMigrationWaitsForFirstAcceptedReaderFrame()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance captured = capturedTransitionalAppearance();
        OctavoAppearance expected = captured.withFontSizeSp(14);
        OctavoAppearanceStore files = new OctavoAppearanceStore(context);
        byte[] legacyRecord =
            OctavoAppearanceStore.previousRecordForTesting(captured);
        writeAppearanceRecord(files, legacyRecord);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertEquals(expected, activity.appearanceForTesting());
                assertTrue(activity.appearanceStoreForTesting()
                               .hasPendingMigration());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveSuccessCountForTesting());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
            });
            assertArrayEquals(
                legacyRecord,
                readAppearanceRecord(files));

            scenario.onActivity(activity ->
                assertTrue(activity.openFixtureForTesting()));
            awaitInitialPage(scenario);
            assertEquals(1, awaitSaveSuccessCount(scenario, 1));
            scenario.onActivity(activity -> {
                assertFalse(activity.appearanceStoreForTesting()
                                .hasPendingMigration());
                assertEquals(
                    1,
                    activity.appearanceStoreForTesting()
                        .saveSuccessCountForTesting());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
            });

            byte[] published = readAppearanceRecord(files);
            assertEquals(
                OctavoAppearanceStore.currentStoreVersionForTesting(),
                appearanceRecordVersion(published));
            assertFalse(java.util.Arrays.equals(
                legacyRecord, published));
        }

        OctavoAppearanceStore reloaded =
            new OctavoAppearanceStore(context);
        assertEquals(expected, reloaded.load());
        assertFalse(reloaded.hasPendingMigration());
        assertEquals(0, reloaded.saveSuccessCountForTesting());
    }

    @Test
    public void migrationPublicationFailureIsVisibleAndRetriesOnPresentation()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance captured = capturedTransitionalAppearance();
        OctavoAppearance expected = captured.withFontSizeSp(14);
        OctavoAppearanceStore files = new OctavoAppearanceStore(context);
        byte[] legacyRecord =
            OctavoAppearanceStore.previousRecordForTesting(captured);
        writeAppearanceRecord(files, legacyRecord);
        assertTrue(files.temporaryFileForTesting().mkdir());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertEquals(expected, activity.appearanceForTesting());
                assertTrue(activity.appearanceStoreForTesting()
                               .hasPendingMigration());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveSuccessCountForTesting());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
            });
            assertArrayEquals(
                legacyRecord,
                readAppearanceRecord(files));

            scenario.onActivity(activity ->
                assertTrue(activity.openFixtureForTesting()));
            awaitInitialPage(scenario);
            awaitVisibleAppearanceSaveFailure(scenario, 1);
            assertArrayEquals(
                legacyRecord,
                readAppearanceRecord(files));
            scenario.onActivity(activity -> {
                assertTrue(activity.appearanceStoreForTesting()
                               .hasPendingMigration());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveSuccessCountForTesting());
                assertEquals(
                    1,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
                assertEquals(
                    "Appearance changed, but could not be saved",
                    activity.lastOpenErrorForTesting());
            });

            assertTrue(files.temporaryFileForTesting().isDirectory());
            assertTrue(files.temporaryFileForTesting().delete());
            moveToNextPresentedPage(scenario);
            assertEquals(1, awaitSaveSuccessCount(scenario, 1));
            scenario.onActivity(activity -> {
                assertFalse(activity.appearanceStoreForTesting()
                                .hasPendingMigration());
                assertEquals(
                    1,
                    activity.appearanceStoreForTesting()
                        .saveSuccessCountForTesting());
                assertEquals(
                    1,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
            });

            byte[] published = readAppearanceRecord(files);
            assertEquals(
                OctavoAppearanceStore.currentStoreVersionForTesting(),
                appearanceRecordVersion(published));
            assertFalse(java.util.Arrays.equals(
                legacyRecord, published));
        }

        OctavoAppearanceStore reloaded =
            new OctavoAppearanceStore(context);
        assertEquals(expected, reloaded.load());
        assertFalse(reloaded.hasPendingMigration());
    }


    @Test
    public void rapidChoicesCoalesceAndChromeIsPageNeutral()
        throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance first = OctavoAppearance.defaults()
            .withTheme(OctavoAppearance.THEME_SEPIA)
            .withReducedMotion(true);
        OctavoAppearance second = first.withTheme(
            OctavoAppearance.THEME_DUSK);
        OctavoAppearance latest = second.withTheme(
            OctavoAppearance.THEME_OLED);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            awaitLocationCacheComplete(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            long[] before = state(scenario);
            long savesBefore = currentSaveSuccessCount(scenario);

            scenario.onActivity(activity -> {
                activity.requestAppearanceForTesting(first);
                activity.requestAppearanceForTesting(second);
                activity.requestAppearanceForTesting(latest);
            });

            long[] coalesced = awaitPresentedAppearance(
                scenario,
                latest,
                before[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1);
            assertEquals(
                before[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1,
                coalesced[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]);
            assertEquals(
                before[OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT] + 1,
                coalesced[
                    OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT]);
            assertEquals(
                before[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT],
                coalesced[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            assertEquals(
                before[OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT],
                coalesced[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
            assertEquals(
                savesBefore + 1,
                awaitSaveSuccessCount(
                    scenario, savesBefore + 1));
            assertHostAppearance(scenario, latest);
            assertEquals(latest, new OctavoAppearanceStore(context).load());

            long[] hidden = coalesced;
            assertEquals(0,
                         hidden[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            ChromeOcclusion chrome = chromeOcclusion(scenario);
            hidden = awaitReaderComposition(scenario, chrome, false);
            assertCanonicalFullViewportLayout(chrome, hidden);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] visible = awaitReaderComposition(
                scenario, chrome, true);
            assertChromeNativeStateUnchanged(hidden, visible);
            assertEquals(
                hidden[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                visible[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            scenario.onActivity(activity -> {
                assertTrue(activity.chromeVisibleForTesting());
                assertEquals(latest, activity.appearanceForTesting());
            });

            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] hiddenAgain = awaitReaderComposition(
                scenario, chrome, false);
            assertChromeNativeStateUnchanged(hidden, hiddenAgain);
            assertChromeNativeStateUnchanged(visible, hiddenAgain);
            assertEquals(
                visible[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                hiddenAgain[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertNativeAppearance(hiddenAgain, latest);
            scenario.onActivity(activity -> {
                assertFalse(activity.chromeVisibleForTesting());
                assertEquals(latest, activity.appearanceForTesting());
            });

            assertEquals(latest, new OctavoAppearanceStore(context).load());

            OctavoAppearanceStore.clearForTesting(context);
            scenario.onActivity(activity ->
                activity.queuePresentedAppearancePersistenceForTesting());
            scenario.moveToState(Lifecycle.State.CREATED);
            assertEquals(
                "onPause did not flush the last presented appearance",
                latest,
                new OctavoAppearanceStore(context).load());
            scenario.moveToState(Lifecycle.State.RESUMED);
            awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_RESUMED] == 1
                    && current[OctavoSurfaceView.STATE_HAS_SURFACE] == 1,
                "8vo did not resume after the appearance pause flush");

            OctavoAppearanceStore files =
                new OctavoAppearanceStore(context);
            assertFalse(files.temporaryFileForTesting().exists());
            assertTrue(files.temporaryFileForTesting().mkdir());
            AtomicLong failuresBefore = new AtomicLong();
            CountDownLatch deferredFailureVisible = new CountDownLatch(1);
            AtomicReference<String> deferredFailureText =
                new AtomicReference<>();
            AtomicReference<Integer> deferredFailureColor =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                failuresBefore.set(
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
                ViewGroup reader = (ViewGroup)activity.findViewById(
                    R.id.octavo_reader_root);
                assertNotNull(reader);
                reader.setOnHierarchyChangeListener(
                    new ViewGroup.OnHierarchyChangeListener() {
                        @Override
                        public void onChildViewAdded(
                            View parent, View child) {
                            if (child.getId()
                                != R.id.octavo_reader_failure
                                || !(child instanceof TextView)) {
                                return;
                            }
                            TextView banner = (TextView)child;
                            deferredFailureText.set(
                                banner.getText().toString());
                            if (banner.getBackground()
                                instanceof ColorDrawable) {
                                deferredFailureColor.set(
                                    ((ColorDrawable)banner.getBackground())
                                        .getColor());
                            }
                            deferredFailureVisible.countDown();
                        }

                        @Override
                        public void onChildViewRemoved(
                            View parent, View child) {
                        }
                    });
                activity.queuePresentedAppearancePersistenceForTesting();
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            assertTrue(files.temporaryFileForTesting().isDirectory());
            assertTrue(files.temporaryFileForTesting().delete());
            assertFalse(files.temporaryFileForTesting().exists());
            assertEquals(
                "A failed atomic publication replaced the prior record",
                latest,
                new OctavoAppearanceStore(context).load());
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertTrue(
                "The deferred persistence failure was never attached to "
                    + "the reader UI",
                deferredFailureVisible.await(6, TimeUnit.SECONDS));
            assertEquals(
                "Appearance changed, but could not be saved",
                deferredFailureText.get());
            assertEquals(
                Integer.valueOf(
                    OctavoDesignTokens.forAppearance(latest).dialogSurface),
                deferredFailureColor.get());
            scenario.onActivity(activity -> {
                assertEquals(
                    failuresBefore.get() + 1,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
                assertEquals(
                    "Appearance changed, but could not be saved",
                    activity.lastOpenErrorForTesting());
                ViewGroup reader = (ViewGroup)activity.findViewById(
                    R.id.octavo_reader_root);
                assertNotNull(reader);
                reader.setOnHierarchyChangeListener(null);
            });

            OctavoAppearanceStore.clearForTesting(context);
            scenario.onActivity(activity ->
                activity.queuePresentedAppearancePersistenceForTesting());
        }
        assertEquals(
            "Activity destruction did not flush the last presented appearance",
            latest,
            new OctavoAppearanceStore(context).load());
    }
    @Test
    public void everyLayoutValuePreservesAnchorGeometryAndReadableExtremes()
        throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance baseline =
            OctavoAppearance.defaults().withReducedMotion(true);
        assertTrue(new OctavoAppearanceStore(context).save(baseline));
        int[] clippingFamilies = {
            OctavoAppearance.FONT_FAMILY_LITERARY,
            OctavoAppearance.FONT_FAMILY_CLEAR,
        };
        for (int family : clippingFamilies) {
            assertTypographyAtlasCellsUnclipped(
                context,
                baseline.withFontFamily(family).withFontSizeSp(16));
            assertTypographyAtlasCellsUnclipped(
                context,
                baseline.withFontFamily(family).withFontSizeSp(28));
        }


        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            long[] baselinePage = moveToNextPresentedPage(scenario);
            long anchorSpine =
                baselinePage[
                    OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                baselinePage[
                    OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            assertLayoutPresentationHealthy(
                scenario,
                baselinePage,
                baseline,
                anchorSpine,
                anchorOffset);

            int[] fontFamilies = {
                OctavoAppearance.FONT_FAMILY_CLEAR,
                OctavoAppearance.FONT_FAMILY_LITERARY,
            };
            long[] returnedBaseline = baselinePage;
            for (int family : fontFamilies) {
                returnedBaseline = presentLayoutChoice(
                    scenario,
                    baseline.withFontFamily(family),
                    anchorSpine,
                    anchorOffset,
                    true);
            }
            assertBaselineLayoutRoundTrip(
                baselinePage, returnedBaseline);

            long previousTextPx = -1;
            for (int sizeSp : OctavoAppearance.fontSizesSp()) {
                long[] sizeState = presentLayoutChoice(
                    scenario,
                    baseline.withFontSizeSp(sizeSp),
                    anchorSpine,
                    anchorOffset,
                    sizeSp == 16 || sizeSp == 28);
                assertTrue(
                    sizeState[
                        OctavoSurfaceView.STATE_TYPOGRAPHY_TEXT_PX]
                        > previousTextPx);
                previousTextPx = sizeState[
                    OctavoSurfaceView.STATE_TYPOGRAPHY_TEXT_PX];
            }
            returnedBaseline = presentLayoutChoice(
                scenario,
                baseline,
                anchorSpine,
                anchorOffset,
                false);
            assertBaselineLayoutRoundTrip(
                baselinePage, returnedBaseline);

            long previousLineAdvance = -1;
            for (int spacing
                     : OctavoAppearance.lineSpacingsPermille()) {
                long[] spacingState = presentLayoutChoice(
                    scenario,
                    baseline.withLineSpacingPermille(spacing),
                    anchorSpine,
                    anchorOffset,
                    spacing == 1150 || spacing == 1500);
                assertTrue(
                    spacingState[
                        OctavoSurfaceView
                            .STATE_TYPOGRAPHY_LINE_ADVANCE_PX]
                        > previousLineAdvance);
                previousLineAdvance = spacingState[
                    OctavoSurfaceView.STATE_TYPOGRAPHY_LINE_ADVANCE_PX];
            }
            returnedBaseline = presentLayoutChoice(
                scenario,
                baseline,
                anchorSpine,
                anchorOffset,
                false);
            assertBaselineLayoutRoundTrip(
                baselinePage, returnedBaseline);

            int[] margins = {
                OctavoAppearance.MARGINS_WIDE,
                OctavoAppearance.MARGINS_BALANCED,
                OctavoAppearance.MARGINS_FOCUSED,
            };
            long previousContentWidth = -1;
            for (int margin : margins) {
                long[] marginState = presentLayoutChoice(
                    scenario,
                    baseline.withMargins(margin),
                    anchorSpine,
                    anchorOffset,
                    margin == OctavoAppearance.MARGINS_WIDE
                    || margin == OctavoAppearance.MARGINS_FOCUSED);
                assertTrue(
                    marginState[
                        OctavoSurfaceView.STATE_CONTENT_WIDTH]
                        > previousContentWidth);
                previousContentWidth = marginState[
                    OctavoSurfaceView.STATE_CONTENT_WIDTH];
            }
            returnedBaseline = presentLayoutChoice(
                scenario,
                baseline,
                anchorSpine,
                anchorOffset,
                false);
            assertBaselineLayoutRoundTrip(
                baselinePage, returnedBaseline);
            flushAndAssertAppearancePersisted(scenario, baseline);
        }
    }

    @Test
    public void publisherJustificationPreservesHardLinesAndPunctuation()
        throws IOException, InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        File evidence = createPublicationEvidenceEpub(context);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity ->
                assertTrue(activity.openDocumentForTesting(
                    Uri.fromFile(evidence))));
            long[] presented = awaitInitialPage(scenario);
            AtomicReference<String> visibleText = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                visibleText.set(view.visibleTextForTesting());
            });
            assertNotNull(visibleText.get());
            assertTrue(visibleText.get().contains(
                "The reader’s deliberate line"));
            assertTrue(visibleText.get().contains(
                "“Second line—kept intact.”"));
            assertTrue(visibleText.get().contains(
                "Third line…still intact."));
            assertTrue(
                "Hard-line fixture produced no justification plans",
                presented[
                    OctavoSurfaceView.STATE_JUSTIFICATION_PLAN_COUNT] >= 3);
            assertEquals(
                "Publisher justification expanded a hard-break line",
                0,
                presented[
                    OctavoSurfaceView
                        .STATE_JUSTIFICATION_ACTIVE_ROW_COUNT]);
            assertEquals(
                "Hard-break fixture applied inter-word expansion",
                0,
                presented[
                    OctavoSurfaceView
                        .STATE_JUSTIFICATION_APPLIED_EXTRA_PX]);
            assertEquals(
                "Publication punctuation fell back to a missing glyph",
                0,
                presented[
                    OctavoSurfaceView
                        .STATE_TYPOGRAPHY_MISSING_GLYPH_COUNT]);
        } finally {
            if (evidence.exists() && !evidence.delete()) {
                evidence.deleteOnExit();
            }
        }
    }

    @Test
    public void styledBookProvesFamilyAlignmentAndPublisherColorPolicyPixels()
        throws IOException, InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance publisher = OctavoAppearance.defaults()
            .withFontSizeSp(28)
            .withAlignment(OctavoAppearance.ALIGNMENT_PUBLISHER)
            .withPublisherColors(
                OctavoAppearance.PUBLISHER_COLORS_ALLOW)
            .withReducedMotion(true);
        OctavoAppearance clearPublisher =
            publisher.withFontFamily(
                OctavoAppearance.FONT_FAMILY_CLEAR);
        char literaryLeft =
            findRuntimeOverhangGlyph(context, publisher, true);
        char literaryRight =
            findRuntimeOverhangGlyph(context, publisher, false);
        char clearLeft =
            findRuntimeOverhangGlyph(context, clearPublisher, true);
        char clearRight =
            findRuntimeOverhangGlyph(context, clearPublisher, false);
        File styled = createStyledEvidenceEpub(
            context, literaryLeft, literaryRight, clearLeft, clearRight);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity ->
                assertTrue(activity.openDocumentForTesting(
                    Uri.fromFile(styled))));
            long[] initial = awaitInitialPage(scenario);
            long[] atPublisher =
                requestAndAwaitAppearance(scenario, publisher);
            assertAnchorInsidePage(
                atPublisher,
                atPublisher[
                    OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX],
                atPublisher[
                    OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
            assertTrue(
                "Publisher alignment produced no justification plans",
                atPublisher[
                    OctavoSurfaceView.STATE_JUSTIFICATION_PLAN_COUNT] > 0);
            assertTrue(
                "Publisher alignment activated no justified rows",
                atPublisher[
                    OctavoSurfaceView
                        .STATE_JUSTIFICATION_ACTIVE_ROW_COUNT] > 0);
            assertTrue(
                "Publisher alignment applied no inter-word expansion",
                atPublisher[
                    OctavoSurfaceView
                        .STATE_JUSTIFICATION_APPLIED_EXTRA_PX] > 0);
            ChromeOcclusion styledChrome = chromeOcclusion(scenario);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] publisherHidden = awaitReaderComposition(
                scenario, styledChrome, false);
            assertChromeNativeStateUnchanged(
                atPublisher, publisherHidden);

            Bitmap publisherFrame = copyFrame(surface(scenario));
            assertNotNull(publisherFrame);
            long publisherPixels;
            int publisherGreenPixels;
            try {
                publisherPixels = framePixelHash(publisherFrame);
                publisherGreenPixels = countPixelsNear(
                    publisherFrame,
                    atPublisher,
                    Color.rgb(0x13, 0x7A, 0x43),
                    72);
                assertStyledRuntimeOverhang(
                    publisherFrame,
                    atPublisher,
                    OctavoDesignTokens.forAppearance(publisher),
                    LITERARY_LEFT_PROBE,
                    LITERARY_RIGHT_PROBE);
            } finally {
                publisherFrame.recycle();
            }
            assertTrue(
                "The styled evidence book exposed no publisher-color ink",
                publisherGreenPixels > 5);

            OctavoAppearance ragged =
                publisher.withAlignment(
                    OctavoAppearance.ALIGNMENT_RAGGED_RIGHT);
            long[] atRagged =
                requestAndAwaitAppearance(scenario, ragged);
            assertVisualOnlyAlignmentInvariants(
                atPublisher, atRagged);
            assertEquals(
                "Ragged-right changed the available justification plans",
                atPublisher[
                    OctavoSurfaceView.STATE_JUSTIFICATION_PLAN_COUNT],
                atRagged[
                    OctavoSurfaceView.STATE_JUSTIFICATION_PLAN_COUNT]);
            assertEquals(
                "Ragged-right activated justification rows",
                0,
                atRagged[
                    OctavoSurfaceView
                        .STATE_JUSTIFICATION_ACTIVE_ROW_COUNT]);
            assertEquals(
                "Ragged-right applied inter-word expansion",
                0,
                atRagged[
                    OctavoSurfaceView
                        .STATE_JUSTIFICATION_APPLIED_EXTRA_PX]);
            assertNotEquals(
                "Publisher and ragged-right retained the same "
                    + "justification semantic evidence",
                atPublisher[
                    OctavoSurfaceView.STATE_JUSTIFICATION_SEMANTIC_HASH],
                atRagged[
                    OctavoSurfaceView.STATE_JUSTIFICATION_SEMANTIC_HASH]);
            Bitmap raggedFrame = copyFrame(surface(scenario));
            assertNotNull(raggedFrame);
            long raggedPixels;
            try {
                raggedPixels = framePixelHash(raggedFrame);
            } finally {
                raggedFrame.recycle();
            }
            assertNotEquals(
                "Publisher and ragged-right alignment rendered "
                    + "identical styled-book pixels",
                publisherPixels,
                raggedPixels);

            OctavoAppearance themeSafe =
                ragged.withPublisherColors(
                    OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE);
            long[] atThemeSafe =
                requestAndAwaitAppearance(scenario, themeSafe);
            assertExactAPage(atRagged, atThemeSafe);
            assertLayoutGeometryUnchanged(atRagged, atThemeSafe);
            assertEquals(
                atRagged[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT],
                atThemeSafe[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            Bitmap themeSafeFrame = copyFrame(surface(scenario));
            assertNotNull(themeSafeFrame);
            long themeSafePixels;
            int safeGreenPixels;
            try {
                themeSafePixels = framePixelHash(themeSafeFrame);
                safeGreenPixels = countPixelsNear(
                    themeSafeFrame,
                    atThemeSafe,
                    Color.rgb(0x13, 0x7A, 0x43),
                    72);
            } finally {
                themeSafeFrame.recycle();
            }
            assertNotEquals(
                "Publisher color policy rendered identical pixels",
                raggedPixels,
                themeSafePixels);
            assertTrue(
                "Theme-safe policy did not suppress publisher-color ink",
                safeGreenPixels < publisherGreenPixels);

            long anchorSpine =
                atThemeSafe[
                    OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                atThemeSafe[
                    OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            OctavoAppearance clear =
                clearPublisher;
            long[] atClear =
                requestAndAwaitAppearance(scenario, clear);
            assertAnchorInsidePage(atClear, anchorSpine, anchorOffset);
            Bitmap clearFrame = copyFrame(surface(scenario));
            assertNotNull(clearFrame);
            try {
                assertNotEquals(
                    "Curated font families rendered identical pixels",
                    publisherPixels,
                    framePixelHash(clearFrame));
                assertStyledRuntimeOverhang(
                    clearFrame,
                    atClear,
                    OctavoDesignTokens.forAppearance(clear),
                    CLEAR_LEFT_PROBE,
                    CLEAR_RIGHT_PROBE);
            } finally {
                clearFrame.recycle();
            }
            assertTrue(
                atClear[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]
                    > initial[
                        OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
        }
        assertTrue(!styled.exists() || styled.delete());
    }

    @Test
    public void rapidPaperRoundTripAndDarkToOledBoundedSamplesStayDim()
        throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance paper =
            OctavoAppearance.defaults().withReducedMotion(true);
        assertTrue(new OctavoAppearanceStore(context).save(paper));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            long[] before = moveToNextPresentedPage(scenario);
            long anchorSpine =
                before[
                    OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                before[
                    OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            OctavoAppearance warmDark =
                paper.withTheme(OctavoAppearance.THEME_WARM_DARK);
            OctavoAppearance finalPaper =
                paper.withFontSizeSp(24);

            scenario.onActivity(activity -> {
                activity.requestAppearanceForTesting(paper);
                activity.requestAppearanceForTesting(warmDark);
                activity.requestAppearanceForTesting(finalPaper);
                assertTrue(
                    "Final same-theme request left a stale transition",
                    activity.findViewById(
                        R.id.octavo_appearance_transition) == null);
            });
            long[] coalesced = awaitPresentedAppearance(
                scenario,
                finalPaper,
                before[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1);
            assertEquals(
                before[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1,
                coalesced[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]);
            assertEquals(
                before[OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT] + 1,
                coalesced[
                    OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT]);
            assertEquals(
                before[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT] + 1,
                coalesced[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            assertEquals(
                before[OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT] + 1,
                coalesced[
                    OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT]);
            assertEquals(
                before[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT],
                coalesced[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
            assertLayoutPresentationHealthy(
                scenario,
                coalesced,
                finalPaper,
                anchorSpine,
                anchorOffset);
            awaitAppearanceTransitionRemoved(scenario);
            flushAndAssertAppearancePersisted(
                scenario, finalPaper);

            OctavoAppearance captureWarmDark = finalPaper.withTheme(
                OctavoAppearance.THEME_WARM_DARK);
            long[] atWarmDark = presentLayoutChoice(
                scenario,
                captureWarmDark,
                anchorSpine,
                anchorOffset,
                false);
            awaitAppearanceTransitionRemoved(scenario);

            OctavoAppearance oled = captureWarmDark.withTheme(
                OctavoAppearance.THEME_OLED);
            long minimumOledGeneration =
                atWarmDark[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1;
            AtomicReference<Rect> composedBounds = new AtomicReference<>();
            scenario.onActivity(activity -> {
                activity.requestAppearanceForTesting(oled);
                assertNotNull(
                    "OLED transition scrim was not installed",
                    activity.findViewById(
                        R.id.octavo_appearance_transition));
                Rect appBounds = new Rect();
                assertTrue(
                    "8vo's visible app window had no composed bounds",
                    activity.getWindow().getDecorView()
                        .getGlobalVisibleRect(appBounds));
                composedBounds.set(appBounds);
            });

            for (int frameIndex = 0;
                 frameIndex < 8;
                 ++frameIndex) {
                Bitmap composed =
                    InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation().takeScreenshot();
                assertNotNull(
                    "Composed transition screenshot " + frameIndex
                        + " was unavailable",
                    composed);
                try {
                    assertNoLargeBrightComposedFrame(
                        composed,
                        composedBounds.get(),
                        "theme switch " + frameIndex);
                } finally {
                    composed.recycle();
                }
                if (frameIndex + 1 < 8) {
                    SystemClock.sleep(20);
                }
            }

            long[] atOled = awaitPresentedAppearance(
                scenario, oled, minimumOledGeneration);
            assertEquals(
                atWarmDark[
                    OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT] + 1,
                atOled[
                    OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT]);
            assertEquals(
                atWarmDark[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT],
                atOled[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            assertLayoutPresentationHealthy(
                scenario,
                atOled,
                oled,
                anchorSpine,
                anchorOffset);
            awaitAppearanceTransitionRemoved(scenario);
            assertNoAppearanceTransition(scenario);
            flushAndAssertAppearancePersisted(scenario, oled);
        }
    }

    @Test
    public void fullViewportChromeCompositionIsNativeStateNeutral()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            awaitLocationCacheComplete(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            long[] initialHidden = moveToNextPresentedPage(scenario);
            long anchorSpine =
                initialHidden[
                    OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                initialHidden[
                    OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            ChromeOcclusion chrome = chromeOcclusion(scenario);
            initialHidden = awaitReaderComposition(
                scenario, chrome, false);
            assertCanonicalFullViewportLayout(chrome, initialHidden);
            assertWindowChromeCompositionFrame(
                scenario, initialHidden, false);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] visible = awaitReaderComposition(
                scenario, chrome, true);
            assertChromeNativeStateUnchanged(initialHidden, visible);
            assertEquals(
                initialHidden[
                    OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                visible[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertMeasuredChromeOcclusion(chrome, visible);
            assertCanonicalFullViewportLayout(chrome, visible);
            assertWindowChromeCompositionFrame(
                scenario, visible, true);

            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] hidden = awaitReaderComposition(
                scenario, chrome, false);
            assertChromeNativeStateUnchanged(visible, hidden);
            assertReaderChromeInsetsEqual(visible, hidden);
            assertEquals(
                visible[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                hidden[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertCanonicalFullViewportLayout(chrome, hidden);
            assertAnchorInsidePage(hidden, anchorSpine, anchorOffset);
            assertWindowChromeCompositionFrame(
                scenario, hidden, false);

            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] shown = awaitReaderComposition(
                scenario, chrome, true);
            assertChromeNativeStateUnchanged(hidden, shown);
            assertChromeNativeStateUnchanged(visible, shown);
            assertReaderChromeInsetsEqual(hidden, shown);
            assertEquals(
                hidden[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                shown[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertCanonicalFullViewportLayout(chrome, shown);
            assertAnchorInsidePage(shown, anchorSpine, anchorOffset);
            assertWindowChromeCompositionFrame(
                scenario, shown, true);

            AtomicReference<long[]> resizedInset = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.setReaderChromeInsets(
                    chrome.top + 1, chrome.bottom));
                resizedInset.set(view.nativeStateForTesting());
            });
            assertNotNull(resizedInset.get());
            long[] afterInset = resizedInset.get();
            assertEquals(
                chrome.top + 1,
                afterInset[
                    OctavoSurfaceView.STATE_READER_CHROME_INSET_TOP]);
            assertEquals(
                chrome.bottom,
                afterInset[
                    OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM]);
            assertChromeNativeStateUnchanged(shown, afterInset);
            assertAnchorInsidePage(afterInset, anchorSpine, anchorOffset);
            assertEquals(
                shown[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT],
                afterInset[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
            assertEquals(
                shown[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT],
                afterInset[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT],
                afterInset[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT],
                afterInset[
                    OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT],
                afterInset[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
            assertEquals(
                0,
                afterInset[OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
        }
    }


    @Test
    public void compactAndLargeRotationRestoreExactPresentedAnchor() {
        OctavoAppearance extreme = OctavoAppearance.create(
            OctavoAppearance.THEME_WARM_DARK,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            28,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            requestOrientation(
                scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            awaitConfigurationOrientation(
                scenario, Configuration.ORIENTATION_PORTRAIT);
            openFixture(scenario);
            awaitInitialPage(scenario);
            requestAndAwaitAppearance(scenario, extreme);
            long[] baseline = moveToNextPresentedPage(scenario);
            long anchorSpine =
                baseline[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                baseline[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            ViewportSize fullPortrait = new ViewportSize(
                (int)baseline[OctavoSurfaceView.STATE_WIDTH],
                (int)baseline[OctavoSurfaceView.STATE_HEIGHT]);
            assertViewportSettled(
                baseline, anchorSpine, anchorOffset);
            flushPosition(scenario);

            ViewportSize compactRequest =
                setReaderViewportDp(scenario, 320, 480);
            long[] compact = awaitViewport(
                scenario,
                compactRequest,
                baseline[OctavoSurfaceView.STATE_FRAME_COUNT],
                anchorSpine,
                anchorOffset,
                "8vo did not present the compact reader viewport");
            assertViewportSettled(
                compact, anchorSpine, anchorOffset);

            ViewportSize largeRequest =
                setReaderViewportDp(scenario, 600, 840);
            long[] large = awaitViewport(
                scenario,
                largeRequest,
                compact[OctavoSurfaceView.STATE_FRAME_COUNT],
                anchorSpine,
                anchorOffset,
                "8vo did not present the large reader viewport");
            assertViewportSettled(large, anchorSpine, anchorOffset);
            assertNotEquals(
                compact[OctavoSurfaceView.STATE_WIDTH],
                large[OctavoSurfaceView.STATE_WIDTH]);
            assertNotEquals(
                compact[OctavoSurfaceView.STATE_HEIGHT],
                large[OctavoSurfaceView.STATE_HEIGHT]);
            assertTrue(
                "Compact and large viewports did not repaginate",
                compact[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                    != large[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                || compact[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                    != large[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                || compact[
                    OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE]
                    != large[
                        OctavoSurfaceView
                            .STATE_PAGE_ONE_PAST_LAST_BYTE]);

            ViewportSize compactReturnRequest =
                setReaderViewportDp(scenario, 320, 480);
            long[] compactReturn = awaitViewport(
                scenario,
                compactReturnRequest,
                large[OctavoSurfaceView.STATE_FRAME_COUNT],
                anchorSpine,
                anchorOffset,
                "8vo did not restore the compact reader viewport");
            assertViewportSettled(
                compactReturn, anchorSpine, anchorOffset);
            assertExactViewportPage(compact, compactReturn);

            resetReaderViewport(scenario);
            long[] restoredFull = awaitViewport(
                scenario,
                fullPortrait,
                compactReturn[OctavoSurfaceView.STATE_FRAME_COUNT],
                anchorSpine,
                anchorOffset,
                "8vo did not restore the full portrait viewport");
            assertViewportSettled(
                restoredFull, anchorSpine, anchorOffset);
            assertExactViewportPage(baseline, restoredFull);

            long portraitAppearanceGeneration =
                restoredFull[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION];
            requestOrientation(
                scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            awaitConfigurationOrientation(
                scenario, Configuration.ORIENTATION_LANDSCAPE);
            long[] landscape = awaitRotatedViewport(
                scenario,
                false,
                restoredFull[OctavoSurfaceView.STATE_FRAME_COUNT],
                portraitAppearanceGeneration,
                anchorSpine,
                anchorOffset,
                "8vo did not restore the semantic anchor in landscape");
            assertViewportSettled(
                landscape, anchorSpine, anchorOffset);

            requestOrientation(
                scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            awaitConfigurationOrientation(
                scenario, Configuration.ORIENTATION_PORTRAIT);
            long[] portraitReturn = awaitRotatedViewport(
                scenario,
                true,
                landscape[OctavoSurfaceView.STATE_FRAME_COUNT],
                landscape[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION],
                anchorSpine,
                anchorOffset,
                "8vo did not restore the semantic anchor in portrait");
            assertViewportSettled(
                portraitReturn, anchorSpine, anchorOffset);
            assertExactViewportPage(baseline, portraitReturn);
            assertEquals(fullPortrait.width,
                         portraitReturn[OctavoSurfaceView.STATE_WIDTH]);
            assertEquals(fullPortrait.height,
                         portraitReturn[OctavoSurfaceView.STATE_HEIGHT]);
        }
    }

    @Test
    public void chromeCompositionTransitionCancelsGestureWithoutPageTurn()
        throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance reducedMotion =
            OctavoAppearance.defaults().withReducedMotion(true);
        assertTrue(
            new OctavoAppearanceStore(context).save(reducedMotion));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            awaitLocationCacheComplete(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            long[] hidden = moveToNextPresentedPage(scenario);
            ChromeOcclusion chrome = chromeOcclusion(scenario);
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] visible = awaitReaderComposition(
                scenario, chrome, true);
            assertChromeNativeStateUnchanged(hidden, visible);
            assertCanonicalFullViewportLayout(chrome, visible);

            AtomicReference<long[]> afterGesture = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view =
                    (OctavoSurfaceView)activity.findViewById(
                        R.id.octavo_surface);
                assertNotNull(view);
                long eventTime = SystemClock.uptimeMillis();
                float right = view.getWidth() * 5.0f / 6.0f;
                float centerY = view.getHeight() / 2.0f;
                dispatchTouchAction(
                    view,
                    MotionEvent.ACTION_DOWN,
                    right,
                    centerY,
                    eventTime,
                    eventTime);
                assertTrue(activity.setChromeVisibleForTesting(false));
                dispatchTouchAction(
                    view,
                    MotionEvent.ACTION_UP,
                    right,
                    centerY,
                    eventTime,
                    eventTime + 20);
                afterGesture.set(view.nativeStateForTesting());
            });
            assertNotNull(afterGesture.get());
            long[] after = afterGesture.get();
            assertChromeNativeStateUnchanged(visible, after);
            assertEquals(
                visible[OctavoSurfaceView.STATE_TOUCH_COUNT] + 3,
                after[OctavoSurfaceView.STATE_TOUCH_COUNT]);
            assertEquals(
                0, after[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertEquals(
                visible[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                after[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertEquals(
                visible[OctavoSurfaceView.STATE_TAP_INTENT_COUNT],
                after[OctavoSurfaceView.STATE_TAP_INTENT_COUNT]);
            assertEquals(
                visible[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT],
                after[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
            long[] hiddenAfterTransition = awaitReaderComposition(
                scenario, chrome, false);
            assertChromeNativeStateUnchanged(after, hiddenAfterTransition);
            assertWindowChromeCompositionFrame(
                scenario, hiddenAfterTransition, false);
        }
    }

    @Test
    public void layoutExtremaRoundTripAnchorAcrossReplacementAndRecreation() {
        OctavoAppearance appearanceA = OctavoAppearance.create(
            OctavoAppearance.THEME_PAPER,
            OctavoAppearance.FONT_FAMILY_LITERARY,
            16,
            1150,
            OctavoAppearance.MARGINS_WIDE,
            OctavoAppearance.ALIGNMENT_PUBLISHER,
            OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE,
            true);
        OctavoAppearance appearanceB = OctavoAppearance.create(
            OctavoAppearance.THEME_WARM_DARK,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            28,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            requestAndAwaitAppearance(scenario, appearanceA);

            long[] firstA = state(scenario);
            assertTrue("The fixture needs a second page at layout A",
                       firstA[OctavoSurfaceView.STATE_PAGE_COUNT] >= 2);
            long moveSuccessBefore =
                firstA[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT];
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.movePageForAccessibility(1));
            });
            long[] baselineA = awaitState(
                scenario,
                current ->
                    current[
                        OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                        == moveSuccessBefore + 1
                    && current[
                        OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                        == current[
                            OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                    && current[
                        OctavoSurfaceView
                            .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                    && current[OctavoSurfaceView.STATE_PAGE_INDEX] == 2,
                "8vo did not present layout A's second page");
            long anchorSpine =
                baselineA[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                baselineA[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            assertAnchorInsidePage(
                baselineA, anchorSpine, anchorOffset);
            assertNativeAppearance(baselineA, appearanceA);
            flushPosition(scenario);

            long generationBeforeCycles =
                baselineA[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION];
            long applyCountBeforeCycles =
                baselineA[
                    OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT];
            long reflowRequestsBeforeCycles =
                baselineA[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT];
            long reflowSuccessBeforeCycles =
                baselineA[OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT];

            long[] returnedA = baselineA;
            for (int cycle = 0; cycle < 8; ++cycle) {
                long[] atB =
                    requestAndAwaitAppearance(scenario, appearanceB);
                assertAnchorInsidePage(atB, anchorSpine, anchorOffset);
                if (cycle == 0) {
                    assertNotEquals(
                        baselineA[
                            OctavoSurfaceView.STATE_TYPOGRAPHY_TEXT_PX],
                        atB[OctavoSurfaceView.STATE_TYPOGRAPHY_TEXT_PX]);
                    assertNotEquals(
                        baselineA[
                            OctavoSurfaceView
                                .STATE_TYPOGRAPHY_LINE_ADVANCE_PX],
                        atB[
                            OctavoSurfaceView
                                .STATE_TYPOGRAPHY_LINE_ADVANCE_PX]);
                    assertNotEquals(
                        baselineA[
                            OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH],
                        atB[
                            OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH]);
                    assertTrue(
                        "Layout B did not materially repaginate layout A",
                        atB[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                            != baselineA[
                                OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]
                        || atB[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                            != baselineA[
                                OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                        || atB[
                            OctavoSurfaceView
                                .STATE_PAGE_ONE_PAST_LAST_BYTE]
                            != baselineA[
                                OctavoSurfaceView
                                    .STATE_PAGE_ONE_PAST_LAST_BYTE]);
                }

                returnedA =
                    requestAndAwaitAppearance(scenario, appearanceA);
                assertAnchorInsidePage(
                    returnedA, anchorSpine, anchorOffset);
                assertExactAPage(baselineA, returnedA);
                assertLayoutGeometryUnchanged(baselineA, returnedA);
            }

            assertEquals(
                generationBeforeCycles + 16,
                returnedA[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]);
            assertEquals(
                returnedA[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION],
                returnedA[
                    OctavoSurfaceView
                        .STATE_APPEARANCE_PRESENTED_GENERATION]);
            assertEquals(
                applyCountBeforeCycles + 16,
                returnedA[
                    OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT]);
            assertEquals(
                reflowRequestsBeforeCycles + 16,
                returnedA[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            assertEquals(
                reflowSuccessBeforeCycles + 16,
                returnedA[
                    OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT]);
            assertEquals(
                0,
                returnedA[
                    OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
            assertEquals(
                0,
                returnedA[
                    OctavoSurfaceView.STATE_APPEARANCE_FAILURE_COUNT]);
            assertHostAppearance(scenario, appearanceA);

            OctavoAppearance surfaceAppearance =
                appearanceA.withTheme(
                    OctavoAppearance.THEME_WARM_DARK);
            returnedA = requestAndAwaitAppearance(
                scenario, surfaceAppearance);
            assertExactAPage(baselineA, returnedA);
            assertLayoutGeometryUnchanged(baselineA, returnedA);
            flushPosition(scenario);

            long surfaceGeneration =
                returnedA[OctavoSurfaceView.STATE_SURFACE_GENERATION];
            long surfaceDestroyCount =
                returnedA[
                    OctavoSurfaceView.STATE_SURFACE_DESTROY_COUNT];
            long frameCount =
                returnedA[OctavoSurfaceView.STATE_FRAME_COUNT];
            long renderFailureCount =
                returnedA[
                    OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT];
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.forcePrePresentFailuresForTesting(2));
                view.replaceNativeSurfaceForTesting();
                long[] gated = view.nativeStateForTesting();
                assertNotNull(gated);
                assertEquals(
                    1,
                    gated[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING]);
                assertFalse(view.movePageForAccessibility(1));
            });
            assertNoBrightComposedFrames(
                scenario, "surface replacement", 4);
            long[] replaced = awaitState(
                scenario,
                current ->
                    current[
                        OctavoSurfaceView.STATE_SURFACE_GENERATION]
                        > surfaceGeneration
                    && current[
                        OctavoSurfaceView.STATE_SURFACE_DESTROY_COUNT]
                        > surfaceDestroyCount
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > frameCount
                    && appearanceMatchesState(surfaceAppearance, current)
                    && current[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                        == current[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_PRESENTED_GENERATION]
                    && current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == renderFailureCount + 2
                    && current[
                        OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING]
                        == 0
                    && current[
                        OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING]
                        == 0,
                "8vo did not retain layout A across surface replacement");
            assertNativeAppearance(
                replaced,
                surfaceAppearance,
                renderFailureCount + 2);
            assertAnchorInsidePage(
                replaced, anchorSpine, anchorOffset);
            assertExactAPage(baselineA, replaced);
            assertLayoutGeometryUnchanged(baselineA, replaced);
            assertHostAppearance(scenario, surfaceAppearance);

            OctavoAppearance abandonedAppearance =
                surfaceAppearance.withTheme(
                    OctavoAppearance.THEME_OLED);
            long terminalFrame =
                replaced[OctavoSurfaceView.STATE_FRAME_COUNT];
            long terminalFailureCount =
                replaced[
                    OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT];
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.forcePrePresentFailuresForTesting(8));
                view.replaceNativeSurfaceForTesting();
                activity.requestAppearanceForTesting(
                    abandonedAppearance);
                long[] gated = view.nativeStateForTesting();
                assertNotNull(gated);
                assertEquals(
                    1,
                    gated[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING]);
                assertFalse(view.movePageForAccessibility(1));
            });
            long[] exhausted = awaitState(
                scenario,
                current ->
                    current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == terminalFailureCount + 5
                    && current[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 1
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        == terminalFrame,
                "8vo did not terminate bounded presentation retries");
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertFalse(view.hasPendingAppearanceRequest());
                assertEquals(
                    surfaceAppearance,
                    view.presentedAppearanceForTesting());
                assertEquals(
                    surfaceAppearance,
                    activity.appearanceForTesting());
                assertNull(activity.findViewById(
                    R.id.octavo_appearance_transition));
                assertNotNull(activity.findViewById(
                    R.id.octavo_reader_failure));
                activity.requestAppearanceForTesting(abandonedAppearance);
            });
            long[] recoveredSurface = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > terminalFrame
                    && current[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 0
                    && current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == terminalFailureCount + 8
                    && appearanceMatchesState(
                        abandonedAppearance, current)
                    && current[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                        == current[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_PRESENTED_GENERATION],
                "8vo did not recover after a bounded retry terminal");
            assertNativeAppearance(
                recoveredSurface,
                abandonedAppearance,
                terminalFailureCount + 8);
            assertAnchorInsidePage(
                recoveredSurface, anchorSpine, anchorOffset);
            assertExactAPage(baselineA, recoveredSurface);
            assertLayoutGeometryUnchanged(
                baselineA, recoveredSurface);
            awaitAppearanceTransitionRemoved(scenario);
            assertNoAppearanceTransition(scenario);
            assertHostAppearance(scenario, abandonedAppearance);

            long acquisitionFrame = recoveredSurface[
                OctavoSurfaceView.STATE_FRAME_COUNT];
            long acquisitionSurfaceGeneration = recoveredSurface[
                OctavoSurfaceView.STATE_SURFACE_GENERATION];
            long acquisitionDestroyCount = recoveredSurface[
                OctavoSurfaceView.STATE_SURFACE_DESTROY_COUNT];
            long acquisitionFailureCount = recoveredSurface[
                OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT];
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(
                    view.forceSurfaceAcquisitionFailuresForTesting(1));
                view.replaceNativeSurfaceForTesting();
            });
            long[] acquisitionRejected = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_HAS_SURFACE] == 0
                    && current[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 1
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        == acquisitionFrame
                    && current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == acquisitionFailureCount + 1
                    && current[
                        OctavoSurfaceView.STATE_SURFACE_DESTROY_COUNT]
                        > acquisitionDestroyCount,
                "8vo did not expose the forced surface-acquisition failure");
            assertEquals(
                acquisitionSurfaceGeneration,
                acquisitionRejected[
                    OctavoSurfaceView.STATE_SURFACE_GENERATION]);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertFalse(view.movePageForAccessibility(1));
                assertEquals(
                    "Unable to acquire reader surface; reopen the book",
                    activity.lastOpenErrorForTesting());
                assertNotNull(activity.findViewById(
                    R.id.octavo_reader_failure));
                view.replaceNativeSurfaceForTesting();
            });
            long[] acquisitionRecovered = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                    && current[
                        OctavoSurfaceView.STATE_SURFACE_GENERATION]
                        > acquisitionSurfaceGeneration
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > acquisitionFrame
                    && current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == acquisitionFailureCount + 1
                    && current[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 0
                    && appearanceMatchesState(
                        abandonedAppearance, current),
                "8vo did not recover on the next valid surface event");
            assertNativeAppearance(
                acquisitionRecovered,
                abandonedAppearance,
                acquisitionFailureCount + 1);
            assertAnchorInsidePage(
                acquisitionRecovered, anchorSpine, anchorOffset);
            assertExactAPage(baselineA, acquisitionRecovered);
            assertLayoutGeometryUnchanged(
                baselineA, acquisitionRecovered);

            flushPosition(scenario);
            scenario.recreate();
            assertNoBrightComposedFrames(
                scenario, "activity recreation", 4);
            long[] recreated = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_RESUMED] == 1
                    && current[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && current[
                        OctavoSurfaceView.STATE_RESTORE_SUCCEEDED] == 1
                    && appearanceMatchesState(abandonedAppearance, current)
                    && current[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                        == current[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_PRESENTED_GENERATION]
                    && current[
                        OctavoSurfaceView
                            .STATE_REFLOW_PRESENTATION_PENDING] == 0,
                "8vo did not retain layout A across Activity recreation");
            assertNativeAppearance(recreated, abandonedAppearance);
            assertEquals(
                1,
                recreated[OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertEquals(
                1,
                recreated[OctavoSurfaceView.STATE_RESTORE_ATTEMPTED]);
            assertEquals(
                1,
                recreated[OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(
                0,
                recreated[
                    OctavoSurfaceView.STATE_RESTORE_FAILURE_COUNT]);
            assertAnchorInsidePage(
                recreated, anchorSpine, anchorOffset);
            assertExactAPage(baselineA, recreated);
            assertLayoutGeometryUnchanged(baselineA, recreated);
            assertHostAppearance(scenario, abandonedAppearance);
            Context context = ApplicationProvider.getApplicationContext();
            assertEquals(
                abandonedAppearance,
                new OctavoAppearanceStore(context).load());

            long savesBeforeRollback =
                flushAndReadSaveSuccessCount(scenario);
            long[] beforeReapplyFailure = state(scenario);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.forcePresentFailuresForTesting(1));
                view.reapplyAppearance();
            });
            long[] reapplyRollback = awaitState(
                scenario,
                current ->
                    current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == beforeReapplyFailure[
                            OctavoSurfaceView
                                .STATE_RENDER_FAILURE_COUNT] + 1
                    && current[
                        OctavoSurfaceView
                            .STATE_APPEARANCE_FAILURE_COUNT]
                        == beforeReapplyFailure[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_FAILURE_COUNT] + 1
                    && current[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                        == current[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_PRESENTED_GENERATION]
                    && current[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 0
                    && appearanceMatchesState(
                        abandonedAppearance, current),
                "8vo falsely accepted or failed to settle a failed reapply");
            assertTrue(
                reapplyRollback[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    >= beforeReapplyFailure[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 2);
            SystemClock.sleep(160);
            assertEquals(
                savesBeforeRollback,
                flushAndReadSaveSuccessCount(scenario));
            assertHostAppearanceWithoutFlush(
                scenario, abandonedAppearance);

            OctavoAppearance reducedMotionOnly =
                abandonedAppearance.withReducedMotion(
                    !abandonedAppearance.reducedMotion());
            long[] beforeReducedFailure = state(scenario);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.forcePresentFailuresForTesting(1));
                activity.requestAppearanceForTesting(
                    reducedMotionOnly);
            });
            long[] reducedRollback = awaitState(
                scenario,
                current ->
                    current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == beforeReducedFailure[
                            OctavoSurfaceView
                                .STATE_RENDER_FAILURE_COUNT] + 1
                    && current[
                        OctavoSurfaceView
                            .STATE_APPEARANCE_FAILURE_COUNT]
                        == beforeReducedFailure[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_FAILURE_COUNT] + 1
                    && current[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                        == current[
                            OctavoSurfaceView
                                .STATE_APPEARANCE_PRESENTED_GENERATION]
                    && current[
                        OctavoSurfaceView.STATE_REDUCED_MOTION]
                        == (abandonedAppearance.reducedMotion() ? 1 : 0)
                    && appearanceMatchesState(
                        abandonedAppearance, current),
                "8vo persisted a failed reduced-motion-only appearance");
            assertFalse(appearanceMatchesState(
                reducedMotionOnly, reducedRollback));
            SystemClock.sleep(160);
            assertEquals(
                savesBeforeRollback,
                flushAndReadSaveSuccessCount(scenario));
            assertHostAppearanceWithoutFlush(
                scenario, abandonedAppearance);
        }
    }

    private static long[] presentLayoutChoice(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected,
        long anchorSpine,
        long anchorOffset,
        boolean captureReadablePixels)
        throws InterruptedException {
        long[] before = state(scenario);
        boolean alreadyPresented =
            before[OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                == before[
                    OctavoSurfaceView
                        .STATE_APPEARANCE_PRESENTED_GENERATION]
            && appearanceMatchesState(expected, before);
        long minimumGeneration =
            before[OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
            + (alreadyPresented ? 0 : 1);
        scenario.onActivity(activity ->
            activity.requestAppearanceForTesting(expected));
        long[] presented = awaitPresentedAppearance(
            scenario, expected, minimumGeneration);
        assertLayoutPresentationHealthy(
            scenario,
            presented,
            expected,
            anchorSpine,
            anchorOffset);
        if (captureReadablePixels) {
            Bitmap frame = copyFrame(surface(scenario));
            assertNotNull(frame);
            try {
                assertContrastingReaderInk(
                    presented,
                    frame,
                    OctavoDesignTokens.forAppearance(expected));
                assertReaderInkHasUnclippedVerticalEdges(
                    presented,
                    frame,
                    OctavoDesignTokens.forAppearance(expected));
            } finally {
                frame.recycle();
            }
        }
        return presented;
    }

    private static long[] awaitPresentedAppearance(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected,
        long minimumGeneration) {
        long[] presented = awaitState(
            scenario,
            current ->
                current[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    >= minimumGeneration
                && current[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    == current[
                        OctavoSurfaceView
                            .STATE_APPEARANCE_PRESENTED_GENERATION]
                && current[
                    OctavoSurfaceView
                        .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && current[
                    OctavoSurfaceView
                        .STATE_REFLOW_PRESENTATION_PENDING] == 0
                && appearanceMatchesState(expected, current),
            "8vo did not present the requested layout appearance");
        assertNativeAppearance(presented, expected);
        scenario.onActivity(activity -> {
            assertEquals(expected, activity.appearanceForTesting());
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(
                    R.id.octavo_surface);
            assertNotNull(view);
            assertEquals(
                expected, view.presentedAppearanceForTesting());
        });
        return presented;
    }

    private static void assertLayoutPresentationHealthy(
        ActivityScenario<OctavoActivity> scenario,
        long[] presented,
        OctavoAppearance expected,
        long anchorSpine,
        long anchorOffset) {
        assertNativeAppearance(presented, expected);
        assertViewportSettled(
            presented, anchorSpine, anchorOffset);
        assertMeasuredChromeOcclusion(
            chromeOcclusion(scenario), presented);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView
                    .STATE_PAGE_MOVE_PRESENTATION_PENDING]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView
                    .STATE_REFLOW_PRESENTATION_PENDING]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_APPEARANCE_FAILURE_COUNT]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
        assertEquals(
            0,
            presented[
                OctavoSurfaceView.STATE_READER_VIEW_ERRORS]);
    }

    private static void assertBaselineLayoutRoundTrip(
        long[] baseline,
        long[] returned) {
        assertExactAPage(baseline, returned);
        assertLayoutGeometryUnchanged(baseline, returned);
        assertReaderGeometryUnchanged(baseline, returned);
    }

    private static void flushAndAssertAppearancePersisted(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        scenario.onActivity(activity -> {
            activity.flushAppearancePersistenceForTesting();
            assertEquals(expected, activity.appearanceForTesting());
            assertEquals(
                expected,
                activity.appearanceStoreForTesting().current());
        });
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(
            expected, new OctavoAppearanceStore(context).load());
    }

    private static void awaitAppearanceTransitionRemoved(
        ActivityScenario<OctavoActivity> scenario) {
        for (int attempt = 0; attempt < 180; ++attempt) {
            AtomicReference<Boolean> removed =
                new AtomicReference<>(false);
            scenario.onActivity(activity -> removed.set(
                activity.findViewById(
                    R.id.octavo_appearance_transition) == null));
            if (Boolean.TRUE.equals(removed.get())) {
                return;
            }
            SystemClock.sleep(16);
        }
        fail("8vo did not remove its appearance-transition scrim");
    }

    private static void awaitReaderEntryCoverRemoved(
        ActivityScenario<OctavoActivity> scenario) {
        for (int attempt = 0; attempt < 180; ++attempt) {
            AtomicReference<Boolean> removed =
                new AtomicReference<>(false);
            scenario.onActivity(activity -> removed.set(
                activity.findViewById(
                    R.id.octavo_reader_entry_cover) == null));
            if (Boolean.TRUE.equals(removed.get())) {
                return;
            }
            SystemClock.sleep(16);
        }
        fail("8vo did not remove its successful reader-entry cover");
    }

    private static void assertNoAppearanceTransition(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity ->
            assertTrue(
                "Appearance-transition view remained attached",
                activity.findViewById(
                    R.id.octavo_appearance_transition) == null));
    }

    private static void assertNoLargeBrightComposedFrame(
        Bitmap frame,
        Rect requestedBounds,
        String frameLabel) {
        final int sampleStride = 4;
        final int nearWhiteLimitPermille = 50;
        final int brightLimitPermille = 120;
        assertNotNull("8vo's composed app bounds were unavailable",
                      requestedBounds);
        int left = Math.max(0, requestedBounds.left);
        int top = Math.max(0, requestedBounds.top);
        int right = Math.min(frame.getWidth(), requestedBounds.right);
        int bottom = Math.min(frame.getHeight(), requestedBounds.bottom);
        assertTrue("8vo's composed app bounds were empty", right > left);
        assertTrue("8vo's composed app bounds were empty", bottom > top);
        long sampleCount = 0;
        long nearWhiteCount = 0;
        long brightCount = 0;
        for (int y = top; y < bottom; y += sampleStride) {
            for (int x = left; x < right; x += sampleStride) {
                int pixel = frame.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                int luminance =
                    (54 * red + 183 * green + 19 * blue) >> 8;
                ++sampleCount;
                if (red >= 240 && green >= 240 && blue >= 240) {
                    ++nearWhiteCount;
                }
                if (luminance >= 224) {
                    ++brightCount;
                }
            }
        }
        assertTrue(sampleCount > 0);
        long nearWhitePermille =
            nearWhiteCount * 1000L / sampleCount;
        long brightPermille =
            brightCount * 1000L / sampleCount;
        assertTrue(
            "Composed " + frameLabel
                + " was near-white across "
                + nearWhitePermille + " permille",
            nearWhitePermille <= nearWhiteLimitPermille);
        assertTrue(
            "Composed " + frameLabel
                + " was bright across "
                + brightPermille + " permille",
            brightPermille <= brightLimitPermille);
    }

    private static void assertNoBrightComposedFrames(
        ActivityScenario<OctavoActivity> scenario,
        String phase,
        int frameCount) {
        AtomicReference<Rect> bounds = new AtomicReference<>();
        scenario.onActivity(activity -> {
            Rect visible = new Rect();
            assertTrue(
                phase + " had no visible app bounds",
                activity.getWindow().getDecorView()
                    .getGlobalVisibleRect(visible));
            bounds.set(visible);
        });
        for (int index = 0; index < frameCount; ++index) {
            Bitmap composed =
                InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().takeScreenshot();
            assertNotNull(
                phase + " screenshot " + index + " was unavailable",
                composed);
            try {
                assertNoLargeBrightComposedFrame(
                    composed, bounds.get(), phase + " sample " + index);
            } finally {
                composed.recycle();
            }
            if (index + 1 < frameCount) {
                SystemClock.sleep(20);
            }
        }
    }

    private static long[] moveToNextPresentedPage(
        ActivityScenario<OctavoActivity> scenario) {
        long[] before = state(scenario);
        assertTrue(
            "The fixture needs a second page",
            before[OctavoSurfaceView.STATE_PAGE_COUNT] >= 2);
        long successBefore =
            before[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT];
        long presentedBefore =
            before[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT];
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(
                    R.id.octavo_surface);
            assertNotNull(view);
            assertTrue(view.movePageForAccessibility(1));
        });
        return awaitState(
            scenario,
            current ->
                current[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                    == successBefore + 1
                && current[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                    == presentedBefore + 1
                && current[
                    OctavoSurfaceView
                        .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && current[
                    OctavoSurfaceView
                        .STATE_REFLOW_PRESENTATION_PENDING] == 0,
            "8vo did not present the fixture's next page");
    }

    private static ChromeOcclusion chromeOcclusion(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<ChromeOcclusion> result =
            new AtomicReference<>();
        scenario.onActivity(activity -> {
            View surface =
                activity.findViewById(R.id.octavo_surface);
            View topChrome =
                activity.findViewById(R.id.octavo_reader_top_chrome);
            View bottomChrome =
                activity.findViewById(R.id.octavo_reader_bottom_chrome);
            assertNotNull(surface);
            assertNotNull(topChrome);
            assertNotNull(bottomChrome);
            int width = surface.getWidth();
            int height = surface.getHeight();
            int top = Math.max(
                0,
                Math.min(
                    height,
                    topChrome.getBottom() - surface.getTop()));
            int bottom = Math.max(
                0,
                Math.min(
                    height - top,
                    surface.getBottom() - bottomChrome.getTop()));
            result.set(new ChromeOcclusion(
                width,
                height,
                top,
                bottom,
                topChrome.getHeight(),
                bottomChrome.getHeight()));
        });
        assertNotNull(result.get());
        return result.get();
    }

    private static void assertMeasuredChromeOcclusion(
        ChromeOcclusion occlusion,
        long[] snapshot) {
        assertTrue(occlusion.width > 0);
        assertTrue(occlusion.height > 0);
        assertTrue(occlusion.top > 0);
        assertTrue(occlusion.bottom > 0);
        assertTrue(occlusion.top + occlusion.bottom < occlusion.height);
        assertEquals(occlusion.topViewHeight, occlusion.top);
        assertEquals(occlusion.bottomViewHeight, occlusion.bottom);
        assertEquals(
            occlusion.width,
            snapshot[OctavoSurfaceView.STATE_WIDTH]);
        assertEquals(
            occlusion.height,
            snapshot[OctavoSurfaceView.STATE_HEIGHT]);
        assertEquals(
            occlusion.top,
            snapshot[
                OctavoSurfaceView.STATE_READER_CHROME_INSET_TOP]);
        assertEquals(
            occlusion.bottom,
            snapshot[
                OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM]);

        assertCanonicalFullViewportLayout(occlusion, snapshot);
    }

    private static long[] awaitReaderComposition(
        ActivityScenario<OctavoActivity> scenario,
        ChromeOcclusion chrome,
        boolean visible) {
        AtomicReference<long[]> snapshot = new AtomicReference<>();
        AtomicReference<Boolean> settled =
            new AtomicReference<>(false);
        for (int attempt = 0; attempt < 180; ++attempt) {
            scenario.onActivity(activity -> {
                OctavoSurfaceView surface = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                View top = activity.findViewById(
                    R.id.octavo_reader_top_chrome);
                View bottom = activity.findViewById(
                    R.id.octavo_reader_bottom_chrome);
                assertNotNull(surface);
                assertNotNull(top);
                assertNotNull(bottom);
                long[] state = surface.nativeStateForTesting();
                snapshot.set(state);

                int availableHeight = Math.max(
                    chrome.height - chrome.top - chrome.bottom, 1);
                float expectedScale = visible
                    ? Math.min(
                        1.0f,
                        availableHeight / (float)chrome.height)
                    : 1.0f;
                float expectedTranslationX = visible
                    ? (chrome.width
                       - chrome.width * expectedScale) / 2.0f
                    : 0.0f;
                float expectedTranslationY = visible
                    ? chrome.top
                        + (availableHeight
                           - chrome.height * expectedScale) / 2.0f
                    : 0.0f;
                boolean chromeViewsSettled = visible
                    ? top.getVisibility() == View.VISIBLE
                        && bottom.getVisibility() == View.VISIBLE
                        && near(top.getAlpha(), 1.0f, 0.02f)
                        && near(bottom.getAlpha(), 1.0f, 0.02f)
                    : top.getVisibility() == View.INVISIBLE
                        && bottom.getVisibility() == View.INVISIBLE;
                settled.set(
                    state != null
                    && state[OctavoSurfaceView.STATE_CHROME_VISIBLE]
                        == (visible ? 1 : 0)
                    && chromeViewsSettled
                    && near(surface.getPivotX(), 0.0f, 0.01f)
                    && near(surface.getPivotY(), 0.0f, 0.01f)
                    && near(
                        surface.getScaleX(), expectedScale, 0.002f)
                    && near(
                        surface.getScaleY(), expectedScale, 0.002f)
                    && near(
                        surface.getTranslationX(),
                        expectedTranslationX,
                        0.75f)
                    && near(
                        surface.getTranslationY(),
                        expectedTranslationY,
                        0.75f));
            });
            if (Boolean.TRUE.equals(settled.get())) {
                assertNotNull(snapshot.get());
                return snapshot.get();
            }
            SystemClock.sleep(16);
        }
        fail("8vo did not settle the "
             + (visible ? "visible" : "hidden")
             + " reader chrome composition");
        return new long[0];
    }

    private static boolean near(float actual,
                                float expected,
                                float tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void assertCanonicalFullViewportLayout(
        ChromeOcclusion chrome,
        long[] snapshot) {
        int width = (int)snapshot[OctavoSurfaceView.STATE_WIDTH];
        int height = (int)snapshot[OctavoSurfaceView.STATE_HEIGHT];
        int pageX =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_X];
        int pageY =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_Y];
        int pageWidth =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
        int pageHeight =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT];
        int contentX =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_X];
        int contentY =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_Y];
        int contentWidth =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_WIDTH];
        int contentHeight =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_HEIGHT];

        assertEquals(chrome.width, width);
        assertEquals(chrome.height, height);
        assertTrue(pageX >= 0);
        assertTrue(pageWidth > 0);
        assertTrue(pageX + pageWidth <= width);
        assertEquals(
            "Native page did not begin at the canonical viewport top",
            0,
            pageY);
        assertEquals(
            "Native page did not retain the full viewport height",
            height,
            pageHeight);
        assertTrue(contentX >= pageX);
        assertTrue(contentWidth > 0);
        assertTrue(contentX + contentWidth <= pageX + pageWidth);
        assertTrue(contentY > pageY);
        assertTrue(contentHeight > 0);
        assertTrue(contentY + contentHeight < pageY + pageHeight);
        assertEquals(
            "Canonical reader content lost symmetric vertical insets",
            contentY - pageY,
            pageY + pageHeight - contentY - contentHeight);
        assertTrue(
            "Native pagination still reserved the top chrome band",
            contentY < chrome.top);
        assertTrue(
            "Native pagination still reserved the bottom chrome band",
            contentY + contentHeight
                > height - chrome.bottom);
    }

    private static void assertWindowChromeCompositionFrame(
        ActivityScenario<OctavoActivity> scenario,
        long[] snapshot,
        boolean chromeVisible) {
        AtomicReference<Rect> surfaceBounds = new AtomicReference<>();
        AtomicReference<Rect> topBounds = new AtomicReference<>();
        AtomicReference<Rect> bottomBounds = new AtomicReference<>();
        scenario.onActivity(activity -> {
            View surface = activity.findViewById(R.id.octavo_surface);
            View top = activity.findViewById(
                R.id.octavo_reader_top_chrome);
            View bottom = activity.findViewById(
                R.id.octavo_reader_bottom_chrome);
            assertNotNull(surface);
            assertNotNull(top);
            assertNotNull(bottom);
            Rect surfaceRect = new Rect();
            assertTrue(surface.getGlobalVisibleRect(surfaceRect));
            surfaceBounds.set(surfaceRect);
            Rect topRect = new Rect();
            Rect bottomRect = new Rect();
            if (chromeVisible) {
                assertTrue(top.isShown());
                assertTrue(bottom.isShown());
                assertTrue(top.getGlobalVisibleRect(topRect));
                assertTrue(bottom.getGlobalVisibleRect(bottomRect));
            } else {
                assertFalse(top.isShown());
                assertFalse(bottom.isShown());
            }
            topBounds.set(topRect);
            bottomBounds.set(bottomRect);
        });

        Bitmap composed = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation().takeScreenshot();
        assertNotNull(composed);
        try {
            OctavoDesignTokens tokens = OctavoDesignTokens.forAppearance(
                appearanceFromState(snapshot));
            if (chromeVisible) {
                assertRegionContainsColor(
                    composed,
                    topBounds.get(),
                    tokens.chromeSurface,
                    "top chrome");
                assertRegionContainsColor(
                    composed,
                    bottomBounds.get(),
                    tokens.chromeSurface,
                    "bottom chrome");
            } else {
                Rect surfaceRect = surfaceBounds.get();
                assertNotNull(surfaceRect);
                assertContrastingReaderInk(
                    snapshot,
                    composed,
                    tokens,
                    surfaceRect.left,
                    surfaceRect.top);
            }
        } finally {
            composed.recycle();
        }
    }

    private static void assertRegionContainsColor(Bitmap frame,
                                                  Rect region,
                                                  int expected,
                                                  String label) {
        assertNotNull(region);
        int left = Math.max(0, region.left);
        int top = Math.max(0, region.top);
        int right = Math.min(frame.getWidth(), region.right);
        int bottom = Math.min(frame.getHeight(), region.bottom);
        assertTrue(label + " region was empty", left < right && top < bottom);
        int matches = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int y = top; y < bottom; y += 2) {
            for (int x = left; x < right; x += 2) {
                int pixel = frame.getPixel(x, y);
                int distance =
                    Math.abs(Color.red(pixel) - Color.red(expected))
                    + Math.abs(Color.green(pixel) - Color.green(expected))
                    + Math.abs(Color.blue(pixel) - Color.blue(expected));
                nearestDistance = Math.min(nearestDistance, distance);
                if (distance <= 12) {
                    matches += 1;
                }
            }
        }
        int fullFrameMatches = 0;
        if (matches <= 8) {
            for (int y = 0; y < frame.getHeight(); y += 4) {
                for (int x = 0; x < frame.getWidth(); x += 4) {
                    if (pixelNear(frame.getPixel(x, y), expected, 12)) {
                        fullFrameMatches += 1;
                    }
                }
            }
        }
        assertTrue(
            label + " was absent from the composed window capture "
                + "region=" + region
                + " frame=" + frame.getWidth() + "x" + frame.getHeight()
                + " nearest=" + nearestDistance
                + " fullMatches=" + fullFrameMatches,
            matches > 8);
    }

    private static void assertChromeNativeStateUnchanged(
        long[] expected,
        long[] actual) {
        int[] fields = {
            OctavoSurfaceView.STATE_WIDTH,
            OctavoSurfaceView.STATE_HEIGHT,
            OctavoSurfaceView.STATE_FRAME_COUNT,
            OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT,
            OctavoSurfaceView.STATE_READER_INITIALIZED,
            OctavoSurfaceView.STATE_DOCUMENT_OPEN,
            OctavoSurfaceView.STATE_READER_FRAME_READY,
            OctavoSurfaceView.STATE_VISIBLE_TEXT_SIZE,
            OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH,
            OctavoSurfaceView.STATE_PAGE_INDEX,
            OctavoSurfaceView.STATE_PAGE_COUNT,
            OctavoSurfaceView.STATE_READER_VIEW_READY,
            OctavoSurfaceView.STATE_READER_VIEW_ERRORS,
            OctavoSurfaceView.STATE_READER_VIEW_DRAW_COUNT,
            OctavoSurfaceView.STATE_PAGE_SURFACE_X,
            OctavoSurfaceView.STATE_PAGE_SURFACE_Y,
            OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH,
            OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT,
            OctavoSurfaceView.STATE_PROGRESS_PAGE_INDEX,
            OctavoSurfaceView.STATE_PROGRESS_PAGE_COUNT,
            OctavoSurfaceView.STATE_TAP_INTENT_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT,
            OctavoSurfaceView.STATE_SPINE_INDEX,
            OctavoSurfaceView.STATE_SECTION_COUNT,
            OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX,
            OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT,
            OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX,
            OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET,
            OctavoSurfaceView.STATE_DOCUMENT_GENERATION,
            OctavoSurfaceView.STATE_APPEARANCE_GENERATION,
            OctavoSurfaceView.STATE_APPEARANCE_PRESENTED_GENERATION,
            OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT,
            OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT,
            OctavoSurfaceView.STATE_APPEARANCE_FAILURE_COUNT,
            OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT,
            OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT,
            OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT,
            OctavoSurfaceView.STATE_PAGE_FIRST_BYTE,
            OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE,
            OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_CONTENT_X,
            OctavoSurfaceView.STATE_CONTENT_Y,
            OctavoSurfaceView.STATE_CONTENT_WIDTH,
            OctavoSurfaceView.STATE_CONTENT_HEIGHT,
            OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_JUSTIFICATION_PLAN_COUNT,
            OctavoSurfaceView.STATE_JUSTIFICATION_ACTIVE_ROW_COUNT,
            OctavoSurfaceView.STATE_JUSTIFICATION_APPLIED_EXTRA_PX,
            OctavoSurfaceView.STATE_JUSTIFICATION_SEMANTIC_HASH,
        };
        for (int field : fields) {
            assertEquals(
                "Chrome composition changed native reader field " + field,
                expected[field],
                actual[field]);
        }
    }

    private static void assertReaderChromeInsetsEqual(
        long[] expected,
        long[] actual) {
        assertEquals(
            expected[OctavoSurfaceView.STATE_READER_CHROME_INSET_TOP],
            actual[OctavoSurfaceView.STATE_READER_CHROME_INSET_TOP]);
        assertEquals(
            expected[OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM],
            actual[OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM]);
    }

    private static OctavoAppearance appearanceFromState(
        long[] snapshot) {
        return OctavoAppearance.create(
            (int)snapshot[OctavoSurfaceView.STATE_THEME],
            (int)snapshot[OctavoSurfaceView.STATE_FONT_FAMILY],
            (int)snapshot[OctavoSurfaceView.STATE_FONT_SIZE_SP],
            (int)snapshot[
                OctavoSurfaceView.STATE_LINE_SPACING_PERMILLE],
            (int)snapshot[OctavoSurfaceView.STATE_MARGIN],
            (int)snapshot[OctavoSurfaceView.STATE_ALIGNMENT],
            (int)snapshot[
                OctavoSurfaceView.STATE_PUBLISHER_COLORS],
            true);
    }

    private static ViewportSize setReaderViewportDp(
        ActivityScenario<OctavoActivity> scenario,
        int widthDp,
        int heightDp) {
        AtomicReference<ViewportSize> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            FrameLayout root =
                (FrameLayout)activity.findViewById(
                    R.id.octavo_reader_root);
            assertNotNull(root);
            float density =
                activity.getResources().getDisplayMetrics().density;
            int width = Math.max(1, Math.round(widthDp * density));
            int height = Math.max(1, Math.round(heightDp * density));
            root.setLayoutParams(new FrameLayout.LayoutParams(
                width, height, Gravity.CENTER));
            root.requestLayout();
            result.set(new ViewportSize(
                Math.max(
                    1,
                    width - root.getPaddingLeft()
                        - root.getPaddingRight()),
                Math.max(
                    1,
                    height - root.getPaddingTop()
                        - root.getPaddingBottom())));
        });
        assertNotNull(result.get());
        return result.get();
    }

    private static void resetReaderViewport(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            FrameLayout root =
                (FrameLayout)activity.findViewById(
                    R.id.octavo_reader_root);
            assertNotNull(root);
            root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
            root.requestLayout();
        });
    }

    private static long[] awaitViewport(
        ActivityScenario<OctavoActivity> scenario,
        ViewportSize target,
        long frameBefore,
        long anchorSpine,
        long anchorOffset,
        String failureMessage) {
        return awaitState(
            scenario,
            current ->
                current[OctavoSurfaceView.STATE_WIDTH] == target.width
                && current[OctavoSurfaceView.STATE_HEIGHT] == target.height
                && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                    > frameBefore
                && isPresentedAtAnchor(
                    current, anchorSpine, anchorOffset),
            failureMessage);
    }

    private static long[] awaitRotatedViewport(
        ActivityScenario<OctavoActivity> scenario,
        boolean portrait,
        long frameBefore,
        long appearanceGenerationBefore,
        long anchorSpine,
        long anchorOffset,
        String failureMessage) {
        return awaitState(
            scenario,
            current ->
                (portrait
                    ? current[OctavoSurfaceView.STATE_HEIGHT]
                        > current[OctavoSurfaceView.STATE_WIDTH]
                    : current[OctavoSurfaceView.STATE_WIDTH]
                        > current[OctavoSurfaceView.STATE_HEIGHT])
                && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                    > frameBefore
                && current[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    > appearanceGenerationBefore
                && isPresentedAtAnchor(
                    current, anchorSpine, anchorOffset),
            failureMessage);
    }

    private static boolean isPresentedAtAnchor(
        long[] snapshot,
        long anchorSpine,
        long anchorOffset) {
        return snapshot[OctavoSurfaceView.STATE_RESUMED] == 1
            && snapshot[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
            && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
            && snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
            && snapshot[
                OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]
                == anchorSpine
            && snapshot[
                OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]
                == anchorOffset
            && snapshot[
                OctavoSurfaceView
                    .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
            && snapshot[
                OctavoSurfaceView
                    .STATE_REFLOW_PRESENTATION_PENDING] == 0
            && snapshot[
                OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING] == 0
            && snapshot[
                OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                == snapshot[
                    OctavoSurfaceView
                        .STATE_APPEARANCE_PRESENTED_GENERATION];
    }

    private static void assertViewportSettled(
        long[] snapshot,
        long anchorSpine,
        long anchorOffset) {
        assertAnchorInsidePage(snapshot, anchorSpine, anchorOffset);
        int width = (int)snapshot[OctavoSurfaceView.STATE_WIDTH];
        int height = (int)snapshot[OctavoSurfaceView.STATE_HEIGHT];
        int pageX =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_X];
        int pageY =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_Y];
        int pageWidth =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
        int pageHeight =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT];
        int contentX =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_X];
        int contentY =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_Y];
        int contentWidth =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_WIDTH];
        int contentHeight =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_HEIGHT];
        int top =
            (int)snapshot[
                OctavoSurfaceView.STATE_READER_CHROME_INSET_TOP];
        int bottom =
            (int)snapshot[
                OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM];

        assertTrue(width > 0);
        assertTrue(height > 0);
        assertTrue(pageX >= 0);
        assertEquals(0, pageY);
        assertTrue(pageWidth > 0);
        assertEquals(height, pageHeight);
        assertTrue(pageX + pageWidth <= width);
        assertEquals(height, pageY + pageHeight);
        assertTrue(contentX >= pageX);
        assertTrue(contentY >= pageY);
        assertTrue(contentWidth > 0);
        assertTrue(contentHeight > 0);
        assertTrue(contentX + contentWidth <= pageX + pageWidth);
        assertTrue(contentY + contentHeight <= pageY + pageHeight);
        assertTrue(top > 0);
        assertTrue(bottom > 0);
        assertEquals(
            contentY - pageY,
            pageY + pageHeight - contentY - contentHeight);
        assertTrue(contentY < top);
        assertTrue(contentY + contentHeight > height - bottom);
        assertEquals(
            snapshot[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT],
            snapshot[OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT]);
        assertEquals(
            snapshot[
                OctavoSurfaceView.STATE_APPEARANCE_GENERATION],
            snapshot[
                OctavoSurfaceView
                    .STATE_APPEARANCE_PRESENTED_GENERATION]);
        assertEquals(
            0,
            snapshot[
                OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING]);
        assertEquals(
            0,
            snapshot[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
        assertEquals(
            0,
            snapshot[OctavoSurfaceView.STATE_READER_VIEW_ERRORS]);
        assertEquals(
            0,
            snapshot[OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT]);
        assertEquals(
            0,
            snapshot[OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
        assertEquals(
            0,
            snapshot[
                OctavoSurfaceView.STATE_APPEARANCE_FAILURE_COUNT]);
    }

    private static void assertExactViewportPage(
        long[] expected,
        long[] actual) {
        assertExactAPage(expected, actual);
        int[] fields = {
            OctavoSurfaceView.STATE_WIDTH,
            OctavoSurfaceView.STATE_HEIGHT,
            OctavoSurfaceView.STATE_PAGE_SURFACE_X,
            OctavoSurfaceView.STATE_PAGE_SURFACE_Y,
            OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH,
            OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT,
            OctavoSurfaceView.STATE_CONTENT_X,
            OctavoSurfaceView.STATE_CONTENT_Y,
            OctavoSurfaceView.STATE_CONTENT_WIDTH,
            OctavoSurfaceView.STATE_CONTENT_HEIGHT,
            OctavoSurfaceView.STATE_READER_CHROME_INSET_TOP,
            OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM,
        };
        for (int field : fields) {
            assertEquals(
                "Viewport round trip changed native field " + field,
                expected[field],
                actual[field]);
        }
    }

    private static void assertReaderGeometryUnchanged(
        long[] expected,
        long[] actual) {
        for (int field = OctavoSurfaceView.STATE_CONTENT_X;
             field <=
                 OctavoSurfaceView.STATE_READER_CHROME_INSET_BOTTOM;
             ++field) {
            assertEquals(
                "Chrome changed native reader geometry field " + field,
                expected[field],
                actual[field]);
        }
    }

    private static void requestOrientation(
        ActivityScenario<OctavoActivity> scenario,
        int requestedOrientation) {
        scenario.onActivity(activity ->
            activity.setRequestedOrientation(requestedOrientation));
    }

    private static void awaitConfigurationOrientation(
        ActivityScenario<OctavoActivity> scenario,
        int expectedOrientation) {
        for (int attempt = 0; attempt < 120; ++attempt) {
            AtomicReference<Integer> actual = new AtomicReference<>();
            scenario.onActivity(activity -> actual.set(
                activity.getResources().getConfiguration().orientation));
            if (actual.get() != null
                && actual.get() == expectedOrientation) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("8vo did not reach configuration orientation "
             + expectedOrientation);
    }

    private static void dispatchTapAt(
        OctavoSurfaceView view,
        float x,
        float y,
        long downTime) {
        MotionEvent down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0);
        MotionEvent up = MotionEvent.obtain(
            downTime,
            downTime + 10,
            MotionEvent.ACTION_UP,
            x,
            y,
            0);
        try {
            assertTrue(view.dispatchTouchEvent(down));
            assertTrue(view.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static void dispatchTouchAction(OctavoSurfaceView view,
                                            int action,
                                            float x,
                                            float y,
                                            long downTime,
                                            long eventTime) {
        MotionEvent event = MotionEvent.obtain(
            downTime, eventTime, action, x, y, 0);
        try {
            assertTrue(view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private static final class ChromeOcclusion {
        final int width;
        final int height;
        final int top;
        final int bottom;
        final int topViewHeight;
        final int bottomViewHeight;

        ChromeOcclusion(int width,
                        int height,
                        int top,
                        int bottom,
                        int topViewHeight,
                        int bottomViewHeight) {
            this.width = width;
            this.height = height;
            this.top = top;
            this.bottom = bottom;
            this.topViewHeight = topViewHeight;
            this.bottomViewHeight = bottomViewHeight;
        }
    }

    private static final class ViewportSize {
        final int width;
        final int height;

        ViewportSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static void openFixture(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            assertTrue(activity.libraryVisibleForTesting());
            assertTrue(activity.openFixtureForTesting());
        });
    }

    private static View findClickableText(View root, String expected) {
        if (root == null) {
            return null;
        }
        if (root.isClickable()
            && root instanceof TextView
            && expected.equals(((TextView)root).getText().toString())) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup)root;
        for (int index = 0; index < group.getChildCount(); ++index) {
            View match = findClickableText(
                group.getChildAt(index), expected);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static void assertReaderEntryChromeHidden(
        OctavoActivity activity) {
        OctavoSurfaceView reader = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        View top = activity.findViewById(
            R.id.octavo_reader_top_chrome);
        View bottom = activity.findViewById(
            R.id.octavo_reader_bottom_chrome);
        assertNotNull(reader);
        assertNotNull(top);
        assertNotNull(bottom);
        assertFalse(activity.chromeVisibleForTesting());
        assertFalse(reader.chromeVisibleForTesting());
        long[] snapshot = reader.nativeStateForTesting();
        assertNotNull(snapshot);
        assertEquals(
            0, snapshot[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
        assertEquals(
            0, snapshot[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
        assertEquals(View.INVISIBLE, top.getVisibility());
        assertEquals(View.INVISIBLE, bottom.getVisibility());
        assertEquals(0.0f, top.getAlpha(), 0.0f);
        assertEquals(0.0f, bottom.getAlpha(), 0.0f);
        assertFalse(top.isShown());
        assertFalse(bottom.isShown());
    }

    private static boolean chromeViewIsExactlyHidden(View view) {
        return view.getVisibility() == View.INVISIBLE
            && Float.compare(view.getAlpha(), 0.0f) == 0
            && !view.isShown();
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface)));
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
        for (int attempt = 0; attempt < 300; ++attempt) {
            long[] current = state(scenario);
            if (condition.matches(current)) {
                return current;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] locationCacheState(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> snapshot = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView view = (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface);
            snapshot.set(
                view == null
                    ? null : view.locationCacheStateForTesting());
        });
        long[] current = snapshot.get();
        assertNotNull(current);
        assertEquals(10, current.length);
        return current;
    }

    private static long[] awaitLocationCacheComplete(
        ActivityScenario<OctavoActivity> scenario)
        throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            long[] current = locationCacheState(scenario);
            if (current != null && current.length == 10
                && current[0] == 1 && current[1] == 1) {
                return current;
            }
            SystemClock.sleep(20);
        }
        fail("8vo did not complete bounded deferred location warming");
        return new long[0];
    }

    private static long[] awaitLocationCacheFailure(
        ActivityScenario<OctavoActivity> scenario)
        throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            long[] current = locationCacheState(scenario);
            if (current[8] > 0) {
                InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();
                return locationCacheState(scenario);
            }
            SystemClock.sleep(20);
        }
        fail("8vo did not surface its deferred location failure");
        return new long[0];
    }

    private static long[] awaitInitialPage(
        ActivityScenario<OctavoActivity> scenario) {
        return awaitState(
            scenario,
            current ->
                current[OctavoSurfaceView.STATE_RESUMED] == 1
                && current[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                && current[OctavoSurfaceView.STATE_WIDTH] > 0
                && current[OctavoSurfaceView.STATE_HEIGHT] > 0
                && current[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                && current[
                    OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && current[
                    OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
                && current[OctavoSurfaceView.STATE_PAGE_INDEX] == 1
                && current[
                    OctavoSurfaceView
                        .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                && current[
                    OctavoSurfaceView
                        .STATE_HOST_PRESENTATION_PENDING] == 0
                && current[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    == current[
                        OctavoSurfaceView
                            .STATE_APPEARANCE_PRESENTED_GENERATION],
            "8vo did not present its initial Port 7 page");
    }

    private static long[] requestAndAwaitAppearance(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        long minimumGeneration =
            state(scenario)[
                OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1;
        scenario.onActivity(activity ->
            activity.requestAppearanceForTesting(expected));
        return awaitAppearance(
            scenario, expected, minimumGeneration);
    }

    private static long[] awaitAppearance(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected,
        long minimumGeneration) {
        long[] presented = awaitState(
            scenario,
            current ->
                current[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    >= minimumGeneration
                && current[
                    OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    == current[
                        OctavoSurfaceView
                            .STATE_APPEARANCE_PRESENTED_GENERATION]
                && current[
                    OctavoSurfaceView
                        .STATE_REFLOW_PRESENTATION_PENDING] == 0
                && appearanceMatchesState(expected, current),
            "8vo did not present appearance "
                + OctavoAppearance.themeCode(expected.themeId()));
        assertNativeAppearance(presented, expected);
        assertHostAppearance(scenario, expected);
        return presented;
    }

    private static boolean appearanceMatchesState(
        OctavoAppearance expected,
        long[] state) {
        return state != null
            && state.length == OctavoSurfaceView.STATE_FIELD_COUNT
            && state[OctavoSurfaceView.STATE_THEME]
                == expected.themeId()
            && state[OctavoSurfaceView.STATE_FONT_FAMILY]
                == expected.fontFamilyId()
            && state[OctavoSurfaceView.STATE_FONT_SIZE_SP]
                == expected.fontSizeSp()
            && state[
                OctavoSurfaceView.STATE_LINE_SPACING_PERMILLE]
                == expected.lineSpacingPermille()
            && state[OctavoSurfaceView.STATE_MARGIN]
                == expected.marginsId()
            && state[OctavoSurfaceView.STATE_ALIGNMENT]
                == expected.alignmentId()
            && state[OctavoSurfaceView.STATE_PUBLISHER_COLORS]
                == expected.publisherColorsId()
            && state[OctavoSurfaceView.STATE_REDUCED_MOTION]
                == (expected.reducedMotion() ? 1 : 0)
            && state[OctavoSurfaceView.STATE_PALETTE_HASH]
                == nativePaletteHash(
                    OctavoDesignTokens.forAppearance(expected)
                        .nativeUi0Colors());
    }

    private static void assertNativeAppearance(
        long[] state,
        OctavoAppearance expected) {
        assertNativeAppearance(state, expected, 0);
    }

    private static void assertNativeAppearance(
        long[] state,
        OctavoAppearance expected,
        long expectedRenderFailures) {
        assertTrue(appearanceMatchesState(expected, state));
        assertEquals(
            state[OctavoSurfaceView.STATE_APPEARANCE_GENERATION],
            state[
                OctavoSurfaceView
                    .STATE_APPEARANCE_PRESENTED_GENERATION]);
        assertEquals(
            0,
            state[
                OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING]);
        assertEquals(
            0,
            state[OctavoSurfaceView.STATE_APPEARANCE_FAILURE_COUNT]);
        assertEquals(
            0,
            state[OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
        assertEquals(
            expectedRenderFailures,
            state[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
        assertEquals(
            0,
            state[OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING]);
        assertTrue(
            state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                < state[
                    OctavoSurfaceView
                        .STATE_PAGE_ONE_PAST_LAST_BYTE]);
    }

    private static void assertHostAppearance(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        scenario.onActivity(activity -> {
            activity.flushAppearancePersistenceForTesting();
            assertEquals(expected, activity.appearanceForTesting());
            assertEquals(
                expected,
                activity.appearanceStoreForTesting().current());
            OctavoSurfaceView view = (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface);
            assertNotNull(view);
            assertEquals(
                expected, view.presentedAppearanceForTesting());
        });
    }

    private static void assertHostAppearanceWithoutFlush(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        scenario.onActivity(activity -> {
            assertEquals(expected, activity.appearanceForTesting());
            assertEquals(
                expected,
                activity.appearanceStoreForTesting().current());
            OctavoSurfaceView view = (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface);
            assertNotNull(view);
            assertEquals(
                expected, view.presentedAppearanceForTesting());
            assertFalse(view.hasPendingAppearanceRequest());
        });
    }

    private static long flushAndReadSaveSuccessCount(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicLong result = new AtomicLong(-1);
        scenario.onActivity(activity -> {
            activity.flushAppearancePersistenceForTesting();
            result.set(activity.appearanceStoreForTesting()
                           .saveSuccessCountForTesting());
        });
        assertTrue(result.get() >= 0);
        return result.get();
    }

    private static long currentSaveSuccessCount(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicLong result = new AtomicLong(-1);
        scenario.onActivity(activity -> result.set(
            activity.appearanceStoreForTesting()
                .saveSuccessCountForTesting()));
        assertTrue(result.get() >= 0);
        return result.get();
    }

    private static long awaitSaveSuccessCount(
        ActivityScenario<OctavoActivity> scenario,
        long expected) {
        for (int attempt = 0; attempt < 120; ++attempt) {
            long current = currentSaveSuccessCount(scenario);
            if (current >= expected) {
                return current;
            }
            SystemClock.sleep(50);
        }
        fail("8vo did not automatically publish the presented appearance");
        return -1;
    }

    private static long nativePaletteHash(int[] colors) {
        long hash = FNV_OFFSET_BASIS;
        for (int color : colors) {
            hash = (hash ^ Integer.toUnsignedLong(color))
                * FNV_PRIME;
        }
        return hash;
    }

    private static long framePixelHash(Bitmap frame) {
        long hash = FNV_OFFSET_BASIS;
        for (int y = 0; y < frame.getHeight(); y += 2) {
            for (int x = 0; x < frame.getWidth(); x += 2) {
                hash = (hash ^ Integer.toUnsignedLong(
                            frame.getPixel(x, y)))
                    * FNV_PRIME;
            }
        }
        return hash;
    }

    private static int countPixelsNear(Bitmap frame,
                                       long[] state,
                                       int expected,
                                       int maximumDistance) {
        int left = (int)state[OctavoSurfaceView.STATE_CONTENT_X];
        int top = (int)state[OctavoSurfaceView.STATE_CONTENT_Y];
        int right = Math.min(
            frame.getWidth(),
            left + (int)state[OctavoSurfaceView.STATE_CONTENT_WIDTH]);
        int bottom = Math.min(
            frame.getHeight(),
            top + (int)state[OctavoSurfaceView.STATE_CONTENT_HEIGHT]);
        int count = 0;
        for (int y = Math.max(0, top); y < bottom; ++y) {
            for (int x = Math.max(0, left); x < right; ++x) {
                int pixel = frame.getPixel(x, y);
                int distance =
                    Math.abs(Color.red(pixel) - Color.red(expected))
                    + Math.abs(Color.green(pixel)
                               - Color.green(expected))
                    + Math.abs(Color.blue(pixel)
                               - Color.blue(expected));
                if (distance <= maximumDistance) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private static char findRuntimeOverhangGlyph(
        Context context,
        OctavoAppearance appearance,
        boolean leftSide) {
        OctavoTypography typography =
            OctavoTypography.create(context, appearance);
        int columns = typography.metrics[5];
        int rowsPerStyle = typography.metrics[6];
        int cellWidth = typography.metrics[7];
        int cellHeight = typography.metrics[8];
        int stride = typography.metrics[11];
        int originX = typography.metrics[16];
        int italicStyle = 2;
        int bestGlyph = -1;
        int bestDepth = 0;
        for (int glyph = 1;
             glyph < OctavoTypography.GLYPH_COUNT;
             ++glyph) {
            int codepoint =
                OctavoTypography.codepointForGlyphForTesting(glyph);
            if (!Character.isLetter(codepoint)) {
                continue;
            }
            int column = glyph % columns;
            int row = italicStyle * rowsPerStyle
                + glyph / columns;
            int cellLeft = column * cellWidth;
            int cellTop = row * cellHeight;
            int firstInk = cellWidth;
            int lastInk = -1;
            for (int y = 0; y < cellHeight; ++y) {
                for (int x = 0; x < cellWidth; ++x) {
                    if ((typography.alpha[
                            (cellTop + y) * stride + cellLeft + x]
                         & 0xFF) != 0) {
                        firstInk = Math.min(firstInk, x);
                        lastInk = Math.max(lastInk, x);
                    }
                }
            }
            if (lastInk < firstInk) {
                continue;
            }
            int advance = typography.metrics[
                OctavoTypography.ADVANCE_OFFSET
                    + italicStyle * OctavoTypography.GLYPH_COUNT
                    + glyph];
            int depth = leftSide
                ? originX - firstInk
                : lastInk - (originX + advance - 1);
            if (depth > bestDepth) {
                bestDepth = depth;
                bestGlyph = glyph;
            }
        }
        assertTrue(
            (leftSide ? "left" : "right")
                + " runtime overhang probe glyph was unavailable",
            bestGlyph >= 0 && bestDepth > 0);
        int codepoint =
            OctavoTypography.codepointForGlyphForTesting(bestGlyph);
        assertTrue(codepoint <= Character.MAX_VALUE);
        return (char)codepoint;
    }

    private static void assertStyledRuntimeOverhang(
        Bitmap frame,
        long[] state,
        OctavoDesignTokens tokens,
        int leftColor,
        int rightColor) {
        int pageLeft =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_X];
        int pageRight = pageLeft
            + (int)state[
                OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
        int contentLeft =
            (int)state[OctavoSurfaceView.STATE_CONTENT_X];
        int contentRight = contentLeft
            + (int)state[OctavoSurfaceView.STATE_CONTENT_WIDTH];
        int contentTop =
            (int)state[OctavoSurfaceView.STATE_CONTENT_Y];
        int contentBottom = contentTop
            + (int)state[OctavoSurfaceView.STATE_CONTENT_HEIGHT];
        assertTrue(pageLeft + 2 < contentLeft);
        assertTrue(contentRight < pageRight - 2);

        int leftPixels = 0;
        int rightPixels = 0;
        int leftFirstY = contentBottom;
        int leftLastY = contentTop - 1;
        int rightFirstY = contentBottom;
        int rightLastY = contentTop - 1;
        for (int y = contentTop; y < contentBottom; ++y) {
            for (int x = pageLeft + 2; x < contentLeft; ++x) {
                if (pixelNear(frame.getPixel(x, y), leftColor, 160)) {
                    leftPixels += 1;
                    leftFirstY = Math.min(leftFirstY, y);
                    leftLastY = Math.max(leftLastY, y);
                }
            }
            for (int x = contentRight; x < pageRight - 2; ++x) {
                if (pixelNear(frame.getPixel(x, y), rightColor, 160)) {
                    rightPixels += 1;
                    rightFirstY = Math.min(rightFirstY, y);
                    rightLastY = Math.max(rightLastY, y);
                }
            }
        }
        assertTrue(
            "Styled italic left bearing was clipped at the content edge",
            leftPixels > 0);
        assertTrue(
            "Styled italic right overhang was clipped at the content edge",
            rightPixels > 0);
        assertTrue(
            "Styled left/right overhang probes did not occupy separate rows",
            leftFirstY < rightFirstY
                && leftLastY < rightLastY);
    }

    private static boolean pixelNear(
        int pixel,
        int expected,
        int maximumDistance) {
        int distance =
            Math.abs(Color.red(pixel) - Color.red(expected))
            + Math.abs(Color.green(pixel) - Color.green(expected))
            + Math.abs(Color.blue(pixel) - Color.blue(expected));
        return distance <= maximumDistance;
    }

    private static File createPublicationEvidenceEpub(Context context)
        throws IOException {
        File result = new File(
            context.getCacheDir(), "port7-publication-evidence.epub");
        if (result.exists() && !result.delete()) {
            throw new IOException(
                "Unable to replace the publication evidence EPUB");
        }

        String container =
            "<?xml version='1.0'?>"
                + "<container version='1.0' "
                + "xmlns='urn:oasis:names:tc:opendocument:"
                + "xmlns:container'><rootfiles><rootfile "
                + "full-path='OEBPS/content.opf' "
                + "media-type='application/oebps-package+xml'/>"
                + "</rootfiles></container>";
        String packageDocument =
            "<?xml version='1.0'?>"
                + "<package xmlns='http://www.idpf.org/2007/opf' "
                + "version='2.0' unique-identifier='book-id'>"
                + "<metadata xmlns:dc='http://purl.org/dc/elements/1.1/'>"
                + "<dc:title>Port 7 Publication Evidence</dc:title>"
                + "<dc:identifier id='book-id'>"
                + "port7-publication</dc:identifier>"
                + "<dc:language>en</dc:language></metadata>"
                + "<manifest>"
                + "<item id='ncx' href='toc.ncx' "
                + "media-type='application/x-dtbncx+xml'/>"
                + "<item id='chapter' href='chapter.xhtml' "
                + "media-type='application/xhtml+xml'/>"
                + "</manifest><spine toc='ncx'>"
                + "<itemref idref='chapter'/></spine></package>";
        String navigation =
            "<?xml version='1.0'?>"
                + "<ncx xmlns='http://www.daisy.org/z3986/2005/ncx/' "
                + "version='2005-1'><head/>"
                + "<docTitle><text>Port 7 Publication Evidence</text>"
                + "</docTitle><navMap>"
                + "<navPoint id='chapter' playOrder='1'>"
                + "<navLabel><text>Publication evidence</text></navLabel>"
                + "<content src='chapter.xhtml'/></navPoint>"
                + "</navMap></ncx>";
        String chapter =
            "<?xml version='1.0'?>"
                + "<html xmlns='http://www.w3.org/1999/xhtml'>"
                + "<head><title>Publication evidence</title></head>"
                + "<body><p style='text-align:justify'>"
                + "The reader’s deliberate line<br/>"
                + "“Second line—kept intact.”<br/>"
                + "Third line…still intact."
                + "</p></body></html>";

        try (ZipOutputStream output =
                 new ZipOutputStream(new FileOutputStream(result))) {
            addZipEntry(output, "mimetype", "application/epub+zip");
            addZipEntry(
                output, "META-INF/container.xml", container);
            addZipEntry(
                output, "OEBPS/content.opf", packageDocument);
            addZipEntry(output, "OEBPS/toc.ncx", navigation);
            addZipEntry(output, "OEBPS/chapter.xhtml", chapter);
        }
        return result;
    }

    private static File createStyledEvidenceEpub(
        Context context,
        char literaryLeft,
        char literaryRight,
        char clearLeft,
        char clearRight)
        throws IOException {
        File result = new File(
            context.getCacheDir(), "port7-styled-evidence.epub");
        if (result.exists() && !result.delete()) {
            throw new IOException(
                "Unable to replace the styled evidence EPUB");
        }

        String container =
            "<?xml version='1.0'?>"
                + "<container version='1.0' "
                + "xmlns='urn:oasis:names:tc:opendocument:"
                + "xmlns:container'><rootfiles><rootfile "
                + "full-path='OEBPS/content.opf' "
                + "media-type='application/oebps-package+xml'/>"
                + "</rootfiles></container>";
        String packageDocument =
            "<?xml version='1.0'?>"
                + "<package xmlns='http://www.idpf.org/2007/opf' "
                + "version='2.0' unique-identifier='book-id'>"
                + "<metadata xmlns:dc='http://purl.org/dc/elements/1.1/'>"
                + "<dc:title>Port 7 Styled Evidence</dc:title>"
                + "<dc:identifier id='book-id'>port7-styled</dc:identifier>"
                + "<dc:language>en</dc:language></metadata>"
                + "<manifest>"
                + "<item id='ncx' href='toc.ncx' "
                + "media-type='application/x-dtbncx+xml'/>"
                + "<item id='chapter' href='chapter.xhtml' "
                + "media-type='application/xhtml+xml'/>"
                + "</manifest><spine toc='ncx'>"
                + "<itemref idref='chapter'/></spine></package>";
        String navigation =
            "<?xml version='1.0'?>"
                + "<ncx xmlns='http://www.daisy.org/z3986/2005/ncx/' "
                + "version='2005-1'><head/>"
                + "<docTitle><text>Port 7 Styled Evidence</text></docTitle>"
                + "<navMap><navPoint id='chapter' playOrder='1'>"
                + "<navLabel><text>Styled evidence</text></navLabel>"
                + "<content src='chapter.xhtml'/></navPoint>"
                + "</navMap></ncx>";
        StringBuilder chapter = new StringBuilder(
            "<?xml version='1.0'?>"
                + "<html xmlns='http://www.w3.org/1999/xhtml'>"
                + "<head><title>Styled evidence</title></head><body>"
                + "<h1 style='text-align:center'>"
                + "Port Seven Styled Evidence</h1>");
        chapter.append("<p style='text-align:justify'>")
            .append("<span style='color:#137a43'>")
            .append("Publisher justification evidence begins with a "
                    + "deliberately long paragraph whose softly wrapped "
                    + "rows contain several interior spaces and enough "
                    + "natural ink to exercise restrained expansion.")
            .append("</span>")
            .append("</p>")
            .append("<p style='text-align:left'>")
            .append("<span style='color:#dc1414'><em>")
            .append(literaryLeft)
            .append("</em></span></p>")
            .append("<p style='text-align:right'>")
            .append("<span style='color:#143cdc'><em>")
            .append(literaryRight)
            .append("</em></span></p>")
            .append("<p style='text-align:left'>")
            .append("<span style='color:#00aab8'><em>")
            .append(clearLeft)
            .append("</em></span></p>")
            .append("<p style='text-align:right'>")
            .append("<span style='color:#c88a00'><em>")
            .append(clearRight)
            .append("</em></span></p>");
        for (int index = 1; index <= 28; ++index) {
            chapter.append("<p style='text-align:justify'>")
                .append("<span style='color:#137a43'>")
                .append("Publisher green row ")
                .append(index)
                .append("</span> demonstrates independently controlled ")
                .append("alignment, color policy, and curated typography.")
                .append("</p>");
        }
        chapter.append("</body></html>");

        try (ZipOutputStream output =
                 new ZipOutputStream(new FileOutputStream(result))) {
            addZipEntry(output, "mimetype", "application/epub+zip");
            addZipEntry(
                output, "META-INF/container.xml", container);
            addZipEntry(
                output, "OEBPS/content.opf", packageDocument);
            addZipEntry(output, "OEBPS/toc.ncx", navigation);
            addZipEntry(
                output, "OEBPS/chapter.xhtml", chapter.toString());
        }
        return result;
    }

    private static void addZipEntry(ZipOutputStream output,
                                    String name,
                                    String contents)
        throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static Bitmap copyFrame(OctavoSurfaceView surface)
        throws InterruptedException {
        int width = surface.getWidth();
        int height = surface.getHeight();
        assertTrue(width > 0);
        assertTrue(height > 0);
        Bitmap bitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888);
        HandlerThread copyThread =
            new HandlerThread("8vo-appearance-pixel-copy");
        copyThread.start();
        try {
            for (int attempt = 0; attempt < 10; ++attempt) {
                CountDownLatch copied = new CountDownLatch(1);
                AtomicReference<Integer> result =
                    new AtomicReference<>(PixelCopy.ERROR_UNKNOWN);
                PixelCopy.request(
                    surface,
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
            fail("PixelCopy could not read the Port 7 frame");
            bitmap.recycle();
            return null;
        } finally {
            copyThread.quitSafely();
        }
    }

    private static void assertExactPageAndBackgroundPixels(
        long[] state,
        Bitmap frame,
        OctavoDesignTokens tokens,
        boolean forbidNearWhite) {
        int pageX =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_X];
        int pageY =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_Y];
        int pageWidth =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
        int pageHeight =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT];
        assertTrue(pageX >= 4);
        assertTrue(pageY >= 0);
        assertTrue(pageWidth > 12);
        assertTrue(pageHeight > 16);
        assertTrue(pageX + pageWidth <= frame.getWidth());
        assertTrue(pageY + pageHeight <= frame.getHeight());

        int pageTop = pageY + 3;
        int pageBottom = Math.min(pageTop + 8, pageY + pageHeight - 2);
        assertExactRegion(
            frame,
            pageX + 3,
            pageTop,
            pageX + pageWidth - 3,
            pageBottom,
            tokens.readerPage,
            forbidNearWhite,
            "page");

        int backgroundTop =
            pageY + Math.max(pageHeight / 2 - 4, 0);
        int backgroundBottom =
            Math.min(backgroundTop + 8, frame.getHeight());
        assertExactRegion(
            frame,
            0,
            backgroundTop,
            pageX,
            backgroundBottom,
            tokens.readerPage,
            forbidNearWhite,
            "left background");
        assertExactRegion(
            frame,
            pageX + pageWidth,
            backgroundTop,
            frame.getWidth(),
            backgroundBottom,
            tokens.readerPage,
            forbidNearWhite,
            "right background");
    }

    private static void assertExactRegion(Bitmap frame,
                                          int left,
                                          int top,
                                          int right,
                                          int bottom,
                                          int expected,
                                          boolean forbidNearWhite,
                                          String label) {
        assertTrue(label + " region is empty", left < right && top < bottom);
        for (int y = top; y < bottom; ++y) {
            for (int x = left; x < right; ++x) {
                int actual = frame.getPixel(x, y);
                assertEquals(
                    label + " pixel " + x + "," + y,
                    expected,
                    actual);
                if (forbidNearWhite) {
                    assertFalse(
                        label + " contained a near-white pixel at "
                            + x + "," + y,
                        Color.alpha(actual) == 0xFF
                        && Color.red(actual) >= 0xF0
                        && Color.green(actual) >= 0xF0
                        && Color.blue(actual) >= 0xF0);
                }
            }
        }
    }

    private static void assertContrastingReaderInk(
        long[] state,
        Bitmap frame,
        OctavoDesignTokens tokens) {
        assertContrastingReaderInk(state, frame, tokens, 0, 0);
    }

    private static void assertContrastingReaderInk(
        long[] state,
        Bitmap frame,
        OctavoDesignTokens tokens,
        int offsetX,
        int offsetY) {
        int pageX =
            offsetX
            + (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_X];
        int pageY =
            offsetY
            + (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_Y];
        int pageWidth =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
        int pageHeight =
            (int)state[OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT];
        int inset = 24;
        int contrasting = 0;
        for (int y = pageY + inset;
             y < pageY + pageHeight - inset;
             ++y) {
            for (int x = pageX + inset;
                 x < pageX + pageWidth - inset;
                 ++x) {
                int pixel = frame.getPixel(x, y);
                int distance =
                    Math.abs(Color.red(pixel)
                             - Color.red(tokens.readerPage))
                    + Math.abs(Color.green(pixel)
                               - Color.green(tokens.readerPage))
                    + Math.abs(Color.blue(pixel)
                               - Color.blue(tokens.readerPage));
                if (distance >= 48) {
                    ++contrasting;
                }
            }
        }
        assertTrue(
            OctavoAppearance.themeLabel(tokens.themeId)
                + " reader page did not contain contrasting body ink",
            contrasting > 40);
    }

    private static void assertReaderInkHasUnclippedVerticalEdges(
        long[] state,
        Bitmap frame,
        OctavoDesignTokens tokens) {
        int left = (int)state[OctavoSurfaceView.STATE_CONTENT_X];
        int top = (int)state[OctavoSurfaceView.STATE_CONTENT_Y];
        int right = left
            + (int)state[OctavoSurfaceView.STATE_CONTENT_WIDTH];
        int bottom = top
            + (int)state[OctavoSurfaceView.STATE_CONTENT_HEIGHT];
        assertTrue(left >= 0 && top >= 0);
        assertTrue(right <= frame.getWidth());
        assertTrue(bottom <= frame.getHeight());

        int firstInkY = bottom;
        int lastInkY = top - 1;
        int contrasting = 0;
        for (int y = top; y < bottom; ++y) {
            for (int x = left; x < right; ++x) {
                int pixel = frame.getPixel(x, y);
                int distance =
                    Math.abs(Color.red(pixel)
                             - Color.red(tokens.readerPage))
                    + Math.abs(Color.green(pixel)
                               - Color.green(tokens.readerPage))
                    + Math.abs(Color.blue(pixel)
                               - Color.blue(tokens.readerPage));
                if (distance >= 48) {
                    firstInkY = Math.min(firstInkY, y);
                    lastInkY = Math.max(lastInkY, y);
                    contrasting += 1;
                }
            }
        }
        assertTrue("Reader content contained no inspectable glyph ink",
                   contrasting > 40);
        assertTrue(
            "Reader glyph ink touched the top clip edge",
            firstInkY > top);
        assertTrue(
            "Reader glyph ink touched the bottom clip edge",
            lastInkY < bottom - 1);
    }

    private static void assertTypographyAtlasCellsUnclipped(
        Context context,
        OctavoAppearance appearance) {
        OctavoTypography typography =
            OctavoTypography.create(context, appearance);
        int columns = typography.metrics[5];
        int rowsPerStyle = typography.metrics[6];
        int cellWidth = typography.metrics[7];
        int cellHeight = typography.metrics[8];
        int atlasWidth = typography.metrics[9];
        int atlasHeight = typography.metrics[10];
        int stride = typography.metrics[11];
        assertEquals(columns * cellWidth, atlasWidth);
        assertEquals(
            rowsPerStyle * OctavoTypography.STYLE_COUNT * cellHeight,
            atlasHeight);
        assertEquals(stride * atlasHeight, typography.alpha.length);

        for (int style = 0;
             style < OctavoTypography.STYLE_COUNT;
             ++style) {
            for (int glyph = 0;
                 glyph < OctavoTypography.GLYPH_COUNT;
                 ++glyph) {
                int column = glyph % columns;
                int row = style * rowsPerStyle + glyph / columns;
                int left = column * cellWidth;
                int top = row * cellHeight;
                int right = left + cellWidth - 1;
                int bottom = top + cellHeight - 1;
                boolean hasInk = false;
                for (int x = left; x <= right; ++x) {
                    assertEquals(
                        "Glyph atlas ink touched a horizontal cell edge",
                        0,
                        typography.alpha[top * stride + x] & 0xFF);
                    assertEquals(
                        "Glyph atlas ink touched a horizontal cell edge",
                        0,
                        typography.alpha[bottom * stride + x] & 0xFF);
                }
                for (int y = top; y <= bottom; ++y) {
                    assertEquals(
                        "Glyph atlas ink touched a vertical cell edge",
                        0,
                        typography.alpha[y * stride + left] & 0xFF);
                    assertEquals(
                        "Glyph atlas ink touched a vertical cell edge",
                        0,
                        typography.alpha[y * stride + right] & 0xFF);
                    for (int x = left + 1; x < right; ++x) {
                        hasInk |=
                            (typography.alpha[y * stride + x] & 0xFF)
                                != 0;
                    }
                }
                int codepoint =
                    OctavoTypography.codepointForGlyphForTesting(glyph);
                assertTrue(
                    "Printable glyph had no atlas ink",
                    Character.isWhitespace(codepoint)
                        || Character.isSpaceChar(codepoint)
                        || Character.getType(codepoint)
                            == Character.FORMAT
                        || hasInk);
            }
        }
    }

    private static void assertAnchorInsidePage(long[] state,
                                               long anchorSpine,
                                               long anchorOffset) {
        assertEquals(
            anchorSpine,
            state[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
        assertEquals(
            anchorOffset,
            state[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
        assertEquals(
            anchorSpine,
            state[OctavoSurfaceView.STATE_SPINE_INDEX]);
        assertTrue(
            "Page started after the successful semantic anchor",
            state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                <= anchorOffset);
        assertTrue(
            "Page ended before the successful semantic anchor",
            anchorOffset
                < state[
                    OctavoSurfaceView
                        .STATE_PAGE_ONE_PAST_LAST_BYTE]);
    }

    private static void assertExactAPage(
        long[] expected,
        long[] actual) {
        int[] fields = {
            OctavoSurfaceView.STATE_SPINE_INDEX,
            OctavoSurfaceView.STATE_PAGE_INDEX,
            OctavoSurfaceView.STATE_PAGE_COUNT,
            OctavoSurfaceView.STATE_VISIBLE_TEXT_SIZE,
            OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH,
            OctavoSurfaceView.STATE_PAGE_FIRST_BYTE,
            OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE,
        };
        for (int field : fields) {
            assertEquals(
                "Layout A did not recover native page field " + field,
                expected[field],
                actual[field]);
        }
    }

    private static void assertVisualOnlyAlignmentInvariants(
        long[] expected,
        long[] actual) {
        assertExactAPage(expected, actual);
        assertLayoutGeometryUnchanged(expected, actual);
        assertReaderGeometryUnchanged(expected, actual);
        int[] fields = {
            OctavoSurfaceView.STATE_PROGRESS_PAGE_INDEX,
            OctavoSurfaceView.STATE_PROGRESS_PAGE_COUNT,
            OctavoSurfaceView.STATE_SPINE_INDEX,
            OctavoSurfaceView.STATE_SECTION_COUNT,
            OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX,
            OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT,
            OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX,
            OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET,
            OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT,
            OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT,
            OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT,
            OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT,
        };
        for (int field : fields) {
            assertEquals(
                "Visual-only alignment changed native invariant "
                    + field,
                expected[field],
                actual[field]);
        }
    }

    private static void assertLayoutGeometryUnchanged(
        long[] expected,
        long[] actual) {
        for (int field = OctavoSurfaceView.STATE_PAGE_SURFACE_X;
             field <= OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT;
             ++field) {
            assertEquals(
                "Layout A did not recover geometry field " + field,
                expected[field],
                actual[field]);
        }
    }

    private static OctavoAppearance capturedTransitionalAppearance() {
        return OctavoAppearance.create(
            OctavoAppearance.THEME_SEPIA,
            OctavoAppearance.FONT_FAMILY_LITERARY,
            18,
            1250,
            OctavoAppearance.MARGINS_BALANCED,
            OctavoAppearance.ALIGNMENT_PUBLISHER,
            OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE,
            false);
    }

    private static void writeAppearanceRecord(
        OctavoAppearanceStore store,
        byte[] bytes)
        throws IOException {
        File parent = store.appearanceFileForTesting().getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileOutputStream output =
                 new FileOutputStream(
                     store.appearanceFileForTesting(), false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static byte[] readAppearanceRecord(
        OctavoAppearanceStore store)
        throws IOException {
        File file = store.appearanceFileForTesting();
        assertTrue(file.isFile());
        assertEquals(
            OctavoAppearanceStore.recordBytesForTesting(),
            file.length());
        byte[] bytes =
            new byte[OctavoAppearanceStore.recordBytesForTesting()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count =
                    input.read(bytes, offset, bytes.length - offset);
                assertTrue(count > 0);
                offset += count;
            }
            assertEquals(-1, input.read());
        }
        return bytes;
    }

    private static int appearanceRecordVersion(byte[] bytes) {
        assertNotNull(bytes);
        assertTrue(bytes.length >= 2 * Integer.BYTES);
        return ByteBuffer.wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN)
            .getInt(Integer.BYTES);
    }

    private static void awaitVisibleAppearanceSaveFailure(
        ActivityScenario<OctavoActivity> scenario,
        long expectedFailureCount) {
        String expectedMessage =
            "Appearance changed, but could not be saved";
        for (int attempt = 0; attempt < 120; ++attempt) {
            AtomicReference<Boolean> observed =
                new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                View candidate = activity.findViewById(
                    R.id.octavo_reader_failure);
                boolean visible =
                    candidate instanceof TextView
                    && expectedMessage.contentEquals(
                        ((TextView)candidate).getText());
                observed.set(
                    visible
                    && expectedMessage.equals(
                        activity.lastOpenErrorForTesting())
                    && activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting()
                        >= expectedFailureCount);
            });
            if (Boolean.TRUE.equals(observed.get())) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("The gated migration save failure was not visibly reported");
    }


    private static void flushPosition(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            OctavoSurfaceView view = (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface);
            assertNotNull(view);
            view.flushPersistenceForTesting();
        });
    }
}
