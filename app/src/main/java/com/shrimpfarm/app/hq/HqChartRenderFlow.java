package com.shrimpfarm.app.hq;

/** 图表重绘步骤。图例条目数变化时必须让 setData 按新条目重算缓存。 */
final class HqChartRenderFlow {

    private HqChartRenderFlow() {
    }

    static void render(Runnable applyLegend, Runnable setData,
                       Runnable applyYRange, Runnable invalidate) {
        applyLegend.run();
        setData.run();
        applyYRange.run();
        invalidate.run();
    }
}
