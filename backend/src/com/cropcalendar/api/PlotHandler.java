package com.cropcalendar.api;

import com.cropcalendar.orchestration.CacheStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /api/plots/environment —— 地块环境感知（T06）
 *
 * 根据经纬度返回实时气象、土壤类型、周边河流与水质等级，
 * 鱼塘类额外返回水质指标，全部纳入农事适宜度因子。
 *
 * 零依赖：JDK 自带 HttpClient，Open-Meteo 免费接口无 key。
 */
public class PlotHandler implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final double EARTH_R = 6371.0;

    /**
     * 环境数据缓存：气温/湿度这类数据几十秒内不会变，
     * 而这个接口每次都要实时打 Open-Meteo（实测 2.3 秒，超过 p95<2s 的验收线），
     * 不缓存的话用户每点一次地块详情都要等两秒多。
     * 5 分钟对"实时监控"够用，对上游也友好。
     */
    private static final CacheStore CACHE = new CacheStore();
    private static final int CACHE_TTL_SECONDS = 300;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);
        String type = HttpSupport.trimToNull(q.get("type"));
        if (type == null) type = "farmland";

        // 坐标取到小数点后三位（约 100 米）再拼 key，附近地块能共用一次上游请求
        String cacheKey = "plot:" + Math.round(lat * 1000) + ":" + Math.round(lon * 1000) + ":" + type;
        String cached = CACHE.get(cacheKey, String.class);
        if (cached != null) {
            HttpSupport.write(ex, 200, cached);
            return;
        }

        try {
            WeatherCurrent wc = fetchCurrentWeather(lat, lon);
            String soilType = inferSoilType(lat, lon);
            String soilDesc = soilDescription(soilType);
            List<RiverInfo> rivers = findNearbyRivers(lat, lon);
            // 水质等级只认最近水体的实测等级；没有邻近水体就返回 null，
            // 不再用坐标算一个"看起来很像真的"等级糊弄用户。
            String waterGrade = rivers.isEmpty() ? null : rivers.get(0).grade();
            List<String> factors = buildFactors(wc, soilType, waterGrade, type, rivers);

            List<String> parts = new ArrayList<>();
            parts.add("\"temperature\":" + (wc.temp != null ? wc.temp : "null"));
            parts.add("\"humidity\":" + (wc.humidity != null ? wc.humidity : "null"));
            parts.add("\"windSpeed\":" + (wc.wind != null ? wc.wind : "null"));
            parts.add("\"weatherCode\":" + (wc.code != null ? wc.code : "null"));
            parts.add("\"soilType\":" + Json.str(soilType));
            parts.add("\"soilDescription\":" + Json.str(soilDesc));

            List<String> riverJson = new ArrayList<>();
            for (RiverInfo r : rivers) {
                riverJson.add(Json.obj(
                        "name", Json.str(r.name),
                        "distance", String.valueOf(r.distance),
                        "waterGrade", Json.str(r.grade)));
            }
            parts.add("\"nearbyRivers\":" + Json.arr(riverJson));
            parts.add("\"waterQualityGrade\":" + Json.str(waterGrade));

            if ("fishpond".equals(type)) {
                // 没有任何水质传感器接入，这三项是按经纬度推的估算值，
                // 所以显式带上 estimated=true，前端会标"估算"，别当实测数据讲。
                double ph = 6.5 + ((lon * 7 + lat * 3) % 20) / 10.0;
                double dox = 5.0 + ((lat * 5 + lon * 2) % 50) / 10.0;
                double turb = 10 + ((lat * 3 + lon * 4) % 40);
                parts.add("\"waterQuality\":" + Json.obj(
                        "grade", Json.str(waterGrade),
                        "ph", String.format("%.1f", ph),
                        "dissolvedOxygen", String.format("%.1f", dox),
                        "turbidity", String.format("%.0f", turb),
                        "estimated", "true"));
            } else {
                parts.add("\"waterQuality\":null");
            }

            List<String> factorJson = new ArrayList<>();
            for (String f : factors) factorJson.add(Json.str(f));
            parts.add("\"factors\":" + Json.arr(factorJson));

            // parts 里每一项已经是拼好的 "\"key\":value" 片段，直接拼接成对象。
            // 不能再交给 Json.obj()：它接收的是「键, 值」交替参数，
            // 传片段数组会把每个片段当成键，导致整段键值错位、返回非法 JSON。
            String body = "{" + String.join(",", parts) + "}";
            CACHE.put(cacheKey, body, CACHE_TTL_SECONDS);
            HttpSupport.write(ex, 200, body);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("error", Json.str("INTERNAL_ERROR"), "message", Json.str(String.valueOf(e.getMessage()))));
        }
    }

    private record WeatherCurrent(Double temp, Integer humidity, Double wind, Integer code) {}

    private WeatherCurrent fetchCurrentWeather(double lat, double lon) {
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
                lat, lon);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return new WeatherCurrent(null, null, null, null);
            String body = resp.body();
            return parseCurrent(body);
        } catch (Exception e) {
            return new WeatherCurrent(null, null, null, null);
        }
    }

    private WeatherCurrent parseCurrent(String body) {
        Double temp = null; Integer humidity = null; Double wind = null; Integer code = null;
        int curIdx = body.indexOf("\"current\":");
        if (curIdx < 0) return new WeatherCurrent(null, null, null, null);
        String cur = body.substring(curIdx);
        temp = extractDouble(cur, "\"temperature_2m\":");
        humidity = extractInt(cur, "\"relative_humidity_2m\":");
        wind = extractDouble(cur, "\"wind_speed_10m\":");
        code = extractInt(cur, "\"weather_code\":");
        return new WeatherCurrent(temp, humidity, wind, code);
    }

    private Double extractDouble(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        i += key.length();
        int end = i;
        while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.' || s.charAt(end) == '-')) end++;
        try { return Double.parseDouble(s.substring(i, end)); } catch (Exception e) { return null; }
    }

    private Integer extractInt(String s, String key) {
        Double d = extractDouble(s, key);
        return d != null ? d.intValue() : null;
    }

    private String inferSoilType(double lat, double lon) {
        if (lat >= 42 && lon >= 120) return "黑土";
        if (lat >= 35 && lat < 42 && lon >= 110 && lon < 122) return "潮土";
        if (lat >= 20 && lat < 35 && lon >= 105 && lon <= 122) return "红壤";
        if (lat >= 35 && lon >= 75 && lon < 110) return "黄绵土";
        if (lat >= 20 && lat < 35 && lon >= 90 && lon < 105) return "紫色土";
        if (lat >= 25 && lon >= 100 && lon < 110) return "黄壤";
        return "褐土";
    }

    private String soilDescription(String type) {
        return switch (type) {
            case "黑土" -> "有机质丰富，保水保肥力强，宜种大豆玉米";
            case "潮土" -> "土层深厚，肥力中等，宜种小麦玉米大蒜";
            case "红壤" -> "酸性偏强，需改良，宜种茶果薯类";
            case "黄绵土" -> "土质疏松，易侵蚀，需保水措施";
            case "紫色土" -> "含磷钾丰富，宜种水稻果蔬";
            case "黄壤" -> "湿润肥沃，宜种水稻烟草";
            default -> "土层较厚，适种性广";
        };
    }

    private record RiverInfo(String name, double distance, String grade) {}

    private static final String[] RIVER_NAMES = {
            "黄河中游", "黄河下游", "长江中游", "长江下游", "淮河",
            "珠江", "松花江", "海河", "辽河", "钱塘江"
    };
    private static final double[] RIVER_LAT = {36.0, 35.2, 30.5, 32.0, 33.0, 23.1, 46.0, 39.0, 42.0, 30.0};
    private static final double[] RIVER_LON = {110.5, 115.0, 112.3, 118.8, 116.3, 113.3, 126.5, 117.2, 123.5, 120.5};
    private static final String[] RIVER_GRADES = {"Ⅲ类", "Ⅲ类", "Ⅱ类", "Ⅱ类", "Ⅳ类", "Ⅲ类", "Ⅲ类", "Ⅳ类", "Ⅳ类", "Ⅱ类"};

    private List<RiverInfo> findNearbyRivers(double lat, double lon) {
        List<RiverInfo> result = new ArrayList<>();
        for (int i = 0; i < RIVER_NAMES.length; i++) {
            double dist = haversine(lat, lon, RIVER_LAT[i], RIVER_LON[i]);
            if (dist <= 200) {
                result.add(new RiverInfo(RIVER_NAMES[i], Math.round(dist * 10) / 10.0, RIVER_GRADES[i]));
            }
        }
        result.sort((a, b) -> Double.compare(a.distance, b.distance));
        return result.size() > 4 ? result.subList(0, 4) : result;
    }

    private List<String> buildFactors(WeatherCurrent wc, String soil, String waterGrade, String type, List<RiverInfo> rivers) {
        List<String> f = new ArrayList<>();
        f.add("土壤：" + soil);
        if (wc.temp != null) {
            if (wc.temp >= 15 && wc.temp <= 30) f.add("气温适宜（" + wc.temp + "°C）");
            else if (wc.temp < 5) f.add("气温偏低，注意防冻");
            else if (wc.temp > 35) f.add("高温预警，注意灌溉");
            else f.add("气温一般（" + wc.temp + "°C）");
        }
        if (wc.humidity != null) {
            if (wc.humidity >= 40 && wc.humidity <= 75) f.add("湿度适中（" + wc.humidity + "%）");
            else if (wc.humidity > 80) f.add("湿度过高，防病害");
            else f.add("湿度偏低（" + wc.humidity + "%）");
        }
        if (waterGrade != null) {
            f.add("水质等级：" + waterGrade + "（参考" + rivers.get(0).name + "）");
        } else {
            f.add("水质：附近无可参考监测水体");
        }
        if ("fishpond".equals(type)) f.add("鱼塘地块：关注溶氧与pH");
        if ("greenhouse".equals(type)) f.add("大棚地块：关注通风控温");
        if ("paddy".equals(type)) f.add("水稻田：保持水位管理");
        if (!rivers.isEmpty()) f.add("近" + rivers.get(0).name + "（" + rivers.get(0).distance + "km）");
        return f;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_R * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}