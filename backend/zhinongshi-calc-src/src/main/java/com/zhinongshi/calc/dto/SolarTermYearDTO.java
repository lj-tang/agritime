package com.zhinongshi.calc.dto;

import com.zhinongshi.calc.common.ResultWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 某年份 24 节气结果 DTO（模块4 输出）。
 * <p>JSON 样例:
 * <pre>{"year":2026,"solarTerms":[{"order":1,"name":"立春","date":"2026-02-04"}],
 * "errorCode":null,"message":"success"}</pre></p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SolarTermYearDTO extends ResultWrapper {

    /** 年份 */
    private Integer year;

    /** 24 节气列表（按 order 1-24 排序） */
    private List<SolarTermDTO> solarTerms;
}