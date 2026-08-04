package ro.devze.octavo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

final class OctavoReaderImageBridge {
    static final long PACKET_VERSION = 1;
    static final int HEADER_COUNT = 2;
    static final int ROW_STRIDE = 4;
    static final int MAX_FRAME_IMAGES = 16;
    static final int STATUS_UNAVAILABLE = 0;
    static final int STATUS_LOADED = 1;
    static final int STATUS_DIMENSION_CAP = 4;
    static final int STATUS_DECODE_FAILED = 5;
    static final int STATUS_CACHE_FULL = 6;
    static final int MAX_DIMENSION = 4096;
    static final long MAX_PIXEL_COUNT = 8L * 1024L * 1024L;
    static final long MAX_PREPARATION_ENCODED_BYTES =
        16L * 1024L * 1024L;
    static final long MAX_PREPARATION_DECODED_PIXELS =
        8L * 1024L * 1024L;

    private static final String TAG = "8vo";

    private OctavoReaderImageBridge() {
    }

    static final class PreparationBudget {
        private long encodedBytes;
        private long decodedPixels;
        private boolean rejected;
        private final long maximumEncodedBytes;
        private final long maximumDecodedPixels;

        PreparationBudget() {
            this(MAX_PREPARATION_ENCODED_BYTES,
                 MAX_PREPARATION_DECODED_PIXELS);
        }

        PreparationBudget(long maximumEncodedBytes,
                          long maximumDecodedPixels) {
            if (maximumEncodedBytes <= 0
                || maximumEncodedBytes > MAX_PREPARATION_ENCODED_BYTES
                || maximumDecodedPixels <= 0
                || maximumDecodedPixels
                    > MAX_PREPARATION_DECODED_PIXELS) {
                throw new IllegalArgumentException(
                    "Invalid reader image preparation budget");
            }
            this.maximumEncodedBytes = maximumEncodedBytes;
            this.maximumDecodedPixels = maximumDecodedPixels;
        }

        boolean canStartResource() {
            return !rejected
                && encodedBytes < maximumEncodedBytes
                && decodedPixels < maximumDecodedPixels;
        }

        long remainingEncodedBytes() {
            return rejected ? 0
                : maximumEncodedBytes - encodedBytes;
        }

        void rejectFurtherResources() {
            rejected = true;
        }

        boolean tryChargeEncodedBytes(long byteCount) {
            if (rejected || byteCount <= 0
                || byteCount
                    > maximumEncodedBytes - encodedBytes) {
                rejected = true;
                return false;
            }
            encodedBytes += byteCount;
            return true;
        }

        boolean tryChargeDecodedPixels(long pixelCount) {
            if (rejected || pixelCount <= 0
                || pixelCount
                    > maximumDecodedPixels - decodedPixels) {
                rejected = true;
                return false;
            }
            decodedPixels += pixelCount;
            return true;
        }

        long encodedBytes() {
            return encodedBytes;
        }

        long decodedPixels() {
            return decodedPixels;
        }
    }

    static boolean prepareFrameImages(long handle) {
        return prepareFrameImages(handle, new PreparationBudget());
    }

    static boolean prepareFrameImagesForTesting(
        long handle,
        long maximumEncodedBytes,
        long maximumDecodedPixels) {
        return prepareFrameImages(
            handle,
            new PreparationBudget(
                maximumEncodedBytes, maximumDecodedPixels));
    }

    private static boolean prepareFrameImages(
        long handle, PreparationBudget budget) {
        try {
            long[] packet = OctavoNative.frameImagesSnapshot(handle);
            if (!validPacket(packet)) {
                return false;
            }
            int count = (int)packet[1];
            Set<Long> preparedResources = new HashSet<>();
            for (int imageIndex = 0; imageIndex < count; ++imageIndex) {
                int row = HEADER_COUNT + imageIndex * ROW_STRIDE;
                int status = (int)packet[row + 1];
                boolean hasResource = packet[row + 2] != 0;
                if (!hasResource || status != STATUS_UNAVAILABLE) {
                    continue;
                }
                long resourceIndex = packet[row];
                if (!preparedResources.add(resourceIndex)) {
                    continue;
                }
                if (!budget.canStartResource()) {
                    publishFailure(handle, imageIndex, STATUS_CACHE_FULL);
                    continue;
                }
                decodeFrameImage(handle, imageIndex, budget);
            }
            return packetPrepared(
                OctavoNative.frameImagesSnapshot(handle));
        } catch (OutOfMemoryError | RuntimeException exception) {
            Log.w(TAG, "Reader image preparation failed", exception);
            return false;
        }
    }

