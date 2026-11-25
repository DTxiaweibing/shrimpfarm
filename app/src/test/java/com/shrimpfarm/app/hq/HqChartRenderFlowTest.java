package com.shrimpfarm.app.hq;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HqChartRenderFlowTest {

    /** 柱状图（1 条图例）切回折线图（2 条）时，图例尺寸缓存必须跟着更新，否则绘制第 2 条越界崩溃。 */
    @Test
    public void updatesLegendBeforeDataSetSoCachedSizesMatchEntries() {
        final List<String> calls = new ArrayList<>();
        final int[] entries = {1};       // 柱状图：1 条图例
        final int[] cachedSizes = {1};   // 上一次绘制留下的图例尺寸缓存

        HqChartRenderFlow.render(
                () -> {
                    calls.add("legend");
                    entries[0] = 2;      // 切回折线图：价格区间 + 中值线
                },
                () -> {
                    calls.add("setData");
                    cachedSizes[0] = entries[0]; // MPAndroidChart 在 setData 时按当前图例条目数重算缓存
                },
                () -> calls.add("yRange"),
                () -> {
                    calls.add("invalidate");
                    assertTrue("图例尺寸缓存少于条目数，绘制时会越界", cachedSizes[0] >= entries[0]);
                });

        assertEquals(Arrays.asList("legend", "setData", "yRange", "invalidate"), calls);
    }
}
