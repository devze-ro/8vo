package ro.devze.octavo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Caller-driven, annotation-only synchronization state machine.
 *
 * The coordinator yields values. It owns no provider interface, callback,
 * thread, scheduler, account, or network permission.
 */
final class OctavoAnnotationSyncCoordinator {
    static final String LOGICAL_OBJECT_NAME =
        "state/annotations/o1ap-v1";
    static final int MAX_PROVIDER_VALUE_BYTES = 512;
    static final int MAX_PRECONDITION_CONFLICTS = 3;

    enum StepKind {
        READ,
        WRITE,
        COMPLETE
    }

    enum WriteMode {
        CREATE_IF_MISSING,
        REPLACE_IF_REVISION
    }

    enum CompletionCode {
        CONVERGED,
        BUSY,
        LOCAL_NOT_LOADED,
        LOCAL_BLOCKED,
        SYNC_STATE_NOT_LOADED,
        SYNC_STATE_BLOCKED,
        SYNC_STATE_INVALID,
        SYNC_STATE_LIMIT,
        SYNC_STATE_PUBLISH_FAILED,
        BINDING_MISMATCH,
        REVIEW_REQUIRED,
        REMOTE_DELETION_REVIEW_REQUIRED,
        REMOTE_INVALID,
        REMOTE_FUTURE_VERSION,
        REMOTE_INPUT_LIMIT,
        REMOTE_DUPLICATES,
        LOCAL_JOIN_LIMIT,
        LOCAL_ANNOTATION_PUBLISH_FAILED,
        LOCAL_EXPORT_LIMIT,
        LOCAL_EXPORT_FAILED,
        UNAUTHORIZED,
        QUOTA,
        TRANSIENT,
        PERMANENT,
        CANCELLED,
        OUTCOME_UNKNOWN,
        INVALID_RESPONSE,
        REVISION_RETRY_LIMIT
    }

    enum ProviderFailure {
        UNAUTHORIZED,
        QUOTA,
        TRANSIENT,
        PERMANENT,
        CANCELLED
    }

    enum PendingStatus {
        CLEAN,
        PENDING,
        ATTENTION_REQUIRED,
        LOCAL_NOT_LOADED,
        LOCAL_BLOCKED,
        SYNC_STATE_NOT_LOADED,
        SYNC_STATE_BLOCKED,
        BINDING_MISMATCH,
        LOCAL_FAILURE
    }

    enum ReadStatus {
        MISSING,
        FOUND,
        DUPLICATES,
        FAILURE
    }

    enum WriteStatus {
        COMMITTED,
        PRECONDITION_FAILED,
        DEFINITE_FAILURE,
        OUTCOME_UNKNOWN
    }

    static final class ReadResult {
        final ReadStatus status;
        final ProviderFailure failure;
        final boolean portableBytesOverBound;
        private final byte[] handle;
        private final byte[] revision;
        private final byte[] portableBytes;

        private ReadResult(ReadStatus status,
                           ProviderFailure failure,
                           byte[] handle,
                           byte[] revision,
                           byte[] portableBytes) {
            this.status = status;
            this.failure = failure;
            this.handle = boundedCopy(handle, MAX_PROVIDER_VALUE_BYTES);
            this.revision = boundedCopy(revision,
                                        MAX_PROVIDER_VALUE_BYTES);
            portableBytesOverBound = portableBytes != null
                && portableBytes.length
                    > OctavoAnnotationStore.portableFileByteLimit();
            this.portableBytes = portableBytesOverBound
                ? null : copy(portableBytes);
        }

        static ReadResult missing() {
            return new ReadResult(ReadStatus.MISSING,
                                  null,
                                  null,
                                  null,
                                  null);
        }

        static ReadResult found(byte[] handle,
                                byte[] revision,
                                byte[] portableBytes) {
            return new ReadResult(ReadStatus.FOUND,
                                  null,
                                  handle,
                                  revision,
                                  portableBytes);
        }

        static ReadResult duplicates() {
            return new ReadResult(ReadStatus.DUPLICATES,
                                  null,
                                  null,
                                  null,
                                  null);
        }

        static ReadResult failure(ProviderFailure failure) {
            return new ReadResult(ReadStatus.FAILURE,
                                  failure,
                                  null,
                                  null,
                                  null);
        }
    }

    static final class WriteResult {
        final WriteStatus status;
        final ProviderFailure failure;
        private final byte[] handle;
        private final byte[] revision;

        private WriteResult(WriteStatus status,
                            ProviderFailure failure,
                            byte[] handle,
                            byte[] revision) {
            this.status = status;
            this.failure = failure;
            this.handle = boundedCopy(handle, MAX_PROVIDER_VALUE_BYTES);
            this.revision = boundedCopy(revision,
                                        MAX_PROVIDER_VALUE_BYTES);
        }

        static WriteResult committed(byte[] handle, byte[] revision) {
            return new WriteResult(WriteStatus.COMMITTED,
                                   null,
                                   handle,
                                   revision);
        }