    private static boolean validPacket(long[] packet) {
        if (packet == null || packet.length < HEADER_COUNT
            || packet[0] != PACKET_VERSION || packet[1] < 0
            || packet[1] > MAX_FRAME_IMAGES) {
            return false;
        }
        return packet.length == HEADER_COUNT + packet[1] * ROW_STRIDE;
    }

    private static boolean packetPrepared(long[] packet) {
        if (!validPacket(packet)) {
            return false;
        }
        int count = (int)packet[1];
        for (int imageIndex = 0; imageIndex < count; ++imageIndex) {
            int row = HEADER_COUNT + imageIndex * ROW_STRIDE;
            boolean hasResource = packet[row + 2] != 0;
            if (hasResource
                && packet[row + 1] == STATUS_UNAVAILABLE) {
                return false;
            }
        }
        return true;
    }

    private static void decodeFrameImage(long handle, int imageIndex,
                                         PreparationBudget budget) {
        Bitmap bitmap = null;
        try {
            long encodedByteLimit = budget.remainingEncodedBytes();
            byte[] encoded = OctavoNative.frameImageEncodedBytes(
                handle, imageIndex, encodedByteLimit);
            if (encoded == null) {
                publishFailure(handle, imageIndex, STATUS_DECODE_FAILED);
                return;
            }
            if (encoded.length == 0) {
                publishFailure(handle, imageIndex, STATUS_CACHE_FULL);
                budget.rejectFurtherResources();
                return;
            }
            if (!budget.tryChargeEncodedBytes(encoded.length)) {
                publishFailure(handle, imageIndex, STATUS_CACHE_FULL);
                return;
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(
                encoded, 0, encoded.length, bounds);
            long pixelCount =
                (long)bounds.outWidth * (long)bounds.outHeight;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                publishFailure(handle, imageIndex, STATUS_DECODE_FAILED);
                return;
            }
            if (bounds.outWidth > MAX_DIMENSION
                || bounds.outHeight > MAX_DIMENSION || pixelCount <= 0
                || pixelCount > MAX_PIXEL_COUNT) {
                publishFailure(handle, imageIndex, STATUS_DIMENSION_CAP);
                return;
            }
            if (!budget.tryChargeDecodedPixels(pixelCount)) {
                publishFailure(handle, imageIndex, STATUS_CACHE_FULL);
                return;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            bitmap = BitmapFactory.decodeByteArray(
                encoded, 0, encoded.length, options);
            if (bitmap == null || bitmap.getWidth() != bounds.outWidth
                || bitmap.getHeight() != bounds.outHeight) {
                publishFailure(handle, imageIndex, STATUS_DECODE_FAILED);
                return;
            }
            int[] pixels = new int[(int)pixelCount];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                             bitmap.getWidth(), bitmap.getHeight());
            if (!OctavoNative.setFrameImageDecodeResult(
                    handle, imageIndex, STATUS_LOADED,
                    bitmap.getWidth(), bitmap.getHeight(), pixels)) {
                Log.w(TAG, "Reader image cache rejected a decoded frame image");
            }
        } catch (OutOfMemoryError | RuntimeException exception) {
            Log.w(TAG, "Reader image decode failed", exception);
            publishFailure(handle, imageIndex, STATUS_DECODE_FAILED);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private static void publishFailure(long handle, int imageIndex,
                                       int status) {
        OctavoNative.setFrameImageDecodeResult(
            handle, imageIndex, status, 0, 0, null);
    }
}
