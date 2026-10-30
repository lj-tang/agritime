package com.cropcalendar.orchestration;

import com.cropcalendar.compute.ComputeStub;
import com.cropcalendar.compute.ComputeStub.MarketHint;
import com.cropcalendar.compute.ComputeStub.RawNode;
import com.cropcalendar.service.crop.CropRegistry;
import com.cropcalendar.service.guidance.GuidanceService;
import com.cropcalendar.compute.ComputeStub.ScoreResult;
import com.cropcalendar.compute.ComputeStub.TermHint;
import com.cropcalendar.compute.ComputeStub.WeatherWindow;
import com.cropcalendar.config.AppConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ★★★ 这是「甲」的地盘 —— 编排与交付层 ★★★
 *
 * 六步编排：收集 → 匹配 → 生成 → 取天气 → 打分 → 叠加提醒
 *
 * 铁律：这个类不引入任何天气或识别 SDK，只调用 ComputeStub（乙）导出的纯函数。
 * 一旦这里出现 Open-Meteo 地址或识别客户端，分层就白拆了。
 *
 * 降级策略（三档，一档比一档保守）：
 *   ① 上游 800ms 内返回 → 直接用，并写入缓存
 *   ② 超时/异常但有缓存 → 返回缓存，标注数据时间与降级原因
 *   ③ 超时/异常且无缓存 → 明说「取不到」，绝不编造数字
 */
