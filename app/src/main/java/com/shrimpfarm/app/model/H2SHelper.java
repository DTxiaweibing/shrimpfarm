package com.shrimpfarm.app.model;

import android.content.Context;

import com.shrimpfarm.app.R;

public class H2SHelper {
    public static String getAdvice(double h2s) {
        if (h2s > 0.05) return "硫化氢超标:改底增氧硫酸亚铁";
        if (h2s > 0.01) return "硫化氢超标:加强改底增氧";
        return "";
    }

    public static String getAdvice(Context context, double h2s) {
        if (h2s > 0.05) return context.getString(R.string.alert_h2s_severe);
        if (h2s > 0.01) return context.getString(R.string.alert_h2s);
        return "";
    }
}
