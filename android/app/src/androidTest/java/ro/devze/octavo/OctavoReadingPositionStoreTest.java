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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoReadingPositionStoreTest {
    private static final String BOOK = digest(1);
    private static final String OTHER_BOOK = digest(2);
    private static final String LOCAL = device(1);
    private static final String REMOTE_A = device(100);
    private static final String REMOTE_B = device(101);

    private File testRoot;

    @Before
    public void createIsolatedFilesDirectory() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context.getCacheDir());
        testRoot = new File(
            context.getCacheDir(),
            "octavo-reading-position-" + System.nanoTime());
        assertFalse(testRoot.exists());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void portableWireIsCanonicalBoundedAndRoundTripsExactMaximum()
        throws IOException {
        ArrayList<OctavoReadingPositionPortable.Lane> lanes =
            new ArrayList<>();
        for (int index = 15; index >= 0; --index) {
            lanes.add(new OctavoReadingPositionPortable.Lane(
                device(1000 + index),
                Long.MAX_VALUE - index,
                0xffffffffL - index,
                Long.MAX_VALUE - index));
        }
        OctavoReadingPositionPortable.Snapshot snapshot =
            new OctavoReadingPositionPortable.Snapshot(BOOK, lanes);
        byte[] bytes = OctavoReadingPositionPortable.encode(snapshot);

        assertEquals(1048, bytes.length);
        assertEquals(OctavoReadingPositionPortable.maximumV1Bytes(),
                     bytes.length);
        assertEquals(OctavoReadingPositionPortable.magicForTesting(),
                     readInt(bytes, 0));
        assertEquals(OctavoReadingPositionPortable.versionForTesting(),
                     readInt(bytes, Integer.BYTES));
        assertEquals(1, readInt(bytes, 2 * Integer.BYTES));
        assertEquals(64, readInt(bytes, 3 * Integer.BYTES));
        assertEquals(16, readInt(bytes, 80));

        OctavoReadingPositionPortable.DecodeResult decoded =
            OctavoReadingPositionPortable.decode(bytes);
        assertEquals(OctavoReadingPositionPortable.DecodeStatus.READY,
                     decoded.status);
        assertEquals(16, decoded.snapshot().laneCount());
        assertEquals(device(1000),
                     decoded.snapshot().lanes().get(0).deviceId);
        assertEquals(device(1015),
                     decoded.snapshot().lanes().get(15).deviceId);
        assertArrayEquals(bytes,
                          OctavoReadingPositionPortable.encode(
                              decoded.snapshot()));

        List<OctavoReadingPositionPortable.Lane> semanticOrder =
            OctavoReadingPositionPortable.reviewOrder(
                decoded.snapshot().lanes());
        assertEquals(device(1000), semanticOrder.get(0).deviceId);
        assertEquals(device(1015), semanticOrder.get(15).deviceId);
    }

    @Test
    public void portableRejectsEveryNoncanonicalAndOverboundShape()
        throws IOException {
        byte[] canonical = twoLaneBytes();
        assertReady(canonical);

        byte[] badChecksum = canonical.clone();
        badChecksum[badChecksum.length - 1] ^= 1;
        assertInvalid(badChecksum);

        assertInvalid(Arrays.copyOf(canonical, canonical.length - 1));

        byte[] wrongHeaderCount = canonical.clone();
        putInt(wrongHeaderCount, 8, 2);
        updateChecksum(wrongHeaderCount);
        assertInvalid(wrongHeaderCount);

        byte[] uppercaseDigest = canonical.clone();
        uppercaseDigest[16] = 'A';
        updateChecksum(uppercaseDigest);
        assertInvalid(uppercaseDigest);

        byte[] uppercaseDevice = canonical.clone();
        uppercaseDevice[88] = 'A';
        updateChecksum(uppercaseDevice);
        assertInvalid(uppercaseDevice);

        byte[] zeroSequence = canonical.clone();
        putLong(zeroSequence, 120, 0);
        updateChecksum(zeroSequence);
        assertInvalid(zeroSequence);

        byte[] overboundSpine = canonical.clone();
        putLong(overboundSpine, 128, 0x100000000L);
        updateChecksum(overboundSpine);
        assertInvalid(overboundSpine);

        byte[] negativeOffset = canonical.clone();
        putLong(negativeOffset, 136, -1);
        updateChecksum(negativeOffset);
        assertInvalid(negativeOffset);

        byte[] outOfOrder = canonical.clone();
        swap(outOfOrder, 84, 144, 60);
        updateChecksum(outOfOrder);
        assertInvalid(outOfOrder);

        byte[] duplicate = canonical.clone();
        System.arraycopy(duplicate, 88, duplicate, 148, 32);
        updateChecksum(duplicate);
        assertInvalid(duplicate);

        byte[] trailing = new byte[canonical.length + 1];
        System.arraycopy(canonical, 0, trailing, 0,
                         canonical.length - Integer.BYTES);
        trailing[canonical.length - Integer.BYTES] = 0;
        updateChecksum(trailing);
        assertInvalid(trailing);

        byte[] negativeLaneCount = canonical.clone();
        putInt(negativeLaneCount, 80, -1);
        updateChecksum(negativeLaneCount);
        assertInvalid(negativeLaneCount);

        byte[] tooMany = canonical.clone();
        putInt(tooMany, 80, 17);
        updateChecksum(tooMany);
        assertEquals(OctavoReadingPositionPortable.DecodeStatus.LIMIT,
                     OctavoReadingPositionPortable.decode(tooMany).status);

        byte[] oversized = new byte[128 * 1024 + 1];
        putInt(oversized, 0,
               OctavoReadingPositionPortable.magicForTesting());
        putInt(oversized, 4,
               OctavoReadingPositionPortable.versionForTesting());
        assertEquals(OctavoReadingPositionPortable.DecodeStatus.LIMIT,
                     OctavoReadingPositionPortable.decode(oversized).status);
    }

    @Test
    public void futurePortableBytesAreDistinctAndPreservedExactly()
        throws IOException {
        byte[] future = twoLaneBytes();
        putInt(future, Integer.BYTES,
               OctavoReadingPositionPortable.versionForTesting() + 1);
        future[future.length - 1] ^= 0x55;

        OctavoReadingPositionPortable.DecodeResult decoded =
            OctavoReadingPositionPortable.decode(future);
        assertEquals(
            OctavoReadingPositionPortable.DecodeStatus.FUTURE_VERSION,
            decoded.status);
        assertNull(decoded.snapshot());
        assertArrayEquals(future, decoded.preservedBytes());
        byte[] copy = decoded.preservedBytes();
        copy[0] ^= 1;
        assertArrayEquals(future, decoded.preservedBytes());
    }

    @Test
    public void portableMergeIsLaneLocalDeterministicAndAtomic()
        throws IOException {
        OctavoReadingPositionPortable.Snapshot local = snapshot(
            BOOK,
            lane(REMOTE_A, 5, 2, 20),
            lane(REMOTE_B, 3, 1, 10));

        OctavoReadingPositionPortable.MergeResult stale =
            OctavoReadingPositionPortable.merge(
                local, snapshot(BOOK, lane(REMOTE_A, 4, 9, 90)));
        assertEquals(OctavoReadingPositionPortable.MergeStatus.UNCHANGED,
                     stale.status);
        assertTrue(stale.snapshot == local);

        OctavoReadingPositionPortable.MergeResult idempotent =
            OctavoReadingPositionPortable.merge(
                local, snapshot(BOOK, lane(REMOTE_A, 5, 2, 20)));
        assertEquals(OctavoReadingPositionPortable.MergeStatus.UNCHANGED,
                     idempotent.status);

        OctavoReadingPositionPortable.MergeResult higher =
            OctavoReadingPositionPortable.merge(
                local, snapshot(BOOK, lane(REMOTE_A, 6, 3, 30)));
        assertEquals(OctavoReadingPositionPortable.MergeStatus.MERGED,
                     higher.status);
        assertEquals(6, higher.snapshot.lane(REMOTE_A).sequence);
        assertEquals(3, higher.snapshot.lane(REMOTE_A).spineIndex);

        OctavoReadingPositionPortable.MergeResult equivocation =
            OctavoReadingPositionPortable.merge(
                local, snapshot(BOOK, lane(REMOTE_A, 5, 2, 21)));
        assertEquals(
            OctavoReadingPositionPortable.MergeStatus.EQUIVOCATION,
            equivocation.status);
        assertTrue(equivocation.snapshot == local);

        assertEquals(
            OctavoReadingPositionPortable.MergeStatus.WRONG_BOOK,
            OctavoReadingPositionPortable.merge(
                local, snapshot(OTHER_BOOK,
                                lane(REMOTE_A, 6, 3, 30))).status);

        ArrayList<OctavoReadingPositionPortable.Lane> full =
            new ArrayList<>();
        for (int index = 0; index < 16; ++index) {
            full.add(lane(device(1000 + index), 1, 0, index));
        }
        OctavoReadingPositionPortable.Snapshot fullSnapshot =
            new OctavoReadingPositionPortable.Snapshot(BOOK, full);
        OctavoReadingPositionPortable.MergeResult limit =
            OctavoReadingPositionPortable.merge(
                fullSnapshot,
                snapshot(BOOK, lane(device(2000), 1, 0, 0)));
        assertEquals(OctavoReadingPositionPortable.MergeStatus.LIMIT,
                     limit.status);
        assertTrue(limit.snapshot == fullSnapshot);
    }

    @Test
    public void privateStoreRecordsOnlyExplicitSuccessfulPresentations()
        throws IOException {
        OctavoReadingPositionStore store = store("presentation", LOCAL);
        assertEquals(OctavoReadingPositionStore.LoadStatus.MISSING,
                     store.load());
        assertFalse(store.stateFileForTesting().exists());
        assertEquals(LOCAL, store.deviceId());

        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 11, 1, 10, 20, false));
        assertNull(store.localLane(BOOK));
        assertFalse(store.stateFileForTesting().exists());
        assertFalse(store.lastError().isEmpty());

        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 11, 1, 12, 20, true));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 11, 2, 10, 20, true));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 11, 1, 10, 10, true));
        assertNull(store.localLane(BOOK));
        assertFalse(store.stateFileForTesting().exists());

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 11, 1, 10, 20, true));
        assertLane(store.localLane(BOOK), LOCAL, 1, 1, 11);
        byte[] first = readFile(store.stateFileForTesting());
        assertFalse(store.temporaryFileForTesting().exists());

        assertEquals(OctavoReadingPositionStore.MutationResult.UNCHANGED,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 11, 1, 10, 20, true));
        assertArrayEquals(first, readFile(store.stateFileForTesting()));
        assertLane(store.localLane(BOOK), LOCAL, 1, 1, 11);

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 12, 1, 10, 20, true));
        assertLane(store.localLane(BOOK), LOCAL, 2, 1, 12);
        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     store.beginBookReview(BOOK, true));
        assertEquals(1, store.reviewEpoch(BOOK));

        OctavoReadingPositionStore reloaded =
            new OctavoReadingPositionStore(
                store.stateFileForTesting().getParentFile()
                    .getParentFile(),
                device(999));
        assertEquals(OctavoReadingPositionStore.LoadStatus.LOADED,
                     reloaded.load());
        assertEquals(LOCAL, reloaded.deviceId());
        assertEquals(1, reloaded.reviewEpoch(BOOK));
        assertLane(reloaded.localLane(BOOK), LOCAL, 2, 1, 12);
        assertArrayEquals(readFile(store.stateFileForTesting()),
                          reloaded.canonicalBytesForTesting());
    }

    @Test
    public void generatedInstallationDeviceIdIsCanonicalAndStable()
        throws IOException {
        File files = child("random-device");
        OctavoReadingPositionStore store =
            new OctavoReadingPositionStore(files);
        assertEquals(OctavoReadingPositionStore.LoadStatus.MISSING,
                     store.load());
        String generated = store.deviceId();
        assertTrue(OctavoReadingPositionPortable.validDeviceId(generated));
        assertTrue(store.recordSuccessfullyPresented(
            BOOK, 0, 0, 0, 0, 1, true).succeeded());

        OctavoReadingPositionStore reloaded =
            new OctavoReadingPositionStore(files);
        String provisional = reloaded.deviceId();
        assertTrue(OctavoReadingPositionPortable.validDeviceId(provisional));
        assertEquals(OctavoReadingPositionStore.LoadStatus.LOADED,
                     reloaded.load());
        assertEquals(generated, reloaded.deviceId());
        assertArrayEquals(readFile(store.stateFileForTesting()),
                          reloaded.canonicalBytesForTesting());
    }

    @Test
    public void remoteMergeResetsOnlySupersededDecisionAndRejectsReplay()
        throws IOException {
        OctavoReadingPositionStore store = readyStore("merge", 0, 5);
        byte[] beforeMissing = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.UNCHANGED,
            store.mergeSimulatedRemoteBytes(null));
        assertArrayEquals(beforeMissing, readFile(store.stateFileForTesting()));
        byte[] remote = remoteBytes(BOOK,
            lane(REMOTE_A, 2, 3, 20),
            lane(REMOTE_B, 1, 4, 10));
        store.failNextPublishForTesting();
        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.PUBLISH_FAILED,
            store.mergeSimulatedRemoteBytes(remote));
        assertArrayEquals(beforeMissing, readFile(store.stateFileForTesting()));
        assertTrue(store.reviewCandidates(BOOK, 0, 5).isEmpty());
        assertEquals(OctavoReadingPositionStore.PortableMergeResult.MERGED,
                     store.mergeSimulatedRemoteBytes(remote));
        List<OctavoReadingPositionStore.Candidate> candidates =
            store.reviewCandidates(BOOK, 0, 5);
        assertEquals(2, candidates.size());
        assertEquals(REMOTE_B, candidates.get(0).deviceId);
        assertEquals(REMOTE_A, candidates.get(1).deviceId);

        OctavoReadingPositionStore.Candidate a = candidates.get(1);
        assertTrue(store.stay(a).succeeded());
        assertEquals(1, store.reviewCandidates(BOOK, 0, 5).size());
        byte[] stayed = readFile(store.stateFileForTesting());

        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.UNCHANGED,
            store.mergeSimulatedRemoteBytes(
                remoteBytes(BOOK, lane(REMOTE_A, 2, 3, 20))));
        assertArrayEquals(stayed, readFile(store.stateFileForTesting()));
        assertEquals(1, store.reviewCandidates(BOOK, 0, 5).size());

        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.UNCHANGED,
            store.mergeSimulatedRemoteBytes(
                remoteBytes(BOOK, lane(REMOTE_A, 1, 9, 90))));
        assertArrayEquals(stayed, readFile(store.stateFileForTesting()));

        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.EQUIVOCATION,
            store.mergeSimulatedRemoteBytes(
                remoteBytes(BOOK, lane(REMOTE_A, 2, 3, 21))));
        assertArrayEquals(stayed, readFile(store.stateFileForTesting()));

        assertEquals(OctavoReadingPositionStore.PortableMergeResult.MERGED,
                     store.mergeSimulatedRemoteBytes(
                         remoteBytes(BOOK,
                             lane(REMOTE_A, 3, 5, 30))));
        candidates = store.reviewCandidates(BOOK, 0, 5);
        assertEquals(2, candidates.size());
        assertEquals(REMOTE_A, candidates.get(0).deviceId);
        assertEquals(OctavoReadingPositionStore.Decision.NONE,
                     candidates.get(0).decision);

        byte[] ownAdvance = remoteBytes(
            BOOK, lane(LOCAL, 2, 9, 99));
        byte[] beforeOwn = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.OWN_LANE_ADVANCE,
            store.mergeSimulatedRemoteBytes(ownAdvance));
        assertArrayEquals(beforeOwn, readFile(store.stateFileForTesting()));
    }

    @Test
    public void dismissSurvivesRecreationAndRepromptsOnlyAfterExplicitOpen()
        throws IOException {
        OctavoReadingPositionStore store = readyStore("dismiss", 0, 5);
        assertEquals(OctavoReadingPositionStore.PortableMergeResult.MERGED,
                     store.mergeSimulatedRemoteBytes(
                         remoteBytes(BOOK,
                             lane(REMOTE_A, 1, 2, 20))));
        OctavoReadingPositionStore.Candidate candidate =
            onlyCandidate(store, 0, 5);
        assertTrue(store.dismiss(candidate).succeeded());
        assertTrue(store.reviewCandidates(BOOK, 0, 5).isEmpty());
        byte[] dismissed = readFile(store.stateFileForTesting());

        OctavoReadingPositionStore recreated = reload(store, device(999));
        assertEquals(OctavoReadingPositionStore.MutationResult.UNCHANGED,
                     recreated.beginBookReview(BOOK, false));
        assertArrayEquals(dismissed,
                          readFile(recreated.stateFileForTesting()));
        assertTrue(recreated.reviewCandidates(BOOK, 0, 5).isEmpty());

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     recreated.beginBookReview(BOOK, true));
        assertEquals(2, recreated.reviewEpoch(BOOK));
        candidate = onlyCandidate(recreated, 0, 5);
        assertEquals(OctavoReadingPositionStore.Decision.DISMISSED_AT_EPOCH,
                     candidate.decision);

        assertEquals(OctavoReadingPositionStore.MutationResult.UNCHANGED,
                     recreated.recordSuccessfullyPresented(
                         BOOK, 0, 5, 0, 0, 10,
                         true, candidate));
        assertEquals(1,
                     recreated.reviewCandidates(BOOK, 0, 5).size());

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     recreated.recordSuccessfullyPresented(
                         BOOK, 0, 6, 0, 0, 10,
                         true, candidate));
        assertTrue(recreated.reviewCandidates(BOOK, 0, 6).isEmpty());
        OctavoReadingPositionStore afterMove =
            reload(recreated, device(998));
        assertTrue(afterMove.reviewCandidates(BOOK, 0, 6).isEmpty());
    }

    @Test
    public void goPendingRequiresDurabilityAndCompletesOnlyAfterPresentation()
        throws IOException {
        OctavoReadingPositionStore store = readyStore("go", 0, 5);
        assertTrue(store.mergeSimulatedRemoteBytes(
            remoteBytes(BOOK, lane(REMOTE_A, 1, 2, 20))).succeeded());
        OctavoReadingPositionStore.Candidate candidate =
            onlyCandidate(store, 0, 5);
        byte[] baseline = readFile(store.stateFileForTesting());

        store.failNextPublishForTesting();
        assertEquals(OctavoReadingPositionStore.MutationResult.PUBLISH_FAILED,
                     store.markGoPending(candidate, 1, 0, 5));
        assertArrayEquals(baseline, readFile(store.stateFileForTesting()));
        assertNull(store.pendingGo(BOOK));
        assertFalse(store.temporaryFileForTesting().exists());
        assertTrue(store.lastError().contains("Retry"));

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     store.markGoPending(candidate, 1, 0, 5));
        OctavoReadingPositionStore recreated = reload(store, device(999));
        OctavoReadingPositionStore.Candidate pending =
            recreated.pendingGo(BOOK);
        assertNotNull(pending);
        assertEquals(OctavoReadingPositionStore.Decision.GO_PENDING,
                     pending.decision);
        assertEquals(1, pending.originSequence);
        byte[] pendingBytes = readFile(recreated.stateFileForTesting());

        assertEquals(OctavoReadingPositionStore.MutationResult.CONFLICT,
                     recreated.recordSuccessfullyPresented(
                         BOOK, 2, 20, 2, 0, 30, true, pending));
        assertArrayEquals(pendingBytes,
                          readFile(recreated.stateFileForTesting()));
        assertNotNull(recreated.pendingGo(BOOK));
        assertLane(recreated.localLane(BOOK), LOCAL, 1, 0, 5);

        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     recreated.completeGo(
                         pending, 2, 20, 2, 0,
                         2, 0, 30, false));
        assertArrayEquals(pendingBytes,
                          readFile(recreated.stateFileForTesting()));
        assertNotNull(recreated.pendingGo(BOOK));

        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     recreated.completeGo(
                         pending, 2, 21, 2, 0,
                         2, 0, 30, true));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     recreated.completeGo(
                         pending, 2, 20, 2, 0,
                         2, 0, 30, true));
        assertArrayEquals(pendingBytes,
                          readFile(recreated.stateFileForTesting()));
        assertLane(recreated.localLane(BOOK), LOCAL, 1, 0, 5);

        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     recreated.completeGo(
                         pending, 2, 20, 2, 0,
                         2, 0, 20, true));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     recreated.completeGo(
                         pending, 2, 20, 2, 31,
                         2, 0, 30, true));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     recreated.completeGo(
                         pending, 2, 20, 2, 0,
                         3, 0, 30, true));
        assertArrayEquals(pendingBytes,
                          readFile(recreated.stateFileForTesting()));
        assertNotNull(recreated.pendingGo(BOOK));

        recreated.failNextPublishForTesting();
        assertEquals(OctavoReadingPositionStore.MutationResult.PUBLISH_FAILED,
                     recreated.completeGo(
                         pending, 2, 20, 2, 20,
                         2, 0, 30, true));
        assertArrayEquals(pendingBytes,
                          readFile(recreated.stateFileForTesting()));
        assertLane(recreated.localLane(BOOK), LOCAL, 1, 0, 5);

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     recreated.completeGo(
                         pending, 2, 20, 2, 20,
                         2, 0, 30, true));
        assertNull(recreated.pendingGo(BOOK));
        assertLane(recreated.localLane(BOOK), LOCAL, 2, 2, 20);
        assertTrue(recreated.reviewCandidates(BOOK, 2, 20).isEmpty());
        OctavoReadingPositionStore completed =
            reload(recreated, device(997));
        assertNull(completed.pendingGo(BOOK));
        assertLane(completed.localLane(BOOK), LOCAL, 2, 2, 20);
    }

    @Test
    public void pendingDismissRequiresExactPresentedRollbackAndIsAtomic()
        throws IOException {
        OctavoReadingPositionStore store =
            readyStore("pending-dismiss", 0, 5);
        assertTrue(store.mergeSimulatedRemoteBytes(
            remoteBytes(BOOK, lane(REMOTE_A, 1, 2, 20))).succeeded());
        OctavoReadingPositionStore.Candidate candidate =
            onlyCandidate(store, 0, 5);
        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     store.markGoPending(candidate, 1, 0, 5));
        OctavoReadingPositionStore.Candidate pending = store.pendingGo(BOOK);
        assertNotNull(pending);
        byte[] pendingBytes = readFile(store.stateFileForTesting());

        assertEquals(OctavoReadingPositionStore.MutationResult.CONFLICT,
                     store.dismiss(pending));
        assertEquals(OctavoReadingPositionStore.MutationResult.CONFLICT,
                     store.recordSuccessfullyPresented(
                         BOOK, 0, 5, 0, 0, 10, true, pending));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     store.dismissPendingAfterRollback(
                         pending, 0, 6, 0, 0, 10, true));
        assertEquals(OctavoReadingPositionStore.MutationResult.INVALID,
                     store.dismissPendingAfterRollback(
                         pending, 0, 5, 0, 0, 10, false));
        assertArrayEquals(pendingBytes,
                          readFile(store.stateFileForTesting()));
        assertNotNull(store.pendingGo(BOOK));
        assertLane(store.localLane(BOOK), LOCAL, 1, 0, 5);

        store.failNextPublishForTesting();
        assertEquals(OctavoReadingPositionStore.MutationResult.PUBLISH_FAILED,
                     store.dismissPendingAfterRollback(
                         pending, 0, 5, 0, 0, 10, true));
        assertArrayEquals(pendingBytes,
                          readFile(store.stateFileForTesting()));
        assertNotNull(store.pendingGo(BOOK));
        assertLane(store.localLane(BOOK), LOCAL, 1, 0, 5);

        assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                     store.dismissPendingAfterRollback(
                         pending, 0, 5, 0, 0, 10, true));
        assertNull(store.pendingGo(BOOK));
        assertTrue(store.reviewCandidates(BOOK, 0, 5).isEmpty());
        assertLane(store.localLane(BOOK), LOCAL, 1, 0, 5);

        OctavoReadingPositionStore recreated = reload(store, device(996));
        assertNull(recreated.pendingGo(BOOK));
        assertTrue(recreated.reviewCandidates(BOOK, 0, 5).isEmpty());
        assertLane(recreated.localLane(BOOK), LOCAL, 1, 0, 5);
    }

    @Test
    public void privatePublishRollbackFutureBlockAndCorruptQuarantineAreExact()
        throws IOException {
        OctavoReadingPositionStore store = readyStore("recovery", 0, 5);
        byte[] committed = readFile(store.stateFileForTesting());
        byte[] canonical = store.canonicalBytesForTesting();
        store.failNextPublishForTesting();
        assertEquals(OctavoReadingPositionStore.MutationResult.PUBLISH_FAILED,
                     store.recordSuccessfullyPresented(
                         BOOK, 1, 10, 1, 0, 20, true));
        assertArrayEquals(committed, readFile(store.stateFileForTesting()));
        assertArrayEquals(canonical, store.canonicalBytesForTesting());
        assertLane(store.localLane(BOOK), LOCAL, 1, 0, 5);
        assertFalse(store.temporaryFileForTesting().exists());

        File futureRoot = child("future");
        OctavoReadingPositionStore future =
            new OctavoReadingPositionStore(futureRoot, LOCAL);
        byte[] futureBytes = committed.clone();
        putInt(futureBytes, Integer.BYTES,
               OctavoReadingPositionStore.storeVersionForTesting() + 1);
        future.stateFileForTesting().getParentFile().mkdirs();
        writeFile(future.stateFileForTesting(), futureBytes);
        assertEquals(
            OctavoReadingPositionStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            future.load());
        assertEquals(OctavoReadingPositionStore.MutationResult.BLOCKED,
                     future.recordSuccessfullyPresented(
                         BOOK, 2, 20, 2, 0, 30, true));
        assertArrayEquals(futureBytes,
                          readFile(future.stateFileForTesting()));
        assertFalse(future.lastError().isEmpty());

        File corruptRoot = child("corrupt");
        OctavoReadingPositionStore corrupt =
            new OctavoReadingPositionStore(corruptRoot, LOCAL);
        byte[] corruptBytes = committed.clone();
        corruptBytes[corruptBytes.length - 1] ^= 1;
        corrupt.stateFileForTesting().getParentFile().mkdirs();
        writeFile(corrupt.stateFileForTesting(), corruptBytes);
        assertEquals(
            OctavoReadingPositionStore.LoadStatus.CORRUPT_QUARANTINED,
            corrupt.load());
        assertFalse(corrupt.stateFileForTesting().exists());
        assertArrayEquals(corruptBytes,
                          readFile(corrupt.quarantineFileForTesting(1)));
        assertTrue(corrupt.recordSuccessfullyPresented(
            OTHER_BOOK, 0, 0, 0, 0, 1, true).succeeded());
        assertTrue(corrupt.stateFileForTesting().isFile());
        assertArrayEquals(corruptBytes,
                          readFile(corrupt.quarantineFileForTesting(1)));

        File blockedRoot = child("corrupt-blocked");
        OctavoReadingPositionStore blocked =
            new OctavoReadingPositionStore(blockedRoot, LOCAL);
        blocked.stateFileForTesting().getParentFile().mkdirs();
        writeFile(blocked.stateFileForTesting(), corruptBytes);
        for (int index = 1; index <= 3; ++index) {
            writeFile(blocked.quarantineFileForTesting(index),
                      new byte[] {(byte)index});
        }
        assertEquals(OctavoReadingPositionStore.LoadStatus.CORRUPT_BLOCKED,
                     blocked.load());
        assertArrayEquals(corruptBytes,
                          readFile(blocked.stateFileForTesting()));
        assertEquals(OctavoReadingPositionStore.MutationResult.BLOCKED,
                     blocked.recordSuccessfullyPresented(
                         OTHER_BOOK, 0, 0,
                         0, 0, 1, true));
    }

    @Test
    public void exactBookAndLaneLimitsFailWithoutEviction()
        throws IOException {
        OctavoReadingPositionStore store = store("limits", LOCAL);
        assertEquals(OctavoReadingPositionStore.LoadStatus.MISSING,
                     store.load());
        for (int index = 0;
             index < OctavoReadingPositionStore.maximumBookCountForTesting();
             ++index) {
            assertEquals(OctavoReadingPositionStore.MutationResult.UPDATED,
                         store.beginBookReview(digest(1000 + index), true));
        }
        assertEquals(64, store.bookCountForTesting());
        byte[] fullBooks = readFile(store.stateFileForTesting());
        String unknownDigest = digest(9999);
        assertEquals(OctavoReadingPositionStore.PortableMergeResult.UNCHANGED,
                     store.mergeSimulatedRemoteBytes(
                         remoteBytes(unknownDigest)));
        assertEquals(64, store.bookCountForTesting());
        assertNull(store.localLane(unknownDigest));
        assertTrue(store.lastError().isEmpty());
        assertArrayEquals(fullBooks, readFile(store.stateFileForTesting()));
        assertEquals(OctavoReadingPositionStore.MutationResult.LIMIT,
                     store.beginBookReview(unknownDigest, true));
        assertArrayEquals(fullBooks, readFile(store.stateFileForTesting()));
        assertTrue(fullBooks.length
                   <= OctavoReadingPositionStore.maximumFileBytesForTesting());

        OctavoReadingPositionStore lanes = readyStore("lane-limit", 0, 0);
        ArrayList<OctavoReadingPositionPortable.Lane> remote =
            new ArrayList<>();
        for (int index = 0; index < 15; ++index) {
            remote.add(lane(device(1000 + index), 1, 1, index));
        }
        assertEquals(OctavoReadingPositionStore.PortableMergeResult.MERGED,
                     lanes.mergeSimulatedRemoteBytes(
                         OctavoReadingPositionPortable.encode(
                             new OctavoReadingPositionPortable.Snapshot(
                                 BOOK, remote))));
        byte[] fullLanes = readFile(lanes.stateFileForTesting());
        assertEquals(16,
                     OctavoReadingPositionPortable.decode(
                         lanes.exportPortable(BOOK).bytes())
                         .snapshot().laneCount());
        assertEquals(OctavoReadingPositionStore.PortableMergeResult.LIMIT,
                     lanes.mergeSimulatedRemoteBytes(
                         remoteBytes(BOOK,
                             lane(device(9999), 1, 2, 2))));
        assertArrayEquals(fullLanes,
                          readFile(lanes.stateFileForTesting()));
    }

    @Test
    public void sequenceAndReviewExhaustionAreVisibleAndNonmutating()
        throws IOException {
        OctavoReadingPositionStore store = readyStore("exhaustion", 0, 0);
        byte[] maximumSequence = readFile(store.stateFileForTesting());
        putLong(maximumSequence, 168, Long.MAX_VALUE);
        updateChecksum(maximumSequence);
        writeFile(store.stateFileForTesting(), maximumSequence);

        OctavoReadingPositionStore reloaded = reload(store, device(999));
        assertLane(reloaded.localLane(BOOK), LOCAL,
                   Long.MAX_VALUE, 0, 0);
        assertEquals(OctavoReadingPositionStore.MutationResult.EXHAUSTED,
                     reloaded.recordSuccessfullyPresented(
                         BOOK, 0, 1, 0, 0, 2, true));
        assertArrayEquals(maximumSequence,
                          readFile(reloaded.stateFileForTesting()));
        assertFalse(reloaded.lastError().isEmpty());

        byte[] maximumEpoch = maximumSequence.clone();
        putLong(maximumEpoch, 120, Long.MAX_VALUE);
        updateChecksum(maximumEpoch);
        writeFile(reloaded.stateFileForTesting(), maximumEpoch);
        OctavoReadingPositionStore epochReloaded =
            reload(reloaded, device(998));
        assertEquals(Long.MAX_VALUE, epochReloaded.reviewEpoch(BOOK));
        assertEquals(OctavoReadingPositionStore.MutationResult.EXHAUSTED,
                     epochReloaded.beginBookReview(BOOK, true));
        assertArrayEquals(maximumEpoch,
                          readFile(epochReloaded.stateFileForTesting()));
    }

    @Test
    public void simulatedFutureInputIsVisiblePreservedAndNonmutating()
        throws IOException {
        OctavoReadingPositionStore store = readyStore("future-portable", 0, 0);
        byte[] future = remoteBytes(
            BOOK, lane(REMOTE_A, 1, 1, 1));
        putInt(future, Integer.BYTES,
               OctavoReadingPositionPortable.versionForTesting() + 1);
        future[future.length - 1] ^= 0x42;
        byte[] before = readFile(store.stateFileForTesting());

        assertEquals(
            OctavoReadingPositionStore.PortableMergeResult.FUTURE_VERSION,
            store.mergeSimulatedRemoteBytes(future));
        assertArrayEquals(future, store.lastFuturePortableBytes());
        assertArrayEquals(before, readFile(store.stateFileForTesting()));
        assertFalse(store.lastError().isEmpty());
    }

    private OctavoReadingPositionStore readyStore(String name,
                                                  long spineIndex,
                                                  long byteOffset) {
        OctavoReadingPositionStore store = store(name, LOCAL);
        assertEquals(OctavoReadingPositionStore.LoadStatus.MISSING,
                     store.load());
        assertTrue(store.recordSuccessfullyPresented(
            BOOK, spineIndex, byteOffset,
            spineIndex, byteOffset, byteOffset + 1,
            true).succeeded());
        assertTrue(store.beginBookReview(BOOK, true).succeeded());
        return store;
    }

    private OctavoReadingPositionStore store(String name,
                                             String localDevice) {
        File root = child(name);
        return new OctavoReadingPositionStore(root, localDevice);
    }

    private File child(String name) {
        File child = new File(testRoot, name);
        assertFalse(child.exists());
        assertTrue(child.mkdirs());
        return child;
    }

    private static OctavoReadingPositionStore reload(
        OctavoReadingPositionStore source,
        String ignoredConstructorDevice) {
        File files = source.stateFileForTesting().getParentFile()
            .getParentFile();
        OctavoReadingPositionStore reloaded =
            new OctavoReadingPositionStore(files,
                                           ignoredConstructorDevice);
        assertEquals(OctavoReadingPositionStore.LoadStatus.LOADED,
                     reloaded.load());
        return reloaded;
    }

    private static OctavoReadingPositionStore.Candidate onlyCandidate(
        OctavoReadingPositionStore store,
        long spineIndex,
        long byteOffset) {
        List<OctavoReadingPositionStore.Candidate> candidates =
            store.reviewCandidates(BOOK, spineIndex, byteOffset);
        assertEquals(1, candidates.size());
        return candidates.get(0);
    }

    private static OctavoReadingPositionPortable.Snapshot snapshot(
        String digest,
        OctavoReadingPositionPortable.Lane... lanes) {
        return new OctavoReadingPositionPortable.Snapshot(
            digest, Arrays.asList(lanes));
    }

    private static OctavoReadingPositionPortable.Lane lane(
        String deviceId,
        long sequence,
        long spineIndex,
        long byteOffset) {
        return new OctavoReadingPositionPortable.Lane(
            deviceId, sequence, spineIndex, byteOffset);
    }

    private static byte[] remoteBytes(
        String digest,
        OctavoReadingPositionPortable.Lane... lanes)
        throws IOException {
        return OctavoReadingPositionPortable.encode(
            snapshot(digest, lanes));
    }

    private static byte[] twoLaneBytes() throws IOException {
        return remoteBytes(
            BOOK,
            lane(REMOTE_A, 1, 0, 0),
            lane(REMOTE_B, 2, 1, 1));
    }

    private static void assertReady(byte[] bytes) {
        assertEquals(OctavoReadingPositionPortable.DecodeStatus.READY,
                     OctavoReadingPositionPortable.decode(bytes).status);
    }

    private static void assertInvalid(byte[] bytes) {
        assertEquals(OctavoReadingPositionPortable.DecodeStatus.INVALID,
                     OctavoReadingPositionPortable.decode(bytes).status);
    }

    private static void assertLane(
        OctavoReadingPositionPortable.Lane lane,
        String deviceId,
        long sequence,
        long spineIndex,
        long byteOffset) {
        assertNotNull(lane);
        assertEquals(deviceId, lane.deviceId);
        assertEquals(sequence, lane.sequence);
        assertEquals(spineIndex, lane.spineIndex);
        assertEquals(byteOffset, lane.byteOffset);
    }

    private static String digest(int value) {
        return String.format(Locale.ROOT, "%064x", value);
    }

    private static String device(int value) {
        return String.format(Locale.ROOT, "%032x", value);
    }

    private static byte[] readFile(File file) throws IOException {
        assertTrue(file.isFile());
        assertTrue(file.length() <= Integer.MAX_VALUE);
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(
                    bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    offset += count;
                }
            }
            assertEquals(-1, input.read());
        }
        assertEquals(bytes.length, offset);
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

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void putLong(byte[] bytes, int offset, long value) {
        for (int index = 7; index >= 0; --index) {
            bytes[offset + index] = (byte)value;
            value >>>= 8;
        }
    }

    private static void updateChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        putInt(bytes, bytes.length - Integer.BYTES,
               (int)checksum.getValue());
    }

    private static void swap(byte[] bytes,
                             int left,
                             int right,
                             int length) {
        byte[] temporary = Arrays.copyOfRange(bytes, left, left + length);
        System.arraycopy(bytes, right, bytes, left, length);
        System.arraycopy(temporary, 0, bytes, right, length);
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
