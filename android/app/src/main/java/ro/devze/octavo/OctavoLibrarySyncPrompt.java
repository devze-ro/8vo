package ro.devze.octavo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Product-owned Library catalog and managed-transfer prompt content.
 *
 * The host owns the exact staged catalog, candidate, manifest, queue attempt,
 * Back decision, sibling accessibility exclusion, focus restoration, and
 * lifecycle. This view deliberately owns none of those identities; a callback
 * is valid only while the host's exact bound state is still current.
 */
final class OctavoLibrarySyncPrompt extends LinearLayout {
    interface Listener {
        void onApproveCatalog();
        void onDeferCatalog();
        void onDownload();
        void onDismissOffer();
        void onIgnoreOffer();
        void onRetry();
        void onCancel();
    }

    enum Mode {
        CATALOG_APPROVAL,
        OFFER,
        WORKING,
        RETRYABLE_FAILURE
    }

    private static final int MAX_HEADING_CODE_POINTS = 160;
    private static final int MAX_STATUS_CODE_POINTS = 320;
    private static final String DEFAULT_FAILURE_HEADING =
        "Library transfer needs attention";
    private static final String DEFAULT_FAILURE_MESSAGE =
        "The Library transfer could not be completed. Retry is safe.";

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView heading;
    private final TextView detail;
    private final TextView status;
    private final Button approve;
    private final Button download;
    private final Button notNow;
    private final Button ignore;
    private final Button retry;
    private final Button cancel;

    private OctavoAppearance appearance;
    private Mode mode;
    private boolean statusIsError;
    private boolean failureAllowsCancel;

