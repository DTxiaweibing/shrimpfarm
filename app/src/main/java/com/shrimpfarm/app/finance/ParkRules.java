package com.shrimpfarm.app.finance;

/** Pure rules for the A14 park archive: names are required, receiving accounts are optional. */
public final class ParkRules {

    private ParkRules() {
    }

    public static String normalizedName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static boolean validName(String raw) {
        return !normalizedName(raw).isEmpty();
    }

    public static String accountSummary(String accountName, String accountNo) {
        String name = normalizedName(accountName);
        String no = normalizedName(accountNo);
        if (name.isEmpty()) return no;
        if (no.isEmpty()) return name;
        return name + " " + no;
    }
}
