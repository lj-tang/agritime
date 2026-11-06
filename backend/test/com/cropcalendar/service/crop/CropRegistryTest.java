package com.cropcalendar.service.crop;

/**
 * T02 作物名称注册表单测：别名/拼音/规程 id 归一，模糊匹配闸门。
 *
 * 这是农事日历入口的关键链路 —— 之前"农事日历卡死"就是作物名归一没接上。
 * 数据依赖 data/crop-base/alias.json（400 条）与 backend/crop-protocols.json（5 个规程）。
 */
public class CropRegistryTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---- 1. 标准名归一：任意写法 → 标准显示名 ----
        eq("水稻", CropRegistry.standardName("水稻"), "标准名原样返回");
        eq("水稻", CropRegistry.standardName(" 水稻 "), "去首尾空白");
        eq("番茄", CropRegistry.standardName("西红柿"), "别名归一到番茄");
        eq("马铃薯", CropRegistry.standardName("土豆"), "别名归一到马铃薯");
        eq("水稻", CropRegistry.standardName("shuidao"), "拼音归一到水稻");
        eq("番茄", CropRegistry.standardName("fanqie"), "拼音归一到番茄");
        eq("水稻", CropRegistry.standardName("rice"), "规程 id 反查成标准名");
        eq("大蒜", CropRegistry.standardName("dry_garlic"), "规程 id 反查成标准名");

        // ---- 2. 规程映射 ----
        eq("rice", CropRegistry.protocolIdOf("水稻"), "水稻应有规程");
        eq("dry_garlic", CropRegistry.protocolIdOf("大蒜"), "大蒜应有规程");
        check(CropRegistry.protocolIdOf("番茄") == null, "番茄无规程应返回 null");

        // ---- 3. 一步到位：有规程优先规程 id，否则回标准名 ----
        eq("rice", CropRegistry.timelineCropId("水稻"), "有规程优先规程 id");
        eq("rice", CropRegistry.timelineCropId("rice"), "已是规程 id 直接透传");
        eq("番茄", CropRegistry.timelineCropId("西红柿"), "无规程回标准名");

        // ---- 4. 联想搜索：别名命中应排第一 ----
        eq("番茄", CropRegistry.search("西红柿", 5).get(0).entry().displayName(), "别名命中排第一");
        eq("马铃薯", CropRegistry.search("土豆", 5).get(0).entry().displayName(), "别名命中排第一");
        check(!CropRegistry.search("土豆", 20).isEmpty(), "别名搜索应有候选");

        // ---- 5. 模糊匹配闸门：首字不同不许模糊匹配 ----
        // 「土豆」和「大豆」编辑距离只有 1，但首字「土」vs「大」不同，绝不能带出
        boolean noBean = CropRegistry.search("土豆", 50).stream()
                .noneMatch(m -> m.entry().displayName().equals("大豆"));
        check(noBean, "首字不同的作物不应被模糊匹配带出");

        // 「西红柿」和「红枣」编辑距离也不远，但首字「西」vs「红」不同
        boolean noJujube = CropRegistry.search("西红柿", 50).stream()
                .noneMatch(m -> m.entry().displayName().equals("红枣"));
        check(noJujube, "首字不同的作物不应被模糊匹配带出");

        System.out.println("CropRegistryTest: " + passed + " passed, " + failed + " failed");
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

    private static void eq(String expected, String actual, String msg) {
        check(expected == null ? actual == null : expected.equals(actual),
                msg + "，期望「" + expected + "」实际「" + actual + "」");
    }
}
