package com.shrimpfarm.app.hq;

import android.util.Log;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.model.PriceData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 模块打开时把远端有、本地无的行情补齐。
 *
 * <p>请求并行、落库串行：串行请求最多 365 天会把绿点快照拖在同步后面，
 * 落库保持单线程避免多个写事务互相争锁。</p>
 */
public final class HqBackfill {

    private static final String TAG = "HqBackfill";
    private static final int THREADS = 6;

    private HqBackfill() {
    }

    /** 返回真正写入本地库的日期，供调用方刷新页面和统计。 */
    public static List<String> run(DatabaseHelper db, String fromDate, String today,
                                   int windowDays) throws IOException {
        List<String> remote = HqSupabaseClient.fetchRemoteDates(fromDate, today);
        List<String> missing = HqSyncPlanner.missingDates(
                today, db.getMarketPriceDates(), remote, windowDays);
        return saveAll(db, fetchAll(missing));
    }

    private static List<PriceData> fetchAll(List<String> missing) {
        if (missing.isEmpty()) return Collections.emptyList();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(THREADS, missing.size()));
        try {
            List<Future<PriceData>> futures = new ArrayList<>(missing.size());
            for (String date : missing) {
                futures.add(pool.submit(new FetchTask(date)));
            }
            List<PriceData> fetched = new ArrayList<>(missing.size());
            for (Future<PriceData> future : futures) {
                try {
                    PriceData data = future.get();
                    if (data != null && data.categories != null && !data.categories.isEmpty()) {
                        fetched.add(data);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "并行拉取单日失败: " + e.getMessage());
                }
            }
            return fetched;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> saveAll(DatabaseHelper db, List<PriceData> fetched) {
        List<String> saved = new ArrayList<>(fetched.size());
        for (PriceData data : fetched) {
            try {
                db.saveMarketPrices(data);
                saved.add(data.date);
            } catch (Exception e) {
                Log.w(TAG, "落库 " + data.date + " 失败: " + e.getMessage());
            }
        }
        return saved;
    }

    private static final class FetchTask implements Callable<PriceData> {
        private final String date;

        FetchTask(String date) {
            this.date = date;
        }

        @Override
        public PriceData call() throws IOException {
            return HqSupabaseClient.fetchByDateSync(date);
        }
    }
}
