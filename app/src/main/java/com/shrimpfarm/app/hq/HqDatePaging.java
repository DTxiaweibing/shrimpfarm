package com.shrimpfarm.app.hq;

/**
 * 远端日期列表的游标分页纯逻辑。
 *
 * <p>price_records 一天有几十个品种行，PostgREST 单页上限 1000 行，
 * 用日期游标翻页才能拿全窗口内的日期，而不是只拿到最前面十几天。</p>
 */
public final class HqDatePaging {

    static final int PAGE_SIZE = 1000;
    static final int MAX_PAGES = 100;

    private HqDatePaging() {
    }

    /** 首页用 gte 含起点，后续页用 gt 跳过已收完的日期。 */
    static String lowerOp(String cursor) {
        return cursor == null ? "gte." : "gt.";
    }

    static String lowerBound(String fromDate, String cursor) {
        return cursor == null ? fromDate : cursor;
    }

    /** 游标没有前进就停止，避免服务端忽略 gt 条件时死循环。 */
    static String nextCursor(String cursor, String pageMax) {
        if (pageMax == null || pageMax.isEmpty()) return null;
        if (cursor != null && pageMax.compareTo(cursor) <= 0) return null;
        return pageMax;
    }

    static boolean shouldContinue(int rowsFetched, int pagesFetched) {
        return rowsFetched == PAGE_SIZE && pagesFetched < MAX_PAGES;
    }
}
