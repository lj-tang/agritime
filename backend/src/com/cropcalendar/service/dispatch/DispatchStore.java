package com.cropcalendar.service.dispatch;

import com.cropcalendar.store.BlobStore;
import com.cropcalendar.store.StoreFactory;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.cropcalendar.util.AppTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 明日农活外发日志（F01）。
 *
 * 每次 preview/send-now 都留一条：发了什么、走哪个渠道、成功还是降级。
 * 按 userId 落盘，走 StoreFactory（file: var/dispatch_logs/&lt;id&gt;.json；
 * mysql: 同构的 dispatch_logs 表），与提醒/地块同一套持久化口径。
 *
 * 保留最近 MAX 条；账号注销时可由 AuthHandler 调 delete 清掉。
 */
public final class DispatchStore {

    private static final int MAX = 200;
    private static final Object LOCK = new Object();
    private static volatile BlobStore store;

    private DispatchStore() {
    }

    private static BlobStore store() {
        BlobStore s = store;
        if (s == null) {
            s = StoreFactory.forCollection("dispatch_logs");
            store = s;
        }
        return s;
    }

    public static void delete(String userId) {
        synchronized (LOCK) {
            try { store().delete(userId); } catch (IOException ignored) { }
        }
    }

    /** 追加一条日志，返回落盘后的完整视图（JSON，可直接塞进响应的 data）。 */
    @SuppressWarnings("unchecked")
    public static String append(String userId, Map<String, Object> entry) {
        synchronized (LOCK) {
            List<Map<String, Object>> logs = readAll(userId);
            Map<String, Object> row = new LinkedHashMap<>(entry);
            row.putIfAbsent("id", "dispatch_" + System.currentTimeMillis()
                    + "_" + (int) (Math.random() * 1000));
            row.putIfAbsent("sentAt", AppTime.now().toString());
            logs.add(0, row);
            if (logs.size() > MAX) logs = new ArrayList<>(logs.subList(0, MAX));
            try {
                List<String> out = new ArrayList<>();
                for (Map<String, Object> l : logs) out.add(Json.stringify(l));
                store().write(userId, Json.obj("version", Json.num(1), "logs", Json.arr(out)));
            } catch (IOException e) {
                System.err.println("[dispatch] 日志落盘失败：" + e.getMessage());
            }
            return view(logs);
        }
    }

    public static String read(String userId) {
        synchronized (LOCK) {
            return view(readAll(userId));
        }
    }

    private static String view(List<Map<String, Object>> logs) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> l : logs) out.add(Json.stringify(l));
        return Json.obj("count", Json.num(logs.size()), "logs", Json.arr(out));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readAll(String userId) {
        String json = store().read(userId);
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return new ArrayList<>();
        try {
            Object root = JsonParser.parse(json);
            if (!(root instanceof Map<?, ?> m) || !(m.get("logs") instanceof List<?> list)) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> lm) out.add((Map<String, Object>) lm);
            }
            out.sort(Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("sentAt"))).reversed());
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