        static WriteResult preconditionFailed() {
            return new WriteResult(WriteStatus.PRECONDITION_FAILED,
                                   null,
                                   null,
                                   null);
        }

        static WriteResult definiteFailure(ProviderFailure failure) {
            return new WriteResult(WriteStatus.DEFINITE_FAILURE,
                                   failure,
                                   null,
                                   null);
        }

        static WriteResult outcomeUnknown() {
            return new WriteResult(WriteStatus.OUTCOME_UNKNOWN,
                                   null,
                                   null,
                                   null);
        }
    }

    static final class Step {
        final StepKind kind;
        final CompletionCode completion;
        final String operationToken;
        final String bindingFingerprint;
        final String logicalObjectName;
        final WriteMode writeMode;
        final boolean pulled;
        final boolean pushed;
        final int preconditionConflicts;
        final OctavoAnnotationStore.PortableInspection review;
        private final byte[] handle;
        private final byte[] revision;
        private final byte[] portableBytes;

        private Step(StepKind kind,
                     CompletionCode completion,
                     String operationToken,
                     String bindingFingerprint,
                     WriteMode writeMode,
                     byte[] handle,
                     byte[] revision,
                     byte[] portableBytes,
                     boolean pulled,
                     boolean pushed,
                     int preconditionConflicts,
                     OctavoAnnotationStore.PortableInspection review) {
            this.kind = kind;
            this.completion = completion;
            this.operationToken = operationToken;
            this.bindingFingerprint = bindingFingerprint;
            logicalObjectName = LOGICAL_OBJECT_NAME;
            this.writeMode = writeMode;
            this.handle = copy(handle);
            this.revision = copy(revision);
            this.portableBytes = copy(portableBytes);
            this.pulled = pulled;
            this.pushed = pushed;
            this.preconditionConflicts = preconditionConflicts;
            this.review = review;
        }

        byte[] handle() {
            return copy(handle);
        }

        byte[] revision() {
            return copy(revision);
        }

        byte[] portableBytes() {
            return copy(portableBytes);
        }
    }

    private final OctavoAnnotationStore annotations;
    private final OctavoAnnotationSyncStore syncState;
    private final String sessionId;
    private long actionCounter;
    private boolean active;
    private boolean leaseHeld;
    private String bindingFingerprint = "";
    private String approvedDigest = "";
    private boolean approvedStagedAtBegin;
    private boolean approveRemoteRecreation;
    private boolean approvedRecreationAtBegin;
    private String outstandingToken = "";
    private StepKind outstandingKind;
    private boolean pulled;
    private boolean pushed;
    private int preconditionConflicts;
    private boolean remoteObservedPresent;
    private final byte[][] observedHandles =
        new byte[MAX_PRECONDITION_CONFLICTS][];
    private final byte[][] observedRevisions =
        new byte[MAX_PRECONDITION_CONFLICTS][];
    private final String[] observedRemoteDigests =
        new String[MAX_PRECONDITION_CONFLICTS];
    private int observedProviderVersions;
    private String outgoingDigest = "";

    OctavoAnnotationSyncCoordinator(OctavoAnnotationStore annotations,
                                    OctavoAnnotationSyncStore syncState) {
        this(annotations, syncState, randomSessionId());
    }

    OctavoAnnotationSyncCoordinator(OctavoAnnotationStore annotations,
                                    OctavoAnnotationSyncStore syncState,
                                    String sessionIdForTesting) {
        if (annotations == null || syncState == null
            || !isHex(sessionIdForTesting, 32)) {
            throw new IllegalArgumentException();
        }
        this.annotations = annotations;
        this.syncState = syncState;
        sessionId = sessionIdForTesting;
    }

    synchronized Step begin(String bindingFingerprint) {
        return begin(bindingFingerprint, "", false);
    }

