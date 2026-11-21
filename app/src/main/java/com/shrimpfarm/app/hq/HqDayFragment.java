package com.shrimpfarm.app.hq;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PriceCategory;
import com.shrimpfarm.app.model.PriceData;
import com.shrimpfarm.app.model.PriceItem;

import java.util.List;

/**
 * 行情页的一页 = 一天。
 *
 * <p>先读本地缓存；本地没有该日数据时再按日期查远端，拿到后写回本地，
 * 下次翻到这一天就不用再走网络。</p>
 */
public class HqDayFragment extends Fragment {

    private static final String ARG_DATE = "date";

    private String date;
    private PriceCategoryAdapter adapter;
    private View statusLayout;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private boolean loading;

    public static HqDayFragment newInstance(String date) {
        HqDayFragment fragment = new HqDayFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DATE, date);
        fragment.setArguments(args);
        return fragment;
    }

    public String getDate() {
        return date;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hq_day, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        date = getArguments() != null ? getArguments().getString(ARG_DATE) : null;

        RecyclerView recycler = view.findViewById(R.id.recycler_market);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PriceCategoryAdapter();
        adapter.setOnItemClickListener(this::openChart);
        recycler.setAdapter(adapter);

        statusLayout = view.findViewById(R.id.layout_status);
        progressBar = view.findViewById(R.id.progress_bar);
        tvStatus = view.findViewById(R.id.tv_status);
        tvStatus.setOnClickListener(v -> {
            if (!loading) load();
        });

        load();
    }

    /** 本地或远端数据更新后，由 Activity 调用刷新当前页。 */
    public void reload() {
        load();
    }

    private void load() {
        if (date == null || loading) return;

        Context context = getContext();
        if (context == null) return;

        List<PriceCategory> local = DatabaseHelper.getInstance(context).getMarketPricesByDate(date);
        if (local != null && !local.isEmpty()) {
            showData(local);
            return;
        }
        fetchRemote();
    }

    private void fetchRemote() {
        final Context context = getContext();
        if (context == null) return;

        loading = true;
        showLoading();
        HqSupabaseClient.fetchByDate(context, date, new HqSupabaseClient.OnDataCallback() {
            @Override
            public void onSuccess(PriceData data) {
                if (!isAdded()) return;
                Context ctx = getContext();
                if (ctx != null) DatabaseHelper.getInstance(ctx).saveMarketPrices(data);
                loading = false;
                showData(data.categories);
            }

            @Override
            public void onError(String msg) {
                if (!isAdded()) return;
                loading = false;

                // 远端返回空/失败时本地可能刚好被后台同步写入了，先复查一次。
                Context ctx = getContext();
                if (ctx != null) {
                    List<PriceCategory> local =
                            DatabaseHelper.getInstance(ctx).getMarketPricesByDate(date);
                    if (local != null && !local.isEmpty()) {
                        showData(local);
                        return;
                    }
                }
                showEmpty(msg != null);
            }
        });
    }

    private void showData(List<PriceCategory> categories) {
        adapter.setData(categories);
        statusLayout.setVisibility(View.GONE);
    }

    private void showLoading() {
        statusLayout.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.hq_day_loading);
        tvStatus.setClickable(false);
    }

    private void showEmpty(boolean networkError) {
        statusLayout.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        tvStatus.setText(networkError
                ? R.string.hq_day_network_retry
                : R.string.hq_day_empty_retry);
        tvStatus.setClickable(true);
    }

    private void openChart(PriceItem item) {
        Context context = getContext();
        if (context == null) return;
        Intent intent = new Intent(context, HqChartActivity.class);
        intent.putExtra("item_name", item.name);
        startActivity(intent);
    }
}
