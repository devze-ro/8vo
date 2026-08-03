package ro.devze.octavo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.InputType;
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
import java.util.List;

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

    private static final int TITLE_TEXT_SP = 26;
    private static final int SECTION_TEXT_SP = 18;
    private static final int BODY_TEXT_SP = 16;
    private static final int NOTE_TEXT_SP = 14;
    private static final int MAX_VISUAL_DEPTH = 4;
    private static final int INDENT_DP = 12;
    private static final int MAX_STATUS_CHARS = 320;

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
    private TextView contentsEmptyState;

    private OctavoNavigation snapshot;
    private OctavoAppearance appearance;
    private OctavoProgressDisplay presentedProgress;
    private boolean synchronizing;
    private boolean statusIsError;

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
        presentedProgress = initialProgress == null
            ? OctavoProgressDisplay.defaults() : initialProgress;

        setId(R.id.octavo_navigation_panel);
        setOrientation(VERTICAL);
        setPadding(dp(OctavoDesignTokens.SPACE_XL_DP),
                   dp(OctavoDesignTokens.SPACE_XL_DP),
                   dp(OctavoDesignTokens.SPACE_XL_DP),
                   dp(OctavoDesignTokens.SPACE_XXL_DP));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (Build.VERSION.SDK_INT >= 28) {
            setAccessibilityPaneTitle(
                context.getString(R.string.reader_navigation));
        }

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, matchWrap());

        title = textView(context.getString(R.string.reader_navigation),
                         TITLE_TEXT_SP,
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
        tabsLayout.topMargin = dp(OctavoDesignTokens.SPACE_LG_DP);
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
        historyLayout.topMargin = dp(OctavoDesignTokens.SPACE_MD_DP);
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
        forwardLayout.leftMargin = dp(OctavoDesignTokens.SPACE_SM_DP);
        historyControls.addView(forwardButton, forwardLayout);

        status = textView("Preparing navigation.",
                          NOTE_TEXT_SP,
                          Typeface.NORMAL,
                          secondaryText);
        status.setId(R.id.octavo_navigation_status);
        status.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams statusLayout = matchWrap();
        statusLayout.topMargin = dp(OctavoDesignTokens.SPACE_SM_DP);
        statusLayout.bottomMargin = dp(OctavoDesignTokens.SPACE_SM_DP);
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
                                        SECTION_TEXT_SP,
                                        Typeface.BOLD,
                                        primaryText);
        markHeading(goToHeading);
        goToContent.addView(goToHeading, matchWrap());
        TextView goToNote = textView(
            "Choose an exact structural or canonical destination. Your "
                + "saved place changes only after the destination is shown.",
            NOTE_TEXT_SP,
            Typeface.NORMAL,
            secondaryText);
        LinearLayout.LayoutParams goToNoteLayout = matchWrap();
        goToNoteLayout.topMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
        goToNoteLayout.bottomMargin = dp(OctavoDesignTokens.SPACE_MD_DP);
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
            SECTION_TEXT_SP,
            Typeface.BOLD,
            primaryText);
        markHeading(progressHeading);
        LinearLayout.LayoutParams progressHeadingLayout = matchWrap();
        progressHeadingLayout.topMargin =
            dp(OctavoDesignTokens.SPACE_XL_DP);
        goToContent.addView(progressHeading, progressHeadingLayout);

        TextView progressNote = textView(
            "Choose the compact progress detail shown in the reader. The "
                + "choice becomes current after a matching page is shown.",
            NOTE_TEXT_SP,
            Typeface.NORMAL,
            secondaryText);
        LinearLayout.LayoutParams progressNoteLayout = matchWrap();
        progressNoteLayout.topMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
        progressNoteLayout.bottomMargin = dp(OctavoDesignTokens.SPACE_SM_DP);
        goToContent.addView(progressNote, progressNoteLayout);

        progressOptions = new RadioGroup(context);
        progressOptions.setId(R.id.octavo_navigation_progress_options);
        progressOptions.setOrientation(VERTICAL);
        progressOptions.setContentDescription("Reader progress choices");
        progressOptions.setFocusable(false);
        for (OctavoProgressDisplay display : OctavoProgressDisplay.values()) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setTag(display);
            option.setText(display.label());
            option.setTextSize(BODY_TEXT_SP);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            option.setPadding(dp(OctavoDesignTokens.SPACE_MD_DP),
                              dp(OctavoDesignTokens.SPACE_SM_DP),
                              dp(OctavoDesignTokens.SPACE_MD_DP),
                              dp(OctavoDesignTokens.SPACE_SM_DP));
            option.setContentDescription(
                display.label() + " reader progress display");
            option.setDefaultFocusHighlightEnabled(false);
            progressOptions.addView(option, matchWrapRadio());
            progressButtons.add(option);
        }
        progressOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (synchronizing || checkedId == RadioGroup.NO_ID) {
                return;
            }
            if (!navigationInteractive()) {
                updateProgressDisplay(presentedProgress);
                return;
            }
            RadioButton checked = group.findViewById(checkedId);
            if (checked != null
                && checked.getTag() instanceof OctavoProgressDisplay) {
                listener.onProgressDisplayRequested(
                    (OctavoProgressDisplay)checked.getTag());
                showStatus(
                    "Progress display will change after the updated page "
                        + "is shown.");
            }
        });
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

    void updateSnapshot(OctavoNavigation navigation) {
        snapshot = navigation;
        rebuildContents();
        updateAvailability();
        updateSnapshotStatus();
        refreshFocusOrder();
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
        appearance = newAppearance;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(newAppearance);
        setBackgroundColor(tokens.sheetSurface);
        header.setBackgroundColor(tokens.sheetSurface);
        body.setBackgroundColor(tokens.sheetSurface);
        contentsScroll.setBackgroundColor(tokens.sheetSurface);
        contentsList.setBackgroundColor(tokens.sheetSurface);
        goToScroll.setBackgroundColor(tokens.sheetSurface);
        goToContent.setBackgroundColor(tokens.sheetSurface);
        for (TextView text : primaryText) {
            text.setTextColor(tokens.textPrimary);
            text.setHighlightColor(tokens.selection);
        }
        for (TextView text : secondaryText) {
            text.setTextColor(tokens.textSecondary);
            text.setHighlightColor(tokens.selection);
        }
        status.setTextColor(statusIsError ? tokens.error
                                          : tokens.textSecondary);
        dismissButton.setTextColor(tokens.onAccent);
        dismissButton.setBackground(actionBackground(tokens));
        for (Button button : neutralButtons) {
            button.setTextColor(neutralTextColors(tokens));
            button.setBackground(neutralBackground(tokens));
        }
        for (Button button : actionButtons) {
            if (button == dismissButton) {
                continue;
            }
            button.setTextColor(actionTextColors(tokens));
            button.setBackground(actionBackground(tokens));
        }
        ColorStateList radioText = radioTextColors(tokens);
        ColorStateList radioTint = radioTint(tokens);
        for (RadioButton tab : new RadioButton[] {contentsTab, goToTab}) {
            tab.setTextColor(radioText);
            tab.setButtonTintList(radioTint);
            tab.setBackground(optionBackground(tokens));
        }
        for (RadioButton option : progressButtons) {
            option.setTextColor(radioText);
            option.setButtonTintList(radioTint);
            option.setBackground(optionBackground(tokens));
        }
        for (EditText input : inputs) {
            input.setTextColor(tokens.textPrimary);
            input.setHintTextColor(tokens.textMuted);
            input.setBackground(inputBackground(tokens));
        }
        for (RowBinding binding : rowBindings) {
            applyRowStyle(binding, tokens);
        }
        invalidate();
    }

    void showError(String message) {
        setStatus(message == null || message.trim().isEmpty()
                      ? "Navigation could not complete that request."
                      : message,
                  true,
                  true);
    }

    void showStatus(String message) {
        setStatus(message == null || message.trim().isEmpty()
                      ? "Navigation is ready."
                      : message,
                  false,
                  true);
    }

    private void rebuildContents() {
        if (contentsEmptyState != null) {
            secondaryText.remove(contentsEmptyState);
            contentsEmptyState = null;
        }
        contentsList.removeAllViews();
        rowBindings.clear();
        if (snapshot == null || snapshot.rowCount() == 0) {
            TextView empty = textView(
                snapshot == null
                    ? "No navigation information is available yet."
                    : "This book has no usable structural destinations.",
                BODY_TEXT_SP,
                Typeface.NORMAL,
                secondaryText);
            empty.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            contentsEmptyState = empty;
            empty.setGravity(Gravity.CENTER_VERTICAL);
            contentsList.addView(empty, matchWrap());
            return;
        }

        for (int index = 0; index < snapshot.rowCount(); ++index) {
            OctavoNavigation.Row row = snapshot.row(index);
            boolean parent = index + 1 < snapshot.rowCount()
                && snapshot.row(index + 1).depth() > row.depth();
            Button destination = button(visibleRowText(row),
                                        rowDescription(row, parent));
            destination.setTag(row.navIndex());
            destination.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            destination.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
            destination.setSingleLine(false);
            destination.setMaxLines(3);
            destination.setAllCaps(false);
            destination.setOnClickListener(
                view -> {
                    if (navigationInteractive()
                        && row.isDestinationValid()) {
                        listener.onContentsJump((Integer)view.getTag());
                    }
                });
            if (parent) {
                markHeading(destination);
            }
            final int rowIndex = index;
            destination.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                        View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setCollectionItemInfo(
                            AccessibilityNodeInfo.CollectionItemInfo.obtain(
                                rowIndex,
                                1,
                                0,
                                1,
                                parent,
                                row.isCurrent()));
                    }
                });
            RowBinding binding = new RowBinding(destination, row, parent);
            rowBindings.add(binding);
            LinearLayout.LayoutParams rowLayout = matchWrap();
            rowLayout.leftMargin = dp(Math.min(row.depth(), MAX_VISUAL_DEPTH)
                                      * INDENT_DP);
            rowLayout.bottomMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
            contentsList.addView(destination, rowLayout);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        for (RowBinding binding : rowBindings) {
            applyRowStyle(binding, tokens);
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
        if (snapshot == null) {
            setStatus("Navigation information is unavailable.",
                      true,
                      false);
        } else if (snapshot.isPending()) {
            setStatus(
                "Opening destination. Your current place remains saved "
                    + "until it appears.",
                false,
                false);
        } else if (!snapshot.isReady()) {
            setStatus("Navigation is not ready for this book.", true, false);
        } else if (snapshot.isFallback()) {
            setStatus(
                "This book has no usable contents document. Reading "
                    + "sections are shown instead.",
                false,
                false);
        } else if (snapshot.isTruncated()) {
            setStatus("Showing the first " + snapshot.rowCount() + " of "
                          + snapshot.totalCount() + " destinations.",
                      false,
                      false);
        } else if (snapshot.currentRow() >= 0) {
            setStatus("Current section: "
                          + snapshot.row(snapshot.currentRow()).label(),
                      false,
                      false);
        } else {
            setStatus("Navigation is ready.", false, false);
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
                                    BODY_TEXT_SP,
                                    Typeface.BOLD,
                                    primaryText);
        markHeading(heading);
        section.addView(heading, matchWrap());

        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLayout = matchWrap();
        controlsLayout.topMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
        section.addView(controls, controlsLayout);

        EditText input = new EditText(getContext());
        input.setId(inputId);
        input.setTextSize(BODY_TEXT_SP);
        input.setSingleLine(true);
        input.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        input.setPadding(dp(OctavoDesignTokens.SPACE_MD_DP),
                         dp(OctavoDesignTokens.SPACE_SM_DP),
                         dp(OctavoDesignTokens.SPACE_MD_DP),
                         dp(OctavoDesignTokens.SPACE_SM_DP));
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
        goLayout.leftMargin = dp(OctavoDesignTokens.SPACE_SM_DP);
        controls.addView(go, goLayout);

        LinearLayout.LayoutParams sectionLayout = matchWrap();
        sectionLayout.topMargin = dp(OctavoDesignTokens.SPACE_LG_DP);
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
            showStatus("Opening chapter " + value + ".");
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
            showStatus("Opening location " + value + ".");
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
            showStatus("Opening page " + value + ".");
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
            showStatus("Opening " + value + " percent.");
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

    private String visibleRowText(OctavoNavigation.Row row) {
        StringBuilder text = new StringBuilder(row.label());
        String detail = rowProgress(row);
        if (row.isCurrent()) {
            text.append("\nCurrent section");
            if (!detail.isEmpty()) {
                text.append(" | ").append(detail);
            }
        } else if (!detail.isEmpty()) {
            text.append('\n').append(detail);
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

    private void setStatus(String message,
                           boolean error,
                           boolean announce) {
        String normalized = message.trim();
        if (normalized.length() > MAX_STATUS_CHARS) {
            normalized = normalized.substring(0, MAX_STATUS_CHARS);
        }
        statusIsError = error;
        status.setText(normalized);
        status.setContentDescription((error ? "Navigation error. " : "")
                                     + normalized);
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        status.setTextColor(error ? tokens.error : tokens.textSecondary);
        if (announce && isAttachedToWindow()) {
            status.announceForAccessibility(status.getContentDescription());
        }
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

    private TextView textView(String value,
                              int textSizeSp,
                              int style,
                              List<TextView> themeGroup) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(textSizeSp);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0.0f, 1.12f);
        themeGroup.add(view);
        return view;
    }

    private Button button(String value, String description) {
        Button button = new Button(getContext());
        button.setId(View.generateViewId());
        button.setText(value);
        button.setTextSize(BODY_TEXT_SP);
        button.setAllCaps(false);
        button.setMinWidth(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        button.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        button.setPadding(dp(OctavoDesignTokens.SPACE_MD_DP),
                          dp(OctavoDesignTokens.SPACE_SM_DP),
                          dp(OctavoDesignTokens.SPACE_MD_DP),
                          dp(OctavoDesignTokens.SPACE_SM_DP));
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setDefaultFocusHighlightEnabled(false);
        return button;
    }

    private RadioButton tab(String value, String description) {
        RadioButton tab = new RadioButton(getContext());
        tab.setText(value);
        tab.setTextSize(BODY_TEXT_SP);
        tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        tab.setPadding(dp(OctavoDesignTokens.SPACE_MD_DP),
                       dp(OctavoDesignTokens.SPACE_SM_DP),
                       dp(OctavoDesignTokens.SPACE_MD_DP),
                       dp(OctavoDesignTokens.SPACE_SM_DP));
        tab.setContentDescription(description);
        tab.setDefaultFocusHighlightEnabled(false);
        return tab;
    }

    private void applyRowStyle(RowBinding binding,
                               OctavoDesignTokens tokens) {
        binding.button.setTextColor(
            binding.row.isCurrent()
                ? tokens.textPrimary : tokens.textSecondary);
        binding.button.setBackground(
            rowBackground(tokens, binding.row.isCurrent()));
    }

    private StateListDrawable rowBackground(OctavoDesignTokens tokens,
                                            boolean current) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        rounded(tokens.sheetSurface,
                                tokens.dividerMuted,
                                1));
        result.addState(new int[] {android.R.attr.state_pressed},
                        rounded(tokens.buttonSurface,
                                tokens.accentPressed,
                                1));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(current ? tokens.selection
                                        : tokens.buttonSurface,
                                tokens.focus,
                                2));
        result.addState(new int[] {},
                        rounded(current ? tokens.selection
                                        : tokens.sheetSurface,
                                current ? tokens.accent
                                        : tokens.dividerMuted,
                                1));
        return result;
    }

    private StateListDrawable neutralBackground(OctavoDesignTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        rounded(tokens.sheetSurface,
                                tokens.dividerMuted,
                                1));
        result.addState(new int[] {android.R.attr.state_pressed},
                        rounded(tokens.buttonSurface,
                                tokens.accentPressed,
                                1));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(tokens.buttonSurface, tokens.focus, 2));
        result.addState(new int[] {},
                        rounded(tokens.buttonSurface,
                                tokens.divider,
                                1));
        return result;
    }

    private StateListDrawable actionBackground(OctavoDesignTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        rounded(tokens.dividerMuted,
                                tokens.dividerMuted,
                                1));
        result.addState(new int[] {android.R.attr.state_pressed},
                        rounded(tokens.accentPressed,
                                tokens.accentPressed,
                                1));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(tokens.accent, tokens.focus, 2));
        result.addState(new int[] {},
                        rounded(tokens.accent, tokens.accent, 1));
        return result;
    }

    private StateListDrawable optionBackground(OctavoDesignTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {
            android.R.attr.state_checked,
            android.R.attr.state_focused
        }, rounded(tokens.selection, tokens.focus, 2));
        result.addState(new int[] {android.R.attr.state_checked},
                        rounded(tokens.selection, tokens.accent, 1));
        result.addState(new int[] {android.R.attr.state_pressed},
                        rounded(tokens.buttonSurface,
                                tokens.accentPressed,
                                1));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(tokens.buttonSurface, tokens.focus, 2));
        result.addState(new int[] {},
                        rounded(tokens.sheetSurface,
                                tokens.dividerMuted,
                                1));
        return result;
    }

    private StateListDrawable inputBackground(OctavoDesignTokens tokens) {
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        rounded(tokens.sheetSurface,
                                tokens.dividerMuted,
                                1));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(tokens.inputSurface, tokens.focus, 2));
        result.addState(new int[] {},
                        rounded(tokens.inputSurface, tokens.divider, 1));
        return result;
    }

    private GradientDrawable rounded(int fill,
                                     int stroke,
                                     int strokeWidthDp) {
        GradientDrawable result = new GradientDrawable();
        result.setShape(GradientDrawable.RECTANGLE);
        result.setColor(fill);
        result.setCornerRadius(dp(OctavoDesignTokens.RADIUS_MEDIUM_DP));
        result.setStroke(dp(strokeWidthDp), stroke);
        return result;
    }

    private static ColorStateList neutralTextColors(
        OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {}
            },
            new int[] {tokens.textMuted, tokens.textPrimary});
    }

    private static ColorStateList actionTextColors(
        OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {}
            },
            new int[] {tokens.textMuted, tokens.onAccent});
    }

    private static ColorStateList radioTextColors(
        OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                tokens.textMuted,
                tokens.textPrimary,
                tokens.textSecondary
            });
    }

    private static ColorStateList radioTint(OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                tokens.divider,
                tokens.accent,
                tokens.textMuted
            });
    }

    private void markHeading(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) {
            view.setAccessibilityHeading(true);
        }
    }

    private int dp(int value) {
        return Math.max(1,
                        Math.round(value
                                   * getResources()
                                       .getDisplayMetrics().density));
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
        final Button button;
        final OctavoNavigation.Row row;
        final boolean parent;

        RowBinding(Button button,
                   OctavoNavigation.Row row,
                   boolean parent) {
            this.button = button;
            this.row = row;
            this.parent = parent;
        }
    }
}
