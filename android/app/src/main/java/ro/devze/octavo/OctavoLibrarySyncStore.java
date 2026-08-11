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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;

/** Independent private O1LS state for add-only Library synchronization. */
final class OctavoLibrarySyncStore {
    enum LoadStatus {
        MISSING_EMPTY,
        LOADED,
        INITIAL_PUBLISH_FAILED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        OVERBOUND_BLOCKED,
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
        STAGED_DIGEST_MISMATCH,
        NO_STAGED_CURRENT,
        LIMIT_RETAINED,
        INVALID,
        EQUIVOCATION,
        BLOCKED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == MERGED || this == UNCHANGED;
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
        BLOCKED,
        PUBLISH_FAILED,
        PUBLISH_UNCERTAIN;

        boolean succeeded() {
            return this == STAGED_CURRENT || this == UNCHANGED
                || this == LIMIT_RETAINED
                || this == FUTURE_RETAINED;
        }
    }

    enum StagedKind {
        CURRENT(1),
        LIMIT(2),
        FUTURE(3);

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

    enum LocalReconciliationKind {
        PUBLICATION(1),
        REMOVAL(2);

        final int wireId;

        LocalReconciliationKind(int wireId) {
            this.wireId = wireId;
        }

        static LocalReconciliationKind fromWireId(int wireId) {
            for (LocalReconciliationKind value : values()) {
                if (value.wireId == wireId) {
                    return value;
                }
            }
            return null;
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
        DOWNLOADED(1),
        IGNORED(2),
        LOCAL_REMOVED(3),
        DISMISSED_AT_EPOCH(4);

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

    static final class Candidate {
        final String digest;
        final long byteCount;
        final int kind;
        final long reviewEpoch;
        final Decision decision;
        private final OctavoLibraryPortable.Descriptor descriptor;

        private Candidate(RecordState record, long epoch) {
            descriptor = record.descriptor;
            digest = descriptor.digest;
            byteCount = descriptor.byteCount;
            kind = descriptor.kind;
            reviewEpoch = epoch;
            decision = record.decision;
        }

        OctavoLibraryPortable.Descriptor descriptor() {
            return descriptor;
        }

        boolean sameIdentity(Candidate other) {
            return other != null && reviewEpoch == other.reviewEpoch
                && descriptor.sameIdentity(other.descriptor);
        }
    }

    static final class StagedPortable {
        final StagedKind kind;
        final String sha256;
        final int byteCount;
        private final byte[] bytes;

        private StagedPortable(StagedState staged) {
            kind = staged.kind;
            sha256 = staged.sha256;
            bytes = staged.bytes.clone();
            byteCount = bytes.length;
        }

        byte[] bytes() {
            return bytes.clone();
        }
    }

    static final class TransferReconciliation {
        final String digest;
        final long byteCount;
        final int kind;
        final String attemptId;
        final String manifestSha256;
        private final OctavoLibraryPortable.Descriptor descriptor;

        private TransferReconciliation(ReconciliationState value) {
            descriptor = value.descriptor;
            digest = descriptor.digest;
            byteCount = descriptor.byteCount;
            kind = descriptor.kind;
            attemptId = value.attemptId;
            manifestSha256 = value.manifestSha256;
        }

        OctavoLibraryPortable.Descriptor descriptor() {
            return descriptor;
        }

        boolean sameIdentity(TransferReconciliation other) {
            return other != null
                && descriptor.sameIdentity(other.descriptor)
                && attemptId.equals(other.attemptId)
                && manifestSha256.equals(other.manifestSha256);
        }
    }

    static final class LocalReconciliation {
        final LocalReconciliationKind kind;
        final String digest;
        final long byteCount;
        final int contentKind;
        private final OctavoLibraryPortable.Descriptor descriptor;

        private LocalReconciliation(LocalReconciliationState value) {
            kind = value.kind;
            descriptor = value.descriptor;
            digest = descriptor.digest;
            byteCount = descriptor.byteCount;
            contentKind = descriptor.kind;
        }

        OctavoLibraryPortable.Descriptor descriptor() {
            return descriptor;
        }

        boolean sameIdentity(LocalReconciliation other) {
            return other != null && kind == other.kind
                && descriptor.sameIdentity(other.descriptor);
        }
    }

    static final class PortableExport {
        final PortableExportStatus status;
        private final byte[] bytes;

        private PortableExport(PortableExportStatus status,
                               byte[] bytes) {
            this.status = status;
            this.bytes = bytes == null ? null : bytes.clone();
        }

        byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }

    private static final class RecordState {
        final OctavoLibraryPortable.Descriptor descriptor;
        final Decision decision;
        final long decisionEpoch;

        RecordState(OctavoLibraryPortable.Descriptor descriptor,
                    Decision decision,
                    long decisionEpoch) {
            this.descriptor = descriptor;
            this.decision = decision;
            this.decisionEpoch = decisionEpoch;
        }

        static RecordState undecided(
            OctavoLibraryPortable.Descriptor descriptor) {
            return new RecordState(descriptor, Decision.NONE, 0);
        }

        RecordState withDecision(Decision value, long epoch) {
            return new RecordState(descriptor, value, epoch);
        }
    }

    private static final class StagedState {
        final StagedKind kind;
        final String sha256;
        final byte[] bytes;

        StagedState(StagedKind kind, String sha256, byte[] bytes) {
            this.kind = kind;
            this.sha256 = sha256;
            this.bytes = bytes == null ? null : bytes.clone();
        }
    }

    private static final class ReconciliationState {
        final OctavoLibraryPortable.Descriptor descriptor;
        final String attemptId;
        final String manifestSha256;

        ReconciliationState(
            OctavoLibraryPortable.Descriptor descriptor,
            String attemptId,
            String manifestSha256) {
            this.descriptor = descriptor;
            this.attemptId = attemptId;
            this.manifestSha256 = manifestSha256;
        }

        boolean matches(OctavoLibraryPortable.Descriptor other,
                        String otherAttempt,
                        String otherManifest) {
            return descriptor.sameIdentity(other)
                && attemptId.equals(otherAttempt)
                && manifestSha256.equals(otherManifest);
        }
    }

    private static final class LocalReconciliationState {
        final LocalReconciliationKind kind;
        final OctavoLibraryPortable.Descriptor descriptor;

        LocalReconciliationState(
            LocalReconciliationKind kind,
            OctavoLibraryPortable.Descriptor descriptor) {
            this.kind = kind;
            this.descriptor = descriptor;
        }

        boolean matches(LocalReconciliation expected) {
            return expected != null && kind == expected.kind
                && descriptor.sameIdentity(expected.descriptor);
        }
    }

    private static final class State {
        final long reviewEpoch;
        final TreeMap<String, RecordState> records;
        final StagedState staged;
        final ReconciliationState reconciliation;
        final LocalReconciliationState localReconciliation;
        final int attention;

        State(long reviewEpoch,
              Map<String, RecordState> records,
              StagedState staged,
              ReconciliationState reconciliation,
              LocalReconciliationState localReconciliation,
              int attention) {
            this.reviewEpoch = reviewEpoch;
            this.records = new TreeMap<>(records);
            this.staged = staged;
            this.reconciliation = reconciliation;
            this.localReconciliation = localReconciliation;
            this.attention = attention;
        }

        State withEpoch(long value) {
            return new State(value, records, staged, reconciliation,
                             localReconciliation, attention);
        }

        State withRecords(Map<String, RecordState> value) {
            return new State(reviewEpoch, value, staged,
                             reconciliation, localReconciliation,
                             attention);
        }

        State withStaged(StagedState value, int nextAttention) {
            return new State(reviewEpoch, records, value,
                             reconciliation, localReconciliation,
                             nextAttention);
        }

        State withReconciliation(ReconciliationState value) {
            return new State(reviewEpoch, records, staged, value,
                             localReconciliation, attention);
        }

        State withLocalReconciliation(LocalReconciliationState value) {
            return new State(reviewEpoch, records, staged,
                             reconciliation, value, attention);
        }

        State withAttention(int value) {
            return new State(reviewEpoch, records, staged,
                             reconciliation, localReconciliation,
                             value);
        }
    }

    private static final int STORE_MAGIC = 0x4F314C53; // "O1LS"
    private static final int STORE_VERSION = 1;
    private static final int STORE_HEADER_FIELD_COUNT = 7;
    private static final int RECORD_STATE_BYTES = 88;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int QUARANTINE_SLOTS = 3;
    private static final int ATTENTION_NONE = 0;
    private static final int ATTENTION_CURRENT_APPROVAL = 1;
    private static final int ATTENTION_LIMIT_RETAINED = 2;
    private static final int ATTENTION_FUTURE_RETAINED = 3;
    private static final int ATTENTION_STAGED_CONFLICT = 4;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "library-sync.v1";
    private static final String TEMPORARY_FILE = "library-sync.v1.tmp";
    private static final String QUARANTINE_PREFIX =
        "library-sync.corrupt.";

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private State current = emptyState();
    private LoadStatus loadStatus = LoadStatus.MISSING_EMPTY;
    private boolean loadAttempted;
    private boolean mutationsBlocked;
    private boolean stateExpectedOnDisk;
    private boolean failNextPublishForTesting;
    private boolean failNextMoveAfterReplaceForTesting;
    private String lastError = "";

    OctavoLibrarySyncStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoLibrarySyncStore(File filesDirectory) {
        if (filesDirectory == null) {
            throw new IllegalArgumentException(
                "Invalid Library synchronization store");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
    }

    synchronized LoadStatus load() {
        loadAttempted = true;
        mutationsBlocked = false;
        if (!stateFile.exists()) {
            if (stateExpectedOnDisk) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                lastError =
                    "Expected Library sync state is missing; recovery is required.";
                return loadStatus;
            }
            current = emptyState();
            stateExpectedOnDisk = false;
            deleteTemporaryBestEffort();
            if (hasQuarantinedState()) {
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                lastError =
                    "A quarantined Library sync state still requires attention.";
            } else {
                loadStatus = LoadStatus.MISSING_EMPTY;
                lastError = "";
            }
            return loadStatus;
        }
        if (!stateFile.isFile()) {
            stateExpectedOnDisk = true;
            mutationsBlocked = true;
            loadStatus = LoadStatus.CORRUPT_BLOCKED;
            lastError =
                "Library sync state is not a regular file and was preserved.";
            return loadStatus;
        }
        if (stateFile.length() > MAX_FILE_BYTES) {
            stateExpectedOnDisk = true;
            mutationsBlocked = true;
            loadStatus = LoadStatus.OVERBOUND_BLOCKED;
            lastError =
                "Library sync state exceeds its byte limit and was preserved.";
            return loadStatus;
        }
        try {
            byte[] bytes = readBounded(stateFile);
            if (isFutureStore(bytes)) {
                stateExpectedOnDisk = true;
                mutationsBlocked = true;
                loadStatus = LoadStatus.FUTURE_VERSION_BLOCKED;
                lastError =
                    "Library sync state was written by a newer version.";
                return loadStatus;
            }
            current = decodeState(bytes);
            stateExpectedOnDisk = true;
            if (hasQuarantinedState()) {
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                lastError =
                    "A quarantined Library sync state still requires attention.";
            } else {
                loadStatus = LoadStatus.LOADED;
                lastError = visibleStateMessage(current);
            }
            deleteTemporaryBestEffort();
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState()) {
                current = emptyState();
                stateExpectedOnDisk = false;
                MutationResult recreated = publish(current);
                if (recreated == MutationResult.UPDATED) {
                    loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                    lastError =
                        "Corrupt Library sync state was quarantined.";
                } else if (recreated != MutationResult.PUBLISH_UNCERTAIN) {
                    mutationsBlocked = true;
                    loadStatus = LoadStatus.CORRUPT_BLOCKED;
                    lastError =
                        "Corrupt Library sync state was quarantined, but new state could not be saved.";
                }
            } else {
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "Library sync state is corrupt and could not be quarantined.";
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

    synchronized long reviewEpoch() {
        return current.reviewEpoch;
    }

    synchronized OctavoLibraryPortable.Snapshot snapshot() {
        return portableSnapshot(current);
    }

    synchronized Decision decision(String digest) {
        RecordState record = current.records.get(digest);
        return record == null ? null : record.decision;
    }

    synchronized StagedPortable stagedPortable() {
        return current.staged == null
            ? null : new StagedPortable(current.staged);
    }

    synchronized TransferReconciliation transferReconciliation() {
        return current.reconciliation == null
            ? null : new TransferReconciliation(
                current.reconciliation);
    }

    synchronized LocalReconciliation localReconciliation() {
        return current.localReconciliation == null
            ? null : new LocalReconciliation(
                current.localReconciliation);
    }

    synchronized MutationResult beginReviewEpoch(
        boolean explicitLibraryOpen) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (!explicitLibraryOpen) {
            return unchanged();
        }
        if (current.reviewEpoch == Long.MAX_VALUE) {
            return fail(MutationResult.EXHAUSTED,
                        "The Library review counter is exhausted.");
        }
        return publish(current.withEpoch(current.reviewEpoch + 1));
    }

    synchronized MutationResult stageLocalPublication(
        OctavoLibraryPortable.Descriptor descriptor) {
        return stageLocalReconciliation(
            LocalReconciliationKind.PUBLICATION, descriptor);
    }

    synchronized MutationResult stageLocalRemoval(
        OctavoLibraryPortable.Descriptor descriptor) {
        return stageLocalReconciliation(
            LocalReconciliationKind.REMOVAL, descriptor);
    }

    synchronized MutationResult finalizeLocalReconciliation(
        LocalReconciliation expected,
        boolean exactO6ConditionProved) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (expected == null || !exactO6ConditionProved) {
            return fail(MutationResult.INVALID,
                        "The exact local Library condition was not proved.");
        }
        LocalReconciliationState pending =
            current.localReconciliation;
        if (pending == null || !pending.matches(expected)) {
            return fail(MutationResult.CONFLICT,
                        "The local Library reconciliation is stale.");
        }
        RecordState existing = current.records.get(
            pending.descriptor.digest);
        if (existing != null
            && !existing.descriptor.sameIdentity(pending.descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The local Library identity is equivocal.");
        }
        TreeMap<String, RecordState> records =
            new TreeMap<>(current.records);
        if (pending.kind == LocalReconciliationKind.PUBLICATION) {
            if (existing == null
                && records.size()
                   >= OctavoLibraryPortable.maximumRecordCount()) {
                return fail(MutationResult.LIMIT,
                            "The synchronized Library is full.");
            }
            records.put(pending.descriptor.digest,
                        existing == null
                            ? new RecordState(
                                pending.descriptor,
                                Decision.DOWNLOADED, 0)
                            : existing.withDecision(
                                Decision.DOWNLOADED, 0));
        } else if (pending.kind
                   == LocalReconciliationKind.REMOVAL) {
            if (existing == null) {
                return fail(MutationResult.CONFLICT,
                            "The removed Library identity is missing.");
            }
            records.put(existing.descriptor.digest,
                        existing.withDecision(
                            Decision.LOCAL_REMOVED, 0));
        } else {
            return fail(MutationResult.INVALID,
                        "The local Library reconciliation is invalid.");
        }
        return publish(current.withRecords(records)
            .withLocalReconciliation(null));
    }

    synchronized MutationResult clearLocalReconciliation(
        LocalReconciliation expected,
        boolean exactO6Unchanged) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (expected == null || !exactO6Unchanged) {
            return fail(MutationResult.INVALID,
                        "Unchanged local Library state was not proved.");
        }
        LocalReconciliationState pending =
            current.localReconciliation;
        if (pending == null || !pending.matches(expected)) {
            return fail(MutationResult.CONFLICT,
                        "The local Library reconciliation is stale.");
        }
        return publish(current.withLocalReconciliation(null));
    }

    /** Caller attests exact hash, Reader0 validation, and durable O6 presence. */
    synchronized MutationResult recordLocalValidated(
        OctavoLibraryPortable.Descriptor descriptor) {
        return recordLocalValidated(descriptor, null);
    }

    synchronized MutationResult recordLocalValidated(
        OctavoLibraryPortable.Descriptor descriptor,
        Candidate candidate) {
        MutationResult ready = requireMutableDescriptor(descriptor);
        if (ready != null) {
            return ready;
        }
        RecordState existing = current.records.get(descriptor.digest);
        if (existing != null
            && !existing.descriptor.sameIdentity(descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The Library identity is equivocal.");
        }
        if (current.localReconciliation != null
            && current.localReconciliation.descriptor.digest.equals(
                descriptor.digest)) {
            if (current.localReconciliation.kind
                    == LocalReconciliationKind.PUBLICATION
                && current.localReconciliation.descriptor.sameIdentity(
                    descriptor)) {
                return finalizeLocalReconciliation(
                    new LocalReconciliation(
                        current.localReconciliation), true);
            }
            return fail(MutationResult.CONFLICT,
                        "A different local Library reconciliation is pending.");
        }
        if (current.reconciliation != null
            && current.reconciliation.descriptor.digest.equals(
                descriptor.digest)) {
            return fail(MutationResult.CONFLICT,
                        "Finish the reconciled Library transfer first.");
        }
        if (candidate != null
            && !candidateMatches(candidate, existing, descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The Library offer is stale.");
        }
        if (existing == null
            && current.records.size()
               >= OctavoLibraryPortable.maximumRecordCount()) {
            return fail(MutationResult.LIMIT,
                        "The synchronized Library is full.");
        }
        if (existing != null
            && existing.decision == Decision.DOWNLOADED) {
            return unchanged();
        }
        TreeMap<String, RecordState> records =
            new TreeMap<>(current.records);
        RecordState downloaded = existing == null
            ? new RecordState(descriptor, Decision.DOWNLOADED, 0)
            : existing.withDecision(Decision.DOWNLOADED, 0);
        records.put(descriptor.digest, downloaded);
        return publish(current.withRecords(records));
    }

    synchronized MutationResult recordLocalRemoval(String digest) {
        MutationResult ready = requireMutableDigest(digest);
        if (ready != null) {
            return ready;
        }
        RecordState existing = current.records.get(digest);
        if (current.localReconciliation != null
            && current.localReconciliation.descriptor.digest.equals(
                digest)) {
            if (current.localReconciliation.kind
                == LocalReconciliationKind.REMOVAL) {
                return finalizeLocalReconciliation(
                    new LocalReconciliation(
                        current.localReconciliation), true);
            }
            return fail(MutationResult.CONFLICT,
                        "A different local Library reconciliation is pending.");
        }
        if (existing == null
            || existing.decision == Decision.LOCAL_REMOVED) {
            return unchanged();
        }
        TreeMap<String, RecordState> records =
            new TreeMap<>(current.records);
        records.put(digest,
                    existing.withDecision(Decision.LOCAL_REMOVED, 0));
        return publish(current.withRecords(records));
    }

    synchronized MutationResult resetForExplicitDownload(String digest) {
        MutationResult ready = requireMutableDigest(digest);
        if (ready != null) {
            return ready;
        }
        RecordState existing = current.records.get(digest);
        if (existing == null) {
            return fail(MutationResult.INVALID,
                        "The Library identity is not synchronized.");
        }
        if (current.reconciliation != null
            && current.reconciliation.descriptor.digest.equals(digest)) {
            return fail(MutationResult.CONFLICT,
                        "A Library transfer is already reconciled.");
        }
        if (current.localReconciliation != null
            && current.localReconciliation.descriptor.digest.equals(
                digest)) {
            return fail(MutationResult.CONFLICT,
                        "A local Library reconciliation is pending.");
        }
        if (existing.decision == Decision.NONE) {
            return unchanged();
        }
        if (existing.decision != Decision.IGNORED
            && existing.decision != Decision.LOCAL_REMOVED
            && existing.decision != Decision.DOWNLOADED) {
            return fail(MutationResult.CONFLICT,
                        "The Library decision cannot be reset for download.");
        }
        TreeMap<String, RecordState> records =
            new TreeMap<>(current.records);
        records.put(digest, existing.withDecision(Decision.NONE, 0));
        return publish(current.withRecords(records));
    }

    synchronized List<Candidate> reviewCandidates(
        Collection<String> locallyPresentDigests) {
        if (!loadAttempted || mutationsBlocked
            || current.reviewEpoch <= 0) {
            return Collections.emptyList();
        }
        TreeSet<String> present = copyLocalDigests(
            locallyPresentDigests);
        if (present == null) {
            return Collections.emptyList();
        }
        ArrayList<Candidate> result = new ArrayList<>();
        for (RecordState record : current.records.values()) {
            if (!present.contains(record.descriptor.digest)
                && (current.reconciliation == null
                    || !current.reconciliation.descriptor.digest.equals(
                        record.descriptor.digest))
                && (current.localReconciliation == null
                    || !current.localReconciliation.descriptor.digest
                        .equals(record.descriptor.digest))
                && reviewable(record, current.reviewEpoch)) {
                result.add(new Candidate(record, current.reviewEpoch));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized MutationResult keep(Candidate candidate) {
        return decide(candidate, Decision.IGNORED);
    }

    synchronized MutationResult ignore(Candidate candidate) {
        return keep(candidate);
    }

    synchronized MutationResult dismiss(Candidate candidate) {
        return decide(candidate, Decision.DISMISSED_AT_EPOCH);
    }

    synchronized MutationResult later(Candidate candidate) {
        return dismiss(candidate);
    }

    synchronized PortableStageResult stagePortableBytes(byte[] bytes) {
        if (!loadAttempted || mutationsBlocked) {
            lastError = "Library sync state is blocked.";
            return PortableStageResult.BLOCKED;
        }
        if (bytes == null) {
            lastError = "Portable Library bytes are invalid.";
            return PortableStageResult.INVALID;
        }
        if (bytes.length
            > OctavoLibraryPortable.maximumFutureBytes()) {
            lastError = "Portable Library bytes exceed their limit.";
            return PortableStageResult.LIMIT;
        }
        byte[] exact = bytes.clone();
        OctavoLibraryPortable.DecodeResult decoded =
            OctavoLibraryPortable.decode(exact);
        StagedKind kind;
        if (decoded.status
            == OctavoLibraryPortable.DecodeStatus.FUTURE_VERSION) {
            kind = StagedKind.FUTURE;
        } else if (decoded.status
                   == OctavoLibraryPortable.DecodeStatus.LIMIT) {
            lastError = "Portable Library bytes exceed their version-1 limit.";
            return PortableStageResult.LIMIT;
        } else if (decoded.status
                   != OctavoLibraryPortable.DecodeStatus.READY) {
            lastError = "Portable Library bytes are invalid.";
            return PortableStageResult.INVALID;
        } else {
            OctavoLibraryPortable.MergeResult inspected =
                OctavoLibraryPortable.merge(
                    portableSnapshot(current), decoded.snapshot());
            if (inspected.status
                == OctavoLibraryPortable.MergeStatus.EQUIVOCATION) {
                lastError = "A portable Library identity is equivocal.";
                return PortableStageResult.EQUIVOCATION;
            }
            if (inspected.status
                == OctavoLibraryPortable.MergeStatus.INVALID) {
                lastError = "Portable Library merge input is invalid.";
                return PortableStageResult.INVALID;
            }
            kind = inspected.status
                    == OctavoLibraryPortable.MergeStatus.LIMIT
                ? StagedKind.LIMIT : StagedKind.CURRENT;
        }
        String sha256;
        try {
            sha256 = sha256Hex(exact);
        } catch (IOException exception) {
            lastError = "Portable Library bytes could not be hashed.";
            return PortableStageResult.INVALID;
        }
        if (current.staged != null) {
            if (Arrays.equals(current.staged.bytes, exact)) {
                lastError = visibleStateMessage(current);
                return PortableStageResult.UNCHANGED;
            }
            if (current.attention == ATTENTION_STAGED_CONFLICT) {
                lastError = attentionMessage(ATTENTION_STAGED_CONFLICT);
                return PortableStageResult.STAGED_CONFLICT;
            }
            return stagePublicationResult(
                publish(current.withAttention(
                    ATTENTION_STAGED_CONFLICT)),
                PortableStageResult.STAGED_CONFLICT);
        }
        int attention = attentionFor(kind);
        return stagePublicationResult(
            publish(current.withStaged(
                new StagedState(kind, sha256, exact), attention)),
            stageSuccess(kind));
    }

    synchronized PortableMergeResult approveStagedPortable(
        String exactSha256Echo) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return PortableMergeResult.BLOCKED;
        }
        if (!OctavoLibraryPortable.validDigest(exactSha256Echo)) {
            lastError = "The staged Library approval digest is invalid.";
            return PortableMergeResult.INVALID;
        }
        if (current.staged == null
            || current.staged.kind != StagedKind.CURRENT) {
            lastError = "There is no current Library snapshot to approve.";
            return PortableMergeResult.NO_STAGED_CURRENT;
        }
        if (!current.staged.sha256.equals(exactSha256Echo)) {
            lastError = "The staged Library approval digest changed.";
            return PortableMergeResult.STAGED_DIGEST_MISMATCH;
        }
        OctavoLibraryPortable.DecodeResult decoded =
            OctavoLibraryPortable.decode(current.staged.bytes);
        if (decoded.status != OctavoLibraryPortable.DecodeStatus.READY) {
            lastError = "The staged Library snapshot is invalid.";
            return PortableMergeResult.INVALID;
        }
        OctavoLibraryPortable.MergeResult joined =
            OctavoLibraryPortable.merge(
                portableSnapshot(current), decoded.snapshot());
        if (joined.status
            == OctavoLibraryPortable.MergeStatus.EQUIVOCATION) {
            lastError = "The staged Library snapshot is equivocal.";
            return PortableMergeResult.EQUIVOCATION;
        }
        if (joined.status == OctavoLibraryPortable.MergeStatus.LIMIT) {
            MutationResult retained = publish(current.withStaged(
                new StagedState(
                    StagedKind.LIMIT, current.staged.sha256,
                    current.staged.bytes),
                ATTENTION_LIMIT_RETAINED));
            if (retained == MutationResult.UPDATED) {
                return PortableMergeResult.LIMIT_RETAINED;
            }
            return mergePublicationResult(retained);
        }
        if (joined.status == OctavoLibraryPortable.MergeStatus.INVALID) {
            lastError = "The staged Library merge is invalid.";
            return PortableMergeResult.INVALID;
        }
        TreeMap<String, RecordState> records = new TreeMap<>();
        for (OctavoLibraryPortable.Descriptor descriptor
                 : joined.snapshot.descriptors()) {
            RecordState old = current.records.get(descriptor.digest);
            records.put(descriptor.digest,
                        old == null
                            ? RecordState.undecided(descriptor) : old);
        }
        State approved = current.withRecords(records)
            .withStaged(null, ATTENTION_NONE);
        MutationResult published = publish(approved);
        if (published == MutationResult.UPDATED) {
            return joined.status
                    == OctavoLibraryPortable.MergeStatus.UNCHANGED
                ? PortableMergeResult.UNCHANGED
                : PortableMergeResult.MERGED;
        }
        return mergePublicationResult(published);
    }

    synchronized MutationResult reconcileTransferAttempt(
        Candidate candidate,
        String attemptId,
        String manifestSha256) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (candidate == null || !validAttemptId(attemptId)
            || !OctavoLibraryPortable.validDigest(manifestSha256)) {
            return fail(MutationResult.INVALID,
                        "The Library transfer reconciliation is invalid.");
        }
        ReconciliationState existing = current.reconciliation;
        if (existing != null) {
            if (existing.matches(
                    candidate.descriptor, attemptId, manifestSha256)) {
                return unchanged();
            }
            return fail(MutationResult.CONFLICT,
                        "Another Library transfer is being reconciled.");
        }
        RecordState record = current.records.get(candidate.digest);
        if (!candidateMatches(
                candidate, record, candidate.descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The Library transfer offer is stale.");
        }
        return publish(current.withReconciliation(
            new ReconciliationState(
                record.descriptor, attemptId, manifestSha256)));
    }

    synchronized MutationResult completeDownloaded(
        TransferReconciliation expected,
        boolean exactLocallyPresent) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (expected == null || !exactLocallyPresent) {
            return fail(MutationResult.INVALID,
                        "Exact local Library presence was not proved.");
        }
        ReconciliationState reconciliation = current.reconciliation;
        if (reconciliation == null
            || !reconciliation.matches(
                expected.descriptor, expected.attemptId,
                expected.manifestSha256)) {
            return fail(MutationResult.CONFLICT,
                        "The Library transfer reconciliation is stale.");
        }
        RecordState record = current.records.get(
            reconciliation.descriptor.digest);
        if (record == null
            || !record.descriptor.sameIdentity(
                reconciliation.descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The reconciled Library identity changed.");
        }
        TreeMap<String, RecordState> records =
            new TreeMap<>(current.records);
        records.put(record.descriptor.digest,
                    record.withDecision(Decision.DOWNLOADED, 0));
        return publish(current.withRecords(records)
            .withReconciliation(null));
    }

    synchronized MutationResult completeDownloaded(
        Candidate candidate,
        String attemptId,
        String manifestSha256,
        boolean exactLocallyPresent) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (candidate == null || !validAttemptId(attemptId)
            || !OctavoLibraryPortable.validDigest(manifestSha256)) {
            return fail(MutationResult.INVALID,
                        "The Library transfer completion is invalid.");
        }
        ReconciliationState reconciliation = current.reconciliation;
        if (reconciliation == null
            || !reconciliation.matches(
                candidate.descriptor, attemptId, manifestSha256)) {
            return fail(MutationResult.CONFLICT,
                        "The Library transfer reconciliation is stale.");
        }
        return completeDownloaded(
            new TransferReconciliation(reconciliation),
            exactLocallyPresent);
    }

    synchronized MutationResult releaseTransferReconciliation(
        TransferReconciliation expected,
        boolean exactQueueAbsent) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (expected == null || !exactQueueAbsent) {
            return fail(MutationResult.INVALID,
                        "Exact Library queue absence was not proved.");
        }
        ReconciliationState reconciliation = current.reconciliation;
        if (reconciliation == null
            || !reconciliation.matches(
                expected.descriptor, expected.attemptId,
                expected.manifestSha256)) {
            return fail(MutationResult.CONFLICT,
                        "The Library transfer reconciliation is stale.");
        }
        return publish(current.withReconciliation(null));
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
                OctavoLibraryPortable.encode(
                    portableSnapshot(current)));
        } catch (IOException | RuntimeException exception) {
            lastError = "Portable Library bytes could not be created.";
            return new PortableExport(
                PortableExportStatus.LOCAL_FAILURE, null);
        }
    }

