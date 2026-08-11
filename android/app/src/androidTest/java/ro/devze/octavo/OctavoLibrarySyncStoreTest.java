package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibrarySyncStoreTest {
    private static final String ATTEMPT =
        "0123456789abcdef0123456789abcdef";
    private static final String MANIFEST_SHA = digest(900);

    private File testRoot;

    @Before
    public void createIsolatedFilesDirectory() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context.getCacheDir());
        testRoot = new File(
            context.getCacheDir(),
            "octavo-library-sync-" + System.nanoTime());
        assertFalse(testRoot.exists());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void missingStateStaysInMemoryAndLocalRemovalIsAddOnly()
        throws IOException {
        OctavoLibrarySyncStore store = store(testRoot);
        OctavoLibraryPortable.Descriptor book = descriptor(1, 123);
        assertEquals(OctavoLibrarySyncStore.MutationResult.BLOCKED,
                     store.recordLocalValidated(book));
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     store.load());
        assertFalse(store.stateFileForTesting().exists());

        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.recordLocalValidated(book));
        assertEquals(OctavoLibrarySyncStore.Decision.DOWNLOADED,
                     store.decision(book.digest));
        assertEquals(136, readFile(store.stateFileForTesting()).length);
        assertEquals(1, store.snapshot().descriptorCount());

        OctavoLibrarySyncStore.PortableExport exported =
            store.exportPortable();
        assertEquals(
            OctavoLibrarySyncStore.PortableExportStatus.EXPORTED,
            exported.status);
        assertEquals(96, exported.bytes().length);
        assertEquals(book, OctavoLibraryPortable.decode(exported.bytes())
            .snapshot().descriptor(book.digest));

        OctavoLibrarySyncStore reloaded = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.LOADED,
                     reloaded.load());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.recordLocalRemoval(book.digest));
        assertEquals(OctavoLibrarySyncStore.Decision.LOCAL_REMOVED,
                     reloaded.decision(book.digest));
        assertEquals(1, reloaded.snapshot().descriptorCount());

        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.beginReviewEpoch(true));
        assertTrue(reloaded.reviewCandidates(
            Collections.emptyList()).isEmpty());

        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.resetForExplicitDownload(book.digest));
        assertEquals(OctavoLibrarySyncStore.Decision.NONE,
                     reloaded.decision(book.digest));
        assertEquals(1, reloaded.reviewCandidates(
            Collections.emptyList()).size());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UNCHANGED,
                     reloaded.resetForExplicitDownload(book.digest));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.recordLocalValidated(book));
        assertEquals(OctavoLibrarySyncStore.Decision.DOWNLOADED,
                     reloaded.decision(book.digest));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.resetForExplicitDownload(book.digest));
        assertEquals(OctavoLibrarySyncStore.Decision.NONE,
                     reloaded.decision(book.digest));
        assertEquals(1, reloaded.reviewCandidates(
            Collections.emptyList()).size());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.recordLocalValidated(book));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.recordLocalRemoval(book.digest));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UNCHANGED,
                     reloaded.recordLocalRemoval(book.digest));
        assertArrayEquals(exported.bytes(),
                          reloaded.exportPortable().bytes());
    }

    @Test
    public void currentInputIsDurablyStagedAndDigestApproved()
        throws IOException {
        OctavoLibrarySyncStore store = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     store.load());
        OctavoLibraryPortable.Descriptor a = descriptor(10, 100);
        OctavoLibraryPortable.Descriptor b = descriptor(20, 200);
        byte[] remote = portableBytes(b, a);

        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
            store.stagePortableBytes(remote));
        OctavoLibrarySyncStore.StagedPortable staged =
            store.stagedPortable();
        assertNotNull(staged);
        assertEquals(OctavoLibrarySyncStore.StagedKind.CURRENT,
                     staged.kind);
        assertEquals(remote.length, staged.byteCount);
        assertEquals(sha256Hex(remote), staged.sha256);
        byte[] detached = staged.bytes();
        detached[0] ^= 1;
        assertArrayEquals(remote, store.stagedPortable().bytes());
        assertEquals(
            OctavoLibrarySyncStore.PortableMergeResult
                .STAGED_DIGEST_MISMATCH,
            store.approveStagedPortable(digest(999)));
        assertEquals(0, store.snapshot().descriptorCount());

        OctavoLibrarySyncStore reloaded = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.LOADED,
                     reloaded.load());
        assertArrayEquals(remote, reloaded.stagedPortable().bytes());
        assertEquals(OctavoLibrarySyncStore.PortableMergeResult.MERGED,
                     reloaded.approveStagedPortable(staged.sha256));
        assertNull(reloaded.stagedPortable());
        assertEquals(2, reloaded.snapshot().descriptorCount());

        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
            reloaded.stagePortableBytes(remote));
        String replaySha = reloaded.stagedPortable().sha256;
        assertEquals(
            OctavoLibrarySyncStore.PortableMergeResult.UNCHANGED,
            reloaded.approveStagedPortable(replaySha));
        assertNull(reloaded.stagedPortable());
    }

    @Test
    public void offersAndTransferReconciliationSurviveRestart()
        throws IOException {
        OctavoLibrarySyncStore store = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     store.load());
        OctavoLibraryPortable.Descriptor a = descriptor(10, 100);
        OctavoLibraryPortable.Descriptor b = descriptor(20, 200);
        stageAndApprove(store, portableBytes(b, a));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.beginReviewEpoch(true));

        List<OctavoLibrarySyncStore.Candidate> offers =
            store.reviewCandidates(Collections.emptyList());
        assertEquals(2, offers.size());
        OctavoLibrarySyncStore.Candidate download = offers.get(0);
        OctavoLibrarySyncStore.Candidate ignore = offers.get(1);
        assertEquals(a.digest, download.digest);
        assertEquals(b.digest, ignore.digest);
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.ignore(ignore));
        assertEquals(OctavoLibrarySyncStore.Decision.IGNORED,
                     store.decision(b.digest));

        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.reconcileTransferAttempt(
                         download, ATTEMPT, MANIFEST_SHA));
        OctavoLibrarySyncStore.TransferReconciliation marker =
            store.transferReconciliation();
        assertNotNull(marker);
        assertEquals(a.digest, marker.digest);
        assertEquals(ATTEMPT, marker.attemptId);
        assertEquals(MANIFEST_SHA, marker.manifestSha256);
        assertTrue(store.reviewCandidates(
            Collections.emptyList()).isEmpty());
        assertEquals(OctavoLibrarySyncStore.MutationResult.CONFLICT,
                     store.dismiss(download));

        OctavoLibrarySyncStore reloaded = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.LOADED,
                     reloaded.load());
        OctavoLibrarySyncStore.TransferReconciliation recovered =
            reloaded.transferReconciliation();
        assertNotNull(recovered);
        assertTrue(marker.sameIdentity(recovered));
        assertEquals(OctavoLibrarySyncStore.MutationResult.INVALID,
                     reloaded.completeDownloaded(recovered, false));
        assertNotNull(reloaded.transferReconciliation());

        reloaded.failNextPublishForTesting();
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.PUBLISH_FAILED,
            reloaded.completeDownloaded(recovered, true));
        assertNotNull(reloaded.transferReconciliation());
        assertEquals(OctavoLibrarySyncStore.Decision.NONE,
                     reloaded.decision(a.digest));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.completeDownloaded(recovered, true));
        assertNull(reloaded.transferReconciliation());
        assertEquals(OctavoLibrarySyncStore.Decision.DOWNLOADED,
                     reloaded.decision(a.digest));

        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.recordLocalRemoval(a.digest));
        assertEquals(OctavoLibrarySyncStore.Decision.LOCAL_REMOVED,
                     reloaded.decision(a.digest));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.beginReviewEpoch(true));
        assertTrue(reloaded.reviewCandidates(
            Collections.emptyList()).isEmpty());

        OctavoLibraryPortable.Descriptor c = descriptor(30, 300);
        stageAndApprove(reloaded, portableBytes(c));
        List<OctavoLibrarySyncStore.Candidate> next =
            reloaded.reviewCandidates(Collections.emptyList());
        assertEquals(1, next.size());
        assertEquals(c.digest, next.get(0).digest);
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.reconcileTransferAttempt(
                         next.get(0), ATTEMPT, MANIFEST_SHA));
        OctavoLibrarySyncStore.TransferReconciliation canceled =
            reloaded.transferReconciliation();
        assertEquals(OctavoLibrarySyncStore.MutationResult.INVALID,
                     reloaded.releaseTransferReconciliation(
                         canceled, false));
        assertNotNull(reloaded.transferReconciliation());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.releaseTransferReconciliation(
                         canceled, true));
        assertNull(reloaded.transferReconciliation());
    }

    @Test
    public void limitFutureAndEquivocationAreRetainedWithoutMerge()
        throws IOException {
        OctavoLibrarySyncStore full = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     full.load());
        ArrayList<OctavoLibraryPortable.Descriptor> descriptors =
            new ArrayList<>();
        for (int index = 1; index <= 63; ++index) {
            descriptors.add(descriptor(index, index));
        }
        stageAndApprove(full, portableBytes(descriptors));
        byte[] fullState = readFile(full.stateFileForTesting());
        assertEquals(5_592, fullState.length);

        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.EQUIVOCATION,
            full.stagePortableBytes(portableBytes(
                descriptor(1, 999), descriptor(999, 1))));
        assertArrayEquals(fullState,
                          readFile(full.stateFileForTesting()));

        byte[] overflow = portableBytes(descriptor(999, 1));
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.LIMIT_RETAINED,
            full.stagePortableBytes(overflow));
        assertEquals(OctavoLibrarySyncStore.StagedKind.LIMIT,
                     full.stagedPortable().kind);
        assertArrayEquals(overflow, full.stagedPortable().bytes());
        assertEquals(
            OctavoLibrarySyncStore.PortableMergeResult.NO_STAGED_CURRENT,
            full.approveStagedPortable(
                full.stagedPortable().sha256));
        assertEquals(63, full.snapshot().descriptorCount());

        File overCapacityRoot = new File(testRoot, "over-capacity");
        assertTrue(overCapacityRoot.mkdirs());
        OctavoLibrarySyncStore overCapacity = store(overCapacityRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     overCapacity.load());
        byte[] sixtyFour = overCapacityPortable(64);
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.LIMIT,
            overCapacity.stagePortableBytes(sixtyFour));
        assertNull(overCapacity.stagedPortable());
        assertFalse(overCapacity.stateFileForTesting().exists());

        File futureRoot = new File(testRoot, "future");
        assertTrue(futureRoot.mkdirs());
        OctavoLibrarySyncStore futureStore = store(futureRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     futureStore.load());
        byte[] future = futurePortable(65_536, 2);
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.FUTURE_RETAINED,
            futureStore.stagePortableBytes(future));
        assertArrayEquals(future, futureStore.retainedFutureBytes());
        assertEquals(65_648,
                     readFile(futureStore.stateFileForTesting()).length);
        assertEquals(128 * 1024,
                     OctavoLibrarySyncStore.maximumFileBytesForTesting());
        assertEquals(OctavoLibrarySyncStore.PortableStageResult.UNCHANGED,
                     futureStore.stagePortableBytes(future.clone()));
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CONFLICT,
            futureStore.stagePortableBytes(futurePortable(8, 3)));
        assertArrayEquals(future, futureStore.retainedFutureBytes());
        assertTrue(futureStore.lastError().contains("attention"));
    }

    @Test
    public void localPublicationAndRemovalRetriesAreExactAndDurable()
        throws IOException {
        OctavoLibrarySyncStore store = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     store.load());
        OctavoLibraryPortable.Descriptor book = descriptor(77, 777);
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.stageLocalPublication(book));
        OctavoLibrarySyncStore.LocalReconciliation publication =
            store.localReconciliation();
        assertNotNull(publication);
        assertEquals(
            OctavoLibrarySyncStore.LocalReconciliationKind.PUBLICATION,
            publication.kind);
        assertEquals(book.digest, publication.digest);
        assertEquals(0, store.snapshot().descriptorCount());

        OctavoLibrarySyncStore reloaded = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.LOADED,
                     reloaded.load());
        assertTrue(reloaded.lastError().contains("Retry"));
        OctavoLibrarySyncStore.LocalReconciliation recovered =
            reloaded.localReconciliation();
        assertTrue(publication.sameIdentity(recovered));
        assertEquals(OctavoLibrarySyncStore.MutationResult.INVALID,
                     reloaded.finalizeLocalReconciliation(
                         recovered, false));
        reloaded.failNextPublishForTesting();
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.PUBLISH_FAILED,
            reloaded.finalizeLocalReconciliation(recovered, true));
        assertNotNull(reloaded.localReconciliation());
        assertEquals(0, reloaded.snapshot().descriptorCount());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.finalizeLocalReconciliation(
                         recovered, true));
        assertNull(reloaded.localReconciliation());
        assertEquals(OctavoLibrarySyncStore.Decision.DOWNLOADED,
                     reloaded.decision(book.digest));

        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.stageLocalRemoval(book));
        OctavoLibrarySyncStore.LocalReconciliation removal =
            reloaded.localReconciliation();
        assertEquals(
            OctavoLibrarySyncStore.LocalReconciliationKind.REMOVAL,
            removal.kind);
        reloaded.failNextPublishForTesting();
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.PUBLISH_FAILED,
            reloaded.finalizeLocalReconciliation(removal, true));
        assertEquals(OctavoLibrarySyncStore.Decision.DOWNLOADED,
                     reloaded.decision(book.digest));
        assertNotNull(reloaded.localReconciliation());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.recordLocalRemoval(book.digest));
        assertNull(reloaded.localReconciliation());
        assertEquals(OctavoLibrarySyncStore.Decision.LOCAL_REMOVED,
                     reloaded.decision(book.digest));

        OctavoLibraryPortable.Descriptor aborted =
            descriptor(88, 888);
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.stageLocalPublication(aborted));
        OctavoLibrarySyncStore.LocalReconciliation abort =
            reloaded.localReconciliation();
        assertEquals(OctavoLibrarySyncStore.MutationResult.INVALID,
                     reloaded.clearLocalReconciliation(abort, false));
        assertNotNull(reloaded.localReconciliation());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.clearLocalReconciliation(abort, true));
        assertNull(reloaded.localReconciliation());
        assertNull(reloaded.decision(aborted.digest));

        OctavoLibraryPortable.Descriptor superseded =
            descriptor(99, 999);
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.stageLocalPublication(superseded));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     reloaded.stageLocalRemoval(superseded));
        assertNull(reloaded.localReconciliation());
        assertNull(reloaded.decision(superseded.digest));
    }

    @Test
    public void fullCatalogRetainsExactLocalPublicationRetry()
        throws IOException {
        OctavoLibrarySyncStore store = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     store.load());
        ArrayList<OctavoLibraryPortable.Descriptor> full =
            new ArrayList<>();
        for (int index = 1;
             index <= OctavoLibraryPortable.maximumRecordCount();
             ++index) {
            full.add(descriptor(index, index));
        }
        stageAndApprove(store, portableBytes(full));
        OctavoLibraryPortable.Descriptor overflow =
            descriptor(1000, 1000);
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.stageLocalPublication(overflow));
        OctavoLibrarySyncStore.LocalReconciliation pending =
            store.localReconciliation();
        assertNotNull(pending);
        assertEquals(OctavoLibrarySyncStore.MutationResult.LIMIT,
                     store.finalizeLocalReconciliation(pending, true));
        assertTrue(pending.sameIdentity(store.localReconciliation()));

        OctavoLibrarySyncStore reloaded = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.LOADED,
                     reloaded.load());
        assertTrue(pending.sameIdentity(reloaded.localReconciliation()));
        assertEquals(OctavoLibraryPortable.maximumRecordCount(),
                     reloaded.snapshot().descriptorCount());
    }

    @Test
    public void atomicFailureQuarantineAndFutureStoreAreVisible()
        throws IOException {
        OctavoLibrarySyncStore store = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     store.load());
        OctavoLibraryPortable.Descriptor a = descriptor(1, 100);
        store.failNextPublishForTesting();
        assertEquals(OctavoLibrarySyncStore.MutationResult.PUBLISH_FAILED,
                     store.recordLocalValidated(a));
        assertFalse(store.stateFileForTesting().exists());
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     store.recordLocalValidated(a));
        byte[] one = readFile(store.stateFileForTesting());

        OctavoLibraryPortable.Descriptor b = descriptor(2, 200);
        store.failNextMoveAfterReplaceForTesting();
        assertEquals(
            OctavoLibrarySyncStore.MutationResult.PUBLISH_UNCERTAIN,
            store.recordLocalValidated(b));
        assertEquals(
            OctavoLibrarySyncStore.LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
            store.loadStatus());
        assertEquals(OctavoLibrarySyncStore.MutationResult.BLOCKED,
                     store.beginReviewEpoch(true));
        assertFalse(Arrays.equals(
            one, readFile(store.stateFileForTesting())));

        OctavoLibrarySyncStore reconciled = store(testRoot);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.LOADED,
                     reconciled.load());
        assertEquals(2, reconciled.snapshot().descriptorCount());

        byte[] corrupt = readFile(reconciled.stateFileForTesting());
        corrupt[corrupt.length - 1] ^= 1;
        writeFile(reconciled.stateFileForTesting(), corrupt);
        OctavoLibrarySyncStore recovered = store(testRoot);
        assertEquals(
            OctavoLibrarySyncStore.LoadStatus.CORRUPT_QUARANTINED,
            recovered.load());
        assertTrue(recovered.quarantineFileForTesting(1).isFile());
        assertEquals(0, recovered.snapshot().descriptorCount());
        assertEquals(48, readFile(recovered.stateFileForTesting()).length);
        assertTrue(recovered.stateFileForTesting().delete());
        OctavoLibrarySyncStore warningReload = store(testRoot);
        assertEquals(
            OctavoLibrarySyncStore.LoadStatus.CORRUPT_QUARANTINED,
            warningReload.load());
        assertTrue(warningReload.lastError().contains("attention"));
        assertEquals(OctavoLibrarySyncStore.MutationResult.UPDATED,
                     warningReload.beginReviewEpoch(true));
        assertTrue(warningReload.lastError().contains("attention"));

        File overboundRoot = new File(testRoot, "overbound-store");
        assertTrue(overboundRoot.mkdirs());
        OctavoLibrarySyncStore overboundStore = store(overboundRoot);
        byte[] overbound = new byte[
            OctavoLibrarySyncStore.maximumFileBytesForTesting() + 1];
        overbound[0] = 17;
        overbound[overbound.length - 1] = 29;
        writeFile(overboundStore.stateFileForTesting(), overbound);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.OVERBOUND_BLOCKED,
                     overboundStore.load());
        assertArrayEquals(overbound,
                          readFile(overboundStore.stateFileForTesting()));
        assertFalse(overboundStore.quarantineFileForTesting(1).exists());

        File slotsRoot = new File(testRoot, "full-quarantine");
        assertTrue(slotsRoot.mkdirs());
        OctavoLibrarySyncStore slotsStore = store(slotsRoot);
        byte[] malformed = new byte[48];
        writeFile(slotsStore.stateFileForTesting(), malformed);
        for (int index = 1; index <= 3; ++index) {
            writeFile(slotsStore.quarantineFileForTesting(index),
                      new byte[] {(byte)index});
        }
        assertEquals(OctavoLibrarySyncStore.LoadStatus.CORRUPT_BLOCKED,
                     slotsStore.load());
        assertArrayEquals(malformed,
                          readFile(slotsStore.stateFileForTesting()));

        File futureRoot = new File(testRoot, "future-store");
        assertTrue(futureRoot.mkdirs());
        OctavoLibrarySyncStore futureStore = store(futureRoot);
        byte[] futureState = new byte[8];
        writeInt(futureState, 0,
                 OctavoLibrarySyncStore.storeMagicForTesting());
        writeInt(futureState, 4, 0xffffffff);
        writeFile(futureStore.stateFileForTesting(), futureState);
        assertEquals(
            OctavoLibrarySyncStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            futureStore.load());
        assertArrayEquals(futureState,
                          readFile(futureStore.stateFileForTesting()));
        String futureFailure = futureStore.lastError();
        assertTrue(futureFailure.contains("newer version"));
        assertEquals(OctavoLibrarySyncStore.MutationResult.BLOCKED,
                     futureStore.recordLocalValidated(a));
        assertEquals(futureFailure, futureStore.lastError());
    }

    private static void stageAndApprove(
        OctavoLibrarySyncStore store,
        byte[] bytes) throws IOException {
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
            store.stagePortableBytes(bytes));
        String digest = store.stagedPortable().sha256;
        assertEquals(OctavoLibrarySyncStore.PortableMergeResult.MERGED,
                     store.approveStagedPortable(digest));
    }

    private static OctavoLibrarySyncStore store(File root) {
        return new OctavoLibrarySyncStore(root);
    }

    private static OctavoLibraryPortable.Descriptor descriptor(
        int digest, long byteCount) {
        return new OctavoLibraryPortable.Descriptor(
            digest(digest), byteCount);
    }

    private static String digest(int value) {
        return String.format(Locale.US, "%064x", value);
    }

    private static byte[] portableBytes(
        OctavoLibraryPortable.Descriptor... descriptors)
        throws IOException {
        return portableBytes(Arrays.asList(descriptors));
    }

    private static byte[] portableBytes(
        List<OctavoLibraryPortable.Descriptor> descriptors)
        throws IOException {
        return OctavoLibraryPortable.simulatedRemoteBytes(descriptors);
    }

    private static byte[] overCapacityPortable(int count) {
        byte[] bytes = new byte[20 + 76 * count];
        writeInt(bytes, 0, OctavoLibraryPortable.magicForTesting());
        writeInt(bytes, 4, OctavoLibraryPortable.versionForTesting());
        writeInt(bytes, 8, 3);
        writeInt(bytes, 12, count);
        for (int index = 0; index < count; ++index) {
            byte[] digest = digest(index + 1)
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int offset = 16 + index * 76;
            System.arraycopy(digest, 0, bytes, offset, digest.length);
            writeLong(bytes, offset + 64, index + 1);
            writeInt(bytes, offset + 72, 1);
        }
        repairChecksum(bytes);
        return bytes;
    }

    private static byte[] futurePortable(int length, int version) {
        byte[] bytes = new byte[length];
        writeInt(bytes, 0, OctavoLibraryPortable.magicForTesting());
        writeInt(bytes, 4, version);
        return bytes;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(Character.forDigit(
                    (value >>> 4) & 0xf, 16));
                result.append(Character.forDigit(value & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset,
                                       bytes.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            assertEquals(bytes.length, offset);
            assertEquals(-1, input.read());
        }
        return bytes;
    }

    private static void writeFile(File file, byte[] bytes)
        throws IOException {
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileOutputStream output =
                 new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int index = 0; index < Long.BYTES; ++index) {
            bytes[offset + index] =
                (byte)(value >>> (56 - 8 * index));
        }
    }

    private static void repairChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        writeInt(bytes, bytes.length - Integer.BYTES,
                 (int)checksum.getValue());
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
        return file.delete();
    }
}
