package com.cropcalendar;

import com.cropcalendar.api.AdminHandler;
import com.cropcalendar.api.AuthHandler;
import com.cropcalendar.api.ChartHandler;
import com.cropcalendar.api.CropsHandler;
import com.cropcalendar.api.DiseaseHandler;
import com.cropcalendar.api.GeoHandler;
import com.cropcalendar.api.HealthHandler;
import com.cropcalendar.api.IndustryHandler;
import com.cropcalendar.api.MeHandler;
import com.cropcalendar.api.PlotHandler;
import com.cropcalendar.api.PlotAnalyzeHandler;
import com.cropcalendar.api.PlotHistoryHandler;
import com.cropcalendar.api.CropStatusHandler;
import com.cropcalendar.api.PondStatusHandler;
import com.cropcalendar.api.SensorHandler;
import com.cropcalendar.api.SowCheckHandler;
import com.cropcalendar.api.RecognitionHandler;
import com.cropcalendar.api.RecommendHandler;
import com.cropcalendar.api.ReminderHandler;
import com.cropcalendar.api.SiteConfigHandler;
import com.cropcalendar.api.SowWindowHandler;
import com.cropcalendar.api.SupportHandler;
import com.cropcalendar.api.TimelineHandler;
import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.AppTime;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.TimeZone;
import java.util.concurrent.Executors;

/**
 * 骨架服务的入口。
 *
 * 用 JDK 自带的 com.sun.net.httpserver 而不是 Spring Boot，原因很实在：
 *   · 零依赖 —— 不用 Maven、不下载任何 jar
 *   · 启动 < 100ms —— FunctionGraph 上的冷启动问题直接消失
 *   · 每一行都看得懂 —— 没有框架魔法，出了问题能自己查
 *
 * 这个项目只有两个接口和一段编排，JDK 自带的 HttpServer 完全够用。
 */
public class Server {

    public static void main(String[] args) throws IOException {
        // T17：容器默认时区可能是 UTC，先把 JVM 固定到北京时间。
        // 业务代码统一走 AppTime，这一行是兜底（第三方库、兜底路径也会受影响）。
        TimeZone.setDefault(TimeZone.getTimeZone(AppTime.ZONE));
        int port = AppConfig.port();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/health/detail", new HealthHandler());
        server.createContext("/api/crops", new CropsHandler());
        server.createContext("/api/timeline", new TimelineHandler());
        server.createContext("/api/recognition", new RecognitionHandler());
        server.createContext("/api/chart", new ChartHandler());
        server.createContext("/api/plots", new PlotHandler());
        // 更长的前缀优先匹配，所以 /api/plots/analyze 会走这里而不是上面的 PlotHandler
        server.createContext("/api/plots/analyze", new PlotAnalyzeHandler());
        // 地块历史：/analyze 纯分析（匿名可用），其余按登录用户档案
        server.createContext("/api/plots/history", new PlotHistoryHandler());
        server.createContext("/api/disease", new DiseaseHandler());
        server.createContext("/api/geo", new GeoHandler());
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/admin", new AdminHandler());
        server.createContext("/api/me", new MeHandler());
        server.createContext("/api/site-config", new SiteConfigHandler());
        server.createContext("/api/recommend", new RecommendHandler());
        server.createContext("/api/sow-window", new SowWindowHandler());
        server.createContext("/api/support", new SupportHandler());
        server.createContext("/api/industry", new IndustryHandler());
        server.createContext("/api/crop-status", new CropStatusHandler());
        server.createContext("/api/pond-status", new PondStatusHandler());
        server.createContext("/api/sow-check", new SowCheckHandler());
        server.createContext("/api/sensor", new SensorHandler());
        server.createContext("/api/reminders", new ReminderHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[server] 正在停止...");
            server.stop(0);
        }));

        server.start();

        ComputeStub.preloadCommonCrops();

        // T28：设备定时拉取。守护线程，不影响主流程。
        com.cropcalendar.service.sensor.SensorScheduler.start();

        startWarmup(port);

        String base = "http://localhost:" + port;
        System.out.println();
        System.out.println("  会看天的农事日历 · 后端骨架服务已启动");
        System.out.println("  ========================================");
        System.out.println("  健康检查    " + base + "/health");
        System.out.println("  时间线      " + base + "/api/timeline?crop=dry_garlic&sowDate=2025-03-15");
        System.out.println("  识别        " + base + "/api/recognition (POST)");
        System.out.println("  上游超时    " + AppConfig.upstreamTimeoutMs() + " ms");
        System.out.println("  缓存 TTL    " + AppConfig.cacheTtlSeconds() + " s");
        System.out.println("  计算层      " + (AppConfig.mockMode() ? "mock（等乙交付）" : "real"));
        boolean geoReady = !AppConfig.geoQqKey().isBlank() || !AppConfig.geoAmapKey().isBlank();
        System.out.println("  地图地名    " + (geoReady ? "已配置 key" : "未配置 key（标点只能返回 level=unknown）"));
        System.out.println();
        System.out.println("  按 Ctrl+C 停止");
        System.out.println();
    }

    private Server() {
    }

    /**
     * 启动预热：重启后的第一个真实用户请求要付三笔"冷账"——
     * JVM 还没 JIT、到 Open-Meteo 的 TLS 还没握手、缓存是空的。
     * 实测冷启动 2.29s，踩破 p95<2s 的验收线；预热后同类请求 0.3s。
     *
     * 用守护线程异步做，不阻塞启动；失败无所谓，只是少了一次预热。
     */
    private static void startWarmup(int port) {
        Thread t = new Thread(() -> {
            String[] urls = {
                    "http://127.0.0.1:" + port + "/api/plots/environment?lat=35.07&lon=116.31&type=farmland",
                    "http://127.0.0.1:" + port + "/api/plots/environment?lat=30.33&lon=112.24&type=fishpond",
                    "http://127.0.0.1:" + port + "/api/crops"
            };
            for (String u : urls) {
                try {
                    java.net.http.HttpClient.newHttpClient().send(
                            java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(u))
                                    .timeout(java.time.Duration.ofSeconds(10))
                                    .GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                    // 预热失败不影响服务，真实请求会自己再试一次
                }
            }
        }, "warmup");
        t.setDaemon(true);
        t.start();
    }
}
