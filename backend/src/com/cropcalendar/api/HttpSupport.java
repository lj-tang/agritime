package com.cropcalendar.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 层的小工具：CORS、响应写出、query 解析。
 * 全部是骨架服务的基础设施，与业务无关。
 */
final class HttpSupport {

    /**
     * 允许跨域 —— 成员1 的页面很可能跑在另一个端口（比如 5173），
     * 不放开的话他连本地都调不通，会白白浪费一天。
     */
    static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
    }

    /** 统一按 UTF-8 写出 JSON */
    static void write(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 解析 ?a=1&b=2，自动做 URL 解码（中文作物名靠它） */
    static Map<String, String> query(String rawQuery) {
        Map<String, String> m = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return m;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) continue;
            int i = pair.indexOf('=');
            if (i <= 0) continue;
            String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }

    static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static double parseDouble(String s, double fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private HttpSupport() {
    }
}
