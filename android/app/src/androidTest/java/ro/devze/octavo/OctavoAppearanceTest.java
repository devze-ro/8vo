package ro.devze.octavo;

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
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import java.io.FileOutputStream;
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

            long frameBeforeChromeHide =
                initial[OctavoSurfaceView.STATE_FRAME_COUNT];
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            awaitState(
                scenario,
                state ->
                    state[OctavoSurfaceView.STATE_CHROME_VISIBLE] == 0
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > frameBeforeChromeHide,
                "8vo did not present the chrome-free reader frame");

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
            });
            assertNoBrightComposedFrames(
                scenario, "reader entry", 4);
            long[] presented = awaitInitialPage(scenario);
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
    public void rapidChoicesCoalesceAndChromeIsPageNeutral() {
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
            long[] before = awaitInitialPage(scenario);
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

            long[] visible = coalesced;
            assertEquals(1,
                         visible[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            long visibleFrame =
                visible[OctavoSurfaceView.STATE_FRAME_COUNT];
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] hidden = awaitState(
                scenario,
                state ->
                    state[OctavoSurfaceView.STATE_CHROME_VISIBLE] == 0
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > visibleFrame,
                "8vo did not hide and present reader chrome");
            assertPageStateUnchanged(visible, hidden);
            assertEquals(
                visible[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                hidden[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            scenario.onActivity(activity -> {
                assertFalse(activity.chromeVisibleForTesting());
                assertEquals(latest, activity.appearanceForTesting());
            });

            long hiddenFrame =
                hidden[OctavoSurfaceView.STATE_FRAME_COUNT];
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] shown = awaitState(
                scenario,
                state ->
                    state[OctavoSurfaceView.STATE_CHROME_VISIBLE] == 1
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > hiddenFrame,
                "8vo did not show and present reader chrome");
            assertPageStateUnchanged(visible, shown);
            assertEquals(
                hidden[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT] + 1,
                shown[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertNativeAppearance(shown, latest);
            scenario.onActivity(activity -> {
                assertTrue(activity.chromeVisibleForTesting());
                assertEquals(latest, activity.appearanceForTesting());
            });

            AtomicReference<long[]> gatedChrome = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view =
                    (OctavoSurfaceView)activity.findViewById(
                        R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(view.forcePrePresentFailuresForTesting(2));
                long eventTime = SystemClock.uptimeMillis();
                float centerX = view.getWidth() / 2.0f;
                float rightX = view.getWidth() * 5.0f / 6.0f;
                float centerY = view.getHeight() / 2.0f;
                dispatchTapAt(view, centerX, centerY, eventTime);
                dispatchTapAt(view, rightX, centerY, eventTime + 40);
                gatedChrome.set(view.nativeStateForTesting());
            });
            assertNotNull(gatedChrome.get());
            long[] chromeGate = gatedChrome.get();
            assertEquals(
                1,
                chromeGate[
                    OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT] + 2,
                chromeGate[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT] + 1,
                chromeGate[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_TAP_INTENT_COUNT] + 1,
                chromeGate[OctavoSurfaceView.STATE_TAP_INTENT_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT],
                chromeGate[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertEquals(
                1, chromeGate[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertPageStateUnchangedExcept(
                shown,
                chromeGate,
                OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT);
            assertReaderGeometryUnchanged(shown, chromeGate);
            assertChromeViewsVisible(scenario);

            long[] recoveredChrome = awaitState(
                scenario,
                state ->
                    state[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 0
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > shown[OctavoSurfaceView.STATE_FRAME_COUNT]
                    && state[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == shown[
                            OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT] + 2,
                "8vo did not recover the failed center-tap chrome frame");
            assertEquals(
                shown[OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT] + 1,
                recoveredChrome[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT],
                recoveredChrome[OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT]);
            assertEquals(
                1, recoveredChrome[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertPageStateUnchangedExcept(
                shown,
                recoveredChrome,
                OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT);
            assertReaderGeometryUnchanged(shown, recoveredChrome);
            assertChromeViewsVisible(scenario);

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
            scenario.onActivity(activity -> {
                failuresBefore.set(
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
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
            scenario.onActivity(activity -> {
                assertEquals(
                    failuresBefore.get() + 1,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
                assertEquals(
                    "Appearance changed, but could not be saved",
                    activity.lastOpenErrorForTesting());
                View banner = activity.findViewById(
                    R.id.octavo_reader_failure);
                assertNotNull(
                    "The deferred persistence failure was not visible",
                    banner);
                assertTrue(banner.getBackground() instanceof ColorDrawable);
                assertEquals(
                    OctavoDesignTokens.forAppearance(latest).dialogSurface,
                    ((ColorDrawable)banner.getBackground()).getColor());
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
            assertExactAPage(atPublisher, atRagged);
            assertLayoutGeometryUnchanged(atPublisher, atRagged);
            assertEquals(
                atPublisher[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT],
                atRagged[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
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
    public void measuredChromeOcclusionIsAnchorNeutralAcrossVisibility()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            long[] visible = moveToNextPresentedPage(scenario);
            long anchorSpine =
                visible[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            long anchorOffset =
                visible[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            ChromeOcclusion visibleOcclusion = chromeOcclusion(scenario);
            assertMeasuredChromeOcclusion(visibleOcclusion, visible);
            assertChromeOcclusionFrame(
                scenario, visible, visibleOcclusion);

            long visibleFrame =
                visible[OctavoSurfaceView.STATE_FRAME_COUNT];
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] hidden = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_CHROME_VISIBLE] == 0
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > visibleFrame
                    && current[
                        OctavoSurfaceView
                            .STATE_REFLOW_PRESENTATION_PENDING] == 0,
                "8vo did not hide measured reader chrome");
            ChromeOcclusion hiddenOcclusion = chromeOcclusion(scenario);
            assertChromeOcclusionEquals(
                visibleOcclusion, hiddenOcclusion);
            assertPageStateUnchanged(visible, hidden);
            assertReaderGeometryUnchanged(visible, hidden);
            assertAnchorInsidePage(hidden, anchorSpine, anchorOffset);
            assertChromeOcclusionFrame(
                scenario, hidden, hiddenOcclusion);

            long hiddenFrame =
                hidden[OctavoSurfaceView.STATE_FRAME_COUNT];
            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(true)));
            long[] shown = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_CHROME_VISIBLE] == 1
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > hiddenFrame
                    && current[
                        OctavoSurfaceView
                            .STATE_REFLOW_PRESENTATION_PENDING] == 0,
                "8vo did not restore measured reader chrome");
            ChromeOcclusion shownOcclusion = chromeOcclusion(scenario);
            assertChromeOcclusionEquals(
                visibleOcclusion, shownOcclusion);
            assertPageStateUnchanged(visible, shown);
            assertReaderGeometryUnchanged(visible, shown);
            assertAnchorInsidePage(shown, anchorSpine, anchorOffset);
            assertChromeOcclusionFrame(
                scenario, shown, shownOcclusion);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                assertNotNull(view);
                assertTrue(
                    view.forcePrePresentFailuresForTesting(2));
                assertTrue(view.setReaderChromeInsets(
                    shownOcclusion.top + 1,
                    shownOcclusion.bottom));
                long[] gated = view.nativeStateForTesting();
                assertNotNull(gated);
                assertEquals(
                    1,
                    gated[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING]);
                assertFalse(view.movePageForAccessibility(1));
            });
            long[] recovered = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > shown[OctavoSurfaceView.STATE_FRAME_COUNT]
                    && current[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                        == shown[
                            OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT] + 2
                    && current[
                        OctavoSurfaceView
                            .STATE_REFLOW_PRESENTATION_PENDING] == 0
                    && current[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 0
                    && current[
                        OctavoSurfaceView
                            .STATE_READER_CHROME_INSET_TOP]
                        == shownOcclusion.top + 1
                    && current[
                        OctavoSurfaceView
                            .STATE_READER_CHROME_INSET_BOTTOM]
                        == shownOcclusion.bottom,
                "8vo did not retry and present the desired chrome inset");
            assertAnchorInsidePage(recovered, anchorSpine, anchorOffset);
            assertEquals(
                shown[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT],
                recovered[
                    OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]);
            assertEquals(
                shown[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT],
                recovered[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT] + 1,
                recovered[
                    OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT]);
            assertEquals(
                shown[OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT] + 1,
                recovered[
                    OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT]);
            assertEquals(
                shown[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT] + 1,
                recovered[
                    OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT]);
            assertEquals(
                0,
                recovered[OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
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
    public void hiddenChromeBandsRejectPageTapIntents() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance reducedMotion =
            OctavoAppearance.defaults().withReducedMotion(true);
        assertTrue(
            new OctavoAppearanceStore(context).save(reducedMotion));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitInitialPage(scenario);
            long[] visible = moveToNextPresentedPage(scenario);
            ChromeOcclusion occlusion = chromeOcclusion(scenario);
            assertMeasuredChromeOcclusion(occlusion, visible);

            scenario.onActivity(activity ->
                assertTrue(activity.setChromeVisibleForTesting(false)));
            long[] hidden = awaitState(
                scenario,
                current ->
                    current[OctavoSurfaceView.STATE_CHROME_VISIBLE] == 0
                    && current[OctavoSurfaceView.STATE_FRAME_COUNT]
                        > visible[OctavoSurfaceView.STATE_FRAME_COUNT]
                    && current[
                        OctavoSurfaceView
                            .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                    && current[
                        OctavoSurfaceView
                            .STATE_REFLOW_PRESENTATION_PENDING] == 0,
                "8vo did not hide chrome before band tap inspection");
            assertChromeViewsInvisible(scenario);
            assertMeasuredChromeOcclusion(
                chromeOcclusion(scenario), hidden);

            AtomicReference<long[]> tapped = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view =
                    (OctavoSurfaceView)activity.findViewById(
                        R.id.octavo_surface);
                assertNotNull(view);
                long eventTime = SystemClock.uptimeMillis();
                float left = view.getWidth() / 6.0f;
                float right = view.getWidth() * 5.0f / 6.0f;
                float top = occlusion.top / 2.0f;
                float bottom =
                    view.getHeight() - occlusion.bottom / 2.0f;
                dispatchTapAt(view, left, top, eventTime);
                dispatchTapAt(view, right, top, eventTime + 40);
                dispatchTapAt(view, left, bottom, eventTime + 80);
                dispatchTapAt(view, right, bottom, eventTime + 120);
                tapped.set(view.nativeStateForTesting());
            });
            assertNotNull(tapped.get());
            long[] after = tapped.get();
            assertEquals(
                hidden[OctavoSurfaceView.STATE_TOUCH_COUNT] + 8,
                after[OctavoSurfaceView.STATE_TOUCH_COUNT]);
            int[] unchangedCounters = {
                OctavoSurfaceView.STATE_TAP_INTENT_COUNT,
                OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT,
                OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT,
                OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT,
                OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT,
                OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT,
                OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT,
                OctavoSurfaceView.STATE_FRAME_COUNT,
                OctavoSurfaceView.STATE_CHROME_TOGGLE_COUNT,
            };
            for (int field : unchangedCounters) {
                assertEquals(
                    "Hidden chrome-band tap changed native field "
                        + field,
                    hidden[field],
                    after[field]);
            }
            assertEquals(
                0, after[OctavoSurfaceView.STATE_CHROME_VISIBLE]);
            assertPageStateUnchanged(hidden, after);
            assertReaderGeometryUnchanged(hidden, after);
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
        long minimumGeneration =
            state(scenario)[
                OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1;
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
            int[] surfaceLocation = new int[2];
            int[] topLocation = new int[2];
            int[] bottomLocation = new int[2];
            surface.getLocationOnScreen(surfaceLocation);
            topChrome.getLocationOnScreen(topLocation);
            bottomChrome.getLocationOnScreen(bottomLocation);
            int width = surface.getWidth();
            int height = surface.getHeight();
            int top = Math.max(
                0,
                Math.min(
                    height,
                    topLocation[1] + topChrome.getHeight()
                        - surfaceLocation[1]));
            int bottom = Math.max(
                0,
                Math.min(
                    height - top,
                    surfaceLocation[1] + height
                        - bottomLocation[1]));
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

        int contentX =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_X];
        int contentY =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_Y];
        int contentWidth =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_WIDTH];
        int contentHeight =
            (int)snapshot[OctavoSurfaceView.STATE_CONTENT_HEIGHT];
        assertTrue(contentX >= 0);
        assertTrue(contentWidth > 0);
        assertTrue(contentHeight > 0);
        assertTrue(contentY >= occlusion.top);
        assertTrue(
            contentY + contentHeight
                <= occlusion.height - occlusion.bottom);
    }

    private static void assertChromeOcclusionEquals(
        ChromeOcclusion expected,
        ChromeOcclusion actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertEquals(expected.top, actual.top);
        assertEquals(expected.bottom, actual.bottom);
        assertEquals(expected.topViewHeight, actual.topViewHeight);
        assertEquals(
            expected.bottomViewHeight, actual.bottomViewHeight);
    }

    private static void assertChromeOcclusionFrame(
        ActivityScenario<OctavoActivity> scenario,
        long[] snapshot,
        ChromeOcclusion occlusion)
        throws InterruptedException {
        Bitmap frame = copyFrame(surface(scenario));
        assertNotNull(frame);
        try {
            OctavoDesignTokens tokens = OctavoDesignTokens.forAppearance(
                appearanceFromState(snapshot));
            int pageX =
                (int)snapshot[
                    OctavoSurfaceView.STATE_PAGE_SURFACE_X];
            int pageY =
                (int)snapshot[
                    OctavoSurfaceView.STATE_PAGE_SURFACE_Y];
            int pageWidth =
                (int)snapshot[
                    OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
            int pageHeight =
                (int)snapshot[
                    OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT];
            int edge = Math.max(3, Math.min(24, pageWidth / 10));
            int left = pageX + edge;
            int right = pageX + pageWidth - edge;
            int topStart = Math.max(pageY, 0);
            int topEnd = Math.min(
                pageY + pageHeight, occlusion.top);
            int bottomStart = Math.max(
                pageY, occlusion.height - occlusion.bottom);
            int bottomEnd = Math.min(
                pageY + pageHeight, occlusion.height);
            assertReaderOcclusionRegion(
                frame,
                left,
                topStart,
                right,
                topEnd,
                tokens,
                "measured top chrome occlusion");
            assertReaderOcclusionRegion(
                frame,
                left,
                bottomStart,
                right,
                bottomEnd,
                tokens,
                "measured bottom chrome occlusion");
        } finally {
            frame.recycle();
        }
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
        assertTrue(pageY >= 0);
        assertTrue(pageWidth > 0);
        assertTrue(pageHeight > 0);
        assertTrue(pageX + pageWidth <= width);
        assertTrue(pageY + pageHeight <= height);
        assertTrue(contentX >= pageX);
        assertTrue(contentY >= pageY);
        assertTrue(contentWidth > 0);
        assertTrue(contentHeight > 0);
        assertTrue(contentX + contentWidth <= pageX + pageWidth);
        assertTrue(contentY + contentHeight <= pageY + pageHeight);
        assertTrue(top > 0);
        assertTrue(bottom > 0);
        assertTrue(contentY >= top);
        assertTrue(contentY + contentHeight <= height - bottom);
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

    private static void assertChromeViewsVisible(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            assertEquals(
                View.VISIBLE,
                activity.findViewById(
                    R.id.octavo_reader_top_chrome).getVisibility());
            assertEquals(
                View.VISIBLE,
                activity.findViewById(
                    R.id.octavo_reader_bottom_chrome).getVisibility());
        });
    }

    private static void assertChromeViewsInvisible(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            assertEquals(
                View.INVISIBLE,
                activity.findViewById(
                    R.id.octavo_reader_top_chrome).getVisibility());
            assertEquals(
                View.INVISIBLE,
                activity.findViewById(
                    R.id.octavo_reader_bottom_chrome).getVisibility());
        });
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
            char candidate = (char)(
                OctavoTypography.FIRST_CODEPOINT + glyph);
            if (!Character.isLetter(candidate)) {
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
                OctavoTypography.HEADER_COUNT
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
        return (char)(OctavoTypography.FIRST_CODEPOINT + bestGlyph);
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
        chapter.append("<p style='text-align:left'>")
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
            chapter.append("<p style='text-align:right'>")
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
            tokens.chromeSurface,
            forbidNearWhite,
            "left background");
        assertExactRegion(
            frame,
            pageX + pageWidth,
            backgroundTop,
            frame.getWidth(),
            backgroundBottom,
            tokens.chromeSurface,
            forbidNearWhite,
            "right background");
    }

    private static void assertReaderOcclusionRegion(
        Bitmap frame,
        int left,
        int top,
        int right,
        int bottom,
        OctavoDesignTokens tokens,
        String label) {
        assertTrue(label + " region is empty", left < right && top < bottom);
        for (int y = top; y < bottom; ++y) {
            for (int x = left; x < right; ++x) {
                int actual = frame.getPixel(x, y);
                assertTrue(
                    label + " contained reader ink or an unexpected "
                        + "transition pixel at " + x + "," + y
                        + ": " + Integer.toHexString(actual),
                    actual == tokens.readerPage
                    || actual == tokens.divider
                    || actual == tokens.dividerMuted);
            }
        }
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
                assertTrue(
                    "Printable glyph had no atlas ink",
                    glyph == 0 || hasInk);
            }
        }
    }

    private static void assertPageStateUnchanged(
        long[] expected,
        long[] actual) {
        assertPageStateUnchangedExcept(expected, actual, -1);
    }

    private static void assertPageStateUnchangedExcept(
        long[] expected,
        long[] actual,
        int ignoredField) {
        int[] fields = {
            OctavoSurfaceView.STATE_SPINE_INDEX,
            OctavoSurfaceView.STATE_PAGE_INDEX,
            OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH,
            OctavoSurfaceView.STATE_VISIBLE_TEXT_SIZE,
            OctavoSurfaceView.STATE_PAGE_COUNT,
            OctavoSurfaceView.STATE_SECTION_COUNT,
            OctavoSurfaceView.STATE_PROGRESS_PAGE_INDEX,
            OctavoSurfaceView.STATE_PROGRESS_PAGE_COUNT,
            OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX,
            OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT,
            OctavoSurfaceView.STATE_DOCUMENT_GENERATION,
            OctavoSurfaceView.STATE_APPEARANCE_GENERATION,
            OctavoSurfaceView.STATE_APPEARANCE_PRESENTED_GENERATION,
            OctavoSurfaceView.STATE_APPEARANCE_APPLY_COUNT,
            OctavoSurfaceView.STATE_APPEARANCE_GATE_BLOCK_COUNT,
            OctavoSurfaceView.STATE_APPEARANCE_FAILURE_COUNT,
            OctavoSurfaceView.STATE_REFLOW_REQUEST_COUNT,
            OctavoSurfaceView.STATE_REFLOW_SUCCESS_COUNT,
            OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT,
            OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_BOUNDARY_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_GATE_BLOCK_COUNT,
            OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING,
            OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT,
            OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX,
            OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET,
            OctavoSurfaceView.STATE_PAGE_FIRST_BYTE,
            OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE,
            OctavoSurfaceView.STATE_PAGE_SURFACE_X,
            OctavoSurfaceView.STATE_PAGE_SURFACE_Y,
            OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH,
            OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT,
        };
        for (int field : fields) {
            if (field == ignoredField) {
                continue;
            }
            assertEquals(
                "Chrome changed native page field " + field,
                expected[field],
                actual[field]);
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
