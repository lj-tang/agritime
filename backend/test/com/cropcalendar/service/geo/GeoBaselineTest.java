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
 * 面积由 {@link GeoResolver#polygonAreaSqm} 球面公式确定性计算，可以对 20 个已知地块
 * 自动回归：只要公式或几何解析被改坏，一致率就会掉，测试即失败。
 *
 * 坡度 / 坡向依赖外部 90m DEM（Open-Meteo Elevation），无法离线确定性复现，
 * 因此基线文件里 `referenceSlopeDeg / referenceAspectDeg` 留空，等人工核对后回填；
 * 本测试只对面积这一项做自动回归。
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
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> pm)) continue;
            String name = String.valueOf(pm.get("name"));
            double expectedMu = Double.parseDouble(String.valueOf(pm.get("expectedAreaMu")));

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

            // 坡度/坡向人工签字：回填 referenceSlopeDeg/referenceAspectDeg 后做范围核对，
            // 一致性本身由 TerrainServiceTest 对拟合数学做确定性回归，二者合起来才闭环。
            if (pm.get("referenceSlopeDeg") != null || pm.get("referenceAspectDeg") != null) {
                signed++;
                double sl = num(pm, "referenceSlopeDeg");
                double as = num(pm, "referenceAspectDeg");
                if (sl < 0 || sl > 90 || as < 0 || as >= 360) {
                    signedBad++;
                    System.out.println("  FAIL " + name + "：签字值越界（坡度 0~90、坡向 0~360）");
                }
            }
        }

        double rate = (pass + fail) == 0 ? 0 : (double) pass / (pass + fail);
        System.out.println("GeoBaselineTest: " + pass + "/" + (pass + fail) + " 面积一致率 "
                + String.format("%.0f%%", rate * 100)
                + "；坡度/坡向已人工签字 " + signed + "/" + list.size()
                + "（拟合数学见 TerrainServiceTest，回填见 data/geo-baseline.json）");
        if (fail > 0 || pass < 20 || signedBad > 0) System.exit(1);
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
