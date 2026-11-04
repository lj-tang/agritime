package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.WeatherWindow;
import com.cropcalendar.orchestration.CacheStore;
import com.cropcalendar.service.aqua.AquaCost;
import com.cropcalendar.service.aqua.SpeciesCatalog;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/industry/* —— 渔业试点（T09）。
 *
 *   GET /api/industry/species                     品类清单
 *   GET /api/industry/timeline?species=&stockDate=&lat=&lon=   生产日历
 *
 * 鱼苗蟹苗类比种子：放苗日 + 阶段天数 = 具体日期，
 * 再用放苗点位的天气窗口判断该阶段适不适合操作。
 *
 * 水温口径：Open-Meteo 不提供养殖水体温度，这里用**气温**近似判定，
 * 返回里明确标注 tempBasis=air，不冒充水温实测。
 */
public class IndustryHandler implements HttpHandler {

    private static final String[] PATHS = {
            "../data/industries/aquaculture/species.json",
            "data/industries/aquaculture/species.json",
            "/agritime/data/industries/aquaculture/species.json"
    };

    private static volatile List<Map<String, Object>> speciesCache;

    /** 生产日历缓存：一次要打预报 + 去年历史两个上游，缓存 5 分钟 */
    private static final CacheStore CACHE = new CacheStore();
    private static final int CACHE_TTL = 300;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/species")) {
            listSpecies(ex);
        } else if (path.endsWith("/timeline")) {
            timeline(ex);
        } else if (path.endsWith("/cost")) {
            cost(ex);
        } else {
            HttpSupport.write(ex, 404, Json.obj("code", Json.str("40400"), "message", Json.str("未知接口")));
        }
    }

    private void listSpecies(HttpExchange ex) throws IOException {
        List<String> items = new ArrayList<>();
        for (Map<String, Object> s : species()) {
            items.add(Json.obj(
                    "id", Json.str(str(s.get("id"))),
                    "name", Json.str(str(s.get("name"))),
                    "category", Json.str(str(s.get("category"))),
                    "stockUnit", Json.str(str(s.get("stockUnit"))),
                    "stockSeason", Json.str(str(s.get("stockSeason"))),
                    "waterTempMin", num(s.get("waterTempMin")),
                    "waterTempMax", num(s.get("waterTempMax")),
                    "tips", Json.str(str(s.get("tips")))));
        }
        HttpSupport.write(ex, 200, Json.obj(
                "code", Json.str("0"),
                "message", Json.str("ok"),
                "data", Json.obj("total", String.valueOf(items.size()), "species", Json.arr(items))));
    }

    private void timeline(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String id = HttpSupport.trimToNull(q.get("species"));
        String stockDate = HttpSupport.trimToNull(q.get("stockDate"));
        double lat = HttpSupport.parseDouble(q.get("lat"), 30.33);
        double lon = HttpSupport.parseDouble(q.get("lon"), 112.24);
        // 面积决定成本估算的规模；不传就按 10 亩，页面会写明这是估算口径
        double areaMu = HttpSupport.parseDouble(q.get("areaMu"), 10);

        if (id == null) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40001"), "message", Json.str("缺少参数 species")));
            return;
        }
        Map<String, Object> sp = null;
        for (Map<String, Object> s : species()) {
            if (id.equals(str(s.get("id"))) || id.equals(str(s.get("name")))) {
                sp = s;
                break;
            }
        }
        if (sp == null) {
            HttpSupport.write(ex, 404, Json.obj("code", Json.str("40404"), "message", Json.str("没有这个养殖品类")));
            return;
        }
        // 品类允许用中文名查询，但成本模型按 id 查表，这里统一归一到 id
        id = str(sp.get("id"));

        String cacheKey = "industry:" + id + ":" + stockDate + ":" + Math.round(lat * 100) + ":"
                + Math.round(lon * 100) + ":" + Math.round(areaMu * 10);
        String cached = CACHE.get(cacheKey, String.class);
        if (cached != null) {
            HttpSupport.write(ex, 200, cached);
            return;
        }

        LocalDate stock;
        try {
            stock = LocalDate.parse(stockDate);
        } catch (Exception e) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40002"), "message", Json.str("stockDate 格式应为 yyyy-MM-dd")));
            return;
        }

        List<?> stages = sp.get("stages") instanceof List<?> l ? l : List.of();
        List<LocalDate> dates = new ArrayList<>();
        for (Object o : stages) {
            if (o instanceof Map<?, ?> m) {
                int off = intOf(m.get("dayOffset"), 0);
                dates.add(stock.plusDays(off));
            }
        }
        List<String> dateStr = new ArrayList<>();
        for (LocalDate d : dates) dateStr.add(d.toString());

        // 天气用于判断该阶段当天适不适合操作；取不到就标注"无天气数据"，不硬判
        WeatherWindow window = dateStr.isEmpty()
                ? WeatherWindow.unavailable("无节点日期")
                : ComputeStub.fetchWindowWeather(lat, lon, dateStr);

        // 超出预报范围的远期阶段：用去年同期的历史天气做气候均值，
        // 总比"无数据"更有参考价值，且必须标明是常年同期而非预报
        List<String> lastYear = new ArrayList<>();
        for (LocalDate d : dates) {
            if (!window.byDate().containsKey(d.toString())) {
                lastYear.add(d.minusYears(1).toString());
            }
        }
        WeatherWindow normal = lastYear.isEmpty()
                ? WeatherWindow.unavailable("无远期节点")
                : ComputeStub.fetchWindowWeather(lat, lon, lastYear);

        double tMin = dblOf(sp.get("waterTempMin"), 15);
        double tMax = dblOf(sp.get("waterTempMax"), 30);

        List<String> nodes = new ArrayList<>();
        int seq = 0;
        for (Object o : stages) {
            if (!(o instanceof Map<?, ?> m)) continue;
            seq++;
            int off = intOf(m.get("dayOffset"), 0);
            LocalDate d = stock.plusDays(off);
            WeatherWindow.Daily daily = window.byDate().get(d.toString());
            boolean fromNormal = false;
            if (daily == null) {
                daily = normal.byDate().get(d.minusYears(1).toString());
                fromNormal = daily != null;
            }

            String status = "unknown";
            String advice = "无天气数据，按经验安排";
            Double temp = null;
            if (daily != null) {
                temp = Math.round((daily.maxTempC() + daily.minTempC()) / 2 * 10) / 10.0;
                if (temp < tMin) {
                    status = "caution";
                    advice = "气温 " + temp + "℃ 低于适宜区间（" + fmt(tMin) + "~" + fmt(tMax) + "℃），建议推迟或加深水位";
                } else if (temp > tMax) {
                    status = "caution";
                    advice = "气温 " + temp + "℃ 高于适宜区间（" + fmt(tMin) + "~" + fmt(tMax) + "℃），注意增氧与降温";
                } else {
                    status = "ok";
                    advice = "气温 " + temp + "℃ 在适宜区间（" + fmt(tMin) + "~" + fmt(tMax) + "℃）内";
                }
                if (daily.rainMm() >= 20) {
                    advice += "；当日降雨约 " + Math.round(daily.rainMm()) + "mm，注意防溢流与水质突变";
                }
                if (fromNormal) {
                    advice = "常年同期参考：" + advice + "（非预报，按去年同日天气估算）";
                }
            }

            String costHint = "harvest".equals(str(m.get("opType")))
                    ? AquaCost.harvestHint(id, areaMu)
                    : null;

            nodes.add(Json.obj(
                    "nodeSeq", String.valueOf(seq),
                    "costHint", Json.str(costHint),
                    "dayOffset", String.valueOf(off),
                    "date", Json.str(d.toString()),
                    "opType", Json.str(str(m.get("opType"))),
                    "title", Json.str(str(m.get("title"))),
                    "detail", Json.str(str(m.get("detail"))),
                    "status", Json.str(status),
                    "advice", Json.str(advice),
                    "tempC", temp != null ? String.valueOf(temp) : "null"));
        }

        String data = Json.obj(
                "species", Json.str(str(sp.get("name"))),
                "speciesId", Json.str(str(sp.get("id"))),
                "stockDate", Json.str(stock.toString()),
                "stockUnit", Json.str(str(sp.get("stockUnit"))),
                "areaMu", Json.num(areaMu),
                "stockSeason", Json.str(str(sp.get("stockSeason"))),
                "tips", Json.str(str(sp.get("tips"))),
                "waterTempMin", fmt(tMin),
                "waterTempMax", fmt(tMax),
                // 口径声明：气温近似，不是水体实测
                "tempBasis", Json.str("air"),
                "tempNote", Json.str("未接水温传感器时用气温近似判定；在「我的地块」接入水温设备后以实测为准"),
                "weatherStatus", Json.str(window.degraded() ? "degraded" : "ok"),
                "nodes", Json.arr(nodes));

        String body = Json.obj(
                "code", Json.str("0"),
                "message", Json.str("ok"),
                "data", data);
        CACHE.put(cacheKey, body, CACHE_TTL);
        HttpSupport.write(ex, 200, body);
    }

    /**
     * GET /api/industry/cost —— 成本与产出核算（V5 T20）
     *
     * 参数全部可选：不传就用成本模型里的默认值，返回 mode=estimate；
     * 传了 actualFeedKg / actualHarvestKg 就用实际值算，返回 mode=actual。
     */
    private void cost(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String id = HttpSupport.trimToNull(q.get("species"));
        if (id == null) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40001"), "message", Json.str("缺少参数 species")));
            return;
        }
        for (Map<String, Object> s : species()) {
            if (id.equals(str(s.get("id"))) || id.equals(str(s.get("name")))) {
                id = str(s.get("id"));
                break;
            }
        }

        AquaCost.Inputs in = new AquaCost.Inputs(
                dblOrNull(q.get("areaMu")), dblOrNull(q.get("stockQty")),
                dblOrNull(q.get("survivalRate")), dblOrNull(q.get("fcr")),
                dblOrNull(q.get("feedPricePerKg")), dblOrNull(q.get("seedPricePerFish")),
                dblOrNull(q.get("harvestSizeKg")), dblOrNull(q.get("marketPricePerKg")),
                dblOrNull(q.get("electricityPerMu")), dblOrNull(q.get("medicinePerMu")),
                dblOrNull(q.get("laborPerMu")), dblOrNull(q.get("rentPerMu")),
                dblOrNull(q.get("actualFeedKg")), dblOrNull(q.get("actualHarvestKg")));

        AquaCost.Result r;
        try {
            r = AquaCost.estimate(id, in);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("code", Json.str("50001"),
                    "message", Json.str("成本模型读取失败：" + e.getMessage())));
            return;
        }
        if (r == null) {
            HttpSupport.write(ex, 404, Json.obj("code", Json.str("40404"), "message", Json.str("没有这个养殖品类")));
            return;
        }

        List<String> assumptions = new ArrayList<>();
        for (String a : r.assumptions()) assumptions.add(Json.str(a));

        HttpSupport.write(ex, 200, Json.obj(
                "code", Json.str("0"),
                "message", Json.str("ok"),
                "data", Json.obj(
                        "speciesId", Json.str(r.speciesId()),
                        "species", Json.str(r.speciesName()),
                        "mode", Json.str(r.mode()),
                        "estimated", String.valueOf(r.estimated()),
                        "inputs", Json.obj(
                                "areaMu", Json.num(r.areaMu()),
                                "stockQty", Json.num(r.stockQty()),
                                "survivalRate", Json.num(r.survivalRate()),
                                "harvestKg", Json.num(r.harvestKg()),
                                "feedKg", Json.num(r.feedKg())),
                        "cost", Json.obj(
                                "seed", Json.num(r.seedCost()),
                                "feed", Json.num(r.feedCost()),
                                "electricity", Json.num(r.electricityCost()),
                                "medicine", Json.num(r.medicineCost()),
                                "labor", Json.num(r.laborCost()),
                                "rent", Json.num(r.rentCost()),
                                "total", Json.num(r.totalCost())),
                        "output", Json.obj(
                                "harvestKg", Json.num(r.harvestKg()),
                                "revenue", Json.num(r.revenue()),
                                "profit", Json.num(r.profit()),
                                "breakEvenPricePerKg", Json.num(r.breakEvenPricePerKg())),
                        "assumptions", Json.arr(assumptions),
                        "note", Json.str(r.estimated()
                                ? "估算值，非实际账目；填入实际投喂量与出塘量后会按实际值重算"
                                : "按你填写的实际投喂量与出塘量计算"))));
    }

    private static Double dblOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Map<String, Object>> species() {
        return SpeciesCatalog.all();
    }

    private String num(Object o) {
        return o == null ? "null" : String.valueOf(o);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double dblOf(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
