package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhinongshi.calc.common.ResultWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售节日窗口结果 DTO（模块5 输出）。
 * <p>JSON 样例:
 * <pre>{"referenceDate":"2026-05-10","festivals":[{"festivalName":"端午节","festivalDate":"2026-06-19","daysRemaining":40,"remark":"传统销售旺季"}],
 * "errorCode":null,"message":"success"}</pre></p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FestivalWindowDTO extends ResultWrapper {

    /** 当前农事节点日期（计算剩余天数的基准） */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate referenceDate;

    /** 销售节日列表 */
    private List<FestivalDTO> festivals;
}