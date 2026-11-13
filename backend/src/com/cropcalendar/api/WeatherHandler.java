package com.cropcalendar.api;

import com.cropcalendar.config.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 首页天气卡的代理。
 *
 * 为什么需要它：前端原来直接打 https://api.open-meteo.com，国内网络时通时断，
 * 断的时候卡片就显示"天气数据暂不可用"——用户看到的是"又坏了"，其实是跨境网络问题。
 * 服务器（境外 VPS）访问该接口稳定（实测 200 / 1.5s），所以改由后端代取。
 *
 * 只做一件事：把前端传来的查询串原样转发，并原样返回 Open-Meteo 的 JSON，
 * 这样前端解析逻辑（buildWeatherInfo）完全不用改。
 */
public class WeatherHandler implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(AppConfig.httpConnectTimeoutMs()))
            .build();

    private static final String UPSTREAM = "https://api.open-meteo.com/v1/forecast";

    /** 天气 60 秒内不会变；同一坐标反复刷新不该每次都跨境请求。 */
    private static final long CACHE_TTL_MS = 60_000L;
    private static final int CACHE_MAX = 400;
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(String body, long at) {
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpSupport.write(ex, 405, "{\"code\":\"405\",\"message\":\"仅支持 GET\"}");
                return;
            }
            Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
            // 前端沿用的是 Open-Meteo 的参数名（latitude/longitude），两个都收
            String lat = q.containsKey("lat") ? q.get("lat") : q.get("latitude");
            String lon = q.containsKey("lon") ? q.get("lon") : q.get("longitude");
            if (lat == null || lon == null) {
                HttpSupport.write(ex, 400, "{\"code\":\"400\",\"message\":\"缺少 lat / lon\"}");
                return;
            }
            // 坐标只保留 2 位小数（约 1 公里），既是缓存键，也避免无意义的跨境请求
            String key = round2(lat) + "," + round2(lon) + "," + (q.getOrDefault("daily", ""));
            Cached hit = CACHE.get(key);
            if (hit != null && System.currentTimeMillis() - hit.at() < CACHE_TTL_MS) {
                writeRaw(ex, hit.body());
                return;
            }

            String body = fetch(lat, lon, q.get("daily"));
            if (body == null) {
                // 上游失败：回 502，前端据此回退到直连或显示降级文案
                HttpSupport.write(ex, 502, "{\"code\":\"502\",\"message\":\"天气服务暂不可用\"}");
                return;
            }
            if (CACHE.size() > CACHE_MAX) CACHE.clear();
            CACHE.put(key, new Cached(body, System.currentTimeMillis()));
            writeRaw(ex, body);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, "{\"code\":\"500\",\"message\":\"天气代理异常\"}");
        }
    }

    private static String fetch(String lat, String lon, String daily) {
        try {
            StringBuilder url = new StringBuilder(UPSTREAM)
                    .append("?latitude=").append(lat)
                    .append("&longitude=").append(lon)
                    .append("&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
                    .append("&timezone=auto");
            if (daily != null && !daily.isBlank()) {
                url.append("&daily=").append(daily);
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofMillis(AppConfig.httpReadTimeoutMs()))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String round2(String v) {
        try {
            return String.valueOf(Math.round(Double.parseDouble(v) * 100) / 100.0);
        } catch (Exception e) {
            return v;
        }
    }

    /** 原样透传 JSON：不包 data 壳，前端解析逻辑与直连时完全一致。 */
    private static void writeRaw(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=60");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
