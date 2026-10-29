package com.cropcalendar.api;

import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * /api/support —— 客服工单系统（T14）
 *
 * 接口：
 *   POST /api/support/login          超管登录 {"password":"xxx"} → {"token":"xxx"}
 *   POST /api/support/ticket         用户提交工单 {"userId","userName","subject","category","content"}
 *   GET  /api/support/tickets        工单列表 ?userId=xxx（用户）或 ?token=xxx（超管看全部）
 *   GET  /api/support/ticket         工单详情 ?id=xxx
 *   POST /api/support/reply          回复工单 {"ticketId","content","from":"user|admin","token?"}
 *   POST /api/support/close          关闭工单 {"ticketId","token"}
 *
 * 数据持久化：../data/support/tickets.json，每次写操作同步落盘。
 * 超管密码暂用固定值，生产环境应走配置 + 加盐哈希。
 */
public class SupportHandler implements HttpHandler {

    private static final String DATA_DIR = resolveDataDir();
    private static final String DATA_FILE = DATA_DIR + "/tickets.json";
    private static final String ADMIN_PASSWORD = "agritime2026";
    private static final Set<String> ADMIN_TOKENS = Collections.synchronizedSet(new HashSet<>());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile Map<String, Object> STORE;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        String method = ex.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        String path = ex.getRequestURI().getPath();
        try {
            if (path.endsWith("/login") && "POST".equalsIgnoreCase(method)) {
                handleLogin(ex);
            } else if (path.endsWith("/ticket") && "POST".equalsIgnoreCase(method)) {
                handleCreateTicket(ex);
            } else if (path.endsWith("/tickets") && "GET".equalsIgnoreCase(method)) {
                handleListTickets(ex);
            } else if (path.endsWith("/ticket") && "GET".equalsIgnoreCase(method)) {
                handleGetTicket(ex);
            } else if (path.endsWith("/reply") && "POST".equalsIgnoreCase(method)) {
                handleReply(ex);
            } else if (path.endsWith("/close") && "POST".equalsIgnoreCase(method)) {
                handleClose(ex);
            } else {
                HttpSupport.write(ex, 404, Json.obj("code", Json.str("40400"), "message", Json.str("未知接口")));
            }
        } catch (Exception e) {
            e.printStackTrace();
            HttpSupport.write(ex, 500, Json.obj("code", Json.str("50000"), "message", Json.str("服务异常：" + e.getMessage())));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleLogin(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String password = str(body.get("password"));
        if (!ADMIN_PASSWORD.equals(password)) {
            HttpSupport.write(ex, 401, Json.obj("code", Json.str("40101"), "message", Json.str("密码错误")));
            return;
        }
        String token = "tk_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 100000);
        ADMIN_TOKENS.add(token);
        HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "message", Json.str("ok"), "token", Json.str(token)));
    }

    @SuppressWarnings("unchecked")
    private void handleCreateTicket(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String userId = HttpSupport.trimToNull(str(body.get("userId")));
        userId = sessionUserId(ex) == null ? userId : sessionUserId(ex);
        String userName = str(body.get("userName"));
        String subject = HttpSupport.trimToNull(str(body.get("subject")));
        String category = str(body.get("category"));
        String content = HttpSupport.trimToNull(str(body.get("content")));

        if (userId == null || subject == null || content == null) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40001"), "message", Json.str("缺少必填字段：userId/subject/content")));
            return;
        }

        Map<String, Object> store = store();
        List<Map<String, Object>> tickets = ensureTicketsList(store);

        String ticketId = nextTicketId(tickets);
        String now = LocalDateTime.now().format(FMT);
        String msgId = ticketId.replace("T", "M") + "-1";

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", msgId);
        msg.put("from", "user");
        msg.put("content", content);
        msg.put("createdAt", now);

        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("id", ticketId);
        ticket.put("userId", userId);
        ticket.put("userName", userName.isEmpty() ? "匿名用户" : userName);
        ticket.put("subject", subject);
        ticket.put("category", category.isEmpty() ? "其他" : category);
        ticket.put("status", "open");
        ticket.put("createdAt", now);
        ticket.put("updatedAt", now);
        ticket.put("messages", new ArrayList<>(List.of(msg)));

        tickets.add(ticket);
        saveStore();

        HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "message", Json.str("ok"), "ticket", ticketToJson(ticket)));
    }

    @SuppressWarnings("unchecked")
    private void handleListTickets(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String userId = HttpSupport.trimToNull(q.get("userId"));
        String token = HttpSupport.trimToNull(q.get("token"));
        String status = HttpSupport.trimToNull(q.get("status"));

        boolean isAdmin = isSuperAdmin(ex, token);
        String sid = sessionUserId(ex);
        if (sid != null && !isAdmin) userId = sid;
        if (userId == null && !isAdmin) {
            HttpSupport.write(ex, 403, Json.obj("code", Json.str("40301"), "message", Json.str("需要 userId 或超管 token")));
            return;
        }

        Map<String, Object> store = store();
        List<Map<String, Object>> tickets = ensureTicketsList(store);

        List<String> items = new ArrayList<>();
        for (Map<String, Object> t : tickets) {
            if (userId != null && !userId.equals(str(t.get("userId")))) continue;
            if (status != null && !status.equals(str(t.get("status")))) continue;
            items.add(ticketSummaryJson(t));
        }

        HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "total", String.valueOf(items.size()), "tickets", Json.arr(items)));
    }

    @SuppressWarnings("unchecked")
    private void handleGetTicket(HttpExchange ex) throws IOException {
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String id = HttpSupport.trimToNull(q.get("id"));
        if (id == null) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40001"), "message", Json.str("缺少参数 id")));
            return;
        }
        Map<String, Object> store = store();
        List<Map<String, Object>> tickets = ensureTicketsList(store);
        for (Map<String, Object> t : tickets) {
            if (id.equals(str(t.get("id")))) {
                HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "ticket", ticketToJson(t)));
                return;
            }
        }
        HttpSupport.write(ex, 404, Json.obj("code", Json.str("40401"), "message", Json.str("工单不存在")));
    }

    @SuppressWarnings("unchecked")
    private void handleReply(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String ticketId = HttpSupport.trimToNull(str(body.get("ticketId")));
        String content = HttpSupport.trimToNull(str(body.get("content")));
        String from = str(body.get("from"));
        String token = str(body.get("token"));

        if (ticketId == null || content == null) {
            HttpSupport.write(ex, 400, Json.obj("code", Json.str("40001"), "message", Json.str("缺少必填字段：ticketId/content")));
            return;
        }
        if ("admin".equals(from) && !isSuperAdmin(ex, token)) {
            HttpSupport.write(ex, 403, Json.obj("code", Json.str("40301"), "message", Json.str("超管回复需要有效 token")));
            return;
        }
        if (from.isEmpty()) from = "user";

        Map<String, Object> store = store();
        List<Map<String, Object>> tickets = ensureTicketsList(store);
        for (Map<String, Object> t : tickets) {
            if (ticketId.equals(str(t.get("id")))) {
                // 登录用户只能回自己名下的工单，不能拿别人的工单号往上写
                if (!"admin".equals(from)) {
                    String sidR = sessionUserId(ex);
                    if (sidR != null && !sidR.equals(str(t.get("userId")))) {
                        HttpSupport.write(ex, 403, Json.obj("code", Json.str("40301"), "message", Json.str("只能回复自己的工单")));
                        return;
                    }
                }
                List<Map<String, Object>> msgs = (List<Map<String, Object>>) t.get("messages");
                if (msgs == null) { msgs = new ArrayList<>(); t.put("messages", msgs); }
                String now = LocalDateTime.now().format(FMT);
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("id", ticketId.replace("T", "M") + "-" + (msgs.size() + 1));
                msg.put("from", from);
                msg.put("content", content);
                msg.put("createdAt", now);
                msgs.add(msg);
                t.put("updatedAt", now);
                if ("admin".equals(from)) t.put("status", "replied");
                saveStore();
                HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "message", Json.str("ok"), "ticket", ticketToJson(t)));
                return;
            }
        }
        HttpSupport.write(ex, 404, Json.obj("code", Json.str("40401"), "message", Json.str("工单不存在")));
    }

    @SuppressWarnings("unchecked")
    private void handleClose(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String ticketId = HttpSupport.trimToNull(str(body.get("ticketId")));
        String token = str(body.get("token"));
        if (!isSuperAdmin(ex, token)) {
            HttpSupport.write(ex, 403, Json.obj("code", Json.str("40301"), "message", Json.str("需要超管 token")));
            return;
        }
        Map<String, Object> store = store();
        List<Map<String, Object>> tickets = ensureTicketsList(store);
        for (Map<String, Object> t : tickets) {
            if (ticketId.equals(str(t.get("id")))) {
                t.put("status", "closed");
                t.put("updatedAt", LocalDateTime.now().format(FMT));
                saveStore();
                HttpSupport.write(ex, 200, Json.obj("code", Json.str("0"), "message", Json.str("ok")));
                return;
            }
        }
        HttpSupport.write(ex, 404, Json.obj("code", Json.str("40401"), "message", Json.str("工单不存在")));
    }

    // ---- JSON 序列化 ----

    @SuppressWarnings("unchecked")
    private static String ticketToJson(Map<String, Object> t) {
        List<String> msgs = new ArrayList<>();
        List<Map<String, Object>> ml = (List<Map<String, Object>>) t.get("messages");
        if (ml != null) for (Map<String, Object> m : ml) msgs.add(messageJson(m));
        return Json.obj(
                "id", Json.str(str(t.get("id"))),
                "userId", Json.str(str(t.get("userId"))),
                "userName", Json.str(str(t.get("userName"))),
                "subject", Json.str(str(t.get("subject"))),
                "category", Json.str(str(t.get("category"))),
                "status", Json.str(str(t.get("status"))),
                "createdAt", Json.str(str(t.get("createdAt"))),
                "updatedAt", Json.str(str(t.get("updatedAt"))),
                "messages", Json.arr(msgs));
    }

    private static String messageJson(Map<String, Object> m) {
        return Json.obj(
                "id", Json.str(str(m.get("id"))),
                "from", Json.str(str(m.get("from"))),
                "content", Json.str(str(m.get("content"))),
                "createdAt", Json.str(str(m.get("createdAt"))));
    }

    @SuppressWarnings("unchecked")
    private static String ticketSummaryJson(Map<String, Object> t) {
        List<Map<String, Object>> msgs = (List<Map<String, Object>>) t.get("messages");
        int msgCount = msgs == null ? 0 : msgs.size();
        boolean hasAdminReply = msgs != null && msgs.stream().anyMatch(m -> "admin".equals(str(m.get("from"))));
        return Json.obj(
                "id", Json.str(str(t.get("id"))),
                "userId", Json.str(str(t.get("userId"))),
                "userName", Json.str(str(t.get("userName"))),
                "subject", Json.str(str(t.get("subject"))),
                "category", Json.str(str(t.get("category"))),
                "status", Json.str(str(t.get("status"))),
                "createdAt", Json.str(str(t.get("createdAt"))),
                "updatedAt", Json.str(str(t.get("updatedAt"))),
                "msgCount", String.valueOf(msgCount),
                "hasAdminReply", Json.bool(hasAdminReply));
    }

    // ---- 存储读写 ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> store() throws IOException {
        Map<String, Object> cached = STORE;
        if (cached != null) return cached;
        synchronized (SupportHandler.class) {
            if (STORE != null) return STORE;
            Path file = Path.of(DATA_FILE);
            if (!Files.exists(file)) {
                Files.createDirectories(Path.of(DATA_DIR));
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("tickets", new ArrayList<>());
                Files.writeString(file, "{\"tickets\":[]}", StandardCharsets.UTF_8);
                STORE = empty;
                return empty;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> m)) {
                throw new IOException("工单存储结构不对");
            }
            STORE = (Map<String, Object>) m;
            return STORE;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> ensureTicketsList(Map<String, Object> store) {
        Object t = store.get("tickets");
        if (t instanceof List<?> list) return (List<Map<String, Object>>) list;
        List<Map<String, Object>> newList = new ArrayList<>();
        store.put("tickets", newList);
        return newList;
    }

    private static void saveStore() throws IOException {
        if (STORE == null) return;
        Files.createDirectories(Path.of(DATA_DIR));
        Files.writeString(Path.of(DATA_FILE), storeToJson(STORE), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static String storeToJson(Map<String, Object> store) {
        List<Map<String, Object>> tickets = (List<Map<String, Object>>) store.get("tickets");
        List<String> items = new ArrayList<>();
        if (tickets != null) for (Map<String, Object> t : tickets) items.add(ticketToJson(t));
        return Json.obj("tickets", Json.arr(items));
    }

    private static String nextTicketId(List<Map<String, Object>> tickets) {
        int max = 0;
        for (Map<String, Object> t : tickets) {
            String id = str(t.get("id"));
            if (id.startsWith("T")) try { max = Math.max(max, Integer.parseInt(id.substring(1))); } catch (NumberFormatException ignored) {}
        }
        return "T" + String.format("%04d", max + 1);
    }

    // ---- 工具 ----

    private static Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        int len = 0;
        String cl = ex.getRequestHeaders().getFirst("Content-Length");
        if (cl != null) try { len = Integer.parseInt(cl); } catch (NumberFormatException ignored) {}
        if (len <= 0) return Map.of();
        byte[] buf = new byte[len];
        try (InputStream is = ex.getRequestBody()) {
            int off = 0;
            while (off < len) {
                int n = is.read(buf, off, len - off);
                if (n < 0) break;
                off += n;
            }
        }
        String text = new String(buf, 0, Math.min(len, buf.length), StandardCharsets.UTF_8);
        Object parsed = JsonParser.parse(text);
        if (parsed instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) m;
            return result;
        }
        return Map.of();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 数据目录解析：优先环境变量，其次 ../data/support，不可写则 fallback 到 ./data/support */
    private static String resolveDataDir() {
        String env = System.getenv("SUPPORT_DATA_DIR");
        if (env != null && !env.isBlank()) return env.trim();
        String[] candidates = { "../data/support", "data/support", "/tmp/agritime-support" };
        for (String dir : candidates) {
            try {
                java.nio.file.Path p = Path.of(dir);
                Files.createDirectories(p);
                java.nio.file.Path test = p.resolve(".wtest");
                Files.writeString(test, "1", StandardCharsets.UTF_8);
                Files.deleteIfExists(test);
                return dir;
            } catch (Exception ignored) {
            }
        }
        return "/tmp/agritime-support";
    }

    /**
     * 超管身份判定：老的独立 token 和统一账号体系（Cookie 会话）都认。
     * 这样超管登录后直接进客服后台，不用再输一遍密码。
     */
    private static boolean isSuperAdmin(HttpExchange ex, String token) {
        if (token != null && !token.isEmpty() && ADMIN_TOKENS.contains(token)) return true;
        try {
            com.cropcalendar.model.User u = com.cropcalendar.middleware.Auth.current(ex);
            return u != null && u.isAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    /** 当前登录账号；未登录返回 null。有会话时工单必须按这个账号隔离 */
    private static String sessionUserId(HttpExchange ex) {
        try {
            com.cropcalendar.model.User u = com.cropcalendar.middleware.Auth.current(ex);
            return u == null ? null : u.id();
        } catch (Exception e) {
            return null;
        }
    }
}