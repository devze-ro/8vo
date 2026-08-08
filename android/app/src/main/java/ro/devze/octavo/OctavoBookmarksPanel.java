package ro.devze.octavo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native Android projection of the current book's durable annotations. */
final class OctavoBookmarksPanel extends LinearLayout {
    interface Listener {
        void onDismiss();
        void onNavigate(OctavoAnnotationStore.Bookmark bookmark);
        void onRemove(OctavoAnnotationStore.Bookmark bookmark);
        void onNavigate(OctavoAnnotationStore.Highlight highlight);
        void onRemove(OctavoAnnotationStore.Highlight highlight);
        void onRecolor(OctavoAnnotationStore.Highlight highlight,
                       OctavoAnnotationStore.HighlightColor color);
        void onNavigate(OctavoAnnotationStore.Note note);
        void onRemove(OctavoAnnotationStore.Note note);
        void onEdit(OctavoAnnotationStore.Note note, String body);
        boolean onDraftChanged(OctavoNoteDraftStore.Draft draft);
        void onSaveDraft(OctavoNoteDraftStore.Draft draft);
        void onCancelDraft(OctavoNoteDraftStore.Draft draft);
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
    private final LinearLayout noteEditor;
    private final TextView noteEditorTitle;
    private final TextView noteEditorExcerpt;
    private final EditText noteInput;
    private final TextView noteEditorStatus;
    private final Button noteSave;
    private final Button noteCancel;
    private final Button noteRetry;
    private final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<HighlightRow> highlightRows = new ArrayList<>();
    private final ArrayList<NoteRow> noteRows = new ArrayList<>();
    private List<OctavoAnnotationStore.Bookmark> bookmarks =
        Collections.emptyList();
    private List<OctavoAnnotationStore.Highlight> highlights =
        Collections.emptyList();
    private List<OctavoAnnotationStore.Note> notes =
        Collections.emptyList();
    private OctavoNoteDraftStore.Draft activeDraft;
    private boolean changingNoteText;
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
                "Annotations listener is required");
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
            setAccessibilityPaneTitle("Annotations");
        }

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, matchWrap());

        title = text("Annotations",
                     Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE,
                     Typeface.BOLD);
        title.setId(R.id.octavo_bookmarks_title);
        title.setContentDescription("Annotations");
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        header.addView(title, weightedWrap());

        dismissButton = button("Done", "Close annotations");
        dismissButton.setId(R.id.octavo_bookmarks_done);
        dismissButton.setOnClickListener(view -> listener.onDismiss());
        header.addView(dismissButton, wrapWrap());

        status = text("No annotations in this book.",
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

        noteEditor = new LinearLayout(context);
        noteEditor.setOrientation(VERTICAL);
        noteEditor.setVisibility(GONE);
        noteEditor.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        noteEditorTitle = text(
            "New note",
            Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE,
            Typeface.BOLD);
        noteEditorTitle.setId(R.id.octavo_note_editor_title);
        if (Build.VERSION.SDK_INT >= 28) {
            noteEditorTitle.setAccessibilityHeading(true);
        }
        noteEditor.addView(noteEditorTitle, matchWrap());

        noteEditorExcerpt = text(
            "Selected text",
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
            Typeface.NORMAL);
        noteEditorExcerpt.setId(R.id.octavo_note_editor_excerpt);
        noteEditorExcerpt.setMaxLines(3);
        noteEditorExcerpt.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams editorExcerptLayout = matchWrap();
        editorExcerptLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        noteEditor.addView(noteEditorExcerpt, editorExcerptLayout);

        noteInput = new EditText(context);
        noteInput.setId(R.id.octavo_note_editor_input);
        noteInput.setHint("Write a note");
        noteInput.setContentDescription("Note text");
        noteInput.setGravity(Gravity.TOP | Gravity.START);
        noteInput.setMinHeight(controlHeight() * 3);
        noteInput.setMaxLines(8);
        noteInput.setInputType(
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        noteInput.setFilters(new InputFilter[] {
            new Utf8LengthFilter(OctavoNoteDraftStore.maximumNoteBytes())
        });
        LinearLayout.LayoutParams noteInputLayout = matchWrap();
        noteInputLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        noteEditor.addView(noteInput, noteInputLayout);

        noteEditorStatus = text(
            "Draft saved locally.",
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
            Typeface.NORMAL);
        noteEditorStatus.setId(R.id.octavo_note_editor_status);
        noteEditorStatus.setMinHeight(controlHeight());
        noteEditorStatus.setGravity(Gravity.CENTER_VERTICAL);
        noteEditorStatus.setAccessibilityLiveRegion(
            ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams editorStatusLayout = matchWrap();
        editorStatusLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
        noteEditor.addView(noteEditorStatus, editorStatusLayout);

        LinearLayout noteActions = new LinearLayout(context);
        noteActions.setOrientation(HORIZONTAL);
        noteActions.setGravity(Gravity.CENTER_VERTICAL);
        noteSave = button("Save", "Save note");
        noteSave.setId(R.id.octavo_note_editor_save);
        noteSave.setOnClickListener(view -> saveActiveDraft());
        noteActions.addView(noteSave, weightedWrap());
        noteCancel = button("Cancel", "Cancel note editing");
        noteCancel.setId(R.id.octavo_note_editor_cancel);
        noteCancel.setOnClickListener(view -> {
            if (activeDraft != null) {
                listener.onCancelDraft(activeDraft);
            }
        });
        LinearLayout.LayoutParams noteCancelLayout = weightedWrap();
        noteCancelLayout.setMarginStart(spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        noteActions.addView(noteCancel, noteCancelLayout);
        noteRetry = button("Retry draft", "Retry saving note draft");
        noteRetry.setId(R.id.octavo_note_editor_retry);
        noteRetry.setVisibility(GONE);
        noteRetry.setOnClickListener(view -> persistActiveDraft());
        LinearLayout.LayoutParams noteRetryLayout = weightedWrap();
        noteRetryLayout.setMarginStart(spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
        noteActions.addView(noteRetry, noteRetryLayout);
        LinearLayout.LayoutParams noteActionsLayout = matchWrap();
        noteActionsLayout.topMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
        noteEditor.addView(noteActions, noteActionsLayout);

        noteInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value,
                                          int start,
                                          int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence value,
                                      int start,
                                      int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (!changingNoteText && activeDraft != null) {
                    activeDraft = activeDraft.withBody(value.toString());
                    persistActiveDraft();
                }
            }
        });

        LinearLayout.LayoutParams noteEditorLayout = matchWrap();
        noteEditorLayout.bottomMargin = spacing(
            Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP);
        addView(noteEditor, noteEditorLayout);

        scroll = new ScrollView(context);
        scroll.setId(R.id.octavo_bookmarks_scroll);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setContentDescription("Annotations in this book");
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
                        rows.size() + highlightRows.size() + noteRows.size(),
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
        updateAnnotations(Collections.emptyList(),
                          Collections.emptyList(),
                          Collections.emptyList());
    }

    void updateBookmarks(List<OctavoAnnotationStore.Bookmark> updated) {
        updateAnnotations(updated, highlights, notes);
    }

    void updateAnnotations(
        List<OctavoAnnotationStore.Bookmark> updatedBookmarks,
        List<OctavoAnnotationStore.Highlight> updatedHighlights,
        List<OctavoAnnotationStore.Note> updatedNotes) {
        bookmarks = updatedBookmarks == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(
                new ArrayList<>(updatedBookmarks));
        highlights = updatedHighlights == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(
                new ArrayList<>(updatedHighlights));
        notes = updatedNotes == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(
                new ArrayList<>(updatedNotes));
        rebuildRows();
        updateStatus();
        setInteractive(interactive);
    }

    void showNavigationPending() {
        setStatus("Opening annotation.", false);
        setInteractive(false);
    }

    void showError(String message) {
        setStatus(message == null || message.trim().isEmpty()
                      ? "The annotation request could not be completed."
                      : message,
                  true);
        setInteractive(true);
    }

    void applyAppearance(OctavoAppearance updated) {
        if (updated == null) {
            throw new IllegalArgumentException(
                "Annotations appearance is required");
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
        noteEditorTitle.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE));
        noteEditorTitle.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
        noteEditorExcerpt.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        noteEditorExcerpt.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        noteInput.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.BODY));
        noteInput.setPadding(
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_X),
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y),
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_X),
            ui0Adapter.textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y));
        noteInput.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
        noteInput.setHintTextColor(ui0Adapter.inputHintColors());
        noteInput.setBackground(ui0Adapter.inputBackground());
        ui0Adapter.applyTextInputEditingColors(noteInput);
        noteEditorStatus.setTextSize(ui0Adapter.textSizeSp(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
        configureButton(noteSave, true);
        configureButton(noteCancel, false);
        configureButton(noteRetry, false);
        styleRows();
        invalidate();
    }

    int overlayColor() {
        return ui0Adapter.color(Ui0AndroidThemeSnapshot.ColorRole.OVERLAY);
    }

    View preferredInitialFocus() {
        if (activeDraft != null) {
            return noteInput;
        }
        if (!rows.isEmpty()) {
            return rows.get(0).goTo;
        }
        if (!highlightRows.isEmpty()) {
            return highlightRows.get(0).goTo;
        }
        return noteRows.isEmpty() ? dismissButton : noteRows.get(0).goTo;
    }

    private void rebuildRows() {
        list.removeAllViews();
        rows.clear();
        highlightRows.clear();
        noteRows.clear();
        int bookmarkCount = Math.min(bookmarks.size(), MAX_VISIBLE_ROWS);
        int highlightCount = Math.min(
            highlights.size(), MAX_VISIBLE_ROWS - bookmarkCount);
        int noteCount = Math.min(
            notes.size(), MAX_VISIBLE_ROWS - bookmarkCount - highlightCount);
        if (bookmarkCount == 0 && highlightCount == 0 && noteCount == 0) {
            TextView empty = text(
                "Add a bookmark, highlight, or note while reading.",
                Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                Typeface.NORMAL);
            empty.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            list.addView(empty, matchWrap());
            return;
        }
        if (bookmarkCount > 0) {
            list.addView(sectionHeading("Bookmarks"), matchWrap());
        }
        for (int index = 0; index < bookmarkCount; ++index) {
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
        if (highlightCount > 0) {
            LinearLayout.LayoutParams headingLayout = matchWrap();
            headingLayout.topMargin = bookmarkCount > 0
                ? spacing(Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP) : 0;
            list.addView(sectionHeading("Highlights"), headingLayout);
        }
        for (int index = 0; index < highlightCount; ++index) {
            OctavoAnnotationStore.Highlight highlight = highlights.get(index);
            HighlightRow row = new HighlightRow(
                getContext(), highlight, bookmarkCount + index);
            highlightRows.add(row);
            LinearLayout.LayoutParams layout = matchWrap();
            if (index > 0) {
                layout.topMargin = ui0Adapter.treePx(
                    Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP);
            }
            list.addView(row.container, layout);
        }
        if (noteCount > 0) {
            LinearLayout.LayoutParams headingLayout = matchWrap();
            headingLayout.topMargin = bookmarkCount + highlightCount > 0
                ? spacing(Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP) : 0;
            list.addView(sectionHeading("Notes"), headingLayout);
        }
        for (int index = 0; index < noteCount; ++index) {
            OctavoAnnotationStore.Note note = notes.get(index);
            NoteRow row = new NoteRow(
                getContext(), note, bookmarkCount + highlightCount + index);
            noteRows.add(row);
            LinearLayout.LayoutParams layout = matchWrap();
            if (index > 0) {
                layout.topMargin = ui0Adapter.treePx(
                    Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP);
            }
            list.addView(row.container, layout);
        }
        styleRows();
    }

    void showNoteEditor(OctavoNoteDraftStore.Draft draft,
                        boolean recovered,
                        boolean conflicted) {
        if (draft == null) {
            throw new IllegalArgumentException("Note draft is required");
        }
        activeDraft = draft;
        changingNoteText = true;
        noteInput.setText(draft.body);
        noteInput.setSelection(noteInput.length());
        changingNoteText = false;
        noteEditorTitle.setText(conflicted
            ? "Resolve note conflict"
            : draft.isNewNote() ? "New note" : "Edit note");
        noteEditorExcerpt.setText(draft.excerpt.trim().isEmpty()
            ? "Selected reading position" : draft.excerpt);
        noteEditorStatus.setText(recovered
            ? "Recovered unsaved note draft."
            : "Draft saved locally.");
        noteEditorStatus.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
        noteRetry.setVisibility(GONE);
        noteEditor.setVisibility(VISIBLE);
        noteEditor.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_YES);
        noteSave.setEnabled(!draft.body.isEmpty());
        scroll.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        noteInput.requestFocus();
    }

    void finishNoteEditor(String message) {
        activeDraft = null;
        changingNoteText = true;
        noteInput.setText("");
        changingNoteText = false;
        noteEditor.setVisibility(GONE);
        noteEditor.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        scroll.setImportantForAccessibility(
            IMPORTANT_FOR_ACCESSIBILITY_YES);
        setStatus(message, false);
    }

    boolean hasActiveDraft() {
        return activeDraft != null;
    }

    private void persistActiveDraft() {
        if (activeDraft == null) {
            return;
        }
        boolean saved = listener.onDraftChanged(activeDraft);
        noteSave.setEnabled(!activeDraft.body.isEmpty());
        noteRetry.setVisibility(saved ? GONE : VISIBLE);
        noteEditorStatus.setText(saved
            ? "Draft saved locally."
            : "Draft could not be saved. Your text is still in the editor; retry before leaving.");
        noteEditorStatus.setTextColor(ui0Adapter.color(saved
            ? Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY
            : Ui0AndroidThemeSnapshot.ColorRole.DANGER));
    }

    private void saveActiveDraft() {
        if (activeDraft == null || activeDraft.body.isEmpty()) {
            noteEditorStatus.setText("Write note text before saving.");
            noteEditorStatus.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.DANGER));
            return;
        }
        listener.onSaveDraft(activeDraft);
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
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        for (HighlightRow row : highlightRows) {
            row.container.setPadding(
                controlPaddingX(), controlPaddingY(),
                controlPaddingX(), controlPaddingY());
            row.container.setBackground(ui0Adapter.rowBackground(false));
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
            for (int index = 0; index < row.colors.length; ++index) {
                Button button = row.colors[index];
                OctavoAnnotationStore.HighlightColor color =
                    OctavoAnnotationStore.HighlightColor.values()[index];
                configureHighlightColorButton(
                    button,
                    tokens.annotationHighlightColor(color),
                    color == row.highlight.color);
            }
        }
        for (NoteRow row : noteRows) {
            row.container.setPadding(
                controlPaddingX(), controlPaddingY(),
                controlPaddingX(), controlPaddingY());
            row.container.setBackground(ui0Adapter.rowBackground(false));
            row.label.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.BODY));
            row.label.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
            row.excerpt.setTextSize(ui0Adapter.textSizeSp(
                Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
            row.excerpt.setTextColor(ui0Adapter.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY));
            for (TextView body : row.bodies) {
                body.setTextSize(ui0Adapter.textSizeSp(
                    Ui0AndroidThemeSnapshot.TypographyRole.BODY));
                body.setTextColor(ui0Adapter.color(
                    Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
            }
            configureButton(row.goTo, true);
            configureButton(row.edit, false);
            configureButton(row.remove, false);
            for (Button resolve : row.resolve) {
                configureButton(resolve, false);
            }
        }
    }

    private void updateStatus() {
        if (bookmarks.isEmpty() && highlights.isEmpty() && notes.isEmpty()) {
            setStatus("No annotations in this book.", false);
            return;
        }
        String message = bookmarks.size() +
            (bookmarks.size() == 1 ? " bookmark and " : " bookmarks and ")
            + highlights.size()
            + (highlights.size() == 1 ? " highlight and " : " highlights and ")
            + notes.size()
            + (notes.size() == 1 ? " note" : " notes")
            + " in this book.";
        if (bookmarks.size() + highlights.size() + notes.size()
            > MAX_VISIBLE_ROWS) {
            message += " Showing the first " + MAX_VISIBLE_ROWS + ".";
        }
        int conflicts = 0;
        for (OctavoAnnotationStore.Bookmark bookmark : bookmarks) {
            if (bookmark.conflicted) {
                conflicts += 1;
            }
        }
        for (OctavoAnnotationStore.Highlight highlight : highlights) {
            if (highlight.conflicted) {
                conflicts += 1;
            }
        }
        for (OctavoAnnotationStore.Note note : notes) {
            if (note.conflicted) {
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
        for (HighlightRow row : highlightRows) {
            row.goTo.setEnabled(enabled);
            row.remove.setEnabled(enabled);
            for (Button color : row.colors) {
                color.setEnabled(enabled);
            }
        }
        for (NoteRow row : noteRows) {
            row.goTo.setEnabled(enabled);
            row.edit.setEnabled(enabled);
            row.remove.setEnabled(enabled);
            for (Button resolve : row.resolve) {
                resolve.setEnabled(enabled);
            }
        }
        noteInput.setEnabled(enabled);
        noteSave.setEnabled(enabled && activeDraft != null
            && !activeDraft.body.isEmpty());
        noteCancel.setEnabled(enabled);
        noteRetry.setEnabled(enabled);
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

    private TextView sectionHeading(String value) {
        TextView heading = text(
            value,
            Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE,
            Typeface.BOLD);
        heading.setTextColor(ui0Adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY));
        heading.setPadding(0, 0, 0,
            spacing(Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP));
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        return heading;
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

    private void configureHighlightColorButton(Button button,
                                               int argb,
                                               boolean selected) {
        configureButton(button, false);
        button.setBackgroundTintList(ColorStateList.valueOf(argb));
        button.setTextColor(appearance != null
            && OctavoDesignTokens.forAppearance(appearance).darkAppearance
                ? Color.WHITE : Color.BLACK);
        button.setSelected(selected);
        if (Build.VERSION.SDK_INT >= 30) {
            button.setStateDescription(selected
                ? "Current highlight color" : "Available highlight color");
        }
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

    List<OctavoAnnotationStore.Highlight> highlightsForTesting() {
        return highlights;
    }

    List<OctavoAnnotationStore.Note> notesForTesting() {
        return notes;
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

    Button firstHighlightGoToForTesting() {
        return highlightRows.isEmpty() ? null : highlightRows.get(0).goTo;
    }

    Button firstHighlightRemoveForTesting() {
        return highlightRows.isEmpty() ? null : highlightRows.get(0).remove;
    }

    Button firstHighlightColorForTesting(int colorIndex) {
        return highlightRows.isEmpty() || colorIndex < 0 || colorIndex >= 4
            ? null : highlightRows.get(0).colors[colorIndex];
    }

    EditText noteInputForTesting() {
        return noteInput;
    }

    TextView noteEditorStatusForTesting() {
        return noteEditorStatus;
    }

    Button noteSaveForTesting() {
        return noteSave;
    }

    Button noteCancelForTesting() {
        return noteCancel;
    }

    Button noteRetryForTesting() {
        return noteRetry;
    }

    Button firstNoteGoToForTesting() {
        return noteRows.isEmpty() ? null : noteRows.get(0).goTo;
    }

    Button firstNoteEditForTesting() {
        return noteRows.isEmpty() ? null : noteRows.get(0).edit;
    }

    Button firstNoteRemoveForTesting() {
        return noteRows.isEmpty() ? null : noteRows.get(0).remove;
    }

    Button firstNoteResolveForTesting(int version) {
        return noteRows.isEmpty() || version < 0
            || version >= noteRows.get(0).resolve.size()
                ? null : noteRows.get(0).resolve.get(version);
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

    private final class HighlightRow {
        final OctavoAnnotationStore.Highlight highlight;
        final LinearLayout container;
        final TextView label;
        final TextView excerpt;
        final Button[] colors = new Button[4];
        final Button goTo;
        final Button remove;

        HighlightRow(Context context,
                     OctavoAnnotationStore.Highlight highlight,
                     int collectionIndex) {
            this.highlight = highlight;
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
                                collectionIndex, 1, 0, 1, false, false));
                    }
                });

            String location = highlight.color.label + " highlight at section "
                + (highlight.spineIndex + 1);
            if (highlight.conflicted) {
                location += ", merge conflict retained";
            }
            label = text(location,
                         Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                         Typeface.BOLD);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            container.addView(label, matchWrap());

            String excerptText = highlight.excerpt.trim().isEmpty()
                ? "Highlighted text" : highlight.excerpt;
            excerpt = text(excerptText,
                           Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                           Typeface.NORMAL);
            excerpt.setMaxLines(3);
            excerpt.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams excerptLayout = matchWrap();
            excerptLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
            container.addView(excerpt, excerptLayout);

            HorizontalScrollView colorScroll = new HorizontalScrollView(context);
            colorScroll.setFillViewport(false);
            colorScroll.setHorizontalScrollBarEnabled(false);
            colorScroll.setContentDescription(
                "Choose a named highlight color");
            LinearLayout colorStrip = new LinearLayout(context);
            colorStrip.setOrientation(HORIZONTAL);
            for (OctavoAnnotationStore.HighlightColor color
                    : OctavoAnnotationStore.HighlightColor.values()) {
                Button colorButton = button(
                    color.label, "Set highlight color to " + color.label);
                colorButton.setOnClickListener(view -> {
                    if (view.isEnabled()) {
                        listener.onRecolor(highlight, color);
                    }
                });
                colors[color.wireId] = colorButton;
                LinearLayout.LayoutParams colorLayout = wrapWrap();
                if (color.wireId > 0) {
                    colorLayout.setMarginStart(spacing(
                        Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
                }
                colorStrip.addView(colorButton, colorLayout);
            }
            colorScroll.addView(colorStrip,
                new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams colorScrollLayout = matchWrap();
            colorScrollLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
            container.addView(colorScroll, colorScrollLayout);

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLayout = matchWrap();
            actionsLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
            container.addView(actions, actionsLayout);

            goTo = button("Go to", "Go to " + location);
            goTo.setOnClickListener(view -> {
                if (view.isEnabled()) {
                    listener.onNavigate(highlight);
                }
            });
            actions.addView(goTo, weightedWrap());

            remove = button("Remove", "Remove " + location);
            remove.setOnClickListener(view -> {
                if (view.isEnabled()) {
                    listener.onRemove(highlight);
                }
            });
            LinearLayout.LayoutParams removeLayout = weightedWrap();
            removeLayout.setMarginStart(spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
            actions.addView(remove, removeLayout);
        }
    }

    private final class NoteRow {
        final OctavoAnnotationStore.Note note;
        final LinearLayout container;
        final TextView label;
        final TextView excerpt;
        final ArrayList<TextView> bodies = new ArrayList<>();
        final ArrayList<Button> resolve = new ArrayList<>();
        final Button goTo;
        final Button edit;
        final Button remove;

        NoteRow(Context context,
                OctavoAnnotationStore.Note note,
                int collectionIndex) {
            this.note = note;
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
                                collectionIndex, 1, 0, 1, false, false));
                    }
                });

            String location = "Note at section " + (note.spineIndex + 1);
            if (note.conflicted) {
                location += ", " + note.versions.size()
                    + " recoverable versions";
            }
            label = text(location,
                         Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                         Typeface.BOLD);
            label.setMaxLines(2);
            container.addView(label, matchWrap());

            excerpt = text(
                note.excerpt.trim().isEmpty()
                    ? "Saved reading position" : note.excerpt,
                Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                Typeface.NORMAL);
            excerpt.setMaxLines(3);
            excerpt.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams excerptLayout = matchWrap();
            excerptLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
            container.addView(excerpt, excerptLayout);

            for (int index = 0; index < note.versions.size(); ++index) {
                OctavoAnnotationStore.NoteVersion version =
                    note.versions.get(index);
                TextView body = text(
                    version.body,
                    Ui0AndroidThemeSnapshot.TypographyRole.BODY,
                    Typeface.NORMAL);
                body.setMaxLines(note.conflicted ? 6 : 4);
                body.setEllipsize(TextUtils.TruncateAt.END);
                body.setContentDescription(note.conflicted
                    ? "Note version " + (index + 1) + ". " + version.body
                    : "Note text. " + version.body);
                LinearLayout.LayoutParams bodyLayout = matchWrap();
                bodyLayout.topMargin = spacing(
                    Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
                container.addView(body, bodyLayout);
                bodies.add(body);

                if (note.conflicted) {
                    int versionIndex = index;
                    Button use = button(
                        "Use version " + (index + 1),
                        "Resolve note using version " + (index + 1));
                    use.setOnClickListener(view -> listener.onEdit(
                        note, note.versions.get(versionIndex).body));
                    LinearLayout.LayoutParams useLayout = matchWrap();
                    useLayout.topMargin = spacing(
                        Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP);
                    container.addView(use, useLayout);
                    resolve.add(use);
                }
            }

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLayout = matchWrap();
            actionsLayout.topMargin = spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP);
            container.addView(actions, actionsLayout);

            goTo = button("Go to", "Go to " + location);
            goTo.setOnClickListener(view -> listener.onNavigate(note));
            actions.addView(goTo, weightedWrap());

            edit = button("Edit", "Edit " + location);
            edit.setOnClickListener(view -> listener.onEdit(
                note, note.preferredBody()));
            LinearLayout.LayoutParams editLayout = weightedWrap();
            editLayout.setMarginStart(spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
            actions.addView(edit, editLayout);

            remove = button("Remove", "Remove " + location);
            remove.setOnClickListener(view -> listener.onRemove(note));
            LinearLayout.LayoutParams removeLayout = weightedWrap();
            removeLayout.setMarginStart(spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
            actions.addView(remove, removeLayout);
        }
    }

    private static final class Utf8LengthFilter implements InputFilter {
        private final int maximumBytes;

        Utf8LengthFilter(int maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        @Override
        public CharSequence filter(CharSequence source,
                                   int start,
                                   int end,
                                   Spanned destination,
                                   int destinationStart,
                                   int destinationEnd) {
            String prefix = destination.subSequence(
                0, destinationStart).toString();
            String suffix = destination.subSequence(
                destinationEnd, destination.length()).toString();
            String insertion = source.subSequence(start, end).toString();
            if (utf8Length(prefix + insertion + suffix) <= maximumBytes) {
                return null;
            }
            StringBuilder accepted = new StringBuilder();
            for (int offset = 0; offset < insertion.length();) {
                int codePoint = insertion.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                if (utf8Length(prefix + accepted + next + suffix)
                    > maximumBytes) {
                    break;
                }
                accepted.append(next);
                offset += Character.charCount(codePoint);
            }
            return accepted.toString();
        }

        private static int utf8Length(String value) {
            return value.getBytes(StandardCharsets.UTF_8).length;
        }
    }
}
