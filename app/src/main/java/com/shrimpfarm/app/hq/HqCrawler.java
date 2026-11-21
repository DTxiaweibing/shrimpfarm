package com.shrimpfarm.app.hq;

import android.content.Context;

/**
 * 兼容旧调用的入口。
 * 数据源已从 GitHub 上的 price.json 改为 Supabase「水产行情」库（固定无锡市场），
 * 实际逻辑见 {@link HqSupabaseClient}。
 */
public final class HqCrawler {

    public interface OnDataCallback extends HqSupabaseClient.OnDataCallback {
    }

    private HqCrawler() {
    }

    public static void fetchLatest(Context context, OnDataCallback callback) {
        HqSupabaseClient.fetchLatest(context, callback);
    }
}
