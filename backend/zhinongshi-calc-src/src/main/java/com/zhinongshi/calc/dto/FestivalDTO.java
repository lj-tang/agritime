package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 销售节日 DTO（FestivalWindowDTO.festivals 元素）。
 * <p>JSON 样例: {"festivalName":"中秋节","festivalDate":"2026-09-25","daysRemaining":138,"remark":"礼盒销售旺季"}</p>
 */
@Data
public class FestivalDTO {

    /** 节日名称 */
    private String festivalName;

    /** 公历日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate festivalDate;

    /** 距离参考日期剩余天数（跨年安全） */
    private Long daysRemaining;

    /** 备注 */
    private String remark;
}