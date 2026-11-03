package com.cropcalendar.api;

import com.cropcalendar.service.sensor.SensorStore;
import com.cropcalendar.service.sensor.SensorPoller;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.net.URI;
import java.util.Map;

/**
 * 传感器接口（T28）：设备管理 + 代理拉取 + 读数查询/录入。
 * 路由：/api/sensor/*
 */
public class SensorHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) {
        HttpSupport.cors(ex);
        String method = ex.getRequestMethod();
        if ("OPTIONS".equals(method)) { HttpSupport.write(ex, 204, ""); return; }

        URI uri = ex.getRequestURI();
        String path = uri.getPath();
        Map<String, String> q = HttpSupport.query(uri.getRawQuery());

        try {
            if (path.endsWith("/devices")) {
                handleDevices(ex, method, q);
            } else if (path.endsWith("/readings")) {
                handleReadings(ex, method, q);
            } else if (path.endsWith("/proxy")) {
                handleProxy(ex, method, q);
            } else if (path.endsWith("/poll")) {
                handlePoll(ex, method);
            } else {
                HttpSupport.write(ex, 404, HttpSupport.error(404, "未知接口"));
            }
        } catch (Exception e) {
            HttpSupport.write(ex, 500, HttpSupport.error(500, e.getMessage()));
        }
    }

    private void handleDevices(HttpExchange ex, String method, Map<String, String> q) throws Exception {
        String plotId = q.get("plotId");
        if ("POST".equals(method)) {
            Map<String, Object> body = HttpSupport.jsonBody(ex);
            if (body == null) { HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体无效")); return; }
            if (plotId == null || plotId.isEmpty()) plotId = HttpSupport.field(body, "plotId");
            if (plotId == null || plotId.isEmpty()) { HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少参数 plotId")); return; }
            String action = HttpSupport.field(body, "action");
            if ("delete".equals(action)) {
                String deviceId = HttpSupport.field(body, "deviceId");
                HttpSupport.write(ex, 200, HttpSupport.ok(SensorStore.deleteDevice(plotId, deviceId)));
            } else {
                HttpSupport.write(ex, 200, HttpSupport.ok(SensorStore.upsertDevice(plotId, body)));
            }
        } else if ("GET".equals(method)) {
            if (plotId == null || plotId.isEmpty()) { HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少参数 plotId")); return; }
            HttpSupport.write(ex, 200, HttpSupport.ok(SensorStore.listDevices(plotId)));
        } else {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "不支持的方法"));
        }
    }


    private void handleReadings(HttpExchange ex, String method, Map<String, String> q) throws Exception {
        String plotId = q.get("plotId");
        String deviceId = q.get("deviceId");
        int limit = 100;
        try { limit = Integer.parseInt(q.getOrDefault("limit", "100")); } catch (Exception ignored) {}
        if (limit > 1000) limit = 1000;
        if ("GET".equals(method)) {
            if (plotId == null || plotId.isEmpty()) { HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少参数 plotId")); return; }
            HttpSupport.write(ex, 200, HttpSupport.ok(SensorStore.listReadings(plotId, deviceId, limit)));
        } else if ("POST".equals(method)) {
            Map<String, Object> body = HttpSupport.jsonBody(ex);
            if (body == null) { HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体无效")); return; }
            if (plotId == null || plotId.isEmpty()) plotId = HttpSupport.field(body, "plotId");
            if (plotId == null || plotId.isEmpty()) { HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少参数 plotId")); return; }
            String did = HttpSupport.field(body, "deviceId");
            if (did.isEmpty()) did = deviceId;
            if (did.isEmpty()) { HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 deviceId")); return; }
            Object values = body.get("values");
            if (!(values instanceof Map<?, ?>)) { HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 values")); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> v = (Map<String, Object>) values;
            String source = HttpSupport.field(body, "source");
            String src = source.isEmpty() ? "manual" : source;
            String result = SensorStore.addReading(plotId, did, v, src);
            Map<String, Object> dev = SensorStore.getDevice(plotId, did);
            if (dev != null) SensorPoller.checkThresholds(plotId, did, dev, v);
            HttpSupport.write(ex, 200, HttpSupport.ok(result));
        } else {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "不支持的方法"));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleProxy(HttpExchange ex, String method, Map<String, String> q) throws Exception {
        if (!"GET".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 GET")); return; }
        String plotId = q.get("plotId");
        String deviceId = q.get("deviceId");
        if (plotId == null || deviceId == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 plotId 或 deviceId"));
            return;
        }
        Map<String, Object> dev = SensorStore.getDevice(plotId, deviceId);
        if (dev == null) { HttpSupport.write(ex, 404, HttpSupport.error(404, "设备不存在")); return; }
        String url = String.valueOf(dev.get("url"));
        String result = SensorPoller.fetchOnce(url);
        if (result == null) {
            SensorStore.updateStatus(plotId, deviceId, "offline", String.valueOf(dev.get("lastOnline")));
            HttpSupport.write(ex, 502, HttpSupport.error(502, "设备拉取失败"));
            return;
        }
        Object parsed = com.cropcalendar.util.JsonParser.parse(result);
        if (parsed instanceof Map<?, ?> m) {
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> values.put(String.valueOf(k), v));
            SensorStore.addReading(plotId, deviceId, values, "auto");
            SensorPoller.checkThresholds(plotId, deviceId, dev, values);
            HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("reading", Json.stringify(values))));
        } else {
            HttpSupport.write(ex, 502, HttpSupport.error(502, "设备返回格式异常"));
        }
    }

    private void handlePoll(HttpExchange ex, String method) throws Exception {
        if (!"POST".equals(method)) { HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 POST")); return; }
        int count = SensorPoller.pollAll();
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("polled", Json.num(count))));
    }
}