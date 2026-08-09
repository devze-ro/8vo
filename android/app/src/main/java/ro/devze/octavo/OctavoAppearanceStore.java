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

/**
 * Bounded, durable storage for Port 7's single global appearance.
 *
 * Per-book overrides are intentionally absent until their ownership,
 * migration, and removal semantics receive a separate contract.
 */
final class OctavoAppearanceStore {
    enum LoadStatus {
        MISSING,
        CURRENT,
        LEGACY,
        MIGRATION_PENDING,
        CORRUPT
    }

    private static final int STORE_MAGIC = 0x4F375354; // "O7ST"
    /*
     * Versions 1 and 2 predate preference-origin metadata. Version 1 used an
     * 18sp schema default; version 2 used 16sp. During the version-2 rollout,
     * however, an inherited version-1 18sp value could be republished as
     * version 2 without recording its origin. That makes version-2 18sp
     * inherently ambiguous: it can be either the inherited default or an
     * explicit choice. The bounded one-time policy therefore migrates
     * version-1 18sp and version-2 16sp or 18sp to the current 16sp default,
     * retaining every other appearance field. Other pre-v3 sizes and every
     * version-3 size remain exact. All versions retain the bounded 60-byte
     * record and native payload version.
     */
    private static final int LEGACY_STORE_VERSION_18SP_DEFAULT = 1;
    private static final int PREVIOUS_STORE_VERSION_16SP_DEFAULT = 2;
    private static final int STORE_VERSION = 3;
    private static final int MAX_FILE_BYTES = 256;
    private static final int HEADER_INT_COUNT = 3;
    private static final int CHECKSUM_INT_COUNT = 1;
    private static final int RECORD_BYTES =
        (HEADER_INT_COUNT + OctavoAppearance.NATIVE_FIELD_COUNT
         + CHECKSUM_INT_COUNT) * Integer.BYTES;
    private static final String ROOT_DIRECTORY = "port7";
    private static final String APPEARANCE_FILE = "appearance.v1";
    private static final String TEMPORARY_FILE = "appearance.v1.tmp";

    private final File rootDirectory;
    private final File appearanceFile;
    private final File temporaryFile;
    private OctavoAppearance current = OctavoAppearance.defaults();
    private long loadSuccessCount;
    private long loadFailureCount;
    private long saveSuccessCount;
    private long saveFailureCount;
    private long missingFallbackCount;
    private long corruptFallbackCount;
    private boolean pendingMigration;
    private LoadStatus loadStatus = LoadStatus.MISSING;

    OctavoAppearanceStore(Context context) {
        this(requireFilesDirectory(context));
    }

    OctavoAppearanceStore(File filesDirectory) {
        if (filesDirectory == null) {
            throw new IllegalArgumentException("Missing files directory");
        }
        rootDirectory = new File(filesDirectory, ROOT_DIRECTORY);
        appearanceFile = new File(rootDirectory, APPEARANCE_FILE);
        temporaryFile = new File(rootDirectory, TEMPORARY_FILE);
    }

    synchronized OctavoAppearance load() {
        pendingMigration = false;
        if (!appearanceFile.exists()) {
            current = OctavoAppearance.defaults();
            loadStatus = LoadStatus.MISSING;
            missingFallbackCount += 1;
            return current;
        }

        try {
            byte[] bytes = readBounded(appearanceFile);
            DecodedRecord decoded = decode(bytes);
            if (decoded == null) {
                throw new IOException("Invalid Port 7 appearance record");
            }
            OctavoAppearance loaded = decoded.appearance;
            if (requiresFontSizeMigration(decoded.storeVersion, loaded)) {
                loaded = loaded.withFontSizeSp(
                    OctavoAppearance.defaults().fontSizeSp());
                pendingMigration = true;
            }
            current = loaded;
            loadStatus = pendingMigration
                ? LoadStatus.MIGRATION_PENDING
                : decoded.storeVersion == STORE_VERSION
                    ? LoadStatus.CURRENT : LoadStatus.LEGACY;
            loadSuccessCount += 1;
        } catch (IOException | RuntimeException exception) {
            current = OctavoAppearance.defaults();
            pendingMigration = false;
            loadStatus = LoadStatus.CORRUPT;
            loadFailureCount += 1;
            corruptFallbackCount += 1;
        }
        return current;
    }