    synchronized Step begin(String bindingFingerprint,
                            String approvedPendingDigest,
                            boolean approveRemoteRecreation) {
        if (active) {
            return standaloneCompletion(CompletionCode.BUSY);
        }
        if (!isHex(bindingFingerprint, 64)
            || (approvedPendingDigest != null
                && !approvedPendingDigest.isEmpty()
                && !isHex(approvedPendingDigest, 64))) {
            return standaloneCompletion(
                CompletionCode.INVALID_RESPONSE);
        }
        resetSession(bindingFingerprint,
                     approvedPendingDigest == null
                         ? "" : approvedPendingDigest,
                     approveRemoteRecreation);
        active = true;

        if (!annotations.loadAttemptedForPortableSync()) {
            return terminal(CompletionCode.LOCAL_NOT_LOADED, null);
        }
        if (annotations.mutationsBlocked()) {
            return terminal(CompletionCode.LOCAL_BLOCKED, null);
        }
        if (!syncState.loadAttempted()) {
            return terminal(
                CompletionCode.SYNC_STATE_NOT_LOADED, null);
        }
        if (syncState.updatesBlocked()) {
            return terminal(CompletionCode.SYNC_STATE_BLOCKED, null);
        }

        OctavoAnnotationSyncStore.LeaseResult lease =
            syncState.acquireLease(sessionId);
        if (lease == OctavoAnnotationSyncStore.LeaseResult.BUSY) {
            active = false;
            return standaloneCompletion(CompletionCode.BUSY);
        }
        if (lease != OctavoAnnotationSyncStore.LeaseResult.ACQUIRED) {
            return terminal(
                lease == OctavoAnnotationSyncStore.LeaseResult.NOT_LOADED
                    ? CompletionCode.SYNC_STATE_NOT_LOADED
                    : lease
                        == OctavoAnnotationSyncStore.LeaseResult.BLOCKED
                        ? CompletionCode.SYNC_STATE_BLOCKED
                        : CompletionCode.SYNC_STATE_INVALID,
                null);
        }
        leaseHeld = true;

        OctavoAnnotationSyncStore.UpdateResult bound =
            syncState.bind(bindingFingerprint);
        if (!bound.succeeded()) {
            return terminal(mapSyncUpdate(bound), null);
        }

        OctavoAnnotationSyncStore.Snapshot snapshot =
            syncState.snapshot();
        remoteObservedPresent = snapshot.remotePreviouslyPresent;
        if (snapshot.phase
            == OctavoAnnotationSyncStore.Phase.REMOTE_MISSING_REVIEW) {
            if (!approveRemoteRecreation) {
                return terminal(
                    CompletionCode.REMOTE_DELETION_REVIEW_REQUIRED,
                    null);
            }
            approvedRecreationAtBegin = true;
        }
        if (snapshot.phase
            == OctavoAnnotationSyncStore.Phase.REMOTE_SNAPSHOT) {
            byte[] staged = snapshot.remoteSnapshot();
            OctavoAnnotationStore.PortableInspection inspection =
                annotations.inspectPortableBytes(staged);
            Step inspectionFailure = inspectionFailure(inspection, true);
            if (inspectionFailure != null) {
                return inspectionFailure;
            }
            if (snapshot.convergedDigest.isEmpty()
                && !inspection.contentDigest.equals(approvedDigest)) {
                return terminal(
                    CompletionCode.REVIEW_REQUIRED, inspection);
            }
            if (snapshot.convergedDigest.isEmpty()) {
                approvedStagedAtBegin = true;
            }
            Step mergeFailure = mergeStaged(staged, inspection);
            if (mergeFailure != null) {
                return mergeFailure;
            }
        }
        return yieldRead();
    }

