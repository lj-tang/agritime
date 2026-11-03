package com.cropcalendar.service.sensor;

import com.cropcalendar.util.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 传感器定时轮询与阈值告警（T28）。
 */
public final class SensorPoller {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static volatile boolean running = false;

    private SensorPoller() {}

    /** 拉取单个设备 URL，返回 JSON 字符串或 null */
    public static String fetchOnce(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) return resp.body();
            return null;
        } catch (Exception e) {
            System.err.println("[sensor] 拉取失败 " + url + ": " + e.getMessage());
            return null;
        }
    }

    /** 轮询所有设备，返回成功拉取的数量 */
    @SuppressWarnings("unchecked")
    public static int pollAll() {
        List<Map<String, Object>> devs = SensorStore.allDevices();
        int count = 0;
        for (Map<String, Object> dev : devs) {
            String id = String.valueOf(dev.get("id"));
            String plotId = String.valueOf(dev.get("plotId"));
            String url = String.valueOf(dev.get("url"));
            String lastOnline = dev.get("lastOnline") != null ? String.valueOf(dev.get("lastOnline")) : null;

            String result = fetchOnce(url);
            if (result == null) {
                SensorStore.updateStatus(plotId, id, "offline", lastOnline);
                continue;
            }
            try {
                Object parsed = JsonParser.parse(result);
                if (parsed instanceof Map<?, ?> m) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    m.forEach((k, v) -> values.put(String.valueOf(k), v));
                    SensorStore.addReading(plotId, id, values, "auto");
                    checkThresholds(plotId, id, dev, values);
                    count++;
                }
            } catch (Exception e) {
                System.err.println("[sensor] 解析失败 " + url + ": " + e.getMessage());
                SensorStore.updateStatus(plotId, id, "offline", lastOnline);
            }
        }
        return count;
    }

    /** 阈值检查：超阈值生成提醒 */
    @SuppressWarnings("unchecked")
    public static void checkThresholds(String plotId, String deviceId, Map<String, Object> dev, Map<String, Object> values) {
        Object thObj = dev.get("thresholds");
        if (!(thObj instanceof Map<?, ?>)) return;
        Map<String, Object> thresholds = (Map<String, Object>) thObj;

        for (Map.Entry<String, Object> e : thresholds.entrySet()) {
            String key = e.getKey();
            if (!(e.getValue() instanceof Map<?, ?>)) continue;
            Map<String, Object> th = (Map<String, Object>) e.getValue();
            Object valObj = values.get(key);
            if (valObj == null) continue;
            double val = toDouble(valObj);
            Double min = th.get("min") != null ? toDouble(th.get("min")) : null;
            Double max = th.get("max") != null ? toDouble(th.get("max")) : null;

            if (min != null && val < min) {
                generateAlert(plotId, deviceId, dev, key, val, min, "low");
            } else if (max != null && val > max) {
                generateAlert(plotId, deviceId, dev, key, val, max, "high");
            }
        }
    }

    private static void generateAlert(String plotId, String deviceId, Map<String, Object> dev,
                                       String metric, double val, double threshold, String direction) {
        String devName = dev.get("name") != null ? String.valueOf(dev.get("name")) : deviceId;
        String metricLabel = metricLabel(metric);
        String severity = metric.equals("dissolvedOxygen") && direction.equals("low") ? "high" : "medium";
        String title = metricLabel + (direction.equals("low") ? "低于阈值" : "高于阈值");
        String detail = String.format("%s 读数 %.2f，%s阈值 %.2f", devName, val, direction.equals("low") ? "低于" : "高于", threshold);
        String advice = metric.equals("dissolvedOxygen") && direction.equals("low")
                ? "溶氧过低，建议立即开启增氧机并减少投喂"
                : "请检查设备与水质状况";

        System.out.println("[sensor] 告警: " + title + " — " + detail);

        try {
            saveAlertReminder(plotId, title, detail, advice, severity);
        } catch (Exception e) {
            System.err.println("[sensor] 告警持久化失败: " + e.getMessage());
        }
    }

    private static void saveAlertReminder(String plotId, String title, String detail, String advice, String severity) {
        try {
            Path p = Path.of("var", "reminders", "sensor_alerts.json");
            Files.createDirectories(p.getParent());
            Map<String, Object> state = new LinkedHashMap<>();
            if (Files.isReadable(p)) {
                Object root = JsonParser.parse(Files.readString(p));
                if (root instanceof Map<?, ?> m) m.forEach((k, v) -> state.put(String.valueOf(k), v));
            }
            List<Map<String, Object>> items = state.get("items") instanceof List<?> l
                    ? new ArrayList<>((List<Map<String, Object>>) l) : new ArrayList<>();
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("id", "sensor_" + System.currentTimeMillis());
            alert.put("type", "sensor");
            alert.put("level", severity);
            alert.put("title", title);
            alert.put("detail", detail);
            alert.put("advice", advice);
            alert.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            alert.put("plotId", plotId);
            alert.put("source", "sensor");
            alert.put("createdAt", LocalDateTime.now().format(TS));
            items.add(alert);
            while (items.size() > 200) items.remove(0);
            state.put("items", items);
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, com.cropcalendar.util.Json.stringify(state), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            System.err.println("[sensor] 告警保存失败: " + e.getMessage());
        }
    }

    private static String metricLabel(String metric) {
        switch (metric) {
            case "dissolvedOxygen": return "溶氧";
            case "ph": return "pH值";
            case "waterTemp": return "水温";
            case "ammonia": return "氨氮";
            case "turbidity": return "浊度";
            default: return metric;
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}