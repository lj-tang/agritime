package com.cropcalendar.store;

import java.io.IOException;

/**
 * 用户业务数据的键值存储抽象（T30）。
 *
 * 键 = userId，值 = 一段不透明 JSON。后端不解析内容，只按 userId 存取。
 * 文件后端一个用户一个文件；MySQL 后端一张表按主键存取。
 *
 * 让「地块 / 作物 / 设备 / 提醒」这类跨会话、跨设备的数据能在
 * 文件与 MySQL 两种后端之间切换，业务接口不变。
 */
public interface BlobStore {

    /** 读一段 JSON；不存在返回 "{}"，读失败也返回 "{}" 不抛异常。 */
    String read(String key);

    /** 写一段 JSON；落盘失败抛 IOException，由调用方决定降级。 */
    void write(String key, String json) throws IOException;

    /** 删除一段数据（账号注销时用）。 */
    void delete(String key) throws IOException;

    /** 后端标识：file 或 mysql，用于健康检查与日志。 */
    String backend();
}
