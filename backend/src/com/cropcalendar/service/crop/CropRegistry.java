package com.cropcalendar.service.crop;

import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 作物名称注册表（T02）。
 *
 * 唯一职责：把用户嘴里的任意写法（标准名 / 俗名 / 拼音 / 带错别字）
 * 归一到标准显示名，并告诉调用方这个作物有没有现成的农事规程。
 *
 * 数据来源：data/crop-base/alias.json（400 条），
 * 以及 crop-protocols.json 里的 standardName（哪些作物已有规程）。
 *
 * 设计取舍：
 *   · 只有 78 种常见作物带真实俗名与拼音，其余 322 条只有标准名 ——
 *     宁可匹配不到，也不给"看起来像"的假别名。
 *   · 模糊匹配限制在编辑距离 ≤2 且长度 ≥2，避免"梨"误命中"桃"这类单字乱配。
 */
public final class CropRegistry {

    /** 一条作物记录：canonical 是 C001 这类编号，displayName 是标准中文名。 */
    public record Entry(String canonical, String displayName, List<String> aliases, List<String> pinyin) {
    }

    /** 搜索结果：matchedBy 说明命中方式，前端可直接展示"按别名命中"。 */
    public record Match(Entry entry, String matchedBy, int score) {
    }

    private static final String[] ALIAS_PATHS = {
            "../data/crop-base/alias.json",
            "data/crop-base/alias.json",
            "/agritime/data/crop-base/alias.json"
    };
    private static final String[] PROTOCOL_PATHS = {
            "crop-protocols.json",
            "../backend/crop-protocols.json",
            "/agritime/backend/crop-protocols.json"
    };

    private static volatile List<Entry> entries;
    /** displayName → 规程 cropId（dry_garlic 这类） */
    private static volatile Map<String, String> protocolByDisplay;

    private CropRegistry() {
    }

    // ---------------- 加载 ----------------

    private static List<Entry> entries() {
        List<Entry> local = entries;
        if (local != null) return local;
        synchronized (CropRegistry.class) {
            if (entries != null) return entries;
            entries = loadEntries();
            return entries;
        }
    }

