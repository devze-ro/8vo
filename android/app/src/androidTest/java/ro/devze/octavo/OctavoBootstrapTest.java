package ro.devze.octavo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.PixelCopy;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class OctavoBootstrapTest {
    private static long[] state(ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<long[]> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            OctavoSurfaceView surface =
                (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
            result.set(surface.nativeStateForTesting());
        });
        assertNotNull(result.get());
        assertEquals(OctavoSurfaceView.STATE_FIELD_COUNT, result.get().length);
        return result.get();
    }

    private static long[] awaitPresentedFrame(
        ActivityScenario<OctavoActivity> scenario) {
        for (int attempt = 0; attempt < 50; ++attempt) {
            long[] snapshot = state(scenario);
            if (snapshot[OctavoSurfaceView.STATE_RESUMED] == 1
                && snapshot[OctavoSurfaceView.STATE_HAS_SURFACE] == 1
                && snapshot[OctavoSurfaceView.STATE_WIDTH] > 0
                && snapshot[OctavoSurfaceView.STATE_HEIGHT] > 0
                && snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0) {
                return snapshot;
            }
            SystemClock.sleep(100);
        }
        fail("8vo did not present its deterministic native frame");
        return new long[0];
    }

    private static OctavoSurfaceView surface(
        ActivityScenario<OctavoActivity> scenario) {
        AtomicReference<OctavoSurfaceView> result = new AtomicReference<>();
        scenario.onActivity(activity -> result.set(
            (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface)));
        assertNotNull(result.get());
        return result.get();
    }

    private static int copyCenterPixel(OctavoSurfaceView surface)
        throws InterruptedException {
        int width = surface.getWidth();
        int height = surface.getHeight();
        assertTrue(width > 0);
        assertTrue(height > 0);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        HandlerThread copyThread = new HandlerThread("8vo-pixel-copy");
        copyThread.start();
        try {
            for (int attempt = 0; attempt < 10; ++attempt) {
                CountDownLatch copied = new CountDownLatch(1);
                AtomicInteger result = new AtomicInteger(PixelCopy.ERROR_UNKNOWN);
                PixelCopy.request(surface,
                                  bitmap,
                                  copyResult -> {
                                      result.set(copyResult);
                                      copied.countDown();
                                  },
                                  new Handler(copyThread.getLooper()));
                assertTrue(copied.await(5, TimeUnit.SECONDS));
                if (result.get() == PixelCopy.SUCCESS) {
                    return bitmap.getPixel(width / 2, height / 2);
                }
                SystemClock.sleep(100);
            }
            fail("PixelCopy could not read the native frame");
            return Color.TRANSPARENT;
        } finally {
            bitmap.recycle();
            copyThread.quitSafely();
        }
    }

    private static void assertColorClose(int expected, int actual) {
        assertTrue(Math.abs(Color.red(expected) - Color.red(actual)) <= 1);
        assertTrue(Math.abs(Color.green(expected) - Color.green(actual)) <= 1);
        assertTrue(Math.abs(Color.blue(expected) - Color.blue(actual)) <= 1);
        assertEquals(Color.alpha(expected), Color.alpha(actual));
    }

    @Test
    public void nativeFramePathsAndLifecycle() throws InterruptedException {
        assertEquals("0.4.0-dev", OctavoNative.version());
        assertEquals("android", OctavoNative.platform());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            AtomicReference<String> filesPath = new AtomicReference<>();
            AtomicReference<String> cachePath = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView surface =
                    (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
                assertNotNull(surface);
                filesPath.set(surface.filesPathForTesting());
                cachePath.set(surface.cachePathForTesting());
                assertEquals(activity.getFilesDir().getAbsolutePath(), filesPath.get());
                assertEquals(activity.getCacheDir().getAbsolutePath(), cachePath.get());
            });
            assertNotNull(filesPath.get());
            assertNotNull(cachePath.get());

            long[] initial = awaitPresentedFrame(scenario);
            assertEquals(0, initial[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
            assertTrue(initial[OctavoSurfaceView.STATE_SURFACE_GENERATION] >= 1);
            assertTrue(initial[OctavoSurfaceView.STATE_RESUME_COUNT] >= 1);
            assertColorClose(OctavoNative.clearColorArgb(),
                             copyCenterPixel(surface(scenario)));

            scenario.moveToState(Lifecycle.State.CREATED);
            long[] paused = state(scenario);
            assertEquals(0, paused[OctavoSurfaceView.STATE_RESUMED]);
            assertTrue(paused[OctavoSurfaceView.STATE_PAUSE_COUNT] >= 1);
            assertTrue(paused[OctavoSurfaceView.STATE_LIFECYCLE_GENERATION]
                       > initial[OctavoSurfaceView.STATE_LIFECYCLE_GENERATION]);

            scenario.moveToState(Lifecycle.State.RESUMED);
            long[] resumed = awaitPresentedFrame(scenario);
            assertTrue(resumed[OctavoSurfaceView.STATE_RESUME_COUNT]
                       > initial[OctavoSurfaceView.STATE_RESUME_COUNT]);
            assertTrue(resumed[OctavoSurfaceView.STATE_FRAME_COUNT]
                       > initial[OctavoSurfaceView.STATE_FRAME_COUNT]);
            assertEquals(0, resumed[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);

            scenario.recreate();
            long[] recreated = awaitPresentedFrame(scenario);
            assertTrue(recreated[OctavoSurfaceView.STATE_SURFACE_GENERATION] >= 1);
            assertTrue(recreated[OctavoSurfaceView.STATE_FRAME_COUNT] >= 1);
            assertEquals(0, recreated[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
            assertColorClose(OctavoNative.clearColorArgb(),
                             copyCenterPixel(surface(scenario)));
        }
    }
}
