package ro.devze.octavo;

/** Immutable, validated native-Android projection of the pinned UI0 theme. */
final class Ui0AndroidThemeSnapshot {
    static final int MAGIC = 0x4F553941;
    static final int VERSION = 1;
    static final int UI0_API_VERSION = 91;
    static final int PACKET_LENGTH = 154;

    enum AppearanceKind { LIGHT, DARK }
    enum ColorRole {
        APP_BACKGROUND, SIDEBAR_BACKGROUND, SURFACE, SURFACE_ELEVATED,
        SURFACE_MUTED, BORDER, BORDER_MUTED, TEXT_PRIMARY, TEXT_SECONDARY,
        TEXT_MUTED, TEXT_ON_FILL, ACCENT, ACCENT_HOVER, SUCCESS, WARNING,
        DANGER, SELECTION, BADGE, BUTTON, INPUT, FOCUS, OVERLAY,
        CONTROL_PRIMARY, CONTROL_PRIMARY_HOVER, CONTROL_PRIMARY_ACTIVE,
        TEXT_ON_PRIMARY
    }
    enum SpacingRole {
        PAGE_MARGIN, SECTION_GAP, CARD_GAP, ROW_GAP, TOOLBAR_GAP, CONTROL_GAP,
        MAJOR_GROUP_GAP, TEXT_STACK_GAP
    }
    enum RadiusRole { PANEL, CARD, CONTROL, MENU, INPUT, BADGE, PILL }
    enum TypographyRole {
        PAGE_TITLE, SECTION_TITLE, BODY, CAPTION, METADATA, BUTTON, MONO,
        READER_CHROME
    }
    enum DensityRole {
        CONTROL_HEIGHT, ICON_BUTTON_SIZE, ROW_MIN_HEIGHT, CARD_PADDING,
        PANEL_PADDING, MENU_ITEM_HEIGHT
    }
    enum StateRole {
        DEFAULT, HOVERED, ACTIVE, SELECTED, DISABLED, FOCUSED, DESTRUCTIVE
    }
    enum DrawState {
        DEFAULT, HOVERED, ACTIVE, SELECTED, DISABLED, FOCUSED, DESTRUCTIVE
    }
    enum TreeMetric {
        ROW_HEIGHT, ROW_GAP, PADDING_X, TEXT_HEIGHT, INDENT_WIDTH,
        EXPANDER_SIZE, EXPANDER_GAP, CURRENT_BAR_WIDTH, CURRENT_BAR_GAP,
        CHAR_WIDTH
    }
    enum ControlMetric {
        PADDING_X, PADDING_Y, SEGMENT_PADDING_X, INDICATOR_SIZE, INDICATOR_GAP,
        TOGGLE_WIDTH, TOGGLE_HEIGHT, CHAR_WIDTH, TEXT_HEIGHT
    }
    enum TextInputMetric {
        PADDING_X, PADDING_Y, TEXT_HEIGHT, CARET_WIDTH, MIN_SELECTION_WIDTH,
        FALLBACK_CHAR_WIDTH
    }

    private static final int HEADER_COUNT = 20;
    private static final int COLOR_OFFSET = HEADER_COUNT;
    private static final int SPACING_OFFSET = COLOR_OFFSET + ColorRole.values().length;
    private static final int RADIUS_OFFSET = SPACING_OFFSET + SpacingRole.values().length;
    private static final int TYPOGRAPHY_OFFSET = RADIUS_OFFSET + RadiusRole.values().length;
    private static final int TYPOGRAPHY_STRIDE = 2;
    private static final int DENSITY_OFFSET = TYPOGRAPHY_OFFSET
        + TypographyRole.values().length * TYPOGRAPHY_STRIDE;
    private static final int STATE_OFFSET = DENSITY_OFFSET + DensityRole.values().length;
    private static final int STATE_STRIDE = 3;
    private static final int DRAW_OFFSET = STATE_OFFSET
        + StateRole.values().length * STATE_STRIDE;
    private static final int DRAW_STRIDE = 3;
    private static final int TREE_OFFSET = DRAW_OFFSET
        + DrawState.values().length * DRAW_STRIDE;
    private static final int CONTROL_OFFSET = TREE_OFFSET + TreeMetric.values().length;
    private static final int SUMMARY_OFFSET = CONTROL_OFFSET + ControlMetric.values().length;
    private static final int SUMMARY_COUNT = 4;
    private static final int TEXT_INPUT_OFFSET = SUMMARY_OFFSET + SUMMARY_COUNT;
    private static final int MAX_METRIC = 4096;

    private final int[] packet;

