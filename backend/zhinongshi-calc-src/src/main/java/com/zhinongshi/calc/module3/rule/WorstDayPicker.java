package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dto.DailyWeatherDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 最差日选取工具。
 * <p>提供按各气象因子取极值所在日的辅助方法，供规则包选取"最差一天"。</p>
 * <p>核心原则：取窗口内最差一天，不取平均值。</p>
 */
public final class WorstDayPicker {

    private WorstDayPicker() {
    }

    /** 取窗口内降雨量最大的一天。 */
    public static DailyWeatherDTO maxRainfallDay(List<DailyWeatherDTO> days) {
        DailyWeatherDTO target = null;
        double max = Double.NEGATIVE_INFINITY;
        for (DailyWeatherDTO d : days) {
            double v = d.getRainfall() == null ? 0 : d.getRainfall();
            if (v > max) {
                max = v;
                target = d;
            }
        }
        return target;
    }

    /** 取窗口内风力最大的一天。 */
    public static DailyWeatherDTO maxWindDay(List<DailyWeatherDTO> days) {
        DailyWeatherDTO target = null;
        double max = Double.NEGATIVE_INFINITY;
        for (DailyWeatherDTO d : days) {
            double v = d.getWindSpeed() == null ? 0 : d.getWindSpeed();
            if (v > max) {
                max = v;
                target = d;
            }
        }
        return target;
    }

    /** 取窗口内日均温度最高的一天。 */
    public static DailyWeatherDTO maxTempDay(List<DailyWeatherDTO> days) {
        DailyWeatherDTO target = null;
        double max = Double.NEGATIVE_INFINITY;
        for (DailyWeatherDTO d : days) {
            double v = d.getAvgTemperature() == null ? 0 : d.getAvgTemperature();
            if (v > max) {
                max = v;
                target = d;
            }
        }
        return target;
    }

    /** 取窗口内日均温度最低的一天。 */
    public static DailyWeatherDTO minTempDay(List<DailyWeatherDTO> days) {
        DailyWeatherDTO target = null;
        double min = Double.POSITIVE_INFINITY;
        for (DailyWeatherDTO d : days) {
            double v = d.getAvgTemperature() == null ? 0 : d.getAvgTemperature();
            if (v < min) {
                min = v;
                target = d;
            }
        }
        return target;
    }

    /** 取窗口内首个降雨量超过阈值的日子。 */
    public static LocalDate firstRainyDayOver(List<DailyWeatherDTO> days, double threshold) {
        for (DailyWeatherDTO d : days) {
            double v = d.getRainfall() == null ? 0 : d.getRainfall();
            if (v >= threshold) {
                return d.getDate();
            }
        }
        return null;
    }
}