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

/** Library-only confirmation and retained-attention surface for O1LM/O1MS. */
final class OctavoLibraryMembershipPrompt extends LinearLayout {
    enum Mode {
        WITHDRAW_CONFIRMATION,
        RESTORE_CONFIRMATION,
        CONFLICT,
        STAGED_APPROVAL,
        RETAINED_ATTENTION,
        WORKING
    }

    interface Listener {
        void onWithdraw();
        void onRestore();
        void onResolveMember();
        void onResolveWithdrawn();
        void onApproveStaged();
        void onDiscardStaged();
        void onRetry();
    }

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView heading;
    private final TextView detail;
    private final TextView status;
    private final Button primary;
    private final Button secondary;
    private final Button discard;
    private final Button retry;
    private OctavoAppearance appearance;
    private Mode mode;
    private boolean retainedAttention;
    private boolean statusIsError;

    OctavoLibraryMembershipPrompt(Context context,
                                   OctavoAppearance initialAppearance,
                                   Listener listener) {
        super(context);
        if (initialAppearance == null || listener == null) {
            throw new IllegalArgumentException(
                "Membership prompt dependencies are required");
        }
        this.listener = listener;
        appearance = initialAppearance;
        setOrientation(VERTICAL);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setContentDescription("Synchronized Library membership prompt");
        addView(scroll, matchWrap());

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
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
        LinearLayout.LayoutParams detailLayout = matchWrap();
        detailLayout.topMargin = dp(12);
        content.addView(detail, detailLayout);

        status = text("", 15, Typeface.NORMAL);
        status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
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

        primary = button("Confirm", "Confirm the membership action");
        primary.setOnClickListener(view -> {
            if (!primary.isEnabled()) {
                return;
            }
            Mode selected = mode;
            showWorking("Updating synchronized Library membership");
            if (selected == Mode.WITHDRAW_CONFIRMATION) {
                listener.onWithdraw();
            } else if (selected == Mode.RESTORE_CONFIRMATION) {
                listener.onRestore();
            } else if (selected == Mode.STAGED_APPROVAL) {
                listener.onApproveStaged();
            }
        });
        addAction(primary);

        secondary = button("Keep in Library", "Resolve membership as included");
        secondary.setOnClickListener(view -> {
            if (!secondary.isEnabled()) {
                return;
            }
            String selected = secondary.getText().toString();
            showWorking("Resolving synchronized Library membership");
            if ("Keep in Library".equals(selected)) {
                listener.onResolveMember();
            } else {
                listener.onResolveWithdrawn();
            }
        });
        addAction(secondary);

        discard = button(
            "Discard reviewed input",
            "Discard this exact reviewed membership input");
        discard.setOnClickListener(view -> {
            if (!discard.isEnabled()) {
                return;
            }
            Mode selected = mode;
            showWorking(selected == Mode.CONFLICT
                ? "Resolving synchronized Library membership"
                : "Discarding reviewed membership input");
            if (selected == Mode.CONFLICT) {
                listener.onResolveWithdrawn();
            } else {
                listener.onDiscardStaged();
            }
        });
        addAction(discard);

        retry = button("Retry", "Reload synchronized Library membership state");
        retry.setOnClickListener(view -> {
            if (!retry.isEnabled()) {
                return;
            }
            showWorking("Reloading synchronized Library membership");
            listener.onRetry();
        });
        addAction(retry);

        hideAllActions();
        applyAppearance(initialAppearance);
    }

    void showWithdraw(String digest, long byteCount) {
        mode = Mode.WITHDRAW_CONFIRMATION;
        retainedAttention = false;
        setPaneTitle("Withdraw from synchronized Library");
        heading.setText("Withdraw from synchronized Library?");
        setDescriptor(digest, byteCount);
        setStatus(
            "This changes synchronized membership only. The local EPUB, "
                + "reading position, and annotations stay on this device.",
            false);
        primary.setText("Withdraw from synchronized Library");
        primary.setContentDescription(
            "Confirm withdrawal from the synchronized Library");
        showOnly(primary);
    }

    void showRestore(String digest, long byteCount) {
        mode = Mode.RESTORE_CONFIRMATION;
        retainedAttention = false;
        setPaneTitle("Restore to synchronized Library");
        heading.setText("Restore to synchronized Library?");
        setDescriptor(digest, byteCount);
        setStatus(
            "This restores the exact EPUB identity to synchronized membership. "
                + "It does not download or open the book.",
            false);
        primary.setText("Restore to synchronized Library");
        primary.setContentDescription(
            "Confirm restoration to the synchronized Library");
        showOnly(primary);
    }

    void showConflict(String digest, long byteCount) {
        mode = Mode.CONFLICT;
        retainedAttention = true;
        setPaneTitle("Synchronized Library conflict");
        heading.setText("Review synchronized Library conflict");
        setDescriptor(digest, byteCount);
        setStatus(
            "Concurrent Withdraw and Restore actions are retained. Choose an "
                + "explicit resolution that observes the complete history.",
            true);
        secondary.setText("Keep in Library");
        secondary.setContentDescription(
            "Resolve the conflict by keeping this EPUB in the synchronized Library");
        discard.setText("Keep withdrawn");
        discard.setContentDescription(
            "Resolve the conflict by keeping this EPUB withdrawn");
        showOnly(secondary, discard);
    }

