package com.cropcalendar.api;

import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/recommend —— 种植推荐（T15）
 *
 * GET /api/recommend?month=4&lat=35.07&lon=116.31
 *
 * 结合「月份 + 纬度（气候带）+ 市场需求」综合打分：
 *   - 月份匹配：作物适宜播种月里命中当前月，是最高优先级
 *   - 纬度匹配：作物适宜纬度带覆盖用户所在纬度
 *   - 市场需求：market 字段（0~100）作为热度权重
 * 输出 Top8 推荐，每条带推荐理由。全部基于本地规则库，零外部依赖、免 key。
 */
public class RecommendHandler implements HttpHandler {

    private static final String RULES_PATH = "../data/market/recommend-rules.json";
    private static final int TOP_N = 8;

    private static volatile List<Map<String, Object>> RULES;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        int month = (int) HttpSupport.parseDouble(q.get("month"), 0);
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);

        // 地点来源必须如实回传：用户填了"山东济宁"却没解析出坐标时，
        // 前端要能提示"按默认地点计算"，而不是让用户以为推荐是济宁的。
        String locationName = HttpSupport.trimToNull(q.get("location"));
        String locationSource = HttpSupport.trimToNull(q.get("locationSource"));
        if (locationSource == null) locationSource = "default";
        String plotName = HttpSupport.trimToNull(q.get("plotName"));
        Map<String, Double> plotCrops = parsePlotCrops(q.get("plotCrops"));

        if (month < 1 || month > 12) {
            HttpSupport.write(ex, 400, Json.obj(
                    "code", "40001",
                    "message", Json.str("month 需在 1~12 之间"),
                    "data", "null"));
            return;
        }

        List<Map<String, Object>> rules;
        try {
            rules = rules();
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj(
                    "code", "50001",
                    "message", Json.str("推荐规则库读取失败：" + e.getMessage()),
                    "data", "null"));
            return;
        }

        List<Scored> scored = new ArrayList<>();
        for (Map<String, Object> r : rules) {
            Scored s = score(r, month, lat, plotCrops);
            if (s.score > 0) scored.add(s);
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<String> items = new ArrayList<>();
        int n = Math.min(TOP_N, scored.size());
        for (int i = 0; i < n; i++) {
            Scored s = scored.get(i);
            Map<String, Object> r = s.rule;
            items.add(Json.obj(
                    "rank", String.valueOf(i + 1),
                    "name", Json.str(str(r.get("name"))),
                    "cropId", Json.str(str(r.get("cropId"))),
                    "score", Json.num(s.score),
                    "market", Json.str(str(r.get("market"))),
                    "sowMonths", Json.arr(strList(r.get("sowMonths"))),
                    "reason", Json.str(str(r.get("reason"))),
                    "monthMatch", s.monthMatch ? "true" : "false",
                    "climateMatch", s.climateMatch ? "true" : "false",
                    "plotMatch", s.plotScore > 0 ? "true" : "false",
                    // 推荐依据拆开给，前端可展开看每一条为什么加分
                    "reasons", Json.arr(s.reasons)));
        }

        HttpSupport.write(ex, 200, Json.obj(
                "month", String.valueOf(month),
                "lat", Json.num(lat),
                "lon", Json.num(lon),
                "total", String.valueOf(items.size()),
                "location", Json.obj(
                        "name", Json.str(locationName == null ? "默认地点" : locationName),
                        "lat", Json.num(lat),
                        "lon", Json.num(lon),
                        "source", Json.str(locationSource),
                        "resolved", "default".equals(locationSource) ? "false" : "true"),
                "plot", Json.obj(
                        "name", Json.str(plotName),
                        "cropCount", String.valueOf(plotCrops.size()),
                        "used", plotCrops.isEmpty() ? "false" : "true"),
                "recommendations", Json.arr(items)));
    }

    private record Scored(Map<String, Object> rule, double score, boolean monthMatch,
                          boolean climateMatch, double plotScore, List<String> reasons) {
    }

    /** 解析前端传来的地块适宜作物："大蒜:86,小麦:74"。格式不对就当作没传。 */
    private static Map<String, Double> parsePlotCrops(String raw) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String part : raw.split(",")) {
            int idx = part.lastIndexOf(':');
            if (idx <= 0) continue;
            String name = part.substring(0, idx).trim();
            try {
                double sc = Double.parseDouble(part.substring(idx + 1).trim());
                if (!name.isEmpty()) map.put(name, sc);
            } catch (NumberFormatException ignored) {
                // 单个条目格式不对就跳过，不影响其它作物
            }
        }
        return map;
    }

    private Scored score(Map<String, Object> rule, int month, double lat, Map<String, Double> plotCrops) {
        boolean monthMatch = false;
        double s = 0;
        List<String> reasons = new ArrayList<>();

        // 月份匹配：最高权重，命中 +60
        if (rule.get("sowMonths") instanceof List<?> months) {
            for (Object o : months) {
                try {
                    if ((int) Double.parseDouble(str(o)) == month) { monthMatch = true; break; }
                } catch (NumberFormatException ignore) { }
            }
        }
        if (monthMatch) {
            s += 60;
            reasons.add(reason("solar", "节气适播", month + " 月在该作物适播期内（"
                    + joinMonths(rule.get("sowMonths")) + "）", 60));
        } else {
            reasons.add(reason("solar", "非适播期", month + " 月不在适播期（"
                    + joinMonths(rule.get("sowMonths")) + "），仅作参考", 0));
        }

        // 纬度（气候带）匹配：命中 +25
        boolean climateMatch = false;
        if (rule.get("latRange") instanceof List<?> lr && lr.size() >= 2) {
            try {
                double lo = Double.parseDouble(str(lr.get(0)));
                double hi = Double.parseDouble(str(lr.get(1)));
                if (lat >= lo && lat <= hi) {
                    climateMatch = true;
                    s += 25;
                    reasons.add(reason("climate", "气候带匹配",
                            "北纬 " + String.format("%.2f", lat) + "° 落在适宜纬度带 "
                                    + String.format("%.0f", lo) + "°–" + String.format("%.0f", hi) + "°", 25));
                } else {
                    reasons.add(reason("climate", "气候带不符",
                            "北纬 " + String.format("%.2f", lat) + "° 不在适宜纬度带 "
                                    + String.format("%.0f", lo) + "°–" + String.format("%.0f", hi) + "°，需确认品种适应性", 0));
                }
            } catch (NumberFormatException ignore) { }
        }

        // 市场需求热度（0~100）折算，最高 +15
        double market = 0;
        try { market = Double.parseDouble(str(rule.get("market"))); } catch (NumberFormatException ignore) { }
        double marketPoints = market / 100.0 * 15;
        s += marketPoints;
        if (market > 0) {
            reasons.add(reason("market", "市场需求",
                    "市场热度 " + String.format("%.0f", market) + "/100（经验评级，非实时批发价）",
                    Math.round(marketPoints * 10) / 10.0));
        }

        // 地块匹配：命中该地块适宜作物 Top5 再加分，让推荐和"这块地"对得上
        double plotScore = 0;
        String name = str(rule.get("name"));
        if (name != null && plotCrops.containsKey(name)) {
            plotScore = plotCrops.get(name);
            double bonus = Math.min(15, plotScore / 100.0 * 15);
            s += bonus;
            reasons.add(reason("plot", "地块匹配",
                    "与你所选地块的适宜作物一致，该地块评分为 " + String.format("%.0f", plotScore) + " 分",
                    Math.round(bonus * 10) / 10.0));
        } else if (!plotCrops.isEmpty()) {
            reasons.add(reason("plot", "未进入地块推荐",
                    "该作物未进入所选地块的适宜作物 Top5，实际种植需先改善地块条件", 0));
        }

        // 月份不匹配时，只保留"接近月份"的微弱分数，避免全淹掉
        if (!monthMatch) s = Math.min(s, 20);
        // 四项加分理论上可到 115，但界面上分数条是按 100 画的，
        // 超过 100 会显示成"111 分"让人困惑，这里封顶到 100
        s = Math.min(100, s);

        return new Scored(rule, Math.round(s * 10) / 10.0, monthMatch, climateMatch,
                plotScore, reasons);
    }

    private static String reason(String key, String label, String detail, double points) {
        return Json.obj(
                "key", Json.str(key),
                "label", Json.str(label),
                "detail", Json.str(detail),
                "points", Json.num(points));
    }

    private static String joinMonths(Object months) {
        if (!(months instanceof List<?> l) || l.isEmpty()) return "未限定";
        StringBuilder sb = new StringBuilder();
        for (Object o : l) {
            if (sb.length() > 0) sb.append("、");
            // 规则里的月份是数字，直接拼会出现"9.0 月"，转成整数再展示
            String m = str(o);
            if (m != null && m.endsWith(".0")) m = m.substring(0, m.length() - 2);
            sb.append(m).append(" 月");
        }
        return sb.toString();
    }

    private List<String> strList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) out.add(Json.str(str(o)));
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rules() throws IOException {
        List<Map<String, Object>> cached = RULES;
        if (cached != null) return cached;
        synchronized (RecommendHandler.class) {
            if (RULES != null) return RULES;
            String text = Files.readString(Path.of(RULES_PATH), StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> m) || !(m.get("crops") instanceof List<?> list)) {
                throw new IOException("推荐规则库结构不对，应为 {crops:[...]}");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> cm) out.add((Map<String, Object>) cm);
            }
            RULES = out;
            System.out.println("[recommend] 推荐规则库已加载：" + out.size() + " 条");
            return out;
        }
    }
}
