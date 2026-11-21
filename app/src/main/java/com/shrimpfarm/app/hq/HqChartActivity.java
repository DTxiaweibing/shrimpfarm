package com.shrimpfarm.app.hq;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.CombinedChart;import com.github.mikephil.charting.components.Legend;
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
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PricePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 单品种价格走势。
 *
 * <p>每天画一条从最低价到最高价的色带，中间叠一条中值折线：
 * 带子的高度就是当天的议价区间，中值线表示整体走势。</p>
 */
public class HqChartActivity extends BaseActivity {

    private static final int BAND_COLOR = Color.argb(80, 10, 126, 140);
    private static final int LINE_COLOR = Color.parseColor("#0A7E8C");

    private CombinedChart chart;
    private DatabaseHelper dbHelper;
    private XAxis xAxis;

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

        dbHelper = DatabaseHelper.getInstance(this);
        loadData(itemName);
    }

    private void setupChart() {
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(8f, 8f, 8f, 8f);
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR,
                CombinedChart.DrawOrder.LINE
        });

        xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-35f);

        chart.getAxisRight().setEnabled(false);
        YAxis left = chart.getAxisLeft();
        left.setDrawGridLines(true);
        left.setGridColor(0xFFE6E6E6);
        left.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value == Math.round(value)) return String.valueOf((int) value);
                return String.format(Locale.CHINA, "%.1f", value);
            }
        });

        chart.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        chart.getLegend().setOrientation(Legend.LegendOrientation.HORIZONTAL);
        chart.getLegend().setDrawInside(false);
        chart.getLegend().setTextSize(11f);
    }

    private void loadData(String itemName) {
        List<PricePoint> points = dbHelper.getPriceHistory(itemName);
        if (points == null || points.isEmpty()) {
            Toast.makeText(this, getString(R.string.hq_chart_no_data), Toast.LENGTH_SHORT).show();
            return;
        }

        List<BarEntry> bandEntries = new ArrayList<>();
        List<Entry> midEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            PricePoint p = points.get(i);
            // 堆叠柱：第一段透明从 0 撑到最低价，第二段显示区间高度。
            bandEntries.add(new BarEntry(i, new float[]{p.low, p.high - p.low}));
            midEntries.add(new Entry(i, p.price));
            labels.add(shortDate(p.date));
            min = Math.min(min, p.low);
            max = Math.max(max, p.high);
        }

        BarDataSet bandSet = new BarDataSet(bandEntries, "");
        bandSet.setColors(Color.TRANSPARENT, BAND_COLOR);
        bandSet.setDrawValues(false);
        bandSet.setHighlightEnabled(false);

        BarData barData = new BarData(bandSet);
        barData.setBarWidth(0.9f);

        LineDataSet midSet = new LineDataSet(midEntries, "");
        midSet.setColor(LINE_COLOR);
        midSet.setCircleColor(LINE_COLOR);
        midSet.setLineWidth(2f);
        midSet.setCircleRadius(3.5f);
        midSet.setDrawCircleHole(false);
        midSet.setDrawValues(false);
        midSet.setHighlightEnabled(false);
        midSet.setMode(LineDataSet.Mode.LINEAR);

        CombinedData data = new CombinedData();
        data.setData(barData);
        data.setData(new LineData(midSet));

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = Math.round(value);
                return idx >= 0 && idx < labels.size() ? labels.get(idx) : "";
            }
        });

        chart.setData(data);
        applyYRange(min, max);
        applyLegend();
        chart.invalidate();
    }

    /** 不从 0 起，否则 22~78 的区间会被压成一条细线。 */
    private void applyYRange(float min, float max) {
        float span = max - min;
        float pad = span > 0f ? span * 0.12f : Math.max(max * 0.05f, 1f);
        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(Math.max(0f, min - pad));
        left.setAxisMaximum(max + pad);
    }

    private void applyLegend() {
        List<LegendEntry> entries = new ArrayList<>();
        entries.add(new LegendEntry(getString(R.string.hq_chart_legend_range),
                Legend.LegendForm.SQUARE, 10f, 2f, null, BAND_COLOR));
        entries.add(new LegendEntry(getString(R.string.hq_chart_legend_mid),
                Legend.LegendForm.LINE, 18f, 2f, null, LINE_COLOR));
        chart.getLegend().setCustom(entries);
    }

    private String shortDate(String date) {
        if (date == null) return "";
        return date.length() >= 10 ? date.substring(5) : date;
    }
}
