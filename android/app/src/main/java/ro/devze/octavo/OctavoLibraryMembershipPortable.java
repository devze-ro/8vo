package ro.devze.octavo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;

/** Canonical, bounded O1LM causal synchronized-Library membership bytes. */
final class OctavoLibraryMembershipPortable {
    enum DecodeStatus {
        READY,
        FUTURE_VERSION,
        INVALID,
        LIMIT
    }

    enum MergeStatus {
        MERGED,
        UNCHANGED,
        EQUIVOCATION,
        LIMIT,
        INVALID
    }

    enum MutationStatus {
        MUTATED,
        UNCHANGED,
        EQUIVOCATION,
        INVALID,
        LIMIT
    }

    enum Operation {
        WITHDRAW(1),
        RESTORE(2);

        final int wireValue;

        Operation(int wireValue) {
            this.wireValue = wireValue;
        }

        static Operation fromWire(int value) {
            switch (value) {
                case 1: return WITHDRAW;
                case 2: return RESTORE;
                default: return null;
            }
        }
    }

    enum Projection {
        MEMBER,
        WITHDRAWN,
        CONFLICT
    }

    enum LimitScope {
        INPUT,
        JOIN,
        LOCAL
    }

    enum LimitReason {
        RECORD_HISTORY,
        ACTOR_HISTORY,
        CONCURRENT_HEADS,
        ENCODED_BYTES,
        COUNTER_EXHAUSTED
    }

    static final class Descriptor {
        static final int EPUB = 1;

        final String digest;
        final long byteCount;
        final int kind;

        Descriptor(String digest, long byteCount) {
            this(digest, byteCount, EPUB);
        }

        Descriptor(String digest, long byteCount, int kind) {
            if (!validDigest(digest) || byteCount <= 0
                || byteCount > MAX_DOCUMENT_BYTES || kind != EPUB) {
                throw new IllegalArgumentException(
                    "Invalid O1LM descriptor");
            }
            this.digest = digest;
            this.byteCount = byteCount;
            this.kind = kind;
        }

        boolean sameIdentity(Descriptor other) {
            return other != null && digest.equals(other.digest)
                && byteCount == other.byteCount && kind == other.kind;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Descriptor
                && sameIdentity((Descriptor)object);
        }

        @Override
        public int hashCode() {
            int result = digest.hashCode();
            result = 31 * result + Long.hashCode(byteCount);
            return 31 * result + kind;
        }
    }

    /** One live mutation head. Its context is copied and actor-sorted. */
    static final class Mutation {
        final String mutationId;
        final String actorId;
        final long counter;
        final Operation operation;
        private final Map<String, Long> context;

        Mutation(String mutationId,
                 String actorId,
                 long counter,
                 Operation operation,
                 Map<String, Long> context) {
            if (!validActorOrMutationId(mutationId)
                || !validActorOrMutationId(actorId)
                || counter <= 0 || operation == null || context == null
                || context.size() > MAX_ACTORS) {
                throw new IllegalArgumentException(
                    "Invalid O1LM mutation");
            }
            TreeMap<String, Long> copied = copyVector(context, false);
            this.mutationId = mutationId;
            this.actorId = actorId;
            this.counter = counter;
            this.operation = operation;
            this.context = Collections.unmodifiableMap(copied);
        }

        Map<String, Long> context() {
            return context;
        }

        Long contextCounter(String actorId) {
            return context.get(actorId);
        }

