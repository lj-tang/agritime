package com.cropcalendar.user;

import com.cropcalendar.config.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        Path p = pathOf(userId);
        try {
            if (!Files.exists(p)) return "{}";
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[userdata] 读取失败 " + userId + "：" + e.getMessage());
            return "{}";
        }
    }

    public static void write(String userId, String json) throws IOException {
        Path p = pathOf(userId);
        Path dir = p.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path pathOf(String userId) {
        String safe = userId == null ? "anonymous" : userId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        String dir = AppConfig.authDataDir();
        return Path.of(dir.isBlank() ? "var/userdata" : dir, safe + ".json");
    }

    private UserDataStore() {
    }
}
