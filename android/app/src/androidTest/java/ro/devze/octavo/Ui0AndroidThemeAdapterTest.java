package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class Ui0AndroidThemeAdapterTest {
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
    private static final int LEGACY_EDITOR_ACCENT = 0xFF8B7560;
    private static final double MIN_LEGACY_EDITOR_CONTRAST = 3.5;
    private static final double MIN_BODY_TEXT_CONTRAST = 4.5;
    private static final double MIN_NON_TEXT_CONTRAST = 3.0;

    @Test
    public void metricsScaleInPixelsWhileTypographyRemainsSp() {
        Ui0AndroidThemeSnapshot snapshot = snapshot(validPacket());
        Ui0AndroidThemeAdapter adapter =
            new Ui0AndroidThemeAdapter(snapshot, 2.0f);

        assertEquals(0, adapter.dp(0));
        assertEquals(0, adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.TEXT_STACK_GAP));
        assertEquals(48, adapter.spacingPx(
            Ui0AndroidThemeSnapshot.SpacingRole.PAGE_MARGIN));
        assertEquals(12, adapter.radiusPx(
            Ui0AndroidThemeSnapshot.RadiusRole.CONTROL));
        for (Ui0AndroidThemeSnapshot.DensityRole role
                : new Ui0AndroidThemeSnapshot.DensityRole[] {
                    Ui0AndroidThemeSnapshot.DensityRole.CONTROL_HEIGHT,
                    Ui0AndroidThemeSnapshot.DensityRole.ICON_BUTTON_SIZE,
                    Ui0AndroidThemeSnapshot.DensityRole.ROW_MIN_HEIGHT,
                    Ui0AndroidThemeSnapshot.DensityRole.MENU_ITEM_HEIGHT
                }) {
            assertEquals(96, adapter.densityPx(role));
        }
        assertEquals(96, adapter.rowHeightPx());
        assertEquals(20, adapter.currentRailReservePx());
        assertEquals(10, new Ui0AndroidThemeAdapter(snapshot, 1.0f)
            .currentRailInsetPx());
        assertEquals(15, new Ui0AndroidThemeAdapter(snapshot, 1.5f)
            .currentRailInsetPx());
        assertEquals(20, adapter.currentRailInsetPx());
        assertEquals(2.0f, adapter.currentRailRadiusPx(), 0.0f);
        assertEquals(76, adapter.hierarchyTextStartPx(0));
        assertEquals(116, adapter.hierarchyTextStartPx(1));
        assertEquals(96, adapter.treePx(
            Ui0AndroidThemeSnapshot.TreeMetric.ROW_HEIGHT));
        assertEquals(16, adapter.controlPx(
            Ui0AndroidThemeSnapshot.ControlMetric.PADDING_X));
        assertEquals(10, adapter.textInputPx(
            Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y));
        assertEquals(5, new Ui0AndroidThemeAdapter(snapshot, 1.0f)
            .textInputPx(
                Ui0AndroidThemeSnapshot.TextInputMetric.PADDING_Y));
        assertEquals(38, new Ui0AndroidThemeAdapter(snapshot, 1.0f)
            .hierarchyTextStartPx(0));

        int[] expectedSp = new int[] {28, 20, 16, 14, 14, 16, 16, 16};
        Ui0AndroidThemeSnapshot.TypographyRole[] roles =
            Ui0AndroidThemeSnapshot.TypographyRole.values();
        for (int index = 0; index < roles.length; index++) {
            assertEquals(expectedSp[index], adapter.textSizeSp(roles[index]));
            assertEquals(expectedSp[index],
                new Ui0AndroidThemeAdapter(snapshot, 0.5f)
                    .textSizeSp(roles[index]));
        }
        assertEquals(0.875f,
                     adapter.relativeTextScale(
                         Ui0AndroidThemeSnapshot.TypographyRole.CAPTION,
                         Ui0AndroidThemeSnapshot.TypographyRole.BODY),
                     0.0f);

        assertEquals(1,
            new Ui0AndroidThemeAdapter(snapshot, 0.01f).dp(1));
        assertInvalid(() -> new Ui0AndroidThemeAdapter(null, 1.0f));
        assertInvalid(() -> new Ui0AndroidThemeAdapter(snapshot, 0.0f));
        assertInvalid(() -> new Ui0AndroidThemeAdapter(snapshot, Float.NaN));
        assertInvalid(() -> new Ui0AndroidThemeAdapter(
            snapshot, Float.POSITIVE_INFINITY));
        assertInvalid(() -> adapter.dp(-1));
        assertInvalid(() -> adapter.hierarchyTextStartPx(-1));
        assertInvalid(() -> adapter.hierarchyTextStartPx(Integer.MAX_VALUE));
        assertInvalid(() -> adapter.relativeTextScale(
            null, Ui0AndroidThemeSnapshot.TypographyRole.BODY));
        assertInvalid(() -> adapter.relativeTextScale(
            Ui0AndroidThemeSnapshot.TypographyRole.CAPTION, null));
    }

    @Test
    public void semanticColorListsPreserveUi0StatePrecedence() {
        Ui0AndroidThemeSnapshot snapshot = snapshot(validPacket());
        Ui0AndroidThemeAdapter adapter =
            new Ui0AndroidThemeAdapter(snapshot, 1.0f);
        ColorStateList hierarchy = adapter.hierarchyTextColors(
            Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY);

        assertEquals(snapshot.stateTextColor(
                Ui0AndroidThemeSnapshot.StateRole.DISABLED),
            colorFor(hierarchy, android.R.attr.state_focused));
        assertEquals(snapshot.stateTextColor(
                Ui0AndroidThemeSnapshot.StateRole.ACTIVE),
            colorFor(hierarchy,
                android.R.attr.state_enabled,
                android.R.attr.state_pressed,
                android.R.attr.state_focused));
        assertEquals(snapshot.stateTextColor(
                Ui0AndroidThemeSnapshot.StateRole.FOCUSED),
            colorFor(hierarchy,
                android.R.attr.state_enabled,
                android.R.attr.state_focused));
        assertEquals(adapter.currentRowTextColor(),
            colorFor(hierarchy,
                android.R.attr.state_enabled,
                android.R.attr.state_selected));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY),
            colorFor(hierarchy, android.R.attr.state_enabled));

        ColorStateList neutral = adapter.neutralTextColors();
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            colorFor(neutral));
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.DEFAULT),
            colorFor(neutral, android.R.attr.state_enabled));

        ColorStateList action = adapter.actionTextColors();
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            colorFor(action));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_ON_PRIMARY),
            colorFor(action, android.R.attr.state_enabled));

        ColorStateList radioText = adapter.radioTextColors();
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            colorFor(radioText));
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.SELECTED),
            colorFor(radioText,
                android.R.attr.state_enabled,
                android.R.attr.state_checked));
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
            colorFor(radioText,
                android.R.attr.state_enabled,
                android.R.attr.state_checked,
                android.R.attr.state_pressed));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_SECONDARY),
            colorFor(radioText, android.R.attr.state_enabled));

        ColorStateList radioTint = adapter.radioTintColors();
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.BORDER_MUTED),
            colorFor(radioTint));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.ACCENT),
            colorFor(radioTint,
                android.R.attr.state_enabled,
                android.R.attr.state_checked));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_MUTED),
            colorFor(radioTint, android.R.attr.state_enabled));

        ColorStateList inputText = adapter.inputTextColors();
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            colorFor(inputText));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY),
            colorFor(inputText, android.R.attr.state_enabled));

        ColorStateList inputHint = adapter.inputHintColors();
        assertEquals(snapshot.drawText(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            colorFor(inputHint));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.TEXT_MUTED),
            colorFor(inputHint, android.R.attr.state_enabled));
    }

    @Test
    public void nativeFontBindingUsesResolvedSizeAndFitsSupportedScales() {
        Ui0AndroidThemeSnapshot snapshot = snapshot(validPacket());
        Ui0AndroidThemeAdapter adapter =
            new Ui0AndroidThemeAdapter(snapshot, 1.0f);
        for (float scale : new float[] {1.0f, 1.3f}) {
            for (Ui0AndroidThemeSnapshot.TypographyRole role
                    : Ui0AndroidThemeSnapshot.TypographyRole.values()) {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setTypeface(Typeface.DEFAULT);
                float resolvedSize = adapter.textSizeSp(role) * scale;
                paint.setTextSize(resolvedSize);
                assertEquals(resolvedSize, paint.getTextSize(), 0.01f);
                Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
                assertTrue(metrics.bottom > metrics.top);
                if (role != Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE
                        && role != Ui0AndroidThemeSnapshot.TypographyRole
                            .SECTION_TITLE) {
                    assertTrue(metrics.bottom - metrics.top
                        <= adapter.dp(OctavoDesignTokens.TOUCH_TARGET_DP));
                }
                float measuredDigit = paint.measureText("0");
                float nominalDigit = adapter.nominalCharWidthSp(role) * scale;
                assertTrue(Float.isFinite(measuredDigit));
                assertTrue(measuredDigit >= nominalDigit * 0.5f);
                assertTrue(measuredDigit <= nominalDigit * 2.0f);
            }
        }
    }

    @Test
    public void textInputEditingColorsComeFromResolvedUi0Roles() {
        Ui0AndroidThemeSnapshot snapshot = snapshot(validPacket());
        Ui0AndroidThemeAdapter adapter =
            new Ui0AndroidThemeAdapter(snapshot, 1.0f);
        EditText input = new EditText(
            InstrumentationRegistry.getInstrumentation().getTargetContext());

        adapter.applyTextInputEditingColors(input);

        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.SELECTION),
            input.getHighlightColor());
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.SELECTION),
            adapter.textSelectionColor());
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.ACCENT),
            adapter.textCaretColor());
        if (Build.VERSION.SDK_INT >= 29) {
            Drawable cursor = input.getTextCursorDrawable();
            assertNotNull(cursor);
            assertTrue(cursor instanceof ColorDrawable);
            assertEquals(adapter.textCaretColor(),
                ((ColorDrawable)cursor).getColor());
            assertEquals(adapter.textInputPx(
                    Ui0AndroidThemeSnapshot.TextInputMetric.CARET_WIDTH),
                cursor.getIntrinsicWidth());
        }
    }

    @Test
    public void legacyEditorThemeColorContrastsWithEveryUi0InputSurface() {
        Context context = new ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            R.style.Theme_Octavo);
        int colorAccent = themeColor(context, android.R.attr.colorAccent);
        int colorControlActivated = themeColor(
            context, android.R.attr.colorControlActivated);
        assertEquals(LEGACY_EDITOR_ACCENT, colorAccent);
        assertEquals(LEGACY_EDITOR_ACCENT, colorControlActivated);
        if (Build.VERSION.SDK_INT < 29) {
            assertDrawableContainsColor(
                themeDrawable(context, android.R.attr.textCursorDrawable),
                LEGACY_EDITOR_ACCENT);
            assertDrawableContainsColor(
                themeDrawable(context, android.R.attr.textSelectHandle),
                LEGACY_EDITOR_ACCENT);
            assertDrawableContainsColor(
                themeDrawable(context, android.R.attr.textSelectHandleLeft),
                LEGACY_EDITOR_ACCENT);
            assertDrawableContainsColor(
                themeDrawable(context, android.R.attr.textSelectHandleRight),
                LEGACY_EDITOR_ACCENT);
        }

        for (int theme = 0; theme < OctavoAppearance.THEME_COUNT; theme++) {
            OctavoDesignTokens tokens = OctavoDesignTokens.forTheme(theme);
            Ui0AndroidThemeSnapshot snapshot = Ui0AndroidThemeSnapshot.parse(
                OctavoNative.ui0AndroidThemeSnapshot(
                    tokens.darkAppearance,
                    tokens.nativeUi0Colors()));
            assertNotNull(snapshot);
            int inputSurface = snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.INPUT);
            assertTrue(
                contrastRatio(colorControlActivated, inputSurface)
                    >= MIN_LEGACY_EDITOR_CONTRAST);
        }
    }

    @Test
    public void productThemesPreserveUi0FocusAndReadableCurrentRows() {
        for (int theme = 0; theme < OctavoAppearance.THEME_COUNT; theme++) {
            OctavoDesignTokens tokens = OctavoDesignTokens.forTheme(theme);
            assertEquals(tokens.accentPressed, tokens.focus);
            Ui0AndroidThemeSnapshot snapshot = Ui0AndroidThemeSnapshot.parse(
                OctavoNative.ui0AndroidThemeSnapshot(
                    tokens.darkAppearance,
                    tokens.nativeUi0Colors()));
            assertNotNull(snapshot);
            assertEquals(snapshot.color(
                    Ui0AndroidThemeSnapshot.ColorRole.ACCENT_HOVER),
                snapshot.color(
                    Ui0AndroidThemeSnapshot.ColorRole.FOCUS));

            Ui0AndroidThemeAdapter adapter =
                new Ui0AndroidThemeAdapter(snapshot, 1.0f);
            int currentText = colorFor(
                adapter.hierarchyTextColors(
                    Ui0AndroidThemeSnapshot.ColorRole.TEXT_PRIMARY),
                android.R.attr.state_enabled,
                android.R.attr.state_selected);
            assertEquals(adapter.currentRowTextColor(), currentText);
            assertTrue(contrastRatio(
                    currentText, adapter.selectedRowFill())
                >= MIN_BODY_TEXT_CONTRAST);
            assertEquals(snapshot.treeCurrentIndicatorFill(),
                adapter.currentIndicatorColor());
            assertTrue(contrastRatio(
                    adapter.currentIndicatorColor(),
                    snapshot.color(
                        Ui0AndroidThemeSnapshot.ColorRole.SIDEBAR_BACKGROUND))
                >= MIN_NON_TEXT_CONTRAST);
        }
    }

    @Test
    public void drawableBuildersPreserveUi0FillBorderAndOrdering() {
        Ui0AndroidThemeSnapshot snapshot = snapshot(validPacket());
        Ui0AndroidThemeAdapter adapter =
            new Ui0AndroidThemeAdapter(snapshot, 2.0f);

        GradientDrawable panel = adapter.panelBackground();
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.SIDEBAR_BACKGROUND),
            fill(panel));
        assertEquals(12.0f, panel.getCornerRadius(), 0.0f);

        StateListDrawable neutral = adapter.neutralBackground();
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            neutral, android.R.attr.state_focused);
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
            neutral,
            android.R.attr.state_enabled,
            android.R.attr.state_pressed,
            android.R.attr.state_focused);
        assertEquals(snapshot.drawBorder(
                Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
            stroke(gradientFor(
                neutral,
                android.R.attr.state_enabled,
                android.R.attr.state_pressed,
                android.R.attr.state_focused)));
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
            neutral,
            android.R.attr.state_enabled,
            android.R.attr.state_focused);
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.DEFAULT),
            neutral, android.R.attr.state_enabled);

        StateListDrawable action = adapter.actionBackground();
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            action);
        assertFill(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.CONTROL_PRIMARY_ACTIVE),
            action,
            android.R.attr.state_enabled,
            android.R.attr.state_pressed);
        GradientDrawable actionPressedFocus = gradientFor(
            action,
            android.R.attr.state_enabled,
            android.R.attr.state_pressed,
            android.R.attr.state_focused);
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.CONTROL_PRIMARY_ACTIVE),
            fill(actionPressedFocus));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.FOCUS),
            stroke(actionPressedFocus));
        GradientDrawable actionFocus = gradientFor(
            action,
            android.R.attr.state_enabled,
            android.R.attr.state_focused);
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.CONTROL_PRIMARY),
            fill(actionFocus));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.FOCUS),
            stroke(actionFocus));

        StateListDrawable option = adapter.optionBackground();
        GradientDrawable pressedCheckedFocus = gradientFor(
            option,
            android.R.attr.state_enabled,
            android.R.attr.state_checked,
            android.R.attr.state_focused,
            android.R.attr.state_pressed);
        assertEquals(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
            fill(pressedCheckedFocus));
        assertEquals(snapshot.drawBorder(
                Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
            stroke(pressedCheckedFocus));
        GradientDrawable checkedFocus = gradientFor(
            option,
            android.R.attr.state_enabled,
            android.R.attr.state_checked,
            android.R.attr.state_focused);
        assertEquals(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.SELECTED),
            fill(checkedFocus));
        assertEquals(snapshot.drawBorder(
                Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
            stroke(checkedFocus));
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.SELECTED),
            option,
            android.R.attr.state_enabled,
            android.R.attr.state_checked);
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.ACTIVE),
            option,
            android.R.attr.state_enabled,
            android.R.attr.state_checked,
            android.R.attr.state_pressed);
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.FOCUSED),
            option,
            android.R.attr.state_enabled,
            android.R.attr.state_focused);
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.DEFAULT),
            option, android.R.attr.state_enabled);

        StateListDrawable input = adapter.inputBackground();
        assertFill(snapshot.drawFill(
                Ui0AndroidThemeSnapshot.DrawState.DISABLED),
            input);
        GradientDrawable inputFocus = gradientFor(
            input,
            android.R.attr.state_enabled,
            android.R.attr.state_focused);
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.INPUT),
            fill(inputFocus));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.FOCUS),
            stroke(inputFocus));
        GradientDrawable inputDefault = gradientFor(
            input, android.R.attr.state_enabled);
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.INPUT),
            fill(inputDefault));
        assertEquals(snapshot.color(
                Ui0AndroidThemeSnapshot.ColorRole.BORDER),
            stroke(inputDefault));

        LayerDrawable ordinaryRow = adapter.rowBackground(false);
        assertEquals(20, ordinaryRow.getLayerInsetStart(0));
        StateListDrawable ordinaryStates =
            (StateListDrawable) ordinaryRow.getDrawable(0);
        assertFill(Color.TRANSPARENT,
            ordinaryStates, android.R.attr.state_enabled);
        assertFill(snapshot.treeFocusedFill(),
            ordinaryStates,
            android.R.attr.state_enabled,
            android.R.attr.state_focused);

        LayerDrawable currentRow = adapter.rowBackground(true);
        StateListDrawable currentStates =
            (StateListDrawable) currentRow.getDrawable(0);
        assertFill(snapshot.treeSelectedFill(),
            currentStates, android.R.attr.state_enabled);
        GradientDrawable currentFocus = gradientFor(
            currentStates,
            android.R.attr.state_enabled,
            android.R.attr.state_focused);
        assertEquals(snapshot.treeSelectedFill(), fill(currentFocus));
        assertEquals(snapshot.treeFocusColor(), stroke(currentFocus));
        GradientDrawable currentPressedFocus = gradientFor(
            currentStates,
            android.R.attr.state_enabled,
            android.R.attr.state_pressed,
            android.R.attr.state_focused);
        assertEquals(snapshot.stateFillColor(
                Ui0AndroidThemeSnapshot.StateRole.ACTIVE),
            fill(currentPressedFocus));
        assertEquals(snapshot.treeFocusColor(),
            stroke(currentPressedFocus));
        GradientDrawable currentPressed = gradientFor(
            currentStates,
            android.R.attr.state_enabled,
            android.R.attr.state_pressed);
        assertEquals(snapshot.stateFillColor(
                Ui0AndroidThemeSnapshot.StateRole.ACTIVE),
            fill(currentPressed));
        assertEquals(fill(currentPressed), edge(currentPressed));
        assertFill(Color.TRANSPARENT,
            currentStates, android.R.attr.state_focused);

        assertEquals(snapshot.treeFocusedFill(),
            adapter.focusedRowFill(false));
        assertEquals(snapshot.treeSelectedFill(),
            adapter.focusedRowFill(true));
        assertEquals(snapshot.treeSelectedFill(), adapter.selectedRowFill());
        assertEquals(snapshot.treeFocusColor(), adapter.focusColor());
        assertEquals(snapshot.treeCurrentIndicatorFill(),
            adapter.currentIndicatorColor());
    }

    @Test
    public void adapterOwnsNoCallerDataOrProductState() {
        int[] packet = validPacket();
        Ui0AndroidThemeSnapshot snapshot = snapshot(packet);
        Ui0AndroidThemeAdapter adapter =
            new Ui0AndroidThemeAdapter(snapshot, 1.0f);
        int original = adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.APP_BACKGROUND);
        packet[COLOR_OFFSET] ^= 0x00FFFFFF;
        assertEquals(original, adapter.color(
            Ui0AndroidThemeSnapshot.ColorRole.APP_BACKGROUND));

        assertTrue(Modifier.isFinal(
            Ui0AndroidThemeAdapter.class.getModifiers()));
        for (Field field : Ui0AndroidThemeAdapter.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType() == Ui0AndroidThemeSnapshot.class
                || field.getType() == float.class);
        }
        assertNotSame(adapter.panelBackground(), adapter.panelBackground());
        assertNotSame(adapter.rowBackground(true),
            adapter.rowBackground(true));
        assertNotSame(adapter.neutralBackground(),
            adapter.neutralBackground());
    }

    private static int colorFor(ColorStateList list, int... state) {
        return list.getColorForState(state, Color.TRANSPARENT);
    }

    private static int themeColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(
            attribute, value, true));
        if (value.resourceId != 0) {
            return context.getColor(value.resourceId);
        }
        return value.data;
    }

    private static Drawable themeDrawable(Context context, int attribute) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(
            attribute, value, true));
        assertTrue(value.resourceId != 0);
        Drawable result = context.getDrawable(value.resourceId);
        assertNotNull(result);
        return result;
    }

    private static void assertDrawableContainsColor(
        Drawable drawable, int expected) {
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        boolean found = false;
        for (int y = 0; y < height && !found; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) != 0
                        && Color.red(pixel) == Color.red(expected)
                        && Color.green(pixel) == Color.green(expected)
                        && Color.blue(pixel) == Color.blue(expected)) {
                    found = true;
                    break;
                }
            }
        }
        bitmap.recycle();
        assertTrue(found);
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

    private static void assertFill(
        int expected, StateListDrawable drawable, int... state) {
        assertEquals(expected, fill(gradientFor(drawable, state)));
    }

    private static GradientDrawable gradientFor(
        StateListDrawable drawable, int... state) {
        drawable.setState(state);
        Drawable current = drawable.getCurrent();
        assertTrue(current instanceof GradientDrawable);
        return (GradientDrawable) current;
    }

    private static int fill(GradientDrawable drawable) {
        ColorStateList fill = drawable.getColor();
        assertNotNull(fill);
        return fill.getDefaultColor();
    }

    private static int stroke(GradientDrawable drawable) {
        return edge(drawable);
    }

    private static int edge(GradientDrawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(
            48, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(canvas);
        int color = bitmap.getPixel(1, bitmap.getHeight() / 2);
        bitmap.recycle();
        return color;
    }

    private static void assertInvalid(Runnable operation) {
        try {
            operation.run();
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    private static Ui0AndroidThemeSnapshot snapshot(int[] packet) {
        Ui0AndroidThemeSnapshot snapshot =
            Ui0AndroidThemeSnapshot.parse(packet);
        assertNotNull(snapshot);
        return snapshot;
    }

    private static int[] validPacket() {
        int[] packet = new int[Ui0AndroidThemeSnapshot.PACKET_LENGTH];
        int[] header = new int[] {
            Ui0AndroidThemeSnapshot.MAGIC,
            Ui0AndroidThemeSnapshot.VERSION,
            Ui0AndroidThemeSnapshot.UI0_API_VERSION,
            Ui0AndroidThemeSnapshot.PACKET_LENGTH,
            0,
            26, 8, 7, 8, 2, 6, 7, 3, 7, 3, 10, 9, 4, 6, 0
        };
        System.arraycopy(header, 0, packet, 0, header.length);

        for (int index = 0; index < 26; index++) {
            packet[COLOR_OFFSET + index] =
                (int) (0xFF100000L + index * 0x00030507L);
        }
        copy(packet, SPACING_OFFSET,
            24, 16, 8, 8, 8, 6, 18, 0);
        copy(packet, RADIUS_OFFSET,
            6, 6, 6, 6, 6, 6, 999);
        copy(packet, TYPOGRAPHY_OFFSET,
            10, 28,
            9, 20,
            8, 16,
            7, 14,
            7, 14,
            8, 16,
            8, 16,
            8, 16);
        copy(packet, DENSITY_OFFSET,
            48, 48, 48, 12, 16, 48);

        int[][] states = new int[][] {
            {18, 7, 5},
            {4, 7, 5},
            {5, 8, 5},
            {17, 7, 11},
            {4, 9, 6},
            {18, 7, 20},
            {18, 15, 15}
        };
        for (int state = 0; state < states.length; state++) {
            for (int component = 0; component < 3; component++) {
                int role = states[state][component];
                packet[STATE_OFFSET + state * 3 + component] = role;
                packet[DRAW_OFFSET + state * 3 + component] =
                    packet[COLOR_OFFSET + role];
            }
        }

        copy(packet, TREE_OFFSET,
            48, 2, 10, 16, 20, 12, 6, 3, 7, 8);
        copy(packet, CONTROL_OFFSET,
            8, 4, 10, 16, 6, 48, 18, 8, 16);
        int hoverFill = packet[DRAW_OFFSET
            + Ui0AndroidThemeSnapshot.DrawState.HOVERED.ordinal() * 3];
        int activeFill = packet[DRAW_OFFSET
            + Ui0AndroidThemeSnapshot.DrawState.ACTIVE.ordinal() * 3];
        packet[SUMMARY_OFFSET] = lerpArgb(hoverFill, activeFill, 1, 2);
        packet[SUMMARY_OFFSET + 1] = lerpArgb(
            hoverFill, activeFill, 3, 4);
        packet[SUMMARY_OFFSET + 2] = packet[COLOR_OFFSET
            + Ui0AndroidThemeSnapshot.ColorRole.FOCUS.ordinal()];
        packet[SUMMARY_OFFSET + 3] = packet[SUMMARY_OFFSET + 2];
        copy(packet, TEXT_INPUT_OFFSET,
            8, 5, 16, 1, 1, 8);
        return packet;
    }

    private static void copy(int[] target, int offset, int... values) {
        System.arraycopy(values, 0, target, offset, values.length);
    }

    private static int lerpArgb(
        int from, int to, int numerator, int denominator) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            int channel = start
                + ((end - start) * numerator + denominator / 2)
                    / denominator;
            result |= (channel & 0xFF) << shift;
        }
        return result;
    }
}
