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
 */
public final class MysqlBlobStore implements BlobStore {

    private final String table;

    public MysqlBlobStore(String table) {
        this.table = safe(table);
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
            System.err.println("[store:mysql] 读取失败 " + key + "：" + e.getMessage());
            return "{}";
        }
    }

    @Override
    public void write(String key, String json) throws IOException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO " + table + " (k, data, updated_at) VALUES (?, ?, NOW())")) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("MySQL 写入失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE k = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("MySQL 删除失败：" + e.getMessage(), e);
        }
    }

    private static String safe(String t) {
        if (t == null || t.isBlank()) return "store_blobs";
        return t.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
