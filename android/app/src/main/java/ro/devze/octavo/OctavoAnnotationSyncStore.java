package ro.devze.octavo;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;

/**
 * Private, independently atomic retry state for annotation synchronization.
 *
 * This file is product state, not a provider manifest or portable payload.
 * Provider handles and revisions deliberately remain live-operation values.
 */
final class OctavoAnnotationSyncStore {
    enum LoadStatus {
        MISSING,
        LOADED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        FUTURE_VERSION_BLOCKED
    }

    enum Phase {
        IDLE(0),
        REMOTE_SNAPSHOT(1),
        WRITE_IN_FLIGHT(2),
        REMOTE_MISSING_REVIEW(3);

        final int wireId;

        Phase(int wireId) {
            this.wireId = wireId;
        }

        static Phase fromWireId(int wireId) {
            for (Phase phase : values()) {
                if (phase.wireId == wireId) {
                    return phase;
                }
            }
            return null;
        }
    }

    enum Attention {
        NONE(0),
        REMOTE_INVALID(1),
        REMOTE_FUTURE_VERSION(2),
        REMOTE_INPUT_LIMIT(3),
        REMOTE_DUPLICATES(4),
        UNAUTHORIZED(5),
        QUOTA(6),
        TRANSIENT(7),
        PERMANENT(8),
        OUTCOME_UNKNOWN(9),
        INVALID_RESPONSE(10),
        REVISION_RETRY_LIMIT(11),
        LOCAL_JOIN_LIMIT(12),
        LOCAL_ANNOTATION_PUBLISH_FAILED(13),
        LOCAL_EXPORT_LIMIT(14),
        LOCAL_EXPORT_FAILED(15);

        final int wireId;

        Attention(int wireId) {
            this.wireId = wireId;
        }

        static Attention fromWireId(int wireId) {
            for (Attention attention : values()) {
                if (attention.wireId == wireId) {
                    return attention;
                }
            }
            return null;
        }
    }

    enum UpdateResult {
        PUBLISHED,
        UNCHANGED,
        NOT_LOADED,
        BLOCKED,
        BINDING_MISMATCH,
        INVALID,
        LIMIT,
        PUBLISH_FAILED;

        boolean succeeded() {
            return this == PUBLISHED || this == UNCHANGED;
        }
    }

    enum LeaseResult {
        ACQUIRED,
        BUSY,
        NOT_LOADED,
        BLOCKED,
        INVALID
    }

    static final class Snapshot {
        final String bindingFingerprint;
        final String convergedDigest;
        final Phase phase;
        final Attention attention;
        final boolean remotePreviouslyPresent;
        final String inFlightDigest;
        private final byte[] remoteSnapshot;

        private Snapshot(State state) {
            bindingFingerprint = state.bindingFingerprint;
            convergedDigest = state.convergedDigest;
            phase = state.phase;
            attention = state.attention;
            remotePreviouslyPresent = state.remotePreviouslyPresent;
            inFlightDigest = state.inFlightDigest;
            remoteSnapshot = state.remoteSnapshot;
        }

        byte[] remoteSnapshot() {
            return remoteSnapshot == null
                ? null : remoteSnapshot.clone();
        }
    }

    private static final int MAGIC = 0x4f314153; // "O1AS"
    private static final int VERSION = 1;
    private static final int HEADER_FIELD_COUNT = 7;
    private static final int HEX_DIGEST_BYTES = 64;
    private static final int MAXIMUM_OVERHEAD_BYTES = 236;
    private static final int MINIMUM_FILE_BYTES = 44;
    private static final int MAX_FILE_BYTES =
        OctavoAnnotationStore.portableFileByteLimit()
            + MAXIMUM_OVERHEAD_BYTES;
    private static final int QUARANTINE_SLOTS = 3;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "annotation-sync.v1";
    private static final String TEMPORARY_FILE = "annotation-sync.v1.tmp";
    private static final String QUARANTINE_PREFIX =
        "annotation-sync.corrupt.";

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private State current = State.empty();
    private LoadStatus loadStatus = LoadStatus.MISSING;
    private boolean loadAttempted;
    private boolean updatesBlocked;
    private boolean failNextPublishForTesting;
    private String leaseOwner = "";

    OctavoAnnotationSyncStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoAnnotationSyncStore(File filesDirectory) {
        if (filesDirectory == null) {
            throw new IllegalArgumentException();
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
    }

    synchronized LoadStatus load() {
        loadAttempted = true;
        updatesBlocked = false;
        if (!stateFile.exists()) {
            current = State.empty();
            if (hasQuarantinedState()) {
                updatesBlocked = true;
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                return loadStatus;
            }
            loadStatus = LoadStatus.MISSING;
            return loadStatus;
        }
        try {
            if (hasFutureVersion(stateFile)) {
                updatesBlocked = true;
                loadStatus = LoadStatus.FUTURE_VERSION_BLOCKED;
                return loadStatus;
            }
            current = decode(stateFile);
            loadStatus = LoadStatus.LOADED;
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState()) {
                current = State.empty();
                updatesBlocked = true;
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
            } else {
                updatesBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
            }
            return loadStatus;
        }
    }

    synchronized UpdateResult bind(String bindingFingerprint) {
        UpdateResult readiness = readiness(bindingFingerprint, false);
        if (readiness != null) {
            return readiness;
        }
        if (current.bindingFingerprint.equals(bindingFingerprint)) {
            return UpdateResult.UNCHANGED;
        }
        if (!current.bindingFingerprint.isEmpty()) {
            return UpdateResult.BINDING_MISMATCH;
        }
        return publishCandidate(new State(bindingFingerprint,
                                          "",
                                          Phase.IDLE,
                                          Attention.NONE,
                                          false,
                                          "",
                                          null));
    }

