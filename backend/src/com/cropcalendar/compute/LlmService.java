package com.cropcalendar.compute;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.JsonParser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 大模型服务 —— 华为云 MaaS 盘古大模型。
 *
 * 当 crop-protocols.json 中没有某作物的规程时，调大模型动态生成。
 * 零依赖：用 JDK 自带 HttpURLConnection，解析复用 util/JsonParser。
 *
 * 失败不抛异常，返回空列表（铁律 4：compute/ 不抛异常）。
 */
public final class LlmService {

    private static final String PROMPT_TEMPLATE = """
            你是农事专家。为"%s"生成农事规程，只返回纯JSON数组，不要其他文字：
            [{"opType":"sowing","dayOffset":0,"title":"播种","desc":"描述"}]
            opType只能用sowing/irrigation/fertilize/pesticide/harvest，dayOffset相对播种日天数且单调递增，生成6-10个节点。""";

    private static final String CHART_PROMPT_TEMPLATE = """
            为"%s"生成农事图表JSON，只返回JSON对象：
            {"cropEmoji":"🧄","cropColor":"#8bc34a","growthCurve":[{"day":0,"growth":5,"phase":"播种"}],"weatherAdvice":"建议","riskForecast":"风险","cartoonTexts":{"sowing":"🌱配文","irrigation":"💧配文","fertilize":"🍚配文","pesticide":"🛡️配文","harvest":"🌾配文"}}
            生长曲线%s天10-15点(growth 0-100)，卡通配文带emoji15字内。""";

    /**
     * 调大模型为指定作物生成农事规程。
     *
     * @param cropId   作物标识
     * @param cropName 作物名称（传给大模型的提示词）
     * @return 规程节点列表，失败返回空列表
     */
    public static List<ComputeStub.RawNode> generateCropProcedure(String cropId, String cropName) {
        String url = AppConfig.llmApiUrl();
        String key = AppConfig.llmApiKey();
        if (url.isEmpty() || key.isEmpty()) {
            System.err.println("[llm] 未配置 API 端点或密钥，跳过大模型生成");
            return List.of();
        }
        // 超管在后台关掉大模型后，一律走本地规程库（省钱，也避免演示被网络拖慢）
        if (!com.cropcalendar.service.SettingsStore.llmEnabled()) {
            System.err.println("[llm] 后台已禁用大模型，跳过大模型生成");
            return List.of();
        }

        try {
            String response = callMaaS(url, key, cropName);
            if (response == null) {
                return List.of();
            }
            return parseProcedureResponse(response, cropId);
        } catch (Exception e) {
            System.err.println("[llm] 大模型生成失败：" + e.getMessage());
            return List.of();
        }
    }

