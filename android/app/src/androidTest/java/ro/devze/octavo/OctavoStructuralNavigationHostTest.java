package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.TextAppearanceSpan;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.DisplayMetrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoStructuralNavigationHostTest {
    private static final int FLAGS_READY =
        OctavoNavigation.FLAG_READY
            | OctavoNavigation.FLAG_LOCATION_READY
            | OctavoNavigation.FLAG_PAGE_MEANINGFUL;

    @Test
    public void packetAcceptsOneBasedEndpointsAndExposesNestedState() {
        OctavoNavigation navigation = OctavoNavigation.fromNativePacket(
            nestedPacket(FLAGS_READY, 4, 4),
            nestedLabels());

        assertNotNull(navigation);
        assertTrue(navigation.isReady());
        assertFalse(navigation.isPending());
        assertFalse(navigation.isFallback());
        assertFalse(navigation.isTruncated());
        assertTrue(navigation.isLocationReady());
        assertTrue(navigation.isPageMeaningful());
        assertEquals(3, navigation.totalCount());
        assertEquals(1, navigation.currentRow());
        assertEquals(2, navigation.historyBack());
        assertEquals(1, navigation.historyForward());
        assertEquals(4, navigation.transactionGeneration());
        assertEquals(4, navigation.presentedGeneration());
        assertEquals(17, navigation.currentLocation());
        assertEquals(17, navigation.locationCount());
        assertEquals(100, navigation.percent());
        assertEquals(4, navigation.pageIndex());
        assertEquals(4, navigation.pageCount());
        assertEquals(3, navigation.rowCount());

        OctavoNavigation.Row part = navigation.row(0);
        assertEquals(11, part.navIndex());
        assertEquals(0, part.depth());
        assertFalse(part.isCurrent());
        assertTrue(part.isDestinationValid());
        assertTrue(part.isProgressAvailable());
        assertFalse(part.isSynthetic());
        assertEquals(1, part.locationIndex());
        assertEquals(17, part.locationCount());
        assertEquals(1, part.pageIndex());
        assertEquals(4, part.pageCount());
        assertEquals("Part One", part.label());

        OctavoNavigation.Row current = navigation.row(1);
        assertEquals(12, current.navIndex());
        assertEquals(1, current.depth());
        assertTrue(current.isCurrent());
        assertTrue(current.isDestinationValid());
        assertTrue(current.isProgressAvailable());
        assertEquals(17, current.locationIndex());
        assertEquals(17, current.locationCount());
        assertEquals(4, current.pageIndex());
        assertEquals(4, current.pageCount());

        OctavoNavigation.Row unavailable = navigation.row(2);
        assertEquals(2, unavailable.depth());
        assertFalse(unavailable.isDestinationValid());
        assertFalse(unavailable.isProgressAvailable());
        assertEquals(0, unavailable.locationIndex());
        assertEquals(0, unavailable.locationCount());
        assertEquals(0, unavailable.pageIndex());
        assertEquals(0, unavailable.pageCount());

        List<OctavoNavigation.Row> rows = navigation.rows();
        assertSame(current, rows.get(1));
        try {
            rows.add(part);
            fail("Navigation rows must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void packetRejectsMalformedDataWithoutPartialExposure() {
        long[] valid = nestedPacket(FLAGS_READY, 9, 9);
        String[] labels = nestedLabels();

        assertNull(OctavoNavigation.fromNativePacket(null, labels));
        assertNull(OctavoNavigation.fromNativePacket(valid, null));
        assertNull(OctavoNavigation.fromNativePacket(
            new long[OctavoNavigation.HEADER_COUNT - 1], labels));

        assertRejected(valid, labels, 0, OctavoNavigation.VERSION + 1);
        assertRejected(valid, labels, 1, OctavoNavigation.HEADER_COUNT + 1);
        assertRejected(valid, labels, 2, OctavoNavigation.ROW_STRIDE + 1);
        assertRejected(valid, labels, 3, 1L << 20);
        assertRejected(valid, labels, 4, OctavoNavigation.MAX_ROWS + 1L);
        assertRejected(valid, labels, 5, 2);
        assertRejected(valid, labels, 6, 2);
        assertRejected(valid, labels, 7, -1);
        assertRejected(valid, labels, 8, -1);
        assertRejected(valid, labels, 9, -1);
        assertRejected(valid, labels, 10, 10);
        assertRejected(valid, labels, 11, 0);
        assertRejected(valid, labels, 11, 18);
        assertRejected(valid, labels, 12, 0);
        assertRejected(valid, labels, 13, 101);
        assertRejected(valid, labels, 14, 0);
        assertRejected(valid, labels, 14, 5);
        assertRejected(valid, labels, 15, 0);

        long[] extra = new long[valid.length + 1];
        System.arraycopy(valid, 0, extra, 0, valid.length);
        assertNull(OctavoNavigation.fromNativePacket(extra, labels));
        assertNull(OctavoNavigation.fromNativePacket(
            valid, new String[] {"Part One", "Chapter One"}));
        assertNull(OctavoNavigation.fromNativePacket(
            valid, new String[] {"Part One", null, "Scene"}));
        assertNull(OctavoNavigation.fromNativePacket(
            valid, new String[] {"Part One", "   ", "Scene"}));
        char[] oversizedChars = new char[513];
        java.util.Arrays.fill(oversizedChars, 'x');
        assertNull(OctavoNavigation.fromNativePacket(
            valid,
            new String[] {"Part One", new String(oversizedChars), "Scene"}));

        int first = OctavoNavigation.HEADER_COUNT;
        int second = first + OctavoNavigation.ROW_STRIDE;
        int third = second + OctavoNavigation.ROW_STRIDE;
        assertRejected(valid, labels, first, -1);
        assertRejected(valid, labels, first + 1, 1);
        assertRejected(valid, labels, first + 2, 1L << 20);
        assertRejected(valid, labels, first + 3, -1);
        assertRejected(valid, labels, first + 4, -1);
        assertRejected(valid, labels, first + 5, 0);
        assertRejected(valid, labels, first + 5, 18);
        assertRejected(valid, labels, first + 6, 0);
        assertRejected(valid, labels, first + 7, 101);
        assertRejected(valid, labels, first + 8, 0);
        assertRejected(valid, labels, first + 8, 5);
        assertRejected(valid, labels, first + 9, 0);
        assertRejected(valid, labels, second, valid[first]);
        assertRejected(valid, labels, third + 1, 3);

        long[] missingCurrentFlag = valid.clone();
        missingCurrentFlag[second + 2] &= ~OctavoNavigation.ROW_FLAG_CURRENT;
        assertNull(OctavoNavigation.fromNativePacket(
            missingCurrentFlag, labels));

        long[] duplicateCurrent = valid.clone();
        duplicateCurrent[first + 2] |= OctavoNavigation.ROW_FLAG_CURRENT;
        assertNull(OctavoNavigation.fromNativePacket(
            duplicateCurrent, labels));

        long[] unavailableWithProgress = valid.clone();
        unavailableWithProgress[third + 5] = 1;
        assertNull(OctavoNavigation.fromNativePacket(
            unavailableWithProgress, labels));
    }

    @Test
    public void packetAcceptsDeepReaderHierarchyWithinTheSharedBound() {
        assertEquals(64, OctavoNavigation.MAX_DEPTH);

        long[] depth33Packet = depthChainPacket(33);
        String[] depth33Labels = depthChainLabels(33);
        OctavoNavigation depth33 = OctavoNavigation.fromNativePacket(
            depth33Packet, depth33Labels);
        assertNotNull(depth33);
        assertEquals(33, depth33.row(33).depth());

        /*
         * A snapshot contains at most 64 rows and begins at a root, so 63
         * is the deepest hierarchy level a complete valid packet can reach.
         * The host bound remains 64 to match Reader0's public contract.
         */
        long[] deepestPacket = depthChainPacket(63);
        String[] deepestLabels = depthChainLabels(63);
        OctavoNavigation deepest = OctavoNavigation.fromNativePacket(
            deepestPacket, deepestLabels);
        assertNotNull(deepest);
        assertEquals(63, deepest.row(63).depth());

        long[] beyondReaderBound = depth33Packet.clone();
        int finalDepth = OctavoNavigation.HEADER_COUNT
            + 33 * OctavoNavigation.ROW_STRIDE + 1;
        beyondReaderBound[finalDepth] =
            OctavoNavigation.MAX_DEPTH + 1L;
        assertNull(OctavoNavigation.fromNativePacket(
            beyondReaderBound, depth33Labels));
    }

    @Test
    public void truncatedCurrentNeverAliasesAVisibleRowAndUtf8SurvivesJni() {
        long[] packet = depthChainPacket(63);
        packet[3] |= OctavoNavigation.FLAG_TRUNCATED;
        packet[5] = 65;
        packet[6] = -1;
        OctavoNavigation navigation = OctavoNavigation.fromNativePacket(
            packet, depthChainLabels(63));

        assertNotNull(navigation);
        assertTrue(navigation.isTruncated());
        assertEquals(65, navigation.totalCount());
        assertEquals(64, navigation.rowCount());
        assertEquals(-1, navigation.currentRow());
        for (OctavoNavigation.Row row : navigation.rows()) {
            assertFalse(row.isCurrent());
        }

        assertEquals(
            "R\u00e9sum\u00e9 \u2019 \ud83c\udf19",
            OctavoNative.utf8RoundTripForTesting());
    }

    @Test
    public void navigationSheetMotionStartsFromLogicalEnd() {
        runOnMain(() -> {
            Context target = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
            View panel = new View(target);
            panel.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            assertEquals(24,
                         OctavoActivity.navigationPanelStartTranslationX(
                             panel,
                             24));
            panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            assertEquals(-24,
                         OctavoActivity.navigationPanelStartTranslationX(
                             panel,
                             24));
            View anchor = new View(target);
            View inheritedPanel = new View(target);
            anchor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            OctavoActivity.resolveSideSheetLayoutDirection(
                inheritedPanel, anchor);
            assertEquals(View.LAYOUT_DIRECTION_RTL,
                         inheritedPanel.getLayoutDirection());
            assertEquals(-24,
                         OctavoActivity.navigationPanelStartTranslationX(
                             inheritedPanel,
                             24));
            assertEquals(0,
                         OctavoActivity.navigationPanelStartTranslationX(
                             panel,
                             0));
        });
    }

    @Test
    public void compactLandscapeInsetKeepsSideSheetInsideSafeOverlay() {
        runOnMain(() -> {
            Context target = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
            int windowWidth = 480;
            int windowHeight = 320;
            int lateralInset = 48;
            int statusInset = 24;
            int navigationInset = 48;
            int outerGap = 24;
            Configuration compactConfiguration = new Configuration(
                target.getResources().getConfiguration());
            compactConfiguration.fontScale = 1.3f;
            compactConfiguration.densityDpi = DisplayMetrics.DENSITY_DEFAULT;
            compactConfiguration.screenWidthDp = windowWidth;
            compactConfiguration.screenHeightDp = windowHeight;
            compactConfiguration.orientation =
                Configuration.ORIENTATION_LANDSCAPE;
            Context compact = target.createConfigurationContext(
                compactConfiguration);
            Context themed = new ContextThemeWrapper(
                compact, R.style.Theme_Octavo);
            OctavoNavigationPanel sheet = newPanel(
                themed, new RecordingListener());
            OctavoNavigation navigation = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 4, 4), nestedLabels());
            assertNotNull(navigation);
            sheet.updateSnapshot(navigation);
            sheet.showError(
                "Navigation failed and the last presented page could not be "
                    + "restored. Reopen the book to continue from a known "
                    + "reading position.");
            sheet.goToTabForTesting().performClick();

            FrameLayout root = new FrameLayout(themed);
            root.setPadding(
                lateralInset, statusInset, 0, navigationInset);
            FrameLayout overlay = new FrameLayout(themed);
            root.addView(
                overlay,
                new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            int safeWidth = windowWidth - lateralInset;
            int sheetWidth = OctavoActivity.boundedSideSheetWidth(
                safeWidth, outerGap, 560);
            overlay.addView(
                sheet,
                new FrameLayout.LayoutParams(
                    sheetWidth,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.END));

            root.measure(
                View.MeasureSpec.makeMeasureSpec(
                    windowWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    windowHeight, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, windowWidth, windowHeight);

            assertEquals(safeWidth, overlay.getWidth());
            assertEquals(windowHeight - statusInset - navigationInset,
                         overlay.getHeight());
            assertEquals(safeWidth - outerGap, sheet.getWidth());
            assertEquals(outerGap, sheet.getLeft());
            assertEquals(overlay.getWidth(), sheet.getRight());
            assertEquals(1, sheet.statusForTesting().getMaxLines());
            int minimumTarget = OctavoDesignTokens.TOUCH_TARGET_DP;
            assertTrue(sheet.bodyForTesting().getHeight() >= minimumTarget);
            sheet.applyAppearance(OctavoAppearance.defaults());
            root.measure(
                View.MeasureSpec.makeMeasureSpec(
                    windowWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    windowHeight, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, windowWidth, windowHeight);
            assertEquals(1, sheet.statusForTesting().getMaxLines());
            assertTrue(sheet.bodyForTesting().getHeight() >= minimumTarget);

            ScrollView goTo = sheet.goToScrollForTesting();
            RadioGroup options = sheet.progressOptionsForTesting();
            View finalChoice = options.getChildAt(options.getChildCount() - 1);
            goTo.fullScroll(View.FOCUS_DOWN);
            int maximumScroll = Math.max(
                0, goTo.getChildAt(0).getHeight() - goTo.getHeight());
            goTo.scrollTo(0, maximumScroll);
            int finalChoiceBottom = descendantBottom(finalChoice, goTo);
            assertTrue(
                "finalChoiceBottom=" + finalChoiceBottom
                    + " scrollY=" + goTo.getScrollY()
                    + " height=" + goTo.getHeight()
                    + " contentHeight=" + goTo.getChildAt(0).getHeight()
                    + " visibility=" + goTo.getVisibility(),
                finalChoiceBottom
                    <= goTo.getScrollY() + goTo.getHeight());
            assertFalse(goTo.canScrollVertically(1));
        });
    }

    @Test
    public void retryFailureMessagesDistinguishRollbackFailure() {
        assertEquals(
            "Navigation was not committed because the page could not be "
                + "presented.",
            OctavoSurfaceView.navigationPresentationFailureMessage(1));
        assertEquals(
            "Navigation failed and the last presented page could not be "
                + "restored. Reopen the book.",
            OctavoSurfaceView.navigationPresentationFailureMessage(-1));
    }

    @Test
    public void packetRequiresZeroPairsWhenProgressIsUnavailable() {
        long[] noProgress = nestedPacket(
            OctavoNavigation.FLAG_READY, 2, 2);
        noProgress[11] = 0;
        noProgress[12] = 0;
        noProgress[14] = 0;
        noProgress[15] = 0;
        assertNotNull(OctavoNavigation.fromNativePacket(
            noProgress, nestedLabels()));

        long[] strayLocation = noProgress.clone();
        strayLocation[11] = 1;
        assertNull(OctavoNavigation.fromNativePacket(
            strayLocation, nestedLabels()));

        long[] strayCount = noProgress.clone();
        strayCount[12] = 17;
        assertNull(OctavoNavigation.fromNativePacket(
            strayCount, nestedLabels()));

        long[] strayPage = noProgress.clone();
        strayPage[14] = 1;
        assertNull(OctavoNavigation.fromNativePacket(
            strayPage, nestedLabels()));
    }

    @Test
    public void panelPublishesAccessibleHierarchyAndOneBasedProgress() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoNavigationPanel panel = newPanel(listener);
            OctavoNavigation navigation = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 4, 4), nestedLabels());
            assertNotNull(navigation);
            panel.updateSnapshot(navigation);

            assertSame(navigation, panel.snapshotForTesting());
            assertFalse(panel.isFocusable());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                         panel.getImportantForAccessibility());
            AccessibilityNodeInfo panelInfo =
                panel.createAccessibilityNodeInfo();
            assertFalse(panelInfo.isFocusable());
            panelInfo.recycle();
            if (Build.VERSION.SDK_INT >= 28) {
                assertEquals(panel.getContext().getString(
                                 R.string.reader_navigation),
                             panel.getAccessibilityPaneTitle());
            }
            assertEquals("Current section: Chapter One",
                         panel.statusForTesting().getText().toString());
            assertEquals(
                View.ACCESSIBILITY_LIVE_REGION_POLITE,
                panel.statusForTesting().getAccessibilityLiveRegion());
            assertStatusAccessibility(panel, null);
            assertEquals("Reader navigation",
                         panel.findViewById(R.id.octavo_navigation_title)
                             .getContentDescription());
            assertEquals("Table of contents",
                         panel.findViewById(
                             R.id.octavo_navigation_contents_scroll)
                             .getContentDescription());

            LinearLayout list = panel.contentsListForTesting();
            assertEquals(3, list.getChildCount());
            Ui0NavigationRow part = (Ui0NavigationRow)list.getChildAt(0);
            Ui0NavigationRow current =
                (Ui0NavigationRow)list.getChildAt(1);
            Ui0NavigationRow unavailable =
                (Ui0NavigationRow)list.getChildAt(2);

            assertEquals("Part One\nLocation 1 of 17 | 0 percent | Page 1 of 4",
                         part.getText().toString());
            assertEquals(
                "Chapter One\nCurrent section | Location 17 of 17 | "
                    + "100 percent | Page 4 of 4",
                current.getText().toString());
            assertTrue(part.getContentDescription().toString()
                           .contains("Level 1, Part One"));
            assertTrue(part.getContentDescription().toString()
                           .contains("heading"));
            assertTrue(current.getContentDescription().toString()
                           .contains("Level 2, current section"));
            assertTrue(unavailable.getContentDescription().toString()
                           .contains("destination unavailable"));
            assertTrue(part.isEnabled());
            assertTrue(current.isEnabled());
            assertFalse(unavailable.isEnabled());
            assertEquals(3, part.getMaxLines());
            assertSame(TextUtils.TruncateAt.END, part.getEllipsize());
            RelativeSizeSpan[] captionScales =
                ((Spanned)part.getText()).getSpans(
                    0,
                    part.getText().length(),
                    RelativeSizeSpan.class);
            assertEquals(1, captionScales.length);
            assertEquals(0.875f,
                         captionScales[0].getSizeChange(),
                         0.0f);
            Spanned partText = (Spanned)part.getText();
            int captionStart = partText.toString().indexOf('\n') + 1;
            TextAppearanceSpan[] labelColors = partText.getSpans(
                0, captionStart - 1, TextAppearanceSpan.class);
            TextAppearanceSpan[] captionColors = partText.getSpans(
                captionStart, partText.length(), TextAppearanceSpan.class);
            assertEquals(1, labelColors.length);
            assertEquals(1, captionColors.length);
            assertEquals(panel.hierarchyLabelColorForTesting(),
                         labelColors[0].getTextColor().getDefaultColor());
            assertEquals(panel.hierarchyCaptionColorForTesting(),
                         captionColors[0].getTextColor().getDefaultColor());
            assertFalse(part.isCurrentForTesting());
            assertTrue(current.isCurrentForTesting());
            assertTrue(current.isSelected());
            assertTrue(current.railWidthForTesting() > 0);
            assertEquals(panel.currentRailRadiusForTesting(),
                         current.railRadiusForTesting(),
                         0.01f);
            assertTrue(current.railRadiusForTesting()
                       <= current.railWidthForTesting() / 2.0f);
            LinearLayout.LayoutParams partLayout =
                (LinearLayout.LayoutParams)part.getLayoutParams();
            LinearLayout.LayoutParams currentLayout =
                (LinearLayout.LayoutParams)current.getLayoutParams();
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                         partLayout.width);
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                         currentLayout.width);
            assertEquals(0, partLayout.getMarginStart());
            assertEquals(0, currentLayout.getMarginStart());
            assertEquals(dp(panel.getContext(), 38),
                         panel.hierarchyBaseStartPxForTesting());
            assertEquals(panel.hierarchyBaseStartPxForTesting(),
                         part.getPaddingStart());
            assertEquals(panel.hierarchyTextStartPxForTesting(1),
                         current.getPaddingStart());
            assertTrue(Math.abs(
                           panel.treeIndentPxForTesting()
                               - (current.getPaddingStart()
                                  - part.getPaddingStart()))
                       <= 1);
            assertEquals(28, panel.semanticTextSizeSpForTesting(
                Ui0AndroidThemeSnapshot.TypographyRole.PAGE_TITLE));
            assertEquals(20, panel.semanticTextSizeSpForTesting(
                Ui0AndroidThemeSnapshot.TypographyRole.SECTION_TITLE));
            assertEquals(16, panel.semanticTextSizeSpForTesting(
                Ui0AndroidThemeSnapshot.TypographyRole.BODY));
            assertEquals(14, panel.semanticTextSizeSpForTesting(
                Ui0AndroidThemeSnapshot.TypographyRole.CAPTION));
            assertEquals(14, panel.semanticTextSizeSpForTesting(
                Ui0AndroidThemeSnapshot.TypographyRole.METADATA));
            assertEquals(16, panel.semanticTextSizeSpForTesting(
                Ui0AndroidThemeSnapshot.TypographyRole.BUTTON));
            assertTextSizeSp((TextView)panel.findViewById(
                                 R.id.octavo_navigation_title),
                             28);
            LinearLayout goToContent = panel.findViewById(
                R.id.octavo_navigation_go_to_content);
            assertTextSizeSp((TextView)goToContent.getChildAt(0), 20);
            assertTextSizeSp(panel.statusForTesting(), 14);
            assertTextSizeSp(part, 16);
            assertTextSizeSp(panel.dismissButtonForTesting(), 16);
            assertTextSizeSp(panel.chapterInputForTesting(), 16);
            assertEquals(panel.inputSelectionColorForTesting(),
                         panel.chapterInputForTesting().getHighlightColor());
            if (Build.VERSION.SDK_INT >= 29) {
                assertTrue(panel.chapterInputForTesting()
                    .getTextCursorDrawable() instanceof ColorDrawable);
                assertEquals(panel.inputCaretColorForTesting(),
                    ((ColorDrawable)panel.chapterInputForTesting()
                        .getTextCursorDrawable()).getColor());
            }
            assertSame(current, panel.preferredInitialFocus());
            assertEquals(panel.rowSelectedFillForTesting(),
                         panel.rowFocusedFillForTesting(true));
            assertTrue(panel.rowFocusedFillForTesting(false)
                       != panel.rowFocusedFillForTesting(true));

            AccessibilityNodeInfo collection =
                list.createAccessibilityNodeInfo();
            assertNotNull(collection.getCollectionInfo());
            assertEquals(3,
                         collection.getCollectionInfo().getRowCount());
            collection.recycle();

            AccessibilityNodeInfo partInfo =
                part.createAccessibilityNodeInfo();
            assertNotNull(partInfo.getCollectionItemInfo());
            assertEquals(0,
                         partInfo.getCollectionItemInfo().getRowIndex());
            assertTrue(partInfo.getCollectionItemInfo().isHeading());
            assertFalse(partInfo.getCollectionItemInfo().isSelected());
            partInfo.recycle();

            AccessibilityNodeInfo currentInfo =
                current.createAccessibilityNodeInfo();
            assertNotNull(currentInfo.getCollectionItemInfo());
            assertEquals(1,
                         currentInfo.getCollectionItemInfo().getRowIndex());
            assertTrue(currentInfo.getCollectionItemInfo().isHeading());
            assertTrue(currentInfo.getCollectionItemInfo().isSelected());
            currentInfo.recycle();

            int minimum = dp(panel.getContext(),
                             OctavoDesignTokens.TOUCH_TARGET_DP);
            assertTrue(part.getMinHeight() >= minimum);
            assertTrue(current.getMinHeight() >= minimum);
            assertTrue(panel.dismissButtonForTesting().getMinHeight()
                       >= minimum);
            assertEquals(panel.contentsTabForTesting().getId(),
                         panel.dismissButtonForTesting()
                             .getNextFocusForwardId());

            int stableCurrentId = current.getId();
            requestDeterministicFocus(current);
            panel.updateSnapshot(navigation);
            Ui0NavigationRow rebuiltCurrent = (Ui0NavigationRow)
                panel.contentsListForTesting().getChildAt(1);
            assertSame(current, rebuiltCurrent);
            assertEquals(stableCurrentId, rebuiltCurrent.getId());
            assertTrue(rebuiltCurrent.hasFocus());
        });
    }

    @Test
    public void narrowScaledHierarchyKeepsWrappedLabelAndCaptionVisible() {
        runOnMain(() -> {
            Context target = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
            Configuration scaledConfiguration = new Configuration(
                target.getResources().getConfiguration());
            scaledConfiguration.fontScale = 1.3f;
            Context scaled = target.createConfigurationContext(
                scaledConfiguration);
            Context themed = new ContextThemeWrapper(
                scaled, R.style.Theme_Octavo);
            RecordingListener listener = new RecordingListener();
            OctavoNavigationPanel panel = newPanel(themed, listener);
            String longLabel = "AnUnusuallyLongNavigationHeading";
            OctavoNavigation navigation = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 4, 4),
                new String[] {
                    longLabel,
                    "Chapter One",
                    "Scene"
                });
            assertNotNull(navigation);
            panel.updateSnapshot(navigation);

            Ui0NavigationRow row = (Ui0NavigationRow)
                panel.contentsListForTesting().getChildAt(0);
            assertEquals(3, row.getMaxLines());
            assertEquals(1.3f,
                         themed.getResources().getConfiguration().fontScale,
                         0.001f);
            int narrowWidth = row.getCompoundPaddingLeft()
                + row.getCompoundPaddingRight()
                + Math.max(1,
                           (int)Math.ceil(
                               row.getPaint().measureText(longLabel) * 0.60f));
            row.measure(
                View.MeasureSpec.makeMeasureSpec(
                    narrowWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());

            Layout layout = row.getLayout();
            assertNotNull(layout);
            assertTrue(layout.getLineCount() <= 3);
            String renderedText = row.getText().toString();
            int captionStart = renderedText.indexOf('\n') + 1;
            assertTrue(captionStart > 0);
            int captionLine = layout.getLineForOffset(captionStart);
            assertTrue(captionLine >= 2);
            assertTrue(captionLine < layout.getLineCount());
            int visibleEnd = layout.getEllipsisCount(captionLine) > 0
                ? layout.getLineStart(captionLine)
                    + layout.getEllipsisStart(captionLine)
                : layout.getLineEnd(captionLine);
            assertTrue(visibleEnd > captionStart);
            assertTrue(row.getMeasuredHeight()
                       >= row.getTotalPaddingTop()
                           + layout.getHeight()
                           + row.getTotalPaddingBottom());
            assertTrue(row.getMeasuredHeight() > row.getMinHeight());
            assertTrue(row.getMinHeight() >= dp(themed, 48));
        });
    }

    @Test
    public void structuralChangeDoesNotStealFocusFromAnotherControl() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoNavigationPanel panel = newPanel(listener);
            OctavoNavigation ready = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 4, 4), nestedLabels());
            assertNotNull(ready);
            panel.updateSnapshot(ready);
            Ui0NavigationRow originalCurrent = (Ui0NavigationRow)
                panel.contentsListForTesting().getChildAt(1);
            requestDeterministicFocus(originalCurrent);

            OctavoNavigation pending = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY | OctavoNavigation.FLAG_PENDING,
                             5,
                             4),
                nestedLabels());
            assertNotNull(pending);
            panel.updateSnapshot(pending);
            requestDeterministicFocus(panel.dismissButtonForTesting());

            String[] changedLabels =
                new String[] {"Part Two", "Chapter One", "Scene"};
            OctavoNavigation changed = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 6, 6), changedLabels);
            assertNotNull(changed);
            panel.updateSnapshot(changed);
            Ui0NavigationRow replacement = (Ui0NavigationRow)
                panel.contentsListForTesting().getChildAt(1);
            assertNotSame(originalCurrent, replacement);
            assertTrue(panel.dismissButtonForTesting().hasFocus());
            assertFalse(replacement.hasFocus());
        });
    }

    @Test
    public void equivalentPendingRollbackRetainsRowsFocusAndStatusIdentity() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoNavigationPanel panel = newPanel(listener);
            OctavoNavigation ready = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 4, 4), nestedLabels());
            assertNotNull(ready);
            panel.updateSnapshot(ready);

            Ui0NavigationRow part = (Ui0NavigationRow)
                panel.contentsListForTesting().getChildAt(0);
            Ui0NavigationRow current = (Ui0NavigationRow)
                panel.contentsListForTesting().getChildAt(1);
            int statusRevision = panel.statusRevisionForTesting();
            panel.updateSnapshot(ready);
            assertSame(part, panel.contentsListForTesting().getChildAt(0));
            assertSame(current, panel.contentsListForTesting().getChildAt(1));
            assertEquals(statusRevision, panel.statusRevisionForTesting());

            requestDeterministicFocus(current);
            OctavoNavigation pending = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY | OctavoNavigation.FLAG_PENDING,
                             5,
                             4),
                nestedLabels());
            assertNotNull(pending);
            panel.updateSnapshot(pending);
            assertSame(part, panel.contentsListForTesting().getChildAt(0));
            assertSame(current, panel.contentsListForTesting().getChildAt(1));
            assertFalse(current.isEnabled());

            panel.showError("Navigation was rolled back.");
            OctavoNavigation rollback = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 6, 4), nestedLabels());
            assertNotNull(rollback);
            panel.updateSnapshot(rollback);
            assertSame(part, panel.contentsListForTesting().getChildAt(0));
            assertSame(current, panel.contentsListForTesting().getChildAt(1));
            assertTrue(current.isEnabled());
            assertTrue(current.hasFocus());
            assertTrue(current.getContentDescription().toString()
                           .contains("current section"));
            assertEquals("Navigation was rolled back.",
                         panel.statusForTesting().getText().toString());
            assertStatusAccessibility(panel, "Navigation was rolled back.");
            int failureRevision = panel.statusRevisionForTesting();
            panel.showError("Navigation was rolled back.");
            assertEquals(failureRevision, panel.statusRevisionForTesting());
            assertStatusAccessibility(panel, "Navigation was rolled back.");

            OctavoNavigation retryPending =
                OctavoNavigation.fromNativePacket(
                    nestedPacket(
                        FLAGS_READY | OctavoNavigation.FLAG_PENDING,
                        7,
                        4),
                    nestedLabels());
            assertNotNull(retryPending);
            panel.updateSnapshot(retryPending);
            assertTrue(panel.statusForTesting().getText().toString()
                           .startsWith("Opening destination"));
            assertStatusAccessibility(panel, null);
        });
    }

    @Test
    public void panelDisablesAllNavigationWhilePendingAndValidatesWholeNumbers() {
        runOnMain(() -> {
            RecordingListener listener = new RecordingListener();
            OctavoNavigationPanel panel = newPanel(listener);
            OctavoNavigation pending = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY | OctavoNavigation.FLAG_PENDING,
                             5,
                             4),
                nestedLabels());
            assertNotNull(pending);
            panel.updateSnapshot(pending);

            assertTrue(panel.statusForTesting().getText().toString()
                           .startsWith("Opening destination"));
            assertFalse(panel.returnButtonForTesting().isEnabled());
            assertFalse(panel.forwardButtonForTesting().isEnabled());
            assertFalse(panel.contentsListForTesting().getChildAt(0)
                            .isEnabled());
            assertFalse(panel.chapterInputForTesting().isEnabled());
            assertFalse(panel.locationInputForTesting().isEnabled());
            assertFalse(panel.pageInputForTesting().isEnabled());
            assertFalse(panel.percentageInputForTesting().isEnabled());
            assertTrue(panel.locationInputForTesting()
                           .getTextColors().isStateful());
            assertTrue(panel.locationInputForTesting()
                           .getHintTextColors().isStateful());
            assertEquals(panel.inputDisabledTextColorForTesting(),
                         panel.locationInputForTesting()
                             .getCurrentTextColor());
            for (int index = 0;
                 index < panel.progressOptionsForTesting().getChildCount();
                 ++index) {
                assertFalse(panel.progressOptionsForTesting()
                                .getChildAt(index).isEnabled());
            }

            panel.contentsListForTesting().getChildAt(0).performClick();
            panel.findViewById(R.id.octavo_navigation_chapter_go)
                .performClick();
            panel.progressOptionsForTesting().getChildAt(1).performClick();
            assertEquals(0, listener.eventCount);

            OctavoNavigation ready = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 6, 6), nestedLabels());
            assertNotNull(ready);
            panel.updateSnapshot(ready);
            assertTrue(panel.returnButtonForTesting().isEnabled());
            assertTrue(panel.forwardButtonForTesting().isEnabled());
            for (int index = 0;
                 index < panel.progressOptionsForTesting().getChildCount();
                 ++index) {
                assertTrue(panel.progressOptionsForTesting()
                               .getChildAt(index).isEnabled());
            }

            assertWholeNumberInput(panel.chapterInputForTesting());
            assertWholeNumberInput(panel.locationInputForTesting());
            assertWholeNumberInput(panel.pageInputForTesting());
            assertWholeNumberInput(panel.percentageInputForTesting());

            panel.goToTabForTesting().performClick();
            panel.locationInputForTesting().setText("17");
            panel.findViewById(R.id.octavo_navigation_location_go)
                .performClick();
            assertEquals(1, listener.locationCount);
            assertEquals(17, listener.lastLocation);

            panel.pageInputForTesting().setText("5");
            panel.findViewById(R.id.octavo_navigation_page_go)
                .performClick();
            assertEquals(0, listener.pageCount);
            assertStatusAccessibility(
                panel,
                panel.statusForTesting().getText().toString());

            panel.percentageInputForTesting().setText("0");
            panel.findViewById(R.id.octavo_navigation_percentage_go)
                .performClick();
            assertEquals(1, listener.percentageCount);
            assertEquals(0, listener.lastPercentage);

            RadioButton pageOption = progressOption(
                panel.progressOptionsForTesting(),
                OctavoProgressDisplay.PAGE);
            RadioButton percentageOption = progressOption(
                panel.progressOptionsForTesting(),
                OctavoProgressDisplay.PERCENTAGE);
            assertNotNull(pageOption);
            assertNotNull(percentageOption);
            assertTrue(pageOption instanceof PresentedProgressRadioButton);
            assertEquals(percentageOption.getId(),
                         panel.progressOptionsForTesting()
                             .getCheckedRadioButtonId());
            percentageOption.performClick();
            assertEquals(0, listener.progressCount);
            listener.observedRequestedProgress = pageOption;
            listener.observedPresentedProgress = percentageOption;
            List<Boolean> checkedStatesAtEvents = new java.util.ArrayList<>();
            pageOption.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void sendAccessibilityEvent(
                        View host, int eventType) {
                        checkedStatesAtEvents.add(
                            ((RadioButton)host).isChecked());
                        super.sendAccessibilityEvent(host, eventType);
                    }
                });
            pageOption.performClick();
            assertEquals(1, listener.progressCount);
            assertSame(OctavoProgressDisplay.PAGE,
                       listener.lastProgress);
            assertSame(OctavoProgressDisplay.PERCENTAGE,
                       panel.presentedProgressForTesting());
            assertTrue(listener.progressRequestObserved);
            assertFalse(listener.requestSawRequestedChecked);
            assertTrue(listener.requestSawPresentedChecked);
            assertFalse(checkedStatesAtEvents.isEmpty());
            for (boolean checkedAtEvent : checkedStatesAtEvents) {
                assertFalse(checkedAtEvent);
            }
            assertEquals(percentageOption.getId(),
                         panel.progressOptionsForTesting()
                             .getCheckedRadioButtonId());
            panel.showError("Progress display could not be presented.");
            assertEquals(percentageOption.getId(),
                         panel.progressOptionsForTesting()
                             .getCheckedRadioButtonId());
            panel.updateProgressDisplay(OctavoProgressDisplay.PAGE);
            assertSame(OctavoProgressDisplay.PAGE,
                       panel.presentedProgressForTesting());
            assertEquals(pageOption.getId(),
                         panel.progressOptionsForTesting()
                             .getCheckedRadioButtonId());
        });
    }

    @Test
    public void acceptedRequestPublishesOneLiveStatusMutation() {
        runOnMain(() -> {
            OctavoNavigationPanel panel = newPanel(new RecordingListener());
            OctavoNavigation ready = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 4, 4), nestedLabels());
            OctavoNavigation pending = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY | OctavoNavigation.FLAG_PENDING,
                             5,
                             4),
                nestedLabels());
            assertNotNull(ready);
            assertNotNull(pending);
            panel.updateSnapshot(ready);
            int revision = panel.statusRevisionForTesting();

            panel.updateSnapshotWithStatus(
                pending, "Opening location 17.");

            assertEquals(revision + 1, panel.statusRevisionForTesting());
            assertEquals("Opening location 17.",
                         panel.statusForTesting().getText().toString());
            assertStatusAccessibility(panel, null);
            panel.updateSnapshot(pending);
            assertEquals(revision + 1, panel.statusRevisionForTesting());
            assertEquals("Opening location 17.",
                         panel.statusForTesting().getText().toString());

            OctavoNavigation presented = OctavoNavigation.fromNativePacket(
                nestedPacket(FLAGS_READY, 5, 5), nestedLabels());
            assertNotNull(presented);
            panel.updateSnapshot(presented);
            assertEquals(revision + 2, panel.statusRevisionForTesting());
            assertEquals("Current section: Chapter One",
                         panel.statusForTesting().getText().toString());

            panel.showError("The destination could not be opened.");
            int errorRevision = panel.statusRevisionForTesting();
            assertStatusAccessibility(
                panel, "The destination could not be opened.");
            panel.updateAlreadyPresentedSnapshot(presented);
            assertEquals(errorRevision + 1,
                         panel.statusRevisionForTesting());
            assertEquals("Current section: Chapter One",
                         panel.statusForTesting().getText().toString());
            assertStatusAccessibility(panel, null);
        });
    }

    @Test
    public void navigationThemeFailureMessageIsVisibleAndRetryable() {
        assertEquals(
            "Reader navigation styling is unavailable. Reopen Navigation "
                + "to retry.",
            OctavoActivity.navigationThemeFailureMessage());
        assertTrue(OctavoActivity.navigationRequestIsPending(
            OctavoNative.NAVIGATION_ACCEPTED));
        assertFalse(OctavoActivity.navigationRequestIsPending(
            OctavoNative.NAVIGATION_ALREADY_PRESENTED));
        assertFalse(OctavoActivity.navigationRequestIsPending(
            OctavoNative.NAVIGATION_INVALID));
    }

    private static void assertRejected(long[] valid,
                                       String[] labels,
                                       int offset,
                                       long value) {
        long[] malformed = valid.clone();
        malformed[offset] = value;
        assertNull("Expected field " + offset + " to be rejected",
                   OctavoNavigation.fromNativePacket(malformed, labels));
    }

    private static long[] nestedPacket(int flags,
                                       long generation,
                                       long presentedGeneration) {
        long[] packet = new long[
            OctavoNavigation.HEADER_COUNT
                + 3 * OctavoNavigation.ROW_STRIDE];
        packet[0] = OctavoNavigation.VERSION;
        packet[1] = OctavoNavigation.HEADER_COUNT;
        packet[2] = OctavoNavigation.ROW_STRIDE;
        packet[3] = flags;
        packet[4] = 3;
        packet[5] = 3;
        packet[6] = 1;
        packet[7] = 2;
        packet[8] = 1;
        packet[9] = generation;
        packet[10] = presentedGeneration;
        packet[11] = 17;
        packet[12] = 17;
        packet[13] = 100;
        packet[14] = 4;
        packet[15] = 4;

        setRow(packet,
               0,
               11,
               0,
               OctavoNavigation.ROW_FLAG_DESTINATION_VALID
                   | OctavoNavigation.ROW_FLAG_PROGRESS_AVAILABLE,
               0,
               0,
               1,
               17,
               0,
               1,
               4);
        setRow(packet,
               1,
               12,
               1,
               OctavoNavigation.ROW_FLAG_CURRENT
                   | OctavoNavigation.ROW_FLAG_DESTINATION_VALID
                   | OctavoNavigation.ROW_FLAG_PROGRESS_AVAILABLE,
               0,
               320,
               17,
               17,
               100,
               4,
               4);
        setRow(packet,
               2,
               13,
               2,
               0,
               0,
               640,
               0,
               0,
               0,
               0,
               0);
        return packet;
    }

    private static void setRow(long[] packet,
                               int row,
                               long navIndex,
                               long depth,
                               long flags,
                               long spine,
                               long byteOffset,
                               long location,
                               long locationCount,
                               long percent,
                               long page,
                               long pageCount) {
        int offset = OctavoNavigation.HEADER_COUNT
            + row * OctavoNavigation.ROW_STRIDE;
        packet[offset] = navIndex;
        packet[offset + 1] = depth;
        packet[offset + 2] = flags;
        packet[offset + 3] = spine;
        packet[offset + 4] = byteOffset;
        packet[offset + 5] = location;
        packet[offset + 6] = locationCount;
        packet[offset + 7] = percent;
        packet[offset + 8] = page;
        packet[offset + 9] = pageCount;
    }

    private static String[] nestedLabels() {
        return new String[] {"Part One", "Chapter One", "Scene"};
    }

    private static long[] depthChainPacket(int deepestDepth) {
        int rowCount = deepestDepth + 1;
        long[] packet = new long[
            OctavoNavigation.HEADER_COUNT
                + rowCount * OctavoNavigation.ROW_STRIDE];
        packet[0] = OctavoNavigation.VERSION;
        packet[1] = OctavoNavigation.HEADER_COUNT;
        packet[2] = OctavoNavigation.ROW_STRIDE;
        packet[3] = OctavoNavigation.FLAG_READY;
        packet[4] = rowCount;
        packet[5] = rowCount;
        packet[6] = -1;
        packet[9] = 1;
        packet[10] = 1;
        for (int index = 0; index < rowCount; ++index) {
            setRow(packet,
                   index,
                   index,
                   index,
                   OctavoNavigation.ROW_FLAG_DESTINATION_VALID,
                   0,
                   index * 16L,
                   0,
                   0,
                   0,
                   0,
                   0);
        }
        return packet;
    }

    private static String[] depthChainLabels(int deepestDepth) {
        String[] labels = new String[deepestDepth + 1];
        for (int index = 0; index < labels.length; ++index) {
            labels[index] = "Level " + (index + 1);
        }
        return labels;
    }

    private static OctavoNavigationPanel newPanel(
        RecordingListener listener) {
        Context target = InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
        Context themed = new ContextThemeWrapper(
            target, R.style.Theme_Octavo);
        return newPanel(themed, listener);
    }

    private static OctavoNavigationPanel newPanel(
        Context context,
        RecordingListener listener) {
        return new OctavoNavigationPanel(
            context,
            OctavoAppearance.defaults(),
            OctavoProgressDisplay.PERCENTAGE,
            listener);
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

    private static int dp(Context context, int value) {
        return Math.max(1,
                        Math.round(value
                                   * context.getResources()
                                       .getDisplayMetrics().density));
    }

    private static void assertTextSizeSp(TextView view, int expectedSp) {
        TextView probe = new TextView(view.getContext());
        probe.setTextSize(expectedSp);
        assertEquals(probe.getTextSize(),
                     view.getTextSize(),
                     0.01f);
    }

    private static int descendantBottom(View descendant, View ancestor) {
        int bottom = descendant.getBottom();
        View current = descendant;
        while (current.getParent() instanceof View
                && current.getParent() != ancestor) {
            current = (View)current.getParent();
            bottom += current.getTop();
        }
        assertSame(ancestor, current.getParent());
        return bottom;
    }

    private static void assertStatusAccessibility(
        OctavoNavigationPanel panel,
        String expectedError) {
        TextView status = panel.statusForTesting();
        assertNull(status.getContentDescription());
        AccessibilityNodeInfo info = status.createAccessibilityNodeInfo();
        assertEquals(expectedError, info.getError());
        info.recycle();
    }

    private static void assertWholeNumberInput(EditText input) {
        assertEquals(InputType.TYPE_CLASS_NUMBER,
                     input.getInputType() & InputType.TYPE_MASK_CLASS);
        assertEquals(0,
                     input.getInputType()
                         & InputType.TYPE_NUMBER_FLAG_DECIMAL);
        assertEquals(0,
                     input.getInputType()
                          & InputType.TYPE_NUMBER_FLAG_SIGNED);
    }

    private static void requestDeterministicFocus(View view) {
        view.setFocusableInTouchMode(true);
        assertTrue(view.requestFocus());
        assertTrue(view.hasFocus());
    }

    private static RadioButton progressOption(
        RadioGroup group,
        OctavoProgressDisplay display) {
        for (int index = 0; index < group.getChildCount(); ++index) {
            View child = group.getChildAt(index);
            if (child instanceof RadioButton && child.getTag() == display) {
                return (RadioButton)child;
            }
        }
        return null;
    }

    private static final class RecordingListener
        implements OctavoNavigationPanel.Listener {
        int eventCount;
        int locationCount;
        int pageCount;
        int percentageCount;
        int progressCount;
        long lastLocation;
        long lastPage;
        int lastPercentage;
        OctavoProgressDisplay lastProgress;
        RadioButton observedRequestedProgress;
        RadioButton observedPresentedProgress;
        boolean progressRequestObserved;
        boolean requestSawRequestedChecked;
        boolean requestSawPresentedChecked;

        @Override
        public void onDismiss() {
            eventCount += 1;
        }

        @Override
        public void onContentsJump(int navIndex) {
            eventCount += 1;
        }

        @Override
        public void onChapter(int oneBased) {
            eventCount += 1;
        }

        @Override
        public void onLocation(long location) {
            eventCount += 1;
            locationCount += 1;
            lastLocation = location;
        }

        @Override
        public void onPage(long oneBased) {
            eventCount += 1;
            pageCount += 1;
            lastPage = oneBased;
        }

        @Override
        public void onPercentage(int percentage) {
            eventCount += 1;
            percentageCount += 1;
            lastPercentage = percentage;
        }

        @Override
        public void onHistory(boolean forward) {
            eventCount += 1;
        }

        @Override
        public void onProgressDisplayRequested(
            OctavoProgressDisplay display) {
            eventCount += 1;
            progressCount += 1;
            lastProgress = display;
            progressRequestObserved = true;
            requestSawRequestedChecked =
                observedRequestedProgress != null
                    && observedRequestedProgress.isChecked();
            requestSawPresentedChecked =
                observedPresentedProgress != null
                    && observedPresentedProgress.isChecked();
        }
    }
}