        int contextCount() {
            return context.size();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Mutation)) {
                return false;
            }
            Mutation other = (Mutation)object;
            return mutationId.equals(other.mutationId)
                && actorId.equals(other.actorId)
                && counter == other.counter
                && operation == other.operation
                && context.equals(other.context);
        }

        @Override
        public int hashCode() {
            int result = mutationId.hashCode();
            result = 31 * result + actorId.hashCode();
            result = 31 * result + Long.hashCode(counter);
            result = 31 * result + operation.hashCode();
            return 31 * result + context.hashCode();
        }
    }

    /** One descriptor's retained actor frontier and current live heads. */
    static final class Record {
        final Descriptor descriptor;
        private final Map<String, Long> frontier;
        private final List<Mutation> heads;

        Record(Descriptor descriptor,
               Map<String, Long> frontier,
               Collection<Mutation> heads) {
            if (descriptor == null || frontier == null || heads == null
                || frontier.isEmpty() || frontier.size() > MAX_ACTORS
                || heads.isEmpty() || heads.size() > MAX_HEADS) {
                throw new IllegalArgumentException("Invalid O1LM record");
            }
            TreeMap<String, Long> frontierCopy =
                copyVector(frontier, true);
            TreeMap<String, Mutation> headCopy = new TreeMap<>();
            for (Mutation head : heads) {
                if (head == null
                    || headCopy.put(head.mutationId, head) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate O1LM mutation head");
                }
            }
            this.descriptor = descriptor;
            this.frontier = Collections.unmodifiableMap(frontierCopy);
            this.heads = Collections.unmodifiableList(
                new ArrayList<>(headCopy.values()));
            Validation validation = validateRecord(this);
            if (!validation.valid) {
                throw new IllegalArgumentException(
                    "Causally invalid O1LM record");
            }
        }

        Descriptor descriptor() {
            return descriptor;
        }

        Map<String, Long> frontier() {
            return frontier;
        }

        List<Mutation> heads() {
            return heads;
        }

        Projection projection() {
            boolean withdrew = false;
            boolean restored = false;
            for (Mutation head : heads) {
                withdrew |= head.operation == Operation.WITHDRAW;
                restored |= head.operation == Operation.RESTORE;
            }
            if (withdrew && restored) {
                return Projection.CONFLICT;
            }
            return restored ? Projection.MEMBER : Projection.WITHDRAWN;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Record)) {
                return false;
            }
            Record other = (Record)object;
            return descriptor.equals(other.descriptor)
                && frontier.equals(other.frontier)
                && heads.equals(other.heads);
        }

        @Override
        public int hashCode() {
            int result = descriptor.hashCode();
            result = 31 * result + frontier.hashCode();
            return 31 * result + heads.hashCode();
        }
    }

    /** Complete immutable portable membership snapshot, ordered by digest. */
    static final class Snapshot {
        private final TreeMap<String, Record> records;

        Snapshot(Collection<Record> source) {
            if (source == null || source.size() > MAX_RECORDS) {
                throw new IllegalArgumentException(
                    "Invalid O1LM snapshot");
            }
            TreeMap<String, Record> copied = new TreeMap<>();
            for (Record record : source) {
                if (record == null
                    || copied.put(record.descriptor.digest, record) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate O1LM descriptor");
                }
            }
            this.records = copied;
            Validation validation = validateSnapshot(this);
            if (!validation.valid) {
                throw new IllegalArgumentException(
                    "Invalid O1LM snapshot history");
            }
        }

        static Snapshot empty() {
            return new Snapshot(Collections.<Record>emptyList());
        }

        List<Record> records() {
            return Collections.unmodifiableList(
                new ArrayList<>(records.values()));
        }

        Record record(String digest) {
            return records.get(digest);
        }

        int count() {
            return records.size();
        }

        int recordCount() {
            return count();
        }

        Projection projection(String digest) {
            Record record = records.get(digest);
            return record == null ? null : record.projection();
        }

        boolean actorAppears(String actorId) {
            if (actorId == null) {
                return false;
            }
            for (Record record : records.values()) {
                if (record.frontier.containsKey(actorId)) {
                    return true;
                }
            }
            return false;
        }

        long maximumActorCounter(String actorId) {
            long maximum = 0;
            if (actorId == null) {
                return maximum;
            }
            for (Record record : records.values()) {
                Long value = record.frontier.get(actorId);
                if (value != null && value > maximum) {
                    maximum = value;
                }
            }
            return maximum;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Snapshot
                && records.equals(((Snapshot)object).records);
        }

        @Override
        public int hashCode() {
            return records.hashCode();
        }
    }

    static final class DecodeResult {
        final DecodeStatus status;
        final LimitScope limitScope;
        final LimitReason limitReason;
        private final Snapshot snapshot;
        private final byte[] preservedBytes;

        private DecodeResult(DecodeStatus status,
                             Snapshot snapshot,
                             byte[] preservedBytes,
                             LimitScope limitScope,
                             LimitReason limitReason) {
            this.status = status;
            this.snapshot = snapshot;
            this.preservedBytes = preservedBytes == null
                ? null : preservedBytes.clone();
            this.limitScope = limitScope;
            this.limitReason = limitReason;
        }

        Snapshot snapshot() {
            return snapshot;
        }

        byte[] preservedBytes() {
            return preservedBytes == null ? null : preservedBytes.clone();
        }
    }

    static final class MergeResult {
        final MergeStatus status;
        final Snapshot snapshot;
        final LimitScope limitScope;
        final LimitReason limitReason;

        private MergeResult(MergeStatus status,
                            Snapshot snapshot,
                            LimitScope limitScope,
                            LimitReason limitReason) {
            this.status = status;
            this.snapshot = snapshot;
            this.limitScope = limitScope;
            this.limitReason = limitReason;
        }

        Snapshot snapshot() {
            return snapshot;
        }
    }

    static final class MutationResult {
        final MutationStatus status;
        final Snapshot snapshot;
        final LimitScope limitScope;
        final LimitReason limitReason;

        private MutationResult(MutationStatus status,
                               Snapshot snapshot,
                               LimitScope limitScope,
                               LimitReason limitReason) {
            this.status = status;
            this.snapshot = snapshot;
            this.limitScope = limitScope;
            this.limitReason = limitReason;
        }

        Snapshot snapshot() {
            return snapshot;
        }
    }

    private static final int MAGIC = 0x4F314C4D; // "O1LM"
    private static final int VERSION = 1;
    private static final int HEADER_FIELD_COUNT = 1;
    private static final int MAX_RECORDS = 63;
    private static final int MAX_ACTORS = 16;
    private static final int MAX_HEADS = 8;
    private static final int DIGEST_BYTES = 64;
    private static final int ID_BYTES = 32;
    private static final int MINIMUM_V1_BYTES = 20;
    private static final int MINIMUM_RECORD_BYTES = 217;
    private static final int FRONTIER_ENTRY_BYTES = 44;
    private static final int MINIMUM_HEAD_BYTES = 85;
    private static final int CONTEXT_ENTRY_BYTES = 44;
    private static final int MAXIMUM_RECORD_BYTES = 7_104;
    private static final int MAXIMUM_V1_BYTES = 447_572;
    private static final int MAXIMUM_FUTURE_BYTES = 524_244;
    private static final long MAX_DOCUMENT_BYTES = 536_870_912L;
    private static final byte[] MUTATION_NAMESPACE =
        "8vo.port11.library-membership.mutation.v1\n"
            .getBytes(StandardCharsets.US_ASCII);

    private OctavoLibraryMembershipPortable() {
    }

    static byte[] encode(Snapshot snapshot) throws IOException {
        Validation validation = validateSnapshot(snapshot);
        if (!validation.valid) {
            throw new IOException("Invalid O1LM snapshot");
        }
        long expectedLength = encodedLength(snapshot);
        if (expectedLength > MAXIMUM_V1_BYTES) {
            throw new IOException("O1LM snapshot exceeds its byte bound");
        }
        ByteArrayOutputStream payloadBytes =
            new ByteArrayOutputStream((int)expectedLength - Integer.BYTES);
        try (DataOutputStream output =
                 new DataOutputStream(payloadBytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(HEADER_FIELD_COUNT);
            output.writeInt(snapshot.count());
            for (Record record : snapshot.records()) {
                writeString(output, record.descriptor.digest, DIGEST_BYTES);
                output.writeLong(record.descriptor.byteCount);
                output.writeInt(record.descriptor.kind);
                output.writeInt(record.frontier.size());
                for (Map.Entry<String, Long> entry
                         : record.frontier.entrySet()) {
                    writeString(output, entry.getKey(), ID_BYTES);
                    output.writeLong(entry.getValue());
                }
                output.writeInt(record.heads.size());
                for (Mutation head : record.heads) {
                    writeString(output, head.mutationId, ID_BYTES);
                    writeString(output, head.actorId, ID_BYTES);
                    output.writeLong(head.counter);
                    output.writeByte(head.operation.wireValue);
                    output.writeInt(head.context.size());
                    for (Map.Entry<String, Long> entry
                             : head.context.entrySet()) {
                        writeString(output, entry.getKey(), ID_BYTES);
                        output.writeLong(entry.getValue());
                    }
                }
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
        if (result.length != expectedLength
            || result.length < MINIMUM_V1_BYTES
            || result.length > MAXIMUM_V1_BYTES) {
            throw new IOException("Incorrect O1LM encoded length");
        }
        return result;
    }

    static DecodeResult decode(byte[] bytes) {
        if (bytes == null || bytes.length < 2 * Integer.BYTES) {
            return invalidDecode();
        }
        int magic = readInt(bytes, 0);
        int version = readInt(bytes, Integer.BYTES);
        if (magic == MAGIC
            && Integer.compareUnsigned(version, VERSION) > 0) {
            if (bytes.length > MAXIMUM_FUTURE_BYTES) {
                return limitDecode(LimitReason.ENCODED_BYTES);
            }
            return new DecodeResult(
                DecodeStatus.FUTURE_VERSION, null, bytes, null, null);
        }
        if (magic != MAGIC || version != VERSION) {
            return invalidDecode();
        }
        if (bytes.length > MAXIMUM_V1_BYTES) {
            return limitDecode(LimitReason.ENCODED_BYTES);
        }
        if (bytes.length < MINIMUM_V1_BYTES) {
            return invalidDecode();
        }
        try {
            int payloadLength = bytes.length - Integer.BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(bytes, 0, payloadLength);
            if (readInt(bytes, payloadLength)
                != (int)checksum.getValue()) {
                throw new IOException("Invalid O1LM checksum");
            }
            Cursor input = new Cursor(bytes, payloadLength);
            if (input.readInt() != MAGIC
                || input.readInt() != VERSION
                || input.readInt() != HEADER_FIELD_COUNT) {
                throw new IOException("Invalid O1LM header");
            }
            int recordCount = input.readInt();
            if (recordCount < 0) {
                throw new IOException("Negative O1LM record count");
            }
            if (recordCount > MAX_RECORDS) {
                throw new LimitFailure(LimitReason.RECORD_HISTORY);
            }
            input.requireProduct(recordCount, MINIMUM_RECORD_BYTES, 0);
            ArrayList<Record> records = new ArrayList<>(recordCount);
            String previousDigest = null;
            for (int recordIndex = 0;
                 recordIndex < recordCount; ++recordIndex) {
                String digest = input.readHexString(DIGEST_BYTES);
                if (previousDigest != null
                    && previousDigest.compareTo(digest) >= 0) {
                    throw new IOException(
                        "Noncanonical O1LM descriptor order");
                }
                Descriptor descriptor = new Descriptor(
                    digest, input.readLong(), input.readInt());
                int frontierCount = input.readInt();
                if (frontierCount <= 0) {
                    throw new IOException("Empty O1LM frontier");
                }
                if (frontierCount > MAX_ACTORS) {
                    throw new LimitFailure(LimitReason.ACTOR_HISTORY);
                }
                input.requireProduct(
                    frontierCount, FRONTIER_ENTRY_BYTES,
                    Integer.BYTES + MINIMUM_HEAD_BYTES);
                TreeMap<String, Long> frontier = new TreeMap<>();
                String previousActor = null;
                for (int actorIndex = 0;
                     actorIndex < frontierCount; ++actorIndex) {
                    String actor = input.readHexString(ID_BYTES);
                    long counter = input.readLong();
                    if (counter <= 0 || (previousActor != null
                        && previousActor.compareTo(actor) >= 0)) {
                        throw new IOException(
                            "Invalid O1LM frontier entry");
                    }
                    frontier.put(actor, counter);
                    previousActor = actor;
                }
                int headCount = input.readInt();
                if (headCount <= 0) {
                    throw new IOException("Empty O1LM head set");
                }
                if (headCount > MAX_HEADS) {
                    throw new LimitFailure(
                        LimitReason.CONCURRENT_HEADS);
                }
                input.requireProduct(
                    headCount, MINIMUM_HEAD_BYTES, 0);
                ArrayList<Mutation> heads = new ArrayList<>(headCount);
                String previousMutation = null;
                for (int headIndex = 0;
                     headIndex < headCount; ++headIndex) {
                    String mutationId = input.readHexString(ID_BYTES);
                    if (previousMutation != null
                        && previousMutation.compareTo(mutationId) >= 0) {
                        throw new IOException(
                            "Noncanonical O1LM head order");
                    }
                    String actorId = input.readHexString(ID_BYTES);
                    long counter = input.readLong();
                    Operation operation = Operation.fromWire(
                        input.readUnsignedByte());
                    if (counter <= 0 || operation == null) {
                        throw new IOException("Invalid O1LM head");
                    }
                    int contextCount = input.readInt();
                    if (contextCount < 0) {
                        throw new IOException(
                            "Negative O1LM context count");
                    }
                    if (contextCount > MAX_ACTORS) {
                        throw new LimitFailure(
                            LimitReason.ACTOR_HISTORY);
                    }
                    input.requireProduct(
                        contextCount, CONTEXT_ENTRY_BYTES, 0);
                    TreeMap<String, Long> context = new TreeMap<>();
                    String previousContextActor = null;
                    for (int contextIndex = 0;
                         contextIndex < contextCount; ++contextIndex) {
                        String actor = input.readHexString(ID_BYTES);
                        long value = input.readLong();
                        if (value <= 0
                            || (previousContextActor != null
                                && previousContextActor.compareTo(actor)
                                   >= 0)) {
                            throw new IOException(
                                "Invalid O1LM context entry");
                        }
                        context.put(actor, value);
                        previousContextActor = actor;
                    }
                    heads.add(new Mutation(
                        mutationId, actorId, counter,
                        operation, context));
                    previousMutation = mutationId;
                }
                records.add(new Record(descriptor, frontier, heads));
                previousDigest = digest;
            }
            if (input.remaining() != 0) {
                throw new IOException("Trailing O1LM payload bytes");
            }
            Snapshot snapshot = new Snapshot(records);
            return new DecodeResult(
                DecodeStatus.READY, snapshot, null, null, null);
        } catch (LimitFailure failure) {
            return limitDecode(failure.reason);
        } catch (EOFException exception) {
            return invalidDecode();
        } catch (IOException | RuntimeException exception) {
            return invalidDecode();
        }
    }

    static MergeResult merge(Snapshot local, Snapshot remote) {
        Validation localValidation = validateSnapshot(local);
        Validation remoteValidation = validateSnapshot(remote);
        if (!localValidation.valid || !remoteValidation.valid) {
            return mergeResult(MergeStatus.INVALID, local, null);
        }
        if (crossSnapshotEquivocation(local, remote)) {
            return mergeResult(MergeStatus.EQUIVOCATION, local, null);
        }
        TreeMap<String, Record> left = byDigest(local);
        TreeMap<String, Record> right = byDigest(remote);
        TreeSet<String> allDigests = new TreeSet<>();
        allDigests.addAll(left.keySet());
        allDigests.addAll(right.keySet());
        if (allDigests.size() > MAX_RECORDS) {
            return mergeLimit(local, LimitReason.RECORD_HISTORY);
        }
        ArrayList<Record> joinedRecords =
            new ArrayList<>(allDigests.size());
        try {
            for (String digest : allDigests) {
                Record leftRecord = left.get(digest);
                Record rightRecord = right.get(digest);
                if (leftRecord == null) {
                    joinedRecords.add(rightRecord);
                } else if (rightRecord == null) {
                    joinedRecords.add(leftRecord);
                } else {
                    Record joined = joinRecord(leftRecord, rightRecord);
                    if (joined == null) {
                        return mergeResult(
                            MergeStatus.INVALID, local, null);
                    }
                    joinedRecords.add(joined);
                }
            }
            Snapshot joined = new Snapshot(joinedRecords);
            if (encodedLength(joined) > MAXIMUM_V1_BYTES) {
                return mergeLimit(local, LimitReason.ENCODED_BYTES);
            }
            if (joined.equals(local)) {
                return mergeResult(
                    MergeStatus.UNCHANGED, local, null);
            }
            return mergeResult(MergeStatus.MERGED, joined, null);
        } catch (LimitFailure failure) {
            return mergeLimit(local, failure.reason);
        } catch (IOException | RuntimeException exception) {
            return mergeResult(MergeStatus.INVALID, local, null);
        }
    }

    static MutationResult withdraw(Snapshot snapshot,
                                   Descriptor descriptor,
                                   String actorId,
                                   long nextCounter) {
        Record record = snapshot == null || descriptor == null
            ? null : snapshot.record(descriptor.digest);
        if (record != null) {
            if (!record.descriptor.sameIdentity(descriptor)) {
                return mutationResult(
                    MutationStatus.EQUIVOCATION, snapshot, null);
            }
            Projection projection = record.projection();
            if (projection == Projection.CONFLICT) {
                return mutationResult(
                    MutationStatus.INVALID, snapshot, null);
            }
            if (projection == Projection.WITHDRAWN) {
                return mutationResult(
                    MutationStatus.UNCHANGED, snapshot, null);
            }
        }
        return mutate(snapshot, descriptor, actorId, nextCounter,
                      Operation.WITHDRAW);
    }

    static MutationResult restore(Snapshot snapshot,
                                  Descriptor descriptor,
                                  String actorId,
                                  long nextCounter) {
        Record record = snapshot == null || descriptor == null
            ? null : snapshot.record(descriptor.digest);
        if (record == null) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        if (!record.descriptor.sameIdentity(descriptor)) {
            return mutationResult(
                MutationStatus.EQUIVOCATION, snapshot, null);
        }
        Projection projection = record.projection();
        if (projection == Projection.CONFLICT) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        if (projection == Projection.MEMBER) {
            return mutationResult(
                MutationStatus.UNCHANGED, snapshot, null);
        }
        return mutate(snapshot, descriptor, actorId, nextCounter,
                      Operation.RESTORE);
    }

    static MutationResult resolveConflict(Snapshot snapshot,
                                          Descriptor descriptor,
                                          String actorId,
                                          long nextCounter,
                                          Projection target) {
        Record record = snapshot == null || descriptor == null
            ? null : snapshot.record(descriptor.digest);
        if (record == null) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        if (!record.descriptor.sameIdentity(descriptor)) {
            return mutationResult(
                MutationStatus.EQUIVOCATION, snapshot, null);
        }
        if (record.projection() != Projection.CONFLICT
            || (target != Projection.MEMBER
                && target != Projection.WITHDRAWN)) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        Operation operation = target == Projection.MEMBER
            ? Operation.RESTORE : Operation.WITHDRAW;
        return mutate(snapshot, descriptor, actorId, nextCounter,
                      operation);
    }

    private static MutationResult mutate(Snapshot snapshot,
                                         Descriptor descriptor,
                                         String actorId,
                                         long nextCounter,
                                         Operation operation) {
        Validation validation = validateSnapshot(snapshot);
        if (!validation.valid || descriptor == null
            || !validActorOrMutationId(actorId) || nextCounter <= 0
            || operation == null) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        Record oldRecord = snapshot.record(descriptor.digest);
        if (oldRecord != null
            && !oldRecord.descriptor.sameIdentity(descriptor)) {
            return mutationResult(
                MutationStatus.EQUIVOCATION, snapshot, null);
        }
        if (oldRecord == null && operation != Operation.WITHDRAW) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        if (oldRecord == null && snapshot.count() >= MAX_RECORDS) {
            return mutationLimit(snapshot, LimitReason.RECORD_HISTORY);
        }
        long actorMaximum = snapshot.maximumActorCounter(actorId);
        if (actorMaximum == Long.MAX_VALUE) {
            return mutationLimit(snapshot,
                                 LimitReason.COUNTER_EXHAUSTED);
        }
        if (nextCounter <= actorMaximum) {
            return mutationResult(
                MutationStatus.EQUIVOCATION, snapshot, null);
        }
        TreeMap<String, Long> frontier = new TreeMap<>();
        TreeMap<String, Long> context = new TreeMap<>();
        if (oldRecord != null) {
            frontier.putAll(oldRecord.frontier);
            context.putAll(oldRecord.frontier);
            if (!frontier.containsKey(actorId)
                && frontier.size() >= MAX_ACTORS) {
                return mutationLimit(snapshot,
                                     LimitReason.ACTOR_HISTORY);
            }
        }
        frontier.put(actorId, nextCounter);
        String mutationId;
        try {
            mutationId = computeMutationId(
                descriptor, actorId, nextCounter, operation, context);
        } catch (IOException exception) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        for (Record record : snapshot.records()) {
            for (Mutation existing : record.heads) {
                if (existing.mutationId.equals(mutationId)) {
                    return mutationResult(
                        MutationStatus.EQUIVOCATION, snapshot, null);
                }
            }
        }
        Mutation head = new Mutation(
            mutationId, actorId, nextCounter, operation, context);
        Record replacement;
        try {
            replacement = new Record(
                descriptor, frontier, Collections.singletonList(head));
        } catch (RuntimeException exception) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
        ArrayList<Record> records = new ArrayList<>(snapshot.records());
        if (oldRecord != null) {
            records.remove(oldRecord);
        }
        records.add(replacement);
        try {
            Snapshot result = new Snapshot(records);
            if (encodedLength(result) > MAXIMUM_V1_BYTES) {
                return mutationLimit(snapshot,
                                     LimitReason.ENCODED_BYTES);
            }
            return mutationResult(
                MutationStatus.MUTATED, result, null);
        } catch (IOException | RuntimeException exception) {
            return mutationResult(
                MutationStatus.INVALID, snapshot, null);
        }
    }

    private static Record joinRecord(Record left, Record right)
        throws LimitFailure {
        TreeMap<String, Long> frontier = new TreeMap<>(left.frontier);
        for (Map.Entry<String, Long> entry : right.frontier.entrySet()) {
            Long existing = frontier.get(entry.getKey());
            if (existing == null || entry.getValue() > existing) {
                frontier.put(entry.getKey(), entry.getValue());
            }
        }
        if (frontier.size() > MAX_ACTORS) {
            throw new LimitFailure(LimitReason.ACTOR_HISTORY);
        }
        TreeMap<String, Mutation> leftHeads = headsById(left);
        TreeMap<String, Mutation> rightHeads = headsById(right);
        TreeMap<String, Mutation> retained = new TreeMap<>();
        for (Mutation head : left.heads) {
            Mutation shared = rightHeads.get(head.mutationId);
            if (shared != null) {
                retained.put(head.mutationId, head);
            } else if (!incorporates(right.frontier, head)) {
                retained.put(head.mutationId, head);
            }
        }
        for (Mutation head : right.heads) {
            if (!leftHeads.containsKey(head.mutationId)
                && !incorporates(left.frontier, head)) {
                retained.put(head.mutationId, head);
            }
        }
        if (retained.isEmpty()) {
            return null;
        }
        if (retained.size() > MAX_HEADS) {
            throw new LimitFailure(LimitReason.CONCURRENT_HEADS);
        }
        try {
            return new Record(left.descriptor, frontier,
                              retained.values());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean incorporates(Map<String, Long> frontier,
                                        Mutation mutation) {
        Long counter = frontier.get(mutation.actorId);
        return counter != null && counter >= mutation.counter;
    }

    private static boolean crossSnapshotEquivocation(Snapshot left,
                                                     Snapshot right) {
        TreeMap<String, Record> leftRecords = byDigest(left);
        for (Record record : right.records()) {
            Record existing = leftRecords.get(record.descriptor.digest);
            if (existing != null
                && !existing.descriptor.sameIdentity(record.descriptor)) {
                return true;
            }
        }
        HashMap<String, Mutation> mutations = new HashMap<>();
        HashMap<Dot, Mutation> heads = new HashMap<>();
        HashMap<Dot, String> owners = new HashMap<>();
        return indexForEquivocation(left, mutations, heads, owners)
            || indexForEquivocation(right, mutations, heads, owners);
    }

    private static boolean indexForEquivocation(
        Snapshot snapshot,
        Map<String, Mutation> mutations,
        Map<Dot, Mutation> heads,
        Map<Dot, String> owners) {
        for (Record record : snapshot.records()) {
            String digest = record.descriptor.digest;
            for (Mutation head : record.heads) {
                Mutation previousMutation = mutations.put(
                    head.mutationId, head);
                if (previousMutation != null
                    && !previousMutation.equals(head)) {
                    return true;
                }
                Dot headDot = new Dot(head.actorId, head.counter);
                Mutation previousHead = heads.put(headDot, head);
                if (previousHead != null
                    && !previousHead.equals(head)) {
                    return true;
                }
                if (differentOwner(owners, headDot, digest)) {
                    return true;
                }
                for (Map.Entry<String, Long> entry
                         : head.context.entrySet()) {
                    if (differentOwner(
                        owners, new Dot(entry.getKey(), entry.getValue()),
                        digest)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean differentOwner(Map<Dot, String> owners,
                                          Dot dot,
                                          String digest) {
        String previous = owners.put(dot, digest);
        return previous != null && !previous.equals(digest);
    }

    private static Validation validateSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.records == null
            || snapshot.count() > MAX_RECORDS) {
            return Validation.invalid();
        }
        HashMap<String, Mutation> mutationIds = new HashMap<>();
        HashMap<Dot, String> owners = new HashMap<>();
        String previousDigest = null;
        for (Record record : snapshot.records.values()) {
            Validation recordValidation = validateRecord(record);
            if (!recordValidation.valid) {
                return recordValidation;
            }
            String digest = record.descriptor.digest;
            if (previousDigest != null
                && previousDigest.compareTo(digest) >= 0) {
                return Validation.invalid();
            }
            for (Mutation head : record.heads) {
                Mutation previous = mutationIds.put(
                    head.mutationId, head);
                if (previous != null && !previous.equals(head)) {
                    return Validation.equivocation();
                }
                Dot headDot = new Dot(head.actorId, head.counter);
                if (differentOwner(owners, headDot, digest)) {
                    return Validation.equivocation();
                }
                for (Map.Entry<String, Long> entry
                         : head.context.entrySet()) {
                    if (differentOwner(
                        owners, new Dot(entry.getKey(), entry.getValue()),
                        digest)) {
                        return Validation.equivocation();
                    }
                }
            }
            previousDigest = digest;
        }
        return Validation.valid();
    }

    private static Validation validateRecord(Record record) {
        if (record == null || record.descriptor == null
            || !validDigest(record.descriptor.digest)
            || record.descriptor.byteCount <= 0
            || record.descriptor.byteCount > MAX_DOCUMENT_BYTES
            || record.descriptor.kind != Descriptor.EPUB
            || record.frontier == null || record.frontier.isEmpty()
            || record.frontier.size() > MAX_ACTORS
            || record.heads == null || record.heads.isEmpty()
            || record.heads.size() > MAX_HEADS) {
            return Validation.invalid();
        }
        for (Map.Entry<String, Long> entry
                 : record.frontier.entrySet()) {
            if (!validActorOrMutationId(entry.getKey())
                || entry.getValue() == null || entry.getValue() <= 0) {
                return Validation.invalid();
            }
        }
        TreeSet<String> mutationIds = new TreeSet<>();
        TreeSet<String> liveActors = new TreeSet<>();
        for (Mutation head : record.heads) {
            if (head == null
                || !validActorOrMutationId(head.mutationId)
                || !validActorOrMutationId(head.actorId)
                || head.counter <= 0 || head.operation == null
                || head.context == null
                || head.context.size() > MAX_ACTORS
                || !mutationIds.add(head.mutationId)
                || !liveActors.add(head.actorId)) {
                return Validation.invalid();
            }
            Long frontierCounter = record.frontier.get(head.actorId);
            if (frontierCounter == null
                || frontierCounter.longValue() != head.counter) {
                return Validation.invalid();
            }
            if (head.operation == Operation.RESTORE
                && head.context.isEmpty()) {
                return Validation.invalid();
            }
            for (Map.Entry<String, Long> entry
                     : head.context.entrySet()) {
                if (!validActorOrMutationId(entry.getKey())
                    || entry.getValue() == null || entry.getValue() <= 0) {
                    return Validation.invalid();
                }
                Long maximum = record.frontier.get(entry.getKey());
                if (maximum == null || entry.getValue() > maximum
                    || (entry.getKey().equals(head.actorId)
                        && entry.getValue() >= head.counter)) {
                    return Validation.invalid();
                }
            }
            try {
                if (!head.mutationId.equals(computeMutationId(
                    record.descriptor, head.actorId, head.counter,
                    head.operation, head.context))) {
                    return Validation.invalid();
                }
            } catch (IOException exception) {
                return Validation.invalid();
            }
        }
        for (Map.Entry<String, Long> frontier
                 : record.frontier.entrySet()) {
            boolean justified = false;
            for (Mutation head : record.heads) {
                if ((head.actorId.equals(frontier.getKey())
                     && head.counter == frontier.getValue())
                    || frontier.getValue().equals(
                        head.context.get(frontier.getKey()))) {
                    justified = true;
                    break;
                }
            }
            if (!justified) {
                return Validation.invalid();
            }
        }
        for (Mutation observer : record.heads) {
            for (Mutation observed : record.heads) {
                if (observer == observed) {
                    continue;
                }
                Long seen = observer.context.get(observed.actorId);
                if (seen != null && seen >= observed.counter) {
                    return Validation.invalid();
                }
            }
        }
        return Validation.valid();
    }

    private static String computeMutationId(
        Descriptor descriptor,
        String actorId,
        long counter,
        Operation operation,
        Map<String, Long> context) throws IOException {
        if (descriptor == null || !validActorOrMutationId(actorId)
            || counter <= 0 || operation == null || context == null
            || context.size() > MAX_ACTORS) {
            throw new IOException("Invalid mutation identity input");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MUTATION_NAMESPACE);
            writeString(output, descriptor.digest, DIGEST_BYTES);
            output.writeLong(descriptor.byteCount);
            output.writeInt(descriptor.kind);
            writeString(output, actorId, ID_BYTES);
            output.writeLong(counter);
            output.writeByte(operation.wireValue);
            output.writeInt(context.size());
            String previous = null;
            for (Map.Entry<String, Long> entry
                     : context.entrySet()) {
                if (!validActorOrMutationId(entry.getKey())
                    || entry.getValue() == null || entry.getValue() <= 0
                    || (previous != null
                        && previous.compareTo(entry.getKey()) >= 0)) {
                    throw new IOException(
                        "Noncanonical mutation context");
                }
                writeString(output, entry.getKey(), ID_BYTES);
                output.writeLong(entry.getValue());
                previous = entry.getKey();
            }
            output.flush();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray());
            return lowerHex(digest, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable", exception);
        }
    }

    static String mutationIdForTesting(Descriptor descriptor,
                                       String actorId,
                                       long counter,
                                       Operation operation,
                                       Map<String, Long> context)
        throws IOException {
        TreeMap<String, Long> canonical = copyVector(context, false);
        return computeMutationId(descriptor, actorId, counter,
                                 operation, canonical);
    }

    private static long encodedLength(Snapshot snapshot)
        throws IOException {
        if (snapshot == null || snapshot.count() > MAX_RECORDS) {
            throw new IOException("Invalid O1LM snapshot");
        }
        long total = MINIMUM_V1_BYTES;
        for (Record record : snapshot.records()) {
            long recordBytes = 68L + Long.BYTES + Integer.BYTES
                + Integer.BYTES + 44L * record.frontier.size()
                + Integer.BYTES;
            for (Mutation head : record.heads) {
                recordBytes += 36L + 36L + Long.BYTES + 1L
                    + Integer.BYTES + 44L * head.context.size();
            }
            if (recordBytes > MAXIMUM_RECORD_BYTES) {
                throw new IOException("O1LM record exceeds byte bound");
            }
            total += recordBytes;
            if (total > MAXIMUM_V1_BYTES) {
                return total;
            }
        }
        return total;
    }

    private static TreeMap<String, Long> copyVector(
        Map<String, Long> source, boolean requireNonempty) {
        if (source == null || (requireNonempty && source.isEmpty())
            || source.size() > MAX_ACTORS) {
            throw new IllegalArgumentException("Invalid actor vector");
        }
        TreeMap<String, Long> copied = new TreeMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            if (!validActorOrMutationId(entry.getKey())
                || entry.getValue() == null || entry.getValue() <= 0
                || copied.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException(
                    "Invalid actor vector entry");
            }
        }
        return copied;
    }

    private static TreeMap<String, Record> byDigest(Snapshot snapshot) {
        TreeMap<String, Record> result = new TreeMap<>();
        for (Record record : snapshot.records()) {
            result.put(record.descriptor.digest, record);
        }
        return result;
    }

    private static TreeMap<String, Mutation> headsById(Record record) {
        TreeMap<String, Mutation> result = new TreeMap<>();
        for (Mutation head : record.heads) {
            result.put(head.mutationId, head);
        }
        return result;
    }

    private static void writeString(DataOutputStream output,
                                    String value,
                                    int expectedBytes)
        throws IOException {
        if ((expectedBytes == DIGEST_BYTES && !validDigest(value))
            || (expectedBytes == ID_BYTES
                && !validActorOrMutationId(value))) {
            throw new IOException("Invalid canonical O1LM string");
        }
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != expectedBytes) {
            throw new IOException("Invalid O1LM string byte length");
        }
        output.writeInt(expectedBytes);
        output.write(bytes);
    }

    static boolean validDigest(String value) {
        return validLowerHex(value, DIGEST_BYTES);
    }

    static boolean validActorId(String value) {
        return validActorOrMutationId(value);
    }

    private static boolean validActorOrMutationId(String value) {
        return validLowerHex(value, ID_BYTES);
    }

    private static boolean validLowerHex(String value, int length) {
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

    private static String lowerHex(byte[] bytes, int count) {
        char[] result = new char[count * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < count; ++index) {
            int value = bytes[index] & 0xff;
            result[2 * index] = alphabet[value >>> 4];
            result[2 * index + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }

    private static DecodeResult invalidDecode() {
        return new DecodeResult(
            DecodeStatus.INVALID, null, null, null, null);
    }

    private static DecodeResult limitDecode(LimitReason reason) {
        return new DecodeResult(
            DecodeStatus.LIMIT, null, null,
            LimitScope.INPUT, reason);
    }

    private static MergeResult mergeResult(MergeStatus status,
                                           Snapshot snapshot,
                                           LimitReason reason) {
        return new MergeResult(status, snapshot,
            reason == null ? null : LimitScope.JOIN, reason);
    }

    private static MergeResult mergeLimit(Snapshot snapshot,
                                          LimitReason reason) {
        return mergeResult(MergeStatus.LIMIT, snapshot, reason);
    }

    private static MutationResult mutationResult(
        MutationStatus status,
        Snapshot snapshot,
        LimitReason reason) {
        return new MutationResult(status, snapshot,
            reason == null ? null : LimitScope.LOCAL, reason);
    }

    private static MutationResult mutationLimit(Snapshot snapshot,
                                                LimitReason reason) {
        return mutationResult(MutationStatus.LIMIT, snapshot, reason);
    }

    static int minimumV1Bytes() {
        return MINIMUM_V1_BYTES;
    }

    static int maximumRecordBytes() {
        return MAXIMUM_RECORD_BYTES;
    }

    static int maximumV1Bytes() {
        return MAXIMUM_V1_BYTES;
    }

    static int maximumFutureBytes() {
        return MAXIMUM_FUTURE_BYTES;
    }

    static int maximumRecordCount() {
        return MAX_RECORDS;
    }

    static int maximumActorCount() {
        return MAX_ACTORS;
    }

    static int maximumHeadCount() {
        return MAX_HEADS;
    }

    static long maximumDocumentBytes() {
        return MAX_DOCUMENT_BYTES;
    }

    static int magicForTesting() {
        return MAGIC;
    }

    static int versionForTesting() {
        return VERSION;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }

    private static final class Cursor {
        private final byte[] bytes;
        private final int limit;
        private int position;

        Cursor(byte[] bytes, int limit) {
            this.bytes = bytes;
            this.limit = limit;
        }

        int remaining() {
            return limit - position;
        }

        int readUnsignedByte() throws EOFException {
            require(1);
            return bytes[position++] & 0xff;
        }

        int readInt() throws EOFException {
            require(Integer.BYTES);
            int result = OctavoLibraryMembershipPortable.readInt(
                bytes, position);
            position += Integer.BYTES;
            return result;
        }

        long readLong() throws EOFException {
            require(Long.BYTES);
            long result = 0;
            for (int index = 0; index < Long.BYTES; ++index) {
                result = (result << 8) | (bytes[position++] & 0xffL);
            }
            return result;
        }

        String readHexString(int expectedLength)
            throws IOException {
            int length = readInt();
            if (length != expectedLength) {
                throw new IOException("Invalid O1LM string length");
            }
            require(length);
            char[] result = new char[length];
            for (int index = 0; index < length; ++index) {
                int value = bytes[position++] & 0xff;
                if (!((value >= '0' && value <= '9')
                      || (value >= 'a' && value <= 'f'))) {
                    throw new IOException(
                        "Invalid O1LM lowercase ASCII string");
                }
                result[index] = (char)value;
            }
            return new String(result);
        }

        void requireProduct(int count,
                            int elementBytes,
                            int fixedBytes) throws EOFException {
            if (count < 0 || elementBytes < 0 || fixedBytes < 0) {
                throw new EOFException("Invalid O1LM byte preflight");
            }
            long available = remaining();
            if (fixedBytes > available
                || (count != 0
                    && elementBytes
                       > (Long.MAX_VALUE - fixedBytes) / count)) {
                throw new EOFException("Truncated O1LM count payload");
            }
            long required = fixedBytes + (long)count * elementBytes;
            if (required > available) {
                throw new EOFException("Truncated O1LM count payload");
            }
        }

        private void require(int count) throws EOFException {
            if (count < 0 || count > remaining()) {
                throw new EOFException("Truncated O1LM input");
            }
        }
    }

    private static final class Dot {
        final String actor;
        final long counter;

        Dot(String actor, long counter) {
            this.actor = actor;
            this.counter = counter;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Dot)) {
                return false;
            }
            Dot other = (Dot)object;
            return actor.equals(other.actor) && counter == other.counter;
        }

        @Override
        public int hashCode() {
            return 31 * actor.hashCode() + Long.hashCode(counter);
        }
    }

    private static final class Validation {
        final boolean valid;
        final boolean equivocation;

        private Validation(boolean valid, boolean equivocation) {
            this.valid = valid;
            this.equivocation = equivocation;
        }

        static Validation valid() {
            return new Validation(true, false);
        }

        static Validation invalid() {
            return new Validation(false, false);
        }

        static Validation equivocation() {
            return new Validation(false, true);
        }
    }

    private static final class LimitFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final LimitReason reason;

        LimitFailure(LimitReason reason) {
            this.reason = reason;
        }
    }
}
