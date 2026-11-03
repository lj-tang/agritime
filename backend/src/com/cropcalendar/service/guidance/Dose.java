package com.cropcalendar.service.guidance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 亩均用量解析（V5 T17）。
 *
 * guidance.json 里写的是"15-20方/亩""20-25kg/亩""20g/亩兑水30kg喷雾"这类自然语言，
 * 直接拼字符串没法按面积换算。这里把"数值区间 + 单位 + /亩"抽出来，换算成总面积用量。
 *
 * 只认明确标了"/亩"的用量：像"灌根1000倍液""800倍液喷淋苗床"这种稀释倍数，
 * 与面积无关，硬乘面积会算错，所以返回 null，由调用方保持原文案。
 */
public record Dose(double min, double max, String unit) {

    /** 数值 + 可选区间 + 单位 + /亩。单位按长的写在前，避免 "kg" 被 "g" 抢先匹配。 */
    private static final Pattern PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:[-~～至]\\s*(\\d+(?:\\.\\d+)?))?\\s*"
                    + "(kg|公斤|千克|ml|mL|毫升|L|升|g|克|方|m³|立方米)\\s*/\\s*亩");

    /** 解析失败返回 null —— 宁可不给换算，也不给一个错的数字。 */
    public static Dose parse(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = PATTERN.matcher(text);
        if (!m.find()) return null;
        try {
            double lo = Double.parseDouble(m.group(1));
            double hi = m.group(2) == null ? lo : Double.parseDouble(m.group(2));
            if (hi < lo) {
                double t = lo;
                lo = hi;
                hi = t;
            }
            return new Dose(lo, hi, normalizeUnit(m.group(3)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeUnit(String raw) {
        return switch (raw) {
            case "公斤", "千克" -> "kg";
            case "克" -> "g";
            case "毫升", "mL" -> "ml";
            case "升" -> "L";
            case "m³", "立方米" -> "方";
            default -> raw;
        };
    }

    /** 亩均文案，如 "15-20 方/亩"；单位与数字之间留空格，中文里更好读。 */
    public String perMuText() {
        return range(min, max) + " " + unit + "/亩";
    }

    /**
     * 按面积换算后的总量文案。
     * g / ml 超过 1000 时进位到 kg / L —— 2 亩地 20g/亩写成 "40000 g" 没有意义。
     */
    public String totalText(double areaMu) {
        double lo = min * areaMu;
        double hi = max * areaMu;
        String u = unit;
        if ("g".equals(u) && hi >= 1000) {
            lo /= 1000;
            hi /= 1000;
            u = "kg";
        } else if ("ml".equals(u) && hi >= 1000) {
            lo /= 1000;
            hi /= 1000;
            u = "L";
        }
        return range(lo, hi) + " " + u;
    }

    private static String range(double lo, double hi) {
        String a = fmt(lo);
        if (Math.abs(hi - lo) < 1e-9) return a;
        return a + "-" + fmt(hi);
    }

    /** 最多一位小数，去掉多余的 .0 */
    static String fmt(double v) {
        double rounded = Math.round(v * 10) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.valueOf(rounded);
    }
}
