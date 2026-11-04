package com.cropcalendar.service.aqua;

/**
 * T20 养殖成本核算单测：估算模式与实际模式都要和手算对得上。
 */
public class AquaCostTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // ---- 1. 估算模式：草鱼 10 亩，全部用默认参数 ----
        AquaCost.Result r = AquaCost.estimate("grass_carp", AquaCost.Inputs.empty());
        eq(10000.0, r.stockQty(), "放养量 = 1000 尾/亩 × 10 亩");
        eq(11250.0, r.harvestKg(), "产量 = 10000 × 75% × 1.5kg");
        eq(20250.0, r.feedKg(), "饲料 = 11250 × 饲料系数 1.8");
        eq(6000.0, r.seedCost(), "苗种 = 10000 × 0.6 元");
        eq(105300.0, r.feedCost(), "饲料成本 = 20250 × 5.2 元");
        eq(3200.0, r.electricityCost(), "电费 = 320 元/亩 × 10 亩");
        eq(1800.0, r.medicineCost(), "药费 = 180 元/亩 × 10 亩");
        eq(6000.0, r.laborCost(), "人工 = 600 元/亩 × 10 亩");
        eq(9000.0, r.rentCost(), "塘租 = 900 元/亩 × 10 亩");
        eq(131300.0, r.totalCost(), "总成本");
        eq(157500.0, r.revenue(), "收入 = 11250 × 14 元");
        eq(26200.0, r.profit(), "盈亏 = 收入 - 成本");
        eq(11.67, r.breakEvenPricePerKg(), "保本价 = 131300 / 11250");
        check(r.estimated(), "全默认参数应标为估算");
        eq("estimate", r.mode(), "估算模式");
        check(!r.assumptions().isEmpty(), "估算应列出假设");

        // ---- 2. 实际模式：填了实际出塘与投喂 ----
        AquaCost.Result a = AquaCost.estimate("grass_carp", new AquaCost.Inputs(
                10.0, null, null, null, null, null, null, 16.0, null, null, null, null, 15000.0, 9000.0));
        eq(9000.0, a.harvestKg(), "产量应取实际出塘量");
        eq(15000.0, a.feedKg(), "饲料应取实际投喂量");
        eq(9000.0 * 16, a.revenue(), "收入按实际产量 × 售价");
        eq(9000.0 * 16 - (6000 + 15000 * 5.2 + 3200 + 1800 + 6000 + 9000), a.profit(), "实际模式盈亏");
        check(!a.estimated(), "填了实际数据就不再标估算");
        eq("actual", a.mode(), "实际模式");

        // ---- 3. 面积翻倍，苗种与塘租翻倍、亩均不变 ----
        AquaCost.Result big = AquaCost.estimate("grass_carp", new AquaCost.Inputs(
                20.0, null, null, null, null, null, null, null, null, null, null, null, null, null));
        eq(12000.0, big.seedCost(), "20 亩苗种应是 10 亩的两倍");
        eq(18000.0, big.rentCost(), "20 亩塘租应是 10 亩的两倍");

        // ---- 4. 用户覆盖参数 ----
        AquaCost.Result custom = AquaCost.estimate("grass_carp", new AquaCost.Inputs(
                10.0, 10000.0, 0.9, 1.5, 4.0, null, null, null, null, null, null, null, null, null));
        eq(13500.0, custom.harvestKg(), "成活率 90% 时产量 = 10000 × 0.9 × 1.5");
        eq(13500.0 * 1.5, custom.feedKg(), "自定义饲料系数 1.5");

        // ---- 5. 未知品类 ----
        check(AquaCost.estimate("no_such_fish", AquaCost.Inputs.empty()) == null, "未知品类应返回 null");

        // ---- 6. 非法成活率回退到默认值，不产生负产量 ----
        AquaCost.Result bad = AquaCost.estimate("grass_carp", new AquaCost.Inputs(
                10.0, null, 1.8, null, null, null, null, null, null, null, null, null, null, null));
        eq(0.75, bad.survivalRate(), "成活率超过 1 应回退到默认 75%");
        check(bad.harvestKg() > 0, "产量不应为负或零");

        // ---- 7. 起捕提示：要有保本价，方便和行情比 ----
        String hint = AquaCost.harvestHint("grass_carp", 10);
        check(hint != null && hint.contains("保本价"), "起捕提示应包含保本价，实际 " + hint);
        check(hint.contains("11.67"), "保本价应与核算一致，实际 " + hint);

        // ---- 8. 大闸蟹用品种自己的饲料参数 ----
        AquaCost.Result crab = AquaCost.estimate("mitten_crab", AquaCost.Inputs.empty());
        eq("大闸蟹", crab.speciesName(), "品类名");
        check(crab.feedCost() > 0 && crab.breakEvenPricePerKg() > 0, "大闸蟹应有成本与保本价");

        System.out.println("AquaCostTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg);
        }
    }

    private static void eq(double expected, double actual, String msg) {
        check(Math.abs(expected - actual) < 0.02, msg + "，期望 " + expected + " 实际 " + actual);
    }

    private static void eq(String expected, String actual, String msg) {
        check(expected == null ? actual == null : expected.equals(actual),
                msg + "，期望「" + expected + "」实际「" + actual + "」");
    }
}
