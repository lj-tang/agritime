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
 * /api/recommend —— 种植推荐（T15）
 *
 * GET /api/recommend?month=10&lat=35&lon=116&location=山东
 *
 * 综合三个维度打分推荐作物：
 *   ① 节气适宜性 —— 当前节气/月份是否在该作物的播种窗口内
 *   ② 气候匹配   —— 纬度/温度区间是否适合该作物生长
 *   ③ 市场需求热度 —— 来自 data/market/market.json，无文件时用内置默认值
 *
 * 返回 Top10，每条包含三个维度的理由文案。
 */
public class RecommendHandler implements HttpHandler {

    private static final String MARKET_FILE = "../data/market/market.json";

    /** 二十四节气名（按月份近似，用于理由文案） */
    private static final String[] MONTH_TERMS = {
            "小寒/大寒", "立春/雨水", "惊蛰/春分", "清明/谷雨",
            "立夏/小满", "芒种/夏至", "小暑/大暑", "立秋/处暑",
            "白露/秋分", "寒露/霜降", "立冬/小雪", "大雪/冬至"
    };

    /** 内置作物数据库：名称、类别、播种月份、纬度范围、温度范围、生长天数、市场热度 */
    private static final Object[][] CROP_DB = {
            // name, category, plantingMonths, latMin, latMax, tempMin, tempMax, growthDays, marketDemand
            {"大蒜", "蔬菜", new int[]{9, 10}, 25, 45, 5, 25, 240, "high"},
            {"冬小麦", "粮食", new int[]{9, 10, 11}, 30, 45, 0, 20, 230, "medium"},
            {"春小麦", "粮食", new int[]{3, 4}, 40, 55, 5, 20, 120, "medium"},
            {"玉米", "粮食", new int[]{4, 5, 6}, 25, 50, 15, 35, 110, "high"},
            {"水稻", "粮食", new int[]{4, 5, 6}, 20, 45, 18, 35, 130, "high"},
            {"大豆", "粮食", new int[]{5, 6}, 25, 50, 15, 30, 110, "high"},
            {"棉花", "经济", new int[]{4, 5}, 25, 45, 15, 35, 150, "medium"},
            {"油菜", "经济", new int[]{9, 10}, 25, 40, 5, 25, 200, "medium"},
            {"花生", "经济", new int[]{4, 5}, 20, 40, 18, 35, 120, "medium"},
            {"番茄", "蔬菜", new int[]{3, 4, 7}, 20, 45, 15, 35, 90, "high"},
            {"辣椒", "蔬菜", new int[]{3, 4, 7}, 20, 45, 18, 35, 90, "high"},
            {"黄瓜", "蔬菜", new int[]{3, 4, 7}, 20, 45, 15, 35, 70, "medium"},
            {"马铃薯", "蔬菜", new int[]{2, 3, 8}, 30, 55, 5, 25, 100, "high"},
            {"红薯", "粮食", new int[]{4, 5}, 20, 40, 18, 35, 120, "medium"},
            {"白菜", "蔬菜", new int[]{3, 8, 9}, 25, 50, 10, 30, 70, "medium"},
            {"萝卜", "蔬菜", new int[]{3, 8, 9}, 25, 50, 10, 30, 80, "medium"},
            {"西瓜", "水果", new int[]{3, 4}, 20, 45, 18, 35, 90, "high"},
            {"草莓", "水果", new int[]{9, 10}, 25, 45, 5, 25, 120, "high"},
            {"葡萄", "水果", new int[]{3, 4}, 25, 45, 10, 35, 150, "medium"},
            {"苹果", "水果", new int[]{3, 4}, 30, 50, 5, 25, 180, "medium"},
            {"生姜", "蔬菜", new int[]{4, 5}, 20, 40, 18, 35, 120, "medium"},
            {"葱", "蔬菜", new int[]{3, 9}, 25, 50, 5, 30, 90, "low"},
            {"洋葱", "蔬菜", new int[]{9, 10}, 25, 45, 5, 25, 150, "medium"},
            {"甘蔗", "经济", new int[]{2, 3, 4}, 18, 30, 20, 35, 300, "medium"},
            {"绿豆", "粮食", new int[]{5, 6}, 25, 45, 18, 35, 80, "low"},
            {"芝麻", "经济", new int[]{5, 6}, 25, 40, 18, 35, 90, "low"},
            {"高粱", "粮食", new int[]{4, 5}, 25, 50, 15, 35, 110, "low"},
            {"谷子", "粮食", new int[]{4, 5}, 30, 50, 10, 30, 100, "low"},
            {"菠菜", "蔬菜", new int[]{3, 9, 10}, 25, 50, 5, 25, 50, "low"},
            {"芹菜", "蔬菜", new int[]{3, 8, 9}, 25, 50, 10, 25, 90, "low"},
    };

