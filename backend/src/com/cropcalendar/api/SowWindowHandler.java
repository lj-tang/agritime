package com.cropcalendar.api;

import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.service.crop.GddAdvisor;
import com.cropcalendar.service.crop.SowAdvisor;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * GET /api/sow-window?crop=番茄&lat=35.07&lon=116.31 —— 播种起点推荐（T04 / V5 T20）。
 *
 * 以前只给一个"按节气"的日期：节气是祖辈的经验窗口，但回答不了"今年热量够不够"。
 * T20 改成双依据，两个日期各带理由，由用户选：
 *   · 按节气：取最近一个适宜播种的节气（来源见 source）；
 *   · 按积温：从今天起累计 ≥10℃ 活动积温，达到品种所需积温的最早日期；
 *   · 最晚播期：常年初霜日倒推全生育期，避免收获前撞霜。
 *
 * 积温所需的逐日气温来自近三年同日聚合（ClimateNormals），取不到时
 * gddAvailable=false 并说明"只给节气建议"，不编一个日期。
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

        LocalDate today = LocalDate.now(SHANGHAI);
        String standard = CropRegistry.standardName(crop);
        CropProperties.Props props = CropProperties.of(standard);

        SowAdvisor.Suggestion solar = SowAdvisor.suggest(crop, today);
        GddAdvisor.Suggestion gdd = GddAdvisor.earliest(lat, lon, today, props);
        GddAdvisor.Latest latest = GddAdvisor.latestSowDate(lat, props, today);

        if (solar == null && !gdd.available()) {
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

        String gddJson = gdd.available() ? Json.obj(
                "sowDate", Json.str(gdd.sowDate()),
                "daysFromStart", String.valueOf(gdd.daysFromStart()),
                "thresholdC", Json.num(gdd.thresholdC()),
                "targetGdd", gdd.targetGdd() > 0 ? Json.num(gdd.targetGdd()) : "null",
                "maturityDate", gdd.maturityDate() == null ? "null" : Json.str(gdd.maturityDate()),
                "basis", Json.str(gdd.basis())) : "null";

        String latestJson = latest == null ? "null" : Json.obj(
                "date", Json.str(latest.date()),
                "basis", Json.str(latest.basis()));

        // 兼容旧字段：sowDate 优先节气，没有节气就用积温，旧前端仍能直接填
        String primaryDate = solar != null ? solar.sowDate() : gdd.sowDate();
        String primaryBasis = solar != null ? solar.basis() : gdd.basis();
        String primarySource = solar != null ? solar.source() : "gdd";

        String data = Json.obj(
                "crop", Json.str(crop),
                "standardName", Json.str(standard),
                "sowDate", Json.str(primaryDate),
                "solarTerm", solar == null ? "null" : Json.str(solar.termName()),
                "daysFromToday", solar == null ? "null" : String.valueOf(solar.daysFromToday()),
                "source", Json.str(primarySource),
                "basis", Json.str(primaryBasis),
                "solar", solarJson,
                "gdd", gddJson,
                "latest", latestJson,
                "gddAvailable", Json.bool(gdd.available()),
                "note", Json.str(gdd.available() ? null : gdd.note()));

        HttpSupport.write(ex, 200, Json.obj(
                "code", Json.str("0"),
                "message", Json.str("ok"),
                "data", data));
    }
}
