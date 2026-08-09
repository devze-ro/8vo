package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoProgressSyncPromptTest {
    private static final String CHOICE_HEADING =
        "Another device uses a different progress display";
    private static final String CHOICE_HELPER =
        "This changes only the reader's progress detail and does not move "
            + "your reading place.";

    @Test
    public void allSemanticChoicesHaveAnExactLabelledComparison() {
        runOnMain(() -> {
            OctavoProgressSyncPrompt prompt = newPrompt(
                targetContext(),
                OctavoProgressDisplay.PERCENTAGE,
                OctavoProgressDisplay.LOCATION,
                new RecordingListener());
            OctavoProgressDisplay[] values =
                OctavoProgressDisplay.values();

            for (int index = 0; index < values.length; index++) {
                OctavoProgressDisplay current = values[index];
                OctavoProgressDisplay remote =
                    values[(index + 1) % values.length];
                prompt.updateChoices(current, remote);
                assertComparison(prompt, current, remote);
                assertSame(current, prompt.yoursForTesting());
                assertSame(remote, prompt.otherForTesting());
            }

            OctavoProgressDisplay retainedCurrent =
                prompt.yoursForTesting();
            OctavoProgressDisplay retainedRemote =
                prompt.otherForTesting();
            try {
                prompt.updateChoices(retainedCurrent, retainedCurrent);
                fail("Equal choices must not produce a prompt");
            } catch (IllegalArgumentException expected) {
                assertSame(retainedCurrent, prompt.yoursForTesting());
                assertSame(retainedRemote, prompt.otherForTesting());
            }
            try {
                prompt.updateChoices(null, retainedRemote);
                fail("Missing choices must not produce a prompt");
            } catch (IllegalArgumentException expected) {
                assertSame(retainedCurrent, prompt.yoursForTesting());
                assertSame(retainedRemote, prompt.otherForTesting());
            }
        });
    }

    @Test
    public void choiceWorkingFailureAndRetryStatesAreDeterministic() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoProgressSyncPrompt prompt = newPrompt(
                targetContext(),
                OctavoProgressDisplay.PERCENTAGE,
                OctavoProgressDisplay.LOCATION,
                listener);

            assertChoice(prompt);
            assertTrue(prompt.useDisplayForTesting().performClick());
            assertEquals(1, listener.useDisplayCount);
            assertEquals(0, listener.keepMineCount);
            assertEquals(0, listener.retryCount);
            assertEquals(
                "Applying the other device's progress display.",
                prompt.statusForTesting().getText().toString());
            assertFalse(prompt.statusIsErrorForTesting());
            assertFalse(prompt.useDisplayForTesting().isEnabled());
            assertFalse(prompt.keepMineForTesting().isEnabled());
            assertEquals(View.GONE,
                         prompt.retryForTesting().getVisibility());
            assertSame(prompt.headingForTesting(),
                       prompt.preferredInitialFocus());

            prompt.showChoice();
            assertTrue(prompt.keepMineForTesting().performClick());
            assertEquals(1, listener.useDisplayCount);
            assertEquals(1, listener.keepMineCount);

            String failureHeading =
                "Saved progress display needs attention";
            String failureMessage =
                "The saved progress display could not be finalized.";
            prompt.showRetryableFailure(failureHeading, failureMessage);
            assertEquals(failureHeading,
                         prompt.headingForTesting().getText().toString());
            assertEquals(failureMessage,
                         prompt.statusForTesting().getText().toString());
            assertTrue(prompt.statusIsErrorForTesting());
            assertEquals(View.GONE,
                         prompt.useDisplayForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.keepMineForTesting().getVisibility());
            assertEquals(View.VISIBLE,
                         prompt.retryForTesting().getVisibility());
            assertTrue(prompt.retryForTesting().isEnabled());
            assertSame(prompt.retryForTesting(),
                       prompt.preferredInitialFocus());
            assertAccessibleError(prompt.statusForTesting(), failureMessage);

            assertTrue(prompt.retryForTesting().performClick());
            assertEquals(1, listener.retryCount);
            assertEquals(
                "Retrying the progress display update.",
                prompt.statusForTesting().getText().toString());
            assertFalse(prompt.statusIsErrorForTesting());
            assertFalse(prompt.useDisplayForTesting().isEnabled());
            assertFalse(prompt.keepMineForTesting().isEnabled());
            assertEquals(View.GONE,
                         prompt.retryForTesting().getVisibility());

            prompt.showRetryableFailure("Remote update failed.");
            assertEquals(CHOICE_HEADING,
                         prompt.headingForTesting().getText().toString());
            assertTrue(prompt.statusIsErrorForTesting());

            prompt.showChoice();
            assertChoice(prompt);
        });
    }

    @Test
    public void recoveryOnlyStateCannotExposeAFakeRemoteChoice() {
        runOnMain(() -> {
            Context context = targetContext();
            OctavoProgressSyncPrompt prompt =
                new OctavoProgressSyncPrompt(
                    context,
                    OctavoAppearance.defaults(),
                    OctavoProgressDisplay.PERCENTAGE,
                    new RecordingListener());

            assertSame(OctavoProgressDisplay.PERCENTAGE,
                       prompt.yoursForTesting());
            assertNull(prompt.otherForTesting());
            assertEquals(View.GONE,
                         prompt.helperForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.comparisonForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.useDisplayForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.keepMineForTesting().getVisibility());
            assertEquals(View.VISIBLE,
                         prompt.retryForTesting().getVisibility());
            assertTrue(prompt.retryForTesting().isEnabled());
            assertTrue(prompt.statusIsErrorForTesting());
            assertEquals("Progress display update needs attention",
                         prompt.headingForTesting().getText().toString());
            assertEquals(
                "The progress display update could not be completed.",
                prompt.statusForTesting().getText().toString());
            assertSame(prompt.retryForTesting(),
                       prompt.preferredInitialFocus());

            try {
                prompt.showChoice();
                fail("Recovery-only state must not expose a fake choice");
            } catch (IllegalStateException expected) {
                assertEquals(View.GONE,
                             prompt.useDisplayForTesting().getVisibility());
                assertEquals(View.VISIBLE,
                             prompt.retryForTesting().getVisibility());
            }

            prompt.showWorking("Retrying saved progress display.");
            assertEquals(View.GONE,
                         prompt.helperForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.useDisplayForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.keepMineForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.retryForTesting().getVisibility());
            assertSame(prompt.headingForTesting(),
                       prompt.preferredInitialFocus());

            prompt.showRetryableFailure(
                "Still could not save the progress display.");
            assertEquals("Progress display update needs attention",
                         prompt.headingForTesting().getText().toString());
            assertEquals(View.VISIBLE,
                         prompt.retryForTesting().getVisibility());

            prompt.updateChoices(
                OctavoProgressDisplay.PERCENTAGE,
                OctavoProgressDisplay.CHAPTER);
            assertEquals(View.VISIBLE,
                         prompt.helperForTesting().getVisibility());
            assertEquals(View.VISIBLE,
                         prompt.comparisonForTesting().getVisibility());
            assertChoice(prompt);
        });
    }

    @Test
    public void accessibilityFocusAndTargetsAreExplicit() {
        runOnMain(() -> {
            Context context = targetContext();
            OctavoProgressSyncPrompt prompt = newPrompt(
                context,
                OctavoProgressDisplay.PERCENTAGE,
                OctavoProgressDisplay.LOCATION,
                new RecordingListener());

            assertTrue(prompt.headingForTesting().isFocusable());
            assertTrue(prompt.headingForTesting().isFocusableInTouchMode());
            assertTrue(prompt.useDisplayForTesting().isFocusable());
            assertTrue(
                prompt.useDisplayForTesting().isFocusableInTouchMode());
            assertTrue(prompt.keepMineForTesting().isFocusable());
            assertTrue(prompt.keepMineForTesting().isFocusableInTouchMode());
            assertTrue(prompt.retryForTesting().isFocusable());
            assertTrue(prompt.retryForTesting().isFocusableInTouchMode());
            assertSame(prompt.useDisplayForTesting(),
                       prompt.preferredInitialFocus());

            int minimum = dp(context, OctavoDesignTokens.TOUCH_TARGET_DP);
            assertTarget(prompt.useDisplayForTesting(), minimum);
            assertTarget(prompt.keepMineForTesting(), minimum);
            assertTarget(prompt.retryForTesting(), minimum);
            assertTrue(prompt.comparisonForTesting().getMinHeight()
                       >= minimum);
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                         prompt.comparisonForTesting()
                             .getImportantForAccessibility());

            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                         prompt.statusForTesting()
                             .getAccessibilityLiveRegion());
            assertEquals("Progress display confirmation",
                         prompt.scrollForTesting()
                             .getContentDescription().toString());
            if (Build.VERSION.SDK_INT >= 28) {
                assertEquals("Progress display confirmation",
                             prompt.getAccessibilityPaneTitle().toString());
                assertTrue(prompt.headingForTesting()
                               .isAccessibilityHeading());
            }

            prompt.showWorking("Updating the progress display.");
            assertSame(prompt.headingForTesting(),
                       prompt.preferredInitialFocus());
            prompt.showRetryableFailure("Update failed.");
            assertSame(prompt.retryForTesting(),
                       prompt.preferredInitialFocus());
        });
    }

    @Test
    public void twoHundredPercentTextWrapsInABoundedScrollablePrompt() {
        runOnMain(() -> {
            Context target = targetContext();
            Configuration configuration = new Configuration(
                target.getResources().getConfiguration());
            configuration.fontScale = 2.0f;
            Context largeText = target.createConfigurationContext(
                configuration);
            OctavoProgressSyncPrompt prompt = newPrompt(
                largeText,
                OctavoProgressDisplay.PERCENTAGE,
                OctavoProgressDisplay.LOCATION,
                new RecordingListener());

            int width = dp(target, 280);
            int height = dp(target, 360);
            prompt.measure(
                View.MeasureSpec.makeMeasureSpec(
                    width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    height, View.MeasureSpec.AT_MOST));
            prompt.layout(
                0, 0, prompt.getMeasuredWidth(), prompt.getMeasuredHeight());

            assertEquals(2.0f,
                         largeText.getResources().getConfiguration()
                             .fontScale,
                         0.001f);
            assertTrue(prompt.headingForTesting().getLineCount() > 1);
            assertTrue(prompt.helperForTesting().getLineCount() > 1);
            assertTrue(prompt.getMeasuredHeight() <= height);
            assertTrue(prompt.scrollForTesting().getMeasuredHeight()
                       <= height);
            View scrollContent = prompt.scrollForTesting().getChildAt(0);
            assertNotNull(scrollContent);
            assertTrue(scrollContent.getMeasuredHeight()
                       > prompt.scrollForTesting().getMeasuredHeight());
            assertTrue(prompt.scrollForTesting().canScrollVertically(1));
        });
    }

    @Test
    public void rethemeAndReducedMotionRemainHostVisible() {
        runOnMain(() -> {
            OctavoAppearance current = OctavoAppearance.defaults();
            OctavoAppearance updated = current.withTheme(
                OctavoAppearance.THEME_OLED).withReducedMotion(true);
            OctavoProgressSyncPrompt prompt = newPrompt(
                targetContext(),
                OctavoProgressDisplay.PERCENTAGE,
                OctavoProgressDisplay.LOCATION,
                new RecordingListener());

            int initialHeadingColor =
                prompt.headingForTesting().getCurrentTextColor();
            int initialOverlay = prompt.overlayColor();
            assertFalse(prompt.suppressHostMotion());
            assertNull(prompt.getAnimation());

            prompt.applyAppearance(updated);
            OctavoDesignTokens updatedTokens =
                OctavoDesignTokens.forAppearance(updated);
            assertEquals(updatedTokens.textPrimary,
                         prompt.headingForTesting().getCurrentTextColor());
            assertEquals(updatedTokens.overlay, prompt.overlayColor());
            assertNotEquals(initialHeadingColor,
                            prompt.headingForTesting()
                                .getCurrentTextColor());
            assertNotEquals(initialOverlay, prompt.overlayColor());
            assertTrue(prompt.suppressHostMotion());
            assertNull(prompt.getAnimation());

            prompt.applyAppearance(updated.withReducedMotion(false));
            assertFalse(prompt.suppressHostMotion());
        });
    }

    private static OctavoProgressSyncPrompt newPrompt(
        Context context,
        OctavoProgressDisplay current,
        OctavoProgressDisplay remote,
        RecordingListener listener) {
        return new OctavoProgressSyncPrompt(
            context,
            OctavoAppearance.defaults(),
            current,
            remote,
            listener);
    }

    private static void assertChoice(OctavoProgressSyncPrompt prompt) {
        assertEquals(CHOICE_HEADING,
                     prompt.headingForTesting().getText().toString());
        assertEquals(CHOICE_HELPER,
                     prompt.helperForTesting().getText().toString());
        assertEquals(
            "Choose whether to use the other device's progress display or "
                + "keep yours.",
            prompt.statusForTesting().getText().toString());
        assertFalse(prompt.statusIsErrorForTesting());
        assertEquals(View.VISIBLE,
                     prompt.useDisplayForTesting().getVisibility());
        assertEquals(View.VISIBLE,
                     prompt.keepMineForTesting().getVisibility());
        assertEquals(View.GONE,
                     prompt.retryForTesting().getVisibility());
        assertTrue(prompt.useDisplayForTesting().isEnabled());
        assertTrue(prompt.keepMineForTesting().isEnabled());
        assertFalse(prompt.retryForTesting().isEnabled());
        assertSame(prompt.useDisplayForTesting(),
                   prompt.preferredInitialFocus());
        assertEquals("Use this display",
                     prompt.useDisplayForTesting().getText().toString());
        assertEquals("Keep mine",
                     prompt.keepMineForTesting().getText().toString());
        assertEquals("Retry",
                     prompt.retryForTesting().getText().toString());
    }

    private static void assertComparison(
        OctavoProgressSyncPrompt prompt,
        OctavoProgressDisplay current,
        OctavoProgressDisplay remote) {
        assertEquals(
            "Progress display\nYours: " + current.label()
                + "\nOther device: " + remote.label(),
            prompt.comparisonForTesting().getText().toString());
        assertEquals(
            "Progress display. Yours: " + current.label()
                + ". Other device: " + remote.label() + ".",
            prompt.comparisonForTesting()
                .getContentDescription().toString());
    }

    private static void assertTarget(TextView view, int minimum) {
        assertTrue(view.getMinWidth() >= minimum);
        assertTrue(view.getMinHeight() >= minimum);
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

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
    }

    private static int dp(Context context, int value) {
        return Math.max(
            1,
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
            implements OctavoProgressSyncPrompt.Listener {
        int useDisplayCount;
        int keepMineCount;
        int retryCount;

        @Override
        public void onUseDisplay() {
            ++useDisplayCount;
        }

        @Override
        public void onKeepMine() {
            ++keepMineCount;
        }

        @Override
        public void onRetry() {
            ++retryCount;
        }
    }
}
