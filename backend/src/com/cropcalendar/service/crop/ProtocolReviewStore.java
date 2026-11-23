package com.cropcalendar.service.crop;

import com.cropcalendar.util.AppTime;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 作物规程的人工复核记录（后台「规程复核」用）。
 *
 * 为什么不直接改 backend/crop-protocols.json：
 *   · 那个文件 750KB、是由 data/init.sql 生成出来的产物，人工改完下次重新生成就丢了；
 *   · 生成器产出的 387 套规程统一是 pending_review，复核结论属于「运维数据」而不是「种子数据」，
 *     按项目惯例应落在 backend/var（服务账号可写、每日备份覆盖）。
 * 所以这里只持久化「覆盖结论」：cropId → {status, reviewer, at, note}，
 * 列表展示时把默认 reviewStatus 与覆盖结论合并成 effectiveStatus。
 *
 * 文件：var/protocol-reviews.json（相对后端工作目录，即 /agritime/backend/var/protocol-reviews.json，
 * 与工单、账号库同目录，已在每日备份范围内）。
 */
public final class ProtocolReviewStore {

    private static final Path FILE = Path.of("var/protocol-reviews.json");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();

    /** 只允许两种复核结论；清空覆盖用 status=null / "pending"。 */
    public static final Set<String> STATUSES = Set.of("reviewed", "rejected");

    public record Review(String status, String reviewer, String at, String note) {}

    private static volatile Map<String, Review> CACHE;

    private ProtocolReviewStore() {
    }

    public static Map<String, Review> all() {
        Map<String, Review> cached = CACHE;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (CACHE == null) CACHE = read();
            return CACHE;
        }
    }

    public static Review get(String cropId) {
        return cropId == null ? null : all().get(cropId);
    }

    /**
     * 写入/覆盖/清除一次复核结论。
     *
     * @param status reviewed / rejected；null 或 "pending" 表示清除该作物的复核覆盖
     */
    public static Review apply(String cropId, String status, String reviewer, String note) throws IOException {
        synchronized (LOCK) {
            Map<String, Review> next = new LinkedHashMap<>(all());
            Review review = null;
            if (status == null || status.isBlank() || "pending".equalsIgnoreCase(status)) {
                next.remove(cropId);
            } else {
                if (!STATUSES.contains(status)) {
                    throw new IllegalArgumentException("复核状态只能是 reviewed / rejected / pending");
                }
                review = new Review(status, reviewer == null ? "" : reviewer,
                        AppTime.now().format(FMT), note == null ? "" : note);
                next.put(cropId, review);
            }
            persist(next);
            CACHE = Collections.unmodifiableMap(next);
            return review;
        }
    }

    private static Map<String, Review> read() {
        Map<String, Review> out = new LinkedHashMap<>();
        if (!Files.isRegularFile(FILE)) return Collections.unmodifiableMap(out);
        try {
            Object root = JsonParser.parse(Files.readString(FILE, StandardCharsets.UTF_8));
            if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("reviews") instanceof Map<?, ?> reviews)) {
                System.err.println("[protocol-review] 复核记录结构不对，按空记录处理：" + FILE);
                return Collections.unmodifiableMap(out);
            }
            for (Map.Entry<?, ?> entry : reviews.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> m)) continue;
                String status = text(m.get("status"));
                if (!STATUSES.contains(status)) continue;
                out.put(String.valueOf(entry.getKey()),
                        new Review(status, text(m.get("reviewer")), text(m.get("at")), text(m.get("note"))));
            }
        } catch (Exception e) {
            System.err.println("[protocol-review] 读取复核记录失败，按空记录处理：" + e.getMessage());
        }
        return Collections.unmodifiableMap(out);
    }

    private static void persist(Map<String, Review> reviews) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"version\": 1,\n  \"updatedAt\": \"").append(AppTime.now().format(FMT)).append("\",\n");
        sb.append("  \"reviews\": {");
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Review> entry : reviews.entrySet()) {
            Review r = entry.getValue();
            items.add("    " + quote(entry.getKey()) + ": {"
                    + "\"status\": " + quote(r.status())
                    + ", \"reviewer\": " + quote(r.reviewer())
                    + ", \"at\": " + quote(r.at())
                    + ", \"note\": " + quote(r.note()) + "}");
        }
        if (!items.isEmpty()) {
            sb.append('\n').append(String.join(",\n", items)).append('\n').append("  ");
        }
        sb.append("}\n}\n");

        Files.createDirectories(FILE.toAbsolutePath().getParent());
        Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String text(Object v) {
        return v instanceof String s ? s : "";
    }

    /** 最小 JSON 字符串转义；复核备注是人工输入，必须转义后再落盘。 */
    private static String quote(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
