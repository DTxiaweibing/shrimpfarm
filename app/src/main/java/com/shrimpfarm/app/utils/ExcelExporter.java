package com.shrimpfarm.app.utils;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.FeedingRecordActivity.DayRecord;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.FeedingTimeStandard;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExcelExporter {

    public static final int MODE_RAW = 0;
    public static final int MODE_BEHAVIOR = 1;

    private static final String EXPORT_DIR = "虾场导出";

    public static class UntaggedInfo {
        public final Set<String> mix = new LinkedHashSet<>();
        public final Set<String> water = new LinkedHashSet<>();
        public final Set<String> all = new LinkedHashSet<>();

        public boolean isEmpty() {
            return all.isEmpty();
        }
    }

    public static String exportAndSave(Context ctx, String batchName, String stockingDate,
                                       List<DayRecord> records,
                                       Map<String, String> mixTags,
                                       Map<String, String> waterTags,
                                       boolean behaviorMode,
                                       List<DatabaseHelper.CheckRecord> checkRecords,
                                       List<DatabaseHelper.WaterQualityRecord> waterRecords,
                                       boolean isFourMeals) throws Exception {
        byte[] bytes = buildXlsx(ctx, batchName, stockingDate, records, mixTags, waterTags, behaviorMode,
                checkRecords, waterRecords, isFourMeals);
        if (bytes == null) throw new Exception(ctx.getString(R.string.export_toast_failed));
        String suffix = behaviorMode
                ? ctx.getString(R.string.export_suffix_behavior)
                : ctx.getString(R.string.export_suffix_raw);
        String base = sanitizeFileName(batchName) + "_" + suffix;
        String fileName = base + ".xlsx";
        return saveToDownloads(ctx, bytes, fileName, base);
    }

    public static UntaggedInfo collectUntaggedProducts(List<DayRecord> records,
                                                       Map<String, String> mixTags,
                                                       Map<String, String> waterTags) {
        UntaggedInfo info = new UntaggedInfo();
        if (records == null) return info;
        for (DayRecord r : records) {
            if (r == null) continue;
            checkCell(r.waterMix1, waterTags, info, false);
            checkCell(r.waterMix2, waterTags, info, false);
            checkCell(r.waterMix3, waterTags, info, false);
            checkCell(r.waterMix4, waterTags, info, false);
            checkCell(r.mix1, mixTags, info, true);
            checkCell(r.mix2, mixTags, info, true);
            checkCell(r.mix3, mixTags, info, true);
            checkCell(r.mix4, mixTags, info, true);
        }
        return info;
    }

    private static void checkCell(String raw, Map<String, String> tags, UntaggedInfo info, boolean isMix) {
        String product = DatabaseHelper.extractProductName(raw);
        if (product.isEmpty()) return;
        String t = tags.get(product);
        if (t == null || t.isEmpty()) {
            if (isMix) {
                info.mix.add(product);
            } else {
                info.water.add(product);
            }
            info.all.add(product);
        }
    }

    // ==================== .xlsx 生成 ====================

    private static byte[] buildXlsx(Context ctx, String batchName, String stockingDate,
                                    List<DayRecord> records,
                                    Map<String, String> mixTags, Map<String, String> waterTags,
                                    boolean behaviorMode,
                                    List<DatabaseHelper.CheckRecord> checkRecords,
                                    List<DatabaseHelper.WaterQualityRecord> waterRecords,
                                    boolean isFourMeals) throws Exception {
        String dateH = ctx.getString(R.string.export_header_date);
        String dayH = ctx.getString(R.string.export_header_day);
        String breakfastH = ctx.getString(R.string.feeding_header_breakfast);
        String lunchH = ctx.getString(R.string.feeding_header_lunch);
        String dinnerH = ctx.getString(R.string.feeding_header_dinner);
        String snackH = ctx.getString(R.string.feeding_header_night_snack);
        String mix1H = ctx.getString(R.string.export_header_mix1);
        String mix2H = ctx.getString(R.string.export_header_mix2);
        String mix3H = ctx.getString(R.string.export_header_mix3);
        String mix4H = ctx.getString(R.string.export_header_mix4);
        String water1H = ctx.getString(R.string.export_header_water1);
        String water2H = ctx.getString(R.string.export_header_water2);
        String water3H = ctx.getString(R.string.export_header_water3);
        String water4H = ctx.getString(R.string.export_header_water4);
        String remarkH = ctx.getString(R.string.feeding_header_remark);
        String sheetName = ctx.getString(behaviorMode
                ? R.string.export_sheet_behavior : R.string.export_sheet_raw);
        String bName = (batchName == null) ? "" : batchName.trim();
        String title = bName.isEmpty() ? sheetName : bName + " " + sheetName;

        Date firstDate = null;
        if (stockingDate != null && !stockingDate.trim().isEmpty()) {
            firstDate = parseRecordDate(stockingDate);
        }
        if (firstDate == null && records != null && !records.isEmpty()) {
            firstDate = parseRecordDate(records.get(0).date);
        }

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sheet.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>");
        sheet.append("<cols>");
        sheet.append("<col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>");
        sheet.append("<col min=\"2\" max=\"2\" width=\"5\" customWidth=\"1\"/>");
        sheet.append("<col min=\"3\" max=\"3\" width=\"7\" customWidth=\"1\"/>");
        sheet.append("<col min=\"4\" max=\"4\" width=\"10\" customWidth=\"1\"/>");
        sheet.append("<col min=\"5\" max=\"5\" width=\"7\" customWidth=\"1\"/>");
        sheet.append("<col min=\"6\" max=\"6\" width=\"10\" customWidth=\"1\"/>");
        sheet.append("<col min=\"7\" max=\"7\" width=\"7\" customWidth=\"1\"/>");
        sheet.append("<col min=\"8\" max=\"8\" width=\"10\" customWidth=\"1\"/>");
        sheet.append("<col min=\"9\" max=\"9\" width=\"7\" customWidth=\"1\"/>");
        sheet.append("<col min=\"10\" max=\"10\" width=\"10\" customWidth=\"1\"/>");
        sheet.append("<col min=\"11\" max=\"15\" width=\"10\" customWidth=\"1\"/>");
        sheet.append("</cols>");
        sheet.append("<sheetData>");

        // 第1行（标题，跨全表合并居中，两倍行高）
        sheet.append("<row r=\"1\" ht=\"30\" customHeight=\"1\">");
        writeCellWithStyle(sheet, 1, 1, title, 3);
        sheet.append("</row>");

        // 第2行（单行表头，两倍行高）
        sheet.append("<row r=\"2\" ht=\"30\" customHeight=\"1\">");
        writeCell(sheet, 2, 1, dateH, true);
        writeCell(sheet, 2, 2, dayH, true);
        writeCell(sheet, 2, 3, breakfastH, true);
        writeCell(sheet, 2, 4, mix1H, true);
        writeCell(sheet, 2, 5, lunchH, true);
        writeCell(sheet, 2, 6, mix2H, true);
        writeCell(sheet, 2, 7, dinnerH, true);
        writeCell(sheet, 2, 8, mix3H, true);
        writeCell(sheet, 2, 9, snackH, true);
        writeCell(sheet, 2, 10, mix4H, true);
        writeCell(sheet, 2, 11, water1H, true);
        writeCell(sheet, 2, 12, water2H, true);
        writeCell(sheet, 2, 13, water3H, true);
        writeCell(sheet, 2, 14, water4H, true);
        writeCell(sheet, 2, 15, remarkH, true);
        sheet.append("</row>");

        // 数据行
        int row = 3;
        int idx = 1;
        if (records != null) {
            for (DayRecord r : records) {
                if (r == null) { row++; idx++; continue; }
                int day = (firstDate == null) ? idx : dayIndexOf(r.date, firstDate);
                sheet.append("<row r=\"").append(row).append("\" customHeight=\"0\">");
                writeCell(sheet, row, 1, r.date, false);
                writeCell(sheet, row, 2, String.valueOf(day), false);
                writeCell(sheet, row, 3, r.breakfast, false);
                writeCellWrapped(sheet, row, 4, cellOrBlank(r.mix1, mixTags, behaviorMode));
                writeCell(sheet, row, 5, r.lunch, false);
                writeCellWrapped(sheet, row, 6, cellOrBlank(r.mix2, mixTags, behaviorMode));
                writeCell(sheet, row, 7, r.dinner, false);
                writeCellWrapped(sheet, row, 8, cellOrBlank(r.mix3, mixTags, behaviorMode));
                writeCell(sheet, row, 9, r.nightSnack, false);
                writeCellWrapped(sheet, row, 10, cellOrBlank(r.mix4, mixTags, behaviorMode));
                writeCellWrapped(sheet, row, 11, cellOrBlank(r.waterMix1, waterTags, behaviorMode));
                writeCellWrapped(sheet, row, 12, cellOrBlank(r.waterMix2, waterTags, behaviorMode));
                writeCellWrapped(sheet, row, 13, cellOrBlank(r.waterMix3, waterTags, behaviorMode));
                writeCellWrapped(sheet, row, 14, cellOrBlank(r.waterMix4, waterTags, behaviorMode));
                writeCellWrapped(sheet, row, 15, r.remark);
                sheet.append("</row>");
                row++;
                idx++;
            }
        }

        sheet.append("</sheetData>");
        sheet.append("<mergeCells count=\"1\">");
        sheet.append("<mergeCell ref=\"A1:O1\"/>");
        sheet.append("</mergeCells>");
        sheet.append("<printOptions horizontalCentered=\"1\"/>");
        sheet.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>");
        sheet.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
        sheet.append("<headerFooter><oddFooter><center>第 &amp;P 页</center></oddFooter></headerFooter>");
        sheet.append("</worksheet>");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            putEntry(zos, "[Content_Types].xml", contentTypesXml());
            putEntry(zos, "_rels/.rels", relsXml());
            putEntry(zos, "xl/workbook.xml", workbookXml());
            putEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            putEntry(zos, "xl/styles.xml", stylesXml());
            putEntry(zos, "xl/worksheets/sheet1.xml", sheet.toString());
            putEntry(zos, "xl/worksheets/sheet2.xml",
                    buildCheckSheet(ctx, batchName, stockingDate, checkRecords, isFourMeals));
            putEntry(zos, "xl/worksheets/sheet3.xml",
                    buildWaterSheet(ctx, batchName, stockingDate, waterRecords));
        }
        return bos.toByteArray();
    }

    private static String buildCheckSheet(Context ctx, String batchName, String stockingDate,
                                          List<DatabaseHelper.CheckRecord> records,
                                          boolean isFourMeals) {
        String sheetName = ctx.getString(R.string.export_sheet_check);
        String bName = (batchName == null) ? "" : batchName.trim();
        String legend = ctx.getString(R.string.export_check_legend);
        String title = bName.isEmpty() ? sheetName : bName + " " + sheetName;
        String titleText = title + "\n" + legend;
        List<DatabaseHelper.CheckRecord> valid = new ArrayList<>();
        if (records != null) {
            for (DatabaseHelper.CheckRecord r : records) {
                if (r == null || r.excluded || r.recordDate == null) continue;
                valid.add(r);
            }
        }
        Collections.sort(valid, new Comparator<DatabaseHelper.CheckRecord>() {
            @Override
            public int compare(DatabaseHelper.CheckRecord a, DatabaseHelper.CheckRecord b) {
                int c = a.recordDate.compareTo(b.recordDate);
                if (c != 0) return c;
                c = Integer.compare(shedSortKey(a.shedNumber, a.shedRowIndex), shedSortKey(b.shedNumber, b.shedRowIndex));
                if (c != 0) return c;
                return Long.compare(a.id, b.id);
            }
        });

        LinkedHashSet<String> shedOrder = new LinkedHashSet<>();
        for (DatabaseHelper.CheckRecord r : valid) shedOrder.add(shedLabel(r.shedNumber, r.shedRowIndex));
        if (shedOrder.isEmpty()) shedOrder.add(ctx.getString(R.string.export_header_shed));
        List<String> sheds = new ArrayList<>(shedOrder);
        Collections.sort(sheds, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int ai = parseShed(a);
                int bi = parseShed(b);
                if (ai < 0 && bi < 0) return a.compareTo(b);
                if (ai < 0) return 1;
                if (bi < 0) return -1;
                return Integer.compare(ai, bi);
            }
        });

        int shedCount = sheds.size();
        int lastCol = 1 + shedCount * 3;
        String lastColName = colName(lastCol);

        List<TreeSet<Integer>> shedRatioRows = new ArrayList<>();
        for (int i = 0; i < shedCount; i++) shedRatioRows.add(new TreeSet<>());

        Date firstDate = null;
        if (stockingDate != null && !stockingDate.trim().isEmpty()) {
            firstDate = parseRecordDate(stockingDate);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>");

        // 列宽：日期列 + 每棚3列（查料时间 / 用时 / 超时比例）
        sb.append("<cols>");
        sb.append("<col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>");
        for (int i = 0; i < shedCount; i++) {
            int base = 2 + i * 3;
            sb.append("<col min=\"").append(base).append("\" max=\"").append(base)
                    .append("\" width=\"9.3\" customWidth=\"1\"/>");
            sb.append("<col min=\"").append(base + 1).append("\" max=\"").append(base + 1)
                    .append("\" width=\"5.2\" customWidth=\"1\"/>");
            sb.append("<col min=\"").append(base + 2).append("\" max=\"").append(base + 2)
                    .append("\" width=\"9.3\" customWidth=\"1\"/>");
        }
        sb.append("</cols>");

        sb.append("<sheetData>");

        // 第1行：标题（全宽合并）
        sb.append("<row r=\"1\" ht=\"60\" customHeight=\"1\">");
        writeCellWithStyle(sb, 1, 1, titleText, 2);
        sb.append("</row>");

        // 第2行：A2:A3 竖并「棚号/日期」，每棚横并 3 列（widesheet）
        sb.append("<row r=\"2\" ht=\"30\" customHeight=\"1\">");
        writeCell(sb, 2, 1, ctx.getString(R.string.export_header_shed_date), true);
        for (int i = 0; i < shedCount; i++) {
            int base = 2 + i * 3;
            writeCell(sb, 2, base, sheds.get(i), true);
        }
        sb.append("</row>");

        // 第3行：每棚拆三列
        sb.append("<row r=\"3\" ht=\"30\" customHeight=\"1\">");
        for (int i = 0; i < shedCount; i++) {
            int base = 2 + i * 3;
            writeCell(sb, 3, base, ctx.getString(R.string.export_header_check_time), true);
            writeCell(sb, 3, base + 1, ctx.getString(R.string.export_header_elapsed), true);
            writeCell(sb, 3, base + 2, ctx.getString(R.string.export_header_overtime), true);
        }
        sb.append("</row>");

        // 数据区：按日期分组，一天展开 max(N) 行，日期列纵向合并
        int row = 4;
        if (!valid.isEmpty()) {
            int daysWritten = 0;
            // 按日期切分
            List<Map.Entry<String, List<DatabaseHelper.CheckRecord>>> batches = new ArrayList<>();
            String curDate = null;
            List<DatabaseHelper.CheckRecord> curList = new ArrayList<>();
            for (DatabaseHelper.CheckRecord r : valid) {
                if (!r.recordDate.equals(curDate)) {
                    if (curDate != null) batches.add(new java.util.AbstractMap.SimpleEntry<>(curDate, curList));
                    curDate = r.recordDate;
                    curList = new ArrayList<>();
                }
                curList.add(r);
            }
            if (curDate != null) batches.add(new java.util.AbstractMap.SimpleEntry<>(curDate, curList));

            StringBuilder merges = new StringBuilder();
            int mergeCount = 0;
            // 标题合并 + 表头纵横合并
            merges.append("<mergeCell ref=\"A1:").append(lastColName).append("1\"/>");
            merges.append("<mergeCell ref=\"A2:A3\"/>");
            mergeCount += 2;
            for (int i = 0; i < shedCount; i++) {
                int base = 2 + i * 3;
                merges.append("<mergeCell ref=\"")
                        .append(colName(base)).append("2:")
                        .append(colName(base + 2)).append("2\"/>");
                mergeCount++;
            }

            for (Map.Entry<String, List<DatabaseHelper.CheckRecord>> e : batches) {
                String date = e.getKey();
                List<DatabaseHelper.CheckRecord> dayRecords = e.getValue();
                // 按棚聚合
                LinkedHashMap<String, List<DatabaseHelper.CheckRecord>> byShed = new LinkedHashMap<>();
                for (DatabaseHelper.CheckRecord r : dayRecords) {
                    String label = shedLabel(r.shedNumber, r.shedRowIndex);
                    List<DatabaseHelper.CheckRecord> l = byShed.get(label);
                    if (l == null) { l = new ArrayList<>(); byShed.put(label, l); }
                    l.add(r);
                }
                int maxN = 0;
                for (String sh : sheds) {
                    List<DatabaseHelper.CheckRecord> l = byShed.get(sh);
                    if (l != null && l.size() > maxN) maxN = l.size();
                }
                if (maxN == 0) continue;
                int firstRowInDate = row;
                int dayIdx = (firstDate == null) ? (daysWritten + 1) : dayIndexOf(date, firstDate);
                for (int k = 0; k < maxN; k++) {
                    sb.append("<row r=\"").append(row).append("\" customHeight=\"0\">");
                    writeCell(sb, row, 1, k == 0 ? date : "", false);
                    for (int s = 0; s < shedCount; s++) {
                        int base = 2 + s * 3;
                        String sh = sheds.get(s);
                        List<DatabaseHelper.CheckRecord> l = byShed.get(sh);
                        DatabaseHelper.CheckRecord r = (l != null && k < l.size()) ? l.get(k) : null;
                        if (r == null) {
                            writeCell(sb, row, base, "", false);
                            writeCell(sb, row, base + 1, "", false);
                            writeCell(sb, row, base + 2, "", false);
                            continue;
                        }
                        writeCell(sb, row, base, timePart(r.checkTime), false);
                        writeCell(sb, row, base + 1, formatMinutes(r.durationSeconds), false);
                        long stdSec = FeedingTimeStandard.getStandardSeconds(dayIdx, isFourMeals);
                        if (stdSec > 0 && r.durationSeconds > 0) {
                            double ratio = (r.durationSeconds - stdSec) / (double) stdSec;
                            writeNumberCell(sb, row, base + 2, ratio);
                            shedRatioRows.get(s).add(row);
                        } else {
                            writeCell(sb, row, base + 2, "", false);
                        }
                    }
                    sb.append("</row>");
                    row++;
                }
                if (maxN > 1) {
                    merges.append("<mergeCell ref=\"A").append(firstRowInDate)
                            .append(":A").append(row - 1).append("\"/>");
                    mergeCount++;
                }
                daysWritten++;
            }

            sb.append("</sheetData>");
            sb.append("<mergeCells count=\"").append(mergeCount).append("\">").append(merges).append("</mergeCells>");
            sb.append(checkConditionalFormatting(shedRatioRows));
            sb.append("<printOptions horizontalCentered=\"1\"/>");
            sb.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>");
            sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
            sb.append("<headerFooter><oddFooter><center>第 &amp;P 页</center></oddFooter></headerFooter>");
        } else {
            // 无数据：仍输出标题 + 空表头
            sb.append("</sheetData>");
            int mergeCount = 2 + shedCount;
            sb.append("<mergeCells count=\"").append(mergeCount).append("\">");
            sb.append("<mergeCell ref=\"A1:").append(lastColName).append("1\"/>");
            sb.append("<mergeCell ref=\"A2:A3\"/>");
            for (int i = 0; i < shedCount; i++) {
                int base = 2 + i * 3;
                sb.append("<mergeCell ref=\"").append(colName(base)).append("2:").append(colName(base + 2)).append("2\"/>");
            }
            sb.append("</mergeCells>");
            sb.append("<printOptions horizontalCentered=\"1\"/>");
            sb.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>");
            sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
            sb.append("<headerFooter><oddFooter><center>第 &amp;P 页</center></oddFooter></headerFooter>");
        }
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static String buildWaterSheet(Context ctx, String batchName, String stockingDate,
                                          List<DatabaseHelper.WaterQualityRecord> records) {
        String sheetName = ctx.getString(R.string.export_sheet_water);
        String bName = (batchName == null) ? "" : batchName.trim();
        String title = bName.isEmpty() ? sheetName : bName + " " + sheetName;
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>");
        sb.append("<cols>");
        sb.append("<col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>");
        sb.append("<col min=\"2\" max=\"2\" width=\"5\" customWidth=\"1\"/>");
        sb.append("<col min=\"3\" max=\"6\" width=\"10\" customWidth=\"1\"/>");
        sb.append("<col min=\"7\" max=\"7\" width=\"8\" customWidth=\"1\"/>");
        sb.append("<col min=\"8\" max=\"8\" width=\"10\" customWidth=\"1\"/>");
        sb.append("<col min=\"9\" max=\"10\" width=\"8\" customWidth=\"1\"/>");
        sb.append("<col min=\"11\" max=\"13\" width=\"10\" customWidth=\"1\"/>");
        sb.append("</cols>");
        sb.append("<sheetData>");
        sb.append("<row r=\"1\" ht=\"30\" customHeight=\"1\">");
        writeCellWithStyle(sb, 1, 1, title, 3);
        sb.append("</row>");
        sb.append("<row r=\"2\" ht=\"30\" customHeight=\"1\">");
        writeCell(sb, 2, 1, ctx.getString(R.string.export_header_date), true);
        writeCell(sb, 2, 2, ctx.getString(R.string.export_header_day), true);
        writeCell(sb, 2, 3, ctx.getString(R.string.export_header_vibrio), true);
        writeCell(sb, 2, 4, ctx.getString(R.string.export_header_salinity), true);
        writeCell(sb, 2, 5, ctx.getString(R.string.export_header_ammonia), true);
        writeCell(sb, 2, 6, ctx.getString(R.string.export_header_nitrite), true);
        writeCell(sb, 2, 7, ctx.getString(R.string.export_header_ph), true);
        writeCell(sb, 2, 8, ctx.getString(R.string.export_header_do), true);
        writeCell(sb, 2, 9, ctx.getString(R.string.export_header_max_temp), true);
        writeCell(sb, 2, 10, ctx.getString(R.string.export_header_min_temp), true);
        writeCell(sb, 2, 11, ctx.getString(R.string.export_header_chlorine), true);
        writeCell(sb, 2, 12, ctx.getString(R.string.export_header_h2s), true);
        writeCell(sb, 2, 13, ctx.getString(R.string.export_header_orp), true);
        sb.append("</row>");
        Date firstDate = null;
        if (stockingDate != null && !stockingDate.trim().isEmpty()) {
            firstDate = parseRecordDate(stockingDate);
        }
        int row = 3;
        int idx = 1;
        if (records != null) {
            for (DatabaseHelper.WaterQualityRecord r : records) {
                if (r == null) { row++; idx++; continue; }
                int day = (firstDate == null) ? idx : dayIndexOf(r.date, firstDate);
                sb.append("<row r=\"").append(row).append("\" customHeight=\"0\">");
                writeCell(sb, row, 1, r.date, false);
                writeCell(sb, row, 2, String.valueOf(day), false);
                writeCell(sb, row, 3, r.vibrio, false);
                writeCell(sb, row, 4, r.salinity, false);
                writeCell(sb, row, 5, r.ammonia, false);
                writeCell(sb, row, 6, r.nitrite, false);
                writeCell(sb, row, 7, r.ph, false);
                writeCell(sb, row, 8, r.dissolvedOxygen, false);
                writeCell(sb, row, 9, r.maxTemp, false);
                writeCell(sb, row, 10, r.minTemp, false);
                writeCell(sb, row, 11, r.chlorine, false);
                writeCell(sb, row, 12, r.hydrogenSulfide, false);
                writeCell(sb, row, 13, r.orp, false);
                sb.append("</row>");
                row++;
                idx++;
            }
        }
        sb.append("</sheetData>");
        sb.append("<mergeCells count=\"1\"><mergeCell ref=\"A1:M1\"/></mergeCells>");
        sb.append("<printOptions horizontalCentered=\"1\"/>");
        sb.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>");
        sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
        sb.append("<headerFooter><oddFooter><center>第 &amp;P 页</center></oddFooter></headerFooter>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static String timePart(String dateTime) {
        if (dateTime == null) return "";
        int sp = dateTime.indexOf(' ');
        if (sp >= 0) return dateTime.substring(sp + 1);
        return dateTime.trim();
    }

    private static int shedSortKey(String shedNumber, int shedRowIndex) {
        int n = parseShedNumber(shedNumber);
        return n > 0 ? n : shedRowIndex + 1;
    }

    private static int parseShedNumber(String shedNumber) {
        if (shedNumber == null) return -1;
        String t = shedNumber.trim().replace("号", "").replace("棚", "");
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String shedLabel(String shedNumber, int shedRowIndex) {
        String base;
        if (shedNumber != null && !shedNumber.trim().isEmpty()) {
            base = shedNumber.trim();
        } else {
            base = String.valueOf(shedRowIndex + 1);
        }
        String suffix = shedNumber != null && (shedNumber.contains("号") || shedNumber.contains("棚"))
                ? "" : "号";
        return base + suffix;
    }

    private static int parseShed(String label) {
        if (label == null) return -1;
        return parseShedNumber(label);
    }

    private static String formatMinutes(long seconds) {
        if (seconds <= 0) return "";
        return String.valueOf(Math.round(seconds / 60.0));
    }

    private static String cellOrBlank(String raw, Map<String, String> tags, boolean behaviorMode) {
        String product = DatabaseHelper.extractProductName(raw);
        if (behaviorMode) {
            if (product.isEmpty()) return "";
            String t = tags == null ? null : tags.get(product);
            return t == null ? "" : t;
        }
        return product;
    }

    private static void writeCell(StringBuilder sb, int row, int col, String value, boolean header) {
        writeCellWithStyle(sb, row, col, value, header ? 1 : 2);
    }

    private static void writeCellWrapped(StringBuilder sb, int row, int col, String value) {
        writeCellWithStyle(sb, row, col, value, 2);
    }

    private static void writeCellWithStyle(StringBuilder sb, int row, int col, String value, int style) {
        String text = value == null ? "" : value;
        sb.append("<c r=\"").append(colName(col)).append(row).append('"');
        if (style != 0) sb.append(" s=\"").append(style).append('"');
        sb.append(" t=\"inlineStr\"><is><t");
        if (text.indexOf('\n') >= 0 || text.startsWith(" ") || text.endsWith(" ")
                || text.indexOf('\t') >= 0) {
            sb.append(" xml:space=\"preserve\"");
        }
        sb.append('>').append(escapeXml(text)).append("</t></is></c>");
    }

    private static void writeNumberCell(StringBuilder sb, int row, int col, double value) {
        sb.append("<c r=\"").append(colName(col)).append(row).append("\" s=\"5\"><v>")
                .append(formatNumber(value)).append("</v></c>");
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static String checkConditionalFormatting(List<TreeSet<Integer>> shedRatioRows) {
        StringBuilder sb = new StringBuilder();
        int globalPriority = 1;
        for (int i = 0; i < shedRatioRows.size(); i++) {
            TreeSet<Integer> rows = shedRatioRows.get(i);
            if (rows.isEmpty()) continue;
            int base = 2 + i * 3;
            int col = base + 2;
            String colL = colName(col);
            // 合并连续行为范围，如 D4:D5 D7 D10
            StringBuilder sqref = new StringBuilder();
            int prev = -2;
            int rangeStart = -1;
            for (int r : rows) {
                if (prev + 1 == r) {
                    prev = r;
                } else {
                    if (rangeStart >= 0) {
                        if (sqref.length() > 0) sqref.append(' ');
                        sqref.append(colL).append(rangeStart);
                        if (prev != rangeStart) sqref.append(':').append(colL).append(prev);
                    }
                    rangeStart = r;
                    prev = r;
                }
            }
            if (rangeStart >= 0) {
                if (sqref.length() > 0) sqref.append(' ');
                sqref.append(colL).append(rangeStart);
                if (prev != rangeStart) sqref.append(':').append(colL).append(prev);
            }
            // 公式引用用该棚列的首数据行
            String ref = colL + rows.first();
            // dxfId: 0=orange 1=red 2=purple 3=lightgreen 4=darkgreen 5=saturatedblue
            sb.append("<conditionalFormatting sqref=\"").append(sqref).append("\">");
            sb.append("<cfRule type=\"expression\" dxfId=\"0\" priority=\"").append(globalPriority++)
                    .append("\" stopIfTrue=\"1\"><formula>AND(ISNUMBER(").append(ref).append("),").append(ref).append("&gt;=0,").append(ref).append("&lt;0.1)</formula></cfRule>");
            sb.append("<cfRule type=\"expression\" dxfId=\"1\" priority=\"").append(globalPriority++)
                    .append("\" stopIfTrue=\"1\"><formula>AND(ISNUMBER(").append(ref).append("),").append(ref).append("&gt;=0.1,").append(ref).append("&lt;0.2)</formula></cfRule>");
            sb.append("<cfRule type=\"expression\" dxfId=\"2\" priority=\"").append(globalPriority++)
                    .append("\" stopIfTrue=\"1\"><formula>AND(ISNUMBER(").append(ref).append("),").append(ref).append("&gt;=0.2)</formula></cfRule>");
            sb.append("<cfRule type=\"expression\" dxfId=\"3\" priority=\"").append(globalPriority++)
                    .append("\" stopIfTrue=\"1\"><formula>AND(ISNUMBER(").append(ref).append("),").append(ref).append("&lt;0,").append(ref).append("&gt;-0.1)</formula></cfRule>");
            sb.append("<cfRule type=\"expression\" dxfId=\"4\" priority=\"").append(globalPriority++)
                    .append("\" stopIfTrue=\"1\"><formula>AND(ISNUMBER(").append(ref).append("),").append(ref).append("&lt;=-0.1,").append(ref).append("&gt;-0.2)</formula></cfRule>");
            sb.append("<cfRule type=\"expression\" dxfId=\"5\" priority=\"").append(globalPriority++)
                    .append("\"><formula>AND(ISNUMBER(").append(ref).append("),").append(ref).append("&lt;=-0.2)</formula></cfRule>");
            sb.append("</conditionalFormatting>");
        }
        return sb.toString();
    }

    private static int dayIndexOf(String date, Date first) {
        Date d = parseRecordDate(date);
        if (d == null || first == null) return 1;
        long diff = (d.getTime() - first.getTime()) / (24L * 3600 * 1000);
        int day = (int) diff;
        return day >= 0 ? day + 1 : day;
    }

    private static Date parseRecordDate(String date) {
        if (date == null || date.trim().isEmpty()) return null;
        String[] formats = {"yyyy/MM/dd", "yyyy/M/d", "yyyy-MM-dd", "yyyy-M-d",
                "yyyy.M.d", "yyyy年M月d日", "yyyy年MM月dd日"};
        for (String f : formats) {
            try {
                return new SimpleDateFormat(f, java.util.Locale.CHINA).parse(date.trim());
            } catch (Exception ignored) { /* try next */ }
        }
        return null;
    }

    private static String colName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    private static String escapeXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        byte[] data = content.getBytes("UTF-8");
        zos.write(data, 0, data.length);
        zos.closeEntry();
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String relsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"Sheet2\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "<sheet name=\"Sheet3\" sheetId=\"3\" r:id=\"rId3\"/>"
                + "</sheets>"
                + "<definedNames><definedName name=\"_xlnm.Print_Titles\">"
                + "'Sheet1'!$1:$2,'Sheet2'!$1:$3,'Sheet3'!$1:$2"
                + "</definedName></definedNames>"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>"
                + "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"1\">"
                + "<numFmt numFmtId=\"164\" formatCode=\"0.0%\"/>"
                + "</numFmts>"
                + "<fonts count=\"3\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2D84C2\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FF000000\"/></left><right style=\"thin\"><color rgb=\"FF000000\"/></right><top style=\"thin\"><color rgb=\"FF000000\"/></top><bottom style=\"thin\"><color rgb=\"FF000000\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"6\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment wrapText=\"1\" horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "<dxfs count=\"6\">"
                + "<dxf><font><color rgb=\"FFFFFFFF\"/></font><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFED7D31\"/><bgColor rgb=\"FFED7D31\"/></patternFill></fill></dxf>"
                + "<dxf><font><color rgb=\"FFFFFFFF\"/></font><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFF0000\"/><bgColor rgb=\"FFFF0000\"/></patternFill></fill></dxf>"
                + "<dxf><font><color rgb=\"FFFFFFFF\"/></font><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF7030A0\"/><bgColor rgb=\"FF7030A0\"/></patternFill></fill></dxf>"
                + "<dxf><font><color rgb=\"FFFFFFFF\"/></font><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF92D050\"/><bgColor rgb=\"FF92D050\"/></patternFill></fill></dxf>"
                + "<dxf><font><color rgb=\"FFFFFFFF\"/></font><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF008000\"/><bgColor rgb=\"FF008000\"/></patternFill></fill></dxf>"
                + "<dxf><font><color rgb=\"FFFFFFFF\"/></font><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0057D8\"/><bgColor rgb=\"FF0057D8\"/></patternFill></fill></dxf>"
                + "</dxfs>"
                + "</styleSheet>";
    }

    // ==================== 存储到 Downloads ====================

    private static String saveToDownloads(Context ctx, byte[] bytes, String fileName, String base) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return saveToMediaStore(ctx, bytes, fileName, base);
            } catch (Exception e) {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA)
                        .format(new Date());
                String altName = base + "_" + ts + ".xlsx";
                return saveToMediaStore(ctx, bytes, altName, altName.substring(0, altName.length() - 5));
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception(ctx.getString(R.string.export_toast_failed));
            }
            File[] old = dir.listFiles((d, name) -> name != null
                    && (name.startsWith(base + ".") || name.startsWith(base + "_") || name.startsWith(base + " ")));
            if (old != null) {
                for (File f : old) f.delete();
            }
            File f = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(bytes);
                fos.flush();
            }
            return f.getAbsolutePath();
        }
    }

    @SuppressLint("NewApi")
    private static String saveToMediaStore(Context ctx, byte[] bytes, String fileName, String base) throws Exception {
        Uri uri = findExistingTarget(ctx, fileName, base);
        if (uri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_DIR);
            uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception(ctx.getString(R.string.export_toast_failed));
            fixInsertedName(ctx, uri, fileName);
        }
        try (AssetFileDescriptor afd = ctx.getContentResolver().openAssetFileDescriptor(uri, "rwt")) {
            if (afd == null) throw new Exception(ctx.getString(R.string.export_toast_failed));
            try (FileOutputStream fos = new FileOutputStream(afd.getParcelFileDescriptor().getFileDescriptor())) {
                fos.write(bytes);
                fos.flush();
            }
        }
        return "Downloads/" + EXPORT_DIR + "/" + fileName;
    }

    @SuppressLint("NewApi")
    private static void fixInsertedName(Context ctx, Uri uri, String fileName) {
        try {
            Cursor c = ctx.getContentResolver().query(uri,
                    new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME));
                        if (name != null && !name.equals(fileName)) {
                            ContentValues cv = new ContentValues();
                            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                            ctx.getContentResolver().update(uri, cv, null, null);
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("NewApi")
    private static Uri findExistingTarget(Context ctx, String fileName, String base) {
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME};
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(collection, projection, null, null, null);
            if (c == null) return null;
            Uri reuse = null;
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME));
                if (name == null) continue;
                long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                if (name.equals(fileName)) {
                    reuse = uri;
                } else if (name.startsWith(base + "_") || name.startsWith(base + " ")) {
                    ctx.getContentResolver().delete(uri, null, null);
                }
            }
            return reuse;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "batch";
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }
}