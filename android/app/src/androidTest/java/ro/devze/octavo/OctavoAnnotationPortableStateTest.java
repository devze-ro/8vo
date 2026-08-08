package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.zip.CRC32;

@RunWith(AndroidJUnit4.class)
public final class OctavoAnnotationPortableStateTest {
    private static final String FIXTURE_DIGEST =
        "5d81c6ba136774cb4addc01dfc88bec355d637456ee6aacb3004983a6f055ed3";
    private static final String OTHER_DIGEST =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ACTOR_ZERO =
        "00000000000000000000000000000000";
    private static final String YELLOW_ID =
        "10000000000000000000000000000001";
    private static final String PINK_ID =
        "10000000000000000000000000000002";
    private static final String BLUE_ID =
        "10000000000000000000000000000003";
    private static final String ORANGE_ID =
        "10000000000000000000000000000004";
    private static final String STANDALONE_NOTE_ID =
        "20000000000000000000000000000001";
    private static final String ATTACHED_NOTE_ID =
        "20000000000000000000000000000002";
    private static final String CONFLICT_NOTE_ID =
        "30000000000000000000000000000001";
    private static final String GOLDEN_SHA256 =
        "e2dba96f2029e9f0d4a989ff12c3ae9b69c6206a9ec26baa9f71acde2545641e";
    private static final String CAUSAL_GOLDEN_SHA256 =
        "470f3fa199e00721d04d697a7b60e625473757fa9d9598ddafe322a1bc4c4a49";
    private static final String CAUSAL_HIGHLIGHT_ID =
        "30000000000000000000000000000010";
    private static final String CAUSAL_TOMBSTONE_NOTE_ID =
        "30000000000000000000000000000020";
    private static final String CAUSAL_CONFLICT_NOTE_ID =
        "30000000000000000000000000000030";
    private static final String CAUSAL_ACTOR_A =
        "11111111111111111111111111111111";
    private static final String CAUSAL_ACTOR_B =
        "22222222222222222222222222222222";
    private static final String CAUSAL_ACTOR_C =
        "33333333333333333333333333333333";

    private File testRoot;

    @Before
    public void createIsolatedRoot() {
        Context context = ApplicationProvider.getApplicationContext();
        testRoot = new File(context.getCacheDir(),
            "octavo-portable-state-" + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void removeIsolatedRoot() {
        assertTrue(deleteTree(testRoot));
    }

    @Test
    public void portableGoldenRoundTripIsCanonicalAndActorNeutral()
        throws IOException {
        byte[] fixture = readBase64Asset(
            "annotations/v1/portable-full.base64");
        assertEquals(GOLDEN_SHA256, sha256Hex(fixture));
        assertEquals(GOLDEN_SHA256, readTextAsset(
            "annotations/v1/portable-full.sha256").trim());
        String oracle = readTextAsset(
            "annotations/v1/portable-full.json");
        assertTrue(oracle.contains(
            "\"decoded_sha256\": \"" + GOLDEN_SHA256 + "\""));
        assertTrue(oracle.contains("\"byte_count\": \"242131\""));
        assertArrayEquals(
            OctavoAnnotationPortableWire.encode(goldenRecords()), fixture);

        OctavoAnnotationStore authored = store("golden-author", ACTOR_ZERO);
        populateGoldenStore(authored);
        assertArrayEquals(fixture, authored.exportPortableBytes());
        assertEquals(7, authored.recordCountForTesting());

        OctavoAnnotationStore first = store("golden-first", actor(40));
        OctavoAnnotationStore second = store("golden-second", actor(41));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     first.mergePortableBytes(fixture));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     second.mergePortableBytes(fixture));
        assertArrayEquals(fixture, first.exportPortableBytes());
        assertArrayEquals(fixture, second.exportPortableBytes());
        assertNotEquals(
            sha256Hex(first.canonicalBytesForTesting()),
            sha256Hex(second.canonicalBytesForTesting()));
        assertEquals(1, first.bookmarks(FIXTURE_DIGEST).size());
        assertEquals(4, first.highlights(FIXTURE_DIGEST).size());
        assertEquals(2, first.notes(FIXTURE_DIGEST).size());
        assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                     first.mergePortableBytes(fixture));
        assertArrayEquals(fixture, first.exportPortableBytes());

