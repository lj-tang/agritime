package com.cropcalendar.orchestration;

import com.cropcalendar.compute.ComputeStub.WeatherWindow;
import com.cropcalendar.compute.ComputeStub.WeatherWindow.Daily;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气缓存的磁盘副本 —— 进程重启后不必重新拉上游。
 *
 * 只存"真实拿到过"的天气（degraded 结果不落盘），TTL 与内存缓存一致；
 * 读写失败一律静默（缓存坏了不能影响出日历）。落盘位置与 llm-proc-cache 同级：
 * backend/var/weather-cache.json，服务账号可写、已在每日备份范围内。
 */
public final class WeatherCacheStore {

    private static final Path[] PATHS = {
            Path.of("var/weather-cache.json"),
            Path.of("../var/weather-cache.json"),
            Path.of("/agritime/backend/var/weather-cache.json")
    };

    /** dataTime 为上游数据时间文案，expireAtMs 为过期时刻。 */
    public record Cached(WeatherWindow window, String dataTime, long expireAtMs) {
    }

    private record Entry(Map<String, Daily> byDate, String dataTime, long expireAtMs) {
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile Path resolvedPath = null;

    private WeatherCacheStore() {
    }

    /** 取缓存：过期返回 null，不抛异常。 */
    public static Cached get(String key) {
        ensureLoaded();
        Entry e = ENTRIES.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expireAtMs()) {
            ENTRIES.remove(key);
            return null;
        }
        return new Cached(new WeatherWindow(e.byDate(), false, null), e.dataTime(), e.expireAtMs());
    }

    /** 写入并落盘；内容为空或 degraded 时忽略。 */
    public static void put(String key, WeatherWindow window, String dataTime, int ttlSeconds) {
        if (key == null || window == null || window.degraded() || window.byDate().isEmpty()) return;
        ensureLoaded();
        long expireAt = System.currentTimeMillis() + Math.max(1, ttlSeconds) * 1000L;
        ENTRIES.put(key, new Entry(new LinkedHashMap<>(window.byDate()), dataTime, expireAt));
        writeFile();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (WeatherCacheStore.class) {
            if (loaded) return;
            loaded = true;
            path();
            readFile();
        }
    }

    private static Path path() {
        Path p = resolvedPath;
        if (p != null) return p;
        synchronized (WeatherCacheStore.class) {
            if (resolvedPath != null) return resolvedPath;
            for (Path candidate : PATHS) {
                try {
                    if (Files.exists(candidate)) {
                        resolvedPath = candidate;
                        return candidate;
                    }
                } catch (Exception ignored) {
                    // 继续试下一个位置
                }
            }
            for (Path candidate : PATHS) {
                try {
                    Path parent = candidate.toAbsolutePath().getParent();
                    if (parent != null && Files.isDirectory(parent)) {
                        resolvedPath = candidate;
                        return candidate;
                    }
                } catch (Exception ignored) {
                    // 继续试下一个位置
                }
            }
            resolvedPath = Path.of("var/weather-cache.json");
            return resolvedPath;
        }
    }

    private static void readFile() {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            Object root = JsonParser.parse(Files.readString(p, StandardCharsets.UTF_8));
            if (!(root instanceof Map<?, ?> map)) return;
            Object entries = map.get("entries");
            if (!(entries instanceof Map<?, ?> em)) return;
            for (Map.Entry<?, ?> e : em.entrySet()) {
                if (!(e.getKey() instanceof String key)) continue;
                if (!(e.getValue() instanceof Map<?, ?> raw)) continue;
                Entry entry = parseEntry(raw);
                if (entry != null && System.currentTimeMillis() <= entry.expireAtMs()) {
                    ENTRIES.put(key, entry);
                }
            }
        } catch (Exception ignored) {
            // 文件损坏就当没有缓存，绝不能因为缓存拖垮时间线
        }
    }

    private static Entry parseEntry(Map<?, ?> raw) {
        try {
            Object days = raw.get("days");
            if (!(days instanceof Map<?, ?> dm)) return null;
            Map<String, Daily> byDate = new LinkedHashMap<>();
            for (Map.Entry<?, ?> d : dm.entrySet()) {
                if (!(d.getKey() instanceof String date)) continue;
                if (!(d.getValue() instanceof Map<?, ?> dv)) continue;
                byDate.put(date, new Daily(
                        num(dv.get("rain")), num(dv.get("wind")), num(dv.get("min")), num(dv.get("max"))));
            }
            if (byDate.isEmpty()) return null;
            String dataTime = raw.get("dataTime") instanceof String s ? s : "";
            long expireAt = (long) num(raw.get("expiresAt"));
            return new Entry(byDate, dataTime, expireAt);
        } catch (Exception e) {
            return null;
        }
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static synchronized void writeFile() {
        try {
            Path p = path();
            Path parent = p.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"version\":1,\"entries\":{");
            boolean first = true;
            for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                Entry entry = e.getValue();
                sb.append(Json.str(e.getKey())).append(":{");
                sb.append("\"dataTime\":").append(Json.str(entry.dataTime())).append(',');
                sb.append("\"expiresAt\":").append(entry.expireAtMs()).append(',');
                sb.append("\"days\":{");
                boolean firstDay = true;
                for (Map.Entry<String, Daily> d : entry.byDate().entrySet()) {
                    if (!firstDay) sb.append(',');
                    firstDay = false;
                    Daily daily = d.getValue();
                    sb.append(Json.str(d.getKey())).append(":{")
                            .append("\"rain\":").append(Json.num(daily.rainMm())).append(',')
                            .append("\"wind\":").append(Json.num(daily.windMs())).append(',')
                            .append("\"min\":").append(Json.num(daily.minTempC())).append(',')
                            .append("\"max\":").append(Json.num(daily.maxTempC()))
                            .append('}');
                }
                sb.append("}}");
            }
            sb.append("}}");
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // 落盘失败不影响本次请求
        }
    }
}
