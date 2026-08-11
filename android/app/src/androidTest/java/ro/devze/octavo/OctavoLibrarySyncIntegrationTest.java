package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibrarySyncIntegrationTest {
    private static final String ALPHA_ASSET =
        "port6/octavo_port6_alpha.epub";
    private static final long ALPHA_BYTE_COUNT = 44_190L;
    private static final String ALPHA_SHA256 =
        "dd92f87fa70ea37f761cb9348d5f7b2939afea2661f9f4fe16828ac6ca041f80";
    private static final String ALPHA_TITLE = "Port 6 Alpha Book";

    private static final String BETA_ASSET =
        "port6/octavo_port6_beta.epub";
    private static final long BETA_BYTE_COUNT = 56_036L;
    private static final String BETA_SHA256 =
        "e0cca3a5283ce0ad3c2c78871b968c2b5e0711ad81e2bbfaaf92bfe3a35cb0a8";

    private Context context;
    private File alpha;
    private File beta;

    private interface ActivityCondition {
        boolean matches(OctavoActivity activity);
    }

    private enum LateCutpoint {
        PREFIX_PUBLISHED,
        BYTES_VERIFIED,
        READER0_VALIDATED,
        MANAGED_PUBLISHED,
        PORT6_ASSOCIATED,
        O1LS_DOWNLOADED,
        LOCAL_CATALOG_LINKED
    }

    @Before
    public void resetStateAndInstallFixtures() throws IOException {
        context = ApplicationProvider.getApplicationContext();
        clearAll(context);
        alpha = stageAsset(context, ALPHA_ASSET,
                           "port11-integration-alpha.epub");
        beta = stageAsset(context, BETA_ASSET,
                          "port11-integration-beta.epub");
        assertFixture(alpha, ALPHA_BYTE_COUNT, ALPHA_SHA256);
        assertFixture(beta, BETA_BYTE_COUNT, BETA_SHA256);
    }

    @After
    public void clearStateAndFixtureCopies() {
        clearAll(context);
        assertTrue(alpha == null || !alpha.exists() || alpha.delete());
        assertTrue(beta == null || !beta.exists() || beta.delete());
    }

    @Test
    public void catalogReviewOfferBackLifecycleAndFocusAreExact()
        throws Exception {
        byte[] catalog = portable(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        byte[] alphaManifest = OctavoBookManifest.build(alpha).encode();
        byte[] betaManifest = OctavoBookManifest.build(beta).encode();

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    betaManifest));
                assertEquals(
                    OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
                    activity.stagePortableLibraryCatalogForTesting(catalog));
                assertNull(activity.libraryCatalogOfferForTesting());
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.CATALOG_APPROVAL);
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    betaManifest));
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.CATALOG_APPROVAL);
                String exact =
                    activity.stagedPortableLibraryDigestForTesting();
                assertNotNull(exact);
                assertEquals(
                    OctavoLibrarySyncStore.PortableMergeResult
                        .STAGED_DIGEST_MISMATCH,
                    activity.approvePortableLibraryCatalogForTesting(
                        ALPHA_SHA256));
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.CATALOG_APPROVAL);
                assertEquals(
                    OctavoLibrarySyncStore.PortableMergeResult.MERGED,
                    activity.approvePortableLibraryCatalogForTesting(exact));
                assertNull(activity.libraryCatalogOfferForTesting());
                assertNull(activity.librarySyncPromptForTesting());
            });

            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    alphaManifest));
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                OctavoLibrarySyncPrompt prompt =
                    activity.librarySyncPromptForTesting();
                assertNotNull(prompt);
                assertTrue(prompt.downloadForTesting().hasFocus());
            });

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
                assertEquals(
                    OctavoLibrarySyncStore.Decision.NONE,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());
            });

            AtomicReference<OctavoLibrarySyncPrompt> stale =
                new AtomicReference<>();
            scenario.onActivity(activity -> {
                stale.set(activity.librarySyncPromptForTesting());
                activity.onBackPressed();
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibrarySyncStore.Decision.DISMISSED_AT_EPOCH,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertNull(activity.libraryCatalogOfferForTesting());
                View add = activity.findViewById(R.id.octavo_library_add);
                assertNotNull(add);
                assertTrue("Back did not restore Library focus to Add EPUB",
                           add.hasFocus());
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());
            });

            assertNotNull(stale.get());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> stale.get().downloadForTesting().performClick());
            scenario.onActivity(activity -> assertEquals(
                0, activity.bookTransferStoreForTesting().intentCount()));

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    alphaManifest));
                assertNull(activity.libraryCatalogOfferForTesting());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.DISMISSED_AT_EPOCH,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertTrue(activity.openFixtureForTesting());
                activity.closeBookForTesting();
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
                activity.librarySyncPromptForTesting()
                    .ignoreForTesting().performClick();
                assertEquals(
                    OctavoLibrarySyncStore.Decision.IGNORED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertTrue(activity.openFixtureForTesting());
                activity.closeBookForTesting();
                assertNull(activity.libraryCatalogOfferForTesting());
            });
        }
    }

    @Test
    public void queueFirstDownloadRecoversUnpublishedPrefixOnlyOnAction()
        throws Exception {
        byte[] catalog = portable(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertEquals(
                    OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
                    activity.stagePortableLibraryCatalogForTesting(catalog));
                String approval =
                    activity.stagedPortableLibraryDigestForTesting();
                assertNotNull(approval);
                assertEquals(
                    OctavoLibrarySyncStore.PortableMergeResult.MERGED,
                    activity.approvePortableLibraryCatalogForTesting(
                        approval));
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
                activity.librarySyncStoreForTesting()
                    .failNextPublishForTesting();
                assertFalse(
                    activity.downloadLibraryCatalogOfferForTesting());

                OctavoBookTransferStore store =
                    activity.bookTransferStoreForTesting();
                OctavoBookTransferStore.ActiveJob active = store.activeJob();
                assertNotNull(active);
                assertEquals(OctavoBookTransferStore.Phase.STAGED,
                             active.phase);
                assertEquals(0, active.completedPrefix);
                assertTrue(store.stateFileForTesting().isFile());
                assertTrue(store.partFileForTesting(
                    active.digest, active.attemptId).isFile());
                assertNull(activity.librarySyncStoreForTesting()
                               .transferReconciliation());
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNull(activity.librarySyncStoreForTesting()
                               .transferReconciliation());
                OctavoBookTransferStore.ActiveJob active =
                    activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(active);
                assertEquals(OctavoBookTransferStore.Phase.STAGED,
                             active.phase);
                assertEquals(0, active.completedPrefix);
                assertTrue(activity.retryLibraryTransferForTesting());
                assertNotNull(activity.librarySyncStoreForTesting()
                                  .transferReconciliation());
                active = activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(active);
                assertEquals(0, active.completedPrefix);
            });

            AtomicReference<File> part = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoBookTransferStore store =
                    activity.bookTransferStoreForTesting();
                OctavoBookTransferStore.ActiveJob active = store.activeJob();
                assertNotNull(active);
                part.set(store.partFileForTesting(
                    active.digest, active.attemptId));
            });
            appendPrefix(part.get(), alpha, 257);

            scenario.recreate();
            scenario.onActivity(activity -> {
                OctavoBookTransferStore store =
                    activity.bookTransferStoreForTesting();
                assertEquals(
                    OctavoBookTransferStore.LoadStatus
                        .RECOVERED_EXTRA_TRUNCATED,
                    store.loadStatus());
                OctavoBookTransferStore.ActiveJob active = store.activeJob();
                assertNotNull(active);
                assertEquals(0, active.completedPrefix);
                assertEquals(0L, store.partFileForTesting(
                    active.digest, active.attemptId).length());
                assertTrue(active.retryRequired);
                assertEquals(
                    OctavoBookTransferStore.Attention.EXTRA_TRUNCATED,
                    active.attention);
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
            });

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            scenario.onActivity(activity -> {
                OctavoBookTransferStore.ActiveJob active =
                    activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(active);
                assertEquals(0, active.completedPrefix);
                assertTrue(active.retryRequired);
                assertEquals(
                    OctavoBookTransferStore.Attention.EXTRA_TRUNCATED,
                    active.attention);
                assertTrue(activity.retryLibraryTransferForTesting());
                active = activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(active);
                assertEquals(0, active.completedPrefix);
            });

            AtomicBoolean accepted = new AtomicBoolean(false);
            try (FileInputStream chunk = new FileInputStream(alpha)) {
                scenario.onActivity(activity -> accepted.set(
                    activity.acceptNextLibraryDownloadChunkForTesting(
                        0, chunk)));
            }
            assertTrue(accepted.get());
            assertSettledDownload(scenario, alpha);
        }
    }

    @Test
    public void everyLateDownloadCrashCutpointFinishesIdempotently()
        throws Exception {
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();
        for (LateCutpoint cutpoint : LateCutpoint.values()) {
            clearAll(context);
            seedLateCutpoint(cutpoint, alpha, manifestBytes);

            OctavoBookTransferStore before =
                new OctavoBookTransferStore(context);
            assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                         before.load());
            assertNotNull(before.activeJob());
            assertEquals(expectedPhase(cutpoint),
                         before.activeJob().phase);

            try (ActivityScenario<OctavoActivity> scenario =
                     ActivityScenario.launch(OctavoActivity.class)) {
                scenario.onActivity(activity -> {
                    assertPromptMode(
                        activity,
                        OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                    assertTrue(
                        "Cutpoint did not resume: " + cutpoint,
                        activity.retryLibraryTransferForTesting());
                });
                assertSettledDownload(scenario, alpha);
            }
        }
    }

    @Test
    public void importJournalPendingAssociationRequiresExplicitRetry()
        throws Exception {
        OctavoLibraryStore library = initializedLibrary(context);
        OctavoLibraryStore.Book staged =
            library.importDocument(Uri.fromFile(alpha));
        assertTrue(library.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            library.publishReader0ValidatedImport(staged);
        assertTrue(library.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));
        assertNull(library.findBook(ALPHA_SHA256));
        assertTrue(library.importJournalFileForTesting().isFile());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryStore.LoadStatus.IMPORT_ASSOCIATION_PENDING,
                    activity.libraryStoreForTesting().loadStatus());
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertTrue(activity.libraryCatalogStatusForTesting()
                               .contains("Retry"));
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryStore.LoadStatus.IMPORT_ASSOCIATION_PENDING,
                    activity.libraryStoreForTesting().loadStatus());
                assertTrue(
                    activity.retryPendingImportAssociationForTesting());
            });

            awaitActivity(
                scenario,
                activity -> {
                    OctavoLibraryStore.Book book =
                        activity.libraryStoreForTesting().findBook(
                            ALPHA_SHA256);
                    return book != null && book.identityVerified
                        && !book.repairRequired
                        && !activity.libraryStoreForTesting()
                            .hasPendingImportAssociation(
                                ALPHA_SHA256, ALPHA_BYTE_COUNT)
                        && activity.librarySyncStoreForTesting().decision(
                            ALPHA_SHA256)
                           == OctavoLibrarySyncStore.Decision.DOWNLOADED;
                },
                "Pending Port 6 import did not finish exact association");

            scenario.onActivity(activity -> {
                OctavoLibraryStore.Book book =
                    activity.libraryStoreForTesting().findBook(
                        ALPHA_SHA256);
                assertNotNull(book);
                assertEquals(ALPHA_TITLE, book.title);
                assertFalse(activity.libraryStoreForTesting()
                                .importJournalFileForTesting().exists());
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());
            });
        }
        assertNotNull(managed);
    }

    @Test
    public void failedPendingImportCanOnlyBeDiscardedExplicitly()
        throws Exception {
        OctavoLibraryStore library = initializedLibrary(context);
        OctavoLibraryStore.Book staged =
            library.importDocument(Uri.fromFile(alpha));
        assertTrue(library.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            library.publishReader0ValidatedImport(staged);
        mutateSameLength(managed.file);
        assertTrue(library.hasPendingImportAssociation(
            ALPHA_SHA256, ALPHA_BYTE_COUNT));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertEquals(
                    "Discard",
                    activity.librarySyncPromptForTesting()
                        .cancelForTesting().getText().toString());
                assertEquals(
                    View.VISIBLE,
                    activity.librarySyncPromptForTesting()
                        .cancelForTesting().getVisibility());
                assertTrue(
                    activity.retryPendingImportAssociationForTesting());
            });
            awaitActivity(
                scenario,
                activity -> activity.libraryImportAssociationStatusForTesting()
                    != null
                    && activity.libraryImportAssociationStatusForTesting()
                        .contains("identity verification failed")
                    && activity.librarySyncPromptForTesting() != null
                    && activity.librarySyncPromptForTesting().modeForTesting()
                       == OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE,
                "Digest-failed pending import did not return to Retry/Discard");

            assertTrue(managed.file.delete());
            assertTrue(managed.file.mkdir());
            File blocker = new File(managed.file, "preserved");
            writeFile(blocker, new byte[] {1, 2, 3});
            scenario.onActivity(activity -> {
                assertFalse(
                    activity.discardPendingImportAssociationForTesting());
                assertNotNull(activity.librarySyncPromptForTesting());
                assertEquals(
                    "Discard",
                    activity.librarySyncPromptForTesting()
                        .cancelForTesting().getText().toString());
                assertTrue(activity.libraryStoreForTesting()
                               .importJournalFileForTesting().isFile());
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryStore.LoadStatus.IMPORT_ASSOCIATION_PENDING,
                    activity.libraryStoreForTesting().loadStatus());
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertEquals(
                    "Discard",
                    activity.librarySyncPromptForTesting()
                        .cancelForTesting().getText().toString());
            });

            assertTrue(blocker.delete());
            assertTrue(managed.file.delete());
            scenario.onActivity(activity -> assertTrue(
                activity.discardPendingImportAssociationForTesting()));
            waitForIdle();
            scenario.onActivity(activity -> {
                assertFalse(activity.libraryStoreForTesting()
                                .importJournalFileForTesting().exists());
                assertFalse(activity.libraryStoreForTesting()
                                .managedFile(ALPHA_SHA256).exists());
                assertNull(activity.librarySyncPromptForTesting());
                View add = activity.findViewById(R.id.octavo_library_add);
                assertNotNull(add);
                assertTrue(add.hasFocus());
            });
        }
    }

    @Test
    public void sameLengthCorruptDownloadedBookUsesRepairCleanupThenDownload()
        throws Exception {
        seedImportedBook(context, alpha, ALPHA_TITLE);
        OctavoLibrarySyncStore sync = new OctavoLibrarySyncStore(context);
        assertTrue(sync.load() == OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY
                       || sync.loadStatus()
                          == OctavoLibrarySyncStore.LoadStatus.LOADED);
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            sync.recordLocalValidated(descriptor(
                ALPHA_SHA256, ALPHA_BYTE_COUNT)));

        OctavoLibraryStore disk = initializedLibrary(context);
        File managed = disk.managedFile(ALPHA_SHA256);
        assertNotNull(managed);
        mutateSameLength(managed);
        assertEquals(ALPHA_BYTE_COUNT, managed.length());
        assertFalse(ALPHA_SHA256.equals(sha256(managed)));
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                OctavoLibraryStore.Book book =
                    activity.libraryStoreForTesting().findBook(
                        ALPHA_SHA256);
                assertNotNull(book);
                assertFalse(book.identityVerified);
                assertFalse(activity.libraryStoreForTesting()
                                .verifyBookIdentity(book));
                assertTrue(book.repairRequired);
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertTrue(activity.libraryCatalogStatusForTesting()
                               .contains("repair"));
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                OctavoLibraryStore.Book book =
                    activity.libraryStoreForTesting().findBook(
                        ALPHA_SHA256);
                assertNotNull(book);
                assertTrue(book.repairRequired);
                activity.librarySyncPromptForTesting()
                    .retryForTesting().performClick();
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertFalse(activity.libraryStoreForTesting()
                                .managedFile(ALPHA_SHA256).exists());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.NONE,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertTrue(activity.bookTransferStoreForTesting()
                               .cleanupJobs().isEmpty());
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
                assertTrue(activity.downloadLibraryCatalogOfferForTesting());
            });

            AtomicBoolean accepted = new AtomicBoolean(false);
            try (FileInputStream chunk = new FileInputStream(alpha)) {
                scenario.onActivity(activity -> accepted.set(
                    activity.acceptNextLibraryDownloadChunkForTesting(
                        0, chunk)));
            }
            assertTrue(accepted.get());
            assertSettledDownload(scenario, alpha);
        }
    }

    @Test
    public void localPublicationAndRemovalMarkersRecoverWithoutDeadlock()
        throws Exception {
        seedImportedBook(context, alpha, ALPHA_TITLE);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertNull(activity.librarySyncStoreForTesting().decision(
                    ALPHA_SHA256));
                assertNull(activity.librarySyncStoreForTesting()
                               .localReconciliation());
                assertTrue(activity.removeBookForTesting(ALPHA_SHA256));
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertNull(activity.librarySyncStoreForTesting().decision(
                    ALPHA_SHA256));
                assertNull(activity.librarySyncStoreForTesting()
                               .localReconciliation());
                assertTrue(activity.bookTransferStoreForTesting()
                               .cleanupJobs().isEmpty());
            });
        }

        clearAll(context);
        seedImportedBook(context, alpha, ALPHA_TITLE);
        OctavoLibrarySyncStore publication =
            new OctavoLibrarySyncStore(context);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     publication.load());
        ArrayList<OctavoLibraryPortable.Descriptor> full =
            new ArrayList<>();
        for (int index = 1;
             index <= OctavoLibraryPortable.maximumRecordCount();
             ++index) {
            full.add(descriptor(digest(index), index));
        }
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
            publication.stagePortableBytes(
                OctavoLibraryPortable.simulatedRemoteBytes(full)));
        assertEquals(
            OctavoLibrarySyncStore.PortableMergeResult.MERGED,
            publication.approveStagedPortable(
                publication.stagedPortable().sha256));
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            publication.stageLocalPublication(descriptor(
                ALPHA_SHA256, ALPHA_BYTE_COUNT)));
        OctavoLibrarySyncStore.LocalReconciliation fullPending =
            publication.localReconciliation();
        assertNotNull(fullPending);
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.LIMIT,
            publication.finalizeLocalReconciliation(fullPending, true));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertEquals(
                    "Remove from this device",
                    activity.librarySyncPromptForTesting()
                        .cancelForTesting().getText().toString());
                assertEquals(
                    View.VISIBLE,
                    activity.librarySyncPromptForTesting()
                        .cancelForTesting().getVisibility());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotNull(activity.librarySyncStoreForTesting()
                                  .localReconciliation());
                assertTrue(activity.librarySyncPromptForTesting()
                               .cancelForTesting().performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertNull(activity.librarySyncStoreForTesting()
                               .localReconciliation());
                assertTrue(activity.bookTransferStoreForTesting()
                               .cleanupJobs().isEmpty());
                assertNull(activity.librarySyncStoreForTesting().decision(
                    ALPHA_SHA256));
                View add = activity.findViewById(R.id.octavo_library_add);
                assertNotNull(add);
                assertTrue(add.hasFocus());
            });
        }

        clearAll(context);
        seedImportedBook(context, alpha, ALPHA_TITLE);
        OctavoLibrarySyncStore removal =
            new OctavoLibrarySyncStore(context);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     removal.load());
        OctavoLibraryPortable.Descriptor exact = descriptor(
            ALPHA_SHA256, ALPHA_BYTE_COUNT);
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            removal.recordLocalValidated(exact));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                activity.librarySyncStoreForTesting()
                    .failNextPublishForTesting();
                assertTrue(activity.removeBookForTesting(ALPHA_SHA256));
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertFalse(activity.libraryStoreForTesting()
                                .managedFile(ALPHA_SHA256).exists());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.DOWNLOADED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertNull(activity.librarySyncStoreForTesting()
                               .localReconciliation());
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting()
                        .cleanupJobs().size());
                assertEquals(
                    OctavoBookTransferStore.CleanupPhase
                        .AWAITING_SYNC_SUPPRESSION,
                    activity.bookTransferStoreForTesting()
                        .cleanupJobs().get(0).phase);
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
            });
            scenario.recreate();
            scenario.onActivity(activity -> activity
                .librarySyncPromptForTesting()
                .retryForTesting().performClick());
            scenario.onActivity(activity -> {
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertFalse(activity.libraryStoreForTesting()
                                .managedFile(ALPHA_SHA256).exists());
                assertNull(activity.librarySyncStoreForTesting()
                               .localReconciliation());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.LOCAL_REMOVED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertTrue(activity.bookTransferStoreForTesting()
                               .cleanupJobs().isEmpty());
            });
        }

        clearAll(context);
        seedImportedBook(context, alpha, ALPHA_TITLE);
        OctavoLibrarySyncStore unrelated =
            new OctavoLibrarySyncStore(context);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     unrelated.load());
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            unrelated.stageLocalPublication(descriptor(
                BETA_SHA256, BETA_BYTE_COUNT)));
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.removeBookForTesting(ALPHA_SHA256));
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertFalse(activity.libraryStoreForTesting()
                                .managedFile(ALPHA_SHA256).exists());
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting()
                        .cleanupJobs().size());
                assertEquals(
                    OctavoBookTransferStore.CleanupPhase
                        .AWAITING_SYNC_SUPPRESSION,
                    activity.bookTransferStoreForTesting()
                        .cleanupJobs().get(0).phase);
                OctavoLibrarySyncStore.LocalReconciliation pending =
                    activity.librarySyncStoreForTesting()
                        .localReconciliation();
                assertNotNull(pending);
                assertEquals(BETA_SHA256, pending.digest);
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                OctavoLibrarySyncStore.LocalReconciliation pending =
                    activity.librarySyncStoreForTesting()
                        .localReconciliation();
                assertNotNull(pending);
                assertEquals(BETA_SHA256, pending.digest);
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting()
                        .cleanupJobs().size());
                assertTrue(activity.librarySyncPromptForTesting()
                               .retryForTesting().performClick());
                assertNotNull(activity.librarySyncStoreForTesting()
                                  .localReconciliation());
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting()
                        .cleanupJobs().size());
            });
        }
    }

    @Test
    public void suppressedDecisionsNeedExactReviewManifestEpochAndQueue()
        throws Exception {
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();
        byte[] betaManifestBytes = OctavoBookManifest.build(beta).encode();
        OctavoLibrarySyncStore ignored = seedApprovedCatalog(
            context, ALPHA_SHA256, ALPHA_BYTE_COUNT, true);
        List<OctavoLibrarySyncStore.Candidate> candidates =
            ignored.reviewCandidates(Collections.emptyList());
        assertEquals(1, candidates.size());
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            ignored.ignore(candidates.get(0)));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertEquals(
                    OctavoLibrarySyncStore.Decision.IGNORED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertNull(activity.libraryCatalogOfferForTesting());
                Button review = findButton(
                    activity, "Review synchronized EPUB");
                assertNotNull(review);
                assertSuppressedActionLargeTextLayout(activity, review);
                assertTrue(review.performClick());
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertTrue(activity.libraryCatalogStatusForTesting()
                               .contains("available for Download again"));

                assertEquals(
                    OctavoLibrarySyncStore.MutationResult.UPDATED,
                    activity.librarySyncStoreForTesting()
                        .beginReviewEpoch(true));
                assertTrue(activity.librarySyncPromptForTesting()
                               .retryForTesting().performClick());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.IGNORED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                View add = activity.findViewById(R.id.octavo_library_add);
                assertNotNull(add);
                assertTrue(add.hasFocus());

                Button review = findButton(
                    activity, "Review synchronized EPUB");
                assertNotNull(review);
                assertTrue(review.performClick());
                OctavoLibrarySyncPrompt staleManifest =
                    activity.librarySyncPromptForTesting();
                assertNotNull(staleManifest);
                assertTrue(activity.setAvailableBookManifestForTesting(
                    betaManifestBytes));
                assertTrue(staleManifest.retryForTesting().performClick());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.IGNORED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());

                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                review = findButton(activity, "Review synchronized EPUB");
                assertNotNull(review);
                assertTrue(review.performClick());
                OctavoBookTransferStore.StageOutcome staged =
                    activity.bookTransferStoreForTesting()
                        .stageDownload(manifestBytes);
                assertTrue(staged.result.succeeded());
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting().intentCount());
                assertTrue(activity.librarySyncPromptForTesting()
                               .retryForTesting().performClick());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.IGNORED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting().intentCount());
                assertTrue(activity.cancelLibraryTransferForTesting());
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());

                review = findButton(activity, "Review synchronized EPUB");
                assertNotNull(review);
                assertTrue(review.performClick());
                assertTrue(activity.librarySyncPromptForTesting()
                               .retryForTesting().performClick());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.NONE,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);

                String installedManifest =
                    activity.libraryCatalogOfferManifestSha256ForTesting();
                assertNotNull(installedManifest);
                assertEquals(sha256(manifestBytes),
                             installedManifest);
                assertEquals(
                    OctavoLibrarySyncStore.MutationResult.UPDATED,
                    activity.librarySyncStoreForTesting()
                        .beginReviewEpoch(true));
                assertFalse(activity.downloadLibraryCatalogOfferForTesting());
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());

                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
                OctavoLibrarySyncPrompt staleOffer =
                    activity.librarySyncPromptForTesting();
                assertTrue(activity.setAvailableBookManifestForTesting(
                    betaManifestBytes));
                assertTrue(staleOffer.downloadForTesting().performClick());
                assertEquals(
                    0,
                    activity.bookTransferStoreForTesting().intentCount());

                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
                staged = activity.bookTransferStoreForTesting()
                    .stageDownload(manifestBytes);
                assertTrue(staged.result.succeeded());
                assertFalse(activity.downloadLibraryCatalogOfferForTesting());
                assertEquals(
                    1,
                    activity.bookTransferStoreForTesting().intentCount());
                assertTrue(activity.cancelLibraryTransferForTesting());
            });
        }

        clearAll(context);
        OctavoLibrarySyncStore removed = seedApprovedCatalog(
            context, ALPHA_SHA256, ALPHA_BYTE_COUNT, true);
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            removed.recordLocalValidated(descriptor(
                ALPHA_SHA256, ALPHA_BYTE_COUNT)));
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            removed.recordLocalRemoval(ALPHA_SHA256));
        assertEquals(OctavoLibrarySyncStore.Decision.LOCAL_REMOVED,
                     removed.decision(ALPHA_SHA256));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                Button review = findButton(
                    activity, "Review synchronized EPUB");
                assertNotNull(review);
                assertTrue(review.performClick());
                assertTrue(activity.libraryCatalogStatusForTesting()
                               .contains("available for Download again"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertEquals(
                    OctavoLibrarySyncStore.Decision.LOCAL_REMOVED,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                Button review = findButton(
                    activity, "Review synchronized EPUB");
                assertNotNull(review);
                assertTrue(review.performClick());
                assertTrue(activity.librarySyncPromptForTesting()
                               .retryForTesting().performClick());
                assertEquals(
                    OctavoLibrarySyncStore.Decision.NONE,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                assertExactOffer(activity, ALPHA_SHA256, ALPHA_BYTE_COUNT);
            });
        }
    }

    @Test
    public void futureCorruptAndQuarantineEvidenceBlockEveryOffer()
        throws Exception {
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();

        seedApprovedCatalog(context, ALPHA_SHA256, ALPHA_BYTE_COUNT, false);
        OctavoBookTransferStore futureManifest =
            new OctavoBookTransferStore(context);
        assertEquals(OctavoBookTransferStore.LoadStatus.MISSING_EMPTY,
                     futureManifest.load());
        byte[] future = new byte[29];
        writeInt(future, 0, OctavoBookManifest.magicForTesting());
        writeInt(future, 4, OctavoBookManifest.versionForTesting() + 1);
        assertEquals(
            OctavoBookTransferStore.MutationResult.FUTURE_MANIFEST_RETAINED,
            futureManifest.stageDownload(future).result);
        assertBlockedOffer(
            manifestBytes,
            OctavoBookTransferStore.LoadStatus.LOADED,
            "newer book manifest");

        clearAll(context);
        seedApprovedCatalog(context, ALPHA_SHA256, ALPHA_BYTE_COUNT, false);
        OctavoBookTransferStore corrupt =
            new OctavoBookTransferStore(context);
        assertEquals(OctavoBookTransferStore.LoadStatus.MISSING_EMPTY,
                     corrupt.load());
        byte[] malformed = new byte[32];
        writeInt(malformed, 0,
                 OctavoBookTransferStore.storeMagicForTesting());
        writeInt(malformed, 4,
                 OctavoBookTransferStore.storeVersionForTesting());
        writeFile(corrupt.stateFileForTesting(), malformed);
        assertBlockedOffer(
            manifestBytes,
            OctavoBookTransferStore.LoadStatus.CORRUPT_QUARANTINED,
            "quarantined book-transfer state");

        clearAll(context);
        seedApprovedCatalog(context, ALPHA_SHA256, ALPHA_BYTE_COUNT, false);
        OctavoBookTransferStore blocked =
            new OctavoBookTransferStore(context);
        assertEquals(OctavoBookTransferStore.LoadStatus.MISSING_EMPTY,
                     blocked.load());
        for (int slot = 1; slot <= 3; ++slot) {
            writeFile(blocked.quarantineFileForTesting(slot),
                      new byte[] {(byte)slot});
        }
        writeFile(blocked.stateFileForTesting(), malformed);
        assertBlockedOffer(
            manifestBytes,
            OctavoBookTransferStore.LoadStatus.CORRUPT_BLOCKED,
            "could not be quarantined");

        clearAll(context);
        initializedLibrary(context);
        OctavoLibrarySyncStore futureSync =
            new OctavoLibrarySyncStore(context);
        byte[] futureState = new byte[16];
        writeInt(futureState, 0,
                 OctavoLibrarySyncStore.storeMagicForTesting());
        writeInt(futureState, 4,
                 OctavoLibrarySyncStore.storeVersionForTesting() + 1);
        writeFile(futureSync.stateFileForTesting(), futureState);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertEquals(
                    OctavoLibrarySyncStore.LoadStatus.FUTURE_VERSION_BLOCKED,
                    activity.librarySyncStoreForTesting().loadStatus());
                assertNull(activity.libraryCatalogOfferForTesting());
                assertPromptMode(
                    activity,
                    OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertTrue(rootText(activity).contains("newer version"));
                assertArrayEquals(
                    futureState,
                    readFileUnchecked(activity.librarySyncStoreForTesting()
                                          .stateFileForTesting()));
            });
        }
    }

    @Test
    public void backDefersTransferAndCancelsHiddenIdentityCompletion()
        throws Exception {
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();
        seedLateCutpoint(
            LateCutpoint.MANAGED_PUBLISHED, alpha, manifestBytes);

        OctavoBookTransferStore seeded = new OctavoBookTransferStore(context);
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     seeded.load());
        OctavoBookTransferStore.ActiveJob expected = seeded.activeJob();
        assertNotNull(expected);
        long expectedSequence = expected.attemptSequence;
        String expectedAttemptId = expected.attemptId;
        byte[] durableBefore = readFileUnchecked(seeded.stateFileForTesting());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertPromptMode(
                    activity, OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertTrue(activity.retryLibraryTransferForTesting());
                assertPromptMode(
                    activity, OctavoLibrarySyncPrompt.Mode.WORKING);

                // Back runs before the already-posted bounded identity slice.
                // It must revoke the capability and expose ordinary Library
                // workflows without advancing any durable transfer boundary.
                activity.onBackPressed();
                assertNull(activity.librarySyncPromptForTesting());
                View add = activity.findViewById(R.id.octavo_library_add);
                View list = activity.findViewById(R.id.octavo_library_list);
                assertNotNull(add);
                assertNotNull(list);
                assertTrue(add.isEnabled());
                assertNotNull(findButton(
                    activity, "Review pending Library attention"));
            });
            waitForIdle();

            scenario.onActivity(activity -> {
                OctavoBookTransferStore.ActiveJob current =
                    activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(current);
                assertEquals(expectedSequence, current.attemptSequence);
                assertEquals(expectedAttemptId, current.attemptId);
                assertEquals(OctavoBookTransferStore.Phase.MANAGED_PUBLISHED,
                             current.phase);
                assertArrayEquals(
                    durableBefore,
                    readFileUnchecked(activity.bookTransferStoreForTesting()
                                          .stateFileForTesting()));
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));
                assertEquals(
                    OctavoLibrarySyncStore.Decision.NONE,
                    activity.librarySyncStoreForTesting().decision(
                        ALPHA_SHA256));
                View add = activity.findViewById(R.id.octavo_library_add);
                assertNotNull(add);
                assertTrue(add.hasFocus());
                Button review = findButton(
                    activity, "Review pending Library attention");
                assertNotNull(review);
                assertTrue(review.performClick());
            });
            waitForIdle();

            scenario.onActivity(activity -> {
                assertPromptMode(
                    activity, OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE);
                assertTrue(activity.librarySyncPromptForTesting()
                               .retryForTesting().hasFocus());
                OctavoBookTransferStore.ActiveJob current =
                    activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(current);
                assertEquals(expectedSequence, current.attemptSequence);
                assertEquals(expectedAttemptId, current.attemptId);
            });
        }
    }

    @Test
    public void sameSessionO6UncertaintyReloadsBeforeTransferRetry()
        throws Exception {
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();
        seedLateCutpoint(
            LateCutpoint.MANAGED_PUBLISHED, alpha, manifestBytes);

        OctavoBookTransferStore seeded = new OctavoBookTransferStore(context);
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     seeded.load());
        OctavoBookTransferStore.ActiveJob expected = seeded.activeJob();
        assertNotNull(expected);
        long expectedSequence = expected.attemptSequence;
        String expectedAttemptId = expected.attemptId;

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                activity.libraryStoreForTesting()
                    .failNextCatalogMoveAfterReplaceForTesting();
                assertTrue(activity.retryLibraryTransferForTesting());
                assertPromptMode(
                    activity, OctavoLibrarySyncPrompt.Mode.WORKING);
            });

            awaitActivity(
                scenario,
                activity -> activity.libraryStoreForTesting().loadStatus()
                        == OctavoLibraryStore.LoadStatus
                            .PUBLISH_UNCERTAIN_BLOCKED
                    && activity.libraryTransferExplicitRetryRequiredForTesting(),
                "O6 post-replace uncertainty was not retained for Retry");
            scenario.onActivity(activity -> {
                OctavoBookTransferStore.ActiveJob current =
                    activity.bookTransferStoreForTesting().activeJob();
                assertNotNull(current);
                assertEquals(expectedSequence, current.attemptSequence);
                assertEquals(expectedAttemptId, current.attemptId);
                assertEquals(OctavoBookTransferStore.Phase.MANAGED_PUBLISHED,
                             current.phase);
                assertNull(activity.libraryStoreForTesting().findBook(
                    ALPHA_SHA256));

                // This is intentionally the same Activity instance. Retry
                // must reload O6's candidate/prior outcome before resuming the
                // exact retained O1BQ attempt.
                assertTrue(activity.retryLibraryTransferForTesting());
            });
            awaitActivity(
                scenario,
                activity -> activity.bookTransferStoreForTesting()
                                .intentCount() == 0
                    && activity.librarySyncStoreForTesting()
                           .transferReconciliation() == null
                    && activity.libraryStoreForTesting().findBook(
                           ALPHA_SHA256) != null,
                "Same-session O6 uncertainty Retry did not settle");
            assertSettledDownload(scenario, alpha);
        }
    }

    @Test
    public void lateManagedDamageConvertsExactAttemptToRepairCleanup()
        throws Exception {
        byte[] manifestBytes = OctavoBookManifest.build(alpha).encode();
        for (LateCutpoint cutpoint : new LateCutpoint[] {
                 LateCutpoint.MANAGED_PUBLISHED,
                 LateCutpoint.LOCAL_CATALOG_LINKED
             }) {
            clearAll(context);
            seedLateCutpoint(cutpoint, alpha, manifestBytes);

            OctavoBookTransferStore seeded =
                new OctavoBookTransferStore(context);
            assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                         seeded.load());
            OctavoBookTransferStore.ActiveJob expected = seeded.activeJob();
            assertNotNull(expected);
            long expectedSequence = expected.attemptSequence;
            String expectedAttemptId = expected.attemptId;
            byte[] expectedManifestHash = expected.manifestHash();
            File managed = new File(
                initializedLibrary(context).documentDirectoryForTesting(),
                ALPHA_SHA256 + ".epub");
            assertTrue(managed.isFile());
            mutateSameLength(managed);
            assertFalse(ALPHA_SHA256.equals(sha256(managed)));

            try (ActivityScenario<OctavoActivity> scenario =
                     ActivityScenario.launch(OctavoActivity.class)) {
                scenario.onActivity(activity -> assertTrue(
                    activity.retryLibraryTransferForTesting()));
                awaitActivity(
                    scenario,
                    activity -> !activity.bookTransferStoreForTesting()
                                    .cleanupJobs().isEmpty(),
                    "Damaged late transfer did not convert at " + cutpoint);
                scenario.onActivity(activity -> assertExactRepairCleanup(
                    activity, expectedSequence, expectedAttemptId,
                    expectedManifestHash, ALPHA_SHA256, ALPHA_BYTE_COUNT));

                // The cleanup, including its origin proof, must survive a
                // recreation and no damaged bytes may be reported complete.
                scenario.recreate();
                scenario.onActivity(activity -> {
                    assertExactRepairCleanup(
                        activity, expectedSequence, expectedAttemptId,
                        expectedManifestHash,
                        ALPHA_SHA256, ALPHA_BYTE_COUNT);
                    assertTrue(new File(
                        activity.libraryStoreForTesting()
                            .documentDirectoryForTesting(),
                        ALPHA_SHA256 + ".epub").isFile());
                    assertFalse(activity.libraryStoreForTesting()
                                    .verifyManagedFile(
                                        ALPHA_SHA256, ALPHA_BYTE_COUNT));
                    OctavoLibraryStore.Book local =
                        activity.libraryStoreForTesting().findBook(
                            ALPHA_SHA256);
                    if (cutpoint == LateCutpoint.MANAGED_PUBLISHED) {
                        assertNull(local);
                    } else {
                        assertNotNull(local);
                        assertFalse(local.identityVerified);
                    }
                });
            }
        }
    }

    @Test
    public void mutationBetweenFusedSlicesNeverAssociatesStaleBytes()
        throws Exception {
        File large = new File(
            context.getCacheDir(), "port11-integration-large.epub");
        try {
            buildLargeValidEpub(alpha, large);
            assertTrue(large.length() > 4L * 1024L * 1024L);
            byte[] manifestBytes = OctavoBookManifest.build(large).encode();
            OctavoBookManifest.DecodeResult decoded =
                OctavoBookManifest.decode(manifestBytes);
            assertEquals(OctavoBookManifest.DecodeStatus.READY,
                         decoded.status);
            OctavoBookManifest manifest = decoded.manifest();
            assertTrue(manifest.chunkCount > 1);
            seedLateCutpoint(
                LateCutpoint.MANAGED_PUBLISHED, large, manifestBytes);

            OctavoBookTransferStore seeded =
                new OctavoBookTransferStore(context);
            assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                         seeded.load());
            OctavoBookTransferStore.ActiveJob expected = seeded.activeJob();
            assertNotNull(expected);
            long expectedSequence = expected.attemptSequence;
            String expectedAttemptId = expected.attemptId;
            byte[] expectedManifestHash = expected.manifestHash();
            File managed = new File(
                initializedLibrary(context).documentDirectoryForTesting(),
                manifest.digest + ".epub");
            assertTrue(managed.isFile());

            AtomicBoolean mutationRan = new AtomicBoolean(false);
            AtomicReference<Throwable> mutationFailure =
                new AtomicReference<>();
            try (ActivityScenario<OctavoActivity> scenario =
                     ActivityScenario.launch(OctavoActivity.class)) {
                scenario.onActivity(activity -> {
                    assertTrue(activity.retryLibraryTransferForTesting());
                    assertPromptMode(
                        activity, OctavoLibrarySyncPrompt.Mode.WORKING);
                    View root = activity.findViewById(R.id.octavo_library);
                    assertNotNull(root);
                    assertTrue(root.post(() -> {
                        try {
                            // The first 4 MiB slice has already been queued.
                            // Mutate its consumed prefix before the next slice.
                            mutateByteAndAdvanceModifiedTime(managed, 1024);
                        } catch (Throwable failure) {
                            mutationFailure.set(failure);
                        } finally {
                            mutationRan.set(true);
                        }
                    }));
                });
                awaitActivity(
                    scenario,
                    activity -> mutationRan.get(),
                    "The between-slice mutation did not run");
                if (mutationFailure.get() != null) {
                    throw new AssertionError(
                        "Unable to apply between-slice mutation",
                        mutationFailure.get());
                }
                awaitActivity(
                    scenario,
                    activity -> !activity.bookTransferStoreForTesting()
                                    .cleanupJobs().isEmpty(),
                    "A stale fused proof associated changed managed bytes");
                scenario.onActivity(activity -> {
                    assertExactRepairCleanup(
                        activity, expectedSequence, expectedAttemptId,
                        expectedManifestHash,
                        manifest.digest, manifest.byteCount);
                    assertNull(activity.libraryStoreForTesting().findBook(
                        manifest.digest));
                    assertFalse(activity.libraryStoreForTesting()
                                    .verifyManagedFile(
                                        manifest.digest,
                                        manifest.byteCount));
                });
            }
        } finally {
            assertTrue(!large.exists() || large.delete());
        }
    }

    private void assertBlockedOffer(
        byte[] manifestBytes,
        OctavoBookTransferStore.LoadStatus expectedStatus,
        String expectedText) {
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifestBytes));
                assertEquals(expectedStatus,
                             activity.bookTransferStoreForTesting()
                                 .loadStatus());
                assertNull(activity.libraryCatalogOfferForTesting());
                assertNull(activity.librarySyncPromptForTesting());
                assertTrue(
                    "Missing blocked-state explanation: " + expectedText,
                    rootText(activity).contains(expectedText));
            });
        }
    }

    private void seedLateCutpoint(LateCutpoint cutpoint,
                                  File source,
                                  byte[] manifestBytes)
        throws Exception {
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(manifestBytes);
        assertEquals(OctavoBookManifest.DecodeStatus.READY, decoded.status);
        OctavoBookManifest manifest = decoded.manifest();
        assertEquals(source.length(), manifest.byteCount);
        assertEquals(sha256(source), manifest.digest);
        OctavoLibraryStore library = initializedLibrary(context);
        OctavoLibrarySyncStore sync = seedApprovedCatalog(
            context, manifest.digest, manifest.byteCount, true);
        List<OctavoLibrarySyncStore.Candidate> candidates =
            sync.reviewCandidates(Collections.emptyList());
        assertEquals(1, candidates.size());

        OctavoBookTransferStore transfer =
            new OctavoBookTransferStore(context);
        assertEquals(OctavoBookTransferStore.LoadStatus.MISSING_EMPTY,
                     transfer.load());
        OctavoBookTransferStore.StageOutcome staged =
            transfer.stageDownload(manifestBytes);
        assertTrue(staged.result.succeeded());
        OctavoBookTransferStore.ActiveJob active = transfer.activeJob();
        assertNotNull(active);
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            sync.reconcileTransferAttempt(
                candidates.get(0), active.attemptId,
                hex(active.manifestHash())));
        byte[] sourceBytes = Files.readAllBytes(source.toPath());
        int acceptedChunks = cutpoint == LateCutpoint.PREFIX_PUBLISHED
            ? 1 : manifest.chunkCount;
        int offset = 0;
        for (int index = 0; index < acceptedChunks; ++index) {
            int chunkLength = manifest.expectedChunkLength(index);
            active = transfer.activeJob();
            assertNotNull(active);
            try (ByteArrayInputStream chunk = new ByteArrayInputStream(
                     sourceBytes, offset, chunkLength)) {
                assertEquals(
                    OctavoBookTransferStore.MutationResult.UPDATED,
                    transfer.acceptNextDownloadChunk(
                        active.callbackToken, index, chunk));
            }
            offset += chunkLength;
        }
        if (cutpoint != LateCutpoint.PREFIX_PUBLISHED) {
            assertEquals(sourceBytes.length, offset);
        }
        if (cutpoint == LateCutpoint.PREFIX_PUBLISHED) {
            return;
        }

        active = transfer.activeJob();
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            transfer.finishDownload(active.callbackToken));
        if (cutpoint == LateCutpoint.BYTES_VERIFIED) {
            return;
        }

        active = transfer.activeJob();
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            transfer.markReader0Validated(active.callbackToken));
        if (cutpoint == LateCutpoint.READER0_VALIDATED) {
            return;
        }

        active = transfer.activeJob();
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            transfer.publishManaged(
                active.callbackToken,
                library.documentDirectoryForTesting()));
        if (cutpoint == LateCutpoint.MANAGED_PUBLISHED) {
            return;
        }

        active = transfer.activeJob();
        assertTrue(library.recordTransferredBook(
            active.digest, active.byteCount,
            manifest.digest.equals(ALPHA_SHA256)
                ? ALPHA_TITLE : "Port 11 downloaded EPUB"));
        if (cutpoint == LateCutpoint.PORT6_ASSOCIATED) {
            return;
        }

        OctavoLibrarySyncStore.TransferReconciliation reconciliation =
            sync.transferReconciliation();
        assertNotNull(reconciliation);
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.UPDATED,
            sync.completeDownloaded(reconciliation, true));
        if (cutpoint == LateCutpoint.O1LS_DOWNLOADED) {
            return;
        }

        active = transfer.activeJob();
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            transfer.markLocalCatalogLinked(active.callbackToken));
    }

    private static void assertExactRepairCleanup(
        OctavoActivity activity,
        long expectedSequence,
        String expectedAttemptId,
        byte[] expectedManifestHash,
        String expectedDigest,
        long expectedByteCount) {
        List<OctavoBookTransferStore.CleanupJob> cleanups =
            activity.bookTransferStoreForTesting().cleanupJobs();
        assertEquals(1, cleanups.size());
        OctavoBookTransferStore.CleanupJob cleanup = cleanups.get(0);
        assertEquals(expectedSequence, cleanup.attemptSequence);
        assertEquals(expectedDigest, cleanup.digest);
        assertEquals(expectedByteCount, cleanup.byteCount);
        assertEquals(OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE,
                     cleanup.purpose);
        assertEquals(
            OctavoBookTransferStore.CleanupPhase.AWAITING_CATALOG_UNLINK,
            cleanup.phase);
        assertEquals(expectedAttemptId, cleanup.originAttemptId);
        assertArrayEquals(expectedManifestHash, cleanup.originManifestHash());
        assertNull(activity.bookTransferStoreForTesting().activeJob());
        assertEquals(0,
                     activity.bookTransferStoreForTesting().intentCount());
    }

    private static OctavoBookTransferStore.Phase expectedPhase(
        LateCutpoint cutpoint) {
        switch (cutpoint) {
            case PREFIX_PUBLISHED:
                return OctavoBookTransferStore.Phase.TRANSFERRING;
            case BYTES_VERIFIED:
                return OctavoBookTransferStore.Phase.BYTES_VERIFIED;
            case READER0_VALIDATED:
                return OctavoBookTransferStore.Phase.READER0_VALIDATED;
            case MANAGED_PUBLISHED:
            case PORT6_ASSOCIATED:
            case O1LS_DOWNLOADED:
                return OctavoBookTransferStore.Phase.MANAGED_PUBLISHED;
            case LOCAL_CATALOG_LINKED:
                return OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED;
            default:
                throw new AssertionError(cutpoint);
        }
    }

    private static void assertSettledDownload(
        ActivityScenario<OctavoActivity> scenario,
        File source) throws Exception {
        scenario.onActivity(activity -> {
            OctavoLibraryStore library =
                activity.libraryStoreForTesting();
            OctavoLibraryStore.Book book = library.findBook(ALPHA_SHA256);
            assertNotNull(book);
            assertTrue(book.imported);
            assertFalse(book.repairRequired);
            assertTrue(book.identityVerified);
            assertEquals(ALPHA_BYTE_COUNT, book.byteCount);
            assertTrue(library.hasExactManagedBook(
                ALPHA_SHA256, ALPHA_BYTE_COUNT));
            assertEquals(
                OctavoLibrarySyncStore.Decision.DOWNLOADED,
                activity.librarySyncStoreForTesting().decision(
                    ALPHA_SHA256));
            assertNull(activity.librarySyncStoreForTesting()
                           .transferReconciliation());
            assertNull(activity.bookTransferStoreForTesting().activeJob());
            assertEquals(
                0,
                activity.bookTransferStoreForTesting().intentCount());
            assertTrue(activity.bookTransferStoreForTesting()
                           .cleanupJobs().isEmpty());
        });
        assertArrayEquals(Files.readAllBytes(source.toPath()),
                          Files.readAllBytes(
                              new File(contextFiles(scenario),
                                  "port6/documents/" + ALPHA_SHA256
                                      + ".epub").toPath()));
    }

    private static File contextFiles(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<File> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(activity.getFilesDir()));
        assertNotNull(result.get());
        return result.get();
    }

    private static void assertExactOffer(OctavoActivity activity,
                                         String digest,
                                         long byteCount) {
        OctavoLibrarySyncStore.Candidate offer =
            activity.libraryCatalogOfferForTesting();
        assertNotNull(offer);
        assertEquals(digest, offer.digest);
        assertEquals(byteCount, offer.byteCount);
        assertPromptMode(activity, OctavoLibrarySyncPrompt.Mode.OFFER);
    }

    private static void assertPromptMode(
        OctavoActivity activity,
        OctavoLibrarySyncPrompt.Mode expected) {
        OctavoLibrarySyncPrompt prompt =
            activity.librarySyncPromptForTesting();
        assertNotNull(prompt);
        assertEquals(expected, prompt.modeForTesting());
    }

    private static void awaitActivity(
        ActivityScenario<OctavoActivity> scenario,
        ActivityCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 250; ++attempt) {
            AtomicBoolean matched = new AtomicBoolean(false);
            scenario.onActivity(activity ->
                matched.set(condition.matches(activity)));
            if (matched.get()) {
                waitForIdle();
                return;
            }
            SystemClock.sleep(20);
        }
        fail(failureMessage);
    }

    private static String rootText(OctavoActivity activity) {
        View root = activity.findViewById(R.id.octavo_library);
        assertNotNull(root);
        StringBuilder text = new StringBuilder();
        appendText(root, text);
        return text.toString();
    }

    private static Button findButton(OctavoActivity activity,
                                     String exactText) {
        View root = activity.findViewById(R.id.octavo_library);
        return root == null ? null : findButton(root, exactText);
    }

    private static Button findButton(View view, String exactText) {
        if (view instanceof Button
            && exactText.contentEquals(((Button)view).getText())) {
            return (Button)view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int index = 0; index < group.getChildCount(); ++index) {
                Button found = findButton(
                    group.getChildAt(index), exactText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertSuppressedActionLargeTextLayout(
        OctavoActivity activity,
        Button actual) {
        assertEquals("Review synchronized EPUB",
                     actual.getText().toString());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                     actual.getLayoutParams().width);
        View root = activity.findViewById(R.id.octavo_library);
        assertNotNull(root);
        assertTrue(actual.getWidth() <= root.getWidth());

        Configuration configuration = new Configuration(
            activity.getResources().getConfiguration());
        configuration.fontScale = 2.0f;
        Context largeText =
            activity.createConfigurationContext(configuration);
        Button probe = new Button(largeText);
        probe.setAllCaps(false);
        probe.setText(actual.getText());
        int minimum = Math.round(
            48 * largeText.getResources().getDisplayMetrics().density);
        probe.setMinHeight(minimum);
        int narrowWidth = Math.round(
            220 * largeText.getResources().getDisplayMetrics().density);
        probe.measure(
            View.MeasureSpec.makeMeasureSpec(
                narrowWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(
                0, View.MeasureSpec.UNSPECIFIED));
        probe.layout(0, 0,
                     probe.getMeasuredWidth(), probe.getMeasuredHeight());
        assertEquals(2.0f,
                     largeText.getResources().getConfiguration().fontScale,
                     0.001f);
        assertTrue(probe.getLineCount() > 1);
        assertNotNull(probe.getLayout());
        assertEquals(probe.getText().length(),
                     probe.getLayout().getLineEnd(
                         probe.getLineCount() - 1));
        assertEquals(narrowWidth, probe.getMeasuredWidth());
        assertTrue(probe.getMeasuredHeight() >= minimum);
    }

    private static void appendText(View view, StringBuilder text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView)view).getText();
            if (value != null) {
                text.append(value).append('\n');
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int index = 0; index < group.getChildCount(); ++index) {
                appendText(group.getChildAt(index), text);
            }
        }
    }

    private static OctavoLibrarySyncStore seedApprovedCatalog(
        Context context,
        String digest,
        long byteCount,
        boolean beginReview) throws IOException {
        initializedLibrary(context);
        OctavoLibrarySyncStore sync =
            new OctavoLibrarySyncStore(context);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     sync.load());
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
            sync.stagePortableBytes(portable(digest, byteCount)));
        OctavoLibrarySyncStore.StagedPortable staged =
            sync.stagedPortable();
        assertNotNull(staged);
        assertEquals(
            OctavoLibrarySyncStore.PortableMergeResult.MERGED,
            sync.approveStagedPortable(staged.sha256));
        if (beginReview) {
            assertEquals(
                OctavoLibrarySyncStore.MutationResult.UPDATED,
                sync.beginReviewEpoch(true));
        }
        return sync;
    }

    private static OctavoLibraryStore initializedLibrary(Context context) {
        OctavoLibraryStore library = new OctavoLibraryStore(context);
        library.loadCatalog(new File(OctavoFixture.install(context)));
        return library;
    }

    private static OctavoLibraryStore.Book seedImportedBook(
        Context context,
        File source,
        String title) throws IOException {
        OctavoLibraryStore library = initializedLibrary(context);
        OctavoLibraryStore.Book staged =
            library.importDocument(Uri.fromFile(source));
        assertTrue(library.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            library.publishReader0ValidatedImport(staged);
        assertTrue(library.recordOpened(managed, title));
        assertTrue(library.completeImportedCatalogAssociation(managed));
        OctavoLibraryStore.Book result = library.findBook(managed.key);
        assertNotNull(result);
        assertTrue(result.identityVerified);
        assertFalse(result.repairRequired);
        return result;
    }

    private static OctavoLibraryPortable.Descriptor descriptor(
        String digest,
        long byteCount) {
        return new OctavoLibraryPortable.Descriptor(digest, byteCount);
    }

    private static String digest(int value) {
        return String.format(Locale.ROOT, "%064x", value);
    }

    private static byte[] portable(String digest, long byteCount)
        throws IOException {
        return OctavoLibraryPortable.simulatedRemoteBytes(
            Collections.singletonList(descriptor(digest, byteCount)));
    }

    private static File stageAsset(Context context,
                                   String asset,
                                   String name) throws IOException {
        File destination = new File(context.getCacheDir(), name);
        try (InputStream input = context.getAssets().open(asset);
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
        return destination;
    }

    private static void assertFixture(File file,
                                      long byteCount,
                                      String digest) {
        assertTrue(file.isFile());
        assertEquals(byteCount, file.length());
        try {
            assertEquals(digest, sha256(file));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void appendPrefix(File destination,
                                     File source,
                                     int byteCount) throws IOException {
        assertTrue(destination.isFile());
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output =
                 new FileOutputStream(destination, true)) {
            byte[] bytes = new byte[byteCount];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset,
                                       bytes.length - offset);
                if (count < 0) {
                    throw new IOException("Fixture became short");
                }
                offset += count;
            }
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static void mutateSameLength(File file) throws IOException {
        try (RandomAccessFile mutation = new RandomAccessFile(file, "rw")) {
            mutation.seek(file.length() / 2);
            int prior = mutation.read();
            assertTrue(prior >= 0);
            mutation.seek(file.length() / 2);
            mutation.write(prior ^ 0x5a);
            mutation.getFD().sync();
        }
    }

    private static void mutateByteAndAdvanceModifiedTime(File file,
                                                          long offset)
        throws IOException {
        long previousModified = file.lastModified();
        try (RandomAccessFile mutation = new RandomAccessFile(file, "rw")) {
            if (offset < 0 || offset >= mutation.length()) {
                throw new IOException("Mutation offset is outside the EPUB");
            }
            mutation.seek(offset);
            int prior = mutation.read();
            if (prior < 0) {
                throw new IOException("Mutation offset became unavailable");
            }
            mutation.seek(offset);
            mutation.write(prior ^ 0x5a);
            mutation.getFD().sync();
        }
        long distinctModified = Math.max(
            System.currentTimeMillis() + 2000L, previousModified + 2000L);
        if (!file.setLastModified(distinctModified)
            || file.lastModified() == previousModified) {
            throw new IOException("Unable to advance the EPUB modification time");
        }
    }

    private static void buildLargeValidEpub(File source, File destination)
        throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Large EPUB cache directory is unavailable");
        }
        try (ZipFile input = new ZipFile(source);
             FileOutputStream fileOutput =
                 new FileOutputStream(destination, false);
             ZipOutputStream output = new ZipOutputStream(fileOutput)) {
            ZipEntry mimetype = input.getEntry("mimetype");
            if (mimetype == null) {
                throw new IOException("Source EPUB has no mimetype entry");
            }
            try (InputStream entry = input.getInputStream(mimetype)) {
                writeStoredZipEntry(
                    output, mimetype.getName(), readAllBytes(entry));
            }

            java.util.Enumeration<? extends ZipEntry> entries =
                input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry sourceEntry = entries.nextElement();
                if ("mimetype".equals(sourceEntry.getName())) {
                    continue;
                }
                byte[] contents;
                try (InputStream entry = input.getInputStream(sourceEntry)) {
                    contents = readAllBytes(entry);
                }
                writeStoredZipEntry(
                    output, sourceEntry.getName(), contents);
            }

            // Stored rather than deflated so the managed EPUB necessarily
            // crosses Activity's 4 MiB verification-slice boundary. Reader0
            // ignores this unreferenced, app-test-only container resource.
            writeStoredZipEntry(
                output,
                "OEBPS/port11-verification-padding.bin",
                new byte[5 * 1024 * 1024]);
            output.finish();
            output.flush();
            fileOutput.getFD().sync();
        }
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        }
    }

    private static void writeStoredZipEntry(ZipOutputStream output,
                                            String name,
                                            byte[] contents)
        throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(contents);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(contents.length);
        entry.setCompressedSize(contents.length);
        entry.setCrc(checksum.getValue());
        output.putNextEntry(entry);
        output.write(contents);
        output.closeEntry();
    }

    private static String sha256(File file) throws IOException {
        try {
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
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(
                Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void writeFile(File file, byte[] bytes) {
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileOutputStream output =
                 new FileOutputStream(file, false)) {
            output.write(bytes);
            output.getFD().sync();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] readFileUnchecked(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        OctavoLibraryStore.clearForTesting(context);
        OctavoLibrarySyncStore.clearForTesting(context);
        OctavoBookTransferStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        OctavoNoteDraftStore.clearForTesting(context);
    }
}
