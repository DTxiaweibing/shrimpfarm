package com.shrimpfarm.app.hq;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.shrimpfarm.app.R;
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

    public static void fetchLatest(Context context, OnDataCallback callback) {
        fetchWithRetry(context, 0, callback);
    }

    private static void fetchWithRetry(Context context, int retryCount, OnDataCallback callback) {
        OkHttpClient client = HttpClientSingleton.getInstance();
        Request request = new Request.Builder().url(PAGE_URL).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (retryCount < MAX_RETRIES - 1) {
                    final int nextRetry = retryCount + 1;
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> fetchWithRetry(context, nextRetry, callback),
                        3000
                    );
                } else {
                    runOnUiThread(() -> callback.onError(context.getString(R.string.hq_error_network_timeout, MAX_RETRIES)));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> callback.onError(context.getString(R.string.hq_error_server, response.code())));
                    return;
                }
                String json = response.body().string();
                if (json.isEmpty() || json.equals("{}")) {
                    runOnUiThread(() -> callback.onError(context.getString(R.string.hq_error_no_data)));
                    return;
                }
                try {
                    PriceData data = new Gson().fromJson(json, PriceData.class);
                    runOnUiThread(() -> callback.onSuccess(data));
                } catch (Exception e) {
                    runOnUiThread(() -> callback.onError(context.getString(R.string.hq_error_json_parse, e.getMessage())));
                }
            }
        });
    }

    private static void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}