package ro.devze.octavo;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native Android projection of the current book's durable bookmarks. */
final class OctavoBookmarksPanel extends LinearLayout {
    interface Listener {
        void onDismiss();
        void onNavigate(OctavoAnnotationStore.Bookmark bookmark);
        void onRemove(OctavoAnnotationStore.Bookmark bookmark);
    }

    private static final int MAX_VISIBLE_ROWS = 256;
    private static final int MAX_STATUS_CHARS = 320;

    private final Listener listener;
    private final LinearLayout header;
    private final TextView title;
    private final Button dismissButton;
    private final TextView status;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final ArrayList<Row> rows = new ArrayList<>();
    private List<OctavoAnnotationStore.Bookmark> bookmarks =
        Collections.emptyList();
    private OctavoAppearance appearance;
    private Ui0AndroidThemeAdapter ui0Adapter;
    private boolean statusIsError;
    private boolean interactive = true;

    OctavoBookmarksPanel(Context context,
                         OctavoAppearance initialAppearance,
                         Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException(
                "Bookmarks listener is required");
        }
        this.listener = listener;
        appearance = initialAppearance == null
            ? OctavoAppearance.defaults() : initialAppearance;
        ui0Adapter = resolveAdapter(
            appearance, getResources().getDisplayMetrics().density);

