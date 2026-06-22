package com.shrimpfarm.app.hq;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.shrimpfarm.app.model.PriceData;
import com.shrimpfarm.app.utils.HttpClientSingleton;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HqCrawler {

    private static final String PAGE_URL = "https://dtxiaweibing.github.io/TIMU/price.json";
    private static final int MAX_RETRIES = 3;

    public interface OnDataCallback {
        void onSuccess(PriceData data);
        void onError(String msg);
    }

    public static void fetchLatest(OnDataCallback callback) {
        fetchWithRetry(0, callback);
    }

    private static void fetchWithRetry(int retryCount, OnDataCallback callback) {
        OkHttpClient client = HttpClientSingleton.getInstance();
        Request request = new Request.Builder().url(PAGE_URL).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (retryCount < MAX_RETRIES - 1) {
                    final int nextRetry = retryCount + 1;
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> fetchWithRetry(nextRetry, callback),
                        3000
                    );
                } else {
                    runOnUiThread(() -> callback.onError("网络超时，已重试" + MAX_RETRIES + "次"));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> callback.onError("服务器响应错误: " + response.code()));
                    return;
                }
                String json = response.body().string();
                if (json.isEmpty() || json.equals("{}")) {
                    runOnUiThread(() -> callback.onError("暂无行情数据"));
                    return;
                }
                try {
                    PriceData data = new Gson().fromJson(json, PriceData.class);
                    runOnUiThread(() -> callback.onSuccess(data));
                } catch (Exception e) {
                    runOnUiThread(() -> callback.onError("JSON解析失败: " + e.getMessage()));
                }
            }
        });
    }

    private static void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}