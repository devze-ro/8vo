package ro.devze.octavo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Host-owned structural-navigation sheet. The Activity owns presentation and
 * motion; this class owns only the bounded, real Android control hierarchy.
 */
final class OctavoNavigationPanel extends LinearLayout {
    interface Listener {
        void onDismiss();
        void onContentsJump(int navIndex);
        void onChapter(int oneBased);
        void onLocation(long location);
        void onPage(long oneBased);
        void onPercentage(int percentage);
        void onHistory(boolean forward);
        void onProgressDisplayRequested(OctavoProgressDisplay display);
    }

    private static final int MAX_VISUAL_DEPTH = 4;
    private static final int MAX_STATUS_CHARS = 320;
    private static final int COMPACT_HEIGHT_DP = 360;

    private final Listener listener;
    private final LinearLayout header;
    private final TextView title;
    private final Button dismissButton;
    private final RadioGroup tabs;
    private final RadioButton contentsTab;
    private final RadioButton goToTab;
    private final LinearLayout historyControls;
    private final Button returnButton;
    private final Button forwardButton;
    private final TextView status;
    private final FrameLayout body;
    private final ScrollView contentsScroll;
    private final LinearLayout contentsList;
    private final ScrollView goToScroll;
    private final LinearLayout goToContent;
    private final GoToControl chapterControl;
    private final GoToControl locationControl;
    private final GoToControl pageControl;
    private final GoToControl percentageControl;
    private final RadioGroup progressOptions;
    private final List<RadioButton> progressButtons = new ArrayList<>();
    private final List<RowBinding> rowBindings = new ArrayList<>();
    private final List<TextView> primaryText = new ArrayList<>();
    private final List<TextView> secondaryText = new ArrayList<>();
    private final List<Button> neutralButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private final List<EditText> inputs = new ArrayList<>();
    private final Map<TextView, Ui0AndroidThemeSnapshot.TypographyRole>
        semanticTextRoles = new HashMap<>();
    private TextView contentsEmptyState;

    private OctavoNavigation snapshot;
    private OctavoAppearance appearance;
    private Ui0AndroidThemeSnapshot ui0Theme;
    private Ui0AndroidThemeAdapter ui0Adapter;
    private OctavoProgressDisplay presentedProgress;
    private FocusedRow deferredRowFocus;
    private boolean synchronizing;
    private boolean statusIsError;
    private int statusRevision;
    private boolean failureOwnsStatus;
    private long failureTransactionGeneration = -1;
    private boolean acceptedRequestOwnsStatus;
    private long acceptedTransactionGeneration = -1;
    private boolean compactHeightConfigured;
    private boolean compactHeightApplied;

    OctavoNavigationPanel(Context context, Listener listener) {
        this(context,
             OctavoAppearance.defaults(),
             OctavoProgressDisplay.defaults(),
             listener);
    }

    OctavoNavigationPanel(Context context,
                          OctavoAppearance initialAppearance,
                          OctavoProgressDisplay initialProgress,
                          Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException(
                "Navigation panel listener is required");
        }
        this.listener = listener;
        appearance = initialAppearance == null
            ? OctavoAppearance.defaults() : initialAppearance;
        ui0Theme = resolveTheme(appearance);
        ui0Adapter = new Ui0AndroidThemeAdapter(
            ui0Theme,
            getResources().getDisplayMetrics().density);
        presentedProgress = initialProgress == null
            ? OctavoProgressDisplay.defaults() : initialProgress;

