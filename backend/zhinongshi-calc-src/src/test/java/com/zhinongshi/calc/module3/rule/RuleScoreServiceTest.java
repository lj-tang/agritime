package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dal.mapper.JobSensitivityThresholdMapper;
import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.RuleScoreDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * RuleScoreService 单元测试：4 规则包、降雨临界、suggestedDate 推算、采收抢收。
 */
@ExtendWith(MockitoExtension.class)
class RuleScoreServiceTest {

    @Mock
    private JobSensitivityThresholdMapper mapper;

    private RuleScoreService service;

    @BeforeEach
    void setUp() {
        List<RulePackage> pkgs = List.of(
                new SprayRulePackage(), new FertilizeRulePackage(),
                new IrrigateRulePackage(), new HarvestRulePackage());
        service = new RuleScoreService(mapper, pkgs);
    }

    private static final LocalDate ORIG = LocalDate.of(2026, 5, 10);

    @Test
    void spray_red_rainfall_suggestedDatePlusOne() {
        when(mapper.selectByJobType("SPRAY")).thenReturn(sprayThresholds());
        WeatherWindowDTO w = windowOneBadDay(15.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.SPRAY, w, ORIG);

        assertEquals(SuitabilityLevel.RED, dto.getSuitability());
        // worstDay=5-10, suggestedDate=5-11
        assertEquals(LocalDate.of(2026, 5, 11), dto.getSuggestedDate());
        assertFalse(dto.getRiskReasons().isEmpty());
    }

    @Test
    void spray_yellow_suggestedDateEqualsOriginal() {
        when(mapper.selectByJobType("SPRAY")).thenReturn(sprayThresholds());
        WeatherWindowDTO w = windowOneBadDay(7.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.SPRAY, w, ORIG);

        assertEquals(SuitabilityLevel.YELLOW, dto.getSuitability());
        assertEquals(ORIG, dto.getSuggestedDate());
    }

    @Test
    void spray_green() {
        when(mapper.selectByJobType("SPRAY")).thenReturn(sprayThresholds());
        WeatherWindowDTO w = windowOneBadDay(2.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.SPRAY, w, ORIG);

        assertEquals(SuitabilityLevel.GREEN, dto.getSuitability());
        assertEquals(ORIG, dto.getSuggestedDate());
    }

    @Test
    void spray_red_wind() {
        when(mapper.selectByJobType("SPRAY")).thenReturn(sprayThresholds());
        WeatherWindowDTO w = windowOneBadDay(2.0, 6.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.SPRAY, w, ORIG);
        assertEquals(SuitabilityLevel.RED, dto.getSuitability());
    }

    @Test
    void rainfallBoundary_exact10_isRed() {
        when(mapper.selectByJobType("SPRAY")).thenReturn(sprayThresholds());
        WeatherWindowDTO w = windowOneBadDay(10.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.SPRAY, w, ORIG);
        assertEquals(SuitabilityLevel.RED, dto.getSuitability());
    }

    @Test
    void irrigate_red_rainfall() {
        when(mapper.selectByJobType("IRRIGATE")).thenReturn(irrigateThresholds());
        WeatherWindowDTO w = windowOneBadDay(15.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.IRRIGATE, w, ORIG);
        assertEquals(SuitabilityLevel.RED, dto.getSuitability());
    }

    @Test
    void harvest_red_advanceDateBeforeOriginal() {
        when(mapper.selectByJobType("HARVEST")).thenReturn(harvestThresholds());
        WeatherWindowDTO w = windowOneBadDay(15.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.HARVEST, w, ORIG);

        assertEquals(SuitabilityLevel.RED, dto.getSuitability());
        // 抢收 suggestedDate = 首个降雨日(5-10) - 1 = 5-9，早于 originalDate
        assertEquals(LocalDate.of(2026, 5, 9), dto.getSuggestedDate());
        assertTrue(dto.getSuggestedDate().isBefore(ORIG));
    }

    @Test
    void harvest_green() {
        when(mapper.selectByJobType("HARVEST")).thenReturn(harvestThresholds());
        WeatherWindowDTO w = windowOneBadDay(0.0, 2.0, 22.0);

        RuleScoreDTO dto = service.scoreJob(JobType.HARVEST, w, ORIG);
        assertEquals(SuitabilityLevel.GREEN, dto.getSuitability());
        assertEquals(ORIG, dto.getSuggestedDate());
    }

    // ---------- helpers ----------

    private WeatherWindowDTO windowOneBadDay(double badRain, double badWind, double badTemp) {
        WeatherWindowDTO w = new WeatherWindowDTO();
        List<DailyWeatherDTO> days = new ArrayList<>();
        for (int i = -3; i <= 3; i++) {
            DailyWeatherDTO d = new DailyWeatherDTO();
            d.setDate(ORIG.plusDays(i));
            if (i == 0) {
                d.setRainfall(badRain);
                d.setWindSpeed(badWind);
                d.setAvgTemperature(badTemp);
            } else {
                d.setRainfall(0.0);
                d.setWindSpeed(2.0);
                d.setAvgTemperature(22.0);
            }
            days.add(d);
        }
        w.setDailyWeather(days);
        return w;
    }

    private List<JobSensitivityThreshold> sprayThresholds() {
        return List.of(
                thr("rainfall", 10, 9999, "RED"), thr("rainfall", 5, 10, "YELLOW"),
                thr("windSpeed", 5, 99, "RED"), thr("windSpeed", 4, 5, "YELLOW"),
                thr("temperature", 35, 99, "RED"));
    }

    private List<JobSensitivityThreshold> irrigateThresholds() {
        return List.of(thr("rainfall", 10, 9999, "RED"), thr("rainfall", 3, 10, "YELLOW"));
    }

    private List<JobSensitivityThreshold> harvestThresholds() {
        return List.of(thr("rainfall", 10, 9999, "RED"), thr("rainfall", 3, 10, "YELLOW"));
    }

    private JobSensitivityThreshold thr(String factor, double min, double max, String level) {
        JobSensitivityThreshold t = new JobSensitivityThreshold();
        t.setWeatherFactor(factor);
        t.setThresholdMin(BigDecimal.valueOf(min));
        t.setThresholdMax(BigDecimal.valueOf(max));
        t.setRiskLevel(level);
        return t;
    }
}