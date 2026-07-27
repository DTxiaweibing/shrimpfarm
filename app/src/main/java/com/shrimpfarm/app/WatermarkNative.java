package com.shrimpfarm.app;

import android.graphics.Canvas;
import android.graphics.Paint;

public class WatermarkNative {
    static {
        System.loadLibrary("native_watermark");
    }

    public static native boolean shouldShowWatermark(String stockingDate);
    public static native void renderWatermark(Canvas canvas, int width, int height, Paint paint);
    public static native String getRootKey();
    public static native String getOfficialFingerprint();
    public static native String getAiFallbackKey();
}
