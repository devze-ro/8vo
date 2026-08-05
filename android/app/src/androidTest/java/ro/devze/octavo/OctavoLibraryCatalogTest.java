package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibraryCatalogTest {
    private static final String ALPHA_ASSET =
        "port6/octavo_port6_alpha.epub";
    private static final long ALPHA_BYTE_COUNT = 44190L;
    private static final String ALPHA_SHA256 =
        "dd92f87fa70ea37f761cb9348d5f7b2939afea2661f9f4fe16828ac6ca041f80";
    private static final String ALPHA_TITLE = "Port 6 Alpha Book";

    private static final String BETA_ASSET =
        "port6/octavo_port6_beta.epub";
    private static final long BETA_BYTE_COUNT = 56036L;
    private static final String BETA_SHA256 =
        "e0cca3a5283ce0ad3c2c78871b968c2b5e0711ad81e2bbfaaf92bfe3a35cb0a8";
    private static final String BETA_TITLE = "Port 6 Beta Book";

    private static final int LEGACY_SESSION_MAGIC = 0x4F355253;

    @Before
    public void clearPort6Library() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
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
                && snapshot[OctavoSurfaceView.STATE_DOCUMENT_OPEN] == 1
                && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0,
            "8vo did not present the Port 6 library book");
    }

    private static long[] awaitLocationCacheComplete(
        ActivityScenario<OctavoActivity> scenario,
        String failureMessage) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicReference<long[]> snapshot = new AtomicReference<>();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = (OctavoSurfaceView)
                    activity.findViewById(R.id.octavo_surface);
                snapshot.set(
                    view == null
                        ? null : view.locationCacheStateForTesting());
            });
            long[] current = snapshot.get();
            if (current != null && current.length == 10
                && current[0] == 1 && current[1] == 1) {
                InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();
                return state(scenario);
            }
            SystemClock.sleep(20);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] awaitLocationSummary(
        ActivityScenario<OctavoActivity> scenario,
        long expectedIndex,
        long expectedCount,
        String failureMessage) {
        return awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                    == expectedIndex
                && snapshot[
                    OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT]
                    == expectedCount,
            failureMessage);
    }

    private static void assertNativeHealthy(long[] snapshot) {
        assertEquals(0,
                     snapshot[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
        assertEquals(0,
                     snapshot[OctavoSurfaceView.STATE_READER_VIEW_ERRORS]);
        assertEquals(0,
                     snapshot[OctavoSurfaceView.STATE_NAVIGATION_FAILURE_COUNT]);
        assertEquals(
            0,
            snapshot[OctavoSurfaceView.STATE_DOCUMENT_OPEN_FAILURE_COUNT]);
        assertEquals(
            0,
            snapshot[OctavoSurfaceView.STATE_RESTORE_FAILURE_COUNT]);
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

    private static void assertLibrary(
        ActivityScenario<OctavoActivity> scenario,
        int expectedBooks) {
        scenario.onActivity(activity -> {
            assertTrue(activity.libraryVisibleForTesting());
            assertEquals(expectedBooks,
                         activity.libraryStoreForTesting().bookCount());
            View library = activity.findViewById(R.id.octavo_library);
            assertNotNull(library);
            int minimumGutter = Math.round(
                OctavoDesignTokens.SPACE_LG_DP
                * activity.getResources().getDisplayMetrics().density);
            assertTrue("Library left gutter was not installed synchronously",
                       library.getPaddingLeft() >= minimumGutter);
            assertTrue("Library right gutter was not installed synchronously",
                       library.getPaddingRight() >= minimumGutter);
        });
    }

    private static long[] importBook(
        ActivityScenario<OctavoActivity> scenario,
        File source) {
        AtomicBoolean opened = new AtomicBoolean(false);
        scenario.onActivity(activity -> opened.set(
            activity.openDocumentForTesting(Uri.fromFile(source))));
        assertTrue(opened.get());
        return awaitPresented(scenario);
    }

    private static long[] openBook(
        ActivityScenario<OctavoActivity> scenario,
        String key) {
        AtomicBoolean opened = new AtomicBoolean(false);
        scenario.onActivity(activity ->
            opened.set(activity.openBookForTesting(key)));
        assertTrue(opened.get());
        return awaitPresented(scenario);
    }

    private static void closeBook(
        ActivityScenario<OctavoActivity> scenario,
        int expectedBooks) {
        scenario.onActivity(activity -> activity.closeBookForTesting());
        assertLibrary(scenario, expectedBooks);
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

    private static long[] advanceOnePage(
        ActivityScenario<OctavoActivity> scenario,
        long[] before,
        String failureMessage) {
        long previousHash =
            before[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH];
        long previousLocation =
            before[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX];
        tapNext(scenario);
        long[] after = awaitState(
            scenario,
            snapshot ->
                snapshot[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                    == before[
                        OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT] + 1
                && snapshot[
                    OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING] == 0,
            failureMessage);
        assertEquals(
            before[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT] + 1,
            after[OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]);
        assertNotEquals(previousHash,
                        after[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
        assertTrue(
            after[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]
                > previousLocation);
        assertNativeHealthy(after);
        return after;
    }

    private static OctavoLibraryStore.Book flushAndBook(
        ActivityScenario<OctavoActivity> scenario,
        String key) {
        AtomicReference<OctavoLibraryStore.Book> result =
            new AtomicReference<>();
        scenario.onActivity(activity -> {
            ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                .flushPersistenceForTesting();
            result.set(activity.libraryStoreForTesting().findBook(key));
        });
        assertNotNull(result.get());
        assertTrue(result.get().hasPosition);
        return result.get();
    }

    private static File stageAsset(Context context,
                                   String assetPath,
                                   String name)
        throws IOException {
        File source = new File(context.getCacheDir(), name);
        try (InputStream input = context.getAssets().open(assetPath);
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

    private static void copyFile(File source, File destination)
        throws IOException {
        File parent = destination.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output =
                 new FileOutputStream(destination, false)) {
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

    private static void assertFixtureIdentity(File file,
                                              long byteCount,
                                              String sha256)
        throws IOException, NoSuchAlgorithmException {
        assertTrue(file.isFile());
        assertEquals(byteCount, file.length());
        assertEquals(sha256, sha256(file));
    }

    @Test
    public void importsTwoBooksDeduplicatesAndRestoresIndependentPositions()
        throws IOException, NoSuchAlgorithmException {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(context, ALPHA_ASSET,
                                "octavo_port6_alpha_source.epub");
        File beta = stageAsset(context, BETA_ASSET,
                               "octavo_port6_beta_source.epub");
        assertFixtureIdentity(alpha, ALPHA_BYTE_COUNT, ALPHA_SHA256);
        assertFixtureIdentity(beta, BETA_BYTE_COUNT, BETA_SHA256);

        long alphaSpine;
        long alphaByte;
        long alphaPageIndex;
        long alphaPageCount;
        long alphaTextHash;
        long alphaLocationIndex;
        long alphaLocationCount;
        long betaSpine;
        long betaByte;
        long betaPageIndex;
        long betaPageCount;
        long betaTextHash;
        long betaLocationIndex;
        long betaLocationCount;
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(scenario, 1);
            scenario.onActivity(activity -> {
                Intent picker = activity.openDocumentIntentForTesting();
                assertEquals(Intent.ACTION_OPEN_DOCUMENT,
                             picker.getAction());
                assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));
                assertEquals("application/epub+zip", picker.getType());
                assertTrue((picker.getFlags()
                            & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            });

            long[] alphaInitial = importBook(scenario, alpha);
            assertEquals(ALPHA_TITLE, title(scenario));
            assertTrue(text(scenario).contains(
                "first app-private library EPUB"));
            assertEquals(ALPHA_SHA256,
                         surface(scenario).documentKeyForTesting());
            assertTrue(surface(scenario).importedDocumentForTesting());
            assertNativeHealthy(alphaInitial);
            long[] alphaPageTwo = advanceOnePage(
                scenario, alphaInitial,
                "8vo did not present Alpha page 2");
            alphaPageTwo = awaitLocationCacheComplete(
                scenario,
                "8vo did not complete Alpha location warming");
            OctavoLibraryStore.Book alphaBook =
                flushAndBook(scenario, ALPHA_SHA256);
            alphaSpine = alphaBook.spineIndex;
            alphaByte = alphaBook.byteOffset;
            alphaPageIndex = alphaPageTwo[OctavoSurfaceView.STATE_PAGE_INDEX];
            alphaPageCount = alphaPageTwo[OctavoSurfaceView.STATE_PAGE_COUNT];
            alphaTextHash = alphaPageTwo[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH];
            alphaLocationIndex = alphaPageTwo[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX];
            alphaLocationCount = alphaPageTwo[
                OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT];
            assertTrue(alphaByte > 0);
            assertEquals(
                alphaPageTwo[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX],
                alphaSpine);
            closeBook(scenario, 2);

            long[] betaInitial = importBook(scenario, beta);
            assertEquals(BETA_TITLE, title(scenario));
            assertTrue(text(scenario).contains(
                "second app-private library EPUB"));
            assertEquals(BETA_SHA256,
                         surface(scenario).documentKeyForTesting());
            assertNativeHealthy(betaInitial);
            long[] betaPageTwo = advanceOnePage(
                scenario, betaInitial,
                "8vo did not present Beta page 2");
            betaPageTwo = awaitLocationCacheComplete(
                scenario,
                "8vo did not complete Beta location warming");
            OctavoLibraryStore.Book betaBook =
                flushAndBook(scenario, BETA_SHA256);
            betaSpine = betaBook.spineIndex;
            betaByte = betaBook.byteOffset;
            betaPageIndex = betaPageTwo[OctavoSurfaceView.STATE_PAGE_INDEX];
            betaPageCount = betaPageTwo[OctavoSurfaceView.STATE_PAGE_COUNT];
            betaTextHash = betaPageTwo[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH];
            betaLocationIndex = betaPageTwo[OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX];
            betaLocationCount = betaPageTwo[
                OctavoSurfaceView.STATE_PROGRESS_LOCATION_COUNT];
            assertTrue(betaByte > 0);
            assertEquals(
                betaPageTwo[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX],
                betaSpine);
            closeBook(scenario, 3);

            long[] alphaDuplicate = importBook(scenario, alpha);
            alphaDuplicate = awaitLocationSummary(
                scenario,
                alphaLocationIndex,
                alphaLocationCount,
                "8vo did not refresh Alpha duplicate location metadata");
            assertEquals(ALPHA_TITLE, title(scenario));
            assertEquals(1,
                         alphaDuplicate[
                             OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertEquals(1,
                         alphaDuplicate[
                             OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(alphaSpine,
                         alphaDuplicate[
                             OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
            assertEquals(alphaByte,
                         alphaDuplicate[
                             OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
            assertEquals(alphaPageIndex,
                         alphaDuplicate[OctavoSurfaceView.STATE_PAGE_INDEX]);
            assertEquals(alphaPageCount,
                         alphaDuplicate[OctavoSurfaceView.STATE_PAGE_COUNT]);
            assertEquals(alphaTextHash,
                         alphaDuplicate[
                             OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertEquals(alphaLocationIndex,
                         alphaDuplicate[
                             OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]);
            assertNativeHealthy(alphaDuplicate);
            scenario.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(3, store.bookCount());
                assertEquals(3, store.importSuccessCountForTesting());
                assertEquals(1, store.duplicateImportCountForTesting());
                assertEquals(0, store.importFailureCountForTesting());
                assertEquals(0, store.catalogSaveFailureCountForTesting());
                assertTrue(store.catalogFileForTesting().isFile());
            });
            closeBook(scenario, 3);
        }

        try (ActivityScenario<OctavoActivity> relaunched =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(relaunched, 3);
            relaunched.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(1, store.catalogLoadSuccessCountForTesting());
                assertEquals(0, store.catalogLoadFailureCountForTesting());
                assertEquals(0, store.catalogSaveFailureCountForTesting());
            });

            long[] alphaRestored = openBook(relaunched, ALPHA_SHA256);
            alphaRestored = awaitLocationSummary(
                relaunched,
                alphaLocationIndex,
                alphaLocationCount,
                "8vo did not refresh relaunched Alpha location metadata");
            assertEquals(ALPHA_TITLE, title(relaunched));
            assertEquals(1,
                         alphaRestored[
                             OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(alphaSpine,
                         alphaRestored[
                             OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
            assertEquals(alphaByte,
                         alphaRestored[
                             OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
            assertEquals(alphaPageIndex,
                         alphaRestored[OctavoSurfaceView.STATE_PAGE_INDEX]);
            assertEquals(alphaPageCount,
                         alphaRestored[OctavoSurfaceView.STATE_PAGE_COUNT]);
            assertEquals(alphaTextHash,
                         alphaRestored[
                             OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertEquals(alphaLocationIndex,
                         alphaRestored[
                             OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]);
            assertNativeHealthy(alphaRestored);
            closeBook(relaunched, 3);

            long[] betaRestored = openBook(relaunched, BETA_SHA256);
            betaRestored = awaitLocationSummary(
                relaunched,
                betaLocationIndex,
                betaLocationCount,
                "8vo did not refresh relaunched Beta location metadata");
            assertEquals(BETA_TITLE, title(relaunched));
            assertEquals(1,
                         betaRestored[
                             OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(betaSpine,
                         betaRestored[
                             OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
            assertEquals(betaByte,
                         betaRestored[
                             OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
            assertEquals(betaPageIndex,
                         betaRestored[OctavoSurfaceView.STATE_PAGE_INDEX]);
            assertEquals(betaPageCount,
                         betaRestored[OctavoSurfaceView.STATE_PAGE_COUNT]);
            assertEquals(betaTextHash,
                         betaRestored[
                             OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH]);
            assertEquals(betaLocationIndex,
                         betaRestored[
                             OctavoSurfaceView.STATE_PROGRESS_LOCATION_INDEX]);
            assertNativeHealthy(betaRestored);
        }
    }

    @Test
    public void removalDeletesOnlyManagedCopyAndKeepsOtherBook()
        throws IOException, NoSuchAlgorithmException {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(context, ALPHA_ASSET,
                                "octavo_port6_remove_alpha.epub");
        File beta = stageAsset(context, BETA_ASSET,
                               "octavo_port6_remove_beta.epub");
        assertFixtureIdentity(alpha, ALPHA_BYTE_COUNT, ALPHA_SHA256);
        assertFixtureIdentity(beta, BETA_BYTE_COUNT, BETA_SHA256);

        AtomicReference<File> managedAlpha = new AtomicReference<>();
        AtomicReference<File> managedBeta = new AtomicReference<>();
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            importBook(scenario, alpha);
            scenario.onActivity(activity -> managedAlpha.set(
                activity.libraryStoreForTesting()
                    .findBook(ALPHA_SHA256).file));
            closeBook(scenario, 2);

            importBook(scenario, beta);
            scenario.onActivity(activity -> managedBeta.set(
                activity.libraryStoreForTesting()
                    .findBook(BETA_SHA256).file));
            closeBook(scenario, 3);

            AtomicBoolean removed = new AtomicBoolean(false);
            scenario.onActivity(activity -> removed.set(
                activity.removeBookForTesting(ALPHA_SHA256)));
            assertTrue(removed.get());
            assertLibrary(scenario, 2);
            assertNotNull(managedAlpha.get());
            assertNotNull(managedBeta.get());
            assertFalse(managedAlpha.get().exists());
            assertTrue(managedBeta.get().isFile());
            assertTrue("Provider-owned Alpha source was deleted",
                       alpha.isFile());
            assertTrue("Provider-owned Beta source was deleted",
                       beta.isFile());

            scenario.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(1, store.removeSuccessCountForTesting());
                assertEquals(0, store.removeFailureCountForTesting());
                assertEquals(0,
                             store.managedDeleteFailureCountForTesting());
                assertEquals(null, store.findBook(ALPHA_SHA256));
                assertNotNull(store.findBook(BETA_SHA256));
            });
            long[] betaPresented = openBook(scenario, BETA_SHA256);
            assertEquals(BETA_TITLE, title(scenario));
            assertNativeHealthy(betaPresented);
        }
    }

    @Test
    public void invalidImportLeavesSampleOnlyLibraryUsable()
        throws IOException, NoSuchAlgorithmException {
        Context context = ApplicationProvider.getApplicationContext();
        File invalid = new File(context.getCacheDir(),
                                "octavo_port6_invalid.epub");
        try (FileOutputStream output =
                 new FileOutputStream(invalid, false)) {
            output.write(new byte[] {
                0x4E, 0x4F, 0x54, 0x2D, 0x41, 0x4E, 0x2D, 0x45,
                0x50, 0x55, 0x42
            });
            output.getFD().sync();
        }
        String invalidKey = sha256(invalid);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(scenario, 1);
            AtomicBoolean opened = new AtomicBoolean(true);
            AtomicReference<String> error = new AtomicReference<>();
            scenario.onActivity(activity -> {
                opened.set(activity.openDocumentForTesting(
                    Uri.fromFile(invalid)));
                error.set(activity.lastOpenErrorForTesting());
            });
            assertFalse(opened.get());
            assertNotNull(error.get());
            assertLibrary(scenario, 1);
            assertFalse(new File(
                new File(context.getFilesDir(), "port6/documents"),
                invalidKey + ".epub").exists());
            scenario.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(1, store.importSuccessCountForTesting());
                assertEquals(0, store.importFailureCountForTesting());
                assertEquals(0, store.catalogSaveFailureCountForTesting());
            });

            AtomicBoolean fixtureOpened = new AtomicBoolean(false);
            scenario.onActivity(activity -> fixtureOpened.set(
                activity.openFixtureForTesting()));
            assertTrue(fixtureOpened.get());
            long[] fixture = awaitPresented(scenario);
            assertEquals(OctavoFixture.TITLE, title(scenario));
            assertNativeHealthy(fixture);
        }
    }

    @Test
    public void corruptCatalogFallsBackToSampleOnlyLibrary()
        throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File root = new File(context.getFilesDir(), "port6");
        assertTrue(root.isDirectory() || root.mkdirs());
        File catalog = new File(root, "library.v1");
        try (FileOutputStream output =
                 new FileOutputStream(catalog, false)) {
            output.write(new byte[] {1, 2, 3, 4, 5});
            output.getFD().sync();
        }

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(scenario, 1);
            scenario.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(0, store.catalogLoadSuccessCountForTesting());
                assertEquals(1, store.catalogLoadFailureCountForTesting());
                assertEquals(OctavoFixture.SHA256,
                             store.fixtureBook().key);
            });
            AtomicBoolean opened = new AtomicBoolean(false);
            scenario.onActivity(activity -> opened.set(
                activity.openFixtureForTesting()));
            assertTrue(opened.get());
            long[] fixture = awaitPresented(scenario);
            assertEquals(OctavoFixture.TITLE, title(scenario));
            assertNativeHealthy(fixture);
        }
    }

    @Test
    public void migratesPort5ImportedSessionExactlyOnce()
        throws IOException, NoSuchAlgorithmException {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(context, ALPHA_ASSET,
                                "octavo_port6_migration_source.epub");
        assertFixtureIdentity(alpha, ALPHA_BYTE_COUNT, ALPHA_SHA256);

        File legacyRoot = new File(context.getFilesDir(), "port5");
        File legacyDocuments = new File(legacyRoot, "documents");
        assertTrue(legacyDocuments.isDirectory()
                   || legacyDocuments.mkdirs());
        File legacyManaged =
            new File(legacyDocuments, ALPHA_SHA256 + ".epub");
        copyFile(alpha, legacyManaged);
        File legacySession = new File(legacyRoot, "reader_session.v1");
        try (FileOutputStream fileOutput =
                 new FileOutputStream(legacySession, false);
             DataOutputStream output = new DataOutputStream(
                 new BufferedOutputStream(fileOutput))) {
            output.writeInt(LEGACY_SESSION_MAGIC);
            output.writeInt(1);
            output.writeBoolean(true);
            output.writeUTF(ALPHA_SHA256);
            output.writeLong(ALPHA_BYTE_COUNT);
            output.writeBoolean(false);
            output.writeLong(0);
            output.writeLong(0);
            output.flush();
            fileOutput.getFD().sync();
        }

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(scenario, 2);
            scenario.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(1, store.migrationSuccessCountForTesting());
                assertEquals(0, store.migrationFailureCountForTesting());
                assertEquals("Imported EPUB",
                             store.findBook(ALPHA_SHA256).title);
                assertTrue(store.catalogFileForTesting().isFile());
                assertTrue(store.findBook(ALPHA_SHA256).file.isFile());
            });
            long[] alphaPresented = openBook(scenario, ALPHA_SHA256);
            assertEquals(ALPHA_TITLE, title(scenario));
            assertTrue(text(scenario).contains(
                "first app-private library EPUB"));
            assertNativeHealthy(alphaPresented);
            closeBook(scenario, 2);
        }

        assertTrue("Port 5 source was unexpectedly removed",
                   legacyManaged.isFile());
        assertTrue("Port 5 migration record was unexpectedly removed",
                   legacySession.isFile());

        try (ActivityScenario<OctavoActivity> relaunched =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(relaunched, 2);
            relaunched.onActivity(activity -> {
                OctavoLibraryStore store =
                    activity.libraryStoreForTesting();
                assertEquals(1, store.catalogLoadSuccessCountForTesting());
                assertEquals(0, store.catalogLoadFailureCountForTesting());
                assertEquals(0, store.migrationSuccessCountForTesting());
                assertEquals(0, store.migrationFailureCountForTesting());
                assertEquals(ALPHA_TITLE,
                             store.findBook(ALPHA_SHA256).title);
            });
            long[] alphaPresented = openBook(relaunched, ALPHA_SHA256);
            assertEquals(ALPHA_TITLE, title(relaunched));
            assertNativeHealthy(alphaPresented);
        }
    }
}