public class TimelineOrchestrator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 共享线程池：只用来给上游调用做超时控制，不每次请求新建 */
    private static final ExecutorService UPSTREAM = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "upstream-call");
        t.setDaemon(true);
        return t;
    });

    private final CacheStore cache = new CacheStore();

    // ============================================================
    // 对外返回的形状（契约②，D5 冻结）
    // ============================================================

    public record NodeView(int dayOffset, String date, String opType, String title,
                           String status, String suggest, String reason, String guidance,
                           boolean climateNormal) {
    }

    public record TimelineResult(String cropId, String sowDate, String generatedAt,
                                 String dataTime, String weatherStatus, String weatherNote,
                                 String weatherSummary,
                                 TermHint termHint, List<NodeView> nodes,
                                 List<MarketHint> marketHints) {
    }

    /** 内部用：天气 + 这份数据是什么时候的 */
    private record WeatherFetch(WeatherWindow window, String dataTime, String source) {
    }

    // ============================================================

    public TimelineResult build(String cropId, String sowDate, double lat, double lon) {
        LocalDate sow;
        try {
            sow = LocalDate.parse(sowDate, DATE);
        } catch (Exception e) {
            throw new IllegalArgumentException("sowDate 格式应为 yyyy-MM-dd，收到：" + sowDate);
        }

        // ---- 步骤 1 收集 / 步骤 2 匹配规程 / 步骤 3 生成节点 ----
        String cropNameForGuidance = CropRegistry.standardName(cropId);
        List<RawNode> raw = ComputeStub.buildNodes(cropId, sowDate, lat);
        if (raw.isEmpty()) {
            return new TimelineResult(cropId, sowDate, LocalDateTime.now().format(TS),
                    null, "ok", null, null, null, List.of(), List.of());
        }

        // 覆盖整条时间线的日期区间（两端各多留几天给节点窗口用）
        int span = raw.stream().mapToInt(RawNode::dayOffset).max().orElse(0);
        List<String> allDates = new ArrayList<>(span + 11);
        for (int i = -3; i <= span + 7; i++) {
            allDates.add(sow.plusDays(i).format(DATE));
        }

        // ---- 步骤 4 取天气：唯一有 IO 的一步，带三档降级 ----
        WeatherFetch fetched = fetchWeatherWithFallback(lat, lon, allDates);

        // 超出预报范围的日期（节气推荐的起点可能在 3 个月后）用去年同日补位，
        // 否则整条远期时间线全是"请人工判断"，等于白生成。
        WeatherWindow normals = climateNormals(lat, lon, allDates, fetched.window());

        // ---- 步骤 5 逐节点打分 ----
        int w = AppConfig.weatherWindowDays();
        List<NodeView> nodes = new ArrayList<>(raw.size());
        for (RawNode n : raw) {
            String date = sow.plusDays(n.dayOffset()).format(DATE);
            WeatherWindow slice = fetched.window().around(date, w, w);
            boolean climateNormal = false;
            if (slice.byDate().isEmpty()) {
                WeatherWindow ns = normals.around(date, w, w);
                if (!ns.byDate().isEmpty()) {
                    slice = ns;
                    climateNormal = true;
                }
            }
            ScoreResult score = ComputeStub.scoreNode(n.opType(), slice);
            // T03：四类指导随节点下发，天气窗口用同一份 slice，避免两处天气不一致
            String guidance = GuidanceService.build(n.opType(), slice, cropNameForGuidance);
            nodes.add(new NodeView(n.dayOffset(), date, n.opType(), n.title(),
                    score.status(), score.suggest(), score.reason(), guidance, climateNormal));
        }

        // ---- 步骤 6 叠加节气与行情提醒 ----
        TermHint term = ComputeStub.getTermHint(sowDate);
        List<MarketHint> market = ComputeStub.getMarketHint(sowDate, cropId);

        return new TimelineResult(
                cropId, sowDate,
                LocalDateTime.now().format(TS),
                fetched.dataTime(),
                fetched.window().degraded() ? "degraded" : "ok",
                fetched.window().degraded() ? fetched.window().degradedReason() : fetched.source(),
                summarizeWeather(fetched.window()),
                term, nodes, market);
    }

    /** A9: 生成天气概况，如"120天数据，15天有雨，最高35℃最低-2℃" */
    private static String summarizeWeather(WeatherWindow w) {
        if (w.degraded() || w.byDate().isEmpty()) return null;
        int rainyDays = 0;
        double maxTemp = -999, minTemp = 999;
        for (WeatherWindow.Daily d : w.byDate().values()) {
            if (d.rainMm() > 0.1) rainyDays++;
            if (d.maxTempC() > maxTemp) maxTemp = d.maxTempC();
            if (d.minTempC() < minTemp) minTemp = d.minTempC();
        }
        return w.byDate().size() + "天数据，" + rainyDays + "天有雨，最高" + (int) maxTemp + "℃最低" + (int) minTemp + "℃";
    }

    // ============================================================
    // 降级逻辑 —— 这一段是甲的护城河，也是答辩要讲的 40 秒
    // ============================================================

    private WeatherFetch fetchWeatherWithFallback(double lat, double lon, List<String> dates) {
        // A3: 缓存 key 只用地点（0.01度精度），同地点不同日期命中缓存 < 0.5s
        String key = "weather:" + String.format("%.2f", lat) + "," + String.format("%.2f", lon);
        int timeout = AppConfig.upstreamTimeoutMs();

        // 先查缓存：如果缓存数据覆盖了请求的所有日期，直接返回
        WeatherFetch cached = cache.get(key, WeatherFetch.class);
        if (cached != null && !cached.window().degraded() && coversDates(cached.window(), dates)) {
            return new WeatherFetch(cached.window(), cached.dataTime(), "缓存命中（同地点）");
        }

        // 注意：缓存在后台任务里写入，而不是在 get() 成功之后写。
        // 这样即使本次请求已经超时放弃等待，数据随后到了照样会进缓存 ——
        // 下一次请求就能用上，缓存会自己恢复，不需要人工干预。
        Future<WeatherFetch> f = UPSTREAM.submit(() -> {
            WeatherWindow w = ComputeStub.fetchWindowWeather(lat, lon, dates);
            if (w.degraded()) {
                // 失败结果不进缓存 —— 缓存只存真实拿到过的数据。
                // 否则一次断网会把空数据缓存 900 秒，缓存自愈的性质就被破坏了。
                return new WeatherFetch(w, null, "上游取数失败");
            }
            WeatherFetch fresh = new WeatherFetch(w, LocalDateTime.now().format(TS), "上游实时数据");
            cache.put(key, fresh, AppConfig.cacheTtlSeconds());
            return fresh;
        });
        try {
            return f.get(timeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            f.cancel(true);
            return degradeToCache(key, "上游超时 " + timeout + "ms");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return degradeToCache(key, "上游调用被中断");

        } catch (Exception e) {
            return degradeToCache(key, "上游异常 " + e.getClass().getSimpleName());
        }
    }

    /**
     * 常年同期：把缺失日期映射到去年同日，一次请求取回后按原日期重建窗口。
     * 用途是让远期节点有"平均气候"可比，而不是一律返回"取不到天气"。
     * 返回的 window 里 key 是**原始日期**，调用方无感知。
     */
    private WeatherWindow climateNormals(double lat, double lon, List<String> allDates, WeatherWindow have) {
        List<String> lastYear = new ArrayList<>();
        Map<String, String> toOriginal = new LinkedHashMap<>();
        for (String d : allDates) {
            if (have.byDate().containsKey(d)) continue;
            try {
                String ly = LocalDate.parse(d).minusYears(1).toString();
                lastYear.add(ly);
                toOriginal.put(ly, d);
            } catch (Exception ignored) {
                // 日期非法就跳过
            }
        }
        if (lastYear.isEmpty()) return WeatherWindow.unavailable("无缺失日期");
        WeatherWindow ly = ComputeStub.fetchWindowWeather(lat, lon, lastYear);
        if (ly.degraded()) return ly;
        Map<String, WeatherWindow.Daily> mapped = new LinkedHashMap<>();
        ly.byDate().forEach((k, v) -> {
            String orig = toOriginal.get(k);
            if (orig != null) mapped.put(orig, v);
        });
        return new WeatherWindow(mapped, false, null);
    }

    private WeatherFetch degradeToCache(String key, String why) {
        WeatherFetch cached = cache.get(key, WeatherFetch.class);
        if (cached != null) {
            // 有缓存：把上一份数据还给前端，但如实标注它的时间
            return new WeatherFetch(cached.window(), cached.dataTime(), why + "，已使用缓存数据");
        }
        // 没缓存：明说取不到，绝不编造数字
        return new WeatherFetch(
                WeatherWindow.unavailable(why + "，且无缓存，本次不提供天气判断"),
                null,
                why + "，且无缓存");
    }

    /** A3: 检查缓存数据是否覆盖请求的所有日期 */
    private static boolean coversDates(WeatherWindow w, List<String> dates) {
        for (String d : dates) {
            if (!w.byDate().containsKey(d)) return false;
        }
        return true;
    }
}
