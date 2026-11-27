package com.shrimpfarm.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.multidex.MultiDexApplication;

import com.shrimpfarm.app.analysis.DataAnalysisActivity;
import com.shrimpfarm.app.backup.BackgroundBackupManager;
import com.shrimpfarm.app.backup.V17BackupHook;
import com.shrimpfarm.app.checkfeed.CheckFeedActivity;
import com.shrimpfarm.app.mixcalc.MixCalcActivity;
import com.shrimpfarm.app.model.KnowledgeBaseUpdater;

public class MyApplication extends MultiDexApplication {

    private int activityCount = 0;

    private int getStatusBarColor(Activity activity) {
        if (activity instanceof BatchManageActivity || activity instanceof CheckFeedActivity
                || activity instanceof MixCalcActivity || activity instanceof BasicDataActivity
                || activity instanceof DataAnalysisActivity || activity instanceof FeedingRecordActivity
                || activity instanceof ProfileActivity) {
            return 0xFF004D40;
        } else if (activity instanceof CrashReportActivity) {
            return 0xFFBF360C;
        } else {
            return 0xFFFFFFFF;
        }
    }

    private boolean isLightStatusBarIcons(Activity activity) {
        return !(activity instanceof BatchManageActivity || activity instanceof CheckFeedActivity
                || activity instanceof MixCalcActivity || activity instanceof BasicDataActivity
                || activity instanceof DataAnalysisActivity || activity instanceof FeedingRecordActivity
                || activity instanceof ProfileActivity || activity instanceof CrashReportActivity);
    }

