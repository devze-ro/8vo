package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
public final class OctavoAppearanceStoreTest {
    private static final int UI0_OVERLAY_COLOR_INDEX = 21;

    private File testFilesDirectory;

    @Before
    public void createIsolatedFilesDirectory() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context.getCacheDir());
        testFilesDirectory = new File(
            context.getCacheDir(),
            "octavo-appearance-store-" + System.nanoTime());
        assertFalse(testFilesDirectory.exists());
        assertTrue(testFilesDirectory.mkdirs());
    }

    @After
    public void removeIsolatedFilesDirectory() {
        assertTrue(deleteTree(testFilesDirectory));
    }

    @Test
    public void palettesAreOpaqueDistinctAndRespectThemeInvariants() {
        assertEquals(6, OctavoAppearance.THEME_COUNT);
        assertSame(OctavoDesignTokens.forTheme(OctavoAppearance.THEME_PAPER),
                   OctavoDesignTokens.forAppearance(null));

        boolean[] expectedDark = {
            false, false, true, true, true, false
        };
        HashSet<Long> hashes = new HashSet<>();
        for (int theme = 0;
             theme < OctavoAppearance.THEME_COUNT;
             ++theme) {
            OctavoAppearance appearance =
                OctavoAppearance.defaults().withTheme(theme);
            OctavoDesignTokens tokens =
                OctavoDesignTokens.forAppearance(appearance);
            assertSame(tokens, OctavoDesignTokens.forTheme(theme));
            assertEquals(theme, tokens.themeId);
            assertEquals(expectedDark[theme], tokens.darkAppearance);
            assertEquals(expectedDark[theme],
                         tokens.useLightSystemBarIcons());
            assertTrue("Palette hash must be nonzero",
                       tokens.paletteHash() != 0L);
            assertTrue("Every theme needs a unique palette hash",
                       hashes.add(tokens.paletteHash()));
            assertOpaquePalette(tokens);
            assertContrastAtLeast(
                "reader text", tokens.readerText, tokens.readerPage, 7.0);
            assertContrastAtLeast(
                "chrome text", tokens.chromeText, tokens.chromeSurface, 7.0);
            assertContrastAtLeast(
                "settings text",
                tokens.settingsText,
                tokens.settingsSurface,
                7.0);
            assertContrastAtLeast(
                "library text",
                tokens.libraryText,
                tokens.librarySurface,
                7.0);
            assertContrastAtLeast(
                "library return",
                tokens.onLibraryReturn,
                tokens.libraryReturn,
                4.5);
            assertContrastAtLeast(
                "settings secondary text",
                tokens.textSecondary,
                tokens.settingsSurface,
                4.5);
            assertContrastAtLeast(
                "library secondary text",
                tokens.textSecondary,
                tokens.librarySurface,
                4.5);
            assertContrastAtLeast(
                "failure text",
                tokens.error,
                tokens.dialogSurface,
                4.5);
            assertContrastAtLeast(
                "remove action",
                tokens.error,
                tokens.buttonSurface,
                4.5);
            assertContrastAtLeast(
                "unchecked control",
                tokens.textMuted,
                tokens.settingsSurface,
                3.0);
            assertContrastAtLeast(
                "accent control",
                tokens.accent,
                tokens.settingsSurface,
                3.0);
            assertContrastAtLeast(
                "focus indicator",
                tokens.focus,
                tokens.settingsSurface,
                3.0);

            int[] nativeColors = tokens.nativeUi0Colors();
            assertEquals(OctavoDesignTokens.UI0_COLOR_ROLE_COUNT,
                         nativeColors.length);
            for (int index = 0; index < nativeColors.length; ++index) {
                if (index == UI0_OVERLAY_COLOR_INDEX) {
                    assertTranslucent(nativeColors[index]);
                } else {
                    assertOpaque(nativeColors[index]);
                }
            }
        }
        assertEquals(OctavoAppearance.THEME_COUNT, hashes.size());

        OctavoDesignTokens warm = OctavoDesignTokens.forTheme(
            OctavoAppearance.THEME_WARM_DARK);
        assertEquals(0xFF1B1917, warm.readerPage);
        assertEquals(0xFFE8E0D4, warm.readerText);
        assertNotEquals(0xFF000000, warm.readerPage);
        assertTrue(warm.darkAppearance);

        OctavoDesignTokens oled = OctavoDesignTokens.forTheme(
            OctavoAppearance.THEME_OLED);
        assertEquals(0xFF000000, oled.readerPage);
        assertEquals(0xFF000000, oled.statusBar);
        assertEquals(0xFF000000, oled.navigationBar);
        assertEquals(0xFF000000, oled.launch);
        assertNotEquals(0xFFFFFFFF, oled.readerText);
        assertTrue(oled.darkAppearance);

        OctavoDesignTokens highContrast = OctavoDesignTokens.forTheme(
            OctavoAppearance.THEME_HIGH_CONTRAST);
        assertEquals(0xFFFFFFFF, highContrast.readerPage);
        assertEquals(0xFF000000, highContrast.readerText);
        assertFalse(highContrast.darkAppearance);

        OctavoAppearance reduced =
            OctavoAppearance.defaults().withReducedMotion(true);
        assertTrue(OctavoDesignTokens.MOTION_FAST_MS > 0);
        assertTrue(OctavoDesignTokens.MOTION_STANDARD_MS > 0);
        assertTrue(OctavoDesignTokens.MOTION_DELIBERATE_MS > 0);
        assertEquals(0, warm.fastMotionMs(reduced));
        assertEquals(0, warm.standardMotionMs(reduced));
        assertEquals(0, warm.deliberateMotionMs(reduced));
        assertEquals(48, OctavoDesignTokens.TOUCH_TARGET_DP);
        assertEquals(24, OctavoDesignTokens.ICON_SIZE_DP);

        assertRejected(() -> OctavoDesignTokens.forTheme(-1));
        assertRejected(() ->
            OctavoDesignTokens.forTheme(OctavoAppearance.THEME_COUNT));
    }

    @Test
    public void appearanceConfigRoundTripsAndRejectsUnsupportedExtrema() {
        int[] expectedSizes = {16, 18, 21, 24, 28};
        int[] expectedSpacings = {1150, 1250, 1300, 1500};
        assertArrayEquals(expectedSizes, OctavoAppearance.fontSizesSp());
        assertArrayEquals(expectedSpacings,
                          OctavoAppearance.lineSpacingsPermille());

        int[] mutableSizes = OctavoAppearance.fontSizesSp();
        mutableSizes[0] = 999;
        assertArrayEquals(expectedSizes, OctavoAppearance.fontSizesSp());
        int[] mutableSpacings = OctavoAppearance.lineSpacingsPermille();
        mutableSpacings[0] = 999;
        assertArrayEquals(expectedSpacings,
                          OctavoAppearance.lineSpacingsPermille());

        OctavoAppearance defaults = OctavoAppearance.defaults();
        assertEquals(OctavoAppearance.THEME_PAPER, defaults.themeId());
        assertEquals(OctavoAppearance.FONT_FAMILY_LITERARY,
                     defaults.fontFamilyId());
        assertSame(Typeface.SERIF, defaults.systemTypeface());
        assertEquals(18, defaults.fontSizeSp());
        assertEquals(1250, defaults.lineSpacingPermille());
        assertEquals(OctavoAppearance.MARGINS_BALANCED,
                     defaults.marginsId());
        assertEquals(860, defaults.contentWidthPermille());
        assertEquals(OctavoAppearance.ALIGNMENT_PUBLISHER,
                     defaults.alignmentId());
        assertEquals(OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE,
                     defaults.publisherColorsId());
        assertFalse(defaults.reducedMotion());
        assertSame(Typeface.SANS_SERIF,
                   defaults.withFontFamily(
                       OctavoAppearance.FONT_FAMILY_CLEAR).systemTypeface());

        int roundTripCount = 0;
        for (int theme = 0;
             theme < OctavoAppearance.THEME_COUNT;
             ++theme) {
            for (int family = 0;
                 family < OctavoAppearance.FONT_FAMILY_COUNT;
                 ++family) {
                for (int size : expectedSizes) {
                    for (int spacing : expectedSpacings) {
                        for (int margins = 0;
                             margins < OctavoAppearance.MARGINS_COUNT;
                             ++margins) {
                            for (int alignment = 0;
                                 alignment
                                     < OctavoAppearance.ALIGNMENT_COUNT;
                                 ++alignment) {
                                for (int publisher = 0;
                                     publisher
                                         < OctavoAppearance
                                             .PUBLISHER_COLORS_COUNT;
                                     ++publisher) {
                                    for (int reduced = 0;
                                         reduced <= 1;
                                         ++reduced) {
                                        OctavoAppearance appearance =
                                            OctavoAppearance.create(
                                                theme,
                                                family,
                                                size,
                                                spacing,
                                                margins,
                                                alignment,
                                                publisher,
                                                reduced == 1);
                                        int[] config =
                                            appearance.nativeConfig();
                                        assertEquals(
                                            OctavoAppearance
                                                .NATIVE_FIELD_COUNT,
                                            config.length);
                                        assertEquals(
                                            OctavoAppearance.NATIVE_MAGIC,
                                            config[OctavoAppearance
                                                .NATIVE_MAGIC_INDEX]);
                                        assertEquals(
                                            OctavoAppearance.NATIVE_VERSION,
                                            config[OctavoAppearance
                                                .NATIVE_VERSION_INDEX]);
                                        OctavoAppearance decoded =
                                            OctavoAppearance
                                                .fromNativeConfig(config);
                                        assertNotNull(decoded);
                                        assertEquals(appearance, decoded);
                                        assertEquals(appearance.hashCode(),
                                                     decoded.hashCode());
                                        roundTripCount += 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(5760, roundTripCount);

        OctavoAppearance maximum = OctavoAppearance.create(
            OctavoAppearance.THEME_OLED,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            28,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);
        int[] mutated = maximum.nativeConfig();
        mutated[OctavoAppearance.NATIVE_THEME_INDEX] =
            OctavoAppearance.THEME_PAPER;
        assertEquals(OctavoAppearance.THEME_OLED,
                     maximum.themeId());
        assertEquals(OctavoAppearance.THEME_OLED,
                     maximum.nativeConfig()[
                         OctavoAppearance.NATIVE_THEME_INDEX]);
        assertNotEquals(defaults, maximum);

        assertNull(OctavoAppearance.fromNativeConfig(null));
        assertInvalidNativeConfig(
            OctavoAppearance.NATIVE_MAGIC_INDEX, 0);
        assertInvalidNativeConfig(
            OctavoAppearance.NATIVE_VERSION_INDEX, 2);
        assertInvalidNativeConfig(
            OctavoAppearance.NATIVE_CONTENT_WIDTH_PERMILLE_INDEX, 721);
        assertInvalidNativeConfig(
            OctavoAppearance.NATIVE_REDUCED_MOTION_INDEX, 2);

        assertRejected(() -> createWith(-1, 0, 18, 1250, 1, 0, 0));
        assertRejected(() -> createWith(6, 0, 18, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, -1, 18, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 2, 18, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 15, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 29, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1200, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, -1, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, 3, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, 1, 2, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, 1, 0, 2));
    }

    @Test
    public void globalStoreDefaultsPublishesReloadsAndRejectsCorruption()
        throws IOException {
        OctavoAppearanceStore missing =
            new OctavoAppearanceStore(testFilesDirectory);
        File expected = new File(
            new File(testFilesDirectory, "port7"), "appearance.v1");
        assertEquals(expected.getCanonicalFile(),
                     missing.appearanceFileForTesting().getCanonicalFile());
        assertEquals(60, OctavoAppearanceStore.recordBytesForTesting());
        assertTrue(OctavoAppearanceStore.recordBytesForTesting()
                   <= OctavoAppearanceStore.maximumFileBytesForTesting());
        assertEquals(OctavoAppearance.defaults(), missing.current());
        assertEquals(OctavoAppearance.defaults(), missing.load());
        assertEquals(1, missing.missingFallbackCountForTesting());
        assertEquals(0, missing.corruptFallbackCountForTesting());
        assertEquals(0, missing.loadSuccessCountForTesting());
        assertEquals(0, missing.loadFailureCountForTesting());
        assertFalse(missing.appearanceFileForTesting().exists());
        assertFalse(missing.temporaryFileForTesting().exists());

        OctavoAppearance saved = OctavoAppearance.create(
            OctavoAppearance.THEME_OLED,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            28,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);
        assertTrue(missing.save(saved));
        assertEquals(saved, missing.current());
        assertEquals(1, missing.saveSuccessCountForTesting());
        assertEquals(0, missing.saveFailureCountForTesting());
        assertTrue(missing.appearanceFileForTesting().isFile());
        assertEquals(OctavoAppearanceStore.recordBytesForTesting(),
                     missing.appearanceFileForTesting().length());
        assertFalse(missing.temporaryFileForTesting().exists());
        assertOnlyPublishedRecord(missing);

        byte[] published = readFile(missing.appearanceFileForTesting());
        assertFalse(missing.save(null));
        assertEquals(saved, missing.current());
        assertEquals(1, missing.saveSuccessCountForTesting());
        assertEquals(1, missing.saveFailureCountForTesting());
        assertArrayEquals(published,
                          readFile(missing.appearanceFileForTesting()));
        assertFalse(missing.temporaryFileForTesting().exists());
        assertOnlyPublishedRecord(missing);

        OctavoAppearance replacement =
            saved.withTheme(OctavoAppearance.THEME_WARM_DARK);
        assertTrue(missing.temporaryFileForTesting().mkdir());
        assertFalse(missing.save(replacement));
        assertEquals(saved, missing.current());
        assertEquals(1, missing.saveSuccessCountForTesting());
        assertEquals(2, missing.saveFailureCountForTesting());
        assertArrayEquals(published,
                          readFile(missing.appearanceFileForTesting()));
        assertTrue(missing.temporaryFileForTesting().isDirectory());
        assertTrue(missing.temporaryFileForTesting().delete());
        assertFalse(missing.temporaryFileForTesting().exists());
        assertOnlyPublishedRecord(missing);

        OctavoAppearanceStore reloaded =
            new OctavoAppearanceStore(testFilesDirectory);
        assertEquals(saved, reloaded.load());
        assertEquals(saved, reloaded.current());
        assertEquals(1, reloaded.loadSuccessCountForTesting());
        assertEquals(0, reloaded.loadFailureCountForTesting());
        assertEquals(0, reloaded.missingFallbackCountForTesting());
        assertEquals(0, reloaded.corruptFallbackCountForTesting());
        assertFalse(reloaded.temporaryFileForTesting().exists());

        byte[] corruptChecksum = published.clone();
        corruptChecksum[corruptChecksum.length - 1] ^= 0x01;
        writeFile(reloaded.appearanceFileForTesting(), corruptChecksum);
        OctavoAppearanceStore corrupt =
            new OctavoAppearanceStore(testFilesDirectory);
        assertEquals(OctavoAppearance.defaults(), corrupt.load());
        assertEquals(OctavoAppearance.defaults(), corrupt.current());
        assertEquals(0, corrupt.loadSuccessCountForTesting());
        assertEquals(1, corrupt.loadFailureCountForTesting());
        assertEquals(0, corrupt.missingFallbackCountForTesting());
        assertEquals(1, corrupt.corruptFallbackCountForTesting());
        assertFalse(corrupt.temporaryFileForTesting().exists());

        writeFile(
            corrupt.appearanceFileForTesting(),
            new byte[
                OctavoAppearanceStore.maximumFileBytesForTesting() + 1]);
        OctavoAppearanceStore oversized =
            new OctavoAppearanceStore(testFilesDirectory);
        assertEquals(OctavoAppearance.defaults(), oversized.load());
        assertEquals(0, oversized.loadSuccessCountForTesting());
        assertEquals(1, oversized.loadFailureCountForTesting());
        assertEquals(0, oversized.missingFallbackCountForTesting());
        assertEquals(1, oversized.corruptFallbackCountForTesting());
        assertFalse(oversized.temporaryFileForTesting().exists());
    }

    private static OctavoAppearance createWith(int theme,
                                               int family,
                                               int size,
                                               int spacing,
                                               int margins,
                                               int alignment,
                                               int publisher) {
        return OctavoAppearance.create(
            theme,
            family,
            size,
            spacing,
            margins,
            alignment,
            publisher,
            false);
    }

    private static void assertInvalidNativeConfig(int index, int value) {
        int[] config = OctavoAppearance.defaults().nativeConfig();
        config[index] = value;
        assertNull(OctavoAppearance.fromNativeConfig(config));
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
    }

    private static void assertOpaquePalette(OctavoDesignTokens tokens) {
        assertOpaque(tokens.readerPage);
        assertOpaque(tokens.readerText);
        assertOpaque(tokens.chromeSurface);
        assertOpaque(tokens.chromeText);
        assertOpaque(tokens.settingsSurface);
        assertOpaque(tokens.settingsText);
        assertOpaque(tokens.librarySurface);
        assertOpaque(tokens.libraryText);
        assertOpaque(tokens.libraryReturn);
        assertOpaque(tokens.onLibraryReturn);
        assertOpaque(tokens.sheetSurface);
        assertOpaque(tokens.dialogSurface);
        assertOpaque(tokens.textPrimary);
        assertOpaque(tokens.textSecondary);
        assertOpaque(tokens.textMuted);
        assertOpaque(tokens.divider);
        assertOpaque(tokens.dividerMuted);
        assertOpaque(tokens.accent);
        assertOpaque(tokens.accentPressed);
        assertOpaque(tokens.onAccent);
        assertOpaque(tokens.selection);
        assertOpaque(tokens.search);
        assertOpaque(tokens.highlight);
        assertOpaque(tokens.success);
        assertOpaque(tokens.warning);
        assertOpaque(tokens.error);
        assertOpaque(tokens.focus);
        assertTranslucent(tokens.overlay);
        assertOpaque(tokens.buttonSurface);
        assertOpaque(tokens.inputSurface);
        assertOpaque(tokens.statusBar);
        assertOpaque(tokens.navigationBar);
        assertOpaque(tokens.launch);
    }

    private static void assertOpaque(int color) {
        assertEquals(0xFF, color >>> 24);
    }

    private static void assertTranslucent(int color) {
        int alpha = color >>> 24;
        assertTrue(alpha > 0 && alpha < 0xFF);
    }

    private static void assertContrastAtLeast(String label,
                                              int foreground,
                                              int background,
                                              double minimum) {
        double foregroundLuminance = luminance(foreground);
        double backgroundLuminance = luminance(background);
        double ratio =
            (Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
            / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
        assertTrue(label + " contrast was " + ratio, ratio >= minimum);
    }

    private static double luminance(int color) {
        return 0.2126 * linearChannel(Color.red(color))
            + 0.7152 * linearChannel(Color.green(color))
            + 0.0722 * linearChannel(Color.blue(color));
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045
            ? value / 12.92
            : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void assertOnlyPublishedRecord(
        OctavoAppearanceStore store) {
        File root = store.appearanceFileForTesting().getParentFile();
        assertNotNull(root);
        File[] files = root.listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        assertEquals(store.appearanceFileForTesting(), files[0]);
    }

    private static byte[] readFile(File file) throws IOException {
        assertTrue(file.isFile());
        assertTrue(file.length() >= 0 && file.length() <= Integer.MAX_VALUE);
        byte[] bytes = new byte[(int)file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    offset += count;
                }
            }
            assertEquals(-1, input.read());
        }
        assertEquals(bytes.length, offset);
        return bytes;
    }

    private static void writeFile(File file, byte[] bytes)
        throws IOException {
        assertNotNull(file);
        assertNotNull(bytes);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static boolean deleteTree(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return false;
            }
            for (File child : children) {
                if (!deleteTree(child)) {
                    return false;
                }
            }
        }
        return file.delete() || !file.exists();
    }
}
