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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Deterministic, transport-free qualification for annotation sync policy. */
@RunWith(AndroidJUnit4.class)
public final class OctavoAnnotationSyncCoordinatorTest {
    private static final String DIGEST =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String BINDING =
        "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String OTHER_BINDING =
        "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String WRONG_DIGEST =
        "3333333333333333333333333333333333333333333333333333333333333333";
    private static final String ACTOR_A =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ACTOR_B =
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ACTOR_C =
        "cccccccccccccccccccccccccccccccc";
    private static final String NOTE_ID =
        "dddddddddddddddddddddddddddddddd";

    private File testRoot;

    @Before
    public void createIsolatedRoot() {
        Context context = ApplicationProvider.getApplicationContext();
        testRoot = new File(context.getCacheDir(),
                            "octavo-annotation-sync-coordinator");
        assertTrue(deleteTree(testRoot));
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedRoot() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void twoDevicesOfflineCreateRaceConvergesWithoutClock()
        throws IOException {
        Device first = device("race-first", ACTOR_A, session(1));
        Device second = device("race-second", ACTOR_B, session(2));
        assertTrue(first.annotations.putNoteForTesting(
            NOTE_ID, DIGEST, 2, 80, "Device A offline").succeeded());
        assertTrue(second.annotations.putNoteForTesting(
            NOTE_ID, DIGEST, 2, 80, "Device B offline").succeeded());

        FakeRemote remote = FakeRemote.missing(BINDING, true);
        OctavoAnnotationSyncCoordinator.Step firstRead =
            assertRead(first.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step secondRead =
            assertRead(second.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step firstCreate = assertWrite(
            first.coordinator.acceptRead(
                firstRead.operationToken,
                BINDING,
                remote.read(firstRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        OctavoAnnotationSyncCoordinator.Step secondCreate = assertWrite(
            second.coordinator.acceptRead(
                secondRead.operationToken,
                BINDING,
                remote.read(secondRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);

        OctavoAnnotationSyncCoordinator.Step firstComplete =
            first.coordinator.acceptWrite(
                firstCreate.operationToken,
                BINDING,
                remote.write(firstCreate));
        assertCompletion(firstComplete,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertTrue(firstComplete.pushed);
        assertFalse(firstComplete.pulled);

        OctavoAnnotationSyncCoordinator.Step secondRetry =
            second.coordinator.acceptWrite(
                secondCreate.operationToken,
                BINDING,
                remote.write(secondCreate));
        assertRead(secondRetry, BINDING);
        assertEquals(1, secondRetry.preconditionConflicts);

        OctavoAnnotationSyncCoordinator.Step review =
            second.coordinator.acceptRead(
                secondRetry.operationToken,
                BINDING,
                remote.read(secondRetry));
        assertCompletion(review,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertReview(review, 0, 0, 1, 1, 0, 0, 1);
        String approvedDigest = review.review.contentDigest;
        assertEquals(remote.digest(), approvedDigest);
        assertArrayEquals(remote.portableBytes(),
            second.syncState.snapshot().remoteSnapshot());
        int writesBeforeApproval = remote.writeAttempts;

        OctavoAnnotationSyncCoordinator.Step wrongApproval =
            second.coordinator.begin(BINDING, WRONG_DIGEST, false);
        assertCompletion(wrongApproval,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertEquals(approvedDigest, wrongApproval.review.contentDigest);
        assertEquals(writesBeforeApproval, remote.writeAttempts);
        assertTrue(second.annotations.noteBodiesForTesting(NOTE_ID)
                       .equals(Arrays.asList("Device B offline")));

        OctavoAnnotationSyncCoordinator.Step approvedRead = assertRead(
            second.coordinator.begin(BINDING, approvedDigest, false),
            BINDING);
        OctavoAnnotationSyncCoordinator.Step replace = assertWrite(
            second.coordinator.acceptRead(
                approvedRead.operationToken,
                BINDING,
                remote.read(approvedRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        assertTrue(replace.pulled);
        assertEquals(0, replace.preconditionConflicts);
        OctavoAnnotationSyncCoordinator.Step secondComplete =
            second.coordinator.acceptWrite(
                replace.operationToken,
                BINDING,
                remote.write(replace));
        assertCompletion(secondComplete,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertTrue(secondComplete.pulled);
        assertTrue(secondComplete.pushed);

        OctavoAnnotationSyncCoordinator.Step finalRead = assertRead(
            first.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step finalComplete =
            first.coordinator.acceptRead(
                finalRead.operationToken,
                BINDING,
                remote.read(finalRead));
        assertCompletion(finalComplete,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertTrue(finalComplete.pulled);
        assertFalse(finalComplete.pushed);

        byte[] converged = remote.portableBytes();
        assertArrayEquals(converged, first.annotations.exportPortableBytes());
        assertArrayEquals(converged, second.annotations.exportPortableBytes());
        assertEquals(Arrays.asList("Device A offline", "Device B offline"),
                     first.annotations.noteBodiesForTesting(NOTE_ID));
        assertEquals(first.annotations.noteBodiesForTesting(NOTE_ID),
                     second.annotations.noteBodiesForTesting(NOTE_ID));
        assertEquals(5, remote.readAttempts);
        assertEquals(3, remote.writeAttempts);
        assertEquals(1, remote.preconditionFailures);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.CLEAN,
            first.coordinator.pendingStatus(BINDING));
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.CLEAN,
            second.coordinator.pendingStatus(BINDING));
        assertArrayEquals(first.syncState.canonicalBytesForTesting(),
                          second.syncState.canonicalBytesForTesting());
        assertTrue(remote.allTokensWereUnique());
    }

    @Test
    public void readMergeAndWriteFailuresAreExactAndRestartable()
        throws IOException {
        Device device = device("boundaries", ACTOR_A, session(10));
        assertTrue(device.annotations.putNoteForTesting(
            NOTE_ID, DIGEST, 4, 120, "Local durable edit").succeeded());
        byte[] localBefore = device.annotations.canonicalBytesForTesting();
        byte[] localPortableBefore =
            device.annotations.exportPortableBytes();

        OctavoAnnotationStore remoteAuthor = annotationStore(
            "boundaries-remote", ACTOR_B);
        assertTrue(remoteAuthor.putNoteForTesting(
            NOTE_ID, DIGEST, 4, 120, "Remote durable edit").succeeded());
        FakeRemote remote = FakeRemote.found(
            BINDING, remoteAuthor.exportPortableBytes(), true);
        String originalRemoteDigest = remote.digest();

        OctavoAnnotationSyncCoordinator.Step failedRead = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step readFailure =
            device.coordinator.acceptRead(
                failedRead.operationToken,
                BINDING,
                remote.readFailure(
                    failedRead,
                    OctavoAnnotationSyncCoordinator.ProviderFailure.TRANSIENT));
        assertCompletion(readFailure,
            OctavoAnnotationSyncCoordinator.CompletionCode.TRANSIENT);
        assertArrayEquals(localBefore,
                          device.annotations.canonicalBytesForTesting());
        assertEquals(originalRemoteDigest, remote.digest());
        assertEquals(0, remote.writeAttempts);
        assertEquals(OctavoAnnotationSyncStore.Attention.TRANSIENT,
                     device.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            device.coordinator.pendingStatus(BINDING));

        OctavoAnnotationSyncCoordinator.Step reviewRead = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step review =
            device.coordinator.acceptRead(
                reviewRead.operationToken,
                BINDING,
                remote.read(reviewRead));
        assertCompletion(review,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        String approvedRemoteDigest = review.review.contentDigest;
        assertEquals(originalRemoteDigest, approvedRemoteDigest);
        assertArrayEquals(remote.portableBytes(),
            device.syncState.snapshot().remoteSnapshot());
        assertEquals(OctavoAnnotationSyncStore.Attention.NONE,
                     device.syncState.snapshot().attention);

        device.annotations.failNextPublishForTesting();
        OctavoAnnotationSyncCoordinator.Step localFailure =
            device.coordinator.begin(
                BINDING, approvedRemoteDigest, false);
        assertCompletion(localFailure,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .LOCAL_ANNOTATION_PUBLISH_FAILED);
        assertArrayEquals(localBefore,
                          device.annotations.canonicalBytesForTesting());
        assertArrayEquals(localPortableBefore,
                          device.annotations.exportPortableBytes());
        assertArrayEquals(remote.portableBytes(),
                          device.syncState.snapshot().remoteSnapshot());
        assertEquals(
            OctavoAnnotationSyncStore.Attention
                .LOCAL_ANNOTATION_PUBLISH_FAILED,
            device.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            device.coordinator.pendingStatus(BINDING));
        assertEquals(originalRemoteDigest, remote.digest());
        assertEquals(0, remote.writeAttempts);

        device = restart(device, ACTOR_C, session(11));
        OctavoAnnotationSyncCoordinator.Step retryRead = assertRead(
            device.coordinator.begin(
                BINDING, approvedRemoteDigest, false),
            BINDING);
        assertEquals(Arrays.asList(
                         "Local durable edit", "Remote durable edit"),
                     device.annotations.noteBodiesForTesting(NOTE_ID));
        OctavoAnnotationSyncCoordinator.Step outgoing = assertWrite(
            device.coordinator.acceptRead(
                retryRead.operationToken,
                BINDING,
                remote.read(retryRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        byte[] joined = outgoing.portableBytes();
        OctavoAnnotationSyncCoordinator.Step definiteFailure =
            device.coordinator.acceptWrite(
                outgoing.operationToken,
                BINDING,
                remote.definiteWriteFailure(
                    outgoing,
                    OctavoAnnotationSyncCoordinator.ProviderFailure.TRANSIENT));
        assertCompletion(definiteFailure,
            OctavoAnnotationSyncCoordinator.CompletionCode.TRANSIENT);
        assertArrayEquals(joined,
                          device.annotations.exportPortableBytes());
        assertEquals(originalRemoteDigest, remote.digest());
        assertEquals(OctavoAnnotationSyncStore.Phase.WRITE_IN_FLIGHT,
                     device.syncState.snapshot().phase);
        assertEquals(digest(joined),
                     device.syncState.snapshot().inFlightDigest);

        device = restart(device, ACTOR_C, session(12));
        OctavoAnnotationSyncCoordinator.Step reread = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step repeatedReview =
            device.coordinator.acceptRead(
                reread.operationToken,
                BINDING,
                remote.read(reread));
        assertCompletion(repeatedReview,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertEquals(approvedRemoteDigest,
                     repeatedReview.review.contentDigest);
        OctavoAnnotationSyncCoordinator.Step approvedReread = assertRead(
            device.coordinator.begin(
                BINDING, approvedRemoteDigest, false),
            BINDING);
        OctavoAnnotationSyncCoordinator.Step uncertainWrite = assertWrite(
            device.coordinator.acceptRead(
                approvedReread.operationToken,
                BINDING,
                remote.read(approvedReread)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        assertArrayEquals(joined, uncertainWrite.portableBytes());
        OctavoAnnotationSyncCoordinator.Step unknown =
            device.coordinator.acceptWrite(
                uncertainWrite.operationToken,
                BINDING,
                remote.commitWithOutcomeUnknown(uncertainWrite));
        assertCompletion(unknown,
            OctavoAnnotationSyncCoordinator.CompletionCode.OUTCOME_UNKNOWN);
        assertArrayEquals(joined, remote.portableBytes());
        assertEquals(digest(joined),
                     device.syncState.snapshot().inFlightDigest);
        int writesAfterUnknown = remote.writeAttempts;

        device = restart(device, ACTOR_C, session(13));
        OctavoAnnotationSyncCoordinator.Step reconcileRead = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step reconciled =
            device.coordinator.acceptRead(
                reconcileRead.operationToken,
                BINDING,
                remote.read(reconcileRead));
        assertCompletion(reconciled,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertEquals(writesAfterUnknown, remote.writeAttempts);
        assertArrayEquals(joined,
                          device.annotations.exportPortableBytes());
        assertArrayEquals(joined, remote.portableBytes());
        assertEquals(OctavoAnnotationSyncStore.Phase.IDLE,
                     device.syncState.snapshot().phase);
        assertEquals("", device.syncState.snapshot().inFlightDigest);
        assertEquals(digest(joined),
                     device.syncState.snapshot().convergedDigest);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.CLEAN,
            device.coordinator.pendingStatus(BINDING));
    }

    @Test
    public void conditionalConflictsStopAtExactBoundAndResume()
        throws IOException {
        Device device = device("retry-bound", ACTOR_A, session(20));
        FakeRemote remote = FakeRemote.missing(BINDING, true);
        OctavoAnnotationSyncCoordinator.Step bootstrapRead = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step bootstrapCreate = assertWrite(
            device.coordinator.acceptRead(
                bootstrapRead.operationToken,
                BINDING,
                remote.read(bootstrapRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        OctavoAnnotationSyncCoordinator.Step bootstrapComplete =
            device.coordinator.acceptWrite(
                bootstrapCreate.operationToken,
                BINDING,
                remote.write(bootstrapCreate));
        assertCompletion(bootstrapComplete,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        byte[] unchangedRemote = remote.portableBytes();

        assertTrue(device.annotations.putBookmarkForTesting(
            DIGEST, 7, 700, "Offline bookmark", "Bounded retry")
                       .succeeded());
        byte[] local = device.annotations.exportPortableBytes();
        int readsBeforeConflicts = remote.readAttempts;
        int writesBeforeConflicts = remote.writeAttempts;

        OctavoAnnotationSyncCoordinator.Step next = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        for (int conflict = 1;
             conflict
                 <= OctavoAnnotationSyncCoordinator
                     .MAX_PRECONDITION_CONFLICTS;
             ++conflict) {
            OctavoAnnotationSyncCoordinator.Step write = assertWrite(
                device.coordinator.acceptRead(
                    next.operationToken,
                    BINDING,
                    remote.read(next)),
                OctavoAnnotationSyncCoordinator.WriteMode
                    .REPLACE_IF_REVISION,
                BINDING);
            next = device.coordinator.acceptWrite(
                write.operationToken,
                BINDING,
                remote.preconditionFailed(write));
            assertEquals(conflict, next.preconditionConflicts);
            if (conflict
                < OctavoAnnotationSyncCoordinator
                    .MAX_PRECONDITION_CONFLICTS) {
                assertRead(next, BINDING);
            } else {
                assertCompletion(next,
                    OctavoAnnotationSyncCoordinator.CompletionCode
                        .REVISION_RETRY_LIMIT);
            }
        }
        assertEquals(
            readsBeforeConflicts
                + OctavoAnnotationSyncCoordinator
                    .MAX_PRECONDITION_CONFLICTS,
            remote.readAttempts);
        assertEquals(
            writesBeforeConflicts
                + OctavoAnnotationSyncCoordinator
                    .MAX_PRECONDITION_CONFLICTS,
            remote.writeAttempts);
        assertEquals(
            OctavoAnnotationSyncCoordinator.MAX_PRECONDITION_CONFLICTS,
            remote.preconditionFailures);
        assertArrayEquals(unchangedRemote, remote.portableBytes());
        assertEquals(OctavoAnnotationSyncStore.Phase.WRITE_IN_FLIGHT,
                     device.syncState.snapshot().phase);
        assertEquals(digest(local),
                     device.syncState.snapshot().inFlightDigest);

        device = restart(device, ACTOR_B, session(21));
        OctavoAnnotationSyncCoordinator.Step manualRead = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step manualWrite = assertWrite(
            device.coordinator.acceptRead(
                manualRead.operationToken,
                BINDING,
                remote.read(manualRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        OctavoAnnotationSyncCoordinator.Step complete =
            device.coordinator.acceptWrite(
                manualWrite.operationToken,
                BINDING,
                remote.write(manualWrite));
        assertCompletion(complete,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertEquals(0, complete.preconditionConflicts);
        assertArrayEquals(local, remote.portableBytes());
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.CLEAN,
            device.coordinator.pendingStatus(BINDING));
    }

    @Test
    public void invalidFutureAndBlockedLocalNeverOverwriteRemote()
        throws IOException {
        assertRejectedRemote(
            "remote-invalid",
            new byte[] {1, 2, 3, 4, 5, 6, 7},
            OctavoAnnotationSyncCoordinator.CompletionCode.REMOTE_INVALID);

        byte[] future = new byte[2 * Integer.BYTES];
        putInt(future, 0, OctavoAnnotationPortableWire.MAGIC);
        putInt(future, Integer.BYTES,
               OctavoAnnotationPortableWire.VERSION + 1);
        assertRejectedRemote(
            "remote-future",
            future,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .REMOTE_FUTURE_VERSION);

        Device cleanAttention =
            device("attention-clean", ACTOR_A, session(35));
        assertTrue(cleanAttention.annotations.putBookmarkForTesting(
            DIGEST, 2, 22, "Synced", "Clean before remote error")
                       .succeeded());
        FakeRemote cleanRemote = FakeRemote.missing(BINDING, true);
        OctavoAnnotationSyncCoordinator.Step cleanRead = assertRead(
            cleanAttention.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step cleanCreate = assertWrite(
            cleanAttention.coordinator.acceptRead(
                cleanRead.operationToken,
                BINDING,
                cleanRemote.read(cleanRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        assertCompletion(cleanAttention.coordinator.acceptWrite(
                cleanCreate.operationToken,
                BINDING,
                cleanRemote.write(cleanCreate)),
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertEquals(OctavoAnnotationSyncCoordinator.PendingStatus.CLEAN,
                     cleanAttention.coordinator.pendingStatus(BINDING));
        OctavoAnnotationSyncCoordinator.Step invalidRead = assertRead(
            cleanAttention.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step invalidCompletion =
            cleanAttention.coordinator.acceptRead(
                invalidRead.operationToken,
                BINDING,
                cleanRemote.foundResponse(
                    invalidRead,
                    ascii("clean-handle"),
                    ascii("clean-revision"),
                    new byte[] {9, 8, 7, 6, 5, 4, 3}));
        assertCompletion(invalidCompletion,
            OctavoAnnotationSyncCoordinator.CompletionCode.REMOTE_INVALID);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            cleanAttention.coordinator.pendingStatus(BINDING));
        cleanAttention = restart(
            cleanAttention, ACTOR_B, session(36));
        assertEquals(OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
                     cleanAttention.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            cleanAttention.coordinator.pendingStatus(BINDING));

        OctavoAnnotationStore envelopeAuthor = annotationStore(
            "remote-envelope-author", ACTOR_B);
        assertTrue(envelopeAuthor.putBookmarkForTesting(
            DIGEST, 3, 30, "Envelope", "Valid body").succeeded());
        byte[] validEnvelopeBody =
            envelopeAuthor.exportPortableBytes();
        assertRejectedProviderEnvelope(
            "remote-handle-overbound",
            new byte[
                OctavoAnnotationSyncCoordinator.MAX_PROVIDER_VALUE_BYTES
                    + 1],
            ascii("revision"),
            validEnvelopeBody,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .INVALID_RESPONSE);
        assertRejectedProviderEnvelope(
            "remote-revision-overbound",
            ascii("handle"),
            new byte[
                OctavoAnnotationSyncCoordinator.MAX_PROVIDER_VALUE_BYTES
                    + 1],
            validEnvelopeBody,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .INVALID_RESPONSE);
        byte[] overboundBody = new byte[
            OctavoAnnotationStore.maximumPortableFileBytesForTesting()
                + 1];
        assertRejectedProviderEnvelope(
            "remote-body-overbound",
            ascii("handle"),
            ascii("revision"),
            overboundBody,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .REMOTE_INPUT_LIMIT);

        OctavoAnnotationStore replayAuthorA = annotationStore(
            "remote-version-a", ACTOR_B);
        assertTrue(replayAuthorA.putBookmarkForTesting(
            DIGEST, 4, 40, "Version A", "First body").succeeded());
        OctavoAnnotationStore replayAuthorB = annotationStore(
            "remote-version-b", ACTOR_C);
        assertTrue(replayAuthorB.putBookmarkForTesting(
            DIGEST, 5, 50, "Version B", "Second body").succeeded());
        OctavoAnnotationStore replayAuthorC = annotationStore(
            "remote-version-c", actor(330));
        assertTrue(replayAuthorC.putBookmarkForTesting(
            DIGEST, 6, 60, "Version C", "Contradictory body")
                       .succeeded());
        byte[] bodyA = replayAuthorA.exportPortableBytes();
        byte[] bodyB = replayAuthorB.exportPortableBytes();
        byte[] bodyC = replayAuthorC.exportPortableBytes();
        Device replay = device(
            "remote-version-replay", ACTOR_A, session(33));
        FakeRemote bootstrap = FakeRemote.found(BINDING, bodyA, true);
        OctavoAnnotationSyncCoordinator.Step bootstrapRead = assertRead(
            replay.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step bootstrapReview =
            replay.coordinator.acceptRead(
                bootstrapRead.operationToken,
                BINDING,
                bootstrap.read(bootstrapRead));
        assertCompletion(bootstrapReview,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertReview(bootstrapReview, 1, 0, 0, 1, 0, 0, 0);
        OctavoAnnotationSyncCoordinator.Step approvedBootstrapRead =
            assertRead(replay.coordinator.begin(
                BINDING, digest(bodyA), false), BINDING);
        assertCompletion(replay.coordinator.acceptRead(
                approvedBootstrapRead.operationToken,
                BINDING,
                bootstrap.read(approvedBootstrapRead)),
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertEquals(0, bootstrap.writeAttempts);
        assertTrue(replay.annotations.putNoteForTesting(
            NOTE_ID, DIGEST, 7, 70, "Unsynced local body").succeeded());

        FakeRemote replaySequence = FakeRemote.missing(BINDING, true);
        byte[] sameHandle = ascii("same-provider-handle");
        byte[] revisionOne = ascii("revision-one");
        byte[] revisionTwo = ascii("revision-two");
        OctavoAnnotationSyncCoordinator.Step sequenceReadA = assertRead(
            replay.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step sequenceWriteA = assertWrite(
            replay.coordinator.acceptRead(
                sequenceReadA.operationToken,
                BINDING,
                replaySequence.foundResponse(
                    sequenceReadA, sameHandle, revisionOne, bodyA)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        OctavoAnnotationSyncCoordinator.Step sequenceReadB = assertRead(
            replay.coordinator.acceptWrite(
                sequenceWriteA.operationToken,
                BINDING,
                replaySequence.preconditionFailed(sequenceWriteA)),
            BINDING);
        OctavoAnnotationSyncCoordinator.Step sequenceWriteB = assertWrite(
            replay.coordinator.acceptRead(
                sequenceReadB.operationToken,
                BINDING,
                replaySequence.foundResponse(
                    sequenceReadB, sameHandle, revisionTwo, bodyB)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        OctavoAnnotationSyncCoordinator.Step sequenceReadC = assertRead(
            replay.coordinator.acceptWrite(
                sequenceWriteB.operationToken,
                BINDING,
                replaySequence.preconditionFailed(sequenceWriteB)),
            BINDING);
        byte[] localBeforeContradiction =
            replay.annotations.canonicalBytesForTesting();
        byte[] portableBeforeContradiction =
            replay.annotations.exportPortableBytes();
        OctavoAnnotationSyncStore.Snapshot beforeContradiction =
            replay.syncState.snapshot();
        int writesBeforeContradiction = replaySequence.writeAttempts;
        assertCompletion(replay.coordinator.acceptRead(
                sequenceReadC.operationToken,
                BINDING,
                replaySequence.foundResponse(
                    sequenceReadC, sameHandle, revisionOne, bodyC)),
            OctavoAnnotationSyncCoordinator.CompletionCode.INVALID_RESPONSE);
        assertArrayEquals(localBeforeContradiction,
                          replay.annotations.canonicalBytesForTesting());
        assertArrayEquals(portableBeforeContradiction,
                          replay.annotations.exportPortableBytes());
        OctavoAnnotationSyncStore.Snapshot afterContradiction =
            replay.syncState.snapshot();
        assertEquals(beforeContradiction.phase,
                     afterContradiction.phase);
        assertEquals(beforeContradiction.convergedDigest,
                     afterContradiction.convergedDigest);
        assertEquals(beforeContradiction.inFlightDigest,
                     afterContradiction.inFlightDigest);
        assertEquals(beforeContradiction.remotePreviouslyPresent,
                     afterContradiction.remotePreviouslyPresent);
        assertArrayEquals(beforeContradiction.remoteSnapshot(),
                          afterContradiction.remoteSnapshot());
        assertEquals(OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                     afterContradiction.attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            replay.coordinator.pendingStatus(BINDING));
        assertEquals(writesBeforeContradiction,
                     replaySequence.writeAttempts);
        assertEquals(2, replaySequence.preconditionFailures);
        assertEquals(3, replaySequence.readAttempts);

        Device duplicateDevice =
            device("remote-duplicates", ACTOR_A, session(30));
        assertTrue(duplicateDevice.annotations.putBookmarkForTesting(
            DIGEST, 7, 70, "Local", "Duplicate response no-op")
                       .succeeded());
        byte[] duplicateLocal =
            duplicateDevice.annotations.canonicalBytesForTesting();
        FakeRemote duplicateRemote = FakeRemote.missing(BINDING, true);
        OctavoAnnotationSyncCoordinator.Step duplicateRead = assertRead(
            duplicateDevice.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step duplicates =
            duplicateDevice.coordinator.acceptRead(
                duplicateRead.operationToken,
                BINDING,
                duplicateRemote.duplicates(duplicateRead));
        assertCompletion(duplicates,
            OctavoAnnotationSyncCoordinator.CompletionCode.REMOTE_DUPLICATES);
        assertArrayEquals(duplicateLocal,
            duplicateDevice.annotations.canonicalBytesForTesting());
        assertEquals(0, duplicateRemote.writeAttempts);
        assertEquals(OctavoAnnotationSyncStore.Attention.REMOTE_DUPLICATES,
                     duplicateDevice.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            duplicateDevice.coordinator.pendingStatus(BINDING));
        duplicateDevice = restart(
            duplicateDevice, ACTOR_B, session(34));
        assertEquals(OctavoAnnotationSyncStore.Attention.REMOTE_DUPLICATES,
                     duplicateDevice.syncState.snapshot().attention);

        File blockedFiles = directory("blocked-local");
        OctavoAnnotationStore current =
            new OctavoAnnotationStore(blockedFiles, ACTOR_A);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     current.load());
        assertTrue(current.putBookmarkForTesting(
            DIGEST, 1, 1, "Future local", "Must remain exact")
                       .succeeded());
        byte[] futureLocal = readFile(current.stateFileForTesting());
        putInt(futureLocal, Integer.BYTES,
               OctavoAnnotationStore.currentStoreVersionForTesting() + 1);
        updateChecksum(futureLocal);
        writeFile(current.stateFileForTesting(), futureLocal);
        OctavoAnnotationStore blocked =
            new OctavoAnnotationStore(blockedFiles, ACTOR_B);
        assertEquals(
            OctavoAnnotationStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            blocked.load());
        OctavoAnnotationSyncStore blockedSync =
            new OctavoAnnotationSyncStore(blockedFiles);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.MISSING,
                     blockedSync.load());
        OctavoAnnotationSyncCoordinator blockedCoordinator =
            new OctavoAnnotationSyncCoordinator(
                blocked, blockedSync, session(31));
        FakeRemote untouched = FakeRemote.missing(BINDING, true);
        assertCompletion(blockedCoordinator.begin(BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.LOCAL_BLOCKED);
        assertArrayEquals(futureLocal,
                          readFile(blocked.stateFileForTesting()));
        assertEquals(0, untouched.readAttempts);
        assertEquals(0, untouched.writeAttempts);

        File neverLoadedFiles = directory("never-loaded-local");
        OctavoAnnotationStore neverLoaded =
            new OctavoAnnotationStore(neverLoadedFiles, ACTOR_A);
        OctavoAnnotationSyncStore loadedSync =
            new OctavoAnnotationSyncStore(neverLoadedFiles);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.MISSING,
                     loadedSync.load());
        OctavoAnnotationSyncCoordinator neverLoadedCoordinator =
            new OctavoAnnotationSyncCoordinator(
                neverLoaded, loadedSync, session(32));
        assertCompletion(neverLoadedCoordinator.begin(BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.LOCAL_NOT_LOADED);

    }

    @Test
    public void limitedJoinRetainsExactRemoteAcrossRestartAndRetriesAfterResolution()
        throws IOException {
        String recordId = fixedId(807);
        OctavoAnnotationPortableWire.Record fiveHeads =
            new OctavoAnnotationPortableWire.Record(
                recordId,
                OctavoAnnotationPortableWire.Kind.NOTE,
                DIGEST);
        for (int index = 0; index < 5; ++index) {
            fiveHeads.add(OctavoAnnotationPortableWire.put(
                actor(300 + index), 1, 1, 21, 21,
                0, 0, "", "", "", "Left " + index));
        }
        OctavoAnnotationPortableWire.Record fourHeads =
            new OctavoAnnotationPortableWire.Record(
                recordId,
                OctavoAnnotationPortableWire.Kind.NOTE,
                DIGEST);
        for (int index = 0; index < 4; ++index) {
            fourHeads.add(OctavoAnnotationPortableWire.put(
                actor(305 + index), 1, 1, 21, 21,
                0, 0, "", "", "", "Right " + index));
        }
        byte[] fiveHeadBytes = OctavoAnnotationPortableWire.encode(
            Arrays.asList(fiveHeads));
        byte[] fourHeadBytes = OctavoAnnotationPortableWire.encode(
            Arrays.asList(fourHeads));

        Device device = device("limited-join", ACTOR_A, session(40));
        assertTrue(device.annotations.mergePortableBytes(fiveHeadBytes)
                       .succeeded());
        byte[] localBefore =
            device.annotations.canonicalBytesForTesting();
        FakeRemote remote = FakeRemote.found(BINDING, fourHeadBytes, true);
        OctavoAnnotationSyncCoordinator.Step read = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step review =
            device.coordinator.acceptRead(
                read.operationToken,
                BINDING,
                remote.read(read));
        assertCompletion(review,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertEquals(
            OctavoAnnotationStore.PortableInspectionStatus.JOIN_LIMIT,
            review.review.status);
        String approvedDigest = review.review.contentDigest;
        assertEquals(digest(fourHeadBytes), approvedDigest);
        assertArrayEquals(localBefore,
                          device.annotations.canonicalBytesForTesting());
        assertEquals(0, remote.writeAttempts);

        OctavoAnnotationSyncCoordinator.Step limited =
            device.coordinator.begin(BINDING, approvedDigest, false);
        assertCompletion(limited,
            OctavoAnnotationSyncCoordinator.CompletionCode.LOCAL_JOIN_LIMIT);
        OctavoAnnotationSyncStore.Snapshot pending =
            device.syncState.snapshot();
        assertEquals(OctavoAnnotationSyncStore.Phase.REMOTE_SNAPSHOT,
                     pending.phase);
        assertArrayEquals(fourHeadBytes, pending.remoteSnapshot());
        byte[] escapedPending = pending.remoteSnapshot();
        escapedPending[0] ^= 1;
        assertArrayEquals(fourHeadBytes, pending.remoteSnapshot());
        byte[] manifestBeforeRestart =
            device.syncState.canonicalBytesForTesting();

        device = restart(device, ACTOR_B, session(41));
        assertArrayEquals(manifestBeforeRestart,
                          device.syncState.canonicalBytesForTesting());
        OctavoAnnotationSyncCoordinator.Step stillLimited =
            device.coordinator.begin(BINDING, approvedDigest, false);
        assertCompletion(stillLimited,
            OctavoAnnotationSyncCoordinator.CompletionCode.LOCAL_JOIN_LIMIT);
        assertEquals(1, remote.readAttempts);
        assertEquals(0, remote.writeAttempts);

        TreeMap<String, Long> observesFive = new TreeMap<>();
        for (int index = 0; index < 5; ++index) {
            observesFive.put(actor(300 + index), 1L);
        }
        OctavoAnnotationPortableWire.Record resolution =
            new OctavoAnnotationPortableWire.Record(
                recordId,
                OctavoAnnotationPortableWire.Kind.NOTE,
                DIGEST);
        resolution.add(new OctavoAnnotationPortableWire.Head(
            actor(309),
            1,
            OctavoAnnotationPortableWire.Operation.PUT,
            observesFive,
            1,
            21,
            21,
            0,
            0,
            "",
            "",
            "",
            "Resolved left"));
        byte[] resolutionBytes = OctavoAnnotationPortableWire.encode(
            Arrays.asList(resolution));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     device.annotations.mergePortableBytes(resolutionBytes));

        OctavoAnnotationSyncCoordinator.Step retryRead = assertRead(
            device.coordinator.begin(BINDING, approvedDigest, false),
            BINDING);
        assertEquals(1, remote.readAttempts);
        OctavoAnnotationSyncCoordinator.Step write = assertWrite(
            device.coordinator.acceptRead(
                retryRead.operationToken,
                BINDING,
                remote.read(retryRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.REPLACE_IF_REVISION,
            BINDING);
        OctavoAnnotationSyncCoordinator.Step complete =
            device.coordinator.acceptWrite(
                write.operationToken,
                BINDING,
                remote.write(write));
        assertCompletion(complete,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);

        OctavoAnnotationStore alternate = annotationStore(
            "limited-join-alternate", ACTOR_C);
        assertTrue(alternate.mergePortableBytes(resolutionBytes).succeeded());
        assertTrue(alternate.mergePortableBytes(fourHeadBytes).succeeded());
        assertArrayEquals(alternate.exportPortableBytes(),
                          device.annotations.exportPortableBytes());
        assertArrayEquals(alternate.exportPortableBytes(),
                          remote.portableBytes());
        assertEquals(OctavoAnnotationSyncStore.Phase.IDLE,
                     device.syncState.snapshot().phase);
        assertNull(device.syncState.snapshot().remoteSnapshot());
        assertEquals(1, remote.writeAttempts);
    }

    @Test
    public void exactMaximumRemoteSnapshotSyncsWithinHeapBound()
        throws IOException {
        byte[] exact = OctavoAnnotationPortableStateTest
            .exactLimitPortableBytesForTesting();
        assertEquals(
            OctavoAnnotationStore.maximumPortableFileBytesForTesting(),
            exact.length);
        String exactDigest = digest(exact);
        FakeRemote remote = FakeRemote.found(BINDING, exact, false);
        exact = null;

        Device device = device("exact-maximum", ACTOR_A, session(49));
        OctavoAnnotationSyncCoordinator.Step read = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step review =
            device.coordinator.acceptRead(
                read.operationToken, BINDING, remote.read(read));
        assertCompletion(review,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertEquals(exactDigest, review.review.contentDigest);
        assertEquals(OctavoAnnotationSyncStore.Phase.REMOTE_SNAPSHOT,
                     device.syncState.snapshot().phase);

        device = restartWithMissingAnnotations(
            device, ACTOR_B, session(64));
        OctavoAnnotationSyncCoordinator.Step approvedRead = assertRead(
            device.coordinator.begin(BINDING, exactDigest, false),
            BINDING);
        OctavoAnnotationSyncCoordinator.Step converged =
            device.coordinator.acceptRead(
                approvedRead.operationToken,
                BINDING,
                remote.read(approvedRead));
        assertCompletion(converged,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertEquals(0, remote.writeAttempts);
        assertEquals(OctavoAnnotationStore.maximumRecordsForTesting(),
                     device.annotations.recordCountForTesting());
        assertEquals(exactDigest,
                     digest(device.annotations.exportPortableBytes()));
        assertEquals(OctavoAnnotationSyncStore.Phase.IDLE,
                     device.syncState.snapshot().phase);
        assertEquals(exactDigest,
                     device.syncState.snapshot().convergedDigest);

        remote = null;
        device = restart(device, ACTOR_C, session(65));
        assertEquals(exactDigest,
                     digest(device.annotations.exportPortableBytes()));
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.CLEAN,
            device.coordinator.pendingStatus(BINDING));
    }

    @Test
    public void manifestFailureCorruptionAndFutureVersionAreVisibleExact()
        throws IOException {
        Device publishFailure =
            device("manifest-publish", ACTOR_A, session(50));
        assertTrue(publishFailure.annotations.putBookmarkForTesting(
            DIGEST, 8, 800, "Local", "Manifest rollback").succeeded());
        OctavoAnnotationStore remoteAuthor = annotationStore(
            "manifest-publish-remote", ACTOR_B);
        assertTrue(remoteAuthor.putBookmarkForTesting(
            DIGEST, 9, 900, "Remote", "Must not be imported")
                       .succeeded());
        FakeRemote remote = FakeRemote.found(
            BINDING, remoteAuthor.exportPortableBytes(), true);
        byte[] localBefore =
            publishFailure.annotations.canonicalBytesForTesting();
        OctavoAnnotationSyncCoordinator.Step read = assertRead(
            publishFailure.coordinator.begin(BINDING), BINDING);
        byte[] manifestBefore =
            publishFailure.syncState.canonicalBytesForTesting();
        publishFailure.syncState.failNextPublishForTesting();
        OctavoAnnotationSyncCoordinator.Step failedStage =
            publishFailure.coordinator.acceptRead(
                read.operationToken,
                BINDING,
                remote.read(read));
        assertCompletion(failedStage,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .SYNC_STATE_PUBLISH_FAILED);
        assertArrayEquals(localBefore,
            publishFailure.annotations.canonicalBytesForTesting());
        assertArrayEquals(manifestBefore,
            publishFailure.syncState.canonicalBytesForTesting());
        assertEquals(0, remote.writeAttempts);
        assertFalse(publishFailure.syncState.temporaryFileForTesting()
                        .exists());

        Device attentionFailure =
            device("attention-publish", ACTOR_A, session(66));
        assertTrue(attentionFailure.annotations.putBookmarkForTesting(
            DIGEST, 12, 1200, "Local attention", "Preserve phase")
                       .succeeded());
        OctavoAnnotationStore attentionRemoteAuthor = annotationStore(
            "attention-publish-remote", ACTOR_B);
        assertTrue(attentionRemoteAuthor.putBookmarkForTesting(
            DIGEST, 13, 1300, "Remote attention", "Keep inbox")
                       .succeeded());
        FakeRemote attentionRemote = FakeRemote.found(
            BINDING, attentionRemoteAuthor.exportPortableBytes(), true);
        OctavoAnnotationSyncCoordinator.Step attentionStageRead = assertRead(
            attentionFailure.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step attentionReview =
            attentionFailure.coordinator.acceptRead(
                attentionStageRead.operationToken,
                BINDING,
                attentionRemote.read(attentionStageRead));
        assertCompletion(attentionReview,
            OctavoAnnotationSyncCoordinator.CompletionCode.REVIEW_REQUIRED);
        assertEquals(OctavoAnnotationSyncStore.UpdateResult.PUBLISHED,
                     attentionFailure.syncState.recordAttention(
                         BINDING,
                         OctavoAnnotationSyncStore.Attention.REMOTE_INVALID));
        OctavoAnnotationSyncCoordinator.Step attentionRead = assertRead(
            attentionFailure.coordinator.begin(
                BINDING, attentionReview.review.contentDigest, false),
            BINDING);
        byte[] attentionManifestBefore =
            attentionFailure.syncState.canonicalBytesForTesting();
        byte[] attentionLocalBefore =
            attentionFailure.annotations.canonicalBytesForTesting();
        OctavoAnnotationSyncStore.Snapshot attentionBefore =
            attentionFailure.syncState.snapshot();
        attentionFailure.syncState.failNextPublishForTesting();
        OctavoAnnotationSyncCoordinator.Step attentionPublishFailed =
            attentionFailure.coordinator.acceptRead(
                attentionRead.operationToken,
                BINDING,
                attentionRemote.readFailure(
                    attentionRead,
                    OctavoAnnotationSyncCoordinator.ProviderFailure
                        .TRANSIENT));
        assertCompletion(attentionPublishFailed,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .SYNC_STATE_PUBLISH_FAILED);
        assertArrayEquals(attentionManifestBefore,
            attentionFailure.syncState.canonicalBytesForTesting());
        assertArrayEquals(attentionLocalBefore,
            attentionFailure.annotations.canonicalBytesForTesting());
        OctavoAnnotationSyncStore.Snapshot attentionAfter =
            attentionFailure.syncState.snapshot();
        assertEquals(attentionBefore.bindingFingerprint,
                     attentionAfter.bindingFingerprint);
        assertEquals(attentionBefore.convergedDigest,
                     attentionAfter.convergedDigest);
        assertEquals(attentionBefore.phase, attentionAfter.phase);
        assertEquals(OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
                     attentionAfter.attention);
        assertEquals(attentionBefore.remotePreviouslyPresent,
                     attentionAfter.remotePreviouslyPresent);
        assertEquals(attentionBefore.inFlightDigest,
                     attentionAfter.inFlightDigest);
        assertArrayEquals(attentionBefore.remoteSnapshot(),
                          attentionAfter.remoteSnapshot());
        assertEquals(0, attentionRemote.writeAttempts);
        assertFalse(attentionFailure.syncState.temporaryFileForTesting()
                        .exists());

        Device committedManifest =
            device("manifest-after-commit", ACTOR_A, session(57));
        assertTrue(committedManifest.annotations.putBookmarkForTesting(
            DIGEST, 10, 1000, "Pending create", "Committed remotely")
                       .succeeded());
        byte[] committedPortable =
            committedManifest.annotations.exportPortableBytes();
        FakeRemote committedRemote = FakeRemote.missing(BINDING, true);
        OctavoAnnotationSyncCoordinator.Step missingRead = assertRead(
            committedManifest.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step create = assertWrite(
            committedManifest.coordinator.acceptRead(
                missingRead.operationToken,
                BINDING,
                committedRemote.read(missingRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        OctavoAnnotationSyncCoordinator.WriteResult committedResult =
            committedRemote.write(create);
        committedManifest.syncState.failNextPublishForTesting();
        OctavoAnnotationSyncCoordinator.Step acknowledgementFailure =
            committedManifest.coordinator.acceptWrite(
                create.operationToken, BINDING, committedResult);
        assertCompletion(acknowledgementFailure,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .SYNC_STATE_PUBLISH_FAILED);
        assertArrayEquals(committedPortable,
                          committedRemote.portableBytes());
        assertEquals(OctavoAnnotationSyncStore.Phase.WRITE_IN_FLIGHT,
                     committedManifest.syncState.snapshot().phase);
        assertEquals(digest(committedPortable),
                     committedManifest.syncState.snapshot().inFlightDigest);
        int committedWrites = committedRemote.writeAttempts;

        committedManifest = restart(
            committedManifest, ACTOR_B, session(58));
        OctavoAnnotationSyncCoordinator.Step committedRead = assertRead(
            committedManifest.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step committedReconciled =
            committedManifest.coordinator.acceptRead(
                committedRead.operationToken,
                BINDING,
                committedRemote.read(committedRead));
        assertCompletion(committedReconciled,
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertEquals(committedWrites, committedRemote.writeAttempts);
        assertEquals(OctavoAnnotationSyncStore.Phase.IDLE,
                     committedManifest.syncState.snapshot().phase);
        assertEquals(digest(committedPortable),
                     committedManifest.syncState.snapshot().convergedDigest);

        Device missingReview =
            device("manifest-missing-review", ACTOR_A, session(61));
        assertTrue(missingReview.annotations.putBookmarkForTesting(
            DIGEST, 11, 1100, "Recreate only after review", "Durable")
                       .succeeded());
        byte[] recreationBytes =
            missingReview.annotations.exportPortableBytes();
        FakeRemote deletedRemote = FakeRemote.missing(BINDING, true);
        OctavoAnnotationSyncCoordinator.Step initialRead = assertRead(
            missingReview.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step initialCreate = assertWrite(
            missingReview.coordinator.acceptRead(
                initialRead.operationToken,
                BINDING,
                deletedRemote.read(initialRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        assertCompletion(missingReview.coordinator.acceptWrite(
                initialCreate.operationToken,
                BINDING,
                deletedRemote.write(initialCreate)),
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        deletedRemote.deleteExternally();
        int writesBeforeMissingReview = deletedRemote.writeAttempts;

        OctavoAnnotationSyncCoordinator.Step deletionRead = assertRead(
            missingReview.coordinator.begin(BINDING, "", true), BINDING);
        OctavoAnnotationSyncCoordinator.Step deletionReview =
            missingReview.coordinator.acceptRead(
                deletionRead.operationToken,
                BINDING,
                deletedRemote.read(deletionRead));
        assertCompletion(deletionReview,
            OctavoAnnotationSyncCoordinator.CompletionCode
                .REMOTE_DELETION_REVIEW_REQUIRED);
        assertEquals(OctavoAnnotationSyncStore.Phase.REMOTE_MISSING_REVIEW,
                     missingReview.syncState.snapshot().phase);
        assertEquals(writesBeforeMissingReview,
                     deletedRemote.writeAttempts);

        missingReview = restart(
            missingReview, ACTOR_B, session(62));
        assertCompletion(missingReview.coordinator.begin(BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode
                .REMOTE_DELETION_REVIEW_REQUIRED);
        assertEquals(writesBeforeMissingReview,
                     deletedRemote.writeAttempts);
        OctavoAnnotationSyncCoordinator.Step approvedDeletionRead =
            assertRead(missingReview.coordinator.begin(
                BINDING, "", true), BINDING);
        OctavoAnnotationSyncCoordinator.Step approvedRecreate = assertWrite(
            missingReview.coordinator.acceptRead(
                approvedDeletionRead.operationToken,
                BINDING,
                deletedRemote.read(approvedDeletionRead)),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        assertEquals(writesBeforeMissingReview,
                     deletedRemote.writeAttempts);
        assertCompletion(missingReview.coordinator.acceptWrite(
                approvedRecreate.operationToken,
                BINDING,
                deletedRemote.write(approvedRecreate)),
            OctavoAnnotationSyncCoordinator.CompletionCode.CONVERGED);
        assertArrayEquals(recreationBytes, deletedRemote.portableBytes());
        assertEquals(OctavoAnnotationSyncStore.Phase.IDLE,
                     missingReview.syncState.snapshot().phase);

        File corruptFiles = directory("manifest-corrupt");
        OctavoAnnotationSyncStore corruptSource =
            new OctavoAnnotationSyncStore(corruptFiles);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.MISSING,
                     corruptSource.load());
        assertEquals(OctavoAnnotationSyncStore.UpdateResult.PUBLISHED,
                     corruptSource.bind(BINDING));
        byte[] corrupt = readFile(corruptSource.stateFileForTesting());
        corrupt[corrupt.length - 1] ^= 1;
        writeFile(corruptSource.stateFileForTesting(), corrupt);
        OctavoAnnotationSyncStore quarantined =
            new OctavoAnnotationSyncStore(corruptFiles);
        assertEquals(
            OctavoAnnotationSyncStore.LoadStatus.CORRUPT_QUARANTINED,
            quarantined.load());
        assertFalse(quarantined.stateFileForTesting().exists());
        assertArrayEquals(corrupt,
                          readFile(quarantined.quarantineFileForTesting(1)));
        assertFalse(quarantined.temporaryFileForTesting().exists());
        OctavoAnnotationStore corruptAnnotations =
            new OctavoAnnotationStore(corruptFiles, ACTOR_A);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     corruptAnnotations.load());
        OctavoAnnotationSyncCoordinator corruptCoordinator =
            new OctavoAnnotationSyncCoordinator(
                corruptAnnotations, quarantined, session(59));
        assertCompletion(corruptCoordinator.begin(BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode
                .SYNC_STATE_BLOCKED);
        assertArrayEquals(corrupt,
                          readFile(quarantined.quarantineFileForTesting(1)));

        assertEquals(OctavoAnnotationSyncStore.UpdateResult.PUBLISHED,
                     corruptCoordinator.acknowledgeQuarantinedReset());
        assertTrue(quarantined.stateFileForTesting().isFile());
        assertEquals(44L, quarantined.stateFileForTesting().length());
        byte[] acknowledgedEmpty =
            readFile(quarantined.stateFileForTesting());
        assertEquals(44, acknowledgedEmpty.length);
        assertArrayEquals(acknowledgedEmpty,
                          quarantined.canonicalBytesForTesting());
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.LOADED,
                     quarantined.loadStatus());
        assertFalse(quarantined.updatesBlocked());
        OctavoAnnotationSyncStore.Snapshot acknowledgedSnapshot =
            quarantined.snapshot();
        assertEquals("", acknowledgedSnapshot.bindingFingerprint);
        assertEquals("", acknowledgedSnapshot.convergedDigest);
        assertEquals(OctavoAnnotationSyncStore.Phase.IDLE,
                     acknowledgedSnapshot.phase);
        assertEquals(OctavoAnnotationSyncStore.Attention.NONE,
                     acknowledgedSnapshot.attention);
        assertFalse(acknowledgedSnapshot.remotePreviouslyPresent);
        assertEquals("", acknowledgedSnapshot.inFlightDigest);
        assertNull(acknowledgedSnapshot.remoteSnapshot());
        assertArrayEquals(corrupt,
                          readFile(quarantined.quarantineFileForTesting(1)));

        OctavoAnnotationSyncStore acknowledgedRestart =
            new OctavoAnnotationSyncStore(corruptFiles);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.LOADED,
                     acknowledgedRestart.load());
        assertArrayEquals(acknowledgedEmpty,
                          acknowledgedRestart.canonicalBytesForTesting());
        assertEquals("",
                     acknowledgedRestart.snapshot().bindingFingerprint);
        assertFalse(acknowledgedRestart.updatesBlocked());
        OctavoAnnotationSyncCoordinator acknowledgedCoordinator =
            new OctavoAnnotationSyncCoordinator(
                corruptAnnotations, acknowledgedRestart, session(63));
        OctavoAnnotationSyncCoordinator.Step acknowledgedRead = assertRead(
            acknowledgedCoordinator.begin(BINDING), BINDING);
        assertCompletion(acknowledgedCoordinator.cancel(
                acknowledgedRead.operationToken, BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.CANCELLED);

        File futureFiles = directory("manifest-future");
        OctavoAnnotationStore futureAnnotations =
            new OctavoAnnotationStore(futureFiles, ACTOR_A);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     futureAnnotations.load());
        OctavoAnnotationSyncStore futureSource =
            new OctavoAnnotationSyncStore(futureFiles);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.MISSING,
                     futureSource.load());
        assertEquals(OctavoAnnotationSyncStore.UpdateResult.PUBLISHED,
                     futureSource.bind(BINDING));
        byte[] future = readFile(futureSource.stateFileForTesting());
        putInt(future, Integer.BYTES,
               OctavoAnnotationSyncStore.currentVersionForTesting() + 1);
        writeFile(futureSource.stateFileForTesting(), future);
        OctavoAnnotationSyncStore futureBlocked =
            new OctavoAnnotationSyncStore(futureFiles);
        assertEquals(
            OctavoAnnotationSyncStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            futureBlocked.load());
        OctavoAnnotationSyncCoordinator futureCoordinator =
            new OctavoAnnotationSyncCoordinator(
                futureAnnotations, futureBlocked, session(51));
        assertCompletion(futureCoordinator.begin(BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode
                .SYNC_STATE_BLOCKED);
        assertArrayEquals(future,
                          readFile(futureBlocked.stateFileForTesting()));
        assertFalse(futureBlocked.quarantineFileForTesting(1).exists());

        Device binding = device("binding-mismatch", ACTOR_A, session(52));
        assertEquals(OctavoAnnotationSyncStore.UpdateResult.PUBLISHED,
                     binding.syncState.bind(BINDING));
        assertCompletion(binding.coordinator.begin(OTHER_BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.BINDING_MISMATCH);

        Device token = device("operation-token", ACTOR_A, session(53));
        OctavoAnnotationSyncCoordinator.Step tokenRead = assertRead(
            token.coordinator.begin(BINDING), BINDING);
        assertTrue(isHex(tokenRead.operationToken, 32));
        OctavoAnnotationSyncCoordinator concurrent =
            new OctavoAnnotationSyncCoordinator(
                token.annotations, token.syncState, session(60));
        assertCompletion(concurrent.begin(BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.BUSY);
        assertCompletion(token.coordinator.acceptRead(
                tokenRead.operationToken,
                OTHER_BINDING,
                OctavoAnnotationSyncCoordinator.ReadResult.missing()),
            OctavoAnnotationSyncCoordinator.CompletionCode.INVALID_RESPONSE);
        assertTrue(token.syncState.leaseHeldForTesting());
        assertCompletion(token.coordinator.cancel(
                tokenRead.operationToken, BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.CANCELLED);
        assertFalse(token.syncState.leaseHeldForTesting());

        token.coordinator = new OctavoAnnotationSyncCoordinator(
            token.annotations, token.syncState, session(54));
        assertEquals(OctavoAnnotationSyncStore.UpdateResult.PUBLISHED,
                     token.syncState.recordAttention(
                         BINDING,
                         OctavoAnnotationSyncStore.Attention.REMOTE_INVALID));
        OctavoAnnotationSyncCoordinator.Step wrongTokenRead = assertRead(
            token.coordinator.begin(BINDING), BINDING);
        assertCompletion(token.coordinator.acceptRead(
                "not-the-operation-token",
                BINDING,
                OctavoAnnotationSyncCoordinator.ReadResult.missing()),
            OctavoAnnotationSyncCoordinator.CompletionCode.INVALID_RESPONSE);
        assertTrue(token.syncState.leaseHeldForTesting());
        assertEquals(OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
                     token.syncState.snapshot().attention);
        assertCompletion(token.coordinator.cancel(
                wrongTokenRead.operationToken, BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.CANCELLED);
        assertFalse(token.syncState.leaseHeldForTesting());
        assertEquals(OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
                     token.syncState.snapshot().attention);

        token.coordinator = new OctavoAnnotationSyncCoordinator(
            token.annotations, token.syncState, session(55));
        OctavoAnnotationSyncCoordinator.Step cancelRead = assertRead(
            token.coordinator.begin(BINDING), BINDING);
        assertCompletion(token.coordinator.cancel(
                cancelRead.operationToken, BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.CANCELLED);
        assertCompletion(token.coordinator.acceptRead(
                cancelRead.operationToken,
                BINDING,
                OctavoAnnotationSyncCoordinator.ReadResult.missing()),
            OctavoAnnotationSyncCoordinator.CompletionCode.INVALID_RESPONSE);

        token.coordinator = new OctavoAnnotationSyncCoordinator(
            token.annotations, token.syncState, session(56));
        OctavoAnnotationSyncCoordinator.Step staleRead = assertRead(
            token.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step unexecutedWrite = assertWrite(
            token.coordinator.acceptRead(
                staleRead.operationToken,
                BINDING,
                OctavoAnnotationSyncCoordinator.ReadResult.missing()),
            OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING,
            BINDING);
        byte[] firstCopy = unexecutedWrite.portableBytes();
        if (firstCopy.length > 0) {
            firstCopy[0] ^= 1;
        }
        assertArrayEquals(token.annotations.exportPortableBytes(),
                          unexecutedWrite.portableBytes());
        byte[] writeIntentBeforeStaleRead =
            token.syncState.canonicalBytesForTesting();
        OctavoAnnotationSyncStore.Snapshot writeIntentBefore =
            token.syncState.snapshot();
        assertCompletion(token.coordinator.acceptRead(
                staleRead.operationToken,
                BINDING,
                OctavoAnnotationSyncCoordinator.ReadResult.missing()),
            OctavoAnnotationSyncCoordinator.CompletionCode.INVALID_RESPONSE);
        assertArrayEquals(writeIntentBeforeStaleRead,
                          token.syncState.canonicalBytesForTesting());
        OctavoAnnotationSyncStore.Snapshot writeIntentAfter =
            token.syncState.snapshot();
        assertEquals(writeIntentBefore.phase, writeIntentAfter.phase);
        assertEquals(writeIntentBefore.attention, writeIntentAfter.attention);
        assertEquals(writeIntentBefore.inFlightDigest,
                     writeIntentAfter.inFlightDigest);
        assertEquals(writeIntentBefore.remotePreviouslyPresent,
                     writeIntentAfter.remotePreviouslyPresent);
        assertTrue(token.syncState.leaseHeldForTesting());
        assertCompletion(token.coordinator.cancel(
                unexecutedWrite.operationToken, BINDING),
            OctavoAnnotationSyncCoordinator.CompletionCode.CANCELLED);
        assertFalse(token.syncState.leaseHeldForTesting());
        assertEquals(0, remote.writeAttempts);
    }

    private void assertRejectedRemote(
        String name,
        byte[] candidate,
        OctavoAnnotationSyncCoordinator.CompletionCode expected)
        throws IOException {
        assertRejectedRemote(name, candidate, expected, true);
    }

    private void assertRejectedRemote(
        String name,
        byte[] candidate,
        OctavoAnnotationSyncCoordinator.CompletionCode expected,
        boolean exerciseCopies) throws IOException {
        Device device = device(name, ACTOR_A, session(100 + name.length()));
        assertTrue(device.annotations.putBookmarkForTesting(
            DIGEST, 1, 10, "Local", "Preserve exact").succeeded());
        byte[] localFile =
            device.annotations.canonicalBytesForTesting();
        byte[] localPortable =
            device.annotations.exportPortableBytes();
        FakeRemote remote = FakeRemote.found(
            BINDING, candidate, exerciseCopies);
        String remoteDigest = remote.digest();
        int remoteLength = remote.length();
        OctavoAnnotationSyncCoordinator.Step read = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.Step rejected =
            device.coordinator.acceptRead(
                read.operationToken,
                BINDING,
                remote.read(read));
        assertCompletion(rejected, expected);
        assertArrayEquals(localFile,
                          device.annotations.canonicalBytesForTesting());
        assertArrayEquals(localPortable,
                          device.annotations.exportPortableBytes());
        assertEquals(remoteDigest, remote.digest());
        assertEquals(remoteLength, remote.length());
        assertEquals(0, remote.writeAttempts);
        assertEquals(1, remote.readAttempts);
        OctavoAnnotationSyncStore.Attention attention =
            attentionForCompletion(expected);
        assertEquals(attention, device.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            device.coordinator.pendingStatus(BINDING));
        device = restart(device, ACTOR_B,
                         session(180 + name.length()));
        assertEquals(attention, device.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            device.coordinator.pendingStatus(BINDING));
    }

    private void assertRejectedProviderEnvelope(
        String name,
        byte[] handle,
        byte[] revision,
        byte[] portableBytes,
        OctavoAnnotationSyncCoordinator.CompletionCode expected)
        throws IOException {
        Device device = device(name, ACTOR_A, session(140 + name.length()));
        assertTrue(device.annotations.putBookmarkForTesting(
            DIGEST, 2, 20, "Local", "Envelope no-op").succeeded());
        byte[] localFile =
            device.annotations.canonicalBytesForTesting();
        byte[] localPortable =
            device.annotations.exportPortableBytes();
        FakeRemote remote = FakeRemote.missing(BINDING, false);
        OctavoAnnotationSyncCoordinator.Step read = assertRead(
            device.coordinator.begin(BINDING), BINDING);
        OctavoAnnotationSyncCoordinator.ReadResult result =
            remote.foundResponse(
                read, handle, revision, portableBytes);
        boolean bodyOverBound = portableBytes.length
            > OctavoAnnotationStore.maximumPortableFileBytesForTesting();
        assertEquals(bodyOverBound, result.portableBytesOverBound);
        if (handle.length
            > OctavoAnnotationSyncCoordinator.MAX_PROVIDER_VALUE_BYTES) {
            Arrays.fill(handle, (byte)0x41);
        }
        if (revision.length
            > OctavoAnnotationSyncCoordinator.MAX_PROVIDER_VALUE_BYTES) {
            Arrays.fill(revision, (byte)0x42);
        }
        if (bodyOverBound) {
            Arrays.fill(portableBytes, (byte)0x43);
        }
        assertCompletion(device.coordinator.acceptRead(
                read.operationToken, BINDING, result), expected);
        assertArrayEquals(localFile,
                          device.annotations.canonicalBytesForTesting());
        assertArrayEquals(localPortable,
                          device.annotations.exportPortableBytes());
        assertTrue(remote.isMissing());
        assertEquals(1, remote.readAttempts);
        assertEquals(0, remote.writeAttempts);
        OctavoAnnotationSyncStore.Attention attention =
            attentionForCompletion(expected);
        assertEquals(attention, device.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            device.coordinator.pendingStatus(BINDING));
        device = restart(device, ACTOR_B,
                         session(220 + name.length()));
        assertEquals(attention, device.syncState.snapshot().attention);
        assertEquals(
            OctavoAnnotationSyncCoordinator.PendingStatus.ATTENTION_REQUIRED,
            device.coordinator.pendingStatus(BINDING));
    }

    private static OctavoAnnotationSyncStore.Attention
        attentionForCompletion(
            OctavoAnnotationSyncCoordinator.CompletionCode completion) {
        if (completion
            == OctavoAnnotationSyncCoordinator.CompletionCode.REMOTE_INVALID) {
            return OctavoAnnotationSyncStore.Attention.REMOTE_INVALID;
        }
        if (completion
            == OctavoAnnotationSyncCoordinator.CompletionCode
                .REMOTE_FUTURE_VERSION) {
            return OctavoAnnotationSyncStore.Attention
                .REMOTE_FUTURE_VERSION;
        }
        if (completion
            == OctavoAnnotationSyncCoordinator.CompletionCode
                .REMOTE_INPUT_LIMIT) {
            return OctavoAnnotationSyncStore.Attention.REMOTE_INPUT_LIMIT;
        }
        if (completion
            == OctavoAnnotationSyncCoordinator.CompletionCode
                .INVALID_RESPONSE) {
            return OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE;
        }
        throw new AssertionError("No attention mapping for " + completion);
    }

    private Device device(String name, String actor, String session) {
        File files = directory(name);
        OctavoAnnotationStore annotations =
            new OctavoAnnotationStore(files, actor);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     annotations.load());
        OctavoAnnotationSyncStore syncState =
            new OctavoAnnotationSyncStore(files);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.MISSING,
                     syncState.load());
        return new Device(files,
                          annotations,
                          syncState,
                          new OctavoAnnotationSyncCoordinator(
                              annotations, syncState, session));
    }

    private Device restart(Device previous, String actor, String session) {
        OctavoAnnotationStore annotations =
            new OctavoAnnotationStore(previous.files, actor);
        assertEquals(OctavoAnnotationStore.LoadStatus.LOADED,
                     annotations.load());
        OctavoAnnotationSyncStore syncState =
            new OctavoAnnotationSyncStore(previous.files);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.LOADED,
                     syncState.load());
        return new Device(previous.files,
                          annotations,
                          syncState,
                          new OctavoAnnotationSyncCoordinator(
                              annotations, syncState, session));
    }

    private Device restartWithMissingAnnotations(
        Device previous, String actor, String session) {
        OctavoAnnotationStore annotations =
            new OctavoAnnotationStore(previous.files, actor);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     annotations.load());
        OctavoAnnotationSyncStore syncState =
            new OctavoAnnotationSyncStore(previous.files);
        assertEquals(OctavoAnnotationSyncStore.LoadStatus.LOADED,
                     syncState.load());
        return new Device(previous.files,
                          annotations,
                          syncState,
                          new OctavoAnnotationSyncCoordinator(
                              annotations, syncState, session));
    }

    private OctavoAnnotationStore annotationStore(String name, String actor) {
        OctavoAnnotationStore store =
            new OctavoAnnotationStore(directory(name), actor);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING, store.load());
        return store;
    }

    private File directory(String name) {
        File result = new File(testRoot, name);
        assertTrue(result.mkdirs());
        return result;
    }

    private static OctavoAnnotationSyncCoordinator.Step assertRead(
        OctavoAnnotationSyncCoordinator.Step step, String binding) {
        assertNotNull(step);
        assertEquals(OctavoAnnotationSyncCoordinator.StepKind.READ,
                     step.kind);
        assertNull(step.completion);
        assertEquals(binding, step.bindingFingerprint);
        assertEquals(OctavoAnnotationSyncCoordinator.LOGICAL_OBJECT_NAME,
                     step.logicalObjectName);
        assertTrue(isHex(step.operationToken, 32));
        assertNull(step.writeMode);
        assertNull(step.handle());
        assertNull(step.revision());
        assertNull(step.portableBytes());
        return step;
    }

    private static OctavoAnnotationSyncCoordinator.Step assertWrite(
        OctavoAnnotationSyncCoordinator.Step step,
        OctavoAnnotationSyncCoordinator.WriteMode mode,
        String binding) {
        assertNotNull(step);
        assertEquals(OctavoAnnotationSyncCoordinator.StepKind.WRITE,
                     step.kind);
        assertNull(step.completion);
        assertEquals(binding, step.bindingFingerprint);
        assertEquals(OctavoAnnotationSyncCoordinator.LOGICAL_OBJECT_NAME,
                     step.logicalObjectName);
        assertTrue(isHex(step.operationToken, 32));
        assertEquals(mode, step.writeMode);
        assertNotNull(step.portableBytes());
        if (mode
            == OctavoAnnotationSyncCoordinator.WriteMode.CREATE_IF_MISSING) {
            assertNull(step.handle());
            assertNull(step.revision());
        } else {
            assertNotNull(step.handle());
            assertNotNull(step.revision());
        }
        return step;
    }

    private static void assertCompletion(
        OctavoAnnotationSyncCoordinator.Step step,
        OctavoAnnotationSyncCoordinator.CompletionCode code) {
        assertNotNull(step);
        assertEquals(OctavoAnnotationSyncCoordinator.StepKind.COMPLETE,
                     step.kind);
        assertEquals(code, step.completion);
        assertEquals("", step.operationToken);
        assertNull(step.writeMode);
        assertNull(step.handle());
        assertNull(step.revision());
        assertNull(step.portableBytes());
    }

    private static void assertReview(
        OctavoAnnotationSyncCoordinator.Step step,
        int bookmarks,
        int highlights,
        int notes,
        int visible,
        int tombstones,
        int conflicts,
        int noteVersions) {
        assertNotNull(step.review);
        assertEquals(bookmarks, step.review.bookmarkCount);
        assertEquals(highlights, step.review.highlightCount);
        assertEquals(notes, step.review.noteCount);
        assertEquals(visible, step.review.visibleCount);
        assertEquals(tombstones, step.review.tombstoneCount);
        assertEquals(conflicts, step.review.conflictCount);
        assertEquals(noteVersions, step.review.noteVersionCount);
        assertTrue(step.review.byteLength >= 5 * Integer.BYTES);
        assertTrue(isHex(step.review.contentDigest, 64));
    }

    private static String session(int value) {
        return fixedId(1000 + value);
    }

    private static String actor(int value) {
        return fixedId(2000 + value);
    }

    private static String fixedId(int value) {
        String suffix = Integer.toHexString(value);
        StringBuilder result = new StringBuilder(32);
        for (int index = suffix.length(); index < 32; ++index) {
            result.append('0');
        }
        result.append(suffix);
        return result.toString();
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 0xf, 16));
                result.append(Character.forDigit(value & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int index = 0; index < value.length(); ++index) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                  || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] readFile(File file) throws IOException {
        assertTrue(file.isFile());
        assertTrue(file.length() <= Integer.MAX_VALUE);
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw new IOException("Unexpected end of file");
                }
                offset += count;
            }
        }
        return bytes;
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

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void updateChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        putInt(bytes, bytes.length - Integer.BYTES,
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

    private static final class Device {
        final File files;
        final OctavoAnnotationStore annotations;
        final OctavoAnnotationSyncStore syncState;
        OctavoAnnotationSyncCoordinator coordinator;

        Device(File files,
               OctavoAnnotationStore annotations,
               OctavoAnnotationSyncStore syncState,
               OctavoAnnotationSyncCoordinator coordinator) {
            this.files = files;
            this.annotations = annotations;
            this.syncState = syncState;
            this.coordinator = coordinator;
        }
    }

    /** A caller-owned value executor, deliberately not a provider interface. */
    private static final class FakeRemote {
        private final String binding;
        private final boolean exerciseDefensiveCopies;
        private final List<String> trace = new ArrayList<>();
        private final Set<String> operationTokens = new HashSet<>();
        private byte[] handle;
        private byte[] revision;
        private byte[] portableBytes;
        private int revisionCounter;
        int readAttempts;
        int writeAttempts;
        int preconditionFailures;

        private FakeRemote(String binding,
                           byte[] portableBytes,
                           boolean exerciseDefensiveCopies) {
            this.binding = binding;
            this.exerciseDefensiveCopies = exerciseDefensiveCopies;
            this.portableBytes = portableBytes == null
                ? null : portableBytes.clone();
            if (portableBytes != null) {
                handle = ascii("opaque-handle-z");
                revision = ascii("opaque-revision-z");
            }
        }

        static FakeRemote missing(String binding,
                                  boolean exerciseDefensiveCopies) {
            return new FakeRemote(binding, null, exerciseDefensiveCopies);
        }

        static FakeRemote found(String binding,
                                byte[] portableBytes,
                                boolean exerciseDefensiveCopies) {
            if (portableBytes == null) {
                throw new IllegalArgumentException();
            }
            return new FakeRemote(
                binding, portableBytes, exerciseDefensiveCopies);
        }

        OctavoAnnotationSyncCoordinator.ReadResult read(
            OctavoAnnotationSyncCoordinator.Step step) {
            recordRead(step);
            if (portableBytes == null) {
                trace.add("READ:MISSING");
                return OctavoAnnotationSyncCoordinator.ReadResult.missing();
            }
            trace.add("READ:FOUND:"
                + OctavoAnnotationSyncCoordinatorTest.digest(portableBytes));
            if (!exerciseDefensiveCopies) {
                return OctavoAnnotationSyncCoordinator.ReadResult.found(
                    handle, revision, portableBytes);
            }
            byte[] sourceHandle = handle.clone();
            byte[] sourceRevision = revision.clone();
            byte[] sourcePortable = portableBytes.clone();
            OctavoAnnotationSyncCoordinator.ReadResult result =
                OctavoAnnotationSyncCoordinator.ReadResult.found(
                    sourceHandle, sourceRevision, sourcePortable);
            Arrays.fill(sourceHandle, (byte)0x41);
            Arrays.fill(sourceRevision, (byte)0x42);
            if (sourcePortable.length > 0) {
                sourcePortable[0] ^= 1;
            }
            return result;
        }

        OctavoAnnotationSyncCoordinator.ReadResult readFailure(
            OctavoAnnotationSyncCoordinator.Step step,
            OctavoAnnotationSyncCoordinator.ProviderFailure failure) {
            recordRead(step);
            trace.add("READ:FAILURE:" + failure);
            return OctavoAnnotationSyncCoordinator.ReadResult.failure(failure);
        }

        OctavoAnnotationSyncCoordinator.ReadResult foundResponse(
            OctavoAnnotationSyncCoordinator.Step step,
            byte[] responseHandle,
            byte[] responseRevision,
            byte[] responsePortableBytes) {
            recordRead(step);
            trace.add("READ:SCRIPTED_FOUND:"
                + (responsePortableBytes == null
                    ? -1 : responsePortableBytes.length));
            return OctavoAnnotationSyncCoordinator.ReadResult.found(
                responseHandle, responseRevision, responsePortableBytes);
        }

        OctavoAnnotationSyncCoordinator.ReadResult duplicates(
            OctavoAnnotationSyncCoordinator.Step step) {
            recordRead(step);
            trace.add("READ:DUPLICATES");
            return OctavoAnnotationSyncCoordinator.ReadResult.duplicates();
        }

        OctavoAnnotationSyncCoordinator.WriteResult write(
            OctavoAnnotationSyncCoordinator.Step step) {
            recordWrite(step);
            boolean preconditionMatches;
            if (step.writeMode
                == OctavoAnnotationSyncCoordinator.WriteMode
                    .CREATE_IF_MISSING) {
                preconditionMatches = portableBytes == null;
            } else {
                preconditionMatches = portableBytes != null
                    && Arrays.equals(handle, step.handle())
                    && Arrays.equals(revision, step.revision());
            }
            if (!preconditionMatches) {
                preconditionFailures += 1;
                trace.add("WRITE:PRECONDITION_FAILED");
                return OctavoAnnotationSyncCoordinator.WriteResult
                    .preconditionFailed();
            }
            trace.add("WRITE:COMMITTED:" + step.writeMode);
            return commit(step);
        }

        OctavoAnnotationSyncCoordinator.WriteResult preconditionFailed(
            OctavoAnnotationSyncCoordinator.Step step) {
            recordWrite(step);
            advanceRevision();
            preconditionFailures += 1;
            trace.add("WRITE:SCRIPTED_PRECONDITION_FAILED");
            return OctavoAnnotationSyncCoordinator.WriteResult
                .preconditionFailed();
        }

        OctavoAnnotationSyncCoordinator.WriteResult definiteWriteFailure(
            OctavoAnnotationSyncCoordinator.Step step,
            OctavoAnnotationSyncCoordinator.ProviderFailure failure) {
            recordWrite(step);
            trace.add("WRITE:DEFINITE_FAILURE:" + failure);
            return OctavoAnnotationSyncCoordinator.WriteResult
                .definiteFailure(failure);
        }

        OctavoAnnotationSyncCoordinator.WriteResult commitWithOutcomeUnknown(
            OctavoAnnotationSyncCoordinator.Step step) {
            recordWrite(step);
            applyWrite(step);
            trace.add("WRITE:COMMITTED_RESPONSE_LOST");
            return OctavoAnnotationSyncCoordinator.WriteResult
                .outcomeUnknown();
        }

        byte[] portableBytes() {
            return portableBytes == null ? null : portableBytes.clone();
        }

        int length() {
            return portableBytes == null ? 0 : portableBytes.length;
        }

        String digest() {
            return portableBytes == null ? "" :
                OctavoAnnotationSyncCoordinatorTest.digest(portableBytes);
        }

        boolean isMissing() {
            return portableBytes == null;
        }

        void deleteExternally() {
            portableBytes = null;
            handle = null;
            revision = null;
            trace.add("EXTERNAL:DELETE");
        }

        boolean allTokensWereUnique() {
            return operationTokens.size() == readAttempts + writeAttempts;
        }

        private OctavoAnnotationSyncCoordinator.WriteResult commit(
            OctavoAnnotationSyncCoordinator.Step step) {
            applyWrite(step);
            byte[] resultHandle = handle.clone();
            byte[] resultRevision = revision.clone();
            OctavoAnnotationSyncCoordinator.WriteResult result =
                OctavoAnnotationSyncCoordinator.WriteResult.committed(
                    resultHandle, resultRevision);
            if (exerciseDefensiveCopies) {
                Arrays.fill(resultHandle, (byte)0x51);
                Arrays.fill(resultRevision, (byte)0x52);
            }
            return result;
        }

        private void applyWrite(
            OctavoAnnotationSyncCoordinator.Step step) {
            byte[] requested = step.portableBytes();
            assertNotNull(requested);
            if (exerciseDefensiveCopies && requested.length > 0) {
                byte[] escaped = step.portableBytes();
                escaped[0] ^= 1;
                assertArrayEquals(requested, step.portableBytes());
            }
            portableBytes = requested.clone();
            if (handle == null) {
                handle = ascii("opaque-handle-z");
            }
            advanceRevision();
        }

        private void advanceRevision() {
            revisionCounter += 1;
            revision = ascii(revisionCounter % 2 == 0
                ? "opaque-revision-a-" + revisionCounter
                : "opaque-revision-z-" + revisionCounter);
        }

        private void recordRead(
            OctavoAnnotationSyncCoordinator.Step step) {
            assertRead(step, binding);
            assertTrue("Duplicate operation token", operationTokens.add(
                step.operationToken));
            readAttempts += 1;
        }

        private void recordWrite(
            OctavoAnnotationSyncCoordinator.Step step) {
            assertWrite(step, step.writeMode, binding);
            assertTrue("Duplicate operation token", operationTokens.add(
                step.operationToken));
            if (exerciseDefensiveCopies
                && step.writeMode
                    == OctavoAnnotationSyncCoordinator.WriteMode
                        .REPLACE_IF_REVISION) {
                byte[] expectedHandle = step.handle();
                byte[] escapedHandle = step.handle();
                byte[] expectedRevision = step.revision();
                byte[] escapedRevision = step.revision();
                escapedHandle[0] ^= 1;
                escapedRevision[0] ^= 1;
                assertArrayEquals(expectedHandle, step.handle());
                assertArrayEquals(expectedRevision, step.revision());
            }
            writeAttempts += 1;
        }
    }
}
