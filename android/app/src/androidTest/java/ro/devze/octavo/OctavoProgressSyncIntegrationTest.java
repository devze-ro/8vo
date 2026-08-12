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

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoProgressSyncIntegrationTest {
    private static final long WAIT_MILLIS = 15_000;
    private static final String REMOTE_A =
        "11111111111111111111111111111111";
    private static final String REMOTE_B =
        "22222222222222222222222222222222";
    private static final String REMOTE_C =
        "33333333333333333333333333333333";
    private static final String REMOTE_D =
        "44444444444444444444444444444444";

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
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
    public void libraryAndFirstFrameGateEqualRemoteConvergence() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_A, 1, OctavoProgressDisplay.PERCENTAGE));
                assertNull(activity.progressSyncPromptForTesting());
                assertNull(activity.progressSyncStoreForTesting()
                    .localLane());
                assertTrue(activity.openFixtureForTesting());
                assertTrue(surface(activity)
                    .forcePresentFailuresForTesting(1));
                assertNull(activity.progressSyncPromptForTesting());
            });

            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.nativeStateForTesting()[
                    OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT] > 0);
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    presentedReceipt(activity);
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           receipt.choice);
                OctavoProgressPortable.Lane local =
                    activity.progressSyncStoreForTesting().localLane();
                assertNotNull(local);
                assertSame(OctavoProgressDisplay.PERCENTAGE,
                           local.choice.toDisplay());
                assertEquals(1,
                    activity.progressSyncStoreForTesting().reviewEpoch());
                assertTrue(activity.progressSyncStoreForTesting()
                    .reviewCandidates(receipt.choice).isEmpty());
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(receipt.choice));
            });
        }
    }

    @Test
    public void localPanelChoiceStagesBeforePresentationAndDoesNotMove()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoSurfaceView.ProgressPresentationReceipt>
                origin = new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<long[]> navigation = new AtomicReference<>();
            AtomicReference<Long> localSequence = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity));
                target.set(nextDisplay(origin.get().choice, 1));
                navigation.set(surface(activity)
                    .navigationStateForTesting().clone());
                localSequence.set(activity.progressSyncStoreForTesting()
                    .localLane().sequence);
                activity.openNavigationPanelForTesting();
                OctavoNavigationPanel panel =
                    activity.navigationPanelForTesting();
                assertNotNull(panel);
                RadioButton option = progressOption(
                    panel.progressOptionsForTesting(), target.get());
                assertNotNull(option);
                assertTrue(option.isEnabled());
                assertFalse(option.isChecked());
                assertTrue(surface(activity)
                    .forcePresentFailuresForTesting(1));
                assertTrue(option.performClick());

                OctavoProgressSyncStore.Pending pending =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(pending);
                assertSame(OctavoProgressSyncStore.PendingKind.LOCAL,
                           pending.kind);
                assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                           pending.direction);
                assertSame(target.get(), pending.targetDisplay());
                assertEquals(localSequence.get().longValue(),
                    activity.progressSyncStoreForTesting()
                        .localLane().sequence);
                assertSame(origin.get().choice,
                    activity.progressSyncStoreForTesting()
                        .effectiveDisplay());
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(origin.get().choice));
                assertNull(activity.navigationPanelForTesting());
            });

            awaitAppliedProgress(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoSurfaceView.ProgressPresentationReceipt applied =
                    presentedReceipt(activity);
                assertSameAnchor(origin.get(), applied);
                assertEquals(localSequence.get().longValue() + 1,
                    activity.progressSyncStoreForTesting()
                        .localLane().sequence);
                long[] after = surface(activity)
                    .navigationStateForTesting();
                assertEquals(navigation.get()[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_BACK_COUNT],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_BACK_COUNT]);
                assertEquals(navigation.get()[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_FORWARD_COUNT],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);
            });
        }
    }

    @Test
    public void useOtherDisplayPublishesWithoutMovingAndSurvivesRecreation() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoSurfaceView.ProgressPresentationReceipt>
                origin = new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<long[]> navigation = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity));
                target.set(nextDisplay(origin.get().choice, 2));
                navigation.set(surface(activity)
                    .navigationStateForTesting().clone());
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_B, 7, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                assertChoiceModal(activity,
                    activity.progressSyncPromptForTesting(),
                    origin.get().choice, target.get());
                activity.progressSyncPromptForTesting()
                    .useDisplayForTesting().performClick();
            });

            awaitAppliedProgress(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoSurfaceView.ProgressPresentationReceipt applied =
                    presentedReceipt(activity);
                assertSameAnchor(origin.get(), applied);
                long[] after = surface(activity)
                    .navigationStateForTesting();
                assertEquals(navigation.get()[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_BACK_COUNT],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_BACK_COUNT]);
                assertEquals(navigation.get()[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_FORWARD_COUNT],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);
                assertTrue(activity.progressSyncStoreForTesting()
                    .reviewCandidates(target.get()).isEmpty());
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity -> {
                assertSame(target.get(), presentedReceipt(activity).choice);
                assertSame(target.get(),
                    activity.progressSyncStoreForTesting()
                        .effectiveDisplay());
            });
        }
    }

    @Test
    public void keepMineIsolatesLegacyProgressAndSurvivesExactReplay()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoSurfaceView.ProgressPresentationReceipt>
                origin = new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<byte[]> o8pg = new AtomicReference<>();
            AtomicReference<byte[]> o1ps = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity));
                target.set(nextDisplay(origin.get().choice, 3));
                o8pg.set(readFileUnchecked(activity.progressStoreForTesting()
                    .progressFileForTesting()));
                o1ps.set(canonicalBytes(
                    activity.progressSyncStoreForTesting()));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_C, 11, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity ->
                activity.progressSyncPromptForTesting()
                    .keepMineForTesting().performClick());
            awaitNoPrompt(scenario);

            scenario.onActivity(activity -> {
                OctavoSurfaceView.ProgressPresentationReceipt presented =
                    presentedReceipt(activity);
                assertSame(origin.get().choice, presented.choice);
                assertSameAnchor(origin.get(), presented);
                assertArrayEquals(o8pg.get(), readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                assertFalse(java.util.Arrays.equals(
                    o1ps.get(), canonicalBytes(
                        activity.progressSyncStoreForTesting())));
                assertNull(activity.pendingProgressTransactionForTesting());
                assertTrue(activity.progressSyncStoreForTesting()
                    .reviewCandidates(origin.get().choice).isEmpty());
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                activity.simulateRemoteProgressForTesting(
                    REMOTE_C, 11, target.get())));
            assertPromptRemainsAbsent(scenario, 350);
        }
    }

    @Test
    public void backIsLaterForOneEpochAndExplicitReopenReprompts() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<Long> epoch = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    presentedReceipt(activity);
                target.set(nextDisplay(receipt.choice, 1));
                epoch.set(activity.progressSyncStoreForTesting()
                    .reviewEpoch());
                assertTrue(surface(activity).requestFocus());
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_D, 13, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
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
                epoch.get().longValue(),
                activity.progressSyncStoreForTesting().reviewEpoch()));

            explicitReopen(scenario);
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                assertEquals(epoch.get().longValue() + 1,
                    activity.progressSyncStoreForTesting().reviewEpoch());
                activity.progressSyncPromptForTesting()
                    .keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void uncertainExplicitReopenEpochRequiresRetryAfterRecreation()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> origin =
                new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<Long> epochBefore = new AtomicReference<>();
            AtomicReference<byte[]> stateBefore = new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).choice);
                target.set(nextDisplay(origin.get(), 1));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_A, 17, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(OctavoActivity::onBackPressed);
            awaitNoPrompt(scenario);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                epochBefore.set(store.reviewEpoch());
                assertTrue(store.reviewCandidates(origin.get()).isEmpty());
                stateBefore.set(readFileUnchecked(
                    store.stateFileForTesting()));
                store.failNextMoveAfterReplaceForTesting();
            });
            explicitReopen(scenario);
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore blocked =
                    activity.progressSyncStoreForTesting();
                assertSame(
                    OctavoProgressSyncStore.LoadStatus
                        .PUBLISH_UNCERTAIN_BLOCKED,
                    blocked.loadStatus());
                assertEquals(epochBefore.get().longValue(),
                             blocked.reviewEpoch());
                assertTrue(activity.progressSyncReviewPendingForTesting());
                assertFalse(activity
                    .progressSyncReviewInitializedForTesting());
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertNull(activity.pendingProgressTransactionForTesting());
                byte[] committed = readFileUnchecked(
                    blocked.stateFileForTesting());
                assertFalse(java.util.Arrays.equals(
                    stateBefore.get(), committed));

                OctavoProgressSyncStore committedProbe =
                    new OctavoProgressSyncStore(activity);
                assertSame(OctavoProgressSyncStore.LoadStatus.LOADED,
                           committedProbe.load());
                assertEquals(epochBefore.get().longValue() + 1,
                             committedProbe.reviewEpoch());
                assertEquals(1, committedProbe
                    .reviewCandidates(origin.get()).size());
                OctavoProgressSyncStore.Candidate candidate =
                    committedProbe.reviewCandidates(origin.get()).get(0);
                assertEquals(17, candidate.sequence);
                assertSame(target.get(), candidate.targetDisplay());
            });

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                assertSame(OctavoProgressSyncStore.LoadStatus.LOADED,
                           store.loadStatus());
                assertEquals(epochBefore.get().longValue() + 1,
                             store.reviewEpoch());
                assertTrue(activity.progressSyncReviewPendingForTesting());
                assertFalse(activity
                    .progressSyncReviewInitializedForTesting());
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertEquals(1,
                    store.reviewCandidates(origin.get()).size());
                activity.processProgressPresentationReceiptForTesting();
                activity.processProgressPresentationReceiptForTesting();
                assertEquals(epochBefore.get().longValue() + 1,
                             store.reviewEpoch());
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertTrue(activity
                    .progressSyncAwaitingExplicitRetryForTesting());
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });

            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Candidate candidate =
                    activity.pendingProgressSyncCandidateForTesting();
                assertNotNull(candidate);
                assertEquals(17, candidate.sequence);
                assertEquals(epochBefore.get().longValue() + 1,
                             candidate.reviewEpoch);
                assertEquals(epochBefore.get().longValue() + 1,
                    activity.progressSyncStoreForTesting().reviewEpoch());
                assertFalse(activity.progressSyncReviewPendingForTesting());
                assertTrue(activity
                    .progressSyncReviewInitializedForTesting());
                activity.progressSyncPromptForTesting()
                    .keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void presentationFailureNeedsExplicitRetryAcrossRecreation()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> origin =
                new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).choice);
                target.set(nextDisplay(origin.get(), 2));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_A, 19, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                assertTrue(surface(activity)
                    .forcePrePresentFailuresForTesting(5));
                activity.progressSyncPromptForTesting()
                    .useDisplayForTesting().performClick();
            });
            awaitRetryPrompt(scenario, true);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Pending pending =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(pending);
                assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                           pending.direction);
                byte[] before = canonicalBytes(
                    activity.progressSyncStoreForTesting());
                activity.processProgressPresentationReceiptForTesting();
                activity.processProgressPresentationReceiptForTesting();
                assertArrayEquals(before, canonicalBytes(
                    activity.progressSyncStoreForTesting()));
                assertTrue(activity
                    .progressSyncAwaitingExplicitRetryForTesting());
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(origin.get()));
            });

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            scenario.onActivity(activity ->
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick());
            awaitAppliedProgress(scenario, target.get());
        }
    }

    @Test
    public void uncertainStageReloadsExactForwardPendingBeforeApply() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoProgressDisplay origin =
                    presentedReceipt(activity).choice;
                target.set(nextDisplay(origin, 1));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_B, 23, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                activity.progressSyncStoreForTesting()
                    .failNextMoveAfterReplaceForTesting();
                activity.progressSyncPromptForTesting()
                    .useDisplayForTesting().performClick();
            });
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                assertSame(
                    OctavoProgressSyncStore.LoadStatus
                        .PUBLISH_UNCERTAIN_BLOCKED,
                    activity.progressSyncStoreForTesting().loadStatus());
                assertNull(activity.pendingProgressTransactionForTesting());
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });

            awaitRetryPrompt(scenario, true);
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Pending pending =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(pending);
                assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                           pending.direction);
                assertSame(target.get(), pending.targetDisplay());
                assertFalse(activity
                    .progressSyncRollbackRequestedForTesting());
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitAppliedProgress(scenario, target.get());
        }
    }

    @Test
    public void rollbackNeedsNewO8pgProofAfterDefiniteSaveFailure()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> origin =
                new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<byte[]> rollbackBytes =
                new AtomicReference<>();
            AtomicReference<Long> saveFailuresBefore =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).choice);
                target.set(nextDisplay(origin.get(), 1));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_C, 27, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                OctavoProgressSyncStore.Candidate candidate =
                    activity.pendingProgressSyncCandidateForTesting();
                assertNotNull(candidate);
                assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
                           store.stageRemoteApply(candidate, origin.get()));
                OctavoProgressSyncStore.Pending forward =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(forward);
                assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                           forward.direction);
                assertSame(origin.get(), presentedReceipt(activity).choice);
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(origin.get()));
                assertArrayEquals(canonicalBytes(store),
                    readFileUnchecked(store.stateFileForTesting()));
                File temporary = activity.progressStoreForTesting()
                    .temporaryFileForTesting();
                assertFalse(temporary.exists());
                assertTrue(temporary.mkdir());
                saveFailuresBefore.set(activity.progressStoreForTesting()
                    .saveFailureCountForTesting());
                activity.onBackPressed();
            });
            awaitRetryPrompt(scenario, true);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Pending rollback =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(rollback);
                assertSame(
                    OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                    rollback.direction);
                assertTrue(activity
                    .progressSyncRollbackRequestedForTesting());
                assertSame(origin.get(), presentedReceipt(activity).choice);
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(origin.get()));
                assertEquals(saveFailuresBefore.get().longValue() + 1,
                    activity.progressStoreForTesting()
                        .saveFailureCountForTesting());
                rollbackBytes.set(canonicalBytes(
                    activity.progressSyncStoreForTesting()));
                assertArrayEquals(rollbackBytes.get(), readFileUnchecked(
                    activity.progressSyncStoreForTesting()
                        .stateFileForTesting()));
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitRetryPrompt(scenario, true);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Pending rollback =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(rollback);
                assertSame(
                    OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                    rollback.direction);
                assertArrayEquals(rollbackBytes.get(), canonicalBytes(
                    activity.progressSyncStoreForTesting()));
                assertArrayEquals(rollbackBytes.get(), readFileUnchecked(
                    activity.progressSyncStoreForTesting()
                        .stateFileForTesting()));
                assertEquals(saveFailuresBefore.get().longValue() + 2,
                    activity.progressStoreForTesting()
                        .saveFailureCountForTesting());
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(origin.get()));
                File temporary = activity.progressStoreForTesting()
                    .temporaryFileForTesting();
                assertTrue(temporary.isDirectory());
                assertTrue(temporary.delete());
            });
        }
    }

    @Test
    public void rollbackDirectionSurvivesRecreationAndRestoresOrigin() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> origin =
                new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).choice);
                target.set(nextDisplay(origin.get(), 2));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_C, 29, target.get()));
            });
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                OctavoProgressSyncStore.Candidate candidate =
                    activity.pendingProgressSyncCandidateForTesting();
                assertNotNull(candidate);
                assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
                    store.stageRemoteApply(candidate, origin.get()));
                OctavoProgressSyncStore.Pending pending = store.pending();
                assertNotNull(pending);
                assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                           pending.direction);
                assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
                           store.requestRollback(pending));
                assertSame(OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                           store.pending().direction);
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(origin.get()));
            });

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Pending pending =
                    activity.pendingProgressTransactionForTesting();
                assertNotNull(pending);
                assertSame(OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                           pending.direction);
                assertTrue(activity
                    .progressSyncRollbackRequestedForTesting());
                assertSame(origin.get(), presentedReceipt(activity).choice);
                assertSame(OctavoProgressSyncStore.PendingRecovery
                               .ORIGIN_DURABLE,
                    activity.progressSyncStoreForTesting()
                        .pendingRecovery(origin.get()));
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitNoPromptAndNoPending(scenario);
            scenario.onActivity(activity -> {
                assertSame(origin.get(), presentedReceipt(activity).choice);
                assertFalse(activity
                    .progressSyncRollbackRequestedForTesting());
                assertTrue(activity.progressSyncStoreForTesting()
                    .reviewCandidates(origin.get()).isEmpty());
            });
        }
    }

    @Test
    public void remoteSupersessionRejectsDetachedPromptCallbacks() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> firstTarget =
                new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> secondTarget =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoProgressDisplay origin =
                    presentedReceipt(activity).choice;
                firstTarget.set(nextDisplay(origin, 1));
                secondTarget.set(nextDisplay(origin, 2));
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_D, 31, firstTarget.get()));
            });
            OctavoProgressSyncPrompt oldPrompt =
                awaitChoicePrompt(scenario, firstTarget.get());
            scenario.onActivity(activity -> assertTrue(
                activity.simulateRemoteProgressForTesting(
                    REMOTE_D, 32, secondTarget.get())));
            OctavoProgressSyncPrompt replacement =
                awaitChoicePrompt(scenario, secondTarget.get());
            assertNotSame(oldPrompt, replacement);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore.Candidate current =
                    activity.pendingProgressSyncCandidateForTesting();
                assertNotNull(current);
                assertEquals(32, current.sequence);
                assertSame(secondTarget.get(), current.targetDisplay());
                oldPrompt.useDisplayForTesting().performClick();
                oldPrompt.keepMineForTesting().performClick();
                assertNull(activity.pendingProgressTransactionForTesting());
                assertSame(replacement,
                           activity.progressSyncPromptForTesting());
                assertEquals(32,
                    activity.pendingProgressSyncCandidateForTesting()
                        .sequence);
                replacement.keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
        }
    }

    @Test
    public void futureO8pgBytesRemainExactAndBlockSynchronization()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoProgressStore disk = new OctavoProgressStore(context);
        byte[] future = new byte[8];
        writeInt(future, 0, 0x4F385047);
        writeInt(future, 4, 0x80000000);
        writeFile(disk.progressFileForTesting(), future);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertSame(OctavoProgressStore.LoadStatus.FUTURE,
                           activity.progressStoreForTesting().loadStatus());
                assertArrayEquals(future, readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                assertTrue(activity.openFixtureForTesting());
            });
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                byte[] o1psBefore = canonicalBytes(store);
                assertArrayEquals(o1psBefore, readFileUnchecked(
                    store.stateFileForTesting()));
                int laneCountBefore = portableLaneCount(store);
                int candidateCountBefore = store
                    .reviewCandidates(OctavoProgressDisplay.PERCENTAGE)
                    .size();
                assertTrue(activity
                    .progressSyncO8pgFutureBlockedForTesting());
                assertNull(store.localLane());
                assertArrayEquals(future, readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                assertTrue(activity.progressSyncPromptForTesting()
                    .statusForTesting().getText().toString()
                    .toLowerCase(Locale.US).contains("newer"));
                assertFalse(activity.simulateRemoteProgressForTesting(
                    REMOTE_D, 41, OctavoProgressDisplay.LOCATION));
                assertTrue(activity
                    .progressSyncO8pgFutureBlockedForTesting());
                assertArrayEquals(o1psBefore, canonicalBytes(store));
                assertArrayEquals(o1psBefore, readFileUnchecked(
                    store.stateFileForTesting()));
                assertEquals(laneCountBefore, portableLaneCount(store));
                assertEquals(candidateCountBefore, store
                    .reviewCandidates(OctavoProgressDisplay.PERCENTAGE)
                    .size());
                assertNull(store.localLane());
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertNull(activity.pendingProgressTransactionForTesting());
                assertArrayEquals(future, readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> assertArrayEquals(
                future, readFileUnchecked(activity.progressStoreForTesting()
                    .progressFileForTesting())));
        }
    }

    @Test
    public void futureO8pgRestoredPendingRetryNeverRequestsOrPresents()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoProgressDisplay origin = OctavoProgressDisplay.PERCENTAGE;
        OctavoProgressDisplay target = OctavoProgressDisplay.LOCATION;
        OctavoProgressStore progress = new OctavoProgressStore(context);
        assertSame(origin, progress.load());
        assertSame(OctavoProgressStore.LoadStatus.MISSING,
                   progress.loadStatus());
        assertTrue(progress.save(origin));

        OctavoProgressSyncStore seeded =
            new OctavoProgressSyncStore(context);
        assertSame(OctavoProgressSyncStore.LoadStatus.MISSING_CREATED,
                   seeded.load());
        assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
                   seeded.stageInitialPresented(origin));
        OctavoProgressSyncStore.Pending initial = seeded.pending();
        assertNotNull(initial);
        assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
            seeded.completePending(
                initial, origin, origin,
                OctavoProgressSyncStore.O8pgProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
                   seeded.stageLocalApply(origin, target));
        OctavoProgressSyncStore.Pending expectedPending = seeded.pending();
        assertNotNull(expectedPending);
        assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                   expectedPending.direction);
        byte[] exactO1ps = canonicalBytes(seeded);
        assertArrayEquals(exactO1ps, readFile(
            seeded.stateFileForTesting()));

        byte[] futureO8pg = new byte[8];
        writeInt(futureO8pg, 0, 0x4F385047);
        writeInt(futureO8pg, Integer.BYTES, 0x80000000);
        writeFile(progress.progressFileForTesting(), futureO8pg);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertSame(OctavoProgressStore.LoadStatus.FUTURE,
                           activity.progressStoreForTesting().loadStatus());
                assertTrue(activity.openFixtureForTesting());
            });
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);

            scenario.recreate();
            awaitSettledReceipt(scenario);
            awaitRetryPrompt(scenario, true);
            AtomicReference<long[]> navigationBefore =
                new AtomicReference<>();
            AtomicReference<OctavoSurfaceView.ProgressPresentationReceipt>
                receiptBefore = new AtomicReference<>();
            AtomicReference<OctavoProgressSyncStore.Pending> pendingBefore =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                OctavoProgressSyncStore.Pending pending = store.pending();
                assertNotNull(pending);
                assertTrue(expectedPending.sameIdentity(pending));
                pendingBefore.set(pending);
                assertTrue(activity
                    .progressSyncO8pgFutureBlockedForTesting());
                assertArrayEquals(exactO1ps, canonicalBytes(store));
                assertArrayEquals(exactO1ps, readFileUnchecked(
                    store.stateFileForTesting()));
                assertArrayEquals(futureO8pg, readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                long[] navigation = surface(activity)
                    .navigationStateForTesting();
                assertNotNull(navigation);
                assertEquals(0, navigation[
                    OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
                assertSame(origin, presentedReceipt(activity).choice);
                assertTrue(navigation[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_GENERATION] > 0);
                assertEquals(navigation[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION],
                    navigation[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]);
                navigationBefore.set(navigation.clone());
                receiptBefore.set(presentedReceipt(activity));
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitRetryPrompt(scenario, true);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                OctavoProgressSyncStore.Pending pending = store.pending();
                assertNotNull(pending);
                assertTrue(pendingBefore.get().sameIdentity(pending));
                assertArrayEquals(exactO1ps, canonicalBytes(store));
                assertArrayEquals(exactO1ps, readFileUnchecked(
                    store.stateFileForTesting()));
                assertArrayEquals(futureO8pg, readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                long[] before = navigationBefore.get();
                long[] after = surface(activity)
                    .navigationStateForTesting();
                assertNotNull(after);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_GENERATION]);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE]);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]);
                assertEquals(0, after[
                    OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    presentedReceipt(activity);
                assertSame(origin, receipt.choice);
                assertEquals(receiptBefore.get().progressGeneration,
                             receipt.progressGeneration);
                assertTrue(activity
                    .progressSyncO8pgFutureBlockedForTesting());
                assertTrue(activity
                    .progressSyncAwaitingExplicitRetryForTesting());
                assertTrue(activity.progressSyncPromptForTesting()
                    .statusForTesting().getText().toString()
                    .toLowerCase(Locale.US).contains("newer"));
                activity.onBackPressed();
            });
            awaitRetryPrompt(scenario, true);

            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                OctavoProgressSyncStore.Pending pending = store.pending();
                assertNotNull(pending);
                assertTrue(pendingBefore.get().sameIdentity(pending));
                assertSame(OctavoProgressSyncStore.PendingDirection.FORWARD,
                           pending.direction);
                assertArrayEquals(exactO1ps, canonicalBytes(store));
                assertArrayEquals(exactO1ps, readFileUnchecked(
                    store.stateFileForTesting()));
                assertArrayEquals(futureO8pg, readFileUnchecked(
                    activity.progressStoreForTesting()
                        .progressFileForTesting()));
                long[] before = navigationBefore.get();
                long[] after = surface(activity)
                    .navigationStateForTesting();
                assertNotNull(after);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_GENERATION],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_GENERATION]);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE]);
                assertEquals(before[
                        OctavoSurfaceView
                            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE],
                    after[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]);
                assertEquals(0, after[
                    OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    presentedReceipt(activity);
                assertSame(origin, receipt.choice);
                assertEquals(receiptBefore.get().progressGeneration,
                             receipt.progressGeneration);
                assertTrue(activity
                    .progressSyncO8pgFutureBlockedForTesting());
                assertTrue(activity
                    .progressSyncAwaitingExplicitRetryForTesting());
                assertTrue(activity.progressSyncPromptForTesting()
                    .statusForTesting().getText().toString()
                    .toLowerCase(Locale.US).contains("newer"));
            });
        }
    }

    @Test
    public void overboundRawMergeIsLimitAndRetryReloadsWithoutPayload()
        throws IOException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            byte[] overbound = new byte[
                OctavoProgressPortable.maximumFutureBytes() + 1];
            writeInt(overbound, 0,
                     OctavoProgressPortable.magicForTesting());
            writeInt(overbound, Integer.BYTES, 0x80000000);
            AtomicReference<byte[]> stateBefore = new AtomicReference<>();
            AtomicReference<Integer> laneCountBefore =
                new AtomicReference<>();
            AtomicReference<Integer> candidateCountBefore =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                OctavoProgressDisplay presented =
                    presentedReceipt(activity).choice;
                stateBefore.set(canonicalBytes(store));
                laneCountBefore.set(portableLaneCount(store));
                candidateCountBefore.set(
                    store.reviewCandidates(presented).size());
                assertSame(
                    OctavoProgressSyncStore.PortableMergeResult.LIMIT,
                    store.mergePortableBytes(overbound));
                assertArrayEquals(stateBefore.get(), canonicalBytes(store));
                assertFalse(activity
                    .mergeSimulatedRemoteProgressForTesting(overbound));
                assertArrayEquals(stateBefore.get(), canonicalBytes(store));
                assertEquals(laneCountBefore.get().intValue(),
                             portableLaneCount(store));
                assertEquals(candidateCountBefore.get().intValue(),
                    store.reviewCandidates(presented).size());
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertNull(activity.pendingProgressTransactionForTesting());
            });
            awaitRetryPrompt(scenario, false);
            scenario.onActivity(activity -> {
                assertTrue(activity.progressSyncPromptForTesting()
                    .statusForTesting().getText().toString()
                    .toLowerCase(Locale.US).contains("limit"));
                activity.progressSyncPromptForTesting()
                    .retryForTesting().performClick();
            });
            awaitNoPrompt(scenario);
            scenario.onActivity(activity -> {
                OctavoProgressSyncStore store =
                    activity.progressSyncStoreForTesting();
                assertArrayEquals(stateBefore.get(), canonicalBytes(store));
                assertEquals(laneCountBefore.get().intValue(),
                             portableLaneCount(store));
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertNull(activity.pendingProgressTransactionForTesting());
                assertFalse(activity
                    .progressSyncAwaitingExplicitRetryForTesting());
            });
        }
    }

    @Test
    public void finalizedO1psRepairsMissingAndCorruptO8pgWithoutLaneAdvance()
        throws IOException {
        verifyFinalizedO1psRepairsO8pg(false);
        clearAllDurableTestState();
        verifyFinalizedO1psRepairsO8pg(true);
    }

    @Test
    public void corruptO8pgFallsBackOnlyAfterFrameAndStaysSeparate()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoProgressStore disk = new OctavoProgressStore(context);
        byte[] corrupt = new byte[20];
        writeInt(corrupt, 0, 0x4F385047);
        writeInt(corrupt, 4, 1);
        writeInt(corrupt, 8, 1);
        writeInt(corrupt, 12,
                 OctavoProgressDisplay.PERCENTAGE.nativeId());
        writeInt(corrupt, 16, 0x7fffffff);
        writeFile(disk.progressFileForTesting(), corrupt);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertSame(OctavoProgressStore.LoadStatus.CORRUPT,
                           activity.progressStoreForTesting().loadStatus());
                assertTrue(activity.progressStoreForTesting()
                    .recoveredFromCorruption());
                assertNull(activity.progressSyncStoreForTesting()
                    .localLane());
                assertTrue(activity.openFixtureForTesting());
                assertTrue(surface(activity)
                    .forcePresentFailuresForTesting(1));
                assertNull(activity.progressSyncStoreForTesting()
                    .localLane());
                assertNull(activity.progressSyncPromptForTesting());
            });
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    presentedReceipt(activity);
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(receipt.choice));
                assertNotNull(activity.progressSyncStoreForTesting()
                    .localLane());
                File local = activity.progressStoreForTesting()
                    .progressFileForTesting();
                File sync = activity.progressSyncStoreForTesting()
                    .stateFileForTesting();
                assertFalse(local.getAbsolutePath().equals(
                    sync.getAbsolutePath()));
                assertEquals(OctavoProgressStore.recordBytesForTesting(),
                             local.length());
                assertTrue(sync.length() > local.length());
            });
        }
    }

    @Test
    public void ownedModalDefersThenDrainsAccessibleReducedMotionPrompt() {
        Context context = ApplicationProvider.getApplicationContext();
        assertTrue(new OctavoAppearanceStore(context).save(
            OctavoAppearance.defaults().withReducedMotion(true)));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixtureAndAwaitReader(scenario);
            AtomicReference<OctavoProgressDisplay> origin =
                new AtomicReference<>();
            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                origin.set(presentedReceipt(activity).choice);
                target.set(nextDisplay(origin.get(), 3));
                assertTrue(surface(activity).requestFocus());
                activity.openAppearancePanelForTesting();
                assertNotNull(activity.appearancePanelForTesting());
                assertTrue(activity.simulateRemoteProgressForTesting(
                    REMOTE_A, 37, target.get()));
                assertNull(activity.progressSyncPromptForTesting());
            });
            assertPromptRemainsAbsent(scenario, 350);
            scenario.onActivity(activity ->
                activity.closeAppearancePanelForTesting());
            awaitChoicePrompt(scenario, target.get());
            scenario.onActivity(activity -> {
                OctavoProgressSyncPrompt prompt =
                    activity.progressSyncPromptForTesting();
                assertChoiceModal(activity, prompt,
                                  origin.get(), target.get());
                assertEquals(0.0f, prompt.getTranslationY(), 0.0f);
                assertEquals(1.0f, prompt.getAlpha(), 0.0f);
                assertEquals(1,
                    surface(activity).nativeStateForTesting()[
                        OctavoSurfaceView.STATE_REDUCED_MOTION]);
                assertEquals(0,
                    activity.progressSyncPromptMotionDurationForTesting());
                assertLargeTextWraps(activity);
                prompt.keepMineForTesting().performClick();
            });
            awaitNoPrompt(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity).hasFocus(),
                "Closing the progress prompt did not restore reader focus");
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
    }

    private static OctavoSurfaceView surface(OctavoActivity activity) {
        OctavoSurfaceView result = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        assertNotNull(result);
        return result;
    }

    private static OctavoSurfaceView.ProgressPresentationReceipt
        presentedReceipt(OctavoActivity activity) {
        OctavoSurfaceView.ProgressPresentationReceipt result =
            surface(activity).currentProgressPresentationReceipt();
        assertNotNull(result);
        assertTrue(result.strictResumeSettled);
        return result;
    }

    private static OctavoProgressSyncPrompt awaitChoicePrompt(
        ActivityScenario<OctavoActivity> scenario,
        OctavoProgressDisplay expectedTarget) {
        AtomicReference<OctavoProgressSyncPrompt> result =
            new AtomicReference<>();
        awaitActivity(
            scenario,
            activity -> {
                OctavoProgressSyncPrompt prompt =
                    activity.progressSyncPromptForTesting();
                OctavoProgressSyncStore.Candidate candidate =
                    activity.pendingProgressSyncCandidateForTesting();
                if (prompt == null || candidate == null
                    || !prompt.isShown() || prompt.getWidth() <= 0
                    || !prompt.useDisplayForTesting().isEnabled()
                    || !prompt.keepMineForTesting().isEnabled()
                    || prompt.retryForTesting().getVisibility()
                       != View.GONE
                    || (expectedTarget != null
                        && candidate.targetDisplay() != expectedTarget)) {
                    return false;
                }
                result.set(prompt);
                return true;
            },
            "8vo did not show a settled progress-display choice");
        return result.get();
    }

    private static void awaitRetryPrompt(
        ActivityScenario<OctavoActivity> scenario,
        boolean pendingRequired) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoProgressSyncPrompt prompt =
                    activity.progressSyncPromptForTesting();
                return prompt != null && prompt.isShown()
                    && activity
                        .progressSyncAwaitingExplicitRetryForTesting()
                    && (!pendingRequired
                        || activity.pendingProgressTransactionForTesting()
                           != null)
                    && prompt.retryForTesting().getVisibility()
                       == View.VISIBLE
                    && prompt.retryForTesting().isEnabled()
                    && !prompt.useDisplayForTesting().isEnabled()
                    && !prompt.keepMineForTesting().isEnabled()
                    && prompt.statusIsErrorForTesting();
            },
            "The progress-display failure did not expose explicit Retry");
    }

    private static void awaitAppliedProgress(
        ActivityScenario<OctavoActivity> scenario,
        OctavoProgressDisplay expected) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    surface(activity)
                        .currentProgressPresentationReceipt();
                OctavoProgressPortable.Lane local =
                    activity.progressSyncStoreForTesting().localLane();
                return receipt != null && receipt.strictResumeSettled
                    && receipt.choice == expected
                    && local != null
                    && local.choice.toDisplay() == expected
                    && activity.pendingProgressTransactionForTesting()
                       == null
                    && activity.progressSyncPromptForTesting() == null
                    && !activity
                        .progressSyncAwaitingExplicitRetryForTesting()
                    && activity.progressStoreForTesting()
                        .hasCanonicalCurrentRecord(expected);
            },
            "The exact progress display was not durably applied");
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
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    view.currentProgressPresentationReceipt();
                OctavoProgressPortable.Lane local =
                    activity.progressSyncStoreForTesting().localLane();
                return state != null
                    && state.length == OctavoSurfaceView.STATE_FIELD_COUNT
                    && state[OctavoSurfaceView.STATE_RESUMED] == 1
                    && state[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && state[
                        OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
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
                    && local != null
                    && local.choice.toDisplay() == receipt.choice
                    && activity.progressStoreForTesting()
                        .hasCanonicalCurrentRecord(receipt.choice)
                    && activity
                        .progressSyncReviewInitializedForTesting();
            },
            "8vo did not present a settled progress-sync reader frame");
    }

    private static void awaitSettledReceipt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                return view != null
                    && view.currentProgressPresentationReceipt() != null;
            },
            "8vo did not expose a settled progress receipt");
    }

    private static void awaitNoPrompt(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> activity.progressSyncPromptForTesting() == null
                && !activity
                    .progressSyncAwaitingExplicitRetryForTesting(),
            "The progress-display prompt did not close");
    }

    private static void awaitNoPromptAndNoPending(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(
            scenario,
            activity -> activity.progressSyncPromptForTesting() == null
                && activity.pendingProgressTransactionForTesting() == null
                && !activity
                    .progressSyncAwaitingExplicitRetryForTesting(),
            "The progress-display rollback did not finish");
    }

    private static void assertPromptRemainsAbsent(
        ActivityScenario<OctavoActivity> scenario,
        long durationMillis) {
        long deadline = SystemClock.uptimeMillis() + durationMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> assertNull(
                activity.progressSyncPromptForTesting()));
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

    private static RadioButton progressOption(
        RadioGroup group,
        OctavoProgressDisplay display) {
        for (int index = 0; index < group.getChildCount(); ++index) {
            View child = group.getChildAt(index);
            if (child instanceof RadioButton
                && child.getTag() == display) {
                return (RadioButton)child;
            }
        }
        return null;
    }

    private static OctavoProgressDisplay nextDisplay(
        OctavoProgressDisplay origin,
        int delta) {
        OctavoProgressDisplay[] values = OctavoProgressDisplay.values();
        return values[(origin.ordinal() + delta) % values.length];
    }

    private static void assertSameAnchor(
        OctavoSurfaceView.ProgressPresentationReceipt expected,
        OctavoSurfaceView.ProgressPresentationReceipt actual) {
        assertEquals(expected.anchorSpineIndex, actual.anchorSpineIndex);
        assertEquals(expected.anchorByteOffset, actual.anchorByteOffset);
    }

    private static void assertChoiceModal(
        OctavoActivity activity,
        OctavoProgressSyncPrompt prompt,
        OctavoProgressDisplay origin,
        OctavoProgressDisplay remote) {
        assertNotNull(prompt);
        assertSame(origin, prompt.yoursForTesting());
        assertSame(remote, prompt.otherForTesting());
        assertTrue(prompt.headingForTesting().getText().toString()
            .startsWith("Another device uses a different progress display"));
        assertTrue(prompt.helperForTesting().getText().toString()
            .contains("does not move your reading place"));
        assertTrue(prompt.comparisonForTesting().getText().toString()
            .contains("Yours: " + origin.label()));
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                     prompt.statusForTesting()
                         .getAccessibilityLiveRegion());
        if (Build.VERSION.SDK_INT >= 28) {
            assertTrue(prompt.headingForTesting()
                .isAccessibilityHeading());
            assertEquals("Progress display confirmation",
                         prompt.getAccessibilityPaneTitle().toString());
        }
        int minimum = Math.round(
            48 * activity.getResources().getDisplayMetrics().density);
        assertTrue(prompt.useDisplayForTesting().getHeight() >= minimum);
        assertTrue(prompt.keepMineForTesting().getHeight() >= minimum);
        assertTrue(prompt.retryForTesting().getMinHeight() >= minimum);
        assertTrue(prompt.useDisplayForTesting().isFocusableInTouchMode());
        assertTrue(prompt.keepMineForTesting().isFocusableInTouchMode());
        assertTrue(prompt.retryForTesting().isFocusableInTouchMode());
        assertNotNull(prompt.scrollForTesting());
        assertTrue(prompt.useDisplayForTesting().hasFocus()
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
        OctavoProgressSyncPrompt prompt = new OctavoProgressSyncPrompt(
            largeText,
            activity.appearanceForTesting(),
            OctavoProgressDisplay.PERCENTAGE,
            OctavoProgressDisplay.LOCATION,
            new OctavoProgressSyncPrompt.Listener() {
                @Override public void onUseDisplay() { }
                @Override public void onKeepMine() { }
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

    private static byte[] canonicalBytes(
        OctavoProgressSyncStore store) {
        try {
            return store.canonicalBytesForTesting();
        } catch (IOException exception) {
            throw new AssertionError(
                "O1PS canonical bytes were unavailable", exception);
        }
    }

    private static int portableLaneCount(
        OctavoProgressSyncStore store) {
        OctavoProgressSyncStore.PortableExport exported =
            store.exportPortable();
        assertSame(OctavoProgressSyncStore.PortableExportStatus.EXPORTED,
                   exported.status);
        OctavoProgressPortable.DecodeResult decoded =
            OctavoProgressPortable.decode(exported.bytes());
        assertSame(OctavoProgressPortable.DecodeStatus.READY,
                   decoded.status);
        assertNotNull(decoded.snapshot());
        return decoded.snapshot().laneCount();
    }

    private static void verifyFinalizedO1psRepairsO8pg(
        boolean corruptO8pg) throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoProgressDisplay synchronizedDisplay =
            OctavoProgressDisplay.LOCATION;
        OctavoProgressStore progress = new OctavoProgressStore(context);
        assertSame(OctavoProgressDisplay.PERCENTAGE, progress.load());
        assertSame(OctavoProgressStore.LoadStatus.MISSING,
                   progress.loadStatus());
        assertTrue(progress.save(synchronizedDisplay));

        OctavoProgressSyncStore seeded =
            new OctavoProgressSyncStore(context);
        assertSame(OctavoProgressSyncStore.LoadStatus.MISSING_CREATED,
                   seeded.load());
        assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
                   seeded.stageInitialPresented(synchronizedDisplay));
        OctavoProgressSyncStore.Pending pending = seeded.pending();
        assertNotNull(pending);
        assertSame(OctavoProgressSyncStore.MutationResult.UPDATED,
            seeded.completePending(
                pending, synchronizedDisplay, synchronizedDisplay,
                OctavoProgressSyncStore.O8pgProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        OctavoProgressPortable.Lane localBefore = seeded.localLane();
        assertNotNull(localBefore);
        long sequenceBefore = localBefore.sequence;

        byte[] corruptBytes = new byte[20];
        if (corruptO8pg) {
            writeFile(progress.progressFileForTesting(), corruptBytes);
        } else {
            assertTrue(progress.progressFileForTesting().delete());
        }

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertSame(corruptO8pg
                        ? OctavoProgressStore.LoadStatus.CORRUPT
                        : OctavoProgressStore.LoadStatus.MISSING,
                    activity.progressStoreForTesting().loadStatus());
                if (corruptO8pg) {
                    assertArrayEquals(corruptBytes, readFileUnchecked(
                        activity.progressStoreForTesting()
                            .progressFileForTesting()));
                } else {
                    assertFalse(activity.progressStoreForTesting()
                        .progressFileForTesting().exists());
                }
                OctavoProgressPortable.Lane loaded =
                    activity.progressSyncStoreForTesting().localLane();
                assertNotNull(loaded);
                assertEquals(sequenceBefore, loaded.sequence);
                assertSame(synchronizedDisplay,
                           loaded.choice.toDisplay());
                assertTrue(activity.openFixtureForTesting());
            });
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertSame(synchronizedDisplay,
                           presentedReceipt(activity).choice);
                assertSame(OctavoProgressStore.LoadStatus.CURRENT,
                           activity.progressStoreForTesting().loadStatus());
                assertTrue(activity.progressStoreForTesting()
                    .hasCanonicalCurrentRecord(synchronizedDisplay));
                OctavoProgressPortable.Lane recovered =
                    activity.progressSyncStoreForTesting().localLane();
                assertNotNull(recovered);
                assertEquals(sequenceBefore, recovered.sequence);
                assertSame(synchronizedDisplay,
                           recovered.choice.toDisplay());
                assertNull(activity.pendingProgressTransactionForTesting());
                assertNull(activity
                    .pendingProgressSyncCandidateForTesting());
                assertNull(activity.progressSyncPromptForTesting());
            });
        }
    }

    private static byte[] readFileUnchecked(File file) {
        try {
            return readFile(file);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read test state", exception);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        byte[] result = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < result.length) {
                int count = input.read(
                    result, offset, result.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            assertEquals(result.length, offset);
            assertEquals(-1, input.read());
        }
        return result;
    }

    private static void writeFile(File file, byte[] bytes)
        throws IOException {
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static String device(int value) {
        return String.format(Locale.US, "%032x", value);
    }

    private static void clearAllDurableTestState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoLibrarySyncStore.clearForTesting(context);
        OctavoLibraryMembershipStore.clearForTesting(context);
        OctavoBookTransferStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        OctavoNoteDraftStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
    }
}