    private static volatile Map<String, String> MARKET_CACHE;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        try {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        int month = (int) HttpSupport.parseDouble(q.get("month"), LocalDate.now().getMonthValue());
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.0);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.0);
        String location = HttpSupport.trimToNull(q.get("location"));

        if (month < 1 || month > 12) month = LocalDate.now().getMonthValue();

        Map<String, String> market = marketNotes();
        String termLabel = MONTH_TERMS[month - 1];

        List<Scored> results = new ArrayList<>();
        for (Object[] row : CROP_DB) {
            String name = (String) row[0];
            String category = (String) row[1];
            int[] plantMonths = (int[]) row[2];
            double latMin = ((Number) row[3]).doubleValue();
            double latMax = ((Number) row[4]).doubleValue();
            double tempMin = ((Number) row[5]).doubleValue();
            double tempMax = ((Number) row[6]).doubleValue();
            int growthDays = (int) row[7];
            String demand = (String) row[8];

            double seasonScore = seasonScore(plantMonths, month);
            double climateScore = climateScore(latMin, latMax, tempMin, tempMax, lat, month);
            double marketScore = marketScore(demand);
            double total = seasonScore * 0.4 + climateScore * 0.35 + marketScore * 0.25;

            if (total < 0.15) continue;

            List<String> reasons = new ArrayList<>();
            if (seasonScore >= 0.5) reasons.add(seasonReason(plantMonths, month, termLabel));
            if (climateScore >= 0.4) reasons.add(climateReason(latMin, latMax, lat, location));
            if (marketScore >= 0.4) reasons.add(marketReason(name, demand, market));

            if (reasons.isEmpty()) continue;

            results.add(new Scored(name, category, total, seasonScore, climateScore, marketScore,
                    growthDays, demand, reasons, suggestSowDate(plantMonths, month)));
        }

        results.sort((a, b) -> Double.compare(b.total, a.total));

        List<String> items = new ArrayList<>();
        int topN = Math.min(10, results.size());
        for (int i = 0; i < topN; i++) {
            items.add(resultJson(results.get(i)));
        }

        HttpSupport.write(ex, 200, Json.obj(
                "code", Json.str("0"),
                "month", String.valueOf(month),
                "term", Json.str(termLabel),
                "lat", Json.num(lat),
                "lon", Json.num(lon),
                "location", Json.str(location == null ? "" : location),
                "total", String.valueOf(items.size()),
                "recommendations", Json.arr(items)));
        } catch (Throwable t) {
            // 兜底：出异常要回 JSON，不能悄悄掐断连接 —— 否则前端只看到 502，什么都查不出来
            t.printStackTrace();
            HttpSupport.write(ex, 500, Json.obj("code", Json.str("500"),
                    "message", Json.str("推荐服务异常：" + t.getClass().getSimpleName())));
        }
    }

    private record Scored(String name, String category, double total, double season, double climate,
                          double market, int growthDays, String demand, List<String> reasons, String sowDate) {
    }

    private static double seasonScore(int[] plantMonths, int month) {
        for (int m : plantMonths) {
            if (m == month) return 1.0;
            if (Math.abs(m - month) == 1) return 0.6;
        }
        int minDist = 12;
        for (int m : plantMonths) minDist = Math.min(minDist, Math.min(Math.abs(m - month), 12 - Math.abs(m - month)));
        if (minDist == 2) return 0.3;
        return 0.0;
    }

    private static double climateScore(double latMin, double latMax, double tempMin, double tempMax,
                                        double lat, int month) {
        double latScore;
        double absLat = Math.abs(lat);
        if (absLat >= latMin && absLat <= latMax) {
            latScore = 1.0;
        } else {
            double dist = absLat < latMin ? latMin - absLat : absLat - latMax;
            latScore = Math.max(0, 1.0 - dist / 15.0);
        }
        double estTemp = estimateTemp(lat, month);
        double tempScore;
        if (estTemp >= tempMin && estTemp <= tempMax) {
            tempScore = 1.0;
        } else {
            double dist = estTemp < tempMin ? tempMin - estTemp : estTemp - tempMax;
            tempScore = Math.max(0, 1.0 - dist / 15.0);
        }
        return latScore * 0.5 + tempScore * 0.5;
    }

    private static double marketScore(String demand) {
        return switch (demand) {
            case "high" -> 1.0;
            case "medium" -> 0.6;
            default -> 0.3;
        };
    }

    /** 粗略估计月均温：纬度越高越冷，1月最冷7月最热 */
    private static double estimateTemp(double lat, int month) {
        double absLat = Math.abs(lat);
        double base = 30 - absLat * 0.7;
        double seasonal = 15 * Math.cos((month - 7) * Math.PI / 6);
        if (absLat > 35) seasonal *= 1.2;
        return base + seasonal;
    }

    private static String seasonReason(int[] plantMonths, int month, String termLabel) {
        StringBuilder sb = new StringBuilder("节气适宜：");
        sb.append(termLabel).append("前后正值").append(monthNames(plantMonths)).append("播种窗口");
        return sb.toString();
    }

    private static String climateReason(double latMin, double latMax, double lat, String location) {
        String loc = location != null ? location : "当地";
        return String.format("气候匹配：%s纬度%.1f°处于适宜区间（%.0f°~%.0f°），温度满足生长需求", loc, Math.abs(lat), latMin, latMax);
    }

    private static String marketReason(String name, String demand, Map<String, String> market) {
        String label = switch (demand) {
            case "high" -> "需求旺盛";
            case "medium" -> "需求平稳";
            default -> "需求一般";
        };
        String note = market.get(name);
        if (note != null && !note.isBlank()) {
            return "市场需求：" + label + "，" + note;
        }
        return "市场需求：" + label;
    }

    private static String monthNames(int[] months) {
        String[] names = {"1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < months.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(names[months[i] - 1]);
        }
        return sb.toString();
    }

    private static String suggestSowDate(int[] plantMonths, int currentMonth) {
        int target = currentMonth;
        for (int m : plantMonths) if (m == currentMonth) { target = m; break; }
        if (target != currentMonth) {
            for (int m : plantMonths) if (m > currentMonth) { target = m; break; }
        }
        int year = LocalDate.now().getYear();
        if (target < currentMonth) year++;
        int day = target <= 3 || target >= 10 ? 20 : 15;
        return String.format("%d-%02d-%02d", year, target, day);
    }

    private String resultJson(Scored s) {
        List<String> reasonJson = new ArrayList<>();
        for (String r : s.reasons) reasonJson.add(Json.str(r));
        return Json.obj(
                "name", Json.str(s.name),
                "category", Json.str(s.category),
                "score", Json.num(s.total),
                "seasonScore", Json.num(s.season),
                "climateScore", Json.num(s.climate),
                "marketScore", Json.num(s.market),
                "growthDays", String.valueOf(s.growthDays),
                "demand", Json.str(s.demand),
                "sowDate", Json.str(s.sowDate),
                "reasons", Json.arr(reasonJson));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> marketNotes() {
        Map<String, String> cached = MARKET_CACHE;
        if (cached != null) return cached;
        Map<String, String> map = new HashMap<>();
        try {
            Path file = Path.of(MARKET_FILE);
            if (Files.exists(file)) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Object root = JsonParser.parse(text);
                if (root instanceof Map<?, ?> m && m.get("crops") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> cm) {
                            String name = String.valueOf(cm.get("name"));
                            Object noteVal = cm.get("note");
                            String note = noteVal == null ? "" : String.valueOf(noteVal);
                            map.put(name, note);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (map.isEmpty()) {
            map.put("大蒜", "出口需求旺盛，金乡产区价格稳中有升");
            map.put("玉米", "饲料需求大，价格受养殖周期影响");
            map.put("水稻", "主粮保底收购，价格稳定");
            map.put("大豆", "国产缺口大，政策扶持种植");
            map.put("番茄", "设施番茄全年需求，价格波动大");
            map.put("辣椒", "餐饮需求刚性，干辣椒耐储");
            map.put("马铃薯", "第四大主粮，加工需求增长");
            map.put("西瓜", "夏季时令水果，集中上市期价低");
            map.put("草莓", "冬春高端水果，设施种植效益好");
        }
        MARKET_CACHE = map;
        return map;
    }
}