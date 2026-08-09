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

import java.util.ArrayList;
import java.util.List;

/**
 * Product-owned appearance confirmation content. The Activity owns candidate
 * identity, persistence, Back handling, sibling accessibility exclusion,
 * focus restoration, lifecycle, and host motion policy.
 */
final class OctavoAppearanceSyncPrompt extends LinearLayout {
    interface Listener {
        void onUseSettings();
        void onKeepMine();
        void onRetry();
    }

    private static final int MAX_DIFFERENCE_ROWS = 8;
    private static final int MAX_HEADING_CODE_POINTS = 160;
    private static final int MAX_STATUS_CODE_POINTS = 320;
    private static final String CHOICE_HEADING =
        "Another device uses different reading settings";
    private static final String CHOICE_STATUS =
        "Choose whether to use the other device's settings or keep yours.";
    private static final String DEFAULT_FAILURE_HEADING =
        "Reading settings update needs attention";
    private static final String DEFAULT_FAILURE_MESSAGE =
        "The reading settings update could not be completed.";

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView heading;
    private final TextView explanation;
    private final TextView differencesHeading;
    private final LinearLayout differenceList;
    private final List<TextView> differenceRows = new ArrayList<>();
    private final TextView status;
    private final Button useSettings;
    private final Button keepMine;
    private final Button retry;

    private OctavoAppearance appearance;
    private OctavoAppearance presentedProfile;
    private OctavoAppearance remoteProfile;
    private Ui0AndroidThemeAdapter ui0Adapter;
    private boolean statusIsError;

    OctavoAppearanceSyncPrompt(Context context,
                               OctavoAppearance presented,
                               OctavoAppearance remote,
                               Listener listener) {
        this(context, presented, listener);
        updateProfiles(presented, remote);
    }

