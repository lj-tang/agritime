package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GET /health —— 全队的「心跳」。
 * GET /api/health/detail —— A8: 运维详情（缓存状态、作物数、LLM 配置等）。
 */
public class HealthHandler implements HttpHandler {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STARTED_AT = LocalDateTime.now().format(TS);

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        boolean detail = ex.getRequestURI().getPath().contains("detail");
        String body;
        if (detail) {
            int cropCount = ComputeStub.listCropInfos().size();
            body = Json.obj(
                    "status", Json.str("ok"),
                    "service", Json.str("crop-calendar-api"),
                    "version", Json.str("0.2.0"),
                    "computeLayer", Json.str(AppConfig.mockMode() ? "mock" : "real"),
                    "upstreamTimeoutMs", String.valueOf(AppConfig.upstreamTimeoutMs()),
                    "llmTimeoutMs", String.valueOf(AppConfig.llmTimeoutMs()),
                    "cacheTtlSeconds", String.valueOf(AppConfig.cacheTtlSeconds()),
                    "weatherWindowDays", String.valueOf(AppConfig.weatherWindowDays()),
                    "cropCount", String.valueOf(cropCount),
                    "llmModel", Json.str(AppConfig.llmModel()),
                    "startedAt", Json.str(STARTED_AT),
                    "now", Json.str(LocalDateTime.now().format(TS))
            );
        } else {
            body = Json.obj(
                    "status", Json.str("ok"),
                    "service", Json.str("crop-calendar-api"),
                    "version", Json.str("0.1.0"),
                    "computeLayer", Json.str(AppConfig.mockMode() ? "mock（乙未交付）" : "real"),
                    "upstreamTimeoutMs", String.valueOf(AppConfig.upstreamTimeoutMs()),
                    "startedAt", Json.str(STARTED_AT),
                    "now", Json.str(LocalDateTime.now().format(TS))
            );
        }
        HttpSupport.write(ex, 200, body);
    }
}
