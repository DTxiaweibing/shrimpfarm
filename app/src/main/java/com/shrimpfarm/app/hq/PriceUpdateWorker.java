package com.shrimpfarm.app.hq;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.model.PriceData;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PriceUpdateWorker extends Worker {

    private static final String TAG = "PriceUpdateWorker";
    private static final String PRICE_URL = "https://dtxiaweibing.github.io/TIMU/price.json";

    public PriceUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            OkHttpClient client = new OkHttpClient.Builder().build();
            Request request = new Request.Builder().url(PRICE_URL).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                return Result.retry();
            }

            String json = response.body().string();
            PriceData data = new Gson().fromJson(json, PriceData.class);

            if (data == null || data.date == null || data.categories == null || data.categories.isEmpty()) {
                return Result.failure();
            }

            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
            dbHelper.saveMarketPrices(data);

            return Result.success();
        } catch (IOException e) {
            return Result.retry();
        } catch (Exception e) {
            return Result.failure();
        }
    }
}