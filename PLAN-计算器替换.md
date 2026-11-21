# 计算器替换计划：把 mixcalc-lab 合回 shrimpfarm

> 目标：用 `D:\mixcalc-lab` 的现版本计算器整体替换 `D:\shrimpfarm` 现有计算器，
> 只保留旧的「批次 / 基础数据是否填写完整」校验，去掉「棚数上限」，底部按钮「帮助」改成「去查料」。

---

## 一、两边现状对照

| 项目 | shrimpfarm（旧） | mixcalc-lab（新） |
|---|---|---|
| MixCalcActivity | 2261 行，基于原生控件拼表格 | 3389 行，自绘表格 + 单 EditText 覆盖层 |
| mixcalc 包文件数 | 12 | 34 |
| 持久化 | SharedPreferences `MixCalcPrefs` / `saved_data` | 同 prefs 名，改 `batches_v2`（2 个批次），带旧数据迁移 |
| 布局 | 有 `bottom_nav`（5 个 tab） | 全屏表格，无底部导航，自带 `status_bar_spacer` / `nav_bar_spacer` |
| 帮助 | 底部「去查料」+ 无帮助页 | 底部「帮助」→ `assets/help/index.html` |
| 额外功能 | — | 棚号区间（1-20 / 1,3,5）、棚数转移、删除、列设置、批次切换、语言切换、隐私同意弹窗 |

**关键结论**

1. lab 的 `BaseActivity` 是 90 行沙盒桩，**不能覆盖** shrimpfarm 的 355 行正式版（完整性校验 / 水印 / 底部导航 / 左右滑动切页）。
2. lab 的 `AssetWebViewActivity` 与 shrimpfarm 同名但接口不同（`EXTRA_ASSET_PATH` + 全屏 + 开 JS vs `EXTRA_TITLE/EXTRA_FILE` + Toolbar），**不能覆盖**（MainActivity 在用）。
3. 两边 6 个共用布局（`popup_column_settings` / `popup_switch_ratio` / `dialog_simple_confirm` / `dialog_language` / `item_language`）**逐字节一致**；`activity_asset_webview.xml` 不同。
4. strings：87 个同名 key **值完全一致**；lab 多 49 个，其中 38 个是计算器真正要用的。
5. 旧数据可无缝迁移：lab `FeedData.fromJson` 字段是旧的超集（多 `shedCountBaseSum`），`global_water` / `fermented_included_*` 键名一致。

---

## 二、代码迁移清单

### 1. 删除（旧实现，全部无包外引用）

```
app/src/main/java/com/shrimpfarm/app/mixcalc/
├─ MixCalcActivity.java              （被新文件覆盖）
├─ model/FeedData.java               （被新文件覆盖）
├─ table/CellEditorOverlay.java      ┐
├─ table/CellStyle.java              │
├─ table/CellTextProvider.java       │
├─ table/MixCalcTableView.java       ├ 全部被 lab 同名文件替换
├─ table/OnCellEditListener.java     │
├─ table/OnCellTapListener.java      │
├─ table/TableColumn.java            │
├─ table/TableColumnConfig.java      │
├─ table/TableMetrics.java           ┘
└─ utils/ScreenUtils.java            （仅旧 MixCalcActivity 引用）
```

> `mixcalc.model.FeedData`、`mixcalc.table.*`、`mixcalc.utils.ScreenUtils` 在包外零引用（已全量 grep 确认），删了不影响其他模块。

### 2. 整包拷入（34 个 java，覆盖同名）

```
mixcalc/model/  Batch, FeedData, RowSettlement, ShedCoverage, ShedDeletePlan,
                ShedDisplay, ShedDraft, ShedSnapshotAlignment, ShedText
mixcalc/prefs/  MixCalcPrefs
mixcalc/table/  CellEditorOverlay, CellPainter, CellStyle, CellTextProvider,
                MixCalcTableView, NextActionGate, NextRowTarget, OnCellEditListener,
                OnCellTapListener, PillButtonView, PinnedFooter, RevealMath,
                ShedTextPainter, TableColumn, TableColumnConfig, TableMetrics, TabletLayout
mixcalc/ui/     AppDialogs, FlowLayout, PillToast, PopupGeometry, WindowWidthWatcher
mixcalc/        MixCalcActivity
```

