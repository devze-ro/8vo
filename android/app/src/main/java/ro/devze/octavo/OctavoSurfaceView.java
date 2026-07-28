package ro.devze.octavo;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

final class OctavoSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private long nativeHandle;

    OctavoSurfaceView(Context context) {
        super(context);
        nativeHandle = OctavoNative.create();
        if (nativeHandle == 0) {
            throw new IllegalStateException("Unable to create the 8vo native bootstrap");
        }

        setId(R.id.octavo_surface);
        setBackgroundColor(Color.rgb(24, 22, 20));
        setContentDescription("8vo reader surface");
        setFocusable(true);
        setFocusableInTouchMode(true);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceCreated(nativeHandle, holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceChanged(nativeHandle, format, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (nativeHandle != 0) {
            OctavoNative.surfaceDestroyed(nativeHandle);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (nativeHandle == 0) {
            return false;
        }
        return OctavoNative.touch(nativeHandle,
                                  event.getActionMasked(),
                                  event.getX(),
                                  event.getY(),
                                  event.getEventTime());
    }

    void release() {
        if (nativeHandle != 0) {
            getHolder().removeCallback(this);
            OctavoNative.destroy(nativeHandle);
            nativeHandle = 0;
        }
    }
}
