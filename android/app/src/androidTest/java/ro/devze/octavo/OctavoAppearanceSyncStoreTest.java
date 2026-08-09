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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoAppearanceSyncStoreTest {
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
            "octavo-appearance-sync-" + System.nanoTime());
        assertFalse(testRoot.exists());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void missingStateQualifiesOnlyPresentedAndDurableAppearance()
        throws IOException {
        OctavoAppearanceSyncStore store = store();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.BLOCKED,
            store.beginReviewEpoch(true));
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            store.load());
        assertEquals(LOCAL, store.deviceId());
        assertNull(store.effectiveAppearance());
        assertNull(store.localLane());
        assertNull(store.pending());
        assertEquals(0, store.reviewEpoch());
        assertTrue(store.stateFileForTesting().isFile());
        byte[] identityOnly = readFile(store.stateFileForTesting());
        assertEquals(72, identityOnly.length);

        OctavoAppearanceSyncStore.PortableExport empty =
            store.exportPortable();
        assertEquals(
            OctavoAppearanceSyncStore.PortableExportStatus.EXPORTED,
            empty.status);
        assertEquals(20, empty.bytes().length);
        assertEquals(0, OctavoAppearancePortable.decode(empty.bytes())
            .snapshot().laneCount());

        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UNCHANGED,
            store.beginReviewEpoch(false));
        assertArrayEquals(identityOnly,
                          readFile(store.stateFileForTesting()));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.beginReviewEpoch(true));
        assertEquals(1, store.reviewEpoch());
        assertTrue(store.stateFileForTesting().isFile());

        OctavoAppearanceSyncStore reloaded = store();
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reloaded.load());
        assertEquals(LOCAL, reloaded.deviceId());
        assertEquals(1, reloaded.reviewEpoch());
        assertNull(reloaded.effectiveAppearance());

        File randomRoot = childRoot("random-stable-id");
        OctavoAppearanceSyncStore randomFirst =
            new OctavoAppearanceSyncStore(randomRoot);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            randomFirst.load());
        String randomId = randomFirst.deviceId();
        OctavoAppearanceSyncStore randomSecond =
            new OctavoAppearanceSyncStore(randomRoot);
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     randomSecond.load());
        assertEquals(randomId, randomSecond.deviceId());

        File failedIdRoot = childRoot("failed-id");
        OctavoAppearanceSyncStore failedIdentity =
            new OctavoAppearanceSyncStore(failedIdRoot, LOCAL);
        failedIdentity.failNextPublishForTesting();
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.IDENTITY_PUBLISH_FAILED,
            failedIdentity.load());
        assertFalse(failedIdentity.stateFileForTesting().exists());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.BLOCKED,
            failedIdentity.beginReviewEpoch(true));
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            failedIdentity.load());
        assertTrue(failedIdentity.stateFileForTesting().isFile());

        OctavoAppearance presented = OctavoAppearance.defaults();
        assertTrue(reloaded.reviewCandidates(presented).isEmpty());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            reloaded.stageLocalPresented(presented));
        OctavoAppearanceSyncStore.Pending pending = reloaded.pending();
        assertNotNull(pending);
        assertEquals(OctavoAppearanceSyncStore.PendingKind.LOCAL,
                     pending.kind);
        assertFalse(pending.hasOriginLane);
        assertEquals(1, pending.localSequence);
        assertNull(reloaded.effectiveAppearance());
        assertEquals(0, portableSnapshot(reloaded).laneCount());

        byte[] staged = readFile(reloaded.stateFileForTesting());
        OctavoAppearance wrong = presented.withTheme(
            OctavoAppearance.THEME_SEPIA);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.INVALID,
            reloaded.completePending(
                pending, wrong, presented,
                OctavoAppearanceSyncStore.O7stProof.CANONICAL_V3_LOAD));
        assertArrayEquals(staged, readFile(reloaded.stateFileForTesting()));
        assertNull(reloaded.effectiveAppearance());

        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            reloaded.completePending(
                pending, presented, presented,
                OctavoAppearanceSyncStore.O7stProof.CANONICAL_V3_LOAD));
        assertEquals(presented, reloaded.effectiveAppearance());
        assertEquals(1, reloaded.localLane().sequence);
        assertNull(reloaded.pending());
        assertEquals(1, portableSnapshot(reloaded).laneCount());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UNCHANGED,
            reloaded.stageLocalPresented(presented));
    }

    @Test
    public void reviewDecisionsAreExactDurableAndEpochScoped()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        OctavoAppearance local = OctavoAppearance.defaults();
        OctavoAppearance remoteA = local.withTheme(
            OctavoAppearance.THEME_SEPIA);

        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(
                lane(REMOTE_B, 1, local),
                lane(REMOTE_A, 1, remoteA))));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.recordConverged(local));
        List<OctavoAppearanceSyncStore.Candidate> candidates =
            store.reviewCandidates(local);
        assertEquals(1, candidates.size());
        assertEquals(REMOTE_A, candidates.get(0).deviceId);
        assertEquals(1, candidates.get(0).differenceCount());

        OctavoAppearanceSyncStore.Candidate first = candidates.get(0);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.dismiss(first, local));
        assertTrue(store.reviewCandidates(local).isEmpty());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UNCHANGED,
            store.beginReviewEpoch(false));
        assertTrue(store.reviewCandidates(local).isEmpty());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.beginReviewEpoch(true));
        candidates = store.reviewCandidates(local);
        assertEquals(1, candidates.size());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.keep(candidates.get(0), local));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.beginReviewEpoch(true));
        assertTrue(store.reviewCandidates(local).isEmpty());

        OctavoAppearance newer = local.withTheme(
            OctavoAppearance.THEME_DUSK);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 2, newer))));
        OctavoAppearanceSyncStore.Candidate newerCandidate =
            store.reviewCandidates(local).get(0);
        assertEquals(2, newerCandidate.sequence);

        OctavoAppearance localChange = local.withTheme(
            OctavoAppearance.THEME_OLED);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageLocalPresented(localChange));
        OctavoAppearanceSyncStore.Pending pending = store.pending();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.completePending(
                pending, localChange, localChange,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.CONFLICT,
            store.keep(newerCandidate, localChange));

        candidates = store.reviewCandidates(localChange);
        assertEquals(1, candidates.size());
        assertEquals(REMOTE_A, candidates.get(0).deviceId);
        assertEquals(2, candidates.get(0).sequence);
        for (OctavoAppearanceSyncStore.Candidate value : candidates) {
            assertNotEquals(REMOTE_B, value.deviceId);
        }

        OctavoAppearanceSyncStore reload = store();
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reload.load());
        assertEquals(localChange, reload.effectiveAppearance());
        assertEquals(1, reload.reviewCandidates(localChange).size());
    }

    @Test
    public void remoteApplyPublishesOnlyAfterExactPresentationAndO7stProof()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        OctavoAppearance origin = OctavoAppearance.defaults();
        OctavoAppearance target = origin.withFontSizeSp(24)
            .withLineSpacingPermille(1500)
            .withReducedMotion(true);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 5, target))));
        OctavoAppearanceSyncStore.Candidate candidate =
            store.reviewCandidates(origin).get(0);
        assertEquals(3, candidate.differenceCount());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageRemoteApply(candidate, origin));
        OctavoAppearanceSyncStore.Pending pending = store.pending();
        assertNotNull(pending);
        assertEquals(OctavoAppearanceSyncStore.PendingKind.REMOTE,
                     pending.kind);
        assertEquals(origin, pending.originAppearance());
        assertEquals(target, pending.targetAppearance());
        assertEquals(1, portableSnapshot(store).lane(LOCAL).sequence);
        assertEquals(origin,
                     portableSnapshot(store).lane(LOCAL)
                         .profile.toAppearance());

        byte[] staged = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.INVALID,
            store.completePending(
                pending, target, target,
                OctavoAppearanceSyncStore.O7stProof.CANONICAL_V3_LOAD));
        assertArrayEquals(staged, readFile(store.stateFileForTesting()));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.INVALID,
            store.completePending(
                pending, origin, target,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        assertArrayEquals(staged, readFile(store.stateFileForTesting()));

        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.completePending(
                pending, target, target,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        assertNull(store.pending());
        assertEquals(target, store.effectiveAppearance());
        assertEquals(2, store.localLane().sequence);
        assertTrue(store.reviewCandidates(target).isEmpty());

        OctavoAppearanceSyncStore reload = store();
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reload.load());
        assertEquals(target, reload.effectiveAppearance());
        assertNull(reload.pending());
        assertTrue(reload.reviewCandidates(target).isEmpty());
    }

    @Test
    public void supersessionRejectsStaleCompletionUntilOrderedRollback()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        OctavoAppearance origin = OctavoAppearance.defaults();
        OctavoAppearance firstTarget = origin.withTheme(
            OctavoAppearance.THEME_SEPIA);
        OctavoAppearance newerTarget = origin.withTheme(
            OctavoAppearance.THEME_DUSK);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 1, firstTarget))));
        OctavoAppearanceSyncStore.Candidate staleCandidate =
            store.reviewCandidates(origin).get(0);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageRemoteApply(staleCandidate, origin));
        OctavoAppearanceSyncStore.Pending pending = store.pending();
        byte[] pendingBeforeOpen =
            readFile(store.stateFileForTesting());
        long pendingEpoch = store.reviewEpoch();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.BLOCKED,
            store.beginReviewEpoch(true));
        assertEquals(pendingEpoch, store.reviewEpoch());
        assertArrayEquals(pendingBeforeOpen,
                          readFile(store.stateFileForTesting()));

        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 2, newerTarget))));
        byte[] superseded = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.CONFLICT,
            store.completePending(
                pending, firstTarget, firstTarget,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        assertArrayEquals(superseded,
                          readFile(store.stateFileForTesting()));
        assertNotNull(store.pending());

        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.INVALID,
            store.dismissPendingAfterRollback(
                pending, firstTarget, origin,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE,
                false));
        assertArrayEquals(superseded,
                          readFile(store.stateFileForTesting()));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.dismissPendingAfterRollback(
                pending, origin, origin,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE,
                false));
        assertNull(store.pending());

        List<OctavoAppearanceSyncStore.Candidate> candidates =
            store.reviewCandidates(origin);
        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).sequence);
        assertEquals(newerTarget, candidates.get(0).targetAppearance());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.CONFLICT,
            store.stageRemoteApply(staleCandidate, origin));
    }

    @Test
    public void rollbackAtomicallyConsumesDeferredOpenEpochOrClearsAtCeiling()
        throws IOException {
        OctavoAppearance origin = OctavoAppearance.defaults();
        OctavoAppearance target = origin.withTheme(
            OctavoAppearance.THEME_SEPIA);

        File remoteRoot = childRoot("deferred-remote-rollback");
        OctavoAppearanceSyncStore remoteStore =
            qualifiedStore(remoteRoot);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            remoteStore.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 1, target))));
        OctavoAppearanceSyncStore.Candidate remoteCandidate =
            remoteStore.reviewCandidates(origin).get(0);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            remoteStore.stageRemoteApply(remoteCandidate, origin));

        OctavoAppearanceSyncStore loadedRemote =
            new OctavoAppearanceSyncStore(remoteRoot, LOCAL);
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     loadedRemote.load());
        OctavoAppearanceSyncStore.Pending remotePending =
            loadedRemote.pending();
        assertNotNull(remotePending);
        long remoteEpoch = loadedRemote.reviewEpoch();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            loadedRemote.dismissPendingAfterRollback(
                remotePending, origin, origin,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE,
                true));
        assertEquals(remoteEpoch + 1, loadedRemote.reviewEpoch());
        assertNull(loadedRemote.pending());
        assertTrue(loadedRemote.reviewCandidates(origin).isEmpty());
        byte[] dismissedRemote = readFile(
            loadedRemote.stateFileForTesting());

        OctavoAppearanceSyncStore reloadedRemote =
            new OctavoAppearanceSyncStore(remoteRoot, LOCAL);
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reloadedRemote.load());
        assertEquals(remoteEpoch + 1, reloadedRemote.reviewEpoch());
        assertNull(reloadedRemote.pending());
        assertTrue(reloadedRemote.reviewCandidates(origin).isEmpty());
        assertArrayEquals(dismissedRemote,
                          readFile(reloadedRemote.stateFileForTesting()));

        File localRoot = childRoot("deferred-local-rollback");
        OctavoAppearanceSyncStore localStore = qualifiedStore(localRoot);
        long localEpoch = localStore.reviewEpoch();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            localStore.stageLocalPresented(target));
        OctavoAppearanceSyncStore.Pending localPending =
            localStore.pending();
        assertNotNull(localPending);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            localStore.dismissPendingAfterRollback(
                localPending, origin, origin,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE,
                true));
        assertEquals(localEpoch + 1, localStore.reviewEpoch());
        assertNull(localStore.pending());
        assertEquals(origin, localStore.effectiveAppearance());

        File exhaustedRoot = childRoot("deferred-epoch-exhausted");
        OctavoAppearanceSyncStore exhausted =
            qualifiedStore(exhaustedRoot);
        byte[] exhaustedState = readFile(
            exhausted.stateFileForTesting());
        writeLong(exhaustedState, 44, Long.MAX_VALUE);
        repairChecksum(exhaustedState);
        writeFile(exhausted.stateFileForTesting(), exhaustedState);
        OctavoAppearanceSyncStore exhaustedReload =
            new OctavoAppearanceSyncStore(exhaustedRoot, LOCAL);
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     exhaustedReload.load());
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            exhaustedReload.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 1, target))));
        OctavoAppearanceSyncStore.Candidate exhaustedCandidate =
            exhaustedReload.reviewCandidates(origin).get(0);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            exhaustedReload.stageRemoteApply(
                exhaustedCandidate, origin));
        OctavoAppearanceSyncStore.Pending exhaustedPending =
            exhaustedReload.pending();
        assertNotNull(exhaustedPending);
        byte[] beforeCeilingRollback = readFile(
            exhaustedReload.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            exhaustedReload.dismissPendingAfterRollback(
                exhaustedPending, origin, origin,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE,
                true));
        assertEquals(Long.MAX_VALUE, exhaustedReload.reviewEpoch());
        assertNull(exhaustedReload.pending());
        assertTrue(exhaustedReload.reviewCandidates(origin).isEmpty());
        byte[] afterCeilingRollback = readFile(
            exhaustedReload.stateFileForTesting());
        assertFalse(Arrays.equals(beforeCeilingRollback,
                                  afterCeilingRollback));

        OctavoAppearanceSyncStore exhaustedAgain =
            new OctavoAppearanceSyncStore(exhaustedRoot, LOCAL);
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     exhaustedAgain.load());
        assertEquals(Long.MAX_VALUE, exhaustedAgain.reviewEpoch());
        assertNull(exhaustedAgain.pending());
        assertTrue(exhaustedAgain.reviewCandidates(origin).isEmpty());
        assertArrayEquals(afterCeilingRollback,
                          readFile(exhaustedAgain.stateFileForTesting()));
    }

    @Test
    public void publishFailuresRollbackAndUncertainReplaceBlocksExport()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        byte[] before = readFile(store.stateFileForTesting());
        long epoch = store.reviewEpoch();

        store.failNextPublishForTesting();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.PUBLISH_FAILED,
            store.beginReviewEpoch(true));
        assertEquals(epoch, store.reviewEpoch());
        assertArrayEquals(before, readFile(store.stateFileForTesting()));
        assertFalse(store.lastError().isEmpty());

        store.failNextMoveAfterReplaceForTesting();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.PUBLISH_UNCERTAIN,
            store.beginReviewEpoch(true));
        assertEquals(epoch, store.reviewEpoch());
        assertEquals(
            OctavoAppearanceSyncStore.PortableExportStatus.BLOCKED,
            store.exportPortable().status);
        assertFalse(Arrays.equals(
            before, readFile(store.stateFileForTesting())));

        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     store.load());
        assertEquals(epoch + 1, store.reviewEpoch());
        assertEquals(
            OctavoAppearanceSyncStore.PortableExportStatus.EXPORTED,
            store.exportPortable().status);

        OctavoAppearance origin = store.effectiveAppearance();
        OctavoAppearance target = origin.withMargins(
            OctavoAppearance.MARGINS_FOCUSED);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageLocalPresented(target));
        OctavoAppearanceSyncStore.Pending pending = store.pending();
        byte[] staged = readFile(store.stateFileForTesting());
        store.failNextPublishForTesting();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.PUBLISH_FAILED,
            store.completePending(
                pending, target, target,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_ATOMIC_SAVE));
        assertArrayEquals(staged, readFile(store.stateFileForTesting()));
        assertNotNull(store.pending());
        assertEquals(origin, store.effectiveAppearance());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.BLOCKED,
            store.stageLocalPresented(target));
        assertArrayEquals(staged, readFile(store.stateFileForTesting()));

        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.completePending(
                pending, target, target,
                OctavoAppearanceSyncStore.O7stProof
                    .CURRENT_PROCESS_RECONCILED_AFTER_UNCERTAIN_SAVE));
        assertEquals(target, store.effectiveAppearance());
        assertNull(store.pending());
    }

    @Test
    public void pendingSurvivesRestartAndCanonicalProofIsRecoveryOnly()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        OctavoAppearance origin = store.effectiveAppearance();
        OctavoAppearance target = origin.withFontFamily(
            OctavoAppearance.FONT_FAMILY_CLEAR);
        store.mergePortableBytes(portableBytes(
            lane(REMOTE_A, 1, target)));
        OctavoAppearanceSyncStore.Candidate candidate =
            store.reviewCandidates(origin).get(0);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageRemoteApply(candidate, origin));

        OctavoAppearanceSyncStore reload = store();
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reload.load());
        OctavoAppearanceSyncStore.Pending restored = reload.pending();
        assertNotNull(restored);
        assertEquals(
            OctavoAppearanceSyncStore.PendingRecovery.ORIGIN_DURABLE,
            reload.pendingRecovery(origin));
        assertEquals(
            OctavoAppearanceSyncStore.PendingRecovery.TARGET_DURABLE,
            reload.pendingRecovery(target));
        assertEquals(
            OctavoAppearanceSyncStore.PendingRecovery.MISMATCH,
            reload.pendingRecovery(
                origin.withTheme(OctavoAppearance.THEME_OLED)));
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            reload.completePending(
                restored, target, target,
                OctavoAppearanceSyncStore.O7stProof.CANONICAL_V3_LOAD));
        assertEquals(target, reload.effectiveAppearance());
        assertEquals(
            OctavoAppearanceSyncStore.PendingRecovery.NONE,
            reload.pendingRecovery(target));
    }

    @Test
    public void allZeroForeignIdentityStagesAndReloadsWithoutAmbiguity()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        String zero = "00000000000000000000000000000000";
        OctavoAppearance origin = store.effectiveAppearance();
        OctavoAppearance target = origin.withTheme(
            OctavoAppearance.THEME_WARM_DARK);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(
                lane(zero, 7, target))));
        OctavoAppearanceSyncStore.Candidate candidate =
            store.reviewCandidates(origin).get(0);
        assertEquals(zero, candidate.deviceId);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageRemoteApply(candidate, origin));
        assertEquals(zero, store.pending().remoteDeviceId);

        OctavoAppearanceSyncStore reload = store();
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reload.load());
        assertNotNull(reload.pending());
        assertEquals(OctavoAppearanceSyncStore.PendingKind.REMOTE,
                     reload.pending().kind);
        assertEquals(zero, reload.pending().remoteDeviceId);
        assertEquals(7, reload.pending().remoteSequence);
    }

    @Test
    public void futureSlotIsByteExactBoundedUnsignedAndDurable()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        byte[] future = futureBytes(65_536, 0x80000000, 7);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.FUTURE_RETAINED,
            store.mergePortableBytes(future));
        assertArrayEquals(future, store.retainedFutureBytes());
        assertTrue(store.stateFileForTesting().length()
                   < OctavoAppearanceSyncStore.maximumFileBytesForTesting());
        assertFalse(store.lastError().isEmpty());

        byte[] afterFirst = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.UNCHANGED,
            store.mergePortableBytes(future.clone()));
        assertArrayEquals(afterFirst,
                          readFile(store.stateFileForTesting()));

        byte[] different = futureBytes(65_536, 0xffffffff, 9);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.FUTURE_CONFLICT,
            store.mergePortableBytes(different));
        assertArrayEquals(future, store.retainedFutureBytes());
        assertFalse(store.lastError().isEmpty());

        OctavoAppearanceSyncStore reload = store();
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     reload.load());
        assertArrayEquals(future, reload.retainedFutureBytes());
        assertFalse(reload.lastError().isEmpty());

        byte[] beforeOver = readFile(reload.stateFileForTesting());
        byte[] over = futureBytes(65_537, 2, 1);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.LIMIT,
            reload.mergePortableBytes(over));
        assertArrayEquals(beforeOver,
                          readFile(reload.stateFileForTesting()));
        assertArrayEquals(future, reload.retainedFutureBytes());
    }

    @Test
    public void laneCapacityOwnLaneAndSequenceExhaustionRollbackExactly()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        ArrayList<OctavoAppearancePortable.Lane> remotes =
            new ArrayList<>();
        for (int index = 0; index < 15; ++index) {
            remotes.add(lane(device(1000 + index), 1,
                             profileAppearance(index)));
        }
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            store.mergePortableBytes(portableBytes(remotes)));
        assertEquals(16, portableSnapshot(store).laneCount());
        byte[] full = readFile(store.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.LIMIT,
            store.mergePortableBytes(portableBytes(
                lane(device(9999), 1,
                     OctavoAppearance.defaults()))));
        assertArrayEquals(full, readFile(store.stateFileForTesting()));

        File reservedRoot = childRoot("reserved-local-slot");
        OctavoAppearanceSyncStore reserved =
            new OctavoAppearanceSyncStore(reservedRoot, LOCAL);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            reserved.load());
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            reserved.mergePortableBytes(portableBytes(remotes)));
        OctavoAppearance pendingTarget =
            OctavoAppearance.defaults().withTheme(
                OctavoAppearance.THEME_SEPIA);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            reserved.stageLocalPresented(pendingTarget));
        OctavoAppearanceSyncStore.Pending reservedPending =
            reserved.pending();
        assertNotNull(reservedPending);
        assertFalse(reservedPending.hasOriginLane);
        byte[] beforeReservedMerge = readFile(
            reserved.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.LIMIT,
            reserved.mergePortableBytes(portableBytes(
                lane(device(9999), 1,
                     OctavoAppearance.defaults()))));
        assertArrayEquals(beforeReservedMerge,
                          readFile(reserved.stateFileForTesting()));
        assertTrue(reservedPending.sameIdentity(reserved.pending()));
        assertEquals(15, portableSnapshot(reserved).laneCount());

        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.OWN_LANE_ADVANCE,
            store.mergePortableBytes(portableBytes(
                lane(LOCAL, 2, OctavoAppearance.defaults()))));
        assertArrayEquals(full, readFile(store.stateFileForTesting()));
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.EQUIVOCATION,
            store.mergePortableBytes(portableBytes(
                lane(LOCAL, 1,
                     OctavoAppearance.defaults().withTheme(
                         OctavoAppearance.THEME_OLED)))));
        assertArrayEquals(full, readFile(store.stateFileForTesting()));

        File exhaustionRoot = new File(testRoot, "exhaustion");
        assertTrue(exhaustionRoot.mkdirs());
        OctavoAppearanceSyncStore exhausted =
            qualifiedStore(exhaustionRoot);
        byte[] state = readFile(exhausted.stateFileForTesting());
        assertEquals(1, readInt(state, 56));
        writeLong(state, 92, Long.MAX_VALUE);
        repairChecksum(state);
        writeFile(exhausted.stateFileForTesting(), state);
        OctavoAppearanceSyncStore exhaustedReload =
            new OctavoAppearanceSyncStore(exhaustionRoot, LOCAL);
        assertEquals(OctavoAppearanceSyncStore.LoadStatus.LOADED,
                     exhaustedReload.load());
        byte[] before = readFile(exhaustedReload.stateFileForTesting());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.EXHAUSTED,
            exhaustedReload.stageLocalPresented(
                OctavoAppearance.defaults().withTheme(
                    OctavoAppearance.THEME_SEPIA)));
        assertArrayEquals(before,
                          readFile(exhaustedReload.stateFileForTesting()));
        assertNull(exhaustedReload.pending());
    }

    @Test
    public void corruptAndFuturePrivateStateAreQuarantinedOrBlocked()
        throws IOException {
        OctavoAppearanceSyncStore store = qualifiedStore();
        byte[] canonical = readFile(store.stateFileForTesting());
        canonical[canonical.length - 1] ^= 1;
        writeFile(store.stateFileForTesting(), canonical);

        OctavoAppearanceSyncStore corrupt = store();
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.CORRUPT_QUARANTINED,
            corrupt.load());
        assertTrue(corrupt.quarantineFileForTesting(1).isFile());
        assertTrue(corrupt.stateFileForTesting().isFile());
        assertNull(corrupt.localLane());
        assertFalse(corrupt.lastError().isEmpty());

        File futureRoot = new File(testRoot, "future-store");
        assertTrue(futureRoot.mkdirs());
        OctavoAppearanceSyncStore futureStore =
            new OctavoAppearanceSyncStore(futureRoot, LOCAL);
        assertTrue(futureStore.stateFileForTesting()
                       .getParentFile().mkdirs());
        byte[] privateFuture = new byte[8];
        writeInt(privateFuture, 0,
                 OctavoAppearanceSyncStore.storeMagicForTesting());
        writeInt(privateFuture, 4, 0xffffffff);
        writeFile(futureStore.stateFileForTesting(), privateFuture);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            futureStore.load());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.BLOCKED,
            futureStore.beginReviewEpoch(true));
        assertEquals(
            OctavoAppearanceSyncStore.PortableExportStatus.BLOCKED,
            futureStore.exportPortable().status);
        assertArrayEquals(privateFuture,
                          readFile(futureStore.stateFileForTesting()));
    }

    @Test
    public void privateCodecRejectsCrcValidSemanticIncoherence()
        throws IOException {
        File emptyRoot = childRoot("empty-private");
        OctavoAppearanceSyncStore empty =
            new OctavoAppearanceSyncStore(emptyRoot, LOCAL);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            empty.load());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            empty.beginReviewEpoch(true));
        byte[] emptyBytes = readFile(empty.stateFileForTesting());
        assertEquals(72, emptyBytes.length);

        byte[] attentionWithoutFuture = emptyBytes.clone();
        writeInt(attentionWithoutFuture, 52, 1);
        repairChecksum(attentionWithoutFuture);
        assertQuarantined(childRoot("attention-without-future"),
                          attentionWithoutFuture);

        byte[] futureWithoutAttention = new byte[80];
        System.arraycopy(emptyBytes, 0, futureWithoutAttention, 0, 64);
        writeInt(futureWithoutAttention, 64, 8);
        writeInt(futureWithoutAttention, 68,
                 OctavoAppearancePortable.magicForTesting());
        writeInt(futureWithoutAttention, 72, 0x80000000);
        repairChecksum(futureWithoutAttention);
        assertQuarantined(childRoot("future-without-attention"),
                          futureWithoutAttention);

        File sameProfileRoot = childRoot("same-profile-pending");
        OctavoAppearanceSyncStore sameProfile =
            qualifiedStore(sameProfileRoot);
        OctavoAppearance target = OctavoAppearance.defaults().withTheme(
            OctavoAppearance.THEME_SEPIA);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            sameProfile.stageLocalPresented(target));
        byte[] samePending = readFile(
            sameProfile.stateFileForTesting());
        int samePendingStart = pendingStart(samePending);
        System.arraycopy(samePending, samePendingStart + 16,
                         samePending, samePendingStart + 56, 32);
        repairChecksum(samePending);
        assertQuarantined(childRoot("same-profile-hostile"),
                          samePending);

        File decisionRoot = childRoot("pending-decision-base");
        OctavoAppearanceSyncStore pendingDecision =
            qualifiedStore(decisionRoot);
        OctavoAppearance remoteTarget =
            OctavoAppearance.defaults().withTheme(
                OctavoAppearance.THEME_DUSK);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            pendingDecision.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 1, remoteTarget))));
        OctavoAppearanceSyncStore.Candidate remoteCandidate =
            pendingDecision.reviewCandidates(
                OctavoAppearance.defaults()).get(0);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            pendingDecision.stageRemoteApply(
                remoteCandidate, OctavoAppearance.defaults()));
        byte[] decidedPending = readFile(
            pendingDecision.stateFileForTesting());
        byte[] zeroEpochPending = decidedPending.clone();
        writeLong(zeroEpochPending, 44, 0);
        repairChecksum(zeroEpochPending);
        assertQuarantined(childRoot("zero-epoch-remote-pending"),
                          zeroEpochPending);
        int remoteOffset = findAscii(decidedPending, REMOTE_A);
        assertTrue(remoteOffset >= 60);
        writeInt(decidedPending, remoteOffset + 72,
                 OctavoAppearanceSyncStore.Decision.ACCEPTED.wireId);
        repairChecksum(decidedPending);
        assertQuarantined(childRoot("decided-pending-hostile"),
                          decidedPending);

        File keptAtZeroRoot = childRoot("kept-at-zero-base");
        OctavoAppearanceSyncStore keptAtZero =
            qualifiedStore(keptAtZeroRoot);
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            keptAtZero.mergePortableBytes(portableBytes(
                lane(REMOTE_A, 1, remoteTarget))));
        byte[] keptAtZeroBytes = readFile(
            keptAtZero.stateFileForTesting());
        writeLong(keptAtZeroBytes, 44, 0);
        int keptAtZeroRemote = findAscii(keptAtZeroBytes, REMOTE_A);
        assertTrue(keptAtZeroRemote >= 60);
        writeInt(keptAtZeroBytes, keptAtZeroRemote + 72,
                 OctavoAppearanceSyncStore.Decision.KEPT.wireId);
        repairChecksum(keptAtZeroBytes);
        assertQuarantined(childRoot("kept-at-zero-hostile"),
                          keptAtZeroBytes);

        File fullRoot = childRoot("full-private-base");
        OctavoAppearanceSyncStore full =
            new OctavoAppearanceSyncStore(fullRoot, LOCAL);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            full.load());
        ArrayList<OctavoAppearancePortable.Lane> sixteen =
            new ArrayList<>();
        for (int index = 0; index < 16; ++index) {
            sixteen.add(lane(device(2000 + index), 1,
                             profileAppearance(index)));
        }
        assertEquals(
            OctavoAppearanceSyncStore.PortableMergeResult.MERGED,
            full.mergePortableBytes(portableBytes(sixteen)));
        byte[] fullBytes = readFile(full.stateFileForTesting());
        assertEquals(16, readInt(fullBytes, 56));

        int firstForeignDecision = 60 + 72;
        byte[] acceptedWithoutLocal = fullBytes.clone();
        writeInt(acceptedWithoutLocal, firstForeignDecision,
                 OctavoAppearanceSyncStore.Decision.ACCEPTED.wireId);
        repairChecksum(acceptedWithoutLocal);
        assertQuarantined(childRoot("accepted-without-local"),
                          acceptedWithoutLocal);

        byte[] keptWithoutLocal = fullBytes.clone();
        writeInt(keptWithoutLocal, firstForeignDecision,
                 OctavoAppearanceSyncStore.Decision.KEPT.wireId);
        repairChecksum(keptWithoutLocal);
        assertQuarantined(childRoot("kept-without-local"),
                          keptWithoutLocal);

        byte[] dismissedWithoutLocal = fullBytes.clone();
        writeLong(dismissedWithoutLocal, 44, 1);
        writeInt(dismissedWithoutLocal, firstForeignDecision,
                 OctavoAppearanceSyncStore.Decision
                     .DISMISSED_AT_EPOCH.wireId);
        writeLong(dismissedWithoutLocal,
                  firstForeignDecision + Integer.BYTES, 1);
        repairChecksum(dismissedWithoutLocal);
        assertQuarantined(childRoot("dismissed-without-local"),
                          dismissedWithoutLocal);

        int pendingFlag = 60 + 16 * 84;
        byte[] impossiblePending =
            new byte[fullBytes.length + 128];
        System.arraycopy(fullBytes, 0, impossiblePending, 0,
                         pendingFlag);
        writeInt(impossiblePending, pendingFlag, 1);
        int pending = pendingFlag + 4;
        writeInt(impossiblePending, pending,
                 OctavoAppearanceSyncStore.PendingKind.LOCAL.wireId);
        writeInt(impossiblePending, pending + 4, 0);
        writeLong(impossiblePending, pending + 8, 0);
        writeDefaultProfile(impossiblePending, pending + 16);
        writeLong(impossiblePending, pending + 48, 1);
        writeDefaultProfile(impossiblePending, pending + 56);
        byte[] zeroDevice =
            "00000000000000000000000000000000".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(zeroDevice, 0, impossiblePending,
                         pending + 88, zeroDevice.length);
        writeLong(impossiblePending, pending + 120, 0);
        writeInt(impossiblePending, pending + 128, 0);
        repairChecksum(impossiblePending);
        assertQuarantined(childRoot("full-pending-hostile"),
                          impossiblePending);
    }

    private OctavoAppearanceSyncStore store() {
        return new OctavoAppearanceSyncStore(testRoot, LOCAL);
    }

    private File childRoot(String name) {
        File result = new File(testRoot, name);
        assertTrue(result.mkdirs());
        return result;
    }

    private OctavoAppearanceSyncStore qualifiedStore()
        throws IOException {
        return qualifiedStore(testRoot);
    }

    private static OctavoAppearanceSyncStore qualifiedStore(File root)
        throws IOException {
        OctavoAppearanceSyncStore store =
            new OctavoAppearanceSyncStore(root, LOCAL);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED,
            store.load());
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.beginReviewEpoch(true));
        OctavoAppearance appearance = OctavoAppearance.defaults();
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.stageLocalPresented(appearance));
        OctavoAppearanceSyncStore.Pending pending = store.pending();
        assertNotNull(pending);
        assertEquals(
            OctavoAppearanceSyncStore.MutationResult.UPDATED,
            store.completePending(
                pending, appearance, appearance,
                OctavoAppearanceSyncStore.O7stProof.CANONICAL_V3_LOAD));
        return store;
    }

    private static OctavoAppearancePortable.Snapshot portableSnapshot(
        OctavoAppearanceSyncStore store) {
        OctavoAppearanceSyncStore.PortableExport exported =
            store.exportPortable();
        assertEquals(
            OctavoAppearanceSyncStore.PortableExportStatus.EXPORTED,
            exported.status);
        OctavoAppearancePortable.DecodeResult decoded =
            OctavoAppearancePortable.decode(exported.bytes());
        assertEquals(OctavoAppearancePortable.DecodeStatus.READY,
                     decoded.status);
        return decoded.snapshot();
    }

    private static OctavoAppearancePortable.Lane lane(
        String device,
        long sequence,
        OctavoAppearance appearance) {
        return new OctavoAppearancePortable.Lane(
            device, sequence,
            OctavoAppearancePortable.Profile.fromAppearance(appearance));
    }

    private static byte[] portableBytes(
        OctavoAppearancePortable.Lane... lanes) throws IOException {
        return portableBytes(Arrays.asList(lanes));
    }

    private static byte[] portableBytes(
        List<OctavoAppearancePortable.Lane> lanes) throws IOException {
        return OctavoAppearancePortable.encode(
            new OctavoAppearancePortable.Snapshot(lanes));
    }

    private static OctavoAppearance profileAppearance(int seed) {
        return OctavoAppearance.create(
            seed % 6,
            seed % 2,
            OctavoAppearance.fontSizesSp()[seed % 6],
            OctavoAppearance.lineSpacingsPermille()[seed % 4],
            seed % 3,
            seed % 2,
            (seed / 2) % 2,
            (seed & 1) != 0);
    }

    private static byte[] futureBytes(int length,
                                      int version,
                                      int fill) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte)fill);
        writeInt(result, 0,
                 OctavoAppearancePortable.magicForTesting());
        writeInt(result, 4, version);
        return result;
    }

    private static int pendingStart(byte[] bytes) {
        int laneCount = readInt(bytes, 56);
        int pendingFlag = 60 + laneCount * 84;
        assertEquals(1, readInt(bytes, pendingFlag));
        return pendingFlag + 4;
    }

    private static int findAscii(byte[] bytes, String value) {
        byte[] needle = value.getBytes(
            java.nio.charset.StandardCharsets.US_ASCII);
        for (int offset = 0;
             offset + needle.length <= bytes.length; ++offset) {
            boolean equal = true;
            for (int index = 0; index < needle.length; ++index) {
                if (bytes[offset + index] != needle[index]) {
                    equal = false;
                    break;
                }
            }
            if (equal) {
                return offset;
            }
        }
        return -1;
    }

    private static void writeDefaultProfile(byte[] bytes, int offset) {
        int[] values = {0, 0, 1, 1, 1, 0, 0, 0};
        for (int index = 0; index < values.length; ++index) {
            writeInt(bytes, offset + index * Integer.BYTES,
                     values[index]);
        }
    }

    private static void assertQuarantined(File root, byte[] bytes)
        throws IOException {
        OctavoAppearanceSyncStore store =
            new OctavoAppearanceSyncStore(root, LOCAL);
        assertTrue(store.stateFileForTesting()
                       .getParentFile().mkdirs());
        writeFile(store.stateFileForTesting(), bytes);
        assertEquals(
            OctavoAppearanceSyncStore.LoadStatus.CORRUPT_QUARANTINED,
            store.load());
        assertTrue(store.stateFileForTesting().isFile());
        assertTrue(store.quarantineFileForTesting(1).isFile());
        assertArrayEquals(bytes,
                          readFile(store.quarantineFileForTesting(1)));
    }

    private static String device(int value) {
        return String.format(Locale.US, "%032x", value);
    }

    private static byte[] readFile(File file) throws IOException {
        assertTrue(file.isFile());
        assertTrue(file.length() >= 0 && file.length() <= Integer.MAX_VALUE);
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
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

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int index = 7; index >= 0; --index) {
            bytes[offset + index] = (byte)value;
            value >>>= 8;
        }
    }

    private static void repairChecksum(byte[] bytes) {
        int offset = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, offset);
        writeInt(bytes, offset, (int)checksum.getValue());
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
