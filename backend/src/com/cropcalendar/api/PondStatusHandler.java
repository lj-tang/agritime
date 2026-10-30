package com.cropcalendar.api;

import com.cropcalendar.orchestration.CacheStore;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * GET|POST /api/pond-status —— 鱼塘水质状态与投喂建议（V5 T09 深化）
 *
 * 原来的渔业只有一张"放苗 → 起捕"的日历，跟塘里现在什么样毫无关系，
 * 用户看完还是不知道今天该喂多少、要不要开增氧机。这个接口补上这一层：
 *
 * 1. 水温：气温推算（3 天滑动均值 + 水体热惯性系数），明确标注为模型估算；
 * 2. 溶解氧：按水温算饱和溶氧，再用天气（阴雨/闷热/大风）折减，
 *    并给出凌晨最低值 —— 泛塘恰恰发生在凌晨，只报日均会害人；
 * 3. 投喂量：存塘量 × 当前规格 × 水温决定的日投喂率，算出今天喂多少公斤；
 * 4. 风险：缺氧、高温、低温、pH 异常、氨氮超标，每条都给可执行动作。
 *
 * 用户实测的 pH / 氨氮 / 溶氧 优先于模型值，并在返回里标明来源，
 * 不让"估算"冒充"实测"。
 */
public class PondStatusHandler implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final CacheStore CACHE = new CacheStore();
    private static final int TTL_SECONDS = 1800;

    /** 常见养殖鱼类的适温与投喂参数。查不到就用草鱼这一档的保守值。 */
    private static final String[][] SPECIES = {
            {"草鱼", "grass_carp", "18", "30", "3.5"},
            {"鲢鱼", "silver_carp", "15", "32", "3.0"},
            {"鳙鱼", "bighead_carp", "15", "32", "3.0"},
            {"鲤鱼", "common_carp", "15", "30", "3.5"},
            {"鲫鱼", "crucian_carp", "12", "28", "3.0"},
            {"青鱼", "black_carp", "18", "30", "3.5"},
            {"罗非鱼", "tilapia", "20", "34", "4.0"},
            {"南美白对虾", "shrimp", "22", "32", "5.0"},
            {"河蟹", "crab", "15", "30", "3.0"}
    };

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            try {
                Object o = JsonParser.parse(HttpSupport.body(ex));
                if (o instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (!q.containsKey(String.valueOf(e.getKey())) && e.getValue() != null) {
                            q.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                        }
                    }
                }
            } catch (Exception ignored) {
                // 解析失败就只用 query 参数，不报错
            }
        }

        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);
        String species = HttpSupport.trimToNull(q.get("species"));
        double areaMu = HttpSupport.parseDouble(q.get("areaMu"), 5);
        double density = HttpSupport.parseDouble(q.get("densityPerMu"), 1000);
        double avgWeightG = HttpSupport.parseDouble(q.get("avgWeightG"), 250);

        // 用户实测值：给了就用实测，并在返回里标 source=measured
        Double ph = dblOrNull(q.get("ph"));
        Double ammonia = dblOrNull(q.get("ammonia"));
        Double measuredDo = dblOrNull(q.get("dissolvedOxygen"));
        Double measuredTemp = dblOrNull(q.get("waterTemp"));

        String key = "pond:" + Math.round(lat * 100) + ":" + Math.round(lon * 100) + ":" + species
                + ":" + Math.round(areaMu * 10) + ":" + Math.round(density) + ":" + Math.round(avgWeightG)
                + ":" + LocalDate.now();
        String cached = CACHE.get(key, String.class);
        if (cached != null) {
            HttpSupport.write(ex, 200, cached);
            return;
        }

        try {
            Weather w = CompletableFuture.supplyAsync(() -> fetchWeather(lat, lon))
                    .completeOnTimeout(new Weather(), 8, TimeUnit.SECONDS)
                    .get(9, TimeUnit.SECONDS);
            String out = build(species, areaMu, density, avgWeightG, w, ph, ammonia, measuredDo, measuredTemp);
            CACHE.put(key, out, TTL_SECONDS);
            HttpSupport.write(ex, 200, out);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("error", Json.str("鱼塘状态计算失败：" + e.getMessage())));
        }
    }

    private String build(String species, double areaMu, double density, double avgWeightG,
                         Weather w, Double ph, Double ammonia, Double measuredDo, Double measuredTemp) {
        String[] sp = speciesOf(species);
        String name = sp[0];
        double tMin = Double.parseDouble(sp[2]);
        double tMax = Double.parseDouble(sp[3]);
        double baseRate = Double.parseDouble(sp[4]);

        Double waterTemp = measuredTemp != null ? measuredTemp
                : (w.tempRecent3d == null ? null : round1(0.85 * w.tempRecent3d + 1.0));
        boolean tempMeasured = measuredTemp != null;

        // 饱和溶氧（mg/L，淡水、1 标准大气压）：经验二次式，20℃ 约 9.1，30℃ 约 7.6
        Double doSat = waterTemp == null ? null : round2(14.6 - 0.41 * waterTemp + 0.008 * waterTemp * waterTemp);

        // 实际溶氧：晴天有风接近饱和，连阴雨和闷热天要大打折扣
        double satRatio = 0.85;
        String satReason = "晴好、有风，藻类产氧与空气溶入均较好";
        if (w.rain3d != null && w.rain3d > 20 && w.windMax != null && w.windMax < 3) {
            satRatio = 0.50;
            satReason = "连日阴雨且风力小，光合作用弱、气压偏低，极易缺氧";
        } else if (w.rain3d != null && w.rain3d > 5) {
            satRatio = 0.70;
            satReason = "近期有降雨，光照不足，藻类产氧下降";
        } else if (w.cloud != null && w.cloud > 80 && w.windMax != null && w.windMax < 3) {
            satRatio = 0.62;
            satReason = "阴云闷热、几乎无风，是最容易泛塘的天气";
        } else if (w.tempMax7d != null && w.tempMax7d > 33) {
            satRatio = 0.68;
            satReason = "持续高温，水温高则溶氧饱和度下降，且鱼耗氧增加";
        }
        Double doEst = measuredDo != null ? measuredDo : (doSat == null ? null : round2(doSat * satRatio));

        double biomassKg = areaMu * density * avgWeightG / 1000.0;

        // 水温决定的摄食强度：适温区间内按基准率，越界快速下降
        double feedRate = 0;
        String feedReason;
        if (waterTemp == null) {
            feedRate = baseRate;
            feedReason = "水温未知，按常规投喂率 " + fmt(baseRate) + "% 估算";
        } else if (waterTemp < tMin - 3) {
            feedRate = 0;
            feedReason = "水温 " + fmt(waterTemp) + "℃ 低于 " + fmt(tMin) + "℃ 太多，鱼基本停食，应停料";
        } else if (waterTemp < tMin) {
            feedRate = baseRate * 0.4;
            feedReason = "水温 " + fmt(waterTemp) + "℃ 略低于适宜下限 " + fmt(tMin) + "℃，减半投喂";
        } else if (waterTemp > tMax + 2) {
            feedRate = baseRate * 0.5;
            feedReason = "水温 " + fmt(waterTemp) + "℃ 超过适宜上限 " + fmt(tMax) + "℃，高温减料并加强增氧";
        } else if (waterTemp > tMax) {
            feedRate = baseRate * 0.8;
            feedReason = "水温 " + fmt(waterTemp) + "℃ 略高于适宜上限，适当减料";
        } else {
            double mid = (tMin + tMax) / 2.0;
            double dev = Math.abs(waterTemp - mid) / Math.max(1, (tMax - tMin) / 2.0);
            feedRate = baseRate * (1.0 - 0.15 * dev);
            feedReason = "水温 " + fmt(waterTemp) + "℃ 处于适宜区间（" + fmt(tMin) + "~" + fmt(tMax) + "℃）";
        }
        Double doDawn = doEst == null ? null : round2(Math.max(0, doEst - 1.8));
        if (doDawn != null && doDawn < 2.0) {
            // 泛塘临界：这时候还在给投喂量是自相矛盾的，必须直接停料
            feedRate = 0;
            feedReason += "；凌晨溶氧将跌破泛塘临界值，今日停料保鱼";
        } else if (doEst != null && doEst < 4) {
            feedRate *= 0.5;
            feedReason += "；溶氧偏低，必须减料（缺氧时摄食会加重耗氧）";
        }

        double feedKgDay = round2(biomassKg * feedRate / 100.0);

        List<String> risks = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (doEst != null) {
            if (doDawn != null && doDawn < 2.0) {
                risks.add(risk("hypoxia-critical", "严重缺氧", "凌晨溶氧估算 " + fmt(doDawn)
                        + " mg/L，已低于 2 mg/L 的泛塘临界值", "high"));
                actions.add("今晚 22:00 起到次日 8:00 全程开增氧机，并准备粒粒氧等化学增氧剂应急");
                actions.add("明晨停料，先保命再谈长肉；天亮后加注新水 20~30cm");
            } else if (doDawn != null && doDawn < 3.0) {
                risks.add(risk("hypoxia-warn", "缺氧预警", "凌晨溶氧估算 " + fmt(doDawn)
                        + " mg/L，接近泛塘临界", "high"));
                actions.add("凌晨 2:00~8:00 提前开增氧机，不要等鱼浮头才开");
                actions.add("次日减料 30%，减少残饵耗氧");
            } else if (doEst < 4.5) {
                risks.add(risk("hypoxia-mid", "溶氧偏低", "日均溶氧估算 " + fmt(doEst)
                        + " mg/L，低于 4.5 mg/L 的适宜下限", "mid"));
                actions.add("中午 12:00~14:00 开增氧机 2 小时，打破水体分层");
            }
        }
        if (waterTemp != null) {
            if (waterTemp > tMax + 2) {
                risks.add(risk("heat", "水温过高", "水温 " + fmt(waterTemp) + "℃ 高于 " + name
                        + " 适宜上限 " + fmt(tMax) + "℃", "high"));
                actions.add("加深水位至 1.8m 以上、搭遮阳网，避免上下层温差过大");
            } else if (waterTemp < tMin - 2) {
                risks.add(risk("cold", "水温过低", "水温 " + fmt(waterTemp) + "℃ 低于 " + name
                        + " 适宜下限 " + fmt(tMin) + "℃", "mid"));
                actions.add("减少换水量、适当提高水位保温，待回暖后再恢复投喂");
            }
        }
        if (ph != null) {
            if (ph < 6.5) {
                risks.add(risk("ph-low", "pH 偏低", "实测 pH " + fmt(ph) + "，水体偏酸，会抑制鱼类生长", "high"));
                actions.add("每亩用生石灰 10~15kg 全池泼洒调节，7 天后复测");
            } else if (ph > 9.0) {
                risks.add(risk("ph-high", "pH 偏高", "实测 pH " + fmt(ph) + "，偏高易产生氨毒", "high"));
                actions.add("换水 1/3 并停用生石灰，藻类过旺时可适当杀灭部分藻类");
            }
        }
        if (ammonia != null) {
            if (ammonia > 0.6) {
                risks.add(risk("nh3-high", "氨氮超标", "实测氨氮 " + fmt(ammonia)
                        + " mg/L，超过 0.6 mg/L 的危险线", "high"));
                actions.add("立即换水 1/3、停料 2 天，泼洒沸石粉或光合细菌降解氨氮");
            } else if (ammonia > 0.2) {
                risks.add(risk("nh3-mid", "氨氮偏高", "实测氨氮 " + fmt(ammonia)
                        + " mg/L，超过 0.2 mg/L 的安全线", "mid"));
                actions.add("减少投喂 20%、加强换水，定期补充芽孢杆菌等微生态制剂");
            }
        }
        if (risks.isEmpty()) {
            risks.add(risk("ok", "水质指标正常", "水温、溶氧与各项实测指标均在安全范围", "low"));
            actions.add("维持当前投喂与换水节奏，每周固定测一次 pH 与氨氮");
        }

        List<String> rj = new ArrayList<>();
        for (String r : risks) rj.add(Json.raw(r));
        List<String> aj = new ArrayList<>();
        for (String a : actions) aj.add(Json.str(a));

        return Json.obj(
                "species", Json.str(name),
                "speciesId", Json.str(sp[1]),
                "areaMu", Json.num(areaMu),
                "densityPerMu", Json.num(density),
                "avgWeightG", Json.num(avgWeightG),
                "biomassKg", Json.num(Math.round(biomassKg)),
                "water", Json.raw(Json.obj(
                        "tempC", waterTemp == null ? "null" : Json.num(waterTemp),
                        "tempSource", Json.str(tempMeasured ? "实测" : "气温模型估算（3 天滑动均值 × 0.85 + 1.0）"),
                        "dissolvedOxygen", doEst == null ? "null" : Json.num(doEst),
                        "dissolvedOxygenSource", Json.str(measuredDo != null ? "实测" : "模型估算（饱和溶氧 × " + Math.round(satRatio * 100) + "%）"),
                        "dissolvedOxygenDawn", doDawn == null ? "null" : Json.num(doDawn),
                        "saturationRatio", Json.num(Math.round(satRatio * 100) / 100.0),
                        "saturationReason", Json.str(satReason),
                        "ph", ph == null ? "null" : Json.num(ph),
                        "ammonia", ammonia == null ? "null" : Json.num(ammonia))),
                "feeding", Json.raw(Json.obj(
                        "ratePercent", Json.num(Math.round(feedRate * 100) / 100.0),
                        "kgPerDay", Json.num(feedKgDay),
                        "timesPerDay", Json.num(waterTemp != null && waterTemp > 25 ? 3 : 2),
                        "basis", Json.str(feedReason),
                        "note", Json.str("以 1 小时内吃完为准，剩饵及时捞出；闷热、浮头、服药期间停料"))),
                "weather", Json.raw(Json.obj(
                        "airTempC", w.temp == null ? "null" : Json.num(w.temp),
                        "tempRecent3dC", w.tempRecent3d == null ? "null" : Json.num(w.tempRecent3d),
                        "rain3dMm", w.rain3d == null ? "null" : Json.num(w.rain3d),
                        "windMaxMs", w.windMax == null ? "null" : Json.num(w.windMax),
                        "available", w.temp == null && w.tempRecent3d == null ? "false" : "true")),
                "risks", Json.arr(rj),
                "actions", Json.arr(aj),
                "summary", Json.str(summaryOf(name, waterTemp, doEst, doDawn, feedKgDay, feedRate, biomassKg, tempMeasured)),
                "dataSources", Json.obj(
                        "weather", Json.str("Open-Meteo 预报（含过去 3 天实况）"),
                        "species", Json.str("渔业品种库适温区间与投喂率"),
                        "notice", Json.str("水温与溶氧为模型推算，非探头实测；接入实测设备后可覆盖")));
    }

    private static String risk(String code, String label, String detail, String severity) {
        return Json.obj("code", Json.str(code), "label", Json.str(label),
                "detail", Json.str(detail), "severity", Json.str(severity));
    }

    private String summaryOf(String name, Double t, Double doEst, Double doDawn,
                             double feedKg, double rate, double biomass, boolean tempMeasured) {
        StringBuilder sb = new StringBuilder();
        sb.append("塘内存 ").append(name).append(" 约 ").append(Math.round(biomass)).append(" kg");
        if (t != null) sb.append("，水温 ").append(fmt(t)).append("℃").append(tempMeasured ? "（实测）" : "（估算）");
        if (doEst != null) sb.append("，溶氧 ").append(fmt(doEst)).append(" mg/L");
        if (doDawn != null) sb.append("，凌晨最低约 ").append(fmt(doDawn)).append(" mg/L");
        sb.append("。今日建议投喂 ").append(fmt(feedKg)).append(" kg（存塘量的 ").append(fmt(rate)).append("%）");
        return sb.toString();
    }

    // ------------------------------------------------------------------ 上游

    private static final class Weather {
        Double temp, tempRecent3d, rain3d, windMax, cloud, tempMax7d;
    }

    private Weather fetchWeather(double lat, double lon) {
        Weather w = new Weather();
        String url = String.format("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,cloud_cover,wind_speed_10m"
                        + "&daily=temperature_2m_mean,temperature_2m_max,precipitation_sum,wind_speed_10m_max"
                        + "&past_days=3&forecast_days=3&timezone=auto",
                lat, lon);
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return w;
            String b = resp.body();
            int ci = b.indexOf("\"current\":");
            if (ci >= 0) {
                String cur = b.substring(ci);
                w.temp = dbl(cur, "\"temperature_2m\":");
                w.cloud = dbl(cur, "\"cloud_cover\":");
                Double wind = dbl(cur, "\"wind_speed_10m\":");
                if (wind != null) w.windMax = wind;
            }
            int di = b.indexOf("\"daily\":");
            if (di >= 0) {
                String seg = b.substring(di);
                List<Double> means = arr(seg, "temperature_2m_mean");
                List<Double> maxs = arr(seg, "temperature_2m_max");
                List<Double> rains = arr(seg, "precipitation_sum");
                List<Double> winds = arr(seg, "wind_speed_10m_max");
                // 过去 3 天起：past_days=3 时 daily 的前 3 项是昨天及以前
                if (!means.isEmpty()) {
                    double s = 0;
                    int n = 0;
                    for (int i = 0; i < means.size() && i < 3; i++) {
                        if (!Double.isNaN(means.get(i))) { s += means.get(i); n++; }
                    }
                    if (n > 0) w.tempRecent3d = round1(s / n);
                }
                if (!rains.isEmpty()) {
                    double s = 0;
                    for (int i = 0; i < rains.size() && i < 3; i++) {
                        if (!Double.isNaN(rains.get(i))) s += rains.get(i);
                    }
                    w.rain3d = Math.round(s * 10) / 10.0;
                }
                if (!maxs.isEmpty()) {
                    double mx = -99;
                    for (double v : maxs) if (!Double.isNaN(v)) mx = Math.max(mx, v);
                    if (mx > -90) w.tempMax7d = mx;
                }
                if (!winds.isEmpty()) {
                    double mx = 0;
                    for (double v : winds) if (!Double.isNaN(v)) mx = Math.max(mx, v);
                    if (mx > 0) w.windMax = Math.round(mx * 10) / 10.0;
                }
            }
        } catch (Exception e) {
            return w;
        }
        return w;
    }

    // ------------------------------------------------------------------ 工具

    private static String[] speciesOf(String name) {
        if (name != null) {
            for (String[] s : SPECIES) {
                if (s[0].equals(name) || s[1].equalsIgnoreCase(name) || name.contains(s[0])) return s;
            }
        }
        return SPECIES[0];
    }

    private static List<Double> arr(String seg, String key) {
        List<Double> out = new ArrayList<>();
        int pi = seg.indexOf("\"" + key + "\":[");
        if (pi < 0) return out;
        int open = seg.indexOf('[', pi);
        int close = seg.indexOf(']', open);
        if (open < 0 || close < open) return out;
        for (String s : seg.substring(open + 1, close).split(",")) {
            try {
                out.add(Double.parseDouble(s.trim()));
            } catch (Exception e) {
                out.add(Double.NaN);
            }
        }
        return out;
    }

    private static Double dbl(String seg, String key) {
        int i = seg.indexOf(key);
        if (i < 0) return null;
        int s = i + key.length();
        int e = s;
        while (e < seg.length() && (Character.isDigit(seg.charAt(e)) || seg.charAt(e) == '-'
                || seg.charAt(e) == '.' || seg.charAt(e) == 'e' || seg.charAt(e) == 'E')) e++;
        if (e == s) return null;
        try {
            return Double.parseDouble(seg.substring(s, e));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Double dblOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isNaN(v) ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
