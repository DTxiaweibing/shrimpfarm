package com.shrimpfarm.app.backup;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.SupabaseAuthManager;
import com.shrimpfarm.app.utils.DialogHelper;
import com.shrimpfarm.app.utils.StoragePermissionHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupActivity extends AppCompatActivity {

    private LocalBackupManager localManager;
    private WebDavManager webDavManager;
    private SupabaseAuthManager authManager;

    private TextView tvStatus;
    private Button btnLocalBackup;

    private LinearLayout layoutConfig;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnConnect;

    private LinearLayout layoutConnected;
    private Button btnUpload;
    private Button btnDisconnect;

    private Button btnTabLocal;
    private Button btnTabCloud;
    private TextView tvListTitle;
    private ListView lvHistory;

    private boolean showingLocal = true;
    private List<BackupEntry> historyList = new ArrayList<>();
    private BackupHistoryAdapter historyAdapter;
    private BackupEntry pendingRestoreEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        localManager = new LocalBackupManager(this);
        webDavManager = new WebDavManager(this);
        authManager = new SupabaseAuthManager(this);

        if (!authManager.isLoggedIn()) {
            DialogHelper.showStyledConfirmDialog(this, "需要登录",
                    "备份管理需要先登录账号。\n请前往「我的」页面登录后再试。",
                    new String[]{"确定"},
                    new DialogInterface.OnClickListener[]{ (d, w) -> finish() },
                    false);
        }

        tvStatus = findViewById(R.id.tv_backup_status);
        btnLocalBackup = findViewById(R.id.btn_local_backup);
        layoutConfig = findViewById(R.id.layout_webdav_config);
        etUsername = findViewById(R.id.et_webdav_username);
        etPassword = findViewById(R.id.et_webdav_password);
        btnConnect = findViewById(R.id.btn_webdav_connect);
        layoutConnected = findViewById(R.id.layout_webdav_connected);
        btnUpload = findViewById(R.id.btn_webdav_upload);
        btnDisconnect = findViewById(R.id.btn_webdav_disconnect);
        btnTabLocal = findViewById(R.id.btn_tab_local);
        btnTabCloud = findViewById(R.id.btn_tab_cloud);
        tvListTitle = findViewById(R.id.tv_list_title);
        lvHistory = findViewById(R.id.lv_backup_history);

        historyAdapter = new BackupHistoryAdapter();
        lvHistory.setAdapter(historyAdapter);

        setupButtons();
        loadWebDavConfig();
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            DialogHelper.showStyledConfirmDialog(this, "需要权限",
                "本地备份还原需要读写权限，是否去给予权限？",
                new String[]{"取消", "去授权"},
                new int[]{0xFF333333, 0xFF2D84C2},
                new DialogInterface.OnClickListener[]{
                    (d, w) -> {},
                    (d, w) -> StoragePermissionHelper.requestIfNeeded(BackupActivity.this)
                }, true);
        }
        refreshHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistory();
    }

    private void setupButtons() {
        btnLocalBackup.setOnClickListener(v -> {
            if (checkStoragePermission()) doLocalBackup();
        });

        btnConnect.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, "请输入用户名/邮箱", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "请输入应用专用密码", Toast.LENGTH_SHORT).show();
                return;
            }
            testWebDavConnection(username, password);
        });

        btnUpload.setOnClickListener(v -> doWebDavBackup());

        btnDisconnect.setOnClickListener(v -> {
            DialogHelper.showStyledConfirmDialog(this, "确认退出",
                    "退出后需要重新配置坚果云账号，确定退出？",
                    new String[]{"取消", "退出"},
                    new DialogInterface.OnClickListener[]{
                        null,
                        (d, w) -> {
                            webDavManager.clearConfig();
                            updateUI();
                            if (showingLocal) refreshHistory();
                            new Thread(() -> authManager.clearWebDavFromCloud()).start();
                        }
                    });
        });

        btnTabLocal.setOnClickListener(v -> {
            showingLocal = true;
            updateTabStyle();
            refreshHistory();
        });

        btnTabCloud.setOnClickListener(v -> {
            if (!webDavManager.isConfigured()) {
                Toast.makeText(this, "请先配置并测试坚果云连接", Toast.LENGTH_SHORT).show();
                return;
            }
            showingLocal = false;
            updateTabStyle();
            refreshHistory();
        });

        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            BackupEntry entry = historyList.get(position);
            if (entry.isLocal && !StoragePermissionHelper.hasStoragePermission(this)) {
                pendingRestoreEntry = entry;
                StoragePermissionHelper.requestIfNeeded(this);
                return;
            }
            showRestoreOptions(entry);
        });
    }

    private void updateTabStyle() {
        if (showingLocal) {
            btnTabLocal.setBackgroundColor(0xFF444444);
            btnTabLocal.setTextColor(0xFFFFFFFF);
            btnTabCloud.setBackgroundColor(0x00000000);
            btnTabCloud.setTextColor(0xFF888888);
            tvListTitle.setText("本地备份文件（点击可还原）");
        } else {
            btnTabCloud.setBackgroundColor(0xFF1677FF);
            btnTabCloud.setTextColor(0xFFFFFFFF);
            btnTabLocal.setBackgroundColor(0x00000000);
            btnTabLocal.setTextColor(0xFF888888);
            tvListTitle.setText("坚果云备份文件（点击可还原）");
        }
    }

    private boolean checkStoragePermission() {
        return StoragePermissionHelper.requestIfNeeded(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE) {
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                if (pendingRestoreEntry != null) {
                    BackupEntry entry = pendingRestoreEntry;
                    pendingRestoreEntry = null;
                    showRestoreOptions(entry);
                }
                if (showingLocal) refreshHistory();
            } else {
                String msg = pendingRestoreEntry != null ? "需要存储权限才能还原本地备份" : "需要存储权限才能备份到本地";
                pendingRestoreEntry = null;
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingRestoreEntry != null) {
                    BackupEntry entry = pendingRestoreEntry;
                    pendingRestoreEntry = null;
                    showRestoreOptions(entry);
                }
                if (showingLocal) refreshHistory();
            } else {
                String msg = pendingRestoreEntry != null ? "需要存储权限才能还原本地备份" : "需要存储权限才能备份到本地";
                pendingRestoreEntry = null;
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadWebDavConfig() {
        // use local cache immediately, then always refresh from cloud
        String account = authManager.getWebDavAccount();
        String password = authManager.getWebDavPassword();
        if (!account.isEmpty() && !password.isEmpty()) {
            webDavManager.initConnection(account, password);
            webDavManager.saveConfig(account, password);
            updateUI();
        }
        // always try to refresh from cloud
        new Thread(() -> {
            String err = authManager.loadWebDavFromCloud();
            if (err == null) {
                runOnUiThread(() -> {
                    String wa = authManager.getWebDavAccount();
                    String wp = authManager.getWebDavPassword();
                    if (!wa.isEmpty() && !wp.isEmpty()) {
                        webDavManager.initConnection(wa, wp);
                        webDavManager.saveConfig(wa, wp);
                        updateUI();
                    }
                });
            }
        }).start();
    }

    private void testWebDavConnection(String username, String password) {
        final ProgressDialog pd = ProgressDialog.show(this, "", "正在测试连接...", true);
        new Thread(() -> {
            try {
                webDavManager.initConnection(username, password);
                String msg = webDavManager.testConnection();
                String finalUsername = username;
                String finalPassword = password;
                // local save on UI thread
                runOnUiThread(() -> {
                    pd.dismiss();
                    webDavManager.saveConfig(finalUsername, finalPassword);
                    authManager.cacheWebDavLocally(finalUsername, finalPassword);
                    updateUI();
                });
                // cloud sync on background thread (network call)
                String cloudErr = authManager.saveWebDavToCloud(finalUsername, finalPassword);
                runOnUiThread(() -> {
                    if (cloudErr == null) {
                        Toast.makeText(BackupActivity.this, "绑定成功！" + msg, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BackupActivity.this, "绑定成功，但同步到云端失败：" + cloudErr, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    String errMsg = e.getMessage() != null ? e.getMessage() : "";
                    String userMsg;
                    if (errMsg.contains("401") || errMsg.contains("Unauthorized")) {
                        userMsg = "认证失败：请前往坚果云 APP → 设置 → 第三方应用管理生成应用专用密码";
                    } else if (errMsg.contains("timeout") || errMsg.contains("Timeout")) {
                        userMsg = "连接超时，请检查网络后重试。";
                    } else {
                        userMsg = "连接失败：" + errMsg;
                    }
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, "连接失败",
                            userMsg, new String[]{"确定"}, null);
                });
            }
        }).start();
    }

    private void doLocalBackup() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("本地备份");
        pd.setMessage("正在备份...");
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();

        new Thread(() -> {
            try {
                String filePath = localManager.exportToLocal();
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(BackupActivity.this, "本地备份成功！\n" + filePath, Toast.LENGTH_LONG).show();
                    if (showingLocal) refreshHistory();
                    updateUI();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, "备份失败",
                            "备份失败，请按以下步骤排查：\n\n" +
                                    "1. 点击下方「重试」按钮\n" +
                                    "2. 确认App已获得「文件/存储」权限（去手机设置→应用权限管理中开启）\n" +
                                    "3. 确认手机存储空间充足\n\n" +
                                    "如果重试仍失败，可能是数据库文件损坏或备份目录被删除，建议重启手机再试一次。\n\n" +
                                    "错误详情：" + e.getMessage(),
                            new String[]{"关闭", "重试"},
                            new DialogInterface.OnClickListener[]{ null, (d, w) -> doLocalBackup() });
                });
            }
        }).start();
    }

    private void doWebDavBackup() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("坚果云备份");
        pd.setMessage("正在上传...");
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();

        new Thread(() -> {
            try {
                String fileName = webDavManager.uploadBackup();
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(BackupActivity.this, "坚果云备份成功！", Toast.LENGTH_SHORT).show();
                    if (!showingLocal) refreshHistory();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, "上传失败",
                            e.getMessage() + "\n\n请检查网络和账号配置",
                            new String[]{"关闭", "重试"},
                            new DialogInterface.OnClickListener[]{ null, (d, w) -> doWebDavBackup() });
                });
            }
        }).start();
    }

    private void refreshHistory() {
        historyList.clear();
        if (showingLocal) {
            List<LocalBackupManager.BackupFileInfo> localBackups = localManager.listLocalBackups();
            for (LocalBackupManager.BackupFileInfo info : localBackups) {
                historyList.add(new BackupEntry(info.name, info.date, info.size, info, true));
            }
            historyAdapter.notifyDataSetChanged();
        } else {
            new Thread(() -> {
                try {
                    List<String> cloudFiles = webDavManager.listBackups();
                    java.util.Collections.sort(cloudFiles, (a, b) -> Long.compare(parseDateFromName(b), parseDateFromName(a)));
                    runOnUiThread(() -> {
                        for (String name : cloudFiles) {
                            long date = parseDateFromName(name);
                            historyList.add(new BackupEntry(name, date, 0, null, false));
                        }
                        historyAdapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(BackupActivity.this,
                            "获取坚果云备份列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private long parseDateFromName(String name) {
        try {
            String dateStr = name.replace("DataBackup_", "").replace(".db", "");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault());
            Date date = sdf.parse(dateStr);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void showRestoreOptions(BackupEntry entry) {
        String source = entry.isLocal ? "本地" : "坚果云";
        DialogHelper.showStyledConfirmDialog(this, "还原数据",
                "确定要还原以下备份？\n当前数据将被覆盖！\n\n来源: " + source + "\n文件: " + entry.name,
                new String[]{"取消", "还原"},
                new DialogInterface.OnClickListener[]{ null, (d, w) -> startRestore(entry) });
    }

    private void startRestore(BackupEntry entry) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("数据还原");
        pd.setMessage("正在准备...");
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();

        new Thread(() -> {
            try {
                String batchListJson = "";
                if (entry.isLocal) {
                    if (entry.localFileInfo == null) throw new Exception("备份信息丢失");
                    batchListJson = localManager.restoreFromBackup(entry.localFileInfo);
                } else {
                    File tempFile = new File(getCacheDir(), entry.name);
                    webDavManager.downloadBackup(entry.name, tempFile);
                    batchListJson = localManager.restoreFromBackup(
                            new LocalBackupManager.BackupFileInfo(entry.name, entry.date, tempFile.length(), tempFile));
                    if (tempFile.exists()) tempFile.delete();
                }
                if (batchListJson != null && !batchListJson.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                    prefs.edit().putString("batch_list_json", batchListJson).apply();
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(batchListJson);
                        if (arr.length() > 0) {
                            org.json.JSONObject first = arr.getJSONObject(0);
                            prefs.edit()
                                .putString("current_batch_id", first.getString("id"))
                                .putString("current_batch_name", first.getString("name"))
                                .apply();
                        }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    pd.dismiss();
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, "还原成功",
                            "数据已还原，批次信息也已同步。",
                            new String[]{"确定"},
                            new DialogInterface.OnClickListener[]{ (d, w) -> finish() },
                            false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    String msg = e.getMessage();
                    String userMsg;
                    if (msg == null) {
                        userMsg = "未知错误，请重试。";
                    } else if (msg.contains("401") || msg.contains("Unauthorized")) {
                        userMsg = "认证失败：请前往坚果云 APP → 设置 → 第三方应用管理生成应用专用密码";
                    } else if (msg.contains("404") || msg.contains("not found") || msg.contains("不存在")) {
                        userMsg = "备份文件在坚果云上不存在，可能已被删除。";
                    } else if (msg.contains("timeout") || msg.contains("Timeout")) {
                        userMsg = "连接超时，请检查网络后重试。";
                    } else if (msg.contains("数据库文件不存在")) {
                        userMsg = "本地数据库文件不存在，无法还原。";
                    } else {
                        userMsg = "还原失败：" + msg;
                    }
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, "还原失败",
                            userMsg, new String[]{"确定"}, null);
                });
            }
        }).start();
    }

    private void updateUI() {
        boolean configured = webDavManager.isConfigured();

        layoutConfig.setVisibility(configured ? View.GONE : View.VISIBLE);
        layoutConnected.setVisibility(configured ? View.VISIBLE : View.GONE);

        etUsername.setEnabled(!configured);
        etPassword.setEnabled(!configured);

        int localCount = localManager.listLocalBackups().size();
        boolean loggedIn = authManager.isLoggedIn();
        String loginInfo = loggedIn ? (authManager.getNickname() + " | ") : "未登录 | ";
        if (configured) {
            tvStatus.setText(getString(R.string.backup_status_configured, loginInfo, localCount, webDavManager.getSavedUsername()));
        } else {
            tvStatus.setText(getString(R.string.backup_status_unconfigured, loginInfo, localCount));
        }

        if (!configured && !showingLocal) {
            showingLocal = true;
            updateTabStyle();
            refreshHistory();
        }
    }

    private static class BackupEntry {
        final String name;
        final long date;
        final long size;
        final LocalBackupManager.BackupFileInfo localFileInfo;
        final boolean isLocal;

        BackupEntry(String name, long date, long size, LocalBackupManager.BackupFileInfo localFileInfo, boolean isLocal) {
            this.name = name;
            this.date = date;
            this.size = size;
            this.localFileInfo = localFileInfo;
            this.isLocal = isLocal;
        }
    }

    private class BackupHistoryAdapter extends BaseAdapter {
        @Override
        public int getCount() { return historyList.size(); }

        @Override
        public Object getItem(int position) { return historyList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(BackupActivity.this);
            BackupEntry entry = historyList.get(position);

            String label = entry.name;
            if (entry.size > 0) {
                label += " (" + formatSize(entry.size) + ")";
            }
            tv.setText(label);
            tv.setTextSize(14);
            tv.setTextColor(0xFF444444);
            tv.setPadding(20, 16, 20, 16);
            tv.setBackgroundResource(android.R.color.white);
            return tv;
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + "B";
            if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1fKB", bytes / 1024.0);
            return String.format(Locale.getDefault(), "%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }
}
