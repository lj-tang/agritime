package com.cropcalendar;

import com.cropcalendar.api.ChartHandler;
import com.cropcalendar.api.CropsHandler;
import com.cropcalendar.api.DiseaseHandler;
import com.cropcalendar.api.GeoHandler;
import com.cropcalendar.api.HealthHandler;
import com.cropcalendar.api.PlotHandler;
import com.cropcalendar.api.RecognitionHandler;
import com.cropcalendar.api.TimelineHandler;
import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.config.AppConfig;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
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
        int port = AppConfig.port();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/health/detail", new HealthHandler());
        server.createContext("/api/crops", new CropsHandler());
        server.createContext("/api/timeline", new TimelineHandler());
        server.createContext("/api/recognition", new RecognitionHandler());
        server.createContext("/api/chart", new ChartHandler());
        server.createContext("/api/plots", new PlotHandler());
        server.createContext("/api/disease", new DiseaseHandler());
        server.createContext("/api/geo", new GeoHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[server] 正在停止...");
            server.stop(0);
        }));

        server.start();

        ComputeStub.preloadCommonCrops();

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
}
