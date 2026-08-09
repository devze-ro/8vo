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
public final class OctavoAppearanceSyncPromptTest {
    private static final String CHOICE_HEADING =
        "Another device uses different reading settings";

    @Test
    public void allEightSemanticDifferencesAreLabelledAndBounded() {
        runOnMain(() -> {
            OctavoAppearance current = OctavoAppearance.defaults();
            OctavoAppearance remote = allDifferentProfile();
            OctavoAppearanceSyncPrompt prompt = newPrompt(
                targetContext(), current, remote, new RecordingListener());

            assertEquals(8, prompt.differenceCountForTesting());
            assertDifference(
                prompt, 0, "Theme", "Paper", "OLED");
            assertDifference(
                prompt, 1, "Font", "Literary", "Clear");
            assertDifference(
                prompt, 2, "Text size", "Standard", "Largest");
            assertDifference(
                prompt, 3, "Line spacing", "Classic", "Spacious");
            assertDifference(
                prompt, 4, "Page width", "Balanced", "Focused width");
            assertDifference(
                prompt, 5, "Alignment", "Publisher", "Ragged right");
            assertDifference(
                prompt, 6, "Publisher colors", "Theme safe",
                "Allow publisher colors");
            assertDifference(
                prompt, 7, "Reduced motion", "Off", "On");
            assertNull(prompt.differenceForTesting(-1));
            assertNull(prompt.differenceForTesting(8));
            assertEquals(8,
                         prompt.differenceListForTesting().getChildCount());

            OctavoAppearance oneDifference = current.withTheme(
                OctavoAppearance.THEME_SEPIA);
            prompt.updateProfiles(current, oneDifference);
            assertEquals(1, prompt.differenceCountForTesting());
            assertDifference(prompt, 0, "Theme", "Paper", "Sepia");
            assertSame(current, prompt.presentedProfileForTesting());
            assertSame(oneDifference, prompt.remoteProfileForTesting());

            try {
                prompt.updateProfiles(current, current);
                fail("Equal profiles must not produce an empty prompt");
            } catch (IllegalArgumentException expected) {
                assertEquals(1, prompt.differenceCountForTesting());
                assertSame(current, prompt.presentedProfileForTesting());
                assertSame(oneDifference,
                           prompt.remoteProfileForTesting());
            }
        });
    }

    @Test
    public void choiceWorkingFailureAndRetryStatesAreDeterministic() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoAppearanceSyncPrompt prompt = newPrompt(
                targetContext(),
                OctavoAppearance.defaults(),
                allDifferentProfile(),
                listener);

            assertChoice(prompt);
            assertTrue(prompt.useSettingsForTesting().performClick());
            assertEquals(1, listener.useSettingsCount);
            assertEquals(0, listener.keepMineCount);
            assertEquals(0, listener.retryCount);
            assertEquals("Applying the other device's reading settings.",
                         prompt.statusForTesting().getText().toString());
            assertFalse(prompt.statusIsErrorForTesting());
            assertFalse(prompt.useSettingsForTesting().isEnabled());
            assertFalse(prompt.keepMineForTesting().isEnabled());
            assertEquals(View.GONE,
                         prompt.retryForTesting().getVisibility());
            assertSame(prompt.headingForTesting(),
                       prompt.preferredInitialFocus());

            prompt.showChoice();
            assertTrue(prompt.keepMineForTesting().performClick());
            assertEquals(1, listener.useSettingsCount);
            assertEquals(1, listener.keepMineCount);

