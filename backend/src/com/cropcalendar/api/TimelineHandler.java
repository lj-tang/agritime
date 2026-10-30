package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub.MarketHint;
import com.cropcalendar.compute.ComputeStub.TermHint;
import com.cropcalendar.compute.LlmService;
import com.cropcalendar.orchestration.TimelineOrchestrator;
import com.cropcalendar.orchestration.TimelineOrchestrator.TimelineResult;
import com.cropcalendar.orchestration.TimelineOrchestrator.NodeView;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /api/timeline —— 生成农事时间线。
 *
 * 支持两种调用方式：
 *   · POST + JSON body（前端契约 v3）：{crop, sowingDate, location:{latitude,longitude}}
 *   · GET + query params（向后兼容，curl 测试用）：?crop=&sowDate=&lat=&lon=
 *
 * 响应格式对齐 contract/api-contract.json（前端契约 v3）。
 */
public class TimelineHandler implements HttpHandler {

    private static final String EXAMPLE_GET =
            "/api/timeline?crop=dry_garlic&sowDate=2025-03-15&lat=35.07&lon=116.31";

    private final TimelineOrchestrator orchestrator = new TimelineOrchestrator();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        String cropId;
        String sowDate;
        double lat;
        double lon;

        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = JsonParser.parse(body);
            if (!(parsed instanceof Map<?, ?> map)) {
                HttpSupport.write(ex, 400, errJson("INVALID_INPUT", "请求体不是合法 JSON"));
                return;
            }
            cropId = str(map.get("crop"));
            sowDate = str(map.get("sowingDate"));
            Object locObj = map.get("location");
            if (locObj instanceof Map<?, ?> loc) {
                lat = num(loc.get("latitude"), 35.07);
                lon = num(loc.get("longitude"), 116.31);
            } else {
                lat = 35.07;
                lon = 116.31;
            }
        } else if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
            cropId = HttpSupport.trimToNull(q.get("crop"));
            sowDate = HttpSupport.trimToNull(q.get("sowDate"));
            lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
            lon = HttpSupport.parseDouble(q.get("lon"), 116.31);
        } else {
            HttpSupport.write(ex, 405, errJson("INVALID_INPUT", "只支持 GET 或 POST"));
            return;
        }

        if (cropId == null || cropId.isBlank() || sowDate == null || sowDate.isBlank()) {
            HttpSupport.write(ex, 400, errJson("INVALID_INPUT", "缺少必填参数 crop 与 sowingDate"));
            return;
        }

        try {
            // T02：无论用户传的是「西红柿」还是「tomato」，先归一到标准名，
            // 再映射到规程 id；没有规程的作物交给下游按标准名处理。
            String standardName = CropRegistry.standardName(cropId);
            String protocolId = CropRegistry.protocolIdOf(standardName);
            String buildCropId = protocolId != null ? protocolId : standardName;

            TimelineResult r = orchestrator.build(buildCropId, sowDate, lat, lon);

            if (r.nodes().isEmpty()) {
                HttpSupport.write(ex, 422, errJson("UNSUPPORTED_CROP",
                        "暂不支持该作物品种"));
                return;
            }

            HttpSupport.write(ex, 200, toContractJson(r, standardName, cropId));

        } catch (IllegalArgumentException e) {
            HttpSupport.write(ex, 400, errJson("INVALID_INPUT", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            HttpSupport.write(ex, 500, errJson("INTERNAL_ERROR", String.valueOf(e.getMessage())));
        }
    }

    /**
     * 转换成前端契约 v3 格式。
     */
    private static String toContractJson(TimelineResult r) {
        return toContractJson(r, null, null);
    }

    private static String toContractJson(TimelineResult r, String cropName, String requestedCrop) {
        String today = LocalDate.now().toString();
        String dataSource = mapDataSource(r.weatherStatus());
        String dataTime = r.dataTime() != null ? r.dataTime() : null;

        List<String> nodesJson = new ArrayList<>();
        String todayConclusion = "null";
        int seq = 0;
        for (NodeView n : r.nodes()) {
            seq++;
            String status = mapStatus(n.status());
            String certainty = "certain".equals(dataSource) ? "certain" : "predicted";
            if (status.equals("yellow") || status.equals("red")) certainty = "predicted";

            String reasons = n.reason() != null && !n.reason().isBlank()
                    ? Json.arr(List.of(Json.str(n.reason())))
                    : "[]";

            String nodeJson = Json.obj(
                    "nodeId", Json.str(String.valueOf(seq)),
                    "nodeSeq", String.valueOf(seq),
                    "daysAfterSowing", String.valueOf(n.dayOffset()),
                    "date", Json.str(n.date()),
                    "operationType", Json.str(n.opType()),
                    "name", Json.str(n.title()),
                    "description", Json.str(""),
                    "status", Json.str(status),
                    "copy", Json.str(n.suggest()),
                    "reasons", reasons,
                    "rescheduleAdvice", "null",
                    "certainty", Json.str(certainty),
                    "weatherIcon", "null",
                    "festivalIcon", "null");
            nodesJson.add(nodeJson);

            if (n.date().equals(today) || todayConclusion.equals("null")) {
                todayConclusion = Json.obj(
                        "nodeId", Json.str(String.valueOf(seq)),
                        "operationType", Json.str(n.opType()),
                        "status", Json.str(status),
                        "copy", Json.str(n.suggest()),
                        "certainty", Json.str(certainty));
            }
        }

        List<String> reminders = r.marketHints().stream().map(h -> Json.obj(
                "festivalName", Json.str(h.festival()),
                "festivalDate", "null",
                "windowStart", "null",
                "saleWindowDays", String.valueOf(h.daysTo()),
                "reminderCopy", Json.str(h.tip())
        )).toList();

        TermHint t = r.termHint();
        if (t != null && t.name() != null) {
            reminders = new ArrayList<>(reminders);
            reminders.add(Json.obj(
                    "festivalName", Json.str(t.name()),
                    "festivalDate", "null",
                    "windowStart", "null",
                    "saleWindowDays", String.valueOf(t.daysTo()),
                    "reminderCopy", Json.str(t.tip())));
        }

        return Json.obj(
                "apiVersion", Json.str("v1"),
                // T02：把归一结果回传，前端可显示「输入：西红柿 → 作物：番茄」
                "cropName", cropName != null ? Json.str(cropName) : "null",
                "requestedCrop", requestedCrop != null ? Json.str(requestedCrop) : "null",
                "dataSource", Json.str(dataSource),
                "dataTime", dataTime != null ? Json.str(dataTime) : "null",
                "weatherSummary", r.weatherSummary() != null ? Json.str(r.weatherSummary()) : "null",
                "todayConclusion", todayConclusion,
                "nodes", Json.arr(nodesJson),
                "reminders", Json.arr(reminders));
    }

    private static String mapStatus(String s) {
        return switch (s) {
            case "ok" -> "green";
            case "caution" -> "yellow";
            case "degraded" -> "red";
            default -> "yellow";
        };
    }

    private static String mapDataSource(String s) {
        return "ok".equals(s) ? "realtime" : "unavailable";
    }

    private static String errJson(String code, String msg) {
        return Json.obj("error", Json.str(code), "message", Json.str(msg));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double num(Object o, double fallback) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return fallback; }
        }
        return fallback;
    }
}
