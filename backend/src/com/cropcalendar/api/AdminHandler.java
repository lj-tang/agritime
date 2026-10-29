package com.cropcalendar.api;

import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.service.AdminAudit;
import com.cropcalendar.service.AdminFiles;
import com.cropcalendar.service.SettingsStore;
import com.cropcalendar.service.user.UserStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * /api/admin/* —— 超管后台（T10）
 *
 *   GET  /api/admin/users          账号列表
 *   GET  /api/admin/stats          概况
 *   POST /api/admin/users/ban      封号 / 解封 {id, banned}
 *   GET  /api/admin/settings       站点开关
 *   PUT  /api/admin/settings       改开关 {searchEnabled, llmEnabled}
 *   GET  /api/admin/files?path=    列目录
 *   POST /api/admin/files          新建文件/目录 {path, dir}
 *   GET  /api/admin/file?path=     读文件
 *   PUT  /api/admin/file?path=     写文件（整份覆盖）
 *   DELETE /api/admin/file?path=   删除
 *
 * 全部要求 role=admin：未登录 401，非管理员 403。
 * 权限判断放在服务端，前端藏入口只是体验，不是安全边界。
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
        String method = ex.getRequestMethod();

        if (path.endsWith("/stats")) {
            stats(ex);
        } else if (path.endsWith("/users/ban")) {
            ban(ex);
        } else if (path.endsWith("/users/role")) {
            role(ex);
        } else if (path.endsWith("/users/password")) {
            resetPassword(ex);
        } else if (path.endsWith("/users")) {
            users(ex, method);
        } else if (path.endsWith("/audit")) {
            audit(ex);
        } else if (path.endsWith("/settings")) {
            settings(ex, method);
        } else if (path.endsWith("/files")) {
            files(ex, method);
        } else if (path.endsWith("/file")) {
            file(ex, method);
        } else {
            HttpSupport.write(ex, 404, HttpSupport.error(404, "接口不存在"));
        }
    }

    // ---------------- 账号 ----------------

    private void users(HttpExchange ex, String method) throws IOException {
        if ("DELETE".equalsIgnoreCase(method)) {
            deleteUser(ex);
            return;
        }
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

    private void ban(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String id = HttpSupport.field(body, "id");
        Object flag = body == null ? null : body.get("banned");
        boolean banned = flag instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(flag));

        if (id.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少账号 id"));
            return;
        }
        if (!UserStore.setBanned(id, banned)) {
            // 找不到、或者是管理员账号 —— 都不允许封
            HttpSupport.write(ex, 400, HttpSupport.error(400, "无法封禁该账号（不存在或为管理员）"));
            return;
        }
        AdminAudit.log(meName(ex), banned ? "封号" : "解封", id, "banned=" + banned);
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("id", Json.str(id), "banned", String.valueOf(banned))));
    }

    /** 升/降管理员 */
    private void role(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String id = HttpSupport.field(body, "id");
        String role = HttpSupport.field(body, "role");
        if (id.isEmpty() || (!"admin".equals(role) && !"user".equals(role))) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "参数不合法"));
            return;
        }
        User self = Auth.current(ex);
        // 不许改自己的角色，否则手一滑把自己降级就再也进不来
        if (self != null && self.id().equals(id)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "不能修改自己的角色"));
            return;
        }
        if (!UserStore.setRole(id, role)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "账号不存在"));
            return;
        }
        AdminAudit.log(meName(ex), "改角色", id, "role=" + role);
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("id", Json.str(id), "role", Json.str(role))));
    }

    /** 重置密码 */
    private void resetPassword(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String id = HttpSupport.field(body, "id");
        String password = HttpSupport.field(body, "password");
        if (id.isEmpty() || password.length() < 6) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "账号 id 缺失，或密码少于 6 位"));
            return;
        }
        if (!UserStore.resetPassword(id, password)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "账号不存在"));
            return;
        }
        // 审计里只记「改了谁」，绝不记密码本身
        AdminAudit.log(meName(ex), "重置密码", id, "已设置新密码");
        HttpSupport.write(ex, 200, HttpSupport.ok("null"));
    }

    /** 删号 */
    private void deleteUser(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String id = q.get("id");
        if (id == null || id.isBlank()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 id"));
            return;
        }
        User self = Auth.current(ex);
        if (self != null && self.id().equals(id)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "不能删除自己的账号"));
            return;
        }
        User target = UserStore.findById(id);
        if (target != null && target.isAdmin()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "不能删除管理员账号，请先取消其管理员身份"));
            return;
        }
        if (!UserStore.delete(id)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "账号不存在"));
            return;
        }
        AdminAudit.log(meName(ex), "删号", id, target == null ? "" : target.username());
        HttpSupport.write(ex, 200, HttpSupport.ok("null"));
    }

    /** 操作审计 */
    private void audit(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        int limit = 100;
        try {
            if (q.get("limit") != null) limit = Integer.parseInt(q.get("limit").trim());
        } catch (NumberFormatException ignored) {
            // 参数不合法就用默认条数
        }
        List<String> items = new ArrayList<>();
        for (String line : AdminAudit.tail(limit)) {
            items.add(Json.str(line));
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("items", Json.arr(items))));
    }

    /** 审计里记操作人用户名 */
    private static String meName(HttpExchange ex) {
        User u = Auth.current(ex);
        return u == null ? "unknown" : u.username();
    }

    private void stats(HttpExchange ex) throws IOException {
        String today = LocalDate.now().format(DAY);
        String weekAgo = LocalDate.now().minusDays(7).format(DAY);

        int adminCount = 0;
        int bannedCount = 0;
        for (User u : UserStore.listAll()) {
            if (u.isAdmin()) adminCount++;
            if (u.banned()) bannedCount++;
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "total", String.valueOf(UserStore.count()),
                "newToday", String.valueOf(UserStore.countCreatedSince(today)),
                "active7d", String.valueOf(UserStore.countActiveSince(weekAgo)),
                "adminCount", String.valueOf(adminCount),
                "bannedCount", String.valueOf(bannedCount))));
    }

    // ---------------- 站点开关 ----------------

    private void settings(HttpExchange ex, String method) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            HttpSupport.write(ex, 200, HttpSupport.ok(SettingsStore.toJson()));
            return;
        }
        if (!"PUT".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 GET / PUT"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体必须是 JSON 对象"));
            return;
        }
        boolean search = boolOf(body.get("searchEnabled"), SettingsStore.searchEnabled());
        boolean llm = boolOf(body.get("llmEnabled"), SettingsStore.llmEnabled());
        SettingsStore.update(search, llm);
        AdminAudit.log(meName(ex), "改开关", "site",
                "search=" + search + ",llm=" + llm);
        HttpSupport.write(ex, 200, HttpSupport.ok(SettingsStore.toJson()));
    }

    private static boolean boolOf(Object v, boolean fallback) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s.trim());
        return fallback;
    }

    // ---------------- 文件管理 ----------------

    private void files(HttpExchange ex, String method) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());

        if ("GET".equalsIgnoreCase(method)) {
            String dir = q.getOrDefault("path", "/");
            List<String> items = new ArrayList<>();
            for (AdminFiles.Entry e : AdminFiles.list(dir)) {
                items.add(Json.obj(
                        "name", Json.str(e.name()),
                        "path", Json.str(e.path()),
                        "dir", String.valueOf(e.dir()),
                        "size", String.valueOf(e.size()),
                        "mtime", Json.str(e.mtime())));
            }
            String data = Json.obj(
                    "root", Json.str(AdminFiles.root().toString()),
                    "path", Json.str(AdminFiles.rel(AdminFiles.resolve(dir))),
                    "items", Json.arr(items));
            HttpSupport.write(ex, 200, HttpSupport.ok(data));
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            Map<String, Object> body = HttpSupport.jsonBody(ex);
            String target = HttpSupport.field(body, "path");
            boolean isDir = boolOf(body == null ? null : body.get("dir"), false);
            if (target.isEmpty()) {
                HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少路径"));
                return;
            }
            try {
                AdminFiles.create(target, isDir);
                AdminAudit.log(meName(ex), isDir ? "新建目录" : "新建文件", target, "");
                HttpSupport.write(ex, 200, HttpSupport.ok("null"));
            } catch (IOException e) {
                HttpSupport.write(ex, 400, HttpSupport.error(400, e.getMessage()));
            }
            return;
        }

        HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 GET / POST"));
    }

    private void file(HttpExchange ex, String method) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String target = q.get("path");
        if (target == null || target.isBlank()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 path 参数"));
            return;
        }

        try {
            if ("GET".equalsIgnoreCase(method)) {
                String content = AdminFiles.read(target);
                HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                        "path", Json.str(AdminFiles.rel(AdminFiles.resolve(target))),
                        "content", Json.str(content))));
                return;
            }
            if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                String content = HttpSupport.body(ex);
                AdminFiles.write(target, content);
                AdminAudit.log(meName(ex), "改文件", target, content.length() + " 字符");
                HttpSupport.write(ex, 200, HttpSupport.ok("null"));
                return;
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                AdminFiles.delete(target);
                AdminAudit.log(meName(ex), "删文件", target, "");
                HttpSupport.write(ex, 200, HttpSupport.ok("null"));
                return;
            }
        } catch (IOException e) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, e.getMessage()));
            return;
        }

        HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 GET / PUT / DELETE"));
    }
}
