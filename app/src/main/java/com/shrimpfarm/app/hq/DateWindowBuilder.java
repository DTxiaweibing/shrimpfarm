package com.shrimpfarm.app.hq;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 生成行情翻页的日期列表（升序，最后一天是今天）。
 *
 * <p>默认只看最近 {@code windowDays} 天；本地有更早的行情时往前扩，
 * 但最多扩到 {@code maxHistoryDays} 天，避免异常数据生成成千上万页。</p>
 */
public final class DateWindowBuilder {

    private static final String PATTERN = "yyyy-MM-dd";

    private DateWindowBuilder() {
    }

    public static List<String> build(String today, String localEarliest,
                                     int windowDays, int maxHistoryDays) {
        SimpleDateFormat format = new SimpleDateFormat(PATTERN, Locale.CHINA);
        format.setLenient(false);

        Date todayDate = parse(today, format);
        if (todayDate == null) return new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(todayDate);
        calendar.add(Calendar.DAY_OF_YEAR, -(windowDays - 1));
        String earliest = format.format(calendar.getTime());

        calendar.setTime(todayDate);
        calendar.add(Calendar.DAY_OF_YEAR, -(maxHistoryDays - 1));
        String oldest = format.format(calendar.getTime());

        if (localEarliest != null && !localEarliest.isEmpty()) {
            String local = localEarliest.compareTo(oldest) < 0 ? oldest : localEarliest;
            if (local.compareTo(earliest) < 0) earliest = local;
        }

        List<String> dates = new ArrayList<>();
        Date start = parse(earliest, format);
        if (start == null) return dates;

        Calendar cursor = Calendar.getInstance();
        cursor.setTime(start);
        while (!cursor.getTime().after(todayDate)) {
            dates.add(format.format(cursor.getTime()));
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
        return dates;
    }

    private static Date parse(String date, SimpleDateFormat format) {
        if (date == null || date.isEmpty()) return null;
        try {
            return format.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }
}
