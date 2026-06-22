package com.shrimpfarm.app.model;

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
}
