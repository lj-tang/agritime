package com.cropcalendar.service.guidance;

import com.cropcalendar.compute.ComputeStub.WeatherWindow;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** 返回一段可直接拼进响应的 JSON；无数据时返回 "null"。 */
    public static String build(String opType, WeatherWindow slice, String cropName) {
        Map<String, Object> g = guidance();
        if (g == null) return "null";

        double rain = 0;
        double maxTemp = -999;
        int days = 0;
        if (slice != null && !slice.degraded()) {
            for (WeatherWindow.Daily d : slice.byDate().values()) {
                rain += d.rainMm();
                if (d.maxTempC() > maxTemp) maxTemp = d.maxTempC();
                days++;
            }
        }

        // ---- 浇水：按窗口降雨与高温调整 ----
        Map<?, ?> irrigation = asMap(g.get("irrigation"));
        String waterKey = "default";
        String waterBasis;
        if (days == 0) {
            waterBasis = "无天气数据，按常规灌溉建议";
        } else if (rain >= 10) {
            waterKey = "rainy";
            waterBasis = "节点窗口内累计降雨约 " + Math.round(rain) + "mm，可暂停灌溉并注意排水";
        } else if (rain < 2 && maxTemp >= 30) {
            waterKey = "drought";
            waterBasis = "窗口内基本无雨且最高 " + Math.round(maxTemp) + "℃，按旱情加量灌溉";
        } else {
            waterBasis = "窗口内累计降雨约 " + Math.round(rain) + "mm，按常规灌溉";
        }
        Map<?, ?> water = asMap(irrigation.get(waterKey));

        // ---- 施肥：按节点类型选苗期 / 花果期 / 常规 ----
        String fertKey = "default";
        String titleHint = opType == null ? "" : opType;
        if ("fertilize".equals(titleHint)) {
            fertKey = "default";
        }
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
                    "按标签用量兑水喷雾", d.get("prevention")));
            pestSource = "病害库（" + cropName + "）";
            if (pests.size() >= 2) break;
        }
        if (pests.isEmpty()) {
            Map<?, ?> pestRoot = asMap(g.get("pesticide"));
            if (pestRoot.get("commonPests") instanceof List<?> common) {
                for (Object o : common) {
                    Map<?, ?> p = asMap(o);
                    pests.add(pestJson(p.get("name"), p.get("symptoms"), p.get("solution"),
                            p.get("dosage"), p.get("prevention")));
                    if (pests.size() >= 2) break;
                }
            }
        }

        // ---- 农具：按节点类型取对应清单 ----
        String toolKey = switch (opType == null ? "" : opType) {
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

        List<String> notes = new ArrayList<>();
        notes.add(Json.str(waterBasis));
        if (days > 0 && maxTemp >= 35) notes.add(Json.str("窗口内有高温（最高 " + Math.round(maxTemp) + "℃），建议避开正午作业"));
        if (days > 0 && rain >= 10) notes.add(Json.str("降雨较多，施肥建议改在雨前或雨后 2 天进行"));

        return Json.obj(
                "watering", Json.obj(
                        "method", Json.str(str(water.get("method"))),
                        "duration", Json.str(str(water.get("duration"))),
                        "waterPerMu", Json.str(str(water.get("waterPerMu"))),
                        "tips", Json.str(str(water.get("tips"))),
                        "basis", Json.str(waterBasis)),
                "fertilize", Json.obj(
                        "type", Json.str(str(fert.get("type"))),
                        "npk", Json.str(str(fert.get("npk"))),
                        "amount", Json.str(str(fert.get("amount"))),
                        "method", Json.str(str(fert.get("method"))),
                        "tips", Json.str(str(fert.get("tips")))),
                "pest", Json.arr(pests),
                "pestSource", Json.str(pestSource),
                "tools", Json.arr(toolJson),
                "notes", Json.arr(notes));
    }

    private static String pestJson(Object name, Object symptom, Object solution, Object dosage, Object prevention) {
        return Json.obj(
                "name", Json.str(str(name)),
                "symptom", Json.str(str(symptom)),
                "solution", Json.str(str(solution)),
                "dosage", Json.str(str(dosage)),
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
