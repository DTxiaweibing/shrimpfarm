package com.shrimpfarm.app.hq;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TypedValue;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DayViewDecorator;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.shrimpfarm.app.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/** Material 原生日历 + 有数据日期下方的绿点，不自建格子。 */
public final class HqDatePickerDialog {

    public interface OnDateSelected {
        void onDateSelected(String date);
    }

    private HqDatePickerDialog() {
    }

    public static void show(FragmentActivity activity, Set<String> dataDates,
                            String minDate, String maxDate, String selectedDate,
                            OnDateSelected listener) {
        long start = HqDateDots.toUtcMillis(minDate);
        long end = HqDateDots.toUtcMillis(maxDate);
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_date)
                .setSelection(HqDateDots.toUtcMillis(selectedDate))
                .setCalendarConstraints(constraints(start, end))
                .setDayViewDecorator(new DotDecorator(activity, dataDates))
                .build();
        picker.addOnPositiveButtonClickListener(selection ->
                listener.onDateSelected(HqDateDots.fromUtcMillis(selection)));
        picker.show(activity.getSupportFragmentManager(), "hq_date");
    }

    private static CalendarConstraints constraints(long start, long end) {
        return new CalendarConstraints.Builder()
                .setStart(start)
                .setEnd(end)
                .setFirstDayOfWeek(Calendar.MONDAY)
                .setValidator(new RangeValidator(start, end))
                .build();
    }

    /** Parcelable 的日期区间校验：只在可翻页窗口内可选。 */
    private static final class RangeValidator implements CalendarConstraints.DateValidator {
        public static final Parcelable.Creator<RangeValidator> CREATOR =
                new Parcelable.Creator<RangeValidator>() {
                    @Override
                    public RangeValidator createFromParcel(Parcel source) {
                        return new RangeValidator(source.readLong(), source.readLong());
                    }

                    @Override
                    public RangeValidator[] newArray(int size) {
                        return new RangeValidator[size];
                    }
                };

        private final long start;
        private final long end;

        RangeValidator(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean isValid(long date) {
            return date >= start && date <= end;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(start);
            dest.writeLong(end);
        }
    }

    /** Material 传入的 month 是 0 基的，转回 yyyy-MM-dd 才能和绿点日期对上。 */
    private static final class DotDecorator extends DayViewDecorator {
        public static final Parcelable.Creator<DotDecorator> CREATOR =
                new Parcelable.Creator<DotDecorator>() {
                    @Override
                    public DotDecorator createFromParcel(Parcel source) {
                        List<String> dates = new ArrayList<>();
                        source.readStringList(dates);
                        return new DotDecorator(dates, source.readInt());
                    }

                    @Override
                    public DotDecorator[] newArray(int size) {
                        return new DotDecorator[size];
                    }
                };

        private final Set<String> dates;
        private final int dotPx;

        DotDecorator(Context context, Set<String> dates) {
            this(new ArrayList<>(dates), Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 6f, context.getResources().getDisplayMetrics())));
        }

        private DotDecorator(List<String> dates, int dotPx) {
            this.dates = HqDateDots.toSet(dates);
            this.dotPx = dotPx;
        }

        @Override
        public Drawable getCompoundDrawableBottom(Context context, int year, int month,
                                                  int day, boolean valid, boolean selected) {
            if (!HqDateDots.hasDot(dates, year, month, day)) return null;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setSize(dotPx, dotPx);
            dot.setColor(Color.parseColor("#4CAF50"));
            // setCompoundDrawables 不像 WithIntrinsicBounds 那样自动设 bounds；
            // 不设边界 drawable 就是 (0,0,0,0)，绿点完全画不出来。
            int[] bounds = HqDateDots.dotBounds(dotPx);
            dot.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
            return dot;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeStringList(new ArrayList<>(dates));
            dest.writeInt(dotPx);
        }
    }
}
