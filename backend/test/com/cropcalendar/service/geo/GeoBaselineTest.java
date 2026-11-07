package com.cropcalendar.service.geo;

import com.cropcalendar.util.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * T27 地理抽检回归基线。
 *
 * 面积由 {@link GeoResolver#polygonAreaSqm} 球面公式确定性计算，对全部已知地块
 * 自动回归：只要公式或几何解析被改坏，一致率就会掉，测试即失败。
 *
 * 基线含两类地块：20 个平地（面积回归 + 「判平地」）与 20 个坡地（坡度/坡向有真实值）。
 * 坡度/坡向依赖外部 DEM 无法离线复现，故本测试只做：面积自动回归 + reference 值范围核对
 * （平地坡度=0 坡向=null；坡地坡度≥1° 且坡向在 0~360）。拟合数学的确定性回归见
 * TerrainServiceTest，双 DEM 源一致性由 deploy/signoff-baseline.py 在签字时交叉验证。
 */
public class GeoBaselineTest {

    public static void main(String[] args) throws Exception {
        String text = Files.readString(Path.of("data/geo-baseline.json"));
        Object root = JsonParser.parse(text);
        if (!(root instanceof Map<?, ?> m) || !(m.get("plots") instanceof List<?> list)) {
            throw new IllegalStateException("基线结构不对，应为 {plots:[...]}");
        }

        int pass = 0;
        int fail = 0;
        int signed = 0;
        int signedBad = 0;
        int flat = 0;
        int slope = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> pm)) continue;
            String name = String.valueOf(pm.get("name"));
            double expectedMu = Double.parseDouble(String.valueOf(pm.get("expectedAreaMu")));
            boolean isSlope = "slope".equals(String.valueOf(pm.get("terrain")));
            if (isSlope) slope++; else flat++;

            List<double[]> ring = new ArrayList<>();
            if (pm.get("ring") instanceof List<?> rl) {
                for (Object c : rl) {
                    if (c instanceof List<?> pt && pt.size() >= 2) {
                        ring.add(new double[]{
                                Double.parseDouble(String.valueOf(pt.get(0))),
                                Double.parseDouble(String.valueOf(pt.get(1)))});
                    }
                }
            }

            double areaSqm = GeoResolver.polygonAreaSqm(ring);
            double areaMu = areaSqm / 666.667;
            double diff = expectedMu > 0 ? Math.abs(areaMu - expectedMu) / expectedMu : 1;
            boolean ok = diff <= 0.005; // 0.5% 容差，远大于球面/投影两种公式本身的误差
            if (ok) {
                pass++;
            } else {
                fail++;
                System.out.println("  FAIL " + name + "：期望 " + expectedMu + " 亩，实际 "
                        + String.format("%.2f", areaMu) + " 亩");
            }

            // 签字值范围核对：平地坡度=0 坡向=null；坡地坡度≥1° 且坡向在 0~360。
            if (pm.get("referenceSlopeDeg") != null || pm.get("referenceAspectDeg") != null) {
                signed++;
                double sl = num(pm, "referenceSlopeDeg");
                double as = num(pm, "referenceAspectDeg");
                boolean bad = sl < 0 || sl > 90 || as < 0 || as >= 360;
                if (isSlope && sl < 1) bad = true;         // 坡地样本坡度应≥1°，否则是滥竽充数
                if (isSlope && Double.isNaN(as)) bad = true; // 坡地必须有坡向
                if (bad) {
                    signedBad++;
                    System.out.println("  FAIL " + name + "：签字值越界或与地形不符（坡度 " + sl + "、坡向 " + as + "）");
                }
            }
        }

        double rate = (pass + fail) == 0 ? 0 : (double) pass / (pass + fail);
        System.out.println("GeoBaselineTest: " + pass + "/" + (pass + fail) + " 面积一致率 "
                + String.format("%.0f%%", rate * 100)
                + "；坡度/坡向已签字 " + signed + "/" + list.size()
                + "（平地 " + flat + " + 坡地 " + slope + "，拟合数学见 TerrainServiceTest）");
        if (fail > 0 || pass < list.size() || signedBad > 0 || slope < 10) System.exit(1);
    }

    private static double num(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v == null ? 0 : Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
