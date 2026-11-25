package com.shrimpfarm.app.hq;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.MarketSorter;
import com.shrimpfarm.app.model.PriceCategory;
import com.shrimpfarm.app.model.PriceData;
import com.shrimpfarm.app.model.PriceItem;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 从 Supabase「水产行情」库读取无锡市场每日行情。
 *
 * 只使用 publishable(anon) key —— 该 key 在库里被授予 price_records / species /
 * species_specs 的只读权限，不含任何写权限，可以安全放在 App 里。
 * service_role / 个人访问令牌绝不出现在这里。
 */
public final class HqSupabaseClient {

    private static final String TAG = "HqSupabase";

    private static final String HOST = "mqvurkpfmesfbxdccali.supabase.co";
    private static final String BASE = "https://" + HOST + "/rest/v1";
    private static final String KEY = "sb_publishable_yOUQGk94LsFtFd8iOxKpzA_R5pfKkPU";

    /** 统一以无锡市场身份读写，不再区分地区。 */
    private static final int REGION_ID = 10;
    static final String REGION_NAME = "无锡";

    /** 无规格行在字典里的名字，展示时省略。 */
    private static final String DEFAULT_SPEC = "常规";

    private static final int MAX_RETRIES = 2;

    /**
     * DNS 污染兜底：某些网络环境下 *.supabase.co 解析不到，此时改用已知 Cloudflare IP。
     * URL 仍是域名，因此 TLS SNI / 证书校验保持不变，与 curl --resolve 等价。
     */
    private static final String[] FALLBACK_IPS = {"104.18.38.10", "172.64.149.246"};

    private static OkHttpClient client;

    public interface OnDataCallback {
        void onSuccess(PriceData data);

        void onError(String msg);
    }

    private HqSupabaseClient() {}

