package ro.devze.octavo;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.widget.EditText;

/**
 * Product-neutral translation from one validated UI0 snapshot to Android
 * pixels, semantic state lists, and native drawables.
 */
final class Ui0AndroidThemeAdapter {
    private static final float MAX_DENSITY =
        Integer.MAX_VALUE / 4096.0f;
    private static final double MIN_BODY_TEXT_CONTRAST = 4.5;

    private final Ui0AndroidThemeSnapshot snapshot;
    private final float density;

    Ui0AndroidThemeAdapter(Ui0AndroidThemeSnapshot snapshot, float density) {
        if (snapshot == null
                || !Float.isFinite(density)
                || density <= 0.0f
                || density > MAX_DENSITY) {
            throw new IllegalArgumentException();
        }
        this.snapshot = snapshot;
        this.density = density;
    }

    int dp(int value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }
        if (value == 0) {
            return 0;
        }
        return Math.max(1, Math.round(value * density));
    }

    int color(Ui0AndroidThemeSnapshot.ColorRole role) {
        return snapshot.color(role);
    }

    int textSizeSp(Ui0AndroidThemeSnapshot.TypographyRole role) {
        return snapshot.typographyLineHeight(role);
    }

    int nominalCharWidthSp(Ui0AndroidThemeSnapshot.TypographyRole role) {
        return snapshot.typographyCharWidth(role);
    }

    float relativeTextScale(
        Ui0AndroidThemeSnapshot.TypographyRole role,
        Ui0AndroidThemeSnapshot.TypographyRole baseRole) {
        if (role == null || baseRole == null) {
            throw new IllegalArgumentException();
        }
        int base = textSizeSp(baseRole);
        float scale = (float)textSizeSp(role) / (float)base;
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            throw new IllegalArgumentException();
        }
        return scale;
    }

    int spacingPx(Ui0AndroidThemeSnapshot.SpacingRole role) {
        return dp(snapshot.spacing(role));
    }

    int radiusPx(Ui0AndroidThemeSnapshot.RadiusRole role) {
        return dp(snapshot.radius(role));
    }

    int densityPx(Ui0AndroidThemeSnapshot.DensityRole role) {
        return dp(snapshot.density(role));
    }

    int treePx(Ui0AndroidThemeSnapshot.TreeMetric metric) {
        return dp(snapshot.tree(metric));
    }

    int controlPx(Ui0AndroidThemeSnapshot.ControlMetric metric) {
        return dp(snapshot.control(metric));
    }

    int textInputPx(Ui0AndroidThemeSnapshot.TextInputMetric metric) {
        return dp(snapshot.textInput(metric));
    }

    int rowHeightPx() {
        return dp(Math.max(
            snapshot.density(
                Ui0AndroidThemeSnapshot.DensityRole.ROW_MIN_HEIGHT),
            snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.ROW_HEIGHT)));
    }

    int currentRailReservePx() {
        return dp(snapshot.tree(
            Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_WIDTH)
            + snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_GAP));
    }

    int currentRailInsetPx() {
        int rowHeight = snapshot.tree(
            Ui0AndroidThemeSnapshot.TreeMetric.ROW_HEIGHT);
        int logicalInset = Math.min(
            Math.max(3, rowHeight / 5 + 1),
            rowHeight / 2);
        return dp(logicalInset);
    }

    float currentRailRadiusPx() {
        int logicalRadius = Math.min(
            snapshot.radius(Ui0AndroidThemeSnapshot.RadiusRole.PILL),
            snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_WIDTH) / 2);
        return dp(logicalRadius);
    }

    int hierarchyTextStartPx(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException();
        }
        int depthOffset;
        int start;
        try {
            depthOffset = Math.multiplyExact(
                depth,
                snapshot.tree(
                    Ui0AndroidThemeSnapshot.TreeMetric.INDENT_WIDTH));
            start = Math.addExact(
                snapshot.tree(
                    Ui0AndroidThemeSnapshot.TreeMetric.PADDING_X),
                snapshot.tree(
                    Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_WIDTH));
            start = Math.addExact(start, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.CURRENT_BAR_GAP));
            start = Math.addExact(start, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.EXPANDER_SIZE));
            start = Math.addExact(start, snapshot.tree(
                Ui0AndroidThemeSnapshot.TreeMetric.EXPANDER_GAP));
            start = Math.addExact(start, depthOffset);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(error);
        }
        return dp(start);
    }

    int focusedRowFill(boolean current) {
        return current
            ? snapshot.treeSelectedFill()
            : snapshot.treeFocusedFill();
    }

    int selectedRowFill() {
        return snapshot.treeSelectedFill();
    }

    int focusColor() {
        return snapshot.treeFocusColor();
    }

    int currentIndicatorColor() {
        return snapshot.treeCurrentIndicatorFill();
    }

    GradientDrawable panelBackground() {
        int sheet = color(
            Ui0AndroidThemeSnapshot.ColorRole.SIDEBAR_BACKGROUND);
        return rounded(
            sheet,
            sheet,
            0,
            snapshot.radius(Ui0AndroidThemeSnapshot.RadiusRole.PANEL));
    }

    LayerDrawable rowBackground(boolean current) {
        int radius = snapshot.radius(
            Ui0AndroidThemeSnapshot.RadiusRole.CONTROL);
        StateListDrawable states = new StateListDrawable();
        states.addState(
            new int[] {-android.R.attr.state_enabled},
            rounded(Color.TRANSPARENT, Color.TRANSPARENT, 0, radius));
        states.addState(
            new int[] {
                android.R.attr.state_pressed,
                android.R.attr.state_focused
            },
            rounded(
                snapshot.stateFillColor(
                    Ui0AndroidThemeSnapshot.StateRole.ACTIVE),
                snapshot.treeFocusColor(),
                2,
                radius));
        states.addState(
            new int[] {android.R.attr.state_pressed},
            rounded(
                snapshot.stateFillColor(
                    Ui0AndroidThemeSnapshot.StateRole.ACTIVE),
                Color.TRANSPARENT,
                0,
                radius));
        states.addState(
            new int[] {android.R.attr.state_focused},
            rounded(focusedRowFill(current),
                    snapshot.treeFocusColor(),
                    2,
                    radius));
        states.addState(
            new int[] {},
            rounded(current
                        ? snapshot.treeSelectedFill()
                        : Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    0,
                    radius));
        LayerDrawable result = new LayerDrawable(
            new StateListDrawable[] {states});
        result.setLayerInsetRelative(
            0, currentRailReservePx(), 0, 0, 0);
        return result;
    }

    StateListDrawable neutralBackground() {
        int radius = snapshot.radius(
            Ui0AndroidThemeSnapshot.RadiusRole.CONTROL);
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.DISABLED,
                            1,
                            radius));
        result.addState(
            new int[] {
                android.R.attr.state_pressed,
                android.R.attr.state_focused
            },
            rounded(snapshot.drawFill(
                        Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
                    snapshot.drawBorder(
                        Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
                    2,
                    radius));
        result.addState(new int[] {android.R.attr.state_pressed},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.ACTIVE,
                            1,
                            radius));
        result.addState(new int[] {android.R.attr.state_focused},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.FOCUSED,
                            2,
                            radius));
        result.addState(new int[] {},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.DEFAULT,
                            1,
                            radius));
        return result;
    }

    StateListDrawable actionBackground() {
        int radius = snapshot.radius(
            Ui0AndroidThemeSnapshot.RadiusRole.CONTROL);
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.DISABLED,
                            1,
                            radius));
        result.addState(
            new int[] {
                android.R.attr.state_pressed,
                android.R.attr.state_focused
            },
            rounded(
                color(Ui0AndroidThemeSnapshot.ColorRole
                          .CONTROL_PRIMARY_ACTIVE),
                color(Ui0AndroidThemeSnapshot.ColorRole.FOCUS),
                2,
                radius));
        result.addState(new int[] {android.R.attr.state_pressed},
                        rounded(
                            color(Ui0AndroidThemeSnapshot.ColorRole
                                      .CONTROL_PRIMARY_ACTIVE),
                            color(Ui0AndroidThemeSnapshot.ColorRole
                                      .CONTROL_PRIMARY_ACTIVE),
                            1,
                            radius));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(
                            color(Ui0AndroidThemeSnapshot.ColorRole
                                      .CONTROL_PRIMARY),
                            color(Ui0AndroidThemeSnapshot.ColorRole.FOCUS),
                            2,
                            radius));
        result.addState(new int[] {},
                        rounded(
                            color(Ui0AndroidThemeSnapshot.ColorRole
                                      .CONTROL_PRIMARY),
                            color(Ui0AndroidThemeSnapshot.ColorRole
                                      .CONTROL_PRIMARY),
                            1,
                            radius));
        return result;
    }

    StateListDrawable optionBackground() {
        int radius = snapshot.radius(
            Ui0AndroidThemeSnapshot.RadiusRole.CONTROL);
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.DISABLED,
                            1,
                            radius));
        result.addState(
            new int[] {
                android.R.attr.state_pressed,
                android.R.attr.state_focused
            },
            rounded(snapshot.drawFill(
                        Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
                    snapshot.drawBorder(
                        Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
                    2,
                    radius));
        result.addState(new int[] {android.R.attr.state_pressed},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.ACTIVE,
                            1,
                            radius));
        result.addState(
            new int[] {
                android.R.attr.state_checked,
                android.R.attr.state_focused
            },
            rounded(snapshot.drawFill(
                        Ui0AndroidThemeSnapshot.DrawState.SELECTED),
                    snapshot.drawBorder(
                        Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
                    2,
                    radius));
        result.addState(new int[] {android.R.attr.state_checked},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.SELECTED,
                            1,
                            radius));
        result.addState(new int[] {android.R.attr.state_focused},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.FOCUSED,
                            2,
                            radius));
        result.addState(new int[] {},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.DEFAULT,
                            1,
                            radius));
        return result;
    }

    StateListDrawable inputBackground() {
        int radius = snapshot.radius(
            Ui0AndroidThemeSnapshot.RadiusRole.INPUT);
        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {-android.R.attr.state_enabled},
                        drawState(
                            Ui0AndroidThemeSnapshot.DrawState.DISABLED,
                            1,
                            radius));
        result.addState(new int[] {android.R.attr.state_focused},
                        rounded(
                            color(Ui0AndroidThemeSnapshot.ColorRole.INPUT),
                            color(Ui0AndroidThemeSnapshot.ColorRole.FOCUS),
                            2,
                            radius));
        result.addState(new int[] {},
                        rounded(
                            color(Ui0AndroidThemeSnapshot.ColorRole.INPUT),
                            color(Ui0AndroidThemeSnapshot.ColorRole.BORDER),
                            1,
                            radius));
        return result;
    }

    ColorStateList hierarchyTextColors(
        Ui0AndroidThemeSnapshot.ColorRole normalRole) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_pressed},
                new int[] {android.R.attr.state_focused},
                new int[] {android.R.attr.state_selected},
                new int[] {}
            },
            new int[] {
                snapshot.stateTextColor(
                    Ui0AndroidThemeSnapshot.StateRole.DISABLED),
                snapshot.stateTextColor(
                    Ui0AndroidThemeSnapshot.StateRole.ACTIVE),
                snapshot.stateTextColor(
                    Ui0AndroidThemeSnapshot.StateRole.FOCUSED),
                currentRowTextColor(),
                color(normalRole)
            });
    }

    int currentRowTextColor() {
        int selected = snapshot.stateTextColor(
            Ui0AndroidThemeSnapshot.StateRole.SELECTED);
        int alternative = color(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_ON_FILL);
        double selectedContrast = contrastRatio(
            selected, snapshot.treeSelectedFill());
        double alternativeContrast = contrastRatio(
            alternative, snapshot.treeSelectedFill());
        if (selectedContrast >= MIN_BODY_TEXT_CONTRAST
                || selectedContrast >= alternativeContrast) {
            return selected;
        }
        return alternative;
    }

    ColorStateList neutralTextColors() {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {}
            },
            new int[] {
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.DISABLED),
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.DEFAULT)
            });
    }

    ColorStateList actionTextColors() {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {}
            },
            new int[] {
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.DISABLED),
                color(Ui0AndroidThemeSnapshot.ColorRole.TEXT_ON_PRIMARY)
            });
    }

    ColorStateList radioTextColors() {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_pressed},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.DISABLED),
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.SELECTED),
                color(Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY)
            });
    }

    ColorStateList radioTintColors() {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
            },
            new int[] {
                color(Ui0AndroidThemeSnapshot.ColorRole.BORDER_MUTED),
                color(Ui0AndroidThemeSnapshot.ColorRole.ACCENT),
                color(Ui0AndroidThemeSnapshot.ColorRole.TEXT_MUTED)
            });
    }

    ColorStateList inputTextColors() {
        return enabledTextColors(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);
    }

    ColorStateList inputHintColors() {
        return enabledTextColors(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_MUTED);
    }

    int textSelectionColor() {
        return color(Ui0AndroidThemeSnapshot.ColorRole.SELECTION);
    }

    int textCaretColor() {
        return color(Ui0AndroidThemeSnapshot.ColorRole.ACCENT);
    }

    void applyTextInputEditingColors(EditText input) {
        if (input == null) {
            throw new IllegalArgumentException();
        }
        input.setHighlightColor(textSelectionColor());
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }
        input.setTextCursorDrawable(new CaretDrawable(
            textCaretColor(),
            textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.CARET_WIDTH)));
        Drawable center = tinted(input.getTextSelectHandle());
        Drawable left = tinted(input.getTextSelectHandleLeft());
        Drawable right = tinted(input.getTextSelectHandleRight());
        if (center != null) {
            input.setTextSelectHandle(center);
        }
        if (left != null) {
            input.setTextSelectHandleLeft(left);
        }
        if (right != null) {
            input.setTextSelectHandleRight(right);
        }
    }

    private Drawable tinted(Drawable source) {
        if (source == null) {
            return null;
        }
        Drawable result = source.mutate();
        result.setTint(textCaretColor());
        return result;
    }

    private ColorStateList enabledTextColors(
        Ui0AndroidThemeSnapshot.ColorRole enabledRole) {
        return new ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {}
            },
            new int[] {
                snapshot.drawText(
                    Ui0AndroidThemeSnapshot.DrawState.DISABLED),
                color(enabledRole)
            });
    }

    private GradientDrawable drawState(
        Ui0AndroidThemeSnapshot.DrawState state,
        int strokeWidthDp,
        int radiusDp) {
        return rounded(snapshot.drawFill(state),
                       snapshot.drawBorder(state),
                       strokeWidthDp,
                       radiusDp);
    }

    private GradientDrawable rounded(int fill,
                                     int stroke,
                                     int strokeWidthDp,
                                     int radiusDp) {
        GradientDrawable result = new GradientDrawable();
        result.setShape(GradientDrawable.RECTANGLE);
        result.setColor(fill);
        result.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) {
            result.setStroke(dp(strokeWidthDp), stroke);
        }
        return result;
    }

    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
            / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double relativeLuminance(int color) {
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

    private static final class CaretDrawable extends ColorDrawable {
        private final int intrinsicWidth;

        CaretDrawable(int color, int intrinsicWidth) {
            super(color);
            this.intrinsicWidth = Math.max(1, intrinsicWidth);
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicWidth;
        }
    }
}
