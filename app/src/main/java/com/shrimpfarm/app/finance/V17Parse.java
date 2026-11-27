package com.shrimpfarm.app.finance;

import com.shrimpfarm.app.mixcalc.model.ShedCoverage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** V17 纯解析函数：规格取首值、棚号只认唯一、金额修补只在缺金额且两因子齐备时。 */
public final class V17Parse {

    private static final Pattern SPEC_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*尾?\\s*/?\\s*斤?");

    private V17Parse() {
    }

    /** 规格解析结果：value 为区间首值（无效时为 0），range 为原区间文本（无区间时为 ""）。 */
    public static final class SpecParse {
        public final double value;
        public final String range;

        public SpecParse(double value, String range) {
            this.value = value;
            this.range = range == null ? "" : range;
        }
    }

    /** 取规格中的第一个数；0 < v < 1000 之外视为无效，返回 value=0 且 range=""。 */
    public static SpecParse parseSpec(String specs) {
        if (specs == null) {
            return new SpecParse(0, "");
        }
        Matcher m = SPEC_PATTERN.matcher(specs);
        if (!m.find()) {
            return new SpecParse(0, "");
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException ignored) {
            return new SpecParse(0, "");
        }
        if (!(value > 0 && value < 1000)) {
            return new SpecParse(0, "");
        }
        return new SpecParse(value, hasRangeMarker(specs) ? specs : "");
    }

    /** 规格里的尾/斤数，非法或区间为空时返回 0。 */
    public static double parseTailPerJin(String specs) {
        return parseSpec(specs).value;
    }

    /** 唯一棚号返回该号；空、多棚、非法或超过上限一律返回 0（不拆分）。 */
    public static int singleShedNumber(String shedNo) {
        List<Integer> numbers = ShedCoverage.parseShedNumbers(shedNo);
        if (numbers.size() != 1) {
            return 0;
        }
        int number = numbers.get(0);
        return number > 0 && number <= ShedCoverage.MAX_SHED_NUMBER ? number : 0;
    }

    /** 仅当金额缺失（0）且重量、单价都为正时才需要反推金额。 */
    public static boolean needsAmountFixup(double amount, double weight, double price) {
        return amount == 0 && weight > 0 && price > 0;
    }

    private static boolean hasRangeMarker(String text) {
        return text.indexOf('-') >= 0 || text.indexOf('到') >= 0
                || text.indexOf('至') >= 0 || text.indexOf('~') >= 0;
    }
}
