package com.zhinongshi.calc.enums;

/**
 * 统一错误码枚举。
 * <p>异常不直接向外抛出，统一封装为携带 errorCode/message 的结果对象。</p>
 * <p>JSON 样例: errorCode=4003, message="天气接口超时"</p>
 */
public enum ErrorCode {
    OK(0, "成功"),
    CROP_PROCEDURE_MISSING(4001, "作物规程数据缺失"),
    DATE_ILLEGAL(4002, "日期格式非法"),
    WEATHER_TIMEOUT(4003, "天气接口超时"),
    WEATHER_NO_DATA(4004, "无可用气象数据"),
    LOW_CONFIDENCE(4005, "识别置信度不足"),
    RECOGNIZE_DEGRADED(4006, "识别服务异常降级"),
    CROP_PROCEDURE_NOT_FOUND(4007, "识别作物无农事规程，已推荐相近品种"),
    INTERNAL_ERROR(5001, "内部计算异常");

    private final int code;
    private final String desc;

    ErrorCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}