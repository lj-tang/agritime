package com.cropcalendar.api;

import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * /api/industry —— 产业扩展·渔业试点（T09）
 *
 * GET /api/industry/aquaculture/list                          列出渔业品类
 * GET /api/industry/aquaculture/calendar?category=&startDate=&lat=&lon=  生成生产日历
 *
 * 日历节点含放苗/投喂/换水/捕捞等，叠加天气适宜度判断与投喂指导。
 * 数据来源：data/industries/aquaculture/*.json
 */
public class IndustryHandler implements HttpHandler {

    private static final String DATA_DIR = "../data/industries/aquaculture";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        String path = ex.getRequestURI().getPath();
        try {
            if (path.endsWith("/list")) {
                handleList(ex);
            } else if (path.endsWith("/calendar")) {
                handleCalendar(ex);
            } else {
                HttpSupport.write(ex, 404, Json.obj("code", Json.str("40400"), "message", Json.str("未知接口")));
            }
        } catch (Exception e) {
            e.printStackTrace();
            HttpSupport.write(ex, 500, Json.obj("code", Json.str("50000"), "message", Json.str("服务异常：" + e.getMessage())));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleList(HttpExchange ex) throws IOException {
        Path dir = Path.of(DATA_DIR);
        List<String> items = new ArrayList<>();
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                var sorted = files.sorted().toList();
                for (Path f : sorted) {
                    if (!f.toString().endsWith(".json")) continue;
                    try {
                        String text = Files.readString(f, StandardCharsets.UTF_8);
                        Object root = JsonParser.parse(text);
                        if (root instanceof Map<?, ?> m) {
                            items.add(Json.obj(
                                    "category", Json.str(str(m.get("category"))),
                                    "name", Json.str(str(m.get("name"))),
                                    "alias", Json.str(str(m.get("alias"))),
                                    "type", Json.str(str(m.get("type"))),
                                    "growthCycleDays", String.valueOf(m.get("growthCycleDays")),
                                    "optimalTemp", Json.str(str(m.get("optimalTemp"))),
                                    "description", Json.str(str(m.get("description")))));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "total", String.valueOf(items.size()), "categories", Json.arr(items)));
    }

    @SuppressWarnings("unchecked")
    private void handleCalendar(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String category = HttpSupport.trimToNull(q.get("category"));
        String startDateStr = HttpSupport.trimToNull(q.get("startDate"));
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);

        if (category == null) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40001"), "message", Json.str("缺少参数 category")));
            return;
        }

        Path file = Path.of(DATA_DIR, category + ".json");
        if (!Files.exists(file)) {
            HttpSupport.write(ex, 404, Json.obj("code", Json.str("40401"), "message", Json.str("未知渔业品类：" + category)));
            return;
        }

        String text = Files.readString(file, StandardCharsets.UTF_8);
        Object root = JsonParser.parse(text);
        if (!(root instanceof Map<?, ?>)) {
            HttpSupport.write(ex, 500, Json.obj("code", Json.str("50001"), "message", Json.str("数据格式错误")));
            return;
        }
        Map<String, Object> data = (Map<String, Object>) root;

        LocalDate startDate;
        try {
            startDate = startDateStr != null ? LocalDate.parse(startDateStr, FMT) : LocalDate.now();
        } catch (Exception e) {
            startDate = LocalDate.now();
        }

        List<Map<String, Object>> stages = (List<Map<String, Object>>) data.get("stages");
        if (stages == null) stages = List.of();

        List<String> nodeJson = new ArrayList<>();
        for (Map<String, Object> stage : stages) {
            int dayOffset = toInt(stage.get("dayOffset"));
            int duration = toInt(stage.get("durationDays"));
            LocalDate nodeStart = startDate.plusDays(dayOffset);
            LocalDate nodeEnd = nodeStart.plusDays(duration);

            int tempMin = toInt(stage.get("tempMin"));
            int tempMax = toInt(stage.get("tempMax"));
            double estTemp = estimateTemp(lat, nodeStart.getMonthValue());
            String suitability = assessSuitability(estTemp, tempMin, tempMax);
            String weatherAdvice = buildWeatherAdvice(estTemp, tempMin, tempMax, str(stage.get("weatherTip")), nodeStart.getMonthValue());

            List<String> fields = new ArrayList<>();
            fields.add("\"name\":" + Json.str(str(stage.get("name"))));
            fields.add("\"action\":" + Json.str(str(stage.get("action"))));
            fields.add("\"detail\":" + Json.str(str(stage.get("detail"))));
            fields.add("\"startDate\":" + Json.str(nodeStart.format(FMT)));
            fields.add("\"endDate\":" + Json.str(nodeEnd.format(FMT)));
            fields.add("\"dayOffset\":" + dayOffset);
            fields.add("\"durationDays\":" + duration);
            fields.add("\"suitability\":" + Json.str(suitability));
            fields.add("\"estTemp\":" + String.format("%.1f", estTemp));
            fields.add("\"tempRange\":" + Json.str(tempMin + "~" + tempMax + "°C"));
            fields.add("\"weatherAdvice\":" + Json.str(weatherAdvice));
            fields.add("\"weatherTip\":" + Json.str(str(stage.get("weatherTip"))));
            if (stage.get("feedingGuide") != null) {
                fields.add("\"feedingGuide\":" + Json.str(str(stage.get("feedingGuide"))));
            }
            nodeJson.add("{" + String.join(",", fields) + "}");
        }

        String body = Json.obj(
                "code", Json.str("0"),
                "category", Json.str(str(data.get("category"))),
                "name", Json.str(str(data.get("name"))),
                "alias", Json.str(str(data.get("alias"))),
                "type", Json.str(str(data.get("type"))),
                "growthCycleDays", String.valueOf(data.get("growthCycleDays")),
                "optimalTemp", Json.str(str(data.get("optimalTemp"))),
                "description", Json.str(str(data.get("description"))),
                "startDate", Json.str(startDate.format(FMT)),
                "endDate", Json.str(startDate.plusDays(toInt(data.get("growthCycleDays"))).format(FMT)),
                "lat", Json.num(lat),
                "lon", Json.num(lon),
                "nodes", Json.arr(nodeJson));

        HttpSupport.write(ex, 200, body);
    }

    private static String assessSuitability(double temp, int min, int max) {
        if (temp >= min && temp <= max) return "适宜";
        if (temp < min - 5 || temp > max + 5) return "不适宜";
        return "勉强";
    }

    private static String buildWeatherAdvice(double temp, int min, int max, String tip, int month) {
        StringBuilder sb = new StringBuilder();
        if (temp < min) sb.append("当前估计水温").append(String.format("%.0f", temp)).append("°C偏低（需≥").append(min).append("°C）");
        else if (temp > max) sb.append("当前估计水温").append(String.format("%.0f", temp)).append("°C偏高（需≤").append(max).append("°C）");
        else sb.append("水温适宜（").append(String.format("%.0f", temp)).append("°C）");
        if (month >= 6 && month <= 8) sb.append("；夏季注意增氧防泛塘");
        if (month == 12 || month <= 2) sb.append("；冬季注意防冻加深水位");
        if (!tip.isEmpty()) sb.append("；").append(tip);
        return sb.toString();
    }

    private static double estimateTemp(double lat, int month) {
        double absLat = Math.abs(lat);
        double base = 28 - absLat * 0.6;
        double seasonal = 12 * Math.cos((month - 7) * Math.PI / 6);
        return base + seasonal;
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}