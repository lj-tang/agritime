package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 单个节气 DTO（SolarTermYearDTO.solarTerms 元素）。
 * <p>JSON 样例: {"order":1,"name":"立春","date":"2026-02-04"}</p>
 */
@Data
public class SolarTermDTO {

    /** 节气序号 1-24 */
    private Integer order;

    /** 节气名称 */
    private String name;

    /** 公历日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;
}