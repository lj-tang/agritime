package com.cropcalendar.api;

import com.cropcalendar.user.Auth;
import com.cropcalendar.user.User;
import com.cropcalendar.user.UserStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /api/admin/* —— 超管后台的数据接口（T10）
 *
 *   GET /api/admin/users   账号列表（注册时间倒序）
 *   GET /api/admin/stats   概况：总数 / 今日新增 / 7 日活跃
 *
 * 只有 role=admin 能进。非管理员一律 403，
 * 未登录 401 —— 前端据此跳登录页而不是弹「无权限」。
 */
public class AdminHandler implements HttpHandler {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        if (!me.isAdmin()) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "需要管理员权限"));
            return;
        }

        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/stats")) {
            stats(ex);
        } else {
            users(ex);
        }
    }

    private void users(HttpExchange ex) throws IOException {
        List<User> all = new ArrayList<>(UserStore.listAll());
        all.sort(Comparator.comparing(User::createdAt).reversed());

        List<String> items = new ArrayList<>();
        for (User u : all) {
            items.add(u.toJson());
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "total", String.valueOf(items.size()),
                "items", Json.arr(items))));
    }

    private void stats(HttpExchange ex) throws IOException {
        String today = LocalDate.now().format(DAY);
        String weekAgo = LocalDate.now().minusDays(7).format(DAY);

        int adminCount = 0;
        for (User u : UserStore.listAll()) {
            if (u.isAdmin()) adminCount++;
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "total", String.valueOf(UserStore.count()),
                "newToday", String.valueOf(UserStore.countCreatedSince(today)),
                "active7d", String.valueOf(UserStore.countActiveSince(weekAgo)),
                "adminCount", String.valueOf(adminCount))));
    }
}