    synchronized Step acceptRead(String operationToken,
                                 String bindingFingerprint,
                                 ReadResult result) {
        if (!accepts(operationToken,
                     bindingFingerprint,
                     StepKind.READ)) {
            return standaloneCompletion(CompletionCode.INVALID_RESPONSE);
        }
        clearOutstanding();
        if (result == null || result.status == null) {
            return terminalWithAttention(
                CompletionCode.INVALID_RESPONSE,
                OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                null);
        }
        if (result.status == ReadStatus.FAILURE) {
            return terminalProviderFailure(result.failure);
        }
        if (result.status == ReadStatus.DUPLICATES) {
            Step presenceFailure = recordRemotePresence();
            if (presenceFailure != null) {
                return presenceFailure;
            }
            return terminalWithAttention(
                CompletionCode.REMOTE_DUPLICATES,
                OctavoAnnotationSyncStore.Attention.REMOTE_DUPLICATES,
                null);
        }
        if (result.status == ReadStatus.MISSING) {
            OctavoAnnotationSyncStore.Snapshot snapshot =
                syncState.snapshot();
            if ((snapshot.remotePreviouslyPresent
                 || remoteObservedPresent)
                && !approvedRecreationAtBegin) {
                OctavoAnnotationSyncStore.UpdateResult review =
                    syncState.markRemoteMissingReview(
                        this.bindingFingerprint);
                if (!review.succeeded()) {
                    return terminal(mapSyncUpdate(review), null);
                }
                return terminal(
                    CompletionCode.REMOTE_DELETION_REVIEW_REQUIRED,
                    null);
            }
            return createFromCheckedLocal();
        }
        Step presenceFailure = recordRemotePresence();
        if (presenceFailure != null) {
            return presenceFailure;
        }
        if (result.portableBytesOverBound) {
            return terminalWithAttention(
                CompletionCode.REMOTE_INPUT_LIMIT,
                OctavoAnnotationSyncStore.Attention.REMOTE_INPUT_LIMIT,
                null);
        }
        if (result.status != ReadStatus.FOUND
            || !validProviderValue(result.handle)
            || !validProviderValue(result.revision)
            || result.portableBytes == null) {
            return terminalWithAttention(
                CompletionCode.INVALID_RESPONSE,
                OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                null);
        }
        if (result.portableBytes.length
            > OctavoAnnotationStore.portableFileByteLimit()) {
            return terminalWithAttention(
                CompletionCode.REMOTE_INPUT_LIMIT,
                OctavoAnnotationSyncStore.Attention.REMOTE_INPUT_LIMIT,
                null);
        }

        OctavoAnnotationStore.PortableInspection inspection =
            annotations.inspectPortableBytes(result.portableBytes);
        Step inspectionFailure = inspectionFailure(inspection, false);
        if (inspectionFailure != null
            && inspection.status
                != OctavoAnnotationStore.PortableInspectionStatus.JOIN_LIMIT) {
            return inspectionFailure;
        }
        if (providerVersionHasDifferentDigest(result.handle,
                                              result.revision,
                                              inspection.contentDigest)) {
            return terminalWithAttention(
                CompletionCode.INVALID_RESPONSE,
                OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                null);
        }
        if (!rememberProviderVersion(result.handle,
                                     result.revision,
                                     inspection.contentDigest)) {
            return terminalWithAttention(
                CompletionCode.INVALID_RESPONSE,
                OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                null);
        }

        OctavoAnnotationSyncStore.Snapshot beforeStage =
            syncState.snapshot();
        boolean acknowledgedOwnInFlightPayload =
            beforeStage.phase
                == OctavoAnnotationSyncStore.Phase.WRITE_IN_FLIGHT
            && inspection.contentDigest.equals(
                beforeStage.inFlightDigest);
        OctavoAnnotationSyncStore.UpdateResult staged =
            syncState.stageRemote(this.bindingFingerprint,
                                  result.portableBytes);
        if (!staged.succeeded()) {
            return terminal(mapSyncUpdate(staged), null);
        }
        OctavoAnnotationSyncStore.Snapshot stagedState =
            syncState.snapshot();
        boolean matchesApprovedStagedSnapshot =
            approvedStagedAtBegin
            && inspection.contentDigest.equals(approvedDigest);
        if (stagedState.convergedDigest.isEmpty()
            && !acknowledgedOwnInFlightPayload
            && !matchesApprovedStagedSnapshot) {
            return terminal(CompletionCode.REVIEW_REQUIRED, inspection);
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.JOIN_LIMIT) {
            return terminalWithAttention(
                CompletionCode.LOCAL_JOIN_LIMIT,
                OctavoAnnotationSyncStore.Attention.LOCAL_JOIN_LIMIT,
                inspection);
        }

        Step mergeFailure = mergeStaged(result.portableBytes, inspection);
        if (mergeFailure != null) {
            return mergeFailure;
        }
        OctavoAnnotationStore.PortableExport exported = checkedExport();
        Step exportFailure = exportFailure(exported);
        if (exportFailure != null) {
            return exportFailure;
        }
        byte[] localBytes = exported.bytes();
        if (Arrays.equals(localBytes, result.portableBytes)) {
            OctavoAnnotationSyncStore.UpdateResult converged =
                syncState.markConverged(this.bindingFingerprint,
                                        inspection.contentDigest);
            if (!converged.succeeded()) {
                return terminal(mapSyncUpdate(converged), null);
            }
            return terminal(CompletionCode.CONVERGED, null);
        }
        OctavoAnnotationSyncStore.UpdateResult inFlight =
            syncState.markWriteInFlight(
                this.bindingFingerprint,
                digest(localBytes));
        if (!inFlight.succeeded()) {
            return terminal(mapSyncUpdate(inFlight), null);
        }
        return yieldWrite(WriteMode.REPLACE_IF_REVISION,
                          result.handle,
                          result.revision,
                          localBytes);
    }

    synchronized Step acceptWrite(String operationToken,
                                  String bindingFingerprint,
                                  WriteResult result) {
        if (!accepts(operationToken,
                     bindingFingerprint,
                     StepKind.WRITE)) {
            return standaloneCompletion(CompletionCode.INVALID_RESPONSE);
        }
        clearOutstanding();
        if (result == null || result.status == null) {
            return terminalWithAttention(
                CompletionCode.INVALID_RESPONSE,
                OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                null);
        }
        if (result.status == WriteStatus.COMMITTED) {
            if (!validProviderValue(result.handle)
                || !validProviderValue(result.revision)
                || !isHex(outgoingDigest, 64)) {
                return terminalWithAttention(
                    CompletionCode.INVALID_RESPONSE,
                    OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
                    null);
            }
            OctavoAnnotationSyncStore.UpdateResult converged =
                syncState.markConverged(this.bindingFingerprint,
                                        outgoingDigest);
            if (!converged.succeeded()) {
                return terminal(mapSyncUpdate(converged), null);
            }
            pushed = true;
            return terminal(CompletionCode.CONVERGED, null);
        }
        if (result.status == WriteStatus.PRECONDITION_FAILED) {
            preconditionConflicts += 1;
            if (preconditionConflicts
                >= MAX_PRECONDITION_CONFLICTS) {
                return terminalWithAttention(
                    CompletionCode.REVISION_RETRY_LIMIT,
                    OctavoAnnotationSyncStore.Attention.REVISION_RETRY_LIMIT,
                    null);
            }
            return yieldRead();
        }
        if (result.status == WriteStatus.OUTCOME_UNKNOWN) {
            return terminalWithAttention(
                CompletionCode.OUTCOME_UNKNOWN,
                OctavoAnnotationSyncStore.Attention.OUTCOME_UNKNOWN,
                null);
        }
        if (result.status == WriteStatus.DEFINITE_FAILURE) {
            return terminalProviderFailure(result.failure);
        }
        return terminalWithAttention(
            CompletionCode.INVALID_RESPONSE,
            OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE,
            null);
    }

