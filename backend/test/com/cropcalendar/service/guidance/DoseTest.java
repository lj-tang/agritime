package com.cropcalendar.service.guidance;

/**
 * T17 用量解析单测：只测纯函数，不读文件、不发网络请求。
 *
 *   javac -cp backend/build/classes -d /tmp/t backend/test/.../DoseTest.java
 *   java  -cp backend/build/classes:/tmp/t com.cropcalendar.service.guidance.DoseTest
 */
public class DoseTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---- 解析 ----
        Dose water = Dose.parse("15-20方/亩");
        check(water != null, "应能解析 15-20方/亩");
        eq(15.0, water.min(), "水量下限");
        eq(20.0, water.max(), "水量上限");
        eq("方", water.unit(), "水量单位");
        eq("15-20 方/亩", water.perMuText(), "亩均文案");

        Dose fert = Dose.parse("20-25kg/亩");
        eq(20.0, fert.min(), "施肥下限");
        eq(25.0, fert.max(), "施肥上限");
        eq("kg", fert.unit(), "施肥单位");

        Dose spray = Dose.parse("20g/亩兑水30kg喷雾");
        check(spray != null, "带后缀的用量也应解析出 20g/亩");
        eq(20.0, spray.min(), "药量");
        eq(20.0, spray.max(), "单值时上下限相同");
        eq("g", spray.unit(), "药量单位");
        // 兑水的 30kg 不能被当成亩均用量
        check(spray.totalText(1).startsWith("20 g"), "1 亩地总量应为 20 g，实际 " + spray.totalText(1));

        Dose ml = Dose.parse("30ml/亩兑水30kg喷雾");
        eq("ml", ml.unit(), "毫升单位");
        eq("30 ml", ml.totalText(1), "1 亩地药液量");

        // ---- 单位归一 ----
        eq("kg", Dose.parse("10公斤/亩").unit(), "公斤归一为 kg");
        eq("g", Dose.parse("500克/亩").unit(), "克归一为 g");
        eq("方", Dose.parse("30立方米/亩").unit(), "立方米归一为方");

        // ---- 不按面积算的情况必须返回 null ----
        check(Dose.parse("灌根1000倍液") == null, "稀释倍数与面积无关，不应解析");
        check(Dose.parse("800倍液喷淋苗床") == null, "苗床喷淋不应解析");
        check(Dose.parse("按标签用量兑水喷雾") == null, "无数字应返回 null");
        check(Dose.parse(null) == null, "null 输入应返回 null");
        check(Dose.parse("") == null, "空串应返回 null");

        // ---- 面积换算 ----
        eq("30-40 方", water.totalText(2), "2 亩总水量");
        eq("300-400 方", water.totalText(20), "20 亩总水量");
        // 亩均一致、总量随面积变 —— T17 验收第 1 条
        eq(water.perMuText(), water.perMuText(), "亩均量与面积无关");

        // 小单位自动进位，避免出现 "20000 g"
        eq("20 kg", spray.totalText(1000), "1000 亩 20g/亩 应进位为 20 kg");
        eq("2 kg", Dose.parse("1000g/亩").totalText(2), "2000g 应进位为 2 kg");

        // 一位小数
        eq("2.5 kg", Dose.parse("1kg/亩").totalText(2.5), "小数面积保留一位");
        eq(2.5, Dose.parse("1.2-2.5kg/亩").max(), "小数区间解析");

        System.out.println("DoseTest: " + passed + " passed, " + failed + " failed");
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
        check(Math.abs(expected - actual) < 1e-9, msg + "，期望 " + expected + " 实际 " + actual);
    }

    private static void eq(String expected, String actual, String msg) {
        check(expected != null && expected.equals(actual), msg + "，期望「" + expected + "」实际「" + actual + "」");
    }
}