### 3. 明确不拷

| 文件 | 原因 |
|---|---|
| `com/shrimpfarm/app/BaseActivity.java`（lab 桩） | 会拆掉完整性校验、水印、底部导航、滑动切页 |
| `com/shrimpfarm/app/AssetWebViewActivity.java`（lab 版） | 与现版签名冲突，MainActivity 正在用 |
| `com/shrimpfarm/app/LocaleManager.java` | 与现有 `utils/LocaleHelper` 重复且 prefs 不同（`MixCalcPrefs` vs `app_prefs`），会导致「个人中心切了语言、计算器还是老语言」 |

### 4. 新增

- `mixcalc/AssetPageActivity.java`：lab 的 `AssetWebViewActivity` 改名搬过来（全屏 + insets + 开 JS + `en/` 前缀），**只给计算器帮助页用**，不动老的 `AssetWebViewActivity`。
- `res/layout/activity_asset_page.xml`：lab `activity_asset_webview.xml` 改名。

---

## 三、需要改动的行为（7 处）

### ① 底部按钮「帮助」→「去查料」

`MixCalcActivity.getFooterText()`（约 270 行）：

```java
if ("shed_number".equals(columnId)) {
    return getString(R.string.mix_btn_go_check);   // 原：mix_btn_help
}
```

`onFooterClick()`（约 354 行）：

```java
if ("shed_number".equals(columnId)) {
    prepareDataAndNavigate();                       // 原：showHelpPanel()
}
```

`prepareDataAndNavigate()` 从旧版移植（旧版 936 行），只保留真正有用的部分：

```java
private void prepareDataAndNavigate() {
    Intent intent = new Intent(this, CheckFeedActivity.class);
    intent.putExtra("WATER_PERCENTAGE", globalWaterPercentage);
    startActivity(intent);
    finish();
}
```

> 旧版还塞了 `FEED_DATA_MESSAGE` / `TIMESTAMP` / `SOURCE`，全项目只有写入没有读取，不再生成（原来拼一大段文本的逻辑一并删掉）。
> 「帮助」入口保留在右上角菜单里（`mix_menu_help` → `help/index.html`），不影响。

### ② 保留「批次 + 基础数据完整」校验

在 `onCreate` 最前面（`setContentView` 之前）插入，方法体从旧版 2073–2116 行原样搬：

```java
SharedPreferences appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
currentBatchId = appPrefs.getString("current_batch_id", "");
dbHelper = DatabaseHelper.getInstance(this);
if (currentBatchId.isEmpty()) { showNoBatchDialog(); return; }
if (!isBasicDataComplete()) { showBasicDataIncompleteDialog(); return; }
```

搬入：`isBasicDataComplete()`、`showNoBatchDialog()`、`showBasicDataIncompleteDialog()`，以及 `currentBatchId` / `dbHelper` 两个字段。
依赖的 `BatchManageActivity`、`BasicDataActivity`、`mix_msg_no_batch`、`main_msg_complete_basic_data` 等 shrimpfarm 全都有。

### ③ 去掉「最大输入棚数」

新代码本来就没有这段逻辑，**不移植**即可。具体不搬：

- 旧版 324–332 行：读 `pond_count` → `shedCountLimit` → 写 `shed_count_limit`
- 旧版 725–733 行：超限标红判断

`ShedCoverage.MAX_SHED_NUMBER = 99` 是**棚号**范围上限（"1-99"），不是棚数上限，保留。
prefs 里残留的 `shed_count_limit` / `never_show_download_dialog` 无害，不用清。

### ④ 底部导航

