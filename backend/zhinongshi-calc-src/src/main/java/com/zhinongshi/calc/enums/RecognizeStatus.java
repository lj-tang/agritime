package com.zhinongshi.calc.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 作物识别状态枚举。
 * <p>JSON 样例: "SUCCESS" / "LOW_CONFIDENCE" / "DEGRADED" / "PROCEDURE_NOT_FOUND"</p>
 */
public enum RecognizeStatus {
    SUCCESS("SUCCESS", "识别成功，待人工确认"),
    LOW_CONFIDENCE("LOW_CONFIDENCE", "置信度不足，请手动搜索"),
    DEGRADED("DEGRADED", "识别异常降级，回落搜索框"),
    PROCEDURE_NOT_FOUND("PROCEDURE_NOT_FOUND", "识别作物无农事规程，已推荐相近品种");

    private final String code;
    private final String desc;

    RecognizeStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}