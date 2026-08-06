package ro.devze.octavo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class Ui0AndroidThemeSnapshotTest {
    private static final int COLOR_OFFSET = 20;
    private static final int SPACING_OFFSET = 46;
    private static final int RADIUS_OFFSET = 54;
    private static final int TYPOGRAPHY_OFFSET = 61;
    private static final int DENSITY_OFFSET = 77;
    private static final int STATE_OFFSET = 83;
    private static final int DRAW_OFFSET = 104;
    private static final int TREE_OFFSET = 125;
    private static final int CONTROL_OFFSET = 135;
    private static final int SUMMARY_OFFSET = 144;
    private static final int TEXT_INPUT_OFFSET = 148;

    @Test
    public void allSixPalettesResolveExactTypedUi0Snapshots() {
        for (int theme = 0; theme < OctavoAppearance.THEME_COUNT; theme++) {
            OctavoDesignTokens tokens = OctavoDesignTokens.forTheme(theme);
            int[] colors = tokens.nativeUi0Colors();
            int[] packet = OctavoNative.ui0AndroidThemeSnapshot(
                tokens.darkAppearance, colors);
            assertNotNull(packet);
            assertEquals(Ui0AndroidThemeSnapshot.PACKET_LENGTH, packet.length);
            Ui0AndroidThemeSnapshot snapshot =
                Ui0AndroidThemeSnapshot.parse(packet);
            assertNotNull(snapshot);
            assertEquals(
                tokens.darkAppearance
                    ? Ui0AndroidThemeSnapshot.AppearanceKind.DARK
                    : Ui0AndroidThemeSnapshot.AppearanceKind.LIGHT,
                snapshot.appearanceKind());
            assertEquals(91, snapshot.ui0ApiVersion());
            assertEquals(24, snapshot.spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.PAGE_MARGIN));
            assertEquals(16, snapshot.spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.SECTION_GAP));
            assertEquals(8, snapshot.spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.ROW_GAP));
            assertEquals(6, snapshot.spacing(
                Ui0AndroidThemeSnapshot.SpacingRole.CONTROL_GAP));
            for (Ui0AndroidThemeSnapshot.ColorRole role
                    : Ui0AndroidThemeSnapshot.ColorRole.values()) {
                assertEquals(colors[role.ordinal()], snapshot.color(role));
            }
            for (Ui0AndroidThemeSnapshot.DensityRole role
                    : new Ui0AndroidThemeSnapshot.DensityRole[] {
                        Ui0AndroidThemeSnapshot.DensityRole.CONTROL_HEIGHT,
                        Ui0AndroidThemeSnapshot.DensityRole.ICON_BUTTON_SIZE,
                        Ui0AndroidThemeSnapshot.DensityRole.ROW_MIN_HEIGHT,
                        Ui0AndroidThemeSnapshot.DensityRole.MENU_ITEM_HEIGHT
                    }) {
                assertTrue(snapshot.density(role) >= 48);
            }
            assertEquals(48, snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.CONTROL_HEIGHT));
            assertEquals(48, snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.ICON_BUTTON_SIZE));
            assertEquals(48, snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.ROW_MIN_HEIGHT));
            assertEquals(12, snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.CARD_PADDING));
            assertEquals(16, snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.PANEL_PADDING));
            assertEquals(48, snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.MENU_ITEM_HEIGHT));
            assertEquals(6, snapshot.radius(
                Ui0AndroidThemeSnapshot.RadiusRole.PANEL));
            assertEquals(6, snapshot.radius(
                Ui0AndroidThemeSnapshot.RadiusRole.CONTROL));
            assertEquals(48, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.ROW_HEIGHT));
            assertEquals(2, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.ROW_GAP));
            assertEquals(10, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.PADDING_X));
            assertEquals(16, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.TEXT_HEIGHT));
            assertEquals(20, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.INDENT_WIDTH));
            assertEquals(12, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.EXPANDER_SIZE));
            assertEquals(6, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.EXPANDER_GAP));
            assertEquals(3, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_WIDTH));
            assertEquals(7, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_GAP));
            assertEquals(8, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.CHAR_WIDTH));
            assertEquals(8, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X));
            assertEquals(4, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.PADDING_Y));
            assertEquals(10, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.SEGMENT_PADDING_X));
            assertEquals(16, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.INDICATOR_SIZE));
            assertEquals(6, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.INDICATOR_GAP));
            assertEquals(48, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.TOGGLE_WIDTH));
            assertEquals(18, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.TOGGLE_HEIGHT));
            assertEquals(8, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.CHAR_WIDTH));
            assertEquals(16, snapshot.control(
                Ui0AndroidThemeSnapshot.ControlMetric.TEXT_HEIGHT));
            assertEquals(8, snapshot.textInput(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_X));
            assertEquals(5, snapshot.textInput(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y));
            assertEquals(16, snapshot.textInput(
                Ui0AndroidThemeSnapshot.TextInputMetric.TEXT_HEIGHT));
            assertEquals(1, snapshot.textInput(
                Ui0AndroidThemeSnapshot.TextInputMetric.CARET_WIDTH));
            assertEquals(1, snapshot.textInput(
                Ui0AndroidThemeSnapshot.TextInputMetric.MIN_SELECTION_WIDTH));
            assertEquals(8, snapshot.textInput(
                Ui0AndroidThemeSnapshot.TextInputMetric.FALLBACK_CHAR_WIDTH));
            assertEquals(
                snapshot.typographyLineHeight(
                    Ui0AndroidThemeSnapshot.TypographyRole.BODY),
                snapshot.textInput(
                    Ui0AndroidThemeSnapshot.TextInputMetric.TEXT_HEIGHT));
            assertEquals(
                snapshot.typographyCharWidth(
                    Ui0AndroidThemeSnapshot.TypographyRole.BODY),
                snapshot.textInput(
                    Ui0AndroidThemeSnapshot.TextInputMetric.FALLBACK_CHAR_WIDTH));
            assertEquals(
                snapshot.typographyCharWidth(
                    Ui0AndroidThemeSnapshot.TypographyRole.BODY),
                snapshot.tree(Ui0AndroidThemeSnapshot.TreeMetric.CHAR_WIDTH));
            assertEquals(
                snapshot.typographyLineHeight(
                    Ui0AndroidThemeSnapshot.TypographyRole.BODY),
                snapshot.tree(Ui0AndroidThemeSnapshot.TreeMetric.TEXT_HEIGHT));
            assertEquals(
                snapshot.typographyCharWidth(
                    Ui0AndroidThemeSnapshot.TypographyRole.BUTTON),
                snapshot.control(
                    Ui0AndroidThemeSnapshot.ControlMetric.CHAR_WIDTH));
            assertEquals(
                snapshot.typographyLineHeight(
                    Ui0AndroidThemeSnapshot.TypographyRole.BUTTON),
                snapshot.control(
                    Ui0AndroidThemeSnapshot.ControlMetric.TEXT_HEIGHT));
            int hover = snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.HOVERED);
            int active = snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.ACTIVE);
            assertEquals(lerpArgb(hover, active, 1, 2),
                snapshot.treeFocusedFill());
            assertEquals(lerpArgb(hover, active, 3, 4),
                snapshot.treeSelectedFill());
            assertEquals(snapshot.drawBorder(
                Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
                snapshot.treeFocusColor());
            assertEquals(snapshot.treeFocusColor(),
                snapshot.treeCurrentIndicatorFill());
            for (Ui0AndroidThemeSnapshot.StateRole state
                    : Ui0AndroidThemeSnapshot.StateRole.values()) {
                assertEquals(snapshot.color(snapshot.stateFillRole(state)),
                    snapshot.stateFillColor(state));
                assertEquals(snapshot.color(snapshot.stateTextRole(state)),
                    snapshot.stateTextColor(state));
                assertEquals(snapshot.color(snapshot.stateBorderRole(state)),
                    snapshot.stateBorderColor(state));
            }
        }
    }

    @Test
    public void parserOwnsItsPacketAndNativeOwnsCallerColors() {
        OctavoDesignTokens tokens = OctavoDesignTokens.forTheme(
            OctavoAppearance.THEME_PAPER);
        int[] colors = tokens.nativeUi0Colors();
        int[] originalColors = colors.clone();
        int[] packet = OctavoNative.ui0AndroidThemeSnapshot(false, colors);
        assertNotNull(packet);
        Ui0AndroidThemeSnapshot snapshot =
            Ui0AndroidThemeSnapshot.parse(packet);
        assertNotNull(snapshot);

        colors[0] ^= 0x00FFFFFF;
        assertArrayEquals(originalColors,
            java.util.Arrays.copyOfRange(
                snapshot.packetCopy(),
                COLOR_OFFSET,
                COLOR_OFFSET
                    + Ui0AndroidThemeSnapshot.ColorRole.values().length));

        int appBackground = snapshot.color(
            Ui0AndroidThemeSnapshot.ColorRole.APP_BACKGROUND);
        packet[COLOR_OFFSET] ^= 0x00FFFFFF;
        assertEquals(appBackground, snapshot.color(
            Ui0AndroidThemeSnapshot.ColorRole.APP_BACKGROUND));
        int[] copy = snapshot.packetCopy();
        copy[COLOR_OFFSET] ^= 0x00FFFFFF;
        assertEquals(appBackground, snapshot.color(
            Ui0AndroidThemeSnapshot.ColorRole.APP_BACKGROUND));

        int[] transparent = originalColors.clone();
        transparent[Ui0AndroidThemeSnapshot.ColorRole.OVERLAY.ordinal()] = 0;
        Ui0AndroidThemeSnapshot explicitZero = Ui0AndroidThemeSnapshot.parse(
            OctavoNative.ui0AndroidThemeSnapshot(false, transparent));
        assertNotNull(explicitZero);
        assertEquals(0, explicitZero.color(
            Ui0AndroidThemeSnapshot.ColorRole.OVERLAY));
    }

    @Test
    public void nativeAndParserRejectMalformedPackets() {
        assertNull(OctavoNative.ui0AndroidThemeSnapshot(false, null));
        assertNull(OctavoNative.ui0AndroidThemeSnapshot(false, new int[25]));
        assertNull(OctavoNative.ui0AndroidThemeSnapshot(false, new int[27]));
        assertNull(Ui0AndroidThemeSnapshot.parse(null));
        assertNull(Ui0AndroidThemeSnapshot.parse(new int[147]));
        assertNull(Ui0AndroidThemeSnapshot.parse(new int[153]));

        OctavoDesignTokens tokens = OctavoDesignTokens.forTheme(
            OctavoAppearance.THEME_DUSK);
        int[] valid = OctavoNative.ui0AndroidThemeSnapshot(
            tokens.darkAppearance, tokens.nativeUi0Colors());
        assertNotNull(valid);
        assertNotNull(Ui0AndroidThemeSnapshot.parse(valid));

        for (int header = 0; header < 20; header++) {
            int[] malformed = valid.clone();
            if (header == 4) {
                malformed[header] = 2;
            } else if (header == 18 || header == 19) {
                malformed[header] = 1;
            } else {
                malformed[header] ^= 0x40000000;
            }
            assertNull(Ui0AndroidThemeSnapshot.parse(malformed));
        }

        assertMalformed(valid, SPACING_OFFSET, -1);
        assertMalformed(valid, RADIUS_OFFSET, 4097);
        assertMalformed(valid, TYPOGRAPHY_OFFSET, 0);
        assertMalformed(valid, TYPOGRAPHY_OFFSET + 1, 0);
        assertMalformed(valid, DENSITY_OFFSET, 47);
        assertMalformed(valid, DENSITY_OFFSET + 1, 47);
        assertMalformed(valid, DENSITY_OFFSET + 2, 47);
        assertMalformed(valid, DENSITY_OFFSET + 3, -1);
        assertMalformed(valid, DENSITY_OFFSET + 5, 47);
        assertMalformed(valid, STATE_OFFSET, 26);
        assertMalformed(valid, DRAW_OFFSET, valid[DRAW_OFFSET] ^ 1);
        assertMalformed(valid, TREE_OFFSET, 47);
        assertMalformed(valid, TREE_OFFSET + 1, -1);
        assertMalformed(valid, TREE_OFFSET + 3, valid[TREE_OFFSET + 3] + 1);
        assertMalformed(valid, CONTROL_OFFSET, -1);
        assertMalformed(
            valid, CONTROL_OFFSET + 7, valid[CONTROL_OFFSET + 7] + 1);
        for (int summary = 0; summary < 4; summary++) {
            assertMalformed(valid, SUMMARY_OFFSET + summary,
                valid[SUMMARY_OFFSET + summary] ^ 1);
        }
        assertMalformed(valid, TEXT_INPUT_OFFSET, 0);
        assertMalformed(valid, TEXT_INPUT_OFFSET + 1, 0);
        assertMalformed(
            valid, TEXT_INPUT_OFFSET + 2, valid[TEXT_INPUT_OFFSET + 2] + 1);
        assertMalformed(valid, TEXT_INPUT_OFFSET + 3, 0);
        assertMalformed(valid, TEXT_INPUT_OFFSET + 4, 0);
        assertMalformed(
            valid, TEXT_INPUT_OFFSET + 5, valid[TEXT_INPUT_OFFSET + 5] + 1);
    }

    private static void assertMalformed(int[] valid, int index, int value) {
        int[] malformed = valid.clone();
        malformed[index] = value;
        assertNull(Ui0AndroidThemeSnapshot.parse(malformed));
    }

    private static int lerpArgb(
        int from, int to, int numerator, int denominator) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            int channel = start
                + ((end - start) * numerator + denominator / 2) / denominator;
            result |= (channel & 0xFF) << shift;
        }
        return result;
    }
}
