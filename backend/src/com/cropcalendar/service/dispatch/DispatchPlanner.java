package com.cropcalendar.service.dispatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 明日农活外发的纯逻辑（15天冲刺 F01）：模板填充 + 70 字压缩 + 渠道降级。
 *
 * 不读文件、不发网络：配置由 Handler 传进来，这段能无网络回归测试
 * （见 DispatchPlannerTest）。
 *
 * 口径：
 *   · action 模板 = 明日{date}{plot}：{task}。{weatherNote}{toolNote}
 *   · caution 模板 = 明日{date}{plot}：{task}不宜，建议挪到{newDate}。{reason}
 *   · 超 maxChars 时按 dropOrder 逐项置空重填，必填的日期/任务/建议最后才压缩；
 *   · 只收"不宜"的节点时不生成 action 消息，只发 caution —— 别一边说不宜一边发普通任务；
 *   · 渠道降级：sms → serviceAccount → localMock，仅 localMock 恒定可用。
 */
public final class DispatchPlanner {

    private DispatchPlanner() {
    }

    /** 一条待发消息；不宜日任务走 caution，其余走 action。 */
    public record Message(String template, String text, String operationType, String nodeDate) {
    }

    /**
     * 按节点生成消息列表。
     *
     * @param nodes      明日节点，每个含 date/title/status/suggest/reason/newDate/plot
     * @param weatherNote 天气补充（可为 null）
     * @param toolNote    农具补充（可为 null）
     * @param maxChars    字数上限（70）
     */
    public static List<Message> build(List<Map<String, Object>> nodes, String weatherNote,
                                      String toolNote, int maxChars,
                                      String actionTemplate, String cautionTemplate) {
        List<Message> out = new ArrayList<>();
        if (nodes == null) return out;
        for (Map<String, Object> n : nodes) {
            boolean avoid = "avoid".equalsIgnoreCase(s(n.get("status")))
                    || "red".equalsIgnoreCase(s(n.get("status")));
            String name = avoid ? "caution" : "action";
            String template = avoid ? cautionTemplate : actionTemplate;
            if (template == null || template.isBlank()) {
                template = avoid
                        ? "明日{date}{plot}：{task}不宜，建议挪到{newDate}。{reason}"
                        : "明日{date}{plot}：{task}。{weatherNote}{toolNote}";
            }
            Map<String, String> vars = vars(n, weatherNote, toolNote);
            String text = fill(template, vars, maxChars);
            out.add(new Message(name, text, s(n.get("operationType")), s(n.get("date"))));
        }
        return out;
    }

    /** 只保留 caution 消息：当天只有不宜节点时不发普通任务。 */
    public static List<Message> cautionsOnly(List<Message> messages) {
        List<Message> out = new ArrayList<>();
        for (Message m : messages) {
            if ("caution".equals(m.template())) out.add(m);
        }
        return out;
    }

    /**
     * 模板填充 + 超长压缩。
     *
     * 压缩顺序由 dropOrder 控制：先丢农具/天气补充，再丢地块，最后必要时截任务。
     * 日期与"不宜/建议挪到X"是硬信息，不参与裁剪。
     */
    public static String fill(String template, Map<String, String> vars, int maxChars) {
        String result = render(template, vars);
        if (result.length() <= maxChars) return result;
        Map<String, String> v = new LinkedHashMap<>(vars);
        List<String> dropOrder = List.of("toolNote", "weatherNote", "plot", "reason", "task");
        for (String key : dropOrder) {
            if (!v.containsKey(key) || v.get(key).isEmpty()) continue;
            v.put(key, "");
            result = render(template, v);
            if (result.length() <= maxChars) return result;
        }
        // 仍超长：在 maxChars 内截断，但保住结尾句号（如果有）
        String cut = result.substring(0, Math.max(1, maxChars - 1));
        int dot = cut.lastIndexOf('。');
        if (dot > maxChars / 2) cut = cut.substring(0, dot + 1);
        else cut = cut + "。";
        return cut;
    }

    /** 渠道降级：返回本次真正使用的渠道与尝试记录。 */
    public static ChannelResult resolveChannel(List<String> priority, boolean smsReady,
                                               boolean serviceReady) {
        List<String> tried = new ArrayList<>();
        if (priority == null || priority.isEmpty()) {
            return new ChannelResult("localMock", "未配置渠道，回落本地留痕", List.of());
        }
        for (String ch : priority) {
            tried.add(ch);
            if ("sms".equals(ch) && smsReady) {
                return new ChannelResult("sms", "短信网关已发出", tried);
            }
            if ("serviceAccount".equals(ch) && serviceReady) {
                return new ChannelResult("serviceAccount", "服务号已发出", tried);
            }
            if ("localMock".equals(ch)) {
                return new ChannelResult("localMock",
                        "未配置真实通道或前序通道失败，已本地留痕（未真正发到手机）", tried);
            }
        }
        return new ChannelResult("localMock", "所有通道均不可用，已本地留痕", tried);
    }

    public record ChannelResult(String channel, String detail, List<String> tried) {
    }

    private static Map<String, String> vars(Map<String, Object> n, String weatherNote, String toolNote) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("date", s(n.get("dateDisplay")));
        if (v.get("date").isEmpty()) v.put("date", prettyDate(s(n.get("date"))));
        v.put("plot", s(n.get("plot")));
        v.put("task", s(n.get("task")));
        if (v.get("task").isEmpty()) v.put("task", s(n.get("title")));
        v.put("newDate", prettyDate(s(n.get("newDate"))));
        v.put("reason", s(n.get("reason")));
        v.put("weatherNote", weatherNote == null ? "" : weatherNote);
        v.put("toolNote", toolNote == null ? "" : toolNote);
        return v;
    }

    /** "2026-11-26" → "11月26日" */
    public static String prettyDate(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) return s(iso);
        String[] p = iso.split("-");
        return Integer.parseInt(p[1]) + "月" + Integer.parseInt(p[2]) + "日";
    }

    private static String render(String template, Map<String, String> vars) {
        String out = s(template);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        // 裁剪后留下的空位会生成"。。"或连续空格，收敛成一句干净的话
        out = out.replace("。。", "。").replace("：。", "。").replaceAll(" +", " ");
        return out.trim();
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
