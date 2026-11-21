package com.shrimpfarm.app.hq;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PriceData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 无锡行情。一天一页，左滑看更晚、右滑看更早。
 *
 * <p>页范围是最近 {@link #WINDOW_DAYS} 天（本地有更早数据时自动往前扩），
 * 每页先读本地缓存，没有就按日期查远端。未来日期不生成页。</p>
 */
public class HqActivity extends BaseActivity {

    private static final int WINDOW_DAYS = 90;
    private static final int MAX_HISTORY_DAYS = 365;
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private TextView tvDate;
    private TextView tvBackToday;
    private TextView btnPrev;
    private TextView btnNext;
    private ViewPager2 pager;
    private HqDayPagerAdapter pagerAdapter;
    private DatabaseHelper dbHelper;

    private final List<String> dates = new ArrayList<>();
    private boolean userDragged;

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hq);

        dbHelper = DatabaseHelper.getInstance(this);
        tvDate = findViewById(R.id.tv_date);
        tvBackToday = findViewById(R.id.tv_back_today);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        pager = findViewById(R.id.pager_market);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        pagerAdapter = new HqDayPagerAdapter(this);
        pager.setAdapter(pagerAdapter);
        pager.setOffscreenPageLimit(1);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateHeader(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) userDragged = true;
            }
        });

        btnPrev.setOnClickListener(v -> step(-1));
        btnNext.setOnClickListener(v -> step(1));
        tvBackToday.setOnClickListener(v -> {
            if (!dates.isEmpty()) pager.setCurrentItem(dates.size() - 1, true);
        });
        btnRefresh.setOnClickListener(v -> fetchLatest(true));

        List<String> localDates = dbHelper.getMarketPriceDates();
        String initialDate = localDates.isEmpty() ? null : localDates.get(localDates.size() - 1);
        rebuildDates(initialDate);
        fetchLatest(false);
    }

    /** 页范围：最近 WINDOW_DAYS 天；本地有更早的行情就继续往前扩。 */
    private List<String> buildDateWindow() {
        String today = dateFormat().format(new Date());
        List<String> localDates = dbHelper.getMarketPriceDates();
        String localEarliest = localDates.isEmpty() ? null : localDates.get(0);
        return DateWindowBuilder.build(today, localEarliest, WINDOW_DAYS, MAX_HISTORY_DAYS);
    }

    /** 重建日期列表；preferredDate 为空时尽量停在当前这一天。 */
    private void rebuildDates(String preferredDate) {
        String keep = preferredDate != null ? preferredDate : currentDate();

        dates.clear();
        dates.addAll(buildDateWindow());
        pagerAdapter.setDates(dates);

        if (dates.isEmpty()) {
            updateHeader(0);
            return;
        }

        int target = dates.size() - 1;
        if (keep != null) {
            int index = dates.indexOf(keep);
            if (index >= 0) target = index;
        }
        pager.setCurrentItem(Math.max(0, target), false);
        updateHeader(pager.getCurrentItem());
    }

    private String currentDate() {
        if (dates.isEmpty()) return null;
        int index = Math.max(0, Math.min(pager.getCurrentItem(), dates.size() - 1));
        return dates.get(index);
    }

    private void updateHeader(int position) {
        if (dates.isEmpty()) {
            tvDate.setText(R.string.hq_label_date_awaiting);
            return;
        }

        int index = Math.max(0, Math.min(position, dates.size() - 1));
        String date = dates.get(index);
        String suffix = dateSuffix(date);
        tvDate.setText(suffix.isEmpty()
                ? date
                : getString(R.string.hq_date_with_suffix, date, suffix));

        boolean hasPrev = index > 0;
        boolean hasNext = index < dates.size() - 1;
        setArrowEnabled(btnPrev, hasPrev);
        setArrowEnabled(btnNext, hasNext);
        tvBackToday.setVisibility(hasNext ? View.VISIBLE : View.GONE);
    }

    private void setArrowEnabled(TextView arrow, boolean enabled) {
        arrow.setEnabled(enabled);
        arrow.setAlpha(enabled ? 1f : 0.3f);
    }

    private String dateSuffix(String date) {
        SimpleDateFormat fmt = dateFormat();
        String today = fmt.format(new Date());
        if (today.equals(date)) return getString(R.string.hq_date_today);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        if (fmt.format(calendar.getTime()).equals(date)) return getString(R.string.hq_date_yesterday);
        return "";
    }

    private void step(int delta) {
        if (dates.isEmpty()) return;
        int target = pager.getCurrentItem() + delta;
        if (target < 0) {
            Toast.makeText(this, R.string.hq_toast_earliest, Toast.LENGTH_SHORT).show();
            return;
        }
        if (target >= dates.size()) {
            Toast.makeText(this, R.string.hq_toast_latest, Toast.LENGTH_SHORT).show();
            return;
        }
        pager.setCurrentItem(target, true);
    }

    private void fetchLatest(boolean userInitiated) {
        if (userInitiated) {
            userDragged = false;
            tvDate.setText(R.string.hq_label_date_fetching);
        }

        HqCrawler.fetchLatest(this, new HqCrawler.OnDataCallback() {
            @Override
            public void onSuccess(PriceData data) {
                if (isFinishing()) return;
                if (data == null || data.date == null) {
                    updateHeader(pager.getCurrentItem());
                    return;
                }
                dbHelper.saveMarketPrices(data);
                rebuildDates(userDragged ? null : data.date);
                refreshDayFragment(data.date);
            }

            @Override
            public void onError(String msg) {
                if (!userInitiated || isFinishing()) return;
                Toast.makeText(HqActivity.this, msg, Toast.LENGTH_LONG).show();
                updateHeader(pager.getCurrentItem());
            }
        });
    }

    /** 远端回填/刷新后，让正在显示该日期的页重新读一次本地。 */
    private void refreshDayFragment(String date) {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof HqDayFragment) {
                HqDayFragment day = (HqDayFragment) fragment;
                if (date.equals(day.getDate())) day.reload();
            }
        }
    }

    private SimpleDateFormat dateFormat() {
        return new SimpleDateFormat(DATE_PATTERN, Locale.CHINA);
    }
}
