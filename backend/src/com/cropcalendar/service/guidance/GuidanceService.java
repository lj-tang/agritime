package com.cropcalendar.service.guidance;

import com.cropcalendar.compute.ComputeStub.WeatherWindow;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 农事指导（T03）：把 data/guidance/guidance.json 的结构化内容挂到每个时间线节点上。
 *
 * 四类内容：浇水 / 施肥 / 病虫害 / 农具。
 * 浇水与施肥会按节点窗口的天气调整（有雨则建议顺延、高温少雨则加量），
 * 病虫害优先取病害库里对应该作物的条目，没有就退到通用清单并标注。
 *
 * 取不到数据时返回 null，由前端显示"暂无"——不用模板话术糊弄。
 */
public final class GuidanceService {

    private static final String[] GUIDANCE_PATHS = {
            "../data/guidance/guidance.json",
            "data/guidance/guidance.json",
            "/agritime/data/guidance/guidance.json"
    };
    private static final String[] DISEASE_PATHS = {
            "../data/disease/library.json",
            "data/disease/library.json",
            "/agritime/data/disease/library.json"
    };

    private static volatile Map<String, Object> guidance;
    private static volatile List<Map<String, Object>> diseases;

    private GuidanceService() {
    }

    /** 兼容旧调用：不给日期与面积时，只出原文案，不做换算。 */
    public static String build(String opType, WeatherWindow slice, String cropName) {
        return build(opType, slice, cropName, null, 0, false);
    }

    public static String build(String opType, WeatherWindow slice, String cropName,
                               String nodeDate, double areaMu) {
        return build(opType, slice, cropName, nodeDate, areaMu, false);
    }