    private Ui0AndroidThemeSnapshot(int[] packet) {
        this.packet = packet;
    }

    static Ui0AndroidThemeSnapshot parse(int[] source) {
        if (source == null
                || source.length != PACKET_LENGTH
                || TEXT_INPUT_OFFSET + TextInputMetric.values().length
                    != PACKET_LENGTH) {
            return null;
        }
        int[] packet = source.clone();
        if (packet[0] != MAGIC
                || packet[1] != VERSION
                || packet[2] != UI0_API_VERSION
                || packet[3] != PACKET_LENGTH
                || packet[4] < 0
                || packet[4] >= AppearanceKind.values().length
                || packet[5] != ColorRole.values().length
                || packet[6] != SpacingRole.values().length
                || packet[7] != RadiusRole.values().length
                || packet[8] != TypographyRole.values().length
                || packet[9] != TYPOGRAPHY_STRIDE
                || packet[10] != DensityRole.values().length
                || packet[11] != StateRole.values().length
                || packet[12] != STATE_STRIDE
                || packet[13] != DrawState.values().length
                || packet[14] != DRAW_STRIDE
                || packet[15] != TreeMetric.values().length
                || packet[16] != ControlMetric.values().length
                || packet[17] != SUMMARY_COUNT
                || packet[18] != TextInputMetric.values().length
                || packet[19] != 0) {
            return null;
        }
        if (!validMetrics(packet, SPACING_OFFSET, SpacingRole.values().length, false)
                || !validMetrics(packet, RADIUS_OFFSET, RadiusRole.values().length, false)
                || !validMetrics(packet, DENSITY_OFFSET, DensityRole.values().length, false)
                || !validMetrics(packet, TREE_OFFSET, TreeMetric.values().length, false)
                || !validMetrics(packet, CONTROL_OFFSET, ControlMetric.values().length, false)
                || !validMetrics(packet, TEXT_INPUT_OFFSET,
                    TextInputMetric.values().length, true)) {
            return null;
        }
        for (TypographyRole role : TypographyRole.values()) {
            int offset = TYPOGRAPHY_OFFSET + role.ordinal() * TYPOGRAPHY_STRIDE;
            if (!validMetric(packet[offset], true)
                    || !validMetric(packet[offset + 1], true)) {
                return null;
            }
        }
        if (packet[DENSITY_OFFSET + DensityRole.CONTROL_HEIGHT.ordinal()] < 48
                || packet[DENSITY_OFFSET + DensityRole.ICON_BUTTON_SIZE.ordinal()] < 48
                || packet[DENSITY_OFFSET + DensityRole.ROW_MIN_HEIGHT.ordinal()] < 48
                || packet[DENSITY_OFFSET + DensityRole.MENU_ITEM_HEIGHT.ordinal()] < 48
                || packet[TREE_OFFSET + TreeMetric.ROW_HEIGHT.ordinal()] < 48) {
            return null;
        }
        for (StateRole state : StateRole.values()) {
            int offset = STATE_OFFSET + state.ordinal() * STATE_STRIDE;
            for (int component = 0; component < STATE_STRIDE; component++) {
                if (packet[offset + component] < 0
                        || packet[offset + component] >= ColorRole.values().length) {
                    return null;
                }
            }
        }
        for (DrawState state : DrawState.values()) {
            int stateOffset = STATE_OFFSET + state.ordinal() * STATE_STRIDE;
            int drawOffset = DRAW_OFFSET + state.ordinal() * DRAW_STRIDE;
            for (int component = 0; component < DRAW_STRIDE; component++) {
                int colorRole = packet[stateOffset + component];
                if (packet[drawOffset + component] != packet[COLOR_OFFSET + colorRole]) {
                    return null;
                }
            }
        }
        int bodyOffset = TYPOGRAPHY_OFFSET
            + TypographyRole.BODY.ordinal() * TYPOGRAPHY_STRIDE;
        int buttonOffset = TYPOGRAPHY_OFFSET
            + TypographyRole.BUTTON.ordinal() * TYPOGRAPHY_STRIDE;
        if (packet[TREE_OFFSET + TreeMetric.TEXT_HEIGHT.ordinal()] != packet[bodyOffset + 1]
                || packet[TREE_OFFSET + TreeMetric.CHAR_WIDTH.ordinal()] != packet[bodyOffset]
                || packet[CONTROL_OFFSET + ControlMetric.TEXT_HEIGHT.ordinal()]
                    != packet[buttonOffset + 1]
                || packet[CONTROL_OFFSET + ControlMetric.CHAR_WIDTH.ordinal()]
                    != packet[buttonOffset]
                || packet[TEXT_INPUT_OFFSET
                    + TextInputMetric.TEXT_HEIGHT.ordinal()]
                    != packet[bodyOffset + 1]
                || packet[TEXT_INPUT_OFFSET
                    + TextInputMetric.FALLBACK_CHAR_WIDTH.ordinal()]
                    != packet[bodyOffset]) {
            return null;
        }
        int hoverFill = packet[DRAW_OFFSET
            + DrawState.HOVERED.ordinal() * DRAW_STRIDE];
        int activeFill = packet[DRAW_OFFSET
            + DrawState.ACTIVE.ordinal() * DRAW_STRIDE];
        int focusedBorder = packet[DRAW_OFFSET
            + DrawState.FOCUSED.ordinal() * DRAW_STRIDE + 2];
        if (packet[SUMMARY_OFFSET] != lerpArgb(hoverFill, activeFill, 1, 2)
                || packet[SUMMARY_OFFSET + 1] != lerpArgb(hoverFill, activeFill, 3, 4)
                || packet[SUMMARY_OFFSET + 2] != focusedBorder
                || packet[SUMMARY_OFFSET + 3] != packet[SUMMARY_OFFSET + 2]) {
            return null;
        }
        return new Ui0AndroidThemeSnapshot(packet);
    }

