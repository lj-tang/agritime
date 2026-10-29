package com.zhinongshi.calc.module4.solarterm;

import com.zhinongshi.calc.dto.SolarTermDTO;
import com.zhinongshi.calc.dto.SolarTermYearDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SolarTermUtil 单元测试：24 节气天文计算、跨年节气、年份非法。
 * <p>与权威节气表对照，误差 ≤1 天。</p>
 */
class SolarTermUtilTest {

    private final SolarTermUtil util = new SolarTermUtil();

    @Test
    void year2026_all24Terms() {
        SolarTermYearDTO dto = util.calculateSolarTerms(2026);
        assertTrue(dto.isSuccess());
        assertEquals(24, dto.getSolarTerms().size());
        assertEquals(2026, dto.getYear());

        // 抽查关键节气（与权威表对照，允许 ±1 天）
        assertTerm(dto, 1, "立春", LocalDate.of(2026, 2, 4));
        assertTerm(dto, 4, "春分", LocalDate.of(2026, 3, 20));
        assertTerm(dto, 10, "夏至", LocalDate.of(2026, 6, 21));
        assertTerm(dto, 16, "秋分", LocalDate.of(2026, 9, 23));
        assertTerm(dto, 22, "冬至", LocalDate.of(2026, 12, 22));
    }

    @Test
    void crossYearTerms_xiaohan_dahan() {
        SolarTermYearDTO dto = util.calculateSolarTerms(2026);
        // 小寒、大寒落次年 1 月
        assertTerm(dto, 23, "小寒", LocalDate.of(2027, 1, 5));
        assertTerm(dto, 24, "大寒", LocalDate.of(2027, 1, 20));
    }

    @Test
    void year2024_leapYear() {
        SolarTermYearDTO dto = util.calculateSolarTerms(2024);
        assertTrue(dto.isSuccess());
        assertEquals(24, dto.getSolarTerms().size());
        assertTerm(dto, 1, "立春", LocalDate.of(2024, 2, 4));
    }

    @Test
    void illegalYear_outOfRange() {
        SolarTermYearDTO dto = util.calculateSolarTerms(1800);
        assertEquals(5001, dto.getErrorCode());
        assertTrue(dto.getSolarTerms().isEmpty());
    }

    @Test
    void noNetworkRequired() {
        // 纯本地计算，无 mock，验证断网可算
        SolarTermYearDTO dto = util.calculateSolarTerms(2026);
        assertNotNull(dto.getSolarTerms());
        assertEquals(24, dto.getSolarTerms().size());
    }

    private void assertTerm(SolarTermYearDTO dto, int order, String name, LocalDate expected) {
        SolarTermDTO t = dto.getSolarTerms().get(order - 1);
        assertEquals(order, t.getOrder());
        assertEquals(name, t.getName());
        long diff = Math.abs(t.getDate().toEpochDay() - expected.toEpochDay());
        assertTrue(diff <= 1, name + " 计算日期 " + t.getDate() + " 与期望 " + expected + " 相差 " + diff + " 天");
    }
}