package com.zhinongshi.calc.dto;

import com.zhinongshi.calc.common.ResultWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 作物识别适配结果 DTO（模块6 输出）。
 * <p>JSON 样例（识别成功且规程存在）:
 * <pre>{"recognizeStatus":"SUCCESS","needManualConfirm":true,"procedureExists":true,
 * "topCandidate":{"cropId":"C001","cropName":"水稻","confidence":0.92},
 * "candidates":[{"cropId":"C001","cropName":"水稻","confidence":0.92}],
 * "similarCrops":[],"promptMessage":"请人工确认识别结果","errorCode":null,"message":"success"}</pre></p>
 * <p>JSON 样例（识别成功但无规程，已推荐相近品种）:
 * <pre>{"recognizeStatus":"PROCEDURE_NOT_FOUND","needManualConfirm":true,"procedureExists":false,
 * "topCandidate":{"cropId":"X001","cropName":"稗草","confidence":0.88},
 * "candidates":[...],"similarCrops":[{"cropId":"C003","cropName":"玉米","confidence":0.0}],
 * "promptMessage":"未找到「稗草」的农事规程，以下为相近作物，请选择确认",
 * "errorCode":4007,"message":"识别作物无农事规程，已推荐相近品种"}</pre></p>
 * <p>低置信度: recognizeStatus="LOW_CONFIDENCE",errorCode=4005；异常降级: "DEGRADED",errorCode=4006。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CropRecognizeDTO extends ResultWrapper {

    /** 识别状态: SUCCESS/LOW_CONFIDENCE/DEGRADED/PROCEDURE_NOT_FOUND */
    private String recognizeStatus;

    /** 是否需要人工确认（恒为 true，本模块不自动选定作物） */
    private Boolean needManualConfirm;

    /** 识别到的作物是否已有农事规程数据 */
    private Boolean procedureExists;

    /** 最高置信度候选项 */
    private CropCandidate topCandidate;

    /** 候选作物列表（按置信度降序） */
    private List<CropCandidate> candidates;

    /** 相近作物候选列表（当识别品种无规程时，按名称编辑距离推荐 top3） */
    private List<CropCandidate> similarCrops;

    /** 前端展示提示语 */
    private String promptMessage;
}