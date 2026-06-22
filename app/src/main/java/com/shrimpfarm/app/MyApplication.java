package com.shrimpfarm.app;

import android.app.Activity;
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
import com.shrimpfarm.app.checkfeed.CheckFeedActivity;
import com.shrimpfarm.app.mixcalc.MixCalcActivity;

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

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                int statusBarColor = getStatusBarColor(activity);
                boolean lightIcons = isLightStatusBarIcons(activity);

                if (Build.VERSION.SDK_INT >= 35) {
                    WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
                    activity.getWindow().setStatusBarColor(0);
                    activity.getWindow().setNavigationBarColor(0);
                    ViewCompat.setOnApplyWindowInsetsListener(
                            activity.getWindow().getDecorView(), (v, insets) -> {
                        int statusBarHeight = insets.getInsets(
                                WindowInsetsCompat.Type.statusBars()).top;
                        int navBarHeight = insets.getInsets(
                                WindowInsetsCompat.Type.navigationBars()).bottom;
                        View content = v.findViewById(android.R.id.content);
                        if (content != null) {
                            content.setBackgroundColor(statusBarColor);
                            content.setPadding(
                                    content.getPaddingLeft(),
                                    statusBarHeight,
                                    content.getPaddingRight(),
                                    navBarHeight
                            );
                        }
                        return insets;
                    });
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
            public void onActivityResumed(Activity activity) {}

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
