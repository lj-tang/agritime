package com.cropcalendar.service;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 站点级开关（超管在后台改，全站立即生效）。
 *
 * 目前两个：
 *   · searchEnabled —— 关掉后前端禁用作物搜索入口，适合演示时锁定流程
 *   · llmEnabled    —— 关掉后不再调大模型，全部走本地规程库（省钱、也避免演示时被网络拖慢）
 *
 * 存 var/settings.json，缺字段一律按「开」处理 —— 开关类的默认值必须是
 * 「功能可用」，否则配置文件一坏全站就瘫。
 */
public final class SettingsStore {

    private static final Object LOCK = new Object();
    private static Boolean searchEnabled;
    private static Boolean llmEnabled;

    public static boolean searchEnabled() {
        ensure();
        synchronized (LOCK) {
            return searchEnabled;
        }
    }

    public static boolean llmEnabled() {
        ensure();
        synchronized (LOCK) {
            return llmEnabled;
        }
    }

    public static String toJson() {
        ensure();
        synchronized (LOCK) {
            return Json.obj(
                    "searchEnabled", String.valueOf(searchEnabled),
                    "llmEnabled", String.valueOf(llmEnabled));
        }
    }

    public static void update(boolean search, boolean llm) {
        ensure();
        synchronized (LOCK) {
            searchEnabled = search;
            llmEnabled = llm;
            persist();
        }
    }

    private static void ensure() {
        if (searchEnabled != null && llmEnabled != null) return;
        synchronized (LOCK) {
            if (searchEnabled != null && llmEnabled != null) return;
            load();
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        searchEnabled = Boolean.TRUE;
        llmEnabled = Boolean.TRUE;
        Path p = path();
        if (!Files.exists(p)) return;
        try {
            Object root = JsonParser.parse(Files.readString(p, StandardCharsets.UTF_8));
            if (root instanceof Map<?, ?> m) {
                Map<String, Object> mm = (Map<String, Object>) m;
                searchEnabled = readBool(mm, "searchEnabled", true);
                llmEnabled = readBool(mm, "llmEnabled", true);
            }
        } catch (Exception e) {
            System.err.println("[settings] 读取失败，全部按开启处理：" + e.getMessage());
        }
    }

    private static boolean readBool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s.trim());
        return fallback;
    }

    private static void persist() {
        Path p = path();
        try {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, Json.obj(
                    "searchEnabled", String.valueOf(searchEnabled),
                    "llmEnabled", String.valueOf(llmEnabled)), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[settings] 写入失败：" + e.getMessage());
        }
    }

    private static Path path() {
        String configured = AppConfig.adminSettingsPath();
        return Path.of(configured.isBlank() ? "var/settings.json" : configured);
    }

    private SettingsStore() {
    }
}
