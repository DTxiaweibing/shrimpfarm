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
import com.shrimpfarm.app.BaseActivity;
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

public class BackupActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_my;
    }

    public static final String ERR_INFO_LOST = "ERR_BACKUP_INFO_LOST";
    public static final String ERR_DB_FILE_MISSING = "ERR_DB_FILE_MISSING";

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
            DialogHelper.showStyledConfirmDialog(this, getString(R.string.backup_title_need_login),
                    getString(R.string.backup_msg_need_login),
                    new String[]{getString(R.string.btn_ok)},
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
            DialogHelper.showStyledConfirmDialog(this, getString(R.string.backup_title_need_permission),
                getString(R.string.backup_msg_need_permission),
                new String[]{getString(R.string.btn_cancel), getString(R.string.backup_btn_grant)},
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
    }

    private void setupButtons() {
        btnLocalBackup.setOnClickListener(v -> {
            if (checkStoragePermission()) doLocalBackup();
        });

        btnConnect.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, getString(R.string.backup_toast_enter_email), Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.isEmpty()) {
                Toast.makeText(this, getString(R.string.backup_toast_enter_pwd), Toast.LENGTH_SHORT).show();
                return;
            }
            testWebDavConnection(username, password);
        });

        btnUpload.setOnClickListener(v -> doWebDavBackup());

        btnDisconnect.setOnClickListener(v -> {
            DialogHelper.showStyledConfirmDialog(this, getString(R.string.backup_title_confirm_disconnect),
                    getString(R.string.backup_msg_confirm_disconnect),
                    new String[]{getString(R.string.btn_cancel), getString(R.string.backup_btn_disconnect)},
                    new DialogInterface.OnClickListener[]{
                        null,
                        (d, w) -> {
                            webDavManager.clearConfig();
                            updateUI();
                            if (showingLocal) refreshHistory();
                            Thread t = new Thread(() -> authManager.clearWebDavFromCloud(), "BackupActivity-clearCloud");
                            t.setDaemon(true);
                            t.start();
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
                Toast.makeText(this, getString(R.string.backup_toast_configure_first), Toast.LENGTH_SHORT).show();
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
            tvListTitle.setText(getString(R.string.backup_title_local_list));
        } else {
            btnTabCloud.setBackgroundColor(0xFF1677FF);
            btnTabCloud.setTextColor(0xFFFFFFFF);
            btnTabLocal.setBackgroundColor(0x00000000);
            btnTabLocal.setTextColor(0xFF888888);
            tvListTitle.setText(getString(R.string.backup_title_cloud_list));
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
                } else {
                    doLocalBackup();
                }
                if (showingLocal) refreshHistory();
            } else {
                String msg = pendingRestoreEntry != null ? getString(R.string.backup_toast_need_storage_restore) : getString(R.string.backup_toast_need_storage_backup);
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
                } else {
                    doLocalBackup();
                }
                if (showingLocal) refreshHistory();
            } else {
                String msg = pendingRestoreEntry != null ? getString(R.string.backup_toast_need_storage_restore) : getString(R.string.backup_toast_need_storage_backup);
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
        Thread t = new Thread(() -> {
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
        }, "BackupActivity-loadCloud");
        t.setDaemon(true);
        t.start();
    }

    private void testWebDavConnection(String username, String password) {
        final ProgressDialog pd = ProgressDialog.show(this, "", getString(R.string.backup_progress_testing), true);
        Thread t = new Thread(() -> {
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
                        Toast.makeText(BackupActivity.this, getString(R.string.backup_toast_bind_success) + msg, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BackupActivity.this, getString(R.string.backup_toast_bind_sync_fail) + cloudErr, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    String errMsg = e.getMessage() != null ? e.getMessage() : "";
                    String userMsg;
                    if (errMsg.contains("401") || errMsg.contains("Unauthorized")) {
                        userMsg = getString(R.string.backup_err_auth_failed);
                    } else if (errMsg.contains("timeout") || errMsg.contains("Timeout")) {
                        userMsg = getString(R.string.backup_err_timeout);
                    } else {
                        userMsg = getString(R.string.backup_err_connect_prefix) + errMsg;
                    }
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, getString(R.string.backup_title_connect_fail),
                            userMsg, new String[]{getString(R.string.btn_ok)}, null);
                });
            }
        }, "BackupActivity-testConnection");
        t.setDaemon(true);
        t.start();
    }

    private void doLocalBackup() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle(getString(R.string.backup_title_local_backup));
        pd.setMessage(getString(R.string.backup_progress_backing_up));
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();

        Thread t = new Thread(() -> {
            try {
                String filePath = localManager.exportToLocal(true);
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(BackupActivity.this, getString(R.string.backup_toast_local_success, filePath), Toast.LENGTH_LONG).show();
                    if (showingLocal) refreshHistory();
                    updateUI();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, getString(R.string.backup_title_backup_fail),
                            getString(R.string.backup_msg_backup_fail, e.getMessage()),
                            new String[]{getString(R.string.backup_btn_close), getString(R.string.backup_btn_retry)},
                            new DialogInterface.OnClickListener[]{ null, (d, w) -> doLocalBackup() });
                });
            }
        }, "BackupActivity-exportLocal");
        t.setDaemon(true);
        t.start();
    }

    private void doWebDavBackup() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle(getString(R.string.backup_title_cloud_backup));
        pd.setMessage(getString(R.string.backup_progress_uploading));
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();

        Thread t = new Thread(() -> {
            try {
                String fileName = webDavManager.uploadBackup(true);
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(BackupActivity.this, getString(R.string.backup_toast_cloud_success), Toast.LENGTH_SHORT).show();
                    if (!showingLocal) refreshHistory();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, getString(R.string.backup_title_upload_fail),
                            e.getMessage() + "\n\n" + getString(R.string.backup_msg_check_network),
                            new String[]{getString(R.string.backup_btn_close), getString(R.string.backup_btn_retry)},
                            new DialogInterface.OnClickListener[]{ null, (d, w) -> doWebDavBackup() });
                });
            }
        }, "BackupActivity-exportWebDav");
        t.setDaemon(true);
        t.start();
    }

    private void refreshHistory() {
        if (showingLocal) {
            historyList.clear();
            List<LocalBackupManager.BackupFileInfo> localBackups = localManager.listLocalBackups();
            for (LocalBackupManager.BackupFileInfo info : localBackups) {
                historyList.add(new BackupEntry(info.name, info.date, info.size, info, true));
            }
            historyAdapter.notifyDataSetChanged();
        } else {
            Thread t = new Thread(() -> {
                try {
                    List<String> cloudFiles = webDavManager.listBackups();
                    java.util.Collections.sort(cloudFiles, (a, b) -> Long.compare(parseDateFromName(b), parseDateFromName(a)));
                    List<BackupEntry> entries = new ArrayList<>();
                    for (String name : cloudFiles) {
                        long date = parseDateFromName(name);
                        entries.add(new BackupEntry(name, date, 0, null, false));
                    }
                    runOnUiThread(() -> {
                        historyList.clear();
                        historyList.addAll(entries);
                        historyAdapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(BackupActivity.this,
                            getString(R.string.backup_toast_list_fail) + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }, "BackupActivity-refreshCloudList");
            t.setDaemon(true);
            t.start();
        }
    }

    private long parseDateFromName(String name) {
        try {
            String s = name.substring("DataBackup_".length());
            if (s.length() > 16) s = s.substring(0, 16);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault());
            Date date = sdf.parse(s);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void showRestoreOptions(BackupEntry entry) {
        String source = entry.isLocal ? getString(R.string.backup_source_local) : getString(R.string.backup_source_cloud);
        DialogHelper.showStyledConfirmDialog(this, getString(R.string.backup_title_restore),
                getString(R.string.backup_msg_restore_confirm, source, entry.name),
                new String[]{getString(R.string.btn_cancel), getString(R.string.backup_btn_restore)},
                new DialogInterface.OnClickListener[]{ null, (d, w) -> startRestore(entry) });
    }

    private void startRestore(BackupEntry entry) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle(getString(R.string.backup_title_data_restore));
        pd.setMessage(getString(R.string.backup_progress_preparing));
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();

        Thread t = new Thread(() -> {
            try {
                String batchListJson = "";
                if (entry.isLocal) {
                    if (entry.localFileInfo == null) throw new Exception(ERR_INFO_LOST);
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
                    } catch (Exception ignored) { /* ignored */ }
                }
                runOnUiThread(() -> {
                    pd.dismiss();
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, getString(R.string.backup_title_restore_success),
                            getString(R.string.backup_msg_restore_success),
                            new String[]{getString(R.string.btn_ok)},
                            new DialogInterface.OnClickListener[]{ (d, w) -> finish() },
                            false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    String msg = e.getMessage();
                    String userMsg;
                    if (msg == null) {
                        userMsg = getString(R.string.backup_err_unknown);
                    } else if (msg.contains(ERR_INFO_LOST)) {
                        userMsg = getString(R.string.backup_err_info_lost);
                    } else if (msg.contains("401") || msg.contains("Unauthorized")) {
                        userMsg = getString(R.string.backup_err_auth_failed);
                    } else if (msg.contains("404") || msg.contains("not found")) {
                        userMsg = getString(R.string.backup_err_not_found);
                    } else if (msg.contains("timeout") || msg.contains("Timeout")) {
                        userMsg = getString(R.string.backup_err_timeout);
                    } else if (msg.contains(ERR_DB_FILE_MISSING)) {
                        userMsg = getString(R.string.backup_err_local_db_missing);
                    } else {
                        userMsg = getString(R.string.backup_err_restore_prefix) + msg;
                    }
                    DialogHelper.showStyledConfirmDialog(BackupActivity.this, getString(R.string.backup_title_restore_fail),
                            userMsg, new String[]{getString(R.string.btn_ok)}, null);
                });
            }
        }, "BackupActivity-restoreBackup");
        t.setDaemon(true);
        t.start();
    }

    private void updateUI() {
        boolean configured = webDavManager.isConfigured();

        layoutConfig.setVisibility(configured ? View.GONE : View.VISIBLE);
        layoutConnected.setVisibility(configured ? View.VISIBLE : View.GONE);

        etUsername.setEnabled(!configured);
        etPassword.setEnabled(!configured);

        int localCount = localManager.listLocalBackups().size();
        boolean loggedIn = authManager.isLoggedIn();
        String loginInfo = loggedIn ? (authManager.getNickname() + " | ") : getString(R.string.status_not_logged_in) + " | ";
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

            tv.setText(entry.name);
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
