package com.shrimpfarm.app;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.shrimpfarm.app.utils.DialogHelper;

public class BasicDataActivity extends BaseActivity {

    private TextView tabBasic, tabMix, tabWater;
    private View contentBasic, contentMix, contentWater;
    private ListView lvMix, lvWater;
    private PresetAdapter mixAdapter, waterAdapter;

    private EditText etSeedQuantity, etSeedBrand, etFeedBrand, etPondCount, etPondLength, etAeratorCount, etAerationPower;
    private TextView tvStockingDate, tvWaterPrepDate;

    private DatabaseHelper dbHelper;
    private String currentBatchId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_data);

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentBatchId = prefs.getString("current_batch_id", "");
        if (currentBatchId.isEmpty()) {
            showNoBatchDialog();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        initViews();
        setupTabs();
        setupBasicDataTab();
        setupPresetTabs();

        int openTab = getIntent().getIntExtra("open_tab", -1);
        if (openTab != -1) {
            selectTab(openTab);
        } else {
            selectTab(0);
        }
        setupBottomNavigation();
    }

    private void showNoBatchDialog() {
        showStyledConfirmDialog("提示", "请先在批次管理中创建至少一个批次",
            new String[]{"退出", "去创建"},
            new int[]{0xFF666666, 0xFF4CAF50},
            new DialogInterface.OnClickListener[]{
                (dialog, which) -> finish(),
                (dialog, which) -> {
                    startActivity(new Intent(BasicDataActivity.this, BatchManageActivity.class));
                    finish();
                }
            });
    }

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_record;
    }

    private void initViews() {
        tabBasic = findViewById(R.id.tab_basic);
        tabMix = findViewById(R.id.tab_mix);
        tabWater = findViewById(R.id.tab_water);

        FrameLayout contentFrame = findViewById(R.id.content_frame);
        LayoutInflater inflater = LayoutInflater.from(this);
        contentBasic = inflater.inflate(R.layout.tab_basic_data, contentFrame, false);
        contentMix = inflater.inflate(R.layout.tab_preset_list, contentFrame, false);
        contentWater = inflater.inflate(R.layout.tab_preset_list, contentFrame, false);

        etSeedQuantity = contentBasic.findViewById(R.id.et_seed_quantity);
        etSeedBrand = contentBasic.findViewById(R.id.et_seed_brand);
        etFeedBrand = contentBasic.findViewById(R.id.et_feed_brand);
        tvStockingDate = contentBasic.findViewById(R.id.tv_stocking_date);
        tvWaterPrepDate = contentBasic.findViewById(R.id.tv_water_prep_date);
        etPondCount = contentBasic.findViewById(R.id.et_pond_count);
        etPondLength = contentBasic.findViewById(R.id.et_pond_length);
        etAeratorCount = contentBasic.findViewById(R.id.et_aerator_count);
        etAerationPower = contentBasic.findViewById(R.id.et_aeration_power);

        lvMix = contentMix.findViewById(R.id.list_view);
        lvWater = contentWater.findViewById(R.id.list_view);
    }

    private void setupTabs() {
        tabBasic.setOnClickListener(v -> selectTab(0));
        tabMix.setOnClickListener(v -> selectTab(1));
        tabWater.setOnClickListener(v -> selectTab(2));
    }

    private boolean isWaterPrepDateSet() {
        String date = tvWaterPrepDate.getText().toString().trim();
        return !date.isEmpty() && !date.equals("选择日期");
    }

    private void selectTab(int index) {
        if (index != 0 && !isWaterPrepDateSet()) {
            showStyledConfirmDialog("提示", "请先在基础数据中设置做水日",
                    new String[]{"确定"}, null, null);
            return;
        }

        tabBasic.setBackgroundResource(R.drawable.bg_tab_style);
        tabMix.setBackgroundResource(R.drawable.bg_tab_style);
        tabWater.setBackgroundResource(R.drawable.bg_tab_style);

        tabBasic.setTextColor(index == 0 ? 0xFF2d8c42 : 0xFF666666);
        tabMix.setTextColor(index == 1 ? 0xFF2d8c42 : 0xFF666666);
        tabWater.setTextColor(index == 2 ? 0xFF2d8c42 : 0xFF666666);

        FrameLayout contentFrame = findViewById(R.id.content_frame);
        contentFrame.removeAllViews();
        if (index == 0) {
            contentFrame.addView(contentBasic);
        } else if (index == 1) {
            contentFrame.addView(contentMix);
        } else {
            contentFrame.addView(contentWater);
        }
    }

    private void setupBasicDataTab() {
        etSeedQuantity.setText(dbHelper.getBasicData(currentBatchId, "seed_quantity"));
        etSeedBrand.setText(dbHelper.getBasicData(currentBatchId, "seed_brand"));
        etFeedBrand.setText(dbHelper.getBasicData(currentBatchId, "feed_brand"));
        String savedDate = dbHelper.getBasicData(currentBatchId, "stocking_date");
        tvStockingDate.setText(savedDate.isEmpty() ? "选择日期" : savedDate);
        etPondCount.setText(dbHelper.getBasicData(currentBatchId, "pond_count"));
        etPondLength.setText(dbHelper.getBasicData(currentBatchId, "pond_length"));
        etAeratorCount.setText(dbHelper.getBasicData(currentBatchId, "aerator_count"));
        etAerationPower.setText(dbHelper.getBasicData(currentBatchId, "aeration_power"));

        tvStockingDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(BasicDataActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        String selectedDate = String.format(java.util.Locale.ROOT, "%d/%02d/%02d", year, month + 1, dayOfMonth);
                        tvStockingDate.setText(selectedDate);
                        saveBasicData();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        String savedWaterPrep = dbHelper.getBasicData(currentBatchId, "water_prep_date");
        boolean hasWaterPrep = !savedWaterPrep.isEmpty() && !"选择日期".equals(savedWaterPrep);
        tvWaterPrepDate.setText(hasWaterPrep ? savedWaterPrep : "选择日期");

        if (hasWaterPrep) {
            tvWaterPrepDate.setTextColor(0xFFAAAAAA);
            tvWaterPrepDate.setClickable(false);
            tvWaterPrepDate.setFocusable(false);
        } else {
            tvWaterPrepDate.setTextColor(0xFF4CAF50);
            tvWaterPrepDate.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                new DatePickerDialog(BasicDataActivity.this,
                        (view, year, month, dayOfMonth) -> {
                            String selectedDate = String.format(java.util.Locale.ROOT, "%d/%02d/%02d", year, month + 1, dayOfMonth);
                            tvWaterPrepDate.setText(selectedDate);
                            tvWaterPrepDate.setTextColor(0xFFAAAAAA);
                            tvWaterPrepDate.setClickable(false);
                            tvWaterPrepDate.setFocusable(false);
                            saveBasicData();
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)).show();
            });
        }

        TextWatcher autoSaveWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveBasicData(); }
        };

        etSeedQuantity.addTextChangedListener(autoSaveWatcher);
        etSeedBrand.addTextChangedListener(autoSaveWatcher);
        etFeedBrand.addTextChangedListener(autoSaveWatcher);
        etPondCount.addTextChangedListener(autoSaveWatcher);
        etPondLength.addTextChangedListener(autoSaveWatcher);
        etAeratorCount.addTextChangedListener(autoSaveWatcher);
        etAerationPower.addTextChangedListener(autoSaveWatcher);
    }

    private void saveBasicData() {
        dbHelper.saveBasicData(currentBatchId, "seed_quantity", etSeedQuantity.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "seed_brand", etSeedBrand.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "feed_brand", etFeedBrand.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "stocking_date", tvStockingDate.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "pond_count", etPondCount.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "pond_length", etPondLength.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "aerator_count", etAeratorCount.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "aeration_power", etAerationPower.getText().toString().trim());
        dbHelper.saveBasicData(currentBatchId, "water_prep_date", tvWaterPrepDate.getText().toString().trim());
    }

    private void setupPresetTabs() {
        mixAdapter = new PresetAdapter(true);
        waterAdapter = new PresetAdapter(false);
        lvMix.setAdapter(mixAdapter);
        lvWater.setAdapter(waterAdapter);
    }

    // ==================== 内部适配器 ====================
    private class PresetAdapter extends BaseAdapter {
        private boolean isMix;
        private List<String> cachedNames = new ArrayList<>();
        private List<String> cachedTags = new ArrayList<>();
        private Handler handler = new Handler(Looper.getMainLooper());
        private Map<Integer, Runnable> foldRunnables = new HashMap<>();

        PresetAdapter(boolean isMix) {
            this.isMix = isMix;
            for (int i = 0; i < 50; i++) {
                String name = isMix ? dbHelper.getMixPresetByRow(currentBatchId, i + 1)
                        : dbHelper.getWaterPresetByRow(currentBatchId, i + 1);
                String tags = isMix ? dbHelper.getMixPresetTags(currentBatchId, i + 1)
                        : dbHelper.getWaterPresetTags(currentBatchId, i + 1);
                // 去除前缀后存入缓存
                String pureName = (name == null) ? "" : DatabaseHelper.removeUsagePrefix(name);
                String pureTags = (tags == null) ? "" : removePrefixFromTags(tags);
                cachedNames.add(pureName);
                cachedTags.add(pureTags);
            }
        }

        // 辅助：从存储的带前缀标签字符串中提取纯标签（逗号分隔）
        private String removePrefixFromTags(String prefixedTags) {
            if (prefixedTags == null || prefixedTags.isEmpty()) return "";
            String[] parts = prefixedTags.split(",");
            StringBuilder sb = new StringBuilder();
            for (String tag : parts) {
                String pure = DatabaseHelper.removeUsagePrefix(tag);
                if (!pure.isEmpty()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(pure);
                }
            }
            return sb.toString();
        }

        // 辅助：为纯标签字符串加上前缀
        private String addPrefixToTags(String pureTags) {
            if (pureTags == null || pureTags.isEmpty()) return "";
            String prefix = isMix ? "【拌料】" : "【调水】";
            String[] parts = pureTags.split(",");
            StringBuilder sb = new StringBuilder();
            for (String tag : parts) {
                if (!tag.trim().isEmpty()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(prefix).append(tag.trim());
                }
            }
            return sb.toString();
        }

        @Override public int getCount() { return 50; }
        @Override public Object getItem(int position) { return null; }
        @Override public long getItemId(int position) { return position + 1; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(BasicDataActivity.this)
                        .inflate(R.layout.item_preset_row, parent, false);
                holder = new ViewHolder();
                holder.tvRowNumber = convertView.findViewById(R.id.tv_row_number);
                holder.btnTag = convertView.findViewById(R.id.btn_tag);
                holder.etPresetName = convertView.findViewById(R.id.et_preset_name);
                holder.layoutTags = convertView.findViewById(R.id.layout_tags);
                holder.tagsContainer = convertView.findViewById(R.id.tags_container);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final int rowNum = position + 1;
            holder.tvRowNumber.setText(String.format(java.util.Locale.ROOT, "%02d", rowNum));

            Object oldWatcher = holder.etPresetName.getTag();
            if (oldWatcher instanceof TextWatcher) {
                holder.etPresetName.removeTextChangedListener((TextWatcher) oldWatcher);
            }
            holder.etPresetName.setText(cachedNames.get(position));

            boolean hasName = !cachedNames.get(position).isEmpty();
            updateTagButton(holder.btnTag, cachedTags.get(position), hasName);

            if (holder.tagsContainer.getChildCount() == 0) {
                buildTagCheckBoxes(holder, position);
            } else {
                refreshTagCheckBoxes(holder, position);
            }

            // 标签按钮点击：实时获取最新名称
            holder.btnTag.setOnClickListener(v -> {
                String currentName = cachedNames.get(position);
                if (currentName == null || currentName.trim().isEmpty()) {
                    Toast.makeText(BasicDataActivity.this, "请先输入动保名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (holder.layoutTags.getVisibility() == View.VISIBLE) {
                    holder.layoutTags.setVisibility(View.GONE);
                    removeFoldTimer(position);
                } else {
                    holder.layoutTags.setVisibility(View.VISIBLE);
                    scheduleFold(position, holder.layoutTags);
                }
            });

            // 名称输入框监听器
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    String newName = s.toString().trim();
                    cachedNames.set(position, newName);
                    boolean nowHasName = !newName.isEmpty();

                    updateTagButton(holder.btnTag, cachedTags.get(position), nowHasName);

                    if (!nowHasName && holder.layoutTags.getVisibility() == View.VISIBLE) {
                        holder.layoutTags.setVisibility(View.GONE);
                        removeFoldTimer(position);
                    }

                    // 相似度检查（仅针对纯名称）
                    if (!newName.isEmpty()) {
                        for (int i = 0; i < cachedNames.size(); i++) {
                            if (i != position && !cachedNames.get(i).isEmpty()) {
                                double similarity = calculateSimilarity(newName, cachedNames.get(i));
                                if (similarity >= 1.0) {
                                    holder.etPresetName.setText("");
                                    cachedNames.set(position, "");
                                    showStyledConfirmDialog("提示", "相似度过高禁止输入",
                                            new String[]{"确定"}, null, null);
                                    return;
                                }
                            }
                        }
                    }
                    saveCurrentRow(rowNum, newName, cachedTags.get(position));
                }
            };
            holder.etPresetName.setTag(watcher);
            holder.etPresetName.addTextChangedListener(watcher);

            // 重置标签面板可见性（避免复用错乱）
            holder.layoutTags.setVisibility(View.GONE);
            return convertView;
        }

        private void scheduleFold(int position, View layoutTags) {
            removeFoldTimer(position);
            Runnable runnable = () -> {
                layoutTags.setVisibility(View.GONE);
                foldRunnables.remove(position);
            };
            foldRunnables.put(position, runnable);
            handler.postDelayed(runnable, 8000);
        }

        private void removeFoldTimer(int position) {
            Runnable exist = foldRunnables.remove(position);
            if (exist != null) handler.removeCallbacks(exist);
        }

        private void buildTagCheckBoxes(ViewHolder holder, int position) {
            holder.tagsContainer.removeAllViews();
            String[] tagArray = isMix ?
                    new String[]{"护肠类","保肝类","中药排毒类","营养类","防治弧菌","补钙","诱食"} :
                    new String[]{"解毒类","抗应激类","营养类","改底类","有益菌类","藻种","调pH","补硬度","补碱度","防治弧菌","保肝类","增氧","遮光控藻"};
            Set<String> selectedTags = loadSelectedTags(position);
            List<String> allTags = new ArrayList<>(Arrays.asList(tagArray));
            String savedPureTags = cachedTags.get(position);
            if (!TextUtils.isEmpty(savedPureTags)) {
                for (String tag : savedPureTags.split(",")) {
                    if (!allTags.contains(tag)) {
                        allTags.add(tag);
                    }
                }
            }

            final EditText etPresetName = holder.etPresetName;

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int availableWidth = screenWidth - dpToPx(20);
            int tagWidth = dpToPx(80);
            int maxPerRow = Math.max(3, availableWidth / tagWidth);

            LinearLayout row = null;
            int countInRow = 0;
            for (int i = 0; i < allTags.size(); i++) {
                if (countInRow == 0 || countInRow >= maxPerRow) {
                    row = new LinearLayout(BasicDataActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    holder.tagsContainer.addView(row);
                    countInRow = 0;
                }
                CheckBox cb = new CheckBox(BasicDataActivity.this);
                cb.setText(allTags.get(i));
                cb.setTag(allTags.get(i));
                cb.setChecked(selectedTags.contains(allTags.get(i)));
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked && etPresetName.getText().toString().trim().isEmpty()) {
                        Toast.makeText(BasicDataActivity.this, "请先输入动保名称", Toast.LENGTH_SHORT).show();
                        ((CheckBox) buttonView).setChecked(false);
                        return;
                    }
                    String tagName = (String) buttonView.getTag();
                    if (isChecked) selectedTags.add(tagName);
                    else selectedTags.remove(tagName);
                    String newPureTags = TextUtils.join(",", selectedTags);
                    cachedTags.set(position, newPureTags);
                    updateTagButton(holder.btnTag, newPureTags, !etPresetName.getText().toString().trim().isEmpty());
                    saveCurrentRow(position + 1, cachedNames.get(position), newPureTags);
                    removeFoldTimer(position);
                    scheduleFold(position, holder.layoutTags);
                });
                row.addView(cb);
                countInRow++;
            }

            addCustomButton(holder, position, selectedTags, maxPerRow);
        }

        private void addCustomButton(ViewHolder holder, int position, Set<String> selectedTags, int maxPerRow) {
            Button btnCustom = new Button(BasicDataActivity.this);
            btnCustom.setText("+自定义");
            btnCustom.setTextSize(12);
            btnCustom.setOnClickListener(v -> {
                if (holder.etPresetName.getText().toString().trim().isEmpty()) {
                    Toast.makeText(BasicDataActivity.this, "请先输入动保名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                removeFoldTimer(position);
                scheduleFold(position, holder.layoutTags);
                showCustomTagDialog(position, selectedTags);
            });
            int childCount = holder.tagsContainer.getChildCount();
            if (childCount > 0) {
                LinearLayout lastRow = (LinearLayout) holder.tagsContainer.getChildAt(childCount - 1);
                if (lastRow.getChildCount() < maxPerRow) {
                    lastRow.addView(btnCustom);
                    return;
                }
            }
            LinearLayout newRow = new LinearLayout(BasicDataActivity.this);
            newRow.setOrientation(LinearLayout.HORIZONTAL);
            newRow.addView(btnCustom);
            holder.tagsContainer.addView(newRow);
        }

        private void refreshTagCheckBoxes(ViewHolder holder, int position) {
            // 重建整个标签复选框区域，保持与当前缓存一致
            buildTagCheckBoxes(holder, position);
        }

        private Set<String> loadSelectedTags(int position) {
            Set<String> selectedTags = new HashSet<>();
            String savedPureTags = cachedTags.get(position);
            if (!savedPureTags.isEmpty()) {
                Collections.addAll(selectedTags, savedPureTags.split(","));
            }
            return selectedTags;
        }

        private void showCustomTagDialog(int position, Set<String> selectedTags) {
            final EditText[] inputHolder = new EditText[1];
            inputHolder[0] = DialogHelper.showStyledInputDialog(
                BasicDataActivity.this,
                "添加自定义标签",
                "请输入标签名",
                null,
                new String[]{"取消", "确定"},
                new DialogInterface.OnClickListener[]{
                    null,
                    (dialog, which) -> {
                        String customTag = inputHolder[0].getText().toString().trim();
                        if (!customTag.isEmpty()) {
                            selectedTags.add(customTag);
                            String newPureTags = TextUtils.join(",", selectedTags);
                            cachedTags.set(position, newPureTags);
                            View view = lvMix.getChildAt(position - lvMix.getFirstVisiblePosition());
                            if (view != null && view.getTag() instanceof ViewHolder) {
                                ViewHolder h = (ViewHolder) view.getTag();
                                buildTagCheckBoxes(h, position);
                            }
                            saveCurrentRow(position + 1, cachedNames.get(position), newPureTags);
                        }
                    }
                }
            );
        }

        private void updateTagButton(Button btn, String tags, boolean hasName) {
    btn.setTextSize(14);	
    String prefix = isMix ? "拌料动保" : "调水动保";
    if (!hasName) {
        btn.setText(getString(R.string.tag_btn_dot, prefix));
        btn.setTextColor(0xFF000000);
        btn.setBackgroundResource(R.drawable.bg_spinner);
        btn.setEnabled(false);
        btn.setAlpha(0.5f);
    } else if (tags == null || tags.isEmpty()) {
        btn.setText(getString(R.string.tag_btn_mid_dot, prefix));
        btn.setTextColor(0xFF000000);               // 黑色文字
        btn.setBackgroundResource(R.drawable.bg_spinner);
        btn.setEnabled(true);
        btn.setAlpha(1.0f);
    } else {
        btn.setText(getString(R.string.tag_btn_dot, prefix));
        btn.setTextColor(0xFF2D8C42);
        btn.setBackgroundResource(R.drawable.bg_button_secondary);
        btn.setEnabled(true);
        btn.setAlpha(1.0f);
    }
}

        private void saveCurrentRow(int rowNum, String name, String pureTags) {
            // name 是纯名称，保存时加上前缀
            String prefixedName = name.isEmpty() ? "" : (isMix ? "【拌料】" : "【调水】") + name;
            // tags 保存时需要加前缀
            String prefixedTags = addPrefixToTags(pureTags);
            if (isMix) {
                dbHelper.saveMixPreset(currentBatchId, rowNum, prefixedName, prefixedTags);
            } else {
                dbHelper.saveWaterPreset(currentBatchId, rowNum, prefixedName, prefixedTags);
            }
        }

        private int dpToPx(int dp) {
            return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
        }

        class ViewHolder {
            TextView tvRowNumber;
            Button btnTag;
            EditText etPresetName;
            LinearLayout layoutTags;
            LinearLayout tagsContainer;
        }

        private double calculateSimilarity(String s1, String s2) {
            if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return 0;
            String str1 = s1.toLowerCase(java.util.Locale.ROOT);
            String str2 = s2.toLowerCase(java.util.Locale.ROOT);
            if (str1.equals(str2)) return 1.0;

            java.util.HashMap<Character, Integer> count1 = new java.util.HashMap<>();
            java.util.HashMap<Character, Integer> count2 = new java.util.HashMap<>();
            int len1 = 0, len2 = 0;

            for (char c : str1.toCharArray()) {
                count1.put(c, count1.getOrDefault(c, 0) + 1);
                len1++;
            }
            for (char c : str2.toCharArray()) {
                count2.put(c, count2.getOrDefault(c, 0) + 1);
                len2++;
            }

            int match = 0;
            for (char c : count1.keySet()) {
                if (count2.containsKey(c)) {
                    match += Math.min(count1.get(c), count2.get(c));
                }
            }

            int maxLen = Math.max(len1, len2);
            return (double) match / maxLen;
        }
    }
}