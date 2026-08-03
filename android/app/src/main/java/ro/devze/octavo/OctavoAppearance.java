package ro.devze.octavo;

import android.graphics.Typeface;

import java.util.Arrays;

/**
 * Immutable, process-independent reader appearance values.
 *
 * Port 7 deliberately has one global appearance. Per-book overrides would
 * need a separately versioned ownership and migration contract.
 */
final class OctavoAppearance {
    static final int NATIVE_MAGIC = 0x4F375041; // "O7PA"
    static final int NATIVE_VERSION = 1;

    static final int NATIVE_MAGIC_INDEX = 0;
    static final int NATIVE_VERSION_INDEX = 1;
    static final int NATIVE_THEME_INDEX = 2;
    static final int NATIVE_FONT_FAMILY_INDEX = 3;
    static final int NATIVE_FONT_SIZE_SP_INDEX = 4;
    static final int NATIVE_LINE_SPACING_PERMILLE_INDEX = 5;
    static final int NATIVE_MARGIN_INDEX = 6;
    static final int NATIVE_CONTENT_WIDTH_PERMILLE_INDEX = 7;
    static final int NATIVE_ALIGNMENT_INDEX = 8;
    static final int NATIVE_PUBLISHER_COLORS_INDEX = 9;
    static final int NATIVE_REDUCED_MOTION_INDEX = 10;
    static final int NATIVE_FIELD_COUNT = 11;

    static final int THEME_PAPER = 0;
    static final int THEME_SEPIA = 1;
    static final int THEME_DUSK = 2;
    static final int THEME_WARM_DARK = 3;
    static final int THEME_OLED = 4;
    static final int THEME_HIGH_CONTRAST = 5;
    static final int THEME_COUNT = 6;

    static final int FONT_FAMILY_LITERARY = 0;
    static final int FONT_FAMILY_CLEAR = 1;
    static final int FONT_FAMILY_COUNT = 2;

    static final int MARGINS_WIDE = 0;
    static final int MARGINS_BALANCED = 1;
    static final int MARGINS_FOCUSED = 2;
    static final int MARGINS_COUNT = 3;

    static final int ALIGNMENT_PUBLISHER = 0;
    static final int ALIGNMENT_RAGGED_RIGHT = 1;
    static final int ALIGNMENT_COUNT = 2;

    static final int PUBLISHER_COLORS_THEME_SAFE = 0;
    static final int PUBLISHER_COLORS_ALLOW = 1;
    static final int PUBLISHER_COLORS_COUNT = 2;

    private static final int[] FONT_SIZES_SP = {14, 16, 18, 21, 24, 28};
    private static final int[] LINE_SPACINGS_PERMILLE = {
        1150, 1250, 1300, 1500
    };
    private static final int[] CONTENT_WIDTHS_PERMILLE = {720, 860, 960};

    private static final OctavoAppearance DEFAULT = new OctavoAppearance(
        THEME_PAPER,
        FONT_FAMILY_LITERARY,
        14,
        1250,
        MARGINS_BALANCED,
        ALIGNMENT_PUBLISHER,
        PUBLISHER_COLORS_THEME_SAFE,
        false);

    /* Frozen schema default used only to construct legacy test records. */
    private static final OctavoAppearance PREVIOUS_DEFAULT_16SP =
        new OctavoAppearance(
            THEME_PAPER,
            FONT_FAMILY_LITERARY,
            16,
            1250,
            MARGINS_BALANCED,
            ALIGNMENT_PUBLISHER,
            PUBLISHER_COLORS_THEME_SAFE,
            false);


    private final int themeId;
    private final int fontFamilyId;
    private final int fontSizeSp;
    private final int lineSpacingPermille;
    private final int marginsId;
    private final int alignmentId;
    private final int publisherColorsId;
    private final boolean reducedMotion;

