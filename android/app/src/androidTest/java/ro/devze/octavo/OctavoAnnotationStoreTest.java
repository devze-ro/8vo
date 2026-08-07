package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoAnnotationStoreTest {
    private static final String DIGEST =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ACTOR_A =
        "11111111111111111111111111111111";
    private static final String ACTOR_B =
        "22222222222222222222222222222222";
    private static final String ACTOR_C =
        "33333333333333333333333333333333";
    private static final String ACTOR_D =
        "44444444444444444444444444444444";
    private static final String ACTOR_E =
        "55555555555555555555555555555555";
    private static final String ACTOR_F =
        "66666666666666666666666666666666";
    private static final String NOTE_ID =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private File testRoot;

    @Before
    public void createIsolatedRoot() {
        Context context = ApplicationProvider.getApplicationContext();
        testRoot = new File(context.getCacheDir(),
            "octavo-annotation-store-" + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedRoot() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void bookmarkIdentityAtomicRoundTripRemovalAndRestart()
        throws IOException {
        File files = directory("round-trip");
        OctavoAnnotationStore store =
            new OctavoAnnotationStore(files, ACTOR_A);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     store.load());
        assertEquals(
            OctavoAnnotationStore.bookmarkRecordIdForTesting(
                DIGEST, 3, 144),
            OctavoAnnotationStore.bookmarkRecordIdForTesting(
                DIGEST, 3, 144));
        assertNotEquals(
            OctavoAnnotationStore.bookmarkRecordIdForTesting(
                DIGEST, 3, 144),
            OctavoAnnotationStore.bookmarkRecordIdForTesting(
                DIGEST, 3, 145));

        assertEquals(OctavoAnnotationStore.MutationResult.ADDED,
                     store.toggleBookmark(
                         DIGEST, 3, 144, "Bookmark at 25%", "Alpha text"));
        assertTrue(store.isBookmarked(DIGEST, 3, 144));
        assertEquals(1, store.bookmarks(DIGEST).size());
        byte[] added = readFile(store.stateFileForTesting());
        assertTrue(added.length > 0);
        assertTrue(added.length
                   <= OctavoAnnotationStore.maximumFileBytesForTesting());
        assertFalse(store.temporaryFileForTesting().exists());

        OctavoAnnotationStore restarted =
            new OctavoAnnotationStore(files, ACTOR_B);
        assertEquals(OctavoAnnotationStore.LoadStatus.LOADED,
                     restarted.load());
        List<OctavoAnnotationStore.Bookmark> bookmarks =
            restarted.bookmarks(DIGEST);
        assertEquals(1, bookmarks.size());
        assertEquals(3, bookmarks.get(0).spineIndex);
        assertEquals(144, bookmarks.get(0).byteOffset);
        assertEquals("Bookmark at 25%", bookmarks.get(0).label);
        assertEquals("Alpha text", bookmarks.get(0).excerpt);

        assertEquals(OctavoAnnotationStore.MutationResult.REMOVED,
                     restarted.removeBookmark(bookmarks.get(0).recordId));
        assertFalse(restarted.isBookmarked(DIGEST, 3, 144));
        assertTrue(restarted.bookmarks(DIGEST).isEmpty());

        OctavoAnnotationStore removedRestart =
            new OctavoAnnotationStore(files, ACTOR_C);
        assertEquals(OctavoAnnotationStore.LoadStatus.LOADED,
                     removedRestart.load());
        assertFalse(removedRestart.isBookmarked(DIGEST, 3, 144));
        assertEquals(1, removedRestart.recordCountForTesting());
        assertFalse(Arrays.equals(
            added, readFile(removedRestart.stateFileForTesting())));
    }

    @Test
    public void failedPublishRollsBackBytesCounterAndProjection()
        throws IOException {
        File files = directory("rollback");
        OctavoAnnotationStore store =
            new OctavoAnnotationStore(files, ACTOR_A);
        store.load();
        assertEquals(OctavoAnnotationStore.MutationResult.ADDED,
                     store.toggleBookmark(
                         DIGEST, 1, 10, "Bookmark at 1%", "Before"));
        byte[] published = readFile(store.stateFileForTesting());
        byte[] canonical = store.canonicalBytesForTesting();

        store.failNextPublishForTesting();
        assertEquals(OctavoAnnotationStore.MutationResult.FAILED,
                     store.toggleBookmark(
                         DIGEST, 1, 10, "Bookmark at 1%", "Before"));
        assertTrue(store.isBookmarked(DIGEST, 1, 10));
        assertArrayEquals(published, readFile(store.stateFileForTesting()));
        assertArrayEquals(canonical, store.canonicalBytesForTesting());
        assertFalse(store.temporaryFileForTesting().exists());

        assertEquals(OctavoAnnotationStore.MutationResult.REMOVED,
                     store.toggleBookmark(
                         DIGEST, 1, 10, "Bookmark at 1%", "Before"));
        assertFalse(store.isBookmarked(DIGEST, 1, 10));
    }

    @Test
    public void corruptStateIsQuarantinedAndFutureStateIsPreserved()
        throws IOException {
        File corruptFiles = directory("corrupt");
        OctavoAnnotationStore corrupt =
            new OctavoAnnotationStore(corruptFiles, ACTOR_A);
        File corruptParent = corrupt.stateFileForTesting().getParentFile();
        assertNotNull(corruptParent);
        assertTrue(corruptParent.mkdirs());
        byte[] malformed = new byte[] {1, 2, 3, 4, 5, 6, 7};
        writeFile(corrupt.stateFileForTesting(), malformed);
        assertEquals(
            OctavoAnnotationStore.LoadStatus.CORRUPT_QUARANTINED,
            corrupt.load());
        assertFalse(corrupt.mutationsBlocked());
        assertFalse(corrupt.stateFileForTesting().exists());
        assertArrayEquals(
            malformed, readFile(corrupt.quarantineFileForTesting(1)));
        assertEquals(OctavoAnnotationStore.MutationResult.ADDED,
                     corrupt.toggleBookmark(
                         DIGEST, 0, 0, "Bookmark at start", "Start"));

        File futureFiles = directory("future");
        OctavoAnnotationStore template =
            new OctavoAnnotationStore(futureFiles, ACTOR_A);
        template.load();
        assertTrue(template.toggleBookmark(
            DIGEST, 0, 1, "Bookmark", "Future").succeeded());
        byte[] future = readFile(template.stateFileForTesting());
        putInt(future, Integer.BYTES,
               OctavoAnnotationStore.currentStoreVersionForTesting() + 1);
        updateChecksum(future);
        writeFile(template.stateFileForTesting(), future);

        OctavoAnnotationStore blocked =
            new OctavoAnnotationStore(futureFiles, ACTOR_B);
        assertEquals(
            OctavoAnnotationStore.LoadStatus.FUTURE_VERSION_BLOCKED,
            blocked.load());
        assertTrue(blocked.mutationsBlocked());
        assertEquals(OctavoAnnotationStore.MutationResult.BLOCKED,
                     blocked.toggleBookmark(
                         DIGEST, 0, 2, "Bookmark", "Blocked"));
        assertArrayEquals(future, readFile(blocked.stateFileForTesting()));
        assertFalse(blocked.quarantineFileForTesting(1).exists());
    }

    @Test
    public void mergeIsCommutativeIdempotentAndRetainsPutDeleteConflict()
        throws IOException {
        OctavoAnnotationStore left = store("merge-left", ACTOR_A);
        assertTrue(left.toggleBookmark(
            DIGEST, 4, 200, "Original", "Seed").succeeded());
        OctavoAnnotationStore right = store("merge-right", ACTOR_B);
        assertTrue(right.mergePortableState(
            left.exportPortableState()).succeeded());

        assertEquals(OctavoAnnotationStore.MutationResult.REMOVED,
                     left.toggleBookmark(
                         DIGEST, 4, 200, "Original", "Seed"));
        assertEquals(OctavoAnnotationStore.MutationResult.UPDATED,
                     right.putBookmarkForTesting(
                         DIGEST, 4, 200, "Edited", "Concurrent edit"));

        OctavoAnnotationStore leftThenRight =
            store("merge-lr", ACTOR_C);
        assertTrue(leftThenRight.mergePortableState(
            left.exportPortableState()).succeeded());
        assertTrue(leftThenRight.mergePortableState(
            right.exportPortableState()).succeeded());
        byte[] canonical =
            leftThenRight.portableCanonicalBytesForTesting();
        assertEquals(1, leftThenRight.bookmarks(DIGEST).size());
        assertTrue(leftThenRight.bookmarks(DIGEST).get(0).conflicted);
        assertEquals("Edited",
                     leftThenRight.bookmarks(DIGEST).get(0).label);

        OctavoAnnotationStore rightThenLeft =
            store("merge-rl", ACTOR_D);
        assertTrue(rightThenLeft.mergePortableState(
            right.exportPortableState()).succeeded());
        assertTrue(rightThenLeft.mergePortableState(
            left.exportPortableState()).succeeded());
        assertArrayEquals(
            canonical, rightThenLeft.portableCanonicalBytesForTesting());

        assertTrue(leftThenRight.mergePortableState(
            right.exportPortableState()).succeeded());
        assertArrayEquals(
            canonical, leftThenRight.portableCanonicalBytesForTesting());
    }

    @Test
    public void concurrentNoteBodiesRemainRecoverableAndMergeIsAssociative()
        throws IOException {
        OctavoAnnotationStore first = store("notes-first", ACTOR_A);
        assertTrue(first.putNoteForTesting(
            NOTE_ID, DIGEST, 2, 50, "Seed note").succeeded());
        OctavoAnnotationStore second = store("notes-second", ACTOR_B);
        assertTrue(second.mergePortableState(
            first.exportPortableState()).succeeded());
        OctavoAnnotationStore third = store("notes-third", ACTOR_C);
        assertTrue(third.mergePortableState(
            first.exportPortableState()).succeeded());
        assertTrue(first.putNoteForTesting(
            NOTE_ID, DIGEST, 2, 50, "Alpha edit").succeeded());
        assertTrue(second.putNoteForTesting(
            NOTE_ID, DIGEST, 2, 50, "Beta edit").succeeded());
        assertTrue(third.putNoteForTesting(
            NOTE_ID, DIGEST, 2, 50, "Gamma edit").succeeded());

        OctavoAnnotationStore merged = store("notes-merged", ACTOR_D);
        assertTrue(merged.mergePortableState(
            first.exportPortableState()).succeeded());
        assertTrue(merged.mergePortableState(
            second.exportPortableState()).succeeded());
        assertTrue(merged.mergePortableState(
            third.exportPortableState()).succeeded());
        assertEquals(Arrays.asList(
                         "Alpha edit", "Beta edit", "Gamma edit"),
                     merged.noteBodiesForTesting(NOTE_ID));

        OctavoAnnotationStore grouped = store("notes-grouped", ACTOR_E);
        assertTrue(grouped.mergePortableState(
            second.exportPortableState()).succeeded());
        assertTrue(grouped.mergePortableState(
            third.exportPortableState()).succeeded());
        OctavoAnnotationStore associated =
            store("notes-associated", ACTOR_F);
        assertTrue(associated.mergePortableState(
            first.exportPortableState()).succeeded());
        assertTrue(associated.mergePortableState(
            grouped.exportPortableState()).succeeded());
        assertArrayEquals(
            merged.portableCanonicalBytesForTesting(),
            associated.portableCanonicalBytesForTesting());
    }

    private OctavoAnnotationStore store(String name, String actor) {
        OctavoAnnotationStore store =
            new OctavoAnnotationStore(directory(name), actor);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING,
                     store.load());
        return store;
    }

    private File directory(String name) {
        File result = new File(testRoot, name);
        assertTrue(result.mkdirs());
        return result;
    }

    private static byte[] readFile(File file) throws IOException {
        assertTrue(file.isFile());
        assertTrue(file.length() <= Integer.MAX_VALUE);
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
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static void updateChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        putInt(bytes, bytes.length - Integer.BYTES,
               (int)checksum.getValue());
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
