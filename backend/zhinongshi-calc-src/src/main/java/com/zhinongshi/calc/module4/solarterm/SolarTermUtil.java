package com.zhinongshi.calc.module4.solarterm;

import com.zhinongshi.calc.dto.SolarTermDTO;
import com.zhinongshi.calc.dto.SolarTermYearDTO;
import com.zhinongshi.calc.enums.ErrorCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 节气计算工具（模块4）。
 * <p>使用太阳黄经天文算法，Java 本地计算 24 节气公历日期。</p>
 * <p><b>完全不依赖第三方 API，断网也可以计算，无网络请求。</b></p>
 * <p>算法：寿星近似日期作初值 → 牛顿迭代求解太阳黄经达到目标度数的时刻 → 取公历日期。</p>
 */
public class SolarTermUtil {

    private static final double J2000 = 2451545.0;
    private static final double EPOCH_JD = 2440587.5; // 1970-01-01 00:00 UTC

    /**
     * 计算指定年份全部 24 节气。
     *
     * @param year 年份
     * @return 24 节气列表（order 1-24，按立春→大寒排序；小寒大寒落次年）
     */
    public SolarTermYearDTO calculateSolarTerms(int year) {
        SolarTermYearDTO dto = new SolarTermYearDTO();
        dto.setYear(year);

        if (year < 1900 || year > 2100) {
            dto.fail(ErrorCode.INTERNAL_ERROR, "年份超出支持范围(1900-2100)");
            dto.setSolarTerms(new ArrayList<>());
            return dto;
        }

        List<SolarTermDTO> list = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) {
            double target = SolarTermConstants.LONGITUDES[i];
            // 初值：小寒大寒落次年
            int y = SolarTermConstants.isNextYear(i) ? year + 1 : year;
            LocalDate guess = LocalDate.of(y,
                    SolarTermConstants.APPROX_MONTH[i],
                    SolarTermConstants.APPROX_DAY[i]);
            double jde = toJde(guess);

            // 牛顿迭代求解 solarLongitude(jde) = target
            for (int iter = 0; iter < 20; iter++) {
                double lon = SolarLongitudeCalculator.solarLongitude(jde);
                double diff = normalizeAngle(lon - target);
                if (Math.abs(diff) < 1e-7) {
                    break;
                }
                // 数值导数（太阳黄经变化率约 0.9856°/天）
                double eps = 0.5;
                double lon1 = SolarLongitudeCalculator.solarLongitude(jde + eps);
                double deriv = normalizeAngle(lon1 - lon) / eps;
                if (Math.abs(deriv) < 1e-9) {
                    break;
                }
                jde = jde - diff / deriv;
            }

            SolarTermDTO term = new SolarTermDTO();
            term.setOrder(i + 1);
            term.setName(SolarTermConstants.NAMES[i]);
            term.setDate(fromJde(jde));
            list.add(term);
        }

        dto.setSolarTerms(list);
        dto.success();
        return dto;
    }

    /** LocalDate → 儒略日 */
    private static double toJde(LocalDate date) {
        return date.toEpochDay() + EPOCH_JD;
    }

    /** 儒略日 → LocalDate（取当日） */
    private static LocalDate fromJde(double jde) {
        return LocalDate.ofEpochDay((long) Math.floor(jde - EPOCH_JD));
    }

    /** 角度归化到 [-180,180) */
    private static double normalizeAngle(double a) {
        a = a % 360;
        if (a > 180) {
            a -= 360;
        }
        if (a < -180) {
            a += 360;
        }
        return a;
    }
}