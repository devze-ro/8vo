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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Independent private O1RS state for provider-neutral reading positions.
 *
 * The caller supplies the successful-post flag, exact presented anchor, and
 * containing Reader0 page range together. This class rejects an empty,
 * cross-spine, or non-containing range before publication; it never samples
 * mutable Reader0 state or infers presentation from a navigation request.
 */
final class OctavoReadingPositionStore {
    enum LoadStatus {
        MISSING,
        LOADED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        FUTURE_VERSION_BLOCKED
    }

    enum MutationResult {
        UPDATED,
        UNCHANGED,
        INVALID,
        BLOCKED,
        LIMIT,
        EXHAUSTED,
        CONFLICT,
        PUBLISH_FAILED;

        boolean succeeded() {
            return this == UPDATED || this == UNCHANGED;
        }
    }

    enum PortableMergeResult {
        MERGED,
        UNCHANGED,
        INVALID,
        FUTURE_VERSION,
        LIMIT,
        EQUIVOCATION,
        OWN_LANE_ADVANCE,
        BLOCKED,
        PUBLISH_FAILED;

        boolean succeeded() {
            return this == MERGED || this == UNCHANGED;
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
        GO_PENDING(1),
        ACCEPTED(2),
        STAYED(3),
        DISMISSED_AT_EPOCH(4);

        final int wireId;

        Decision(int wireId) {
            this.wireId = wireId;
        }

        static Decision fromWireId(int wireId) {
            for (Decision decision : values()) {
                if (decision.wireId == wireId) {
                    return decision;
                }
            }
            return null;
        }
    }

    static final class Candidate {
        final String bookDigest;
        final String deviceId;
        final long sequence;
        final long spineIndex;
        final long byteOffset;
        final Decision decision;
        final long reviewEpoch;
        final long originSequence;
        final long originSpineIndex;
        final long originByteOffset;

        private Candidate(String bookDigest,
                          LaneState lane,
                          long reviewEpoch,
                          LaneState local) {
            this.bookDigest = bookDigest;
            deviceId = lane.lane.deviceId;
            sequence = lane.lane.sequence;
            spineIndex = lane.lane.spineIndex;
            byteOffset = lane.lane.byteOffset;
            decision = lane.decision;
            this.reviewEpoch = reviewEpoch;
            if (lane.decision == Decision.GO_PENDING) {
                originSequence = lane.originSequence;
                originSpineIndex = lane.originSpineIndex;
                originByteOffset = lane.originByteOffset;
            } else {
                originSequence = local == null ? 0 : local.lane.sequence;
                originSpineIndex = local == null ? 0 : local.lane.spineIndex;
                originByteOffset = local == null ? 0 : local.lane.byteOffset;
            }
        }

        boolean sameIdentity(Candidate other) {
            return other != null
                && bookDigest.equals(other.bookDigest)
                && deviceId.equals(other.deviceId)
                && sequence == other.sequence;
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
        final OctavoReadingPositionPortable.Lane lane;
        final Decision decision;
        final long decisionEpoch;
        final long originSequence;
        final long originSpineIndex;
        final long originByteOffset;

        LaneState(OctavoReadingPositionPortable.Lane lane,
                  Decision decision,
                  long decisionEpoch,
                  long originSequence,
                  long originSpineIndex,
                  long originByteOffset) {
            this.lane = lane;
            this.decision = decision;
            this.decisionEpoch = decisionEpoch;
            this.originSequence = originSequence;
            this.originSpineIndex = originSpineIndex;
            this.originByteOffset = originByteOffset;
        }

        static LaneState undecided(OctavoReadingPositionPortable.Lane lane) {
            return new LaneState(lane, Decision.NONE, 0, 0, 0, 0);
        }

        LaneState withDecision(Decision next,
                               long epoch,
                               long originSequence,
                               long originSpineIndex,
                               long originByteOffset) {
            return new LaneState(lane, next, epoch,
                                 originSequence,
                                 originSpineIndex,
                                 originByteOffset);
        }
    }

    private static final class BookState {
        final String digest;
        final long reviewEpoch;
        final TreeMap<String, LaneState> lanes;

        BookState(String digest,
                  long reviewEpoch,
                  Map<String, LaneState> lanes) {
            this.digest = digest;
            this.reviewEpoch = reviewEpoch;
            this.lanes = new TreeMap<>(lanes);
        }

        BookState withEpoch(long epoch) {
            return new BookState(digest, epoch, lanes);
        }

        BookState withLanes(Map<String, LaneState> next) {
            return new BookState(digest, reviewEpoch, next);
        }
    }

    private static final class State {
        final String deviceId;
        final TreeMap<String, BookState> books;

        State(String deviceId, Map<String, BookState> books) {
            this.deviceId = deviceId;
            this.books = new TreeMap<>(books);
        }

        State withBook(BookState book) {
            TreeMap<String, BookState> next = new TreeMap<>(books);
            next.put(book.digest, book);
            return new State(deviceId, next);
        }
    }

    private static final int STORE_MAGIC = 0x4F315253; // "O1RS"
    private static final int STORE_VERSION = 1;
    private static final int STORE_HEADER_FIELD_COUNT = 2;
    private static final int MAX_BOOKS = 64;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int DEVICE_ID_BYTES = 32;
    private static final int BOOK_DIGEST_BYTES = 64;
    private static final int QUARANTINE_SLOTS = 3;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "reading-positions.v1";
    private static final String TEMPORARY_FILE = "reading-positions.v1.tmp";
    private static final String QUARANTINE_PREFIX =
        "reading-positions.corrupt.";

