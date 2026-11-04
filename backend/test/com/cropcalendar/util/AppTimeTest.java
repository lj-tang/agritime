package com.cropcalendar.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * T17 时区单测：业务时间必须固定在北京时间，与 JVM 默认时区无关。
 */
public class AppTimeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        eq("Asia/Shanghai", AppTime.ZONE.getId(), "时区必须是 Asia/Shanghai");

        ZoneId sh = ZoneId.of("Asia/Shanghai");
        eq(LocalDate.now(sh), AppTime.today(), "today() 应等于北京时间的今天");

        long diff = Math.abs(ChronoUnit.SECONDS.between(AppTime.now(), LocalDateTime.now(sh)));
        check(diff <= 2, "now() 与北京时间相差应在 2 秒内，实际 " + diff + " 秒");

        // 固定时区后，即使 JVM 跑在 UTC，"今天"也不会被算成前一天/后一天
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            eq(LocalDate.now(sh), AppTime.today(), "JVM 切到 UTC 后 today() 仍应是北京时间的今天");
        } finally {
            java.util.TimeZone.setDefault(original);
        }

        System.out.println("AppTimeTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg);
        }
    }

    private static void eq(Object expected, Object actual, String msg) {
        check(expected == null ? actual == null : expected.equals(actual),
                msg + "，期望「" + expected + "」实际「" + actual + "」");
    }
}
