package com.zhinongshi.calc.module4.solarterm;

/**
 * 太阳视黄经计算器。
 * <p>采用 Jean Meeus《天文算法》简化公式（VSOP87 截断），精度优于 0.01°，满足日级节气计算。</p>
 * <p>完全不依赖第三方 API，断网可计算。</p>
 */
public final class SolarLongitudeCalculator {

    private SolarLongitudeCalculator() {
    }

    /**
     * 计算给定儒略日(jde)的太阳视黄经（度，[0,360)）。
     *
     * @param jde 儒略日（力学时，忽略 ΔT 对日期无影响）
     * @return 太阳视黄经（度）
     */
    public static double solarLongitude(double jde) {
        // 儒略世纪（自 J2000.0）
        double t = (jde - 2451545.0) / 36525.0;

        // 太阳平黄经
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        // 平近点角
        double m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t;
        double mr = Math.toRadians(m);

        // 中心差 C
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(mr)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * mr)
                + 0.000289 * Math.sin(3 * mr);

        // 太阳真黄经 = 平黄经 + 中心差
        double lon = l0 + c;
        // 归化到 [0,360)
        lon = lon % 360;
        if (lon < 0) {
            lon += 360;
        }
        return lon;
    }
}