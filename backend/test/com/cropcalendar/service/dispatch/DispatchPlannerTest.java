package com.cropcalendar.service.dispatch;

import com.cropcalendar.service.dispatch.DispatchPlanner.ChannelResult;
import com.cropcalendar.service.dispatch.DispatchPlanner.Message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 明日农活外发回归（F01）：不依赖网络与配置文件，只测纯逻辑。
 * 覆盖：模板填充、日期格式化、70 字压缩顺序、只收不宜不发普通任务、渠道降级。
 */
public class DispatchPlannerTest {

    private static int checks = 0;

    private static final String ACTION = "明日{date}{plot}：{task}。{weatherNote}{toolNote}";
    private static final String CAUTION = "明日{date}{plot}：{task}不宜，建议挪到{newDate}。{reason}";

    public static void main(String[] args) {
        // ---- 日期展示 ----
        eq("11月26日", DispatchPlanner.prettyDate("2026-11-26"), "ISO 转中文短日期");
        eq("1月5日", DispatchPlanner.prettyDate("2027-01-05"), "跨年不带前导零");
        eq("", DispatchPlanner.prettyDate(null), "null 日期不抛异常");

        // ---- action 填充 ----
        Map<String, String> v = vars("11月26日", "东坡地", "给水稻浇水", "雨后偏湿，建议上午。", "带水管。");
        eq("明日11月26日东坡地：给水稻浇水。雨后偏湿，建议上午。带水管。",
                DispatchPlanner.fill(ACTION, v, 70), "模板完整填充");

        // ---- 超 70 字按 dropOrder 裁剪：先丢农具、再丢天气 ----
        Map<String, String> longV = vars("11月26日", "东坡地", "给水稻浇水并检查沟渠是否通畅",
                "雨后偏湿建议上午并且注意排水", "带水管和铁锹以及雨具手套");
        String compressed = DispatchPlanner.fill(ACTION, longV, 40);
        check(compressed.length() <= 40, "压缩后不超过上限（实际 " + compressed.length() + "）");
        check(compressed.contains("给水稻浇水"), "核心任务不丢");
        check(!compressed.contains("铁锹"), "先丢农具补充");
        check(compressed.endsWith("。"), "结尾保持句号");

        // ---- 只留必填，仍超长则截断 ----
        Map<String, String> tight = vars("11月26日", "", "给水稻浇水并且检查沟渠是否通畅以及田埂加固情况", "", "");
        String cut = DispatchPlanner.fill(ACTION, tight, 20);
        check(cut.length() <= 20, "极限截断不超上限");
        check(cut.contains("11月26日"), "日期保住");

        // ---- caution 填充：不宜/建议挪期/原因都在 ----
        Map<String, String> cv = vars("11月26日", "东坡地", "打药", "", "");
        cv.put("newDate", "11月28日");
        cv.put("reason", "当天有雨");
        eq("明日11月26日东坡地：打药不宜，建议挪到11月28日。当天有雨",
                DispatchPlanner.fill(CAUTION, cv, 70), "不宜模板含挪期建议");

        // ---- 只收不宜时不发普通任务 ----
        List<Map<String, Object>> nodes = List.of(
                node("green", "浇水"),
                node("red", "打药"));
        List<Message> all = DispatchPlanner.build(nodes, "", "", 70, ACTION, CAUTION);
        eq(2, all.size(), "两条节点生成两条消息");
        eq("action", all.get(0).template(), "green 走 action");
        eq("caution", all.get(1).template(), "red 走 caution");

        List<Map<String, Object>> onlyRed = List.of(node("red", "打药"));
        List<Message> redOnly = DispatchPlanner.build(onlyRed, "", "", 70, ACTION, CAUTION);
        check(redOnly.stream().noneMatch(m -> "action".equals(m.template())), "只有不宜节点时没有 action 消息");
        eq(1, DispatchPlanner.cautionsOnly(redOnly).size(), "只有不宜节点时剩下的是 caution");

        List<Map<String, Object>> mixed = List.of(node("green", "浇水"), node("red", "打药"));
        eq(1, DispatchPlanner.cautionsOnly(DispatchPlanner.build(mixed, "", "", 70, ACTION, CAUTION)).size(),
                "混合时只挑出 caution 那条");

        // ---- 渠道降级 ----
        ChannelResult sms = DispatchPlanner.resolveChannel(List.of("sms", "serviceAccount", "localMock"), true, true);
        eq("sms", sms.channel(), "短信可用优先短信");
        ChannelResult sa = DispatchPlanner.resolveChannel(List.of("sms", "serviceAccount", "localMock"), false, true);
        eq("serviceAccount", sa.channel(), "短信不可用切服务号");
        eq(2, sa.tried().size(), "记录尝试过的渠道");
        ChannelResult mock = DispatchPlanner.resolveChannel(List.of("sms", "serviceAccount", "localMock"), false, false);
        eq("localMock", mock.channel(), "都没有凭据落本地留痕");
        check(mock.detail().contains("未真正发到手机"), "明确说没真发");
        ChannelResult empty = DispatchPlanner.resolveChannel(List.of(), false, false);
        eq("localMock", empty.channel(), "空优先级也兜底");

        System.out.println("DispatchPlannerTest: " + checks + " 项通过");
    }

    private static Map<String, String> vars(String date, String plot, String task, String weather, String tool) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("plot", plot);
        m.put("task", task);
        m.put("newDate", "");
        m.put("reason", "");
        m.put("weatherNote", weather);
        m.put("toolNote", tool);
        return m;
    }

    private static Map<String, Object> node(String status, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", "2026-11-26");
        m.put("status", status);
        m.put("title", title);
        m.put("task", title);
        m.put("reason", "天气原因");
        m.put("newDate", "2026-11-28");
        return m;
    }

    private static void eq(Object expected, Object actual, String msg) {
        checks++;
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError(msg + " —— 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void check(boolean ok, String msg) {
        checks++;
        if (!ok) throw new AssertionError(msg);
    }
}
