package ro.devze.octavo;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Local-only crash-recovery state for an incomplete note editor. */
final class OctavoNoteDraftStore {
    enum LoadStatus {
        MISSING,
        LOADED,
        CORRUPT_QUARANTINED,
        CORRUPT_BLOCKED,
        FUTURE_VERSION_BLOCKED
    }

    static final class Draft {
        final String recordId;
        final String expectedRevisionToken;
        final String bookDigest;
        final long spineIndex;
        final long byteStart;
        final long byteEnd;
        final String attachedHighlightId;
        final String excerpt;
        final String body;

        Draft(String recordId,
              String expectedRevisionToken,
              String bookDigest,
              long spineIndex,
              long byteStart,
              long byteEnd,
              String attachedHighlightId,
              String excerpt,
              String body) {
            this.recordId = recordId;
            this.expectedRevisionToken = expectedRevisionToken;
            this.bookDigest = bookDigest;
            this.spineIndex = spineIndex;
            this.byteStart = byteStart;
            this.byteEnd = byteEnd;
            this.attachedHighlightId = attachedHighlightId;
            this.excerpt = excerpt;
            this.body = body;
        }

        Draft withBody(String updatedBody) {
            return new Draft(recordId,
                             expectedRevisionToken,
                             bookDigest,
                             spineIndex,
                             byteStart,
                             byteEnd,
                             attachedHighlightId,
                             excerpt,
                             updatedBody);
        }

        boolean isNewNote() {
            return expectedRevisionToken.isEmpty();
        }
    }

    private static final int MAGIC = 0x4f314e44; // O1ND
    private static final int VERSION = 1;
    private static final int MAX_FILE_BYTES = 8 * 1024;
    private static final int ID_BYTES = 32;
    private static final int DIGEST_BYTES = 64;
    private static final int MAX_EXCERPT_BYTES = 512;
    private static final int MAX_NOTE_BYTES = 4096;
    private static final int QUARANTINE_SLOTS = 3;
    private static final String ROOT_DIRECTORY = "port11";
    private static final String STATE_FILE = "note-draft.v1";
    private static final String TEMPORARY_FILE = "note-draft.v1.tmp";
    private static final String QUARANTINE_PREFIX = "note-draft.corrupt.";

    private final File rootDirectory;
    private final File stateFile;
    private final File temporaryFile;
    private Draft current;
    private boolean blocked;
    private boolean failNextPublishForTesting;

    OctavoNoteDraftStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoNoteDraftStore(File filesDirectory) {
        if (filesDirectory == null) {
            throw new IllegalArgumentException();
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        stateFile = new File(rootDirectory, STATE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
    }

    synchronized LoadStatus load() {
        blocked = false;
        current = null;
        if (!stateFile.exists()) {
            return LoadStatus.MISSING;
        }
        try {
            byte[] bytes = readBounded(stateFile);
            if (bytes.length < 2 * Integer.BYTES) {
                throw new IOException("Truncated draft header");
            }
            try (DataInputStream header = new DataInputStream(
                     new ByteArrayInputStream(bytes))) {
                if (header.readInt() == MAGIC
                    && header.readInt() > VERSION) {
                    blocked = true;
                    return LoadStatus.FUTURE_VERSION_BLOCKED;
                }
            }
            current = decode(bytes);
            return LoadStatus.LOADED;
        } catch (IOException | RuntimeException exception) {
            if (quarantine()) {
                return LoadStatus.CORRUPT_QUARANTINED;
            }
            blocked = true;
            return LoadStatus.CORRUPT_BLOCKED;
        }
    }

    synchronized Draft current() {
        return current;
    }

    synchronized boolean save(Draft draft) {
        if (blocked || !valid(draft)) {
            return false;
        }
        try {
            byte[] bytes = encode(draft);
            if (failNextPublishForTesting) {
                failNextPublishForTesting = false;
                return false;
            }
            requireDirectory(rootDirectory);
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
            current = draft;
            return true;
        } catch (IOException | RuntimeException exception) {
            deleteBestEffort(temporaryFile);
            return false;
        }
    }

    synchronized boolean clear() {
        if (blocked) {
            return false;
        }
        if (stateFile.exists() && !stateFile.delete()) {
            return false;
        }
        deleteBestEffort(temporaryFile);
        current = null;
        return true;
    }

    synchronized void failNextPublishForTesting() {
        failNextPublishForTesting = true;
    }

    File stateFileForTesting() {
        return stateFile;
    }

    static int maximumNoteBytes() {
        return MAX_NOTE_BYTES;
    }

    static void clearForTesting(Context context) {
        OctavoNoteDraftStore store = new OctavoNoteDraftStore(context);
        if (!deleteBestEffort(store.temporaryFile)
            || !deleteBestEffort(store.stateFile)) {
            throw new IllegalStateException("Unable to clear note draft");
        }
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            if (!deleteBestEffort(new File(
                    store.rootDirectory, QUARANTINE_PREFIX + index))) {
                throw new IllegalStateException(
                    "Unable to clear quarantined note draft");
            }
        }
    }

    private static byte[] encode(Draft draft) throws IOException {
        if (!valid(draft)) {
            throw new IOException("Invalid note draft");
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payload)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            writeString(output, draft.recordId, ID_BYTES);
            writeString(output, draft.expectedRevisionToken, ID_BYTES);
            writeString(output, draft.bookDigest, DIGEST_BYTES);
            output.writeLong(draft.spineIndex);
            output.writeLong(draft.byteStart);
            output.writeLong(draft.byteEnd);
            writeString(output, draft.attachedHighlightId, ID_BYTES);
            writeString(output, draft.excerpt, MAX_EXCERPT_BYTES);
            writeString(output, draft.body, MAX_NOTE_BYTES);
        }
        byte[] content = payload.toByteArray();
        if (content.length > MAX_FILE_BYTES - Integer.BYTES) {
            throw new IOException("Note draft exceeds bound");
        }
        CRC32 crc = new CRC32();
        crc.update(content);
        ByteArrayOutputStream complete = new ByteArrayOutputStream();
        complete.write(content);
        try (DataOutputStream output = new DataOutputStream(complete)) {
            output.writeInt((int)crc.getValue());
        }
        return complete.toByteArray();
    }