新布局没有 `bottom_nav`，`BaseActivity.getCurrentNavId()` 是抽象方法，必须实现：

```java
@Override
protected int getCurrentNavId() { return R.id.nav_mix; }
```

> **待确认项 A**：是否把旧布局里的 `bottom_nav` 块搬进新布局（见第六节）。

### ⑤ 语言切换改用现有 LocaleHelper

新 Activity 有 6 处引用 lab 的 `LocaleManager`（2892–2947、3058–3059）。改为：

- `utils/LocaleHelper` 补 `public static final String LANG_AUTO/ZH/EN` 和 `getEffectiveLang(Context)`（几十行，纯新增）。
- 计算器语言菜单调用 `LocaleHelper.setLocale(this, lang)` + `recreate()`。

这样全 App 只有一套语言偏好（`app_prefs` / `app_language`），个人中心和计算器不会打架。

### ⑥ BuildConfig

`MixCalcActivity` 用到 `BuildConfig.VERSION_NAME` / `VERSION_CODE`（版本号显示、隐私同意版本）。
shrimpfarm 的 `app/build.gradle` 没开 buildConfig（AGP 8.13 默认关），需要加：

```gradle
buildFeatures { buildConfig true }
```

### ⑦ 隐私同意弹窗

lab 的 `showPrivacyConsentIfNeeded()` 会在首次进入时弹「隐私政策与使用协议」，文案读 `assets/隐私政策与使用协议弹窗.txt` / `assets/en/privacy_consent.txt`，链接跳 `privacy.html` / `user.html`。

> **待确认项 B**：见第六节。

---

## 四、资源清单

### 布局

| 文件 | 动作 |
|---|---|
| `layout/activity_mix_calc.xml` | 用 lab 版替换（含 `status_bar_spacer` / `nav_bar_spacer`） |
| `layout/popup_main_menu.xml` | 新增（shrimpfarm 没有） |
| `layout/activity_asset_page.xml` | 新增（lab `activity_asset_webview.xml` 改名） |
| 其余 5 个共用布局 | 不动（已验证逐字节一致） |

### drawable

新增 `bg_pill_green.xml`、`ic_menu.xml`（lab 有、shrimpfarm 无；其余 5 个已存在且同名同内容）。

### strings

`values/strings.xml` 补 38 个 key：

```
mix_batch_01, mix_batch_02, mix_batch_dialog_title, mix_batch_selected,
mix_btn_got_it, mix_btn_view_help, mix_dialog_switch_done_msg, mix_dialog_switch_done_title,
mix_lang_dialog_title, mix_lang_option_en, mix_lang_option_system, mix_lang_option_zh,
mix_menu_col_op_format, mix_menu_version_format, mix_order_message, mix_order_title,
mix_shed_delete, mix_shed_delete_msg_fallback, mix_shed_delete_msg_mixed,
mix_shed_delete_msg_permanent, mix_shed_delete_title, mix_shed_hint_msg, mix_shed_hint_title,
mix_shed_list_count, mix_shed_list_empty, mix_shed_list_title, mix_shed_menu_more,
mix_shed_menu_title, mix_shed_over_limit, mix_shed_sel_all, mix_shed_sel_invert,
mix_shed_transfer_in, mix_shed_transfer_out, mix_shed_transfer_whole_to_blank,
privacy_btn_agree, privacy_btn_disagree, privacy_consent_load_fail
```

- `mix_btn_help` **不加**（改用已有的 `mix_btn_go_check`）。
- `values-en/strings.xml` 同步补对应英文（lab 的 49 个 key 里除 `app_name` / `mix_btn_help` 全要）。
- 其余 87 个同名 key 值完全一致，不动。

### assets

```
assets/help/         index.html + img1..img6.png
assets/en/help/      index.html + img1..img6.png
```

（`privacy.html` / `user.html` / `en/` 版本按待确认项 B 决定。）

### dimens

无缺口，不动。

---

## 五、Manifest

