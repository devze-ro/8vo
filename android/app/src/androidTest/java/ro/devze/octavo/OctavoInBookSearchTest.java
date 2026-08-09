package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoInBookSearchTest {
    private static final long WAIT_MILLIS = 12_000;

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
        OctavoAnnotationStore.clearForTesting(context);
    }

    @Test
    public void queryDirectResultStepAndClearRemainPresentationGated() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);

            AtomicReference<long[]> origin = new AtomicReference<>();
            AtomicInteger commit = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                origin.set(view.readingPositionForTesting());
                commit.set(view.commitSearch("paragraph 250"));
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, commit.get());
            OctavoSearch results = awaitSearch(
                scenario,
                search -> search.isReady() && !search.isPending()
                    && search.query().equals("paragraph 250")
                    && search.totalCount() == 4,
                "Reader0 search results were not presented");
            assertEquals(4, results.rowCount());
            assertFalse(results.isTruncated());
            assertEquals(0, results.activeIndex());
            assertEquals("Chapter One", results.row(0).section());
            assertTrue(results.row(0).snippet().contains("paragraph 250"));

            long[] beforeNavigation = navigationState(scenario);
            AtomicInteger jump = new AtomicInteger();
            scenario.onActivity(activity -> jump.set(
                surface(activity).requestSearchResult(1)));
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, jump.get());
            long[] presented = awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[OctavoSurfaceView
                        .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       > beforeNavigation[OctavoSurfaceView
                           .NAVIGATION_STATE_SEMANTIC_GENERATION]
                    && state[OctavoSurfaceView
                        .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       == state[OctavoSurfaceView
                           .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                    && state[OctavoSurfaceView
                        .NAVIGATION_STATE_HISTORY_BACK_COUNT] == 1,
                "Search-result navigation was not committed");
            OctavoSearch active = awaitSearch(
                scenario,
                search -> !search.isPending() && search.activeIndex() == 1,
                "Presented search result did not become active");
            assertEquals("Chapter Two", active.row(1).section());
            assertNotEquals(origin.get()[1], readingPosition(scenario)[1]);

            AtomicReference<long[]> presentedPosition =
                new AtomicReference<>(readingPosition(scenario));
            AtomicInteger failedJump = new AtomicInteger();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(8));
                failedJump.set(view.requestSearchResult(2));
            });
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, failedJump.get());
            awaitNavigation(
                scenario,
                state -> state[OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[OctavoSurfaceView
                        .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                       == presented[OctavoSurfaceView
                           .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION],
                "Failed search presentation did not roll back");
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(0));
                assertTrue(view.presentPreparedFrameForTesting());
            });
            awaitReaderReady(scenario);
            assertArrayEquals(presentedPosition.get(), readingPosition(scenario));
            OctavoSearch rolledBack = searchSnapshot(scenario);
            if (rolledBack == null) {
                AtomicReference<long[]> packet = new AtomicReference<>();
                scenario.onActivity(activity -> packet.set(
                    surface(activity).searchPacketForTesting()));
                fail("Rolled-back search packet was invalid: "
                     + Arrays.toString(packet.get()));
            }
            assertEquals(1, rolledBack.activeIndex());
            AtomicInteger clear = new AtomicInteger();
            scenario.onActivity(activity -> clear.set(
                surface(activity).clearSearch()));
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED, clear.get());
            OctavoSearch cleared = awaitSearch(
                scenario,
                search -> !search.isPending() && search.query().isEmpty(),
                "Search clear was not presented");
            assertEquals(0, cleared.rowCount());
            assertEquals(0, cleared.totalCount());
        }
    }

    @Test
    public void sheetUsesNativeAccessibleControlsAndDisclosesTruncation() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertTrue(activity.setChromeVisibleForTesting(true));
                activity.openSearchPanelForTesting();
            });
            awaitActivity(
                scenario,
                activity -> activity.searchPanelForTesting() != null,
                "Find in book did not open");

            scenario.onActivity(activity -> {
                OctavoSearchPanel panel = activity.searchPanelForTesting();
                assertNotNull(panel);
                EditText input = panel.queryInputForTesting();
                assertTrue(input.hasFocus());
                assertEquals("Search text",
                             input.getContentDescription().toString());
                assertTrue(input.getMinHeight() >= dp(activity, 48));
                Button previous = panel.previousButtonForTesting();
                Button next = panel.nextButtonForTesting();
                assertTrue(previous.getMinHeight() >= dp(activity, 48));
                assertTrue(next.getMinHeight() >= dp(activity, 48));
                input.setText("chapter");
                ((Button)activity.findViewById(
                    R.id.octavo_search_submit)).performClick();
            });

            OctavoSearch results = awaitSearch(
                scenario,
                search -> !search.isPending()
                    && search.query().equals("chapter")
                    && search.rowCount() == OctavoSearch.MAX_ROWS,
                "Bounded chapter results were not presented");
            assertTrue(results.isTruncated());
            assertTrue(results.totalCount() > results.rowCount());
            scenario.onActivity(activity -> {
                OctavoSearchPanel panel = activity.searchPanelForTesting();
                assertNotNull(panel);
                assertEquals(OctavoSearch.MAX_ROWS,
                             panel.resultListForTesting().getChildCount());
                String status = panel.statusForTesting().getText().toString();
                assertTrue(status.contains(
                    "showing the first " + OctavoSearch.MAX_ROWS));
                assertEquals(View.VISIBLE, panel.getVisibility());
            });

            scenario.onActivity(OctavoActivity::closeSearchPanelForTesting);
            awaitActivity(
                scenario,
                activity -> activity.searchPanelForTesting() == null
                    && activity.readerSearchForTesting().hasFocus(),
                "Find in book did not restore focus to its reader control");
        }
    }

    @Test
    public void sheetRetriesSubmissionAcrossBusyReaderPresentation() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertTrue(activity.setChromeVisibleForTesting(true));
                activity.openSearchPanelForTesting();
            });
            awaitActivity(
                scenario,
                activity -> activity.searchPanelForTesting() != null,
                "Find in book did not open for busy submission");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(1));
                assertEquals(
                    OctavoNative.NAVIGATION_ACCEPTED,
                    view.requestProgressDisplay(OctavoProgressDisplay.PAGE));
                OctavoSearchPanel panel = activity.searchPanelForTesting();
                assertNotNull(panel);
                panel.queryInputForTesting().setText("paragraph 250");
                ((Button)activity.findViewById(
                    R.id.octavo_search_submit)).performClick();
            });

            OctavoSearch results = awaitSearch(
                scenario,
                search -> !search.isPending()
                    && search.query().equals("paragraph 250")
                    && search.totalCount() == 4,
                "Busy reader presentation dropped the submitted query");
            assertEquals(4, results.rowCount());
            scenario.onActivity(activity -> assertEquals(
                "paragraph 250",
                activity.searchPanelForTesting().queryInputForTesting()
                    .getText().toString()));
        }
    }

    @Test
    public void failedQueryRestoresLastPresentedResultsAndRecreationClearsThem() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertEquals(
                OctavoNative.NAVIGATION_ACCEPTED,
                surface(activity).commitSearch("paragraph 250")));
            awaitSearch(
                scenario,
                search -> !search.isPending()
                    && search.query().equals("paragraph 250"),
                "Baseline search was not presented");

            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.forcePresentFailuresForTesting(5));
                assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                             view.commitSearch("chapter"));
            });
            awaitActivity(
                scenario,
                activity -> !surface(activity)
                    .searchMutationPendingForTesting()
                    && !surface(activity).hasNavigationPending(),
                "Failed query did not leave its presentation transaction");
            OctavoSearch restored = awaitSearch(
                scenario,
                search -> !search.isPending()
                    && search.query().equals("paragraph 250"),
                "Failed query did not restore the last presented results");
            assertEquals(4, restored.totalCount());

            StringBuilder overlong = new StringBuilder();
            for (int index = 0;
                 index <= OctavoSearch.MAX_QUERY_UTF8_BYTES;
                 ++index) {
                overlong.append('x');
            }
            scenario.onActivity(activity -> assertEquals(
                OctavoNative.NAVIGATION_INVALID,
                surface(activity).commitSearch(overlong.toString())));
            assertEquals("paragraph 250", searchSnapshot(scenario).query());

            scenario.recreate();
            awaitReaderReady(scenario);
            OctavoSearch recreated = searchSnapshot(scenario);
            assertNotNull(recreated);
            assertTrue(recreated.query().isEmpty());
            assertEquals(0, recreated.rowCount());
        }
    }

    @Test
    public void packetValidationRejectsMalformedRows() {
        long[] valid = new long[OctavoSearch.HEADER_COUNT
                                + OctavoSearch.ROW_STRIDE];
        valid[0] = OctavoSearch.VERSION;
        valid[1] = OctavoSearch.HEADER_COUNT;
        valid[2] = OctavoSearch.ROW_STRIDE;
        valid[3] = 1;
        valid[4] = 1;
        valid[5] = 1;
        valid[6] = 0;
        valid[7] = 1;
        valid[8] = 5;
        valid[OctavoSearch.HEADER_COUNT] = 0;
        valid[OctavoSearch.HEADER_COUNT + 1] = 3;
        valid[OctavoSearch.HEADER_COUNT + 2] = 8;
        valid[OctavoSearch.HEADER_COUNT + 3] = 2;
        valid[OctavoSearch.HEADER_COUNT + 4] = 5;
        OctavoSearch parsed = OctavoSearch.fromNativePacket(
            valid, "alpha", new String[] {"Chapter One"},
            new String[] {"--alpha--"});
        assertNotNull(parsed);
        long[] malformed = valid.clone();
        malformed[OctavoSearch.HEADER_COUNT + 4] = 100;
        assertTrue(OctavoSearch.fromNativePacket(
            malformed, "alpha", new String[] {"Chapter One"},
            new String[] {"--alpha--"}) == null);
        malformed = valid.clone();
        malformed[3] |= 1L << 12;
        assertTrue(OctavoSearch.fromNativePacket(
            malformed, "alpha", new String[] {"Chapter One"},
            new String[] {"--alpha--"}) == null);
        malformed = valid.clone();
        malformed[8] = 4;
        assertTrue(OctavoSearch.fromNativePacket(
            malformed, "alpha", new String[] {"Chapter One"},
            new String[] {"--alpha--"}) == null);
        malformed = valid.clone();
        malformed[9] = 1;
        assertTrue(OctavoSearch.fromNativePacket(
            malformed, "alpha", new String[] {"Chapter One"},
            new String[] {"--alpha--"}) == null);
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
                    && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && state[OctavoSurfaceView
                        .STATE_READER_FRAME_READY] == 1
                    && state[OctavoSurfaceView
                        .STATE_HOST_PRESENTATION_PENDING] == 0
                    && navigation != null
                    && navigation[OctavoSurfaceView
                        .NAVIGATION_STATE_PENDING] == 0;
            },
            "8vo did not present a settled reader frame");
    }

    private static OctavoSearch awaitSearch(
        ActivityScenario<OctavoActivity> scenario,
        java.util.function.Predicate<OctavoSearch> condition,
        String failureMessage) {
        AtomicReference<OctavoSearch> match = new AtomicReference<>();
        awaitActivity(
            scenario,
            activity -> {
                OctavoSearch search = surface(activity).searchSnapshot();
                if (search != null && condition.test(search)) {
                    match.set(search);
                    return true;
                }
                return false;
            },
            failureMessage);
        return match.get();
    }

    private static OctavoSearch searchSnapshot(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSearch> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).searchSnapshot()));
        return result.get();
    }

    private static long[] awaitNavigation(
        ActivityScenario<OctavoActivity> scenario,
        java.util.function.Predicate<long[]> condition,
        String failureMessage) {
        AtomicReference<long[]> result = new AtomicReference<>();
        awaitActivity(
            scenario,
            activity -> {
                long[] state = surface(activity)
                    .navigationStateForTesting();
                if (state != null && condition.test(state)) {
                    result.set(state.clone());
                    return true;
                }
                return false;
            },
            failureMessage);
        return result.get();
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

    private static int dp(Context context, int value) {
        return Math.round(value
            * context.getResources().getDisplayMetrics().density);
    }
}
