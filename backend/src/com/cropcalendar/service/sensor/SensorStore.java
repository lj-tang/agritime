package com.cropcalendar.service.sensor;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 传感器设备与读数持久化（V5 T17）。
 *
 * 与 attic 里那份队友稿的区别：那份按 plotId 存、没有用户目录，知道 plotId 就能读别人的塘。
 * 这里改成 var/sensors/&lt;userId&gt;/&lt;plotId&gt;.json，归属由登录态决定，plotId 只是用户内部的编号。
 *
 * 一个文件里放两段：
 *   devices  —— 设备清单（名称、拉取地址、阈值、在线状态）
 *   readings —— 每个设备最近 N 条读数
 * 写入统一走临时文件 + 原子替换。
 */
public final class SensorStore {

    private static final Object LOCK = new Object();
    private static final int MAX_READINGS = 500;

    private SensorStore() {
    }

    private static Path pathOf(String userId, String plotId) {
        return Path.of("var", "sensors", safe(userId), safe(plotId) + ".json");
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static Map<String, Object> load(String userId, String plotId) {
        Path p = pathOf(userId, plotId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("devices", new ArrayList<Map<String, Object>>());
        out.put("readings", new LinkedHashMap<String, Object>());
        if (!Files.isReadable(p)) return out;
        try {
            Object root = JsonParser.parse(Files.readString(p, StandardCharsets.UTF_8));
            if (root instanceof Map<?, ?> m) {
                if (m.get("devices") instanceof List<?> l) {
                    List<Map<String, Object>> ds = new ArrayList<>();
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> dm) ds.add(copy(dm));
                    }
                    out.put("devices", ds);
                }
                if (m.get("readings") instanceof Map<?, ?> rm) {
                    Map<String, Object> rs = new LinkedHashMap<>();
                    rm.forEach((k, v) -> rs.put(String.valueOf(k), v));
                    out.put("readings", rs);
                }
            }
        } catch (Exception e) {
            System.err.println("[sensor] 读取失败 " + userId + "/" + plotId + "：" + e.getMessage());
        }
        return out;
    }

