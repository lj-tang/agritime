package com.cropcalendar.service.aqua;

import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水产养殖品种库的唯一读取入口。
 *
 * 之前 /api/industry 读 species.json，/api/pond-status 又在代码里硬编码了一份
 * 九种鱼的适温区间 —— 同一个"草鱼适温 18~30℃"存了两份，改一处必错一处。
 * 现在两边都从这里取，数据只在 species.json 里维护。
 */
public final class SpeciesCatalog {

    private static final String[] PATHS = {
            "../data/industries/aquaculture/species.json",
            "data/industries/aquaculture/species.json",
            "/agritime/data/industries/aquaculture/species.json"
    };

    private static volatile List<Map<String, Object>> cache;

    private SpeciesCatalog() {
    }

    /** 全部品种（原始结构，含 stages），供生产日历使用。 */
    public static List<Map<String, Object>> all() {
        List<Map<String, Object>> local = cache;
        if (local != null) return local;
        synchronized (SpeciesCatalog.class) {
            if (cache != null) return cache;
            List<Map<String, Object>> list = new ArrayList<>();
            for (String p : PATHS) {
                try {
                    Path path = Path.of(p);
                    if (!Files.isReadable(path)) continue;
                    Object root = JsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
                    if (root instanceof Map<?, ?> rm && rm.get("species") instanceof List<?> arr) {
                        for (Object o : arr) {
                            if (!(o instanceof Map<?, ?> s)) continue;
                            Map<String, Object> item = new LinkedHashMap<>();
                            s.forEach((k, v) -> item.put(String.valueOf(k), v));
                            list.add(item);
                        }
                    }
                    if (!list.isEmpty()) break;
                } catch (Exception e) {
                    System.err.println("[species] 读取 " + p + " 失败：" + e.getMessage());
                }
            }
            cache = list;
            return cache;
        }
    }

    /** 按中文名或 id 找品种；找不到返回 null，由调用方决定降级方式。 */
    public static Map<String, Object> find(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return null;
        String q = nameOrId.trim();
        for (Map<String, Object> s : all()) {
            String name = str(s.get("name"));
            String id = str(s.get("id"));
            if (q.equals(name) || q.equalsIgnoreCase(id)) return s;
        }
        // 退一步做包含匹配，"草鱼苗"也能落到"草鱼"
        for (Map<String, Object> s : all()) {
            String name = str(s.get("name"));
            if (name != null && (q.contains(name) || name.contains(q))) return s;
        }
        return null;
    }

    public static double numOf(Map<String, Object> m, String key, double def) {
        if (m == null) return def;
        Object o = m.get(key);
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    public static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
