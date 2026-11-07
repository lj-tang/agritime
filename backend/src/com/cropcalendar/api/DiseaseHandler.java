package com.cropcalendar.api;

import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/disease/detect —— 作物病害检测（T13）
 *
 * GET /api/disease/detect?crop=tomato&q=叶子有褐色斑点&hasImage=1
 *
 * 匹配方式：按症状关键词与用户描述做相似度打分，返回置信度最高的前三条。
 * 先说清楚边界：**没有接入图像识别**。传了图片只会被标记"已收到但未分析"，
 * 结论仍然来自文字描述。与其假装能看图，不如把这条说在接口返回里。
 */
public class DiseaseHandler implements HttpHandler {

    /** 相对于后端工作目录（systemd 的 WorkingDirectory=/agritime/backend） */
    private static final String LIB_PATH = "../data/disease/library.json";
    private static final int TOP_N = 3;

    private static volatile List<Map<String, Object>> LIBRARY;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String crop = HttpSupport.trimToNull(q.get("crop"));
        String text = HttpSupport.trimToNull(q.get("q"));
        boolean hasImage = "1".equals(q.get("hasImage")) || "true".equalsIgnoreCase(q.get("hasImage"));

        // T13：前端把图片缩到 64×64 后统计的颜色占比传上来（褐斑/黄化/白粉）。
        // 这不是训练模型识别，是颜色特征的规则匹配，返回里会写清楚方法。
        Double brown = parseRatio(q.get("brown"));
        Double yellow = parseRatio(q.get("yellow"));
        Double white = parseRatio(q.get("white"));
        String hint = dominantFeature(brown, yellow, white);

        if (text == null && !hasImage) {
            HttpSupport.write(ex, 400, Json.obj(
                    "code", "40001",
                    "message", Json.str("请描述症状（如：叶片有褐色斑点）或上传照片"),
                    "data", "null"));
            return;
        }
        if (text == null && crop == null) {
            HttpSupport.write(ex, 400, Json.obj(
                    "code", "40002",
                    "message", Json.str("只上传照片时，请先选择作物，否则无法给出候选病害"),
                    "data", "null"));
            return;
        }

