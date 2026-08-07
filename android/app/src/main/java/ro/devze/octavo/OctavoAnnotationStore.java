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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Product-owned, provider-neutral Port 11 annotation state.
 *
 * Reader0 remains authoritative for every spine and UTF-8 byte anchor. This
 * class validates, merges, and atomically publishes copied anchor values; it
 * never interprets EPUB content or rendered coordinates.
 */
final class OctavoAnnotationStore {
    enum MutationResult {
        ADDED,
        REMOVED,
        UPDATED,
        FAILED,
        BLOCKED,
        LIMIT;

        boolean succeeded() {
            return this == ADDED || this == REMOVED || this == UPDATED;
        }
    }

    enum LoadStatus {
        MISSING,
        LOADED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        FUTURE_VERSION_BLOCKED
    }

    static final class Bookmark {
        final String recordId;
        final String bookDigest;
        final long spineIndex;
        final long byteOffset;
        final String label;
        final String excerpt;
        final boolean conflicted;

        Bookmark(String recordId,
                 String bookDigest,
                 long spineIndex,
                 long byteOffset,
                 String label,
                 String excerpt,
                 boolean conflicted) {
            this.recordId = recordId;
            this.bookDigest = bookDigest;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
            this.label = label;
            this.excerpt = excerpt;
            this.conflicted = conflicted;
        }
    }

    /** Immutable deep copy used by a future provider adapter and offline tests. */
    static final class PortableState {
        private final TreeMap<String, Envelope> records;

        private PortableState(TreeMap<String, Envelope> records) {
            this.records = copyRecords(records);
        }
    }

    private enum Kind {
        BOOKMARK(1),
        HIGHLIGHT(2),
        NOTE(3);

        final int wireId;

        Kind(int wireId) {
            this.wireId = wireId;
        }

        static Kind fromWireId(int wireId) {
            for (Kind kind : values()) {
                if (kind.wireId == wireId) {
                    return kind;
                }
            }
            return null;
        }
    }

    private enum Operation {
        PUT(1),
        DELETE(2);

        final int wireId;

        Operation(int wireId) {
            this.wireId = wireId;
        }

        static Operation fromWireId(int wireId) {
            for (Operation operation : values()) {
                if (operation.wireId == wireId) {
                    return operation;
                }
            }
            return null;
        }
    }

    private static final int STORE_MAGIC = 0x4F31414E; // "O1AN"
    private static final int STORE_VERSION = 1;
    private static final int HEADER_FIELD_COUNT = 3;
    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_RECORDS = 2048;
    private static final int MAX_ACTORS_PER_RECORD = 16;
    private static final int MAX_HEADS_PER_RECORD = 8;
    private static final int MAX_LABEL_BYTES = 256;
    private static final int MAX_EXCERPT_BYTES = 512;
    private static final int MAX_NOTE_BYTES = 4096;
    private static final int MAX_ATTACHED_ID_BYTES = 32;
    private static final long MAX_SPINE_INDEX = 0xffffffffL;
    private static final int HEX_ID_BYTES = 32;
    private static final int DIGEST_BYTES = 64;
    private static final int QUARANTINE_SLOTS = 3;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "annotations.v1";
    private static final String TEMPORARY_FILE = "annotations.v1.tmp";
    private static final String QUARANTINE_PREFIX =
        "annotations.corrupt.";
    private static final byte[] BOOKMARK_NAMESPACE =
        "8vo.port11.bookmark.v1\n".getBytes(StandardCharsets.US_ASCII);

    private static final Comparator<Bookmark> BOOKMARK_ORDER =
        Comparator.comparingLong((Bookmark bookmark) -> bookmark.spineIndex)
            .thenComparingLong(bookmark -> bookmark.byteOffset)
            .thenComparing(bookmark -> bookmark.recordId);

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private final SecureRandom random;
    private State current;
    private LoadStatus loadStatus = LoadStatus.MISSING;
    private boolean mutationsBlocked;
    private boolean failNextPublishForTesting;

    OctavoAnnotationStore(Context context) {
        this(requireFilesDirectory(context), new SecureRandom(), null);
    }

    OctavoAnnotationStore(File filesDirectory) {
        this(filesDirectory, new SecureRandom(), null);
    }

    OctavoAnnotationStore(File filesDirectory, String actorIdForTesting) {
        this(filesDirectory, new SecureRandom(), actorIdForTesting);
    }

