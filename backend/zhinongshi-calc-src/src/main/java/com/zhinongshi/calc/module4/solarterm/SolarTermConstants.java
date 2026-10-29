package com.zhinongshi.calc.module4.solarterm;

/**
 * 24 节气常量表。
 * <p>order 1-24 从立春开始；小寒(23)、大寒(24) 落次年。</p>
 */
public final class SolarTermConstants {

    private SolarTermConstants() {
    }

    /** 24 节气名称（order 1-24） */
    public static final String[] NAMES = {
            "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
            "立夏", "小满", "芒种", "夏至", "小暑", "大暑",
            "立秋", "处暑", "白露", "秋分", "寒露", "霜降",
            "立冬", "小雪", "大雪", "冬至", "小寒", "大寒"
    };

    /** 各节气对应太阳黄经度数（立春=315°，春分=0°，冬至=270°...） */
    public static final double[] LONGITUDES = {
            315, 330, 345, 0, 15, 30,
            45, 60, 75, 90, 105, 120,
            135, 150, 165, 180, 195, 210,
            225, 240, 255, 270, 285, 300
    };

    /** 各节气近似公历月（用于牛顿迭代初值） */
    public static final int[] APPROX_MONTH = {
            2, 2, 3, 3, 4, 4,
            5, 5, 6, 6, 7, 7,
            8, 8, 9, 9, 10, 10,
            11, 11, 12, 12, 1, 1
    };

    /** 各节气近似公历日（用于牛顿迭代初值） */
    public static final int[] APPROX_DAY = {
            4, 19, 6, 20, 5, 20,
            5, 21, 6, 21, 7, 23,
            7, 23, 7, 23, 8, 23,
            7, 22, 7, 22, 5, 20
    };

    /** 小寒、大寒（index 22,23）落次年 */
    public static boolean isNextYear(int index) {
        return index >= 22;
    }
}