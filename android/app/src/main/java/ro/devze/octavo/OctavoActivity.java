package ro.devze.octavo;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class OctavoActivity extends Activity {
    private static final int REQUEST_ADD_EPUB = 6001;
    private static final String STATE_ACTIVE_BOOK_KEY =
        "octavo.port6.active_book_key";
    private static final String STATE_CHROME_VISIBLE =
        "octavo.port7.chrome_visible";
    private static final String STATE_POSITION_REVIEW_PENDING =
        "octavo.port11.position_review_pending";
    private static final String STATE_POSITION_RETRY_ACTION =
        "octavo.port11.position_retry_action";
    private static final String STATE_POSITION_RETRY_BOOK =
        "octavo.port11.position_retry_book";
    private static final String STATE_POSITION_RETRY_DEVICE =
        "octavo.port11.position_retry_device";
    private static final String STATE_POSITION_RETRY_SEQUENCE =
        "octavo.port11.position_retry_sequence";
    private static final String STATE_POSITION_RETRY_SPINE =
        "octavo.port11.position_retry_spine";
    private static final String STATE_POSITION_RETRY_BYTE =
        "octavo.port11.position_retry_byte";
    private static final String STATE_POSITION_RETRY_EPOCH =
        "octavo.port11.position_retry_epoch";
    private static final String STATE_POSITION_RETRY_ORIGIN_SEQUENCE =
        "octavo.port11.position_retry_origin_sequence";
    private static final String STATE_POSITION_RETRY_ORIGIN_SPINE =
        "octavo.port11.position_retry_origin_spine";
    private static final String STATE_POSITION_RETRY_ORIGIN_BYTE =
        "octavo.port11.position_retry_origin_byte";
    private static final String STATE_APPEARANCE_REVIEW_PENDING =
        "octavo.port11.appearance_review_pending";
    private static final String STATE_APPEARANCE_RETRY_ACTION =
        "octavo.port11.appearance_retry_action";
    private static final String STATE_APPEARANCE_RETRY_DEVICE =
        "octavo.port11.appearance_retry_device";
    private static final String STATE_APPEARANCE_RETRY_SEQUENCE =
        "octavo.port11.appearance_retry_sequence";
    private static final String STATE_APPEARANCE_RETRY_EPOCH =
        "octavo.port11.appearance_retry_epoch";
    private static final String STATE_APPEARANCE_RETRY_ORIGIN_SEQUENCE =
        "octavo.port11.appearance_retry_origin_sequence";
    private static final String STATE_APPEARANCE_RETRY_LOCAL_SEQUENCE =
        "octavo.port11.appearance_retry_local_sequence";
    private static final String STATE_APPEARANCE_RETRY_REMOTE_DEVICE =
        "octavo.port11.appearance_retry_remote_device";
    private static final String STATE_APPEARANCE_RETRY_REMOTE_SEQUENCE =
        "octavo.port11.appearance_retry_remote_sequence";
    private static final String STATE_APPEARANCE_RETRY_PENDING_KIND =
        "octavo.port11.appearance_retry_pending_kind";
    private static final String STATE_APPEARANCE_REVIEW_EPOCH_BEFORE_RETRY =
        "octavo.port11.appearance_review_epoch_before_retry";
    private static final String STATE_APPEARANCE_ROLLBACK_EPOCH_RETRY =
        "octavo.port11.appearance_rollback_epoch_retry";
    private static final String STATE_APPEARANCE_ABANDON_AFTER_RELOAD =
        "octavo.port11.appearance_abandon_after_reload";
    private static final int POSITION_RETRY_MARK_GO = 1;
    private static final int POSITION_RETRY_STAY = 2;
    private static final int POSITION_RETRY_DISMISS = 3;
    private static final int APPEARANCE_RETRY_RELOAD = 1;
    private static final int APPEARANCE_RETRY_USE = 2;
    private static final int APPEARANCE_RETRY_KEEP = 3;
    private static final int APPEARANCE_RETRY_DISMISS = 4;
    private static final int APPEARANCE_RETRY_FORWARD = 5;
    private static final int APPEARANCE_RETRY_ROLLBACK = 6;
    private static final int SEARCH_BUSY_RETRY_LIMIT = 96;
    private static final long SEARCH_BUSY_RETRY_DELAY_MILLIS = 32;

    private static final class ReadingPositionChoiceRetry {
        final int action;
        final String bookDigest;
        final String deviceId;
        final long sequence;
        final long spineIndex;
        final long byteOffset;
        final long reviewEpoch;
        final long originSequence;
        final long originSpineIndex;
        final long originByteOffset;

        ReadingPositionChoiceRetry(
            int action,
            OctavoReadingPositionStore.Candidate candidate) {
            this(action,
                 candidate.bookDigest,
                 candidate.deviceId,
                 candidate.sequence,
                 candidate.spineIndex,
                 candidate.byteOffset,
                 candidate.reviewEpoch,
                 candidate.originSequence,
                 candidate.originSpineIndex,
                 candidate.originByteOffset);
        }

        private ReadingPositionChoiceRetry(int action,
                                           String bookDigest,
                                           String deviceId,
                                           long sequence,
                                           long spineIndex,
                                           long byteOffset,
                                           long reviewEpoch,
                                           long originSequence,
                                           long originSpineIndex,
                                           long originByteOffset) {
            this.action = action;
            this.bookDigest = bookDigest;
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
            this.reviewEpoch = reviewEpoch;
            this.originSequence = originSequence;
            this.originSpineIndex = originSpineIndex;
            this.originByteOffset = originByteOffset;
        }

        boolean matches(OctavoReadingPositionStore.Candidate candidate) {
            return candidate != null
                && bookDigest.equals(candidate.bookDigest)
                && deviceId.equals(candidate.deviceId)
                && sequence == candidate.sequence
                && spineIndex == candidate.spineIndex
                && byteOffset == candidate.byteOffset
                && reviewEpoch == candidate.reviewEpoch
                && originSequence == candidate.originSequence
                && originSpineIndex == candidate.originSpineIndex
                && originByteOffset == candidate.originByteOffset;
        }

        void save(Bundle state) {
            state.putInt(STATE_POSITION_RETRY_ACTION, action);
            state.putString(STATE_POSITION_RETRY_BOOK, bookDigest);
            state.putString(STATE_POSITION_RETRY_DEVICE, deviceId);
            state.putLong(STATE_POSITION_RETRY_SEQUENCE, sequence);
            state.putLong(STATE_POSITION_RETRY_SPINE, spineIndex);
            state.putLong(STATE_POSITION_RETRY_BYTE, byteOffset);
            state.putLong(STATE_POSITION_RETRY_EPOCH, reviewEpoch);
            state.putLong(
                STATE_POSITION_RETRY_ORIGIN_SEQUENCE, originSequence);
            state.putLong(STATE_POSITION_RETRY_ORIGIN_SPINE,
                          originSpineIndex);
            state.putLong(STATE_POSITION_RETRY_ORIGIN_BYTE,
                          originByteOffset);
        }

        static ReadingPositionChoiceRetry restore(Bundle state) {
            if (state == null) {
                return null;
            }
            int action = state.getInt(STATE_POSITION_RETRY_ACTION, 0);
            String bookDigest = state.getString(STATE_POSITION_RETRY_BOOK);
            String deviceId = state.getString(STATE_POSITION_RETRY_DEVICE);
            long sequence = state.getLong(STATE_POSITION_RETRY_SEQUENCE, 0);
            long spineIndex = state.getLong(STATE_POSITION_RETRY_SPINE, -1);
            long byteOffset = state.getLong(STATE_POSITION_RETRY_BYTE, -1);
            long reviewEpoch = state.getLong(STATE_POSITION_RETRY_EPOCH, 0);
            long originSequence = state.getLong(
                STATE_POSITION_RETRY_ORIGIN_SEQUENCE, 0);
            long originSpineIndex = state.getLong(
                STATE_POSITION_RETRY_ORIGIN_SPINE, -1);
            long originByteOffset = state.getLong(
                STATE_POSITION_RETRY_ORIGIN_BYTE, -1);
            if ((action != POSITION_RETRY_MARK_GO
                 && action != POSITION_RETRY_STAY
                 && action != POSITION_RETRY_DISMISS)
                || !OctavoReadingPositionPortable.validBookDigest(bookDigest)
                || !OctavoReadingPositionPortable.validDeviceId(deviceId)
                || sequence <= 0 || reviewEpoch <= 0 || originSequence <= 0
                || !OctavoReadingPositionPortable.validAnchor(
                    spineIndex, byteOffset)
                || !OctavoReadingPositionPortable.validAnchor(
                    originSpineIndex, originByteOffset)) {
                return null;
            }
            return new ReadingPositionChoiceRetry(
                action, bookDigest, deviceId, sequence,
                spineIndex, byteOffset, reviewEpoch,
                originSequence, originSpineIndex, originByteOffset);
        }
    }

    private static final class AppearanceSyncRetryDescriptor {
        final int action;
        final String deviceId;
        final long sequence;
        final long reviewEpoch;
        final long originSequence;
        final long localSequence;
        final String remoteDeviceId;
        final long remoteSequence;
        final int pendingKind;

        private AppearanceSyncRetryDescriptor(
            int action, String deviceId, long sequence,
            long reviewEpoch, long originSequence, long localSequence,
            String remoteDeviceId, long remoteSequence, int pendingKind) {
            this.action = action;
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.reviewEpoch = reviewEpoch;
            this.originSequence = originSequence;
            this.localSequence = localSequence;
            this.remoteDeviceId = remoteDeviceId;
            this.remoteSequence = remoteSequence;
            this.pendingKind = pendingKind;
        }

        static AppearanceSyncRetryDescriptor reload() {
            return new AppearanceSyncRetryDescriptor(
                APPEARANCE_RETRY_RELOAD, null, 0, 0, 0,
                0, null, 0, 0);
        }

        static AppearanceSyncRetryDescriptor candidate(
            int action, OctavoAppearanceSyncStore.Candidate candidate) {
            return new AppearanceSyncRetryDescriptor(
                action, candidate.deviceId, candidate.sequence,
                candidate.reviewEpoch, candidate.originLocalSequence,
                0, null, 0, 0);
        }

        static AppearanceSyncRetryDescriptor pending(
            int action, OctavoAppearanceSyncStore.Pending pending) {
            return new AppearanceSyncRetryDescriptor(
                action, null, 0, 0, pending.originLocalSequence,
                pending.localSequence, pending.remoteDeviceId,
                pending.remoteSequence,
                pending.kind == OctavoAppearanceSyncStore.PendingKind.LOCAL
                    ? 1 : 2);
        }

        boolean candidateAction() {
            return action >= APPEARANCE_RETRY_USE
                && action <= APPEARANCE_RETRY_DISMISS;
        }

        boolean pendingAction() {
            return action == APPEARANCE_RETRY_FORWARD
                || action == APPEARANCE_RETRY_ROLLBACK;
        }

        boolean matches(OctavoAppearanceSyncStore.Candidate candidate) {
            return candidateAction() && candidate != null
                && deviceId.equals(candidate.deviceId)
                && sequence == candidate.sequence
                && reviewEpoch == candidate.reviewEpoch
                && originSequence == candidate.originLocalSequence;
        }

        boolean matches(OctavoAppearanceSyncStore.Pending pending) {
            return pendingAction() && pending != null
                && originSequence == pending.originLocalSequence
                && localSequence == pending.localSequence
                && remoteDeviceId.equals(pending.remoteDeviceId)
                && remoteSequence == pending.remoteSequence
                && pendingKind
                   == (pending.kind
                       == OctavoAppearanceSyncStore.PendingKind.LOCAL
                           ? 1 : 2);
        }

        void save(Bundle state) {
            state.putInt(STATE_APPEARANCE_RETRY_ACTION, action);
            state.putString(STATE_APPEARANCE_RETRY_DEVICE, deviceId);
            state.putLong(STATE_APPEARANCE_RETRY_SEQUENCE, sequence);
            state.putLong(STATE_APPEARANCE_RETRY_EPOCH, reviewEpoch);
            state.putLong(STATE_APPEARANCE_RETRY_ORIGIN_SEQUENCE,
                          originSequence);
            state.putLong(STATE_APPEARANCE_RETRY_LOCAL_SEQUENCE,
                          localSequence);
            state.putString(STATE_APPEARANCE_RETRY_REMOTE_DEVICE,
                            remoteDeviceId);
            state.putLong(STATE_APPEARANCE_RETRY_REMOTE_SEQUENCE,
                          remoteSequence);
            state.putInt(STATE_APPEARANCE_RETRY_PENDING_KIND,
                         pendingKind);
        }

        static AppearanceSyncRetryDescriptor restore(Bundle state) {
            if (state == null) {
                return null;
            }
            int action = state.getInt(STATE_APPEARANCE_RETRY_ACTION, 0);
            if (action == APPEARANCE_RETRY_RELOAD) {
                return reload();
            }
            String device = state.getString(STATE_APPEARANCE_RETRY_DEVICE);
            long sequence = state.getLong(
                STATE_APPEARANCE_RETRY_SEQUENCE, 0);
            long epoch = state.getLong(STATE_APPEARANCE_RETRY_EPOCH, 0);
            long origin = state.getLong(
                STATE_APPEARANCE_RETRY_ORIGIN_SEQUENCE, 0);
            long local = state.getLong(
                STATE_APPEARANCE_RETRY_LOCAL_SEQUENCE, 0);
            String remote = state.getString(
                STATE_APPEARANCE_RETRY_REMOTE_DEVICE);
            long remoteSequence = state.getLong(
                STATE_APPEARANCE_RETRY_REMOTE_SEQUENCE, 0);
            int kind = state.getInt(
                STATE_APPEARANCE_RETRY_PENDING_KIND, 0);
            if (action >= APPEARANCE_RETRY_USE
                && action <= APPEARANCE_RETRY_DISMISS
                && OctavoAppearancePortable.validDeviceId(device)
                && sequence > 0 && epoch > 0 && origin > 0) {
                return new AppearanceSyncRetryDescriptor(
                    action, device, sequence, epoch, origin,
                    0, null, 0, 0);
            }
            if ((action == APPEARANCE_RETRY_FORWARD
                 || action == APPEARANCE_RETRY_ROLLBACK)
                && origin >= 0 && local > 0
                && OctavoAppearancePortable.validDeviceId(remote)
                && remoteSequence >= 0 && (kind == 1 || kind == 2)) {
                return new AppearanceSyncRetryDescriptor(
                    action, null, 0, 0, origin, local,
                    remote, remoteSequence, kind);
            }
            return null;
        }
    }

    private OctavoLibraryStore libraryStore;
    private OctavoAppearanceStore appearanceStore;
    private OctavoAppearanceSyncStore appearanceSyncStore;
    private OctavoAppearance appearance;
    private OctavoProgressStore progressStore;
    private OctavoProgressDisplay progressDisplay;
    private OctavoAnnotationStore annotationStore;
    private OctavoNoteDraftStore noteDraftStore;
    private OctavoReadingPositionStore readingPositionStore;
    private LinearLayout libraryRoot;
    private FrameLayout systemBarRoot;
    private View statusBarScrim;
    private View navigationBarScrim;
    private FrameLayout readerRoot;
    private LinearLayout readerTopChrome;
    private LinearLayout readerBottomChrome;
    private Button readerLibrary;
    private Button readerSearch;
    private Button readerSettings;
    private Button readerBookmarkToggle;
    private Button readerReturn;
    private Button readerProgress;
    private Button readerBookmarks;
    private FrameLayout appearanceOverlay;
    private OctavoAppearancePanel appearancePanel;
    private FrameLayout navigationOverlay;
    private OctavoNavigationPanel navigationPanel;
    private FrameLayout searchOverlay;
    private OctavoSearchPanel searchPanel;
    private FrameLayout bookmarksOverlay;
    private OctavoBookmarksPanel bookmarksPanel;
    private FrameLayout readingPositionOverlay;
    private OctavoReadingPositionPrompt readingPositionPrompt;
    private FrameLayout appearanceSyncOverlay;
    private OctavoAppearanceSyncPrompt appearanceSyncPrompt;
    private TextView failureBanner;
    private boolean failureBannerAnnouncementDeferred;
    private View readerEntryCover;
    private int readerEntryCoverGeneration;
    private View appearanceTransitionScrim;
    private OctavoAppearance appearanceTransitionTarget;
    private int appearanceTransitionGeneration;
    private OctavoSurfaceView surfaceView;
    private OctavoLibraryStore.Book activeBook;
    private boolean activityResumed;
    private boolean chromeVisible;
    private String lastOpenError;
    private String deferredAppearanceFailure;
    private String deferredProgressFailure;
    private OctavoAppearance pendingAppearancePersistence;
    private boolean appearancePersistencePosted;
    private boolean appearanceSyncReviewPending;
    private boolean appearanceSyncReviewInitialized;
    private boolean appearanceSyncPendingLoaded;
    private boolean appearanceSyncAwaitingExplicitRetry;
    private boolean appearanceSyncRollbackRequested;
    private boolean appearanceSyncUnstagedRollbackRequested;
    private OctavoAppearance appearanceSyncUnstagedOrigin;
    private OctavoAppearanceSyncStore.Candidate appearanceSyncCandidate;
    private OctavoAppearanceSyncStore.Candidate
        appearanceSyncPromptCandidate;
    private long appearanceSyncPromptGeneration;
    private OctavoAppearanceSyncStore.Pending appearanceSyncPending;
    private Runnable appearanceSyncRetry;
    private String appearanceSyncFailureHeading;
    private String appearanceSyncFailureMessage;
    private AppearanceSyncRetryDescriptor appearanceSyncRetryDescriptor;
    private long appearanceSyncReviewEpochBeforeRetry = -1;
    private boolean appearanceSyncRollbackEpochReconciliation;
    private boolean appearanceSyncStageUncertain;
    private boolean appearanceSyncAbandonAfterReload;
    private OctavoSurfaceView appearanceReceiptSurface;
    private OctavoSurfaceView.AppearancePresentationReceipt
        latestAppearanceReceipt;
    private OctavoSurfaceView consumedAppearanceReceiptSurface;
    private OctavoAppearance consumedAppearanceReceiptProfile;
    private long consumedAppearanceReceiptGeneration = -1;
    private long consumedAppearanceReceiptFrame = -1;
    private OctavoProgressDisplay pendingProgressPersistence;
    private boolean progressPersistencePosted;
    private boolean navigationSnapshotRefreshPosted;
    private boolean searchSnapshotRefreshPosted;
    private boolean bookmarkNavigationPending;
    private boolean noteSelectionRetained;
    private boolean readingPositionReviewPending;
    private boolean readingPositionReviewInitialized;
    private boolean hasPresentedReadingPosition;
    private long presentedReadingSpineIndex;
    private long presentedReadingByteOffset;
    private long presentedReadingPageSpineIndex;
    private long presentedReadingPageFirstByte;
    private long presentedReadingPageOnePastLastByte;
    private long presentedReadingFrameCount;
    private OctavoReadingPositionStore.Candidate readingPositionCandidate;
    private Runnable readingPositionRetry;
    private boolean readingPositionAwaitingExplicitRetry;
    private ReadingPositionChoiceRetry readingPositionChoiceRetry;
    private final Runnable persistAppearance = () -> {
        appearancePersistencePosted = false;
        flushAppearancePersistence();
    };
    private final Runnable persistProgress = () -> {
        progressPersistencePosted = false;
        flushProgressPersistence();
    };
    private final Runnable refreshNavigationSnapshot = () -> {
        navigationSnapshotRefreshPosted = false;
        if (navigationPanel == null || surfaceView == null) {
            return;
        }
        navigationPanel.updateSnapshot(surfaceView.navigationSnapshot());
    };
    private final Runnable refreshSearchSnapshot = () -> {
        searchSnapshotRefreshPosted = false;
        if (searchPanel == null || surfaceView == null) {
            return;
        }
        searchPanel.updateSnapshot(surfaceView.searchSnapshot());
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                this::onBackPressed);
        }
        appearanceStore = new OctavoAppearanceStore(this);
        appearance = appearanceStore.load();
        boolean appearanceResetAfterCorruption =
            appearanceStore.recoveredFromCorruption();
        appearanceSyncStore = new OctavoAppearanceSyncStore(this);
        OctavoAppearanceSyncStore.LoadStatus appearanceSyncLoadStatus =
            appearanceSyncStore.load();
        appearanceSyncPendingLoaded =
            appearanceSyncStore.pending() != null;
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.restore(savedInstanceState);
        appearanceSyncReviewEpochBeforeRetry = savedInstanceState == null
            ? -1 : savedInstanceState.getLong(
                STATE_APPEARANCE_REVIEW_EPOCH_BEFORE_RETRY, -1);
        appearanceSyncRollbackEpochReconciliation =
            savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_APPEARANCE_ROLLBACK_EPOCH_RETRY, false);
        appearanceSyncAbandonAfterReload = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_APPEARANCE_ABANDON_AFTER_RELOAD, false);
        appearanceSyncStageUncertain = appearanceSyncAbandonAfterReload;
        if (appearanceSyncAbandonAfterReload) {
            appearanceSyncAwaitingExplicitRetry = true;
        }
        if (appearanceSyncRetryDescriptor != null
            && appearanceSyncRetryDescriptor.action
               == APPEARANCE_RETRY_RELOAD) {
            appearanceSyncAwaitingExplicitRetry = true;
        }
        if (appearanceSyncRetryDescriptor != null
            && appearanceSyncRetryDescriptor.pendingAction()) {
            OctavoAppearanceSyncStore.Pending restoredPending =
                appearanceSyncStore.pending();
            if (!appearanceSyncRetryDescriptor.matches(restoredPending)) {
                if (appearanceSyncReviewEpochBeforeRetry >= 0
                    && appearanceSyncRetryDescriptor.action
                       == APPEARANCE_RETRY_ROLLBACK) {
                    appearanceSyncRetryDescriptor =
                        AppearanceSyncRetryDescriptor.reload();
                    appearanceSyncAwaitingExplicitRetry = true;
                    appearanceSyncRollbackRequested = true;
                } else {
                    appearanceSyncRetryDescriptor = null;
                }
            } else {
                appearanceSyncRollbackRequested =
                    appearanceSyncRetryDescriptor.action
                    == APPEARANCE_RETRY_ROLLBACK;
            }
        }
        progressStore = new OctavoProgressStore(this);
        progressDisplay = progressStore.load();
        boolean progressResetAfterCorruption =
            progressStore.recoveredFromCorruption();
        annotationStore = new OctavoAnnotationStore(this);
        OctavoAnnotationStore.LoadStatus annotationLoadStatus =
            annotationStore.load();
        noteDraftStore = new OctavoNoteDraftStore(this);
        OctavoNoteDraftStore.LoadStatus noteDraftLoadStatus =
            noteDraftStore.load();
        readingPositionStore = new OctavoReadingPositionStore(this);
        OctavoReadingPositionStore.LoadStatus readingPositionLoadStatus =
            readingPositionStore.load();
        chromeVisible = savedInstanceState != null
            && savedInstanceState.getBoolean(STATE_CHROME_VISIBLE, false);
        applyWindowAppearance();
        libraryStore = new OctavoLibraryStore(this);
        File fixture = new File(OctavoFixture.install(this));
        libraryStore.loadCatalog(fixture);

        String restoreKey = savedInstanceState == null
            ? null
            : savedInstanceState.getString(STATE_ACTIVE_BOOK_KEY);
        boolean restoreReviewPending = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_POSITION_REVIEW_PENDING, false);
        boolean restoreAppearanceReviewPending = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_APPEARANCE_REVIEW_PENDING, false);
        OctavoLibraryStore.Book restoreBook =
            restoreKey == null ? null : libraryStore.findBook(restoreKey);
        boolean readerRestored = restoreBook != null
            && showReader(restoreBook, false);
        if (!readerRestored) {
            showLibrary();
        } else {
            readingPositionReviewPending = restoreReviewPending;
            appearanceSyncReviewPending =
                restoreAppearanceReviewPending;
            ReadingPositionChoiceRetry restoredRetry =
                ReadingPositionChoiceRetry.restore(savedInstanceState);
            if (restoredRetry != null
                && activeBook.key.equals(restoredRetry.bookDigest)) {
                readingPositionChoiceRetry = restoredRetry;
            }
        }
        if (appearanceResetAfterCorruption) {
            showOpenFailure(
                "Reader appearance was reset because its saved settings were invalid");
        }
        if (progressResetAfterCorruption) {
            showOpenFailure(
                "Reader progress display was reset because its saved setting was invalid");
        }
        reportAnnotationLoadStatus(annotationLoadStatus);
        reportNoteDraftLoadStatus(noteDraftLoadStatus);
        reportReadingPositionLoadStatus(readingPositionLoadStatus);
        reportAppearanceSyncLoadStatus(appearanceSyncLoadStatus);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (activeBook != null) {
            state.putString(STATE_ACTIVE_BOOK_KEY, activeBook.key);
        }
        state.putBoolean(STATE_CHROME_VISIBLE, chromeVisible);
        state.putBoolean(
            STATE_POSITION_REVIEW_PENDING,
            readingPositionReviewPending);
        state.putBoolean(
            STATE_APPEARANCE_REVIEW_PENDING,
            appearanceSyncReviewPending);
        if (readingPositionChoiceRetry != null && activeBook != null
            && activeBook.key.equals(
                readingPositionChoiceRetry.bookDigest)) {
            readingPositionChoiceRetry.save(state);
        }
        AppearanceSyncRetryDescriptor appearanceRetry =
            appearanceSyncRetryDescriptor;
        OctavoAppearanceSyncStore.Pending syncPending =
            appearanceSyncStore == null ? null
                : appearanceSyncStore.pending();
        if (syncPending != null) {
            appearanceRetry = AppearanceSyncRetryDescriptor.pending(
                appearanceSyncRollbackRequested
                    ? APPEARANCE_RETRY_ROLLBACK
                    : APPEARANCE_RETRY_FORWARD,
                syncPending);
        } else if (appearanceSyncAwaitingExplicitRetry
                   && appearanceRetry == null) {
            appearanceRetry = AppearanceSyncRetryDescriptor.reload();
        }
        if (appearanceRetry != null) {
            appearanceRetry.save(state);
        }
        if (appearanceSyncReviewEpochBeforeRetry >= 0) {
            state.putLong(
                STATE_APPEARANCE_REVIEW_EPOCH_BEFORE_RETRY,
                appearanceSyncReviewEpochBeforeRetry);
            state.putBoolean(
                STATE_APPEARANCE_ROLLBACK_EPOCH_RETRY,
                appearanceSyncRollbackEpochReconciliation);
        }
        if (appearanceSyncAbandonAfterReload) {
            state.putBoolean(
                STATE_APPEARANCE_ABANDON_AFTER_RELOAD, true);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyWindowAppearance();
        updateAppearancePanelWidth();
        updateNavigationPanelWidth();
        updateSearchPanelWidth();
        updateBookmarksPanelWidth();
        if (navigationPanel != null) {
            navigationPanel.applyAppearance(appearance);
        }
        if (searchPanel != null) {
            searchPanel.applyAppearance(appearance);
        }
        if (bookmarksPanel != null) {
            bookmarksPanel.applyAppearance(appearance);
        }
        if (readingPositionPrompt != null) {
            readingPositionPrompt.applyAppearance(appearance);
            if (readingPositionOverlay != null) {
                readingPositionOverlay.setBackgroundColor(
                    readingPositionPrompt.overlayColor());
            }
            updateReadingPositionPromptBounds();
        }
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.applyAppearance(appearance);
            if (appearanceSyncOverlay != null) {
                appearanceSyncOverlay.setBackgroundColor(
                    appearanceSyncPrompt.overlayColor());
            }
            updateAppearanceSyncPromptBounds();
        }
        if (surfaceView != null) {
            surfaceView.reapplyAppearance();
        }
        restorePendingReadingPositionAfterLifecycle();
        restorePendingAppearanceAfterLifecycle();
    }

    @Override
    public void onBackPressed() {
        if (appearanceSyncPrompt != null) {
            dismissAppearanceSyncForBack();
        } else if (readingPositionPrompt != null) {
            dismissReadingPositionForBack();
        } else if (appearancePanel != null) {
            closeAppearancePanel();
        } else if (bookmarksPanel != null) {
            closeBookmarksPanel();
        } else if (searchPanel != null) {
            closeSearchPanel();
        } else if (navigationPanel != null) {
            closeNavigationPanel();
        } else if (surfaceView != null
                   && surfaceView.dismissSelectionForBack()) {
            // Text selection is a transient reader mode and owns this Back.
            OctavoSurfaceView selectionOwner = surfaceView;
            selectionOwner.post(() -> {
                if (surfaceView != selectionOwner
                    || selectionOwner.hasSelectionForAccessibility()) {
                    return;
                }
                processAppearancePresentationReceipt();
                considerAppearanceSyncCandidate();
            });
        } else if (surfaceView != null
                   && surfaceView.hasNavigationPending()) {
            // A destination remains provisional until its frame is posted.
            // Back consumes this event instead of exposing provisional state.
            return;
        } else if (surfaceView != null
                   && surfaceView.canReturnInHistory()) {
            surfaceView.requestHistoryNavigation(false);
        } else if (surfaceView != null) {
            showLibrary();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (surfaceView != null) {
            surfaceView.hostResumed();
        }
        restorePendingReadingPositionAfterLifecycle();
        restorePendingAppearanceAfterLifecycle();
        if (readingPositionReviewInitialized) {
            considerReadingPositionCandidate();
        }
        if (appearanceSyncReviewInitialized) {
            considerAppearanceSyncCandidate();
        }
        if (deferredAppearanceFailure != null) {
            String message = deferredAppearanceFailure;
            deferredAppearanceFailure = null;
            showOpenFailure(message);
        }
        if (deferredProgressFailure != null) {
            String message = deferredProgressFailure;
            deferredProgressFailure = null;
            showOpenFailure(message);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (surfaceView != null) {
            surfaceView.hostPaused();
        }
        restorePendingReadingPositionAfterLifecycle();
        flushAppearancePersistence();
        flushProgressPersistence();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        flushAppearancePersistence();
        flushProgressPersistence();
        releaseReader();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ADD_EPUB || resultCode != RESULT_OK) {
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (uri == null) {
            showOpenFailure("The document picker returned no EPUB");
            return;
        }
        openDocument(uri);
    }

    private boolean openDocument(Uri uri) {
        OctavoLibraryStore.Book candidate;
        try {
            candidate = libraryStore.importDocument(uri);
        } catch (IOException | RuntimeException exception) {
            showOpenFailure("Unable to import the selected EPUB");
            return false;
        }
        boolean alreadyCataloged =
            libraryStore.findBook(candidate.key) != null;
        if (!showReader(candidate, true)) {
            if (!alreadyCataloged) {
                libraryStore.discardUncataloged(candidate);
            }
            showOpenFailure("The selected file is not a readable EPUB");
            return false;
        }
        lastOpenError = null;
        return true;
    }

    private boolean showReader(OctavoLibraryStore.Book requested,
                               boolean recordOpened) {
        long readerEntryStartedMillis = SystemClock.uptimeMillis();
        OctavoLibraryStore.Book current =
            libraryStore.findBook(requested.key);
        OctavoLibraryStore.Book target = current == null ? requested : current;
        OctavoLibraryStore.Session session =
            current == null
                ? new OctavoLibraryStore.Session(target)
                : libraryStore.sessionFor(target);
        if (session == null) {
            return false;
        }
        boolean strictResume = false;
        OctavoReadingPositionPortable.Lane synchronizedLane =
            readingPositionStore == null
                ? null : readingPositionStore.localLane(target.key);
        if (synchronizedLane != null) {
            session = new OctavoLibraryStore.Session(
                target,
                true,
                synchronizedLane.spineIndex,
                synchronizedLane.byteOffset);
            strictResume = true;
        }
        if (recordOpened) {
            chromeVisible = false;
        }

        OctavoSurfaceView replacement;
        try {
            replacement =
                new OctavoSurfaceView(
                    this,
                    libraryStore,
                    session,
                    strictResume,
                    appearance,
                    progressDisplay,
                    annotationStore.highlights(session.book.key),
                    annotationStore.notes(session.book.key),
                    chromeVisible,
                    readerEntryStartedMillis,
                    createReaderListener());
        } catch (RuntimeException exception) {
            return false;
        }
        String readerTitle = replacement.documentTitleForTesting();
        if (recordOpened
            && !libraryStore.recordOpened(target, readerTitle)) {
            replacement.release();
            return false;
        }
        target = libraryStore.findBook(target.key);
        if (target == null) {
            replacement.release();
            return false;
        }

        if (surfaceView != null || readerRoot != null || activeBook != null) {
            releaseReader();
        }
        FrameLayout root = createReaderRoot(replacement, readerTitle);
        FrameLayout windowRoot = createSystemBarFrame(
            root, 0,
            OctavoDesignTokens.forAppearance(appearance).readerPage);
        surfaceView = replacement;
        activeBook = target;
        readingPositionReviewPending = recordOpened;
        readingPositionReviewInitialized = false;
        appearanceSyncReviewPending = recordOpened;
        appearanceSyncReviewInitialized = false;
        hasPresentedReadingPosition = false;
        libraryRoot = null;
        readerRoot = root;
        installReaderEntryCover();
        setContentView(windowRoot, matchParentLayout());
        windowRoot.requestApplyInsets();
        updateBookmarkToggle();
        if (activityResumed) {
            replacement.hostResumed();
        }
        return true;
    }

    private FrameLayout createReaderRoot(OctavoSurfaceView replacement,
                                         String readerTitle) {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout root = new FrameLayout(this);
        root.setId(R.id.octavo_reader_root);
        root.setBackgroundColor(tokens.readerPage);
        root.addView(replacement, matchParentLayout());

        LinearLayout top = new LinearLayout(this);
        top.setId(R.id.octavo_reader_top_chrome);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClickable(true);
        top.setFocusable(false);
        top.setFocusableInTouchMode(false);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(tokens.chromeSurface);
        top.setElevation(dp(2));

        Button library = createThemedButton(
            getString(R.string.library),
            tokens.libraryReturn,
            tokens.onLibraryReturn);
        library.setId(R.id.octavo_reader_library);
        library.setContentDescription(getString(R.string.library));
        library.setOnClickListener(view -> showLibrary());
        top.addView(library, chromeButtonLayout());

        TextView title = new TextView(this);
        title.setText(readerTitle);
        title.setTextSize(16);
        title.setTextColor(tokens.chromeText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(12), 0, dp(12), 0);
        title.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(title, weightedLayout());

        Button bookmarkToggle = createThemedButton(
            "☆", tokens.buttonSurface, tokens.chromeText);
        bookmarkToggle.setId(R.id.octavo_reader_bookmark_toggle);
        bookmarkToggle.setContentDescription(
            getString(R.string.bookmark_add));
        bookmarkToggle.setOnClickListener(view -> toggleCurrentBookmark());
        top.addView(bookmarkToggle, chromeButtonLayout());

        Button search = createThemedButton(
            getString(R.string.reader_search),
            tokens.buttonSurface,
            tokens.chromeText);
        search.setId(R.id.octavo_reader_search);
        search.setContentDescription("Find in book");
        search.setOnClickListener(view -> openSearchPanel());
        top.addView(search, chromeButtonLayout());

        Button settings = createThemedButton(
            "Aa", tokens.buttonSurface, tokens.chromeText);
        settings.setId(R.id.octavo_reader_appearance);
        settings.setContentDescription("Reader appearance");
        settings.setOnClickListener(view -> openAppearancePanel());
        top.addView(settings, chromeButtonLayout());

        FrameLayout.LayoutParams topLayout =
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top, topLayout);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setId(R.id.octavo_reader_bottom_chrome);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setClickable(true);
        bottom.setFocusable(false);
        bottom.setFocusableInTouchMode(false);
        bottom.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottom.setBackgroundColor(tokens.chromeSurface);
        bottom.setElevation(dp(2));

        Button returnControl = createThemedButton(
            getString(R.string.navigation_return),
            tokens.buttonSurface,
            tokens.chromeText);
        returnControl.setId(R.id.octavo_reader_return);
        returnControl.setContentDescription(
            "Return to the previous reading position");
        returnControl.setOnClickListener(view -> {
            if (surfaceView != null) {
                surfaceView.requestHistoryNavigation(false);
            }
        });
        bottom.addView(returnControl, chromeButtonLayout());

        Button progress = createThemedButton(
            readerProgressLabel(replacement),
            tokens.buttonSurface,
            tokens.chromeText);
        progress.setId(R.id.octavo_reader_progress);
        updateProgressControlLabel(progress, progress.getText());
        progress.setTextSize(14);
        progress.setSingleLine(true);
        progress.setEllipsize(TextUtils.TruncateAt.END);
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(dp(8), 0, dp(8), 0);
        progress.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        progress.setOnClickListener(view -> openNavigationPanel());
        bottom.addView(progress, weightedLayout());

        Button bookmarks = createThemedButton(
            "Annotations",
            tokens.buttonSurface,
            tokens.chromeText);
        bookmarks.setId(R.id.octavo_reader_bookmarks);
        bookmarks.setContentDescription("Open annotations in this book");
        bookmarks.setOnClickListener(view -> openBookmarksPanel());
        bottom.addView(bookmarks, chromeButtonLayout());

        FrameLayout.LayoutParams bottomLayout =
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottom, bottomLayout);

        top.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bottom.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        library.setAccessibilityTraversalBefore(bookmarkToggle.getId());
        bookmarkToggle.setAccessibilityTraversalBefore(search.getId());
        search.setAccessibilityTraversalBefore(settings.getId());
        settings.setAccessibilityTraversalBefore(replacement.getId());
        replacement.setAccessibilityTraversalBefore(returnControl.getId());
        returnControl.setAccessibilityTraversalBefore(progress.getId());
        progress.setAccessibilityTraversalBefore(bookmarks.getId());
        bookmarks.setAccessibilityTraversalBefore(library.getId());
        library.setNextFocusForwardId(bookmarkToggle.getId());
        bookmarkToggle.setNextFocusForwardId(search.getId());
        search.setNextFocusForwardId(settings.getId());
        settings.setNextFocusForwardId(replacement.getId());
        replacement.setNextFocusForwardId(returnControl.getId());
        returnControl.setNextFocusForwardId(progress.getId());
        progress.setNextFocusForwardId(bookmarks.getId());
        bookmarks.setNextFocusForwardId(library.getId());
        library.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event, null, bookmarkToggle));
        bookmarkToggle.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event, library, search));
        search.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event, bookmarkToggle, settings));
        settings.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event, search, replacement));
        returnControl.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event, replacement, progress));
        progress.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event,
                returnControl.isShown() ? returnControl : replacement,
                bookmarks));
        bookmarks.setOnKeyListener((view, keyCode, event) ->
            moveReaderKeyboardFocus(
                keyCode, event, progress, library));
        readerTopChrome = top;
        readerBottomChrome = bottom;
        readerLibrary = library;
        readerSearch = search;
        readerSettings = settings;
        readerBookmarkToggle = bookmarkToggle;
        readerReturn = returnControl;
        readerProgress = progress;
        readerBookmarks = bookmarks;
        updateReaderNavigationAvailability(replacement);
        setChromeViewsVisible(chromeVisible, false);

        int chromeWidthSpec = View.MeasureSpec.makeMeasureSpec(
            getResources().getDisplayMetrics().widthPixels,
            View.MeasureSpec.EXACTLY);
        int chromeHeightSpec = View.MeasureSpec.makeMeasureSpec(
            0, View.MeasureSpec.UNSPECIFIED);
        top.measure(chromeWidthSpec, chromeHeightSpec);
        bottom.measure(chromeWidthSpec, chromeHeightSpec);
        replacement.setReaderChromeInsets(
            top.getMeasuredHeight(), bottom.getMeasuredHeight());
        root.addOnLayoutChangeListener(
            (view, left, upper, right, lower,
             oldLeft, oldUpper, oldRight, oldLower) ->
                updateReaderChromeComposition(
                    replacement, top, bottom, false));
        return root;
    }

    private void updateReaderChromeComposition(
        OctavoSurfaceView surface,
        View topChrome,
        View bottomChrome,
        boolean animate) {
        if (surface == null || topChrome == null || bottomChrome == null) {
            return;
        }
        int surfaceWidth = surface.getWidth();
        int surfaceHeight = surface.getHeight();
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            return;
        }
        int top = Math.max(
            0,
            Math.min(
                surfaceHeight,
                topChrome.getBottom() - surface.getTop()));
        int bottom = Math.max(
            0,
            Math.min(
                surfaceHeight - top,
                surface.getBottom() - bottomChrome.getTop()));
        if (top > 0 && bottom > 0) {
            surface.setReaderChromeInsets(top, bottom);
        }
        float scale = 1.0f;
        float translationX = 0.0f;
        float translationY = 0.0f;
        if (chromeVisible) {
            int availableHeight = Math.max(
                surfaceHeight - top - bottom, 1);
            scale = Math.min(
                1.0f, availableHeight / (float)surfaceHeight);
            translationX = (surfaceWidth - surfaceWidth * scale) / 2.0f;
            translationY = top
                + (availableHeight - surfaceHeight * scale) / 2.0f;
        }
        surface.setPivotX(0.0f);
        surface.setPivotY(0.0f);
        surface.animate().cancel();
        int duration = readerChromeMotionDuration(animate);
        if (!animate || duration == 0) {
            surface.setScaleX(scale);
            surface.setScaleY(scale);
            surface.setTranslationX(translationX);
            surface.setTranslationY(translationY);
            return;
        }
        surface.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationX(translationX)
            .translationY(translationY)
            .setDuration(duration)
            .start();
    }

    private OctavoSurfaceView.Listener createReaderListener() {
        return new OctavoSurfaceView.Listener() {
            @Override
            public void onChromeVisibilityChanged(boolean visible) {
                setChromeViewsVisible(visible, true);
            }

            @Override
            public void onAppearancePresented(
                OctavoAppearance presented) {
                appearance = presented;
                applyWindowAppearance();
                applyReaderAppearanceTokens();
                if (appearancePanel != null) {
                    appearancePanel.updatePresentedAppearance(presented);
                }
                processAppearancePresentationReceipt();
                finishAppearanceTransition(presented);
            }

            @Override
            public void onAppearanceFailure() {
                boolean hasNewerRequest = surfaceView != null
                    && surfaceView.hasPendingAppearanceRequest();
                if (!hasNewerRequest) {
                    if (appearancePanel != null) {
                        appearancePanel.updatePresentedAppearance(appearance);
                    }
                    cancelAppearanceTransition();
                }
                if (appearanceSyncStore != null
                    && appearanceSyncStore.pending() != null) {
                    OctavoAppearanceSyncStore.Pending pending =
                        appearanceSyncStore.pending();
                    showAppearanceSyncFailure(
                        "Reading settings update needs attention",
                        "The requested reading settings were not confirmed "
                            + "on screen. Retry is safe.",
                        appearanceSyncRollbackRequested
                            ? () -> beginPendingAppearanceRollback(
                                pending,
                                "Restoring your reading settings.")
                            : OctavoActivity.this::
                                retryPendingAppearanceForward);
                } else if (appearanceSyncUnstagedRollbackRequested) {
                    showAppearanceSyncFailure(
                        "Reading settings update needs attention",
                        "Your earlier reading settings were not confirmed "
                            + "on screen. Retry is safe.",
                        OctavoActivity.this::retryUnstagedAppearanceRollback);
                } else {
                    showOpenFailure("Unable to present reader appearance");
                }
            }

            @Override
            public void onReaderSurfaceFailure() {
                showOpenFailure(
                    "Unable to acquire reader surface; reopen the book");
            }

            @Override
            public void onReaderLocationSummaryFailure() {
                showOpenFailure(
                    "Whole-book progress is unavailable; you can keep reading");
            }

            @Override
            public void onPresentationRetriesExhausted(
                boolean appearanceStillAwaiting,
                boolean highlightStillAwaiting,
                boolean noteMarkerStillAwaiting) {
                if (!appearanceStillAwaiting) {
                    if (appearancePanel != null) {
                        appearancePanel.updatePresentedAppearance(
                            appearance);
                    }
                    cancelAppearanceTransition();
                }
                OctavoReadingPositionStore.Candidate pending =
                    activeBook == null || readingPositionStore == null
                        ? null
                        : readingPositionStore.pendingGo(activeBook.key);
                if (pending != null && readingPositionPrompt != null) {
                    readingPositionCandidate = pending;
                    showReadingPositionRetryableFailure(
                        "The requested page was not confirmed on screen. "
                            + "Retry is safe.");
                    readingPositionRetry = () ->
                        navigateToPendingReadingPosition(pending);
                }
                if (pending == null || highlightStillAwaiting
                    || noteMarkerStillAwaiting
                    || appearanceStillAwaiting) {
                    if (appearanceStillAwaiting
                        && appearanceSyncStore != null
                        && appearanceSyncStore.pending() != null) {
                        OctavoAppearanceSyncStore.Pending syncPending =
                            appearanceSyncStore.pending();
                        showAppearanceSyncFailure(
                            "Reading settings update needs attention",
                            "The requested reading settings were not "
                                + "confirmed on screen. Retry is safe.",
                            appearanceSyncRollbackRequested
                                ? () -> beginPendingAppearanceRollback(
                                    syncPending,
                                    "Restoring your reading settings.")
                                : OctavoActivity.this::
                                    retryPendingAppearanceForward);
                    } else if (appearanceStillAwaiting
                               && appearanceSyncUnstagedRollbackRequested) {
                        showAppearanceSyncFailure(
                            "Reading settings update needs attention",
                            "Your earlier reading settings were not "
                                + "confirmed on screen. Retry is safe.",
                            OctavoActivity.this::
                                retryUnstagedAppearanceRollback);
                    } else {
                        showOpenFailure(highlightStillAwaiting
                        ? "Highlight saved, but it could not be displayed. "
                            + "Reopen the book to retry."
                        : noteMarkerStillAwaiting
                            ? "Note saved, but its marker could not be displayed. "
                                + "Reopen the book to retry."
                            : "Unable to present reader changes; try again");
                    }
                }
            }

            @Override
            public void onAppearanceRequestsSettled(
                OctavoAppearance settled) {
                finishAppearanceTransition(settled);
                processAppearancePresentationReceipt();
            }

            @Override
            public void onReaderPresentationChanged(String label) {
                finishReaderEntryCover();
                processAppearancePresentationReceipt();
                if (readerProgress != null && label != null) {
                    updateProgressControlLabel(readerProgress, label);
                }
                updateReaderNavigationAvailability(surfaceView);
                scheduleNavigationSnapshotRefresh();
                scheduleSearchSnapshotRefresh();
                updateBookmarkToggle();
            }

            @Override
            public void onReadingPositionRestoreFailure() {
                showReadingPositionRestoreFailure();
            }

            @Override
            public void onReadingPositionPresented(
                long spineIndex,
                long byteOffset,
                long pageSpineIndex,
                long pageFirstByte,
                long pageOnePastLastByte,
                long frameCount) {
                handlePresentedReadingPosition(
                    spineIndex,
                    byteOffset,
                    pageSpineIndex,
                    pageFirstByte,
                    pageOnePastLastByte,
                    frameCount);
            }

            @Override
            public void onNavigationStateChanged() {
                updateReaderNavigationAvailability(surfaceView);
                scheduleNavigationSnapshotRefresh();
                scheduleSearchSnapshotRefresh();
            }

            @Override
            public void onStructuralNavigationPresented(long generation) {
                updateReaderNavigationAvailability(surfaceView);
                if (navigationPanel != null) {
                    closeNavigationPanel();
                }
                if (bookmarkNavigationPending) {
                    bookmarkNavigationPending = false;
                    closeBookmarksPanel();
                }
                scheduleSearchSnapshotRefresh();
                updateBookmarkToggle();
            }

            @Override
            public void onProgressDisplayPresented(
                OctavoProgressDisplay presented,
                long generation) {
                if (presented == null) {
                    return;
                }
                progressDisplay = presented;
                if (readerProgress != null && surfaceView != null) {
                    updateProgressControlLabel(
                        readerProgress, readerProgressLabel(surfaceView));
                }
                if (navigationPanel != null) {
                    navigationPanel.updateProgressDisplay(presented);
                }
                persistPresentedProgress(presented);
                updateReaderNavigationAvailability(surfaceView);
                scheduleNavigationSnapshotRefresh();
            }

            @Override
            public void onNavigationRequestFailure(String message) {
                bookmarkNavigationPending = false;
                OctavoReadingPositionStore.Candidate pending =
                    activeBook == null || readingPositionStore == null
                        ? null
                        : readingPositionStore.pendingGo(activeBook.key);
                if (pending != null && readingPositionPrompt != null) {
                    readingPositionCandidate = pending;
                    showReadingPositionRetryableFailure(message);
                    readingPositionRetry = () ->
                        navigateToPendingReadingPosition(pending);
                } else {
                    reportNavigationRequestFailure(message);
                }
            }

            @Override
            public boolean onHighlightRequested(
                long spineIndex,
                long byteStart,
                long byteEnd,
                OctavoAnnotationStore.HighlightColor color,
                String selectedText) {
                if (annotationStore == null || activeBook == null
                    || surfaceView == null) {
                    showOpenFailure("Highlights are unavailable.");
                    return false;
                }
                OctavoAnnotationStore.MutationResult result =
                    annotationStore.addHighlight(
                        activeBook.key,
                        spineIndex,
                        byteStart,
                        byteEnd,
                        color,
                        annotationExcerpt(selectedText));
                if (!result.succeeded()) {
                    showOpenFailure(annotationMutationFailure(result));
                    return false;
                }
                refreshBookmarksPanel();
                boolean accepted = surfaceView.replaceHighlights(
                    annotationStore.highlights(activeBook.key),
                    true,
                    color.label + " highlight added.");
                if (!accepted) {
                    showOpenFailure(
                        "Highlight saved, but its display is unavailable. "
                        + "Reopen the book to retry.");
                }
                return accepted;
            }

            @Override
            public boolean onNoteRequested(long spineIndex,
                                           long byteStart,
                                           long byteEnd,
                                           String selectedText) {
                if (annotationStore == null || noteDraftStore == null
                    || activeBook == null || surfaceView == null) {
                    showOpenFailure("Notes are unavailable.");
                    return false;
                }
                String recordId = annotationStore.newNoteRecordId();
                if (recordId == null) {
                    showOpenFailure(
                        "The bounded annotation store is full; remove an annotation and retry");
                    return false;
                }
                OctavoNoteDraftStore.Draft draft =
                    new OctavoNoteDraftStore.Draft(
                        recordId,
                        "",
                        activeBook.key,
                        spineIndex,
                        byteStart,
                        byteEnd,
                        "",
                        annotationExcerpt(selectedText),
                        "");
                if (!noteDraftStore.save(draft)) {
                    showOpenFailure(
                        "Unable to save a recoverable note draft; the selection was preserved");
                    return false;
                }
                noteSelectionRetained = true;
                openBookmarksPanel();
                if (bookmarksPanel == null) {
                    noteSelectionRetained = false;
                    showOpenFailure("The note editor is unavailable.");
                    return false;
                }
                bookmarksPanel.showNoteEditor(draft, false, false);
                return true;
            }

            @Override
            public boolean onNoteMarkerRequested(
                OctavoAnnotationStore.Note note) {
                return openNoteFromMarker(note);
            }
        };
    }

    private void handlePresentedReadingPosition(
        long spineIndex,
        long byteOffset,
        long pageSpineIndex,
        long pageFirstByte,
        long pageOnePastLastByte,
        long frameCount) {
        if (activeBook == null || readingPositionStore == null
            || spineIndex < 0 || byteOffset < 0
            || pageSpineIndex != spineIndex
            || pageFirstByte < 0
            || pageOnePastLastByte <= pageFirstByte
            || byteOffset < pageFirstByte
            || byteOffset >= pageOnePastLastByte) {
            return;
        }
        hasPresentedReadingPosition = true;
        presentedReadingSpineIndex = spineIndex;
        presentedReadingByteOffset = byteOffset;
        presentedReadingPageSpineIndex = pageSpineIndex;
        presentedReadingPageFirstByte = pageFirstByte;
        presentedReadingPageOnePastLastByte = pageOnePastLastByte;
        presentedReadingFrameCount = frameCount;
        if (readingPositionAwaitingExplicitRetry) {
            return;
        }

        OctavoReadingPositionStore.Candidate pending =
            readingPositionStore.pendingGo(activeBook.key);
        if (pending != null && pageContainsReadingCandidate(pending)) {
            readingPositionCandidate = pending;
            completePresentedPositionMove(pending);
            return;
        }

        OctavoReadingPositionStore.Candidate shown =
            readingPositionCandidate;
        boolean restoreFocusIfNoPrompt = false;
        boolean movedFromPrompt = shown != null
            && (shown.originSpineIndex != spineIndex
                || shown.originByteOffset != byteOffset);
        OctavoReadingPositionStore.MutationResult recorded =
            readingPositionStore.recordSuccessfullyPresented(
                activeBook.key,
                spineIndex,
                byteOffset,
                pageSpineIndex,
                pageFirstByte,
                pageOnePastLastByte,
                true,
                shown);
        if (recorded == OctavoReadingPositionStore.MutationResult.CONFLICT
            && shown != null) {
            restoreFocusIfNoPrompt = readingPositionPrompt != null;
            closeReadingPositionPrompt(false);
            shown = null;
            movedFromPrompt = false;
            recorded = readingPositionStore.recordSuccessfullyPresented(
                activeBook.key,
                spineIndex,
                byteOffset,
                spineIndex,
                pageFirstByte,
                pageOnePastLastByte,
                true);
        }
        if (!recorded.succeeded()) {
            final OctavoReadingPositionStore.Candidate retryShown = shown;
            if (movedFromPrompt && retryShown != null) {
                rememberReadingPositionChoiceRetry(
                    POSITION_RETRY_DISMISS, retryShown);
            }
            showReadingPositionStoreFailure(() ->
                retryPresentedReadingPosition(retryShown));
            return;
        }
        if (movedFromPrompt) {
            clearReadingPositionChoiceRetry(shown);
            closeReadingPositionPrompt(true);
        } else if (shown == null && readingPositionPrompt != null) {
            closeReadingPositionPrompt(true);
        }
        if (!initializeReadingPositionReview()) {
            if (restoreFocusIfNoPrompt
                && readingPositionPrompt == null) {
                restoreReadingPositionFocusAfterClose();
            }
            return;
        }
        considerReadingPositionCandidate();
        if (restoreFocusIfNoPrompt && readingPositionPrompt == null) {
            restoreReadingPositionFocusAfterClose();
        }
    }

    private void retryPresentedReadingPosition(
        OctavoReadingPositionStore.Candidate candidate) {
        if (!hasPresentedReadingPosition || activeBook == null) {
            showReadingPositionStoreFailure(
                () -> retryPresentedReadingPosition(candidate));
            return;
        }
        OctavoReadingPositionStore.MutationResult result =
            readingPositionStore.recordSuccessfullyPresented(
                activeBook.key,
                presentedReadingSpineIndex,
                presentedReadingByteOffset,
                presentedReadingPageSpineIndex,
                presentedReadingPageFirstByte,
                presentedReadingPageOnePastLastByte,
                true,
                candidate);
        if (!result.succeeded()) {
            showReadingPositionStoreFailure(
                () -> retryPresentedReadingPosition(candidate));
            return;
        }
        clearReadingPositionChoiceRetry(candidate);
        closeReadingPositionPrompt(true);
        if (initializeReadingPositionReview()) {
            considerReadingPositionCandidate();
        }
    }

    private boolean initializeReadingPositionReview() {
        if (readingPositionReviewInitialized) {
            return true;
        }
        if (activeBook == null || readingPositionStore == null) {
            return false;
        }
        OctavoReadingPositionStore.MutationResult result =
            readingPositionStore.beginBookReview(
                activeBook.key, readingPositionReviewPending);
        if (!result.succeeded()) {
            showReadingPositionStoreFailure(() -> {
                if (initializeReadingPositionReview()) {
                    considerReadingPositionCandidate();
                }
            });
            return false;
        }
        readingPositionReviewInitialized = true;
        readingPositionReviewPending = false;
        return true;
    }

    private void considerReadingPositionCandidate() {
        if (!activityResumed || !hasPresentedReadingPosition
            || activeBook == null || surfaceView == null
            || readingPositionStore == null
            || readingPositionPrompt != null
            || appearanceSyncPrompt != null
            || appearancePanel != null || navigationPanel != null
            || searchPanel != null || bookmarksPanel != null) {
            return;
        }
        List<OctavoReadingPositionStore.Candidate> candidates =
            readingPositionStore.reviewCandidates(
                activeBook.key,
                presentedReadingPageSpineIndex,
                presentedReadingByteOffset);
        if (readingPositionChoiceRetry != null) {
            OctavoReadingPositionStore.Candidate retained = null;
            for (OctavoReadingPositionStore.Candidate candidate
                    : candidates) {
                if (readingPositionChoiceRetry.matches(candidate)) {
                    retained = candidate;
                    break;
                }
            }
            if (retained != null
                && !pageContainsReadingCandidate(retained)) {
                qualifyAndShowReadingPosition(retained);
                return;
            }
            readingPositionChoiceRetry = null;
        }
        for (OctavoReadingPositionStore.Candidate candidate : candidates) {
            if (pageContainsReadingCandidate(candidate)) {
                continue;
            }
            qualifyAndShowReadingPosition(candidate);
            return;
        }
    }

    private void qualifyAndShowReadingPosition(
        OctavoReadingPositionStore.Candidate candidate) {
        if (!readingPositionCandidateIsCurrent(candidate)
            || surfaceView == null) {
            boolean promptWasVisible = readingPositionPrompt != null;
            closeReadingPositionPrompt(false);
            considerReadingPositionCandidate();
            if (promptWasVisible && readingPositionPrompt == null) {
                restoreReadingPositionFocusAfterClose();
            }
            return;
        }
        long[] qualification = surfaceView.qualifySyncedReadingPosition(
            candidate.spineIndex, candidate.byteOffset);
        boolean exact = qualification != null
            && qualification.length
                == OctavoNative.POSITION_QUALIFICATION_FIELD_COUNT
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_STATUS]
                == OctavoNative.NAVIGATION_ACCEPTED
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_SPINE_INDEX]
                == candidate.spineIndex
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_BYTE_OFFSET]
                == candidate.byteOffset;
        readingPositionCandidate = candidate;
        if (!exact) {
            if (!ensureReadingPositionPrompt(
                    "a saved location in this book")) {
                return;
            }
            showReadingPositionRetryableFailure(
                "Reading position unavailable",
                "The other device's exact Reader0 location could not be "
                    + "verified. Retry is safe.");
            readingPositionRetry = () ->
                qualifyAndShowReadingPosition(candidate);
            return;
        }
        if (!ensureReadingPositionPrompt(
                readingPositionLocationLabel(qualification))) {
            return;
        }
        if (showRetainedReadingPositionChoiceRetry(candidate)) {
            return;
        } else if (candidate.decision
            == OctavoReadingPositionStore.Decision.GO_PENDING) {
            showReadingPositionRetryableFailure(
                "The earlier move was not confirmed on screen. Retry to "
                    + "finish it safely.");
            readingPositionRetry = () ->
                navigateToPendingReadingPosition(candidate);
        } else {
            readingPositionRetry = null;
            readingPositionPrompt.showChoice();
        }
    }

    private boolean readingPositionCandidateIsCurrent(
        OctavoReadingPositionStore.Candidate candidate) {
        if (candidate == null || activeBook == null) {
            return false;
        }
        for (OctavoReadingPositionStore.Candidate current
                : readingPositionStore.reviewCandidates(
                    activeBook.key,
                    presentedReadingSpineIndex,
                    presentedReadingByteOffset)) {
            if (candidate.sameIdentity(current)) {
                return true;
            }
        }
        return false;
    }

    private void rememberReadingPositionChoiceRetry(
        int action,
        OctavoReadingPositionStore.Candidate candidate) {
        if (candidate != null) {
            readingPositionChoiceRetry =
                new ReadingPositionChoiceRetry(action, candidate);
        }
    }

    private void clearReadingPositionChoiceRetry(
        OctavoReadingPositionStore.Candidate candidate) {
        if (readingPositionChoiceRetry != null
            && readingPositionChoiceRetry.matches(candidate)) {
            readingPositionChoiceRetry = null;
        }
    }

    private boolean showRetainedReadingPositionChoiceRetry(
        OctavoReadingPositionStore.Candidate candidate) {
        ReadingPositionChoiceRetry retained = readingPositionChoiceRetry;
        if (retained == null || !retained.matches(candidate)) {
            return false;
        }
        if (candidate.decision
                == OctavoReadingPositionStore.Decision.GO_PENDING
            && retained.action != POSITION_RETRY_DISMISS) {
            readingPositionChoiceRetry = null;
            return false;
        }
        String message;
        if (retained.action == POSITION_RETRY_MARK_GO) {
            message = "Go there was not saved. Retry is safe; the reader "
                + "has not moved.";
            readingPositionRetry = this::goToReadingPositionCandidate;
        } else if (retained.action == POSITION_RETRY_STAY) {
            message = "Stay here was not saved. Retry is safe; the reader "
                + "has not moved.";
            readingPositionRetry = this::stayAtPresentedReadingPosition;
        } else {
            message = "The dismissal was not saved. Retry is safe; the "
                + "reader has not moved.";
            readingPositionRetry = this::dismissReadingPositionForBack;
        }
        showReadingPositionRetryableFailure(
            "Reading position update needs attention", message);
        return true;
    }

    private void goToReadingPositionCandidate() {
        OctavoReadingPositionStore.Candidate candidate =
            readingPositionCandidate;
        if (candidate == null || activeBook == null) {
            showReadingPositionStoreFailure(
                this::considerReadingPositionCandidate);
            return;
        }
        OctavoReadingPositionStore.MutationResult result =
            readingPositionStore.markGoPending(
                candidate,
                candidate.originSequence,
                candidate.originSpineIndex,
                candidate.originByteOffset);
        if (!result.succeeded()) {
            rememberReadingPositionChoiceRetry(
                POSITION_RETRY_MARK_GO, candidate);
            showReadingPositionStoreFailure(
                this::goToReadingPositionCandidate);
            return;
        }
        clearReadingPositionChoiceRetry(candidate);
        OctavoReadingPositionStore.Candidate pending =
            readingPositionStore.pendingGo(activeBook.key);
        if (pending == null || !pending.sameIdentity(candidate)) {
            showReadingPositionStoreFailure(
                this::goToReadingPositionCandidate);
            return;
        }
        readingPositionCandidate = pending;
        navigateToPendingReadingPosition(pending);
    }

    private void navigateToPendingReadingPosition(
        OctavoReadingPositionStore.Candidate candidate) {
        if (!readingPositionCandidateIsCurrent(candidate)
            || surfaceView == null) {
            showReadingPositionStoreFailure(
                () -> navigateToPendingReadingPosition(candidate));
            return;
        }
        long[] qualification = surfaceView.qualifySyncedReadingPosition(
            candidate.spineIndex, candidate.byteOffset);
        boolean exact = qualification != null
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_STATUS]
                == OctavoNative.NAVIGATION_ACCEPTED
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_SPINE_INDEX]
                == candidate.spineIndex
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_BYTE_OFFSET]
                == candidate.byteOffset;
        if (!exact) {
            if (!ensureReadingPositionPrompt(
                    "a saved location in this book")) {
                return;
            }
            showReadingPositionRetryableFailure(
                "The exact saved location is unavailable. Retry is safe.");
            readingPositionRetry = () ->
                navigateToPendingReadingPosition(candidate);
            return;
        }
        int request = surfaceView.requestSyncedReadingPosition(
            candidate.spineIndex, candidate.byteOffset);
        if (request == OctavoNative.NAVIGATION_ALREADY_PRESENTED
            && pageContainsReadingCandidate(candidate)) {
            completePresentedPositionMove(candidate);
        } else if (request == OctavoNative.NAVIGATION_ACCEPTED) {
            if (!ensureReadingPositionPrompt(
                    readingPositionLocationLabel(qualification))) {
                return;
            }
            readingPositionPrompt.showWorking(
                "Opening the other device's position. Waiting for the "
                    + "page to appear.");
            readingPositionRetry = () ->
                navigateToPendingReadingPosition(candidate);
        } else {
            if (!ensureReadingPositionPrompt(
                    readingPositionLocationLabel(qualification))) {
                return;
            }
            showReadingPositionRetryableFailure(
                request == OctavoNative.NAVIGATION_BUSY
                    ? "The reader is finishing another page. Retry when it "
                        + "settles."
                    : "The other device's position could not be opened. "
                        + "Retry is safe.");
            readingPositionRetry = () ->
                navigateToPendingReadingPosition(candidate);
        }
    }

    private void completePresentedPositionMove(
        OctavoReadingPositionStore.Candidate candidate) {
        if (!hasPresentedReadingPosition || activeBook == null
            || !pageContainsReadingCandidate(candidate)) {
            showReadingPositionStoreFailure(
                () -> navigateToPendingReadingPosition(candidate));
            return;
        }
        OctavoReadingPositionStore.MutationResult result =
            readingPositionStore.completeGo(
                candidate,
                candidate.spineIndex,
                candidate.byteOffset,
                presentedReadingSpineIndex,
                presentedReadingByteOffset,
                presentedReadingPageSpineIndex,
                presentedReadingPageFirstByte,
                presentedReadingPageOnePastLastByte,
                true);
        if (!result.succeeded()) {
            if (!ensureReadingPositionPrompt(
                    "the requested saved location")) {
                return;
            }
            showReadingPositionRetryableFailure(
                "The page appeared, but its durable confirmation could not "
                    + "be saved. Retry is safe.");
            readingPositionRetry = () ->
                completePresentedPositionMove(candidate);
            return;
        }
        clearReadingPositionChoiceRetry(candidate);
        closeReadingPositionPrompt(true);
    }

    private void stayAtPresentedReadingPosition() {
        OctavoReadingPositionStore.Candidate candidate =
            readingPositionCandidate;
        OctavoReadingPositionStore.MutationResult result =
            readingPositionStore.stay(candidate);
        if (!result.succeeded()) {
            rememberReadingPositionChoiceRetry(
                POSITION_RETRY_STAY, candidate);
            showReadingPositionStoreFailure(
                this::stayAtPresentedReadingPosition);
            return;
        }
        clearReadingPositionChoiceRetry(candidate);
        closeReadingPositionPrompt(true);
    }

    private void dismissReadingPositionForBack() {
        OctavoReadingPositionStore.Candidate candidate =
            readingPositionCandidate;
        if (candidate == null) {
            readingPositionChoiceRetry = null;
            closeReadingPositionPrompt(true);
            return;
        }
        if (surfaceView != null && surfaceView.hasNavigationPending()) {
            readingPositionPrompt.showWorking(
                "Waiting for the requested page to finish.");
            return;
        }
        OctavoReadingPositionStore.MutationResult result;
        boolean presentedAtOrigin = hasPresentedReadingPosition
            && presentedReadingSpineIndex == candidate.originSpineIndex
            && presentedReadingByteOffset == candidate.originByteOffset;
        if (candidate.decision
            == OctavoReadingPositionStore.Decision.GO_PENDING) {
            if (!presentedAtOrigin) {
                showReadingPositionRetryableFailure(
                    "The requested move is still pending. Retry its durable "
                        + "confirmation before dismissing it.");
                readingPositionRetry = pageContainsReadingCandidate(candidate)
                    ? () -> completePresentedPositionMove(candidate)
                    : () -> navigateToPendingReadingPosition(candidate);
                return;
            }
            readingPositionAwaitingExplicitRetry = false;
            result = readingPositionStore.dismissPendingAfterRollback(
                candidate,
                presentedReadingSpineIndex,
                presentedReadingByteOffset,
                presentedReadingPageSpineIndex,
                presentedReadingPageFirstByte,
                presentedReadingPageOnePastLastByte,
                true);
        } else if (!presentedAtOrigin) {
            readingPositionAwaitingExplicitRetry = false;
            if (activeBook == null) {
                rememberReadingPositionChoiceRetry(
                    POSITION_RETRY_DISMISS, candidate);
                showReadingPositionStoreFailure(
                    this::dismissReadingPositionForBack);
                return;
            }
            result = readingPositionStore.recordSuccessfullyPresented(
                activeBook.key,
                presentedReadingSpineIndex,
                presentedReadingByteOffset,
                presentedReadingPageSpineIndex,
                presentedReadingPageFirstByte,
                presentedReadingPageOnePastLastByte,
                true,
                candidate);
        } else {
            readingPositionAwaitingExplicitRetry = false;
            result = readingPositionStore.dismiss(candidate);
        }
        if (!result.succeeded()) {
            rememberReadingPositionChoiceRetry(
                POSITION_RETRY_DISMISS, candidate);
            showReadingPositionStoreFailure(
                this::dismissReadingPositionForBack);
            return;
        }
        clearReadingPositionChoiceRetry(candidate);
        closeReadingPositionPrompt(true);
    }

    private void restorePendingReadingPositionAfterLifecycle() {
        if (activeBook == null || readingPositionStore == null
            || readingPositionPrompt == null) {
            return;
        }
        OctavoReadingPositionStore.Candidate pending =
            readingPositionStore.pendingGo(activeBook.key);
        if (pending == null) {
            if (readingPositionCandidate == null
                && readingPositionRetry != null) {
                showReadingPositionRetryableFailure(
                    "The reading-position operation was paused. Retry is "
                        + "safe.");
            }
            return;
        }
        readingPositionCandidate = pending;
        showReadingPositionRetryableFailure(
            "The move was paused before its page was confirmed. Retry is "
                + "safe.");
        readingPositionRetry = () ->
            navigateToPendingReadingPosition(pending);
    }

    private boolean pageContainsReadingCandidate(
        OctavoReadingPositionStore.Candidate candidate) {
        return candidate != null
            && hasPresentedReadingPosition
            && candidate.spineIndex == presentedReadingSpineIndex
            && presentedReadingPageFirstByte <= candidate.byteOffset
            && presentedReadingPageOnePastLastByte > candidate.byteOffset;
    }

    private String readingPositionLocationLabel(long[] qualification) {
        if (qualification != null
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_LOCATION_AVAILABLE] == 1
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_LOCATION_INDEX] > 0
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_LOCATION_COUNT]
                >= qualification[
                    OctavoNative.POSITION_QUALIFICATION_LOCATION_INDEX]) {
            return String.format(
                Locale.ROOT,
                "Location %d of %d (%d%%)",
                qualification[
                    OctavoNative.POSITION_QUALIFICATION_LOCATION_INDEX],
                qualification[
                    OctavoNative.POSITION_QUALIFICATION_LOCATION_COUNT],
                qualification[
                    OctavoNative.POSITION_QUALIFICATION_PERCENT]);
        }
        return "a saved location in this book";
    }

    private void showReadingPositionRestoreFailure() {
        OctavoReadingPositionPortable.Lane local =
            activeBook == null || readingPositionStore == null
                ? null : readingPositionStore.localLane(activeBook.key);
        if (local == null) {
            showOpenFailure(
                "The synchronized reading position could not be restored");
            return;
        }
        if (!ensureReadingPositionPrompt(
                "the saved position for this device")) {
            return;
        }
        showReadingPositionRetryableFailure(
            "Reading position unavailable",
            "The exact saved Reader0 location was not presented. The book "
                + "remains unchanged; Retry is safe.");
        readingPositionRetry = () -> retrySynchronizedLocalResume(local);
    }

    private void retrySynchronizedLocalResume(
        OctavoReadingPositionPortable.Lane local) {
        if (surfaceView == null) {
            showReadingPositionRestoreFailure();
            return;
        }
        long[] qualification = surfaceView.qualifySyncedReadingPosition(
            local.spineIndex, local.byteOffset);
        boolean exact = qualification != null
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_STATUS]
                == OctavoNative.NAVIGATION_ACCEPTED
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_SPINE_INDEX]
                == local.spineIndex
            && qualification[
                OctavoNative.POSITION_QUALIFICATION_BYTE_OFFSET]
                == local.byteOffset;
        if (!exact) {
            showReadingPositionRestoreFailure();
            return;
        }
        int request = surfaceView.requestSyncedReadingPosition(
            local.spineIndex, local.byteOffset);
        if (request == OctavoNative.NAVIGATION_ACCEPTED) {
            readingPositionPrompt.showWorking(
                "Retrying the exact saved position. Waiting for the page "
                    + "to appear.");
            readingPositionRetry = () ->
                retrySynchronizedLocalResume(local);
        } else {
            showReadingPositionRestoreFailure();
        }
    }

    private void showReadingPositionRetryableFailure(String message) {
        readingPositionAwaitingExplicitRetry = true;
        readingPositionPrompt.showRetryableFailure(message);
    }

    private void showReadingPositionRetryableFailure(
        String heading,
        String message) {
        readingPositionAwaitingExplicitRetry = true;
        readingPositionPrompt.showRetryableFailure(heading, message);
    }

    private void showReadingPositionStoreFailure(Runnable retry) {
        if (!ensureReadingPositionPrompt(
                "this reading position update")) {
            return;
        }
        String message = readingPositionStore == null
            ? "Reading-position state is unavailable."
            : readingPositionStore.lastError();
        showReadingPositionRetryableFailure(
            "Reading position update needs attention",
            TextUtils.isEmpty(message)
                ? "The reading position could not be saved. Retry is safe."
                : message);
        readingPositionRetry = retry;
    }

    private boolean ensureReadingPositionPrompt(String locationLabel) {
        if (readerRoot == null || appearanceSyncPrompt != null) {
            showOpenFailure("Reading-position confirmation is unavailable");
            return false;
        }
        if (readingPositionPrompt != null) {
            readingPositionPrompt.setLocationLabel(locationLabel);
            return true;
        }
        if (appearancePanel != null) {
            closeAppearancePanel(false);
        }
        if (navigationPanel != null) {
            closeNavigationPanel(false);
        }
        if (searchPanel != null) {
            closeSearchPanel(false);
        }
        if (bookmarksPanel != null) {
            closeBookmarksPanel(false);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(8));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        OctavoReadingPositionPrompt prompt;
        try {
            prompt = new OctavoReadingPositionPrompt(
                    this,
                    appearance,
                    locationLabel,
                    new OctavoReadingPositionPrompt.Listener() {
                    @Override
                    public void onGoThere() {
                        goToReadingPositionCandidate();
                    }

                    @Override
                    public void onStayHere() {
                        stayAtPresentedReadingPosition();
                    }

                    @Override
                    public void onRetry() {
                        Runnable retry = readingPositionRetry;
                        readingPositionRetry = null;
                        readingPositionAwaitingExplicitRetry = false;
                        if (retry != null) {
                            retry.run();
                        } else {
                            showReadingPositionStoreFailure(
                                OctavoActivity.this::
                                    considerReadingPositionCandidate);
                        }
                    }
                    });
        } catch (IllegalStateException failure) {
            showOpenFailure(
                "Reading-position styling is unavailable; reopen to retry");
            return false;
        }
        overlay.setBackgroundColor(prompt.overlayColor());
        FrameLayout.LayoutParams promptLayout =
            new FrameLayout.LayoutParams(
                readingPositionPromptWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        promptLayout.leftMargin = dp(20);
        promptLayout.topMargin = dp(20);
        promptLayout.rightMargin = dp(20);
        promptLayout.bottomMargin = dp(20);
        overlay.addView(prompt, promptLayout);
        readerRoot.addView(overlay, matchParentLayout());
        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerTopChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerBottomChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readingPositionOverlay = overlay;
        readingPositionPrompt = prompt;
        obscureFailureBannerForModalPrompt();
        int duration = sideSheetMotionDuration(tokens);
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            prompt.setTranslationY(dp(16));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            prompt.animate().translationY(0.0f)
                .setDuration(duration).start();
        }
        prompt.post(() -> {
            if (readingPositionPrompt != prompt) {
                return;
            }
            View focus = prompt.preferredInitialFocus();
            focus.requestFocus();
            focus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            prompt.announceForAccessibility(
                "Reading position confirmation opened");
        });
        return true;
    }

    private int readingPositionPromptWidth() {
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = readerRoot == null || readerRoot.getWidth() <= 0
            ? displayWidth : readerRoot.getWidth();
        return Math.min(
            appearancePanelWidth(),
            Math.max(availableWidth - dp(40), 1));
    }

    private void updateReadingPositionPromptBounds() {
        if (readingPositionPrompt == null
            || !(readingPositionPrompt.getLayoutParams()
                instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams layout =
            (FrameLayout.LayoutParams)
                readingPositionPrompt.getLayoutParams();
        layout.width = readingPositionPromptWidth();
        readingPositionPrompt.setLayoutParams(layout);
    }

    private void closeReadingPositionPrompt(boolean restoreFocus) {
        boolean ownedPrompt = readingPositionOverlay != null
            || readingPositionPrompt != null;
        if (readingPositionOverlay != null) {
            readingPositionOverlay.animate().cancel();
            if (readingPositionOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup)readingPositionOverlay.getParent())
                    .removeView(readingPositionOverlay);
            }
        }
        if (readingPositionPrompt != null) {
            readingPositionPrompt.animate().cancel();
        }
        readingPositionOverlay = null;
        readingPositionPrompt = null;
        readingPositionCandidate = null;
        readingPositionRetry = null;
        readingPositionAwaitingExplicitRetry = false;
        if (!ownedPrompt) {
            return;
        }
        restoreReaderAccessibilityBoundary();
        if (appearanceSyncPrompt == null) {
            restoreFailureBannerAfterModalPrompt();
        }
        if (restoreFocus) {
            considerAppearanceSyncCandidate();
        }
        if (restoreFocus) {
            restoreReadingPositionFocusAfterClose();
        }
    }

    private void restoreReadingPositionFocusAfterClose() {
        if (readingPositionPrompt != null
            || appearanceSyncPrompt != null
            || surfaceView == null || !surfaceView.isShown()) {
            return;
        }
        OctavoSurfaceView focusReturn = surfaceView;
        focusReturn.requestFocus();
        focusReturn.post(() -> {
            if (readingPositionPrompt != null
                || appearanceSyncPrompt != null
                || surfaceView != focusReturn || !focusReturn.isShown()) {
                return;
            }
            focusReturn.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            focusReturn.announceForAccessibility(
                "Reading position confirmation closed");
        });
    }

    private void obscureFailureBannerForModalPrompt() {
        if (failureBanner == null) {
            return;
        }
        failureBanner.setVisibility(View.INVISIBLE);
        failureBanner.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void restoreFailureBannerAfterModalPrompt() {
        TextView banner = failureBanner;
        if (banner == null || !(banner.getParent() instanceof ViewGroup)) {
            failureBannerAnnouncementDeferred = false;
            return;
        }
        banner.setVisibility(View.VISIBLE);
        banner.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (!failureBannerAnnouncementDeferred) {
            return;
        }
        banner.post(() -> {
            if (readingPositionPrompt == null
                && appearanceSyncPrompt == null
                && failureBanner == banner
                && banner.isShown()
                && failureBannerAnnouncementDeferred) {
                failureBannerAnnouncementDeferred = false;
                banner.announceForAccessibility(
                    banner.getText().toString());
            }
        });
    }

    private void reportReadingPositionLoadStatus(
        OctavoReadingPositionStore.LoadStatus status) {
        if (status == OctavoReadingPositionStore.LoadStatus
                .CORRUPT_QUARANTINED) {
            showOpenFailure(
                "Invalid reading-position state was quarantined; local "
                    + "positions start fresh");
        } else if (status == OctavoReadingPositionStore.LoadStatus
                       .CORRUPT_BLOCKED
                   || status == OctavoReadingPositionStore.LoadStatus
                       .FUTURE_VERSION_BLOCKED) {
            showOpenFailure(readingPositionStore.lastError());
        }
    }

    private void reportAppearanceSyncLoadStatus(
        OctavoAppearanceSyncStore.LoadStatus status) {
        if (status == null
            || status == OctavoAppearanceSyncStore.LoadStatus.LOADED
            || status
               == OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED) {
            return;
        }
        String message = appearanceSyncStore == null
            ? "Reading-settings synchronization is unavailable."
            : appearanceSyncStore.lastError();
        if (TextUtils.isEmpty(message)) {
            message = status
                == OctavoAppearanceSyncStore.LoadStatus
                    .CORRUPT_QUARANTINED
                ? "Invalid reading-settings sync state was quarantined."
                : "Reading-settings synchronization needs attention.";
        }
        final String visibleMessage = message;
        if (readerRoot != null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                visibleMessage,
                this::reloadAppearanceSyncState);
        } else {
            showOpenFailure(visibleMessage);
        }
    }

    /**
     * Consumes only settled Surface evidence. The receipt key is recorded
     * before touching either private store so a duplicate frame can never
     * turn a surfaced failure into an implicit Retry.
     */
    private void processAppearancePresentationReceipt() {
        OctavoSurfaceView receiptOwner = surfaceView;
        if (receiptOwner == null || appearanceSyncStore == null) {
            return;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            receiptOwner.currentAppearancePresentationReceipt();
        if (receipt == null || receipt.profile == null
            || !receipt.strictResumeSettled) {
            return;
        }
        latestAppearanceReceipt = receipt;
        appearanceReceiptSurface = receiptOwner;
        if (consumedAppearanceReceiptSurface == receiptOwner
            && consumedAppearanceReceiptGeneration
               == receipt.appearanceGeneration
            && consumedAppearanceReceiptFrame == receipt.frameCount
            && receipt.profile.equals(consumedAppearanceReceiptProfile)) {
            return;
        }
        consumedAppearanceReceiptSurface = receiptOwner;
        consumedAppearanceReceiptGeneration =
            receipt.appearanceGeneration;
        consumedAppearanceReceiptFrame = receipt.frameCount;
        consumedAppearanceReceiptProfile = receipt.profile;

        if (appearanceSyncAwaitingExplicitRetry) {
            presentRetainedAppearanceSyncFailure();
            return;
        }
        if (appearanceSyncUnstagedRollbackRequested) {
            if (appearanceSyncUnstagedOrigin != null
                && receipt.profile.equals(
                    appearanceSyncUnstagedOrigin)) {
                if (appearanceSyncAbandonAfterReload) {
                    finishUncertainStageAbandon(receipt);
                    return;
                }
                appearanceSyncUnstagedRollbackRequested = false;
                appearanceSyncUnstagedOrigin = null;
                if (appearanceStore.loadStatus()
                        == OctavoAppearanceStore.LoadStatus.CURRENT
                    && appearanceStore.hasCanonicalCurrentRecord(
                        receipt.profile)) {
                    finishAppearanceConvergence(receipt);
                } else {
                    saveCanonicalAppearanceWithoutLaneAdvance(receipt);
                }
            }
            return;
        }
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        appearanceSyncPending = pending;
        if (pending != null) {
            if (appearanceSyncPendingLoaded) {
                showLoadedPendingAppearanceRetry(pending, receipt);
                return;
            }
            if (appearanceSyncRollbackRequested) {
                if (receipt.profile.equals(pending.originAppearance())) {
                    finishPendingAppearanceRollback(pending, receipt);
                }
                return;
            }
            if (receipt.profile.equals(pending.targetAppearance())) {
                completePendingAppearanceForward(pending, receipt, false);
            }
            return;
        }

        appearanceSyncPendingLoaded = false;
        OctavoAppearancePortable.Lane local =
            appearanceSyncStore.localLane();
        if (local == null
            || !receipt.profile.equals(local.profile.toAppearance())) {
            closeStaleAppearanceSyncChoice(false);
            stagePresentedLocalAppearance(receipt);
            return;
        }

        if (appearanceStore.loadStatus()
                != OctavoAppearanceStore.LoadStatus.CURRENT
            || !appearanceStore.hasCanonicalCurrentRecord(
                receipt.profile)) {
            saveCanonicalAppearanceWithoutLaneAdvance(receipt);
            return;
        }
        finishAppearanceConvergence(receipt);
    }

    private void stagePresentedLocalAppearance(
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        if (!receiptIsCurrent(receipt)) {
            return;
        }
        OctavoAppearancePortable.Lane priorLocal =
            appearanceSyncStore.localLane();
        OctavoAppearanceSyncStore.MutationResult staged =
            appearanceSyncStore.stageLocalPresented(receipt.profile);
        if (staged == OctavoAppearanceSyncStore.MutationResult.UNCHANGED) {
            finishAppearanceConvergence(receipt);
            return;
        }
        if (staged != OctavoAppearanceSyncStore.MutationResult.UPDATED) {
            appearanceSyncUnstagedOrigin = priorLocal == null
                ? null : priorLocal.profile.toAppearance();
            appearanceSyncStageUncertain = staged
                == OctavoAppearanceSyncStore.MutationResult
                    .PUBLISH_UNCERTAIN;
            showAppearanceMutationFailure(
                staged,
                "The presented reading settings could not be staged.",
                () -> retryStagePresentedLocalAppearance(
                    receipt.profile));
            return;
        }
        appearanceSyncStageUncertain = false;
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        if (pending == null
            || !receipt.profile.equals(pending.targetAppearance())) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The staged reading-settings update could not be verified.",
                this::reloadAppearanceSyncState);
            return;
        }
        appearanceSyncPending = pending;
        completePendingAppearanceForward(pending, receipt, false);
    }

    private void saveCanonicalAppearanceWithoutLaneAdvance(
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        if (!receiptIsCurrent(receipt)) {
            return;
        }
        OctavoAppearanceSyncStore.O7stProof proof =
            saveExactAppearanceForSync(receipt.profile);
        if (proof == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The presented reading settings could not be saved. "
                    + "Retry is safe.",
                () -> retrySaveCanonicalAppearanceWithoutLaneAdvance(
                    receipt.profile));
            return;
        }
        finishAppearanceConvergence(receipt);
    }

    private void completePendingAppearanceForward(
        OctavoAppearanceSyncStore.Pending expected,
        OctavoSurfaceView.AppearancePresentationReceipt receipt,
        boolean allowCanonicalLoadProof) {
        if (!receiptIsCurrent(receipt)
            || expected == null
            || !expected.sameIdentity(appearanceSyncStore.pending())
            || !receipt.profile.equals(expected.targetAppearance())) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The exact pending reading settings are not currently "
                    + "confirmed on screen.",
                this::retryPendingAppearanceForward);
            return;
        }

        OctavoAppearanceSyncStore.O7stProof proof = null;
        if (allowCanonicalLoadProof
            && appearanceStore.loadStatus()
               == OctavoAppearanceStore.LoadStatus.CURRENT
            && appearanceStore.hasCanonicalCurrentRecord(
                receipt.profile)) {
            proof = OctavoAppearanceSyncStore.O7stProof.CANONICAL_V3_LOAD;
        }
        if (proof == null) {
            proof = saveExactAppearanceForSync(receipt.profile);
        }
        if (proof == null) {
            appearanceSyncPending = expected;
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The settings appeared, but their local record could not "
                    + "be saved. Retry is safe.",
                this::retryPendingAppearanceForward);
            return;
        }
        OctavoAppearanceSyncStore.MutationResult completed =
            appearanceSyncStore.completePending(
                expected, receipt.profile, receipt.profile, proof);
        if (completed == OctavoAppearanceSyncStore.MutationResult.CONFLICT
            && expected.hasOriginLane) {
            beginPendingAppearanceRollback(
                expected,
                "The other device changed its settings while they were "
                    + "being applied. Restoring your settings.");
            return;
        }
        if (!completed.succeeded()) {
            showAppearanceMutationFailure(
                completed,
                "The durable reading-settings confirmation could not be "
                    + "finished.",
                this::retryPendingAppearanceForward);
            return;
        }
        appearanceSyncPending = null;
        appearanceSyncPendingLoaded = false;
        appearanceSyncRollbackRequested = false;
        appearanceSyncStageUncertain = false;
        appearanceSyncAbandonAfterReload = false;
        appearanceSyncRetryDescriptor = null;
        finishAppearanceConvergence(receipt);
    }

    private OctavoAppearanceSyncStore.O7stProof saveExactAppearanceForSync(
        OctavoAppearance target) {
        if (appearanceStore.save(target)) {
            pendingAppearancePersistence = null;
            return OctavoAppearanceSyncStore.O7stProof
                .CURRENT_PROCESS_ATOMIC_SAVE;
        }
        if (appearanceStore.hasCanonicalCurrentRecord(target)) {
            pendingAppearancePersistence = null;
            return OctavoAppearanceSyncStore.O7stProof
                .CURRENT_PROCESS_RECONCILED_AFTER_UNCERTAIN_SAVE;
        }
        return null;
    }

    private void finishAppearanceConvergence(
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        if (!receiptIsCurrent(receipt)) {
            return;
        }
        OctavoAppearanceSyncStore.MutationResult converged =
            appearanceSyncStore.recordConverged(receipt.profile);
        if (!converged.succeeded()) {
            showAppearanceMutationFailure(
                converged,
                "Equal reading-settings candidates could not be recorded.",
                () -> retryFinishAppearanceConvergence(
                    receipt.profile));
            return;
        }
        appearanceSyncAwaitingExplicitRetry = false;
        appearanceSyncRetry = null;
        if (appearanceSyncRetryDescriptor != null
            && !appearanceSyncRetryDescriptor.candidateAction()) {
            appearanceSyncRetryDescriptor = null;
        }
        boolean retainChoice = appearanceSyncCandidate != null
            && appearanceSyncCandidateIsCurrent(
                appearanceSyncCandidate, receipt.profile);
        if (retainChoice) {
            ensureAppearanceSyncChoicePrompt(
                receipt.profile,
                appearanceSyncCandidate.targetAppearance(),
                appearanceSyncCandidate);
        } else {
            closeAppearanceSyncPrompt(true);
        }
        if (initializeAppearanceSyncReview()) {
            if (!retainChoice) {
                considerAppearanceSyncCandidate();
            }
        }
    }

    private boolean initializeAppearanceSyncReview() {
        if (appearanceSyncReviewInitialized) {
            return true;
        }
        if (appearanceSyncStore == null) {
            return false;
        }
        boolean advanceExplicitOpen = appearanceSyncReviewPending;
        long reviewEpochBefore = appearanceSyncStore.reviewEpoch();
        OctavoAppearanceSyncStore.MutationResult result =
            appearanceSyncStore.beginReviewEpoch(
                advanceExplicitOpen);
        if (!result.succeeded()) {
            if (advanceExplicitOpen
                && (result == OctavoAppearanceSyncStore.MutationResult
                        .PUBLISH_UNCERTAIN
                    || result == OctavoAppearanceSyncStore.MutationResult
                        .BLOCKED)) {
                appearanceSyncReviewEpochBeforeRetry = reviewEpochBefore;
                appearanceSyncRollbackEpochReconciliation = false;
                showAppearanceSyncFailure(
                    "Reading settings update needs attention",
                    appearanceSyncStore.lastError(),
                    this::reconcileAppearanceReviewEpoch);
                return false;
            }
            showAppearanceMutationFailure(
                result,
                "Reading-settings review could not be initialized.",
                () -> {
                    if (initializeAppearanceSyncReview()) {
                        closeAppearanceSyncPrompt(true);
                        considerAppearanceSyncCandidate();
                    }
                });
            return false;
        }
        appearanceSyncReviewInitialized = true;
        appearanceSyncReviewPending = false;
        appearanceSyncReviewEpochBeforeRetry = -1;
        appearanceSyncRollbackEpochReconciliation = false;
        return true;
    }

    private void retryPendingAppearanceForward() {
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore == null ? null
                : appearanceSyncStore.pending();
        if (pending == null) {
            reloadAppearanceSyncState();
            return;
        }
        appearanceSyncPending = pending;
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.pending(
                APPEARANCE_RETRY_FORWARD, pending);
        boolean allowCanonicalLoadProof = appearanceSyncPendingLoaded;
        appearanceSyncPendingLoaded = false;
        appearanceSyncRollbackRequested = false;
        appearanceSyncAwaitingExplicitRetry = false;
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt != null
            && receipt.profile.equals(pending.targetAppearance())) {
            completePendingAppearanceForward(
                pending, receipt, allowCanonicalLoadProof);
            return;
        }
        if (surfaceView == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The reader is unavailable. Reopen the book and Retry.",
                this::retryPendingAppearanceForward);
            return;
        }
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.showWorking(
                "Applying the pending reading settings. Waiting for the "
                    + "reader to confirm them on screen.");
        }
        requestReaderAppearance(pending.targetAppearance());
    }

    private void retryUnstagedAppearanceRollback() {
        OctavoAppearance origin = appearanceSyncUnstagedOrigin;
        if (origin == null && appearanceSyncStore != null) {
            OctavoAppearancePortable.Lane local =
                appearanceSyncStore.localLane();
            origin = local == null ? null : local.profile.toAppearance();
        }
        if (origin == null || surfaceView == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "No earlier synchronized settings can be restored. Retry "
                    + "the original settings update.",
                this::reloadAppearanceSyncState);
            return;
        }
        appearanceSyncUnstagedOrigin = origin;
        appearanceSyncUnstagedRollbackRequested = true;
        appearanceSyncAwaitingExplicitRetry = false;
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.showWorking(
                "Restoring your durable reading settings.");
        }
        requestReaderAppearance(origin);
    }

    private void showLoadedPendingAppearanceRetry(
        OctavoAppearanceSyncStore.Pending pending,
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        String message;
        if (receipt != null
            && receipt.profile.equals(pending.targetAppearance())
            && appearanceStore.loadStatus()
               == OctavoAppearanceStore.LoadStatus.CURRENT
            && appearanceStore.hasCanonicalCurrentRecord(
                receipt.profile)) {
            message = "The settings and their local record are present, "
                + "but synchronization confirmation still needs to finish.";
        } else if (receipt != null
                   && receipt.profile.equals(pending.originAppearance())) {
            message = "A settings update was interrupted before it was "
                + "shown. Retry is safe; your current settings remain.";
        } else {
            message = "A settings update was interrupted and its exact "
                + "durable state needs to be reconciled. Retry is safe.";
        }
        showAppearanceSyncFailure(
            "Reading settings update needs attention",
            message,
            appearanceSyncRollbackRequested
                ? () -> beginPendingAppearanceRollback(
                    pending, "Restoring your reading settings.")
                : this::retryPendingAppearanceForward);
    }

    private void beginPendingAppearanceRollback(
        OctavoAppearanceSyncStore.Pending pending,
        String status) {
        if (pending == null || !pending.hasOriginLane) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "These first reading settings have no earlier synchronized "
                    + "profile to restore. Retry the update.",
                this::retryPendingAppearanceForward);
            return;
        }
        appearanceSyncPending = pending;
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.pending(
                APPEARANCE_RETRY_ROLLBACK, pending);
        appearanceSyncRollbackRequested = true;
        appearanceSyncPendingLoaded = false;
        appearanceSyncAwaitingExplicitRetry = false;
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt != null
            && receipt.profile.equals(pending.originAppearance())) {
            finishPendingAppearanceRollback(pending, receipt);
            return;
        }
        if (surfaceView == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The reader is unavailable. Reopen the book and Retry.",
                () -> beginPendingAppearanceRollback(pending, status));
            return;
        }
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.showWorking(status);
        }
        requestReaderAppearance(pending.originAppearance());
    }

    private void finishPendingAppearanceRollback(
        OctavoAppearanceSyncStore.Pending expected,
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        if (!receiptIsCurrent(receipt)
            || expected == null
            || !expected.sameIdentity(appearanceSyncStore.pending())
            || !receipt.profile.equals(expected.originAppearance())) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The earlier settings have not been confirmed on screen.",
                () -> beginPendingAppearanceRollback(
                    expected, "Restoring your reading settings."));
            return;
        }
        OctavoAppearanceSyncStore.O7stProof proof =
            saveExactAppearanceForSync(receipt.profile);
        if (proof == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "Your settings were restored on screen, but their local "
                    + "record could not be saved. Retry is safe.",
                () -> retryFinishPendingAppearanceRollback(expected));
            return;
        }
        boolean advanceDeferredReviewEpoch =
            appearanceSyncReviewPending;
        long rollbackReviewEpochBefore =
            appearanceSyncStore.reviewEpoch();
        OctavoAppearanceSyncStore.MutationResult dismissed =
            appearanceSyncStore.dismissPendingAfterRollback(
                expected, receipt.profile, receipt.profile, proof,
                advanceDeferredReviewEpoch);
        if (!dismissed.succeeded()) {
            if (dismissed == OctavoAppearanceSyncStore.MutationResult
                    .PUBLISH_UNCERTAIN
                || dismissed == OctavoAppearanceSyncStore.MutationResult
                    .BLOCKED) {
                appearanceSyncRollbackRequested = true;
                appearanceSyncReviewEpochBeforeRetry =
                    advanceDeferredReviewEpoch
                        ? rollbackReviewEpochBefore : -1;
                appearanceSyncRollbackEpochReconciliation =
                    advanceDeferredReviewEpoch;
                showAppearanceSyncFailure(
                    "Reading settings update needs attention",
                    appearanceSyncStore.lastError(),
                    () -> reloadAppearanceSyncStateForRollback(
                        expected, rollbackReviewEpochBefore,
                        advanceDeferredReviewEpoch));
            } else {
                showAppearanceMutationFailure(
                    dismissed,
                    "The restored settings could not be durably confirmed.",
                    () -> retryFinishPendingAppearanceRollback(expected));
            }
            return;
        }
        appearanceSyncPending = null;
        appearanceSyncRollbackRequested = false;
        appearanceSyncPendingLoaded = false;
        appearanceSyncAwaitingExplicitRetry = false;
        appearanceSyncStageUncertain = false;
        appearanceSyncAbandonAfterReload = false;
        appearanceSyncRetryDescriptor = null;
        if (advanceDeferredReviewEpoch) {
            appearanceSyncReviewPending = false;
            appearanceSyncReviewInitialized = true;
        }
        appearanceSyncReviewEpochBeforeRetry = -1;
        appearanceSyncRollbackEpochReconciliation = false;
        closeAppearanceSyncPrompt(true);
        finishAppearanceConvergence(receipt);
    }

    private void reloadAppearanceSyncState() {
        if (appearanceSyncStore == null) {
            return;
        }
        OctavoAppearanceSyncStore.LoadStatus status =
            appearanceSyncStore.load();
        appearanceSyncPending = appearanceSyncStore.pending();
        appearanceSyncPendingLoaded = appearanceSyncPending != null;
        appearanceSyncAwaitingExplicitRetry = false;
        appearanceSyncRollbackRequested = false;
        if (status != OctavoAppearanceSyncStore.LoadStatus.LOADED
            && status
               != OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED) {
            reportAppearanceSyncLoadStatus(status);
            return;
        }
        if (!appearanceSyncAbandonAfterReload) {
            // Reload proves which side of the uncertain staging replace won.
            // A later, unrelated Back must not inherit that resolved state.
            appearanceSyncStageUncertain = false;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (appearanceSyncPending != null && receipt != null) {
            showLoadedPendingAppearanceRetry(
                appearanceSyncPending, receipt);
        } else if (receipt != null) {
            stageOrConvergeAfterExplicitRetry(receipt);
        }
    }

    private void reloadAppearanceSyncForAbandon() {
        if (appearanceSyncStore == null) {
            return;
        }
        OctavoAppearanceSyncStore.LoadStatus status =
            appearanceSyncStore.load();
        if (status != OctavoAppearanceSyncStore.LoadStatus.LOADED
            && status
               != OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                appearanceSyncStore.lastError(),
                this::reloadAppearanceSyncForAbandon);
            return;
        }
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        appearanceSyncPending = pending;
        appearanceSyncPendingLoaded = pending != null;
        appearanceSyncStageUncertain = false;
        if (pending != null) {
            if (!pending.hasOriginLane) {
                showAppearanceSyncFailure(
                    "Reading settings update needs attention",
                    "The uncertain first settings publication has no "
                        + "earlier synchronized profile to restore.",
                    this::retryPendingAppearanceForward);
                return;
            }
            appearanceSyncAbandonAfterReload = false;
            appearanceSyncPendingLoaded = false;
            beginPendingAppearanceRollback(
                pending, "Restoring your reading settings.");
            return;
        }
        OctavoAppearancePortable.Lane local =
            appearanceSyncStore.localLane();
        OctavoAppearance origin = local == null
            ? appearanceStore.current()
            : local.profile.toAppearance();
        if (origin == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The earlier durable reading settings could not be "
                    + "identified.",
                this::reloadAppearanceSyncForAbandon);
            return;
        }
        appearanceSyncUnstagedOrigin = origin;
        appearanceSyncUnstagedRollbackRequested = true;
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt != null && receipt.profile.equals(origin)) {
            finishUncertainStageAbandon(receipt);
            return;
        }
        if (surfaceView == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "Reopen the reader to restore the earlier settings.",
                this::reloadAppearanceSyncForAbandon);
            return;
        }
        appearanceSyncAwaitingExplicitRetry = false;
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.showWorking(
                "Restoring your durable reading settings.");
        }
        requestReaderAppearance(origin);
    }

    private void finishUncertainStageAbandon(
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        if (!receiptIsCurrent(receipt)) {
            return;
        }
        OctavoAppearanceSyncStore.O7stProof proof =
            saveExactAppearanceForSync(receipt.profile);
        if (proof == null) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The earlier settings appeared, but their local record "
                    + "could not be restored.",
                this::reloadAppearanceSyncForAbandon);
            return;
        }
        AppearanceSyncRetryDescriptor descriptor =
            appearanceSyncRetryDescriptor;
        if (descriptor != null && descriptor.candidateAction()) {
            OctavoAppearanceSyncStore.Candidate exact = null;
            for (OctavoAppearanceSyncStore.Candidate candidate
                    : appearanceSyncStore.reviewCandidates(
                        receipt.profile)) {
                if (descriptor.matches(candidate)) {
                    exact = candidate;
                    break;
                }
            }
            if (exact != null) {
                OctavoAppearanceSyncStore.MutationResult dismissed =
                    appearanceSyncStore.dismiss(exact, receipt.profile);
                if (!dismissed.succeeded()) {
                    showAppearanceMutationFailure(
                        dismissed,
                        "Later could not be saved after recovery.",
                        this::reloadAppearanceSyncForAbandon);
                    return;
                }
            }
        }
        appearanceSyncAbandonAfterReload = false;
        appearanceSyncStageUncertain = false;
        appearanceSyncUnstagedRollbackRequested = false;
        appearanceSyncUnstagedOrigin = null;
        appearanceSyncRollbackRequested = false;
        appearanceSyncRetryDescriptor = null;
        finishAppearanceConvergence(receipt);
    }

    private void reconcileAppearanceReviewEpoch() {
        if (appearanceSyncStore == null
            || appearanceSyncReviewEpochBeforeRetry < 0) {
            reloadAppearanceSyncState();
            return;
        }
        long before = appearanceSyncReviewEpochBeforeRetry;
        OctavoAppearanceSyncStore.LoadStatus status =
            appearanceSyncStore.load();
        if (status != OctavoAppearanceSyncStore.LoadStatus.LOADED
            && status
               != OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED) {
            reportAppearanceSyncLoadStatus(status);
            return;
        }
        long expected = before == Long.MAX_VALUE
            ? Long.MAX_VALUE : before + 1;
        long loaded = appearanceSyncStore.reviewEpoch();
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        appearanceSyncAwaitingExplicitRetry = false;
        if (appearanceSyncRollbackEpochReconciliation
            && pending != null) {
            if (loaded != before) {
                showAppearanceSyncFailure(
                    "Reading settings update needs attention",
                    "The rollback state and review epoch disagree; "
                        + "automatic recovery is blocked.",
                    this::reconcileAppearanceReviewEpoch);
                return;
            }
            appearanceSyncReviewEpochBeforeRetry = -1;
            appearanceSyncRollbackEpochReconciliation = false;
            appearanceSyncRollbackRequested = true;
            beginPendingAppearanceRollback(
                pending, "Restoring your reading settings.");
            return;
        }
        boolean advancedRollbackAtMaximum =
            appearanceSyncRollbackEpochReconciliation
            && pending == null && loaded == expected;
        if (pending == null && loaded == expected
            && (loaded != before || advancedRollbackAtMaximum)) {
            boolean reconciledRollback =
                appearanceSyncRollbackEpochReconciliation;
            OctavoSurfaceView.AppearancePresentationReceipt receipt = null;
            if (reconciledRollback) {
                receipt = currentAppearanceReceipt();
                OctavoAppearancePortable.Lane local =
                    appearanceSyncStore.localLane();
                if (receipt == null || local == null
                    || !receipt.profile.equals(
                        local.profile.toAppearance())) {
                    showAppearanceSyncFailure(
                        "Reading settings update needs attention",
                        "The rollback was saved, but its exact restored "
                            + "settings are not yet confirmed on screen.",
                        this::reconcileAppearanceReviewEpoch);
                    return;
                }
            }
            appearanceSyncReviewEpochBeforeRetry = -1;
            appearanceSyncRollbackEpochReconciliation = false;
            appearanceSyncReviewPending = false;
            appearanceSyncReviewInitialized = true;
            appearanceSyncPending = null;
            appearanceSyncPendingLoaded = false;
            appearanceSyncRollbackRequested = false;
            appearanceSyncRetryDescriptor = null;
            if (reconciledRollback) {
                finishAppearanceConvergence(receipt);
            } else {
                closeAppearanceSyncPrompt(true);
                considerAppearanceSyncCandidate();
            }
            return;
        }
        if (pending == null && loaded == before
            && !appearanceSyncRollbackEpochReconciliation) {
            appearanceSyncReviewEpochBeforeRetry = -1;
            appearanceSyncRollbackEpochReconciliation = false;
            if (initializeAppearanceSyncReview()) {
                closeAppearanceSyncPrompt(true);
                considerAppearanceSyncCandidate();
            }
            return;
        }
        showAppearanceSyncFailure(
            "Reading settings update needs attention",
            "The reading-settings review epoch changed unexpectedly; "
                + "automatic review is blocked.",
            this::reconcileAppearanceReviewEpoch);
    }

    private void reloadAppearanceSyncStateForRollback(
        OctavoAppearanceSyncStore.Pending expected,
        long reviewEpochBefore,
        boolean advancedDeferredReviewEpoch) {
        if (appearanceSyncStore == null) {
            return;
        }
        OctavoAppearanceSyncStore.LoadStatus status =
            appearanceSyncStore.load();
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        appearanceSyncPending = pending;
        appearanceSyncPendingLoaded = pending != null;
        appearanceSyncAwaitingExplicitRetry = false;
        if (status != OctavoAppearanceSyncStore.LoadStatus.LOADED
            && status
               != OctavoAppearanceSyncStore.LoadStatus.MISSING_CREATED) {
            appearanceSyncRollbackRequested = true;
            reportAppearanceSyncLoadStatus(status);
            return;
        }
        if (pending == null) {
            appearanceSyncRollbackRequested = false;
            if (advancedDeferredReviewEpoch) {
                long expectedEpoch = reviewEpochBefore == Long.MAX_VALUE
                    ? Long.MAX_VALUE : reviewEpochBefore + 1;
                if (appearanceSyncStore.reviewEpoch() != expectedEpoch) {
                    showAppearanceSyncFailure(
                        "Reading settings update needs attention",
                        "The rollback completed, but its review epoch could "
                            + "not be proven. Retry is safe.",
                        this::reloadAppearanceSyncState);
                    return;
                }
                appearanceSyncReviewPending = false;
                appearanceSyncReviewInitialized = true;
            }
            appearanceSyncReviewEpochBeforeRetry = -1;
            appearanceSyncRollbackEpochReconciliation = false;
            appearanceSyncRetryDescriptor = null;
            OctavoSurfaceView.AppearancePresentationReceipt receipt =
                currentAppearanceReceipt();
            if (receipt != null) {
                finishAppearanceConvergence(receipt);
            } else {
                closeAppearanceSyncPrompt(true);
            }
            return;
        }
        if (expected != null && !expected.sameIdentity(pending)) {
            appearanceSyncRollbackRequested = false;
            OctavoSurfaceView.AppearancePresentationReceipt receipt =
                currentAppearanceReceipt();
            if (receipt != null) {
                showLoadedPendingAppearanceRetry(pending, receipt);
            } else {
                showAppearanceSyncFailure(
                    "Reading settings update needs attention",
                    "The reconciled settings are waiting for an exact "
                        + "reader frame. Retry after the reader settles.",
                    this::reloadAppearanceSyncState);
            }
            return;
        }
        appearanceSyncRollbackRequested = true;
        beginPendingAppearanceRollback(
            pending, "Restoring your reading settings.");
    }

    private void stageOrConvergeAfterExplicitRetry(
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        appearanceSyncAwaitingExplicitRetry = false;
        consumedAppearanceReceiptSurface = null;
        processAppearancePresentationReceipt();
    }

    private void retryStagePresentedLocalAppearance(
        OctavoAppearance expectedProfile) {
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null || !receipt.profile.equals(expectedProfile)) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The exact presented settings changed before Retry. Wait "
                    + "for the reader to settle, then Retry.",
                () -> retryStagePresentedLocalAppearance(expectedProfile));
            return;
        }
        stagePresentedLocalAppearance(receipt);
    }

    private void retrySaveCanonicalAppearanceWithoutLaneAdvance(
        OctavoAppearance expectedProfile) {
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null || !receipt.profile.equals(expectedProfile)) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The exact presented settings changed before Retry.",
                () -> retrySaveCanonicalAppearanceWithoutLaneAdvance(
                    expectedProfile));
            return;
        }
        saveCanonicalAppearanceWithoutLaneAdvance(receipt);
    }

    private void retryFinishAppearanceConvergence(
        OctavoAppearance expectedProfile) {
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null || !receipt.profile.equals(expectedProfile)) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The exact presented settings changed before Retry.",
                () -> retryFinishAppearanceConvergence(expectedProfile));
            return;
        }
        finishAppearanceConvergence(receipt);
    }

    private void retryFinishPendingAppearanceRollback(
        OctavoAppearanceSyncStore.Pending expected) {
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null || expected == null
            || !receipt.profile.equals(expected.originAppearance())) {
            beginPendingAppearanceRollback(
                expected, "Restoring your reading settings.");
            return;
        }
        finishPendingAppearanceRollback(expected, receipt);
    }

    private void showAppearanceMutationFailure(
        OctavoAppearanceSyncStore.MutationResult result,
        String fallback,
        Runnable retry) {
        String message = appearanceSyncStore == null
            ? null : appearanceSyncStore.lastError();
        if (TextUtils.isEmpty(message)) {
            message = fallback;
        }
        Runnable exactRetry = retry;
        if (result == OctavoAppearanceSyncStore.MutationResult
                .PUBLISH_UNCERTAIN
            || result == OctavoAppearanceSyncStore.MutationResult.BLOCKED) {
            exactRetry = this::reloadAppearanceSyncState;
        }
        showAppearanceSyncFailure(
            "Reading settings update needs attention",
            message,
            exactRetry);
    }

    private OctavoSurfaceView.AppearancePresentationReceipt
        currentAppearanceReceipt() {
        if (surfaceView == null) {
            return null;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            surfaceView.currentAppearancePresentationReceipt();
        if (receipt != null) {
            latestAppearanceReceipt = receipt;
            appearanceReceiptSurface = surfaceView;
        }
        return receipt;
    }

    private boolean receiptIsCurrent(
        OctavoSurfaceView.AppearancePresentationReceipt receipt) {
        if (receipt == null || surfaceView == null
            || appearanceReceiptSurface != surfaceView) {
            return false;
        }
        OctavoSurfaceView.AppearancePresentationReceipt current =
            surfaceView.currentAppearancePresentationReceipt();
        return current != null
            && current.appearanceGeneration == receipt.appearanceGeneration
            && current.frameCount == receipt.frameCount
            && current.profile.equals(receipt.profile);
    }

    private void considerAppearanceSyncCandidate() {
        if (appearanceSyncAwaitingExplicitRetry) {
            presentRetainedAppearanceSyncFailure();
            return;
        }
        if (!activityResumed || !appearanceSyncReviewInitialized
            || surfaceView == null || appearanceSyncStore == null
            || appearanceSyncStore.pending() != null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || appearancePanel != null || navigationPanel != null
            || searchPanel != null || bookmarksPanel != null
            || surfaceView.hasSelectionForAccessibility()) {
            return;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null) {
            return;
        }
        List<OctavoAppearanceSyncStore.Candidate> candidates =
            appearanceSyncStore.reviewCandidates(receipt.profile);
        if (candidates.isEmpty()) {
            considerReadingPositionCandidate();
            return;
        }
        OctavoAppearanceSyncStore.Candidate candidate = candidates.get(0);
        AppearanceSyncRetryDescriptor retained =
            appearanceSyncRetryDescriptor;
        if (retained != null && retained.candidateAction()) {
            OctavoAppearanceSyncStore.Candidate retryCandidate = null;
            for (OctavoAppearanceSyncStore.Candidate current : candidates) {
                if (retained.matches(current)) {
                    retryCandidate = current;
                    break;
                }
            }
            if (retryCandidate == null) {
                appearanceSyncRetryDescriptor = null;
            } else {
                appearanceSyncCandidate = retryCandidate;
                if (ensureAppearanceSyncChoicePrompt(
                        receipt.profile,
                        retryCandidate.targetAppearance(),
                        retryCandidate)) {
                    showAppearanceSyncFailure(
                        "Reading settings update needs attention",
                        "The earlier choice was not saved. Retry is safe; "
                            + "the reader has not moved.",
                        appearanceCandidateRetry(retained.action));
                }
                return;
            }
        }
        if (candidate.targetAppearance().equals(receipt.profile)) {
            OctavoAppearanceSyncStore.MutationResult converged =
                appearanceSyncStore.recordConverged(receipt.profile);
            if (!converged.succeeded()) {
                showAppearanceMutationFailure(
                    converged,
                    "Matching reading settings could not be recorded.",
                    this::considerAppearanceSyncCandidate);
            } else {
                considerAppearanceSyncCandidate();
            }
            return;
        }
        appearanceSyncCandidate = candidate;
        if (!ensureAppearanceSyncChoicePrompt(
                receipt.profile, candidate.targetAppearance(),
                candidate)) {
            appearanceSyncCandidate = null;
        }
    }

    private boolean appearanceSyncCandidateIsCurrent(
        OctavoAppearanceSyncStore.Candidate candidate,
        OctavoAppearance exactPresentedOrigin) {
        if (candidate == null || exactPresentedOrigin == null
            || appearanceSyncStore == null
            || appearanceSyncStore.pending() != null) {
            return false;
        }
        for (OctavoAppearanceSyncStore.Candidate current
                : appearanceSyncStore.reviewCandidates(
                    exactPresentedOrigin)) {
            if (candidate.sameIdentity(current)) {
                return true;
            }
        }
        return false;
    }

    private void useAppearanceSyncCandidate() {
        useAppearanceSyncCandidate(appearanceSyncCandidate);
    }

    private void useAppearanceSyncCandidate(
        OctavoAppearanceSyncStore.Candidate expected) {
        OctavoAppearanceSyncStore.Candidate candidate =
            appearanceSyncCandidate;
        if (expected == null || candidate == null
            || !expected.sameIdentity(candidate)) {
            return;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null
            || !appearanceSyncCandidateIsCurrent(
                candidate, receipt.profile)
            || !receipt.profile.equals(candidate.originAppearance())) {
            closeAppearanceSyncPrompt(false);
            considerAppearanceSyncCandidate();
            restoreAppearanceSyncFocusAfterClose();
            return;
        }
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.candidate(
                APPEARANCE_RETRY_USE, candidate);
        OctavoAppearanceSyncStore.MutationResult staged =
            appearanceSyncStore.stageRemoteApply(
                candidate, receipt.profile);
        if (staged != OctavoAppearanceSyncStore.MutationResult.UPDATED) {
            appearanceSyncStageUncertain = staged
                == OctavoAppearanceSyncStore.MutationResult
                    .PUBLISH_UNCERTAIN;
            appearanceSyncUnstagedOrigin = receipt.profile;
            showAppearanceMutationFailure(
                staged,
                "The other device's settings could not be staged.",
                this::useAppearanceSyncCandidate);
            return;
        }
        appearanceSyncStageUncertain = false;
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        if (pending == null
            || !pending.targetAppearance().equals(
                candidate.targetAppearance())) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The staged reading settings could not be verified.",
                this::reloadAppearanceSyncState);
            return;
        }
        appearanceSyncPending = pending;
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.pending(
                APPEARANCE_RETRY_FORWARD, pending);
        appearanceSyncCandidate = null;
        appearanceSyncPendingLoaded = false;
        appearanceSyncAwaitingExplicitRetry = false;
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.showWorking(
                "Applying the other device's reading settings. Waiting "
                    + "for the reader to confirm them on screen.");
        }
        requestReaderAppearance(pending.targetAppearance());
    }

    private void keepCurrentAppearanceSettings() {
        keepCurrentAppearanceSettings(appearanceSyncCandidate);
    }

    private void keepCurrentAppearanceSettings(
        OctavoAppearanceSyncStore.Candidate expected) {
        OctavoAppearanceSyncStore.Candidate candidate =
            appearanceSyncCandidate;
        if (expected == null || candidate == null
            || !expected.sameIdentity(candidate)) {
            return;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt == null
            || !appearanceSyncCandidateIsCurrent(
                candidate, receipt.profile)
            || !receipt.profile.equals(candidate.originAppearance())) {
            closeAppearanceSyncPrompt(false);
            considerAppearanceSyncCandidate();
            restoreAppearanceSyncFocusAfterClose();
            return;
        }
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.candidate(
                APPEARANCE_RETRY_KEEP, candidate);
        OctavoAppearanceSyncStore.MutationResult kept =
            appearanceSyncStore.keep(candidate, receipt.profile);
        if (!kept.succeeded()) {
            showAppearanceMutationFailure(
                kept,
                "Keep mine could not be saved.",
                this::keepCurrentAppearanceSettings);
            return;
        }
        appearanceSyncRetryDescriptor = null;
        appearanceSyncAwaitingExplicitRetry = false;
        appearanceSyncRetry = null;
        appearanceSyncStageUncertain = false;
        appearanceSyncAbandonAfterReload = false;
        closeAppearanceSyncPrompt(true);
        considerAppearanceSyncCandidate();
    }

    private void dismissAppearanceSyncForBack() {
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore == null ? null
                : appearanceSyncStore.pending();
        if (pending != null) {
            appearanceSyncRetryDescriptor =
                AppearanceSyncRetryDescriptor.pending(
                    APPEARANCE_RETRY_ROLLBACK, pending);
            beginPendingAppearanceRollback(
                pending,
                "Restoring your reading settings before dismissing the "
                    + "interrupted update.");
            return;
        }
        OctavoAppearanceSyncStore.Candidate candidate =
            appearanceSyncCandidate;
        if (appearanceSyncStageUncertain) {
            appearanceSyncAbandonAfterReload = true;
            appearanceSyncRollbackRequested = true;
            appearanceSyncAwaitingExplicitRetry = false;
            if (candidate != null) {
                appearanceSyncRetryDescriptor =
                    AppearanceSyncRetryDescriptor.candidate(
                        APPEARANCE_RETRY_DISMISS, candidate);
            } else if (appearanceSyncRetryDescriptor == null) {
                appearanceSyncRetryDescriptor =
                    AppearanceSyncRetryDescriptor.reload();
            }
            reloadAppearanceSyncForAbandon();
            return;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (candidate == null) {
            if (appearanceSyncAwaitingExplicitRetry) {
                OctavoAppearance origin = appearanceSyncUnstagedOrigin;
                if (origin == null && appearanceSyncStore != null) {
                    OctavoAppearancePortable.Lane local =
                        appearanceSyncStore.localLane();
                    origin = local == null ? null
                        : local.profile.toAppearance();
                }
                if (origin != null) {
                    appearanceSyncUnstagedOrigin = origin;
                    appearanceSyncUnstagedRollbackRequested = true;
                    appearanceSyncAwaitingExplicitRetry = false;
                    appearanceSyncRetryDescriptor =
                        AppearanceSyncRetryDescriptor.reload();
                    if (appearanceSyncPrompt != null) {
                        appearanceSyncPrompt.showWorking(
                            "Restoring your durable reading settings before "
                                + "closing this update.");
                    }
                    requestReaderAppearance(origin);
                    return;
                }
                if (appearanceSyncPrompt != null) {
                    appearanceSyncPrompt.announceForAccessibility(
                        "Retry is required to finish the reading settings "
                            + "update");
                }
                return;
            }
            closeAppearanceSyncPrompt(true);
            return;
        }
        if (receipt == null
            || !appearanceSyncCandidateIsCurrent(
                candidate, receipt.profile)
            || !receipt.profile.equals(candidate.originAppearance())) {
            closeAppearanceSyncPrompt(false);
            considerAppearanceSyncCandidate();
            restoreAppearanceSyncFocusAfterClose();
            return;
        }
        appearanceSyncRetryDescriptor =
            AppearanceSyncRetryDescriptor.candidate(
                APPEARANCE_RETRY_DISMISS, candidate);
        OctavoAppearanceSyncStore.MutationResult dismissed =
            appearanceSyncStore.dismiss(candidate, receipt.profile);
        if (!dismissed.succeeded()) {
            showAppearanceMutationFailure(
                dismissed,
                "Later could not be saved.",
                this::dismissAppearanceSyncForBack);
            return;
        }
        appearanceSyncRetryDescriptor = null;
        appearanceSyncAwaitingExplicitRetry = false;
        appearanceSyncRetry = null;
        appearanceSyncStageUncertain = false;
        appearanceSyncAbandonAfterReload = false;
        closeAppearanceSyncPrompt(true);
        considerAppearanceSyncCandidate();
    }

    private boolean ensureAppearanceSyncChoicePrompt(
        OctavoAppearance presented,
        OctavoAppearance remote,
        OctavoAppearanceSyncStore.Candidate expected) {
        if (readerRoot == null || surfaceView == null
            || readingPositionPrompt != null
            || presented == null || remote == null
            || presented.equals(remote)
            || surfaceView.hasSelectionForAccessibility()) {
            return false;
        }
        if (appearanceSyncPrompt != null
            && appearanceSyncPromptCandidate != null
            && appearanceSyncPromptCandidate.sameIdentity(expected)) {
            try {
                appearanceSyncPrompt.updateProfiles(presented, remote);
                return true;
            } catch (IllegalArgumentException exception) {
                closeAppearanceSyncPrompt(false);
            }
        } else if (appearanceSyncPrompt != null) {
            closeAppearanceSyncPrompt(false);
        }
        closeReaderPanelsForAppearanceSync();
        OctavoAppearanceSyncPrompt prompt;
        long promptGeneration = appearanceSyncPromptGeneration + 1;
        try {
            prompt = new OctavoAppearanceSyncPrompt(
                this, presented, remote,
                appearanceSyncPromptListener(
                    expected, promptGeneration));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            showOpenFailure(
                "Reading-settings confirmation is unavailable; reopen "
                    + "the book to retry");
            return false;
        }
        appearanceSyncPromptGeneration = promptGeneration;
        appearanceSyncPromptCandidate = expected;
        return installAppearanceSyncPrompt(prompt);
    }

    private boolean ensureAppearanceSyncFailurePrompt() {
        if (readerRoot == null || surfaceView == null
            || readingPositionPrompt != null
            || surfaceView.hasSelectionForAccessibility()
            || (appearanceSyncPrompt == null
                && !hasSettledAppearanceReceiptEvidence())) {
            return false;
        }
        if (appearanceSyncPrompt != null) {
            return true;
        }
        closeReaderPanelsForAppearanceSync();
        OctavoAppearance current = appearance;
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            currentAppearanceReceipt();
        if (receipt != null) {
            current = receipt.profile;
        }
        OctavoAppearanceSyncPrompt prompt;
        long promptGeneration = appearanceSyncPromptGeneration + 1;
        try {
            OctavoAppearanceSyncStore.Pending pending =
                appearanceSyncStore == null ? null
                    : appearanceSyncStore.pending();
            OctavoAppearanceSyncStore.Candidate candidate =
                appearanceSyncCandidate;
            OctavoAppearance target = pending != null
                ? pending.targetAppearance()
                : candidate == null ? null
                    : candidate.targetAppearance();
            prompt = target != null && !target.equals(current)
                ? new OctavoAppearanceSyncPrompt(
                    this, current, target,
                    appearanceSyncPromptListener(
                        null, promptGeneration))
                : new OctavoAppearanceSyncPrompt(
                    this, current, appearanceSyncPromptListener(
                        null, promptGeneration));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            showOpenFailure(
                "Reading-settings recovery is unavailable; reopen the "
                    + "book to retry");
            return false;
        }
        appearanceSyncPromptGeneration = promptGeneration;
        return installAppearanceSyncPrompt(prompt);
    }

    private OctavoAppearanceSyncPrompt.Listener
        appearanceSyncPromptListener(
            OctavoAppearanceSyncStore.Candidate expected,
            long promptGeneration) {
        return new OctavoAppearanceSyncPrompt.Listener() {
            @Override
            public void onUseSettings() {
                if (appearanceSyncPromptGeneration
                    != promptGeneration) {
                    return;
                }
                useAppearanceSyncCandidate(expected);
            }

            @Override
            public void onKeepMine() {
                if (appearanceSyncPromptGeneration
                    != promptGeneration) {
                    return;
                }
                keepCurrentAppearanceSettings(expected);
            }

            @Override
            public void onRetry() {
                if (appearanceSyncPromptGeneration
                    != promptGeneration) {
                    return;
                }
                Runnable retry = appearanceSyncRetry;
                appearanceSyncRetry = null;
                appearanceSyncAwaitingExplicitRetry = false;
                if (retry != null) {
                    retry.run();
                } else if (appearanceSyncStore != null
                           && appearanceSyncStore.pending() != null) {
                    retryPendingAppearanceForward();
                } else {
                    reloadAppearanceSyncState();
                }
            }
        };
    }

    private boolean installAppearanceSyncPrompt(
        OctavoAppearanceSyncPrompt prompt) {
        if (readerRoot == null || surfaceView == null || prompt == null) {
            return false;
        }
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(8));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setBackgroundColor(prompt.overlayColor());
        FrameLayout.LayoutParams layout =
            new FrameLayout.LayoutParams(
                appearanceSyncPromptWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        layout.leftMargin = dp(20);
        layout.topMargin = dp(20);
        layout.rightMargin = dp(20);
        layout.bottomMargin = dp(20);
        overlay.addView(prompt, layout);
        readerRoot.addView(overlay, matchParentLayout());
        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        appearanceSyncOverlay = overlay;
        appearanceSyncPrompt = prompt;
        obscureFailureBannerForModalPrompt();
        int duration = appearanceSyncPromptMotionDuration();
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            prompt.setTranslationY(dp(16));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            prompt.animate().translationY(0.0f)
                .setDuration(duration).start();
        }
        prompt.post(() -> {
            if (appearanceSyncPrompt != prompt) {
                return;
            }
            View focus = prompt.preferredInitialFocus();
            focus.requestFocus();
            focus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            prompt.announceForAccessibility(
                "Reading settings confirmation opened");
        });
        return true;
    }

    private void closeReaderPanelsForAppearanceSync() {
        if (appearancePanel != null) {
            closeAppearancePanel(false);
        }
        if (navigationPanel != null) {
            closeNavigationPanel(false);
        }
        if (searchPanel != null) {
            closeSearchPanel(false);
        }
        if (bookmarksPanel != null) {
            closeBookmarksPanel(false);
        }
    }

    private void showAppearanceSyncFailure(String heading,
                                           String message,
                                           Runnable retry) {
        appearanceSyncAwaitingExplicitRetry = true;
        appearanceSyncRetry = retry;
        appearanceSyncFailureHeading = TextUtils.isEmpty(heading)
            ? "Reading settings update needs attention" : heading;
        appearanceSyncFailureMessage = TextUtils.isEmpty(message)
            ? "The reading-settings update was not saved. Retry is safe."
            : message;
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore == null ? null
                : appearanceSyncStore.pending();
        if (pending != null) {
            appearanceSyncRetryDescriptor =
                AppearanceSyncRetryDescriptor.pending(
                    appearanceSyncRollbackRequested
                        ? APPEARANCE_RETRY_ROLLBACK
                        : APPEARANCE_RETRY_FORWARD,
                    pending);
        } else if (appearanceSyncRetryDescriptor == null) {
            appearanceSyncRetryDescriptor =
                AppearanceSyncRetryDescriptor.reload();
        }
        lastOpenError = appearanceSyncFailureMessage;
        if (presentRetainedAppearanceSyncFailure()) {
            return;
        }
        if (readerRoot == null
            || (activityResumed
                && !hasSettledAppearanceReceiptEvidence()
                && readingPositionPrompt == null
                && appearancePanel == null && navigationPanel == null
                && searchPanel == null && bookmarksPanel == null
                && (surfaceView == null
                    || !surfaceView.hasSelectionForAccessibility()))) {
            showOpenFailure(lastOpenError);
        }
    }

    private boolean presentRetainedAppearanceSyncFailure() {
        if (!appearanceSyncAwaitingExplicitRetry || !activityResumed
            || readerRoot == null || surfaceView == null
            || readingPositionPrompt != null
            || appearancePanel != null || navigationPanel != null
            || searchPanel != null || bookmarksPanel != null
            || surfaceView.hasSelectionForAccessibility()
            || (appearanceSyncPrompt == null
                && !hasSettledAppearanceReceiptEvidence())) {
            return false;
        }
        if (appearanceSyncRetry == null) {
            appearanceSyncRetry = restoredAppearanceSyncRetry();
        }
        if (!ensureAppearanceSyncFailurePrompt()) {
            return false;
        }
        String heading = TextUtils.isEmpty(appearanceSyncFailureHeading)
            ? "Reading settings update needs attention"
            : appearanceSyncFailureHeading;
        String message = TextUtils.isEmpty(appearanceSyncFailureMessage)
            ? appearanceSyncStore != null
                && !TextUtils.isEmpty(appearanceSyncStore.lastError())
                ? appearanceSyncStore.lastError()
                : "The earlier reading-settings update was not saved. "
                    + "Retry is required; this frame will not retry it "
                    + "automatically."
            : appearanceSyncFailureMessage;
        appearanceSyncPrompt.showRetryableFailure(heading, message);
        View focus = appearanceSyncPrompt.preferredInitialFocus();
        focus.post(() -> {
            if (appearanceSyncPrompt == null || !focus.isShown()) {
                return;
            }
            focus.requestFocus();
            focus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
        });
        return true;
    }

    private Runnable restoredAppearanceSyncRetry() {
        if (appearanceSyncAbandonAfterReload) {
            return this::reloadAppearanceSyncForAbandon;
        }
        if (appearanceSyncReviewEpochBeforeRetry >= 0) {
            return this::reconcileAppearanceReviewEpoch;
        }
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore == null ? null
                : appearanceSyncStore.pending();
        if (pending != null) {
            boolean rollback = appearanceSyncRollbackRequested
                || (appearanceSyncRetryDescriptor != null
                    && appearanceSyncRetryDescriptor.action
                       == APPEARANCE_RETRY_ROLLBACK);
            return rollback
                ? () -> beginPendingAppearanceRollback(
                    pending, "Restoring your reading settings.")
                : this::retryPendingAppearanceForward;
        }
        if (appearanceSyncRetryDescriptor != null
            && appearanceSyncRetryDescriptor.candidateAction()) {
            return appearanceCandidateRetry(
                appearanceSyncRetryDescriptor.action);
        }
        return this::reloadAppearanceSyncState;
    }

    private boolean hasSettledAppearanceReceiptEvidence() {
        if (surfaceView == null) {
            return false;
        }
        OctavoSurfaceView.AppearancePresentationReceipt receipt =
            surfaceView.currentAppearancePresentationReceipt();
        if (receipt == null || receipt.profile == null
            || !receipt.strictResumeSettled) {
            return false;
        }
        latestAppearanceReceipt = receipt;
        appearanceReceiptSurface = surfaceView;
        return true;
    }

    private Runnable appearanceCandidateRetry(int action) {
        switch (action) {
            case APPEARANCE_RETRY_USE:
                return this::useAppearanceSyncCandidate;
            case APPEARANCE_RETRY_KEEP:
                return this::keepCurrentAppearanceSettings;
            case APPEARANCE_RETRY_DISMISS:
                return this::dismissAppearanceSyncForBack;
            default:
                return this::reloadAppearanceSyncState;
        }
    }

    private void restorePendingAppearanceAfterLifecycle() {
        if (appearanceSyncStore == null || surfaceView == null) {
            return;
        }
        OctavoAppearanceSyncStore.Pending pending =
            appearanceSyncStore.pending();
        if (pending == null) {
            return;
        }
        appearanceSyncPending = pending;
        if (!appearanceSyncAwaitingExplicitRetry) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The reading-settings update was paused before its durable "
                    + "confirmation finished. Retry is safe.",
                appearanceSyncRollbackRequested
                    ? () -> beginPendingAppearanceRollback(
                        pending, "Restoring your reading settings.")
                    : this::retryPendingAppearanceForward);
        }
    }

    private int appearanceSyncPromptWidth() {
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = readerRoot == null
                || readerRoot.getWidth() <= 0
            ? displayWidth : readerRoot.getWidth();
        return Math.min(
            appearancePanelWidth(),
            Math.max(availableWidth - dp(40), 1));
    }

    private void updateAppearanceSyncPromptBounds() {
        if (appearanceSyncPrompt == null
            || !(appearanceSyncPrompt.getLayoutParams()
                instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams layout =
            (FrameLayout.LayoutParams)
                appearanceSyncPrompt.getLayoutParams();
        layout.width = appearanceSyncPromptWidth();
        appearanceSyncPrompt.setLayoutParams(layout);
    }

    private int appearanceSyncPromptMotionDuration() {
        if (appearanceSyncPrompt != null
            && appearanceSyncPrompt.suppressHostMotion()) {
            return 0;
        }
        return sideSheetMotionDuration(
            OctavoDesignTokens.forAppearance(appearance));
    }

    private void closeAppearanceSyncPrompt(boolean restoreFocus) {
        boolean ownedPrompt = appearanceSyncOverlay != null
            || appearanceSyncPrompt != null;
        appearanceSyncPromptGeneration += 1;
        if (appearanceSyncOverlay != null) {
            appearanceSyncOverlay.animate().cancel();
            if (appearanceSyncOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup)appearanceSyncOverlay.getParent())
                    .removeView(appearanceSyncOverlay);
            }
        }
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.animate().cancel();
        }
        appearanceSyncOverlay = null;
        appearanceSyncPrompt = null;
        appearanceSyncPromptCandidate = null;
        appearanceSyncCandidate = null;
        if (!ownedPrompt) {
            return;
        }
        restoreReaderAccessibilityBoundary();
        if (readingPositionPrompt == null) {
            restoreFailureBannerAfterModalPrompt();
        }
        if (restoreFocus) {
            restoreAppearanceSyncFocusAfterClose();
        }
    }

    private void restoreReaderAccessibilityBoundary() {
        boolean readerObscured = appearancePanel != null
            || navigationPanel != null || searchPanel != null
            || bookmarksPanel != null || readingPositionPrompt != null
            || appearanceSyncPrompt != null;
        if (surfaceView != null) {
            surfaceView.setImportantForAccessibility(
                readerObscured
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        int chromeImportance = chromeVisible && !readerObscured
            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(chromeImportance);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                chromeImportance);
        }
    }

    private void restoreAppearanceSyncFocusAfterClose() {
        if (appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || surfaceView == null || !surfaceView.isShown()) {
            return;
        }
        OctavoSurfaceView target = surfaceView;
        target.requestFocus();
        target.post(() -> {
            if (appearanceSyncPrompt != null
                || readingPositionPrompt != null
                || surfaceView != target
                || !target.isShown()) {
                return;
            }
            target.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            target.announceForAccessibility(
                "Reading settings confirmation closed");
        });
    }

    private void closeStaleAppearanceSyncChoice(boolean restoreFocus) {
        if (appearanceSyncCandidate != null
            && appearanceSyncStore.pending() == null) {
            closeAppearanceSyncPrompt(restoreFocus);
        }
    }

    private boolean openNoteFromMarker(
        OctavoAnnotationStore.Note projectedNote) {
        if (projectedNote == null || annotationStore == null
            || noteDraftStore == null || activeBook == null) {
            showOpenFailure("Notes are unavailable.");
            return false;
        }
        OctavoAnnotationStore.Note durableNote = null;
        for (OctavoAnnotationStore.Note note
                : annotationStore.notes(activeBook.key)) {
            if (projectedNote.recordId.equals(note.recordId)) {
                durableNote = note;
                break;
            }
        }
        if (durableNote == null) {
            showOpenFailure(
                "This note is no longer available; reopen the book to refresh its marker.");
            return true;
        }

        OctavoNoteDraftStore.Draft existing = noteDraftStore.current();
        if (existing != null) {
            openBookmarksPanel();
            if (bookmarksPanel == null) {
                showOpenFailure("The note editor is unavailable.");
                return false;
            }
            if (!existing.recordId.equals(durableNote.recordId)) {
                bookmarksPanel.showError(
                    "Finish or cancel the current note draft before opening another note.");
            } else {
                bookmarksPanel.announceForAccessibility("Note opened");
            }
            return true;
        }

        openBookmarksPanel();
        if (bookmarksPanel == null) {
            showOpenFailure("The note editor is unavailable.");
            return false;
        }
        return beginNoteEditor(durableNote, durableNote.preferredBody());
    }

    private boolean beginNoteEditor(OctavoAnnotationStore.Note note,
                                    String body) {
        if (note == null || noteDraftStore == null) {
            return false;
        }
        OctavoNoteDraftStore.Draft draft =
            new OctavoNoteDraftStore.Draft(
                note.recordId,
                note.revisionToken,
                note.bookDigest,
                note.spineIndex,
                note.byteStart,
                note.byteEnd,
                note.attachedHighlightId,
                note.excerpt,
                body == null ? "" : body);
        if (!noteDraftStore.save(draft)) {
            if (bookmarksPanel != null) {
                bookmarksPanel.showError(
                    "Unable to save a recoverable note draft; the note was not changed");
            } else {
                showOpenFailure(
                    "Unable to save a recoverable note draft; the note was not changed");
            }
            return false;
        }
        if (bookmarksPanel == null) {
            showOpenFailure("The note editor is unavailable.");
            return false;
        }
        bookmarksPanel.showNoteEditor(draft, false, note.conflicted);
        return true;
    }

    private void updateReaderNavigationAvailability(OctavoSurfaceView view) {
        if (view == null) {
            return;
        }
        boolean pending = view.hasNavigationPending();
        boolean canReturn = !pending && view.canReturnInHistory();
        if (readerReturn != null) {
            boolean returnHadFocus = readerReturn.hasFocus()
                || readerReturn.isAccessibilityFocused();
            if (!canReturn && returnHadFocus && view.isShown()) {
                view.requestFocus(View.FOCUS_FORWARD);
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
            }
            readerReturn.setEnabled(canReturn);
            readerReturn.setVisibility(
                canReturn ? View.VISIBLE : View.INVISIBLE);
            readerReturn.setImportantForAccessibility(
                canReturn
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        if (readerProgress != null) {
            readerProgress.setEnabled(!pending);
        }
        if (readerSearch != null) {
            readerSearch.setEnabled(!pending);
        }
        if (readerBookmarkToggle != null) {
            readerBookmarkToggle.setEnabled(!pending);
        }
        if (readerBookmarks != null) {
            readerBookmarks.setEnabled(!pending);
        }
        int libraryId = readerLibrary == null
            ? View.NO_ID : readerLibrary.getId();
        int bookmarksId = readerBookmarks == null
            ? libraryId : readerBookmarks.getId();
        int progressId = readerProgress == null
            ? bookmarksId : readerProgress.getId();
        int afterReaderId = canReturn && readerReturn != null
            ? readerReturn.getId() : progressId;
        view.setAccessibilityTraversalBefore(afterReaderId);
        view.setNextFocusForwardId(afterReaderId);
        view.setKeyboardBoundaryFocusIds(
            readerSettings == null ? View.NO_ID : readerSettings.getId(),
            afterReaderId);
        if (readerReturn != null) {
            readerReturn.setAccessibilityTraversalBefore(progressId);
            readerReturn.setNextFocusForwardId(progressId);
        }
        if (readerProgress != null) {
            readerProgress.setAccessibilityTraversalBefore(bookmarksId);
            readerProgress.setNextFocusForwardId(bookmarksId);
        }
        if (readerBookmarks != null) {
            readerBookmarks.setAccessibilityTraversalBefore(libraryId);
            readerBookmarks.setNextFocusForwardId(libraryId);
        }
    }

    private String readerProgressLabel(OctavoSurfaceView view) {
        String label = view == null ? null : view.progressLabelForTesting();
        if (label == null || label.trim().isEmpty()) {
            OctavoProgressDisplay fallback = progressDisplay == null
                ? OctavoProgressDisplay.defaults() : progressDisplay;
            return fallback.label();
        }
        return label;
    }

    private void updateProgressControlLabel(Button control,
                                            CharSequence label) {
        if (control == null) {
            return;
        }
        String visible = label == null ? "" : label.toString().trim();
        if (visible.isEmpty()) {
            OctavoProgressDisplay fallback = progressDisplay == null
                ? OctavoProgressDisplay.defaults() : progressDisplay;
            visible = fallback.label();
        }
        control.setText(visible);
        control.setContentDescription(
            "Open reader navigation. " + visible);
    }

    private void toggleCurrentBookmark() {
        if (annotationStore == null || activeBook == null
            || surfaceView == null) {
            showOpenFailure("The current reading position is unavailable");
            return;
        }
        long[] anchor = surfaceView.presentedAnchorForAnnotations();
        if (anchor == null) {
            showOpenFailure(
                "Wait for the current page before changing its bookmark");
            return;
        }
        String progress = readerProgressLabel(surfaceView);
        String label = boundedUtf8(
            "Bookmark at " + progress, 256);
        String excerpt = annotationExcerpt(
            surfaceView.visibleTextForTesting());
        OctavoAnnotationStore.MutationResult result =
            annotationStore.toggleBookmark(
                activeBook.key, anchor[1], anchor[2], label, excerpt);
        if (!result.succeeded()) {
            showOpenFailure(annotationMutationFailure(result));
            return;
        }
        updateBookmarkToggle();
        refreshBookmarksPanel();
        String announcement = result == OctavoAnnotationStore
            .MutationResult.ADDED
                ? "Bookmark added" : "Bookmark removed";
        if (readerBookmarkToggle != null) {
            readerBookmarkToggle.announceForAccessibility(announcement);
        }
    }

    private void updateBookmarkToggle() {
        if (readerBookmarkToggle == null) {
            return;
        }
        long[] anchor = surfaceView == null
            ? null : surfaceView.presentedAnchorForAnnotations();
        boolean ready = activeBook != null && anchor != null;
        boolean bookmarked = ready && annotationStore != null
            && annotationStore.isBookmarked(
                activeBook.key, anchor[1], anchor[2]);
        readerBookmarkToggle.setText(bookmarked ? "★" : "☆");
        readerBookmarkToggle.setSelected(bookmarked);
        readerBookmarkToggle.setContentDescription(bookmarked
            ? getString(R.string.bookmark_remove_current)
            : getString(R.string.bookmark_add));
        readerBookmarkToggle.setEnabled(
            ready && surfaceView != null
                && !surfaceView.hasNavigationPending());
    }

    private void refreshBookmarksPanel() {
        if (bookmarksPanel != null && activeBook != null
            && annotationStore != null) {
            bookmarksPanel.updateAnnotations(
                annotationStore.bookmarks(activeBook.key),
                annotationStore.highlights(activeBook.key),
                annotationStore.notes(activeBook.key));
        }
    }

    private static String annotationExcerpt(String visibleText) {
        String normalized = visibleText == null
            ? "" : visibleText.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            normalized = "Saved reading position";
        }
        return boundedUtf8(normalized, 512);
    }

    private static String boundedUtf8(String value, int maximumBytes) {
        if (value == null || maximumBytes < 0) {
            throw new IllegalArgumentException();
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            <= maximumBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            String candidate = result.toString()
                + new String(Character.toChars(codePoint));
            if (candidate.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8).length
                > maximumBytes) {
                break;
            }
            result.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static String annotationMutationFailure(
        OctavoAnnotationStore.MutationResult result) {
        if (result == OctavoAnnotationStore.MutationResult.BLOCKED) {
            return "Annotations are read-only until their saved state is recovered";
        }
        if (result == OctavoAnnotationStore.MutationResult.LIMIT) {
            return "The bounded annotation store is full; remove an annotation and retry";
        }
        if (result == OctavoAnnotationStore.MutationResult.CONFLICT) {
            return "The note changed since editing began; reopen it to review every retained version";
        }
        return "Unable to save the annotation; the previous state was preserved";
    }

    private void reportAnnotationLoadStatus(
        OctavoAnnotationStore.LoadStatus status) {
        if (status == OctavoAnnotationStore.LoadStatus
                .CORRUPT_QUARANTINED) {
            showOpenFailure(
                "Invalid annotation state was isolated; annotations were reset");
        } else if (status == OctavoAnnotationStore.LoadStatus
                       .FUTURE_VERSION_BLOCKED) {
            showOpenFailure(
                "Annotations were created by a newer 8vo and are read-only here");
        } else if (status == OctavoAnnotationStore.LoadStatus
                       .CORRUPT_BLOCKED) {
            showOpenFailure(
                "Invalid annotation state could not be isolated; annotations are read-only");
        }
    }

    private void reportNoteDraftLoadStatus(
        OctavoNoteDraftStore.LoadStatus status) {
        if (status == OctavoNoteDraftStore.LoadStatus
                .CORRUPT_QUARANTINED) {
            showOpenFailure(
                "An invalid unsaved note draft was isolated and reset");
        } else if (status == OctavoNoteDraftStore.LoadStatus
                       .FUTURE_VERSION_BLOCKED) {
            showOpenFailure(
                "An unsaved note draft was created by a newer 8vo and was preserved");
        } else if (status == OctavoNoteDraftStore.LoadStatus
                       .CORRUPT_BLOCKED) {
            showOpenFailure(
                "An invalid note draft could not be isolated; draft editing is unavailable");
        }
    }

    private void scheduleNavigationSnapshotRefresh() {
        if (navigationSnapshotRefreshPosted || navigationPanel == null
            || surfaceView == null || readerRoot == null) {
            return;
        }
        navigationSnapshotRefreshPosted = true;
        readerRoot.postOnAnimation(refreshNavigationSnapshot);
    }

    private void cancelNavigationSnapshotRefresh() {
        if (readerRoot != null) {
            readerRoot.removeCallbacks(refreshNavigationSnapshot);
        }
        navigationSnapshotRefreshPosted = false;
    }

    private void scheduleSearchSnapshotRefresh() {
        if (searchSnapshotRefreshPosted || searchPanel == null
            || surfaceView == null || readerRoot == null) {
            return;
        }
        searchSnapshotRefreshPosted = true;
        readerRoot.postOnAnimation(refreshSearchSnapshot);
    }

    private void cancelSearchSnapshotRefresh() {
        if (readerRoot != null) {
            readerRoot.removeCallbacks(refreshSearchSnapshot);
        }
        searchSnapshotRefreshPosted = false;
    }

    private void reportNavigationRequestFailure(String message) {
        String visible = message == null || message.trim().isEmpty()
            ? "Unable to complete reader navigation" : message.trim();
        OctavoNavigationPanel target = navigationPanel;
        if (target != null) {
            target.post(() -> {
                if (navigationPanel == target) {
                    target.showError(visible);
                }
            });
            return;
        }
        OctavoSearchPanel searchTarget = searchPanel;
        if (searchTarget != null) {
            searchTarget.post(() -> {
                if (searchPanel == searchTarget) {
                    searchTarget.showError(visible);
                }
            });
            return;
        }
        OctavoBookmarksPanel bookmarksTarget = bookmarksPanel;
        if (bookmarksTarget != null) {
            bookmarksTarget.post(() -> {
                if (bookmarksPanel == bookmarksTarget) {
                    bookmarksTarget.showError(visible);
                }
            });
            return;
        }
        showOpenFailure(visible);
    }

    private static boolean moveReaderKeyboardFocus(
        int keyCode,
        KeyEvent event,
        View backward,
        View forward) {
        if (keyCode != KeyEvent.KEYCODE_TAB
            || event.getAction() != KeyEvent.ACTION_DOWN
            || event.getRepeatCount() != 0
            || !(event.hasNoModifiers()
                 || event.hasModifiers(KeyEvent.META_SHIFT_ON))) {
            return false;
        }
        boolean reverse = event.isShiftPressed();
        View target = reverse ? backward : forward;
        return target != null
            && target.isShown()
            && target.isEnabled()
            && target.isFocusable()
            && target.requestFocus(
                reverse ? View.FOCUS_BACKWARD : View.FOCUS_FORWARD);
    }

    private void applyWindowAppearance() {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        getWindow().setStatusBarColor(tokens.statusBar);
        getWindow().setNavigationBarColor(tokens.navigationBar);
        getWindow().getDecorView().setBackgroundColor(tokens.launch);
        int systemUi = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!tokens.useLightSystemBarIcons()) {
            systemUi |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            systemUi |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
    }

    private void requestReaderAppearance(OctavoAppearance requested) {
        if (surfaceView == null || requested == null) {
            return;
        }
        prepareAppearanceTransition(requested);
        surfaceView.requestAppearance(requested);
    }

    private void installReaderEntryCover() {
        cancelReaderEntryCover();
        if (readerRoot == null) {
            return;
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        View cover = new View(this);
        cover.setId(R.id.octavo_reader_entry_cover);
        cover.setBackgroundColor(tokens.readerPage);
        cover.setClickable(true);
        cover.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        readerRoot.addView(cover, matchParentLayout());
        readerEntryCover = cover;
    }

    private void finishReaderEntryCover() {
        if (readerEntryCover == null || readerRoot == null) {
            return;
        }
        int generation = readerEntryCoverGeneration;
        FrameLayout root = readerRoot;
        root.postOnAnimation(() -> root.postOnAnimation(() -> {
            if (readerRoot == root
                && generation == readerEntryCoverGeneration) {
                cancelReaderEntryCover();
            }
        }));
    }

    private void cancelReaderEntryCover() {
        readerEntryCoverGeneration += 1;
        if (readerEntryCover != null
            && readerEntryCover.getParent() instanceof ViewGroup) {
            ((ViewGroup)readerEntryCover.getParent())
                .removeView(readerEntryCover);
        }
        readerEntryCover = null;
    }

    private void prepareAppearanceTransition(OctavoAppearance requested) {
        if (readerRoot == null) {
            return;
        }
        if (requested.themeId() == appearance.themeId()) {
            if (appearanceTransitionScrim != null
                && surfaceView != null
                && surfaceView.hasAppearanceAwaitingPresentation()) {
                retargetAppearanceTransition(requested);
            } else {
                cancelAppearanceTransition();
            }
            return;
        }
        retargetAppearanceTransition(requested);
    }

    private void retargetAppearanceTransition(
        OctavoAppearance requested) {
        appearanceTransitionTarget = requested;
        appearanceTransitionGeneration += 1;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(requested);
        if (appearanceTransitionScrim == null) {
            View scrim = new View(this);
            scrim.setId(R.id.octavo_appearance_transition);
            scrim.setClickable(true);
            scrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int index = readerRoot.getChildCount();
            if (appearanceOverlay != null) {
                index = Math.min(
                    index, readerRoot.indexOfChild(appearanceOverlay));
            }
            if (appearanceSyncOverlay != null) {
                index = Math.min(
                    index,
                    readerRoot.indexOfChild(appearanceSyncOverlay));
            }
            index = Math.max(index, 0);
            readerRoot.addView(scrim, index, matchParentLayout());
            appearanceTransitionScrim = scrim;
        }
        appearanceTransitionScrim.setBackgroundColor(tokens.readerPage);
    }

    private void finishAppearanceTransition(OctavoAppearance presented) {
        if (appearanceTransitionScrim == null
            || appearanceTransitionTarget == null
            || !appearanceTransitionTarget.equals(presented)
            || (surfaceView != null
                && surfaceView.hasPendingAppearanceRequest())
            || readerRoot == null) {
            return;
        }
        int generation = appearanceTransitionGeneration;
        FrameLayout root = readerRoot;
        root.postOnAnimation(() -> root.postOnAnimation(() -> {
            if (readerRoot == root
                && generation == appearanceTransitionGeneration
                && appearanceTransitionTarget != null
                && appearanceTransitionTarget.equals(presented)) {
                cancelAppearanceTransition();
            }
        }));
    }

    private void cancelAppearanceTransition() {
        appearanceTransitionGeneration += 1;
        if (appearanceTransitionScrim != null
            && appearanceTransitionScrim.getParent() instanceof ViewGroup) {
            ((ViewGroup)appearanceTransitionScrim.getParent())
                .removeView(appearanceTransitionScrim);
        }
        appearanceTransitionScrim = null;
        appearanceTransitionTarget = null;
    }

    private void persistPresentedAppearance(OctavoAppearance presented) {
        pendingAppearancePersistence = presented;
        if (appearancePersistencePosted) {
            return;
        }
        View decor = getWindow().getDecorView();
        appearancePersistencePosted = true;
        decor.postOnAnimation(persistAppearance);
    }

    private void persistPresentedProgress(OctavoProgressDisplay presented) {
        pendingProgressPersistence = presented;
        if (progressPersistencePosted) {
            return;
        }
        View decor = getWindow().getDecorView();
        progressPersistencePosted = true;
        decor.postOnAnimation(persistProgress);
    }

    private void flushProgressPersistence() {
        if (progressPersistencePosted) {
            getWindow().getDecorView().removeCallbacks(persistProgress);
            progressPersistencePosted = false;
        }
        OctavoProgressDisplay candidate = pendingProgressPersistence;
        pendingProgressPersistence = null;
        if (candidate != null && !progressStore.save(candidate)) {
            // Retain the latest presented choice for an explicit lifecycle or
            // later presentation retry; never publish a fallback record.
            pendingProgressPersistence = candidate;
            reportProgressPersistenceFailure();
        }
    }

    private void reportProgressPersistenceFailure() {
        String message = "Progress display changed, but could not be saved";
        lastOpenError = message;
        if (activityResumed) {
            OctavoNavigationPanel target = navigationPanel;
            if (target != null) {
                target.showError(message);
            } else {
                showOpenFailure(message);
            }
        } else {
            deferredProgressFailure = message;
        }
    }

    private void flushAppearancePersistence() {
        if (appearancePersistencePosted) {
            getWindow().getDecorView().removeCallbacks(persistAppearance);
            appearancePersistencePosted = false;
        }
        OctavoAppearance candidate = pendingAppearancePersistence;
        if (candidate != null && appearanceSyncAwaitingExplicitRetry
            && appearanceSyncStore != null
            && appearanceSyncStore.pending() != null) {
            // O1SS owns an explicit saga Retry. A pause/destroy flush must
            // not publish O7ST out of order or consume that Retry intent.
            return;
        }
        pendingAppearancePersistence = null;
        if (candidate != null && !appearanceStore.save(candidate)) {
            // Retain the exact successfully presented appearance. A failed
            // atomic replace must not turn a newer on-screen choice into a
            // one-shot persistence attempt.
            pendingAppearancePersistence = candidate;
            reportAppearancePersistenceFailure();
        }
    }

    private void reportAppearancePersistenceFailure() {
        String message = "Appearance changed, but could not be saved";
        lastOpenError = message;
        if (activityResumed) {
            showOpenFailure(message);
        } else {
            deferredAppearanceFailure = message;
        }
    }

    private void setChromeViewsVisible(boolean visible, boolean animate) {
        chromeVisible = visible;
        if (!visible && surfaceView != null) {
            View focused = getCurrentFocus();
            if (focused != null
                && (focused.getParent() == readerTopChrome
                    || focused.getParent() == readerBottomChrome)) {
                surfaceView.requestFocus(View.FOCUS_FORWARD);
            }
        }
        if (surfaceView != null) {
            surfaceView.beginChromeCompositionTransition(
                readerChromeMotionDuration(animate));
        }
        setChromeViewVisible(readerTopChrome, visible, animate);
        setChromeViewVisible(readerBottomChrome, visible, animate);
        updateReaderChromeComposition(
            surfaceView, readerTopChrome, readerBottomChrome, animate);
    }

    private void setChromeViewVisible(View view,
                                      boolean visible,
                                      boolean animate) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setImportantForAccessibility(
            visible && appearancePanel == null
                && navigationPanel == null
                && searchPanel == null
                && bookmarksPanel == null
                && readingPositionPrompt == null
                && appearanceSyncPrompt == null
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int duration = readerChromeMotionDuration(animate);
        if (!animate || duration == 0) {
            view.setAlpha(visible ? 1.0f : 0.0f);
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0.0f);
            view.animate().alpha(1.0f).setDuration(duration).start();
        } else {
            view.animate()
                .alpha(0.0f)
                .setDuration(duration)
                .withEndAction(() -> view.setVisibility(View.INVISIBLE))
                .start();
        }
    }

    private int readerChromeMotionDuration(boolean animate) {
        if (!animate) {
            return 0;
        }
        AccessibilityManager manager = (AccessibilityManager)
            getSystemService(ACCESSIBILITY_SERVICE);
        if (manager != null && manager.isEnabled()
            && manager.isTouchExplorationEnabled()) {
            return 0;
        }
        return OctavoDesignTokens.forAppearance(appearance)
            .fastMotionMs(appearance);
    }

    private int sideSheetMotionDuration(OctavoDesignTokens tokens) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !ValueAnimator.areAnimatorsEnabled()) {
            return 0;
        }
        AccessibilityManager manager = (AccessibilityManager)
            getSystemService(ACCESSIBILITY_SERVICE);
        if (manager != null && manager.isEnabled()
            && manager.isTouchExplorationEnabled()) {
            return 0;
        }
        return tokens.standardMotionMs(appearance);
    }

    static int navigationPanelStartTranslationX(View panel,
                                                int distancePx) {
        if (panel == null || distancePx < 0) {
            throw new IllegalArgumentException();
        }
        return panel.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
            ? -distancePx : distancePx;
    }

    static void resolveSideSheetLayoutDirection(View panel, View anchor) {
        if (panel == null || anchor == null) {
            throw new IllegalArgumentException();
        }
        panel.setLayoutDirection(anchor.getLayoutDirection());
    }

    static String navigationThemeFailureMessage() {
        return "Reader navigation styling is unavailable. Reopen Navigation "
            + "to retry.";
    }

    static boolean navigationRequestIsPending(int result) {
        return result == OctavoNative.NAVIGATION_ACCEPTED;
    }

    static int boundedSideSheetWidth(int availableWidthPx,
                                     int outerGapPx,
                                     int maximumWidthPx) {
        if (availableWidthPx <= 0 || outerGapPx < 0
            || maximumWidthPx <= 0) {
            throw new IllegalArgumentException();
        }
        return Math.min(maximumWidthPx,
                        Math.max(availableWidthPx - outerGapPx, 1));
    }

    private void applyReaderAppearanceTokens() {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        if (readerRoot != null) {
            readerRoot.setBackgroundColor(tokens.readerPage);
        }
        if (readerEntryCover != null) {
            readerEntryCover.setBackgroundColor(tokens.readerPage);
        }
        if (systemBarRoot != null) {
            systemBarRoot.setBackgroundColor(tokens.readerPage);
        }
        if (statusBarScrim != null) {
            statusBarScrim.setBackgroundColor(tokens.statusBar);
        }
        if (navigationBarScrim != null) {
            navigationBarScrim.setBackgroundColor(tokens.navigationBar);
        }
        tintChrome(readerTopChrome, tokens);
        tintChrome(readerBottomChrome, tokens);
        if (appearanceOverlay != null) {
            appearanceOverlay.setBackgroundColor(tokens.overlay);
        }
        if (navigationPanel != null) {
            navigationPanel.applyAppearance(appearance);
        }
        if (navigationOverlay != null && navigationPanel != null) {
            navigationOverlay.setBackgroundColor(
                navigationPanel.overlayColor());
        }
        if (searchPanel != null) {
            searchPanel.applyAppearance(appearance);
        }
        if (searchOverlay != null && searchPanel != null) {
            searchOverlay.setBackgroundColor(searchPanel.overlayColor());
        }
        if (bookmarksPanel != null) {
            bookmarksPanel.applyAppearance(appearance);
        }
        if (bookmarksOverlay != null && bookmarksPanel != null) {
            bookmarksOverlay.setBackgroundColor(
                bookmarksPanel.overlayColor());
        }
        if (appearanceSyncPrompt != null) {
            appearanceSyncPrompt.applyAppearance(appearance);
        }
        if (appearanceSyncOverlay != null
            && appearanceSyncPrompt != null) {
            appearanceSyncOverlay.setBackgroundColor(
                appearanceSyncPrompt.overlayColor());
        }
        if (failureBanner != null) {
            failureBanner.setTextColor(tokens.error);
            failureBanner.setBackgroundColor(tokens.dialogSurface);
        }
    }

    private void tintChrome(LinearLayout chrome,
                            OctavoDesignTokens tokens) {
        if (chrome == null) {
            return;
        }
        chrome.setBackgroundColor(tokens.chromeSurface);
        for (int index = 0; index < chrome.getChildCount(); ++index) {
            View child = chrome.getChildAt(index);
            if (child instanceof Button) {
                Button button = (Button)child;
                boolean library =
                    button.getId() == R.id.octavo_reader_library;
                button.setTextColor(
                    library ? tokens.onLibraryReturn : tokens.chromeText);
                button.setBackgroundTintList(ColorStateList.valueOf(
                    library ? tokens.libraryReturn : tokens.buttonSurface));
            } else if (child instanceof TextView) {
                ((TextView)child).setTextColor(tokens.chromeText);
            }
        }
    }

    private void openBookmarksPanel() {
        if (readerRoot == null || surfaceView == null
            || activeBook == null || annotationStore == null
            || bookmarksPanel != null
            || readingPositionPrompt != null
            || appearanceSyncPrompt != null) {
            return;
        }
        if (appearancePanel != null) {
            closeAppearancePanel(false);
        }
        if (navigationPanel != null) {
            closeNavigationPanel(false);
        }
        if (searchPanel != null) {
            closeSearchPanel(false);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setId(R.id.octavo_bookmarks_overlay);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(4));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setOnClickListener(view -> closeBookmarksPanel());

        OctavoBookmarksPanel panel;
        try {
            panel = new OctavoBookmarksPanel(
                this,
                appearance,
                new OctavoBookmarksPanel.Listener() {
                    @Override
                    public void onDismiss() {
                        closeBookmarksPanel();
                    }

                    @Override
                    public void onNavigate(
                        OctavoAnnotationStore.Bookmark bookmark) {
                        if (surfaceView == null) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    "The reader is unavailable.");
                            }
                            return;
                        }
                        int result = surfaceView.requestAnnotationNavigation(
                            bookmark.spineIndex, bookmark.byteOffset);
                        if (result == OctavoNative.NAVIGATION_ACCEPTED) {
                            bookmarkNavigationPending = true;
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showNavigationPending();
                            }
                        } else if (result == OctavoNative
                                       .NAVIGATION_ALREADY_PRESENTED) {
                            closeBookmarksPanel();
                        }
                        updateReaderNavigationAvailability(surfaceView);
                    }

                    @Override
                    public void onRemove(
                        OctavoAnnotationStore.Bookmark bookmark) {
                        OctavoAnnotationStore.MutationResult result =
                            annotationStore.removeBookmark(
                                bookmark.recordId);
                        if (result.succeeded()) {
                            refreshBookmarksPanel();
                            updateBookmarkToggle();
                            if (bookmarksPanel != null) {
                                bookmarksPanel.announceForAccessibility(
                                    "Bookmark removed");
                            }
                        } else if (bookmarksPanel != null) {
                            bookmarksPanel.showError(
                                annotationMutationFailure(result));
                        }
                    }

                    @Override
                    public void onNavigate(
                        OctavoAnnotationStore.Highlight highlight) {
                        if (surfaceView == null) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    "The reader is unavailable.");
                            }
                            return;
                        }
                        int result = surfaceView.requestAnnotationNavigation(
                            highlight.spineIndex, highlight.byteStart);
                        if (result == OctavoNative.NAVIGATION_ACCEPTED) {
                            bookmarkNavigationPending = true;
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showNavigationPending();
                            }
                        } else if (result == OctavoNative
                                       .NAVIGATION_ALREADY_PRESENTED) {
                            closeBookmarksPanel();
                        }
                        updateReaderNavigationAvailability(surfaceView);
                    }

                    @Override
                    public void onRemove(
                        OctavoAnnotationStore.Highlight highlight) {
                        OctavoAnnotationStore.MutationResult result =
                            annotationStore.removeHighlight(highlight.recordId);
                        if (!result.succeeded()) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    annotationMutationFailure(result));
                            }
                            return;
                        }
                        List<OctavoAnnotationStore.Highlight> updated =
                            annotationStore.highlights(activeBook.key);
                        boolean projected = surfaceView != null
                            && surfaceView.replaceHighlights(
                                updated, false, "Highlight removed.");
                        refreshBookmarksPanel();
                        if (!projected && bookmarksPanel != null) {
                            bookmarksPanel.showError(
                                "Highlight removed, but the page could not "
                                + "refresh. Reopen the book to retry.");
                        }
                    }

                    @Override
                    public void onRecolor(
                        OctavoAnnotationStore.Highlight highlight,
                        OctavoAnnotationStore.HighlightColor color) {
                        OctavoAnnotationStore.MutationResult result =
                            annotationStore.updateHighlightColor(
                                highlight.recordId, color);
                        if (!result.succeeded()) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    annotationMutationFailure(result));
                            }
                            return;
                        }
                        boolean projected = surfaceView != null
                            && surfaceView.replaceHighlights(
                                annotationStore.highlights(activeBook.key),
                                false,
                                "Highlight changed to " + color.label + ".");
                        refreshBookmarksPanel();
                        if (!projected && bookmarksPanel != null) {
                            bookmarksPanel.showError(
                                "Highlight color saved, but the page could not "
                                + "refresh. Reopen the book to retry.");
                        }
                    }

                    @Override
                    public void onNavigate(
                        OctavoAnnotationStore.Note note) {
                        if (surfaceView == null) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    "The reader is unavailable.");
                            }
                            return;
                        }
                        int result = surfaceView.requestAnnotationNavigation(
                            note.spineIndex, note.byteStart);
                        if (result == OctavoNative.NAVIGATION_ACCEPTED) {
                            bookmarkNavigationPending = true;
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showNavigationPending();
                            }
                        } else if (result == OctavoNative
                                       .NAVIGATION_ALREADY_PRESENTED) {
                            closeBookmarksPanel();
                        }
                        updateReaderNavigationAvailability(surfaceView);
                    }

                    @Override
                    public void onRemove(
                        OctavoAnnotationStore.Note note) {
                        OctavoAnnotationStore.MutationResult result =
                            annotationStore.removeNote(
                                note.recordId, note.revisionToken);
                        if (result.succeeded()) {
                            boolean projected = surfaceView != null
                                && surfaceView.replaceNoteMarkers(
                                    annotationStore.notes(activeBook.key),
                                    false,
                                    "Note removed.");
                            refreshBookmarksPanel();
                            if (!projected && bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    "Note removed, but its marker could not "
                                    + "refresh. Reopen the book to retry.");
                            }
                        } else if (bookmarksPanel != null) {
                            bookmarksPanel.showError(
                                annotationMutationFailure(result));
                        }
                    }

                    @Override
                    public void onEdit(OctavoAnnotationStore.Note note,
                                       String body) {
                        beginNoteEditor(note, body);
                    }

                    @Override
                    public boolean onDraftChanged(
                        OctavoNoteDraftStore.Draft draft) {
                        return noteDraftStore != null
                            && noteDraftStore.save(draft);
                    }

                    @Override
                    public void onSaveDraft(
                        OctavoNoteDraftStore.Draft draft) {
                        OctavoAnnotationStore.MutationResult result =
                            annotationStore.saveNote(
                                draft.recordId,
                                draft.expectedRevisionToken,
                                draft.bookDigest,
                                draft.spineIndex,
                                draft.byteStart,
                                draft.byteEnd,
                                draft.attachedHighlightId,
                                draft.excerpt,
                                draft.body);
                        if (!result.succeeded()) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    annotationMutationFailure(result));
                            }
                            return;
                        }
                        boolean draftCleared = noteDraftStore.clear();
                        boolean projected = surfaceView != null
                            && surfaceView.replaceNoteMarkers(
                                annotationStore.notes(activeBook.key),
                                true,
                                "Note saved.");
                        boolean selectionCleared = projected;
                        if (!projected) {
                            selectionCleared = surfaceView == null
                                || surfaceView.clearSelectionAfterDurableNote();
                        }
                        noteSelectionRetained = surfaceView != null;
                        refreshBookmarksPanel();
                        if (bookmarksPanel != null) {
                            bookmarksPanel.finishNoteEditor(draftCleared
                                ? projected
                                    ? "Note saved."
                                    : selectionCleared
                                        ? "Note saved, but its marker could not be displayed; reopen the book to retry."
                                        : "Note saved, but its marker could not be displayed and the text selection could not be cleared; retry by closing Annotations."
                                : "Note saved, but its recovered draft could not be cleared; retry is safe.");
                        }
                    }

                    @Override
                    public void onCancelDraft(
                        OctavoNoteDraftStore.Draft draft) {
                        if (!noteDraftStore.clear()) {
                            if (bookmarksPanel != null) {
                                bookmarksPanel.showError(
                                    "The recovered draft could not be discarded; it remains available");
                            }
                            return;
                        }
                        boolean selectionCleared = surfaceView == null
                            || surfaceView.clearSelectionAfterDurableNote();
                        noteSelectionRetained = !selectionCleared;
                        if (bookmarksPanel != null) {
                            bookmarksPanel.finishNoteEditor(
                                selectionCleared
                                    ? "Note editing cancelled."
                                    : "Note editing cancelled, but the text selection could not be cleared; retry by closing Annotations.");
                            bookmarksPanel.announceForAccessibility(
                                "Note editing cancelled");
                        }
                    }
                });
        } catch (IllegalStateException failure) {
            showOpenFailure(
                "Annotation styling is unavailable. Reopen the reader to retry.");
            return;
        }
        panel.updateAnnotations(
            annotationStore.bookmarks(activeBook.key),
            annotationStore.highlights(activeBook.key),
            annotationStore.notes(activeBook.key));
        OctavoNoteDraftStore.Draft recovered = noteDraftStore == null
            ? null : noteDraftStore.current();
        if (recovered != null
            && activeBook.key.equals(recovered.bookDigest)) {
            boolean conflicted = false;
            for (OctavoAnnotationStore.Note note
                    : annotationStore.notes(activeBook.key)) {
                if (note.recordId.equals(recovered.recordId)) {
                    conflicted = note.conflicted;
                    break;
                }
            }
            panel.showNoteEditor(recovered, true, conflicted);
        }
        overlay.setBackgroundColor(panel.overlayColor());
        panel.setClickable(true);
        panel.setOnClickListener(view -> {
        });
        FrameLayout.LayoutParams panelLayout =
            new FrameLayout.LayoutParams(
                appearancePanelWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        overlay.addView(panel, panelLayout);
        resolveSideSheetLayoutDirection(panel, readerRoot);
        overlay.addOnLayoutChangeListener(
            (view, left, top, right, bottom,
             oldLeft, oldTop, oldRight, oldBottom) ->
                updateSideSheetWidth(panel, overlay));
        readerRoot.addView(overlay, matchParentLayout());

        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerTopChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerBottomChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        bookmarksOverlay = overlay;
        bookmarksPanel = panel;

        int duration = sideSheetMotionDuration(tokens);
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            panel.setTranslationX(navigationPanelStartTranslationX(
                panel, dp(24)));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            panel.animate().translationX(0.0f)
                .setDuration(duration).start();
        }
        panel.post(() -> {
            if (bookmarksPanel != panel) {
                return;
            }
            View initialFocus = panel.preferredInitialFocus();
            initialFocus.requestFocus();
            initialFocus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            panel.announceForAccessibility("Annotations opened");
        });
    }

    private void openNavigationPanel() {
        if (readerRoot == null || surfaceView == null
            || navigationPanel != null
            || readingPositionPrompt != null
            || appearanceSyncPrompt != null) {
            return;
        }
        if (appearancePanel != null) {
            closeAppearancePanel(false);
        }
        if (searchPanel != null) {
            closeSearchPanel(false);
        }
        if (bookmarksPanel != null) {
            closeBookmarksPanel(false);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setId(R.id.octavo_navigation_overlay);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(4));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setOnClickListener(view -> closeNavigationPanel());

        OctavoProgressDisplay presented =
            surfaceView.presentedProgressDisplay();
        if (presented == null) {
            presented = progressDisplay;
        }
        OctavoNavigationPanel panel;
        try {
            panel = new OctavoNavigationPanel(
                this,
                appearance,
                presented,
                new OctavoNavigationPanel.Listener() {
                @Override
                public void onDismiss() {
                    closeNavigationPanel();
                }

                @Override
                public void onContentsJump(int navIndex) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestContentsNavigation(navIndex),
                        "Opening the selected section.");
                }

                @Override
                public void onChapter(int oneBased) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestChapterNavigation(oneBased),
                        "Opening chapter " + oneBased + ".");
                }

                @Override
                public void onLocation(long location) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestLocationNavigation(location),
                        "Opening location " + location + ".");
                }

                @Override
                public void onPage(long oneBased) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestPageNavigation(oneBased),
                        "Opening page " + oneBased + ".");
                }

                @Override
                public void onPercentage(int percentage) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestPercentageNavigation(
                                percentage),
                        "Opening " + percentage + " percent.");
                }

                @Override
                public void onHistory(boolean forward) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestHistoryNavigation(forward),
                        forward
                            ? "Moving forward."
                            : "Returning to the previous reading position.");
                }

                @Override
                public void onProgressDisplayRequested(
                    OctavoProgressDisplay requested) {
                    requestNavigation(
                        surfaceView == null
                            ? OctavoNative.NAVIGATION_UNAVAILABLE
                            : surfaceView.requestProgressDisplay(requested),
                        "Updating the reader progress display.");
                }
                });
        } catch (IllegalStateException failure) {
            showOpenFailure(navigationThemeFailureMessage());
            return;
        }
        panel.updateSnapshot(surfaceView.navigationSnapshot());
        overlay.setBackgroundColor(panel.overlayColor());
        panel.setClickable(true);
        panel.setOnClickListener(view -> {
        });

        FrameLayout.LayoutParams panelLayout =
            new FrameLayout.LayoutParams(
                appearancePanelWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        overlay.addView(panel, panelLayout);
        // The inherited child direction is not guaranteed to be resolved
        // before its first layout. Copy the already attached reader root so
        // the entrance motion begins at the logical end in RTL as well.
        resolveSideSheetLayoutDirection(panel, readerRoot);
        overlay.addOnLayoutChangeListener(
            (view, left, top, right, bottom,
             oldLeft, oldTop, oldRight, oldBottom) ->
                updateSideSheetWidth(panel, overlay));
        readerRoot.addView(overlay, matchParentLayout());

        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerTopChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerBottomChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        navigationOverlay = overlay;
        navigationPanel = panel;
        scheduleNavigationSnapshotRefresh();

        int duration = tokens.standardMotionMs(appearance);
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            panel.setTranslationX(navigationPanelStartTranslationX(
                panel,
                dp(24)));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            panel.animate().translationX(0.0f)
                .setDuration(duration).start();
        }
        panel.post(() -> {
            if (navigationPanel != panel) {
                return;
            }
            View initialFocus = panel.preferredInitialFocus();
            initialFocus.requestFocus();
            initialFocus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            panel.announceForAccessibility("Reader navigation opened");
        });
    }

    private void requestNavigation(int result, String acceptedStatus) {
        if (navigationPanel != null && surfaceView != null
            && navigationRequestIsPending(result)) {
            navigationPanel.updateSnapshotWithStatus(
                surfaceView.navigationSnapshot(),
                acceptedStatus);
        } else if (navigationPanel != null && surfaceView != null
                   && result == OctavoNative.NAVIGATION_ALREADY_PRESENTED) {
            navigationPanel.updateAlreadyPresentedSnapshot(
                surfaceView.navigationSnapshot());
        }
        updateReaderNavigationAvailability(surfaceView);
    }

    private void openSearchPanel() {
        if (readerRoot == null || surfaceView == null
            || searchPanel != null
            || readingPositionPrompt != null
            || appearanceSyncPrompt != null) {
            return;
        }
        if (appearancePanel != null) {
            closeAppearancePanel(false);
        }
        if (navigationPanel != null) {
            closeNavigationPanel(false);
        }
        if (bookmarksPanel != null) {
            closeBookmarksPanel(false);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setId(R.id.octavo_search_overlay);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(4));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setOnClickListener(view -> closeSearchPanel());

        OctavoSearchPanel panel;
        try {
            panel = new OctavoSearchPanel(
                this,
                appearance,
                new OctavoSearchPanel.Listener() {
                    @Override
                    public void onDismiss() {
                        closeSearchPanel();
                    }

                    @Override
                    public void onSubmit(String query) {
                        OctavoSearchPanel target = searchPanel;
                        if (target == null) {
                            return;
                        }
                        android.view.inputmethod.InputMethodManager keyboard =
                            (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                        if (keyboard != null) {
                            keyboard.hideSoftInputFromWindow(
                                target.getWindowToken(), 0);
                        }
                        target.postOnAnimation(() ->
                            submitSearchWhenReaderReady(
                                target, query, 0));
                    }

                    @Override
                    public void onClear() {
                        requestSearch(
                            surfaceView == null
                                ? OctavoNative.NAVIGATION_UNAVAILABLE
                                : surfaceView.clearSearch(),
                            "Clearing search results.");
                    }

                    @Override
                    public void onStep(boolean forward) {
                        requestSearch(
                            surfaceView == null
                                ? OctavoNative.NAVIGATION_UNAVAILABLE
                                : surfaceView.moveSearchResult(forward),
                            forward
                                ? "Opening the next result."
                                : "Opening the previous result.");
                    }

                    @Override
                    public void onActivate(int resultIndex) {
                        requestSearch(
                            surfaceView == null
                                ? OctavoNative.NAVIGATION_UNAVAILABLE
                                : surfaceView.requestSearchResult(resultIndex),
                            "Opening search result " + (resultIndex + 1)
                                + ".");
                    }
                });
        } catch (IllegalStateException failure) {
            showOpenFailure(
                "Find in book styling is unavailable. Reopen the reader to retry.");
            return;
        }
        panel.updateSnapshot(surfaceView.searchSnapshot());
        overlay.setBackgroundColor(panel.overlayColor());
        panel.setClickable(true);
        panel.setOnClickListener(view -> {
        });
        FrameLayout.LayoutParams panelLayout =
            new FrameLayout.LayoutParams(
                appearancePanelWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        overlay.addView(panel, panelLayout);
        resolveSideSheetLayoutDirection(panel, readerRoot);
        overlay.addOnLayoutChangeListener(
            (view, left, top, right, bottom,
             oldLeft, oldTop, oldRight, oldBottom) ->
                updateSideSheetWidth(panel, overlay));
        readerRoot.addView(overlay, matchParentLayout());

        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerTopChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerBottomChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        searchOverlay = overlay;
        searchPanel = panel;
        scheduleSearchSnapshotRefresh();

        int duration = tokens.standardMotionMs(appearance);
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            panel.setTranslationX(navigationPanelStartTranslationX(
                panel, dp(24)));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            panel.animate().translationX(0.0f)
                .setDuration(duration).start();
        }
        panel.post(() -> {
            if (searchPanel != panel) {
                return;
            }
            View initialFocus = panel.preferredInitialFocus();
            initialFocus.requestFocus();
            initialFocus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            android.view.inputmethod.InputMethodManager keyboard =
                (android.view.inputmethod.InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(
                    initialFocus,
                    android.view.inputmethod.InputMethodManager
                        .SHOW_IMPLICIT);
            }
            panel.announceForAccessibility("Find in book opened");
        });
    }

    private void requestSearch(int result, String acceptedStatus) {
        if (searchPanel != null && surfaceView != null
            && navigationRequestIsPending(result)) {
            searchPanel.showAcceptedNavigation(acceptedStatus);
            searchPanel.updateSnapshot(surfaceView.searchSnapshot());
        } else if (searchPanel != null && surfaceView != null
                   && result == OctavoNative.NAVIGATION_ALREADY_PRESENTED) {
            searchPanel.updateSnapshot(surfaceView.searchSnapshot());
        }
        updateReaderNavigationAvailability(surfaceView);
        scheduleSearchSnapshotRefresh();
    }

    private void submitSearchWhenReaderReady(OctavoSearchPanel target,
                                             String query,
                                             int busyAttempts) {
        if (searchPanel != target || surfaceView == null) {
            return;
        }
        int result = surfaceView.tryCommitSearch(query);
        if (result == OctavoNative.NAVIGATION_BUSY) {
            if (busyAttempts >= SEARCH_BUSY_RETRY_LIMIT) {
                target.showError(
                    "The reader is still updating. Try the search again.");
                return;
            }
            target.showAcceptedNavigation(
                "Waiting for the reader to finish updating.");
            target.postDelayed(
                () -> submitSearchWhenReaderReady(
                    target, query, busyAttempts + 1),
                SEARCH_BUSY_RETRY_DELAY_MILLIS);
            return;
        }
        requestSearch(result, "Searching this book.");
    }

    private void openAppearancePanel() {
        if (readerRoot == null || surfaceView == null
            || appearancePanel != null
            || readingPositionPrompt != null
            || appearanceSyncPrompt != null) {
            return;
        }
        if (navigationPanel != null) {
            closeNavigationPanel(false);
        }
        if (searchPanel != null) {
            closeSearchPanel(false);
        }
        if (bookmarksPanel != null) {
            closeBookmarksPanel(false);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setId(R.id.octavo_appearance_overlay);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(4));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setBackgroundColor(tokens.overlay);
        overlay.setOnClickListener(view -> closeAppearancePanel());

        OctavoAppearancePanel panel = new OctavoAppearancePanel(
            this,
            appearance,
            new OctavoAppearancePanel.Listener() {
                @Override
                public void onAppearanceRequested(
                    OctavoAppearance requested) {
                    requestReaderAppearance(requested);
                }

                @Override
                public void onDismiss() {
                    closeAppearancePanel();
                }
            });
        panel.setId(R.id.octavo_appearance_panel);
        panel.setClickable(true);
        panel.setOnClickListener(view -> {
        });

        FrameLayout.LayoutParams panelLayout =
            new FrameLayout.LayoutParams(
                appearancePanelWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        overlay.addView(panel, panelLayout);
        overlay.addOnLayoutChangeListener(
            (view, left, top, right, bottom,
             oldLeft, oldTop, oldRight, oldBottom) ->
                updateSideSheetWidth(panel, overlay));
        readerRoot.addView(overlay, matchParentLayout());

        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerTopChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerBottomChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        appearanceOverlay = overlay;
        appearancePanel = panel;

        int duration = tokens.standardMotionMs(appearance);
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            panel.setTranslationX(dp(24));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            panel.animate().translationX(0.0f)
                .setDuration(duration).start();
        }
        panel.post(() -> {
            if (appearancePanel != panel) {
                return;
            }
            panel.requestFocus();
            panel.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            panel.announceForAccessibility(
                "Reading appearance opened");
        });
    }

    private void closeAppearancePanel() {
        closeAppearancePanel(true);
    }

    private void closeAppearancePanel(boolean restoreFocus) {
        if (appearanceOverlay == null) {
            return;
        }
        View focusReturn = readerSettings;
        appearanceOverlay.animate().cancel();
        if (appearancePanel != null) {
            appearancePanel.animate().cancel();
        }
        if (appearanceOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)appearanceOverlay.getParent())
                .removeView(appearanceOverlay);
        }
        appearanceOverlay = null;
        appearancePanel = null;
        if (failureBanner != null
            && failureBanner.getParent() instanceof ViewGroup) {
            ((ViewGroup)failureBanner.getParent())
                .removeView(failureBanner);
        }
        failureBanner = null;
        if (surfaceView != null) {
            surfaceView.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (restoreFocus) {
                surfaceView.announceForAccessibility(
                    "Reading appearance closed");
            }
        }
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (restoreFocus) {
            considerAppearanceSyncCandidate();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && focusReturn != null
            && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (appearancePanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || !focusReturn.isShown()) {
                    return;
                }
                focusReturn.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
                focusReturn.announceForAccessibility(
                    "Reader appearance closed");
            });
        }
    }

    private void closeNavigationPanel() {
        closeNavigationPanel(true);
    }

    private void closeNavigationPanel(boolean restoreFocus) {
        if (navigationOverlay == null) {
            return;
        }
        View focusReturn = readerProgress != null
            && readerProgress.isShown() && readerProgress.isEnabled()
                ? readerProgress : surfaceView;
        cancelNavigationSnapshotRefresh();
        navigationOverlay.animate().cancel();
        if (navigationPanel != null) {
            navigationPanel.animate().cancel();
        }
        if (navigationOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)navigationOverlay.getParent())
                .removeView(navigationOverlay);
        }
        navigationOverlay = null;
        navigationPanel = null;
        if (surfaceView != null) {
            surfaceView.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (restoreFocus) {
            considerAppearanceSyncCandidate();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (navigationPanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || !focusReturn.isShown()) {
                    return;
                }
                focusReturn.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
                focusReturn.announceForAccessibility(
                    "Reader navigation closed");
            });
        }
    }

    private void closeSearchPanel() {
        closeSearchPanel(true);
    }

    private void closeSearchPanel(boolean restoreFocus) {
        if (searchOverlay == null) {
            return;
        }
        View focusReturn = readerSearch != null
            && readerSearch.isShown() && readerSearch.isEnabled()
                ? readerSearch : surfaceView;
        cancelSearchSnapshotRefresh();
        searchOverlay.animate().cancel();
        if (searchPanel != null) {
            searchPanel.animate().cancel();
        }
        android.view.inputmethod.InputMethodManager keyboard =
            (android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && searchPanel != null) {
            keyboard.hideSoftInputFromWindow(
                searchPanel.getWindowToken(), 0);
        }
        if (searchOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)searchOverlay.getParent())
                .removeView(searchOverlay);
        }
        searchOverlay = null;
        searchPanel = null;
        if (bookmarksOverlay != null
            && bookmarksOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)bookmarksOverlay.getParent())
                .removeView(bookmarksOverlay);
        }
        bookmarksOverlay = null;
        bookmarksPanel = null;
        bookmarkNavigationPending = false;
        if (surfaceView != null) {
            surfaceView.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (restoreFocus) {
            considerAppearanceSyncCandidate();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (searchPanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || !focusReturn.isShown()) {
                    return;
                }
                if (!focusReturn.hasFocus()) {
                    focusReturn.requestFocusFromTouch();
                }
                focusReturn.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
                focusReturn.announceForAccessibility(
                    "Find in book closed");
            });
        }
    }

    private void closeBookmarksPanel() {
        closeBookmarksPanel(true);
    }

    private void closeBookmarksPanel(boolean restoreFocus) {
        if (bookmarksOverlay == null) {
            return;
        }
        View focusReturn = readerBookmarks != null
            && readerBookmarks.isShown() && readerBookmarks.isEnabled()
                ? readerBookmarks : surfaceView;
        if (noteSelectionRetained && surfaceView != null) {
            surfaceView.clearSelectionAfterDurableNote();
            noteSelectionRetained = false;
        }
        bookmarksOverlay.animate().cancel();
        if (bookmarksPanel != null) {
            bookmarksPanel.animate().cancel();
        }
        if (bookmarksOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)bookmarksOverlay.getParent())
                .removeView(bookmarksOverlay);
        }
        bookmarksOverlay = null;
        bookmarksPanel = null;
        if (surfaceView != null) {
            surfaceView.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (restoreFocus) {
            considerAppearanceSyncCandidate();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (bookmarksPanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || !focusReturn.isShown()) {
                    return;
                }
                focusReturn.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
                focusReturn.announceForAccessibility("Annotations closed");
            });
        }
    }

    private void showLibrary() {
        releaseReader();
        chromeVisible = false;
        activeBook = null;
        applyWindowAppearance();
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);

        LinearLayout root = createInsetRoot();
        root.setId(R.id.octavo_library);
        root.setBackgroundColor(tokens.librarySurface);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(R.string.library_title);
        heading.setTextSize(24);
        heading.setTextColor(tokens.libraryText);
        header.addView(heading, weightedLayout());

        Button addButton = new Button(this);
        addButton.setId(R.id.octavo_library_add);
        addButton.setText(R.string.add_epub);
        addButton.setContentDescription(getString(R.string.add_epub));
        addButton.setAllCaps(false);
        addButton.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        addButton.setTextColor(tokens.onAccent);
        addButton.setBackgroundTintList(
            ColorStateList.valueOf(tokens.accent));
        addButton.setOnClickListener(view ->
            startActivityForResult(createOpenDocumentIntent(),
                                   REQUEST_ADD_EPUB));
        header.addView(addButton, wrapLayout());
        root.addView(header, matchParentWidthLayout());

        TextView summary = new TextView(this);
        int importedCount = Math.max(0, libraryStore.bookCount() - 1);
        summary.setText(String.format(
            Locale.ROOT,
            importedCount == 1
                ? "%d imported book plus the built-in sample"
                : "%d imported books plus the built-in sample",
            importedCount));
        summary.setPadding(0, dp(4), 0, dp(12));
        summary.setTextColor(tokens.textSecondary);
        root.addView(summary, matchParentWidthLayout());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.octavo_library_list);
        list.setOrientation(LinearLayout.VERTICAL);
        for (OctavoLibraryStore.Book book : libraryStore.books()) {
            list.addView(createBookRow(book), matchParentWidthLayout());
        }
        scroll.addView(list, matchParentWidthLayout());
        root.addView(scroll, surfaceLayout());

        libraryRoot = root;
        FrameLayout windowRoot = createSystemBarFrame(
            root,
            dp(OctavoDesignTokens.SPACE_LG_DP),
            tokens.librarySurface);
        setContentView(windowRoot, matchParentLayout());
        windowRoot.requestApplyInsets();
    }

    private View createBookRow(OctavoLibraryStore.Book book) {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView title = new TextView(this);
        title.setText(book.title);
        title.setTextSize(19);
        title.setTextColor(tokens.libraryText);
        row.addView(title, matchParentWidthLayout());

        TextView status = new TextView(this);
        String progress = book.hasPosition
            ? "Resume available"
            : "Not started";
        status.setText(book.imported
                           ? progress
                           : "Built-in sample | " + progress);
        status.setTextColor(tokens.textSecondary);
        status.setPadding(0, dp(2), 0, dp(6));
        row.addView(status, matchParentWidthLayout());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button open = new Button(this);
        open.setText(book.hasPosition ? R.string.resume : R.string.open);
        open.setAllCaps(false);
        open.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        open.setTextColor(tokens.chromeText);
        open.setBackgroundTintList(
            ColorStateList.valueOf(tokens.buttonSurface));
        open.setContentDescription("Open " + book.title);
        open.setOnClickListener(view -> {
            if (!showReader(book, true)) {
                showOpenFailure("Unable to open the library book");
            }
        });
        actions.addView(open, wrapLayout());

        if (book.imported) {
            Button remove = new Button(this);
            remove.setText(R.string.remove);
            remove.setAllCaps(false);
            remove.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            remove.setTextColor(tokens.error);
            remove.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            remove.setContentDescription("Remove " + book.title);
            remove.setOnClickListener(view -> {
                if (libraryStore.removeBook(book.key)) {
                    showLibrary();
                } else {
                    showOpenFailure("Unable to remove the library book");
                }
            });
            actions.addView(remove, wrapLayout());
        }
        row.addView(actions, matchParentWidthLayout());

        View divider = new View(this);
        divider.setBackgroundColor(tokens.divider);
        row.addView(divider,
                    new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(1))));
        return row;
    }

    private LinearLayout createInsetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        return root;
    }

    private int appearancePanelWidth() {
        int available = getResources().getDisplayMetrics().widthPixels;
        if (readerRoot != null) {
            if (readerRoot.getWidth() > 0) {
                available = readerRoot.getWidth();
            }
            available -= readerRoot.getPaddingLeft()
                + readerRoot.getPaddingRight();
        }
        return boundedSideSheetWidth(Math.max(available, 1),
                                     dp(24),
                                     dp(560));
    }

    private void updateAppearancePanelWidth() {
        updateSideSheetWidth(appearancePanel, appearanceOverlay);
    }

    private void updateNavigationPanelWidth() {
        updateSideSheetWidth(navigationPanel, navigationOverlay);
    }

    private void updateSearchPanelWidth() {
        updateSideSheetWidth(searchPanel, searchOverlay);
    }

    private void updateBookmarksPanelWidth() {
        updateSideSheetWidth(bookmarksPanel, bookmarksOverlay);
    }

    private void updateSideSheetWidth(View panel, FrameLayout overlay) {
        if (panel == null) {
            return;
        }
        int width = overlay != null && overlay.getWidth() > 0
            ? boundedSideSheetWidth(overlay.getWidth(), dp(24), dp(560))
            : appearancePanelWidth();
        ViewGroup.LayoutParams current = panel.getLayoutParams();
        if (current != null && current.width != width) {
            current.width = width;
            panel.setLayoutParams(current);
        }
    }

    private FrameLayout createSystemBarFrame(View content,
                                             int horizontalPadding,
                                             int contentBackground) {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(contentBackground);
        content.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        frame.addView(content, matchParentLayout());

        View status = new View(this);
        status.setId(R.id.octavo_status_bar_scrim);
        status.setBackgroundColor(tokens.statusBar);
        status.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(status,
                      new FrameLayout.LayoutParams(
                          ViewGroup.LayoutParams.MATCH_PARENT, 0,
                          Gravity.TOP));

        View navigation = new View(this);
        navigation.setId(R.id.octavo_navigation_bar_scrim);
        navigation.setBackgroundColor(tokens.navigationBar);
        navigation.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(navigation,
                      new FrameLayout.LayoutParams(
                          ViewGroup.LayoutParams.MATCH_PARENT, 0,
                          Gravity.BOTTOM));

        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            content.setPadding(left + horizontalPadding,
                               top,
                               right + horizontalPadding,
                               bottom);
            FrameLayout.LayoutParams statusLayout =
                (FrameLayout.LayoutParams)status.getLayoutParams();
            statusLayout.height = top;
            status.setLayoutParams(statusLayout);
            FrameLayout.LayoutParams navigationLayout =
                (FrameLayout.LayoutParams)navigation.getLayoutParams();
            navigationLayout.height = bottom;
            navigation.setLayoutParams(navigationLayout);
            return insets.replaceSystemWindowInsets(0, 0, 0, 0);
        });
        systemBarRoot = frame;
        statusBarScrim = status;
        navigationBarScrim = navigation;
        return frame;
    }

    private void releaseReader() {
        flushProgressPersistence();
        closeAppearanceSyncPrompt(false);
        closeReadingPositionPrompt(false);
        readingPositionChoiceRetry = null;
        if (failureBanner != null
            && failureBanner.getParent() instanceof ViewGroup) {
            ((ViewGroup)failureBanner.getParent())
                .removeView(failureBanner);
        }
        failureBanner = null;
        failureBannerAnnouncementDeferred = false;
        readingPositionReviewPending = false;
        readingPositionReviewInitialized = false;
        appearanceSyncReviewPending = false;
        appearanceSyncReviewInitialized = false;
        OctavoAppearanceSyncStore.Pending durableAppearancePending =
            appearanceSyncStore == null ? null
                : appearanceSyncStore.pending();
        if (durableAppearancePending == null) {
            appearanceSyncPendingLoaded = false;
            appearanceSyncRollbackRequested = false;
            appearanceSyncRetryDescriptor = null;
        } else {
            appearanceSyncPending = durableAppearancePending;
            appearanceSyncRetryDescriptor =
                AppearanceSyncRetryDescriptor.pending(
                    appearanceSyncRollbackRequested
                        ? APPEARANCE_RETRY_ROLLBACK
                        : APPEARANCE_RETRY_FORWARD,
                    durableAppearancePending);
        }
        appearanceSyncAwaitingExplicitRetry = false;
        appearanceSyncUnstagedRollbackRequested = false;
        appearanceSyncUnstagedOrigin = null;
        appearanceSyncCandidate = null;
        if (durableAppearancePending == null) {
            appearanceSyncPending = null;
        }
        appearanceSyncRetry = null;
        appearanceReceiptSurface = null;
        latestAppearanceReceipt = null;
        consumedAppearanceReceiptSurface = null;
        consumedAppearanceReceiptProfile = null;
        consumedAppearanceReceiptGeneration = -1;
        consumedAppearanceReceiptFrame = -1;
        hasPresentedReadingPosition = false;
        presentedReadingSpineIndex = 0;
        presentedReadingByteOffset = 0;
        presentedReadingPageSpineIndex = 0;
        presentedReadingPageFirstByte = 0;
        presentedReadingPageOnePastLastByte = 0;
        presentedReadingFrameCount = 0;
        cancelNavigationSnapshotRefresh();
        cancelSearchSnapshotRefresh();
        noteSelectionRetained = false;
        bookmarksOverlay = null;
        bookmarksPanel = null;
        if (navigationOverlay != null
            && navigationOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)navigationOverlay.getParent())
                .removeView(navigationOverlay);
        }
        navigationOverlay = null;
        navigationPanel = null;
        if (searchOverlay != null
            && searchOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)searchOverlay.getParent())
                .removeView(searchOverlay);
        }
        searchOverlay = null;
        searchPanel = null;
        cancelAppearanceTransition();
        cancelReaderEntryCover();
        if (surfaceView != null) {
            surfaceView.release();
            surfaceView = null;
        }
        if (appearanceOverlay != null
            && appearanceOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)appearanceOverlay.getParent())
                .removeView(appearanceOverlay);
        }
        appearanceOverlay = null;
        appearancePanel = null;
        readerRoot = null;
        readerTopChrome = null;
        readerBottomChrome = null;
        readerLibrary = null;
        readerSearch = null;
        readerSettings = null;
        readerBookmarkToggle = null;
        readerReturn = null;
        readerProgress = null;
        readerBookmarks = null;
        systemBarRoot = null;
        statusBarScrim = null;
        navigationBarScrim = null;
    }

    private void showOpenFailure(String message) {
        lastOpenError = message;
        if (readerRoot == null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        if (failureBanner != null
            && failureBanner.getParent() instanceof ViewGroup) {
            ((ViewGroup)failureBanner.getParent())
                .removeView(failureBanner);
        }
        failureBannerAnnouncementDeferred = false;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        TextView banner = new TextView(this);
        banner.setId(R.id.octavo_reader_failure);
        banner.setText(message);
        banner.setContentDescription(message);
        banner.setTextSize(16);
        banner.setTextColor(tokens.error);
        banner.setGravity(Gravity.CENTER);
        banner.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setBackgroundColor(tokens.dialogSurface);
        banner.setElevation(dp(6));
        FrameLayout.LayoutParams layout =
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        layout.leftMargin = dp(16);
        layout.rightMargin = dp(16);
        layout.bottomMargin = dp(72);
        readerRoot.addView(banner, layout);
        failureBanner = banner;
        if (readingPositionPrompt != null
            || appearanceSyncPrompt != null) {
            failureBannerAnnouncementDeferred = true;
            obscureFailureBannerForModalPrompt();
        } else {
            banner.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            banner.announceForAccessibility(message);
        }
        banner.postDelayed(() -> {
            if (failureBanner == banner
                && banner.getParent() instanceof ViewGroup) {
                ((ViewGroup)banner.getParent()).removeView(banner);
                failureBanner = null;
                failureBannerAnnouncementDeferred = false;
            }
        }, 5000);
    }

    private static Intent createOpenDocumentIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/epub+zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[] {
                            "application/epub+zip",
                            "application/octet-stream",
                            "application/zip"
                        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private int dp(int value) {
        return Math.max(value,
                        Math.round(getResources().getDisplayMetrics().density
                                   * value));
    }

    private Button createThemedButton(CharSequence label,
                                      int background,
                                      int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinWidth(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        button.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        button.setTextColor(foreground);
        button.setBackgroundTintList(
            ColorStateList.valueOf(background));
        return button;
    }

    private LinearLayout.LayoutParams chromeButtonLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static ViewGroup.LayoutParams matchParentLayout() {
        return new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams matchParentWidthLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weightedLayout() {
        return new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f);
    }

    private static LinearLayout.LayoutParams surfaceLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f);
    }

    boolean openDocumentForTesting(Uri uri) {
        return openDocument(uri);
    }

    boolean openFixtureForTesting() {
        OctavoLibraryStore.Book fixture = libraryStore.fixtureBook();
        return fixture != null && showReader(fixture, true);
    }

    boolean openBookForTesting(String key) {
        OctavoLibraryStore.Book book = libraryStore.findBook(key);
        return book != null && showReader(book, true);
    }

    boolean removeBookForTesting(String key) {
        boolean removed = libraryStore.removeBook(key);
        if (removed) {
            showLibrary();
        }
        return removed;
    }

    void closeBookForTesting() {
        showLibrary();
    }

    boolean libraryVisibleForTesting() {
        return libraryRoot != null && surfaceView == null;
    }

    String activeBookKeyForTesting() {
        return activeBook == null ? null : activeBook.key;
    }

    Intent openDocumentIntentForTesting() {
        return createOpenDocumentIntent();
    }

    OctavoLibraryStore libraryStoreForTesting() {
        return libraryStore;
    }

    OctavoAppearance appearanceForTesting() {
        return appearance;
    }

    OctavoAppearanceStore appearanceStoreForTesting() {
        return appearanceStore;
    }

    OctavoAppearancePanel appearancePanelForTesting() {
        return appearancePanel;
    }

    void openAppearancePanelForTesting() {
        openAppearancePanel();
    }

    void closeAppearancePanelForTesting() {
        closeAppearancePanel();
    }

    OctavoNavigationPanel navigationPanelForTesting() {
        return navigationPanel;
    }

    void openNavigationPanelForTesting() {
        openNavigationPanel();
    }

    void closeNavigationPanelForTesting() {
        closeNavigationPanel();
    }

    OctavoSearchPanel searchPanelForTesting() {
        return searchPanel;
    }

    OctavoBookmarksPanel bookmarksPanelForTesting() {
        return bookmarksPanel;
    }

    OctavoAnnotationStore annotationStoreForTesting() {
        return annotationStore;
    }

    OctavoNoteDraftStore noteDraftStoreForTesting() {
        return noteDraftStore;
    }

    boolean simulateRemotePositionForTesting(String deviceId,
                                             long sequence,
                                             long spineIndex,
                                             long byteOffset) {
        if (activeBook == null) {
            return false;
        }
        try {
            return mergeSimulatedRemotePositionForTesting(
                OctavoReadingPositionPortable.simulatedRemoteBytes(
                    activeBook.key,
                    deviceId,
                    sequence,
                    spineIndex,
                    byteOffset));
        } catch (IOException | RuntimeException exception) {
            showReadingPositionStoreFailure(() ->
                simulateRemotePositionForTesting(
                    deviceId, sequence, spineIndex, byteOffset));
            return false;
        }
    }

    boolean mergeSimulatedRemotePositionForTesting(byte[] bytes) {
        if (readingPositionStore == null) {
            return false;
        }
        OctavoReadingPositionStore.PortableMergeResult result =
            readingPositionStore.mergeSimulatedRemoteBytes(bytes);
        if (!result.succeeded()) {
            byte[] retryBytes = bytes == null ? null : bytes.clone();
            showReadingPositionStoreFailure(() ->
                mergeSimulatedRemotePositionForTesting(retryBytes));
            return false;
        }
        if (result == OctavoReadingPositionStore.PortableMergeResult.MERGED) {
            boolean promptWasVisible = readingPositionPrompt != null;
            closeReadingPositionPrompt(false);
            if (hasPresentedReadingPosition
                && initializeReadingPositionReview()) {
                considerReadingPositionCandidate();
            }
            if (promptWasVisible && readingPositionPrompt == null) {
                restoreReadingPositionFocusAfterClose();
            }
        }
        return true;
    }

    OctavoReadingPositionPrompt positionPromptForTesting() {
        return readingPositionPrompt;
    }

    OctavoReadingPositionStore readingPositionStoreForTesting() {
        return readingPositionStore;
    }

    OctavoReadingPositionStore.Candidate
        pendingPositionCandidateForTesting() {
        return readingPositionCandidate;
    }

    boolean simulateRemoteAppearanceForTesting(String deviceId,
                                                long sequence,
                                                OctavoAppearance remote) {
        try {
            return mergeSimulatedRemoteAppearanceForTesting(
                OctavoAppearancePortable.simulatedRemoteBytes(
                    deviceId, sequence, remote));
        } catch (IOException | RuntimeException exception) {
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                "The simulated remote reading settings are invalid.",
                this::considerAppearanceSyncCandidate);
            return false;
        }
    }

    boolean mergeSimulatedRemoteAppearanceForTesting(byte[] bytes) {
        if (appearanceSyncStore == null) {
            return false;
        }
        OctavoAppearanceSyncStore.PortableMergeResult result =
            appearanceSyncStore.mergePortableBytes(bytes);
        if (!result.succeeded()) {
            byte[] retryBytes = bytes == null ? null : bytes.clone();
            showAppearanceSyncFailure(
                "Reading settings update needs attention",
                appearanceSyncStore.lastError(),
                () -> mergeSimulatedRemoteAppearanceForTesting(
                    retryBytes));
            return false;
        }
        if (result == OctavoAppearanceSyncStore.PortableMergeResult.MERGED) {
            boolean promptWasVisible = appearanceSyncPrompt != null;
            OctavoSurfaceView.AppearancePresentationReceipt receipt =
                currentAppearanceReceipt();
            if (appearanceSyncStore.pending() == null
                && appearanceSyncCandidate != null
                && (receipt == null
                    || !appearanceSyncCandidateIsCurrent(
                        appearanceSyncCandidate, receipt.profile))) {
                closeAppearanceSyncPrompt(false);
            }
            if (receipt != null
                && appearanceSyncStore.pending() == null) {
                finishAppearanceConvergence(receipt);
            }
            if (promptWasVisible && appearanceSyncPrompt == null) {
                restoreAppearanceSyncFocusAfterClose();
            }
        }
        return true;
    }

    OctavoAppearanceSyncPrompt appearanceSyncPromptForTesting() {
        return appearanceSyncPrompt;
    }

    OctavoAppearanceSyncStore appearanceSyncStoreForTesting() {
        return appearanceSyncStore;
    }

    OctavoAppearanceSyncStore.Candidate
        pendingAppearanceCandidateForTesting() {
        return appearanceSyncCandidate;
    }

    OctavoAppearanceSyncStore.Candidate
        pendingAppearanceSyncCandidateForTesting() {
        return appearanceSyncCandidate;
    }

    OctavoAppearanceSyncStore.Pending
        pendingAppearanceTransactionForTesting() {
        return appearanceSyncStore == null ? null
            : appearanceSyncStore.pending();
    }

    boolean appearanceSyncAwaitingExplicitRetryForTesting() {
        return appearanceSyncAwaitingExplicitRetry;
    }

    boolean appearanceSyncRollbackRequestedForTesting() {
        return appearanceSyncRollbackRequested;
    }

    boolean appearanceSyncStageUncertainForTesting() {
        return appearanceSyncStageUncertain;
    }

    boolean appearanceSyncAbandonAfterReloadForTesting() {
        return appearanceSyncAbandonAfterReload;
    }

    boolean appearanceSyncReviewPendingForTesting() {
        return appearanceSyncReviewPending;
    }

    boolean appearanceSyncReviewInitializedForTesting() {
        return appearanceSyncReviewInitialized;
    }

    int appearanceSyncPromptMotionDurationForTesting() {
        return appearanceSyncPromptMotionDuration();
    }

    void processAppearancePresentationReceiptForTesting() {
        processAppearancePresentationReceipt();
    }

    long[] currentReadingPageForTesting() {
        return !hasPresentedReadingPosition
            ? null
            : new long[] {
                presentedReadingSpineIndex,
                presentedReadingByteOffset,
                presentedReadingPageFirstByte,
                presentedReadingPageOnePastLastByte,
                presentedReadingFrameCount
            };
    }

    int positionPromptMotionDurationForTesting() {
        return sideSheetMotionDuration(
            OctavoDesignTokens.forAppearance(appearance));
    }

    boolean positionAwaitingExplicitRetryForTesting() {
        return readingPositionAwaitingExplicitRetry;
    }

    void showFailureForTesting(String message) {
        showOpenFailure(message);
    }

    void openSearchPanelForTesting() {
        openSearchPanel();
    }

    void closeSearchPanelForTesting() {
        closeSearchPanel();
    }

    void openBookmarksPanelForTesting() {
        openBookmarksPanel();
    }

    void closeBookmarksPanelForTesting() {
        closeBookmarksPanel();
    }

    void toggleCurrentBookmarkForTesting() {
        toggleCurrentBookmark();
    }

    Button readerSearchForTesting() {
        return readerSearch;
    }

    Button readerBookmarkToggleForTesting() {
        return readerBookmarkToggle;
    }

    Button readerBookmarksForTesting() {
        return readerBookmarks;
    }

    Button readerProgressForTesting() {
        return readerProgress;
    }

    Button readerReturnForTesting() {
        return readerReturn;
    }

    OctavoProgressDisplay progressDisplayForTesting() {
        return progressDisplay;
    }

    OctavoProgressStore progressStoreForTesting() {
        return progressStore;
    }

    void flushProgressPersistenceForTesting() {
        flushProgressPersistence();
    }

    void queuePresentedProgressPersistenceForTesting() {
        persistPresentedProgress(progressDisplay);
    }

    void requestAppearanceForTesting(OctavoAppearance requested) {
        requestReaderAppearance(requested);
    }

    void flushAppearancePersistenceForTesting() {
        flushAppearancePersistence();
    }

    void stagePresentedAppearanceForLifecyclePersistenceForTesting() {
        if (appearancePersistencePosted) {
            getWindow().getDecorView().removeCallbacks(persistAppearance);
            appearancePersistencePosted = false;
        }
        pendingAppearancePersistence = appearance;
    }

    boolean setChromeVisibleForTesting(boolean visible) {
        return surfaceView != null
            && surfaceView.setChromeVisible(visible);
    }

    boolean chromeVisibleForTesting() {
        return chromeVisible;
    }

    String lastOpenErrorForTesting() {
        return lastOpenError;
    }
}
