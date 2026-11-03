package com.cropcalendar.service.sensor;

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
 * 传感器设备与读数持久化（T28）。
 * 路径：var/sensors/&lt;plotId&gt;.json
 */
public final class SensorStore {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_READINGS = 1000;

    private SensorStore() {}

    private static Path dir() {
        Path p = Path.of("var", "sensors");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    private static Path pathOf(String plotId) {
        String safe = plotId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return dir().resolve(safe + ".json");
    }

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
        r.put("devices", new ArrayList<Map<String, Object>>());
        r.put("readings", new ArrayList<Map<String, Object>>());
        return r;
    }

    private static void save(Path p, Map<String, Object> state) {
        try {
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, Json.stringify(state), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[sensor] 保存失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> devices(Map<String, Object> state) {
        Object d = state.get("devices");
        if (d instanceof List<?> l) {
            List<Map<String, Object>> r = new ArrayList<>();
            for (Object o : l) if (o instanceof Map<?, ?> m) r.add((Map<String, Object>) m);
            return r;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readings(Map<String, Object> state) {
        Object r = state.get("readings");
        if (r instanceof List<?> l) {
            List<Map<String, Object>> res = new ArrayList<>();
            for (Object o : l) if (o instanceof Map<?, ?> m) res.add((Map<String, Object>) m);
            return res;
        }
        return new ArrayList<>();
    }

    /** 获取设备列表 */
    public static String listDevices(String plotId) {
        synchronized (LOCK) {
            Map<String, Object> state = load(pathOf(plotId));
            return Json.obj("devices", Json.arr(devices(state).stream().map(Json::stringify).collect(java.util.stream.Collectors.toList())));
        }
    }

    /** 添加或更新设备 */
    @SuppressWarnings("unchecked")
    public static String upsertDevice(String plotId, Map<String, Object> body) {
        synchronized (LOCK) {
            Path p = pathOf(plotId);
            Map<String, Object> state = load(p);
            List<Map<String, Object>> devs = devices(state);
            String id = body.get("id") != null ? String.valueOf(body.get("id")) : "dev_" + System.currentTimeMillis();
            Map<String, Object> dev = null;
            for (Map<String, Object> d : devs) {
                if (id.equals(String.valueOf(d.get("id")))) { dev = d; break; }
            }
            if (dev == null) {
                dev = new LinkedHashMap<>();
                dev.put("id", id);
                dev.put("createdAt", LocalDateTime.now().format(TS));
                devs.add(dev);
            }
            copyField(body, dev, "name");
            copyField(body, dev, "url");
            copyField(body, dev, "interval", 300);
            copyField(body, dev, "plotId", plotId);
            if (body.get("thresholds") instanceof Map<?, ?> t) {
                dev.put("thresholds", new LinkedHashMap<>((Map<String, Object>) t));
            }
            dev.put("status", "unknown");
            dev.put("lastOnline", null);
            state.put("devices", devs);
            save(p, state);
            return Json.obj("device", Json.stringify(dev));
        }
    }

    /** 删除设备 */
    public static String deleteDevice(String plotId, String deviceId) {
        synchronized (LOCK) {
            Path p = pathOf(plotId);
            Map<String, Object> state = load(p);
            List<Map<String, Object>> devs = devices(state);
            devs.removeIf(d -> deviceId.equals(String.valueOf(d.get("id"))));
            List<Map<String, Object>> rds = readings(state);
            rds.removeIf(r -> deviceId.equals(String.valueOf(r.get("deviceId"))));
            state.put("devices", devs);
            state.put("readings", rds);
            save(p, state);
            return Json.obj("deleted", Json.str(deviceId));
        }
    }

    /** 获取读数 */
    public static String listReadings(String plotId, String deviceId, int limit) {
        synchronized (LOCK) {
            Map<String, Object> state = load(pathOf(plotId));
            List<Map<String, Object>> rds = readings(state);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (int i = rds.size() - 1; i >= 0 && filtered.size() < limit; i--) {
                Map<String, Object> r = rds.get(i);
                if (deviceId == null || deviceId.equals(String.valueOf(r.get("deviceId")))) {
                    filtered.add(0, r);
                }
            }
            return Json.obj("readings", Json.arr(filtered.stream().map(Json::stringify).collect(java.util.stream.Collectors.toList())));
        }
    }

    /** 添加读数（手动或自动） */
    @SuppressWarnings("unchecked")
    public static String addReading(String plotId, String deviceId, Map<String, Object> values, String source) {
        synchronized (LOCK) {
            Path p = pathOf(plotId);
            Map<String, Object> state = load(p);
            List<Map<String, Object>> rds = readings(state);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("deviceId", deviceId);
            r.put("timestamp", LocalDateTime.now().format(TS));
            r.put("values", new LinkedHashMap<>(values));
            r.put("source", source != null ? source : "manual");
            rds.add(r);
            while (rds.size() > MAX_READINGS) rds.remove(0);
            state.put("readings", rds);
            List<Map<String, Object>> devs = devices(state);
            for (Map<String, Object> d : devs) {
                if (deviceId.equals(String.valueOf(d.get("id")))) {
                    d.put("status", "online");
                    d.put("lastOnline", r.get("timestamp"));
                    break;
                }
            }
            save(p, state);
            return Json.obj("reading", Json.stringify(r));
        }
    }

    /** 更新设备状态（离线判定） */
    public static void updateStatus(String plotId, String deviceId, String status, String lastOnline) {
        synchronized (LOCK) {
            Path p = pathOf(plotId);
            Map<String, Object> state = load(p);
            List<Map<String, Object>> devs = devices(state);
            for (Map<String, Object> d : devs) {
                if (deviceId.equals(String.valueOf(d.get("id")))) {
                    d.put("status", status);
                    if (lastOnline != null) d.put("lastOnline", lastOnline);
                    break;
                }
            }
            save(p, state);
        }
    }

    /** 获取所有设备的配置（供轮询用） */
    public static List<Map<String, Object>> allDevices() {
        synchronized (LOCK) {
            List<Map<String, Object>> all = new ArrayList<>();
            try {
                Path d = dir();
                if (!Files.isDirectory(d)) return all;
                for (Path f : Files.newDirectoryStream(d, "*.json")) {
                    Map<String, Object> state = load(f);
                    all.addAll(devices(state));
                }
            } catch (IOException ignored) {}
            return all;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getDevice(String plotId, String deviceId) {
        synchronized (LOCK) {
            Map<String, Object> state = load(pathOf(plotId));
            for (Map<String, Object> d : devices(state)) {
                if (deviceId.equals(String.valueOf(d.get("id")))) return d;
            }
            return null;
        }
    }

    private static void copyField(Map<String, Object> src, Map<String, Object> dst, String key) {
        if (src.containsKey(key)) dst.put(key, src.get(key));
    }

    private static void copyField(Map<String, Object> src, Map<String, Object> dst, String key, Object def) {
        if (src.containsKey(key) && src.get(key) != null) dst.put(key, src.get(key));
        else if (!dst.containsKey(key)) dst.put(key, def);
    }
}