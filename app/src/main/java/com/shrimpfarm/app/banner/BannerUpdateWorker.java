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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BannerUpdateWorker extends Worker {

    private static final String TAG = "BannerUpdWorker";
    private static final String BANNER_JSON_URL = "https://dtxiaweibing.github.io/TIMU/banner.json";
    private static final String PREFS_BANNER_JSON_CACHE = "cached_banner_json";

    private static final String[][] PROMO_PAGES = {
        {"https://dtxiaweibing.github.io/TIMU/promo/help.html", "help.html"},
        {"https://dtxiaweibing.github.io/TIMU/promo/zhaomu.html", "zhaomu.html"}
    };

    public BannerUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "后台开始下载最新 banner.json");

        OkHttpClient client = HttpClientSingleton.getInstance();

        downloadPromoPages(client);

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

            Log.d(TAG, "后台更新成功，已缓存最新 JSON");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "后台更新异常: " + e.getMessage());
            return Result.retry();
        }
    }

    private void downloadPromoPages(OkHttpClient client) {
        File cacheDir = new File(getApplicationContext().getFilesDir(), "banner_pages");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        for (String[] page : PROMO_PAGES) {
            String url = page[0];
            String name = page[1];
            File tmpFile = new File(cacheDir, name + ".tmp");
            File cachedFile = new File(cacheDir, name);
            String lmKey = "cached_promo_" + name + "_lm";

            // 残文件检测：文件存在但只有0字节，则删除重新下载
            if (cachedFile.exists() && cachedFile.length() == 0) {
                cachedFile.delete();
                prefs.edit().remove(lmKey).apply();
            }

            try {
                Request.Builder reqBuilder = new Request.Builder().url(url);
                String savedLm = prefs.getString(lmKey, "");
                if (!savedLm.isEmpty()) {
                    reqBuilder.header("If-Modified-Since", savedLm);
                }

                Response resp = client.newCall(reqBuilder.build()).execute();
                if (resp.code() == 304) {
                    Log.d(TAG, name + " 无更新，跳过");
                    continue;
                }
                if (resp.isSuccessful() && resp.body() != null) {
                    // 先写到 .tmp 文件（原子写入）
                    try (InputStream is = resp.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(tmpFile)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                    // 写入成功，重命名为正式文件
                    if (cachedFile.exists()) cachedFile.delete();
                    tmpFile.renameTo(cachedFile);

                    String serverLm = resp.header("Last-Modified");
                    if (serverLm != null) {
                        prefs.edit().putString(lmKey, serverLm).apply();
                    }
                    Log.d(TAG, "已缓存: " + name);
                }
            } catch (Exception e) {
                Log.e(TAG, name + " 下载失败: " + e.getMessage());
                // 下载失败，清理 .tmp 残文件
                if (tmpFile.exists()) tmpFile.delete();
            }
        }
    }
}