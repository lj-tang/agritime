package com.cropcalendar.service.geo;

import java.util.List;

/**
 * 地块可种植性分类 / 多边形面积回归（不需要网络）。
 *
 *   javac -cp build/classes -d /tmp/out test/com/cropcalendar/service/geo/GeoClassifyTest.java
 *   java -cp build/classes:/tmp/out com.cropcalendar.service.geo.GeoClassifyTest
 */
public class GeoClassifyTest {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        // 100m × 100m 方块（赤道附近），面积应约 9,993 ㎡ ≈ 15 亩
        List<double[]> square = List.of(
                new double[]{0, 0},
                new double[]{0, 0.000898},
                new double[]{0.000898, 0.000898},
                new double[]{0.000898, 0});
        double area = GeoResolver.polygonAreaSqm(square);
        check("area-100m", Math.abs(area - 9993) < 60, String.valueOf(area));

        double[] center = GeoResolver.ringCentroid(square);
        check("centroid", Math.abs(center[0] - 0.000449) < 1e-9 && Math.abs(center[1] - 0.000449) < 1e-9,
                center[0] + "," + center[1]);

        check("landuse-farmland", GeoResolver.mapCategory("landuse", "farmland") == GeoResolver.Category.FARMLAND, "");
        check("landuse-orchard", GeoResolver.mapCategory("landuse", "orchard") == GeoResolver.Category.FARMLAND, "");
        check("landuse-residential", GeoResolver.mapCategory("landuse", "residential") == GeoResolver.Category.URBAN, "");
        check("landuse-commercial", GeoResolver.mapCategory("landuse", "commercial") == GeoResolver.Category.URBAN, "");
        check("landuse-construction", GeoResolver.mapCategory("landuse", "construction") == GeoResolver.Category.URBAN, "");
        check("leisure-park", GeoResolver.mapCategory("leisure", "park") == GeoResolver.Category.GREEN, "");
        check("leisure-garden", GeoResolver.mapCategory("leisure", "garden") == GeoResolver.Category.GREEN, "");
        check("leisure-swimming", GeoResolver.mapCategory("leisure", "swimming_pool") == GeoResolver.Category.URBAN, "");
        check("natural-water", GeoResolver.mapCategory("natural", "water") == GeoResolver.Category.WATER, "");
        check("natural-grass", GeoResolver.mapCategory("natural", "grassland") == GeoResolver.Category.FARMLAND, "");
        check("place-village", GeoResolver.mapCategory("place", "village") == GeoResolver.Category.FARMLAND, "");
        check("place-neighbourhood", GeoResolver.mapCategory("place", "neighbourhood") == GeoResolver.Category.URBAN, "");
        check("amenity-school", GeoResolver.mapCategory("amenity", "school") == GeoResolver.Category.URBAN, "");
        check("amenity-bench-ignored", GeoResolver.mapCategory("amenity", "bench") == null, "");
        check("highway-ignored", GeoResolver.mapCategory("highway", "motorway") == null, "");
        check("power-ignored", GeoResolver.mapCategory("power", "tower") == null, "");
        check("boundary-ignored", GeoResolver.mapCategory("boundary", "administrative") == null, "");
        check("railway-line-ignored", GeoResolver.mapCategory("railway", "rail") == null, "");
        check("building-urban", GeoResolver.mapCategory("building", "yes") == GeoResolver.Category.URBAN, "");
        check("unknown-empty", GeoResolver.mapCategory("", "") == null, "");

        System.out.println(fail == 0 ? "ALL PASS (" + pass + ")" : (fail + " FAILED / " + (pass + fail)));
        if (fail > 0) System.exit(1);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("ok   " + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        } else {
            fail++;
            System.out.println("FAIL " + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        }
    }
}