1. `MixCalcActivity` 补属性（lab 有、shrimpfarm 缺）：

```xml
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|navigation|uiMode|layoutDirection"
android:windowSoftInputMode="adjustResize"
```

（`theme` / `screenOrientation="portrait"` 已有）

2. 新增：

```xml
<activity
    android:name=".mixcalc.AssetPageActivity"
    android:theme="@style/AppTheme.NoWindowBackground"
    android:screenOrientation="behind"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|navigation|uiMode|layoutDirection"
    android:exported="false" />
```

---

## 六、两个待确认项

### A. 底部导航（推荐：保留）

| 方案 | 说明 |
|---|---|
| **A1 保留（推荐）** | 把旧 `activity_mix_calc.xml` 里的 `bottom_nav` 块搬到新布局最底部（`nav_bar_spacer` 之上），`onCreate` 末尾调 `setupBottomNavigation()`。App 里 5 个主页面都有底部导航，计算器突然没有会变成「只能按返回键退出」的死角。代价：表格可视高度少约 56dp，仍可滚动。 |
| A2 不要 | 保持 lab 全屏设计，靠返回键退出。表格高度最充裕，但和 App 其它页面不一致。 |

### B. 隐私同意弹窗（推荐：去掉，菜单链接指向现有页面）

| 方案 | 说明 |
|---|---|
| **B1 去掉（推荐）** | shrimpfarm 现有版本没有这套首次启动同意流程，用户是从应用商店升级上来的，突然弹一次容易困惑；且会引入第二份隐私政策文本。菜单里的「隐私政策 / 用户协议」改为打开现有的 `privacy-policy.html` / `user-agreement.html`（走老的 `AssetWebViewActivity`）。 |
| B2 保留 | 把 lab 的 `隐私政策与使用协议弹窗.txt`、`privacy.html`、`user.html`、`en/` 一起拷进来，按 `VERSION_CODE` 记录同意状态。文本会和 App 现有隐私政策并存两份。 |

---

## 七、执行顺序

1. 建分支 / 打 tag（当前工作区有未提交的行情改动，先提交或 stash）。
2. 删旧 mixcalc 文件 → 拷入 lab 34 个文件 + `AssetPageActivity`。
3. 改 `MixCalcActivity`：①②③④⑤⑦。
4. 改 `LocaleHelper`、`app/build.gradle`。
5. 资源：布局 / drawable / strings / assets。
6. Manifest。
7. 拷测试（19 个类到 `app/src/test/java/com/shrimpfarm/app/mixcalc/...`）。
8. 编译 + 单测。

> 按 AGENTS.md：**不在命令行打包**，出包由你在 Android Studio 完成。

---

## 八、验证清单

### 自动化

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

19 个测试类（model 12 / table 5 / ui 2）应全绿。注意 lab 测试里 `ShedLimitTest` 等是针对棚号区间，不是棚数上限。

### 手动（装 AS 出的包）

1. **旧数据**：升级安装 → 打开计算器，原来的行数据、水量、列显示/冻结状态都在（`saved_data` → `batches_v2` 迁移）。
2. **基础数据校验**：新建一个没填基础数据的批次 → 进计算器弹「请先完善基础数据」→「去设置」跳基础数据页。
3. **无批次**：清掉 `current_batch_id` → 弹「还没有批次」。
4. **去查料**：底部按钮显示「去查料」，点击跳查料页且水量百分比一致。
5. **棚号区间**：`1-20`、`1,3,5`、`1-5,8,10-12` 都能正确展开；棚数合计正确。
6. **棚数上限不再拦截**：填超过 `pond_count` 的棚数不再标红/拦截。
7. **表格**：横竖滚动、单元格编辑、键盘不遮挡表尾、列设置、冻结列、长按表头菜单。
8. **批次**：菜单切批次 01/02，数据互不干扰。
9. **语言**：菜单切英文 → 计算器变英文；去个人中心看语言设置是否同步。
10. **帮助**：菜单「使用帮助」打开 `assets/help/index.html`，图片正常显示，返回不崩。
11. **底部导航**（若选 A1）：5 个 tab 高亮正确，点击可切换。
12. **完整性校验**：非正版包仍弹「非正版」拦截（说明 BaseActivity 没被 lab 桩覆盖）。

