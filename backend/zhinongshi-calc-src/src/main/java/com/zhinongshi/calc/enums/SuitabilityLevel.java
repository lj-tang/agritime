package com.zhinongshi.calc.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 适宜度状态枚举。
 * <p>JSON 样例: "GREEN" / "YELLOW" / "RED"</p>
 * <ul>
 *   <li>GREEN 适宜：条件良好，可按计划执行</li>
 *   <li>YELLOW 谨慎：中间状态，需关注但可执行</li>
 *   <li>RED 建议调整：命中风险条件，建议推迟/抢收</li>
 * </ul>
 */
public enum SuitabilityLevel {
    GREEN("GREEN", "适宜"),
    YELLOW("YELLOW", "谨慎"),
    RED("RED", "建议调整");

    private final String code;
    private final String desc;

    SuitabilityLevel(String code, String desc) {
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