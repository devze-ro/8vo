package ro.devze.octavo;

import android.content.Context;
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

/**
 * Product-owned progress-display confirmation content. The Activity owns
 * candidate identity, persistence, Back handling, sibling accessibility
 * exclusion, focus restoration, lifecycle, and host motion policy.
 */
final class OctavoProgressSyncPrompt extends LinearLayout {
    interface Listener {
        void onUseDisplay();
        void onKeepMine();
        void onRetry();
    }

    private static final int MAX_HEADING_CODE_POINTS = 160;
    private static final int MAX_STATUS_CODE_POINTS = 320;
    private static final String CHOICE_HEADING =
        "Another device uses a different progress display";
    private static final String CHOICE_HELPER =
        "This changes only the reader's progress detail and does not move "
            + "your reading place.";
    private static final String CHOICE_STATUS =
        "Choose whether to use the other device's progress display or keep "
            + "yours.";
    private static final String DEFAULT_FAILURE_HEADING =
        "Progress display update needs attention";
    private static final String DEFAULT_FAILURE_MESSAGE =
        "The progress display update could not be completed.";

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView heading;
    private final TextView helper;
    private final TextView comparison;
    private final TextView status;
    private final Button useDisplay;
    private final Button keepMine;
    private final Button retry;

    private OctavoAppearance appearance;
    private OctavoProgressDisplay yours;
    private OctavoProgressDisplay other;
    private Ui0AndroidThemeAdapter ui0Adapter;
    private boolean statusIsError;

    OctavoProgressSyncPrompt(Context context,
                             OctavoAppearance initialAppearance,
                             OctavoProgressDisplay yours,
                             OctavoProgressDisplay other,
                             Listener listener) {
        this(context, initialAppearance, yours, listener);
        updateChoices(yours, other);
    }

    OctavoProgressSyncPrompt(Context context,
                             OctavoAppearance initialAppearance,
                             OctavoProgressDisplay current,
                             Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException(
                "Progress-sync prompt listener is required");
        }
        if (initialAppearance == null) {
            throw new IllegalArgumentException(
                "Progress-sync prompt appearance is required");
        }
        if (current == null) {
            throw new IllegalArgumentException(
                "Current progress display is required");
        }
        this.listener = listener;
        appearance = initialAppearance;
        ui0Adapter = resolveAdapter(
            appearance, getResources().getDisplayMetrics().density);

