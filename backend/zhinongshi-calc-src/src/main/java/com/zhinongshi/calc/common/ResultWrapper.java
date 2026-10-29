package com.zhinongshi.calc.common;

import com.zhinongshi.calc.enums.ErrorCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一结果包装基类。
 * <p>所有顶层结果 DTO 继承本类，保证 errorCode / message 字段固定存在。</p>
 * <p>成功: errorCode=null, message="success"；失败/降级: errorCode=错误码, message=说明。</p>
 * <p>异常不外抛，内部 catch 后封装为失败结果返回，由后端甲编排层处理 HTTP 响应。</p>
 */
@Getter
@Setter
public abstract class ResultWrapper {

    private Integer errorCode;
    private String message;

    /** 标记成功。 */
    public void success() {
        this.errorCode = null;
        this.message = "success";
    }

    /** 标记成功并附带业务提示。 */
    public void success(String message) {
        this.errorCode = null;
        this.message = message;
    }

    /** 标记失败，使用错误码默认描述。 */
    public void fail(ErrorCode ec) {
        this.errorCode = ec.getCode();
        this.message = ec.getDesc();
    }

    /** 标记失败，使用自定义描述。 */
    public void fail(ErrorCode ec, String message) {
        this.errorCode = ec.getCode();
        this.message = message;
    }

    public boolean isSuccess() {
        return errorCode == null || errorCode == 0;
    }
}