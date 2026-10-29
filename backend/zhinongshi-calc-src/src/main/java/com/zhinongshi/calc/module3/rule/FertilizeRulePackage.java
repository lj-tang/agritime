package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 施肥规则包（FERTILIZE）。
 * <p>怕累计暴雨冲刷、低温土壤吸收差。根据窗口累计降雨量、日均温度判定适宜等级。</p>
 */
@Component
public class FertilizeRulePackage implements RulePackage {

    @Override
    public JobType supportJobType() {
        return JobType.FERTILIZE;
    }

    @Override
    public RuleEvalResult evaluate(WeatherWindowDTO window, List<JobSensitivityThreshold> thresholds) {
        RuleEvalResult res = new RuleEvalResult();
        List<DailyWeatherDTO> days = window.getDailyWeather();
        if (days == null || days.isEmpty()) {
            res.setLevel(SuitabilityLevel.GREEN);
            return res;
        }

        // 累计降雨量
        double cumulativeRain = 0;
        for (DailyWeatherDTO d : days) {
            cumulativeRain += d.getRainfall() == null ? 0 : d.getRainfall();
        }
        // 最低日均温
        DailyWeatherDTO coldest = WorstDayPicker.minTempDay(days);
        double minTemp = coldest != null && coldest.getAvgTemperature() != null
                ? coldest.getAvgTemperature() : 0;

        boolean red = false;
        boolean yellow = false;

        if (hitFactor(cumulativeRain, thresholds, "cumulativeRainfall", "RED")) {
            red = true;
            res.getRiskReasons().add("窗口累计降雨量 " + cumulativeRain + "mm 超过冲刷阈值，肥料易流失");
        }
        if (hitFactor(cumulativeRain, thresholds, "cumulativeRainfall", "YELLOW")) {
            yellow = true;
        }
        if (hitFactor(minTemp, thresholds, "temperature", "RED")) {
            red = true;
            res.getRiskReasons().add("窗口最低日均温 " + minTemp + "℃，土壤吸收差");
        }

        // 最差日：累计降雨贡献最大日或最低温日
        DailyWeatherDTO worst = cumulativeRain >= Math.abs(minTemp)
                ? WorstDayPicker.maxRainfallDay(days) : coldest;
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