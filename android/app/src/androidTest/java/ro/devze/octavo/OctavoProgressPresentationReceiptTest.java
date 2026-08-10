package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoProgressPresentationReceiptTest {
    private static final long WAIT_MILLIS = 12_000;
    private static final long OVERBOUND_BYTE = Long.MAX_VALUE - 1;

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
    public void firstRealFrameExposesExactCommittedReceipt() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
                assertNull(surface(activity)
                    .currentProgressPresentationReceipt());
            });
            awaitActivity(
                scenario,
                activity -> surface(activity)
                    .currentProgressPresentationReceipt() != null,
                "The first real frame did not expose a progress receipt");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    view.currentProgressPresentationReceipt();
                long[] state = view.nativeStateForTesting();
                long[] navigation = view.navigationStateForTesting();
                long[] position = view.readingPositionForTesting();
                assertNotNull(receipt);
                assertNotNull(state);
                assertNotNull(navigation);
                assertNotNull(position);
                assertSame(view.presentedProgressDisplay(), receipt.choice);
                assertEquals(
                    navigation[OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION],
                    receipt.progressGeneration);
                assertEquals(state[OctavoSurfaceView.STATE_FRAME_COUNT],
                             receipt.frameCount);
                assertTrue(receipt.frameCount > 0);
                assertEquals(position[1], receipt.anchorSpineIndex);
                assertEquals(position[2], receipt.anchorByteOffset);
                assertEquals(state[OctavoSurfaceView.STATE_SPINE_INDEX],
                             receipt.pageSpineIndex);
                assertEquals(
                    state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE],
                    receipt.pageFirstByte);
                assertEquals(
                    state[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE],
                    receipt.pageOnePastLastByte);
                assertTrue(receipt.strictResumeSettled);
            });
        }
    }

    @Test
    public void progressRequestWaitsForExactPostedModeAndGeneration() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity)
                    .currentProgressPresentationReceipt() != null,
                "The initial progress receipt was not exposed");

            AtomicReference<OctavoProgressDisplay> target =
                new AtomicReference<>();
            AtomicReference<Long> priorGeneration =
                new AtomicReference<>();
            AtomicReference<Long> priorFrame = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.ProgressPresentationReceipt before =
                    view.currentProgressPresentationReceipt();
                assertNotNull(before);
                priorGeneration.set(before.progressGeneration);
                priorFrame.set(before.frameCount);
                OctavoProgressDisplay[] choices =
                    OctavoProgressDisplay.values();
                target.set(choices[
                    (before.choice.ordinal() + 1) % choices.length]);
                assertEquals(
                    OctavoNative.NAVIGATION_ACCEPTED,
                    view.requestProgressDisplay(target.get()));
                long[] navigation = view.navigationStateForTesting();
                assertNotNull(navigation);
                assertEquals(
                    1,
                    navigation[OctavoSurfaceView
                        .NAVIGATION_STATE_PENDING]);
                assertNull(view.currentProgressPresentationReceipt());
            });

            awaitActivity(
                scenario,
                activity -> {
                    OctavoSurfaceView.ProgressPresentationReceipt receipt =
                        surface(activity)
                            .currentProgressPresentationReceipt();
                    return receipt != null
                        && receipt.choice == target.get()
                        && receipt.progressGeneration
                           > priorGeneration.get()
                        && receipt.frameCount > priorFrame.get();
                },
                "The requested progress mode did not reach an exact frame");
        }
    }

    @Test
    public void unresolvedStrictRecoverySuppressesFallbackReceipt() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoReadingPositionStore store =
            new OctavoReadingPositionStore(context);
        assertEquals(OctavoReadingPositionStore.LoadStatus.MISSING,
                     store.load());
        assertTrue(store.recordSuccessfullyPresented(
            OctavoFixture.SHA256,
            0, OVERBOUND_BYTE,
            0, OVERBOUND_BYTE, Long.MAX_VALUE,
            true).succeeded());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitActivity(
                scenario,
                activity -> {
                    long[] state = surface(activity)
                        .nativeStateForTesting();
                    return state != null
                        && state.length
                           == OctavoSurfaceView.STATE_FIELD_COUNT
                        && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                        && state[OctavoSurfaceView
                            .STATE_RESTORE_REQUESTED] == 1
                        && state[OctavoSurfaceView
                            .STATE_RESTORE_ATTEMPTED] == 1
                        && state[OctavoSurfaceView
                            .STATE_RESTORE_SUCCEEDED] == 0;
                },
                "The strict-resume fallback frame was not posted");

            scenario.onActivity(activity -> assertNull(
                surface(activity)
                    .currentProgressPresentationReceipt()));
        }
    }

    @Test
    public void allPresentationTransactionsFailClosed() {
        ReceiptFixture fixture = new ReceiptFixture();
        assertNotNull(fixture.receipt());

        long[] state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_FRAME_COUNT] = 0;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        int[] activeFields = {
            OctavoSurfaceView.STATE_RESUMED,
            OctavoSurfaceView.STATE_HAS_SURFACE,
            OctavoSurfaceView.STATE_DOCUMENT_OPEN,
            OctavoSurfaceView.STATE_READER_FRAME_READY,
        };
        for (int field : activeFields) {
            state = fixture.state.clone();
            state[field] = 0;
            assertNull(fixture.receipt(state, fixture.navigation,
                                       fixture.position));
        }

        state = fixture.state.clone();
        state[OctavoSurfaceView
            .STATE_PAGE_MOVE_PRESENTATION_PENDING] = 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING] = 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_HOST_PRESENTATION_PENDING] = 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] += 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        long[] navigation = fixture.navigation.clone();
        navigation[OctavoSurfaceView.NAVIGATION_STATE_PENDING] = 1;
        assertNull(fixture.receipt(fixture.state, navigation,
                                   fixture.position));

        navigation = fixture.navigation.clone();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_SEMANTIC_GENERATION] += 1;
        assertNull(fixture.receipt(fixture.state, navigation,
                                   fixture.position));

        assertNull(fixture.receipt(true, false, false));
        assertNull(fixture.receipt(false, true, false));
        assertNull(fixture.receipt(false, false, true));
    }

    @Test
    public void generationModeAnchorAndPageMustMatchExactly() {
        ReceiptFixture fixture = new ReceiptFixture();
        assertNotNull(fixture.receipt());

        long[] navigation = fixture.navigation.clone();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_GENERATION] += 1;
        assertNull(fixture.receipt(fixture.state, navigation,
                                   fixture.position));

        navigation = fixture.navigation.clone();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_GENERATION] = 0;
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION] = 0;
        assertNull(fixture.receipt(fixture.state, navigation,
                                   fixture.position));

        navigation = fixture.navigation.clone();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE] =
                OctavoProgressDisplay.PAGE.nativeId();
        assertNull(fixture.receipt(fixture.state, navigation,
                                   fixture.position));

        navigation = fixture.navigation.clone();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE] =
                OctavoProgressDisplay.PAGE.nativeId();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE] =
                OctavoProgressDisplay.PAGE.nativeId();
        assertNull(fixture.receipt(fixture.state, navigation,
                                   fixture.position));

        long[] position = fixture.position.clone();
        position[0] = 0;
        assertNull(fixture.receipt(fixture.state, fixture.navigation,
                                   position));

        position = fixture.position.clone();
        position[1] = 0x1_0000_0000L;
        assertNull(fixture.receipt(fixture.state, fixture.navigation,
                                   position));

        position = fixture.position.clone();
        position[2] = -1;
        assertNull(fixture.receipt(fixture.state, fixture.navigation,
                                   position));

        long[] state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX] += 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET] += 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_SPINE_INDEX] += 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE] =
            fixture.position[2] + 1;
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE] =
            fixture.position[2];
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));

        state = fixture.state.clone();
        state[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE] =
            state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE];
        assertNull(fixture.receipt(state, fixture.navigation,
                                   fixture.position));
    }

    private static final class ReceiptFixture {
        private final OctavoProgressDisplay choice =
            OctavoProgressDisplay.LOCATION;
        private final long[] state = committedState();
        private final long[] navigation = committedNavigation(choice);
        private final long[] position = {1, 3, 42};

        OctavoSurfaceView.ProgressPresentationReceipt receipt() {
            return receipt(false, false, false);
        }

        OctavoSurfaceView.ProgressPresentationReceipt receipt(
            boolean javaPresentationPending,
            boolean javaAppearanceUnsettled,
            boolean strictResumeBlocked) {
            return OctavoSurfaceView
                .progressPresentationReceiptForTesting(
                    choice, state, navigation, position,
                    javaPresentationPending,
                    javaAppearanceUnsettled,
                    strictResumeBlocked);
        }

        OctavoSurfaceView.ProgressPresentationReceipt receipt(
            long[] candidateState,
            long[] candidateNavigation,
            long[] candidatePosition) {
            return OctavoSurfaceView
                .progressPresentationReceiptForTesting(
                    choice,
                    candidateState,
                    candidateNavigation,
                    candidatePosition,
                    false,
                    false,
                    false);
        }
    }

    private static long[] committedState() {
        long[] state = new long[OctavoSurfaceView.STATE_FIELD_COUNT];
        state[OctavoSurfaceView.STATE_RESUMED] = 1;
        state[OctavoSurfaceView.STATE_HAS_SURFACE] = 1;
        state[OctavoSurfaceView.STATE_DOCUMENT_OPEN] = 1;
        state[OctavoSurfaceView.STATE_READER_FRAME_READY] = 1;
        state[OctavoSurfaceView.STATE_FRAME_COUNT] = 7;
        state[OctavoSurfaceView.STATE_SPINE_INDEX] = 3;
        state[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX] = 3;
        state[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET] = 42;
        state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE] = 40;
        state[OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE] = 60;
        state[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] = 5;
        state[OctavoSurfaceView.STATE_APPEARANCE_PRESENTED_GENERATION] = 5;
        return state;
    }

    private static long[] committedNavigation(
        OctavoProgressDisplay choice) {
        long[] navigation = new long[
            OctavoSurfaceView.NAVIGATION_STATE_FIELD_COUNT];
        navigation[OctavoSurfaceView.NAVIGATION_STATE_VERSION] = 1;
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_SEMANTIC_GENERATION] = 11;
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION] = 11;
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_GENERATION] = 13;
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION] = 13;
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_REQUESTED_MODE] = choice.nativeId();
        navigation[OctavoSurfaceView
            .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE] = choice.nativeId();
        return navigation;
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

    private static void clearAllDurableTestState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoLibrarySyncStore.clearForTesting(context);
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
