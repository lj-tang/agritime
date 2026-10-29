package com.zhinongshi.calc.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作物农事规程实体（表 crop_procedure）。
 * 存储格式：播种后 N 天执行作业（相对天数，不写死绝对日期）。
 */
@Data
@TableName("crop_procedure")
public class CropProcedure {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作物品种标识 */
    private String cropId;

    /** 作物名称 */
    private String cropName;

    /** 作业名称 */
    private String jobName;

    /** 作业类型: SPRAY/FERTILIZE/IRRIGATE/HARVEST */
    private String jobType;

    /** 播种后相对天数 */
    private Integer daysAfterSowing;

    /** 作业描述 */
    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}