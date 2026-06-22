package com.shrimpfarm.app;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.shrimpfarm.app.checkfeed.CheckFeedActivity;
import com.shrimpfarm.app.mixcalc.MixCalcActivity;

public abstract class BaseActivity extends AppCompatActivity {

    protected TextView navHome, navRecord, navCheck, navMix, navMy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