    synchronized boolean save(OctavoAppearance candidate) {
        if (candidate == null) {
            saveFailureCount += 1;
            return false;
        }

        byte[] bytes;
        try {
            bytes = encode(candidate);
            requireDirectory(rootDirectory);
            try (FileOutputStream output =
                     new FileOutputStream(temporaryFile, false)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            publishAtomically(temporaryFile, appearanceFile);
        } catch (IOException | RuntimeException exception) {
            deleteTemporaryBestEffort();
            saveFailureCount += 1;
            return false;
        }

        current = candidate;
        pendingMigration = false;
        loadStatus = LoadStatus.CURRENT;
        saveSuccessCount += 1;
        return true;
    }

    synchronized OctavoAppearance current() {
        return current;
    }

    synchronized boolean hasPendingMigration() {
        return pendingMigration;
    }

    synchronized LoadStatus loadStatus() {
        return loadStatus;
    }

    /**
     * Verifies canonical current-version bytes without substituting an
     * in-memory default for a missing, corrupt, or legacy record.
     */
    synchronized boolean hasCanonicalCurrentRecord(
        OctavoAppearance expected) {
        if (expected == null) {
            return false;
        }
        try {
            DecodedRecord decoded = decode(readBounded(appearanceFile));
            return decoded != null
                && decoded.storeVersion == STORE_VERSION
                && expected.equals(decoded.appearance);
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

    File appearanceFileForTesting() {
        return appearanceFile;
    }

    File temporaryFileForTesting() {
        return temporaryFile;
    }

    static int maximumFileBytesForTesting() {
        return MAX_FILE_BYTES;
    }

    static int recordBytesForTesting() {
        return RECORD_BYTES;
    }

    static int currentStoreVersionForTesting() {
        return STORE_VERSION;
    }

    static int legacyStoreVersionForTesting() {
        return LEGACY_STORE_VERSION_18SP_DEFAULT;
    }

    static int previousStoreVersionForTesting() {
        return PREVIOUS_STORE_VERSION_16SP_DEFAULT;
    }

    static byte[] legacyRecordForTesting(OctavoAppearance appearance)
        throws IOException {
        return encode(appearance, LEGACY_STORE_VERSION_18SP_DEFAULT);
    }

    static byte[] previousRecordForTesting(OctavoAppearance appearance)
        throws IOException {
        return encode(appearance, PREVIOUS_STORE_VERSION_16SP_DEFAULT);
    }

    static void clearForTesting(Context context) {
        OctavoAppearanceStore store = new OctavoAppearanceStore(context);
        if (store.temporaryFile.exists()
            && !store.temporaryFile.delete()) {
            throw new IllegalStateException(
                "Unable to clear temporary appearance state");
        }
        if (store.appearanceFile.exists()
            && !store.appearanceFile.delete()) {
            throw new IllegalStateException(
                "Unable to clear appearance state");
        }
        String[] remaining = store.rootDirectory.list();
        if (remaining != null && remaining.length == 0
            && !store.rootDirectory.delete()
            && store.rootDirectory.exists()) {
            throw new IllegalStateException(
                "Unable to clear appearance directory");
        }
    }

    private static File requireFilesDirectory(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context application = context.getApplicationContext();
        return (application == null ? context : application).getFilesDir();
    }

    private static byte[] encode(OctavoAppearance appearance)
        throws IOException {
        return encode(appearance, STORE_VERSION);
    }

    private static byte[] encode(OctavoAppearance appearance,
                                 int storeVersion)
        throws IOException {
        if (appearance == null
            || (storeVersion != STORE_VERSION
                && storeVersion != PREVIOUS_STORE_VERSION_16SP_DEFAULT
                && storeVersion != LEGACY_STORE_VERSION_18SP_DEFAULT)
            || !appearanceIsValidForStoreVersion(
                appearance, storeVersion)) {
            throw new IOException();
        }
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_BYTES)
            .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(STORE_MAGIC);
        buffer.putInt(storeVersion);
        buffer.putInt(OctavoAppearance.NATIVE_FIELD_COUNT);
        for (int value : appearance.nativeConfig()) {
            buffer.putInt(value);
        }

        byte[] bytes = buffer.array();
        int checksumOffset = RECORD_BYTES - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, checksumOffset);
        buffer.position(checksumOffset);
        buffer.putInt((int)checksum.getValue());
        if (bytes.length <= 0 || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Appearance record exceeds its bound");
        }
        return bytes;
    }

    private static boolean appearanceIsValidForStoreVersion(
        OctavoAppearance appearance,
        int storeVersion) {
        return appearance != null
            && (storeVersion == STORE_VERSION
                || appearance.fontSizeSp() != 14);
    }

    private static boolean requiresFontSizeMigration(
        int storeVersion,
        OctavoAppearance appearance) {
        if (appearance == null) {
            return false;
        }
        return (storeVersion == LEGACY_STORE_VERSION_18SP_DEFAULT
                && appearance.fontSizeSp() == 18)
            || (storeVersion == PREVIOUS_STORE_VERSION_16SP_DEFAULT
                && (appearance.fontSizeSp() == 16
                    || appearance.fontSizeSp() == 18));
    }

    private static DecodedRecord decode(byte[] bytes) {
        if (bytes == null || bytes.length != RECORD_BYTES) {
            return null;
        }

        int checksumOffset = RECORD_BYTES - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, checksumOffset);

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int fieldCount = buffer.getInt();
        if (magic != STORE_MAGIC
            || (version != STORE_VERSION
                && version != PREVIOUS_STORE_VERSION_16SP_DEFAULT
                && version != LEGACY_STORE_VERSION_18SP_DEFAULT)
            || fieldCount != OctavoAppearance.NATIVE_FIELD_COUNT) {
            return null;
        }

        int[] config = new int[OctavoAppearance.NATIVE_FIELD_COUNT];
        for (int index = 0; index < config.length; ++index) {
            config[index] = buffer.getInt();
        }
        int storedChecksum = buffer.getInt();
        if (buffer.hasRemaining()
            || storedChecksum != (int)checksum.getValue()) {
            return null;
        }
        OctavoAppearance appearance =
            OctavoAppearance.fromNativeConfig(config);
        return !appearanceIsValidForStoreVersion(appearance, version)
            ? null : new DecodedRecord(version, appearance);
    }

    private static byte[] readBounded(File file) throws IOException {
        if (!file.isFile() || file.length() != RECORD_BYTES
            || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Invalid appearance file length");
        }

        byte[] bounded = new byte[MAX_FILE_BYTES + 1];
        int count = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (count < bounded.length) {
                int read = input.read(bounded, count, bounded.length - count);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                count += read;
            }
        }
        if (count != RECORD_BYTES) {
            throw new IOException("Appearance file changed while reading");
        }
        return Arrays.copyOf(bounded, count);
    }

    private static void requireDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(
                "Unable to create " + directory.getAbsolutePath());
        }
    }

    private static void publishAtomically(File temporary, File destination)
        throws IOException {
        Files.move(temporary.toPath(),
                   destination.toPath(),
                   StandardCopyOption.ATOMIC_MOVE,
                   StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteTemporaryBestEffort() {
        if (temporaryFile.isFile()) {
            temporaryFile.delete();
        }
    }

    private static final class DecodedRecord {
        final int storeVersion;
        final OctavoAppearance appearance;

        DecodedRecord(int storeVersion, OctavoAppearance appearance) {
            this.storeVersion = storeVersion;
            this.appearance = appearance;
        }
    }
}