    /**
     * 调大模型生成生长曲线、天气建议和风险预测。
     *
     * @param cropName  作物名称
     * @param totalDays 从播种到收获的总天数
     * @return JSON 字符串（包含 growthCurve / weatherAdvice / riskForecast），失败返回 null
     */
    public static String generateChartData(String cropName, int totalDays) {
        String url = AppConfig.llmApiUrl();
        String key = AppConfig.llmApiKey();
        if (url.isEmpty() || key.isEmpty()) return null;
        if (!com.cropcalendar.service.SettingsStore.llmEnabled()) return null;

        try {
            String prompt = String.format(CHART_PROMPT_TEMPLATE, cropName, totalDays, totalDays);
            String body = buildRequestBody(prompt);

            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setConnectTimeout(AppConfig.llmTimeoutMs());
            conn.setReadTimeout(AppConfig.llmTimeoutMs());
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                System.err.println("[llm] 图表数据生成 HTTP " + code);
                return null;
            }

            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return extractChartJson(response);
        } catch (Exception e) {
            System.err.println("[llm] 图表数据生成失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 从 MaaS 响应中提取图表 JSON 对象。
     */
    @SuppressWarnings("unchecked")
    private static String extractChartJson(String response) {
        Object parsed = JsonParser.parse(response);
        if (!(parsed instanceof Map)) return null;

        Map<String, Object> root = (Map<String, Object>) parsed;
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List)) return null;

        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) return null;

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map)) return null;

        Map<String, Object> choice = (Map<String, Object>) firstChoice;
        Object messageObj = choice.get("message");
        if (!(messageObj instanceof Map)) return null;

        Map<String, Object> message = (Map<String, Object>) messageObj;
        Object contentObj = message.get("content");
        if (!(contentObj instanceof String)) return null;

        String content = (String) contentObj;
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return null;
    }

    /**
     * POST 到华为云 MaaS chat/completions 接口。
     */
    private static String callMaaS(String url, String key, String cropName) throws IOException, InterruptedException {
        String prompt = String.format(PROMPT_TEMPLATE, cropName);
        String body = buildRequestBody(prompt);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setConnectTimeout(AppConfig.llmTimeoutMs());
        conn.setReadTimeout(AppConfig.llmTimeoutMs());
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            System.err.println("[llm] MaaS 返回 HTTP " + code);
            return null;
        }

        return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 构造 OpenAI 兼容的请求体。
     */
    private static String buildRequestBody(String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"");
        sb.append(escapeJson(AppConfig.llmModel()));
        sb.append("\",\"messages\":[{\"role\":\"user\",\"content\":\"");
        sb.append(escapeJson(prompt));
        sb.append("\"}],\"temperature\":0.7}");
        return sb.toString();
    }

    /**
     * 从 MaaS 响应中提取规程节点。
     *
     * 响应格式：{"choices":[{"message":{"content":"[{...}]"}}]}
     */
    @SuppressWarnings("unchecked")
    private static List<ComputeStub.RawNode> parseProcedureResponse(String response, String cropId) {
        Object parsed = JsonParser.parse(response);
        if (!(parsed instanceof Map)) return List.of();

        Map<String, Object> root = (Map<String, Object>) parsed;
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List)) return List.of();

        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) return List.of();

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map)) return List.of();

        Map<String, Object> choice = (Map<String, Object>) firstChoice;
        Object messageObj = choice.get("message");
        if (!(messageObj instanceof Map)) return List.of();

        Map<String, Object> message = (Map<String, Object>) messageObj;
        Object contentObj = message.get("content");
        if (!(contentObj instanceof String)) return List.of();

        String content = (String) contentObj;
        String jsonStr = extractJsonArray(content);
        if (jsonStr == null) return List.of();

        Object arrParsed = JsonParser.parse(jsonStr);
        if (!(arrParsed instanceof List)) return List.of();

        List<ComputeStub.RawNode> nodes = new ArrayList<>();
        for (Object item : (List<?>) arrParsed) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> node = (Map<String, Object>) item;

            String opType = strOf(node.get("opType"));
            String title = strOf(node.get("title"));
            int dayOffset = intOf(node.get("dayOffset"));

            if (opType == null || opType.isBlank() || dayOffset < 0) continue;
            if (!isValidOpType(opType)) continue;

            nodes.add(new ComputeStub.RawNode(cropId, opType, dayOffset, title));
        }

        nodes.sort(Comparator.comparingInt(ComputeStub.RawNode::dayOffset));
        return nodes;
    }

    /**
     * 从大模型回复中提取 JSON 数组（去掉 markdown 标记和多余文字）。
     */
    private static String extractJsonArray(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("[")) return trimmed;
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return null;
    }

    private static boolean isValidOpType(String op) {
        return op.equals("sowing") || op.equals("irrigation") || op.equals("fertilize")
                || op.equals("pesticide") || op.equals("harvest");
    }

    private static String strOf(Object o) {
        return o == null ? null : o.toString();
    }

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (NumberFormatException e) { return -1; }
        }
        return -1;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private LlmService() {}
}