    private OctavoAnnotationStore(File filesDirectory,
                                  SecureRandom random,
                                  String actorId) {
        if (filesDirectory == null || random == null
            || (actorId != null && !isHex(actorId, HEX_ID_BYTES))) {
            throw new IllegalArgumentException();
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
        this.random = random;
        current = new State(actorId == null ? randomId(random) : actorId,
                            0,
                            new TreeMap<>());
    }

    synchronized LoadStatus load() {
        mutationsBlocked = false;
        if (!stateFile.exists()) {
            current = new State(current.actorId, 0, new TreeMap<>());
            loadStatus = LoadStatus.MISSING;
            return loadStatus;
        }

        try {
            byte[] bytes = readBounded(stateFile);
            if (bytes.length < 2 * Integer.BYTES) {
                throw new IOException("Truncated annotation header");
            }
            try (DataInputStream header = new DataInputStream(
                     new ByteArrayInputStream(bytes))) {
                int magic = header.readInt();
                int version = header.readInt();
                if (magic == STORE_MAGIC && version > STORE_VERSION) {
                    mutationsBlocked = true;
                    loadStatus = LoadStatus.FUTURE_VERSION_BLOCKED;
                    return loadStatus;
                }
            }
            State decoded = decode(bytes);
            current = decoded;
            loadStatus = LoadStatus.LOADED;
            return loadStatus;
        } catch (IOException | RuntimeException exception) {
            if (quarantineCorruptState()) {
                current = new State(current.actorId, 0, new TreeMap<>());
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
            } else {
                mutationsBlocked = true;
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
            }
            return loadStatus;
        }
    }

    synchronized MutationResult toggleBookmark(String bookDigest,
                                                long spineIndex,
                                                long byteOffset,
                                                String label,
                                                String excerpt) {
        if (mutationsBlocked) {
            return MutationResult.BLOCKED;
        }
        if (!validDigest(bookDigest)
            || !validPoint(spineIndex, byteOffset)
            || !validText(label, MAX_LABEL_BYTES)
            || !validText(excerpt, MAX_EXCERPT_BYTES)) {
            return MutationResult.FAILED;
        }
        String recordId = bookmarkRecordId(
            bookDigest, spineIndex, byteOffset);
        Envelope existing = current.records.get(recordId);
        if (existing != null && visiblePut(existing) != null) {
            return mutate(existing,
                          deleteMutation(existing),
                          MutationResult.REMOVED);
        }
        if (existing == null && current.records.size() >= MAX_RECORDS) {
            return MutationResult.LIMIT;
        }
        Envelope envelope = existing == null
            ? new Envelope(recordId,
                           Kind.BOOKMARK,
                           bookDigest,
                           new TreeMap<>(),
                           new TreeMap<>())
            : existing;
        Mutation put = putMutation(envelope,
                                   spineIndex,
                                   byteOffset,
                                   byteOffset,
                                   0,
                                   0,
                                   "",
                                   label,
                                   excerpt,
                                   "");
        return mutate(envelope, put, MutationResult.ADDED);
    }

    synchronized MutationResult removeBookmark(String recordId) {
        if (mutationsBlocked) {
            return MutationResult.BLOCKED;
        }
        Envelope envelope = current.records.get(recordId);
        if (envelope == null || envelope.kind != Kind.BOOKMARK
            || visiblePut(envelope) == null) {
            return MutationResult.FAILED;
        }
        return mutate(envelope,
                      deleteMutation(envelope),
                      MutationResult.REMOVED);
    }

    synchronized boolean isBookmarked(String bookDigest,
                                      long spineIndex,
                                      long byteOffset) {
        if (!validDigest(bookDigest)
            || !validPoint(spineIndex, byteOffset)) {
            return false;
        }
        Envelope envelope = current.records.get(
            bookmarkRecordId(bookDigest, spineIndex, byteOffset));
        return envelope != null && visiblePut(envelope) != null;
    }

    synchronized List<Bookmark> bookmarks(String bookDigest) {
        if (!validDigest(bookDigest)) {
            return Collections.emptyList();
        }
        ArrayList<Bookmark> result = new ArrayList<>();
        for (Envelope envelope : current.records.values()) {
            if (envelope.kind != Kind.BOOKMARK
                || !bookDigest.equals(envelope.bookDigest)) {
                continue;
            }
            Mutation visible = visiblePut(envelope);
            if (visible == null) {
                continue;
            }
            result.add(new Bookmark(envelope.recordId,
                                    envelope.bookDigest,
                                    visible.spineIndex,
                                    visible.byteStart,
                                    visible.label,
                                    visible.excerpt,
                                    envelope.heads.size() > 1));
        }
        result.sort(BOOKMARK_ORDER);
        return Collections.unmodifiableList(result);
    }

    synchronized PortableState exportPortableState() {
        return new PortableState(current.records);
    }

    synchronized MutationResult mergePortableState(PortableState remote) {
        if (mutationsBlocked) {
            return MutationResult.BLOCKED;
        }
        if (remote == null) {
            return MutationResult.FAILED;
        }
        try {
            TreeMap<String, Envelope> merged = mergeRecords(
                current.records, remote.records);
            long mergedCounter = current.counter;
            for (Envelope envelope : merged.values()) {
                Long incorporated = envelope.frontier.get(current.actorId);
                if (incorporated != null) {
                    mergedCounter = Math.max(
                        mergedCounter, incorporated);
                }
            }
            State candidate = new State(current.actorId,
                                        mergedCounter,
                                        merged);
            if (!publish(candidate)) {
                return MutationResult.FAILED;
            }
            current = candidate;
            return MutationResult.UPDATED;
        } catch (IOException | RuntimeException exception) {
            return MutationResult.LIMIT;
        }
    }

    synchronized LoadStatus loadStatus() {
        return loadStatus;
    }

    synchronized boolean mutationsBlocked() {
        return mutationsBlocked;
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    synchronized byte[] canonicalBytesForTesting() throws IOException {
        return encode(current);
    }

    synchronized byte[] portableCanonicalBytesForTesting()
        throws IOException {
        return encode(new State("00000000000000000000000000000000",
                                0,
                                copyRecords(current.records)));
    }

    synchronized int recordCountForTesting() {
        return current.records.size();
    }

    synchronized MutationResult putBookmarkForTesting(
        String bookDigest,
        long spineIndex,
        long byteOffset,
        String label,
        String excerpt) {
        if (!validDigest(bookDigest)
            || !validPoint(spineIndex, byteOffset)
            || !validText(label, MAX_LABEL_BYTES)
            || !validText(excerpt, MAX_EXCERPT_BYTES)) {
            return MutationResult.FAILED;
        }
        String recordId = bookmarkRecordId(
            bookDigest, spineIndex, byteOffset);
        Envelope envelope = current.records.get(recordId);
        if (envelope == null) {
            if (current.records.size() >= MAX_RECORDS) {
                return MutationResult.LIMIT;
            }
            envelope = new Envelope(recordId,
                                    Kind.BOOKMARK,
                                    bookDigest,
                                    new TreeMap<>(),
                                    new TreeMap<>());
        }
        Mutation put = putMutation(envelope,
                                   spineIndex,
                                   byteOffset,
                                   byteOffset,
                                   0,
                                   0,
                                   "",
                                   label,
                                   excerpt,
                                   "");
        return mutate(envelope, put, MutationResult.UPDATED);
    }

    synchronized MutationResult putNoteForTesting(
        String recordId,
        String bookDigest,
        long spineIndex,
        long byteOffset,
        String note) {
        if (!isHex(recordId, HEX_ID_BYTES)
            || !validDigest(bookDigest)
            || !validPoint(spineIndex, byteOffset)
            || !validText(note, MAX_NOTE_BYTES)) {
            return MutationResult.FAILED;
        }
        Envelope envelope = current.records.get(recordId);
        if (envelope == null) {
            if (current.records.size() >= MAX_RECORDS) {
                return MutationResult.LIMIT;
            }
            envelope = new Envelope(recordId,
                                    Kind.NOTE,
                                    bookDigest,
                                    new TreeMap<>(),
                                    new TreeMap<>());
        } else if (envelope.kind != Kind.NOTE
                   || !bookDigest.equals(envelope.bookDigest)) {
            return MutationResult.FAILED;
        }
        Mutation put = putMutation(envelope,
                                   spineIndex,
                                   byteOffset,
                                   byteOffset,
                                   0,
                                   0,
                                   "",
                                   "",
                                   "",
                                   note);
        return mutate(envelope, put, MutationResult.UPDATED);
    }

    synchronized List<String> noteBodiesForTesting(String recordId) {
        Envelope envelope = current.records.get(recordId);
        if (envelope == null || envelope.kind != Kind.NOTE) {
            return Collections.emptyList();
        }
        ArrayList<String> result = new ArrayList<>();
        for (Mutation head : envelope.heads.values()) {
            if (head.operation == Operation.PUT) {
                result.add(head.note);
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
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

    static int maximumRecordsForTesting() {
        return MAX_RECORDS;
    }

    static int currentStoreVersionForTesting() {
        return STORE_VERSION;
    }

    static String bookmarkRecordIdForTesting(String digest,
                                             long spineIndex,
                                             long byteOffset) {
        return bookmarkRecordId(digest, spineIndex, byteOffset);
    }

    static void clearForTesting(Context context) {
        OctavoAnnotationStore store = new OctavoAnnotationStore(context);
        if (!deleteFile(store.temporaryFile)
            || !deleteFile(store.stateFile)) {
            throw new IllegalStateException(
                "Unable to clear annotation state");
        }
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            if (!deleteFile(store.quarantineFileForTesting(index))) {
                throw new IllegalStateException(
                    "Unable to clear quarantined annotation state");
            }
        }
        String[] remaining = store.rootDirectory.list();
        if (remaining != null && remaining.length == 0
            && !store.rootDirectory.delete()
            && store.rootDirectory.exists()) {
            throw new IllegalStateException(
                "Unable to clear annotation directory");
        }
    }

    private MutationResult mutate(Envelope original,
                                  Mutation mutation,
                                  MutationResult success) {
        if (mutationsBlocked || original == null || mutation == null) {
            return mutationsBlocked
                ? MutationResult.BLOCKED : MutationResult.FAILED;
        }
        try {
            TreeMap<String, Envelope> records = copyRecords(current.records);
            Envelope candidateEnvelope = copyEnvelope(original);
            candidateEnvelope.frontier.merge(
                mutation.actorId, mutation.counter, Math::max);
            candidateEnvelope.heads.clear();
            candidateEnvelope.heads.put(mutation.mutationId, mutation);
            validateEnvelope(candidateEnvelope);
            records.put(candidateEnvelope.recordId, candidateEnvelope);
            State candidate = new State(current.actorId,
                                        mutation.counter,
                                        records);
            if (!publish(candidate)) {
                return MutationResult.FAILED;
            }
            current = candidate;
            return success;
        } catch (IOException | RuntimeException exception) {
            return MutationResult.LIMIT;
        }
    }

    private Mutation putMutation(Envelope envelope,
                                 long spineIndex,
                                 long byteStart,
                                 long byteEnd,
                                 int color,
                                 int flags,
                                 String attachedId,
                                 String label,
                                 String excerpt,
                                 String note) {
        return newMutation(envelope,
                           Operation.PUT,
                           spineIndex,
                           byteStart,
                           byteEnd,
                           color,
                           flags,
                           attachedId,
                           label,
                           excerpt,
                           note);
    }

    private Mutation deleteMutation(Envelope envelope) {
        Mutation visible = visiblePut(envelope);
        long spineIndex = visible == null ? 0 : visible.spineIndex;
        long byteStart = visible == null ? 0 : visible.byteStart;
        long byteEnd = visible == null ? byteStart : visible.byteEnd;
        return newMutation(envelope,
                           Operation.DELETE,
                           spineIndex,
                           byteStart,
                           byteEnd,
                           0,
                           0,
                           "",
                           "",
                           "",
                           "");
    }

    private Mutation newMutation(Envelope envelope,
                                 Operation operation,
                                 long spineIndex,
                                 long byteStart,
                                 long byteEnd,
                                 int color,
                                 int flags,
                                 String attachedId,
                                 String label,
                                 String excerpt,
                                 String note) {
        if (current.counter == Long.MAX_VALUE) {
            throw new IllegalStateException("Actor counter exhausted");
        }
        long counter = current.counter + 1;
        TreeMap<String, Long> context = new TreeMap<>(envelope.frontier);
        Mutation unsigned = new Mutation("",
                                         current.actorId,
                                         counter,
                                         operation,
                                         context,
                                         spineIndex,
                                         byteStart,
                                         byteEnd,
                                         color,
                                         flags,
                                         attachedId,
                                         label,
                                         excerpt,
                                         note);
        String mutationId = mutationId(envelope, unsigned);
        return unsigned.withId(mutationId);
    }

    private boolean publish(State candidate) throws IOException {
        byte[] bytes = encode(candidate);
        if (failNextPublishForTesting) {
            failNextPublishForTesting = false;
            return false;
        }
        requireDirectory(rootDirectory);
        try {
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
            return true;
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            return false;
        }
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

    private static TreeMap<String, Envelope> mergeRecords(
        TreeMap<String, Envelope> left,
        TreeMap<String, Envelope> right) throws IOException {
        TreeMap<String, Envelope> result = copyRecords(left);
        for (Map.Entry<String, Envelope> entry : right.entrySet()) {
            Envelope other = entry.getValue();
            Envelope existing = result.get(entry.getKey());
            if (existing == null) {
                result.put(entry.getKey(), copyEnvelope(other));
                continue;
            }
            if (existing.kind != other.kind
                || !existing.bookDigest.equals(other.bookDigest)) {
                throw new IOException("Record identity collision");
            }
            TreeMap<String, Long> frontier =
                new TreeMap<>(existing.frontier);
            for (Map.Entry<String, Long> actor : other.frontier.entrySet()) {
                frontier.merge(actor.getKey(), actor.getValue(), Math::max);
            }
            TreeMap<String, Mutation> heads =
                new TreeMap<>(existing.heads);
            for (Mutation mutation : other.heads.values()) {
                Mutation duplicate = heads.get(mutation.mutationId);
                if (duplicate != null && !duplicate.equalsMutation(mutation)) {
                    throw new IOException("Mutation identity collision");
                }
                heads.put(mutation.mutationId, copyMutation(mutation));
            }
            ArrayList<Mutation> all = new ArrayList<>(heads.values());
            for (Mutation candidate : all) {
                for (Mutation observer : all) {
                    if (candidate == observer) {
                        continue;
                    }
                    Long observed = observer.context.get(candidate.actorId);
                    if (observed != null && observed >= candidate.counter) {
                        heads.remove(candidate.mutationId);
                        break;
                    }
                }
            }
            Envelope merged = new Envelope(existing.recordId,
                                           existing.kind,
                                           existing.bookDigest,
                                           frontier,
                                           heads);
            validateEnvelope(merged);
            result.put(merged.recordId, merged);
        }
        if (result.size() > MAX_RECORDS) {
            throw new IOException("Record capacity exceeded");
        }
        for (Envelope envelope : result.values()) {
            validateEnvelope(envelope);
        }
        return result;
    }

    private static Mutation visiblePut(Envelope envelope) {
        if (envelope == null) {
            return null;
        }
        for (Mutation head : envelope.heads.values()) {
            if (head.operation == Operation.PUT) {
                return head;
            }
        }
        return null;
    }

    private static byte[] encode(State state) throws IOException {
        validateState(state);
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payload)) {
            output.writeInt(STORE_MAGIC);
            output.writeInt(STORE_VERSION);
            output.writeInt(HEADER_FIELD_COUNT);
            writeString(output, state.actorId, HEX_ID_BYTES);
            output.writeLong(state.counter);
            output.writeInt(state.records.size());
            for (Envelope envelope : state.records.values()) {
                writeEnvelope(output, envelope);
            }
            output.flush();
        }
        byte[] withoutChecksum = payload.toByteArray();
        if (withoutChecksum.length <= 0
            || withoutChecksum.length > MAX_FILE_BYTES - Integer.BYTES) {
            throw new IOException("Annotation state exceeds its bound");
        }
        CRC32 checksum = new CRC32();
        checksum.update(withoutChecksum);
        ByteArrayOutputStream complete = new ByteArrayOutputStream(
            withoutChecksum.length + Integer.BYTES);
        complete.write(withoutChecksum);
        try (DataOutputStream output = new DataOutputStream(complete)) {
            output.writeInt((int)checksum.getValue());
            output.flush();
        }
        byte[] bytes = complete.toByteArray();
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Annotation state exceeds its bound");
        }
        return bytes;
    }

    private static State decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length <= Integer.BYTES
            || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid annotation file length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, payloadLength);
        int storedChecksum = ((bytes[payloadLength] & 0xff) << 24)
            | ((bytes[payloadLength + 1] & 0xff) << 16)
            | ((bytes[payloadLength + 2] & 0xff) << 8)
            | (bytes[payloadLength + 3] & 0xff);
        if (storedChecksum != (int)checksum.getValue()) {
            throw new IOException("Invalid annotation checksum");
        }
        try (DataInputStream input = new DataInputStream(
                 new ByteArrayInputStream(bytes, 0, payloadLength))) {
            if (input.readInt() != STORE_MAGIC
                || input.readInt() != STORE_VERSION
                || input.readInt() != HEADER_FIELD_COUNT) {
                throw new IOException("Invalid annotation header");
            }
            String actorId = readString(input, HEX_ID_BYTES, true);
            long counter = input.readLong();
            int recordCount = input.readInt();
            if (counter < 0 || recordCount < 0
                || recordCount > MAX_RECORDS) {
                throw new IOException("Invalid annotation counts");
            }
            TreeMap<String, Envelope> records = new TreeMap<>();
            String previousRecordId = null;
            for (int index = 0; index < recordCount; ++index) {
                Envelope envelope = readEnvelope(input);
                if (previousRecordId != null
                    && previousRecordId.compareTo(envelope.recordId) >= 0) {
                    throw new IOException("Noncanonical record order");
                }
                records.put(envelope.recordId, envelope);
                previousRecordId = envelope.recordId;
            }
            if (input.read() != -1) {
                throw new IOException("Trailing annotation bytes");
            }
            State state = new State(actorId, counter, records);
            validateState(state);
            return state;
        } catch (EOFException exception) {
            throw new IOException("Truncated annotation state", exception);
        }
    }

