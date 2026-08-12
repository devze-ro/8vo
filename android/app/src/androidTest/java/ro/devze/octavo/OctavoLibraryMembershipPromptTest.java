package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoLibraryMembershipPromptTest {
    private static final String DIGEST =
        "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";

    @Test
    public void withdrawAndRestoreAreExplicitAndKeepLocalDataWording() {
        runOnMain(() -> {
            RecordingListener withdraw = new RecordingListener();
            OctavoLibraryMembershipPrompt prompt = prompt(withdraw);
            prompt.showWithdraw(DIGEST, 4096);
            assertEquals(
                OctavoLibraryMembershipPrompt.Mode.WITHDRAW_CONFIRMATION,
                prompt.modeForTesting());
            assertTrue(prompt.statusForTesting().getText().toString()
                           .contains("local EPUB"));
            assertFalse(allText(prompt).contains(DIGEST));
            assertVisible(prompt.primaryForTesting(), true);
            assertVisible(prompt.secondaryForTesting(), false);
            assertSame(
                prompt.primaryForTesting(), prompt.preferredInitialFocus());
            assertTrue(prompt.primaryForTesting().performClick());
            assertEquals(1, withdraw.withdrawCount);

            RecordingListener restore = new RecordingListener();
            OctavoLibraryMembershipPrompt restored = prompt(restore);
            restored.showRestore(DIGEST, 4096);
            assertEquals(
                OctavoLibraryMembershipPrompt.Mode.RESTORE_CONFIRMATION,
                restored.modeForTesting());
            assertTrue(restored.statusForTesting().getText().toString()
                           .contains("does not download"));
            assertTrue(restored.primaryForTesting().performClick());
            assertEquals(1, restore.restoreCount);
        });
    }

    @Test
    public void conflictHasTwoDistinctExplicitResolutionActions() {
        runOnMain(() -> {
            RecordingListener member = new RecordingListener();
            OctavoLibraryMembershipPrompt keep = prompt(member);
            keep.showConflict(DIGEST, 8192);
            assertEquals(
                OctavoLibraryMembershipPrompt.Mode.CONFLICT,
                keep.modeForTesting());
            assertTrue(keep.retainedAttention());
            assertVisible(keep.secondaryForTesting(), true);
            assertVisible(keep.discardForTesting(), true);
            assertEquals("Keep in Library",
                         keep.secondaryForTesting().getText().toString());
            assertEquals("Keep withdrawn",
                         keep.discardForTesting().getText().toString());
            assertTrue(keep.secondaryForTesting().performClick());
            assertEquals(1, member.resolveMemberCount);

            RecordingListener withdrawn = new RecordingListener();
            OctavoLibraryMembershipPrompt remove = prompt(withdrawn);
            remove.showConflict(DIGEST, 8192);
            assertTrue(remove.discardForTesting().performClick());
            assertEquals(1, withdrawn.resolveWithdrawnCount);
        });
    }

    @Test
    public void stagedApprovalAndRetainedFailureExposeOnlyBoundedActions() {
        runOnMain(() -> {
            RecordingListener approve = new RecordingListener();
            OctavoLibraryMembershipPrompt staged = prompt(approve);
            staged.showStagedApproval(3, DIGEST, 7);
            assertEquals(
                OctavoLibraryMembershipPrompt.Mode.STAGED_APPROVAL,
                staged.modeForTesting());
            assertTrue(staged.retainedAttention());
            assertTrue(staged.detailForTesting().getText().toString()
                           .contains("review 7"));
            assertVisible(staged.primaryForTesting(), true);
            assertVisible(staged.discardForTesting(), true);
            assertVisible(staged.retryForTesting(), false);
            assertTrue(staged.primaryForTesting().performClick());
            assertEquals(1, approve.approveCount);

            RecordingListener retry = new RecordingListener();
            OctavoLibraryMembershipPrompt failure = prompt(retry);
            failure.showRetainedAttention(
                "Membership needs an update",
                "Exact future bytes are retained.",
                true);
            assertTrue(failure.statusIsErrorForTesting());
            assertVisible(failure.retryForTesting(), true);
            assertVisible(failure.discardForTesting(), true);
            assertSame(
                failure.retryForTesting(), failure.preferredInitialFocus());
            assertTrue(failure.retryForTesting().performClick());
            assertEquals(1, retry.retryCount);
        });
    }

    @Test
    public void paneTouchTargetsLargeTextAndReducedMotionRemainSafe() {
        runOnMain(() -> {
            Context target = targetContext();
            Configuration configuration = new Configuration(
                target.getResources().getConfiguration());
            configuration.fontScale = 2.0f;
            Context largeText = target.createConfigurationContext(
                configuration);
            OctavoLibraryMembershipPrompt prompt =
                new OctavoLibraryMembershipPrompt(
                    largeText,
                    OctavoAppearance.defaults(),
                    new RecordingListener());
            prompt.showConflict(DIGEST, 4096);

            int minimum = dp(
                target, OctavoDesignTokens.TOUCH_TARGET_DP);
            for (Button button : new Button[] {
                    prompt.primaryForTesting(),
                    prompt.secondaryForTesting(),
                    prompt.discardForTesting(),
                    prompt.retryForTesting() }) {
                assertTrue(button.getMinWidth() >= minimum);
                assertTrue(button.getMinHeight() >= minimum);
            }
            assertTrue(prompt.headingForTesting().isFocusable());
            assertEquals(
                View.ACCESSIBILITY_LIVE_REGION_POLITE,
                prompt.statusForTesting().getAccessibilityLiveRegion());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertNotNull(prompt.getAccessibilityPaneTitle());
                assertTrue(prompt.headingForTesting()
                               .isAccessibilityHeading());
            }

            int width = dp(target, 260);
            int height = dp(target, 300);
            prompt.measure(
                View.MeasureSpec.makeMeasureSpec(
                    width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    height, View.MeasureSpec.AT_MOST));
            prompt.layout(
                0, 0, prompt.getMeasuredWidth(), prompt.getMeasuredHeight());
            assertTrue(prompt.headingForTesting().getLineCount() > 1);
            assertTrue(prompt.getMeasuredHeight() <= height);
            assertNotNull(prompt.scrollForTesting().getChildAt(0));

            OctavoAppearance defaults = OctavoAppearance.defaults();
            OctavoAppearance reduced = OctavoAppearance.create(
                OctavoAppearance.THEME_OLED,
                defaults.fontFamilyId(), defaults.fontSizeSp(),
                defaults.lineSpacingPermille(), defaults.marginsId(),
                defaults.alignmentId(), defaults.publisherColorsId(), true);
            prompt.applyAppearance(reduced);
            assertTrue(prompt.suppressHostMotion());
            assertEquals(null, prompt.getAnimation());
        });
    }

    private static OctavoLibraryMembershipPrompt prompt(
        RecordingListener listener) {
        return new OctavoLibraryMembershipPrompt(
            targetContext(), OctavoAppearance.defaults(), listener);
    }

    private static String allText(OctavoLibraryMembershipPrompt prompt) {
        return prompt.headingForTesting().getText() + "\n"
            + prompt.detailForTesting().getText() + "\n"
            + prompt.statusForTesting().getText() + "\n"
            + prompt.primaryForTesting().getText() + "\n"
            + prompt.secondaryForTesting().getText() + "\n"
            + prompt.discardForTesting().getText() + "\n"
            + prompt.retryForTesting().getText();
    }

    private static void assertVisible(View view, boolean expected) {
        assertEquals(expected ? View.VISIBLE : View.GONE,
                     view.getVisibility());
        assertEquals(expected, view.isEnabled());
    }

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
    }

    private static int dp(Context context, int value) {
        return Math.max(value, Math.round(value
            * context.getResources().getDisplayMetrics().density));
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
            throw new AssertionError("Membership UI assertion failed",
                                     failure.get());
        }
    }

    private static final class RecordingListener
            implements OctavoLibraryMembershipPrompt.Listener {
        int withdrawCount;
        int restoreCount;
        int resolveMemberCount;
        int resolveWithdrawnCount;
        int approveCount;
        int discardCount;
        int retryCount;

        @Override public void onWithdraw() { ++withdrawCount; }
        @Override public void onRestore() { ++restoreCount; }
        @Override public void onResolveMember() { ++resolveMemberCount; }
        @Override public void onResolveWithdrawn() {
            ++resolveWithdrawnCount;
        }
        @Override public void onApproveStaged() { ++approveCount; }
        @Override public void onDiscardStaged() { ++discardCount; }
        @Override public void onRetry() { ++retryCount; }
    }
}
