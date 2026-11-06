package com.cropcalendar.service.user;

import com.cropcalendar.store.BlobStore;
import com.cropcalendar.store.StoreFactory;

import java.io.IOException;

/**
 * 用户业务数据的存储（我的作物 / 我的地块 / 搜索历史）。
 *
 * 结构上刻意做成「不透明 JSON」：后端不解析里面有什么，
 * 只负责按 userId 存取。这样前端加字段不用改后端，
 * 也不会因为某个字段改名把老数据读崩。
 *
 * 一个用户一个文件：写入时只影响自己那份，不会像单文件全量库那样
 * 一个人保存就把所有人的数据重写一遍。
 */
public final class UserDataStore {

    /** 单用户上限 512KB —— 正常几十条记录也就几十 KB，超了基本是异常 */
    public static final int MAX_BYTES = 512 * 1024;

    public static String read(String userId) {
        return store().read(userId);
    }

    public static void write(String userId, String json) throws IOException {
        store().write(userId, json);
    }

    /** 删号时一并删掉这个人的数据 */
    public static void delete(String userId) throws IOException {
        store().delete(userId);
    }

    /** 后端由 store.backend 配置决定：file（默认）或 mysql（驱动可用时）。 */
    private static BlobStore store() {
        return StoreFactory.userData();
    }

    private UserDataStore() {
    }
}
