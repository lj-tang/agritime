package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.CropInfo;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            for (Map.Entry<String, Integer> e : nodeCount.entrySet()) {
                String display = CropRegistry.standardName(e.getKey());
                items.add(item(e.getKey(), display, "exact", true, e.getValue(), List.of(), List.of()));
            }
            HttpSupport.write(ex, 200, Json.obj(
                    "total", String.valueOf(items.size()),
                    "query", "null",
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
        List<String> aliasJson = new ArrayList<>();
        for (String a : aliases) aliasJson.add(Json.str(a));
        List<String> pinyinJson = new ArrayList<>();
        for (String p : pinyin) pinyinJson.add(Json.str(p));

        return Json.obj(
                "cropId", Json.str(cropId),
                "name", Json.str(displayName),
                "canonical", Json.str(cropId),
                "displayName", Json.str(displayName),
                "matchedBy", Json.str(matchedBy),
                "hasProtocol", String.valueOf(hasProtocol),
                "nodeCount", String.valueOf(nodeCount),
                "aliases", Json.arr(aliasJson),
                "pinyin", Json.arr(pinyinJson));
    }
}
