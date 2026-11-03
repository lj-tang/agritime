package com.cropcalendar.service.guidance;

import com.cropcalendar.compute.ComputeStub.WeatherWindow;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T17 指导换算单测：按面积换算 + 未来 3 天降水调整 + 节点类型差异化。
 *
 * 只构造内存里的天气窗口，不读网络；guidance.json 从仓库根目录读取。
 */
public class GuidanceServiceTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final String NODE = "2026-11-10";

    public static void main(String[] args) {
        // 未来 3 天有雨（每天 5mm，共 15mm），前 3 天干
        WeatherWindow rainy = window(0, 5);
        // 墒情正常：前期 2mm/天、未来 3mm/天，不触发暂停灌溉或补灌
        WeatherWindow normal = window(2, 3);

        // ---- 1. 2 亩 vs 20 亩：亩均一致、总量不同 ----
        String two = GuidanceService.build("fertilize", normal, "番茄", NODE, 2);
        String twenty = GuidanceService.build("fertilize", normal, "番茄", NODE, 20);
        check(two.contains("\"perMu\":{\"min\":15,\"max\":20,\"unit\":\"方\",\"text\":\"15-20 方/亩\"}"),
                "2 亩：浇水亩均量应为 15-20 方/亩，实际 " + two);
        check(two.contains("\"total\":{\"min\":30,\"max\":40,\"text\":\"30-40 方\"}"),
                "2 亩总水量应为 30-40 方，实际 " + two);
        check(twenty.contains("\"total\":{\"min\":300,\"max\":400,\"text\":\"300-400 方\"}"),
                "20 亩总水量应为 300-400 方，实际 " + twenty);
        check(two.contains("\"text\":\"15-20 方/亩\"") && twenty.contains("\"text\":\"15-20 方/亩\""),
                "两种面积下的亩均量必须一致");

        // 施肥 20-25kg/亩 在 2 亩下是 40-50kg
        check(two.contains("\"text\":\"40-50 kg\""), "2 亩施肥总量应为 40-50 kg，实际 " + two);

        // ---- 2. 未填面积：只给亩均，明确提示补录 ----
        String noArea = GuidanceService.build("fertilize", rainy, "番茄", NODE, 0);
        check(noArea.contains("\"total\":null"), "未填面积时不应编造总用量，实际 " + noArea);
        check(noArea.contains("\"areaMu\":null"), "未填面积时 areaMu 应为 null");
        check(noArea.contains("填入面积可自动换算总用量"), "未填面积应提示补录，实际 " + noArea);

        // ---- 3. 未来 3 天有雨 → 雨前施用 ----
        String rainyFert = GuidanceService.build("fertilize", rainy, "番茄", NODE, 2);
        check(rainyFert.contains("\"weatherAdvice\":{\"code\":\"before-rain\""), "有雨时应给 before-rain，实际 " + rainyFert);
        check(rainyFert.contains("雨前 1~2 天施用"), "施肥节点有雨应提示雨前施用，实际 " + rainyFert);

        // ---- 4. 连续无雨 → 补灌 ----
        WeatherWindow dry = window(0, 0);
        String dryWater = GuidanceService.build("irrigation", dry, "小麦", NODE, 3);
        check(dryWater.contains("\"code\":\"drought\""), "连续无雨应给 drought，实际 " + dryWater);
        check(dryWater.contains("建议补灌"), "灌溉节点无雨应提示补灌，实际 " + dryWater);

        // ---- 5. 有雨时灌溉改为暂停 ----
        String rainyWater = GuidanceService.build("irrigation", rainy, "小麦", NODE, 3);
        check(rainyWater.contains("暂停灌溉"), "有雨时灌溉建议应暂停，实际 " + rainyWater);
        check(rainyWater.contains("\"code\":\"before-rain\""), "有雨时灌溉也应是 before-rain");

        // ---- 6. 节点类型差异化：收获节点不给浇水，改说抢收 ----
        String harvest = GuidanceService.build("harvest", rainy, "小麦", NODE, 3);
        check(!harvest.contains("\"watering\""), "收获节点不应再建议浇水，实际 " + harvest);
        check(harvest.contains("抢在雨前收获"), "收获节点有雨应提示抢收，实际 " + harvest);
        check(harvest.contains("\"tools\""), "收获节点仍应给农具清单");

        // 打药节点：给病虫害清单，且用药量按面积换算
        String spray = GuidanceService.build("pesticide", rainy, "番茄", NODE, 2);
        check(spray.contains("\"pest\":["), "打药节点应给病虫害清单，实际 " + spray);
        check(spray.contains("\"pestSource\""), "应标注病虫害来源");

        // ---- 7. 远期节点：给用量，不给天气结论 ----
        String far = GuidanceService.build("fertilize", null, "番茄", NODE, 2, true);
        check(far.contains("\"weatherAdvice\":null"), "远期节点不应给天气结论，实际 " + far);
        check(far.contains("远期节点按常年规程给量"), "远期节点应说明依据，实际 " + far);

        // ---- 8. 过湿：先排渍 ----
        WeatherWindow flood = window(0, 20);
        String waterlog = GuidanceService.build("fertilize", flood, "番茄", NODE, 2);
        check(waterlog.contains("\"code\":\"waterlog\""), "3 天 60mm 应判过湿，实际 " + waterlog);
        check(waterlog.contains("清沟排渍"), "过湿应提示排渍，实际 " + waterlog);

        System.out.println("GuidanceServiceTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** 构造以 NODE 为中心的窗口：前 3 天固定 0mm，后 3 天每天 rainPerDay。 */
    private static WeatherWindow window(double pastRainPerDay, double nextRainPerDay) {
        Map<String, WeatherWindow.Daily> byDate = new LinkedHashMap<>();
        LocalDate center = LocalDate.parse(NODE);
        for (int i = -7; i <= 7; i++) {
            LocalDate d = center.plusDays(i);
            double rain = i >= 0 ? nextRainPerDay : pastRainPerDay;
            byDate.put(d.toString(), new WeatherWindow.Daily(rain, 2.0, 12.0, 24.0));
        }
        return new WeatherWindow(byDate, false, null);
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg);
        }
    }
}
