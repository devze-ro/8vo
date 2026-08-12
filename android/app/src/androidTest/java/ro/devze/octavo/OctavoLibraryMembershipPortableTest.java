package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibraryMembershipPortableTest {
    @Test
    public void canonicalWireHasExactEmptyRecordAndStructuralBounds()
        throws IOException {
        OctavoLibraryMembershipPortable.Snapshot empty = snapshot();
        byte[] emptyBytes = OctavoLibraryMembershipPortable.encode(empty);
        assertEquals(20, emptyBytes.length);
        assertEquals(20,
                     OctavoLibraryMembershipPortable.minimumV1Bytes());
        assertEquals(7_104,
                     OctavoLibraryMembershipPortable.maximumRecordBytes());
        assertEquals(447_572,
                     OctavoLibraryMembershipPortable.maximumV1Bytes());
        assertEquals(524_244,
                     OctavoLibraryMembershipPortable.maximumFutureBytes());
        assertEquals(OctavoLibraryMembershipPortable.magicForTesting(),
                     readInt(emptyBytes, 0));
        assertEquals(OctavoLibraryMembershipPortable.versionForTesting(),
                     readInt(emptyBytes, 4));
        assertEquals(1, readInt(emptyBytes, 8));
        assertEquals(0, readInt(emptyBytes, 12));

        ArrayList<OctavoLibraryMembershipPortable.Record> records =
            new ArrayList<>();
        for (int index = 0; index < 63; ++index) {
            records.add(maximumRecord(index));
        }
        OctavoLibraryMembershipPortable.Snapshot maximum =
            new OctavoLibraryMembershipPortable.Snapshot(records);
        byte[] maximumBytes =
            OctavoLibraryMembershipPortable.encode(maximum);
        assertEquals(447_572, maximumBytes.length);
        OctavoLibraryMembershipPortable.DecodeResult decoded =
            OctavoLibraryMembershipPortable.decode(maximumBytes);
        assertEquals(OctavoLibraryMembershipPortable.DecodeStatus.READY,
                     decoded.status);
        assertEquals(maximum, decoded.snapshot());
        assertArrayEquals(maximumBytes,
                          OctavoLibraryMembershipPortable.encode(
                              decoded.snapshot()));

        byte[] currentPlusOne = new byte[447_573];
        writeInt(currentPlusOne, 0,
                 OctavoLibraryMembershipPortable.magicForTesting());
        writeInt(currentPlusOne, 4,
                 OctavoLibraryMembershipPortable.versionForTesting());
        assertInputLimit(currentPlusOne,
                         OctavoLibraryMembershipPortable.LimitReason
                             .ENCODED_BYTES);

        byte[] futureAtCap = futureBytes(524_244, 2);
        assertEquals(
            OctavoLibraryMembershipPortable.DecodeStatus.FUTURE_VERSION,
            OctavoLibraryMembershipPortable.decode(futureAtCap).status);
        assertInputLimit(
            futureBytes(524_245, 0xffffffff),
            OctavoLibraryMembershipPortable.LimitReason.ENCODED_BYTES);
    }

    @Test
    public void rootWithdrawUsesFrozenHashInputAndRoundTrips()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(1, 123);
        OctavoLibraryMembershipPortable.MutationResult result =
            OctavoLibraryMembershipPortable.withdraw(
                snapshot(), descriptor, actor(10), 1);
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.MUTATED,
                     result.status);
        assertNull(result.limitScope);
        assertNull(result.limitReason);
        assertEquals(1, result.snapshot.count());
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
            result.snapshot.projection(descriptor.digest));
        OctavoLibraryMembershipPortable.Record record =
            result.snapshot.record(descriptor.digest);
        assertEquals("66a69de068b05edc04df80d5493021cd",
                     record.heads().get(0).mutationId);
        assertTrue(record.heads().get(0).context().isEmpty());
        assertEquals(Long.valueOf(1),
                     record.frontier().get(actor(10)));

        byte[] bytes = OctavoLibraryMembershipPortable.encode(
            result.snapshot);
        assertEquals(237, bytes.length);
        OctavoLibraryMembershipPortable.DecodeResult decoded =
            OctavoLibraryMembershipPortable.decode(bytes);
        assertEquals(OctavoLibraryMembershipPortable.DecodeStatus.READY,
                     decoded.status);
        assertEquals(result.snapshot, decoded.snapshot());
        assertArrayEquals(bytes,
                          OctavoLibraryMembershipPortable.encode(
                              decoded.snapshot()));
    }

    @Test
    public void decodeRejectsMalformedNoncanonicalAndCausallyInvalidBytes()
        throws IOException {
        byte[] root = bytes(withdrawnRoot(1, 100, 10, 1));

        byte[] checksum = root.clone();
        checksum[checksum.length - 1] ^= 1;
        assertDecode(checksum,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] fieldCount = root.clone();
        writeInt(fieldCount, 8, 2);
        repairChecksum(fieldCount);
        assertDecode(fieldCount,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] uppercaseDigest = root.clone();
        uppercaseDigest[20] = 'A';
        repairChecksum(uppercaseDigest);
        assertDecode(uppercaseDigest,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] wrongDigestLength = root.clone();
        writeInt(wrongDigestLength, 16, 63);
        repairChecksum(wrongDigestLength);
        assertDecode(wrongDigestLength,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] zeroByteCount = root.clone();
        writeLong(zeroByteCount, 84, 0);
        repairChecksum(zeroByteCount);
        assertDecode(zeroByteCount,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] wrongKind = root.clone();
        writeInt(wrongKind, 92, 2);
        repairChecksum(wrongKind);
        assertDecode(wrongKind,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] changedMutationId = root.clone();
        changedMutationId[152] = changedMutationId[152] == '0'
            ? (byte)'1' : (byte)'0';
        repairChecksum(changedMutationId);
        assertDecode(changedMutationId,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] headCounterDiffersFromFrontier = root.clone();
        writeLong(headCounterDiffersFromFrontier, 220, 2);
        repairChecksum(headCounterDiffersFromFrontier);
        assertDecode(headCounterDiffersFromFrontier,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] badOperation = root.clone();
        badOperation[228] = 3;
        repairChecksum(badOperation);
        assertDecode(badOperation,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] trailing = Arrays.copyOf(root, root.length + 1);
        assertDecode(trailing,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);
        assertDecode(Arrays.copyOf(root, root.length - 1),
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);
        assertDecode(null,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);
        assertDecode(new byte[7],
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] plausibleRecordWithoutMinimumBytes =
            OctavoLibraryMembershipPortable.encode(snapshot());
        writeInt(plausibleRecordWithoutMinimumBytes, 12, 1);
        repairChecksum(plausibleRecordWithoutMinimumBytes);
        assertDecode(plausibleRecordWithoutMinimumBytes,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] plausibleFrontiersWithoutMinimumBytes = root.clone();
        writeInt(plausibleFrontiersWithoutMinimumBytes, 96, 16);
        repairChecksum(plausibleFrontiersWithoutMinimumBytes);
        assertDecode(plausibleFrontiersWithoutMinimumBytes,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] plausibleHeadsWithoutMinimumBytes = root.clone();
        writeInt(plausibleHeadsWithoutMinimumBytes, 144, 8);
        repairChecksum(plausibleHeadsWithoutMinimumBytes);
        assertDecode(plausibleHeadsWithoutMinimumBytes,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] plausibleContextsWithoutMinimumBytes = root.clone();
        writeInt(plausibleContextsWithoutMinimumBytes, 229, 16);
        repairChecksum(plausibleContextsWithoutMinimumBytes);
        assertDecode(plausibleContextsWithoutMinimumBytes,
                     OctavoLibraryMembershipPortable.DecodeStatus.INVALID);

        byte[] recordLimit = OctavoLibraryMembershipPortable.encode(
            snapshot());
        writeInt(recordLimit, 12, 64);
        repairChecksum(recordLimit);
        assertInputLimit(
            recordLimit,
            OctavoLibraryMembershipPortable.LimitReason.RECORD_HISTORY);

        byte[] actorLimit = root.clone();
        writeInt(actorLimit, 96, 17);
        repairChecksum(actorLimit);
        assertInputLimit(
            actorLimit,
            OctavoLibraryMembershipPortable.LimitReason.ACTOR_HISTORY);

        byte[] headLimit = root.clone();
        writeInt(headLimit, 144, 9);
        repairChecksum(headLimit);
        assertInputLimit(
            headLimit,
            OctavoLibraryMembershipPortable.LimitReason.CONCURRENT_HEADS);
    }

    @Test
    public void constructorRejectsRestoreWithoutHistoryAndGlobalDotReuse()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor first =
            descriptor(1, 100);
        TreeMap<String, Long> firstFrontier = vector(actor(1), 1);
        OctavoLibraryMembershipPortable.Mutation emptyRestore = mutation(
            first, actor(1), 1,
            OctavoLibraryMembershipPortable.Operation.RESTORE,
            Collections.<String, Long>emptyMap());
        expectIllegalArgument(new Action() {
            @Override public void run() {
                new OctavoLibraryMembershipPortable.Record(
                    first, firstFrontier,
                    Collections.singletonList(emptyRestore));
            }
        });

        OctavoLibraryMembershipPortable.Record firstRecord =
            withdrawnRoot(1, 100, 9, 1);
        OctavoLibraryMembershipPortable.Record secondRecord =
            withdrawnRoot(2, 200, 9, 1);
        expectIllegalArgument(new Action() {
            @Override public void run() {
                new OctavoLibraryMembershipPortable.Snapshot(
                    Arrays.asList(firstRecord, secondRecord));
            }
        });
    }

    @Test
    public void oppositeFrontierJoinPrunesStaleHeadsAndRejectsCycles()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(7, 700);
        OctavoLibraryMembershipPortable.Snapshot root = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                snapshot(), descriptor, actor(1), 1));
        OctavoLibraryMembershipPortable.Snapshot restored = mutated(
            OctavoLibraryMembershipPortable.restore(
                root, descriptor, actor(2), 1));
        OctavoLibraryMembershipPortable.Snapshot withdrawn = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                restored, descriptor, actor(3), 1));

        assertEquals(withdrawn, merged(withdrawn, root));
        assertEquals(withdrawn, merged(root, withdrawn));
        OctavoLibraryMembershipPortable.MergeResult idempotent =
            OctavoLibraryMembershipPortable.merge(withdrawn, withdrawn);
        assertEquals(OctavoLibraryMembershipPortable.MergeStatus.UNCHANGED,
                     idempotent.status);
        assertSame(withdrawn, idempotent.snapshot);

        TreeMap<String, Long> both = vector(
            actor(20), 1, actor(21), 1);
        OctavoLibraryMembershipPortable.Mutation leftHead = mutation(
            descriptor, actor(20), 1,
            OctavoLibraryMembershipPortable.Operation.WITHDRAW,
            vector(actor(21), 1));
        OctavoLibraryMembershipPortable.Mutation rightHead = mutation(
            descriptor, actor(21), 1,
            OctavoLibraryMembershipPortable.Operation.WITHDRAW,
            vector(actor(20), 1));
        OctavoLibraryMembershipPortable.Snapshot leftCycle = snapshot(
            new OctavoLibraryMembershipPortable.Record(
                descriptor, both, Collections.singletonList(leftHead)));
        OctavoLibraryMembershipPortable.Snapshot rightCycle = snapshot(
            new OctavoLibraryMembershipPortable.Record(
                descriptor, both, Collections.singletonList(rightHead)));
        OctavoLibraryMembershipPortable.MergeResult cycle =
            OctavoLibraryMembershipPortable.merge(leftCycle, rightCycle);
        assertEquals(OctavoLibraryMembershipPortable.MergeStatus.INVALID,
                     cycle.status);
        assertSame(leftCycle, cycle.snapshot);
    }

    @Test
    public void concurrentOppositeHeadsStayConflictUntilExplicitResolution()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(11, 1_111);
        OctavoLibraryMembershipPortable.Snapshot initialWithdraw = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                snapshot(), descriptor, actor(1), 1));
        OctavoLibraryMembershipPortable.Snapshot commonRestore = mutated(
            OctavoLibraryMembershipPortable.restore(
                initialWithdraw, descriptor, actor(2), 1));
        OctavoLibraryMembershipPortable.Snapshot withdrawBranch = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                commonRestore, descriptor, actor(3), 1));
        OctavoLibraryMembershipPortable.Snapshot restoreBranch = mutated(
            OctavoLibraryMembershipPortable.restore(
                initialWithdraw, descriptor, actor(4), 1));

        OctavoLibraryMembershipPortable.Snapshot conflict =
            merged(withdrawBranch, restoreBranch);
        assertEquals(
            OctavoLibraryMembershipPortable.Projection.CONFLICT,
            conflict.projection(descriptor.digest));
        assertEquals(conflict,
                     merged(restoreBranch, withdrawBranch));

        OctavoLibraryMembershipPortable.MutationResult ordinaryWithdraw =
            OctavoLibraryMembershipPortable.withdraw(
                conflict, descriptor, actor(5), 1);
        OctavoLibraryMembershipPortable.MutationResult ordinaryRestore =
            OctavoLibraryMembershipPortable.restore(
                conflict, descriptor, actor(5), 1);
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.INVALID,
                     ordinaryWithdraw.status);
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.INVALID,
                     ordinaryRestore.status);
        assertSame(conflict, ordinaryWithdraw.snapshot);
        assertSame(conflict, ordinaryRestore.snapshot);

        OctavoLibraryMembershipPortable.Snapshot member = mutated(
            OctavoLibraryMembershipPortable.resolveConflict(
                conflict, descriptor, actor(5), 1,
                OctavoLibraryMembershipPortable.Projection.MEMBER));
        assertEquals(OctavoLibraryMembershipPortable.Projection.MEMBER,
                     member.projection(descriptor.digest));
        assertEquals(member, merged(member, conflict));
        assertEquals(member, merged(conflict, member));

        OctavoLibraryMembershipPortable.Snapshot withdrawn = mutated(
            OctavoLibraryMembershipPortable.resolveConflict(
                conflict, descriptor, actor(6), 1,
                OctavoLibraryMembershipPortable.Projection.WITHDRAWN));
        assertEquals(OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
                     withdrawn.projection(descriptor.digest));
        assertEquals(withdrawn, merged(withdrawn, conflict));
    }

    @Test
    public void localMutationRulesAreExactAndTypedLimitsRollback()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(1, 100);
        OctavoLibraryMembershipPortable.Snapshot empty = snapshot();
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.INVALID,
            OctavoLibraryMembershipPortable.restore(
                empty, descriptor, actor(1), 1).status);

        OctavoLibraryMembershipPortable.MutationResult rootResult =
            OctavoLibraryMembershipPortable.withdraw(
                empty, descriptor, actor(1), 1);
        OctavoLibraryMembershipPortable.Snapshot root = mutated(rootResult);
        OctavoLibraryMembershipPortable.MutationResult repeated =
            OctavoLibraryMembershipPortable.withdraw(
                root, descriptor, "not-an-actor", -1);
        assertEquals(
            OctavoLibraryMembershipPortable.MutationStatus.UNCHANGED,
            repeated.status);
        assertSame(root, repeated.snapshot);

        OctavoLibraryMembershipPortable.Descriptor mismatch =
            descriptor(1, 101);
        assertEquals(
            OctavoLibraryMembershipPortable.MutationStatus.EQUIVOCATION,
            OctavoLibraryMembershipPortable.withdraw(
                root, mismatch, actor(2), 1).status);
        assertEquals(
            OctavoLibraryMembershipPortable.MutationStatus.EQUIVOCATION,
            OctavoLibraryMembershipPortable.resolveConflict(
                root, mismatch, actor(2), 1,
                OctavoLibraryMembershipPortable.Projection.MEMBER).status);
        assertEquals(
            OctavoLibraryMembershipPortable.MutationStatus.INVALID,
            OctavoLibraryMembershipPortable.resolveConflict(
                root, descriptor, actor(2), 1,
                OctavoLibraryMembershipPortable.Projection.MEMBER).status);

        OctavoLibraryMembershipPortable.Snapshot fullRecords = snapshot();
        for (int index = 0; index < 63; ++index) {
            fullRecords = mutated(
                OctavoLibraryMembershipPortable.withdraw(
                    fullRecords, descriptor(1_000 + index, index + 1),
                    actor(100), index + 1));
        }
        assertMutationLimit(
            OctavoLibraryMembershipPortable.withdraw(
                fullRecords, descriptor(2_000, 1), actor(100), 64),
            fullRecords,
            OctavoLibraryMembershipPortable.LimitReason.RECORD_HISTORY);

        OctavoLibraryMembershipPortable.Snapshot actorFull = snapshot();
        for (int index = 0; index < 16; ++index) {
            if ((index & 1) == 0) {
                actorFull = mutated(
                    OctavoLibraryMembershipPortable.withdraw(
                        actorFull, descriptor, actor(200 + index), 1));
            } else {
                actorFull = mutated(
                    OctavoLibraryMembershipPortable.restore(
                        actorFull, descriptor, actor(200 + index), 1));
            }
        }
        assertMutationLimit(
            OctavoLibraryMembershipPortable.withdraw(
                actorFull, descriptor, actor(216), 1),
            actorFull,
            OctavoLibraryMembershipPortable.LimitReason.ACTOR_HISTORY);

        OctavoLibraryMembershipPortable.Snapshot exhausted = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                snapshot(), descriptor, actor(300), Long.MAX_VALUE));
        assertMutationLimit(
            OctavoLibraryMembershipPortable.restore(
                exhausted, descriptor, actor(300), Long.MAX_VALUE),
            exhausted,
            OctavoLibraryMembershipPortable.LimitReason.COUNTER_EXHAUSTED);
    }

    @Test
    public void joinDetectsEquivocationBeforeCapacityAndHeadLimitIsTyped()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(50, 500);
        OctavoLibraryMembershipPortable.Snapshot root = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                snapshot(), descriptor, actor(1), 1));
        OctavoLibraryMembershipPortable.Snapshot joined = null;
        for (int index = 0; index < 9; ++index) {
            OctavoLibraryMembershipPortable.Snapshot branch = mutated(
                OctavoLibraryMembershipPortable.restore(
                    root, descriptor, actor(10 + index), 1));
            if (joined == null) {
                joined = branch;
            } else if (index < 8) {
                joined = merged(joined, branch);
            } else {
                OctavoLibraryMembershipPortable.MergeResult limited =
                    OctavoLibraryMembershipPortable.merge(joined, branch);
                assertEquals(
                    OctavoLibraryMembershipPortable.MergeStatus.LIMIT,
                    limited.status);
                assertSame(joined, limited.snapshot);
                assertEquals(
                    OctavoLibraryMembershipPortable.LimitScope.JOIN,
                    limited.limitScope);
                assertEquals(
                    OctavoLibraryMembershipPortable.LimitReason
                        .CONCURRENT_HEADS,
                    limited.limitReason);
            }
        }

        ArrayList<OctavoLibraryMembershipPortable.Record> full =
            new ArrayList<>();
        for (int index = 0; index < 63; ++index) {
            full.add(withdrawnRoot(1_000 + index, index + 1,
                                  1_000 + index, 1));
        }
        OctavoLibraryMembershipPortable.Snapshot fullSnapshot =
            new OctavoLibraryMembershipPortable.Snapshot(full);
        OctavoLibraryMembershipPortable.Snapshot incoming = snapshot(
            withdrawnRoot(1_000, 501, 5_000, 1),
            withdrawnRoot(9_999, 1, 5_001, 1));
        OctavoLibraryMembershipPortable.MergeResult equivocation =
            OctavoLibraryMembershipPortable.merge(fullSnapshot, incoming);
        assertEquals(
            OctavoLibraryMembershipPortable.MergeStatus.EQUIVOCATION,
            equivocation.status);
        assertSame(fullSnapshot, equivocation.snapshot);

        OctavoLibraryMembershipPortable.Snapshot sameDotWithdraw =
            snapshot(withdrawnRoot(77, 77, 7_700, 1));
        OctavoLibraryMembershipPortable.Descriptor sameDotDescriptor =
            descriptor(77, 77);
        TreeMap<String, Long> sameDotFrontier = vector(
            actor(7_700), 1, actor(7_701), 1);
        OctavoLibraryMembershipPortable.Mutation forgedRestore = mutation(
            sameDotDescriptor, actor(7_700), 1,
            OctavoLibraryMembershipPortable.Operation.RESTORE,
            vector(actor(7_701), 1));
        OctavoLibraryMembershipPortable.Snapshot sameDotRestore = snapshot(
            new OctavoLibraryMembershipPortable.Record(
                sameDotDescriptor, sameDotFrontier,
                Collections.singletonList(forgedRestore)));
        assertEquals(
            OctavoLibraryMembershipPortable.MergeStatus.EQUIVOCATION,
            OctavoLibraryMembershipPortable.merge(
                sameDotWithdraw, sameDotRestore).status);
    }

    @Test
    public void snapshotsResultsAndPreservedFutureBytesAreDefensive()
        throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(1, 100);
        OctavoLibraryMembershipPortable.Snapshot state = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                snapshot(), descriptor, actor(1), 1));
        assertEquals(1, state.count());
        assertEquals(1, state.recordCount());
        assertTrue(state.actorAppears(actor(1)));
        assertFalse(state.actorAppears(actor(2)));
        assertEquals(1, state.maximumActorCounter(actor(1)));
        assertEquals(0, state.maximumActorCounter(actor(2)));
        assertNull(state.projection(digest(99)));
        assertNotNull(state.record(descriptor.digest).descriptor());

        expectUnsupported(new Action() {
            @Override public void run() {
                state.records().clear();
            }
        });
        expectUnsupported(new Action() {
            @Override public void run() {
                state.record(descriptor.digest).frontier()
                    .put(actor(2), 2L);
            }
        });
        expectUnsupported(new Action() {
            @Override public void run() {
                state.record(descriptor.digest).heads().get(0)
                    .context().put(actor(2), 2L);
            }
        });

        byte[] future = futureBytes(32, 2);
        OctavoLibraryMembershipPortable.DecodeResult decoded =
            OctavoLibraryMembershipPortable.decode(future);
        byte[] first = decoded.preservedBytes();
        first[10] = 99;
        assertNotEquals(first[10], decoded.preservedBytes()[10]);
        future[11] = 88;
        assertNotEquals(future[11], decoded.preservedBytes()[11]);
    }

    private static OctavoLibraryMembershipPortable.Record maximumRecord(
        int recordIndex) throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(10_000 + recordIndex, recordIndex + 1);
        TreeMap<String, Long> frontier = new TreeMap<>();
        ArrayList<String> actors = new ArrayList<>();
        for (int actorIndex = 0; actorIndex < 16; ++actorIndex) {
            String actor = actor(
                100_000 + 16 * recordIndex + actorIndex);
            actors.add(actor);
            frontier.put(actor, actorIndex < 8 ? 2L : 1L);
        }
        TreeMap<String, Long> context = new TreeMap<>();
        for (String actor : actors) {
            context.put(actor, 1L);
        }
        ArrayList<OctavoLibraryMembershipPortable.Mutation> heads =
            new ArrayList<>();
        for (int actorIndex = 0; actorIndex < 8; ++actorIndex) {
            heads.add(mutation(
                descriptor, actors.get(actorIndex), 2,
                OctavoLibraryMembershipPortable.Operation.RESTORE,
                context));
        }
        return new OctavoLibraryMembershipPortable.Record(
            descriptor, frontier, heads);
    }

    private static OctavoLibraryMembershipPortable.Record withdrawnRoot(
        int digestValue,
        long byteCount,
        int actorValue,
        long counter) throws IOException {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            descriptor(digestValue, byteCount);
        String actor = actor(actorValue);
        OctavoLibraryMembershipPortable.Mutation mutation = mutation(
            descriptor, actor, counter,
            OctavoLibraryMembershipPortable.Operation.WITHDRAW,
            Collections.<String, Long>emptyMap());
        return new OctavoLibraryMembershipPortable.Record(
            descriptor, vector(actor, counter),
            Collections.singletonList(mutation));
    }

    private static OctavoLibraryMembershipPortable.Mutation mutation(
        OctavoLibraryMembershipPortable.Descriptor descriptor,
        String actor,
        long counter,
        OctavoLibraryMembershipPortable.Operation operation,
        Map<String, Long> context) throws IOException {
        String mutationId =
            OctavoLibraryMembershipPortable.mutationIdForTesting(
                descriptor, actor, counter, operation, context);
        return new OctavoLibraryMembershipPortable.Mutation(
            mutationId, actor, counter, operation, context);
    }

    private static OctavoLibraryMembershipPortable.Snapshot mutated(
        OctavoLibraryMembershipPortable.MutationResult result) {
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.MUTATED,
                     result.status);
        return result.snapshot;
    }

    private static OctavoLibraryMembershipPortable.Snapshot merged(
        OctavoLibraryMembershipPortable.Snapshot left,
        OctavoLibraryMembershipPortable.Snapshot right) {
        OctavoLibraryMembershipPortable.MergeResult result =
            OctavoLibraryMembershipPortable.merge(left, right);
        if (result.status
            == OctavoLibraryMembershipPortable.MergeStatus.UNCHANGED) {
            return result.snapshot;
        }
        assertEquals(OctavoLibraryMembershipPortable.MergeStatus.MERGED,
                     result.status);
        return result.snapshot;
    }

    private static byte[] bytes(
        OctavoLibraryMembershipPortable.Record... records)
        throws IOException {
        return OctavoLibraryMembershipPortable.encode(snapshot(records));
    }

    private static OctavoLibraryMembershipPortable.Snapshot snapshot(
        OctavoLibraryMembershipPortable.Record... records) {
        return new OctavoLibraryMembershipPortable.Snapshot(
            Arrays.asList(records));
    }

    private static OctavoLibraryMembershipPortable.Descriptor descriptor(
        int digestValue, long byteCount) {
        return new OctavoLibraryMembershipPortable.Descriptor(
            digest(digestValue), byteCount);
    }

    private static String digest(int value) {
        return String.format(Locale.US, "%064x", value);
    }

    private static String actor(int value) {
        return String.format(Locale.US, "%032x", value);
    }

    private static TreeMap<String, Long> vector(
        String actor, long counter) {
        TreeMap<String, Long> result = new TreeMap<>();
        result.put(actor, counter);
        return result;
    }

    private static TreeMap<String, Long> vector(
        String firstActor, long firstCounter,
        String secondActor, long secondCounter) {
        TreeMap<String, Long> result = vector(firstActor, firstCounter);
        result.put(secondActor, secondCounter);
        return result;
    }

    private static void assertMutationLimit(
        OctavoLibraryMembershipPortable.MutationResult result,
        OctavoLibraryMembershipPortable.Snapshot expectedSnapshot,
        OctavoLibraryMembershipPortable.LimitReason reason) {
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.LIMIT,
                     result.status);
        assertSame(expectedSnapshot, result.snapshot);
        assertEquals(OctavoLibraryMembershipPortable.LimitScope.LOCAL,
                     result.limitScope);
        assertEquals(reason, result.limitReason);
    }

    private static void assertInputLimit(
        byte[] bytes,
        OctavoLibraryMembershipPortable.LimitReason reason) {
        OctavoLibraryMembershipPortable.DecodeResult result =
            OctavoLibraryMembershipPortable.decode(bytes);
        assertEquals(OctavoLibraryMembershipPortable.DecodeStatus.LIMIT,
                     result.status);
        assertEquals(OctavoLibraryMembershipPortable.LimitScope.INPUT,
                     result.limitScope);
        assertEquals(reason, result.limitReason);
        assertNull(result.snapshot());
    }

    private static void assertDecode(
        byte[] bytes,
        OctavoLibraryMembershipPortable.DecodeStatus expected) {
        assertEquals(expected,
                     OctavoLibraryMembershipPortable.decode(bytes).status);
    }

    private static byte[] futureBytes(int length, int version) {
        byte[] bytes = new byte[length];
        writeInt(bytes, 0,
                 OctavoLibraryMembershipPortable.magicForTesting());
        writeInt(bytes, 4, version);
        return bytes;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
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

    private static void expectIllegalArgument(Action action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Action action) {
        try {
            action.run();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private interface Action {
        void run();
    }
}
