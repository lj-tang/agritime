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
        } else if (path.endsWith("/suitability")) {
            handleSuitability(ex);
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

    /**
     * POST /api/geo/suitability
     * 接收用户地图框出的地块多边形（[[lat,lon],...]），计算面积并采样多点做可种植性判定。
     * 采样策略：质心 + 包围盒对角线两端，避免一整块「城市里夹着一点农田」被误判为适宜，
     * 也避免一大片农田里蹭到一点建成区被误判为不适宜。
     */
    private void handleSuitability(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, err(405, "仅支持 POST"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, err(400, "请求体不是合法 JSON"));
            return;
        }
        Object polyObj = body.get("polygon");
        if (!(polyObj instanceof List<?> raw) || raw.size() < 3) {
            HttpSupport.write(ex, 400, err(400, "polygon 至少需要 3 个点"));
            return;
        }
        java.util.List<double[]> ring = new java.util.ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof List<?> pt) || pt.size() < 2) {
                HttpSupport.write(ex, 400, err(400, "polygon 的点格式应为 [lat, lon]"));
                return;
            }
            Double la = dbl(pt.get(0));
            Double lo = dbl(pt.get(1));
            if (la == null || lo == null || !valid(la, lo)) {
                HttpSupport.write(ex, 400, err(400, "polygon 含非法经纬度"));
                return;
            }
            ring.add(new double[]{ la, lo });
        }

        double area = GeoResolver.polygonAreaSqm(ring);
        double[] center = GeoResolver.ringCentroid(ring);
        java.util.List<double[]> samples = samplePoints(ring, center);

        // 并行采样：5 个点各要打一次 Photon，串行会拖到 10s+，并行后 2~3s
        java.util.List<GeoResolver.Classification> classes = samples.parallelStream()
                .map(pt -> GeoResolver.classify(pt[0], pt[1]))
                .collect(java.util.stream.Collectors.toList());

        int urban = 0, green = 0, farm = 0, water = 0, unknown = 0;
        java.util.List<String> sampleJson = new java.util.ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            double[] pt = samples.get(i);
            GeoResolver.Classification cl = classes.get(i);
            switch (cl.category()) {
                case "urban" -> urban++;
                case "green" -> green++;
                case "farmland" -> farm++;
                case "water" -> water++;
                default -> unknown++;
            }
            sampleJson.add(Json.obj(
                    "lat", String.valueOf(round6(pt[0])),
                    "lon", String.valueOf(round6(pt[1])),
                    "category", Json.str(cl.category()),
                    "label", Json.str(cl.label())));
        }

        String status;
        if (urban > 0) status = "unsuitable";
        else if (green > 0) status = "caution";
        else if (farm > 0 || water > 0) status = "suitable";
        else status = "unknown";

        String reason = switch (status) {
            case "unsuitable" -> "该区域包含城市建成区/核心区（如商务区、商业区、住宅区等），明显不适合农业种植，请重新框选地块。";
            case "caution" -> "该区域为城市绿地（公园/花园等），可尝试种植，但请确认您拥有合法的使用与管理权限。";
            case "suitable" -> "该区域土地类型适宜农业种植。";
            default -> "未能识别该区域土地类型，请自行判断是否符合种植与用地规定。";
        };

        String data = Json.obj(
                "status", Json.str(status),
                "reason", Json.str(reason),
                "areaSqm", String.valueOf(Math.round(area)),
                "areaMu", String.valueOf(Math.round(area / 666.6667 * 10.0) / 10.0),
                "center", Json.obj("lat", String.valueOf(round6(center[0])), "lon", String.valueOf(round6(center[1]))),
                "samples", Json.arr(sampleJson));
        HttpSupport.write(ex, 200, HttpSupport.ok(data));
    }

    /** 质心 + 包围盒对角线两端共 3 个采样点（退化时仅取质心） */
    private static java.util.List<double[]> samplePoints(java.util.List<double[]> ring, double[] center) {
        double latMin = Double.MAX_VALUE, latMax = -Double.MAX_VALUE;
        double lonMin = Double.MAX_VALUE, lonMax = -Double.MAX_VALUE;
        for (double[] p : ring) {
            latMin = Math.min(latMin, p[0]); latMax = Math.max(latMax, p[0]);
            lonMin = Math.min(lonMin, p[1]); lonMax = Math.max(lonMax, p[1]);
        }
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        pts.add(center);
        if (latMax - latMin < 1e-6 || lonMax - lonMin < 1e-6) return pts;
        // 3 个点足够：质心 + 包围盒对角线两端。Photon 限流 1 次/秒，采样越多确认越慢
        double latA = latMin + 0.3 * (latMax - latMin), latB = latMin + 0.7 * (latMax - latMin);
        double lonA = lonMin + 0.3 * (lonMax - lonMin), lonB = lonMin + 0.7 * (lonMax - lonMin);
        pts.add(new double[]{ latA, lonA });
        pts.add(new double[]{ latB, lonB });
        return pts;
    }

    private static Double dbl(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
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
