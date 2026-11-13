package com.cropcalendar.service.geo;

/**
 * 坐标系换算：WGS84 ↔ GCJ02（国测局「火星坐标」）↔ BD09MC（百度墨卡托）。
 *
 * 为什么必须有这一层？
 *   · 浏览器 geolocation、IP 定位、离线地名库、我们存进地块的经纬度，都是 WGS84；
 *   · 地图瓦片用的是高德底图，属于 GCJ02；
 *   · 高德/百度的接口也都只认自家坐标系。
 * 直接把 WGS84 画到高德瓦片上，在中国境内会偏 300~600 米——用户看到的
 * 标记落在隔壁那条街，逆地理自然就"识别不出具体位置"。
 *
 * 所有方法统一返回 {lon, lat}（与高德接口的参数顺序一致），避免调用处
 * 反复搞错经纬度顺序（这个 bug 之前已经出现过一次）。
 */
public final class CoordTransform {

    private static final double A = 6378245.0;                 // 克拉索夫斯基椭球长半轴
    private static final double EE = 0.00669342162296594323;   // 第一偏心率平方

    /** 百度墨卡托的分段边界（纬度方向），从北到南 */
    private static final double[] MCBAND = {
            12890594.86, 8362377.87, 5591021, 3481989.83, 1678043.12, 0
    };

    /** 每段对应的反算系数，取自百度地图 JS 的 MC2LL */
    private static final double[][] MC2LL = {
            {1.410526172116255e-8, 8.98305509648872e-6, -1.9939833816331, 200.9824383106796,
                    -187.2403703815547, 91.6087516669843, -23.38765649603339, 2.57121317296198,
                    -0.03801003308653, 17337981.2},
            {-7.435856389565537e-9, 8.983055097726239e-6, -0.78625201886289, 96.32687599759846,
                    -1.85204757529826, -59.36935905485877, 47.40033549296737, -16.50741931063887,
                    2.28786674699375, 10260144.86},
            {-3.030883460898826e-8, 8.98305509983578e-6, 0.30071316287616, 59.74293618442277,
                    7.357984074871, -25.38371002664745, 13.45380521110908, -3.29883767235584,
                    0.32710905363475, 6856817.37},
            {-1.981981304930552e-8, 8.983055099779535e-6, 0.03278182852591, 40.31678527705744,
                    0.65659298677277, -4.44255534477492, 0.85341911805263, 0.12923347998204,
                    -0.04625736007561, 4482777.06},
            {3.09191371068437e-9, 8.983055096812155e-6, 6.95799581859e-5, 23.10934304144901,
                    -0.00023663490511, -0.6321817810242, -0.00663494467273, 0.03430082397953,
                    -0.00466043876332, 2555164.4},
            {2.890871144776878e-9, 8.983055095805407e-6, -3.068298e-8, 7.47137025468032,
                    -3.53937994e-6, -0.02145144861037, -1.234426596e-5, 0.00010322952773,
                    -3.23890364e-6, 826088.5}
    };

    private CoordTransform() {
    }

    /** 境外不做偏移：GCJ02 只在中国大陆范围内加偏 */
    public static boolean outOfChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    /** WGS84 → GCJ02，入参/出参都是 {lon, lat} */
    public static double[] wgs84ToGcj02(double lon, double lat) {
        if (outOfChina(lat, lon)) return new double[]{lon, lat};
        double[] d = delta(lon, lat);
        return new double[]{lon + d[0], lat + d[1]};
    }

    /** GCJ02 → WGS84。逆变换没有闭式解，用「先加偏再回退」迭代两次，误差 < 1 米 */
    public static double[] gcj02ToWgs84(double lon, double lat) {
        if (outOfChina(lat, lon)) return new double[]{lon, lat};
        double wlon = lon;
        double wlat = lat;
        for (int i = 0; i < 2; i++) {
            double[] g = wgs84ToGcj02(wlon, wlat);
            wlon += lon - g[0];
            wlat += lat - g[1];
        }
        return new double[]{wlon, wlat};
    }

    /** BD09MC（百度墨卡托，搜索结果里的 x/y 除以 100）→ WGS84 */
    public static double[] bd09mcToWgs84(double x, double y) {
        double[] gcj = bd09mcToGcj02(x, y);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    /** BD09MC → GCJ02 */
    public static double[] bd09mcToGcj02(double x, double y) {
        double[] c = null;
        for (int i = 0; i < MCBAND.length; i++) {
            if (Math.abs(y) >= MCBAND[i]) {
                c = MC2LL[i];
                break;
            }
        }
        if (c == null) c = MC2LL[MC2LL.length - 1];

        double lon = c[0] + c[1] * Math.abs(x);
        double t = Math.abs(y) / c[9];
        double lat = c[2] + c[3] * t + c[4] * Math.pow(t, 2) + c[5] * Math.pow(t, 3)
                + c[6] * Math.pow(t, 4) + c[7] * Math.pow(t, 5) + c[8] * Math.pow(t, 6);
        if (y < 0) lat = -lat;
        if (x < 0) lon = -lon;

        // BD09 → GCJ02：先去掉百度自己的二次偏移
        double z = Math.sqrt(lon * lon + lat * lat) - 0.00002 * Math.sin(lat * Math.PI * 3000.0 / 180.0);
        double theta = Math.atan2(lat, lon) - 0.000003 * Math.cos(lon * Math.PI * 3000.0 / 180.0);
        return new double[]{z * Math.cos(theta), z * Math.sin(theta)};
    }

    /** GCJ02 相对 WGS84 的偏移量（度） */
    private static double[] delta(double lon, double lat) {
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{dLon, dLat};
    }

    private static double transformLat(double x, double y) {
        double r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        r += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        r += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return r;
    }

    private static double transformLon(double x, double y) {
        double r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        r += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        r += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return r;
    }
}