    synchronized Step cancel(String operationToken,
                             String bindingFingerprint) {
        if (!active || outstandingKind == null
            || operationToken == null
            || !operationToken.equals(outstandingToken)
            || !this.bindingFingerprint.equals(bindingFingerprint)) {
            return standaloneCompletion(CompletionCode.INVALID_RESPONSE);
        }
        clearOutstanding();
        return terminal(CompletionCode.CANCELLED, null);
    }

    synchronized PendingStatus pendingStatus(String bindingFingerprint) {
        if (!isHex(bindingFingerprint, 64)) {
            return PendingStatus.BINDING_MISMATCH;
        }
        if (!annotations.loadAttemptedForPortableSync()) {
            return PendingStatus.LOCAL_NOT_LOADED;
        }
        if (annotations.mutationsBlocked()) {
            return PendingStatus.LOCAL_BLOCKED;
        }
        if (!syncState.loadAttempted()) {
            return PendingStatus.SYNC_STATE_NOT_LOADED;
        }
        if (syncState.updatesBlocked()) {
            return PendingStatus.SYNC_STATE_BLOCKED;
        }
        OctavoAnnotationSyncStore.Snapshot snapshot =
            syncState.snapshot();
        if (!snapshot.bindingFingerprint.isEmpty()
            && !snapshot.bindingFingerprint.equals(bindingFingerprint)) {
            return PendingStatus.BINDING_MISMATCH;
        }
        if (snapshot.attention
            != OctavoAnnotationSyncStore.Attention.NONE) {
            return PendingStatus.ATTENTION_REQUIRED;
        }
        OctavoAnnotationStore.PortableExport exported =
            annotations.checkedExportPortableBytes();
        if (exported.status
            == OctavoAnnotationStore.PortableExportStatus.NOT_LOADED) {
            return PendingStatus.LOCAL_NOT_LOADED;
        }
        if (exported.status
            == OctavoAnnotationStore.PortableExportStatus.BLOCKED) {
            return PendingStatus.LOCAL_BLOCKED;
        }
        if (exported.status
            != OctavoAnnotationStore.PortableExportStatus.EXPORTED) {
            return PendingStatus.LOCAL_FAILURE;
        }
        if (snapshot.bindingFingerprint.isEmpty()
            || snapshot.phase != OctavoAnnotationSyncStore.Phase.IDLE
            || snapshot.convergedDigest.isEmpty()) {
            return PendingStatus.PENDING;
        }
        return digest(exported.bytes()).equals(snapshot.convergedDigest)
            ? PendingStatus.CLEAN : PendingStatus.PENDING;
    }

    synchronized OctavoAnnotationSyncStore.UpdateResult discardPending(
        String bindingFingerprint) {
        if (active) {
            return OctavoAnnotationSyncStore.UpdateResult.INVALID;
        }
        OctavoAnnotationSyncStore.LeaseResult lease =
            syncState.acquireLease(sessionId);
        if (lease != OctavoAnnotationSyncStore.LeaseResult.ACQUIRED) {
            return mapLeaseToUpdate(lease);
        }
        try {
            return syncState.discardPending(bindingFingerprint);
        } finally {
            syncState.releaseLease(sessionId);
        }
    }

    synchronized OctavoAnnotationSyncStore.UpdateResult resetBinding(
        String expectedBinding, String newBinding) {
        if (active) {
            return OctavoAnnotationSyncStore.UpdateResult.INVALID;
        }
        OctavoAnnotationSyncStore.LeaseResult lease =
            syncState.acquireLease(sessionId);
        if (lease != OctavoAnnotationSyncStore.LeaseResult.ACQUIRED) {
            return mapLeaseToUpdate(lease);
        }
        try {
            return syncState.resetBinding(expectedBinding, newBinding);
        } finally {
            syncState.releaseLease(sessionId);
        }
    }

    synchronized OctavoAnnotationSyncStore.UpdateResult
        acknowledgeQuarantinedReset() {
        if (active) {
            return OctavoAnnotationSyncStore.UpdateResult.INVALID;
        }
        return syncState.acknowledgeQuarantinedReset();
    }

    private Step createFromCheckedLocal() {
        OctavoAnnotationStore.PortableExport exported = checkedExport();
        Step exportFailure = exportFailure(exported);
        if (exportFailure != null) {
            return exportFailure;
        }
        byte[] localBytes = exported.bytes();
        OctavoAnnotationSyncStore.UpdateResult inFlight =
            syncState.markWriteInFlight(bindingFingerprint,
                                        digest(localBytes));
        if (!inFlight.succeeded()) {
            return terminal(mapSyncUpdate(inFlight), null);
        }
        return yieldWrite(WriteMode.CREATE_IF_MISSING,
                          null,
                          null,
                          localBytes);
    }