        List<Map<String, Object>> lib;
        try {
            lib = library();
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj(
                    "code", "50001",
                    "message", Json.str("病害库读取失败：" + e.getMessage()),
                    "data", "null"));
            return;
        }

        String norm = text == null ? "" : normalize(text);
        List<Scored> scored = new ArrayList<>();
        for (Map<String, Object> d : lib) {
            boolean cropHit = crop == null || matchesCrop(d, crop);
            if (!cropHit) {
                continue;
            }
            Scored s = score(d, norm, hint);
            if (s.score > 0) {
                scored.add(s);
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // T16：文字问诊不再拿同作物的其他病害凑满 3 条。
        // 有命中就只给命中的；一条都没命中就直说"没匹配到"并转人工。
        // 只有"仅上传图片、没有文字"时，才按颜色特征 / 常见病害给候选，并在 note 里写明这不是匹配结果。
        // 只保留"说得出依据"的结果：要么命中至少一条典型症状，要么用户直接写了病名。
        // 光靠零散字词凑出来的低分项（覆盖率 >0 但一条症状都没对上）不给展示，
        // 否则又变成"有分数、没依据"。
        List<Scored> real = new ArrayList<>();
        for (Scored s : scored) {
            if (s.nameHit || !s.matchedSymptoms.isEmpty()) real.add(s);
        }
        boolean fallbackByImage = real.isEmpty() && hasImage;
        if (fallbackByImage) {
            java.util.Set<String> used = new java.util.HashSet<>();
            for (Map<String, Object> d : lib) {
                if (real.size() >= TOP_N) break;
                if (!used.add(str(d.get("id")))) continue;
                if (crop != null && !matchesCrop(d, crop)) continue;
                real.add(new Scored(d, 0.25, 0, List.of(), false));
            }
        }
        boolean needHuman = real.isEmpty() && !hasImage;

        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, real.size()); i++) {
            Scored s = real.get(i);
            Map<String, Object> d = s.disease;
            List<String> evidence = new ArrayList<>();
            for (String kw : s.matchedSymptoms) evidence.add(Json.str("症状：" + kw));
            if (s.nameHit) evidence.add(Json.str("病名直接命中"));
            // 匹配度是"该病害的典型症状被覆盖了多少"，与命中词条一起给出，
            // 不再只甩一个看不出由来的小数
            int matchRate = s.nameHit ? 100 : (int) Math.round(s.coverage * 100);
            items.add(Json.obj(
                    "id", Json.str(str(d.get("id"))),
                    "name", Json.str(str(d.get("name"))),
                    "type", Json.str(str(d.get("type"))),
                    "confidence", Json.num(s.score),
                    "matchRate", String.valueOf(matchRate),
                    "matchedSymptoms", strArray(s.matchedSymptoms),
                    "evidence", Json.arr(evidence),
                    "pathogen", Json.str(str(d.get("pathogen"))),
                    "conditions", Json.str(str(d.get("conditions"))),
                    "safetyInterval", d.get("safetyInterval") == null ? "null" : String.valueOf(d.get("safetyInterval")),
                    "safetyNote", Json.str(str(d.get("safetyNote"))),
                    "scope", Json.str(str(d.get("scope"))),
                    "source", Json.str(str(d.get("source"))),
                    "symptoms", strArray(d.get("symptoms")),
                    "treatment", strArray(d.get("treatment")),
                    "medicine", strArray(d.get("medicine")),
                    "prevention", strArray(d.get("prevention"))));
        }

        String note = null;
        if (hint != null) {
            note = "图片已按颜色特征参与排序（" + featureLabel(hint)
                    + " 占比最高），属规则匹配，不是训练模型识别；结论请结合文字描述与田间观察";
        } else if (fallbackByImage) {
            note = norm.isEmpty()
                    ? "没有文字描述，无法做症状匹配。以下按该作物常见病害列出候选，仅供参考，请补充症状或转人工"
                    : "文字未匹配到已知病害，以下按该作物常见病害列出候选，不代表匹配结果，请补齐症状描述或转人工";
        } else if (hasImage) {
            note = "图片未参与分析；结果来自文字描述匹配";
        } else if (needHuman) {
            note = "没有匹配到已知病害。别硬猜：补一句症状细节，或直接转人工让农技人员看";
        }

        HttpSupport.write(ex, 200, Json.obj(
                "crop", Json.str(crop),
                "query", Json.str(text),
                "imageUsed", String.valueOf(hint != null),
                "imageMethod", Json.str(hint != null ? "color-feature" : "none"),
                "note", Json.str(note),
                "needHuman", String.valueOf(needHuman),
                "humanHint", Json.str(needHuman
                        ? "点「转人工」把这段描述发给反馈，或在「反馈」里发起咨询；也可以补一句部位、颜色、有没有霉层"
                        : null),
                "total", String.valueOf(items.size()),
                "matches", Json.arr(items)));
    }

    private Double parseRatio(String raw) {
        if (raw == null) return null;
        try {
            double v = Double.parseDouble(raw.trim());
            return (v < 0 || v > 1) ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /** 占比超过 8% 才算显著特征，否则说明叶片大体健康 */
    private String dominantFeature(Double brown, Double yellow, Double white) {
        String best = null;
        double max = 0.08;
        if (brown != null && brown > max) { max = brown; best = "brown"; }
        if (yellow != null && yellow > max) { max = yellow; best = "yellow"; }
        if (white != null && white > max) { best = "white"; }
        return best;
    }

    private String featureLabel(String hint) {
        return switch (hint) {
            case "brown" -> "褐色病斑";
            case "yellow" -> "黄化";
            case "white" -> "白粉／霉层";
            default -> "颜色特征";
        };
    }

    /** 病斑颜色与病害症状词的对应关系，命中越多加分越多 */
    private double hintBonus(Map<String, Object> disease, String hint) {
        if (!(disease.get("symptoms") instanceof List<?> symptoms)) return 0;
        String[] keys = switch (hint) {
            case "brown" -> new String[]{"褐", "病斑", "斑", "锈", "枯"};
            case "yellow" -> new String[]{"黄", "褪绿", "枯黄"};
            case "white" -> new String[]{"白", "霉", "粉"};
            default -> new String[0];
        };
        int hit = 0;
        for (Object o : symptoms) {
            String s = str(o);
            if (s == null) continue;
            for (String k : keys) {
                if (s.contains(k)) { hit++; break; }
            }
        }
        if (hit == 0) return 0;
        return Math.min(0.12, hit * 0.03);
    }

    /**
     * 一条打分结果。
     *
     * @param coverage      典型症状被覆盖的比例（0~1），用来当"匹配度"
     * @param matchedSymptoms 命中的典型症状原文，直接展示给用户当依据
     * @param nameHit       用户是否直接写了病名
     */
    private record Scored(Map<String, Object> disease, double score, double coverage,
                          List<String> matchedSymptoms, boolean nameHit) {
    }

    /** 症状词命中度：完全包含记 1，否则按二元组覆盖率折算，避免用户措辞不同就零分 */
    private Scored score(Map<String, Object> disease, String text, String hint) {
        List<?> symptoms = disease.get("symptoms") instanceof List<?> l ? l : List.of();
        if (symptoms.isEmpty()) {
            return new Scored(disease, 0, 0, List.of(), false);
        }
        // 只有图片没有文字时，用颜色特征给同作物病害排序，而不是任意挑三条
        if (text.isEmpty()) {
            double b = hint == null ? 0 : hintBonus(disease, hint);
            return new Scored(disease, b > 0 ? 0.3 + b : 0, 0, List.of(), false);
        }
        double sum = 0;
        List<String> matched = new ArrayList<>();
        for (Object o : symptoms) {
            String raw = str(o);
            String kw = normalize(raw);
            if (kw.isEmpty()) continue;
            double r = keywordScore(text, kw);
            // 过半二元组命中就算"这条典型症状对上了"，作为展示依据；
            // 阈值不能取 1.0 —— 用户写"黄色条状孢子堆"、库里写"鲜黄色…"是很正常的事
            if (r >= 0.3) matched.add(raw);
            sum += r;
        }
        double coverage = sum / symptoms.size();
        // 名称命中额外加权：用户常常直接写"晚疫病"
        String name = normalize(str(disease.get("name")));
        boolean nameHit = !name.isEmpty() && text.contains(name);
        if (nameHit) {
            coverage = Math.min(1.0, coverage + 0.35);
        }
        if (coverage < 0.12) {
            return new Scored(disease, 0, coverage, matched, nameHit);
        }
        // 置信度压在 0.35~0.95，避免出现"99% 确诊"这种不负责任的数字
        double conf = 0.35 + 0.6 * Math.min(1.0, coverage);
        // 颜色特征只做小幅加分（最多 +0.12），不能盖过文字描述
        double bonus = hint == null ? 0 : hintBonus(disease, hint);
        conf = Math.min(0.95, conf + bonus);
        return new Scored(disease, Math.round(conf * 100) / 100.0,
                Math.round(coverage * 1000) / 1000.0, matched, nameHit);
    }

    private double keywordScore(String text, String kw) {
        if (kw.length() < 2) {
            return text.contains(kw) ? 1.0 : 0.0;
        }
        if (text.contains(kw)) {
            return 1.0;
        }
        int total = kw.length() - 1;
        int hit = 0;
        for (int i = 0; i < total; i++) {
            if (text.contains(kw.substring(i, i + 2))) hit++;
        }
        return 0.6 * hit / total;
    }

    private boolean matchesCrop(Map<String, Object> disease, String crop) {
        if (!(disease.get("crops") instanceof List<?> crops)) return false;
        String c = crop.trim().toLowerCase();
        for (Object o : crops) {
            String v = str(o).trim().toLowerCase();
            if (v.equals(c) || v.contains(c) || c.contains(v)) return true;
        }
        return false;
    }

    private String strArray(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) out.add(Json.str(str(o)));
        }
        return Json.arr(out);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("[\\s，。、；：,.;:!！?？\"'“”‘’()（）]", "");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> library() throws IOException {
        List<Map<String, Object>> cached = LIBRARY;
        if (cached != null) return cached;
        synchronized (DiseaseHandler.class) {
            if (LIBRARY != null) return LIBRARY;
            String text = Files.readString(Path.of(LIB_PATH), StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> m) || !(m.get("diseases") instanceof List<?> list)) {
                throw new IOException("病害库结构不对，应为 {diseases:[...]}");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> dm) {
                    out.add((Map<String, Object>) dm);
                }
            }
            LIBRARY = out;
            System.out.println("[disease] 病害库已加载：" + out.size() + " 条");
            return out;
        }
    }
}
