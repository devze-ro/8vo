package ro.devze.octavo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class OctavoActivity extends Activity {
    private static final int REQUEST_ADD_EPUB = 6001;
    private static final String STATE_ACTIVE_BOOK_KEY =
        "octavo.port6.active_book_key";
    private static final String STATE_CHROME_VISIBLE =
        "octavo.port7.chrome_visible";

    private OctavoLibraryStore libraryStore;
    private OctavoAppearanceStore appearanceStore;
    private OctavoAppearance appearance;
    private LinearLayout libraryRoot;
    private FrameLayout systemBarRoot;
    private View statusBarScrim;
    private View navigationBarScrim;
    private FrameLayout readerRoot;
    private LinearLayout readerTopChrome;
    private LinearLayout readerBottomChrome;
    private Button readerPrevious;
    private Button readerNext;
    private Button readerSettings;
    private TextView readerProgress;
    private FrameLayout appearanceOverlay;
    private OctavoAppearancePanel appearancePanel;
    private TextView failureBanner;
    private View appearanceTransitionScrim;
    private OctavoAppearance appearanceTransitionTarget;
    private int appearanceTransitionGeneration;
    private OctavoSurfaceView surfaceView;
    private OctavoLibraryStore.Book activeBook;
    private boolean activityResumed;
    private boolean chromeVisible = true;
    private String lastOpenError;
    private String deferredAppearanceFailure;
    private OctavoAppearance pendingAppearancePersistence;
    private boolean appearancePersistencePosted;
    private final Runnable persistAppearance = () -> {
        appearancePersistencePosted = false;
        flushAppearancePersistence();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appearanceStore = new OctavoAppearanceStore(this);
        appearance = appearanceStore.load();
        boolean appearanceResetAfterCorruption =
            appearanceStore.recoveredFromCorruption();
        chromeVisible = savedInstanceState == null
            || savedInstanceState.getBoolean(STATE_CHROME_VISIBLE, true);
        applyWindowAppearance();
        libraryStore = new OctavoLibraryStore(this);
        File fixture = new File(OctavoFixture.install(this));
        libraryStore.loadCatalog(fixture);

        String restoreKey = savedInstanceState == null
            ? null
            : savedInstanceState.getString(STATE_ACTIVE_BOOK_KEY);
        OctavoLibraryStore.Book restoreBook =
            restoreKey == null ? null : libraryStore.findBook(restoreKey);
        if (restoreBook == null || !showReader(restoreBook, false)) {
            showLibrary();
        }
        if (appearanceResetAfterCorruption) {
            showOpenFailure(
                "Reader appearance was reset because its saved settings were invalid");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (activeBook != null) {
            state.putString(STATE_ACTIVE_BOOK_KEY, activeBook.key);
        }
        state.putBoolean(STATE_CHROME_VISIBLE, chromeVisible);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyWindowAppearance();
        updateAppearancePanelWidth();
        if (surfaceView != null) {
            surfaceView.reapplyAppearance();
        }
    }

    @Override
    public void onBackPressed() {
        if (appearancePanel != null) {
            closeAppearancePanel();
        } else if (surfaceView != null) {
            showLibrary();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (surfaceView != null) {
            surfaceView.hostResumed();
        }
        if (deferredAppearanceFailure != null) {
            String message = deferredAppearanceFailure;
            deferredAppearanceFailure = null;
            showOpenFailure(message);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (surfaceView != null) {
            surfaceView.hostPaused();
        }
        flushAppearancePersistence();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        flushAppearancePersistence();
        releaseReader();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ADD_EPUB || resultCode != RESULT_OK) {
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (uri == null) {
            showOpenFailure("The document picker returned no EPUB");
            return;
        }
        openDocument(uri);
    }

    private boolean openDocument(Uri uri) {
        OctavoLibraryStore.Book candidate;
        try {
            candidate = libraryStore.importDocument(uri);
        } catch (IOException | RuntimeException exception) {
            showOpenFailure("Unable to import the selected EPUB");
            return false;
        }
        boolean alreadyCataloged =
            libraryStore.findBook(candidate.key) != null;
        if (!showReader(candidate, true)) {
            if (!alreadyCataloged) {
                libraryStore.discardUncataloged(candidate);
            }
            showOpenFailure("The selected file is not a readable EPUB");
            return false;
        }
        lastOpenError = null;
        return true;
    }

    private boolean showReader(OctavoLibraryStore.Book requested,
                               boolean recordOpened) {
        OctavoLibraryStore.Book current =
            libraryStore.findBook(requested.key);
        OctavoLibraryStore.Book target = current == null ? requested : current;
        OctavoLibraryStore.Session session =
            current == null
                ? new OctavoLibraryStore.Session(target)
                : libraryStore.sessionFor(target);
        if (session == null) {
            return false;
        }

        OctavoSurfaceView replacement;
        try {
            replacement =
                new OctavoSurfaceView(
                    this,
                    libraryStore,
                    session,
                    appearance,
                    chromeVisible,
                    createReaderListener());
        } catch (RuntimeException exception) {
            return false;
        }
        String readerTitle = replacement.documentTitleForTesting();
        if (recordOpened
            && !libraryStore.recordOpened(target, readerTitle)) {
            replacement.release();
            return false;
        }
        target = libraryStore.findBook(target.key);
        if (target == null) {
            replacement.release();
            return false;
        }

        releaseReader();
        FrameLayout root = createReaderRoot(replacement, readerTitle);
        FrameLayout windowRoot = createSystemBarFrame(
            root, 0,
            OctavoDesignTokens.forAppearance(appearance).readerPage);
        surfaceView = replacement;
        activeBook = target;
        libraryRoot = null;
        readerRoot = root;
        setContentView(windowRoot, matchParentLayout());
        windowRoot.requestApplyInsets();
        if (activityResumed) {
            replacement.hostResumed();
        }
        return true;
    }

    private FrameLayout createReaderRoot(OctavoSurfaceView replacement,
                                         String readerTitle) {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout root = new FrameLayout(this);
        root.setId(R.id.octavo_reader_root);
        root.setBackgroundColor(tokens.readerPage);
        root.addView(replacement, matchParentLayout());

        LinearLayout top = new LinearLayout(this);
        top.setId(R.id.octavo_reader_top_chrome);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClickable(true);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(tokens.chromeSurface);
        top.setElevation(dp(2));

        Button library = createThemedButton(
            getString(R.string.library),
            tokens.libraryReturn,
            tokens.onLibraryReturn);
        library.setId(R.id.octavo_reader_library);
        library.setContentDescription(getString(R.string.library));
        library.setOnClickListener(view -> showLibrary());
        top.addView(library, chromeButtonLayout());

        TextView title = new TextView(this);
        title.setText(readerTitle);
        title.setTextSize(16);
        title.setTextColor(tokens.chromeText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(12), 0, dp(12), 0);
        title.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(title, weightedLayout());

        Button settings = createThemedButton(
            "Aa", tokens.buttonSurface, tokens.chromeText);
        settings.setId(R.id.octavo_reader_appearance);
        settings.setContentDescription("Reader appearance");
        settings.setFocusableInTouchMode(true);
        settings.setOnClickListener(view -> openAppearancePanel());
        top.addView(settings, chromeButtonLayout());

        FrameLayout.LayoutParams topLayout =
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top, topLayout);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setId(R.id.octavo_reader_bottom_chrome);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setClickable(true);
        bottom.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottom.setBackgroundColor(tokens.chromeSurface);
        bottom.setElevation(dp(2));

        Button previous = createThemedButton(
            "‹", tokens.buttonSurface, tokens.chromeText);
        previous.setId(R.id.octavo_reader_previous);
        previous.setContentDescription("Previous page");
        previous.setOnClickListener(view ->
            replacement.movePageForAccessibility(-1));
        bottom.addView(previous, chromeButtonLayout());

        TextView progress = new TextView(this);
        progress.setId(R.id.octavo_reader_progress);
        progress.setText(replacement.progressLabelForTesting());
        progress.setContentDescription(progress.getText());
        progress.setTextSize(14);
        progress.setTextColor(tokens.chromeText);
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(dp(8), 0, dp(8), 0);
        progress.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        bottom.addView(progress, weightedLayout());

        Button next = createThemedButton(
            "›", tokens.buttonSurface, tokens.chromeText);
        next.setId(R.id.octavo_reader_next);
        next.setContentDescription("Next page");
        next.setOnClickListener(view ->
            replacement.movePageForAccessibility(1));
        bottom.addView(next, chromeButtonLayout());

        FrameLayout.LayoutParams bottomLayout =
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottom, bottomLayout);

        top.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bottom.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        library.setAccessibilityTraversalBefore(settings.getId());
        settings.setAccessibilityTraversalBefore(replacement.getId());
        replacement.setAccessibilityTraversalBefore(previous.getId());
        previous.setAccessibilityTraversalBefore(next.getId());
        next.setAccessibilityTraversalBefore(progress.getId());
        readerTopChrome = top;
        readerBottomChrome = bottom;
        readerPrevious = previous;
        readerNext = next;
        readerSettings = settings;
        readerProgress = progress;
        updateReaderNavigationButtons(replacement);
        setChromeViewsVisible(chromeVisible, false);

        int chromeWidthSpec = View.MeasureSpec.makeMeasureSpec(
            getResources().getDisplayMetrics().widthPixels,
            View.MeasureSpec.EXACTLY);
        int chromeHeightSpec = View.MeasureSpec.makeMeasureSpec(
            0, View.MeasureSpec.UNSPECIFIED);
        top.measure(chromeWidthSpec, chromeHeightSpec);
        bottom.measure(chromeWidthSpec, chromeHeightSpec);
        replacement.setReaderChromeInsets(
            top.getMeasuredHeight(), bottom.getMeasuredHeight());
        root.addOnLayoutChangeListener(
            (view, left, upper, right, lower,
             oldLeft, oldUpper, oldRight, oldLower) ->
                updateReaderChromeInsets(replacement, top, bottom));
        return root;
    }

    private static void updateReaderChromeInsets(
        OctavoSurfaceView surface,
        View topChrome,
        View bottomChrome) {
        int surfaceHeight = surface.getHeight();
        if (surfaceHeight <= 0) {
            return;
        }
        int top = Math.max(
            0,
            Math.min(
                surfaceHeight,
                topChrome.getBottom() - surface.getTop()));
        int bottom = Math.max(
            0,
            Math.min(
                surfaceHeight - top,
                surface.getBottom() - bottomChrome.getTop()));
        if (top > 0 && bottom > 0) {
            surface.setReaderChromeInsets(top, bottom);
        }
    }

    private OctavoSurfaceView.Listener createReaderListener() {
        return new OctavoSurfaceView.Listener() {
            @Override
            public void onChromeVisibilityChanged(boolean visible) {
                setChromeViewsVisible(visible, true);
            }

            @Override
            public void onAppearancePresented(
                OctavoAppearance presented) {
                appearance = presented;
                applyWindowAppearance();
                applyReaderAppearanceTokens();
                if (appearancePanel != null) {
                    appearancePanel.updatePresentedAppearance(presented);
                }
                persistPresentedAppearance(presented);
                finishAppearanceTransition(presented);
            }

            @Override
            public void onAppearanceFailure() {
                boolean hasNewerRequest = surfaceView != null
                    && surfaceView.hasPendingAppearanceRequest();
                if (!hasNewerRequest) {
                    if (appearancePanel != null) {
                        appearancePanel.updatePresentedAppearance(appearance);
                    }
                    cancelAppearanceTransition();
                }
                showOpenFailure("Unable to present reader appearance");
            }

            @Override
            public void onReaderSurfaceFailure() {
                showOpenFailure(
                    "Unable to acquire reader surface; reopen the book");
            }

            @Override
            public void onPresentationRetriesExhausted(
                boolean appearanceStillAwaiting) {
                if (!appearanceStillAwaiting) {
                    if (appearancePanel != null) {
                        appearancePanel.updatePresentedAppearance(
                            appearance);
                    }
                    cancelAppearanceTransition();
                }
                showOpenFailure(
                    "Unable to present reader changes; try again");
            }

            @Override
            public void onAppearanceRequestsSettled(
                OctavoAppearance settled) {
                finishAppearanceTransition(settled);
            }

            @Override
            public void onReaderPresentationChanged(String label) {
                if (readerProgress != null && label != null) {
                    readerProgress.setText(label);
                    readerProgress.setContentDescription(label);
                }
                updateReaderNavigationButtons(surfaceView);
            }
        };
    }

    private void updateReaderNavigationButtons(OctavoSurfaceView view) {
        if (readerPrevious == null || readerNext == null || view == null) {
            return;
        }
        readerPrevious.setEnabled(view.canMovePrevious());
        readerNext.setEnabled(view.canMoveNext());
    }

    private void applyWindowAppearance() {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        getWindow().setStatusBarColor(tokens.statusBar);
        getWindow().setNavigationBarColor(tokens.navigationBar);
        getWindow().getDecorView().setBackgroundColor(tokens.launch);
        int systemUi = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!tokens.useLightSystemBarIcons()) {
            systemUi |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            systemUi |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
    }

    private void requestReaderAppearance(OctavoAppearance requested) {
        if (surfaceView == null || requested == null) {
            return;
        }
        prepareAppearanceTransition(requested);
        surfaceView.requestAppearance(requested);
    }

    private void prepareAppearanceTransition(OctavoAppearance requested) {
        if (readerRoot == null) {
            return;
        }
        if (requested.themeId() == appearance.themeId()) {
            if (appearanceTransitionScrim != null
                && surfaceView != null
                && surfaceView.hasAppearanceAwaitingPresentation()) {
                retargetAppearanceTransition(requested);
            } else {
                cancelAppearanceTransition();
            }
            return;
        }
        retargetAppearanceTransition(requested);
    }

    private void retargetAppearanceTransition(
        OctavoAppearance requested) {
        appearanceTransitionTarget = requested;
        appearanceTransitionGeneration += 1;
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(requested);
        if (appearanceTransitionScrim == null) {
            View scrim = new View(this);
            scrim.setId(R.id.octavo_appearance_transition);
            scrim.setClickable(true);
            scrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int index = appearanceOverlay == null
                ? readerRoot.getChildCount()
                : readerRoot.indexOfChild(appearanceOverlay);
            readerRoot.addView(scrim, index, matchParentLayout());
            appearanceTransitionScrim = scrim;
        }
        appearanceTransitionScrim.setBackgroundColor(tokens.readerPage);
    }

    private void finishAppearanceTransition(OctavoAppearance presented) {
        if (appearanceTransitionScrim == null
            || appearanceTransitionTarget == null
            || !appearanceTransitionTarget.equals(presented)
            || (surfaceView != null
                && surfaceView.hasPendingAppearanceRequest())
            || readerRoot == null) {
            return;
        }
        int generation = appearanceTransitionGeneration;
        FrameLayout root = readerRoot;
        root.postOnAnimation(() -> root.postOnAnimation(() -> {
            if (readerRoot == root
                && generation == appearanceTransitionGeneration
                && appearanceTransitionTarget != null
                && appearanceTransitionTarget.equals(presented)) {
                cancelAppearanceTransition();
            }
        }));
    }

    private void cancelAppearanceTransition() {
        appearanceTransitionGeneration += 1;
        if (appearanceTransitionScrim != null
            && appearanceTransitionScrim.getParent() instanceof ViewGroup) {
            ((ViewGroup)appearanceTransitionScrim.getParent())
                .removeView(appearanceTransitionScrim);
        }
        appearanceTransitionScrim = null;
        appearanceTransitionTarget = null;
    }

    private void persistPresentedAppearance(OctavoAppearance presented) {
        pendingAppearancePersistence = presented;
        if (appearancePersistencePosted) {
            return;
        }
        View decor = getWindow().getDecorView();
        appearancePersistencePosted = true;
        decor.postOnAnimation(persistAppearance);
    }

    private void flushAppearancePersistence() {
        if (appearancePersistencePosted) {
            getWindow().getDecorView().removeCallbacks(persistAppearance);
            appearancePersistencePosted = false;
        }
        OctavoAppearance candidate = pendingAppearancePersistence;
        pendingAppearancePersistence = null;
        if (candidate != null && !appearanceStore.save(candidate)) {
            reportAppearancePersistenceFailure();
        }
    }

    private void reportAppearancePersistenceFailure() {
        String message = "Appearance changed, but could not be saved";
        lastOpenError = message;
        if (activityResumed) {
            showOpenFailure(message);
        } else {
            deferredAppearanceFailure = message;
        }
    }

    private void setChromeViewsVisible(boolean visible, boolean animate) {
        chromeVisible = visible;
        setChromeViewVisible(readerTopChrome, visible, animate);
        setChromeViewVisible(readerBottomChrome, visible, animate);
    }

    private void setChromeViewVisible(View view,
                                      boolean visible,
                                      boolean animate) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setImportantForAccessibility(
            visible && appearancePanel == null
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int duration = OctavoDesignTokens.forAppearance(appearance)
            .fastMotionMs(appearance);
        if (!animate || duration == 0) {
            view.setAlpha(visible ? 1.0f : 0.0f);
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0.0f);
            view.animate().alpha(1.0f).setDuration(duration).start();
        } else {
            view.animate()
                .alpha(0.0f)
                .setDuration(duration)
                .withEndAction(() -> view.setVisibility(View.INVISIBLE))
                .start();
        }
    }

    private void applyReaderAppearanceTokens() {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        if (readerRoot != null) {
            readerRoot.setBackgroundColor(tokens.readerPage);
        }
        if (systemBarRoot != null) {
            systemBarRoot.setBackgroundColor(tokens.readerPage);
        }
        if (statusBarScrim != null) {
            statusBarScrim.setBackgroundColor(tokens.statusBar);
        }
        if (navigationBarScrim != null) {
            navigationBarScrim.setBackgroundColor(tokens.navigationBar);
        }
        tintChrome(readerTopChrome, tokens);
        tintChrome(readerBottomChrome, tokens);
        if (appearanceOverlay != null) {
            appearanceOverlay.setBackgroundColor(tokens.overlay);
        }
        if (failureBanner != null) {
            failureBanner.setTextColor(tokens.error);
            failureBanner.setBackgroundColor(tokens.dialogSurface);
        }
    }

    private void tintChrome(LinearLayout chrome,
                            OctavoDesignTokens tokens) {
        if (chrome == null) {
            return;
        }
        chrome.setBackgroundColor(tokens.chromeSurface);
        for (int index = 0; index < chrome.getChildCount(); ++index) {
            View child = chrome.getChildAt(index);
            if (child instanceof Button) {
                Button button = (Button)child;
                boolean library =
                    button.getId() == R.id.octavo_reader_library;
                button.setTextColor(
                    library ? tokens.onLibraryReturn : tokens.chromeText);
                button.setBackgroundTintList(ColorStateList.valueOf(
                    library ? tokens.libraryReturn : tokens.buttonSurface));
            } else if (child instanceof TextView) {
                ((TextView)child).setTextColor(tokens.chromeText);
            }
        }
    }

    private void openAppearancePanel() {
        if (readerRoot == null || surfaceView == null
            || appearancePanel != null) {
            return;
        }
        surfaceView.setChromeVisible(true);
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setId(R.id.octavo_appearance_overlay);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setElevation(dp(4));
        overlay.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setBackgroundColor(tokens.overlay);
        overlay.setOnClickListener(view -> closeAppearancePanel());

        OctavoAppearancePanel panel = new OctavoAppearancePanel(
            this,
            appearance,
            new OctavoAppearancePanel.Listener() {
                @Override
                public void onAppearanceRequested(
                    OctavoAppearance requested) {
                    requestReaderAppearance(requested);
                }

                @Override
                public void onDismiss() {
                    closeAppearancePanel();
                }
            });
        panel.setId(R.id.octavo_appearance_panel);
        panel.setClickable(true);
        panel.setOnClickListener(view -> {
        });

        FrameLayout.LayoutParams panelLayout =
            new FrameLayout.LayoutParams(
                appearancePanelWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        overlay.addView(panel, panelLayout);
        readerRoot.addView(overlay, matchParentLayout());

        surfaceView.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerTopChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        readerBottomChrome.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        appearanceOverlay = overlay;
        appearancePanel = panel;

        int duration = tokens.standardMotionMs(appearance);
        if (duration > 0) {
            overlay.setAlpha(0.0f);
            panel.setTranslationX(dp(24));
            overlay.animate().alpha(1.0f).setDuration(duration).start();
            panel.animate().translationX(0.0f)
                .setDuration(duration).start();
        }
        panel.post(() -> {
            if (appearancePanel != panel) {
                return;
            }
            panel.requestFocus();
            panel.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null);
            panel.announceForAccessibility(
                "Reading appearance opened");
        });
    }

    private void closeAppearancePanel() {
        if (appearanceOverlay == null) {
            return;
        }
        View focusReturn = readerSettings;
        appearanceOverlay.animate().cancel();
        if (appearancePanel != null) {
            appearancePanel.animate().cancel();
        }
        if (appearanceOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)appearanceOverlay.getParent())
                .removeView(appearanceOverlay);
        }
        appearanceOverlay = null;
        appearancePanel = null;
        if (failureBanner != null
            && failureBanner.getParent() instanceof ViewGroup) {
            ((ViewGroup)failureBanner.getParent())
                .removeView(failureBanner);
        }
        failureBanner = null;
        if (surfaceView != null) {
            surfaceView.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            surfaceView.announceForAccessibility(
                "Reading appearance closed");
        }
        if (readerTopChrome != null) {
            readerTopChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (readerBottomChrome != null) {
            readerBottomChrome.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (focusReturn != null && focusReturn.isShown()) {
            focusReturn.requestFocus();
            focusReturn.post(() -> {
                if (appearancePanel != null || !focusReturn.isShown()) {
                    return;
                }
                focusReturn.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null);
                focusReturn.announceForAccessibility(
                    "Reader appearance closed");
            });
        }
    }

    private void showLibrary() {
        releaseReader();
        activeBook = null;
        applyWindowAppearance();
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);

        LinearLayout root = createInsetRoot();
        root.setId(R.id.octavo_library);
        root.setBackgroundColor(tokens.librarySurface);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(R.string.library_title);
        heading.setTextSize(24);
        heading.setTextColor(tokens.libraryText);
        header.addView(heading, weightedLayout());

        Button addButton = new Button(this);
        addButton.setId(R.id.octavo_library_add);
        addButton.setText(R.string.add_epub);
        addButton.setContentDescription(getString(R.string.add_epub));
        addButton.setAllCaps(false);
        addButton.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        addButton.setTextColor(tokens.onAccent);
        addButton.setBackgroundTintList(
            ColorStateList.valueOf(tokens.accent));
        addButton.setOnClickListener(view ->
            startActivityForResult(createOpenDocumentIntent(),
                                   REQUEST_ADD_EPUB));
        header.addView(addButton, wrapLayout());
        root.addView(header, matchParentWidthLayout());

        TextView summary = new TextView(this);
        int importedCount = Math.max(0, libraryStore.bookCount() - 1);
        summary.setText(String.format(
            Locale.ROOT,
            importedCount == 1
                ? "%d imported book plus the built-in sample"
                : "%d imported books plus the built-in sample",
            importedCount));
        summary.setPadding(0, dp(4), 0, dp(12));
        summary.setTextColor(tokens.textSecondary);
        root.addView(summary, matchParentWidthLayout());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.octavo_library_list);
        list.setOrientation(LinearLayout.VERTICAL);
        for (OctavoLibraryStore.Book book : libraryStore.books()) {
            list.addView(createBookRow(book), matchParentWidthLayout());
        }
        scroll.addView(list, matchParentWidthLayout());
        root.addView(scroll, surfaceLayout());

        libraryRoot = root;
        FrameLayout windowRoot = createSystemBarFrame(
            root, dp(16), tokens.librarySurface);
        setContentView(windowRoot, matchParentLayout());
        windowRoot.requestApplyInsets();
    }

    private View createBookRow(OctavoLibraryStore.Book book) {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(this);
        title.setText(book.title);
        title.setTextSize(19);
        title.setTextColor(tokens.libraryText);
        row.addView(title, matchParentWidthLayout());

        TextView status = new TextView(this);
        String progress = book.hasPosition
            ? "Resume available"
            : "Not started";
        status.setText(book.imported
                           ? progress
                           : "Built-in sample | " + progress);
        status.setTextColor(tokens.textSecondary);
        status.setPadding(0, dp(2), 0, dp(6));
        row.addView(status, matchParentWidthLayout());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button open = new Button(this);
        open.setText(book.hasPosition ? R.string.resume : R.string.open);
        open.setAllCaps(false);
        open.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        open.setTextColor(tokens.chromeText);
        open.setBackgroundTintList(
            ColorStateList.valueOf(tokens.buttonSurface));
        open.setContentDescription("Open " + book.title);
        open.setOnClickListener(view -> {
            if (!showReader(book, true)) {
                showOpenFailure("Unable to open the library book");
            }
        });
        actions.addView(open, wrapLayout());

        if (book.imported) {
            Button remove = new Button(this);
            remove.setText(R.string.remove);
            remove.setAllCaps(false);
            remove.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
            remove.setTextColor(tokens.error);
            remove.setBackgroundTintList(
                ColorStateList.valueOf(tokens.buttonSurface));
            remove.setContentDescription("Remove " + book.title);
            remove.setOnClickListener(view -> {
                if (libraryStore.removeBook(book.key)) {
                    showLibrary();
                } else {
                    showOpenFailure("Unable to remove the library book");
                }
            });
            actions.addView(remove, wrapLayout());
        }
        row.addView(actions, matchParentWidthLayout());

        View divider = new View(this);
        divider.setBackgroundColor(tokens.divider);
        row.addView(divider,
                    new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(1))));
        return row;
    }

    private LinearLayout createInsetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        return root;
    }

    private int appearancePanelWidth() {
        int available = Math.max(
            getResources().getDisplayMetrics().widthPixels - dp(24),
            1);
        return Math.min(dp(560), available);
    }

    private void updateAppearancePanelWidth() {
        if (appearancePanel == null) {
            return;
        }
        ViewGroup.LayoutParams current = appearancePanel.getLayoutParams();
        if (current != null && current.width != appearancePanelWidth()) {
            current.width = appearancePanelWidth();
            appearancePanel.setLayoutParams(current);
        }
    }

    private FrameLayout createSystemBarFrame(View content,
                                             int horizontalPadding,
                                             int contentBackground) {
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(contentBackground);
        frame.addView(content, matchParentLayout());

        View status = new View(this);
        status.setId(R.id.octavo_status_bar_scrim);
        status.setBackgroundColor(tokens.statusBar);
        status.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(status,
                      new FrameLayout.LayoutParams(
                          ViewGroup.LayoutParams.MATCH_PARENT, 0,
                          Gravity.TOP));

        View navigation = new View(this);
        navigation.setId(R.id.octavo_navigation_bar_scrim);
        navigation.setBackgroundColor(tokens.navigationBar);
        navigation.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(navigation,
                      new FrameLayout.LayoutParams(
                          ViewGroup.LayoutParams.MATCH_PARENT, 0,
                          Gravity.BOTTOM));

        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            content.setPadding(left + horizontalPadding,
                               top,
                               right + horizontalPadding,
                               bottom);
            FrameLayout.LayoutParams statusLayout =
                (FrameLayout.LayoutParams)status.getLayoutParams();
            statusLayout.height = top;
            status.setLayoutParams(statusLayout);
            FrameLayout.LayoutParams navigationLayout =
                (FrameLayout.LayoutParams)navigation.getLayoutParams();
            navigationLayout.height = bottom;
            navigation.setLayoutParams(navigationLayout);
            return insets.replaceSystemWindowInsets(0, 0, 0, 0);
        });
        systemBarRoot = frame;
        statusBarScrim = status;
        navigationBarScrim = navigation;
        return frame;
    }

    private void releaseReader() {
        cancelAppearanceTransition();
        if (surfaceView != null) {
            surfaceView.release();
            surfaceView = null;
        }
        if (appearanceOverlay != null
            && appearanceOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup)appearanceOverlay.getParent())
                .removeView(appearanceOverlay);
        }
        appearanceOverlay = null;
        appearancePanel = null;
        readerRoot = null;
        readerTopChrome = null;
        readerBottomChrome = null;
        readerPrevious = null;
        readerNext = null;
        readerSettings = null;
        readerProgress = null;
        systemBarRoot = null;
        statusBarScrim = null;
        navigationBarScrim = null;
    }

    private void showOpenFailure(String message) {
        lastOpenError = message;
        if (readerRoot == null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        if (failureBanner != null
            && failureBanner.getParent() instanceof ViewGroup) {
            ((ViewGroup)failureBanner.getParent())
                .removeView(failureBanner);
        }
        OctavoDesignTokens tokens =
            OctavoDesignTokens.forAppearance(appearance);
        TextView banner = new TextView(this);
        banner.setId(R.id.octavo_reader_failure);
        banner.setText(message);
        banner.setContentDescription(message);
        banner.setTextSize(16);
        banner.setTextColor(tokens.error);
        banner.setGravity(Gravity.CENTER);
        banner.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setBackgroundColor(tokens.dialogSurface);
        banner.setElevation(dp(6));
        FrameLayout.LayoutParams layout =
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        layout.leftMargin = dp(16);
        layout.rightMargin = dp(16);
        layout.bottomMargin = dp(72);
        readerRoot.addView(banner, layout);
        failureBanner = banner;
        banner.announceForAccessibility(message);
        banner.postDelayed(() -> {
            if (failureBanner == banner
                && banner.getParent() instanceof ViewGroup) {
                ((ViewGroup)banner.getParent()).removeView(banner);
                failureBanner = null;
            }
        }, 5000);
    }

    private static Intent createOpenDocumentIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/epub+zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[] {
                            "application/epub+zip",
                            "application/octet-stream",
                            "application/zip"
                        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private int dp(int value) {
        return Math.max(value,
                        Math.round(getResources().getDisplayMetrics().density
                                   * value));
    }

    private Button createThemedButton(CharSequence label,
                                      int background,
                                      int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinWidth(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        button.setMinHeight(dp(OctavoDesignTokens.TOUCH_TARGET_DP));
        button.setTextColor(foreground);
        button.setBackgroundTintList(
            ColorStateList.valueOf(background));
        return button;
    }

    private LinearLayout.LayoutParams chromeButtonLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static ViewGroup.LayoutParams matchParentLayout() {
        return new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams matchParentWidthLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weightedLayout() {
        return new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f);
    }

    private static LinearLayout.LayoutParams surfaceLayout() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f);
    }

    boolean openDocumentForTesting(Uri uri) {
        return openDocument(uri);
    }

    boolean openFixtureForTesting() {
        OctavoLibraryStore.Book fixture = libraryStore.fixtureBook();
        return fixture != null && showReader(fixture, true);
    }

    boolean openBookForTesting(String key) {
        OctavoLibraryStore.Book book = libraryStore.findBook(key);
        return book != null && showReader(book, true);
    }

    boolean removeBookForTesting(String key) {
        boolean removed = libraryStore.removeBook(key);
        if (removed) {
            showLibrary();
        }
        return removed;
    }

    void closeBookForTesting() {
        showLibrary();
    }

    boolean libraryVisibleForTesting() {
        return libraryRoot != null && surfaceView == null;
    }

    String activeBookKeyForTesting() {
        return activeBook == null ? null : activeBook.key;
    }

    Intent openDocumentIntentForTesting() {
        return createOpenDocumentIntent();
    }

    OctavoLibraryStore libraryStoreForTesting() {
        return libraryStore;
    }

    OctavoAppearance appearanceForTesting() {
        return appearance;
    }

    OctavoAppearanceStore appearanceStoreForTesting() {
        return appearanceStore;
    }

    OctavoAppearancePanel appearancePanelForTesting() {
        return appearancePanel;
    }

    void openAppearancePanelForTesting() {
        openAppearancePanel();
    }

    void closeAppearancePanelForTesting() {
        closeAppearancePanel();
    }

    void requestAppearanceForTesting(OctavoAppearance requested) {
        requestReaderAppearance(requested);
    }

    void flushAppearancePersistenceForTesting() {
        flushAppearancePersistence();
    }

    void queuePresentedAppearancePersistenceForTesting() {
        persistPresentedAppearance(appearance);
    }

    boolean setChromeVisibleForTesting(boolean visible) {
        return surfaceView != null
            && surfaceView.setChromeVisible(visible);
    }

    boolean chromeVisibleForTesting() {
        return chromeVisible;
    }

    String lastOpenErrorForTesting() {
        return lastOpenError;
    }
}
