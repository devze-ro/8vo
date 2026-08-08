package ro.devze.octavo;

/** Host-owned Port 7 design tokens. UI0 remains product-neutral. */
final class OctavoDesignTokens {
    static final int UI0_COLOR_ROLE_COUNT = 26;

    static final int SPACE_XXS_DP = 2;
    static final int SPACE_XS_DP = 4;
    static final int SPACE_SM_DP = 8;
    static final int SPACE_MD_DP = 12;
    static final int SPACE_LG_DP = 16;
    static final int SPACE_XL_DP = 24;
    static final int SPACE_XXL_DP = 32;

    static final int RADIUS_SMALL_DP = 8;
    static final int RADIUS_MEDIUM_DP = 14;
    static final int RADIUS_LARGE_DP = 22;
    static final int RADIUS_PILL_DP = 1000;

    static final int TOUCH_TARGET_DP = 48;
    static final int ICON_SIZE_DP = 24;

    static final int MOTION_FAST_MS = 120;
    static final int MOTION_STANDARD_MS = 180;
    static final int MOTION_DELIBERATE_MS = 240;

    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private static final OctavoDesignTokens[] THEMES = {
        new OctavoDesignTokens(
            OctavoAppearance.THEME_PAPER, false,
            0xFFFFFDF9, 0xFF1B1A18,
            0xFFF7F3EA, 0xFF302D28,
            0xFFF2EDE4, 0xFF2B2823,
            0xFFFAF7F0, 0xFF23201C,
            0xFFE7D8CA, 0xFF4E2B18,
            0xFFF6F1E8, 0xFFFFFDF9,
            0xFF1B1A18, 0xFF514B43, 0xFF7A7368,
            0xFFD8CFC2, 0xFFE8E0D5,
            0xFFB25724, 0xFF873D18, 0xFFFFFBF6,
            0xFFF4D8BF, 0xFFF0C96F, 0xFFF5E29A,
            0xFF2F7955, 0xFFA46A17, 0xFFAD3D3D,
            0xFF873D18, 0x990E0C09,
            0xFFEEE7DC, 0xFFFFFFFF,
            0xFFF7F3EA, 0xFFF7F3EA, 0xFFFAF7F0),
        new OctavoDesignTokens(
            OctavoAppearance.THEME_SEPIA, false,
            0xFFF4E7CA, 0xFF332718,
            0xFFE8D5B0, 0xFF3A2A1B,
            0xFFE5D0A9, 0xFF3A2A1B,
            0xFFEEDDBB, 0xFF382A1C,
            0xFFD3AE79, 0xFF4D3013,
            0xFFF1E1C2, 0xFFF8EACE,
            0xFF332718, 0xFF68533B, 0xFF866E50,
            0xFFC9AC7D, 0xFFDECAAA,
            0xFFA94E24, 0xFF7D3418, 0xFFFFF8E9,
            0xFFE6BF8A, 0xFFE3B64F, 0xFFF1D67A,
            0xFF3F7655, 0xFF9A6016, 0xFF96352F,
            0xFF7D3418, 0x991A1208,
            0xFFE1CCA6, 0xFFF8EACE,
            0xFFE8D5B0, 0xFFEEDDBB, 0xFFEEDDBB),
        new OctavoDesignTokens(
            OctavoAppearance.THEME_DUSK, true,
            0xFF273039, 0xFFE4E1D9,
            0xFF1E252C, 0xFFEAE6DE,
            0xFF29333C, 0xFFEAE5DC,
            0xFF222A31, 0xFFE7E4DC,
            0xFF4A3B31, 0xFFF4DECB,
            0xFF303941, 0xFF364049,
            0xFFE4E1D9, 0xFFB9BCC0, 0xFF89939B,
            0xFF53606A, 0xFF3C4852,
            0xFFC89A64, 0xFFA77745, 0xFF1A1510,
            0xFF4E5F72, 0xFF8B6D37, 0xFF6F5F38,
            0xFF6DB58B, 0xFFD2A95E, 0xFFEC958D,
            0xFFA77745, 0xCC080B0E,
            0xFF343E47, 0xFF1B2228,
            0xFF1E252C, 0xFF1A2026, 0xFF222A31),
        new OctavoDesignTokens(
            OctavoAppearance.THEME_WARM_DARK, true,
            0xFF1B1917, 0xFFE8E0D4,
            0xFF12110F, 0xFFE9E0D3,
            0xFF201D1A, 0xFFE9E0D3,
            0xFF171513, 0xFFE5DCCE,
            0xFF453126, 0xFFF0D9C6,
            0xFF24211E, 0xFF2A2622,
            0xFFE8E0D4, 0xFFC2B6A8, 0xFF8E8378,
            0xFF514A43, 0xFF342F2A,
            0xFFC68B5B, 0xFFA66B3E, 0xFF17110D,
            0xFF5A4031, 0xFF72542F, 0xFF5C4B2D,
            0xFF74AE83, 0xFFD0A15E, 0xFFD87870,
            0xFFA66B3E, 0xCC070605,
            0xFF292520, 0xFF201D1A,
            0xFF12110F, 0xFF0F0E0D, 0xFF171513),
        new OctavoDesignTokens(
            OctavoAppearance.THEME_OLED, true,
            0xFF000000, 0xFFDCD8D0,
            0xFF030303, 0xFFE5E1DA,
            0xFF090909, 0xFFE6E2DB,
            0xFF070707, 0xFFE0DDD7,
            0xFF2B2018, 0xFFF2DAC5,
            0xFF0E0E0E, 0xFF151515,
            0xFFDCD8D0, 0xFFA9A6A0, 0xFF74716C,
            0xFF3A3A3A, 0xFF222222,
            0xFFB98755, 0xFF93663E, 0xFF000000,
            0xFF3D3028, 0xFF604922, 0xFF4E4428,
            0xFF62A777, 0xFFC49450, 0xFFD36E68,
            0xFF93663E, 0xDD000000,
            0xFF181818, 0xFF101010,
            0xFF000000, 0xFF000000, 0xFF000000),
        new OctavoDesignTokens(
            OctavoAppearance.THEME_HIGH_CONTRAST, false,
            0xFFFFFFFF, 0xFF000000,
            0xFFFFFFFF, 0xFF000000,
            0xFFFFFFFF, 0xFF000000,
            0xFFFFFFFF, 0xFF000000,
            0xFF0047AB, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF,
            0xFF000000, 0xFF1A1A1A, 0xFF3D3D3D,
            0xFF000000, 0xFF5A5A5A,
            0xFF0047AB, 0xFF002F74, 0xFFFFFFFF,
            0xFFA9D2FF, 0xFFFFC400, 0xFFFFE66D,
            0xFF006B3C, 0xFF7A4A00, 0xFFB00020,
            0xFF002F74, 0xBB000000,
            0xFFE6E6E6, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF),
    };

