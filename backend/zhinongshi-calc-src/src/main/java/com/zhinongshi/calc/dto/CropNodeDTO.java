package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 农事节点 DTO（CropScheduleDTO.nodes 元素）。
 * <p>JSON 样例:
 * <pre>{"nodeId":1,"jobType":"SPRAY","jobTypeName":"打药","daysAfterSowing":30,"executeDate":"2026-05-10","description":"分蘖期病虫害防治"}</pre></p>
 */
@Data
public class CropNodeDTO {

    /** 节点序号 */
    private Integer nodeId;

    /** 作业类型 code（SPRAY/FERTILIZE/IRRIGATE/HARVEST） */
    private String jobType;

    /** 作业类型中文名 */
    private String jobTypeName;

    /** 播种后相对天数 */
    private Integer daysAfterSowing;

    /** 换算后执行日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate executeDate;

    /** 作业描述 */
    private String description;
}