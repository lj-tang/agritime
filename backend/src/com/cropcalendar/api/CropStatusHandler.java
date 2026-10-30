package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.RawNode;
import com.cropcalendar.orchestration.CacheStore;
import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.service.crop.CropRegistry;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * GET /api/crop-status —— 我的作物真实状态（V5 T12 深化）
 *
 * 以前的"我的作物"只是一张写着作物名和播种日期的卡片：系统并不知道这棵作物
 * 现在多高了、该不该浇水、什么时候收 —— 用户看到的和日历上第一次生成的内容
 * 没有任何区别，等于"存了但没跟踪"。
 *
 * 这个接口把档案变成活的状态：
 * 1. 用真实农事规程（crop-protocols.json）算出当前处在哪个节点、下个节点是什么；
 * 2. 拉播种日到今天的实测气温，按作物基点温度累计活动积温（GDD）；
 * 3. 拉去年同一段日历窗口的积温做常年基准，算出"今年比常年快/慢几天"——
 *    这才是生育进度的真实来源，而不是简单地用「过了多少天 / 全生育期」；
 * 4. 同理对比累计降水，判断偏旱还是偏涝；
 * 5. 结合未来 7 天预报给出本周 3 条具体做法。
 *
 * 没有气象数据时如实降级为日历进度，不用假数据凑。
 */