    OctavoLibrarySyncPrompt(Context context,
                            OctavoAppearance initialAppearance,
                            Listener listener) {
        super(context);
        if (initialAppearance == null) {
            throw new IllegalArgumentException(
                "Library-sync prompt appearance is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException(
                "Library-sync prompt listener is required");
        }
        this.listener = listener;
        appearance = initialAppearance;

        setOrientation(VERTICAL);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setContentDescription("Library synchronization prompt");
        addView(scroll, matchWrap());

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setFocusable(false);
        scroll.addView(
            content,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        heading = text("", 20, Typeface.BOLD);
        heading.setId(View.generateViewId());
        heading.setFocusable(true);
        heading.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            heading.setAccessibilityHeading(true);
        }
        content.addView(heading, matchWrap());

        detail = text("", 17, Typeface.NORMAL);
        detail.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams detailLayout = matchWrap();
        detailLayout.topMargin = dp(12);
        content.addView(detail, detailLayout);

        status = text("", 15, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_YES);
        status.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (statusIsError) {
                    info.setError(status.getText());
                }
            }
        });
        LinearLayout.LayoutParams statusLayout = matchWrap();
        statusLayout.topMargin = dp(12);
        content.addView(status, statusLayout);

        approve = button(
            "Approve",
            "Approve the reviewed Library discovery catalog");
        approve.setOnClickListener(view -> {
            if (!approve.isEnabled()) {
                return;
            }
            showWorking(
                "Approving Library discovery",
                "Saving the reviewed catalog.",
                false);
            listener.onApproveCatalog();
        });
        addAction(approve);

        download = button(
            "Download",
            "Download the offered EPUB to this device");
        download.setOnClickListener(view -> {
            if (!download.isEnabled()) {
                return;
            }
            showWorking(
                "Preparing EPUB download",
                "Saving the download request before transferring bytes.",
                false);
            listener.onDownload();
        });
        addAction(download);

        notNow = button("Not now", "Review this Library item later");
        notNow.setOnClickListener(view -> {
            if (!notNow.isEnabled()) {
                return;
            }
            boolean catalog = mode == Mode.CATALOG_APPROVAL;
            showWorking(
                catalog ? "Deferring Library review" : "Deferring EPUB offer",
                "Saving your choice.",
                false);
            if (catalog) {
                listener.onDeferCatalog();
            } else {
                listener.onDismissOffer();
            }
        });
        addAction(notNow);

        ignore = button(
            "Don't show again",
            "Do not offer this exact EPUB again");
        ignore.setOnClickListener(view -> {
            if (!ignore.isEnabled()) {
                return;
            }
            showWorking(
                "Hiding this EPUB offer",
                "Saving your choice for this exact EPUB.",
                false);
            listener.onIgnoreOffer();
        });
        addAction(ignore);

        retry = button("Retry", "Retry the Library transfer step");
        retry.setOnClickListener(view -> {
            if (!retry.isEnabled()) {
                return;
            }
            boolean allowCancel = failureAllowsCancel;
            showWorking(
                "Retrying Library transfer",
                "Retrying the saved transfer step.",
                allowCancel);
            listener.onRetry();
        });
        addAction(retry);

        cancel = button("Cancel", "Cancel this EPUB transfer");
        cancel.setOnClickListener(view -> {
            if (!cancel.isEnabled()) {
                return;
            }
            showWorking(
                "Cancelling EPUB transfer",
                "Saving cancellation and cleaning up partial bytes.",
                false);
            listener.onCancel();
        });
        addAction(cancel);

        applyAppearance(appearance);
        hideAllActions();
        detail.setVisibility(GONE);
        showRetryableFailure(
            DEFAULT_FAILURE_HEADING,
            DEFAULT_FAILURE_MESSAGE,
            false);
    }

    OctavoLibrarySyncPrompt(Context context,
                            OctavoAppearance initialAppearance,
                            int entryCount,
                            String stagedSha256,
                            Listener listener) {
        this(context, initialAppearance, listener);
        showCatalogApproval(entryCount, stagedSha256);
    }

    OctavoLibrarySyncPrompt(Context context,
                            OctavoAppearance initialAppearance,
                            String digest,
                            long byteCount,
                            Listener listener) {
        this(context, initialAppearance, listener);
        showOffer(digest, byteCount);
    }

    void showCatalogApproval(int entryCount, String stagedSha256) {
        if (entryCount < 0 || entryCount > 63) {
            throw new IllegalArgumentException(
                "Library discovery count is out of bounds");
        }
        String digest = requireDigest(stagedSha256);
        mode = Mode.CATALOG_APPROVAL;
        setPaneTitle("Library discovery confirmation");
        heading.setText("Review Library discovery");
        detail.setText(entryCount == 1
            ? "1 EPUB identity is ready for review - "
                + shortDigest(digest)
            : String.format(
                Locale.ROOT,
                "%d EPUB identities are ready for review - %s",
                entryCount,
                shortDigest(digest)));
        detail.setContentDescription(entryCount == 1
            ? "1 EPUB identity is ready for review. Catalog fingerprint "
                + shortDigest(digest) + "."
            : String.format(
                Locale.ROOT,
                "%d EPUB identities are ready for review. Catalog fingerprint %s.",
                entryCount,
                shortDigest(digest)));
        detail.setVisibility(VISIBLE);
        setStatus(
            "Approve only the catalog you reviewed, or choose Not now.",
            false);
        showOnly(approve, notNow);
    }

    void showOffer(String digest, long byteCount) {
        String exactDigest = requireDigest(digest);
        if (byteCount <= 0 || byteCount > 536870912L) {
            throw new IllegalArgumentException(
                "Offered EPUB byte count is out of bounds");
        }
        mode = Mode.OFFER;
        setPaneTitle("Book download confirmation");
        heading.setText("Book available from another device");
        String size = humanReadableByteCount(byteCount);
        detail.setText(
            "EPUB - " + size + " - " + shortDigest(exactDigest));
        detail.setContentDescription(
            "EPUB. " + size + ". Identifier "
                + shortDigest(exactDigest) + ".");
        detail.setVisibility(VISIBLE);
        setStatus(
            "Download this EPUB, offer it later, or hide this exact EPUB.",
            false);
        showOnly(download, notNow, ignore);
    }

    void showTransferProgress(String digest,
                              long byteCount,
                              long completedBytes,
                              int completedChunks,
                              int chunkCount) {
        String exactDigest = requireDigest(digest);
        if (byteCount <= 0 || byteCount > 536870912L
            || completedBytes < 0 || completedBytes > byteCount
            || chunkCount <= 0 || chunkCount > 128
            || completedChunks < 0 || completedChunks > chunkCount) {
            throw new IllegalArgumentException(
                "Transfer progress is out of bounds");
        }
        mode = Mode.WORKING;
        failureAllowsCancel = false;
        setPaneTitle("EPUB download status");
        heading.setText("Downloading EPUB");
        detail.setText(
            "EPUB - " + humanReadableByteCount(byteCount)
                + " - " + shortDigest(exactDigest));
        detail.setContentDescription(
            "EPUB. " + humanReadableByteCount(byteCount)
                + ". Identifier " + shortDigest(exactDigest) + ".");
        detail.setVisibility(VISIBLE);
        setStatus(
            "Downloaded " + humanReadableByteCount(completedBytes)
                + " of " + humanReadableByteCount(byteCount)
                + " - chunk " + completedChunks + " of " + chunkCount,
            false);
        showOnly(cancel);
    }

    void showWorking(String workingHeading,
                     String message,
                     boolean allowCancel) {
        mode = Mode.WORKING;
        failureAllowsCancel = false;
        setPaneTitle("Library transfer status");
        heading.setText(normalized(
            workingHeading,
            MAX_HEADING_CODE_POINTS,
            "Updating Library transfer"));
        setStatus(normalized(
            message,
            MAX_STATUS_CODE_POINTS,
            "Updating the saved Library transfer."), false);
        if (allowCancel) {
            showOnly(cancel);
        } else {
            hideAllActions();
        }
    }

    void showRetryableFailure(String failureHeading,
                              String message,
                              boolean allowCancel) {
        mode = Mode.RETRYABLE_FAILURE;
        failureAllowsCancel = allowCancel;
        setPaneTitle("Library transfer needs attention");
        heading.setText(normalized(
            failureHeading,
            MAX_HEADING_CODE_POINTS,
            DEFAULT_FAILURE_HEADING));
        setStatus(normalized(
            message,
            MAX_STATUS_CODE_POINTS,
            DEFAULT_FAILURE_MESSAGE), true);
        if (allowCancel) {
            showOnly(retry, cancel);
        } else {
            showOnly(retry);
        }
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException(
                "Library-sync prompt appearance is required");
        }
        appearance = updated;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        int padding = dp(OctavoDesignTokens.SPACE_LG_DP);
        content.setPadding(padding, padding, padding, padding);
        setBackgroundColor(tokens.librarySurface);
        scroll.setBackgroundColor(tokens.librarySurface);
        content.setBackgroundColor(tokens.librarySurface);
        heading.setTextColor(tokens.libraryText);
        detail.setTextColor(tokens.textSecondary);
        status.setTextColor(statusIsError
            ? tokens.error : tokens.textSecondary);
        configureButton(approve, true, tokens);
        configureButton(download, true, tokens);
        configureButton(notNow, false, tokens);
        configureButton(ignore, false, tokens);
        ignore.setTextColor(tokens.error);
        configureButton(retry, true, tokens);
        configureButton(cancel, false, tokens);
        cancel.setTextColor(tokens.error);
        invalidate();
    }

    int overlayColor() {
        return OctavoDesignTokens.forAppearance(appearance).overlay;
    }

    View preferredInitialFocus() {
        if (retry.getVisibility() == VISIBLE && retry.isEnabled()) {
            return retry;
        }
        if (approve.getVisibility() == VISIBLE && approve.isEnabled()) {
            return approve;
        }
        if (download.getVisibility() == VISIBLE && download.isEnabled()) {
            return download;
        }
        if (cancel.getVisibility() == VISIBLE && cancel.isEnabled()) {
            return cancel;
        }
        return heading;
    }

    boolean suppressHostMotion() {
        return appearance.reducedMotion();
    }

    /** Returns true only when Back durably maps to the visible Not-now path. */
    boolean handleBack() {
        if (mode != Mode.CATALOG_APPROVAL && mode != Mode.OFFER) {
            return false;
        }
        return notNow.performClick();
    }

    private void setStatus(String message, boolean error) {
        statusIsError = error;
        status.setText(message);
        status.setContentDescription(message);
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        status.setTextColor(error ? tokens.error : tokens.textSecondary);
    }

    private void setPaneTitle(String value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setAccessibilityPaneTitle(value);
        }
    }

    private void showOnly(Button... visible) {
        hideAllActions();
        for (Button action : visible) {
            action.setVisibility(VISIBLE);
            action.setEnabled(true);
        }
    }

    private void hideAllActions() {
        Button[] actions = {
            approve, download, notNow, ignore, retry, cancel
        };
        for (Button action : actions) {
            action.setVisibility(GONE);
            action.setEnabled(false);
        }
    }

    private void addAction(Button action) {
        LinearLayout.LayoutParams layout = matchWrap();
        layout.topMargin = dp(8);
        content.addView(action, layout);
    }

    private TextView text(String value, float sizeSp, int style) {
        TextView result = new TextView(getContext());
        result.setText(value);
        result.setTextSize(sizeSp);
        result.setTypeface(Typeface.DEFAULT, style);
        result.setLineSpacing(0.0f, 1.12f);
        return result;
    }

    private Button button(String value, String description) {
        Button result = new Button(getContext());
        result.setId(View.generateViewId());
        result.setText(value);
        result.setContentDescription(description);
        result.setAllCaps(false);
        result.setFocusable(true);
        result.setFocusableInTouchMode(true);
        result.setGravity(Gravity.CENTER);
        return result;
    }

    private void configureButton(Button button,
                                 boolean action,
                                 OctavoDesignTokens tokens) {
        int minimum = dp(OctavoDesignTokens.TOUCH_TARGET_DP);
        button.setMinWidth(minimum);
        button.setMinHeight(minimum);
        button.setTextColor(action ? tokens.onAccent : tokens.chromeText);
        button.setBackgroundTintList(ColorStateList.valueOf(
            action ? tokens.accent : tokens.buttonSurface));
    }

    private int dp(int value) {
        return Math.max(
            value,
            Math.round(value
                       * getResources().getDisplayMetrics().density));
    }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException(
                "A lowercase SHA-256 digest is required");
        }
        for (int index = 0; index < value.length(); ++index) {
            char digit = value.charAt(index);
            if (!((digit >= '0' && digit <= '9')
                  || (digit >= 'a' && digit <= 'f'))) {
                throw new IllegalArgumentException(
                    "A lowercase SHA-256 digest is required");
            }
        }
        return value;
    }

    private static String shortDigest(String digest) {
        return digest.substring(0, 12);
    }

    private static String humanReadableByteCount(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0);
        }
        return String.format(
            Locale.ROOT,
            "%.1f MiB",
            value / (1024.0 * 1024.0));
    }

    private static String normalized(String value,
                                     int maximumCodePoints,
                                     String fallback) {
        if (maximumCodePoints <= 0 || fallback == null) {
            throw new IllegalArgumentException();
        }
        if (value == null) {
            return fallback;
        }
        StringBuilder result = new StringBuilder(
            Math.min(value.length(), maximumCodePoints));
        boolean pendingSpace = false;
        int examinedCodePoints = 0;
        int writtenCodePoints = 0;
        int maximumExaminedCodePoints = maximumCodePoints * 8;
        for (int index = 0;
             index < value.length()
                 && examinedCodePoints < maximumExaminedCodePoints
                 && writtenCodePoints < maximumCodePoints;) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            ++examinedCodePoints;
            if (Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (pendingSpace && writtenCodePoints + 1 < maximumCodePoints) {
                result.append(' ');
                ++writtenCodePoints;
            }
            pendingSpace = false;
            result.appendCodePoint(codePoint);
            ++writtenCodePoints;
        }
        return result.length() == 0 ? fallback : result.toString();
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    Mode modeForTesting() { return mode; }
    TextView headingForTesting() { return heading; }
    TextView detailForTesting() { return detail; }
    TextView statusForTesting() { return status; }
    Button approveForTesting() { return approve; }
    Button downloadForTesting() { return download; }
    Button notNowForTesting() { return notNow; }
    Button ignoreForTesting() { return ignore; }
    Button retryForTesting() { return retry; }
    Button cancelForTesting() { return cancel; }
    ScrollView scrollForTesting() { return scroll; }
    boolean statusIsErrorForTesting() { return statusIsError; }
}
