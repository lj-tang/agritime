package com.cropcalendar.service.aqua;

import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖成本与产出核算（V5 T20）。
 *
 * 口径：
 *   放养量 = 亩密度 × 面积（或用户直接给总尾数）
 *   产量   = 放养量 × 成活率 × 出塘规格
 *   饲料量 = 产量 × 饲料系数
 *   成本   = 苗种 + 饲料 + 电费 + 药费 + 人工 + 塘租
 *   保本价 = 总成本 ÷ 产量
 *
 * 两件事必须分清：
 *   · 没有实际出塘/投喂数据时，全部走默认参数，标记 estimated=true；
 *   · 用户填了实际投喂量与出塘量，就用实际值算，标记 estimated=false。
 *   两者不能混在一起，否则"估算"会被当成"账目"。
 */
public final class AquaCost {

    private static final String[] PATHS = {
            "../data/industries/aquaculture/cost-model.json",
            "data/industries/aquaculture/cost-model.json",
            "/agritime/data/industries/aquaculture/cost-model.json"
    };
    private static final double MIN_AREA = 0.1;
    private static final double MAX_AREA = 1000;

    private static volatile Map<String, Object> MODEL;

    private AquaCost() {
    }

    /** 用户可覆盖的参数，null 表示用默认值。 */
    public record Inputs(Double areaMu, Double stockQty, Double survivalRate, Double fcr,
                         Double feedPricePerKg, Double seedPricePerFish, Double harvestSizeKg,
                         Double marketPricePerKg, Double electricityPerMu, Double medicinePerMu,
                         Double laborPerMu, Double rentPerMu, Double actualFeedKg,
                         Double actualHarvestKg) {
        public static Inputs empty() {
            return new Inputs(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record Result(String speciesId, String speciesName, String mode,
                         double areaMu, double stockQty, double survivalRate, double harvestKg,
                         double feedKg, double seedCost, double feedCost, double electricityCost,
                         double medicineCost, double laborCost, double rentCost, double totalCost,
                         double revenue, double profit, double breakEvenPricePerKg,
                         boolean estimated, List<String> assumptions) {
    }

    /**
     * @param speciesId 品类 id（grass_carp / common_carp / mitten_crab）
     * @return 未知品类返回 null，由调用方回 400
     */
    public static Result estimate(String speciesId, Inputs in) throws Exception {
        Map<String, Object> model = model();
        Map<?, ?> species = model.get("species") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> sp = species.get(speciesId) instanceof Map<?, ?> s ? s : null;
        if (sp == null) return null;
        Map<?, ?> defaults = model.get("defaults") instanceof Map<?, ?> d ? d : Map.of();

        List<String> assumptions = new ArrayList<>();
        boolean estimated = in.actualFeedKg() == null && in.actualHarvestKg() == null;

        double areaMu = clamp(pick(in.areaMu(), defaults.get("areaMu"), 10), MIN_AREA, MAX_AREA);
        double survival = rate(pick(in.survivalRate(), defaults.get("survivalRate"), 0.75), 0.75);
        double fcr = positive(pick(in.fcr(), firstNonNull(sp.get("fcr"), defaults.get("fcr")), 1.8), 1.8);
        double feedPrice = positive(pick(in.feedPricePerKg(), firstNonNull(sp.get("feedPricePerKg"), defaults.get("feedPricePerKg")), 5.2), 5.2);
        double seedPrice = positive(pick(in.seedPricePerFish(), sp.get("seedPricePerFish"), 0.6), 0.6);
        double harvestSize = positive(pick(in.harvestSizeKg(), sp.get("harvestSizeKg"), 1.5), 1.5);
        double marketPrice = positive(pick(in.marketPricePerKg(), sp.get("marketPricePerKg"), 14), 14);

        double density = positive(sp.get("stockDensityPerMu"), 1000);
        double stockQty = in.stockQty() != null && in.stockQty() > 0
                ? in.stockQty()
                : density * areaMu;
        if (in.stockQty() == null) assumptions.add("放养量按 " + fmt(density) + " 尾/亩 × " + fmt(areaMu) + " 亩估算");

        double harvestKg = in.actualHarvestKg() != null && in.actualHarvestKg() > 0
                ? in.actualHarvestKg()
                : stockQty * survival * harvestSize;
        if (in.actualHarvestKg() != null) {
            assumptions.add("产量用实际出塘量 " + fmt(in.actualHarvestKg()) + " kg");
        } else {
            assumptions.add("产量 = 放养量 × 成活率 " + fmt(survival * 100) + "% × 出塘规格 "
                    + fmt(harvestSize) + " kg/尾");
        }

        double feedKg = in.actualFeedKg() != null && in.actualFeedKg() > 0
                ? in.actualFeedKg()
                : harvestKg * fcr;
        if (in.actualFeedKg() != null) {
            assumptions.add("饲料用实际投喂量 " + fmt(in.actualFeedKg()) + " kg");
        } else {
            assumptions.add("饲料量 = 产量 × 饲料系数 " + fmt(fcr));
        }

        double seedCost = stockQty * seedPrice;
        double feedCost = feedKg * feedPrice;
        double electricity = perMu(in.electricityPerMu(), defaults.get("electricityPerMu"), 320) * areaMu;
        double medicine = perMu(in.medicinePerMu(), defaults.get("medicinePerMu"), 180) * areaMu;
        double labor = perMu(in.laborPerMu(), defaults.get("laborPerMu"), 600) * areaMu;
        double rent = perMu(in.rentPerMu(), defaults.get("rentPerMu"), 900) * areaMu;
        double totalCost = seedCost + feedCost + electricity + medicine + labor + rent;

        double revenue = harvestKg * marketPrice;
        double profit = revenue - totalCost;
        double breakEven = harvestKg > 0 ? totalCost / harvestKg : 0;

        if (estimated) {
            assumptions.add("电费/药费/人工/塘租按亩均默认值计算，可在表单里改成自己的实际支出");
        }
        if (in.actualHarvestKg() == null) {
            assumptions.add("未填实际出塘量，本次为估算值，不是实际账目");
        }

        return new Result(speciesId, str(sp.get("name")), estimated ? "estimate" : "actual",
                round1(areaMu), Math.round(stockQty), round1(survival * 100) / 100,
                round1(harvestKg), round1(feedKg),
                round1(seedCost), round1(feedCost), round1(electricity), round1(medicine),
                round1(labor), round1(rent), round1(totalCost),
                round1(revenue), round1(profit), Math.round(breakEven * 100) / 100.0,
                estimated, assumptions);
    }

    /** 起捕节点的提示文案：按当前默认参数给一句能落地的判断 */
    public static String harvestHint(String speciesId, double areaMu) {
        try {
            Result r = estimate(speciesId, new Inputs(areaMu, null, null, null, null, null, null,
                    null, null, null, null, null, null, null));
            if (r == null) return null;
            return "按 " + fmt(r.areaMu()) + " 亩估算：可出塘约 " + fmt(r.harvestKg())
                    + " kg，成本约 " + fmt(r.totalCost()) + " 元，保本价 "
                    + String.format("%.2f", r.breakEvenPricePerKg())
                    + " 元/kg；售价高于保本价才值得卖";
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 参数与数据 ----------------

    private static double pick(Double user, Object speciesValue, double fallback) {
        if (user != null && user > 0) return user;
        return positive(speciesValue, fallback);
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static double perMu(Double user, Object fallbackValue, double fallback) {
        if (user != null && user >= 0) return user;
        return Math.max(0, positive(fallbackValue, fallback));
    }

    private static double positive(Object o, double fallback) {
        double v = dbl(o, fallback);
        return v > 0 ? v : fallback;
    }

    private static double rate(double v, double fallback) {
        if (v <= 0 || v > 1) return fallback;
        return v;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double dbl(Object o, double fallback) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? fallback : Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String fmt(double v) {
        double r = round1(v);
        return Math.abs(r - Math.rint(r)) < 1e-9 ? String.valueOf((long) Math.rint(r)) : String.valueOf(r);
    }

    private static Map<String, Object> model() throws Exception {
        Map<String, Object> local = MODEL;
        if (local != null) return local;
        synchronized (AquaCost.class) {
            if (MODEL != null) return MODEL;
            for (String p : PATHS) {
                Path path = Path.of(p);
                if (!Files.isReadable(path)) continue;
                Object root = JsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
                if (root instanceof Map<?, ?> m) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    m.forEach((k, v) -> out.put(String.valueOf(k), v));
                    MODEL = out;
                    return out;
                }
            }
            throw new IllegalStateException("成本模型未找到：" + String.join(", ", PATHS));
        }
    }
}
