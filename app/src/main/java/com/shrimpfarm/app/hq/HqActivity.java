package com.shrimpfarm.app.hq;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.shrimpfarm.app.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PriceCategory;
import com.shrimpfarm.app.model.PriceData;
import com.shrimpfarm.app.model.PriceItem;

import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;

public class HqActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    private TextView tvDate;
    private RecyclerView recyclerMarket;
    private PriceCategoryAdapter adapter;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hq);

        try {
            dbHelper = DatabaseHelper.getInstance(this);
            tvDate = findViewById(R.id.tv_date);
            recyclerMarket = findViewById(R.id.recycler_market);
            Button btnRefresh = findViewById(R.id.btn_refresh);

            recyclerMarket.setLayoutManager(new LinearLayoutManager(this));
            adapter = new PriceCategoryAdapter();
            recyclerMarket.setAdapter(adapter);

            adapter.setOnItemClickListener(this::openChart);
            btnRefresh.setOnClickListener(v -> fetchData());

            loadFromDbThenRefresh();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.hq_error_init, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void loadFromDbThenRefresh() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        
        try {
            List<PriceCategory> list = dbHelper.getMarketPricesByDate(today);
            if (list != null && !list.isEmpty()) {
                displayData(today, list);
            } else {
                PriceData lastData = dbHelper.getLatestMarketPrices();
                if (lastData != null && lastData.date != null) {
                    displayData(lastData.date + getString(R.string.hq_label_cached), lastData.categories);
                } else {
                    tvDate.setText(getString(R.string.hq_label_date_awaiting));
                }
            }
        } catch (Exception e) {
            tvDate.setText(getString(R.string.hq_label_date_awaiting));
        }

        fetchDataInBackground();
    }

    private void fetchDataInBackground() {
        HqCrawler.fetchLatest(this, new HqCrawler.OnDataCallback() {
            @Override
            public void onSuccess(PriceData data) {
                if (data != null && data.date != null) {
                    dbHelper.saveMarketPrices(data);
                    displayData(data.date, data.categories);
                }
            }

            @Override
            public void onError(String msg) {
                // background refresh silent
            }
        });
    }

    private void fetchData() {
        tvDate.setText(getString(R.string.hq_label_date_fetching));
        HqCrawler.fetchLatest(this, new HqCrawler.OnDataCallback() {
            @Override
            public void onSuccess(PriceData data) {
                if (data != null && data.date != null) {
                    dbHelper.saveMarketPrices(data);
                    displayData(data.date, data.categories);
                }
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(HqActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void displayData(String date, List<PriceCategory> categories) {
        tvDate.setText(getString(R.string.date_prefix, date));
        adapter.setData(categories);
    }

    private void openChart(PriceItem item) {
        Intent intent = new Intent(this, HqChartActivity.class);
        intent.putExtra("item_name", item.name);
        startActivity(intent);
    }
}