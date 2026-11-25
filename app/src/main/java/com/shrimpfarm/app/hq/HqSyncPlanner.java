package com.shrimpfarm.app.hq;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure logic for deciding which daily market dates need backfill sync.
 * No Android / network dependency so it can be unit tested in isolation.
 */
public final class HqSyncPlanner {

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private HqSyncPlanner() {
    }

    /**
     * Returns dates that exist remotely, fall inside the window ending at
     * {@code today}, and are missing locally. Result is sorted ascending.
     */
    public static List<String> missingDates(String today, List<String> localDates,
                                            List<String> remoteDates, int windowDays) {
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.US);
        format.setLenient(false);
        Date todayDate = parse(format, today);
        if (todayDate == null || windowDays <= 0) {
            return Collections.emptyList();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(todayDate);
        calendar.add(Calendar.DAY_OF_MONTH, -(windowDays - 1));
        Date windowStart = calendar.getTime();

        Set<String> local = normalize(format, localDates);
        Set<String> remote = new TreeSet<String>();
        if (remoteDates != null) {
            for (String date : remoteDates) {
                Date parsed = parse(format, date);
                if (parsed != null && !parsed.before(windowStart) && !parsed.after(todayDate)) {
                    remote.add(date);
                }
            }
        }
        remote.removeAll(local);
        return new ArrayList<String>(remote);
    }

    private static Set<String> normalize(SimpleDateFormat format, List<String> dates) {
        Set<String> result = new TreeSet<String>();
        if (dates != null) {
            for (String date : dates) {
                if (parse(format, date) != null) {
                    result.add(date);
                }
            }
        }
        return result;
    }

    private static Date parse(SimpleDateFormat format, String value) {
        if (value == null) {
            return null;
        }
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        if (parsed == null || position.getIndex() != value.length()) {
            return null;
        }
        return parsed;
    }
}
