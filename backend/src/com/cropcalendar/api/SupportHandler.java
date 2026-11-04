package com.cropcalendar.api;

import com.cropcalendar.util.AppTime;
import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * /api/support/* —— 客服工单系统（T14 / V5 T18）
 *
 *   POST /api/support/ticket    用户提交工单 {subject, category, content}
 *   GET  /api/support/tickets   工单列表（分页 + 状态筛选）
 *                               ?status=all|open|processing|resolved&page=1&pageSize=10
 *   GET  /api/support/ticket    工单详情 ?id=xxx（查看即清当前身份的未读）
 *   POST /api/support/reply     回复 {ticketId, content}（用户/超管均可）
 *   POST /api/support/status    状态流转 {ticketId, status}（仅超管）
 *   POST /api/support/close     关闭 {ticketId}（仅超管，等价于 resolved）
 *
 * 鉴权：完全复用 T10 账号系统。当前登录用户由 Auth.current(ex) 拿到，
 * 超管判断走 User.isAdmin()；未登录只允许提交匿名工单（带访客标识）。
 * 不另起一套 token / 硬编码密码 —— 那会造出「两套超管」。
 *
 * V5 T18 补齐三件真事：
 *   1. 列表分页并返回总数、总页数、各状态计数，不再一次把全部工单甩给前端；
 *   2. 状态从"open/replied/closed"改成"待处理/处理中/已解决"，回复与查看都会
 *      驱动状态变化；旧数据在读取时自动归一，不丢历史工单；
 *   3. 未读计数分身份算：用户看"客服回了我没"，管理员看"用户又追问了没"。
 *      查看详情即清当前身份的未读，回复后对方未读 +1。
 *
 * 持久化：../data/support/tickets.json，读写加锁。这里必须整份序列化原始 Map ——
 * 早先用 ticketJson() 落盘会丢掉 userId 与未读字段，重启后用户就找不到自己的工单了。
 */
public class SupportHandler implements HttpHandler {

