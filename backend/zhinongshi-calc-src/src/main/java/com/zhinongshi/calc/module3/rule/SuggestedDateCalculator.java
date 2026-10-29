package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;

import java.time.LocalDate;

/**
 * suggestedDate 推算工具（spec §4.3.5a）。
 * <ul>
 *   <li>RED（建议推迟）：suggestedDate = worstDay + 1天</li>
 *   <li>RED（采收抢收）：suggestedDate = 首个降雨风险日前 1 天</li>
 *   <li>GREEN / YELLOW：suggestedDate = originalDate</li>
 * </ul>
 * <p>边界说明：若采收抢收计算出的 suggestedDate 早于 originalDate，业务上代表需要立刻抢收，
 * 该结果允许（不强制钳制到 originalDate 之后），由后端甲交给前端展示。</p>
 */
public final class SuggestedDateCalculator {

    private SuggestedDateCalculator() {
    }

    /**
     * 推算建议执行日期。
     *
     * @param level          适宜等级
     * @param jobType        作业类型
     * @param originalDate   原计划日期
     * @param worstDay       窗口内最差日
     * @param suggestAdvance 是否建议提前抢收
     * @param advanceDate    抢收建议日期（首个降雨风险日前 1 天）
     * @return 建议执行日期
     */
    public static LocalDate calculate(SuitabilityLevel level, JobType jobType,
                                      LocalDate originalDate, LocalDate worstDay,
                                      boolean suggestAdvance, LocalDate advanceDate) {
        if (level == SuitabilityLevel.RED) {
            // 采收抢收：取首个降雨风险日前 1 天
            if (jobType == JobType.HARVEST && suggestAdvance && advanceDate != null) {
                return advanceDate;
            }
            // 建议推迟：最差日后 1 天
            if (worstDay != null) {
                return worstDay.plusDays(1);
            }
            return originalDate;
        }
        // GREEN / YELLOW：维持原计划
        return originalDate;
    }
}