    private OctavoAppearance(int themeId,
                             int fontFamilyId,
                             int fontSizeSp,
                             int lineSpacingPermille,
                             int marginsId,
                             int alignmentId,
                             int publisherColorsId,
                             boolean reducedMotion) {
        this.themeId = themeId;
        this.fontFamilyId = fontFamilyId;
        this.fontSizeSp = fontSizeSp;
        this.lineSpacingPermille = lineSpacingPermille;
        this.marginsId = marginsId;
        this.alignmentId = alignmentId;
        this.publisherColorsId = publisherColorsId;
        this.reducedMotion = reducedMotion;
    }

    static OctavoAppearance defaults() {
        return DEFAULT;
    }

    static OctavoAppearance previousDefault16Sp() {
        return PREVIOUS_DEFAULT_16SP;
    }

    static OctavoAppearance create(int themeId,
                                   int fontFamilyId,
                                   int fontSizeSp,
                                   int lineSpacingPermille,
                                   int marginsId,
                                   int alignmentId,
                                   int publisherColorsId,
                                   boolean reducedMotion) {
        requireTheme(themeId);
        requireFontFamily(fontFamilyId);
        requireFontSize(fontSizeSp);
        requireLineSpacing(lineSpacingPermille);
        requireMargins(marginsId);
        requireAlignment(alignmentId);
        requirePublisherColors(publisherColorsId);
        return new OctavoAppearance(
            themeId,
            fontFamilyId,
            fontSizeSp,
            lineSpacingPermille,
            marginsId,
            alignmentId,
            publisherColorsId,
            reducedMotion);
    }

