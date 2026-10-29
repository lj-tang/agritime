package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.JobType;

import java.util.List;

/**
 * 作业敏感度规则包策略接口。
 * <p>每个作业类型对应一个规则包实现，由 RuleScoreService 按 JobType 路由调用。</p>
 */
public interface RulePackage {

    /** 本规则包支持的作业类型。 */
    JobType supportJobType();

    /**
     * 评估窗口气象，输出适宜等级、最差日、风险原因。
     *
     * @param window     7 天气象窗口
     * @param thresholds 该作业类型的阈值配置
     * @return 评估结果
     */
    RuleEvalResult evaluate(WeatherWindowDTO window, List<JobSensitivityThreshold> thresholds);

    /**
     * 判断某因子值是否命中指定风险等级的阈值区间。
     *
     * @param value      气象因子值
     * @param thresholds 阈值配置列表
     * @param factor     气象因子名
     * @param riskLevel  风险等级 GREEN/YELLOW/RED
     * @return 命中返回 true
     */
    default boolean hitFactor(double value, List<JobSensitivityThreshold> thresholds,
                              String factor, String riskLevel) {
        for (JobSensitivityThreshold t : thresholds) {
            if (!factor.equals(t.getWeatherFactor()) || !riskLevel.equals(t.getRiskLevel())) {
                continue;
            }
            double min = t.getThresholdMin() != null ? t.getThresholdMin().doubleValue() : Double.NEGATIVE_INFINITY;
            double max = t.getThresholdMax() != null ? t.getThresholdMax().doubleValue() : Double.POSITIVE_INFINITY;
            if (value >= min && value <= max) {
                return true;
            }
        }
        return false;
    }
}