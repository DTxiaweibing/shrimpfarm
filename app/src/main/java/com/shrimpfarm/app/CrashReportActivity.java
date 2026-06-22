package com.shrimpfarm.app;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CrashReportActivity extends AppCompatActivity {

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
        tvLog.setText(log != null ? log : "未找到崩溃日志文件");

        btnShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, tvLog.getText().toString());
            startActivity(Intent.createChooser(share, "分享崩溃日志"));
        });

        btnClear.setOnClickListener(v -> {
            CrashHandler.clearCrashLogs(this);
            Toast.makeText(this, "崩溃日志已清除", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
