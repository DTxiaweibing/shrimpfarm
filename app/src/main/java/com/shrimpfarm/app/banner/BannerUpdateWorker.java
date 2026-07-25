package com.shrimpfarm.app.banner;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shrimpfarm.app.model.BannerItem;
import com.shrimpfarm.app.utils.HttpClientSingleton;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BannerUpdateWorker extends Worker {

    private static final String TAG = "BannerUpdWorker";
    private static final String BANNER_JSON_URL = "https://dtxiaweibing.github.io/TIMU/banner.json";
    private static final String PREFS_BANNER_JSON_CACHE = "cached_banner_json";

    public BannerUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        OkHttpClient client = HttpClientSingleton.getInstance();

        try {
            Request jsonReq = new Request.Builder().url(BANNER_JSON_URL).build();
            Response jsonResp = client.newCall(jsonReq).execute();
            if (!jsonResp.isSuccessful()) {
                Log.w(TAG, "下载失败，HTTP: " + jsonResp.code());
                return Result.retry();
            }

            String newJson = jsonResp.body().string();
            List<BannerItem> items = new Gson().fromJson(newJson, new TypeToken<List<BannerItem>>() {}.getType());
            if (items == null || items.isEmpty()) {
                Log.w(TAG, "JSON 内容为空或格式错误");
                return Result.failure();
            }

            SharedPreferences prefs = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(PREFS_BANNER_JSON_CACHE, newJson)
                    .apply();

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "后台更新异常: " + e.getMessage());
            return Result.retry();
        }
    }

}