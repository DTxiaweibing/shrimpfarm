package com.shrimpfarm.app.model;

import android.content.Context;

import com.shrimpfarm.app.R;

public class VibrioHelper {
    public static String getAdvice(double vibrio, int day) {
        boolean isSmallSeed = day <= 60;
        String bottom = isSmallSeed ? "聚铁" : "过硫";
        if (vibrio >= 1001) {
            return "弧菌严重超标:停料聚合硫酸铁改底碘制剂消杀看不到掉苗试喂50%饲料";
        }
        if (vibrio >= 500) {
            return "弧菌超标:聚铁二氧化氯噬菌体";
        }
        if (vibrio >= 200) {
            return "弧菌偏高:" + bottom + "改底";
        }
        return "";
    }

    public static String getAdvice(Context context, double vibrio, int day) {
        boolean isSmallSeed = day <= 60;
        if (vibrio >= 1001) {
            return context.getString(R.string.alert_vibrio_severe);
        }
        if (vibrio >= 500) {
            return context.getString(R.string.alert_vibrio_high);
        }
        if (vibrio >= 200) {
            String bottom = context.getString(isSmallSeed ? R.string.alert_vibrio_pfs : R.string.alert_vibrio_peroxy);
            return context.getString(R.string.alert_vibrio_elevated, bottom);
        }
        return "";
    }
}
