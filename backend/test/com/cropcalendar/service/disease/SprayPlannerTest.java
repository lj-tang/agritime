package com.cropcalendar.service.disease;

import com.cropcalendar.service.disease.SprayPlanner.DayWeather;
import com.cropcalendar.service.disease.SprayPlanner.HarvestShift;
import com.cropcalendar.service.disease.SprayPlanner.SprayDay;

import java.time.LocalDate;
import java.util.List;

/**
 * 病害打药排期回归（F02）：不依赖网络，只测纯计算。
 * 覆盖：打分三项、同分取早、缺数不给分、整段缺数降级、
 *       安全间隔日计算、多采收节点逐一后移、已足够晚的不动。
 */
public class SprayPlannerTest {

    private static int checks = 0;

    public static void main(String[] args) {
        // ---- 打分：雨≤2 +40，风≤4 +20，日均温 15~28 +40 ----
        SprayDay perfect = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", 0.0, 2.0, 22.0)), 2, 4, 15, 28);
        eq(100, perfect.score(), "三项都满足=100分");
        eq("2026-11-26", perfect.date(), "选这一天");
        check(!perfect.noWeather(), "有天气不算降级");

        SprayDay rainy = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", 8.0, 2.0, 22.0)), 2, 4, 15, 28);
        eq(60, rainy.score(), "雨超标丢40分");

        SprayDay windyCold = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", 0.0, 9.0, 5.0)), 2, 4, 15, 28);
        eq(40, windyCold.score(), "风与温度都超标只剩雨的40分");

        // ---- 选最高分，同分选更早 ----
        SprayDay best = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", 0.0, 1.0, 20.0),   // 100
                new DayWeather("2026-11-27", 12.0, 8.0, 2.0),  // 0
                new DayWeather("2026-11-28", 0.0, 1.0, 21.0)), // 100
                2, 4, 15, 28);
        eq("2026-11-26", best.date(), "同分(100)选更早的 26 日");

        SprayDay pickHigh = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", 12.0, 8.0, 2.0),  // 0
                new DayWeather("2026-11-27", 0.0, 1.0, 20.0)), // 100
                2, 4, 15, 28);
        eq("2026-11-27", pickHigh.date(), "低分日在前也选高分的 27 日");

        // ---- 缺数不给分，不当 0 也不冒充达标 ----
        SprayDay partial = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", null, 2.0, 22.0)), 2, 4, 15, 28);
        eq(60, partial.score(), "雨缺测只拿风和温度的60分");

        SprayDay none = SprayPlanner.pickSprayDay(List.of(
                new DayWeather("2026-11-26", null, null, null)), 2, 4, 15, 28);
        eq(0, none.score(), "三项全缺=0分");
        check(none.noWeather(), "整段缺数标记 noWeather，供上层降级提示");
        check(none.reason().contains("临近"), "缺数时提示临近再看预报");

        SprayDay empty = SprayPlanner.pickSprayDay(List.of(), 2, 4, 15, 28);
        check(empty.noWeather(), "候选为空也降级，不抛异常");

        // ---- 安全间隔日 ----
        eq("2026-12-10", SprayPlanner.earliestHarvest("2026-11-26", 14), "喷药日+14天");
        eq("2026-12-08", SprayPlanner.earliestHarvest("2026-11-26", 12), "喷药日+12天");
        eq("2026-11-26", SprayPlanner.earliestHarvest("2026-11-26", 0), "0天间隔=喷药当天");

        // ---- 多采收节点后移：早于 earliest 的都后移 ----
        List<HarvestShift> shifts = SprayPlanner.shiftHarvests(
                List.of("2026-12-06", "2026-12-10", "2026-12-20"), "2026-12-10");
        eq(1, shifts.size(), "三个节点只有 12-06 需要后移");
        eq("2026-12-06", shifts.get(0).originalDate(), "记住原日期");
        eq("2026-12-10", shifts.get(0).newDate(), "后移到间隔期截止日");
        eq(4, shifts.get(0).days(), "后移4天");

        List<HarvestShift> all = SprayPlanner.shiftHarvests(
                List.of("2026-12-01", "2026-12-05", "2026-12-09"), "2026-12-10");
        eq(3, all.size(), "三个都早于截止日，全部后移");
        eq("2026-12-10", all.get(2).newDate(), "第三个也落到同一截止日");

        eq(0, SprayPlanner.shiftHarvests(List.of("2026-12-11", "2026-12-15"), "2026-12-10").size(),
                "已晚于截止日的不动");
        eq(0, SprayPlanner.shiftHarvests(List.of(), "2026-12-10").size(), "无采收节点返回空");

        // 乱序 + 非法日期不参与
        List<HarvestShift> messy = SprayPlanner.shiftHarvests(
                List.of("2026-12-20", "bad-date", "2026-12-01"), "2026-12-10");
        eq(1, messy.size(), "非法日期跳过，晚的不要，只处理早的那个");

        // ---- 诊断后 5 天序列 ----
        List<String> days = SprayPlanner.nextDays("2026-11-25", 5);
        eq(5, days.size(), "扫描5天");
        eq("2026-11-26", days.get(0), "从诊断次日起");
        eq("2026-11-30", days.get(4), "到第5天");

        System.out.println("SprayPlannerTest: " + checks + " 项通过");
    }

    private static void eq(Object expected, Object actual, String msg) {
        checks++;
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError(msg + " —— 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void check(boolean ok, String msg) {
        checks++;
        if (!ok) throw new AssertionError(msg);
    }
}
