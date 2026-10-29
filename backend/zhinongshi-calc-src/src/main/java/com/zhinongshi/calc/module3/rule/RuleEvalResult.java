package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.enums.SuitabilityLevel;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则包评估中间结果。
 */
@Data
public class RuleEvalResult {

    /** 适宜等级 */
    private SuitabilityLevel level;

    /** 窗口内最差日 */
    private LocalDate worstDay;

    /** 风险原因文本（逐条） */
    private List<String> riskReasons = new ArrayList<>();

    /** 是否建议提前抢收（采收规则包专用） */
    private boolean suggestAdvance;

    /** 抢收建议日期（首个降雨风险日前 1 天） */
    private LocalDate advanceDate;
}