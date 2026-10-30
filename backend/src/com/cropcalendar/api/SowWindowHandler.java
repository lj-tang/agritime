package com.cropcalendar.api;

import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.service.crop.SowAdvisor;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * GET /api/sow-window?crop=番茄 —— 播种起点推荐（T04 双模式播种的"方式A"）。
 *
 * 用户不填日期时，按「地点 + 作物」取最近一个适宜播种的节气作为起点。
 * 返回里带 source 与 basis，说明这个日期是怎么来的：
 *   solar-term-crop   节气表里点名了该作物
 *   solar-term-season 按作物适宜季节匹配到的节气
 *   nearest-term      没匹配上，取最近节气兜底（会明确提示需结合当地经验）
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

        SowAdvisor.Suggestion s = SowAdvisor.suggest(crop, LocalDate.now(SHANGHAI));
        if (s == null) {
            HttpSupport.write(ex, 503, Json.obj(
                    "code", Json.str("50301"),
                    "message", Json.str("节气推算不可用，请手动选择播种日期")));
            return;
        }

        String data = Json.obj(
                "crop", Json.str(crop),
                "standardName", Json.str(CropRegistry.standardName(crop)),
                "sowDate", Json.str(s.sowDate()),
                "solarTerm", Json.str(s.termName()),
                "daysFromToday", String.valueOf(s.daysFromToday()),
                "source", Json.str(s.source()),
                "basis", Json.str(s.basis()));

        HttpSupport.write(ex, 200, Json.obj(
                "code", Json.str("0"),
                "message", Json.str("ok"),
                "data", data));
    }
}
