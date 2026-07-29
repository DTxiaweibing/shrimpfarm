package com.shrimpfarm.app;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.shrimpfarm.app.checkfeed.CheckFeedActivity;
import com.shrimpfarm.app.mixcalc.MixCalcActivity;
import com.shrimpfarm.app.utils.LocaleHelper;

public abstract class BaseActivity extends AppCompatActivity {

    protected TextView navHome, navRecord, navCheck, navMix, navMy;
    private GestureDetector gestureDetector;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
    private static final int SWIPE_THRESHOLD = 200;
    private static final int SWIPE_VELOCITY_THRESHOLD = 200;
    private Handler integrityHandler;
    private boolean integrityDialogShown = false;
    private boolean watermarkApplied = false;

    @Override
    public void setContentView(int layoutResID) {
        View original = getLayoutInflater().inflate(layoutResID, null);
        wrapWithWatermark(original);
    }

    @Override
    public void setContentView(View view) {
        wrapWithWatermark(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        wrapWithWatermark(view);
    }

    private void wrapWithWatermark(View content) {
        if (content instanceof WatermarkFrameLayout) {
            super.setContentView(content);
            return;
        }
        WatermarkFrameLayout wfl = new WatermarkFrameLayout(this);
        wfl.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        super.setContentView(wfl);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppIntegrityChecker.verified) {
            showPiratedBlockingDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppIntegrityChecker.startCheck(this);
        if (!AppIntegrityChecker.verified && !integrityDialogShown) {
            showPiratedBlockingDialog();
            return;
        }
        integrityHandler = new Handler(Looper.getMainLooper());
        integrityHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || integrityDialogShown) return;
                if (!AppIntegrityChecker.verified) {
                    showPiratedBlockingDialog();
                } else {
                    integrityHandler.postDelayed(this, 800);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (integrityHandler != null) {
            integrityHandler.removeCallbacksAndMessages(null);
        }
    }

    @SuppressLint("InflateParams")
    protected void showPiratedBlockingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d, keyCode, event) -> keyCode == android.view.KeyEvent.KEYCODE_BACK);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFFFFFFFF);
        layout.setPadding(0, 0, 0, 0);

        TextView tv = new TextView(this);
        tv.setText(getString(R.string.base_dialog_non_genuine));
        tv.setTextSize(18);
        tv.setTextColor(0xFF333333);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(48, 48, 48, 48);
        layout.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View divider = new View(this);
        divider.setBackgroundColor(0xFFE0E0E0);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        Button btn = new Button(this);
        btn.setText(getString(R.string.base_btn_exit));
        btn.setTextSize(16);
        btn.setBackgroundResource(android.R.color.transparent);
        btn.setTextColor(0xFFE53935);
        btn.setOnClickListener(v -> {
            integrityDialogShown = true;
            dialog.dismiss();
            finishAffinity();
            System.exit(0);
        });
        layout.addView(divider);
        layout.addView(btn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (48 * getResources().getDisplayMetrics().density + 0.5f)));

        dialog.setContentView(layout);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(android.view.Gravity.CENTER);
            window.setDimAmount(0.6f);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (!isFinishing() && !integrityDialogShown) {
            integrityDialogShown = true;
            dialog.show();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    protected void enableSwipeNavigation() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (isTouchOnSwipeBlockedView(e1.getX(), e1.getY())) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        swipeToPage(getCurrentPosition() - 1, true);
                    } else {
                        swipeToPage(getCurrentPosition() + 1, false);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private int getCurrentPosition() {
        int id = getCurrentNavId();
        if (id == R.id.nav_home) return 0;
        if (id == R.id.nav_record) return 1;
        if (id == R.id.nav_check) return 2;
        if (id == R.id.nav_mix) return 3;
        if (id == R.id.nav_my) return 4;
        return -1;
    }

    private void swipeToPage(int targetPos, boolean swipeRight) {
        if (targetPos < 0 || targetPos > 4) return;
        Class<?> cls;
        switch (targetPos) {
            case 0: cls = MainActivity.class; break;
            case 1: cls = FeedingRecordActivity.class; break;
            case 2: cls = CheckFeedActivity.class; break;
            case 3: cls = MixCalcActivity.class; break;
            default: cls = ProfileActivity.class; break;
        }
        if (cls.equals(getClass())) return;
        startActivity(new Intent(this, cls));
        if (swipeRight) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        } else {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
        finish();
    }

    protected boolean isTouchOnSwipeBlockedView(float x, float y) {
        return false;
    }

    protected void setupBottomNavigation() {
        navHome = findViewById(R.id.nav_home);
        navRecord = findViewById(R.id.nav_record);
        navCheck = findViewById(R.id.nav_check);
        navMix = findViewById(R.id.nav_mix);
        navMy = findViewById(R.id.nav_my);

        View.OnClickListener navListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.nav_home) {
                    if (!(BaseActivity.this instanceof MainActivity)) {
                        startActivity(new Intent(BaseActivity.this, MainActivity.class));
                        finish();
                    }
                } else if (id == R.id.nav_record) {
                    if (!(BaseActivity.this instanceof FeedingRecordActivity)) {
                        startActivity(new Intent(BaseActivity.this, FeedingRecordActivity.class));
                        if (!(BaseActivity.this instanceof MainActivity)) finish();
                    }
                } else if (id == R.id.nav_check) {
                    if (!(BaseActivity.this instanceof CheckFeedActivity)) {
                        startActivity(new Intent(BaseActivity.this, CheckFeedActivity.class));
                        if (!(BaseActivity.this instanceof MainActivity)) finish();
                    }
                } else if (id == R.id.nav_mix) {
                    if (!(BaseActivity.this instanceof com.shrimpfarm.app.mixcalc.MixCalcActivity)) {
                        startActivity(new Intent(BaseActivity.this, com.shrimpfarm.app.mixcalc.MixCalcActivity.class));
                        if (!(BaseActivity.this instanceof MainActivity)) finish();
                    }
                
                } else if (id == R.id.nav_my) {
                    if (!(BaseActivity.this instanceof ProfileActivity)) {
                        startActivity(new Intent(BaseActivity.this, ProfileActivity.class));
                        if (!(BaseActivity.this instanceof MainActivity)) finish();
                    }
                }
                updateBottomNavHighlight(id);
            }
        };

        navHome.setOnClickListener(navListener);
        navRecord.setOnClickListener(navListener);
        navCheck.setOnClickListener(navListener);
        navMix.setOnClickListener(navListener);
        navMy.setOnClickListener(navListener);

        int currentId = getCurrentNavId();
        updateBottomNavHighlight(currentId);
    }

    protected void showStyledConfirmDialog(String title, String message, String[] buttonTexts, int[] buttonColors, DialogInterface.OnClickListener[] listeners) {
        showStyledConfirmDialog(title, message, buttonTexts, buttonColors, listeners, true);
    }

    @android.annotation.SuppressLint("InflateParams")
    protected void showStyledConfirmDialog(String title, String message, String[] buttonTexts, int[] buttonColors, DialogInterface.OnClickListener[] listeners, boolean cancelable) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_confirm, null);
        dialog.setContentView(dialogView);

        dialog.setCanceledOnTouchOutside(false);
        if (!cancelable) dialog.setCancelable(false);
        dialog.setOnCancelListener(d -> {
            if (listeners != null && listeners.length > 0 && listeners[0] != null) {
                listeners[0].onClick(d, 0);
            }
        });

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(this);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(buttonColors != null && i < buttonColors.length ? buttonColors[i] : 0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    protected abstract int getCurrentNavId();

    protected void updateBottomNavHighlight(int selectedId) {
        if (navHome != null) navHome.setTextColor(selectedId == R.id.nav_home ? 0xFF4CAF50 : 0xFF757575);
        if (navRecord != null) navRecord.setTextColor(selectedId == R.id.nav_record ? 0xFF4CAF50 : 0xFF757575);
        if (navCheck != null) navCheck.setTextColor(selectedId == R.id.nav_check ? 0xFF4CAF50 : 0xFF757575);
        if (navMix != null) navMix.setTextColor(selectedId == R.id.nav_mix ? 0xFF4CAF50 : 0xFF757575);
        if (navMy != null) navMy.setTextColor(selectedId == R.id.nav_my ? 0xFF4CAF50 : 0xFF757575);
    }
}