        setId(R.id.octavo_bookmarks_panel);
        setOrientation(VERTICAL);
        setFocusable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Build.VERSION.SDK_INT >= 28) {
            setAccessibilityPaneTitle("Bookmarks");
        }

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, matchWrap());

        title = text("Bookmarks",
                     Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
                     Typeface.BOLD);
        title.setId(R.id.octavo_bookmarks_title);
        title.setContentDescription("Bookmarks");
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        header.addView(title, weightedWrap());

        dismissButton = button("Done", "Close bookmarks");
        dismissButton.setId(R.id.octavo_bookmarks_done);
        dismissButton.setOnClickListener(view -> listener.onDismiss());
        header.addView(dismissButton, wrapWrap());

        status = text("No bookmarks in this book.",
                      Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                      Typeface.NORMAL);
        status.setId(R.id.octavo_bookmarks_status);
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

        scroll = new ScrollView(context);
        scroll.setId(R.id.octavo_bookmarks_scroll);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setContentDescription("Bookmarks in this book");
        list = new LinearLayout(context);
        list.setId(R.id.octavo_bookmarks_list);
        list.setOrientation(VERTICAL);
        list.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCollectionInfo(
                    AccessibilityNodeInfo.CollectionInfo.obtain(
                        rows.size(),
                        1,
                        false,
                        AccessibilityNodeInfo.CollectionInfo
                            .SELECTION_MODE_NONE));
            }
        });
        scroll.addView(list,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        applyAppearance(appearance);
        updateBookmarks(Collections.emptyList());
    }

    void updateBookmarks(List<OctavoAnnotationStore.Bookmark> updated) {
        bookmarks = updated == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(updated));
        rebuildRows();
        updateStatus();
        setInteractive(interactive);
    }

    void showNavigationPending() {
        setStatus("Opening bookmark.", false);
        setInteractive(false);
    }

    void showError(String message) {
        setStatus(message == null || message.trim().isEmpty()
                      ? "The bookmark request could not be completed."
                      : message,
                  true);
        setInteractive(true);
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException(
                "Bookmarks appearance is required");
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
        scroll.setBackgroundColor(sheet);
        list.setBackgroundColor(sheet);
        title.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE));
        title.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
        status.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        status.setTextColor(ui0Adapter.color(statusIsError
            ? Ui0AndroidThemeSnapshot.ColorRole.DANGER
            : Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        configureButton(dismissButton, true);
        styleRows();
        invalidate();
    }

    int overlayColor() {
        return ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.OVERLAY);
    }

    View preferredInitialFocus() {
        return rows.isEmpty() ? dismissButton : rows.get(0).goTo;
    }

    private void rebuildRows() {
        list.removeAllViews();
        rows.clear();
        int visibleCount = Math.min(bookmarks.size(), MAX_VISIBLE_ROWS);
        if (visibleCount == 0) {
            TextView empty = text(
                "Use Add bookmark at the current reading position.",
                Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                Typeface.NORMAL);
            empty.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            list.addView(empty, matchWrap());
            return;
        }
        for (int index = 0; index < visibleCount; ++index) {
            OctavoAnnotationStore.Bookmark bookmark = bookmarks.get(index);
            Row row = new Row(getContext(), bookmark, index);
            rows.add(row);
            LinearLayout.LayoutParams layout = matchWrap();
            if (index > 0) {
                layout.topMargin = ui0Adapter.treePx(
                    Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP);
            }
            list.addView(row.container, layout);
        }
        styleRows();
    }

    private void styleRows() {
        for (Row row : rows) {
            row.container.setPadding(
                controlPaddingX(),
                controlPaddingY(),
                controlPaddingX(),
                controlPaddingY());
            row.container.setBackground(
                ui0Adapter.rowBackground(false));
            row.label.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.BODY));
            row.label.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
            row.excerpt.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
            row.excerpt.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            configureButton(row.goTo, true);
            configureButton(row.remove, false);
        }
    }

    private void updateStatus() {
        if (bookmarks.isEmpty()) {
            setStatus("No bookmarks in this book.", false);
            return;
        }
        String message = bookmarks.size() == 1
            ? "1 bookmark in this book."
            : bookmarks.size() + " bookmarks in this book.";
        if (bookmarks.size() > MAX_VISIBLE_ROWS) {
            message += " Showing the first " + MAX_VISIBLE_ROWS + ".";
        }
        int conflicts = 0;
        for (OctavoAnnotationStore.Bookmark bookmark : bookmarks) {
            if (bookmark.conflicted) {
                conflicts += 1;
            }
        }
        if (conflicts > 0) {
            message += " " + conflicts + " merge conflict"
                + (conflicts == 1 ? " is" : "s are")
                + " retained for resolution.";
        }
        setStatus(message, false);
    }

    private void setInteractive(boolean enabled) {
        interactive = enabled;
        for (Row row : rows) {
            row.goTo.setEnabled(enabled);
            row.remove.setEnabled(enabled);
        }
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
        result.setAllCaps(false);
        result.setDefaultFocusHighlightEnabled(false);
        return result;
    }

    private void configureButton(Button button, boolean action) {
        button.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
        button.setMinWidth(controlHeight());
        button.setMinHeight(controlHeight());
        button.setPadding(controlPaddingX(),
                          controlPaddingY(),
                          controlPaddingX(),
                          controlPaddingY());
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
                "UI0 Android bookmarks theme is unavailable or incompatible");
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

    List<OctavoAnnotationStore.Bookmark> bookmarksForTesting() {
        return bookmarks;
    }

    LinearLayout listForTesting() {
        return list;
    }

    TextView statusForTesting() {
        return status;
    }

    Button firstGoToForTesting() {
        return rows.isEmpty() ? null : rows.get(0).goTo;
    }

    Button firstRemoveForTesting() {
        return rows.isEmpty() ? null : rows.get(0).remove;
    }

    private final class Row {
        final LinearLayout container;
        final TextView label;
        final TextView excerpt;
        final Button goTo;
        final Button remove;

        Row(Context context,
            OctavoAnnotationStore.Bookmark bookmark,
            int index) {
            container = new LinearLayout(context);
            container.setOrientation(VERTICAL);
            container.setFocusable(false);
            container.setImportantForAccessibility(
                IMPORTANT_FOR_ACCESSIBILITY_NO);
            container.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                        View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setCollectionItemInfo(
                            AccessibilityNodeInfo.CollectionItemInfo.obtain(
                                index, 1, 0, 1, false, false));
                    }
                });

            label = text(bookmark.label,
                         Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                         Typeface.BOLD);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            container.addView(label, matchWrap());

            excerpt = text(bookmark.excerpt,
                           Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                           Typeface.NORMAL);
            excerpt.setMaxLines(3);
            excerpt.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams excerptLayout = matchWrap();
            excerptLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
            container.addView(excerpt, excerptLayout);

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLayout = matchWrap();
            actionsLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
            container.addView(actions, actionsLayout);

            goTo = button("Go to", "Go to " + bookmark.label);
            goTo.setOnClickListener(view -> {
                if (view.isEnabled()) {
                    listener.onNavigate(bookmark);
                }
            });
            actions.addView(goTo, weightedWrap());

            remove = button("Remove", "Remove " + bookmark.label);
            remove.setOnClickListener(view -> {
                if (view.isEnabled()) {
                    listener.onRemove(bookmark);
                }
            });
            LinearLayout.LayoutParams removeLayout = weightedWrap();
            removeLayout.setMarginStart(spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
            actions.addView(remove, removeLayout);
        }
    }
}