    private static void writeEnvelope(DataOutputStream output,
                                      Envelope envelope) throws IOException {
        validateEnvelope(envelope);
        writeString(output, envelope.recordId, HEX_ID_BYTES);
        output.writeByte(envelope.kind.wireId);
        writeString(output, envelope.bookDigest, DIGEST_BYTES);
        output.writeInt(envelope.frontier.size());
        for (Map.Entry<String, Long> actor : envelope.frontier.entrySet()) {
            writeString(output, actor.getKey(), HEX_ID_BYTES);
            output.writeLong(actor.getValue());
        }
        output.writeInt(envelope.heads.size());
        for (Mutation mutation : envelope.heads.values()) {
            writeMutation(output, mutation);
        }
    }

    private static Envelope readEnvelope(DataInputStream input)
        throws IOException {
        String recordId = readString(input, HEX_ID_BYTES, true);
        Kind kind = Kind.fromWireId(input.readUnsignedByte());
        String digest = readString(input, DIGEST_BYTES, true);
        int frontierCount = input.readInt();
        if (kind == null || frontierCount <= 0
            || frontierCount > MAX_ACTORS_PER_RECORD) {
            throw new IOException("Invalid annotation envelope");
        }
        TreeMap<String, Long> frontier = new TreeMap<>();
        String previousActor = null;
        for (int index = 0; index < frontierCount; ++index) {
            String actor = readString(input, HEX_ID_BYTES, true);
            long counter = input.readLong();
            if (counter <= 0 || (previousActor != null
                                 && previousActor.compareTo(actor) >= 0)) {
                throw new IOException("Invalid frontier");
            }
            frontier.put(actor, counter);
            previousActor = actor;
        }
        int headCount = input.readInt();
        if (headCount <= 0 || headCount > MAX_HEADS_PER_RECORD) {
            throw new IOException("Invalid head count");
        }
        TreeMap<String, Mutation> heads = new TreeMap<>();
        String previousHead = null;
        for (int index = 0; index < headCount; ++index) {
            Mutation mutation = readMutation(input);
            if (previousHead != null
                && previousHead.compareTo(mutation.mutationId) >= 0) {
                throw new IOException("Noncanonical head order");
            }
            heads.put(mutation.mutationId, mutation);
            previousHead = mutation.mutationId;
        }
        Envelope envelope = new Envelope(recordId,
                                         kind,
                                         digest,
                                         frontier,
                                         heads);
        validateEnvelope(envelope);
        return envelope;
    }

