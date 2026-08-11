package ro.devze.octavo;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;

/**
 * Opens a fully hashed staging file through Reader0 without installing a
 * Surface or changing the active reader. The caller still owns content
 * identity, durable publication, and every transfer decision.
 */
final class OctavoManagedEpubValidator {
    static final class Result {
        final boolean valid;
        final String title;

        private Result(boolean valid, String title) {
            this.valid = valid;
            this.title = title;
        }
    }

    private OctavoManagedEpubValidator() {
    }

    static Result validate(Context context,
                           File stagedFile,
                           OctavoAppearance appearance) {
        if (context == null || stagedFile == null || !stagedFile.isFile()) {
            return new Result(false, null);
        }
        OctavoAppearance selected = appearance == null
            ? OctavoAppearance.defaults() : appearance;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(selected);
        OctavoTypography typography;
        try {
            typography = OctavoTypography.create(context, selected);
        } catch (RuntimeException exception) {
            return new Result(false, null);
        }

        long handle = 0;
        try {
            handle = OctavoNative.create(
                context.getFilesDir().getAbsolutePath(),
                context.getCacheDir().getAbsolutePath(),
                stagedFile.getAbsolutePath(),
                0,
                0,
                false,
                false,
                false,
                selected.nativeConfig(),
                tokens.nativeUi0Colors(),
                tokens.annotationHighlightColors(),
                typography.metrics,
                typography.alpha,
                SystemClock.uptimeMillis());
            if (handle == 0) {
                return new Result(false, null);
            }
            String title = OctavoNative.documentTitle(handle);
            if (title == null || title.trim().isEmpty()) {
                return new Result(false, null);
            }
            return new Result(true, title);
        } catch (RuntimeException exception) {
            return new Result(false, null);
        } finally {
            if (handle != 0) {
                OctavoNative.destroy(handle);
            }
        }
    }
}
