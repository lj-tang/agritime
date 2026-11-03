package com.cropcalendar.service.plot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地块历史回归（V5 T20）：连作、产量趋势、排序、CSV 转义。
 * 不依赖文件与网络，直接测纯函数。
 */
public class PlotHistoryTest {

    private static int checks = 0;

    public static void main(String[] args) {
        // ---- 1. 连续两年同作物 + 产量下降 ----
        List<Map<String, Object>> recs = new ArrayList<>();
        recs.add(rec("2024", "番茄", "2024-03-01", 1000.0));
        recs.add(rec("2025", "番茄", "2025-03-05", 900.0));
        Map<String, Object> a = PlotHistory.analyze(recs);
        eq(2, a.get("total"), "共 2 条记录");
        eq(1, warnings(a).size(), "连续两年同作物应提示连作");
        Map<?, ?> w = (Map<?, ?>) warnings(a).get(0);
        eq("番茄", w.get("crop"), "连作作物");
        check(String.valueOf(w.get("risk")).contains("轮作"), "风险说明应建议轮作");

        Map<?, ?> trend = (Map<?, ?>) a.get("yieldTrend");
        eq(true, trend.get("available"), "两年有产量应可看趋势");
        eq("down", trend.get("direction"), "1000→900 应判下降");
        eq(-10.0, trend.get("changePct"), "变化 -10%");

        // ---- 2. 轮作不报连作 ----
        List<Map<String, Object>> diff = new ArrayList<>();
        diff.add(rec("2024", "番茄", "2024-03-01", 1000.0));
        diff.add(rec("2025", "玉米", "2025-04-01", 1200.0));
        eq(0, warnings(PlotHistory.analyze(diff)).size(), "换作物不报连作");

        // ---- 3. 隔年同作物不算连作 ----
        List<Map<String, Object>> gap = new ArrayList<>();
        gap.add(rec("2023", "番茄", "2023-03-01", 1000.0));
        gap.add(rec("2025", "番茄", "2025-03-01", 1000.0));
        eq(0, warnings(PlotHistory.analyze(gap)).size(), "年份不连续不算连作");

        // ---- 4. 无产量不做趋势 ----
        List<Map<String, Object>> noYield = new ArrayList<>();
        noYield.add(rec("2024", "番茄", "2024-03-01", null));
        noYield.add(rec("2025", "番茄", "2025-03-01", null));
        Map<?, ?> t2 = (Map<?, ?>) PlotHistory.analyze(noYield).get("yieldTrend");
        eq(false, t2.get("available"), "无产量不做趋势");
        check(String.valueOf(t2.get("hint")).contains("补录产量"), "应提示补录产量");

        // ---- 5. 排序：年份倒序，同年播期倒序 ----
        List<Map<String, Object>> mixed = new ArrayList<>();
        mixed.add(rec("2024", "番茄", "2024-03-01", null));
        mixed.add(rec("2025", "玉米", "2025-04-01", null));
        mixed.add(rec("2025", "小麦", "2025-10-01", null));
        List<?> rows = (List<?>) PlotHistory.analyze(mixed).get("records");
        eq("小麦", ((Map<?, ?>) rows.get(0)).get("cropName"), "同年播期晚的在前");
        eq("玉米", ((Map<?, ?>) rows.get(1)).get("cropName"), "同年播期早的在后");
        eq("番茄", ((Map<?, ?>) rows.get(2)).get("cropName"), "年份早的排在后面");

        // ---- 6. CSV 转义 ----
        List<Map<String, Object>> csvRecs = new ArrayList<>();
        csvRecs.add(rec("2024", "番茄,大果", "2024-03-01", 1000.0));
        String csv = PlotHistory.toCsv(csvRecs);
        check(csv.startsWith("年份,作物"), "CSV 应有表头");
        check(csv.contains("\"番茄,大果\""), "含逗号的字段应加引号");
        check(csv.contains(",1000.0,"), "产量应输出");

        System.out.println("[PlotHistoryTest] 通过 " + checks + " 项断言");
    }

    private static Map<String, Object> rec(String year, String crop, String sow, Double yield) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordId", "r_" + year + "_" + crop);
        m.put("plotId", "p1");
        m.put("cropName", crop);
        m.put("cropId", crop);
        m.put("sowDate", sow);
        m.put("year", Integer.parseInt(year));
        m.put("yield", yield);
        m.put("unit", "kg");
        m.put("note", "");
        return m;
    }

    private static List<?> warnings(Map<String, Object> a) {
        Object v = a.get("continuousWarnings");
        return v instanceof List<?> l ? l : List.of();
    }

    private static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
