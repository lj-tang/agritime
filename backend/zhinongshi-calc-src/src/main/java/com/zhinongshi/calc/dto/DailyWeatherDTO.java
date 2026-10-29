package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 单日气象指标 DTO（WeatherWindowDTO.dailyWeather 元素）。
 * <p>JSON 样例:
 * <pre>{"date":"2026-05-10","rainfall":2.5,"avgTemperature":22.3,"windSpeed":3.5}</pre></p>
 */
@Data
public class DailyWeatherDTO {

    /** 日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 降雨量(mm) */
    private Double rainfall;

    /** 日均温度(℃) */
    private Double avgTemperature;

    /** 风力(级) */
    private Double windSpeed;
}