    private static void writeMutation(DataOutputStream output,
                                      Mutation mutation) throws IOException {
        validateMutation(mutation);
        writeString(output, mutation.mutationId, HEX_ID_BYTES);
        writeString(output, mutation.actorId, HEX_ID_BYTES);
        output.writeLong(mutation.counter);
        output.writeByte(mutation.operation.wireId);
        output.writeInt(mutation.context.size());
        for (Map.Entry<String, Long> actor : mutation.context.entrySet()) {
            writeString(output, actor.getKey(), HEX_ID_BYTES);
            output.writeLong(actor.getValue());
        }
        output.writeLong(mutation.spineIndex);
        output.writeLong(mutation.byteStart);
        output.writeLong(mutation.byteEnd);
        output.writeInt(mutation.color);
        output.writeInt(mutation.flags);
        writeString(output, mutation.attachedId, MAX_ATTACHED_ID_BYTES);
        writeString(output, mutation.label, MAX_LABEL_BYTES);
        writeString(output, mutation.excerpt, MAX_EXCERPT_BYTES);
        writeString(output, mutation.note, MAX_NOTE_BYTES);
    }

    private static Mutation readMutation(DataInputStream input)
        throws IOException {
        String mutationId = readString(input, HEX_ID_BYTES, true);
        String actorId = readString(input, HEX_ID_BYTES, true);
        long counter = input.readLong();
        Operation operation = Operation.fromWireId(input.readUnsignedByte());
        int contextCount = input.readInt();
        if (counter <= 0 || operation == null || contextCount < 0
            || contextCount > MAX_ACTORS_PER_RECORD) {
            throw new IOException("Invalid mutation header");
        }
        TreeMap<String, Long> context = new TreeMap<>();
        String previousActor = null;
        for (int index = 0; index < contextCount; ++index) {
            String actor = readString(input, HEX_ID_BYTES, true);
            long observed = input.readLong();
            if (observed <= 0 || (previousActor != null
                                 && previousActor.compareTo(actor) >= 0)) {
                throw new IOException("Invalid mutation context");
            }
            context.put(actor, observed);
            previousActor = actor;
        }
        Mutation mutation = new Mutation(
            mutationId,
            actorId,
            counter,
            operation,
            context,
            input.readLong(),
            input.readLong(),
            input.readLong(),
            input.readInt(),
            input.readInt(),
            readString(input, MAX_ATTACHED_ID_BYTES, false),
            readString(input, MAX_LABEL_BYTES, false),
            readString(input, MAX_EXCERPT_BYTES, false),
            readString(input, MAX_NOTE_BYTES, false));
        validateMutation(mutation);
        return mutation;
    }

