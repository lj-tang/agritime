package com.cropcalendar.api;

import com.cropcalendar.service.geo.GeoResolver;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /api/geo/* —— 地图标点的地名服务（T06）
 *
 *   GET /api/geo/reverse?lat=39.996312&lon=116.480913
 *       经纬度 → 小区级中文地名。经纬度只在后端使用，前端只展示地名。
 *
 *   GET /api/geo/search?q=望京&lat=39.99&lon=116.48
 *       关键字 → 候选地点（含经纬度，供前端落标点）
 *
 * 上游全部失败时仍返回 200，用 level=unknown / 空数组表达「没查到」，
 * 让前端能区分「没识别出来」和「请求失败」，不至于白屏。
 */
public class GeoHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/search")) {
            handleSearch(ex);
        } else {
            handleReverse(ex);
        }
    }

    private void handleReverse(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        Double lat = num(q.get("lat"));
        Double lon = num(q.get("lon"));
        if (!valid(lat, lon)) {
            HttpSupport.write(ex, 400, err(400, "经纬度不合法"));
            return;
        }

        GeoResolver.Place p = GeoResolver.resolve(lat, lon);
        String data = Json.obj(
                "displayName", Json.str(p.displayName()),
                "name", Json.str(p.name()),
                "street", Json.str(p.street()),
                "district", Json.str(p.district()),
                "city", Json.str(p.city()),
                "province", Json.str(p.province()),
                "level", Json.str(p.level()),
                "source", Json.str(p.source()));
        HttpSupport.write(ex, 200, Json.obj(
                "code", "0",
                "message", Json.str("ok"),
                "data", data));
    }

    private void handleSearch(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String kw = HttpSupport.trimToNull(q.get("q"));
        if (kw == null) {
            HttpSupport.write(ex, 400, err(400, "缺少关键字 q"));
            return;
        }
        Double lat = num(q.get("lat"));
        Double lon = num(q.get("lon"));
        // 没传中心点时按全国搜，高德/腾讯都接受缺省
        double la = lat == null ? 0 : lat;
        double lo = lon == null ? 0 : lon;

        List<String> items = new ArrayList<>();
        for (GeoResolver.Suggestion s : GeoResolver.search(kw, la, lo, 8)) {
            items.add(Json.obj(
                    "title", Json.str(s.title()),
                    "address", Json.str(s.address()),
                    "lat", String.valueOf(s.lat()),
                    "lon", String.valueOf(s.lon()),
                    "source", Json.str(s.source())));
        }
        HttpSupport.write(ex, 200, Json.obj(
                "code", "0",
                "message", Json.str("ok"),
                "data", Json.obj("items", Json.arr(items))));
    }

    private static boolean valid(Double lat, Double lon) {
        return lat != null && lon != null
                && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }

    private static Double num(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String err(int code, String msg) {
        return Json.obj(
                "code", String.valueOf(code),
                "message", Json.str(msg),
                "data", "null");
    }
}
