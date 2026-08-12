package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibraryMembershipIntegrationTest {
    private static final String ALPHA_ASSET =
        "port6/octavo_port6_alpha.epub";
    private static final long ALPHA_BYTE_COUNT = 44_190L;
    private static final String ALPHA_SHA256 =
        "dd92f87fa70ea37f761cb9348d5f7b2939afea2661f9f4fe16828ac6ca041f80";
    private static final String ALPHA_TITLE = "Port 6 Alpha Book";

    private Context context;
    private File alpha;

    @Before
    public void resetAndStageFixture() throws IOException {
        context = ApplicationProvider.getApplicationContext();
        clearAll(context);
        alpha = new File(context.getCacheDir(),
                         "membership-integration-alpha.epub");
        try (InputStream input = context.getAssets().open(ALPHA_ASSET);
             FileOutputStream output = new FileOutputStream(alpha, false)) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.flush();
        }
        assertEquals(ALPHA_BYTE_COUNT, alpha.length());
    }

    @After
    public void clearState() {
        clearAll(context);
        assertTrue(alpha == null || !alpha.exists() || alpha.delete());
    }

    @Test
    public void stagedReviewBackDefersExactlyAndRecreationNeedsExplicitOpen()
        throws Exception {
        seedCatalog(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        OctavoLibraryMembershipStore membership = membershipStore();
        byte[] stagedBytes = withdrawnBytes(
            ALPHA_SHA256, ALPHA_BYTE_COUNT, 1);
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult.STAGED_CURRENT,
            membership.stagePortableBytes(stagedBytes).result);
        OctavoLibraryMembershipStore.StagedPortable staged =
            membership.stagedPortable();
        assertNotNull(staged);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                OctavoLibraryMembershipPrompt prompt =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(prompt);
                assertEquals(
                    OctavoLibraryMembershipPrompt.Mode.STAGED_APPROVAL,
                    prompt.modeForTesting());
                activity.onBackPressed();
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertNull(activity.libraryMembershipPromptForTesting());
                assertTrue(activity
                               .libraryMembershipAttentionDeferredForTesting());
                assertNotNull(findButton(
                    activity, "Review pending membership attention"));
                OctavoLibraryMembershipStore.StagedPortable retained =
                    activity.libraryMembershipStoreForTesting()
                        .stagedPortable();
                assertNotNull(retained);
                assertTrue(retained.sameIdentity(staged));
                assertArrayEquals(stagedBytes, retained.bytes());
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNull(activity.libraryMembershipPromptForTesting());
                Button reopen = findButton(
                    activity, "Review pending membership attention");
                assertNotNull(reopen);
                assertTrue(reopen.performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                OctavoLibraryMembershipPrompt prompt =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(prompt);
                assertTrue(prompt.primaryForTesting().performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertNull(activity.libraryMembershipStoreForTesting()
                               .stagedPortable());
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
            });
        }
    }

    @Test
    public void localRowWithdrawRestoreNeverRemovesOrOpensTheBook()
        throws Exception {
        seedImportedBook();
        OctavoLibrarySyncStore sync = new OctavoLibrarySyncStore(context);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     sync.load());
        assertTrue(sync.recordLocalValidated(
            new OctavoLibraryPortable.Descriptor(
                ALPHA_SHA256, ALPHA_BYTE_COUNT)).succeeded());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                Button withdraw = findButton(
                    activity, "Withdraw from synchronized Library");
                assertNotNull(withdraw);
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                             withdraw.getLayoutParams().width);
                assertTrue(withdraw.performClick());
            });
            scenario.onActivity(activity -> {
                OctavoLibraryMembershipPrompt prompt =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(prompt);
                assertEquals(
                    OctavoLibraryMembershipPrompt.Mode.WITHDRAW_CONFIRMATION,
                    prompt.modeForTesting());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                OctavoLibraryMembershipPrompt prompt =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(prompt);
                assertEquals(
                    OctavoLibraryMembershipPrompt.Mode.WITHDRAW_CONFIRMATION,
                    prompt.modeForTesting());
                activity.onBackPressed();
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                Button withdraw = findButton(
                    activity, "Withdraw from synchronized Library");
                assertNotNull(withdraw);
                assertTrue(withdraw.hasFocus());
                assertTrue(withdraw.performClick());
                assertTrue(activity.libraryMembershipPromptForTesting()
                               .primaryForTesting().performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
                assertNotNull(activity.libraryStoreForTesting()
                                  .findBook(ALPHA_SHA256));
                assertTrue(activity.libraryStoreForTesting()
                               .managedFile(ALPHA_SHA256).isFile());
                assertNull(activity.activeBookKeyForTesting());
                Button restore = findButton(
                    activity, "Restore to synchronized Library");
                assertNotNull(restore);
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                             restore.getLayoutParams().width);
                assertTrue(restore.performClick());
                assertTrue(activity.libraryMembershipPromptForTesting()
                               .primaryForTesting().performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.MEMBER,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
                assertNotNull(activity.libraryStoreForTesting()
                                  .findBook(ALPHA_SHA256));
                assertNull(activity.activeBookKeyForTesting());
                assertTrue(activity.removeBookForTesting(ALPHA_SHA256));
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.MEMBER,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
                assertNull(activity.libraryStoreForTesting()
                               .findBook(ALPHA_SHA256));
            });
        }
    }

    @Test
    public void concurrentHistoryRequiresExplicitConflictResolution()
        throws Exception {
        seedCatalog(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            membershipDescriptor(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        OctavoLibraryMembershipPortable.Snapshot root = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                OctavoLibraryMembershipPortable.Snapshot.empty(),
                descriptor, actor(1), 1));
        OctavoLibraryMembershipPortable.Snapshot common = mutated(
            OctavoLibraryMembershipPortable.restore(
                root, descriptor, actor(2), 1));
        OctavoLibraryMembershipPortable.Snapshot withdrawn = mutated(
            OctavoLibraryMembershipPortable.withdraw(
                common, descriptor, actor(3), 1));
        OctavoLibraryMembershipPortable.Snapshot restored = mutated(
            OctavoLibraryMembershipPortable.restore(
                root, descriptor, actor(4), 1));
        OctavoLibraryMembershipPortable.MergeResult merged =
            OctavoLibraryMembershipPortable.merge(withdrawn, restored);
        assertEquals(OctavoLibraryMembershipPortable.MergeStatus.MERGED,
                     merged.status);
        approveMembership(OctavoLibraryMembershipPortable.encode(
            merged.snapshot()));

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                OctavoLibraryMembershipPrompt prompt =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(prompt);
                assertEquals(OctavoLibraryMembershipPrompt.Mode.CONFLICT,
                             prompt.modeForTesting());
                assertTrue(prompt.secondaryForTesting().performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.MEMBER,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
                assertNull(activity.libraryMembershipPromptForTesting());
            });
        }
    }

    @Test
    public void knownWithdrawalBlocksOfferUntilExplicitRestore()
        throws Exception {
        seedCatalog(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        approveMembership(withdrawnBytes(
            ALPHA_SHA256, ALPHA_BYTE_COUNT, 5));
        byte[] manifest = OctavoBookManifest.build(alpha).encode();

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.setAvailableBookManifestForTesting(
                    manifest));
                assertNull(activity.libraryCatalogOfferForTesting());
                assertNull(activity.librarySyncPromptForTesting());
                Button restore = findButton(
                    activity, "Restore to synchronized Library");
                assertNotNull(restore);
                assertTrue(restore.performClick());
                assertTrue(activity.libraryMembershipPromptForTesting()
                               .primaryForTesting().performClick());
            });
            waitForIdle();
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.MEMBER,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
                assertNotNull(activity.libraryCatalogOfferForTesting());
                assertNotNull(activity.librarySyncPromptForTesting());
                assertEquals(
                    OctavoLibrarySyncPrompt.Mode.OFFER,
                    activity.librarySyncPromptForTesting().modeForTesting());
            });
        }
    }

    @Test
    public void staleConfirmationCannotOverwriteNewMembershipHistory()
        throws Exception {
        seedCatalog(ALPHA_SHA256, ALPHA_BYTE_COUNT);
        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.beginLibraryMembershipWithdrawForTesting(
                    ALPHA_SHA256, ALPHA_BYTE_COUNT));
                OctavoLibraryMembershipPrompt stale =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(stale);
                OctavoLibraryMembershipPortable.Descriptor descriptor =
                    membershipDescriptor(ALPHA_SHA256, ALPHA_BYTE_COUNT);
                OctavoLibraryMembershipStore.Receipt fresh =
                    activity.libraryMembershipStoreForTesting()
                        .receipt(descriptor);
                assertNotNull(fresh);
                assertTrue(activity.libraryMembershipStoreForTesting()
                               .withdraw(fresh).succeeded());
                long generation = activity.libraryMembershipStoreForTesting()
                    .stateGeneration();
                assertTrue(stale.primaryForTesting().performClick());
                assertEquals(generation,
                             activity.libraryMembershipStoreForTesting()
                                 .stateGeneration());
                assertEquals(
                    OctavoLibraryMembershipPortable.Projection.WITHDRAWN,
                    activity.libraryMembershipStoreForTesting()
                        .projection(ALPHA_SHA256));
            });
        }
    }

    @Test
    public void futurePrivateStateIsPreservedAndBackOnlyDefersItsUi()
        throws Exception {
        OctavoLibraryMembershipStore seed =
            new OctavoLibraryMembershipStore(context);
        File state = seed.stateFileForTesting();
        assertTrue(state.getParentFile().isDirectory()
                   || state.getParentFile().mkdirs());
        byte[] future = new byte[8];
        writeInt(future, 0,
                 OctavoLibraryMembershipStore.storeMagicForTesting());
        writeInt(future, 4,
                 OctavoLibraryMembershipStore.storeVersionForTesting() + 1);
        Files.write(state.toPath(), future);

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(
                    OctavoLibraryMembershipStore.LoadStatus
                        .FUTURE_VERSION_BLOCKED,
                    activity.libraryMembershipStoreForTesting()
                        .loadStatus());
                OctavoLibraryMembershipPrompt prompt =
                    activity.libraryMembershipPromptForTesting();
                assertNotNull(prompt);
                assertEquals(
                    OctavoLibraryMembershipPrompt.Mode.RETAINED_ATTENTION,
                    prompt.modeForTesting());
                assertTrue(prompt.statusForTesting().getText().toString()
                               .contains("newer version"));
                activity.onBackPressed();
            });
            waitForIdle();
            assertArrayEquals(future, Files.readAllBytes(state.toPath()));
            scenario.onActivity(activity -> {
                assertNull(activity.libraryMembershipPromptForTesting());
                assertTrue(activity
                               .libraryMembershipAttentionDeferredForTesting());
                assertNotNull(findButton(
                    activity, "Review pending membership attention"));
                assertTrue(activity.findViewById(
                    R.id.octavo_library_add).isEnabled());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNull(activity.libraryMembershipPromptForTesting());
                assertEquals(
                    OctavoLibraryMembershipStore.LoadStatus
                        .FUTURE_VERSION_BLOCKED,
                    activity.libraryMembershipStoreForTesting()
                        .loadStatus());
            });
            assertArrayEquals(future, Files.readAllBytes(state.toPath()));
        }
    }

    private void seedImportedBook() throws IOException {
        OctavoLibraryStore library = new OctavoLibraryStore(context);
        library.loadCatalog(new File(OctavoFixture.install(context)));
        OctavoLibraryStore.Book staged =
            library.importDocument(Uri.fromFile(alpha));
        assertTrue(library.verifyBookIdentity(staged));
        OctavoLibraryStore.Book managed =
            library.publishReader0ValidatedImport(staged);
        assertTrue(library.recordOpened(managed, ALPHA_TITLE));
        assertTrue(library.completeImportedCatalogAssociation(managed));
    }

    private void seedCatalog(String digest, long byteCount)
        throws IOException {
        OctavoLibrarySyncStore sync = new OctavoLibrarySyncStore(context);
        assertEquals(OctavoLibrarySyncStore.LoadStatus.MISSING_EMPTY,
                     sync.load());
        assertEquals(
            OctavoLibrarySyncStore.PortableStageResult.STAGED_CURRENT,
            sync.stagePortableBytes(
                OctavoLibraryPortable.simulatedRemoteBytes(
                    Collections.singletonList(
                        new OctavoLibraryPortable.Descriptor(
                            digest, byteCount)))));
        OctavoLibrarySyncStore.StagedPortable staged =
            sync.stagedPortable();
        assertNotNull(staged);
        assertEquals(
            OctavoLibrarySyncStore.PortableMergeResult.MERGED,
            sync.approveStagedPortable(staged.sha256));
    }

    private OctavoLibraryMembershipStore membershipStore() {
        OctavoLibraryMembershipStore store =
            new OctavoLibraryMembershipStore(context);
        assertEquals(
            OctavoLibraryMembershipStore.LoadStatus.MISSING_EMPTY,
            store.load());
        return store;
    }

    private void approveMembership(byte[] bytes) {
        OctavoLibraryMembershipStore store = membershipStore();
        assertEquals(
            OctavoLibraryMembershipStore.PortableStageResult.STAGED_CURRENT,
            store.stagePortableBytes(bytes).result);
        OctavoLibraryMembershipStore.StagedPortable staged =
            store.stagedPortable();
        assertNotNull(staged);
        assertEquals(
            OctavoLibraryMembershipStore.PortableApprovalResult.MERGED,
            store.approveStagedPortable(staged));
    }

    private static byte[] withdrawnBytes(String digest,
                                         long byteCount,
                                         int actor) throws IOException {
        OctavoLibraryMembershipPortable.MutationResult result =
            OctavoLibraryMembershipPortable.withdraw(
                OctavoLibraryMembershipPortable.Snapshot.empty(),
                membershipDescriptor(digest, byteCount), actor(actor), 1);
        return OctavoLibraryMembershipPortable.encode(mutated(result));
    }

    private static OctavoLibraryMembershipPortable.Descriptor
        membershipDescriptor(String digest, long byteCount) {
        return new OctavoLibraryMembershipPortable.Descriptor(
            digest, byteCount);
    }

    private static OctavoLibraryMembershipPortable.Snapshot mutated(
        OctavoLibraryMembershipPortable.MutationResult result) {
        assertEquals(OctavoLibraryMembershipPortable.MutationStatus.MUTATED,
                     result.status);
        assertNotNull(result.snapshot());
        return result.snapshot();
    }

    private static String actor(int value) {
        return String.format(Locale.ROOT, "%032x", value);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    private static Button findButton(OctavoActivity activity,
                                     String exactText) {
        View root = activity.findViewById(R.id.octavo_library);
        return root == null ? null : findButton(root, exactText);
    }

    private static Button findButton(View view, String exactText) {
        if (view instanceof Button
            && exactText.contentEquals(((Button)view).getText())) {
            return (Button)view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int index = 0; index < group.getChildCount(); ++index) {
                Button found = findButton(
                    group.getChildAt(index), exactText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        OctavoLibraryMembershipStore.clearForTesting(context);
        OctavoLibraryStore.clearForTesting(context);
        OctavoLibrarySyncStore.clearForTesting(context);
        OctavoBookTransferStore.clearForTesting(context);
        OctavoReadingPositionStore.clearForTesting(context);
        OctavoProgressStore.clearForTesting(context);
        OctavoProgressSyncStore.clearForTesting(context);
        OctavoAppearanceStore.clearForTesting(context);
        OctavoAppearanceSyncStore.clearForTesting(context);
        OctavoAnnotationStore.clearForTesting(context);
        OctavoNoteDraftStore.clearForTesting(context);
    }
}