    static OctavoAppearance fromNativeConfig(int[] config) {
        if (config == null || config.length != NATIVE_FIELD_COUNT
            || config[NATIVE_MAGIC_INDEX] != NATIVE_MAGIC
            || config[NATIVE_VERSION_INDEX] != NATIVE_VERSION) {
            return null;
        }
        try {
            OctavoAppearance result = create(
                config[NATIVE_THEME_INDEX],
                config[NATIVE_FONT_FAMILY_INDEX],
                config[NATIVE_FONT_SIZE_SP_INDEX],
                config[NATIVE_LINE_SPACING_PERMILLE_INDEX],
                config[NATIVE_MARGIN_INDEX],
                config[NATIVE_ALIGNMENT_INDEX],
                config[NATIVE_PUBLISHER_COLORS_INDEX],
                config[NATIVE_REDUCED_MOTION_INDEX] == 1);
            if ((config[NATIVE_REDUCED_MOTION_INDEX] != 0
                 && config[NATIVE_REDUCED_MOTION_INDEX] != 1)
                || config[NATIVE_CONTENT_WIDTH_PERMILLE_INDEX]
                   != result.contentWidthPermille()) {
                return null;
            }
            return result;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    int themeId() {
        return themeId;
    }

    int fontFamilyId() {
        return fontFamilyId;
    }

    Typeface systemTypeface() {
        return fontFamilyId == FONT_FAMILY_CLEAR
            ? Typeface.SANS_SERIF
            : Typeface.SERIF;
    }

    int fontSizeSp() {
        return fontSizeSp;
    }

    int lineSpacingPermille() {
        return lineSpacingPermille;
    }

    int marginsId() {
        return marginsId;
    }

    int contentWidthPermille() {
        return CONTENT_WIDTHS_PERMILLE[marginsId];
    }

    int alignmentId() {
        return alignmentId;
    }

    int publisherColorsId() {
        return publisherColorsId;
    }

    boolean reducedMotion() {
        return reducedMotion;
    }

    OctavoAppearance withTheme(int value) {
        return create(value, fontFamilyId, fontSizeSp, lineSpacingPermille,
                      marginsId, alignmentId, publisherColorsId,
                      reducedMotion);
    }

    OctavoAppearance withFontFamily(int value) {
        return create(themeId, value, fontSizeSp, lineSpacingPermille,
                      marginsId, alignmentId, publisherColorsId,
                      reducedMotion);
    }

    OctavoAppearance withFontSizeSp(int value) {
        return create(themeId, fontFamilyId, value, lineSpacingPermille,
                      marginsId, alignmentId, publisherColorsId,
                      reducedMotion);
    }

    OctavoAppearance withLineSpacingPermille(int value) {
        return create(themeId, fontFamilyId, fontSizeSp, value,
                      marginsId, alignmentId, publisherColorsId,
                      reducedMotion);
    }

    OctavoAppearance withMargins(int value) {
        return create(themeId, fontFamilyId, fontSizeSp,
                      lineSpacingPermille, value, alignmentId,
                      publisherColorsId, reducedMotion);
    }

    OctavoAppearance withAlignment(int value) {
        return create(themeId, fontFamilyId, fontSizeSp,
                      lineSpacingPermille, marginsId, value,
                      publisherColorsId, reducedMotion);
    }

    OctavoAppearance withPublisherColors(int value) {
        return create(themeId, fontFamilyId, fontSizeSp,
                      lineSpacingPermille, marginsId, alignmentId, value,
                      reducedMotion);
    }

    OctavoAppearance withReducedMotion(boolean value) {
        return create(themeId, fontFamilyId, fontSizeSp,
                      lineSpacingPermille, marginsId, alignmentId,
                      publisherColorsId, value);
    }

    int[] nativeConfig() {
        int[] result = new int[NATIVE_FIELD_COUNT];
        result[NATIVE_MAGIC_INDEX] = NATIVE_MAGIC;
        result[NATIVE_VERSION_INDEX] = NATIVE_VERSION;
        result[NATIVE_THEME_INDEX] = themeId;
        result[NATIVE_FONT_FAMILY_INDEX] = fontFamilyId;
        result[NATIVE_FONT_SIZE_SP_INDEX] = fontSizeSp;
        result[NATIVE_LINE_SPACING_PERMILLE_INDEX] = lineSpacingPermille;
        result[NATIVE_MARGIN_INDEX] = marginsId;
        result[NATIVE_CONTENT_WIDTH_PERMILLE_INDEX] = contentWidthPermille();
        result[NATIVE_ALIGNMENT_INDEX] = alignmentId;
        result[NATIVE_PUBLISHER_COLORS_INDEX] = publisherColorsId;
        result[NATIVE_REDUCED_MOTION_INDEX] = reducedMotion ? 1 : 0;
        return result;
    }

    static int[] fontSizesSp() {
        return Arrays.copyOf(FONT_SIZES_SP, FONT_SIZES_SP.length);
    }

    static int[] lineSpacingsPermille() {
        return Arrays.copyOf(
            LINE_SPACINGS_PERMILLE, LINE_SPACINGS_PERMILLE.length);
    }

    static String themeCode(int value) {
        switch (value) {
            case THEME_PAPER: return "paper";
            case THEME_SEPIA: return "sepia";
            case THEME_DUSK: return "dusk";
            case THEME_WARM_DARK: return "warm-dark";
            case THEME_OLED: return "oled";
            case THEME_HIGH_CONTRAST: return "high-contrast";
            default: throw new IllegalArgumentException("Unknown theme");
        }
    }

    static String themeLabel(int value) {
        switch (value) {
            case THEME_PAPER: return "Paper";
            case THEME_SEPIA: return "Sepia";
            case THEME_DUSK: return "Dusk";
            case THEME_WARM_DARK: return "Warm dark";
            case THEME_OLED: return "OLED";
            case THEME_HIGH_CONTRAST: return "High contrast";
            default: throw new IllegalArgumentException("Unknown theme");
        }
    }

    static String fontFamilyCode(int value) {
        switch (value) {
            case FONT_FAMILY_LITERARY: return "literary";
            case FONT_FAMILY_CLEAR: return "clear";
            default:
                throw new IllegalArgumentException("Unknown font family");
        }
    }

    static String fontFamilyLabel(int value) {
        switch (value) {
            case FONT_FAMILY_LITERARY: return "Literary";
            case FONT_FAMILY_CLEAR: return "Clear";
            default:
                throw new IllegalArgumentException("Unknown font family");
        }
    }

    static String fontSizeLabel(int value) {
        requireFontSize(value);
        return value + " sp";
    }

    static String lineSpacingLabel(int value) {
        switch (value) {
            case 1150: return "Compact";
            case 1250: return "Classic";
            case 1300: return "Comfortable";
            case 1500: return "Spacious";
            default:
                throw new IllegalArgumentException("Unknown line spacing");
        }
    }

    static String marginsCode(int value) {
        switch (value) {
            case MARGINS_WIDE: return "wide";
            case MARGINS_BALANCED: return "balanced";
            case MARGINS_FOCUSED: return "focused";
            default: throw new IllegalArgumentException("Unknown margins");
        }
    }

    static String marginsLabel(int value) {
        switch (value) {
            case MARGINS_WIDE: return "Wide margins";
            case MARGINS_BALANCED: return "Balanced";
            case MARGINS_FOCUSED: return "Focused width";
            default: throw new IllegalArgumentException("Unknown margins");
        }
    }

    static String alignmentCode(int value) {
        switch (value) {
            case ALIGNMENT_PUBLISHER: return "publisher";
            case ALIGNMENT_RAGGED_RIGHT: return "ragged-right";
            default: throw new IllegalArgumentException("Unknown alignment");
        }
    }

    static String alignmentLabel(int value) {
        switch (value) {
            case ALIGNMENT_PUBLISHER: return "Publisher";
            case ALIGNMENT_RAGGED_RIGHT: return "Ragged right";
            default: throw new IllegalArgumentException("Unknown alignment");
        }
    }

    static String publisherColorsCode(int value) {
        switch (value) {
            case PUBLISHER_COLORS_THEME_SAFE: return "theme-safe";
            case PUBLISHER_COLORS_ALLOW: return "allow";
            default:
                throw new IllegalArgumentException(
                    "Unknown publisher color policy");
        }
    }

    static String publisherColorsLabel(int value) {
        switch (value) {
            case PUBLISHER_COLORS_THEME_SAFE: return "Theme safe";
            case PUBLISHER_COLORS_ALLOW: return "Allow publisher colors";
            default:
                throw new IllegalArgumentException(
                    "Unknown publisher color policy");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OctavoAppearance)) {
            return false;
        }
        OctavoAppearance value = (OctavoAppearance)other;
        return themeId == value.themeId
            && fontFamilyId == value.fontFamilyId
            && fontSizeSp == value.fontSizeSp
            && lineSpacingPermille == value.lineSpacingPermille
            && marginsId == value.marginsId
            && alignmentId == value.alignmentId
            && publisherColorsId == value.publisherColorsId
            && reducedMotion == value.reducedMotion;
    }

    @Override
    public int hashCode() {
        int result = themeId;
        result = 31 * result + fontFamilyId;
        result = 31 * result + fontSizeSp;
        result = 31 * result + lineSpacingPermille;
        result = 31 * result + marginsId;
        result = 31 * result + alignmentId;
        result = 31 * result + publisherColorsId;
        result = 31 * result + (reducedMotion ? 1 : 0);
        return result;
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private static void requireTheme(int value) {
        if (value < 0 || value >= THEME_COUNT) {
            throw new IllegalArgumentException("Unknown theme");
        }
    }

    private static void requireFontFamily(int value) {
        if (value < 0 || value >= FONT_FAMILY_COUNT) {
            throw new IllegalArgumentException("Unknown font family");
        }
    }

    private static void requireFontSize(int value) {
        if (!contains(FONT_SIZES_SP, value)) {
            throw new IllegalArgumentException("Unsupported font size");
        }
    }

    private static void requireLineSpacing(int value) {
        if (!contains(LINE_SPACINGS_PERMILLE, value)) {
            throw new IllegalArgumentException("Unsupported line spacing");
        }
    }

    private static void requireMargins(int value) {
        if (value < 0 || value >= MARGINS_COUNT) {
            throw new IllegalArgumentException("Unknown margins");
        }
    }

    private static void requireAlignment(int value) {
        if (value < 0 || value >= ALIGNMENT_COUNT) {
            throw new IllegalArgumentException("Unknown alignment");
        }
    }

    private static void requirePublisherColors(int value) {
        if (value < 0 || value >= PUBLISHER_COLORS_COUNT) {
            throw new IllegalArgumentException("Unknown publisher colors");
        }
    }
}