    final int themeId;
    final boolean darkAppearance;
    final int readerPage;
    final int readerText;
    final int chromeSurface;
    final int chromeText;
    final int settingsSurface;
    final int settingsText;
    final int librarySurface;
    final int libraryText;
    final int libraryReturn;
    final int onLibraryReturn;
    final int sheetSurface;
    final int dialogSurface;
    final int textPrimary;
    final int textSecondary;
    final int textMuted;
    final int divider;
    final int dividerMuted;
    final int accent;
    final int accentPressed;
    final int onAccent;
    final int selection;
    final int search;
    final int highlight;
    final int success;
    final int warning;
    final int error;
    final int focus;
    final int overlay;
    final int buttonSurface;
    final int inputSurface;
    final int statusBar;
    final int navigationBar;
    final int launch;
    private final long paletteHash;

    private OctavoDesignTokens(int themeId,
                               boolean darkAppearance,
                               int readerPage,
                               int readerText,
                               int chromeSurface,
                               int chromeText,
                               int settingsSurface,
                               int settingsText,
                               int librarySurface,
                               int libraryText,
                               int libraryReturn,
                               int onLibraryReturn,
                               int sheetSurface,
                               int dialogSurface,
                               int textPrimary,
                               int textSecondary,
                               int textMuted,
                               int divider,
                               int dividerMuted,
                               int accent,
                               int accentPressed,
                               int onAccent,
                               int selection,
                               int search,
                               int highlight,
                               int success,
                               int warning,
                               int error,
                               int focus,
                               int overlay,
                               int buttonSurface,
                               int inputSurface,
                               int statusBar,
                               int navigationBar,
                               int launch) {
        this.themeId = themeId;
        this.darkAppearance = darkAppearance;
        this.readerPage = readerPage;
        this.readerText = readerText;
        this.chromeSurface = chromeSurface;
        this.chromeText = chromeText;
        this.settingsSurface = settingsSurface;
        this.settingsText = settingsText;
        this.librarySurface = librarySurface;
        this.libraryText = libraryText;
        this.libraryReturn = libraryReturn;
        this.onLibraryReturn = onLibraryReturn;
        this.sheetSurface = sheetSurface;
        this.dialogSurface = dialogSurface;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
        this.divider = divider;
        this.dividerMuted = dividerMuted;
        this.accent = accent;
        this.accentPressed = accentPressed;
        this.onAccent = onAccent;
        this.selection = selection;
        this.search = search;
        this.highlight = highlight;
        this.success = success;
        this.warning = warning;
        this.error = error;
        this.focus = focus;
        this.overlay = overlay;
        this.buttonSurface = buttonSurface;
        this.inputSurface = inputSurface;
        this.statusBar = statusBar;
        this.navigationBar = navigationBar;
        this.launch = launch;
        paletteHash = hashPalette();
    }

    static OctavoDesignTokens forAppearance(OctavoAppearance appearance) {
        return forTheme(appearance == null
                            ? OctavoAppearance.defaults().themeId()
                            : appearance.themeId());
    }

    static OctavoDesignTokens forTheme(int themeId) {
        if (themeId < 0 || themeId >= THEMES.length) {
            throw new IllegalArgumentException("Unknown theme");
        }
        return THEMES[themeId];
    }

