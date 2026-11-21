package com.shrimpfarm.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 系统栏留白的底色：顶部状态栏一条、底部导航栏（或输入法）一条，中间透明留给页面。
 *
 * <p>API 35+ 强制 edge-to-edge 后窗口不再自己让位，由 {@link MyApplication} 给
 * {@code android.R.id.content} 加 padding。背景是铺满整个 View 的（含 padding），
 * 若只设一个纯色，底部导航栏区就会跟状态栏同色——深绿页面上看起来就是“底栏发绿”。
 * 这里只画上下两条，中间透明，页面自己的布局照常盖住中间。</p>
 */
public class SystemBarBackgroundDrawable extends Drawable {

    private final Paint paint = new Paint();
    private final int topColor;
    private final int bottomColor;
    private int topHeight;
    private int bottomHeight;

    public SystemBarBackgroundDrawable(int topColor, int bottomColor) {
        this.topColor = topColor;
        this.bottomColor = bottomColor;
    }

    /** 更新上下两条留白的高度（px）。高度没变时不做重绘。 */
    public void setInsets(int topHeight, int bottomHeight) {
        if (this.topHeight == topHeight && this.bottomHeight == bottomHeight) {
            return;
        }
        this.topHeight = topHeight;
        this.bottomHeight = bottomHeight;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (topHeight > 0) {
            paint.setColor(topColor);
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + topHeight, paint);
        }
        if (bottomHeight > 0) {
            paint.setColor(bottomColor);
            canvas.drawRect(bounds.left, bounds.bottom - bottomHeight, bounds.right, bounds.bottom, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
