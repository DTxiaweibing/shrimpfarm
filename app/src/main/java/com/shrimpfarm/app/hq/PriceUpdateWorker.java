package com.shrimpfarm.app.hq;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.model.PriceData;

/** 后台定时同步无锡行情到本地缓存，数据源为 Supabase。 */
public class PriceUpdateWorker extends Worker {

    private static final String TAG = "PriceUpdateWorker";

    public PriceUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            PriceData data = HqSupabaseClient.fetchSync();
            if (data == null || data.date == null || data.categories == null || data.categories.isEmpty()) {
                Log.w(TAG, "库中暂无行情数据");
                return Result.failure();
            }

            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getApplicationContext());
            dbHelper.saveMarketPrices(data);
            Log.i(TAG, "已同步无锡行情 " + data.date + "，分类数 " + data.categories.size());
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "同步行情失败", e);
            return Result.retry();
        }
    }
}