    private static OkHttpClient getClient() {
        if (client == null) {
            Dns dns = hostname -> {
                if (!HOST.equals(hostname)) return Dns.SYSTEM.lookup(hostname);

                // 系统解析结果优先；无论成功与否都追加已知 IP 作为兜底。
                // OkHttp 会依次尝试列表中的地址，因此 DNS 正常时不受影响，
                // 解析被污染/失败时仍连得上。
                List<InetAddress> result = new ArrayList<>();
                try {
                    List<InetAddress> addrs = Dns.SYSTEM.lookup(hostname);
                    if (addrs != null) result.addAll(addrs);
                } catch (Exception ignored) {
                    // 落到兜底 IP
                }
                for (String ip : FALLBACK_IPS) {
                    try {
                        InetAddress addr = InetAddress.getByName(ip);
                        if (!result.contains(addr)) result.add(addr);
                    } catch (UnknownHostException ignored) {
                        // 字面量 IP 不会失败，仅作保护
                    }
                }
                if (!result.isEmpty()) return result;
                throw new UnknownHostException(hostname);
            };
            client = new OkHttpClient.Builder()
                    .dns(dns)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .writeTimeout(25, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }

    /** 异步获取，回调已在主线程。保持与旧 HqCrawler 相同的回调签名。 */
    public static void fetchLatest(final Context context, final OnDataCallback callback) {
        new Thread(() -> {
            Exception lastError = null;
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    final PriceData data = fetchSync();
                    runOnUiThread(() -> {
                        if (data != null && data.categories != null && !data.categories.isEmpty()) {
                            callback.onSuccess(data);
                        } else {
                            callback.onError(context.getString(R.string.hq_error_no_data));
                        }
                    });
                    return;
                } catch (Exception e) {
                    lastError = e;
                    Log.w(TAG, "获取行情失败，第 " + (attempt + 1) + " 次", e);
                    if (attempt < MAX_RETRIES - 1) {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            final String detail = lastError != null ? String.valueOf(lastError.getMessage()) : "unknown";
            runOnUiThread(() -> callback.onError(context.getString(R.string.hq_error_supabase, detail)));
        }, "hq-supabase-fetch").start();
    }

    /** 品种字典项，保留 sort 顺序用于分组。 */
    private static final class SpeciesInfo {
        final int id;
        final String name;
        final String category;

        SpeciesInfo(int id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }
    }

    private static List<SpeciesInfo> speciesList;
    private static Map<Integer, String> speciesNames;
    private static Map<Integer, String> speciesCategories;
    private static Map<Integer, String> specNames;

    /**
     * 同步获取最新一天的无锡行情。供后台 WorkManager 调用。
     * 步骤：查最新日期 → 查当天记录 → 组装。
     */
    public static PriceData fetchSync() throws IOException {
        String date = fetchLatestDate();
        if (date == null) return null;
        return fetchByDateSync(date);
    }

    /** 最新一条记录的日期，没有数据时返回 null。 */
    public static String fetchLatestDate() throws IOException {
        JsonArray dateRows = queryArray("price_records?select=record_date&region_id=eq." + REGION_ID
                + "&order=record_date.desc&limit=1");
        if (dateRows.size() == 0) return null;
        String date = getStr(dateRows.get(0).getAsJsonObject(), "record_date");
        return (date == null || date.isEmpty()) ? null : date;
    }

    /**
     * 查询指定区间内有行情记录的日期（升序、去重），供本地补缺使用。
     * 只取日期列，不拉价格详情，网络开销很小。
     */
    public static List<String> fetchRemoteDates(String fromDate, String toDate) throws IOException {
        if (fromDate == null || fromDate.isEmpty() || toDate == null || toDate.isEmpty()) {
            return Collections.emptyList();
        }
        String path = "price_records?select=record_date&region_id=eq." + REGION_ID
                + "&record_date=gte." + encode(fromDate)
                + "&record_date=lte." + encode(toDate)
                + "&order=record_date.asc&limit=1000";
        Set<String> dates = new TreeSet<>();
        for (JsonElement el : queryArray(path)) {
            String date = getStr(el.getAsJsonObject(), "record_date");
            if (!date.isEmpty()) dates.add(date);
        }
        return new ArrayList<>(dates);
    }

    /** 同步获取指定日期的无锡行情；该日没有记录时返回 null。 */
    public static PriceData fetchByDateSync(String date) throws IOException {
        if (date == null || date.isEmpty()) return null;

        JsonArray records = queryArray("price_records?select=species_id,spec_id,price&record_date=eq."
                + encode(date) + "&region_id=eq." + REGION_ID + "&limit=1000");
        if (records.size() == 0) return null;

        ensureDictionaries();

        // 品种 id -> 当天的所有规格行
        Map<Integer, List<PriceItem>> bySpecies = new HashMap<>();
        for (JsonElement el : records) {
            JsonObject o = el.getAsJsonObject();
            int sid = getInt(o, "species_id");
            String name = speciesNames.get(sid);
            if (name == null || name.isEmpty()) continue;

            String spec = specNames.get(getInt(o, "spec_id"));
            if (spec != null && !spec.isEmpty() && !DEFAULT_SPEC.equals(spec)) {
                name = name + " · " + spec;
            }
            String price = getStr(o, "price");
            if (price == null) price = "";

            List<PriceItem> items = bySpecies.get(sid);
            if (items == null) {
                items = new ArrayList<>();
                bySpecies.put(sid, items);
            }
            items.add(new PriceItem(name, price));
        }

        // 按品种字典顺序分组，界面顺序稳定
        LinkedHashMap<String, PriceCategory> grouped = new LinkedHashMap<>();
        for (SpeciesInfo s : speciesList) {
            List<PriceItem> items = bySpecies.get(s.id);
            if (items == null || items.isEmpty()) continue;

            // 同一品种内按规格排：2两 < 2.5两 < 3两，大 > 中 > 小。
            MarketSorter.sortItems(items);

            String category = speciesCategories.get(s.id);
            if (category == null || category.isEmpty()) category = "其他";
            PriceCategory cat = grouped.get(category);
            if (cat == null) {
                cat = new PriceCategory();
                cat.title = category;
                cat.items = new ArrayList<>();
                grouped.put(category, cat);
            }
            cat.items.addAll(items);
        }

        if (grouped.isEmpty()) return null;

        List<PriceCategory> categories = new ArrayList<>(grouped.values());
        MarketSorter.sortCategories(categories);
        return new PriceData(date, categories);
    }

    /** 按日期异步拉取，回调在主线程；本地没有该日数据时使用。 */
    public static void fetchByDate(final Context context, final String date,
                                   final OnDataCallback callback) {
        new Thread(() -> {
            try {
                final PriceData data = fetchByDateSync(date);
                runOnUiThread(() -> {
                    if (data != null && data.categories != null && !data.categories.isEmpty()) {
                        callback.onSuccess(data);
                    } else {
                        // msg 为 null 表示「该日没有数据」，非 null 表示网络/服务异常。
                        callback.onError(null);
                    }
                });
            } catch (Exception e) {
                final String detail = String.valueOf(e.getMessage());
                runOnUiThread(() -> callback.onError(context.getString(R.string.hq_error_supabase, detail)));
            }
        }, "hq-fetch-date").start();
    }

    /** 品种/规格字典很少变化，进程内缓存一次，翻页时不再重复请求。 */
    private static synchronized void ensureDictionaries() throws IOException {
        if (speciesList != null && speciesNames != null
                && speciesCategories != null && specNames != null) {
            return;
        }

        JsonArray species = queryArray("species?select=id,name,category&order=sort&limit=1000");
        JsonArray specs = queryArray("species_specs?select=id,name&limit=1000");

        Map<Integer, String> names = new HashMap<>();
        for (JsonElement el : specs) {
            JsonObject o = el.getAsJsonObject();
            names.put(getInt(o, "id"), getStr(o, "name"));
        }

        List<SpeciesInfo> list = new ArrayList<>();
        Map<Integer, String> speciesNameMap = new HashMap<>();
        Map<Integer, String> categoryMap = new HashMap<>();
        for (JsonElement el : species) {
            JsonObject o = el.getAsJsonObject();
            int id = getInt(o, "id");
            String name = getStr(o, "name");
            String category = getStr(o, "category");
            list.add(new SpeciesInfo(id, name, category));
            speciesNameMap.put(id, name);
            categoryMap.put(id, category);
        }

        specNames = names;
        speciesList = list;
        speciesNames = speciesNameMap;
        speciesCategories = categoryMap;
    }

    private static JsonArray queryArray(String path) throws IOException {
        Request request = new Request.Builder()
                .url(BASE + "/" + path)
                .header("apikey", KEY)
                .header("Authorization", "Bearer " + KEY)
                .header("Accept", "application/json")
                .build();
        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            JsonElement parsed = JsonParser.parseString(response.body().string());
            if (!parsed.isJsonArray()) {
                throw new IOException("返回格式异常");
            }
            return parsed.getAsJsonArray();
        }
    }

    private static String encode(String s) throws IOException {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    }

    private static int getInt(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) return 0;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getStr(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) return "";
        return el.getAsString();
    }

    private static void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}
