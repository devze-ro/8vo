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
public final class OctavoProgressSyncStoreTest {
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
            "octavo-progress-sync-" + System.nanoTime());
        assertFalse(testRoot.exists());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void identityOnlyStateAndProofGatesAreDurable()
        throws IOException {
        OctavoProgressSyncStore store = store();
        assertEquals(OctavoProgressSyncStore.MutationResult.BLOCKED,
                     store.beginReviewEpoch(true));
        assertEquals(OctavoProgressSyncStore.LoadStatus.MISSING_CREATED,
                     store.load());
        assertEquals(LOCAL, store.deviceId());
        assertNull(store.effectiveDisplay());
        assertNull(store.localLane());
        assertNull(store.pending());
        assertEquals(72, readFile(store.stateFileForTesting()).length);

        OctavoProgressSyncStore.PortableExport empty =
            store.exportPortable();
        assertEquals(
            OctavoProgressSyncStore.PortableExportStatus.EXPORTED,
            empty.status);
        assertEquals(20, empty.bytes().length);
        assertEquals(0, OctavoProgressPortable.decode(empty.bytes())
            .snapshot().laneCount());

        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageInitialPresented(
                         OctavoProgressDisplay.PERCENTAGE));
        OctavoProgressSyncStore.Pending initial = store.pending();
        assertNotNull(initial);
        assertEquals(OctavoProgressSyncStore.PendingDirection.FORWARD,
                     initial.direction);
        assertFalse(initial.hasOriginLane);
        assertEquals(initial.originDisplay(), initial.targetDisplay());
        assertNull(store.effectiveDisplay());
        assertEquals(0, portableSnapshot(store).laneCount());
        assertEquals(156, readFile(store.stateFileForTesting()).length);

        byte[] staged = readFile(store.stateFileForTesting());
        assertEquals(OctavoProgressSyncStore.MutationResult.INVALID,
                     store.requestRollback(initial));
        assertArrayEquals(staged, readFile(store.stateFileForTesting()));
        assertEquals(OctavoProgressSyncStore.MutationResult.INVALID,
                     store.completePending(
                         initial, OctavoProgressDisplay.PAGE,
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD));
        assertArrayEquals(staged, readFile(store.stateFileForTesting()));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.completePending(
                         initial, OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD));
        assertEquals(OctavoProgressDisplay.PERCENTAGE,
                     store.effectiveDisplay());
        assertEquals(1, store.localLane().sequence);

        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageLocalApply(
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PAGE));
        OctavoProgressSyncStore.Pending ordinary = store.pending();
        assertTrue(ordinary.hasOriginLane);
        assertEquals(OctavoProgressSyncStore.MutationResult.INVALID,
                     store.completePending(
                         ordinary, OctavoProgressDisplay.PAGE,
                         OctavoProgressDisplay.PAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.completePending(
                         ordinary, OctavoProgressDisplay.PAGE,
                         OctavoProgressDisplay.PAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CURRENT_PROCESS_ATOMIC_SAVE));
        assertEquals(2, store.localLane().sequence);

        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageLocalApply(
                         OctavoProgressDisplay.PAGE,
                         OctavoProgressDisplay.LOCATION));
        OctavoProgressSyncStore reloaded = store();
        assertEquals(OctavoProgressSyncStore.LoadStatus.LOADED,
                     reloaded.load());
        assertEquals(LOCAL, reloaded.deviceId());
        OctavoProgressSyncStore.Pending recovered = reloaded.pending();
        assertNotNull(recovered);
        assertEquals(OctavoProgressSyncStore.PendingRecovery.TARGET_DURABLE,
                     reloaded.pendingRecovery(
                         OctavoProgressDisplay.LOCATION));
        assertEquals(OctavoProgressSyncStore.PendingRecovery.ORIGIN_DURABLE,
                     reloaded.pendingRecovery(
                         OctavoProgressDisplay.PAGE));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     reloaded.completePending(
                         recovered, OctavoProgressDisplay.LOCATION,
                         OctavoProgressDisplay.LOCATION,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD));
        assertEquals(3, reloaded.localLane().sequence);
        assertNull(reloaded.pending());
    }

    @Test
    public void reviewDecisionsAreExactEpochScopedAndReplaySafe()
        throws IOException {
        OctavoProgressSyncStore store = qualifiedStore();
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.beginReviewEpoch(true));
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     store.mergePortableBytes(portableBytes(
                         lane(REMOTE_B, 1,
                              OctavoProgressDisplay.PERCENTAGE),
                         lane(REMOTE_A, 1,
                              OctavoProgressDisplay.LOCATION))));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.recordConverged(
                         OctavoProgressDisplay.PERCENTAGE));

        List<OctavoProgressSyncStore.Candidate> candidates =
            store.reviewCandidates(OctavoProgressDisplay.PERCENTAGE);
        assertEquals(1, candidates.size());
        assertEquals(REMOTE_A, candidates.get(0).deviceId);
        OctavoProgressSyncStore.Candidate first = candidates.get(0);
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.dismiss(
                         first, OctavoProgressDisplay.PERCENTAGE));
        assertTrue(store.reviewCandidates(
            OctavoProgressDisplay.PERCENTAGE).isEmpty());
        assertEquals(OctavoProgressSyncStore.MutationResult.UNCHANGED,
                     store.beginReviewEpoch(false));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.beginReviewEpoch(true));

        candidates = store.reviewCandidates(
            OctavoProgressDisplay.PERCENTAGE);
        assertEquals(1, candidates.size());
        assertEquals(OctavoProgressSyncStore.MutationResult.CONFLICT,
                     store.keep(
                         first, OctavoProgressDisplay.PERCENTAGE));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.keep(
                         candidates.get(0),
                         OctavoProgressDisplay.PERCENTAGE));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.beginReviewEpoch(true));
        assertTrue(store.reviewCandidates(
            OctavoProgressDisplay.PERCENTAGE).isEmpty());

        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     store.mergePortableBytes(portableBytes(
                         lane(REMOTE_A, 2,
                              OctavoProgressDisplay.CHAPTER))));
        candidates = store.reviewCandidates(
            OctavoProgressDisplay.PERCENTAGE);
        assertEquals(1, candidates.size());
        assertEquals(OctavoProgressSyncStore.Decision.NONE,
                     candidates.get(0).decision);
        assertEquals(2, candidates.get(0).sequence);

        byte[] beforeStale = store.canonicalBytesForTesting();
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.UNCHANGED,
                     store.mergePortableBytes(portableBytes(
                         lane(REMOTE_A, 1,
                              OctavoProgressDisplay.LOCATION))));
        assertArrayEquals(beforeStale,
                          store.canonicalBytesForTesting());
        assertEquals(1, store.reviewCandidates(
            OctavoProgressDisplay.PERCENTAGE).size());
    }

    @Test
    public void pendingRemoteSupersessionRequiresOrderedRollback()
        throws IOException {
        OctavoProgressSyncStore store = qualifiedStore();
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.beginReviewEpoch(true));
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     store.mergePortableBytes(portableBytes(
                         lane(REMOTE_A, 1,
                              OctavoProgressDisplay.LOCATION))));
        OctavoProgressSyncStore.Candidate first =
            store.reviewCandidates(
                OctavoProgressDisplay.PERCENTAGE).get(0);
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageRemoteApply(
                         first, OctavoProgressDisplay.PERCENTAGE));
        OctavoProgressSyncStore.Pending pending = store.pending();
        assertNotNull(pending);
        assertEquals(OctavoProgressSyncStore.PendingKind.REMOTE,
                     pending.kind);
        assertEquals(OctavoProgressSyncStore.PendingDirection.FORWARD,
                     pending.direction);
        assertEquals(OctavoProgressSyncStore.MutationResult.BLOCKED,
                     store.beginReviewEpoch(true));

        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     store.mergePortableBytes(portableBytes(
                         lane(REMOTE_A, 2,
                              OctavoProgressDisplay.CHAPTER),
                         lane(REMOTE_B, 1,
                              OctavoProgressDisplay.PAGE))));
        assertTrue(pending.sameIdentity(store.pending()));
        assertEquals(OctavoProgressSyncStore.MutationResult.CONFLICT,
                     store.completePending(
                         pending, OctavoProgressDisplay.LOCATION,
                         OctavoProgressDisplay.LOCATION,
                         OctavoProgressSyncStore.O8pgProof
                             .CURRENT_PROCESS_ATOMIC_SAVE));
        assertNotNull(store.pending());
        byte[] beforeRollbackRequest =
            store.canonicalBytesForTesting();
        store.failNextPublishForTesting();
        assertEquals(OctavoProgressSyncStore.MutationResult.PUBLISH_FAILED,
                     store.requestRollback(pending));
        assertEquals(OctavoProgressSyncStore.PendingDirection.FORWARD,
                     store.pending().direction);
        assertArrayEquals(beforeRollbackRequest,
                          store.canonicalBytesForTesting());
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.requestRollback(pending));
        OctavoProgressSyncStore.Pending rollback = store.pending();
        assertEquals(OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                     rollback.direction);

        OctavoProgressSyncStore reloaded = store();
        assertEquals(OctavoProgressSyncStore.LoadStatus.LOADED,
                     reloaded.load());
        rollback = reloaded.pending();
        assertNotNull(rollback);
        assertEquals(OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                     rollback.direction);
        assertEquals(OctavoProgressSyncStore.MutationResult.UNCHANGED,
                     reloaded.requestRollback(rollback));
        assertEquals(OctavoProgressSyncStore.PendingRecovery.TARGET_DURABLE,
                     reloaded.pendingRecovery(
                         OctavoProgressDisplay.LOCATION));
        assertEquals(OctavoProgressSyncStore.MutationResult.CONFLICT,
                     reloaded.completePending(
                         rollback, OctavoProgressDisplay.LOCATION,
                         OctavoProgressDisplay.LOCATION,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD));
        assertEquals(OctavoProgressSyncStore.MutationResult.INVALID,
                     reloaded.dismissPendingAfterRollback(
                         rollback, OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD,
                         false));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     reloaded.dismissPendingAfterRollback(
                         rollback, OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CURRENT_PROCESS_ATOMIC_SAVE,
                         false));
        store = reloaded;
        assertNull(store.pending());
        List<OctavoProgressSyncStore.Candidate> candidates =
            store.reviewCandidates(OctavoProgressDisplay.PERCENTAGE);
        assertEquals(2, candidates.size());
        assertEquals(REMOTE_A, candidates.get(0).deviceId);
        assertEquals(2, candidates.get(0).sequence);

        OctavoProgressSyncStore.Candidate newer = candidates.get(0);
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageRemoteApply(
                         newer, OctavoProgressDisplay.PERCENTAGE));
        OctavoProgressSyncStore.Pending second = store.pending();
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.completePending(
                         second, OctavoProgressDisplay.CHAPTER,
                         OctavoProgressDisplay.CHAPTER,
                         OctavoProgressSyncStore.O8pgProof
                             .CURRENT_PROCESS_RECONCILED_AFTER_UNCERTAIN_SAVE));
        assertEquals(OctavoProgressDisplay.CHAPTER,
                     store.effectiveDisplay());
        assertEquals(2, store.localLane().sequence);
        assertNull(store.pending());
    }

    @Test
    public void ownLaneGuardCapacityAndSequenceExhaustionAreExact()
        throws IOException {
        OctavoProgressSyncStore store = qualifiedStore();
        byte[] before = store.canonicalBytesForTesting();
        assertEquals(
            OctavoProgressSyncStore.PortableMergeResult.OWN_LANE_ADVANCE,
            store.mergePortableBytes(portableBytes(
                lane(LOCAL, 2, OctavoProgressDisplay.PAGE))));
        assertArrayEquals(before, store.canonicalBytesForTesting());
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.EQUIVOCATION,
                     store.mergePortableBytes(portableBytes(
                         lane(LOCAL, 1,
                              OctavoProgressDisplay.LOCATION))));
        assertArrayEquals(before, store.canonicalBytesForTesting());

        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageLocalApply(
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PAGE));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.completePending(
                         store.pending(), OctavoProgressDisplay.PAGE,
                         OctavoProgressDisplay.PAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CURRENT_PROCESS_ATOMIC_SAVE));
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     store.mergePortableBytes(portableBytes(
                         lane(LOCAL, 1,
                              OctavoProgressDisplay.CHAPTER),
                         lane(REMOTE_A, 1,
                              OctavoProgressDisplay.LOCATION))));
        assertEquals(2, store.localLane().sequence);

        File capacityRoot = childRoot("capacity");
        OctavoProgressSyncStore capacity =
            new OctavoProgressSyncStore(capacityRoot, LOCAL);
        assertEquals(OctavoProgressSyncStore.LoadStatus.MISSING_CREATED,
                     capacity.load());
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     capacity.stageInitialPresented(
                         OctavoProgressDisplay.PAGE));
        ArrayList<OctavoProgressPortable.Lane> firstFifteen =
            new ArrayList<>();
        for (int index = 0; index < 15; ++index) {
            firstFifteen.add(lane(
                device(200 + index), 1,
                OctavoProgressDisplay.LOCATION));
        }
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     capacity.mergePortableBytes(portableBytes(
                         firstFifteen)));
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.LIMIT,
                     capacity.mergePortableBytes(portableBytes(
                         lane(device(999), 1,
                              OctavoProgressDisplay.CHAPTER))));
        assertEquals(15, portableSnapshot(capacity).laneCount());

        byte[] exhausted = store.canonicalBytesForTesting();
        writeLong(exhausted, 92, Long.MAX_VALUE);
        repairChecksum(exhausted);
        writeFile(store.stateFileForTesting(), exhausted);
        OctavoProgressSyncStore exhaustedStore = store();
        assertEquals(OctavoProgressSyncStore.LoadStatus.LOADED,
                     exhaustedStore.load());
        assertEquals(OctavoProgressSyncStore.MutationResult.EXHAUSTED,
                     exhaustedStore.stageLocalApply(
                         OctavoProgressDisplay.PAGE,
                         OctavoProgressDisplay.CHAPTER));
    }

    @Test
    public void futureQuarantineAndAtomicReplaceRecoveryAreBounded()
        throws IOException {
        OctavoProgressSyncStore store = qualifiedStore();
        byte[] future = futurePortable(65_536, 0xffffffff);
        assertEquals(
            OctavoProgressSyncStore.PortableMergeResult.FUTURE_RETAINED,
            store.mergePortableBytes(future));
        assertArrayEquals(future, store.retainedFutureBytes());
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.UNCHANGED,
                     store.mergePortableBytes(future.clone()));
        byte[] different = future.clone();
        different[different.length - 1] = 1;
        assertEquals(
            OctavoProgressSyncStore.PortableMergeResult.FUTURE_CONFLICT,
            store.mergePortableBytes(different));
        assertArrayEquals(future, store.retainedFutureBytes());

        byte[] stable = store.canonicalBytesForTesting();
        store.failNextPublishForTesting();
        assertEquals(OctavoProgressSyncStore.MutationResult.PUBLISH_FAILED,
                     store.beginReviewEpoch(true));
        assertArrayEquals(stable, readFile(store.stateFileForTesting()));

        long oldEpoch = store.reviewEpoch();
        store.failNextMoveAfterReplaceForTesting();
        assertEquals(
            OctavoProgressSyncStore.MutationResult.PUBLISH_UNCERTAIN,
            store.beginReviewEpoch(true));
        assertEquals(OctavoProgressSyncStore.MutationResult.BLOCKED,
                     store.beginReviewEpoch(true));
        assertEquals(OctavoProgressSyncStore.LoadStatus.LOADED,
                     store.load());
        assertEquals(oldEpoch + 1, store.reviewEpoch());

        File futureRoot = childRoot("future-private");
        OctavoProgressSyncStore futureStore =
            new OctavoProgressSyncStore(futureRoot, LOCAL);
        byte[] futurePrivate = new byte[
            OctavoProgressSyncStore.maximumFileBytesForTesting()];
        writeInt(futurePrivate, 0,
                 OctavoProgressSyncStore.storeMagicForTesting());
        writeInt(futurePrivate, 4, 0x80000000);
        writeFile(futureStore.stateFileForTesting(), futurePrivate);
        assertEquals(
            OctavoProgressSyncStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            futureStore.load());
        assertArrayEquals(futurePrivate,
                          readFile(futureStore.stateFileForTesting()));
        assertEquals(
            OctavoProgressSyncStore.PortableExportStatus.BLOCKED,
            futureStore.exportPortable().status);

        File corruptRoot = childRoot("corrupt-private");
        OctavoProgressSyncStore corrupt =
            new OctavoProgressSyncStore(corruptRoot, LOCAL);
        byte[] malformed = new byte[72];
        writeInt(malformed, 0,
                 OctavoProgressSyncStore.storeMagicForTesting());
        writeInt(malformed, 4,
                 OctavoProgressSyncStore.storeVersionForTesting());
        writeFile(corrupt.stateFileForTesting(), malformed);
        assertEquals(
            OctavoProgressSyncStore.LoadStatus.CORRUPT_QUARANTINED,
            corrupt.load());
        assertArrayEquals(malformed,
                          readFile(corrupt.quarantineFileForTesting(1)));
        assertEquals(72, readFile(corrupt.stateFileForTesting()).length);

        File directionRoot = childRoot("invalid-direction");
        OctavoProgressSyncStore direction =
            new OctavoProgressSyncStore(directionRoot, LOCAL);
        assertEquals(OctavoProgressSyncStore.LoadStatus.MISSING_CREATED,
                     direction.load());
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     direction.stageInitialPresented(
                         OctavoProgressDisplay.PERCENTAGE));
        byte[] invalidDirection =
            direction.canonicalBytesForTesting();
        writeInt(invalidDirection, 68, 2);
        repairChecksum(invalidDirection);
        writeFile(direction.stateFileForTesting(), invalidDirection);
        OctavoProgressSyncStore invalidDirectionStore =
            new OctavoProgressSyncStore(directionRoot, LOCAL);
        assertEquals(
            OctavoProgressSyncStore.LoadStatus.CORRUPT_QUARANTINED,
            invalidDirectionStore.load());
        assertArrayEquals(
            invalidDirection,
            readFile(invalidDirectionStore.quarantineFileForTesting(1)));
    }

    @Test
    public void terminalEpochRollbackStillClearsPendingSafely()
        throws IOException {
        OctavoProgressSyncStore original = qualifiedStore();
        byte[] terminal = original.canonicalBytesForTesting();
        writeLong(terminal, 44, Long.MAX_VALUE);
        repairChecksum(terminal);
        writeFile(original.stateFileForTesting(), terminal);

        OctavoProgressSyncStore store = store();
        assertEquals(OctavoProgressSyncStore.LoadStatus.LOADED,
                     store.load());
        assertEquals(Long.MAX_VALUE, store.reviewEpoch());
        assertEquals(OctavoProgressSyncStore.PortableMergeResult.MERGED,
                     store.mergePortableBytes(portableBytes(
                         lane(REMOTE_A, 1,
                              OctavoProgressDisplay.LOCATION))));
        OctavoProgressSyncStore.Candidate candidate =
            store.reviewCandidates(
                OctavoProgressDisplay.PERCENTAGE).get(0);
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.stageRemoteApply(
                         candidate, OctavoProgressDisplay.PERCENTAGE));
        OctavoProgressSyncStore.Pending pending = store.pending();
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.requestRollback(pending));
        pending = store.pending();
        assertEquals(OctavoProgressSyncStore.PendingDirection.ROLLBACK,
                     pending.direction);
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     store.dismissPendingAfterRollback(
                         pending, OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CURRENT_PROCESS_ATOMIC_SAVE,
                         true));
        assertEquals(Long.MAX_VALUE, store.reviewEpoch());
        assertNull(store.pending());
        assertTrue(store.reviewCandidates(
            OctavoProgressDisplay.PERCENTAGE).isEmpty());
    }

    private OctavoProgressSyncStore store() {
        return new OctavoProgressSyncStore(testRoot, LOCAL);
    }

    private OctavoProgressSyncStore qualifiedStore() throws IOException {
        OctavoProgressSyncStore result = store();
        assertEquals(OctavoProgressSyncStore.LoadStatus.MISSING_CREATED,
                     result.load());
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     result.stageInitialPresented(
                         OctavoProgressDisplay.PERCENTAGE));
        assertEquals(OctavoProgressSyncStore.MutationResult.UPDATED,
                     result.completePending(
                         result.pending(),
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressDisplay.PERCENTAGE,
                         OctavoProgressSyncStore.O8pgProof
                             .CANONICAL_V1_LOAD));
        return result;
    }

    private File childRoot(String name) {
        File result = new File(testRoot, name);
        assertFalse(result.exists());
        assertTrue(result.mkdirs());
        return result;
    }

    private static OctavoProgressPortable.Snapshot portableSnapshot(
        OctavoProgressSyncStore store) {
        OctavoProgressSyncStore.PortableExport exported =
            store.exportPortable();
        assertEquals(
            OctavoProgressSyncStore.PortableExportStatus.EXPORTED,
            exported.status);
        OctavoProgressPortable.DecodeResult decoded =
            OctavoProgressPortable.decode(exported.bytes());
        assertEquals(OctavoProgressPortable.DecodeStatus.READY,
                     decoded.status);
        return decoded.snapshot();
    }

    private static byte[] portableBytes(
        OctavoProgressPortable.Lane... lanes) throws IOException {
        return portableBytes(Arrays.asList(lanes));
    }

    private static byte[] portableBytes(
        List<OctavoProgressPortable.Lane> lanes) throws IOException {
        return OctavoProgressPortable.encode(
            new OctavoProgressPortable.Snapshot(lanes));
    }

    private static OctavoProgressPortable.Lane lane(
        String device, long sequence, OctavoProgressDisplay display) {
        return new OctavoProgressPortable.Lane(
            device, sequence,
            OctavoProgressPortable.Choice.fromDisplay(display));
    }

    private static byte[] futurePortable(int length, int version) {
        byte[] result = new byte[length];
        writeInt(result, 0, OctavoProgressPortable.magicForTesting());
        writeInt(result, 4, version);
        return result;
    }

    private static String device(int value) {
        return String.format(Locale.US, "%032x", value);
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
