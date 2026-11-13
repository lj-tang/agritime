package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.CropInfo;
import com.cropcalendar.service.crop.AgroZone;
import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.service.crop.CropTemplates;
import com.cropcalendar.service.crop.PlantingWindow;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GET /api/crops —— 作物清单与搜索联想（A7 / T02）。
 *
 * 两种用法：
 *   · 不带 q：返回有农事规程的作物（可直接进时间线），name 为标准中文名
 *   · 带 q  ：在 400 条作物库里做归一搜索，返回 Top8，
 *             matchedBy 说明是「标准名 / 别名 / 拼音 / 包含 / 模糊」命中的
 *
 * 关键约定（T02 验收第 3 条）：对外只暴露标准名，前端展示俗称可以由
 * aliases 字段自行处理，但不允许把俗称当作物 id 传给其它接口。
 */
public class CropsHandler implements HttpHandler {

    private static final int SEARCH_LIMIT = 8;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, Json.obj("error", Json.str("只支持 GET")));
            return;
        }

        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String keyword = HttpSupport.trimToNull(q.get("q"));

        // 规程作物的节点数，用于告诉前端"这个作物能不能直接进时间线"
        Map<String, Integer> nodeCount = new HashMap<>();
        for (CropInfo c : ComputeStub.listCropInfos()) {
            nodeCount.put(c.cropId(), c.nodeCount());
        }

        List<String> items = new ArrayList<>();
        if (keyword == null) {
            // 作物库：返回**全部 400 种**，不再只给"有规程的 21 种"。
            // 之前前端「全部品种」下拉拿到的就是这个残缺列表，用户看到的库是不完整的。
            // 每条带类别、生育期、播期窗口与精度，前端可直接按类别分组浏览。
            String category = HttpSupport.trimToNull(q.get("category"));
            int limit = (int) HttpSupport.parseDouble(q.get("limit"), 0);
            int offset = (int) HttpSupport.parseDouble(q.get("offset"), 0);
            if (offset < 0) offset = 0;
            if (limit < 0) limit = 0;

            List<LibItem> lib = new ArrayList<>();
            for (CropRegistry.Entry e : CropRegistry.all()) {
                CropProperties.Props props = CropProperties.of(e.displayName());
                CropTemplates.Group group = CropTemplates.groupOf(props);
                if (category != null
                        && !category.equalsIgnoreCase(group.name())
                        && !category.equals(group.label())) {
                    continue;   // 支持 ?category=LEAFY 或 ?category=叶菜
                }
                String protocolId = CropRegistry.protocolIdOf(e.displayName());
                int count = protocolId == null ? 0 : nodeCount.getOrDefault(protocolId, 0);
                lib.add(new LibItem(e, props, group, protocolId, count));
            }
            // 先按类别顺序（与规程模板同一套枚举），类内按名称，保证每次返回顺序一致
            lib.sort(Comparator.comparingInt((LibItem x) -> x.group().ordinal())
                    .thenComparing(x -> x.entry().displayName()));

            Map<String, Integer> catCount = new LinkedHashMap<>();
            for (LibItem x : lib) catCount.merge(x.group().label(), 1, Integer::sum);

            for (int i = offset; i < lib.size() && (limit == 0 || i < offset + limit); i++) {
                items.add(libraryItem(lib.get(i)));
            }

            List<String> cats = new ArrayList<>();
            for (Map.Entry<String, Integer> c : catCount.entrySet()) {
                cats.add(Json.obj(
                        "label", Json.str(c.getKey()),
                        "count", String.valueOf(c.getValue())));
            }

            HttpSupport.write(ex, 200, Json.obj(
                    "total", String.valueOf(lib.size()),
                    "offset", String.valueOf(offset),
                    "query", "null",
                    "categories", Json.arr(cats),
                    "crops", Json.arr(items)));
            return;
        }

        List<CropRegistry.Match> matches = CropRegistry.search(keyword, SEARCH_LIMIT);
        for (CropRegistry.Match m : matches) {
            String protocolId = CropRegistry.protocolIdOf(m.entry().displayName());
            int count = protocolId == null ? 0 : nodeCount.getOrDefault(protocolId, 0);
            items.add(item(
                    protocolId != null ? protocolId : m.entry().canonical(),
                    m.entry().displayName(),
                    m.matchedBy(),
                    protocolId != null && count > 0,
                    count,
                    m.entry().aliases(),
                    m.entry().pinyin()));
        }

        HttpSupport.write(ex, 200, Json.obj(
                "total", String.valueOf(items.size()),
                "query", Json.str(keyword),
                "standardName", Json.str(CropRegistry.standardName(keyword)),
                "crops", Json.arr(items)));
    }

    private String item(String cropId, String displayName, String matchedBy,
                        boolean hasProtocol, int nodeCount,
                        List<String> aliases, List<String> pinyin) {
        CropProperties.Props p = CropProperties.of(displayName);
        CropTemplates.Group group = CropTemplates.groupOf(p);
        return Json.obj(
                "cropId", Json.str(cropId),
                "name", Json.str(displayName),
                "canonical", Json.str(cropId),
                "displayName", Json.str(displayName),
                "matchedBy", Json.str(matchedBy),
                "category", Json.str(group.label()),
                "categoryKey", Json.str(group.name()),
                "lifespan", Json.str(p == null ? "" : p.lifespan()),
                "totalDays", String.valueOf(p == null ? 0 : p.totalDays()),
                "window", Json.str(windowText(p)),
                "windowAction", Json.str(group.action()),
                "windowPrecision", Json.str(precisionOf(p)),
                "propsSource", Json.str(p == null ? "" : p.source()),
                "hasProtocol", String.valueOf(hasProtocol),
                "nodeCount", String.valueOf(nodeCount),
                "aliases", Json.arr(strArray(aliases)),
                "pinyin", Json.arr(strArray(pinyin)));
    }

    /** 作物库列表的一条：比搜索结果多给窗口与类别，前端据此分组、标注"已校核/按季节推导"。 */
    private String libraryItem(LibItem x) {
        CropProperties.Props p = x.props();
        return Json.obj(
                "cropId", Json.str(x.protocolId() != null ? x.protocolId() : x.entry().canonical()),
                "name", Json.str(x.entry().displayName()),
                "canonical", Json.str(x.entry().canonical()),
                "displayName", Json.str(x.entry().displayName()),
                "matchedBy", Json.str("exact"),
                "category", Json.str(x.group().label()),
                "categoryKey", Json.str(x.group().name()),
                "lifespan", Json.str(p == null ? "" : p.lifespan()),
                "totalDays", String.valueOf(p == null ? 0 : p.totalDays()),
                "window", Json.str(windowText(p)),
                "windowAction", Json.str(x.group().action()),
                "windowPrecision", Json.str(precisionOf(p)),
                "propsSource", Json.str(p == null ? "" : p.source()),
                "hasProtocol", String.valueOf(x.protocolId() != null && x.nodeCount() > 0),
                "nodeCount", String.valueOf(x.nodeCount()),
                "aliases", Json.arr(strArray(x.entry().aliases())),
                "pinyin", Json.arr(strArray(x.entry().pinyin())));
    }

    /**
     * 列表里展示的播期窗口。统一按华北基准算 —— 列表是"浏览"用，
     * 具体到某块地的时间和霜期，进时间线时才按真实经纬度算。
     */
    private static String windowText(CropProperties.Props p) {
        if (p == null) return "未限定";
        Set<Integer> months = PlantingWindow.monthsFor(p, 35.07, 116.31);
        return AgroZone.monthsText(months);
    }

    private static String precisionOf(CropProperties.Props p) {
        if (p == null) return "unknown";
        return p.hasSowMonths() ? p.precision() : "season-rule";
    }

    private static List<String> strArray(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s != null && !s.isBlank()) out.add(Json.str(s));
        }
        return out;
    }

    private record LibItem(CropRegistry.Entry entry, CropProperties.Props props,
                           CropTemplates.Group group, String protocolId, int nodeCount) {
    }
}
