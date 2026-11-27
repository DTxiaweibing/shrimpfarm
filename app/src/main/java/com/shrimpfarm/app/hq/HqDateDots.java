package com.shrimpfarm.app.hq;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** 行情日历纯逻辑：本地有数据的日期打绿点，翻页窗口外的日期不可选。 */
final class HqDateDots {

    static final class Cell {
        final String date;
        final boolean hasData;
        final boolean selectable;

        Cell(String date, boolean hasData, boolean selectable) {
            this.date = date;
            this.hasData = hasData;
            this.selectable = selectable;
        }
    }

    private HqDateDots() {
    }

    static Set<String> toSet(List<String> dates) {
        Set<String> set = new HashSet<>();
        if (dates == null) return set;
        for (String date : dates) {
            if (date != null && !date.isEmpty()) set.add(date);
        }
        return set;
    }

    /** Material 的 month 是 0 基的，转成 yyyy-MM-dd 才能和本地库日期对上。 */
    static boolean hasDot(Set<String> dates, int year, int month0Based, int day) {
        return dates.contains(String.format(Locale.US, "%04d-%02d-%02d", year, month0Based + 1, day));
    }

    /** TextView.setCompoundDrawables 不会按 intrinsic size 设 bounds，必须显式给出非零边界。 */
    static int[] dotBounds(int dotPx) {
        return new int[]{0, 0, dotPx, dotPx};
    }

    /** 同步未落库时不能对本地库取绿点快照，否则会漏掉正在补的日期。 */
    static boolean canReadDots(int pendingSyncs, boolean hasDates) {
        return hasDates && pendingSyncs == 0;
    }

    /** MaterialDatePicker 内部用 UTC 日历，日期字符串转毫秒必须走 UTC。 */
    static long toUtcMillis(String date) {
        if (date == null || date.length() != 10) return 0L;
        ParsePosition position = new ParsePosition(0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        Date parsed = format.parse(date, position);
        if (parsed == null || position.getIndex() != date.length()) return 0L;
        return parsed.getTime();
    }

    /** UTC 毫秒回转 yyyy-MM-dd，和 MaterialDatePicker 的选择结果配套。 */
    static String fromUtcMillis(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    static List<Cell> month(int year, int month, Set<String> dataDates,
                            String minDate, String maxDate) {
        Calendar cal = new GregorianCalendar(year, month - 1, 1);
        int leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int size = ((leading + days + 6) / 7) * 7;
        List<Cell> cells = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int day = i - leading + 1;
            if (day < 1 || day > days) {
                cells.add(new Cell(null, false, false));
                continue;
            }
            String date = String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
            boolean selectable = inRange(date, minDate, maxDate);
            cells.add(new Cell(date, selectable && dataDates.contains(date), selectable));
        }
        return cells;
    }

    private static boolean inRange(String date, String minDate, String maxDate) {
        if (minDate != null && date.compareTo(minDate) < 0) return false;
        return maxDate == null || date.compareTo(maxDate) <= 0;
    }
}
