package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 打药规则包（SPRAY）。
 * <p>怕降雨、大风、高温。命中任意风险条件 → RED（建议推迟）；条件良好 → GREEN；中间状态 → YELLOW。</p>
 */
@Component
public class SprayRulePackage implements RulePackage {

    @Override
    public JobType supportJobType() {
        return JobType.SPRAY;
    }

    @Override
    public RuleEvalResult evaluate(WeatherWindowDTO window, List<JobSensitivityThreshold> thresholds) {
        RuleEvalResult res = new RuleEvalResult();
        List<DailyWeatherDTO> days = window.getDailyWeather();
        if (days == null || days.isEmpty()) {
            res.setLevel(SuitabilityLevel.GREEN);
            return res;
        }

        boolean red = false;
        boolean yellow = false;
        DailyWeatherDTO worst = null;
        double worstRisk = Double.NEGATIVE_INFINITY;

        for (DailyWeatherDTO d : days) {
            double rain = d.getRainfall() == null ? 0 : d.getRainfall();
            double wind = d.getWindSpeed() == null ? 0 : d.getWindSpeed();
            double temp = d.getAvgTemperature() == null ? 0 : d.getAvgTemperature();

            // 命中 RED 条件
            if (hitFactor(rain, thresholds, "rainfall", "RED")) {
                red = true;
                res.getRiskReasons().add(d.getDate() + " 日降雨量 " + rain + "mm 超过打药阈值");
            }
            if (hitFactor(wind, thresholds, "windSpeed", "RED")) {
                red = true;
                res.getRiskReasons().add(d.getDate() + " 风力 " + wind + " 级超过阈值");
            }
            if (hitFactor(temp, thresholds, "temperature", "RED")) {
                red = true;
                res.getRiskReasons().add(d.getDate() + " 高温 " + temp + "℃ 超过阈值");
            }
            // 命中 YELLOW 条件
            if (hitFactor(rain, thresholds, "rainfall", "YELLOW")) {
                yellow = true;
            }
            if (hitFactor(wind, thresholds, "windSpeed", "YELLOW")) {
                yellow = true;
            }

            // 风险打分取最差日（降雨 + 风力*2 + 超温部分）
            double risk = rain + wind * 2 + Math.max(temp - 30, 0);
            if (risk > worstRisk) {
                worstRisk = risk;
                worst = d;
            }
        }

        res.setWorstDay(worst != null ? worst.getDate() : null);
        if (red) {
            res.setLevel(SuitabilityLevel.RED);
        } else if (yellow) {
            res.setLevel(SuitabilityLevel.YELLOW);
        } else {
            res.setLevel(SuitabilityLevel.GREEN);
        }
        return res;
    }
}