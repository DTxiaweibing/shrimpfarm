package com.shrimpfarm.app.feedtable;

/**
 * 绘制层取数据的唯一入口。
 *
 * <p>绘制层只认这 5 个方法，不知道 {@code DayRecord}、数据库、批次状态等业务概念；
 * 产品值到显示名的转换、灰显规则、日期格式全部由实现方负责。</p>
 */
public interface RecordTextProvider {

    /**
     * 表头文案。
     *
     * @param key {@link RecordTableColumns#DATE} 或 {@code columns.headers()[i].textKey}
     */
    String getHeaderText(String key);

    /** 冻结日期列的显示文本（如 "M月d日"），格式由实现方决定 */
    String getDateText(int day);

    /** 单元格显示文本；备注列也走这里。返回 null 视同空串 */
    String getCellText(int day, String columnId);

    /** 是否灰显（批次未开始前 / 放苗日之前） */
    boolean isCellGrayed(int day, String columnId);

    /** 是否可编辑；本卡只透传，不参与绘制 */
    boolean isCellEditable(int day, String columnId);
}