        OctavoAnnotationStore restarted = new OctavoAnnotationStore(
            new File(testRoot, "golden-first"), actor(42));
        assertEquals(OctavoAnnotationStore.LoadStatus.LOADED,
                     restarted.load());
        assertArrayEquals(fixture, restarted.exportPortableBytes());
        assertArrayEquals(first.canonicalBytesForTesting(),
                          restarted.canonicalBytesForTesting());
        assertEquals(1, restarted.bookmarks(FIXTURE_DIGEST).size());
        assertEquals(4, restarted.highlights(FIXTURE_DIGEST).size());
        assertEquals(2, restarted.notes(FIXTURE_DIGEST).size());
    }

    @Test
    public void causalGoldenLocksContextsConflictsDeletesAndWideCounters()
        throws IOException {
        byte[] fixture = readBase64Asset(
            "annotations/v1/portable-causal.base64");
        assertEquals(1792, fixture.length);
        assertEquals(CAUSAL_GOLDEN_SHA256, sha256Hex(fixture));
        assertEquals(CAUSAL_GOLDEN_SHA256, readTextAsset(
            "annotations/v1/portable-causal.sha256").trim());
        assertTrue((fixture[fixture.length - Integer.BYTES] & 0x80) != 0);
        assertArrayEquals(
            OctavoAnnotationPortableWire.encode(causalGoldenRecords()),
            fixture);

        String oracle = readTextAsset(
            "annotations/v1/portable-causal.json");
        assertTrue(oracle.contains(
            "\"counter\": \"9007199254740993\""));
        assertTrue(oracle.contains(
            "\"decoded_sha256\": \"" + CAUSAL_GOLDEN_SHA256 + "\""));
        assertTrue(oracle.contains(
            "\"crc32_iso_hdlc\": \"8f2f9462\""));

        OctavoAnnotationStore imported =
            store("causal-golden", actor(43));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     imported.mergePortableBytes(fixture));
        assertArrayEquals(fixture, imported.exportPortableBytes());
        assertEquals(3, imported.recordCountForTesting());
        assertTrue(imported.highlights(FIXTURE_DIGEST).isEmpty());
        assertEquals(Arrays.asList(
            "Conflict A: cafe\u0301\r\nline two",
            "Conflict B: \u039a\u03b1\u03bb\u03b7\u03bc\u03ad\u03c1\u03b1 \ud83d\udc4b"),
            imported.noteBodiesForTesting(CAUSAL_CONFLICT_NOTE_ID));
        assertTrue(imported.noteBodiesForTesting(
            CAUSAL_TOMBSTONE_NOTE_ID).isEmpty());
        assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                     imported.mergePortableBytes(fixture));

        OctavoAnnotationStore restarted = new OctavoAnnotationStore(
            new File(testRoot, "causal-golden"), actor(44));
        assertEquals(OctavoAnnotationStore.LoadStatus.LOADED,
                     restarted.load());
        assertArrayEquals(fixture, restarted.exportPortableBytes());
        assertArrayEquals(imported.canonicalBytesForTesting(),
                          restarted.canonicalBytesForTesting());
    }

    @Test
    public void versionedAttachmentsAndEmptyTombstonesConverge()
        throws IOException {
        String highlightXId = fixedId(900);
        String highlightYId = fixedId(901);
        String noteId = fixedId(902);
        OctavoAnnotationPortableWire.Record highlightX = highlightRecord(
            highlightXId, actor(400), 1, 0, 100, 110, 0, "X range");
        OctavoAnnotationPortableWire.Record noteX = noteRecord(
            noteId, actor(400), 2, 0, 100, 110,
            0, 0, highlightXId, "", "X range", "X body");
        byte[] snapshotX = OctavoAnnotationPortableWire.encode(
            Arrays.asList(highlightX, noteX));

        TreeMap<String, Long> observesX = new TreeMap<>();
        observesX.put(actor(400), 2L);
        OctavoAnnotationPortableWire.Record deleteX =
            new OctavoAnnotationPortableWire.Record(
                noteId, OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        deleteX.add(new OctavoAnnotationPortableWire.Head(
            actor(401), 1, OctavoAnnotationPortableWire.Operation.DELETE,
            observesX, 0, 100, 110, 0, 0,
            "", "", "", ""));
        byte[] snapshotDelete = OctavoAnnotationPortableWire.encode(
            Arrays.asList(highlightX, deleteX));

        OctavoAnnotationPortableWire.Record highlightY = highlightRecord(
            highlightYId, actor(402), 1, 0, 100, 110, 2, "Y range");
        OctavoAnnotationPortableWire.Record noteY = noteRecord(
            noteId, actor(402), 2, 0, 100, 110,
            0, 0, highlightYId, "", "Y range", "Y body");
        byte[] snapshotY = OctavoAnnotationPortableWire.encode(
            Arrays.asList(highlightY, noteY));

        byte[][] snapshots = {snapshotX, snapshotDelete, snapshotY};
        int[][] permutations = {
            {0, 1, 2}, {0, 2, 1}, {1, 0, 2},
            {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
        };
        byte[] converged = null;
        OctavoAnnotationStore resolver = null;
        for (int index = 0; index < permutations.length; ++index) {
            OctavoAnnotationStore candidate = store(
                "attachment-permutation-" + index, actor(450 + index));
            for (int source : permutations[index]) {
                assertTrue(candidate.mergePortableBytes(
                    snapshots[source]).succeeded());
            }
            byte[] bytes = candidate.exportPortableBytes();
            if (converged == null) {
                converged = bytes;
                resolver = candidate;
            } else {
                assertArrayEquals(converged, bytes);
            }
            OctavoAnnotationStore.Note note =
                candidate.notes(FIXTURE_DIGEST).get(0);
            assertEquals(highlightYId, note.attachedHighlightId);
            assertEquals(Collections.singletonList("Y body"),
                         candidate.noteBodiesForTesting(noteId));
            assertTrue(note.conflicted);
        }

        OctavoAnnotationStore.Note projected =
            resolver.notes(FIXTURE_DIGEST).get(0);
        assertEquals(OctavoAnnotationStore.MutationResult.UPDATED,
                     resolver.saveNote(
                         projected.recordId,
                         projected.revisionToken,
                         projected.bookDigest,
                         projected.spineIndex,
                         projected.byteStart,
                         projected.byteEnd,
                         projected.attachedHighlightId,
                         projected.excerpt,
                         "Resolved Y"));
        byte[] resolved = resolver.exportPortableBytes();
        for (byte[] stale : snapshots) {
            assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                         resolver.mergePortableBytes(stale));
        }
        assertArrayEquals(resolved, resolver.exportPortableBytes());
        assertEquals(Collections.singletonList("Resolved Y"),
                     resolver.noteBodiesForTesting(noteId));

        OctavoAnnotationStore restarted = new OctavoAnnotationStore(
            new File(testRoot, "attachment-permutation-0"), actor(499));
        assertEquals(OctavoAnnotationStore.LoadStatus.LOADED,
                     restarted.load());
        assertArrayEquals(resolved, restarted.exportPortableBytes());
    }

    @Test
    public void permutationsConvergeAndStaleReplayCannotResurrect()
        throws IOException {
        OctavoAnnotationStore first = store("perm-first", actor(1));
        assertTrue(first.putNoteForTesting(
            CONFLICT_NOTE_ID, FIXTURE_DIGEST, 2, 80, "Seed").succeeded());
        byte[] seed = first.exportPortableBytes();
        OctavoAnnotationStore second = store("perm-second", actor(2));
        OctavoAnnotationStore third = store("perm-third", actor(3));
        assertTrue(second.mergePortableBytes(seed).succeeded());
        assertTrue(third.mergePortableBytes(seed).succeeded());
        assertTrue(first.putNoteForTesting(
            CONFLICT_NOTE_ID, FIXTURE_DIGEST, 2, 80, "Alpha").succeeded());
        assertTrue(second.putNoteForTesting(
            CONFLICT_NOTE_ID, FIXTURE_DIGEST, 2, 80, "Beta").succeeded());
        assertTrue(third.putNoteForTesting(
            CONFLICT_NOTE_ID, FIXTURE_DIGEST, 2, 80, "Gamma").succeeded());
        byte[][] snapshots = {
            first.exportPortableBytes(),
            second.exportPortableBytes(),
            third.exportPortableBytes()
        };
        int[][] permutations = {
            {0, 1, 2}, {0, 2, 1}, {1, 0, 2},
            {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
        };
        byte[] converged = null;
        OctavoAnnotationStore resolutionStore = null;
        for (int index = 0; index < permutations.length; ++index) {
            OctavoAnnotationStore target = store(
                "perm-target-" + index, actor(10 + index));
            for (int source : permutations[index]) {
                assertTrue(target.mergePortableBytes(
                    snapshots[source]).succeeded());
            }
            assertEquals(Arrays.asList("Alpha", "Beta", "Gamma"),
                         target.noteBodiesForTesting(CONFLICT_NOTE_ID));
            byte[] candidate = target.exportPortableBytes();
            if (converged == null) {
                converged = candidate;
                resolutionStore = target;
            } else {
                assertArrayEquals(converged, candidate);
            }
            assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                         target.mergePortableBytes(candidate));
        }

        OctavoAnnotationStore.Note conflict =
            resolutionStore.notes(FIXTURE_DIGEST).get(0);
        assertEquals(OctavoAnnotationStore.MutationResult.UPDATED,
                     resolutionStore.saveNote(
                         conflict.recordId,
                         conflict.revisionToken,
                         conflict.bookDigest,
                         conflict.spineIndex,
                         conflict.byteStart,
                         conflict.byteEnd,
                         conflict.attachedHighlightId,
                         conflict.excerpt,
                         "Resolved"));
        byte[] resolved = resolutionStore.exportPortableBytes();
        for (byte[] stale : snapshots) {
            assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                         resolutionStore.mergePortableBytes(stale));
            assertArrayEquals(resolved, resolutionStore.exportPortableBytes());
        }
        assertEquals(Collections.singletonList("Resolved"),
                     resolutionStore.noteBodiesForTesting(CONFLICT_NOTE_ID));

        for (int index = 0; index < snapshots.length; ++index) {
            OctavoAnnotationStore staleReplica = store(
                "resolved-into-stale-" + index, actor(20 + index));
            assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                         staleReplica.mergePortableBytes(snapshots[index]));
            assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                         staleReplica.mergePortableBytes(resolved));
            assertArrayEquals(resolved, staleReplica.exportPortableBytes());
            assertEquals(Collections.singletonList("Resolved"),
                         staleReplica.noteBodiesForTesting(CONFLICT_NOTE_ID));
        }
        OctavoAnnotationStore freshResolution =
            store("resolved-into-fresh", actor(24));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     freshResolution.mergePortableBytes(resolved));
        assertArrayEquals(resolved,
                          freshResolution.exportPortableBytes());

        OctavoAnnotationStore tombstone = store("stale-delete", actor(30));
        assertTrue(tombstone.toggleBookmark(
            FIXTURE_DIGEST, 1, 44, "Bookmark", "Before delete").succeeded());
        byte[] beforeDelete = tombstone.exportPortableBytes();
        assertEquals(OctavoAnnotationStore.MutationResult.REMOVED,
                     tombstone.toggleBookmark(
                         FIXTURE_DIGEST, 1, 44,
                         "Bookmark", "Before delete"));
        byte[] afterDelete = tombstone.exportPortableBytes();
        assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                     tombstone.mergePortableBytes(beforeDelete));
        assertArrayEquals(afterDelete, tombstone.exportPortableBytes());
        assertFalse(tombstone.isBookmarked(FIXTURE_DIGEST, 1, 44));

        OctavoAnnotationStore staleBookmark =
            store("tombstone-into-stale", actor(31));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     staleBookmark.mergePortableBytes(beforeDelete));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     staleBookmark.mergePortableBytes(afterDelete));
        assertArrayEquals(afterDelete,
                          staleBookmark.exportPortableBytes());
        assertFalse(staleBookmark.isBookmarked(FIXTURE_DIGEST, 1, 44));
        OctavoAnnotationStore freshTombstone =
            store("tombstone-into-fresh", actor(32));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     freshTombstone.mergePortableBytes(afterDelete));
        assertArrayEquals(afterDelete,
                          freshTombstone.exportPortableBytes());
        assertFalse(freshTombstone.isBookmarked(FIXTURE_DIGEST, 1, 44));
    }

    @Test
    public void hostilePortableInputsAreExactNoOps() throws IOException {
        OctavoAnnotationStore target = store("hostile-target", actor(50));
        assertTrue(target.toggleBookmark(
            FIXTURE_DIGEST, 0, 7, "Local", "Keep me").succeeded());
        byte[] fixture = OctavoAnnotationPortableWire.encode(goldenRecords());

        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID, null);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            Arrays.copyOf(fixture, fixture.length - 1));
        byte[] badChecksum = fixture.clone();
        badChecksum[badChecksum.length - 1] ^= 1;
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID, badChecksum);
        byte[] future = fixture.clone();
        putInt(future, Integer.BYTES,
               OctavoAnnotationStore.currentPortableVersionForTesting() + 1);
        updateChecksum(future);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.FUTURE_VERSION, future);
        byte[] futureHeader = new byte[2 * Integer.BYTES];
        putInt(futureHeader, 0, OctavoAnnotationPortableWire.MAGIC);
        putInt(futureHeader, Integer.BYTES,
               OctavoAnnotationPortableWire.VERSION + 1);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.FUTURE_VERSION,
            futureHeader);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            insertTrailingPayloadByte(fixture));

        List<OctavoAnnotationPortableWire.Record> reversed = goldenRecords();
        reversed.sort((left, right) -> right.recordId.compareTo(left.recordId));
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                reversed, reversed.size(), false, true));

        OctavoAnnotationPortableWire.Record flagged = noteRecord(
            fixedId(700), actor(51), 1, 0, 10, 10,
            0, 2, "", "", "", "Unknown flag");
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(flagged)));
        OctavoAnnotationPortableWire.Record missingAttachment = noteRecord(
            fixedId(701), actor(52), 1, 0, 10, 20,
            0, 0, fixedId(999), "", "Excerpt", "Attached");
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(missingAttachment)));

        OctavoAnnotationPortableWire.Record dominated =
            new OctavoAnnotationPortableWire.Record(
                fixedId(702), OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        OctavoAnnotationPortableWire.Head earlier =
            OctavoAnnotationPortableWire.put(
                actor(53), 1, 0, 30, 30, 0, 0,
                "", "", "", "Earlier");
        dominated.add(earlier);
        TreeMap<String, Long> observesEarlier = new TreeMap<>();
        observesEarlier.put(actor(53), 1L);
        dominated.add(new OctavoAnnotationPortableWire.Head(
            actor(54), 1,
            OctavoAnnotationPortableWire.Operation.PUT,
            observesEarlier,
            0, 30, 30, 0, 0, "", "", "", "Later"));
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(dominated)));

        OctavoAnnotationPortableWire.Record dotFirst = noteRecord(
            fixedId(703), actor(55), 9, 0, 40, 40,
            0, 0, "", "", "", "First dot owner");
        OctavoAnnotationPortableWire.Record dotSecond = noteRecord(
            fixedId(704), actor(55), 9, 0, 50, 50,
            0, 0, "", "", "", "Second dot owner");
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Arrays.asList(dotFirst, dotSecond)));

        byte[] malformedUtf8 = fixture.clone();
        int bodyOffset = indexOf(malformedUtf8,
            "Reader note: café".getBytes(StandardCharsets.UTF_8));
        assertTrue(bodyOffset >= 0);
        malformedUtf8[bodyOffset] = (byte)0xff;
        updateChecksum(malformedUtf8);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            malformedUtf8);

        String fixedRecordId = fixedId(705);
        OctavoAnnotationPortableWire.Record fixedAnchor = noteRecord(
            fixedRecordId, actor(56), 1, 0, 60, 60,
            0, 0, "", "", "", "Fixed anchor");
        OctavoAnnotationStore anchorTarget =
            store("hostile-anchor-target", actor(58));
        assertTrue(anchorTarget.mergePortableBytes(
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(fixedAnchor))).succeeded());
        TreeMap<String, Long> observesFixed = new TreeMap<>();
        observesFixed.put(actor(56), 1L);
        OctavoAnnotationPortableWire.Record movedAnchor =
            new OctavoAnnotationPortableWire.Record(
                fixedRecordId, OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        movedAnchor.add(new OctavoAnnotationPortableWire.Head(
            actor(57), 1,
            OctavoAnnotationPortableWire.Operation.PUT,
            observesFixed,
            0, 61, 61, 0, 0, "", "", "", "Moved anchor"));
        assertRejectedExact(anchorTarget,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(movedAnchor)));

        OctavoAnnotationPortableWire.Record frontierAhead = noteRecord(
            fixedId(706), actor(59), 3, 0, 70, 70,
            0, 0, "", "", "", "Stale own head");
        frontierAhead.frontier(actor(59), 5);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(frontierAhead)));

        OctavoAnnotationPortableWire.Record unjustifiedFrontier = noteRecord(
            fixedId(707), actor(66), 1, 0, 71, 71,
            0, 0, "", "", "", "Unrelated frontier component");
        unjustifiedFrontier.frontier(actor(67), Long.MAX_VALUE);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(unjustifiedFrontier)));

        TreeMap<String, Long> contextAheadValues = new TreeMap<>();
        contextAheadValues.put(actor(68), 2L);
        OctavoAnnotationPortableWire.Record contextAhead =
            new OctavoAnnotationPortableWire.Record(
                fixedId(708), OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        contextAhead.add(new OctavoAnnotationPortableWire.Head(
            actor(69), 1, OctavoAnnotationPortableWire.Operation.PUT,
            contextAheadValues, 0, 72, 72, 0, 0,
            "", "", "", "Context ahead"));
        contextAhead.frontier(actor(68), 1);
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(contextAhead)));

        String crossBookHighlightId = fixedId(709);
        OctavoAnnotationPortableWire.Record crossBookHighlight =
            highlightRecord(crossBookHighlightId, actor(70), 1,
                            0, 73, 74, 0, "Other book target");
        OctavoAnnotationPortableWire.Record crossBookNote =
            new OctavoAnnotationPortableWire.Record(
                fixedId(710), OctavoAnnotationPortableWire.Kind.NOTE,
                OTHER_DIGEST);
        crossBookNote.add(OctavoAnnotationPortableWire.put(
            actor(71), 1, 0, 73, 74, 0, 0,
            crossBookHighlightId, "", "", "Cross-book attachment"));
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Arrays.asList(crossBookHighlight, crossBookNote)));

        OctavoAnnotationPortableWire.Record deletePayload =
            new OctavoAnnotationPortableWire.Record(
                fixedId(711), OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        deletePayload.add(new OctavoAnnotationPortableWire.Head(
            actor(72), 1, OctavoAnnotationPortableWire.Operation.DELETE,
            new TreeMap<>(), 0, 75, 75, 0, 0,
            "", "", "Must be empty", ""));
        assertRejectedExact(target,
            OctavoAnnotationStore.PortableMergeResult.INVALID,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(deletePayload)));
    }

    @Test
    public void everyPortableBoundIsInclusiveAndOneOverRollsBack()
        throws IOException {
        String label256 = repeat('l', 256);
        String excerpt512 = repeat('e', 512);
        String note4096 = repeat('n', 4096);
        OctavoAnnotationPortableWire.Record bookmark = bookmarkRecord(
            actor(60), 1, 3, 77, label256, excerpt512);
        OctavoAnnotationPortableWire.Record note = noteRecord(
            fixedId(801), actor(61), 1, 3, 80, 117,
            0, 0, "", "", excerpt512, note4096);
        OctavoAnnotationStore exactStrings =
            store("bound-strings", actor(62));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     exactStrings.mergePortableBytes(
                         OctavoAnnotationPortableWire.encode(
                             Arrays.asList(bookmark, note))));
        byte[] exactStringState = exactStrings.exportPortableBytes();
        assertBoundRejected(exactStrings,
            OctavoAnnotationPortableWire.encode(Collections.singletonList(
                bookmarkRecord(actor(63), 1, 3, 78,
                               repeat('l', 257), ""))));
        assertBoundRejected(exactStrings,
            OctavoAnnotationPortableWire.encode(Collections.singletonList(
                bookmarkRecord(actor(64), 1, 3, 79,
                               "", repeat('e', 513)))));
        assertBoundRejected(exactStrings,
            OctavoAnnotationPortableWire.encode(Collections.singletonList(
                noteRecord(fixedId(802), actor(65), 1, 3, 90, 90,
                           0, 0, "", "", "", repeat('n', 4097)))));
        assertArrayEquals(exactStringState,
                          exactStrings.exportPortableBytes());

        TreeMap<String, Long> observedActors15 = new TreeMap<>();
        for (int index = 1; index < 16; ++index) {
            observedActors15.put(actor(70 + index), 1L);
        }
        OctavoAnnotationPortableWire.Record actors16 =
            new OctavoAnnotationPortableWire.Record(
                fixedId(803), OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        actors16.add(new OctavoAnnotationPortableWire.Head(
            actor(70), 1, OctavoAnnotationPortableWire.Operation.PUT,
            observedActors15, 0, 0, 0, 0, 0,
            "", "", "", "Sixteen causally justified actors"));
        OctavoAnnotationStore exactActors =
            store("bound-actors", actor(90));
        assertTrue(exactActors.mergePortableBytes(
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(actors16))).succeeded());

        TreeMap<String, Long> observedActors16 = new TreeMap<>();
        for (int index = 1; index < 17; ++index) {
            observedActors16.put(actor(170 + index), 1L);
        }
        OctavoAnnotationPortableWire.Record actors17 =
            new OctavoAnnotationPortableWire.Record(
                fixedId(806), OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        actors17.add(new OctavoAnnotationPortableWire.Head(
            actor(170), 1, OctavoAnnotationPortableWire.Operation.PUT,
            observedActors16, 0, 0, 0, 0, 0,
            "", "", "", "Seventeen actors"));
        assertBoundRejected(exactActors,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(actors17)));

        OctavoAnnotationPortableWire.Record heads =
            new OctavoAnnotationPortableWire.Record(
                fixedId(804), OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        for (int index = 0; index < 8; ++index) {
            heads.add(OctavoAnnotationPortableWire.put(
                actor(100 + index), 1, 1, 20, 20,
                0, 0, "", "", "", "Head " + index));
        }
        OctavoAnnotationStore exactHeads =
            store("bound-heads", actor(110));
        assertTrue(exactHeads.mergePortableBytes(
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(heads))).succeeded());
        heads.add(OctavoAnnotationPortableWire.put(
            actor(108), 1, 1, 20, 20,
            0, 0, "", "", "", "Head 8"));
        assertBoundRejected(exactHeads,
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(heads)));

        String joinBoundRecordId = fixedId(807);
        OctavoAnnotationPortableWire.Record fiveHeads =
            new OctavoAnnotationPortableWire.Record(
                joinBoundRecordId, OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        for (int index = 0; index < 5; ++index) {
            fiveHeads.add(OctavoAnnotationPortableWire.put(
                actor(300 + index), 1, 1, 21, 21,
                0, 0, "", "", "", "Left " + index));
        }
        OctavoAnnotationPortableWire.Record fourHeads =
            new OctavoAnnotationPortableWire.Record(
                joinBoundRecordId, OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        for (int index = 0; index < 4; ++index) {
            fourHeads.add(OctavoAnnotationPortableWire.put(
                actor(305 + index), 1, 1, 21, 21,
                0, 0, "", "", "", "Right " + index));
        }
        byte[] fiveHeadBytes = OctavoAnnotationPortableWire.encode(
            Collections.singletonList(fiveHeads));
        byte[] fourHeadBytes = OctavoAnnotationPortableWire.encode(
            Collections.singletonList(fourHeads));
        OctavoAnnotationStore boundedJoin =
            store("bound-join", actor(310));
        assertTrue(boundedJoin.mergePortableBytes(fiveHeadBytes).succeeded());
        assertBoundRejected(boundedJoin, fourHeadBytes);

        TreeMap<String, Long> observesFive = new TreeMap<>();
        for (int index = 0; index < 5; ++index) {
            observesFive.put(actor(300 + index), 1L);
        }
        OctavoAnnotationPortableWire.Record resolution =
            new OctavoAnnotationPortableWire.Record(
                joinBoundRecordId, OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        resolution.add(new OctavoAnnotationPortableWire.Head(
            actor(309), 1, OctavoAnnotationPortableWire.Operation.PUT,
            observesFive, 1, 21, 21, 0, 0,
            "", "", "", "Resolved left"));
        byte[] resolutionBytes = OctavoAnnotationPortableWire.encode(
            Collections.singletonList(resolution));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     boundedJoin.mergePortableBytes(resolutionBytes));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     boundedJoin.mergePortableBytes(fourHeadBytes));
        OctavoAnnotationStore alternateJoin =
            store("bound-join-alternate", actor(311));
        assertTrue(alternateJoin.mergePortableBytes(resolutionBytes)
                       .succeeded());
        assertTrue(alternateJoin.mergePortableBytes(fourHeadBytes)
                       .succeeded());
        assertArrayEquals(alternateJoin.exportPortableBytes(),
                          boundedJoin.exportPortableBytes());

        byte[] recordOverflow = OctavoAnnotationPortableWire.encode(
            Collections.emptyList(),
            OctavoAnnotationStore.maximumRecordsForTesting() + 1,
            true,
            true);
        assertBoundRejected(exactHeads, recordOverflow);

        OctavoAnnotationStore portableOneOver =
            store("bound-portable-one-over", actor(120));
        assertTrue(portableOneOver.toggleBookmark(
            FIXTURE_DIGEST, 0, 8, "Local", "Small rollback state")
                       .succeeded());
        assertBoundRejected(portableOneOver,
            new byte[
                OctavoAnnotationStore.maximumPortableFileBytesForTesting()
                    + 1]);

        OctavoAnnotationPortableWire.Record exhausted = noteRecord(
            fixedId(805), actor(130), Long.MAX_VALUE,
            0, 5, 5, 0, 0, "", "", "", "Counter maximum");
        OctavoAnnotationStore counterStore =
            store("bound-counter", actor(130));
        String originalActor = counterStore.actorIdForTesting();
        assertTrue(counterStore.mergePortableBytes(
            OctavoAnnotationPortableWire.encode(
                Collections.singletonList(exhausted))).succeeded());
        assertNotEquals(originalActor, counterStore.actorIdForTesting());
        assertTrue(counterStore.toggleBookmark(
            FIXTURE_DIGEST, 0, 6, "Rotated", "No dot reuse").succeeded());

        OctavoAnnotationStore futureStore =
            store("bound-future-local", actor(131));
        File futureParent = futureStore.stateFileForTesting().getParentFile();
        assertTrue(futureParent.mkdirs());
        long oversizedLength =
            (long)OctavoAnnotationStore.maximumFileBytesForTesting() + 1L;
        writeSparseStateFile(
            futureStore.stateFileForTesting(), oversizedLength,
            OctavoAnnotationStore.currentStoreVersionForTesting() + 1);
        assertEquals(OctavoAnnotationStore.LoadStatus.FUTURE_VERSION_BLOCKED,
                     futureStore.load());
        assertTrue(futureStore.mutationsBlocked());
        assertEquals(OctavoAnnotationStore.PortableMergeResult.BLOCKED,
                     futureStore.mergePortableBytes(exactStringState));
        assertEquals(oversizedLength,
                     futureStore.stateFileForTesting().length());
        assertFalse(futureStore.quarantineFileForTesting(1).exists());

        OctavoAnnotationStore currentStore =
            store("bound-current-local", actor(132));
        File currentParent = currentStore.stateFileForTesting().getParentFile();
        assertTrue(currentParent.mkdirs());
        writeSparseStateFile(
            currentStore.stateFileForTesting(), oversizedLength,
            OctavoAnnotationStore.currentStoreVersionForTesting());
        assertEquals(OctavoAnnotationStore.LoadStatus.CORRUPT_QUARANTINED,
                     currentStore.load());
        assertFalse(currentStore.stateFileForTesting().exists());
        assertEquals(oversizedLength,
                     currentStore.quarantineFileForTesting(1).length());
    }

    @Test
    public void exactPortableFileAndRecordLimitsAreAccepted()
        throws IOException {
        byte[] exactFile = exactLimitPortableBytesForTesting();
        assertEquals(
            OctavoAnnotationStore.maximumPortableFileBytesForTesting(),
            exactFile.length);
        OctavoAnnotationStore exactFileStore =
            store("bound-file-exact", actor(121));
        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     exactFileStore.mergePortableBytes(exactFile));
        assertEquals(OctavoAnnotationStore.maximumRecordsForTesting(),
                     exactFileStore.recordCountForTesting());
        assertEquals(OctavoAnnotationStore.maximumFileBytesForTesting(),
                     exactFileStore.stateFileForTesting().length());
        assertArrayEquals(exactFile, exactFileStore.exportPortableBytes());
        assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                     exactFileStore.mergePortableBytes(exactFile));
    }

    @Test
    public void failedMergePublishRollsBackBytesCounterAndProjection()
        throws IOException {
        OctavoAnnotationStore target = store("rollback-target", actor(140));
        assertTrue(target.toggleBookmark(
            FIXTURE_DIGEST, 0, 11, "Local", "Baseline").succeeded());
        OctavoAnnotationStore remote = store("rollback-remote", actor(141));
        assertTrue(remote.putNoteForTesting(
            CONFLICT_NOTE_ID, FIXTURE_DIGEST, 1, 22, "Remote").succeeded());
        byte[] remoteBytes = remote.exportPortableBytes();
        byte[] published = readFile(target.stateFileForTesting());
        byte[] canonical = target.canonicalBytesForTesting();
        byte[] portable = target.exportPortableBytes();

        target.failNextPublishForTesting();
        assertEquals(OctavoAnnotationStore.PortableMergeResult.PUBLISH_FAILED,
                     target.mergePortableBytes(remoteBytes));
        assertArrayEquals(published, readFile(target.stateFileForTesting()));
        assertArrayEquals(canonical, target.canonicalBytesForTesting());
        assertArrayEquals(portable, target.exportPortableBytes());
        assertTrue(target.notes(FIXTURE_DIGEST).isEmpty());
        assertFalse(target.temporaryFileForTesting().exists());

        assertEquals(OctavoAnnotationStore.PortableMergeResult.MERGED,
                     target.mergePortableBytes(remoteBytes));
        assertEquals(1, target.notes(FIXTURE_DIGEST).size());
        assertEquals(OctavoAnnotationStore.PortableMergeResult.UNCHANGED,
                     target.mergePortableBytes(remoteBytes));

        OctavoAnnotationStore control =
            store("rollback-control", actor(140));
        assertTrue(control.toggleBookmark(
            FIXTURE_DIGEST, 0, 11, "Local", "Baseline").succeeded());
        assertTrue(control.mergePortableBytes(remoteBytes).succeeded());
        assertTrue(target.toggleBookmark(
            FIXTURE_DIGEST, 0, 12, "Next", "No gap").succeeded());
        assertTrue(control.toggleBookmark(
            FIXTURE_DIGEST, 0, 12, "Next", "No gap").succeeded());
        assertArrayEquals(control.exportPortableBytes(),
                          target.exportPortableBytes());
        assertArrayEquals(control.canonicalBytesForTesting(),
                          target.canonicalBytesForTesting());
    }

    private void populateGoldenStore(OctavoAnnotationStore store) {
        assertTrue(store.putBookmarkForTesting(
            FIXTURE_DIGEST, 0, 0, "Chapter One", "Chapter One").succeeded());
        assertTrue(store.putHighlightForTesting(
            YELLOW_ID, FIXTURE_DIGEST, 0, 0, 11,
            OctavoAnnotationStore.HighlightColor.YELLOW,
            "Chapter One").succeeded());
        assertTrue(store.putHighlightForTesting(
            PINK_ID, FIXTURE_DIGEST, 0, 12, 25,
            OctavoAnnotationStore.HighlightColor.PINK,
            "First chapter").succeeded());
        assertTrue(store.putHighlightForTesting(
            BLUE_ID, FIXTURE_DIGEST, 1, 0, 11,
            OctavoAnnotationStore.HighlightColor.BLUE,
            "Chapter Two").succeeded());
        assertTrue(store.putHighlightForTesting(
            ORANGE_ID, FIXTURE_DIGEST, 1, 12, 26,
            OctavoAnnotationStore.HighlightColor.ORANGE,
            "Second chapter").succeeded());
        assertTrue(store.saveNote(
            STANDALONE_NOTE_ID, "", FIXTURE_DIGEST,
            0, 12, 25, "", "First chapter",
            "Reader note: café — Καλημέρα 👋").succeeded());
        assertTrue(store.saveNote(
            ATTACHED_NOTE_ID, "", FIXTURE_DIGEST,
            0, 0, 11, YELLOW_ID, "Chapter One",
            "Attached to “Chapter One”.").succeeded());
    }

    private static List<OctavoAnnotationPortableWire.Record> goldenRecords() {
        ArrayList<OctavoAnnotationPortableWire.Record> records =
            new ArrayList<>();
        records.add(bookmarkRecord(
            ACTOR_ZERO, 1, 0, 0, "Chapter One", "Chapter One"));
        records.add(highlightRecord(
            YELLOW_ID, ACTOR_ZERO, 2, 0, 0, 11, 0, "Chapter One"));
        records.add(highlightRecord(
            PINK_ID, ACTOR_ZERO, 3, 0, 12, 25, 1, "First chapter"));
        records.add(highlightRecord(
            BLUE_ID, ACTOR_ZERO, 4, 1, 0, 11, 2, "Chapter Two"));
        records.add(highlightRecord(
            ORANGE_ID, ACTOR_ZERO, 5, 1, 12, 26, 3, "Second chapter"));
        records.add(noteRecord(
            STANDALONE_NOTE_ID, ACTOR_ZERO, 6, 0, 12, 25,
            0, 0, "", "", "First chapter",
            "Reader note: café — Καλημέρα 👋"));
        records.add(noteRecord(
            ATTACHED_NOTE_ID, ACTOR_ZERO, 7, 0, 0, 11,
            0, 0, YELLOW_ID, "", "Chapter One",
            "Attached to “Chapter One”."));
        return records;
    }

    private static List<OctavoAnnotationPortableWire.Record>
        causalGoldenRecords() {
        ArrayList<OctavoAnnotationPortableWire.Record> records =
            new ArrayList<>();

        TreeMap<String, Long> highlightContext = new TreeMap<>();
        highlightContext.put(CAUSAL_ACTOR_C, 2L);
        OctavoAnnotationPortableWire.Record highlight =
            new OctavoAnnotationPortableWire.Record(
                CAUSAL_HIGHLIGHT_ID,
                OctavoAnnotationPortableWire.Kind.HIGHLIGHT,
                FIXTURE_DIGEST);
        highlight.add(new OctavoAnnotationPortableWire.Head(
            CAUSAL_ACTOR_C, 3,
            OctavoAnnotationPortableWire.Operation.DELETE,
            highlightContext, 1, 12, 26, 0, 0,
            "", "", "", ""));
        records.add(highlight);

        TreeMap<String, Long> deleteContext = new TreeMap<>();
        deleteContext.put(CAUSAL_ACTOR_A, 9007199254740990L);
        deleteContext.put(CAUSAL_ACTOR_B, 12L);
        deleteContext.put(CAUSAL_ACTOR_C, 5L);
        OctavoAnnotationPortableWire.Record deletedNote =
            new OctavoAnnotationPortableWire.Record(
                CAUSAL_TOMBSTONE_NOTE_ID,
                OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        deletedNote.add(new OctavoAnnotationPortableWire.Head(
            CAUSAL_ACTOR_B, 14,
            OctavoAnnotationPortableWire.Operation.DELETE,
            deleteContext, 1, 12, 26, 0, 0,
            "", "", "", ""));
        records.add(deletedNote);

        TreeMap<String, Long> conflictContext = new TreeMap<>();
        conflictContext.put(CAUSAL_ACTOR_A, 9007199254740992L);
        conflictContext.put(CAUSAL_ACTOR_B, 12L);
        conflictContext.put(CAUSAL_ACTOR_C, 5L);
        OctavoAnnotationPortableWire.Record conflict =
            new OctavoAnnotationPortableWire.Record(
                CAUSAL_CONFLICT_NOTE_ID,
                OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        conflict.add(new OctavoAnnotationPortableWire.Head(
            CAUSAL_ACTOR_B, 13,
            OctavoAnnotationPortableWire.Operation.PUT,
            conflictContext, 1, 12, 26, 0, 0,
            CAUSAL_HIGHLIGHT_ID, "", "Second chapter",
            "Conflict B: \u039a\u03b1\u03bb\u03b7\u03bc\u03ad\u03c1\u03b1 \ud83d\udc4b"));
        conflict.add(new OctavoAnnotationPortableWire.Head(
            CAUSAL_ACTOR_A, 9007199254740993L,
            OctavoAnnotationPortableWire.Operation.PUT,
            conflictContext, 1, 12, 26, 0, 1,
            CAUSAL_HIGHLIGHT_ID, "", "Second chapter",
            "Conflict A: cafe\u0301\r\nline two"));
        records.add(conflict);
        return records;
    }

    private static OctavoAnnotationPortableWire.Record bookmarkRecord(
        String actorId,
        long counter,
        long spineIndex,
        long byteOffset,
        String label,
        String excerpt) {
        OctavoAnnotationPortableWire.Record result =
            new OctavoAnnotationPortableWire.Record(
                OctavoAnnotationPortableWire.bookmarkRecordId(
                    FIXTURE_DIGEST, spineIndex, byteOffset),
                OctavoAnnotationPortableWire.Kind.BOOKMARK,
                FIXTURE_DIGEST);
        return result.add(OctavoAnnotationPortableWire.put(
            actorId, counter, spineIndex, byteOffset, byteOffset,
            0, 0, "", label, excerpt, ""));
    }

    private static OctavoAnnotationPortableWire.Record highlightRecord(
        String recordId,
        String actorId,
        long counter,
        long spineIndex,
        long byteStart,
        long byteEnd,
        int color,
        String excerpt) {
        OctavoAnnotationPortableWire.Record result =
            new OctavoAnnotationPortableWire.Record(
                recordId,
                OctavoAnnotationPortableWire.Kind.HIGHLIGHT,
                FIXTURE_DIGEST);
        return result.add(OctavoAnnotationPortableWire.put(
            actorId, counter, spineIndex, byteStart, byteEnd,
            color, 0, "", "", excerpt, ""));
    }

    private static OctavoAnnotationPortableWire.Record noteRecord(
        String recordId,
        String actorId,
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
        OctavoAnnotationPortableWire.Record result =
            new OctavoAnnotationPortableWire.Record(
                recordId,
                OctavoAnnotationPortableWire.Kind.NOTE,
                FIXTURE_DIGEST);
        return result.add(OctavoAnnotationPortableWire.put(
            actorId, counter, spineIndex, byteStart, byteEnd,
            color, flags, attachedId, label, excerpt, note));
    }

    static byte[] exactLimitPortableBytesForTesting() throws IOException {
        int recordCount = OctavoAnnotationStore.maximumRecordsForTesting();
        int targetBytes =
            OctavoAnnotationStore.maximumPortableFileBytesForTesting();
        ArrayList<OctavoAnnotationPortableWire.Record> records =
            new ArrayList<>(recordCount);
        for (int index = 0; index < recordCount; ++index) {
            records.add(noteRecord(
                fixedId(10000 + index), actor(200), index + 1L,
                index & 3, 2L * index, 2L * index + 1L,
                0, 0, "", "", "", "n"));
        }
        int baseBytes = OctavoAnnotationPortableWire.encode(records).length;

        OctavoAnnotationPortableWire.Record sample = noteRecord(
            fixedId(9990), actor(200), 1, 0, 0, 1,
            0, 0, "", "", "", "n");
        int sampleBase = OctavoAnnotationPortableWire.encode(
            Collections.singletonList(sample)).length;
        sample.add(OctavoAnnotationPortableWire.put(
            actor(201), 1, 0, 0, 1,
            0, 0, "", "", repeat('e', 512), repeat('n', 4096)));
        int fullIncrement = OctavoAnnotationPortableWire.encode(
            Collections.singletonList(sample)).length - sampleBase;
        int adjustableBytes = 512 + 4095;
        int minimumIncrement = fullIncrement - adjustableBytes;
        int remaining = targetBytes - baseBytes;
        assertTrue(remaining > minimumIncrement);
        int fullHeads = remaining / fullIncrement;
        int remainder = remaining - fullHeads * fullIncrement;
        assertTrue(fullHeads < recordCount * 7);

        for (int index = 0; index < fullHeads; ++index) {
            int recordIndex = index / 7;
            int actorIndex = 1 + index % 7;
            OctavoAnnotationPortableWire.Record record =
                records.get(recordIndex);
            long start = 2L * recordIndex;
            record.add(OctavoAnnotationPortableWire.put(
                actor(200 + actorIndex), recordIndex + 1L,
                recordIndex & 3, start, start + 1,
                0, 0, "", "", repeat('e', 512), repeat('n', 4096)));
        }
        if (remainder <= adjustableBytes) {
            replaceBaseHead(records.get(0), remainder);
        } else {
            assertTrue(remainder >= minimumIncrement);
            int adjustable = remainder - minimumIncrement;
            int recordIndex = fullHeads / 7;
            int actorIndex = 1 + fullHeads % 7;
            long start = 2L * recordIndex;
            records.get(recordIndex).add(
                OctavoAnnotationPortableWire.put(
                    actor(200 + actorIndex), recordIndex + 1L,
                    recordIndex & 3, start, start + 1,
                    0, 0, "", "",
                    repeat('e', Math.min(512, adjustable)),
                    repeat('n', 1 + Math.max(0, adjustable - 512))));
        }
        byte[] result = OctavoAnnotationPortableWire.encode(records);
        assertEquals(targetBytes, result.length);
        return result;
    }

    private static void replaceBaseHead(
        OctavoAnnotationPortableWire.Record record,
        int additionalBytes) {
        int excerptBytes = Math.min(512, additionalBytes);
        int noteAdditional = additionalBytes - excerptBytes;
        ArrayList<OctavoAnnotationPortableWire.Head> extras =
            new ArrayList<>(record.heads.subList(1, record.heads.size()));
        record.heads.clear();
        record.frontier.clear();
        record.add(OctavoAnnotationPortableWire.put(
            actor(200), 1, 0, 0, 1,
            0, 0, "", "", repeat('e', excerptBytes),
            repeat('n', 1 + noteAdditional)));
        for (OctavoAnnotationPortableWire.Head extra : extras) {
            record.add(extra);
        }
    }

    private void assertRejectedExact(
        OctavoAnnotationStore store,
        OctavoAnnotationStore.PortableMergeResult expected,
        byte[] candidate) throws IOException {
        byte[] file = readFile(store.stateFileForTesting());
        byte[] canonical = store.canonicalBytesForTesting();
        byte[] portable = store.exportPortableBytes();
        int records = store.recordCountForTesting();
        assertEquals(expected, store.mergePortableBytes(candidate));
        assertArrayEquals(file, readFile(store.stateFileForTesting()));
        assertArrayEquals(canonical, store.canonicalBytesForTesting());
        assertArrayEquals(portable, store.exportPortableBytes());
        assertEquals(records, store.recordCountForTesting());
        assertFalse(store.temporaryFileForTesting().exists());
    }

    private void assertBoundRejected(OctavoAnnotationStore store,
                                     byte[] candidate) throws IOException {
        assertRejectedExact(store,
            OctavoAnnotationStore.PortableMergeResult.LIMIT, candidate);
    }

    private OctavoAnnotationStore store(String name, String actor) {
        File files = new File(testRoot, name);
        assertTrue(files.mkdirs());
        OctavoAnnotationStore store =
            new OctavoAnnotationStore(files, actor);
        assertEquals(OctavoAnnotationStore.LoadStatus.MISSING, store.load());
        return store;
    }

    private static byte[] readBase64Asset(String name) throws IOException {
        try (InputStream input = InstrumentationRegistry
                 .getInstrumentation().getContext().getAssets().open(name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (;;) {
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    bytes.write(buffer, 0, count);
                }
            }
            return Base64.decode(
                bytes.toString(StandardCharsets.US_ASCII.name()),
                Base64.DEFAULT);
        }
    }

    private static String readTextAsset(String name) throws IOException {
        try (InputStream input = InstrumentationRegistry
                 .getInstrumentation().getContext().getAssets().open(name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (;;) {
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    bytes.write(buffer, 0, count);
                }
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
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

    private static void writeSparseStateFile(File file,
                                             long length,
                                             int version)
        throws IOException {
        assertTrue(length >= 2L * Integer.BYTES);
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.setLength(0);
            output.setLength(length);
            output.seek(0);
            output.writeInt(0x4F31414E);
            output.writeInt(version);
            output.getFD().sync();
        }
    }

    private static byte[] insertTrailingPayloadByte(byte[] source) {
        byte[] result = new byte[source.length + 1];
        int payloadLength = source.length - Integer.BYTES;
        System.arraycopy(source, 0, result, 0, payloadLength);
        result[payloadLength] = 0;
        updateChecksum(result);
        return result;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int start = 0; start <= haystack.length - needle.length; ++start) {
            boolean equal = true;
            for (int index = 0; index < needle.length; ++index) {
                if (haystack[start + index] != needle[index]) {
                    equal = false;
                    break;
                }
            }
            if (equal) {
                return start;
            }
        }
        return -1;
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

    private static String actor(int value) {
        return String.format(Locale.ROOT, "%032x", value);
    }

    private static String fixedId(int value) {
        return String.format(Locale.ROOT, "%032x", value);
    }

    private static String repeat(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 0xf, 16));
                result.append(Character.forDigit(value & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
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