    private void applyStatusBarIcons(Activity activity, boolean lightIcons) {
        if (!lightIcons) return;
        View decorView = activity.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 33) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            decorView.setSystemUiVisibility(
                    decorView.getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** 系统状态栏高度：优先取 insets，取不到时退回系统资源（华为兼容层可能返回空 insets）。 */
    private static int statusBarHeightOf(Activity activity, int insetTop) {
        if (insetTop > 0) return insetTop;
        int resId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                return activity.getResources().getDimensionPixelSize(resId);
            } catch (Throwable ignored) { /* ignored */ }
        }
        return (int) (24 * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 底部导航栏高度：优先取 insets，取不到时退回系统资源。 */
    private static int navBarHeightOf(Activity activity, int insetBottom) {
        if (insetBottom > 0) return insetBottom;
        int resId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                return activity.getResources().getDimensionPixelSize(resId);
            } catch (Throwable ignored) { /* ignored */ }
        }
        return (int) (48 * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 从 root window insets 取一次系统栏高度，仅在 content 还没被别的路径加过 padding 时补上。
     * 用于个别 ROM 不把 insets 派发给 decor 监听器的情况（否则页面一直"边到边"）。
     */
    private static void applyInsetsFallbackIfNeeded(Activity activity) {
        if (activity instanceof MixCalcActivity) return;
        if (Build.VERSION.SDK_INT < 35) return;
        try {
            View decor = activity.getWindow().getDecorView();
            View content = decor.findViewById(android.R.id.content);
            if (content == null) return;
            WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(decor);
            int top = 0;
            int bottom = 0;
            if (wi != null) {
                top = wi.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                bottom = Math.max(
                        wi.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                        wi.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            }
            top = statusBarHeightOf(activity, top);
            bottom = navBarHeightOf(activity, bottom);
            if ((top > 0 || bottom > 0)
                    && content.getPaddingTop() == 0
                    && content.getPaddingBottom() == 0) {
                content.setPadding(content.getPaddingLeft(), top,
                        content.getPaddingRight(), bottom);
                if (content.getBackground() instanceof SystemBarBackgroundDrawable) {
                    ((SystemBarBackgroundDrawable) content.getBackground()).setInsets(top, bottom);
                }
                android.util.Log.i("MyApplication", "insets fallback applied: top=" + top + " bottom=" + bottom);
            }
        } catch (Throwable t) {
            android.util.Log.w("MyApplication", "insets fallback failed", t);
        }
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.fontScale = 1.0f;
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        V17BackupHook.run(this);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));

        KnowledgeBaseUpdater.startBackgroundPolling(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                int statusBarColor = getStatusBarColor(activity);
                boolean lightIcons = isLightStatusBarIcons(activity);

                // MixCalcActivity 自己处理 insets（spacer），不要重复加 padding。
                // 但系统导航栏的颜色/图标外观仍需与其他页面保持一致。
                if (activity instanceof MixCalcActivity) {
                    applyStatusBarIcons(activity, lightIcons);
                    if (Build.VERSION.SDK_INT >= 35) {
                        try {
                            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                        } catch (Throwable t) {
                            android.util.Log.w("MyApplication", "setDecorFitsSystemWindows failed", t);
                        }
                        try {
                            activity.getWindow().setStatusBarColor(0);
                            activity.getWindow().setNavigationBarColor(0);
                        } catch (Throwable t) {
                            android.util.Log.w("MyApplication", "set system bar colors failed", t);
                        }
                        try {
                            WindowInsetsController controller = activity.getWindow().getInsetsController();
                            if (controller != null) {
                                controller.setSystemBarsAppearance(
                                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                            }
                        } catch (Throwable t) {
                            android.util.Log.w("MyApplication", "light nav bar icons failed", t);
                        }
                    }
                    return;
                }

                // Edge-to-edge：手动给 content 加 padding 避开状态栏/导航栏/输入法。
                // 关键：insets 监听器必须最先注册，且不受后面着色调用异常影响——
                // 否则华为等 ROM 上 getInsetsController()/配色一抛异常，padding 就没了，
                // 页面就会全部“边到边”（内容钻到系统栏底下）。
                if (Build.VERSION.SDK_INT >= 35) {
                    final SystemBarBackgroundDrawable bars =
                            new SystemBarBackgroundDrawable(statusBarColor, Color.WHITE);
                    // 与改动前（HEAD）完全一致：监听 decor、给 content 加 padding、不消费 insets。
                    // 只把原来单一的 statusBarColor 底色换成"上状态栏色 + 下导航栏白"的双色背景。
                    ViewCompat.setOnApplyWindowInsetsListener(
                            activity.getWindow().getDecorView(), (v, insets) -> {
                                try {
                                    int statusBarHeight = statusBarHeightOf(activity,
                                            insets.getInsets(WindowInsetsCompat.Type.statusBars()).top);
                                    int navBarHeight = insets.getInsets(
                                            WindowInsetsCompat.Type.navigationBars()).bottom;
                                    int imeHeight = insets.getInsets(
                                            WindowInsetsCompat.Type.ime()).bottom;
                                    int bottom = navBarHeightOf(activity, Math.max(navBarHeight, imeHeight));
                                    View content = v.findViewById(android.R.id.content);
                                    if (content != null) {
                                        if (content.getBackground() != bars) {
                                            content.setBackground(bars);
                                        }
                                        content.setPadding(
                                                content.getPaddingLeft(),
                                                statusBarHeight,
                                                content.getPaddingRight(),
                                                bottom
                                        );
                                        bars.setInsets(statusBarHeight, bottom);
                                    }
                                } catch (Throwable t) {
                                    android.util.Log.e("MyApplication", "apply insets padding failed", t);
                                }
                                return insets;
                            });

                    // 以下只是“外观”，各自兜底，任何一步失败都不影响上面的留白。
                    try {
                        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                    } catch (Throwable t) {
                        android.util.Log.w("MyApplication", "setDecorFitsSystemWindows failed", t);
                    }
                    try {
                        activity.getWindow().setStatusBarColor(0);
                        activity.getWindow().setNavigationBarColor(0);
                    } catch (Throwable t) {
                        android.util.Log.w("MyApplication", "set system bar colors failed", t);
                    }
                    try {
                        WindowInsetsController controller = activity.getWindow().getInsetsController();
                        if (controller != null) {
                            controller.setSystemBarsAppearance(
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                        }
                    } catch (Throwable t) {
                        android.util.Log.w("MyApplication", "light nav bar icons failed", t);
                    }
                    // 确保首帧就派发一次 insets，兜底某些 ROM 不主动回调。
                    try {
                        ViewCompat.requestApplyInsets(activity.getWindow().getDecorView());
                    } catch (Throwable ignored) { /* ignored */ }
                    // 首帧布局后再补一次：若监听器一次都没回调，至少这里有值。
                    try {
                        activity.getWindow().getDecorView().post(
                                () -> applyInsetsFallbackIfNeeded(activity));
                    } catch (Throwable ignored) { /* ignored */ }
                } else {
                    activity.getWindow().setStatusBarColor(statusBarColor);
                }
                applyStatusBarIcons(activity, lightIcons);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                activityCount++;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                // 兜底：个别 ROM（含华为兼容环境）不把 insets 派发给 decor 的监听器，
                // 导致页面一直“边到边”。resumed 时再取一次 root window insets，
                // 只在 padding 仍为 0 时补上，不与监听器打架。
                applyInsetsFallbackIfNeeded(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {
                activityCount--;
                if (activityCount <= 0) {
                    BackgroundBackupManager.onAppBackground(MyApplication.this.getApplicationContext());
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }
}