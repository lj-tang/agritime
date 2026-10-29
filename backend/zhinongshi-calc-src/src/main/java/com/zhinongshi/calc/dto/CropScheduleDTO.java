package com.zhinongshi.calc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhinongshi.calc.common.ResultWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 作物农事节点列表结果 DTO（模块1 输出）。
 * <p>JSON 样例:
 * <pre>{"cropId":"C001","cropName":"水稻","sowingDate":"2026-04-10","nodeCount":8,
 * "nodes":[{"nodeId":1,"jobType":"SPRAY","jobTypeName":"打药","daysAfterSowing":30,"executeDate":"2026-05-10","description":"分蘖期病虫害防治"}],
 * "errorCode":null,"message":"success"}</pre></p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CropScheduleDTO extends ResultWrapper {

    /** 作物品种标识 */
    private String cropId;

    /** 作物名称 */
    private String cropName;

    /** 播种日期 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate sowingDate;

    /** 节点数量 */
    private Integer nodeCount;

    /** 农事节点列表 */
    private List<CropNodeDTO> nodes;
}