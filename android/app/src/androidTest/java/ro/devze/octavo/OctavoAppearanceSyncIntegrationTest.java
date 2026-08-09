package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoAppearanceSyncIntegrationTest {
    private static final long WAIT_MILLIS = 15_000;
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
    private static final String REMOTE_POSITION =
        "99999999999999999999999999999999";

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    private static final class RestoredCreationProbe
        implements Application.ActivityLifecycleCallbacks {
        boolean armed;
        boolean observed;
        String failure;

        @Override
        public void onActivityCreated(Activity raw, Bundle state) {}

        @Override
        public void onActivityPostCreated(Activity raw, Bundle state) {
            if (!armed || !(raw instanceof OctavoActivity)) {
                return;
            }
            armed = false;
            observed = true;
            OctavoActivity activity = (OctavoActivity)raw;
            if (state == null) {
                failure = "The recreated reader had no saved state";
                return;
            }
            if (activity.activeBookKeyForTesting() == null) {
                failure = "The recreated reader was not restored";
                return;
            }
            OctavoSurfaceView view = (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface);
            if (view == null) {
                failure = "The restored reader surface was absent";
                return;
            }
            if (view.currentAppearancePresentationReceipt() != null) {
                failure = "The creation probe ran after a settled receipt";
                return;
            }
            if (activity.appearanceSyncStoreForTesting().loadStatus()
                != OctavoAppearanceSyncStore.LoadStatus
                    .CORRUPT_QUARANTINED) {
                failure = "The abnormal O1SS load was not retained";
                return;
            }
            if (activity.appearanceSyncPromptForTesting() != null) {
                failure = "O1SS installed a prompt before presentation";
            }
        }

        void assertObservedWithoutFailure() {
            assertTrue("The recreated Activity creation was not observed",
                       observed);
            assertNull(failure, failure);
        }

        @Override
        public void onActivityStarted(Activity activity) {}

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity,
                                                Bundle state) {}

        @Override
        public void onActivityDestroyed(Activity activity) {}
    }

    @Before
    public void clearDurableState() {
        clearAllDurableTestState();
    }

    @After
    public void clearDurableStateAfterward() {
        clearAllDurableTestState();
    }

    @Test
    public void remoteWaitsForSettledReceiptAndPromptOwnsTheModalSurface() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoAppearance origin =
            OctavoAppearance.defaults().withReducedMotion(true);
        assertTrue(new OctavoAppearanceStore(context).save(origin));
        OctavoAppearance remote = origin.withTheme(
            OctavoAppearance.THEME_SEPIA);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(1));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_A, 1, remote));
                assertNull(activity.appearanceSyncPromptForTesting());
                assertNull(activity.appearanceSyncStoreForTesting()
                    .localLane());
            });

            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncPrompt prompt =
                    activity.appearanceSyncPromptForTesting();
                assertChoiceModal(activity, prompt, origin, remote);
                assertEquals(0,
                    activity.appearanceSyncPromptMotionDurationForTesting());
                assertEquals(1,
                    activity.appearanceSyncStoreForTesting().reviewEpoch());
                prompt.keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void beginReviewPublishFailureRetryClosesModalAndReviewsCandidate() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> remote =
                new AtomicReference<>();
            AtomicReference<Long> epochBefore = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoAppearance origin =
                    presentedReceipt(activity).profile;
                remote.set(origin.withTheme(
                    nextTheme(origin.themeId(), 1)));
                epochBefore.set(activity.appearanceSyncStoreForTesting()
                    .reviewEpoch());
                assertTrue(epochBefore.get() > 0);
                activity.closeBookForTesting();
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_A, 35, remote.get()));
                activity.appearanceSyncStoreForTesting()
                    .failNextPublishForTesting();
                assertTrue(activity.openFixtureForTesting());
            });
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);

            AtomicReference<OctavoAppearanceSyncPrompt> failurePrompt =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                failurePrompt.set(
                    activity.appearanceSyncPromptForTesting());
                assertNotNull(failurePrompt.get());
                assertFalse(activity
                    .appearanceSyncReviewInitializedForTesting());
                assertTrue(activity
                    .appearanceSyncReviewPendingForTesting());
                assertEquals(epochBefore.get().longValue(),
                    activity.appearanceSyncStoreForTesting()
                        .reviewEpoch());
                failurePrompt.get().retryForTesting().performClick();
            });

            OctavoAppearanceSyncPrompt choice =
                awaitChoicePrompt(scenario);
            assertNotSame(failurePrompt.get(), choice);
            scenario.onActivity(activity -> {
                assertTrue(activity
                    .appearanceSyncReviewInitializedForTesting());
                assertFalse(activity
                    .appearanceSyncReviewPendingForTesting());
                assertEquals(epochBefore.get().longValue() + 1,
                    activity.appearanceSyncStoreForTesting()
                        .reviewEpoch());
                OctavoAppearanceSyncStore.Candidate candidate =
                    activity.pendingAppearanceSyncCandidateForTesting();
                assertNotNull(candidate);
                assertEquals(REMOTE_A, candidate.deviceId);
                assertEquals(35, candidate.sequence);
                assertEquals(remote.get(), candidate.targetAppearance());
                assertSame(choice,
                           activity.appearanceSyncPromptForTesting());
                choice.keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void equalRemoteConvergesBeforeALaterLocalChange() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> localTarget =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                localTarget.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 1)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_B, 3, origin.get()));
                assertNull(activity.appearanceSyncPromptForTesting());
                activity.requestAppearanceForTesting(localTarget.get());
            });

            awaitLocalProfile(scenario, localTarget.get());
            assertPromptRemainsAbsent(scenario, 450);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore store =
                    activity.appearanceSyncStoreForTesting();
                assertTrue(store.reviewCandidates(
                    localTarget.get()).isEmpty());
                assertEquals(localTarget.get(),
                    presentedReceipt(activity).profile);
            });
        }
    }

    @Test
    public void usePublishesExactProfileAndRetainsReaderAnchorAcrossReflow() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoSurfaceView.AppearancePresentationReceipt>
                originReceipt = new AtomicReference<>();
            AtomicReference<OctavoAppearance> target =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                originReceipt.set(presentedReceipt(activity));
                target.set(nextLayoutAppearance(
                    originReceipt.get().profile));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_C, 7, target.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity ->
                activity.appearanceSyncPromptForTesting()
                    .useSettingsForTesting().performClick());

            awaitAppliedProfile(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoSurfaceView.AppearancePresentationReceipt accepted =
                    presentedReceipt(activity);
                assertEquals(originReceipt.get().anchorSpineIndex,
                             accepted.anchorSpineIndex);
                assertEquals(originReceipt.get().anchorByteOffset,
                             accepted.anchorByteOffset);
                assertEquals(accepted.anchorSpineIndex,
                             accepted.pageSpineIndex);
                assertTrue(accepted.anchorByteOffset
                           >= accepted.pageFirstByte);
                assertTrue(accepted.anchorByteOffset
                           < accepted.pageOnePastLastByte);
                assertTrue(activity.appearanceStoreForTesting()
                    .hasCanonicalCurrentRecord(target.get()));
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity -> {
                assertEquals(target.get(),
                             presentedReceipt(activity).profile);
                assertEquals(target.get(),
                    activity.appearanceSyncStoreForTesting()
                        .effectiveAppearance());
            });
        }
    }

    @Test
    public void keepMineDoesNotMoveAndSurvivesRecreationAndReplay() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> remote =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                remote.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 2)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_D, 11, remote.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity ->
                activity.appearanceSyncPromptForTesting()
                    .keepMineForTesting().performClick());
            awaitNoPrompt(scenario);

            scenario.onActivity(activity -> {
                assertEquals(origin.get(),
                             presentedReceipt(activity).profile);
                assertNull(activity.pendingAppearanceTransactionForTesting());
                assertTrue(activity.appearanceSyncStoreForTesting()
                    .reviewCandidates(origin.get()).isEmpty());
            });
            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity -> assertTrue(
                activity.simulateRemoteAppearanceForTesting(
                    REMOTE_D, 11, remote.get())));
            assertPromptRemainsAbsent(scenario, 350);
        }
    }

    @Test
    public void backIsLaterForOneEpochAndExplicitReopenReprompts() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> remote =
                new AtomicReference<>();
            AtomicReference<Long> firstEpoch = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                remote.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 3)));
                firstEpoch.set(activity.appearanceSyncStoreForTesting()
                    .reviewEpoch());
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_E, 13, remote.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(OctavoActivity::onBackPressed);
            awaitNoPrompt(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity).hasFocus(),
                "Later did not restore reader focus");

            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity -> assertEquals(
                firstEpoch.get().longValue(),
                activity.appearanceSyncStoreForTesting().reviewEpoch()));

            explicitReopenAndAwaitReader(scenario);
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                assertEquals(firstEpoch.get().longValue() + 1,
                    activity.appearanceSyncStoreForTesting()
                        .reviewEpoch());
                assertEquals(remote.get(),
                    activity.pendingAppearanceSyncCandidateForTesting()
                        .targetAppearance());
                activity.appearanceSyncPromptForTesting()
                    .keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void localChangeStalesOldPromptAndReevaluatesAgainstNewOrigin() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> remote =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> localTarget =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                remote.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 1)));
                localTarget.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 2)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_A, 17, remote.get()));
            });
            OctavoAppearanceSyncPrompt oldPrompt =
                awaitChoicePrompt(scenario);
            AtomicReference<OctavoAppearanceSyncStore.Candidate>
                oldCandidate = new AtomicReference<>();
            scenario.onActivity(activity -> {
                oldCandidate.set(
                    activity.pendingAppearanceSyncCandidateForTesting());
                assertNotNull(oldCandidate.get());
                activity.requestAppearanceForTesting(localTarget.get());
            });

            OctavoAppearanceSyncPrompt newPrompt =
                awaitChoicePromptWithOrigin(scenario, localTarget.get());
            assertNotSame(oldPrompt, newPrompt);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore.Candidate current =
                    activity.pendingAppearanceSyncCandidateForTesting();
                assertNotNull(current);
                assertFalse(oldCandidate.get().sameIdentity(current));
                assertEquals(REMOTE_A, current.deviceId);
                assertEquals(17, current.sequence);
                assertEquals(localTarget.get(), current.originAppearance());
                assertEquals(remote.get(), current.targetAppearance());

                oldPrompt.useSettingsForTesting().performClick();
                assertNull(activity.pendingAppearanceTransactionForTesting());
                assertSame(newPrompt,
                           activity.appearanceSyncPromptForTesting());
                newPrompt.keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void remoteSupersessionRejectsDetachedPromptCallbacks() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> first =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> second =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoAppearance origin =
                    presentedReceipt(activity).profile;
                first.set(origin.withTheme(
                    nextTheme(origin.themeId(), 1)));
                second.set(origin.withTheme(
                    nextTheme(origin.themeId(), 2)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_B, 21, first.get()));
            });
            OctavoAppearanceSyncPrompt oldPrompt =
                awaitChoicePrompt(scenario);

            scenario.onActivity(activity -> assertTrue(
                activity.simulateRemoteAppearanceForTesting(
                    REMOTE_B, 22, second.get())));
            OctavoAppearanceSyncPrompt newPrompt =
                awaitChoicePrompt(scenario);
            assertNotSame(oldPrompt, newPrompt);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore.Candidate current =
                    activity.pendingAppearanceSyncCandidateForTesting();
                assertNotNull(current);
                assertEquals(22, current.sequence);
                assertEquals(second.get(), current.targetAppearance());

                oldPrompt.useSettingsForTesting().performClick();
                oldPrompt.keepMineForTesting().performClick();
                assertNull(activity.pendingAppearanceTransactionForTesting());
                assertSame(newPrompt,
                           activity.appearanceSyncPromptForTesting());
                assertEquals(22,
                    activity.pendingAppearanceSyncCandidateForTesting()
                        .sequence);
                newPrompt.keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void publishFailureRequiresExplicitRetryAndIgnoresDuplicateReceipt()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> target =
                new AtomicReference<>();
            AtomicReference<byte[]> beforeFailure =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                target.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 4)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_C, 25, target.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore store =
                    activity.appearanceSyncStoreForTesting();
                beforeFailure.set(canonicalBytes(store));
                store.failNextPublishForTesting();
                activity.appearanceSyncPromptForTesting()
                    .useSettingsForTesting().performClick();
            });
            awaitRetryPrompt(scenario, false);

            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore store =
                    activity.appearanceSyncStoreForTesting();
                assertNull(store.pending());
                assertArrayEquals(beforeFailure.get(),
                                  canonicalBytes(store));
                assertEquals(origin.get(),
                             presentedReceipt(activity).profile);
                activity.processAppearancePresentationReceiptForTesting();
                activity.processAppearancePresentationReceiptForTesting();
                assertArrayEquals(beforeFailure.get(),
                                  canonicalBytes(store));
                assertNull(store.pending());
                assertTrue(activity
                    .appearanceSyncAwaitingExplicitRetryForTesting());
            });

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity ->
                activity.appearanceSyncPromptForTesting()
                    .retryForTesting().performClick());
            awaitAppliedProfile(scenario, target.get());
        }
    }

    @Test
    public void uncertainStageRetryClearsAbandonStateBeforeLaterChoices() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> target =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoAppearance origin = presentedReceipt(activity).profile;
                target.set(origin.withTheme(
                    nextTheme(origin.themeId(), 5)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_D, 28, target.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                activity.appearanceSyncStoreForTesting()
                    .failNextMoveAfterReplaceForTesting();
                activity.appearanceSyncPromptForTesting()
                    .useSettingsForTesting().performClick();
            });
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                assertTrue(activity
                    .appearanceSyncStageUncertainForTesting());
                activity.appearanceSyncPromptForTesting()
                    .retryForTesting().performClick();
            });

            // Reload has now proven that the staged pending bytes won. The
            // pending saga still needs its own explicit forward Retry, but it
            // must no longer poison Back handling for a later candidate.
            awaitRetryPrompt(scenario, true);
            scenario.onActivity(activity -> {
                assertFalse(activity
                    .appearanceSyncStageUncertainForTesting());
                assertFalse(activity
                    .appearanceSyncAbandonAfterReloadForTesting());
                activity.appearanceSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitAppliedProfile(scenario, target.get());
            scenario.onActivity(activity -> {
                assertFalse(activity
                    .appearanceSyncStageUncertainForTesting());
                assertFalse(activity
                    .appearanceSyncAbandonAfterReloadForTesting());
            });
        }
    }

    @Test
    public void loadedPendingBackRollsBackAndConsumesDeferredOpenEpoch() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> target =
                new AtomicReference<>();
            AtomicReference<Long> originalEpoch = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                target.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 5)));
                originalEpoch.set(activity.appearanceSyncStoreForTesting()
                    .reviewEpoch());
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_D, 29, target.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore.Candidate candidate =
                    activity.pendingAppearanceSyncCandidateForTesting();
                assertNotNull(candidate);
                assertSame(
                    OctavoAppearanceSyncStore.MutationResult.UPDATED,
                    activity.appearanceSyncStoreForTesting()
                        .stageRemoteApply(
                            candidate,
                            presentedReceipt(activity).profile));
                OctavoAppearanceSyncStore.Pending pending =
                    activity.pendingAppearanceTransactionForTesting();
                assertNotNull(pending);
                assertEquals(target.get(), pending.targetAppearance());
            });

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            explicitReopen(scenario);
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            scenario.onActivity(activity -> {
                assertTrue(activity
                    .appearanceSyncReviewPendingForTesting());
                assertFalse(activity
                    .appearanceSyncReviewInitializedForTesting());
                assertEquals(originalEpoch.get().longValue(),
                    activity.appearanceSyncStoreForTesting().reviewEpoch());
                activity.onBackPressed();
            });

            awaitNoPromptAndNoPending(scenario);
            scenario.onActivity(activity -> {
                assertEquals(originalEpoch.get().longValue() + 1,
                    activity.appearanceSyncStoreForTesting()
                        .reviewEpoch());
                assertFalse(activity
                    .appearanceSyncReviewPendingForTesting());
                assertTrue(activity
                    .appearanceSyncReviewInitializedForTesting());
                assertEquals(origin.get(),
                             presentedReceipt(activity).profile);
                assertTrue(activity.appearanceSyncStoreForTesting()
                    .reviewCandidates(origin.get()).isEmpty());
            });

            explicitReopenAndAwaitReader(scenario);
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                assertEquals(originalEpoch.get().longValue() + 2,
                    activity.appearanceSyncStoreForTesting()
                        .reviewEpoch());
                activity.appearanceSyncPromptForTesting()
                    .keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void abnormalRestoredLoadDefersRetryUntilFirstSettledReceipt() {
        Application application = (Application)
            ApplicationProvider.getApplicationContext();
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                corruptStateFile(
                    activity.appearanceSyncStoreForTesting());
            });

            RestoredCreationProbe probe = new RestoredCreationProbe();
            probe.armed = true;
            application.registerActivityLifecycleCallbacks(probe);
            try {
                scenario.recreate();
            } finally {
                application.unregisterActivityLifecycleCallbacks(probe);
            }
            probe.assertObservedWithoutFailure();

            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                assertSame(
                    OctavoAppearanceSyncStore.LoadStatus
                        .CORRUPT_QUARANTINED,
                    activity.appearanceSyncStoreForTesting()
                        .loadStatus());
                assertTrue(activity.appearanceSyncPromptForTesting()
                    .statusForTesting().getText().toString()
                    .toLowerCase().contains("quarantined"));
                activity.appearanceSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitAppliedProfile(scenario, origin.get());
        }
    }

    @Test
    public void appearanceRetryDrainsAfterReadingPositionPromptCloses() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> appearanceTarget =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoAppearance origin =
                    presentedReceipt(activity).profile;
                appearanceTarget.set(origin.withTheme(
                    nextTheme(origin.themeId(), 1)));
                long[] target = nextQualifiedPositionTarget(
                    surface(activity));
                assertTrue(activity.simulateRemotePositionForTesting(
                    REMOTE_POSITION, 1, target[0], target[1]));
            });
            awaitPositionChoicePrompt(scenario);

            scenario.onActivity(activity -> {
                OctavoReadingPositionPrompt positionPrompt =
                    activity.positionPromptForTesting();
                assertNotNull(positionPrompt);
                activity.appearanceSyncStoreForTesting()
                    .failNextPublishForTesting();
                assertFalse(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_A, 37, appearanceTarget.get()));
                assertSame(positionPrompt,
                           activity.positionPromptForTesting());
                assertNull(activity.appearanceSyncPromptForTesting());
                assertTrue(activity
                    .appearanceSyncAwaitingExplicitRetryForTesting());
                positionPrompt.stayHereForTesting().performClick();
            });

            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                assertNull(activity.positionPromptForTesting());
                activity.appearanceSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity ->
                activity.appearanceSyncPromptForTesting()
                    .keepMineForTesting().performClick());
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void committedUncertainRollbackRecreationConvergesEqualForeignLane() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoAppearance> origin =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> remoteTarget =
                new AtomicReference<>();
            AtomicReference<OctavoAppearance> laterLocal =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).profile);
                remoteTarget.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 1)));
                laterLocal.set(origin.get().withTheme(
                    nextTheme(origin.get().themeId(), 2)));
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_A, 41, remoteTarget.get()));
            });
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoAppearanceSyncStore.Candidate candidate =
                    activity.pendingAppearanceSyncCandidateForTesting();
                assertNotNull(candidate);
                assertSame(
                    OctavoAppearanceSyncStore.MutationResult.UPDATED,
                    activity.appearanceSyncStoreForTesting()
                        .stageRemoteApply(candidate, origin.get()));
                OctavoAppearanceSyncStore.Pending pending =
                    activity.pendingAppearanceTransactionForTesting();
                assertNotNull(pending);
                assertEquals(remoteTarget.get(), pending.targetAppearance());
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_B, 43, origin.get()));
                assertNotNull(
                    activity.pendingAppearanceTransactionForTesting());
            });

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            explicitReopen(scenario);
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            scenario.onActivity(activity -> {
                assertTrue(activity
                    .appearanceSyncReviewPendingForTesting());
                activity.appearanceSyncStoreForTesting()
                    .failNextMoveAfterReplaceForTesting();
                activity.onBackPressed();
            });
            awaitRetryPrompt(scenario, true);

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);
            AtomicReference<byte[]> beforeConvergence =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                assertNull(
                    activity.pendingAppearanceTransactionForTesting());
                assertTrue(activity
                    .appearanceSyncRollbackRequestedForTesting());
                beforeConvergence.set(canonicalBytes(
                    activity.appearanceSyncStoreForTesting()));
                activity.appearanceSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitNoPromptAndNoPending(scenario);
            scenario.onActivity(activity -> {
                assertFalse(Arrays.equals(
                    beforeConvergence.get(), canonicalBytes(
                        activity.appearanceSyncStoreForTesting())));
                assertEquals(origin.get(),
                             presentedReceipt(activity).profile);
                activity.requestAppearanceForTesting(laterLocal.get());
            });
            awaitLocalProfile(scenario, laterLocal.get());
            assertPromptRemainsAbsent(scenario, 450);
            scenario.onActivity(activity -> assertTrue(
                activity.appearanceSyncStoreForTesting()
                    .reviewCandidates(laterLocal.get()).isEmpty()));
        }
    }

    @Test
    public void ownedPanelDefersPromptUntilUserClosesIt() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            scenario.onActivity(activity -> {
                OctavoAppearance origin =
                    presentedReceipt(activity).profile;
                OctavoAppearance remote = origin.withTheme(
                    nextTheme(origin.themeId(), 1));
                activity.openAppearancePanelForTesting();
                assertNotNull(activity.appearancePanelForTesting());
                assertTrue(activity.simulateRemoteAppearanceForTesting(
                    REMOTE_E, 31, remote));
                assertNull(activity.appearanceSyncPromptForTesting());
            });
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity ->
                activity.closeAppearancePanelForTesting());
            awaitChoicePrompt(scenario);
            scenario.onActivity(activity ->
                activity.appearanceSyncPromptForTesting()
                    .keepMineForTesting().performClick());
            awaitNoPrompt(scenario);
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

    private static void explicitReopenAndAwaitReader(
        ActivityScenario<OctavoActivity> scenario) {
        explicitReopen(scenario);
        awaitReaderReady(scenario);
    }

    private static void explicitReopen(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.closeBookForTesting();
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

    private static OctavoSurfaceView.AppearancePresentationReceipt
        presentedReceipt(OctavoActivity activity) {
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            surface(activity).currentAppearancePresentationReceipt();
        assertNotNull(receipt);
        assertTrue(receipt.strictResumeSettled);
        return receipt;
    }

    private static OctavoAppearanceSyncPrompt awaitChoicePrompt(
        ActivityScenario<OctavoActivity> scenario) {
        return awaitChoicePromptWithOrigin(scenario, null);
    }

    private static OctavoAppearanceSyncPrompt awaitChoicePromptWithOrigin(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expectedOrigin) {
        AtomicReference<OctavoAppearanceSyncPrompt> result =
            new AtomicReference<>();
        awaitActivity(
            scenario,
            activity -> {
                OctavoAppearanceSyncPrompt prompt =
                    activity.appearanceSyncPromptForTesting();
                OctavoAppearanceSyncStore.Candidate candidate =
                    activity.pendingAppearanceSyncCandidateForTesting();
                if (prompt == null || candidate == null
                    || !prompt.isShown() || prompt.getWidth() <= 0
                    || !prompt.useSettingsForTesting().isEnabled()
                    || !prompt.keepMineForTesting().isEnabled()
                    || prompt.retryForTesting().getVisibility()
                       != View.GONE
                    || (expectedOrigin != null
                        && !expectedOrigin.equals(
                            candidate.originAppearance()))) {
                    return false;
                }
                result.set(prompt);
                return true;
            },
            "8vo did not show a settled reading-settings choice");
        return result.get();
    }

    private static void awaitRetryPrompt(
        ActivityScenario<OctavoActivity> scenario,
        boolean pendingRequired) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoAppearanceSyncPrompt prompt =
                    activity.appearanceSyncPromptForTesting();
                return prompt != null && prompt.isShown()
                    && activity
                        .appearanceSyncAwaitingExplicitRetryForTesting()
                    && (!pendingRequired
                        || activity.pendingAppearanceTransactionForTesting()
                           != null)
                    && prompt.retryForTesting().getVisibility()
                       == View.VISIBLE
                    && prompt.retryForTesting().isEnabled()
                    && !prompt.useSettingsForTesting().isEnabled()
                    && !prompt.keepMineForTesting().isEnabled()
                    && !prompt.statusForTesting().getText().toString()
                        .trim().isEmpty();
            },
            "The reading-settings failure did not expose explicit Retry");
    }

    private static void awaitPositionChoicePrompt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoReadingPositionPrompt prompt =
                    activity.positionPromptForTesting();
                return prompt != null && prompt.isShown()
                    && prompt.goThereForTesting().isEnabled()
                    && prompt.stayHereForTesting().isEnabled()
                    && prompt.retryForTesting().getVisibility()
                       == View.GONE;
            },
            "8vo did not show the reading-position choice");
    }

    private static void awaitAppliedProfile(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView.AppearancePresentationReceipt receipt =
                    surface(activity)
                        .currentAppearancePresentationReceipt();
                OctavoAppearancePortable.Lane local =
                    activity.appearanceSyncStoreForTesting().localLane();
                return receipt != null && receipt.strictResumeSettled
                    && expected.equals(receipt.profile)
                    && local != null
                    && expected.equals(local.profile.toAppearance())
                    && activity.pendingAppearanceTransactionForTesting()
                       == null
                    && activity.appearanceSyncPromptForTesting() == null
                    && !activity
                        .appearanceSyncAwaitingExplicitRetryForTesting()
                    && activity.appearanceStoreForTesting()
                        .hasCanonicalCurrentRecord(expected);
            },
            "The exact reading-settings profile was not durably applied");
    }

    private static void awaitLocalProfile(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView.AppearancePresentationReceipt receipt =
                    surface(activity)
                        .currentAppearancePresentationReceipt();
                OctavoAppearancePortable.Lane local =
                    activity.appearanceSyncStoreForTesting().localLane();
                return receipt != null && expected.equals(receipt.profile)
                    && local != null
                    && expected.equals(local.profile.toAppearance())
                    && activity.pendingAppearanceTransactionForTesting()
                       == null;
            },
            "The local reading-settings profile did not settle");
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
                OctavoSurfaceView.AppearancePresentationReceipt receipt =
                    view.currentAppearancePresentationReceipt();
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
                    && receipt != null && receipt.strictResumeSettled
                    && activity.appearanceSyncStoreForTesting()
                        .localLane() != null
                    && activity
                        .appearanceSyncReviewInitializedForTesting();
            },
            "8vo did not present a settled appearance-sync reader frame");
    }

    private static void awaitSettledReceipt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                if (view == null) {
                    return false;
                }
                OctavoSurfaceView.AppearancePresentationReceipt receipt =
                    view.currentAppearancePresentationReceipt();
                return receipt != null && receipt.strictResumeSettled;
            },
            "8vo did not expose a settled appearance receipt");
    }

    private static void awaitNoPrompt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> activity.appearanceSyncPromptForTesting() == null
                && !activity
                    .appearanceSyncAwaitingExplicitRetryForTesting(),
            "The reading-settings prompt did not close");
    }

    private static void awaitNoPromptAndNoPending(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> activity.appearanceSyncPromptForTesting() == null
                && activity.pendingAppearanceTransactionForTesting() == null
                && !activity
                    .appearanceSyncAwaitingExplicitRetryForTesting(),
            "The reading-settings rollback did not finish");
    }

    private static void assertPromptRemainsAbsent(
        ActivityScenario<OctavoActivity> scenario,
        long durationMillis) {
        long deadline = SystemClock.uptimeMillis() + durationMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> assertNull(
                activity.appearanceSyncPromptForTesting()));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(25);
        }
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

    private static void assertChoiceModal(
        OctavoActivity activity,
        OctavoAppearanceSyncPrompt prompt,
        OctavoAppearance expectedOrigin,
        OctavoAppearance expectedRemote) {
        assertNotNull(prompt);
        assertEquals(expectedOrigin,
                     prompt.presentedProfileForTesting());
        assertEquals(expectedRemote,
                     prompt.remoteProfileForTesting());
        assertTrue(prompt.headingForTesting().getText().toString()
            .startsWith("Another device uses different reading settings"));
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                     prompt.statusForTesting()
                         .getAccessibilityLiveRegion());
        if (Build.VERSION.SDK_INT >= 28) {
            assertTrue(prompt.headingForTesting()
                .isAccessibilityHeading());
            assertEquals("Reading settings confirmation",
                         prompt.getAccessibilityPaneTitle().toString());
        }
        int minimum = Math.round(
            48 * activity.getResources().getDisplayMetrics().density);
        assertTrue(prompt.useSettingsForTesting().getHeight() >= minimum);
        assertTrue(prompt.keepMineForTesting().getHeight() >= minimum);
        assertTrue(prompt.retryForTesting().getMinHeight() >= minimum);
        assertTrue(prompt.useSettingsForTesting().isFocusableInTouchMode());
        assertTrue(prompt.keepMineForTesting().isFocusableInTouchMode());
        assertTrue(prompt.retryForTesting().isFocusableInTouchMode());
        assertNotNull(prompt.scrollForTesting());
        assertTrue(prompt.useSettingsForTesting().hasFocus()
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

    private static OctavoAppearance nextLayoutAppearance(
        OctavoAppearance origin) {
        int[] sizes = OctavoAppearance.fontSizesSp();
        int index = 0;
        while (index < sizes.length
               && sizes[index] != origin.fontSizeSp()) {
            ++index;
        }
        int next = sizes[(index + 1) % sizes.length];
        return origin.withFontSizeSp(next);
    }

    private static long[] nextQualifiedPositionTarget(
        OctavoSurfaceView view) {
        long[] state = view.nativeStateForTesting();
        long[] position = view.readingPositionForTesting();
        assertNotNull(state);
        assertNotNull(position);
        assertEquals(3, position.length);
        assertEquals(1, position[0]);
        long spine = position[1];
        long onePast = state[
            OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE];
        long[][] probes = new long[][] {
            {spine, onePast},
            {spine + 1, 0},
            {spine + 2, 0}
        };
        for (long[] probe : probes) {
            long[] qualified = view.qualifySyncedReadingPosition(
                probe[0], probe[1]);
            if (qualified != null
                && qualified[0] == OctavoNative.NAVIGATION_ACCEPTED
                && (probe[0] != position[1]
                    || probe[1] != position[2])) {
                return probe;
            }
        }
        fail("The fixture had no exact alternate reading-position anchor");
        return null;
    }

    private static int nextTheme(int current, int delta) {
        return (current + delta) % OctavoAppearance.THEME_COUNT;
    }

    private static byte[] canonicalBytes(
        OctavoAppearanceSyncStore store) {
        try {
            return store.canonicalBytesForTesting();
        } catch (IOException exception) {
            throw new AssertionError(
                "O1SS canonical bytes were unavailable", exception);
        }
    }

    private static void corruptStateFile(
        OctavoAppearanceSyncStore store) {
        byte[] corrupt = canonicalBytes(store);
        assertTrue(corrupt.length > 0);
        corrupt[corrupt.length - 1] ^= 0x5a;
        File destination = store.stateFileForTesting();
        try (FileOutputStream output =
                 new FileOutputStream(destination, false)) {
            output.write(corrupt);
            output.flush();
            output.getFD().sync();
        } catch (IOException exception) {
            throw new AssertionError(
                "Unable to inject corrupt O1SS bytes", exception);
        }
    }

    private static void clearAllDurableTestState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        OctavoNoteDraftStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
    }
}