            String failureHeading =
                "Saved reading settings need attention";
            String failureMessage =
                "The saved settings could not be finalized.";
            prompt.showRetryableFailure(failureHeading, failureMessage);
            assertEquals(failureHeading,
                         prompt.headingForTesting().getText().toString());
            assertEquals(failureMessage,
                         prompt.statusForTesting().getText().toString());
            assertTrue(prompt.statusIsErrorForTesting());
            assertEquals(View.GONE,
                         prompt.useSettingsForTesting().getVisibility());
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
            assertEquals("Retrying the reading settings update.",
                         prompt.statusForTesting().getText().toString());
            assertFalse(prompt.statusIsErrorForTesting());
            assertFalse(prompt.useSettingsForTesting().isEnabled());
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
    public void failureOnlyRecoveryOwnsNoFakeRemoteChoice() {
        runOnMain(() -> {
            Context context = targetContext();
            OctavoAppearance current = OctavoAppearance.defaults();
            OctavoAppearanceSyncPrompt prompt =
                new OctavoAppearanceSyncPrompt(
                    context, current, new RecordingListener());

            assertSame(current, prompt.presentedProfileForTesting());
            assertNull(prompt.remoteProfileForTesting());
            assertEquals(0, prompt.differenceCountForTesting());
            assertEquals(View.GONE,
                         prompt.explanationForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.differencesHeadingForTesting()
                             .getVisibility());
            assertEquals(View.GONE,
                         prompt.differenceListForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.useSettingsForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.keepMineForTesting().getVisibility());
            assertEquals(View.VISIBLE,
                         prompt.retryForTesting().getVisibility());
            assertTrue(prompt.retryForTesting().isEnabled());
            assertTrue(prompt.statusIsErrorForTesting());
            assertEquals("Reading settings update needs attention",
                         prompt.headingForTesting().getText().toString());
            assertEquals(
                "The reading settings update could not be completed.",
                prompt.statusForTesting().getText().toString());
            assertSame(prompt.retryForTesting(),
                       prompt.preferredInitialFocus());

            try {
                prompt.showChoice();
                fail("Failure-only state must not expose a fake choice");
            } catch (IllegalStateException expected) {
                assertEquals(View.GONE,
                             prompt.useSettingsForTesting().getVisibility());
                assertEquals(View.VISIBLE,
                             prompt.retryForTesting().getVisibility());
            }

            prompt.showWorking("Retrying saved settings.");
            assertEquals(View.GONE,
                         prompt.explanationForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.useSettingsForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.keepMineForTesting().getVisibility());
            assertEquals(View.GONE,
                         prompt.retryForTesting().getVisibility());
            assertSame(prompt.headingForTesting(),
                       prompt.preferredInitialFocus());

            prompt.showRetryableFailure("Still could not save settings.");
            assertEquals("Reading settings update needs attention",
                         prompt.headingForTesting().getText().toString());
            assertEquals(View.VISIBLE,
                         prompt.retryForTesting().getVisibility());

            prompt.updateProfiles(
                current,
                current.withTheme(OctavoAppearance.THEME_SEPIA));
            assertEquals(View.VISIBLE,
                         prompt.explanationForTesting().getVisibility());
            assertEquals(View.VISIBLE,
                         prompt.differencesHeadingForTesting()
                             .getVisibility());
            assertEquals(View.VISIBLE,
                         prompt.differenceListForTesting().getVisibility());
            assertEquals(1, prompt.differenceCountForTesting());
            assertChoice(prompt);
        });
    }

    @Test
    public void modalAccessibilityFocusAndTargetsAreExplicit() {
        runOnMain(() -> {
            Context context = targetContext();
            OctavoAppearanceSyncPrompt prompt = newPrompt(
                context,
                OctavoAppearance.defaults(),
                allDifferentProfile(),
                new RecordingListener());

            assertTrue(prompt.headingForTesting().isFocusable());
            assertTrue(prompt.headingForTesting().isFocusableInTouchMode());
            assertTrue(prompt.useSettingsForTesting().isFocusable());
            assertTrue(
                prompt.useSettingsForTesting().isFocusableInTouchMode());
            assertTrue(prompt.keepMineForTesting().isFocusable());
            assertTrue(prompt.keepMineForTesting().isFocusableInTouchMode());
            assertTrue(prompt.retryForTesting().isFocusable());
            assertTrue(prompt.retryForTesting().isFocusableInTouchMode());
            assertSame(prompt.useSettingsForTesting(),
                       prompt.preferredInitialFocus());

            int minimum = dp(context, OctavoDesignTokens.TOUCH_TARGET_DP);
            assertTarget(prompt.useSettingsForTesting(), minimum);
            assertTarget(prompt.keepMineForTesting(), minimum);
            assertTarget(prompt.retryForTesting(), minimum);
            for (int index = 0;
                 index < prompt.differenceCountForTesting(); index++) {
                TextView row = prompt.differenceForTesting(index);
                assertNotNull(row);
                assertTrue(row.getMinHeight() >= minimum);
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                             row.getImportantForAccessibility());
            }

            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                         prompt.statusForTesting()
                             .getAccessibilityLiveRegion());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                         prompt.differenceListForTesting()
                             .getImportantForAccessibility());
            if (Build.VERSION.SDK_INT >= 28) {
                assertEquals("Reading settings confirmation",
                             prompt.getAccessibilityPaneTitle().toString());
                assertTrue(prompt.headingForTesting()
                               .isAccessibilityHeading());
                assertTrue(prompt.differencesHeadingForTesting()
                               .isAccessibilityHeading());
            }

