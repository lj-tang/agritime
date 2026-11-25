package com.cropcalendar.api;

import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.orchestration.TimelineOrchestrator;
import com.cropcalendar.orchestration.TimelineOrchestrator.NodeView;
import com.cropcalendar.orchestration.TimelineOrchestrator.TimelineResult;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.service.dispatch.DispatchConfig;
import com.cropcalendar.service.dispatch.DispatchPlanner;
import com.cropcalendar.service.dispatch.DispatchPlanner.ChannelResult;
import com.cropcalendar.service.dispatch.DispatchPlanner.Message;
import com.cropcalendar.service.dispatch.DispatchStore;
import com.cropcalendar.util.AppTime;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/dispatch/* —— 明日农活外发（15天冲刺 F01）。
 *
 * GET  /api/dispatch/config     读取配置（渠道、字数上限、模板）
 * POST /api/dispatch/config     覆盖配置（写到 data/dispatch/config.json，热更新）
 * POST /api/dispatch/preview    按日历生成明日消息预览，不落日志
 * POST /api/dispatch/send-now   生成并发送，落日志（渠道降级留痕）
 * GET  /api/dispatch/logs       发件日志（需登录；未登录 401）
 *
 * 明日任务来自时间线里"日期正好是明天"的节点，与前端日历同源，
 * 保证"手机收到的"和"日历上写的"是同一条。red（不宜）节点走 caution 模板，
 * 当天只有不宜任务时不发普通任务。
 *
 * 渠道只有 localMock 是仓库内可用的：sms / serviceAccount 需要真实凭据，
 * 只从环境变量 DISPATCH_SMS_URL / DISPATCH_SERVICE_URL 读，缺失时自动降级，
 * 日志里写明"未真正发到手机"——不编造"已发送成功"。
 */
