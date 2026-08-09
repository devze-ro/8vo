package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.SystemClock;
import android.widget.RadioButton;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoStructuralNavigationIntegrationTest {
    private static final long WAIT_MILLIS = 10_000;

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    @Before
    public void clearDurableTestState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
    }

    @Test
    public void rapidJumpIsBusyBackConsumesPendingAndRecreationRestoresOrigin() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            awaitLocationReady(scenario);

            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicReference<long[]> pendingPosition = new AtomicReference<>();
            AtomicInteger first = new AtomicInteger();
            AtomicInteger second = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                view.flushPersistenceForTesting();
                origin.set(view.readingPositionForTesting());
                assertValidPosition(origin.get());
                assertTrue(view.forcePresentFailuresForTesting(8));
                first.set(view.requestPercentageNavigation(100));
                second.set(view.requestPercentageNavigation(25));
                pendingPosition.set(view.readingPositionForTesting());
                activity.onBackPressed();
                assertFalse(activity.libraryVisibleForTesting());
            });

            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, first.get());
            assertEquals(OctavoNative.NAVIGATION_BUSY, second.get());
            assertNotNull(pendingPosition.get());
            assertArrayEquals(new long[] {0, 0, 0}, pendingPosition.get());

            long[] pending = awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 1
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       > state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION],
                "The first structural jump did not remain provisional");
            assertEquals(0,
                         pending[
                             OctavoSurfaceView
                                 .NAVIGATION_STATE_HISTORY_BACK_COUNT]);
            assertEquals(0,
                         pending[
                             OctavoSurfaceView
                                 .NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);

            scenario.recreate();
            awaitReaderReady(scenario);
            long[] restored = readingPosition(scenario);
            assertValidPosition(restored);
            assertArrayEquals(origin.get(), restored);
            long[] recreatedNavigation = navigationState(scenario);
            assertNotNull(recreatedNavigation);
            assertEquals(0,
                         recreatedNavigation[
                             OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
            assertEquals(0,
                         recreatedNavigation[
                             OctavoSurfaceView
                                 .NAVIGATION_STATE_HISTORY_BACK_COUNT]);
            assertEquals(0,
                         recreatedNavigation[
                             OctavoSurfaceView
                                 .NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);
        }
    }

    @Test
    public void openPanelPublishesPendingStateAndRejectsStaleControls() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            awaitLocationReady(scenario);

            scenario.onActivity(OctavoActivity::openNavigationPanelForTesting);
            awaitActivity(
                scenario,
                activity -> {
                    OctavoNavigationPanel panel =
                        activity.navigationPanelForTesting();
                    OctavoNavigation snapshot = panel == null
                        ? null : panel.snapshotForTesting();
                    return snapshot != null && snapshot.isReady()
                        && !snapshot.isPending();
                },
                "8vo did not open a ready navigation panel");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoNavigationPanel panel =
                    activity.navigationPanelForTesting();
                assertNotNull(panel);
                assertTrue(view.forcePresentFailuresForTesting(8));

                long[] before = view.navigationStateForTesting();
                assertNotNull(before);
                long progressGeneration = before[
                    OctavoSurfaceView.NAVIGATION_STATE_PROGRESS_GENERATION];

                panel.goToTabForTesting().performClick();
                panel.percentageInputForTesting().setText("100");
                panel.findViewById(
                    R.id.octavo_navigation_percentage_go).performClick();

                OctavoNavigation pending = panel.snapshotForTesting();
                assertNotNull(pending);
                assertTrue(pending.isPending());
                assertFalse(panel.returnButtonForTesting().isEnabled());
                assertFalse(panel.forwardButtonForTesting().isEnabled());
                assertTrue(
                    panel.contentsListForTesting().getChildCount() > 0);
                for (int index = 0;
                     index < panel.contentsListForTesting().getChildCount();
                     ++index) {
                    assertFalse(panel.contentsListForTesting()
                                    .getChildAt(index).isEnabled());
                }
                assertFalse(panel.chapterInputForTesting().isEnabled());
                assertFalse(panel.locationInputForTesting().isEnabled());
                assertFalse(panel.pageInputForTesting().isEnabled());
                assertFalse(panel.percentageInputForTesting().isEnabled());
                assertFalse(panel.findViewById(
                    R.id.octavo_navigation_chapter_go).isEnabled());
                assertFalse(panel.findViewById(
                    R.id.octavo_navigation_location_go).isEnabled());
                assertFalse(panel.findViewById(
                    R.id.octavo_navigation_page_go).isEnabled());
                assertFalse(panel.findViewById(
                    R.id.octavo_navigation_percentage_go).isEnabled());

                RadioButton page = null;
                RadioButton percentage = null;
                for (int index = 0;
                     index < panel.progressOptionsForTesting()
                         .getChildCount();
                     ++index) {
                    RadioButton option = (RadioButton)
                        panel.progressOptionsForTesting().getChildAt(index);
                    assertFalse(option.isEnabled());
                    if (option.getTag() == OctavoProgressDisplay.PAGE) {
                        page = option;
                    } else if (option.getTag()
                               == OctavoProgressDisplay.PERCENTAGE) {
                        percentage = option;
                    }
                }
                assertNotNull(page);
                assertNotNull(percentage);
                assertTrue(percentage.isChecked());
                page.performClick();
                assertFalse(page.isChecked());
                assertTrue(percentage.isChecked());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           panel.presentedProgressForTesting());

                long[] after = view.navigationStateForTesting();
                assertNotNull(after);
                assertEquals(
                    progressGeneration,
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_GENERATION]);
                assertEquals(
                    1,
                    after[OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
            });
        }
    }

    @Test
    public void retryExhaustionRollsBackStructuralAndProgressRequests() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            awaitLocationReady(scenario);

            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicReference<long[]> structuralStateBefore =
                new AtomicReference<>();
            AtomicReference<long[]> structuralNavigationBefore =
                new AtomicReference<>();
            AtomicInteger structuralResult = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                structuralStateBefore.set(view.nativeStateForTesting());
                structuralNavigationBefore.set(
                    view.navigationStateForTesting());
                assertValidPosition(origin.get());
                assertNotNull(structuralStateBefore.get());
                assertNotNull(structuralNavigationBefore.get());
                assertTrue(view.forcePrePresentFailuresForTesting(5));
                structuralResult.set(
                    view.requestPercentageNavigation(100));
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         structuralResult.get());

            AtomicReference<long[]> structuralRecoveredState =
                new AtomicReference<>();
            AtomicReference<long[]> structuralRecoveredNavigation =
                new AtomicReference<>();
            awaitActivity(
                scenario,
                activity -> {
                    OctavoSurfaceView view = surface(activity);
                    long[] state = view.nativeStateForTesting();
                    long[] navigation = view.navigationStateForTesting();
                    if (state == null || navigation == null) {
                        return false;
                    }
                    boolean recovered =
                        state[
                            OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                            == structuralStateBefore.get()[
                                OctavoSurfaceView
                                    .STATE_RENDER_FAILURE_COUNT] + 5
                        && state[OctavoSurfaceView.STATE_FRAME_COUNT]
                            == structuralStateBefore.get()[
                                OctavoSurfaceView.STATE_FRAME_COUNT] + 1
                        && state[
                            OctavoSurfaceView
                                .STATE_HOST_PRESENTATION_PENDING] == 0
                        && navigation[
                            OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                        && navigation[
                            OctavoSurfaceView
                                .NAVIGATION_STATE_SEMANTIC_GENERATION]
                            == structuralNavigationBefore.get()[
                                OctavoSurfaceView
                                    .NAVIGATION_STATE_SEMANTIC_GENERATION]
                                + 1
                        && navigation[
                            OctavoSurfaceView
                                .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                            == structuralNavigationBefore.get()[
                                OctavoSurfaceView
                                    .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                        && !view.presentationFailureNotifiedForTesting();
                    if (recovered) {
                        structuralRecoveredState.set(state.clone());
                        structuralRecoveredNavigation.set(
                            navigation.clone());
                    }
                    return recovered;
                },
                "The exhausted structural jump did not restore and present "
                    + "its origin exactly once");

            assertArrayEquals(origin.get(), readingPosition(scenario));
            assertEquals(
                structuralNavigationBefore.get()[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_BACK_COUNT],
                structuralRecoveredNavigation.get()[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_BACK_COUNT]);
            assertEquals(
                structuralNavigationBefore.get()[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_FORWARD_COUNT],
                structuralRecoveredNavigation.get()[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);
            scenario.onActivity(activity -> assertEquals(
                OctavoSurfaceView.navigationPresentationFailureMessage(1),
                activity.lastOpenErrorForTesting()));

            AtomicReference<long[]> progressStateBefore =
                new AtomicReference<>();
            AtomicReference<long[]> progressNavigationBefore =
                new AtomicReference<>();
            AtomicLong progressSaveCountBefore = new AtomicLong();
            AtomicInteger progressResult = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                progressStateBefore.set(view.nativeStateForTesting());
                progressNavigationBefore.set(
                    view.navigationStateForTesting());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressDisplayForTesting());
                long saveCount = activity.progressStoreForTesting()
                    .saveSuccessCountForTesting();
                assertTrue(saveCount >= 1);
                progressSaveCountBefore.set(saveCount);
                assertTrue(view.forcePrePresentFailuresForTesting(5));
                progressResult.set(view.requestProgressDisplay(
                    OctavoProgressDisplay.PAGE));
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         progressResult.get());

            AtomicReference<long[]> progressRecoveredState =
                new AtomicReference<>();
            awaitActivity(
                scenario,
                activity -> {
                    OctavoSurfaceView view = surface(activity);
                    long[] state = view.nativeStateForTesting();
                    long[] navigation = view.navigationStateForTesting();
                    if (state == null || navigation == null) {
                        return false;
                    }
                    boolean recovered =
                        state[
                            OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]
                            == progressStateBefore.get()[
                                OctavoSurfaceView
                                    .STATE_RENDER_FAILURE_COUNT] + 5
                        && state[OctavoSurfaceView.STATE_FRAME_COUNT]
                            == progressStateBefore.get()[
                                OctavoSurfaceView.STATE_FRAME_COUNT] + 1
                        && state[
                            OctavoSurfaceView
                                .STATE_HOST_PRESENTATION_PENDING] == 0
                        && navigation[
                            OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                        && navigation[
                            OctavoSurfaceView
                                .NAVIGATION_STATE_PROGRESS_GENERATION]
                            == progressNavigationBefore.get()[
                                OctavoSurfaceView
                                    .NAVIGATION_STATE_PROGRESS_GENERATION]
                                + 1
                        && navigation[
                            OctavoSurfaceView
                                .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                            == progressNavigationBefore.get()[
                                OctavoSurfaceView
                                    .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                        && navigation[
                            OctavoSurfaceView
                                .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE]
                            == OctavoProgressDisplay.PERCENTAGE.nativeId()
                        && navigation[
                            OctavoSurfaceView
                                .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]
                            == OctavoProgressDisplay.PERCENTAGE.nativeId()
                        && !view.presentationFailureNotifiedForTesting();
                    if (recovered) {
                        progressRecoveredState.set(state.clone());
                    }
                    return recovered;
                },
                "The exhausted progress change did not restore and present "
                    + "its previous mode exactly once");

            scenario.onActivity(activity -> {
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressDisplayForTesting());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           surface(activity).presentedProgressDisplay());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressStoreForTesting().current());
                assertEquals(progressSaveCountBefore.get(),
                             activity.progressStoreForTesting()
                                 .saveSuccessCountForTesting());
                assertEquals(
                    OctavoSurfaceView
                        .navigationPresentationFailureMessage(1),
                    activity.lastOpenErrorForTesting());
            });

            SystemClock.sleep(200);
            scenario.onActivity(activity -> {
                long[] stable = surface(activity).nativeStateForTesting();
                assertNotNull(stable);
                assertEquals(
                    progressRecoveredState.get()[
                        OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT],
                    stable[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
                assertEquals(
                    progressRecoveredState.get()[
                        OctavoSurfaceView.STATE_FRAME_COUNT],
                    stable[OctavoSurfaceView.STATE_FRAME_COUNT]);
            });
        }
    }

    @Test
    public void unpresentedProgressChoiceIsNotSavedAcrossRecreation() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);

            AtomicInteger result = new AtomicInteger();
            AtomicLong progressSaveCountBefore = new AtomicLong();
            AtomicReference<File> progressFile = new AtomicReference<>();
            scenario.onActivity(activity -> {
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressDisplayForTesting());
                OctavoProgressStore store =
                    activity.progressStoreForTesting();
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           store.current());
                long saveCount = store.saveSuccessCountForTesting();
                assertTrue(saveCount >= 1);
                progressSaveCountBefore.set(saveCount);
                progressFile.set(store.progressFileForTesting());
                assertTrue(progressFile.get().isFile());

                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(8));
                result.set(view.requestProgressDisplay(
                    OctavoProgressDisplay.PAGE));
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressDisplayForTesting());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           store.current());
                assertEquals(progressSaveCountBefore.get(),
                             store.saveSuccessCountForTesting());
                assertTrue(progressFile.get().isFile());
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, result.get());

            long[] pending = awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 1
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION]
                       > state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE]
                       == OctavoProgressDisplay.PAGE.nativeId()
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]
                       == OctavoProgressDisplay.PERCENTAGE.nativeId(),
                "The progress choice did not remain presentation-gated");
            assertEquals(1,
                         pending[OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
            scenario.onActivity(activity -> {
                OctavoProgressStore store =
                    activity.progressStoreForTesting();
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           store.current());
                assertEquals(progressSaveCountBefore.get(),
                             store.saveSuccessCountForTesting());
                assertTrue(store.progressFileForTesting().isFile());
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressDisplayForTesting());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           surface(activity).presentedProgressDisplay());
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           activity.progressStoreForTesting().current());
                assertEquals(0,
                             activity.progressStoreForTesting()
                                 .saveSuccessCountForTesting());
                assertTrue(activity.progressStoreForTesting()
                               .progressFileForTesting().isFile());
            });
        }
    }

    @Test
    public void presentedProgressChoiceIsPersistedAndRestored() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);

            AtomicInteger result = new AtomicInteger();
            scenario.onActivity(activity -> result.set(
                surface(activity).requestProgressDisplay(
                    OctavoProgressDisplay.LOCATION)));
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, result.get());

            awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION] > 0
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]
                       == OctavoProgressDisplay.LOCATION.nativeId(),
                "The presented progress choice was not committed");

            scenario.onActivity(activity -> {
                activity.flushProgressPersistenceForTesting();
                assertSame(OctavoProgressDisplay.LOCATION,
                           activity.progressDisplayForTesting());
                assertSame(OctavoProgressDisplay.LOCATION,
                           surface(activity).presentedProgressDisplay());
                OctavoProgressStore store =
                    activity.progressStoreForTesting();
                assertSame(OctavoProgressDisplay.LOCATION,
                           store.current());
                assertTrue(store.saveSuccessCountForTesting() >= 1);
                assertEquals(0, store.saveFailureCountForTesting());
                assertTrue(store.progressFileForTesting().isFile());
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertSame(OctavoProgressDisplay.LOCATION,
                           activity.progressDisplayForTesting());
                assertSame(OctavoProgressDisplay.LOCATION,
                           surface(activity).presentedProgressDisplay());
                assertSame(OctavoProgressDisplay.LOCATION,
                           activity.progressStoreForTesting().current());
                assertTrue(activity.progressStoreForTesting()
                               .loadSuccessCountForTesting() >= 1);
                long[] navigation =
                    surface(activity).navigationStateForTesting();
                assertNotNull(navigation);
                assertEquals(
                    OctavoProgressDisplay.LOCATION.nativeId(),
                    navigation[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]);
                assertEquals(
                    navigation[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION],
                    navigation[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]);
            });
        }
    }

    @Test
    public void presentedJumpAndReturnRestoreTheExactSemanticOrigin() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            awaitLocationReady(scenario);

            long[] origin = readingPosition(scenario);
            assertValidPosition(origin);
            long[] before = navigationState(scenario);
            assertNotNull(before);

            AtomicInteger jumpResult = new AtomicInteger();
            scenario.onActivity(activity -> jumpResult.set(
                surface(activity).requestPercentageNavigation(100)));
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         jumpResult.get());

            long[] jumpedNavigation = awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       > before[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_GENERATION]
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_HISTORY_BACK_COUNT] == 1,
                "The structural jump was not presented and recorded");
            long[] target = readingPosition(scenario);
            assertValidPosition(target);
            assertFalse(origin[1] == target[1] && origin[2] == target[2]);
            assertEquals(0,
                         jumpedNavigation[
                             OctavoSurfaceView
                                 .NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);

            AtomicInteger returnResult = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.canReturnInHistory());
                returnResult.set(view.requestHistoryNavigation(false));
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         returnResult.get());

            awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       > jumpedNavigation[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_GENERATION]
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                    && state[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_HISTORY_FORWARD_COUNT] == 1,
                "Return was not presented into forward history");
            assertArrayEquals(origin, readingPosition(scenario));
        }
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
                long[] navigation = view.navigationStateForTesting();
                return state != null
                    && state.length == OctavoSurfaceView.STATE_FIELD_COUNT
                    && state[OctavoSurfaceView.STATE_RESUMED] == 1
                    && state[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && state[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                    && state[
                        OctavoSurfaceView
                            .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                    && state[
                        OctavoSurfaceView
                            .STATE_REFLOW_PRESENTATION_PENDING] == 0
                    && state[
                        OctavoSurfaceView
                            .STATE_HOST_PRESENTATION_PENDING] == 0
                    && navigation != null
                    && navigation.length
                       == OctavoSurfaceView.NAVIGATION_STATE_FIELD_COUNT
                    && navigation[
                        OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0;
            },
            "8vo did not present a settled reader frame");
    }

    private static void awaitLocationReady(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> {
                long[] state = surface(activity)
                    .locationCacheStateForTesting();
                return state != null && state.length == 10
                    && state[0] == 1 && state[1] == 1;
            },
            "8vo did not complete deterministic location warming");
    }

    private static long[] awaitNavigation(
        ActivityScenario<OctavoActivity> scenario,
        java.util.function.Predicate<long[]> condition,
        String failureMessage) {
        AtomicReference<long[]> match = new AtomicReference<>();
        awaitActivity(
            scenario,
            activity -> {
                long[] state = surface(activity)
                    .navigationStateForTesting();
                if (state != null && condition.test(state)) {
                    match.set(state.clone());
                    return true;
                }
                return false;
            },
            failureMessage);
        assertNotNull(match.get());
        return match.get();
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
                InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();
                return;
            }
            SystemClock.sleep(25);
        }
        fail(failureMessage);
    }

    private static long[] navigationState(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).navigationStateForTesting()));
        return result.get();
    }

    private static long[] readingPosition(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).readingPositionForTesting()));
        return result.get();
    }

    private static void assertValidPosition(long[] position) {
        assertNotNull(position);
        assertEquals(3, position.length);
        assertEquals(1, position[0]);
        assertTrue(position[1] >= 0);
        assertTrue(position[2] >= 0);
    }
}
