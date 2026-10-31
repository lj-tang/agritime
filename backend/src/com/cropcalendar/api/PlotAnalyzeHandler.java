package com.cropcalendar.api;

import com.cropcalendar.orchestration.CacheStore;
import com.cropcalendar.service.crop.AgroZone;
import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.util.HttpJson;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * POST /api/plots/analyze —— 地块真实空间分析（V5 T06 深化）
 *
 * 以前的地块评分只有一个质心点：用户框了 20 亩的坡地，系统按中心那一格的
 * 气象和土壤给个分，框的形状、坡度、高差全不参与 —— 等于"框了但没分析"。
 *
 * 这个接口把用户画的闭合多边形当真：
 * 1. 球面投影算真实面积、周长、质心、紧凑度（能不能进农机）；
 * 2. 在多边形内部撒网格采样点 + 顶点，批量打 90m DEM 拿高程；
 * 3. 用最小二乘拟合平面求坡度与坡向（坡向决定阳坡/阴坡）；
 * 4. 质心处取真实土壤模型（0cm/6cm 地温、0-1cm 体积含水率）与 7 天降水；
 * 5. 五项加权评分，缺数据项不参与并重新归一化，绝不拿默认值假装测到；
 * 6. 输出限制因子、可操作改良建议、按区域/坡度/墒情/季節匹配的适宜作物。
 *
 * 零依赖：JDK HttpClient + Open-Meteo 免费接口（无 key）。
 */
