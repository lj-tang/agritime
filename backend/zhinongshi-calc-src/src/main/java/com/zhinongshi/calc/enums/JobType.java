package com.zhinongshi.calc.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 农事作业类型枚举。
 * <p>JSON 样例: "SPRAY" / "FERTILIZE" / "IRRIGATE" / "HARVEST"</p>
 */
public enum JobType {
    SPRAY("SPRAY", "打药"),
    FERTILIZE("FERTILIZE", "施肥"),
    IRRIGATE("IRRIGATE", "灌溉"),
    HARVEST("HARVEST", "采收");

    private final String code;
    private final String desc;

    JobType(String code, String desc) {
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

    /** 根据 code 解析枚举，忽略大小写；非法返回 null。 */
    public static JobType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (JobType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}