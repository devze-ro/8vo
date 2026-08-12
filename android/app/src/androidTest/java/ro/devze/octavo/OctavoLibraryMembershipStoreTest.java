package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.TreeMap;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibraryMembershipStoreTest {
    private File testRoot;

    @Before
    public void createIsolatedFilesDirectory() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context.getCacheDir());
        testRoot = new File(
            context.getCacheDir(),
            "octavo-library-membership-" + System.nanoTime());
        assertFalse(testRoot.exists());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void receiptBoundWithdrawRestoreAndRestartAreExact()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor book =
            descriptor(1, 101);
        OctavoLibraryMembershipStore store =
            store(testRoot, actor(1));
        assertNull(store.receipt(book));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.MISSING_EMPTY,
            store.load());
        assertFalse(store.stateFileForTesting().exists());
        assertEquals(96, store.metadataBytesForTesting());
        assertEquals(
            OctavoLibraryMembershipPortable.minimumV1Bytes() + 96,
            store.canonicalBytesForTesting().length);

        OctavoLibraryMembershipStore.Receipt absent =
            store.receipt(book);
        assertNotNull(absent);
        assertFalse(absent.recordPresent);
        assertNull(absent.projection);
        assertEquals(0, absent.stateGeneration);
        byte[] pristine = store.canonicalBytesForTesting();
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.INVALID,
            store.restore(absent).result);
        assertArrayEquals(pristine, store.canonicalBytesForTesting());
        assertFalse(store.stateFileForTesting().exists());

        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            store.withdraw(absent).result);
        assertTrue(store.stateFileForTesting().isFile());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            store.loadStatus());
        assertEquals(1, store.counter());
        assertEquals(1, store.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            store.projection(book.digest));
        byte[] withdrawn = readFile(store.stateFileForTesting());

        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.CONFLICT,
            store.withdraw(absent).result);
        assertArrayEquals(withdrawn, readFile(store.stateFileForTesting()));

        OctavoLibraryMembershipStore.Receipt withdrawnReceipt =
            store.receipt(book);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UNCHANGED,
            store.withdraw(withdrawnReceipt).result);
        assertArrayEquals(withdrawn, readFile(store.stateFileForTesting()));
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            store.restore(withdrawnReceipt).result);
        assertEquals(2, store.counter());
        assertEquals(2, store.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.MEMBER,
            store.projection(book.digest));

        OctavoLibraryMembershipStore restarted =
            store(testRoot, actor(99));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            restarted.load());
        assertEquals(actor(1), restarted.actorId());
        assertEquals(2, restarted.counter());
        assertEquals(2, restarted.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.MEMBER,
            restarted.projection(book.digest));
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.CONFLICT,
            restarted.restore(withdrawnReceipt).result);
        assertNull(restarted.receipt(
            new OctavoLibraryMembershipPortable.Descriptor(
                book.digest, book.byteCount + 1)));
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.MEMBER,
            restarted.projection(book.digest));
    }

    @Test
    public void stagingBindsEpochHashesBaseAndConflictAttention()
        throws IOException {
        OctavoLibraryMembershipStore store =
            loadedStore(testRoot, actor(2));
        OctavoLibraryMembershipPortable.Descriptor remoteBook =
            descriptor(2, 202);
        byte[] remote = withdrawnPortable(
            remoteBook, actor(20), 1);

        OctavoLibraryMembershipStore.StageOutcome staged =
            store.stagePortableBytes(remote);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            staged.result);
        assertEquals(1, store.reviewEpoch());
        assertEquals(0, store.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            store.loadStatus());
        assertEquals(160, store.metadataBytesForTesting());
        OctavoLibraryMembershipStore.StagedPortable receipt =
            store.stagedPortable();
        assertNotNull(receipt);
        assertEquals(1, receipt.reviewEpoch);
        assertEquals(sha256Hex(remote), receipt.sha256);
        assertEquals(
            sha256Hex(OctavoLibraryMembershipPortable.encode(
                OctavoLibraryMembershipPortable.Snapshot.empty())),
            receipt.baseSha256);
        byte[] defensive = receipt.bytes();
        defensive[defensive.length - 1] ^= 1;
        assertArrayEquals(remote, store.stagedPortable().bytes());
        byte[] stagedPrivate = readFile(store.stateFileForTesting());

        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult.UNCHANGED,
            store.stagePortableBytes(remote.clone()).result);
        assertEquals(1, store.reviewEpoch());
        assertArrayEquals(stagedPrivate,
                          readFile(store.stateFileForTesting()));

        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.MERGED,
            store.approveStagedPortable(receipt));
        assertNull(store.stagedPortable());
        assertEquals(1, store.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            store.projection(remoteBook.digest));

        OctavoLibraryMembershipPortable.Descriptor second =
            descriptor(3, 303);
        byte[] secondRemote = withdrawnPortable(
            second, actor(21), 1);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            store.stagePortableBytes(secondRemote).result);
        assertEquals(2, store.reviewEpoch());
        OctavoLibraryMembershipStore.StagedPortable beforeLocal =
            store.stagedPortable();
        assertNotNull(beforeLocal);

        OctavoLibraryMembershipPortable.Descriptor local =
            descriptor(4, 404);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            store.withdraw(store.receipt(local)).result);
        assertEquals(
            OctavoLibraryMembershipStore.Attention.STALE_BASE,
            store.attention());
        assertArrayEquals(secondRemote, store.stagedPortable().bytes());
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult
                .STALE_RECEIPT,
            store.approveStagedPortable(beforeLocal));
        OctavoLibraryMembershipStore.StagedPortable stale =
            store.stagedPortable();
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.STALE_BASE,
            store.approveStagedPortable(stale));
        assertEquals(
            OctavoLibraryMembershipStore.PortableDiscardResult.DISCARDED,
            store.discardStagedPortable(stale));

        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            store.stagePortableBytes(secondRemote).result);
        assertEquals(3, store.reviewEpoch());
        byte[] firstRetained = store.stagedPortable().bytes();
        byte[] different = withdrawnPortable(
            descriptor(5, 505), actor(22), 1);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CONFLICT,
            store.stagePortableBytes(different).result);
        assertArrayEquals(firstRetained, store.stagedPortable().bytes());
        assertEquals(3, store.reviewEpoch());
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult
                .STAGED_CONFLICT,
            store.approveStagedPortable(store.stagedPortable()));
        assertEquals(
            OctavoLibraryMembershipStore.PortableDiscardResult.DISCARDED,
            store.discardStagedPortable(store.stagedPortable()));
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            store.stagePortableBytes(secondRemote).result);
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult
                .STALE_RECEIPT,
            store.approveStagedPortable(stale));
    }

    @Test
    public void conflictRequiresExactReceiptAndExplicitTarget()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor book =
            descriptor(18, 1818);
        byte[] conflict = conflictPortable(book, 180);

        File memberRoot = childRoot("resolve-member");
        OctavoLibraryMembershipStore member =
            loadedStore(memberRoot, actor(188));
        OctavoLibraryMembershipStore.Receipt absent =
            member.receipt(book);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            member.stagePortableBytes(conflict).result);
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.MERGED,
            member.approveStagedPortable(member.stagedPortable()));
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.CONFLICT,
            member.projection(book.digest));
        OctavoLibraryMembershipStore.Receipt exactConflict =
            member.receipt(book);
        byte[] before = readFile(member.stateFileForTesting());
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.CONFLICT,
            member.withdraw(exactConflict).result);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.CONFLICT,
            member.restore(exactConflict).result);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.CONFLICT,
            member.resolveConflict(
                absent,
                OctavoLibraryMembershipPortable.Projection.MEMBER).result);
        assertArrayEquals(before, readFile(member.stateFileForTesting()));
        assertEquals(1, member.stateGeneration());
        assertEquals(0, member.counter());

        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            member.resolveConflict(
                exactConflict,
                OctavoLibraryMembershipPortable.Projection.MEMBER).result);
        assertEquals(1, member.counter());
        assertEquals(2, member.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.MEMBER,
            member.projection(book.digest));
        OctavoLibraryMembershipStore memberRestart =
            store(memberRoot, actor(189));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            memberRestart.load());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.MEMBER,
            memberRestart.projection(book.digest));

        File withdrawnRoot = childRoot("resolve-withdrawn");
        OctavoLibraryMembershipStore withdrawn =
            loadedStore(withdrawnRoot, actor(190));
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            withdrawn.stagePortableBytes(conflict).result);
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.MERGED,
            withdrawn.approveStagedPortable(
                withdrawn.stagedPortable()));
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            withdrawn.resolveConflict(
                withdrawn.receipt(book),
                OctavoLibraryMembershipPortable.Projection.WITHDRAWN)
                .result);
        assertEquals(1, withdrawn.counter());
        assertEquals(2, withdrawn.stateGeneration());
        OctavoLibraryMembershipStore withdrawnRestart =
            store(withdrawnRoot, actor(191));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            withdrawnRestart.load());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            withdrawnRestart.projection(book.digest));
    }

    @Test
    public void futureAndInputLimitsAreByteExactAndDurable()
        throws IOException {
        OctavoLibraryMembershipStore store =
            loadedStore(testRoot, actor(3));
        byte[] future = futurePortable(37, 2);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .FUTURE_RETAINED,
            store.stagePortableBytes(future).result);
        assertArrayEquals(future, store.retainedFutureBytes());
        assertEquals(
            OctavoLibraryMembershipStore.Attention.FUTURE_RETAINED,
            store.attention());
        assertEquals(160, store.metadataBytesForTesting());
        byte[] retainedPrivate = readFile(store.stateFileForTesting());

        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult.UNCHANGED,
            store.stagePortableBytes(future.clone()).result);
        byte[] conflict = future.clone();
        conflict[12] = 91;
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CONFLICT,
            store.stagePortableBytes(conflict).result);
        assertArrayEquals(future, store.retainedFutureBytes());
        assertFalse(Arrays.equals(
            retainedPrivate, readFile(store.stateFileForTesting())));
        byte[] conflictPrivate = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CONFLICT,
            store.stagePortableBytes(conflict).result);
        assertArrayEquals(conflictPrivate,
                          readFile(store.stateFileForTesting()));
        OctavoLibraryMembershipStore.StagedPortable futureReceipt =
            store.stagedPortable();
        OctavoLibraryMembershipPortable.Descriptor local =
            descriptor(30, 3030);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            store.withdraw(store.receipt(local)).result);
        assertEquals(
            OctavoLibraryMembershipStore.Attention.STALE_BASE,
            store.attention());
        assertArrayEquals(future, store.retainedFutureBytes());
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult
                .STALE_RECEIPT,
            store.approveStagedPortable(futureReceipt));
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.STALE_BASE,
            store.approveStagedPortable(store.stagedPortable()));
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CONFLICT,
            store.stagePortableBytes(conflict).result);
        assertEquals(
            OctavoLibraryMembershipStore.Attention.STAGED_CONFLICT,
            store.attention());
        assertArrayEquals(future, store.retainedFutureBytes());
        OctavoLibraryMembershipStore conflictRestart =
            store(testRoot, actor(300));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            conflictRestart.load());
        assertEquals(
            OctavoLibraryMembershipStore.Attention.STAGED_CONFLICT,
            conflictRestart.attention());
        assertArrayEquals(future,
                          conflictRestart.retainedFutureBytes());

        File limitRoot = childRoot("portable-limit");
        OctavoLibraryMembershipStore limitStore =
            loadedStore(limitRoot, actor(4));
        byte[] overbound = new byte[
            OctavoLibraryMembershipPortable.maximumFutureBytes() + 1];
        writeInt(overbound, 0,
                 OctavoLibraryMembershipPortable.magicForTesting());
        writeInt(overbound, 4, 2);
        byte[] before = limitStore.canonicalBytesForTesting();
        OctavoLibraryMembershipStore.StageOutcome limited =
            limitStore.stagePortableBytes(overbound);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult.LIMIT,
            limited.result);
        assertEquals(
            OctavoLibraryMembershipPortable.LimitScope.INPUT,
            limited.limitScope);
        assertEquals(
            OctavoLibraryMembershipPortable.LimitReason.ENCODED_BYTES,
            limited.limitReason);
        assertArrayEquals(before, limitStore.canonicalBytesForTesting());
        assertFalse(limitStore.stateFileForTesting().exists());

        File futureStoreRoot = childRoot("private-future");
        OctavoLibraryMembershipStore futureStore =
            store(futureStoreRoot, actor(5));
        byte[] futurePrivate = new byte[8];
        writeInt(futurePrivate, 0,
                 OctavoLibraryMembershipStore.storeMagicForTesting());
        writeInt(futurePrivate, 4, 0xffffffff);
        writeFile(futureStore.stateFileForTesting(), futurePrivate);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .FUTURE_VERSION_BLOCKED,
            futureStore.load());
        assertArrayEquals(futurePrivate,
                          readFile(futureStore.stateFileForTesting()));
        assertFalse(futureStore.quarantineFileForTesting(1).exists());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .FUTURE_VERSION_BLOCKED,
            futureStore.load());
        assertArrayEquals(futurePrivate,
                          readFile(futureStore.stateFileForTesting()));
    }

    @Test
    public void publicationFailureAndUncertaintyAcceptOnlyPriorOrCandidate()
        throws IOException {
        File candidateRoot = childRoot("uncertain-candidate");
        OctavoLibraryMembershipStore candidateStore =
            loadedStore(candidateRoot, actor(6));
        OctavoLibraryMembershipPortable.Descriptor book =
            descriptor(6, 606);
        OctavoLibraryMembershipStore.Receipt absent =
            candidateStore.receipt(book);
        byte[] pristine = candidateStore.canonicalBytesForTesting();
        candidateStore.failNextPublishForTesting();
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.PUBLISH_FAILED,
            candidateStore.withdraw(absent).result);
        assertArrayEquals(pristine,
                          candidateStore.canonicalBytesForTesting());
        assertFalse(candidateStore.stateFileForTesting().exists());
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            candidateStore.withdraw(absent).result);

        OctavoLibraryMembershipStore.Receipt withdrawn =
            candidateStore.receipt(book);
        byte[] prior = readFile(candidateStore.stateFileForTesting());
        candidateStore.failNextMoveAfterReplaceForTesting();
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult
                .PUBLISH_UNCERTAIN,
            candidateStore.restore(withdrawn).result);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .PUBLISH_UNCERTAIN_BLOCKED,
            candidateStore.loadStatus());
        assertArrayEquals(prior,
                          candidateStore.canonicalBytesForTesting());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            candidateStore.load());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.MEMBER,
            candidateStore.projection(book.digest));

        File priorRoot = childRoot("uncertain-prior");
        OctavoLibraryMembershipStore priorStore =
            loadedStore(priorRoot, actor(7));
        OctavoLibraryMembershipPortable.Descriptor priorBook =
            descriptor(7, 707);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            priorStore.withdraw(priorStore.receipt(priorBook)).result);
        byte[] exactPrior = readFile(priorStore.stateFileForTesting());
        priorStore.failNextMoveAfterReplaceForTesting();
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult
                .PUBLISH_UNCERTAIN,
            priorStore.restore(priorStore.receipt(priorBook)).result);
        writeFile(priorStore.stateFileForTesting(), exactPrior);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            priorStore.load());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            priorStore.projection(priorBook.digest));

        File otherRoot = childRoot("uncertain-other");
        OctavoLibraryMembershipStore other =
            loadedStore(otherRoot, actor(8));
        OctavoLibraryMembershipPortable.Descriptor otherBook =
            descriptor(8, 808);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            other.withdraw(other.receipt(otherBook)).result);
        byte[] otherPrivate = readFile(other.stateFileForTesting());

        priorStore.failNextMoveAfterReplaceForTesting();
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult
                .PUBLISH_UNCERTAIN,
            priorStore.restore(priorStore.receipt(priorBook)).result);
        writeFile(priorStore.stateFileForTesting(), otherPrivate);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .PUBLISH_UNCERTAIN_BLOCKED,
            priorStore.load());
        assertArrayEquals(otherPrivate,
                          readFile(priorStore.stateFileForTesting()));
    }

    @Test
    public void corruptStateNeverPublishesEmptyAndRecoveryUsesFreshActor()
        throws IOException {
        File corruptRoot = childRoot("corrupt-recovery");
        OctavoLibraryMembershipStore authored =
            loadedStore(corruptRoot, actor(9));
        OctavoLibraryMembershipPortable.Descriptor book =
            descriptor(9, 909);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            authored.withdraw(authored.receipt(book)).result);
        byte[] portable = authored.exportPortable().bytes();
        assertNotNull(portable);
        byte[] corrupt = readFile(authored.stateFileForTesting());
        corrupt[corrupt.length - 1] ^= 1;
        writeFile(authored.stateFileForTesting(), corrupt);

        OctavoLibraryMembershipStore blocked =
            store(corruptRoot, actor(90));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .CORRUPT_QUARANTINED_BLOCKED,
            blocked.load());
        assertFalse(blocked.stateFileForTesting().exists());
        assertArrayEquals(
            corrupt, readFile(blocked.quarantineFileForTesting(1)));
        assertNull(blocked.receipt(book));
        assertEquals(
            OctavoLibraryMembershipStore.RecoveryResult.STALE_DIGEST,
            blocked.recoverFromReviewedPortable(
                portable, digest(999)));
        assertFalse(blocked.stateFileForTesting().exists());

        assertEquals(
            OctavoLibraryMembershipStore.RecoveryResult.RECOVERED,
            blocked.recoverFromReviewedPortable(
                portable, sha256Hex(portable)));
        assertTrue(blocked.stateFileForTesting().isFile());
        assertTrue(blocked.quarantineFileForTesting(1).isFile());
        assertNotEquals(actor(9), blocked.actorId());
        assertNotEquals(actor(90), blocked.actorId());
        assertEquals(0, blocked.counter());
        assertEquals(1, blocked.stateGeneration());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            blocked.projection(book.digest));

        OctavoLibraryMembershipStore restarted =
            store(corruptRoot, actor(91));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .LOADED_QUARANTINE_ATTENTION,
            restarted.load());
        assertNotNull(restarted.receipt(book));

        File missingRoot = childRoot("missing-quarantine");
        OctavoLibraryMembershipStore missing =
            store(missingRoot, actor(10));
        writeFile(missing.quarantineFileForTesting(1),
                  new byte[] {1, 2, 3});
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .CORRUPT_QUARANTINED_BLOCKED,
            missing.load());
        assertFalse(missing.stateFileForTesting().exists());

        File fullRoot = childRoot("full-quarantine");
        OctavoLibraryMembershipStore full =
            store(fullRoot, actor(11));
        byte[] malformed = new byte[116];
        writeFile(full.stateFileForTesting(), malformed);
        for (int slot = 1; slot <= 3; ++slot) {
            writeFile(full.quarantineFileForTesting(slot),
                      new byte[] {(byte)slot});
        }
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.CORRUPT_BLOCKED,
            full.load());
        assertArrayEquals(malformed, readFile(full.stateFileForTesting()));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.CORRUPT_BLOCKED,
            full.load());
        assertArrayEquals(malformed, readFile(full.stateFileForTesting()));

        File overboundRoot = childRoot("overbound-private");
        OctavoLibraryMembershipStore overboundStore =
            store(overboundRoot, actor(12));
        byte[] overbound = new byte[
            OctavoLibraryMembershipStore.maximumFileBytesForTesting() + 1];
        overbound[0] = 17;
        overbound[overbound.length - 1] = 29;
        writeFile(overboundStore.stateFileForTesting(), overbound);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.OVERBOUND_BLOCKED,
            overboundStore.load());
        assertArrayEquals(overbound,
                          readFile(overboundStore.stateFileForTesting()));
        assertFalse(overboundStore.quarantineFileForTesting(1).exists());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.OVERBOUND_BLOCKED,
            overboundStore.load());
        assertArrayEquals(overbound,
                          readFile(overboundStore.stateFileForTesting()));
    }

    @Test
    public void crcValidPrivateSemanticCorruptionIsQuarantined()
        throws IOException {
        OctavoLibraryMembershipStore authored =
            loadedStore(testRoot, actor(13));
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            authored.withdraw(authored.receipt(
                descriptor(13, 1313))).result);
        byte[] invalid = readFile(authored.stateFileForTesting());
        // Attention is at byte 68 in the fixed field-count-8 O1MS prefix.
        writeInt(invalid, 68,
                 OctavoLibraryMembershipStore.Attention
                     .CURRENT_APPROVAL.wireId);
        repairChecksum(invalid);
        writeFile(authored.stateFileForTesting(), invalid);

        OctavoLibraryMembershipStore rejected =
            store(testRoot, actor(14));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .CORRUPT_QUARANTINED_BLOCKED,
            rejected.load());
        assertArrayEquals(
            invalid, readFile(rejected.quarantineFileForTesting(1)));
        assertFalse(rejected.stateFileForTesting().exists());

        File staleRoot = childRoot("crc-valid-staged-base");
        OctavoLibraryMembershipStore staged =
            loadedStore(staleRoot, actor(15));
        byte[] incoming = withdrawnPortable(
            descriptor(15, 1515), actor(16), 1);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            staged.stagePortableBytes(incoming).result);
        byte[] staleBase = readFile(staged.stateFileForTesting());
        // Staged SHA is bytes 92..123; base SHA is bytes 124..155.
        staleBase[124] ^= 1;
        repairChecksum(staleBase);
        writeFile(staged.stateFileForTesting(), staleBase);
        OctavoLibraryMembershipStore staleRejected =
            store(staleRoot, actor(17));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .CORRUPT_QUARANTINED_BLOCKED,
            staleRejected.load());
        assertArrayEquals(
            staleBase,
            readFile(staleRejected.quarantineFileForTesting(1)));
    }

    @Test
    public void destinationFreshnessRejectsSameSessionAppearanceAndLoss()
        throws IOException {
        File appearanceRoot = childRoot("freshness-appearance");
        OctavoLibraryMembershipStore missing =
            loadedStore(appearanceRoot, actor(30));
        byte[] unexpected = currentPrivateState(
            actor(31), 0, 0,
            OctavoLibraryMembershipPortable.encode(
                OctavoLibraryMembershipPortable.Snapshot.empty()));
        writeFile(missing.stateFileForTesting(), unexpected);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .PUBLISH_UNCERTAIN_BLOCKED,
            missing.load());
        assertArrayEquals(unexpected,
                          readFile(missing.stateFileForTesting()));
        assertTrue(missing.stateFileForTesting().delete());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.MISSING_EMPTY,
            missing.load());

        File lossRoot = childRoot("freshness-loss");
        OctavoLibraryMembershipStore loaded =
            loadedStore(lossRoot, actor(32));
        OctavoLibraryMembershipPortable.Descriptor book =
            descriptor(32, 3232);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            loaded.withdraw(loaded.receipt(book)).result);
        byte[] exactPrior = readFile(loaded.stateFileForTesting());
        assertTrue(loaded.stateFileForTesting().delete());
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .PUBLISH_UNCERTAIN_BLOCKED,
            loaded.load());
        writeFile(loaded.stateFileForTesting(), exactPrior);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            loaded.load());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            loaded.projection(book.digest));
    }

    @Test
    public void publicationLockSerializesStoresAndPreMoveRechecksDestination()
        throws IOException {
        File sharedRoot = childRoot("cross-instance-lock");
        OctavoLibraryMembershipStore first =
            loadedStore(sharedRoot, actor(33));
        OctavoLibraryMembershipStore second =
            loadedStore(sharedRoot, actor(34));
        OctavoLibraryMembershipPortable.Descriptor firstBook =
            descriptor(33, 3333);
        OctavoLibraryMembershipPortable.Descriptor secondBook =
            descriptor(34, 3434);
        OctavoLibraryMembershipStore.Receipt firstReceipt =
            first.receipt(firstBook);
        OctavoLibraryMembershipStore.Receipt secondReceipt =
            second.receipt(secondBook);
        File lockParent = first.lockFileForTesting().getParentFile();
        assertNotNull(lockParent);
        assertTrue(lockParent.isDirectory() || lockParent.mkdirs());
        try (RandomAccessFile access = new RandomAccessFile(
                 first.lockFileForTesting(), "rw");
             FileChannel channel = access.getChannel();
             FileLock heldLock = channel.lock()) {
            assertTrue(heldLock.isValid());
            assertEquals(
                OctavoLibraryMembershipStore.MutationResult.PUBLISH_FAILED,
                first.withdraw(firstReceipt).result);
            assertFalse(first.stateFileForTesting().exists());
            assertFalse(first.temporaryFileForTesting().exists());
        }
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            first.withdraw(firstReceipt).result);
        byte[] firstAuthority = readFile(first.stateFileForTesting());
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult
                .PUBLISH_UNCERTAIN,
            second.withdraw(secondReceipt).result);
        assertArrayEquals(firstAuthority,
                          readFile(first.stateFileForTesting()));
        assertFalse(second.temporaryFileForTesting().exists());

        File recheckRoot = childRoot("pre-move-recheck");
        OctavoLibraryMembershipStore rechecked =
            loadedStore(recheckRoot, actor(35));
        byte[] unexpected = currentPrivateState(
            actor(36), 0, 0,
            OctavoLibraryMembershipPortable.encode(
                OctavoLibraryMembershipPortable.Snapshot.empty()));
        rechecked.replaceDestinationAfterTempSyncForTesting(unexpected);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult
                .PUBLISH_UNCERTAIN,
            rechecked.withdraw(rechecked.receipt(
                descriptor(35, 3535))).result);
        assertArrayEquals(unexpected,
                          readFile(rechecked.stateFileForTesting()));
        assertFalse(rechecked.temporaryFileForTesting().exists());
        assertEquals(0, rechecked.stateGeneration());
    }

    @Test
    public void actorRotationSelfAheadAndCounterExhaustionAreAtomic()
        throws IOException {
        File selfAheadRoot = childRoot("self-ahead");
        String reusedActor = actor(40);
        OctavoLibraryMembershipStore selfAhead =
            loadedStore(selfAheadRoot, reusedActor);
        OctavoLibraryMembershipPortable.Descriptor aheadBook =
            descriptor(40, 4040);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            selfAhead.stagePortableBytes(withdrawnPortable(
                aheadBook, reusedActor, 5)).result);
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.MERGED,
            selfAhead.approveStagedPortable(
                selfAhead.stagedPortable()));
        assertNotEquals(reusedActor, selfAhead.actorId());
        assertFalse(selfAhead.snapshot().actorAppears(selfAhead.actorId()));
        assertEquals(0, selfAhead.counter());

        File rotateRoot = childRoot("counter-rotate");
        byte[] emptyPortable = OctavoLibraryMembershipPortable.encode(
            OctavoLibraryMembershipPortable.Snapshot.empty());
        OctavoLibraryMembershipStore rotate =
            store(rotateRoot, actor(41));
        writeFile(rotate.stateFileForTesting(), currentPrivateState(
            actor(41), Long.MAX_VALUE, 0, emptyPortable));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            rotate.load());
        OctavoLibraryMembershipPortable.Descriptor rotatedBook =
            descriptor(41, 4141);
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.UPDATED,
            rotate.withdraw(rotate.receipt(rotatedBook)).result);
        assertNotEquals(actor(41), rotate.actorId());
        assertEquals(1, rotate.counter());

        File exhaustedRoot = childRoot("counter-exhausted");
        String exhaustedActor = actor(42);
        OctavoLibraryMembershipPortable.Descriptor exhaustedBook =
            descriptor(42, 4242);
        TreeMap<String, Long> frontier = new TreeMap<>();
        TreeMap<String, Long> context = new TreeMap<>();
        frontier.put(exhaustedActor, Long.MAX_VALUE);
        for (int index = 1; index < 16; ++index) {
            String value = actor(42 + index);
            frontier.put(value, 1L);
            context.put(value, 1L);
        }
        String mutationId = OctavoLibraryMembershipPortable
            .mutationIdForTesting(
                exhaustedBook, exhaustedActor, Long.MAX_VALUE,
                OctavoLibraryMembershipPortable.Operation.WITHDRAW,
                context);
        OctavoLibraryMembershipPortable.Record fullRecord =
            new OctavoLibraryMembershipPortable.Record(
                exhaustedBook, frontier,
                Collections.singletonList(
                    new OctavoLibraryMembershipPortable.Mutation(
                        mutationId, exhaustedActor, Long.MAX_VALUE,
                        OctavoLibraryMembershipPortable.Operation
                            .WITHDRAW,
                        context)));
        OctavoLibraryMembershipPortable.Snapshot fullFrontier =
            new OctavoLibraryMembershipPortable.Snapshot(
                Collections.singletonList(fullRecord));
        OctavoLibraryMembershipStore exhausted =
            store(exhaustedRoot, actor(99));
        writeFile(exhausted.stateFileForTesting(), currentPrivateState(
            exhaustedActor, Long.MAX_VALUE, 7,
            OctavoLibraryMembershipPortable.encode(fullFrontier)));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            exhausted.load());
        byte[] before = readFile(exhausted.stateFileForTesting());
        OctavoLibraryMembershipStore.MutationOutcome limited =
            exhausted.restore(exhausted.receipt(exhaustedBook));
        assertEquals(
            OctavoLibraryMembershipStore.MutationResult.LIMIT,
            limited.result);
        assertEquals(
            OctavoLibraryMembershipPortable.LimitScope.LOCAL,
            limited.limitScope);
        assertEquals(
            OctavoLibraryMembershipPortable.LimitReason
                .COUNTER_EXHAUSTED,
            limited.limitReason);
        assertEquals(exhaustedActor, exhausted.actorId());
        assertEquals(Long.MAX_VALUE, exhausted.counter());
        assertEquals(7, exhausted.stateGeneration());
        assertArrayEquals(before, readFile(exhausted.stateFileForTesting()));
    }

    @Test
    public void joinLimitIsRetainedAndGenerationExhaustionRollsBack()
        throws IOException {
        File limitRoot = childRoot("retained-join-limit");
        ArrayList<OctavoLibraryMembershipPortable.Record> records =
            new ArrayList<>();
        for (int index = 1; index <= 63; ++index) {
            OctavoLibraryMembershipPortable.Descriptor descriptor =
                descriptor(100 + index, 5000 + index);
            OctavoLibraryMembershipPortable.MutationResult mutation =
                OctavoLibraryMembershipPortable.withdraw(
                    OctavoLibraryMembershipPortable.Snapshot.empty(),
                    descriptor, actor(100 + index), 1);
            assertEquals(
                OctavoLibraryMembershipPortable.MutationStatus.MUTATED,
                mutation.status);
            records.add(mutation.snapshot.record(descriptor.digest));
        }
        OctavoLibraryMembershipPortable.Snapshot full =
            new OctavoLibraryMembershipPortable.Snapshot(records);
        OctavoLibraryMembershipStore limitStore =
            store(limitRoot, actor(999));
        byte[] fullBytes = OctavoLibraryMembershipPortable.encode(full);
        writeFile(limitStore.stateFileForTesting(), currentPrivateState(
            actor(999), 0, 4, fullBytes));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            limitStore.load());
        byte[] overflow = withdrawnPortable(
            descriptor(1000, 6000), actor(1000), 1);
        OctavoLibraryMembershipStore.StageOutcome retained =
            limitStore.stagePortableBytes(overflow);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .LIMIT_RETAINED,
            retained.result);
        assertEquals(
            OctavoLibraryMembershipPortable.LimitScope.JOIN,
            retained.limitScope);
        assertEquals(
            OctavoLibraryMembershipPortable.LimitReason.RECORD_HISTORY,
            retained.limitReason);
        assertArrayEquals(overflow, limitStore.stagedPortable().bytes());
        assertArrayEquals(fullBytes, limitStore.exportPortable().bytes());
        byte[] retainedState = readFile(limitStore.stateFileForTesting());
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult
                .LIMIT_RETAINED,
            limitStore.approveStagedPortable(
                limitStore.stagedPortable()));
        assertArrayEquals(retainedState,
                          readFile(limitStore.stateFileForTesting()));

        File forgedLimitRoot = childRoot("forged-join-limit");
        OctavoLibraryMembershipStore forgedLimit =
            store(forgedLimitRoot, actor(998));
        byte[] impossibleReason = retainedState.clone();
        writeInt(impossibleReason, 76, 5); // COUNTER_EXHAUSTED
        repairChecksum(impossibleReason);
        writeFile(forgedLimit.stateFileForTesting(), impossibleReason);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .CORRUPT_QUARANTINED_BLOCKED,
            forgedLimit.load());
        assertArrayEquals(
            impossibleReason,
            readFile(forgedLimit.quarantineFileForTesting(1)));

        File forgedApprovalRoot = childRoot("forged-approval");
        OctavoLibraryMembershipStore forgedApproval =
            store(forgedApprovalRoot, actor(997));
        byte[] falseApproval = retainedState.clone();
        writeInt(falseApproval, 68,
                 OctavoLibraryMembershipStore.Attention
                     .CURRENT_APPROVAL.wireId);
        writeInt(falseApproval, 72, 0);
        writeInt(falseApproval, 76, 0);
        repairChecksum(falseApproval);
        writeFile(forgedApproval.stateFileForTesting(), falseApproval);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus
                .CORRUPT_QUARANTINED_BLOCKED,
            forgedApproval.load());
        assertArrayEquals(
            falseApproval,
            readFile(forgedApproval.quarantineFileForTesting(1)));

        File generationRoot = childRoot("generation-exhausted");
        OctavoLibraryMembershipStore generation =
            store(generationRoot, actor(70));
        byte[] empty = OctavoLibraryMembershipPortable.encode(
            OctavoLibraryMembershipPortable.Snapshot.empty());
        writeFile(generation.stateFileForTesting(), currentPrivateState(
            actor(70), 0, Long.MAX_VALUE, empty));
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            generation.load());
        byte[] changed = withdrawnPortable(
            descriptor(70, 7070), actor(71), 1);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            generation.stagePortableBytes(changed).result);
        OctavoLibraryMembershipStore.StagedPortable staged =
            generation.stagedPortable();
        byte[] exactBefore = readFile(generation.stateFileForTesting());
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.EXHAUSTED,
            generation.approveStagedPortable(staged));
        assertEquals(0, generation.counter());
        assertEquals(Long.MAX_VALUE, generation.stateGeneration());
        assertTrue(staged.sameIdentity(generation.stagedPortable()));
        assertArrayEquals(exactBefore,
                          readFile(generation.stateFileForTesting()));

        assertEquals(
            OctavoLibraryMembershipStore.PortableDiscardResult.DISCARDED,
            generation.discardStagedPortable(
                generation.stagedPortable()));
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult
                .STAGED_CURRENT,
            generation.stagePortableBytes(empty).result);
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.UNCHANGED,
            generation.approveStagedPortable(
                generation.stagedPortable()));
        assertEquals(Long.MAX_VALUE, generation.stateGeneration());
        assertNull(generation.stagedPortable());
    }

    @Test
    public void reviewEpochExhaustionRejectsFirstStageWithoutMutation()
        throws IOException {
        File root = childRoot("review-epoch-exhausted");
        String localActor = actor(72);
        byte[] empty = OctavoLibraryMembershipPortable.encode(
            OctavoLibraryMembershipPortable.Snapshot.empty());
        byte[] privateState = currentPrivateState(
            localActor, 0, 9, empty);
        writeLong(privateState, 60, Long.MAX_VALUE);
        repairChecksum(privateState);

        OctavoLibraryMembershipStore store = store(root, actor(73));
        writeFile(store.stateFileForTesting(), privateState);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.LOADED,
            store.load());
        assertEquals(localActor, store.actorId());
        assertEquals(0, store.counter());
        assertEquals(9, store.stateGeneration());
        assertEquals(Long.MAX_VALUE, store.reviewEpoch());
        assertNull(store.stagedPortable());
        assertEquals(
            OctavoLibraryMembershipStore.Attention.NONE,
            store.attention());
        byte[] exactBefore = readFile(store.stateFileForTesting());
        byte[] snapshotBefore = store.exportPortable().bytes();

        byte[] candidate = withdrawnPortable(
            descriptor(72, 7272), actor(74), 1);
        OctavoLibraryMembershipStore.StageOutcome exhausted =
            store.stagePortableBytes(candidate);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult.EXHAUSTED,
            exhausted.result);
        assertNull(exhausted.limitScope);
        assertNull(exhausted.limitReason);
        assertEquals(localActor, store.actorId());
        assertEquals(0, store.counter());
        assertEquals(9, store.stateGeneration());
        assertEquals(Long.MAX_VALUE, store.reviewEpoch());
        assertNull(store.stagedPortable());
        assertEquals(
            OctavoLibraryMembershipStore.Attention.NONE,
            store.attention());
        assertArrayEquals(snapshotBefore, store.exportPortable().bytes());
        assertArrayEquals(exactBefore,
                          readFile(store.stateFileForTesting()));
    }

    private File childRoot(String name) {
        File result = new File(testRoot, name);
        assertTrue(result.mkdir());
        return result;
    }

    private static OctavoLibraryMembershipStore loadedStore(
        File root,
        String actor) {
        OctavoLibraryMembershipStore store = store(root, actor);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.MISSING_EMPTY,
            store.load());
        return store;
    }

    private static OctavoLibraryMembershipStore store(
        File root,
        String actor) {
        return new OctavoLibraryMembershipStore(root, actor);
    }

    private static byte[] withdrawnPortable(
        OctavoLibraryMembershipPortable.Descriptor descriptor,
        String actor,
        long counter) throws IOException {
        OctavoLibraryMembershipPortable.MutationResult result =
            OctavoLibraryMembershipPortable.withdraw(
                OctavoLibraryMembershipPortable.Snapshot.empty(),
                descriptor, actor, counter);
        assertEquals(
            OctavoLibraryMembershipPortable.MutationStatus.MUTATED,
            result.status);
        return OctavoLibraryMembershipPortable.encode(result.snapshot);
    }

    private static byte[] conflictPortable(
        OctavoLibraryMembershipPortable.Descriptor descriptor,
        int firstActor) throws IOException {
        String observedActor = actor(firstActor);
        String withdrawActor = actor(firstActor + 1);
        String restoreActor = actor(firstActor + 2);
        TreeMap<String, Long> context = new TreeMap<>();
        context.put(observedActor, 1L);

        OctavoLibraryMembershipPortable.Mutation withdraw =
            new OctavoLibraryMembershipPortable.Mutation(
                OctavoLibraryMembershipPortable.mutationIdForTesting(
                    descriptor, withdrawActor, 1,
                    OctavoLibraryMembershipPortable.Operation.WITHDRAW,
                    context),
                withdrawActor, 1,
                OctavoLibraryMembershipPortable.Operation.WITHDRAW,
                context);
        OctavoLibraryMembershipPortable.Mutation restore =
            new OctavoLibraryMembershipPortable.Mutation(
                OctavoLibraryMembershipPortable.mutationIdForTesting(
                    descriptor, restoreActor, 1,
                    OctavoLibraryMembershipPortable.Operation.RESTORE,
                    context),
                restoreActor, 1,
                OctavoLibraryMembershipPortable.Operation.RESTORE,
                context);
        TreeMap<String, Long> frontier = new TreeMap<>();
        frontier.put(observedActor, 1L);
        frontier.put(withdrawActor, 1L);
        frontier.put(restoreActor, 1L);
        OctavoLibraryMembershipPortable.Record record =
            new OctavoLibraryMembershipPortable.Record(
                descriptor, frontier, Arrays.asList(withdraw, restore));
        return OctavoLibraryMembershipPortable.encode(
            new OctavoLibraryMembershipPortable.Snapshot(
                Collections.singletonList(record)));
    }

    private static OctavoLibraryMembershipPortable.Descriptor descriptor(
        int digest,
        long byteCount) {
        return new OctavoLibraryMembershipPortable.Descriptor(
            digest(digest), byteCount);
    }

    private static String digest(int value) {
        return String.format(Locale.US, "%064x", value);
    }

    private static String actor(int value) {
        return String.format(Locale.US, "%032x", value);
    }

    private static byte[] futurePortable(int length, int version) {
        byte[] result = new byte[length];
        writeInt(result, 0,
                 OctavoLibraryMembershipPortable.magicForTesting());
        writeInt(result, 4, version);
        return result;
    }

    private static byte[] currentPrivateState(
        String actor,
        long counter,
        long generation,
        byte[] currentPortable) {
        byte[] result = new byte[96 + currentPortable.length];
        writeInt(result, 0,
                 OctavoLibraryMembershipStore.storeMagicForTesting());
        writeInt(result, 4,
                 OctavoLibraryMembershipStore.storeVersionForTesting());
        writeInt(result, 8, 8);
        byte[] actorBytes = actor.getBytes(StandardCharsets.US_ASCII);
        assertEquals(32, actorBytes.length);
        System.arraycopy(actorBytes, 0, result, 12, actorBytes.length);
        writeLong(result, 44, counter);
        writeLong(result, 52, generation);
        writeLong(result, 60, 0);
        writeInt(result, 68,
                 OctavoLibraryMembershipStore.Attention.NONE.wireId);
        writeInt(result, 72, 0);
        writeInt(result, 76, 0);
        writeInt(result, 80, currentPortable.length);
        writeInt(result, 84, 0);
        writeInt(result, 88, 0);
        System.arraycopy(currentPortable, 0, result, 92,
                         currentPortable.length);
        repairChecksum(result);
        return result;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) {
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
        byte[] result = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < result.length) {
                int count = input.read(
                    result, offset, result.length - offset);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    offset += count;
                }
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
        try (FileOutputStream output = new FileOutputStream(file, false)) {
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
