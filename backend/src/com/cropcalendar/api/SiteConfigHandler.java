package com.cropcalendar.api;

import com.cropcalendar.service.SettingsStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * /api/site-config —— 公开的站点开关（T10）
 *
 * 前端只拿得到「能不能搜索」「大模型是否可用」这两个布尔，
 * 用来决定是否禁用入口、是否提示。不暴露任何后台信息。
 */
public class SiteConfigHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "searchEnabled", String.valueOf(SettingsStore.searchEnabled()),
                "llmEnabled", String.valueOf(SettingsStore.llmEnabled()))));
    }
}
