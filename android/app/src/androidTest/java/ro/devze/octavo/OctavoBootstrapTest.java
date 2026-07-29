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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
                && snapshot[OctavoSurfaceView.STATE_FRAME_COUNT] > 0
                && snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY] == 1
                && snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY] == 1) {
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

    private static Bitmap copyFrame(OctavoSurfaceView surface)
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
                    return bitmap;
                }
                SystemClock.sleep(100);
            }
            fail("PixelCopy could not read the native frame");
            bitmap.recycle();
            return null;
        } finally {
            copyThread.quitSafely();
        }
    }

    private static String sha256(File file)
        throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02X", value & 0xFF));
        }
        return result.toString();
    }

    private static void assertColorClose(int expected, int actual) {
        String message =
            "expected ARGB " + Color.alpha(expected) + ","
            + Color.red(expected) + "," + Color.green(expected) + ","
            + Color.blue(expected) + " but was "
            + Color.alpha(actual) + "," + Color.red(actual) + ","
            + Color.green(actual) + "," + Color.blue(actual);
        assertTrue(message,
                   Math.abs(Color.red(expected) - Color.red(actual)) <= 1);
        assertTrue(message,
                   Math.abs(Color.green(expected) - Color.green(actual)) <= 1);
        assertTrue(message,
                   Math.abs(Color.blue(expected) - Color.blue(actual)) <= 1);
        assertEquals(message, Color.alpha(expected), Color.alpha(actual));
    }

    private static void assertStaticReaderPage(long[] snapshot,
                                               Bitmap bitmap) {
        assertEquals(1, snapshot[OctavoSurfaceView.STATE_READER_INITIALIZED]);
        assertEquals(1, snapshot[OctavoSurfaceView.STATE_DOCUMENT_OPEN]);
        assertEquals(1, snapshot[OctavoSurfaceView.STATE_READER_FRAME_READY]);
        assertTrue(snapshot[OctavoSurfaceView.STATE_VISIBLE_TEXT_SIZE] > 0);
        assertTrue(snapshot[OctavoSurfaceView.STATE_VISIBLE_TEXT_HASH] != 0);
        assertTrue(snapshot[OctavoSurfaceView.STATE_PAGE_INDEX] > 0);
        assertTrue(snapshot[OctavoSurfaceView.STATE_PAGE_COUNT] > 0);
        assertEquals(1, snapshot[OctavoSurfaceView.STATE_READER_VIEW_READY]);
        assertEquals(0, snapshot[OctavoSurfaceView.STATE_READER_VIEW_ERRORS]);
        assertTrue(snapshot[OctavoSurfaceView.STATE_READER_VIEW_DRAW_COUNT] > 1);

        int pageX = (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_X];
        int pageY = (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_Y];
        int pageWidth =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_WIDTH];
        int pageHeight =
            (int)snapshot[OctavoSurfaceView.STATE_PAGE_SURFACE_HEIGHT];
        assertTrue(pageX >= 0);
        assertTrue(pageY >= 0);
        assertTrue(pageWidth > 0);
        assertTrue(pageHeight > 0);
        assertTrue(pageX + pageWidth <= bitmap.getWidth());
        assertTrue(pageY + pageHeight <= bitmap.getHeight());

        assertColorClose(OctavoNative.clearColorArgb(),
                         bitmap.getPixel(1, pageY + pageHeight / 2));
        assertColorClose(Color.rgb(0xFF, 0xFD, 0xF9),
                         bitmap.getPixel(pageX + 4, pageY + 4));

        int inkPixels = 0;
        int expectedInk = Color.rgb(0x1B, 0x1A, 0x18);
        for (int y = pageY + 24; y < pageY + pageHeight - 24; ++y) {
            for (int x = pageX + 24; x < pageX + pageWidth - 24; ++x) {
                int pixel = bitmap.getPixel(x, y);
                if (Math.abs(Color.red(pixel) - Color.red(expectedInk)) <= 1
                    && Math.abs(Color.green(pixel) - Color.green(expectedInk)) <= 1
                    && Math.abs(Color.blue(pixel) - Color.blue(expectedInk)) <= 1) {
                    ++inkPixels;
                }
            }
        }
        assertTrue("Reader page did not contain rasterized text", inkPixels > 40);
    }

    @Test
    public void staticReaderFramePathsAndLifecycle()
        throws InterruptedException, IOException, NoSuchAlgorithmException {
        assertEquals("0.4.0-dev", OctavoNative.version());
        assertEquals("android", OctavoNative.platform());
        assertEquals("0.4.3-dev", OctavoNative.groundVersion());
        assertEquals("0.5.0-dev", OctavoNative.readerVersion());
        assertEquals("0.1.0-dev", OctavoNative.uiVersion());
        assertEquals("0.3.0-dev", OctavoNative.readerViewVersion());

        try (ActivityScenario<OctavoActivity> scenario =
                 ActivityScenario.launch(OctavoActivity.class)) {
            AtomicReference<String> filesPath = new AtomicReference<>();
            AtomicReference<String> cachePath = new AtomicReference<>();
            AtomicReference<String> fixturePath = new AtomicReference<>();
            AtomicReference<String> visibleText = new AtomicReference<>();
            scenario.onActivity(activity -> {
                OctavoSurfaceView surface =
                    (OctavoSurfaceView)activity.findViewById(R.id.octavo_surface);
                assertNotNull(surface);
                filesPath.set(surface.filesPathForTesting());
                cachePath.set(surface.cachePathForTesting());
                fixturePath.set(surface.fixturePathForTesting());
                visibleText.set(surface.visibleTextForTesting());
                assertEquals(activity.getFilesDir().getAbsolutePath(), filesPath.get());
                assertEquals(activity.getCacheDir().getAbsolutePath(), cachePath.get());
                assertEquals(new File(activity.getFilesDir(),
                                      "port2/octavo_port2.epub").getAbsolutePath(),
                             fixturePath.get());
            });
            assertNotNull(filesPath.get());
            assertNotNull(cachePath.get());
            assertNotNull(fixturePath.get());

            long[] initial = awaitPresentedFrame(scenario);
            scenario.onActivity(activity -> visibleText.set(
                ((OctavoSurfaceView)activity.findViewById(R.id.octavo_surface))
                    .visibleTextForTesting()));
            assertNotNull(visibleText.get());
            assertTrue(visibleText.get().contains("Chapter One"));
            assertTrue(visibleText.get().contains("First chapter paragraph"));
            File fixture = new File(fixturePath.get());
            assertTrue(fixture.isFile());
            assertEquals(1927, fixture.length());
            assertEquals("35EE6AB86D98D310BAAA0981905652D9D75BA4D814C34A6249AD2F66B45BE00A",
                         sha256(fixture));
            assertEquals(0, initial[OctavoSurfaceView.STATE_RENDER_FAILURE_COUNT]);
            assertTrue(initial[OctavoSurfaceView.STATE_SURFACE_GENERATION] >= 1);
            assertTrue(initial[OctavoSurfaceView.STATE_RESUME_COUNT] >= 1);
            Bitmap initialFrame = copyFrame(surface(scenario));
            assertNotNull(initialFrame);
            try {
                assertStaticReaderPage(initial, initialFrame);
            } finally {
                initialFrame.recycle();
            }

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
            Bitmap recreatedFrame = copyFrame(surface(scenario));
            assertNotNull(recreatedFrame);
            try {
                assertStaticReaderPage(recreated, recreatedFrame);
            } finally {
                recreatedFrame.recycle();
            }
        }
    }
}
