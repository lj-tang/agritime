package com.cropcalendar.service.crop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 积温播期回归（V5 T20）：不依赖网络，只测纯计算。
 * 覆盖：积温文本解析、达标日累计、缺测跳过、最晚播期倒推。
 */
public class GddAdvisorTest {

    private static int checks = 0;

    public static void main(String[] args) {
        // ---- 从作物属性文本里取所需积温 ----
        eq(2500.0, GddAdvisor.parseTargetGdd("喜温，≥10℃积温需 2500℃以上；华南可双季，北方单季"), "水稻取 2500 而不是 10");
        eq(1800.0, GddAdvisor.parseTargetGdd("≥10℃积温 1800~2200℃"), "区间取首个三位以上数值");
        eq(-1.0, GddAdvisor.parseTargetGdd("喜温，不耐霜冻"), "没标积温返回 -1");
        eq(-1.0, GddAdvisor.parseTargetGdd(null), "null 返回 -1");

        // ---- 生长下限温度：≥10℃ 里的 10，而不是积温 2500 ----
        eq(10.0, GddAdvisor.parseThreshold("喜温，≥10℃积温需 2500℃以上"), "下限温度取 10");
        eq(10.0, GddAdvisor.parseThreshold("喜温"), "没标时默认 10");

        // ---- 连续稳定通过下限温度 ----
        List<Double> rise = new ArrayList<>(List.of(5.0, 5.0, 12.0, 13.0, 14.0, 15.0, 16.0));
        eq(2, GddAdvisor.stableAbove(rise, 10, 5), "连续 5 天通过 10℃ 的第一天是索引 2");
        List<Double> gapRun = new ArrayList<>(List.of(12.0, 13.0, Double.NaN, 14.0, 15.0, 16.0, 17.0, 18.0));
        eq(3, GddAdvisor.stableAbove(gapRun, 10, 5), "缺测打断连续段，从索引 3 重新计");
        List<Double> stillCold = new ArrayList<>();
        for (int i = 0; i < 30; i++) stillCold.add(8.0);
        eq(-1, GddAdvisor.stableAbove(stillCold, 10, 5), "持续低于下限返回 -1");

        // ---- 春播 / 秋播窗口 ----
        check(GddAdvisor.isAutumnSown("秋播"), "秋播识别");
        check(GddAdvisor.isAutumnSown("秋播/冬播"), "秋冬连播识别");
        check(!GddAdvisor.isAutumnSown("春茬/秋茬"), "含春优先按春播");
        check(!GddAdvisor.isAutumnSown("春播/夏播"), "春播不是秋播");
        eq(LocalDate.of(2027, 2, 1), GddAdvisor.nextWindow(LocalDate.of(2026, 11, 3), 2, 1),
                "11 月的下一个 2-1 在明年");
        eq(LocalDate.of(2026, 2, 1), GddAdvisor.nextWindow(LocalDate.of(2026, 1, 15), 2, 1),
                "1 月的下一个 2-1 在今年");
        List<Double> cool = new ArrayList<>(List.of(18.0, 15.0, 14.0, 13.0, 12.0, 11.0));
        eq(1, GddAdvisor.stableBelow(cool, 15, 5), "连续 5 天 ≤15℃ 从索引 1 开始");
        List<Double> notEnough = new ArrayList<>(List.of(18.0, 16.0, 15.0, 14.0, 13.0, 12.0));
        eq(-1, GddAdvisor.stableBelow(notEnough, 15, 5), "只有 4 天连续不算稳定通过");

        // ---- 活动积温累计 ----
        List<Double> warm = new ArrayList<>();
        for (int i = 0; i < 200; i++) warm.add(20.0);
        GddAdvisor.Acc a = GddAdvisor.accumulate(warm, 10, 500);
        eq(49, a.day(), "20℃ 每天 10℃ 积温，500℃ 应在第 50 天（索引 49）");
        eq(0, a.missing(), "无缺测");

        List<Double> cold = new ArrayList<>();
        for (int i = 0; i < 240; i++) cold.add(8.0);
        eq(-1, GddAdvisor.accumulate(cold, 10, 500).day(), "低于基温永远不达标");

        List<Double> gaps = new ArrayList<>();
        gaps.add(Double.NaN);
        gaps.add(null);
        for (int i = 0; i < 60; i++) gaps.add(20.0);
        GddAdvisor.Acc c = GddAdvisor.accumulate(gaps, 10, 500);
        eq(2, c.missing(), "两天缺测");
        eq(51, c.day(), "跳过缺测后第 52 天达标（索引 51）");

        // ---- 最晚播期：今年初霜已过时看明年 ----
        CropProperties.Props props = new CropProperties.Props(
                "rice", "水稻", "long", "annual", "single", 120,
                "春播", "华南、长江流域", "喜温，≥10℃积温需 2500℃以上", "manual", List.of());
        GddAdvisor.Latest latest = GddAdvisor.latestSowDate(35.0, props, LocalDate.of(2026, 11, 3));
        check(latest != null, "最晚播期应有值");
        check(latest.basis().contains("120 天"), "依据应含生育期");
        check(LocalDate.parse(latest.date()).isAfter(LocalDate.of(2026, 11, 3)), "最晚播期应在今天之后");
        check(latest.basis().contains("初霜"), "依据应说明初霜");

        CropProperties.Props wheat = new CropProperties.Props(
                "wheat", "小麦", "long", "annual", "single", 230,
                "秋播", "黄淮海平原", "耐寒，越冬期可耐 -10℃", "manual", List.of());
        check(GddAdvisor.latestSowDate(34.8, wheat, LocalDate.of(2026, 11, 3)) == null,
                "秋播作物不给初霜倒推的最晚播期");

        System.out.println("[GddAdvisorTest] 通过 " + checks + " 项断言");
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
