package com.cropcalendar.api;

import com.cropcalendar.user.Auth;
import com.cropcalendar.user.User;
import com.cropcalendar.user.UserDataStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * /api/me/data —— 登录用户的业务数据（T10）
 *
 *   GET /api/me/data   取回 {crops, plots, history, updatedAt}
 *   PUT /api/me/data   整份覆盖写
 *
 * 为什么整份覆盖而不是逐条增量？数据量很小（几十条），
 * 增量接口要处理冲突、乱序、重放，收益远不如复杂度；
 * 前端已经按 updatedAt 做过合并，这里只做「最后一次写入生效」。
 */
public class MeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        User me = Auth.current(ex);
        if (me == null) {
            HttpSupport.write(ex, 401, HttpSupport.error(401, "请先登录"));
            return;
        }

        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            String stored = UserDataStore.read(me.id());
            // 直接透传，不解析 —— 存进去什么形状就吐什么形状
            HttpSupport.write(ex, 200, "{\"code\":0,\"message\":\"ok\",\"data\":" + stored + "}");
            return;
        }

        if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
            byte[] raw = ex.getRequestBody().readAllBytes();
            if (raw.length > UserDataStore.MAX_BYTES) {
                HttpSupport.write(ex, 413, HttpSupport.error(413, "数据过大，请清理后再试"));
                return;
            }
            String body = new String(raw, StandardCharsets.UTF_8).trim();
            if (!isJsonObject(body)) {
                HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体必须是 JSON 对象"));
                return;
            }
            try {
                UserDataStore.write(me.id(), body);
            } catch (IOException e) {
                HttpSupport.write(ex, 500, HttpSupport.error(500, "保存失败：" + e.getMessage()));
                return;
            }
            HttpSupport.write(ex, 200, HttpSupport.ok("null"));
            return;
        }

        HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 GET / PUT"));
    }

    /** 只做形状校验，不解析内容：以 { 开头、} 结尾、能被解析器吃下去 */
    private static boolean isJsonObject(String body) {
        if (body.isEmpty() || body.charAt(0) != '{') return false;
        try {
            com.cropcalendar.util.JsonParser.parse(body);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