    private static final String DATA_FILE = "../data/support/tickets.json";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 三态固定；replied/closed 是 V4 旧值，读取时归一 */
    private static final Set<String> STATUSES = Set.of("open", "processing", "resolved");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile Map<String, Object> store;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        String path = ex.getRequestURI().getPath();
        try {
            if (path.endsWith("/ticket") && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                create(ex);
            } else if (path.endsWith("/tickets") && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
                list(ex);
            } else if (path.endsWith("/ticket") && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
                detail(ex);
            } else if (path.endsWith("/reply") && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                reply(ex);
            } else if (path.endsWith("/status") && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                status(ex);
            } else if (path.endsWith("/close") && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                close(ex);
            } else {
                HttpSupport.write(ex, 404, HttpSupport.error(404, "接口不存在"));
            }
        } catch (Exception e) {
            HttpSupport.write(ex, 500, HttpSupport.error(500, "服务异常：" + e.getMessage()));
        }
    }

    // ---------- 提交工单 ----------
    @SuppressWarnings("unchecked")
    private void create(HttpExchange ex) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体不是合法 JSON"));
            return;
        }
        String subject = HttpSupport.field(body, "subject");
        String category = HttpSupport.field(body, "category");
        String content = HttpSupport.field(body, "content");
        if (subject.isEmpty() || content.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请填写问题标题和描述"));
            return;
        }

        User u = Auth.current(ex);
        String vid = visitorId(ex);
        String userId = u != null ? u.id()
                : (vid != null ? "anon_" + vid : "anon_ip_" + ip(ex));
        String userName = u != null ? (u.nickname().isEmpty() ? u.username() : u.nickname()) : "访客";
        String now = FMT.format(AppTime.now());

        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("id", "T" + System.currentTimeMillis() + "_" + shortUuid());
        ticket.put("userId", userId);
        ticket.put("userName", userName);
        ticket.put("subject", subject);
        ticket.put("category", category.isEmpty() ? "其他" : category);
        ticket.put("content", content);
        ticket.put("status", "open");
        ticket.put("createdAt", now);
        ticket.put("updatedAt", now);
        ticket.put("lastReplyAt", "");
        ticket.put("lastReplyBy", "");
        ticket.put("unreadForUser", false);
        ticket.put("unreadForAdmin", true);
        ticket.put("replies", new ArrayList<>());

        lock.writeLock().lock();
        try {
            Map<String, Object> s = store();
            ((List<Object>) s.computeIfAbsent("tickets", k -> new ArrayList<>())).add(ticket);
            persist(s);
        } finally {
            lock.writeLock().unlock();
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(ticket, false)));
    }

    // ---------- 工单列表（分页 + 状态筛选） ----------
    @SuppressWarnings("unchecked")
    private void list(HttpExchange ex) throws IOException {
        User u = Auth.current(ex);
        boolean admin = u != null && u.isAdmin();
        boolean anonymous = u == null;

        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String statusRaw = HttpSupport.trimToNull(q.get("status"));
        String filter = (statusRaw == null || "all".equalsIgnoreCase(statusRaw)) ? null : normalizeStatus(statusRaw);
        int page = Math.max(1, intOf(q.get("page"), 1));
        int pageSize = clamp(intOf(q.get("pageSize"), DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);

        List<Map<String, Object>> all;
        lock.readLock().lock();
        try {
            all = new ArrayList<>((List<Map<String, Object>>) store().getOrDefault("tickets", new ArrayList<>()));
        } finally {
            lock.readLock().unlock();
        }

        // 当前身份可见的工单：超管全部，用户只看自己的，匿名看不到列表（前端用本地 id 逐个查详情）
        List<Map<String, Object>> visible = new ArrayList<>();
        if (!anonymous) {
            for (int i = all.size() - 1; i >= 0; i--) {
                Map<String, Object> t = all.get(i);
                boolean mine = u != null && str(t.get("userId")).equals(u.id());
                if (!admin && !mine) continue;
                visible.add(t);
            }
        }
        // 最近有活动的排前面：回复/改状态都会更新 updatedAt
        visible.sort((a, b) -> activityAt(b).compareTo(activityAt(a)));

        // 各状态计数基于"可见全集"，不受当前筛选影响，前端 tab 才能显示真实数量
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("all", visible.size());
        counts.put("open", 0);
        counts.put("processing", 0);
        counts.put("resolved", 0);
        int unread = 0;
        for (Map<String, Object> t : visible) {
            String st = normalizeStatus(str(t.get("status")));
            counts.put(st, counts.getOrDefault(st, 0) + 1);
            if (isUnread(t, admin)) unread++;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> t : visible) {
            if (filter != null && !filter.equals(normalizeStatus(str(t.get("status"))))) continue;
            filtered.add(t);
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);

        List<String> items = new ArrayList<>();
        for (Map<String, Object> t : filtered.subList(from, to)) {
            items.add(ticketSummary(t, admin));
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "items", Json.arr(items),
                "total", String.valueOf(total),
                "page", String.valueOf(page),
                "pageSize", String.valueOf(pageSize),
                "totalPages", String.valueOf(totalPages),
                "unreadCount", String.valueOf(unread),
                "statusCounts", Json.obj(
                        "all", String.valueOf(counts.getOrDefault("all", 0)),
                        "open", String.valueOf(counts.getOrDefault("open", 0)),
                        "processing", String.valueOf(counts.getOrDefault("processing", 0)),
                        "resolved", String.valueOf(counts.getOrDefault("resolved", 0))),
                "anonymous", Json.bool(anonymous),
                "isAdmin", Json.bool(admin))));
    }

    // ---------- 工单详情（查看即清当前身份的未读） ----------
    @SuppressWarnings("unchecked")
    private void detail(HttpExchange ex) throws IOException {
        String id = HttpSupport.trimToNull(HttpSupport.query(ex.getRequestURI().getRawQuery()).get("id"));
        if (id == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 id"));
            return;
        }
        User u = Auth.current(ex);
        Map<String, Object> t = find(id);
        if (t == null) {
            HttpSupport.write(ex, 404, HttpSupport.error(404, "工单不存在"));
            return;
        }
        boolean admin = u != null && u.isAdmin();
        boolean mine = u != null && str(t.get("userId")).equals(u.id());
        if (!admin && !mine) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "无权查看该工单"));
            return;
        }

        lock.writeLock().lock();
        try {
            if (admin) t.put("unreadForAdmin", false);
            else t.put("unreadForUser", false);
            persist(store());
        } finally {
            lock.writeLock().unlock();
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t, admin)));
    }

    // ---------- 回复 ----------
    @SuppressWarnings("unchecked")
    private void reply(HttpExchange ex) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体不是合法 JSON"));
            return;
        }
        String ticketId = HttpSupport.field(body, "ticketId");
        String content = HttpSupport.field(body, "content");
        if (ticketId.isEmpty() || content.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 ticketId 或回复内容"));
            return;
        }

        User u = Auth.current(ex);
        Map<String, Object> t = find(ticketId);
        if (t == null) {
            HttpSupport.write(ex, 404, HttpSupport.error(404, "工单不存在"));
            return;
        }
        boolean admin = u != null && u.isAdmin();
        boolean mine = u != null && str(t.get("userId")).equals(u.id());
        if (!admin && !mine) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "无权回复该工单"));
            return;
        }

        String now = FMT.format(AppTime.now());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from", admin ? "admin" : "user");
        msg.put("who", admin ? (u.nickname().isEmpty() ? "客服" : u.nickname()) : str(t.get("userName")));
        msg.put("content", content);
        msg.put("time", now);

        lock.writeLock().lock();
        try {
            ((List<Object>) t.computeIfAbsent("replies", k -> new ArrayList<>())).add(msg);
            String current = normalizeStatus(str(t.get("status")));
            if (admin) {
                // 客服回过话，工单就算在处理中；已解决的也能被客服重新打开
                t.put("status", "processing");
                t.put("unreadForUser", true);
                t.put("unreadForAdmin", false);
                t.put("lastReplyBy", "admin");
            } else {
                // 用户在已解决工单下追问，视为重新打开
                t.put("status", "resolved".equals(current) ? "open" : current);
                t.put("unreadForAdmin", true);
                t.put("unreadForUser", false);
                t.put("lastReplyBy", "user");
            }
            t.put("lastReplyAt", now);
            t.put("updatedAt", now);
            persist(store());
        } finally {
            lock.writeLock().unlock();
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t, admin)));
    }

    // ---------- 状态流转（仅超管） ----------
    private void status(HttpExchange ex) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体不是合法 JSON"));
            return;
        }
        User u = Auth.current(ex);
        if (u == null || !u.isAdmin()) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "仅管理员可流转工单状态"));
            return;
        }
        String ticketId = HttpSupport.field(body, "ticketId");
        String target = HttpSupport.field(body, "status").trim().toLowerCase();
        if (ticketId.isEmpty() || !STATUSES.contains(target)) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "status 只能是 open / processing / resolved"));
            return;
        }
        Map<String, Object> t = find(ticketId);
        if (t == null) {
            HttpSupport.write(ex, 404, HttpSupport.error(404, "工单不存在"));
            return;
        }

        lock.writeLock().lock();
        try {
            applyStatus(t, target);
            persist(store());
        } finally {
            lock.writeLock().unlock();
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t, true)));
    }

    // ---------- 关闭（兼容旧调用，等价于已解决） ----------
    private void close(HttpExchange ex) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体不是合法 JSON"));
            return;
        }
        String ticketId = HttpSupport.field(body, "ticketId");
        if (ticketId.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "缺少 ticketId"));
            return;
        }
        User u = Auth.current(ex);
        if (u == null || !u.isAdmin()) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "仅管理员可关闭工单"));
            return;
        }
        Map<String, Object> t = find(ticketId);
        if (t == null) {
            HttpSupport.write(ex, 404, HttpSupport.error(404, "工单不存在"));
            return;
        }

        lock.writeLock().lock();
        try {
            applyStatus(t, "resolved");
            persist(store());
        } finally {
            lock.writeLock().unlock();
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t, true)));
    }

    private void applyStatus(Map<String, Object> t, String target) {
        String now = FMT.format(AppTime.now());
        t.put("status", target);
        t.put("updatedAt", now);
        if ("resolved".equals(target)) {
            // 解决是给用户的结论：用户端要看到未读，管理员自己的待办清零
            t.put("unreadForUser", true);
            t.put("unreadForAdmin", false);
        } else if ("open".equals(target)) {
            t.put("unreadForAdmin", true);
            t.put("unreadForUser", false);
        } else {
            t.put("unreadForAdmin", false);
            t.put("unreadForUser", false);
        }
    }

    // ---------- 序列化 ----------
    private String ticketSummary(Map<String, Object> t, boolean admin) {
        return Json.obj(
                "id", Json.str(str(t.get("id"))),
                "userName", Json.str(str(t.get("userName"))),
                "subject", Json.str(str(t.get("subject"))),
                "category", Json.str(str(t.get("category"))),
                "status", Json.str(normalizeStatus(str(t.get("status")))),
                "createdAt", Json.str(str(t.get("createdAt"))),
                "updatedAt", Json.str(activityAt(t)),
                "lastReplyAt", Json.str(str(t.get("lastReplyAt"))),
                "lastReplyBy", Json.str(str(t.get("lastReplyBy"))),
                "unread", Json.bool(isUnread(t, admin)),
                "replyCount", String.valueOf(((List<?>) t.getOrDefault("replies", List.of())).size()));
    }

    private String ticketJson(Map<String, Object> t, boolean admin) {
        List<String> replies = new ArrayList<>();
        for (Object o : (List<?>) t.getOrDefault("replies", List.of())) {
            if (o instanceof Map<?, ?> m) {
                replies.add(Json.obj(
                        "from", Json.str(str(m.get("from"))),
                        "who", Json.str(str(m.get("who"))),
                        "content", Json.str(str(m.get("content"))),
                        "time", Json.str(str(m.get("time")))));
            }
        }
        return Json.obj(
                "id", Json.str(str(t.get("id"))),
                "userId", Json.str(str(t.get("userId"))),
                "userName", Json.str(str(t.get("userName"))),
                "subject", Json.str(str(t.get("subject"))),
                "category", Json.str(str(t.get("category"))),
                "content", Json.str(str(t.get("content"))),
                "status", Json.str(normalizeStatus(str(t.get("status")))),
                "createdAt", Json.str(str(t.get("createdAt"))),
                "updatedAt", Json.str(activityAt(t)),
                "lastReplyAt", Json.str(str(t.get("lastReplyAt"))),
                "lastReplyBy", Json.str(str(t.get("lastReplyBy"))),
                "unread", Json.bool(isUnread(t, admin)),
                "replies", Json.arr(replies));
    }

    // ---------- 状态 / 未读 / 分页工具 ----------

    /** 旧值归一：replied → processing，closed → resolved，其余按待处理 */
    private static String normalizeStatus(String raw) {
        if (raw == null) return "open";
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "processing", "replied" -> "processing";
            case "resolved", "closed", "done" -> "resolved";
            default -> "open";
        };
    }

    private static boolean isUnread(Map<String, Object> t, boolean admin) {
        Object v = t.get(admin ? "unreadForAdmin" : "unreadForUser");
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static String activityAt(Map<String, Object> t) {
        String updated = str(t.get("updatedAt"));
        if (!updated.isEmpty()) return updated;
        String last = str(t.get("lastReplyAt"));
        if (!last.isEmpty()) return last;
        return str(t.get("createdAt"));
    }

    private static int intOf(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------- 存储 ----------
    @SuppressWarnings("unchecked")
    private Map<String, Object> store() throws IOException {
        Map<String, Object> s = store;
        if (s != null) return s;
        Path p = Path.of(DATA_FILE);
        if (Files.exists(p)) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            s = root instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
        } else {
            s = new LinkedHashMap<>();
        }
        // 旧工单补字段：状态归一、未读默认值、活动时间
        for (Object o : (List<Object>) s.getOrDefault("tickets", new ArrayList<>())) {
            if (o instanceof Map<?, ?> raw) migrate((Map<String, Object>) raw);
        }
        store = s;
        return s;
    }

    @SuppressWarnings("unchecked")
    private static void migrate(Map<String, Object> t) {
        String st = normalizeStatus(str(t.get("status")));
        t.put("status", st);
        if (str(t.get("updatedAt")).isEmpty()) {
            String last = str(t.get("lastReplyAt"));
            t.put("updatedAt", last.isEmpty() ? str(t.get("createdAt")) : last);
        }
        String lastFrom = "";
        List<Object> replies = (List<Object>) t.getOrDefault("replies", new ArrayList<>());
        if (!replies.isEmpty() && replies.get(replies.size() - 1) instanceof Map<?, ?> m) {
            lastFrom = str(m.get("from"));
        }
        if (!t.containsKey("lastReplyBy")) t.put("lastReplyBy", lastFrom);
        if (!t.containsKey("unreadForUser")) {
            t.put("unreadForUser", "admin".equals(lastFrom) && !"resolved".equals(st));
        }
        if (!t.containsKey("unreadForAdmin")) {
            t.put("unreadForAdmin", !"resolved".equals(st) && !"admin".equals(lastFrom));
        }
    }

    /**
     * 整份序列化原始 Map。早先这里用 ticketJson() 落盘，只写了响应字段，
     * userId / 未读状态全被丢掉 —— 服务重启后用户就看不到自己提交的工单。
     */
    private void persist(Map<String, Object> s) throws IOException {
        Path p = Path.of(DATA_FILE);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, Json.stringify(s), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> find(String id) {
        lock.readLock().lock();
        try {
            for (Object o : (List<Object>) store().getOrDefault("tickets", new ArrayList<>())) {
                if (o instanceof Map<?, ?> m && id.equals(str(m.get("id")))) {
                    return (Map<String, Object>) m;
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 匿名访客标识。
     *
     * 之前直接取 X-Forwarded-For 的第一段：nginx 用的是 proxy_add_x_forwarded_for，
     * 客户端自己带的头会排在前面，所以任何人不改代码就能伪造成别人，读到对方的工单。
     * 现在优先用 nginx 覆盖写入的 X-Real-IP（$remote_addr，客户端伪造不了）；
     * 再退到 XFF 的**最后一段**（nginx 追加的那一段），最后才用 socket 地址。
     */
    private String ip(HttpExchange ex) {
        String real = ex.getRequestHeaders().getFirst("X-Real-IP");
        if (isIpLike(real)) return real.trim();

        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String[] parts = fwd.split(",");
            String last = parts[parts.length - 1].trim();
            if (isIpLike(last)) return last;
        }
        return ex.getRemoteAddress() == null ? "unknown" : ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private boolean isIpLike(String s) {
        if (s == null) return false;
        String v = s.trim();
        if (v.isEmpty() || v.length() > 45) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == ':' || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 浏览器侧生成的访客 ID。同一出口 IP（校园网、公司 NAT）下所有人共用一个 IP，
     * 只按 IP 认定身份会让互不相识的人看到彼此的工单，所以优先用访客 ID。
     */
    private String visitorId(HttpExchange ex) {
        String v = ex.getRequestHeaders().getFirst("X-Visitor-Id");
        if (v == null) return null;
        String t = v.trim();
        if (t.length() < 8 || t.length() > 64) return null;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_';
            if (!ok) return null;
        }
        return t;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
