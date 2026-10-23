package com.cropcalendar.api;

import com.cropcalendar.compute.LlmService;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * POST /api/chart —— 异步获取大模型生成的图表数据+卡通配文。
 *
 * 拆分出来是为了让 /api/timeline 立即返回节点+天气打分，
 * 图表和配文在后台异步加载，用户不用等 20-30 秒。
 */
public class ChartHandler implements HttpHandler {

    /** A5: 同作物图表数据内存缓存，二次请求 < 0.5s */
    private static final ConcurrentHashMap<String, String> CHART_CACHE = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, Json.obj("error", Json.str("只支持 POST")));
            return;
        }

        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = JsonParser.parse(body);
            String crop = "";
            int totalDays = 200;
            if (parsed instanceof Map<?, ?> m) {
                Object c = m.get("crop");
                if (c != null) crop = c.toString();
                Object d = m.get("totalDays");
                if (d instanceof Number n) totalDays = n.intValue();
            }

            String chartJson = CHART_CACHE.get(crop + ":" + totalDays);
            if (chartJson == null) {
                chartJson = LlmService.generateChartData(crop, totalDays);
                if (chartJson != null) {
                    CHART_CACHE.put(crop + ":" + totalDays, chartJson);
                }
            }
            String response = chartJson != null ? chartJson : Json.obj("error", Json.str("大模型生成失败"));

            byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(200, respBytes.length);
            ex.getResponseBody().write(respBytes);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("error", Json.str("图表服务异常: " + e.getMessage())));
        }
    }
}