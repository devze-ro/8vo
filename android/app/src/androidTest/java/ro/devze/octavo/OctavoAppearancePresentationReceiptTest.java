package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
public final class OctavoAppearancePresentationReceiptTest {
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
    public void committedReceiptMatchesExactReaderStateAndContainingPage() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity)
                    .currentAppearancePresentationReceipt() != null,
                "The initial committed appearance receipt was not exposed");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.AppearancePresentationReceipt receipt =
                    view.currentAppearancePresentationReceipt();
                long[] state = view.nativeStateForTesting();
                long[] position = view.readingPositionForTesting();
                assertNotNull(receipt);
                assertNotNull(state);
                assertNotNull(position);
                assertEquals(view.presentedAppearanceForTesting(),
                             receipt.profile);
                assertEquals(
                    state[OctavoSurfaceView
                        .STATE_APPEARANCE_PRESENTED_GENERATION],
                    receipt.appearanceGeneration);
                assertEquals(state[OctavoSurfaceView.STATE_FRAME_COUNT],
                             receipt.frameCount);
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
                assertTrue(receipt.anchorByteOffset
                           >= receipt.pageFirstByte);
                assertTrue(receipt.anchorByteOffset
                           < receipt.pageOnePastLastByte);
                assertTrue(receipt.strictResumeSettled);
            });
        }
    }

    @Test
    public void queuedAppearanceRequestSuppressesCommittedReceipt() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity)
                    .currentAppearancePresentationReceipt() != null,
                "The initial committed appearance receipt was not exposed");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoAppearance current =
                    view.presentedAppearanceForTesting();
                OctavoAppearance target = current.withTheme(
                    (current.themeId() + 1)
                    % OctavoAppearance.THEME_COUNT);
                view.requestAppearanceForTesting(target);
                assertNull(view.currentAppearancePresentationReceipt());
            });
        }
    }

    @Test
    public void unresolvedStrictResumeSuppressesSafeFallbackReceipt() {
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
                    OctavoSurfaceView view = surface(activity);
                    long[] state = view.nativeStateForTesting();
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
                "The strict-resume safe fallback was not presented");

            scenario.onActivity(activity -> assertNull(
                surface(activity)
                    .currentAppearancePresentationReceipt()));
        }
    }

    @Test
    public void nativePendingTransactionsSuppressReceiptUntilExactCommit() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitActivity(
                scenario,
                activity -> surface(activity)
                    .currentAppearancePresentationReceipt() != null,
                "The initial committed appearance receipt was not exposed");

            AtomicReference<OctavoAppearance> expectedProfile =
                new AtomicReference<>();
            AtomicReference<Long> priorFrame = new AtomicReference<>();

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.AppearancePresentationReceipt before =
                    view.currentAppearancePresentationReceipt();
                assertNotNull(before);
                expectedProfile.set(before.profile);
                priorFrame.set(before.frameCount);
                OctavoProgressDisplay current =
                    view.presentedProgressDisplay();
                OctavoProgressDisplay[] displays =
                    OctavoProgressDisplay.values();
                OctavoProgressDisplay target = displays[
                    (current.ordinal() + 1) % displays.length];
                assertEquals(
                    OctavoNative.NAVIGATION_ACCEPTED,
                    view.requestProgressDisplay(target));
                long[] navigation = view.navigationStateForTesting();
                assertNotNull(navigation);
                assertEquals(
                    1,
                    navigation[OctavoSurfaceView
                        .NAVIGATION_STATE_PENDING]);
                assertNull(view.currentAppearancePresentationReceipt());
            });
            awaitCommittedReceiptAfter(
                scenario, priorFrame.get(), expectedProfile.get(),
                "Navigation presentation did not commit exactly");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.AppearancePresentationReceipt before =
                    view.currentAppearancePresentationReceipt();
                assertNotNull(before);
                priorFrame.set(before.frameCount);
                long[] state = view.nativeStateForTesting();
                assertNotNull(state);
                assertTrue(state[OctavoSurfaceView.STATE_PAGE_COUNT] > 1);
                int direction = state[OctavoSurfaceView.STATE_PAGE_INDEX] + 1
                    < state[OctavoSurfaceView.STATE_PAGE_COUNT] ? 1 : -1;
                assertTrue(view.movePageForAccessibility(direction));
                long[] pending = view.nativeStateForTesting();
                assertNotNull(pending);
                assertEquals(
                    1,
                    pending[OctavoSurfaceView
                        .STATE_PAGE_MOVE_PRESENTATION_PENDING]);
                assertNull(view.currentAppearancePresentationReceipt());
            });
            awaitCommittedReceiptAfter(
                scenario, priorFrame.get(), expectedProfile.get(),
                "Page-move presentation did not commit exactly");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.AppearancePresentationReceipt before =
                    view.currentAppearancePresentationReceipt();
                assertNotNull(before);
                priorFrame.set(before.frameCount);
                int[] sizes = OctavoAppearance.fontSizesSp();
                int targetSize = sizes[0] == before.profile.fontSizeSp()
                    ? sizes[1] : sizes[0];
                OctavoAppearance target =
                    before.profile.withFontSizeSp(targetSize);
                assertTrue(view.forcePrePresentFailuresForTesting(2));
                view.requestAppearanceWithLocationWarmGateProbeForTesting(
                    target);
                long[] pending = view.nativeStateForTesting();
                assertNotNull(pending);
                assertEquals(
                    1,
                    pending[OctavoSurfaceView
                        .STATE_REFLOW_PRESENTATION_PENDING]);
                assertTrue(
                    pending[OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                    != pending[OctavoSurfaceView
                        .STATE_APPEARANCE_PRESENTED_GENERATION]);
                assertNull(view.currentAppearancePresentationReceipt());
            });
            awaitCommittedReceiptAfter(
                scenario, priorFrame.get(), expectedProfile.get(),
                "Reflow rollback presentation did not commit exactly");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                OctavoSurfaceView.AppearancePresentationReceipt before =
                    view.currentAppearancePresentationReceipt();
                assertNotNull(before);
                priorFrame.set(before.frameCount);
                view.replaceNativeSurfaceForTesting();
                long[] pending = view.nativeStateForTesting();
                assertNotNull(pending);
                assertEquals(
                    1,
                    pending[OctavoSurfaceView
                        .STATE_HOST_PRESENTATION_PENDING]);
                assertNull(view.currentAppearancePresentationReceipt());
            });
            awaitCommittedReceiptAfter(
                scenario, priorFrame.get(), expectedProfile.get(),
                "Host presentation did not commit exactly");
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

    private static void awaitCommittedReceiptAfter(
        ActivityScenario<OctavoActivity> scenario,
        long priorFrame,
        OctavoAppearance expectedProfile,
        String failureMessage) {
        awaitActivity(
            scenario,
            activity -> {
                OctavoSurfaceView.AppearancePresentationReceipt receipt =
                    surface(activity)
                        .currentAppearancePresentationReceipt();
                return receipt != null
                    && receipt.frameCount > priorFrame
                    && expectedProfile.equals(receipt.profile)
                    && receipt.pageSpineIndex == receipt.anchorSpineIndex
                    && receipt.anchorByteOffset >= receipt.pageFirstByte
                    && receipt.anchorByteOffset
                       < receipt.pageOnePastLastByte;
            },
            failureMessage);
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
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        OctavoNoteDraftStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
    }
}
