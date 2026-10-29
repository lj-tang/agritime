package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 采收规则包（HARVEST）。
 * <p>遇降雨风险 → 建议提前抢收（RED），suggestedDate 取首个降雨风险日前 1 天；
 * 无降雨 → 适宜。</p>
 */
@Component
public class HarvestRulePackage implements RulePackage {

    @Override
    public JobType supportJobType() {
        return JobType.HARVEST;
    }

    @Override
    public RuleEvalResult evaluate(WeatherWindowDTO window, List<JobSensitivityThreshold> thresholds) {
        RuleEvalResult res = new RuleEvalResult();
        List<DailyWeatherDTO> days = window.getDailyWeather();
        if (days == null || days.isEmpty()) {
            res.setLevel(SuitabilityLevel.GREEN);
            return res;
        }

        double redThreshold = getRedRainThreshold(thresholds);
        LocalDate firstRainy = WorstDayPicker.firstRainyDayOver(days, redThreshold);
        if (firstRainy != null) {
            // 命中降雨风险，建议提前抢收
            res.setLevel(SuitabilityLevel.RED);
            res.setWorstDay(firstRainy);
            res.setSuggestAdvance(true);
            res.setAdvanceDate(firstRainy.minusDays(1));
            res.getRiskReasons().add("窗口内 " + firstRainy + " 存在降雨风险，建议在 "
                    + firstRainy.minusDays(1) + " 前抢收");
            return res;
        }

        DailyWeatherDTO maxRain = WorstDayPicker.maxRainfallDay(days);
        double maxR = maxRain != null && maxRain.getRainfall() != null ? maxRain.getRainfall() : 0;
        res.setWorstDay(maxRain != null ? maxRain.getDate() : null);

        if (hitFactor(maxR, thresholds, "rainfall", "YELLOW")) {
            res.setLevel(SuitabilityLevel.YELLOW);
            res.getRiskReasons().add("少量降雨 " + maxR + "mm，关注采收时机");
        } else {
            res.setLevel(SuitabilityLevel.GREEN);
        }
        return res;
    }

    /** 从阈值表取降雨 RED 下限，默认 10mm。 */
    private double getRedRainThreshold(List<JobSensitivityThreshold> thresholds) {
        for (JobSensitivityThreshold t : thresholds) {
            if ("rainfall".equals(t.getWeatherFactor()) && "RED".equals(t.getRiskLevel())
                    && t.getThresholdMin() != null) {
                return t.getThresholdMin().doubleValue();
            }
        }
        return 10.0;
    }
}