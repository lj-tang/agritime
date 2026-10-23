package com.cropcalendar.util;

import java.util.List;

/**
 * 极简 JSON 输出工具：只做序列化，不做解析。
 *
 * 为什么手写？因为这个项目要零依赖运行 —— 不用 Maven、不下载任何 jar，
 * javac 编译完就能跑。骨架服务的响应结构是我们自己定的，字段可控，
 * 手写序列化完全够用。
 *
 * 使用约定：所有字符串值都必须先过 str() 拿到带引号的字面量，
 * 数字与布尔值直接 String.valueOf()，对象/数组用 obj() / arr() 嵌套。
 */
public final class Json {

    /** 字符串 → 带引号且转义安全的 JSON 字面量。null 输出为 null。 */
    public static String str(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    /**
     * 拼对象：obj("k1", v1, "k2", v2)。
     * 值必须已经是序列化好的片段（字符串先过 str()）。
     */
    public static String obj(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("键值必须成对出现");
        }
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) b.append(',');
            b.append(str(String.valueOf(kv[i]))).append(':').append(kv[i + 1]);
        }
        return b.append('}').toString();
    }

    /** 拼数组：元素必须已经是序列化好的片段。 */
    public static String arr(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    /** 数字：整数不显示小数点，小数保留两位。 */
    public static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 100.0) / 100.0);
    }

    public static String bool(boolean b) {
        return String.valueOf(b);
    }

    /** 原样输出（用于已经是合法 JSON 片段的场景）。 */
    public static String raw(String jsonFragment) {
        return jsonFragment == null ? "null" : jsonFragment;
    }

    private Json() {
    }
}
