package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibrarySyncPromptTest {
    private static final String CATALOG_DIGEST =
        "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";
    private static final String BOOK_DIGEST =
        "fedcba9876543210fedcba9876543210"
            + "fedcba9876543210fedcba9876543210";

    @Test
    public void stagedCatalogApprovalAndBackAreExplicitAndBounded() {
        runOnMain(() -> {
            RecordingListener approveListener = new RecordingListener();
            OctavoLibrarySyncPrompt approvePrompt =
                new OctavoLibrarySyncPrompt(
                    targetContext(),
                    OctavoAppearance.defaults(),
                    2,
                    CATALOG_DIGEST,
                    approveListener);

            assertEquals(
                OctavoLibrarySyncPrompt.Mode.CATALOG_APPROVAL,
                approvePrompt.modeForTesting());
            assertEquals(
                "Review Library discovery",
                approvePrompt.headingForTesting().getText().toString());
            assertEquals(
                "2 EPUB identities are ready for review - 0123456789ab",
                approvePrompt.detailForTesting().getText().toString());
            assertFalse(allVisibleText(approvePrompt)
                            .contains(CATALOG_DIGEST));
            assertVisible(approvePrompt.approveForTesting(), true);
            assertVisible(approvePrompt.notNowForTesting(), true);
            assertVisible(approvePrompt.downloadForTesting(), false);
            assertVisible(approvePrompt.ignoreForTesting(), false);
            assertVisible(approvePrompt.retryForTesting(), false);
            assertVisible(approvePrompt.cancelForTesting(), false);
            assertSame(
                approvePrompt.approveForTesting(),
                approvePrompt.preferredInitialFocus());

            assertTrue(approvePrompt.approveForTesting().performClick());
            assertEquals(1, approveListener.approveCount);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.WORKING,
                approvePrompt.modeForTesting());
            assertFalse(approvePrompt.handleBack());

            RecordingListener deferListener = new RecordingListener();
            OctavoLibrarySyncPrompt deferPrompt =
                new OctavoLibrarySyncPrompt(
                    targetContext(),
                    OctavoAppearance.defaults(),
                    1,
                    CATALOG_DIGEST,
                    deferListener);
            assertTrue(deferPrompt.handleBack());
            assertEquals(1, deferListener.deferCatalogCount);
            assertEquals(0, deferListener.dismissOfferCount);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.WORKING,
                deferPrompt.modeForTesting());

            assertCatalogArgumentsRejected(64, CATALOG_DIGEST);
            assertCatalogArgumentsRejected(1, CATALOG_DIGEST.toUpperCase());
        });
    }

    @Test
    public void remoteOfferHasOnlyDownloadNotNowAndIgnoreActions() {
        runOnMain(() -> {
            RecordingListener downloadListener = new RecordingListener();
            OctavoLibrarySyncPrompt downloadPrompt = offerPrompt(
                targetContext(), downloadListener);

            assertEquals(
                OctavoLibrarySyncPrompt.Mode.OFFER,
                downloadPrompt.modeForTesting());
            assertEquals(
                "Book available from another device",
                downloadPrompt.headingForTesting().getText().toString());
            assertEquals(
                "EPUB - 4.0 MiB - fedcba987654",
                downloadPrompt.detailForTesting().getText().toString());
            assertFalse(allVisibleText(downloadPrompt).contains(BOOK_DIGEST));
            assertFalse(allVisibleText(downloadPrompt).contains("title"));
            assertVisible(downloadPrompt.downloadForTesting(), true);
            assertVisible(downloadPrompt.notNowForTesting(), true);
            assertVisible(downloadPrompt.ignoreForTesting(), true);
            assertVisible(downloadPrompt.approveForTesting(), false);
            assertVisible(downloadPrompt.retryForTesting(), false);
            assertVisible(downloadPrompt.cancelForTesting(), false);
            assertSame(
                downloadPrompt.downloadForTesting(),
                downloadPrompt.preferredInitialFocus());

            assertTrue(downloadPrompt.downloadForTesting().performClick());
            assertEquals(1, downloadListener.downloadCount);
            assertEquals(
                "Saving the download request before transferring bytes.",
                downloadPrompt.statusForTesting().getText().toString());
            assertAllCallbacks(downloadListener, 0, 0, 1, 0, 0, 0, 0);

            RecordingListener dismissListener = new RecordingListener();
            OctavoLibrarySyncPrompt dismissPrompt = offerPrompt(
                targetContext(), dismissListener);
            assertTrue(dismissPrompt.handleBack());
            assertAllCallbacks(dismissListener, 0, 0, 0, 1, 0, 0, 0);

            RecordingListener ignoreListener = new RecordingListener();
            OctavoLibrarySyncPrompt ignorePrompt = offerPrompt(
                targetContext(), ignoreListener);
            assertTrue(ignorePrompt.ignoreForTesting().performClick());
            assertAllCallbacks(ignoreListener, 0, 0, 0, 0, 1, 0, 0);

            assertOfferArgumentsRejected("not-a-digest", 1);
            assertOfferArgumentsRejected(BOOK_DIGEST, 0);
            assertOfferArgumentsRejected(BOOK_DIGEST, 536870913L);
        });
    }

    @Test
    public void progressFailureRetryAndCancelNeverRetryImplicitly() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoLibrarySyncPrompt prompt =
                new OctavoLibrarySyncPrompt(
                    targetContext(),
                    OctavoAppearance.defaults(),
                    listener);
            assertAllCallbacks(listener, 0, 0, 0, 0, 0, 0, 0);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE,
                prompt.modeForTesting());
            assertFalse(prompt.handleBack());

            prompt.showTransferProgress(
                BOOK_DIGEST,
                8L * 1024L * 1024L,
                4L * 1024L * 1024L,
                1,
                2);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.WORKING,
                prompt.modeForTesting());
            assertEquals(
                "Downloaded 4.0 MiB of 8.0 MiB - chunk 1 of 2",
                prompt.statusForTesting().getText().toString());
            assertVisible(prompt.cancelForTesting(), true);
            assertSame(
                prompt.cancelForTesting(), prompt.preferredInitialFocus());
            assertFalse(prompt.handleBack());

            String failure = "The next chunk did not match.";
            prompt.showRetryableFailure(
                "EPUB download needs attention", failure, true);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE,
                prompt.modeForTesting());
            assertTrue(prompt.statusIsErrorForTesting());
            assertVisible(prompt.retryForTesting(), true);
            assertVisible(prompt.cancelForTesting(), true);
            assertSame(
                prompt.retryForTesting(), prompt.preferredInitialFocus());
            assertAccessibleError(prompt.statusForTesting(), failure);

            assertTrue(prompt.retryForTesting().performClick());
            assertEquals(1, listener.retryCount);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.WORKING,
                prompt.modeForTesting());
            assertVisible(prompt.retryForTesting(), false);
            assertVisible(prompt.cancelForTesting(), true);
            assertFalse(prompt.statusIsErrorForTesting());

            assertTrue(prompt.cancelForTesting().performClick());
            assertEquals(1, listener.cancelCount);
            assertVisible(prompt.cancelForTesting(), false);
            assertFalse(prompt.handleBack());

            RecordingListener recreatedListener = new RecordingListener();
            OctavoLibrarySyncPrompt recreated =
                new OctavoLibrarySyncPrompt(
                    targetContext(),
                    OctavoAppearance.defaults(),
                    recreatedListener);
            recreated.showRetryableFailure(
                "EPUB download needs attention", failure, true);
            assertAllCallbacks(
                recreatedListener, 0, 0, 0, 0, 0, 0, 0);
            assertEquals(
                OctavoLibrarySyncPrompt.Mode.RETRYABLE_FAILURE,
                recreated.modeForTesting());
        });
    }

    @Test
    public void promptHasNamedPaneHeadingLiveErrorTouchFocusAnd48DpActions() {
        runOnMain(() -> {
            OctavoLibrarySyncPrompt prompt = offerPrompt(
                targetContext(), new RecordingListener());
            int minimum = dp(
                targetContext(), OctavoDesignTokens.TOUCH_TARGET_DP);

            assertTrue(prompt.headingForTesting().isFocusable());
            assertTrue(
                prompt.headingForTesting().isFocusableInTouchMode());
            for (Button button : new Button[] {
                    prompt.approveForTesting(),
                    prompt.downloadForTesting(),
                    prompt.notNowForTesting(),
                    prompt.ignoreForTesting(),
                    prompt.retryForTesting(),
                    prompt.cancelForTesting() }) {
                assertTrue(button.isFocusable());
                assertTrue(button.isFocusableInTouchMode());
                assertTrue(button.getMinWidth() >= minimum);
                assertTrue(button.getMinHeight() >= minimum);
            }
            assertEquals(
                View.ACCESSIBILITY_LIVE_REGION_POLITE,
                prompt.statusForTesting().getAccessibilityLiveRegion());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertEquals(
                    "Book download confirmation",
                    prompt.getAccessibilityPaneTitle().toString());
                assertTrue(
                    prompt.headingForTesting().isAccessibilityHeading());
            }

            prompt.showRetryableFailure(
                "Cleanup needs attention",
                "Partial EPUB cleanup did not finish.",
                false);
            assertVisible(prompt.retryForTesting(), true);
            assertVisible(prompt.cancelForTesting(), false);
            assertSame(
                prompt.retryForTesting(), prompt.preferredInitialFocus());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertEquals(
                    "Library transfer needs attention",
                    prompt.getAccessibilityPaneTitle().toString());
            }
        });
    }

    @Test
    public void twoHundredPercentTextWrapsInsideScrollablePrompt() {
        runOnMain(() -> {
            Context target = targetContext();
            Configuration configuration = new Configuration(
                target.getResources().getConfiguration());
            configuration.fontScale = 2.0f;
            Context largeText = target.createConfigurationContext(
                configuration);
            OctavoLibrarySyncPrompt prompt = offerPrompt(
                largeText, new RecordingListener());

            int width = dp(target, 260);
            int height = dp(target, 300);
            prompt.measure(
                View.MeasureSpec.makeMeasureSpec(
                    width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    height, View.MeasureSpec.AT_MOST));
            prompt.layout(
                0, 0, prompt.getMeasuredWidth(), prompt.getMeasuredHeight());

            assertEquals(
                2.0f,
                largeText.getResources().getConfiguration().fontScale,
                0.001f);
            assertTrue(prompt.headingForTesting().getLineCount() > 1);
            assertTrue(prompt.getMeasuredHeight() <= height);
            assertTrue(
                prompt.scrollForTesting().getMeasuredHeight() <= height);
            View scrollContent = prompt.scrollForTesting().getChildAt(0);
            assertNotNull(scrollContent);
            assertTrue(
                scrollContent.getMeasuredHeight()
                    > prompt.scrollForTesting().getMeasuredHeight());
            assertTrue(prompt.scrollForTesting().canScrollVertically(1));
        });
    }

    @Test
    public void appearanceAndReducedMotionRemainHostVisible() {
        runOnMain(() -> {
            OctavoAppearance initial = OctavoAppearance.defaults();
            OctavoLibrarySyncPrompt prompt = offerPrompt(
                targetContext(), new RecordingListener());
            int initialHeading =
                prompt.headingForTesting().getCurrentTextColor();
            int initialOverlay = prompt.overlayColor();
            assertFalse(prompt.suppressHostMotion());
            assertEquals(null, prompt.getAnimation());

            OctavoAppearance reduced = OctavoAppearance.create(
                OctavoAppearance.THEME_OLED,
                initial.fontFamilyId(),
                initial.fontSizeSp(),
                initial.lineSpacingPermille(),
                initial.marginsId(),
                initial.alignmentId(),
                initial.publisherColorsId(),
                true);
            prompt.applyAppearance(reduced);
            assertEquals(
                OctavoDesignTokens.forAppearance(reduced).libraryText,
                prompt.headingForTesting().getCurrentTextColor());
            assertEquals(
                OctavoDesignTokens.forAppearance(reduced).overlay,
                prompt.overlayColor());
            assertNotEquals(
                initialHeading,
                prompt.headingForTesting().getCurrentTextColor());
            assertNotEquals(initialOverlay, prompt.overlayColor());
            assertTrue(prompt.suppressHostMotion());
            assertEquals(null, prompt.getAnimation());
        });
    }

    private static OctavoLibrarySyncPrompt offerPrompt(
        Context context, RecordingListener listener) {
        return new OctavoLibrarySyncPrompt(
            context,
            OctavoAppearance.defaults(),
            BOOK_DIGEST,
            4L * 1024L * 1024L,
            listener);
    }

    private static void assertCatalogArgumentsRejected(
        int count, String digest) {
        try {
            new OctavoLibrarySyncPrompt(
                targetContext(),
                OctavoAppearance.defaults(),
                count,
                digest,
                new RecordingListener());
            fail("Invalid catalog review arguments must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertOfferArgumentsRejected(
        String digest, long byteCount) {
        try {
            new OctavoLibrarySyncPrompt(
                targetContext(),
                OctavoAppearance.defaults(),
                digest,
                byteCount,
                new RecordingListener());
            fail("Invalid offer arguments must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static String allVisibleText(OctavoLibrarySyncPrompt prompt) {
        return prompt.headingForTesting().getText().toString()
            + "\n" + prompt.detailForTesting().getText()
            + "\n" + prompt.statusForTesting().getText()
            + "\n" + prompt.approveForTesting().getText()
            + "\n" + prompt.downloadForTesting().getText()
            + "\n" + prompt.notNowForTesting().getText()
            + "\n" + prompt.ignoreForTesting().getText()
            + "\n" + prompt.retryForTesting().getText()
            + "\n" + prompt.cancelForTesting().getText();
    }

    private static void assertVisible(View view, boolean expected) {
        assertEquals(expected ? View.VISIBLE : View.GONE,
                     view.getVisibility());
        assertEquals(expected, view.isEnabled());
    }

    private static void assertAccessibleError(TextView status,
                                              String expected) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        try {
            status.onInitializeAccessibilityNodeInfo(info);
            assertNotNull(info.getError());
            assertEquals(expected, info.getError().toString());
        } finally {
            info.recycle();
        }
    }

    private static void assertAllCallbacks(RecordingListener listener,
                                           int approve,
                                           int deferCatalog,
                                           int download,
                                           int dismissOffer,
                                           int ignoreOffer,
                                           int retry,
                                           int cancel) {
        assertEquals(approve, listener.approveCount);
        assertEquals(deferCatalog, listener.deferCatalogCount);
        assertEquals(download, listener.downloadCount);
        assertEquals(dismissOffer, listener.dismissOfferCount);
        assertEquals(ignoreOffer, listener.ignoreOfferCount);
        assertEquals(retry, listener.retryCount);
        assertEquals(cancel, listener.cancelCount);
    }

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
    }

    private static int dp(Context context, int value) {
        return Math.max(
            value,
            Math.round(value
                       * context.getResources()
                           .getDisplayMetrics().density));
    }

    private static void runOnMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        if (failure.get() != null) {
            if (failure.get() instanceof AssertionError) {
                throw (AssertionError)failure.get();
            }
            throw new AssertionError("UI assertion failed", failure.get());
        }
    }

    private static final class RecordingListener
            implements OctavoLibrarySyncPrompt.Listener {
        int approveCount;
        int deferCatalogCount;
        int downloadCount;
        int dismissOfferCount;
        int ignoreOfferCount;
        int retryCount;
        int cancelCount;

        @Override
        public void onApproveCatalog() {
            ++approveCount;
        }

        @Override
        public void onDeferCatalog() {
            ++deferCatalogCount;
        }

        @Override
        public void onDownload() {
            ++downloadCount;
        }

        @Override
        public void onDismissOffer() {
            ++dismissOfferCount;
        }

        @Override
        public void onIgnoreOffer() {
            ++ignoreOfferCount;
        }

        @Override
        public void onRetry() {
            ++retryCount;
        }

        @Override
        public void onCancel() {
            ++cancelCount;
        }
    }
}
