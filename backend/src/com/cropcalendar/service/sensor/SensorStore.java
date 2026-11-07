package com.cropcalendar.service.sensor;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.store.BlobStore;
import com.cropcalendar.store.StoreFactory;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 传感器设备与读数持久化（V5 T17）。
 *
 * 与 attic 里那份队友稿的区别：那份按 plotId 存、没有用户目录，知道 plotId 就能读别人的塘。
 * 这里按用户隔离：store 的 key = userId，一份档案里放该用户全部地块
 * （plots: {plotId: {devices, readings}}），归属由登录态决定，plotId 只是用户内部编号。
 *
 * 存储走 {@link com.cropcalendar.store.StoreFactory}（T30）：file 后端是 var/sensors/&lt;userId&gt;.json，
 * mysql 后端是同构的 sensors 表；两种后端可切换、可回落。后台调度器经 listKeys() 枚举全量设备。
 */
public final class SensorStore {

    private static final Object LOCK = new Object();
    private static final int MAX_READINGS = 500;

    private SensorStore() {
    }

    private static volatile BlobStore store;

    private static BlobStore store() {
        BlobStore s = store;
        if (s == null) {
            s = StoreFactory.forCollection("sensors");
            store = s;
        }
        return s;
    }

    /** 读某个用户的整份档案 {plots: {plotId: {devices, readings}}}。不存在返回空结构。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readUser(String userId) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("plots", new LinkedHashMap<String, Object>());
        String json = store().read(userId);
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return user;
        try {
            Object root = JsonParser.parse(json);
            if (root instanceof Map<?, ?> m && m.get("plots") instanceof Map<?, ?> plots) {
                Map<String, Object> clean = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : plots.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> pm) clean.put(String.valueOf(e.getKey()), copy(pm));
                }
                user.put("plots", clean);
            }
        } catch (Exception e) {
            System.err.println("[sensor] 档案解析失败 " + userId + "：" + e.getMessage() + "，按空档案处理");
        }
        return user;
    }

    private static void writeUser(String userId, Map<String, Object> user) throws IOException {
        store().write(userId, Json.stringify(user));
    }

    /** 取某个地块的状态（devices/readings）。不存在返回带默认值的空结构。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String userId, String plotId) {
        Map<String, Object> user = readUser(userId);
        Map<String, Object> plots = (Map<String, Object>) user.get("plots");
        Map<String, Object> out = (Map<String, Object>) plots.get(plotId);
        if (out == null) {
            out = new LinkedHashMap<>();
            out.put("devices", new ArrayList<Map<String, Object>>());
            out.put("readings", new LinkedHashMap<String, Object>());
        }
        return out;
    }

    private static void save(String userId, String plotId, Map<String, Object> state) throws IOException {
        Map<String, Object> user = readUser(userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> plots = (Map<String, Object>) user.get("plots");
        plots.put(plotId, state);
        writeUser(userId, user);
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

    /** 枚举所有 (userId, plotId)：后台调度器靠它扫全量设备。走 store.listKeys()，两种后端一致。 */
    public static List<String[]> listPlots() {
        List<String[]> out = new ArrayList<>();
        for (String userId : store().listKeys()) {
            if (userId == null || userId.isBlank()) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> plots = (Map<String, Object>) readUser(userId).get("plots");
            for (Object plotId : plots.keySet()) {
                out.add(new String[]{userId, String.valueOf(plotId)});
            }
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
            try {
                store().delete(userId);
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