    long paletteHash() {
        return paletteHash;
    }

    boolean useLightSystemBarIcons() {
        return darkAppearance;
    }

    int fastMotionMs(OctavoAppearance appearance) {
        return appearance != null && appearance.reducedMotion()
            ? 0 : MOTION_FAST_MS;
    }

    int standardMotionMs(OctavoAppearance appearance) {
        return appearance != null && appearance.reducedMotion()
            ? 0 : MOTION_STANDARD_MS;
    }

    int deliberateMotionMs(OctavoAppearance appearance) {
        return appearance != null && appearance.reducedMotion()
            ? 0 : MOTION_DELIBERATE_MS;
    }

    /**
     * Exact UI0ColorRole order from ui0_tokens.h. A fresh array preserves
     * token immutability across the Java/JNI boundary.
     */
    int[] nativeUi0Colors() {
        return new int[] {
            chromeSurface,   // AppBackground
            sheetSurface,    // SidebarBackground
            readerPage,      // Surface
            dialogSurface,   // SurfaceElevated
            librarySurface,  // SurfaceMuted
            divider,         // Border
            dividerMuted,    // BorderMuted
            textPrimary,     // TextPrimary
            textSecondary,   // TextSecondary
            textMuted,       // TextMuted
            onAccent,        // TextOnFill
            accent,          // Accent
            accentPressed,   // AccentHover
            success,         // Success
            warning,         // Warning
            error,           // Danger
            selection,       // Selection
            highlight,       // Badge
            buttonSurface,   // Button
            inputSurface,    // Input
            focus,           // Focus
            overlay,         // Overlay
            accent,          // ControlPrimary
            accentPressed,   // ControlPrimaryHover
            accentPressed,   // ControlPrimaryActive
            onAccent,        // TextOnPrimary
        };
    }

    /** Product-owned semantic annotation palette; IDs, never ARGB, persist. */
    int[] annotationHighlightColors() {
        switch (themeId) {
            case OctavoAppearance.THEME_PAPER:
                return new int[] {
                    highlight, 0xFFF2C6D5, 0xFFBFDDF2, 0xFFF4C58B
                };
            case OctavoAppearance.THEME_SEPIA:
                return new int[] {
                    highlight, 0xFFE6B8BE, 0xFFB8CEE1, 0xFFE7B46E
                };
            case OctavoAppearance.THEME_DUSK:
                return new int[] {
                    highlight, 0xFF67434D, 0xFF3B5268, 0xFF6B4935
                };
            case OctavoAppearance.THEME_WARM_DARK:
                return new int[] {
                    highlight, 0xFF624147, 0xFF3B4F61, 0xFF68452F
                };
            case OctavoAppearance.THEME_OLED:
                return new int[] {
                    highlight, 0xFF59383D, 0xFF30485C, 0xFF5E3E2A
                };
            case OctavoAppearance.THEME_HIGH_CONTRAST:
                return new int[] {
                    highlight, 0xFFFFB6CF, 0xFFA9D2FF, 0xFFFFD08A
                };
            default:
                throw new IllegalStateException("Unknown highlight palette");
        }
    }

    int annotationHighlightColor(OctavoAnnotationStore.HighlightColor color) {
        if (color == null) {
            throw new IllegalArgumentException("Highlight color is required");
        }
        return annotationHighlightColors()[color.wireId];
    }

    private long hashPalette() {
        long hash = mix(FNV_OFFSET_BASIS, themeId);
        hash = mix(hash, darkAppearance ? 1 : 0);
        hash = mix(hash, readerPage);
        hash = mix(hash, readerText);
        hash = mix(hash, chromeSurface);
        hash = mix(hash, chromeText);
        hash = mix(hash, settingsSurface);
        hash = mix(hash, settingsText);
        hash = mix(hash, librarySurface);
        hash = mix(hash, libraryText);
        hash = mix(hash, libraryReturn);
        hash = mix(hash, onLibraryReturn);
        hash = mix(hash, sheetSurface);
        hash = mix(hash, dialogSurface);
        hash = mix(hash, textPrimary);
        hash = mix(hash, textSecondary);
        hash = mix(hash, textMuted);
        hash = mix(hash, divider);
        hash = mix(hash, dividerMuted);
        hash = mix(hash, accent);
        hash = mix(hash, accentPressed);
        hash = mix(hash, onAccent);
        hash = mix(hash, selection);
        hash = mix(hash, search);
        hash = mix(hash, highlight);
        for (int color : annotationHighlightColors()) {
            hash = mix(hash, color);
        }
        hash = mix(hash, success);
        hash = mix(hash, warning);
        hash = mix(hash, error);
        hash = mix(hash, focus);
        hash = mix(hash, overlay);
        hash = mix(hash, buttonSurface);
        hash = mix(hash, inputSurface);
        hash = mix(hash, statusBar);
        hash = mix(hash, navigationBar);
        return mix(hash, launch);
    }

    private static long mix(long hash, int value) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            hash ^= (value >>> shift) & 0xFFL;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
