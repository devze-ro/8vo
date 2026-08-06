package ro.devze.octavo;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputFilter;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Host-owned native side sheet for Reader0's bounded search projection. */
final class OctavoSearchPanel extends LinearLayout {
    interface Listener {
        void onDismiss();
        void onSubmit(String query);
        void onClear();
        void onStep(boolean forward);
        void onActivate(int resultIndex);
    }

    private static final int MAX_STATUS_CHARS = 320;

    private final Listener listener;
    private final LinearLayout header;
    private final TextView title;
    private final Button dismissButton;
    private final LinearLayout queryControls;
    private final EditText queryInput;
    private final Button submitButton;
    private final LinearLayout resultControls;
    private final Button previousButton;
    private final Button nextButton;
    private final Button clearButton;
    private final TextView status;
    private final ScrollView resultScroll;
    private final LinearLayout resultList;
    private final List<Ui0NavigationRow> resultRows = new ArrayList<>();
    private OctavoAppearance appearance;
    private Ui0AndroidThemeAdapter ui0Adapter;
    private OctavoSearch snapshot;
    private String submittedQuery;
    private boolean statusIsError;

    OctavoSearchPanel(Context context,
                      OctavoAppearance initialAppearance,
                      Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException("Search listener is required");
        }
        this.listener = listener;
        appearance = initialAppearance == null
            ? OctavoAppearance.defaults() : initialAppearance;
        ui0Adapter = resolveAdapter(
            appearance, getResources().getDisplayMetrics().density);

