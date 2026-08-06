package ro.devze.octavo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;

/**
 * Product-neutral native row for a UI0 hierarchy. The host supplies the
 * resolved colors and geometry; this view owns only Android text layout and
 * the direction-aware current-item rail.
 */
final class Ui0NavigationRow extends Button {
    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean current;
    private int railWidthPx;
    private int railInsetPx;
    private float railRadiusPx;

    Ui0NavigationRow(Context context) {
        super(context);
        setAllCaps(false);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        setSingleLine(false);
        setMaxLines(3);
        setEllipsize(TextUtils.TruncateAt.END);
        setDefaultFocusHighlightEnabled(false);
    }

    void setHierarchyText(CharSequence label,
                          CharSequence caption,
                          boolean heading,
                          float captionScale,
                          ColorStateList labelColors,
                          ColorStateList captionColors) {
        if (label == null || label.length() == 0
            || !Float.isFinite(captionScale) || captionScale <= 0.0f
            || labelColors == null || captionColors == null) {
            throw new IllegalArgumentException(
                "Hierarchy text and colors are required");
        }
        SpannableStringBuilder text = new SpannableStringBuilder(label);
        text.setSpan(new TextAppearanceSpan(null,
                                            Typeface.NORMAL,
                                            -1,
                                            labelColors,
                                            null),
                     0,
                     label.length(),
                     Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (heading) {
            text.setSpan(new StyleSpan(Typeface.BOLD),
                         0,
                         label.length(),
                         Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (caption != null && caption.length() > 0) {
            int start = text.length() + 1;
            text.append('\n').append(caption);
            text.setSpan(new RelativeSizeSpan(captionScale),
                         start,
                         text.length(),
                         Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new TextAppearanceSpan(null,
                                                Typeface.NORMAL,
                                                -1,
                                                captionColors,
                                                null),
                         start,
                         text.length(),
                         Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        setText(text);
    }

    void setCurrentRail(boolean isCurrent,
                        int color,
                        int widthPx,
                        int verticalInsetPx,
                        float radiusPx) {
        if (widthPx < 0 || verticalInsetPx < 0 || radiusPx < 0.0f) {
            throw new IllegalArgumentException(
                "Hierarchy rail geometry must be non-negative");
        }
        current = isCurrent;
        railPaint.setColor(color);
        railWidthPx = widthPx;
        railInsetPx = verticalInsetPx;
        railRadiusPx = radiusPx;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (current && railWidthPx > 0
            && getHeight() > railInsetPx * 2) {
            float left = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? getWidth() - railWidthPx : 0.0f;
            RectF rail = new RectF(left,
                                   railInsetPx,
                                   left + railWidthPx,
                                   getHeight() - railInsetPx);
            canvas.drawRoundRect(rail,
                                 railRadiusPx,
                                 railRadiusPx,
                                 railPaint);
        }
        super.onDraw(canvas);
    }

    boolean isCurrentForTesting() {
        return current;
    }

    int railWidthForTesting() {
        return railWidthPx;
    }

    float railRadiusForTesting() {
        return railRadiusPx;
    }
}
