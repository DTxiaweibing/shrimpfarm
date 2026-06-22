package com.shrimpfarm.app.hq;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PricePoint;

import java.util.ArrayList;
import java.util.List;

public class HqChartActivity extends AppCompatActivity {

    private LineChart chart;
    private DatabaseHelper dbHelper;
    private XAxis xAxis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hq_chart);

        String itemName = getIntent().getStringExtra("item_name");
        if (itemName == null) {
            Toast.makeText(this, "缺少品种参数", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tv_chart_title);
        tvTitle.setText(getString(R.string.price_trend_title, itemName));

        chart = findViewById(R.id.line_chart);
        setupChart();

        dbHelper = new DatabaseHelper(this);
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

        xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
    }

    private void loadData(String itemName) {
        List<PricePoint> points = dbHelper.getPriceHistory(itemName);
        if (points == null || points.isEmpty()) {
            Toast.makeText(this, "暂无历史数据", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            PricePoint p = points.get(i);
            entries.add(new Entry(i, p.price));
            labels.add(p.date);
        }

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return idx >= 0 && idx < labels.size() ? labels.get(idx) : "";
            }
        });

        LineDataSet dataSet = new LineDataSet(entries, "价格(元/斤)");
        dataSet.setColor(Color.parseColor("#0A7E8C"));
        dataSet.setCircleColor(Color.parseColor("#0A7E8C"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(dataSet));
        chart.invalidate();
    }
}