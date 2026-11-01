package com.cropcalendar.service.reminder;

import com.cropcalendar.util.JsonParser;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提醒档案回归（V5 T18）：不依赖网络、不需要登录，直接测存储层。
 * 覆盖验收条目：记录持久、未读数、高风险置顶、开关可恢复、已读不回退、过期清理。
 */
public class ReminderStoreTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static int checks = 0;

    public static void main(String[] args) {
        String uid = "reminder_test_" + System.currentTimeMillis();
        ReminderStore.delete(uid);
        try {
            String today = LocalDate.now(ZONE).toString();
            String future = LocalDate.now(ZONE).plusDays(2).toString();
            String expired = LocalDate.now(ZONE).minusDays(20).toString();

            // 1. 空档案
            Map<String, Object> v = view(ReminderStore.read(uid));
            eq(0, size(v.get("items")), "新档案应为空");
            eq(0, intVal(v.get("unreadCount")), "新档案未读数为 0");

            // 2. 推入三条（高/中/低）+ 一条过期
            List<Object> items = new ArrayList<>();
            items.add(item("w1", "weather", "high", "暴雨预警", future));
            items.add(item("d1", "disease", "medium", "病害风险", future));
            items.add(item("n1", "window", "low", "适宜窗口", today));
            items.add(item("old", "window", "low", "过期提醒", expired));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("items", items);
            v = view(ReminderStore.sync(uid, body));
            List<Object> stored = list(v.get("items"));
            eq(3, stored.size(), "过期项应被清理");
            eq("w1", text(map(stored.get(0)).get("id")), "高风险应置顶");
            eq(3, intVal(v.get("unreadCount")), "三条未读");

            // 3. 重复 upsert 同 id 不能把已读顶回未读
            Map<String, Object> actions = new LinkedHashMap<>();
            actions.put("readIds", List.of("w1"));
            Map<String, Object> readBody = new LinkedHashMap<>();
            readBody.put("actions", actions);
            v = view(ReminderStore.sync(uid, readBody));
            eq(2, intVal(v.get("unreadCount")), "标为已读后未读数减 1");

            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("items", List.of(item("w1", "weather", "high", "暴雨预警", future)));
            v = view(ReminderStore.sync(uid, replay));
            eq(2, intVal(v.get("unreadCount")), "重复推送不能把已读顶回未读");
            eq(Boolean.TRUE, map(find(v, "w1")).get("read"), "已读状态应保留");

            // 4. 关掉病害类：该类不再计入未读，但记录仍在档案里，重开可恢复
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("disease", Boolean.FALSE);
            Map<String, Object> offBody = new LinkedHashMap<>();
            offBody.put("settings", settings);
            v = view(ReminderStore.sync(uid, offBody));
            eq(1, intVal(v.get("unreadCount")), "关闭病害类后未读数只剩适宜窗口");
            eq(3, size(v.get("items")), "被关闭类别的记录仍保留在档案里");

            Map<String, Object> onBody = new LinkedHashMap<>();
            Map<String, Object> onSettings = new LinkedHashMap<>();
            onSettings.put("disease", Boolean.TRUE);
            onBody.put("settings", onSettings);
            v = view(ReminderStore.sync(uid, onBody));
            eq(2, intVal(v.get("unreadCount")), "重新打开即恢复");

            // 5. 删除与全部已读
            Map<String, Object> finalBody = new LinkedHashMap<>();
            Map<String, Object> finalActions = new LinkedHashMap<>();
            finalActions.put("removeIds", List.of("n1"));
            finalActions.put("readAll", Boolean.TRUE);
            finalBody.put("actions", finalActions);
            v = view(ReminderStore.sync(uid, finalBody));
            eq(0, intVal(v.get("unreadCount")), "全部已读后未读为 0");
            eq(2, size(v.get("items")), "删除指定项后剩两条");

            // 6. 重新读取仍然在（跨请求持久化）
            v = view(ReminderStore.read(uid));
            eq(2, size(v.get("items")), "重新读取档案仍在，刷新页面不丢");

            System.out.println("[ReminderStoreTest] 通过 " + checks + " 项断言");
        } finally {
            ReminderStore.delete(uid);
        }
    }

    private static Map<String, Object> item(String id, String type, String level, String title, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("level", level);
        m.put("title", title);
        m.put("detail", "测试提醒");
        m.put("advice", "按建议执行");
        m.put("date", date);
        m.put("createdAt", "2026-11-01T00:00:00");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> view(String json) {
        return (Map<String, Object>) JsonParser.parse(json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static List<Object> list(Object o) {
        return o instanceof List<?> l ? new ArrayList<Object>(l) : new ArrayList<>();
    }

    private static Object find(Map<String, Object> v, String id) {
        for (Object o : list(v.get("items"))) {
            if (id.equals(text(map(o).get("id")))) return o;
        }
        return null;
    }

    private static int size(Object o) {
        return o instanceof List<?> l ? l.size() : 0;
    }

    private static int intVal(Object o) {
        return o instanceof Number n ? n.intValue() : -1;
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
