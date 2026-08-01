package ro.devze.octavo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Host-owned, globally scoped reader appearance settings surface. */
final class OctavoAppearancePanel extends ScrollView {
    interface Listener {
        void onAppearanceRequested(OctavoAppearance appearance);
        void onDismiss();
    }

    private interface OptionLabel {
        String forValue(int value);
    }

    private interface AppearanceMutation {
        OctavoAppearance apply(OctavoAppearance base, int value);
    }

    private static final int TITLE_TEXT_SP = 26;
    private static final int SECTION_TEXT_SP = 18;
    private static final int SUBSECTION_TEXT_SP = 14;
    private static final int BODY_TEXT_SP = 16;
    private static final int NOTE_TEXT_SP = 14;

    private final Listener listener;
    private final LinearLayout content;
    private final Button dismissButton;
    private final TextView globalDefaultsNote;
    private final OptionGroup themeOptions;
    private final OptionGroup fontFamilyOptions;
    private final OptionGroup fontSizeOptions;
    private final OptionGroup lineSpacingOptions;
    private final OptionGroup marginOptions;
    private final OptionGroup alignmentOptions;
    private final OptionGroup publisherColorOptions;
    private final Switch reducedMotionSwitch;
    private final List<TextView> primaryText = new ArrayList<>();
    private final List<TextView> secondaryText = new ArrayList<>();
    private final List<RadioButton> optionButtons = new ArrayList<>();
    private final List<View> dividers = new ArrayList<>();

    private OctavoAppearance presentedAppearance;
    private OctavoAppearance requestedAppearance;
    private boolean synchronizing;

    OctavoAppearancePanel(Context context,
                          OctavoAppearance initialAppearance,
                          Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException(
                "Appearance panel listener is required");
        }
        this.listener = listener;

