package com.shrimpfarm.app;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ProfileActivity extends BaseActivity {

    private SupabaseAuthManager authManager;
    private LinearLayout layoutLoggedIn;
    private ScrollView layoutLogin;
    private TextView tvNickname, tvEmail, tvRecorderInfo, tvError, tvToggleMode;
    private EditText etEmail, etPassword, etNickname;
    private Button btnLogin;
    private boolean isRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authManager = new SupabaseAuthManager(this);

        layoutLoggedIn = findViewById(R.id.layout_logged_in);
        layoutLogin = findViewById(R.id.layout_login);
        tvNickname = findViewById(R.id.tv_nickname);
        tvEmail = findViewById(R.id.tv_email);
        tvRecorderInfo = findViewById(R.id.tv_recorder_info);
        tvToggleMode = findViewById(R.id.tv_toggle_mode);
        tvError = findViewById(R.id.tv_error);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etNickname = findViewById(R.id.et_nickname);
        btnLogin = findViewById(R.id.btn_login);
        Button btnLogout = findViewById(R.id.btn_logout);
        Button btnEditNickname = findViewById(R.id.btn_edit_nickname);

        TextView tvForgotPwd = findViewById(R.id.tv_forgot_pwd);

        tvToggleMode.setOnClickListener(v -> toggleMode());
        btnLogin.setOnClickListener(v -> doAuth());
        tvForgotPwd.setOnClickListener(v -> doForgotPassword());
        btnLogout.setOnClickListener(v -> {
            authManager.logout();
            updateUI();
        });
        btnEditNickname.setOnClickListener(v -> showEditNicknameDialog());

        setupBottomNavigation();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager.isLoggedIn()) {
            new Thread(() -> {
                String err = authManager.loadWebDavFromCloud();
                if (err == null) {
                    runOnUiThread(this::updateUI);
                }
            }).start();
        }
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_my;
    }

    private void updateUI() {
        if (authManager.isLoggedIn()) {
            layoutLoggedIn.setVisibility(View.VISIBLE);
            layoutLogin.setVisibility(View.GONE);
            tvNickname.setText(authManager.getNickname());
            tvEmail.setText(authManager.getEmail());
            tvRecorderInfo.setText(getString(R.string.recorder_info, authManager.getNickname()));
        } else {
            layoutLoggedIn.setVisibility(View.GONE);
            layoutLogin.setVisibility(View.VISIBLE);
            etNickname.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
            btnLogin.setText(isRegisterMode ? "注册" : "登录");
            tvToggleMode.setText(isRegisterMode ? "已有账号？点此登录" : "没有账号？点此注册");
            tvError.setVisibility(View.GONE);
        }
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        updateUI();
    }

    private void doAuth() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) { showError("请输入邮箱"); return; }
        if (password.isEmpty()) { showError("请输入密码"); return; }
        if (password.length() < 6) { showError("密码至少6位"); return; }

        final ProgressDialog pd = ProgressDialog.show(this, "", isRegisterMode ? "注册中..." : "登录中...", true);

        new Thread(() -> {
            final SupabaseAuthManager.AuthResult result;
            if (isRegisterMode) {
                String nickname = etNickname.getText().toString().trim();
                if (nickname.isEmpty()) { nickname = email.split("@")[0]; }
                result = authManager.register(email, password, nickname);
            } else {
                result = authManager.login(email, password);
            }

            runOnUiThread(() -> {
                pd.dismiss();
                if (result.success) {
                    tvError.setVisibility(View.GONE);
                    updateUI();
                } else {
                    showError(result.message);
                }
            });
        }).start();
    }

    private void showError(String msg) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private void doForgotPassword() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) { showError("请先输入邮箱"); return; }

        final ProgressDialog pd = ProgressDialog.show(this, "", "发送中...", true);
        new Thread(() -> {
            final String error = authManager.forgotPassword(email);
            runOnUiThread(() -> {
                pd.dismiss();
                if (error == null) {
                    showStyledConfirmDialog("已发送",
                            "密码重置链接已发送到 " + email + "，请登录邮箱查看并重置密码",
                            new String[]{"确定"}, null, null);
                } else {
                    showError(error);
                }
            });
        }).start();
    }

    private void showEditNicknameDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("修改昵称");

        final EditText input = new EditText(this);
        input.setText(authManager.getNickname());
        input.setSelection(input.getText().length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String newNick = input.getText().toString().trim();
            if (newNick.isEmpty()) { return; }
            authManager.saveNickname(newNick);
            updateUI();
            new Thread(() -> {
                String error = authManager.updateNickname(newNick);
                if (error != null) {
                    runOnUiThread(() -> showError("云端同步失败（本地已保存）: " + error));
                }
            }).start();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
