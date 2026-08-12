package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.SystemClock;

import androidx.lifecycle.Lifecycle;
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
public final class OctavoStrictResumePersistenceGateTest {
    private static final long WAIT_MILLIS = 12_000;
    private static final long OVERBOUND_BYTE = Long.MAX_VALUE - 1;

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    @Before
    public void prepareOverboundLocalPosition() {
        clearAllDurableTestState();
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
    }

    @After
    public void clearDurableTestStateAfterward() {
        clearAllDurableTestState();
    }

    @Test
    public void unresolvedStrictResumeNeverLeaksSafeFallbackToCatalog() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openFixtureForTesting());
            });
            awaitStrictResumeFailure(scenario);

            scenario.onActivity(activity -> {
                surface(activity).flushPersistenceForTesting();
                assertNoLegacyPosition(activity);
                assertOverboundLocalLane(activity);
            });

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            awaitStrictResumeFailure(scenario);

            scenario.onActivity(activity -> {
                assertNoLegacyPosition(activity);
                assertOverboundLocalLane(activity);
                activity.closeBookForTesting();
                assertTrue(activity.libraryVisibleForTesting());
                assertNoLegacyPosition(activity);
                assertOverboundLocalLane(activity);
            });
        }
    }

    private static void awaitStrictResumeFailure(
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
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && state[OctavoSurfaceView.STATE_RESTORE_REQUESTED] == 1
                    && state[OctavoSurfaceView.STATE_RESTORE_ATTEMPTED] == 1
                    && state[OctavoSurfaceView.STATE_RESTORE_SUCCEEDED] == 0;
            },
            "The overbound strict resume did not fail after a safe frame");
    }

    private static void assertNoLegacyPosition(OctavoActivity activity) {
        OctavoLibraryStore.Book book = activity.libraryStoreForTesting()
            .findBook(OctavoFixture.SHA256);
        assertNotNull(book);
        assertFalse(book.hasPosition);
    }

    private static void assertOverboundLocalLane(
        OctavoActivity activity) {
        OctavoReadingPositionPortable.Lane lane =
            activity.readingPositionStoreForTesting()
                .localLane(OctavoFixture.SHA256);
        assertNotNull(lane);
        assertEquals(0, lane.spineIndex);
        assertEquals(OVERBOUND_BYTE, lane.byteOffset);
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
