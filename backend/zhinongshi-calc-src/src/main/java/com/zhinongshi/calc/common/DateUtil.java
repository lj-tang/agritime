package com.zhinongshi.calc.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期工具类。
 * <p>提供相对天数换算、7 天窗口生成、跨年天数计算等纯函数。</p>
 */
public final class DateUtil {

    private DateUtil() {
    }

    /**
     * 相对天数换算为绝对日期。
     *
     * @param date 基准日期
     * @param days 相对天数
     * @return 基准日期 + days
     */
    public static LocalDate plusDays(LocalDate date, int days) {
        return date.plusDays(days);
    }

    /**
     * 生成 7 天气象窗口：[originalDate-3, originalDate+3]，共 7 天。
     *
     * @param originalDate 原计划执行日期
     * @return 7 天日期列表（升序）
     */
    public static List<LocalDate> sevenDayWindow(LocalDate originalDate) {
        List<LocalDate> window = new ArrayList<>(7);
        for (int offset = -3; offset <= 3; offset++) {
            window.add(originalDate.plusDays(offset));
        }
        return window;
    }

    /**
     * 跨年安全地解析节日公历日期。
     * <p>当 (month, day) 早于 referenceDate 的月日时，按次年构造，保证结果 >= referenceDate。</p>
     * <p>例如 referenceDate=2026-12-20，春节=1-25 → 返回 2027-01-25。</p>
     *
     * @param referenceDate 农事节点日期
     * @param month         节日公历月
     * @param day           节日公历日
     * @return 跨年处理后的节日日期
     */
    public static LocalDate resolveCrossYearDate(LocalDate referenceDate, int month, int day) {
        LocalDate candidate = LocalDate.of(referenceDate.getYear(), month, day);
        if (candidate.isBefore(referenceDate)) {
            candidate = LocalDate.of(referenceDate.getYear() + 1, month, day);
        }
        return candidate;
    }

    /**
     * 计算两个日期间的天数差（to - from），跨年安全。
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 天数差；任一为 null 返回 null
     */
    public static Long daysBetweenCrossYear(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(from, to);
    }
}