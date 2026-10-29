package com.cropcalendar.api;

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
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * /api/support/* —— 客服工单系统（T14）
 *
 *   POST /api/support/ticket    用户提交工单 {subject, category, content}
 *   GET  /api/support/tickets   工单列表：用户看自己的，超管看全部
 *   GET  /api/support/ticket    工单详情 ?id=xxx
 *   POST /api/support/reply     回复 {ticketId, content}（用户/超管均可）
 *   POST /api/support/close     关闭 {ticketId}（仅超管）
 *
 * 鉴权：完全复用 T10 账号系统。当前登录用户由 Auth.current(ex) 拿到，
 * 超管判断走 User.isAdmin()；未登录只允许提交匿名工单（带 IP 标识）。
 * 不另起一套 token / 硬编码密码 —— 那会造出「两套超管」。
 *
 * 持久化：../data/support/tickets.json，读写加锁，写操作同步落盘。
 */
public class SupportHandler implements HttpHandler {

    private static final String DATA_FILE = "../data/support/tickets.json";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        String userId = u != null ? u.id() : "anon_" + ip(ex);
        String userName = u != null ? (u.nickname().isEmpty() ? u.username() : u.nickname()) : "访客";

        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("id", "T" + System.currentTimeMillis() + "_" + shortUuid());
        ticket.put("userId", userId);
        ticket.put("userName", userName);
        ticket.put("subject", subject);
        ticket.put("category", category.isEmpty() ? "其他" : category);
        ticket.put("content", content);
        ticket.put("status", "open");
        ticket.put("createdAt", FMT.format(LocalDateTime.now()));
        ticket.put("replies", new ArrayList<>());

        lock.writeLock().lock();
        try {
            Map<String, Object> s = store();
            ((List<Object>) s.computeIfAbsent("tickets", k -> new ArrayList<>())).add(ticket);
            persist(s);
        } finally {
            lock.writeLock().unlock();
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(ticket)));
    }

    // ---------- 工单列表 ----------
    @SuppressWarnings("unchecked")
    private void list(HttpExchange ex) throws IOException {
        User u = Auth.current(ex);
        boolean admin = u != null && u.isAdmin();
        String filter = HttpSupport.trimToNull(HttpSupport.query(ex.getRequestURI().getRawQuery()).get("status"));

        lock.readLock().lock();
        List<Map<String, Object>> all;
        try {
            all = (List<Map<String, Object>>) store().getOrDefault("tickets", new ArrayList<>());
        } finally {
            lock.readLock().unlock();
        }

        List<String> items = new ArrayList<>();
        // 最新在前
        for (int i = all.size() - 1; i >= 0; i--) {
            Map<String, Object> t = all.get(i);
            boolean mine = u != null && str(t.get("userId")).equals(u.id());
            if (!admin && !mine) continue;          // 用户只看自己的，超管看全部
            if (filter != null && !filter.equals(str(t.get("status")))) continue;
            items.add(ticketSummary(t));
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.arr(items)));
    }

    // ---------- 工单详情 ----------
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
        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t)));
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
        if ("closed".equals(str(t.get("status")))) {
            HttpSupport.write(ex, 409, HttpSupport.error(409, "工单已关闭，无法回复"));
            return;
        }
        boolean admin = u != null && u.isAdmin();
        boolean mine = u != null && str(t.get("userId")).equals(u.id());
        if (!admin && !mine) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "无权回复该工单"));
            return;
        }

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from", admin ? "admin" : "user");
        msg.put("who", admin ? (u.nickname().isEmpty() ? "客服" : u.nickname()) : str(t.get("userName")));
        msg.put("content", content);
        msg.put("time", FMT.format(LocalDateTime.now()));

        lock.writeLock().lock();
        try {
            ((List<Object>) t.computeIfAbsent("replies", k -> new ArrayList<>())).add(msg);
            t.put("status", "replied");
            persist(store());
        } finally {
            lock.writeLock().unlock();
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t)));
    }

    // ---------- 关闭 ----------
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
            t.put("status", "closed");
            persist(store());
        } finally {
            lock.writeLock().unlock();
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(ticketJson(t)));
    }

    // ---------- 序列化 ----------
    private String ticketSummary(Map<String, Object> t) {
        return Json.obj(
                "id", Json.str(str(t.get("id"))),
                "userName", Json.str(str(t.get("userName"))),
                "subject", Json.str(str(t.get("subject"))),
                "category", Json.str(str(t.get("category"))),
                "status", Json.str(str(t.get("status"))),
                "createdAt", Json.str(str(t.get("createdAt"))),
                "replyCount", String.valueOf(((List<?>) t.getOrDefault("replies", List.of())).size()));
    }

    private String ticketJson(Map<String, Object> t) {
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
                "userName", Json.str(str(t.get("userName"))),
                "subject", Json.str(str(t.get("subject"))),
                "category", Json.str(str(t.get("category"))),
                "content", Json.str(str(t.get("content"))),
                "status", Json.str(str(t.get("status"))),
                "createdAt", Json.str(str(t.get("createdAt"))),
                "replies", Json.arr(replies));
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
        store = s;
        return s;
    }

    private void persist(Map<String, Object> s) throws IOException {
        Path p = Path.of(DATA_FILE);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, Json.obj("tickets", Json.arr(serializeTickets(s))), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private List<String> serializeTickets(Map<String, Object> s) {
        List<String> out = new ArrayList<>();
        for (Object o : (List<Object>) s.getOrDefault("tickets", new ArrayList<>())) {
            if (o instanceof Map<?, ?> m) out.add(ticketJson((Map<String, Object>) m));
        }
        return out;
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

    private String ip(HttpExchange ex) {
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return ex.getRemoteAddress() == null ? "unknown" : ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
