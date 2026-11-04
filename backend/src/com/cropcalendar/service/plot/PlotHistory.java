package com.cropcalendar.service.plot;

import com.cropcalendar.util.AppTime;
import com.cropcalendar.util.Json;
import java.time.ZoneId;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地块历史与地力档案（V5）。
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
            String now = AppTime.now().toString();
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

    // ============================================================
    // T19 茬口冲突校验（纯函数）
    //
    // 设计口径：不在服务端另存一份"地块档案"，而是前端把 MyCropsStore 里已有的
    // 作物记录传上来，直接判断新播期能不能排 —— 数据源只有一个，不会出现
    // "档案说空着、作物说种着"的两套账。
    // ============================================================

    /** 一条已有种植季。harvest 为估算时 estimatedHarvest=true，文案里要说明。 */
    public record Season(String plotId, String plotName, String cropName,
                         LocalDate sow, LocalDate harvest, boolean estimatedHarvest) {
    }

    /** 一条校验结论，level 沿用播种校验的三级口径：block / warn / ok。 */
    public record Item(String code, String level, String title, String detail, String suggestion) {
    }

    public record ScheduleCheck(String verdict, List<Item> items, String earliestSowDate) {
        public static ScheduleCheck pass() {
            return new ScheduleCheck("pass", List.of(), null);
        }
    }

    /** 收获后留几天整地，才能排下一季。 */
    private static final int LAND_PREP_DAYS = 7;
    /** 记录没写收获期时的兜底生育期。 */
    private static final int DEFAULT_GROWING_DAYS = 120;
    /** 农忙窗口：播种前后、收获前后，这几天要集中出工。 */
    private static final int SOW_BUSY_BEFORE = 3;
    private static final int SOW_BUSY_AFTER = 10;
    private static final int HARVEST_BUSY_BEFORE = 10;
    private static final int HARVEST_BUSY_AFTER = 3;

    /**
     * 茬口校验：同地块重叠阻断、同地块连作提醒、跨地块农忙重叠提醒。
     *
     * @param cropName  新播作物（标准名）
     * @param sowDate   新播期 yyyy-MM-dd
     * @param plotId    关联地块 id；为空表示没关联地块，直接通过
     * @param totalDays 新作物的全生育期，&lt;=0 时按 120 天估算
     * @param records   该用户已有的作物记录（前端上传）
     */
    public static ScheduleCheck checkSchedule(String cropName, String sowDate, String plotId,
                                              int totalDays, List<Map<String, Object>> records) {
        if (plotId == null || plotId.isBlank() || sowDate == null || sowDate.isBlank()) {
            return ScheduleCheck.pass();
        }
        LocalDate sow;
        try {
            sow = LocalDate.parse(sowDate);
        } catch (Exception e) {
            return ScheduleCheck.pass();
        }
        int days = totalDays > 0 ? totalDays : DEFAULT_GROWING_DAYS;
        LocalDate newHarvest = sow.plusDays(days);
        String crop = cropName == null ? "" : cropName;

        List<Season> seasons = normalize(records);
        List<Item> items = new ArrayList<>();
        String earliest = null;

        // ---- 1. 同一地块：新播期与上一季收获期重叠 → 阻断 ----
        Season blocker = null;
        for (Season s : seasons) {
            if (!plotId.equals(s.plotId())) continue;
            // 同一季重排（同作物同播期）不算冲突，否则刷新日历会被自己拦住
            if (s.cropName().equals(crop) && s.sow().equals(sow)) continue;
            if (!sow.isAfter(s.harvest()) && !newHarvest.isBefore(s.sow())) {
                if (blocker == null || s.harvest().isAfter(blocker.harvest())) blocker = s;
            }
        }
        if (blocker != null) {
            LocalDate earliestDate = blocker.harvest().plusDays(LAND_PREP_DAYS);
            earliest = earliestDate.toString();
            long overlap = ChronoUnit.DAYS.between(
                    sow.isAfter(blocker.sow()) ? sow : blocker.sow(),
                    newHarvest.isBefore(blocker.harvest()) ? newHarvest : blocker.harvest()) + 1;
            String plotLabel = blocker.plotName().isEmpty() ? "该地块" : "「" + blocker.plotName() + "」";
            items.add(new Item("plot-occupied", "block", "这块地还没腾出来",
                    plotLabel + "上还有一季" + blocker.cropName() + "：" + blocker.sow() + " 播种、"
                            + blocker.harvest() + " 收获。你要排的" + crop + "从 " + sow + " 起，两季重叠 "
                            + Math.max(overlap, 0) + " 天。"
                            + (blocker.estimatedHarvest() ? "（该记录未填收获期，按生育期估算）" : ""),
                    "最早可排到 " + earliestDate + "（收获后留 " + LAND_PREP_DAYS + " 天整地）"));
        }

        // ---- 2. 同一地块连作 → 提醒，不阻断 ----
        Season sameCrop = null;
        for (Season s : seasons) {
            if (!plotId.equals(s.plotId())) continue;
            if (!s.cropName().equals(crop) || s.cropName().isEmpty()) continue;
            if (s.sow().equals(sow)) continue;
            if (sameCrop == null || s.sow().isAfter(sameCrop.sow())) sameCrop = s;
        }
        if (sameCrop != null) {
            String plotLabel = sameCrop.plotName().isEmpty() ? "该地块" : "「" + sameCrop.plotName() + "」";
            items.add(new Item("continuous-cropping", "warn", "同一地块连作" + crop,
                    plotLabel + "上一季（" + sameCrop.sow() + "～" + sameCrop.harvest() + "）种的就是"
                            + crop + "。连作容易让土传病害累积、养分失衡。",
                    "换一茬豆科或禾本科作物轮作；确实要连作，至少换抗病品种并增施有机肥"));
        }

        // ---- 3. 跨地块农忙重叠 → 提醒，不阻断 ----
        // 只比"集中出工"的那几周，不是比整个生育期 —— 整季重叠是常态，全报等于没报。
        for (Season s : seasons) {
            if (plotId.equals(s.plotId())) continue;
            LocalDate[] sowBusy = {s.sow().minusDays(SOW_BUSY_BEFORE), s.sow().plusDays(SOW_BUSY_AFTER)};
            LocalDate[] harvestBusy = {s.harvest().minusDays(HARVEST_BUSY_BEFORE), s.harvest().plusDays(HARVEST_BUSY_AFTER)};
            String phase = null;
            LocalDate[] hit = null;
            if (!sow.isBefore(sowBusy[0]) && !sow.isAfter(sowBusy[1])) {
                phase = "播种";
                hit = sowBusy;
            } else if (!sow.isBefore(harvestBusy[0]) && !sow.isAfter(harvestBusy[1])) {
                phase = "收获";
                hit = harvestBusy;
            }
            if (phase == null) continue;
            String plotLabel = s.plotName().isEmpty() ? "另一块地" : "「" + s.plotName() + "」";
            items.add(new Item("cross-plot-busy", "warn", "另一块地同期也有重活",
                    "你计划的 " + sow + " 前后，" + plotLabel + "上的" + s.cropName()
                            + "正好在" + phase + "（" + hit[0] + "～" + hit[1] + "），两块地同时开工人手可能排不开。",
                    "错开 3~5 天，或提前安排帮工与农机"));
            break;
        }

        String verdict = "pass";
        for (Item it : items) {
            if ("block".equals(it.level())) {
                verdict = "block";
                break;
            }
            if ("warn".equals(it.level())) verdict = "warn";
        }
        return new ScheduleCheck(verdict, items, earliest);
    }

    /** 把前端传来的记录整理成季节区间；缺收获期时按作物生育期估算并标记。 */
    static List<Season> normalize(List<Map<String, Object>> records) {
        List<Season> out = new ArrayList<>();
        if (records == null) return out;
        for (Map<String, Object> r : records) {
            if (r == null) continue;
            LocalDate sow = parseDate(str(r.get("sowDate")));
            if (sow == null) continue;
            String cropName = str(r.get("cropName"));
            LocalDate harvest = parseDate(str(r.get("harvestDate")));
            boolean estimated = false;
            if (harvest == null) {
                harvest = sow.plusDays(growingDays(cropName));
                estimated = true;
            }
            if (harvest.isBefore(sow)) harvest = sow;
            out.add(new Season(str(r.get("plotId")), str(r.get("plotName")),
                    cropName, sow, harvest, estimated));
        }
        return out;
    }

    private static int growingDays(String cropName) {
        if (cropName == null || cropName.isEmpty()) return DEFAULT_GROWING_DAYS;
        try {
            String std = com.cropcalendar.service.crop.CropRegistry.standardName(cropName);
            com.cropcalendar.service.crop.CropProperties.Props p =
                    com.cropcalendar.service.crop.CropProperties.of(std);
            if (p != null && p.totalDays() > 0) return p.totalDays();
        } catch (Exception ignored) {
            // 属性库读不到就按兜底生育期
        }
        return DEFAULT_GROWING_DAYS;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
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
        if (date == null || date.length() < 4) return AppTime.today().getYear();
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (Exception e) {
            return AppTime.today().getYear();
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