package ro.devze.octavo;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Bounded, atomic storage for Port 8's single global progress choice. */
final class OctavoProgressStore {
    enum LoadStatus {
        MISSING,
        CURRENT,
        FUTURE,
        CORRUPT
    }

    private static final int STORE_MAGIC = 0x4F385047; // "O8PG"
    private static final int STORE_VERSION = 1;
    private static final int FIELD_COUNT = 1;
    private static final int MAX_FILE_BYTES = 64;
    private static final int RECORD_BYTES = 5 * Integer.BYTES;
    private static final String ROOT_DIRECTORY = "port8";
    private static final String PROGRESS_FILE = "progress.v1";
    private static final String TEMPORARY_FILE = "progress.v1.tmp";

    private final File rootDirectory;
    private final File progressFile;
    private final File temporaryFile;
    private OctavoProgressDisplay current = OctavoProgressDisplay.defaults();
    private long loadSuccessCount;
    private long loadFailureCount;
    private long saveSuccessCount;
    private long saveFailureCount;
    private long missingFallbackCount;
    private long corruptFallbackCount;
    private LoadStatus loadStatus = LoadStatus.MISSING;

    OctavoProgressStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoProgressStore(File filesDirectory) {
        if (filesDirectory == null) {
            throw new IllegalArgumentException("Missing files directory");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        progressFile = new File(rootDirectory, PROGRESS_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
    }

    synchronized OctavoProgressDisplay load() {
        if (!progressFile.exists()) {
            current = OctavoProgressDisplay.defaults();
            loadStatus = LoadStatus.MISSING;
            missingFallbackCount += 1;
            return current;
        }
        try {
            byte[] bytes = readBounded(progressFile);
            if (isRecognizableFuture(bytes)) {
                current = OctavoProgressDisplay.defaults();
                loadStatus = LoadStatus.FUTURE;
                loadFailureCount += 1;
                corruptFallbackCount += 1;
                return current;
            }
            OctavoProgressDisplay loaded = decode(bytes);
            if (loaded == null) {
                throw new IOException("Invalid Port 8 progress record");
            }
            current = loaded;
            loadStatus = LoadStatus.CURRENT;
            loadSuccessCount += 1;
        } catch (IOException | RuntimeException exception) {
            current = OctavoProgressDisplay.defaults();
            loadStatus = LoadStatus.CORRUPT;
            loadFailureCount += 1;
            corruptFallbackCount += 1;
        }
        return current;
    }

    synchronized boolean save(OctavoProgressDisplay candidate) {
        if (candidate == null) {
            saveFailureCount += 1;
            return false;
        }
        if (loadStatus == LoadStatus.FUTURE
            || hasRecognizableFutureRecord()) {
            loadStatus = LoadStatus.FUTURE;
            saveFailureCount += 1;
            return false;
        }
        try {
            byte[] bytes = encode(candidate);
            requireDirectory(rootDirectory);
            try (FileOutputStream output =
                     new FileOutputStream(temporaryFile, false)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            Files.move(temporaryFile.toPath(),
                       progressFile.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            saveFailureCount += 1;
            return false;
        }
        current = candidate;
        loadStatus = LoadStatus.CURRENT;
        saveSuccessCount += 1;
        return true;
    }

    synchronized OctavoProgressDisplay current() {
        return current;
    }

    synchronized LoadStatus loadStatus() {
        return loadStatus;
    }

    /**
     * Proves exact canonical current-version bytes without treating an
     * in-memory fallback or a recognizable future record as durable state.
     */
    synchronized boolean hasCanonicalCurrentRecord(
        OctavoProgressDisplay expected) {
        if (expected == null) {
            return false;
        }
        try {
            byte[] bytes = readBounded(progressFile);
            return !isRecognizableFuture(bytes)
                && decode(bytes) == expected;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    synchronized boolean recoveredFromCorruption() {
        return corruptFallbackCount > 0;
    }

    synchronized long loadSuccessCountForTesting() {
        return loadSuccessCount;
    }

    synchronized long loadFailureCountForTesting() {
        return loadFailureCount;
    }

    synchronized long saveSuccessCountForTesting() {
        return saveSuccessCount;
    }

    synchronized long saveFailureCountForTesting() {
        return saveFailureCount;
    }

    synchronized long missingFallbackCountForTesting() {
        return missingFallbackCount;
    }

    synchronized long corruptFallbackCountForTesting() {
        return corruptFallbackCount;
    }

    File progressFileForTesting() {
        return progressFile;
    }

    File temporaryFileForTesting() {
        return temporaryFile;
    }

    static int recordBytesForTesting() {
        return RECORD_BYTES;
    }

    static int maximumFileBytesForTesting() {
        return MAX_FILE_BYTES;
    }

    static void clearForTesting(Context context) {
        OctavoProgressStore store = new OctavoProgressStore(context);
        if (store.temporaryFile.exists()
            && !store.temporaryFile.delete()) {
            throw new IllegalStateException(
                "Unable to clear temporary progress state");
        }
        if (store.progressFile.exists() && !store.progressFile.delete()) {
            throw new IllegalStateException(
                "Unable to clear progress state");
        }
        String[] remaining = store.rootDirectory.list();
        if (remaining != null && remaining.length == 0
            && !store.rootDirectory.delete()
            && store.rootDirectory.exists()) {
            throw new IllegalStateException(
                "Unable to clear progress directory");
        }
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context application = context.getApplicationContext();
        return (application == null ? context : application).getFilesDir();
    }

    private static byte[] encode(OctavoProgressDisplay display)
        throws IOException {
        if (display == null) {
            throw new IOException("Missing progress display");
        }
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_BYTES)
            .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(STORE_MAGIC);
        buffer.putInt(STORE_VERSION);
        buffer.putInt(FIELD_COUNT);
        buffer.putInt(display.nativeId());
        byte[] bytes = buffer.array();
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, RECORD_BYTES - Integer.BYTES);
        buffer.putInt((int)checksum.getValue());
        if (bytes.length <= 0 || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Progress record exceeds its bound");
        }
        return bytes;
    }

    private static OctavoProgressDisplay decode(byte[] bytes) {
        if (bytes == null || bytes.length != RECORD_BYTES) {
            return null;
        }
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, RECORD_BYTES - Integer.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int fieldCount = buffer.getInt();
        int nativeId = buffer.getInt();
        int storedChecksum = buffer.getInt();
        if (buffer.hasRemaining()
            || magic != STORE_MAGIC
            || version != STORE_VERSION
            || fieldCount != FIELD_COUNT
            || storedChecksum != (int)checksum.getValue()) {
            return null;
        }
        return OctavoProgressDisplay.fromNativeId(nativeId);
    }

    private static boolean isRecognizableFuture(byte[] bytes) {
        if (bytes == null || bytes.length < 2 * Integer.BYTES
            || bytes.length > MAX_FILE_BYTES) {
            return false;
        }
        ByteBuffer prefix = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return prefix.getInt() == STORE_MAGIC
            && Integer.compareUnsigned(prefix.getInt(), STORE_VERSION) > 0;
    }

    private boolean hasRecognizableFutureRecord() {
        if (!progressFile.exists()) {
            return false;
        }
        try {
            return isRecognizableFuture(readBounded(progressFile));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() < 0
            || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Invalid progress file length");
        }
        byte[] bounded = new byte[MAX_FILE_BYTES + 1];
        int count = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (count < bounded.length) {
                int read = input.read(bounded, count, bounded.length - count);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    count += read;
                }
            }
        }
        if (count != file.length()) {
            throw new IOException("Progress file changed while reading");
        }
        return Arrays.copyOf(bounded, count);
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
}