    private static List<Entry> loadEntries() {
        List<Entry> list = new ArrayList<>();
        String text = readFirst(ALIAS_PATHS);
        if (text == null) {
            System.err.println("[crop-registry] 找不到 data/crop-base/alias.json，作物归一功能降级为原样透传");
            return list;
        }
        try {
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("mappings") instanceof List<?> mappings)) {
                System.err.println("[crop-registry] alias.json 结构不对（缺 mappings 数组）");
                return list;
            }
            for (Object o : mappings) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String canonical = str(m.get("canonical"));
                String display = str(m.get("displayName"));
                if (canonical == null || display == null) continue;
                list.add(new Entry(canonical, display, strList(m.get("aliases")), strList(m.get("pinyin"))));
            }
        } catch (Exception e) {
            System.err.println("[crop-registry] alias.json 解析失败：" + e.getMessage());
        }
        return list;
    }

    private static Map<String, String> protocols() {
        Map<String, String> local = protocolByDisplay;
        if (local != null) return local;
        synchronized (CropRegistry.class) {
            if (protocolByDisplay != null) return protocolByDisplay;
            Map<String, String> map = new LinkedHashMap<>();
            String text = readFirst(PROTOCOL_PATHS);
            if (text != null) {
                try {
                    Object root = JsonParser.parse(text);
                    if (root instanceof Map<?, ?> rootMap && rootMap.get("crops") instanceof List<?> crops) {
                        for (Object o : crops) {
                            if (!(o instanceof Map<?, ?> c)) continue;
                            String id = str(c.get("cropId"));
                            // standardName 是 V5 新增字段；老数据只有 name（如「山东金乡大蒜」），
                            // 用 name 兜底但去掉产地前缀，避免把"山东金乡大蒜"当成标准名。
                            String std = str(c.get("standardName"));
                            if (std == null) std = stripOrigin(str(c.get("name")));
                            if (id != null && std != null) map.put(std, id);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[crop-registry] crop-protocols.json 解析失败：" + e.getMessage());
                }
            }
            protocolByDisplay = map;
            return map;
        }
    }

    // ---------------- 对外能力 ----------------

    /** 把任意输入归一到标准显示名；匹配不到时返回清洗后的原输入（不返回 null）。 */
    public static String standardName(String input) {
        if (input == null) return null;
        String q = input.trim();
        if (q.isEmpty()) return q;
        Match m = best(q);
        return m == null ? q : m.entry().displayName();
    }

    /** 该标准名对应的规程 cropId；没有规程返回 null。 */
    public static String protocolIdOf(String displayName) {
        if (displayName == null) return null;
        return protocols().get(displayName.trim());
    }

    /** 归一 + 规程映射一步到位：优先返回规程 id，其次标准名。 */
    public static String timelineCropId(String input) {
        String std = standardName(input);
        if (std == null) return null;
        String pid = protocolIdOf(std);
        return pid != null ? pid : std;
    }

    /** 搜索联想：返回带命中方式的候选，供 /api/crops?q= 使用。 */
    public static List<Match> search(String q, int limit) {
        List<Match> out = new ArrayList<>();
        if (q == null || q.isBlank()) return out;
        String kw = q.trim();
        String lower = kw.toLowerCase(Locale.ROOT);
        List<Entry> all = entries();

        for (Entry e : all) {
            int score = scoreOf(e, kw, lower);
            if (score < 0) continue;
            out.add(new Match(e, matchedBy(e, kw, lower, score), score));
        }
        out.sort((a, b) -> {
            if (a.score() != b.score()) return Integer.compare(a.score(), b.score());
            return a.entry().displayName().compareTo(b.entry().displayName());
        });
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static Match best(String q) {
        List<Match> hits = search(q, 1);
        return hits.isEmpty() ? null : hits.get(0);
    }

    /**
     * 打分：数字越小越靠前，-1 表示不匹配。
     * 0 标准名完全相等 / 1 别名相等 / 2 拼音相等 / 3 前缀 / 4 包含 / 10+ 编辑距离
     */
    private static int scoreOf(Entry e, String kw, String lower) {
        String name = e.displayName();
        if (name.equals(kw)) return 0;
        for (String a : e.aliases()) {
            if (a.equals(kw) || a.equalsIgnoreCase(lower)) return 1;
        }
        for (String p : e.pinyin()) {
            if (p.equalsIgnoreCase(lower)) return 2;
        }
        if (name.startsWith(kw) && kw.length() >= 1) return 3;
        // 单字查询不做包含匹配，否则"梨""桃""枣"会互相带出一大片
        if (kw.length() >= 2 && name.contains(kw)) return 4;
        for (String a : e.aliases()) {
            if (kw.length() >= 2 && a.contains(kw)) return 5;
        }
        if (kw.length() >= 2) {
            int d = distance(kw, name);
            if (d <= 2) return 10 + d;
            for (String a : e.aliases()) {
                if (a.length() < 2) continue;
                int da = distance(kw, a);
                if (da <= 2) return 10 + da;
            }
        }
        return -1;
    }

    private static String matchedBy(Entry e, String kw, String lower, int score) {
        if (score == 0) return "exact";
        if (score == 1) return "alias";
        if (score == 2) return "pinyin";
        if (score <= 5) return "contains";
        return "fuzzy";
    }

    // ---------------- 工具 ----------------

    private static String stripOrigin(String name) {
        if (name == null) return null;
        // 「山东金乡大蒜」→「大蒜」：只做保守处理，去掉常见地名前缀
        String[] prefixes = {"山东金乡", "山东", "河南", "河北", "江苏", "安徽", "湖北", "湖南"};
        for (String p : prefixes) {
            if (name.startsWith(p) && name.length() > p.length()) {
                return name.substring(p.length());
            }
        }
        return name;
    }

    private static int distance(String a, String b) {
        int la = a.length(), lb = b.length();
        if (la == 0) return lb;
        if (lb == 0) return la;
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, lb + 1);
        }
        return prev[lb];
    }

    private static String readFirst(String[] candidates) {
        for (String p : candidates) {
            try {
                Path path = Path.of(p);
                if (Files.isReadable(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // 换下一个候选路径
            }
        }
        return null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                String s = str(item);
                if (s != null) out.add(s);
            }
        }
        return out;
    }
}
