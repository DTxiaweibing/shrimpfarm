package com.shrimpfarm.app.model;

import android.content.Context;

import com.shrimpfarm.app.R;

public class DOHelper {
    public static String getAdvice(double doValue) {
        if (doValue < 3) return "溶氧超标:全开增氧机+换水";
        if (doValue < 5) return "溶氧超标:增加增氧机";
        return "";
    }

    public static String getAdvice(Context context, double doValue) {
        if (doValue < 3) return context.getString(R.string.alert_do_low);
        if (doValue < 5) return context.getString(R.string.alert_do_moderate);
        return "";
    }
}