    private Step recordRemotePresence() {
        OctavoAnnotationSyncStore.UpdateResult recorded =
            syncState.recordRemoteMayHaveExisted(bindingFingerprint);
        if (!recorded.succeeded()) {
            return terminal(mapSyncUpdate(recorded), null);
        }
        remoteObservedPresent = true;
        return null;
    }

    private Step mergeStaged(
        byte[] staged,
        OctavoAnnotationStore.PortableInspection inspection) {
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.JOIN_LIMIT) {
            return terminalWithAttention(
                CompletionCode.LOCAL_JOIN_LIMIT,
                OctavoAnnotationSyncStore.Attention.LOCAL_JOIN_LIMIT,
                inspection);
        }
        OctavoAnnotationStore.PortableMergeResult merged =
            annotations.mergePortableBytes(staged);
        if (merged == OctavoAnnotationStore.PortableMergeResult.MERGED) {
            pulled = true;
            return null;
        }
        if (merged == OctavoAnnotationStore.PortableMergeResult.UNCHANGED) {
            return null;
        }
        if (merged == OctavoAnnotationStore.PortableMergeResult.BLOCKED) {
            return terminal(CompletionCode.LOCAL_BLOCKED, null);
        }
        if (merged == OctavoAnnotationStore.PortableMergeResult.LIMIT) {
            return terminalWithAttention(
                CompletionCode.LOCAL_JOIN_LIMIT,
                OctavoAnnotationSyncStore.Attention.LOCAL_JOIN_LIMIT,
                inspection);
        }
        if (merged
            == OctavoAnnotationStore.PortableMergeResult.PUBLISH_FAILED) {
            return terminalWithAttention(
                CompletionCode.LOCAL_ANNOTATION_PUBLISH_FAILED,
                OctavoAnnotationSyncStore.Attention
                    .LOCAL_ANNOTATION_PUBLISH_FAILED,
                null);
        }
        if (merged
            == OctavoAnnotationStore.PortableMergeResult.FUTURE_VERSION) {
            return terminalWithAttention(
                CompletionCode.REMOTE_FUTURE_VERSION,
                OctavoAnnotationSyncStore.Attention.REMOTE_FUTURE_VERSION,
                null);
        }
        return terminalWithAttention(
            CompletionCode.REMOTE_INVALID,
            OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
            null);
    }

    private Step inspectionFailure(
        OctavoAnnotationStore.PortableInspection inspection,
        boolean staged) {
        if (inspection == null || inspection.status == null) {
            return terminalWithAttention(
                CompletionCode.REMOTE_INVALID,
                OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
                null);
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.READY
            || inspection.status
                == OctavoAnnotationStore.PortableInspectionStatus.UNCHANGED) {
            return null;
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.JOIN_LIMIT) {
            return staged
                ? terminalWithAttention(
                    CompletionCode.LOCAL_JOIN_LIMIT,
                    OctavoAnnotationSyncStore.Attention.LOCAL_JOIN_LIMIT,
                    inspection)
                : null;
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.NOT_LOADED) {
            return terminal(CompletionCode.LOCAL_NOT_LOADED, null);
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.BLOCKED) {
            return terminal(CompletionCode.LOCAL_BLOCKED, null);
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.FUTURE_VERSION) {
            return terminalWithAttention(
                CompletionCode.REMOTE_FUTURE_VERSION,
                OctavoAnnotationSyncStore.Attention.REMOTE_FUTURE_VERSION,
                null);
        }
        if (inspection.status
            == OctavoAnnotationStore.PortableInspectionStatus.INPUT_LIMIT) {
            return terminalWithAttention(
                CompletionCode.REMOTE_INPUT_LIMIT,
                OctavoAnnotationSyncStore.Attention.REMOTE_INPUT_LIMIT,
                null);
        }
        return terminalWithAttention(
            CompletionCode.REMOTE_INVALID,
            OctavoAnnotationSyncStore.Attention.REMOTE_INVALID,
            null);
    }

    private OctavoAnnotationStore.PortableExport checkedExport() {
        return annotations.checkedExportPortableBytes();
    }

    private Step exportFailure(
        OctavoAnnotationStore.PortableExport exported) {
        if (exported != null
            && exported.status
                == OctavoAnnotationStore.PortableExportStatus.EXPORTED) {
            return null;
        }
        if (exported == null) {
            return terminalWithAttention(
                CompletionCode.LOCAL_EXPORT_FAILED,
                OctavoAnnotationSyncStore.Attention.LOCAL_EXPORT_FAILED,
                null);
        }
        if (exported.status
            == OctavoAnnotationStore.PortableExportStatus.NOT_LOADED) {
            return terminal(CompletionCode.LOCAL_NOT_LOADED, null);
        }
        if (exported.status
            == OctavoAnnotationStore.PortableExportStatus.BLOCKED) {
            return terminal(CompletionCode.LOCAL_BLOCKED, null);
        }
        if (exported.status
            == OctavoAnnotationStore.PortableExportStatus.LIMIT) {
            return terminalWithAttention(
                CompletionCode.LOCAL_EXPORT_LIMIT,
                OctavoAnnotationSyncStore.Attention.LOCAL_EXPORT_LIMIT,
                null);
        }
        return terminalWithAttention(
            CompletionCode.LOCAL_EXPORT_FAILED,
            OctavoAnnotationSyncStore.Attention.LOCAL_EXPORT_FAILED,
            null);
    }

    private Step yieldRead() {
        String token = nextToken(StepKind.READ);
        outstandingToken = token;
        outstandingKind = StepKind.READ;
        return new Step(StepKind.READ,
                        null,
                        token,
                        bindingFingerprint,
                        null,
                        null,
                        null,
                        null,
                        pulled,
                        pushed,
                        preconditionConflicts,
                        null);
    }

    private Step yieldWrite(WriteMode mode,
                            byte[] handle,
                            byte[] revision,
                            byte[] portableBytes) {
        if (mode == null || portableBytes == null
            || (mode == WriteMode.REPLACE_IF_REVISION
                && (!validProviderValue(handle)
                    || !validProviderValue(revision)))
            || (mode == WriteMode.CREATE_IF_MISSING
                && (handle != null || revision != null))) {
            return terminal(CompletionCode.INVALID_RESPONSE, null);
        }
        outgoingDigest = digest(portableBytes);
        String token = nextToken(StepKind.WRITE);
        outstandingToken = token;
        outstandingKind = StepKind.WRITE;
        return new Step(StepKind.WRITE,
                        null,
                        token,
                        bindingFingerprint,
                        mode,
                        handle,
                        revision,
                        portableBytes,
                        pulled,
                        pushed,
                        preconditionConflicts,
                        null);
    }

    private Step terminalProviderFailure(ProviderFailure failure) {
        CompletionCode code = mapProviderFailure(failure);
        OctavoAnnotationSyncStore.Attention attention =
            attentionForProviderFailure(failure);
        return attention == null
            ? terminal(code, null)
            : terminalWithAttention(code, attention, null);
    }

    private Step terminalWithAttention(
        CompletionCode code,
        OctavoAnnotationSyncStore.Attention attention,
        OctavoAnnotationStore.PortableInspection review) {
        OctavoAnnotationSyncStore.UpdateResult recorded =
            syncState.recordAttention(bindingFingerprint, attention);
        if (!recorded.succeeded()) {
            return terminal(mapSyncUpdate(recorded), null);
        }
        return terminal(code, review);
    }

    private Step terminal(
        CompletionCode code,
        OctavoAnnotationStore.PortableInspection review) {
        active = false;
        clearOutstanding();
        if (leaseHeld) {
            syncState.releaseLease(sessionId);
            leaseHeld = false;
        }
        return new Step(StepKind.COMPLETE,
                        code,
                        "",
                        bindingFingerprint,
                        null,
                        null,
                        null,
                        null,
                        pulled,
                        pushed,
                        preconditionConflicts,
                        review);
    }

    private Step standaloneCompletion(CompletionCode code) {
        return new Step(StepKind.COMPLETE,
                        code,
                        "",
                        "",
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        0,
                        null);
    }

    private boolean accepts(String token,
                            String binding,
                            StepKind kind) {
        return active && kind != null && kind == outstandingKind
            && token != null && token.equals(outstandingToken)
            && binding != null
            && binding.equals(bindingFingerprint);
    }

    private void clearOutstanding() {
        outstandingToken = "";
        outstandingKind = null;
    }

    private void resetSession(String binding,
                              String approval,
                              boolean approveRecreation) {
        bindingFingerprint = binding;
        approvedDigest = approval;
        approvedStagedAtBegin = false;
        approveRemoteRecreation = approveRecreation;
        approvedRecreationAtBegin = false;
        clearOutstanding();
        pulled = false;
        pushed = false;
        preconditionConflicts = 0;
        remoteObservedPresent = false;
        for (int index = 0;
             index < observedProviderVersions;
             ++index) {
            observedHandles[index] = null;
            observedRevisions[index] = null;
            observedRemoteDigests[index] = null;
        }
        observedProviderVersions = 0;
        outgoingDigest = "";
    }

    private boolean providerVersionHasDifferentDigest(byte[] handle,
                                                      byte[] revision,
                                                      String contentDigest) {
        for (int index = 0;
             index < observedProviderVersions;
             ++index) {
            if (Arrays.equals(observedHandles[index], handle)
                && Arrays.equals(observedRevisions[index], revision)) {
                return !observedRemoteDigests[index].equals(contentDigest);
            }
        }
        return false;
    }

    private boolean rememberProviderVersion(byte[] handle,
                                            byte[] revision,
                                            String contentDigest) {
        for (int index = 0;
             index < observedProviderVersions;
             ++index) {
            if (Arrays.equals(observedHandles[index], handle)
                && Arrays.equals(observedRevisions[index], revision)) {
                return observedRemoteDigests[index].equals(contentDigest);
            }
        }
        if (observedProviderVersions >= observedHandles.length) {
            return false;
        }
        int index = observedProviderVersions;
        observedProviderVersions += 1;
        observedHandles[index] = copy(handle);
        observedRevisions[index] = copy(revision);
        observedRemoteDigests[index] = contentDigest;
        return true;
    }

    private String nextToken(StepKind kind) {
        if (actionCounter == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Sync operation token space exhausted");
        }
        actionCounter += 1;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write("8vo.port11.sync.operation.v1\n".getBytes(
                StandardCharsets.US_ASCII));
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(sessionId.getBytes(StandardCharsets.US_ASCII));
                output.write(bindingFingerprint.getBytes(
                    StandardCharsets.US_ASCII));
                output.writeInt(kind.ordinal());
                output.writeLong(actionCounter);
                output.flush();
            }
            return first128Hex(sha256(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static CompletionCode mapSyncUpdate(
        OctavoAnnotationSyncStore.UpdateResult result) {
        if (result == OctavoAnnotationSyncStore.UpdateResult.NOT_LOADED) {
            return CompletionCode.SYNC_STATE_NOT_LOADED;
        }
        if (result == OctavoAnnotationSyncStore.UpdateResult.BLOCKED) {
            return CompletionCode.SYNC_STATE_BLOCKED;
        }
        if (result
            == OctavoAnnotationSyncStore.UpdateResult.BINDING_MISMATCH) {
            return CompletionCode.BINDING_MISMATCH;
        }
        if (result == OctavoAnnotationSyncStore.UpdateResult.LIMIT) {
            return CompletionCode.SYNC_STATE_LIMIT;
        }
        if (result
            == OctavoAnnotationSyncStore.UpdateResult.PUBLISH_FAILED) {
            return CompletionCode.SYNC_STATE_PUBLISH_FAILED;
        }
        return CompletionCode.SYNC_STATE_INVALID;
    }

    private static OctavoAnnotationSyncStore.UpdateResult mapLeaseToUpdate(
        OctavoAnnotationSyncStore.LeaseResult result) {
        if (result == OctavoAnnotationSyncStore.LeaseResult.NOT_LOADED) {
            return OctavoAnnotationSyncStore.UpdateResult.NOT_LOADED;
        }
        if (result == OctavoAnnotationSyncStore.LeaseResult.BLOCKED) {
            return OctavoAnnotationSyncStore.UpdateResult.BLOCKED;
        }
        return OctavoAnnotationSyncStore.UpdateResult.INVALID;
    }

    private static CompletionCode mapProviderFailure(
        ProviderFailure failure) {
        if (failure == ProviderFailure.UNAUTHORIZED) {
            return CompletionCode.UNAUTHORIZED;
        }
        if (failure == ProviderFailure.QUOTA) {
            return CompletionCode.QUOTA;
        }
        if (failure == ProviderFailure.TRANSIENT) {
            return CompletionCode.TRANSIENT;
        }
        if (failure == ProviderFailure.PERMANENT) {
            return CompletionCode.PERMANENT;
        }
        if (failure == ProviderFailure.CANCELLED) {
            return CompletionCode.CANCELLED;
        }
        return CompletionCode.INVALID_RESPONSE;
    }

    private static OctavoAnnotationSyncStore.Attention
        attentionForProviderFailure(ProviderFailure failure) {
        if (failure == ProviderFailure.UNAUTHORIZED) {
            return OctavoAnnotationSyncStore.Attention.UNAUTHORIZED;
        }
        if (failure == ProviderFailure.QUOTA) {
            return OctavoAnnotationSyncStore.Attention.QUOTA;
        }
        if (failure == ProviderFailure.TRANSIENT) {
            return OctavoAnnotationSyncStore.Attention.TRANSIENT;
        }
        if (failure == ProviderFailure.PERMANENT) {
            return OctavoAnnotationSyncStore.Attention.PERMANENT;
        }
        if (failure == ProviderFailure.CANCELLED) {
            return null;
        }
        return OctavoAnnotationSyncStore.Attention.INVALID_RESPONSE;
    }

    private static boolean validProviderValue(byte[] value) {
        return value != null && value.length > 0
            && value.length <= MAX_PROVIDER_VALUE_BYTES;
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }

    private static byte[] boundedCopy(byte[] value, int maximumBytes) {
        if (value == null || value.length > maximumBytes) {
            return null;
        }
        return value.clone();
    }

    private static String digest(byte[] bytes) {
        return fullHex(sha256(bytes));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String first128Hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(32);
        for (int index = 0; index < 16; ++index) {
            appendHex(result, bytes[index]);
        }
        return result.toString();
    }

    private static String fullHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            appendHex(result, value);
        }
        return result.toString();
    }

    private static void appendHex(StringBuilder result, byte value) {
        result.append(Character.forDigit((value >>> 4) & 0xf, 16));
        result.append(Character.forDigit(value & 0xf, 16));
    }

    private static String randomSessionId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return fullHex(bytes);
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
}
