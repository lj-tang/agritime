package com.cropcalendar.service.dispatch;

import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 明日农活外发配置读取（F01）：带缓存，文件不存在时用内置默认，不让闭环断掉。 */
public final class DispatchConfig {

    private static final String PATH = "../data/dispatch/config.json";
    private static volatile Map<String, Object> CACHE;

    private DispatchConfig() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> get() {
        Map<String, Object> c = CACHE;
        if (c != null) return c;
        synchronized (DispatchConfig.class) {
            if (CACHE != null) return CACHE;
            Map<String, Object> loaded = defaults();
            try {
                if (Files.exists(Path.of(PATH))) {
                    Object root = JsonParser.parse(Files.readString(Path.of(PATH), StandardCharsets.UTF_8));
                    if (root instanceof Map<?, ?> m) loaded = (Map<String, Object>) m;
                }
            } catch (Exception e) {
                System.err.println("[dispatch] 配置读取失败，用内置默认：" + e.getMessage());
            }
            CACHE = loaded;
            return loaded;
        }
    }

    /** 仅用于测试：换配置后清缓存。 */
    public static void clear() {
        CACHE = null;
    }

    @SuppressWarnings("unchecked")
    public static String template(String key) {
        Object t = get().get("templates");
        if (t instanceof Map<?, ?> tm && tm.get(key) != null) return String.valueOf(tm.get(key));
        return "action".equals(key)
                ? "明日{date}{plot}：{task}。{weatherNote}{toolNote}"
                : "明日{date}{plot}：{task}不宜，建议挪到{newDate}。{reason}";
    }

    public static int maxChars() {
        Object v = get().get("maxMessageChars");
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignored) { }
        }
        return 70;
    }

    @SuppressWarnings("unchecked")
    public static List<String> priority() {
        Object p = get().get("priority");
        if (p instanceof List<?> l) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            if (!out.isEmpty()) return out;
        }
        return List.of("sms", "serviceAccount", "localMock");
    }

    private static Map<String, Object> defaults() {
        return Map.of(
                "version", "1.0.0",
                "defaultSendHour", 18,
                "maxMessageChars", 70,
                "priority", List.of("sms", "serviceAccount", "localMock"));
    }
}
