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
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
    private static final String STATE_PROGRESS_REVIEW_PENDING =
        "octavo.port11.progress_review_pending";
    private static final String STATE_PROGRESS_RETRY_ACTION =
        "octavo.port11.progress_retry_action";
    private static final String STATE_PROGRESS_RETRY_DEVICE =
        "octavo.port11.progress_retry_device";
    private static final String STATE_PROGRESS_RETRY_SEQUENCE =
        "octavo.port11.progress_retry_sequence";
    private static final String STATE_PROGRESS_RETRY_EPOCH =
        "octavo.port11.progress_retry_epoch";
    private static final String STATE_PROGRESS_RETRY_ORIGIN_SEQUENCE =
        "octavo.port11.progress_retry_origin_sequence";
    private static final String STATE_PROGRESS_RETRY_LOCAL_SEQUENCE =
        "octavo.port11.progress_retry_local_sequence";
    private static final String STATE_PROGRESS_RETRY_REMOTE_DEVICE =
        "octavo.port11.progress_retry_remote_device";
    private static final String STATE_PROGRESS_RETRY_REMOTE_SEQUENCE =
        "octavo.port11.progress_retry_remote_sequence";
    private static final String STATE_PROGRESS_RETRY_PENDING_KIND =
        "octavo.port11.progress_retry_pending_kind";
    private static final String STATE_PROGRESS_RETRY_ORIGIN_CHOICE =
        "octavo.port11.progress_retry_origin_choice";
    private static final String STATE_PROGRESS_RETRY_TARGET_CHOICE =
        "octavo.port11.progress_retry_target_choice";
    private static final String STATE_PROGRESS_REVIEW_EPOCH_BEFORE_RETRY =
        "octavo.port11.progress_review_epoch_before_retry";
    private static final String STATE_PROGRESS_ROLLBACK_EPOCH_RETRY =
        "octavo.port11.progress_rollback_epoch_retry";
    private static final String STATE_PROGRESS_ABANDON_AFTER_RELOAD =
        "octavo.port11.progress_abandon_after_reload";
    private static final String STATE_LIBRARY_REVIEW_EPOCH_ACTIVE =
        "octavo.port11.library_review_epoch_active";
    private static final String STATE_LIBRARY_CATALOG_REVIEW_DEFERRED =
        "octavo.port11.library_catalog_review_deferred";
    private static final String STATE_LIBRARY_IDENTITY_RECORD_OPENED =
        "octavo.port11.library_identity_record_opened";
    private static final String STATE_LIBRARY_REVIEW_EPOCH_RETRY =
        "octavo.port11.library_review_epoch_retry";
    private static final String STATE_LIBRARY_REVIEW_EPOCH_BEFORE_RETRY =
        "octavo.port11.library_review_epoch_before_retry";
    private static final String STATE_LIBRARY_DISCOVERY_RETRY_KEY =
        "octavo.port11.library_discovery_retry_key";
    private static final String STATE_LIBRARY_FOCUS_BOOK_KEY =
        "octavo.port11.library_focus_book_key";
    private static final String STATE_LIBRARY_FOCUS_REMOVE =
        "octavo.port11.library_focus_remove";
    private static final String STATE_LIBRARY_FOCUS_ACTION =
        "octavo.port11.library_focus_action";
    private static final String STATE_LIBRARY_SUPPRESSED_REVIEW =
        "octavo.port11.library_suppressed_review";
    private static final String STATE_LIBRARY_ATTENTION_DEFERRED =
        "octavo.port11.library_attention_deferred";
    private static final String STATE_LIBRARY_MEMBERSHIP_ATTENTION_DEFERRED =
        "octavo.port11.library_membership_attention_deferred";
    private static final String STATE_LIBRARY_MEMBERSHIP_PENDING_ACTION =
        "octavo.port11.library_membership_pending_action";
    private static final String STATE_LIBRARY_MEMBERSHIP_PENDING_DIGEST =
        "octavo.port11.library_membership_pending_digest";
    private static final String STATE_LIBRARY_MEMBERSHIP_PENDING_BYTES =
        "octavo.port11.library_membership_pending_bytes";
    private static final String
        STATE_LIBRARY_MEMBERSHIP_PENDING_RECORD_FINGERPRINT =
            "octavo.port11.library_membership_pending_record_fingerprint";
    private static final String
        STATE_LIBRARY_MEMBERSHIP_PENDING_SNAPSHOT_FINGERPRINT =
            "octavo.port11.library_membership_pending_snapshot_fingerprint";
    private static final String STATE_LIBRARY_MEMBERSHIP_PENDING_GENERATION =
        "octavo.port11.library_membership_pending_generation";
    private static final String
        STATE_LIBRARY_MEMBERSHIP_PENDING_RECORD_PRESENT =
            "octavo.port11.library_membership_pending_record_present";
    private static final String STATE_LIBRARY_MEMBERSHIP_PENDING_PROJECTION =
        "octavo.port11.library_membership_pending_projection";
    private static final int LIBRARY_IDENTITY_MODE_NONE = 0;
    private static final int LIBRARY_IDENTITY_MODE_OPEN = 1;
    private static final int LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION = 2;
    private static final int LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION = 3;
    private static final int LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION = 4;
    private static final String LIBRARY_FOCUS_ADD =
        "octavo.library.focus.add";
    private static final int LIBRARY_FOCUS_NONE = 0;
    private static final int LIBRARY_FOCUS_ADD_ACTION = 1;
    private static final int LIBRARY_FOCUS_OPEN = 2;
    private static final int LIBRARY_FOCUS_REMOVE_LOCAL = 3;
    private static final int LIBRARY_FOCUS_WITHDRAW = 4;
    private static final int LIBRARY_FOCUS_RESTORE = 5;
    private static final int LIBRARY_FOCUS_MEMBERSHIP_REVIEW = 6;
    private static final int LIBRARY_MEMBERSHIP_ACTION_NONE = 0;
    private static final int LIBRARY_MEMBERSHIP_ACTION_WITHDRAW = 1;
    private static final int LIBRARY_MEMBERSHIP_ACTION_RESTORE = 2;
    private static final int LIBRARY_MEMBERSHIP_ACTION_RESOLVE_MEMBER = 3;
    private static final int LIBRARY_MEMBERSHIP_ACTION_RESOLVE_WITHDRAWN = 4;
    private static final int POSITION_RETRY_MARK_GO = 1;
    private static final int POSITION_RETRY_STAY = 2;
    private static final int POSITION_RETRY_DISMISS = 3;
    private static final int APPEARANCE_RETRY_RELOAD = 1;
    private static final int APPEARANCE_RETRY_USE = 2;
    private static final int APPEARANCE_RETRY_KEEP = 3;
    private static final int APPEARANCE_RETRY_DISMISS = 4;
    private static final int APPEARANCE_RETRY_FORWARD = 5;
    private static final int APPEARANCE_RETRY_ROLLBACK = 6;
    private static final int PROGRESS_RETRY_RELOAD = 1;
    private static final int PROGRESS_RETRY_USE = 2;
    private static final int PROGRESS_RETRY_KEEP = 3;
    private static final int PROGRESS_RETRY_DISMISS = 4;
    private static final int PROGRESS_RETRY_FORWARD = 5;
    private static final int PROGRESS_RETRY_ROLLBACK = 6;
    private static final int PROGRESS_RETRY_LOCAL_STAGE = 7;
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

    /**
     * Saved UI intent only. O1PS remains the authority for candidates and
     * pending transactions; every restored descriptor must match it exactly
     * before it can act.
     */
    private static final class ProgressSyncRetryDescriptor {
        final int action;
        final String deviceId;
        final long sequence;
        final long reviewEpoch;
        final long originSequence;
        final long localSequence;
        final String remoteDeviceId;
        final long remoteSequence;
        final int pendingKind;
        final int originChoiceId;
        final int targetChoiceId;

        private ProgressSyncRetryDescriptor(
            int action, String deviceId, long sequence,
            long reviewEpoch, long originSequence, long localSequence,
            String remoteDeviceId, long remoteSequence, int pendingKind,
            int originChoiceId, int targetChoiceId) {
            this.action = action;
            this.deviceId = deviceId;
            this.sequence = sequence;
            this.reviewEpoch = reviewEpoch;
            this.originSequence = originSequence;
            this.localSequence = localSequence;
            this.remoteDeviceId = remoteDeviceId;
            this.remoteSequence = remoteSequence;
            this.pendingKind = pendingKind;
            this.originChoiceId = originChoiceId;
            this.targetChoiceId = targetChoiceId;
        }

        static ProgressSyncRetryDescriptor reload() {
            return new ProgressSyncRetryDescriptor(
                PROGRESS_RETRY_RELOAD, null, 0, 0, 0,
                0, null, 0, 0, -1, -1);
        }

        static ProgressSyncRetryDescriptor candidate(
            int action, OctavoProgressSyncStore.Candidate candidate) {
            return new ProgressSyncRetryDescriptor(
                action, candidate.deviceId, candidate.sequence,
                candidate.reviewEpoch, candidate.originLocalSequence,
                0, null, 0, 0, -1, -1);
        }

        static ProgressSyncRetryDescriptor pending(
            int action, OctavoProgressSyncStore.Pending pending) {
            int durableAction = pending.direction
                    == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                ? PROGRESS_RETRY_ROLLBACK : PROGRESS_RETRY_FORWARD;
            if (action != durableAction) {
                throw new IllegalArgumentException(
                    "Progress Retry direction is stale");
            }
            return new ProgressSyncRetryDescriptor(
                durableAction, null, 0, 0, pending.originLocalSequence,
                pending.localSequence, pending.remoteDeviceId,
                pending.remoteSequence,
                pending.kind == OctavoProgressSyncStore.PendingKind.LOCAL
                    ? 1 : 2,
                -1, -1);
        }

        static ProgressSyncRetryDescriptor localStage(
            OctavoProgressPortable.Lane origin,
            OctavoProgressDisplay target) {
            if (origin == null || target == null
                || origin.choice.toDisplay() == target) {
                throw new IllegalArgumentException(
                    "Invalid local progress stage Retry");
            }
            return new ProgressSyncRetryDescriptor(
                PROGRESS_RETRY_LOCAL_STAGE, null, 0, 0,
                origin.sequence, 0, null, 0, 0,
                origin.choice.semanticId,
                OctavoProgressPortable.Choice.fromDisplay(
                    target).semanticId);
        }

        boolean candidateAction() {
            return action >= PROGRESS_RETRY_USE
                && action <= PROGRESS_RETRY_DISMISS;
        }

        boolean pendingAction() {
            return action == PROGRESS_RETRY_FORWARD
                || action == PROGRESS_RETRY_ROLLBACK;
        }

        boolean localStageAction() {
            return action == PROGRESS_RETRY_LOCAL_STAGE;
        }

        OctavoProgressDisplay localStageOriginDisplay() {
            return new OctavoProgressPortable.Choice(
                originChoiceId).toDisplay();
        }

        OctavoProgressDisplay localStageTargetDisplay() {
            return new OctavoProgressPortable.Choice(
                targetChoiceId).toDisplay();
        }

        boolean matches(OctavoProgressSyncStore.Candidate candidate) {
            return candidateAction() && candidate != null
                && deviceId.equals(candidate.deviceId)
                && sequence == candidate.sequence
                && reviewEpoch == candidate.reviewEpoch
                && originSequence == candidate.originLocalSequence;
        }

        boolean matches(OctavoProgressSyncStore.Pending pending) {
            return pendingAction() && pending != null
                && action
                   == (pending.direction
                           == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                       ? PROGRESS_RETRY_ROLLBACK
                       : PROGRESS_RETRY_FORWARD)
                && originSequence == pending.originLocalSequence
                && localSequence == pending.localSequence
                && remoteDeviceId.equals(pending.remoteDeviceId)
                && remoteSequence == pending.remoteSequence
                && pendingKind
                   == (pending.kind
                       == OctavoProgressSyncStore.PendingKind.LOCAL
                           ? 1 : 2);
        }

        boolean matchesPendingIdentity(
            OctavoProgressSyncStore.Pending pending) {
            return pendingAction() && pending != null
                && originSequence == pending.originLocalSequence
                && localSequence == pending.localSequence
                && remoteDeviceId.equals(pending.remoteDeviceId)
                && remoteSequence == pending.remoteSequence
                && pendingKind
                   == (pending.kind
                       == OctavoProgressSyncStore.PendingKind.LOCAL
                           ? 1 : 2);
        }

        void save(Bundle state) {
            state.putInt(STATE_PROGRESS_RETRY_ACTION, action);
            state.putString(STATE_PROGRESS_RETRY_DEVICE, deviceId);
            state.putLong(STATE_PROGRESS_RETRY_SEQUENCE, sequence);
            state.putLong(STATE_PROGRESS_RETRY_EPOCH, reviewEpoch);
            state.putLong(STATE_PROGRESS_RETRY_ORIGIN_SEQUENCE,
                          originSequence);
            state.putLong(STATE_PROGRESS_RETRY_LOCAL_SEQUENCE,
                          localSequence);
            state.putString(STATE_PROGRESS_RETRY_REMOTE_DEVICE,
                            remoteDeviceId);
            state.putLong(STATE_PROGRESS_RETRY_REMOTE_SEQUENCE,
                          remoteSequence);
            state.putInt(STATE_PROGRESS_RETRY_PENDING_KIND, pendingKind);
            state.putInt(
                STATE_PROGRESS_RETRY_ORIGIN_CHOICE, originChoiceId);
            state.putInt(
                STATE_PROGRESS_RETRY_TARGET_CHOICE, targetChoiceId);
        }

        static ProgressSyncRetryDescriptor restore(Bundle state) {
            if (state == null) {
                return null;
            }
            int action = state.getInt(STATE_PROGRESS_RETRY_ACTION, 0);
            if (action == PROGRESS_RETRY_RELOAD) {
                return reload();
            }
            String device = state.getString(STATE_PROGRESS_RETRY_DEVICE);
            long sequence = state.getLong(
                STATE_PROGRESS_RETRY_SEQUENCE, 0);
            long epoch = state.getLong(
                STATE_PROGRESS_RETRY_EPOCH, 0);
            long origin = state.getLong(
                STATE_PROGRESS_RETRY_ORIGIN_SEQUENCE, 0);
            long local = state.getLong(
                STATE_PROGRESS_RETRY_LOCAL_SEQUENCE, 0);
            String remote = state.getString(
                STATE_PROGRESS_RETRY_REMOTE_DEVICE);
            long remoteSequence = state.getLong(
                STATE_PROGRESS_RETRY_REMOTE_SEQUENCE, 0);
            int kind = state.getInt(
                STATE_PROGRESS_RETRY_PENDING_KIND, 0);
            int originChoice = state.getInt(
                STATE_PROGRESS_RETRY_ORIGIN_CHOICE, -1);
            int targetChoice = state.getInt(
                STATE_PROGRESS_RETRY_TARGET_CHOICE, -1);
            if (action >= PROGRESS_RETRY_USE
                && action <= PROGRESS_RETRY_DISMISS
                && OctavoProgressPortable.validDeviceId(device)
                && sequence > 0 && epoch > 0 && origin > 0) {
                return new ProgressSyncRetryDescriptor(
                    action, device, sequence, epoch, origin,
                    0, null, 0, 0, -1, -1);
            }
            if ((action == PROGRESS_RETRY_FORWARD
                 || action == PROGRESS_RETRY_ROLLBACK)
                && origin >= 0 && local > 0
                && OctavoProgressPortable.validDeviceId(remote)
                && remoteSequence >= 0 && (kind == 1 || kind == 2)) {
                return new ProgressSyncRetryDescriptor(
                    action, null, 0, 0, origin, local,
                    remote, remoteSequence, kind, -1, -1);
            }
            if (action == PROGRESS_RETRY_LOCAL_STAGE
                && origin > 0
                && originChoice >= OctavoProgressPortable.Choice.CHAPTER
                && originChoice
                   <= OctavoProgressPortable.Choice.PERCENTAGE
                && targetChoice >= OctavoProgressPortable.Choice.CHAPTER
                && targetChoice
                   <= OctavoProgressPortable.Choice.PERCENTAGE
                && originChoice != targetChoice) {
                return new ProgressSyncRetryDescriptor(
                    action, null, 0, 0, origin, 0,
                    null, 0, 0, originChoice, targetChoice);
            }
            return null;
        }
    }

    private OctavoLibraryStore libraryStore;
    private File libraryFixture;
    private OctavoLibrarySyncStore librarySyncStore;
    private OctavoBookTransferStore bookTransferStore;
    private OctavoLibraryMembershipStore libraryMembershipStore;
    private OctavoAppearanceStore appearanceStore;
    private OctavoAppearanceSyncStore appearanceSyncStore;
    private OctavoAppearance appearance;
    private OctavoProgressStore progressStore;
    private OctavoProgressSyncStore progressSyncStore;
    private OctavoProgressDisplay progressDisplay;
    private OctavoAnnotationStore annotationStore;
    private OctavoNoteDraftStore noteDraftStore;
    private OctavoReadingPositionStore readingPositionStore;
    private LinearLayout libraryRoot;
    private TextView libraryIdentityStatus;
    private TextView libraryCatalogStatus;
    private OctavoLibrarySyncPrompt librarySyncPrompt;
    private OctavoLibraryMembershipPrompt libraryMembershipPrompt;
    private OctavoLibraryMembershipStore.Receipt libraryMembershipReceipt;
    private OctavoLibraryMembershipStore.StagedPortable
        libraryMembershipStaged;
    private OctavoLibrarySyncStore.StagedPortable libraryPromptStaged;
    private Runnable libraryPromptRetry;
    private Runnable libraryPromptCancel;
    private OctavoLibrarySyncStore.Candidate libraryCatalogOffer;
    private String libraryCatalogOfferManifestSha256;
    private byte[] libraryCatalogManifestBytes;
    private String libraryCatalogManifestDigest;
    private boolean libraryReviewEpochActive;
    private boolean libraryReviewEpochRetryPending;
    private long libraryReviewEpochBeforeRetry = -1;
    private boolean libraryCatalogReviewDeferred;
    private String transientTransferReader0Title;
    private String libraryTransferCatalogRetryMessage;
    private boolean libraryTransferExplicitRetryRequired;
    private String libraryDiscoveryStatus;
    private String libraryDiscoveryRetryBookKey;
    private boolean libraryDiscoveryDerivedThisActivity;
    private String libraryFocusBookKey;
    private int libraryFocusAction;
    private View libraryRowFocusReturn;
    private boolean librarySuppressedReviewRequested;
    private boolean libraryAttentionDeferred;
    private boolean libraryMembershipAttentionDeferred;
    private int libraryMembershipPendingAction;
    private String libraryMembershipPendingDigest;
    private long libraryMembershipPendingByteCount;
    private String libraryMembershipPendingRecordFingerprint;
    private String libraryMembershipPendingSnapshotFingerprint;
    private long libraryMembershipPendingStateGeneration = -1;
    private boolean libraryMembershipPendingRecordPresent;
    private int libraryMembershipPendingProjection = -1;
    private String libraryImportAssociationStatus;
    private OctavoLibraryStore.Book pendingImportAssociationBook;
    private OctavoLibraryStore.Book rejectedStagedImportCleanupBook;
    private OctavoLibraryStore.Book pendingLibraryIdentityBook;
    private int pendingLibraryIdentityMode = LIBRARY_IDENTITY_MODE_NONE;
    private boolean pendingLibraryIdentityRecordOpened;
    private OctavoLibrarySyncStore.LocalReconciliation
        pendingLibraryIdentityLocalReconciliation;
    private long pendingLibraryIdentityTransferAttemptSequence;
    private String pendingLibraryIdentityTransferAttemptId;
    private OctavoBookTransferStore.Phase pendingLibraryIdentityTransferPhase;
    private boolean pendingLibraryRestorePositionReview;
    private boolean pendingLibraryRestoreAppearanceReview;
    private boolean pendingLibraryRestoreProgressReview;
    private ReadingPositionChoiceRetry pendingLibraryReadingPositionRetry;
    private boolean libraryIdentityVerificationPosted;
    private final Runnable libraryIdentityVerification =
        this::continueLibraryIdentityVerification;
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
    private FrameLayout progressSyncOverlay;
    private OctavoProgressSyncPrompt progressSyncPrompt;
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
    private boolean progressSyncReviewPending;
    private boolean progressSyncReviewInitialized;
    private boolean progressSyncPendingLoaded;
    private boolean progressSyncAwaitingExplicitRetry;
    private boolean progressSyncRollbackRequested;
    private boolean progressSyncO8pgFutureBlocked;
    private OctavoProgressSyncStore.Candidate progressSyncCandidate;
    private OctavoProgressSyncStore.Candidate progressSyncPromptCandidate;
    private OctavoProgressSyncStore.Pending progressSyncPending;
    private Runnable progressSyncRetry;
    private String progressSyncFailureHeading;
    private String progressSyncFailureMessage;
    private ProgressSyncRetryDescriptor progressSyncRetryDescriptor;
    private long progressSyncReviewEpochBeforeRetry = -1;
    private boolean progressSyncRollbackEpochReconciliation;
    private boolean progressSyncAbandonAfterReload;
    private long progressSyncPromptGeneration;
    private OctavoSurfaceView progressReceiptSurface;
    private OctavoSurfaceView.ProgressPresentationReceipt
        latestProgressReceipt;
    private OctavoSurfaceView consumedProgressReceiptSurface;
    private OctavoProgressDisplay consumedProgressReceiptChoice;
    private long consumedProgressReceiptGeneration = -1;
    private long consumedProgressReceiptFrame = -1;
    private long progressSyncOriginSpineIndex = -1;
    private long progressSyncOriginByteOffset = -1;
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
        OctavoProgressStore.LoadStatus progressLoadStatus =
            progressStore.loadStatus();
        boolean progressResetAfterCorruption =
            progressLoadStatus == OctavoProgressStore.LoadStatus.CORRUPT;
        progressSyncO8pgFutureBlocked =
            progressLoadStatus == OctavoProgressStore.LoadStatus.FUTURE;
        progressSyncStore = new OctavoProgressSyncStore(this);
        OctavoProgressSyncStore.LoadStatus progressSyncLoadStatus =
            progressSyncStore.load();
        boolean progressRecoveredFromFinalizedSyncLane = false;
        if ((progressLoadStatus == OctavoProgressStore.LoadStatus.MISSING
             || progressLoadStatus
                == OctavoProgressStore.LoadStatus.CORRUPT)
            && progressSyncLoadStatus
               == OctavoProgressSyncStore.LoadStatus.LOADED) {
            OctavoProgressDisplay synchronizedDisplay =
                progressSyncStore.effectiveDisplay();
            if (synchronizedDisplay != null) {
                // A finalized O1PS lane is the durable provenance for a
                // missing/invalid O8PG. Present that exact choice first so
                // the real-frame path recreates O8PG without advancing the
                // local lane as though the fallback were a new user choice.
                progressDisplay = synchronizedDisplay;
                progressRecoveredFromFinalizedSyncLane = true;
            }
        }
        progressSyncPendingLoaded =
            progressSyncStore.pending() != null;
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.restore(savedInstanceState);
        progressSyncReviewEpochBeforeRetry = savedInstanceState == null
            ? -1 : savedInstanceState.getLong(
                STATE_PROGRESS_REVIEW_EPOCH_BEFORE_RETRY, -1);
        progressSyncRollbackEpochReconciliation =
            savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_PROGRESS_ROLLBACK_EPOCH_RETRY, false);
        if (progressSyncReviewEpochBeforeRetry >= 0) {
            // An uncertain epoch publication is user-action-required state.
            // A recreated first frame must not initialize or advance review
            // until Retry reloads and proves the exact prior/candidate bytes.
            progressSyncAwaitingExplicitRetry = true;
        }
        progressSyncAbandonAfterReload =
            savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_PROGRESS_ABANDON_AFTER_RELOAD, false);
        if (progressSyncRetryDescriptor != null
            && progressSyncRetryDescriptor.action
               == PROGRESS_RETRY_RELOAD) {
            progressSyncAwaitingExplicitRetry = true;
        }
        if (progressSyncRetryDescriptor != null
            && progressSyncRetryDescriptor.localStageAction()) {
            progressSyncAwaitingExplicitRetry = true;
        }
        if (progressSyncAbandonAfterReload) {
            progressSyncAwaitingExplicitRetry = true;
        }
        OctavoProgressSyncStore.Pending loadedProgressPending =
            progressSyncStore.pending();
        if (loadedProgressPending != null) {
            progressSyncRollbackRequested =
                loadedProgressPending.direction
                == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
            progressSyncAwaitingExplicitRetry = true;
            if (progressSyncRetryDescriptor != null
                && progressSyncRetryDescriptor.pendingAction()
                && !(progressSyncAbandonAfterReload
                    ? progressSyncRetryDescriptor.matchesPendingIdentity(
                        loadedProgressPending)
                    : progressSyncRetryDescriptor.matches(
                        loadedProgressPending))) {
                progressSyncRetryDescriptor = null;
            }
        } else if (progressSyncRetryDescriptor != null
                   && progressSyncRetryDescriptor.pendingAction()) {
            progressSyncRetryDescriptor = null;
        }
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
        libraryReviewEpochActive = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_REVIEW_EPOCH_ACTIVE, false);
        libraryCatalogReviewDeferred = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_CATALOG_REVIEW_DEFERRED, false);
        libraryReviewEpochRetryPending = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_REVIEW_EPOCH_RETRY, false);
        libraryReviewEpochBeforeRetry = savedInstanceState == null
            ? -1 : savedInstanceState.getLong(
                STATE_LIBRARY_REVIEW_EPOCH_BEFORE_RETRY, -1);
        libraryDiscoveryRetryBookKey = savedInstanceState == null
            ? null : savedInstanceState.getString(
                STATE_LIBRARY_DISCOVERY_RETRY_KEY);
        libraryFocusBookKey = savedInstanceState == null
            ? null : savedInstanceState.getString(
                STATE_LIBRARY_FOCUS_BOOK_KEY);
        libraryFocusAction = savedInstanceState == null
            ? LIBRARY_FOCUS_NONE
            : savedInstanceState.getInt(
                STATE_LIBRARY_FOCUS_ACTION,
                savedInstanceState.getBoolean(
                    STATE_LIBRARY_FOCUS_REMOVE, false)
                    ? LIBRARY_FOCUS_REMOVE_LOCAL
                    : LIBRARY_FOCUS_OPEN);
        librarySuppressedReviewRequested = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_SUPPRESSED_REVIEW, false);
        libraryAttentionDeferred = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_ATTENTION_DEFERRED, false);
        libraryMembershipAttentionDeferred = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_MEMBERSHIP_ATTENTION_DEFERRED, false);
        libraryMembershipPendingAction = savedInstanceState == null
            ? LIBRARY_MEMBERSHIP_ACTION_NONE
            : savedInstanceState.getInt(
                STATE_LIBRARY_MEMBERSHIP_PENDING_ACTION,
                LIBRARY_MEMBERSHIP_ACTION_NONE);
        libraryMembershipPendingDigest = savedInstanceState == null
            ? null : savedInstanceState.getString(
                STATE_LIBRARY_MEMBERSHIP_PENDING_DIGEST);
        libraryMembershipPendingByteCount = savedInstanceState == null
            ? 0 : savedInstanceState.getLong(
                STATE_LIBRARY_MEMBERSHIP_PENDING_BYTES, 0);
        libraryMembershipPendingRecordFingerprint =
            savedInstanceState == null ? null
            : savedInstanceState.getString(
                STATE_LIBRARY_MEMBERSHIP_PENDING_RECORD_FINGERPRINT);
        libraryMembershipPendingSnapshotFingerprint =
            savedInstanceState == null ? null
            : savedInstanceState.getString(
                STATE_LIBRARY_MEMBERSHIP_PENDING_SNAPSHOT_FINGERPRINT);
        libraryMembershipPendingStateGeneration =
            savedInstanceState == null ? -1
            : savedInstanceState.getLong(
                STATE_LIBRARY_MEMBERSHIP_PENDING_GENERATION, -1);
        libraryMembershipPendingRecordPresent =
            savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_MEMBERSHIP_PENDING_RECORD_PRESENT, false);
        libraryMembershipPendingProjection =
            savedInstanceState == null ? -1
            : savedInstanceState.getInt(
                STATE_LIBRARY_MEMBERSHIP_PENDING_PROJECTION, -1);
        librarySyncStore = new OctavoLibrarySyncStore(this);
        librarySyncStore.load();
        bookTransferStore = new OctavoBookTransferStore(this);
        bookTransferStore.load();
        libraryMembershipStore =
            new OctavoLibraryMembershipStore(this);
        libraryMembershipStore.load();
        libraryTransferExplicitRetryRequired =
            bookTransferStore.intentCount() != 0;
        libraryStore = new OctavoLibraryStore(this);
        libraryFixture = new File(OctavoFixture.install(this));
        libraryStore.loadCatalog(libraryFixture);

        String restoreKey = savedInstanceState == null
            ? null
            : savedInstanceState.getString(STATE_ACTIVE_BOOK_KEY);
        boolean restoreIdentityRecordOpened = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_LIBRARY_IDENTITY_RECORD_OPENED, false);
        boolean restoreReviewPending = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_POSITION_REVIEW_PENDING, false);
        boolean restoreAppearanceReviewPending = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_APPEARANCE_REVIEW_PENDING, false);
        boolean restoreProgressReviewPending = savedInstanceState != null
            && savedInstanceState.getBoolean(
                STATE_PROGRESS_REVIEW_PENDING, false);
        pendingLibraryRestorePositionReview = restoreReviewPending;
        pendingLibraryRestoreAppearanceReview =
            restoreAppearanceReviewPending;
        pendingLibraryRestoreProgressReview = restoreProgressReviewPending;
        pendingLibraryReadingPositionRetry =
            ReadingPositionChoiceRetry.restore(savedInstanceState);
        OctavoLibraryStore.Book restoreBook =
            restoreKey == null ? null : libraryStore.findBook(restoreKey);
        boolean readerRestored = restoreBook != null
            && showReader(restoreBook, restoreIdentityRecordOpened);
        if (!readerRestored) {
            showLibrary(!libraryReviewEpochActive
                && !libraryReviewEpochRetryPending);
        } else if (activeBook != null && !restoreIdentityRecordOpened) {
            readingPositionReviewPending = restoreReviewPending;
            appearanceSyncReviewPending =
                restoreAppearanceReviewPending;
            progressSyncReviewPending = restoreProgressReviewPending;
            ReadingPositionChoiceRetry restoredRetry =
                pendingLibraryReadingPositionRetry;
            if (restoredRetry != null
                && activeBook.key.equals(restoredRetry.bookDigest)) {
                readingPositionChoiceRetry = restoredRetry;
            }
            pendingLibraryReadingPositionRetry = null;
        } else if (activeBook != null) {
            pendingLibraryReadingPositionRetry = null;
        }
        if (appearanceResetAfterCorruption) {
            showOpenFailure(
                "Reader appearance was reset because its saved settings were invalid");
        }
        if (progressResetAfterCorruption) {
            showOpenFailure(progressRecoveredFromFinalizedSyncLane
                ? "Reader progress display will be recovered from its last "
                    + "synchronized setting after the reader confirms it "
                    + "on screen"
                : "Reader progress display was reset because its saved "
                    + "setting was invalid");
        }
        reportAnnotationLoadStatus(annotationLoadStatus);
        reportNoteDraftLoadStatus(noteDraftLoadStatus);
        reportReadingPositionLoadStatus(readingPositionLoadStatus);
        reportAppearanceSyncLoadStatus(appearanceSyncLoadStatus);
        reportProgressSyncLoadStatus(
            progressSyncLoadStatus, progressLoadStatus);
        if (hasText(libraryStore.lastError())) {
            showOpenFailure(libraryStore.lastError());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (activeBook != null) {
            state.putString(STATE_ACTIVE_BOOK_KEY, activeBook.key);
        } else if (pendingLibraryIdentityMode
                       == LIBRARY_IDENTITY_MODE_OPEN
                   && pendingLibraryIdentityBook != null
                   && !libraryStore.isStagedImport(
                       pendingLibraryIdentityBook)
                   && libraryStore.findBook(
                       pendingLibraryIdentityBook.key) != null) {
            state.putString(
                STATE_ACTIVE_BOOK_KEY, pendingLibraryIdentityBook.key);
            state.putBoolean(
                STATE_LIBRARY_IDENTITY_RECORD_OPENED,
                pendingLibraryIdentityRecordOpened);
        }
        state.putBoolean(STATE_CHROME_VISIBLE, chromeVisible);
        state.putBoolean(
            STATE_LIBRARY_REVIEW_EPOCH_ACTIVE,
            libraryReviewEpochActive && libraryRoot != null);
        state.putBoolean(
            STATE_LIBRARY_CATALOG_REVIEW_DEFERRED,
            libraryCatalogReviewDeferred && libraryRoot != null);
        state.putBoolean(
            STATE_LIBRARY_REVIEW_EPOCH_RETRY,
            libraryReviewEpochRetryPending);
        state.putLong(
            STATE_LIBRARY_REVIEW_EPOCH_BEFORE_RETRY,
            libraryReviewEpochBeforeRetry);
        if (hasText(libraryDiscoveryRetryBookKey)) {
            state.putString(
                STATE_LIBRARY_DISCOVERY_RETRY_KEY,
                libraryDiscoveryRetryBookKey);
        }
        if (hasText(libraryFocusBookKey)) {
            state.putString(
                STATE_LIBRARY_FOCUS_BOOK_KEY, libraryFocusBookKey);
            state.putInt(
                STATE_LIBRARY_FOCUS_ACTION, libraryFocusAction);
        }
        state.putBoolean(
            STATE_LIBRARY_SUPPRESSED_REVIEW,
            librarySuppressedReviewRequested && libraryRoot != null);
        state.putBoolean(
            STATE_LIBRARY_ATTENTION_DEFERRED,
            libraryAttentionDeferred && libraryRoot != null);
        state.putBoolean(
            STATE_LIBRARY_MEMBERSHIP_ATTENTION_DEFERRED,
            libraryMembershipAttentionDeferred && libraryRoot != null);
        if (libraryMembershipPendingAction
                != LIBRARY_MEMBERSHIP_ACTION_NONE
            && hasText(libraryMembershipPendingDigest)
            && libraryMembershipPendingByteCount > 0) {
            state.putInt(
                STATE_LIBRARY_MEMBERSHIP_PENDING_ACTION,
                libraryMembershipPendingAction);
            state.putString(
                STATE_LIBRARY_MEMBERSHIP_PENDING_DIGEST,
                libraryMembershipPendingDigest);
            state.putLong(
                STATE_LIBRARY_MEMBERSHIP_PENDING_BYTES,
                libraryMembershipPendingByteCount);
            state.putString(
                STATE_LIBRARY_MEMBERSHIP_PENDING_RECORD_FINGERPRINT,
                libraryMembershipPendingRecordFingerprint);
            state.putString(
                STATE_LIBRARY_MEMBERSHIP_PENDING_SNAPSHOT_FINGERPRINT,
                libraryMembershipPendingSnapshotFingerprint);
            state.putLong(
                STATE_LIBRARY_MEMBERSHIP_PENDING_GENERATION,
                libraryMembershipPendingStateGeneration);
            state.putBoolean(
                STATE_LIBRARY_MEMBERSHIP_PENDING_RECORD_PRESENT,
                libraryMembershipPendingRecordPresent);
            state.putInt(
                STATE_LIBRARY_MEMBERSHIP_PENDING_PROJECTION,
                libraryMembershipPendingProjection);
        }
        state.putBoolean(
            STATE_POSITION_REVIEW_PENDING,
            readingPositionReviewPending);
        state.putBoolean(
            STATE_APPEARANCE_REVIEW_PENDING,
            appearanceSyncReviewPending);
        state.putBoolean(
            STATE_PROGRESS_REVIEW_PENDING,
            progressSyncReviewPending);
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
        ProgressSyncRetryDescriptor progressRetry =
            progressSyncRetryDescriptor;
        OctavoProgressSyncStore.Pending progressPending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (progressPending != null) {
            progressRetry = ProgressSyncRetryDescriptor.pending(
                progressPending.direction
                    == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                    ? PROGRESS_RETRY_ROLLBACK
                    : PROGRESS_RETRY_FORWARD,
                progressPending);
        } else if (progressSyncAwaitingExplicitRetry
                   && progressRetry == null) {
            progressRetry = ProgressSyncRetryDescriptor.reload();
        }
        if (progressRetry != null) {
            progressRetry.save(state);
        }
        if (progressSyncReviewEpochBeforeRetry >= 0) {
            state.putLong(
                STATE_PROGRESS_REVIEW_EPOCH_BEFORE_RETRY,
                progressSyncReviewEpochBeforeRetry);
            state.putBoolean(
                STATE_PROGRESS_ROLLBACK_EPOCH_RETRY,
                progressSyncRollbackEpochReconciliation);
        }
        if (progressSyncAbandonAfterReload) {
            state.putBoolean(
                STATE_PROGRESS_ABANDON_AFTER_RELOAD, true);
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
        if (progressSyncPrompt != null) {
            progressSyncPrompt.applyAppearance(appearance);
            if (progressSyncOverlay != null) {
                progressSyncOverlay.setBackgroundColor(
                    progressSyncPrompt.overlayColor());
            }
            updateProgressSyncPromptBounds();
        }
        if (libraryMembershipPrompt != null) {
            libraryMembershipPrompt.applyAppearance(appearance);
        }
        if (surfaceView != null) {
            surfaceView.reapplyAppearance();
        }
        restorePendingAppearanceAfterLifecycle();
        restorePendingReadingPositionAfterLifecycle();
        restorePendingProgressAfterLifecycle();
    }

    @Override
    public void onBackPressed() {
        if (libraryMembershipPrompt != null) {
            dismissLibraryMembershipForBack();
            return;
        } else if (librarySyncPrompt != null) {
            if (librarySyncPrompt.handleBack()) {
                return;
            }
            deferLibraryAttention();
            return;
        } else if (appearanceSyncPrompt != null) {
            dismissAppearanceSyncForBack();
        } else if (readingPositionPrompt != null) {
            dismissReadingPositionForBack();
        } else if (progressSyncPrompt != null) {
            dismissProgressSyncForBack();
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
                processProgressPresentationReceipt();
                drainDeferredSyncPrompts();
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
            showLibrary(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        scheduleLibraryIdentityVerification();
        if (surfaceView != null) {
            surfaceView.hostResumed();
        }
        restorePendingAppearanceAfterLifecycle();
        restorePendingReadingPositionAfterLifecycle();
        restorePendingProgressAfterLifecycle();
        drainDeferredSyncPrompts();
        if (deferredAppearanceFailure != null) {
            String message = deferredAppearanceFailure;
            deferredAppearanceFailure = null;
            showOpenFailure(message);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (libraryRoot != null) {
            libraryRoot.removeCallbacks(libraryIdentityVerification);
        }
        libraryIdentityVerificationPosted = false;
        if (surfaceView != null) {
            surfaceView.hostPaused();
        }
        restorePendingReadingPositionAfterLifecycle();
        flushAppearancePersistence();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        flushAppearancePersistence();
        if (libraryStore != null) {
            libraryStore.cancelBookIdentityVerification();
        }
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
        if (!showReader(candidate, true)) {
            boolean retainedAssociation =
                libraryStore.hasPendingImportAssociation(
                    candidate.key, candidate.byteCount);
            boolean cleanupComplete = retainedAssociation
                || discardRejectedLibraryOpen(candidate);
            if (retainedAssociation) {
                showOpenFailure(nonemptyMessage(
                    libraryStore.lastError(),
                    "The validated EPUB needs Library association Retry"));
            } else if (!cleanupComplete) {
                showOpenFailure(nonemptyMessage(
                    libraryStore.lastError(),
                    "The rejected EPUB staging file needs cleanup Retry"));
            } else {
                showOpenFailure("The selected file is not a readable EPUB");
            }
            return false;
        }
        lastOpenError = null;
        return true;
    }

    private boolean showReader(OctavoLibraryStore.Book requested,
                               boolean recordOpened) {
        long readerEntryStartedMillis = SystemClock.uptimeMillis();
        boolean stagedImport = libraryStore.isStagedImport(requested);
        OctavoLibraryStore.Book current =
            libraryStore.findBook(requested.key);
        // A picker repair owns exact fixed staging bytes. Prefer them over an
        // existing same-digest O6 row until Reader0 validates and the import
        // journal atomically replaces the managed destination.
        OctavoLibraryStore.Book target = stagedImport
            ? requested : current == null ? requested : current;
        OctavoLibraryStore.IdentityCheckStatus identityStatus =
            libraryStore.verifyBookIdentityStep(
                target, 4 * 1024 * 1024);
        if (identityStatus == OctavoLibraryStore.IdentityCheckStatus.PENDING) {
            pendingLibraryIdentityBook = target;
            pendingLibraryIdentityMode = LIBRARY_IDENTITY_MODE_OPEN;
            pendingLibraryIdentityRecordOpened = recordOpened;
            pendingLibraryIdentityLocalReconciliation = null;
            if (libraryRoot == null) {
                showLibrary();
            } else {
                updateLibraryIdentityStatus(
                    "Verifying EPUB identity before opening");
            }
            scheduleLibraryIdentityVerification();
            return true;
        }
        if (identityStatus != OctavoLibraryStore.IdentityCheckStatus.VERIFIED) {
            return false;
        }

        if (stagedImport) {
            OctavoManagedEpubValidator.Result validated =
                OctavoManagedEpubValidator.validate(
                    this, target.file, appearance);
            if (!validated.valid) {
                libraryImportAssociationStatus =
                    "Reader validation rejected the staged EPUB";
                return false;
            }
            try {
                target = libraryStore.publishReader0ValidatedImport(target);
            } catch (IOException | RuntimeException exception) {
                libraryImportAssociationStatus = nonemptyMessage(
                    libraryStore.lastError(),
                    "Managed EPUB publication needs Retry");
                return false;
            }
            if (target == null) {
                libraryImportAssociationStatus =
                    "Managed EPUB publication needs Retry";
                return false;
            }
            pendingImportAssociationBook = target;
            current = libraryStore.findBook(target.key);
        }
        boolean pendingImportAssociation =
            libraryStore.hasPendingImportAssociation(
                target.key, target.byteCount);
        OctavoLibraryStore.Session session =
            current == null || pendingImportAssociation
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
        boolean catalogPersistenceBlocked = false;
        if (recordOpened
            && !libraryStore.recordOpened(target, readerTitle)) {
            if (pendingImportAssociation
                || current == null || !libraryStore.mutationBlocked()) {
                replacement.release();
                return false;
            }
            catalogPersistenceBlocked = true;
        }
        target = libraryStore.findBook(target.key);
        if (target == null) {
            replacement.release();
            return false;
        }
        String discoveryFailure = null;
        boolean importAssociationComplete = true;
        if (pendingImportAssociation) {
            importAssociationComplete =
                libraryStore.completeImportedCatalogAssociation(target);
            if (importAssociationComplete) {
                pendingImportAssociationBook = null;
                libraryImportAssociationStatus = null;
            } else {
                discoveryFailure = nonemptyMessage(
                    libraryStore.lastError(),
                    "The book opened, but its Library association needs Retry");
            }
        }
        if (target.imported && importAssociationComplete) {
            if (!recordLocalDiscovery(target)) {
                discoveryFailure = nonemptyMessage(
                    librarySyncStore.lastError(),
                    "The book opened, but Library discovery needs Retry");
            }
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
        pendingLibraryIdentityBook = null;
        pendingLibraryIdentityMode = LIBRARY_IDENTITY_MODE_NONE;
        pendingLibraryIdentityRecordOpened = false;
        pendingLibraryIdentityLocalReconciliation = null;
        libraryFocusBookKey = null;
        libraryFocusAction = LIBRARY_FOCUS_NONE;
        libraryRowFocusReturn = null;
        libraryReviewEpochActive = false;
        readingPositionReviewPending = recordOpened;
        readingPositionReviewInitialized = false;
        appearanceSyncReviewPending = recordOpened;
        appearanceSyncReviewInitialized = false;
        progressSyncReviewPending = recordOpened;
        progressSyncReviewInitialized = false;
        hasPresentedReadingPosition = false;
        libraryRoot = null;
        librarySyncPrompt = null;
        libraryMembershipPrompt = null;
        libraryMembershipReceipt = null;
        libraryMembershipStaged = null;
        libraryPromptStaged = null;
        libraryPromptRetry = null;
        libraryPromptCancel = null;
        readerRoot = root;
        installReaderEntryCover();
        setContentView(windowRoot, matchParentLayout());
        windowRoot.requestApplyInsets();
        updateBookmarkToggle();
        if (activityResumed) {
            replacement.hostResumed();
        }
        if (catalogPersistenceBlocked) {
            showOpenFailure(libraryStore.lastError() == null
                ? "The book opened, but Library metadata is read-only"
                : libraryStore.lastError());
        }
        if (discoveryFailure != null) {
            showOpenFailure(discoveryFailure);
        }
        return true;
    }

    private void scheduleLibraryIdentityVerification() {
        if (!activityResumed
            || libraryRoot == null
            || pendingLibraryIdentityBook == null
            || libraryIdentityVerificationPosted) {
            return;
        }
        libraryIdentityVerificationPosted = true;
        libraryRoot.post(libraryIdentityVerification);
    }

    private void continueLibraryIdentityVerification() {
        libraryIdentityVerificationPosted = false;
        if (!activityResumed || pendingLibraryIdentityBook == null) {
            return;
        }
        OctavoLibraryStore.Book target = pendingLibraryIdentityBook;
        int mode = pendingLibraryIdentityMode;
        if (mode == LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION) {
            continueTransferFinalizationIdentityVerification(target);
            return;
        }
        OctavoLibraryStore.IdentityCheckStatus status =
            libraryStore.verifyBookIdentityStep(
                target, 4 * 1024 * 1024);
        if (status == OctavoLibraryStore.IdentityCheckStatus.PENDING) {
            if (mode == LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION) {
                libraryImportAssociationStatus =
                    "Verifying exact managed EPUB identity";
            } else if (mode
                       == LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION) {
                libraryDiscoveryStatus = "Verification in progress";
            }
            updateLibraryIdentityStatus(mode
                == LIBRARY_IDENTITY_MODE_OPEN
                    ? "Verifying EPUB identity before opening"
                    : "Verifying exact Library EPUB identity");
            scheduleLibraryIdentityVerification();
            return;
        }

        boolean recordOpened = pendingLibraryIdentityRecordOpened;
        OctavoLibrarySyncStore.LocalReconciliation localReconciliation =
            pendingLibraryIdentityLocalReconciliation;
        if (status != OctavoLibraryStore.IdentityCheckStatus.VERIFIED) {
            clearPendingLibraryIdentityOperation();
            pendingLibraryReadingPositionRetry = null;
            if (mode == LIBRARY_IDENTITY_MODE_OPEN) {
                discardRejectedLibraryOpen(target);
            } else if (mode
                       == LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION) {
                pendingImportAssociationBook = target;
                libraryImportAssociationStatus =
                    "Exact managed EPUB identity verification failed";
            } else if (mode
                       == LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION) {
                libraryDiscoveryStatus =
                    "Exact local EPUB verification failed";
            }
            showLibrary();
            showOpenFailure(
                "The EPUB did not match its recorded content identity");
            return;
        }

        if (mode == LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION) {
            clearPendingLibraryIdentityOperation();
            boolean associated =
                completePendingImportAssociationAfterIdentity(target);
            showLibrary();
            if (!associated) {
                showOpenFailure(nonemptyMessage(
                    firstNonemptyMessage(
                        libraryStore.lastError(),
                        libraryImportAssociationStatus),
                    "The validated EPUB still needs Library association Retry"));
            }
            return;
        }

        if (mode == LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION) {
            clearPendingLibraryIdentityOperation();
            boolean published = completeLocalPublicationAfterIdentity(
                target, localReconciliation);
            showLibrary();
            if (!published) {
                showOpenFailure(nonemptyMessage(
                    firstNonemptyMessage(
                        librarySyncStore.lastError(),
                        libraryDiscoveryStatus),
                    "Local Library discovery still needs Retry"));
            }
            return;
        }

        clearPendingLibraryIdentityOperation();
        if (!showReader(target, recordOpened) || activeBook == null) {
            pendingLibraryReadingPositionRetry = null;
            boolean cleanupComplete =
                discardRejectedLibraryOpen(target);
            showLibrary();
            showOpenFailure(cleanupComplete
                ? "Unable to open the verified Library book"
                : nonemptyMessage(
                    libraryStore.lastError(),
                    "The rejected EPUB staging file needs cleanup Retry"));
            return;
        }
        if (!recordOpened) {
            readingPositionReviewPending =
                pendingLibraryRestorePositionReview;
            appearanceSyncReviewPending =
                pendingLibraryRestoreAppearanceReview;
            progressSyncReviewPending =
                pendingLibraryRestoreProgressReview;
            ReadingPositionChoiceRetry restoredRetry =
                pendingLibraryReadingPositionRetry;
            if (restoredRetry != null
                && activeBook.key.equals(restoredRetry.bookDigest)) {
                readingPositionChoiceRetry = restoredRetry;
            }
        }
        pendingLibraryReadingPositionRetry = null;
    }

    private void continueTransferFinalizationIdentityVerification(
        OctavoLibraryStore.Book target) {
        long attemptSequence =
            pendingLibraryIdentityTransferAttemptSequence;
        String attemptId = pendingLibraryIdentityTransferAttemptId;
        OctavoBookTransferStore.Phase phase =
            pendingLibraryIdentityTransferPhase;
        String readerTitle = transientTransferReader0Title;
        if (!exactPendingTransferFinalization(
                target, attemptSequence, attemptId, phase)) {
            libraryStore.cancelBookIdentityVerification();
            clearPendingLibraryIdentityOperation();
            transientTransferReader0Title = null;
            libraryTransferExplicitRetryRequired =
                bookTransferStore.intentCount() != 0;
            libraryTransferCatalogRetryMessage =
                "The retained transfer changed between verification steps; "
                    + "its exact current state needs explicit Retry";
            showLibrary();
            showOpenFailure(libraryTransferCatalogRetryMessage);
            return;
        }
        OctavoLibraryStore.TransferredBookStepStatus status =
            libraryStore.verifyAndRecordTransferredBookStep(
                target, readerTitle, 4 * 1024 * 1024);
        if (status
            == OctavoLibraryStore.TransferredBookStepStatus.PENDING) {
            updateLibraryIdentityStatus(
                "Verifying downloaded EPUB and publishing its Library record");
            scheduleLibraryIdentityVerification();
            return;
        }

        boolean convertedToRepair = status
                == OctavoLibraryStore.TransferredBookStepStatus
                    .IDENTITY_FAILED
            && convertPublishedTransferToRepairCleanup(
                target.key, target.byteCount,
                attemptSequence, attemptId, phase);
        boolean finalized = status
                == OctavoLibraryStore.TransferredBookStepStatus.COMPLETED
            && completeTransferFinalizationAfterCatalogRecord(
                attemptSequence, attemptId, phase);
        if (convertedToRepair || finalized) {
            libraryTransferExplicitRetryRequired = false;
            libraryTransferCatalogRetryMessage = null;
        } else {
            libraryTransferExplicitRetryRequired = true;
            if (status
                == OctavoLibraryStore.TransferredBookStepStatus
                    .CATALOG_RETRY) {
                libraryTransferCatalogRetryMessage = nonemptyMessage(
                    libraryStore.lastError(),
                    "Publishing the local Library catalog needs Retry");
                recordTransferCatalogRetry(attemptSequence, attemptId, phase);
            } else if (status
                       == OctavoLibraryStore.TransferredBookStepStatus.STALE) {
                libraryTransferCatalogRetryMessage =
                    "The retained transfer changed; publishing its local "
                        + "Library record needs explicit Retry";
            } else if (status
                       == OctavoLibraryStore.TransferredBookStepStatus
                           .COMPLETED) {
                libraryTransferCatalogRetryMessage = nonemptyMessage(
                    firstNonemptyMessage(
                        librarySyncStore.lastError(),
                        bookTransferStore.lastError()),
                    "The local Library record is complete; private transfer "
                        + "reconciliation needs Retry");
            } else if (!convertedToRepair) {
                libraryTransferCatalogRetryMessage =
                    "The exact EPUB repair transition needs Retry";
            }
        }
        clearPendingLibraryIdentityOperation();
        transientTransferReader0Title = null;
        showLibrary();
        if (!convertedToRepair && !finalized) {
            showOpenFailure(nonemptyMessage(
                firstNonemptyMessage(
                    libraryTransferCatalogRetryMessage,
                    libraryStore.lastError(),
                    bookTransferStore.lastError(),
                    librarySyncStore.lastError()),
                "The downloaded EPUB still needs completion Retry"));
        }
    }

    private boolean exactPendingTransferFinalization(
        OctavoLibraryStore.Book expected,
        long expectedAttemptSequence,
        String expectedAttemptId,
        OctavoBookTransferStore.Phase expectedPhase) {
        OctavoBookTransferStore.ActiveJob active =
            bookTransferStore.activeJob();
        return expected != null && hasText(expectedAttemptId)
            && (expectedPhase
                    == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
                || expectedPhase
                    == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED)
            && active != null
            && active.direction
               == OctavoBookTransferStore.Direction.DOWNLOAD
            && active.durableDirection
               == OctavoBookTransferStore.DurableDirection.FORWARD
            && active.phase == expectedPhase
            && active.attemptSequence == expectedAttemptSequence
            && active.attemptId.equals(expectedAttemptId)
            && active.digest.equals(expected.key)
            && active.byteCount == expected.byteCount
            && (expectedPhase
                    != OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED
                || librarySyncStore.decision(active.digest)
                   == OctavoLibrarySyncStore.Decision.DOWNLOADED);
    }

    private void clearPendingLibraryIdentityOperation() {
        pendingLibraryIdentityBook = null;
        pendingLibraryIdentityMode = LIBRARY_IDENTITY_MODE_NONE;
        pendingLibraryIdentityRecordOpened = false;
        pendingLibraryIdentityLocalReconciliation = null;
        pendingLibraryIdentityTransferAttemptSequence = 0;
        pendingLibraryIdentityTransferAttemptId = null;
        pendingLibraryIdentityTransferPhase = null;
    }

    private boolean completePendingImportAssociationAfterIdentity(
        OctavoLibraryStore.Book pending) {
        if (pending == null
            || !libraryStore.hasPendingImportAssociation(
                pending.key, pending.byteCount)) {
            pendingImportAssociationBook = null;
            libraryImportAssociationStatus =
                "The pending import changed before association";
            return false;
        }
        pendingImportAssociationBook = pending;
        OctavoManagedEpubValidator.Result validated =
            OctavoManagedEpubValidator.validate(
                this, pending.file, appearance);
        if (!validated.valid) {
            libraryImportAssociationStatus =
                "Reader validation rejected the managed EPUB";
            return false;
        }
        if (!libraryStore.recordValidatedPendingImport(
                pending, validated.title)) {
            libraryImportAssociationStatus = nonemptyMessage(
                libraryStore.lastError(),
                "The validated EPUB needs durable Library association Retry");
            return false;
        }
        OctavoLibraryStore.Book associated =
            libraryStore.findBook(pending.key);
        if (associated == null
            || associated.byteCount != pending.byteCount
            || !libraryStore.completeImportedCatalogAssociation(associated)) {
            libraryImportAssociationStatus = nonemptyMessage(
                libraryStore.lastError(),
                "The EPUB import journal needs completion Retry");
            return false;
        }
        pendingImportAssociationBook = null;
        libraryImportAssociationStatus = null;
        if (!recordLocalDiscovery(associated)) {
            libraryDiscoveryStatus =
                "Local discovery publication needs Retry";
        }
        return true;
    }

    private boolean completeLocalPublicationAfterIdentity(
        OctavoLibraryStore.Book book,
        OctavoLibrarySyncStore.LocalReconciliation expected) {
        if (book == null || !book.imported || book.repairRequired
            || !book.identityVerified) {
            libraryDiscoveryStatus = "Exact local EPUB is unavailable";
            return false;
        }
        OctavoManagedEpubValidator.Result validated =
            OctavoManagedEpubValidator.validate(
                this, book.file, appearance);
        if (!validated.valid) {
            libraryDiscoveryStatus = "Reader validation failed";
            return false;
        }
        if (expected == null) {
            if (librarySyncStore.decision(book.key)
                == OctavoLibrarySyncStore.Decision.DOWNLOADED) {
                if (book.key.equals(libraryDiscoveryRetryBookKey)) {
                    libraryDiscoveryRetryBookKey = null;
                }
                libraryDiscoveryStatus = null;
                return true;
            }
            boolean discovered = recordLocalDiscovery(book);
            libraryDiscoveryStatus = discovered
                ? null : "Local discovery publication needs Retry";
            return discovered;
        }
        OctavoLibrarySyncStore.LocalReconciliation current =
            librarySyncStore.localReconciliation();
        if (current == null || !current.sameIdentity(expected)
            || current.kind
               != OctavoLibrarySyncStore.LocalReconciliationKind.PUBLICATION
            || !current.digest.equals(book.key)
            || current.byteCount != book.byteCount) {
            libraryDiscoveryStatus =
                "Local discovery identity changed before publication";
            return false;
        }
        boolean finalized = librarySyncStore.finalizeLocalReconciliation(
            current, true).succeeded();
        if (finalized && book.key.equals(libraryDiscoveryRetryBookKey)) {
            libraryDiscoveryRetryBookKey = null;
        } else if (!finalized) {
            libraryDiscoveryRetryBookKey = book.key;
        }
        libraryDiscoveryStatus = finalized
            ? null : "Local discovery publication needs Retry";
        return finalized;
    }

    private void updateLibraryIdentityStatus(String message) {
        if (libraryIdentityStatus == null) {
            return;
        }
        libraryIdentityStatus.setText(message);
        libraryIdentityStatus.setContentDescription(message);
        libraryIdentityStatus.setVisibility(View.VISIBLE);
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
        library.setOnClickListener(view -> showLibrary(true));
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
                processProgressPresentationReceipt();
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
                processProgressPresentationReceipt();
                updateReaderNavigationAvailability(surfaceView);
                scheduleNavigationSnapshotRefresh();
            }

            @Override
            public void onNavigationRequestFailure(String message) {
                bookmarkNavigationPending = false;
                OctavoProgressSyncStore.Pending progressPending =
                    progressSyncStore == null ? null
                        : progressSyncStore.pending();
                if (progressPending != null) {
                    progressSyncPending = progressPending;
                    showProgressSyncFailure(
                        "Progress display update needs attention",
                        TextUtils.isEmpty(message)
                            ? "The requested progress display was not "
                                + "confirmed on screen. Retry is safe."
                            : message,
                        progressPending.direction
                            == OctavoProgressSyncStore.PendingDirection
                                .ROLLBACK
                            ? () -> retryPendingProgressRollback(
                                progressPending)
                            : OctavoActivity.this::
                                retryPendingProgressForward);
                    return;
                }
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
            || progressSyncPrompt != null
            || appearancePanel != null || navigationPanel != null
            || searchPanel != null || bookmarksPanel != null
            || surfaceView.hasSelectionForAccessibility()) {
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
        considerProgressSyncCandidate();
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
        if (appearanceSyncPrompt != null || progressSyncPrompt != null) {
            return false;
        }
        if (readerRoot == null) {
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
        if (appearanceSyncPrompt == null
            && progressSyncPrompt == null) {
            restoreFailureBannerAfterModalPrompt();
        }
        if (restoreFocus) {
            drainDeferredSyncPrompts();
            if (appearanceSyncPrompt == null
                && readingPositionPrompt == null
                && progressSyncPrompt == null) {
                restoreReadingPositionFocusAfterClose();
            }
        }
    }

    private void restoreReadingPositionFocusAfterClose() {
        if (readingPositionPrompt != null
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null
            || surfaceView == null || !surfaceView.isShown()) {
            return;
        }
        OctavoSurfaceView focusReturn = surfaceView;
        focusReturn.requestFocus();
        focusReturn.post(() -> {
            if (readingPositionPrompt != null
                || appearanceSyncPrompt != null
                || progressSyncPrompt != null
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
                && progressSyncPrompt == null
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
            || progressSyncPrompt != null
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
            || progressSyncPrompt != null
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
            || progressSyncPrompt != null
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
            || progressSyncPrompt != null
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
        if (readingPositionPrompt == null
            && progressSyncPrompt == null) {
            restoreFailureBannerAfterModalPrompt();
        }
        if (restoreFocus) {
            drainDeferredSyncPrompts();
            if (appearanceSyncPrompt == null
                && readingPositionPrompt == null
                && progressSyncPrompt == null) {
                restoreAppearanceSyncFocusAfterClose();
            }
        }
    }

    private void restoreReaderAccessibilityBoundary() {
        boolean readerObscured = appearancePanel != null
            || navigationPanel != null || searchPanel != null
            || bookmarksPanel != null || readingPositionPrompt != null
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null;
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
            || progressSyncPrompt != null
            || surfaceView == null || !surfaceView.isShown()) {
            return;
        }
        OctavoSurfaceView target = surfaceView;
        target.requestFocus();
        target.post(() -> {
            if (appearanceSyncPrompt != null
                || readingPositionPrompt != null
                || progressSyncPrompt != null
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

    private void reportProgressSyncLoadStatus(
        OctavoProgressSyncStore.LoadStatus syncStatus,
        OctavoProgressStore.LoadStatus localStatus) {
        if (localStatus == OctavoProgressStore.LoadStatus.FUTURE) {
            progressSyncO8pgFutureBlocked = true;
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The saved progress display was written by a newer "
                    + "version. Its bytes were preserved and synchronization "
                    + "is blocked.",
                this::reloadProgressSyncState);
            return;
        }
        if (syncStatus == null
            || syncStatus == OctavoProgressSyncStore.LoadStatus.LOADED
            || syncStatus
               == OctavoProgressSyncStore.LoadStatus.MISSING_CREATED) {
            return;
        }
        String message = progressSyncStore == null
            ? "Progress-display synchronization is unavailable."
            : progressSyncStore.lastError();
        if (TextUtils.isEmpty(message)) {
            message = syncStatus
                == OctavoProgressSyncStore.LoadStatus.CORRUPT_QUARANTINED
                ? "Invalid progress-display sync state was quarantined."
                : "Progress-display synchronization needs attention.";
        }
        if (syncStatus
            == OctavoProgressSyncStore.LoadStatus.CORRUPT_QUARANTINED) {
            showOpenFailure(message);
            return;
        }
        showProgressSyncFailure(
            "Progress display update needs attention",
            message,
            this::reloadProgressSyncState);
    }

    /**
     * Consumes one exact settled real-frame receipt. Its identity is marked
     * consumed before either file is touched so a duplicate frame can never
     * execute a user-visible Retry implicitly.
     */
    private void processProgressPresentationReceipt() {
        OctavoSurfaceView owner = surfaceView;
        if (owner == null || progressSyncStore == null) {
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            owner.currentProgressPresentationReceipt();
        if (receipt == null || receipt.choice == null
            || !receipt.strictResumeSettled) {
            return;
        }
        latestProgressReceipt = receipt;
        progressReceiptSurface = owner;
        if (consumedProgressReceiptSurface == owner
            && consumedProgressReceiptGeneration
               == receipt.progressGeneration
            && consumedProgressReceiptFrame == receipt.frameCount
            && consumedProgressReceiptChoice == receipt.choice) {
            return;
        }
        consumedProgressReceiptSurface = owner;
        consumedProgressReceiptGeneration = receipt.progressGeneration;
        consumedProgressReceiptFrame = receipt.frameCount;
        consumedProgressReceiptChoice = receipt.choice;
        progressDisplay = receipt.choice;

        if (progressSyncO8pgFutureBlocked) {
            presentRetainedProgressSyncFailure();
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        progressSyncPending = pending;
        if (pending != null) {
            progressSyncRollbackRequested = pending.direction
                == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
            if (progressSyncPendingLoaded) {
                if (progressSyncRetryDescriptor != null
                    && progressSyncRetryDescriptor.localStageAction()) {
                    if (!localStageMatchesPending(
                            progressSyncRetryDescriptor, pending)) {
                        showProgressSyncFailure(
                            "Progress display update needs attention",
                            "The restored local display Retry does not "
                                + "match the durable pending transaction.",
                            this::reloadProgressSyncForLocalStage);
                        return;
                    }
                    progressSyncRetryDescriptor =
                        ProgressSyncRetryDescriptor.pending(
                            PROGRESS_RETRY_FORWARD, pending);
                }
                showLoadedPendingProgressRetry(pending, receipt);
                return;
            }
            if (progressSyncAwaitingExplicitRetry) {
                presentRetainedProgressSyncFailure();
                return;
            }
            if (pending.direction
                == OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
                if (receipt.choice == pending.originDisplay()) {
                    finishPendingProgressRollback(pending, receipt);
                }
            } else if (receipt.choice == pending.targetDisplay()) {
                completePendingProgressForward(pending, receipt, false);
            }
            return;
        }

        if (progressSyncAwaitingExplicitRetry) {
            presentRetainedProgressSyncFailure();
            return;
        }

        progressSyncPendingLoaded = false;
        OctavoProgressPortable.Lane local =
            progressSyncStore.localLane();
        if (local == null) {
            stageInitialPresentedProgress(receipt);
            return;
        }
        OctavoProgressDisplay localDisplay = local.choice.toDisplay();
        if (receipt.choice != localDisplay) {
            // Product UI stages before requesting the Surface. This bounded
            // compatibility path handles package-private/test requests and
            // upgrades from the pre-O1PS Port 8 implementation.
            stageObservedPresentedProgress(receipt, localDisplay);
            return;
        }
        if (!progressStore.hasCanonicalCurrentRecord(receipt.choice)) {
            saveCanonicalProgressWithoutLaneAdvance(receipt);
            return;
        }
        finishProgressConvergence(receipt);
    }

    private void stageInitialPresentedProgress(
        OctavoSurfaceView.ProgressPresentationReceipt receipt) {
        if (!progressReceiptIsCurrent(receipt)
            || progressSyncO8pgFutureBlocked) {
            return;
        }
        OctavoProgressSyncStore.MutationResult staged =
            progressSyncStore.stageInitialPresented(receipt.choice);
        if (staged == OctavoProgressSyncStore.MutationResult.UNCHANGED) {
            finishProgressConvergence(receipt);
            return;
        }
        if (staged != OctavoProgressSyncStore.MutationResult.UPDATED) {
            showProgressMutationFailure(
                staged,
                "The presented progress display could not be staged.",
                () -> retryReceiptProgressMutation(receipt.choice));
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending == null || pending.targetDisplay() != receipt.choice) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The staged progress display could not be verified.",
                this::reloadProgressSyncState);
            return;
        }
        progressSyncPending = pending;
        completePendingProgressForward(
            pending, receipt,
            progressStore.loadStatus()
                == OctavoProgressStore.LoadStatus.CURRENT);
    }

    private void stageObservedPresentedProgress(
        OctavoSurfaceView.ProgressPresentationReceipt receipt,
        OctavoProgressDisplay finalizedOrigin) {
        if (!progressReceiptIsCurrent(receipt)
            || finalizedOrigin == null
            || finalizedOrigin == receipt.choice) {
            return;
        }
        OctavoProgressSyncStore.MutationResult staged =
            progressSyncStore.stageLocalApply(
                finalizedOrigin, receipt.choice);
        if (staged != OctavoProgressSyncStore.MutationResult.UPDATED) {
            showProgressMutationFailure(
                staged,
                "The presented progress display could not be staged.",
                () -> retryReceiptProgressMutation(receipt.choice));
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending == null || pending.targetDisplay() != receipt.choice) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The staged progress display could not be verified.",
                this::reloadProgressSyncState);
            return;
        }
        progressSyncPending = pending;
        completePendingProgressForward(pending, receipt, false);
    }

    private void retryReceiptProgressMutation(
        OctavoProgressDisplay expectedChoice) {
        progressSyncAwaitingExplicitRetry = false;
        clearConsumedProgressReceipt();
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null || receipt.choice != expectedChoice) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The exact progress display is no longer confirmed on "
                    + "screen.",
                this::reloadProgressSyncState);
            return;
        }
        processProgressPresentationReceipt();
    }

    private void saveCanonicalProgressWithoutLaneAdvance(
        OctavoSurfaceView.ProgressPresentationReceipt receipt) {
        if (!progressReceiptIsCurrent(receipt)) {
            return;
        }
        OctavoProgressSyncStore.O8pgProof proof =
            saveExactProgressForSync(receipt.choice);
        if (proof == null) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The presented progress display could not be saved. "
                    + "Retry is safe.",
                () -> retrySaveCanonicalProgress(receipt.choice));
            return;
        }
        finishProgressConvergence(receipt);
    }

    private void retrySaveCanonicalProgress(
        OctavoProgressDisplay expectedChoice) {
        progressSyncAwaitingExplicitRetry = false;
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null || receipt.choice != expectedChoice) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The exact progress display is no longer confirmed on "
                    + "screen.",
                this::reloadProgressSyncState);
            return;
        }
        saveCanonicalProgressWithoutLaneAdvance(receipt);
    }

    private void completePendingProgressForward(
        OctavoProgressSyncStore.Pending expected,
        OctavoSurfaceView.ProgressPresentationReceipt receipt,
        boolean allowCanonicalLoadProof) {
        OctavoProgressSyncStore.Pending current =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (!progressReceiptIsCurrent(receipt)
            || expected == null || current == null
            || !expected.sameIdentity(current)
            || current.direction
               != OctavoProgressSyncStore.PendingDirection.FORWARD
            || receipt.choice != expected.targetDisplay()) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The exact pending progress display is not currently "
                    + "confirmed on screen.",
                this::retryPendingProgressForward);
            return;
        }
        if (progressSyncOriginSpineIndex >= 0
            && (receipt.anchorSpineIndex != progressSyncOriginSpineIndex
                || receipt.anchorByteOffset
                   != progressSyncOriginByteOffset)) {
            beginPendingProgressRollback(
                expected,
                "The reading place changed while applying the display. "
                    + "Restoring your earlier display.");
            return;
        }

        OctavoProgressSyncStore.O8pgProof proof = null;
        if (allowCanonicalLoadProof
            && progressStore.loadStatus()
               == OctavoProgressStore.LoadStatus.CURRENT
            && progressStore.hasCanonicalCurrentRecord(receipt.choice)) {
            proof = OctavoProgressSyncStore.O8pgProof.CANONICAL_V1_LOAD;
        }
        if (proof == null) {
            proof = saveExactProgressForSync(receipt.choice);
        }
        if (proof == null) {
            progressSyncPending = expected;
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The display appeared, but its local record could not be "
                    + "saved. Retry is safe.",
                this::retryPendingProgressForward);
            return;
        }
        OctavoProgressSyncStore.MutationResult completed =
            progressSyncStore.completePending(
                expected, receipt.choice, receipt.choice, proof);
        if (completed == OctavoProgressSyncStore.MutationResult.CONFLICT
            && expected.hasOriginLane) {
            beginPendingProgressRollback(
                expected,
                "The other device changed while its display was being "
                    + "applied. Restoring your display.");
            return;
        }
        if (!completed.succeeded()) {
            showProgressMutationFailure(
                completed,
                "The durable progress-display confirmation could not be "
                    + "finished.",
                this::retryPendingProgressForward);
            return;
        }
        progressSyncPending = null;
        progressSyncPendingLoaded = false;
        progressSyncRollbackRequested = false;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncAbandonAfterReload = false;
        progressSyncRetryDescriptor = null;
        progressSyncOriginSpineIndex = -1;
        progressSyncOriginByteOffset = -1;
        finishProgressConvergence(receipt);
    }

    private OctavoProgressSyncStore.O8pgProof saveExactProgressForSync(
        OctavoProgressDisplay target) {
        if (progressSyncO8pgFutureBlocked
            || progressStore.loadStatus()
               == OctavoProgressStore.LoadStatus.FUTURE) {
            return null;
        }
        boolean canonicalBefore =
            progressStore.hasCanonicalCurrentRecord(target);
        if (progressStore.save(target)) {
            return OctavoProgressSyncStore.O8pgProof
                .CURRENT_PROCESS_ATOMIC_SAVE;
        }
        if (progressStore.loadStatus()
            == OctavoProgressStore.LoadStatus.FUTURE) {
            progressSyncO8pgFutureBlocked = true;
            return null;
        }
        if (!canonicalBefore
            && progressStore.hasCanonicalCurrentRecord(target)) {
            return OctavoProgressSyncStore.O8pgProof
                .CURRENT_PROCESS_RECONCILED_AFTER_UNCERTAIN_SAVE;
        }
        return null;
    }

    private void finishProgressConvergence(
        OctavoSurfaceView.ProgressPresentationReceipt receipt) {
        if (!progressReceiptIsCurrent(receipt)
            || !progressStore.hasCanonicalCurrentRecord(receipt.choice)) {
            return;
        }
        OctavoProgressSyncStore.MutationResult converged =
            progressSyncStore.recordConverged(receipt.choice);
        if (!converged.succeeded()) {
            showProgressMutationFailure(
                converged,
                "Matching progress-display candidates could not be "
                    + "recorded.",
                () -> retryFinishProgressConvergence(receipt.choice));
            return;
        }
        progressDisplay = receipt.choice;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncRetry = null;
        if (progressSyncRetryDescriptor != null
            && !progressSyncRetryDescriptor.candidateAction()
            && !progressSyncRetryDescriptor.localStageAction()) {
            progressSyncRetryDescriptor = null;
        }
        boolean retainChoice = progressSyncCandidate != null
            && progressSyncCandidateIsCurrent(
                progressSyncCandidate, receipt.choice);
        boolean promptWasVisible = progressSyncPrompt != null;
        if (retainChoice) {
            ensureProgressSyncChoicePrompt(
                receipt.choice,
                progressSyncCandidate.targetDisplay(),
                progressSyncCandidate);
        } else {
            closeProgressSyncPrompt(false);
        }
        if (initializeProgressSyncReview() && !retainChoice) {
            drainDeferredSyncPrompts();
        }
        if (promptWasVisible
            && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null) {
            restoreProgressSyncFocusAfterClose();
        }
    }

    private void retryFinishProgressConvergence(
        OctavoProgressDisplay expectedChoice) {
        progressSyncAwaitingExplicitRetry = false;
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null || receipt.choice != expectedChoice) {
            reloadProgressSyncState();
            return;
        }
        finishProgressConvergence(receipt);
    }

    private OctavoSurfaceView.ProgressPresentationReceipt
        currentProgressReceipt() {
        if (surfaceView == null) {
            return null;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            surfaceView.currentProgressPresentationReceipt();
        if (receipt != null) {
            latestProgressReceipt = receipt;
            progressReceiptSurface = surfaceView;
        }
        return receipt;
    }

    private boolean progressReceiptIsCurrent(
        OctavoSurfaceView.ProgressPresentationReceipt receipt) {
        if (receipt == null || surfaceView == null
            || progressReceiptSurface != surfaceView) {
            return false;
        }
        OctavoSurfaceView.ProgressPresentationReceipt current =
            surfaceView.currentProgressPresentationReceipt();
        return current != null
            && current.choice == receipt.choice
            && current.progressGeneration == receipt.progressGeneration
            && current.frameCount == receipt.frameCount
            && current.anchorSpineIndex == receipt.anchorSpineIndex
            && current.anchorByteOffset == receipt.anchorByteOffset;
    }

    private void clearConsumedProgressReceipt() {
        consumedProgressReceiptSurface = null;
        consumedProgressReceiptChoice = null;
        consumedProgressReceiptGeneration = -1;
        consumedProgressReceiptFrame = -1;
    }

    private boolean initializeProgressSyncReview() {
        if (progressSyncReviewInitialized) {
            return true;
        }
        if (progressSyncStore == null
            || progressSyncO8pgFutureBlocked
            || progressSyncStore.pending() != null) {
            return false;
        }
        boolean advanceExplicitOpen = progressSyncReviewPending;
        long before = progressSyncStore.reviewEpoch();
        OctavoProgressSyncStore.MutationResult result =
            progressSyncStore.beginReviewEpoch(advanceExplicitOpen);
        if (!result.succeeded()) {
            if (advanceExplicitOpen
                && (result == OctavoProgressSyncStore.MutationResult
                        .PUBLISH_UNCERTAIN
                    || result == OctavoProgressSyncStore.MutationResult
                        .BLOCKED)) {
                progressSyncReviewEpochBeforeRetry = before;
                progressSyncRollbackEpochReconciliation = false;
                showProgressSyncFailure(
                    "Progress display update needs attention",
                    progressSyncStore.lastError(),
                    this::reconcileProgressSyncReviewEpoch);
                return false;
            }
            showProgressMutationFailure(
                result,
                "Progress-display review could not be initialized.",
                () -> {
                    if (initializeProgressSyncReview()) {
                        closeProgressSyncPrompt(true);
                        considerProgressSyncCandidate();
                    }
                });
            return false;
        }
        progressSyncReviewInitialized = true;
        progressSyncReviewPending = false;
        progressSyncReviewEpochBeforeRetry = -1;
        progressSyncRollbackEpochReconciliation = false;
        return true;
    }

    private void reconcileProgressSyncReviewEpoch() {
        if (progressSyncStore == null
            || progressSyncReviewEpochBeforeRetry < 0) {
            reloadProgressSyncState();
            return;
        }
        long before = progressSyncReviewEpochBeforeRetry;
        OctavoProgressSyncStore.LoadStatus status =
            progressSyncStore.load();
        if (status != OctavoProgressSyncStore.LoadStatus.LOADED
            && status
               != OctavoProgressSyncStore.LoadStatus.MISSING_CREATED) {
            reportProgressSyncLoadStatus(
                status, progressStore.loadStatus());
            return;
        }
        long expected = before == Long.MAX_VALUE
            ? Long.MAX_VALUE : before + 1;
        long loaded = progressSyncStore.reviewEpoch();
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        progressSyncAwaitingExplicitRetry = false;
        if (progressSyncRollbackEpochReconciliation) {
            if (pending != null && loaded == before) {
                progressSyncReviewEpochBeforeRetry = -1;
                progressSyncRollbackEpochReconciliation = false;
                progressSyncPendingLoaded = true;
                retryPendingProgressRollback(pending);
                return;
            }
            if (pending == null && loaded == expected) {
                progressSyncPending = null;
                progressSyncPendingLoaded = false;
                progressSyncRollbackRequested = false;
                progressSyncAbandonAfterReload = false;
                progressSyncRetryDescriptor = null;
                progressSyncReviewPending = false;
                progressSyncReviewInitialized = true;
                progressSyncReviewEpochBeforeRetry = -1;
                progressSyncRollbackEpochReconciliation = false;
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    currentProgressReceipt();
                if (receipt != null
                    && progressStore.hasCanonicalCurrentRecord(
                        receipt.choice)) {
                    finishProgressConvergence(receipt);
                }
                return;
            }
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The rollback state and review epoch disagree; automatic "
                    + "recovery is blocked.",
                this::reconcileProgressSyncReviewEpoch);
            return;
        }
        if (pending != null) {
            progressSyncPendingLoaded = true;
            showLoadedPendingProgressRetry(
                pending, currentProgressReceipt());
            return;
        }
        if (loaded == expected && (loaded != before
                                   || before == Long.MAX_VALUE)) {
            progressSyncReviewInitialized = true;
            progressSyncReviewPending = false;
            progressSyncReviewEpochBeforeRetry = -1;
            closeProgressSyncPrompt(false);
            drainDeferredSyncPrompts();
            return;
        }
        if (loaded == before) {
            progressSyncReviewEpochBeforeRetry = -1;
            progressSyncAwaitingExplicitRetry = false;
            initializeProgressSyncReview();
            return;
        }
        showProgressSyncFailure(
            "Progress display update needs attention",
            "The progress review epoch changed unexpectedly.",
            this::reconcileProgressSyncReviewEpoch);
    }

    private void showLoadedPendingProgressRetry(
        OctavoProgressSyncStore.Pending pending,
        OctavoSurfaceView.ProgressPresentationReceipt receipt) {
        progressSyncPending = pending;
        progressSyncPendingLoaded = true;
        if (progressSyncAbandonAfterReload) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The interrupted dismissal must be reconciled before the "
                    + "progress display can continue.",
                this::reloadProgressSyncForAbandon);
            return;
        }
        progressSyncRollbackRequested = pending.direction
            == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
        OctavoProgressDisplay canonical =
            progressStore.loadStatus()
                    == OctavoProgressStore.LoadStatus.CURRENT
                ? progressStore.current() : null;
        OctavoProgressSyncStore.PendingRecovery recovery =
            progressSyncStore.pendingRecovery(canonical);
        String message;
        if (pending.direction
            == OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
            message = recovery
                    == OctavoProgressSyncStore.PendingRecovery.ORIGIN_DURABLE
                ? "Your earlier display is saved, but rollback "
                    + "confirmation still needs to finish."
                : "A progress-display rollback was interrupted. Retry is "
                    + "required to restore it safely.";
        } else if (recovery
                   == OctavoProgressSyncStore.PendingRecovery.TARGET_DURABLE) {
            message = "The display and its local record are present, but "
                + "synchronization confirmation still needs to finish.";
        } else if (recovery
                   == OctavoProgressSyncStore.PendingRecovery.ORIGIN_DURABLE) {
            message = "A display update was interrupted before it was "
                + "shown. Retry is safe; your saved display remains.";
        } else {
            message = "A display update was interrupted and its exact "
                + "durable state needs to be reconciled. Retry is safe.";
        }
        showProgressSyncFailure(
            "Progress display update needs attention",
            message,
            pending.direction
                == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                ? () -> retryPendingProgressRollback(pending)
                : this::retryPendingProgressForward);
    }

    private void retryPendingProgressForward() {
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (pending == null) {
            reloadProgressSyncState();
            return;
        }
        if (pending.direction
            == OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
            retryPendingProgressRollback(pending);
            return;
        }
        progressSyncPending = pending;
        progressSyncRollbackRequested = false;
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.pending(
                PROGRESS_RETRY_FORWARD, pending);
        boolean allowCanonicalLoadProof = progressSyncPendingLoaded;
        progressSyncPendingLoaded = false;
        progressSyncAwaitingExplicitRetry = false;
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt != null
            && receipt.choice == pending.targetDisplay()) {
            completePendingProgressForward(
                pending, receipt, allowCanonicalLoadProof);
            return;
        }
        requestPendingProgressTarget(
            pending,
            "Applying the pending progress display. Waiting for the "
                + "reader to confirm it on screen.");
    }

    private void requestPendingProgressTarget(
        OctavoProgressSyncStore.Pending pending,
        String workingMessage) {
        if (pending == null || surfaceView == null) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The reader is unavailable. Reopen the book and Retry.",
                this::retryPendingProgressForward);
            return;
        }
        ensureProgressSyncPendingPrompt(pending);
        if (progressSyncPrompt != null) {
            progressSyncPrompt.showWorking(workingMessage);
        }
        int result = surfaceView.requestProgressDisplay(
            pending.targetDisplay());
        if (result == OctavoNative.NAVIGATION_ALREADY_PRESENTED) {
            OctavoSurfaceView.ProgressPresentationReceipt receipt =
                currentProgressReceipt();
            if (receipt != null
                && receipt.choice == pending.targetDisplay()) {
                completePendingProgressForward(pending, receipt, false);
                return;
            }
        } else if (result == OctavoNative.NAVIGATION_ACCEPTED) {
            return;
        }
        showProgressSyncFailure(
            "Progress display update needs attention",
            result == OctavoNative.NAVIGATION_BUSY
                ? "The reader is finishing another change. Retry when it "
                    + "settles."
                : "The progress display could not be presented. Retry is "
                    + "safe.",
            this::retryPendingProgressForward);
    }

    private void beginPendingProgressRollback(
        OctavoProgressSyncStore.Pending expected,
        String status) {
        if (expected == null || !expected.hasOriginLane) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "These first synchronized progress settings have no "
                    + "earlier display to restore. Retry the update.",
                this::retryPendingProgressForward);
            return;
        }
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.pending(
                expected.direction
                    == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                    ? PROGRESS_RETRY_ROLLBACK
                    : PROGRESS_RETRY_FORWARD,
                expected);
        OctavoProgressSyncStore.MutationResult requested =
            progressSyncStore.requestRollback(expected);
        if (!requested.succeeded()) {
            if (requested
                    == OctavoProgressSyncStore.MutationResult
                        .PUBLISH_UNCERTAIN
                || requested
                    == OctavoProgressSyncStore.MutationResult.BLOCKED) {
                progressSyncAbandonAfterReload = true;
                showProgressSyncFailure(
                    "Progress display update needs attention",
                    progressSyncStore.lastError(),
                    this::reloadProgressSyncForAbandon);
                return;
            }
            showProgressMutationFailure(
                requested,
                "Rollback could not be staged.",
                () -> beginPendingProgressRollback(expected, status));
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending == null
            || pending.direction
               != OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The staged rollback could not be verified.",
                this::reloadProgressSyncState);
            return;
        }
        progressSyncPending = pending;
        progressSyncPendingLoaded = false;
        progressSyncRollbackRequested = true;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.pending(
                PROGRESS_RETRY_ROLLBACK, pending);
        retryPendingProgressRollback(pending, status);
    }

    private void retryPendingProgressRollback(
        OctavoProgressSyncStore.Pending expected) {
        retryPendingProgressRollback(
            expected, "Restoring your progress display.");
    }

    private void retryPendingProgressRollback(
        OctavoProgressSyncStore.Pending expected,
        String status) {
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (expected == null || pending == null
            || !expected.sameIdentity(pending)
            || pending.direction
               != OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
            reloadProgressSyncState();
            return;
        }
        progressSyncPending = pending;
        progressSyncRollbackRequested = true;
        progressSyncPendingLoaded = false;
        progressSyncAwaitingExplicitRetry = false;
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt != null
            && receipt.choice == pending.originDisplay()) {
            finishPendingProgressRollback(pending, receipt);
            return;
        }
        if (surfaceView == null) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The reader is unavailable. Reopen the book and Retry.",
                () -> retryPendingProgressRollback(pending));
            return;
        }
        ensureProgressSyncPendingPrompt(pending);
        if (progressSyncPrompt != null) {
            progressSyncPrompt.showWorking(status);
        }
        int result = surfaceView.requestProgressDisplay(
            pending.originDisplay());
        if (result == OctavoNative.NAVIGATION_ALREADY_PRESENTED) {
            receipt = currentProgressReceipt();
            if (receipt != null
                && receipt.choice == pending.originDisplay()) {
                finishPendingProgressRollback(pending, receipt);
                return;
            }
        } else if (result == OctavoNative.NAVIGATION_ACCEPTED) {
            return;
        }
        showProgressSyncFailure(
            "Progress display update needs attention",
            result == OctavoNative.NAVIGATION_BUSY
                ? "The reader is finishing another change. Retry when it "
                    + "settles."
                : "Your earlier progress display could not be restored. "
                    + "Retry is safe.",
            () -> retryPendingProgressRollback(pending));
    }

    private void finishPendingProgressRollback(
        OctavoProgressSyncStore.Pending expected,
        OctavoSurfaceView.ProgressPresentationReceipt receipt) {
        OctavoProgressSyncStore.Pending current =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (!progressReceiptIsCurrent(receipt)
            || expected == null || current == null
            || !expected.sameIdentity(current)
            || current.direction
               != OctavoProgressSyncStore.PendingDirection.ROLLBACK
            || receipt.choice != current.originDisplay()) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The earlier progress display has not been confirmed on "
                    + "screen.",
                () -> retryPendingProgressRollback(expected));
            return;
        }
        OctavoProgressSyncStore.O8pgProof proof =
            saveExactProgressForSync(receipt.choice);
        if (proof == null) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "Your display was restored on screen, but its local record "
                    + "could not be saved. Retry is safe.",
                () -> retryPendingProgressRollback(expected));
            return;
        }
        boolean advanceDeferredEpoch = progressSyncReviewPending;
        long before = progressSyncStore.reviewEpoch();
        OctavoProgressSyncStore.MutationResult dismissed =
            progressSyncStore.dismissPendingAfterRollback(
                expected, receipt.choice, receipt.choice, proof,
                advanceDeferredEpoch);
        if (!dismissed.succeeded()) {
            if (dismissed
                    == OctavoProgressSyncStore.MutationResult.PUBLISH_UNCERTAIN
                || dismissed
                    == OctavoProgressSyncStore.MutationResult.BLOCKED) {
                progressSyncReviewEpochBeforeRetry =
                    advanceDeferredEpoch ? before : -1;
                progressSyncRollbackEpochReconciliation =
                    advanceDeferredEpoch;
                showProgressSyncFailure(
                    "Progress display update needs attention",
                    progressSyncStore.lastError(),
                    advanceDeferredEpoch
                        ? this::reconcileProgressSyncReviewEpoch
                        : this::reloadProgressSyncState);
            } else {
                showProgressMutationFailure(
                    dismissed,
                    "The restored progress display could not be durably "
                        + "confirmed.",
                    () -> retryPendingProgressRollback(expected));
            }
            return;
        }
        progressSyncPending = null;
        progressSyncPendingLoaded = false;
        progressSyncRollbackRequested = false;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncAbandonAfterReload = false;
        progressSyncRetryDescriptor = null;
        progressSyncOriginSpineIndex = -1;
        progressSyncOriginByteOffset = -1;
        if (advanceDeferredEpoch) {
            progressSyncReviewPending = false;
            progressSyncReviewInitialized = true;
        }
        progressSyncReviewEpochBeforeRetry = -1;
        progressSyncRollbackEpochReconciliation = false;
        closeProgressSyncPrompt(true);
        finishProgressConvergence(receipt);
    }

    private void reloadProgressSyncForAbandon() {
        if (progressSyncStore == null) {
            return;
        }
        OctavoProgressSyncStore.LoadStatus status =
            progressSyncStore.load();
        if (status != OctavoProgressSyncStore.LoadStatus.LOADED
            && status
               != OctavoProgressSyncStore.LoadStatus.MISSING_CREATED) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                progressSyncStore.lastError(),
                this::reloadProgressSyncForAbandon);
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        ProgressSyncRetryDescriptor descriptor =
            progressSyncRetryDescriptor;
        if (progressSyncAbandonAfterReload && pending == null
            && descriptor != null && descriptor.pendingAction()) {
            OctavoProgressPortable.Lane local =
                progressSyncStore.localLane();
            OctavoSurfaceView.ProgressPresentationReceipt receipt =
                currentProgressReceipt();
            if (local != null
                && local.sequence == descriptor.originSequence
                && receipt != null
                && receipt.choice == local.choice.toDisplay()
                && progressStore.hasCanonicalCurrentRecord(
                    receipt.choice)) {
                progressSyncPending = null;
                progressSyncPendingLoaded = false;
                progressSyncRollbackRequested = false;
                progressSyncAbandonAfterReload = false;
                progressSyncRetryDescriptor = null;
                finishProgressConvergence(receipt);
                return;
            }
        }
        if (!progressSyncAbandonAfterReload || pending == null
            || descriptor == null
            || !descriptor.matchesPendingIdentity(pending)) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The uncertain rollback could not be matched to its exact "
                    + "pending progress transaction.",
                this::reloadProgressSyncForAbandon);
            return;
        }
        progressSyncPending = pending;
        progressSyncPendingLoaded = false;
        progressSyncAwaitingExplicitRetry = false;
        if (pending.direction
            == OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
            progressSyncRollbackRequested = true;
            progressSyncRetryDescriptor =
                ProgressSyncRetryDescriptor.pending(
                    PROGRESS_RETRY_ROLLBACK, pending);
            retryPendingProgressRollback(pending);
            return;
        }
        if (pending.direction
            == OctavoProgressSyncStore.PendingDirection.FORWARD) {
            beginPendingProgressRollback(
                pending,
                "Restoring your progress display before dismissing the "
                    + "interrupted update.");
            return;
        }
        showProgressSyncFailure(
            "Progress display update needs attention",
            "The pending progress transaction has an invalid direction.",
            this::reloadProgressSyncForAbandon);
    }

    private void reloadProgressSyncForLocalStage() {
        ProgressSyncRetryDescriptor descriptor =
            progressSyncRetryDescriptor;
        if (descriptor == null || !descriptor.localStageAction()
            || progressSyncStore == null || progressStore == null) {
            reloadProgressSyncState();
            return;
        }
        progressStore.load();
        OctavoProgressStore.LoadStatus localStatus =
            progressStore.loadStatus();
        progressSyncO8pgFutureBlocked =
            localStatus == OctavoProgressStore.LoadStatus.FUTURE;
        OctavoProgressSyncStore.LoadStatus status =
            progressSyncStore.load();
        if (progressSyncO8pgFutureBlocked
            || (status != OctavoProgressSyncStore.LoadStatus.LOADED
                && status != OctavoProgressSyncStore.LoadStatus
                    .MISSING_CREATED)) {
            reportProgressSyncLoadStatus(status, localStatus);
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending != null) {
            if (!localStageMatchesPending(descriptor, pending)) {
                showProgressSyncFailure(
                    "Progress display update needs attention",
                    "The uncertain local display stage resolved to a "
                        + "different pending transaction.",
                    this::reloadProgressSyncForLocalStage);
                return;
            }
            progressSyncRetryDescriptor =
                ProgressSyncRetryDescriptor.pending(
                    PROGRESS_RETRY_FORWARD, pending);
            progressSyncPending = pending;
            progressSyncPendingLoaded = true;
            showLoadedPendingProgressRetry(
                pending, currentProgressReceipt());
            return;
        }
        OctavoProgressPortable.Lane local =
            progressSyncStore.localLane();
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        boolean exactPrior = local != null
            && local.sequence == descriptor.originSequence
            && local.choice.toDisplay()
                == descriptor.localStageOriginDisplay()
            && receipt != null
            && receipt.choice == descriptor.localStageOriginDisplay()
            && progressStore.hasCanonicalCurrentRecord(receipt.choice);
        if (!exactPrior) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The uncertain local display stage could not be matched "
                    + "to either exact durable outcome.",
                this::reloadProgressSyncForLocalStage);
            return;
        }
        progressSyncAwaitingExplicitRetry = true;
        showProgressSyncFailure(
            "Progress display update needs attention",
            "The progress-display stage did not publish. Retry will stage "
                + "the same display again.",
            this::retryUnstagedLocalProgressApply);
    }

    private void retryUnstagedLocalProgressApply() {
        ProgressSyncRetryDescriptor descriptor =
            progressSyncRetryDescriptor;
        if (descriptor == null || !descriptor.localStageAction()) {
            reloadProgressSyncState();
            return;
        }
        OctavoProgressPortable.Lane local =
            progressSyncStore == null ? null
                : progressSyncStore.localLane();
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (local == null || receipt == null
            || local.sequence != descriptor.originSequence
            || local.choice.toDisplay()
                != descriptor.localStageOriginDisplay()
            || receipt.choice != descriptor.localStageOriginDisplay()
            || !progressStore.hasCanonicalCurrentRecord(receipt.choice)) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The exact local progress origin is no longer current.",
                this::reloadProgressSyncForLocalStage);
            return;
        }
        progressSyncAwaitingExplicitRetry = false;
        requestProgressDisplayFromNavigation(
            descriptor.localStageTargetDisplay());
    }

    private static boolean localStageMatchesPending(
        ProgressSyncRetryDescriptor descriptor,
        OctavoProgressSyncStore.Pending pending) {
        return descriptor != null && descriptor.localStageAction()
            && pending != null
            && pending.kind == OctavoProgressSyncStore.PendingKind.LOCAL
            && pending.direction
               == OctavoProgressSyncStore.PendingDirection.FORWARD
            && pending.hasOriginLane
            && pending.originLocalSequence == descriptor.originSequence
            && pending.originDisplay()
               == descriptor.localStageOriginDisplay()
            && pending.targetDisplay()
               == descriptor.localStageTargetDisplay();
    }

    private void reloadProgressSyncState() {
        if (progressSyncStore == null || progressStore == null) {
            return;
        }
        if (progressSyncAbandonAfterReload) {
            reloadProgressSyncForAbandon();
            return;
        }
        if (progressSyncRetryDescriptor != null
            && progressSyncRetryDescriptor.localStageAction()) {
            reloadProgressSyncForLocalStage();
            return;
        }
        OctavoProgressDisplay loadedDisplay = progressStore.load();
        OctavoProgressStore.LoadStatus localStatus =
            progressStore.loadStatus();
        progressSyncO8pgFutureBlocked =
            localStatus == OctavoProgressStore.LoadStatus.FUTURE;
        OctavoProgressSyncStore.LoadStatus status =
            progressSyncStore.load();
        progressSyncPending = progressSyncStore.pending();
        progressSyncPendingLoaded = progressSyncPending != null;
        progressSyncRollbackRequested = progressSyncPending != null
            && progressSyncPending.direction
               == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
        progressSyncAwaitingExplicitRetry = false;
        clearConsumedProgressReceipt();
        if (progressSyncO8pgFutureBlocked
            || (status != OctavoProgressSyncStore.LoadStatus.LOADED
                && status != OctavoProgressSyncStore.LoadStatus
                    .MISSING_CREATED)) {
            reportProgressSyncLoadStatus(status, localStatus);
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (progressSyncPending != null) {
            showLoadedPendingProgressRetry(
                progressSyncPending, receipt);
            return;
        }
        if (receipt == null) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "A settled reader frame is required before progress "
                    + "synchronization can continue.",
                this::reloadProgressSyncState);
            return;
        }
        if (localStatus == OctavoProgressStore.LoadStatus.CURRENT
            && loadedDisplay != receipt.choice) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The saved and presented progress displays disagree. "
                    + "Reopen the reader before retrying.",
                this::reloadProgressSyncState);
            return;
        }
        processProgressPresentationReceipt();
    }

    private void requestProgressDisplayFromNavigation(
        OctavoProgressDisplay requested) {
        if (requested == null || surfaceView == null
            || progressSyncStore == null || progressStore == null) {
            requestNavigation(
                OctavoNative.NAVIGATION_UNAVAILABLE,
                "Updating the reader progress display.");
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        OctavoProgressPortable.Lane local =
            progressSyncStore.localLane();
        if (progressSyncO8pgFutureBlocked
            || progressSyncAwaitingExplicitRetry
            || progressSyncStore.pending() != null
            || receipt == null || local == null
            || local.choice.toDisplay() != receipt.choice
            || !progressStore.hasCanonicalCurrentRecord(receipt.choice)) {
            closeNavigationPanel(false);
            showProgressSyncFailure(
                "Progress display update needs attention",
                "Finish the retained progress synchronization Retry "
                    + "before changing this display.",
                progressSyncStore.pending() == null
                    ? this::reloadProgressSyncState
                    : this::retryPendingProgressForward);
            return;
        }
        if (requested == receipt.choice) {
            requestNavigation(
                OctavoNative.NAVIGATION_ALREADY_PRESENTED,
                "The reader already uses this progress display.");
            return;
        }
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.localStage(local, requested);
        OctavoProgressSyncStore.MutationResult staged =
            progressSyncStore.stageLocalApply(
                receipt.choice, requested);
        if (staged != OctavoProgressSyncStore.MutationResult.UPDATED) {
            closeNavigationPanel(false);
            showProgressMutationFailure(
                staged,
                "The progress display change could not be staged.",
                () -> requestProgressDisplayAfterRetry(requested));
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending == null || pending.targetDisplay() != requested) {
            closeNavigationPanel(false);
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The staged progress display could not be verified.",
                this::reloadProgressSyncState);
            return;
        }
        progressSyncPending = pending;
        progressSyncPendingLoaded = false;
        progressSyncRollbackRequested = false;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.pending(
                PROGRESS_RETRY_FORWARD, pending);
        progressSyncOriginSpineIndex = receipt.anchorSpineIndex;
        progressSyncOriginByteOffset = receipt.anchorByteOffset;
        closeNavigationPanel(false);
        requestPendingProgressTarget(
            pending,
            "Updating the reader progress display. Waiting for the reader "
                + "to confirm it on screen.");
    }

    private void requestProgressDisplayAfterRetry(
        OctavoProgressDisplay requested) {
        progressSyncAwaitingExplicitRetry = false;
        closeProgressSyncPrompt(false);
        openNavigationPanel();
        requestProgressDisplayFromNavigation(requested);
    }

    private void drainDeferredSyncPrompts() {
        if (!activityResumed) {
            return;
        }
        if (appearanceSyncPrompt == null
            && appearanceSyncAwaitingExplicitRetry) {
            presentRetainedAppearanceSyncFailure();
        }
        if (appearanceSyncPrompt != null) {
            return;
        }
        if (appearanceSyncReviewInitialized) {
            considerAppearanceSyncCandidate();
        }
        if (appearanceSyncPrompt != null) {
            return;
        }
        if (readingPositionPrompt == null
            && readingPositionReviewInitialized) {
            considerReadingPositionCandidate();
        }
        if (readingPositionPrompt != null) {
            return;
        }
        if (progressSyncPrompt == null
            && progressSyncAwaitingExplicitRetry) {
            presentRetainedProgressSyncFailure();
        }
        if (progressSyncPrompt != null) {
            return;
        }
        if (progressSyncReviewInitialized) {
            considerProgressSyncCandidate();
        }
    }

    private void considerProgressSyncCandidate() {
        if (progressSyncAwaitingExplicitRetry
            || progressSyncO8pgFutureBlocked) {
            presentRetainedProgressSyncFailure();
            return;
        }
        if (!activityResumed || !progressSyncReviewInitialized
            || surfaceView == null || progressSyncStore == null
            || progressSyncStore.pending() != null
            || progressSyncPrompt != null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || appearancePanel != null || navigationPanel != null
            || searchPanel != null || bookmarksPanel != null
            || surfaceView.hasSelectionForAccessibility()) {
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null
            || !progressStore.hasCanonicalCurrentRecord(receipt.choice)) {
            return;
        }
        List<OctavoProgressSyncStore.Candidate> candidates =
            progressSyncStore.reviewCandidates(receipt.choice);
        if (candidates.isEmpty()) {
            return;
        }
        ProgressSyncRetryDescriptor retained =
            progressSyncRetryDescriptor;
        if (retained != null && retained.candidateAction()) {
            OctavoProgressSyncStore.Candidate retryCandidate = null;
            for (OctavoProgressSyncStore.Candidate current : candidates) {
                if (retained.matches(current)) {
                    retryCandidate = current;
                    break;
                }
            }
            if (retryCandidate == null) {
                progressSyncRetryDescriptor = null;
            } else {
                progressSyncCandidate = retryCandidate;
                if (ensureProgressSyncChoicePrompt(
                        receipt.choice,
                        retryCandidate.targetDisplay(),
                        retryCandidate)) {
                    showProgressSyncFailure(
                        "Progress display update needs attention",
                        "The earlier choice was not saved. Retry is safe; "
                            + "the reading place has not moved.",
                        progressCandidateRetry(retained.action));
                }
                return;
            }
        }
        OctavoProgressSyncStore.Candidate candidate = candidates.get(0);
        progressSyncCandidate = candidate;
        if (!ensureProgressSyncChoicePrompt(
                receipt.choice, candidate.targetDisplay(), candidate)) {
            progressSyncCandidate = null;
        }
    }

    private boolean progressSyncCandidateIsCurrent(
        OctavoProgressSyncStore.Candidate candidate,
        OctavoProgressDisplay exactPresentedOrigin) {
        if (candidate == null || exactPresentedOrigin == null
            || progressSyncStore == null
            || progressSyncStore.pending() != null) {
            return false;
        }
        for (OctavoProgressSyncStore.Candidate current
                : progressSyncStore.reviewCandidates(
                    exactPresentedOrigin)) {
            if (candidate.sameIdentity(current)) {
                return true;
            }
        }
        return false;
    }

    private void useProgressSyncCandidate() {
        useProgressSyncCandidate(progressSyncCandidate);
    }

    private void useProgressSyncCandidate(
        OctavoProgressSyncStore.Candidate expected) {
        OctavoProgressSyncStore.Candidate candidate =
            progressSyncCandidate;
        if (expected == null || candidate == null
            || !expected.sameIdentity(candidate)) {
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null
            || !progressSyncCandidateIsCurrent(
                candidate, receipt.choice)
            || receipt.choice != candidate.originDisplay()) {
            closeProgressSyncPrompt(false);
            considerProgressSyncCandidate();
            restoreProgressSyncFocusAfterClose();
            return;
        }
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.candidate(
                PROGRESS_RETRY_USE, candidate);
        OctavoProgressSyncStore.MutationResult staged =
            progressSyncStore.stageRemoteApply(
                candidate, receipt.choice);
        if (staged != OctavoProgressSyncStore.MutationResult.UPDATED) {
            showProgressMutationFailure(
                staged,
                "The other device's progress display could not be staged.",
                this::useProgressSyncCandidate);
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending == null
            || pending.targetDisplay() != candidate.targetDisplay()) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The staged progress display could not be verified.",
                this::reloadProgressSyncState);
            return;
        }
        progressSyncPending = pending;
        progressSyncPendingLoaded = false;
        progressSyncRollbackRequested = false;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.pending(
                PROGRESS_RETRY_FORWARD, pending);
        progressSyncCandidate = null;
        progressSyncOriginSpineIndex = receipt.anchorSpineIndex;
        progressSyncOriginByteOffset = receipt.anchorByteOffset;
        requestPendingProgressTarget(
            pending,
            "Applying the other device's progress display. Waiting for "
                + "the reader to confirm it on screen.");
    }

    private void keepCurrentProgressDisplay() {
        keepCurrentProgressDisplay(progressSyncCandidate);
    }

    private void keepCurrentProgressDisplay(
        OctavoProgressSyncStore.Candidate expected) {
        OctavoProgressSyncStore.Candidate candidate =
            progressSyncCandidate;
        if (expected == null || candidate == null
            || !expected.sameIdentity(candidate)) {
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null
            || !progressSyncCandidateIsCurrent(
                candidate, receipt.choice)
            || receipt.choice != candidate.originDisplay()) {
            closeProgressSyncPrompt(false);
            considerProgressSyncCandidate();
            restoreProgressSyncFocusAfterClose();
            return;
        }
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.candidate(
                PROGRESS_RETRY_KEEP, candidate);
        OctavoProgressSyncStore.MutationResult kept =
            progressSyncStore.keep(candidate, receipt.choice);
        if (!kept.succeeded()) {
            showProgressMutationFailure(
                kept,
                "Keep mine could not be saved.",
                this::keepCurrentProgressDisplay);
            return;
        }
        progressSyncRetryDescriptor = null;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncRetry = null;
        closeProgressSyncPrompt(false);
        drainDeferredSyncPrompts();
        if (appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null) {
            restoreProgressSyncFocusAfterClose();
        }
    }

    private Runnable progressCandidateRetry(int action) {
        switch (action) {
            case PROGRESS_RETRY_USE:
                return this::useProgressSyncCandidate;
            case PROGRESS_RETRY_KEEP:
                return this::keepCurrentProgressDisplay;
            case PROGRESS_RETRY_DISMISS:
                return this::dismissProgressSyncForBack;
            default:
                return this::reloadProgressSyncState;
        }
    }

    private void dismissProgressSyncForBack() {
        if (progressSyncO8pgFutureBlocked
            || (progressStore != null
                && progressStore.loadStatus()
                   == OctavoProgressStore.LoadStatus.FUTURE)) {
            // Back cannot turn a preserved newer O8PG into a rollback or a
            // durable Later decision. Keep the recovery surface in place and
            // require an explicit reload without touching either file or the
            // currently presented Surface choice.
            progressSyncO8pgFutureBlocked = true;
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The saved progress display was written by a newer "
                    + "version. Its bytes were preserved and "
                    + "synchronization is blocked.",
                this::reloadProgressSyncState);
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (pending != null) {
            if (surfaceView != null
                && surfaceView.hasNavigationPending()) {
                if (progressSyncPrompt != null) {
                    progressSyncPrompt.showWorking(
                        "Waiting for the progress display to settle.");
                }
                return;
            }
            if (!pending.hasOriginLane) {
                if (progressSyncPrompt != null) {
                    progressSyncPrompt.announceForAccessibility(
                        "Retry is required to finish the first progress "
                            + "display confirmation");
                }
                return;
            }
            if (pending.direction
                == OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
                retryPendingProgressRollback(pending);
            } else {
                beginPendingProgressRollback(
                    pending,
                    "Restoring your progress display before dismissing "
                        + "the interrupted update.");
            }
            return;
        }
        OctavoProgressSyncStore.Candidate candidate =
            progressSyncCandidate;
        if (candidate == null) {
            if (progressSyncAwaitingExplicitRetry
                || progressSyncO8pgFutureBlocked) {
                if (progressSyncPrompt != null) {
                    progressSyncPrompt.announceForAccessibility(
                        "Retry is required to finish the progress display "
                            + "update");
                }
                return;
            }
            closeProgressSyncPrompt(true);
            return;
        }
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt == null
            || !progressSyncCandidateIsCurrent(
                candidate, receipt.choice)
            || receipt.choice != candidate.originDisplay()) {
            closeProgressSyncPrompt(false);
            considerProgressSyncCandidate();
            restoreProgressSyncFocusAfterClose();
            return;
        }
        progressSyncRetryDescriptor =
            ProgressSyncRetryDescriptor.candidate(
                PROGRESS_RETRY_DISMISS, candidate);
        OctavoProgressSyncStore.MutationResult dismissed =
            progressSyncStore.dismiss(candidate, receipt.choice);
        if (!dismissed.succeeded()) {
            showProgressMutationFailure(
                dismissed,
                "Later could not be saved.",
                this::dismissProgressSyncForBack);
            return;
        }
        progressSyncRetryDescriptor = null;
        progressSyncAwaitingExplicitRetry = false;
        progressSyncRetry = null;
        closeProgressSyncPrompt(false);
        drainDeferredSyncPrompts();
        if (appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null) {
            restoreProgressSyncFocusAfterClose();
        }
    }

    private boolean ensureProgressSyncChoicePrompt(
        OctavoProgressDisplay presented,
        OctavoProgressDisplay remote,
        OctavoProgressSyncStore.Candidate expected) {
        if (readerRoot == null || surfaceView == null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || presented == null || remote == null
            || presented == remote
            || surfaceView.hasSelectionForAccessibility()) {
            return false;
        }
        if (progressSyncPrompt != null
            && progressSyncPromptCandidate != null
            && progressSyncPromptCandidate.sameIdentity(expected)) {
            try {
                progressSyncPrompt.updateChoices(presented, remote);
                return true;
            } catch (IllegalArgumentException exception) {
                closeProgressSyncPrompt(false);
            }
        } else if (progressSyncPrompt != null) {
            closeProgressSyncPrompt(false);
        }
        closeReaderPanelsForProgressSync();
        OctavoProgressSyncPrompt prompt;
        long generation = progressSyncPromptGeneration + 1;
        try {
            prompt = new OctavoProgressSyncPrompt(
                this, appearance, presented, remote,
                progressSyncPromptListener(expected, generation));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            showOpenFailure(
                "Progress-display confirmation is unavailable; reopen "
                    + "the book to retry");
            return false;
        }
        progressSyncPromptGeneration = generation;
        progressSyncPromptCandidate = expected;
        return installProgressSyncPrompt(prompt);
    }

    private boolean ensureProgressSyncPendingPrompt(
        OctavoProgressSyncStore.Pending pending) {
        if (pending == null || readerRoot == null || surfaceView == null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || surfaceView.hasSelectionForAccessibility()) {
            return false;
        }
        if (progressSyncPrompt != null) {
            return true;
        }
        closeReaderPanelsForProgressSync();
        OctavoProgressDisplay current = progressDisplay;
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt != null) {
            current = receipt.choice;
        }
        OctavoProgressDisplay other = current == pending.targetDisplay()
            ? pending.originDisplay() : pending.targetDisplay();
        OctavoProgressSyncPrompt prompt;
        long generation = progressSyncPromptGeneration + 1;
        try {
            prompt = other != null && other != current
                ? new OctavoProgressSyncPrompt(
                    this, appearance, current, other,
                    progressSyncPromptListener(null, generation))
                : new OctavoProgressSyncPrompt(
                    this, appearance, current,
                    progressSyncPromptListener(null, generation));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            showOpenFailure(
                "Progress-display recovery is unavailable; reopen the "
                    + "book to retry");
            return false;
        }
        progressSyncPromptGeneration = generation;
        return installProgressSyncPrompt(prompt);
    }

    private boolean ensureProgressSyncFailurePrompt() {
        if (readerRoot == null || surfaceView == null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || surfaceView.hasSelectionForAccessibility()
            || (progressSyncPrompt == null
                && !hasSettledProgressReceiptEvidence())) {
            return false;
        }
        if (progressSyncPrompt != null) {
            return true;
        }
        closeReaderPanelsForProgressSync();
        OctavoProgressDisplay current = progressDisplay == null
            ? OctavoProgressDisplay.defaults() : progressDisplay;
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        if (receipt != null) {
            current = receipt.choice;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        OctavoProgressSyncStore.Candidate candidate =
            progressSyncCandidate;
        OctavoProgressDisplay other = pending != null
            ? (current == pending.targetDisplay()
                ? pending.originDisplay() : pending.targetDisplay())
            : candidate == null ? null : candidate.targetDisplay();
        OctavoProgressSyncPrompt prompt;
        long generation = progressSyncPromptGeneration + 1;
        try {
            prompt = other != null && other != current
                ? new OctavoProgressSyncPrompt(
                    this, appearance, current, other,
                    progressSyncPromptListener(null, generation))
                : new OctavoProgressSyncPrompt(
                    this, appearance, current,
                    progressSyncPromptListener(null, generation));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            showOpenFailure(
                "Progress-display recovery is unavailable; reopen the "
                    + "book to retry");
            return false;
        }
        progressSyncPromptGeneration = generation;
        return installProgressSyncPrompt(prompt);
    }

    private OctavoProgressSyncPrompt.Listener progressSyncPromptListener(
        OctavoProgressSyncStore.Candidate expected,
        long generation) {
        return new OctavoProgressSyncPrompt.Listener() {
            @Override
            public void onUseDisplay() {
                if (progressSyncPromptGeneration != generation) {
                    return;
                }
                useProgressSyncCandidate(expected);
            }

            @Override
            public void onKeepMine() {
                if (progressSyncPromptGeneration != generation) {
                    return;
                }
                keepCurrentProgressDisplay(expected);
            }

            @Override
            public void onRetry() {
                if (progressSyncPromptGeneration != generation) {
                    return;
                }
                Runnable retry = progressSyncRetry;
                progressSyncRetry = null;
                progressSyncAwaitingExplicitRetry = false;
                if (retry != null) {
                    retry.run();
                    return;
                }
                OctavoProgressSyncStore.Pending pending =
                    progressSyncStore == null ? null
                        : progressSyncStore.pending();
                if (pending != null
                    && pending.direction
                       == OctavoProgressSyncStore.PendingDirection
                           .ROLLBACK) {
                    retryPendingProgressRollback(pending);
                } else if (pending != null) {
                    retryPendingProgressForward();
                } else {
                    reloadProgressSyncState();
                }
            }
        };
    }

    private boolean installProgressSyncPrompt(
        OctavoProgressSyncPrompt prompt) {
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
                progressSyncPromptWidth(),
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
        progressSyncOverlay = overlay;
        progressSyncPrompt = prompt;
        obscureFailureBannerForModalPrompt();
        int duration = progressSyncPromptMotionDuration();
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            prompt.setTranslationY(dp(16));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            prompt.animate().translationY(0.0f)
                .setDuration(duration).start();
        }
        prompt.post(() -> {
            if (progressSyncPrompt != prompt) {
                return;
            }
            View focus = prompt.preferredInitialFocus();
            focus.requestFocus();
            focus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            prompt.announceForAccessibility(
                "Progress display confirmation opened");
        });
        return true;
    }

    private void closeReaderPanelsForProgressSync() {
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

    private void showProgressSyncFailure(String heading,
                                         String message,
                                         Runnable retry) {
        if (progressSyncO8pgFutureBlocked
            || (progressStore != null
                && progressStore.loadStatus()
                   == OctavoProgressStore.LoadStatus.FUTURE)) {
            progressSyncO8pgFutureBlocked = true;
            heading = "Progress display update needs attention";
            message = "The saved progress display was written by a newer "
                + "version. Its bytes were preserved and synchronization "
                + "is blocked.";
            retry = this::reloadProgressSyncState;
        }
        progressSyncAwaitingExplicitRetry = true;
        progressSyncRetry = retry;
        progressSyncFailureHeading = TextUtils.isEmpty(heading)
            ? "Progress display update needs attention" : heading;
        progressSyncFailureMessage = TextUtils.isEmpty(message)
            ? "The progress-display update was not saved. Retry is safe."
            : message;
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (pending != null) {
            progressSyncRetryDescriptor =
                ProgressSyncRetryDescriptor.pending(
                    pending.direction
                        == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                        ? PROGRESS_RETRY_ROLLBACK
                        : PROGRESS_RETRY_FORWARD,
                    pending);
        } else if (progressSyncRetryDescriptor == null) {
            progressSyncRetryDescriptor =
                ProgressSyncRetryDescriptor.reload();
        }
        lastOpenError = progressSyncFailureMessage;
        if (presentRetainedProgressSyncFailure()) {
            return;
        }
        if (readerRoot == null
            || (activityResumed
                && !hasSettledProgressReceiptEvidence()
                && appearanceSyncPrompt == null
                && readingPositionPrompt == null
                && appearancePanel == null && navigationPanel == null
                && searchPanel == null && bookmarksPanel == null
                && (surfaceView == null
                    || !surfaceView.hasSelectionForAccessibility()))) {
            showOpenFailure(lastOpenError);
        }
    }

    private boolean presentRetainedProgressSyncFailure() {
        if (!progressSyncAwaitingExplicitRetry || !activityResumed
            || readerRoot == null || surfaceView == null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || appearancePanel != null || navigationPanel != null
            || searchPanel != null || bookmarksPanel != null
            || surfaceView.hasSelectionForAccessibility()
            || (progressSyncPrompt == null
                && !hasSettledProgressReceiptEvidence())) {
            return false;
        }
        if (progressSyncRetry == null) {
            progressSyncRetry = restoredProgressSyncRetry();
        }
        if (!ensureProgressSyncFailurePrompt()) {
            return false;
        }
        String heading = TextUtils.isEmpty(progressSyncFailureHeading)
            ? "Progress display update needs attention"
            : progressSyncFailureHeading;
        String message = TextUtils.isEmpty(progressSyncFailureMessage)
            ? progressSyncStore != null
                && !TextUtils.isEmpty(progressSyncStore.lastError())
                ? progressSyncStore.lastError()
                : "The earlier progress-display update was not saved. "
                    + "Retry is required; this frame will not retry it "
                    + "automatically."
            : progressSyncFailureMessage;
        progressSyncPrompt.showRetryableFailure(heading, message);
        View focus = progressSyncPrompt.preferredInitialFocus();
        focus.post(() -> {
            if (progressSyncPrompt == null || !focus.isShown()) {
                return;
            }
            focus.requestFocus();
            focus.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
        });
        return true;
    }

    private Runnable restoredProgressSyncRetry() {
        if (progressSyncO8pgFutureBlocked
            || (progressStore != null
                && progressStore.loadStatus()
                   == OctavoProgressStore.LoadStatus.FUTURE)) {
            // A preserved newer O8PG blocks every restored action. In
            // particular, a durable pending/candidate descriptor must not
            // request a Surface mode before a reload proves the block gone.
            progressSyncO8pgFutureBlocked = true;
            return this::reloadProgressSyncState;
        }
        if (progressSyncAbandonAfterReload) {
            return this::reloadProgressSyncForAbandon;
        }
        if (progressSyncReviewEpochBeforeRetry >= 0) {
            return this::reconcileProgressSyncReviewEpoch;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        if (pending != null
            && pending.direction
               == OctavoProgressSyncStore.PendingDirection.ROLLBACK) {
            return () -> retryPendingProgressRollback(pending);
        }
        if (pending != null) {
            return this::retryPendingProgressForward;
        }
        if (progressSyncRetryDescriptor != null
            && progressSyncRetryDescriptor.candidateAction()) {
            return progressCandidateRetry(
                progressSyncRetryDescriptor.action);
        }
        if (progressSyncRetryDescriptor != null
            && progressSyncRetryDescriptor.localStageAction()) {
            return this::reloadProgressSyncForLocalStage;
        }
        return this::reloadProgressSyncState;
    }

    private boolean hasSettledProgressReceiptEvidence() {
        OctavoSurfaceView.ProgressPresentationReceipt receipt =
            currentProgressReceipt();
        return receipt != null && receipt.choice != null
            && receipt.strictResumeSettled;
    }

    private void restorePendingProgressAfterLifecycle() {
        if (progressSyncStore == null || surfaceView == null) {
            return;
        }
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore.pending();
        if (pending == null) {
            return;
        }
        if (progressSyncRetryDescriptor != null
            && progressSyncRetryDescriptor.localStageAction()) {
            if (!localStageMatchesPending(
                    progressSyncRetryDescriptor, pending)) {
                showProgressSyncFailure(
                    "Progress display update needs attention",
                    "The restored local display Retry does not match the "
                        + "durable pending transaction.",
                    this::reloadProgressSyncForLocalStage);
                return;
            }
            progressSyncRetryDescriptor =
                ProgressSyncRetryDescriptor.pending(
                    PROGRESS_RETRY_FORWARD, pending);
        }
        if (progressSyncAbandonAfterReload) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The interrupted dismissal must be reconciled before the "
                    + "progress display can continue.",
                this::reloadProgressSyncForAbandon);
            return;
        }
        progressSyncPending = pending;
        progressSyncRollbackRequested = pending.direction
            == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
        if (!progressSyncAwaitingExplicitRetry) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The progress-display update was paused before its durable "
                    + "confirmation finished. Retry is safe.",
                pending.direction
                    == OctavoProgressSyncStore.PendingDirection.ROLLBACK
                    ? () -> retryPendingProgressRollback(pending)
                    : this::retryPendingProgressForward);
        }
    }

    private void showProgressMutationFailure(
        OctavoProgressSyncStore.MutationResult result,
        String fallback,
        Runnable retry) {
        String detail = progressSyncStore == null
            ? null : progressSyncStore.lastError();
        String message = TextUtils.isEmpty(detail) ? fallback : detail;
        Runnable exactRetry = result
                == OctavoProgressSyncStore.MutationResult.PUBLISH_UNCERTAIN
            || result == OctavoProgressSyncStore.MutationResult.BLOCKED
            ? this::reloadProgressSyncState : retry;
        showProgressSyncFailure(
            "Progress display update needs attention",
            message,
            exactRetry);
    }

    private int progressSyncPromptWidth() {
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = readerRoot == null
                || readerRoot.getWidth() <= 0
            ? displayWidth : readerRoot.getWidth();
        return Math.min(
            appearancePanelWidth(),
            Math.max(availableWidth - dp(40), 1));
    }

    private void updateProgressSyncPromptBounds() {
        if (progressSyncPrompt == null
            || !(progressSyncPrompt.getLayoutParams()
                instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams layout =
            (FrameLayout.LayoutParams)
                progressSyncPrompt.getLayoutParams();
        layout.width = progressSyncPromptWidth();
        progressSyncPrompt.setLayoutParams(layout);
    }

    private int progressSyncPromptMotionDuration() {
        if (progressSyncPrompt != null
            && progressSyncPrompt.suppressHostMotion()) {
            return 0;
        }
        return sideSheetMotionDuration(
            OctavoDesignTokens.forAppearance(appearance));
    }

    private void closeProgressSyncPrompt(boolean restoreFocus) {
        boolean ownedPrompt = progressSyncOverlay != null
            || progressSyncPrompt != null;
        progressSyncPromptGeneration += 1;
        if (progressSyncOverlay != null) {
            progressSyncOverlay.animate().cancel();
            if (progressSyncOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup)progressSyncOverlay.getParent())
                    .removeView(progressSyncOverlay);
            }
        }
        if (progressSyncPrompt != null) {
            progressSyncPrompt.animate().cancel();
        }
        progressSyncOverlay = null;
        progressSyncPrompt = null;
        progressSyncPromptCandidate = null;
        progressSyncCandidate = null;
        if (!ownedPrompt) {
            return;
        }
        restoreReaderAccessibilityBoundary();
        if (appearanceSyncPrompt == null
            && readingPositionPrompt == null) {
            restoreFailureBannerAfterModalPrompt();
        }
        if (restoreFocus) {
            drainDeferredSyncPrompts();
            if (appearanceSyncPrompt == null
                && readingPositionPrompt == null
                && progressSyncPrompt == null) {
                restoreProgressSyncFocusAfterClose();
            }
        }
    }

    private void restoreProgressSyncFocusAfterClose() {
        if (progressSyncPrompt != null
            || appearanceSyncPrompt != null
            || readingPositionPrompt != null
            || surfaceView == null || !surfaceView.isShown()) {
            return;
        }
        View target = readerProgress != null && readerProgress.isShown()
            ? readerProgress : surfaceView;
        target.requestFocus();
        target.post(() -> {
            if (progressSyncPrompt != null
                || appearanceSyncPrompt != null
                || readingPositionPrompt != null
                || !target.isShown()) {
                return;
            }
            target.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            target.announceForAccessibility(
                "Progress display confirmation closed");
        });
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
        if (progressSyncPrompt != null) {
            progressSyncPrompt.applyAppearance(appearance);
        }
        if (progressSyncOverlay != null
            && progressSyncPrompt != null) {
            progressSyncOverlay.setBackgroundColor(
                progressSyncPrompt.overlayColor());
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
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null) {
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
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null) {
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
                    requestProgressDisplayFromNavigation(requested);
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
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null) {
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
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null) {
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
            drainDeferredSyncPrompts();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null
            && focusReturn != null
            && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (appearancePanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || progressSyncPrompt != null
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
            drainDeferredSyncPrompts();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null
            && focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (navigationPanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || progressSyncPrompt != null
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
            drainDeferredSyncPrompts();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null
            && focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (searchPanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || progressSyncPrompt != null
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
            drainDeferredSyncPrompts();
        }
        if (restoreFocus && appearanceSyncPrompt == null
            && readingPositionPrompt == null
            && progressSyncPrompt == null
            && focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (bookmarksPanel != null
                    || appearanceSyncPrompt != null
                    || readingPositionPrompt != null
                    || progressSyncPrompt != null
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
        showLibrary(false);
    }

    private void showLibrary(boolean beginReviewEpoch) {
        if (surfaceView != null || readerRoot != null || activeBook != null) {
            releaseReader();
        }
        if (libraryReviewEpochRetryPending
            && libraryReviewEpochBeforeRetry == Long.MAX_VALUE
            && librarySyncStore.reviewEpoch() == Long.MAX_VALUE) {
            libraryReviewEpochActive = true;
            libraryReviewEpochRetryPending = false;
            libraryReviewEpochBeforeRetry = -1;
        }
        if (beginReviewEpoch) {
            libraryCatalogReviewDeferred = false;
            long priorEpoch = librarySyncStore.reviewEpoch();
            OctavoLibrarySyncStore.MutationResult reviewed =
                librarySyncStore.beginReviewEpoch(true);
            if (reviewed.succeeded()) {
                libraryReviewEpochActive = true;
                libraryReviewEpochRetryPending = false;
                libraryReviewEpochBeforeRetry = -1;
            } else if (reviewed
                       == OctavoLibrarySyncStore.MutationResult.EXHAUSTED
                       && priorEpoch == Long.MAX_VALUE) {
                // Long.MAX_VALUE is a settled terminal epoch. NONE records
                // remain reviewable there, while records dismissed at MAX
                // remain suppressed; there is no next epoch to reconcile.
                libraryReviewEpochActive = true;
                libraryReviewEpochRetryPending = false;
                libraryReviewEpochBeforeRetry = -1;
            } else {
                libraryReviewEpochActive = false;
                libraryReviewEpochRetryPending = true;
                libraryReviewEpochBeforeRetry = priorEpoch;
            }
        }
        chromeVisible = false;
        activeBook = null;
        libraryRowFocusReturn = null;
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
        addButton.setOnClickListener(view -> {
            libraryFocusBookKey = LIBRARY_FOCUS_ADD;
            libraryFocusAction = LIBRARY_FOCUS_ADD_ACTION;
            startActivityForResult(createOpenDocumentIntent(),
                                   REQUEST_ADD_EPUB);
        });
        header.addView(addButton, wrapLayout());
        root.addView(header, matchParentWidthLayout());

        OctavoLibraryPortable.Descriptor suppressedHeaderEntry =
            suppressedLibraryCatalogDescriptor(
                availableLibraryCatalogManifest());
        if (!librarySuppressedReviewRequested
            && suppressedHeaderEntry != null
            && canStartLibraryCatalogDownload()) {
            Button suppressed = new Button(this);
            suppressed.setText("Review synchronized EPUB");
            suppressed.setContentDescription(
                "Review a suppressed synchronized EPUB");
            suppressed.setAllCaps(false);
            suppressed.setMinHeight(
                dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            suppressed.setTextColor(tokens.chromeText);
            suppressed.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            suppressed.setOnClickListener(view -> {
                libraryFocusBookKey = LIBRARY_FOCUS_ADD;
                libraryFocusAction = LIBRARY_FOCUS_ADD_ACTION;
                librarySuppressedReviewRequested = true;
                showLibrary();
            });
            root.addView(suppressed, matchParentWidthLayout());
        }

        if (libraryAttentionDeferred
            && !retainedLibraryAttentionAvailable()) {
            libraryAttentionDeferred = false;
        }
        if (libraryAttentionDeferred) {
            Button attention = new Button(this);
            attention.setText("Review pending Library attention");
            attention.setContentDescription(
                "Review pending Library transfer or recovery attention");
            attention.setAllCaps(false);
            attention.setMinHeight(
                dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            attention.setTextColor(tokens.chromeText);
            attention.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            attention.setOnClickListener(view -> {
                libraryAttentionDeferred = false;
                showLibrary();
            });
            root.addView(attention, matchParentWidthLayout());
        }

        if (libraryMembershipAttentionDeferred
            && !retainedLibraryMembershipAttentionAvailable()) {
            libraryMembershipAttentionDeferred = false;
        }
        if (libraryMembershipAttentionDeferred) {
            Button membershipAttention = new Button(this);
            membershipAttention.setText(
                "Review pending membership attention");
            membershipAttention.setContentDescription(
                "Review pending synchronized Library membership attention");
            membershipAttention.setAllCaps(false);
            membershipAttention.setFocusable(true);
            membershipAttention.setFocusableInTouchMode(true);
            membershipAttention.setMinHeight(
                dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            membershipAttention.setTextColor(tokens.chromeText);
            membershipAttention.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            membershipAttention.setOnClickListener(view -> {
                libraryFocusBookKey = LIBRARY_FOCUS_ADD;
                libraryFocusAction = LIBRARY_FOCUS_MEMBERSHIP_REVIEW;
                libraryMembershipAttentionDeferred = false;
                showLibrary();
            });
            root.addView(
                membershipAttention, matchParentWidthLayout());
            if (LIBRARY_FOCUS_ADD.equals(libraryFocusBookKey)
                && libraryFocusAction
                    == LIBRARY_FOCUS_MEMBERSHIP_REVIEW) {
                libraryRowFocusReturn = membershipAttention;
            }
        }

        OctavoLibraryMembershipStore.Receipt membershipHistory =
            firstAbsentLibraryMembershipHistory();
        if (membershipHistory != null
            && !libraryMembershipAttentionDeferred) {
            Button membershipReview = new Button(this);
            membershipReview.setText(
                membershipHistory.projection
                    == OctavoLibraryMembershipPortable.Projection.CONFLICT
                    ? "Review synchronized Library conflict"
                    : "Restore to synchronized Library");
            membershipReview.setContentDescription(
                membershipReview.getText() + " for EPUB "
                    + shortDigest(membershipHistory.digest));
            membershipReview.setAllCaps(false);
            membershipReview.setFocusable(true);
            membershipReview.setFocusableInTouchMode(true);
            membershipReview.setMinHeight(
                dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            membershipReview.setTextColor(tokens.chromeText);
            membershipReview.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            membershipReview.setOnClickListener(view -> {
                libraryFocusBookKey = LIBRARY_FOCUS_ADD;
                libraryFocusAction = LIBRARY_FOCUS_MEMBERSHIP_REVIEW;
                beginLibraryMembershipAction(
                    membershipHistory,
                    membershipHistory.projection
                        == OctavoLibraryMembershipPortable.Projection.CONFLICT
                        ? LIBRARY_MEMBERSHIP_ACTION_RESOLVE_MEMBER
                        : LIBRARY_MEMBERSHIP_ACTION_RESTORE);
            });
            root.addView(membershipReview, matchParentWidthLayout());
            if (LIBRARY_FOCUS_ADD.equals(libraryFocusBookKey)
                && libraryFocusAction
                    == LIBRARY_FOCUS_MEMBERSHIP_REVIEW) {
                libraryRowFocusReturn = membershipReview;
            }
        }

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

        String libraryError = firstNonemptyMessage(
            libraryCatalogDurableAttentionMessage(),
            libraryStore.lastError(),
            librarySyncStore.lastError(),
            bookTransferStore.lastError(),
            libraryMembershipStore.lastError());
        if (hasText(libraryError)) {
            TextView libraryStatus = new TextView(this);
            libraryStatus.setText(libraryError);
            libraryStatus.setContentDescription(libraryError);
            libraryStatus.setTextColor(tokens.error);
            libraryStatus.setAccessibilityLiveRegion(
                View.ACCESSIBILITY_LIVE_REGION_POLITE);
            libraryStatus.setPadding(0, 0, 0, dp(12));
            root.addView(libraryStatus, matchParentWidthLayout());
        }
        if (librarySyncStore.reviewEpoch() == Long.MAX_VALUE) {
            TextView terminalReviewStatus = new TextView(this);
            String terminalMessage =
                "Library review is at its final epoch. Undecided books "
                    + "remain available; previously deferred books stay "
                    + "suppressed.";
            terminalReviewStatus.setText(terminalMessage);
            terminalReviewStatus.setContentDescription(terminalMessage);
            terminalReviewStatus.setTextColor(tokens.textSecondary);
            terminalReviewStatus.setPadding(0, 0, 0, dp(12));
            root.addView(
                terminalReviewStatus, matchParentWidthLayout());
        }

        TextView identityStatus = new TextView(this);
        identityStatus.setTextColor(tokens.textSecondary);
        identityStatus.setAccessibilityLiveRegion(
            View.ACCESSIBILITY_LIVE_REGION_POLITE);
        identityStatus.setPadding(0, 0, 0, dp(12));
        identityStatus.setVisibility(
            pendingLibraryIdentityBook == null ? View.GONE : View.VISIBLE);
        if (pendingLibraryIdentityBook != null) {
            identityStatus.setText("Verifying EPUB identity before opening");
            identityStatus.setContentDescription(
                "Verifying EPUB identity before opening");
        }
        root.addView(identityStatus, matchParentWidthLayout());
        libraryIdentityStatus = identityStatus;

        libraryCatalogOffer = null;
        libraryCatalogOfferManifestSha256 = null;
        librarySyncPrompt = null;
        libraryMembershipPrompt = null;
        libraryMembershipReceipt = null;
        libraryMembershipStaged = null;
        libraryPromptStaged = null;
        libraryPromptRetry = null;
        libraryPromptCancel = null;
        View catalogPanel = createLibraryCatalogPanel(tokens);
        boolean catalogModal = librarySyncPrompt != null
            || libraryMembershipPrompt != null;
        if (catalogPanel != null) {
            root.addView(
                catalogPanel,
                catalogModal ? surfaceLayout() : matchParentWidthLayout());
        }
        if (!catalogModal) {
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
        } else {
            addButton.setEnabled(false);
            for (int index = 0; index < root.getChildCount(); ++index) {
                View sibling = root.getChildAt(index);
                if (sibling != catalogPanel) {
                    sibling.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                }
            }
        }

        libraryRoot = root;
        FrameLayout windowRoot = createSystemBarFrame(
            root,
            dp(OctavoDesignTokens.SPACE_LG_DP),
            tokens.librarySurface);
        setContentView(windowRoot, matchParentLayout());
        windowRoot.requestApplyInsets();
        if (libraryMembershipPrompt != null) {
            View focus = libraryMembershipPrompt.preferredInitialFocus();
            focus.post(() -> {
                if (libraryMembershipPrompt == null || !focus.isShown()) {
                    return;
                }
                focus.requestFocus();
                focus.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
            });
        } else if (librarySyncPrompt != null) {
            View focus = librarySyncPrompt.preferredInitialFocus();
            focus.post(() -> {
                if (librarySyncPrompt == null || !focus.isShown()) {
                    return;
                }
                focus.requestFocus();
                focus.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
            });
        } else if ((pendingLibraryIdentityBook == null
                    || libraryAttentionDeferred)
                   && hasText(libraryFocusBookKey)) {
            View focus = libraryRowFocusReturn == null
                ? addButton : libraryRowFocusReturn;
            String expectedFocusKey = libraryFocusBookKey;
            focus.post(() -> {
                if (librarySyncPrompt != null
                    || libraryMembershipPrompt != null || !focus.isShown()
                    || !expectedFocusKey.equals(libraryFocusBookKey)) {
                    return;
                }
                focus.requestFocus();
                focus.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
                libraryFocusBookKey = null;
                libraryFocusAction = LIBRARY_FOCUS_NONE;
                libraryRowFocusReturn = null;
            });
        }
        scheduleLibraryIdentityVerification();
    }

    private View createLibraryCatalogPanel(OctavoDesignTokens tokens) {
        libraryCatalogStatus = null;
        if (libraryAttentionDeferred
            && retainedLibraryAttentionAvailable()) {
            return null;
        }
        libraryAttentionDeferred = false;
        if (pendingLibraryIdentityBook != null
            && pendingLibraryIdentityMode
               == LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION) {
            return createPendingImportAssociationPanel(
                pendingLibraryIdentityBook, true);
        }
        if (pendingLibraryIdentityBook != null
            && pendingLibraryIdentityMode
               == LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION) {
            return createLocalPublicationWorkingPanel(
                pendingLibraryIdentityBook);
        }
        if (pendingLibraryIdentityBook != null
            && pendingLibraryIdentityMode
               == LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION) {
            return createTransferFinalizationWorkingPanel(
                pendingLibraryIdentityBook);
        }
        if (pendingLibraryIdentityBook != null) {
            return null;
        }
        if (rejectedStagedImportCleanupBook != null
            && libraryStore.isStagedImport(
                rejectedStagedImportCleanupBook)) {
            return createRejectedStagedImportCleanupPanel(
                rejectedStagedImportCleanupBook);
        }
        rejectedStagedImportCleanupBook = null;
        OctavoLibraryStore.Book pendingImport =
            currentPendingImportAssociationBook();
        if (pendingImport != null) {
            return createPendingImportAssociationPanel(
                pendingImport, false);
        }
        // A durable cleanup intent owns its local row/file crash boundary.
        // Let it advance before any O1LS marker (including an unrelated one)
        // can mask the only explicit cleanup Retry surface.
        if (!bookTransferStore.cleanupJobs().isEmpty()) {
            return createLibraryTransferPanel(tokens);
        }
        OctavoLibrarySyncStore.LocalReconciliation local =
            librarySyncStore.localReconciliation();
        if (local != null) {
            return createLocalLibraryReconciliationPanel(tokens, local);
        }
        OctavoLibraryStore.Book discoveryRetry =
            currentLocalDiscoveryRetryBook();
        if (discoveryRetry != null) {
            return createLocalDiscoveryRetryPanel(discoveryRetry);
        }
        if (bookTransferStore.activeJob() != null) {
            return createLibraryTransferPanel(tokens);
        }
        OctavoLibrarySyncStore.TransferReconciliation reconciliation =
            librarySyncStore.transferReconciliation();
        if (reconciliation != null) {
            return createLibraryTransferReconciliationPanel(
                tokens, reconciliation);
        }
        if (libraryReviewEpochRetryPending) {
            return createLibraryReviewEpochRetryPanel(tokens);
        }

        OctavoLibrarySyncStore.StagedPortable staged =
            librarySyncStore.stagedPortable();
        if (staged != null
            && staged.kind == OctavoLibrarySyncStore.StagedKind.CURRENT
            && !libraryCatalogReviewDeferred) {
            OctavoLibraryPortable.DecodeResult decoded =
                OctavoLibraryPortable.decode(staged.bytes());
            if (decoded.status == OctavoLibraryPortable.DecodeStatus.READY) {
                return createLibraryCatalogReviewPanel(
                    tokens, staged, decoded.snapshot().descriptorCount());
            }
        }

        if (bookTransferStore.intentCount() != 0) {
            return createLibraryTransferPanel(tokens);
        }
        OctavoBookManifest availableManifest =
            availableLibraryCatalogManifest();
        if (libraryCatalogRepairDownloadNeeded(availableManifest)
            && canStartLibraryCatalogDownload()) {
            return createLibraryRepairDownloadPanel(availableManifest);
        }
        View membershipPanel = createLibraryMembershipPanel(tokens);
        if (membershipPanel != null) {
            return membershipPanel;
        }
        OctavoLibraryPortable.Descriptor suppressed =
            suppressedLibraryCatalogDescriptor(availableManifest);
        boolean suppressedReady = suppressed != null
            && canStartLibraryCatalogDownload();
        if (librarySuppressedReviewRequested && suppressedReady) {
            return createSuppressedLibraryCatalogPanel(suppressed);
        }
        if (librarySuppressedReviewRequested && !suppressedReady) {
            librarySuppressedReviewRequested = false;
        }
        OctavoLibrarySyncStore.Candidate offer = eligibleLibraryCatalogOffer();
        if (offer == null) {
            return null;
        }
        libraryCatalogOffer = offer;
        return createLibraryCatalogOfferPanel(tokens, offer);
    }

    private View createLibraryMembershipPanel(OctavoDesignTokens tokens) {
        if (libraryMembershipPendingAction
                != LIBRARY_MEMBERSHIP_ACTION_NONE) {
            View pending = createPendingLibraryMembershipPanel();
            if (pending != null) {
                return pending;
            }
        }
        if (libraryMembershipAttentionDeferred
            && retainedLibraryMembershipAttentionAvailable()) {
            return null;
        }
        libraryMembershipAttentionDeferred = false;

        if (libraryMembershipStoreBlocked()) {
            OctavoLibraryMembershipPrompt prompt =
                newLibraryMembershipPrompt();
            prompt.showRetainedAttention(
                "Synchronized Library membership needs Retry",
                nonemptyMessage(
                    libraryMembershipStore.lastError(),
                    "The exact membership state is retained and blocked."),
                libraryMembershipStore.stagedPortable() != null);
            return prompt;
        }

        OctavoLibraryMembershipStore.StagedPortable staged =
            libraryMembershipStore.stagedPortable();
        if (staged != null) {
            libraryMembershipStaged = staged;
            if (staged.kind
                    == OctavoLibraryMembershipStore.StagedKind.CURRENT
                && staged.attention
                    == OctavoLibraryMembershipStore.Attention
                        .CURRENT_APPROVAL) {
                OctavoLibraryMembershipPortable.DecodeResult decoded =
                    OctavoLibraryMembershipPortable.decode(staged.bytes());
                if (decoded.status
                    == OctavoLibraryMembershipPortable.DecodeStatus.READY) {
                    OctavoLibraryMembershipPrompt prompt =
                        newLibraryMembershipPrompt();
                    prompt.showStagedApproval(
                        decoded.snapshot().recordCount(),
                        staged.sha256, staged.reviewEpoch);
                    return prompt;
                }
            }
            OctavoLibraryMembershipPrompt prompt =
                newLibraryMembershipPrompt();
            prompt.showRetainedAttention(
                membershipAttentionHeading(staged.attention),
                nonemptyMessage(
                    libraryMembershipStore.lastError(),
                    "The exact reviewed membership input is retained."),
                true);
            return prompt;
        }

        OctavoLibraryMembershipStore.Receipt crossFamily =
            firstLibraryMembershipCrossFamilyIssue();
        if (crossFamily != null) {
            libraryMembershipReceipt = crossFamily;
            OctavoLibraryMembershipPrompt prompt =
                newLibraryMembershipPrompt();
            OctavoLibraryPortable.Descriptor catalog =
                librarySyncStore.snapshot().descriptor(crossFamily.digest);
            boolean missing = catalog == null;
            prompt.showRetainedAttention(
                missing
                    ? "Membership catalog identity is unavailable"
                    : "Membership catalog identity is equivocal",
                missing
                    ? "The exact membership history is retained, but its "
                        + "Library discovery identity is not yet available."
                    : "The membership and Library discovery byte counts do "
                        + "not match. Both exact states are retained.",
                false);
            return prompt;
        }
        OctavoLibraryMembershipStore.Receipt conflict =
            firstLibraryMembershipConflict();
        if (conflict != null) {
            libraryMembershipReceipt = conflict;
            OctavoLibraryMembershipPrompt prompt =
                newLibraryMembershipPrompt();
            prompt.showConflict(conflict.digest, conflict.byteCount);
            return prompt;
        }
        return null;
    }

    private View createPendingLibraryMembershipPanel() {
        OctavoLibraryMembershipPortable.Descriptor descriptor =
            pendingLibraryMembershipDescriptor();
        OctavoLibraryMembershipStore.Receipt receipt = descriptor == null
            ? null : libraryMembershipStore.receipt(descriptor);
        if (!validPendingLibraryMembershipAction(receipt)) {
            clearPendingLibraryMembershipAction();
            return null;
        }
        libraryMembershipReceipt = receipt;
        OctavoLibraryMembershipPrompt prompt =
            newLibraryMembershipPrompt();
        if (libraryMembershipPendingAction
            == LIBRARY_MEMBERSHIP_ACTION_WITHDRAW) {
            prompt.showWithdraw(receipt.digest, receipt.byteCount);
        } else if (libraryMembershipPendingAction
                   == LIBRARY_MEMBERSHIP_ACTION_RESTORE) {
            prompt.showRestore(receipt.digest, receipt.byteCount);
        } else {
            prompt.showConflict(receipt.digest, receipt.byteCount);
        }
        return prompt;
    }

    private OctavoLibraryMembershipPrompt newLibraryMembershipPrompt() {
        final OctavoLibraryMembershipPrompt[] owner =
            new OctavoLibraryMembershipPrompt[1];
        OctavoLibraryMembershipPrompt prompt =
            new OctavoLibraryMembershipPrompt(
                this,
                appearance,
                new OctavoLibraryMembershipPrompt.Listener() {
                    private boolean current() {
                        return owner[0] != null
                            && libraryMembershipPrompt == owner[0]
                            && libraryRoot != null;
                    }

                    @Override
                    public void onWithdraw() {
                        if (current()) {
                            applyLibraryMembershipMutation(
                                LIBRARY_MEMBERSHIP_ACTION_WITHDRAW);
                        }
                    }

                    @Override
                    public void onRestore() {
                        if (current()) {
                            applyLibraryMembershipMutation(
                                LIBRARY_MEMBERSHIP_ACTION_RESTORE);
                        }
                    }

                    @Override
                    public void onResolveMember() {
                        if (current()) {
                            applyLibraryMembershipMutation(
                                LIBRARY_MEMBERSHIP_ACTION_RESOLVE_MEMBER);
                        }
                    }

                    @Override
                    public void onResolveWithdrawn() {
                        if (current()) {
                            applyLibraryMembershipMutation(
                                LIBRARY_MEMBERSHIP_ACTION_RESOLVE_WITHDRAWN);
                        }
                    }

                    @Override
                    public void onApproveStaged() {
                        if (current()) {
                            approveLibraryMembershipStaged();
                        }
                    }

                    @Override
                    public void onDiscardStaged() {
                        if (current()) {
                            discardLibraryMembershipStaged();
                        }
                    }

                    @Override
                    public void onRetry() {
                        if (current()) {
                            retryLibraryMembershipAttention();
                        }
                    }
                });
        owner[0] = prompt;
        libraryMembershipPrompt = prompt;
        return prompt;
    }

    private void applyLibraryMembershipMutation(int action) {
        OctavoLibraryMembershipStore.Receipt expected =
            libraryMembershipReceipt;
        if (!exactLibraryMembershipCatalogDescriptor(expected)) {
            clearPendingLibraryMembershipAction();
            libraryMembershipReceipt = null;
            showLibrary();
            showOpenFailure(
                "The exact Library discovery identity changed; membership was not updated");
            return;
        }
        OctavoLibraryMembershipStore.MutationOutcome outcome;
        if (action == LIBRARY_MEMBERSHIP_ACTION_WITHDRAW) {
            outcome = libraryMembershipStore.withdraw(expected);
        } else if (action == LIBRARY_MEMBERSHIP_ACTION_RESTORE) {
            outcome = libraryMembershipStore.restore(expected);
        } else if (action
                   == LIBRARY_MEMBERSHIP_ACTION_RESOLVE_MEMBER) {
            outcome = libraryMembershipStore.resolveConflict(
                expected,
                OctavoLibraryMembershipPortable.Projection.MEMBER);
        } else if (action
                   == LIBRARY_MEMBERSHIP_ACTION_RESOLVE_WITHDRAWN) {
            outcome = libraryMembershipStore.resolveConflict(
                expected,
                OctavoLibraryMembershipPortable.Projection.WITHDRAWN);
        } else {
            return;
        }
        clearPendingLibraryMembershipAction();
        libraryMembershipReceipt = null;
        showLibrary();
        if (outcome == null || !outcome.succeeded()) {
            showOpenFailure(nonemptyMessage(
                libraryMembershipStore.lastError(),
                "Synchronized Library membership still needs review"));
        }
    }

    private void approveLibraryMembershipStaged() {
        OctavoLibraryMembershipStore.StagedPortable expected =
            libraryMembershipStaged;
        OctavoLibraryMembershipStore.PortableApprovalResult result =
            libraryMembershipStore.approveStagedPortable(expected);
        libraryMembershipStaged = null;
        showLibrary();
        if (!result.succeeded()) {
            showOpenFailure(nonemptyMessage(
                libraryMembershipStore.lastError(),
                "Membership approval still needs review"));
        }
    }

    private void discardLibraryMembershipStaged() {
        OctavoLibraryMembershipStore.StagedPortable expected =
            libraryMembershipStaged;
        OctavoLibraryMembershipStore.PortableDiscardResult result =
            libraryMembershipStore.discardStagedPortable(expected);
        libraryMembershipStaged = null;
        showLibrary();
        if (!result.succeeded()) {
            showOpenFailure(nonemptyMessage(
                libraryMembershipStore.lastError(),
                "Membership input could not be discarded"));
        }
    }

    private void retryLibraryMembershipAttention() {
        libraryMembershipStore.load();
        libraryMembershipAttentionDeferred = false;
        showLibrary();
    }

    private void dismissLibraryMembershipForBack() {
        boolean retained = libraryMembershipPrompt != null
            && libraryMembershipPrompt.retainedAttention();
        libraryMembershipPrompt = null;
        libraryMembershipReceipt = null;
        libraryMembershipStaged = null;
        if (retained) {
            clearPendingLibraryMembershipAction();
            libraryMembershipAttentionDeferred = true;
            if (!hasText(libraryFocusBookKey)) {
                libraryFocusBookKey = LIBRARY_FOCUS_ADD;
                libraryFocusAction = LIBRARY_FOCUS_MEMBERSHIP_REVIEW;
            }
        } else {
            clearPendingLibraryMembershipAction();
        }
        showLibrary();
    }

    private boolean retainedLibraryMembershipAttentionAvailable() {
        return libraryMembershipStoreBlocked()
            || libraryMembershipStore.stagedPortable() != null
            || firstLibraryMembershipConflict() != null
            || firstLibraryMembershipCrossFamilyIssue() != null;
    }

    private boolean libraryMembershipStoreBlocked() {
        OctavoLibraryMembershipStore.LoadStatus status =
            libraryMembershipStore.loadStatus();
        return status
                == OctavoLibraryMembershipStore.LoadStatus
                    .CORRUPT_QUARANTINED_BLOCKED
            || status
                == OctavoLibraryMembershipStore.LoadStatus.CORRUPT_BLOCKED
            || status
                == OctavoLibraryMembershipStore.LoadStatus.OVERBOUND_BLOCKED
            || status
                == OctavoLibraryMembershipStore.LoadStatus
                    .FUTURE_VERSION_BLOCKED
            || status
                == OctavoLibraryMembershipStore.LoadStatus
                    .PUBLISH_UNCERTAIN_BLOCKED;
    }

    private OctavoLibraryMembershipStore.Receipt
        firstLibraryMembershipConflict() {
        if (libraryMembershipStoreBlocked()) {
            return null;
        }
        for (OctavoLibraryMembershipPortable.Record record
                 : libraryMembershipStore.snapshot().records()) {
            if (record.projection()
                == OctavoLibraryMembershipPortable.Projection.CONFLICT) {
                return libraryMembershipStore.receipt(record.descriptor());
            }
        }
        return null;
    }

    private OctavoLibraryMembershipStore.Receipt
        firstLibraryMembershipCrossFamilyIssue() {
        if (libraryMembershipStoreBlocked()) {
            return null;
        }
        for (OctavoLibraryMembershipPortable.Record record
                 : libraryMembershipStore.snapshot().records()) {
            OctavoLibraryPortable.Descriptor catalog =
                librarySyncStore.snapshot().descriptor(
                    record.descriptor.digest);
            if (catalog == null
                || catalog.byteCount != record.descriptor.byteCount) {
                return libraryMembershipStore.receipt(record.descriptor());
            }
        }
        return null;
    }

    private OctavoLibraryMembershipStore.Receipt
        firstAbsentLibraryMembershipHistory() {
        if (libraryMembershipStoreBlocked()) {
            return null;
        }
        for (OctavoLibraryMembershipPortable.Record record
                 : libraryMembershipStore.snapshot().records()) {
            OctavoLibraryPortable.Descriptor catalog =
                librarySyncStore.snapshot().descriptor(
                    record.descriptor.digest);
            if (catalog == null
                || catalog.byteCount != record.descriptor.byteCount
                || libraryStore.findBook(record.descriptor.digest) != null) {
                continue;
            }
            OctavoLibraryMembershipPortable.Projection projection =
                record.projection();
            if (projection
                    == OctavoLibraryMembershipPortable.Projection.WITHDRAWN
                || projection
                    == OctavoLibraryMembershipPortable.Projection.CONFLICT) {
                return libraryMembershipStore.receipt(record.descriptor());
            }
        }
        return null;
    }

    private boolean libraryMembershipAllowsCatalogOffer(
        String digest,
        long byteCount) {
        if (libraryMembershipStoreBlocked()) {
            return false;
        }
        OctavoLibraryMembershipPortable.Record record =
            libraryMembershipStore.snapshot().record(digest);
        if (record == null) {
            // The provider/object-presence coordinator is a later family.
            // Preserve the accepted O1LC offer path without interpreting
            // O1LM absence as synchronized-membership evidence.
            return true;
        }
        return record.descriptor.byteCount == byteCount
            && record.projection()
                == OctavoLibraryMembershipPortable.Projection.MEMBER;
    }

    private void beginLibraryMembershipAction(
        OctavoLibraryMembershipStore.Receipt expected,
        int action) {
        if (expected == null) {
            return;
        }
        libraryMembershipPendingAction = action;
        libraryMembershipPendingDigest = expected.digest;
        libraryMembershipPendingByteCount = expected.byteCount;
        libraryMembershipPendingRecordFingerprint =
            expected.recordFingerprint;
        libraryMembershipPendingSnapshotFingerprint =
            expected.snapshotFingerprint;
        libraryMembershipPendingStateGeneration =
            expected.stateGeneration;
        libraryMembershipPendingRecordPresent = expected.recordPresent;
        libraryMembershipPendingProjection = expected.projection == null
            ? -1 : expected.projection.ordinal();
        showLibrary();
    }

    private OctavoLibraryMembershipPortable.Descriptor
        pendingLibraryMembershipDescriptor() {
        if (!hasText(libraryMembershipPendingDigest)
            || libraryMembershipPendingByteCount <= 0
            || libraryMembershipPendingByteCount > 536870912L) {
            return null;
        }
        try {
            return new OctavoLibraryMembershipPortable.Descriptor(
                libraryMembershipPendingDigest,
                libraryMembershipPendingByteCount);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean validPendingLibraryMembershipAction(
        OctavoLibraryMembershipStore.Receipt receipt) {
        if (receipt == null) {
            return false;
        }
        if (!exactLibraryMembershipCatalogDescriptor(receipt)) {
            return false;
        }
        int projection = receipt.projection == null
            ? -1 : receipt.projection.ordinal();
        if (receipt.stateGeneration
                != libraryMembershipPendingStateGeneration
            || receipt.recordPresent
                != libraryMembershipPendingRecordPresent
            || projection != libraryMembershipPendingProjection
            || !receipt.recordFingerprint.equals(
                libraryMembershipPendingRecordFingerprint)
            || !receipt.snapshotFingerprint.equals(
                libraryMembershipPendingSnapshotFingerprint)) {
            return false;
        }
        if (libraryMembershipPendingAction
            == LIBRARY_MEMBERSHIP_ACTION_WITHDRAW) {
            return !receipt.recordPresent
                || receipt.projection
                    == OctavoLibraryMembershipPortable.Projection.MEMBER;
        }
        if (libraryMembershipPendingAction
            == LIBRARY_MEMBERSHIP_ACTION_RESTORE) {
            return receipt.recordPresent
                && receipt.projection
                    == OctavoLibraryMembershipPortable.Projection.WITHDRAWN;
        }
        return (libraryMembershipPendingAction
                    == LIBRARY_MEMBERSHIP_ACTION_RESOLVE_MEMBER
                || libraryMembershipPendingAction
                    == LIBRARY_MEMBERSHIP_ACTION_RESOLVE_WITHDRAWN)
            && receipt.recordPresent
            && receipt.projection
                == OctavoLibraryMembershipPortable.Projection.CONFLICT;
    }

    private void clearPendingLibraryMembershipAction() {
        libraryMembershipPendingAction = LIBRARY_MEMBERSHIP_ACTION_NONE;
        libraryMembershipPendingDigest = null;
        libraryMembershipPendingByteCount = 0;
        libraryMembershipPendingRecordFingerprint = null;
        libraryMembershipPendingSnapshotFingerprint = null;
        libraryMembershipPendingStateGeneration = -1;
        libraryMembershipPendingRecordPresent = false;
        libraryMembershipPendingProjection = -1;
    }

    private boolean exactLibraryMembershipCatalogDescriptor(
        OctavoLibraryMembershipStore.Receipt receipt) {
        if (receipt == null) {
            return false;
        }
        if (librarySyncLoadBlocked(librarySyncStore.loadStatus())) {
            return false;
        }
        OctavoLibraryPortable.Descriptor catalog =
            librarySyncStore.snapshot().descriptor(receipt.digest);
        return catalog != null && catalog.byteCount == receipt.byteCount
            && receipt.kind
                == OctavoLibraryMembershipPortable.Descriptor.EPUB;
    }

    private static String membershipAttentionHeading(
        OctavoLibraryMembershipStore.Attention attention) {
        if (attention
            == OctavoLibraryMembershipStore.Attention.FUTURE_RETAINED) {
            return "Membership input needs a newer app";
        }
        if (attention
            == OctavoLibraryMembershipStore.Attention.JOIN_LIMIT_RETAINED) {
            return "Membership history reached a limit";
        }
        if (attention
            == OctavoLibraryMembershipStore.Attention.STAGED_CONFLICT) {
            return "Membership review inputs conflict";
        }
        if (attention
            == OctavoLibraryMembershipStore.Attention.STALE_BASE) {
            return "Membership review base changed";
        }
        return "Synchronized Library membership needs review";
    }

    private boolean retainedLibraryAttentionAvailable() {
        if (pendingLibraryIdentityBook != null
            || (rejectedStagedImportCleanupBook != null
                && libraryStore.isStagedImport(
                    rejectedStagedImportCleanupBook))
            || currentPendingImportAssociationBook() != null
            || !bookTransferStore.cleanupJobs().isEmpty()
            || librarySyncStore.localReconciliation() != null
            || bookTransferStore.activeJob() != null
            || bookTransferStore.intentCount() != 0
            || librarySyncStore.transferReconciliation() != null
            || libraryReviewEpochRetryPending) {
            return true;
        }
        if (hasText(libraryDiscoveryRetryBookKey)
            && currentLocalDiscoveryRetryBook() != null) {
            return true;
        }
        OctavoBookManifest manifest = availableLibraryCatalogManifest();
        return libraryCatalogRepairDownloadNeeded(manifest)
            || (librarySuppressedReviewRequested
                && suppressedLibraryCatalogDescriptor(manifest) != null);
    }

    private void deferLibraryAttention() {
        if (librarySyncPrompt == null
            || !retainedLibraryAttentionAvailable()) {
            return;
        }
        if (pendingLibraryIdentityBook != null) {
            OctavoLibraryStore.Book pending = pendingLibraryIdentityBook;
            int mode = pendingLibraryIdentityMode;
            OctavoLibrarySyncStore.LocalReconciliation local =
                pendingLibraryIdentityLocalReconciliation;
            if (libraryRoot != null) {
                libraryRoot.removeCallbacks(libraryIdentityVerification);
            }
            libraryIdentityVerificationPosted = false;
            libraryStore.cancelBookIdentityVerification();
            clearPendingLibraryIdentityOperation();
            transientTransferReader0Title = null;
            if (mode == LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION) {
                libraryTransferExplicitRetryRequired = true;
                libraryTransferCatalogRetryMessage =
                    "Retained transfer verification needs explicit Retry";
            } else if (mode
                       == LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION) {
                pendingImportAssociationBook = pending;
                libraryImportAssociationStatus =
                    "Imported EPUB association was deferred; Retry when ready";
            } else if (mode
                       == LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION) {
                if (local == null) {
                    libraryDiscoveryRetryBookKey = pending.key;
                }
                libraryDiscoveryStatus =
                    "Local Library discovery was deferred; Retry when ready";
            }
        }
        libraryAttentionDeferred = true;
        libraryFocusBookKey = LIBRARY_FOCUS_ADD;
        libraryFocusAction = LIBRARY_FOCUS_ADD_ACTION;
        showLibrary();
    }

    private OctavoLibraryPortable.Descriptor
        suppressedLibraryCatalogDescriptor(OctavoBookManifest manifest) {
        if (manifest == null) {
            return null;
        }
        OctavoLibrarySyncStore.Decision decision =
            librarySyncStore.decision(manifest.digest);
        if (decision != OctavoLibrarySyncStore.Decision.IGNORED
            && decision
               != OctavoLibrarySyncStore.Decision.LOCAL_REMOVED) {
            return null;
        }
        OctavoLibraryPortable.Descriptor descriptor =
            librarySyncStore.snapshot().descriptor(manifest.digest);
        if (descriptor == null
            || descriptor.byteCount != manifest.byteCount) {
            return null;
        }
        OctavoLibraryStore.Book local =
            libraryStore.findBook(descriptor.digest);
        if (local != null
            && (!local.imported || !local.repairRequired)) {
            return null;
        }
        return descriptor;
    }

    private View createSuppressedLibraryCatalogPanel(
        OctavoLibraryPortable.Descriptor descriptor) {
        OctavoLibrarySyncStore.Decision expectedDecision =
            librarySyncStore.decision(descriptor.digest);
        long expectedEpoch = librarySyncStore.reviewEpoch();
        String expectedManifestSha256 =
            sha256Hex(libraryCatalogManifestBytes);
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showRetryableFailure(
            expectedDecision == OctavoLibrarySyncStore.Decision.IGNORED
                ? "Hidden synchronized EPUB"
                : "EPUB removed from this device",
            "EPUB - " + humanReadableByteCount(descriptor.byteCount)
                + " - " + shortDigest(descriptor.digest)
                + " - Make this exact EPUB available for Download again",
            false);
        libraryPromptRetry = () -> {
            boolean exact = reloadTransferStoresForExplicitRetry()
                && exactSuppressedLibraryCatalogAction(
                    descriptor, expectedDecision, expectedEpoch,
                    expectedManifestSha256);
            boolean prepared = false;
            if (exact) {
                OctavoLibraryStore.Book local =
                    libraryStore.findBook(descriptor.digest);
                if (local == null) {
                    prepared = librarySyncStore.resetForExplicitDownload(
                        descriptor.digest).succeeded();
                } else {
                    OctavoBookTransferStore.CleanupOutcome staged =
                        bookTransferStore.stageRepairManagedCleanup(
                            local.key, local.byteCount);
                    prepared = staged.result.succeeded()
                        && retryRepairManagedCleanup(
                            staged.attemptSequence);
                }
            }
            librarySuppressedReviewRequested = false;
            showLibrary();
            if (!prepared) {
                showOpenFailure(nonemptyMessage(
                    firstNonemptyMessage(
                        librarySyncStore.lastError(),
                        bookTransferStore.lastError(),
                        libraryStore.lastError()),
                    "The suppressed EPUB changed before it could be re-offered"));
            }
        };
        return prompt;
    }

    private boolean exactSuppressedLibraryCatalogAction(
        OctavoLibraryPortable.Descriptor expected,
        OctavoLibrarySyncStore.Decision expectedDecision,
        long expectedEpoch,
        String expectedManifestSha256) {
        if (expected == null || expectedDecision == null
            || expectedEpoch != librarySyncStore.reviewEpoch()
            || !hasText(expectedManifestSha256)
            || !expectedManifestSha256.equals(
                sha256Hex(libraryCatalogManifestBytes))
            || !canStartLibraryCatalogDownload()
            || librarySyncStore.decision(expected.digest)
               != expectedDecision) {
            return false;
        }
        OctavoBookManifest manifest = availableLibraryCatalogManifest();
        OctavoLibraryPortable.Descriptor current =
            librarySyncStore.snapshot().descriptor(expected.digest);
        return manifest != null && current != null
            && manifest.digest.equals(expected.digest)
            && manifest.byteCount == expected.byteCount
            && current.sameIdentity(expected)
            && suppressedLibraryCatalogDescriptor(manifest) != null;
    }

    private View createRejectedStagedImportCleanupPanel(
        OctavoLibraryStore.Book staged) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showRetryableFailure(
            "Rejected EPUB cleanup needs Retry",
            "EPUB - " + humanReadableByteCount(staged.byteCount)
                + " - " + shortDigest(staged.key)
                + " - Clear the fixed local import staging file",
            false);
        libraryPromptRetry = () -> {
            boolean cleared = libraryStore.discardUncataloged(staged);
            if (cleared) {
                rejectedStagedImportCleanupBook = null;
            }
            showLibrary();
            if (!cleared) {
                showOpenFailure(nonemptyMessage(
                    libraryStore.lastError(),
                    "The rejected EPUB staging file still needs cleanup Retry"));
            }
        };
        return prompt;
    }

    private OctavoLibraryStore.Book currentLocalDiscoveryRetryBook() {
        if (!hasText(libraryDiscoveryRetryBookKey)) {
            if (libraryDiscoveryDerivedThisActivity) {
                return null;
            }
            OctavoLibrarySyncStore.LoadStatus status =
                librarySyncStore.loadStatus();
            boolean mutableQuietState =
                !librarySyncLoadBlocked(status)
                && status
                   != OctavoLibrarySyncStore.LoadStatus
                       .CORRUPT_QUARANTINED
                && librarySyncStore.stagedPortable() == null
                && librarySyncStore.transferReconciliation() == null
                && librarySyncStore.localReconciliation() == null
                && bookTransferStore.retainedIntentCount() == 0;
            if (!mutableQuietState) {
                return null;
            }
            libraryDiscoveryDerivedThisActivity = true;
            OctavoLibraryPortable.Snapshot snapshot =
                librarySyncStore.snapshot();
            for (OctavoLibraryStore.Book candidate
                     : libraryStore.books()) {
                if (candidate.imported && !candidate.repairRequired
                    && snapshot.descriptor(candidate.key) == null) {
                    libraryDiscoveryRetryBookKey = candidate.key;
                    libraryDiscoveryStatus =
                        "Local discovery needs explicit Retry";
                    break;
                }
            }
            if (!hasText(libraryDiscoveryRetryBookKey)) {
                return null;
            }
        }
        OctavoLibraryStore.Book book =
            libraryStore.findBook(libraryDiscoveryRetryBookKey);
        if (book == null || !book.imported || book.repairRequired
            || librarySyncStore.decision(libraryDiscoveryRetryBookKey)
               == OctavoLibrarySyncStore.Decision.DOWNLOADED) {
            libraryDiscoveryRetryBookKey = null;
            return null;
        }
        return book;
    }

    private View createLocalDiscoveryRetryPanel(
        OctavoLibraryStore.Book book) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showRetryableFailure(
            "Local Library discovery needs Retry",
            "EPUB - " + humanReadableByteCount(book.byteCount)
                + " - " + shortDigest(book.key)
                + " - " + nonemptyMessage(
                    libraryDiscoveryStatus,
                    "Publish this validated local EPUB to the synchronized Library"),
            false);
        libraryPromptRetry = () -> {
            boolean completed = reloadLibrarySyncForExplicitRetry()
                && retryLocalDiscovery(book);
            showLibrary();
            if (!completed && !"Verification in progress".equals(
                    libraryDiscoveryStatus)) {
                showOpenFailure(nonemptyMessage(
                    librarySyncStore.lastError(),
                    "Local Library discovery still needs Retry"));
            }
        };
        return prompt;
    }

    private boolean retryLocalDiscovery(OctavoLibraryStore.Book expected) {
        OctavoLibraryStore.Book current =
            currentLocalDiscoveryRetryBook();
        if (expected == null || current == null
            || !current.key.equals(expected.key)
            || current.byteCount != expected.byteCount) {
            return false;
        }
        if (!current.identityVerified) {
            pendingLibraryIdentityBook = current;
            pendingLibraryIdentityMode =
                LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION;
            pendingLibraryIdentityRecordOpened = false;
            pendingLibraryIdentityLocalReconciliation = null;
            libraryDiscoveryStatus = "Verification in progress";
            return false;
        }
        return completeLocalPublicationAfterIdentity(current, null);
    }

    private boolean libraryCatalogRepairDownloadNeeded(
        OctavoBookManifest manifest) {
        if (manifest == null
            || librarySyncStore.decision(manifest.digest)
               != OctavoLibrarySyncStore.Decision.DOWNLOADED) {
            return false;
        }
        OctavoLibraryStore.Book local =
            libraryStore.findBook(manifest.digest);
        return local == null
            || (local.imported && local.repairRequired);
    }

    private View createLibraryRepairDownloadPanel(
        OctavoBookManifest expectedManifest) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showRetryableFailure(
            "Downloaded EPUB needs repair",
            "EPUB - "
                + humanReadableByteCount(expectedManifest.byteCount)
                + " - " + shortDigest(expectedManifest.digest)
                + " - Make an explicit repair download available",
            false);
        libraryPromptRetry = () -> {
            boolean retryReady = reloadTransferStoresForExplicitRetry();
            OctavoBookManifest current =
                availableLibraryCatalogManifest();
            boolean exact = retryReady && current != null
                && current.digest.equals(expectedManifest.digest)
                && current.byteCount == expectedManifest.byteCount
                && libraryCatalogRepairDownloadNeeded(current)
                && canStartLibraryCatalogDownload();
            boolean prepared = false;
            if (exact) {
                OctavoLibraryStore.Book local =
                    libraryStore.findBook(current.digest);
                if (local == null) {
                    prepared = librarySyncStore.resetForExplicitDownload(
                        current.digest).succeeded();
                } else if (local.imported && local.repairRequired
                           && local.key.equals(current.digest)) {
                    OctavoBookTransferStore.CleanupOutcome staged =
                        bookTransferStore.stageRepairManagedCleanup(
                            local.key, local.byteCount);
                    prepared = staged.result.succeeded()
                        && retryRepairManagedCleanup(
                            staged.attemptSequence);
                }
            }
            showLibrary();
            if (!prepared) {
                showOpenFailure(nonemptyMessage(
                    firstNonemptyMessage(
                        bookTransferStore.lastError(),
                        librarySyncStore.lastError(),
                        libraryStore.lastError()),
                    "The repair download could not be made available"));
            }
        };
        return prompt;
    }

    private OctavoLibraryStore.Book currentPendingImportAssociationBook() {
        if (libraryStore.loadStatus()
            != OctavoLibraryStore.LoadStatus.IMPORT_ASSOCIATION_PENDING) {
            if (pendingImportAssociationBook != null
                && !libraryStore.hasPendingImportAssociation(
                    pendingImportAssociationBook.key,
                    pendingImportAssociationBook.byteCount)) {
                pendingImportAssociationBook = null;
            }
            return null;
        }
        if (pendingImportAssociationBook != null
            && libraryStore.hasPendingImportAssociation(
                pendingImportAssociationBook.key,
                pendingImportAssociationBook.byteCount)) {
            return pendingImportAssociationBook;
        }
        pendingImportAssociationBook = libraryStore.pendingImportedBook();
        return pendingImportAssociationBook;
    }

    private View createPendingImportAssociationPanel(
        OctavoLibraryStore.Book pending,
        boolean working) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        String identity = "EPUB - "
            + humanReadableByteCount(pending.byteCount)
            + " - " + shortDigest(pending.key);
        if (working) {
            prompt.showWorking(
                "Verifying imported EPUB",
                identity + " - " + nonemptyMessage(
                    libraryImportAssociationStatus,
                    "Verifying exact managed EPUB identity"),
                false);
        } else {
            prompt.showRetryableFailure(
                "Imported EPUB needs Library association",
                identity + " - " + nonemptyMessage(
                    libraryImportAssociationStatus,
                    "Retry exact identity and Reader validation"),
                true);
            prompt.cancelForTesting().setText("Discard");
            prompt.cancelForTesting().setContentDescription(
                "Discard this pending imported EPUB from this device");
            libraryPromptRetry = () -> {
                boolean started = retryPendingImportAssociation(pending);
                showLibrary();
                if (!started) {
                    showOpenFailure(nonemptyMessage(
                        firstNonemptyMessage(
                            libraryStore.lastError(),
                            libraryImportAssociationStatus),
                        "The imported EPUB still needs Library association Retry"));
                }
            };
            libraryPromptCancel = () -> {
                boolean discarded = discardPendingImportAssociation(pending);
                showLibrary();
                if (!discarded) {
                    showOpenFailure(nonemptyMessage(
                        libraryStore.lastError(),
                        "The pending imported EPUB still needs Discard Retry"));
                }
            };
        }
        return prompt;
    }

    private View createLocalPublicationWorkingPanel(
        OctavoLibraryStore.Book book) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showWorking(
            "Verifying local EPUB",
            "EPUB - " + humanReadableByteCount(book.byteCount)
                + " - " + shortDigest(book.key)
                + " - " + nonemptyMessage(
                    libraryDiscoveryStatus,
                    "Verifying exact local EPUB identity"),
            false);
        return prompt;
    }

    private View createTransferFinalizationWorkingPanel(
        OctavoLibraryStore.Book book) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showWorking(
            "Verifying downloaded EPUB",
            "EPUB - " + humanReadableByteCount(book.byteCount)
                + " - " + shortDigest(book.key)
                + " - Re-proving exact managed bytes before completion",
            false);
        return prompt;
    }

    private boolean retryPendingImportAssociation(
        OctavoLibraryStore.Book expected) {
        if (!reloadLibraryStoreForExplicitRetry()) {
            return false;
        }
        OctavoLibraryStore.Book pending =
            currentPendingImportAssociationBook();
        if (expected == null || pending == null
            || !pending.key.equals(expected.key)
            || pending.byteCount != expected.byteCount) {
            libraryImportAssociationStatus =
                "The pending import changed before Retry";
            return false;
        }
        OctavoLibraryStore.Book associated =
            libraryStore.findBook(pending.key);
        if (associated != null
            && associated.byteCount == pending.byteCount
            && libraryStore.completeImportedCatalogAssociation(associated)) {
            pendingImportAssociationBook = null;
            libraryImportAssociationStatus = null;
            if (!recordLocalDiscovery(associated)) {
                libraryDiscoveryStatus =
                    "Local discovery publication needs Retry";
            }
            return true;
        }
        pendingLibraryIdentityBook = pending;
        pendingLibraryIdentityMode =
            LIBRARY_IDENTITY_MODE_IMPORT_ASSOCIATION;
        pendingLibraryIdentityRecordOpened = false;
        pendingLibraryIdentityLocalReconciliation = null;
        libraryImportAssociationStatus =
            "Verifying exact managed EPUB identity";
        return true;
    }

    private boolean discardPendingImportAssociation(
        OctavoLibraryStore.Book expected) {
        if (!reloadLibraryStoreForExplicitRetry()) {
            return false;
        }
        OctavoLibraryStore.Book pending =
            currentPendingImportAssociationBook();
        if (expected == null || pending == null
            || !pending.key.equals(expected.key)
            || pending.byteCount != expected.byteCount
            || !libraryStore.discardPendingImportAssociation(pending)) {
            return false;
        }
        pendingImportAssociationBook = null;
        libraryImportAssociationStatus = null;
        libraryFocusBookKey = LIBRARY_FOCUS_ADD;
        libraryFocusAction = LIBRARY_FOCUS_ADD_ACTION;
        return true;
    }

    private OctavoLibrarySyncPrompt newLibrarySyncPrompt() {
        if (!hasText(libraryFocusBookKey)) {
            libraryFocusBookKey = LIBRARY_FOCUS_ADD;
            libraryFocusAction = LIBRARY_FOCUS_ADD_ACTION;
        }
        final OctavoLibrarySyncPrompt[] owner =
            new OctavoLibrarySyncPrompt[1];
        OctavoLibrarySyncPrompt prompt = new OctavoLibrarySyncPrompt(
            this,
            appearance,
            new OctavoLibrarySyncPrompt.Listener() {
                private boolean current() {
                    return owner[0] != null
                        && librarySyncPrompt == owner[0]
                        && libraryRoot != null;
                }

                @Override
                public void onApproveCatalog() {
                    if (current()) {
                        approveLibraryCatalog(libraryPromptStaged);
                    }
                }

                @Override
                public void onDeferCatalog() {
                    if (current()) {
                        deferLibraryCatalogReview();
                    }
                }

                @Override
                public void onDownload() {
                    if (current()) {
                        downloadLibraryCatalogOffer();
                    }
                }

                @Override
                public void onDismissOffer() {
                    if (current()) {
                        dismissLibraryCatalogOffer();
                    }
                }

                @Override
                public void onIgnoreOffer() {
                    if (current()) {
                        ignoreLibraryCatalogOffer();
                    }
                }

                @Override
                public void onRetry() {
                    if (current() && libraryPromptRetry != null) {
                        libraryPromptRetry.run();
                    }
                }

                @Override
                public void onCancel() {
                    if (current() && libraryPromptCancel != null) {
                        libraryPromptCancel.run();
                    }
                }
            });
        owner[0] = prompt;
        librarySyncPrompt = prompt;
        libraryCatalogStatus = prompt.statusForTesting();
        return prompt;
    }

    private View createLibraryReviewEpochRetryPanel(
        OctavoDesignTokens tokens) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showRetryableFailure(
            "Library review needs reconciliation",
            nonemptyMessage(
                librarySyncStore.lastError(),
                "No book offer will be shown until the exact Library review "
                    + "state is reloaded and reconciled."),
            false);
        libraryPromptRetry = () -> {
            boolean resolved = retryLibraryReviewEpoch();
            showLibrary();
            if (!resolved) {
                showOpenFailure(nonemptyMessage(
                    librarySyncStore.lastError(),
                    "Library review reconciliation still needs Retry"));
            }
        };
        return prompt;
    }

    private boolean retryLibraryReviewEpoch() {
        if (!libraryReviewEpochRetryPending
            || libraryReviewEpochBeforeRetry < 0) {
            return false;
        }
        librarySyncStore.load();
        long reloaded = librarySyncStore.reviewEpoch();
        if (libraryReviewEpochBeforeRetry == Long.MAX_VALUE) {
            if (reloaded != Long.MAX_VALUE) {
                return false;
            }
            libraryReviewEpochRetryPending = false;
            libraryReviewEpochBeforeRetry = -1;
            libraryReviewEpochActive = true;
            return true;
        }
        long candidateEpoch = libraryReviewEpochBeforeRetry + 1;
        if (reloaded == candidateEpoch) {
            libraryReviewEpochRetryPending = false;
            libraryReviewEpochBeforeRetry = -1;
            libraryReviewEpochActive = true;
            return true;
        }
        if (reloaded != libraryReviewEpochBeforeRetry) {
            return false;
        }
        OctavoLibrarySyncStore.MutationResult retried =
            librarySyncStore.beginReviewEpoch(true);
        if (!retried.succeeded()) {
            return false;
        }
        libraryReviewEpochRetryPending = false;
        libraryReviewEpochBeforeRetry = -1;
        libraryReviewEpochActive = true;
        return true;
    }

    private View createLocalLibraryReconciliationPanel(
        OctavoDesignTokens tokens,
        OctavoLibrarySyncStore.LocalReconciliation reconciliation) {
        boolean publication = reconciliation.kind
            == OctavoLibrarySyncStore.LocalReconciliationKind.PUBLICATION;
        OctavoLibraryStore.Book removable = publication
            ? libraryStore.findBook(reconciliation.digest) : null;
        boolean canRemove = removable != null && removable.imported
            && !removable.repairRequired
            && removable.byteCount == reconciliation.byteCount;
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        String message = "EPUB - "
            + humanReadableByteCount(reconciliation.byteCount)
            + " - " + shortDigest(reconciliation.digest);
        if (publication && hasText(libraryDiscoveryStatus)) {
            message += " - " + libraryDiscoveryStatus;
        }
        prompt.showRetryableFailure(
            publication
                ? "Local Library discovery needs Retry"
                : "Local removal suppression needs Retry",
            message,
            canRemove);
        if (canRemove) {
            prompt.cancelForTesting().setText("Remove from this device");
            prompt.cancelForTesting().setContentDescription(
                "Remove this EPUB from this device");
        }
        libraryPromptRetry = () -> {
            boolean resolved = reloadTransferStoresForExplicitRetry()
                && retryLocalLibraryReconciliation(reconciliation);
            showLibrary();
            if (!resolved && !"Verification in progress".equals(
                    libraryDiscoveryStatus)) {
                showOpenFailure(nonemptyMessage(
                    librarySyncStore.lastError(),
                    "Local Library reconciliation still needs Retry"));
            }
        };
        if (canRemove) {
            libraryPromptCancel = () -> {
                OctavoLibrarySyncStore.LocalReconciliation current =
                    librarySyncStore.localReconciliation();
                OctavoLibraryStore.Book currentBook =
                    libraryStore.findBook(reconciliation.digest);
                boolean exact = current != null
                    && current.kind
                       == OctavoLibrarySyncStore.LocalReconciliationKind
                           .PUBLICATION
                    && current.sameIdentity(reconciliation)
                    && currentBook != null && currentBook.imported
                    && !currentBook.repairRequired
                    && currentBook.byteCount == reconciliation.byteCount;
                boolean removed = false;
                if (exact) {
                    libraryFocusBookKey = currentBook.key;
                    libraryFocusAction = LIBRARY_FOCUS_REMOVE_LOCAL;
                    removed = removeImportedBook(currentBook);
                }
                showLibrary();
                if (!removed) {
                    showOpenFailure(nonemptyMessage(
                        firstNonemptyMessage(
                            bookTransferStore.lastError(),
                            libraryStore.lastError(),
                            librarySyncStore.lastError()),
                        "Removing this EPUB from this device still needs Retry"));
                }
            };
        }
        return prompt;
    }

    private boolean retryLocalLibraryReconciliation(
        OctavoLibrarySyncStore.LocalReconciliation expected) {
        OctavoLibrarySyncStore.LocalReconciliation current =
            librarySyncStore.localReconciliation();
        if (expected == null || current == null
            || !current.sameIdentity(expected)) {
            return false;
        }
        if (current.kind
            == OctavoLibrarySyncStore.LocalReconciliationKind.REMOVAL) {
            OctavoBookTransferStore.CleanupJob cleanup =
                cleanupJob(current.digest, current.byteCount);
            if (cleanup == null) {
                OctavoBookTransferStore.CleanupOutcome staged =
                    bookTransferStore.stageManagedCleanup(
                        current.digest, current.byteCount);
                if (!staged.result.succeeded()) {
                    return false;
                }
                cleanup = cleanupJob(staged.attemptSequence);
            }
            if (cleanup == null) {
                return false;
            }
            libraryDiscoveryStatus = null;
            return retryManagedCleanup(cleanup.attemptSequence);
        }

        OctavoLibraryStore.Book book =
            libraryStore.findBook(current.digest);
        if (book == null || !book.imported
            || book.byteCount != current.byteCount
            || book.repairRequired) {
            libraryDiscoveryStatus = "Exact local EPUB is unavailable";
            return false;
        }
        if (!book.identityVerified) {
            pendingLibraryIdentityBook = book;
            pendingLibraryIdentityMode =
                LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION;
            pendingLibraryIdentityRecordOpened = false;
            pendingLibraryIdentityLocalReconciliation = current;
            libraryDiscoveryStatus = "Verification in progress";
            return false;
        }
        return completeLocalPublicationAfterIdentity(book, current);
    }

    private View createLibraryCatalogReviewPanel(
        OctavoDesignTokens tokens,
        OctavoLibrarySyncStore.StagedPortable staged,
        int entryCount) {
        libraryPromptStaged = staged;
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showCatalogApproval(entryCount, staged.sha256);
        return prompt;
    }

    private View createLibraryCatalogOfferPanel(
        OctavoDesignTokens tokens,
        OctavoLibrarySyncStore.Candidate candidate) {
        libraryCatalogOfferManifestSha256 =
            sha256Hex(libraryCatalogManifestBytes);
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showOffer(candidate.digest, candidate.byteCount);
        return prompt;
    }

    private void approveLibraryCatalog(
        OctavoLibrarySyncStore.StagedPortable expected) {
        OctavoLibrarySyncStore.StagedPortable current =
            librarySyncStore.stagedPortable();
        if (current == null || expected == null
            || !current.sha256.equals(expected.sha256)) {
            showLibrary();
            showOpenFailure("The Library catalog review changed");
            return;
        }
        OctavoLibrarySyncStore.PortableMergeResult result =
            librarySyncStore.approveStagedPortable(expected.sha256);
        showLibrary();
        if (!result.succeeded()) {
            showOpenFailure(nonemptyMessage(
                librarySyncStore.lastError(),
                "The Library catalog could not be approved"));
        }
    }

    private void deferLibraryCatalogReview() {
        libraryCatalogReviewDeferred = true;
        showLibrary();
    }

    private void dismissLibraryCatalogOffer() {
        OctavoLibrarySyncStore.Candidate candidate = libraryCatalogOffer;
        String manifestSha256 = libraryCatalogOfferManifestSha256;
        if (!exactInstalledLibraryCatalogOffer(
                candidate, manifestSha256)) {
            libraryCatalogOffer = null;
            libraryCatalogOfferManifestSha256 = null;
            showLibrary();
            showOpenFailure("The Library offer changed before Not now");
            return;
        }
        OctavoLibrarySyncStore.MutationResult result =
            librarySyncStore.dismiss(candidate);
        libraryCatalogOffer = null;
        libraryCatalogOfferManifestSha256 = null;
        showLibrary();
        if (!result.succeeded()) {
            showOpenFailure(nonemptyMessage(
                librarySyncStore.lastError(),
                "The Library offer could not be deferred"));
        }
    }

    private void ignoreLibraryCatalogOffer() {
        OctavoLibrarySyncStore.Candidate candidate = libraryCatalogOffer;
        String manifestSha256 = libraryCatalogOfferManifestSha256;
        if (!exactInstalledLibraryCatalogOffer(
                candidate, manifestSha256)) {
            libraryCatalogOffer = null;
            libraryCatalogOfferManifestSha256 = null;
            showLibrary();
            showOpenFailure("The Library offer changed before hiding it");
            return;
        }
        OctavoLibrarySyncStore.MutationResult result =
            librarySyncStore.ignore(candidate);
        libraryCatalogOffer = null;
        libraryCatalogOfferManifestSha256 = null;
        showLibrary();
        if (!result.succeeded()) {
            showOpenFailure(nonemptyMessage(
                librarySyncStore.lastError(),
                "The Library offer could not be ignored"));
        }
    }

    private OctavoLibrarySyncStore.Candidate eligibleLibraryCatalogOffer() {
        OctavoBookManifest manifest = availableLibraryCatalogManifest();
        if (manifest == null || !canStartLibraryCatalogDownload()) {
            return null;
        }
        List<OctavoLibrarySyncStore.Candidate> candidates =
            librarySyncStore.reviewCandidates(locallyPresentBookDigests());
        for (OctavoLibrarySyncStore.Candidate candidate : candidates) {
            if (candidate.digest.equals(manifest.digest)
                && candidate.byteCount == manifest.byteCount
                && libraryCatalogCandidateHasSafeLocalState(candidate)
                && libraryMembershipAllowsCatalogOffer(
                    candidate.digest, candidate.byteCount)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean exactInstalledLibraryCatalogOffer(
        OctavoLibrarySyncStore.Candidate expected,
        String expectedManifestSha256) {
        if (expected == null || !hasText(expectedManifestSha256)
            || !expectedManifestSha256.equals(
                sha256Hex(libraryCatalogManifestBytes))
            || !canStartLibraryCatalogDownload()) {
            return false;
        }
        OctavoBookManifest manifest = availableLibraryCatalogManifest();
        if (manifest == null
            || !manifest.digest.equals(expected.digest)
            || manifest.byteCount != expected.byteCount) {
            return false;
        }
        for (OctavoLibrarySyncStore.Candidate current
                 : librarySyncStore.reviewCandidates(
                     locallyPresentBookDigests())) {
            if (current.sameIdentity(expected)
                && current.decision == expected.decision
                && current.kind == expected.kind
                && libraryCatalogCandidateHasSafeLocalState(current)
                && libraryMembershipAllowsCatalogOffer(
                    current.digest, current.byteCount)) {
                return true;
            }
        }
        return false;
    }

    private boolean canStartLibraryCatalogDownload() {
        OctavoBookManifest manifest = availableLibraryCatalogManifest();
        OctavoLibraryStore.Book manifestLocal = manifest == null
            ? null : libraryStore.findBook(manifest.digest);
        boolean repairOccupiesExistingSlot = manifestLocal != null
            && manifestLocal.imported
            && manifestLocal.repairRequired;
        if (!libraryReviewEpochActive
            || libraryReviewEpochRetryPending
            || libraryStore.mutationBlocked()
            || (libraryStore.bookCount() >= 64
                && !repairOccupiesExistingSlot)
            || librarySyncStore.stagedPortable() != null
            || librarySyncStore.transferReconciliation() != null
            || librarySyncStore.localReconciliation() != null
            || bookTransferStore.retainedIntentCount() != 0
            || librarySyncStore.loadStatus()
               == OctavoLibrarySyncStore.LoadStatus.CORRUPT_QUARANTINED
            || bookTransferStore.loadStatus()
               == OctavoBookTransferStore.LoadStatus.CORRUPT_QUARANTINED
            || bookTransferStore.futureManifestAttention()
               != OctavoBookTransferStore.Attention.NONE) {
            return false;
        }
        OctavoLibrarySyncStore.LoadStatus syncStatus =
            librarySyncStore.loadStatus();
        if (librarySyncLoadBlocked(syncStatus)) {
            return false;
        }
        OctavoBookTransferStore.LoadStatus transferStatus =
            bookTransferStore.loadStatus();
        return !bookTransferLoadBlocked(transferStatus)
            && transferStatus
                   != OctavoBookTransferStore.LoadStatus
                       .MANAGED_RECONCILE_REQUIRED;
    }

    private String libraryCatalogDurableAttentionMessage() {
        if (librarySyncStore.loadStatus()
            == OctavoLibrarySyncStore.LoadStatus.CORRUPT_QUARANTINED) {
            return "A quarantined synchronized-Library state requires explicit recovery attention.";
        }
        if (bookTransferStore.loadStatus()
            == OctavoBookTransferStore.LoadStatus.CORRUPT_QUARANTINED) {
            return "A quarantined book-transfer state requires explicit recovery attention.";
        }
        OctavoBookTransferStore.Attention future =
            bookTransferStore.futureManifestAttention();
        if (future
            == OctavoBookTransferStore.Attention.FUTURE_MANIFEST_CONFLICT) {
            return "A conflicting newer book manifest was retained for explicit review.";
        }
        if (future
            == OctavoBookTransferStore.Attention.FUTURE_MANIFEST_RETAINED) {
            return "A newer book manifest was retained for explicit review.";
        }
        return null;
    }

    private static boolean librarySyncLoadBlocked(
        OctavoLibrarySyncStore.LoadStatus status) {
        return status
                == OctavoLibrarySyncStore.LoadStatus.INITIAL_PUBLISH_FAILED
            || status == OctavoLibrarySyncStore.LoadStatus.CORRUPT_BLOCKED
            || status == OctavoLibrarySyncStore.LoadStatus.OVERBOUND_BLOCKED
            || status
                == OctavoLibrarySyncStore.LoadStatus.FUTURE_VERSION_BLOCKED
            || status
                == OctavoLibrarySyncStore.LoadStatus
                    .PUBLISH_UNCERTAIN_BLOCKED;
    }

    private static boolean bookTransferLoadBlocked(
        OctavoBookTransferStore.LoadStatus status) {
        return status
                == OctavoBookTransferStore.LoadStatus.CORRUPT_BLOCKED
            || status
                == OctavoBookTransferStore.LoadStatus.OVERBOUND_BLOCKED
            || status
                == OctavoBookTransferStore.LoadStatus
                    .FUTURE_VERSION_BLOCKED
            || status
                == OctavoBookTransferStore.LoadStatus
                    .PART_RECONCILE_BLOCKED
            || status
                == OctavoBookTransferStore.LoadStatus
                    .PUBLISH_UNCERTAIN_BLOCKED;
    }

    private boolean reloadLibrarySyncForExplicitRetry() {
        if (!reloadLibraryStoreForExplicitRetry()) {
            return false;
        }
        if (librarySyncLoadBlocked(librarySyncStore.loadStatus())) {
            librarySyncStore.load();
        }
        return !librarySyncLoadBlocked(librarySyncStore.loadStatus());
    }

    private boolean reloadLibraryStoreForExplicitRetry() {
        if (libraryStore.mutationBlocked()) {
            if (libraryFixture == null || !libraryFixture.isFile()) {
                return false;
            }
            libraryStore.reloadCatalog(libraryFixture);
            pendingImportAssociationBook = null;
            rejectedStagedImportCleanupBook = null;
        }
        return !libraryStore.mutationBlocked();
    }

    private boolean reloadTransferStoresForExplicitRetry() {
        if (!reloadLibrarySyncForExplicitRetry()) {
            return false;
        }
        if (bookTransferLoadBlocked(bookTransferStore.loadStatus())) {
            bookTransferStore.load();
        }
        return !bookTransferLoadBlocked(bookTransferStore.loadStatus());
    }

    private boolean explicitlyRetryLibraryTransfer() {
        if (!reloadTransferStoresForExplicitRetry()) {
            return false;
        }
        OctavoBookTransferStore.ActiveJob active =
            bookTransferStore.activeJob();
        if (active == null
            && (bookTransferStore.intentCount() != 0
                || librarySyncStore.transferReconciliation() == null)) {
            return false;
        }
        boolean advanced = resumeLibraryTransfer();
        if (advanced) {
            libraryTransferExplicitRetryRequired = false;
        }
        return advanced || transferFinalizationIdentityInstalled();
    }

    private boolean transferFinalizationIdentityInstalled() {
        return pendingLibraryIdentityBook != null
            && pendingLibraryIdentityMode
               == LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION
            && pendingLibraryIdentityTransferAttemptSequence > 0
            && hasText(pendingLibraryIdentityTransferAttemptId)
            && pendingLibraryIdentityTransferPhase != null;
    }

    private boolean libraryCatalogCandidateHasSafeLocalState(
        OctavoLibrarySyncStore.Candidate candidate) {
        if (candidate == null) {
            return false;
        }
        OctavoLibraryStore.Book local =
            libraryStore.findBook(candidate.digest);
        if (local == null) {
            return true;
        }
        // A same-length row is not exact presence until its incremental
        // identity gate succeeds. Defer the offer while verification is
        // outstanding; a typed repair row may be replaced by the download.
        return local.imported && local.repairRequired
            && local.key.equals(candidate.digest);
    }

    private OctavoBookManifest availableLibraryCatalogManifest() {
        if (libraryCatalogManifestBytes == null) {
            return null;
        }
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(libraryCatalogManifestBytes);
        if (decoded.status != OctavoBookManifest.DecodeStatus.READY) {
            return null;
        }
        OctavoBookManifest manifest = decoded.manifest();
        return manifest.digest.equals(libraryCatalogManifestDigest)
            ? manifest : null;
    }

    private OctavoLibrarySyncStore.LocalReconciliation
        cleanupSuppressionBlocker(
            OctavoBookTransferStore.CleanupJob cleanup) {
        if (cleanup == null
            || cleanup.purpose
               != OctavoBookTransferStore.CleanupPurpose.LOCAL_REMOVE
            || cleanup.phase
               != OctavoBookTransferStore.CleanupPhase
                   .AWAITING_SYNC_SUPPRESSION) {
            return null;
        }
        OctavoLibrarySyncStore.LocalReconciliation local =
            librarySyncStore.localReconciliation();
        return local == null
                || (local.digest.equals(cleanup.digest)
                    && local.byteCount == cleanup.byteCount)
            ? null : local;
    }

    private View createLibraryTransferPanel(OctavoDesignTokens tokens) {
        List<OctavoBookTransferStore.CleanupJob> cleanups =
            bookTransferStore.cleanupJobs();
        if (!cleanups.isEmpty()) {
            OctavoBookTransferStore.CleanupJob job = cleanups.get(0);
            OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
            boolean repair = job.purpose
                == OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE;
            boolean uncataloged = job.purpose
                == OctavoBookTransferStore.CleanupPurpose.UNCATALOGED;
            boolean locallyRemoved = job.phase
                == OctavoBookTransferStore.CleanupPhase
                    .AWAITING_SYNC_SUPPRESSION
                && libraryStore.findBook(job.digest) == null;
            OctavoLibrarySyncStore.LocalReconciliation localBlocker =
                cleanupSuppressionBlocker(job);
            OctavoLibraryStore.Book blockerBook = localBlocker != null
                    && localBlocker.kind
                       == OctavoLibrarySyncStore.LocalReconciliationKind
                           .PUBLICATION
                ? libraryStore.findBook(localBlocker.digest) : null;
            boolean canRemoveBlocker = blockerBook != null
                && blockerBook.imported && !blockerBook.repairRequired
                && blockerBook.byteCount == localBlocker.byteCount;
            prompt.showRetryableFailure(
                localBlocker != null
                    ? "Cleanup is waiting for Library reconciliation"
                    : repair
                    ? "EPUB repair cleanup needs Retry"
                    : locallyRemoved && !uncataloged
                    ? "Synchronized Library suppression needs Retry"
                    : "Local cleanup needs attention",
                localBlocker != null
                    ? "Removed EPUB - "
                        + humanReadableByteCount(job.byteCount)
                        + " - " + shortDigest(job.digest)
                        + " - First resolve retained Library marker - "
                        + shortDigest(localBlocker.digest)
                    : repair
                    ? "EPUB - " + humanReadableByteCount(job.byteCount)
                        + " - " + shortDigest(job.digest)
                        + " - Remove the corrupt local bytes before repair download"
                    : locallyRemoved && !uncataloged
                    ? "Removed from this device; synchronized-library "
                        + "suppression needs Retry. EPUB - "
                        + humanReadableByteCount(job.byteCount)
                        + " - " + shortDigest(job.digest)
                    : "EPUB - " + humanReadableByteCount(job.byteCount)
                        + " - " + shortDigest(job.digest)
                        + " - Retry removal from this device",
                canRemoveBlocker);
            if (canRemoveBlocker) {
                prompt.cancelForTesting().setText("Remove from this device");
                prompt.cancelForTesting().setContentDescription(
                    "Remove the blocking EPUB from this device");
            }
            libraryPromptRetry = () -> {
                boolean retryReady = reloadTransferStoresForExplicitRetry();
                boolean completed;
                if (retryReady && localBlocker != null) {
                    OctavoLibrarySyncStore.LocalReconciliation current =
                        librarySyncStore.localReconciliation();
                    boolean blockerResolved = current != null
                        && current.sameIdentity(localBlocker)
                        && retryLocalLibraryReconciliation(current);
                    completed = blockerResolved
                        && retryCleanupByPurpose(job.attemptSequence);
                } else {
                    completed = retryReady
                        && retryCleanupByPurpose(job.attemptSequence);
                }
                showLibrary();
                if (!completed
                    && !"Verification in progress".equals(
                        libraryDiscoveryStatus)
                    && !(job.purpose
                         == OctavoBookTransferStore.CleanupPurpose.LOCAL_REMOVE
                         && localRemovalBoundaryComplete(
                             job.attemptSequence))) {
                    showOpenFailure(nonemptyMessage(
                        firstNonemptyMessage(
                            bookTransferStore.lastError(),
                            librarySyncStore.lastError(),
                            libraryStore.lastError()),
                        "Removing this EPUB still needs Retry"));
                }
            };
            if (canRemoveBlocker) {
                libraryPromptCancel = () -> {
                    boolean retryReady =
                        reloadTransferStoresForExplicitRetry();
                    OctavoLibrarySyncStore.LocalReconciliation current =
                        librarySyncStore.localReconciliation();
                    OctavoLibraryStore.Book currentBook =
                        libraryStore.findBook(localBlocker.digest);
                    boolean exact = retryReady && current != null
                        && current.kind
                           == OctavoLibrarySyncStore.LocalReconciliationKind
                               .PUBLICATION
                        && current.sameIdentity(localBlocker)
                        && currentBook != null && currentBook.imported
                        && !currentBook.repairRequired
                        && currentBook.byteCount == localBlocker.byteCount;
                    boolean removed = false;
                    if (exact) {
                        libraryFocusBookKey = currentBook.key;
                        libraryFocusAction = LIBRARY_FOCUS_REMOVE_LOCAL;
                        removed = removeImportedBook(currentBook);
                    }
                    showLibrary();
                    if (!removed) {
                        showOpenFailure(nonemptyMessage(
                            firstNonemptyMessage(
                                bookTransferStore.lastError(),
                                libraryStore.lastError(),
                                librarySyncStore.lastError()),
                            "The blocking EPUB still needs removal Retry"));
                    }
                };
            }
            return prompt;
        }

        OctavoBookTransferStore.ActiveJob job =
            bookTransferStore.activeJob();
        if (job == null) {
            if (bookTransferStore.intentCount() == 0) {
                return null;
            }
            OctavoLibrarySyncPrompt blocked = newLibrarySyncPrompt();
            blocked.showRetryableFailure(
                "Book transfer needs attention",
                nonemptyMessage(
                    bookTransferStore.lastError(),
                    "A queued transfer cannot be activated safely"),
                false);
            libraryPromptRetry = () -> {
                boolean advanced = explicitlyRetryLibraryTransfer();
                showLibrary();
                if (!advanced) {
                    showOpenFailure(nonemptyMessage(
                        bookTransferStore.lastError(),
                        "The queued transfer still needs explicit recovery"));
                }
            };
            return blocked;
        }

        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        boolean reconciled = exactLibraryTransferReconciliation(job)
            || ((job.phase
                    == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
                 || job.phase
                    == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED)
                && librarySyncStore.decision(job.digest)
                   == OctavoLibrarySyncStore.Decision.DOWNLOADED);
        String status = reconciled
            ? transferStatus(job)
            : "EPUB - " + humanReadableByteCount(job.byteCount)
                + " - " + shortDigest(job.digest)
                + " - Download setup needs reconciliation Retry";
        if (libraryTransferExplicitRetryRequired) {
            status += " - Retained transfer needs explicit Retry";
        }
        boolean progress = job.direction
                == OctavoBookTransferStore.Direction.DOWNLOAD
            && job.durableDirection
               == OctavoBookTransferStore.DurableDirection.FORWARD
            && !job.retryRequired
            && job.attention == OctavoBookTransferStore.Attention.NONE
            && !libraryTransferExplicitRetryRequired
            && reconciled
            && (job.phase == OctavoBookTransferStore.Phase.STAGED
                || job.phase
                   == OctavoBookTransferStore.Phase.TRANSFERRING)
            && job.completedPrefix < job.chunkCount;
        boolean cancellable = job.durableDirection
                == OctavoBookTransferStore.DurableDirection.FORWARD
            && (job.direction
                    == OctavoBookTransferStore.Direction.DOWNLOAD
                ? (job.phase
                        != OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
                    && job.phase
                        != OctavoBookTransferStore.Phase
                            .LOCAL_CATALOG_LINKED)
                : (job.phase == OctavoBookTransferStore.Phase.STAGED
                    || job.phase
                       == OctavoBookTransferStore.Phase.TRANSFERRING
                    || job.phase
                       == OctavoBookTransferStore.Phase.BYTES_VERIFIED));
        if (progress) {
            long completedBytes = Math.min(
                job.byteCount,
                (long)job.completedPrefix * 4L * 1024L * 1024L);
            prompt.showTransferProgress(
                job.digest, job.byteCount, completedBytes,
                job.completedPrefix, job.chunkCount);
        } else {
            prompt.showRetryableFailure(
                job.direction == OctavoBookTransferStore.Direction.DOWNLOAD
                    ? "Book download needs attention"
                    : "Book transfer needs attention",
                status,
                cancellable);
        }
        libraryPromptRetry = () -> {
            boolean advanced = explicitlyRetryLibraryTransfer();
            showLibrary();
            if (!advanced
                && pendingLibraryIdentityMode
                   != LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION) {
                showOpenFailure(nonemptyMessage(
                    firstNonemptyMessage(
                        bookTransferStore.lastError(),
                        librarySyncStore.lastError(),
                        libraryStore.lastError()),
                    "The book transfer still needs Retry"));
            }
        };
        if (cancellable) {
            libraryPromptCancel = () -> {
                boolean cancelled = cancelLibraryTransfer();
                showLibrary();
                if (!cancelled) {
                    showOpenFailure(nonemptyMessage(
                        bookTransferStore.lastError(),
                        "Cancellation cleanup needs Retry"));
                }
            };
        }
        return prompt;
    }

    private View createLibraryTransferReconciliationPanel(
        OctavoDesignTokens tokens,
        OctavoLibrarySyncStore.TransferReconciliation reconciliation) {
        OctavoLibrarySyncPrompt prompt = newLibrarySyncPrompt();
        prompt.showRetryableFailure(
            "Book download needs reconciliation",
            "EPUB - " + humanReadableByteCount(reconciliation.byteCount)
                + " - " + shortDigest(reconciliation.digest),
            false);
        libraryPromptRetry = () -> {
            boolean reconciled = reloadTransferStoresForExplicitRetry()
                && retryLibraryTransferReconciliation(reconciliation);
            showLibrary();
            if (!reconciled) {
                showOpenFailure(nonemptyMessage(
                    librarySyncStore.lastError(),
                    "The book download still needs reconciliation"));
            }
        };
        return prompt;
    }

    private String transferStatus(OctavoBookTransferStore.ActiveJob job) {
        String identity = "EPUB - " + humanReadableByteCount(job.byteCount)
            + " - " + shortDigest(job.digest);
        if (job.durableDirection
            == OctavoBookTransferStore.DurableDirection.CANCEL) {
            return identity + " - Cancellation cleanup needs Retry";
        }
        if (job.direction != OctavoBookTransferStore.Direction.DOWNLOAD) {
            return identity + " - Waiting for its explicit caller";
        }
        if ((job.phase == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
             || job.phase
                == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED)
            && hasText(libraryTransferCatalogRetryMessage)) {
            return identity + " - " + libraryTransferCatalogRetryMessage;
        }
        if (job.attention != OctavoBookTransferStore.Attention.NONE) {
            return identity + " - " + transferAttentionMessage(
                job.attention);
        }
        if (job.retryRequired) {
            return identity + " - Durable transfer state needs Retry";
        }
        if (job.phase == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
            || job.phase
               == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED) {
            if (libraryStore.mutationBlocked()) {
                return identity + " - " + nonemptyMessage(
                    libraryStore.lastError(),
                    "The local Library catalog is blocked; publication needs Retry");
            }
            if (libraryStore.findBook(job.digest) == null
                && libraryStore.bookCount() >= 64) {
                return identity
                    + " - The Library is full; publication needs Retry";
            }
        }
        if (job.phase == OctavoBookTransferStore.Phase.STAGED
            || job.phase == OctavoBookTransferStore.Phase.TRANSFERRING) {
            return String.format(
                Locale.ROOT,
                "%s - %d of %d chunks verified",
                identity, job.completedPrefix, job.chunkCount);
        }
        if (job.phase == OctavoBookTransferStore.Phase.BYTES_VERIFIED) {
            return identity + " - Exact bytes verified - Reader check pending";
        }
        if (job.phase == OctavoBookTransferStore.Phase.READER0_VALIDATED) {
            return identity + " - Reader-validated - Local publication pending";
        }
        if (job.phase == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED) {
            return identity + " - Managed EPUB published - Library link pending";
        }
        if (job.phase
            == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED) {
            return identity + " - Download completion needs Retry";
        }
        return identity + " - Transfer needs Retry";
    }

    private static String transferAttentionMessage(
        OctavoBookTransferStore.Attention attention) {
        if (attention == OctavoBookTransferStore.Attention.RETRY_CHUNK) {
            return "The next exact chunk needs Retry";
        }
        if (attention
            == OctavoBookTransferStore.Attention.PREFIX_REPAIRED) {
            return "A staged prefix was repaired; exact transfer Retry is required";
        }
        if (attention
            == OctavoBookTransferStore.Attention.EXTRA_TRUNCATED) {
            return "Unexpected staged bytes were removed; Retry is required";
        }
        if (attention
            == OctavoBookTransferStore.Attention.CANCEL_CLEANUP) {
            return "Cancellation cleanup needs Retry";
        }
        if (attention
            == OctavoBookTransferStore.Attention.MANAGED_RECONCILE_REQUIRED) {
            return "Managed publication needs reconciliation Retry";
        }
        if (attention
            == OctavoBookTransferStore.Attention.READER0_REJECTED) {
            return "Reader validation rejected these bytes";
        }
        if (attention
            == OctavoBookTransferStore.Attention.CATALOG_LINK_FAILED) {
            return "The local Library link needs Retry";
        }
        if (attention
                == OctavoBookTransferStore.Attention
                    .FUTURE_MANIFEST_RETAINED
            || attention
                == OctavoBookTransferStore.Attention
                    .FUTURE_MANIFEST_CONFLICT) {
            return "A newer transfer manifest was retained for explicit review";
        }
        if (attention
            == OctavoBookTransferStore.Attention.MANAGED_DESTINATION_CONFLICT) {
            return "The managed destination conflicts with these exact bytes";
        }
        if (attention
            == OctavoBookTransferStore.Attention.COMPLETE_HASH_MISMATCH) {
            return "The complete staged EPUB failed exact identity verification";
        }
        if (attention
            == OctavoBookTransferStore.Attention.REMOTE_OBJECT_MISMATCH) {
            return "The simulated remote object failed exact verification";
        }
        if (attention
            == OctavoBookTransferStore.Attention.UPLOAD_SOURCE_REQUIRED) {
            return "The explicit upload source must be provided again";
        }
        return "Book transfer needs explicit Retry";
    }

    private boolean downloadLibraryCatalogOffer() {
        OctavoLibrarySyncStore.Candidate candidate = libraryCatalogOffer;
        String promptManifestSha256 =
            libraryCatalogOfferManifestSha256;
        OctavoBookManifest manifest = availableLibraryCatalogManifest();
        if (candidate == null || manifest == null
            || !candidate.digest.equals(manifest.digest)
            || candidate.byteCount != manifest.byteCount
            || !exactInstalledLibraryCatalogOffer(
                candidate, promptManifestSha256)) {
            showLibrary();
            showOpenFailure("The Library offer changed before Download");
            return false;
        }
        OctavoBookTransferStore.StageOutcome staged =
            bookTransferStore.stageDownload(libraryCatalogManifestBytes);
        if (!staged.result.succeeded() || !staged.active) {
            showLibrary();
            showOpenFailure(nonemptyMessage(
                bookTransferStore.lastError(),
                "The download could not be durably queued"));
            return false;
        }
        // This attempt was durably created by the current, still-explicit
        // Download action. Only jobs reconstructed by a later Activity load
        // must stop again at the explicit-Retry gate.
        libraryTransferExplicitRetryRequired = false;
        OctavoBookTransferStore.ActiveJob job =
            bookTransferStore.activeJob();
        if (job == null || job.attemptSequence != staged.attemptSequence
            || !job.attemptId.equals(staged.attemptId)
            || !job.digest.equals(candidate.digest)
            || job.byteCount != candidate.byteCount
            || !ensureLibraryTransferReconciliation(job, candidate)) {
            showLibrary();
            showOpenFailure(nonemptyMessage(
                librarySyncStore.lastError(),
                "The queued download needs reconciliation Retry"));
            return false;
        }
        transientTransferReader0Title = null;
        libraryCatalogOffer = null;
        libraryCatalogOfferManifestSha256 = null;
        showLibrary();
        return true;
    }

    private boolean ensureLibraryTransferReconciliation(
        OctavoBookTransferStore.ActiveJob job,
        OctavoLibrarySyncStore.Candidate knownCandidate) {
        if (job == null
            || job.direction != OctavoBookTransferStore.Direction.DOWNLOAD) {
            return false;
        }
        String manifestHash = hexBytes(job.manifestHash());
        OctavoLibrarySyncStore.TransferReconciliation current =
            librarySyncStore.transferReconciliation();
        if (current != null) {
            return exactLibraryTransferReconciliation(job);
        }
        OctavoLibrarySyncStore.Candidate candidate = knownCandidate;
        if (candidate == null) {
            for (OctavoLibrarySyncStore.Candidate value
                     : librarySyncStore.reviewCandidates(
                         locallyPresentBookDigests())) {
                if (value.digest.equals(job.digest)
                    && value.byteCount == job.byteCount) {
                    candidate = value;
                    break;
                }
            }
        }
        return candidate != null
            && librarySyncStore.reconcileTransferAttempt(
                candidate, job.attemptId, manifestHash).succeeded();
    }

    private boolean exactLibraryTransferReconciliation(
        OctavoBookTransferStore.ActiveJob job) {
        if (job == null) {
            return false;
        }
        OctavoLibrarySyncStore.TransferReconciliation current =
            librarySyncStore.transferReconciliation();
        return current != null
            && current.digest.equals(job.digest)
            && current.byteCount == job.byteCount
            && current.attemptId.equals(job.attemptId)
            && current.manifestSha256.equals(
                hexBytes(job.manifestHash()));
    }

    private boolean resumeLibraryTransfer() {
        for (int transition = 0; transition < 12; ++transition) {
            OctavoBookTransferStore.ActiveJob job =
                bookTransferStore.activeJob();
            if (job == null) {
                OctavoLibrarySyncStore.TransferReconciliation pending =
                    librarySyncStore.transferReconciliation();
                return pending == null
                    || retryLibraryTransferReconciliation(pending);
            }
            if (job.direction != OctavoBookTransferStore.Direction.DOWNLOAD) {
                return false;
            }
            if (job.durableDirection
                == OctavoBookTransferStore.DurableDirection.CANCEL) {
                return cancelLibraryTransfer();
            }
            if (!ensureLibraryTransferReconciliation(job, null)
                && librarySyncStore.decision(job.digest)
                   != OctavoLibrarySyncStore.Decision.DOWNLOADED) {
                return false;
            }

            if (job.phase == OctavoBookTransferStore.Phase.STAGED
                || job.phase == OctavoBookTransferStore.Phase.TRANSFERRING) {
                if (job.phase == OctavoBookTransferStore.Phase.TRANSFERRING
                    && job.completedPrefix == job.chunkCount) {
                    if (!bookTransferStore.finishDownload(
                            job.callbackToken).succeeded()) {
                        return false;
                    }
                    continue;
                }
                // The raw next chunk remains caller-owned. Retry never
                // invents a provider or advances bytes by itself.
                return true;
            }

            if (job.phase == OctavoBookTransferStore.Phase.BYTES_VERIFIED) {
                File staging =
                    bookTransferStore.stagedDownloadForReader0(job);
                OctavoManagedEpubValidator.Result validated =
                    OctavoManagedEpubValidator.validate(
                        this, staging, appearance);
                if (!validated.valid) {
                    bookTransferStore.recordReader0Rejected(
                        job.callbackToken);
                    transientTransferReader0Title = null;
                    return false;
                }
                transientTransferReader0Title = validated.title;
                if (!bookTransferStore.markReader0Validated(
                        job.callbackToken).succeeded()) {
                    return false;
                }
                continue;
            }

            if (job.phase
                == OctavoBookTransferStore.Phase.READER0_VALIDATED) {
                if (!hasText(transientTransferReader0Title)) {
                    File staging =
                        bookTransferStore.stagedDownloadForReader0(job);
                    OctavoManagedEpubValidator.Result validated =
                        OctavoManagedEpubValidator.validate(
                            this, staging, appearance);
                    if (!validated.valid) {
                        // publishManaged performs the exact durable hash
                        // check and retains a typed mismatch if bytes moved.
                        bookTransferStore.publishManaged(
                            job.callbackToken,
                            libraryStore.documentDirectoryForTesting());
                        return false;
                    }
                    transientTransferReader0Title = validated.title;
                }
                OctavoBookTransferStore.MutationResult published =
                    bookTransferStore.loadStatus()
                        == OctavoBookTransferStore.LoadStatus
                            .MANAGED_RECONCILE_REQUIRED
                    ? bookTransferStore.reconcileManagedPublication(
                        job.callbackToken,
                        libraryStore.documentDirectoryForTesting())
                    : bookTransferStore.publishManaged(
                        job.callbackToken,
                        libraryStore.documentDirectoryForTesting());
                if (!published.succeeded()) {
                    return false;
                }
                continue;
            }

            if (job.phase
                == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED) {
                return beginTransferFinalizationIdentity(job);
            }

            if (job.phase
                == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED) {
                if (librarySyncStore.decision(job.digest)
                    != OctavoLibrarySyncStore.Decision.DOWNLOADED) {
                    return false;
                }
                return beginTransferFinalizationIdentity(job);
            }
            return false;
        }
        return false;
    }

    private boolean beginTransferFinalizationIdentity(
        OctavoBookTransferStore.ActiveJob job) {
        if (job == null
            || job.direction != OctavoBookTransferStore.Direction.DOWNLOAD
            || job.durableDirection
               != OctavoBookTransferStore.DurableDirection.FORWARD
            || (job.phase
                    != OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
                && job.phase
                    != OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED)) {
            return false;
        }
        OctavoLibraryStore.TransferredBookOutcome outcome =
            libraryStore.transferredBookForIdentityVerification(
                job.digest, job.byteCount);
        if (outcome.status
                == OctavoLibraryStore.TransferredBookStatus
                    .BYTES_UNAVAILABLE
            || outcome.status
                == OctavoLibraryStore.TransferredBookStatus
                    .LOCAL_CONFLICT) {
            boolean converted = convertPublishedTransferToRepairCleanup(
                job.digest, job.byteCount,
                job.attemptSequence, job.attemptId, job.phase);
            if (!converted) {
                libraryTransferExplicitRetryRequired = true;
            }
            return converted;
        }
        if (outcome.status
            != OctavoLibraryStore.TransferredBookStatus.READY) {
            retainTransferCatalogRetry(job, outcome.status);
            return false;
        }

        OctavoManagedEpubValidator.Result validated =
            OctavoManagedEpubValidator.validate(
                this, outcome.book.file, appearance);
        if (!validated.valid) {
            transientTransferReader0Title = null;
            boolean converted = convertPublishedTransferToRepairCleanup(
                job.digest, job.byteCount,
                job.attemptSequence, job.attemptId, job.phase);
            if (!converted) {
                libraryTransferExplicitRetryRequired = true;
            }
            return converted;
        }
        transientTransferReader0Title = validated.title;
        libraryTransferCatalogRetryMessage = null;
        pendingLibraryIdentityBook = outcome.book;
        pendingLibraryIdentityMode =
            LIBRARY_IDENTITY_MODE_TRANSFER_FINALIZATION;
        pendingLibraryIdentityRecordOpened = false;
        pendingLibraryIdentityLocalReconciliation = null;
        pendingLibraryIdentityTransferAttemptSequence =
            job.attemptSequence;
        pendingLibraryIdentityTransferAttemptId = job.attemptId;
        pendingLibraryIdentityTransferPhase = job.phase;
        return false;
    }

    private void retainTransferCatalogRetry(
        OctavoBookTransferStore.ActiveJob expected,
        OctavoLibraryStore.TransferredBookStatus status) {
        transientTransferReader0Title = null;
        libraryTransferExplicitRetryRequired = true;
        if (status
            == OctavoLibraryStore.TransferredBookStatus.CATALOG_FULL) {
            libraryTransferCatalogRetryMessage =
                "The Library is full; publishing this downloaded EPUB needs Retry";
        } else if (status
                   == OctavoLibraryStore.TransferredBookStatus
                       .CATALOG_BLOCKED) {
            libraryTransferCatalogRetryMessage = nonemptyMessage(
                libraryStore.lastError(),
                "The local Library catalog is blocked; publication needs Retry");
        } else {
            libraryTransferCatalogRetryMessage =
                "Local Library publication preflight changed; explicit Retry is required";
        }
        recordTransferCatalogRetry(
            expected.attemptSequence, expected.attemptId, expected.phase);
    }

    private void recordTransferCatalogRetry(
        long expectedAttemptSequence,
        String expectedAttemptId,
        OctavoBookTransferStore.Phase expectedPhase) {
        OctavoBookTransferStore.ActiveJob current =
            bookTransferStore.activeJob();
        if (expectedPhase
                != OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
            || current == null
            || current.phase != expectedPhase
            || current.attemptSequence != expectedAttemptSequence
            || !current.attemptId.equals(expectedAttemptId)) {
            return;
        }
        bookTransferStore.recordLocalCatalogLinkFailed(
            current.callbackToken);
    }

    private boolean completeTransferFinalizationAfterCatalogRecord(
        long expectedAttemptSequence,
        String expectedAttemptId,
        OctavoBookTransferStore.Phase expectedPhase) {
        OctavoBookTransferStore.ActiveJob job =
            bookTransferStore.activeJob();
        OctavoLibraryStore.Book associated = job == null
            ? null : libraryStore.findBook(job.digest);
        if (job == null
            || job.direction != OctavoBookTransferStore.Direction.DOWNLOAD
            || job.durableDirection
               != OctavoBookTransferStore.DurableDirection.FORWARD
            || (expectedPhase
                    != OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
                && expectedPhase
                    != OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED)
            || job.phase != expectedPhase
            || job.attemptSequence != expectedAttemptSequence
            || !job.attemptId.equals(expectedAttemptId)
            || associated == null || !associated.imported
            || associated.repairRequired
            || !associated.identityVerified
            || associated.byteCount != job.byteCount
            || (expectedPhase
                    == OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED
                && librarySyncStore.decision(job.digest)
                   != OctavoLibrarySyncStore.Decision.DOWNLOADED)) {
            return false;
        }
        if (expectedPhase
            == OctavoBookTransferStore.Phase.MANAGED_PUBLISHED) {
            OctavoLibrarySyncStore.TransferReconciliation transfer =
                librarySyncStore.transferReconciliation();
            if (transfer != null) {
                if (!exactLibraryTransferReconciliation(job)
                    || !librarySyncStore.completeDownloaded(
                        transfer, true).succeeded()) {
                    return false;
                }
            } else if (librarySyncStore.decision(job.digest)
                       != OctavoLibrarySyncStore.Decision.DOWNLOADED) {
                return false;
            }
            if (!bookTransferStore.markLocalCatalogLinked(
                    job.callbackToken).succeeded()) {
                return false;
            }
            job = bookTransferStore.activeJob();
            if (job == null
                || job.phase
                   != OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED
                || job.attemptSequence != expectedAttemptSequence
                || !job.attemptId.equals(expectedAttemptId)) {
                return false;
            }
        }
        boolean finalized = bookTransferStore.finalizeTransfer(
            job.callbackToken).succeeded();
        if (finalized) {
            transientTransferReader0Title = null;
        }
        return finalized;
    }

    private boolean convertPublishedTransferToRepairCleanup(
        String expectedDigest,
        long expectedByteCount,
        long expectedAttemptSequence,
        String expectedAttemptId,
        OctavoBookTransferStore.Phase expectedPhase) {
        // A conversion may activate the next queued attempt. Never let its
        // Reader0 gate inherit the prior attempt's transient title.
        transientTransferReader0Title = null;
        OctavoBookTransferStore.ActiveJob active =
            bookTransferStore.activeJob();
        if (!hasText(expectedDigest) || expectedByteCount <= 0
            || active == null
            || active.direction
               != OctavoBookTransferStore.Direction.DOWNLOAD
            || active.durableDirection
               != OctavoBookTransferStore.DurableDirection.FORWARD
            || (expectedPhase
                    != OctavoBookTransferStore.Phase.MANAGED_PUBLISHED
                && expectedPhase
                    != OctavoBookTransferStore.Phase.LOCAL_CATALOG_LINKED)
            || active.phase != expectedPhase
            || active.attemptSequence != expectedAttemptSequence
            || !active.attemptId.equals(expectedAttemptId)
            || !active.digest.equals(expectedDigest)
            || active.byteCount != expectedByteCount) {
            return false;
        }
        // The exact O1BQ attempt owns the conversion from this point. Revoke
        // any O6 verification capability so it cannot outlive that attempt,
        // including across a failed or uncertain cleanup publication.
        libraryStore.cancelBookIdentityVerification();
        byte[] expectedManifestHash = active.manifestHash();
        OctavoBookTransferStore.CleanupOutcome converted =
            bookTransferStore.convertPublishedDownloadToRepairCleanup(
                active.callbackToken);
        if (!(converted.result.succeeded()
              || converted.result
                 == OctavoBookTransferStore.MutationResult
                     .PUBLISH_UNCERTAIN)
            || converted.attemptSequence != expectedAttemptSequence) {
            return false;
        }
        OctavoBookTransferStore.CleanupJob cleanup =
            cleanupJob(expectedAttemptSequence);
        boolean exactCleanup = cleanup != null
            && cleanup.purpose
               == OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE
            && cleanup.digest.equals(expectedDigest)
            && cleanup.byteCount == expectedByteCount
            && expectedAttemptId.equals(cleanup.originAttemptId)
            && MessageDigest.isEqual(
                expectedManifestHash, cleanup.originManifestHash());
        if (exactCleanup) {
            libraryTransferCatalogRetryMessage = null;
        }
        return exactCleanup;
    }

    private boolean retryLibraryTransferReconciliation(
        OctavoLibrarySyncStore.TransferReconciliation expected) {
        if (expected == null) {
            return false;
        }
        OctavoBookTransferStore.ActiveJob active =
            bookTransferStore.activeJob();
        if (active != null) {
            return resumeLibraryTransfer();
        }
        if (libraryStore.hasExactManagedBook(
                expected.digest, expected.byteCount)) {
            return librarySyncStore.completeDownloaded(
                expected, true).succeeded();
        }
        if (bookTransferStore.intentCount() != 0) {
            return false;
        }
        return librarySyncStore.releaseTransferReconciliation(
            expected, true).succeeded();
    }

    private boolean cancelLibraryTransfer() {
        OctavoBookTransferStore.ActiveJob active =
            bookTransferStore.activeJob();
        if (active == null) {
            return false;
        }
        OctavoLibrarySyncStore.TransferReconciliation reconciliation =
            librarySyncStore.transferReconciliation();
        OctavoBookTransferStore.MutationResult cancelled =
            bookTransferStore.cancelActive(active.callbackToken);
        if (!cancelled.succeeded()) {
            return false;
        }
        transientTransferReader0Title = null;
        if (reconciliation != null
            && bookTransferStore.activeJob() == null) {
            return librarySyncStore.releaseTransferReconciliation(
                reconciliation, true).succeeded();
        }
        return true;
    }

    private List<String> locallyPresentBookDigests() {
        ArrayList<String> result = new ArrayList<>();
        for (OctavoLibraryStore.Book book : libraryStore.books()) {
            if (book.imported && !book.repairRequired
                && book.identityVerified) {
                result.add(book.key);
            }
        }
        return result;
    }

    private boolean recordLocalDiscovery(OctavoLibraryStore.Book book) {
        if (book == null || !book.imported || book.repairRequired
            || !book.identityVerified) {
            return false;
        }
        OctavoLibraryPortable.Descriptor descriptor =
            new OctavoLibraryPortable.Descriptor(book.key, book.byteCount);
        OctavoLibrarySyncStore.LocalReconciliation pending =
            librarySyncStore.localReconciliation();
        if (pending == null) {
            OctavoLibrarySyncStore.MutationResult staged =
                librarySyncStore.stageLocalPublication(descriptor);
            if (!staged.succeeded()) {
                libraryDiscoveryRetryBookKey = book.key;
                return false;
            }
            pending = librarySyncStore.localReconciliation();
        }
        if (pending == null
            || pending.kind
               != OctavoLibrarySyncStore.LocalReconciliationKind.PUBLICATION
            || !pending.descriptor().sameIdentity(descriptor)) {
            libraryDiscoveryRetryBookKey = book.key;
            return false;
        }
        boolean completed = librarySyncStore.finalizeLocalReconciliation(
            pending, true).succeeded();
        if (completed && book.key.equals(libraryDiscoveryRetryBookKey)) {
            libraryDiscoveryRetryBookKey = null;
        } else if (!completed) {
            libraryDiscoveryRetryBookKey = book.key;
        }
        return completed;
    }

    private boolean finalizeLocalRemoval(
        OctavoLibraryPortable.Descriptor descriptor) {
        // The exact local catalog row and managed bytes are already absent at
        // this point. Only now may O1LS replace/clear a publication marker or
        // stage LOCAL_REMOVED suppression; an O1LS failure must not block the
        // local removal boundary.
        OctavoLibrarySyncStore.LocalReconciliation before =
            librarySyncStore.localReconciliation();
        if (librarySyncStore.decision(descriptor.digest) == null
            && before == null) {
            return true;
        }
        if (!librarySyncStore.stageLocalRemoval(descriptor).succeeded()) {
            return false;
        }
        OctavoLibrarySyncStore.LocalReconciliation pending =
            librarySyncStore.localReconciliation();
        if (librarySyncStore.decision(descriptor.digest) == null) {
            return pending == null;
        }
        if (pending == null
            || pending.kind
               != OctavoLibrarySyncStore.LocalReconciliationKind.REMOVAL
            || !pending.descriptor().sameIdentity(descriptor)) {
            return false;
        }
        return librarySyncStore.finalizeLocalReconciliation(
            pending, true).succeeded();
    }

    private boolean removeImportedBook(OctavoLibraryStore.Book book) {
        if (book == null || !book.imported
            || libraryStore.findBook(book.key) == null) {
            return false;
        }
        OctavoBookTransferStore.CleanupJob existing =
            cleanupJob(book.key, book.byteCount);
        long attemptSequence;
        if (existing != null) {
            if (existing.purpose
                != OctavoBookTransferStore.CleanupPurpose.LOCAL_REMOVE) {
                return false;
            }
            attemptSequence = existing.attemptSequence;
        } else {
            OctavoBookTransferStore.CleanupOutcome staged =
                bookTransferStore.stageManagedCleanup(
                    book.key, book.byteCount);
            if (!staged.result.succeeded()) {
                return false;
            }
            attemptSequence = staged.attemptSequence;
        }
        return retryManagedCleanup(attemptSequence)
            || localRemovalBoundaryComplete(attemptSequence);
    }

    private boolean retryCleanupByPurpose(long attemptSequence) {
        OctavoBookTransferStore.CleanupJob job =
            cleanupJob(attemptSequence);
        if (job == null) {
            return false;
        }
        if (job.purpose
            == OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE) {
            return retryRepairManagedCleanup(attemptSequence);
        }
        if (job.purpose
            == OctavoBookTransferStore.CleanupPurpose.UNCATALOGED) {
            return retryUncatalogedManagedCleanup(attemptSequence);
        }
        return retryManagedCleanup(attemptSequence);
    }

    private boolean retryRepairManagedCleanup(long attemptSequence) {
        OctavoBookTransferStore.CleanupJob job =
            cleanupJob(attemptSequence);
        if (job == null
            || job.purpose
               != OctavoBookTransferStore.CleanupPurpose.REPAIR_REPLACE) {
            return false;
        }
        OctavoLibraryStore.Book local =
            libraryStore.findBook(job.digest);
        byte[] repairOriginManifestHash = job.originManifestHash();
        boolean convertedRepair = hasText(job.originAttemptId)
            && job.originAttemptId.length() == 32
            && repairOriginManifestHash != null
            && repairOriginManifestHash.length == 32;
        if (job.phase
            == OctavoBookTransferStore.CleanupPhase.AWAITING_CATALOG_UNLINK) {
            if (local != null) {
                if (!local.imported
                    || (!local.repairRequired && !convertedRepair)
                    || local.byteCount != job.byteCount
                    || !libraryStore.removeBookRecordOnly(job.digest)) {
                    return false;
                }
            }
            if (!bookTransferStore.markCleanupCatalogUnlinked(
                    job.callbackToken).succeeded()) {
                return false;
            }
            job = cleanupJob(attemptSequence);
            if (job == null) {
                return false;
            }
        } else if (local != null) {
            return false;
        }
        if (job.phase
            == OctavoBookTransferStore.CleanupPhase.READY_TO_DELETE) {
            if (!bookTransferStore.deleteManagedForCleanup(
                    job.callbackToken,
                    libraryStore.documentDirectoryForTesting()).succeeded()) {
                return false;
            }
            job = cleanupJob(attemptSequence);
            if (job == null) {
                return false;
            }
        }
        if (job.phase
            != OctavoBookTransferStore.CleanupPhase
                .AWAITING_SYNC_SUPPRESSION) {
            return false;
        }
        OctavoLibrarySyncStore.TransferReconciliation transfer =
            librarySyncStore.transferReconciliation();
        if (transfer != null) {
            OctavoBookTransferStore.ActiveJob active =
                bookTransferStore.activeJob();
            byte[] originManifestHash = job.originManifestHash();
            if (!transfer.digest.equals(job.digest)
                || transfer.byteCount != job.byteCount
                || (active != null && active.digest.equals(job.digest))
                || !hasText(job.originAttemptId)
                || originManifestHash == null
                || !transfer.attemptId.equals(job.originAttemptId)
                || !transfer.manifestSha256.equals(
                    hexBytes(originManifestHash))
                || !librarySyncStore.releaseTransferReconciliation(
                    transfer, true).succeeded()) {
                return false;
            }
        }
        if (!librarySyncStore.resetForExplicitDownload(
                job.digest).succeeded()) {
            return false;
        }
        return bookTransferStore.finalizeManagedCleanup(
            job.callbackToken, true).succeeded();
    }

    private boolean retryUncatalogedManagedCleanup(long attemptSequence) {
        OctavoBookTransferStore.CleanupJob job =
            cleanupJob(attemptSequence);
        if (job == null
            || job.purpose
               != OctavoBookTransferStore.CleanupPurpose.UNCATALOGED) {
            return false;
        }
        if (job.phase
            == OctavoBookTransferStore.CleanupPhase.READY_TO_DELETE) {
            if (!bookTransferStore.deleteManagedForCleanup(
                    job.callbackToken,
                    libraryStore.documentDirectoryForTesting()).succeeded()) {
                return false;
            }
            job = cleanupJob(attemptSequence);
            if (job == null) {
                return false;
            }
        }
        return job.phase
                == OctavoBookTransferStore.CleanupPhase
                    .AWAITING_SYNC_SUPPRESSION
            && bookTransferStore.finalizeManagedCleanup(
                job.callbackToken, true).succeeded();
    }

    private boolean retryManagedCleanup(long attemptSequence) {
        OctavoBookTransferStore.CleanupJob job =
            cleanupJob(attemptSequence);
        if (job == null
            || job.purpose
               != OctavoBookTransferStore.CleanupPurpose.LOCAL_REMOVE) {
            return false;
        }
        OctavoLibraryStore.Book local = libraryStore.findBook(job.digest);
        if (job.phase
            == OctavoBookTransferStore.CleanupPhase.AWAITING_CATALOG_UNLINK) {
            if (local != null
                && (!local.imported || local.byteCount != job.byteCount)) {
                return false;
            }
            if (local != null && !libraryStore.removeBookRecordOnly(job.digest)) {
                return false;
            }
            OctavoBookTransferStore.MutationResult unlinked =
                bookTransferStore.markCleanupCatalogUnlinked(
                    job.callbackToken);
            if (!unlinked.succeeded()) {
                return false;
            }
            job = cleanupJob(attemptSequence);
            if (job == null) {
                return false;
            }
        } else if (local != null) {
            return false;
        }

        if (job.phase == OctavoBookTransferStore.CleanupPhase.READY_TO_DELETE) {
            OctavoBookTransferStore.MutationResult deleted =
                bookTransferStore.deleteManagedForCleanup(
                    job.callbackToken,
                    libraryStore.documentDirectoryForTesting());
            if (!deleted.succeeded()) {
                return false;
            }
            job = cleanupJob(attemptSequence);
            if (job == null) {
                return false;
            }
        }
        if (job.phase
            != OctavoBookTransferStore.CleanupPhase
                .AWAITING_SYNC_SUPPRESSION) {
            return false;
        }
        boolean suppression = finalizeLocalRemoval(
            new OctavoLibraryPortable.Descriptor(
                job.digest, job.byteCount));
        if (!suppression) {
            // Keep the exact O1BQ cleanup intent until the private
            // LOCAL_REMOVED suppression is durable and retryable.
            return false;
        }
        return bookTransferStore.finalizeManagedCleanup(
            job.callbackToken, true).succeeded();
    }

    private OctavoBookTransferStore.CleanupJob cleanupJob(
        long attemptSequence) {
        for (OctavoBookTransferStore.CleanupJob job
                 : bookTransferStore.cleanupJobs()) {
            if (job.attemptSequence == attemptSequence) {
                return job;
            }
        }
        return null;
    }

    private OctavoBookTransferStore.CleanupJob cleanupJob(
        String digest,
        long byteCount) {
        for (OctavoBookTransferStore.CleanupJob job
                 : bookTransferStore.cleanupJobs()) {
            if (job.digest.equals(digest)
                && job.byteCount == byteCount) {
                return job;
            }
        }
        return null;
    }

    private boolean localRemovalBoundaryComplete(long attemptSequence) {
        OctavoBookTransferStore.CleanupJob job =
            cleanupJob(attemptSequence);
        File managed = job == null
            ? null : libraryStore.managedFile(job.digest);
        return job != null
            && job.phase
               == OctavoBookTransferStore.CleanupPhase
                   .AWAITING_SYNC_SUPPRESSION
            && libraryStore.findBook(job.digest) == null
            && managed != null && !managed.exists();
    }

    private boolean discardUncatalogedWithDurableCleanup(
        OctavoLibraryStore.Book book) {
        if (book != null && libraryStore.isStagedImport(book)) {
            boolean discarded = libraryStore.discardUncataloged(book);
            rejectedStagedImportCleanupBook = discarded ? null : book;
            return discarded;
        }
        if (book != null
            && libraryStore.hasPendingImportAssociation(
                book.key, book.byteCount)) {
            // The Port 6 import journal owns bytes after its first durable
            // publication marker. Preserve them for exact association Retry.
            return false;
        }
        if (book == null || !book.imported
            || libraryStore.findBook(book.key) != null) {
            return false;
        }
        OctavoBookTransferStore.CleanupOutcome staged =
            bookTransferStore.stageUncatalogedManagedCleanup(
                book.key, book.byteCount);
        if (!staged.result.succeeded()) {
            return false;
        }
        OctavoBookTransferStore.CleanupJob job =
            cleanupJob(staged.attemptSequence);
        if (job == null) {
            return false;
        }
        if (!bookTransferStore.deleteManagedForCleanup(
                job.callbackToken,
                libraryStore.documentDirectoryForTesting()).succeeded()) {
            return false;
        }
        job = cleanupJob(staged.attemptSequence);
        return job != null
            && bookTransferStore.finalizeManagedCleanup(
                job.callbackToken, true).succeeded();
    }

    private boolean discardRejectedLibraryOpen(
        OctavoLibraryStore.Book book) {
        if (book == null) {
            return false;
        }
        if (libraryStore.hasPendingImportAssociation(
                book.key, book.byteCount)) {
            return true;
        }
        if (libraryStore.isStagedImport(book)) {
            boolean discarded = libraryStore.discardUncataloged(book);
            rejectedStagedImportCleanupBook = discarded ? null : book;
            return discarded;
        }
        if (libraryStore.findBook(book.key) != null) {
            return true;
        }
        return discardUncatalogedWithDurableCleanup(book);
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
        String progress = book.repairRequired
            ? "Repair required"
            : book.hasPosition
            ? "Resume available"
            : "Not started";
        OctavoLibraryMembershipStore.Receipt membership =
            membershipReceiptForBook(book);
        if (membership != null && membership.recordPresent) {
            if (membership.projection
                == OctavoLibraryMembershipPortable.Projection.WITHDRAWN) {
                progress += " | Synchronized Library: withdrawn";
            } else if (membership.projection
                       == OctavoLibraryMembershipPortable.Projection.MEMBER) {
                progress += " | Synchronized Library: restored";
            } else {
                progress += " | Synchronized Library: conflict";
            }
        }
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
        open.setEnabled(!book.repairRequired);
        open.setOnClickListener(view -> {
            libraryFocusBookKey = book.key;
            libraryFocusAction = LIBRARY_FOCUS_OPEN;
            if (!showReader(book, true)) {
                showLibrary();
                showOpenFailure("Unable to open the library book");
            }
        });
        actions.addView(open, wrapLayout());
        if (book.key.equals(libraryFocusBookKey)
            && libraryFocusAction == LIBRARY_FOCUS_OPEN) {
            libraryRowFocusReturn = open;
        }

        if (book.imported) {
            Button remove = new Button(this);
            remove.setText("Remove from this device");
            remove.setAllCaps(false);
            remove.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            remove.setTextColor(tokens.error);
            remove.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            remove.setContentDescription(
                "Remove " + book.title + " from this device");
            remove.setOnClickListener(view -> {
                libraryFocusBookKey = book.key;
                libraryFocusAction = LIBRARY_FOCUS_REMOVE_LOCAL;
                if (removeImportedBook(book)) {
                    showLibrary();
                } else {
                    showLibrary();
                    showOpenFailure(nonemptyMessage(
                        firstNonemptyMessage(
                            bookTransferStore.lastError(),
                            librarySyncStore.lastError(),
                            libraryStore.lastError()),
                        "Removing this book needs Retry"));
                }
            });
            actions.addView(remove, wrapLayout());
            if (book.key.equals(libraryFocusBookKey)
                && libraryFocusAction == LIBRARY_FOCUS_REMOVE_LOCAL) {
                libraryRowFocusReturn = remove;
            }
        }
        row.addView(actions, matchParentWidthLayout());

        if (membership != null) {
            Button membershipAction = new Button(this);
            int action;
            if (!membership.recordPresent
                || membership.projection
                    == OctavoLibraryMembershipPortable.Projection.MEMBER) {
                action = LIBRARY_MEMBERSHIP_ACTION_WITHDRAW;
                membershipAction.setText(
                    "Withdraw from synchronized Library");
                membershipAction.setContentDescription(
                    "Withdraw " + book.title
                        + " from the synchronized Library");
                membershipAction.setTextColor(tokens.error);
            } else if (membership.projection
                       == OctavoLibraryMembershipPortable.Projection
                           .WITHDRAWN) {
                action = LIBRARY_MEMBERSHIP_ACTION_RESTORE;
                membershipAction.setText(
                    "Restore to synchronized Library");
                membershipAction.setContentDescription(
                    "Restore " + book.title
                        + " to the synchronized Library");
                membershipAction.setTextColor(tokens.chromeText);
            } else {
                action = LIBRARY_MEMBERSHIP_ACTION_RESOLVE_MEMBER;
                membershipAction.setText(
                    "Review synchronized Library conflict");
                membershipAction.setContentDescription(
                    "Review the synchronized Library conflict for "
                        + book.title);
                membershipAction.setTextColor(tokens.chromeText);
            }
            membershipAction.setAllCaps(false);
            membershipAction.setFocusable(true);
            membershipAction.setFocusableInTouchMode(true);
            membershipAction.setMinHeight(
                dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            membershipAction.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            membershipAction.setOnClickListener(view -> {
                libraryFocusBookKey = book.key;
                libraryFocusAction = action
                        == LIBRARY_MEMBERSHIP_ACTION_WITHDRAW
                    ? LIBRARY_FOCUS_WITHDRAW
                    : LIBRARY_FOCUS_RESTORE;
                beginLibraryMembershipAction(membership, action);
            });
            LinearLayout.LayoutParams membershipLayout =
                matchParentWidthLayout();
            membershipLayout.topMargin = dp(8);
            row.addView(membershipAction, membershipLayout);
            if (book.key.equals(libraryFocusBookKey)
                && (libraryFocusAction == LIBRARY_FOCUS_WITHDRAW
                    || libraryFocusAction == LIBRARY_FOCUS_RESTORE)) {
                libraryRowFocusReturn = membershipAction;
            }
        }

        View divider = new View(this);
        divider.setBackgroundColor(tokens.divider);
        row.addView(divider,
                    new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(1))));
        return row;
    }

    private OctavoLibraryMembershipStore.Receipt membershipReceiptForBook(
        OctavoLibraryStore.Book book) {
        if (book == null || !book.imported || book.repairRequired
            || libraryMembershipStoreBlocked()) {
            return null;
        }
        OctavoLibraryPortable.Descriptor catalog =
            librarySyncStore.snapshot().descriptor(book.key);
        if (catalog == null || catalog.byteCount != book.byteCount) {
            return null;
        }
        try {
            return libraryMembershipStore.receipt(
                new OctavoLibraryMembershipPortable.Descriptor(
                    catalog.digest, catalog.byteCount));
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
        closeAppearanceSyncPrompt(false);
        closeReadingPositionPrompt(false);
        closeProgressSyncPrompt(false);
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
        progressSyncReviewPending = false;
        progressSyncReviewInitialized = false;
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
        OctavoProgressSyncStore.Pending durableProgressPending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        progressSyncPending = durableProgressPending;
        progressSyncPendingLoaded = durableProgressPending != null;
        progressSyncRollbackRequested = durableProgressPending != null
            && durableProgressPending.direction
               == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
        ProgressSyncRetryDescriptor retainedProgressRetry =
            progressSyncRetryDescriptor;
        if (durableProgressPending != null) {
            progressSyncRetryDescriptor =
                ProgressSyncRetryDescriptor.pending(
                    progressSyncRollbackRequested
                        ? PROGRESS_RETRY_ROLLBACK
                        : PROGRESS_RETRY_FORWARD,
                    durableProgressPending);
        } else if (retainedProgressRetry != null
                   && (retainedProgressRetry.localStageAction()
                       || retainedProgressRetry.candidateAction())) {
            progressSyncRetryDescriptor = retainedProgressRetry;
        } else {
            progressSyncRetryDescriptor = null;
        }
        progressSyncAwaitingExplicitRetry =
            (progressSyncRetryDescriptor != null
             && progressSyncRetryDescriptor.localStageAction())
            || progressSyncAbandonAfterReload;
        progressSyncCandidate = null;
        progressSyncRetry = null;
        progressReceiptSurface = null;
        latestProgressReceipt = null;
        clearConsumedProgressReceipt();
        progressSyncOriginSpineIndex = -1;
        progressSyncOriginByteOffset = -1;
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
            || appearanceSyncPrompt != null
            || progressSyncPrompt != null) {
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

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String nonemptyMessage(String value,
                                          String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static String firstNonemptyMessage(String... values) {
        if (values != null) {
            for (String value : values) {
                if (hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String shortDigest(String digest) {
        return digest == null || digest.length() < 8
            ? "unknown identity" : digest.substring(0, 8) + "...";
    }

    private static String humanReadableByteCount(long byteCount) {
        if (byteCount < 1024) {
            return String.format(Locale.ROOT, "%d B", byteCount);
        }
        if (byteCount < 1024L * 1024L) {
            return String.format(
                Locale.ROOT, "%.1f KiB", byteCount / 1024.0);
        }
        return String.format(
            Locale.ROOT, "%.1f MiB", byteCount / (1024.0 * 1024.0));
    }

    private static String hexBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        char[] result = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; ++index) {
            int value = bytes[index] & 0xFF;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0F];
        }
        return new String(result);
    }

    private static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return hexBytes(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable", exception);
        }
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
        OctavoLibraryStore.Book book = libraryStore.findBook(key);
        boolean removed = book != null && removeImportedBook(book);
        showLibrary();
        return removed;
    }

    void closeBookForTesting() {
        showLibrary(true);
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

    OctavoLibrarySyncStore librarySyncStoreForTesting() {
        return librarySyncStore;
    }

    OctavoBookTransferStore bookTransferStoreForTesting() {
        return bookTransferStore;
    }

    OctavoLibraryMembershipStore libraryMembershipStoreForTesting() {
        return libraryMembershipStore;
    }

    OctavoLibrarySyncPrompt librarySyncPromptForTesting() {
        return librarySyncPrompt;
    }

    OctavoLibraryMembershipPrompt libraryMembershipPromptForTesting() {
        return libraryMembershipPrompt;
    }

    OctavoLibraryMembershipStore.PortableStageResult
        stagePortableLibraryMembershipForTesting(byte[] bytes) {
        OctavoLibraryMembershipStore.StageOutcome outcome =
            libraryMembershipStore.stagePortableBytes(bytes);
        if (libraryRoot != null) {
            showLibrary();
        }
        return outcome.result;
    }

    boolean beginLibraryMembershipActionForTesting(
        String digest,
        long byteCount,
        int action) {
        OctavoLibraryMembershipStore.Receipt receipt;
        try {
            receipt = libraryMembershipStore.receipt(
                new OctavoLibraryMembershipPortable.Descriptor(
                    digest, byteCount));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (receipt == null) {
            return false;
        }
        beginLibraryMembershipAction(receipt, action);
        return libraryMembershipPrompt != null;
    }

    boolean beginLibraryMembershipWithdrawForTesting(
        String digest,
        long byteCount) {
        return beginLibraryMembershipActionForTesting(
            digest, byteCount, LIBRARY_MEMBERSHIP_ACTION_WITHDRAW);
    }

    boolean libraryMembershipAttentionDeferredForTesting() {
        return libraryMembershipAttentionDeferred;
    }

    OctavoLibrarySyncStore.PortableStageResult
        stagePortableLibraryCatalogForTesting(byte[] bytes) {
        OctavoLibrarySyncStore.PortableStageResult result =
            librarySyncStore.stagePortableBytes(bytes);
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    String stagedPortableLibraryDigestForTesting() {
        OctavoLibrarySyncStore.StagedPortable staged =
            librarySyncStore.stagedPortable();
        return staged == null ? null : staged.sha256;
    }

    OctavoLibrarySyncStore.PortableMergeResult
        approvePortableLibraryCatalogForTesting(String exactDigestEcho) {
        OctavoLibrarySyncStore.PortableMergeResult result =
            librarySyncStore.approveStagedPortable(exactDigestEcho);
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean setAvailableBookManifestForTesting(byte[] bytes) {
        OctavoBookManifest.DecodeResult decoded =
            OctavoBookManifest.decode(bytes);
        if (decoded.status != OctavoBookManifest.DecodeStatus.READY) {
            return false;
        }
        OctavoBookManifest manifest = decoded.manifest();
        libraryCatalogManifestBytes = bytes.clone();
        libraryCatalogManifestDigest = manifest.digest;
        if (libraryRoot != null) {
            showLibrary();
        }
        return true;
    }

    OctavoLibrarySyncStore.Candidate libraryCatalogOfferForTesting() {
        return libraryCatalogOffer;
    }

    String libraryCatalogOfferManifestSha256ForTesting() {
        return libraryCatalogOfferManifestSha256;
    }

    boolean downloadLibraryCatalogOfferForTesting() {
        return downloadLibraryCatalogOffer();
    }

    boolean acceptNextLibraryDownloadChunkForTesting(
        int index,
        InputStream callerOwnedChunk) {
        OctavoBookTransferStore.ActiveJob job =
            bookTransferStore.activeJob();
        if (libraryTransferExplicitRetryRequired || job == null
            || !ensureLibraryTransferReconciliation(job, null)) {
            return false;
        }
        OctavoBookTransferStore.MutationResult accepted =
            bookTransferStore.acceptNextDownloadChunk(
                job.callbackToken, index, callerOwnedChunk);
        if (!accepted.succeeded()) {
            if (libraryRoot != null) {
                showLibrary();
            }
            return false;
        }
        OctavoBookTransferStore.ActiveJob updated =
            bookTransferStore.activeJob();
        boolean result = updated != null
            && updated.completedPrefix == updated.chunkCount
            ? resumeLibraryTransfer()
                || transferFinalizationIdentityInstalled()
            : true;
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean retryLibraryTransferForTesting() {
        boolean result = explicitlyRetryLibraryTransfer();
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean libraryTransferExplicitRetryRequiredForTesting() {
        return libraryTransferExplicitRetryRequired;
    }

    boolean cancelLibraryTransferForTesting() {
        boolean result = cancelLibraryTransfer();
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean retryManagedCleanupForTesting(long attemptSequence) {
        boolean result = reloadTransferStoresForExplicitRetry()
            && retryCleanupByPurpose(attemptSequence);
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean retryPendingImportAssociationForTesting() {
        OctavoLibraryStore.Book pending =
            currentPendingImportAssociationBook();
        boolean result = pending != null
            && retryPendingImportAssociation(pending);
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean discardPendingImportAssociationForTesting() {
        OctavoLibraryStore.Book pending =
            currentPendingImportAssociationBook();
        boolean result = pending != null
            && discardPendingImportAssociation(pending);
        if (libraryRoot != null) {
            showLibrary();
        }
        return result;
    }

    boolean verifyNextLibraryDiscoveryForTesting() {
        OctavoLibrarySyncStore.LocalReconciliation pending =
            librarySyncStore.localReconciliation();
        if (pending != null) {
            boolean result = reloadTransferStoresForExplicitRetry()
                && retryLocalLibraryReconciliation(pending);
            if (libraryRoot != null) {
                showLibrary();
            }
            return result;
        }
        for (OctavoLibraryStore.Book book : libraryStore.books()) {
            OctavoLibrarySyncStore.Decision decision =
                librarySyncStore.decision(book.key);
            if (!book.imported || book.repairRequired
                || (decision
                        == OctavoLibrarySyncStore.Decision.DOWNLOADED
                    && book.identityVerified)) {
                continue;
            }
            if (!book.identityVerified) {
                pendingLibraryIdentityBook = book;
                pendingLibraryIdentityMode =
                    LIBRARY_IDENTITY_MODE_LOCAL_PUBLICATION;
                pendingLibraryIdentityRecordOpened = false;
                pendingLibraryIdentityLocalReconciliation = null;
                libraryDiscoveryStatus = "Verification in progress";
                if (libraryRoot != null) {
                    showLibrary();
                }
                return false;
            }
            boolean result =
                completeLocalPublicationAfterIdentity(book, null);
            if (libraryRoot != null) {
                showLibrary();
            }
            return result;
        }
        libraryDiscoveryStatus = null;
        return true;
    }

    String libraryDiscoveryStatusForTesting() {
        return libraryDiscoveryStatus;
    }

    String libraryImportAssociationStatusForTesting() {
        return libraryImportAssociationStatus;
    }

    String libraryCatalogStatusForTesting() {
        return libraryCatalogStatus == null
            ? null : libraryCatalogStatus.getText().toString();
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

    boolean simulateRemoteProgressForTesting(
        String deviceId,
        long sequence,
        OctavoProgressDisplay display) {
        try {
            return mergeSimulatedRemoteProgressForTesting(
                OctavoProgressPortable.simulatedRemoteBytes(
                    deviceId, sequence, display));
        } catch (IOException | RuntimeException exception) {
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The simulated remote progress display is invalid.",
                this::reloadProgressSyncState);
            return false;
        }
    }

    boolean mergeSimulatedRemoteProgressForTesting(byte[] bytes) {
        if (progressSyncStore == null) {
            return false;
        }
        if (progressSyncO8pgFutureBlocked
            || progressStore == null
            || progressStore.loadStatus()
               == OctavoProgressStore.LoadStatus.FUTURE) {
            progressSyncO8pgFutureBlocked = true;
            showProgressSyncFailure(
                "Progress display update needs attention",
                "The saved progress display was written by a newer "
                    + "version. Its bytes were preserved and "
                    + "synchronization is blocked.",
                this::reloadProgressSyncState);
            return false;
        }
        OctavoProgressSyncStore.PortableMergeResult result =
            progressSyncStore.mergePortableBytes(bytes);
        if (!result.succeeded()) {
            Runnable retry = this::reloadProgressSyncState;
            if (bytes != null
                && bytes.length
                   <= OctavoProgressPortable.maximumFutureBytes()) {
                byte[] retryBytes = bytes.clone();
                retry = () -> mergeSimulatedRemoteProgressForTesting(
                    retryBytes);
            }
            showProgressSyncFailure(
                "Progress display update needs attention",
                progressSyncStore.lastError(),
                retry);
            return false;
        }
        if (result
            == OctavoProgressSyncStore.PortableMergeResult
                .FUTURE_RETAINED) {
            showOpenFailure(progressSyncStore.lastError());
            return true;
        }
        if (result
            == OctavoProgressSyncStore.PortableMergeResult.MERGED) {
            boolean promptWasVisible = progressSyncPrompt != null;
            if (progressSyncStore.pending() == null) {
                closeProgressSyncPrompt(false);
                OctavoSurfaceView.ProgressPresentationReceipt receipt =
                    currentProgressReceipt();
                if (receipt != null
                    && progressStore.hasCanonicalCurrentRecord(
                        receipt.choice)) {
                    finishProgressConvergence(receipt);
                } else {
                    considerProgressSyncCandidate();
                }
                if (promptWasVisible
                    && appearanceSyncPrompt == null
                    && readingPositionPrompt == null
                    && progressSyncPrompt == null) {
                    restoreProgressSyncFocusAfterClose();
                }
            }
        }
        return true;
    }

    OctavoProgressSyncPrompt progressSyncPromptForTesting() {
        return progressSyncPrompt;
    }

    OctavoProgressSyncStore progressSyncStoreForTesting() {
        return progressSyncStore;
    }

    OctavoProgressSyncStore.Candidate
        pendingProgressSyncCandidateForTesting() {
        return progressSyncCandidate;
    }

    OctavoProgressSyncStore.Pending
        pendingProgressTransactionForTesting() {
        return progressSyncStore == null ? null
            : progressSyncStore.pending();
    }

    boolean progressSyncAwaitingExplicitRetryForTesting() {
        return progressSyncAwaitingExplicitRetry;
    }

    boolean progressSyncRollbackRequestedForTesting() {
        OctavoProgressSyncStore.Pending pending =
            progressSyncStore == null ? null
                : progressSyncStore.pending();
        return pending != null
            && pending.direction
               == OctavoProgressSyncStore.PendingDirection.ROLLBACK;
    }

    boolean progressSyncReviewPendingForTesting() {
        return progressSyncReviewPending;
    }

    boolean progressSyncReviewInitializedForTesting() {
        return progressSyncReviewInitialized;
    }

    boolean progressSyncO8pgFutureBlockedForTesting() {
        return progressSyncO8pgFutureBlocked;
    }

    int progressSyncPromptMotionDurationForTesting() {
        return progressSyncPromptMotionDuration();
    }

    void processProgressPresentationReceiptForTesting() {
        processProgressPresentationReceipt();
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
        processProgressPresentationReceipt();
    }

    void queuePresentedProgressPersistenceForTesting() {
        processProgressPresentationReceipt();
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
