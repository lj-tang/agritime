package com.cropcalendar.api;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.Candidate;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * POST /api/recognition —— 拍照识别作物。
 *
 * 识别只出候选，最后一步永远由人确认（needConfirm 恒为 true）。
 * A6: 支持 JSON body 含 name 字段时走文本匹配（别名+编辑距离），西红柿→番茄候选。
 */
public class RecognitionHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, Json.obj(
                    "error", Json.str("INVALID_INPUT"),
                    "message", Json.str("只支持 POST")));
            return;
        }

        byte[] bodyBytes = ex.getRequestBody().readAllBytes();

        try {
            List<Candidate> candidates;

            String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
            Object parsed = JsonParser.parse(bodyStr);
            if (parsed instanceof Map<?, ?> m && m.get("name") != null) {
                String name = m.get("name").toString();
                candidates = ComputeStub.matchCropByName(name);
            } else {
                candidates = ComputeStub.identifyCrop(bodyBytes.length > 0 ? bodyBytes : new byte[0]);
            }

            if (candidates.isEmpty()) {
                HttpSupport.write(ex, 200, Json.obj(
                        "needConfirm", "true",
                        "candidates", "[]",
                        "fallback", Json.str("search")));
                return;
            }

            List<String> cJson = candidates.stream().map(c -> Json.obj(
                    "crop", Json.str(c.name()),
                    "confidence", String.valueOf(c.confidence())
            )).toList();

            HttpSupport.write(ex, 200, Json.obj(
                    "needConfirm", "true",
                    "candidates", Json.arr(cJson),
                    "fallback", Json.str("none")));

        } catch (Exception e) {
            e.printStackTrace();
            HttpSupport.write(ex, 200, Json.obj(
                    "needConfirm", "true",
                    "candidates", "[]",
                    "fallback", Json.str("search")));
        }
    }
}