        setOrientation(VERTICAL);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(true);
        setClipToOutline(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (Build.VERSION.SDK_INT >= 28) {
            setAccessibilityPaneTitle("Progress display confirmation");
        }

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setContentDescription("Progress display confirmation");
        addView(scroll, matchWrap());

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setFocusable(false);
        scroll.addView(content,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        heading = text(
            CHOICE_HEADING,
            Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
            Typeface.BOLD);
        heading.setId(View.generateViewId());
        heading.setFocusable(true);
        heading.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        content.addView(heading, matchWrap());

        helper = text(
            CHOICE_HELPER,
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Typeface.NORMAL);
        LinearLayout.LayoutParams helperLayout = matchWrap();
        helperLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        content.addView(helper, helperLayout);

        comparison = text(
            "",
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Typeface.NORMAL);
        comparison.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams comparisonLayout = matchWrap();
        comparisonLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        content.addView(comparison, comparisonLayout);

        status = text(
            CHOICE_STATUS,
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
            Typeface.NORMAL);
        status.setMinHeight(controlHeight());
        status.setGravity(Gravity.CENTER_VERTICAL);
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
        statusLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        content.addView(status, statusLayout);

        useDisplay = button(
            "Use this display",
            "Use the other device's progress display");
        useDisplay.setOnClickListener(view -> {
            if (!useDisplay.isEnabled()) {
                return;
            }
            showWorking("Applying the other device's progress display.");
            this.listener.onUseDisplay();
        });
        LinearLayout.LayoutParams useLayout = matchWrap();
        useLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(useDisplay, useLayout);

        keepMine = button(
            "Keep mine",
            "Keep your current progress display");
        keepMine.setOnClickListener(view -> {
            if (!keepMine.isEnabled()) {
                return;
            }
            showWorking("Keeping your progress display.");
            this.listener.onKeepMine();
        });
        LinearLayout.LayoutParams keepLayout = matchWrap();
        keepLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(keepMine, keepLayout);

        retry = button(
            "Retry",
            "Retry the progress display update");
        retry.setVisibility(GONE);
        retry.setOnClickListener(view -> {
            if (!retry.isEnabled()) {
                return;
            }
            showWorking("Retrying the progress display update.");
            this.listener.onRetry();
        });
        LinearLayout.LayoutParams retryLayout = matchWrap();
        retryLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(retry, retryLayout);

        this.yours = current;
        other = null;
        setChoiceContentVisible(false);
        applyAppearance(appearance);
        showRetryableFailure(
            DEFAULT_FAILURE_HEADING, DEFAULT_FAILURE_MESSAGE);
    }

    void updateChoices(OctavoProgressDisplay current,
                       OctavoProgressDisplay remote) {
        requireDifferentChoices(current, remote);
        yours = current;
        other = remote;
        comparison.setText(
            "Progress display\nYours: " + current.label()
                + "\nOther device: " + remote.label());
        comparison.setContentDescription(
            "Progress display. Yours: " + current.label()
                + ". Other device: " + remote.label() + ".");
        setChoiceContentVisible(true);
        showChoice();
    }

    void showChoice() {
        if (!hasRemoteChoice()) {
            throw new IllegalStateException(
                "Progress display choice requires different values");
        }
        heading.setText(CHOICE_HEADING);
        setStatus(CHOICE_STATUS, false);
        useDisplay.setVisibility(VISIBLE);
        keepMine.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        useDisplay.setEnabled(true);
        keepMine.setEnabled(true);
        retry.setEnabled(false);
    }

    void showWorking(String message) {
        setStatus(normalized(
            message,
            MAX_STATUS_CODE_POINTS,
            "Updating the progress display."), false);
        int choiceVisibility = hasRemoteChoice() ? VISIBLE : GONE;
        useDisplay.setVisibility(choiceVisibility);
        keepMine.setVisibility(choiceVisibility);
        retry.setVisibility(GONE);
        useDisplay.setEnabled(false);
        keepMine.setEnabled(false);
        retry.setEnabled(false);
    }

    void showRetryableFailure(String message) {
        heading.setText(hasRemoteChoice()
            ? CHOICE_HEADING : DEFAULT_FAILURE_HEADING);
        showRetryableFailureInternal(message);
    }

    void showRetryableFailure(String failureHeading,
                              String message) {
        heading.setText(normalized(
            failureHeading,
            MAX_HEADING_CODE_POINTS,
            DEFAULT_FAILURE_HEADING));
        showRetryableFailureInternal(message);
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException(
                "Progress-sync prompt appearance is required");
        }
        appearance = updated;
        ui0Adapter = resolveAdapter(
            appearance, getResources().getDisplayMetrics().density);
        int padding = ui0Adapter.densityPx(
            Ui0AndroidThemeSnapshot.DensityRole.PANEL_PADDING);
        content.setPadding(padding, padding, padding, padding);
        int sheet = ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.SIDEBAR_BACKGROUND);
        setBackground(ui0Adapter.panelBackground());
        scroll.setBackgroundColor(sheet);
        content.setBackgroundColor(sheet);

        configureText(
            heading,
            Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);
        configureText(
            helper,
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY);
        configureComparison();
        status.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        status.setTextColor(ui0Adapter.color(statusIsError
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        status.setMinHeight(controlHeight());
        configureButton(useDisplay, true);
        configureButton(keepMine, false);
        configureButton(retry, true);
        invalidate();
    }

    int overlayColor() {
        return ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.OVERLAY);
    }

    View preferredInitialFocus() {
        if (retry.getVisibility() == VISIBLE && retry.isEnabled()) {
            return retry;
        }
        return useDisplay.getVisibility() == VISIBLE
                && useDisplay.isEnabled()
            ? useDisplay : heading;
    }

    boolean suppressHostMotion() {
        return appearance.reducedMotion();
    }

    private void showRetryableFailureInternal(String message) {
        setStatus(normalized(
            message,
            MAX_STATUS_CODE_POINTS,
            DEFAULT_FAILURE_MESSAGE), true);
        useDisplay.setVisibility(GONE);
        keepMine.setVisibility(GONE);
        retry.setVisibility(VISIBLE);
        useDisplay.setEnabled(false);
        keepMine.setEnabled(false);
        retry.setEnabled(true);
    }

    private void setChoiceContentVisible(boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        helper.setVisibility(visibility);
        comparison.setVisibility(visibility);
    }

    private boolean hasRemoteChoice() {
        return yours != null && other != null && yours != other;
    }

