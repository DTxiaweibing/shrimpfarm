package com.shrimpfarm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public class WatermarkFrameLayout extends FrameLayout {

    private Paint watermarkPaint;
    private boolean showWatermark = false;
    private boolean buttonAdded = false;
    private TextView upgradeBtn;

    public WatermarkFrameLayout(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        showWatermark = isOver50Days(getContext());
        if (showWatermark) {
            watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            watermarkPaint.setColor(0x12FF0000);
            watermarkPaint.setStyle(Paint.Style.FILL);
            watermarkPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (showWatermark) {
            try {
                WatermarkNative.renderWatermark(canvas, getWidth(), getHeight(), watermarkPaint);
            } catch (UnsatisfiedLinkError e) {
                // fallback silently if .so not loaded
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (showWatermark && !buttonAdded && getChildCount() > 0) {
            buttonAdded = true;
            upgradeBtn = new TextView(getContext());
            upgradeBtn.setText("升级正版");
            upgradeBtn.setTextSize(13);
            upgradeBtn.setTextColor(0xFFFFFFFF);
            upgradeBtn.setPadding(dpi(12), dpi(6), dpi(12), dpi(6));
            upgradeBtn.setBackgroundColor(0xCCE53935);
            upgradeBtn.setGravity(Gravity.CENTER);
            upgradeBtn.setVisibility(View.GONE);
            upgradeBtn.setOnClickListener(v -> openUpgradePage());
            LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM | Gravity.START;
            lp.bottomMargin = dpi(24);
            lp.leftMargin = dpi(12);
            addView(upgradeBtn, lp);
            postDelayed(() -> { if (upgradeBtn != null) upgradeBtn.setVisibility(View.VISIBLE); }, 3000);
        }
    }

    private static final String UPGRADE_URL =
            "https://gh-proxy.com/https://github.com/DTxiaweibing/TIMU/releases/latest/download/app-release.apk";

    private void openUpgradePage() {
        Context ctx = getContext();
        ctx.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(UPGRADE_URL)));
    }

    private int dpi(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    public static boolean isOver50Days(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String batchId = prefs.getString("current_batch_id", "");
        if (batchId.isEmpty()) return false;
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        String stockingDate = db.getBasicData(batchId, "stocking_date");
        if (stockingDate == null || stockingDate.isEmpty() || "选择日期".equals(stockingDate)) return false;
        try {
            return WatermarkNative.shouldShowWatermark(stockingDate);
        } catch (UnsatisfiedLinkError e) {
            return dateFallback(stockingDate);
        }
    }

    private static boolean dateFallback(String stockingDate) {
        String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
        for (String fmt : formats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(fmt, java.util.Locale.CHINA);
                java.util.Date parsed = sdf.parse(stockingDate);
                if (parsed == null) continue;
                java.util.Calendar stock = java.util.Calendar.getInstance();
                stock.setTime(parsed);
                java.util.Calendar now = java.util.Calendar.getInstance();
                long diff = now.getTimeInMillis() - stock.getTimeInMillis();
                return (int) (diff / (1000 * 60 * 60 * 24)) >= 50;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
