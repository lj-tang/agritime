package com.cropcalendar.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 关键参数外置 —— 改配置不改代码。
 *
 * 优先级：环境变量 > application.properties > 内置默认值
 *
 * 为什么要做成三层？因为华为云 FunctionGraph 上不方便改文件，
 * 用环境变量就能临时把超时阈值调大调小，不用重新打包。
 * 这也是手册里「关键参数外置，别人也能调」那条要求的落地。
 */
public final class AppConfig {

    private static final Properties FILE = new Properties();

    static {
        Path p = Paths.get("application.properties");
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                FILE.load(in);
                System.out.println("[config] 已加载 " + p.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[config] 读取 application.properties 失败，使用内置默认值：" + e.getMessage());
            }
        } else {
            System.out.println("[config] 未找到 application.properties，使用内置默认值");
        }
    }

    /** HTTP 监听端口 */
    public static int port() {
        return intOf("server.port", 8080);
    }

    /** 上游（天气/识别）超时阈值：超过这个时间就降级返回缓存 */
    public static int upstreamTimeoutMs() {
        return intOf("upstream.timeout.ms", 800);
    }

    /** 缓存有效期 */
    public static int cacheTtlSeconds() {
        return intOf("cache.ttl.seconds", 900);
    }

    /** 计算层是否还在用 mock（乙未交付时为 true） */
    public static boolean mockMode() {
        return boolOf("compute.mock", true);
    }

    /** 节点前后各取几天的天气窗口 —— 打分用 */
    public static int weatherWindowDays() {
        return intOf("weather.window.days", 3);
    }

    /**
     * 【仅 mock 模式生效】人为制造上游延迟，单位毫秒。
     * 存在的唯一目的：让降级分支可以被演练 —— 降级路径不能测，就等于没写。
     * 乙交付真实实现后，这个参数自然失效。
     */
    public static int mockUpstreamDelayMs() {
        return intOf("mock.upstream.delay.ms", 0);
    }

    /** 大模型 API 端点（华为云 MaaS） */
    public static String llmApiUrl() {
        String v = raw("llm.api.url");
        return (v != null && !v.isBlank()) ? v.trim() : "";
    }

    /** 大模型 API 密钥 */
    public static String llmApiKey() {
        String v = raw("llm.api.key");
        return (v != null && !v.isBlank()) ? v.trim() : "";
    }

    /** 大模型名称 */
    public static String llmModel() {
        String v = raw("llm.model");
        return (v != null && !v.isBlank()) ? v.trim() : "openPangu-2.0-flash";
    }

    /** 大模型调用超时（毫秒） */
    public static int llmTimeoutMs() {
        return intOf("llm.timeout.ms", 10000);
    }

    /**
     * 高德 Web 服务 key（逆地理编码用）。
     * 留空则地图标点只能拿到 level=unknown —— 国内环境 Nominatim 不可达，
     * 没有 key 就拿不到任何中文地名，所以这个值要配。
     */
    public static String geoAmapKey() {
        String v = raw("geo.amap.key");
        return (v != null && !v.isBlank()) ? v.trim() : "";
    }

    /** 腾讯位置服务 key（可选）。两者都配时优先用腾讯，它的 recommend 字段更贴近小区名。 */
    public static String geoQqKey() {
        String v = raw("geo.qq.key");
        return (v != null && !v.isBlank()) ? v.trim() : "";
    }

    /** 账号库落盘路径（相对后端工作目录），默认 var/users.json */
    public static String authStorePath() {
        String v = raw("auth.store.path");
        return (v != null && !v.isBlank()) ? v.trim() : "var/users.json";
    }

    /**
     * 预置超管的初始密码。只在账号库为空、首次建库时生效；
     * 生产环境务必用环境变量 AUTH_ADMIN_PASSWORD 覆盖，别用代码里的默认值。
     */
    public static String authAdminPassword() {
        String v = raw("auth.admin.password");
        return (v != null && !v.isBlank()) ? v.trim() : "";
    }

    /** 用户业务数据的存放目录（我的作物/地块/历史），默认 var/userdata */
    public static String authDataDir() {
        String v = raw("auth.data.dir");
        return (v != null && !v.isBlank()) ? v.trim() : "var/userdata";
    }

    /** 站点开关文件路径（禁用搜索 / 禁用大模型），默认 var/settings.json */
    public static String adminSettingsPath() {
        String v = raw("admin.settings.path");
        return (v != null && !v.isBlank()) ? v.trim() : "var/settings.json";
    }

    /**
     * 后台文件管理的根目录，默认 /agritime（整站）。
     * 改成 / 就等于把整机文件读写暴露给超管接口，只在演示机上这么干。
     */
    public static String adminFsRoot() {
        String v = raw("admin.fs.root");
        return (v != null && !v.isBlank()) ? v.trim() : "/agritime";
    }

    /** 是否开放注册。关掉后只有超管能建号，用于正式对外时收口 */
    public static boolean authAllowRegister() {
        return boolOf("auth.allow.register", true);
    }

    private static String raw(String key) {
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return FILE.getProperty(key);
    }

    private static int intOf(String key, int fallback) {
        String v = raw(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            System.err.println("[config] " + key + " = " + v + " 不是整数，回退到默认值 " + fallback);
            return fallback;
        }
    }

    private static boolean boolOf(String key, boolean fallback) {
        String v = raw(key);
        if (v == null || v.isBlank()) return fallback;
        return Boolean.parseBoolean(v.trim());
    }

    private AppConfig() {
    }
}
