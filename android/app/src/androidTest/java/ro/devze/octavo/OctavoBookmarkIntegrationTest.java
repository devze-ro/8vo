package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoBookmarkIntegrationTest {
    private static final long WAIT_MILLIS = 12_000;
    private static final String ALPHA_ASSET =
        "port6/octavo_port6_alpha.epub";

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    @Before
    public void clearDurableState() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
    }

    @Test
    public void toggleListRollbackRemovalAndRecreationAreAuthoritative() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);

            AtomicReference<String> bookKey = new AtomicReference<>();
            AtomicReference<long[]> anchor = new AtomicReference<>();
            scenario.onActivity(activity -> {
                bookKey.set(activity.activeBookKeyForTesting());
                anchor.set(surface(activity).presentedAnchorForAnnotations());
                assertNotNull(bookKey.get());
                assertValidPosition(anchor.get());
                activity.toggleCurrentBookmarkForTesting();
                assertTrue(activity.annotationStoreForTesting()
                    .isBookmarked(
                        bookKey.get(), anchor.get()[1], anchor.get()[2]));
                assertEquals("★",
                             activity.readerBookmarkToggleForTesting()
                                 .getText().toString());
                assertEquals("Remove bookmark",
                             activity.readerBookmarkToggleForTesting()
                                 .getContentDescription().toString());
                activity.openBookmarksPanelForTesting();
            });

            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .bookmarksForTesting().size() == 1,
                "The durable bookmark did not appear in its workspace");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertNotNull(panel);
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                             panel.statusForTesting()
                                 .getAccessibilityLiveRegion());
                assertEquals(1, panel.bookmarksForTesting().size());
                assertNotNull(panel.firstGoToForTesting());
                assertNotNull(panel.firstRemoveForTesting());
                assertTrue(panel.firstGoToForTesting()
                    .getContentDescription().toString()
                    .startsWith("Go to Bookmark at"));
                assertTrue(panel.firstRemoveForTesting()
                    .getContentDescription().toString()
                    .startsWith("Remove Bookmark at"));

                activity.annotationStoreForTesting()
                    .failNextPublishForTesting();
                activity.toggleCurrentBookmarkForTesting();
                assertTrue(activity.annotationStoreForTesting()
                    .isBookmarked(
                        bookKey.get(), anchor.get()[1], anchor.get()[2]));
                assertEquals("★",
                             activity.readerBookmarkToggleForTesting()
                                 .getText().toString());
                assertTrue(activity.lastOpenErrorForTesting()
                    .contains("previous state was preserved"));
                activity.closeBookmarksPanelForTesting();
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals(bookKey.get(),
                             activity.activeBookKeyForTesting());
                assertTrue(activity.annotationStoreForTesting()
                    .isBookmarked(
                        bookKey.get(), anchor.get()[1], anchor.get()[2]));
                assertEquals("★",
                             activity.readerBookmarkToggleForTesting()
                                 .getText().toString());
                activity.openBookmarksPanelForTesting();
            });
            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .firstRemoveForTesting() != null,
                "The restarted bookmark workspace did not become ready");
            scenario.onActivity(activity -> activity
                .bookmarksPanelForTesting().firstRemoveForTesting()
                .performClick());
            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .bookmarksForTesting().isEmpty(),
                "Removing a durable bookmark did not update the workspace");
            scenario.onActivity(activity -> {
                assertFalse(activity.annotationStoreForTesting()
                    .isBookmarked(
                        bookKey.get(), anchor.get()[1], anchor.get()[2]));
                assertEquals("☆",
                             activity.readerBookmarkToggleForTesting()
                                 .getText().toString());
            });
        }
    }

    @Test
    public void failedGoToKeepsSheetAndPositionThenRetryPresentsExactAnchor() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            awaitLocationReady(scenario);
            AtomicReference<long[]> saved = new AtomicReference<>();
            scenario.onActivity(activity -> {
                saved.set(surface(activity).presentedAnchorForAnnotations());
                assertValidPosition(saved.get());
                activity.toggleCurrentBookmarkForTesting();
                assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                             surface(activity)
                                 .requestPercentageNavigation(100));
            });
            awaitReaderReady(scenario);
            long[] jumped = readingPosition(scenario);
            assertFalse(saved.get()[1] == jumped[1]
                        && saved.get()[2] == jumped[2]);

            scenario.onActivity(activity -> {
                activity.openBookmarksPanelForTesting();
                assertTrue(surface(activity)
                    .forcePrePresentFailuresForTesting(5));
            });
            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .firstGoToForTesting() != null,
                "The bookmark navigation action was unavailable");
            scenario.onActivity(activity -> activity
                .bookmarksPanelForTesting().firstGoToForTesting()
                .performClick());
            awaitActivity(
                scenario,
                activity -> {
                    OctavoBookmarksPanel panel =
                        activity.bookmarksPanelForTesting();
                    long[] navigation =
                        surface(activity).navigationStateForTesting();
                    return panel != null && navigation != null
                        && navigation[OctavoSurfaceView
                            .NAVIGATION_STATE_PENDING] == 0
                        && panel.statusForTesting().getText().toString()
                            .toLowerCase(Locale.ROOT)
                            .contains("could not");
                },
                "A failed bookmark jump did not roll back visibly");
            assertArrayEquals(jumped, readingPosition(scenario));

            scenario.onActivity(activity -> activity
                .bookmarksPanelForTesting().firstGoToForTesting()
                .performClick());
            awaitActivity(
                scenario,
                activity -> activity.bookmarksPanelForTesting() == null
                    && !surface(activity).hasNavigationPending(),
                "A retried bookmark jump was not presented");
            assertArrayEquals(saved.get(), readingPosition(scenario));
        }
    }

    @Test
    public void managedBookRemovalAndDigestReimportKeepAnnotations() {
        Context context = ApplicationProvider.getApplicationContext();
        File source = stageAsset(context, ALPHA_ASSET,
                                 "port11-reimport-alpha.epub");
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertTrue(activity.openDocumentForTesting(
                    Uri.fromFile(source)));
            });
            awaitReaderReady(scenario);
            AtomicReference<String> digest = new AtomicReference<>();
            AtomicReference<long[]> anchor = new AtomicReference<>();
            scenario.onActivity(activity -> {
                digest.set(activity.activeBookKeyForTesting());
                anchor.set(surface(activity).presentedAnchorForAnnotations());
                activity.toggleCurrentBookmarkForTesting();
                assertTrue(activity.removeBookForTesting(digest.get()));
                assertTrue(activity.libraryVisibleForTesting());
                assertEquals(1, activity.annotationStoreForTesting()
                    .bookmarks(digest.get()).size());
                assertTrue(activity.openDocumentForTesting(
                    Uri.fromFile(source)));
            });

            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals(digest.get(),
                             activity.activeBookKeyForTesting());
                List<OctavoAnnotationStore.Bookmark> bookmarks =
                    activity.annotationStoreForTesting()
                        .bookmarks(digest.get());
                assertEquals(1, bookmarks.size());
                assertEquals(anchor.get()[1], bookmarks.get(0).spineIndex);
                assertEquals(anchor.get()[2], bookmarks.get(0).byteOffset);
            });
        }
        assertTrue(source.delete() || !source.exists());
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
                    && state[OctavoSurfaceView
                        .STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0
                    && state[OctavoSurfaceView
                        .STATE_REFLOW_PRESENTATION_PENDING] == 0
                    && state[OctavoSurfaceView
                        .STATE_HOST_PRESENTATION_PENDING] == 0
                    && navigation != null
                    && navigation.length
                       == OctavoSurfaceView.NAVIGATION_STATE_FIELD_COUNT
                    && navigation[OctavoSurfaceView
                        .NAVIGATION_STATE_PENDING] == 0;
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
            "8vo did not complete location warming");
    }

    private static long[] readingPosition(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).readingPositionForTesting()));
        assertValidPosition(result.get());
        return result.get();
    }

    private static void assertValidPosition(long[] position) {
        assertNotNull(position);
        assertEquals(3, position.length);
        assertEquals(1, position[0]);
        assertTrue(position[1] >= 0);
        assertTrue(position[2] >= 0);
    }

    private static File stageAsset(Context context,
                                   String asset,
                                   String name) {
        File target = new File(context.getCacheDir(), name);
        try (InputStream input = context.getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.flush();
            output.getFD().sync();
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
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
}
