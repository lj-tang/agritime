package com.cropcalendar.api;

import com.cropcalendar.util.AppTime;
import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.service.AdminAudit;
import com.cropcalendar.service.AdminFiles;
import com.cropcalendar.service.SettingsStore;
import com.cropcalendar.service.crop.ProtocolReviewStore;
import com.cropcalendar.service.user.UserStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
        } else if (path.endsWith("/protocols/review")) {
            reviewProtocol(ex);
        } else if (path.endsWith("/protocols")) {
            protocols(ex);
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
        String today = AppTime.today().format(DAY);
        String weekAgo = AppTime.today().minusDays(7).format(DAY);

        int adminCount = 0;
        int bannedCount = 0;
        for (User u : UserStore.listAll()) {
            if (u.isAdmin()) adminCount++;
            if (u.banned()) bannedCount++;
        }

        // 规程复核覆盖进度 + 全站工单概况，给后台首页两个新卡片用
        int protocolReviewed = 0;
        int protocolRejected = 0;
        int protocolPending = 0;
        for (ComputeStub.ProtocolMeta meta : ComputeStub.protocolMetas()) {
            switch (effectiveStatus(meta)) {
                case "reviewed" -> protocolReviewed++;
                case "rejected" -> protocolRejected++;
                default -> protocolPending++;
            }
        }
        Map<String, Integer> tickets = SupportHandler.adminStatusCounts();

        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "total", String.valueOf(UserStore.count()),
                "newToday", String.valueOf(UserStore.countCreatedSince(today)),
                "active7d", String.valueOf(UserStore.countActiveSince(weekAgo)),
                "adminCount", String.valueOf(adminCount),
                "bannedCount", String.valueOf(bannedCount),
                "protocolTotal", String.valueOf(ComputeStub.protocolMetas().size()),
                "protocolReviewed", String.valueOf(protocolReviewed),
                "protocolPending", String.valueOf(protocolPending),
                "protocolRejected", String.valueOf(protocolRejected),
                "ticketTotal", String.valueOf(tickets.getOrDefault("all", 0)),
                "ticketOpen", String.valueOf(tickets.getOrDefault("open", 0)),
                "ticketUnread", String.valueOf(tickets.getOrDefault("unread", 0)))));
    }

    // ---------------- 作物规程复核 ----------------

    /**
     * 规程列表。默认 reviewStatus 来自数据文件（387 套生成规程统一是 pending_review），
     * 后台复核过的作物在 var/protocol-reviews.json 里有覆盖记录，这里合并成有效状态。
     */
    private void protocols(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String filter = q.get("status") == null ? "all" : q.get("status").trim().toLowerCase();
        if (!"all".equals(filter) && !"pending".equals(filter)
                && !"reviewed".equals(filter) && !"rejected".equals(filter)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "status 只能是 all / pending / reviewed / rejected"));
            return;
        }
        String keyword = q.get("q") == null ? "" : q.get("q").trim().toLowerCase();
        int limit = 200;
        try {
            if (q.get("limit") != null) limit = Math.max(1, Math.min(500, Integer.parseInt(q.get("limit").trim())));
        } catch (NumberFormatException ignored) {
            // 参数不合法就用默认条数
        }

        List<ComputeStub.ProtocolMeta> metas = new ArrayList<>(ComputeStub.protocolMetas());
        metas.sort(Comparator.comparing(ComputeStub.ProtocolMeta::cropId));
        int[] counts = new int[4]; // all / pending / reviewed / rejected
        List<String> items = new ArrayList<>();
        for (ComputeStub.ProtocolMeta meta : metas) {
            String status = effectiveStatus(meta);
            counts[0]++;
            switch (status) {
                case "reviewed" -> counts[2]++;
                case "rejected" -> counts[3]++;
                default -> counts[1]++;
            }
            if (!"all".equals(filter) && !status.equals(filter)) continue;
            if (!keyword.isEmpty() && !matchesProtocol(meta, keyword)) continue;
            if (items.size() >= limit) continue;

            ProtocolReviewStore.Review review = ProtocolReviewStore.get(meta.cropId());
            List<String> regions = new ArrayList<>();
            for (String region : meta.regions()) regions.add(Json.str(region));
            items.add(Json.obj(
                    "cropId", Json.str(meta.cropId()),
                    "name", Json.str(meta.name()),
                    "standardName", Json.str(meta.standardName()),
                    "source", Json.str(meta.source()),
                    "nodeCount", String.valueOf(meta.nodeCount()),
                    "regions", Json.arr(regions),
                    "defaultStatus", Json.str(meta.reviewStatus()),
                    "reviewStatus", Json.str(status),
                    "reviewer", Json.str(review == null ? "" : review.reviewer()),
                    "reviewedAt", Json.str(review == null ? "" : review.at()),
                    "note", Json.str(review == null ? "" : review.note())));
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "items", Json.arr(items),
                "total", String.valueOf(items.size()),
                "countAll", String.valueOf(counts[0]),
                "countPending", String.valueOf(counts[1]),
                "countReviewed", String.valueOf(counts[2]),
                "countRejected", String.valueOf(counts[3]),
                "limit", String.valueOf(limit))));
    }

    /** 记录一条人工复核结论：通过 / 驳回 / 撤回（回到待复核）。 */
    private void reviewProtocol(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String cropId = HttpSupport.field(body, "cropId");
        String status = HttpSupport.field(body, "status");
        String note = HttpSupport.field(body, "note");
        if (cropId.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 cropId"));
            return;
        }
        if (status.isEmpty() || (!"reviewed".equals(status) && !"rejected".equals(status) && !"pending".equals(status))) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "status 只能是 reviewed / rejected / pending"));
            return;
        }
        if (ComputeStub.protocolMeta(cropId) == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "没有该作物的规程：" + cropId));
            return;
        }
        try {
            ProtocolReviewStore.Review review = ProtocolReviewStore.apply(cropId, status, meName(ex), note);
            String effective = review == null ? "pending" : review.status();
            AdminAudit.log(meName(ex), "规程复核", cropId,
                    effective + (note.isEmpty() ? "" : ("：" + note)));
            HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                    "cropId", Json.str(cropId),
                    "reviewStatus", Json.str(effective))));
        } catch (IllegalArgumentException e) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, e.getMessage()));
        } catch (IOException e) {
            HttpSupport.write(ex, 500, HttpSupport.error(500, "保存复核记录失败：" + e.getMessage()));
        }
    }

    /** 覆盖记录优先；没有覆盖则按数据文件的默认 reviewStatus 归类。 */
    private static String effectiveStatus(ComputeStub.ProtocolMeta meta) {
        ProtocolReviewStore.Review review = ProtocolReviewStore.get(meta.cropId());
        if (review != null) return review.status();
        String raw = meta.reviewStatus() == null ? "" : meta.reviewStatus().trim().toLowerCase();
        if (raw.startsWith("reject")) return "rejected";
        if (raw.contains("pending")) return "pending";
        if (raw.contains("review") || "approved".equals(raw) || "reviewed".equals(raw)) return "reviewed";
        return "pending";
    }

    private static boolean matchesProtocol(ComputeStub.ProtocolMeta meta, String keyword) {
        String[] fields = {meta.cropId(), meta.name(), meta.standardName(), meta.source()};
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(keyword)) return true;
        }
        for (String region : meta.regions()) {
            if (region != null && region.toLowerCase().contains(keyword)) return true;
        }
        return false;
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
