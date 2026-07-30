package ro.devze.octavo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public final class OctavoActivity extends Activity {
    private static final int REQUEST_OPEN_EPUB = 5001;

    private LinearLayout readerRoot;
    private OctavoDocumentStore documentStore;
    private OctavoSurfaceView surfaceView;
    private boolean activityResumed;
    private String lastOpenError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        documentStore = new OctavoDocumentStore(this);
        File fixture = new File(OctavoFixture.install(this));
        OctavoDocumentStore.Session session =
            documentStore.loadSession(fixture);

        readerRoot = new LinearLayout(this);
        readerRoot.setOrientation(LinearLayout.VERTICAL);
        readerRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            0);
            return insets.replaceSystemWindowInsets(
                0, 0, 0, insets.getSystemWindowInsetBottom());
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.END);
        Button openButton = new Button(this);
        openButton.setId(R.id.octavo_open_epub);
        openButton.setText(R.string.open_epub);
        openButton.setContentDescription(getString(R.string.open_epub));
        openButton.setAllCaps(false);
        openButton.setOnClickListener(view ->
            startActivityForResult(createOpenDocumentIntent(),
                                   REQUEST_OPEN_EPUB));
        LinearLayout.LayoutParams openLayout = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = Math.max(
            8, Math.round(getResources().getDisplayMetrics().density * 8));
        openLayout.setMargins(margin, margin, margin, margin);
        toolbar.addView(openButton, openLayout);
        readerRoot.addView(toolbar, matchParentWidthLayout());

        surfaceView = new OctavoSurfaceView(this, documentStore, session);
        readerRoot.addView(surfaceView, surfaceLayout());
        setContentView(readerRoot, matchParentLayout());
        readerRoot.requestApplyInsets();
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
        if (surfaceView != null) {
            surfaceView.release();
            surfaceView = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_EPUB
            || resultCode != RESULT_OK) {
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
        OctavoDocumentStore.Document document;
        try {
            document = documentStore.importDocument(uri);
        } catch (IOException | RuntimeException exception) {
            showOpenFailure("Unable to import the selected EPUB");
            return false;
        }

        OctavoDocumentStore.Session session =
            new OctavoDocumentStore.Session(document, false, 0, 0);
        OctavoSurfaceView replacement;
        try {
            replacement =
                new OctavoSurfaceView(this, documentStore, session);
        } catch (RuntimeException exception) {
            showOpenFailure("The selected file is not a readable EPUB");
            return false;
        }

        OctavoSurfaceView previous = surfaceView;
        if (previous != null) {
            previous.release();
            readerRoot.removeView(previous);
        }
        surfaceView = replacement;
        readerRoot.addView(replacement, 1, surfaceLayout());
        if (activityResumed) {
            replacement.hostResumed();
        }
        lastOpenError = null;
        return true;
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

    private static ViewGroup.LayoutParams matchParentLayout() {
        return new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static ViewGroup.LayoutParams matchParentWidthLayout() {
        return new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
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

    Intent openDocumentIntentForTesting() {
        return createOpenDocumentIntent();
    }

    OctavoDocumentStore documentStoreForTesting() {
        return documentStore;
    }

    String lastOpenErrorForTesting() {
        return lastOpenError;
    }
}
