package com.cropcalendar.store;

import com.cropcalendar.service.user.UserDataStore;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * T30 存储层单测（不连 MySQL，只验证可离线复现的部分）：
 *  - 文件后端读写删 round-trip
 *  - UserDataStore 现在走 StoreFactory，默认后端是 file，行为与原来一致
 *  - MySQL 后端能正常构造（表名清洗），backend() 返回 mysql
 *  - StoreFactory 默认返回 file 后端
 */
public class StoreTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // ---- 1. 文件后端 round-trip ----
        Path dir = Files.createTempDirectory("store-test");
        BlobStore file = new FileBlobStore(dir.toString());
        eq("file", file.backend(), "文件后端标识");
        eq("{}", file.read("nobody"), "不存在的键应返回 {}");
        file.write("u1", "{\"plots\":[1]}");
        eq("{\"plots\":[1]}", file.read("u1"), "写后能读回");
        file.write("u1", "{\"plots\":[1,2]}");
        eq("{\"plots\":[1,2]}", file.read("u1"), "覆盖写生效");
        file.delete("u1");
        eq("{}", file.read("u1"), "删后读回 {}");

        // 键清洗：非法字符替换为下划线，两个不同非法键不应互相污染
        file.write("a/b", "x");
        eq("x", file.read("a_b"), "键清洗后同一文件");

        // ---- 2. UserDataStore 走 StoreFactory，默认 file ----
        String user = "u_store_test_" + System.currentTimeMillis();
        UserDataStore.write(user, "{\"crops\":[]}");
        eq("{\"crops\":[]}", UserDataStore.read(user), "UserDataStore 写读一致");
        UserDataStore.delete(user);
        eq("{}", UserDataStore.read(user), "UserDataStore 删后为空");

        // ---- 3. MySQL 后端可构造，backend() 正确（不实际连接）；连接失败回落文件 ----
        BlobStore mysql = new MysqlBlobStore("a b-c", dir.toString());
        eq("mysql", mysql.backend(), "MySQL 后端标识");
        eq("{}", mysql.read("nobody"), "无驱动时读取回落文件，不抛异常");
        // 无驱动时写也应降级到文件而不是抛异常
        mysql.write("fallback_key", "{\"v\":1}");
        eq("{\"v\":1}", new FileBlobStore(dir.toString()).read("fallback_key"), "连接失败时写应落到文件后端");
        // 删除也应同步删文件镜像：否则 MySQL 挂了会把已删数据「复活」
        mysql.write("to_delete", "{\"v\":1}");
        mysql.delete("to_delete");
        eq("{}", new FileBlobStore(dir.toString()).read("to_delete"), "删除应同步文件镜像");

        // ---- 4. StoreFactory 默认 file ----
        eq("file", StoreFactory.forCollection("userdata").backend(), "默认后端应为 file");

        System.out.println("StoreTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void eq(String expected, String actual, String msg) {
        check(expected == null ? actual == null : expected.equals(actual),
                msg + "，期望「" + expected + "」实际「" + actual + "」");
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg);
        }
    }
}
