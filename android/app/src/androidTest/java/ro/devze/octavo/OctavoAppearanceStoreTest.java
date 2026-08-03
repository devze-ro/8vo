package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.zip.CRC32;

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
        int[] expectedSizes = {14, 16, 18, 21, 24, 28};
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
        assertEquals(14, defaults.fontSizeSp());
        assertEquals(defaults.withFontSizeSp(16),
                     OctavoAppearance.previousDefault16Sp());
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
        assertEquals(6912, roundTripCount);

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
        assertRejected(() -> createWith(0, 0, 13, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 15, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 29, 1250, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1200, 1, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, -1, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, 3, 0, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, 1, 2, 0));
        assertRejected(() -> createWith(0, 0, 18, 1250, 1, 0, 2));
    }

    @Test
    public void typographyAtlasCacheIsBoundedAndKeyedByTypography() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoTypography.clearCacheForTesting();
        OctavoAppearance defaults = OctavoAppearance.defaults();

        OctavoTypography first =
            OctavoTypography.create(context, defaults);
        assertEquals(1, OctavoTypography.cacheBuildCountForTesting());
        assertEquals(0, OctavoTypography.cacheHitCountForTesting());

        OctavoTypography themed = OctavoTypography.create(
            context,
            defaults.withTheme(OctavoAppearance.THEME_WARM_DARK)
                .withMargins(OctavoAppearance.MARGINS_WIDE));
        assertSame(first, themed);
        assertEquals(1, OctavoTypography.cacheBuildCountForTesting());
        assertEquals(1, OctavoTypography.cacheHitCountForTesting());

        OctavoTypography spaced = OctavoTypography.create(
            context, defaults.withLineSpacingPermille(1300));
        assertNotSame(first, spaced);
        assertEquals(2, OctavoTypography.cacheBuildCountForTesting());
        assertEquals(1, OctavoTypography.cacheHitCountForTesting());

        OctavoTypography spacedAgain = OctavoTypography.create(
            context, defaults.withLineSpacingPermille(1300));
        assertSame(spaced, spacedAgain);
        assertEquals(2, OctavoTypography.cacheBuildCountForTesting());
        assertEquals(2, OctavoTypography.cacheHitCountForTesting());
    }

    @Test
    public void typographyAtlasPublishesBoundedSparsePublicationGlyphs() {
        Context context = ApplicationProvider.getApplicationContext();
        OctavoTypography.clearCacheForTesting();
        OctavoTypography typography = OctavoTypography.create(
            context, OctavoAppearance.defaults());
        int[] metrics = typography.metrics;

        assertEquals(OctavoTypography.MAGIC, metrics[0]);
        assertEquals(OctavoTypography.VERSION, metrics[1]);
        assertEquals(OctavoTypography.FIRST_CODEPOINT, metrics[2]);
        assertEquals(OctavoTypography.GLYPH_COUNT, metrics[3]);
        assertEquals(OctavoTypography.STYLE_COUNT, metrics[4]);
        assertEquals(OctavoTypography.COLUMN_COUNT, metrics[5]);
        assertEquals(OctavoTypography.HEADER_COUNT,
                     OctavoTypography.CODEPOINT_OFFSET);
        assertEquals(
            OctavoTypography.CODEPOINT_OFFSET
                + OctavoTypography.GLYPH_COUNT,
            OctavoTypography.ADVANCE_OFFSET);
        assertEquals(
            OctavoTypography.ADVANCE_OFFSET
                + OctavoTypography.STYLE_COUNT
                    * OctavoTypography.GLYPH_COUNT,
            metrics.length);

        assertTrue("Sparse glyph table exceeded its bounded count",
                   OctavoTypography.GLYPH_COUNT <= 256);
        int previousCodepoint = -1;
        for (int glyph = 0;
             glyph < OctavoTypography.GLYPH_COUNT;
             ++glyph) {
            int codepoint =
                OctavoTypography.codepointForGlyphForTesting(glyph);
            assertEquals(
                codepoint,
                metrics[OctavoTypography.CODEPOINT_OFFSET + glyph]);
            assertTrue("Glyph codepoints must be strictly increasing",
                       codepoint > previousCodepoint);
            previousCodepoint = codepoint;
        }
        assertTrue(
            "Publication glyph table unexpectedly became contiguous",
            previousCodepoint - OctavoTypography.FIRST_CODEPOINT + 1
                > OctavoTypography.GLYPH_COUNT);

        int atlasWidth = metrics[9];
        int atlasHeight = metrics[10];
        int stride = metrics[11];
        assertTrue(atlasWidth > 0 && atlasWidth <= 16384);
        assertTrue(atlasHeight > 0 && atlasHeight <= 16384);
        assertTrue(stride >= atlasWidth);
        assertEquals((long)stride * atlasHeight,
                     (long)typography.alpha.length);
        assertTrue("Typography atlas storage exceeded its 64 MiB bound",
                   typography.alpha.length <= 64 * 1024 * 1024);

        int[] publicationPunctuation = {
            0x2013, // en dash
            0x2014, // em dash
            0x2018, // left single quotation mark
            0x2019, // right single quotation mark / apostrophe
            0x201C, // left double quotation mark
            0x201D, // right double quotation mark
            0x2022, // bullet
            0x2026, // ellipsis
        };
        for (int codepoint : publicationPunctuation) {
            int glyph = findGlyphForCodepoint(metrics, codepoint);
            assertTrue(
                "Missing publication punctuation U+"
                    + Integer.toHexString(codepoint),
                glyph >= 0);
            assertEquals(codepoint,
                         OctavoTypography
                             .codepointForGlyphForTesting(glyph));
            for (int style = 0;
                 style < OctavoTypography.STYLE_COUNT;
                 ++style) {
                int advance = metrics[
                    OctavoTypography.ADVANCE_OFFSET
                        + style * OctavoTypography.GLYPH_COUNT
                        + glyph];
                assertTrue(
                    "Publication punctuation has no positive advance",
                    advance > 0);
                assertGlyphHasInk(typography, glyph, style);
            }
        }

        assertRejected(() ->
            OctavoTypography.codepointForGlyphForTesting(-1));
        assertRejected(() ->
            OctavoTypography.codepointForGlyphForTesting(
                OctavoTypography.GLYPH_COUNT));
    }

    @Test
    public void legacySchemaDefaultFontMigratesWithOtherPreferences()
        throws IOException {
        assertEquals(1,
                     OctavoAppearanceStore.legacyStoreVersionForTesting());
        assertEquals(2,
                     OctavoAppearanceStore.previousStoreVersionForTesting());
        assertEquals(3,
                     OctavoAppearanceStore.currentStoreVersionForTesting());
        assertEquals(1, OctavoAppearance.NATIVE_VERSION);

        OctavoAppearance legacyDefault =
            OctavoAppearance.defaults().withFontSizeSp(18);
        OctavoAppearanceStore migrating =
            new OctavoAppearanceStore(testFilesDirectory);
        assertTrue(migrating.appearanceFileForTesting()
                       .getParentFile().mkdirs());
        byte[] legacyDefaultRecord =
            OctavoAppearanceStore.legacyRecordForTesting(legacyDefault);
        writeFile(migrating.appearanceFileForTesting(), legacyDefaultRecord);

        assertEquals(OctavoAppearance.defaults(), migrating.load());
        assertEquals(14, migrating.current().fontSizeSp());
        assertTrue(migrating.hasPendingMigration());
        assertEquals(1, migrating.loadSuccessCountForTesting());
        assertEquals(0, migrating.loadFailureCountForTesting());
        assertEquals(0, migrating.saveSuccessCountForTesting());
        assertEquals(0, migrating.saveFailureCountForTesting());
        assertEquals(0, migrating.corruptFallbackCountForTesting());
        assertArrayEquals(
            legacyDefaultRecord,
            readFile(migrating.appearanceFileForTesting()));

        assertTrue(migrating.save(migrating.current()));
        assertFalse(migrating.hasPendingMigration());
        assertEquals(1, migrating.saveSuccessCountForTesting());
        byte[] migratedRecord =
            readFile(migrating.appearanceFileForTesting());
        assertEquals(3, recordVersion(migratedRecord));
        assertFalse(java.util.Arrays.equals(
            legacyDefaultRecord, migratedRecord));

        OctavoAppearanceStore migrated =
            new OctavoAppearanceStore(testFilesDirectory);
        assertEquals(OctavoAppearance.defaults(), migrated.load());
        assertEquals(1, migrated.loadSuccessCountForTesting());
        assertFalse(migrated.hasPendingMigration());
        assertEquals(0, migrated.saveSuccessCountForTesting());
        assertEquals(0, migrated.corruptFallbackCountForTesting());

        OctavoAppearance[] schemaDefaultWithOtherPreferences = {
            legacyDefault.withTheme(OctavoAppearance.THEME_SEPIA),
            legacyDefault.withFontFamily(
                OctavoAppearance.FONT_FAMILY_CLEAR),
            legacyDefault.withLineSpacingPermille(1150),
            legacyDefault.withMargins(OctavoAppearance.MARGINS_WIDE),
            legacyDefault.withAlignment(
                OctavoAppearance.ALIGNMENT_RAGGED_RIGHT),
            legacyDefault.withPublisherColors(
                OctavoAppearance.PUBLISHER_COLORS_ALLOW),
            legacyDefault.withReducedMotion(true),
        };
        for (OctavoAppearance legacy : schemaDefaultWithOtherPreferences) {
            OctavoAppearance expected = legacy.withFontSizeSp(14);
            byte[] legacyRecord =
                OctavoAppearanceStore.legacyRecordForTesting(legacy);
            writeFile(migrating.appearanceFileForTesting(), legacyRecord);
            OctavoAppearanceStore migratedPreference =
                new OctavoAppearanceStore(testFilesDirectory);

            assertEquals(expected, migratedPreference.load());
            assertEquals(expected, migratedPreference.current());
            assertTrue(migratedPreference.hasPendingMigration());
            assertEquals(1, migratedPreference.loadSuccessCountForTesting());
            assertEquals(0, migratedPreference.loadFailureCountForTesting());
            assertEquals(0, migratedPreference.saveSuccessCountForTesting());
            assertEquals(0, migratedPreference.saveFailureCountForTesting());
            assertEquals(0, migratedPreference.corruptFallbackCountForTesting());
            assertArrayEquals(
                legacyRecord,
                readFile(migratedPreference.appearanceFileForTesting()));

            assertTrue(migratedPreference.save(migratedPreference.current()));
            assertFalse(migratedPreference.hasPendingMigration());
            assertEquals(1, migratedPreference.saveSuccessCountForTesting());
            assertEquals(3, recordVersion(readFile(
                migratedPreference.appearanceFileForTesting())));
            assertFalse(java.util.Arrays.equals(
                legacyRecord,
                readFile(migratedPreference.appearanceFileForTesting())));
        }

        OctavoAppearance[] preserved = {
            legacyDefault.withFontSizeSp(16),
            legacyDefault.withFontSizeSp(21),
            legacyDefault.withFontSizeSp(24),
            legacyDefault.withFontSizeSp(28),
        };
        for (OctavoAppearance expected : preserved) {
            byte[] legacyRecord =
                OctavoAppearanceStore.legacyRecordForTesting(expected);
            writeFile(migrating.appearanceFileForTesting(), legacyRecord);
            OctavoAppearanceStore preservedStore =
                new OctavoAppearanceStore(testFilesDirectory);

            assertEquals(expected, preservedStore.load());
            assertEquals(expected, preservedStore.current());
            assertFalse(preservedStore.hasPendingMigration());
            assertEquals(1, preservedStore.loadSuccessCountForTesting());
            assertEquals(0, preservedStore.loadFailureCountForTesting());
            assertEquals(0, preservedStore.saveSuccessCountForTesting());
            assertEquals(0, preservedStore.saveFailureCountForTesting());
            assertEquals(0, preservedStore.corruptFallbackCountForTesting());
            assertArrayEquals(
                legacyRecord,
                readFile(preservedStore.appearanceFileForTesting()));
            assertEquals(1, recordVersion(readFile(
                preservedStore.appearanceFileForTesting())));
        }

        OctavoAppearanceStore explicit =
            new OctavoAppearanceStore(testFilesDirectory);
        assertTrue(explicit.save(legacyDefault));
        OctavoAppearanceStore explicitReload =
            new OctavoAppearanceStore(testFilesDirectory);
        assertEquals(legacyDefault, explicitReload.load());
        assertFalse(explicitReload.hasPendingMigration());
        assertEquals(18, explicitReload.current().fontSizeSp());
        assertEquals(0, explicitReload.saveSuccessCountForTesting());
        assertEquals(3, recordVersion(readFile(
            explicitReload.appearanceFileForTesting())));
    }

    @Test
    public void previousSchemaAndTransitionalFontsMigrateFieldWise()
        throws IOException {
        assertEquals(2,
                     OctavoAppearanceStore.previousStoreVersionForTesting());
        assertEquals(3,
                     OctavoAppearanceStore.currentStoreVersionForTesting());
        OctavoAppearance previousDefault =
            OctavoAppearance.previousDefault16Sp();
        /*
         * This is the captured iQOO tuple whose inherited v1 18sp origin was
         * lost when it was republished as v2. No remaining v2 field can
         * distinguish that inheritance from an explicit 18sp choice.
         */
        OctavoAppearance realIqooTransitionalRecord =
            OctavoAppearance.create(
                OctavoAppearance.THEME_SEPIA,
                OctavoAppearance.FONT_FAMILY_LITERARY,
                18,
                1250,
                OctavoAppearance.MARGINS_BALANCED,
                OctavoAppearance.ALIGNMENT_PUBLISHER,
                OctavoAppearance.PUBLISHER_COLORS_THEME_SAFE,
                false);
        OctavoAppearance[] migrationCandidates = {
            previousDefault,
            previousDefault.withTheme(OctavoAppearance.THEME_DUSK),
            previousDefault.withFontFamily(
                OctavoAppearance.FONT_FAMILY_CLEAR),
            previousDefault.withLineSpacingPermille(1150),
            previousDefault.withMargins(OctavoAppearance.MARGINS_WIDE),
            previousDefault.withAlignment(
                OctavoAppearance.ALIGNMENT_RAGGED_RIGHT),
            previousDefault.withPublisherColors(
                OctavoAppearance.PUBLISHER_COLORS_ALLOW),
            previousDefault.withReducedMotion(true),
            realIqooTransitionalRecord,
        };

        OctavoAppearanceStore target =
            new OctavoAppearanceStore(testFilesDirectory);
        assertTrue(target.appearanceFileForTesting()
                       .getParentFile().mkdirs());
        for (OctavoAppearance legacy : migrationCandidates) {
            OctavoAppearance expected = legacy.withFontSizeSp(14);
            byte[] previousRecord =
                OctavoAppearanceStore.previousRecordForTesting(legacy);
            writeFile(target.appearanceFileForTesting(), previousRecord);
            OctavoAppearanceStore loaded =
                new OctavoAppearanceStore(testFilesDirectory);

            assertEquals(expected, loaded.load());
            assertEquals(expected, loaded.current());
            assertTrue(loaded.hasPendingMigration());
            assertEquals(1, loaded.loadSuccessCountForTesting());
            assertEquals(0, loaded.loadFailureCountForTesting());
            assertEquals(0, loaded.saveSuccessCountForTesting());
            assertEquals(0, loaded.saveFailureCountForTesting());
            assertEquals(0, loaded.corruptFallbackCountForTesting());
            assertArrayEquals(
                previousRecord,
                readFile(loaded.appearanceFileForTesting()));

            assertTrue(loaded.save(loaded.current()));
            assertFalse(loaded.hasPendingMigration());
            assertEquals(1, loaded.saveSuccessCountForTesting());
            assertEquals(3, recordVersion(readFile(
                loaded.appearanceFileForTesting())));
            assertFalse(java.util.Arrays.equals(
                previousRecord,
                readFile(loaded.appearanceFileForTesting())));
        }

        OctavoAppearance[] preserved = {
            previousDefault.withFontSizeSp(21),
            previousDefault.withFontSizeSp(24),
            previousDefault.withFontSizeSp(28),
        };
        for (OctavoAppearance expected : preserved) {
            byte[] previousRecord =
                OctavoAppearanceStore.previousRecordForTesting(expected);
            writeFile(target.appearanceFileForTesting(), previousRecord);
            OctavoAppearanceStore loaded =
                new OctavoAppearanceStore(testFilesDirectory);

            assertEquals(expected, loaded.load());
            assertFalse(loaded.hasPendingMigration());
            assertEquals(expected, loaded.current());
            assertEquals(1, loaded.loadSuccessCountForTesting());
            assertEquals(0, loaded.loadFailureCountForTesting());
            assertEquals(0, loaded.saveSuccessCountForTesting());
            assertEquals(0, loaded.saveFailureCountForTesting());
            assertEquals(0, loaded.corruptFallbackCountForTesting());
            assertArrayEquals(
                previousRecord,
                readFile(loaded.appearanceFileForTesting()));
            assertEquals(2, recordVersion(readFile(
                loaded.appearanceFileForTesting())));
        }

        OctavoAppearanceStore explicitV3 =
            new OctavoAppearanceStore(testFilesDirectory);
        assertTrue(explicitV3.save(realIqooTransitionalRecord));
        byte[] explicitV3Record =
            readFile(explicitV3.appearanceFileForTesting());
        assertEquals(3, recordVersion(explicitV3Record));

        OctavoAppearanceStore explicitV3Reload =
            new OctavoAppearanceStore(testFilesDirectory);
        assertEquals(realIqooTransitionalRecord, explicitV3Reload.load());
        assertFalse(explicitV3Reload.hasPendingMigration());
        assertEquals(18, explicitV3Reload.current().fontSizeSp());
        assertEquals(1, explicitV3Reload.loadSuccessCountForTesting());
        assertEquals(0, explicitV3Reload.saveSuccessCountForTesting());
        assertEquals(0, explicitV3Reload.saveFailureCountForTesting());
        assertArrayEquals(
            explicitV3Record,
            readFile(explicitV3Reload.appearanceFileForTesting()));
    }

    @Test
    public void legacyVersionsRejectImpossible14SpRecords()
        throws IOException {
        OctavoAppearanceStore writer =
            new OctavoAppearanceStore(testFilesDirectory);
        assertTrue(writer.save(OctavoAppearance.defaults()));
        byte[] current =
            readFile(writer.appearanceFileForTesting());

        int[] legacyVersions = {
            OctavoAppearanceStore.legacyStoreVersionForTesting(),
            OctavoAppearanceStore.previousStoreVersionForTesting(),
        };
        for (int version : legacyVersions) {
            byte[] impossible =
                retagRecordWithChecksum(current, version);
            writeFile(writer.appearanceFileForTesting(), impossible);
            OctavoAppearanceStore rejected =
                new OctavoAppearanceStore(testFilesDirectory);

            assertEquals(OctavoAppearance.defaults(), rejected.load());
            assertEquals(0, rejected.loadSuccessCountForTesting());
            assertEquals(1, rejected.loadFailureCountForTesting());
            assertEquals(1, rejected.corruptFallbackCountForTesting());
            assertEquals(0, rejected.missingFallbackCountForTesting());
            assertArrayEquals(
                impossible,
                readFile(rejected.appearanceFileForTesting()));
        }
    }

    @Test
    public void failedLegacyDefaultRewritePreservesValidRecordForRetry()
        throws IOException {
        File filesDirectory = new File(
            testFilesDirectory, Integer.toString(2));
        assertTrue(filesDirectory.mkdirs());
        OctavoAppearanceStore store =
            new OctavoAppearanceStore(filesDirectory);
        assertTrue(store.appearanceFileForTesting()
                       .getParentFile().mkdirs());
        OctavoAppearance legacyDefault =
            OctavoAppearance.defaults().withFontSizeSp(18);
        byte[] legacyRecord =
            OctavoAppearanceStore.legacyRecordForTesting(legacyDefault);
        writeFile(store.appearanceFileForTesting(), legacyRecord);
        assertTrue(store.temporaryFileForTesting().mkdir());

        assertEquals(OctavoAppearance.defaults(), store.load());
        assertEquals(14, store.current().fontSizeSp());
        assertTrue(store.hasPendingMigration());
        assertEquals(1, store.loadSuccessCountForTesting());
        assertEquals(0, store.loadFailureCountForTesting());
        assertEquals(0, store.saveSuccessCountForTesting());
        assertEquals(0, store.saveFailureCountForTesting());
        assertEquals(0, store.corruptFallbackCountForTesting());
        assertArrayEquals(legacyRecord,
                          readFile(store.appearanceFileForTesting()));

        assertFalse(store.save(store.current()));
        assertTrue(store.hasPendingMigration());
        assertEquals(0, store.saveSuccessCountForTesting());
        assertEquals(1, store.saveFailureCountForTesting());
        assertArrayEquals(legacyRecord,
                          readFile(store.appearanceFileForTesting()));

        assertTrue(store.temporaryFileForTesting().delete());
        assertTrue(store.save(store.current()));
        assertFalse(store.hasPendingMigration());
        assertEquals(1, store.saveSuccessCountForTesting());
        assertEquals(1, store.saveFailureCountForTesting());
        assertEquals(3, recordVersion(readFile(
            store.appearanceFileForTesting())));
        assertEquals(0, store.corruptFallbackCountForTesting());
        assertFalse(store.temporaryFileForTesting().exists());

        OctavoAppearanceStore reloaded =
            new OctavoAppearanceStore(filesDirectory);
        assertEquals(OctavoAppearance.defaults(), reloaded.load());
        assertFalse(reloaded.hasPendingMigration());
        assertEquals(0, reloaded.saveSuccessCountForTesting());
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
        assertEquals(14, missing.current().fontSizeSp());
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
        assertEquals(3, recordVersion(published));
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
        assertEquals(14, corrupt.current().fontSizeSp());
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
        assertEquals(14, oversized.current().fontSizeSp());
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

    private static int findGlyphForCodepoint(int[] metrics,
                                             int codepoint) {
        for (int glyph = 0;
             glyph < OctavoTypography.GLYPH_COUNT;
             ++glyph) {
            if (metrics[OctavoTypography.CODEPOINT_OFFSET + glyph]
                == codepoint) {
                return glyph;
            }
        }
        return -1;
    }

    private static void assertGlyphHasInk(OctavoTypography typography,
                                          int glyph,
                                          int style) {
        int[] metrics = typography.metrics;
        int columns = metrics[5];
        int rowsPerStyle = metrics[6];
        int cellWidth = metrics[7];
        int cellHeight = metrics[8];
        int stride = metrics[11];
        int column = glyph % columns;
        int row = style * rowsPerStyle + glyph / columns;
        int left = column * cellWidth;
        int top = row * cellHeight;
        boolean hasInk = false;
        for (int y = top; y < top + cellHeight && !hasInk; ++y) {
            for (int x = left; x < left + cellWidth; ++x) {
                if ((typography.alpha[y * stride + x] & 0xFF) != 0) {
                    hasInk = true;
                    break;
                }
            }
        }
        assertTrue("Publication punctuation glyph has no atlas ink",
                   hasInk);
    }

    private static byte[] retagRecordWithChecksum(byte[] bytes,
                                                   int version) {
        assertNotNull(bytes);
        assertTrue(bytes.length >= 2 * Integer.BYTES);
        byte[] result = bytes.clone();
        ByteBuffer buffer = ByteBuffer.wrap(result)
            .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(Integer.BYTES, version);
        int checksumOffset = result.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(result, 0, checksumOffset);
        buffer.putInt(checksumOffset, (int)checksum.getValue());
        return result;
    }

    private static int recordVersion(byte[] bytes) {
        assertNotNull(bytes);
        assertTrue(bytes.length >= 2 * Integer.BYTES);
        return ByteBuffer.wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN)
            .getInt(Integer.BYTES);
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
