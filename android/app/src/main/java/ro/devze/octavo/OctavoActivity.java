package ro.devze.octavo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

    private OctavoLibraryStore libraryStore;
    private LinearLayout libraryRoot;
    private OctavoSurfaceView surfaceView;
    private OctavoLibraryStore.Book activeBook;
    private boolean activityResumed;
    private String lastOpenError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (activeBook != null) {
            state.putString(STATE_ACTIVE_BOOK_KEY, activeBook.key);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (surfaceView != null) {
            surfaceView.hostResumed();
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        if (surfaceView != null) {
            surfaceView.hostPaused();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
                new OctavoSurfaceView(this, libraryStore, session);
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

        LinearLayout root = createInsetRoot(0);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));

        Button closeButton = new Button(this);
        closeButton.setId(R.id.octavo_reader_library);
        closeButton.setText(R.string.library);
        closeButton.setContentDescription(getString(R.string.library));
        closeButton.setAllCaps(false);
        closeButton.setOnClickListener(view -> showLibrary());
        toolbar.addView(closeButton, wrapLayout());

        TextView title = new TextView(this);
        title.setText(readerTitle);
        title.setTextSize(16);
        title.setTextColor(Color.rgb(63, 59, 53));
        title.setSingleLine(true);
        title.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), 0, 0, 0);
        toolbar.addView(title, weightedLayout());

        root.addView(toolbar, matchParentWidthLayout());
        root.addView(replacement, surfaceLayout());

        releaseReader();
        surfaceView = replacement;
        activeBook = target;
        libraryRoot = null;
        setContentView(root, matchParentLayout());
        root.requestApplyInsets();
        if (activityResumed) {
            replacement.hostResumed();
        }
        return true;
    }

    private void showLibrary() {
        releaseReader();
        activeBook = null;

        LinearLayout root = createInsetRoot(dp(16));
        root.setId(R.id.octavo_library);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(R.string.library_title);
        heading.setTextSize(24);
        heading.setTextColor(Color.rgb(35, 32, 28));
        header.addView(heading, weightedLayout());

        Button addButton = new Button(this);
        addButton.setId(R.id.octavo_library_add);
        addButton.setText(R.string.add_epub);
        addButton.setContentDescription(getString(R.string.add_epub));
        addButton.setAllCaps(false);
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
        summary.setTextColor(Color.rgb(91, 86, 78));
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
        setContentView(root, matchParentLayout());
        root.requestApplyInsets();
    }

    private View createBookRow(OctavoLibraryStore.Book book) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(this);
        title.setText(book.title);
        title.setTextSize(19);
        title.setTextColor(Color.rgb(35, 32, 28));
        row.addView(title, matchParentWidthLayout());

        TextView status = new TextView(this);
        String progress = book.hasPosition
            ? "Resume available"
            : "Not started";
        status.setText(book.imported
                           ? progress
                           : "Built-in sample | " + progress);
        status.setTextColor(Color.rgb(91, 86, 78));
        status.setPadding(0, dp(2), 0, dp(6));
        row.addView(status, matchParentWidthLayout());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button open = new Button(this);
        open.setText(book.hasPosition ? R.string.resume : R.string.open);
        open.setAllCaps(false);
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
        divider.setBackgroundColor(Color.rgb(220, 216, 207));
        row.addView(divider,
                    new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(1))));
        return row;
    }

    private LinearLayout createInsetRoot(int horizontalPadding) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft()
                                + horizontalPadding,
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight()
                                + horizontalPadding,
                            0);
            return insets.replaceSystemWindowInsets(
                0, 0, 0, insets.getSystemWindowInsetBottom());
        });
        return root;
    }

    private void releaseReader() {
        if (surfaceView != null) {
            surfaceView.release();
            surfaceView = null;
        }
    }

    private void showOpenFailure(String message) {
        lastOpenError = message;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

    String lastOpenErrorForTesting() {
        return lastOpenError;
    }
}
