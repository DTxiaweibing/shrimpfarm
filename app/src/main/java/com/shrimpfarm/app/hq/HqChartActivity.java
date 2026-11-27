package com.shrimpfarm.app.hq;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PricePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 单品种价格走势：点一下图表在两种模式间切换。
 *
 * <p>折线模式每天画一条最低价到最高价的色带，叠一条中值折线；
 * 柱状模式用当天中值画橙色柱子。Y 轴不从 0 起，柱子用透明底座
 * 从 0 撑到轴最小值，橙色段才从图底正常长出。</p>
 */
public class HqChartActivity extends BaseActivity {

    private static final int BAND_COLOR = Color.argb(80, 10, 126, 140);
    private static final int LINE_COLOR = Color.parseColor("#0A7E8C");
    private static final int BAR_COLOR = Color.parseColor("#FF9800");
    private static final float MIN_DAY_WIDTH_DP = 24f;

    private CombinedChart chart;
    private DatabaseHelper dbHelper;
    private XAxis xAxis;
    private final List<PricePoint> points = new ArrayList<>();
    private boolean barMode;
    private float minPrice;
    private float maxPrice;

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hq_chart);

        String itemName = getIntent().getStringExtra("item_name");
        if (itemName == null) {
            Toast.makeText(this, getString(R.string.hq_chart_error_no_param), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tv_chart_title);
        tvTitle.setText(getString(R.string.price_trend_title, itemName));

        chart = findViewById(R.id.combined_chart);
        setupChart();
        setupToggle();

        dbHelper = DatabaseHelper.getInstance(this);
        loadData(itemName);
    }

    private void setupChart() {
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        // 价格轴始终固定：捏合只缩放日期轴，正反方向都生效。
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(true);
        // 双击改为自己的档位逻辑（翻倍放大，到顶后还原），不用库默认的 X/Y 一起放大。
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(8f, 8f, 8f, 30f);
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR,
                CombinedChart.DrawOrder.LINE
        });

        xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setTextSize(14f);
        xAxis.setTypeface(Typeface.DEFAULT_BOLD);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-35f);

        chart.getAxisRight().setEnabled(false);
        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.BLACK);
        left.setTextSize(16f);
        left.setTypeface(Typeface.DEFAULT_BOLD);
        left.setDrawGridLines(true);
        left.setGridColor(0xFFE6E6E6);
        // 让库生成的刻度本身落在半元网格上，避免出现 11.2 这类间隔。
        left.setGranularity(HqAxisScale.STEP);
        left.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return HqAxisScale.format(value);
            }
        });

        chart.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        chart.getLegend().setOrientation(Legend.LegendOrientation.HORIZONTAL);
        chart.getLegend().setDrawInside(false);
        chart.getLegend().setTextSize(13f);
        chart.getLegend().setTextColor(Color.BLACK);
    }

    /** 用手势监听的单击回调，避免和拖拽/缩放冲突。 */
    private void setupToggle() {
        chart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent e, ChartTouchListener.ChartGesture g) {
            }

            @Override
            public void onChartGestureEnd(MotionEvent e, ChartTouchListener.ChartGesture g) {
            }

            @Override
            public void onChartLongPressed(MotionEvent e) {
            }

            @Override
            public void onChartDoubleTapped(MotionEvent e) {
                if (points.isEmpty()) return;
                float minDayWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        MIN_DAY_WIDTH_DP, getResources().getDisplayMetrics());
                int steps = HqChartZoom.stepsForAllDays(points.size(), chart.getWidth(), minDayWidth);
                float target = HqChartZoom.nextScale(chart.getScaleX(), steps);
                if (target <= 1f) {
                    chart.fitScreen();
                } else {
                    chart.zoom(target / chart.getScaleX(), 1f, e.getX(), e.getY());
                }
            }

            @Override
            public void onChartSingleTapped(MotionEvent e) {
                if (points.isEmpty()) return;
                barMode = !barMode;
                render();
            }

            @Override
            public void onChartFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            }

            @Override
            public void onChartScale(MotionEvent e, float sx, float sy) {
            }

            @Override
            public void onChartTranslate(MotionEvent e, float dx, float dy) {
            }
        });
    }

    private void loadData(String itemName) {
        List<PricePoint> loaded = dbHelper.getPriceHistory(itemName);
        if (loaded == null || loaded.isEmpty()) {
            Toast.makeText(this, getString(R.string.hq_chart_no_data), Toast.LENGTH_SHORT).show();
            return;
        }

        points.clear();
        points.addAll(loaded);
        minPrice = Float.MAX_VALUE;
        maxPrice = -Float.MAX_VALUE;
        final List<String> labels = new ArrayList<>();
        for (PricePoint p : points) {
            labels.add(shortDate(p.date));
            minPrice = Math.min(minPrice, p.low);
            maxPrice = Math.max(maxPrice, p.high);
        }

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = Math.round(value);
                return idx >= 0 && idx < labels.size() ? labels.get(idx) : "";
            }
        });
        render();
    }

    private void render() {
        HqChartRenderFlow.render(
                this::applyLegend,
                () -> chart.setData(buildData()),
                this::applyYRange,
                chart::invalidate);
    }

    private CombinedData buildData() {
        CombinedData data = new CombinedData();
        data.setData(buildBars(axisMinimum()));
        if (!barMode) data.setData(buildLine());
        return data;
    }

    private BarData buildBars(float base) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            PricePoint p = points.get(i);
            if (barMode) {
                entries.add(new BarEntry(i, new float[]{base, Math.max(0f, p.price - base)}));
            } else {
                entries.add(new BarEntry(i, new float[]{p.low, Math.max(0f, p.high - p.low)}));
            }
        }
        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(Color.TRANSPARENT, barMode ? BAR_COLOR : BAND_COLOR);
        set.setDrawValues(false);
        set.setHighlightEnabled(false);
        BarData data = new BarData(set);
        data.setBarWidth(barMode ? 0.6f : 0.9f);
        return data;
    }

    private LineData buildLine() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            entries.add(new Entry(i, points.get(i).price));
        }
        LineDataSet set = new LineDataSet(entries, "");
        set.setColor(LINE_COLOR);
        set.setCircleColor(LINE_COLOR);
        set.setLineWidth(2f);
        set.setCircleRadius(3.5f);
        set.setDrawCircleHole(false);
        set.setDrawValues(false);
        set.setHighlightEnabled(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        return new LineData(set);
    }

    private float axisPadding() {
        float span = maxPrice - minPrice;
        return span > 0f ? span * 0.12f : Math.max(maxPrice * 0.05f, 1f);
    }

    private float axisMinimum() {
        return Math.max(0f, minPrice - axisPadding());
    }

    private void applyYRange() {
        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(axisMinimum());
        left.setAxisMaximum(maxPrice + axisPadding());
    }

    private void applyLegend() {
        List<LegendEntry> entries = new ArrayList<>();
        if (barMode) {
            entries.add(new LegendEntry(getString(R.string.hq_chart_legend_bar),
                    Legend.LegendForm.SQUARE, 10f, 2f, null, BAR_COLOR));
        } else {
            entries.add(new LegendEntry(getString(R.string.hq_chart_legend_range),
                    Legend.LegendForm.SQUARE, 10f, 2f, null, BAND_COLOR));
            entries.add(new LegendEntry(getString(R.string.hq_chart_legend_mid),
                    Legend.LegendForm.LINE, 18f, 2f, null, LINE_COLOR));
        }
        chart.getLegend().setCustom(entries);
    }

    private String shortDate(String date) {
        if (date == null) return "";
        return date.length() >= 10 ? date.substring(5) : date;
    }
}
