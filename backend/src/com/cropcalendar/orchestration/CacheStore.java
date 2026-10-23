package com.cropcalendar.orchestration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存 —— W1 专用，不依赖任何外部存储。
 *
 * 手册里说「W1 完全不碰 Redis」，所以这里先用 ConcurrentHashMap 顶着。
 * W2 真要换成 Redis 时，只改这个类的实现，TimelineOrchestrator 一行都不用动 ——
 * 这就是把「存储」和「编排」分开的好处。
 */
public class CacheStore {

    private record Entry(Object value, long expireAtMs) {
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    public void put(String key, Object value, int ttlSeconds) {
        map.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    /** 取不到或已过期都返回 null，绝不抛异常。 */
    public <T> T get(String key, Class<T> type) {
        Entry e = map.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expireAtMs()) {
            map.remove(key);
            return null;
        }
        return type.cast(e.value());
    }

    public int size() {
        return map.size();
    }
}
