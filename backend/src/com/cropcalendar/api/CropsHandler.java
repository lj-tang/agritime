package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.CropInfo;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/crops —— 返回支持的作物列表（A7）。
 *
 * 供前端作物选择下拉框使用，包含预设作物与 LLM 已动态生成的作物。
 */
public class CropsHandler implements HttpHandler {

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

        List<CropInfo> crops = ComputeStub.listCropInfos();
        List<String> items = new ArrayList<>();
        for (CropInfo c : crops) {
            items.add(Json.obj(
                    "cropId", Json.str(c.cropId()),
                    "name", Json.str(c.name()),
                    "nodeCount", String.valueOf(c.nodeCount())));
        }

        String body = Json.obj(
                "total", String.valueOf(crops.size()),
                "crops", Json.arr(items));
        HttpSupport.write(ex, 200, body);
    }
}