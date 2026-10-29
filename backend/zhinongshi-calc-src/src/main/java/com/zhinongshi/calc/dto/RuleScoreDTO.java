package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhinongshi.calc.common.ResultWrapper;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 作业敏感度打分结果 DTO（模块3 输出）。
 * <p>JSON 样例:
 * <pre>{"jobType":"SPRAY","suitability":"RED","originalDate":"2026-05-10","suggestedDate":"2026-05-13",
 * "worstDay":"2026-05-12","riskReasons":["窗口内最大日降雨量15.2mm超过打药阈值10mm"],
 * "errorCode":null,"message":"建议推迟打药作业"}</pre></p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RuleScoreDTO extends ResultWrapper {

    /** 作业类型 code */
    private String jobType;

    /** 适宜状态: GREEN/YELLOW/RED */
    private SuitabilityLevel suitability;

    /** 原计划执行日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate originalDate;

    /** 建议执行日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate suggestedDate;

    /** 窗口内最差日 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate worstDay;

    /** 风险原因文本说明（逐条） */
    private List<String> riskReasons;
}