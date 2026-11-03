package com.cropcalendar.api;

import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.service.plot.PlotHistory;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/plots/history —— 地块历史与地力档案（V5 T20）
 *
 * POST /api/plots/history/analyze         纯分析：传记录，算连作/产量趋势（允许匿名）
 * GET  /api/plots/history?plotId=xxx      服务端档案（登录后，按用户存）
 * POST /api/plots/history                 追加一条档案记录
 * POST /api/plots/history/yield           补录产量 {recordId, yield}
 * GET  /api/plots/history/export?plotId=xxx 导出 CSV（服务端档案）
 *
 * 为什么分析接口不要求登录：地块与作物记录目前存在浏览器（MyCropsStore），
 * 前端把已有记录传上来就能算，不落盘、不涉及他人数据。这样未登录也能看
 * "这块地种过什么"，登录后才额外有服务端档案。
 */
public class PlotHistoryHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();

        // 纯分析放在鉴权之前：它不读也不写任何人的档案
        if (path.endsWith("/history/analyze") && "POST".equals(method)) {
            handleAnalyze(ex);
            return;
        }

        User me = Auth.current(ex);
        if (me == null) {
            HttpSupport.write(ex, 401, Json.obj("code", "401", "message", Json.str("请先登录"), "data", "null"));
            return;
        }

        try {
            if (path.endsWith("/export") && "GET".equals(method)) {
                handleExport(ex, me.id());
            } else if (path.endsWith("/yield") && "POST".equals(method)) {
                handleUpdateYield(ex, me.id());
            } else if (path.endsWith("/history") && "GET".equals(method)) {
                handleList(ex, me.id());
            } else if (path.endsWith("/history") && "POST".equals(method)) {
                handleAdd(ex, me.id());
            } else {
                HttpSupport.write(ex, 404, Json.obj("code", "404", "message", Json.str("接口不存在"), "data", "null"));
            }
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("code", "500", "message", Json.str("服务器错误：" + e.getMessage()), "data", "null"));
        }
    }

    // ---------- 纯分析（匿名可用） ----------

    private void handleAnalyze(HttpExchange ex) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("请求体不是合法 JSON"), "data", "null"));
            return;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        if (body.get("records") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    m.forEach((k, v) -> rec.put(String.valueOf(k), v));
                    records.add(rec);
                }
            }
        }
        Map<String, Object> result = PlotHistory.analyze(records);
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "records", Json.arr(recordsJson(result.get("records"))),
                "continuousWarnings", Json.arr(warningsJson(result.get("continuousWarnings"))),
                "yieldTrend", trendJson(result.get("yieldTrend")),
                "total", String.valueOf(result.get("total")))));
    }

    // ---------- 服务端档案（登录后） ----------

    private void handleList(HttpExchange ex, String userId) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String plotId = HttpSupport.trimToNull(q.get("plotId"));
        if (plotId == null) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("缺少 plotId"), "data", "null"));
            return;
        }
        Map<String, Object> result = PlotHistory.analyze(PlotHistory.listByPlot(userId, plotId));
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "records", Json.arr(recordsJson(result.get("records"))),
                "continuousWarnings", Json.arr(warningsJson(result.get("continuousWarnings"))),
                "yieldTrend", trendJson(result.get("yieldTrend")),
                "total", String.valueOf(result.get("total")))));
    }

    private void handleAdd(HttpExchange ex, String userId) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("请求体不是合法 JSON"), "data", "null"));
            return;
        }
        String plotId = str(body.get("plotId"));
        if (plotId.isEmpty()) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("缺少 plotId"), "data", "null"));
            return;
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("plotId", plotId);
        record.put("plotName", str(body.get("plotName")));
        record.put("cropId", str(body.get("cropId")));
        record.put("cropName", str(body.get("cropName")));
        record.put("sowDate", str(body.get("sowDate")));
        record.put("harvestDate", str(body.get("harvestDate")));
        record.put("yield", body.get("yield"));
        record.put("unit", str(body.get("unit")));
        record.put("year", body.get("year"));
        record.put("note", str(body.get("note")));

        Map<String, Object> saved = PlotHistory.addRecord(userId, record);
        if (saved == null) {
            HttpSupport.write(ex, 500, Json.obj("code", "500", "message", Json.str("保存失败"), "data", "null"));
            return;
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "recordId", Json.str(str(saved.get("recordId"))),
                "plotId", Json.str(str(saved.get("plotId"))),
                "cropName", Json.str(str(saved.get("cropName"))),
                "year", String.valueOf(saved.get("year")),
                "createdAt", Json.str(str(saved.get("createdAt"))))));
    }

    private void handleUpdateYield(HttpExchange ex, String userId) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("请求体不是合法 JSON"), "data", "null"));
            return;
        }
        String recordId = str(body.get("recordId"));
        double yield = 0;
        Object y = body.get("yield");
        if (y instanceof Number n) yield = n.doubleValue();
        else try { yield = Double.parseDouble(str(y)); } catch (Exception ignored) { }

        if (recordId.isEmpty()) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("缺少 recordId"), "data", "null"));
            return;
        }
        boolean ok = PlotHistory.updateYield(userId, recordId, yield);
        HttpSupport.write(ex, ok ? 200 : 404, HttpSupport.ok(Json.obj(
                "recordId", Json.str(recordId),
                "yield", String.valueOf(yield),
                "updated", String.valueOf(ok))));
    }

    private void handleExport(HttpExchange ex, String userId) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String plotId = HttpSupport.trimToNull(q.get("plotId"));
        if (plotId == null) {
            HttpSupport.write(ex, 400, Json.obj("code", "400", "message", Json.str("缺少 plotId"), "data", "null"));
            return;
        }
        String csv = PlotHistory.exportCsv(userId, plotId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=plot-history-" + plotId + ".csv");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    // ---------- 序列化 ----------

    private static List<String> recordsJson(Object o) {
        List<String> out = new ArrayList<>();
        if (!(o instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> r)) continue;
            out.add(Json.obj(
                    "recordId", Json.str(str(r.get("recordId"))),
                    "plotId", Json.str(str(r.get("plotId"))),
                    "cropId", Json.str(str(r.get("cropId"))),
                    "cropName", Json.str(str(r.get("cropName"))),
                    "sowDate", Json.str(str(r.get("sowDate"))),
                    "harvestDate", Json.str(str(r.get("harvestDate"))),
                    "yield", r.get("yield") == null ? "null" : Json.num(doubleVal(r.get("yield"))),
                    "unit", Json.str(str(r.get("unit"))),
                    "year", String.valueOf(intVal(r.get("year"))),
                    "note", Json.str(str(r.get("note"))),
                    "createdAt", Json.str(str(r.get("createdAt")))));
        }
        return out;
    }

    private static List<String> warningsJson(Object o) {
        List<String> out = new ArrayList<>();
        if (!(o instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> w)) continue;
            out.add(Json.obj(
                    "years", Json.str(str(w.get("years"))),
                    "crop", Json.str(str(w.get("crop"))),
                    "risk", Json.str(str(w.get("risk")))));
        }
        return out;
    }

    private static String trendJson(Object o) {
        if (!(o instanceof Map<?, ?> t)) {
            return Json.obj("available", "false", "hint", Json.str("补录产量后可看趋势"));
        }
        if (!Boolean.TRUE.equals(t.get("available"))) {
            return Json.obj("available", "false", "hint", Json.str(str(t.get("hint"))));
        }
        List<String> points = new ArrayList<>();
        if (t.get("points") instanceof List<?> list) {
            for (Object p : list) points.add(Json.stringify(p));
        }
        return Json.obj(
                "available", "true",
                "direction", Json.str(str(t.get("direction"))),
                "changePct", t.get("changePct") == null ? "null" : Json.num(doubleVal(t.get("changePct"))),
                "points", Json.arr(points));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static double doubleVal(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
