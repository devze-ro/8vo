package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoNoteIntegrationTest {
    private static final long WAIT_MILLIS = 12_000;
    private static final String ACTOR_A =
        "11111111111111111111111111111111";
    private static final String ACTOR_B =
        "22222222222222222222222222222222";
    private static final String NOTE_ID =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

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
        OctavoNoteDraftStore.clearForTesting(context);
    }

    @Test
    public void addNoteDraftRecoversRollsBackEditsAndRemoves()
        throws InterruptedException {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            Bitmap initialFrame = copyFrame(surface(scenario));
            int accent = OctavoDesignTokens.forAppearance(
                OctavoAppearance.defaults()).accent;
            int initialAccentPixels = countPixelsNear(
                initialFrame, accent, 6);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            awaitActivity(scenario,
                activity -> selection(surface(activity)).active,
                "Selection was not presented for a note");
            AtomicReference<long[]> selectedRange = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                selectedRange.set(view.selectedRangeForTesting());
                assertNotNull(selectedRange.get());
                AccessibilityNodeProvider provider =
                    view.getAccessibilityNodeProvider();
                assertNotNull(provider);
                AccessibilityNodeInfo page = provider.createAccessibilityNodeInfo(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT);
                assertTrue(hasAction(page, R.id.octavo_action_add_note));
                page.recycle();
                assertTrue(provider.performAction(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                    R.id.octavo_action_add_note, null));
            });
            awaitActivity(scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting().hasActiveDraft(),
                "The note editor did not open");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertEquals("Note text",
                    panel.noteInputForTesting()
                        .getContentDescription().toString());
                assertEquals("Save note",
                    panel.noteSaveForTesting()
                        .getContentDescription().toString());
                assertEquals("Cancel note editing",
                    panel.noteCancelForTesting()
                        .getContentDescription().toString());
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                    panel.noteEditorStatusForTesting()
                        .getAccessibilityLiveRegion());
                assertTrue(panel.noteInputForTesting().getPaddingLeft() > 0);
                assertTrue(panel.noteInputForTesting().getPaddingTop() > 0);
                assertEquals(panel.noteInputForTesting().getPaddingLeft(),
                             panel.noteInputForTesting().getPaddingRight());
                assertEquals(panel.noteInputForTesting().getPaddingTop(),
                             panel.noteInputForTesting().getPaddingBottom());
                panel.noteInputForTesting().setText("Recovered draft β");
                assertNotNull(activity.noteDraftStoreForTesting().current());
                assertEquals("Recovered draft β",
                    activity.noteDraftStoreForTesting().current().body);
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(OctavoActivity::openBookmarksPanelForTesting);
            awaitActivity(scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting().hasActiveDraft(),
                "The durable note draft was not recovered");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertEquals("Recovered draft β",
                             panel.noteInputForTesting().getText().toString());
                assertTrue(panel.noteEditorStatusForTesting().getText()
                    .toString().contains("Recovered"));
                activity.annotationStoreForTesting()
                    .failNextPublishForTesting();
                panel.noteSaveForTesting().performClick();
                assertTrue(activity.annotationStoreForTesting().notes(
                    activity.activeBookKeyForTesting()).isEmpty());
                assertTrue(panel.hasActiveDraft());
                assertEquals("Recovered draft β",
                             panel.noteInputForTesting().getText().toString());
                assertTrue(panel.statusForTesting().getText().toString()
                    .contains("previous state was preserved"));
                panel.noteSaveForTesting().performClick();
            });
            awaitActivity(scenario,
                activity -> activity.annotationStoreForTesting().notes(
                        activity.activeBookKeyForTesting()).size() == 1
                    && noteMarkersPresented(surface(activity), 1)
                    && !selection(surface(activity)).active
                    && !activity.bookmarksPanelForTesting().hasActiveDraft()
                    && activity.noteDraftStoreForTesting().current() == null,
                "The retried note was not durably saved");
            scenario.onActivity(activity -> {
                OctavoAnnotationStore.Note note =
                    activity.annotationStoreForTesting().notes(
                        activity.activeBookKeyForTesting()).get(0);
                assertEquals(selectedRange.get()[2], note.spineIndex);
                assertEquals(selectedRange.get()[3], note.byteStart);
                assertEquals(selectedRange.get()[4], note.byteEnd);
                assertEquals("Recovered draft β", note.preferredBody());
                assertNoteMarkerSnapshot(
                    surface(activity).noteMarkerSnapshotForTesting(),
                    selectedRange.get());
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertNotNull(panel.firstNoteGoToForTesting());
                assertNotNull(panel.firstNoteEditForTesting());
                assertNotNull(panel.firstNoteRemoveForTesting());
            });

            scenario.onActivity(OctavoActivity::closeBookmarksPanelForTesting);
            Bitmap noteFrame = copyFrame(surface(scenario));
            assertTrue("The note marker accent pixels were absent",
                countPixelsNear(noteFrame, accent, 6)
                    > initialAccentPixels + 20);
            int[] markerPixel = findAddedPixel(
                initialFrame, noteFrame, accent, 6);
            assertNotNull("The note marker could not be located", markerPixel);
            initialFrame.recycle();
            noteFrame.recycle();
            scenario.onActivity(activity -> assertTrue(
                tap(surface(activity), markerPixel[0], markerPixel[1])));
            awaitActivity(scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting().hasActiveDraft(),
                "Tapping the note marker did not open its editor");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertEquals("Recovered draft β",
                             panel.noteInputForTesting().getText().toString());
                assertEquals(
                    activity.annotationStoreForTesting().notes(
                        activity.activeBookKeyForTesting()).get(0).recordId,
                    activity.noteDraftStoreForTesting().current().recordId);
                activity.noteDraftStoreForTesting()
                    .failNextPublishForTesting();
                panel.noteInputForTesting().setText("Edited unsaved body");
                assertEquals(View.VISIBLE,
                             panel.noteRetryForTesting().getVisibility());
                assertEquals("Edited unsaved body",
                             panel.noteInputForTesting().getText().toString());
                panel.noteRetryForTesting().performClick();
                assertEquals("Edited unsaved body",
                    activity.noteDraftStoreForTesting().current().body);
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertNoteMarkerSnapshot(
                    surface(activity).noteMarkerSnapshotForTesting(),
                    selectedRange.get());
                activity.openBookmarksPanelForTesting();
            });
            awaitActivity(scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting().hasActiveDraft(),
                "The edited note draft was not recovered");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                assertEquals("Edited unsaved body",
                             panel.noteInputForTesting().getText().toString());
                panel.noteSaveForTesting().performClick();
            });
            awaitActivity(scenario,
                activity -> !activity.bookmarksPanelForTesting().hasActiveDraft()
                    && activity.annotationStoreForTesting().notes(
                        activity.activeBookKeyForTesting()).get(0)
                        .preferredBody().equals("Edited unsaved body"),
                "The recovered note edit was not saved");
            scenario.onActivity(activity -> activity.bookmarksPanelForTesting()
                .firstNoteRemoveForTesting().performClick());
            awaitActivity(scenario,
                activity -> activity.annotationStoreForTesting().notes(
                        activity.activeBookKeyForTesting()).isEmpty()
                    && noteMarkersPresented(surface(activity), 0),
                "The note was not removed");
            scenario.onActivity(OctavoActivity::closeBookmarksPanelForTesting);
            Bitmap removedFrame = copyFrame(surface(scenario));
            assertTrue("Removing the note did not remove its marker pixels",
                countPixelsNear(removedFrame, accent, 6)
                    <= initialAccentPixels + 20);
            removedFrame.recycle();
        }
    }

    @Test
    public void concurrentBodiesAreVisibleAndExplicitResolutionObservesAllHeads() {
        Context context = ApplicationProvider.getApplicationContext();
        File leftRoot = new File(context.getCacheDir(),
            "note-conflict-left-" + System.nanoTime());
        File rightRoot = new File(context.getCacheDir(),
            "note-conflict-right-" + System.nanoTime());
        AtomicReference<String> resolvedBody = new AtomicReference<>();
        assertTrue(leftRoot.mkdirs());
        assertTrue(rightRoot.mkdirs());
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                String digest = activity.activeBookKeyForTesting();
                OctavoAnnotationStore left =
                    new OctavoAnnotationStore(leftRoot, ACTOR_A);
                OctavoAnnotationStore right =
                    new OctavoAnnotationStore(rightRoot, ACTOR_B);
                left.load();
                right.load();
                assertTrue(left.putNoteForTesting(
                    NOTE_ID, digest, 0, 0, "Alpha version").succeeded());
                assertTrue(right.putNoteForTesting(
                    NOTE_ID, digest, 0, 0, "Beta version").succeeded());
                assertTrue(activity.annotationStoreForTesting()
                    .mergePortableState(left.exportPortableState()).succeeded());
                assertTrue(activity.annotationStoreForTesting()
                    .mergePortableState(right.exportPortableState()).succeeded());
                activity.openBookmarksPanelForTesting();
            });
            awaitActivity(scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting()
                        .firstNoteResolveForTesting(1) != null,
                "Concurrent note bodies were not both visible");
            scenario.onActivity(activity -> {
                OctavoBookmarksPanel panel =
                    activity.bookmarksPanelForTesting();
                List<OctavoAnnotationStore.Note> notes =
                    panel.notesForTesting();
                assertEquals(1, notes.size());
                assertTrue(notes.get(0).conflicted);
                assertEquals(2, notes.get(0).versions.size());
                String selectedBody = notes.get(0).versions.get(1).body;
                resolvedBody.set(selectedBody);
                panel.firstNoteResolveForTesting(1).performClick();
                assertTrue(panel.hasActiveDraft());
                assertEquals(selectedBody,
                             panel.noteInputForTesting().getText().toString());
                panel.noteSaveForTesting().performClick();
            });
            awaitActivity(scenario,
                activity -> {
                    OctavoAnnotationStore.Note note =
                        activity.annotationStoreForTesting().notes(
                            activity.activeBookKeyForTesting()).get(0);
                    return !note.conflicted && note.versions.size() == 1
                        && note.preferredBody().equals(resolvedBody.get());
                },
                "The explicit conflict resolution did not observe all heads");
        } finally {
            assertTrue(deleteTree(leftRoot));
            assertTrue(deleteTree(rightRoot));
        }
    }

    @Test
    public void markerPresentationFailureIsVisibleAndRecoversOnRestart() {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            openFixture(scenario);
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> assertTrue(
                surface(activity).selectTextForAccessibility()));
            awaitActivity(scenario,
                activity -> selection(surface(activity)).active,
                "Selection was not presented for the failure probe");
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                AccessibilityNodeProvider provider =
                    view.getAccessibilityNodeProvider();
                assertNotNull(provider);
                assertTrue(provider.performAction(
                    OctavoReaderAccessibilityProvider.VIRTUAL_PAGE_CONTENT,
                    R.id.octavo_action_add_note,
                    null));
            });
            awaitActivity(scenario,
                activity -> activity.bookmarksPanelForTesting() != null
                    && activity.bookmarksPanelForTesting().hasActiveDraft(),
                "The note editor did not open for the failure probe");
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                activity.bookmarksPanelForTesting().noteInputForTesting()
                    .setText("Presentation recovery note");
                assertTrue(view.forcePresentFailuresForTesting(8));
                activity.bookmarksPanelForTesting().noteSaveForTesting()
                    .performClick();
            });
            awaitActivity(scenario,
                activity -> activity.annotationStoreForTesting().notes(
                        activity.activeBookKeyForTesting()).size() == 1
                    && surface(activity)
                        .presentationFailureNotifiedForTesting(),
                "The durable note did not expose marker presentation failure");
            scenario.onActivity(activity -> {
                long[] snapshot =
                    surface(activity).noteMarkerSnapshotForTesting();
                assertNotNull(snapshot);
                assertEquals(1, snapshot[2]);
                assertTrue(snapshot[3] > snapshot[4]);
                assertEquals(1, snapshot[5]);
                OctavoSelection current = selection(surface(activity));
                assertTrue(current.active);
                assertFalse(current.pending);
                assertTrue(activity.lastOpenErrorForTesting()
                    .contains("Note saved"));
            });

            scenario.recreate();
            awaitReaderReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals(1, activity.annotationStoreForTesting().notes(
                    activity.activeBookKeyForTesting()).size());
                assertTrue(noteMarkersPresented(surface(activity), 1));
                OctavoSelection current = selection(surface(activity));
                assertFalse(current.active);
                assertFalse(current.pending);
            });
        }
    }

    private static boolean hasAction(AccessibilityNodeInfo node, int id) {
        for (AccessibilityNodeInfo.AccessibilityAction action
                : node.getActionList()) {
            if (action.getId() == id) {
                return true;
            }
        }
        return false;
    }

    private static void openFixture(
        ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> assertTrue(
            activity.openFixtureForTesting()));
    }

    private static OctavoSurfaceView surface(OctavoActivity activity) {
        OctavoSurfaceView view = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        assertNotNull(view);
        return view;
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(surface(activity)));
        assertNotNull(result.get());
        return result.get();
    }

    private static boolean noteMarkersPresented(OctavoSurfaceView view,
                                                int count) {
        long[] snapshot = view.noteMarkerSnapshotForTesting();
        return snapshot != null && snapshot.length >= 7
            && snapshot[2] == count && snapshot[3] == snapshot[4]
            && snapshot[5] == 0;
    }

    private static void assertNoteMarkerSnapshot(long[] snapshot,
                                                 long[] range) {
        assertNotNull(snapshot);
        assertNotNull(range);
        assertEquals(10, snapshot.length);
        assertEquals(1, snapshot[0]);
        assertEquals(1, snapshot[2]);
        assertEquals(snapshot[3], snapshot[4]);
        assertEquals(0, snapshot[5]);
        assertEquals(3, snapshot[6]);
        assertEquals(range[2], snapshot[7]);
        assertEquals(range[3], snapshot[8]);
        assertEquals(range[4], snapshot[9]);
    }

    private static Bitmap copyFrame(OctavoSurfaceView surface)
        throws InterruptedException {
        int width = surface.getWidth();
        int height = surface.getHeight();
        assertTrue(width > 0);
        assertTrue(height > 0);
        Bitmap bitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888);
        HandlerThread copyThread = new HandlerThread(
            "8vo-note-marker-pixel-copy");
        copyThread.start();
        try {
            for (int attempt = 0; attempt < 10; ++attempt) {
                CountDownLatch copied = new CountDownLatch(1);
                AtomicInteger result = new AtomicInteger(
                    PixelCopy.ERROR_UNKNOWN);
                PixelCopy.request(surface, bitmap, copyResult -> {
                    result.set(copyResult);
                    copied.countDown();
                }, new Handler(copyThread.getLooper()));
                assertTrue(copied.await(5, TimeUnit.SECONDS));
                if (result.get() == PixelCopy.SUCCESS) {
                    return bitmap;
                }
                SystemClock.sleep(100);
            }
            fail("PixelCopy could not read the note-marker frame");
            bitmap.recycle();
            return null;
        } finally {
            copyThread.quitSafely();
        }
    }

    private static int countPixelsNear(Bitmap bitmap,
                                       int expected,
                                       int tolerance) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); ++y) {
            for (int x = 0; x < bitmap.getWidth(); ++x) {
                int pixel = bitmap.getPixel(x, y);
                int distance = Math.abs(Color.red(pixel) - Color.red(expected))
                    + Math.abs(Color.green(pixel) - Color.green(expected))
                    + Math.abs(Color.blue(pixel) - Color.blue(expected));
                if (distance <= tolerance) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private static int[] findAddedPixel(Bitmap before,
                                        Bitmap after,
                                        int expected,
                                        int tolerance) {
        assertEquals(before.getWidth(), after.getWidth());
        assertEquals(before.getHeight(), after.getHeight());
        for (int y = 0; y < after.getHeight(); ++y) {
            for (int x = 0; x < after.getWidth(); ++x) {
                if (pixelNear(after.getPixel(x, y), expected, tolerance)
                    && !pixelNear(
                        before.getPixel(x, y), expected, tolerance)) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    private static boolean pixelNear(int pixel,
                                     int expected,
                                     int tolerance) {
        int distance = Math.abs(Color.red(pixel) - Color.red(expected))
            + Math.abs(Color.green(pixel) - Color.green(expected))
            + Math.abs(Color.blue(pixel) - Color.blue(expected));
        return distance <= tolerance;
    }

    private static boolean tap(OctavoSurfaceView view, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(
            now, now + 25, MotionEvent.ACTION_UP, x, y, 0);
        try {
            return view.dispatchTouchEvent(down)
                && view.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static OctavoSelection selection(OctavoSurfaceView view) {
        OctavoSelection result =
            OctavoSelection.fromNative(view.selectionPacketForTesting());
        assertNotNull(result);
        return result;
    }

    private static void awaitReaderReady(
        ActivityScenario<OctavoActivity> scenario) {
        awaitActivity(scenario, activity -> {
            OctavoSurfaceView view = (OctavoSurfaceView)
                activity.findViewById(R.id.octavo_surface);
            if (view == null) {
                return false;
            }
            long[] state = view.nativeStateForTesting();
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
                    .STATE_HOST_PRESENTATION_PENDING] == 0;
        }, "8vo did not present a settled reader frame");
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
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(25);
        }
        fail(failureMessage);
    }

    private static boolean deleteTree(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return false;
            }
            for (File child : children) {
                if (!deleteTree(child)) {
                    return false;
                }
            }
        }
        return file.delete() || !file.exists();
    }
}
