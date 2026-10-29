package com.zhinongshi.calc.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售节日配置实体（表 sales_festival）。
 * 配置驱动，不改代码即可新增/修改节日。
 */
@Data
@TableName("sales_festival")
public class SalesFestival {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 节日名称 */
    private String festivalName;

    /** 公历月(1-12) */
    private Integer festivalMonth;

    /** 公历日(1-31) */
    private Integer festivalDay;

    /** 备注 */
    private String remark;

    /** 是否启用: 1是 0否 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}