public class DispatchHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if (path.endsWith("/config")) {
            if ("GET".equalsIgnoreCase(method)) {
                HttpSupport.write(ex, 200, HttpSupport.ok(rawConfig()));
            } else if ("POST".equalsIgnoreCase(method)) {
                saveConfig(ex);
            } else {
                HttpSupport.write(ex, 405, HttpSupport.error(405, "config 只支持 GET / POST"));
            }
            return;
        }
        if (path.endsWith("/preview") || path.endsWith("/send-now")) {
            boolean send = path.endsWith("/send-now");
            if (!"POST".equalsIgnoreCase(method)) {
                HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
                return;
            }
            previewOrSend(ex, send);
            return;
        }
        if (path.endsWith("/logs")) {
            User me = Auth.current(ex);
            if (me == null) {
                HttpSupport.write(ex, 401, HttpSupport.error(401, "请先登录查看发件日志"));
                return;
            }
            HttpSupport.write(ex, 200, HttpSupport.ok(DispatchStore.read(me.id())));
            return;
        }
        HttpSupport.write(ex, 404, HttpSupport.error(404, "未知的 dispatch 路径"));
    }

    // ====================================================================

    private void previewOrSend(HttpExchange ex, boolean send) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体必须是 JSON 对象"));
            return;
        }
        String crop = HttpSupport.field(body, "crop");
        String sowDate = HttpSupport.field(body, "sowDate");
        String date = HttpSupport.field(body, "date");
        if (date.isEmpty()) date = AppTime.today().plusDays(1).toString();
        double lat = HttpSupport.parseDouble(str(body.get("lat")), 35.07);
        double lon = HttpSupport.parseDouble(str(body.get("lon")), 116.31);
        String plot = HttpSupport.field(body, "plot");
        boolean explicit = body.containsKey("tasks") && body.get("tasks") instanceof List<?>;

        List<Map<String, Object>> nodes = explicit
                ? explicitNodes(body.get("tasks"), date)
                : tomorrowNodes(crop, sowDate, lat, lon, date);
        if (nodes == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400,
                    "需要 crop + sowDate（从日历取明日任务），或显式传 tasks 数组"));
            return;
        }

        String weatherNote = HttpSupport.field(body, "weatherNote");
        String toolNote = HttpSupport.field(body, "toolNote");
        List<Message> all = DispatchPlanner.build(nodes, weatherNote, toolNote, DispatchConfig.maxChars(),
                DispatchConfig.template("action"), DispatchConfig.template("caution"));
        boolean onlyCaution = nodes.stream().noneMatch(n ->
                !"red".equalsIgnoreCase(str(n.get("status")))
                        && !"avoid".equalsIgnoreCase(str(n.get("status"))));
        List<Message> messages = onlyCaution ? DispatchPlanner.cautionsOnly(all) : all;

        // 渠道降级：preview 只看凭据是否配置；send-now 真的逐个尝试，失败自动切下一路。
        // 没有真实通道（默认情况）就落到 localMock，日志里明确写"未真正发到手机"。
        List<String> payload = messages.stream().map(Message::text).toList();
        ChannelResult channel;
        List<String> tried = new ArrayList<>();
        if (!send) {
            channel = DispatchPlanner.resolveChannel(DispatchConfig.priority(),
                    hasEnv("DISPATCH_SMS_URL"), hasEnv("DISPATCH_SERVICE_URL"));
        } else {
            channel = dispatchForReal(payload, tried);
        }

        List<String> msgJson = new ArrayList<>();
        for (Message m : messages) {
            msgJson.add(Json.obj("template", Json.str(m.template()), "text", Json.str(m.text()),
                    "operationType", Json.str(m.operationType()), "nodeDate", Json.str(m.nodeDate())));
        }

        String logEntry = null;
        if (send) {
            User me = Auth.current(ex);
            String userId = me == null ? (HttpSupport.field(body, "userId").isEmpty()
                    ? "anonymous" : HttpSupport.field(body, "userId")) : me.id();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "dispatch_" + System.currentTimeMillis());
            entry.put("userId", userId);
            entry.put("date", date);
            entry.put("channel", channel.channel());
            entry.put("status", "localMock".equals(channel.channel()) ? "mocked" : "sent");
            entry.put("message", String.join(" / ", messages.stream().map(Message::text).toList()));
            entry.put("tried", channel.tried());
            entry.put("detail", channel.detail());
            entry.put("sentAt", java.time.OffsetDateTime.now(
                    java.time.ZoneId.of("Asia/Shanghai")).format(
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            logEntry = DispatchStore.append(userId, entry);
        }

        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "date", Json.str(date),
                "crop", Json.str(crop),
                "previewOnly", Json.bool(!send),
                "onlyCaution", Json.bool(onlyCaution),
                "messageCount", Json.num(messages.size()),
                "messages", Json.arr(msgJson),
                "channel", Json.str(channel.channel()),
                "deliveryDetail", Json.str(channel.detail()),
                "maxChars", Json.num(DispatchConfig.maxChars()),
                "logs", logEntry == null ? "null" : logEntry)));
    }

    /** 从时间线取日期正好是目标日的节点；失败返回 null（让调用方明确报错，不编造任务）。 */
    private static List<Map<String, Object>> tomorrowNodes(String crop, String sowDate,
                                                           double lat, double lon, String date) {
        if (crop.isBlank() || sowDate.isBlank()) return null;
        try {
            String cropId = CropRegistry.timelineCropId(crop);
            TimelineResult r = new TimelineOrchestrator().build(cropId, sowDate, lat, lon);
            List<Map<String, Object>> out = new ArrayList<>();
            for (NodeView n : r.nodes()) {
                if (!date.equals(n.date())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", n.date());
                m.put("dateDisplay", DispatchPlanner.prettyDate(n.date()));
                m.put("operationType", n.opType());
                m.put("title", n.title());
                m.put("task", n.suggest());
                m.put("status", mapStatus(n.status()));
                m.put("reason", n.reason());
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            System.err.println("[dispatch] 时间线读取失败：" + e.getMessage());
            return null;
        }
    }

    private static String mapStatus(String raw) {
        return switch (raw == null ? "" : raw) {
            case "ok" -> "green";
            case "caution" -> "yellow";
            case "degraded" -> "red";
            default -> raw;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> explicitNodes(Object tasks, String date) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<?>) tasks) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) row.put(String.valueOf(e.getKey()), e.getValue());
            row.putIfAbsent("date", date);
            row.putIfAbsent("dateDisplay", DispatchPlanner.prettyDate(date));
            out.add(row);
        }
        return out;
    }

    private static String rawConfig() {
        String text;
        try {
            text = Files.readString(Path.of("../data/dispatch/config.json"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            text = "{}";
        }
        Object root = JsonParser.parse(text);
        return Json.stringify(root);
    }

    private void saveConfig(HttpExchange ex) throws IOException {
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        if (body == null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请求体必须是 JSON 对象"));
            return;
        }
        // 只允许改这几项；渠道优先级不允许把 localMock 去掉（否则没有兜底）
        Map<String, Object> cur;
        try {
            Object root = JsonParser.parse(rawConfig());
            cur = root instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
        } catch (Exception e) {
            cur = new LinkedHashMap<>();
        }
        if (body.containsKey("defaultSendHour")) cur.put("defaultSendHour", body.get("defaultSendHour"));
        if (body.containsKey("maxMessageChars")) cur.put("maxMessageChars", body.get("maxMessageChars"));
        if (body.containsKey("templates") && body.get("templates") instanceof Map<?, ?>) {
            cur.put("templates", body.get("templates"));
        }
        List<String> priority = DispatchConfig.priority();
        if (!priority.contains("localMock")) {
            priority = new ArrayList<>(priority);
            priority.add("localMock");
        }
        cur.put("priority", priority);
        try {
            Path p = Path.of("../data/dispatch/config.json");
            Files.writeString(p, Json.stringify(cur), StandardCharsets.UTF_8);
            DispatchConfig.clear();
            HttpSupport.write(ex, 200, HttpSupport.ok(Json.stringify(cur)));
        } catch (Exception e) {
            HttpSupport.write(ex, 500, HttpSupport.error(500, "配置写入失败：" + e.getMessage()));
        }
    }

    /**
     * 逐个渠道真实发送。
     *
     * sms / serviceAccount 的网关地址只从环境变量取，POST 一段 JSON：
     * {"to":"", "messages":["明日…"]}。成功（2xx）即停；失败记下原因后切下一路。
     * localMock 永远可用，但 detail 明说"未真正发到手机"——不编造发送成功。
     */
    private static ChannelResult dispatchForReal(List<String> payload, List<String> tried) {
        for (String ch : DispatchConfig.priority()) {
            tried.add(ch);
            String envKey = "sms".equals(ch) ? "DISPATCH_SMS_URL"
                    : "serviceAccount".equals(ch) ? "DISPATCH_SERVICE_URL" : null;
            if (envKey == null || !hasEnv(envKey)) continue;
            String url = System.getenv(envKey);
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofMillis(
                                com.cropcalendar.config.AppConfig.httpConnectTimeoutMs())).build();
                List<String> msgJson = payload.stream().map(t -> Json.str(t)).toList();
                String reqBody = Json.obj("to", Json.str(System.getenv("DISPATCH_SMS_TO")),
                        "messages", Json.arr(msgJson));
                java.net.http.HttpResponse<String> resp = client.send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                                .timeout(java.time.Duration.ofMillis(
                                        com.cropcalendar.config.AppConfig.httpReadTimeoutMs()))
                                .header("Content-Type", "application/json; charset=UTF-8")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(reqBody,
                                        StandardCharsets.UTF_8))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return new ChannelResult(ch, ch + " 已发出（HTTP " + resp.statusCode() + "）", tried);
                }
                System.err.println("[dispatch] " + ch + " 发送失败 HTTP " + resp.statusCode()
                        + "，尝试下一路");
            } catch (Exception e) {
                System.err.println("[dispatch] " + ch + " 发送异常：" + e.getMessage()
                        + "，尝试下一路");
            }
        }
        return new ChannelResult("localMock",
                "真实通道未配置或全部失败，已本地留痕（未真正发到手机）", tried);
    }

    private static boolean hasEnv(String key) {
        String v = System.getenv(key);
        return v != null && !v.isBlank();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
