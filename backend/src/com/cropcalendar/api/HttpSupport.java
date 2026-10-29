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

    /**
     * 统一按 UTF-8 写出 JSON。
     * 客户端提前断开（刷新、切页、超时取消）时 JDK 会抛 Broken pipe / insufficient bytes，
     * 这不是服务端错误，静默关闭即可；否则 app.err.log 会被这类堆栈刷满，
     * 真正的问题反而被淹掉。
     */
    static void write(HttpExchange ex, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        try {
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException clientGone) {
            // 连接已断，无需再回写任何内容
        } finally {
            ex.close();
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

    /** 读请求体（UTF-8）。上限 64KB —— 账号接口不该有更大的载荷 */
    static String body(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length > 64 * 1024) {
            throw new IOException("请求体过大");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 读请求体并解析成对象；不是合法 JSON 对象时返回 null，由调用方回 400 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> jsonBody(HttpExchange ex) throws IOException {
        String text = body(ex);
        if (text.isBlank()) return null;
        try {
            Object root = com.cropcalendar.util.JsonParser.parse(text);
            return root instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 取 JSON 对象里的字符串字段，缺省返回空串 */
    static String field(Map<String, Object> m, String key) {
        if (m == null) return "";
        Object v = m.get(key);
        return v instanceof String s ? s.trim() : "";
    }

    /** 统一的成功响应 */
    static String ok(String dataJson) {
        return "{\"code\":0,\"message\":\"ok\",\"data\":" + dataJson + "}";
    }

    /** 统一的错误响应 */
    static String error(int code, String message) {
        return "{\"code\":" + code + ",\"message\":"
                + com.cropcalendar.util.Json.str(message) + ",\"data\":null}";
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
