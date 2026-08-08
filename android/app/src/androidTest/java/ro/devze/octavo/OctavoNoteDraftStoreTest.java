package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public final class OctavoNoteDraftStoreTest {
    private static final String RECORD =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String REVISION =
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DIGEST =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private File root;

    @Before
    public void createRoot() {
        Context context = ApplicationProvider.getApplicationContext();
        root = new File(context.getCacheDir(),
            "octavo-note-draft-" + System.nanoTime());
        assertTrue(root.mkdirs());
    }

    @After
    public void removeRoot() {
        assertTrue(deleteTree(root));
    }

    @Test
    public void exactDraftAutosaveRestartFailureRollbackAndClear() {
        OctavoNoteDraftStore store = new OctavoNoteDraftStore(root);
        assertEquals(OctavoNoteDraftStore.LoadStatus.MISSING, store.load());
        OctavoNoteDraftStore.Draft first = draft("alpha βeta");
        assertTrue(store.save(first));
        assertTrue(store.stateFileForTesting().isFile());

        OctavoNoteDraftStore restarted = new OctavoNoteDraftStore(root);
        assertEquals(OctavoNoteDraftStore.LoadStatus.LOADED,
                     restarted.load());
        assertDraft(first, restarted.current());

        restarted.failNextPublishForTesting();
        assertFalse(restarted.save(first.withBody("not published")));
        assertEquals("alpha βeta", restarted.current().body);
        OctavoNoteDraftStore afterFailure = new OctavoNoteDraftStore(root);
        assertEquals(OctavoNoteDraftStore.LoadStatus.LOADED,
                     afterFailure.load());
        assertEquals("alpha βeta", afterFailure.current().body);

        assertTrue(afterFailure.save(first.withBody("saved edit")));
        assertTrue(afterFailure.clear());
        assertNull(afterFailure.current());
        assertFalse(afterFailure.stateFileForTesting().exists());
        assertEquals(OctavoNoteDraftStore.LoadStatus.MISSING,
                     new OctavoNoteDraftStore(root).load());
    }

    @Test
    public void corruptDraftIsQuarantinedWithoutInventingText()
        throws IOException {
        OctavoNoteDraftStore store = new OctavoNoteDraftStore(root);
        File state = store.stateFileForTesting();
        assertNotNull(state.getParentFile());
        assertTrue(state.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(state)) {
            output.write(new byte[] {1, 2, 3, 4, 5});
            output.flush();
            output.getFD().sync();
        }
        assertEquals(OctavoNoteDraftStore.LoadStatus.CORRUPT_QUARANTINED,
                     store.load());
        assertNull(store.current());
        assertFalse(state.exists());
        assertTrue(store.save(draft("replacement")));
    }

    private static OctavoNoteDraftStore.Draft draft(String body) {
        return new OctavoNoteDraftStore.Draft(
            RECORD, REVISION, DIGEST, 3, 120, 160, "",
            "Selected source", body);
    }

    private static void assertDraft(OctavoNoteDraftStore.Draft expected,
                                    OctavoNoteDraftStore.Draft actual) {
        assertNotNull(actual);
        assertEquals(expected.recordId, actual.recordId);
        assertEquals(expected.expectedRevisionToken,
                     actual.expectedRevisionToken);
        assertEquals(expected.bookDigest, actual.bookDigest);
        assertEquals(expected.spineIndex, actual.spineIndex);
        assertEquals(expected.byteStart, actual.byteStart);
        assertEquals(expected.byteEnd, actual.byteEnd);
        assertEquals(expected.excerpt, actual.excerpt);
        assertEquals(expected.body, actual.body);
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
