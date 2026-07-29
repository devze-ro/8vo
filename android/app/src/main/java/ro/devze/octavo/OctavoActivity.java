package ro.devze.octavo;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;

public final class OctavoActivity extends Activity {
    private OctavoSurfaceView surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        surfaceView = new OctavoSurfaceView(this, OctavoFixture.install(this));
        setContentView(surfaceView,
                       new ViewGroup.LayoutParams(
                           ViewGroup.LayoutParams.MATCH_PARENT,
                           ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceView != null) {
            surfaceView.hostResumed();
        }
    }

    @Override
    protected void onPause() {
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
}
