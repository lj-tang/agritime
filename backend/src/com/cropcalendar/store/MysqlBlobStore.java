package com.cropcalendar.store;

import com.cropcalendar.config.AppConfig;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MySQL 后端（T30）。
 *
 * 只用 JDK 自带的 {@code java.sql} 接口 —— 编译零依赖；
 * 真正跑起来需要在 classpath 上放 mysql-connector jar，并有一台可连的 MySQL。
 * 表结构见 {@code data/store-mysql.sql}。
 *
 * 表名会被清洗成 [A-Za-z0-9_]，拼进 SQL 不会注入；键与值走 PreparedStatement 占位。
 *
 * 健壮性：MySQL 连接失败时**降级到文件后端**，读写删都不丢数据、不 500 ——
 * 这对应任务书里"切换失败可回滚到文件存储、服务不可用时降级读本地"。
 * 文件后端同时充当「实时镜像」：写成功也同步一份到文件、删也同步删，
 * 保证 MySQL 挂掉后降级读到的本地数据是最新的，而不是迁移前那批旧数据。
 */
public final class MysqlBlobStore implements BlobStore {

    private final String table;
    private final FileBlobStore fileFallback;

    public MysqlBlobStore(String table, String fileDir) {
        this.table = safe(table);
        this.fileFallback = new FileBlobStore(fileDir);
    }

    @Override
    public String backend() {
        return "mysql";
    }

    private Connection open() throws SQLException {
        String url = AppConfig.mysqlJdbcUrl();
        if (url == null || url.isBlank()) {
            throw new SQLException("store.mysql.url 未配置");
        }
        return DriverManager.getConnection(url, AppConfig.mysqlUser(), AppConfig.mysqlPassword());
    }

    @Override
    public String read(String key) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT data FROM " + table + " WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
            return "{}";
        } catch (SQLException e) {
            warn("读取", key, e);
            return fileFallback.read(key);
        }
    }

    @Override
    public void write(String key, String json) throws IOException {
        boolean mysqlOk = true;
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO " + table + " (k, data, updated_at) VALUES (?, ?, NOW())")) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            warn("写入", key, e);
            mysqlOk = false;
        }
        // 文件既是 MySQL 失败时的唯一落点，也是成功时的实时镜像：
        // 否则 MySQL 一挂，「降级读本地」拿到的还是迁移前那批旧数据。
        try {
            fileFallback.write(key, json);
        } catch (IOException mirrorFail) {
            if (!mysqlOk) throw mirrorFail;   // 两个后端都写失败才向上抛
            // MySQL 成功、文件镜像失败：主数据已在库，忽略镜像错误
        }
    }

    @Override
    public void delete(String key) throws IOException {
        boolean mysqlOk = true;
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE k = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            warn("删除", key, e);
            mysqlOk = false;
        }
        // 同步删文件镜像：否则 MySQL 挂了之后会把已删的数据「复活」出来
        try {
            fileFallback.delete(key);
        } catch (IOException mirrorFail) {
            if (!mysqlOk) throw mirrorFail;
        }
    }

    private void warn(String action, String key, SQLException e) {
        System.err.println("[store:mysql] " + action + " " + key + " 失败，已降级到文件存储："
                + e.getMessage());
    }

    private static String safe(String t) {
        if (t == null || t.isBlank()) return "store_blobs";
        return t.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
