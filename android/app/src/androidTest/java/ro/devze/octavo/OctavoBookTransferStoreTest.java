package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public final class OctavoBookTransferStoreTest {
    private File testRoot;

    @Before
    public void createIsolatedFilesDirectory() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context.getCacheDir());
        testRoot = new File(
            context.getCacheDir(),
            "octavo-book-transfer-" + System.nanoTime());
        assertFalse(testRoot.exists());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void downloadIsSequentialRestartSafeAndPublicationGated()
        throws IOException {
        byte[] epub = bytes(
            OctavoBookManifest.fixedChunkSize() + 37, 17);
        OctavoBookManifest manifest = manifest(epub);
        OctavoBookTransferStore store = store();
        assertEquals(
            OctavoBookTransferStore.LoadStatus.MISSING_EMPTY,
            store.load());
        assertFalse(store.stateFileForTesting().exists());
        OctavoBookTransferStore.StageOutcome staged =
            store.stageDownload(manifest.encode());
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     staged.result);
        assertTrue(staged.active);
        OctavoBookTransferStore.ActiveJob first = store.activeJob();
        assertNotNull(first);
        assertEquals(OctavoBookTransferStore.Phase.STAGED,
                     first.phase);
        assertEquals(32, first.attemptId.length());
        assertTrue(store.partFileForTesting(
            first.digest, first.attemptId).isFile());
        assertNull(store.stagedDownloadForReader0(first));

        int chunkSize = manifest.expectedChunkLength(0);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            store.acceptNextDownloadChunk(
                first.callbackToken, 0,
                new ByteArrayInputStream(
                    Arrays.copyOfRange(epub, 0, chunkSize))));
        assertEquals(1, store.activeJob().completedPrefix);

        OctavoBookTransferStore reloaded = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     reloaded.load());
        OctavoBookTransferStore.ActiveJob second =
            reloaded.activeJob();
        assertNotSame(first.callbackToken, second.callbackToken);
        assertEquals(1, second.completedPrefix);
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            reloaded.acceptNextDownloadChunk(
                first.callbackToken, 1,
                new ByteArrayInputStream(
                    Arrays.copyOfRange(epub, chunkSize, epub.length))));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.acceptNextDownloadChunk(
                second.callbackToken, 1,
                new ByteArrayInputStream(
                    Arrays.copyOfRange(epub, chunkSize, epub.length))));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.finishDownload(second.callbackToken));

        OctavoBookTransferStore.ActiveJob verified =
            reloaded.activeJob();
        File readerFile =
            reloaded.stagedDownloadForReader0(verified);
        assertNotNull(readerFile);
        assertArrayEquals(epub, readFile(readerFile));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.recordReader0Rejected(
                verified.callbackToken));
        assertEquals(OctavoBookTransferStore.Attention.READER0_REJECTED,
                     reloaded.activeJob().attention);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.markReader0Validated(
                verified.callbackToken));

        File managed = reloaded.managedDirectoryForTesting();
        File destination =
            new File(managed, manifest.digest + ".epub");
        byte[] sameLengthMutation = epub.clone();
        sameLengthMutation[101] ^= 0x6d;
        writeFile(readerFile, sameLengthMutation);
        assertEquals(
            OctavoBookTransferStore.MutationResult.VERIFY_FAILED,
            reloaded.publishManaged(
                verified.callbackToken, managed));
        assertFalse(destination.exists());
        writeFile(readerFile, epub);
        byte[] wrongDestination = bytes(11, 99);
        writeFile(destination, wrongDestination);
        assertEquals(
            OctavoBookTransferStore.MutationResult.VERIFY_FAILED,
            reloaded.publishManaged(
                verified.callbackToken, managed));
        assertArrayEquals(wrongDestination, readFile(destination));
        assertTrue(readerFile.isFile());
        assertTrue(destination.delete());
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.publishManaged(
                verified.callbackToken, managed));
        assertArrayEquals(epub, readFile(destination));
        assertFalse(readerFile.exists());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            reloaded.cancelActive(verified.callbackToken));
        assertEquals(OctavoBookTransferStore.Phase.MANAGED_PUBLISHED,
                     reloaded.activeJob().phase);
        assertArrayEquals(epub, readFile(destination));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.recordLocalCatalogLinkFailed(
                verified.callbackToken));
        assertEquals(
            OctavoBookTransferStore.Attention.CATALOG_LINK_FAILED,
            reloaded.activeJob().attention);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.markLocalCatalogLinked(
                verified.callbackToken));
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            reloaded.cancelActive(verified.callbackToken));
        assertEquals(OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED,
                     reloaded.activeJob().phase);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            reloaded.finalizeTransfer(verified.callbackToken));
        assertNull(reloaded.activeJob());
        assertEquals(0, reloaded.retainedIntentCount());
    }

    @Test
    public void restartRehashesPrefixAndTruncatesUncommittedBytes()
        throws IOException {
        byte[] epub = bytes(
            OctavoBookManifest.fixedChunkSize() + 29, 41);
        OctavoBookManifest manifest = manifest(epub);
        OctavoBookTransferStore store = loadedStore();
        store.stageDownload(manifest.encode());
        OctavoBookTransferStore.ActiveJob staged = store.activeJob();
        File part = store.partFileForTesting(
            manifest.digest, staged.attemptId);
        appendFile(part, bytes(17, 3));

        OctavoBookTransferStore recovered = store();
        assertEquals(
            OctavoBookTransferStore.LoadStatus
                .RECOVERED_EXTRA_TRUNCATED,
            recovered.load());
        assertEquals(0, part.length());
        OctavoBookTransferStore.CallbackToken token =
            recovered.activeJob().callbackToken;
        int firstLength = manifest.expectedChunkLength(0);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            recovered.acceptNextDownloadChunk(
                token, 0, new ByteArrayInputStream(
                    Arrays.copyOfRange(epub, 0, firstLength))));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            recovered.acceptNextDownloadChunk(
                token, 1, new ByteArrayInputStream(
                    Arrays.copyOfRange(
                        epub, firstLength, epub.length))));

        try (RandomAccessFile mutation =
                 new RandomAccessFile(part, "rw")) {
            mutation.seek(firstLength + 3L);
            int original = mutation.read();
            mutation.seek(firstLength + 3L);
            mutation.write(original ^ 0x5a);
            mutation.getFD().sync();
        }
        OctavoBookTransferStore repaired = store();
        assertEquals(
            OctavoBookTransferStore.LoadStatus
                .RECOVERED_PREFIX_REPAIRED,
            repaired.load());
        assertEquals(1, repaired.activeJob().completedPrefix);
        assertEquals(firstLength, part.length());
        assertTrue(repaired.activeJob().retryRequired);
        assertEquals(OctavoBookTransferStore.Attention.PREFIX_REPAIRED,
                     repaired.activeJob().attention);
    }

    @Test
    public void uploadRequiresApprovalRebindAndWholeFileRevalidation()
        throws IOException {
        byte[] epub = bytes(809, 71);
        OctavoBookManifest expected = manifest(epub);
        OctavoBookTransferStore store = loadedStore();
        File managed = store.managedDirectoryForTesting();
        File source = new File(
            managed, expected.digest + ".epub");
        File outside = new File(
            testRoot, expected.digest + ".epub");
        File wrongName = new File(managed, "approved.epub");
        writeFile(source, epub);
        writeFile(outside, epub);
        writeFile(wrongName, epub);
        assertEquals(
            OctavoBookTransferStore.MutationResult.INVALID,
            store.stageExplicitUpload(source, false, true).result);
        assertEquals(
            OctavoBookTransferStore.MutationResult.INVALID,
            store.stageExplicitUpload(source, true, false).result);
        assertEquals(
            OctavoBookTransferStore.MutationResult.INVALID,
            store.stageExplicitUpload(outside, true, true).result);
        assertEquals(
            OctavoBookTransferStore.MutationResult.INVALID,
            store.stageExplicitUpload(wrongName, true, true).result);
        OctavoBookTransferStore.StageOutcome staged =
            store.stageExplicitUpload(source, true, true);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     staged.result);

        OctavoBookTransferStore uploadRestart = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     uploadRestart.load());
        OctavoBookTransferStore.ActiveJob job =
            uploadRestart.activeJob();
        assertTrue(job.retryRequired);
        assertEquals(
            OctavoBookTransferStore.MutationResult.INVALID,
            uploadRestart.bindUploadSource(
                job.callbackToken, outside));
        assertEquals(
            OctavoBookTransferStore.MutationResult.INVALID,
            uploadRestart.bindUploadSource(
                job.callbackToken, wrongName));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            uploadRestart.bindUploadSource(
                job.callbackToken, source));
        ByteArrayOutputStream uploaded = new ByteArrayOutputStream();
        OctavoBookTransferStore.ChunkRead chunk =
            uploadRestart.readNextUploadChunk(
                job.callbackToken, 0, uploaded);
        assertEquals(OctavoBookTransferStore.ChunkReadStatus.READY,
                     chunk.status);
        assertEquals(epub.length, chunk.byteCount);
        assertArrayEquals(epub, uploaded.toByteArray());
        ByteArrayOutputStream duplicate = new ByteArrayOutputStream();
        assertEquals(
            OctavoBookTransferStore.ChunkReadStatus.CONFLICT,
            uploadRestart.readNextUploadChunk(
                job.callbackToken, 0, duplicate).status);
        assertEquals(0, duplicate.size());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            uploadRestart.bindUploadSource(
                job.callbackToken, source));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            uploadRestart.confirmUploadChunk(job.callbackToken, 0));

        byte[] mutated = epub.clone();
        mutated[17] ^= 0x33;
        writeFile(source, mutated);
        assertEquals(
            OctavoBookTransferStore.MutationResult.VERIFY_FAILED,
            uploadRestart.finishUpload(job.callbackToken));

        writeFile(source, epub);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     uploadRestart.bindUploadSource(
                         job.callbackToken, source));
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     uploadRestart.finishUpload(job.callbackToken));

        OctavoBookTransferStore restarted = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     restarted.load());
        assertEquals(OctavoBookTransferStore.Phase.BYTES_VERIFIED,
                     restarted.activeJob().phase);
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            restarted.finalizeTransfer(job.callbackToken));
        OctavoBookTransferStore.CallbackToken fresh =
            restarted.activeJob().callbackToken;
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            restarted.finalizeTransfer(fresh));
        byte[] wrongRemote = manifest(epub).encode();
        wrongRemote[wrongRemote.length - 1] ^= 1;
        assertEquals(
            OctavoBookTransferStore.MutationResult.VERIFY_FAILED,
            restarted.markSimulatedRemoteObjectVerified(
                fresh, wrongRemote));
        assertEquals(
            OctavoBookTransferStore.Attention.REMOTE_OBJECT_MISMATCH,
            restarted.activeJob().attention);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            restarted.markSimulatedRemoteObjectVerified(
                fresh, manifest(epub).encode()));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            restarted.finalizeTransfer(fresh));
    }

    @Test
    public void uncertainQueueReplaceRetainsPartForReloadRecovery()
        throws IOException {
        byte[] epub = bytes(113, 5);
        OctavoBookManifest manifest = manifest(epub);
        OctavoBookTransferStore store = loadedStore();
        store.failNextMoveAfterReplaceForTesting();
        OctavoBookTransferStore.StageOutcome uncertain =
            store.stageDownload(manifest.encode());
        assertEquals(
            OctavoBookTransferStore.MutationResult.PUBLISH_UNCERTAIN,
            uncertain.result);
        assertNotNull(uncertain.attemptId);
        assertTrue(store.partFileForTesting(
            manifest.digest, uncertain.attemptId).isFile());
        assertNull(store.activeJob());

        OctavoBookTransferStore reloaded = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     reloaded.load());
        assertNotNull(reloaded.activeJob());
        reloaded.failNextPublishForTesting();
        assertEquals(
            OctavoBookTransferStore.MutationResult.PUBLISH_FAILED,
            reloaded.acceptNextDownloadChunk(
                reloaded.activeJob().callbackToken, 0,
                new ByteArrayInputStream(epub)));
        assertEquals(0,
                     reloaded.partFileForTesting(
                         manifest.digest,
                         reloaded.activeJob().attemptId).length());
        assertEquals(0, reloaded.activeJob().completedPrefix);
    }

    @Test
    public void cleanupIsDurableAfterCatalogBoundaryAndTouchesOnlyTarget()
        throws IOException {
        byte[] epub = bytes(211, 93);
        OctavoBookManifest manifest = manifest(epub);
        OctavoBookTransferStore store = loadedStore();
        File managed = store.managedDirectoryForTesting();
        File target = new File(managed, manifest.digest + ".epub");
        File unrelated = new File(managed, "unrelated.epub");
        writeFile(target, epub);
        writeFile(unrelated, bytes(9, 2));

        OctavoBookTransferStore.CleanupOutcome staged =
            store.stageManagedCleanup(
                manifest.digest, manifest.byteCount);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     staged.result);
        OctavoBookTransferStore.CleanupJob job =
            store.cleanupJobs().get(0);
        assertEquals(OctavoBookTransferStore.CleanupPurpose.LOCAL_REMOVE,
                     job.purpose);
        assertNull(job.originAttemptId);
        assertNull(job.originManifestHash());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            store.deleteManagedForCleanup(
                job.callbackToken, managed));
        assertTrue(target.isFile());
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            store.markCleanupCatalogUnlinked(job.callbackToken));

        OctavoBookTransferStore restarted = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     restarted.load());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            restarted.deleteManagedForCleanup(
                job.callbackToken, managed));
        OctavoBookTransferStore.CleanupJob fresh =
            restarted.cleanupJobs().get(0);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            restarted.deleteManagedForCleanup(
                fresh.callbackToken, managed));
        assertFalse(target.exists());
        assertTrue(unrelated.isFile());
        assertEquals(
            OctavoBookTransferStore.CleanupPhase
                .AWAITING_SYNC_SUPPRESSION,
            restarted.cleanupJobs().get(0).phase);

        OctavoBookTransferStore suppressionRestart = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     suppressionRestart.load());
        assertEquals(
            OctavoBookTransferStore.CleanupPhase
                .AWAITING_SYNC_SUPPRESSION,
            suppressionRestart.cleanupJobs().get(0).phase);
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            suppressionRestart.finalizeManagedCleanup(
                fresh.callbackToken, true));
        OctavoBookTransferStore.CleanupToken suppressionToken =
            suppressionRestart.cleanupJobs().get(0).callbackToken;
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            suppressionRestart.finalizeManagedCleanup(
                suppressionToken, false));
        assertEquals(1, suppressionRestart.cleanupIntentCount());
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            suppressionRestart.finalizeManagedCleanup(
                suppressionToken, true));
        assertEquals(0, suppressionRestart.cleanupIntentCount());

        writeFile(target, epub);
        OctavoBookTransferStore.CleanupOutcome uncataloged =
            suppressionRestart.stageUncatalogedManagedCleanup(
                manifest.digest, manifest.byteCount);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     uncataloged.result);
        assertEquals(
            OctavoBookTransferStore.CleanupPhase.READY_TO_DELETE,
            suppressionRestart.cleanupJobs().get(0).phase);
        assertEquals(OctavoBookTransferStore.CleanupPurpose.UNCATALOGED,
                     suppressionRestart.cleanupJobs().get(0).purpose);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            suppressionRestart.deleteManagedForCleanup(
                suppressionRestart.cleanupJobs().get(0).callbackToken,
                managed));
        assertFalse(target.exists());
        assertTrue(unrelated.isFile());
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            suppressionRestart.finalizeManagedCleanup(
                suppressionRestart.cleanupJobs().get(0).callbackToken,
                true));

        writeFile(target, epub);
        OctavoBookTransferStore.CleanupOutcome repair =
            suppressionRestart.stageRepairManagedCleanup(
                manifest.digest, manifest.byteCount);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     repair.result);
        OctavoBookTransferStore repairRestart = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     repairRestart.load());
        OctavoBookTransferStore.CleanupJob repairJob =
            repairRestart.cleanupJobs().get(0);
        assertEquals(OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE,
                     repairJob.purpose);
        assertNull(repairJob.originAttemptId);
        assertNull(repairJob.originManifestHash());
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            repairRestart.markCleanupCatalogUnlinked(
                repairJob.callbackToken));
        repairJob = repairRestart.cleanupJobs().get(0);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            repairRestart.deleteManagedForCleanup(
                repairJob.callbackToken, managed));
        repairJob = repairRestart.cleanupJobs().get(0);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            repairRestart.finalizeManagedCleanup(
                repairJob.callbackToken, true));
        assertFalse(target.exists());
    }

    @Test
    public void publishedDownloadConvertsToExactRepairAcrossPhasesAndRestart()
        throws IOException {
        for (boolean catalogLinked : new boolean[] { false, true }) {
            File root = childRoot(
                catalogLinked ? "converted-linked" : "converted-published");
            OctavoBookTransferStore store = loadedStore(root);
            byte[] epub = bytes(2049, catalogLinked ? 31 : 29);
            OctavoBookManifest manifest = manifest(epub);
            OctavoBookTransferStore.ActiveJob published =
                publishDownload(store, epub, catalogLinked);
            assertEquals(
                catalogLinked
                    ? OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED
                    : OctavoBookTransferStore.Phase.MANAGED_PUBLISHED,
                published.phase);
            long attemptSequence = published.attemptSequence;
            byte[] manifestHash = published.manifestHash();
            File destination = new File(
                store.managedDirectoryForTesting(),
                manifest.digest + ".epub");
            byte[] sameLengthCorruption = epub.clone();
            sameLengthCorruption[127] ^= 0x55;
            writeFile(destination, sameLengthCorruption);

            OctavoBookTransferStore.CleanupOutcome converted =
                store.convertPublishedDownloadToRepairCleanup(
                    published.callbackToken);
            assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                         converted.result);
            assertEquals(attemptSequence, converted.attemptSequence);
            assertNull(store.activeJob());
            assertEquals(0, store.intentCount());
            assertEquals(1, store.cleanupIntentCount());
            assertArrayEquals(sameLengthCorruption,
                              readFile(destination));
            OctavoBookTransferStore.CleanupJob immediate =
                store.cleanupJobs().get(0);
            assertRepairOrigin(immediate, published, manifestHash);
            byte[] callerMutation = immediate.originManifestHash();
            callerMutation[0] ^= 1;
            assertArrayEquals(manifestHash,
                              immediate.originManifestHash());
            assertEquals(
                OctavoBookTransferStore.MutationResult.CONFLICT,
                store.convertPublishedDownloadToRepairCleanup(
                    published.callbackToken).result);

            OctavoBookTransferStore restarted =
                new OctavoBookTransferStore(root);
            assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                         restarted.load());
            assertNull(restarted.activeJob());
            OctavoBookTransferStore.CleanupJob cleanup =
                restarted.cleanupJobs().get(0);
            assertRepairOrigin(cleanup, published, manifestHash);
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                restarted.markCleanupCatalogUnlinked(
                    cleanup.callbackToken));
            cleanup = restarted.cleanupJobs().get(0);
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                restarted.deleteManagedForCleanup(
                    cleanup.callbackToken,
                    restarted.managedDirectoryForTesting()));
            assertFalse(destination.exists());
            cleanup = restarted.cleanupJobs().get(0);
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                restarted.finalizeManagedCleanup(
                    cleanup.callbackToken, true));
            assertEquals(0, restarted.retainedIntentCount());
        }
    }

    @Test
    public void conversionRejectsWrongPhaseDirectionAndStaleToken()
        throws IOException {
        File downloadRoot = childRoot("conversion-phase-rejection");
        OctavoBookTransferStore download = loadedStore(downloadRoot);
        byte[] epub = bytes(4097, 67);
        OctavoBookManifest manifest = manifest(epub);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            download.stageDownload(manifest.encode()).result);
        OctavoBookTransferStore.CallbackToken token =
            download.activeJob().callbackToken;
        assertConversionConflict(download, token);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            download.acceptNextDownloadChunk(
                token, 0, new ByteArrayInputStream(epub)));
        assertEquals(OctavoBookTransferStore.Phase.TRANSFERRING,
                     download.activeJob().phase);
        assertConversionConflict(download, token);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            download.finishDownload(token));
        assertEquals(OctavoBookTransferStore.Phase.BYTES_VERIFIED,
                     download.activeJob().phase);
        assertConversionConflict(download, token);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            download.markReader0Validated(token));
        assertEquals(OctavoBookTransferStore.Phase.READER0_VALIDATED,
                     download.activeJob().phase);
        assertConversionConflict(download, token);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            download.publishManaged(
                token, download.managedDirectoryForTesting()));

        OctavoBookTransferStore restarted =
            new OctavoBookTransferStore(downloadRoot);
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     restarted.load());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            restarted.convertPublishedDownloadToRepairCleanup(token).result);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            restarted.convertPublishedDownloadToRepairCleanup(
                restarted.activeJob().callbackToken).result);

        File uploadRoot = childRoot("conversion-upload-rejection");
        OctavoBookTransferStore upload = loadedStore(uploadRoot);
        byte[] uploadBytes = bytes(701, 73);
        OctavoBookManifest uploadManifest = manifest(uploadBytes);
        File source = new File(
            upload.managedDirectoryForTesting(),
            uploadManifest.digest + ".epub");
        writeFile(source, uploadBytes);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            upload.stageExplicitUpload(source, true, true).result);
        assertConversionConflict(
            upload, upload.activeJob().callbackToken);
        assertEquals(0, upload.cleanupIntentCount());
    }

    @Test
    public void conversionPreservesFullQueueAndActivatesNextDownload()
        throws IOException {
        File root = childRoot("conversion-full-queue");
        OctavoBookTransferStore store = loadedStore(root);
        byte[] firstBytes = bytes(997, 79);
        OctavoBookTransferStore.ActiveJob first =
            publishDownload(store, firstBytes, false);
        byte[] nextBytes = bytes(1231, 83);
        OctavoBookManifest nextManifest = manifest(nextBytes);
        OctavoBookTransferStore.StageOutcome queued =
            store.stageDownload(nextManifest.encode());
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     queued.result);
        assertFalse(queued.active);
        assertEquals(1, store.queuedIntentCount());
        for (int index = 0; index < 61; ++index) {
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                store.stageUncatalogedManagedCleanup(
                    digest(10000 + index), index + 1).result);
        }
        assertEquals(63, store.retainedIntentCount());

        OctavoBookTransferStore.CleanupOutcome converted =
            store.convertPublishedDownloadToRepairCleanup(
                first.callbackToken);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     converted.result);
        assertEquals(first.attemptSequence,
                     converted.attemptSequence);
        assertEquals(63, store.retainedIntentCount());
        assertEquals(62, store.cleanupIntentCount());
        assertEquals(0, store.queuedIntentCount());
        OctavoBookTransferStore.ActiveJob next = store.activeJob();
        assertNotNull(next);
        assertEquals(queued.attemptSequence, next.attemptSequence);
        assertEquals(nextManifest.digest, next.digest);
        assertEquals(OctavoBookTransferStore.Phase.STAGED, next.phase);
        assertTrue(store.partFileForTesting(
            next.digest, next.attemptId).isFile());
        assertRepairOrigin(
            store.cleanupJobs().get(0), first, first.manifestHash());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            store.convertPublishedDownloadToRepairCleanup(
                first.callbackToken).result);

        OctavoBookTransferStore restarted =
            new OctavoBookTransferStore(root);
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     restarted.load());
        assertEquals(63, restarted.retainedIntentCount());
        assertEquals(nextManifest.digest,
                     restarted.activeJob().digest);
        assertRepairOrigin(
            restarted.cleanupJobs().get(0), first,
            first.manifestHash());
    }

    @Test
    public void conversionPublishFailureKeepsPriorAndCanRetry()
        throws IOException {
        File root = childRoot("conversion-certain-failure");
        OctavoBookTransferStore store = loadedStore(root);
        byte[] firstBytes = bytes(911, 89);
        OctavoBookTransferStore.ActiveJob first =
            publishDownload(store, firstBytes, true);
        byte[] nextBytes = bytes(877, 97);
        OctavoBookManifest nextManifest = manifest(nextBytes);
        OctavoBookTransferStore.StageOutcome queued =
            store.stageDownload(nextManifest.encode());
        assertFalse(queued.active);
        File nextPart = store.partFileForTesting(
            nextManifest.digest, queued.attemptId);
        assertFalse(nextPart.exists());
        byte[] prior = store.canonicalBytesForTesting();
        store.failNextPublishForTesting();

        OctavoBookTransferStore.CleanupOutcome failed =
            store.convertPublishedDownloadToRepairCleanup(
                first.callbackToken);
        assertEquals(
            OctavoBookTransferStore.MutationResult.PUBLISH_FAILED,
            failed.result);
        assertEquals(0, failed.attemptSequence);
        assertArrayEquals(prior, store.canonicalBytesForTesting());
        assertEquals(first.attemptSequence,
                     store.activeJob().attemptSequence);
        assertEquals(0, store.cleanupIntentCount());
        assertFalse(nextPart.exists());

        OctavoBookTransferStore.CleanupOutcome retried =
            store.convertPublishedDownloadToRepairCleanup(
                first.callbackToken);
        assertEquals(OctavoBookTransferStore.MutationResult.UPDATED,
                     retried.result);
        assertEquals(first.attemptSequence,
                     retried.attemptSequence);
        assertEquals(nextManifest.digest, store.activeJob().digest);
        assertTrue(nextPart.isFile());
        assertRepairOrigin(
            store.cleanupJobs().get(0), first,
            first.manifestHash());
    }

    @Test
    public void conversionUncertaintyReloadAcceptsPriorOrRepairCandidate()
        throws IOException {
        File candidateRoot = childRoot("conversion-uncertain-candidate");
        OctavoBookTransferStore candidateStore = loadedStore(candidateRoot);
        OctavoBookTransferStore.ActiveJob candidateFirst =
            publishDownload(candidateStore, bytes(733, 101), false);
        byte[] nextBytes = bytes(719, 103);
        OctavoBookManifest nextManifest = manifest(nextBytes);
        OctavoBookTransferStore.StageOutcome candidateNext =
            candidateStore.stageDownload(nextManifest.encode());
        File candidatePart = candidateStore.partFileForTesting(
            nextManifest.digest, candidateNext.attemptId);
        candidateStore.failNextMoveAfterReplaceForTesting();
        OctavoBookTransferStore.CleanupOutcome uncertainCandidate =
            candidateStore.convertPublishedDownloadToRepairCleanup(
                candidateFirst.callbackToken);
        assertEquals(
            OctavoBookTransferStore.MutationResult.PUBLISH_UNCERTAIN,
            uncertainCandidate.result);
        assertEquals(candidateFirst.attemptSequence,
                     uncertainCandidate.attemptSequence);
        assertEquals(candidateFirst.attemptSequence,
                     candidateStore.activeJob().attemptSequence);
        assertEquals(0, candidateStore.cleanupIntentCount());
        assertTrue(candidatePart.isFile());

        OctavoBookTransferStore candidateReload =
            new OctavoBookTransferStore(candidateRoot);
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     candidateReload.load());
        assertEquals(nextManifest.digest,
                     candidateReload.activeJob().digest);
        assertTrue(candidatePart.isFile());
        assertRepairOrigin(
            candidateReload.cleanupJobs().get(0), candidateFirst,
            candidateFirst.manifestHash());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            candidateReload.convertPublishedDownloadToRepairCleanup(
                candidateFirst.callbackToken).result);

        File priorRoot = childRoot("conversion-uncertain-prior");
        OctavoBookTransferStore priorStore = loadedStore(priorRoot);
        byte[] priorBytes = bytes(677, 107);
        OctavoBookTransferStore.ActiveJob priorFirst =
            publishDownload(priorStore, priorBytes, true);
        byte[] priorState = priorStore.canonicalBytesForTesting();
        priorStore.failNextMoveAfterReplaceForTesting();
        OctavoBookTransferStore.CleanupOutcome uncertainPrior =
            priorStore.convertPublishedDownloadToRepairCleanup(
                priorFirst.callbackToken);
        assertEquals(
            OctavoBookTransferStore.MutationResult.PUBLISH_UNCERTAIN,
            uncertainPrior.result);
        writeFile(priorStore.stateFileForTesting(), priorState);

        OctavoBookTransferStore priorReload =
            new OctavoBookTransferStore(priorRoot);
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     priorReload.load());
        assertEquals(priorFirst.attemptSequence,
                     priorReload.activeJob().attemptSequence);
        assertEquals(OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED,
                     priorReload.activeJob().phase);
        assertEquals(0, priorReload.cleanupIntentCount());
        assertEquals(
            OctavoBookTransferStore.MutationResult.CONFLICT,
            priorReload.convertPublishedDownloadToRepairCleanup(
                priorFirst.callbackToken).result);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            priorReload.convertPublishedDownloadToRepairCleanup(
                priorReload.activeJob().callbackToken).result);
    }

    @Test
    public void futureManifestIsRetainedExactlyAndConflictCannotReplaceIt()
        throws IOException {
        OctavoBookTransferStore store = loadedStore();
        byte[] future = new byte[29];
        writeInt(future, 0, OctavoBookManifest.magicForTesting());
        writeInt(future, 4,
                 OctavoBookManifest.versionForTesting() + 1);
        assertEquals(
            OctavoBookTransferStore.MutationResult
                .FUTURE_MANIFEST_RETAINED,
            store.stageDownload(future).result);
        assertArrayEquals(future,
                          store.retainedFutureManifestBytes());
        assertEquals(
            OctavoBookTransferStore.Attention.FUTURE_MANIFEST_RETAINED,
            store.futureManifestAttention());
        assertEquals(
            OctavoBookTransferStore.MutationResult.UNCHANGED,
            store.stageDownload(future.clone()).result);

        byte[] conflict = future.clone();
        conflict[12] = 77;
        assertEquals(
            OctavoBookTransferStore.MutationResult
                .FUTURE_MANIFEST_CONFLICT,
            store.stageDownload(conflict).result);
        assertArrayEquals(future,
                          store.retainedFutureManifestBytes());
        assertEquals(
            OctavoBookTransferStore.Attention.FUTURE_MANIFEST_CONFLICT,
            store.futureManifestAttention());

        OctavoBookTransferStore restarted = store();
        assertEquals(OctavoBookTransferStore.LoadStatus.LOADED,
                     restarted.load());
        assertArrayEquals(future,
                          restarted.retainedFutureManifestBytes());
        assertEquals(
            OctavoBookTransferStore.Attention.FUTURE_MANIFEST_CONFLICT,
            restarted.futureManifestAttention());
    }

    @Test
    public void queueCapFutureAndCorruptPrivateStateAreStrict()
        throws IOException {
        OctavoBookTransferStore store = loadedStore();
        for (int index = 1; index <= 63; ++index) {
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                store.stageUncatalogedManagedCleanup(
                    digest(index), index).result);
        }
        assertEquals(63, store.retainedIntentCount());
        assertEquals(
            OctavoBookTransferStore.MutationResult.LIMIT,
            store.stageUncatalogedManagedCleanup(
                digest(1000), 1).result);
        assertTrue(store.stateFileForTesting().length() < 1024 * 1024);

        File futureRoot = childRoot("future");
        OctavoBookTransferStore future =
            new OctavoBookTransferStore(futureRoot);
        byte[] futureBytes = new byte[32];
        writeInt(futureBytes, 0,
                 OctavoBookTransferStore.storeMagicForTesting());
        writeInt(futureBytes, 4,
                 OctavoBookTransferStore.storeVersionForTesting() + 1);
        writeFile(future.stateFileForTesting(), futureBytes);
        assertEquals(
            OctavoBookTransferStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            future.load());
        assertArrayEquals(futureBytes,
                          readFile(future.stateFileForTesting()));

        File corruptRoot = childRoot("corrupt");
        OctavoBookTransferStore corrupt =
            new OctavoBookTransferStore(corruptRoot);
        byte[] malformed = new byte[32];
        writeInt(malformed, 0,
                 OctavoBookTransferStore.storeMagicForTesting());
        writeInt(malformed, 4,
                 OctavoBookTransferStore.storeVersionForTesting());
        writeFile(corrupt.stateFileForTesting(), malformed);
        assertEquals(
            OctavoBookTransferStore.LoadStatus.CORRUPT_QUARANTINED,
            corrupt.load());
        assertArrayEquals(malformed,
                          readFile(corrupt.quarantineFileForTesting(1)));
        OctavoBookTransferStore corruptReloaded =
            new OctavoBookTransferStore(corruptRoot);
        assertEquals(
            OctavoBookTransferStore.LoadStatus.CORRUPT_QUARANTINED,
            corruptReloaded.load());
        assertTrue(corruptReloaded.hasCorruptQuarantineAttention());

        File directoryEvidenceRoot = childRoot("directory-evidence");
        OctavoBookTransferStore directoryEvidence =
            new OctavoBookTransferStore(directoryEvidenceRoot);
        assertTrue(directoryEvidence.quarantineFileForTesting(1).mkdirs());
        assertEquals(
            OctavoBookTransferStore.LoadStatus.CORRUPT_QUARANTINED,
            directoryEvidence.load());
        assertTrue(directoryEvidence.hasCorruptQuarantineAttention());

        File overboundRoot = childRoot("overbound");
        OctavoBookTransferStore overbound =
            new OctavoBookTransferStore(overboundRoot);
        byte[] tooLarge = new byte[1024 * 1024 + 1];
        writeInt(tooLarge, 0,
                 OctavoBookTransferStore.storeMagicForTesting());
        writeInt(tooLarge, 4,
                 OctavoBookTransferStore.storeVersionForTesting());
        writeFile(overbound.stateFileForTesting(), tooLarge);
        assertEquals(
            OctavoBookTransferStore.LoadStatus.OVERBOUND_BLOCKED,
            overbound.load());
        assertArrayEquals(tooLarge,
                          readFile(overbound.stateFileForTesting()));
        assertFalse(overbound.quarantineFileForTesting(1).exists());

        File fullQuarantineRoot = childRoot("full-quarantine");
        OctavoBookTransferStore fullQuarantine =
            new OctavoBookTransferStore(fullQuarantineRoot);
        byte[] evidence = new byte[] { 1, 2, 3 };
        for (int slot = 1; slot <= 3; ++slot) {
            writeFile(fullQuarantine.quarantineFileForTesting(slot),
                      evidence);
        }
        writeFile(fullQuarantine.stateFileForTesting(), malformed);
        assertEquals(
            OctavoBookTransferStore.LoadStatus.CORRUPT_BLOCKED,
            fullQuarantine.load());
        assertArrayEquals(malformed,
                          readFile(fullQuarantine.stateFileForTesting()));
    }

    private OctavoBookTransferStore store() {
        return new OctavoBookTransferStore(testRoot);
    }

    private OctavoBookTransferStore loadedStore() {
        return loadedStore(testRoot);
    }

    private static OctavoBookTransferStore loadedStore(File root) {
        OctavoBookTransferStore result =
            new OctavoBookTransferStore(root);
        assertEquals(
            OctavoBookTransferStore.LoadStatus.MISSING_EMPTY,
            result.load());
        return result;
    }

    private static OctavoBookTransferStore.ActiveJob publishDownload(
        OctavoBookTransferStore store,
        byte[] epub,
        boolean catalogLinked) throws IOException {
        OctavoBookManifest manifest = manifest(epub);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            store.stageDownload(manifest.encode()).result);
        OctavoBookTransferStore.ActiveJob active = store.activeJob();
        assertNotNull(active);
        int offset = 0;
        for (int index = 0; index < manifest.chunkCount; ++index) {
            int length = manifest.expectedChunkLength(index);
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                store.acceptNextDownloadChunk(
                    active.callbackToken, index,
                    new ByteArrayInputStream(Arrays.copyOfRange(
                        epub, offset, offset + length))));
            offset += length;
        }
        assertEquals(epub.length, offset);
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            store.finishDownload(active.callbackToken));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            store.markReader0Validated(active.callbackToken));
        assertEquals(
            OctavoBookTransferStore.MutationResult.UPDATED,
            store.publishManaged(
                active.callbackToken,
                store.managedDirectoryForTesting()));
        if (catalogLinked) {
            assertEquals(
                OctavoBookTransferStore.MutationResult.UPDATED,
                store.markLocalCatalogLinked(active.callbackToken));
        }
        OctavoBookTransferStore.ActiveJob result = store.activeJob();
        assertNotNull(result);
        return result;
    }

    private static void assertConversionConflict(
        OctavoBookTransferStore store,
        OctavoBookTransferStore.CallbackToken token) {
        OctavoBookTransferStore.CleanupOutcome outcome =
            store.convertPublishedDownloadToRepairCleanup(token);
        assertEquals(OctavoBookTransferStore.MutationResult.CONFLICT,
                     outcome.result);
        assertEquals(0, outcome.attemptSequence);
        assertEquals(0, store.cleanupIntentCount());
    }

    private static void assertRepairOrigin(
        OctavoBookTransferStore.CleanupJob cleanup,
        OctavoBookTransferStore.ActiveJob origin,
        byte[] manifestHash) {
        assertEquals(origin.attemptSequence, cleanup.attemptSequence);
        assertEquals(origin.digest, cleanup.digest);
        assertEquals(origin.byteCount, cleanup.byteCount);
        assertEquals(OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE,
                     cleanup.purpose);
        assertEquals(
            OctavoBookTransferStore.CleanupPhase.AWAITING_CATALOG_UNLINK,
            cleanup.phase);
        assertFalse(cleanup.retryRequired);
        assertEquals(origin.attemptId, cleanup.originAttemptId);
        assertArrayEquals(manifestHash, cleanup.originManifestHash());
    }

    private File childRoot(String name) {
        File result = new File(testRoot, name);
        assertTrue(result.mkdirs());
        return result;
    }

    private static OctavoBookManifest manifest(byte[] bytes)
        throws IOException {
        return OctavoBookManifest.build(new ByteArrayInputStream(bytes));
    }

    private static byte[] bytes(int count, int seed) {
        byte[] result = new byte[count];
        int value = seed;
        for (int index = 0; index < count; ++index) {
            value = value * 1664525 + 1013904223;
            result[index] = (byte)(value >>> 13);
        }
        return result;
    }

    private static String digest(int value) {
        return String.format(Locale.US, "%064x", value);
    }

    private static byte[] readFile(File file) throws IOException {
        byte[] result = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < result.length) {
                int count = input.read(
                    result, offset, result.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            assertEquals(result.length, offset);
            assertEquals(-1, input.read());
        }
        return result;
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

    private static void appendFile(File file, byte[] bytes)
        throws IOException {
        try (FileOutputStream output =
                 new FileOutputStream(file, true)) {
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
