package com.cropcalendar.service.plot;

import com.cropcalendar.util.Json;
import java.time.ZoneId;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地块历史与地力档案（T26）。
 *
 * 按地块聚合历年种植记录：种过什么、播期、收获期、产量。
 * 用地块历史支撑两个因子：连作检测与产量趋势（地力）。
 *
 * 存储路径：var/plot-history/&lt;userId&gt;.json，写入用临时文件 + 原子替换。
 */
public final class PlotHistory {
    private static final java.time.ZoneId SH = java.time.ZoneId.of("Asia/Shanghai");

    private static final int MAX_RECORDS = 500;
    private static final Object LOCK = new Object();

    private PlotHistory() {
    }

    private static Path pathOf(String userId) {
        return Path.of("var/plot-history", userId + ".json");
    }

    private static List<Map<String, Object>> readAll(String userId) {
        Path p = pathOf(userId);
        try {
            if (!Files.exists(p)) return new ArrayList<>();
            String text = Files.readString(p, StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (root instanceof Map<?, ?> m && m.get("records") instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> rm) out.add((Map<String, Object>) rm);
                }
                return out;
            }
        } catch (Exception e) {
            System.err.println("[plot-history] 读取失败 " + userId + "：" + e.getMessage());
        }
        return new ArrayList<>();
    }

    private static void writeAll(String userId, List<Map<String, Object>> records) throws IOException {
        Path p = pathOf(userId);
        Files.createDirectories(p.getParent());
        List<String> arr = new ArrayList<>();
        for (Map<String, Object> r : records) {
            arr.add(Json.obj(
                    "recordId", Json.str(str(r.get("recordId"))),
                    "plotId", Json.str(str(r.get("plotId"))),
                    "plotName", Json.str(str(r.get("plotName"))),
                    "cropId", Json.str(str(r.get("cropId"))),
                    "cropName", Json.str(str(r.get("cropName"))),
                    "sowDate", Json.str(str(r.get("sowDate"))),
                    "harvestDate", Json.str(str(r.get("harvestDate"))),
                    "yield", r.get("yield") != null ? String.valueOf(r.get("yield")) : "null",
                    "unit", Json.str(str(r.get("unit"))),
                    "year", String.valueOf(r.get("year")),
                    "note", Json.str(str(r.get("note"))),
                    "createdAt", Json.str(str(r.get("createdAt")))));
        }
        String json = Json.obj("records", Json.arr(arr));
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 添加种植记录，返回完整记录 */
    public static Map<String, Object> addRecord(String userId, Map<String, Object> record) {
        synchronized (LOCK) {
            List<Map<String, Object>> all = readAll(userId);
            String recordId = "rec_" + Long.toString(System.currentTimeMillis(), 36)
                    + "_" + Integer.toString((int) (Math.random() * 100000), 36);
            String now = java.time.LocalDateTime.now(SH).toString();
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("recordId", recordId);
            rec.put("plotId", str(record.get("plotId")));
            rec.put("plotName", str(record.get("plotName")));
            rec.put("cropId", str(record.get("cropId")));
            rec.put("cropName", str(record.get("cropName")));
            rec.put("sowDate", str(record.get("sowDate")));
            rec.put("harvestDate", str(record.get("harvestDate")));
            rec.put("yield", record.get("yield"));
            rec.put("unit", str(record.get("unit")));
            rec.put("year", record.get("year") != null ? record.get("year") : extractYear(str(record.get("sowDate"))));
            rec.put("note", str(record.get("note")));
            rec.put("createdAt", now);
            all.add(0, rec);
            if (all.size() > MAX_RECORDS) all = new ArrayList<>(all.subList(0, MAX_RECORDS));
            try {
                writeAll(userId, all);
            } catch (IOException e) {
                System.err.println("[plot-history] 写入失败 " + userId + "：" + e.getMessage());
                return null;
            }
            return rec;
        }
    }

    /** 查询用户所有种植记录（跨地块），按年份倒序 */
    public static List<Map<String, Object>> listAll(String userId) {
        List<Map<String, Object>> all = readAll(userId);
        all.sort((a, b) -> Integer.compare(intVal(b.get("year")), intVal(a.get("year"))));
        return all;
    }

    /** 按地块查历史，按年份倒序 */
    public static List<Map<String, Object>> listByPlot(String userId, String plotId) {
        List<Map<String, Object>> all = readAll(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : all) {
            if (plotId.equals(str(r.get("plotId")))) result.add(r);
        }
        result.sort((a, b) -> Integer.compare(intVal(b.get("year")), intVal(a.get("year"))));
        return result;
    }

    /** 补录产量 */
    public static boolean updateYield(String userId, String recordId, double yield) {
        synchronized (LOCK) {
            List<Map<String, Object>> all = readAll(userId);
            boolean found = false;
            for (Map<String, Object> r : all) {
                if (recordId.equals(str(r.get("recordId")))) {
                    r.put("yield", yield);
                    found = true;
                    break;
                }
            }
            if (found) {
                try {
                    writeAll(userId, all);
                } catch (IOException e) {
                    return false;
                }
            }
            return found;
        }
    }

    /** 连作检测：连续两年种同一作物 */
    public static List<Map<String, Object>> detectContinuousCropping(List<Map<String, Object>> records) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (int i = 0; i < records.size() - 1; i++) {
            int y1 = intVal(records.get(i).get("year"));
            int y2 = intVal(records.get(i + 1).get("year"));
            String c1 = str(records.get(i).get("cropName"));
            String c2 = str(records.get(i + 1).get("cropName"));
            if (y1 - y2 == 1 && !c1.isEmpty() && c1.equals(c2)) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("years", y2 + "～" + y1);
                w.put("crop", c1);
                w.put("risk", "连作可能导致土传病害加重、土壤养分失衡，建议轮作换茬");
                warnings.add(w);
            }
        }
        return warnings;
    }

    /** 产量趋势分析：至少两年有产量才判断，否则只提示补录 */
    public static Map<String, Object> yieldTrend(List<Map<String, Object>> records) {
        List<Map<String, Object>> withYield = new ArrayList<>();
        for (Map<String, Object> r : records) {
            Object y = r.get("yield");
            if (y != null && doubleVal(y) > 0) withYield.add(r);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (withYield.size() < 2) {
            result.put("available", false);
            result.put("hint", "补录产量后可看趋势");
            return result;
        }
        withYield.sort((a, b) -> Integer.compare(intVal(a.get("year")), intVal(b.get("year"))));
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> r : withYield) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("year", intVal(r.get("year")));
            p.put("yield", doubleVal(r.get("yield")));
            p.put("crop", str(r.get("cropName")));
            points.add(p);
        }
        double first = doubleVal(withYield.get(0).get("yield"));
        double last = doubleVal(withYield.get(withYield.size() - 1).get("yield"));
        String direction = last > first * 1.05 ? "up" : last < first * 0.95 ? "down" : "stable";
        result.put("available", true);
        result.put("direction", direction);
        result.put("changePct", first > 0 ? Math.round((last - first) / first * 1000) / 10.0 : 0);
        result.put("points", points);
        return result;
    }

    /**
     * 纯分析（V5 T20）：前端把 MyCropsStore 里的记录传上来，直接算连作与产量趋势，
     * 不在服务端另存一份 —— 地块历史的数据源只有一个，避免两套档案对不上。
     */
    public static Map<String, Object> analyze(List<Map<String, Object>> records) {
        List<Map<String, Object>> sorted = new ArrayList<>(records == null ? List.of() : records);
        // 年份倒序；同一年按播期倒序，保证连作检测比较的是相邻两季
        sorted.sort((a, b) -> {
            int byYear = Integer.compare(intVal(b.get("year")), intVal(a.get("year")));
            if (byYear != 0) return byYear;
            return str(b.get("sowDate")).compareTo(str(a.get("sowDate")));
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", sorted);
        out.put("continuousWarnings", detectContinuousCropping(sorted));
        out.put("yieldTrend", yieldTrend(sorted));
        out.put("total", sorted.size());
        return out;
    }

    /** 导出 CSV（服务端档案） */
    public static String exportCsv(String userId, String plotId) {
        return toCsv(listByPlot(userId, plotId));
    }

    /** 纯转换，单测直接调：字段含逗号/引号时按 RFC 4180 转义。 */
    static String toCsv(List<Map<String, Object>> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("年份,作物,播种期,收获期,产量,单位,备注\n");
        for (Map<String, Object> r : records) {
            sb.append(intVal(r.get("year"))).append(",")
                    .append(csvEscape(str(r.get("cropName")))).append(",")
                    .append(csvEscape(str(r.get("sowDate")))).append(",")
                    .append(csvEscape(str(r.get("harvestDate")))).append(",")
                    .append(r.get("yield") != null ? doubleVal(r.get("yield")) : "").append(",")
                    .append(csvEscape(str(r.get("unit")))).append(",")
                    .append(csvEscape(str(r.get("note")))).append("\n");
        }
        return sb.toString();
    }

    private static String csvEscape(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static int extractYear(String date) {
        if (date == null || date.length() < 4) return java.time.LocalDate.now(SH).getYear();
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (Exception e) {
            return java.time.LocalDate.now(SH).getYear();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double doubleVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}