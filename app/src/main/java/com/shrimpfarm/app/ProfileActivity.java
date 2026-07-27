package com.shrimpfarm.app;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ProfileActivity extends BaseActivity {

    private SupabaseAuthManager authManager;
    private LinearLayout layoutLoggedIn;
    private ScrollView layoutLogin;
    private TextView tvNickname, tvEmail, tvRecorderInfo, tvError, tvLogoutOnLogin;
    private EditText etEmail, etPassword, etNickname;
    private ImageView ivTogglePwd;
    private Button btnLogin, btnRegister;
    private boolean pwdVisible = false;

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
        tvError = findViewById(R.id.tv_error);
        tvLogoutOnLogin = findViewById(R.id.tv_logout_on_login);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etNickname = findViewById(R.id.et_nickname);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        ivTogglePwd = findViewById(R.id.iv_toggle_pwd);
        Button btnLogout = findViewById(R.id.btn_logout);
        Button btnEditNickname = findViewById(R.id.btn_edit_nickname);

        TextView tvForgotPwd = findViewById(R.id.tv_forgot_pwd);

        btnLogin.setOnClickListener(v -> doLogin());
        btnRegister.setOnClickListener(v -> doRegister());
        ivTogglePwd.setOnClickListener(v -> {
            pwdVisible = !pwdVisible;
            int pos = etPassword.getSelectionEnd();
            etPassword.setInputType(pwdVisible ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etPassword.setSelection(pos);
            ivTogglePwd.setImageResource(pwdVisible ? R.drawable.ic_eye_open : R.drawable.ic_eye_closed);
        });
        tvForgotPwd.setOnClickListener(v -> doForgotPassword());
        btnLogout.setOnClickListener(v -> {
            authManager.logout();
            updateUI();
        });
        tvLogoutOnLogin.setOnClickListener(v -> {
            authManager.logout();
            updateUI();
        });
        btnEditNickname.setOnClickListener(v -> showEditNicknameDialog());

        setupBottomNavigation();
        enableSwipeNavigation();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager.isLoggedIn()) {
            Thread t = new Thread(() -> {
                String err = authManager.loadWebDavFromCloud();
                if (err == null) {
                    runOnUiThread(this::updateUI);
                }
            }, "Profile-refresh");
            t.setDaemon(true);
            t.start();
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
            tvError.setVisibility(View.GONE);
            SharedPreferences sp = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean hasCachedSession = !"未登录".equals(sp.getString("login_user_name", "未登录"))
                    || authManager.getToken() != null && !authManager.getToken().isEmpty();
            tvLogoutOnLogin.setVisibility(hasCachedSession ? View.VISIBLE : View.GONE);
        }
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) { showError("请输入邮箱"); return; }
        if (password.isEmpty()) { showError("请输入密码"); return; }
        if (password.length() < 6) { showError("密码至少6位"); return; }

        final ProgressDialog pd = ProgressDialog.show(this, "", "登录中...", true);

        Thread t = new Thread(() -> {
            final SupabaseAuthManager.AuthResult result = authManager.login(email, password);

            runOnUiThread(() -> {
                pd.dismiss();
                if (result.success) {
                    tvError.setVisibility(View.GONE);
                    updateUI();
                } else {
                    showError(result.message);
                }
            });
        }, "Profile-login");
        t.setDaemon(true);
        t.start();
    }

    private void doRegister() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();

        if (email.isEmpty()) { showError("请输入邮箱"); return; }
        if (password.isEmpty()) { showError("请输入密码"); return; }
        if (password.length() < 6) { showError("密码至少6位"); return; }
        if (nickname.isEmpty()) { nickname = email.split("@")[0]; }
        final String finalNickname = nickname;
        final String finalEmail = email;
        final String finalPassword = password;

        final ProgressDialog pd = ProgressDialog.show(this, "", "注册中...", true);

        Thread t = new Thread(() -> {
            final SupabaseAuthManager.AuthResult result = authManager.register(finalEmail, finalPassword, finalNickname);

            runOnUiThread(() -> {
                pd.dismiss();
                if (result.success) {
                    tvError.setVisibility(View.GONE);
                    etEmail.setText(finalEmail);
                    etPassword.setText(finalPassword);
                    etNickname.setText("");
                    layoutLoggedIn.setVisibility(View.GONE);
                    layoutLogin.setVisibility(View.VISIBLE);
                    tvLogoutOnLogin.setVisibility(View.GONE);
                } else {
                    showError(result.message);
                }
            });
        }, "Profile-register");
        t.setDaemon(true);
        t.start();
    }

    private void showError(String msg) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private void doForgotPassword() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) { showError("请先输入邮箱"); return; }

        final ProgressDialog pd = ProgressDialog.show(this, "", "发送中...", true);
        Thread t = new Thread(() -> {
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
        }, "Profile-forgotPwd");
        t.setDaemon(true);
        t.start();
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
            Thread t = new Thread(() -> {
                String error = authManager.updateNickname(newNick);
                if (error != null) {
                    runOnUiThread(() -> showError("云端同步失败（本地已保存）: " + error));
                }
            }, "Profile-updateNickname");
            t.setDaemon(true);
            t.start();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