    private void setStatus(String message, boolean error) {
        statusIsError = error;
        status.setText(message);
        status.setTextColor(ui0Adapter.color(error
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
    }

    private TextView text(String value,
                          Ui0AndroidThemeSnapshot.TypographyRole role,
                          int style) {
        TextView result = new TextView(getContext());
        result.setText(value);
        result.setTextSize(ui0Adapter.textSizeSp(role));
        result.setTypeface(Typeface.DEFAULT, style);
        result.setLineSpacing(0.0f, 1.12f);
        return result;
    }

    private Button button(String value, String description) {
        Button result = new Button(getContext());
        result.setId(View.generateViewId());
        result.setText(value);
        result.setContentDescription(description);
        result.setFocusable(true);
        result.setFocusableInTouchMode(true);
        result.setAllCaps(false);
        result.setDefaultFocusHighlightEnabled(false);
        return result;
    }

    private void configureText(
        TextView text,
        Ui0AndroidThemeSnapshot.TypographyRole typography,
        Ui0AndroidThemeSnapshot.ColorRole color) {
        text.setTextSize(ui0Adapter.textSizeSp(typography));
        text.setTextColor(ui0Adapter.color(color));
    }

    private void configureComparison() {
        configureText(
            comparison,
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);
        comparison.setMinHeight(Math.max(
            ui0Adapter.rowHeightPx(),
            ui0Adapter.dp(OctavoDesignTokens.TOUCH_TARGET_DP)));
        int horizontal = ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X);
        int vertical = ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_Y);
        comparison.setPadding(horizontal, vertical, horizontal, vertical);
        comparison.setGravity(Gravity.CENTER_VERTICAL);
        comparison.setBackground(ui0Adapter.optionBackground());
    }

    private void configureButton(Button button, boolean action) {
        button.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
        button.setMinWidth(controlHeight());
        button.setMinHeight(controlHeight());
        button.setPadding(controlPaddingX(), controlPaddingY(),
                          controlPaddingX(), controlPaddingY());
        button.setGravity(Gravity.CENTER);
        button.setTextColor(action
            ? ui0Adapter.actionTextColors()
            : ui0Adapter.neutralTextColors());
        button.setBackground(action
            ? ui0Adapter.actionBackground()
            : ui0Adapter.neutralBackground());
    }

    private int spacing(Ui0AndroidThemeSnapshot.SpacingRole role) {
        return ui0Adapter.spacingPx(role);
    }

    private int controlHeight() {
        return Math.max(
            ui0Adapter.densityPx(
                Ui0AndroidThemeSnapshot.DensityRole.CONTROL_HEIGHT),
            ui0Adapter.dp(OctavoDesignTokens.TOUCH_TARGET_DP));
    }

    private int controlPaddingX() {
        return ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X);
    }

    private int controlPaddingY() {
        return ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_Y);
    }

    private static void requireDifferentChoices(
        OctavoProgressDisplay current, OctavoProgressDisplay remote) {
        if (current == null || remote == null) {
            throw new IllegalArgumentException(
                "Progress display choices are required");
        }
        if (current == remote) {
            throw new IllegalArgumentException(
                "Progress-sync prompt requires different choices");
        }
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

    private static Ui0AndroidThemeAdapter resolveAdapter(
        OctavoAppearance appearance, float density) {
        OctavoDesignTokens product =
            OctavoDesignTokens.forAppearance(appearance);
        Ui0AndroidThemeSnapshot snapshot = Ui0AndroidThemeSnapshot.parse(
            OctavoNative.ui0AndroidThemeSnapshot(
                product.darkAppearance,
                product.nativeUi0Colors()));
        if (snapshot == null) {
            throw new IllegalStateException(
                "UI0 Android progress-sync theme is unavailable or "
                    + "incompatible");
        }
        return new Ui0AndroidThemeAdapter(snapshot, density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    TextView headingForTesting() { return heading; }
    TextView helperForTesting() { return helper; }
    TextView comparisonForTesting() { return comparison; }
    TextView statusForTesting() { return status; }
    Button useDisplayForTesting() { return useDisplay; }
    Button keepMineForTesting() { return keepMine; }
    Button retryForTesting() { return retry; }
    ScrollView scrollForTesting() { return scroll; }
    OctavoProgressDisplay yoursForTesting() { return yours; }
    OctavoProgressDisplay otherForTesting() { return other; }
    boolean statusIsErrorForTesting() { return statusIsError; }
}