    private static void save(String userId, String plotId, Map<String, Object> state) throws IOException {
        Path p = pathOf(userId, plotId);
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, Json.stringify(state), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------------- 设备 ----------------

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> devices(String userId, String plotId) {
        synchronized (LOCK) {
            return (List<Map<String, Object>>) load(userId, plotId).get("devices");
        }
    }

    public static Map<String, Object> findDevice(String userId, String plotId, String deviceId) {
        for (Map<String, Object> d : devices(userId, plotId)) {
            if (deviceId != null && deviceId.equals(str(d.get("id")))) return d;
        }
        return null;
    }

    /** 新增或更新设备；返回落盘后的设备对象，失败返回 null。 */
    public static Map<String, Object> upsertDevice(String userId, String plotId, Map<String, Object> body) {
        synchronized (LOCK) {
            Map<String, Object> state = load(userId, plotId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) state.get("devices");
            String id = str(body.get("deviceId"));
            Map<String, Object> dev = null;
            if (id != null) {
                for (Map<String, Object> d : list) {
                    if (id.equals(str(d.get("id")))) { dev = d; break; }
                }
            }
            if (dev == null) {
                id = "dev_" + Long.toString(System.currentTimeMillis(), 36)
                        + "_" + Integer.toString((int) (Math.random() * 1000), 36);
                dev = new LinkedHashMap<>();
                dev.put("id", id);
                dev.put("createdAt", com.cropcalendar.util.AppTime.now().toString());
                list.add(dev);
            }
            dev.put("name", first(str(body.get("name")), "未命名设备"));
            dev.put("url", first(str(body.get("url")), ""));
            // T28：拉取间隔（分钟）。用户可自定义，缺省用配置；下限 1 分钟防止打爆设备
            double interval = dblOf(body.get("interval"), AppConfig.sensorDefaultIntervalMin());
            dev.put("intervalMin", Math.max(1, interval));
            if (body.get("thresholds") instanceof Map<?, ?> th) {
                Map<String, Object> clean = new LinkedHashMap<>();
                th.forEach((k, v) -> {
                    if (v instanceof Map<?, ?> range) {
                        clean.put(String.valueOf(k), copy(range));
                    }
                });
                dev.put("thresholds", clean);
            }
            dev.put("status", first(str(dev.get("status")), "unknown"));
            try {
                save(userId, plotId, state);
            } catch (IOException e) {
                System.err.println("[sensor] 写入失败：" + e.getMessage());
                return null;
            }
            return dev;
        }
    }

    public static boolean deleteDevice(String userId, String plotId, String deviceId) {
        synchronized (LOCK) {
            Map<String, Object> state = load(userId, plotId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) state.get("devices");
            boolean removed = list.removeIf(d -> deviceId != null && deviceId.equals(str(d.get("id"))));
            if (!removed) return false;
            try {
                save(userId, plotId, state);
            } catch (IOException e) {
                return false;
            }
            return true;
        }
    }

    public static void markStatus(String userId, String plotId, String deviceId, String status) {
        synchronized (LOCK) {
            Map<String, Object> state = load(userId, plotId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) state.get("devices");
            for (Map<String, Object> d : list) {
                if (deviceId != null && deviceId.equals(str(d.get("id")))) {
                    d.put("status", status);
                    if ("online".equals(status)) {
                        d.put("lastOnline", com.cropcalendar.util.AppTime.now().toString());
                    }
                    try {
                        save(userId, plotId, state);
                    } catch (IOException ignored) {
                        // 状态更新失败不影响读数本身
                    }
                    return;
                }
            }
        }
    }

    /** 记录一次后台调度拉取的时间点（成功与否都记，避免同一设备在一个周期内被反复拉）。 */
    public static void setLastPollAt(String userId, String plotId, String deviceId) {
        synchronized (LOCK) {
            Map<String, Object> state = load(userId, plotId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) state.get("devices");
            for (Map<String, Object> d : list) {
                if (deviceId != null && deviceId.equals(str(d.get("id")))) {
                    d.put("lastPollAt", com.cropcalendar.util.AppTime.now().toString());
                    try {
                        save(userId, plotId, state);
                    } catch (IOException ignored) {
                        // 调度时间戳写失败不致命
                    }
                    return;
                }
            }
        }
    }

    /** 枚举所有 (userId, plotId)：后台调度器靠它扫全量设备。 */
    public static List<String[]> listPlots() {
        List<String[]> out = new ArrayList<>();
        Path root = Path.of("var", "sensors");
        if (!Files.isDirectory(root)) return out;
        try (var users = Files.list(root)) {
            for (Path u : users.toList()) {
                if (!Files.isDirectory(u)) continue;
                String userId = u.getFileName().toString();
                try (var files = Files.list(u)) {
                    for (Path f : files.toList()) {
                        String fn = f.getFileName().toString();
                        if (!fn.endsWith(".json")) continue;
                        out.add(new String[]{userId, fn.substring(0, fn.length() - ".json".length())});
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[sensor] 枚举设备目录失败：" + e.getMessage());
        }
        return out;
    }

    public static double intervalMinOf(Map<String, Object> dev) {
        Object v = dev.get("intervalMin");
        if (v instanceof Number n) return Math.max(1, n.doubleValue());
        return AppConfig.sensorDefaultIntervalMin();
    }

    /**
     * 一次 load、在回调里批量改、一次 save（健壮性优化）。
     *
     * 之前调度器对每台设备都各自 load + save，同一个 plot 文件被反复读写 N 遍，
     * 设备一多就是 O(n²) 的文件 IO。这里把整块处理收敛成 load 一次、save 一次。
     *
     * 注意并发语义：load 和 save 各自加锁，中间的回调在锁外执行（允许做网络 IO）。
     * 调度器是单线程，不会与自身冲突；唯一可能的重叠是「手动拉取」与「调度拉取」
     * 同时命中同一 plot，此时后者 save 会覆盖前者，代价最多是某条读数/状态回退一个
     * 周期，下一个周期自动纠正 —— 传感器数据低频且非关键，这个取舍是可接受的。
     */
    public static void mutatePlot(String userId, String plotId,
                                  java.util.function.Consumer<Map<String, Object>> fn) {
        Map<String, Object> state;
        synchronized (LOCK) {
            state = load(userId, plotId);
        }
        fn.accept(state);
        synchronized (LOCK) {
            try {
                save(userId, plotId, state);
            } catch (IOException e) {
                System.err.println("[sensor] 批量落盘失败 " + userId + "/" + plotId + "：" + e.getMessage());
            }
        }
    }

    /** 纯内存追加一条读数（不加锁、不落盘），返回记录，供批量路径复用。 */
    static Map<String, Object> appendReadingInto(Map<String, Object> state, String deviceId,
                                                 Map<String, Object> values, String source) {
        @SuppressWarnings("unchecked")
        Map<String, Object> readings = (Map<String, Object>) state.get("readings");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = readings.get(deviceId) instanceof List<?> l
                ? new ArrayList<>((List<Map<String, Object>>) l)
                : new ArrayList<>();

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("ts", com.cropcalendar.util.AppTime.now().toString());
        rec.put("source", first(source, "manual"));
        Map<String, Object> v = new LinkedHashMap<>();
        values.forEach((k, val) -> {
            if (val instanceof Number || val instanceof String) v.put(k, val);
        });
        rec.put("values", v);
        list.add(0, rec);
        while (list.size() > MAX_READINGS) list.remove(list.size() - 1);
        readings.put(deviceId, list);
        return rec;
    }

    // ---------------- 读数 ----------------

    public static Map<String, Object> addReading(String userId, String plotId, String deviceId,
                                                 Map<String, Object> values, String source) {
        synchronized (LOCK) {
            Map<String, Object> state = load(userId, plotId);
            Map<String, Object> rec = appendReadingInto(state, deviceId, values, source);
            try {
                save(userId, plotId, state);
            } catch (IOException e) {
                System.err.println("[sensor] 读数写入失败：" + e.getMessage());
                return null;
            }
            return rec;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> readings(String userId, String plotId, String deviceId, int limit) {
        synchronized (LOCK) {
            Map<String, Object> state = load(userId, plotId);
            Map<String, Object> all = (Map<String, Object>) state.get("readings");
            List<Map<String, Object>> out = new ArrayList<>();
            if (deviceId != null) {
                if (all.get(deviceId) instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> m && out.size() < limit) out.add(copy(m));
                    }
                }
                return out;
            }
            all.forEach((k, v) -> {
                if (v instanceof List<?> l && !l.isEmpty() && out.size() < limit) {
                    Object first = l.get(0);
                    if (first instanceof Map<?, ?> m) {
                        Map<String, Object> one = copy(m);
                        one.put("deviceId", k);
                        out.add(one);
                    }
                }
            });
            return out;
        }
    }

    /** 账号注销时清掉该用户的传感器档案，与提醒/作物档案一起删。 */
    public static void purge(String userId) {
        synchronized (LOCK) {
            Path dir = Path.of("var", "sensors", safe(userId));
            try {
                if (!Files.isDirectory(dir)) return;
                try (var stream = Files.list(dir)) {
                    for (Path p : stream.toList()) Files.deleteIfExists(p);
                }
                Files.deleteIfExists(dir);
            } catch (IOException e) {
                System.err.println("[sensor] 清理失败 " + userId + "：" + e.getMessage());
            }
        }
    }

    // ---------------- 工具 ----------------

    private static Map<String, Object> copy(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    public static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    private static double dblOf(Object o, double fallback) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? fallback : Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