        setId(R.id.octavo_search_panel);
        setOrientation(VERTICAL);
        setFocusable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Build.VERSION.SDK_INT >= 28) {
            setAccessibilityPaneTitle("Find in book");
        }

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, matchWrap());

        title = text("Find in book",
                     Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
                     Typeface.BOLD);
        title.setId(R.id.octavo_search_title);
        title.setContentDescription("Find in book");
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        header.addView(title, weightedWrap());

        dismissButton = button("Done", "Close find in book");
        dismissButton.setId(R.id.octavo_search_done);
        dismissButton.setOnClickListener(view -> listener.onDismiss());
        header.addView(dismissButton, wrapWrap());

        queryControls = new LinearLayout(context);
        queryControls.setOrientation(HORIZONTAL);
        queryControls.setGravity(Gravity.CENTER_VERTICAL);
        queryControls.setId(R.id.octavo_search_query_controls);
        LinearLayout.LayoutParams queryLayout = matchWrap();
        queryLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        addView(queryControls, queryLayout);

        queryInput = new EditText(context);
        queryInput.setId(R.id.octavo_search_query);
        queryInput.setSingleLine(true);
        queryInput.setHint("Search this book");
        queryInput.setContentDescription("Search text");
        queryInput.setInputType(InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setFilters(new InputFilter[] {
            new InputFilter.LengthFilter(OctavoSearch.MAX_QUERY_UTF8_BYTES)
        });
        queryInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                || event != null && event.getAction() == KeyEvent.ACTION_UP
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                submit();
                return true;
            }
            return false;
        });
        queryControls.addView(queryInput, weightedWrap());

        submitButton = button("Search", "Search this book");
        submitButton.setId(R.id.octavo_search_submit);
        submitButton.setOnClickListener(view -> submit());
        LinearLayout.LayoutParams submitLayout = wrapWrap();
        submitLayout.setMarginStart(spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        queryControls.addView(submitButton, submitLayout);

        resultControls = new LinearLayout(context);
        resultControls.setOrientation(HORIZONTAL);
        resultControls.setGravity(Gravity.CENTER_VERTICAL);
        resultControls.setId(R.id.octavo_search_result_controls);
        LinearLayout.LayoutParams controlsLayout = matchWrap();
        controlsLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        addView(resultControls, controlsLayout);

        previousButton = button("Previous", "Previous search result");
        previousButton.setId(R.id.octavo_search_previous);
        previousButton.setOnClickListener(view -> listener.onStep(false));
        resultControls.addView(previousButton, weightedWrap());

        nextButton = button("Next", "Next search result");
        nextButton.setId(R.id.octavo_search_next);
        nextButton.setOnClickListener(view -> listener.onStep(true));
        LinearLayout.LayoutParams nextLayout = weightedWrap();
        nextLayout.setMarginStart(spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        resultControls.addView(nextButton, nextLayout);

        clearButton = button("Clear", "Clear search results");
        clearButton.setId(R.id.octavo_search_clear);
        clearButton.setOnClickListener(view -> listener.onClear());
        LinearLayout.LayoutParams clearLayout = weightedWrap();
        clearLayout.setMarginStart(spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        resultControls.addView(clearButton, clearLayout);

        status = text("Search this book.",
                      Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                      Typeface.NORMAL);
        status.setId(R.id.octavo_search_status);
        status.setMinHeight(controlHeight());
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
        statusLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        statusLayout.bottomMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        addView(status, statusLayout);

        resultScroll = new ScrollView(context);
        resultScroll.setId(R.id.octavo_search_results_scroll);
        resultScroll.setFillViewport(true);
        resultScroll.setClipToPadding(false);
        resultScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        resultScroll.setVerticalScrollBarEnabled(false);
        resultScroll.setContentDescription("Search results");
        resultList = new LinearLayout(context);
        resultList.setId(R.id.octavo_search_results);
        resultList.setOrientation(VERTICAL);
        resultScroll.addView(resultList,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(resultScroll,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        updateSnapshot(null);
        applyAppearance(appearance);
    }

    void updateSnapshot(OctavoSearch updated) {
        snapshot = updated;
        boolean submissionNotPublished = submittedQuery != null
            && (updated == null
                || !submittedQuery.equals(updated.query()));
        if (updated != null
            && !submissionNotPublished
            && (!queryInput.hasFocus() || updated.query().isEmpty())
            && !updated.query().contentEquals(queryInput.getText())) {
            queryInput.setText(updated.query());
            queryInput.setSelection(queryInput.length());
        }
        if (updated != null && submittedQuery != null
            && submittedQuery.equals(updated.query())
            && !updated.isPending()) {
            submittedQuery = null;
        }
        rebuildResults();
        updateAvailability();
        updateStatus();
    }

    void showPending() {
        setStatus("Searching this book.", false);
        setInteractive(false);
    }

    void showError(String message) {
        submittedQuery = null;
        setStatus(message == null || message.trim().isEmpty()
                      ? "Search could not complete that request."
                      : message,
                  true);
        updateAvailability();
    }

    void showAcceptedNavigation(String message) {
        setStatus(message, false);
        setInteractive(false);
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException("Search appearance is required");
        }
        appearance = updated;
        ui0Adapter = resolveAdapter(
            updated, getResources().getDisplayMetrics().density);
        int padding = ui0Adapter.densityPx(
            Ui0AndroidThemeSnapshot.DensityRole.PANEL_PADDING);
        setPadding(padding, padding, padding, padding);
        int sheet = ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.SIDEBAR_BACKGROUND);
        setBackground(ui0Adapter.panelBackground());
        header.setBackgroundColor(sheet);
        resultScroll.setBackgroundColor(sheet);
        resultList.setBackgroundColor(sheet);
        title.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE));
        title.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
        status.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        status.setTextColor(ui0Adapter.color(statusIsError
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        configureInput();
        for (Button control : new Button[] {
                dismissButton, submitButton, previousButton,
                nextButton, clearButton}) {
            configureButtonGeometry(control);
            boolean action = control == dismissButton
                || control == submitButton;
            control.setTextColor(action
                ? ui0Adapter.actionTextColors()
                : ui0Adapter.neutralTextColors());
            control.setBackground(action
                ? ui0Adapter.actionBackground()
                : ui0Adapter.neutralBackground());
        }
        styleRows();
        invalidate();
    }

    int overlayColor() {
        return ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.OVERLAY);
    }

    View preferredInitialFocus() {
        return queryInput;
    }

    private void submit() {
        String value = queryInput.getText() == null
            ? "" : queryInput.getText().toString().trim();
        if (value.isEmpty()) {
            showError("Enter text to search for.");
            queryInput.requestFocus();
            return;
        }
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > OctavoSearch.MAX_QUERY_UTF8_BYTES) {
            showError("Search text must be 127 UTF-8 bytes or fewer.");
            queryInput.requestFocus();
            return;
        }
        submittedQuery = value;
        showPending();
        listener.onSubmit(value);
    }

    private void rebuildResults() {
        resultList.removeAllViews();
        resultRows.clear();
        if (snapshot == null || snapshot.rowCount() == 0) {
            TextView empty = text(snapshot == null
                    ? "Search results are not available yet."
                    : snapshot.query().isEmpty()
                        ? "Enter text to find in this book."
                        : "No matches found.",
                Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                Typeface.NORMAL);
            empty.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            resultList.addView(empty, matchWrap());
            return;
        }
        for (int index = 0; index < snapshot.rowCount(); ++index) {
            OctavoSearch.Row result = snapshot.row(index);
            Ui0NavigationRow row = new Ui0NavigationRow(getContext());
            row.setId(View.generateViewId());
            row.setTag(result.index());
            row.setContentDescription(resultDescription(result));
            row.setSelected(result.index() == snapshot.activeIndex());
            row.setOnClickListener(view -> {
                Object tag = view.getTag();
                if (tag instanceof Integer && view.isEnabled()) {
                    listener.onActivate((Integer)tag);
                }
            });
            resultRows.add(row);
            LinearLayout.LayoutParams layout = matchWrap();
            if (index > 0) {
                layout.topMargin = ui0Adapter.treePx(
                    Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP);
            }
            resultList.addView(row, layout);
        }
        styleRows();
    }

    private void styleRows() {
        if (snapshot == null) {
            return;
        }
        for (int index = 0; index < resultRows.size(); ++index) {
            Ui0NavigationRow row = resultRows.get(index);
            OctavoSearch.Row result = snapshot.row(index);
            boolean active = result.index() == snapshot.activeIndex();
            row.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.BODY));
            row.setHierarchyText(result.section(),
                                 result.snippet(),
                                 false,
                                 ui0Adapter.relativeTextScale(
                                     Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                                     Ui0AndroidThemeSnapshot.TypographyRole.BODY),
                                 ui0Adapter.hierarchyTextColors(
                                     Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY),
                                 ui0Adapter.hierarchyTextColors(
                                     Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            row.setMinHeight(ui0Adapter.rowHeightPx());
            row.setPaddingRelative(
                ui0Adapter.hierarchyTextStartPx(0),
                controlPaddingY(),
                ui0Adapter.treePx(
                    Ui0AndroidThemeSnapshot.TreeMetric.PADDING_X),
                controlPaddingY());
            row.setCurrentRail(active,
                ui0Adapter.currentIndicatorColor(),
                ui0Adapter.treePx(
                    Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_WIDTH),
                ui0Adapter.currentRailInsetPx(),
                ui0Adapter.currentRailRadiusPx());
            row.setBackground(ui0Adapter.rowBackground(active));
        }
    }

    private void updateAvailability() {
        boolean pending = submittedQuery != null
            || (snapshot != null && snapshot.isPending());
        boolean ready = snapshot != null && snapshot.isReady();
        submitButton.setEnabled(!pending);
        queryInput.setEnabled(!pending);
        boolean hasResults = ready && snapshot.rowCount() > 0 && !pending;
        previousButton.setEnabled(hasResults);
        nextButton.setEnabled(hasResults);
        clearButton.setEnabled(ready && !snapshot.query().isEmpty() && !pending);
        for (Ui0NavigationRow row : resultRows) {
            row.setEnabled(hasResults);
        }
    }

    private void setInteractive(boolean interactive) {
        queryInput.setEnabled(interactive);
        submitButton.setEnabled(interactive);
        previousButton.setEnabled(interactive && snapshot != null
                                  && snapshot.rowCount() > 0);
        nextButton.setEnabled(interactive && snapshot != null
                              && snapshot.rowCount() > 0);
        clearButton.setEnabled(interactive && snapshot != null
                               && !snapshot.query().isEmpty());
        for (Ui0NavigationRow row : resultRows) {
            row.setEnabled(interactive);
        }
    }

    private void updateStatus() {
        if (snapshot == null || !snapshot.isReady()) {
            setStatus("Search is unavailable until the book is ready.", false);
        } else if (submittedQuery != null
                   && !submittedQuery.equals(snapshot.query())) {
            setStatus("Waiting for the reader to finish updating.", false);
        } else if (snapshot.isPending()) {
            setStatus("Updating search results.", false);
        } else if (snapshot.query().isEmpty()) {
            setStatus("Search this book.", false);
        } else if (snapshot.totalCount() == 0) {
            setStatus("No matches for “" + snapshot.query() + ".”", false);
        } else {
            String count = snapshot.totalCount() == 1
                ? "1 match" : snapshot.totalCount() + " matches";
            if (snapshot.isTruncated()) {
                count += "; showing the first " + snapshot.rowCount();
            }
            setStatus(count + ".", false);
        }
    }

    private String resultDescription(OctavoSearch.Row row) {
        StringBuilder value = new StringBuilder();
        value.append("Search result ").append(row.index() + 1)
            .append(" of ").append(snapshot.totalCount()).append(", ")
            .append(row.section()).append(", ").append(row.snippet());
        if (row.index() == snapshot.activeIndex()) {
            value.append(", current result");
        }
        return value.toString();
    }

    private void setStatus(String message, boolean error) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.length() > MAX_STATUS_CHARS) {
            normalized = normalized.substring(0, MAX_STATUS_CHARS);
        }
        statusIsError = error;
        status.setText(normalized);
        status.setTextColor(ui0Adapter.color(error
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
    }

    private TextView text(String value,
                          Ui0AndroidThemeSnapshot.TypographyRole role,
                          int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(ui0Adapter.textSizeSp(role));
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0.0f, 1.12f);
        return view;
    }

    private Button button(String value, String description) {
        Button result = new Button(getContext());
        result.setText(value);
        result.setContentDescription(description);
        result.setDefaultFocusHighlightEnabled(false);
        configureButtonGeometry(result);
        return result;
    }

    private void configureButtonGeometry(Button button) {
        button.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
        button.setAllCaps(false);
        button.setMinWidth(controlHeight());
        button.setMinHeight(controlHeight());
        button.setPadding(controlPaddingX(), controlPaddingY(),
                          controlPaddingX(), controlPaddingY());
        button.setGravity(Gravity.CENTER);
    }

    private void configureInput() {
        queryInput.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BODY));
        queryInput.setMinHeight(controlHeight());
        queryInput.setPadding(
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_X),
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y),
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_X),
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y));
        queryInput.setTextColor(ui0Adapter.inputTextColors());
        queryInput.setHintTextColor(ui0Adapter.inputHintColors());
        queryInput.setBackground(ui0Adapter.inputBackground());
        ui0Adapter.applyTextInputEditingColors(queryInput);
    }

    private int spacing(Ui0AndroidThemeSnapshot.SpacingRole role) {
        return ui0Adapter.spacingPx(role);
    }

    private int controlHeight() {
        return ui0Adapter.densityPx(
            Ui0AndroidThemeSnapshot.DensityRole.CONTROL_HEIGHT);
    }

    private int controlPaddingX() {
        return ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X);
    }

    private int controlPaddingY() {
        return ui0Adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_Y);
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
                "UI0 Android search theme is unavailable or incompatible");
        }
        return new Ui0AndroidThemeAdapter(snapshot, density);
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

    OctavoSearch snapshotForTesting() { return snapshot; }
    EditText queryInputForTesting() { return queryInput; }
    LinearLayout resultListForTesting() { return resultList; }
    TextView statusForTesting() { return status; }
    Button previousButtonForTesting() { return previousButton; }
    Button nextButtonForTesting() { return nextButton; }
}