    synchronized byte[] retainedFutureBytes() {
        return current.staged == null
                || current.staged.kind != StagedKind.FUTURE
            ? null : current.staged.bytes.clone();
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

    private MutationResult decide(Candidate candidate,
                                  Decision decision) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (candidate == null
            || (decision != Decision.IGNORED
                && decision != Decision.DISMISSED_AT_EPOCH)) {
            return fail(MutationResult.INVALID,
                        "Invalid Library decision.");
        }
        RecordState record = current.records.get(candidate.digest);
        if (!candidateMatches(
                candidate, record, candidate.descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The Library offer is stale.");
        }
        long epoch = decision == Decision.DISMISSED_AT_EPOCH
            ? current.reviewEpoch : 0;
        TreeMap<String, RecordState> records =
            new TreeMap<>(current.records);
        records.put(candidate.digest,
                    record.withDecision(decision, epoch));
        return publish(current.withRecords(records));
    }

    private MutationResult stageLocalReconciliation(
        LocalReconciliationKind kind,
        OctavoLibraryPortable.Descriptor descriptor) {
        MutationResult ready = requireMutableDescriptor(descriptor);
        if (ready != null) {
            return ready;
        }
        if (kind == null) {
            return fail(MutationResult.INVALID,
                        "The local Library reconciliation is invalid.");
        }
        LocalReconciliationState existingPending =
            current.localReconciliation;
        RecordState existing = current.records.get(descriptor.digest);
        if (existingPending != null) {
            if (existingPending.kind == kind
                && existingPending.descriptor.sameIdentity(descriptor)) {
                return unchanged();
            }
            if (kind == LocalReconciliationKind.REMOVAL
                && existingPending.kind
                   == LocalReconciliationKind.PUBLICATION
                && existingPending.descriptor.sameIdentity(descriptor)) {
                // The explicit local removal supersedes a not-yet-finalized
                // local discovery. If O1LC never contained the digest there
                // is nothing to suppress; otherwise retain an exact removal
                // marker for the independently durable cleanup saga.
                return publish(existing == null
                    ? current.withLocalReconciliation(null)
                    : current.withLocalReconciliation(
                        new LocalReconciliationState(
                            LocalReconciliationKind.REMOVAL,
                            descriptor)));
            }
            return fail(MutationResult.CONFLICT,
                        "Another local Library reconciliation is pending.");
        }
        if (existing != null
            && !existing.descriptor.sameIdentity(descriptor)) {
            return fail(MutationResult.CONFLICT,
                        "The local Library identity is equivocal.");
        }
        if (kind == LocalReconciliationKind.REMOVAL
            && existing == null) {
            return fail(MutationResult.INVALID,
                        "The removed Library identity is not synchronized.");
        }
        if (current.reconciliation != null
            && current.reconciliation.descriptor.digest.equals(
                descriptor.digest)) {
            return fail(MutationResult.CONFLICT,
                        "A transfer for this Library identity is pending.");
        }
        return publish(current.withLocalReconciliation(
            new LocalReconciliationState(kind, descriptor)));
    }

    private boolean candidateMatches(
        Candidate candidate,
        RecordState record,
        OctavoLibraryPortable.Descriptor descriptor) {
        return candidate != null && record != null
            && descriptor != null
            && candidate.reviewEpoch == current.reviewEpoch
            && candidate.descriptor.sameIdentity(descriptor)
            && candidate.descriptor.sameIdentity(record.descriptor)
            && (current.reconciliation == null
                || !current.reconciliation.descriptor.digest.equals(
                    candidate.digest))
            && (current.localReconciliation == null
                || !current.localReconciliation.descriptor.digest.equals(
                    candidate.digest))
            && reviewable(record, current.reviewEpoch);
    }

    private MutationResult requireMutable() {
        if (!loadAttempted) {
            return fail(MutationResult.BLOCKED,
                        "Library sync state has not been loaded.");
        }
        if (mutationsBlocked) {
            // Keep the typed load/reconciliation cause (future version,
            // overbound bytes, corrupt evidence, or uncertain publication)
            // visible. A rejected mutator must not replace it with a generic
            // message that makes the explicit Retry surface misleading.
            return MutationResult.BLOCKED;
        }
        return null;
    }

    private MutationResult requireMutableDescriptor(
        OctavoLibraryPortable.Descriptor descriptor) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (!validDescriptor(descriptor)) {
            return fail(MutationResult.INVALID,
                        "The Library descriptor is invalid.");
        }
        return null;
    }