        setFillViewport(true);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 28) {
            setAccessibilityPaneTitle("Reading appearance");
        }

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(OctavoDesignTokens.SPACE_XL_DP),
                           dp(OctavoDesignTokens.SPACE_XL_DP),
                           dp(OctavoDesignTokens.SPACE_XL_DP),
                           dp(OctavoDesignTokens.SPACE_XXL_DP));
        addView(content,
                new LayoutParams(LayoutParams.MATCH_PARENT,
                                 LayoutParams.WRAP_CONTENT));

        TextView title = textView("Reading appearance",
                                  TITLE_TEXT_SP,
                                  Typeface.BOLD,
                                  primaryText);
        title.setContentDescription("Reading appearance settings");
        markHeading(title);
        content.addView(title, matchWrap());

        globalDefaultsNote = textView(
            "Preferences are global defaults for every book. Changes become "
                + "current only after the updated page is successfully shown.",
            NOTE_TEXT_SP,
            Typeface.NORMAL,
            secondaryText);
        LinearLayout.LayoutParams noteLayout = matchWrap();
        noteLayout.topMargin = dp(OctavoDesignTokens.SPACE_SM_DP);
        content.addView(globalDefaultsNote, noteLayout);

        dismissButton = new Button(context);
        dismissButton.setText("Done");
        dismissButton.setAllCaps(false);
        dismissButton.setTextSize(BODY_TEXT_SP);
        dismissButton.setMinWidth(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        dismissButton.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        dismissButton.setGravity(Gravity.CENTER);
        dismissButton.setContentDescription("Close reading appearance settings");
        dismissButton.setOnClickListener(view -> this.listener.onDismiss());
        dismissButton.setDefaultFocusHighlightEnabled(false);
        LinearLayout.LayoutParams dismissLayout = wrapWrap();
        dismissLayout.gravity = Gravity.END;
        dismissLayout.topMargin = dp(OctavoDesignTokens.SPACE_LG_DP);
        content.addView(dismissButton, dismissLayout);

        addSection("Theme",
                   "Six independently tuned palettes for daylight, low light, "
                       + "night reading, OLED, and high contrast.");
        themeOptions = addOptions(
            "Theme choices",
            sequence(OctavoAppearance.THEME_COUNT),
            this::themeOptionLabel,
            (base, value) -> base.withTheme(value));

        addSection("Typography",
                   "Only licensed system typefaces are used by these curated "
                       + "reader families.");
        addSubsection("Font family");
        fontFamilyOptions = addOptions(
            "Font family choices",
            sequence(OctavoAppearance.FONT_FAMILY_COUNT),
            this::fontFamilyOptionLabel,
            (base, value) -> base.withFontFamily(value));
        addSubsection("Font size");
        fontSizeOptions = addOptions(
            "Font size choices",
            OctavoAppearance.fontSizesSp(),
            OctavoAppearance::fontSizeLabel,
            (base, value) -> base.withFontSizeSp(value));
        addSubsection("Line spacing");
        lineSpacingOptions = addOptions(
            "Line spacing choices",
            OctavoAppearance.lineSpacingsPermille(),
            this::lineSpacingOptionLabel,
            (base, value) -> base.withLineSpacingPermille(value));

        addSection("Page layout",
                   "Layout changes rebuild canonical pagination and preserve "
                       + "the last successfully shown reading location.");
        addSubsection("Margins and content width");
        marginOptions = addOptions(
            "Margin and content width choices",
            sequence(OctavoAppearance.MARGINS_COUNT),
            this::marginOptionLabel,
            (base, value) -> base.withMargins(value));
        addSubsection("Alignment");
        alignmentOptions = addOptions(
            "Text alignment choices",
            sequence(OctavoAppearance.ALIGNMENT_COUNT),
            this::alignmentOptionLabel,
            (base, value) -> base.withAlignment(value));

        addSection("Publisher styles",
                   "Choose whether supported publisher colors adapt to the "
                       + "active reader theme.");
        publisherColorOptions = addOptions(
            "Publisher color choices",
            sequence(OctavoAppearance.PUBLISHER_COLORS_COUNT),
            this::publisherColorOptionLabel,
            (base, value) -> base.withPublisherColors(value));

        addSection("Motion",
                   "Reduced motion removes nonessential reader-control "
                       + "transitions without changing page state.");
        reducedMotionSwitch = new Switch(context);
        reducedMotionSwitch.setText("Reduce motion");
        reducedMotionSwitch.setTextSize(BODY_TEXT_SP);
        reducedMotionSwitch.setGravity(Gravity.CENTER_VERTICAL);
        reducedMotionSwitch.setMinHeight(
            dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        reducedMotionSwitch.setPadding(
            dp(OctavoDesignTokens.SPACE_MD_DP),
            dp(OctavoDesignTokens.SPACE_SM_DP),
            dp(OctavoDesignTokens.SPACE_MD_DP),
            dp(OctavoDesignTokens.SPACE_SM_DP));
        reducedMotionSwitch.setContentDescription(
            "Reduce motion for reader controls");
        reducedMotionSwitch.setDefaultFocusHighlightEnabled(false);
        reducedMotionSwitch.setOnCheckedChangeListener(
            (button, checked) -> {
                if (!synchronizing) {
                    request(requestedAppearance.withReducedMotion(checked));
                }
            });
        content.addView(reducedMotionSwitch, matchWrap());

        updatePresentedAppearance(initialAppearance == null
                                      ? OctavoAppearance.defaults()
                                      : initialAppearance);
    }

    /**
     * Commits the palette and selections only after the reader presents the
     * matching appearance. This also authoritatively resolves pending choices.
     */
    void updatePresentedAppearance(OctavoAppearance appearance) {
        if (appearance == null) {
            throw new IllegalArgumentException(
                "Presented appearance is required");
        }
        presentedAppearance = appearance;
        requestedAppearance = appearance;
        synchronizing = true;
        try {
            themeOptions.check(appearance.themeId());
            fontFamilyOptions.check(appearance.fontFamilyId());
            fontSizeOptions.check(appearance.fontSizeSp());
            lineSpacingOptions.check(appearance.lineSpacingPermille());
            marginOptions.check(appearance.marginsId());
            alignmentOptions.check(appearance.alignmentId());
            publisherColorOptions.check(appearance.publisherColorsId());
            reducedMotionSwitch.setChecked(appearance.reducedMotion());
        } finally {
            synchronizing = false;
        }
        applyTokens(OctavoDesignTokens.forAppearance(appearance));
    }

    private void request(OctavoAppearance candidate) {
        if (candidate == null || candidate.equals(requestedAppearance)) {
            return;
        }
        requestedAppearance = candidate;
        listener.onAppearanceRequested(candidate);
    }

    private void addSection(String title, String note) {
        addDivider();
        TextView heading = textView(title,
                                    SECTION_TEXT_SP,
                                    Typeface.BOLD,
                                    primaryText);
        markHeading(heading);
        content.addView(heading, matchWrap());
        TextView detail = textView(note,
                                   NOTE_TEXT_SP,
                                   Typeface.NORMAL,
                                   secondaryText);
        LinearLayout.LayoutParams detailLayout = matchWrap();
        detailLayout.topMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
        detailLayout.bottomMargin = dp(OctavoDesignTokens.SPACE_MD_DP);
        content.addView(detail, detailLayout);
    }

    private void addSubsection(String title) {
        TextView heading = textView(title,
                                    SUBSECTION_TEXT_SP,
                                    Typeface.BOLD,
                                    secondaryText);
        markHeading(heading);
        LinearLayout.LayoutParams layout = matchWrap();
        layout.topMargin = dp(OctavoDesignTokens.SPACE_LG_DP);
        layout.bottomMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
        content.addView(heading, layout);
    }

    private OptionGroup addOptions(String accessibilityLabel,
                                   int[] values,
                                   OptionLabel label,
                                   AppearanceMutation mutation) {
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.VERTICAL);
        group.setContentDescription(accessibilityLabel);
        group.setFocusable(false);
        SparseArray<RadioButton> buttons = new SparseArray<>();
        for (int value : values) {
            RadioButton option = new RadioButton(getContext());
            option.setId(View.generateViewId());
            option.setTag(value);
            option.setText(label.forValue(value));
            option.setTextSize(BODY_TEXT_SP);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            option.setPadding(dp(OctavoDesignTokens.SPACE_MD_DP),
                              dp(OctavoDesignTokens.SPACE_SM_DP),
                              dp(OctavoDesignTokens.SPACE_MD_DP),
                              dp(OctavoDesignTokens.SPACE_SM_DP));
            option.setDefaultFocusHighlightEnabled(false);
            RadioGroup.LayoutParams optionLayout = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            optionLayout.bottomMargin = dp(OctavoDesignTokens.SPACE_XS_DP);
            group.addView(option, optionLayout);
            buttons.put(value, option);
            optionButtons.add(option);
        }
        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            if (synchronizing || checkedId == RadioGroup.NO_ID) {
                return;
            }
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null && selected.getTag() instanceof Integer) {
                int value = (Integer)selected.getTag();
                request(mutation.apply(requestedAppearance, value));
            }
        });
        content.addView(group, matchWrap());
        return new OptionGroup(group, buttons);
    }

    private TextView textView(String text,
                              int textSizeSp,
                              int typefaceStyle,
                              List<TextView> themeGroup) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setTypeface(Typeface.DEFAULT, typefaceStyle);
        view.setLineSpacing(0.0f, 1.12f);
        view.setHighlightColor(
            OctavoDesignTokens.forAppearance(OctavoAppearance.defaults())
                .selection);
        themeGroup.add(view);
        return view;
    }

    private void addDivider() {
        View divider = new View(getContext());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(dp(1), 1));
        layout.topMargin = dp(OctavoDesignTokens.SPACE_XL_DP);
        layout.bottomMargin = dp(OctavoDesignTokens.SPACE_XL_DP);
        content.addView(divider, layout);
        dividers.add(divider);
    }

    private void applyTokens(OctavoDesignTokens tokens) {
        setBackgroundColor(tokens.settingsSurface);
        content.setBackgroundColor(tokens.settingsSurface);
        for (TextView text : primaryText) {
            text.setTextColor(tokens.settingsText);
            text.setHighlightColor(tokens.selection);
        }
        for (TextView text : secondaryText) {
            text.setTextColor(tokens.textSecondary);
            text.setHighlightColor(tokens.selection);
        }
        ColorStateList optionText = optionTextColors(tokens);
        ColorStateList radioTint = radioTint(tokens);
        for (RadioButton option : optionButtons) {
            option.setTextColor(optionText);
            option.setButtonTintList(radioTint);
            option.setBackground(optionBackground(tokens));
        }
        for (View divider : dividers) {
            divider.setBackgroundColor(tokens.dividerMuted);
        }
        dismissButton.setTextColor(tokens.onAccent);
        dismissButton.setBackground(actionBackground(tokens));
        reducedMotionSwitch.setTextColor(optionText);
        reducedMotionSwitch.setThumbTintList(switchThumbTint(tokens));
        reducedMotionSwitch.setTrackTintList(switchTrackTint(tokens));
        reducedMotionSwitch.setBackground(optionBackground(tokens));
        invalidate();
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
                        rounded(tokens.buttonSurface, tokens.accentPressed, 1));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(tokens.buttonSurface, tokens.focus, 2));
        result.addState(new int[] {},
                        rounded(tokens.settingsSurface,
                                tokens.dividerMuted,
                                1));
        return result;
    }

    private StateListDrawable actionBackground(OctavoDesignTokens tokens) {
        StateListDrawable result = new StateListDrawable();
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

    private static ColorStateList optionTextColors(
        OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                tokens.textMuted,
                tokens.settingsText,
                tokens.settingsText
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

    private static ColorStateList switchThumbTint(
        OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                tokens.divider,
                tokens.accent,
                tokens.textSecondary
            });
    }

    private static ColorStateList switchTrackTint(
        OctavoDesignTokens tokens) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                tokens.dividerMuted,
                tokens.selection,
                tokens.inputSurface
            });
    }

    private String themeOptionLabel(int value) {
        String detail;
        switch (value) {
            case OctavoAppearance.THEME_PAPER:
                detail = "warm near-white for daylight";
                break;
            case OctavoAppearance.THEME_SEPIA:
                detail = "soft parchment for relaxed reading";
                break;
            case OctavoAppearance.THEME_DUSK:
                detail = "cool low-light blue charcoal";
                break;
            case OctavoAppearance.THEME_WARM_DARK:
                detail = "warm charcoal and soft off-white for night";
                break;
            case OctavoAppearance.THEME_OLED:
                detail = "optional pure black for OLED screens";
                break;
            case OctavoAppearance.THEME_HIGH_CONTRAST:
                detail = "strongest light-theme separation";
                break;
            default:
                throw new IllegalArgumentException("Unknown theme");
        }
        return OctavoAppearance.themeLabel(value) + " — " + detail;
    }

    private String fontFamilyOptionLabel(int value) {
        String detail = value == OctavoAppearance.FONT_FAMILY_LITERARY
            ? "system serif" : "system sans serif";
        return OctavoAppearance.fontFamilyLabel(value) + " — " + detail;
    }

    private String lineSpacingOptionLabel(int value) {
        return OctavoAppearance.lineSpacingLabel(value) + " — "
            + (value / 10) + "% line height";
    }

    private String marginOptionLabel(int value) {
        int width = OctavoAppearance.defaults()
            .withMargins(value)
            .contentWidthPermille() / 10;
        return OctavoAppearance.marginsLabel(value) + " — " + width
            + "% content width";
    }

    private String alignmentOptionLabel(int value) {
        String detail = value == OctavoAppearance.ALIGNMENT_PUBLISHER
            ? "honor supported book alignment"
            : "left aligned with an open right edge";
        return OctavoAppearance.alignmentLabel(value) + " — " + detail;
    }

    private String publisherColorOptionLabel(int value) {
        String detail = value
            == OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE
                ? "adapt supported book colors for theme contrast"
                : "preserve supported book colors";
        return OctavoAppearance.publisherColorsLabel(value) + " — " + detail;
    }

    private static int[] sequence(int count) {
        int[] result = new int[Math.max(count, 0)];
        for (int index = 0; index < result.length; ++index) {
            result[index] = index;
        }
        return result;
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

    OctavoAppearance presentedAppearanceForTesting() {
        return presentedAppearance;
    }

    OctavoAppearance requestedAppearanceForTesting() {
        return requestedAppearance;
    }

    RadioGroup themeGroupForTesting() {
        return themeOptions.group;
    }

    RadioGroup fontFamilyGroupForTesting() {
        return fontFamilyOptions.group;
    }

    RadioGroup fontSizeGroupForTesting() {
        return fontSizeOptions.group;
    }

    RadioGroup lineSpacingGroupForTesting() {
        return lineSpacingOptions.group;
    }

    RadioGroup marginGroupForTesting() {
        return marginOptions.group;
    }

    RadioGroup alignmentGroupForTesting() {
        return alignmentOptions.group;
    }

    RadioGroup publisherColorGroupForTesting() {
        return publisherColorOptions.group;
    }

    Switch reducedMotionSwitchForTesting() {
        return reducedMotionSwitch;
    }

    Button dismissButtonForTesting() {
        return dismissButton;
    }

    TextView globalDefaultsNoteForTesting() {
        return globalDefaultsNote;
    }

    RadioButton themeOptionForTesting(int value) {
        return themeOptions.buttons.get(value);
    }

    RadioButton fontFamilyOptionForTesting(int value) {
        return fontFamilyOptions.buttons.get(value);
    }

    RadioButton fontSizeOptionForTesting(int value) {
        return fontSizeOptions.buttons.get(value);
    }

    RadioButton lineSpacingOptionForTesting(int value) {
        return lineSpacingOptions.buttons.get(value);
    }

    RadioButton marginOptionForTesting(int value) {
        return marginOptions.buttons.get(value);
    }

    RadioButton alignmentOptionForTesting(int value) {
        return alignmentOptions.buttons.get(value);
    }

    RadioButton publisherColorOptionForTesting(int value) {
        return publisherColorOptions.buttons.get(value);
    }

    private static final class OptionGroup {
        final RadioGroup group;
        final SparseArray<RadioButton> buttons;

        OptionGroup(RadioGroup group, SparseArray<RadioButton> buttons) {
            this.group = group;
            this.buttons = buttons;
        }

        void check(int value) {
            RadioButton button = buttons.get(value);
            if (button == null) {
                throw new IllegalArgumentException(
                    "Unsupported appearance option");
            }
            group.check(button.getId());
        }
    }
}
