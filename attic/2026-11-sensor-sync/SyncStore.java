package com.cropcalendar.service.sync;

import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 多端数据同步存储（T30）。
 * 路径：var/sync/&lt;userId&gt;.json
 * 冲突合并：以 updatedAt 为准，保留修改历史。
 */
public final class SyncStore {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_HISTORY = 500;

    private SyncStore() {}

    private static Path dir() {
        Path p = Path.of("var", "sync");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    private static Path pathOf(String userId) {
        String safe = userId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return dir().resolve(safe + ".json");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(Path p) {
        if (!Files.isReadable(p)) return newState();
        try {
            Object root = JsonParser.parse(Files.readString(p, StandardCharsets.UTF_8));
            if (root instanceof Map<?, ?> m) {
                Map<String, Object> r = new LinkedHashMap<>();
                m.forEach((k, v) -> r.put(String.valueOf(k), v));
                return r;
            }
        } catch (Exception ignored) {}
        return newState();
    }

    private static Map<String, Object> newState() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("records", new LinkedHashMap<String, Object>());
        r.put("history", new ArrayList<Map<String, Object>>());
        r.put("lastSyncAt", LocalDateTime.now().format(TS));
        return r;
    }

    private static void save(Path p, Map<String, Object> state) {
        try {
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, Json.stringify(state), StandardCharsets.UTF_8);
            try { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException e) { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { System.err.println("[sync] 保存失败：" + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> records(Map<String, Object> state) {
        Object r = state.get("records");
        if (r instanceof Map<?, ?> m) {
            Map<String, Object> res = new LinkedHashMap<>();
            m.forEach((k, v) -> res.put(String.valueOf(k), v));
            return res;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> history(Map<String, Object> state) {
        Object h = state.get("history");
        if (h instanceof List<?> l) {
            List<Map<String, Object>> res = new ArrayList<>();
            for (Object o : l) if (o instanceof Map<?, ?> m) res.add((Map<String, Object>) m);
            return res;
        }
        return new ArrayList<>();
    }

    /** 推送数据：合并前端推送的记录，冲突以 updatedAt 为准 */
    @SuppressWarnings("unchecked")
    public static String push(String userId, Map<String, Object> body) {
        synchronized (LOCK) {
            Path p = pathOf(userId);
            Map<String, Object> state = load(p);
            Map<String, Object> recs = records(state);
            List<Map<String, Object>> hist = history(state);

            Object itemsObj = body.get("items");
            if (!(itemsObj instanceof List<?>)) itemsObj = List.of();
            List<?> items = (List<?>) itemsObj;

            int merged = 0, skipped = 0, conflict = 0;
            String device = body.get("device") != null ? String.valueOf(body.get("device")) : "unknown";

            for (Object o : items) {
                if (!(o instanceof Map<?, ?>)) continue;
                Map<String, Object> item = (Map<String, Object>) o;
                String id = String.valueOf(item.get("id"));
                String type = String.valueOf(item.get("type"));
                String updatedAt = item.get("updatedAt") != null ? String.valueOf(item.get("updatedAt")) : LocalDateTime.now().format(TS);
                boolean deleted = Boolean.TRUE.equals(item.get("deleted"));

                Map<String, Object> existing = (Map<String, Object>) recs.get(id);
                if (existing != null) {
                    String existUpdated = existing.get("updatedAt") != null ? String.valueOf(existing.get("updatedAt")) : "";
                    if (updatedAt.compareTo(existUpdated) <= 0) {
                        skipped++;
                        continue;
                    }
                    conflict++;
                    hist.add(historyEntry(id, "conflict", updatedAt, device, type + " 由 " + existUpdated + " 更新为 " + updatedAt));
                } else {
                    hist.add(historyEntry(id, "create", updatedAt, device, type));
                }

                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("id", id);
                rec.put("type", type);
                rec.put("data", item.get("data"));
                rec.put("updatedAt", updatedAt);
                rec.put("deleted", deleted);
                rec.put("device", device);
                recs.put(id, rec);
                merged++;
            }

            while (hist.size() > MAX_HISTORY) hist.remove(0);
            state.put("records", recs);
            state.put("history", hist);
            state.put("lastSyncAt", LocalDateTime.now().format(TS));
            save(p, state);

            return Json.obj(
                "merged", Json.num(merged),
                "skipped", Json.num(skipped),
                "conflict", Json.num(conflict),
                "lastSyncAt", Json.str((String) state.get("lastSyncAt"))
            );
        }
    }

    /** 拉取数据：返回 since 之后修改的记录 */
    @SuppressWarnings("unchecked")
    public static String pull(String userId, String since) {
        synchronized (LOCK) {
            Map<String, Object> state = load(pathOf(userId));
            Map<String, Object> recs = records(state);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object v : recs.values()) {
                if (!(v instanceof Map<?, ?>)) continue;
                Map<String, Object> rec = (Map<String, Object>) v;
                String updatedAt = rec.get("updatedAt") != null ? String.valueOf(rec.get("updatedAt")) : "";
                if (since == null || since.isEmpty() || updatedAt.compareTo(since) > 0) {
                    result.add(rec);
                }
            }
            return Json.obj(
                "items", Json.arr(result.stream().map(Json::stringify).collect(java.util.stream.Collectors.toList())),
                "lastSyncAt", Json.str(String.valueOf(state.getOrDefault("lastSyncAt", "")))
            );
        }
    }

    /** 导出数据 */
    @SuppressWarnings("unchecked")
    public static String exportData(String userId, String format) {
        synchronized (LOCK) {
            Map<String, Object> state = load(pathOf(userId));
            Map<String, Object> recs = records(state);
            if ("csv".equalsIgnoreCase(format)) {
                return exportCsv(recs);
            }
            return Json.stringify(state);
        }
    }

    @SuppressWarnings("unchecked")
    private static String exportCsv(Map<String, Object> recs) {
        StringBuilder sb = new StringBuilder("id,type,updatedAt,deleted,device,data_json\n");
        for (Object v : recs.values()) {
            if (!(v instanceof Map<?, ?>)) continue;
            Map<String, Object> rec = (Map<String, Object>) v;
            sb.append(csvVal(rec.get("id"))).append(',')
              .append(csvVal(rec.get("type"))).append(',')
              .append(csvVal(rec.get("updatedAt"))).append(',')
              .append(csvVal(rec.get("deleted"))).append(',')
              .append(csvVal(rec.get("device"))).append(',')
              .append(csvVal(Json.stringify(rec.get("data")))).append('\n');
        }
        return sb.toString();
    }

    private static String csvVal(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** 账号注销：删除所有同步数据 */
    public static String deleteAccount(String userId) {
        synchronized (LOCK) {
            Path p = pathOf(userId);
            String result = "ok";
            try {
                if (Files.isReadable(p)) Files.delete(p);
                else result = "not_found";
            } catch (IOException e) { result = "error: " + e.getMessage(); }
            return Json.obj("deleted", Json.str(result));
        }
    }

    /** 获取修改历史 */
    public static String history(String userId) {
        synchronized (LOCK) {
            Map<String, Object> state = load(pathOf(userId));
            List<Map<String, Object>> hist = history(state);
            return Json.obj("history", Json.arr(hist.stream().map(Json::stringify).collect(java.util.stream.Collectors.toList())));
        }
    }

    private static Map<String, Object> historyEntry(String id, String action, String at, String device, String summary) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("id", id);
        h.put("action", action);
        h.put("at", at);
        h.put("device", device);
        h.put("summary", summary);
        return h;
    }
}