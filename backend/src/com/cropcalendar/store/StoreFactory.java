package com.cropcalendar.store;

import com.cropcalendar.config.AppConfig;

/**
 * 按配置返回文件或 MySQL 后端（T30）。
 *
 * 切到 mysql 但驱动缺失（或没配置连接串）时，回落到 file 并打日志 ——
 * 这是"切换失败可回滚到文件存储，数据不丢"的兜底：服务仍能启动，读写走原文件。
 */
public final class StoreFactory {

    private StoreFactory() {
    }

    /** 普通集合（键=userId）。表名/目录名 = collection。 */
    public static BlobStore forCollection(String collection) {
        return mysqlOrFile(collection, "var/" + collection);
    }

    /** 用户业务数据（我的作物/地块）沿用 auth.data.dir，方便运维改位置。 */
    public static BlobStore userData() {
        String dir = AppConfig.authDataDir();
        return mysqlOrFile("userdata", dir.isBlank() ? "var/userdata" : dir);
    }

    private static BlobStore mysqlOrFile(String collection, String fileDir) {
        if ("mysql".equalsIgnoreCase(AppConfig.storeBackend())) {
            if (mysqlAvailable()) {
                return new MysqlBlobStore(collection);
            }
            System.err.println("[store] 配置要求 mysql，但驱动/连接不可用，已回落到 file（数据不丢）");
        }
        return new FileBlobStore(fileDir);
    }

    private static boolean mysqlAvailable() {
        // 兼容两种驱动：Oracle 的 mysql-connector-j 与 MariaDB 的 mariadb-java-client。
        // 只要 classpath 上有其一，DriverManager 就会自动加载并连库。
        for (String driver : new String[]{"com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver"}) {
            try {
                Class.forName(driver);
                return true;
            } catch (ClassNotFoundException ignore) {
                // 试下一个
            }
        }
        return false;
    }
}
