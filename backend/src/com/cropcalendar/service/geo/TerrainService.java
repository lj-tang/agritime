package com.cropcalendar.service.geo;

/**
 * 地形坡度 / 坡向的最小二乘平面拟合 —— 纯数学，离线可单测（T27）。
 *
 * 从 {@code com.cropcalendar.api.PlotAnalyzeHandler} 里抽出来单独放这，
 * 原因有二：
 *   1. 坡度 / 坡向是地块评分里最容易出错的几何量，必须能脱离网络、
 *      用合成数据确定性回归，否则「坡向朝南还是朝北」这类错误改坏了也没人知道；
 *   2. T27 的地理抽检基线（data/geo-baseline.json）要按真实地块核对坡度坡向，
 *      先让这段数学独立可测，人工回填 referenceSlopeDeg / referenceAspectDeg 后
 *      才有可信的对照物。
 */
public final class TerrainService {

    /** 拟合结果。坡度不足 1° 或数据退化时 aspectDeg 为 NaN —— 谈坡向没有意义。 */
    public record Slope(double slopeDeg, double aspectDeg) {
    }

    private TerrainService() {
    }

    /**
     * 最小二乘拟合 z = a·x + b·y + c，其中 x 为东向米、y 为北向米。
     *
     * 坡度取梯度模 {@code atan(hypot(a, b))}，坡向取下坡方向的方位角：
     * 0=北、90=东、180=南、270=西（与气象/地理惯例一致）。
     *
     * 样本不足 3 个、或点位退化（共线，法方程奇异）时返回 {@code slope=0, aspect=NaN}，
     * 由调用方决定是否退出该因子，而不是硬凑一个朝向。
     */
    public static Slope fitPlane(double[] xs, double[] ys, double[] z) {
        if (xs == null || ys == null || z == null) return new Slope(0, Double.NaN);
        int n = Math.min(xs.length, Math.min(ys.length, z.length));
        if (n < 3) return new Slope(0, Double.NaN);

        double sx = 0, sy = 0, sz = 0;
        for (int i = 0; i < n; i++) {
            sx += xs[i];
            sy += ys[i];
            sz += z[i];
        }
        double mx = sx / n, my = sy / n, mz = sz / n;

        double sxx = 0, syy = 0, sxy = 0, sxz = 0, syz = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - mx;
            double dy = ys[i] - my;
            double dz = z[i] - mz;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
            sxz += dx * dz;
            syz += dy * dz;
        }

        double det = sxx * syy - sxy * sxy;
        if (Math.abs(det) < 1e-9) return new Slope(0, Double.NaN);

        double a = (sxz * syy - syz * sxy) / det;   // dz/dx（东向梯度）
        double b = (syz * sxx - sxz * sxy) / det;   // dz/dy（北向梯度）
        double slopeDeg = Math.toDegrees(Math.atan(Math.hypot(a, b)));
        double bear = Math.toDegrees(Math.atan2(-a, -b));
        double aspectDeg = (bear + 360) % 360;
        return new Slope(slopeDeg, aspectDeg);
    }
}
