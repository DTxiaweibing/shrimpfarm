package com.shrimpfarm.app;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.view.Gravity;
import androidx.core.view.GravityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.*;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.navigation.NavigationView;
import com.shrimpfarm.app.analysis.DataAnalysisActivity;
import com.shrimpfarm.app.banner.BannerManager;
import com.shrimpfarm.app.utils.DialogHelper;
import com.shrimpfarm.app.model.AlertItem;
import com.shrimpfarm.app.model.ExcelBasedFeedConversion;
import com.shrimpfarm.app.home.AlertGenerator;
import static com.shrimpfarm.app.home.AlertGenerator.PREF_SMART_MASTER;
import static com.shrimpfarm.app.home.AlertGenerator.PREF_SMART_PREFIX;
import com.shrimpfarm.app.home.TaskScheduler;
import com.shrimpfarm.app.utils.EncryptUtils;
import com.shrimpfarm.app.utils.LocaleHelper;

import java.text.SimpleDateFormat;
import java.util.*;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;

@SuppressWarnings("SpellCheckingInspection")
public class MainActivity extends BaseActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ViewPager vpBanner;
    private GridView gvFunctions;
    private ActionBarDrawerToggle toggle;
    private Toolbar toolbar;
    private com.shrimpfarm.app.startup.AppStartupManager startupManager;

    private TextView tvBatchName;
    private TextView tvRecorderName;
    private TextView toolbarBatchName;

    private SharedPreferences prefs;
    private String currentBatchName = "";
    private String currentRecorder = "";

    // 轮播图管理器
    private BannerManager bannerManager;

    // 计划任务
    private ViewGroup scrollTaskBars;
    private ViewGroup layoutAlertBars;
    private LinearLayout alertBarsContainer;
    private LinearLayout layoutTaskBars;
    private BroadcastReceiver taskUpdateReceiver;

    // 功能网格数据
    private final int[] funcIcons = {
            R.drawable.jcsj, R.drawable.yzjl, R.drawable.cljl, R.drawable.bljs,
            R.drawable.szjc, R.drawable.sjcx, R.drawable.clyg, R.drawable.bzjy,
            R.drawable.zjzx, R.drawable.hqzx
    };
    private String[] funcNames;

    private static final String PREF_FEED_DISPLAY_MODE = "feed_display_mode";
    private static final String[] SMART_AGENT_KEYS = {
        "feed_increase", "feed_timeout", "feed_check",
        "water_quality", "water_core", "nitrite",
        "vibrio", "chlorine", "h2s", "orp", "do",
        "estimate", "feed_time"
    };
    private static final int POSITION_ESTIMATE = 6;

    private DatabaseHelper dbHelper;
    private ExcelBasedFeedConversion fcrModel;
    private SharedPreferences alertPrefs;
    private static final String PREF_DISMISSED_ALERTS = "dismissed_alerts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        if (CrashHandler.hasCrashLog(this)) {
            startActivity(new Intent(this, CrashReportActivity.class));
            finish();
            return;
        }

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        if (!prefs.getBoolean("consent_accepted", false)) {
            showConsentDialog();
            return;
        }

        AppIntegrityChecker.startCheck(this);

        setContentView(R.layout.activity_main);

        alertPrefs = getSharedPreferences("alert_prefs", MODE_PRIVATE);
        currentBatchName = prefs.getString("current_batch_name", "");
        currentRecorder = prefs.getString("login_user_name", "");

        initViews();
        addVersionFooter();
        setupToolbar();
        setupDrawer();
        startupManager = new com.shrimpfarm.app.startup.AppStartupManager(this,
            this::showUpdateAvailableUI);
        startupManager.run();

        setBannerHeight();
        initBanner();                     // 初始化轮播图（仅从缓存加载）
        setupFunctionGrid();
        setupBottomNavigation();
        enableSwipeNavigation();
        updateBatchDisplay();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    @Override
    protected boolean isTouchOnSwipeBlockedView(float x, float y) {
        if (vpBanner == null) return false;
        int[] loc = new int[2];
        vpBanner.getLocationOnScreen(loc);
        return x >= loc[0] && x <= loc[0] + vpBanner.getWidth()
            && y >= loc[1] && y <= loc[1] + vpBanner.getHeight();
    }

    private void addVersionFooter() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView tvVersion = new TextView(this);
            tvVersion.setText(getString(R.string.app_version, versionName));
            tvVersion.setTextSize(12);
            tvVersion.setTextColor(0xFF000000);
            tvVersion.setGravity(Gravity.CENTER);
            tvVersion.setPadding(0, 8, 0, 12);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.BOTTOM;
            tvVersion.setLayoutParams(params);
            navigationView.addView(tvVersion);
            navigationView.post(() -> {
                int navBarH = navigationView.getPaddingBottom();
                if (navBarH > 0) tvVersion.setPadding(0, 8, 0, navBarH + 12);
            });
        } catch (Exception ignored) { /* ignored */ }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        vpBanner = findViewById(R.id.vp_banner);
        gvFunctions = findViewById(R.id.gv_functions);
        View headerView = navigationView.getHeaderView(0);
        tvBatchName = headerView.findViewById(R.id.tv_batch_name);
        tvRecorderName = headerView.findViewById(R.id.tv_recorder_name);

        scrollTaskBars = findViewById(R.id.scroll_task_bars);
        layoutAlertBars = findViewById(R.id.layout_alert_bars);
        alertBarsContainer = findViewById(R.id.alert_bars_container);
        layoutTaskBars = findViewById(R.id.layout_task_bars);
    }

    private void setBannerHeight() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int bannerHeight = screenWidth * 5 / 14;
        ViewGroup.LayoutParams params = vpBanner.getLayoutParams();
        params.height = bannerHeight;
        vpBanner.setLayoutParams(params);
    }

    private void initBanner() {
        bannerManager = new BannerManager(this, vpBanner,
                new int[]{R.drawable.banner_1, R.drawable.banner_2});
        bannerManager.init();
    }

    private void adjustNavigationViewWidth() {
        View headerView = navigationView.getHeaderView(0);
        TextView tvBatchName = headerView.findViewById(R.id.tv_batch_name);
        String batchText = tvBatchName.getText().toString();
        android.graphics.Paint paint = tvBatchName.getPaint();
        float textWidth = paint.measureText(batchText);
        float density = getResources().getDisplayMetrics().density;
        float paddingWidth = 32 * density;
        float totalWidthPx = textWidth + paddingWidth;
        int requiredWidthDp = (int) (totalWidthPx / density);
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int minWidth = screenWidthDp / 3;
        int maxWidth = screenWidthDp / 2;
        int finalWidth = Math.max(minWidth, Math.min(requiredWidthDp, maxWidth));
        ViewGroup.LayoutParams params = navigationView.getLayoutParams();
        params.width = (int) (finalWidth * density);
        navigationView.setLayoutParams(params);
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbarBatchName = new TextView(this);
        toolbarBatchName.setTextSize(18);
        toolbarBatchName.setTypeface(Typeface.DEFAULT_BOLD);
        toolbarBatchName.setTextColor(0xFF333333);
        Toolbar.LayoutParams params = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        toolbarBatchName.setLayoutParams(params);
        toolbar.addView(toolbarBatchName);
    }

 private void setupDrawer() {
    toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
            R.string.open_drawer, R.string.close_drawer);
    drawerLayout.addDrawerListener(toggle);
    toggle.syncState();

    navigationView.setNavigationItemSelectedListener(item -> {
        int id = item.getItemId();
        if (id == R.id.menu_batch_manage) {
            startActivity(new Intent(MainActivity.this, BatchManageActivity.class));
        } else if (id == R.id.menu_plan_task) {
            if (currentBatchName.isEmpty()) {
                showStyledConfirmDialog(getString(R.string.main_title_tip),
                    getString(R.string.main_msg_create_batch_first),
                    new String[]{getString(R.string.btn_cancel), getString(R.string.main_go_setting)},
                    new int[]{0xFF666666, 0xFF4CAF50},
                    new DialogInterface.OnClickListener[]{
                        (dialog, which) -> {},
                        (dialog, which) -> startActivity(new Intent(MainActivity.this, BatchManageActivity.class))
                    });
            } else {
                String batchId = prefs.getString("current_batch_id", "");
                if (batchId.isEmpty()) {
                    showStyledConfirmDialog(getString(R.string.main_title_tip),
                        getString(R.string.main_msg_create_batch_first),
                        new String[]{getString(R.string.btn_cancel), getString(R.string.main_go_setting)},
                        new int[]{0xFF666666, 0xFF4CAF50},
                        new DialogInterface.OnClickListener[]{
                            (dialog, which) -> {},
                            (dialog, which) -> startActivity(new Intent(MainActivity.this, BatchManageActivity.class))
                        });
                } else {
                    String seedQuantity = dbHelper.getBasicData(batchId, "seed_quantity");
                    String pondCount = dbHelper.getBasicData(batchId, "pond_count");
                    String pondLength = dbHelper.getBasicData(batchId, "pond_length");
                    String aeratorCount = dbHelper.getBasicData(batchId, "aerator_count");
                    String aerationPower = dbHelper.getBasicData(batchId, "aeration_power");
                    String stockingDate = dbHelper.getBasicData(batchId, "stocking_date");
                    String feedBrand = dbHelper.getBasicData(batchId, "feed_brand");

                    boolean isComplete = !seedQuantity.isEmpty() &&
                        !pondCount.isEmpty() &&
                        !pondLength.isEmpty() &&
                        !aeratorCount.isEmpty() &&
                        !aerationPower.isEmpty() &&
                        !stockingDate.isEmpty() &&
                        !stockingDate.equals(getString(R.string.main_select_date)) &&
                        !feedBrand.isEmpty();

                    if (!isComplete) {
                        showStyledConfirmDialog(getString(R.string.main_title_tip),
                            getString(R.string.main_msg_complete_basic_data),
                            new String[]{getString(R.string.btn_cancel), getString(R.string.main_go_setting)},
                            new int[]{0xFF666666, 0xFF4CAF50},
                            new DialogInterface.OnClickListener[]{
                                (dialog, which) -> {},
                                (dialog, which) -> startActivity(new Intent(MainActivity.this, BasicDataActivity.class))
                            });
                    } else {
                        startActivity(new Intent(MainActivity.this, PlanTaskActivity.class));
                    }
                }
            }
        } else if (id == R.id.menu_smart_assistant) {
            showSmartAssistantDialog();
        } else if (id == R.id.menu_backup) {
            startActivity(new Intent(MainActivity.this, com.shrimpfarm.app.backup.BackupActivity.class));
        } else if (id == R.id.menu_privacy_policy) {
            Intent intent = new Intent(MainActivity.this, AssetWebViewActivity.class);
            intent.putExtra(AssetWebViewActivity.EXTRA_TITLE, getString(R.string.menu_privacy_policy));
            intent.putExtra(AssetWebViewActivity.EXTRA_FILE, "privacy-policy.html");
            startActivity(intent);
        } else if (id == R.id.menu_user_agreement) {
            Intent intent = new Intent(MainActivity.this, AssetWebViewActivity.class);
            intent.putExtra(AssetWebViewActivity.EXTRA_TITLE, getString(R.string.menu_user_agreement));
            intent.putExtra(AssetWebViewActivity.EXTRA_FILE, "user-agreement.html");
            startActivity(intent);
        } else if (id == R.id.menu_language) {
            showLanguageDialog();
        } else if (id == R.id.menu_update_version) {
            if (startupManager != null && startupManager.hasUnseenUpdate()) {
                showUpdateDialog();
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(com.shrimpfarm.app.startup.AppStartupManager.getUpdatePageUrl())));
            }
        }
        drawerLayout.closeDrawers();
        return true;
    });

    Menu menu = navigationView.getMenu();
    for (int i = 0; i < menu.size(); i++) {
        MenuItem menuItem = menu.getItem(i);
        SpannableString s = new SpannableString(menuItem.getTitle());
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        menuItem.setTitle(s);
    }
}

    private Drawable createScaledNotifyIcon() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notify_update);
        if (bitmap == null) return AppCompatResources.getDrawable(this, R.drawable.ic_menu_update_red);
        float density = getResources().getDisplayMetrics().density;
        int targetHeightPx = (int) (32 * density + 0.5f);
        int targetWidthPx = (int) (bitmap.getWidth() * targetHeightPx / (float) bitmap.getHeight());
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidthPx, targetHeightPx, true);
        if (scaled != bitmap) bitmap.recycle();
        return new BitmapDrawable(getResources(), scaled);
    }

    private void showUpdateAvailableUI() {
        if (toolbar == null) return;
        toggle.setDrawerIndicatorEnabled(false);
        toolbar.setNavigationIcon(createScaledNotifyIcon());
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        MenuItem item = navigationView.getMenu().findItem(R.id.menu_update_version);
        if (item != null) {
            CharSequence current = item.getTitle();
            SpannableString s = current instanceof SpannableString
                ? SpannableString.valueOf(current) : new SpannableString(current);
            s.setSpan(new android.text.style.ForegroundColorSpan(Color.RED), 0, s.length(), 0);
            item.setTitle(s);
        }
    }

    private void resetUpdateUI() {
        if (toolbar == null) return;
        toggle.setDrawerIndicatorEnabled(true);
        toggle.syncState();
        toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        MenuItem item = navigationView.getMenu().findItem(R.id.menu_update_version);
        if (item != null) {
            SpannableString s = new SpannableString(getString(R.string.menu_update_version));
            s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
            item.setTitle(s);
        }
    }

    private void showLanguageDialog() {
        String[] langs = {getString(R.string.lang_auto), getString(R.string.lang_chinese), getString(R.string.lang_english)};
        final String[] langKeys = {"auto", "zh", "en"};
        String current = com.shrimpfarm.app.utils.LocaleHelper.getSavedLang(this);
        int checked = 0;
        for (int i = 0; i < langKeys.length; i++) {
            if (langKeys[i].equals(current)) { checked = i; break; }
        }
        Dialog dialog = new Dialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_language, null);
        dialog.setContentView(dialogView);
        dialog.setCanceledOnTouchOutside(true);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(R.string.language_title);

        ListView lv = dialogView.findViewById(R.id.lv_languages);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_language, langs);
        lv.setAdapter(adapter);
        lv.setItemChecked(checked, true);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            String key = langKeys[position];
            com.shrimpfarm.app.utils.LocaleHelper.setLocale(this, key);
            recreate();
        });

        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private void showUpdateDialog() {
        if (startupManager == null) return;
        final String version = startupManager.getUpdateVersion();
        final String log = startupManager.getUpdateLog();
        showStyledConfirmDialog(
            getString(R.string.main_find_new_version, version),
            log,
            new String[]{getString(R.string.main_btn_view_update), getString(R.string.main_btn_ignore_version), getString(R.string.main_btn_remind_later)},
            new int[]{0xFF4CAF50, 0xFF666666, 0xFF666666},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> {
                    startupManager.remindLater();
                    resetUpdateUI();
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(com.shrimpfarm.app.startup.AppStartupManager.getUpdatePageUrl())));
                },
                (dialog, which) -> {
                    startupManager.ignoreVersion(version);
                    resetUpdateUI();
                },
                (dialog, which) -> {
                    startupManager.remindLater();
                    resetUpdateUI();
                }
            }
        );
    }

    private String getAgentDisplayName(String key) {
        switch (key) {
            case "feed_increase": return getString(R.string.agent_feed_increase);
            case "feed_timeout": return getString(R.string.agent_feed_timeout);
            case "feed_check": return getString(R.string.agent_feed_check);
            case "water_quality": return getString(R.string.agent_water_dispatch);
            case "water_core": return getString(R.string.agent_water_core);
            case "nitrite": return getString(R.string.agent_nitrite);
            case "vibrio": return getString(R.string.agent_vibrio);
            case "chlorine": return getString(R.string.agent_chlorine);
            case "h2s": return getString(R.string.agent_h2s);
            case "orp": return getString(R.string.agent_orp);
            case "do": return getString(R.string.agent_do);
            case "estimate": return getString(R.string.agent_estimate);
            case "feed_time": return getString(R.string.agent_feed_time);
            default: return key;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showConsentDialog() {
        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setStatusBarColor(0);
        webView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                if ("agree".equals(message)) {
                    prefs.edit().putBoolean("consent_accepted", true).apply();
                    result.confirm();
                    recreate();
                    return true;
                } else if ("disagree".equals(message)) {
                    result.confirm();
                    runOnUiThread(() -> showStyledConfirmDialog(getString(R.string.main_title_tip),
                        getString(R.string.main_msg_disagree_privacy),
                        new String[]{getString(R.string.btn_ok)}, null,
                        new DialogInterface.OnClickListener[]{ (d, w) -> finishAndRemoveTask() },
                        false));
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }
        });
        String consentFile = "privacy-consent.html";
        String lang = LocaleHelper.getSavedLang(this);
        if ("en".equals(lang)) {
            consentFile = "privacy-consent-en.html";
        }
        webView.loadUrl("file:///android_asset/" + consentFile);
        setContentView(webView);
    }

    private void showSmartAssistantDialog() {
        android.widget.FrameLayout tmpRoot = new android.widget.FrameLayout(this);
        View sheet = getLayoutInflater().inflate(R.layout.layout_smart_assistant_dialog, tmpRoot, false);

        SwitchCompat switchMaster = sheet.findViewById(R.id.switch_master);
        LinearLayout agentList = sheet.findViewById(R.id.layout_agent_list);
        SharedPreferences sp = getSharedPreferences("app_prefs", MODE_PRIVATE);

        boolean masterOn = sp.getBoolean(PREF_SMART_MASTER, true);
        switchMaster.setChecked(masterOn);
        populateAgentList(agentList, sp, masterOn);

        switchMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean(PREF_SMART_MASTER, isChecked).apply();
            for (int i = 0; i < agentList.getChildCount(); i++) {
                View row = agentList.getChildAt(i);
                SwitchCompat sw = row.findViewById(R.id.switch_agent);
                if (sw != null) sw.setEnabled(isChecked);
            }
            loadPlanTasks();
        });

        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setNavigationBarColor(Color.WHITE);
            float density = getResources().getDisplayMetrics().density;
            int screenWidthDp = (int)(getResources().getDisplayMetrics().widthPixels / density);
            int dialogWidthDp = Math.max(180, screenWidthDp / 3);
            View toolbar = findViewById(R.id.toolbar);
            View bottomNav = findViewById(R.id.bottom_nav);
            int[] loc = new int[2];
            toolbar.getLocationOnScreen(loc);
            int topY = loc[1] + toolbar.getHeight();
            bottomNav.getLocationOnScreen(loc);
            int bottomY = loc[1];
            int dialogHeight = bottomY - topY;
            window.setLayout((int)(dialogWidthDp * density), dialogHeight);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.START | Gravity.TOP;
            lp.x = 0;
            lp.y = topY;
            lp.dimAmount = 0f;
            window.setAttributes(lp);
        }

        drawerLayout.closeDrawers();
        drawerLayout.postDelayed(() -> {
            if (!isFinishing()) dialog.show();
        }, 250);
    }

    private void populateAgentList(LinearLayout agentList, SharedPreferences sp, boolean masterOn) {
        for (int i = 0; i < SMART_AGENT_KEYS.length; i++) {
            String key = SMART_AGENT_KEYS[i];
            String name = getAgentDisplayName(key);

            View row = getLayoutInflater().inflate(R.layout.item_agent_switch, agentList, false);
            TextView tvName = row.findViewById(R.id.tv_agent_name);
            SwitchCompat sw = row.findViewById(R.id.switch_agent);
            tvName.setText(name);
            boolean checked = sp.getBoolean(PREF_SMART_PREFIX + key, true);
            sw.setChecked(checked);
            sw.setEnabled(masterOn);

            final int index = i;
            final String agentKey = key;
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sp.edit().putBoolean(PREF_SMART_PREFIX + agentKey, isChecked).apply();
                loadPlanTasks();
                if ("estimate".equals(agentKey) && gvFunctions != null) {
                    gvFunctions.invalidateViews();
                }
            });

            agentList.addView(row);
        }
    }

    private void setupFunctionGrid() {
        dbHelper = DatabaseHelper.getInstance(this);
        fcrModel = new ExcelBasedFeedConversion();

        funcNames = new String[]{
            getString(R.string.func_basic_data),
            getString(R.string.func_feeding_record),
            getString(R.string.func_check_feed),
            getString(R.string.func_mix_calc),
            getString(R.string.func_water_quality),
            getString(R.string.func_data_analysis),
            getString(R.string.func_yield_estimate),
            getString(R.string.func_community_help),
            getString(R.string.func_expert_consult),
            getString(R.string.func_market_info)
        };

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < funcIcons.length && i < funcNames.length; i++) {
            Map<String, Object> map = new HashMap<>();
            map.put("icon", funcIcons[i]);
            map.put("name", funcNames[i]);
            map.put("position", i);
            items.add(map);
        }

        SimpleAdapter adapter = new SimpleAdapter(
                this, items, R.layout.item_grid_function,
                new String[]{"icon", "name"},
                new int[]{R.id.iv_icon, R.id.tv_name}
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (position == 0 && currentBatchName != null && !currentBatchName.isEmpty()) {
                    TextView tvData = view.findViewById(R.id.tv_data);
                    if (tvData != null) {
                        int day = dbHelper.getStockingDay(prefs.getString("current_batch_id", ""));
                        if (day > 0) {
                            tvData.setText(String.valueOf(day));
                            tvData.setTextColor(0xFFFF0000);
                            tvData.setTextSize(20);
                            tvData.setVisibility(View.VISIBLE);
                        }
                    }
                }
                if (position == POSITION_ESTIMATE && currentBatchName != null && !currentBatchName.isEmpty()) {
                    TextView tvData = view.findViewById(R.id.tv_data);
                    TextView tvName = view.findViewById(R.id.tv_name);
                    ImageView ivIcon = view.findViewById(R.id.iv_icon);
                    if (tvData != null && ivIcon != null) {
                        tvData.setVisibility(View.VISIBLE);
                        if (prefs.getBoolean(PREF_SMART_PREFIX + "estimate", true)) {
                            updateEstimateButtonData(tvData, tvName);
                            ivIcon.setOnClickListener(v -> toggleEstimateDisplay(prefs, tvData, tvName));
                        } else {
                            double total = calculateTotalFeed(prefs.getString("current_batch_id", ""));
                            tvData.setText(String.format(Locale.getDefault(), "%.1f", total));
                            tvName.setText(getString(R.string.main_feed_total));
                            ivIcon.setOnClickListener(null);
                        }
                    }
                }
                return view;
            }
        };
        gvFunctions.setAdapter(adapter);

        gvFunctions.setOnItemClickListener((parent, view, position, id) -> {
                Intent intent = null;
                switch (position) {
                    case 0: intent = new Intent(MainActivity.this, BasicDataActivity.class); break;
                    case 1: intent = new Intent(MainActivity.this, FeedingRecordActivity.class); break;
                    case 2: intent = new Intent(MainActivity.this, com.shrimpfarm.app.checkfeed.CheckFeedActivity.class); break;
                    case 3: intent = new Intent(MainActivity.this, com.shrimpfarm.app.mixcalc.MixCalcActivity.class); break;
                    case 4: intent = new Intent(MainActivity.this, com.shrimpfarm.app.water.WaterQualityActivity.class); break;
                    case 5: intent = new Intent(MainActivity.this, DataAnalysisActivity.class); break;
                    case 6: return;
                    case 7: intent = new Intent(MainActivity.this, HelpActivity.class); break;
                    case 8: intent = new Intent(MainActivity.this, ExpertActivity.class); break;
                    case 9: intent = new Intent(MainActivity.this, com.shrimpfarm.app.hq.HqActivity.class); break;
                }
                if (intent != null) startActivity(intent);
        });
    }

    private void updateBatchDisplay() {
        String displayName = currentBatchName.isEmpty() ? getString(R.string.nav_batch_not_selected) : currentBatchName;
        if (tvBatchName != null) tvBatchName.setText(displayName);
        if (tvRecorderName != null) tvRecorderName.setText(String.format(Locale.getDefault(), getString(R.string.main_recorder_format), currentRecorder));
        if (toolbarBatchName != null) toolbarBatchName.setText(displayName);
        adjustNavigationViewWidth();
    }

    private void loadPlanTasks() {
        String batchId = prefs.getString("current_batch_id", "");
        alertBarsContainer.removeAllViews();
        layoutAlertBars.setVisibility(View.GONE);
        layoutTaskBars.removeAllViews();
        if (batchId.isEmpty()) { scrollTaskBars.setVisibility(View.INVISIBLE); return; }
        if (dbHelper == null) dbHelper = DatabaseHelper.getInstance(this);

        SharedPreferences sp = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!TaskScheduler.isTaskTimeVisible(sp)) {
            scrollTaskBars.setVisibility(View.INVISIBLE); return;
        }

        Thread t = new Thread(() -> {
            List<AlertItem> alerts = AlertGenerator.generate(MainActivity.this, dbHelper, sp, batchId);
            Set<String> dismissed = alertPrefs.getStringSet(PREF_DISMISSED_ALERTS, new HashSet<>());
            List<TaskScheduler.TaskItem> tasks = TaskScheduler.computeTasks(MainActivity.this, dbHelper, batchId);

            runOnUiThread(() -> {
                int alertCount = 0;
                for (AlertItem alert : alerts) {
                    if (!dismissed.contains(String.valueOf(alert.id))) {
                        alertBarsContainer.addView(buildAlertBar(alert));
                        alertCount++;
                    }
                }

                if (alertCount > 0) {
                    layoutAlertBars.post(() -> {
                        positionAlertBars();
                        layoutAlertBars.setVisibility(View.VISIBLE);
                    });
                } else {
                    layoutAlertBars.setVisibility(View.GONE);
                }

                int taskCount = tasks.size();
                if (taskCount > 0) {
                    scrollTaskBars.setVisibility(View.VISIBLE);
                    for (TaskScheduler.TaskItem task : tasks) {
                        layoutTaskBars.addView(buildTaskBar(task.taskId, task.batchId, task.label, task.badgeText, task.bgColor));
                    }
                    final ScrollView sv = findViewById(R.id.scroll_task_bars);
                    if (sv != null) {
                        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
                    }
                } else {
                    scrollTaskBars.setVisibility(View.INVISIBLE);
                }
            });
        }, "MainActivity-loadPlanTasks");
        t.setDaemon(true);
        t.start();
    }

    private View buildTaskBar(final long taskId, final String batchId, String label,
                              String badgeText, int bgColor) {
        float density = getResources().getDisplayMetrics().density;
        View bar = getLayoutInflater().inflate(R.layout.item_home_task_bar, layoutTaskBars, false);
        TextView tvLabel = bar.findViewById(R.id.tv_due_label);
        tvLabel.setText(badgeText);
        tvLabel.setVisibility(View.VISIBLE);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(3);
        badgeBg.setColor(0x33FFFFFF);
        tvLabel.setBackground(badgeBg);

        TextView tvTitle = bar.findViewById(R.id.tv_task_title);
        tvTitle.setText(label);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6);
        bg.setColor(bgColor);
        bar.setBackground(bg);

        if (badgeText != null && badgeText.equals(getString(R.string.badge_tomorrow))) {
            // due tomorrow — no complete button needed
        } else {
            Button btnComplete = new Button(this);
            btnComplete.setText(getString(R.string.main_btn_complete));
            btnComplete.setTextSize(13);
            btnComplete.setTextColor(0xFFFFFFFF);
            btnComplete.setPadding((int)(10 * density), (int)(3 * density),
                    (int)(10 * density), (int)(3 * density));
            btnComplete.setMinWidth(0);
            btnComplete.setMinHeight(0);
            btnComplete.setMinimumWidth(0);
            btnComplete.setMinimumHeight(0);
            btnComplete.setIncludeFontPadding(false);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(4);
            btnBg.setColor(0x00000000);
            btnBg.setStroke((int)(1.5f * density + 0.5f), 0xFFFFFFFF);
            btnComplete.setBackground(btnBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(4);
            ((LinearLayout) bar).addView(btnComplete, lp);
            btnComplete.setOnClickListener(v -> {
                dbHelper.completeTask(taskId, batchId);
                if (bar.getParent() instanceof ViewGroup) {
                    ((ViewGroup) bar.getParent()).removeView(bar);
                }
                if (layoutTaskBars.getChildCount() == 0) {
                    scrollTaskBars.setVisibility(View.INVISIBLE);
                }
            });
        }
        return bar;
    }

    @SuppressWarnings("ExtractMethodRecommender")
    private View buildAlertBar(final AlertItem alert) {
        float density = getResources().getDisplayMetrics().density;
        float dp1_5 = 1.5f * density;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int)(10 * density), 0, (int)(4 * density), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6);
        bg.setColor(0xFFFB6D0F);
        bar.setBackground(bg);

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(34 * density));
        barLp.setMargins((int)(12 * density), (int)dp1_5, (int)(12 * density), (int)dp1_5);
        bar.setLayoutParams(barLp);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(alert.message);
        tvMsg.setTextColor(0xFFFFFFFF);
        tvMsg.setTextSize(16);
        tvMsg.setSingleLine(true);
        tvMsg.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tvMsg.setMarqueeRepeatLimit(-1);
        tvMsg.setSelected(true);
        tvMsg.setGravity(Gravity.CENTER_VERTICAL);
        tvMsg.setIncludeFontPadding(false);
        tvMsg.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(tvMsg);

        Button btnOk = new Button(this);
        btnOk.setText(getString(R.string.main_btn_got_it));
        btnOk.setTextSize(13);
        btnOk.setTextColor(0xFFFFFFFF);
        btnOk.setPadding((int)(10 * density), (int)(3 * density),
                (int)(10 * density), (int)(3 * density));
        btnOk.setMinWidth(0);
        btnOk.setMinHeight(0);
        btnOk.setMinimumWidth(0);
        btnOk.setMinimumHeight(0);
        btnOk.setIncludeFontPadding(false);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(4);
        btnBg.setColor(0x00000000);
        btnBg.setStroke((int)(1.5f * density + 0.5f), 0xFFFFFFFF);
        btnOk.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMarginEnd(0);
        bar.addView(btnOk, btnLp);

        btnOk.setOnClickListener(v -> dismissAlert(alert, bar));

        return bar;
    }

    private void dismissAlert(AlertItem alert, LinearLayout bar) {
        Set<String> dismissed = new HashSet<>(alertPrefs.getStringSet(PREF_DISMISSED_ALERTS, new HashSet<>()));
        dismissed.add(String.valueOf(alert.id));
        alertPrefs.edit().putStringSet(PREF_DISMISSED_ALERTS, dismissed).apply();
        ViewGroup parent = (ViewGroup) bar.getParent();
        if (parent != null) {
            parent.removeView(bar);
        }
        if (alertBarsContainer.getChildCount() == 0) {
            layoutAlertBars.setVisibility(View.GONE);
        }
    }

    private void positionAlertBars() {
        int[] gridPos = new int[2];
        gvFunctions.getLocationInWindow(gridPos);
        int gridBottom = gridPos[1] + gvFunctions.getHeight();

        int[] parentPos = new int[2];
        ((View) layoutAlertBars.getParent()).getLocationInWindow(parentPos);

        int targetY = gridBottom - parentPos[1];
        if (targetY < 0) targetY = 0;

        layoutAlertBars.setY(targetY);
        ViewGroup.LayoutParams lp = layoutAlertBars.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutAlertBars.setLayoutParams(lp);
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onResume() {
        super.onResume();
        if (!prefs.getBoolean("consent_accepted", false)) return;
        dbHelper = DatabaseHelper.getInstance(this);
        currentBatchName = prefs.getString("current_batch_name", "");
        currentRecorder = prefs.getString("login_user_name", "");
        updateBatchDisplay();
        setupFunctionGrid();
        refreshEstimateData();
        if (bannerManager != null) {
            bannerManager.onResume();
        }
        if (startupManager != null) {
            startupManager.run();
        }
        loadPlanTasks();

        if (taskUpdateReceiver == null) {
            taskUpdateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    loadPlanTasks();
                }
            };
            IntentFilter filter = new IntentFilter("com.shrimpfarm.app.TASK_UPDATE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(taskUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(taskUpdateReceiver, filter);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (taskUpdateReceiver != null) {
            try { unregisterReceiver(taskUpdateReceiver); } catch (Exception ignored) { /* ignored */ }
            taskUpdateReceiver = null;
        }
    }

    private void refreshEstimateData() {
        if (gvFunctions == null || gvFunctions.getAdapter() == null) return;
        for (int i = 0; i < gvFunctions.getChildCount(); i++) {
            View view = gvFunctions.getChildAt(i);
            if (view != null) {
                TextView tvData = view.findViewById(R.id.tv_data);
                TextView tvName = view.findViewById(R.id.tv_name);
                if (tvData != null && tvData.getVisibility() == View.VISIBLE) {
                    if (i == 0) {
                        int day = dbHelper.getStockingDay(prefs.getString("current_batch_id", ""));
                        if (day > 0) {
                            tvData.setText(String.valueOf(day));
                            tvData.setTextColor(0xFFFF0000);
                            tvData.setTextSize(20);
                        }
                    } else {
                        updateEstimateButtonData(tvData, tvName);
                    }
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) return true;
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bannerManager != null) {
            bannerManager.destroy();
        }
    }

    private void updateEstimateButtonData(TextView tvData, TextView tvName) {
        if (dbHelper == null || currentBatchName.isEmpty()) {
            tvData.setText(""); tvName.setText(getString(R.string.main_estimate_yield)); return;
        }
        String batchId = prefs.getString("current_batch_id", "");
        if (batchId.isEmpty()) {
            tvData.setText(""); tvName.setText(getString(R.string.main_estimate_yield)); return;
        }

        String mode = prefs.getString(PREF_FEED_DISPLAY_MODE, "total");
        if ("estimate".equals(mode)) {
            double estimate = calculateEstimate(batchId);
            tvData.setText(String.format(Locale.getDefault(), "%.1f", estimate));
            tvName.setText(getString(R.string.main_estimate_yield));
        } else {
            double total = calculateTotalFeed(batchId);
            tvData.setText(String.format(Locale.getDefault(), "%.1f", total));
            tvName.setText(getString(R.string.main_feed_total));
        }
    }

    private double calculateTotalFeed(String batchId) {
        double total = 0;
        Cursor cursor = null;
        try {
            Calendar cal = Calendar.getInstance();
            String todayStr = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(cal.getTime());
            cursor = dbHelper.getReadableDatabase().rawQuery(
                    "SELECT " + DatabaseHelper.COLUMN_BREAKFAST + ", " +
                    DatabaseHelper.COLUMN_LUNCH + ", " +
                    DatabaseHelper.COLUMN_DINNER + ", " +
                    DatabaseHelper.COLUMN_NIGHT_SNACK +
                    " FROM " + DatabaseHelper.TABLE_DAILY_RECORDS +
                    " WHERE " + DatabaseHelper.COLUMN_BATCH_ID + "=? AND " +
                    DatabaseHelper.COLUMN_DATE + "<=?",
                    new String[]{batchId, todayStr});
            while (cursor.moveToNext()) {
                for (int i = 0; i < 4; i++) {
                    String encVal = cursor.getString(i);
                    if (encVal != null && !encVal.isEmpty()) {
                        try {
                            String val = EncryptUtils.decrypt(encVal);
                            if (val != null && !val.isEmpty()) {
                                total += Double.parseDouble(val);
                            }
                        } catch (Exception ignored) { /* ignored */ }
                    }
                }
            }
        } catch (Exception ignored) { /* ignored */ }
        finally { if (cursor != null && !cursor.isClosed()) cursor.close(); }
        return total;
    }

    private double calculateEstimate(String batchId) {
        double totalFeed = calculateTotalFeed(batchId);
        if (totalFeed <= 0) return 0;

        String seedBrand = dbHelper.getBasicData(batchId, "seed_brand");
        String feedBrand = dbHelper.getBasicData(batchId, "feed_brand");
        String stockingDate = dbHelper.getBasicData(batchId, "stocking_date");

        if (stockingDate.isEmpty() || getString(R.string.main_select_date).equals(stockingDate)) {
            return totalFeed * 0.8;
        }

        int days = calculateDaysSinceStocking(stockingDate);
        return fcrModel.estimateYield((float) totalFeed, days, seedBrand, feedBrand);
    }

    private int calculateDaysSinceStocking(String stockingDate) {
        if (stockingDate.isEmpty()) return 0;
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
            } catch (Exception ignored) { /* ignored */ }
        }
        return 0;
    }

    private void toggleEstimateDisplay(SharedPreferences prefs, TextView tvData, TextView tvName) {
        String currentMode = prefs.getString(PREF_FEED_DISPLAY_MODE, "total");
        String newMode = "total".equals(currentMode) ? "estimate" : "total";
        prefs.edit().putString(PREF_FEED_DISPLAY_MODE, newMode).apply();
        updateEstimateButtonData(tvData, tvName);
    }


}