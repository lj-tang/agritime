package com.cropcalendar.api;

import com.cropcalendar.service.sync.SyncStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.net.URI;
import java.util.Map;

/**
 * 数据同步接口（T30）：推送/拉取/导出/注销。
 * 路由：/api/sync/*
 */
public class SyncHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) {
        HttpSupport.cors(ex);
        String method = ex.getRequestMethod();
        if ("OPTIONS".equals(method)) { HttpSupport.write(ex, 204, ""); return; }

        URI uri = ex.getRequestURI();
        String path = uri.getPath();
        Map<String, String> q = HttpSupport.query(uri.getRawQuery());

        try {
            String userId = resolveUser(ex, q);
            if (userId == null) {
                HttpSupport.write(ex, 401, HttpSupport.error(401, "未登录"));
                return;
            }
            if (path.endsWith("/push")) {
                handlePush(ex, method, userId);
            } else if (path.endsWith("/pull")) {
                handlePull(ex, method, userId, q);
            } else if (path.endsWith("/export")) {
                handleExport(ex, method, userId, q);
            } else if (path.endsWith("/account")) {
                handleAccount(ex, method, userId);
            } else if (path.endsWith("/history")) {
                handleHistory(ex, method, userId);
            } else if (path.endsWith("/status")) {
                handleStatus(ex, method, userId);
            } else {
                HttpSupport.write(ex, 404, HttpSupport.error(404, "未知接口"));
            }
        } catch (Exception e) {
            HttpSupport.write(ex, 500, HttpSupport.error(500, e.getMessage()));
        }
    }

    private String resolveUser(HttpExchange ex, Map<String, String> q) {
        try {
            var me = com.cropcalendar.middleware.Auth.current(ex);
            if (me != null) return me.id();
        } catch (Exception ignored) {}
        return q.get("userId");
    }

    private void handlePush(HttpExchange ex, String method, String userId) throws Exception {
        if (!"POST".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 POST")); return; }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) { HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体无效")); return; }
        HttpSupport.write(ex, 200, HttpSupport.ok(SyncStore.push(userId, body)));
    }

    private void handlePull(HttpExchange ex, String method, String userId, Map<String, String> q) throws Exception {
        if (!"GET".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 GET")); return; }
        String since = q.get("since");
        HttpSupport.write(ex, 200, HttpSupport.ok(SyncStore.pull(userId, since)));
    }

    private void handleExport(HttpExchange ex, String method, String userId, Map<String, String> q) throws Exception {
        if (!"GET".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 GET")); return; }
        String format = q.getOrDefault("format", "json");
        String data = SyncStore.exportData(userId, format);
        if ("csv".equalsIgnoreCase(format)) {
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"export.csv\"");
            byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        } else {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"export.json\"");
            byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        }
    }

    private void handleAccount(HttpExchange ex, String method, String userId) throws Exception {
        if (!"DELETE".equals(method) && !"POST".equals(method)) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 DELETE/POST")); return;
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(SyncStore.deleteAccount(userId)));
    }

    private void handleHistory(HttpExchange ex, String method, String userId) throws Exception {
        if (!"GET".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 GET")); return; }
        HttpSupport.write(ex, 200, HttpSupport.ok(SyncStore.history(userId)));
    }

    private void handleStatus(HttpExchange ex, String method, String userId) throws Exception {
        if (!"GET".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 GET")); return; }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("online", Json.bool(true), "userId", Json.str(userId))));
    }
}