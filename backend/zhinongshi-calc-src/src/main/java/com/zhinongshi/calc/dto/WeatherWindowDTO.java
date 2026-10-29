package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhinongshi.calc.common.ResultWrapper;
import com.zhinongshi.calc.enums.DataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 7 天气象窗口结果 DTO（模块2 输出）。
 * <p>JSON 样例:
 * <pre>{"longitude":116.40,"latitude":39.90,"startDate":"2026-05-10","endDate":"2026-05-16",
 * "dataSource":"real","dailyWeather":[{"date":"2026-05-10","rainfall":2.5,"avgTemperature":22.3,"windSpeed":3.5}],
 * "errorCode":null,"message":"success"}</pre></p>
 * <p>降级时 dataSource="cache"(errorCode=4003) 或 "none"(errorCode=4004)。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WeatherWindowDTO extends ResultWrapper {

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 窗口起始日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 窗口结束日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 数据来源: real/cache/none */
    private DataSource dataSource;

    /** 每日气象指标 */
    private List<DailyWeatherDTO> dailyWeather;
}