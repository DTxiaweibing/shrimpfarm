package com.shrimpfarm.app.hq;

/** 价格走势图 X 轴缩放档位：双击每次翻倍，到「每天可见」档后再双击复位。 */
final class HqChartZoom {

    private static final int MAX_STEPS = 6;

    private HqChartZoom() {
    }

    static float nextScale(float current, int maxSteps) {
        float cap = (float) (1 << maxSteps);
        if (current >= cap - 0.001f) return 1f;
        return Math.min(current * 2f, cap);
    }

    static int stepsForAllDays(int pointCount, float chartWidthPx, float minDayWidthPx) {
        if (pointCount <= 0 || chartWidthPx <= 0f || minDayWidthPx <= 0f) return 1;
        double required = minDayWidthPx * pointCount / chartWidthPx;
        int steps = (int) Math.ceil(Math.log(required) / Math.log(2.0));
        return Math.min(Math.max(steps, 1), MAX_STEPS);
    }
}