    private static final Comparator<Candidate> CANDIDATE_ORDER =
        Comparator.comparingLong((Candidate candidate) ->
            candidate.spineIndex)
            .thenComparingLong(candidate -> candidate.byteOffset)
            .reversed()
            .thenComparing(candidate -> candidate.deviceId);

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private State current;
    private LoadStatus loadStatus = LoadStatus.MISSING;
    private boolean loadAttempted;
    private boolean mutationsBlocked;
    private boolean failNextPublishForTesting;
    private String lastError = "";
    private byte[] lastFuturePortableBytes;

    OctavoReadingPositionStore(Context context) {
        this(requireFilesDirectory(context), new SecureRandom(), null);
    }

    OctavoReadingPositionStore(File filesDirectory) {
        this(filesDirectory, new SecureRandom(), null);
    }

    OctavoReadingPositionStore(File filesDirectory,
                               String deviceIdForTesting) {
        this(filesDirectory, new SecureRandom(), deviceIdForTesting);
    }

    private OctavoReadingPositionStore(File filesDirectory,
                                       SecureRandom random,
                                       String deviceId) {
        if (filesDirectory == null || random == null
            || (deviceId != null
                && !OctavoReadingPositionPortable.validDeviceId(deviceId))) {
            throw new IllegalArgumentException("Invalid position store");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
        current = new State(
            deviceId == null ? randomDeviceId(random) : deviceId,
            Collections.emptyMap());
    }

    synchronized LoadStatus load() {
        loadAttempted = true;
        mutationsBlocked = false;
        lastFuturePortableBytes = null;
        if (!stateFile.exists()) {
            current = new State(current.deviceId, Collections.emptyMap());
            loadStatus = LoadStatus.MISSING;
            lastError = "";
            deleteTemporaryBestEffort();
            return loadStatus;
        }
        try {
            byte[] bytes = readBounded(stateFile);
            if (isFutureStore(bytes)) {
                mutationsBlocked = true;
                loadStatus = LoadStatus.FUTURE_VERSION_BLOCKED;
                lastError =
                    "Reading positions were written by a newer version.";
                return loadStatus;
            }
            current = decodeState(bytes);
            loadStatus = LoadStatus.LOADED;
            lastError = "";
            deleteTemporaryBestEffort();
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState()) {
                current = new State(
                    current.deviceId, Collections.emptyMap());
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                lastError =
                    "Corrupt reading-position state was quarantined.";
            } else {
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "Reading-position state is corrupt and could not be quarantined.";
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

    synchronized MutationResult beginBookReview(String bookDigest,
                                                boolean explicitOpen) {
        MutationResult ready = requireMutableDigest(bookDigest);
        if (ready != null) {
            return ready;
        }
        BookState existing = current.books.get(bookDigest);
        if (existing == null) {
            if (!explicitOpen) {
                return unchanged();
            }
            if (current.books.size() >= MAX_BOOKS) {
                return fail(MutationResult.LIMIT,
                            "The reading-position book limit was reached.");
            }
            BookState created = new BookState(
                bookDigest, 1,
                Collections.emptyMap());
            return publish(current.withBook(created));
        }
        if (!explicitOpen) {
            return unchanged();
        }
        if (existing.reviewEpoch == Long.MAX_VALUE) {
            return fail(MutationResult.EXHAUSTED,
                        "The reading-position review counter is exhausted.");
        }
        return publish(current.withBook(
            existing.withEpoch(existing.reviewEpoch + 1)));
    }

    synchronized OctavoReadingPositionPortable.Lane localLane(
        String bookDigest) {
        BookState book = current.books.get(bookDigest);
        LaneState local = book == null
            ? null : book.lanes.get(current.deviceId);
        return local == null ? null : local.lane;
    }

    synchronized long reviewEpoch(String bookDigest) {
        BookState book = current.books.get(bookDigest);
        return book == null ? 0 : book.reviewEpoch;
    }

    synchronized List<Candidate> reviewCandidates(String bookDigest,
                                                  long presentedSpineIndex,
                                                  long presentedByteOffset) {
        if (!loadAttempted || mutationsBlocked
            || !OctavoReadingPositionPortable.validBookDigest(bookDigest)
            || !OctavoReadingPositionPortable.validAnchor(
                presentedSpineIndex, presentedByteOffset)) {
            return Collections.emptyList();
        }
        BookState book = current.books.get(bookDigest);
        if (book == null || book.reviewEpoch <= 0) {
            return Collections.emptyList();
        }
        LaneState local = book.lanes.get(current.deviceId);
        if (local == null
            || local.lane.spineIndex != presentedSpineIndex
            || local.lane.byteOffset != presentedByteOffset) {
            return Collections.emptyList();
        }
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (LaneState lane : book.lanes.values()) {
            if (lane.lane.deviceId.equals(current.deviceId)) {
                continue;
            }
            boolean pending = lane.decision == Decision.GO_PENDING;
            boolean undecided = lane.decision == Decision.NONE;
            boolean dismissedBefore =
                lane.decision == Decision.DISMISSED_AT_EPOCH
                && lane.decisionEpoch < book.reviewEpoch;
            if (!(pending || undecided || dismissedBefore)) {
                continue;
            }
            if (!pending
                && compareAnchor(lane.lane.spineIndex,
                                 lane.lane.byteOffset,
                                 presentedSpineIndex,
                                 presentedByteOffset) <= 0) {
                continue;
            }
            candidates.add(new Candidate(
                book.digest, lane, book.reviewEpoch, local));
        }
        candidates.sort(CANDIDATE_ORDER);
        return Collections.unmodifiableList(candidates);
    }

    synchronized Candidate pendingGo(String bookDigest) {
        BookState book = current.books.get(bookDigest);
        if (book == null) {
            return null;
        }
        LaneState local = book.lanes.get(current.deviceId);
        for (LaneState lane : book.lanes.values()) {
            if (lane.decision == Decision.GO_PENDING) {
                return new Candidate(
                    book.digest, lane, book.reviewEpoch, local);
            }
        }
        return null;
    }

    synchronized PortableMergeResult mergeSimulatedRemoteBytes(
        byte[] bytes) {
        if (!loadAttempted) {
            lastError = "Reading-position state has not been loaded.";
            return PortableMergeResult.BLOCKED;
        }
        if (mutationsBlocked) {
            lastError = "Reading-position state is blocked.";
            return PortableMergeResult.BLOCKED;
        }
        if (bytes == null) {
            lastFuturePortableBytes = null;
            lastError = "";
            return PortableMergeResult.UNCHANGED;
        }
        OctavoReadingPositionPortable.DecodeResult decoded =
            OctavoReadingPositionPortable.decode(bytes);
        if (decoded.status
            == OctavoReadingPositionPortable.DecodeStatus.FUTURE_VERSION) {
            lastFuturePortableBytes = decoded.preservedBytes();
            lastError =
                "The simulated position uses a newer portable version.";
            return PortableMergeResult.FUTURE_VERSION;
        }
        lastFuturePortableBytes = null;
        if (decoded.status
            == OctavoReadingPositionPortable.DecodeStatus.LIMIT) {
            lastError = "The simulated position exceeds its byte limit.";
            return PortableMergeResult.LIMIT;
        }
        if (decoded.status
            != OctavoReadingPositionPortable.DecodeStatus.READY) {
            lastError = "The simulated position is invalid.";
            return PortableMergeResult.INVALID;
        }
        OctavoReadingPositionPortable.Snapshot remote = decoded.snapshot();
        if (remote.laneCount() == 0) {
            lastError = "";
            return PortableMergeResult.UNCHANGED;
        }
        BookState existing = current.books.get(remote.bookDigest);
        if (existing == null && current.books.size() >= MAX_BOOKS) {
            lastError = "The reading-position book limit was reached.";
            return PortableMergeResult.LIMIT;
        }
        LaneState own = existing == null
            ? null : existing.lanes.get(current.deviceId);
        OctavoReadingPositionPortable.Lane incomingOwn =
            remote.lane(current.deviceId);
        if (incomingOwn != null && own != null) {
            if (incomingOwn.sequence > own.lane.sequence) {
                lastError =
                    "Remote bytes cannot advance this device's position lane.";
                return PortableMergeResult.OWN_LANE_ADVANCE;
            }
            if (incomingOwn.sequence == own.lane.sequence
                && !incomingOwn.samePosition(own.lane)) {
                lastError = "The simulated device lane is equivocal.";
                return PortableMergeResult.EQUIVOCATION;
            }
        } else if (incomingOwn != null) {
            lastError =
                "Remote bytes cannot create this device's position lane.";
            return PortableMergeResult.OWN_LANE_ADVANCE;
        }

        TreeMap<String, LaneState> lanes = existing == null
            ? new TreeMap<>() : new TreeMap<>(existing.lanes);
        boolean changed = false;
        for (OctavoReadingPositionPortable.Lane incoming
                 : remote.lanes()) {
            LaneState retained = lanes.get(incoming.deviceId);
            if (retained == null) {
                if (lanes.size()
                    >= OctavoReadingPositionPortable.maximumLaneCount()) {
                    lastError =
                        "The reading-position device limit was reached.";
                    return PortableMergeResult.LIMIT;
                }
                lanes.put(incoming.deviceId,
                          LaneState.undecided(incoming));
                changed = true;
            } else if (incoming.sequence > retained.lane.sequence) {
                lanes.put(incoming.deviceId,
                          LaneState.undecided(incoming));
                changed = true;
            } else if (incoming.sequence == retained.lane.sequence
                       && !incoming.samePosition(retained.lane)) {
                lastError = "The simulated device lane is equivocal.";
                return PortableMergeResult.EQUIVOCATION;
            }
        }
        if (!changed) {
            lastError = "";
            return PortableMergeResult.UNCHANGED;
        }
        BookState joined = new BookState(
            remote.bookDigest,
            existing == null ? 0 : existing.reviewEpoch,
            lanes);
        MutationResult result = publish(current.withBook(joined));
        if (result == MutationResult.UPDATED) {
            return PortableMergeResult.MERGED;
        }
        return result == MutationResult.LIMIT
            ? PortableMergeResult.LIMIT
            : result == MutationResult.BLOCKED
                ? PortableMergeResult.BLOCKED
                : PortableMergeResult.PUBLISH_FAILED;
    }

    synchronized PortableExport exportPortable(String bookDigest) {
        if (!loadAttempted) {
            return new PortableExport(
                PortableExportStatus.NOT_LOADED, null);
        }
        if (mutationsBlocked) {
            return new PortableExport(
                PortableExportStatus.BLOCKED, null);
        }
        if (!OctavoReadingPositionPortable.validBookDigest(bookDigest)) {
            return new PortableExport(
                PortableExportStatus.INVALID, null);
        }
        BookState book = current.books.get(bookDigest);
        ArrayList<OctavoReadingPositionPortable.Lane> lanes =
            new ArrayList<>();
        if (book != null) {
            for (LaneState lane : book.lanes.values()) {
                lanes.add(lane.lane);
            }
        }
        try {
            return new PortableExport(
                PortableExportStatus.EXPORTED,
                OctavoReadingPositionPortable.encode(
                    new OctavoReadingPositionPortable.Snapshot(
                        bookDigest, lanes)));
        } catch (IOException | RuntimeException exception) {
            lastError = "Reading positions could not be encoded.";
            return new PortableExport(
                PortableExportStatus.LOCAL_FAILURE, null);
        }
    }

    synchronized MutationResult recordSuccessfullyPresented(
        String bookDigest,
        long spineIndex,
        long byteOffset,
        long pageSpineIndex,
        long pageFirstByte,
        long pageOnePastLastByte,
        boolean presentationSucceeded) {
        return recordSuccessfullyPresented(
            bookDigest, spineIndex, byteOffset,
            pageSpineIndex, pageFirstByte, pageOnePastLastByte,
            presentationSucceeded, null);
    }

    synchronized MutationResult recordSuccessfullyPresented(
        String bookDigest,
        long spineIndex,
        long byteOffset,
        long pageSpineIndex,
        long pageFirstByte,
        long pageOnePastLastByte,
        boolean presentationSucceeded,
        Candidate candidateToDismiss) {
        MutationResult ready = requireMutableDigest(bookDigest);
        if (ready != null) {
            return ready;
        }
        if (!presentationSucceeded
            || !validPresentedPage(
                spineIndex, byteOffset,
                pageSpineIndex, pageFirstByte,
                pageOnePastLastByte)) {
            return fail(MutationResult.INVALID,
                        "Only a successfully presented position can be saved.");
        }
        BookState existing = current.books.get(bookDigest);
        if (existing == null && current.books.size() >= MAX_BOOKS) {
            return fail(MutationResult.LIMIT,
                        "The reading-position book limit was reached.");
        }
        if (candidateToDismiss != null) {
            if (existing == null
                || !candidateMatches(existing, candidateToDismiss)
                || candidateToDismiss.reviewEpoch != existing.reviewEpoch
                || existing.reviewEpoch <= 0) {
                return fail(MutationResult.CONFLICT,
                            "The position confirmation is stale.");
            }
            LaneState currentRemote =
                existing.lanes.get(candidateToDismiss.deviceId);
            if (currentRemote.decision == Decision.GO_PENDING) {
                return fail(MutationResult.CONFLICT,
                            "The pending move requires an exact completion or rollback.");
            }
            if (currentRemote.decision == Decision.ACCEPTED
                || currentRemote.decision == Decision.STAYED) {
                return fail(MutationResult.CONFLICT,
                            "The position confirmation was already decided.");
            }
        }
        TreeMap<String, LaneState> lanes = existing == null
            ? new TreeMap<>() : new TreeMap<>(existing.lanes);
        LaneState local = lanes.get(current.deviceId);
        boolean localChanged = local == null
            || local.lane.spineIndex != spineIndex
            || local.lane.byteOffset != byteOffset;
        if (localChanged) {
            if (local == null
                && lanes.size()
                   >= OctavoReadingPositionPortable.maximumLaneCount()) {
                return fail(MutationResult.LIMIT,
                            "The reading-position device limit was reached.");
            }
            if (local != null && local.lane.sequence == Long.MAX_VALUE) {
                return fail(MutationResult.EXHAUSTED,
                            "This device's position sequence is exhausted.");
            }
            long nextSequence = local == null
                ? 1 : local.lane.sequence + 1;
            lanes.put(current.deviceId, LaneState.undecided(
                new OctavoReadingPositionPortable.Lane(
                    current.deviceId, nextSequence,
                    spineIndex, byteOffset)));
        }

        boolean decisionChanged = false;
        if (candidateToDismiss != null) {
            BookState decisionBook = existing;
            LaneState remote = lanes.get(candidateToDismiss.deviceId);
            if (spineIndex != candidateToDismiss.originSpineIndex
                || byteOffset != candidateToDismiss.originByteOffset) {
                lanes.put(remote.lane.deviceId,
                          remote.withDecision(
                              Decision.DISMISSED_AT_EPOCH,
                              decisionBook.reviewEpoch, 0, 0, 0));
                decisionChanged = true;
            }
        }
        if (!localChanged && !decisionChanged) {
            return unchanged();
        }
        BookState next = new BookState(
            bookDigest,
            existing == null ? 0 : existing.reviewEpoch,
            lanes);
        return publish(current.withBook(next));
    }

    synchronized MutationResult markGoPending(Candidate candidate,
                                              long originSequence,
                                              long originSpineIndex,
                                              long originByteOffset) {
        if (candidate == null) {
            return fail(MutationResult.INVALID,
                        "No position candidate was selected.");
        }
        MutationResult ready = requireMutableDigest(candidate.bookDigest);
        if (ready != null) {
            return ready;
        }
        BookState book = current.books.get(candidate.bookDigest);
        if (book == null || book.reviewEpoch <= 0
            || candidate.reviewEpoch != book.reviewEpoch
            || !candidateMatches(book, candidate)
            || !OctavoReadingPositionPortable.validAnchor(
                originSpineIndex, originByteOffset)) {
            return fail(MutationResult.CONFLICT,
                        "The position confirmation is stale.");
        }
        LaneState local = book.lanes.get(current.deviceId);
        if (local == null
            || local.lane.sequence != originSequence
            || local.lane.spineIndex != originSpineIndex
            || local.lane.byteOffset != originByteOffset) {
            return fail(MutationResult.CONFLICT,
                        "The current reading position changed.");
        }
        for (LaneState lane : book.lanes.values()) {
            if (lane.decision == Decision.GO_PENDING
                && !lane.lane.deviceId.equals(candidate.deviceId)) {
                return fail(MutationResult.CONFLICT,
                            "Another position move is already pending.");
            }
        }
        LaneState remote = book.lanes.get(candidate.deviceId);
        if (remote.decision == Decision.ACCEPTED
            || remote.decision == Decision.STAYED
            || (remote.decision == Decision.DISMISSED_AT_EPOCH
                && remote.decisionEpoch == book.reviewEpoch)) {
            return fail(MutationResult.CONFLICT,
                        "The position confirmation was already decided.");
        }
        if (remote.decision == Decision.GO_PENDING
            && remote.originSequence == originSequence
            && remote.originSpineIndex == originSpineIndex
            && remote.originByteOffset == originByteOffset) {
            return unchanged();
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(book.lanes);
        lanes.put(remote.lane.deviceId,
                  remote.withDecision(
                      Decision.GO_PENDING, 0,
                      originSequence,
                      originSpineIndex,
                      originByteOffset));
        return publish(current.withBook(book.withLanes(lanes)));
    }

    synchronized MutationResult completeGo(
        Candidate candidate,
        long qualifiedTargetSpineIndex,
        long qualifiedTargetByteOffset,
        long actualPresentedSpineIndex,
        long actualPresentedByteOffset,
        long pageSpineIndex,
        long pageFirstByte,
        long pageOnePastLastByte,
        boolean presentationSucceeded) {
        if (candidate == null || !presentationSucceeded
            || qualifiedTargetSpineIndex != candidate.spineIndex
            || qualifiedTargetByteOffset != candidate.byteOffset
            || actualPresentedSpineIndex != qualifiedTargetSpineIndex
            || actualPresentedByteOffset != qualifiedTargetByteOffset
            || !validPresentedPage(
                actualPresentedSpineIndex,
                actualPresentedByteOffset,
                pageSpineIndex,
                pageFirstByte,
                pageOnePastLastByte)
            || qualifiedTargetSpineIndex != pageSpineIndex
            || qualifiedTargetByteOffset < pageFirstByte
            || qualifiedTargetByteOffset >= pageOnePastLastByte) {
            return fail(MutationResult.INVALID,
                        "The requested position was not successfully presented.");
        }
        MutationResult ready = requireMutableDigest(candidate.bookDigest);
        if (ready != null) {
            return ready;
        }
        BookState book = current.books.get(candidate.bookDigest);
        if (book == null || !candidateMatches(book, candidate)) {
            return fail(MutationResult.CONFLICT,
                        "The position confirmation is stale.");
        }
        LaneState remote = book.lanes.get(candidate.deviceId);
        LaneState local = book.lanes.get(current.deviceId);
        if (remote.decision != Decision.GO_PENDING || local == null
            || local.lane.sequence != remote.originSequence
            || local.lane.spineIndex != remote.originSpineIndex
            || local.lane.byteOffset != remote.originByteOffset) {
            return fail(MutationResult.CONFLICT,
                        "The pending position move no longer matches.");
        }
        boolean localChanged =
            local.lane.spineIndex != actualPresentedSpineIndex
            || local.lane.byteOffset != actualPresentedByteOffset;
        if (localChanged && local.lane.sequence == Long.MAX_VALUE) {
            return fail(MutationResult.EXHAUSTED,
                        "This device's position sequence is exhausted.");
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(book.lanes);
        if (localChanged) {
            lanes.put(current.deviceId, LaneState.undecided(
                new OctavoReadingPositionPortable.Lane(
                    current.deviceId,
                    local.lane.sequence + 1,
                    actualPresentedSpineIndex,
                    actualPresentedByteOffset)));
        }
        lanes.put(remote.lane.deviceId,
                  remote.withDecision(
                      Decision.ACCEPTED, 0, 0, 0, 0));
        return publish(current.withBook(book.withLanes(lanes)));
    }

    synchronized MutationResult stay(Candidate candidate) {
        return decide(candidate, Decision.STAYED);
    }

    synchronized MutationResult dismiss(Candidate candidate) {
        return decide(candidate, Decision.DISMISSED_AT_EPOCH);
    }

    synchronized MutationResult dismissPendingAfterRollback(
        Candidate candidate,
        long actualPresentedSpineIndex,
        long actualPresentedByteOffset,
        long pageSpineIndex,
        long pageFirstByte,
        long pageOnePastLastByte,
        boolean presentationSucceeded) {
        if (candidate == null || candidate.decision != Decision.GO_PENDING
            || !presentationSucceeded
            || actualPresentedSpineIndex != candidate.originSpineIndex
            || actualPresentedByteOffset != candidate.originByteOffset
            || !validPresentedPage(
                actualPresentedSpineIndex,
                actualPresentedByteOffset,
                pageSpineIndex,
                pageFirstByte,
                pageOnePastLastByte)) {
            return fail(MutationResult.INVALID,
                        "The pending move was not rolled back on screen.");
        }
        MutationResult ready = requireMutableDigest(candidate.bookDigest);
        if (ready != null) {
            return ready;
        }
        BookState book = current.books.get(candidate.bookDigest);
        if (book == null || book.reviewEpoch <= 0
            || candidate.reviewEpoch != book.reviewEpoch
            || !candidateMatches(book, candidate)) {
            return fail(MutationResult.CONFLICT,
                        "The position confirmation is stale.");
        }
        LaneState remote = book.lanes.get(candidate.deviceId);
        LaneState local = book.lanes.get(current.deviceId);
        if (remote.decision != Decision.GO_PENDING || local == null
            || remote.originSequence != candidate.originSequence
            || remote.originSpineIndex != candidate.originSpineIndex
            || remote.originByteOffset != candidate.originByteOffset
            || local.lane.sequence != remote.originSequence
            || local.lane.spineIndex != actualPresentedSpineIndex
            || local.lane.byteOffset != actualPresentedByteOffset) {
            return fail(MutationResult.CONFLICT,
                        "The pending position move no longer matches.");
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(book.lanes);
        lanes.put(remote.lane.deviceId,
                  remote.withDecision(
                      Decision.DISMISSED_AT_EPOCH,
                      book.reviewEpoch, 0, 0, 0));
        return publish(current.withBook(book.withLanes(lanes)));
    }

    synchronized byte[] lastFuturePortableBytes() {
        return lastFuturePortableBytes == null
            ? null : lastFuturePortableBytes.clone();
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    synchronized byte[] canonicalBytesForTesting() throws IOException {
        return encodeState(current);
    }

    synchronized int bookCountForTesting() {
        return current.books.size();
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

    static int maximumBookCountForTesting() {
        return MAX_BOOKS;
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
        OctavoReadingPositionStore store =
            new OctavoReadingPositionStore(context);
        deleteOwnedForTesting(store.temporaryFile);
        deleteOwnedForTesting(store.stateFile);
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            deleteOwnedForTesting(store.quarantineFileForTesting(index));
        }
        String[] remaining = store.rootDirectory.list();
        if (remaining != null && remaining.length == 0
            && !store.rootDirectory.delete()
            && store.rootDirectory.exists()) {
            throw new IllegalStateException(
                "Unable to clear reading-position directory");
        }
    }

    private MutationResult decide(Candidate candidate,
                                  Decision decision) {
        if (candidate == null
            || (decision != Decision.STAYED
                && decision != Decision.DISMISSED_AT_EPOCH)) {
            return fail(MutationResult.INVALID,
                        "No position candidate was selected.");
        }
        MutationResult ready = requireMutableDigest(candidate.bookDigest);
        if (ready != null) {
            return ready;
        }
        BookState book = current.books.get(candidate.bookDigest);
        if (book == null || book.reviewEpoch <= 0
            || candidate.reviewEpoch != book.reviewEpoch
            || !candidateMatches(book, candidate)) {
            return fail(MutationResult.CONFLICT,
                        "The position confirmation is stale.");
        }
        LaneState lane = book.lanes.get(candidate.deviceId);
        if (lane.decision == Decision.GO_PENDING) {
            return fail(MutationResult.CONFLICT,
                        "The pending move must be rolled back before dismissal.");
        }
        if (lane.decision == Decision.ACCEPTED
            || lane.decision == Decision.STAYED) {
            if (lane.decision == decision) {
                return unchanged();
            }
            return fail(MutationResult.CONFLICT,
                        "The position confirmation was already decided.");
        }
        long epoch = decision == Decision.DISMISSED_AT_EPOCH
            ? book.reviewEpoch : 0;
        if (lane.decision == decision
            && lane.decisionEpoch == epoch) {
            return unchanged();
        }
        TreeMap<String, LaneState> lanes = new TreeMap<>(book.lanes);
        lanes.put(lane.lane.deviceId,
                  lane.withDecision(decision, epoch, 0, 0, 0));
        return publish(current.withBook(book.withLanes(lanes)));
    }

    private MutationResult requireMutableDigest(String bookDigest) {
        if (!loadAttempted) {
            return fail(MutationResult.BLOCKED,
                        "Reading-position state has not been loaded.");
        }
        if (mutationsBlocked) {
            return fail(MutationResult.BLOCKED,
                        "Reading-position state is blocked.");
        }
        if (!OctavoReadingPositionPortable.validBookDigest(bookDigest)) {
            return fail(MutationResult.INVALID,
                        "The book identity is invalid.");
        }
        return null;
    }

    private MutationResult publish(State candidate) {
        try {
            byte[] bytes = encodeState(candidate);
            if (bytes.length > MAX_FILE_BYTES) {
                return fail(MutationResult.LIMIT,
                            "Reading-position state exceeds its byte limit.");
            }
            requireDirectory(rootDirectory);
            if (failNextPublishForTesting) {
                failNextPublishForTesting = false;
                throw new IOException("Injected position publish failure");
            }
            try (FileOutputStream output =
                     new FileOutputStream(temporaryFile, false)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            Files.move(temporaryFile.toPath(),
                       stateFile.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
            current = candidate;
            lastError = "";
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            return fail(MutationResult.PUBLISH_FAILED,
                        "Reading positions could not be saved. Retry.");
        }
    }

    private MutationResult unchanged() {
        lastError = "";
        return MutationResult.UNCHANGED;
    }

    private MutationResult fail(MutationResult result, String message) {
        lastError = message;
        return result;
    }

    private static boolean candidateMatches(BookState book,
                                            Candidate candidate) {
        if (book == null || candidate == null
            || !book.digest.equals(candidate.bookDigest)) {
            return false;
        }
        LaneState lane = book.lanes.get(candidate.deviceId);
        return lane != null
            && lane.lane.sequence == candidate.sequence
            && lane.lane.spineIndex == candidate.spineIndex
            && lane.lane.byteOffset == candidate.byteOffset;
    }

    private static int compareAnchor(long leftSpine,
                                     long leftByte,
                                     long rightSpine,
                                     long rightByte) {
        int spine = Long.compare(leftSpine, rightSpine);
        return spine != 0 ? spine : Long.compare(leftByte, rightByte);
    }

    private static boolean validPresentedPage(
        long actualSpineIndex,
        long actualByteOffset,
        long pageSpineIndex,
        long pageFirstByte,
        long pageOnePastLastByte) {
        return OctavoReadingPositionPortable.validAnchor(
                   actualSpineIndex, actualByteOffset)
            && OctavoReadingPositionPortable.validAnchor(
                   pageSpineIndex, pageFirstByte)
            && pageOnePastLastByte > pageFirstByte
            && actualSpineIndex == pageSpineIndex
            && actualByteOffset >= pageFirstByte
            && actualByteOffset < pageOnePastLastByte;
    }

    private static byte[] encodeState(State state) throws IOException {
        if (state == null
            || !OctavoReadingPositionPortable.validDeviceId(state.deviceId)
            || state.books.size() > MAX_BOOKS) {
            throw new IOException("Invalid O1RS state");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(STORE_MAGIC);
            output.writeInt(STORE_VERSION);
            output.writeInt(STORE_HEADER_FIELD_COUNT);
            writeAscii(output, state.deviceId, DEVICE_ID_BYTES);
            output.writeInt(state.books.size());
            String previousBook = null;
            for (BookState book : state.books.values()) {
                if (!validBookState(book, state.deviceId)
                    || (previousBook != null
                        && previousBook.compareTo(book.digest) >= 0)) {
                    throw new IOException("Noncanonical O1RS book");
                }
                writeAscii(output, book.digest, BOOK_DIGEST_BYTES);
                output.writeLong(book.reviewEpoch);
                output.writeInt(book.lanes.size());
                String previousLane = null;
                for (LaneState lane : book.lanes.values()) {
                    if (!validLaneState(lane, book.reviewEpoch,
                                        state.deviceId)
                        || (previousLane != null
                            && previousLane.compareTo(
                                lane.lane.deviceId) >= 0)) {
                        throw new IOException("Noncanonical O1RS lane");
                    }
                    writeAscii(output, lane.lane.deviceId,
                               DEVICE_ID_BYTES);
                    output.writeLong(lane.lane.sequence);
                    output.writeLong(lane.lane.spineIndex);
                    output.writeLong(lane.lane.byteOffset);
                    output.writeInt(lane.decision.wireId);
                    output.writeLong(lane.decisionEpoch);
                    output.writeLong(lane.originSequence);
                    output.writeLong(lane.originSpineIndex);
                    output.writeLong(lane.originByteOffset);
                    previousLane = lane.lane.deviceId;
                }
                previousBook = book.digest;
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
            throw new IOException("O1RS state exceeds its bound");
        }
        return result;
    }

    private static State decodeState(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < minimumStateBytes()
            || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid O1RS byte length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, payloadLength);
        if (readInt(bytes, payloadLength)
            != (int)checksum.getValue()) {
            throw new IOException("Invalid O1RS checksum");
        }
        try {
            ByteArrayInputStream payload =
                new ByteArrayInputStream(bytes, 0, payloadLength);
            DataInputStream input = new DataInputStream(payload);
            if (input.readInt() != STORE_MAGIC
                || input.readInt() != STORE_VERSION
                || input.readInt() != STORE_HEADER_FIELD_COUNT) {
                throw new IOException("Invalid O1RS header");
            }
            String deviceId = readAscii(input, DEVICE_ID_BYTES, false);
            int bookCount = input.readInt();
            if (bookCount < 0 || bookCount > MAX_BOOKS) {
                throw new IOException("Invalid O1RS book count");
            }
            TreeMap<String, BookState> books = new TreeMap<>();
            String previousBook = null;
            for (int bookIndex = 0;
                 bookIndex < bookCount; ++bookIndex) {
                String digest = readAscii(
                    input, BOOK_DIGEST_BYTES, true);
                long epoch = input.readLong();
                int laneCount = input.readInt();
                if (epoch < 0 || laneCount < 0
                    || laneCount
                       > OctavoReadingPositionPortable.maximumLaneCount()
                    || (previousBook != null
                        && previousBook.compareTo(digest) >= 0)) {
                    throw new IOException("Invalid O1RS book");
                }
                TreeMap<String, LaneState> lanes = new TreeMap<>();
                String previousLane = null;
                for (int laneIndex = 0;
                     laneIndex < laneCount; ++laneIndex) {
                    String laneDevice = readAscii(
                        input, DEVICE_ID_BYTES, false);
                    OctavoReadingPositionPortable.Lane lane =
                        new OctavoReadingPositionPortable.Lane(
                            laneDevice,
                            input.readLong(),
                            input.readLong(),
                            input.readLong());
                    Decision decision =
                        Decision.fromWireId(input.readInt());
                    LaneState laneState = new LaneState(
                        lane, decision,
                        input.readLong(), input.readLong(),
                        input.readLong(), input.readLong());
                    if (decision == null
                        || !validLaneState(laneState, epoch, deviceId)
                        || (previousLane != null
                            && previousLane.compareTo(laneDevice) >= 0)) {
                        throw new IOException("Invalid O1RS lane");
                    }
                    lanes.put(laneDevice, laneState);
                    previousLane = laneDevice;
                }
                BookState book = new BookState(digest, epoch, lanes);
                if (!validBookState(book, deviceId)) {
                    throw new IOException("Invalid O1RS book state");
                }
                books.put(digest, book);
                previousBook = digest;
            }
            if (payload.available() != 0) {
                throw new IOException("Trailing O1RS payload");
            }
            return new State(deviceId, books);
        } catch (EOFException exception) {
            throw new IOException("Truncated O1RS state", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid O1RS scalar", exception);
        }
    }

    private static boolean validBookState(BookState book,
                                          String localDeviceId) {
        if (book == null
            || !OctavoReadingPositionPortable.validBookDigest(book.digest)
            || book.reviewEpoch < 0
            || book.lanes.size()
               > OctavoReadingPositionPortable.maximumLaneCount()) {
            return false;
        }
        int pendingCount = 0;
        LaneState local = book.lanes.get(localDeviceId);
        for (LaneState lane : book.lanes.values()) {
            if (!validLaneState(lane, book.reviewEpoch, localDeviceId)) {
                return false;
            }
            if (lane.decision == Decision.GO_PENDING) {
                pendingCount += 1;
                if (local == null
                    || local.lane.sequence != lane.originSequence
                    || local.lane.spineIndex != lane.originSpineIndex
                    || local.lane.byteOffset != lane.originByteOffset) {
                    return false;
                }
            }
        }
        return pendingCount <= 1;
    }

    private static boolean validLaneState(LaneState lane,
                                          long reviewEpoch,
                                          String localDeviceId) {
        if (lane == null || lane.lane == null || lane.decision == null
            || !OctavoReadingPositionPortable.validDeviceId(
                lane.lane.deviceId)
            || lane.lane.sequence <= 0
            || !OctavoReadingPositionPortable.validAnchor(
                lane.lane.spineIndex, lane.lane.byteOffset)) {
            return false;
        }
        if (lane.lane.deviceId.equals(localDeviceId)) {
            return lane.decision == Decision.NONE
                && zeroDecisionPayload(lane);
        }
        if (lane.decision == Decision.NONE
            || lane.decision == Decision.ACCEPTED
            || lane.decision == Decision.STAYED) {
            return zeroDecisionPayload(lane);
        }
        if (lane.decision == Decision.DISMISSED_AT_EPOCH) {
            return lane.decisionEpoch > 0
                && lane.decisionEpoch <= reviewEpoch
                && lane.originSequence == 0
                && lane.originSpineIndex == 0
                && lane.originByteOffset == 0;
        }
        return lane.decision == Decision.GO_PENDING
            && lane.decisionEpoch == 0
            && lane.originSequence > 0
            && OctavoReadingPositionPortable.validAnchor(
                lane.originSpineIndex, lane.originByteOffset);
    }

    private static boolean zeroDecisionPayload(LaneState lane) {
        return lane.decisionEpoch == 0
            && lane.originSequence == 0
            && lane.originSpineIndex == 0
            && lane.originByteOffset == 0;
    }

    private static boolean isFutureStore(byte[] bytes) {
        return bytes != null && bytes.length >= 2 * Integer.BYTES
            && readInt(bytes, 0) == STORE_MAGIC
            && readInt(bytes, Integer.BYTES) > STORE_VERSION;
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
            throw new IOException("Invalid O1RS file length");
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
                throw new IOException("O1RS file changed while reading");
            }
        }
        return result;
    }

    private static void writeAscii(DataOutputStream output,
                                   String value,
                                   int expectedLength)
        throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != expectedLength) {
            throw new IOException("Invalid O1RS text length");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readAscii(DataInputStream input,
                                    int expectedLength,
                                    boolean digest)
        throws IOException {
        int length = input.readInt();
        if (length != expectedLength) {
            throw new IOException("Invalid O1RS text length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String result = new String(bytes, StandardCharsets.US_ASCII);
        boolean valid = digest
            ? OctavoReadingPositionPortable.validBookDigest(result)
            : OctavoReadingPositionPortable.validDeviceId(result);
        if (!valid) {
            throw new IOException("Invalid O1RS hexadecimal text");
        }
        return result;
    }

    private static int minimumStateBytes() {
        return 3 * Integer.BYTES
            + Integer.BYTES + DEVICE_ID_BYTES
            + Integer.BYTES
            + Integer.BYTES;
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
                "Unable to create reading-position directory");
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
                "Unable to clear reading-position test state");
        }
    }
}
