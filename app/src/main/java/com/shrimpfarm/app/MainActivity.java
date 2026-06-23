package com.shrimpfarm.app;

import android.app.AlertDialog;
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
import android.webkit.WebViewClient;
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
import com.shrimpfarm.app.model.AlertItem;
import com.shrimpfarm.app.model.ExcelBasedFeedConversion;
import com.shrimpfarm.app.model.FeedCheckAlertModel;
import com.shrimpfarm.app.model.FeedIncreaseAlertModel;
import com.shrimpfarm.app.model.WaterQualityAlertModel;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.text.SimpleDateFormat;
import java.util.*;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;

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
    private String currentRecorder = "未登录";

    // 轮播图管理器
    private BannerManager bannerManager;

    // 计划任务
    private ViewGroup scrollTaskBars;
    private ViewGroup layoutAlertBars;
    private LinearLayout alertBarsContainer;
    private LinearLayout layoutTaskBars;
    private LinearLayout bottomNav;
    private BroadcastReceiver taskUpdateReceiver;

    // 功能网格数据
    private final int[] funcIcons = {
            R.drawable.jcsj, R.drawable.yzjl, R.drawable.cljl, R.drawable.bljs,
            R.drawable.szjc, R.drawable.sjcx, R.drawable.clyg, R.drawable.bzjy,
            R.drawable.zjzx, R.drawable.hqzx
    };
    private final String[] funcNames = {
            "基础数据", "养殖记录", "查料记录", "拌料计算",
            "水质检测", "数据分析", "产量预估", "帮助建议",
            "专家咨询", "行情资讯"
    };

    private static final String PREF_FEED_DISPLAY_MODE = "feed_display_mode";
    private static final String PREF_SMART_MASTER = "smart_assistant_master";
    private static final String PREF_SMART_PREFIX = "smart_agent_";
    private static final String[][] SMART_AGENTS = {
        {"饲料增量检测", "feed_increase"},
        {"单棚吃料超时提醒", "feed_timeout"},
        {"查料分析", "feed_check"},
        {"水质总调度", "water_quality"},
        {"水质核心检测", "water_core"},
        {"亚硝酸盐检测", "nitrite"},
        {"弧菌检测", "vibrio"},
        {"余氯检测", "chlorine"},
        {"硫化氢检测", "h2s"},
        {"氧化还原电位", "orp"},
        {"溶解氧检测", "do"},
        {"产量预估", "estimate"},
        {"查料用时提醒", "feed_time"}
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

        setContentView(R.layout.activity_main);

        alertPrefs = getSharedPreferences("alert_prefs", MODE_PRIVATE);
        currentBatchName = prefs.getString("current_batch_name", "");
        currentRecorder = prefs.getString("login_user_name", "未登录");

        initViews();
        addVersionFooter();
        setupToolbar();
        setupDrawer();
        startupManager = new com.shrimpfarm.app.startup.AppStartupManager(this,
            () -> showUpdateAvailableUI());
        startupManager.run();

        setBannerHeight();
        initBanner();                     // 初始化轮播图（仅从缓存加载）
        setupFunctionGrid();
        setupBottomNavigation();
        updateBatchDisplay();
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
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
        } catch (Exception ignored) {
        }
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
        bottomNav = findViewById(R.id.bottom_nav);
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
                showStyledConfirmDialog("提示", "请先创建养殖批次",
                    new String[]{"取消", "去设置"},
                    new int[]{0xFF666666, 0xFF4CAF50},
                    new DialogInterface.OnClickListener[]{
                        (dialog, which) -> {},
                        (dialog, which) -> startActivity(new Intent(MainActivity.this, BatchManageActivity.class))
                    });
            } else {
                String batchId = prefs.getString("current_batch_id", "");
                if (batchId.isEmpty()) {
                    showStyledConfirmDialog("提示", "请先创建养殖批次",
                        new String[]{"取消", "去设置"},
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
                        !stockingDate.equals("选择日期") &&
                        !feedBrand.isEmpty();

                    if (!isComplete) {
                        showStyledConfirmDialog("提示", "请先完成基础数据中的所有必填项",
                            new String[]{"取消", "去设置"},
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
            intent.putExtra(AssetWebViewActivity.EXTRA_TITLE, "隐私政策");
            intent.putExtra(AssetWebViewActivity.EXTRA_FILE, "privacy-policy.html");
            startActivity(intent);
        } else if (id == R.id.menu_user_agreement) {
            Intent intent = new Intent(MainActivity.this, AssetWebViewActivity.class);
            intent.putExtra(AssetWebViewActivity.EXTRA_TITLE, "用户协议");
            intent.putExtra(AssetWebViewActivity.EXTRA_FILE, "user-agreement.html");
            startActivity(intent);
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

    private void showUpdateDialog() {
        if (startupManager == null) return;
        final String version = startupManager.getUpdateVersion();
        final String log = startupManager.getUpdateLog();
        showStyledConfirmDialog(
            "发现新版本 v" + version,
            log,
            new String[]{"查看更新", "忽略此版本", "以后再说"},
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

    @SuppressLint("SetJavaScriptEnabled")
    private void showConsentDialog() {
        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
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
                    runOnUiThread(() -> showStyledConfirmDialog("提示",
                        "您已选择不同意隐私政策，应用即将退出。",
                        new String[]{"确定"}, null,
                        new DialogInterface.OnClickListener[]{ (d, w) -> finishAndRemoveTask() },
                        false));
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }
        });
        webView.loadUrl("file:///android_asset/privacy-consent.html");
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
            window.setLayout((int)(dialogWidthDp * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            View toolbar = findViewById(R.id.toolbar);
            int[] loc = new int[2];
            toolbar.getLocationOnScreen(loc);
            int toolbarBottomY = loc[1] + toolbar.getHeight();
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.START | Gravity.TOP;
            lp.x = 0;
            lp.y = toolbarBottomY;
            lp.dimAmount = 0f;
            window.setAttributes(lp);
        }

        drawerLayout.closeDrawers();
        drawerLayout.postDelayed(() -> {
            if (!isFinishing()) dialog.show();
        }, 250);
    }

    private void populateAgentList(LinearLayout agentList, SharedPreferences sp, boolean masterOn) {
        for (int i = 0; i < SMART_AGENTS.length; i++) {
            String name = SMART_AGENTS[i][0];
            String key = SMART_AGENTS[i][1];

            View row = getLayoutInflater().inflate(R.layout.item_agent_switch, agentList, false);
            TextView tvName = row.findViewById(R.id.tv_agent_name);
            SwitchCompat sw = row.findViewById(R.id.switch_agent);
            tvName.setText(name);
            boolean checked = sp.getBoolean(PREF_SMART_PREFIX + key, true);
            sw.setChecked(checked);
            sw.setEnabled(masterOn);

            final int index = i;
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> sp.edit().putBoolean(PREF_SMART_PREFIX + SMART_AGENTS[index][1], isChecked).apply());

            agentList.addView(row);
        }
    }

    private void setupFunctionGrid() {
        dbHelper = new DatabaseHelper(this);
        fcrModel = new ExcelBasedFeedConversion();

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
                            tvName.setText("投喂总量");
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
        String displayName = currentBatchName.isEmpty() ? "未选择批次" : currentBatchName;
        if (tvBatchName != null) tvBatchName.setText(displayName);
        if (tvRecorderName != null) tvRecorderName.setText(String.format(Locale.getDefault(), "记录人：%s", currentRecorder));
        if (toolbarBatchName != null) toolbarBatchName.setText(displayName);
        adjustNavigationViewWidth();
    }

    private void loadPlanTasks() {
        String batchId = prefs.getString("current_batch_id", "");
        alertBarsContainer.removeAllViews();
        layoutAlertBars.setVisibility(View.GONE);
        layoutTaskBars.removeAllViews();
        if (batchId.isEmpty()) { scrollTaskBars.setVisibility(View.INVISIBLE); return; }
        if (dbHelper == null) dbHelper = new DatabaseHelper(this);

        SharedPreferences sp = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!sp.getBoolean("plan_task_master_switch", true)) {
            scrollTaskBars.setVisibility(View.INVISIBLE); return;
        }

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);
        if (hour >= 6 && hour < 17) {
            if (!sp.getBoolean("plan_task_day_switch", true)) {
                scrollTaskBars.setVisibility(View.INVISIBLE); return;
            }
        } else if (hour >= 17 && (hour < 22 || (hour == 22 && minute <= 30))) {
            if (!sp.getBoolean("plan_task_night_switch", true)) {
                scrollTaskBars.setVisibility(View.INVISIBLE); return;
            }
        } else {
            scrollTaskBars.setVisibility(View.INVISIBLE); return;
        }

        int stockingDay = dbHelper.getStockingDay(batchId);
        if (stockingDay <= 0) { scrollTaskBars.setVisibility(View.INVISIBLE); return; }

        List<View> overdueViews = new ArrayList<>();
        List<View> todayViews = new ArrayList<>();
        List<View> tomorrowViews = new ArrayList<>();

        Cursor c = dbHelper.getAllSubTasks(batchId);
        if (c != null) {
            while (c.moveToNext()) {
                long taskId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_ID));
                long parentId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PARENT_ID));
                String subName = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TASK_NAME));
                int unitType = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_UNIT_TYPE));
                int startValue = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_START_VALUE));
                int endValue = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_END_VALUE));
                double intervalValue = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_INTERVAL_VALUE));
                int lastTriggerDay = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_DAY));
                double lastTriggerFeed = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_TRIGGER_FEED));

                String mainName = subName;
                if (parentId > 0) {
                    Cursor pc = dbHelper.getReadableDatabase().query(
                            DatabaseHelper.TABLE_PLAN_TASKS,
                            new String[]{DatabaseHelper.COLUMN_TASK_NAME},
                            DatabaseHelper.COLUMN_TASK_ID + "=?", new String[]{String.valueOf(parentId)},
                            null, null, null);
                    if (pc.moveToFirst()) mainName = pc.getString(0);
                    pc.close();
                }
                if (mainName == null || mainName.isEmpty()) mainName = "任务";

                boolean showOverdue = false;
                boolean showToday = false;
                boolean showTomorrow = false;

                if (unitType == 0) {
                    int inter = (int) intervalValue;
                    if (inter <= 0) inter = 1;
                    boolean isPast17 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 17;

                    // 今天
                    if (stockingDay >= startValue && stockingDay <= endValue &&
                            (stockingDay - startValue) % inter == 0 && stockingDay > lastTriggerDay) {
                        if (isPast17) {
                            showOverdue = true;
                        } else {
                            showToday = true;
                        }
                    }
                    // 明天
                    if (stockingDay + 1 >= startValue && stockingDay + 1 <= endValue &&
                            (stockingDay + 1 - startValue) % inter == 0) {
                        showTomorrow = true;
                    }
                    // 超期：最近一个到期日 < 今天且未完成
                    int intervalsPast = (stockingDay - 1 - startValue) / inter;
                    if (intervalsPast >= 0) {
                        int lastPastDue = startValue + intervalsPast * inter;
                        if (lastPastDue >= startValue && lastPastDue <= endValue && lastPastDue > lastTriggerDay) {
                            showOverdue = true;
                        }
                    }
                } else {
                    double currentFeed = dbHelper.getAccumulatedFeed(batchId, startValue, stockingDay);
                    double nextThreshold = lastTriggerFeed + intervalValue;
                    if (currentFeed >= nextThreshold) {
                        showToday = true;
                    }
                }

                String taskLabel = mainName;
                if (subName != null && !subName.isEmpty() && !subName.equals(mainName)) {
                    taskLabel = mainName + " - " + subName;
                }

                if (showOverdue) {
                    overdueViews.add(buildTaskBar(taskId, batchId, taskLabel, "已超期", 0xFFFF0000));
                }
                if (showToday) {
                    todayViews.add(buildTaskBar(taskId, batchId, taskLabel, "今天", 0xFF0000FF));
                }
                if (showTomorrow) {
                    tomorrowViews.add(buildTaskBar(taskId, batchId, taskLabel, "明天", 0xFF0C8918));
                }
            }
            c.close();
        }

        int taskCount = overdueViews.size() + todayViews.size() + tomorrowViews.size();

        int alertCount = 0;
        if (dbHelper != null && sp.getBoolean(PREF_SMART_MASTER, true)) {
            Set<String> dismissed = alertPrefs.getStringSet(PREF_DISMISSED_ALERTS, new HashSet<>());
            List<AlertItem> alerts = new ArrayList<>();
            if (sp.getBoolean(PREF_SMART_PREFIX + "feed_increase", true))
                alerts.addAll(FeedIncreaseAlertModel.check(dbHelper.getReadableDatabase(), batchId));
            if (sp.getBoolean(PREF_SMART_PREFIX + "feed_timeout", true)) {
                String today = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
                String stockingDate = dbHelper.getBasicData(batchId, "stocking_date");
                boolean isFourMeals = FeedCheckAlertModel.isFourMeals(dbHelper.getReadableDatabase(), batchId);
                long standardSeconds = FeedCheckAlertModel.getStandardSeconds(stockingDate, isFourMeals);
                if (standardSeconds > 0) {
                    List<DatabaseHelper.CheckRecord> records = dbHelper.getCheckRecordsByDate(batchId, today);
                    long[] durations = new long[records.size()];
                    int[] shedNums = new int[records.size()];
                    int validCount = 0;
                    for (DatabaseHelper.CheckRecord r : records) {
                        if (!r.excluded && r.durationSeconds > 0) {
                            durations[validCount] = r.durationSeconds * 1000;
                            try {
                                shedNums[validCount] = Integer.parseInt(r.shedNumber);
                            } catch (NumberFormatException e) {
                                shedNums[validCount] = r.shedRowIndex + 1;
                            }
                            validCount++;
                        }
                    }
                    if (validCount > 0) {
                        FeedCheckAlertModel.TimeoutResult result = FeedCheckAlertModel.checkShedTimeouts(
                            standardSeconds,
                            Arrays.copyOf(durations, validCount),
                            Arrays.copyOf(shedNums, validCount));
                        if (!result.shedNumbers.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (int s : result.shedNumbers) {
                                if (sb.length() > 0) sb.append("、");
                                sb.append(s);
                            }
                            alerts.add(new AlertItem(sb + "号棚吃料超时，请检查", "SHED_TIMEOUT"));
                        }
                    }
                }
            }
            if (sp.getBoolean(PREF_SMART_PREFIX + "feed_check", true))
                alerts.addAll(FeedCheckAlertModel.check(dbHelper.getReadableDatabase(), batchId));
            if (sp.getBoolean(PREF_SMART_PREFIX + "feed_time", true)) {
                Cursor ft = dbHelper.getReadableDatabase().query(
                    DatabaseHelper.TABLE_FEEDING_CHECK_ANALYSIS, null,
                    "batch_id=?", new String[]{batchId},
                    null, null, "record_time DESC", "1");
                if (ft.moveToFirst()) {
                    double avgSec = ft.getDouble(ft.getColumnIndexOrThrow("avg_seconds"));
                    double stdSec = ft.getDouble(ft.getColumnIndexOrThrow("standard_seconds"));
                    if (stdSec > 0) {
                        double ratio = (avgSec - stdSec) / stdSec;
                        if (ratio > 0.20) {
                            alerts.add(new AlertItem("查料用时超20%，请优化查料流程", "FEED_TIME"));
                        } else if (ratio > 0.10) {
                            alerts.add(new AlertItem("查料用时超10%，注意提高效率", "FEED_TIME"));
                        }
                    }
                }
                ft.close();
            }
            if (sp.getBoolean(PREF_SMART_PREFIX + "water_quality", true))
                alerts.addAll(WaterQualityAlertModel.check(dbHelper.getReadableDatabase(), batchId,
                    sp.getBoolean(PREF_SMART_PREFIX + "water_core", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "nitrite", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "vibrio", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "chlorine", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "h2s", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "orp", true),
                    sp.getBoolean(PREF_SMART_PREFIX + "do", true)));
            for (AlertItem alert : alerts) {
                String idStr = String.valueOf(alert.id);
                if (!dismissed.contains(idStr)) {
                    alertBarsContainer.addView(buildAlertBar(alert));
                    alertCount++;
                }
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

        if (taskCount > 0) {
            scrollTaskBars.setVisibility(View.VISIBLE);
        } else {
            scrollTaskBars.setVisibility(View.INVISIBLE);
        }

        if (taskCount == 0 && alertCount == 0) {
            return;
        }

        for (View v : overdueViews) layoutTaskBars.addView(v);
        for (View v : todayViews) layoutTaskBars.addView(v);
        for (View v : tomorrowViews) layoutTaskBars.addView(v);

        // 默认滚动到最底部，使最下方的任务条紧贴底导
        final ScrollView sv = findViewById(R.id.scroll_task_bars);
        if (sv != null && taskCount > 0) {
            sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
        }
    }

    private View buildTaskBar(final long taskId, final String batchId, String label,
                              String badgeText, int bgColor) {
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

        if (!badgeText.equals("明天")) {
            Button btnComplete = new Button(this);
            btnComplete.setText("完成");
            btnComplete.setTextSize(14);
            btnComplete.setPadding(6, 2, 6, 2);
            btnComplete.setMinWidth(0);
            btnComplete.setMinHeight(0);
            btnComplete.setMinimumWidth(0);
            btnComplete.setMinimumHeight(0);
            btnComplete.setIncludeFontPadding(false);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(5);
            btnBg.setColor(0x00000000);
            btnBg.setStroke((int)(1.5f * getResources().getDisplayMetrics().density + 0.5f), 0xFFFFFFFF);
            btnComplete.setBackground(btnBg);
            btnComplete.setTextColor(0xFFFFFFFF);
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
        tvMsg.setTextSize(13);
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
        btnOk.setText("明白");
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

        int[] navPos = new int[2];
        bottomNav.getLocationInWindow(navPos);
        int navTop = navPos[1];

        int[] parentPos = new int[2];
        ((View) layoutAlertBars.getParent()).getLocationInWindow(parentPos);

        int targetY = gridBottom - parentPos[1];
        int targetH = navTop - gridBottom;
        if (targetY < 0) targetY = 0;
        if (targetH < 0) targetH = 0;

        layoutAlertBars.setY(targetY);
        ViewGroup.LayoutParams lp = layoutAlertBars.getLayoutParams();
        lp.height = targetH;
        layoutAlertBars.setLayoutParams(lp);
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onResume() {
        super.onResume();
        if (!prefs.getBoolean("consent_accepted", false)) return;
        dbHelper = DatabaseHelper.getInstance(this);
        currentBatchName = prefs.getString("current_batch_name", "");
        currentRecorder = prefs.getString("login_user_name", "未登录");
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
                registerReceiver(taskUpdateReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(taskUpdateReceiver, filter);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (taskUpdateReceiver != null) {
            try { unregisterReceiver(taskUpdateReceiver); } catch (Exception ignored) {}
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
            tvData.setText(""); tvName.setText("产量预估"); return;
        }
        String batchId = prefs.getString("current_batch_id", "");
        if (batchId.isEmpty()) {
            tvData.setText(""); tvName.setText("产量预估"); return;
        }

        String mode = prefs.getString(PREF_FEED_DISPLAY_MODE, "total");
        if ("estimate".equals(mode)) {
            double estimate = calculateEstimate(batchId);
            tvData.setText(String.format(Locale.getDefault(), "%.1f", estimate));
            tvName.setText("产量预估");
        } else {
            double total = calculateTotalFeed(batchId);
            tvData.setText(String.format(Locale.getDefault(), "%.1f", total));
            tvName.setText("投喂总量");
        }
    }

    private double calculateTotalFeed(String batchId) {
        double total = 0;
        try {
            Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                    "SELECT " + DatabaseHelper.COLUMN_BREAKFAST + ", " +
                    DatabaseHelper.COLUMN_LUNCH + ", " +
                    DatabaseHelper.COLUMN_DINNER + ", " +
                    DatabaseHelper.COLUMN_NIGHT_SNACK +
                    " FROM " + DatabaseHelper.TABLE_DAILY_RECORDS +
                    " WHERE " + DatabaseHelper.COLUMN_BATCH_ID + " = ?",
                    new String[]{batchId});
            while (cursor.moveToNext()) {
                for (int i = 0; i < 4; i++) {
                    String encVal = cursor.getString(i);
                    if (encVal != null && !encVal.isEmpty()) {
                        try {
                            String val = EncryptUtils.decrypt(encVal);
                            if (val != null && !val.isEmpty()) {
                                total += Double.parseDouble(val);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            cursor.close();
        } catch (Exception ignored) {}
        return total;
    }

    private double calculateEstimate(String batchId) {
        double totalFeed = calculateTotalFeed(batchId);
        if (totalFeed <= 0) return 0;

        String seedBrand = dbHelper.getBasicData(batchId, "seed_brand");
        String feedBrand = dbHelper.getBasicData(batchId, "feed_brand");
        String stockingDate = dbHelper.getBasicData(batchId, "stocking_date");

        if (stockingDate.isEmpty() || "选择日期".equals(stockingDate)) {
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
            } catch (Exception ignored) {}
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