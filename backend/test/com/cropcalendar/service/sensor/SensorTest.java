package com.cropcalendar.service.sensor;

import com.cropcalendar.service.reminder.ReminderStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T17 传感器单测：SSRF 防护、用户隔离存储、阈值告警落到提醒档案。
 * 不访问外网 —— 只校验"该拦的拦住"，公网地址用固定 IP 字面量避免 DNS。
 */
public class SensorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        String user = "u_test_sensor_" + System.currentTimeMillis();
        String plot = "plot_test_1";

        // ---- 1. SSRF：内网与非法协议必须拦住 ----
        String[] blocked = {
                "file:///etc/passwd",
                "ftp://example.com/a",
                "gopher://127.0.0.1:6379/_INFO",
                "http://localhost:8080/status",
                "http://127.0.0.1/",
                "http://127.0.0.1:8099/api/admin",
                "http://10.1.2.3/data",
                "http://172.16.0.9/data",
                "http://192.168.1.1/data",
                "http://169.254.169.254/latest/meta-data",
                "http://100.64.0.1/data",
                "http://[::1]/data",
                "http://0.0.0.0/data",
                "http://metadata.google.internal/x",
        };
        for (String url : blocked) {
            check(!allowed(url), "应拦截：" + url);
        }

        // ---- 2. 公网地址放行（IP 字面量，不触发 DNS）----
        check(allowed("http://93.184.216.34/data.json"), "公网 http 应放行");
        check(allowed("https://93.184.216.34/data.json"), "公网 https 应放行");

        // ---- 3. 设备与读数：按用户 + 地块隔离 ----
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "1 号塘溶氧仪");
        body.put("url", "http://93.184.216.34/device.json");
        Map<String, Object> th = new LinkedHashMap<>();
        Map<String, Object> doRange = new LinkedHashMap<>();
        doRange.put("min", 4.0);
        th.put("dissolvedOxygen", doRange);
        body.put("thresholds", th);

        Map<String, Object> dev = SensorStore.upsertDevice(user, plot, body);
        check(dev != null, "设备应保存成功");
        String deviceId = SensorStore.str(dev.get("id"));
        check(deviceId != null, "设备应有 id");

        eq(1, SensorStore.devices(user, plot).size(), "设备数量");
        eq(0, SensorStore.devices(user, "plot_other").size(), "别的塘不应看到这台设备");
        eq(0, SensorStore.devices("u_other_sensor", plot).size(), "别的账号不应看到这台设备");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("dissolvedOxygen", 3.2);
        values.put("waterTemp", 24.5);
        Map<String, Object> rec = SensorStore.addReading(user, plot, deviceId, values, "auto");
        check(rec != null, "读数应保存成功");
        List<Map<String, Object>> rs = SensorStore.readings(user, plot, deviceId, 10);
        eq(1, rs.size(), "读数条数");
        check(rs.get(0).get("values") instanceof Map<?, ?> v && v.get("dissolvedOxygen") != null,
                "读数应包含溶氧");

        // ---- 4. 阈值告警写进提醒档案（type=sensor）----
        Map<String, Object> dev2 = SensorStore.findDevice(user, plot, deviceId);
        List<Map<String, Object>> alerts = SensorPoller.checkThresholds(user, plot, dev2, values);
        eq(1, alerts.size(), "溶氧 3.2 低于下限 4，应产生 1 条告警");
        eq("sensor", String.valueOf(alerts.get(0).get("type")), "告警类型");
        eq("high", String.valueOf(alerts.get(0).get("level")), "溶氧偏低应为高等级");

        String reminder = ReminderStore.read(user);
        check(reminder.contains("\"type\":\"sensor\""), "提醒档案里应有设备告警");
        check(reminder.contains("溶氧"), "告警标题应写明指标");

        // 同一天重复拉取不应刷屏（同 id 合并）
        SensorPoller.checkThresholds(user, plot, dev2, values);
        String again = ReminderStore.read(user);
        int count = countOccurrences(again, "\"type\":\"sensor\"");
        eq(1, count, "同指标同方向同一天只应有一条");

        // ---- 5. 未超阈值不告警 ----
        Map<String, Object> normal = new LinkedHashMap<>();
        normal.put("dissolvedOxygen", 6.1);
        eq(0, SensorPoller.checkThresholds(user, plot, dev2, normal).size(), "正常读数不应告警");

        // ---- 6. 删除设备 ----
        check(SensorStore.deleteDevice(user, plot, deviceId), "设备应删除成功");
        eq(0, SensorStore.devices(user, plot).size(), "删除后设备数量");

        // 收尾：清掉测试档案
        SensorStore.purge(user);
        ReminderStore.delete(user);

        System.out.println("SensorTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static boolean allowed(String url) {
        try {
            SensorPoller.assertPublicUrl(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int countOccurrences(String s, String needle) {
        int n = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + msg);
        }
    }

    private static void eq(int expected, int actual, String msg) {
        check(expected == actual, msg + "，期望 " + expected + " 实际 " + actual);
    }

    private static void eq(String expected, String actual, String msg) {
        check(expected == null ? actual == null : expected.equals(actual),
                msg + "，期望「" + expected + "」实际「" + actual + "」");
    }
}