---

## 九、风险

| 风险 | 影响 | 处理 |
|---|---|---|
| 误拷 lab 的 `BaseActivity` / `AssetWebViewActivity` | 完整性校验、水印、MainActivity 隐私页全废 | 清单里明确排除，验证项 10/12 覆盖 |
| `recreate()` 切语言后表格重建丢焦点 | 轻微 | 切语言本来就会重建，可接受 |
| 旧 prefs 里 `saved_data` 格式若有用户手工改过 | 迁移异常 | `MixCalcPrefs` 已 try/catch 兜底为空表 |
| 新表格依赖 insets 逻辑与 BaseActivity 水印层叠加 | 状态栏高度可能算错 | 验证项 7 覆盖，必要时在 `setupSystemBarInsets` 里改用 `getWindow().getDecorView()` |
| 发布包 `minifyEnabled true` | 新类被混淆后反射失效（目前无反射） | 跑一次 release 构建确认（你在 AS 里做） |

---

## 十、执行结果（已完成）

### 决策

- **A1 保留底部导航**：`bottom_nav` 已搬进新布局（表格与 `nav_bar_spacer` 之间），`onCreate` 末尾调 `setupBottomNavigation()`，平板模式额外 `enableSwipeNavigation()`。
- **B1 去掉隐私同意弹窗**：`showPrivacyConsentIfNeeded` / `addConsentButton` / `readAssetLines` 整段删除；菜单「隐私政策 / 用户协议」改走现有 `AssetWebViewActivity`，打开 `privacy-policy.html` / `user-agreement.html`。

### 实际改动

| 项 | 结果 |
|---|---|
| 删除旧实现 | `mixcalc/utils/ScreenUtils.java` 删除；`table/*`、`model/FeedData.java`、`MixCalcActivity.java` 全部被新文件覆盖 |
| 新实现 | 33 个 java（model 9 / prefs 1 / table 17 / ui 5 / activity 1）+ 新增 `mixcalc/AssetPageActivity.java` |
| 底部按钮 | `mix_btn_help` → `mix_btn_go_check`；点击走 `prepareDataAndNavigate()` |
| 基础数据校验 | `checkBatchAndBasicData()` + `isBasicDataComplete()` + 两个引导对话框，`onCreate` 最前面执行 |
| 棚数上限 | 未移植（新代码无此逻辑） |
| 语言 | 6 处 `LocaleManager` 改 `LocaleHelper`；`LocaleHelper` 补 `LANG_AUTO/ZH/EN` + `getEffectiveLang()` |
| BuildConfig | `app/build.gradle` 加 `buildFeatures { buildConfig true }` |
| 资源 | `activity_mix_calc.xml`（含底部导航）、`popup_main_menu.xml`、`activity_asset_page.xml`、`bg_pill_green`、`ic_menu`、`assets/help`、`assets/en/help` |
| strings | zh/en 各补 42 个 key（比原计划多 4 个：`mix_menu_*` 由 `popup_main_menu.xml` 引用） |
| Manifest | `MixCalcActivity` 补 `configChanges` + `adjustResize`；注册 `.mixcalc.AssetPageActivity` |
| 测试 | 19 个测试类拷入 `app/src/test/java/com/shrimpfarm/app/mixcalc/` |

### 验证

```
.\gradlew.bat :app:testDebugUnitTest
BUILD SUCCESSFUL
22 个测试类 / 164 个用例 / 0 失败
（含既有 20 个行情用例 + 新计算器 144 个用例）
```

`:app:compileDebugJavaWithJavac` 通过。未执行任何打包命令。

