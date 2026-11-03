package com.cropcalendar.service.plot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T19 茬口冲突校验单测（纯函数，不读文件、不联网）。
 *
 *   java -cp backend/build/classes:backend/build/test-classes com.cropcalendar.service.plot.PlotScheduleTest
 */
public class PlotScheduleTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---- 1. 同地块：玉米 6/1 播、10/1 收，再排 9/15 小麦 → 阻断 ----
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(rec("p1", "一号地", "玉米", "2026-06-01", "2026-10-01"));

        PlotHistory.ScheduleCheck c1 = PlotHistory.checkSchedule(
                "小麦", "2026-09-15", "p1", 240, records);
        eq("block", c1.verdict(), "同地块重叠应阻断");
        check(c1.items().size() >= 1, "阻断时应给出条目");
        PlotHistory.Item block = c1.items().get(0);
        eq("plot-occupied", block.code(), "阻断条码");
        check(block.detail().contains("玉米"), "应写明冲突作物，实际 " + block.detail());
        check(block.detail().contains("2026-10-01"), "应写明冲突收获期，实际 " + block.detail());
        check(block.detail().contains("2026-09-15"), "应写明新播期，实际 " + block.detail());
        eq("2026-10-08", c1.earliestSowDate(), "建议最早播期应为收获后 7 天");
        check(block.suggestion().contains("2026-10-08"), "建议文案应带最早播期，实际 " + block.suggestion());

        // ---- 2. 没关联地块 → 直接通过，不额外报错 ----
        PlotHistory.ScheduleCheck c2 = PlotHistory.checkSchedule(
                "小麦", "2026-09-15", null, 240, records);
        eq("pass", c2.verdict(), "无地块应通过");
        eq(0, c2.items().size(), "无地块不应产生任何条目");
        PlotHistory.ScheduleCheck c2b = PlotHistory.checkSchedule(
                "小麦", "2026-09-15", "  ", 240, records);
        eq("pass", c2b.verdict(), "空地块 id 应通过");

        // ---- 3. 同地块但上一季已收完 → 通过 ----
        PlotHistory.ScheduleCheck c3 = PlotHistory.checkSchedule(
                "小麦", "2026-10-20", "p1", 240, records);
        eq("pass", c3.verdict(), "腾地后应通过，实际 " + c3.verdict());
        eq(0, c3.items().size(), "腾地后不应有条目");

        // ---- 4. 同地块连作 → 提醒不阻断 ----
        List<Map<String, Object>> sameCrop = new ArrayList<>();
        sameCrop.add(rec("p1", "一号地", "小麦", "2025-10-01", "2026-06-01"));
        PlotHistory.ScheduleCheck c4 = PlotHistory.checkSchedule(
                "小麦", "2026-10-10", "p1", 240, sameCrop);
        eq("warn", c4.verdict(), "连作应提醒不阻断");
        check(c4.items().stream().anyMatch(i -> "continuous-cropping".equals(i.code())),
                "应给连作提醒，实际 " + c4.items());
        check(c4.earliestSowDate() == null, "连作不需要给改期建议");

        // ---- 5. 跨地块农忙重叠 → 提醒 ----
        List<Map<String, Object>> twoPlots = new ArrayList<>();
        twoPlots.add(rec("p1", "一号地", "玉米", "2026-06-01", "2026-10-01"));
        twoPlots.add(rec("p2", "二号地", "小麦", "2026-10-05", "2027-06-01"));
        // 新季（油菜）排在小麦播种期附近（10-05 前 3 天窗口内），且不是 p2
        PlotHistory.ScheduleCheck c5 = PlotHistory.checkSchedule(
                "油菜", "2026-10-03", "p1", 120, twoPlots);
        check(c5.items().stream().anyMatch(i -> "cross-plot-busy".equals(i.code())),
                "应给跨地块农忙提醒，实际 " + c5.items());
        check(c5.verdict().equals("warn") || c5.verdict().equals("block"),
                "跨地块提醒至少应为 warn，实际 " + c5.verdict());

        // ---- 6. 同一季重排（同作物同播期）不应被自己拦住 ----
        PlotHistory.ScheduleCheck c6 = PlotHistory.checkSchedule(
                "玉米", "2026-06-01", "p1", 120, records);
        eq("pass", c6.verdict(), "同一季重排应通过，实际 " + c6.verdict());

        // ---- 7. 缺收获期 → 按生育期估算并标注 ----
        List<Map<String, Object>> noHarvest = new ArrayList<>();
        noHarvest.add(rec("p1", "一号地", "玉米", "2026-06-01", null));
        PlotHistory.ScheduleCheck c7 = PlotHistory.checkSchedule(
                "小麦", "2026-08-01", "p1", 240, noHarvest);
        eq("block", c7.verdict(), "缺收获期也要按生育期估算后判定");
        check(c7.items().get(0).detail().contains("估算"), "应说明收获期是估算的，实际 " + c7.items().get(0).detail());

        // ---- 8. 记录结构不合法时不影响判定 ----
        List<Map<String, Object>> junk = new ArrayList<>();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("cropName", "玉米");
        bad.put("sowDate", "不是日期");
        junk.add(bad);
        junk.add(null);
        PlotHistory.ScheduleCheck c8 = PlotHistory.checkSchedule(
                "小麦", "2026-09-15", "p1", 240, junk);
        eq("pass", c8.verdict(), "脏记录应被忽略而不是报错");

        // ---- 9. 收获期早于播期时按当天算，不产生负区间 ----
        List<Map<String, Object>> reversed = new ArrayList<>();
        reversed.add(rec("p1", "一号地", "玉米", "2026-10-01", "2026-06-01"));
        PlotHistory.ScheduleCheck c9 = PlotHistory.checkSchedule(
                "小麦", "2026-10-10", "p1", 240, reversed);
        eq("pass", c9.verdict(), "倒置日期的记录应按当天收获处理");

        System.out.println("PlotScheduleTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static Map<String, Object> rec(String plotId, String plotName, String crop,
                                           String sow, String harvest) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plotId", plotId);
        m.put("plotName", plotName);
        m.put("cropName", crop);
        m.put("sowDate", sow);
        if (harvest != null) m.put("harvestDate", harvest);
        return m;
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg);
        }
    }

    private static void eq(String expected, String actual, String msg) {
        check(expected == null ? actual == null : expected.equals(actual),
                msg + "，期望「" + expected + "」实际「" + actual + "」");
    }

    private static void eq(int expected, int actual, String msg) {
        check(expected == actual, msg + "，期望 " + expected + " 实际 " + actual);
    }
}
