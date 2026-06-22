// 创建一个新的工具类：ScreenUtils.java
package com.shrimpfarm.app.mixcalc.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

public class ScreenUtils {

    /**
     * 检测是否为平板（屏幕尺寸大于8英寸）
     */
    public static boolean isTablet(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            DisplayMetrics outMetrics = new DisplayMetrics();
            display.getMetrics(outMetrics);

            float widthInches = outMetrics.widthPixels / outMetrics.xdpi;
            float heightInches = outMetrics.heightPixels / outMetrics.ydpi;
            double diagonalInches = Math.sqrt(
                Math.pow(widthInches, 2) + Math.pow(heightInches, 2)
            );

            // 对角线大于8英寸即为平板
            return diagonalInches >= 8.0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取屏幕宽度（像素）
     */
    public static int getScreenWidthPx(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            DisplayMetrics outMetrics = new DisplayMetrics();
            display.getMetrics(outMetrics);
            return outMetrics.widthPixels;
        } catch (Exception e) {
            e.printStackTrace();
            return 1080; // 默认宽度
        }
    }

    /**
     * 获取屏幕高度（像素）
     */
    public static int getScreenHeightPx(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            DisplayMetrics outMetrics = new DisplayMetrics();
            display.getMetrics(outMetrics);
            return outMetrics.heightPixels;
        } catch (Exception e) {
            e.printStackTrace();
            return 1920; // 默认高度
        }
    }

    /**
     * 获取屏幕密度
     */
    public static float getDensity(Context context) {
        try {
            return context.getResources().getDisplayMetrics().density;
        } catch (Exception e) {
            e.printStackTrace();
            return 1.0f;
        }
    }
}
