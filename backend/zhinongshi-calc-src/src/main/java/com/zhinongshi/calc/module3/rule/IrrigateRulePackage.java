package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 灌溉规则包（IRRIGATE）。
 * <p>检测降雨：近期有降雨 → 建议暂缓灌溉；无降雨 → 适宜。</p>
 */
@Component
public class IrrigateRulePackage implements RulePackage {

    @Override
    public JobType supportJobType() {
        return JobType.IRRIGATE;
    }

    @Override
    public RuleEvalResult evaluate(WeatherWindowDTO window, List<JobSensitivityThreshold> thresholds) {
        RuleEvalResult res = new RuleEvalResult();
        List<DailyWeatherDTO> days = window.getDailyWeather();
        if (days == null || days.isEmpty()) {
            res.setLevel(SuitabilityLevel.GREEN);
            return res;
        }

        DailyWeatherDTO maxRain = WorstDayPicker.maxRainfallDay(days);
        double maxR = maxRain != null && maxRain.getRainfall() != null ? maxRain.getRainfall() : 0;
        res.setWorstDay(maxRain != null ? maxRain.getDate() : null);

        if (hitFactor(maxR, thresholds, "rainfall", "RED")) {
            res.setLevel(SuitabilityLevel.RED);
            res.getRiskReasons().add("近期有降雨 " + maxR + "mm，建议暂缓灌溉");
        } else if (hitFactor(maxR, thresholds, "rainfall", "YELLOW")) {
            res.setLevel(SuitabilityLevel.YELLOW);
            res.getRiskReasons().add("近期少量降雨 " + maxR + "mm，谨慎灌溉");
        } else {
            res.setLevel(SuitabilityLevel.GREEN);
        }
        return res;
    }
}