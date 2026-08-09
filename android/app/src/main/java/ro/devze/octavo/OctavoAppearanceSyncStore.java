package ro.devze.octavo;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Independent private O1SS state for global appearance synchronization.
 *
 * The caller owns Surface presentation and O7ST publication. This store
 * advances its local portable lane only after the caller supplies the exact
 * successfully presented target and a separate durable O7ST proof.
 */
final class OctavoAppearanceSyncStore {
    enum LoadStatus {
        MISSING_CREATED,
        LOADED,
        IDENTITY_PUBLISH_FAILED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        FUTURE_VERSION_BLOCKED,
        PUBLISH_UNCERTAIN_BLOCKED
    }

    enum MutationResult {
        UPDATED,
        UNCHANGED,
        INVALID,
        BLOCKED,
        LIMIT,
        EXHAUSTED,
        CONFLICT,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == UPDATED || this == UNCHANGED;
        }
    }

    enum PortableMergeResult {
        MERGED,
        UNCHANGED,
        FUTURE_RETAINED,
        FUTURE_CONFLICT,
        INVALID,
        LIMIT,
        EQUIVOCATION,
        OWN_LANE_ADVANCE,
        BLOCKED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == MERGED || this == UNCHANGED
                || this == FUTURE_RETAINED;
        }
    }

    enum PortableExportStatus {
        EXPORTED,
        NOT_LOADED,
        BLOCKED,
        INVALID,
        LOCAL_FAILURE
    }

    enum Decision {
        NONE(0),
        ACCEPTED(1),
        KEPT(2),
        DISMISSED_AT_EPOCH(3);

        final int wireId;

        Decision(int wireId) {
            this.wireId = wireId;
        }

        static Decision fromWireId(int wireId) {
            for (Decision value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    enum PendingKind {
        LOCAL(1),
        REMOTE(2);

        final int wireId;

        PendingKind(int wireId) {
            this.wireId = wireId;
        }

        static PendingKind fromWireId(int wireId) {
            for (PendingKind value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    enum O7stProof {
        CANONICAL_V3_LOAD,
        CURRENT_PROCESS_ATOMIC_SAVE,
        CURRENT_PROCESS_RECONCILED_AFTER_UNCERTAIN_SAVE
    }

    enum PendingRecovery {
        NONE,
        TARGET_DURABLE,
        ORIGIN_DURABLE,
        MISMATCH
    }

    static final class Candidate {
        final String deviceId;
        final long sequence;
        final long reviewEpoch;
        final long originLocalSequence;
        final Decision decision;
        private final OctavoAppearancePortable.Profile targetProfile;
        private final OctavoAppearancePortable.Profile originProfile;

        private Candidate(LaneState lane,
                          long reviewEpoch,
                          OctavoAppearancePortable.Lane local) {
            deviceId = lane.lane.deviceId;
            sequence = lane.lane.sequence;
            this.reviewEpoch = reviewEpoch;
            originLocalSequence = local.sequence;
            decision = lane.decision;
            targetProfile = lane.lane.profile;
            originProfile = local.profile;
        }

        OctavoAppearance targetAppearance() {
            return targetProfile.toAppearance();
        }

        OctavoAppearance originAppearance() {
            return originProfile.toAppearance();
        }

        int differenceCount() {
            return targetProfile.differenceCount(originProfile);
        }

        boolean sameIdentity(Candidate other) {
            return other != null
                && deviceId.equals(other.deviceId)
                && sequence == other.sequence
                && reviewEpoch == other.reviewEpoch
                && originLocalSequence == other.originLocalSequence
                && targetProfile.equals(other.targetProfile)
                && originProfile.equals(other.originProfile);
        }
    }

    static final class Pending {
        final PendingKind kind;
        final boolean hasOriginLane;
        final long originLocalSequence;
        final long localSequence;
        final String remoteDeviceId;
        final long remoteSequence;
        private final OctavoAppearancePortable.Profile originProfile;
        private final OctavoAppearancePortable.Profile targetProfile;

        private Pending(PendingState value) {
            kind = value.kind;
            hasOriginLane = value.hasOriginLane;
            originLocalSequence = value.originLocalSequence;
            localSequence = value.localSequence;
            remoteDeviceId = value.remoteDeviceId;
            remoteSequence = value.remoteSequence;
            originProfile = value.originProfile;
            targetProfile = value.targetProfile;
        }

        OctavoAppearance originAppearance() {
            return originProfile.toAppearance();
        }

        OctavoAppearance targetAppearance() {
            return targetProfile.toAppearance();
        }

        boolean sameIdentity(Pending other) {
            return other != null
                && kind == other.kind
                && hasOriginLane == other.hasOriginLane
                && originLocalSequence == other.originLocalSequence
                && localSequence == other.localSequence
                && remoteDeviceId.equals(other.remoteDeviceId)
                && remoteSequence == other.remoteSequence
                && originProfile.equals(other.originProfile)
                && targetProfile.equals(other.targetProfile);
        }
    }

    static final class PortableExport {
        final PortableExportStatus status;
        private final byte[] bytes;

        private PortableExport(PortableExportStatus status, byte[] bytes) {
            this.status = status;
            this.bytes = bytes == null ? null : bytes.clone();
        }

        byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }

    private static final class LaneState {
        final OctavoAppearancePortable.Lane lane;
        final Decision decision;
        final long decisionEpoch;

        LaneState(OctavoAppearancePortable.Lane lane,
                  Decision decision,
                  long decisionEpoch) {
            this.lane = lane;
            this.decision = decision;
            this.decisionEpoch = decisionEpoch;
        }

        static LaneState undecided(OctavoAppearancePortable.Lane lane) {
            return new LaneState(lane, Decision.NONE, 0);
        }

        LaneState withDecision(Decision value, long epoch) {
            return new LaneState(lane, value, epoch);
        }
    }

    private static final class PendingState {
        final PendingKind kind;
        final boolean hasOriginLane;
        final long originLocalSequence;
        final OctavoAppearancePortable.Profile originProfile;
        final long localSequence;
        final OctavoAppearancePortable.Profile targetProfile;
        final String remoteDeviceId;
        final long remoteSequence;

        PendingState(PendingKind kind,
                     boolean hasOriginLane,
                     long originLocalSequence,
                     OctavoAppearancePortable.Profile originProfile,
                     long localSequence,
                     OctavoAppearancePortable.Profile targetProfile,
                     String remoteDeviceId,
                     long remoteSequence) {
            this.kind = kind;
            this.hasOriginLane = hasOriginLane;
            this.originLocalSequence = originLocalSequence;
            this.originProfile = originProfile;
            this.localSequence = localSequence;
            this.targetProfile = targetProfile;
            this.remoteDeviceId = remoteDeviceId;
            this.remoteSequence = remoteSequence;
        }
    }

    private static final class State {
        final String deviceId;
        final long reviewEpoch;
        final TreeMap<String, LaneState> lanes;
        final PendingState pending;
        final byte[] futureBytes;
        final int attention;

        State(String deviceId,
              long reviewEpoch,
              Map<String, LaneState> lanes,
              PendingState pending,
              byte[] futureBytes,
              int attention) {
            this.deviceId = deviceId;
            this.reviewEpoch = reviewEpoch;
            this.lanes = new TreeMap<>(lanes);
            this.pending = pending;
            this.futureBytes = futureBytes == null
                ? null : futureBytes.clone();
            this.attention = attention;
        }

        State withEpoch(long value) {
            return new State(deviceId, value, lanes, pending,
                             futureBytes, attention);
        }

        State withLanes(Map<String, LaneState> value) {
            return new State(deviceId, reviewEpoch, value, pending,
                             futureBytes, attention);
        }

        State withPending(PendingState value) {
            return new State(deviceId, reviewEpoch, lanes, value,
                             futureBytes, attention);
        }

        State withFuture(byte[] value, int nextAttention) {
            return new State(deviceId, reviewEpoch, lanes, pending,
                             value, nextAttention);
        }

        State withAttention(int value) {
            return new State(deviceId, reviewEpoch, lanes, pending,
                             futureBytes, value);
        }
    }

    private static final int STORE_MAGIC = 0x4F315353; // "O1SS"
    private static final int STORE_VERSION = 1;
    private static final int STORE_HEADER_FIELD_COUNT = 6;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int DEVICE_ID_BYTES = 32;
    private static final int QUARANTINE_SLOTS = 3;
    private static final int ATTENTION_NONE = 0;
    private static final int ATTENTION_FUTURE_RETAINED = 1;
    private static final int ATTENTION_FUTURE_CONFLICT = 2;
    private static final String ZERO_DEVICE_ID =
        "00000000000000000000000000000000";
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "appearance-sync.v1";
    private static final String TEMPORARY_FILE = "appearance-sync.v1.tmp";
    private static final String QUARANTINE_PREFIX =
        "appearance-sync.corrupt.";

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private State current;
    private LoadStatus loadStatus = LoadStatus.MISSING_CREATED;
    private boolean loadAttempted;
    private boolean mutationsBlocked;
    private boolean stateExpectedOnDisk;
    private boolean pendingLoadedFromDisk;
    private boolean failNextPublishForTesting;
    private boolean failNextMoveAfterReplaceForTesting;
    private String lastError = "";

    OctavoAppearanceSyncStore(Context context) {
        this(requireFilesDirectory(context), new SecureRandom(), null);
    }

    OctavoAppearanceSyncStore(File filesDirectory) {
        this(filesDirectory, new SecureRandom(), null);
    }

    OctavoAppearanceSyncStore(File filesDirectory,
                              String deviceIdForTesting) {
        this(filesDirectory, new SecureRandom(), deviceIdForTesting);
    }

    private OctavoAppearanceSyncStore(File filesDirectory,
                                      SecureRandom random,
                                      String deviceId) {
        if (filesDirectory == null || random == null
            || (deviceId != null
                && !OctavoAppearancePortable.validDeviceId(deviceId))) {
            throw new IllegalArgumentException(
                "Invalid appearance synchronization store");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
        current = emptyState(
            deviceId == null ? randomDeviceId(random) : deviceId);
    }

    synchronized LoadStatus load() {
        loadAttempted = true;
        mutationsBlocked = false;
        pendingLoadedFromDisk = false;
        if (!stateFile.exists()) {
            if (stateExpectedOnDisk) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                lastError =
                    "Expected appearance sync state is missing; recovery is required.";
                return loadStatus;
            }
            current = emptyState(current.deviceId);
            stateExpectedOnDisk = false;
            deleteTemporaryBestEffort();
            MutationResult created = publish(current);
            if (created == MutationResult.UPDATED) {
                loadStatus = LoadStatus.MISSING_CREATED;
                lastError = "";
            } else if (created != MutationResult.PUBLISH_UNCERTAIN) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.IDENTITY_PUBLISH_FAILED;
                lastError =
                    "The appearance device identity could not be saved. Retry.";
            }
            return loadStatus;
        }
        try {
            byte[] bytes = readBounded(stateFile);
            if (isFutureStore(bytes)) {
                stateExpectedOnDisk = true;
                mutationsBlocked = true;
                loadStatus = LoadStatus.FUTURE_VERSION_BLOCKED;
                lastError =
                    "Appearance sync state was written by a newer version.";
                return loadStatus;
            }
            current = decodeState(bytes);
            stateExpectedOnDisk = true;
            pendingLoadedFromDisk = current.pending != null;
            loadStatus = LoadStatus.LOADED;
            lastError = attentionMessage(current.attention);
            deleteTemporaryBestEffort();
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState()) {
                current = emptyState(current.deviceId);
                stateExpectedOnDisk = false;
                MutationResult recreated = publish(current);
                if (recreated == MutationResult.UPDATED) {
                    loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                    lastError =
                        "Corrupt appearance sync state was quarantined.";
                } else if (recreated
                           != MutationResult.PUBLISH_UNCERTAIN) {
                    mutationsBlocked = true;
                    loadStatus = LoadStatus.CORRUPT_BLOCKED;
                    lastError =
                        "Corrupt appearance sync state was quarantined, but a new identity could not be saved.";
                }
            } else {
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "Appearance sync state is corrupt and could not be quarantined.";
            }
            return loadStatus;
        }
    }

    synchronized LoadStatus loadStatus() {
        return loadStatus;
    }

    synchronized String lastError() {
        return lastError;
    }

    synchronized String deviceId() {
        return current.deviceId;
    }

    synchronized OctavoAppearance effectiveAppearance() {
        OctavoAppearancePortable.Lane lane = localLaneInternal();
        return lane == null ? null : lane.profile.toAppearance();
    }

    synchronized OctavoAppearancePortable.Lane localLane() {
        return localLaneInternal();
    }

    synchronized long reviewEpoch() {
        return current.reviewEpoch;
    }

    synchronized MutationResult beginReviewEpoch(
        boolean explicitReaderOpen) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (!explicitReaderOpen) {
            return unchanged();
        }
        if (current.pending != null) {
            return fail(MutationResult.BLOCKED,
                        "Resolve the pending appearance Retry before reopening review.");
        }
        if (current.reviewEpoch == Long.MAX_VALUE) {
            return fail(MutationResult.EXHAUSTED,
                        "The appearance review counter is exhausted.");
        }
        return publish(current.withEpoch(current.reviewEpoch + 1));
    }

    synchronized MutationResult recordConverged(
        OctavoAppearance exactSuccessfullyPresented) {
        MutationResult ready = requireMutableAppearance(
            exactSuccessfullyPresented);
        if (ready != null) {
            return ready;
        }
        if (current.pending != null) {
            return fail(MutationResult.BLOCKED,
                        "An appearance transaction is pending.");
        }
        OctavoAppearancePortable.Profile presented = profile(
            exactSuccessfullyPresented);
        OctavoAppearancePortable.Lane local = localLaneInternal();
        if (local == null || !local.profile.equals(presented)) {
            return fail(MutationResult.INVALID,
                        "The presented appearance is not the local lane.");
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(current.lanes);
        boolean changed = false;
        for (Map.Entry<String, LaneState> entry : current.lanes.entrySet()) {
            LaneState lane = entry.getValue();
            if (entry.getKey().equals(current.deviceId)
                || !lane.lane.profile.equals(presented)
                || lane.decision == Decision.ACCEPTED) {
                continue;
            }
            lanes.put(entry.getKey(),
                      lane.withDecision(Decision.ACCEPTED, 0));
            changed = true;
        }
        return changed ? publish(current.withLanes(lanes)) : unchanged();
    }

    synchronized List<Candidate> reviewCandidates(
        OctavoAppearance exactSuccessfullyPresented) {
        if (!loadAttempted || mutationsBlocked || current.pending != null
            || exactSuccessfullyPresented == null
            || current.reviewEpoch <= 0) {
            return Collections.emptyList();
        }
        OctavoAppearancePortable.Profile presented;
        try {
            presented = profile(exactSuccessfullyPresented);
        } catch (IllegalArgumentException exception) {
            return Collections.emptyList();
        }
        OctavoAppearancePortable.Lane local = localLaneInternal();
        if (local == null || !local.profile.equals(presented)) {
            return Collections.emptyList();
        }
        ArrayList<Candidate> result = new ArrayList<>();
        for (LaneState lane : current.lanes.values()) {
            if (lane.lane.deviceId.equals(current.deviceId)
                || lane.lane.profile.equals(presented)) {
                continue;
            }
            boolean reviewable = lane.decision == Decision.NONE
                || (lane.decision == Decision.DISMISSED_AT_EPOCH
                    && lane.decisionEpoch < current.reviewEpoch);
            if (reviewable) {
                result.add(new Candidate(
                    lane, current.reviewEpoch, local));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized Pending pending() {
        return current.pending == null ? null : new Pending(current.pending);
    }

    synchronized PendingRecovery pendingRecovery(
        OctavoAppearance provenCanonicalO7st) {
        if (current.pending == null) {
            return PendingRecovery.NONE;
        }
        if (provenCanonicalO7st == null) {
            return PendingRecovery.MISMATCH;
        }
        OctavoAppearancePortable.Profile durable;
        try {
            durable = profile(provenCanonicalO7st);
        } catch (IllegalArgumentException exception) {
            return PendingRecovery.MISMATCH;
        }
        if (durable.equals(current.pending.targetProfile)) {
            return PendingRecovery.TARGET_DURABLE;
        }
        if (durable.equals(current.pending.originProfile)) {
            return PendingRecovery.ORIGIN_DURABLE;
        }
        return PendingRecovery.MISMATCH;
    }

    synchronized PortableMergeResult mergePortableBytes(byte[] bytes) {
        if (!loadAttempted || mutationsBlocked) {
            lastError = "Appearance sync state is blocked.";
            return PortableMergeResult.BLOCKED;
        }
        OctavoAppearancePortable.DecodeResult decoded =
            OctavoAppearancePortable.decode(bytes);
        if (decoded.status
            == OctavoAppearancePortable.DecodeStatus.FUTURE_VERSION) {
            return retainFuture(decoded.preservedBytes());
        }
        if (decoded.status == OctavoAppearancePortable.DecodeStatus.LIMIT) {
            lastError = "Portable appearance bytes exceed their limit.";
            return PortableMergeResult.LIMIT;
        }
        if (decoded.status != OctavoAppearancePortable.DecodeStatus.READY) {
            lastError = "Portable appearance bytes are invalid.";
            return PortableMergeResult.INVALID;
        }
        OctavoAppearancePortable.Snapshot remote = decoded.snapshot();
        OctavoAppearancePortable.Lane local = localLaneInternal();
        OctavoAppearancePortable.Lane incomingOwn =
            remote.lane(current.deviceId);
        if (incomingOwn != null) {
            if (local == null || incomingOwn.sequence > local.sequence) {
                lastError =
                    "Remote bytes cannot advance this device's appearance lane.";
                return PortableMergeResult.OWN_LANE_ADVANCE;
            }
            if (incomingOwn.sequence == local.sequence
                && !incomingOwn.sameProfile(local)) {
                lastError = "This device's appearance lane is equivocal.";
                return PortableMergeResult.EQUIVOCATION;
            }
        }
        OctavoAppearancePortable.MergeResult joined =
            OctavoAppearancePortable.merge(snapshot(current), remote);
        if (joined.status == OctavoAppearancePortable.MergeStatus.LIMIT) {
            lastError = "The appearance device limit was reached.";
            return PortableMergeResult.LIMIT;
        }
        if (joined.status
            == OctavoAppearancePortable.MergeStatus.EQUIVOCATION) {
            lastError = "A portable appearance lane is equivocal.";
            return PortableMergeResult.EQUIVOCATION;
        }
        if (joined.status
            == OctavoAppearancePortable.MergeStatus.INVALID) {
            lastError = "Portable appearance merge input is invalid.";
            return PortableMergeResult.INVALID;
        }
        if (joined.status
            == OctavoAppearancePortable.MergeStatus.UNCHANGED) {
            lastError = attentionMessage(current.attention);
            return PortableMergeResult.UNCHANGED;
        }
        if (current.pending != null
            && current.pending.kind == PendingKind.LOCAL
            && !current.pending.hasOriginLane
            && joined.snapshot.laneCount()
               >= OctavoAppearancePortable.maximumLaneCount()) {
            lastError =
                "The pending local appearance needs the final device slot.";
            return PortableMergeResult.LIMIT;
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>();
        for (OctavoAppearancePortable.Lane lane : joined.snapshot.lanes()) {
            LaneState old = current.lanes.get(lane.deviceId);
            lanes.put(lane.deviceId,
                      old != null && old.lane.equals(lane)
                          ? old : LaneState.undecided(lane));
        }
        MutationResult published = publish(current.withLanes(lanes));
        return mergePublicationResult(published);
    }

    synchronized MutationResult stageLocalPresented(
        OctavoAppearance exactSuccessfullyPresentedTarget) {
        MutationResult ready = requireMutableAppearance(
            exactSuccessfullyPresentedTarget);
        if (ready != null) {
            return ready;
        }
        if (current.pending != null) {
            return fail(MutationResult.BLOCKED,
                        "An appearance Retry is already pending.");
        }
        OctavoAppearancePortable.Profile target =
            profile(exactSuccessfullyPresentedTarget);
        OctavoAppearancePortable.Lane local = localLaneInternal();
        if (local != null && local.profile.equals(target)) {
            return unchanged();
        }
        if (local == null
            && current.lanes.size()
               >= OctavoAppearancePortable.maximumLaneCount()) {
            return fail(MutationResult.LIMIT,
                        "The appearance device limit was reached.");
        }
        long nextSequence = nextSequence(local);
        if (nextSequence <= 0) {
            return fail(MutationResult.EXHAUSTED,
                        "This device's appearance sequence is exhausted.");
        }
        PendingState pending = new PendingState(
            PendingKind.LOCAL,
            local != null,
            local == null ? 0 : local.sequence,
            local == null ? target : local.profile,
            nextSequence,
            target,
            ZERO_DEVICE_ID,
            0);
        MutationResult result = publish(current.withPending(pending));
        if (result == MutationResult.UPDATED) {
            pendingLoadedFromDisk = false;
        }
        return result;
    }

    synchronized MutationResult stageRemoteApply(
        Candidate candidate,
        OctavoAppearance exactSuccessfullyPresentedOrigin) {
        MutationResult ready = requireMutableAppearance(
            exactSuccessfullyPresentedOrigin);
        if (ready != null) {
            return ready;
        }
        if (current.pending != null) {
            return fail(MutationResult.BLOCKED,
                        "An appearance Retry is already pending.");
        }
        LaneState remote = candidateLane(candidate);
        OctavoAppearancePortable.Lane local = localLaneInternal();
        if (remote == null || local == null
            || !candidateMatches(candidate, remote, local)
            || !local.profile.equals(
                profile(exactSuccessfullyPresentedOrigin))
            || remote.lane.profile.equals(local.profile)) {
            return fail(MutationResult.CONFLICT,
                        "The appearance offer is stale.");
        }
        if (!(remote.decision == Decision.NONE
              || (remote.decision == Decision.DISMISSED_AT_EPOCH
                  && remote.decisionEpoch < current.reviewEpoch))) {
            return fail(MutationResult.CONFLICT,
                        "The appearance offer was already decided.");
        }
        long nextSequence = nextSequence(local);
        if (nextSequence <= 0) {
            return fail(MutationResult.EXHAUSTED,
                        "This device's appearance sequence is exhausted.");
        }
        PendingState pending = new PendingState(
            PendingKind.REMOTE,
            true,
            local.sequence,
            local.profile,
            nextSequence,
            remote.lane.profile,
            remote.lane.deviceId,
            remote.lane.sequence);
        MutationResult result = publish(current.withPending(pending));
        if (result == MutationResult.UPDATED) {
            pendingLoadedFromDisk = false;
        }
        return result;
    }

    synchronized MutationResult completePending(
        Pending expected,
        OctavoAppearance exactSuccessfullyPresentedTarget,
        OctavoAppearance exactProvenO7stTarget,
        O7stProof proof) {
        MutationResult ready = requireMutableAppearance(
            exactSuccessfullyPresentedTarget);
        if (ready != null) {
            return ready;
        }
        PendingState pending = current.pending;
        if (pending == null || !pendingMatches(pending, expected)) {
            return fail(MutationResult.CONFLICT,
                        "The pending appearance transaction is stale.");
        }
        if (pending.kind == PendingKind.REMOTE) {
            LaneState remote = current.lanes.get(pending.remoteDeviceId);
            if (remote == null
                || remote.lane.sequence != pending.remoteSequence
                || !remote.lane.profile.equals(pending.targetProfile)
                || !reviewable(remote, current.reviewEpoch)) {
                return fail(MutationResult.CONFLICT,
                            "The remote appearance was superseded; restore the origin.");
            }
        }
        if (exactProvenO7stTarget == null || proof == null
            || !pending.targetProfile.equals(
                profile(exactSuccessfullyPresentedTarget))
            || !pending.targetProfile.equals(
                profile(exactProvenO7stTarget))
            || !validForwardProof(pending, proof)) {
            return fail(MutationResult.INVALID,
                        "The target was not both presented and saved.");
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(current.lanes);
        lanes.put(current.deviceId, LaneState.undecided(
            new OctavoAppearancePortable.Lane(
                current.deviceId, pending.localSequence,
                pending.targetProfile)));
        for (Map.Entry<String, LaneState> entry :
             new ArrayList<>(lanes.entrySet())) {
            LaneState lane = entry.getValue();
            if (!entry.getKey().equals(current.deviceId)
                && lane.lane.profile.equals(pending.targetProfile)) {
                lanes.put(entry.getKey(),
                          lane.withDecision(Decision.ACCEPTED, 0));
            }
        }
        State candidate = current.withLanes(lanes).withPending(null);
        MutationResult result = publish(candidate);
        if (result == MutationResult.UPDATED) {
            pendingLoadedFromDisk = false;
        }
        return result;
    }

    synchronized MutationResult keep(
        Candidate candidate,
        OctavoAppearance exactSuccessfullyPresentedOrigin) {
        return decide(candidate, exactSuccessfullyPresentedOrigin,
                      Decision.KEPT);
    }

    synchronized MutationResult dismiss(
        Candidate candidate,
        OctavoAppearance exactSuccessfullyPresentedOrigin) {
        return decide(candidate, exactSuccessfullyPresentedOrigin,
                      Decision.DISMISSED_AT_EPOCH);
    }

    synchronized MutationResult dismissPendingAfterRollback(
        Pending expected,
        OctavoAppearance exactSuccessfullyPresentedOrigin,
        OctavoAppearance exactProvenO7stOrigin,
        O7stProof proof,
        boolean advanceDeferredExplicitOpenEpoch) {
        MutationResult ready = requireMutableAppearance(
            exactSuccessfullyPresentedOrigin);
        if (ready != null) {
            return ready;
        }
        PendingState pending = current.pending;
        if (pending == null || !pendingMatches(pending, expected)) {
            return fail(MutationResult.CONFLICT,
                        "The pending appearance rollback is stale.");
        }
        if (!pending.hasOriginLane
            || exactProvenO7stOrigin == null || proof == null
            || proof == O7stProof.CANONICAL_V3_LOAD
            || !pending.originProfile.equals(
                profile(exactSuccessfullyPresentedOrigin))
            || !pending.originProfile.equals(
                profile(exactProvenO7stOrigin))) {
            return fail(MutationResult.INVALID,
                        "The origin was not both re-presented and restored.");
        }
        OctavoAppearancePortable.Lane local = localLaneInternal();
        if (local == null || local.sequence != pending.originLocalSequence
            || !local.profile.equals(pending.originProfile)) {
            return fail(MutationResult.CONFLICT,
                        "The rollback origin is stale.");
        }
        long decisionEpoch = current.reviewEpoch;
        if (advanceDeferredExplicitOpenEpoch
            && decisionEpoch < Long.MAX_VALUE) {
            decisionEpoch += 1;
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(current.lanes);
        if (pending.kind == PendingKind.REMOTE) {
            LaneState remote = lanes.get(pending.remoteDeviceId);
            if (remote != null
                && remote.lane.sequence == pending.remoteSequence
                && remote.lane.profile.equals(pending.targetProfile)) {
                lanes.put(pending.remoteDeviceId,
                          remote.withDecision(
                              Decision.DISMISSED_AT_EPOCH,
                              decisionEpoch));
            }
        }
        State candidate = current.withLanes(lanes).withPending(null)
            .withEpoch(decisionEpoch);
        MutationResult result = publish(candidate);
        if (result == MutationResult.UPDATED) {
            pendingLoadedFromDisk = false;
        }
        return result;
    }

    synchronized PortableExport exportPortable() {
        if (!loadAttempted) {
            return new PortableExport(
                PortableExportStatus.NOT_LOADED, null);
        }
        if (mutationsBlocked) {
            return new PortableExport(PortableExportStatus.BLOCKED, null);
        }
        try {
            return new PortableExport(
                PortableExportStatus.EXPORTED,
                OctavoAppearancePortable.encode(snapshot(current)));
        } catch (IOException | RuntimeException exception) {
            lastError = "Portable appearance bytes could not be created.";
            return new PortableExport(
                PortableExportStatus.LOCAL_FAILURE, null);
        }
    }

    synchronized byte[] retainedFutureBytes() {
        return current.futureBytes == null
            ? null : current.futureBytes.clone();
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    synchronized void failNextMoveAfterReplaceForTesting() {
        failNextMoveAfterReplaceForTesting = true;
    }

    synchronized byte[] canonicalBytesForTesting() throws IOException {
        return encodeState(current);
    }

    File stateFileForTesting() {
        return stateFile;
    }

    File temporaryFileForTesting() {
        return temporaryFile;
    }

    File quarantineFileForTesting(int oneBasedIndex) {
        return new File(rootDirectory,
                        QUARANTINE_PREFIX + oneBasedIndex);
    }

    static int maximumFileBytesForTesting() {
        return MAX_FILE_BYTES;
    }

    static int storeMagicForTesting() {
        return STORE_MAGIC;
    }

    static int storeVersionForTesting() {
        return STORE_VERSION;
    }

    static void clearForTesting(Context context) {
        File root = new File(requireFilesDirectory(context), ROOT_DIRECTORY);
        deleteOwnedForTesting(new File(root, STATE_FILE));
        deleteOwnedForTesting(new File(root, TEMPORARY_FILE));
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            deleteOwnedForTesting(new File(
                root, QUARANTINE_PREFIX + index));
        }
        File[] remaining = root.listFiles();
        if (remaining != null && remaining.length == 0) {
            root.delete();
        }
    }

    private MutationResult decide(
        Candidate candidate,
        OctavoAppearance exactSuccessfullyPresentedOrigin,
        Decision decision) {
        MutationResult ready = requireMutableAppearance(
            exactSuccessfullyPresentedOrigin);
        if (ready != null) {
            return ready;
        }
        if (decision != Decision.KEPT
            && decision != Decision.DISMISSED_AT_EPOCH) {
            return fail(MutationResult.INVALID,
                        "Invalid appearance decision.");
        }
        if (current.pending != null) {
            return fail(MutationResult.BLOCKED,
                        "An appearance transaction is pending.");
        }
        LaneState remote = candidateLane(candidate);
        OctavoAppearancePortable.Lane local = localLaneInternal();
        if (remote == null || local == null
            || !candidateMatches(candidate, remote, local)
            || !local.profile.equals(
                profile(exactSuccessfullyPresentedOrigin))) {
            return fail(MutationResult.CONFLICT,
                        "The appearance offer is stale.");
        }
        if (!(remote.decision == Decision.NONE
              || (remote.decision == Decision.DISMISSED_AT_EPOCH
                  && remote.decisionEpoch < current.reviewEpoch))) {
            return fail(MutationResult.CONFLICT,
                        "The appearance offer was already decided.");
        }
        long epoch = decision == Decision.DISMISSED_AT_EPOCH
            ? current.reviewEpoch : 0;
        TreeMap<String, LaneState> lanes = new TreeMap<>(current.lanes);
        lanes.put(remote.lane.deviceId,
                  remote.withDecision(decision, epoch));
        return publish(current.withLanes(lanes));
    }

    private PortableMergeResult retainFuture(byte[] bytes) {
        if (bytes == null
            || bytes.length > OctavoAppearancePortable.maximumFutureBytes()) {
            lastError = "Future appearance bytes exceed their limit.";
            return PortableMergeResult.LIMIT;
        }
        if (current.futureBytes == null) {
            MutationResult result = publish(current.withFuture(
                bytes, ATTENTION_FUTURE_RETAINED));
            if (result == MutationResult.UPDATED) {
                return PortableMergeResult.FUTURE_RETAINED;
            }
            return mergePublicationResult(result);
        }
        if (Arrays.equals(current.futureBytes, bytes)) {
            lastError = attentionMessage(current.attention);
            return PortableMergeResult.UNCHANGED;
        }
        State attention = current.attention == ATTENTION_FUTURE_CONFLICT
            ? current : current.withAttention(ATTENTION_FUTURE_CONFLICT);
        if (attention == current) {
            lastError = attentionMessage(ATTENTION_FUTURE_CONFLICT);
            return PortableMergeResult.FUTURE_CONFLICT;
        }
        MutationResult result = publish(attention);
        if (result == MutationResult.UPDATED) {
            return PortableMergeResult.FUTURE_CONFLICT;
        }
        return mergePublicationResult(result);
    }

    private MutationResult requireMutable() {
        if (!loadAttempted) {
            return fail(MutationResult.BLOCKED,
                        "Appearance sync state has not been loaded.");
        }
        if (mutationsBlocked) {
            return fail(MutationResult.BLOCKED,
                        "Appearance sync state is blocked.");
        }
        return null;
    }

    private MutationResult requireMutableAppearance(
        OctavoAppearance appearance) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (appearance == null) {
            return fail(MutationResult.INVALID,
                        "The appearance is invalid.");
        }
        try {
            profile(appearance);
        } catch (IllegalArgumentException exception) {
            return fail(MutationResult.INVALID,
                        "The appearance is invalid.");
        }
        return null;
    }

    private MutationResult publish(State candidate) {
        byte[] previous = null;
        boolean previousExists = stateFile.isFile();
        boolean candidateEncoded = false;
        boolean destinationVerified = false;
        boolean moveAttempted = false;
        try {
            byte[] bytes = encodeState(candidate);
            candidateEncoded = true;
            if (bytes.length > MAX_FILE_BYTES) {
                return fail(MutationResult.LIMIT,
                            "Appearance sync state exceeds its byte limit.");
            }
            if (previousExists != stateExpectedOnDisk) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Appearance sync destination presence changed; reload is required.");
            }
            if (previousExists) {
                previous = readBounded(stateFile);
                byte[] expected = encodeState(current);
                if (!Arrays.equals(previous, expected)) {
                    mutationsBlocked = true;
                    loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                    return fail(
                        MutationResult.PUBLISH_UNCERTAIN,
                        "Appearance sync state changed unexpectedly; reload is required.");
                }
                destinationVerified = true;
            } else if (stateFile.exists()) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Appearance sync destination is not a regular file.");
            } else {
                destinationVerified = true;
            }
            requireDirectory(rootDirectory);
            if (failNextPublishForTesting) {
                failNextPublishForTesting = false;
                throw new IOException("Injected O1SS publish failure");
            }
            try (FileOutputStream output =
                     new FileOutputStream(temporaryFile, false)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            moveAttempted = true;
            Files.move(temporaryFile.toPath(), stateFile.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            if (failNextMoveAfterReplaceForTesting) {
                failNextMoveAfterReplaceForTesting = false;
                throw new IOException(
                    "Injected uncertain O1SS replace result");
            }
            current = candidate;
            stateExpectedOnDisk = true;
            lastError = attentionMessage(candidate.attention);
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            if (candidateEncoded && !destinationVerified) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Appearance sync destination could not be verified; reload is required.");
            }
            if (moveAttempted
                && !destinationStillEquals(previousExists, previous)) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Appearance sync publication is uncertain; reload is required.");
            }
            return fail(MutationResult.PUBLISH_FAILED,
                        "Appearance sync state could not be saved. Retry.");
        }
    }

    private boolean destinationStillEquals(boolean previousExists,
                                           byte[] previous) {
        try {
            if (!previousExists) {
                return !stateFile.exists();
            }
            return stateFile.isFile()
                && Arrays.equals(previous, readBounded(stateFile));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private MutationResult unchanged() {
        lastError = attentionMessage(current.attention);
        return MutationResult.UNCHANGED;
    }

    private MutationResult fail(MutationResult result, String message) {
        lastError = message;
        return result;
    }

    private PortableMergeResult mergePublicationResult(
        MutationResult result) {
        switch (result) {
            case UPDATED: return PortableMergeResult.MERGED;
            case PUBLISH_UNCERTAIN:
                return PortableMergeResult.PUBLISH_UNCERTAIN;
            case PUBLISH_FAILED:
                return PortableMergeResult.PUBLISH_FAILED;
            case LIMIT: return PortableMergeResult.LIMIT;
            case BLOCKED: return PortableMergeResult.BLOCKED;
            default: return PortableMergeResult.INVALID;
        }
    }

    private LaneState candidateLane(Candidate candidate) {
        return candidate == null ? null
            : current.lanes.get(candidate.deviceId);
    }

    private boolean candidateMatches(Candidate candidate,
                                     LaneState remote,
                                     OctavoAppearancePortable.Lane local) {
        return candidate != null && remote != null && local != null
            && candidate.reviewEpoch == current.reviewEpoch
            && candidate.sequence == remote.lane.sequence
            && candidate.deviceId.equals(remote.lane.deviceId)
            && candidate.originLocalSequence == local.sequence
            && candidate.targetProfile.equals(remote.lane.profile)
            && candidate.originProfile.equals(local.profile);
    }

    private static boolean pendingMatches(PendingState current,
                                          Pending expected) {
        return current != null && expected != null
            && current.kind == expected.kind
            && current.hasOriginLane == expected.hasOriginLane
            && current.originLocalSequence
               == expected.originLocalSequence
            && current.localSequence == expected.localSequence
            && current.remoteDeviceId.equals(expected.remoteDeviceId)
            && current.remoteSequence == expected.remoteSequence
            && current.originProfile.equals(expected.originProfile)
            && current.targetProfile.equals(expected.targetProfile);
    }

    private boolean validForwardProof(PendingState pending,
                                      O7stProof proof) {
        if (proof == O7stProof.CURRENT_PROCESS_ATOMIC_SAVE
            || proof
               == O7stProof.CURRENT_PROCESS_RECONCILED_AFTER_UNCERTAIN_SAVE) {
            return true;
        }
        return proof == O7stProof.CANONICAL_V3_LOAD
            && (pendingLoadedFromDisk
                || (pending.kind == PendingKind.LOCAL
                    && !pending.hasOriginLane));
    }

    private OctavoAppearancePortable.Lane localLaneInternal() {
        LaneState local = current.lanes.get(current.deviceId);
        return local == null ? null : local.lane;
    }

    private static long nextSequence(
        OctavoAppearancePortable.Lane local) {
        if (local == null) {
            return 1;
        }
        return local.sequence == Long.MAX_VALUE
            ? -1 : local.sequence + 1;
    }

    private static OctavoAppearancePortable.Profile profile(
        OctavoAppearance appearance) {
        return OctavoAppearancePortable.Profile.fromAppearance(appearance);
    }

    private static OctavoAppearancePortable.Snapshot snapshot(State state) {
        ArrayList<OctavoAppearancePortable.Lane> lanes =
            new ArrayList<>(state.lanes.size());
        for (LaneState lane : state.lanes.values()) {
            lanes.add(lane.lane);
        }
        return new OctavoAppearancePortable.Snapshot(lanes);
    }

    private static State emptyState(String deviceId) {
        return new State(deviceId, 0, Collections.emptyMap(),
                         null, null, ATTENTION_NONE);
    }

    private static byte[] encodeState(State state) throws IOException {
        if (!validState(state)) {
            throw new IOException("Invalid O1SS state");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(STORE_MAGIC);
            output.writeInt(STORE_VERSION);
            output.writeInt(STORE_HEADER_FIELD_COUNT);
            writeDeviceId(output, state.deviceId);
            output.writeLong(state.reviewEpoch);
            output.writeInt(state.attention);
            output.writeInt(state.lanes.size());
            String previous = null;
            for (LaneState lane : state.lanes.values()) {
                if (previous != null
                    && previous.compareTo(lane.lane.deviceId) >= 0) {
                    throw new IOException("Noncanonical O1SS lane order");
                }
                writeLaneState(output, lane);
                previous = lane.lane.deviceId;
            }
            output.writeInt(state.pending == null ? 0 : 1);
            if (state.pending != null) {
                writePending(output, state.pending);
            }
            if (state.futureBytes == null) {
                output.writeInt(0);
            } else {
                output.writeInt(state.futureBytes.length);
                output.write(state.futureBytes);
            }
            output.flush();
        }
        byte[] payload = payloadBytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(payload, 0, payload.length);
        ByteArrayOutputStream resultBytes =
            new ByteArrayOutputStream(payload.length + Integer.BYTES);
        try (DataOutputStream output =
                 new DataOutputStream(resultBytes)) {
            output.write(payload);
            output.writeInt((int)checksum.getValue());
            output.flush();
        }
        byte[] result = resultBytes.toByteArray();
        if (result.length <= 0 || result.length > MAX_FILE_BYTES) {
            throw new IOException("O1SS state exceeds its bound");
        }
        return result;
    }

    private static State decodeState(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < minimumStateBytes()
            || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid O1SS byte length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, payloadLength);
        if (readInt(bytes, payloadLength)
            != (int)checksum.getValue()) {
            throw new IOException("Invalid O1SS checksum");
        }
        try {
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != STORE_MAGIC
                || input.readInt() != STORE_VERSION
                || input.readInt() != STORE_HEADER_FIELD_COUNT) {
                throw new IOException("Invalid O1SS header");
            }
            String deviceId = readDeviceId(input);
            long epoch = input.readLong();
            int attention = input.readInt();
            int laneCount = input.readInt();
            if (epoch < 0 || !validAttention(attention)
                || laneCount < 0
                || laneCount
                   > OctavoAppearancePortable.maximumLaneCount()) {
                throw new IOException("Invalid O1SS bounds");
            }
            TreeMap<String, LaneState> lanes = new TreeMap<>();
            String previous = null;
            for (int index = 0; index < laneCount; ++index) {
                LaneState lane = readLaneState(input);
                if (previous != null
                    && previous.compareTo(lane.lane.deviceId) >= 0) {
                    throw new IOException("Noncanonical O1SS lanes");
                }
                lanes.put(lane.lane.deviceId, lane);
                previous = lane.lane.deviceId;
            }
            int pendingPresent = input.readInt();
            if (pendingPresent < 0 || pendingPresent > 1) {
                throw new IOException("Invalid O1SS pending flag");
            }
            PendingState pending = pendingPresent == 0
                ? null : readPending(input);
            int futureLength = input.readInt();
            if (futureLength < 0
                || futureLength
                   > OctavoAppearancePortable.maximumFutureBytes()) {
                throw new IOException("Invalid O1SS future length");
            }
            byte[] future = null;
            if (futureLength > 0) {
                future = new byte[futureLength];
                input.readFully(future);
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1SS payload");
            }
            State state = new State(deviceId, epoch, lanes, pending,
                                    future, attention);
            if (!validState(state)) {
                throw new IOException("Invalid O1SS state");
            }
            return state;
        } catch (EOFException exception) {
            throw new IOException("Truncated O1SS state", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid O1SS scalar", exception);
        }
    }

    private static boolean validState(State state) {
        if (state == null
            || !OctavoAppearancePortable.validDeviceId(state.deviceId)
            || state.reviewEpoch < 0
            || state.lanes.size()
               > OctavoAppearancePortable.maximumLaneCount()
            || !validAttention(state.attention)
            || (state.futureBytes != null
                && !recognizableFuture(state.futureBytes))) {
            return false;
        }
        if ((state.futureBytes == null)
            != (state.attention == ATTENTION_NONE)) {
            return false;
        }
        LaneState local = state.lanes.get(state.deviceId);
        for (Map.Entry<String, LaneState> entry : state.lanes.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().lane.deviceId)
                || !validLaneState(
                    entry.getValue(), state.deviceId,
                    state.reviewEpoch)) {
                return false;
            }
            if (local == null
                && entry.getValue().decision != Decision.NONE) {
                return false;
            }
        }
        return validPending(state.pending, local, state.lanes,
                            state.reviewEpoch);
    }

    private static boolean validLaneState(LaneState lane,
                                          String localDeviceId,
                                          long reviewEpoch) {
        if (lane == null || lane.lane == null || lane.decision == null
            || !OctavoAppearancePortable.validDeviceId(
                lane.lane.deviceId)
            || lane.lane.sequence <= 0 || lane.lane.profile == null) {
            return false;
        }
        if (lane.lane.deviceId.equals(localDeviceId)) {
            return lane.decision == Decision.NONE
                && lane.decisionEpoch == 0;
        }
        if (lane.decision == Decision.DISMISSED_AT_EPOCH) {
            return lane.decisionEpoch > 0
                && lane.decisionEpoch <= reviewEpoch;
        }
        if (lane.decision == Decision.KEPT && reviewEpoch <= 0) {
            return false;
        }
        return lane.decisionEpoch == 0;
    }

    private static boolean validPending(
        PendingState pending,
        LaneState local,
        Map<String, LaneState> lanes,
        long reviewEpoch) {
        if (pending == null) {
            return true;
        }
        if (pending.kind == null || pending.originProfile == null
            || pending.targetProfile == null
            || !OctavoAppearancePortable.validDeviceId(
                pending.remoteDeviceId)
            || pending.localSequence <= 0) {
            return false;
        }
        if (pending.hasOriginLane) {
            if (local == null
                || pending.originLocalSequence != local.lane.sequence
                || !pending.originProfile.equals(local.lane.profile)
                || pending.targetProfile.equals(pending.originProfile)
                || pending.localSequence
                   != nextSequence(local.lane)) {
                return false;
            }
        } else if (local != null || pending.originLocalSequence != 0
                   || pending.localSequence != 1
                   || pending.kind != PendingKind.LOCAL
                   || lanes.size()
                      >= OctavoAppearancePortable.maximumLaneCount()
                   || !pending.originProfile.equals(
                       pending.targetProfile)) {
            return false;
        }
        if (pending.kind == PendingKind.LOCAL) {
            return pending.remoteDeviceId.equals(ZERO_DEVICE_ID)
                && pending.remoteSequence == 0;
        }
        if (reviewEpoch <= 0 || !pending.hasOriginLane
            || pending.remoteDeviceId.equals(local.lane.deviceId)
            || pending.remoteSequence <= 0) {
            return false;
        }
        LaneState remote = lanes.get(pending.remoteDeviceId);
        if (remote == null) {
            return false;
        }
        if (remote.lane.sequence > pending.remoteSequence) {
            return remote.decision == Decision.NONE;
        }
        return remote.lane.sequence == pending.remoteSequence
            && remote.lane.profile.equals(pending.targetProfile)
            && reviewable(remote, reviewEpoch);
    }

    private static boolean reviewable(LaneState lane, long reviewEpoch) {
        return lane != null && (lane.decision == Decision.NONE
            || (lane.decision == Decision.DISMISSED_AT_EPOCH
                && lane.decisionEpoch < reviewEpoch));
    }

    private static void writeLaneState(DataOutputStream output,
                                       LaneState lane)
        throws IOException {
        writeDeviceId(output, lane.lane.deviceId);
        output.writeLong(lane.lane.sequence);
        writeProfile(output, lane.lane.profile);
        output.writeInt(lane.decision.wireId);
        output.writeLong(lane.decisionEpoch);
    }

    private static LaneState readLaneState(DataInputStream input)
        throws IOException {
        String deviceId = readDeviceId(input);
        long sequence = input.readLong();
        OctavoAppearancePortable.Profile profile = readProfile(input);
        Decision decision = Decision.fromWireId(input.readInt());
        long decisionEpoch = input.readLong();
        if (decision == null) {
            throw new IOException("Invalid O1SS decision");
        }
        return new LaneState(
            new OctavoAppearancePortable.Lane(
                deviceId, sequence, profile),
            decision, decisionEpoch);
    }

    private static void writePending(DataOutputStream output,
                                     PendingState pending)
        throws IOException {
        output.writeInt(pending.kind.wireId);
        output.writeInt(pending.hasOriginLane ? 1 : 0);
        output.writeLong(pending.originLocalSequence);
        writeProfile(output, pending.originProfile);
        output.writeLong(pending.localSequence);
        writeProfile(output, pending.targetProfile);
        writeDeviceId(output, pending.remoteDeviceId);
        output.writeLong(pending.remoteSequence);
    }

    private static PendingState readPending(DataInputStream input)
        throws IOException {
        PendingKind kind = PendingKind.fromWireId(input.readInt());
        int originPresent = input.readInt();
        long originSequence = input.readLong();
        OctavoAppearancePortable.Profile origin = readProfile(input);
        long localSequence = input.readLong();
        OctavoAppearancePortable.Profile target = readProfile(input);
        String remoteDevice = readDeviceId(input);
        long remoteSequence = input.readLong();
        if (kind == null || originPresent < 0 || originPresent > 1) {
            throw new IOException("Invalid O1SS pending transaction");
        }
        return new PendingState(
            kind, originPresent == 1, originSequence, origin,
            localSequence, target, remoteDevice, remoteSequence);
    }

    private static void writeProfile(
        DataOutputStream output,
        OctavoAppearancePortable.Profile profile) throws IOException {
        output.writeInt(profile.theme);
        output.writeInt(profile.fontIntent);
        output.writeInt(profile.textSizeTier);
        output.writeInt(profile.lineSpacingTier);
        output.writeInt(profile.width);
        output.writeInt(profile.alignment);
        output.writeInt(profile.publisherColors);
        output.writeInt(profile.reducedMotion);
    }

    private static OctavoAppearancePortable.Profile readProfile(
        DataInputStream input) throws IOException {
        try {
            return new OctavoAppearancePortable.Profile(
                input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid O1SS appearance", exception);
        }
    }

    private static void writeDeviceId(DataOutputStream output,
                                      String deviceId)
        throws IOException {
        byte[] bytes = deviceId.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != DEVICE_ID_BYTES
            || !OctavoAppearancePortable.validDeviceId(deviceId)) {
            throw new IOException("Invalid O1SS device identity");
        }
        output.write(bytes);
    }

    private static String readDeviceId(DataInputStream input)
        throws IOException {
        byte[] bytes = new byte[DEVICE_ID_BYTES];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (!OctavoAppearancePortable.validDeviceId(result)) {
            throw new IOException("Invalid O1SS device identity");
        }
        return result;
    }

    private static boolean validAttention(int value) {
        return value == ATTENTION_NONE
            || value == ATTENTION_FUTURE_RETAINED
            || value == ATTENTION_FUTURE_CONFLICT;
    }

    private static String attentionMessage(int value) {
        if (value == ATTENTION_FUTURE_RETAINED) {
            return "Newer portable appearance bytes were retained for review.";
        }
        if (value == ATTENTION_FUTURE_CONFLICT) {
            return "Different newer portable appearance bytes require attention.";
        }
        return "";
    }

    private static boolean recognizableFuture(byte[] bytes) {
        if (bytes == null || bytes.length < 2 * Integer.BYTES
            || bytes.length
               > OctavoAppearancePortable.maximumFutureBytes()) {
            return false;
        }
        return readInt(bytes, 0)
                == OctavoAppearancePortable.magicForTesting()
            && Integer.compareUnsigned(
                   readInt(bytes, Integer.BYTES),
                   OctavoAppearancePortable.versionForTesting()) > 0;
    }

    private static int minimumStateBytes() {
        return 3 * Integer.BYTES + DEVICE_ID_BYTES
            + Long.BYTES + 4 * Integer.BYTES + Integer.BYTES;
    }

    private static boolean isFutureStore(byte[] bytes) {
        return bytes != null && bytes.length >= 2 * Integer.BYTES
            && readInt(bytes, 0) == STORE_MAGIC
            && Integer.compareUnsigned(
                   readInt(bytes, Integer.BYTES), STORE_VERSION) > 0;
    }

    private boolean quarantineCorruptState() {
        deleteTemporaryBestEffort();
        try {
            requireDirectory(rootDirectory);
            for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
                File quarantine = quarantineFileForTesting(index);
                if (quarantine.exists()) {
                    continue;
                }
                Files.move(stateFile.toPath(), quarantine.toPath(),
                           StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
        return false;
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0
            || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Invalid O1SS file length");
        }
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
            if (offset != result.length || input.read() != -1) {
                throw new IOException("O1SS file changed while reading");
            }
        }
        return result;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context application = context.getApplicationContext();
        return (application == null ? context : application).getFilesDir();
    }

    private static void requireDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(
                "Unable to create appearance synchronization directory");
        }
    }

    private void deleteTemporaryBestEffort() {
        if (temporaryFile.isFile()) {
            temporaryFile.delete();
        }
    }

    private static String randomDeviceId(SecureRandom random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder result = new StringBuilder(DEVICE_ID_BYTES);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static void deleteOwnedForTesting(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException(
                "Unable to clear appearance sync test state");
        }
    }
}
