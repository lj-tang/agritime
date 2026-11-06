package com.cropcalendar.service.sensor;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.AppTime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备定时拉取（V5 T28）。
 *
 * 之前的实现只有手动「立即拉取」，用户不点就永远拿不到新读数，
 * 无法兑现"按设备配置的间隔自动取数"这条验收。
 *
 * 这里起一个单线程守护调度器，每隔 {@code sensor.poll.tick.seconds} 扫一遍全量设备：
 *   1. 到期的设备调用 {@link SensorPoller#pollDevice} 拉一次（成功标在线、失败标离线）；
 *   2. 超过 N 个拉取周期仍无新数据的设备，标「离线」并保留最后在线时间。
 *
 * 拉取仍然走 SSRF 校验，阈值告警仍然写进提醒中心，与本模块手动拉取共用同一套逻辑。
 */
public final class SensorScheduler {

    private static ScheduledExecutorService EXEC;

    private SensorScheduler() {
    }

    /** 启动调度器；重复调用只启动一次。进程退出时守护线程随之结束，无需显式停止。 */
    public static synchronized void start() {
        if (EXEC != null) return;
        int tick = Math.max(10, AppConfig.sensorPollTickSeconds());
        EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensor-scheduler");
            t.setDaemon(true);
            return t;
        });
        // 首次 5 秒后跑，避免和启动预热抢资源
        EXEC.scheduleWithFixedDelay(SensorScheduler::pollDue, 5, tick, TimeUnit.SECONDS);
        System.out.println("[sensor] 定时拉取已启动：每 " + tick + " 秒扫一遍到期设备，"
                + "默认间隔 " + AppConfig.sensorDefaultIntervalMin() + " 分钟，"
                + AppConfig.sensorOfflineCycles() + " 个周期无数据判离线");
    }

    /**
     * 扫全量设备：到期拉取 + 超时判离线。异常吞掉，不让一个坏设备打断整轮。
     *
     * 每个 plot 走一次 {@link SensorStore#mutatePlot}：load 一次、批量处理、save 一次。
     * 之前对每台设备各自 load + save，同一文件反复读写 N 遍（O(n²) IO），现在收敛成 N 台设备共享一次落盘。
     */
    public static void pollDue() {
        int offlineCycles = Math.max(1, AppConfig.sensorOfflineCycles());
        LocalDateTime now = AppTime.now();
        for (String[] up : SensorStore.listPlots()) {
            String userId = up[0];
            String plotId = up[1];
            SensorStore.mutatePlot(userId, plotId,
                    state -> processPlot(userId, plotId, state, now, offlineCycles));
        }
    }

    @SuppressWarnings("unchecked")
    private static void processPlot(String userId, String plotId, Map<String, Object> state,
                                    LocalDateTime now, int offlineCycles) {
        List<Map<String, Object>> devs = (List<Map<String, Object>>) state.get("devices");
        if (devs == null) return;
        for (Map<String, Object> dev : devs) {
            try {
                double intervalMin = SensorStore.intervalMinOf(dev);

                // 离线判定：用「最近一次在线」而不是「最近一次拉取」。
                // 拉取失败会立即标离线，但设备恢复后要等下一次拉取才回在线，
                // 用 lastOnline 判断才不会把"刚失败一次"的恢复期设备误判成长期离线。
                LocalDateTime lastOnline = parse(SensorStore.str(dev.get("lastOnline")));
                if (lastOnline != null
                        && Duration.between(lastOnline, now).toMinutes() >= intervalMin * offlineCycles) {
                    dev.put("status", "offline");
                }

                LocalDateTime lastPoll = parse(SensorStore.str(dev.get("lastPollAt")));
                boolean due = lastPoll == null
                        || Duration.between(lastPoll, now).toMinutes() >= intervalMin;
                if (due) {
                    SensorPoller.pollDeviceInto(userId, plotId, state, dev);
                }
            } catch (Exception e) {
                System.err.println("[sensor] 调度单设备异常：" + e.getMessage());
            }
        }
    }

    private static LocalDateTime parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
