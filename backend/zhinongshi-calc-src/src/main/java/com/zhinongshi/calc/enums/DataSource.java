package com.zhinongshi.calc.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 数据来源枚举。
 * <p>JSON 样例: "real" / "cache" / "none"</p>
 * <ul>
 *   <li>real 实时接口数据</li>
 *   <li>cache 降级缓存数据（接口超时/网络异常，由后端甲填充）</li>
 *   <li>none 完全无数据</li>
 * </ul>
 */
public enum DataSource {
    REAL("real", "实时数据"),
    CACHE("cache", "缓存数据"),
    NONE("none", "无数据");

    private final String code;
    private final String desc;

    DataSource(String code, String desc) {
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