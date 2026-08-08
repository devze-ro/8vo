package ro.devze.octavo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Independent test-side writer for the frozen annotation portable-v1 bytes. */
final class OctavoAnnotationPortableWire {
    static final int MAGIC = 0x4F314150; // "O1AP"
    static final int VERSION = 1;
    static final int HEADER_FIELD_COUNT = 1;

    enum Kind {
        BOOKMARK(1),
        HIGHLIGHT(2),
        NOTE(3);

        final int wireId;

        Kind(int wireId) {
            this.wireId = wireId;
        }
    }

    enum Operation {
        PUT(1),
        DELETE(2);

        final int wireId;

        Operation(int wireId) {
            this.wireId = wireId;
        }
    }

    static final class Record {
        final String recordId;
        final Kind kind;
        final String bookDigest;
        final TreeMap<String, Long> frontier = new TreeMap<>();
        final List<Head> heads = new ArrayList<>();

        Record(String recordId, Kind kind, String bookDigest) {
            this.recordId = recordId;
            this.kind = kind;
            this.bookDigest = bookDigest;
        }

        Record add(Head head) {
            heads.add(head);
            frontier.merge(head.actorId, head.counter, Math::max);
            for (Map.Entry<String, Long> observed
                     : head.context.entrySet()) {
                frontier.merge(
                    observed.getKey(), observed.getValue(), Math::max);
            }
            return this;
        }

        Record frontier(String actorId, long counter) {
            frontier.put(actorId, counter);
            return this;
        }
    }

    static final class Head {
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

        Head(String actorId,
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
            this.actorId = actorId;
            this.counter = counter;
            this.operation = operation;
            this.context = new TreeMap<>(context);
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
    }

    private OctavoAnnotationPortableWire() {
    }

    static Head put(String actorId,
                    long counter,
                    long spineIndex,
                    long byteStart,
                    long byteEnd,
                    int color,
                    int flags,
                    String attachedId,
                    String label,
                    String excerpt,
                    String note) {
        return new Head(actorId,
                        counter,
                        Operation.PUT,
                        new TreeMap<>(),
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

    static Head delete(String actorId,
                       long counter,
                       TreeMap<String, Long> context,
                       long spineIndex,
                       long byteStart,
                       long byteEnd) {
        return new Head(actorId,
                        counter,
                        Operation.DELETE,
                        context,
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

    static byte[] encode(List<Record> source) throws IOException {
        return encode(source, source.size(), true, true);
    }

    static byte[] encode(List<Record> source,
                         int declaredRecordCount,
                         boolean canonicalRecords,
                         boolean canonicalHeads) throws IOException {
        ArrayList<Record> records = new ArrayList<>(source);
        if (canonicalRecords) {
            records.sort(Comparator.comparing(record -> record.recordId));
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payload)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(HEADER_FIELD_COUNT);
            output.writeInt(declaredRecordCount);
            for (Record record : records) {
                writeRecord(output, record, canonicalHeads);
            }
            output.flush();
        }
        return appendChecksum(payload.toByteArray());
    }

    static String bookmarkRecordId(String digest,
                                   long spineIndex,
                                   long byteOffset) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write("8vo.port11.bookmark.v1\n".getBytes(
                StandardCharsets.US_ASCII));
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

    static String mutationId(Record record, Head head) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, record.recordId);
                output.writeByte(record.kind.wireId);
                writeString(output, record.bookDigest);
                writeString(output, head.actorId);
                output.writeLong(head.counter);
                output.writeByte(head.operation.wireId);
                output.writeInt(head.context.size());
                for (Map.Entry<String, Long> observed
                         : head.context.entrySet()) {
                    writeString(output, observed.getKey());
                    output.writeLong(observed.getValue());
                }
                writePayload(output, head);
                output.flush();
            }
            return first128Hex(sha256(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeRecord(DataOutputStream output,
                                    Record record,
                                    boolean canonicalHeads)
        throws IOException {
        writeString(output, record.recordId);
        output.writeByte(record.kind.wireId);
        writeString(output, record.bookDigest);
        output.writeInt(record.frontier.size());
        for (Map.Entry<String, Long> actor : record.frontier.entrySet()) {
            writeString(output, actor.getKey());
            output.writeLong(actor.getValue());
        }
        ArrayList<Head> heads = new ArrayList<>(record.heads);
        if (canonicalHeads) {
            heads.sort(Comparator.comparing(head -> mutationId(record, head)));
        }
        output.writeInt(heads.size());
        for (Head head : heads) {
            writeString(output, mutationId(record, head));
            writeString(output, head.actorId);
            output.writeLong(head.counter);
            output.writeByte(head.operation.wireId);
            output.writeInt(head.context.size());
            for (Map.Entry<String, Long> observed
                     : head.context.entrySet()) {
                writeString(output, observed.getKey());
                output.writeLong(observed.getValue());
            }
            writePayload(output, head);
        }
    }

    private static void writePayload(DataOutputStream output, Head head)
        throws IOException {
        output.writeLong(head.spineIndex);
        output.writeLong(head.byteStart);
        output.writeLong(head.byteEnd);
        output.writeInt(head.color);
        output.writeInt(head.flags);
        writeString(output, head.attachedId);
        writeString(output, head.label);
        writeString(output, head.excerpt);
        writeString(output, head.note);
    }

    private static void writeString(DataOutputStream output, String value)
        throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] appendChecksum(byte[] payload)
        throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        ByteArrayOutputStream complete = new ByteArrayOutputStream(
            payload.length + Integer.BYTES);
        complete.write(payload);
        try (DataOutputStream output = new DataOutputStream(complete)) {
            output.writeInt((int)checksum.getValue());
            output.flush();
        }
        return complete.toByteArray();
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
            result.append(Character.forDigit((bytes[index] >>> 4) & 0xf, 16));
            result.append(Character.forDigit(bytes[index] & 0xf, 16));
        }
        return result.toString();
    }
}
