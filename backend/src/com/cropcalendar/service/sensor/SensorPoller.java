package com.cropcalendar.service.sensor;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.service.reminder.ReminderStore;
import com.cropcalendar.util.AppTime;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备拉取与阈值告警（V5 T17）。
 *
 * 设备地址由用户填写，服务端会真的去请求它 —— 这就是一个天然的 SSRF 入口。
 * 所以每次拉取前必须先过 {@link #assertPublicUrl(String)}：
 * 只允许 http/https，解析出的地址不能是回环、内网、链路本地、组播或云厂商元数据段。
 *
 * 告警不再另存一份文件，直接写进该用户的 ReminderStore（type=sensor），
 * 这样提醒中心能看到、能标已读、能按账号同步。
 */
public final class SensorPoller {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(AppConfig.httpConnectTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private SensorPoller() {
    }

    /** 拉取一台设备：成功返回读数，失败返回 null 并把设备标记 offline。 */
    public static Map<String, Object> pollDevice(String userId, String plotId, Map<String, Object> dev) {
        String deviceId = SensorStore.str(dev.get("id"));
        String url = SensorStore.str(dev.get("url"));
        if (url == null) {
            SensorStore.markStatus(userId, plotId, deviceId, "unconfigured");
            return null;
        }
        try {
            Map<String, Object> values = fetchOnce(url);
            if (values == null) {
                SensorStore.markStatus(userId, plotId, deviceId, "offline");
                return null;
            }
            SensorStore.addReading(userId, plotId, deviceId, values, "auto");
            SensorStore.markStatus(userId, plotId, deviceId, "online");
            List<Map<String, Object>> alerts = checkThresholds(userId, plotId, dev, values);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("deviceId", deviceId);
            out.put("values", values);
            out.put("alerts", alerts);
            return out;
        } catch (Exception e) {
            System.err.println("[sensor] 拉取失败 " + deviceId + "：" + e.getMessage());
            SensorStore.markStatus(userId, plotId, deviceId, "offline");
            return null;
        }
    }

    /** 拉取该地块全部设备，返回成功数量与明细。 */
    public static List<Map<String, Object>> pollPlot(String userId, String plotId) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> dev : SensorStore.devices(userId, plotId)) {
            Map<String, Object> r = pollDevice(userId, plotId, dev);
            if (r != null) results.add(r);
        }
        return results;
    }

    /**
     * 请求设备并解析成扁平数值表。
     * 支持两种返回体：直接 {"waterTemp":22.5,...}，或 {"values":{...}} 包一层。
     */
    public static Map<String, Object> fetchOnce(String url) throws IOException, InterruptedException {
        assertPublicUrl(url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(AppConfig.httpReadTimeoutMs()))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("设备返回 HTTP " + resp.statusCode());
        }
        String body = resp.body();
        if (body == null) return null;
        if (body.length() > MAX_BODY_BYTES) {
            throw new IOException("设备返回体过大（" + body.length() + " 字节）");
        }
        Object root = JsonParser.parse(body);
        if (!(root instanceof Map<?, ?> m)) {
            throw new IOException("设备返回的不是 JSON 对象");
        }
        Object inner = m.get("values");
        Map<?, ?> src = inner instanceof Map<?, ?> v ? v : m;

        Map<String, Object> values = new LinkedHashMap<>();
        src.forEach((k, v) -> {
            if (v instanceof Number) {
                values.put(String.valueOf(k), v);
            } else if (v instanceof String s) {
                try {
                    values.put(String.valueOf(k), Double.parseDouble(s.trim()));
                } catch (NumberFormatException ignored) {
                    // 非数值字段（设备型号、时间戳字符串）不参与阈值判断
                }
            }
        });
        if (values.isEmpty()) throw new IOException("设备返回里没有可用的数值字段");
        return values;
    }

    /**
     * 阈值判定：只对配了阈值的指标判断，超限就往提醒中心写一条。
     * 同一设备同一指标同一天复用同一个 id，反复拉取只会更新内容，不会刷屏。
     */
    public static List<Map<String, Object>> checkThresholds(String userId, String plotId,
                                                            Map<String, Object> dev,
                                                            Map<String, Object> values) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (!(dev.get("thresholds") instanceof Map<?, ?> thresholds)) return alerts;

        String deviceId = SensorStore.str(dev.get("id"));
        String deviceName = SensorStore.str(dev.get("name"));
        for (Map.Entry<?, ?> e : thresholds.entrySet()) {
            String metric = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> range)) continue;
            Object raw = values.get(metric);
            if (!(raw instanceof Number n)) continue;

            double val = n.doubleValue();
            Double min = dbl(range.get("min"));
            Double max = dbl(range.get("max"));
            String direction = null;
            double boundary = 0;
            if (min != null && val < min) { direction = "low"; boundary = min; }
            else if (max != null && val > max) { direction = "high"; boundary = max; }
            if (direction == null) continue;

            String title = label(metric) + (direction.equals("low") ? "低于下限" : "高于上限");
            String detail = (deviceName == null ? "设备" : deviceName) + " 读数 " + trim(val)
                    + (direction.equals("low") ? "，低于下限 " : "，高于上限 ") + trim(boundary);
            String advice = advice(metric, direction);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "sensor|" + deviceId + "|" + metric + "|" + direction + "|" + AppTime.today());
            item.put("type", "sensor");
            item.put("level", severity(metric, direction));
            item.put("title", title);
            item.put("detail", detail);
            item.put("advice", advice);
            item.put("date", AppTime.today().toString());
            item.put("plotId", plotId);
            item.put("name", deviceName);
            item.put("source", "sensor");
            item.put("createdAt", AppTime.now().toString());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("items", List.of(item));
            ReminderStore.sync(userId, body);
            alerts.add(item);
        }
        return alerts;
    }

    /**
     * SSRF 防护：只放行公网 http/https。
     *
     * 这里刻意做了三层：协议白名单、主机名黑名单、解析后的地址段检查。
     * 只看字符串（比如判断是否包含 127.0.0.1）挡不住 127.0.0.1.nip.io 这类域名，
     * 所以必须真正解析一次 DNS 再判断。
     */
    public static void assertPublicUrl(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IOException("设备地址不合法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("设备地址只支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IOException("设备地址缺少主机名");
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".local") || lower.endsWith(".internal")) {
            throw new IOException("不允许访问内网地址");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new IOException("设备域名解析失败");
        }
        for (InetAddress addr : addresses) {
            if (isPrivate(addr)) throw new IOException("不允许访问内网地址");
        }
    }

    private static boolean isPrivate(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            // 100.64.0.0/10 运营商级 NAT
            if (first == 100 && second >= 64 && second <= 127) return true;
            // 0.0.0.0/8
            if (first == 0) return true;
            // 169.254.169.254 云厂商元数据（已被 link-local 覆盖，这里显式再挡一次）
            if (first == 169 && second == 254) return true;
        }
        if (b.length == 16) {
            // fc00::/7 唯一本地地址
            if ((b[0] & 0xfe) == 0xfc) return true;
        }
        return false;
    }

    private static String label(String metric) {
        return switch (metric) {
            case "dissolvedOxygen" -> "溶氧";
            case "waterTemp" -> "水温";
            case "ph" -> "pH";
            case "ammonia" -> "氨氮";
            case "turbidity" -> "浊度";
            default -> metric;
        };
    }

    private static String severity(String metric, String direction) {
        // 溶氧偏低是最危险的：几小时内就能翻塘
        if ("dissolvedOxygen".equals(metric) && "low".equals(direction)) return "high";
        if ("ammonia".equals(metric) && "high".equals(direction)) return "high";
        return "medium";
    }

    private static String advice(String metric, String direction) {
        if ("dissolvedOxygen".equals(metric) && "low".equals(direction)) {
            return "立即开增氧机，暂停投喂，必要时换水；次日清晨复测";
        }
        if ("ammonia".equals(metric) && "high".equals(direction)) {
            return "减少投喂，加大换水，必要时用微生物制剂降氨";
        }
        if ("waterTemp".equals(metric)) {
            return "水温异常时鱼虾摄食下降，先调水位再观察摄食";
        }
        if ("ph".equals(metric)) {
            return "pH 异常先测早晚两次，确认后再决定是否用生石灰或有机酸调节";
        }
        return "检查设备探头是否结垢，并人工复测一次";
    }

    private static Double dbl(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? null : Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String trim(double v) {
        double r = Math.round(v * 100) / 100.0;
        return Math.abs(r - Math.rint(r)) < 1e-9 ? String.valueOf((long) Math.rint(r)) : String.valueOf(r);
    }
}
