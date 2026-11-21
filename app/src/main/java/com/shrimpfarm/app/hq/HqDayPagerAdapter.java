package com.shrimpfarm.app.hq;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

/** 按日期建页；日期列表变化时用日期哈希做稳定 id，避免整页重建。 */
public class HqDayPagerAdapter extends FragmentStateAdapter {

    private final List<String> dates = new ArrayList<>();

    public HqDayPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setDates(List<String> list) {
        dates.clear();
        if (list != null) dates.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return HqDayFragment.newInstance(dates.get(position));
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    @Override
    public long getItemId(int position) {
        return dates.get(position).hashCode();
    }

    @Override
    public boolean containsItem(long itemId) {
        for (String date : dates) {
            if (date.hashCode() == itemId) return true;
        }
        return false;
    }
}
