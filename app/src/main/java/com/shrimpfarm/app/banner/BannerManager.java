package com.shrimpfarm.app.banner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shrimpfarm.app.BannerPageActivity;
import com.shrimpfarm.app.model.BannerItem;
import com.shrimpfarm.app.utils.HttpClientSingleton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BannerManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREFS_BANNER_JSON_CACHE = "cached_banner_json";
    private static final String PREFS_BANNER_READY = "banner_ready";

    private final Context context;
    private final ViewPager viewPager;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] localImageRes;

    // 核心数据源
    private final List<BannerItem> currentItems = new ArrayList<>();
    private Runnable bannerRunnable;
    private boolean isDestroyed = false;

    public BannerManager(Context context, ViewPager viewPager, int[] localImageRes) {
        this.context = context.getApplicationContext();
        this.viewPager = viewPager;
        this.localImageRes = localImageRes;
        this.prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        // 设置唯一适配器
        viewPager.setAdapter(new BannerPagerAdapter());
        // 启动自动轮播
        startAutoScroll();

        // 监听缓存变化，实现自动更新
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    // ---------- 公开方法 ----------

    /**
     * 初始化轮播图
     */
    public void init() {
        boolean ready = prefs.getBoolean(PREFS_BANNER_READY, false);
        List<BannerItem> cachedItems = loadFromCache();

        if (ready && cachedItems != null && !cachedItems.isEmpty()) {
            // 上次已成功，直接应用
            applyNewData(cachedItems);
        } else {
            // 未就绪，先显示内置图，然后尝试预加载
            currentItems.clear();
            refreshAdapter();
            if (cachedItems != null && !cachedItems.isEmpty()) {
                tryPreloadAndApply(cachedItems);
            }
        }
    }

    /**
     * 主页面 onResume 时调用，用于兜底更新
     */
    public void onResume() {
        if (!prefs.getBoolean(PREFS_BANNER_READY, false)) {
            List<BannerItem> cached = loadFromCache();
            if (cached != null && !cached.isEmpty()) {
                tryPreloadAndApply(cached);
            }
        }
    }

    public void destroy() {
        isDestroyed = true;
        handler.removeCallbacks(bannerRunnable);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    // ---------- SharedPreferences 监听 ----------
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREFS_BANNER_JSON_CACHE.equals(key)) {
            List<BannerItem> items = loadFromCache();
            if (items != null && !items.isEmpty()) {
                tryPreloadAndApply(items);
            }
        }
    }

    // ---------- 内部核心逻辑 ----------

    private List<BannerItem> loadFromCache() {
        try {
            String json = prefs.getString(PREFS_BANNER_JSON_CACHE, "");
            if (json.isEmpty()) return null;
            return new Gson().fromJson(json, new TypeToken<List<BannerItem>>() {}.getType());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 预加载所有图片和网页，成功后更新UI
     */
    private void tryPreloadAndApply(List<BannerItem> newItems) {
        new Thread(() -> {
            if (isDestroyed) return;

            // 1. 预加载全部图片到 Glide 磁盘缓存
            boolean allCached = preloadAllImages(newItems);
            if (!allCached) return;

            // 2. 下载所有链接网页到本地
            preloadAllPages(newItems);

            // 3. 切回主线程更新数据和界面
            handler.post(() -> {
                if (isDestroyed) return;
                applyNewData(newItems);
                // 4. 标记为"已成功显示过网络图"
                prefs.edit().putBoolean(PREFS_BANNER_READY, true).apply();
            });
        }).start();
    }

    private boolean preloadAllImages(List<BannerItem> items) {
        if (items == null || items.isEmpty()) return false;
        CountDownLatch latch = new CountDownLatch(items.size());
        AtomicBoolean allSuccess = new AtomicBoolean(true);

        for (BannerItem item : items) {
            Glide.with(context)
                    .load(item.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                    Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                    boolean isFirstResource) {
                            allSuccess.set(false);
                            latch.countDown();
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                                       com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                       com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            latch.countDown();
                            return false;
                        }
                    })
                    .preload();
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        return allSuccess.get();
    }

    private void preloadAllPages(List<BannerItem> items) {
        if (items == null || items.isEmpty()) return;
        OkHttpClient client = HttpClientSingleton.getInstance();
        File cacheDir = new File(context.getFilesDir(), "banner_pages");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        for (BannerItem item : items) {
            if (item.linkUrl == null || item.linkUrl.isEmpty()) continue;
            try {
                String fileName = getCacheFileName(item.linkUrl);
                File cachedFile = new File(cacheDir, fileName);
                if (cachedFile.exists() && cachedFile.length() > 0) continue;
                // 残文件删除重下
                if (cachedFile.exists()) cachedFile.delete();

                Request request = new Request.Builder().url(item.linkUrl).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(cachedFile)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    static String getCacheFileName(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return sb.toString() + ".html";
        } catch (Exception e) {
            return String.valueOf(url.hashCode()) + ".html";
        }
    }

    /**
     * 应用新数据，并通知适配器刷新（视图复用）
     */
    private void applyNewData(List<BannerItem> newItems) {
        currentItems.clear();
        currentItems.addAll(newItems);
        refreshAdapter();
    }

    /**
     * 通知适配器数据已更新，实现平滑刷新
     */
    private void refreshAdapter() {
        PagerAdapter adapter = viewPager.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        // 重置轮播
        startAutoScroll();
    }

    // ---------- 自动轮播 ----------
    private void startAutoScroll() {
        handler.removeCallbacks(bannerRunnable);

        bannerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDestroyed || viewPager == null) return;
                int next = viewPager.getCurrentItem() + 1;
                viewPager.setCurrentItem(next, true);
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(bannerRunnable, 3000);
    }

    // ---------- PagerAdapter（视图复用核心） ----------
    private class BannerPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            // 如果没有数据，显示内置图
            int size = currentItems.isEmpty() ? localImageRes.length : currentItems.size();
            return size == 0 ? 0 : Integer.MAX_VALUE;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            if (currentItems.isEmpty()) {
                // 显示内置图
                int index = position % localImageRes.length;
                iv.setImageResource(localImageRes[index]);
            } else {
                // 显示网络图
                int index = position % currentItems.size();
                BannerItem item = currentItems.get(index);
                Glide.with(context)
                        .load(item.imageUrl)
                        .placeholder(localImageRes[0])
                        .error(localImageRes[0])
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(iv);

                final String link = item.linkUrl;
                if (link != null && !link.isEmpty()) {
                    iv.setOnClickListener(v -> {
                        File cacheFile = new File(context.getFilesDir(), "banner_pages/" + getCacheFileName(link));
                        if (cacheFile.exists()) {
                            Intent intent = new Intent(context, BannerPageActivity.class);
                            intent.putExtra("file_path", cacheFile.getAbsolutePath());
                            intent.putExtra("base_url", link);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } else {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                    });
                }
            }
            container.addView(iv);
            return iv;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }
    }
}