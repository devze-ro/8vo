package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoDocumentPersistenceTest {
    private static final long SELECTED_BYTE_COUNT = 41514L;
    private static final String SELECTED_SHA256 =
        "e47ae862774f391578021438faf503639971745b0172cfdbb960de02c77d3643";

    @Before
    public void clearPort5Session() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoDocumentStore.clearSessionForTesting(context);
    }

    private interface StateCondition {
        boolean matches(long[] state);
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface)));
        assertNotNull(result.get());
        return result.get();
    }

    private static long[] state(ActivityScenario<OctavoActivity> scenario) {
        long[] result = surface(scenario).nativeStateForTesting();
        assertNotNull(result);
        assertEquals(OctavoSurfaceView.STATE_FIELD_COUNT, result.length);
        return result;
    }

    private static long[] awaitState(
        ActivityScenario<OctavoActivity> scenario,
        StateCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 160; ++attempt) {
            long[] snapshot = state(scenario);
            if (condition.matches(snapshot)) {
                return snapshot;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] awaitPresented(
        ActivityScenario<OctavoActivity> scenario) {
        return awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_RESUMED] == 1
                && snapshot[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                && snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0,
            "8vo did not present the Port 5 document");
    }

    private static String title(ActivityScenario<OctavoActivity> scenario) {
        String result = surface(scenario).documentTitleForTesting();
        assertNotNull(result);
        return result;
    }

    private static String text(ActivityScenario<OctavoActivity> scenario) {
        String result = surface(scenario).visibleTextForTesting();
        assertNotNull(result);
        return result;
    }

    private static String documentPath(
        ActivityScenario<OctavoActivity> scenario) {
        String result = surface(scenario).documentPathForTesting();
        assertNotNull(result);
        return result;
    }

    private static void tapNext(ActivityScenario<OctavoActivity> scenario) {
        scenario.onActivity(activity -> {
            OctavoSurfaceView view =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            long now = SystemClock.uptimeMillis();
            float x = view.getWidth() * 5.0f / 6.0f;
            float y = view.getHeight() / 2.0f;
            MotionEvent down = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(
                now, now + 20, MotionEvent.ACTION_UP, x, y, 0);
            try {
                view.dispatchTouchEvent(down);
                view.dispatchTouchEvent(up);
            } finally {
                down.recycle();
                up.recycle();
            }
        });
    }

    private static File stageSelectedAsset(Context context)
        throws IOException {
        File source = new File(context.getCacheDir(),
                               "octavo_port5_selected_source.epub");
        try (InputStream input = context.getAssets().open(
                 "port5/octavo_port5_selected.epub");
             FileOutputStream output = new FileOutputStream(source, false)) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.getFD().sync();
        }
        return source;
    }

    private static String sha256(File file)
        throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT,
                                        "%02x",
                                        value & 0xFF));
        }
        return result.toString();
    }

    @Test
    public void pickerContractImportsDistinctEpubAndRestoresPresentedLocation()
        throws IOException, NoSuchAlgorithmException {
        Context context = ApplicationProvider.getApplicationContext();
        File selectedSource = stageSelectedAsset(context);
        assertEquals(SELECTED_BYTE_COUNT, selectedSource.length());
        assertEquals(SELECTED_SHA256, sha256(selectedSource));

        long selectedSpine;
        long selectedByte;
        String importedPath;
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            long[] initial = awaitPresented(scenario);
            assertEquals("Octavo Android Port 5", title(scenario));
            assertEquals(0,
                         initial[OctavoSurfaceView.STATE_RESTORE_REQUESTED]);

            AtomicReference<Intent> picker = new AtomicReference<>();
            AtomicBoolean opened = new AtomicBoolean();
            AtomicLong savesBefore = new AtomicLong();
            scenario.onActivity(activity -> {
                picker.set(activity.openDocumentIntentForTesting());
                savesBefore.set(activity.documentStoreForTesting()
                                    .sessionSaveSuccessCountForTesting());
                opened.set(activity.openDocumentForTesting(
                    Uri.fromFile(selectedSource)));
            });
            assertNotNull(picker.get());
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,
                         picker.get().getAction());
            assertTrue(picker.get().hasCategory(Intent.CATEGORY_OPENABLE));
            assertEquals("application/epub+zip", picker.get().getType());
            assertTrue((picker.get().getFlags()
                        & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            assertTrue(opened.get());

            long[] selected = awaitState(
                scenario,
                snapshot ->
                    snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                    && snapshot[OctavoSurfaceView.STATE_DOCUMENT_OPEN] == 1
                    && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                    && snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] == 1
                    && "Selected Port 5 Book".equals(title(scenario)),
                "8vo did not publish the selected EPUB");
            assertEquals(1,
                         selected[
                             OctavoSurfaceView.STATE_DOCUMENT_OPEN_SUCCESS_COUNT]);
            assertEquals(0,
                         selected[
                             OctavoSurfaceView.STATE_DOCUMENT_OPEN_FAILURE_COUNT]);
            assertEquals(0,
                         selected[OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertTrue(surface(scenario).importedDocumentForTesting());
            assertEquals(SELECTED_SHA256,
                         surface(scenario).documentKeyForTesting());
            assertTrue(text(scenario).contains(
                "app-private imported EPUB"));
            assertFalse(text(scenario).contains(
                "deterministic Port 5 content"));

            importedPath = documentPath(scenario);
            File imported = new File(importedPath);
            assertTrue(imported.isFile());
            assertEquals(SELECTED_BYTE_COUNT, imported.length());
            assertEquals(SELECTED_SHA256 + ".epub", imported.getName());
            assertEquals("documents", imported.getParentFile().getName());
            assertEquals(SELECTED_SHA256, sha256(imported));

            tapNext(scenario);
            long[] pageTwo = awaitState(
                scenario,
                snapshot ->
                    snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] == 2
                    && snapshot[
                        OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                        == 0,
                "8vo did not present selected-book page 2");
            selectedSpine =
                pageTwo[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX];
            selectedByte =
                pageTwo[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET];
            assertTrue(selectedByte > 0);

            scenario.onActivity(activity -> {
                ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                    .flushPersistenceForTesting();
                OctavoDocumentStore store =
                    activity.documentStoreForTesting();
                assertEquals(1, store.importSuccessCountForTesting());
                assertEquals(0, store.importFailureCountForTesting());
                assertTrue(store.sessionSaveSuccessCountForTesting()
                           > savesBefore.get());
                assertEquals(0,
                             store.sessionSaveFailureCountForTesting());
                assertTrue(store.sessionFileForTesting().isFile());
            });
        }

        try (ActivityScenario<OctavoActivity> relaunched =
                 ActivityScenario.launch(OctavoActivity.class)) {
            long[] restored = awaitPresented(relaunched);
            assertEquals("Selected Port 5 Book", title(relaunched));
            assertEquals(importedPath, documentPath(relaunched));
            assertEquals(1,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertEquals(1,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_ATTEMPTED]);
            assertEquals(1,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(0,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_FAILURE_COUNT]);
            assertTrue(restored[
                           OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH] != 0);
            assertEquals(selectedSpine,
                         restored[
                             OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
            assertTrue(restored[
                           OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]
                       <= selectedByte);
            assertTrue(text(relaunched).contains(
                "app-private imported EPUB"));
            relaunched.onActivity(activity -> {
                OctavoDocumentStore store =
                    activity.documentStoreForTesting();
                assertEquals(1, store.sessionLoadSuccessCountForTesting());
                assertEquals(0, store.sessionLoadFailureCountForTesting());
                assertEquals(0, store.sessionSaveFailureCountForTesting());
            });
        }
    }

    @Test
    public void invalidSelectedFileLeavesPresentedDocumentUntouched()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File invalid = new File(context.getCacheDir(),
                                "octavo_port5_invalid.epub");
        try (FileOutputStream output =
                 new FileOutputStream(invalid, false)) {
            output.write(new byte[] {0x4E, 0x4F, 0x54, 0x45, 0x50, 0x55, 0x42});
            output.getFD().sync();
        }

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            long[] initial = awaitPresented(scenario);
            String initialPath = documentPath(scenario);
            String initialTitle = title(scenario);
            AtomicBoolean opened = new AtomicBoolean(true);
            AtomicReference<String> error = new AtomicReference<>();
            scenario.onActivity(activity -> {
                opened.set(activity.openDocumentForTesting(
                    Uri.fromFile(invalid)));
                error.set(activity.lastOpenErrorForTesting());
            });
            assertFalse(opened.get());
            assertNotNull(error.get());

            long[] after = awaitPresented(scenario);
            assertEquals(initialPath, documentPath(scenario));
            assertEquals(initialTitle, title(scenario));
            assertEquals(initial[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH],
                         after[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertEquals(0,
                         after[
                             OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
            scenario.onActivity(activity -> {
                ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                    .flushPersistenceForTesting();
                OctavoDocumentStore store =
                    activity.documentStoreForTesting();
                assertEquals(1, store.importSuccessCountForTesting());
                assertEquals(0, store.sessionSaveFailureCountForTesting());
            });
        }
    }

    @Test
    public void corruptSessionFallsBackToDeterministicFixture()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File root = new File(context.getFilesDir(), "port5");
        assertTrue(root.isDirectory() || root.mkdirs());
        File session = new File(root, "reader_session.v1");
        try (FileOutputStream output =
                 new FileOutputStream(session, false)) {
            output.write(new byte[] {1, 2, 3, 4, 5});
            output.getFD().sync();
        }

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            long[] initial = awaitPresented(scenario);
            assertEquals("Octavo Android Port 5", title(scenario));
            assertFalse(surface(scenario).importedDocumentForTesting());
            assertEquals(0,
                         initial[OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertEquals(0,
                         initial[
                             OctavoSurfaceView.STATE_RESTORE_FAILURE_COUNT]);
            scenario.onActivity(activity -> {
                OctavoDocumentStore store =
                    activity.documentStoreForTesting();
                assertEquals(0, store.sessionLoadSuccessCountForTesting());
                assertEquals(1, store.sessionLoadFailureCountForTesting());
                assertEquals(0, store.sessionSaveFailureCountForTesting());
            });
        }
    }
}