    synchronized UpdateResult recordRemoteMayHaveExisted(
        String bindingFingerprint) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        if (current.remotePreviouslyPresent) {
            if (current.phase != Phase.REMOTE_MISSING_REVIEW) {
                return UpdateResult.UNCHANGED;
            }
        }
        Phase nextPhase = current.phase == Phase.REMOTE_MISSING_REVIEW
            ? Phase.IDLE : current.phase;
        return publishCandidate(new State(bindingFingerprint,
                                          current.convergedDigest,
                                          nextPhase,
                                          current.attention,
                                          true,
                                          nextPhase == Phase.WRITE_IN_FLIGHT
                                              ? current.inFlightDigest : "",
                                          current.remoteSnapshot));
    }

    synchronized UpdateResult markRemoteMissingReview(
        String bindingFingerprint) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        State candidate = new State(bindingFingerprint,
                                    current.convergedDigest,
                                    Phase.REMOTE_MISSING_REVIEW,
                                    Attention.NONE,
                                    true,
                                    "",
                                    null);
        if (stateEquals(current, candidate)) {
            return UpdateResult.UNCHANGED;
        }
        return publishCandidate(candidate);
    }

    synchronized UpdateResult recordAttention(
        String bindingFingerprint, Attention attention) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        if (attention == null || attention == Attention.NONE) {
            return UpdateResult.INVALID;
        }
        if (current.attention == attention) {
            return UpdateResult.UNCHANGED;
        }
        return publishCandidate(new State(bindingFingerprint,
                                          current.convergedDigest,
                                          current.phase,
                                          attention,
                                          current.remotePreviouslyPresent,
                                          current.inFlightDigest,
                                          current.remoteSnapshot));
    }

    synchronized LeaseResult acquireLease(String ownerToken) {
        if (!loadAttempted) {
            return LeaseResult.NOT_LOADED;
        }
        if (updatesBlocked) {
            return LeaseResult.BLOCKED;
        }
        if (!isHex(ownerToken, 32)) {
            return LeaseResult.INVALID;
        }
        if (!leaseOwner.isEmpty() && !leaseOwner.equals(ownerToken)) {
            return LeaseResult.BUSY;
        }
        leaseOwner = ownerToken;
        return LeaseResult.ACQUIRED;
    }

    synchronized void releaseLease(String ownerToken) {
        if (ownerToken != null && leaseOwner.equals(ownerToken)) {
            leaseOwner = "";
        }
    }

    synchronized boolean leaseHeldForTesting() {
        return !leaseOwner.isEmpty();
    }

    synchronized UpdateResult stageRemote(String bindingFingerprint,
                                           byte[] portableBytes) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        if (portableBytes == null
            || portableBytes.length < 5 * Integer.BYTES
            || portableBytes.length
                > OctavoAnnotationStore.portableFileByteLimit()) {
            return UpdateResult.INVALID;
        }
        return publishCandidate(new State(bindingFingerprint,
                                          current.convergedDigest,
                                          Phase.REMOTE_SNAPSHOT,
                                          Attention.NONE,
                                          true,
                                          "",
                                          portableBytes));
    }

    synchronized UpdateResult markWriteInFlight(
        String bindingFingerprint,
        String submittedDigest) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        if (!isHex(submittedDigest, HEX_DIGEST_BYTES)) {
            return UpdateResult.INVALID;
        }
        if (current.phase == Phase.WRITE_IN_FLIGHT
            && current.attention == Attention.NONE
            && current.remotePreviouslyPresent
            && current.inFlightDigest.equals(submittedDigest)
            && current.remoteSnapshot == null) {
            return UpdateResult.UNCHANGED;
        }
        return publishCandidate(new State(bindingFingerprint,
                                          current.convergedDigest,
                                          Phase.WRITE_IN_FLIGHT,
                                          Attention.NONE,
                                          true,
                                          submittedDigest,
                                          null));
    }

    synchronized UpdateResult markConverged(String bindingFingerprint,
                                            String contentDigest) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        if (!isHex(contentDigest, HEX_DIGEST_BYTES)) {
            return UpdateResult.INVALID;
        }
        State candidate = new State(bindingFingerprint,
                                    contentDigest,
                                    Phase.IDLE,
                                    Attention.NONE,
                                    true,
                                    "",
                                    null);
        if (stateEquals(current, candidate)) {
            return UpdateResult.UNCHANGED;
        }
        return publishCandidate(candidate);
    }

    synchronized UpdateResult discardPending(
        String bindingFingerprint) {
        UpdateResult readiness = readiness(bindingFingerprint, true);
        if (readiness != null) {
            return readiness;
        }
        if (current.phase == Phase.IDLE
            && current.attention == Attention.NONE) {
            return UpdateResult.UNCHANGED;
        }
        return publishCandidate(new State(bindingFingerprint,
                                          current.convergedDigest,
                                          Phase.IDLE,
                                          Attention.NONE,
                                          current.remotePreviouslyPresent,
                                          "",
                                          null));
    }

    synchronized UpdateResult resetBinding(String expectedBinding,
                                           String newBinding) {
        if (!loadAttempted) {
            return UpdateResult.NOT_LOADED;
        }
        if (updatesBlocked) {
            return UpdateResult.BLOCKED;
        }
        if (!isHex(expectedBinding, HEX_DIGEST_BYTES)
            || !isHex(newBinding, HEX_DIGEST_BYTES)) {
            return UpdateResult.INVALID;
        }
        if (!current.bindingFingerprint.equals(expectedBinding)) {
            return UpdateResult.BINDING_MISMATCH;
        }
        if (expectedBinding.equals(newBinding)
            && current.convergedDigest.isEmpty()
            && current.phase == Phase.IDLE
            && current.attention == Attention.NONE
            && !current.remotePreviouslyPresent) {
            return UpdateResult.UNCHANGED;
        }
        return publishCandidate(new State(newBinding,
                                          "",
                                          Phase.IDLE,
                                          Attention.NONE,
                                          false,
                                          "",
                                          null));
    }

    synchronized UpdateResult acknowledgeQuarantinedReset() {
        if (!loadAttempted) {
            return UpdateResult.NOT_LOADED;
        }
        if (loadStatus != LoadStatus.CORRUPT_QUARANTINED
            || stateFile.exists()) {
            return UpdateResult.INVALID;
        }
        State empty = State.empty();
        try {
            if (!publish(empty)) {
                return UpdateResult.PUBLISH_FAILED;
            }
            current = empty;
            updatesBlocked = false;
            loadStatus = LoadStatus.LOADED;
            return UpdateResult.PUBLISHED;
        } catch (IOException | RuntimeException exception) {
            return UpdateResult.PUBLISH_FAILED;
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(current);
    }

    synchronized LoadStatus loadStatus() {
        return loadStatus;
    }

    synchronized boolean loadAttempted() {
        return loadAttempted;
    }

    synchronized boolean updatesBlocked() {
        return updatesBlocked;
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    synchronized byte[] canonicalBytesForTesting() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        writeComplete(bytes, current);
        return bytes.toByteArray();
    }

    static int maximumFileBytesForTesting() {
        return MAX_FILE_BYTES;
    }

    static int currentVersionForTesting() {
        return VERSION;
    }

    File stateFileForTesting() {
        return stateFile;
    }

    File temporaryFileForTesting() {
        return temporaryFile;
    }

    File quarantineFileForTesting(int index) {
        return new File(rootDirectory, QUARANTINE_PREFIX + index);
    }

    private UpdateResult readiness(String bindingFingerprint,
                                   boolean requireExistingBinding) {
        if (!loadAttempted) {
            return UpdateResult.NOT_LOADED;
        }
        if (updatesBlocked) {
            return UpdateResult.BLOCKED;
        }
        if (!isHex(bindingFingerprint, HEX_DIGEST_BYTES)) {
            return UpdateResult.INVALID;
        }
        if (requireExistingBinding
            && !current.bindingFingerprint.equals(bindingFingerprint)) {
            return UpdateResult.BINDING_MISMATCH;
        }
        return null;
    }

    private UpdateResult publishCandidate(State candidate) {
        try {
            validateState(candidate);
            if (stateEquals(current, candidate)) {
                return UpdateResult.UNCHANGED;
            }
            if (!publish(candidate)) {
                return UpdateResult.PUBLISH_FAILED;
            }
            current = candidate;
            return UpdateResult.PUBLISHED;
        } catch (LimitException exception) {
            return UpdateResult.LIMIT;
        } catch (IOException | RuntimeException exception) {
            return UpdateResult.INVALID;
        }
    }

    private boolean publish(State candidate) throws IOException {
        if (failNextPublishForTesting) {
            failNextPublishForTesting = false;
            return false;
        }
        requireDirectory(rootDirectory);
        try {
            try (FileOutputStream output =
                     new FileOutputStream(temporaryFile, false)) {
                writeComplete(output, candidate);
                output.flush();
                output.getFD().sync();
            }
            Files.move(temporaryFile.toPath(),
                       stateFile.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            return false;
        }
    }

    private static void writeComplete(OutputStream output, State state)
        throws IOException {
        validateState(state);
        long completeBytes = completeByteCount(state);
        if (completeBytes > MAX_FILE_BYTES) {
            throw new LimitException("Sync state exceeds its bound");
        }
        CrcOutputStream checked = new CrcOutputStream(
            output, MAX_FILE_BYTES);
        DataOutputStream data = new DataOutputStream(checked);
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        data.writeInt(HEADER_FIELD_COUNT);
        writeString(data, state.bindingFingerprint);
        writeString(data, state.convergedDigest);
        data.writeInt(state.phase.wireId);
        data.writeInt(state.attention.wireId);
        data.writeInt(state.remotePreviouslyPresent ? 1 : 0);
        writeString(data, state.inFlightDigest);
        if (state.remoteSnapshot == null) {
            data.writeInt(0);
        } else {
            data.writeInt(state.remoteSnapshot.length);
            data.write(state.remoteSnapshot);
        }
        data.flush();
        int checksum = (int)checked.checksum();
        checked.setChecking(false);
        data.writeInt(checksum);
        data.flush();
        if (checked.count() != completeBytes) {
            throw new IOException("Unexpected sync-state byte count");
        }
    }

    private static State decode(File file) throws IOException {
        long length = file.length();
        if (length < MINIMUM_FILE_BYTES
            || length > MAX_FILE_BYTES) {
            throw new IOException("Invalid sync-state file length");
        }
        try (FileInputStream input = new FileInputStream(file)) {
            CrcInputStream checked = new CrcInputStream(input);
            DataInputStream data = new DataInputStream(checked);
            try {
                if (data.readInt() != MAGIC
                    || data.readInt() != VERSION
                    || data.readInt() != HEADER_FIELD_COUNT) {
                    throw new IOException("Invalid sync-state header");
                }
                String binding = readString(data, HEX_DIGEST_BYTES);
                String converged = readString(data, HEX_DIGEST_BYTES);
                Phase phase = Phase.fromWireId(data.readInt());
                Attention attention = Attention.fromWireId(data.readInt());
                int remotePreviouslyPresentValue = data.readInt();
                if (remotePreviouslyPresentValue < 0
                    || remotePreviouslyPresentValue > 1) {
                    throw new IOException(
                        "Invalid prior-remote sync-state flag");
                }
                String inFlightDigest = readString(
                    data, HEX_DIGEST_BYTES);
                int snapshotLength = data.readInt();
                if (snapshotLength < 0
                    || snapshotLength
                        > OctavoAnnotationStore.portableFileByteLimit()) {
                    throw new LimitException(
                        "Invalid sync-state snapshot length");
                }
                byte[] snapshot = snapshotLength == 0
                    ? null : new byte[snapshotLength];
                if (snapshot != null) {
                    data.readFully(snapshot);
                }
                int actualChecksum = (int)checked.checksum();
                checked.setChecking(false);
                int storedChecksum = data.readInt();
                if (storedChecksum != actualChecksum || data.read() != -1) {
                    throw new IOException("Invalid sync-state checksum");
                }
                State state = new State(binding,
                                        converged,
                                        phase,
                                        attention,
                                        remotePreviouslyPresentValue != 0,
                                        inFlightDigest,
                                        snapshot);
                validateState(state);
                return state;
            } catch (EOFException exception) {
                throw new IOException("Truncated sync state", exception);
            }
        }
    }

    private static long completeByteCount(State state) {
        int snapshotBytes = state.remoteSnapshot == null
            ? 0 : state.remoteSnapshot.length;
        return 3L * Integer.BYTES
            + Integer.BYTES + state.bindingFingerprint.length()
            + Integer.BYTES + state.convergedDigest.length()
            + Integer.BYTES
            + Integer.BYTES
            + Integer.BYTES
            + Integer.BYTES + state.inFlightDigest.length()
            + Integer.BYTES + snapshotBytes
            + Integer.BYTES;
    }

    private static void validateState(State state) throws IOException {
        if (state == null || state.phase == null
            || state.attention == null) {
            throw new IOException("Invalid sync state");
        }
        boolean empty = state.bindingFingerprint.isEmpty();
        if ((!empty
             && !isHex(state.bindingFingerprint, HEX_DIGEST_BYTES))
            || (!state.convergedDigest.isEmpty()
                && !isHex(state.convergedDigest, HEX_DIGEST_BYTES))
            || (!state.inFlightDigest.isEmpty()
                && !isHex(state.inFlightDigest, HEX_DIGEST_BYTES))) {
            throw new IOException("Invalid sync-state digest");
        }
        if (empty
            && (!state.convergedDigest.isEmpty()
                 || state.phase != Phase.IDLE
                 || state.attention != Attention.NONE
                 || state.remotePreviouslyPresent
                || !state.inFlightDigest.isEmpty()
                || state.remoteSnapshot != null)) {
            throw new IOException("Unbound sync state has data");
        }
        if (state.phase == Phase.REMOTE_SNAPSHOT) {
            if (state.remoteSnapshot == null
                || !state.remotePreviouslyPresent
                || state.remoteSnapshot.length < 5 * Integer.BYTES
                || state.remoteSnapshot.length
                    > OctavoAnnotationStore.portableFileByteLimit()) {
                throw new IOException("Invalid staged remote snapshot");
            }
        } else if (state.remoteSnapshot != null) {
            throw new IOException("Unexpected staged remote snapshot");
        }
        if ((state.phase == Phase.WRITE_IN_FLIGHT)
            != !state.inFlightDigest.isEmpty()) {
            throw new IOException("Invalid in-flight content digest");
        }
        if (state.phase == Phase.WRITE_IN_FLIGHT
            && !state.remotePreviouslyPresent) {
            throw new IOException("In-flight write has no remote history");
        }
        if (state.phase == Phase.REMOTE_MISSING_REVIEW
            && !state.remotePreviouslyPresent) {
            throw new IOException("Missing review has no remote history");
        }
        if (!state.convergedDigest.isEmpty()
            && !state.remotePreviouslyPresent) {
            throw new IOException(
                "Converged sync state forgot prior remote presence");
        }
        if (completeByteCount(state) > MAX_FILE_BYTES) {
            throw new LimitException("Sync state exceeds its bound");
        }
    }

    private static boolean stateEquals(State left, State right) {
        if (!left.bindingFingerprint.equals(right.bindingFingerprint)
            || !left.convergedDigest.equals(right.convergedDigest)
            || left.phase != right.phase
            || left.attention != right.attention
            || left.remotePreviouslyPresent
                != right.remotePreviouslyPresent
            || !left.inFlightDigest.equals(right.inFlightDigest)) {
            return false;
        }
        if (left.remoteSnapshot == null || right.remoteSnapshot == null) {
            return left.remoteSnapshot == right.remoteSnapshot;
        }
        if (left.remoteSnapshot.length != right.remoteSnapshot.length) {
            return false;
        }
        for (int index = 0;
             index < left.remoteSnapshot.length;
             ++index) {
            if (left.remoteSnapshot[index] != right.remoteSnapshot[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean quarantineCorruptState() {
        if (!stateFile.isFile()) {
            return false;
        }
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            File quarantine = quarantineFileForTesting(index);
            if (quarantine.exists()) {
                continue;
            }
            try {
                Files.move(stateFile.toPath(),
                           quarantine.toPath(),
                           StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (IOException | RuntimeException exception) {
                return false;
            }
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

    private static boolean hasFutureVersion(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(
                 new FileInputStream(file))) {
            int magic = input.readInt();
            int version = input.readInt();
            return magic == MAGIC && version > VERSION;
        } catch (EOFException exception) {
            return false;
        }
    }

    private void deleteTemporaryBestEffort() {
        try {
            Files.deleteIfExists(temporaryFile.toPath());
        } catch (IOException | RuntimeException ignored) {
            // The stale bounded temporary file is never treated as state.
        }
    }

    private static void requireDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            return;
        }
        if (directory.exists() || !directory.mkdirs()) {
            throw new IOException("Cannot create sync-state directory");
        }
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null || context.getFilesDir() == null) {
            throw new IllegalArgumentException();
        }
        return context.getFilesDir();
    }

    private static void writeString(DataOutputStream output, String value)
        throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maximumBytes)
        throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximumBytes) {
            throw new IOException("Invalid sync-state string length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
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

    private static final class State {
        final String bindingFingerprint;
        final String convergedDigest;
        final Phase phase;
        final Attention attention;
        final boolean remotePreviouslyPresent;
        final String inFlightDigest;
        final byte[] remoteSnapshot;

        State(String bindingFingerprint,
              String convergedDigest,
              Phase phase,
              Attention attention,
              boolean remotePreviouslyPresent,
              String inFlightDigest,
              byte[] remoteSnapshot) {
            this.bindingFingerprint = bindingFingerprint;
            this.convergedDigest = convergedDigest;
            this.phase = phase;
            this.attention = attention;
            this.remotePreviouslyPresent = remotePreviouslyPresent;
            this.inFlightDigest = inFlightDigest;
            this.remoteSnapshot = remoteSnapshot == null
                ? null : remoteSnapshot.clone();
        }

        static State empty() {
            return new State("", "", Phase.IDLE, Attention.NONE,
                             false, "", null);
        }
    }

    private static final class CrcOutputStream extends FilterOutputStream {
        private final CRC32 checksum = new CRC32();
        private final int maximumBytes;
        private int count;
        private boolean checking = true;

        CrcOutputStream(OutputStream output, int maximumBytes) {
            super(output);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            out.write(value);
            if (checking) {
                checksum.update(value);
            }
            count += 1;
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
            throws IOException {
            if (bytes == null) {
                throw new NullPointerException("bytes");
            }
            if (offset < 0 || length < 0
                || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            out.write(bytes, offset, length);
            if (checking) {
                checksum.update(bytes, offset, length);
            }
            count += length;
        }

        long checksum() {
            return checksum.getValue();
        }

        int count() {
            return count;
        }

        void setChecking(boolean checking) {
            this.checking = checking;
        }

        private void requireCapacity(int additional) throws LimitException {
            if (additional < 0 || additional > maximumBytes - count) {
                throw new LimitException("Sync state exceeds its bound");
            }
        }
    }

    private static final class CrcInputStream extends FilterInputStream {
        private final CRC32 checksum = new CRC32();
        private boolean checking = true;

        CrcInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0 && checking) {
                checksum.update(value);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
            throws IOException {
            int read = in.read(bytes, offset, length);
            if (read > 0 && checking) {
                checksum.update(bytes, offset, read);
            }
            return read;
        }

        long checksum() {
            return checksum.getValue();
        }

        void setChecking(boolean checking) {
            this.checking = checking;
        }
    }

    private static final class LimitException extends IOException {
        LimitException(String message) {
            super(message);
        }
    }
}