            prompt.showWorking("Updating settings.");
            assertSame(prompt.headingForTesting(),
                       prompt.preferredInitialFocus());
            prompt.showRetryableFailure("Update failed.");
            assertSame(prompt.retryForTesting(),
                       prompt.preferredInitialFocus());
        });
    }

    @Test
    public void twoHundredPercentTextWrapsInsideABoundedScrollablePrompt() {
        runOnMain(() -> {
            Context target = targetContext();
            Configuration configuration = new Configuration(
                target.getResources().getConfiguration());
            configuration.fontScale = 2.0f;
            Context largeText = target.createConfigurationContext(
                configuration);
            OctavoAppearanceSyncPrompt prompt = newPrompt(
                largeText,
                OctavoAppearance.defaults(),
                allDifferentProfile(),
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
            OctavoAppearance remote = allDifferentProfile();
            OctavoAppearanceSyncPrompt prompt = newPrompt(
                targetContext(), current, remote, new RecordingListener());

            int initialHeadingColor =
                prompt.headingForTesting().getCurrentTextColor();
            int initialOverlay = prompt.overlayColor();
            assertFalse(prompt.suppressHostMotion());
            assertNull(prompt.getAnimation());

            prompt.applyAppearance(remote);
            OctavoDesignTokens remoteTokens =
                OctavoDesignTokens.forAppearance(remote);
            assertEquals(remoteTokens.textPrimary,
                         prompt.headingForTesting().getCurrentTextColor());
            assertEquals(remoteTokens.overlay, prompt.overlayColor());
            assertNotEquals(initialHeadingColor,
                            prompt.headingForTesting()
                                .getCurrentTextColor());
            assertNotEquals(initialOverlay, prompt.overlayColor());
            assertTrue(prompt.suppressHostMotion());
            assertNull(prompt.getAnimation());

            prompt.applyAppearance(remote.withReducedMotion(false));
            assertFalse(prompt.suppressHostMotion());
        });
    }

    private static OctavoAppearanceSyncPrompt newPrompt(
        Context context,
        OctavoAppearance current,
        OctavoAppearance remote,
        RecordingListener listener) {
        return new OctavoAppearanceSyncPrompt(
            context, current, remote, listener);
    }

    private static OctavoAppearance allDifferentProfile() {
        return OctavoAppearance.create(
            OctavoAppearance.THEME_OLED,
            OctavoAppearance.FONT_FAMILY_CLEAR,
            28,
            1500,
            OctavoAppearance.MARGINS_FOCUSED,
            OctavoAppearance.ALIGNMENT_RAGGED_RIGHT,
            OctavoAppearance.PUBLISHER_COLORS_ALLOW,
            true);
    }

    private static void assertChoice(OctavoAppearanceSyncPrompt prompt) {
        assertEquals(CHOICE_HEADING,
                     prompt.headingForTesting().getText().toString());
        assertEquals(
            "Choose whether to use the other device's settings or keep "
                + "yours.",
            prompt.statusForTesting().getText().toString());
        assertFalse(prompt.statusIsErrorForTesting());
        assertEquals(View.VISIBLE,
                     prompt.useSettingsForTesting().getVisibility());
        assertEquals(View.VISIBLE,
                     prompt.keepMineForTesting().getVisibility());
        assertEquals(View.GONE,
                     prompt.retryForTesting().getVisibility());
        assertTrue(prompt.useSettingsForTesting().isEnabled());
        assertTrue(prompt.keepMineForTesting().isEnabled());
        assertFalse(prompt.retryForTesting().isEnabled());
        assertSame(prompt.useSettingsForTesting(),
                   prompt.preferredInitialFocus());
    }

    private static void assertDifference(
        OctavoAppearanceSyncPrompt prompt,
        int index,
        String label,
        String current,
        String remote) {
        TextView row = prompt.differenceForTesting(index);
        assertNotNull(row);
        assertEquals(label + "\nCurrent: " + current
                         + "\nOther device: " + remote,
                     row.getText().toString());
        assertEquals(label + ". Current: " + current
                         + ". Other device: " + remote + ".",
                     row.getContentDescription().toString());
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
            implements OctavoAppearanceSyncPrompt.Listener {
        int useSettingsCount;
        int keepMineCount;
        int retryCount;

        @Override
        public void onUseSettings() {
            ++useSettingsCount;
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
