package com.cropcalendar.api;

import com.cropcalendar.util.AppTime;
import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.service.crop.GddAdvisor;
import com.cropcalendar.service.crop.PlantingWindow;
import com.cropcalendar.service.crop.SowAdvisor;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * GET /api/sow-window?crop=番茄&lat=35.07&lon=116.31 —— 只看作物和地点，回答"什么时候种"。
 *
 * V5.3 起主答案是**适宜播期窗口**（PlantingWindow）：起止日期、推荐日、最晚日、预计收获，
 * 且窗口与 /api/sow-check 的校验共用同一份月份规则，不会再出现"推荐日通不过自己校验"。
 *
 * 节气与积温退居佐证：
 *   · solar —— 祖辈的经验窗口，用于解释"为什么是这个时段"；
 *   · gdd   —— 今年热量够不够，已并入推荐日的修正。
 * 积温取不到时不影响窗口给答案，只是少一层修正，gddAvailable=false 如实说明。
 */
public class SowWindowHandler implements HttpHandler {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, Json.obj("code", Json.str("40500"), "message", Json.str("只支持 GET")));
            return;
        }

        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        String crop = HttpSupport.trimToNull(q.get("crop"));
        if (crop == null) {
            HttpSupport.write(ex, 400, Json.obj(
                    "code", Json.str("40001"),
                    "message", Json.str("缺少必填参数 crop")));
            return;
        }

        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);
        if (lat < -90 || lat > 90) lat = 35.07;
        if (lon < -180 || lon > 180) lon = 116.31;

        LocalDate today = AppTime.today();
        String standard = CropRegistry.standardName(crop);
        CropProperties.Props props = CropProperties.of(standard);

        PlantingWindow.Window win = PlantingWindow.of(props, lat, lon, today);
        SowAdvisor.Suggestion solar = SowAdvisor.suggest(crop, today);
        GddAdvisor.Suggestion gdd = GddAdvisor.earliest(lat, lon, today, props);

        // 窗口拿不到（作物没属性）才退回节气/积温；三者都没有才报不可用
        if (win == null && solar == null && (gdd == null || !gdd.available())) {
            HttpSupport.write(ex, 503, Json.obj(
                    "code", Json.str("50301"),
                    "message", Json.str("播期推算暂不可用，请手动选择播种日期")));
            return;
        }

        String solarJson = solar == null ? "null" : Json.obj(
                "sowDate", Json.str(solar.sowDate()),
                "solarTerm", Json.str(solar.termName()),
                "daysFromToday", String.valueOf(solar.daysFromToday()),
                "source", Json.str(solar.source()),
                "basis", Json.str(solar.basis()));

        String gddJson = (gdd != null && gdd.available()) ? Json.obj(
                "sowDate", Json.str(gdd.sowDate()),
                "daysFromStart", String.valueOf(gdd.daysFromStart()),
                "thresholdC", Json.num(gdd.thresholdC()),
                "targetGdd", gdd.targetGdd() > 0 ? Json.num(gdd.targetGdd()) : "null",
                "maturityDate", gdd.maturityDate() == null ? "null" : Json.str(gdd.maturityDate()),
                "basis", Json.str(gdd.basis())) : "null";

        String windowJson = win == null ? "null" : Json.obj(
                "start", Json.str(win.startDate()),
                "end", Json.str(win.endDate()),
                "months", monthsArray(win.months()),
                "monthsText", Json.str(win.monthsText()),
                "action", Json.str(win.action()),
                "groupLabel", Json.str(win.groupLabel()),
                "precision", Json.str(win.precision()),
                "region", Json.str(win.region()),
                "basis", Json.str(win.basis()));

        String harvestJson = win == null ? "null" : Json.obj(
                "date", win.harvestDate() == null ? "null" : Json.str(win.harvestDate()),
                "totalDays", String.valueOf(win.totalDays()),
                "canMatureBeforeFrost", Json.bool(win.canMatureBeforeFrost()),
                "perennial", Json.bool(win.perennial()));

        // 主答案：窗口推荐日。没有窗口时才退回节气/积温，并如实标注来源
        String primaryDate = win != null ? win.recommendedDate() : (solar != null ? solar.sowDate() : gdd.sowDate());
        String primarySource = win != null ? "planting-window" : (solar != null ? solar.source() : "gdd");
        String primaryBasis = win != null ? win.basis() : (solar != null ? solar.basis() : gdd.basis());
        String latestDate = win != null ? win.latestDate() : (gdd != null && gdd.available() ? null : null);
        String latestBasis = win != null ? win.latestBasis() : null;

        String data = Json.obj(
                "crop", Json.str(crop),
                "standardName", Json.str(standard),
                // 兼容旧字段：前端历史上只认 sowDate/basis/source
                "sowDate", Json.str(primaryDate),
                "source", Json.str(primarySource),
                "basis", Json.str(primaryBasis),
                "daysFromToday", win != null ? String.valueOf(win.daysFromToday())
                        : (solar == null ? "null" : String.valueOf(solar.daysFromToday())),
                "window", windowJson,
                "harvest", harvestJson,
                "latest", latestDate == null ? "null" : Json.obj(
                        "date", Json.str(latestDate),
                        "basis", Json.str(latestBasis)),
                "solar", solarJson,
                "gdd", gddJson,
                "gddAvailable", Json.bool(gdd != null && gdd.available()),
                "note", Json.str(win != null ? win.note()
                        : (gdd != null && gdd.available() ? null : gdd == null ? null : gdd.note())));

        HttpSupport.write(ex, 200, Json.obj(
                "code", Json.str("0"),
                "message", Json.str("ok"),
                "data", data));
    }

    private static String monthsArray(List<Integer> months) {
        if (months == null || months.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < months.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(months.get(i));
        }
        return sb.append(']').toString();
    }
}
