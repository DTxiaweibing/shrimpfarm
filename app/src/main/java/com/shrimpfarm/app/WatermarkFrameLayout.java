package com.shrimpfarm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class WatermarkFrameLayout extends FrameLayout {

    private static final int TRIGGER_DAYS = 50;
    private static final int WATERMARK_COLOR = 0x12FF0000;
    private static final float WATERMARK_TEXT_SIZE = 36;
    private static final float WATERMARK_LINE_SPACING = 200;

    private Paint watermarkPaint;
    private Bitmap watermarkBitmap;
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
            watermarkPaint.setTextSize(dp(WATERMARK_TEXT_SIZE));
            watermarkPaint.setColor(WATERMARK_COLOR);
            watermarkPaint.setStyle(Paint.Style.FILL);
            watermarkPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (showWatermark && w > 0 && h > 0) {
            createWatermarkBitmap(w, h);
        }
    }

    private void createWatermarkBitmap(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap((int) WATERMARK_LINE_SPACING, h, Bitmap.Config.ARGB_4444);
        Canvas c = new Canvas(bmp);
        float textY = -dp(40);
        while (textY < h + dp(100)) {
            c.save();
            c.rotate(-45, WATERMARK_LINE_SPACING / 2f, textY);
            c.drawText("非官方正版", dp(8), textY, watermarkPaint);
            c.restore();
            textY += dp(WATERMARK_LINE_SPACING);
        }
        watermarkPaint.setShader(new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        bmp.recycle();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (showWatermark) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), watermarkPaint);
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

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density + 0.5f;
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
        int days = calculateDaysSinceStocking(stockingDate);
        return days >= TRIGGER_DAYS;
    }

    private static int calculateDaysSinceStocking(String stockingDate) {
        String[] formats = {"yyyy/MM/dd", "yyyy-MM-dd", "yyyy.M.d"};
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.CHINA);
                Date parsedDate = sdf.parse(stockingDate);
                if (parsedDate == null) continue;
                Calendar stockCal = Calendar.getInstance();
                stockCal.setTime(parsedDate);
                Calendar now = Calendar.getInstance();
                long diff = now.getTimeInMillis() - stockCal.getTimeInMillis();
                return (int) (diff / (1000 * 60 * 60 * 24));
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
