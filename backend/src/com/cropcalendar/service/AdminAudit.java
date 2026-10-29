package com.cropcalendar.service;

import com.cropcalendar.config.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 超管操作审计（T10）
 *
 * 谁、什么时候、对谁、做了什么 —— 一行一条，追加写，不做删改。
 * 后台的封号、改角色、重置密码、删号、改开关都会留痕，
 * 出问题时能倒查是谁动的，而不是只能看「现在怎么变成这样了」。
 *
 * 用追加写而不是每次全量重写：审计日志的价值就在于「不可被悄悄改掉」，
 * 全量重写会顺手把历史一起覆盖。
 */
public final class AdminAudit {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();

    public static void log(String actor, String action, String target, String detail) {
        String line = String.join(" | ",
                LocalDateTime.now().format(TS),
                clean(actor), clean(action), clean(target), clean(detail));
        synchronized (LOCK) {
            try {
                Path p = path();
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                Files.writeString(p, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("[audit] 写审计日志失败：" + e.getMessage());
            }
        }
    }

    /** 取最近 n 条，新的在前 */
    public static List<String> tail(int limit) {
        Path p = path();
        List<String> out = new ArrayList<>();
        if (!Files.exists(p)) return out;
        try {
            List<String> all = Files.readAllLines(p, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - Math.max(1, Math.min(limit, 500)));
            for (int i = all.size() - 1; i >= from; i--) {
                String s = all.get(i);
                if (!s.isBlank()) out.add(s);
            }
        } catch (IOException e) {
            System.err.println("[audit] 读审计日志失败：" + e.getMessage());
        }
        return out;
    }

    /** 分隔符是 |，字段里的 | 换掉，避免把一行拆错 */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replace("|", "/").replace("\n", " ").replace("\r", " ").trim();
    }

    private static Path path() {
        String configured = AppConfig.adminAuditPath();
        return Path.of(configured.isBlank() ? "var/admin-audit.log" : configured);
    }

    private AdminAudit() {
    }
}