    private static void validateState(State state) throws IOException {
        if (state == null || !isHex(state.actorId, HEX_ID_BYTES)
            || state.counter < 0 || state.records.size() > MAX_RECORDS) {
            throw new IOException("Invalid annotation state");
        }
        for (Map.Entry<String, Envelope> entry : state.records.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().recordId)) {
                throw new IOException("Mismatched record key");
            }
            validateEnvelope(entry.getValue());
            Long incorporated =
                entry.getValue().frontier.get(state.actorId);
            if (incorporated != null && incorporated > state.counter) {
                throw new IOException("Actor counter trails its frontier");
            }
        }
    }

    private static void validateEnvelope(Envelope envelope)
        throws IOException {
        if (envelope == null
            || !isHex(envelope.recordId, HEX_ID_BYTES)
            || envelope.kind == null
            || !validDigest(envelope.bookDigest)
            || envelope.frontier.isEmpty()
            || envelope.frontier.size() > MAX_ACTORS_PER_RECORD
            || envelope.heads.isEmpty()
            || envelope.heads.size() > MAX_HEADS_PER_RECORD) {
            throw new IOException("Invalid record envelope");
        }
        for (Map.Entry<String, Long> actor : envelope.frontier.entrySet()) {
            if (!isHex(actor.getKey(), HEX_ID_BYTES)
                || actor.getValue() == null || actor.getValue() <= 0) {
                throw new IOException("Invalid record frontier");
            }
        }
        for (Map.Entry<String, Mutation> entry : envelope.heads.entrySet()) {
            Mutation mutation = entry.getValue();
            validateMutation(mutation);
            Long incorporated = envelope.frontier.get(mutation.actorId);
            if (!entry.getKey().equals(mutation.mutationId)
                || incorporated == null
                || incorporated < mutation.counter) {
                throw new IOException("Unincorporated mutation head");
            }
            String expectedId = mutationId(envelope, mutation.withId(""));
            if (!expectedId.equals(mutation.mutationId)) {
                throw new IOException("Invalid mutation identity");
            }
            if (mutation.operation == Operation.PUT) {
                if (envelope.kind == Kind.BOOKMARK
                    && (mutation.byteEnd != mutation.byteStart
                        || mutation.color != 0
                        || !mutation.attachedId.isEmpty()
                        || !mutation.note.isEmpty()
                        || !envelope.recordId.equals(bookmarkRecordId(
                            envelope.bookDigest,
                            mutation.spineIndex,
                            mutation.byteStart)))) {
                    throw new IOException("Invalid bookmark payload");
                }
                if (envelope.kind == Kind.HIGHLIGHT
                    && (mutation.byteEnd <= mutation.byteStart
                        || !mutation.attachedId.isEmpty()
                        || !mutation.note.isEmpty())) {
                    throw new IOException("Invalid highlight payload");
                }
                if (envelope.kind == Kind.NOTE
                    && mutation.note.isEmpty()) {
                    throw new IOException("Invalid note payload");
                }
            } else if (!mutation.attachedId.isEmpty()
                       || !mutation.label.isEmpty()
                       || !mutation.excerpt.isEmpty()
                       || !mutation.note.isEmpty()) {
                throw new IOException("Delete mutation contains payload");
            }
        }
    }

    private static void validateMutation(Mutation mutation)
        throws IOException {
        if (mutation == null
            || (!mutation.mutationId.isEmpty()
                && !isHex(mutation.mutationId, HEX_ID_BYTES))
            || !isHex(mutation.actorId, HEX_ID_BYTES)
            || mutation.counter <= 0
            || mutation.operation == null
            || mutation.context.size() > MAX_ACTORS_PER_RECORD
            || !validPoint(mutation.spineIndex, mutation.byteStart)
            || mutation.byteEnd < mutation.byteStart
            || mutation.color < 0 || mutation.color > 3
            || mutation.flags < 0
            || !validOptionalHex(mutation.attachedId)
            || !validText(mutation.label, MAX_LABEL_BYTES)
            || !validText(mutation.excerpt, MAX_EXCERPT_BYTES)
            || !validText(mutation.note, MAX_NOTE_BYTES)) {
            throw new IOException("Invalid annotation mutation");
        }
        for (Map.Entry<String, Long> actor : mutation.context.entrySet()) {
            if (!isHex(actor.getKey(), HEX_ID_BYTES)
                || actor.getValue() == null || actor.getValue() <= 0) {
                throw new IOException("Invalid mutation context");
            }
        }
    }

    private static String mutationId(Envelope envelope,
                                     Mutation mutation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, envelope.recordId, HEX_ID_BYTES);
                output.writeByte(envelope.kind.wireId);
                writeString(output, envelope.bookDigest, DIGEST_BYTES);
                writeString(output, mutation.actorId, HEX_ID_BYTES);
                output.writeLong(mutation.counter);
                output.writeByte(mutation.operation.wireId);
                output.writeInt(mutation.context.size());
                for (Map.Entry<String, Long> actor
                         : mutation.context.entrySet()) {
                    writeString(output, actor.getKey(), HEX_ID_BYTES);
                    output.writeLong(actor.getValue());
                }
                output.writeLong(mutation.spineIndex);
                output.writeLong(mutation.byteStart);
                output.writeLong(mutation.byteEnd);
                output.writeInt(mutation.color);
                output.writeInt(mutation.flags);
                writeString(output,
                            mutation.attachedId,
                            MAX_ATTACHED_ID_BYTES);
                writeString(output, mutation.label, MAX_LABEL_BYTES);
                writeString(output, mutation.excerpt, MAX_EXCERPT_BYTES);
                writeString(output, mutation.note, MAX_NOTE_BYTES);
                output.flush();
            }
            return first128Hex(sha256(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String bookmarkRecordId(String digest,
                                           long spineIndex,
                                           long byteOffset) {
        if (!validDigest(digest)
            || !validPoint(spineIndex, byteOffset)) {
            throw new IllegalArgumentException();
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(BOOKMARK_NAMESPACE);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(digest.getBytes(StandardCharsets.US_ASCII));
                output.writeLong(spineIndex);
                output.writeLong(byteOffset);
                output.flush();
            }
            return first128Hex(sha256(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String first128Hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(HEX_ID_BYTES);
        for (int index = 0; index < 16; ++index) {
            result.append(Character.forDigit((bytes[index] >>> 4) & 0xf, 16));
            result.append(Character.forDigit(bytes[index] & 0xf, 16));
        }
        return result.toString();
    }

    private static String randomId(SecureRandom random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return first128Hex(bytes);
    }

    private static void writeString(DataOutputStream output,
                                    String value,
                                    int maximumBytes) throws IOException {
        if (!validText(value, maximumBytes)) {
            throw new IOException("String exceeds annotation bound");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input,
                                     int maximumBytes,
                                     boolean requireMaximum) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximumBytes
            || (requireMaximum && length != maximumBytes)) {
            throw new IOException("Invalid annotation string length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("Invalid UTF-8 annotation string");
        }
        return value;
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0
            || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Invalid annotation file length");
        }
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    offset += read;
                }
            }
            if (input.read() != -1) {
                throw new IOException("Annotation file grew while reading");
            }
        }
        if (offset != bytes.length) {
            throw new IOException("Annotation file changed while reading");
        }
        return bytes;
    }

    private static TreeMap<String, Envelope> copyRecords(
        TreeMap<String, Envelope> records) {
        TreeMap<String, Envelope> copy = new TreeMap<>();
        for (Map.Entry<String, Envelope> entry : records.entrySet()) {
            copy.put(entry.getKey(), copyEnvelope(entry.getValue()));
        }
        return copy;
    }

    private static Envelope copyEnvelope(Envelope envelope) {
        TreeMap<String, Mutation> heads = new TreeMap<>();
        for (Map.Entry<String, Mutation> entry : envelope.heads.entrySet()) {
            heads.put(entry.getKey(), copyMutation(entry.getValue()));
        }
        return new Envelope(envelope.recordId,
                            envelope.kind,
                            envelope.bookDigest,
                            new TreeMap<>(envelope.frontier),
                            heads);
    }

    private static Mutation copyMutation(Mutation mutation) {
        return new Mutation(mutation.mutationId,
                            mutation.actorId,
                            mutation.counter,
                            mutation.operation,
                            new TreeMap<>(mutation.context),
                            mutation.spineIndex,
                            mutation.byteStart,
                            mutation.byteEnd,
                            mutation.color,
                            mutation.flags,
                            mutation.attachedId,
                            mutation.label,
                            mutation.excerpt,
                            mutation.note);
    }

    private static boolean validPoint(long spineIndex, long byteOffset) {
        return spineIndex >= 0 && spineIndex <= MAX_SPINE_INDEX
            && byteOffset >= 0;
    }

    private static boolean validDigest(String value) {
        return isHex(value, DIGEST_BYTES);
    }

    private static boolean validOptionalHex(String value) {
        return value != null
            && (value.isEmpty() || isHex(value, HEX_ID_BYTES));
    }

    private static boolean isHex(String value, int exactBytes) {
        if (value == null || value.length() != exactBytes) {
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

    private static boolean validText(String value, int maximumBytes) {
        if (value == null) {
            return false;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= maximumBytes
            && value.equals(new String(bytes, StandardCharsets.UTF_8));
    }

    private static void requireDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(
                "Unable to create " + directory.getAbsolutePath());
        }
    }

    private static boolean deleteFile(File file) {
        return file == null || !file.exists() || file.delete();
    }

    private void deleteTemporaryBestEffort() {
        if (temporaryFile.isFile()) {
            temporaryFile.delete();
        }
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context application = context.getApplicationContext();
        return (application == null ? context : application).getFilesDir();
    }

    private static final class State {
        final String actorId;
        final long counter;
        final TreeMap<String, Envelope> records;

        State(String actorId,
              long counter,
              TreeMap<String, Envelope> records) {
            this.actorId = actorId;
            this.counter = counter;
            this.records = records;
        }
    }

    private static final class Envelope {
        final String recordId;
        final Kind kind;
        final String bookDigest;
        final TreeMap<String, Long> frontier;
        final TreeMap<String, Mutation> heads;

        Envelope(String recordId,
                 Kind kind,
                 String bookDigest,
                 TreeMap<String, Long> frontier,
                 TreeMap<String, Mutation> heads) {
            this.recordId = recordId;
            this.kind = kind;
            this.bookDigest = bookDigest;
            this.frontier = frontier;
            this.heads = heads;
        }
    }

    private static final class Mutation {
        final String mutationId;
        final String actorId;
        final long counter;
        final Operation operation;
        final TreeMap<String, Long> context;
        final long spineIndex;
        final long byteStart;
        final long byteEnd;
        final int color;
        final int flags;
        final String attachedId;
        final String label;
        final String excerpt;
        final String note;

        Mutation(String mutationId,
                 String actorId,
                 long counter,
                 Operation operation,
                 TreeMap<String, Long> context,
                 long spineIndex,
                 long byteStart,
                 long byteEnd,
                 int color,
                 int flags,
                 String attachedId,
                 String label,
                 String excerpt,
                 String note) {
            this.mutationId = mutationId;
            this.actorId = actorId;
            this.counter = counter;
            this.operation = operation;
            this.context = context;
            this.spineIndex = spineIndex;
            this.byteStart = byteStart;
            this.byteEnd = byteEnd;
            this.color = color;
            this.flags = flags;
            this.attachedId = attachedId;
            this.label = label;
            this.excerpt = excerpt;
            this.note = note;
        }

        Mutation withId(String id) {
            return new Mutation(id,
                                actorId,
                                counter,
                                operation,
                                new TreeMap<>(context),
                                spineIndex,
                                byteStart,
                                byteEnd,
                                color,
                                flags,
                                attachedId,
                                label,
                                excerpt,
                                note);
        }

        boolean equalsMutation(Mutation other) {
            return other != null
                && mutationId.equals(other.mutationId)
                && actorId.equals(other.actorId)
                && counter == other.counter
                && operation == other.operation
                && context.equals(other.context)
                && spineIndex == other.spineIndex
                && byteStart == other.byteStart
                && byteEnd == other.byteEnd
                && color == other.color
                && flags == other.flags
                && attachedId.equals(other.attachedId)
                && label.equals(other.label)
                && excerpt.equals(other.excerpt)
                && note.equals(other.note);
        }
    }
}
