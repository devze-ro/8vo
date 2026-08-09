package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoReadingPositionIntegrationTest {
    private static final long WAIT_MILLIS = 12_000;
    private static final String REMOTE_A =
        "11111111111111111111111111111111";
    private static final String REMOTE_B =
        "22222222222222222222222222222222";
    private static final String REMOTE_C =
        "33333333333333333333333333333333";
    private static final String REMOTE_D =
        "44444444444444444444444444444444";
    private static final String REMOTE_E =
        "55555555555555555555555555555555";

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    @Before
    public void clearDurableTestState() {
        clearAllDurableTestState();
    }

    @After
    public void clearDurableTestStateAfterward() {
        clearAllDurableTestState();
    }

    private static void clearAllDurableTestState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        OctavoNoteDraftStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
    }

    @Test
    public void candidateWaitsForPostedFrameAndSamePageIsNotOffered() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(1));

                // The fixture starts in spine zero. This valid foreign
                // anchor is merged before any frame can be posted.
                activity.simulateRemotePositionForTesting(
                    REMOTE_A, 1, 1, 0);
                assertNull(activity.positionPromptForTesting());
            });

            OctavoReadingPositionPrompt prompt = awaitChoicePrompt(scenario);
            assertNotNull(prompt);
            scenario.onActivity(activity -> {
                long[] state = surface(activity).nativeStateForTesting();
                assertNotNull(state);
                assertTrue(state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0);
                assertTrue(
                    state[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT] > 0);
                assertEquals(1,
                    activity.readingPositionStoreForTesting()
                        .reviewEpoch(OctavoFixture.SHA256));
                activity.positionPromptForTesting()
                    .stayHereForTesting().performClick();
            });
            awaitNoPrompt(scenario);

            AtomicReference<long[]> samePage = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                long[] state = view.nativeStateForTesting();
                long[] position = view.readingPositionForTesting();
                assertValidPosition(position);
                assertNotNull(state);
                long first = state[
                    OctavoSurfaceView.STATE_PAGE_FIRST_BYTE];
                long onePast = state[
                    OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE];
                assertTrue(onePast - first > 1);
                long byteOffset = first + 1;
                assertTrue(byteOffset >= position[2]);
                samePage.set(new long[] {position[1], byteOffset});
                activity.simulateRemotePositionForTesting(
                    REMOTE_B, 1, position[1], byteOffset);
                assertNull(activity.positionPromptForTesting());
            });
            assertPromptRemainsAbsent(scenario, 350);

            scenario.onActivity(activity -> {
                long[] position = surface(activity)
                    .readingPositionForTesting();
                List<OctavoReadingPositionStore.Candidate> candidates =
                    activity.readingPositionStoreForTesting()
                        .reviewCandidates(
                            OctavoFixture.SHA256,
                            position[1], position[2]);
                assertEquals(1, candidates.size());
                assertEquals(REMOTE_B, candidates.get(0).deviceId);
                assertEquals(samePage.get()[0],
                             candidates.get(0).spineIndex);
                assertEquals(samePage.get()[1],
                             candidates.get(0).byteOffset);
                assertEquals(OctavoReadingPositionStore.Decision.NONE,
                             candidates.get(0).decision);
            });
        }
    }

    @Test
    public void stayDoesNotMoveSurvivesRecreationAndPromptIsAccessible() {
        Context context = ApplicationProvider.getApplicationContext();
        assertTrue(new OctavoAppearanceStore(context).save(
            OctavoAppearance.defaults().withReducedMotion(true)));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicReference<long[]> target = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                assertValidPosition(origin.get());
                target.set(nextQualifiedTarget(view, false));
                assertTrue(view.requestFocus());
                activity.simulateRemotePositionForTesting(
                    REMOTE_C, 7, target.get()[0], target.get()[1]);
            });
            awaitChoicePrompt(scenario);

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            awaitChoicePrompt(scenario);

            scenario.onActivity(activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                assertPromptAccessibility(activity, prompt);
                assertEquals(0.0f, prompt.getTranslationY(), 0.0f);
                assertEquals(1.0f, prompt.getAlpha(), 0.0f);
                assertEquals(1,
                    surface(activity).nativeStateForTesting()[
                        OctavoSurfaceView.STATE_REDUCED_MOTION]);
                assertEquals(0,
                    activity.positionPromptMotionDurationForTesting());
                assertLargeTextWraps(activity);
                prompt.stayHereForTesting().performClick();
            });
            awaitNoPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));

            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            assertArrayEquals(origin.get(), readingPosition(scenario));
        }
    }

    @Test
    public void goIsPendingAcrossFailureAndRecreationUntilRetryPosts() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicReference<long[]> target = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                assertValidPosition(origin.get());
                target.set(nextQualifiedTarget(view, false));
                activity.simulateRemotePositionForTesting(
                    REMOTE_D, 19, target.get()[0], target.get()[1]);
            });
            awaitChoicePrompt(scenario);

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePrePresentFailuresForTesting(5));
                activity.positionPromptForTesting()
                    .goThereForTesting().performClick();
            });
            awaitRetryPrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoReadingPositionStore.Candidate pending =
                    activity.readingPositionStoreForTesting()
                        .pendingGo(OctavoFixture.SHA256);
                assertNotNull(pending);
                assertEquals(REMOTE_D, pending.deviceId);
                assertEquals(19, pending.sequence);
                assertEquals(OctavoReadingPositionStore.Decision.GO_PENDING,
                             pending.decision);
                assertArrayEquals(origin.get(),
                    surface(activity).readingPositionForTesting());
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            awaitRetryPrompt(scenario);
            scenario.onActivity(activity -> {
                assertNotNull(activity.readingPositionStoreForTesting()
                    .pendingGo(OctavoFixture.SHA256));
                activity.positionPromptForTesting()
                    .retryForTesting().performClick();
            });

            awaitActivity(
                scenario,
                activity -> {
                    OctavoSurfaceView view = surface(activity);
                    if (activity.positionPromptForTesting() != null
                        || activity.readingPositionStoreForTesting()
                            .pendingGo(OctavoFixture.SHA256) != null) {
                        return false;
                    }
                    long[] state = view.nativeStateForTesting();
                    long[] position = view.readingPositionForTesting();
                    return state != null && position != null
                        && position[0] == 1
                        && state[
                            OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]
                           == target.get()[0]
                        && state[
                            OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                           <= target.get()[1]
                        && state[
                            OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE]
                           > target.get()[1];
                },
                "Go there was not completed by an exact containing post");

            long[] accepted = readingPosition(scenario);
            assertValidPosition(accepted);
            assertFalse(sameAnchor(origin.get(), accepted));
            scenario.onActivity(activity -> {
                OctavoReadingPositionPortable.Lane local =
                    activity.readingPositionStoreForTesting()
                        .localLane(OctavoFixture.SHA256);
                assertNotNull(local);
                assertEquals(accepted[1], local.spineIndex);
                assertEquals(accepted[2], local.byteOffset);
                assertTrue(activity.readingPositionStoreForTesting()
                    .reviewCandidates(
                        OctavoFixture.SHA256,
                        accepted[1], accepted[2]).isEmpty());
            });
        }
    }

    @Test
    public void backAndLocalMoveDismissOnlyTheCurrentReviewEpoch() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> target = new AtomicReference<>();
            AtomicReference<Long> firstEpoch = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                target.set(nextQualifiedTarget(view, true));
                assertTrue(view.requestFocus());
                firstEpoch.set(activity.readingPositionStoreForTesting()
                    .reviewEpoch(OctavoFixture.SHA256));
                activity.simulateRemotePositionForTesting(
                    REMOTE_E, 23, target.get()[0], target.get()[1]);
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(OctavoActivity::onBackPressed);
            awaitNoPrompt(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity).hasFocus(),
                "Back did not restore focus to the reader surface");

            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity -> assertEquals(
                firstEpoch.get().longValue(),
                activity.readingPositionStoreForTesting()
                    .reviewEpoch(OctavoFixture.SHA256)));

            explicitReopen(scenario);
            awaitChoicePrompt(scenario);
            AtomicReference<Long> secondEpoch = new AtomicReference<>();
            AtomicInteger moveResult = new AtomicInteger();
            scenario.onActivity(activity -> {
                secondEpoch.set(activity.readingPositionStoreForTesting()
                    .reviewEpoch(OctavoFixture.SHA256));
                assertEquals(firstEpoch.get() + 1,
                             secondEpoch.get().longValue());
                activity.readingPositionStoreForTesting()
                    .failNextPublishForTesting();
                moveResult.set(surface(activity).requestPageNavigation(2));
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         moveResult.get());
            awaitReaderReady(scenario);
            awaitRetryPrompt(scenario);
            assertRetryPromptRemains(scenario, 450);
            scenario.onActivity(OctavoActivity::onBackPressed);
            awaitNoPrompt(scenario);

            long[] moved = readingPosition(scenario);
            assertTrue(compareAnchor(
                moved[1], moved[2], target.get()[0], target.get()[1]) < 0);
            scenario.onActivity(activity -> {
                OctavoReadingPositionStore store =
                    activity.readingPositionStoreForTesting();
                OctavoReadingPositionPortable.Lane local =
                    store.localLane(OctavoFixture.SHA256);
                assertNotNull(local);
                assertEquals(moved[1], local.spineIndex);
                assertEquals(moved[2], local.byteOffset);
                assertTrue(store.reviewCandidates(
                    OctavoFixture.SHA256,
                    moved[1], moved[2]).isEmpty());
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            explicitReopen(scenario);
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> assertEquals(
                secondEpoch.get() + 1,
                activity.readingPositionStoreForTesting()
                    .reviewEpoch(OctavoFixture.SHA256)));
        }
    }

    @Test
    public void overboundReaderClampShowsOnlyRetryAndNeverAccepts() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> origin = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(surface(activity).readingPositionForTesting());
                assertValidPosition(origin.get());
                activity.simulateRemotePositionForTesting(
                    REMOTE_A, 31, origin.get()[1], Long.MAX_VALUE);
            });
            awaitRetryPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));

            scenario.onActivity(activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                assertNotNull(prompt);
                assertEquals(View.GONE,
                             prompt.goThereForTesting().getVisibility());
                assertEquals(View.GONE,
                             prompt.stayHereForTesting().getVisibility());
                OctavoReadingPositionStore store =
                    activity.readingPositionStoreForTesting();
                assertNull(store.pendingGo(OctavoFixture.SHA256));
                List<OctavoReadingPositionStore.Candidate> candidates =
                    store.reviewCandidates(
                        OctavoFixture.SHA256,
                        origin.get()[1], origin.get()[2]);
                assertEquals(1, candidates.size());
                OctavoReadingPositionStore.Candidate retained =
                    candidates.get(0);
                assertEquals(REMOTE_A, retained.deviceId);
                assertEquals(Long.MAX_VALUE, retained.byteOffset);
                assertEquals(OctavoReadingPositionStore.Decision.NONE,
                             retained.decision);
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            awaitRetryPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));
        }
    }

    @Test
    public void failedGoChoiceRetrySurvivesActivityRecreation() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicReference<long[]> target = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                target.set(nextQualifiedTarget(view, false));
                activity.simulateRemotePositionForTesting(
                    REMOTE_A, 41, target.get()[0], target.get()[1]);
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                activity.readingPositionStoreForTesting()
                    .failNextPublishForTesting();
                activity.positionPromptForTesting()
                    .goThereForTesting().performClick();
            });
            awaitRetryPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));
            scenario.onActivity(activity -> assertNull(
                activity.readingPositionStoreForTesting()
                    .pendingGo(OctavoFixture.SHA256)));

            scenario.recreate();
            awaitReaderReady(scenario);
            awaitRetryPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));
            scenario.onActivity(activity -> {
                assertNull(activity.readingPositionStoreForTesting()
                    .pendingGo(OctavoFixture.SHA256));
                activity.positionPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitActivity(
                scenario,
                activity -> {
                    OctavoReadingPositionPortable.Lane local =
                        activity.readingPositionStoreForTesting()
                            .localLane(OctavoFixture.SHA256);
                    return activity.positionPromptForTesting() == null
                        && activity.readingPositionStoreForTesting()
                               .pendingGo(OctavoFixture.SHA256) == null
                        && local != null
                        && local.spineIndex == target.get()[0]
                        && local.byteOffset == target.get()[1];
                },
                "Recreated failed Go there did not retain its exact Retry");
        }
    }

    @Test
    public void failedStayAndBackRetriesSurviveActivityRecreation() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicReference<long[]> target = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                target.set(nextQualifiedTarget(view, false));
                activity.simulateRemotePositionForTesting(
                    REMOTE_B, 43, target.get()[0], target.get()[1]);
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                activity.readingPositionStoreForTesting()
                    .failNextPublishForTesting();
                activity.positionPromptForTesting()
                    .stayHereForTesting().performClick();
            });
            awaitRetryPrompt(scenario);
            scenario.recreate();
            awaitReaderReady(scenario);
            awaitRetryPrompt(scenario);
            scenario.onActivity(activity -> activity
                .positionPromptForTesting().retryForTesting().performClick());
            awaitNoPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));

            scenario.onActivity(activity -> activity
                .simulateRemotePositionForTesting(
                    REMOTE_C, 47, target.get()[0], target.get()[1]));
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                activity.readingPositionStoreForTesting()
                    .failNextPublishForTesting();
                activity.onBackPressed();
            });
            awaitRetryPrompt(scenario);
            scenario.recreate();
            awaitReaderReady(scenario);
            awaitRetryPrompt(scenario);
            scenario.onActivity(activity -> activity
                .positionPromptForTesting().retryForTesting().performClick());
            awaitNoPrompt(scenario);
            assertArrayEquals(origin.get(), readingPosition(scenario));

            explicitReopen(scenario);
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> assertEquals(
                REMOTE_C,
                activity.pendingPositionCandidateForTesting().deviceId));
        }
    }

    @Test
    public void staleReplayRestoresFocusAndDefersUnrelatedFailureBanner() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> origin = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                long[] target = nextQualifiedTarget(view, false);
                activity.simulateRemotePositionForTesting(
                    REMOTE_D, 53, target[0], target[1]);
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                assertTrue(prompt.goThereForTesting().requestFocus());
                activity.showFailureForTesting(
                    "An unrelated reader failure remains available");
                View banner = activity.findViewById(
                    R.id.octavo_reader_failure);
                assertNotNull(banner);
                assertEquals(View.INVISIBLE, banner.getVisibility());
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    banner.getImportantForAccessibility());

                assertTrue(activity.simulateRemotePositionForTesting(
                    REMOTE_D, 54, origin.get()[1], origin.get()[2]));
                assertNull(activity.positionPromptForTesting());
                assertEquals(View.VISIBLE, banner.getVisibility());
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                    banner.getImportantForAccessibility());
                assertTrue(surface(activity).hasFocus());
            });
            awaitActivity(
                scenario,
                activity -> activity.positionPromptForTesting() == null
                    && surface(activity).hasFocus(),
                "Stale replay did not restore focus to the reader surface");
        }
    }

    @Test
    public void backCannotDismissGoAfterTargetPostBeforeDurableCompletion() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<long[]> target = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                target.set(nextQualifiedTarget(view, false));
                activity.simulateRemotePositionForTesting(
                    REMOTE_E, 59, target.get()[0], target.get()[1]);
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                activity.positionPromptForTesting()
                    .goThereForTesting().performClick();
                activity.readingPositionStoreForTesting()
                    .failNextPublishForTesting();
            });
            awaitRetryPrompt(scenario);
            assertRetryPromptRemains(scenario, 450);
            scenario.onActivity(activity -> {
                long[] position = surface(activity)
                    .readingPositionForTesting();
                assertEquals(target.get()[0], position[1]);
                assertEquals(target.get()[1], position[2]);
                assertNotNull(activity.readingPositionStoreForTesting()
                    .pendingGo(OctavoFixture.SHA256));
                activity.onBackPressed();
                assertNotNull(activity.positionPromptForTesting());
                assertNotNull(activity.readingPositionStoreForTesting()
                    .pendingGo(OctavoFixture.SHA256));
                assertEquals(View.VISIBLE,
                    activity.positionPromptForTesting()
                        .retryForTesting().getVisibility());
                activity.positionPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitNoPrompt(scenario);
            scenario.onActivity(activity -> assertNull(
                activity.readingPositionStoreForTesting()
                    .pendingGo(OctavoFixture.SHA256)));
        }
    }

    private static void openFixtureAndAwaitReader(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            assertTrue(activity.libraryVisibleForTesting());
            assertTrue(activity.openFixtureForTesting());
        });
        awaitReaderReady(scenario);
    }

    private static void explicitReopen(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.closeBookForTesting();
            assertTrue(activity.libraryVisibleForTesting());
            assertTrue(activity.openFixtureForTesting());
        });
        awaitReaderReady(scenario);
    }

    private static OctavoSurfaceView surface(OctavoActivity activity) {
        OctavoSurfaceView view = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        assertNotNull(view);
        return view;
    }

    private static OctavoReadingPositionPrompt awaitChoicePrompt(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoReadingPositionPrompt> result =
            new AtomicReference<>();
        awaitActivity(
            scenario,
            activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                if (prompt == null || !prompt.isShown()
                    || prompt.getWidth() <= 0
                    || !prompt.goThereForTesting().isEnabled()
                    || !prompt.stayHereForTesting().isEnabled()
                    || prompt.retryForTesting().getVisibility()
                       != View.GONE) {
                    return false;
                }
                result.set(prompt);
                return true;
            },
            "8vo did not show a settled reading-position choice");
        return result.get();
    }

    private static void awaitRetryPrompt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                return prompt != null && prompt.isShown()
                    && activity.positionAwaitingExplicitRetryForTesting()
                    && prompt.retryForTesting().getVisibility()
                       == View.VISIBLE
                    && prompt.retryForTesting().isEnabled()
                    && !prompt.goThereForTesting().isEnabled()
                    && !prompt.stayHereForTesting().isEnabled()
                    && !prompt.statusForTesting().getText().toString()
                        .trim().isEmpty();
            },
            "Failed Go there did not expose its visible Retry state");
    }

    private static void assertRetryPromptRemains(
        ActivityScenario<OctavoActivity> scenario,
        long durationMillis) {
        long deadline = SystemClock.uptimeMillis() + durationMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                assertNotNull(prompt);
                assertTrue(activity.positionAwaitingExplicitRetryForTesting());
                assertEquals(View.VISIBLE,
                             prompt.retryForTesting().getVisibility());
                assertTrue(prompt.retryForTesting().isEnabled());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(25);
        }
    }

    private static void awaitNoPrompt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> activity.positionPromptForTesting() == null
                && !activity.positionAwaitingExplicitRetryForTesting(),
            "The reading-position prompt did not close");
    }

    private static void assertPromptRemainsAbsent(
        ActivityScenario<OctavoActivity> scenario,
        long durationMillis) {
        long deadline = SystemClock.uptimeMillis() + durationMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> assertNull(
                activity.positionPromptForTesting()));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(25);
        }
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

    private static long[] nextQualifiedTarget(OctavoSurfaceView view,
                                               boolean preferFarSpine) {
        long[] state = view.nativeStateForTesting();
        long[] position = view.readingPositionForTesting();
        assertNotNull(state);
        assertValidPosition(position);
        long currentSpine = position[1];
        long onePast = state[
            OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE];
        long[][] probes = preferFarSpine
            ? new long[][] {
                {currentSpine + 2, 0},
                {currentSpine + 1, 0},
                {currentSpine, onePast}
            }
            : new long[][] {
                {currentSpine, onePast},
                {currentSpine + 1, 0},
                {currentSpine + 2, 0}
            };
        for (long[] probe : probes) {
            long[] qualified = view.qualifySyncedReadingPosition(
                probe[0], probe[1]);
            if (qualified != null
                && qualified[0] == OctavoNative.NAVIGATION_ACCEPTED
                && compareAnchor(
                    probe[0], probe[1], position[1], position[2]) > 0
                && (probe[0] != position[1]
                    || probe[1] < state[
                        OctavoSurfaceView.STATE_PAGE_FIRST_BYTE]
                    || probe[1] >= onePast)) {
                return probe;
            }
        }
        fail("The deterministic fixture had no exact later-page anchor");
        return null;
    }

    private static long[] readingPosition(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).readingPositionForTesting()));
        return result.get();
    }

    private static void assertPromptAccessibility(
        OctavoActivity activity,
        OctavoReadingPositionPrompt prompt) {
        assertNotNull(prompt);
        assertTrue(prompt.headingForTesting().getText().toString()
            .startsWith("Another device is at "));
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                     prompt.statusForTesting()
                         .getAccessibilityLiveRegion());
        if (Build.VERSION.SDK_INT >= 28) {
            assertTrue(prompt.headingForTesting().isAccessibilityHeading());
            assertEquals("Reading position confirmation",
                         prompt.getAccessibilityPaneTitle().toString());
        }
        int minimum = Math.round(
            48 * activity.getResources().getDisplayMetrics().density);
        assertTrue(prompt.goThereForTesting().getHeight() >= minimum);
        assertTrue(prompt.stayHereForTesting().getHeight() >= minimum);
        assertTrue(prompt.retryForTesting().getMinHeight() >= minimum);
        assertTrue(prompt.goThereForTesting().isFocusableInTouchMode());
        assertTrue(prompt.stayHereForTesting().isFocusableInTouchMode());
        assertTrue(prompt.retryForTesting().isFocusableInTouchMode());
        assertTrue(prompt.headingForTesting().getMaxLines() > 1);
        assertNotNull(prompt.scrollForTesting());
        assertTrue(prompt.goThereForTesting().hasFocus()
            || prompt.headingForTesting().hasFocus());
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            surface(activity).getImportantForAccessibility());
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            activity.findViewById(R.id.octavo_reader_top_chrome)
                .getImportantForAccessibility());
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            activity.findViewById(R.id.octavo_reader_bottom_chrome)
                .getImportantForAccessibility());
    }

    private static void assertLargeTextWraps(OctavoActivity activity) {
        Configuration configuration = new Configuration(
            activity.getResources().getConfiguration());
        configuration.fontScale = 2.0f;
        Context largeText =
            activity.createConfigurationContext(configuration);
        OctavoReadingPositionPrompt prompt =
            new OctavoReadingPositionPrompt(
                largeText,
                activity.appearanceForTesting(),
                "Location 123 of 456 in a deliberately long chapter name",
                new OctavoReadingPositionPrompt.Listener() {
                    @Override public void onGoThere() { }
                    @Override public void onStayHere() { }
                    @Override public void onRetry() { }
                });
        int width = Math.round(
            280 * activity.getResources().getDisplayMetrics().density);
        int height = Math.round(
            360 * activity.getResources().getDisplayMetrics().density);
        prompt.measure(
            View.MeasureSpec.makeMeasureSpec(
                width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(
                height, View.MeasureSpec.AT_MOST));
        prompt.layout(0, 0,
                      prompt.getMeasuredWidth(),
                      prompt.getMeasuredHeight());
        assertTrue(prompt.headingForTesting().getLineCount() > 1);
        assertTrue(prompt.getMeasuredHeight() <= height);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                     prompt.scrollForTesting().getLayoutParams().width);
    }

    private static void assertValidPosition(long[] position) {
        assertNotNull(position);
        assertEquals(3, position.length);
        assertEquals(1, position[0]);
        assertTrue(position[1] >= 0);
        assertTrue(position[2] >= 0);
    }

    private static boolean sameAnchor(long[] first, long[] second) {
        return first != null && second != null
            && first.length == 3 && second.length == 3
            && first[1] == second[1] && first[2] == second[2];
    }

    private static int compareAnchor(long firstSpine,
                                     long firstByte,
                                     long secondSpine,
                                     long secondByte) {
        int spine = Long.compare(firstSpine, secondSpine);
        return spine != 0 ? spine : Long.compare(firstByte, secondByte);
    }

}