    OctavoAppearanceSyncPrompt(Context context,
                               OctavoAppearance current,
                               Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException(
                "Appearance-sync prompt listener is required");
        }
        if (current == null) {
            throw new IllegalArgumentException(
                "Current appearance profile is required");
        }
        this.listener = listener;
        appearance = current;
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
            setAccessibilityPaneTitle("Reading settings confirmation");
        }

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setContentDescription("Reading settings confirmation");
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

        explanation = text(
            "Review the settings that differ from the ones you are using.",
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Typeface.NORMAL);
        LinearLayout.LayoutParams explanationLayout = matchWrap();
        explanationLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        content.addView(explanation, explanationLayout);

        differencesHeading = text(
            "Different settings",
            Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE,
            Typeface.BOLD);
        differencesHeading.setId(View.generateViewId());
        if (Build.VERSION.SDK_INT >= 28) {
            differencesHeading.setAccessibilityHeading(true);
        }
        LinearLayout.LayoutParams differencesHeadingLayout = matchWrap();
        differencesHeadingLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        content.addView(differencesHeading, differencesHeadingLayout);

        differenceList = new LinearLayout(context);
        differenceList.setOrientation(VERTICAL);
        differenceList.setFocusable(false);
        differenceList.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams differenceListLayout = matchWrap();
        differenceListLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        content.addView(differenceList, differenceListLayout);

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

        useSettings = button(
            "Use these settings",
            "Use the other device's reading settings");
        useSettings.setOnClickListener(view -> {
            if (!useSettings.isEnabled()) {
                return;
            }
            showWorking("Applying the other device's reading settings.");
            this.listener.onUseSettings();
        });
        LinearLayout.LayoutParams useLayout = matchWrap();
        useLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(useSettings, useLayout);

        keepMine = button(
            "Keep mine",
            "Keep your current reading settings");
        keepMine.setOnClickListener(view -> {
            if (!keepMine.isEnabled()) {
                return;
            }
            showWorking("Keeping your reading settings.");
            this.listener.onKeepMine();
        });
        LinearLayout.LayoutParams keepLayout = matchWrap();
        keepLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(keepMine, keepLayout);

        retry = button(
            "Retry",
            "Retry the reading settings update");
        retry.setVisibility(GONE);
        retry.setOnClickListener(view -> {
            if (!retry.isEnabled()) {
                return;
            }
            showWorking("Retrying the reading settings update.");
            this.listener.onRetry();
        });
        LinearLayout.LayoutParams retryLayout = matchWrap();
        retryLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        content.addView(retry, retryLayout);

        presentedProfile = current;
        remoteProfile = null;
        setDifferenceSectionVisible(false);
        applyAppearance(appearance);
        showRetryableFailure(
            DEFAULT_FAILURE_HEADING, DEFAULT_FAILURE_MESSAGE);
    }

    void updateProfiles(OctavoAppearance presented,
                        OctavoAppearance remote) {
        requireDifferentProfiles(presented, remote);
        List<Difference> differences = differences(presented, remote);
        if (differences.isEmpty()
                || differences.size() > MAX_DIFFERENCE_ROWS) {
            throw new IllegalArgumentException(
                "Appearance-sync profiles require bounded differences");
        }

        differenceList.removeAllViews();
        differenceRows.clear();
        for (int index = 0; index < differences.size(); index++) {
            Difference difference = differences.get(index);
            TextView row = text(
                difference.visibleText(),
                Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                Typeface.NORMAL);
            row.setContentDescription(difference.accessibilityText());
            row.setImportantForAccessibility(
                IMPORTANT_FOR_ACCESSIBILITY_YES);
            configureDifferenceRow(row);
            LinearLayout.LayoutParams layout = matchWrap();
            if (index > 0) {
                layout.topMargin = spacing(
                    Ui0AndroidThemeSnapshot.SpacingRole.CARD_GAP);
            }
            differenceList.addView(row, layout);
            differenceRows.add(row);
        }
        presentedProfile = presented;
        remoteProfile = remote;
        setDifferenceSectionVisible(true);
        showChoice();
    }

    void showChoice() {
        if (!hasRemoteChoice()) {
            throw new IllegalStateException(
                "Appearance choice requires different profiles");
        }
        heading.setText(CHOICE_HEADING);
        setStatus(CHOICE_STATUS, false);
        useSettings.setVisibility(VISIBLE);
        keepMine.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
        useSettings.setEnabled(true);
        keepMine.setEnabled(true);
        retry.setEnabled(false);
    }

    void showWorking(String message) {
        setStatus(normalized(
            message,
            MAX_STATUS_CODE_POINTS,
            "Updating the reading settings."), false);
        int choiceVisibility = hasRemoteChoice() ? VISIBLE : GONE;
        useSettings.setVisibility(choiceVisibility);
        keepMine.setVisibility(choiceVisibility);
        retry.setVisibility(GONE);
        useSettings.setEnabled(false);
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
                "Appearance-sync prompt appearance is required");
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
            explanation,
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY);
        configureText(
            differencesHeading,
            Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE,
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);
        for (TextView difference : differenceRows) {
            configureDifferenceRow(difference);
        }
        status.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        status.setTextColor(ui0Adapter.color(statusIsError
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        status.setMinHeight(controlHeight());
        configureButton(useSettings, true);
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
        return useSettings.getVisibility() == VISIBLE
                && useSettings.isEnabled()
            ? useSettings : heading;
    }

    boolean suppressHostMotion() {
        return appearance.reducedMotion();
    }

    private void showRetryableFailureInternal(String message) {
        setStatus(normalized(
            message,
            MAX_STATUS_CODE_POINTS,
            DEFAULT_FAILURE_MESSAGE), true);
        useSettings.setVisibility(GONE);
        keepMine.setVisibility(GONE);
        retry.setVisibility(VISIBLE);
        useSettings.setEnabled(false);
        keepMine.setEnabled(false);
        retry.setEnabled(true);
    }

    private void setDifferenceSectionVisible(boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        explanation.setVisibility(visibility);
        differencesHeading.setVisibility(visibility);
        differenceList.setVisibility(visibility);
    }

    private boolean hasRemoteChoice() {
        return presentedProfile != null
            && remoteProfile != null
            && !presentedProfile.equals(remoteProfile)
            && !differenceRows.isEmpty();
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

    private void configureDifferenceRow(TextView row) {
        configureText(
            row,
            Ui0AndroidThemeSnapshot.TypographyRole.BODY,
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);
        row.setMinHeight(Math.max(
            ui0Adapter.rowHeightPx(),
            ui0Adapter.dp(OctavoDesignTokens.TOUCH_TARGET_DP)));
        int horizontal = ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X);
        int vertical = ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_Y);
        row.setPadding(horizontal, vertical, horizontal, vertical);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ui0Adapter.optionBackground());
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

    private static List<Difference> differences(
        OctavoAppearance presented, OctavoAppearance remote) {
        List<Difference> result = new ArrayList<>(MAX_DIFFERENCE_ROWS);
        addDifference(
            result,
            "Theme",
            presented.themeId() != remote.themeId(),
            OctavoAppearance.themeLabel(presented.themeId()),
            OctavoAppearance.themeLabel(remote.themeId()));
        addDifference(
            result,
            "Font",
            presented.fontFamilyId() != remote.fontFamilyId(),
            OctavoAppearance.fontFamilyLabel(presented.fontFamilyId()),
            OctavoAppearance.fontFamilyLabel(remote.fontFamilyId()));
        addDifference(
            result,
            "Text size",
            presented.fontSizeSp() != remote.fontSizeSp(),
            textSizeTierLabel(presented.fontSizeSp()),
            textSizeTierLabel(remote.fontSizeSp()));
        addDifference(
            result,
            "Line spacing",
            presented.lineSpacingPermille()
                != remote.lineSpacingPermille(),
            OctavoAppearance.lineSpacingLabel(
                presented.lineSpacingPermille()),
            OctavoAppearance.lineSpacingLabel(
                remote.lineSpacingPermille()));
        addDifference(
            result,
            "Page width",
            presented.marginsId() != remote.marginsId(),
            OctavoAppearance.marginsLabel(presented.marginsId()),
            OctavoAppearance.marginsLabel(remote.marginsId()));
        addDifference(
            result,
            "Alignment",
            presented.alignmentId() != remote.alignmentId(),
            OctavoAppearance.alignmentLabel(presented.alignmentId()),
            OctavoAppearance.alignmentLabel(remote.alignmentId()));
        addDifference(
            result,
            "Publisher colors",
            presented.publisherColorsId()
                != remote.publisherColorsId(),
            OctavoAppearance.publisherColorsLabel(
                presented.publisherColorsId()),
            OctavoAppearance.publisherColorsLabel(
                remote.publisherColorsId()));
        addDifference(
            result,
            "Reduced motion",
            presented.reducedMotion() != remote.reducedMotion(),
            onOffLabel(presented.reducedMotion()),
            onOffLabel(remote.reducedMotion()));
        return result;
    }

    private static void addDifference(List<Difference> target,
                                      String label,
                                      boolean different,
                                      String current,
                                      String remote) {
        if (different) {
            target.add(new Difference(label, current, remote));
        }
    }

    private static String textSizeTierLabel(int fontSizeSp) {
        switch (fontSizeSp) {
            case 14: return "Compact";
            case 16: return "Standard";
            case 18: return "Comfortable";
            case 21: return "Large";
            case 24: return "Larger";
            case 28: return "Largest";
            default:
                throw new IllegalArgumentException(
                    "Unsupported appearance text size");
        }
    }

    private static String onOffLabel(boolean value) {
        return value ? "On" : "Off";
    }

    private static void requireDifferentProfiles(
        OctavoAppearance presented, OctavoAppearance remote) {
        if (presented == null || remote == null) {
            throw new IllegalArgumentException(
                "Appearance-sync profiles are required");
        }
        if (presented.equals(remote)) {
            throw new IllegalArgumentException(
                "Appearance-sync prompt requires different profiles");
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
                "UI0 Android appearance-sync theme is unavailable or "
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
    TextView explanationForTesting() { return explanation; }
    TextView differencesHeadingForTesting() { return differencesHeading; }
    LinearLayout differenceListForTesting() { return differenceList; }
    int differenceCountForTesting() { return differenceRows.size(); }
    TextView differenceForTesting(int index) {
        return index >= 0 && index < differenceRows.size()
            ? differenceRows.get(index) : null;
    }
    TextView statusForTesting() { return status; }
    Button useSettingsForTesting() { return useSettings; }
    Button keepMineForTesting() { return keepMine; }
    Button retryForTesting() { return retry; }
    ScrollView scrollForTesting() { return scroll; }
    OctavoAppearance presentedProfileForTesting() {
        return presentedProfile;
    }
    OctavoAppearance remoteProfileForTesting() { return remoteProfile; }
    boolean statusIsErrorForTesting() { return statusIsError; }

    private static final class Difference {
        final String label;
        final String current;
        final String remote;

        Difference(String label, String current, String remote) {
            this.label = label;
            this.current = current;
            this.remote = remote;
        }

        String visibleText() {
            return label + "\nCurrent: " + current
                + "\nOther device: " + remote;
        }

        String accessibilityText() {
            return label + ". Current: " + current
                + ". Other device: " + remote + ".";
        }
    }
}
