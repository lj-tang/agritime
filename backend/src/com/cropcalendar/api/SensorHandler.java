package com.cropcalendar.api;

import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.service.sensor.SensorPoller;
import com.cropcalendar.service.sensor.SensorStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/sensor/* —— 传感器设备与读数（V5 T17）。
 *
 *   GET    /api/sensor/devices?plotId=     设备清单
 *   POST   /api/sensor/devices             新增/更新设备 {plotId,name,url,thresholds}
 *   POST   /api/sensor/devices {action:delete,deviceId}
 *   GET    /api/sensor/readings?plotId=&deviceId=&limit=
 *   POST   /api/sensor/readings            手工录入 {plotId,deviceId,values}
 *   POST   /api/sensor/poll                立即拉取 {plotId} 或 {plotId,deviceId}
 *
 * 所有接口都要求登录：设备与读数按 userId 隔离，plotId 只在该用户内部有意义。
 * 设备地址由用户填写，拉取前统一过 SSRF 校验（见 SensorPoller.assertPublicUrl）。
 */
public class SensorHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        User user = Auth.current(ex);
        if (user == null) {
            HttpSupport.write(ex, 401, HttpSupport.error(401, "请先登录后再接入设备"));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        try {
            if (path.endsWith("/devices")) {
                devices(ex, method, q, user.id());
            } else if (path.endsWith("/readings")) {
                readings(ex, method, q, user.id());
            } else if (path.endsWith("/poll")) {
                poll(ex, method, user.id());
            } else {
                HttpSupport.write(ex, 404, HttpSupport.error(404, "未知接口"));
            }
        } catch (IllegalArgumentException e) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            HttpSupport.write(ex, 500, HttpSupport.error(500, "设备接口异常：" + e.getMessage()));
        }
    }

    private void devices(HttpExchange ex, String method, Map<String, String> q, String userId) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            String plotId = HttpSupport.trimToNull(q.get("plotId"));
            if (plotId == null) throw new IllegalArgumentException("缺少参数 plotId");
            List<String> items = new ArrayList<>();
            for (Map<String, Object> d : SensorStore.devices(userId, plotId)) {
                items.add(deviceJson(d));
            }
            HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                    "plotId", Json.str(plotId),
                    "total", String.valueOf(items.size()),
                    "devices", Json.arr(items))));
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "不支持的方法"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) throw new IllegalArgumentException("请求体不是合法 JSON");
        String plotId = HttpSupport.trimToNull(HttpSupport.field(body, "plotId"));
        if (plotId == null) throw new IllegalArgumentException("缺少参数 plotId");

        if ("delete".equals(HttpSupport.field(body, "action"))) {
            String deviceId = HttpSupport.field(body, "deviceId");
            boolean ok = SensorStore.deleteDevice(userId, plotId, deviceId);
            HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("deleted", String.valueOf(ok))));
            return;
        }

        String url = HttpSupport.field(body, "url");
        if (!url.isEmpty()) {
            // 写入前先校验一次地址，避免存进去一个永远拉不动、或者指向内网的 URL
            try {
                SensorPoller.assertPublicUrl(url);
            } catch (IOException e) {
                throw new IllegalArgumentException("设备地址不可用：" + e.getMessage());
            }
        }
        Map<String, Object> dev = SensorStore.upsertDevice(userId, plotId, body);
        if (dev == null) throw new IllegalStateException("设备保存失败");
        HttpSupport.write(ex, 200, HttpSupport.ok(deviceJson(dev)));
    }

    private void readings(HttpExchange ex, String method, Map<String, String> q, String userId) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            String plotId = HttpSupport.trimToNull(q.get("plotId"));
            if (plotId == null) throw new IllegalArgumentException("缺少参数 plotId");
            String deviceId = HttpSupport.trimToNull(q.get("deviceId"));
            int limit = (int) HttpSupport.parseDouble(q.get("limit"), 100);
            limit = Math.max(1, Math.min(500, limit));
            List<Map<String, Object>> list = SensorStore.readings(userId, plotId, deviceId, limit);
            List<String> items = new ArrayList<>();
            for (Map<String, Object> r : list) items.add(Json.stringify(r));
            HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                    "total", String.valueOf(items.size()),
                    "readings", Json.arr(items))));
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "不支持的方法"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) throw new IllegalArgumentException("请求体不是合法 JSON");
        String plotId = HttpSupport.trimToNull(HttpSupport.field(body, "plotId"));
        String deviceId = HttpSupport.trimToNull(HttpSupport.field(body, "deviceId"));
        if (plotId == null || deviceId == null) throw new IllegalArgumentException("缺少 plotId 或 deviceId");
        if (!(body.get("values") instanceof Map<?, ?> v)) throw new IllegalArgumentException("缺少 values");

        Map<String, Object> values = new LinkedHashMap<>();
        v.forEach((k, val) -> values.put(String.valueOf(k), val));
        Map<String, Object> rec = SensorStore.addReading(userId, plotId, deviceId, values, "manual");
        if (rec == null) throw new IllegalStateException("读数保存失败");

        // 手工录入也要过阈值：用户自己填的 3.2mg/L 溶氧，同样应该报警
        Map<String, Object> dev = SensorStore.findDevice(userId, plotId, deviceId);
        List<Map<String, Object>> alerts = dev == null
                ? List.of()
                : SensorPoller.checkThresholds(userId, plotId, dev, values);

        List<String> alertJson = new ArrayList<>();
        for (Map<String, Object> a : alerts) alertJson.add(Json.stringify(a));
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "reading", Json.stringify(rec),
                "alerts", Json.arr(alertJson))));
    }

    private void poll(HttpExchange ex, String method, String userId) throws IOException {
        if (!"POST".equalsIgnoreCase(method)) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "仅支持 POST"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) throw new IllegalArgumentException("请求体不是合法 JSON");
        String plotId = HttpSupport.trimToNull(HttpSupport.field(body, "plotId"));
        String deviceId = HttpSupport.trimToNull(HttpSupport.field(body, "deviceId"));
        if (plotId == null) throw new IllegalArgumentException("缺少参数 plotId");

        List<Map<String, Object>> results = new ArrayList<>();
        if (deviceId != null) {
            Map<String, Object> dev = SensorStore.findDevice(userId, plotId, deviceId);
            if (dev == null) throw new IllegalArgumentException("设备不存在");
            Map<String, Object> r = SensorPoller.pollDevice(userId, plotId, dev);
            if (r != null) results.add(r);
        } else {
            results = SensorPoller.pollPlot(userId, plotId);
        }

        List<String> json = new ArrayList<>();
        int alerts = 0;
        for (Map<String, Object> r : results) {
            json.add(Json.stringify(r));
            if (r.get("alerts") instanceof List<?> l) alerts += l.size();
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "polled", String.valueOf(results.size()),
                "alerts", String.valueOf(alerts),
                "results", Json.arr(json))));
    }

    private String deviceJson(Map<String, Object> d) {
        Object thresholds = d.get("thresholds");
        return Json.obj(
                "id", Json.str(SensorStore.str(d.get("id"))),
                "name", Json.str(SensorStore.str(d.get("name"))),
                "url", Json.str(SensorStore.str(d.get("url"))),
                "status", Json.str(SensorStore.str(d.get("status"))),
                "lastOnline", Json.str(SensorStore.str(d.get("lastOnline"))),
                "thresholds", thresholds instanceof Map<?, ?> ? Json.stringify(thresholds) : "null");
    }
}