    void showStagedApproval(int recordCount,
                            String stagedSha256,
                            long reviewEpoch) {
        if (recordCount < 0 || recordCount > 63 || reviewEpoch <= 0) {
            throw new IllegalArgumentException(
                "Staged membership review is out of bounds");
        }
        String digest = requireDigest(stagedSha256);
        mode = Mode.STAGED_APPROVAL;
        retainedAttention = true;
        setPaneTitle("Synchronized Library membership review");
        heading.setText("Review synchronized Library membership");
        detail.setText(String.format(
            Locale.ROOT,
            "%d membership histories - review %d - %s",
            recordCount, reviewEpoch, shortDigest(digest)));
        detail.setContentDescription(String.format(
            Locale.ROOT,
            "%d membership histories. Review epoch %d. Fingerprint %s.",
            recordCount, reviewEpoch, shortDigest(digest)));
        setStatus(
            "Approve only this exact reviewed input, or discard it without "
                + "changing current membership.",
            false);
        primary.setText("Approve membership history");
        primary.setContentDescription(
            "Approve this exact synchronized Library membership history");
        discard.setText("Discard reviewed input");
        discard.setContentDescription(
            "Discard this exact reviewed membership input");
        showOnly(primary, discard);
    }

    void showRetainedAttention(String attentionHeading,
                               String message,
                               boolean stagedDiscardAvailable) {
        mode = Mode.RETAINED_ATTENTION;
        retainedAttention = true;
        setPaneTitle("Synchronized Library membership needs attention");
        heading.setText(normalized(
            attentionHeading, 96,
            "Synchronized Library membership needs attention"));
        detail.setVisibility(GONE);
        setStatus(normalized(
            message, 320,
            "The exact membership state is retained and needs review."), true);
        discard.setText("Discard reviewed input");
        discard.setContentDescription(
            "Discard this exact reviewed membership input");
        if (stagedDiscardAvailable) {
            showOnly(retry, discard);
        } else {
            showOnly(retry);
        }
    }

    void showWorking(String message) {
        mode = Mode.WORKING;
        retainedAttention = true;
        setPaneTitle("Updating synchronized Library membership");
        heading.setText("Updating synchronized Library membership");
        detail.setVisibility(GONE);
        setStatus(normalized(
            message, 320,
            "Updating synchronized Library membership."), false);
        hideAllActions();
    }

    boolean retainedAttention() {
        return retainedAttention;
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException("Prompt appearance is required");
        }
        appearance = updated;
        OctavoDesignTokens tokens = OctavoDesignTokens.forAppearance(updated);
        int padding = dp(OctavoDesignTokens.SPACE_LG_DP);
        content.setPadding(padding, padding, padding, padding);
        setBackgroundColor(tokens.librarySurface);
        scroll.setBackgroundColor(tokens.librarySurface);
        content.setBackgroundColor(tokens.librarySurface);
        heading.setTextColor(tokens.libraryText);
        detail.setTextColor(tokens.textSecondary);
        status.setTextColor(statusIsError ? tokens.error : tokens.textSecondary);
        configureButton(primary, true, tokens);
        configureButton(secondary, true, tokens);
        configureButton(discard, false, tokens);
        discard.setTextColor(tokens.error);
        configureButton(retry, true, tokens);
        invalidate();
    }

    int overlayColor() {
        return OctavoDesignTokens.forAppearance(appearance).overlay;
    }

    View preferredInitialFocus() {
        if (retry.getVisibility() == VISIBLE) {
            return retry;
        }
        if (primary.getVisibility() == VISIBLE) {
            return primary;
        }
        if (secondary.getVisibility() == VISIBLE) {
            return secondary;
        }
        return heading;
    }

    boolean suppressHostMotion() {
        return appearance.reducedMotion();
    }

    private void setDescriptor(String digest, long byteCount) {
        String exactDigest = requireDigest(digest);
        if (byteCount <= 0 || byteCount > 536870912L) {
            throw new IllegalArgumentException(
                "Membership EPUB byte count is out of bounds");
        }
        detail.setText(
            "EPUB - " + humanReadableByteCount(byteCount)
                + " - " + shortDigest(exactDigest));
        detail.setContentDescription(
            "EPUB. " + humanReadableByteCount(byteCount)
                + ". Identifier " + shortDigest(exactDigest) + ".");
        detail.setVisibility(VISIBLE);
    }

    private void setStatus(String message, boolean error) {
        statusIsError = error;
        status.setText(message);
        status.setContentDescription(message);
        OctavoDesignTokens tokens = OctavoDesignTokens.forAppearance(appearance);
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
        Button[] actions = {primary, secondary, discard, retry};
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
        return Math.max(value, Math.round(value
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
            Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024.0));
    }

    private static String normalized(String value,
                                     int maximumCodePoints,
                                     String fallback) {
        if (value == null) {
            return fallback;
        }
        StringBuilder result = new StringBuilder();
        int written = 0;
        boolean space = false;
        for (int offset = 0;
             offset < value.length() && written < maximumCodePoints;) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint)) {
                space = result.length() > 0;
                continue;
            }
            if (space && written + 1 < maximumCodePoints) {
                result.append(' ');
                ++written;
            }
            space = false;
            result.appendCodePoint(codePoint);
            ++written;
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
    Button primaryForTesting() { return primary; }
    Button secondaryForTesting() { return secondary; }
    Button discardForTesting() { return discard; }
    Button retryForTesting() { return retry; }
    ScrollView scrollForTesting() { return scroll; }
    boolean statusIsErrorForTesting() { return statusIsError; }
}
