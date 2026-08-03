package ro.devze.octavo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.TypedValue;

import java.nio.ByteBuffer;

final class OctavoTypography {
    static final int MAGIC = 0x4F545950;
    static final int VERSION = 2;
    static final int FIRST_CODEPOINT = 32;
    private static final int LAST_ASCII_CODEPOINT = 126;
    private static final int FIRST_LATIN1_CODEPOINT = 160;
    private static final int LAST_LATIN1_CODEPOINT = 255;
    private static final int[] PUBLICATION_CODEPOINTS = {
        0x0152, 0x0153, 0x0160, 0x0161, 0x0178, 0x017D, 0x017E,
        0x0192, 0x02C6, 0x02DC, 0x2002, 0x2003, 0x2007, 0x2009,
        0x200A, 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2015,
        0x2018, 0x2019, 0x201A, 0x201B, 0x201C, 0x201D, 0x201E,
        0x201F, 0x2020, 0x2021, 0x2022, 0x2026, 0x2030, 0x2032,
        0x2033, 0x2039, 0x203A, 0x2044, 0x20AC, 0x2122, 0x2212
    };
    private static final int[] CODEPOINTS = buildCodepoints();
    static final int GLYPH_COUNT = CODEPOINTS.length;
    static final int STYLE_COUNT = 4;
    static final int COLUMN_COUNT = 16;
    static final int HEADER_COUNT = 18;
    static final int CODEPOINT_OFFSET = HEADER_COUNT;
    static final int ADVANCE_OFFSET = CODEPOINT_OFFSET + GLYPH_COUNT;
    private static final int MAX_TEXT_PX = 512;
    private static final int MAX_ATLAS_DIMENSION = 16384;
    private static final int MAX_ATLAS_BYTES = 64 * 1024 * 1024;
    /*
     * Reader entry used to rasterize the identical atlas on every resume.
     * Keep exactly one immutable atlas: this bounds retained storage while
     * making the common same-typography resume allocation-free.
     */
    private static OctavoTypography cachedTypography;
    private static int cachedFontFamilyId = -1;
    private static int cachedTextPx = -1;
    private static int cachedLineSpacingPermille = -1;
    private static long cacheHitCount;
    private static long cacheBuildCount;

    final int[] metrics;
    final byte[] alpha;

    private OctavoTypography(int[] metrics, byte[] alpha) {
        this.metrics = metrics;
        this.alpha = alpha;
    }

    static OctavoTypography create(Context context) {
        return create(context, OctavoAppearance.defaults());
    }