    AppearanceKind appearanceKind() { return AppearanceKind.values()[packet[4]]; }
    int ui0ApiVersion() { return packet[2]; }
    int color(ColorRole role) { return packet[COLOR_OFFSET + role.ordinal()]; }
    int spacing(SpacingRole role) { return packet[SPACING_OFFSET + role.ordinal()]; }
    int radius(RadiusRole role) { return packet[RADIUS_OFFSET + role.ordinal()]; }
    int typographyCharWidth(TypographyRole role) {
        return packet[TYPOGRAPHY_OFFSET + role.ordinal() * TYPOGRAPHY_STRIDE];
    }
    int typographyLineHeight(TypographyRole role) {
        return packet[TYPOGRAPHY_OFFSET + role.ordinal() * TYPOGRAPHY_STRIDE + 1];
    }
    int density(DensityRole role) { return packet[DENSITY_OFFSET + role.ordinal()]; }
    ColorRole stateFillRole(StateRole state) { return stateColorRole(state, 0); }
    ColorRole stateTextRole(StateRole state) { return stateColorRole(state, 1); }
    ColorRole stateBorderRole(StateRole state) { return stateColorRole(state, 2); }
    int stateFillColor(StateRole state) { return color(stateFillRole(state)); }
    int stateTextColor(StateRole state) { return color(stateTextRole(state)); }
    int stateBorderColor(StateRole state) { return color(stateBorderRole(state)); }
    int drawFill(DrawState state) {
        return packet[DRAW_OFFSET + state.ordinal() * DRAW_STRIDE];
    }
    int drawText(DrawState state) {
        return packet[DRAW_OFFSET + state.ordinal() * DRAW_STRIDE + 1];
    }
    int drawBorder(DrawState state) {
        return packet[DRAW_OFFSET + state.ordinal() * DRAW_STRIDE + 2];
    }
    int tree(TreeMetric metric) { return packet[TREE_OFFSET + metric.ordinal()]; }
    int control(ControlMetric metric) {
        return packet[CONTROL_OFFSET + metric.ordinal()];
    }
    int textInput(TextInputMetric metric) {
        return packet[TEXT_INPUT_OFFSET + metric.ordinal()];
    }
    int treeFocusedFill() { return packet[SUMMARY_OFFSET]; }
    int treeSelectedFill() { return packet[SUMMARY_OFFSET + 1]; }
    int treeFocusColor() { return packet[SUMMARY_OFFSET + 2]; }
    int treeCurrentIndicatorFill() { return packet[SUMMARY_OFFSET + 3]; }
    int[] packetCopy() { return packet.clone(); }

    private ColorRole stateColorRole(StateRole state, int component) {
        return ColorRole.values()[packet[STATE_OFFSET
            + state.ordinal() * STATE_STRIDE + component]];
    }

    private static boolean validMetrics(int[] packet,
                                        int offset,
                                        int count,
                                        boolean requirePositive) {
        for (int index = 0; index < count; index++) {
            if (!validMetric(packet[offset + index], requirePositive)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validMetric(int value, boolean requirePositive) {
        return value >= (requirePositive ? 1 : 0) && value <= MAX_METRIC;
    }

    private static int lerpArgb(int from, int to, int numerator, int denominator) {
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
