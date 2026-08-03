package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoProgressStoreTest {
    private static final int EXPECTED_MAGIC = 0x4F385047;
    private static final int EXPECTED_VERSION = 1;
    private static final int EXPECTED_FIELD_COUNT = 1;
    private static final int MAGIC_OFFSET = 0;
    private static final int VERSION_OFFSET = Integer.BYTES;
    private static final int FIELD_COUNT_OFFSET = 2 * Integer.BYTES;
    private static final int MODE_OFFSET = 3 * Integer.BYTES;

    private File testFilesDirectory;

    @Before
    public void createIsolatedFilesDirectory() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context.getCacheDir());
        testFilesDirectory = new File(
            context.getCacheDir(),
            "octavo-progress-store-" + System.nanoTime());
        assertFalse(testFilesDirectory.exists());
        assertTrue(testFilesDirectory.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testFilesDirectory));
    }

    @Test
    public void displayDefaultsNativeIdsAndLabelsAreStable() {
        OctavoProgressDisplay[] displays = OctavoProgressDisplay.values();
        assertEquals(4, displays.length);
        assertSame(OctavoProgressDisplay.PERCENTAGE,
                   OctavoProgressDisplay.defaults());

        OctavoProgressDisplay[] expectedDisplays = {
            OctavoProgressDisplay.CHAPTER,
            OctavoProgressDisplay.PAGE,
            OctavoProgressDisplay.LOCATION,
            OctavoProgressDisplay.PERCENTAGE,
        };
        String[] expectedLabels = {
            "Chapter", "Page", "Location", "Percentage"
        };
        for (int index = 0; index < expectedDisplays.length; ++index) {
            assertSame(expectedDisplays[index], displays[index]);
            assertEquals(index, displays[index].nativeId());
            assertEquals(expectedLabels[index], displays[index].label());
            assertSame(displays[index],
                       OctavoProgressDisplay.fromNativeId(index));
        }
        assertNull(OctavoProgressDisplay.fromNativeId(-1));
        assertNull(OctavoProgressDisplay.fromNativeId(displays.length));
        assertNull(OctavoProgressDisplay.fromNativeId(Integer.MIN_VALUE));
        assertNull(OctavoProgressDisplay.fromNativeId(Integer.MAX_VALUE));
    }

    @Test
    public void missingLoadUsesDefaultWithoutPublishingAndCountsFallback()
        throws IOException {
        OctavoProgressStore store =
            new OctavoProgressStore(testFilesDirectory);
        File expectedFile = new File(
            new File(testFilesDirectory, "port8"), "progress.v1");

        assertEquals(expectedFile.getCanonicalFile(),
                     store.progressFileForTesting().getCanonicalFile());
        assertSame(OctavoProgressDisplay.PERCENTAGE, store.current());
        assertSame(OctavoProgressDisplay.PERCENTAGE, store.load());
        assertSame(OctavoProgressDisplay.PERCENTAGE, store.current());
        assertEquals(0, store.loadSuccessCountForTesting());
        assertEquals(0, store.loadFailureCountForTesting());
        assertEquals(0, store.saveSuccessCountForTesting());
        assertEquals(0, store.saveFailureCountForTesting());
        assertEquals(1, store.missingFallbackCountForTesting());
        assertEquals(0, store.corruptFallbackCountForTesting());
        assertFalse(store.recoveredFromCorruption());
        assertFalse(store.progressFileForTesting().exists());
        assertFalse(store.temporaryFileForTesting().exists());
    }

    @Test
    public void everyDisplayPublishesAtomicallyAndRoundTripsExactBytes()
        throws IOException {
        assertEquals(5 * Integer.BYTES,
                     OctavoProgressStore.recordBytesForTesting());
        assertTrue(OctavoProgressStore.recordBytesForTesting() > 0);
        assertTrue(OctavoProgressStore.recordBytesForTesting()
                   <= OctavoProgressStore.maximumFileBytesForTesting());

        for (OctavoProgressDisplay display
                 : OctavoProgressDisplay.values()) {
            File filesDirectory = new File(
                testFilesDirectory, "mode-" + display.nativeId());
            assertTrue(filesDirectory.mkdirs());
            OctavoProgressStore store =
                new OctavoProgressStore(filesDirectory);

            assertTrue(store.save(display));
            assertSame(display, store.current());
            assertEquals(1, store.saveSuccessCountForTesting());
            assertEquals(0, store.saveFailureCountForTesting());
            assertEquals(0, store.loadSuccessCountForTesting());
            assertTrue(store.progressFileForTesting().isFile());
            assertEquals(OctavoProgressStore.recordBytesForTesting(),
                         store.progressFileForTesting().length());
            assertFalse(store.temporaryFileForTesting().exists());
            assertOnlyPublishedRecord(store);

            byte[] published = readFile(store.progressFileForTesting());
            assertRecord(published, display);

            OctavoProgressStore reloaded =
                new OctavoProgressStore(filesDirectory);
            assertSame(display, reloaded.load());
            assertSame(display, reloaded.current());
            assertEquals(1, reloaded.loadSuccessCountForTesting());
            assertEquals(0, reloaded.loadFailureCountForTesting());
            assertEquals(0, reloaded.missingFallbackCountForTesting());
            assertEquals(0, reloaded.corruptFallbackCountForTesting());
            assertFalse(reloaded.recoveredFromCorruption());

            assertTrue(reloaded.save(display));
            assertArrayEquals(
                published, readFile(reloaded.progressFileForTesting()));
            assertFalse(reloaded.temporaryFileForTesting().exists());
            assertOnlyPublishedRecord(reloaded);
        }
    }

    @Test
    public void malformedRecordsAreRejectedWithoutRewritingThem()
        throws IOException {
        OctavoProgressStore templateStore =
            new OctavoProgressStore(testFilesDirectory);
        assertTrue(templateStore.save(OctavoProgressDisplay.LOCATION));
        byte[] valid = readFile(templateStore.progressFileForTesting());

        byte[] truncated = new byte[valid.length - 1];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);
        byte[] oversized = new byte[
            OctavoProgressStore.maximumFileBytesForTesting() + 1];
        byte[] badChecksum = valid.clone();
        badChecksum[badChecksum.length - 1] ^= 0x01;
        byte[] wrongMode = withIntAndChecksum(valid, MODE_OFFSET, 99);
        byte[] wrongMagic = withIntAndChecksum(
            valid, MAGIC_OFFSET, EXPECTED_MAGIC ^ 0x01);
        byte[] wrongVersion = withIntAndChecksum(
            valid, VERSION_OFFSET, EXPECTED_VERSION + 1);
        byte[] wrongFieldCount = withIntAndChecksum(
            valid, FIELD_COUNT_OFFSET, EXPECTED_FIELD_COUNT + 1);
        byte[][] malformed = {
            truncated,
            oversized,
            badChecksum,
            wrongMode,
            wrongMagic,
            wrongVersion,
            wrongFieldCount,
        };

        for (int index = 0; index < malformed.length; ++index) {
            File filesDirectory = new File(
                testFilesDirectory, "malformed-" + index);
            assertTrue(filesDirectory.mkdirs());
            OctavoProgressStore store =
                new OctavoProgressStore(filesDirectory);
            File parent = store.progressFileForTesting().getParentFile();
            assertNotNull(parent);
            assertTrue(parent.mkdirs());
            writeFile(store.progressFileForTesting(), malformed[index]);

            assertSame(OctavoProgressDisplay.PERCENTAGE, store.load());
            assertSame(OctavoProgressDisplay.PERCENTAGE, store.current());
            assertEquals(0, store.loadSuccessCountForTesting());
            assertEquals(1, store.loadFailureCountForTesting());
            assertEquals(0, store.saveSuccessCountForTesting());
            assertEquals(0, store.saveFailureCountForTesting());
            assertEquals(0, store.missingFallbackCountForTesting());
            assertEquals(1, store.corruptFallbackCountForTesting());
            assertTrue(store.recoveredFromCorruption());
            assertArrayEquals(
                malformed[index], readFile(store.progressFileForTesting()));
            assertFalse(store.temporaryFileForTesting().exists());
        }
    }

    @Test
    public void failedSavePreservesPublishedBytesAndCurrentChoice()
        throws IOException {
        OctavoProgressStore store =
            new OctavoProgressStore(testFilesDirectory);
        assertTrue(store.save(OctavoProgressDisplay.LOCATION));
        byte[] published = readFile(store.progressFileForTesting());
        assertTrue(store.temporaryFileForTesting().mkdir());

        assertFalse(store.save(OctavoProgressDisplay.CHAPTER));
        assertSame(OctavoProgressDisplay.LOCATION, store.current());
        assertEquals(1, store.saveSuccessCountForTesting());
        assertEquals(1, store.saveFailureCountForTesting());
        assertArrayEquals(published, readFile(store.progressFileForTesting()));
        assertTrue(store.temporaryFileForTesting().isDirectory());

        assertFalse(store.save(null));
        assertSame(OctavoProgressDisplay.LOCATION, store.current());
        assertEquals(1, store.saveSuccessCountForTesting());
        assertEquals(2, store.saveFailureCountForTesting());
        assertArrayEquals(published, readFile(store.progressFileForTesting()));
        assertTrue(store.temporaryFileForTesting().delete());

        assertTrue(store.save(OctavoProgressDisplay.CHAPTER));
        assertSame(OctavoProgressDisplay.CHAPTER, store.current());
        assertEquals(2, store.saveSuccessCountForTesting());
        assertEquals(2, store.saveFailureCountForTesting());
        assertFalse(store.temporaryFileForTesting().exists());
        assertOnlyPublishedRecord(store);

        OctavoProgressStore reloaded =
            new OctavoProgressStore(testFilesDirectory);
        assertSame(OctavoProgressDisplay.CHAPTER, reloaded.load());
        assertEquals(1, reloaded.loadSuccessCountForTesting());
        assertEquals(0, reloaded.loadFailureCountForTesting());
    }

    @Test
    public void failedAtomicPublishRemovesTemporaryBytesAndAllowsRetry()
        throws IOException {
        File filesDirectory = new File(testFilesDirectory, "blocked-target");
        assertTrue(filesDirectory.mkdirs());
        OctavoProgressStore store =
            new OctavoProgressStore(filesDirectory);
        File target = store.progressFileForTesting();
        File parent = target.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.mkdirs());
        assertTrue(target.mkdir());
        File sentinel = new File(target, "keep");
        writeFile(sentinel, new byte[] {1});

        assertFalse(store.save(OctavoProgressDisplay.PAGE));
        assertSame(OctavoProgressDisplay.PERCENTAGE, store.current());
        assertEquals(0, store.saveSuccessCountForTesting());
        assertEquals(1, store.saveFailureCountForTesting());
        assertTrue(target.isDirectory());
        assertTrue(sentinel.isFile());
        assertFalse(store.temporaryFileForTesting().exists());

        assertTrue(sentinel.delete());
        assertTrue(target.delete());
        assertTrue(store.save(OctavoProgressDisplay.PAGE));
        assertSame(OctavoProgressDisplay.PAGE, store.current());
        assertEquals(1, store.saveSuccessCountForTesting());
        assertEquals(1, store.saveFailureCountForTesting());
        assertFalse(store.temporaryFileForTesting().exists());
        assertOnlyPublishedRecord(store);
    }

    private static void assertRecord(byte[] bytes,
                                     OctavoProgressDisplay display) {
        assertNotNull(bytes);
        assertEquals(OctavoProgressStore.recordBytesForTesting(),
                     bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN);
        assertEquals(EXPECTED_MAGIC, buffer.getInt());
        assertEquals(EXPECTED_VERSION, buffer.getInt());
        assertEquals(EXPECTED_FIELD_COUNT, buffer.getInt());
        assertEquals(display.nativeId(), buffer.getInt());
        int storedChecksum = buffer.getInt();
        assertFalse(buffer.hasRemaining());
        assertEquals(checksum(bytes), storedChecksum);
    }

    private static byte[] withIntAndChecksum(byte[] source,
                                             int offset,
                                             int value) {
        assertNotNull(source);
        byte[] result = source.clone();
        ByteBuffer buffer = ByteBuffer.wrap(result)
            .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(offset, value);
        buffer.putInt(result.length - Integer.BYTES, checksum(result));
        return result;
    }

    private static int checksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        return (int)checksum.getValue();
    }

    private static void assertOnlyPublishedRecord(
        OctavoProgressStore store) {
        File root = store.progressFileForTesting().getParentFile();
        assertNotNull(root);
        File[] files = root.listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        assertEquals(store.progressFileForTesting(), files[0]);
    }

    private static byte[] readFile(File file) throws IOException {
        assertTrue(file.isFile());
        assertTrue(file.length() >= 0 && file.length() <= Integer.MAX_VALUE);
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    offset += count;
                }
            }
            assertEquals(-1, input.read());
        }
        assertEquals(bytes.length, offset);
        return bytes;
    }

    private static void writeFile(File file, byte[] bytes)
        throws IOException {
        assertNotNull(file);
        assertNotNull(bytes);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static boolean deleteTree(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return false;
            }
            for (File child : children) {
                if (!deleteTree(child)) {
                    return false;
                }
            }
        }
        return file.delete() || !file.exists();
    }
}
