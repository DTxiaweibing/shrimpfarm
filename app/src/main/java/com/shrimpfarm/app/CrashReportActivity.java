package com.shrimpfarm.app;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.shrimpfarm.app.BaseActivity;

public class CrashReportActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_report);

        TextView tvLog = findViewById(R.id.tv_crash_log);
        Button btnShare = findViewById(R.id.btn_share);
        Button btnClear = findViewById(R.id.btn_clear);

        GradientDrawable shareBg = new GradientDrawable();
        shareBg.setCornerRadius(8);
        shareBg.setColor(0xFFD84315);
        btnShare.setBackground(shareBg);

        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setCornerRadius(8);
        clearBg.setColor(0xFF757575);
        btnClear.setBackground(clearBg);

        String log = CrashHandler.getLatestCrashLog(this);
        tvLog.setText(log != null ? log : getString(R.string.crash_no_log_found));

        btnShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, tvLog.getText().toString());
            startActivity(Intent.createChooser(share, getString(R.string.crash_share_title)));
        });

        btnClear.setOnClickListener(v -> {
            CrashHandler.clearCrashLogs(this);
            Toast.makeText(this, getString(R.string.crash_log_cleared), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