    private static Draft decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length <= Integer.BYTES
            || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid note draft length");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, payloadLength);
        int stored = ((bytes[payloadLength] & 0xff) << 24)
            | ((bytes[payloadLength + 1] & 0xff) << 16)
            | ((bytes[payloadLength + 2] & 0xff) << 8)
            | (bytes[payloadLength + 3] & 0xff);
        if (stored != (int)crc.getValue()) {
            throw new IOException("Invalid note draft checksum");
        }
        try (DataInputStream input = new DataInputStream(
                 new ByteArrayInputStream(bytes, 0, payloadLength))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Invalid note draft header");
            }
            Draft draft = new Draft(
                readString(input, ID_BYTES),
                readString(input, ID_BYTES),
                readString(input, DIGEST_BYTES),
                input.readLong(),
                input.readLong(),
                input.readLong(),
                readString(input, ID_BYTES),
                readString(input, MAX_EXCERPT_BYTES),
                readString(input, MAX_NOTE_BYTES));
            if (input.read() != -1 || !valid(draft)) {
                throw new IOException("Invalid note draft payload");
            }
            return draft;
        }
    }

    private boolean quarantine() {
        for (int index = 1; index <= QUARANTINE_SLOTS; ++index) {
            File target = new File(
                rootDirectory, QUARANTINE_PREFIX + index);
            if (target.exists()) {
                continue;
            }
            try {
                Files.move(stateFile.toPath(), target.toPath(),
                           StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (IOException | RuntimeException exception) {
                return false;
            }
        }
        return false;
    }

    private static boolean valid(Draft draft) {
        return draft != null
            && isHex(draft.recordId, ID_BYTES)
            && (draft.expectedRevisionToken.isEmpty()
                || isHex(draft.expectedRevisionToken, ID_BYTES))
            && isHex(draft.bookDigest, DIGEST_BYTES)
            && draft.spineIndex >= 0 && draft.spineIndex <= 0xffffffffL
            && draft.byteStart >= 0 && draft.byteEnd >= draft.byteStart
            && (draft.attachedHighlightId.isEmpty()
                || isHex(draft.attachedHighlightId, ID_BYTES))
            && validText(draft.excerpt, MAX_EXCERPT_BYTES)
            && validText(draft.body, MAX_NOTE_BYTES);
    }

    private static boolean validText(String value, int maximumBytes) {
        return value != null
            && value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
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

    private static void writeString(DataOutputStream output,
                                    String value,
                                    int maximumBytes) throws IOException {
        if (!validText(value, maximumBytes)) {
            throw new IOException("Draft string exceeds bound");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input,
                                     int maximumBytes) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximumBytes) {
            throw new IOException("Invalid draft string length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("Invalid UTF-8 note draft");
        }
        return value;
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0
            || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Invalid note draft file");
        }
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            if (input.read() != -1) {
                throw new IOException("Note draft grew while reading");
            }
        }
        if (offset != bytes.length) {
            throw new IOException("Note draft changed while reading");
        }
        return bytes;
    }

    private static void requireDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()
            && !directory.isDirectory()) {
            throw new IOException("Unable to create note draft directory");
        }
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null || context.getFilesDir() == null) {
            throw new IllegalArgumentException();
        }
        return context.getFilesDir();
    }

    private static boolean deleteBestEffort(File file) {
        return file == null || !file.exists() || file.delete();
    }
}
