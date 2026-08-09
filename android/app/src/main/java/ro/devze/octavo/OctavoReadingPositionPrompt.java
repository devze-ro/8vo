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
 * Product-owned reading-position confirmation content. The Activity owns the
 * candidate identity, persistence, navigation, Back handling, sibling
 * accessibility exclusion, focus restoration, lifecycle, and motion.
 */
final class OctavoReadingPositionPrompt extends LinearLayout {
    interface Listener {
        void onGoThere();
        void onStayHere();
        void onRetry();
    }

    private static final int MAX_LABEL_CODE_POINTS = 160;
    private static final int MAX_STATUS_CODE_POINTS = 320;
    private static final String DEFAULT_LOCATION_LABEL = "a saved location";

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView heading;
    private final TextView explanation;
    private final TextView status;
    private final Button goThere;
    private final Button stayHere;
    private final Button retry;

    private OctavoAppearance appearance;
    private Ui0AndroidThemeAdapter ui0Adapter;
    private boolean statusIsError;

    OctavoReadingPositionPrompt(Context context,
                                OctavoAppearance initialAppearance,
                                String locationLabel,
                                Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException(
                "Reading-position prompt listener is required");
        }
        this.listener = listener;
        appearance = initialAppearance == null
            ? OctavoAppearance.defaults() : initialAppearance;
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
            setAccessibilityPaneTitle("Reading position confirmation");
        }

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setContentDescription("Reading position confirmation");
        addView(scroll, matchWrap());

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setFocusable(false);
        scroll.addView(content,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        heading = text(
            "", Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
            Typeface.BOLD);
        heading.setFocusable(true);
        heading.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        content.addView(heading, matchWrap());

        explanation = text(
            "Would you like to go there or keep reading here?",
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Typeface.NORMAL);
        LinearLayout.LayoutParams explanationLayout = matchWrap();
        explanationLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        content.addView(explanation, explanationLayout);

        status = text(
            "Choose whether to move to the other device's location.",
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
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        content.addView(status, statusLayout);

        goThere = button("Go there", "Go to the other device's position");
        goThere.setOnClickListener(view -> {
            if (!goThere.isEnabled()) {
                return;
            }
            showWorking("Opening the other device's position.");
            this.listener.onGoThere();
        });
        LinearLayout.LayoutParams goLayout = matchWrap();
        goLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(goThere, goLayout);

        stayHere = button("Stay here", "Keep reading at this position");
        stayHere.setOnClickListener(view -> {
            if (!stayHere.isEnabled()) {
                return;
            }
            showWorking("Keeping this reading position.");
            this.listener.onStayHere();
        });
        LinearLayout.LayoutParams stayLayout = matchWrap();
        stayLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(stayHere, stayLayout);

        retry = button("Retry", "Retry the reading position request");
        retry.setVisibility(GONE);
        retry.setOnClickListener(view -> {
            if (!retry.isEnabled()) {
                return;
            }
            showWorking("Retrying the reading position request.");
            this.listener.onRetry();
        });
        LinearLayout.LayoutParams retryLayout = matchWrap();
        retryLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(retry, retryLayout);

        setLocationLabel(locationLabel);
        applyAppearance(appearance);
        showChoice();
    }

    void setLocationLabel(String locationLabel) {
        String label = normalized(locationLabel,
                                  MAX_LABEL_CODE_POINTS,
                                  DEFAULT_LOCATION_LABEL);
        heading.setText("Another device is at " + label
                            + sentenceSuffix(label));
    }

    void showChoice() {
        setStatus(
            "Choose whether to move to the other device's location.", false);
        goThere.setVisibility(VISIBLE);
        stayHere.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        goThere.setEnabled(true);
        stayHere.setEnabled(true);
        retry.setEnabled(false);
    }

    void showWorking(String message) {
        setStatus(normalized(message,
                             MAX_STATUS_CODE_POINTS,
                             "Updating the reading position."),
                  false);
        goThere.setVisibility(VISIBLE);
        stayHere.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        goThere.setEnabled(false);
        stayHere.setEnabled(false);
        retry.setEnabled(false);
    }

    void showRetryableFailure(String message) {
        setStatus(normalized(message,
                             MAX_STATUS_CODE_POINTS,
                             "The reading position request could not be "
                                 + "completed."),
                  true);
        goThere.setVisibility(GONE);
        stayHere.setVisibility(GONE);
        retry.setVisibility(VISIBLE);
        goThere.setEnabled(false);
        stayHere.setEnabled(false);
        retry.setEnabled(true);
    }

    void showRetryableFailure(String failureHeading,
                              String message) {
        heading.setText(normalized(
            failureHeading,
            MAX_LABEL_CODE_POINTS,
            "Reading position update needs attention."));
        showRetryableFailure(message);
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException(
                "Reading-position prompt appearance is required");
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
        heading.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE));
        heading.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
        explanation.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BODY));
        explanation.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        status.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        status.setTextColor(ui0Adapter.color(statusIsError
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        configureButton(goThere, true);
        configureButton(stayHere, false);
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
        return goThere.getVisibility() == VISIBLE && goThere.isEnabled()
            ? goThere : heading;
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

    private static String sentenceSuffix(String value) {
        int last = value.codePointBefore(value.length());
        return last == '.' || last == '!' || last == '?'
                || last == 0x2026
            ? "" : ".";
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
                "UI0 Android reading-position theme is unavailable or "
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
    TextView statusForTesting() { return status; }
    Button goThereForTesting() { return goThere; }
    Button stayHereForTesting() { return stayHere; }
    Button retryForTesting() { return retry; }
    ScrollView scrollForTesting() { return scroll; }
}