public class CropStatusHandler implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final DateTimeFormatter D = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 积温与降水一天之内不会变，缓存 6 小时足够，也避免每次开页面都打归档接口。 */
    private static final CacheStore CACHE = new CacheStore();
    private static final int TTL_SECONDS = 6 * 3600;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String crop = HttpSupport.trimToNull(q.get("crop"));
        String sowDate = HttpSupport.trimToNull(q.get("sowDate"));
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);

        if (crop == null || sowDate == null) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("缺少 crop 或 sowDate")));
            return;
        }
        LocalDate sow;
        try {
            sow = LocalDate.parse(sowDate, D);
        } catch (Exception e) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("sowDate 格式应为 yyyy-MM-dd")));
            return;
        }

        String key = "crop-status:" + crop + ":" + sowDate + ":" + Math.round(lat * 100) + ":" + Math.round(lon * 100)
                + ":" + LocalDate.now();
        String cached = CACHE.get(key, String.class);
        if (cached != null) {
            HttpSupport.write(ex, 200, cached);
            return;
        }

        try {
            String out = build(crop, sow, sowDate, lat, lon);
            CACHE.put(key, out, TTL_SECONDS);
            HttpSupport.write(ex, 200, out);
        } catch (IllegalArgumentException e) {
            HttpSupport.write(ex, 404, Json.obj("error", Json.str(e.getMessage())));
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("error", Json.str("状态计算失败：" + e.getMessage())));
        }
    }

    private String build(String crop, LocalDate sow, String sowDate, double lat, double lon) {
        String std = CropRegistry.standardName(crop);
        String display = std != null ? std : crop;
        String protocolId = CropRegistry.protocolIdOf(crop);
        String buildId = protocolId != null ? protocolId : crop;

        CropProperties.Props props = CropProperties.of(display);
        List<RawNode> nodes = new ArrayList<>(ComputeStub.buildNodes(buildId, sowDate, lat));
        nodes.sort(Comparator.comparingInt(RawNode::dayOffset));

        LocalDate today = LocalDate.now();
        long daysSince = java.time.temporal.ChronoUnit.DAYS.between(sow, today);
        int totalDays = props != null && props.totalDays() > 0 ? props.totalDays() : 120;
        if (!nodes.isEmpty()) {
            int last = nodes.get(nodes.size() - 1).dayOffset();
            // 规程比属性库更能代表"从种到收"的实际跨度，取较大者
            totalDays = Math.max(totalDays, last);
        }

        RawNode current = null, next = null;
        for (RawNode n : nodes) {
            if (n.dayOffset() <= daysSince) current = n;
            else if (next == null) next = n;
        }

        // 积温与降水：实测（今年）+ 常年基准（去年同一窗口），两路并行
        int n = (int) Math.max(0, daysSince);
        CompletableFuture<Series> actualF = CompletableFuture.supplyAsync(() -> fetchSeries(lat, lon, sow, today));
        CompletableFuture<Series> normalF = CompletableFuture.supplyAsync(
                () -> fetchSeries(lat, lon, sow.minusYears(1), today.minusYears(1)));

        Series actual = actualF.completeOnTimeout(null, 8, TimeUnit.SECONDS).join();
        Series normal = normalF.completeOnTimeout(null, 8, TimeUnit.SECONDS).join();

        double baseT = baseTempOf(props);
        Double gddActual = actual == null ? null : gdd(actual, baseT);
        Double gddNormal = normal == null ? null : gdd(normal, baseT);
        Double rainActual = actual == null ? null : sum(actual.precip);
        Double rainNormal = normal == null ? null : sum(normal.precip);

        boolean hasGdd = gddActual != null && gddNormal != null && gddNormal > 1;
        double ratio = hasGdd ? gddActual / gddNormal : 1.0;

        double calProgress = totalDays > 0 ? (double) n / totalDays : 0;
        double realProgress = clamp01(calProgress * ratio);
        // 正负号统一成"进度偏快为正"：ratio>1 说明今年积温多，进程提前
        int dayDelta = (int) Math.round((realProgress - calProgress) * totalDays);

        String phase = phaseOf(realProgress);
        String harvest = sow.plusDays(totalDays).format(D);
        LocalDate harvestAdj = sow.plusDays(Math.max(1, (int) Math.round(totalDays / Math.max(0.2, ratio))));

        List<String> actions = new ArrayList<>();
        String weekNote = weekActions(actions, next, current, lat, lon, rainActual, rainNormal, dayDelta, display);

        List<String> kv = new ArrayList<>();
        kv.add("crop"); kv.add(Json.str(display));
        kv.add("cropId"); kv.add(Json.str(props == null ? null : props.cropId()));
        kv.add("sowDate"); kv.add(Json.str(sowDate));
        kv.add("today"); kv.add(Json.str(today.format(D)));
        kv.add("daysSince"); kv.add(Json.num((double) n));
        kv.add("totalDays"); kv.add(Json.num((double) totalDays));
        kv.add("progressPct"); kv.add(Json.num(Math.round(calProgress * 100)));
        kv.add("adjustedProgressPct"); kv.add(Json.num(Math.round(realProgress * 100)));
        kv.add("dayDelta"); kv.add(Json.num((double) dayDelta));
        kv.add("phase"); kv.add(Json.str(phase));
        kv.add("harvestDate"); kv.add(Json.str(harvest));
        kv.add("harvestDateAdjusted"); kv.add(Json.str(harvestAdj.format(D)));
        kv.add("daysToHarvest"); kv.add(Json.num((double) java.time.temporal.ChronoUnit.DAYS.between(today, harvestAdj)));

        if (current != null) {
            kv.add("currentNode"); kv.add(Json.raw(nodeJson(current, sow)));
        }
        if (next != null) {
            kv.add("nextNode"); kv.add(Json.raw(nodeJson(next, sow)));
        }

        kv.add("gdd"); kv.add(Json.raw(Json.obj(
                "baseTempC", Json.num(baseT),
                "actual", gddActual == null ? "null" : Json.num(Math.round(gddActual)),
                "normal", gddNormal == null ? "null" : Json.num(Math.round(gddNormal)),
                "ratio", Json.num(Math.round(ratio * 100) / 100.0),
                "available", hasGdd ? "true" : "false")));

        kv.add("water"); kv.add(Json.raw(Json.obj(
                "precipActualMm", rainActual == null ? "null" : Json.num(Math.round(rainActual * 10) / 10.0),
                "precipNormalMm", rainNormal == null ? "null" : Json.num(Math.round(rainNormal * 10) / 10.0),
                "status", Json.str(waterStatus(rainActual, rainNormal)))));

        List<String> aj = new ArrayList<>();
        for (String a : actions) aj.add(Json.str(a));
        kv.add("weekActions"); kv.add(Json.arr(aj));
        kv.add("weekNote"); kv.add(Json.str(weekNote));

        kv.add("summary"); kv.add(Json.str(summaryOf(display, phase, dayDelta, waterStatus(rainActual, rainNormal),
                next, harvestAdj, hasGdd)));
        kv.add("dataSources"); kv.add(Json.obj(
                "protocol", Json.str("作物农事规程 crop-protocols.json（真实节点，非估算）"),
                "gdd", Json.str("Open-Meteo 归档气温实测 + 去年同期常年基准"),
                "forecast", Json.str("Open-Meteo 未来 7 天预报"),
                "progress", Json.str(hasGdd ? "积温校正进度（今年积温 / 常年同期积温）" : "日历进度（积温数据不足，未校正）")));

        return Json.obj(kv.toArray());
    }

    // ------------------------------------------------------------------ 阶段与文案

    private static String phaseOf(double p) {
        if (p < 0.12) return "苗期";
        if (p < 0.35) return "营养生长期";
        if (p < 0.55) return "拔节/分枝期";
        if (p < 0.72) return "开花授粉期";
        if (p < 0.90) return "结实灌浆期";
        if (p < 1.0) return "成熟前期";
        return "已到收获期";
    }

    private static String waterStatus(Double actual, Double normal) {
        if (actual == null || normal == null) return "无降水数据";
        if (normal < 1) return actual > 40 ? "偏多" : "正常";
        double r = actual / normal;
        if (r < 0.6) return "偏旱";
        if (r < 0.85) return "略偏干";
        if (r <= 1.2) return "正常";
        if (r <= 1.6) return "略偏多";
        return "偏涝";
    }

    private String summaryOf(String crop, String phase, int dayDelta, String water,
                             RawNode next, LocalDate harvest, boolean hasGdd) {
        StringBuilder sb = new StringBuilder();
        sb.append(crop).append("当前处于").append(phase);
        if (hasGdd) {
            if (dayDelta >= 3) sb.append("，比常年同期提前约 ").append(dayDelta).append(" 天");
            else if (dayDelta <= -3) sb.append("，比常年同期推迟约 ").append(Math.abs(dayDelta)).append(" 天");
            else sb.append("，生育进程与常年基本同步");
        } else {
            sb.append("（按日历进度推算，积温数据不足未做校正）");
        }
        sb.append("；水分状况").append(water);
        if (next != null) sb.append("；下一步是").append(next.title());
        sb.append("，预计 ").append(harvest).append(" 前后可收");
        return sb.toString();
    }

    private String weekActions(List<String> out, RawNode next, RawNode current,
                               double lat, double lon, Double rainActual, Double rainNormal,
                               int dayDelta, String crop) {
        // 未来 7 天预报：用时间线同一套窗口数据，保证两边天气说法一致
        List<String> dates = new ArrayList<>();
        LocalDate t = LocalDate.now();
        for (int i = 0; i < 7; i++) dates.add(t.plusDays(i).format(D));
        ComputeStub.WeatherWindow w = null;
        try {
            w = ComputeStub.fetchWindowWeather(lat, lon, dates);
        } catch (Exception ignored) {
            w = null;
        }
        double rain7 = 0, tmax = -99, tmin = 99;
        int rainyDays = 0;
        boolean hasFc = w != null && w.byDate() != null && !w.byDate().isEmpty();
        if (hasFc) {
            for (ComputeStub.WeatherWindow.Daily d : w.byDate().values()) {
                rain7 += d.rainMm();
                tmax = Math.max(tmax, d.maxTempC());
                tmin = Math.min(tmin, d.minTempC());
                if (d.rainMm() > 0.1) rainyDays++;
            }
        }

        // 第 1 条：下一个节点要做什么
        if (next != null) {
            String g = null;
            try {
                // GuidanceService 返回的是结构化 JSON，直接拼进句子会把原始 JSON 甩给用户，
                // 这里只取灌溉/施肥/工具三样，压成一句人话
                g = guidanceToText(com.cropcalendar.service.guidance.GuidanceService.build(next.opType(), w, crop));
            } catch (Exception ignored) {
                g = null;
            }
            out.add("近期农事：" + next.title()
                    + (g != null && !g.isBlank() ? " —— " + g : " —— 按规程及时完成"));
        } else if (current != null) {
            out.add("已到最后节点：" + current.title() + "，注意及时采收，避免落粒或品质下降");
        } else {
            out.add("尚未匹配到农事规程，建议先在日历里生成一次完整时间线");
        }

        // 第 2 条：本周天气决定的水肥动作
        if (hasFc) {
            if (rain7 >= 30) {
                out.add("未来 7 天累计降水 " + String.format("%.0f", rain7) + "mm（" + rainyDays
                        + " 天有雨），暂停灌溉、提前疏通沟渠防渍，追肥改在雨歇间隙");
            } else if (rain7 >= 5) {
                out.add("未来 7 天降水 " + String.format("%.0f", rain7) + "mm，墒情基本够用，"
                        + "追肥可随雨进行，利用率最高");
            } else {
                out.add("未来 7 天几乎无降水（" + String.format("%.0f", rain7)
                        + "mm），需补水 25~35 m³/亩，宜早晚浇、避免中午高温时段");
            }
            if (tmax >= 33) {
                out.add("本周最高温 " + String.format("%.0f", tmax)
                        + "℃，注意高温胁迫：加大通风/遮阳，灌溉改在清晨，叶面补钾提高耐热性");
            } else if (tmin <= 2) {
                out.add("本周最低温 " + String.format("%.0f", tmin)
                        + "℃，有霜冻风险：及时覆盖或熏烟，已播地块暂停浇水以免结冰伤根");
            }
        } else {
            out.add("本周天气预报暂未取到，请按常规农事安排并留意临近预警");
        }

        // 第 3 条：长势偏差带来的管理动作
        if (out.size() < 3) {
            if (dayDelta <= -5) {
                out.add("积温偏少、生育进程推迟约 " + Math.abs(dayDelta)
                        + " 天：适当增施速效氮肥提苗，必要时选用早熟品种或提前覆膜");
            } else if (dayDelta >= 5) {
                out.add("积温偏多、生育进程提前约 " + dayDelta
                        + " 天：注意控制氮肥防止旺长，提前做好收获与晾晒准备");
            } else {
                out.add("生育进程与常年同步，按常规管理即可，重点巡查病虫与杂草");
            }
        }
        return hasFc ? "未来 7 天：" + rainyDays + " 天有雨，累计 " + String.format("%.0f", rain7)
                + "mm，最高 " + String.format("%.0f", tmax) + "℃，最低 " + String.format("%.0f", tmin) + "℃"
                : "本周天气预报未取到";
    }

    // ------------------------------------------------------------------ 数据抓取与计算

    private static final class Series {
        List<Double> tmean = new ArrayList<>();
        List<Double> precip = new ArrayList<>();
    }

    private Series fetchSeries(double lat, double lon, LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) return null;
        String url = String.format("https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f"
                        + "&start_date=%s&end_date=%s"
                        + "&daily=temperature_2m_mean,precipitation_sum&timezone=auto",
                lat, lon, from.format(D), to.format(D));
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(9)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            Object o = JsonParser.parse(resp.body());
            if (!(o instanceof Map<?, ?> m) || !(m.get("daily") instanceof Map<?, ?> d)) return null;
            Series s = new Series();
            s.tmean = numList(d.get("temperature_2m_mean"));
            s.precip = numList(d.get("precipitation_sum"));
            if (s.tmean.isEmpty()) return null;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Double> numList(Object o) {
        List<Double> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object v : l) out.add(v instanceof Number n ? n.doubleValue() : Double.NaN);
        }
        return out;
    }

    /** 活动积温：Σ max(0, 日均温 - 基点温度)。缺测那天跳过，不按 0 计。 */
    private static double gdd(Series s, double base) {
        double sum = 0;
        for (double t : s.tmean) {
            if (Double.isNaN(t)) continue;
            sum += Math.max(0, t - base);
        }
        return sum;
    }

    private static double sum(List<Double> xs) {
        double s = 0;
        for (double x : xs) if (!Double.isNaN(x)) s += x;
        return s;
    }

    /** 基点温度：耐寒作物用 5℃，其余用 10℃。从属性库的文字描述里读，不新增假数据字段。 */
    private static double baseTempOf(CropProperties.Props p) {
        if (p != null && p.tempRequirement() != null && p.tempRequirement().contains("耐寒")) return 5.0;
        return 10.0;
    }

    /** 把 GuidanceService 的结构化指导压成一句话，只保留农户真正要执行的部分。 */
    private static String guidanceToText(String json) {
        if (json == null || json.isBlank()) return null;
        StringBuilder sb = new StringBuilder();
        try {
            Object o = JsonParser.parse(json);
            if (!(o instanceof Map<?, ?> m)) return null;
            Object wa = m.get("watering");
            if (wa instanceof Map<?, ?> wm) {
                String method = str(wm.get("method"));
                String per = str(wm.get("waterPerMu"));
                if (method != null) sb.append("灌溉：").append(method);
                if (per != null && !"0方/亩".equals(per) && !"—".equals(per)) sb.append("（").append(per).append("）");
            }
            Object fe = m.get("fertilize");
            if (fe instanceof Map<?, ?> fm) {
                String type = str(fm.get("type"));
                String amount = str(fm.get("amount"));
                if (sb.length() > 0) sb.append("；");
                sb.append("施肥：").append(type == null ? "按需" : type);
                if (amount != null) sb.append(" ").append(amount);
            }
            Object tools = m.get("tools");
            if (tools instanceof List<?> tl && !tl.isEmpty()) {
                if (sb.length() > 0) sb.append("；");
                sb.append("用具：").append(str(tl.get(0)));
            }
        } catch (Exception e) {
            return null;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nodeJson(RawNode n, LocalDate sow) {
        return Json.obj("dayOffset", Json.num((double) n.dayOffset()),
                "date", Json.str(sow.plusDays(n.dayOffset()).format(D)),
                "opType", Json.str(n.opType()),
                "title", Json.str(n.title()));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
