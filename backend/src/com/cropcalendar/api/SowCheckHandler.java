package com.cropcalendar.api;

import com.cropcalendar.service.crop.AgroZone;
import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GET /api/sow-check —— 播种前的常识拦截
 *
 * 原来的日历对输入照单全收：输入"水稻 + 12 月 + 哈尔滨"照样生成一整条时间线，
 * 用户看到的是排得整整齐齐的假计划。真实农户不会这么干，系统也不该配合。
 *
 * 这里按农事常识做三级判定：
 *   block —— 明显不成立，必须改（作物区域不符、播季不符、日期已过收获期、未来太远）
 *   warn  —— 能种但不建议（播期擦边、生育期撞霜冻、积温可能不足）
 *   ok    —— 放行
 *
 * 判定只依赖作物属性库与经纬度常年气候，不臆造"今年一定怎样"的结论。
 */
public class SowCheckHandler implements HttpHandler {

    /**
     * 播种计划的合理提前量。
     * 秋播、育苗移栽常常提前一两个月安排，所以 60 天内属于正常计划范畴；
     * 超过 60 天才算"明显不合理"，因为茬口和天气都还没定。
     */
    private static final int PLANNED_FUTURE_DAYS = 60;
    /** 超过这个天数连计划都谈不上。 */
    private static final int HARD_FUTURE_DAYS = 120;

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
        String sowRaw = HttpSupport.trimToNull(q.get("sowDate"));
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);

        if (crop == null) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("缺少 crop")));
            return;
        }
        // 先看是不是精确认识这个作物。standardName 对认不出的输入会原样返回，
        // 所以不能用它判空 —— 那样"冬小麦"会被当成已知作物放行，白白生成一份假日历。
        List<CropRegistry.Match> hits = CropRegistry.search(crop, 5);
        boolean exact = !hits.isEmpty() && hits.get(0).score() <= 2;
        CropProperties.Props props = exact ? CropProperties.of(hits.get(0).entry().displayName()) : null;
        String standard = props != null ? hits.get(0).entry().displayName() : CropRegistry.standardName(crop);

        if (props == null) {
            // 认不出就不给"能种"的结论。宁可让用户多点一下确认，也不排一份错的日历。
            List<String> cands = new ArrayList<>();
            for (CropRegistry.Match m : hits) {
                cands.add(Json.obj("name", Json.str(m.entry().displayName()), "score", Json.num(m.score())));
            }
            String detail = cands.isEmpty()
                    ? "作物库里没有「" + crop + "」，无法判断播期是否合理"
                    : "作物库里没有「" + crop + "」这个品种，但有相近的：" + joinNames(cands);
            List<String> items = new ArrayList<>();
            items.add(Json.obj(
                    "level", Json.str("block"),
                    "code", Json.str("unknown-crop"),
                    "title", Json.str("未识别到该作物"),
                    "detail", Json.str(detail),
                    "suggestion", Json.str(cands.isEmpty()
                            ? "换个标准名试试（如「水稻」「小麦」「玉米」）"
                            : "点上面的候选确认，或改用标准名")));
            HttpSupport.write(ex, 200, Json.obj(
                    "verdict", Json.str("block"),
                    "crop", Json.str(crop),
                    "items", Json.arr(items),
                    "candidates", Json.arr(cands)));
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate sow;
        try {
            sow = sowRaw == null ? null : LocalDate.parse(sowRaw);
        } catch (Exception e) {
            HttpSupport.write(ex, 400, Json.obj("error", Json.str("sowDate 格式应为 yyyy-MM-dd")));
            return;
        }

        String region = AgroZone.regionOf(lat, lon);
        List<String> items = new ArrayList<>();

        // ---- 1. 区域是否对口 ----
        //
        // 这条不能一刀切判死：水稻的主产区写的是"华南、长江流域"，但华北有小站稻、
        // 东北有盘锦稻，直接 block 会误伤真实种植。只有跨气候带（热带/南亚热带作物
        // 跑到东北、西北）才拦，其余降级为提醒，让用户自己确认品种适应性。
        if (!AgroZone.regionMatches(props.suitableRegion(), region)) {
            boolean crossZone = isCrossClimateZone(props.suitableRegion(), region);
            items.add(item(crossZone ? "block" : "warn",
                    "region-mismatch",
                    crossZone ? "该作物与本地气候带不符" : "该作物主产区不在本地",
                    standard + " 的主产区是「" + props.suitableRegion() + "」，当前地块在「" + region + "」。"
                            + (crossZone ? "跨气候带种植，积温与无霜期都难以满足。" : "本地若有引种成功的品种仍可种植。"),
                    crossZone ? "换成适合" + region + "的品种，或确认地块坐标是否正确"
                            : "建议先小面积试种，并向当地农技部门确认品种适应性"));
        }

        // ---- 2. 日期本身的合理性 ----
        if (sow != null) {
            long delta = ChronoUnit.DAYS.between(today, sow);
            if (delta > HARD_FUTURE_DAYS) {
                items.add(item("block", "too-far", "播种日期太远，排不出有意义的日历",
                        "距离今天还有 " + delta + " 天。茬口和前作都还没定，日历只能靠猜。",
                        "等进入 " + HARD_FUTURE_DAYS + " 天以内再生成；现在可以先在「我的地块」记下计划"));
            } else if (delta > PLANNED_FUTURE_DAYS) {
                items.add(item("warn", "plan-only", "属于远期计划",
                        "距离今天还有 " + delta + " 天。可以按计划播期排农事节点，"
                                + "但这段时间的天气结论只能给常年概览，不能当作预报。",
                        "日历生成后，临近播种前 10 天再刷新一次，会换成真实预报"));
            }
            int total = props.totalDays() > 0 ? props.totalDays() : 120;
            // 过去太久的播期排出来也没有农事意义，但"看历史复盘"是合理需求，
            // 所以只拦两年以上的，其余降级为提醒。
            if (delta < -730) {
                items.add(item("block", "too-old", "这个日期太久远",
                        "距今已 " + Math.abs(delta) + " 天，超出可查询范围。",
                        "改到近两年内；若只是想回顾，可用时间线上的历史查询"));
            } else if (delta < 0 && Math.abs(delta) > total) {
                items.add(item("warn", "past-maturity", "已过理论成熟期",
                        standard + " 全生育期约 " + total + " 天，从 " + sow + " 算起已经 " + Math.abs(delta)
                                + " 天，理论上早已收获。",
                        "可以生成日历用于复盘；若要安排下一季，请改用新的播期"));
            }

            // ---- 3. 播季是否对口 ----
            Set<Integer> months = AgroZone.sowMonths(props.suitableSeason());
            if (!months.isEmpty() && !months.contains(sow.getMonthValue())) {
                boolean nearEdge = months.contains(sow.getMonthValue() - 1) || months.contains(sow.getMonthValue() + 1)
                        || months.contains(sow.getMonthValue() + 11) || months.contains(sow.getMonthValue() - 11);
                if (nearEdge) {
                    items.add(item("warn", "season-edge", "播期贴近适播窗口边缘",
                            standard + " 的适播期是「" + props.suitableSeason() + "」（约 " + AgroZone.monthsText(months)
                                    + "），你选的 " + sow.getMonth() + " 月 " + sow.getDayOfMonth() + " 日在窗口外沿。",
                            "窗口外沿播种风险可控，但建议加盖地膜或改用早熟品种"));
                } else {
                    items.add(item("block", "season-mismatch", "播期不在适播季",
                            standard + " 的适播期是「" + props.suitableSeason() + "」（约 " + AgroZone.monthsText(months)
                                    + "），你选的是 " + sow.getMonthValue() + " 月。",
                            "改到适播期内，或换一个适合" + AgroZone.seasonName(sow.getMonth()) + "季播种的作物"));
                }
            }

            // ---- 4. 生育期会不会撞上霜冻 ----
            if (AgroZone.isWarmLoving(props.tempRequirement()) && !AgroZone.isColdHardy(props.tempRequirement())) {
                int ff = AgroZone.frostFreeDays(lat);
                if (AgroZone.inFrostSeason(sow, lat)) {
                    items.add(item("block", "sow-in-frost", "播种时正处常年霜期",
                            standard + " 喜温，而 " + sow + " 落在本地常年霜期内（无霜期约 " + ff + " 天），"
                                    + "播种后难以出苗，出苗也会被冻死。",
                            "等终霜过后再播，或改用温室育苗"));
                } else {
                    LocalDate frost = AgroZone.firstFrostWithin(sow, total, lat);
                    if (frost != null) {
                        items.add(item("warn", "frost-risk", "生育后期可能遭遇霜冻",
                                standard + " 喜温，" + total + " 天的生育期会在 " + frost + " 前后进入常年霜期"
                                        + "（本地无霜期约 " + ff + " 天）。",
                                "提前 7~10 天播种、选早熟品种，或准备防霜措施（覆盖、熏烟）"));
                    }
                }
            }

            // ---- 5. 积温是否够 ----
            int ff = AgroZone.frostFreeDays(lat);
            if (total > ff && !AgroZone.isColdHardy(props.tempRequirement())) {
                items.add(item("warn", "gdd-short", "生育期长于本地无霜期",
                        standard + " 需要 " + total + " 天，本地无霜期约 " + ff + " 天，积温可能不足以正常成熟。",
                        "改种早熟品种，或采用育苗移栽争取 15~20 天"));
            }
        } else {
            // 没给日期：只给建议窗口，不算通过也不算阻断
            Set<Integer> months = AgroZone.sowMonths(props.suitableSeason());
            items.add(item("ok", "suggest-window", "建议播种窗口",
                    months.isEmpty() ? "该作物未限定播季" : standard + " 适播期约 " + AgroZone.monthsText(months),
                    months.isEmpty() ? "可直接生成日历" : "点「按节气推荐」可自动填入最近的可播日期"));
        }

        String verdict = "ok";
        for (String it : items) {
            if (it.contains("\"level\":\"block\"")) { verdict = "block"; break; }
        }
        if (!"block".equals(verdict)) {
            for (String it : items) {
                if (it.contains("\"level\":\"warn\"")) { verdict = "warn"; break; }
            }
        }
        if (items.isEmpty()) {
            items.add(item("ok", "pass", "播期合理",
                    standard + " 在 " + region + " 的 " + (sow == null ? "当前" : sow.getMonthValue() + " 月")
                            + " 播种符合常识", "可以生成日历"));
        }

        // 给一个可执行的替代日期：最近一个落在适播期内的日子
        String suggested = null;
        Set<Integer> months = AgroZone.sowMonths(props.suitableSeason());
        if (sow != null && !months.isEmpty() && !months.contains(sow.getMonthValue())) {
            suggested = nearestSowDate(sow, months, today).toString();
        }

        List<String> js = new ArrayList<>(items);
        HttpSupport.write(ex, 200, Json.obj(
                "verdict", Json.str(verdict),
                "crop", Json.str(standard),
                "cropId", Json.str(props.cropId()),
                "sowDate", Json.str(sow == null ? null : sow.toString()),
                "region", Json.str(region),
                "suitableSeason", Json.str(props.suitableSeason()),
                "suitableRegion", Json.str(props.suitableRegion()),
                "totalDays", Json.num((double) (props.totalDays() > 0 ? props.totalDays() : 120)),
                "frostFreeDays", Json.num((double) AgroZone.frostFreeDays(lat)),
                "suggestedSowDate", Json.str(suggested),
                "items", Json.arr(js)));
    }

    /**
     * 是否跨气候带。热带/南亚热带作物（主产区写"华南"且不耐寒）种到东北、
     * 西北、青藏高原，属于怎么管都长不好的组合，才值得直接拦下来。
     */
    private static boolean isCrossClimateZone(String suitableRegion, String region) {
        if (suitableRegion == null || region == null) return false;
        boolean southCrop = suitableRegion.contains("华南") && !suitableRegion.contains("华北")
                && !suitableRegion.contains("东北") && !suitableRegion.contains("西北");
        boolean northPlot = "东北".equals(region) || "西北".equals(region) || "青藏高原".equals(region);
        return southCrop && northPlot;
    }

    /** 找一个离原日期最近、且落在适播期内的日期（优先往后找，符合"改期"的直觉）。 */
    private static LocalDate nearestSowDate(LocalDate from, Set<Integer> months, LocalDate today) {
        LocalDate base = from.isBefore(today.minusDays(1)) ? today : from;
        for (int i = 0; i < 400; i++) {
            LocalDate d = base.plusDays(i);
            if (months.contains(d.getMonthValue())) return d;
        }
        return base;
    }

    private static String item(String level, String code, String title, String detail, String suggestion) {
        return Json.obj(
                "level", Json.str(level),
                "code", Json.str(code),
                "title", Json.str(title),
                "detail", Json.str(detail),
                "suggestion", Json.str(suggestion));
    }

    private static String joinNames(List<String> jsonItems) {
        StringBuilder sb = new StringBuilder();
        for (String s : jsonItems) {
            int i = s.indexOf("\"name\":\"");
            if (i < 0) continue;
            int start = i + 8;
            int end = s.indexOf('"', start);
            if (end < 0) continue;
            if (sb.length() > 0) sb.append("、");
            sb.append(s, start, end);
        }
        return sb.toString();
    }
}