    static synchronized OctavoTypography create(
        Context context,
        OctavoAppearance appearance) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        OctavoAppearance selected = appearance == null
            ? OctavoAppearance.defaults() : appearance;
        float resolvedTextPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            selected.fontSizeSp(),
            context.getResources().getDisplayMetrics());
        if (Float.isNaN(resolvedTextPx)
            || Float.isInfinite(resolvedTextPx)) {
            throw new IllegalArgumentException("Invalid display metrics");
        }
        int textPx = Math.max(
            Math.round(resolvedTextPx), selected.fontSizeSp());
        if (textPx <= 0 || textPx > MAX_TEXT_PX) {
            throw new IllegalArgumentException("Text size exceeds its bound");
        }
        if (cachedTypography != null
            && cachedFontFamilyId == selected.fontFamilyId()
            && cachedTextPx == textPx
            && cachedLineSpacingPermille
                == selected.lineSpacingPermille()) {
            cacheHitCount += 1;
            return cachedTypography;
        }
        Typeface family = selected.systemTypeface();
        Paint[] paints = new Paint[] {
            paint(textPx, family, Typeface.NORMAL),
            paint(textPx, family, Typeface.BOLD),
            paint(textPx, family, Typeface.ITALIC),
            paint(textPx, family, Typeface.BOLD_ITALIC)
        };

        int ascent = 0;
        int descent = 0;
        int minimumLeft = 0;
        int maximumRight = 0;
        int maximumAdvance = 0;
        Rect bounds = new Rect();
        int[][] advances = new int[STYLE_COUNT][GLYPH_COUNT];
        for (int style = 0; style < STYLE_COUNT; ++style) {
            Paint.FontMetricsInt font = paints[style].getFontMetricsInt();
            ascent = Math.max(ascent, -font.ascent);
            descent = Math.max(descent, font.descent);
            for (int glyph = 0; glyph < GLYPH_COUNT; ++glyph) {
                String text = textForCodepoint(CODEPOINTS[glyph]);
                paints[style].getTextBounds(text, 0, 1, bounds);
                minimumLeft = Math.min(minimumLeft, bounds.left);
                maximumRight = Math.max(maximumRight, bounds.right);
                ascent = Math.max(ascent, -bounds.top);
                descent = Math.max(descent, bounds.bottom);
                int advance = Math.max(
                    Math.round(paints[style].measureText(text)), 1);
                advances[style][glyph] = advance;
                maximumAdvance = Math.max(maximumAdvance, advance);
            }
        }

        int padding = Math.max(textPx / 8, 3);
        int originX = padding - minimumLeft;
        int baselineY = padding + ascent;
        int cellWidth = Math.max(
            maximumAdvance,
            maximumRight - minimumLeft) + padding * 2;
        int cellHeight = ascent + descent + padding * 2;
        int rowsPerStyle =
            (GLYPH_COUNT + COLUMN_COUNT - 1) / COLUMN_COUNT;
        long atlasWidthLong = (long)cellWidth * COLUMN_COUNT;
        long atlasHeightLong =
            (long)cellHeight * rowsPerStyle * STYLE_COUNT;
        if (atlasWidthLong <= 0 || atlasHeightLong <= 0
            || atlasWidthLong > MAX_ATLAS_DIMENSION
            || atlasHeightLong > MAX_ATLAS_DIMENSION
            || atlasWidthLong * atlasHeightLong > MAX_ATLAS_BYTES) {
            throw new IllegalArgumentException(
                "Typography atlas dimensions exceed their bounds");
        }
        int atlasWidth = (int)atlasWidthLong;
        int atlasHeight = (int)atlasHeightLong;
        Bitmap bitmap = Bitmap.createBitmap(
            atlasWidth, atlasHeight, Bitmap.Config.ALPHA_8);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0);
            for (int style = 0; style < STYLE_COUNT; ++style) {
                for (int glyph = 0; glyph < GLYPH_COUNT; ++glyph) {
                    int column = glyph % COLUMN_COUNT;
                    int row = glyph / COLUMN_COUNT +
                        style * rowsPerStyle;
                    canvas.drawText(
                        textForCodepoint(CODEPOINTS[glyph]),
                        column * cellWidth + originX,
                        row * cellHeight + baselineY,
                        paints[style]);
                }
            }

            int stride = bitmap.getRowBytes();
            long alphaBytesLong = (long)stride * atlasHeight;
            if (stride < atlasWidth || alphaBytesLong <= 0
                || alphaBytesLong > MAX_ATLAS_BYTES) {
                throw new IllegalArgumentException(
                    "Typography atlas storage exceeds its bound");
            }
            ByteBuffer pixels = ByteBuffer.allocate((int)alphaBytesLong);
            bitmap.copyPixelsToBuffer(pixels);
            int[] metrics = new int[
                ADVANCE_OFFSET + STYLE_COUNT * GLYPH_COUNT];
            metrics[0] = MAGIC;
            metrics[1] = VERSION;
            metrics[2] = FIRST_CODEPOINT;
            metrics[3] = GLYPH_COUNT;
            metrics[4] = STYLE_COUNT;
            metrics[5] = COLUMN_COUNT;
            metrics[6] = rowsPerStyle;
            metrics[7] = cellWidth;
            metrics[8] = cellHeight;
            metrics[9] = atlasWidth;
            metrics[10] = atlasHeight;
            metrics[11] = stride;
            metrics[12] = textPx;
            metrics[13] = ascent;
            metrics[14] = descent;
            int naturalLineHeight = ascent + descent;
            long requestedLineAdvance =
                ((long)naturalLineHeight
                 * selected.lineSpacingPermille() + 999L) / 1000L;
            metrics[15] = (int)Math.max(
                requestedLineAdvance, cellHeight - padding);
            metrics[16] = originX;
            metrics[17] = baselineY;
            int at = CODEPOINT_OFFSET;
            for (int codepoint : CODEPOINTS) {
                metrics[at++] = codepoint;
            }
            for (int style = 0; style < STYLE_COUNT; ++style) {
                for (int glyph = 0; glyph < GLYPH_COUNT; ++glyph) {
                    metrics[at++] = advances[style][glyph];
                }
            }
            OctavoTypography result =
                new OctavoTypography(metrics, pixels.array());
            cachedTypography = result;
            cachedFontFamilyId = selected.fontFamilyId();
            cachedTextPx = textPx;
            cachedLineSpacingPermille =
                selected.lineSpacingPermille();
            cacheBuildCount += 1;
            return result;
        } finally {
            bitmap.recycle();
        }
    }

    static synchronized void clearCacheForTesting() {
        cachedTypography = null;
        cachedFontFamilyId = -1;
        cachedTextPx = -1;
        cachedLineSpacingPermille = -1;
        cacheHitCount = 0;
        cacheBuildCount = 0;
    }

    static synchronized long cacheHitCountForTesting() {
        return cacheHitCount;
    }

    static synchronized long cacheBuildCountForTesting() {
        return cacheBuildCount;
    }

    static int codepointForGlyphForTesting(int glyph) {
        if (glyph < 0 || glyph >= GLYPH_COUNT) {
            throw new IllegalArgumentException("Glyph index is out of range");
        }
        return CODEPOINTS[glyph];
    }

    private static int[] buildCodepoints() {
        int asciiCount = LAST_ASCII_CODEPOINT - FIRST_CODEPOINT + 1;
        int latin1Count = LAST_LATIN1_CODEPOINT - FIRST_LATIN1_CODEPOINT + 1;
        int[] result = new int[
            asciiCount + latin1Count + PUBLICATION_CODEPOINTS.length];
        int at = 0;
        for (int value = FIRST_CODEPOINT;
             value <= LAST_ASCII_CODEPOINT;
             ++value) {
            result[at++] = value;
        }
        for (int value = FIRST_LATIN1_CODEPOINT;
             value <= LAST_LATIN1_CODEPOINT;
             ++value) {
            result[at++] = value;
        }
        for (int value : PUBLICATION_CODEPOINTS) {
            if (at > 0 && value <= result[at - 1]) {
                throw new IllegalStateException(
                    "Typography codepoints must be strictly increasing");
            }
            result[at++] = value;
        }
        return result;
    }

    private static String textForCodepoint(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    private static Paint paint(int textPx, Typeface family, int style) {
        Paint result = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG |
            Paint.SUBPIXEL_TEXT_FLAG);
        result.setColor(0xFFFFFFFF);
        result.setTextSize(textPx);
        result.setTypeface(Typeface.create(family, style));
        result.setHinting(Paint.HINTING_ON);
        return result;
    }
}
