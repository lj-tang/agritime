package com.cropcalendar.service.crop;

import com.cropcalendar.compute.ComputeStub.RawNode;
import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作物属性（T07）：生长周期 / 年限 / 熟制，并据此调整农事节点。
 *
 * 数据：data/crop-base/properties.json（400 种作物全覆盖）。
 * 其中 source=manual 是人工校准的常见作物，source=category-rule 是按作物类别推定的，
 * 两者都在接口里如实标注，不把推定值说成实测农艺参数。
 *
 * 对时间线的影响（只做两件确定的事，不臆造具体天数）：
 *   1. 一年多熟 / 一年多茬 → 追加一轮周期（第二季整体后移半个生育期）
 *   2. 多年生 → 追加翌年的管理节点（越冬、萌芽肥、修剪），体现"跨年"
 */
public final class CropProperties {

    public record Props(String cropId, String displayName, String growthCycle, String lifespan,
                        String croppingSystem, int totalDays, String suitableSeason,
                        String suitableRegion, String tempRequirement, String source) {
    }

    private static final String[] PATHS = {
            "../data/crop-base/properties.json",
            "data/crop-base/properties.json",
            "/agritime/data/crop-base/properties.json"
    };

    private static volatile Map<String, Props> byName;

    private CropProperties() {
    }

    private static Map<String, Props> all() {
        Map<String, Props> local = byName;
        if (local != null) return local;
        synchronized (CropProperties.class) {
            if (byName != null) return byName;
            byName = load();
            return byName;
        }
    }

    private static Map<String, Props> load() {
        Map<String, Props> map = new LinkedHashMap<>();
        for (String p : PATHS) {
            try {
                Path path = Path.of(p);
                if (!Files.isReadable(path)) continue;
                Object root = JsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
                if (!(root instanceof Map<?, ?> rm) || !(rm.get("crops") instanceof List<?> crops)) {
                    System.err.println("[crop-props] properties.json 结构不对（缺 crops 数组）");
                    return map;
                }
                for (Object o : crops) {
                    if (!(o instanceof Map<?, ?> c)) continue;
                    String name = str(c.get("displayName"));
                    if (name == null) continue;
                    map.put(name, new Props(
                            str(c.get("cropId")),
                            name,
                            strOr(c.get("growthCycle"), "short"),
                            strOr(c.get("lifespan"), "annual"),
                            strOr(c.get("croppingSystem"), "single"),
                            intOr(c.get("totalDays"), 120),
                            strOr(c.get("suitableSeason"), ""),
                            strOr(c.get("suitableRegion"), ""),
                            strOr(c.get("tempRequirement"), ""),
                            strOr(c.get("source"), "category-rule")));
                }
                return map;
            } catch (Exception e) {
                System.err.println("[crop-props] 读取 " + p + " 失败：" + e.getMessage());
            }
        }
        System.err.println("[crop-props] 找不到 properties.json，作物属性按默认值处理");
        return map;
    }

    /** 按作物 id 或标准名查属性；查不到返回 null（调用方自行降级）。 */
    public static Props of(String cropIdOrName) {
        if (cropIdOrName == null || cropIdOrName.isBlank()) return null;
        String name = CropRegistry.standardName(cropIdOrName);
        return name == null ? null : all().get(name);
    }

    /**
     * 按属性扩展节点列表。
     *
     * @param base 规程算出的基础节点（未排序）
     * @param p    作物属性，可为 null
     * @param lat  纬度：双季稻只在南方成立，北方不追加第二季
     */
    public static List<RawNode> expand(List<RawNode> base, Props p, double lat) {
        if (p == null || base == null || base.isEmpty()) return base;

        List<RawNode> out = new ArrayList<>(base);
        int span = base.stream().mapToInt(RawNode::dayOffset).max().orElse(0);

        boolean secondCycle = "double".equals(p.croppingSystem()) || "multi".equals(p.croppingSystem());
        // 水稻一年多熟只在华南/长江以南成立，北方双季稻不现实
        if (secondCycle && "水稻".equals(p.displayName()) && !(lat > 0 && lat < 32)) {
            secondCycle = false;
        }
        if (secondCycle) {
            int shift = "multi".equals(p.croppingSystem()) ? Math.max(45, span / 2) : Math.max(90, span / 2);
            for (RawNode n : base) {
                out.add(new RawNode(n.cropId(), n.opType(), n.dayOffset() + shift, "第二季·" + n.title()));
            }
        }

        if ("perennial".equals(p.lifespan())) {
            String id = base.get(0).cropId();
            out.add(new RawNode(id, "overwinter", 330, "越冬管理（清园、涂白、防冻）"));
            out.add(new RawNode(id, "fertilize", 365, "翌年萌芽肥"));
            out.add(new RawNode(id, "prune", 395, "翌年修剪与花期管理"));
        }
        return out;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static String strOr(Object o, String def) {
        String s = str(o);
        return s == null ? def : s;
    }

    private static int intOr(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