public class PlotAnalyzeHandler implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** 地形分析缓存：DEM 和土壤模型不会分钟级变化，30 分钟足够，也保护上游。 */
    private static final CacheStore CACHE = new CacheStore();
    private static final int TTL_SECONDS = 1800;

    private static final int MAX_SAMPLES = 30;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        String raw;
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            raw = HttpSupport.body(ex);
        } else {
            // GET 也支持：预览时用 polygon=lon,lat;lon,lat;... 便于浏览器直接调试
            Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
            String pg = q.getOrDefault("polygon", "");
            StringBuilder sb = new StringBuilder("{\"polygon\":[");
            String[] pts = pg.split(";");
            for (int i = 0; i < pts.length; i++) {
                String[] ll = pts[i].split(",");
                if (ll.length < 2) continue;
                if (i > 0) sb.append(',');
                sb.append('[').append(ll[0]).append(',').append(ll[1]).append(']');
            }
            sb.append("],\"type\":").append(Json.str(q.getOrDefault("type", "farmland"))).append('}');
            raw = sb.toString();
        }

        Object root;
        try {
            root = JsonParser.parse(raw);
        } catch (Exception e) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("请求体不是合法 JSON")));
            return;
        }
        if (!(root instanceof Map<?, ?> rm)) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("请求体不是合法 JSON")));
            return;
        }

        List<double[]> ring = readRing(rm.get("polygon"));
        if (ring == null) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("polygon 至少 3 个顶点，格式 [[lon,lat],...]")));
            return;
        }
        String type = rm.get("type") instanceof String s ? s : "farmland";
        // 用户接了传感器就用实测覆盖模型值，没接就保持 null 走估算
        Double measuredMoisture = HttpJson.num(rm.get("soilMoisture"));
        Double measuredSoilTemp = HttpJson.num(rm.get("soilTemp"));
        Double measuredWaterTemp = HttpJson.num(rm.get("waterTemp"));

        // 实测值必须进缓存键：否则同一块地先算过模型值，接入传感器后仍会返回旧结论
        String key = cacheKey(ring, type) + ":" + fmtKey(measuredMoisture) + ":" + fmtKey(measuredSoilTemp)
                + ":" + fmtKey(measuredWaterTemp);
        String cached = CACHE.get(key, String.class);
        if (cached != null) {
            HttpSupport.write(ex, 200, cached);
            return;
        }

        try {
            String out = analyze(ring, type, measuredMoisture, measuredSoilTemp, measuredWaterTemp);
            CACHE.put(key, out, TTL_SECONDS);
            HttpSupport.write(ex, 200, out);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("error", Json.str("分析失败：" + e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ 主流程

    private String analyze(List<double[]> ring, String type,
                           Double measuredMoisture, Double measuredSoilTemp, Double measuredWaterTemp) throws Exception {
        Geometry g = Geometry.of(ring);

        // 高程采样与气象/土壤抓取并行：两路上游互不依赖，串行会慢一倍
        CompletableFuture<Terrain> elevFuture = CompletableFuture.supplyAsync(() -> fetchElevation(ring, g));
        CompletableFuture<Weather> wFuture = CompletableFuture.supplyAsync(() -> fetchWeather(g));

        Terrain t = elevFuture.completeOnTimeout(Terrain.empty(), 6, TimeUnit.SECONDS)
                .get(7, TimeUnit.SECONDS);
        Weather w = wFuture.completeOnTimeout(new Weather(), 6, TimeUnit.SECONDS)
                .get(7, TimeUnit.SECONDS);
        if (measuredMoisture != null) {
            w.soilMoisture = measuredMoisture;
            w.moistureMeasured = true;
        }
        if (measuredSoilTemp != null) {
            w.soilTemp0 = measuredSoilTemp;
            w.soilTempMeasured = true;
        }
        if (measuredWaterTemp != null) {
            w.waterTemp = measuredWaterTemp;
        }

        String region = AgroZone.regionOf(g.centroidLat, g.centroidLon);
        String soilType = AgroZone.soilTypeOf(region);

        List<Factor> factors = new ArrayList<>();
        List<Limit> limits = new ArrayList<>();
        boolean pond = "pond".equalsIgnoreCase(type)
                || "fishpond".equalsIgnoreCase(type) || "aquaculture".equalsIgnoreCase(type);

        addTerrainFactor(factors, t, limits, pond);
        addSoilFactor(factors, w, limits, pond);
        addClimateFactor(factors, w, limits);
        addShapeFactor(factors, g, limits);
        addDrainageFactor(factors, t, w, g, limits);
        addLightFactor(factors, t, w, g, limits, pond);
        addWindFactor(factors, t, w, limits);

        double total = 0, wsum = 0;
        for (Factor f : factors) { total += f.score * f.weight; wsum += f.weight; }
        double score = wsum > 0 ? total / wsum : 0;

        String level;
        if (score >= 85) level = "适宜";
        else if (score >= 70) level = "较适宜";
        else if (score >= 55) level = "一般";
        else level = "需注意";

        List<String> fj = new ArrayList<>();
        for (Factor f : factors) {
            fj.add(Json.obj(
                    "key", Json.str(f.key),
                    "label", Json.str(f.label),
                    "score", Json.num((double) Math.round(f.score)),
                    "weight", Json.num(Math.round(f.weight / wsum * 100) / 100.0),
                    "note", Json.str(f.note)));
        }

        List<String> lj = new ArrayList<>();
        limits.sort(Comparator.comparingInt(l -> l.severity));
        for (Limit l : limits) {
            lj.add(Json.obj("code", Json.str(l.code),
                    "label", Json.str(l.label),
                    "detail", Json.str(l.detail),
                    "severity", Json.str(l.severity >= 3 ? "high" : l.severity == 2 ? "mid" : "low")));
        }

        List<String> suggestions = suggestionsOf(t, g, w, limits, pond);
        List<String> sj = new ArrayList<>();
        for (String s : suggestions) sj.add(Json.str(s));

        List<CropHit> crops = suitableCrops(region, t, w, g, pond);
        List<String> cj = new ArrayList<>();
        for (CropHit c : crops) {
            cj.add(Json.obj("name", Json.str(c.name),
                    "score", Json.num((double) Math.round(c.score)),
                    "reason", Json.str(c.reason),
                    "days", Json.num((double) c.days),
                    "season", Json.str(c.season)));
        }

        return Json.obj(
                "geometry", Json.raw(geometryJson(g)),
                "terrain", Json.raw(terrainJson(t)),
                "soil", Json.raw(soilJson(w, soilType)),
                "climate", Json.raw(climateJson(w)),
                "region", Json.str(region),
                "type", Json.str(type),
                "score", Json.num((double) Math.round(score)),
                "level", Json.str(level),
                "summary", Json.str(summaryOf(level, t, g, w, pond)),
                "factors", Json.arr(fj),
                "limits", Json.arr(lj),
                "suggestions", Json.arr(sj),
                "suitableCrops", Json.arr(cj),
                "dataSources", Json.obj(
                        "elevation", Json.str("Open-Meteo Elevation（90m DEM，批量采样 " + t.sampleCount + " 点）"),
                        "soil", Json.str("Open-Meteo 土壤模型（0cm/6cm 地温、0-1cm 体积含水率）"),
                        "climate", Json.str("Open-Meteo 预报（实时气温湿度 + 未来 7 天降水）"),
                        "crops", Json.str("作物属性库 properties.json + 区域/坡度/墒情规则匹配")),
                "analyzedAt", Json.str(LocalDate.now().toString()));
    }

    // ------------------------------------------------------------------ 几何

    private static final class Geometry {
        double areaM2;
        double perimeterM;
        double centroidLat;
        double centroidLon;
        double compactness;
        double minLon, minLat, maxLon, maxLat;
        int vertexCount;

        static Geometry of(List<double[]> ring) {
            Geometry g = new Geometry();
            int n = ring.size();
            double lat0 = ring.get(0)[1];
            double kx = 111320.0 * Math.cos(Math.toRadians(lat0));
            double ky = 110540.0;
            double[] x = new double[n], y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = (ring.get(i)[0] - ring.get(0)[0]) * kx;
                y[i] = (ring.get(i)[1] - ring.get(0)[1]) * ky;
            }
            double a2 = 0, cx = 0, cy = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double cross = x[i] * y[j] - x[j] * y[i];
                a2 += cross;
                cx += (x[i] + x[j]) * cross;
                cy += (y[i] + y[j]) * cross;
            }
            g.areaM2 = Math.abs(a2) / 2.0;
            if (a2 != 0) {
                cx /= (3 * a2);
                cy /= (3 * a2);
            }
            double per = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                per += Math.hypot(x[j] - x[i], y[j] - y[i]);
            }
            g.perimeterM = per;
            g.centroidLon = ring.get(0)[0] + (kx == 0 ? 0 : cx / kx);
            g.centroidLat = ring.get(0)[1] + cy / ky;
            g.compactness = per > 0 ? Math.min(1, 4 * Math.PI * g.areaM2 / (per * per)) : 0;
            g.minLon = g.maxLon = ring.get(0)[0];
            g.minLat = g.maxLat = ring.get(0)[1];
            for (double[] p : ring) {
                g.minLon = Math.min(g.minLon, p[0]);
                g.maxLon = Math.max(g.maxLon, p[0]);
                g.minLat = Math.min(g.minLat, p[1]);
                g.maxLat = Math.max(g.maxLat, p[1]);
            }
            g.vertexCount = n;
            return g;
        }

        double mu() { return areaM2 / 666.667; }
    }

    private String geometryJson(Geometry g) {
        // 面积给 1 位小数就够用（0.1 亩 ≈ 67 m²，GPS 描点本身也没这个精度）；
        // 不足 1 亩时保留 2 位，否则小菜园会显示成 0.0 亩。
        double mu = g.mu();
        double muOut = mu >= 1 ? Math.round(mu * 10) / 10.0 : Math.round(mu * 100) / 100.0;
        return Json.obj(
                "areaM2", Json.num((double) Math.round(g.areaM2)),
                "areaMu", Json.num(muOut),
                "perimeterM", Json.num((double) Math.round(g.perimeterM)),
                "vertexCount", Json.num((double) g.vertexCount),
                "compactness", Json.num(Math.round(g.compactness * 100) / 100.0),
                "centroid", Json.obj("lat", Json.num(Math.round(g.centroidLat * 1e6) / 1e6),
                        "lon", Json.num(Math.round(g.centroidLon * 1e6) / 1e6)),
                "bbox", Json.arr(List.of(Json.num(g.minLon), Json.num(g.minLat),
                        Json.num(g.maxLon), Json.num(g.maxLat))));
    }

    /** 内部网格采样点：先按 bbox 撒格，再只保留落在多边形内的点，最后补顶点。 */
    private static List<double[]> samplePoints(List<double[]> ring, Geometry g, int grid) {
        List<double[]> pts = new ArrayList<>();
        double dLon = (g.maxLon - g.minLon) / (grid + 1.0);
        double dLat = (g.maxLat - g.minLat) / (grid + 1.0);
        for (int i = 1; i <= grid; i++) {
            for (int j = 1; j <= grid; j++) {
                double lon = g.minLon + dLon * i;
                double lat = g.minLat + dLat * j;
                if (inside(ring, lon, lat)) pts.add(new double[]{lon, lat});
            }
        }
        // 顶点代表边界起伏，网格太稀时会漏掉边缘的陡坎
        for (double[] v : ring) pts.add(new double[]{v[0], v[1]});
        return pts;
    }

    /** 射线法判断点是否在多边形内（经纬度平面近似，地块尺度误差可忽略）。 */
    private static boolean inside(List<double[]> ring, double lon, double lat) {
        boolean c = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring.get(i)[0], yi = ring.get(i)[1];
            double xj = ring.get(j)[0], yj = ring.get(j)[1];
            if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) c = !c;
        }
        return c;
    }

    // ------------------------------------------------------------------ 地形

    private static final class Terrain {
        double min, max, mean, relief, slopeDeg, aspectDeg;
        int sampleCount;
        boolean available;

        static Terrain empty() {
            Terrain t = new Terrain();
            t.available = false;
            t.aspectDeg = Double.NaN;
            return t;
        }

        static Terrain of(double[] z) {
            Terrain t = new Terrain();
            if (z == null || z.length < 3) { t.available = false; t.aspectDeg = Double.NaN; return t; }
            t.available = true;
            t.sampleCount = z.length;
            double s = 0;
            t.min = Double.MAX_VALUE;
            t.max = -Double.MAX_VALUE;
            for (double v : z) { s += v; t.min = Math.min(t.min, v); t.max = Math.max(t.max, v); }
            t.mean = s / z.length;
            t.relief = t.max - t.min;
            t.slopeDeg = 0;
            t.aspectDeg = Double.NaN;
            return t;
        }
    }

    /** 最小二乘拟合 z = a·x + b·y + c，坡度取梯度模，坡向取下坡方向的方位角。 */
    private void fitPlane(Geometry g, List<double[]> pts, double[] z, Terrain t) {
        if (!t.available || z.length != pts.size()) return;
        double kx = 111320.0 * Math.cos(Math.toRadians(g.centroidLat));
        double ky = 110540.0;
        double sx = 0, sy = 0, sz = 0;
        int n = pts.size();
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (pts.get(i)[0] - g.centroidLon) * kx;
            ys[i] = (pts.get(i)[1] - g.centroidLat) * ky;
            sx += xs[i]; sy += ys[i]; sz += z[i];
        }
        double mx = sx / n, my = sy / n, mz = sz / n;
        double sxx = 0, syy = 0, sxy = 0, sxz = 0, syz = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - mx, dy = ys[i] - my, dz = z[i] - mz;
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy;
            sxz += dx * dz; syz += dy * dz;
        }
        double det = sxx * syy - sxy * sxy;
        if (Math.abs(det) < 1e-9) { t.slopeDeg = 0; return; }
        double a = (sxz * syy - syz * sxy) / det;   // dz/dx（东向）
        double b = (syz * sxx - sxz * sxy) / det;   // dz/dy（北向）
        t.slopeDeg = Math.toDegrees(Math.atan(Math.hypot(a, b)));
        // 坡向 = 坡面朝向（下坡方向）：0=北 90=东 180=南 270=西
        double bear = Math.toDegrees(Math.atan2(-a, -b));
        t.aspectDeg = (bear + 360) % 360;
    }

    private String terrainJson(Terrain t) {
        if (!t.available) {
            return Json.obj("available", "false", "reason", Json.str("高程数据获取失败，地形项不参与评分"));
        }
        String aspect = aspectLabel(t.aspectDeg, t.slopeDeg);
        return Json.obj(
                "available", "true",
                "elevMin", Json.num(t.min),
                "elevMax", Json.num(t.max),
                "elevMean", Json.num(Math.round(t.mean * 10) / 10.0),
                "relief", Json.num(Math.round(t.relief * 10) / 10.0),
                "slopeDeg", Json.num(Math.round(t.slopeDeg * 10) / 10.0),
                "aspectDeg", Json.num(Double.isNaN(t.aspectDeg) ? 0 : Math.round(t.aspectDeg)),
                "aspectLabel", Json.str(aspect),
                "sampleCount", Json.num((double) t.sampleCount),
                "source", Json.str("Open-Meteo Elevation（90m DEM）"));
    }

    /** 坡度不足 1° 时谈坡向没有意义，直接标平地，避免给农户一个误导性的「朝向」。 */
    private static String aspectLabel(double deg, double slope) {
        if (Double.isNaN(deg) || slope < 1.0) return "平地";
        String[] names = {"北（阴坡）", "东北", "东", "东南", "南（阳坡）", "西南", "西", "西北", "北（阴坡）"};
        int idx = (int) Math.round(((deg % 360) / 45.0));
        if (idx > 8) idx = 8;
        return names[idx];
    }

    // ------------------------------------------------------------------ 评分因子

    private static final class Factor {
        String key, label, note;
        double score, weight;
    }

    private static final class Limit {
        String code, label, detail;
        int severity; // 3 高 2 中 1 低

        Limit(String code, String label, String detail, int severity) {
            this.code = code;
            this.label = label;
            this.detail = detail;
            this.severity = severity;
        }
    }

    private void addTerrainFactor(List<Factor> f, Terrain t, List<Limit> limits, boolean pond) {
        if (!t.available) return;
        Factor x = new Factor();
        x.key = "terrain";
        x.label = "地形";
        x.weight = pond ? 0.20 : 0.18;
        double s;
        double sl = t.slopeDeg;
        if (pond) {
            // 鱼塘要的是汇水洼地：太平坦说明没有集雨面，太陡则塘埂难做
            if (sl < 0.5) s = 70;
            else if (sl < 3) s = 100;
            else if (sl < 6) s = 88;
            else if (sl < 12) s = 60;
            else s = 32;
        } else {
            if (sl < 2) s = 100;
            else if (sl < 5) s = 90;
            else if (sl < 8) s = 78;
            else if (sl < 15) s = 60;
            else if (sl < 25) s = 35;
            else s = 15;
        }
        if (t.relief > 30) s -= 15;
        else if (t.relief > 15) s -= 8;
        x.score = clamp(s);
        x.note = "坡度 " + String.format("%.1f", sl) + "°、高差 " + String.format("%.1f", t.relief)
                + "m、高程 " + String.format("%.0f", t.min) + "~" + String.format("%.0f", t.max)
                + "m、" + aspectLabel(t.aspectDeg, sl);

        if (!pond) {
            if (sl >= 25) limits.add(new Limit("steep", "坡度过陡", "坡度 " + String.format("%.1f", sl)
                    + "°，农机无法作业且有水土流失风险，建议修梯田或改种林果", 3));
            else if (sl >= 15) limits.add(new Limit("slope15", "坡度偏大", "坡度 " + String.format("%.1f", sl)
                    + "°，宜等高种植、小型农机或改种果树茶树", 2));
            if (t.relief > 20) limits.add(new Limit("relief", "地表高差大", "区内高差 " + String.format("%.1f", t.relief)
                    + "m，灌溉难以均匀，建议分区平整或分区灌溉", 2));
        } else if (sl > 12) {
            limits.add(new Limit("pond-slope", "塘址偏陡", "坡度 " + String.format("%.1f", sl)
                    + "°，塘埂填筑量大且易渗漏，建议另选洼地或分级筑坝", 3));
        }
        f.add(x);
    }

    private void addSoilFactor(List<Factor> f, Weather w, List<Limit> limits, boolean pond) {
        Factor x = new Factor();
        x.key = "soil";
        x.label = "土壤";
        x.weight = pond ? 0.15 : 0.16;
        Double m = w.soilMoisture;
        Double st = w.soilTemp0;
        List<Double> parts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (m != null) {
            double v = m;
            double ms;
            if (pond) {
                // 鱼塘选址反而希望土壤黏重保水
                if (v > 0.35) ms = 100;
                else if (v > 0.25) ms = 85;
                else if (v > 0.15) ms = 62;
                else ms = 40;
            } else {
                if (v >= 0.20 && v <= 0.35) ms = 100;
                else if ((v >= 0.15 && v < 0.20) || (v > 0.35 && v <= 0.42)) ms = 80;
                else if (v >= 0.10 && v < 0.15) ms = 60;
                else if (v > 0.42) ms = 45;
                else ms = 35;
            }
            parts.add(ms);
            notes.add("0-1cm 体积含水率 " + String.format("%.3f", v) + " m³/m³");
            if (!pond) {
                if (v > 0.42) limits.add(new Limit("wet", "土壤过湿", "含水率 " + String.format("%.3f", v)
                        + "，接近饱和，需开沟排渍并推迟整地", 2));
                else if (v < 0.10) limits.add(new Limit("dry", "土壤干旱", "含水率 " + String.format("%.3f", v)
                        + "，播前需造墒或灌溉补墒", 3));
            }
        }
        if (st != null) {
            double v = st;
            double ss;
            if (v < 8) ss = 55;
            else if (v < 12) ss = 70;
            else if (v <= 25) ss = 100;
            else if (v <= 32) ss = 85;
            else ss = 60;
            parts.add(ss);
            notes.add("地表温度 " + String.format("%.1f", v) + "℃");
            if (v < 8 && !pond) limits.add(new Limit("cold-soil", "地温偏低", "地表温度 " + String.format("%.1f", v)
                    + "℃，种子发芽缓慢，建议覆膜提温或等回暖再播", 2));
        }
        if (parts.isEmpty()) {
            // 拿不到就不评分，绝不用默认值凑一项
            return;
        }
        double sum = 0;
        for (double p : parts) sum += p;
        x.score = clamp(sum / parts.size());
        x.note = String.join("，", notes);
        f.add(x);
    }

    private void addClimateFactor(List<Factor> f, Weather w, List<Limit> limits) {
        Factor x = new Factor();
        x.key = "climate";
        x.label = "气候";
        x.weight = 0.15;
        List<Double> parts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (w.temp != null) {
            double v = w.temp;
            double s;
            if (v >= 15 && v <= 28) s = 100;
            else if ((v >= 10 && v < 15) || (v > 28 && v <= 32)) s = 80;
            else if (v >= 5 && v < 10) s = 60;
            else if (v > 32 && v <= 38) s = 45;
            else s = 30;
            parts.add(s);
            notes.add("气温 " + String.format("%.1f", v) + "℃");
            if (v > 35) limits.add(new Limit("heat", "高温胁迫", "气温 " + String.format("%.1f", v)
                    + "℃，作物蒸腾过盛，建议遮阳、早晚灌溉", 3));
            else if (v < 5) limits.add(new Limit("cold", "气温过低", "气温 " + String.format("%.1f", v)
                    + "℃，多数作物停止生长，建议覆盖保温", 2));
        }
        if (w.humidity != null) {
            double v = w.humidity;
            double s;
            if (v >= 50 && v <= 80) s = 100;
            else if ((v >= 40 && v < 50) || (v > 80 && v <= 90)) s = 80;
            else s = 60;
            parts.add(s);
            notes.add("湿度 " + Math.round(v) + "%");
        }
        if (w.precip7d != null) {
            double v = w.precip7d;
            double s;
            if (v >= 10 && v <= 60) s = 100;
            else if (v > 60 && v <= 120) s = 85;
            else if (v >= 3 && v < 10) s = 70;
            else if (v < 3) s = 55;
            else s = 60;
            parts.add(s);
            notes.add("未来 7 天降水 " + String.format("%.1f", v) + "mm");
            if (v > 120) limits.add(new Limit("waterlog", "降水过量", "未来 7 天 " + String.format("%.0f", v)
                    + "mm，存在渍涝风险，提前疏通排水沟", 3));
            else if (v < 3) limits.add(new Limit("drought", "降水偏少", "未来 7 天仅 " + String.format("%.1f", v)
                    + "mm，需安排补灌", 2));
        }
        if (parts.isEmpty()) return;
        double sum = 0;
        for (double p : parts) sum += p;
        x.score = clamp(sum / parts.size());
        x.note = String.join("，", notes);
        f.add(x);
    }

    /**
     * 光照：用未来 7 天的实测短波辐射均值，再按坡向修正。
     * 阳坡比阴坡多收 5%~10% 的光，这在山区是能差出一茬庄稼的。
     */
    private void addLightFactor(List<Factor> f, Terrain t, Weather w, Geometry g,
                                List<Limit> limits, boolean pond) {
        if (w.radiation7d == null) return;
        Factor x = new Factor();
        x.key = "light";
        x.label = "光照";
        x.weight = pond ? 0.06 : 0.12;
        double r = w.radiation7d;
        double s;
        if (r >= 18) s = 100;
        else if (r >= 14) s = 88;
        else if (r >= 10) s = 72;
        else if (r >= 6) s = 55;
        else s = 35;
        String aspectNote = "";
        if (t.available && t.slopeDeg >= 3 && !Double.isNaN(t.aspectDeg)) {
            double a = t.aspectDeg;
            boolean south = a > 135 && a < 225;
            boolean north = a < 45 || a > 315;
            if (south) { s += 6; aspectNote = "；南向坡受光好"; }
            else if (north) {
                s -= 10;
                aspectNote = "；北向坡偏阴，光照偏弱";
                limits.add(new Limit("shade", "阴坡光照不足", "坡向朝北且坡度 " + String.format("%.1f", t.slopeDeg)
                        + "°，日均光照偏少，喜光作物易徒长、成熟推迟", 2));
            }
        }
        x.score = clamp(s);
        x.note = "短波辐射 " + String.format("%.1f", r) + " MJ/m²·d" + aspectNote;
        if (r < 8 && !pond) {
            limits.add(new Limit("low-light", "光照偏少", "日均短波辐射仅 " + String.format("%.1f", r)
                    + " MJ/m²，光合产物积累慢，建议选耐阴品种或适当稀植", 2));
        }
        f.add(x);
    }

    /**
     * 风害：未来 7 天最大风速。6 级以上（≥10.8 m/s）会造成倒伏与落花落果，
     * 也直接决定这几天能不能喷药 —— 风大时雾滴飘移，打了等于白打。
     */
    private void addWindFactor(List<Factor> f, Terrain t, Weather w, List<Limit> limits) {
        if (w.windMax7d == null) return;
        Factor x = new Factor();
        x.key = "wind";
        x.label = "风害";
        x.weight = 0.08;
        double v = w.windMax7d;
        double s;
        if (v < 3) s = 100;
        else if (v < 5) s = 92;
        else if (v < 8) s = 78;
        else if (v < 10.8) s = 58;
        else if (v < 14) s = 38;
        else s = 20;
        // 山顶、垭口这类地形 DEM 上表现为局部高点，风速会明显大于周边
        String reliefNote = "";
        if (t.available && t.min < t.mean - 8) {
            s -= 5;
            reliefNote = "；地块内有明显凸起，迎风面风速更大";
        }
        x.score = clamp(s);
        x.note = "7 天最大风速 " + String.format("%.1f", v) + " m/s" + reliefNote;
        if (v >= 10.8) {
            limits.add(new Limit("wind", "大风风险", "未来 7 天最大风速 " + String.format("%.1f", v)
                    + " m/s（6 级以上），高秆作物易倒伏，喷药雾滴会飘移", 3));
        } else if (v >= 8) {
            limits.add(new Limit("wind-mid", "风力偏大", "最大风速 " + String.format("%.1f", v)
                    + " m/s，喷药请选清晨或傍晚风小的时段", 1));
        }
        f.add(x);
    }

    private void addShapeFactor(List<Factor> f, Geometry g, List<Limit> limits) {
        Factor x = new Factor();
        x.key = "shape";
        x.label = "形状与规模";
        x.weight = 0.10;
        double c = g.compactness;
        double cs;
        if (c >= 0.75) cs = 100;
        else if (c >= 0.60) cs = 88;
        else if (c >= 0.45) cs = 72;
        else if (c >= 0.30) cs = 55;
        else cs = 35;
        double mu = g.mu();
        double as;
        if (mu >= 5) as = 100;
        else if (mu >= 2) as = 88;
        else if (mu >= 1) as = 75;
        else if (mu >= 0.5) as = 62;
        else as = 45;
        x.score = clamp((cs + as) / 2);
        x.note = String.format("%.2f", mu) + " 亩、周长 " + Math.round(g.perimeterM)
                + "m、紧凑度 " + String.format("%.2f", c);
        if (mu < 0.5) limits.add(new Limit("tiny", "面积过小", String.format("%.2f", mu)
                + " 亩，机械化与规模效益受限，建议与相邻地块合并经营", 2));
        if (c < 0.35) limits.add(new Limit("fragment", "形状破碎", "紧凑度 " + String.format("%.2f", c)
                + "，边界曲折，农机转弯多、边角浪费大，建议整理地界", 1));
        f.add(x);
    }

    private void addDrainageFactor(List<Factor> f, Terrain t, Weather w, Geometry g, List<Limit> limits) {
        Factor x = new Factor();
        x.key = "drainage";
        x.label = "排水与保水";
        x.weight = 0.13;
        double sl = t.available ? t.slopeDeg : Double.NaN;
        double p = w.precip7d == null ? Double.NaN : w.precip7d;
        double s;
        if (Double.isNaN(sl)) {
            s = 70;
        } else if (sl < 0.6) {
            if (!Double.isNaN(p) && p > 60) s = 40;
            else if (!Double.isNaN(p) && p >= 20) s = 75;
            else s = 88;
        } else if (sl < 3) {
            s = 100;
        } else if (sl < 8) {
            s = 90;
        } else if (sl < 15) {
            s = 70;
        } else {
            s = 45;
        }
        if (w.soilMoisture != null && w.soilMoisture > 0.42) s -= 10;
        x.score = clamp(s);
        x.note = (Double.isNaN(sl) ? "坡度未知" : "坡度 " + String.format("%.1f", sl) + "°")
                + (Double.isNaN(p) ? "" : "、7 天降水 " + String.format("%.0f", p) + "mm")
                + (w.soilMoisture == null ? "" : "、含水率 " + String.format("%.3f", w.soilMoisture));
        if (!Double.isNaN(sl) && sl < 0.6 && !Double.isNaN(p) && p > 60) {
            limits.add(new Limit("flat-wet", "低洼易涝", "近于平地且未来 7 天降水 " + String.format("%.0f", p)
                    + "mm，排水不畅，建议起垄栽培并挖排水沟", 3));
        }
        if (!Double.isNaN(sl) && sl > 15) {
            limits.add(new Limit("erosion", "水土流失风险", "坡度 " + String.format("%.1f", sl)
                    + "°，遇强降雨易冲刷表土，建议等高垄作或种草带", 2));
        }
        f.add(x);
    }

    // ------------------------------------------------------------------ 建议与作物

    private List<String> suggestionsOf(Terrain t, Geometry g, Weather w, List<Limit> limits, boolean pond) {
        List<String> out = new ArrayList<>();
        boolean has = false;
        for (Limit l : limits) {
            if (l.severity < 2) continue;
            out.add(l.detail);
            has = true;
        }
        if (!has) out.add("未发现明显限制因子，按常规农事安排即可");

        if (pond) {
            out.add("塘埂顶宽建议不小于 1.5m、边坡比 1:1.5，坡面夯实防渗");
            if (g.mu() > 3 && g.mu() < 10) out.add("面积 " + String.format("%.1f", g.mu())
                    + " 亩属中等规模，建议配 1 台 1.5kW 增氧机（每 3~5 亩 1 台）");
            else if (g.mu() >= 10) out.add("面积 " + String.format("%.1f", g.mu())
                    + " 亩，建议按 3kW/10 亩配置增氧机并分区投喂");
            out.add("进水口与排水口分设对角，便于换水时形成整体循环");
            return out;
        }

        if (t.available && t.slopeDeg >= 8 && t.slopeDeg < 25) {
            out.add("坡地建议等高起垄，垄向与坡向垂直，可减少径流冲刷");
        }
        if (t.available && !Double.isNaN(t.aspectDeg) && t.slopeDeg > 3) {
            double a = t.aspectDeg;
            if (a > 135 && a < 225) out.add("阳坡（朝南）光照足、地温高，喜温作物可早播 3~5 天");
            else if (a < 45 || a > 315) out.add("阴坡（朝北）光照弱、地温低，宜选耐阴作物或适当稀植");
        }
        if (w.precip7d != null && w.precip7d >= 10 && w.precip7d <= 60) {
            out.add("未来 7 天有 " + String.format("%.0f", w.precip7d) + "mm 降水，雨前追肥利用率最高，宜在降雨前 1 天完成");
        }
        if (w.soilMoisture != null && w.soilMoisture < 0.15) {
            out.add("墒情不足，播前灌水 30~40 m³/亩造墒，或采用地膜覆盖保墒");
        }
        if (g.compactness < 0.5) out.add("地块边界曲折，作业前先规划往返路线，减少空行程");
        return out;
    }

    private static final class CropHit {
        String name, reason, season;
        double score;
        int days;
    }

    /**
     * 适宜作物：不用拍脑袋写死清单，而是拿作物属性库里的
     * suitableRegion / tempRequirement / suitableSeason 与这个地块的
     * 区域、坡度、墒情、当前月份逐一匹配打分，取前 5。
     */
    private List<CropHit> suitableCrops(String region, Terrain t, Weather w, Geometry g, boolean pond) {
        Map<String, CropProperties.Props> all;
        try {
            java.lang.reflect.Method m = CropProperties.class.getDeclaredMethod("all");
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, CropProperties.Props> got = (Map<String, CropProperties.Props>) m.invoke(null);
            all = got == null ? new LinkedHashMap<>() : got;
        } catch (Exception e) {
            return List.of();
        }

        int month = LocalDate.now().getMonthValue();
        String season = month >= 3 && month <= 5 ? "春" : month >= 6 && month <= 8 ? "夏"
                : month >= 9 && month <= 11 ? "秋" : "冬";
        double sl = t.available ? t.slopeDeg : 0;
        double moist = w.soilMoisture == null ? 0.22 : w.soilMoisture;

        List<CropHit> hits = new ArrayList<>();
        for (CropProperties.Props p : all.values()) {
            if (p == null || p.displayName() == null) continue;
            double sc = 50;
            StringBuilder rs = new StringBuilder();
            String sr = p.suitableRegion() == null ? "" : p.suitableRegion();
            boolean regionHit = !sr.isBlank() && (sr.contains(region) || region.contains(sr));
            if (regionHit) { sc += 18; rs.append("适宜区域为").append(sr).append("；"); }
            else if (sr.isBlank()) { sc += 6; }
            else { sc -= 12; }

            String ss = p.suitableSeason() == null ? "" : p.suitableSeason();
            if (ss.contains(season + "播")) { sc += 14; rs.append("当前").append(season).append("季可播；"); }
            else if (!ss.isBlank()) { sc -= 6; }

            boolean wetLover = isWet(p.displayName());
            if (pond) {
                if (isAquatic(p.displayName())) { sc += 30; rs.append("水生/湿生作物，适合塘边或浅水区；"); }
                else { sc -= 25; }
            } else {
                if (wetLover && moist >= 0.30) { sc += 12; rs.append("喜湿且当前墒情充足；"); }
                if (isDroughtTolerant(p.displayName()) && moist < 0.15) { sc += 14; rs.append("耐旱，适合当前偏干墒情；"); }
                if (isSlopeCrop(p.displayName()) && sl >= 5 && sl <= 25) { sc += 12; rs.append("适合坡地种植；"); }
                if (wetLover && sl >= 8) { sc -= 12; rs.append("坡地不利于保水；"); }
            }

            if (p.totalDays() > 0 && p.totalDays() <= 120) sc += 4;
            if (rs.length() == 0) rs.append("属性库匹配度一般，仅供参考");
            CropHit h = new CropHit();
            h.name = p.displayName();
            h.score = clamp(sc);
            h.days = p.totalDays();
            h.season = ss.isBlank() ? "全年" : ss;
            h.reason = rs.toString().replaceAll("；$", "");
            hits.add(h);
        }
        hits.sort(Comparator.comparingDouble((CropHit h) -> -h.score).thenComparing(h -> h.name));
        List<CropHit> top = new ArrayList<>();
        for (CropHit h : hits) {
            if (top.size() >= 5) break;
            if (h.score < 55) continue;
            top.add(h);
        }
        return top;
    }

    private static boolean isWet(String n) {
        return n.contains("水稻") || n.contains("莲藕") || n.contains("茭白") || n.contains("茨菇")
                || n.contains("荸荠") || n.contains("水芹");
    }

    private static boolean isAquatic(String n) {
        return isWet(n) || n.contains("菱") || n.contains("芡实");
    }

    private static boolean isDroughtTolerant(String n) {
        return n.contains("谷子") || n.contains("高粱") || n.contains("糜") || n.contains("向日葵")
                || n.contains("芝麻") || n.contains("绿豆") || n.contains("红薯") || n.contains("甘薯");
    }

    private static boolean isSlopeCrop(String n) {
        return n.contains("茶") || n.contains("果树") || n.contains("核桃") || n.contains("板栗")
                || n.contains("柑橘") || n.contains("苹果") || n.contains("梨") || n.contains("桃")
                || n.contains("油茶") || n.contains("花椒");
    }

    // ------------------------------------------------------------------ 上游抓取

    private Terrain fetchElevation(List<double[]> ring, Geometry g) {
        int grid = g.areaM2 > 60000 ? 5 : 4;
        List<double[]> pts = samplePoints(ring, g, grid);
        if (pts.size() > MAX_SAMPLES) {
            // 均匀抽稀：保留首尾与等间距点，顶点优先（顶点在列表尾部）
            List<double[]> keep = new ArrayList<>();
            int vertexStart = pts.size() - ring.size();
            double step = (double) vertexStart / (MAX_SAMPLES - ring.size());
            for (double i = 0; keep.size() < MAX_SAMPLES - ring.size() && i < vertexStart; i += Math.max(1, step)) {
                keep.add(pts.get((int) i));
            }
            for (int i = vertexStart; i < pts.size() && keep.size() < MAX_SAMPLES; i++) keep.add(pts.get(i));
            pts = keep;
        }
        StringBuilder la = new StringBuilder(), lo = new StringBuilder();
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) { la.append(','); lo.append(','); }
            la.append(String.format("%.5f", pts.get(i)[1]));
            lo.append(String.format("%.5f", pts.get(i)[0]));
        }
        String url = "https://api.open-meteo.com/v1/elevation?latitude=" + la + "&longitude=" + lo;
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return Terrain.empty();
            Object o = JsonParser.parse(resp.body());
            if (!(o instanceof Map<?, ?> m) || !(m.get("elevation") instanceof List<?> list) || list.isEmpty()) {
                return Terrain.empty();
            }
            // 只保留有值的点：DEM 偶尔对个别坐标返回 null，混进去会把拟合带偏
            List<double[]> okPts = new ArrayList<>();
            List<Double> okZ = new ArrayList<>();
            for (int i = 0; i < pts.size() && i < list.size(); i++) {
                if (list.get(i) instanceof Number num) {
                    okPts.add(pts.get(i));
                    okZ.add(num.doubleValue());
                }
            }
            if (okZ.size() < 3) return Terrain.empty();
            double[] z = new double[okZ.size()];
            for (int i = 0; i < z.length; i++) z[i] = okZ.get(i);
            Terrain t = Terrain.of(z);
            fitPlane(g, okPts, z, t);
            return t;
        } catch (Exception e) {
            return Terrain.empty();
        }
    }

    private static final class Weather {
        Double temp, soilTemp0, soilTemp6, soilMoisture, precip7d;
        Double humidity;
        /** 未来 7 天累计短波辐射（MJ/m²），决定光合潜力。 */
        Double radiation7d;
        /** 未来 7 天最大风速（m/s），决定风害与喷药窗口。 */
        Double windMax7d;
        /** 未来 7 天最大日降水量，判断侵蚀风险用。 */
        Double rainMax7d;
        Double waterTemp;
        /** 这两个字段是不是用户传感器测的，决定界面上标"实测"还是"模型"。 */
        boolean moistureMeasured, soilTempMeasured;
        Integer code;
    }

    private Weather fetchWeather(Geometry g) {
        Weather w = new Weather();
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code"
                        + ",soil_temperature_0cm,soil_temperature_6cm,soil_moisture_0_to_1cm"
                        + "&daily=precipitation_sum,shortwave_radiation_sum,wind_speed_10m_max"
                        + "&forecast_days=7&timezone=auto",
                g.centroidLat, g.centroidLon);
        Map<?, ?> root = HttpJson.getMap(url, 8);
        if (root == null) return w;

        w.temp = HttpJson.dbl(root, "current", "temperature_2m");
        w.humidity = HttpJson.dbl(root, "current", "relative_humidity_2m");
        w.soilTemp0 = HttpJson.dbl(root, "current", "soil_temperature_0cm");
        w.soilTemp6 = HttpJson.dbl(root, "current", "soil_temperature_6cm");
        w.soilMoisture = HttpJson.dbl(root, "current", "soil_moisture_0_to_1cm");
        Double code = HttpJson.dbl(root, "current", "weather_code");
        w.code = code == null ? null : (int) Math.round(code);

        List<Double> precip = HttpJson.list(root, "daily", "precipitation_sum");
        if (!precip.isEmpty()) {
            w.precip7d = round1(HttpJson.sum(precip));
            double mx = 0;
            for (double v : precip) if (!Double.isNaN(v)) mx = Math.max(mx, v);
            w.rainMax7d = round1(mx);
        }
        List<Double> rad = HttpJson.list(root, "daily", "shortwave_radiation_sum");
        if (!rad.isEmpty()) w.radiation7d = Math.round(HttpJson.sum(rad) / 7.0 * 10) / 10.0;

        List<Double> wind = HttpJson.list(root, "daily", "wind_speed_10m_max");
        if (!wind.isEmpty()) {
            double mx = 0;
            for (double v : wind) if (!Double.isNaN(v)) mx = Math.max(mx, v);
            // Open-Meteo 的风速单位是 km/h，统一换算成 m/s，避免与"3 级风"这类说法对不上
            w.windMax7d = Math.round(mx / 3.6 * 10) / 10.0;
        }
        return w;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private String soilJson(Weather w, String soilType) {
        List<String> kv = new ArrayList<>();
        kv.add("soilType"); kv.add(Json.str(soilType));
        kv.add("source"); kv.add(Json.str(
                (w.moistureMeasured || w.soilTempMeasured)
                        ? "传感器实测（用户设备）+ 区域规则推断的土壤类型"
                        : "区域规则推断（非取土化验）"));
        if (w.soilMoisture != null) {
            kv.add("moisture"); kv.add(Json.num(w.soilMoisture));
            kv.add("moistureSource"); kv.add(Json.str(w.moistureMeasured ? "measured" : "model"));
        }
        if (w.soilTemp0 != null) { kv.add("temp0cm"); kv.add(Json.num(w.soilTemp0)); }
        if (w.soilTemp6 != null) { kv.add("temp6cm"); kv.add(Json.num(w.soilTemp6)); }
        if (w.soilMoisture == null && w.soilTemp0 == null) {
            kv.add("available"); kv.add("false");
        } else {
            kv.add("available"); kv.add("true");
        }
        return Json.obj(kv.toArray());
    }

    private String climateJson(Weather w) {
        List<String> kv = new ArrayList<>();
        kv.add("source"); kv.add(Json.str("Open-Meteo 预报（质心点）"));
        if (w.temp != null) kv.add("temp");
        if (w.temp != null) kv.add(Json.num(w.temp));
        if (w.humidity != null) { kv.add("humidity"); kv.add(Json.num(w.humidity)); }
        if (w.precip7d != null) { kv.add("precip7d"); kv.add(Json.num(w.precip7d)); }
        if (w.code != null) { kv.add("weatherCode"); kv.add(Json.num(w.code)); }
        return Json.obj(kv.toArray());
    }

    private String summaryOf(String level, Terrain t, Geometry g, Weather w, boolean pond) {
        StringBuilder sb = new StringBuilder();
        sb.append(pond ? "塘址" : "地块").append("面积 ").append(String.format("%.2f", g.mu())).append(" 亩");
        if (t.available) {
            sb.append("，坡度 ").append(String.format("%.1f", t.slopeDeg)).append("°，高差 ")
              .append(String.format("%.1f", t.relief)).append("m");
        }
        sb.append("，综合评定为").append(level);
        if (w.soilMoisture != null) {
            sb.append("；当前墒情").append(w.soilMoisture >= 0.30 ? "充足" : w.soilMoisture < 0.15 ? "偏干" : "适中");
        }
        if (w.precip7d != null) {
            sb.append("，未来 7 天降水 ").append(String.format("%.0f", w.precip7d)).append("mm");
        }
        sb.append("。评分为模型推算，非实地测产");
        return sb.toString();
    }

    // ------------------------------------------------------------------ 工具

    private static String cacheKey(List<double[]> ring, String type) {
        // 顶点逐个入 key 太碎，用「首点 + 顶点数 + 面积量级」近似，够区分不同地块
        double a2 = 0;
        for (int i = 0; i < ring.size(); i++) {
            double[] p = ring.get(i), q = ring.get((i + 1) % ring.size());
            a2 += p[0] * q[1] - q[0] * p[1];
        }
        long area = Math.round(Math.abs(a2) / 2.0 * 1e7);
        return "plot-analyze:" + Math.round(ring.get(0)[0] * 1e4) + ":" + Math.round(ring.get(0)[1] * 1e4)
                + ":" + ring.size() + ":" + area + ":" + type;
    }

    private static String fmtKey(Double v) {
        return v == null ? "-" : String.valueOf(Math.round(v * 1000));
    }

    private static List<double[]> readRing(Object o) {
        if (!(o instanceof List<?> list) || list.size() < 3) return null;
        List<double[]> ring = new ArrayList<>();
        for (Object item : list) {
            double lon, lat;
            if (item instanceof List<?> ll && ll.size() >= 2
                    && ll.get(0) instanceof Number a && ll.get(1) instanceof Number b) {
                lon = a.doubleValue();
                lat = b.doubleValue();
            } else if (item instanceof Map<?, ?> m) {
                Object lo = m.containsKey("lon") ? m.get("lon") : m.get("lng");
                Object la = m.get("lat");
                if (!(lo instanceof Number a) || !(la instanceof Number b)) return null;
                lon = a.doubleValue();
                lat = b.doubleValue();
            } else {
                return null;
            }
            // 先经后纬是 GeoJSON 约定；若用户误填成 [lat,lon]，靠范围纠正
            if (Math.abs(lon) <= 90 && Math.abs(lat) > 90) { double tmp = lon; lon = lat; lat = tmp; }
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            ring.add(new double[]{lon, lat});
        }
        return ring.size() >= 3 ? ring : null;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static Double dbl(String seg, String key) {
        int i = seg.indexOf(key);
        if (i < 0) return null;
        int s = i + key.length();
        int e = s;
        while (e < seg.length() && (Character.isDigit(seg.charAt(e)) || seg.charAt(e) == '-'
                || seg.charAt(e) == '.' || seg.charAt(e) == 'e' || seg.charAt(e) == 'E' || seg.charAt(e) == '+')) e++;
        if (e == s) return null;
        try {
            return Double.parseDouble(seg.substring(s, e));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer intg(String seg, String key) {
        Double d = dbl(seg, key);
        return d == null ? null : (int) Math.round(d);
    }
}
