package com.zhinongshi.calc.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateUtil 单元测试：日期换算、7 天窗口、跨年天数。
 */
class DateUtilTest {

    @Test
    void plusDays_basic() {
        assertEquals(LocalDate.of(2026, 5, 10), DateUtil.plusDays(LocalDate.of(2026, 4, 10), 30));
    }

    @Test
    void plusDays_crossYear() {
        assertEquals(LocalDate.of(2027, 1, 5), DateUtil.plusDays(LocalDate.of(2026, 12, 31), 5));
    }

    @Test
    void sevenDayWindow_sizeAndRange() {
        List<LocalDate> w = DateUtil.sevenDayWindow(LocalDate.of(2026, 5, 10));
        assertEquals(7, w.size());
        assertEquals(LocalDate.of(2026, 5, 7), w.get(0));
        assertEquals(LocalDate.of(2026, 5, 13), w.get(6));
    }

    @Test
    void resolveCrossYearDate_sameYear() {
        assertEquals(LocalDate.of(2026, 6, 19),
                DateUtil.resolveCrossYearDate(LocalDate.of(2026, 5, 10), 6, 19));
    }

    @Test
    void resolveCrossYearDate_nextYear() {
        // 12 月农事节点遇次年 1 月春节
        assertEquals(LocalDate.of(2027, 1, 25),
                DateUtil.resolveCrossYearDate(LocalDate.of(2026, 12, 20), 1, 25));
    }

    @Test
    void daysBetweenCrossYear_cross() {
        assertEquals(36L, DateUtil.daysBetweenCrossYear(
                LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 25)));
    }

    @Test
    void daysBetweenCrossYear_null() {
        assertNull(DateUtil.daysBetweenCrossYear(null, LocalDate.of(2026, 1, 1)));
    }
}