    /**
     * 返回一段可直接拼进响应的 JSON；无数据时返回 "null"。
     *
     * @param nodeDate 节点日期，用来算"未来 3 天"与"前 3 天"的降水；为空时退化为整个窗口
     * @param areaMu   地块面积（亩）；&lt;=0 表示未填，只给亩均用量并提示补录
     * @param farAhead 超出预报期的远期节点：只给规程用量，不编天气结论
     */
    public static String build(String opType, WeatherWindow slice, String cropName,
                               String nodeDate, double areaMu, boolean farAhead) {
        Map<String, Object> g = guidance();
        if (g == null) return "null";

        String op = opType == null ? "" : opType;
        boolean hasArea = areaMu > 0;

        double rain = 0;
        double maxTemp = -999;
        int days = 0;
        double rainPrev = 0;
        double rainNext = 0;
        int prevDays = 0;
        int nextDays = 0;
        if (slice != null && !slice.degraded()) {
            LocalDate center = null;
            if (nodeDate != null) {
                try {
                    center = LocalDate.parse(nodeDate);
                } catch (Exception ignored) {
                    // 日期异常就按整个窗口算
                }
            }
            for (Map.Entry<String, WeatherWindow.Daily> e : slice.byDate().entrySet()) {
                WeatherWindow.Daily d = e.getValue();
                rain += d.rainMm();
                if (d.maxTempC() > maxTemp) maxTemp = d.maxTempC();
                days++;
                if (center == null) continue;
                LocalDate day;
                try {
                    day = LocalDate.parse(e.getKey());
                } catch (Exception ignored) {
                    continue;
                }
                long diff = ChronoUnit.DAYS.between(center, day);
                if (diff >= 0 && diff <= 2) {
                    rainNext += d.rainMm();
                    nextDays++;
                } else if (diff < 0 && diff >= -3) {
                    rainPrev += d.rainMm();
                    prevDays++;
                }
            }
        }
        if (nextDays == 0 && days > 0) {
            // 没有节点日期时，用整个窗口近似"近期降水"，避免两处口径都取不到数
            rainNext = rain;
            rainPrev = rain;
        }

        // ---- 浇水：按未来 3 天降水与前期墒情调整 ----
        Map<?, ?> irrigation = asMap(g.get("irrigation"));
        String waterKey = "default";
        String waterBasis;
        if (days == 0) {
            waterBasis = farAhead
                    ? "远期节点按常年规程给量，作业前 7 天再看天气"
                    : "无天气数据，按常规灌溉建议";
        } else if (rainNext >= 10) {
            waterKey = "rainy";
            waterBasis = "未来 3 天累计降水约 " + Math.round(rainNext) + "mm，可暂停灌溉并注意排水";
        } else if (rainNext < 1 && rainPrev < 2) {
            waterKey = "drought";
            waterBasis = "近 3 天无有效降水、未来 3 天也无雨，按旱情加量补灌";
        } else {
            waterBasis = "未来 3 天降水约 " + Math.round(rainNext) + "mm，按常规灌溉";
        }
        Map<?, ?> water = asMap(irrigation.get(waterKey));

        // ---- 施肥：按节点类型选苗期 / 花果期 / 常规 ----
        String fertKey = "default";
        Map<?, ?> fert = asMap(asMap(g.get("fertilize")).get(fertKey));

        // ---- 病虫害：优先本作物，否则通用清单 ----
        List<String> pests = new ArrayList<>();
        String pestSource = "通用清单";
        for (Map<String, Object> d : diseaseLibrary()) {
            List<?> crops = d.get("crops") instanceof List<?> l ? l : List.of();
            boolean hit = false;
            for (Object c : crops) {
                if (cropName != null && String.valueOf(c).contains(cropName)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) continue;
            pests.add(pestJson(d.get("name"), d.get("symptoms"), d.get("medicine"),
                    "按标签用量兑水喷雾", d.get("prevention"), areaMu));
            pestSource = "病害库（" + cropName + "）";
            if (pests.size() >= 2) break;
        }
        if (pests.isEmpty()) {
            Map<?, ?> pestRoot = asMap(g.get("pesticide"));
            if (pestRoot.get("commonPests") instanceof List<?> common) {
                for (Object o : common) {
                    Map<?, ?> p = asMap(o);
                    pests.add(pestJson(p.get("name"), p.get("symptoms"), p.get("solution"),
                            p.get("dosage"), p.get("prevention"), areaMu));
                    if (pests.size() >= 2) break;
                }
            }
        }

        // ---- 农具：按节点类型取对应清单 ----
        String toolKey = switch (op) {
            case "sowing" -> "sowing";
            case "irrigation" -> "irrigation";
            case "fertilize" -> "fertilize";
            case "pesticide" -> "pesticide";
            case "harvest" -> "harvest";
            default -> "sowing";
        };
        List<String> toolJson = new ArrayList<>();
        if (asMap(g.get("tools")).get(toolKey) instanceof List<?> tools) {
            for (Object t : tools) toolJson.add(Json.str(String.valueOf(t)));
        }

        // ---- 天气建议：同一份降水数据，按节点类型给不同的作业口径 ----
        String adviceCode = null;
        String adviceLevel = "info";
        String adviceText = null;
        if (days > 0 && !farAhead) {
            if (rainNext >= 40) {
                adviceCode = "waterlog";
                adviceLevel = "warn";
                adviceText = "未来 3 天累计降水约 " + Math.round(rainNext) + "mm，田间易积水，先清沟排渍再作业";
            } else if (rainNext >= 10) {
                adviceCode = "before-rain";
                adviceLevel = "warn";
                adviceText = switch (op) {
                    case "irrigation" -> "未来 3 天有雨（约 " + Math.round(rainNext) + "mm），可暂停灌溉，注意排水防涝";
                    case "harvest" -> "未来 3 天有雨（约 " + Math.round(rainNext) + "mm），抢在雨前收获，或备好防雨覆盖";
                    case "sowing" -> "未来 3 天有雨（约 " + Math.round(rainNext) + "mm），可抢墒播种，注意别让田面积水";
                    default -> "未来 3 天有雨（约 " + Math.round(rainNext) + "mm），赶在雨前 1~2 天施用，避免被雨水冲刷";
                };
            } else if (rainNext < 1 && rainPrev < 2) {
                adviceCode = "drought";
                adviceLevel = "warn";
                adviceText = switch (op) {
                    case "irrigation" -> "近 3 天与未来 3 天均无有效降水，建议补灌（" + waterText(water) + "）";
                    case "fertilize" -> "土壤偏干、未来 3 天无雨，施肥后及时补灌，避免烧根";
                    case "pesticide" -> "近期无有效降水，药液易蒸发，建议早晚施药并适当加大兑水量";
                    default -> "近期无有效降水，注意保墒，播后及时镇压";
                };
            } else {
                adviceCode = "normal";
                adviceText = "未来 3 天降水约 " + Math.round(rainNext) + "mm，墒情接近常年";
            }
        }

        List<String> notes = new ArrayList<>();
        notes.add(Json.str(waterBasis));
        if (days > 0 && maxTemp >= 35) notes.add(Json.str("窗口内有高温（最高 " + Math.round(maxTemp) + "℃），建议避开正午作业"));
        if (!hasArea) notes.add(Json.str("未填地块面积，用量按亩给出；填入面积可自动换算总用量"));

        Map<String, String> parts = new LinkedHashMap<>();
        // 浇水：收获节点不掺灌溉建议
        if (!"harvest".equals(op)) {
            parts.put("watering", Json.obj(
                    "method", Json.str(str(water.get("method"))),
                    "duration", Json.str(str(water.get("duration"))),
                    "waterPerMu", Json.str(str(water.get("waterPerMu"))),
                    "dose", doseJson(Dose.parse(str(water.get("waterPerMu"))), areaMu),
                    "tips", Json.str(str(water.get("tips"))),
                    "basis", Json.str(waterBasis)));
        }
        // 施肥：只在施肥 / 播种（基肥）节点给
        if ("fertilize".equals(op) || "sowing".equals(op)) {
            parts.put("fertilize", Json.obj(
                    "type", Json.str(str(fert.get("type"))),
                    "npk", Json.str(str(fert.get("npk"))),
                    "amount", Json.str(str(fert.get("amount"))),
                    "dose", doseJson(Dose.parse(str(fert.get("amount"))), areaMu),
                    "method", Json.str(str(fert.get("method"))),
                    "tips", Json.str(str(fert.get("tips")))));
        }
        // 病虫害：打药节点给全量，播种节点只给苗期风险
        if ("pesticide".equals(op) || "sowing".equals(op)) {
            parts.put("pest", Json.arr(pests));
            parts.put("pestSource", Json.str(pestSource));
        }
        parts.put("tools", Json.arr(toolJson));
        parts.put("notes", Json.arr(notes));
        parts.put("area", Json.obj(
                "areaMu", hasArea ? Json.num(areaMu) : "null",
                "hint", hasArea ? "null" : Json.str("填入面积可自动换算总用量")));
        parts.put("weatherAdvice", adviceCode == null ? "null" : Json.obj(
                "code", Json.str(adviceCode),
                "level", Json.str(adviceLevel),
                "text", Json.str(adviceText)));
        List<String> kv = new ArrayList<>(parts.size() * 2);
        parts.forEach((k, v) -> {
            kv.add(k);
            kv.add(v);
        });
        return Json.obj((Object[]) kv.toArray(new String[0]));
    }

    private static String waterText(Map<?, ?> water) {
        String perMu = str(water.get("waterPerMu"));
        return perMu == null ? "按常规灌水量" : perMu;
    }

    /**
     * 亩均 + 总面积用量。
     * 未填面积时 total 为 null，前端只显示亩均并提示补录，不编一个假总量。
     */
    private static String doseJson(Dose dose, double areaMu) {
        if (dose == null) return "null";
        String total = areaMu > 0
                ? Json.obj(
                "min", Json.num(round1(dose.min() * areaMu)),
                "max", Json.num(round1(dose.max() * areaMu)),
                "text", Json.str(dose.totalText(areaMu)))
                : "null";
        return Json.obj(
                "perMu", Json.obj(
                        "min", Json.num(dose.min()),
                        "max", Json.num(dose.max()),
                        "unit", Json.str(dose.unit()),
                        "text", Json.str(dose.perMuText())),
                "total", total);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String pestJson(Object name, Object symptom, Object solution, Object dosage,
                                   Object prevention, double areaMu) {
        return Json.obj(
                "name", Json.str(str(name)),
                "symptom", Json.str(str(symptom)),
                "solution", Json.str(str(solution)),
                "dosage", Json.str(str(dosage)),
                "dose", doseJson(Dose.parse(str(dosage)), areaMu),
                "prevention", Json.str(str(prevention)));
    }

    // ---------------- 数据加载 ----------------

    private static Map<String, Object> guidance() {
        Map<String, Object> local = guidance;
        if (local != null) return local;
        synchronized (GuidanceService.class) {
            if (guidance != null) return guidance;
            Object root = parseFirst(GUIDANCE_PATHS);
            if (root instanceof Map<?, ?> m) {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), v));
                guidance = out;
            } else {
                System.err.println("[guidance] guidance.json 不可用，节点指导降级为 null");
                guidance = Map.of();
            }
            return guidance;
        }
    }

    private static List<Map<String, Object>> diseaseLibrary() {
        List<Map<String, Object>> local = diseases;
        if (local != null) return local;
        synchronized (GuidanceService.class) {
            if (diseases != null) return diseases;
            List<Map<String, Object>> list = new ArrayList<>();
            Object root = parseFirst(DISEASE_PATHS);
            if (root instanceof Map<?, ?> m && m.get("diseases") instanceof List<?> arr) {
                for (Object o : arr) {
                    if (!(o instanceof Map<?, ?> d)) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    d.forEach((k, v) -> item.put(String.valueOf(k), v));
                    list.add(item);
                }
            }
            diseases = list;
            return diseases;
        }
    }

    private static Object parseFirst(String[] paths) {
        for (String p : paths) {
            try {
                Path path = Path.of(p);
                if (!Files.isReadable(path)) continue;
                return JsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("[guidance] 解析 " + p + " 失败：" + e.getMessage());
            }
        }
        return null;
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }
}
