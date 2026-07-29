package com.shrimpfarm.app.model;

import android.content.Context;

import com.shrimpfarm.app.R;

public class ORPHelper {
    public static String getAdvice(double orp) {
        if (orp < 100) return "ORP超标:增氧改底减料";
        if (orp < 150) return "ORP超标:增氧改底";
        return "";
    }

    public static String getAdvice(Context context, double orp) {
        if (orp < 100) return context.getString(R.string.alert_orp_low);
        if (orp < 150) return context.getString(R.string.alert_orp_moderate);
        return "";
    }
}