    private MutationResult requireMutableDigest(String digest) {
        MutationResult ready = requireMutable();
        if (ready != null) {
            return ready;
        }
        if (!OctavoLibraryPortable.validDigest(digest)) {
            return fail(MutationResult.INVALID,
                        "The Library digest is invalid.");
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
                            "Library sync state exceeds its byte limit.");
            }
            if (previousExists != stateExpectedOnDisk) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Library sync destination presence changed; reload is required.");
            }
            if (previousExists) {
                previous = readBounded(stateFile);
                byte[] expected = encodeState(current);
                if (!Arrays.equals(previous, expected)) {
                    mutationsBlocked = true;
                    loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                    return fail(
                        MutationResult.PUBLISH_UNCERTAIN,
                        "Library sync state changed unexpectedly; reload is required.");
                }
                destinationVerified = true;
            } else if (stateFile.exists()) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Library sync destination is not a regular file.");
            } else {
                destinationVerified = true;
            }
            requireDirectory(rootDirectory);
            if (failNextPublishForTesting) {
                failNextPublishForTesting = false;
                throw new IOException("Injected O1LS publish failure");
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
                    "Injected uncertain O1LS replace result");
            }
            current = candidate;
            stateExpectedOnDisk = true;
            lastError = visibleStateMessage(candidate);
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            if (candidateEncoded && !destinationVerified) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Library sync destination could not be verified; reload is required.");
            }
            if (moveAttempted
                && !destinationStillEquals(previousExists, previous)) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                return fail(
                    MutationResult.PUBLISH_UNCERTAIN,
                    "Library sync publication is uncertain; reload is required.");
            }
            return fail(MutationResult.PUBLISH_FAILED,
                        "Library sync state could not be saved. Retry.");
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
        lastError = visibleStateMessage(current);
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
            case LIMIT: return PortableMergeResult.LIMIT_RETAINED;
            case BLOCKED: return PortableMergeResult.BLOCKED;
            default: return PortableMergeResult.INVALID;
        }
    }

    private PortableStageResult stagePublicationResult(
        MutationResult result,
        PortableStageResult success) {
        switch (result) {
            case UPDATED: return success;
            case PUBLISH_UNCERTAIN:
                return PortableStageResult.PUBLISH_UNCERTAIN;
            case PUBLISH_FAILED:
                return PortableStageResult.PUBLISH_FAILED;
            case LIMIT: return PortableStageResult.LIMIT;
            case BLOCKED: return PortableStageResult.BLOCKED;
            default: return PortableStageResult.INVALID;
        }
    }

    private static PortableStageResult stageSuccess(StagedKind kind) {
        switch (kind) {
            case CURRENT: return PortableStageResult.STAGED_CURRENT;
            case LIMIT: return PortableStageResult.LIMIT_RETAINED;
            case FUTURE: return PortableStageResult.FUTURE_RETAINED;
            default:
                throw new IllegalArgumentException(
                    "Invalid staged Library kind");
        }
    }

    private static int attentionFor(StagedKind kind) {
        switch (kind) {
            case CURRENT: return ATTENTION_CURRENT_APPROVAL;
            case LIMIT: return ATTENTION_LIMIT_RETAINED;
            case FUTURE: return ATTENTION_FUTURE_RETAINED;
            default:
                throw new IllegalArgumentException(
                    "Invalid staged Library kind");
        }
    }

    private static OctavoLibraryPortable.Snapshot portableSnapshot(
        State state) {
        ArrayList<OctavoLibraryPortable.Descriptor> descriptors =
            new ArrayList<>(state.records.size());
        for (RecordState record : state.records.values()) {
            descriptors.add(record.descriptor);
        }
        return new OctavoLibraryPortable.Snapshot(descriptors);
    }

    private static State emptyState() {
        return new State(0, Collections.emptyMap(), null, null, null,
                         ATTENTION_NONE);
    }

    private static byte[] encodeState(State state) throws IOException {
        if (!validState(state)) {
            throw new IOException("Invalid O1LS state");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(STORE_MAGIC);
            output.writeInt(STORE_VERSION);
            output.writeInt(STORE_HEADER_FIELD_COUNT);
            output.writeLong(state.reviewEpoch);
            output.writeInt(state.attention);
            output.writeInt(state.records.size());
            String previous = null;
            for (RecordState record : state.records.values()) {
                if (previous != null
                    && previous.compareTo(record.descriptor.digest) >= 0) {
                    throw new IOException(
                        "Noncanonical O1LS record order");
                }
                writeRecordState(output, record);
                previous = record.descriptor.digest;
            }
            if (state.staged == null) {
                output.writeInt(0);
                output.writeInt(0);
            } else {
                output.writeInt(state.staged.kind.wireId);
                output.writeInt(state.staged.bytes.length);
                writeDigest(output, state.staged.sha256);
                output.write(state.staged.bytes);
            }
            output.writeInt(state.reconciliation == null ? 0 : 1);
            if (state.reconciliation != null) {
                writeDescriptor(
                    output, state.reconciliation.descriptor);
                writeAttemptId(
                    output, state.reconciliation.attemptId);
                writeDigest(
                    output, state.reconciliation.manifestSha256);
            }
            output.writeInt(
                state.localReconciliation == null ? 0 : 1);
            if (state.localReconciliation != null) {
                output.writeInt(
                    state.localReconciliation.kind.wireId);
                writeDescriptor(
                    output, state.localReconciliation.descriptor);
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
        if (result.length < minimumStateBytes()
            || result.length > MAX_FILE_BYTES) {
            throw new IOException("O1LS state exceeds its bound");
        }
        return result;
    }

    private static State decodeState(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < minimumStateBytes()
            || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid O1LS byte length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, payloadLength);
        if (readInt(bytes, payloadLength)
            != (int)checksum.getValue()) {
            throw new IOException("Invalid O1LS checksum");
        }
        try {
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != STORE_MAGIC
                || input.readInt() != STORE_VERSION
                || input.readInt() != STORE_HEADER_FIELD_COUNT) {
                throw new IOException("Invalid O1LS header");
            }
            long reviewEpoch = input.readLong();
            int attention = input.readInt();
            int recordCount = input.readInt();
            if (reviewEpoch < 0 || !validAttention(attention)
                || recordCount < 0
                || recordCount
                   > OctavoLibraryPortable.maximumRecordCount()) {
                throw new IOException("Invalid O1LS bounds");
            }
            TreeMap<String, RecordState> records = new TreeMap<>();
            String previous = null;
            for (int index = 0; index < recordCount; ++index) {
                RecordState record = readRecordState(input);
                if (previous != null
                    && previous.compareTo(record.descriptor.digest) >= 0) {
                    throw new IOException(
                        "Noncanonical O1LS records");
                }
                records.put(record.descriptor.digest, record);
                previous = record.descriptor.digest;
            }
            int stagedKindId = input.readInt();
            int stagedLength = input.readInt();
            if (stagedLength < 0
                || stagedLength
                   > OctavoLibraryPortable.maximumFutureBytes()) {
                throw new IOException("Invalid O1LS staged length");
            }
            StagedState staged = null;
            if (stagedKindId == 0) {
                if (stagedLength != 0) {
                    throw new IOException(
                        "Invalid O1LS empty staged input");
                }
            } else {
                StagedKind kind = StagedKind.fromWireId(stagedKindId);
                if (kind == null || stagedLength < 2 * Integer.BYTES) {
                    throw new IOException(
                        "Invalid O1LS staged input");
                }
                String sha256 = readDigest(input);
                byte[] stagedBytes = new byte[stagedLength];
                input.readFully(stagedBytes);
                staged = new StagedState(kind, sha256, stagedBytes);
            }
            int reconciliationPresent = input.readInt();
            if (reconciliationPresent < 0
                || reconciliationPresent > 1) {
                throw new IOException(
                    "Invalid O1LS reconciliation flag");
            }
            ReconciliationState reconciliation = null;
            if (reconciliationPresent == 1) {
                reconciliation = new ReconciliationState(
                    readDescriptor(input), readAttemptId(input),
                    readDigest(input));
            }
            int localReconciliationPresent = input.readInt();
            if (localReconciliationPresent < 0
                || localReconciliationPresent > 1) {
                throw new IOException(
                    "Invalid O1LS local reconciliation flag");
            }
            LocalReconciliationState localReconciliation = null;
            if (localReconciliationPresent == 1) {
                LocalReconciliationKind kind =
                    LocalReconciliationKind.fromWireId(
                        input.readInt());
                if (kind == null) {
                    throw new IOException(
                        "Invalid O1LS local reconciliation kind");
                }
                localReconciliation = new LocalReconciliationState(
                    kind, readDescriptor(input));
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1LS payload");
            }
            State state = new State(
                reviewEpoch, records, staged, reconciliation,
                localReconciliation, attention);
            if (!validState(state)) {
                throw new IOException("Invalid O1LS state");
            }
            return state;
        } catch (EOFException exception) {
            throw new IOException("Truncated O1LS state", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid O1LS scalar", exception);
        }
    }

    private static boolean validState(State state) {
        if (state == null || state.reviewEpoch < 0
            || state.records.size()
               > OctavoLibraryPortable.maximumRecordCount()
            || !validAttention(state.attention)
            || !validStaged(state.staged, state.attention)) {
            return false;
        }
        if ((state.staged == null)
            != (state.attention == ATTENTION_NONE)) {
            return false;
        }
        for (Map.Entry<String, RecordState> entry
                 : state.records.entrySet()) {
            RecordState record = entry.getValue();
            if (record == null
                || !entry.getKey().equals(record.descriptor.digest)
                || !validDescriptor(record.descriptor)
                || record.decision == null) {
                return false;
            }
            if (record.decision == Decision.DISMISSED_AT_EPOCH) {
                if (record.decisionEpoch <= 0
                    || record.decisionEpoch > state.reviewEpoch) {
                    return false;
                }
            } else if (record.decisionEpoch != 0) {
                return false;
            }
            if (record.decision == Decision.IGNORED
                && state.reviewEpoch <= 0) {
                return false;
            }
        }
        if (state.staged != null
            && state.staged.kind != StagedKind.FUTURE) {
            OctavoLibraryPortable.DecodeResult decoded =
                OctavoLibraryPortable.decode(state.staged.bytes);
            if (decoded.status != OctavoLibraryPortable.DecodeStatus.READY) {
                return false;
            }
            OctavoLibraryPortable.MergeStatus mergeStatus =
                OctavoLibraryPortable.merge(
                    portableSnapshot(state), decoded.snapshot()).status;
            if (state.staged.kind == StagedKind.CURRENT) {
                if (mergeStatus != OctavoLibraryPortable.MergeStatus.MERGED
                    && mergeStatus
                       != OctavoLibraryPortable.MergeStatus.UNCHANGED) {
                    return false;
                }
            } else if (state.staged.kind == StagedKind.LIMIT) {
                if (mergeStatus != OctavoLibraryPortable.MergeStatus.LIMIT) {
                    return false;
                }
            } else {
                return false;
            }
        }
        if (state.reconciliation != null) {
            ReconciliationState reconciliation = state.reconciliation;
            RecordState reconciled = state.records.get(
                reconciliation.descriptor.digest);
            if (!validDescriptor(reconciliation.descriptor)
                || !validAttemptId(reconciliation.attemptId)
                || !OctavoLibraryPortable.validDigest(
                    reconciliation.manifestSha256)
                || reconciled == null
                || !reconciled.descriptor.sameIdentity(
                    reconciliation.descriptor)
                || state.reviewEpoch <= 0
                || !reviewable(reconciled, state.reviewEpoch)) {
                return false;
            }
        }
        if (state.localReconciliation == null) {
            return true;
        }
        LocalReconciliationState local = state.localReconciliation;
        if (local.kind == null || !validDescriptor(local.descriptor)
            || (state.reconciliation != null
                && state.reconciliation.descriptor.digest.equals(
                    local.descriptor.digest))) {
            return false;
        }
        RecordState localRecord = state.records.get(
            local.descriptor.digest);
        if (localRecord != null
            && !localRecord.descriptor.sameIdentity(local.descriptor)) {
            return false;
        }
        if (local.kind == LocalReconciliationKind.REMOVAL) {
            return localRecord != null;
        }
        return local.kind == LocalReconciliationKind.PUBLICATION;
    }

    private static boolean validStaged(StagedState staged,
                                       int attention) {
        if (staged == null) {
            return attention == ATTENTION_NONE;
        }
        if (staged.kind == null || staged.bytes == null
            || staged.bytes.length < 2 * Integer.BYTES
            || staged.bytes.length
               > OctavoLibraryPortable.maximumFutureBytes()
            || !OctavoLibraryPortable.validDigest(staged.sha256)) {
            return false;
        }
        try {
            if (!staged.sha256.equals(sha256Hex(staged.bytes))) {
                return false;
            }
        } catch (IOException exception) {
            return false;
        }
        OctavoLibraryPortable.DecodeResult decoded =
            OctavoLibraryPortable.decode(staged.bytes);
        boolean kindMatches;
        switch (staged.kind) {
            case CURRENT:
                kindMatches = decoded.status
                    == OctavoLibraryPortable.DecodeStatus.READY;
                break;
            case LIMIT:
                kindMatches = decoded.status
                    == OctavoLibraryPortable.DecodeStatus.READY;
                break;
            case FUTURE:
                kindMatches = decoded.status
                    == OctavoLibraryPortable.DecodeStatus.FUTURE_VERSION;
                break;
            default:
                kindMatches = false;
        }
        return kindMatches
            && (attention == attentionFor(staged.kind)
                || attention == ATTENTION_STAGED_CONFLICT);
    }

    private static boolean validDescriptor(
        OctavoLibraryPortable.Descriptor descriptor) {
        return descriptor != null
            && OctavoLibraryPortable.validDigest(descriptor.digest)
            && descriptor.byteCount > 0
            && descriptor.byteCount
               <= OctavoLibraryPortable.maximumDocumentBytes()
            && descriptor.kind
               == OctavoLibraryPortable.Descriptor.EPUB;
    }

    private static boolean validAttemptId(String value) {
        if (value == null || value.length() != 32) {
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

    private static TreeSet<String> copyLocalDigests(
        Collection<String> digests) {
        if (digests == null
            || digests.size()
               > OctavoLibraryPortable.maximumRecordCount()) {
            return null;
        }
        TreeSet<String> copied = new TreeSet<>();
        int inspected = 0;
        for (String digest : digests) {
            ++inspected;
            if (inspected > OctavoLibraryPortable.maximumRecordCount()
                || !OctavoLibraryPortable.validDigest(digest)) {
                return null;
            }
            copied.add(digest);
        }
        return copied;
    }

    private static boolean reviewable(RecordState record,
                                      long reviewEpoch) {
        return record != null && (record.decision == Decision.NONE
            || (record.decision == Decision.DISMISSED_AT_EPOCH
                && record.decisionEpoch < reviewEpoch));
    }

    private static void writeRecordState(DataOutputStream output,
                                         RecordState record)
        throws IOException {
        writeDescriptor(output, record.descriptor);
        output.writeInt(record.decision.wireId);
        output.writeLong(record.decisionEpoch);
    }

    private static RecordState readRecordState(DataInputStream input)
        throws IOException {
        OctavoLibraryPortable.Descriptor descriptor =
            readDescriptor(input);
        Decision decision = Decision.fromWireId(input.readInt());
        long decisionEpoch = input.readLong();
        if (decision == null) {
            throw new IOException("Invalid O1LS decision");
        }
        return new RecordState(
            descriptor, decision, decisionEpoch);
    }

    private static void writeDescriptor(
        DataOutputStream output,
        OctavoLibraryPortable.Descriptor descriptor)
        throws IOException {
        if (!validDescriptor(descriptor)) {
            throw new IOException("Invalid O1LS descriptor");
        }
        writeDigest(output, descriptor.digest);
        output.writeLong(descriptor.byteCount);
        output.writeInt(descriptor.kind);
    }

    private static OctavoLibraryPortable.Descriptor readDescriptor(
        DataInputStream input) throws IOException {
        return new OctavoLibraryPortable.Descriptor(
            readDigest(input), input.readLong(), input.readInt());
    }

    private static void writeDigest(DataOutputStream output,
                                    String digest) throws IOException {
        byte[] bytes = digest.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != 64
            || !OctavoLibraryPortable.validDigest(digest)) {
            throw new IOException("Invalid O1LS digest");
        }
        output.write(bytes);
    }

    private static String readDigest(DataInputStream input)
        throws IOException {
        byte[] bytes = new byte[64];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (!OctavoLibraryPortable.validDigest(result)) {
            throw new IOException("Invalid O1LS digest");
        }
        return result;
    }

    private static void writeAttemptId(DataOutputStream output,
                                       String attemptId)
        throws IOException {
        byte[] bytes = attemptId.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != 32 || !validAttemptId(attemptId)) {
            throw new IOException("Invalid O1LS transfer attempt");
        }
        output.write(bytes);
    }

    private static String readAttemptId(DataInputStream input)
        throws IOException {
        byte[] bytes = new byte[32];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        if (!validAttemptId(result)) {
            throw new IOException("Invalid O1LS transfer attempt");
        }
        return result;
    }

    private static boolean validAttention(int value) {
        return value == ATTENTION_NONE
            || value == ATTENTION_CURRENT_APPROVAL
            || value == ATTENTION_LIMIT_RETAINED
            || value == ATTENTION_FUTURE_RETAINED
            || value == ATTENTION_STAGED_CONFLICT;
    }

    private static String attentionMessage(int value) {
        if (value == ATTENTION_CURRENT_APPROVAL) {
            return "A portable Library snapshot is awaiting approval.";
        }
        if (value == ATTENTION_LIMIT_RETAINED) {
            return "A portable Library snapshot exceeded the catalog limit and was retained for review.";
        }
        if (value == ATTENTION_FUTURE_RETAINED) {
            return "Newer portable Library bytes were retained for review.";
        }
        if (value == ATTENTION_STAGED_CONFLICT) {
            return "Different portable Library bytes require attention.";
        }
        return "";
    }

    private static String stateMessage(State state) {
        if (state.localReconciliation != null) {
            return state.localReconciliation.kind
                    == LocalReconciliationKind.PUBLICATION
                ? "A validated local book is awaiting Library sync publication Retry."
                : "A local book removal is awaiting Library sync suppression Retry.";
        }
        return attentionMessage(state.attention);
    }

    private String visibleStateMessage(State state) {
        return hasQuarantinedState()
            ? "A quarantined Library sync state still requires attention."
            : stateMessage(state);
    }

    private static int minimumStateBytes() {
        return 3 * Integer.BYTES + Long.BYTES
            + 7 * Integer.BYTES;
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

    private boolean hasQuarantinedState() {
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            if (quarantineFileForTesting(index).exists()) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0
            || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Invalid O1LS file length");
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
                throw new IOException("O1LS file changed while reading");
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

    private static String sha256Hex(byte[] bytes) throws IOException {
        if (bytes == null
            || bytes.length
               > OctavoLibraryPortable.maximumFutureBytes()) {
            throw new IOException("Invalid O1LS hash input");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(Character.forDigit(
                    (value >>> 4) & 0xf, 16));
                result.append(Character.forDigit(value & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context application = context.getApplicationContext();
        return (application == null ? context : application).getFilesDir();
    }

    private static void requireDirectory(File directory)
        throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(
                "Unable to create Library synchronization directory");
        }
    }

    private void deleteTemporaryBestEffort() {
        if (temporaryFile.isFile()) {
            temporaryFile.delete();
        }
    }

    private static void deleteOwnedForTesting(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException(
                "Unable to clear Library sync test state");
        }
    }
}
