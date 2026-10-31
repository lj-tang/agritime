package com.cropcalendar.util;

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
 * 上游 JSON 抓取与解析的唯一入口。
 *
 * 之前每个 Handler 各自写一份 extractDouble / arr / dbl，三份实现三种边界处理，
 * 出现过"从 daily_units 里读到单位字符串导致降水恒为 0"这类事故 ——
 * 同一个 bug 修三遍。这里收口成一份，谁要改只改这里。
 */
public final class HttpJson {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private HttpJson() {
    }

    /** GET 一个 JSON；失败返回 null（调用方自行降级，绝不返回假数据）。 */
    public static String get(String url, int timeoutSeconds) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET().build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            return resp.body();
        } catch (Exception e) {
            return null;
        }
    }

    /** GET 并解析成 Map；失败返回 null。 */
    public static Map<?, ?> getMap(String url, int timeoutSeconds) {
        String body = get(url, timeoutSeconds);
        if (body == null) return null;
        try {
            Object o = JsonParser.parse(body);
            return o instanceof Map<?, ?> m ? m : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 取值

    public static Double num(Object o) {
        if (o instanceof Number n) {
            double v = n.doubleValue();
            return Double.isNaN(v) ? null : v;
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public static Double dbl(Map<?, ?> m, String key) {
        return m == null ? null : num(m.get(key));
    }

    /** 取嵌套对象里某个键的值，路径如 "current" → "temperature_2m"。 */
    public static Double dbl(Map<?, ?> root, String objKey, String key) {
        if (root == null) return null;
        Object o = root.get(objKey);
        return o instanceof Map<?, ?> m ? num(m.get(key)) : null;
    }

    /** 取数值数组，缺测保留为 NaN，方便调用方跳过而不是当 0。 */
    public static List<Double> list(Map<?, ?> root, String objKey, String key) {
        List<Double> out = new ArrayList<>();
        if (root == null) return out;
        Object o = root.get(objKey);
        if (!(o instanceof Map<?, ?> m)) return out;
        Object arr = m.get(key);
        if (!(arr instanceof List<?> l)) return out;
        for (Object v : l) {
            Double d = num(v);
            out.add(d == null ? Double.NaN : d);
        }
        return out;
    }

    /** 求和，自动跳过 NaN。 */
    public static double sum(List<Double> xs) {
        double s = 0;
        for (double x : xs) if (!Double.isNaN(x)) s += x;
        return s;
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
