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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Private, bounded O1BQ intent queue and exact-byte transfer staging.
 *
 * This class deliberately does not know about providers, accounts, network
 * clients, Reader0, or the Port 6 catalog. Callers retain those resources and
 * attest the Reader0 and catalog boundaries through explicit phase methods.
 */
final class OctavoBookTransferStore {
    enum LoadStatus {
        MISSING_EMPTY,
        LOADED,
        RECOVERED_EXTRA_TRUNCATED,
        RECOVERED_PREFIX_REPAIRED,
        RECOVERED_CANCEL,
        RECOVERED_ORPHAN_REMOVED,
        MANAGED_RECONCILE_REQUIRED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        FUTURE_VERSION_BLOCKED,
        OVERBOUND_BLOCKED,
        PART_RECONCILE_BLOCKED,
        PUBLISH_UNCERTAIN_BLOCKED
    }

    enum MutationResult {
        UPDATED,
        UNCHANGED,
        INVALID,
        BLOCKED,
        LIMIT,
        CONFLICT,
        EXHAUSTED,
        FUTURE_MANIFEST_RETAINED,
        FUTURE_MANIFEST_CONFLICT,
        IO_FAILED,
        VERIFY_FAILED,
        CLEANUP_FAILED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == UPDATED || this == UNCHANGED;
        }
    }

    enum ChunkReadStatus {
        READY,
        INVALID,
        BLOCKED,
        CONFLICT,
        IO_FAILED,
        VERIFY_FAILED
    }

    enum Direction {
        DOWNLOAD(1),
        UPLOAD(2);

        final int wireId;

        Direction(int wireId) {
            this.wireId = wireId;
        }

        static Direction fromWireId(int wireId) {
            for (Direction value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    enum Phase {
        QUEUED(0),
        STAGED(1),
        TRANSFERRING(2),
        BYTES_VERIFIED(3),
        READER0_VALIDATED(4),
        MANAGED_PUBLISHED(5),
        LOCAL_CATALOG_LINKED(6),
        REMOTE_OBJECT_VERIFIED(7);

        final int wireId;

        Phase(int wireId) {
            this.wireId = wireId;
        }

        static Phase fromWireId(int wireId) {
            for (Phase value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }

        boolean active() {
            return this != QUEUED;
        }
    }

    enum DurableDirection {
        FORWARD(1),
        CANCEL(2);

        final int wireId;

        DurableDirection(int wireId) {
            this.wireId = wireId;
        }

        static DurableDirection fromWireId(int wireId) {
            for (DurableDirection value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    enum Attention {
        NONE(0),
        RETRY_CHUNK(1),
        PREFIX_REPAIRED(2),
        EXTRA_TRUNCATED(3),
        CANCEL_CLEANUP(4),
        UPLOAD_SOURCE_REQUIRED(5),
        MANAGED_RECONCILE_REQUIRED(6),
        READER0_REJECTED(7),
        CATALOG_LINK_FAILED(8),
        FUTURE_MANIFEST_RETAINED(9),
        FUTURE_MANIFEST_CONFLICT(10),
        REMOTE_OBJECT_MISMATCH(11),
        COMPLETE_HASH_MISMATCH(12),
        MANAGED_DESTINATION_CONFLICT(13);

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

    enum CleanupPhase {
        AWAITING_CATALOG_UNLINK(1),
        READY_TO_DELETE(2),
        AWAITING_SYNC_SUPPRESSION(3);

        final int wireId;

        CleanupPhase(int wireId) {
            this.wireId = wireId;
        }

        static CleanupPhase fromWireId(int wireId) {
            for (CleanupPhase value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    enum CleanupPurpose {
        LOCAL_REMOVE(1),
        REPAIR_REPLACE(2),
        UNCATALOGED(3);

        final int wireId;

        CleanupPurpose(int wireId) {
            this.wireId = wireId;
        }

        static CleanupPurpose fromWireId(int wireId) {
            for (CleanupPurpose value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
        }
    }

    /** Fresh, process-local authority. Its identity is never serialized. */
    static final class CallbackToken {
        final long attemptSequence;
        final String attemptId;
        private final byte[] nonce;

        private CallbackToken(long attemptSequence,
                              String attemptId,
                              byte[] nonce) {
            this.attemptSequence = attemptSequence;
            this.attemptId = attemptId;
            this.nonce = nonce.clone();
        }
    }

    /** Fresh process-local authority for one durable managed-file cleanup. */
    static final class CleanupToken {
        final long attemptSequence;
        private final byte[] nonce;

        private CleanupToken(long attemptSequence, byte[] nonce) {
            this.attemptSequence = attemptSequence;
            this.nonce = nonce.clone();
        }
    }

    static final class ActiveJob {
        final long attemptSequence;
        final String attemptId;
        final Direction direction;
        final Phase phase;
        final DurableDirection durableDirection;
        final String digest;
        final long byteCount;
        final int completedPrefix;
        final int chunkCount;
        final boolean retryRequired;
        final Attention attention;
        final CallbackToken callbackToken;
        private final byte[] manifestBytes;
        private final byte[] manifestHash;

        private ActiveJob(IntentState state,
                          CallbackToken callbackToken,
                          boolean uploadSourceMissing) {
            attemptSequence = state.attemptSequence;
            attemptId = state.attemptId;
            direction = state.direction;
            phase = state.phase;
            durableDirection = state.durableDirection;
            digest = state.manifest.digest;
            byteCount = state.manifest.byteCount;
            completedPrefix = state.completedPrefix;
            chunkCount = state.manifest.chunkCount;
            retryRequired = state.retryRequired || uploadSourceMissing;
            attention = uploadSourceMissing
                ? Attention.UPLOAD_SOURCE_REQUIRED : state.attention;
            this.callbackToken = callbackToken;
            manifestBytes = state.manifestBytes.clone();
            manifestHash = state.manifestHash.clone();
        }

        byte[] manifestBytes() {
            return manifestBytes.clone();
        }

        byte[] manifestHash() {
            return manifestHash.clone();
        }
    }

    static final class StageOutcome {
        final MutationResult result;
        final long attemptSequence;
        final String attemptId;
        final boolean active;

        private StageOutcome(MutationResult result,
                             long attemptSequence,
                             String attemptId,
                             boolean active) {
            this.result = result;
            this.attemptSequence = attemptSequence;
            this.attemptId = attemptId;
            this.active = active;
        }
    }

    static final class CleanupJob {
        final long attemptSequence;
        final String digest;
        final long byteCount;
        final CleanupPurpose purpose;
        final CleanupPhase phase;
        final boolean retryRequired;
        final String originAttemptId;
        final CleanupToken callbackToken;
        private final byte[] originManifestHash;

        private CleanupJob(CleanupState state, CleanupToken token) {
            attemptSequence = state.attemptSequence;
            digest = state.digest;
            byteCount = state.byteCount;
            purpose = state.purpose;
            phase = state.phase;
            retryRequired = state.retryRequired;
            originAttemptId = state.originAttemptId;
            callbackToken = token;
            originManifestHash = state.originManifestHash == null
                ? null : state.originManifestHash.clone();
        }

        byte[] originManifestHash() {
            return originManifestHash == null
                ? null : originManifestHash.clone();
        }
    }

    static final class CleanupOutcome {
        final MutationResult result;
        final long attemptSequence;

        private CleanupOutcome(MutationResult result,
                               long attemptSequence) {
            this.result = result;
            this.attemptSequence = attemptSequence;
        }
    }

    static final class ChunkRead {
        final ChunkReadStatus status;
        final int index;
        final int byteCount;

        private ChunkRead(ChunkReadStatus status,
                          int index,
                          int byteCount) {
            this.status = status;
            this.index = index;
            this.byteCount = byteCount;
        }
    }

    private static final class IntentState {
        final long attemptSequence;
        final String attemptId;
        final Direction direction;
        final Phase phase;
        final DurableDirection durableDirection;
        final int completedPrefix;
        final boolean retryRequired;
        final Attention attention;
        final OctavoBookManifest manifest;
        final byte[] manifestBytes;
        final byte[] manifestHash;

        IntentState(long attemptSequence,
                    String attemptId,
                    Direction direction,
                    Phase phase,
                    DurableDirection durableDirection,
                    int completedPrefix,
                    boolean retryRequired,
                    Attention attention,
                    OctavoBookManifest manifest,
                    byte[] manifestBytes,
                    byte[] manifestHash) {
            this.attemptSequence = attemptSequence;
            this.attemptId = attemptId;
            this.direction = direction;
            this.phase = phase;
            this.durableDirection = durableDirection;
            this.completedPrefix = completedPrefix;
            this.retryRequired = retryRequired;
            this.attention = attention;
            this.manifest = manifest;
            this.manifestBytes = manifestBytes.clone();
            this.manifestHash = manifestHash.clone();
        }

        IntentState withTransfer(Phase nextPhase,
                                 int nextPrefix,
                                 boolean nextRetry,
                                 Attention nextAttention) {
            return new IntentState(
                attemptSequence, attemptId, direction, nextPhase,
                durableDirection,
                nextPrefix, nextRetry, nextAttention, manifest,
                manifestBytes, manifestHash);
        }

        IntentState withPhase(Phase nextPhase) {
            return new IntentState(
                attemptSequence, attemptId, direction, nextPhase,
                durableDirection, completedPrefix, false, Attention.NONE,
                manifest, manifestBytes, manifestHash);
        }

        IntentState withAttention(Attention nextAttention) {
            return new IntentState(
                attemptSequence, attemptId, direction, phase,
                durableDirection, completedPrefix, true, nextAttention,
                manifest, manifestBytes, manifestHash);
        }

        IntentState withCancel(Attention nextAttention,
                               boolean retry) {
            return new IntentState(
                attemptSequence, attemptId, direction, phase,
                DurableDirection.CANCEL, completedPrefix, retry,
                nextAttention, manifest, manifestBytes, manifestHash);
        }

        IntentState activate() {
            return new IntentState(
                attemptSequence, attemptId, direction, Phase.STAGED,
                DurableDirection.FORWARD, 0, false, Attention.NONE,
                manifest, manifestBytes, manifestHash);
        }
    }

    private static final class CleanupState {
        final long attemptSequence;
        final String digest;
        final long byteCount;
        final CleanupPurpose purpose;
        final CleanupPhase phase;
        final boolean retryRequired;
        final String originAttemptId;
        final byte[] originManifestHash;

        CleanupState(long attemptSequence,
                     String digest,
                     long byteCount,
                     CleanupPurpose purpose,
                     CleanupPhase phase,
                     boolean retryRequired) {
            this(attemptSequence, digest, byteCount, purpose, phase,
                 retryRequired, null, null);
        }

        CleanupState(long attemptSequence,
                     String digest,
                     long byteCount,
                     CleanupPurpose purpose,
                     CleanupPhase phase,
                     boolean retryRequired,
                     String originAttemptId,
                     byte[] originManifestHash) {
            this.attemptSequence = attemptSequence;
            this.digest = digest;
            this.byteCount = byteCount;
            this.purpose = purpose;
            this.phase = phase;
            this.retryRequired = retryRequired;
            this.originAttemptId = originAttemptId;
            this.originManifestHash = originManifestHash == null
                ? null : originManifestHash.clone();
        }

        CleanupState withPhase(CleanupPhase value) {
            return new CleanupState(
                attemptSequence, digest, byteCount, purpose, value, false,
                originAttemptId, originManifestHash);
        }

        CleanupState withRetry() {
            return new CleanupState(
                attemptSequence, digest, byteCount, purpose, phase, true,
                originAttemptId, originManifestHash);
        }
    }

    private static final class State {
        final long nextAttemptSequence;
        final List<IntentState> intents;
        final List<CleanupState> cleanups;
        final byte[] futureManifestBytes;
        final byte[] futureManifestHash;
        final boolean futureManifestConflict;

        State(long nextAttemptSequence,
              List<IntentState> intents,
              List<CleanupState> cleanups,
              byte[] futureManifestBytes,
              byte[] futureManifestHash,
              boolean futureManifestConflict) {
            this.nextAttemptSequence = nextAttemptSequence;
            this.intents = Collections.unmodifiableList(
                new ArrayList<>(intents));
            this.cleanups = Collections.unmodifiableList(
                new ArrayList<>(cleanups));
            this.futureManifestBytes = futureManifestBytes == null
                ? null : futureManifestBytes.clone();
            this.futureManifestHash = futureManifestHash == null
                ? null : futureManifestHash.clone();
            this.futureManifestConflict = futureManifestConflict;
        }

        State withIntents(List<IntentState> value) {
            return new State(
                nextAttemptSequence, value, cleanups,
                futureManifestBytes, futureManifestHash,
                futureManifestConflict);
        }

        State withCleanups(List<CleanupState> value) {
            return new State(
                nextAttemptSequence, intents, value,
                futureManifestBytes, futureManifestHash,
                futureManifestConflict);
        }

        State withFutureManifest(byte[] bytes, boolean conflict) {
            return new State(
                nextAttemptSequence, intents, cleanups, bytes,
                bytes == null ? null : sha256(bytes), conflict);
        }
    }

    private static final class PrefixCheck {
        final int validPrefix;
        final boolean exact;
        final boolean extra;

        PrefixCheck(int validPrefix, boolean exact, boolean extra) {
            this.validPrefix = validPrefix;
            this.exact = exact;
            this.extra = extra;
        }
    }

    private static final int STORE_MAGIC = 0x4F314251; // "O1BQ"
    private static final int STORE_VERSION = 1;
    private static final int STORE_FIELD_COUNT = 5;
    private static final int MAX_STATE_BYTES = 1024 * 1024;
    private static final int MAX_INTENTS = 63;
    private static final int MANIFEST_HASH_BYTES = 32;
    private static final int ATTEMPT_ID_BYTES = 32;
    private static final int QUARANTINE_SLOTS = 3;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "book-transfer.v1";
    private static final String TEMPORARY_FILE = "book-transfer.v1.tmp";
    private static final String QUARANTINE_PREFIX =
        "book-transfer.corrupt.";
    private static final String PART_SUFFIX = ".part";

    private final File rootDirectory;
    private final File managedDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private final SecureRandom random;
    private State current = new State(
        0, Collections.emptyList(), Collections.emptyList(),
        null, null, false);
    private LoadStatus loadStatus = LoadStatus.MISSING_EMPTY;
    private boolean loadAttempted;
    private boolean mutationsBlocked;
    private boolean managedReconcileRequired;
    private boolean stateExpectedOnDisk;
    private CallbackToken runtimeToken;
    private final ArrayList<CleanupToken> runtimeCleanupTokens =
        new ArrayList<>();
    private File runtimeUploadSource;
    private int lastUploadReadIndex = -1;
    private boolean failNextPublishForTesting;
    private boolean failNextMoveAfterReplaceForTesting;
    private String lastError = "";

    OctavoBookTransferStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoBookTransferStore(File filesDirectory) {
        this(filesDirectory,
             new File(new File(filesDirectory, "port6"), "documents"),
             new SecureRandom());
    }

    OctavoBookTransferStore(File filesDirectory,
                            File managedDirectory) {
        this(filesDirectory, managedDirectory, new SecureRandom());
    }

    private OctavoBookTransferStore(File filesDirectory,
                                    File managedDirectory,
                                    SecureRandom random) {
        if (filesDirectory == null || managedDirectory == null
            || random == null) {
            throw new IllegalArgumentException(
                "Invalid book transfer store");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        this.managedDirectory = managedDirectory;
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
        this.random = random;
    }

    synchronized LoadStatus load() {
        loadAttempted = true;
        mutationsBlocked = false;
        managedReconcileRequired = false;
        runtimeUploadSource = null;
        lastUploadReadIndex = -1;
        deleteTemporaryBestEffort();
        if (!stateFile.exists()) {
            if (stateExpectedOnDisk) {
                return block(
                    LoadStatus.PUBLISH_UNCERTAIN_BLOCKED,
                    "Expected book transfer state is missing; reload cannot prove publication.");
            }
            try {
                boolean removed = removeUnexpectedParts(null);
                current = new State(
                    0, Collections.emptyList(), Collections.emptyList(),
                    null, null, false);
                stateExpectedOnDisk = false;
                loadStatus = removed
                    ? LoadStatus.RECOVERED_ORPHAN_REMOVED
                    : LoadStatus.MISSING_EMPTY;
                lastError = removed
                    ? "An orphan transfer part was removed." : "";
                if (!removed && hasQuarantineEvidence()) {
                    loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                    lastError =
                        "Quarantined corrupt book transfer state requires attention.";
                }
            } catch (IOException | RuntimeException exception) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "Book transfer storage could not be initialized.";
            }
            refreshRuntimeToken();
            return loadStatus;
        }
        try {
            if (stateFile.isFile()
                && stateFile.length() > MAX_STATE_BYTES) {
                stateExpectedOnDisk = true;
                return block(
                    LoadStatus.OVERBOUND_BLOCKED,
                    "Book transfer state exceeds 1 MiB and was preserved.");
            }
            byte[] bytes = readBounded(stateFile);
            if (isFutureStore(bytes)) {
                stateExpectedOnDisk = true;
                return block(
                    LoadStatus.FUTURE_VERSION_BLOCKED,
                    "Book transfer state was written by a newer version.");
            }
            current = decodeState(bytes);
            stateExpectedOnDisk = true;
            deleteTemporaryBestEffort();
            refreshRuntimeToken();
            try {
                loadStatus = reconcileAfterLoad();
                if (loadStatus == LoadStatus.LOADED
                    && hasQuarantineEvidence()) {
                    loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                    lastError =
                        "Quarantined corrupt book transfer state requires attention.";
                }
            } catch (IOException | RuntimeException exception) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PART_RECONCILE_BLOCKED;
                lastError =
                    "Transfer staging could not be reconciled safely.";
            }
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState()) {
                current = new State(
                    0, Collections.emptyList(), Collections.emptyList(),
                    null, null, false);
                stateExpectedOnDisk = false;
                boolean stagingClean;
                try {
                    removeUnexpectedParts(null);
                    stagingClean = true;
                } catch (IOException cleanupException) {
                    stagingClean = false;
                }
                MutationResult recreated = stagingClean
                    ? publish(current) : MutationResult.CLEANUP_FAILED;
                if (recreated == MutationResult.UPDATED) {
                    loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                    lastError =
                        "Corrupt book transfer state was quarantined.";
                } else if (recreated != MutationResult.PUBLISH_UNCERTAIN) {
                    mutationsBlocked = true;
                    loadStatus = LoadStatus.CORRUPT_BLOCKED;
                    lastError =
                        "Corrupt book transfer state was quarantined, but replacement failed.";
                }
            } else {
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "Corrupt book transfer state could not be quarantined.";
            }
            refreshRuntimeToken();
            return loadStatus;
        }
    }

    synchronized LoadStatus loadStatus() {
        return loadStatus;
    }

    synchronized String lastError() {
        return lastError;
    }

    synchronized byte[] retainedFutureManifestBytes() {
        return current.futureManifestBytes == null
            ? null : current.futureManifestBytes.clone();
    }

    synchronized Attention futureManifestAttention() {
        if (current.futureManifestBytes == null) {
            return Attention.NONE;
        }
        return current.futureManifestConflict
            ? Attention.FUTURE_MANIFEST_CONFLICT
            : Attention.FUTURE_MANIFEST_RETAINED;
    }

    synchronized int intentCount() {
        return current.intents.size();
    }

    synchronized int cleanupIntentCount() {
        return current.cleanups.size();
    }

    synchronized int retainedIntentCount() {
        return current.intents.size() + current.cleanups.size();
    }

    synchronized int queuedIntentCount() {
        int count = 0;
        for (IntentState intent : current.intents) {
            if (intent.phase == Phase.QUEUED) {
                count += 1;
            }
        }
        return count;
    }

    synchronized ActiveJob activeJob() {
        IntentState active = activeInternal();
        if (active == null || runtimeToken == null) {
            return null;
        }
        boolean sourceMissing = active.direction == Direction.UPLOAD
            && (active.phase == Phase.STAGED
                || active.phase == Phase.TRANSFERRING)
            && runtimeUploadSource == null;
        return new ActiveJob(active, runtimeToken, sourceMissing);
    }

    synchronized List<CleanupJob> cleanupJobs() {
        ArrayList<CleanupJob> result =
            new ArrayList<>(current.cleanups.size());
        for (CleanupState cleanup : current.cleanups) {
            CleanupToken token = cleanupToken(cleanup.attemptSequence);
            if (token != null) {
                result.add(new CleanupJob(cleanup, token));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized StageOutcome stageDownload(byte[] canonicalManifestBytes) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return new StageOutcome(ready, 0, null, false);
        }
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(canonicalManifestBytes);
        if (decoded.status == OctavoBookManifest.DecodeStatus.FUTURE_VERSION) {
            byte[] future = decoded.preservedBytes();
            if (current.futureManifestBytes == null) {
                MutationResult retained = publish(
                    current.withFutureManifest(future, false));
                if (retained.succeeded()) {
                    lastError =
                        "A newer book manifest was retained for attention.";
                    return new StageOutcome(
                        MutationResult.FUTURE_MANIFEST_RETAINED,
                        0, null, false);
                }
                return new StageOutcome(retained, 0, null, false);
            }
            if (Arrays.equals(current.futureManifestBytes, future)) {
                return new StageOutcome(
                    MutationResult.UNCHANGED, 0, null, false);
            }
            if (!current.futureManifestConflict) {
                MutationResult conflict = publish(
                    current.withFutureManifest(
                        current.futureManifestBytes, true));
                if (!conflict.succeeded()) {
                    return new StageOutcome(
                        conflict, 0, null, false);
                }
            }
            lastError =
                "A different newer book manifest conflicts with the retained bytes.";
            return new StageOutcome(
                MutationResult.FUTURE_MANIFEST_CONFLICT,
                0, null, false);
        }
        if (decoded.status == OctavoBookManifest.DecodeStatus.LIMIT) {
            return stageFailure(MutationResult.LIMIT,
                                "The book manifest exceeds its bound.");
        }
        if (decoded.status != OctavoBookManifest.DecodeStatus.READY) {
            return stageFailure(MutationResult.INVALID,
                                "The book manifest is invalid.");
        }
        return stage(Direction.DOWNLOAD, decoded.manifest(),
                     canonicalManifestBytes, null);
    }

    synchronized StageOutcome stageExplicitUpload(
        File callerApprovedManagedFile,
        boolean explicitUserApproval,
        boolean exactImportedCatalogAssociationProved) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return new StageOutcome(ready, 0, null, false);
        }
        if (!explicitUserApproval
            || !exactImportedCatalogAssociationProved
            || callerApprovedManagedFile == null) {
            return stageFailure(
                MutationResult.INVALID,
                "Upload requires an explicitly approved imported Library EPUB.");
        }
        File controlledSource;
        try {
            controlledSource = controlledManagedUploadSource(
                callerApprovedManagedFile);
        } catch (IOException | RuntimeException exception) {
            return stageFailure(
                MutationResult.INVALID,
                "Upload requires an EPUB owned by the managed directory.");
        }
        try {
            OctavoBookManifest manifest =
                OctavoBookManifest.build(controlledSource);
            if (!controlledSource.getName().equals(
                    manifest.digest + ".epub")) {
                return stageFailure(
                    MutationResult.INVALID,
                    "The managed EPUB filename must match its exact digest.");
            }
            byte[] manifestBytes = manifest.encode();
            return stage(Direction.UPLOAD, manifest, manifestBytes,
                         controlledSource);
        } catch (IOException | RuntimeException exception) {
            return stageFailure(
                MutationResult.VERIFY_FAILED,
                "The approved managed EPUB could not be fully verified.");
        }
    }

    synchronized CleanupOutcome stageManagedCleanup(
        String digest,
        long byteCount) {
        return stageCleanup(
            digest, byteCount, CleanupPurpose.LOCAL_REMOVE,
            CleanupPhase.AWAITING_CATALOG_UNLINK);
    }

    synchronized CleanupOutcome stageRepairManagedCleanup(
        String digest,
        long byteCount) {
        return stageCleanup(
            digest, byteCount, CleanupPurpose.REPAIR_REPLACE,
            CleanupPhase.AWAITING_CATALOG_UNLINK);
    }

    /**
     * Atomically replaces the exact post-publication download intent with a
     * repair cleanup receipt. The managed EPUB is deliberately left untouched
     * until the caller durably unlinks its exact catalog association.
     */
    synchronized CleanupOutcome convertPublishedDownloadToRepairCleanup(
        CallbackToken token) {
        MutationResult ready = requireActiveToken(token);
        if (ready != null) {
            return new CleanupOutcome(ready, 0);
        }
        IntentState active = activeInternal();
        if (active.direction != Direction.DOWNLOAD
            || active.durableDirection != DurableDirection.FORWARD
            || (active.phase != Phase.MANAGED_PUBLISHED
                && active.phase != Phase.LOCAL_CATALOG_LINKED)) {
            lastError =
                "Only the exact published download can become a repair cleanup.";
            return new CleanupOutcome(MutationResult.CONFLICT, 0);
        }
        for (CleanupState cleanup : current.cleanups) {
            if (cleanup.attemptSequence == active.attemptSequence
                || cleanup.digest.equals(active.manifest.digest)) {
                lastError =
                    "That exact EPUB already has a cleanup intent.";
                return new CleanupOutcome(MutationResult.CONFLICT, 0);
            }
        }

        ArrayList<IntentState> intents =
            new ArrayList<>(current.intents);
        if (!intents.remove(active)) {
            lastError = "The active transfer changed unexpectedly.";
            return new CleanupOutcome(MutationResult.CONFLICT, 0);
        }
        File createdPart = null;
        if (!intents.isEmpty()) {
            IntentState next = intents.get(0);
            if (next.phase != Phase.QUEUED) {
                lastError = "The queued transfer order is invalid.";
                return new CleanupOutcome(MutationResult.CONFLICT, 0);
            }
            next = next.activate();
            intents.set(0, next);
            if (next.direction == Direction.DOWNLOAD) {
                createdPart = partFile(next);
                if (!createEmptyPart(createdPart)) {
                    lastError =
                        "The next download could not create clean staging.";
                    return new CleanupOutcome(
                        MutationResult.CLEANUP_FAILED, 0);
                }
            }
        }

        CleanupState replacement = new CleanupState(
            active.attemptSequence, active.manifest.digest,
            active.manifest.byteCount, CleanupPurpose.REPAIR_REPLACE,
            CleanupPhase.AWAITING_CATALOG_UNLINK, false,
            active.attemptId, active.manifestHash);
        ArrayList<CleanupState> cleanups =
            new ArrayList<>(current.cleanups);
        int insertion = 0;
        while (insertion < cleanups.size()
               && cleanups.get(insertion).attemptSequence
                  < replacement.attemptSequence) {
            insertion += 1;
        }
        cleanups.add(insertion, replacement);
        State candidate = new State(
            current.nextAttemptSequence, intents, cleanups,
            current.futureManifestBytes, current.futureManifestHash,
            current.futureManifestConflict);
        MutationResult result = publish(candidate);
        if (!result.succeeded()
            && result != MutationResult.PUBLISH_UNCERTAIN
            && createdPart != null && createdPart.isFile()) {
            if (!createdPart.delete()) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PART_RECONCILE_BLOCKED;
                lastError =
                    "The prior transfer is durable, but next staging cleanup requires reload.";
                return new CleanupOutcome(
                    MutationResult.CLEANUP_FAILED, 0);
            }
        }
        return new CleanupOutcome(
            result,
            (result.succeeded()
                || result == MutationResult.PUBLISH_UNCERTAIN)
                ? active.attemptSequence : 0);
    }

    synchronized CleanupOutcome stageUncatalogedManagedCleanup(
        String digest,
        long byteCount) {
        return stageCleanup(
            digest, byteCount, CleanupPurpose.UNCATALOGED,
            CleanupPhase.READY_TO_DELETE);
    }

    synchronized MutationResult markCleanupCatalogUnlinked(
        CleanupToken token) {
        MutationResult ready = requireCleanupToken(token);
        if (ready != null) {
            return ready;
        }
        CleanupState cleanup = cleanupInternal(token.attemptSequence);
        if (cleanup.phase == CleanupPhase.READY_TO_DELETE) {
            return unchanged();
        }
        if (cleanup.phase
            != CleanupPhase.AWAITING_CATALOG_UNLINK) {
            return fail(
                MutationResult.CONFLICT,
                "The cleanup has already crossed the catalog boundary.");
        }
        return replaceCleanupAndPublish(
            cleanup.withPhase(CleanupPhase.READY_TO_DELETE));
    }

    synchronized MutationResult deleteManagedForCleanup(
        CleanupToken token,
        File callerOwnedManagedDirectory) {
        MutationResult ready = requireCleanupToken(token);
        if (ready != null) {
            return ready;
        }
        CleanupState cleanup = cleanupInternal(token.attemptSequence);
        if (cleanup.phase != CleanupPhase.READY_TO_DELETE) {
            return fail(
                MutationResult.CONFLICT,
                "The local catalog row must be durably absent before file cleanup.");
        }
        File destination;
        try {
            destination = managedDestination(
                callerOwnedManagedDirectory, cleanup.digest);
        } catch (IOException | RuntimeException exception) {
            return fail(MutationResult.INVALID,
                        "The managed EPUB directory is invalid.");
        }
        if (destination.exists()
            && (!destination.isFile() || !destination.delete())) {
            MutationResult recorded = replaceCleanupAndPublish(
                cleanup.withRetry());
            if (!recorded.succeeded()) {
                return recorded;
            }
            return fail(
                MutationResult.CLEANUP_FAILED,
                "The managed EPUB cleanup is durable and needs Retry.");
        }
        if (destination.exists()) {
            return fail(
                MutationResult.CLEANUP_FAILED,
                "The managed EPUB is still present after cleanup.");
        }
        return replaceCleanupAndPublish(cleanup.withPhase(
            CleanupPhase.AWAITING_SYNC_SUPPRESSION));
    }

    synchronized MutationResult finalizeManagedCleanup(
        CleanupToken token,
        boolean exactSuppressionDurableOrNotApplicable) {
        MutationResult ready = requireCleanupToken(token);
        if (ready != null) {
            return ready;
        }
        CleanupState cleanup = cleanupInternal(token.attemptSequence);
        if (cleanup.phase != CleanupPhase.AWAITING_SYNC_SUPPRESSION
            || !exactSuppressionDurableOrNotApplicable) {
            return fail(
                MutationResult.CONFLICT,
                "Durable local-removal suppression or an exact not-applicable proof is required.");
        }
        ArrayList<CleanupState> cleanups =
            new ArrayList<>(current.cleanups);
        if (!cleanups.remove(cleanup)) {
            return fail(MutationResult.CONFLICT,
                        "The managed cleanup intent changed unexpectedly.");
        }
        return publish(current.withCleanups(cleanups));
    }

    synchronized MutationResult acceptNextDownloadChunk(
        CallbackToken token,
        int index,
        InputStream callerOwnedChunk) {
        MutationResult ready = requireActive(
            token, Direction.DOWNLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if ((active.phase != Phase.STAGED
             && active.phase != Phase.TRANSFERRING)
            || index != active.completedPrefix
            || index < 0
            || index >= active.manifest.chunkCount
            || callerOwnedChunk == null) {
            return fail(MutationResult.CONFLICT,
                        "Only the exact next download chunk is accepted.");
        }
        File part = partFile(active);
        long prefixBytes = prefixBytes(active.manifest,
                                       active.completedPrefix);
        if (!isControlledPart(part) || !part.isFile()
            || part.length() != prefixBytes) {
            mutationsBlocked = true;
            return fail(
                MutationResult.BLOCKED,
                "The staged EPUB no longer matches its durable prefix; reload is required.");
        }
        int expectedLength = active.manifest.expectedChunkLength(index);
        MessageDigest digest = sha256Digest();
        boolean appended = false;
        try (RandomAccessFile output = new RandomAccessFile(part, "rw")) {
            output.seek(prefixBytes);
            byte[] buffer = new byte[32 * 1024];
            int remaining = expectedLength;
            while (remaining > 0) {
                int count = callerOwnedChunk.read(
                    buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new IOException("Download chunk is short");
                }
                if (count == 0) {
                    int value = callerOwnedChunk.read();
                    if (value < 0) {
                        throw new IOException("Download chunk is short");
                    }
                    buffer[0] = (byte)value;
                    count = 1;
                }
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                remaining -= count;
                appended = true;
            }
            if (callerOwnedChunk.read() != -1
                || !Arrays.equals(
                    digest.digest(), active.manifest.chunkHash(index))) {
                throw new IOException(
                    "Download chunk length or digest does not match O1BM");
            }
            output.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            if (!truncateAndSync(part, prefixBytes)) {
                mutationsBlocked = true;
                return fail(
                    MutationResult.BLOCKED,
                    "A rejected download chunk could not be rolled back safely.");
            }
            persistAttentionBestEffort(
                active.withTransfer(active.phase,
                                    active.completedPrefix, true,
                                    Attention.RETRY_CHUNK));
            return fail(MutationResult.VERIFY_FAILED,
                        "The download chunk failed exact verification.");
        }
        IntentState next = active.withTransfer(
            Phase.TRANSFERRING, index + 1, false, Attention.NONE);
        MutationResult result = replaceActiveAndPublish(next);
        if (result == MutationResult.PUBLISH_FAILED && appended
            && !truncateAndSync(part, prefixBytes)) {
            mutationsBlocked = true;
            loadStatus = LoadStatus.PART_RECONCILE_BLOCKED;
            return fail(
                MutationResult.PUBLISH_UNCERTAIN,
                "The chunk was verified, but its durable prefix could not be reconciled.");
        }
        return result;
    }

    synchronized MutationResult finishDownload(CallbackToken token) {
        MutationResult ready = requireActive(
            token, Direction.DOWNLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.TRANSFERRING
            || active.completedPrefix != active.manifest.chunkCount) {
            return fail(MutationResult.CONFLICT,
                        "All download chunks must be durable before finishing.");
        }
        if (!fileMatchesManifest(
                partFile(active), active.manifest)) {
            IntentState failed = active.withAttention(
                Attention.COMPLETE_HASH_MISMATCH);
            MutationResult recorded = replaceActiveAndPublish(failed);
            if (!recorded.succeeded()) {
                return recorded;
            }
            return fail(
                MutationResult.VERIFY_FAILED,
                "The complete staged EPUB failed exact verification.");
        }
        return replaceActiveAndPublish(active.withTransfer(
            Phase.BYTES_VERIFIED, active.completedPrefix,
            false, Attention.NONE));
    }

    synchronized File stagedDownloadForReader0(ActiveJob expected) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return null;
        }
        IntentState active = activeInternal();
        if (expected == null || active == null
            || expected.callbackToken != runtimeToken
            || expected.attemptSequence != active.attemptSequence
            || !expected.attemptId.equals(active.attemptId)
            || expected.direction != Direction.DOWNLOAD
            || (expected.phase != Phase.BYTES_VERIFIED
                && expected.phase != Phase.READER0_VALIDATED)
            || expected.phase != active.phase
            || active.durableDirection != DurableDirection.FORWARD
            || expected.completedPrefix != active.completedPrefix
            || !expected.digest.equals(active.manifest.digest)) {
            lastError =
                "The Reader0 staging request does not match verified bytes.";
            return null;
        }
        File part = partFile(active);
        if (!fileMatchesManifest(part, active.manifest)) {
            lastError =
                "The staged EPUB changed before Reader0 validation.";
            return null;
        }
        try {
            File canonical = part.getCanonicalFile();
            if (!isControlledPart(canonical)) {
                lastError = "The Reader0 staging path is not controlled.";
                return null;
            }
            lastError = "";
            return canonical;
        } catch (IOException exception) {
            lastError = "The Reader0 staging path is unavailable.";
            return null;
        }
    }

    synchronized MutationResult recordReader0Rejected(
        CallbackToken token) {
        MutationResult ready = requireActive(
            token, Direction.DOWNLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.BYTES_VERIFIED) {
            return fail(
                MutationResult.CONFLICT,
                "Reader0 rejection does not match verified staged bytes.");
        }
        return replaceActiveAndPublish(
            active.withAttention(Attention.READER0_REJECTED));
    }

    synchronized MutationResult markReader0Validated(
        CallbackToken token) {
        MutationResult ready = requireActive(
            token, Direction.DOWNLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.BYTES_VERIFIED
            || active.completedPrefix != active.manifest.chunkCount) {
            return fail(
                MutationResult.CONFLICT,
                "Reader0 validation is accepted only after exact bytes are complete.");
        }
        return replaceActiveAndPublish(
            active.withPhase(Phase.READER0_VALIDATED));
    }

    synchronized MutationResult publishManaged(
        CallbackToken token,
        File callerOwnedManagedDirectory) {
        MutationResult ready = requireActiveAllowManagedReconcile(
            token, Direction.DOWNLOAD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.durableDirection != DurableDirection.FORWARD
            || active.phase != Phase.READER0_VALIDATED) {
            return fail(
                MutationResult.CONFLICT,
                "Managed publication requires Reader0-validated bytes.");
        }
        File destination;
        try {
            destination = managedDestination(
                callerOwnedManagedDirectory, active.manifest.digest);
        } catch (IOException | RuntimeException exception) {
            return fail(MutationResult.INVALID,
                        "The managed EPUB directory is invalid.");
        }
        File part = partFile(active);
        boolean destinationExact =
            fileMatchesManifest(destination, active.manifest);
        if (!part.isFile()) {
            if (!destinationExact) {
                managedReconcileRequired = true;
                return fail(
                    MutationResult.VERIFY_FAILED,
                    "Neither a verified part nor the exact managed EPUB is available.");
            }
        } else {
            if (!fileMatchesManifest(part, active.manifest)) {
                MutationResult recorded = replaceActiveAndPublish(
                    active.withAttention(
                        Attention.COMPLETE_HASH_MISMATCH));
                if (!recorded.succeeded()) {
                    return recorded;
                }
                return fail(
                    MutationResult.VERIFY_FAILED,
                    "The staged EPUB failed publication revalidation.");
            }
            if (destination.exists() && !destinationExact) {
                MutationResult recorded = replaceActiveAndPublish(
                    active.withAttention(
                        Attention.MANAGED_DESTINATION_CONFLICT));
                if (!recorded.succeeded()) {
                    return recorded;
                }
                return fail(
                    MutationResult.VERIFY_FAILED,
                    "A different managed destination blocks publication.");
            }
            if (destinationExact) {
                if (!part.delete()) {
                    return fail(
                        MutationResult.CLEANUP_FAILED,
                        "The exact managed EPUB exists, but staging cleanup failed.");
                }
            } else {
                try {
                    Files.move(part.toPath(), destination.toPath(),
                               StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException | RuntimeException exception) {
                    boolean moved = !part.exists()
                        && fileMatchesManifest(
                            destination, active.manifest);
                    if (!moved) {
                        if (part.isFile()
                            && fileMatchesManifest(
                                part, active.manifest)) {
                            return fail(
                                MutationResult.IO_FAILED,
                                "The verified EPUB could not be atomically published.");
                        }
                        mutationsBlocked = true;
                        managedReconcileRequired = true;
                        return fail(
                            MutationResult.PUBLISH_UNCERTAIN,
                            "Managed EPUB publication is uncertain; reconcile before retrying.");
                    }
                }
            }
        }
        MutationResult published = replaceActiveAndPublish(
            active.withPhase(Phase.MANAGED_PUBLISHED));
        if (!published.succeeded()) {
            mutationsBlocked = true;
            managedReconcileRequired = true;
            loadStatus = LoadStatus.MANAGED_RECONCILE_REQUIRED;
            return fail(
                MutationResult.PUBLISH_UNCERTAIN,
                "The exact managed EPUB exists, but queue publication must be reconciled.");
        }
        managedReconcileRequired = false;
        return published;
    }

    synchronized MutationResult reconcileManagedPublication(
        CallbackToken token,
        File callerOwnedManagedDirectory) {
        if (!loadAttempted) {
            return fail(MutationResult.BLOCKED,
                        "Load book transfer state first.");
        }
        IntentState active = activeInternal();
        if (!validToken(token, active)
            || active == null
            || active.direction != Direction.DOWNLOAD
            || active.phase != Phase.READER0_VALIDATED
            || active.durableDirection != DurableDirection.FORWARD) {
            return fail(MutationResult.CONFLICT,
                        "The managed publication callback is stale.");
        }
        File destination;
        try {
            destination = managedDestination(
                callerOwnedManagedDirectory, active.manifest.digest);
        } catch (IOException | RuntimeException exception) {
            return fail(MutationResult.INVALID,
                        "The managed EPUB directory is invalid.");
        }
        if (!fileMatchesManifest(destination, active.manifest)) {
            if (partFile(active).isFile()) {
                mutationsBlocked = false;
                managedReconcileRequired = false;
                return publishManaged(token, callerOwnedManagedDirectory);
            }
            managedReconcileRequired = true;
            mutationsBlocked = true;
            return fail(
                MutationResult.VERIFY_FAILED,
                "Managed publication cannot be proven from exact bytes.");
        }
        mutationsBlocked = false;
        managedReconcileRequired = false;
        MutationResult result = replaceActiveAndPublish(
            active.withPhase(Phase.MANAGED_PUBLISHED));
        if (!result.succeeded()) {
            mutationsBlocked = true;
            managedReconcileRequired = true;
        }
        return result;
    }

    synchronized MutationResult markLocalCatalogLinked(
        CallbackToken token) {
        MutationResult ready = requireActive(
            token, Direction.DOWNLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.MANAGED_PUBLISHED) {
            return fail(
                MutationResult.CONFLICT,
                "Local catalog linkage must follow managed publication.");
        }
        return replaceActiveAndPublish(
            active.withPhase(Phase.LOCAL_CATALOG_LINKED));
    }

    synchronized MutationResult recordLocalCatalogLinkFailed(
        CallbackToken token) {
        MutationResult ready = requireActive(
            token, Direction.DOWNLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.MANAGED_PUBLISHED) {
            return fail(
                MutationResult.CONFLICT,
                "Catalog failure does not match published managed bytes.");
        }
        return replaceActiveAndPublish(
            active.withAttention(Attention.CATALOG_LINK_FAILED));
    }

    synchronized MutationResult bindUploadSource(
        CallbackToken token,
        File callerOwnedManagedFile) {
        MutationResult ready = requireActive(
            token, Direction.UPLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        if (lastUploadReadIndex != -1) {
            return fail(
                MutationResult.CONFLICT,
                "Confirm the exact upload chunk before rebinding its source.");
        }
        IntentState active = activeInternal();
        File controlledSource;
        try {
            controlledSource = controlledManagedUploadSource(
                callerOwnedManagedFile);
            if (!controlledSource.getName().equals(
                    active.manifest.digest + ".epub")) {
                return fail(
                    MutationResult.INVALID,
                    "The upload source filename does not match the approved EPUB.");
            }
        } catch (IOException | RuntimeException exception) {
            return fail(
                MutationResult.INVALID,
                "The upload source is not owned by the managed directory.");
        }
        try {
            OctavoBookManifest actual =
                OctavoBookManifest.build(controlledSource);
            if (!active.manifest.sameIdentity(actual)) {
                return fail(
                    MutationResult.VERIFY_FAILED,
                    "The upload source does not match the approved EPUB.");
            }
            if (active.phase == Phase.BYTES_VERIFIED
                || active.phase == Phase.REMOTE_OBJECT_VERIFIED) {
                return unchanged();
            }
            runtimeUploadSource = controlledSource;
            lastUploadReadIndex = -1;
            lastError = "";
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            return fail(
                MutationResult.VERIFY_FAILED,
                "The upload source could not be fully revalidated.");
        }
    }

    synchronized ChunkRead readNextUploadChunk(
        CallbackToken token,
        int index,
        OutputStream callerOwnedOutput) {
        MutationResult ready = requireActive(
            token, Direction.UPLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return readFailure(chunkStatus(ready), index, lastError);
        }
        IntentState active = activeInternal();
        if ((active.phase != Phase.STAGED
             && active.phase != Phase.TRANSFERRING)
            || index != active.completedPrefix
            || index < 0
            || index >= active.manifest.chunkCount
            || callerOwnedOutput == null
            || lastUploadReadIndex != -1) {
            return readFailure(
                ChunkReadStatus.CONFLICT, index,
                "Only one exact upload chunk can await confirmation.");
        }
        if (runtimeUploadSource == null) {
            return readFailure(
                ChunkReadStatus.BLOCKED, index,
                "Rebind the explicitly approved upload source.");
        }
        int length = active.manifest.expectedChunkLength(index);
        long offset = (long)index * active.manifest.chunkSize;
        if (!runtimeUploadSource.isFile()
            || runtimeUploadSource.length() != active.manifest.byteCount) {
            runtimeUploadSource = null;
            lastUploadReadIndex = -1;
            return readFailure(
                ChunkReadStatus.VERIFY_FAILED, index,
                "The upload source changed after approval.");
        }
        lastUploadReadIndex = -2;
        try (RandomAccessFile input =
                 new RandomAccessFile(runtimeUploadSource, "r")) {
            input.seek(offset);
            byte[] buffer = new byte[32 * 1024];
            MessageDigest digest = sha256Digest();
            int remaining = length;
            while (remaining > 0) {
                int count = input.read(
                    buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new IOException("Upload chunk became short");
                }
                if (count == 0) {
                    continue;
                }
                digest.update(buffer, 0, count);
                callerOwnedOutput.write(buffer, 0, count);
                remaining -= count;
            }
            if (!Arrays.equals(digest.digest(),
                               active.manifest.chunkHash(index))) {
                runtimeUploadSource = null;
                lastUploadReadIndex = -1;
                MutationResult recorded = replaceActiveAndPublish(
                    active.withAttention(Attention.RETRY_CHUNK));
                if (!recorded.succeeded()) {
                    return readFailure(
                        chunkStatus(recorded), index, lastError);
                }
                return readFailure(
                    ChunkReadStatus.VERIFY_FAILED, index,
                    "The upload chunk no longer matches O1BM.");
            }
            lastUploadReadIndex = index;
            lastError = "";
            return new ChunkRead(ChunkReadStatus.READY, index, length);
        } catch (IOException | RuntimeException exception) {
            lastUploadReadIndex = -1;
            return readFailure(
                ChunkReadStatus.IO_FAILED, index,
                "The upload chunk could not be read.");
        }
    }

    synchronized MutationResult confirmUploadChunk(
        CallbackToken token,
        int index) {
        MutationResult ready = requireActive(
            token, Direction.UPLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if ((active.phase != Phase.STAGED
             && active.phase != Phase.TRANSFERRING)
            || index != active.completedPrefix
            || lastUploadReadIndex != index) {
            return fail(
                MutationResult.CONFLICT,
                "Upload progress requires the exact chunk read by this runtime token.");
        }
        MutationResult result = replaceActiveAndPublish(
            active.withTransfer(Phase.TRANSFERRING, index + 1,
                                false, Attention.NONE));
        if (result.succeeded()) {
            lastUploadReadIndex = -1;
        }
        return result;
    }

    synchronized MutationResult finishUpload(CallbackToken token) {
        MutationResult ready = requireActive(
            token, Direction.UPLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.TRANSFERRING
            || active.completedPrefix != active.manifest.chunkCount
            || runtimeUploadSource == null) {
            return fail(
                MutationResult.CONFLICT,
                "All upload chunks and the bound source are required.");
        }
        if (!fileMatchesManifest(
                runtimeUploadSource, active.manifest)) {
            runtimeUploadSource = null;
            MutationResult recorded = replaceActiveAndPublish(
                active.withAttention(
                    Attention.COMPLETE_HASH_MISMATCH));
            if (!recorded.succeeded()) {
                return recorded;
            }
            return fail(
                MutationResult.VERIFY_FAILED,
                "The complete upload source changed before completion.");
        }
        return replaceActiveAndPublish(active.withTransfer(
            Phase.BYTES_VERIFIED, active.completedPrefix,
            false, Attention.NONE));
    }

    synchronized MutationResult markSimulatedRemoteObjectVerified(
        CallbackToken token,
        byte[] exactRemoteManifestBytes) {
        MutationResult ready = requireActive(
            token, Direction.UPLOAD, DurableDirection.FORWARD);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.phase != Phase.BYTES_VERIFIED) {
            return fail(
                MutationResult.CONFLICT,
                "Remote-object proof must follow complete local verification.");
        }
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(exactRemoteManifestBytes);
        if (decoded.status != OctavoBookManifest.DecodeStatus.READY
            || !Arrays.equals(
                active.manifestBytes, exactRemoteManifestBytes)
            || !active.manifest.sameIdentity(decoded.manifest())) {
            MutationResult recorded = replaceActiveAndPublish(
                active.withAttention(Attention.REMOTE_OBJECT_MISMATCH));
            if (!recorded.succeeded()) {
                return recorded;
            }
            return fail(
                MutationResult.VERIFY_FAILED,
                "The simulated remote object does not prove the exact O1BM.");
        }
        return replaceActiveAndPublish(
            active.withPhase(Phase.REMOTE_OBJECT_VERIFIED));
    }

    synchronized MutationResult finalizeTransfer(CallbackToken token) {
        MutationResult ready = requireActiveToken(token);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.durableDirection != DurableDirection.FORWARD
            || (active.direction == Direction.DOWNLOAD
                && active.phase != Phase.LOCAL_CATALOG_LINKED)
            || (active.direction == Direction.UPLOAD
                && active.phase != Phase.REMOTE_OBJECT_VERIFIED)) {
            return fail(
                MutationResult.CONFLICT,
                "The transfer has not crossed its required completion boundary.");
        }
        return removeActiveAndActivateNext();
    }

    synchronized MutationResult cancelActive(CallbackToken token) {
        MutationResult ready = requireActiveToken(token);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.durableDirection == DurableDirection.FORWARD
            && !cancellableBeforePublication(active)) {
            return fail(
                MutationResult.CONFLICT,
                "Cancellation cannot cross a published-content boundary.");
        }
        if (active.durableDirection == DurableDirection.FORWARD) {
            MutationResult durable = replaceActiveAndPublish(
                active.withCancel(Attention.CANCEL_CLEANUP, false));
            if (!durable.succeeded()) {
                return durable;
            }
            active = activeInternal();
        }
        if (!cleanupActivePart(active)) {
            IntentState retry = active.withCancel(
                Attention.CANCEL_CLEANUP, true);
            MutationResult recorded = replaceActiveAndPublish(retry);
            if (!recorded.succeeded()) {
                return recorded;
            }
            return fail(
                MutationResult.CLEANUP_FAILED,
                "Cancellation is durable, but staging cleanup needs Retry.");
        }
        return removeActiveAndActivateNext();
    }

    private static boolean cancellableBeforePublication(IntentState active) {
        if (active == null || !active.phase.active()) {
            return false;
        }
        if (active.direction == Direction.DOWNLOAD) {
            return active.phase == Phase.STAGED
                || active.phase == Phase.TRANSFERRING
                || active.phase == Phase.BYTES_VERIFIED
                || active.phase == Phase.READER0_VALIDATED;
        }
        return active.direction == Direction.UPLOAD
            && (active.phase == Phase.STAGED
                || active.phase == Phase.TRANSFERRING
                || active.phase == Phase.BYTES_VERIFIED);
    }

    synchronized MutationResult cancelQueued(long attemptSequence) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        ArrayList<IntentState> intents =
            new ArrayList<>(current.intents);
        for (int index = 0; index < intents.size(); ++index) {
            IntentState intent = intents.get(index);
            if (intent.attemptSequence == attemptSequence) {
                if (intent.phase != Phase.QUEUED) {
                    return fail(MutationResult.CONFLICT,
                                "The active transfer requires its callback token.");
                }
                intents.remove(index);
                return publish(current.withIntents(intents));
            }
        }
        return fail(MutationResult.CONFLICT,
                    "The queued transfer no longer exists.");
    }

    private StageOutcome stage(Direction direction,
                               OctavoBookManifest manifest,
                               byte[] manifestBytes,
                               File uploadSource) {
        if (current.intents.size() + current.cleanups.size()
            >= MAX_INTENTS) {
            return stageFailure(MutationResult.LIMIT,
                                "The transfer queue is full.");
        }
        for (IntentState intent : current.intents) {
            if (intent.manifest.digest.equals(manifest.digest)) {
                return stageFailure(
                    MutationResult.CONFLICT,
                    "That exact EPUB already has a transfer intent.");
            }
        }
        for (CleanupState cleanup : current.cleanups) {
            if (cleanup.digest.equals(manifest.digest)) {
                return stageFailure(
                    MutationResult.CONFLICT,
                    "That exact EPUB already has a managed cleanup intent.");
            }
        }
        if (current.nextAttemptSequence == Long.MAX_VALUE) {
            return stageFailure(MutationResult.EXHAUSTED,
                                "The transfer attempt counter is exhausted.");
        }
        long sequence = current.nextAttemptSequence + 1;
        String attemptId = newAttemptId();
        if (attemptId == null) {
            return stageFailure(
                MutationResult.CONFLICT,
                "A unique transfer attempt identity could not be allocated.");
        }
        boolean active = activeInternal() == null;
        Phase phase = active ? Phase.STAGED : Phase.QUEUED;
        byte[] canonical;
        try {
            canonical = manifest.encode();
        } catch (IOException exception) {
            return stageFailure(MutationResult.INVALID,
                                "The book manifest is not canonical.");
        }
        if (!Arrays.equals(canonical, manifestBytes)) {
            return stageFailure(MutationResult.INVALID,
                                "The book manifest is not canonical.");
        }
        IntentState intent = new IntentState(
            sequence, attemptId, direction, phase,
            DurableDirection.FORWARD,
            0, false, Attention.NONE, manifest, canonical,
            sha256(canonical));
        File createdPart = null;
        if (active && direction == Direction.DOWNLOAD) {
            createdPart = partFile(intent);
            if (!createEmptyPart(createdPart)) {
                return stageFailure(
                    MutationResult.IO_FAILED,
                    "A clean download staging file could not be created.");
            }
        }
        ArrayList<IntentState> intents =
            new ArrayList<>(current.intents);
        intents.add(intent);
        sortIntents(intents);
        State candidate = new State(
            sequence, intents, current.cleanups,
            current.futureManifestBytes, current.futureManifestHash,
            current.futureManifestConflict);
        MutationResult result = publish(candidate);
        if (!result.succeeded()) {
            if (result != MutationResult.PUBLISH_UNCERTAIN
                && createdPart != null && createdPart.isFile()) {
                createdPart.delete();
            }
            return new StageOutcome(
                result,
                result == MutationResult.PUBLISH_UNCERTAIN
                    ? sequence : 0,
                result == MutationResult.PUBLISH_UNCERTAIN
                    ? attemptId : null,
                false);
        }
        if (active && direction == Direction.UPLOAD) {
            runtimeUploadSource = uploadSource;
        }
        return new StageOutcome(
            result, sequence, attemptId, active);
    }

    private CleanupOutcome stageCleanup(String digest,
                                        long byteCount,
                                        CleanupPurpose purpose,
                                        CleanupPhase phase) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return new CleanupOutcome(ready, 0);
        }
        if (!OctavoBookManifest.validDigest(digest)
            || byteCount <= 0
            || byteCount > OctavoBookManifest.maximumDocumentBytes()
            || purpose == null
            || phase == null) {
            lastError = "The managed EPUB cleanup identity is invalid.";
            return new CleanupOutcome(MutationResult.INVALID, 0);
        }
        if (current.intents.size() + current.cleanups.size()
            >= MAX_INTENTS) {
            lastError = "The transfer and cleanup queue is full.";
            return new CleanupOutcome(MutationResult.LIMIT, 0);
        }
        for (IntentState intent : current.intents) {
            if (intent.manifest.digest.equals(digest)) {
                lastError =
                    "That exact EPUB still has a transfer intent.";
                return new CleanupOutcome(MutationResult.CONFLICT, 0);
            }
        }
        for (CleanupState cleanup : current.cleanups) {
            if (cleanup.digest.equals(digest)) {
                lastError =
                    "That exact EPUB already has a cleanup intent.";
                return new CleanupOutcome(MutationResult.CONFLICT, 0);
            }
        }
        if (current.nextAttemptSequence == Long.MAX_VALUE) {
            lastError = "The transfer attempt counter is exhausted.";
            return new CleanupOutcome(MutationResult.EXHAUSTED, 0);
        }
        long sequence = current.nextAttemptSequence + 1;
        ArrayList<CleanupState> cleanups =
            new ArrayList<>(current.cleanups);
        cleanups.add(new CleanupState(
            sequence, digest, byteCount, purpose, phase, false));
        MutationResult result = publish(new State(
            sequence, current.intents, cleanups,
            current.futureManifestBytes, current.futureManifestHash,
            current.futureManifestConflict));
        return new CleanupOutcome(
            result, result.succeeded() ? sequence : 0);
    }

    private MutationResult replaceCleanupAndPublish(
        CleanupState replacement) {
        CleanupState existing = cleanupInternal(
            replacement == null ? 0 : replacement.attemptSequence);
        if (existing == null || replacement == null) {
            return fail(MutationResult.CONFLICT,
                        "The managed cleanup intent changed unexpectedly.");
        }
        ArrayList<CleanupState> cleanups =
            new ArrayList<>(current.cleanups);
        int index = cleanups.indexOf(existing);
        if (index < 0) {
            return fail(MutationResult.CONFLICT,
                        "The managed cleanup intent changed unexpectedly.");
        }
        cleanups.set(index, replacement);
        return publish(current.withCleanups(cleanups));
    }

    private LoadStatus reconcileAfterLoad() throws IOException {
        IntentState active = activeInternal();
        File expectedPart = null;
        if (active != null
            && active.direction == Direction.DOWNLOAD
            && active.phase != Phase.MANAGED_PUBLISHED
            && active.phase != Phase.LOCAL_CATALOG_LINKED) {
            expectedPart = partFile(active);
        }
        boolean removedOrphan = removeUnexpectedParts(expectedPart);
        if (active == null) {
            lastError = removedOrphan
                ? "An orphan transfer part was removed." : "";
            return removedOrphan
                ? LoadStatus.RECOVERED_ORPHAN_REMOVED
                : LoadStatus.LOADED;
        }
        if (active.durableDirection == DurableDirection.CANCEL) {
            if (!cleanupActivePart(active)) {
                mutationsBlocked = true;
                lastError =
                    "Cancellation is durable, but staging cleanup needs Retry.";
                return LoadStatus.PART_RECONCILE_BLOCKED;
            }
            MutationResult removed = removeActiveAndActivateNext();
            if (!removed.succeeded()) {
                if (removed == MutationResult.PUBLISH_UNCERTAIN) {
                    return LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                }
                mutationsBlocked = true;
                return LoadStatus.PART_RECONCILE_BLOCKED;
            }
            lastError = "A durable canceled transfer was cleaned up.";
            return LoadStatus.RECOVERED_CANCEL;
        }
        if (active.direction == Direction.UPLOAD) {
            lastError = (active.phase == Phase.BYTES_VERIFIED
                         || active.phase
                            == Phase.REMOTE_OBJECT_VERIFIED)
                ? ""
                : "Rebind the explicitly approved managed EPUB to continue upload.";
            return removedOrphan
                ? LoadStatus.RECOVERED_ORPHAN_REMOVED
                : LoadStatus.LOADED;
        }
        if (active.phase == Phase.MANAGED_PUBLISHED
            || active.phase == Phase.LOCAL_CATALOG_LINKED) {
            lastError = removedOrphan
                ? "An obsolete transfer part was removed." : "";
            return removedOrphan
                ? LoadStatus.RECOVERED_ORPHAN_REMOVED
                : LoadStatus.LOADED;
        }
        File part = partFile(active);
        if (!part.isFile()) {
            if (active.phase == Phase.READER0_VALIDATED) {
                managedReconcileRequired = true;
                mutationsBlocked = true;
                lastError =
                    "The staging part is absent; reconcile exact managed publication.";
                return LoadStatus.MANAGED_RECONCILE_REQUIRED;
            }
            mutationsBlocked = true;
            lastError = "The durable download prefix has no staging file.";
            return LoadStatus.PART_RECONCILE_BLOCKED;
        }
        PrefixCheck check = verifyPrefix(
            part, active.manifest, active.completedPrefix);
        if (check.exact && !check.extra) {
            lastError = "";
            return removedOrphan
                ? LoadStatus.RECOVERED_ORPHAN_REMOVED
                : LoadStatus.LOADED;
        }
        long validBytes = prefixBytes(active.manifest,
                                      check.validPrefix);
        if (!truncateAndSync(part, validBytes)) {
            mutationsBlocked = true;
            lastError = "The staged EPUB could not be repaired safely.";
            return LoadStatus.PART_RECONCILE_BLOCKED;
        }
        boolean prefixRepaired = check.validPrefix
            != active.completedPrefix;
        Phase phase = check.validPrefix == 0
            ? Phase.STAGED : Phase.TRANSFERRING;
        IntentState repaired = active.withTransfer(
            phase, check.validPrefix, true,
            prefixRepaired
                ? Attention.PREFIX_REPAIRED
                : Attention.EXTRA_TRUNCATED);
        MutationResult published = replaceActiveAndPublish(repaired);
        if (!published.succeeded()) {
            if (published == MutationResult.PUBLISH_UNCERTAIN) {
                return LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
            }
            mutationsBlocked = true;
            return LoadStatus.PART_RECONCILE_BLOCKED;
        }
        lastError = prefixRepaired
            ? "A damaged durable prefix was rolled back to verified chunks."
            : "Uncommitted staging bytes were truncated.";
        return prefixRepaired
            ? LoadStatus.RECOVERED_PREFIX_REPAIRED
            : LoadStatus.RECOVERED_EXTRA_TRUNCATED;
    }

    private MutationResult removeActiveAndActivateNext() {
        IntentState active = activeInternal();
        if (active == null) {
            return fail(MutationResult.CONFLICT,
                        "There is no active transfer.");
        }
        ArrayList<IntentState> intents =
            new ArrayList<>(current.intents);
        if (!intents.remove(active)) {
            return fail(MutationResult.CONFLICT,
                        "The active transfer changed unexpectedly.");
        }
        File createdPart = null;
        if (!intents.isEmpty()) {
            IntentState next = intents.get(0);
            if (next.phase != Phase.QUEUED) {
                return fail(MutationResult.CONFLICT,
                            "The queued transfer order is invalid.");
            }
            next = next.activate();
            intents.set(0, next);
            if (next.direction == Direction.DOWNLOAD) {
                createdPart = partFile(next);
                if (!createEmptyPart(createdPart)) {
                    return fail(
                        MutationResult.CLEANUP_FAILED,
                        "The next download could not create clean staging.");
                }
            }
        }
        MutationResult result = publish(current.withIntents(intents));
        if (!result.succeeded()
            && result != MutationResult.PUBLISH_UNCERTAIN
            && createdPart != null && createdPart.isFile()) {
            createdPart.delete();
        }
        return result;
    }

    private MutationResult replaceActiveAndPublish(IntentState replacement) {
        IntentState active = activeInternal();
        if (active == null
            || replacement == null
            || active.attemptSequence != replacement.attemptSequence) {
            return fail(MutationResult.CONFLICT,
                        "The active transfer changed unexpectedly.");
        }
        ArrayList<IntentState> intents =
            new ArrayList<>(current.intents);
        int index = intents.indexOf(active);
        if (index < 0) {
            return fail(MutationResult.CONFLICT,
                        "The active transfer changed unexpectedly.");
        }
        intents.set(index, replacement);
        return publish(current.withIntents(intents));
    }

    private void persistAttentionBestEffort(IntentState replacement) {
        MutationResult result = replaceActiveAndPublish(replacement);
        if (result == MutationResult.PUBLISH_UNCERTAIN) {
            mutationsBlocked = true;
        }
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
            if (bytes.length <= 0 || bytes.length > MAX_STATE_BYTES) {
                return fail(MutationResult.LIMIT,
                            "Book transfer state exceeds 1 MiB.");
            }
            if (previousExists != stateExpectedOnDisk) {
                return uncertain(
                    "Book transfer destination presence changed; reload is required.");
            }
            if (previousExists) {
                previous = readBounded(stateFile);
                if (!Arrays.equals(previous, encodeState(current))) {
                    return uncertain(
                        "Book transfer state changed unexpectedly; reload is required.");
                }
                destinationVerified = true;
            } else if (stateFile.exists()) {
                return uncertain(
                    "Book transfer destination is not a regular file.");
            } else {
                destinationVerified = true;
            }
            requireDirectory(rootDirectory);
            if (failNextPublishForTesting) {
                failNextPublishForTesting = false;
                throw new IOException("Injected O1BQ publish failure");
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
                    "Injected uncertain O1BQ replace result");
            }
            State previousState = current;
            current = candidate;
            stateExpectedOnDisk = true;
            updateRuntimeAfterStateChange(previousState, candidate);
            lastError = "";
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            if (candidateEncoded && !destinationVerified) {
                return uncertain(
                    "Book transfer destination could not be verified; reload is required.");
            }
            if (moveAttempted
                && !destinationStillEquals(previousExists, previous)) {
                return uncertain(
                    "Book transfer publication is uncertain; reload is required.");
            }
            return fail(MutationResult.PUBLISH_FAILED,
                        "Book transfer state could not be saved. Retry.");
        }
    }

    private static byte[] encodeState(State state) throws IOException {
        validateState(state);
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(STORE_MAGIC);
            output.writeInt(STORE_VERSION);
            output.writeInt(STORE_FIELD_COUNT);
            output.writeLong(state.nextAttemptSequence);
            output.writeInt(state.intents.size());
            output.writeInt(state.cleanups.size());
            output.writeInt(state.futureManifestBytes == null
                            ? 0 : state.futureManifestBytes.length);
            output.writeInt(state.futureManifestConflict ? 1 : 0);
            for (IntentState intent : state.intents) {
                output.writeLong(intent.attemptSequence);
                output.write(
                    intent.attemptId.getBytes(StandardCharsets.US_ASCII));
                output.writeInt(intent.direction.wireId);
                output.writeInt(intent.phase.wireId);
                output.writeInt(intent.durableDirection.wireId);
                output.writeInt(intent.completedPrefix);
                output.writeInt(intent.retryRequired ? 1 : 0);
                output.writeInt(intent.attention.wireId);
                output.writeInt(intent.manifestBytes.length);
                output.write(intent.manifestHash);
                output.write(intent.manifestBytes);
            }
            for (CleanupState cleanup : state.cleanups) {
                output.writeLong(cleanup.attemptSequence);
                output.write(
                    cleanup.digest.getBytes(StandardCharsets.US_ASCII));
                output.writeLong(cleanup.byteCount);
                output.writeInt(cleanup.purpose.wireId);
                output.writeInt(cleanup.phase.wireId);
                output.writeInt(cleanup.retryRequired ? 1 : 0);
                boolean hasOrigin = cleanup.originAttemptId != null;
                output.writeInt(hasOrigin ? 1 : 0);
                if (hasOrigin) {
                    output.write(cleanup.originAttemptId.getBytes(
                        StandardCharsets.US_ASCII));
                    output.write(cleanup.originManifestHash);
                }
            }
            if (state.futureManifestBytes != null) {
                output.write(state.futureManifestHash);
                output.write(state.futureManifestBytes);
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
        if (bytes.length > MAX_STATE_BYTES) {
            throw new IOException("O1BQ exceeds its byte cap");
        }
        return bytes;
    }

    private static State decodeState(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 40
            || bytes.length > MAX_STATE_BYTES) {
            throw new IOException("Invalid O1BQ length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, payloadLength);
        if (readInt(bytes, payloadLength)
            != (int)checksum.getValue()) {
            throw new IOException("Invalid O1BQ checksum");
        }
        try {
            DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, payloadLength));
            if (input.readInt() != STORE_MAGIC
                || input.readInt() != STORE_VERSION
                || input.readInt() != STORE_FIELD_COUNT) {
                throw new IOException("Invalid O1BQ header");
            }
            long nextAttempt = input.readLong();
            int count = input.readInt();
            int cleanupCount = input.readInt();
            int futureLength = input.readInt();
            int futureConflict = input.readInt();
            if (nextAttempt < 0 || count < 0 || cleanupCount < 0
                || count + cleanupCount > MAX_INTENTS
                || futureLength < 0
                || futureLength
                   > OctavoBookManifest.maximumFutureBytes()
                || (futureConflict != 0 && futureConflict != 1)
                || (futureLength == 0 && futureConflict != 0)) {
                throw new IOException("Invalid O1BQ counters");
            }
            ArrayList<IntentState> intents = new ArrayList<>(count);
            for (int index = 0; index < count; ++index) {
                long attempt = input.readLong();
                byte[] attemptIdBytes = new byte[ATTEMPT_ID_BYTES];
                input.readFully(attemptIdBytes);
                String attemptId = new String(
                    attemptIdBytes, StandardCharsets.US_ASCII);
                Direction direction = Direction.fromWireId(input.readInt());
                Phase phase = Phase.fromWireId(input.readInt());
                DurableDirection durable =
                    DurableDirection.fromWireId(input.readInt());
                int prefix = input.readInt();
                int retry = input.readInt();
                Attention attention = Attention.fromWireId(input.readInt());
                int manifestLength = input.readInt();
                byte[] manifestHash = new byte[MANIFEST_HASH_BYTES];
                input.readFully(manifestHash);
                if (manifestLength < OctavoBookManifest.exactEncodedLength(1)
                    || manifestLength > OctavoBookManifest.maximumV1Bytes()
                    || manifestLength > input.available()) {
                    throw new IOException("Invalid O1BQ manifest length");
                }
                byte[] manifestBytes = new byte[manifestLength];
                input.readFully(manifestBytes);
                OctavoBookManifest.DecodeResult decoded =
                    OctavoBookManifest.decode(manifestBytes);
                if (decoded.status != OctavoBookManifest.DecodeStatus.READY
                    || !Arrays.equals(sha256(manifestBytes), manifestHash)) {
                    throw new IOException("Invalid O1BQ manifest");
                }
                intents.add(new IntentState(
                    attempt, attemptId, direction, phase, durable, prefix,
                    retry == 1, attention, decoded.manifest(),
                    manifestBytes, manifestHash));
                if (retry != 0 && retry != 1) {
                    throw new IOException("Invalid O1BQ retry flag");
                }
            }
            ArrayList<CleanupState> cleanups =
                new ArrayList<>(cleanupCount);
            for (int index = 0; index < cleanupCount; ++index) {
                long attempt = input.readLong();
                byte[] digestBytes = new byte[64];
                input.readFully(digestBytes);
                String digest = new String(
                    digestBytes, StandardCharsets.US_ASCII);
                long byteCount = input.readLong();
                CleanupPurpose purpose =
                    CleanupPurpose.fromWireId(input.readInt());
                CleanupPhase phase =
                    CleanupPhase.fromWireId(input.readInt());
                int retry = input.readInt();
                if (retry != 0 && retry != 1) {
                    throw new IOException(
                        "Invalid O1BQ cleanup retry flag");
                }
                int hasOrigin = input.readInt();
                String originAttemptId = null;
                byte[] originManifestHash = null;
                if (hasOrigin == 1) {
                    byte[] originAttemptIdBytes =
                        new byte[ATTEMPT_ID_BYTES];
                    input.readFully(originAttemptIdBytes);
                    originAttemptId = new String(
                        originAttemptIdBytes, StandardCharsets.US_ASCII);
                    originManifestHash =
                        new byte[MANIFEST_HASH_BYTES];
                    input.readFully(originManifestHash);
                } else if (hasOrigin != 0) {
                    throw new IOException(
                        "Invalid O1BQ cleanup origin flag");
                }
                cleanups.add(new CleanupState(
                    attempt, digest, byteCount, purpose, phase,
                    retry == 1, originAttemptId,
                    originManifestHash));
            }
            byte[] futureManifest = null;
            byte[] futureHash = null;
            if (futureLength > 0) {
                if (input.available()
                    != MANIFEST_HASH_BYTES + futureLength) {
                    throw new IOException(
                        "Invalid retained future O1BM length");
                }
                futureHash = new byte[MANIFEST_HASH_BYTES];
                input.readFully(futureHash);
                futureManifest = new byte[futureLength];
                input.readFully(futureManifest);
            }
            if (input.available() != 0) {
                throw new IOException("Trailing O1BQ payload");
            }
            State result = new State(
                nextAttempt, intents, cleanups,
                futureManifest, futureHash, futureConflict == 1);
            validateState(result);
            if (!Arrays.equals(bytes, encodeState(result))) {
                throw new IOException("Noncanonical O1BQ bytes");
            }
            return result;
        } catch (EOFException exception) {
            throw new IOException("Truncated O1BQ", exception);
        }
    }

    private static void validateState(State state) throws IOException {
        if (state == null || state.nextAttemptSequence < 0
            || state.intents.size() + state.cleanups.size()
               > MAX_INTENTS) {
            throw new IOException("Invalid O1BQ state");
        }
        int activeCount = 0;
        ArrayList<String> digests = new ArrayList<>();
        ArrayList<String> attemptIds = new ArrayList<>();
        ArrayList<Long> attempts = new ArrayList<>();
        IntentState previousIntent = null;
        for (IntentState intent : state.intents) {
            if (intent == null
                || intent.attemptSequence <= 0
                || intent.attemptSequence > state.nextAttemptSequence
                || !validAttemptId(intent.attemptId)
                || intent.direction == null
                || intent.phase == null
                || intent.durableDirection == null
                || intent.attention == null
                || intent.manifest == null
                || intent.manifestBytes == null
                || intent.manifestHash == null
                || intent.manifestHash.length != MANIFEST_HASH_BYTES
                || !Arrays.equals(
                    intent.manifestHash, sha256(intent.manifestBytes))
                || intent.completedPrefix < 0
                || intent.completedPrefix > intent.manifest.chunkCount
                || digests.contains(intent.manifest.digest)
                || attemptIds.contains(intent.attemptId)
                || attempts.contains(intent.attemptSequence)) {
                throw new IOException("Invalid O1BQ intent");
            }
            if (previousIntent != null
                && compareIntents(previousIntent, intent) >= 0) {
                throw new IOException("Noncanonical O1BQ queue order");
            }
            OctavoBookManifest.DecodeResult decoded =
                OctavoBookManifest.decode(intent.manifestBytes);
            if (decoded.status != OctavoBookManifest.DecodeStatus.READY
                || !decoded.manifest().sameIdentity(intent.manifest)) {
                throw new IOException("Invalid O1BQ canonical manifest");
            }
            if (intent.phase == Phase.QUEUED) {
                if (intent.completedPrefix != 0
                    || intent.durableDirection != DurableDirection.FORWARD
                    || intent.retryRequired
                    || intent.attention != Attention.NONE) {
                    throw new IOException("Invalid queued O1BQ intent");
                }
            } else {
                activeCount += 1;
                if (activeCount > 1) {
                    throw new IOException("Multiple active O1BQ intents");
                }
                validateActiveIntent(intent);
            }
            digests.add(intent.manifest.digest);
            attemptIds.add(intent.attemptId);
            attempts.add(intent.attemptSequence);
            previousIntent = intent;
        }
        if (!state.intents.isEmpty() && activeCount != 1) {
            throw new IOException("O1BQ queue has no active intent");
        }
        long previousAttempt = 0;
        for (CleanupState cleanup : state.cleanups) {
            boolean hasOrigin = cleanup != null
                && (cleanup.originAttemptId != null
                    || cleanup.originManifestHash != null);
            if (cleanup == null
                || cleanup.attemptSequence <= previousAttempt
                || cleanup.attemptSequence > state.nextAttemptSequence
                || !OctavoBookManifest.validDigest(cleanup.digest)
                || cleanup.byteCount <= 0
                || cleanup.byteCount
                   > OctavoBookManifest.maximumDocumentBytes()
                || cleanup.purpose == null
                || cleanup.phase == null
                || (cleanup.purpose == CleanupPurpose.UNCATALOGED
                    && cleanup.phase
                       == CleanupPhase.AWAITING_CATALOG_UNLINK)
                || (cleanup.retryRequired
                    && cleanup.phase != CleanupPhase.READY_TO_DELETE)
                || (hasOrigin
                    && (cleanup.purpose
                        != CleanupPurpose.REPAIR_REPLACE
                        || !validAttemptId(cleanup.originAttemptId)
                        || cleanup.originManifestHash == null
                        || cleanup.originManifestHash.length
                           != MANIFEST_HASH_BYTES
                        || attemptIds.contains(
                            cleanup.originAttemptId)))
                || (!hasOrigin
                    && (cleanup.originAttemptId != null
                        || cleanup.originManifestHash != null))
                || digests.contains(cleanup.digest)
                || attempts.contains(cleanup.attemptSequence)) {
                throw new IOException("Invalid O1BQ cleanup intent");
            }
            digests.add(cleanup.digest);
            attempts.add(cleanup.attemptSequence);
            if (hasOrigin) {
                attemptIds.add(cleanup.originAttemptId);
            }
            previousAttempt = cleanup.attemptSequence;
        }
        if (state.futureManifestBytes == null) {
            if (state.futureManifestHash != null
                || state.futureManifestConflict) {
                throw new IOException("Invalid empty future O1BM slot");
            }
        } else {
            if (state.futureManifestHash == null
                || state.futureManifestHash.length
                   != MANIFEST_HASH_BYTES
                || state.futureManifestBytes.length
                   > OctavoBookManifest.maximumFutureBytes()
                || !Arrays.equals(
                    state.futureManifestHash,
                    sha256(state.futureManifestBytes))
                || OctavoBookManifest.decode(state.futureManifestBytes)
                       .status
                   != OctavoBookManifest.DecodeStatus.FUTURE_VERSION) {
                throw new IOException("Invalid retained future O1BM");
            }
        }
    }

    private static void validateActiveIntent(IntentState intent)
        throws IOException {
        if (intent.direction == Direction.UPLOAD) {
            if (intent.phase == Phase.READER0_VALIDATED
                || intent.phase == Phase.MANAGED_PUBLISHED
                || intent.phase == Phase.LOCAL_CATALOG_LINKED) {
                throw new IOException("Invalid upload phase");
            }
        } else {
            if (intent.phase == Phase.REMOTE_OBJECT_VERIFIED) {
                throw new IOException("Invalid download phase");
            }
        }
        if (intent.phase == Phase.STAGED
            && intent.completedPrefix != 0) {
            throw new IOException("Invalid staged prefix");
        }
        if (intent.phase == Phase.TRANSFERRING
            && (intent.completedPrefix <= 0
                || intent.completedPrefix
                   > intent.manifest.chunkCount)) {
            throw new IOException("Invalid transferring prefix");
        }
        if ((intent.phase == Phase.BYTES_VERIFIED
             || intent.phase == Phase.READER0_VALIDATED
             || intent.phase == Phase.MANAGED_PUBLISHED
             || intent.phase == Phase.LOCAL_CATALOG_LINKED
             || intent.phase == Phase.REMOTE_OBJECT_VERIFIED)
            && intent.completedPrefix != intent.manifest.chunkCount) {
            throw new IOException("Incomplete verified transfer");
        }
        if (intent.attention == Attention.NONE
            && intent.retryRequired) {
            throw new IOException("Retry lacks O1BQ attention");
        }
        if (intent.durableDirection == DurableDirection.CANCEL
            && (intent.attention != Attention.CANCEL_CLEANUP
                || !cancellableBeforePublication(intent))) {
            throw new IOException("Invalid O1BQ cancel state");
        }
        if (intent.durableDirection == DurableDirection.FORWARD
            && !validForwardAttention(intent)) {
            throw new IOException("Invalid O1BQ attention for phase");
        }
    }

    private static boolean validForwardAttention(IntentState intent) {
        if (intent.attention == Attention.NONE) {
            return !intent.retryRequired;
        }
        if (!intent.retryRequired) {
            return false;
        }
        switch (intent.attention) {
            case RETRY_CHUNK:
                return intent.phase == Phase.STAGED
                    || intent.phase == Phase.TRANSFERRING;
            case PREFIX_REPAIRED:
            case EXTRA_TRUNCATED:
                return intent.direction == Direction.DOWNLOAD
                    && (intent.phase == Phase.STAGED
                        || intent.phase == Phase.TRANSFERRING);
            case READER0_REJECTED:
                return intent.direction == Direction.DOWNLOAD
                    && intent.phase == Phase.BYTES_VERIFIED;
            case CATALOG_LINK_FAILED:
                return intent.direction == Direction.DOWNLOAD
                    && intent.phase == Phase.MANAGED_PUBLISHED;
            case REMOTE_OBJECT_MISMATCH:
                return intent.direction == Direction.UPLOAD
                    && intent.phase == Phase.BYTES_VERIFIED;
            case COMPLETE_HASH_MISMATCH:
                return intent.phase == Phase.TRANSFERRING
                    || (intent.direction == Direction.DOWNLOAD
                        && intent.phase == Phase.READER0_VALIDATED);
            case MANAGED_DESTINATION_CONFLICT:
                return intent.direction == Direction.DOWNLOAD
                    && intent.phase == Phase.READER0_VALIDATED;
            default:
                return false;
        }
    }

    private PrefixCheck verifyPrefix(File file,
                                     OctavoBookManifest manifest,
                                     int declaredPrefix)
        throws IOException {
        if (declaredPrefix < 0
            || declaredPrefix > manifest.chunkCount
            || !file.isFile()) {
            return new PrefixCheck(0, false, false);
        }
        long length = file.length();
        int valid = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            for (int index = 0; index < declaredPrefix; ++index) {
                int remaining = manifest.expectedChunkLength(index);
                MessageDigest digest = sha256Digest();
                while (remaining > 0) {
                    int count = input.read(
                        buffer, 0, Math.min(buffer.length, remaining));
                    if (count < 0) {
                        return new PrefixCheck(valid, false, false);
                    }
                    if (count == 0) {
                        continue;
                    }
                    digest.update(buffer, 0, count);
                    remaining -= count;
                }
                if (!Arrays.equals(
                        digest.digest(), manifest.chunkHash(index))) {
                    return new PrefixCheck(valid, false, false);
                }
                valid += 1;
            }
        }
        long expected = prefixBytes(manifest, declaredPrefix);
        return new PrefixCheck(valid, length >= expected,
                               length > expected);
    }

    private static boolean fileMatchesManifest(
        File file,
        OctavoBookManifest manifest) {
        if (file == null || manifest == null
            || !file.isFile() || file.length() != manifest.byteCount) {
            return false;
        }
        try {
            return manifest.sameIdentity(OctavoBookManifest.build(file));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private boolean createEmptyPart(File part) {
        try {
            requireDirectory(managedDirectory);
            if (!isControlledPart(part) || part.exists()) {
                return false;
            }
            try (FileOutputStream output =
                     new FileOutputStream(part, false)) {
                output.flush();
                output.getFD().sync();
            }
            return part.isFile() && part.length() == 0;
        } catch (IOException | RuntimeException exception) {
            if (part != null && part.isFile()) {
                part.delete();
            }
            return false;
        }
    }

    private boolean cleanupActivePart(IntentState active) {
        if (active == null || active.direction != Direction.DOWNLOAD
            || active.phase == Phase.MANAGED_PUBLISHED
            || active.phase == Phase.LOCAL_CATALOG_LINKED) {
            return true;
        }
        File part = partFile(active);
        return !part.exists() || (part.isFile() && part.delete());
    }

    private boolean removeUnexpectedParts(File expected) throws IOException {
        requireDirectory(managedDirectory);
        File[] files = managedDirectory.listFiles();
        if (files == null) {
            throw new IOException("Transfer directory cannot be listed");
        }
        boolean removed = false;
        File expectedCanonical = expected == null
            ? null : expected.getCanonicalFile();
        for (File file : files) {
            if (!isPartName(file.getName())) {
                continue;
            }
            if (expectedCanonical != null
                && file.getCanonicalFile().equals(expectedCanonical)) {
                continue;
            }
            if (!file.isFile() || !file.delete()) {
                throw new IOException(
                    "Unexpected transfer staging cannot be removed");
            }
            removed = true;
        }
        return removed;
    }

    private File managedDestination(File directory, String digest)
        throws IOException {
        if (directory == null || !OctavoBookManifest.validDigest(digest)) {
            throw new IOException("Invalid managed directory");
        }
        File canonicalDirectory = directory.getCanonicalFile();
        File configuredDirectory = managedDirectory.getCanonicalFile();
        if (!canonicalDirectory.equals(configuredDirectory)) {
            throw new IOException(
                "Managed directory does not match the configured owner");
        }
        requireDirectory(configuredDirectory);
        if (!canonicalDirectory.isDirectory()) {
            throw new IOException("Managed directory is unavailable");
        }
        File destination =
            new File(canonicalDirectory, digest + ".epub");
        File canonicalDestination = destination.getCanonicalFile();
        if (!canonicalDirectory.equals(
                canonicalDestination.getParentFile())) {
            throw new IOException("Managed destination escapes its directory");
        }
        return canonicalDestination;
    }

    private File controlledManagedUploadSource(File source)
        throws IOException {
        if (source == null) {
            throw new IOException("Missing managed upload source");
        }
        File canonicalDirectory = managedDirectory.getCanonicalFile();
        File canonicalSource = source.getCanonicalFile();
        if (!canonicalDirectory.isDirectory()
            || !canonicalSource.isFile()
            || !canonicalDirectory.equals(
                canonicalSource.getParentFile())) {
            throw new IOException(
                "Upload source is outside the managed directory");
        }
        return canonicalSource;
    }

    private boolean isControlledPart(File part) {
        try {
            return part != null
                && isPartName(part.getName())
                && managedDirectory.getCanonicalFile().equals(
                    part.getCanonicalFile().getParentFile());
        } catch (IOException exception) {
            return false;
        }
    }

    private File partFile(IntentState intent) {
        if (intent == null
            || !OctavoBookManifest.validDigest(intent.manifest.digest)
            || !validAttemptId(intent.attemptId)) {
            throw new IllegalArgumentException("Invalid transfer identity");
        }
        return new File(
            managedDirectory,
            intent.manifest.digest + "." + intent.attemptId
                + PART_SUFFIX);
    }

    private static boolean isPartName(String name) {
        if (name == null
            || name.length() != 64 + 1 + ATTEMPT_ID_BYTES
                               + PART_SUFFIX.length()
            || name.charAt(64) != '.'
            || !name.endsWith(PART_SUFFIX)) {
            return false;
        }
        return OctavoBookManifest.validDigest(
            name.substring(0, 64))
            && validAttemptId(name.substring(65, 65 + ATTEMPT_ID_BYTES));
    }

    private IntentState activeInternal() {
        for (IntentState intent : current.intents) {
            if (intent.phase.active()) {
                return intent;
            }
        }
        return null;
    }

    private String newAttemptId() {
        for (int tries = 0; tries < 8; ++tries) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String candidate = lowerHex(bytes);
            boolean duplicate = false;
            for (IntentState intent : current.intents) {
                if (intent.attemptId.equals(candidate)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                for (CleanupState cleanup : current.cleanups) {
                    if (candidate.equals(cleanup.originAttemptId)) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate) {
                return candidate;
            }
        }
        return null;
    }

    private static void sortIntents(ArrayList<IntentState> intents) {
        intents.sort(new Comparator<IntentState>() {
            @Override
            public int compare(IntentState left, IntentState right) {
                return compareIntents(left, right);
            }
        });
    }

    private static int compareIntents(IntentState left,
                                      IntentState right) {
        int digest = left.manifest.digest.compareTo(
            right.manifest.digest);
        return digest != 0
            ? digest : left.attemptId.compareTo(right.attemptId);
    }

    private static boolean validAttemptId(String value) {
        if (value == null || value.length() != ATTEMPT_ID_BYTES) {
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

    private CleanupState cleanupInternal(long attemptSequence) {
        for (CleanupState cleanup : current.cleanups) {
            if (cleanup.attemptSequence == attemptSequence) {
                return cleanup;
            }
        }
        return null;
    }

    private CleanupToken cleanupToken(long attemptSequence) {
        for (CleanupToken token : runtimeCleanupTokens) {
            if (token.attemptSequence == attemptSequence) {
                return token;
            }
        }
        return null;
    }

    private MutationResult requireMutable() {
        if (!loadAttempted) {
            return fail(MutationResult.BLOCKED,
                        "Load book transfer state first.");
        }
        if (mutationsBlocked || managedReconcileRequired) {
            return fail(MutationResult.BLOCKED,
                        "Book transfer recovery is required.");
        }
        return null;
    }

    private MutationResult requireActiveToken(CallbackToken token) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (!validToken(token, active)) {
            return fail(MutationResult.CONFLICT,
                        "The transfer callback token is stale.");
        }
        return null;
    }

    private MutationResult requireCleanupToken(CleanupToken token) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (token == null || token.nonce.length != 16
            || token != cleanupToken(token.attemptSequence)
            || cleanupInternal(token.attemptSequence) == null) {
            return fail(MutationResult.CONFLICT,
                        "The managed cleanup callback token is stale.");
        }
        return null;
    }

    private MutationResult requireActive(
        CallbackToken token,
        Direction direction,
        DurableDirection durableDirection) {
        MutationResult ready = requireActiveToken(token);
        if (ready != null) {
            return ready;
        }
        IntentState active = activeInternal();
        if (active.direction != direction
            || active.durableDirection != durableDirection) {
            return fail(MutationResult.CONFLICT,
                        "The transfer callback does not match the active job.");
        }
        return null;
    }

    private MutationResult requireActiveAllowManagedReconcile(
        CallbackToken token,
        Direction direction) {
        if (!loadAttempted) {
            return fail(MutationResult.BLOCKED,
                        "Load book transfer state first.");
        }
        IntentState active = activeInternal();
        if (!validToken(token, active)
            || active == null || active.direction != direction) {
            return fail(MutationResult.CONFLICT,
                        "The transfer callback token is stale.");
        }
        if (mutationsBlocked && !managedReconcileRequired) {
            return fail(MutationResult.BLOCKED,
                        "Book transfer recovery is required.");
        }
        return null;
    }

    private boolean validToken(CallbackToken token,
                               IntentState active) {
        return token != null && token == runtimeToken
            && active != null
            && token.attemptSequence == active.attemptSequence
            && token.attemptId.equals(active.attemptId)
            && token.nonce.length == 16;
    }

    private void refreshRuntimeToken() {
        runtimeCleanupTokens.clear();
        for (CleanupState cleanup : current.cleanups) {
            runtimeCleanupTokens.add(newCleanupToken(
                cleanup.attemptSequence));
        }
        IntentState active = activeInternal();
        if (active == null) {
            runtimeToken = null;
            runtimeUploadSource = null;
            lastUploadReadIndex = -1;
            return;
        }
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        runtimeToken = new CallbackToken(
            active.attemptSequence, active.attemptId, nonce);
        runtimeUploadSource = null;
        lastUploadReadIndex = -1;
    }

    private void updateRuntimeAfterStateChange(State previous,
                                               State candidate) {
        IntentState before = activeIn(previous);
        IntentState after = activeIn(candidate);
        boolean activeChanged = (before == null) != (after == null)
            || (before != null && after != null
                && before.attemptSequence != after.attemptSequence);
        if (activeChanged) {
            refreshRuntimeToken();
            return;
        }
        synchronizeCleanupTokens(candidate.cleanups);
    }

    private void synchronizeCleanupTokens(
        List<CleanupState> cleanups) {
        ArrayList<CleanupToken> next = new ArrayList<>(cleanups.size());
        for (CleanupState cleanup : cleanups) {
            CleanupToken existing = cleanupToken(
                cleanup.attemptSequence);
            next.add(existing == null
                ? newCleanupToken(cleanup.attemptSequence) : existing);
        }
        runtimeCleanupTokens.clear();
        runtimeCleanupTokens.addAll(next);
    }

    private CleanupToken newCleanupToken(long attemptSequence) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        return new CleanupToken(attemptSequence, nonce);
    }

    private static IntentState activeIn(State state) {
        for (IntentState intent : state.intents) {
            if (intent.phase.active()) {
                return intent;
            }
        }
        return null;
    }

    private MutationResult unchanged() {
        lastError = "";
        return MutationResult.UNCHANGED;
    }

    private MutationResult fail(MutationResult result, String message) {
        lastError = message;
        return result;
    }

    private StageOutcome stageFailure(MutationResult result,
                                      String message) {
        lastError = message;
        return new StageOutcome(result, 0, null, false);
    }

    private ChunkRead readFailure(ChunkReadStatus status,
                                  int index,
                                  String message) {
        lastError = message;
        return new ChunkRead(status, index, 0);
    }

    private MutationResult uncertain(String message) {
        mutationsBlocked = true;
        loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
        return fail(MutationResult.PUBLISH_UNCERTAIN, message);
    }

    private LoadStatus block(LoadStatus status, String message) {
        mutationsBlocked = true;
        loadStatus = status;
        lastError = message;
        return status;
    }

    private static ChunkReadStatus chunkStatus(MutationResult result) {
        if (result == MutationResult.CONFLICT) {
            return ChunkReadStatus.CONFLICT;
        }
        if (result == MutationResult.INVALID) {
            return ChunkReadStatus.INVALID;
        }
        if (result == MutationResult.IO_FAILED) {
            return ChunkReadStatus.IO_FAILED;
        }
        if (result == MutationResult.VERIFY_FAILED) {
            return ChunkReadStatus.VERIFY_FAILED;
        }
        return ChunkReadStatus.BLOCKED;
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

    synchronized boolean hasCorruptQuarantineAttention() {
        return hasQuarantineEvidence();
    }

    private boolean hasQuarantineEvidence() {
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            if (quarantineFileForTesting(index).exists()) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0
            || file.length() > MAX_STATE_BYTES) {
            throw new IOException("Invalid O1BQ file length");
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
                throw new IOException("O1BQ changed while reading");
            }
        }
        return result;
    }

    private static boolean isFutureStore(byte[] bytes) {
        return bytes != null && bytes.length >= 2 * Integer.BYTES
            && readInt(bytes, 0) == STORE_MAGIC
            && Integer.compareUnsigned(
                   readInt(bytes, Integer.BYTES), STORE_VERSION) > 0;
    }

    private static long prefixBytes(OctavoBookManifest manifest,
                                    int prefix) {
        if (prefix < 0 || prefix > manifest.chunkCount) {
            throw new IllegalArgumentException("Invalid chunk prefix");
        }
        if (prefix == manifest.chunkCount) {
            return manifest.byteCount;
        }
        return (long)prefix * manifest.chunkSize;
    }

    private static boolean truncateAndSync(File file, long length) {
        if (file == null || !file.isFile() || length < 0) {
            return false;
        }
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.setLength(length);
            output.getFD().sync();
            return output.length() == length;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static byte[] sha256(byte[] bytes) {
        MessageDigest digest = sha256Digest();
        digest.update(bytes, 0, bytes.length);
        return digest.digest();
    }

    private static String lowerHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; ++index) {
            int value = bytes[index] & 0xff;
            result[2 * index] = digits[value >>> 4];
            result[2 * index + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable", exception);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
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

    private static void requireDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(
                "Unable to create " + directory.getAbsolutePath());
        }
    }

    private void deleteTemporaryBestEffort() {
        if (temporaryFile.isFile()) {
            temporaryFile.delete();
        }
    }

    static void clearForTesting(Context context) {
        File filesDirectory = requireFilesDirectory(context);
        File stateRoot = new File(filesDirectory, ROOT_DIRECTORY);
        deleteOwnedForTesting(new File(stateRoot, STATE_FILE));
        deleteOwnedForTesting(new File(stateRoot, TEMPORARY_FILE));
        for (int slot = 1; slot <= QUARANTINE_SLOTS; ++slot) {
            deleteOwnedForTesting(new File(
                stateRoot, QUARANTINE_PREFIX + slot));
        }
        File documents = new File(
            new File(filesDirectory, "port6"), "documents");
        File[] files = documents.listFiles();
        if (files == null) {
            if (documents.exists() && !documents.isDirectory()) {
                throw new IllegalStateException(
                    "Managed document directory is invalid");
            }
            return;
        }
        for (File file : files) {
            if (isPartName(file.getName())) {
                deleteOwnedForTesting(file);
            }
        }
    }

    private static void deleteOwnedForTesting(File file) {
        if (!file.exists()) {
            return;
        }
        if (!file.isFile() || !file.delete()) {
            throw new IllegalStateException(
                "Unable to clear owned book-transfer test state");
        }
    }

    File stateFileForTesting() {
        return stateFile;
    }

    File partFileForTesting(String digest, String attemptId) {
        if (!OctavoBookManifest.validDigest(digest)
            || !validAttemptId(attemptId)) {
            throw new IllegalArgumentException(
                "Invalid transfer identity");
        }
        return new File(
            managedDirectory,
            digest + "." + attemptId + PART_SUFFIX);
    }

    File managedDirectoryForTesting() {
        return managedDirectory;
    }

    File quarantineFileForTesting(int slot) {
        return new File(rootDirectory, QUARANTINE_PREFIX + slot);
    }

    synchronized byte[] canonicalBytesForTesting() throws IOException {
        return encodeState(current);
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    synchronized void failNextMoveAfterReplaceForTesting() {
        failNextMoveAfterReplaceForTesting = true;
    }

    static int storeMagicForTesting() {
        return STORE_MAGIC;
    }

    static int storeVersionForTesting() {
        return STORE_VERSION;
    }
}
