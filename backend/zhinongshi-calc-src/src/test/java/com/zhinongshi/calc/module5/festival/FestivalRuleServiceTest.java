package com.zhinongshi.calc.module5.festival;

import com.zhinongshi.calc.dal.entity.SalesFestival;
import com.zhinongshi.calc.dal.mapper.SalesFestivalMapper;
import com.zhinongshi.calc.dto.FestivalDTO;
import com.zhinongshi.calc.dto.FestivalWindowDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * FestivalRuleService 单元测试：跨年 daysRemaining、窗口过滤、referenceDate 为 null。
 */
@ExtendWith(MockitoExtension.class)
class FestivalRuleServiceTest {

    @Mock
    private SalesFestivalMapper mapper;

    @InjectMocks
    private FestivalRuleService service;

    private List<SalesFestival> festivals() {
        return List.of(fest("春节", 1, 25), fest("中秋节", 9, 25), fest("冬至", 12, 22));
    }

    @Test
    void crossYear_daysRemaining() {
        when(mapper.selectEnabled()).thenReturn(festivals());

        // 12 月农事节点遇次年 1 月春节
        FestivalWindowDTO dto = service.queryFestivals(null, null, LocalDate.of(2026, 12, 20));

        FestivalDTO spring = dto.getFestivals().stream()
                .filter(f -> f.getFestivalName().equals("春节")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2027, 1, 25), spring.getFestivalDate());
        assertEquals(36L, spring.getDaysRemaining());
    }

    @Test
    void sameYear_daysRemaining() {
        when(mapper.selectEnabled()).thenReturn(festivals());

        FestivalWindowDTO dto = service.queryFestivals(null, null, LocalDate.of(2026, 5, 10));

        FestivalDTO mid = dto.getFestivals().stream()
                .filter(f -> f.getFestivalName().equals("中秋节")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 25), mid.getFestivalDate());
        assertEquals(138L, mid.getDaysRemaining());
    }

    @Test
    void nullReferenceDate_daysRemainingNull() {
        when(mapper.selectEnabled()).thenReturn(festivals());

        FestivalWindowDTO dto = service.queryFestivals(null, null, null);

        assertTrue(dto.isSuccess());
        dto.getFestivals().forEach(f -> assertNull(f.getDaysRemaining(), f.getFestivalName() + " daysRemaining 应为 null"));
    }

    @Test
    void windowFilter_onlyFuture() {
        when(mapper.selectEnabled()).thenReturn(festivals());

        // referenceDate=2026-12-20，春节跨年到 2027-01-25（未来），中秋 2026-09-25 已过但跨年构造会变 2027-09-25
        FestivalWindowDTO dto = service.queryFestivals(null, null, LocalDate.of(2026, 12, 20));
        assertTrue(dto.isSuccess());
        // 所有返回节日都应是未来（>= referenceDate）
        dto.getFestivals().forEach(f ->
                assertTrue(f.getFestivalDate().isAfter(LocalDate.of(2026, 12, 19)),
                        f.getFestivalName() + " 应在未来"));
    }

    private SalesFestival fest(String name, int m, int d) {
        SalesFestival f = new SalesFestival();
        f.setFestivalName(name);
        f.setFestivalMonth(m);
        f.setFestivalDay(d);
        f.setEnabled(1);
        return f;
    }
}