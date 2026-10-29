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
            Scored s = score(r, month, lat);
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
                    "climateMatch", s.climateMatch ? "true" : "false"));
        }

        HttpSupport.write(ex, 200, Json.obj(
                "month", String.valueOf(month),
                "lat", Json.num(lat),
                "total", String.valueOf(items.size()),
                "recommendations", Json.arr(items)));
    }

    private record Scored(Map<String, Object> rule, double score, boolean monthMatch, boolean climateMatch) {
    }

    private Scored score(Map<String, Object> rule, int month, double lat) {
        boolean monthMatch = false;
        double s = 0;

        // 月份匹配：最高权重，命中 +60
        if (rule.get("sowMonths") instanceof List<?> months) {
            for (Object o : months) {
                try {
                    if ((int) Double.parseDouble(str(o)) == month) { monthMatch = true; break; }
                } catch (NumberFormatException ignore) { }
            }
        }
        if (monthMatch) s += 60;

        // 纬度（气候带）匹配：命中 +25
        boolean climateMatch = false;
        if (rule.get("latRange") instanceof List<?> lr && lr.size() >= 2) {
            try {
                double lo = Double.parseDouble(str(lr.get(0)));
                double hi = Double.parseDouble(str(lr.get(1)));
                if (lat >= lo && lat <= hi) { climateMatch = true; s += 25; }
            } catch (NumberFormatException ignore) { }
        }

        // 市场需求热度（0~100）折算，最高 +15
        double market = 0;
        try { market = Double.parseDouble(str(rule.get("market"))); } catch (NumberFormatException ignore) { }
        s += market / 100.0 * 15;

        // 月份不匹配时，只保留"接近月份"的微弱分数，避免全淹掉
        if (!monthMatch) s = Math.min(s, 20);

        return new Scored(rule, Math.round(s * 10) / 10.0, monthMatch, climateMatch);
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
