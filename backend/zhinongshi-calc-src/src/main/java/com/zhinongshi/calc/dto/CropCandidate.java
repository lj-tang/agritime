package com.zhinongshi.calc.dto;

import lombok.Data;

/**
 * 作物识别候选项 DTO（模块6 输入/输出元素）。
 * <p>JSON 样例: {"cropId":"C001","cropName":"水稻","confidence":0.92}</p>
 */
@Data
public class CropCandidate {

    /** 作物品种标识 */
    private String cropId;

    /** 作物名称 */
    private String cropName;

    /** 置信度(0-1) */
    private Double confidence;
}