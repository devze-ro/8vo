package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two independently invokable halves of the Port 7 process-restart probe.
 *
 * The ordinary connected suite excludes this class.
 * scripts/android_port7_process_restart.ps1 runs
 * {@link #seedDurableReaderState()}, force-stops the target application
 * outside instrumentation, confirms that its process is gone, and then runs
 * {@link #verifyDurableReaderStateAfterRestart()}. Keeping the process
 * boundary outside this class prevents a killed instrumentation process from
 * reporting an ambiguous result.
 */
@RunWith(AndroidJUnit4.class)
@ExternalProcessRestartProbe
public final class OctavoProcessRestartTest {
    private static final int EVIDENCE_MAGIC = 0x4F375052; // "O7PR"
    private static final int EVIDENCE_VERSION = 1;
    private static final int EVIDENCE_FILE_CAP = 512;
    private static final String EVIDENCE_DIRECTORY = "port7";
    private static final String EVIDENCE_FILE =
        "process_restart_expected.v1";
    private static final String EVIDENCE_TEMPORARY_FILE =
        "process_restart_expected.v1.tmp";

    private interface StateCondition {
        boolean matches(long[] state);
    }

    private static final class ExpectedState {
        final String bookKey;
        final long spineIndex;
        final long byteOffset;
        final OctavoAppearance appearance;

        ExpectedState(String bookKey,
                      long spineIndex,
                      long byteOffset,
                      OctavoAppearance appearance) {
            this.bookKey = bookKey;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
            this.appearance = appearance;
        }
    }

    @Test
    public void seedDurableReaderState() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        clearEvidence(context);

        OctavoAppearance expectedAppearance = extremeAppearance();
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.libraryVisibleForTesting());
                assertNull(activity.activeBookKeyForTesting());
                assertTrue(activity.openFixtureForTesting());
            });

            long[] initial = awaitPresented(
                scenario,
                OctavoAppearance.defaults(),
                false,
                "8vo did not present the restart fixture");
            long minimumGeneration =
                initial[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] + 1;
            scenario.onActivity(activity ->
                activity.requestAppearanceForTesting(expectedAppearance));
            long[] extreme = awaitState(
                scenario,
                state -> isPresented(state, expectedAppearance)
                    && state[
                        OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                        >= minimumGeneration,
                "8vo did not present the restart layout extreme");
            assertTrue(
                "The restart fixture needs a second page",
                extreme[OctavoSurfaceView.STATE_PAGE_COUNT] >= 2);
            assertHealthyAndSettled(extreme);
            assertHostAppearance(scenario, expectedAppearance);

            long moveSuccessBefore =
                extreme[OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT];
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                assertTrue(view.movePageForAccessibility(1));
            });
            long[] pageTwo = awaitState(
                scenario,
                state -> isPresented(state, expectedAppearance)
                    && state[OctavoSurfaceView.STATE_PAGE_INDEX] == 2
                    && state[
                        OctavoSurfaceView.STATE_PAGE_MOVE_SUCCESS_COUNT]
                        == moveSuccessBefore + 1
                    && state[
                        OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTED_COUNT]
                        == state[
                            OctavoSurfaceView
                                .STATE_PAGE_MOVE_SUCCESS_COUNT],
                "8vo did not present page 2 for restart seeding");
            assertHealthyAndSettled(pageTwo);

            AtomicReference<String> bookKey = new AtomicReference<>();
            AtomicReference<long[]> position = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                view.flushPersistenceForTesting();
                bookKey.set(view.documentKeyForTesting());
                position.set(view.readingPositionForTesting());

                OctavoLibraryStore.Book persisted =
                    activity.libraryStoreForTesting().findBook(bookKey.get());
                assertNotNull(persisted);
                assertTrue(persisted.hasPosition);
                assertEquals(pageTwo[
                                 OctavoSurfaceView
                                     .STATE_PRESENTED_SPINE_INDEX],
                             persisted.spineIndex);
                assertEquals(pageTwo[
                                 OctavoSurfaceView
                                     .STATE_PRESENTED_BYTE_OFFSET],
                             persisted.byteOffset);
                assertEquals(
                    0,
                    activity.libraryStoreForTesting()
                        .catalogSaveFailureCountForTesting());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
            });

            assertNotNull(bookKey.get());
            assertNotNull(position.get());
            assertEquals(3, position.get().length);
            assertEquals(1, position.get()[0]);
            assertEquals(
                pageTwo[OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX],
                position.get()[1]);
            assertEquals(
                pageTwo[OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET],
                position.get()[2]);
            assertAnchorInsidePage(
                pageTwo, position.get()[1], position.get()[2]);

            writeEvidence(
                context,
                new ExpectedState(bookKey.get(),
                                  position.get()[1],
                                  position.get()[2],
                                  expectedAppearance));
        }
    }

    @Test
    public void verifyDurableReaderStateAfterRestart() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        ExpectedState expected = readEvidence(context);
        assertEquals(
            expected.appearance,
            new OctavoAppearanceStore(context).load());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(
                    "A fresh 8vo process must remain library-first",
                    activity.libraryVisibleForTesting());
                assertNull(activity.activeBookKeyForTesting());
                assertEquals(expected.appearance,
                             activity.appearanceForTesting());
                assertTrue(activity.openBookForTesting(expected.bookKey));
            });

            long[] restored = awaitPresented(
                scenario,
                expected.appearance,
                true,
                "8vo did not reopen durable reader state after restart");
            assertEquals(1,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_REQUESTED]);
            assertEquals(1,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_ATTEMPTED]);
            assertEquals(1,
                         restored[
                             OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]);
            assertEquals(0,
                         restored[
                             OctavoSurfaceView
                                 .STATE_RESTORE_FAILURE_COUNT]);
            assertEquals(expected.spineIndex,
                         restored[
                             OctavoSurfaceView
                                 .STATE_PRESENTED_SPINE_INDEX]);
            assertEquals(expected.byteOffset,
                         restored[
                             OctavoSurfaceView
                                 .STATE_PRESENTED_BYTE_OFFSET]);
            assertAnchorInsidePage(
                restored, expected.spineIndex, expected.byteOffset);
            assertHealthyAndSettled(restored);
            assertHostAppearance(scenario, expected.appearance);

            scenario.onActivity(activity -> {
                assertEquals(expected.bookKey,
                             activity.activeBookKeyForTesting());
                OctavoSurfaceView view = surface(activity);
                assertEquals(expected.bookKey,
                             view.documentKeyForTesting());
                long[] position = view.readingPositionForTesting();
                assertNotNull(position);
                assertEquals(3, position.length);
                assertEquals(1, position[0]);
                assertEquals(expected.spineIndex, position[1]);
                assertEquals(expected.byteOffset, position[2]);
            });
        }
    }

    private static OctavoAppearance extremeAppearance() {
        return OctavoAppearance.create(
            OctavoAppearance.THEME_WARM_DARK,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            28,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);
    }

    private static OctavoSurfaceView surface(OctavoActivity activity) {
        OctavoSurfaceView result = (OctavoSurfaceView)
            activity.findViewById(R.id.octavo_surface);
        assertNotNull(result);
        return result;
    }

    private static long[] state(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity ->
            result.set(surface(activity).nativeStateForTesting()));
        assertNotNull(result.get());
        assertEquals(OctavoSurfaceView.STATE_FIELD_COUNT,
                     result.get().length);
        return result.get();
    }

    private static long[] awaitState(
        ActivityScenario<OctavoActivity> scenario,
        StateCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 300; ++attempt) {
            long[] current = state(scenario);
            if (condition.matches(current)) {
                return current;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] awaitPresented(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expectedAppearance,
        boolean requireRestore,
        String failureMessage) {
        return awaitState(
            scenario,
            state -> isPresented(state, expectedAppearance)
                && (!requireRestore
                    || state[OctavoSurfaceView.STATE_RESTORE_SUCCEEDED]
                        == 1),
            failureMessage);
    }

    private static boolean isPresented(long[] state,
                                       OctavoAppearance expected) {
        return state != null
            && state.length == OctavoSurfaceView.STATE_FIELD_COUNT
            && state[OctavoSurfaceView.STATE_RESUMED] == 1
            && state[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
            && state[OctavoSurfaceView.STATE_WIDTH] > 0
            && state[OctavoSurfaceView.STATE_HEIGHT] > 0
            && state[OctavoSurfaceView.STATE_DOCUMENT_OPEN] == 1
            && state[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
            && state[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1
            && state[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
            && state[
                OctavoSurfaceView.STATE_PAGE_MOVE_PRESENTATION_PENDING]
                == 0
            && state[
                OctavoSurfaceView.STATE_REFLOW_PRESENTATION_PENDING]
                == 0
            && state[OctavoSurfaceView.STATE_APPEARANCE_GENERATION]
                == state[
                    OctavoSurfaceView
                        .STATE_APPEARANCE_PRESENTED_GENERATION]
            && state[OctavoSurfaceView.STATE_THEME]
                == expected.themeId()
            && state[OctavoSurfaceView.STATE_FONT_FAMILY]
                == expected.fontFamilyId()
            && state[OctavoSurfaceView.STATE_FONT_SIZE_SP]
                == expected.fontSizeSp()
            && state[
                OctavoSurfaceView.STATE_LINE_SPACING_PERMILLE]
                == expected.lineSpacingPermille()
            && state[OctavoSurfaceView.STATE_MARGIN]
                == expected.marginsId()
            && state[OctavoSurfaceView.STATE_ALIGNMENT]
                == expected.alignmentId()
            && state[OctavoSurfaceView.STATE_PUBLISHER_COLORS]
                == expected.publisherColorsId();
    }

    private static void assertHealthyAndSettled(long[] state) {
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_PAGE_MOVE_PRESENTATION_PENDING]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_REFLOW_PRESENTATION_PENDING]);
        assertTrue(
            state[OctavoSurfaceView.STATE_APPEARANCE_GENERATION] > 0);
        assertEquals(
            state[OctavoSurfaceView.STATE_APPEARANCE_GENERATION],
            state[
                OctavoSurfaceView.STATE_APPEARANCE_PRESENTED_GENERATION]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_PAGE_MOVE_GATE_BLOCK_COUNT]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_APPEARANCE_GATE_BLOCK_COUNT]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
        assertEquals(0,
                     state[OctavoSurfaceView.STATE_READER_VIEW_ERRORS]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_NAVIGATION_FAILURE_COUNT]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_DOCUMENT_OPEN_FAILURE_COUNT]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_RESTORE_FAILURE_COUNT]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView
                             .STATE_APPEARANCE_FAILURE_COUNT]);
        assertEquals(0,
                     state[
                         OctavoSurfaceView.STATE_REFLOW_FAILURE_COUNT]);
    }

    private static void assertHostAppearance(
        ActivityScenario<OctavoActivity> scenario,
        OctavoAppearance expected) {
        scenario.onActivity(activity -> {
            activity.flushAppearancePersistenceForTesting();
            assertEquals(expected, activity.appearanceForTesting());
            assertEquals(expected,
                         activity.appearanceStoreForTesting().current());
            assertEquals(expected,
                         surface(activity).presentedAppearanceForTesting());
        });
    }

    private static void assertAnchorInsidePage(long[] state,
                                               long spineIndex,
                                               long byteOffset) {
        assertEquals(spineIndex,
                     state[
                         OctavoSurfaceView.STATE_PRESENTED_SPINE_INDEX]);
        assertEquals(byteOffset,
                     state[
                         OctavoSurfaceView.STATE_PRESENTED_BYTE_OFFSET]);
        assertEquals(spineIndex,
                     state[OctavoSurfaceView.STATE_SPINE_INDEX]);
        assertTrue(
            "The restored page starts after the durable anchor",
            state[OctavoSurfaceView.STATE_PAGE_FIRST_BYTE] <= byteOffset);
        assertTrue(
            "The restored page ends before the durable anchor",
            byteOffset
                < state[
                    OctavoSurfaceView.STATE_PAGE_ONE_PAST_LAST_BYTE]);
    }

    private static void writeEvidence(Context context,
                                      ExpectedState expected)
        throws IOException {
        requireValidKey(expected.bookKey);
        if (expected.spineIndex < 0
            || expected.spineIndex > 0xFFFFFFFFL
            || expected.byteOffset < 0
            || expected.appearance == null) {
            throw new IOException("Invalid process-restart evidence");
        }

        File directory = evidenceDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create restart evidence directory");
        }
        File temporary = new File(directory, EVIDENCE_TEMPORARY_FILE);
        File destination = new File(directory, EVIDENCE_FILE);
        try (FileOutputStream fileOutput =
                 new FileOutputStream(temporary, false);
             DataOutputStream output = new DataOutputStream(
                 new BufferedOutputStream(fileOutput))) {
            output.writeInt(EVIDENCE_MAGIC);
            output.writeInt(EVIDENCE_VERSION);
            output.writeUTF(expected.bookKey);
            output.writeLong(expected.spineIndex);
            output.writeLong(expected.byteOffset);
            int[] appearance = expected.appearance.nativeConfig();
            output.writeInt(appearance.length);
            for (int value : appearance) {
                output.writeInt(value);
            }
            output.flush();
            fileOutput.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            throw exception;
        }
        if (temporary.length() <= 0
            || temporary.length() > EVIDENCE_FILE_CAP) {
            temporary.delete();
            throw new IOException("Restart evidence exceeds its bound");
        }
        publishAtomically(temporary, destination);
    }

    private static ExpectedState readEvidence(Context context)
        throws IOException {
        File source = new File(evidenceDirectory(context), EVIDENCE_FILE);
        if (!source.isFile()
            || source.length() <= 0
            || source.length() > EVIDENCE_FILE_CAP) {
            throw new IOException(
                "Run seedDurableReaderState before restart verification");
        }

        try (DataInputStream input = new DataInputStream(
                 new BufferedInputStream(new FileInputStream(source)))) {
            int magic = input.readInt();
            int version = input.readInt();
            String key = input.readUTF();
            long spineIndex = input.readLong();
            long byteOffset = input.readLong();
            int fieldCount = input.readInt();
            if (magic != EVIDENCE_MAGIC
                || version != EVIDENCE_VERSION
                || fieldCount != OctavoAppearance.NATIVE_FIELD_COUNT) {
                throw new IOException("Invalid restart evidence header");
            }
            requireValidKey(key);
            if (spineIndex < 0
                || spineIndex > 0xFFFFFFFFL
                || byteOffset < 0) {
                throw new IOException("Invalid restart evidence anchor");
            }
            int[] appearance = new int[fieldCount];
            for (int index = 0; index < fieldCount; ++index) {
                appearance[index] = input.readInt();
            }
            if (input.read() != -1) {
                throw new IOException("Restart evidence has trailing data");
            }
            OctavoAppearance decoded =
                OctavoAppearance.fromNativeConfig(appearance);
            if (decoded == null) {
                throw new IOException("Invalid restart evidence appearance");
            }
            return new ExpectedState(key, spineIndex, byteOffset, decoded);
        } catch (EOFException exception) {
            throw new IOException("Truncated restart evidence", exception);
        }
    }

    private static void clearEvidence(Context context) {
        File directory = evidenceDirectory(context);
        File temporary = new File(directory, EVIDENCE_TEMPORARY_FILE);
        File destination = new File(directory, EVIDENCE_FILE);
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException(
                "Unable to clear temporary restart evidence");
        }
        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException(
                "Unable to clear restart evidence");
        }
    }

    private static File evidenceDirectory(Context context) {
        return new File(context.getFilesDir(), EVIDENCE_DIRECTORY);
    }

    private static void requireValidKey(String key) throws IOException {
        if (key == null || !key.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid restart evidence book key");
        }
    }

    private static void publishAtomically(File temporary, File destination)
        throws IOException {
        try {
            Files.move(temporary.toPath(),
                       destination.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(),
                       destination.toPath(),
                       StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
