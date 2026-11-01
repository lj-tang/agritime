package com.cropcalendar.api;

import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.service.reminder.ReminderStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * GET|POST /api/reminders —— 提醒记录（V5 T18）
 *
 * 以前提醒只是 localStorage 里的一个勾选标记，换个设备就没了。
 * 现在按账号存一份档案：分类（灾害天气/病害风险/农事适宜窗口）、
 * 三级风险、已读状态、开关设置都在服务端，刷新和重开页面仍在。
 *
 * 未登录返回 401 —— 前端据此降级读本地镜像，并明确提示"当前为本地提醒"。
 * 这样不登录也能用，登录后才跨设备同步，不会把人挡在门外。
 */
public class ReminderHandler implements HttpHandler {

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
            HttpSupport.write(ex, 200, HttpSupport.ok(ReminderStore.read(me.id())));
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            Map<String, Object> body = HttpSupport.jsonBody(ex);
            if (body == null) {
                HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体必须是 JSON 对象"));
                return;
            }
            try {
                HttpSupport.write(ex, 200, HttpSupport.ok(ReminderStore.sync(me.id(), body)));
            } catch (IllegalStateException e) {
                HttpSupport.write(ex, 500, HttpSupport.error(500, e.getMessage()));
            }
            return;
        }
        HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 GET / POST"));
    }
}
