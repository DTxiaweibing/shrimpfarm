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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                                       boolean behaviorMode) throws Exception {
        byte[] bytes = buildXlsx(ctx, batchName, stockingDate, records, mixTags, waterTags, behaviorMode);
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
                                    boolean behaviorMode) throws Exception {
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
        }
        return bos.toByteArray();
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
                + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "<definedNames><definedName name=\"_xlnm.Print_Titles\">'Sheet1'!$1:$2</definedName></definedNames>"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
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
                + "<cellXfs count=\"5\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment wrapText=\"1\" horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
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