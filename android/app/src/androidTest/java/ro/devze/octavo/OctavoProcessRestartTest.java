package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two independently invokable halves of the Port 8 process-restart probe.
 *
 * The ordinary connected suite excludes this class.
 * scripts/android_port7_process_restart.ps1 remains the compatible driver and runs
 * {@link #seedDurableReaderState()}, force-stops the target application
 * outside instrumentation, confirms that its process is gone, and then runs
 * {@link #verifyDurableReaderStateAfterRestart()}. Keeping the process
 * boundary outside this class prevents a killed instrumentation process from
 * reporting an ambiguous result. The historical Port 7 wrapper remains a
 * compatible entry point for the same two-method contract.
 */
@RunWith(AndroidJUnit4.class)
@ExternalProcessRestartProbe
public final class OctavoProcessRestartTest {
    private static final int EVIDENCE_MAGIC = 0x4F385052; // "O8PR"
    private static final int EVIDENCE_VERSION = 2;
    private static final int EVIDENCE_FILE_CAP = 512;
    private static final String EVIDENCE_DIRECTORY = "port8";
    private static final String EVIDENCE_FILE =
        "process_restart_expected.v2";
    private static final String EVIDENCE_TEMPORARY_FILE =
        "process_restart_expected.v2.tmp";

    private interface StateCondition {
        boolean matches(long[] state);
    }

    private interface SearchCondition {
        boolean matches(OctavoSearch search);
    }

    private static final class ExpectedState {
        final String bookKey;
        final long originSpineIndex;
        final long originByteOffset;
        final long spineIndex;
        final long byteOffset;
        final OctavoAppearance appearance;
        final OctavoProgressDisplay progressDisplay;

        ExpectedState(String bookKey,
                      long originSpineIndex,
                      long originByteOffset,
                      long spineIndex,
                      long byteOffset,
                      OctavoAppearance appearance,
                      OctavoProgressDisplay progressDisplay) {
            this.bookKey = bookKey;
            this.originSpineIndex = originSpineIndex;
            this.originByteOffset = originByteOffset;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
            this.appearance = appearance;
            this.progressDisplay = progressDisplay;
        }
    }

    @Test
    public void seedDurableReaderState() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoLibraryStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        clearEvidence(context);

        OctavoAppearance expectedAppearance = extremeAppearance();
        OctavoProgressDisplay expectedProgress = OctavoProgressDisplay.LOCATION;
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

            awaitLocationReady(scenario);
            long[] origin = readingPosition(scenario);
            assertValidPosition(origin);
            assertAnchorInsidePage(extreme, origin[1], origin[2]);

            long[] navigationBefore = navigationState(scenario);
            assertNotNull(navigationBefore);
            assertEquals(
                0,
                navigationBefore[
                    OctavoSurfaceView.NAVIGATION_STATE_PENDING]);
            assertEquals(
                navigationBefore[
                    OctavoSurfaceView.NAVIGATION_STATE_SEMANTIC_GENERATION],
                navigationBefore[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]);
            assertEquals(
                0,
                navigationBefore[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_BACK_COUNT]);
            assertEquals(
                0,
                navigationBefore[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);

            AtomicInteger jumpResult = new AtomicInteger();
            scenario.onActivity(activity -> jumpResult.set(
                surface(activity).requestPercentageNavigation(100)));
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         jumpResult.get());
            long[] jumpedNavigation = awaitNavigationState(
                scenario,
                state -> state[
                             OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       > navigationBefore[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_GENERATION]
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_HISTORY_BACK_COUNT] == 1
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_HISTORY_FORWARD_COUNT] == 0,
                "8vo did not present and record the restart jump");
            long[] jumped = state(scenario);
            assertHealthyAndSettled(jumped);
            long[] target = readingPosition(scenario);
            assertValidPosition(target);
            assertFalse(origin[1] == target[1] && origin[2] == target[2]);
            assertAnchorInsidePage(jumped, target[1], target[2]);

            long progressGenerationBefore =
                jumpedNavigation[
                    OctavoSurfaceView.NAVIGATION_STATE_PROGRESS_GENERATION];
            AtomicInteger progressResult = new AtomicInteger();
            scenario.onActivity(activity -> progressResult.set(
                surface(activity).requestProgressDisplay(expectedProgress)));
            assertEquals(OctavoNative.NAVIGATION_ACCEPTED,
                         progressResult.get());
            long[] presentedNavigation = awaitNavigationState(
                scenario,
                state -> state[
                             OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_GENERATION]
                       > progressGenerationBefore
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]
                       == expectedProgress.nativeId(),
                "8vo did not present the restart progress choice");
            long[] progressFrame = state(scenario);
            assertHealthyAndSettled(progressFrame);
            long[] afterProgress = readingPosition(scenario);
            assertValidPosition(afterProgress);
            assertEquals(target[1], afterProgress[1]);
            assertEquals(target[2], afterProgress[2]);
            assertAnchorInsidePage(
                progressFrame, afterProgress[1], afterProgress[2]);
            assertEquals(
                1,
                presentedNavigation[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_BACK_COUNT]);
            assertEquals(
                0,
                presentedNavigation[
                    OctavoSurfaceView.NAVIGATION_STATE_HISTORY_FORWARD_COUNT]);


            AtomicReference<String> bookKey = new AtomicReference<>();
            AtomicReference<long[]> position = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView view = surface(activity);
                view.flushPersistenceForTesting();
                activity.flushProgressPersistenceForTesting();
                bookKey.set(view.documentKeyForTesting());
                position.set(view.readingPositionForTesting());
                activity.toggleCurrentBookmarkForTesting();
                assertTrue(activity.annotationStoreForTesting()
                    .isBookmarked(
                        bookKey.get(), position.get()[1], position.get()[2]));

                OctavoLibraryStore.Book persisted =
                    activity.libraryStoreForTesting().findBook(bookKey.get());
                assertNotNull(persisted);
                assertTrue(persisted.hasPosition);
                assertEquals(afterProgress[1],
                             persisted.spineIndex);
                assertEquals(afterProgress[2],
                             persisted.byteOffset);
                assertEquals(
                    0,
                    activity.libraryStoreForTesting()
                        .catalogSaveFailureCountForTesting());
                assertEquals(
                    0,
                    activity.appearanceStoreForTesting()
                        .saveFailureCountForTesting());
                assertSame(expectedProgress,
                           activity.progressDisplayForTesting());
                assertSame(expectedProgress,
                           view.presentedProgressDisplay());
                assertSame(expectedProgress,
                           activity.progressStoreForTesting().current());
                assertTrue(activity.progressStoreForTesting()
                               .saveSuccessCountForTesting() >= 1);
                assertEquals(0, activity.progressStoreForTesting()
                    .saveFailureCountForTesting());
            });

            assertNotNull(bookKey.get());
            assertNotNull(position.get());
            assertEquals(3, position.get().length);
            assertEquals(1, position.get()[0]);
            assertEquals(
                afterProgress[1],
                position.get()[1]);
            assertEquals(
                afterProgress[2],
                position.get()[2]);
            assertAnchorInsidePage(
                progressFrame, position.get()[1], position.get()[2]);

            scenario.onActivity(activity -> assertEquals(
                OctavoNative.NAVIGATION_ACCEPTED,
                surface(activity).commitSearch("paragraph 250")));
            OctavoSearch seededSearch = awaitSearch(
                scenario,
                search -> !search.isPending()
                    && search.query().equals("paragraph 250")
                    && search.totalCount() == 4,
                "8vo did not present the transient restart search");
            assertEquals(4, seededSearch.rowCount());

            writeEvidence(
                context,
                new ExpectedState(bookKey.get(),
                                  origin[1],
                                  origin[2],
                                  position.get()[1],
                                  position.get()[2],
                                  expectedAppearance,
                                  expectedProgress));
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
            scenario.onActivity(activity -> assertTrue(
                activity.annotationStoreForTesting().isBookmarked(
                    expected.bookKey,
                    expected.spineIndex,
                    expected.byteOffset)));
            assertHealthyAndSettled(restored);
            assertHostAppearance(scenario, expected.appearance);
            OctavoSearch restoredSearch = searchSnapshot(scenario);
            assertNotNull(restoredSearch);
            assertTrue(restoredSearch.isReady());
            assertTrue(
                "In-book search must not survive a process restart",
                restoredSearch.query().isEmpty());
            assertEquals(0, restoredSearch.rowCount());
            assertEquals(0, restoredSearch.totalCount());

            assertFalse(
                expected.originSpineIndex == expected.spineIndex
                    && expected.originByteOffset == expected.byteOffset);
            long[] restoredNavigation = awaitNavigationState(
                scenario,
                state -> state[
                             OctavoSurfaceView.NAVIGATION_STATE_PENDING] == 0
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_SEMANTIC_PRESENTED_GENERATION]
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_GENERATION]
                       == state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_PRESENTED_GENERATION]
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]
                       == expected.progressDisplay.nativeId()
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_HISTORY_BACK_COUNT] == 0
                    && state[
                           OctavoSurfaceView
                               .NAVIGATION_STATE_HISTORY_FORWARD_COUNT] == 0,
                "8vo did not restore progress with empty session history");
            assertEquals(
                expected.progressDisplay.nativeId(),
                restoredNavigation[
                    OctavoSurfaceView
                        .NAVIGATION_STATE_PROGRESS_PRESENTED_MODE]);

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
                assertSame(expected.progressDisplay,
                           activity.progressDisplayForTesting());
                assertSame(expected.progressDisplay,
                           view.presentedProgressDisplay());
                assertSame(expected.progressDisplay,
                           activity.progressStoreForTesting().current());
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

    private static OctavoSearch searchSnapshot(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSearch> result = new AtomicReference<>();
        scenario.onActivity(activity ->
            result.set(surface(activity).searchSnapshot()));
        return result.get();
    }

    private static OctavoSearch awaitSearch(
        ActivityScenario<OctavoActivity> scenario,
        SearchCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 240; ++attempt) {
            OctavoSearch current = searchSnapshot(scenario);
            if (current != null && condition.matches(current)) {
                return current;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return null;
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

    private static void awaitLocationReady(
        ActivityScenario<OctavoActivity> scenario) {
        for (int attempt = 0; attempt < 300; ++attempt) {
            AtomicReference<long[]> result = new AtomicReference<>();
            scenario.onActivity(activity -> result.set(
                surface(activity).locationCacheStateForTesting()));
            long[] current = result.get();
            if (current != null && current.length == 10
                && current[0] == 1 && current[1] == 1) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("8vo did not complete deterministic location warming");
    }

    private static long[] navigationState(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).navigationStateForTesting()));
        assertNotNull(result.get());
        assertEquals(OctavoSurfaceView.NAVIGATION_STATE_FIELD_COUNT,
                     result.get().length);
        return result.get();
    }

    private static long[] awaitNavigationState(
        ActivityScenario<OctavoActivity> scenario,
        StateCondition condition,
        String failureMessage) {
        for (int attempt = 0; attempt < 300; ++attempt) {
            long[] current = navigationState(scenario);
            if (condition.matches(current)) {
                return current;
            }
            SystemClock.sleep(50);
        }
        fail(failureMessage);
        return new long[0];
    }

    private static long[] readingPosition(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            surface(activity).readingPositionForTesting()));
        return result.get();
    }

    private static void assertValidPosition(long[] position) {
        assertNotNull(position);
        assertEquals(3, position.length);
        assertEquals(1, position[0]);
        assertTrue(position[1] >= 0);
        assertTrue(position[2] >= 0);
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
        if (expected.originSpineIndex < 0
            || expected.originSpineIndex > 0xFFFFFFFFL
            || expected.originByteOffset < 0
            || expected.spineIndex < 0
            || expected.spineIndex > 0xFFFFFFFFL
            || expected.byteOffset < 0
            || expected.appearance == null
            || expected.progressDisplay == null) {
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
            output.writeLong(expected.originSpineIndex);
            output.writeLong(expected.originByteOffset);
            output.writeLong(expected.spineIndex);
            output.writeLong(expected.byteOffset);
            output.writeInt(expected.progressDisplay.nativeId());
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
            long originSpineIndex = input.readLong();
            long originByteOffset = input.readLong();
            long spineIndex = input.readLong();
            long byteOffset = input.readLong();
            int progressDisplayId = input.readInt();
            int fieldCount = input.readInt();
            if (magic != EVIDENCE_MAGIC
                || version != EVIDENCE_VERSION
                || fieldCount != OctavoAppearance.NATIVE_FIELD_COUNT) {
                throw new IOException("Invalid restart evidence header");
            }
            requireValidKey(key);
            if (originSpineIndex < 0
                || originSpineIndex > 0xFFFFFFFFL
                || originByteOffset < 0
                || spineIndex < 0
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
            OctavoProgressDisplay progressDisplay =
                OctavoProgressDisplay.fromNativeId(progressDisplayId);
            if (decoded == null || progressDisplay == null) {
                throw new IOException("Invalid restart evidence appearance");
            }
            return new ExpectedState(key,
                                     originSpineIndex,
                                     originByteOffset,
                                     spineIndex,
                                     byteOffset,
                                     decoded,
                                     progressDisplay);
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
