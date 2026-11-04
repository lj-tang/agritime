package com.cropcalendar.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 统一时区（V5 T17）。
 *
 * 为什么不能直接 {@code LocalDate.now()}：容器的默认时区可能是 UTC，
 * 北京时间 8 点之前在 UTC 还是"昨天"。日历、提醒、工单的日期一旦偏移一天，
 * 用户看到的就是错的日子 —— 这类 bug 在本地开发（东八区）永远复现不出来。
 *
 * 所以全后端与"今天/现在"有关的时间都走这里，服务启动时还会把 JVM 默认时区
 * 一并固定，避免第三方库或漏改的调用点再受容器影响。
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppTime() {
    }

    /** 北京时间"今天" */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 北京时间"现在"（不含时区偏移，配合业务侧格式器使用） */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
