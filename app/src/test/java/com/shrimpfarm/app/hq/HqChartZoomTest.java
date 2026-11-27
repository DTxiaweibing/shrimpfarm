package com.shrimpfarm.app.hq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HqChartZoomTest {

    /** 双击必须每次把 X 轴翻倍，而不是库默认的 1.4 倍。 */
    @Test
    public void doublesXScaleOnEachDoubleTap() {
        assertEquals(2f, HqChartZoom.nextScale(1f, 3), 0f);
        assertEquals(4f, HqChartZoom.nextScale(2f, 3), 0f);
        assertEquals(8f, HqChartZoom.nextScale(4f, 3), 0f);
    }

    /** 到达「每天可见」的档位后再双击，必须回到原始状态而不是继续放大。 */
    @Test
    public void returnsToOriginalScaleAfterCap() {
        assertEquals(1f, HqChartZoom.nextScale(8f, 3), 0f);
    }

    /** 档位数由「一天至少 50px」反解；空数据也要有安全档位。 */
    @Test
    public void derivesCapFromDayCountAndChartWidth() {
        assertEquals(3, HqChartZoom.stepsForAllDays(60, 600f, 50f));
        assertEquals(3, HqChartZoom.stepsForAllDays(60, 700f, 50f));
        assertEquals(2, HqChartZoom.stepsForAllDays(60, 900f, 50f));
        assertEquals(1, HqChartZoom.stepsForAllDays(60, 4000f, 50f));
        assertEquals(1, HqChartZoom.stepsForAllDays(0, 600f, 50f));
    }
}
