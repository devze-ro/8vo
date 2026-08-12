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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.CRC32;

/**
 * Android-private O1MS authority for the provider-neutral O1LM overlay.
 *
 * This store owns no catalog, EPUB, provider, account, transfer, annotation,
 * position, appearance, or progress state. Portable input remains inert until
 * it is explicitly approved against the exact snapshot on which it was
 * staged.
 */
final class OctavoLibraryMembershipStore {
    enum LoadStatus {
        MISSING_EMPTY,
        LOADED,
        LOADED_QUARANTINE_ATTENTION,
        CORRUPT_QUARANTINED_BLOCKED,
        CORRUPT_BLOCKED,
        OVERBOUND_BLOCKED,
        FUTURE_VERSION_BLOCKED,
        PUBLISH_UNCERTAIN_BLOCKED
    }

    enum MutationResult {
        UPDATED,
        UNCHANGED,
        INVALID,
        CONFLICT,
        BLOCKED,
        LIMIT,
        EXHAUSTED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == UPDATED || this == UNCHANGED;
        }
    }

    enum PortableStageResult {
        STAGED_CURRENT,
        UNCHANGED,
        LIMIT_RETAINED,
        FUTURE_RETAINED,
        STAGED_CONFLICT,
        INVALID,
        LIMIT,
        EQUIVOCATION,
        EXHAUSTED,
        BLOCKED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == STAGED_CURRENT || this == UNCHANGED
                || this == LIMIT_RETAINED
                || this == FUTURE_RETAINED;
        }
    }

    enum PortableApprovalResult {
        MERGED,
        UNCHANGED,
        STALE_RECEIPT,
        STALE_BASE,
        STAGED_CONFLICT,
        LIMIT_RETAINED,
        FUTURE_VERSION_BLOCKED,
        NO_STAGED_INPUT,
        INVALID,
        EQUIVOCATION,
        LIMIT,
        EXHAUSTED,
        BLOCKED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == MERGED || this == UNCHANGED;
        }
    }

    enum PortableDiscardResult {
        DISCARDED,
        UNCHANGED,
        STALE_RECEIPT,
        INVALID,
        BLOCKED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == DISCARDED || this == UNCHANGED;
        }
    }

    enum RecoveryResult {
        RECOVERED,
        INVALID,
        STALE_DIGEST,
        NOT_RECOVERABLE,
        ACTOR_UNAVAILABLE,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN
    }

    enum PortableExportStatus {
        EXPORTED,
        NOT_LOADED,
        BLOCKED,
        LOCAL_FAILURE
    }

    enum StagedKind {
        CURRENT(1),
        FUTURE(2);

        final int wireId;

        StagedKind(int wireId) {
            this.wireId = wireId;
        }

        static StagedKind fromWireId(int wireId) {
            for (StagedKind value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    enum Attention {
        NONE(0),
        CURRENT_APPROVAL(1),
        JOIN_LIMIT_RETAINED(2),
        FUTURE_RETAINED(3),
        STAGED_CONFLICT(4),
        STALE_BASE(5);

        final int wireId;

        Attention(int wireId) {
            this.wireId = wireId;
        }

        static Attention fromWireId(int wireId) {
            for (Attention value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    static final class MutationOutcome {
        final MutationResult result;
        final OctavoLibraryMembershipPortable.LimitScope limitScope;
        final OctavoLibraryMembershipPortable.LimitReason limitReason;

        private MutationOutcome(
            MutationResult result,
            OctavoLibraryMembershipPortable.LimitScope limitScope,
            OctavoLibraryMembershipPortable.LimitReason limitReason) {
            this.result = result;
            this.limitScope = limitScope;
            this.limitReason = limitReason;
        }

        boolean succeeded() {
            return result.succeeded();
        }
    }

    static final class StageOutcome {
        final PortableStageResult result;
        final OctavoLibraryMembershipPortable.LimitScope limitScope;
        final OctavoLibraryMembershipPortable.LimitReason limitReason;

        private StageOutcome(
            PortableStageResult result,
            OctavoLibraryMembershipPortable.LimitScope limitScope,
            OctavoLibraryMembershipPortable.LimitReason limitReason) {
            this.result = result;
            this.limitScope = limitScope;
            this.limitReason = limitReason;
        }

        boolean succeeded() {
            return result.succeeded();
        }
    }

    static final class Receipt {
        final String digest;
        final long byteCount;
        final int kind;
        final boolean recordPresent;
        final OctavoLibraryMembershipPortable.Projection projection;
        final String recordFingerprint;
        final String snapshotFingerprint;
        final long stateGeneration;
        private final OctavoLibraryMembershipPortable.Descriptor descriptor;
        private final byte[] authority;

        private Receipt(
            OctavoLibraryMembershipPortable.Descriptor descriptor,
            boolean recordPresent,
            OctavoLibraryMembershipPortable.Projection projection,
            String recordFingerprint,
            String snapshotFingerprint,
            long stateGeneration,
            byte[] authority) {
            this.descriptor = descriptor;
            digest = descriptor.digest;
            byteCount = descriptor.byteCount;
            kind = descriptor.kind;
            this.recordPresent = recordPresent;
            this.projection = projection;
            this.recordFingerprint = recordFingerprint;
            this.snapshotFingerprint = snapshotFingerprint;
            this.stateGeneration = stateGeneration;
            this.authority = authority.clone();
        }

        OctavoLibraryMembershipPortable.Descriptor descriptor() {
            return descriptor;
        }

        boolean sameIdentity(Receipt other) {
            return other != null
                && descriptor.sameIdentity(other.descriptor)
                && recordPresent == other.recordPresent
                && projection == other.projection
                && recordFingerprint.equals(other.recordFingerprint)
                && snapshotFingerprint.equals(other.snapshotFingerprint)
                && stateGeneration == other.stateGeneration;
        }
    }

    static final class StagedPortable {
        final StagedKind kind;
        final String sha256;
        final String baseSha256;
        final int byteCount;
        final long reviewEpoch;
        final Attention attention;
        final OctavoLibraryMembershipPortable.LimitScope limitScope;
        final OctavoLibraryMembershipPortable.LimitReason limitReason;
        private final byte[] bytes;
        private final byte[] authority;

        private StagedPortable(StagedState staged,
                               long reviewEpoch,
                               Attention attention,
                               LimitState limit,
                               byte[] authority) {
            kind = staged.kind;
            sha256 = lowerHex(staged.sha256);
            baseSha256 = lowerHex(staged.baseSha256);
            bytes = staged.bytes.clone();
            byteCount = bytes.length;
            this.reviewEpoch = reviewEpoch;
            this.attention = attention;
            limitScope = limit == null ? null : limit.scope;
            limitReason = limit == null ? null : limit.reason;
            this.authority = authority.clone();
        }

        byte[] bytes() {
            return bytes.clone();
        }

        boolean sameIdentity(StagedPortable other) {
            return other != null && kind == other.kind
                && sha256.equals(other.sha256)
                && baseSha256.equals(other.baseSha256)
                && reviewEpoch == other.reviewEpoch
                && attention == other.attention
                && limitScope == other.limitScope
                && limitReason == other.limitReason;
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

    private static final class LimitState {
        final OctavoLibraryMembershipPortable.LimitScope scope;
        final OctavoLibraryMembershipPortable.LimitReason reason;

        LimitState(OctavoLibraryMembershipPortable.LimitScope scope,
                   OctavoLibraryMembershipPortable.LimitReason reason) {
            this.scope = scope;
            this.reason = reason;
        }
    }

    private static final class StagedState {
        final StagedKind kind;
        final byte[] sha256;
        final byte[] baseSha256;
        final byte[] bytes;

        StagedState(StagedKind kind,
                    byte[] sha256,
                    byte[] baseSha256,
                    byte[] bytes) {
            this.kind = kind;
            this.sha256 = sha256.clone();
            this.baseSha256 = baseSha256.clone();
            this.bytes = bytes.clone();
        }
    }

    private static final class State {
        final String actorId;
        final long counter;
        final long stateGeneration;
        final long reviewEpoch;
        final OctavoLibraryMembershipPortable.Snapshot snapshot;
        final StagedState staged;
        final Attention attention;
        final LimitState limit;

        State(String actorId,
              long counter,
              long stateGeneration,
              long reviewEpoch,
              OctavoLibraryMembershipPortable.Snapshot snapshot,
              StagedState staged,
              Attention attention,
              LimitState limit) {
            this.actorId = actorId;
            this.counter = counter;
            this.stateGeneration = stateGeneration;
            this.reviewEpoch = reviewEpoch;
            this.snapshot = snapshot;
            this.staged = staged;
            this.attention = attention;
            this.limit = limit;
        }

        State withStaged(long nextEpoch,
                         StagedState value,
                         Attention nextAttention,
                         LimitState nextLimit) {
            return new State(actorId, counter, stateGeneration, nextEpoch,
                             snapshot, value, nextAttention, nextLimit);
        }

        State withStagedAttention(Attention value) {
            return new State(actorId, counter, stateGeneration, reviewEpoch,
                             snapshot, staged, value, limit);
        }

        State withSnapshot(String nextActor,
                           long nextCounter,
                           long nextGeneration,
                           OctavoLibraryMembershipPortable.Snapshot value) {
            boolean stale = staged != null;
            return new State(nextActor, nextCounter, nextGeneration,
                             reviewEpoch, value, staged,
                             stale ? Attention.STALE_BASE : attention,
                             stale ? null : limit);
        }

        State approved(String nextActor,
                       long nextCounter,
                       long nextGeneration,
                       OctavoLibraryMembershipPortable.Snapshot value) {
            return new State(nextActor, nextCounter, nextGeneration,
                             reviewEpoch, value, null, Attention.NONE, null);
        }

        State withoutStaged() {
            return new State(actorId, counter, stateGeneration, reviewEpoch,
                             snapshot, null, Attention.NONE, null);
        }
    }

    private static final int STORE_MAGIC = 0x4F314D53; // "O1MS"
    private static final int STORE_VERSION = 1;
    private static final int STORE_FIELD_COUNT = 8;
    private static final int MAX_STATE_BYTES = 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 8 * 1024;
    private static final int MIN_CURRENT_BYTES = 20;
    private static final int MAX_CURRENT_BYTES = 447_572;
    private static final int MAX_PORTABLE_BYTES = 524_244;
    private static final int HASH_BYTES = 32;
    private static final int ACTOR_BYTES = 32;
    private static final int QUARANTINE_SLOTS = 3;
    private static final int ACTOR_ATTEMPTS = 64;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "library-membership.v1";
    private static final String TEMPORARY_FILE =
        "library-membership.v1.tmp";
    private static final String LOCK_FILE =
        "library-membership.v1.lock";
    private static final String QUARANTINE_PREFIX =
        "library-membership.corrupt.";

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private final File lockFile;
    private final SecureRandom random;
    private State current;
    private LoadStatus loadStatus = LoadStatus.MISSING_EMPTY;
    private boolean loadAttempted;
    private boolean mutationsBlocked;
    private boolean stateExpectedOnDisk;
    private boolean canonicalDestinationObserved;
    private boolean failNextPublishForTesting;
    private boolean failNextMoveAfterReplaceForTesting;
    private byte[] replaceDestinationAfterTempSyncForTesting;
    private String lastError = "";
    private byte[] receiptAuthority = randomAuthority();
    private byte[] stagedAuthority = randomAuthority();

    private boolean uncertainPriorExists;
    private byte[] uncertainPriorBytes;
    private byte[] uncertainCandidateBytes;

    OctavoLibraryMembershipStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoLibraryMembershipStore(File filesDirectory) {
        this(filesDirectory, new SecureRandom(), null);
    }

    OctavoLibraryMembershipStore(File filesDirectory,
                                 String actorIdForTesting) {
        this(filesDirectory, new SecureRandom(), actorIdForTesting);
    }

    private OctavoLibraryMembershipStore(File filesDirectory,
                                         SecureRandom random,
                                         String actorId) {
        if (filesDirectory == null || random == null
            || (actorId != null && !validActorId(actorId))) {
            throw new IllegalArgumentException(
                "Invalid Library membership store");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
        lockFile = new File(rootDirectory, LOCK_FILE);
        this.random = random;
        String fresh = actorId == null ? randomActorId(random) : actorId;
        current = emptyState(fresh);
    }

    synchronized LoadStatus load() {
        boolean hadDestinationObservation =
            canonicalDestinationObserved;
        loadAttempted = true;
        refreshAuthorities();
        if (uncertainCandidateBytes != null) {
            return reconcileUncertainLoad();
        }
        mutationsBlocked = false;
        if (hadDestinationObservation) {
            if (stateExpectedOnDisk) {
                byte[] expected = safeCanonicalCurrentBytes();
                try {
                    if (expected == null || !stateFile.isFile()
                        || stateFile.length() > MAX_STATE_BYTES
                        || !Arrays.equals(
                            expected, readBounded(stateFile))) {
                        uncertain(
                            true, expected, expected == null
                                ? new byte[0] : expected,
                            "Library membership destination changed after it was observed; exact reload is required.");
                        return loadStatus;
                    }
                } catch (IOException | RuntimeException exception) {
                    uncertain(
                        true, expected, expected == null
                            ? new byte[0] : expected,
                        "Library membership destination could not be reverified exactly.");
                    return loadStatus;
                }
            } else if (stateFile.exists()) {
                uncertain(
                    false, null, new byte[0],
                    "Library membership destination appeared after exact absence was observed.");
                return loadStatus;
            }
        }
        if (!stateFile.exists()) {
            if (stateExpectedOnDisk) {
                return block(
                    LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
                    "Expected Library membership state is missing; exact reload recovery is required.");
            }
            current = emptyState(current.actorId);
            stateExpectedOnDisk = false;
            if (hasQuarantineEvidence()) {
                canonicalDestinationObserved = false;
                return block(
                    LoadStatus.CORRUPT_QUARANTINED_BLOCKED,
                    "Quarantined Library membership bytes require explicit reviewed recovery.");
            }
            loadStatus = LoadStatus.MISSING_EMPTY;
            canonicalDestinationObserved = true;
            lastError = "";
            return loadStatus;
        }
        if (!stateFile.isFile()) {
            stateExpectedOnDisk = true;
            canonicalDestinationObserved = false;
            return block(
                LoadStatus.CORRUPT_BLOCKED,
                "Library membership state is not a regular file and was preserved.");
        }
        if (stateFile.length() > MAX_STATE_BYTES) {
            stateExpectedOnDisk = true;
            canonicalDestinationObserved = false;
            return block(
                LoadStatus.OVERBOUND_BLOCKED,
                "Library membership state exceeds 1 MiB and was preserved.");
        }
        byte[] observedBytes = null;
        try {
            byte[] bytes = readBounded(stateFile);
            observedBytes = bytes;
            if (isFutureStore(bytes)) {
                stateExpectedOnDisk = true;
                canonicalDestinationObserved = false;
                return block(
                    LoadStatus.FUTURE_VERSION_BLOCKED,
                    "Library membership state was written by a newer version.");
            }
            current = decodeState(bytes);
            stateExpectedOnDisk = true;
            canonicalDestinationObserved = true;
            mutationsBlocked = false;
            if (hasQuarantineEvidence()) {
                loadStatus = LoadStatus.LOADED_QUARANTINE_ATTENTION;
                lastError =
                    "Quarantined Library membership evidence remains for attention.";
            } else {
                loadStatus = LoadStatus.LOADED;
                lastError = attentionMessage(current);
            }
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState(observedBytes)) {
                stateExpectedOnDisk = false;
                canonicalDestinationObserved = false;
                mutationsBlocked = true;
                current = emptyState(current.actorId);
                loadStatus = LoadStatus.CORRUPT_QUARANTINED_BLOCKED;
                lastError =
                    "Corrupt Library membership bytes were quarantined; explicit reviewed recovery is required.";
            } else {
                stateExpectedOnDisk = true;
                canonicalDestinationObserved = false;
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "Corrupt Library membership bytes could not be quarantined and were preserved.";
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

    synchronized String actorId() {
        return current.actorId;
    }

    synchronized long counter() {
        return current.counter;
    }

    synchronized long stateGeneration() {
        return current.stateGeneration;
    }

    synchronized long reviewEpoch() {
        return current.reviewEpoch;
    }

    synchronized Attention attention() {
        return current.attention;
    }

    synchronized OctavoLibraryMembershipPortable.LimitScope limitScope() {
        return current.limit == null ? null : current.limit.scope;
    }

    synchronized OctavoLibraryMembershipPortable.LimitReason limitReason() {
        return current.limit == null ? null : current.limit.reason;
    }

    synchronized OctavoLibraryMembershipPortable.Snapshot snapshot() {
        return current.snapshot;
    }

    synchronized OctavoLibraryMembershipPortable.Projection projection(
        String digest) {
        if (!loadAttempted || mutationsBlocked
            || !validLowerHex(digest, 64)) {
            return null;
        }
        return current.snapshot.projection(digest);
    }

    synchronized StagedPortable stagedPortable() {
        return current.staged == null ? null
            : new StagedPortable(current.staged, current.reviewEpoch,
                                 current.attention, current.limit,
                                 stagedAuthority);
    }

    synchronized Receipt receipt(
        OctavoLibraryMembershipPortable.Descriptor descriptor) {
        if (!loadAttempted || mutationsBlocked
            || !validDescriptor(descriptor)) {
            lastError = !loadAttempted || mutationsBlocked
                ? "Library membership state is blocked."
                : "The Library membership descriptor is invalid.";
            return null;
        }
        try {
            Receipt result = createReceipt(descriptor);
            if (result == null) {
                lastError =
                    "The Library membership descriptor is equivocal.";
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            lastError =
                "A Library membership receipt could not be created.";
            return null;
        }
    }

    synchronized MutationOutcome withdraw(Receipt expected) {
        return mutate(expected,
                      OctavoLibraryMembershipPortable.Operation.WITHDRAW,
                      false);
    }

    synchronized MutationOutcome restore(Receipt expected) {
        return mutate(expected,
                      OctavoLibraryMembershipPortable.Operation.RESTORE,
                      false);
    }

    synchronized MutationOutcome resolveConflict(
        Receipt expected,
        OctavoLibraryMembershipPortable.Projection target) {
        if (target != OctavoLibraryMembershipPortable.Projection.MEMBER
            && target
               != OctavoLibraryMembershipPortable.Projection.WITHDRAWN) {
            return mutation(
                MutationResult.INVALID, null, null,
                "The Library membership conflict target is invalid.");
        }
        return mutate(
            expected,
            target == OctavoLibraryMembershipPortable.Projection.MEMBER
                ? OctavoLibraryMembershipPortable.Operation.RESTORE
                : OctavoLibraryMembershipPortable.Operation.WITHDRAW,
            true);
    }

    synchronized StageOutcome stagePortableBytes(byte[] bytes) {
        if (!loadAttempted || mutationsBlocked) {
            lastError = "Library membership state is blocked.";
            return stage(PortableStageResult.BLOCKED, null, null);
        }
        if (bytes == null || bytes.length < 2 * Integer.BYTES) {
            lastError = "Portable Library membership bytes are invalid.";
            return stage(PortableStageResult.INVALID, null, null);
        }
        if (bytes.length > MAX_PORTABLE_BYTES) {
            lastError = "Portable Library membership bytes exceed their cap.";
            return stage(
                PortableStageResult.LIMIT,
                OctavoLibraryMembershipPortable.LimitScope.INPUT,
                OctavoLibraryMembershipPortable.LimitReason.ENCODED_BYTES);
        }
        byte[] exact = bytes.clone();
        OctavoLibraryMembershipPortable.DecodeResult decoded =
            OctavoLibraryMembershipPortable.decode(exact);
        StagedKind kind;
        if (decoded.status
            == OctavoLibraryMembershipPortable.DecodeStatus.FUTURE_VERSION) {
            kind = StagedKind.FUTURE;
        } else if (decoded.status
                   == OctavoLibraryMembershipPortable.DecodeStatus.LIMIT) {
            lastError = "Portable Library membership input reached a typed limit.";
            return stage(
                PortableStageResult.LIMIT,
                decoded.limitScope, decoded.limitReason);
        } else if (decoded.status
                   != OctavoLibraryMembershipPortable.DecodeStatus.READY) {
            lastError = "Portable Library membership bytes are invalid.";
            return stage(PortableStageResult.INVALID, null, null);
        } else {
            kind = StagedKind.CURRENT;
        }

        if (current.staged != null) {
            if (Arrays.equals(current.staged.bytes, exact)) {
                lastError = attentionMessage(current);
                return stage(PortableStageResult.UNCHANGED,
                             current.limit == null
                                ? null : current.limit.scope,
                             current.limit == null
                                ? null : current.limit.reason);
            }
            if (current.attention == Attention.STAGED_CONFLICT) {
                lastError = attentionMessage(current);
                return stage(PortableStageResult.STAGED_CONFLICT,
                             current.limit == null
                                ? null : current.limit.scope,
                             current.limit == null
                                ? null : current.limit.reason);
            }
            MutationResult published = publish(
                current.withStagedAttention(Attention.STAGED_CONFLICT));
            if (published == MutationResult.UPDATED) {
                stagedAuthority = randomAuthority();
                return stage(PortableStageResult.STAGED_CONFLICT,
                             current.limit == null
                                ? null : current.limit.scope,
                             current.limit == null
                                ? null : current.limit.reason);
            }
            return stagePublication(published, null, null,
                                    PortableStageResult.STAGED_CONFLICT);
        }

        if (current.reviewEpoch == Long.MAX_VALUE) {
            lastError =
                "The Library membership review epoch is exhausted.";
            return stage(PortableStageResult.EXHAUSTED, null, null);
        }
        try {
            byte[] baseBytes =
                OctavoLibraryMembershipPortable.encode(current.snapshot);
            StagedState staged = new StagedState(
                kind, sha256(exact), sha256(baseBytes), exact);
            Attention nextAttention;
            LimitState limit = null;
            PortableStageResult success;
            if (kind == StagedKind.FUTURE) {
                nextAttention = Attention.FUTURE_RETAINED;
                success = PortableStageResult.FUTURE_RETAINED;
            } else {
                OctavoLibraryMembershipPortable.MergeResult inspected =
                    OctavoLibraryMembershipPortable.merge(
                        current.snapshot, decoded.snapshot());
                if (inspected.status
                    == OctavoLibraryMembershipPortable.MergeStatus.INVALID) {
                    lastError =
                        "Portable Library membership join input is invalid.";
                    return stage(PortableStageResult.INVALID, null, null);
                }
                if (inspected.status
                    == OctavoLibraryMembershipPortable.MergeStatus
                        .EQUIVOCATION) {
                    lastError =
                        "Portable Library membership history is equivocal.";
                    return stage(
                        PortableStageResult.EQUIVOCATION, null, null);
                }
                if (inspected.status
                    == OctavoLibraryMembershipPortable.MergeStatus.LIMIT) {
                    nextAttention = Attention.JOIN_LIMIT_RETAINED;
                    limit = new LimitState(
                        inspected.limitScope, inspected.limitReason);
                    success = PortableStageResult.LIMIT_RETAINED;
                } else {
                    nextAttention = Attention.CURRENT_APPROVAL;
                    success = PortableStageResult.STAGED_CURRENT;
                }
            }
            MutationResult published = publish(current.withStaged(
                current.reviewEpoch + 1, staged, nextAttention, limit));
            if (published == MutationResult.UPDATED) {
                stagedAuthority = randomAuthority();
                return stage(success,
                             limit == null ? null : limit.scope,
                             limit == null ? null : limit.reason);
            }
            return stagePublication(
                published,
                limit == null ? null : limit.scope,
                limit == null ? null : limit.reason,
                success);
        } catch (IOException | RuntimeException exception) {
            lastError =
                "Portable Library membership input could not be staged.";
            return stage(PortableStageResult.INVALID, null, null);
        }
    }

    synchronized PortableApprovalResult approveStagedPortable(
        StagedPortable expected) {
        if (!loadAttempted || mutationsBlocked) {
            return PortableApprovalResult.BLOCKED;
        }
        if (current.staged == null) {
            lastError = "There is no staged Library membership input.";
            return PortableApprovalResult.NO_STAGED_INPUT;
        }
        if (!matchesStagedReceipt(expected)) {
            lastError = "The staged Library membership receipt is stale.";
            return PortableApprovalResult.STALE_RECEIPT;
        }
        if (current.attention == Attention.STAGED_CONFLICT) {
            lastError = attentionMessage(current);
            return PortableApprovalResult.STAGED_CONFLICT;
        }
        if (current.attention == Attention.STALE_BASE) {
            lastError = attentionMessage(current);
            return PortableApprovalResult.STALE_BASE;
        }
        if (current.attention == Attention.JOIN_LIMIT_RETAINED) {
            lastError = attentionMessage(current);
            return PortableApprovalResult.LIMIT_RETAINED;
        }
        if (current.staged.kind == StagedKind.FUTURE) {
            lastError = attentionMessage(current);
            return PortableApprovalResult.FUTURE_VERSION_BLOCKED;
        }
        if (current.attention != Attention.CURRENT_APPROVAL) {
            lastError = "The staged Library membership state is invalid.";
            return PortableApprovalResult.INVALID;
        }
        try {
            byte[] baseBytes =
                OctavoLibraryMembershipPortable.encode(current.snapshot);
            if (!Arrays.equals(
                    current.staged.baseSha256, sha256(baseBytes))) {
                lastError =
                    "The staged Library membership base is stale.";
                return PortableApprovalResult.STALE_BASE;
            }
            OctavoLibraryMembershipPortable.DecodeResult decoded =
                OctavoLibraryMembershipPortable.decode(
                    current.staged.bytes);
            if (decoded.status
                != OctavoLibraryMembershipPortable.DecodeStatus.READY) {
                lastError =
                    "The staged Library membership input is invalid.";
                return PortableApprovalResult.INVALID;
            }
            OctavoLibraryMembershipPortable.MergeResult joined =
                OctavoLibraryMembershipPortable.merge(
                    current.snapshot, decoded.snapshot());
            if (joined.status
                == OctavoLibraryMembershipPortable.MergeStatus.INVALID) {
                return PortableApprovalResult.INVALID;
            }
            if (joined.status
                == OctavoLibraryMembershipPortable.MergeStatus
                    .EQUIVOCATION) {
                return PortableApprovalResult.EQUIVOCATION;
            }
            if (joined.status
                == OctavoLibraryMembershipPortable.MergeStatus.LIMIT) {
                return PortableApprovalResult.LIMIT;
            }
            boolean changed = joined.status
                == OctavoLibraryMembershipPortable.MergeStatus.MERGED;
            if (changed
                && current.stateGeneration == Long.MAX_VALUE) {
                lastError =
                    "The Library membership state generation is exhausted.";
                return PortableApprovalResult.EXHAUSTED;
            }
            String actor = current.actorId;
            long counter = current.counter;
            if (changed
                && joined.snapshot.maximumActorCounter(actor) > counter) {
                actor = freshUnusedActor(joined.snapshot, actor);
                if (actor == null) {
                    lastError =
                        "A fresh Library membership actor could not be created.";
                    return PortableApprovalResult.BLOCKED;
                }
                counter = 0;
            }
            State approved = current.approved(
                actor, counter,
                changed ? current.stateGeneration + 1
                        : current.stateGeneration,
                joined.snapshot);
            MutationResult published = publish(approved);
            if (published == MutationResult.UPDATED) {
                stagedAuthority = randomAuthority();
                return changed ? PortableApprovalResult.MERGED
                    : PortableApprovalResult.UNCHANGED;
            }
            if (published == MutationResult.PUBLISH_UNCERTAIN) {
                return PortableApprovalResult.PUBLISH_UNCERTAIN;
            }
            return PortableApprovalResult.PUBLISH_FAILED;
        } catch (IOException | RuntimeException exception) {
            lastError =
                "The staged Library membership input could not be approved.";
            return PortableApprovalResult.INVALID;
        }
    }

    synchronized PortableDiscardResult discardStagedPortable(
        StagedPortable expected) {
        if (!loadAttempted || mutationsBlocked) {
            return PortableDiscardResult.BLOCKED;
        }
        if (current.staged == null) {
            if (expected == null) {
                lastError = attentionMessage(current);
                return PortableDiscardResult.UNCHANGED;
            }
            lastError = "The staged Library membership receipt is stale.";
            return PortableDiscardResult.STALE_RECEIPT;
        }
        if (!matchesStagedReceipt(expected)) {
            lastError = "The staged Library membership receipt is stale.";
            return PortableDiscardResult.STALE_RECEIPT;
        }
        MutationResult published = publish(current.withoutStaged());
        if (published == MutationResult.UPDATED) {
            stagedAuthority = randomAuthority();
            return PortableDiscardResult.DISCARDED;
        }
        if (published == MutationResult.PUBLISH_UNCERTAIN) {
            return PortableDiscardResult.PUBLISH_UNCERTAIN;
        }
        return PortableDiscardResult.PUBLISH_FAILED;
    }

    synchronized PortableExport exportPortable() {
        if (!loadAttempted) {
            return new PortableExport(
                PortableExportStatus.NOT_LOADED, null);
        }
        if (mutationsBlocked) {
            return new PortableExport(
                PortableExportStatus.BLOCKED, null);
        }
        try {
            return new PortableExport(
                PortableExportStatus.EXPORTED,
                OctavoLibraryMembershipPortable.encode(current.snapshot));
        } catch (IOException | RuntimeException exception) {
            lastError = "Portable Library membership bytes could not be created.";
            return new PortableExport(
                PortableExportStatus.LOCAL_FAILURE, null);
        }
    }

    synchronized byte[] retainedFutureBytes() {
        return current.staged != null
                && current.staged.kind == StagedKind.FUTURE
            ? current.staged.bytes.clone() : null;
    }

    synchronized boolean hasCorruptQuarantineAttention() {
        return hasQuarantineEvidence();
    }

    synchronized RecoveryResult recoverFromReviewedPortable(
        byte[] exactCurrentBytes,
        String exactSha256Echo) {
        if (!loadAttempted
            || loadStatus
               != LoadStatus.CORRUPT_QUARANTINED_BLOCKED
            || stateFile.exists() || !hasQuarantineEvidence()) {
            lastError =
                "Library membership state is not eligible for reviewed recovery.";
            return RecoveryResult.NOT_RECOVERABLE;
        }
        if (exactCurrentBytes == null
            || exactCurrentBytes.length < MIN_CURRENT_BYTES
            || exactCurrentBytes.length > MAX_CURRENT_BYTES
            || !validLowerHex(exactSha256Echo, 64)) {
            lastError = "Reviewed Library membership recovery input is invalid.";
            return RecoveryResult.INVALID;
        }
        byte[] exact = exactCurrentBytes.clone();
        if (!lowerHex(sha256(exact)).equals(exactSha256Echo)) {
            lastError =
                "Reviewed Library membership recovery digest changed.";
            return RecoveryResult.STALE_DIGEST;
        }
        try {
            OctavoLibraryMembershipPortable.DecodeResult decoded =
                OctavoLibraryMembershipPortable.decode(exact);
            if (decoded.status
                    != OctavoLibraryMembershipPortable.DecodeStatus.READY
                || !Arrays.equals(
                    exact,
                    OctavoLibraryMembershipPortable.encode(
                        decoded.snapshot()))) {
                lastError =
                    "Reviewed Library membership recovery bytes are invalid.";
                return RecoveryResult.INVALID;
            }
            String actor = freshUnusedActor(
                decoded.snapshot(), current.actorId);
            if (actor == null) {
                lastError =
                    "A fresh Library membership recovery actor could not be created.";
                return RecoveryResult.ACTOR_UNAVAILABLE;
            }
            State recovered = new State(
                actor, 0, 1, 0, decoded.snapshot(), null,
                Attention.NONE, null);
            MutationResult published = publish(recovered);
            if (published == MutationResult.UPDATED) {
                mutationsBlocked = false;
                loadStatus = LoadStatus.LOADED_QUARANTINE_ATTENTION;
                refreshAuthorities();
                lastError =
                    "Reviewed Library membership state was recovered; quarantined evidence remains.";
                return RecoveryResult.RECOVERED;
            }
            if (published == MutationResult.PUBLISH_UNCERTAIN) {
                return RecoveryResult.PUBLISH_UNCERTAIN;
            }
            return RecoveryResult.PUBLISH_FAILED;
        } catch (IOException | RuntimeException exception) {
            lastError =
                "Reviewed Library membership recovery bytes are invalid.";
            return RecoveryResult.INVALID;
        }
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    synchronized void failNextMoveAfterReplaceForTesting() {
        failNextMoveAfterReplaceForTesting = true;
    }

    synchronized void replaceDestinationAfterTempSyncForTesting(
        byte[] bytes) {
        if (bytes == null || bytes.length <= 0
            || bytes.length > MAX_STATE_BYTES) {
            throw new IllegalArgumentException(
                "Invalid injected O1MS destination bytes");
        }
        replaceDestinationAfterTempSyncForTesting = bytes.clone();
    }

    synchronized byte[] canonicalBytesForTesting() throws IOException {
        return encodeState(current);
    }

    synchronized int metadataBytesForTesting() throws IOException {
        int currentLength = OctavoLibraryMembershipPortable
            .encode(current.snapshot).length;
        int stagedLength = current.staged == null
            ? 0 : current.staged.bytes.length;
        return encodeState(current).length - currentLength - stagedLength;
    }

    File stateFileForTesting() {
        return stateFile;
    }

    File temporaryFileForTesting() {
        return temporaryFile;
    }

    File lockFileForTesting() {
        return lockFile;
    }

    File quarantineFileForTesting(int oneBasedIndex) {
        return new File(rootDirectory,
                        QUARANTINE_PREFIX + oneBasedIndex);
    }

    static int maximumFileBytesForTesting() {
        return MAX_STATE_BYTES;
    }

    static int maximumMetadataBytesForTesting() {
        return MAX_METADATA_BYTES;
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
        deleteOwnedForTesting(new File(root, LOCK_FILE));
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            deleteOwnedForTesting(new File(
                root, QUARANTINE_PREFIX + index));
        }
        File[] remaining = root.listFiles();
        if (remaining != null && remaining.length == 0) {
            root.delete();
        }
    }

    private static State emptyState(String actorId) {
        return new State(
            actorId, 0, 0, 0,
            new OctavoLibraryMembershipPortable.Snapshot(
                Collections.emptyList()),
            null, Attention.NONE, null);
    }

    private MutationOutcome requireMutable() {
        if (!loadAttempted) {
            return mutation(MutationResult.BLOCKED, null, null,
                            "Library membership state has not been loaded.");
        }
        if (mutationsBlocked) {
            return new MutationOutcome(
                MutationResult.BLOCKED, null, null);
        }
        return null;
    }

    private MutationOutcome mutate(
        Receipt expected,
        OctavoLibraryMembershipPortable.Operation operation,
        boolean resolvingConflict) {
        MutationOutcome ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (operation == null || !matchesReceipt(expected)) {
            return mutation(
                MutationResult.CONFLICT, null, null,
                "The Library membership action receipt is stale.");
        }
        if (resolvingConflict) {
            if (!expected.recordPresent
                || expected.projection
                   != OctavoLibraryMembershipPortable.Projection.CONFLICT) {
                return mutation(
                    MutationResult.INVALID, null, null,
                    "Only an exact current conflict can be resolved.");
            }
        } else if (expected.projection
                   == OctavoLibraryMembershipPortable.Projection.CONFLICT) {
            return mutation(
                MutationResult.CONFLICT, null, null,
                "A Library membership conflict requires explicit resolution.");
        }

        if (!resolvingConflict) {
            if (operation
                    == OctavoLibraryMembershipPortable.Operation.RESTORE
                && !expected.recordPresent) {
                return mutation(
                    MutationResult.INVALID, null, null,
                    "Absent Library membership history cannot be restored.");
            }
            if (expected.recordPresent
                && ((operation
                        == OctavoLibraryMembershipPortable.Operation.WITHDRAW
                     && expected.projection
                        == OctavoLibraryMembershipPortable.Projection
                            .WITHDRAWN)
                    || (operation
                        == OctavoLibraryMembershipPortable.Operation.RESTORE
                        && expected.projection
                           == OctavoLibraryMembershipPortable.Projection
                               .MEMBER))) {
                return unchangedMutation();
            }
        }
        if (current.stateGeneration == Long.MAX_VALUE) {
            return mutation(
                MutationResult.EXHAUSTED, null, null,
                "The Library membership state generation is exhausted.");
        }

        String actor = current.actorId;
        long nextCounter;
        boolean rotatedForExhaustion = false;
        if (current.counter == Long.MAX_VALUE) {
            actor = freshUnusedActor(current.snapshot, current.actorId);
            if (actor == null) {
                return mutation(
                    MutationResult.LIMIT,
                    OctavoLibraryMembershipPortable.LimitScope.LOCAL,
                    OctavoLibraryMembershipPortable.LimitReason
                        .COUNTER_EXHAUSTED,
                    "The Library membership actor counter is exhausted.");
            }
            nextCounter = 1;
            rotatedForExhaustion = true;
        } else {
            nextCounter = current.counter + 1;
        }

        OctavoLibraryMembershipPortable.MutationResult changed;
        if (resolvingConflict) {
            changed = OctavoLibraryMembershipPortable.resolveConflict(
                current.snapshot, expected.descriptor, actor,
                nextCounter,
                operation
                    == OctavoLibraryMembershipPortable.Operation.RESTORE
                    ? OctavoLibraryMembershipPortable.Projection.MEMBER
                    : OctavoLibraryMembershipPortable.Projection
                        .WITHDRAWN);
        } else if (operation
                   == OctavoLibraryMembershipPortable.Operation.WITHDRAW) {
            changed = OctavoLibraryMembershipPortable.withdraw(
                current.snapshot, expected.descriptor, actor,
                nextCounter);
        } else {
            changed = OctavoLibraryMembershipPortable.restore(
                current.snapshot, expected.descriptor, actor,
                nextCounter);
        }
        if (changed.status
            == OctavoLibraryMembershipPortable.MutationStatus.LIMIT) {
            OctavoLibraryMembershipPortable.LimitReason reason =
                rotatedForExhaustion
                    && changed.limitReason
                       == OctavoLibraryMembershipPortable.LimitReason
                           .ACTOR_HISTORY
                ? OctavoLibraryMembershipPortable.LimitReason
                    .COUNTER_EXHAUSTED
                : changed.limitReason;
            return mutation(
                MutationResult.LIMIT,
                changed.limitScope, reason,
                "The Library membership mutation reached a typed limit.");
        }
        if (changed.status
            == OctavoLibraryMembershipPortable.MutationStatus
                .EQUIVOCATION) {
            return mutation(
                MutationResult.CONFLICT, null, null,
                "The Library membership mutation is equivocal.");
        }
        if (changed.status
            == OctavoLibraryMembershipPortable.MutationStatus.INVALID) {
            return mutation(
                MutationResult.INVALID, null, null,
                "The Library membership mutation is invalid.");
        }
        if (changed.status
            == OctavoLibraryMembershipPortable.MutationStatus.UNCHANGED) {
            return unchangedMutation();
        }
        if (changed.status
            != OctavoLibraryMembershipPortable.MutationStatus.MUTATED
            || changed.snapshot == null) {
            return mutation(
                MutationResult.INVALID, null, null,
                "The Library membership mutation result is invalid.");
        }
        boolean stalesRetainedStage = current.staged != null;
        State candidate = current.withSnapshot(
            actor, nextCounter, current.stateGeneration + 1,
            changed.snapshot);
        MutationResult published = publish(candidate);
        if (published == MutationResult.UPDATED) {
            receiptAuthority = randomAuthority();
            if (stalesRetainedStage) {
                stagedAuthority = randomAuthority();
            }
        }
        return mutationPublication(published, null, null);
    }

    private Receipt createReceipt(
        OctavoLibraryMembershipPortable.Descriptor descriptor)
        throws IOException {
        OctavoLibraryMembershipPortable.Record record =
            current.snapshot.record(descriptor.digest);
        if (record != null
            && !record.descriptor.sameIdentity(descriptor)) {
            return null;
        }
        boolean present = record != null;
        OctavoLibraryMembershipPortable.Projection projection =
            present ? current.snapshot.projection(descriptor.digest) : null;
        byte[] snapshotBytes =
            OctavoLibraryMembershipPortable.encode(current.snapshot);
        String snapshotFingerprint = lowerHex(sha256(snapshotBytes));
        String recordFingerprint = recordFingerprint(
            descriptor, record);
        return new Receipt(
            descriptor, present, projection, recordFingerprint,
            snapshotFingerprint, current.stateGeneration,
            receiptAuthority);
    }

    private boolean matchesReceipt(Receipt expected) {
        if (expected == null
            || !MessageDigest.isEqual(
                expected.authority, receiptAuthority)) {
            return false;
        }
        try {
            Receipt actual = createReceipt(expected.descriptor);
            return actual != null && expected.sameIdentity(actual);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static String recordFingerprint(
        OctavoLibraryMembershipPortable.Descriptor descriptor,
        OctavoLibraryMembershipPortable.Record record)
        throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(
                "8vo.port11.library-membership.receipt.v1\n"
                    .getBytes(StandardCharsets.US_ASCII));
            byte[] digest = descriptor.digest.getBytes(
                StandardCharsets.US_ASCII);
            output.writeInt(digest.length);
            output.write(digest);
            output.writeLong(descriptor.byteCount);
            output.writeInt(descriptor.kind);
            output.writeInt(record == null ? 0 : 1);
            if (record != null) {
                byte[] canonical =
                    OctavoLibraryMembershipPortable.encode(
                        new OctavoLibraryMembershipPortable.Snapshot(
                            Collections.singletonList(record)));
                output.writeInt(canonical.length);
                output.write(canonical);
            }
            output.flush();
        }
        return lowerHex(sha256(bytes.toByteArray()));
    }

    private static boolean validDescriptor(
        OctavoLibraryMembershipPortable.Descriptor descriptor) {
        return descriptor != null
            && validLowerHex(descriptor.digest, 64)
            && descriptor.byteCount > 0
            && descriptor.byteCount <= 536_870_912L
            && descriptor.kind == 1;
    }

    private MutationOutcome unchangedMutation() {
        lastError = attentionMessage(current);
        return new MutationOutcome(MutationResult.UNCHANGED, null, null);
    }

    private MutationOutcome mutation(MutationResult result,
                                     OctavoLibraryMembershipPortable.LimitScope scope,
                                     OctavoLibraryMembershipPortable.LimitReason reason,
                                     String message) {
        lastError = message;
        return new MutationOutcome(result, scope, reason);
    }

    private MutationOutcome mutationPublication(
        MutationResult result,
        OctavoLibraryMembershipPortable.LimitScope scope,
        OctavoLibraryMembershipPortable.LimitReason reason) {
        return new MutationOutcome(result, scope, reason);
    }

    private static StageOutcome stage(
        PortableStageResult result,
        OctavoLibraryMembershipPortable.LimitScope scope,
        OctavoLibraryMembershipPortable.LimitReason reason) {
        return new StageOutcome(result, scope, reason);
    }

    private StageOutcome stagePublication(
        MutationResult result,
        OctavoLibraryMembershipPortable.LimitScope scope,
        OctavoLibraryMembershipPortable.LimitReason reason,
        PortableStageResult success) {
        if (result == MutationResult.UPDATED) {
            return stage(success, scope, reason);
        }
        if (result == MutationResult.PUBLISH_UNCERTAIN) {
            return stage(
                PortableStageResult.PUBLISH_UNCERTAIN, scope, reason);
        }
        if (result == MutationResult.PUBLISH_FAILED) {
            return stage(
                PortableStageResult.PUBLISH_FAILED, scope, reason);
        }
        if (result == MutationResult.LIMIT) {
            return stage(PortableStageResult.LIMIT, scope, reason);
        }
        return stage(PortableStageResult.BLOCKED, scope, reason);
    }

    private boolean matchesStagedReceipt(StagedPortable expected) {
        if (expected == null || current.staged == null
            || !MessageDigest.isEqual(
                expected.authority, stagedAuthority)) {
            return false;
        }
        StagedPortable actual = new StagedPortable(
            current.staged, current.reviewEpoch, current.attention,
            current.limit, stagedAuthority);
        return expected.sameIdentity(actual)
            && Arrays.equals(expected.bytes, current.staged.bytes);
    }

    private MutationResult publish(State candidate) {
        final byte[] candidateBytes;
        final byte[] expectedBytes;
        final boolean expectedExists = stateExpectedOnDisk;
        try {
            candidateBytes = encodeState(candidate);
            expectedBytes = expectedExists
                ? encodeState(current) : null;
        } catch (IOException | RuntimeException exception) {
            lastError = "Library membership candidate bytes are invalid.";
            return MutationResult.PUBLISH_FAILED;
        }
        try {
            requireDirectory(rootDirectory);
            try (RandomAccessFile lockAccess =
                     new RandomAccessFile(lockFile, "rw");
                 FileChannel lockChannel = lockAccess.getChannel()) {
                FileLock publicationLock;
                try {
                    publicationLock = lockChannel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    publicationLock = null;
                }
                if (publicationLock == null) {
                    lastError =
                        "Another Library membership publication is active. Retry.";
                    return MutationResult.PUBLISH_FAILED;
                }
                try (FileLock heldPublicationLock = publicationLock) {
                    if (!heldPublicationLock.isValid()) {
                        lastError =
                            "Library membership publication lock was lost. Retry.";
                        return MutationResult.PUBLISH_FAILED;
                    }
                    return publishLocked(
                        candidate, candidateBytes,
                        expectedExists, expectedBytes);
                }
            }
        } catch (IOException | RuntimeException exception) {
            lastError =
                "Library membership publication lock could not be acquired. Retry.";
            return MutationResult.PUBLISH_FAILED;
        }
    }

    private MutationResult publishLocked(
        State candidate,
        byte[] candidateBytes,
        boolean expectedExists,
        byte[] expectedBytes) {
        boolean destinationVerified = false;
        try {
            if (!destinationEquals(expectedExists, expectedBytes)) {
                return uncertain(
                    expectedExists, expectedBytes, candidateBytes,
                    "Library membership destination changed before publication; exact reload is required.");
            }
            destinationVerified = true;
            if (failNextPublishForTesting) {
                failNextPublishForTesting = false;
                throw new IOException("Injected O1MS publish failure");
            }
            try (FileOutputStream output =
                     new FileOutputStream(temporaryFile, false)) {
                output.write(candidateBytes);
                output.flush();
                output.getFD().sync();
            }
            if (!temporaryFile.isFile()
                || !Arrays.equals(
                    candidateBytes, readBounded(temporaryFile))) {
                throw new IOException(
                    "O1MS temporary candidate changed before replace");
            }
            if (replaceDestinationAfterTempSyncForTesting != null) {
                byte[] injected =
                    replaceDestinationAfterTempSyncForTesting;
                replaceDestinationAfterTempSyncForTesting = null;
                try (FileOutputStream output =
                         new FileOutputStream(stateFile, false)) {
                    output.write(injected);
                    output.flush();
                    output.getFD().sync();
                }
            }
            // The destination is checked again after the candidate is fully
            // flushed and immediately before the only authority-changing
            // operation. The same-directory file lock excludes another
            // O1MS publisher from the fixed temporary path and replace.
            if (!destinationEquals(expectedExists, expectedBytes)) {
                deleteTemporaryBestEffort();
                return uncertain(
                    expectedExists, expectedBytes, candidateBytes,
                    "Library membership destination changed while the candidate was flushed; exact reload is required.");
            }
            Files.move(temporaryFile.toPath(), stateFile.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            if (failNextMoveAfterReplaceForTesting) {
                failNextMoveAfterReplaceForTesting = false;
                throw new IOException(
                    "Injected uncertain O1MS replace result");
            }
            current = candidate;
            stateExpectedOnDisk = true;
            canonicalDestinationObserved = true;
            mutationsBlocked = false;
            if (hasQuarantineEvidence()) {
                loadStatus = LoadStatus.LOADED_QUARANTINE_ATTENTION;
                lastError =
                    "Quarantined Library membership evidence remains for attention.";
            } else {
                loadStatus = LoadStatus.LOADED;
                lastError = attentionMessage(candidate);
            }
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            if (!destinationVerified) {
                lastError =
                    "Library membership destination could not be verified. Retry.";
                return MutationResult.PUBLISH_FAILED;
            }
            if (!destinationEquals(expectedExists, expectedBytes)) {
                return uncertain(
                    expectedExists, expectedBytes, candidateBytes,
                    "Library membership publication is uncertain; exact reload is required.");
            }
            lastError =
                "Library membership state could not be saved. Retry.";
            return MutationResult.PUBLISH_FAILED;
        }
    }

    private boolean destinationEquals(boolean expectedExists,
                                      byte[] expectedBytes) {
        try {
            if (!expectedExists) {
                return !stateFile.exists();
            }
            return expectedBytes != null && stateFile.isFile()
                && Arrays.equals(expectedBytes, readBounded(stateFile));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private byte[] safeCanonicalCurrentBytes() {
        try {
            return encodeState(current);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private MutationResult uncertain(boolean priorExists,
                                     byte[] prior,
                                     byte[] candidate,
                                     String message) {
        uncertainPriorExists = priorExists;
        uncertainPriorBytes = prior == null ? null : prior.clone();
        uncertainCandidateBytes = candidate == null
            ? new byte[0] : candidate.clone();
        mutationsBlocked = true;
        loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
        lastError = message;
        return MutationResult.PUBLISH_UNCERTAIN;
    }

    private LoadStatus reconcileUncertainLoad() {
        byte[] observed = null;
        boolean observedExists = stateFile.isFile();
        try {
            if (observedExists) {
                observed = readBounded(stateFile);
            } else if (stateFile.exists()) {
                return block(
                    LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
                    "Library membership reload found an unexpected destination type.");
            }
            boolean priorMatches = observedExists == uncertainPriorExists
                && (!observedExists
                    || Arrays.equals(observed, uncertainPriorBytes));
            boolean candidateMatches = observedExists
                && uncertainCandidateBytes.length > 0
                && Arrays.equals(observed, uncertainCandidateBytes);
            if (!priorMatches && !candidateMatches) {
                return block(
                    LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
                    "Library membership reload found neither the exact prior nor candidate bytes.");
            }
            if (candidateMatches) {
                current = decodeState(observed);
                stateExpectedOnDisk = true;
                canonicalDestinationObserved = true;
            } else if (!observedExists) {
                stateExpectedOnDisk = false;
                canonicalDestinationObserved = true;
            } else {
                current = decodeState(observed);
                stateExpectedOnDisk = true;
                canonicalDestinationObserved = true;
            }
            clearUncertain();
            if (!stateExpectedOnDisk && hasQuarantineEvidence()) {
                canonicalDestinationObserved = false;
                return block(
                    LoadStatus.CORRUPT_QUARANTINED_BLOCKED,
                    "Exact prior absence was proved, but quarantined bytes still require reviewed recovery.");
            }
            mutationsBlocked = false;
            if (!stateExpectedOnDisk) {
                loadStatus = LoadStatus.MISSING_EMPTY;
                lastError = "";
            } else if (hasQuarantineEvidence()) {
                loadStatus = LoadStatus.LOADED_QUARANTINE_ATTENTION;
                lastError =
                    "Quarantined Library membership evidence remains for attention.";
            } else {
                loadStatus = LoadStatus.LOADED;
                lastError = attentionMessage(current);
            }
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            return block(
                LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
                "Library membership reload could not prove exact prior or candidate bytes.");
        }
    }

    private void clearUncertain() {
        uncertainPriorExists = false;
        uncertainPriorBytes = null;
        uncertainCandidateBytes = null;
    }

    private LoadStatus block(LoadStatus status, String message) {
        mutationsBlocked = true;
        loadStatus = status;
        lastError = message;
        return status;
    }

    private boolean quarantineCorruptState(byte[] expectedCorruptBytes) {
        try {
            requireDirectory(rootDirectory);
            try (RandomAccessFile lockAccess =
                     new RandomAccessFile(lockFile, "rw");
                 FileChannel lockChannel = lockAccess.getChannel()) {
                FileLock quarantineLock;
                try {
                    quarantineLock = lockChannel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    quarantineLock = null;
                }
                if (quarantineLock == null) {
                    return false;
                }
                try (FileLock heldQuarantineLock = quarantineLock) {
                    if (!heldQuarantineLock.isValid()) {
                        return false;
                    }
                    if (!stateFile.isFile()) {
                        return false;
                    }
                    if (expectedCorruptBytes != null) {
                        if (!Arrays.equals(
                                expectedCorruptBytes,
                                readBounded(stateFile))) {
                            return false;
                        }
                    } else if (stateFile.length() > 0) {
                        byte[] observed = readBounded(stateFile);
                        if (isFutureStore(observed)) {
                            return false;
                        }
                        try {
                            decodeState(observed);
                            return false;
                        } catch (IOException | RuntimeException exception) {
                            // Still malformed under the exclusion lock.
                        }
                    }
                    for (int index = 1;
                         index <= QUARANTINE_SLOTS; ++index) {
                        File quarantine = quarantineFileForTesting(index);
                        if (quarantine.exists()) {
                            continue;
                        }
                        Files.move(
                            stateFile.toPath(), quarantine.toPath(),
                            StandardCopyOption.ATOMIC_MOVE);
                        return true;
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
        return false;
    }

    private boolean hasQuarantineEvidence() {
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            if (quarantineFileForTesting(index).exists()) {
                return true;
            }
        }
        return false;
    }

    private void refreshAuthorities() {
        receiptAuthority = randomAuthority();
        stagedAuthority = randomAuthority();
    }

    private static byte[] randomAuthority() {
        byte[] result = new byte[HASH_BYTES];
        new SecureRandom().nextBytes(result);
        return result;
    }

    private static String randomActorId(SecureRandom random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return lowerHex(bytes);
    }

    private String freshUnusedActor(
        OctavoLibraryMembershipPortable.Snapshot snapshot,
        String additionallyDisallowed) {
        for (int attempt = 0; attempt < ACTOR_ATTEMPTS; ++attempt) {
            String candidate = randomActorId(random);
            if (!candidate.equals(additionallyDisallowed)
                && !snapshot.actorAppears(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean validActorId(String value) {
        return validLowerHex(value, ACTOR_BYTES);
    }

    private static boolean validLowerHex(String value, int characters) {
        if (value == null || value.length() != characters) {
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

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable", exception);
        }
    }

    private static String lowerHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; ++index) {
            int value = bytes[index] & 0xff;
            result[2 * index] = digits[value >>> 4];
            result[2 * index + 1] = digits[value & 0xf];
        }
        return new String(result);
    }

    private static String attentionMessage(State state) {
        if (state.attention == Attention.CURRENT_APPROVAL) {
            return "A Library membership snapshot awaits approval.";
        }
        if (state.attention == Attention.JOIN_LIMIT_RETAINED) {
            return "A Library membership snapshot reached a typed history limit and was retained.";
        }
        if (state.attention == Attention.FUTURE_RETAINED) {
            return "Newer Library membership bytes were retained for review.";
        }
        if (state.attention == Attention.STAGED_CONFLICT) {
            return "Different staged Library membership bytes require explicit review.";
        }
        if (state.attention == Attention.STALE_BASE) {
            return "The staged Library membership snapshot has a stale local base and must be discarded.";
        }
        return "";
    }

    private static Context applicationContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context application = context.getApplicationContext();
        return application == null ? context : application;
    }

    private static File requireFilesDirectory(Context context) {
        return applicationContext(context).getFilesDir();
    }

    private static void requireDirectory(File directory)
        throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(
                "Unable to create Library membership directory");
        }
    }

    private void deleteTemporaryBestEffort() {
        if (temporaryFile.isFile()) {
            temporaryFile.delete();
        }
    }

    private static void deleteOwnedForTesting(File file) {
        if (!file.exists()) {
            return;
        }
        if (!file.isFile() || !file.delete()) {
            throw new IllegalStateException(
                "Unable to clear Library membership test state");
        }
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0
            || file.length() > MAX_STATE_BYTES) {
            throw new IOException("Invalid O1MS file length");
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
                throw new IOException("O1MS changed while reading");
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

    private static boolean isFutureStore(byte[] bytes) {
        return bytes != null && bytes.length >= 2 * Integer.BYTES
            && bytes.length <= MAX_STATE_BYTES
            && readInt(bytes, 0) == STORE_MAGIC
            && Integer.compareUnsigned(
                   readInt(bytes, Integer.BYTES), STORE_VERSION) > 0;
    }

    private static byte[] encodeState(State state) throws IOException {
        validateState(state);
        byte[] currentBytes =
            OctavoLibraryMembershipPortable.encode(state.snapshot);
        int stagedLength = state.staged == null
            ? 0 : state.staged.bytes.length;
        int metadataLength = state.staged == null ? 96 : 160;
        long exactLength = (long)metadataLength
            + currentBytes.length + stagedLength;
        if (exactLength > MAX_STATE_BYTES
            || metadataLength > MAX_METADATA_BYTES) {
            throw new IOException("O1MS exceeds its private byte cap");
        }
        ByteArrayOutputStream payloadBytes =
            new ByteArrayOutputStream((int)exactLength - Integer.BYTES);
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(STORE_MAGIC);
            output.writeInt(STORE_VERSION);
            output.writeInt(STORE_FIELD_COUNT);
            output.write(
                state.actorId.getBytes(StandardCharsets.US_ASCII));
            output.writeLong(state.counter);
            output.writeLong(state.stateGeneration);
            output.writeLong(state.reviewEpoch);
            output.writeInt(state.attention.wireId);
            output.writeInt(state.limit == null
                            ? 0 : scopeWireId(state.limit.scope));
            output.writeInt(state.limit == null
                            ? 0 : reasonWireId(state.limit.reason));
            output.writeInt(currentBytes.length);
            output.writeInt(state.staged == null
                            ? 0 : state.staged.kind.wireId);
            output.writeInt(stagedLength);
            if (state.staged != null) {
                output.write(state.staged.sha256);
                output.write(state.staged.baseSha256);
            }
            output.write(currentBytes);
            if (state.staged != null) {
                output.write(state.staged.bytes);
            }
            output.flush();
        }
        byte[] payload = payloadBytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(payload, 0, payload.length);
        ByteArrayOutputStream result =
            new ByteArrayOutputStream(payload.length + Integer.BYTES);
        try (DataOutputStream output = new DataOutputStream(result)) {
            output.write(payload);
            output.writeInt((int)checksum.getValue());
            output.flush();
        }
        byte[] bytes = result.toByteArray();
        if (bytes.length != exactLength) {
            throw new IOException("Invalid O1MS encoded length");
        }
        return bytes;
    }

    private static State decodeState(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 96 + MIN_CURRENT_BYTES
            || bytes.length > MAX_STATE_BYTES) {
            throw new IOException("Invalid O1MS byte length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, payloadLength);
        if (readInt(bytes, payloadLength)
            != (int)checksum.getValue()) {
            throw new IOException("Invalid O1MS checksum");
        }
        try {
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != STORE_MAGIC
                || input.readInt() != STORE_VERSION
                || input.readInt() != STORE_FIELD_COUNT) {
                throw new IOException("Invalid O1MS header");
            }
            byte[] actorBytes = new byte[ACTOR_BYTES];
            input.readFully(actorBytes);
            String actorId = new String(
                actorBytes, StandardCharsets.US_ASCII);
            long counter = input.readLong();
            long stateGeneration = input.readLong();
            long reviewEpoch = input.readLong();
            Attention attention = Attention.fromWireId(input.readInt());
            int scopeId = input.readInt();
            int reasonId = input.readInt();
            int currentLength = input.readInt();
            int stagedKindId = input.readInt();
            int stagedLength = input.readInt();
            StagedKind stagedKind = stagedKindId == 0
                ? null : StagedKind.fromWireId(stagedKindId);
            int metadataLength = stagedKindId == 0 ? 96 : 160;
            if (!validActorId(actorId)
                || counter < 0 || stateGeneration < 0
                || reviewEpoch < 0 || attention == null
                || currentLength < MIN_CURRENT_BYTES
                || currentLength > MAX_CURRENT_BYTES
                || stagedLength < 0
                || stagedLength > MAX_PORTABLE_BYTES
                || (stagedKindId == 0
                    && (stagedKind != null || stagedLength != 0))
                || (stagedKindId != 0 && stagedKind == null)
                || metadataLength > MAX_METADATA_BYTES
                || (long)metadataLength + currentLength + stagedLength
                   != bytes.length) {
                throw new IOException("Invalid O1MS bounds");
            }
            LimitState limit = readLimit(scopeId, reasonId);
            byte[] stagedHash = null;
            byte[] baseHash = null;
            if (stagedKind != null) {
                stagedHash = new byte[HASH_BYTES];
                baseHash = new byte[HASH_BYTES];
                input.readFully(stagedHash);
                input.readFully(baseHash);
            }
            byte[] currentBytes = new byte[currentLength];
            input.readFully(currentBytes);
            byte[] stagedBytes = null;
            if (stagedKind != null) {
                stagedBytes = new byte[stagedLength];
                input.readFully(stagedBytes);
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1MS payload");
            }
            OctavoLibraryMembershipPortable.DecodeResult decoded =
                OctavoLibraryMembershipPortable.decode(currentBytes);
            if (decoded.status
                    != OctavoLibraryMembershipPortable.DecodeStatus.READY
                || !Arrays.equals(
                    currentBytes,
                    OctavoLibraryMembershipPortable.encode(
                        decoded.snapshot()))) {
                throw new IOException(
                    "Invalid canonical current O1LM snapshot");
            }
            StagedState staged = stagedKind == null ? null
                : new StagedState(
                    stagedKind, stagedHash, baseHash, stagedBytes);
            State state = new State(
                actorId, counter, stateGeneration, reviewEpoch,
                decoded.snapshot(), staged, attention, limit);
            validateState(state);
            if (!Arrays.equals(bytes, encodeState(state))) {
                throw new IOException("Noncanonical O1MS bytes");
            }
            return state;
        } catch (EOFException exception) {
            throw new IOException("Truncated O1MS", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid O1MS scalar", exception);
        }
    }

    private static void validateState(State state) throws IOException {
        if (state == null || !validActorId(state.actorId)
            || state.counter < 0 || state.stateGeneration < 0
            || state.reviewEpoch < 0 || state.snapshot == null
            || state.attention == null) {
            throw new IOException("Invalid O1MS state");
        }
        byte[] currentBytes =
            OctavoLibraryMembershipPortable.encode(state.snapshot);
        if (currentBytes.length < MIN_CURRENT_BYTES
            || currentBytes.length > MAX_CURRENT_BYTES) {
            throw new IOException("Invalid O1MS current snapshot length");
        }
        OctavoLibraryMembershipPortable.DecodeResult currentDecoded =
            OctavoLibraryMembershipPortable.decode(currentBytes);
        if (currentDecoded.status
            != OctavoLibraryMembershipPortable.DecodeStatus.READY
            || state.snapshot.maximumActorCounter(state.actorId)
               > state.counter) {
            throw new IOException("Invalid O1MS actor state");
        }
        if (state.staged == null) {
            if (state.attention != Attention.NONE || state.limit != null) {
                throw new IOException("Invalid empty O1MS staging state");
            }
            return;
        }
        if (state.reviewEpoch <= 0 || state.staged.kind == null
            || state.staged.sha256.length != HASH_BYTES
            || state.staged.baseSha256.length != HASH_BYTES
            || state.staged.bytes.length < 2 * Integer.BYTES
            || state.staged.bytes.length > MAX_PORTABLE_BYTES
            || !Arrays.equals(
                state.staged.sha256, sha256(state.staged.bytes))) {
            throw new IOException("Invalid O1MS staged bytes");
        }
        OctavoLibraryMembershipPortable.DecodeResult stagedDecoded =
            OctavoLibraryMembershipPortable.decode(state.staged.bytes);
        boolean currentStaged = state.staged.kind == StagedKind.CURRENT
            && stagedDecoded.status
               == OctavoLibraryMembershipPortable.DecodeStatus.READY;
        boolean futureStaged = state.staged.kind == StagedKind.FUTURE
            && stagedDecoded.status
               == OctavoLibraryMembershipPortable.DecodeStatus.FUTURE_VERSION;
        if (!currentStaged && !futureStaged) {
            throw new IOException("Invalid O1MS staged kind");
        }
        byte[] currentHash = sha256(currentBytes);
        boolean baseMatches = Arrays.equals(
            state.staged.baseSha256, currentHash);
        OctavoLibraryMembershipPortable.MergeResult stagedJoin =
            currentStaged
            ? OctavoLibraryMembershipPortable.merge(
                state.snapshot, stagedDecoded.snapshot())
            : null;
        boolean ordinaryJoin = stagedJoin != null
            && (stagedJoin.status
                == OctavoLibraryMembershipPortable.MergeStatus.MERGED
                || stagedJoin.status
                   == OctavoLibraryMembershipPortable.MergeStatus
                       .UNCHANGED);
        boolean exactRetainedLimit = stagedJoin != null
            && stagedJoin.status
               == OctavoLibraryMembershipPortable.MergeStatus.LIMIT
            && state.limit != null
            && state.limit.scope
               == OctavoLibraryMembershipPortable.LimitScope.JOIN
            && stagedJoin.limitScope == state.limit.scope
            && stagedJoin.limitReason == state.limit.reason
            && state.limit.reason
               != OctavoLibraryMembershipPortable.LimitReason
                   .COUNTER_EXHAUSTED;
        switch (state.attention) {
            case CURRENT_APPROVAL:
                if (!currentStaged || state.limit != null
                    || !baseMatches || !ordinaryJoin) {
                    throw new IOException("Invalid O1MS approval attention");
                }
                break;
            case JOIN_LIMIT_RETAINED:
                if (!currentStaged || !baseMatches
                    || !exactRetainedLimit) {
                    throw new IOException("Invalid O1MS limit attention");
                }
                break;
            case FUTURE_RETAINED:
                if (!futureStaged || state.limit != null
                    || !baseMatches) {
                    throw new IOException("Invalid O1MS future attention");
                }
                break;
            case STAGED_CONFLICT:
                if ((!baseMatches && state.limit != null)
                    || (baseMatches && currentStaged
                        && state.limit == null && !ordinaryJoin)
                    || (baseMatches && currentStaged
                        && state.limit != null && !exactRetainedLimit)
                    || (futureStaged && state.limit != null)) {
                    throw new IOException("Invalid O1MS conflict limit");
                }
                break;
            case STALE_BASE:
                if ((!currentStaged && !futureStaged)
                    || state.limit != null
                    || baseMatches) {
                    throw new IOException("Invalid O1MS stale-base attention");
                }
                break;
            default:
                throw new IOException("Invalid O1MS staged attention");
        }
    }

    private static LimitState readLimit(int scopeId, int reasonId)
        throws IOException {
        if (scopeId == 0 && reasonId == 0) {
            return null;
        }
        OctavoLibraryMembershipPortable.LimitScope scope =
            scopeFromWireId(scopeId);
        OctavoLibraryMembershipPortable.LimitReason reason =
            reasonFromWireId(reasonId);
        if (scope == null || reason == null) {
            throw new IOException("Invalid O1MS typed limit");
        }
        return new LimitState(scope, reason);
    }

    private static int scopeWireId(
        OctavoLibraryMembershipPortable.LimitScope value) {
        switch (value) {
            case INPUT: return 1;
            case JOIN: return 2;
            case LOCAL: return 3;
            default: throw new IllegalArgumentException("Invalid limit scope");
        }
    }

    private static OctavoLibraryMembershipPortable.LimitScope
        scopeFromWireId(int value) {
        switch (value) {
            case 1: return OctavoLibraryMembershipPortable.LimitScope.INPUT;
            case 2: return OctavoLibraryMembershipPortable.LimitScope.JOIN;
            case 3: return OctavoLibraryMembershipPortable.LimitScope.LOCAL;
            default: return null;
        }
    }

    private static int reasonWireId(
        OctavoLibraryMembershipPortable.LimitReason value) {
        switch (value) {
            case RECORD_HISTORY: return 1;
            case ACTOR_HISTORY: return 2;
            case CONCURRENT_HEADS: return 3;
            case ENCODED_BYTES: return 4;
            case COUNTER_EXHAUSTED: return 5;
            default: throw new IllegalArgumentException("Invalid limit reason");
        }
    }

    private static OctavoLibraryMembershipPortable.LimitReason
        reasonFromWireId(int value) {
        switch (value) {
            case 1:
                return OctavoLibraryMembershipPortable.LimitReason
                    .RECORD_HISTORY;
            case 2:
                return OctavoLibraryMembershipPortable.LimitReason
                    .ACTOR_HISTORY;
            case 3:
                return OctavoLibraryMembershipPortable.LimitReason
                    .CONCURRENT_HEADS;
            case 4:
                return OctavoLibraryMembershipPortable.LimitReason
                    .ENCODED_BYTES;
            case 5:
                return OctavoLibraryMembershipPortable.LimitReason
                    .COUNTER_EXHAUSTED;
            default:
                return null;
        }
    }
}
