package com.shrimpfarm.app.utils;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

public class HttpClientSingleton {
    private static OkHttpClient instance;

    private HttpClientSingleton() {}

    public static synchronized OkHttpClient getInstance() {
        if (instance == null) {
            instance = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return instance;
    }
}