package com.cropcalendar.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器：与 util/Json 的序列化互补。
 *
 * 为什么手写？项目零依赖（AGENTS.md 铁律 3），没有 Jackson/Gson 可用。
 * 只支持规程表这类受控配置所需的标准 JSON 子集：对象、数组、字符串（含转义）、
 * 数字、true/false/null。
 *
 * 返回类型约定：对象 → LinkedHashMap&lt;String,Object&gt;（保序）、数组 → List&lt;Object&gt;、
 * 字符串 → String、数字 → Double、布尔 → Boolean、null → null。
 *
 * 本类不做任何 IO、不吞错误：文本非法时抛 IllegalArgumentException，
 * 由调用方（compute 层）捕获并降级 —— 兜底责任在调用方，不在解析器。
 */
public final class JsonParser {

    /** 解析一段 JSON 文本，返回上述约定类型的对象树。 */
    public static Object parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("JSON 文本为空");
        }
        P p = new P(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (!p.eof()) {
            throw new IllegalArgumentException("JSON 末尾有多余内容，位置 " + p.pos);
        }
        return v;
    }

    private JsonParser() {
    }

    private static final class P {
        private final String s;
        private int pos = 0;

        private P(String s) {
            this.s = s;
        }

        private boolean eof() {
            return pos >= s.length();
        }

        private char peek() {
            if (eof()) throw new IllegalArgumentException("JSON 意外结束");
            return s.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void skipWs() {
            while (!eof()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void expect(char want) {
            char got = next();
            if (got != want) {
                throw new IllegalArgumentException("JSON 期望 '" + want + "' 实际 '" + got + "'，位置 " + (pos - 1));
            }
        }

        private Object value() {
            skipWs();
            return switch (peek()) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object v) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("JSON 非法字面量，位置 " + pos);
            }
            pos += word.length();
            return v;
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                m.put(key, value());
                skipWs();
                char c = next();
                if (c == '}') return m;
                if (c != ',') {
                    throw new IllegalArgumentException("JSON 期望 ',' 或 '}' 实际 '" + c + "'，位置 " + (pos - 1));
                }
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return l;
            }
            while (true) {
                l.add(value());
                skipWs();
                char c = next();
                if (c == ']') return l;
                if (c != ',') {
                    throw new IllegalArgumentException("JSON 期望 ',' 或 ']' 实际 '" + c + "'，位置 " + (pos - 1));
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"'  -> b.append('"');
                    case '\\' -> b.append('\\');
                    case '/'  -> b.append('/');
                    case 'b'  -> b.append('\b');
                    case 'f'  -> b.append('\f');
                    case 'n'  -> b.append('\n');
                    case 'r'  -> b.append('\r');
                    case 't'  -> b.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw new IllegalArgumentException("JSON unicode 转义不完整，位置 " + pos);
                        }
                        b.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("JSON 非法转义 '\\" + e + "'");
                }
            }
        }

        private Double number() {
            int start = pos;
            if (!eof() && (peek() == '-' || peek() == '+')) pos++;
            boolean digits = false;
            while (!eof()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9')) {
                    digits = true;
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    pos++;
                } else {
                    break;
                }
            }
            if (!digits) {
                throw new IllegalArgumentException("JSON 非法数字，位置 " + start);
            }
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("JSON 非法数字 '" + s.substring(start, pos) + "'");
            }
        }
    }
}