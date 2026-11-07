package com.cropcalendar.service.geo;

/**
 * TerrainService 坡度/坡向拟合的确定性回归（T27）。
 *
 * 全部用合成数据，不碰网络：
 *   · 东向抬升平面 → 坡度 atan(0.1)≈5.71°，坡向 270°（西，下坡朝西）
 *   · 南向抬升平面 → 坡度 ≈5.71°，坡向 180°（南）
 *   · 水平面 → 坡度 0
 *   · 样本不足 / 共线退化 → 坡度 0、坡向 NaN
 */
public class TerrainServiceTest {

    private static int pass = 0, fail = 0;

    public static void main(String[] args) {
        eastTilt();
        southTilt();
        flatPlane();
        tooFewSamples();
        collinearDegenerate();

        System.out.println("TerrainServiceTest: " + pass + "/" + (pass + fail) + " 通过");
        if (fail > 0) System.exit(1);
    }

    private static void eastTilt() {
        // z = 0.1·x：往东每米抬升 0.1 米 → 下坡方向朝西（270°）
        double[] xs = {-10, 10, 0};
        double[] ys = {0, 0, 10};
        double[] z = {-1.0, 1.0, 0.0};
        TerrainService.Slope s = TerrainService.fitPlane(xs, ys, z);
        checkClose("东向坡·坡度", s.slopeDeg(), 5.7106, 0.05);
        checkClose("东向坡·坡向", s.aspectDeg(), 270, 0.5);
    }

    private static void southTilt() {
        // z = 0.1·y：往北每米抬升 0.1 米 → 下坡方向朝南（180°）
        double[] xs = {0, 0, 10};
        double[] ys = {-10, 10, 0};
        double[] z = {-1.0, 1.0, 0.0};
        TerrainService.Slope s = TerrainService.fitPlane(xs, ys, z);
        checkClose("南向坡·坡度", s.slopeDeg(), 5.7106, 0.05);
        checkClose("南向坡·坡向", s.aspectDeg(), 180, 0.5);
    }

    private static void flatPlane() {
        double[] xs = {-10, 10, 0};
        double[] ys = {0, 0, 10};
        double[] z = {0.0, 0.0, 0.0};
        TerrainService.Slope s = TerrainService.fitPlane(xs, ys, z);
        checkClose("水平面·坡度", s.slopeDeg(), 0.0, 0.001);
    }

    private static void tooFewSamples() {
        TerrainService.Slope s = TerrainService.fitPlane(new double[]{0, 1}, new double[]{0, 1}, new double[]{0, 1});
        check("样本不足·坡度归零", s.slopeDeg() == 0.0);
        check("样本不足·坡向为 NaN", Double.isNaN(s.aspectDeg()));
    }

    private static void collinearDegenerate() {
        // 三个点共线（都在 y=x 上），法方程奇异，det≈0
        double[] xs = {0, 1, 2};
        double[] ys = {0, 1, 2};
        double[] z = {0.0, 1.0, 2.0};
        TerrainService.Slope s = TerrainService.fitPlane(xs, ys, z);
        check("共线退化·坡度归零", s.slopeDeg() == 0.0);
        check("共线退化·坡向为 NaN", Double.isNaN(s.aspectDeg()));
    }

    private static void checkClose(String name, double actual, double expect, double tol) {
        if (Double.isNaN(actual) || Math.abs(actual - expect) > tol) {
            fail++;
            System.out.println("  FAIL " + name + "：期望 " + expect + "，实际 " + actual);
        } else {
            pass++;
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("  FAIL " + name);
        }
    }
}
