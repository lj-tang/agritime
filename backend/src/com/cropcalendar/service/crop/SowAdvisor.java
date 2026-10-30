package com.cropcalendar.service.crop;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.TermHint;
import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 播种起点推荐（T04 双模式播种：用户不填日期时按节气给起点）。
 *
 * 判定顺序（越靠前越贴合作物本身）：
 *   1. 节气表里登记了该作物（solar-term-crop）
 *   2. 没登记，但作物属性的适宜季节与节气所在月份对得上（solar-term-season）
 *   3. 都不匹配：取最近的节气，并如实标注这是兜底（nearest-term）
 *
 * 节气日期不是查表，而是复用 ComputeStub 的太阳黄经计算，
 * 逐年逐日都准，不受"某年公式修正值"影响。
 */
public final class SowAdvisor {

    public record Suggestion(String sowDate, String termName, int daysFromToday,
                             String source, String basis) {
    }

    private record Term(String name, LocalDate date) {
    }

    private static final String[] PATHS = {
            "../data/guidance/solar-term-farming.json",
            "data/guidance/solar-term-farming.json",
            "/agritime/data/guidance/solar-term-farming.json"
    };

    private static volatile Map<String, List<String>> termCrops;

    private SowAdvisor() {
    }

    /** 给某作物推荐最近一个适合播种的节气；算不出来返回 null。 */
    public static Suggestion suggest(String cropIdOrName, LocalDate today) {
        if (cropIdOrName == null || cropIdOrName.isBlank() || today == null) return null;
        String cropName = CropRegistry.standardName(cropIdOrName);
        CropProperties.Props props = CropProperties.of(cropName);

        List<Term> terms = upcomingTerms(today, 13);
        if (terms.isEmpty()) return null;

        Map<String, List<String>> crops = termCrops();

        // 1. 节气表里点名了这个作物
        for (Term t : terms) {
            List<String> list = crops.getOrDefault(t.name(), List.of());
            for (String c : list) {
                if (c.contains(cropName) || cropName.contains(c)) {
                    return new Suggestion(t.date().toString(), t.name(),
                            (int) (t.date().toEpochDay() - today.toEpochDay()),
                            "solar-term-crop",
                            t.name() + "是" + cropName + "的适宜播种节气");
                }
            }
        }

        // 2. 按作物的适宜季节匹配节气所在月份
        if (props != null && props.suitableSeason() != null && !props.suitableSeason().isBlank()) {
            String season = props.suitableSeason();
            for (Term t : terms) {
                int m = t.date().getMonthValue();
                boolean hit =
                        (season.contains("春") && m >= 2 && m <= 4) ||
                        (season.contains("夏") && m >= 5 && m <= 7) ||
                        (season.contains("秋") && m >= 8 && m <= 10) ||
                        (season.contains("冬") && (m == 11 || m == 12 || m == 1));
                if (hit) {
                    return new Suggestion(t.date().toString(), t.name(),
                            (int) (t.date().toEpochDay() - today.toEpochDay()),
                            "solar-term-season",
                            cropName + "适宜" + season + "，取最近的" + t.name());
                }
            }
        }

        // 3. 兜底：最近的节气，明确说明没有作物针对性
        Term first = terms.get(0);
        return new Suggestion(first.date().toString(), first.name(),
                (int) (first.date().toEpochDay() - today.toEpochDay()),
                "nearest-term",
                "未收录" + cropName + "的适宜节气，取最近的" + first.name() + "作起点，请结合当地经验调整");
    }

    /** 从 today 起往后取 n 个节气（含今天若正好是节气） */
    private static List<Term> upcomingTerms(LocalDate today, int n) {
        List<Term> out = new ArrayList<>();
        try {
            // getTermHint(d) 返回"d 所处的节气名"和"到下一个节气的天数"，
            // 且当 d 正好是节气当天时返回的就是这个节气 —— 所以跨到 next 再取一次名字即可。
            if (isTermStart(today)) {
                out.add(new Term(ComputeStub.getTermHint(today.toString()).name(), today));
            }
            LocalDate cursor = today;
            while (out.size() < n) {
                TermHint h = ComputeStub.getTermHint(cursor.toString());
                if (h == null || h.daysTo() <= 0) break;
                LocalDate next = cursor.plusDays(h.daysTo());
                TermHint at = ComputeStub.getTermHint(next.toString());
                if (at == null || at.name() == null) break;
                out.add(new Term(at.name(), next));
                cursor = next;
                if (cursor.getYear() > today.getYear() + 2) break;
            }
        } catch (Exception e) {
            System.err.println("[sow-advisor] 节气推算失败：" + e.getMessage());
        }
        return out;
    }

    private static boolean isTermStart(LocalDate d) {
        try {
            TermHint cur = ComputeStub.getTermHint(d.toString());
            TermHint prev = ComputeStub.getTermHint(d.minusDays(1).toString());
            return cur != null && prev != null && cur.name() != null
                    && !cur.name().equals(prev.name());
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, List<String>> termCrops() {
        Map<String, List<String>> local = termCrops;
        if (local != null) return local;
        synchronized (SowAdvisor.class) {
            if (termCrops != null) return termCrops;
            Map<String, List<String>> map = new LinkedHashMap<>();
            Object root = null;
            for (String p : PATHS) {
                try {
                    Path path = Path.of(p);
                    if (Files.isReadable(path)) {
                        root = JsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[sow-advisor] 读取 " + p + " 失败：" + e.getMessage());
                }
            }
            if (root instanceof List<?> arr) {
                for (Object o : arr) {
                    if (!(o instanceof Map<?, ?> t)) continue;
                    String name = str(t.get("termName"));
                    if (name == null) continue;
                    List<String> crops = new ArrayList<>();
                    if (t.get("applicableCrops") instanceof List<?> cs) {
                        for (Object c : cs) {
                            String s = str(c);
                            if (s != null) crops.add(s);
                        }
                    }
                    map.put(name, crops);
                }
            }
            termCrops = map;
            return map;
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }
}
