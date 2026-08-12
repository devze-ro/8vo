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
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

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
        OctavoLibrarySyncStore.clearForTesting(context);
        OctavoLibraryMembershipStore.clearForTesting(context);
        OctavoBookTransferStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
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

    private static OctavoLibraryStore.Book importAndAssociate(
        OctavoLibraryStore store, File source, String title) throws Exception {
        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(source));
        assertNotNull(staged);
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        assertTrue(store.recordOpened(managed, title));
        assertTrue(store.completeImportedCatalogAssociation(managed));
        return store.findBook(managed.key);
    }

    private static byte[] readAllBytesUnchecked(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException exception) {
            throw new AssertionError("Unable to read test evidence", exception);
        }
    }

    private static void writeImportJournal(File file,
                                           String key,
                                           long byteCount,
                                           int phase) throws IOException {
        byte[] prefix;
        try (java.io.ByteArrayOutputStream bytes =
                 new java.io.ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x4F36494A);
            output.writeInt(1);
            output.writeInt(phase);
            output.write(key.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.writeLong(byteCount);
            output.flush();
            prefix = bytes.toByteArray();
        }
        CRC32 crc = new CRC32();
        crc.update(prefix);
        try (FileOutputStream fileOutput =
                 new FileOutputStream(file, false);
             DataOutputStream output = new DataOutputStream(fileOutput)) {
            output.write(prefix);
            output.writeInt((int)crc.getValue());
            output.flush();
            fileOutput.getFD().sync();
        }
        assertEquals(88, file.length());
    }

    @Test
    public void sameLengthManagedSubstitutionIsRepairedAndNeverLoadsAsDigest()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_digest_repair.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        File claimed = new File(
            store.documentDirectoryForTesting(), ALPHA_SHA256 + ".epub");
        copyFile(alpha, claimed);
        try (RandomAccessFile mutation = new RandomAccessFile(claimed, "rw")) {
            int first = mutation.read();
            assertTrue(first >= 0);
            mutation.seek(0);
            mutation.write(first ^ 0x01);
            mutation.getFD().sync();
        }
        assertEquals(ALPHA_BYTE_COUNT, claimed.length());
        assertNotEquals(ALPHA_SHA256, sha256(claimed));
        assertFalse(store.verifyManagedFile(ALPHA_SHA256, ALPHA_BYTE_COUNT));

        OctavoLibraryStore.Book imported =
            store.importDocument(Uri.fromFile(alpha));
        assertEquals(ALPHA_SHA256, imported.key);
        assertTrue(store.isStagedImport(imported));
        assertNotEquals(ALPHA_SHA256, sha256(claimed));
        assertTrue(store.verifyBookIdentity(imported));
        imported = store.publishReader0ValidatedImport(imported);
        assertFalse(store.isStagedImport(imported));
        assertTrue(store.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        assertEquals(ALPHA_SHA256, sha256(claimed));
        assertTrue(store.recordOpened(imported, ALPHA_TITLE));
        assertTrue(store.completeImportedCatalogAssociation(imported));
        assertFalse(store.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        byte[] catalogBefore = Files.readAllBytes(
            store.catalogFileForTesting().toPath());

        try (RandomAccessFile mutation = new RandomAccessFile(claimed, "rw")) {
            mutation.seek(claimed.length() - 1);
            int last = mutation.read();
            assertTrue(last >= 0);
            mutation.seek(claimed.length() - 1);
            mutation.write(last ^ 0x01);
            mutation.getFD().sync();
        }
        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.LOADED,
                     reopened.loadStatus());
        assertEquals(2, reopened.bookCount());
        assertFalse(reopened.findBook(ALPHA_SHA256).repairRequired);
        assertFalse(reopened.verifyBookIdentity(
            reopened.findBook(ALPHA_SHA256)));
        assertTrue(reopened.findBook(ALPHA_SHA256).repairRequired);
        assertEquals(OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR,
                     reopened.loadStatus());
        assertEquals(null, reopened.sessionFor(
            reopened.findBook(ALPHA_SHA256)));
        assertFalse(reopened.hasExactManagedBook(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        assertFalse(reopened.recordTransferredBook(
            ALPHA_SHA256, ALPHA_BYTE_COUNT, ALPHA_TITLE));
        assertFalse(Arrays.equals(
            catalogBefore,
            Files.readAllBytes(reopened.catalogFileForTesting().toPath())));

        OctavoLibraryStore afterRestart =
            new OctavoLibraryStore(context);
        afterRestart.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR,
                     afterRestart.loadStatus());
        assertTrue(afterRestart.findBook(ALPHA_SHA256).repairRequired);
        assertFalse(afterRestart.verifyBookIdentity(
            afterRestart.findBook(ALPHA_SHA256)));
        assertFalse(afterRestart.hasExactManagedBook(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
    }

    @Test
    public void crashBeforeManagedMoveClearsOnlyFixedStagingAndJournal()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_pre_move_crash.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.isStagedImport(staged));
        assertEquals(
            store.documentDirectoryForTesting().getCanonicalFile(),
            store.importStagingFileForTesting()
                .getParentFile().getCanonicalFile());
        assertTrue(store.importStagingFileForTesting().isFile());
        assertFalse(store.managedFile(ALPHA_SHA256).exists());
        writeImportJournal(store.importJournalFileForTesting(),
                           ALPHA_SHA256,
                           ALPHA_BYTE_COUNT,
                           1);

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertFalse(reopened.importStagingFileForTesting().exists());
        assertFalse(reopened.importJournalFileForTesting().exists());
        assertFalse(reopened.managedFile(ALPHA_SHA256).exists());
        assertEquals(1, reopened.bookCount());
    }

    @Test
    public void definiteAtomicMoveFailureRollsBackReadyImportWithoutRestart()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_move_failure.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(staged));
        File conflictingDestination = store.managedFile(ALPHA_SHA256);
        assertTrue(conflictingDestination.mkdir());
        File preserved = new File(conflictingDestination, "preserved");
        try (FileOutputStream output =
                 new FileOutputStream(preserved, false)) {
            output.write(new byte[] {4, 5, 6});
            output.getFD().sync();
        }

        try {
            store.publishReader0ValidatedImport(staged);
            fail("Non-file destination unexpectedly accepted atomic move");
        } catch (IOException expected) {
            assertFalse(store.mutationBlocked());
        }
        assertFalse(store.importJournalFileForTesting().exists());
        assertTrue(store.isStagedImport(staged));
        assertFalse(store.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        assertTrue(preserved.isFile());
        assertTrue(store.discardUncataloged(staged));
        assertFalse(store.importStagingFileForTesting().exists());

        assertTrue(preserved.delete());
        assertTrue(conflictingDestination.delete());
        OctavoLibraryStore.Book retry =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.isStagedImport(retry));
        assertTrue(store.discardUncataloged(retry));
        assertFalse(store.importStagingFileForTesting().exists());
    }

    @Test
    public void alteredRejectedStagingCannotLockOutLaterImports()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_altered_staging.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book altered =
            store.importDocument(Uri.fromFile(alpha));
        try (RandomAccessFile mutation = new RandomAccessFile(
                 store.importStagingFileForTesting(), "rw")) {
            int first = mutation.read();
            assertTrue(first >= 0);
            mutation.seek(0);
            mutation.write(first ^ 0x01);
            mutation.getFD().sync();
        }
        assertFalse(store.verifyBookIdentity(altered));
        try {
            store.publishReader0ValidatedImport(altered);
            fail("Altered fixed staging unexpectedly published");
        } catch (IOException expected) {
            assertFalse(store.mutationBlocked());
        }
        assertTrue(store.isStagedImport(altered));
        assertTrue(store.discardUncataloged(altered));
        assertFalse(store.importStagingFileForTesting().exists());

        OctavoLibraryStore.Book truncated =
            store.importDocument(Uri.fromFile(alpha));
        try (RandomAccessFile mutation = new RandomAccessFile(
                 store.importStagingFileForTesting(), "rw")) {
            mutation.setLength(ALPHA_BYTE_COUNT - 1);
            mutation.getFD().sync();
        }
        assertTrue(store.isStagedImport(truncated));
        assertTrue(store.discardUncataloged(truncated));
        assertFalse(store.importStagingFileForTesting().exists());
        assertFalse(store.mutationBlocked());

        OctavoLibraryStore.Book alreadyAbsent =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.importStagingFileForTesting().delete());
        assertTrue(store.isStagedImport(alreadyAbsent));
        assertTrue(store.discardUncataloged(alreadyAbsent));
        assertFalse(store.importStagingFileForTesting().exists());
        assertFalse(store.mutationBlocked());
    }

    @Test
    public void crashAfterManagedMoveRequiresExplicitVerifiedAssociationRetry()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_post_move_crash.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        assertFalse(store.importStagingFileForTesting().exists());
        assertTrue(store.importJournalFileForTesting().isFile());
        assertTrue(store.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        assertEquals(null, store.findBook(ALPHA_SHA256));
        assertFixtureIdentity(
            managed.file, ALPHA_BYTE_COUNT, ALPHA_SHA256);

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertEquals(
            OctavoLibraryStore.LoadStatus.IMPORT_ASSOCIATION_PENDING,
            reopened.loadStatus());
        assertEquals(null, reopened.findBook(ALPHA_SHA256));
        assertTrue(reopened.importJournalFileForTesting().isFile());
        OctavoLibraryStore.Book recovered = reopened.pendingImportedBook();
        assertNotNull(recovered);
        assertEquals("Imported EPUB", recovered.title);
        assertFalse(recovered.repairRequired);
        assertFalse(recovered.identityVerified);
        assertFalse(reopened.importStagingFileForTesting().exists());
        assertFixtureIdentity(
            recovered.file, ALPHA_BYTE_COUNT, ALPHA_SHA256);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.VERIFIED,
            reopened.verifyBookIdentityStep(recovered, 4 * 1024 * 1024));
        assertTrue(reopened.recordValidatedPendingImport(
            recovered, ALPHA_TITLE));
        assertTrue(reopened.completeImportedCatalogAssociation(recovered));
        assertNotNull(reopened.findBook(ALPHA_SHA256));
        assertEquals(0, reopened.findBook(ALPHA_SHA256).lastOpenedTime);
        assertFalse(reopened.importJournalFileForTesting().exists());
        assertTrue(reopened.catalogFileForTesting().isFile());
    }

    @Test
    public void sameLengthMutationAfterManagedMoveNeverAutoAssociates()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_pending_mutation.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        try (RandomAccessFile mutation =
                 new RandomAccessFile(managed.file, "rw")) {
            int first = mutation.read();
            assertTrue(first >= 0);
            mutation.seek(0);
            mutation.write(first ^ 0x01);
            mutation.getFD().sync();
        }
        assertEquals(ALPHA_BYTE_COUNT, managed.file.length());

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertEquals(null, reopened.findBook(ALPHA_SHA256));
        OctavoLibraryStore.Book pending = reopened.pendingImportedBook();
        assertNotNull(pending);
        assertFalse(pending.identityVerified);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.FAILED,
            reopened.verifyBookIdentityStep(
                pending, 4 * 1024 * 1024));
        assertEquals(null, reopened.findBook(ALPHA_SHA256));
        assertTrue(reopened.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        assertTrue(reopened.importJournalFileForTesting().isFile());
    }

    @Test
    public void failedPendingIdentityCanBeDiscardedWithoutTouchingLibrary()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_discard_pending_alpha.epub");
        File beta = stageAsset(
            context, BETA_ASSET, "octavo_port6_discard_pending_beta.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book betaStaged =
            store.importDocument(Uri.fromFile(beta));
        assertTrue(store.verifyBookIdentity(betaStaged));
        OctavoLibraryStore.Book betaManaged =
            store.publishReader0ValidatedImport(betaStaged);
        assertTrue(store.recordOpened(betaManaged, BETA_TITLE));
        assertTrue(store.completeImportedCatalogAssociation(betaManaged));
        byte[] betaBytes = Files.readAllBytes(betaManaged.file.toPath());

        OctavoLibraryStore.Book alphaStaged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(alphaStaged));
        OctavoLibraryStore.Book alphaManaged =
            store.publishReader0ValidatedImport(alphaStaged);
        try (RandomAccessFile mutation =
                 new RandomAccessFile(alphaManaged.file, "rw")) {
            int first = mutation.read();
            assertTrue(first >= 0);
            mutation.seek(0);
            mutation.write(first ^ 0x01);
            mutation.getFD().sync();
        }

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        OctavoLibraryStore.Book pending = reopened.pendingImportedBook();
        assertNotNull(pending);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.FAILED,
            reopened.verifyBookIdentityStep(pending, 4 * 1024 * 1024));
        assertTrue(reopened.discardPendingImportAssociation(pending));
        assertFalse(alphaManaged.file.exists());
        assertFalse(reopened.importJournalFileForTesting().exists());
        assertEquals(null, reopened.pendingImportedBook());

        OctavoLibraryStore.Book retainedBeta =
            reopened.findBook(BETA_SHA256);
        assertNotNull(retainedBeta);
        assertFalse(retainedBeta.repairRequired);
        assertTrue(retainedBeta.file.isFile());
        assertTrue(Arrays.equals(
            betaBytes, Files.readAllBytes(retainedBeta.file.toPath())));
    }

    @Test
    public void pendingDiscardRefusesHealthyAssociationAndMarksLateDamage()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_discard_late_damage.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        assertTrue(store.recordOpened(managed, ALPHA_TITLE));
        assertEquals(
            OctavoLibraryStore.LoadStatus.IMPORT_ASSOCIATION_PENDING,
            store.loadStatus());
        assertFalse(store.discardPendingImportAssociation(managed));
        assertTrue(managed.file.isFile());
        assertTrue(store.importJournalFileForTesting().isFile());
        assertFalse(store.findBook(ALPHA_SHA256).repairRequired);

        try (RandomAccessFile damaged =
                 new RandomAccessFile(managed.file, "rw")) {
            damaged.setLength(ALPHA_BYTE_COUNT - 1);
            damaged.getFD().sync();
        }
        assertTrue(store.discardPendingImportAssociation(managed));
        assertFalse(managed.file.exists());
        assertFalse(store.importJournalFileForTesting().exists());
        OctavoLibraryStore.Book retained = store.findBook(ALPHA_SHA256);
        assertNotNull(retained);
        assertTrue(retained.repairRequired);
        assertFalse(retained.identityVerified);
        assertEquals(
            OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR,
            store.loadStatus());
    }

    @Test
    public void pendingDiscardRetriesDeletionAndJournalClearAfterRestart()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_discard_retry_alpha.epub");
        File beta = stageAsset(
            context, BETA_ASSET, "octavo_port6_discard_retry_beta.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        assertTrue(managed.file.delete());
        assertTrue(managed.file.mkdir());
        File blocker = new File(managed.file, "preserved");
        try (FileOutputStream output =
                 new FileOutputStream(blocker, false)) {
            output.write(new byte[] {1, 2, 3});
            output.getFD().sync();
        }
        OctavoLibraryStore.Book pending = store.pendingImportedBook();
        assertNotNull(pending);
        assertFalse(store.discardPendingImportAssociation(pending));
        assertTrue(blocker.isFile());
        assertTrue(store.importJournalFileForTesting().isFile());

        assertTrue(blocker.delete());
        assertTrue(managed.file.delete());
        OctavoLibraryStore afterDeleteFailure =
            new OctavoLibraryStore(context);
        afterDeleteFailure.loadCatalog(fixture);
        OctavoLibraryStore.Book absentPending =
            afterDeleteFailure.pendingImportedBook();
        assertNotNull(absentPending);
        assertTrue(afterDeleteFailure.discardPendingImportAssociation(
            absentPending));
        assertFalse(afterDeleteFailure.importJournalFileForTesting().exists());

        OctavoLibraryStore.Book betaStaged =
            afterDeleteFailure.importDocument(Uri.fromFile(beta));
        assertTrue(afterDeleteFailure.verifyBookIdentity(betaStaged));
        OctavoLibraryStore.Book betaManaged =
            afterDeleteFailure.publishReader0ValidatedImport(betaStaged);
        File journalTemporary =
            afterDeleteFailure.importJournalTemporaryFileForTesting();
        assertTrue(journalTemporary.mkdir());
        File journalBlocker = new File(journalTemporary, "preserved");
        try (FileOutputStream output =
                 new FileOutputStream(journalBlocker, false)) {
            output.write(new byte[] {4, 5, 6});
            output.getFD().sync();
        }
        assertFalse(afterDeleteFailure.discardPendingImportAssociation(
            betaManaged));
        assertFalse(betaManaged.file.exists());
        assertTrue(afterDeleteFailure.importJournalFileForTesting().isFile());

        assertTrue(journalBlocker.delete());
        assertTrue(journalTemporary.delete());
        OctavoLibraryStore afterJournalFailure =
            new OctavoLibraryStore(context);
        afterJournalFailure.loadCatalog(fixture);
        OctavoLibraryStore.Book journalPending =
            afterJournalFailure.pendingImportedBook();
        assertNotNull(journalPending);
        assertTrue(afterJournalFailure.discardPendingImportAssociation(
            journalPending));
        assertFalse(afterJournalFailure.importJournalFileForTesting().exists());
        assertFalse(afterJournalFailure.managedFile(BETA_SHA256).exists());
    }

    @Test
    public void crashAfterCatalogAssociationClearsJournalWithoutTitleLoss()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File beta = stageAsset(
            context, BETA_ASSET, "octavo_port6_post_catalog_crash.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);

        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(beta));
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        assertTrue(store.recordOpened(managed, BETA_TITLE));
        assertTrue(store.importJournalFileForTesting().isFile());

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        OctavoLibraryStore.Book recovered = reopened.findBook(BETA_SHA256);
        assertNotNull(recovered);
        assertEquals(BETA_TITLE, recovered.title);
        assertFalse(reopened.importJournalFileForTesting().exists());
        assertTrue(reopened.completeImportedCatalogAssociation(recovered));
    }

    @Test
    public void malformedImportJournalIsPreservedAndBlocksReplacement()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_bad_import_journal.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore seed = new OctavoLibraryStore(context);
        copyFile(alpha, seed.importStagingFileForTesting());
        byte[] malformed = new byte[] {0x4F, 0x36, 0x49, 0x4A, 1, 2, 3};
        try (FileOutputStream output = new FileOutputStream(
                 seed.importJournalFileForTesting(), false)) {
            output.write(malformed);
            output.getFD().sync();
        }

        OctavoLibraryStore blocked = new OctavoLibraryStore(context);
        blocked.loadCatalog(fixture);
        assertEquals(
            OctavoLibraryStore.LoadStatus.IMPORT_RECOVERY_BLOCKED,
            blocked.loadStatus());
        assertTrue(blocked.importStagingFileForTesting().isFile());
        assertTrue(Arrays.equals(
            malformed,
            Files.readAllBytes(
                blocked.importJournalFileForTesting().toPath())));
        try {
            blocked.importDocument(Uri.fromFile(alpha));
            fail("Malformed journal unexpectedly allowed replacement");
        } catch (IOException expected) {
            assertTrue(blocked.mutationBlocked());
        }
        assertTrue(blocked.importStagingFileForTesting().isFile());
        assertTrue(Arrays.equals(
            malformed,
            Files.readAllBytes(
                blocked.importJournalFileForTesting().toPath())));
    }

    @Test
    public void unclearedFixedStagingBlocksReplacementWithoutOverwrite()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_staging_block.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore seed = new OctavoLibraryStore(context);
        File staging = seed.importStagingFileForTesting();
        assertTrue(staging.mkdir());
        File evidence = new File(staging, "preserved");
        try (FileOutputStream output =
                 new FileOutputStream(evidence, false)) {
            output.write(new byte[] {1, 2, 3});
            output.getFD().sync();
        }

        OctavoLibraryStore blocked = new OctavoLibraryStore(context);
        blocked.loadCatalog(fixture);
        assertEquals(
            OctavoLibraryStore.LoadStatus.IMPORT_RECOVERY_BLOCKED,
            blocked.loadStatus());
        assertTrue(evidence.isFile());
        try {
            blocked.importDocument(Uri.fromFile(alpha));
            fail("Uncleared fixed staging unexpectedly allowed replacement");
        } catch (IOException expected) {
            assertTrue(blocked.mutationBlocked());
        }
        assertTrue(evidence.isFile());
    }

    @Test
    public void managedIdentityVerificationAdvancesInBoundedSlices()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        File managed = new File(
            store.documentDirectoryForTesting(),
            "bounded-identity.epub");
        byte[] block = new byte[64 * 1024];
        for (int index = 0; index < block.length; ++index) {
            block[index] = (byte)(index * 31 + 7);
        }
        long byteCount = 8L * 1024L * 1024L;
        try (FileOutputStream output =
                 new FileOutputStream(managed, false)) {
            for (long written = 0; written < byteCount;
                 written += block.length) {
                output.write(block);
            }
            output.getFD().sync();
        }
        String digest = sha256(managed);
        File canonical = new File(
            store.documentDirectoryForTesting(), digest + ".epub");
        assertTrue(managed.renameTo(canonical));
        OctavoLibraryStore.Book candidate =
            new OctavoLibraryStore.Book(
                canonical, digest, byteCount, true, false, false,
                "Bounded verification", 0, 0, false, 0, 0);

        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.PENDING,
            store.verifyBookIdentityStep(candidate, 4 * 1024 * 1024));
        assertFalse(candidate.identityVerified);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.VERIFIED,
            store.verifyBookIdentityStep(candidate, 4 * 1024 * 1024));
        assertTrue(candidate.identityVerified);
        assertFalse(candidate.repairRequired);
        assertTrue(canonical.delete());
    }

    @Test
    public void validatedTransferredProjectionPersistsWithoutOpeningAgain()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_validated_projection.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore seed = new OctavoLibraryStore(context);
        seed.loadCatalog(fixture);
        OctavoLibraryStore.Book staged =
            seed.importDocument(Uri.fromFile(alpha));
        assertTrue(seed.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            seed.publishReader0ValidatedImport(staged);
        assertTrue(seed.recordOpened(managed, ALPHA_TITLE));
        long lastOpened = seed.findBook(ALPHA_SHA256).lastOpenedTime;
        assertTrue(lastOpened > 0);
        assertTrue(seed.completeImportedCatalogAssociation(managed));

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        OctavoLibraryStore.Book book = reopened.findBook(ALPHA_SHA256);
        assertNotNull(book);
        assertFalse(book.identityVerified);
        OctavoLibraryStore.TransferredBookOutcome prepared =
            reopened.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        OctavoLibraryStore.Book capability = prepared.book;
        assertTrue(book == capability);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.COMPLETED,
            reopened.verifyAndRecordTransferredBookStep(
                capability, "Reader validated projection",
                4 * 1024 * 1024));
        assertEquals(lastOpened, book.lastOpenedTime);
        assertEquals("Reader validated projection", book.title);

        OctavoLibraryStore afterProjection =
            new OctavoLibraryStore(context);
        afterProjection.loadCatalog(fixture);
        OctavoLibraryStore.Book persisted =
            afterProjection.findBook(ALPHA_SHA256);
        assertNotNull(persisted);
        assertEquals("Reader validated projection", persisted.title);
        assertEquals(lastOpened, persisted.lastOpenedTime);
        assertFalse(persisted.identityVerified);
    }

    @Test
    public void validatedTransferredProjectionCreatesMissingCatalogRow()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_validated_transfer.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        File managed = store.managedFile(ALPHA_SHA256);
        copyFile(alpha, managed);

        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        OctavoLibraryStore.Book pending = prepared.book;
        assertNotNull(pending);
        assertEquals(null, store.findBook(ALPHA_SHA256));
        assertFalse(pending.identityVerified);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.COMPLETED,
            store.verifyAndRecordTransferredBookStep(
                pending, ALPHA_TITLE, 4 * 1024 * 1024));
        OctavoLibraryStore.Book created = store.findBook(ALPHA_SHA256);
        assertNotNull(created);
        assertTrue(created.identityVerified);
        assertEquals(0, created.lastOpenedTime);
        assertEquals(ALPHA_TITLE, created.title);

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertNotNull(reopened.findBook(ALPHA_SHA256));
        assertEquals(ALPHA_TITLE, reopened.findBook(ALPHA_SHA256).title);
        assertEquals(0, reopened.findBook(ALPHA_SHA256).lastOpenedTime);
    }

    @Test
    public void transferredProjectionAlwaysRequiresFreshOwnedDigestProof()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_freshness.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.Book staged =
            store.importDocument(Uri.fromFile(alpha));
        assertTrue(store.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            store.publishReader0ValidatedImport(staged);
        assertTrue(store.recordOpened(managed, ALPHA_TITLE));
        assertTrue(store.completeImportedCatalogAssociation(managed));
        OctavoLibraryStore.Book current = store.findBook(ALPHA_SHA256);
        assertNotNull(current);
        assertTrue(current.identityVerified);

        try (RandomAccessFile mutation =
                 new RandomAccessFile(current.file, "rw")) {
            int first = mutation.read();
            assertTrue(first >= 0);
            mutation.seek(0);
            mutation.write(first ^ 0x01);
            mutation.getFD().sync();
        }
        assertEquals(ALPHA_BYTE_COUNT, current.file.length());
        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        OctavoLibraryStore.Book capability = prepared.book;
        assertTrue(current == capability);
        assertFalse(capability.identityVerified);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.FAILED,
            store.verifyBookIdentityStep(capability, 4 * 1024 * 1024));
        assertTrue(current.repairRequired);
        assertEquals(OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR,
                     store.loadStatus());
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.STALE,
            store.verifyAndRecordTransferredBookStep(
                capability, ALPHA_TITLE, 4 * 1024 * 1024));

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertTrue(reopened.findBook(ALPHA_SHA256).repairRequired);
    }

    @Test
    public void transientTransferredCapabilityRejectsMutationForgeryAndReuse()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_capability.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.LoadStatus originalStatus = store.loadStatus();
        String originalError = store.lastError();
        File managed = store.managedFile(ALPHA_SHA256);
        copyFile(alpha, managed);

        OctavoLibraryStore.Book forged = new OctavoLibraryStore.Book(
            managed, ALPHA_SHA256, ALPHA_BYTE_COUNT,
            true, false, true, ALPHA_TITLE, 0, 0, false, 0, 0);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.STALE,
            store.verifyAndRecordTransferredBookStep(
                forged, ALPHA_TITLE, 4 * 1024 * 1024));
        assertEquals(null, store.findBook(ALPHA_SHA256));

        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        OctavoLibraryStore.Book capability = prepared.book;
        assertNotNull(capability);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.VERIFIED,
            store.verifyBookIdentityStep(capability, 4 * 1024 * 1024));
        long verifiedModified = managed.lastModified();
        try (RandomAccessFile mutation = new RandomAccessFile(managed, "rw")) {
            mutation.seek(managed.length() - 1);
            int last = mutation.read();
            assertTrue(last >= 0);
            mutation.seek(managed.length() - 1);
            mutation.write(last ^ 0x01);
            mutation.getFD().sync();
        }
        assertTrue(managed.setLastModified(
            Math.max(System.currentTimeMillis(), verifiedModified + 2000)));
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.IDENTITY_FAILED,
            store.verifyAndRecordTransferredBookStep(
                capability, ALPHA_TITLE, 4 * 1024 * 1024));
        assertFalse(capability.identityVerified);
        assertEquals(null, store.findBook(ALPHA_SHA256));
        assertEquals(originalStatus, store.loadStatus());
        assertEquals(originalError, store.lastError());
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.STALE,
            store.verifyAndRecordTransferredBookStep(
                capability, ALPHA_TITLE, 4 * 1024 * 1024));
    }

    @Test
    public void failedTransferredProjectionPublishInvalidatesCapability()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_publish.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        File managed = store.managedFile(ALPHA_SHA256);
        copyFile(alpha, managed);
        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        OctavoLibraryStore.Book capability = prepared.book;
        assertNotNull(capability);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.VERIFIED,
            store.verifyBookIdentityStep(capability, 4 * 1024 * 1024));

        File temporary = new File(
            store.catalogFileForTesting().getParentFile(), "library.v1.tmp");
        assertTrue(temporary.mkdir());
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.CATALOG_RETRY,
            store.verifyAndRecordTransferredBookStep(
                capability, ALPHA_TITLE, 4 * 1024 * 1024));
        assertFalse(capability.identityVerified);
        assertEquals(null, store.findBook(ALPHA_SHA256));
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.STALE,
            store.verifyAndRecordTransferredBookStep(
                capability, ALPHA_TITLE, 4 * 1024 * 1024));
        assertTrue(!temporary.exists() || temporary.delete());
    }

    @Test
    public void transientTransferredDigestFailureNeverCreatesO6Repair()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_transient_bad.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.LoadStatus originalStatus = store.loadStatus();
        File managed = store.managedFile(ALPHA_SHA256);
        copyFile(alpha, managed);
        try (RandomAccessFile mutation = new RandomAccessFile(managed, "rw")) {
            int first = mutation.read();
            assertTrue(first >= 0);
            mutation.seek(0);
            mutation.write(first ^ 0x01);
            mutation.getFD().sync();
        }

        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.IDENTITY_FAILED,
            store.verifyAndRecordTransferredBookStep(
                prepared.book, ALPHA_TITLE, 4 * 1024 * 1024));
        assertEquals(null, store.findBook(ALPHA_SHA256));
        assertEquals(1, store.bookCount());
        assertEquals(originalStatus, store.loadStatus());
        assertEquals("The transferred EPUB failed digest verification",
                     store.lastError());

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertEquals(null, reopened.findBook(ALPHA_SHA256));
        assertEquals(1, reopened.bookCount());
        assertFalse(reopened.loadStatus()
                    == OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR);
    }

    @Test
    public void transferredDigestLeaseRejectsMutationBetweenBoundedSlices()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.LoadStatus originalStatus = store.loadStatus();

        File source = new File(
            store.documentDirectoryForTesting(),
            "bounded-transfer-lease-source.epub");
        byte[] block = new byte[64 * 1024];
        for (int index = 0; index < block.length; ++index) {
            block[index] = (byte)(index * 29 + 13);
        }
        long byteCount = 9L * 1024L * 1024L;
        try (FileOutputStream output = new FileOutputStream(source, false)) {
            for (long written = 0; written < byteCount;
                 written += block.length) {
                output.write(block);
            }
            output.getFD().sync();
        }
        String digest = sha256(source);
        File managed = store.managedFile(digest);
        assertTrue(source.renameTo(managed));

        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(digest, byteCount);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.PENDING,
            store.verifyAndRecordTransferredBookStep(
                prepared.book, "Bounded transfer lease",
                4 * 1024 * 1024));
        long openingModified = managed.lastModified();
        try (RandomAccessFile mutation = new RandomAccessFile(managed, "rw")) {
            mutation.seek(1024);
            int prior = mutation.read();
            assertTrue(prior >= 0);
            mutation.seek(1024);
            mutation.write(prior ^ 0x01);
            mutation.getFD().sync();
        }
        assertTrue(managed.setLastModified(Math.max(
            System.currentTimeMillis() + 2000,
            openingModified + 2000)));
        assertNotEquals(openingModified, managed.lastModified());

        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.IDENTITY_FAILED,
            store.verifyAndRecordTransferredBookStep(
                prepared.book, "Bounded transfer lease",
                4 * 1024 * 1024));
        assertEquals(null, store.findBook(digest));
        assertEquals(1, store.bookCount());
        assertEquals(originalStatus, store.loadStatus());
        assertEquals("The transferred EPUB failed digest verification",
                     store.lastError());
    }

    @Test
    public void uncertainRepairPublicationPreservesBlockAndReloadsRepair()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_repair_uncertain.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.Book current =
            importAndAssociate(store, alpha, ALPHA_TITLE);
        assertNotNull(current);
        try (RandomAccessFile mutation =
                 new RandomAccessFile(current.file, "rw")) {
            mutation.seek(current.file.length() - 1);
            int last = mutation.read();
            assertTrue(last >= 0);
            mutation.seek(current.file.length() - 1);
            mutation.write(last ^ 0x01);
            mutation.getFD().sync();
        }

        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        store.failNextCatalogMoveAfterReplaceForTesting();
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.CATALOG_RETRY,
            store.verifyAndRecordTransferredBookStep(
                prepared.book, ALPHA_TITLE, 4 * 1024 * 1024));
        assertTrue(current.repairRequired);
        assertFalse(current.identityVerified);
        assertEquals(
            OctavoLibraryStore.LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
            store.loadStatus());
        assertEquals("The local Library publication outcome is uncertain",
                     store.lastError());
        byte[] uncertainCandidate = Files.readAllBytes(
            store.catalogFileForTesting().toPath());

        OctavoLibraryStore.TransferredBookOutcome blocked =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.CATALOG_BLOCKED,
                     blocked.status);
        assertEquals(null, blocked.book);
        assertEquals("The local Library publication outcome is uncertain",
                     store.lastError());
        assertTrue(Arrays.equals(
            uncertainCandidate,
            Files.readAllBytes(store.catalogFileForTesting().toPath())));

        store.reloadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR,
                     store.loadStatus());
        assertNotNull(store.findBook(ALPHA_SHA256));
        assertTrue(store.findBook(ALPHA_SHA256).repairRequired);
        assertTrue(Arrays.equals(
            uncertainCandidate,
            Files.readAllBytes(store.catalogFileForTesting().toPath())));

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.LOADED_WITH_REPAIR,
                     reopened.loadStatus());
        assertNotNull(reopened.findBook(ALPHA_SHA256));
        assertTrue(reopened.findBook(ALPHA_SHA256).repairRequired);
    }

    @Test
    public void identityFailurePreservesPreexistingPublicationBlock()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_block_race.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        OctavoLibraryStore.Book current =
            importAndAssociate(store, alpha, ALPHA_TITLE);
        OctavoLibraryStore.TransferredBookOutcome prepared =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     prepared.status);
        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.PENDING,
            store.verifyBookIdentityStep(prepared.book, 1));

        store.failNextCatalogMoveAfterReplaceForTesting();
        assertFalse(store.savePresented(current, 7, 19));
        assertEquals(
            OctavoLibraryStore.LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
            store.loadStatus());
        String blockedError = store.lastError();
        byte[] blockedCatalog = Files.readAllBytes(
            store.catalogFileForTesting().toPath());
        try (RandomAccessFile mutation =
                 new RandomAccessFile(current.file, "rw")) {
            mutation.seek(current.file.length() - 1);
            int last = mutation.read();
            assertTrue(last >= 0);
            mutation.seek(current.file.length() - 1);
            mutation.write(last ^ 0x01);
            mutation.getFD().sync();
        }

        assertEquals(
            OctavoLibraryStore.IdentityCheckStatus.FAILED,
            store.verifyBookIdentityStep(
                prepared.book, 4 * 1024 * 1024));
        assertFalse(current.identityVerified);
        assertTrue(current.repairRequired);
        assertEquals(
            OctavoLibraryStore.LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
            store.loadStatus());
        assertEquals(blockedError, store.lastError());
        assertTrue(Arrays.equals(
            blockedCatalog,
            Files.readAllBytes(store.catalogFileForTesting().toPath())));
    }

    @Test
    public void blockedCatalogNeverIssuesTransferredCapability()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_blocked.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore seed = new OctavoLibraryStore(context);
        File managed = seed.managedFile(ALPHA_SHA256);
        copyFile(alpha, managed);
        byte[] future = new byte[] {
            0x4F, 0x36, 0x4C, 0x42,
            (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF
        };
        try (FileOutputStream output = new FileOutputStream(
                 seed.catalogFileForTesting(), false)) {
            output.write(future);
            output.getFD().sync();
        }

        OctavoLibraryStore blocked = new OctavoLibraryStore(context);
        blocked.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.FUTURE_VERSION_BLOCKED,
                     blocked.loadStatus());
        OctavoLibraryStore.TransferredBookOutcome blockedOutcome =
            blocked.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.CATALOG_BLOCKED,
                     blockedOutcome.status);
        assertEquals(null, blockedOutcome.book);
        assertTrue(Arrays.equals(
            future,
            Files.readAllBytes(blocked.catalogFileForTesting().toPath())));
        OctavoLibraryStore.Book forged = new OctavoLibraryStore.Book(
            managed, ALPHA_SHA256, ALPHA_BYTE_COUNT,
            true, false, true, ALPHA_TITLE, 0, 0, false, 0, 0);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.STALE,
            blocked.verifyAndRecordTransferredBookStep(
                forged, ALPHA_TITLE, 4 * 1024 * 1024));
        assertEquals(OctavoLibraryStore.LoadStatus.FUTURE_VERSION_BLOCKED,
                     blocked.loadStatus());
        assertTrue(Arrays.equals(
            future,
            Files.readAllBytes(blocked.catalogFileForTesting().toPath())));
    }

    @Test
    public void overboundCorruptCatalogPreservesBlockAndNeverIssuesCapability()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_corrupt_block.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        byte[] overbound = new byte[128 * 1024 + 1];
        Arrays.fill(overbound, (byte)0x5A);
        try (FileOutputStream output = new FileOutputStream(
                 store.catalogFileForTesting(), false)) {
            output.write(overbound);
            output.getFD().sync();
        }
        copyFile(alpha, store.managedFile(ALPHA_SHA256));
        store.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.CORRUPT_BLOCKED,
                     store.loadStatus());
        String blockedError = store.lastError();

        OctavoLibraryStore.TransferredBookOutcome outcome =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.CATALOG_BLOCKED,
                     outcome.status);
        assertEquals(null, outcome.book);
        assertEquals(blockedError, store.lastError());
        assertTrue(Arrays.equals(
            overbound,
            Files.readAllBytes(store.catalogFileForTesting().toPath())));

        OctavoLibraryStore.Book forged = new OctavoLibraryStore.Book(
            store.managedFile(ALPHA_SHA256),
            ALPHA_SHA256, ALPHA_BYTE_COUNT,
            true, false, true, ALPHA_TITLE, 0, 0, false, 0, 0);
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.STALE,
            store.verifyAndRecordTransferredBookStep(
                forged, ALPHA_TITLE, 4 * 1024 * 1024));
        assertEquals(OctavoLibraryStore.LoadStatus.CORRUPT_BLOCKED,
                     store.loadStatus());
        assertEquals(blockedError, store.lastError());
        assertTrue(Arrays.equals(
            overbound,
            Files.readAllBytes(store.catalogFileForTesting().toPath())));
    }

    @Test
    public void fullCatalogRejectsNewTransferButAllowsExactExistingProof()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        store.loadCatalog(fixture);
        String firstDigest = null;
        long firstBytes = 0;
        for (int index = 0; index < 63; ++index) {
            byte[] contents = new byte[] {
                (byte)index, (byte)(index * 37 + 11)
            };
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder keyBuilder = new StringBuilder(64);
            for (byte value : digest.digest(contents)) {
                keyBuilder.append(String.format(
                    Locale.ROOT, "%02x", value & 0xFF));
            }
            String key = keyBuilder.toString();
            File managed = store.managedFile(key);
            try (FileOutputStream output =
                     new FileOutputStream(managed, false)) {
                output.write(contents);
                output.getFD().sync();
            }
            assertTrue(store.recordTransferredBook(
                key, contents.length, "Catalog capacity " + index));
            if (index == 0) {
                firstDigest = key;
                firstBytes = contents.length;
            }
        }
        assertEquals(64, store.bookCount());

        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_transfer_full.epub");
        copyFile(alpha, store.managedFile(ALPHA_SHA256));
        OctavoLibraryStore.TransferredBookOutcome fullOutcome =
            store.transferredBookForIdentityVerification(
                ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.CATALOG_FULL,
                     fullOutcome.status);
        assertEquals(null, fullOutcome.book);
        assertEquals(64, store.bookCount());

        OctavoLibraryStore.TransferredBookOutcome existingOutcome =
            store.transferredBookForIdentityVerification(
                firstDigest, firstBytes);
        assertEquals(OctavoLibraryStore.TransferredBookStatus.READY,
                     existingOutcome.status);
        OctavoLibraryStore.Book existing = existingOutcome.book;
        assertNotNull(existing);
        long savesBeforeProof = store.catalogSaveSuccessCountForTesting();
        assertEquals(
            OctavoLibraryStore.TransferredBookStepStatus.COMPLETED,
            store.verifyAndRecordTransferredBookStep(
                existing, existing.title, 4 * 1024 * 1024));
        assertEquals(savesBeforeProof,
                     store.catalogSaveSuccessCountForTesting());
        assertTrue(existing.identityVerified);
        assertEquals(64, store.bookCount());
    }

    @Test
    public void unsignedFutureCatalogIsPreservedAndBlocksMutation()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        File catalog = store.catalogFileForTesting();
        byte[] future;
        try (java.io.ByteArrayOutputStream bytes =
                 new java.io.ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x4F364C42);
            output.writeInt(0x80000000);
            output.flush();
            future = bytes.toByteArray();
        }
        try (FileOutputStream output = new FileOutputStream(catalog, false)) {
            output.write(future);
            output.getFD().sync();
        }

        store.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.FUTURE_VERSION_BLOCKED,
                     store.loadStatus());
        assertEquals(1, store.bookCount());
        assertFalse(store.recordTransferredBook(
            ALPHA_SHA256, ALPHA_BYTE_COUNT, ALPHA_TITLE));
        assertTrue(Arrays.equals(future, Files.readAllBytes(catalog.toPath())));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            assertLibrary(scenario, 1);
            AtomicBoolean opened = new AtomicBoolean(false);
            scenario.onActivity(activity -> opened.set(
                activity.openFixtureForTesting()));
            assertTrue(opened.get());
            assertNativeHealthy(awaitPresented(scenario));
        }
        assertTrue(Arrays.equals(future, Files.readAllBytes(catalog.toPath())));
    }

    @Test
    public void futureCatalogPreservesPendingImportAsReadOnlyEvidence()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File alpha = stageAsset(
            context, ALPHA_ASSET, "octavo_port6_future_pending.epub");
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        File managed = store.managedFile(ALPHA_SHA256);
        copyFile(alpha, managed);
        writeImportJournal(store.importJournalFileForTesting(),
                           ALPHA_SHA256,
                           ALPHA_BYTE_COUNT,
                           2);
        byte[] journal = Files.readAllBytes(
            store.importJournalFileForTesting().toPath());
        byte[] future;
        try (java.io.ByteArrayOutputStream bytes =
                 new java.io.ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x4F364C42);
            output.writeInt(0x80000000);
            output.flush();
            future = bytes.toByteArray();
        }
        try (FileOutputStream output = new FileOutputStream(
                 store.catalogFileForTesting(), false)) {
            output.write(future);
            output.getFD().sync();
        }

        store.loadCatalog(fixture);
        assertEquals(
            OctavoLibraryStore.LoadStatus.IMPORT_RECOVERY_BLOCKED,
            store.loadStatus());
        assertTrue(store.mutationBlocked());
        assertEquals(null, store.pendingImportedBook());
        OctavoLibraryStore.Book retainedPending =
            new OctavoLibraryStore.Book(
                managed, ALPHA_SHA256, ALPHA_BYTE_COUNT,
                true, false, false, "Imported EPUB",
                0, 0, false, 0, 0);
        assertFalse(store.discardPendingImportAssociation(retainedPending));
        assertTrue(Arrays.equals(
            future,
            Files.readAllBytes(store.catalogFileForTesting().toPath())));
        assertTrue(Arrays.equals(
            journal,
            Files.readAllBytes(
                store.importJournalFileForTesting().toPath())));
        assertFixtureIdentity(managed, ALPHA_BYTE_COUNT, ALPHA_SHA256);
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
                assertFalse(store.importStagingFileForTesting().exists());
                assertFalse(store.importJournalFileForTesting().exists());
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
        byte[] invalid = new byte[] {1, 2, 3, 4, 5};
        try (FileOutputStream output =
                 new FileOutputStream(catalog, false)) {
            output.write(invalid);
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
                assertEquals(
                    OctavoLibraryStore.LoadStatus.CORRUPT_QUARANTINED,
                    store.loadStatus());
                assertTrue(store.catalogFileForTesting().isFile());
                assertTrue(Arrays.equals(
                    invalid,
                    readAllBytesUnchecked(
                        store.catalogQuarantineFileForTesting(1))));
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

        OctavoLibraryStore reopened = new OctavoLibraryStore(context);
        reopened.loadCatalog(new File(OctavoFixture.install(context)));
        assertEquals(OctavoLibraryStore.LoadStatus.CORRUPT_QUARANTINED,
                     reopened.loadStatus());
        assertEquals(1, reopened.catalogLoadSuccessCountForTesting());
        assertTrue(Arrays.equals(
            invalid,
            Files.readAllBytes(
                reopened.catalogQuarantineFileForTesting(1).toPath())));
    }

    @Test
    public void corruptCatalogQuarantineIsBoundedAndOverboundInputIsPreserved()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore seed = new OctavoLibraryStore(context);
        File catalog = seed.catalogFileForTesting();

        for (int slot = 1; slot <= 3; ++slot) {
            byte[] invalid = new byte[] {
                (byte)slot, (byte)(slot + 1), (byte)(slot + 2)
            };
            try (FileOutputStream output =
                     new FileOutputStream(catalog, false)) {
                output.write(invalid);
                output.getFD().sync();
            }
            OctavoLibraryStore recovered =
                new OctavoLibraryStore(context);
            recovered.loadCatalog(fixture);
            assertEquals(
                OctavoLibraryStore.LoadStatus.CORRUPT_QUARANTINED,
                recovered.loadStatus());
            assertTrue(recovered.catalogFileForTesting().isFile());
            assertTrue(Arrays.equals(
                invalid,
                Files.readAllBytes(
                    recovered.catalogQuarantineFileForTesting(slot)
                        .toPath())));
        }

        byte[] fourth = new byte[] {9, 8, 7, 6};
        try (FileOutputStream output =
                 new FileOutputStream(catalog, false)) {
            output.write(fourth);
            output.getFD().sync();
        }
        OctavoLibraryStore blocked = new OctavoLibraryStore(context);
        blocked.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.CORRUPT_BLOCKED,
                     blocked.loadStatus());
        assertTrue(Arrays.equals(
            fourth, Files.readAllBytes(catalog.toPath())));
        assertFalse(blocked.recordOpened(
            blocked.fixtureBook(), OctavoFixture.TITLE));
        assertTrue(Arrays.equals(
            fourth, Files.readAllBytes(catalog.toPath())));

        OctavoLibraryStore.clearForTesting(context);
        OctavoLibrarySyncStore.clearForTesting(context);
        OctavoBookTransferStore.clearForTesting(context);
        fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore overboundStore =
            new OctavoLibraryStore(context);
        File overboundCatalog = overboundStore.catalogFileForTesting();
        byte[] overbound = new byte[128 * 1024 + 1];
        Arrays.fill(overbound, (byte)0x5A);
        try (FileOutputStream output =
                 new FileOutputStream(overboundCatalog, false)) {
            output.write(overbound);
            output.getFD().sync();
        }
        overboundStore.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.CORRUPT_BLOCKED,
                     overboundStore.loadStatus());
        assertTrue(Arrays.equals(
            overbound, Files.readAllBytes(overboundCatalog.toPath())));
        for (int slot = 1; slot <= 3; ++slot) {
            assertFalse(overboundStore
                .catalogQuarantineFileForTesting(slot).exists());
        }
    }

    @Test
    public void nonregularQuarantineEvidenceSurvivesMissingCatalog()
        throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File fixture = new File(OctavoFixture.install(context));
        OctavoLibraryStore store = new OctavoLibraryStore(context);
        File evidence = store.catalogQuarantineFileForTesting(1);
        assertTrue(evidence.mkdirs());
        assertFalse(store.catalogFileForTesting().exists());

        store.loadCatalog(fixture);
        assertEquals(OctavoLibraryStore.LoadStatus.CORRUPT_QUARANTINED,
                     store.loadStatus());
        assertTrue(evidence.isDirectory());
        assertTrue(store.catalogFileForTesting().isFile());
        assertTrue(store.lastError().contains("quarantined"));
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
