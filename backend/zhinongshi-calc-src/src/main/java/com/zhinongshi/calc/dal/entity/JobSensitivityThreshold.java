package com.zhinongshi.calc.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业敏感度阈值配置实体（表 job_sensitivity_threshold）。
 * 配置驱动，不硬编码阈值。
 */
@Data
@TableName("job_sensitivity_threshold")
public class JobSensitivityThreshold {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作业类型 */
    private String jobType;

    /** 气象因子: rainfall/temperature/windSpeed/cumulativeRainfall */
    private String weatherFactor;

    /** 阈值下限(含) */
    private BigDecimal thresholdMin;

    /** 阈值上限(含) */
    private BigDecimal thresholdMax;

    /** 命中该区间对应风险等级: GREEN/YELLOW/RED */
    private String riskLevel;

    /** 阈值说明 */
    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}