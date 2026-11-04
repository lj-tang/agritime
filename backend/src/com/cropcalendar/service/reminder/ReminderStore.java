package com.cropcalendar.service.reminder;

import com.cropcalendar.util.AppTime;
import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 提醒记录存储（V5 T18）。
 *
 * 以前 T04 的提醒只是 localStorage 里一个"这个节点我打过钩"的标记，
 * 换个浏览器就没了，更没有分类、等级、已读状态。这里做成一个用户一份的
 * 服务端档案：按 id 去重、保留已读状态、清掉过期项、按三级风险排序，
 * 并算出未读数。前端仍保留 localStorage 镜像，服务不可用时降级读本地。
 *
 * 存储路径与 UserDataStore 并列：var/userdata/&lt;id&gt;.json 的兄弟目录
 * var/reminders/&lt;id&gt;.json。写入用临时文件 + 原子替换，避免半截 JSON。
 */
public final class ReminderStore {

    /** 北京时间：容器 TZ=UTC 时"今天"也不能偏一天，否则过期清理会误删当天的提醒 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int MAX_ITEMS = 200;
    private static final int PRUNE_AFTER_DAYS = 7;
    // sensor：设备阈值告警（T17），由服务端写入，前端只读
    private static final Set<String> TYPES = Set.of("weather", "disease", "window", "sensor");
    private static final Set<String> LEVELS = Set.of("high", "medium", "low");

    private static final Object LOCK = new Object();

    private record State(List<Map<String, Object>> items, Map<String, Object> settings) {
    }

    private ReminderStore() {
    }

    /** 账号注销时删掉这份档案（V5 T28 的数据删除要求，提醒先落地）。 */
    public static void delete(String userId) {
        synchronized (LOCK) {
            try { Files.deleteIfExists(pathOf(userId)); } catch (IOException ignored) { }
        }
    }

    /** GET 用：返回可直接塞进响应 data 的 JSON。 */
    public static String read(String userId) {
        synchronized (LOCK) {
            State s = readState(userId);
            return view(s);
        }
    }

    /**
     * POST 用：合并前端推来的 items / settings / actions，落盘后返回最新视图。
     *
     * 合并口径：
     *   · 同 id 视为同一条提醒，只更新内容字段，已读状态与创建时间以服务端为准；
     *   · removeIds / readIds / readAll 是显式操作，不会被随后的 upsert 覆盖回未读；
     *   · 关掉某一类后该类仍保留在档案里，只是不出现在列表与未读数中，重开即恢复。
     */
    public static String sync(String userId, Map<String, Object> body) {
        synchronized (LOCK) {
            State s = readState(userId);
            Map<String, Object> settings = s.settings();
            List<Map<String, Object>> items = s.items();

            if (body.get("settings") instanceof Map<?, ?> rawSettings) {
                for (String t : TYPES) {
                    Object v = rawSettings.get(t);
                    if (v instanceof Boolean b) settings.put(t, b);
                }
            }

            Map<?, ?> actions = body.get("actions") instanceof Map<?, ?> a ? a : Map.of();
            List<?> removeIds = actions.get("removeIds") instanceof List<?> l ? l : List.of();
            List<?> readIds = actions.get("readIds") instanceof List<?> l ? l : List.of();
            boolean readAll = Boolean.TRUE.equals(actions.get("readAll"));

            Set<String> removed = strings(removeIds);
            Set<String> marked = strings(readIds);
            items.removeIf(it -> removed.contains(text(it.get("id"))));
            for (Map<String, Object> it : items) {
                if (readAll || marked.contains(text(it.get("id")))) it.put("read", Boolean.TRUE);
            }

            if (body.get("items") instanceof List<?> incoming) {
                for (Object o : incoming) {
                    Map<String, Object> fresh = normalize(o);
                    if (fresh == null) continue;
                    String id = (String) fresh.get("id");
                    int idx = indexOf(items, id);
                    if (idx >= 0) {
                        Map<String, Object> old = items.get(idx);
                        // 已读状态与创建时间是档案的一部分，不由前端推送覆盖
                        fresh.put("read", old.get("read"));
                        fresh.put("createdAt", old.get("createdAt"));
                        items.set(idx, fresh);
                    } else {
                        items.add(fresh);
                    }
                }
            }

            LocalDate cutoff = AppTime.today().minusDays(PRUNE_AFTER_DAYS);
            items.removeIf(it -> {
                String d = text(it.get("date"));
                if (d == null) return false;
                try { return LocalDate.parse(d, DATE).isBefore(cutoff); } catch (Exception e) { return false; }
            });
            items.sort(comparator());
            if (items.size() > MAX_ITEMS) {
                s = new State(new ArrayList<>(items.subList(0, MAX_ITEMS)), settings);
            } else {
                s = new State(items, settings);
            }
            writeState(userId, s);
            return view(s);
        }
    }

