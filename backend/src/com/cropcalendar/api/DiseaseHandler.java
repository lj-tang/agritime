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

        if (text == null && !hasImage) {
            HttpSupport.write(ex, 400, Json.obj(
                    "code", "40001",
                    "message", Json.str("请描述症状（如：叶片有褐色斑点）或上传照片"),
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
            Scored s = score(d, norm);
            if (s.score > 0) {
                scored.add(s);
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // 文字没匹配上、但传了图：给该作物的常见病害，明确标注置信度低
        boolean imageOnly = scored.isEmpty();
        if (imageOnly) {
            for (Map<String, Object> d : lib) {
                if (crop == null || matchesCrop(d, crop)) {
                    scored.add(new Scored(d, 0.30, 0));
                }
                if (scored.size() >= TOP_N) break;
            }
        }

        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, scored.size()); i++) {
            Scored s = scored.get(i);
            Map<String, Object> d = s.disease;
            items.add(Json.obj(
                    "id", Json.str(str(d.get("id"))),
                    "name", Json.str(str(d.get("name"))),
                    "type", Json.str(str(d.get("type"))),
                    "confidence", Json.num(s.score),
                    "pathogen", Json.str(str(d.get("pathogen"))),
                    "conditions", Json.str(str(d.get("conditions"))),
                    "matchedKeywords", String.valueOf(s.hits),
                    "symptoms", strArray(d.get("symptoms")),
                    "treatment", strArray(d.get("treatment")),
                    "medicine", strArray(d.get("medicine")),
                    "prevention", strArray(d.get("prevention"))));
        }

        String note = null;
        if (hasImage && imageOnly) {
            note = "图片已收到，但当前未接入图像识别，以下结果按作物常见病害给出，置信度仅供参考";
        } else if (hasImage) {
            note = "图片仅留档，未参与分析；结果来自文字描述匹配";
        } else if (text != null && scored.isEmpty()) {
            note = "没有匹配到已知病害，建议补充症状细节（部位、颜色、是否有霉层/虫体）";
        }

        HttpSupport.write(ex, 200, Json.obj(
                "crop", Json.str(crop),
                "query", Json.str(text),
                "imageUsed", "false",
                "note", Json.str(note),
                "total", String.valueOf(items.size()),
                "matches", Json.arr(items)));
    }

    private record Scored(Map<String, Object> disease, double score, int hits) {
    }

    /** 症状词命中度：完全包含记 1，否则按二元组覆盖率折算，避免用户措辞不同就零分 */
    private Scored score(Map<String, Object> disease, String text) {
        List<?> symptoms = disease.get("symptoms") instanceof List<?> l ? l : List.of();
        if (symptoms.isEmpty() || text.isEmpty()) {
            return new Scored(disease, 0, 0);
        }
        double sum = 0;
        int exact = 0;
        for (Object o : symptoms) {
            String kw = normalize(str(o));
            if (kw.isEmpty()) continue;
            double r = keywordScore(text, kw);
            if (r >= 1.0) exact++;
            sum += r;
        }
        double coverage = sum / symptoms.size();
        // 名称命中额外加权：用户常常直接写"晚疫病"
        String name = normalize(str(disease.get("name")));
        if (!name.isEmpty() && text.contains(name)) {
            coverage = Math.min(1.0, coverage + 0.35);
            exact++;
        }
        if (coverage < 0.12) {
            return new Scored(disease, 0, exact);
        }
        // 置信度压在 0.35~0.95，避免出现"99% 确诊"这种不负责任的数字
        double conf = 0.35 + 0.6 * Math.min(1.0, coverage);
        return new Scored(disease, Math.round(conf * 100) / 100.0, exact);
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
