package com.cropcalendar.service.geo;

/**
 * T16 土壤质地分类单测（纯函数）。
 * 外部接口本身不在这里测 —— 网络可达性属于部署环境，不能当单测前提。
 */
public class GeoFactorsTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // 黏粒占比优先：≥40 直接判黏土，哪怕砂粒也不少
        eq("黏土", GeoFactors.classify(45, 30), "黏粒 45% 应为黏土");
        eq("黏土", GeoFactors.classify(40, 55), "黏粒刚好 40% 应为黏土");

        // 27~40 为黏壤
        eq("黏壤", GeoFactors.classify(30, 35), "黏粒 30% 应为黏壤");
        eq("黏壤", GeoFactors.classify(27, 60), "黏粒 27% 应为黏壤");

        // 砂粒 ≥70 为砂土
        eq("砂土", GeoFactors.classify(10, 75), "砂粒 75% 应为砂土");
        eq("砂土", GeoFactors.classify(5, 90), "砂粒 90% 应为砂土");

        // 50~70 为砂壤
        eq("砂壤", GeoFactors.classify(12, 55), "砂粒 55% 应为砂壤");

        // 其余为壤土
        eq("壤土", GeoFactors.classify(18, 40), "黏粒 18%、砂粒 40% 应为壤土");
        eq("壤土", GeoFactors.classify(20, 45), "中间区间应为壤土");

        // 缺测（0/0）不应崩，按壤土兜底
        eq("壤土", GeoFactors.classify(0, 0), "无数据时应回落到壤土");

        System.out.println("GeoFactorsTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void eq(String expected, String actual, String msg) {
        if (expected.equals(actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg + "，期望「" + expected + "」实际「" + actual + "」");
        }
    }
}