    /**
     * items 返回完整档案（含被关闭类别的记录），过滤由前端按 settings 做。
     * 不能只回可见项：那样关掉一类再打开，记录就真的丢在前端了，
     * 任务书要求"重新打开即恢复"。未读数只统计当前开启且未读的条目。
     */
    private static String view(State s) {
        List<String> all = new ArrayList<>();
        int unread = 0;
        for (Map<String, Object> it : s.items()) {
            all.add(Json.stringify(it));
            String type = text(it.get("type"));
            boolean enabled = type != null && Boolean.TRUE.equals(s.settings().getOrDefault(type, Boolean.TRUE));
            if (enabled && !Boolean.TRUE.equals(it.get("read"))) unread++;
        }
        return Json.obj(
                "items", Json.arr(all),
                "settings", Json.stringify(s.settings()),
                "unreadCount", String.valueOf(unread),
                "storage", Json.str("server"),
                "serverDate", Json.str(AppTime.today().format(DATE)));
    }

    private static Comparator<Map<String, Object>> comparator() {
        return (a, b) -> {
            int rank = Integer.compare(levelRank(text(a.get("level"))), levelRank(text(b.get("level"))));
            if (rank != 0) return rank;
            int byDate = String.valueOf(a.getOrDefault("date", "")).compareTo(String.valueOf(b.getOrDefault("date", "")));
            if (byDate != 0) return byDate;
            return String.valueOf(b.getOrDefault("createdAt", "")).compareTo(String.valueOf(a.getOrDefault("createdAt", "")));
        };
    }

    private static int levelRank(String level) {
        if ("high".equals(level)) return 0;
        if ("medium".equals(level)) return 1;
        return 2;
    }

    /** 字段白名单：前端多传的字段一律丢掉，避免把整个节点对象塞进档案。 */
    private static Map<String, Object> normalize(Object o) {
        if (!(o instanceof Map<?, ?> raw)) return null;
        String id = text(raw.get("id"));
        String type = text(raw.get("type"));
        String title = text(raw.get("title"));
        if (id == null || id.length() > 160 || type == null || !TYPES.contains(type) || title == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("level", LEVELS.contains(text(raw.get("level"))) ? text(raw.get("level")) : "low");
        m.put("title", clip(title, 200));
        m.put("detail", clip(text(raw.get("detail")), 400));
        m.put("advice", clip(text(raw.get("advice")), 400));
        m.put("date", dateOrNull(raw.get("date")));
        m.put("crop", clip(text(raw.get("crop")), 60));
        m.put("plotId", clip(text(raw.get("plotId")), 60));
        m.put("name", clip(text(raw.get("name")), 120));
        m.put("nodeKey", clip(text(raw.get("nodeKey")), 200));
        m.put("source", clip(text(raw.get("source")), 40));
        m.put("read", Boolean.TRUE.equals(raw.get("read")));
        m.put("createdAt", clip(text(raw.get("createdAt")), 40));
        return m;
    }

    private static String dateOrNull(Object o) {
        String s = text(o);
        if (s == null) return null;
        try { LocalDate.parse(s, DATE); return s; } catch (Exception e) { return null; }
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int indexOf(List<Map<String, Object>> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).get("id"))) return i;
        }
        return -1;
    }

    private static Set<String> strings(List<?> raw) {
        Set<String> out = new java.util.HashSet<>();
        for (Object o : raw) {
            String s = text(o);
            if (s != null) out.add(s);
        }
        return out;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    // ---------------- 文件读写 ----------------

    private static State readState(String userId) {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (String t : TYPES) settings.put(t, Boolean.TRUE);
        List<Map<String, Object>> items = new ArrayList<>();
        Path p = pathOf(userId);
        if (!Files.exists(p)) return new State(items, settings);
        try {
            Object root = JsonParser.parse(Files.readString(p, StandardCharsets.UTF_8));
            if (root instanceof Map<?, ?> m) {
                if (m.get("settings") instanceof Map<?, ?> sm) {
                    for (String t : TYPES) {
                        if (sm.get(t) instanceof Boolean b) settings.put(t, b);
                    }
                }
                if (m.get("items") instanceof List<?> arr) {
                    for (Object o : arr) {
                        Map<String, Object> n = normalize(o);
                        if (n != null) items.add(n);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[reminders] 档案解析失败 " + userId + "：" + e.getMessage() + "，按空档案处理");
        }
        items.sort(comparator());
        return new State(items, settings);
    }

    private static void writeState(String userId, State s) {
        Path p = pathOf(userId);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", s.items());
        root.put("settings", s.settings());
        root.put("updatedAt", AppTime.now().toString());
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, Json.stringify(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("提醒档案写入失败：" + e.getMessage(), e);
        }
    }

    private static Path pathOf(String userId) {
        String safe = (userId == null ? "anonymous" : userId).replaceAll("[^A-Za-z0-9_\\-]", "_");
        Path dataDir = Path.of(AppConfig.authDataDir());
        Path parent = dataDir.getParent() == null ? Path.of(".") : dataDir.getParent();
        return parent.resolve("reminders").resolve(safe + ".json");
    }
}