        setId(R.id.octavo_navigation_panel);
        setOrientation(VERTICAL);
        int panelPadding = ui0Adapter.densityPx(
            Ui0AndroidThemeSnapshot.DensityRole.PANEL_PADDING);
        setPadding(panelPadding, panelPadding, panelPadding, panelPadding);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Build.VERSION.SDK_INT >= 28) {
            setAccessibilityPaneTitle(
                context.getString(R.string.reader_navigation));
        }

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, matchWrap());

        title = textView(context.getString(R.string.reader_navigation),
                         Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
                         Typeface.BOLD,
                         primaryText);
        title.setId(R.id.octavo_navigation_title);
        title.setContentDescription("Reader navigation");
        markHeading(title);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleLayout);

        dismissButton = button(
            context.getString(R.string.navigation_done),
            "Close reader navigation");
        dismissButton.setId(R.id.octavo_navigation_done);
        dismissButton.setOnClickListener(view -> listener.onDismiss());
        actionButtons.add(dismissButton);
        header.addView(dismissButton, wrapWrap());

        tabs = new RadioGroup(context);
        tabs.setId(R.id.octavo_navigation_tabs);
        tabs.setOrientation(HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setContentDescription("Navigation sections");
        tabs.setFocusable(false);
        LinearLayout.LayoutParams tabsLayout = matchWrap();
        tabsLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        addView(tabs, tabsLayout);

        contentsTab = tab(
            context.getString(R.string.navigation_contents),
            "Show table of contents");
        contentsTab.setId(R.id.octavo_navigation_contents_tab);
        goToTab = tab(context.getString(R.string.navigation_go_to),
                     "Show go to controls");
        goToTab.setId(R.id.octavo_navigation_go_to_tab);
        tabs.addView(contentsTab, weightedWrap());
        tabs.addView(goToTab, weightedWrap());

        historyControls = new LinearLayout(context);
        historyControls.setId(R.id.octavo_navigation_history);
        historyControls.setOrientation(HORIZONTAL);
        historyControls.setGravity(Gravity.CENTER_VERTICAL);
        historyControls.setContentDescription("Navigation history");
        LinearLayout.LayoutParams historyLayout = matchWrap();
        historyLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        addView(historyControls, historyLayout);

        returnButton = button(
            context.getString(R.string.navigation_return),
            "Return to the previous reading position");
        returnButton.setId(R.id.octavo_navigation_return);
        returnButton.setOnClickListener(view -> {
            if (historyAvailable(false)) {
                listener.onHistory(false);
            }
        });
        neutralButtons.add(returnButton);
        historyControls.addView(returnButton, weightedWrap());

        forwardButton = button(
            context.getString(R.string.navigation_forward),
            "Move forward in navigation history");
        forwardButton.setId(R.id.octavo_navigation_forward);
        forwardButton.setOnClickListener(view -> {
            if (historyAvailable(true)) {
                listener.onHistory(true);
            }
        });
        neutralButtons.add(forwardButton);
        LinearLayout.LayoutParams forwardLayout = weightedWrap();
        forwardLayout.setMarginStart(ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        historyControls.addView(forwardButton, forwardLayout);

        status = textView("Preparing navigation.",
                           Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                          Typeface.NORMAL,
                          secondaryText);
        status.setId(R.id.octavo_navigation_status);
        status.setMinHeight(controlHeightPx());
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setMaxLines(3);
        status.setEllipsize(TextUtils.TruncateAt.END);
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
        statusLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        statusLayout.bottomMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        addView(status, statusLayout);

        body = new FrameLayout(context);
        LinearLayout.LayoutParams bodyLayout = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        addView(body, bodyLayout);

        contentsScroll = new ScrollView(context);
        contentsScroll.setId(R.id.octavo_navigation_contents_scroll);
        contentsScroll.setFillViewport(true);
        contentsScroll.setClipToPadding(false);
        contentsScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        contentsScroll.setVerticalScrollBarEnabled(false);
        contentsScroll.setContentDescription("Table of contents");
        contentsList = new LinearLayout(context);
        contentsList.setId(R.id.octavo_navigation_contents_list);
        contentsList.setOrientation(VERTICAL);
        contentsList.setAccessibilityDelegate(
            new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCollectionInfo(
                        AccessibilityNodeInfo.CollectionInfo.obtain(
                            rowBindings.size(),
                            1,
                            false,
                            AccessibilityNodeInfo.CollectionInfo
                                .SELECTION_MODE_SINGLE));
                }
            });
        contentsScroll.addView(contentsList,
                               new ScrollView.LayoutParams(
                                   ViewGroup.LayoutParams.MATCH_PARENT,
                                   ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(contentsScroll, matchMatchFrame());

        goToScroll = new ScrollView(context);
        goToScroll.setId(R.id.octavo_navigation_go_to_scroll);
        goToScroll.setFillViewport(true);
        goToScroll.setClipToPadding(false);
        goToScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        goToScroll.setVerticalScrollBarEnabled(false);
        goToScroll.setContentDescription("Go to and progress controls");
        goToContent = new LinearLayout(context);
        goToContent.setId(R.id.octavo_navigation_go_to_content);
        goToContent.setOrientation(VERTICAL);
        goToScroll.addView(goToContent,
                           new ScrollView.LayoutParams(
                               ViewGroup.LayoutParams.MATCH_PARENT,
                               ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(goToScroll, matchMatchFrame());

        TextView goToHeading = textView("Go to",
                                        Ui0AndroidThemeSnapshot
                                            .TypographyRole.SECTION_TITLE,
                                        Typeface.BOLD,
                                        primaryText);
        markHeading(goToHeading);
        goToContent.addView(goToHeading, matchWrap());
        TextView goToNote = textView(
            "Choose an exact structural or canonical destination. Your "
                + "saved place changes only after the destination is shown.",
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
            Typeface.NORMAL,
            secondaryText);
        LinearLayout.LayoutParams goToNoteLayout = matchWrap();
        goToNoteLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.TEXT_STACK_GAP);
        goToNoteLayout.bottomMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        goToContent.addView(goToNote, goToNoteLayout);

        chapterControl = addGoToControl(
            context.getString(R.string.navigation_chapter),
            "Chapter number",
            R.id.octavo_navigation_chapter_input,
            R.id.octavo_navigation_chapter_go,
            this::submitChapter);
        locationControl = addGoToControl(
            context.getString(R.string.navigation_location),
            "Canonical location",
            R.id.octavo_navigation_location_input,
            R.id.octavo_navigation_location_go,
            this::submitLocation);
        pageControl = addGoToControl(
            context.getString(R.string.navigation_page),
            "Page number in the current section",
            R.id.octavo_navigation_page_input,
            R.id.octavo_navigation_page_go,
            this::submitPage);
        percentageControl = addGoToControl(
            context.getString(R.string.navigation_percentage),
            "Book percentage from 0 to 100",
            R.id.octavo_navigation_percentage_input,
            R.id.octavo_navigation_percentage_go,
            this::submitPercentage);

        TextView progressHeading = textView(
            context.getString(R.string.navigation_progress_display),
            Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE,
            Typeface.BOLD,
            primaryText);
        markHeading(progressHeading);
        LinearLayout.LayoutParams progressHeadingLayout = matchWrap();
        progressHeadingLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.MAJOR_GROUP_GAP);
        goToContent.addView(progressHeading, progressHeadingLayout);

        TextView progressNote = textView(
            "Choose the compact progress detail shown in the reader. The "
                + "choice becomes current after a matching page is shown.",
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
            Typeface.NORMAL,
            secondaryText);
        LinearLayout.LayoutParams progressNoteLayout = matchWrap();
        progressNoteLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.TEXT_STACK_GAP);
        progressNoteLayout.bottomMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        goToContent.addView(progressNote, progressNoteLayout);

        progressOptions = new RadioGroup(context);
        progressOptions.setId(R.id.octavo_navigation_progress_options);
        progressOptions.setOrientation(VERTICAL);
        progressOptions.setContentDescription("Reader progress choices");
        progressOptions.setFocusable(false);
        for (OctavoProgressDisplay display : OctavoProgressDisplay.values()) {
            RadioButton option = new PresentedProgressRadioButton(context);
            option.setId(View.generateViewId());
            option.setTag(display);
            option.setText(display.label());
            option.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinHeight(controlHeightPx());
            option.setPadding(controlPaddingXPx(),
                              controlPaddingYPx(),
                              controlPaddingXPx(),
                              controlPaddingYPx());
            option.setContentDescription(
                display.label() + " reader progress display");
            option.setDefaultFocusHighlightEnabled(false);
            option.setOnClickListener(
                view -> requestProgressDisplay(option));
            progressOptions.addView(option, matchWrapRadio());
            progressButtons.add(option);
        }
        goToContent.addView(progressOptions, matchWrap());

        tabs.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == contentsTab.getId()) {
                showSection(true);
            } else if (checkedId == goToTab.getId()) {
                showSection(false);
            }
        });
        contentsTab.setChecked(true);
        updateProgressDisplay(presentedProgress);
        updateSnapshot(null);
        applyAppearance(appearance);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        boolean compactHeight = heightMode != MeasureSpec.UNSPECIFIED
            && MeasureSpec.getSize(heightMeasureSpec)
                <= ui0Adapter.dp(COMPACT_HEIGHT_DP);
        applyCompactHeight(compactHeight);
        int statusLines = compactHeight ? 1 : 3;
        if (status.getMaxLines() != statusLines) {
            status.setMaxLines(statusLines);
        }
        title.setMaxLines(compactHeight ? 1 : 2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void applyCompactHeight(boolean compactHeight) {
        if (compactHeightConfigured
                && compactHeightApplied == compactHeight) {
            return;
        }
        compactHeightConfigured = true;
        compactHeightApplied = compactHeight;
        int compactGap = ui0Adapter.treePx(
            Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP);
        int panelVerticalPadding = compactHeight
            ? compactGap
            : ui0Adapter.densityPx(
                Ui0AndroidThemeSnapshot.DensityRole.PANEL_PADDING);
        setPadding(getPaddingLeft(),
                   panelVerticalPadding,
                   getPaddingRight(),
                   panelVerticalPadding);

        LinearLayout.LayoutParams tabsLayout =
            (LinearLayout.LayoutParams)tabs.getLayoutParams();
        tabsLayout.topMargin = compactHeight
            ? ui0Adapter.spacingPx(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP)
            : ui0Adapter.spacingPx(
                Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        tabs.setLayoutParams(tabsLayout);

        LinearLayout.LayoutParams statusLayout =
            (LinearLayout.LayoutParams)status.getLayoutParams();
        int statusMargin = compactHeight
            ? compactGap
            : ui0Adapter.spacingPx(
                Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        statusLayout.topMargin = statusMargin;
        statusLayout.bottomMargin = statusMargin;
        status.setLayoutParams(statusLayout);
        status.setMinHeight(compactHeight ? 0 : controlHeightPx());
    }

    void updateSnapshot(OctavoNavigation navigation) {
        updateSnapshot(navigation, true);
    }

    void updateSnapshotWithStatus(OctavoNavigation navigation,
                                  String message) {
        updateSnapshot(navigation, false);
        showStatus(message);
        acceptedRequestOwnsStatus =
            snapshot != null && snapshot.isPending();
        acceptedTransactionGeneration = acceptedRequestOwnsStatus
            ? snapshot.transactionGeneration() : -1;
    }

    void updateAlreadyPresentedSnapshot(OctavoNavigation navigation) {
        clearFailureStatusOwnership();
        clearAcceptedRequestStatusOwnership();
        updateSnapshot(navigation, true);
    }

    private void updateSnapshot(OctavoNavigation navigation,
                                boolean publishSnapshotStatus) {
        FocusedRow focused = focusedRow();
        boolean reuse = contentsStructurallyEquivalent(navigation);
        snapshot = navigation;
        if (reuse) {
            refreshContentsInPlace();
        } else {
            rebuildContents();
        }
        updateAvailability();
        if (publishSnapshotStatus) {
            updateSnapshotStatus();
        }
        refreshFocusOrder();
        reconcileRowFocus(focused);
    }

    void updateProgressDisplay(OctavoProgressDisplay progress) {
        if (progress == null) {
            throw new IllegalArgumentException(
                "Presented progress display is required");
        }
        presentedProgress = progress;
        synchronizing = true;
        try {
            for (RadioButton button : progressButtons) {
                if (button.getTag() == progress) {
                    progressOptions.check(button.getId());
                    break;
                }
            }
        } finally {
            synchronizing = false;
        }
    }

    void applyAppearance(OctavoAppearance newAppearance) {
        if (newAppearance == null) {
            throw new IllegalArgumentException(
                "Navigation appearance is required");
        }
        Ui0AndroidThemeSnapshot resolved;
        try {
            resolved = resolveTheme(newAppearance);
        } catch (IllegalStateException failure) {
            showError(
                "Reader navigation styling could not be updated. Close and "
                    + "reopen Navigation to retry.");
            return;
        }
        appearance = newAppearance;
        ui0Theme = resolved;
        ui0Adapter = new Ui0AndroidThemeAdapter(
            ui0Theme,
            getResources().getDisplayMetrics().density);
        compactHeightConfigured = false;
        int panelPadding = ui0Adapter.densityPx(
            Ui0AndroidThemeSnapshot.DensityRole.PANEL_PADDING);
        setPadding(panelPadding, panelPadding, panelPadding, panelPadding);
        int sheet = ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.SIDEBAR_BACKGROUND);
        setBackground(ui0Adapter.panelBackground());
        header.setBackgroundColor(sheet);
        body.setBackgroundColor(sheet);
        contentsScroll.setBackgroundColor(sheet);
        contentsList.setBackgroundColor(sheet);
        goToScroll.setBackgroundColor(sheet);
        goToContent.setBackgroundColor(sheet);
        for (Map.Entry<TextView,
                       Ui0AndroidThemeSnapshot.TypographyRole> entry
                 : semanticTextRoles.entrySet()) {
            entry.getKey().setTextSize(
                ui0Adapter.textSizeSp(entry.getValue()));
        }
        for (TextView text : primaryText) {
            text.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
            text.setHighlightColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.SELECTION));
        }
        for (TextView text : secondaryText) {
            text.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            text.setHighlightColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.SELECTION));
        }
        status.setTextColor(statusIsError
            ? ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.DANGER)
            : ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        dismissButton.setTextColor(ui0Adapter.actionTextColors());
        dismissButton.setBackground(ui0Adapter.actionBackground());
        for (Button button : neutralButtons) {
            applyButtonGeometry(button);
            button.setTextColor(ui0Adapter.neutralTextColors());
            button.setBackground(ui0Adapter.neutralBackground());
        }
        for (Button button : actionButtons) {
            applyButtonGeometry(button);
            if (button == dismissButton) {
                continue;
            }
            button.setTextColor(ui0Adapter.actionTextColors());
            button.setBackground(ui0Adapter.actionBackground());
        }
        ColorStateList radioText = ui0Adapter.radioTextColors();
        ColorStateList radioTint = ui0Adapter.radioTintColors();
        for (RadioButton tab : new RadioButton[] {contentsTab, goToTab}) {
            applyRadioGeometry(tab);
            tab.setTextColor(radioText);
            tab.setButtonTintList(radioTint);
            tab.setBackground(ui0Adapter.optionBackground());
        }
        for (RadioButton option : progressButtons) {
            applyRadioGeometry(option);
            option.setTextColor(radioText);
            option.setButtonTintList(radioTint);
            option.setBackground(ui0Adapter.optionBackground());
        }
        for (EditText input : inputs) {
            input.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.BODY));
            input.setMinHeight(controlHeightPx());
            input.setPadding(textInputPaddingXPx(),
                             textInputPaddingYPx(),
                             textInputPaddingXPx(),
                             textInputPaddingYPx());
            input.setTextColor(ui0Adapter.inputTextColors());
            input.setHintTextColor(ui0Adapter.inputHintColors());
            input.setBackground(ui0Adapter.inputBackground());
            ui0Adapter.applyTextInputEditingColors(input);
        }
        for (RowBinding binding : rowBindings) {
            applyRowStyle(binding);
        }
        invalidate();
    }

    void showError(String message) {
        clearAcceptedRequestStatusOwnership();
        failureOwnsStatus = true;
        failureTransactionGeneration = snapshot == null
            ? -1 : snapshot.transactionGeneration();
        setStatus(message == null || message.trim().isEmpty()
                      ? "Navigation could not complete that request."
                      : message,
                  true);
    }

    void showStatus(String message) {
        clearFailureStatusOwnership();
        clearAcceptedRequestStatusOwnership();
        setStatus(message == null || message.trim().isEmpty()
                      ? "Navigation is ready."
                      : message,
                  false);
    }

    private boolean contentsStructurallyEquivalent(
        OctavoNavigation navigation) {
        if (snapshot == null || navigation == null
            || snapshot.rowCount() == 0
            || snapshot.rowCount() != navigation.rowCount()
            || rowBindings.size() != navigation.rowCount()) {
            return false;
        }
        for (int index = 0; index < navigation.rowCount(); ++index) {
            RowBinding binding = rowBindings.get(index);
            OctavoNavigation.Row row = navigation.row(index);
            boolean parent = index + 1 < navigation.rowCount()
                && navigation.row(index + 1).depth() > row.depth();
            if (binding.row.navIndex() != row.navIndex()
                || binding.row.depth() != row.depth()
                || !binding.row.label().equals(row.label())
                || binding.parent != parent) {
                return false;
            }
        }
        return true;
    }

    private void refreshContentsInPlace() {
        for (int index = 0; index < snapshot.rowCount(); ++index) {
            RowBinding binding = rowBindings.get(index);
            binding.row = snapshot.row(index);
            binding.parent = index + 1 < snapshot.rowCount()
                && snapshot.row(index + 1).depth()
                    > binding.row.depth();
            refreshRowPresentation(binding);
        }
    }

    private void requestProgressDisplay(RadioButton requested) {
        if (synchronizing || requested == null) {
            return;
        }
        if (!navigationInteractive()) {
            updateProgressDisplay(presentedProgress);
            return;
        }
        if (requested.isChecked()) {
            return;
        }
        Object tag = requested.getTag();
        if (tag instanceof OctavoProgressDisplay) {
            listener.onProgressDisplayRequested(
                (OctavoProgressDisplay)tag);
            // User activation never changes checked state. Only a later
            // presented callback may advance it.
            updateProgressDisplay(presentedProgress);
        }
    }

    private void refreshRowPresentation(RowBinding binding) {
        binding.button.setTag(binding.row.navIndex());
        binding.button.setContentDescription(
            rowDescription(binding.row, binding.parent));
        if (Build.VERSION.SDK_INT >= 28) {
            binding.button.setAccessibilityHeading(binding.parent);
        }
        applyRowStyle(binding);
    }

    private void rebuildContents() {
        Map<Integer, Integer> previousIds = new HashMap<>();
        for (RowBinding binding : rowBindings) {
            previousIds.put(binding.row.navIndex(), binding.button.getId());
        }
        if (contentsEmptyState != null) {
            secondaryText.remove(contentsEmptyState);
            semanticTextRoles.remove(contentsEmptyState);
            contentsEmptyState = null;
        }
        contentsList.removeAllViews();
        rowBindings.clear();
        if (snapshot == null || snapshot.rowCount() == 0) {
            TextView empty = textView(
                snapshot == null
                    ? "No navigation information is available yet."
                    : "This book has no usable structural destinations.",
                Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                Typeface.NORMAL,
                secondaryText);
            empty.setMinHeight(rowHeightPx());
            contentsEmptyState = empty;
            empty.setGravity(Gravity.CENTER_VERTICAL);
            contentsList.addView(empty, matchWrap());
            return;
        }

        for (int index = 0; index < snapshot.rowCount(); ++index) {
            OctavoNavigation.Row row = snapshot.row(index);
            boolean parent = index + 1 < snapshot.rowCount()
                && snapshot.row(index + 1).depth() > row.depth();
            Ui0NavigationRow destination = new Ui0NavigationRow(getContext());
            Integer previousId = previousIds.get(row.navIndex());
            destination.setId(previousId == null
                                  ? View.generateViewId() : previousId);
            configureButton(destination,
                            rowDescription(row, parent));
            RowBinding binding = new RowBinding(destination,
                                                row,
                                                parent,
                                                index);
            destination.setOnClickListener(
                view -> {
                    if (binding.button.isEnabled()
                        && navigationInteractive()
                        && binding.row.isDestinationValid()) {
                        listener.onContentsJump(binding.row.navIndex());
                    }
                });
            destination.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                        View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setCollectionItemInfo(
                            AccessibilityNodeInfo.CollectionItemInfo.obtain(
                                binding.rowIndex,
                                1,
                                0,
                                1,
                                binding.parent,
                                binding.row.isCurrent()));
                    }
                });
            rowBindings.add(binding);
            refreshRowPresentation(binding);
            LinearLayout.LayoutParams rowLayout = matchWrap();
            rowLayout.bottomMargin = ui0Adapter.treePx(
                Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP);
            contentsList.addView(destination, rowLayout);
        }
    }

    private boolean navigationInteractive() {
        return snapshot != null && snapshot.isReady()
            && !snapshot.isPending();
    }

    private boolean historyAvailable(boolean forward) {
        if (!navigationInteractive()) {
            return false;
        }
        return forward ? snapshot.historyForward() > 0
            : snapshot.historyBack() > 0;
    }

    private void updateAvailability() {
        boolean interactive = navigationInteractive();
        for (RowBinding binding : rowBindings) {
            binding.button.setEnabled(interactive
                                      && binding.row.isDestinationValid());
        }
        returnButton.setEnabled(historyAvailable(false));
        forwardButton.setEnabled(historyAvailable(true));
        chapterControl.setEnabled(interactive);
        locationControl.setEnabled(interactive
                                   && snapshot.isLocationReady());
        pageControl.setEnabled(interactive && snapshot.isPageMeaningful());
        percentageControl.setEnabled(interactive);
        for (RadioButton button : progressButtons) {
            button.setEnabled(interactive);
        }
        locationControl.input.setContentDescription(
            snapshot != null && snapshot.isLocationReady()
                ? "Canonical location, from 1 to "
                    + snapshot.locationCount()
                : "Canonical location unavailable");
        pageControl.input.setContentDescription(
            snapshot != null && snapshot.isPageMeaningful()
                ? "Page number in the current section, from 1 to "
                    + snapshot.pageCount()
                : "Page navigation is not meaningful for this view");
    }

    private void updateSnapshotStatus() {
        /*
         * An explicit failure owns the live status through equivalent native
         * rollback/ready refresh packets, even if the failed transaction
         * generation reaches Java after the error. Only a newer pending
         * transaction (or showStatus from a new user request) supersedes it.
         */
        if (failureOwnsStatus) {
            if (!failureStatusIsSuperseded()) {
                return;
            }
            clearFailureStatusOwnership();
        }
        if (acceptedRequestOwnsStatus) {
            if (snapshot != null && snapshot.isPending()
                && snapshot.transactionGeneration()
                    == acceptedTransactionGeneration) {
                return;
            }
            clearAcceptedRequestStatusOwnership();
        }
        if (snapshot == null) {
            setStatus("Navigation information is unavailable.",
                      true);
        } else if (snapshot.isPending()) {
            setStatus(
                "Opening destination. Your current place remains saved "
                    + "until it appears.",
                false);
        } else if (!snapshot.isReady()) {
            setStatus("Navigation is not ready for this book.", true);
        } else if (snapshot.isFallback()) {
            setStatus(
                "This book has no usable contents document. Reading "
                    + "sections are shown instead.",
                false);
        } else if (snapshot.isTruncated()) {
            setStatus("Showing the first " + snapshot.rowCount() + " of "
                          + snapshot.totalCount() + " destinations.",
                      false);
        } else if (snapshot.currentRow() >= 0) {
            setStatus("Current section: "
                          + snapshot.row(snapshot.currentRow()).label(),
                      false);
        } else {
            setStatus("Navigation is ready.", false);
        }
    }

    private void showSection(boolean showContents) {
        contentsScroll.setVisibility(showContents ? VISIBLE : GONE);
        goToScroll.setVisibility(showContents ? GONE : VISIBLE);
        if (showContents && !contentsTab.isChecked()) {
            contentsTab.setChecked(true);
        } else if (!showContents && !goToTab.isChecked()) {
            goToTab.setChecked(true);
        }
        refreshFocusOrder();
    }

    private GoToControl addGoToControl(String label,
                                       String accessibilityLabel,
                                       int inputId,
                                       int buttonId,
                                       Runnable submit) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(VERTICAL);
        TextView heading = textView(label,
                                    Ui0AndroidThemeSnapshot
                                        .TypographyRole.BODY,
                                    Typeface.BOLD,
                                    primaryText);
        markHeading(heading);
        section.addView(heading, matchWrap());

        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLayout = matchWrap();
        controlsLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.TEXT_STACK_GAP);
        section.addView(controls, controlsLayout);

        EditText input = new EditText(getContext());
        input.setId(inputId);
        input.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BODY));
        input.setSingleLine(true);
        input.setMinHeight(controlHeightPx());
        input.setPadding(textInputPaddingXPx(),
                         textInputPaddingYPx(),
                         textInputPaddingXPx(),
                         textInputPaddingYPx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setHint(label);
        input.setContentDescription(accessibilityLabel);
        input.setSelectAllOnFocus(false);
        input.setDefaultFocusHighlightEnabled(false);
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                submit.run();
                return true;
            }
            return false;
        });
        inputs.add(input);
        controls.addView(input,
                         new LinearLayout.LayoutParams(
                             0,
                             ViewGroup.LayoutParams.WRAP_CONTENT,
                             1.0f));

        Button go = button(getContext().getString(
                               R.string.navigation_open_destination),
                           "Go to " + label.toLowerCase());
        go.setId(buttonId);
        go.setOnClickListener(view -> submit.run());
        actionButtons.add(go);
        LinearLayout.LayoutParams goLayout = wrapWrap();
        goLayout.setMarginStart(ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        controls.addView(go, goLayout);

        LinearLayout.LayoutParams sectionLayout = matchWrap();
        sectionLayout.topMargin = ui0Adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        goToContent.addView(section, sectionLayout);
        return new GoToControl(section, input, go);
    }

    private void submitChapter() {
        if (!navigationInteractive()) {
            return;
        }
        Long value = positiveValue(chapterControl.input, "Chapter", 1,
                                   Integer.MAX_VALUE);
        if (value != null) {
            listener.onChapter(value.intValue());
        }
    }

    private void submitLocation() {
        if (!navigationInteractive()) {
            return;
        }
        long maximum = snapshot != null && snapshot.isLocationReady()
            ? snapshot.locationCount() : Long.MAX_VALUE;
        Long value = positiveValue(locationControl.input,
                                   "Location",
                                   1,
                                   maximum);
        if (value != null) {
            listener.onLocation(value);
        }
    }

    private void submitPage() {
        if (!navigationInteractive()) {
            return;
        }
        long maximum = snapshot != null && snapshot.isPageMeaningful()
            ? snapshot.pageCount() : Long.MAX_VALUE;
        Long value = positiveValue(pageControl.input,
                                   "Page",
                                   1,
                                   maximum);
        if (value != null) {
            listener.onPage(value);
        }
    }

    private void submitPercentage() {
        if (!navigationInteractive()) {
            return;
        }
        Long value = positiveValue(percentageControl.input,
                                   "Percentage",
                                   0,
                                   100);
        if (value != null) {
            listener.onPercentage(value.intValue());
        }
    }

    private Long positiveValue(EditText input,
                               String label,
                               long minimum,
                               long maximum) {
        String text = input.getText() == null
            ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            showError(label + " is required.");
            input.requestFocus();
            return null;
        }
        try {
            long value = Long.parseLong(text);
            if (value < minimum || value > maximum) {
                showError(label + " must be between " + minimum + " and "
                              + maximum + ".");
                input.requestFocus();
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            showError(label + " is not a valid whole number.");
            input.requestFocus();
            return null;
        }
    }

    private String visibleRowCaption(OctavoNavigation.Row row) {
        StringBuilder text = new StringBuilder();
        String detail = rowProgress(row);
        if (row.isCurrent()) {
            text.append("\nCurrent section");
            if (!detail.isEmpty()) {
                text.append(" | ").append(detail);
            }
        } else if (!detail.isEmpty()) {
            text.append('\n').append(detail);
        }
        if (text.length() > 0 && Character.isWhitespace(text.charAt(0))) {
            text.deleteCharAt(0);
        }
        return text.toString();
    }

    private String rowDescription(OctavoNavigation.Row row, boolean parent) {
        StringBuilder description = new StringBuilder();
        description.append("Level ").append(row.depth() + 1).append(", ");
        if (row.isCurrent()) {
            description.append("current section, ");
        }
        description.append(row.label());
        String progress = rowProgress(row);
        if (!progress.isEmpty()) {
            description.append(", ").append(progress);
        }
        if (parent) {
            description.append(", heading");
        }
        if (row.isSynthetic()) {
            description.append(", generated section");
        }
        if (!row.isDestinationValid()) {
            description.append(", destination unavailable");
        }
        return description.toString();
    }

    private String rowProgress(OctavoNavigation.Row row) {
        if (!row.isProgressAvailable()) {
            return "";
        }
        StringBuilder progress = new StringBuilder();
        progress.append("Location ").append(row.locationIndex())
            .append(" of ").append(row.locationCount())
            .append(" | ").append(row.percent()).append(" percent");
        if (row.pageCount() > 0) {
            progress.append(" | Page ").append(row.pageIndex())
                .append(" of ").append(row.pageCount());
        }
        return progress.toString();
    }

    private void setStatus(String message, boolean error) {
        String normalized = message.trim();
        if (normalized.length() > MAX_STATUS_CHARS) {
            normalized = normalized.substring(0, MAX_STATUS_CHARS);
        }
        if (statusIsError == error
            && normalized.contentEquals(status.getText())) {
            return;
        }
        statusIsError = error;
        status.setText(normalized);
        ++statusRevision;
        status.setTextColor(error
            ? ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.DANGER)
            : ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
    }

    private void refreshFocusOrder() {
        List<View> order = new ArrayList<>();
        order.add(dismissButton);
        order.add(contentsTab);
        order.add(goToTab);
        if (returnButton.isEnabled()) {
            order.add(returnButton);
        }
        if (forwardButton.isEnabled()) {
            order.add(forwardButton);
        }
        if (contentsScroll.getVisibility() == VISIBLE) {
            for (RowBinding binding : rowBindings) {
                if (binding.button.isEnabled()) {
                    order.add(binding.button);
                }
            }
        } else {
            for (GoToControl control : new GoToControl[] {
                    chapterControl,
                    locationControl,
                    pageControl,
                    percentageControl}) {
                if (control.input.isEnabled()) {
                    order.add(control.input);
                    order.add(control.go);
                }
            }
            for (RadioButton button : progressButtons) {
                if (button.isEnabled()) {
                    order.add(button);
                }
            }
        }
        for (int index = 0; index < order.size(); ++index) {
            View view = order.get(index);
            View previous = order.get((index + order.size() - 1)
                                      % order.size());
            View next = order.get((index + 1) % order.size());
            view.setNextFocusUpId(previous.getId());
            view.setNextFocusDownId(next.getId());
            view.setNextFocusForwardId(next.getId());
        }
    }

    private boolean failureStatusIsSuperseded() {
        return snapshot != null
            && snapshot.isPending()
            && (failureTransactionGeneration < 0
                || snapshot.transactionGeneration()
                    > failureTransactionGeneration);
    }

    private void clearFailureStatusOwnership() {
        failureOwnsStatus = false;
        failureTransactionGeneration = -1;
    }

    private void clearAcceptedRequestStatusOwnership() {
        acceptedRequestOwnsStatus = false;
        acceptedTransactionGeneration = -1;
    }

    private FocusedRow focusedRow() {
        for (RowBinding binding : rowBindings) {
            if (binding.button.hasFocus()
                || binding.button.isAccessibilityFocused()) {
                return new FocusedRow(binding.row.navIndex(),
                                      binding.button.hasFocus(),
                                      binding.button
                                          .isAccessibilityFocused());
            }
        }
        return null;
    }

    private void reconcileRowFocus(FocusedRow focused) {
        if (focused != null) {
            deferredRowFocus = focused;
        }
        if (deferredRowFocus == null) {
            return;
        }
        RowBinding target = null;
        for (RowBinding binding : rowBindings) {
            if (binding.row.navIndex() == deferredRowFocus.navIndex) {
                target = binding;
                break;
            }
        }
        if (target == null || hasOtherControlFocus(target.button)) {
            deferredRowFocus = null;
            return;
        }
        if (!target.button.isEnabled()) {
            return;
        }
        if (deferredRowFocus.keyboard) {
            target.button.requestFocus();
        }
        if (deferredRowFocus.accessibility) {
            target.button.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
        }
        deferredRowFocus = null;
    }

    private boolean hasOtherControlFocus(View target) {
        View keyboard = findFocus();
        return keyboard != null && keyboard != this && keyboard != target
            || hasOtherAccessibilityFocus(this, target);
    }

    private static boolean hasOtherAccessibilityFocus(View view,
                                                       View target) {
        if (view != target && view.isAccessibilityFocused()) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup)view;
        for (int index = 0; index < group.getChildCount(); ++index) {
            if (hasOtherAccessibilityFocus(group.getChildAt(index), target)) {
                return true;
            }
        }
        return false;
    }

    View preferredInitialFocus() {
        if (snapshot != null && snapshot.currentRow() >= 0) {
            int currentNavIndex = snapshot.row(snapshot.currentRow())
                .navIndex();
            for (RowBinding binding : rowBindings) {
                if (binding.row.navIndex() == currentNavIndex
                    && binding.button.isEnabled()) {
                    return binding.button;
                }
            }
        }
        return dismissButton;
    }

    private TextView textView(String value,
                              Ui0AndroidThemeSnapshot.TypographyRole role,
                              int style,
                              List<TextView> themeGroup) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(ui0Adapter.textSizeSp(role));
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0.0f, 1.12f);
        themeGroup.add(view);
        semanticTextRoles.put(view, role);
        return view;
    }

    private Button button(String value, String description) {
        Button button = new Button(getContext());
        button.setId(View.generateViewId());
        button.setText(value);
        configureButton(button, description);
        return button;
    }

    private void configureButton(Button button, String description) {
        applyButtonGeometry(button);
        button.setContentDescription(description);
        button.setDefaultFocusHighlightEnabled(false);
    }

    private void applyButtonGeometry(Button button) {
        button.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
        button.setAllCaps(false);
        button.setMinWidth(controlHeightPx());
        button.setMinHeight(controlHeightPx());
        button.setPadding(controlPaddingXPx(),
                          controlPaddingYPx(),
                          controlPaddingXPx(),
                          controlPaddingYPx());
        button.setGravity(Gravity.CENTER);
    }

    private RadioButton tab(String value, String description) {
        RadioButton tab = new RadioButton(getContext());
        tab.setText(value);
        applyRadioGeometry(tab);
        tab.setContentDescription(description);
        tab.setDefaultFocusHighlightEnabled(false);
        return tab;
    }

    private void applyRadioGeometry(RadioButton tab) {
        tab.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
        tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(controlHeightPx());
        tab.setPadding(controlPaddingXPx(),
                       controlPaddingYPx(),
                       controlPaddingXPx(),
                       controlPaddingYPx());
    }

    private void applyRowStyle(RowBinding binding) {
        binding.button.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BODY));
        binding.button.setHierarchyText(
            binding.row.label(),
            visibleRowCaption(binding.row),
            binding.parent,
            ui0Adapter.relativeTextScale(
                Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                Ui0AndroidThemeSnapshot.TypographyRole.BODY),
            ui0Adapter.hierarchyTextColors(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY),
            ui0Adapter.hierarchyTextColors(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        binding.button.setSelected(binding.row.isCurrent());
        binding.button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        binding.button.setMinHeight(rowHeightPx());
        int depth = Math.min(binding.row.depth(), MAX_VISUAL_DEPTH);
        binding.button.setPaddingRelative(
                                          ui0Adapter
                                              .hierarchyTextStartPx(depth),
                                          controlPaddingYPx(),
                                          ui0Adapter.treePx(
                                              Ui0AndroidThemeSnapshot
                                                  .TreeMetric.PADDING_X),
                                          controlPaddingYPx());
        int railWidth = ui0Adapter.treePx(
            Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_WIDTH);
        binding.button.setCurrentRail(
            binding.row.isCurrent(),
            ui0Adapter.currentIndicatorColor(),
            railWidth,
            ui0Adapter.currentRailInsetPx(),
            ui0Adapter.currentRailRadiusPx());
        binding.button.setBackground(ui0Adapter.rowBackground(
            binding.row.isCurrent()));
    }

    private void markHeading(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) {
            view.setAccessibilityHeading(true);
        }
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weightedWrap() {
        return new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
    }

    private static RadioGroup.LayoutParams matchWrapRadio() {
        return new RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static FrameLayout.LayoutParams matchMatchFrame() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    OctavoNavigation snapshotForTesting() {
        return snapshot;
    }

    Button dismissButtonForTesting() {
        return dismissButton;
    }

    RadioButton contentsTabForTesting() {
        return contentsTab;
    }

    RadioButton goToTabForTesting() {
        return goToTab;
    }

    LinearLayout contentsListForTesting() {
        return contentsList;
    }

    TextView statusForTesting() {
        return status;
    }

    FrameLayout bodyForTesting() {
        return body;
    }

    ScrollView goToScrollForTesting() {
        return goToScroll;
    }

    int statusRevisionForTesting() {
        return statusRevision;
    }

    int inputDisabledTextColorForTesting() {
        return ui0Adapter.inputTextColors().getColorForState(
            new int[] {-android.R.attr.state_enabled},
            Color.TRANSPARENT);
    }

    int hierarchyLabelColorForTesting() {
        return ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);
    }

    int hierarchyCaptionColorForTesting() {
        return ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY);
    }

    int inputSelectionColorForTesting() {
        return ui0Adapter.textSelectionColor();
    }

    int inputCaretColorForTesting() {
        return ui0Adapter.textCaretColor();
    }

    EditText chapterInputForTesting() {
        return chapterControl.input;
    }

    EditText locationInputForTesting() {
        return locationControl.input;
    }

    EditText pageInputForTesting() {
        return pageControl.input;
    }

    EditText percentageInputForTesting() {
        return percentageControl.input;
    }

    Button returnButtonForTesting() {
        return returnButton;
    }

    Button forwardButtonForTesting() {
        return forwardButton;
    }

    RadioGroup progressOptionsForTesting() {
        return progressOptions;
    }

    OctavoProgressDisplay presentedProgressForTesting() {
        return presentedProgress;
    }

    int rowFocusedFillForTesting(boolean current) {
        return ui0Adapter.focusedRowFill(current);
    }

    int rowSelectedFillForTesting() {
        return ui0Adapter.selectedRowFill();
    }

    float currentRailRadiusForTesting() {
        return ui0Adapter.currentRailRadiusPx();
    }

    int treeIndentPxForTesting() {
        return ui0Adapter.treePx(
            Ui0AndroidThemeSnapshot.TreeMetric.INDENT_WIDTH);
    }

    int hierarchyBaseStartPxForTesting() {
        return ui0Adapter.hierarchyTextStartPx(0);
    }

    int hierarchyTextStartPxForTesting(int depth) {
        return ui0Adapter.hierarchyTextStartPx(depth);
    }

    int semanticTextSizeSpForTesting(
        Ui0AndroidThemeSnapshot.TypographyRole role) {
        return ui0Adapter.textSizeSp(role);
    }

    private static final class GoToControl {
        final LinearLayout root;
        final EditText input;
        final Button go;

        GoToControl(LinearLayout root, EditText input, Button go) {
            this.root = root;
            this.input = input;
            this.go = go;
        }

        void setEnabled(boolean enabled) {
            root.setEnabled(enabled);
            input.setEnabled(enabled);
            go.setEnabled(enabled);
        }
    }

    private static final class RowBinding {
        final Ui0NavigationRow button;
        OctavoNavigation.Row row;
        boolean parent;
        final int rowIndex;

        RowBinding(Ui0NavigationRow button,
                   OctavoNavigation.Row row,
                   boolean parent,
                   int rowIndex) {
            this.button = button;
            this.row = row;
            this.parent = parent;
            this.rowIndex = rowIndex;
        }
    }

    private static Ui0AndroidThemeSnapshot resolveTheme(
        OctavoAppearance appearance) {
        OctavoDesignTokens product =
            OctavoDesignTokens.forAppearance(appearance);
        Ui0AndroidThemeSnapshot resolved = Ui0AndroidThemeSnapshot.parse(
            OctavoNative.ui0AndroidThemeSnapshot(
                product.darkAppearance,
                product.nativeUi0Colors()));
        if (resolved == null) {
            throw new IllegalStateException(
                "UI0 Android theme snapshot is unavailable or incompatible");
        }
        return resolved;
    }

    int overlayColor() {
        return ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.OVERLAY);
    }

    private int controlHeightPx() {
        return ui0Adapter.densityPx(
            Ui0AndroidThemeSnapshot.DensityRole.CONTROL_HEIGHT);
    }

    private int rowHeightPx() {
        return ui0Adapter.rowHeightPx();
    }

    private int controlPaddingXPx() {
        return ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X);
    }

    private int controlPaddingYPx() {
        return ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_Y);
    }

    private int textInputPaddingXPx() {
        return ui0Adapter.textInputPx(
            Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_X);
    }

    private int textInputPaddingYPx() {
        return ui0Adapter.textInputPx(
            Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y);
    }

    private static final class FocusedRow {
        final int navIndex;
        final boolean keyboard;
        final boolean accessibility;

        FocusedRow(int navIndex,
                   boolean keyboard,
                   boolean accessibility) {
            this.navIndex = navIndex;
            this.keyboard = keyboard;
            this.accessibility = accessibility;
        }
    }
}
