package com.shrimpfarm.app.hq;

import java.util.Locale;

/**
 * 价格轴刻度规则：只落在 0.5 元的整数倍上。
 *
 * <p>MPAndroidChart 默认按 1/2/5 序列算间隔，区间很窄时会得到 0.2 的间隔，
 * 于是出现 11.2、11.4 这类数据库里根本不存在的标签。</p>
 */
public final class HqAxisScale {

    /** 行情价格最小变动单位：半元。 */
    public static final float STEP = 0.5f;

    private HqAxisScale() {
    }

    /** 把任意值吸附到最近的半元网格。 */
    public static float snap(float value) {
        return Math.round(value / STEP) * STEP;
    }

    /** 整数不带小数，半元保留一位小数。 */
    public static String format(float value) {
        float snapped = snap(value);
        if (snapped == Math.round(snapped)) return String.valueOf((int) snapped);
        return String.format(Locale.CHINA, "%.1f", snapped);
    }
}
