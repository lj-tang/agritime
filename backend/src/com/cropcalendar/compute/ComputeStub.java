package com.cropcalendar.compute;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.service.crop.CropProperties;
import com.cropcalendar.util.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ★★★ 这是「乙」的地盘 —— 计算与规则层 ★★★
 *
 * 甲只负责调用这里的六个静态函数，永远不关心内部怎么实现。
 * 甲不引入任何天气或识别 SDK，IO 全部关在这个文件里。
 *
 * 交付铁律（契约④，D3 冻结）：
 *   1. 纯函数，无 IO 副作用 —— 仅 fetchWindowWeather 与 identifyCrop 例外
 *   2. 不抛异常 —— 失败返回 degraded 标记与原因，交给甲兜底
 *   3. 每个模块附带 fixture：输入 → 期望输出，可独立验证
 *   4. 任何模块都可以被 mock 顶替，因此不占关键路径
 *
 * 当前状态：buildNodes 已改为读取规程表（数据驱动，见 crop-protocols.json），
 * 其余五个仍是 stub，返回写死的假数据。
 * 这套 stub 就是「甲的开发不依赖乙的进度」的实现方式 ——
 * 乙交付后把这里的实现换掉即可，甲的编排代码一行都不用改。
 */
public final class ComputeStub {

    // ============================================================
    // 返回类型（契约④的形状，D3 冻结后不许改字段名）
    // ============================================================

    /** 一个原始农事节点：只有相对播种日的天数，还没有绝对日期 */
    public record RawNode(String cropId, String opType, int dayOffset, String title) {
    }

    /**
     * 天气窗口。
     *
     * 同一个类型既表示「整段序列」也表示「某个节点前后 N 天的窗口」，
     * 靠 around() 收窄 —— 这样 fetchWindowWeather 一次网络请求就能
     * 覆盖整条时间线，而 scoreNode 拿到的始终是一个几天的小窗口。
     */
    public record WeatherWindow(Map<String, Daily> byDate, boolean degraded, String degradedReason) {

        /** 某日天气 */
        public record Daily(double rainMm, double windMs, double minTempC, double maxTempC) {
        }

        /** 收窄成以 date 为中心、前后各 before/after 天的窗口 */
        public WeatherWindow around(String date, int before, int after) {
            Map<String, Daily> sub = new LinkedHashMap<>();
            LocalDate center;
            try {
                center = LocalDate.parse(date);
            } catch (Exception e) {
                return new WeatherWindow(sub, degraded, degradedReason);
            }
            for (int i = -before; i <= after; i++) {
                String k = center.plusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                Daily v = byDate.get(k);
                if (v != null) sub.put(k, v);
            }
            return new WeatherWindow(sub, degraded, degradedReason);
        }

        public boolean isEmpty() {
            return byDate.isEmpty();
        }

        // ---- 以下四个取值都遵循同一条原则：取窗口内最差的那一天，而不是取平均。
        //      这是农业场景的常识 —— 只要有一天不适合，这个窗口就不该被推荐。 ----

        public double maxRainMm() {
            return byDate.values().stream().mapToDouble(Daily::rainMm).max().orElse(0);
        }

        public double maxWindMs() {
            return byDate.values().stream().mapToDouble(Daily::windMs).max().orElse(0);
        }

        public double minTempC() {
            return byDate.values().stream().mapToDouble(Daily::minTempC).min().orElse(0);
        }

        public double maxTempC() {
            return byDate.values().stream().mapToDouble(Daily::maxTempC).max().orElse(0);
        }

        public static WeatherWindow unavailable(String reason) {
            return new WeatherWindow(Map.of(), true, reason);
        }
    }

    /**
     * 单节点打分结果。
     * status 只有三个取值，前端怎么展示（颜色/图标/文案）由契约③决定，
     * 不在这个函数里管 —— 那是成员1 的地盘。
     */
    public record ScoreResult(String status, String suggest, String reason) {
        public static final String OK = "ok";
        public static final String CAUTION = "caution";
        public static final String POSTPONE = "postpone";

        public static ScoreResult of(String status, String suggest, String reason) {
            return new ScoreResult(status, suggest, reason);
        }
    }

    /** 节气提醒 */
    public record TermHint(String name, int daysTo, String tip) {
    }

    /** 销售节点提醒 */
    public record MarketHint(String festival, int daysTo, String tip) {
    }

    /** 拍照识别的候选结果 */
    public record Candidate(String cropId, String name, double confidence) {
    }

    // ============================================================
    // 六个导出函数 —— 甲的调度器只认这些签名
    // ============================================================

    /**
     * ① 节点生成器：作物规程 × 播种日 → 节点列表
     * 验收：5 种作物各 ≥ 8 个节点
     */
    public static List<RawNode> buildNodes(String cropId, String sowDate) {
        return buildNodes(cropId, sowDate, Double.NaN);
    }

    /**
     * 带纬度的版本：T07 的作物属性会据此决定要不要生成第二季
     * （双季稻只在华南成立，北方传 35.07 就不会多出一轮）。
     */
    public static List<RawNode> buildNodes(String cropId, String sowDate, double lat) {
        List<RawNode> base = buildBaseNodes(cropId);
        return CropProperties.expand(base, CropProperties.of(cropId), lat);
    }

    private static List<RawNode> buildBaseNodes(String cropId) {
        // 数据驱动：从项目根目录 crop-protocols.json（契约①的数据载体）读取。
        // 新增一种作物 = 往 crops 数组加一个对象，不改任何 Java 代码。
        // 文件缺失/损坏/作物不支持 → 返回空列表，绝不抛异常（铁律 4）。
        if (cropId == null || cropId.isBlank()) {
            return List.of();
        }
        List<RawNode> cached = loadProtocols().get(cropId);
        if (cached != null) {
            return cached;
        }
        List<RawNode> llmCached = LLM_PROC_CACHE.get(cropId);
        if (llmCached != null) {
            return llmCached;
        }
        System.out.println("[buildNodes] 规程表中无 " + cropId + "，调大模型生成");
        List<RawNode> generated = LlmService.generateCropProcedure(cropId, cropId);
        if (!generated.isEmpty()) {
            LLM_PROC_CACHE.put(cropId, generated);
        }
        return generated;
    }

    /**
     * A2: 常见作物规程预热 —— 服务启动时后台异步预生成非预设作物规程，
     * 填满 LLM_PROC_CACHE，首次请求 34s → ~0.4s（缓存命中）。
     * daemon 线程，不阻塞启动；预热失败不影响服务可用性。
     */
    private static final List<String> PRELOAD_CROPS = List.of("tomato", "cotton", "chili_pepper");

    public static void preloadCommonCrops() {
        Thread t = new Thread(() -> {
            int ok = 0, skip = 0, fail = 0;
            for (String crop : PRELOAD_CROPS) {
                try {
                    if (loadProtocols().containsKey(crop) || LLM_PROC_CACHE.containsKey(crop)) {
                        skip++;
                        continue;
                    }
                    System.out.println("[preload] 预热作物规程: " + crop);
                    List<RawNode> nodes = LlmService.generateCropProcedure(crop, crop);
                    if (!nodes.isEmpty()) {
                        LLM_PROC_CACHE.put(crop, nodes);
                        ok++;
                        System.out.println("[preload] " + crop + " 完成: " + nodes.size() + " 节点");
                    } else {
                        fail++;
                    }
                } catch (Exception e) {
                    fail++;
                    System.out.println("[preload] " + crop + " 失败: " + e.getClass().getSimpleName());
                }
            }
            System.out.println("[preload] 规程预热完成: " + ok + " 成功, " + skip + " 跳过, " + fail + " 失败");
        }, "proc-preload");
        t.setDaemon(true);
        t.start();
    }

    /** A7: 作物信息（供 /api/crops 接口使用） */
    public record CropInfo(String cropId, String name, int nodeCount) {}

    /** 返回预设作物列表（含 LLM 缓存中已生成的作物） */
    public static List<CropInfo> listCropInfos() {
        List<CropInfo> infos = new ArrayList<>();
        Map<String, List<RawNode>> presets = loadProtocols();
        for (var entry : presets.entrySet()) {
            infos.add(new CropInfo(entry.getKey(), entry.getKey(), entry.getValue().size()));
        }
        for (var entry : LLM_PROC_CACHE.entrySet()) {
            if (!presets.containsKey(entry.getKey())) {
                infos.add(new CropInfo(entry.getKey(), entry.getKey(), entry.getValue().size()));
            }
        }
        return infos;
    }

    /**
     * ② 天气适配器：节点前后 N 天窗口聚合
     * 【全队唯一有网络 IO 的函数】 —— Open-Meteo
     *
     * 注意：这里接收的是整段日期序列，一次请求拿完，
     * 再由 WeatherWindow.around() 在本地给每个节点切窗口。
     *
     * 双模式（compute.mock 配置）：
     *   · true  —— 确定性假数据（演练用，刻意在两处安排降雨，好让
     *     「建议推迟」这条链路随时可被验证；MOCK_UPSTREAM_DELAY_MS 演练降级）
     *   · false —— 真实调用 Open-Meteo 免费接口（无 key、零依赖，
     *     用 JDK 自带 java.net.http，解析复用 util/JsonParser）
     *
     * 远期节点超出免费层范围（回溯 92 天 ~ 预报 16 天）拿不到数据时，
     * 该日留空 → 窗口为空 → scoreNode 返回 caution「请人工判断」，
     * 这正是产品口径：远期不确定就明说，绝不编造数字。
     * 任何失败都返回 degraded 标记，绝不抛异常（铁律 4）。
     */
    public static WeatherWindow fetchWindowWeather(double lat, double lon, List<String> dates) {
        if (AppConfig.mockMode()) {
            maybeDelay();
            Map<String, WeatherWindow.Daily> byDate = new LinkedHashMap<>();
            for (int i = 0; i < dates.size(); i++) {
                boolean rainy = (i >= 55 && i <= 60) || (i >= 118 && i <= 124);
                byDate.put(dates.get(i), rainy
                        ? new WeatherWindow.Daily(12.5, 6.2, 8.0, 18.0)
                        : new WeatherWindow.Daily(0.0, 2.4, 8.0, 20.0));
            }
            return new WeatherWindow(byDate, false, null);
        }
        return fetchFromOpenMeteo(lat, lon, dates);
    }

    // ============================================================
    // Open-Meteo 真实调用（免费接口，无 key，零第三方依赖）
    // ============================================================

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** archive + forecast 双请求并行专用线程池（A1：串行 ~3s → 并行 ~1.5s） */
    private static final ExecutorService WEATHER_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "weather-osm");
        t.setDaemon(true);
        return t;
    });

    /** Open-Meteo 免费层范围：forecast 接口实测 end_date 上限为今天 +15 天、回溯 92 天 */
    private static final int FORECAST_PAST_DAYS = 92;
    private static final int FORECAST_FUTURE_DAYS = 15;

    private static WeatherWindow fetchFromOpenMeteo(double lat, double lon, List<String> dates) {
        List<LocalDate> wanted;
        try {
            wanted = dates.stream().map(LocalDate::parse).distinct().sorted().toList();
        } catch (Exception e) {
            return WeatherWindow.unavailable("日期序列有非法格式，无法取天气");
        }
        if (wanted.isEmpty()) return WeatherWindow.unavailable("日期序列为空");

        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate min = wanted.get(0);
        LocalDate max = wanted.get(wanted.size() - 1);

        LocalDate archiveEnd = today.minusDays(7);
        LocalDate forecastStart = today.minusDays(FORECAST_PAST_DAYS);
        LocalDate forecastEnd = today.plusDays(FORECAST_FUTURE_DAYS);

        Map<String, WeatherWindow.Daily> byDate = new LinkedHashMap<>();
        String degradedReason = null;

        // A1: archive + forecast 双请求并行，总耗时从串行 ~3s 降至并行 ~1.5s
        CompletableFuture<WeatherWindow> archiveF;
        if (!min.isAfter(archiveEnd)) {
            LocalDate from = min;
            LocalDate to = max.isBefore(archiveEnd) ? max : archiveEnd;
            archiveF = CompletableFuture.supplyAsync(
                    () -> fetchOsm("https://archive-api.open-meteo.com/v1/archive", lat, lon, from, to),
                    WEATHER_POOL);
        } else {
            archiveF = CompletableFuture.completedFuture(null);
        }

        CompletableFuture<WeatherWindow> forecastF;
        if (!max.isBefore(today.minusDays(6))) {
            LocalDate from = min.isBefore(forecastStart) ? forecastStart : min;
            LocalDate to = max.isAfter(forecastEnd) ? forecastEnd : max;
            if (!to.isBefore(from)) {
                forecastF = CompletableFuture.supplyAsync(
                        () -> fetchOsm("https://api.open-meteo.com/v1/forecast", lat, lon, from, to),
                        WEATHER_POOL);
            } else {
                forecastF = CompletableFuture.completedFuture(null);
            }
        } else {
            forecastF = CompletableFuture.completedFuture(null);
        }

        // fetchOsm 内部已 catch 所有异常返回 unavailable，join 不会抛 CompletionException
        WeatherWindow archiveResult = archiveF.join();
        WeatherWindow forecastResult = forecastF.join();

        // 合并结果（保持原有语义：forecast 覆盖 archive 同日期数据）
        if (archiveResult != null) {
            if (archiveResult.degraded()) degradedReason = archiveResult.degradedReason();
            else byDate.putAll(archiveResult.byDate());
        }
        if (forecastResult != null) {
            if (forecastResult.degraded()) { if (degradedReason == null) degradedReason = forecastResult.degradedReason(); }
            else byDate.putAll(forecastResult.byDate());
        }

        if (byDate.isEmpty()) {
            return WeatherWindow.unavailable(degradedReason != null ? degradedReason : "查询区间超出 Open-Meteo 免费层范围");
        }
        return new WeatherWindow(byDate, false, null);
    }

    private static WeatherWindow fetchOsm(String baseUrl, double lat, double lon, LocalDate from, LocalDate to) {
        String url = baseUrl
                + "?latitude=" + lat + "&longitude=" + lon
                + "&daily=precipitation_sum,wind_speed_10m_max,temperature_2m_max,temperature_2m_min"
                + "&wind_speed_unit=ms&timezone=Asia%2FShanghai"
                + "&start_date=" + from + "&end_date=" + to;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return WeatherWindow.unavailable("Open-Meteo HTTP " + resp.statusCode() + "：" + apiReason(resp.body()));
            }
            return parseOpenMeteo(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WeatherWindow.unavailable("天气请求被中断");
        } catch (Exception e) {
            return WeatherWindow.unavailable("Open-Meteo 请求失败：" + e.getClass().getSimpleName());
        }
    }

    /** 从 Open-Meteo 错误响应里提取 reason 字段，方便在 degraded 原因里直接看到错在哪 */
    private static String apiReason(String body) {
        try {
            if (JsonParser.parse(body) instanceof Map<?, ?> m && m.get("reason") instanceof String s) {
                return s;
            }
        } catch (Exception ignored) {
        }
        return "响应无法解析";
    }

    /** 解析 Open-Meteo daily 响应为 WeatherWindow；失败返回 degraded，不抛异常 */
    private static WeatherWindow parseOpenMeteo(String body) {
        try {
            Object root = JsonParser.parse(body);
            if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("daily") instanceof Map<?, ?> daily)
                    || !(daily.get("time") instanceof List<?> times)) {
                return WeatherWindow.unavailable("Open-Meteo 响应缺 daily/time 字段");
            }
            List<Double> rain = doubles(daily.get("precipitation_sum"));
            List<Double> wind = doubles(daily.get("wind_speed_10m_max"));
            List<Double> tMax = doubles(daily.get("temperature_2m_max"));
            List<Double> tMin = doubles(daily.get("temperature_2m_min"));

            Map<String, WeatherWindow.Daily> byDate = new LinkedHashMap<>();
            for (int i = 0; i < times.size(); i++) {
                if (i >= rain.size() || i >= wind.size() || i >= tMax.size() || i >= tMin.size()) break;
                Double r = rain.get(i);
                Double w = wind.get(i);
                Double mx = tMax.get(i);
                Double mn = tMin.get(i);
                if (r == null || w == null || mx == null || mn == null) continue;
                byDate.put(String.valueOf(times.get(i)), new WeatherWindow.Daily(r, w, mn, mx));
            }
            if (byDate.isEmpty()) {
                return WeatherWindow.unavailable("Open-Meteo 返回区间内无可用的逐日数据");
            }
            return new WeatherWindow(byDate, false, null);
        } catch (Exception e) {
            return WeatherWindow.unavailable("Open-Meteo 响应解析失败：" + e.getClass().getSimpleName());
        }
    }

    private static List<Double> doubles(Object arr) {
        if (!(arr instanceof List<?> l)) return List.of();
        List<Double> out = new ArrayList<>(l.size());
        for (Object o : l) {
            out.add(o instanceof Number num ? num.doubleValue() : null);
        }
        return out;
    }

    /**
     * ③ 阈值打分规则：4 类作业 × 4 个天气因子
     * 验收：命中即返回处置动作；窗口取「最差一天」
     */
    public static ScoreResult scoreNode(String opType, WeatherWindow window) {
        if (window == null || window.degraded() || window.isEmpty()) {
            return ScoreResult.of(ScoreResult.CAUTION,
                    "天气数据暂不可用，请人工判断",
                    window == null ? "无天气窗口" : window.degradedReason());
        }
        switch (opType) {
            case "pesticide":  return scoreSpray(window);
            case "fertilize":  return scoreFertilize(window);
            case "irrigation": return scoreIrrigate(window);
            case "harvest":    return scoreHarvest(window);
            default:           return ScoreResult.of(ScoreResult.OK, "按规程推进", "该作业对天气不敏感");
        }
    }

    /**
     * ④ 节气计算：天文算法本地算
     * 验收：断网可用、零依赖、不查表
     *
     * 算法：Meeus 低精度太阳视黄经，误差 < 0.01°（对应时刻约 ±15 分钟）。
     * 判定口径：北京时间（东八区）当日 12:00 的黄经所处 15° 区间，节气交接日
     * 当天即算新节气——与中国官方日历口径一致；跨年边界由黄经连续性天然处理
     * （冬至 12 月下旬 → 小寒 1 月上旬，无需任何特判）。
     */
    public static TermHint getTermHint(String date) {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (Exception e) {
            return new TermHint(null, -1, "日期格式应为 yyyy-MM-dd");
        }
        try {
            double jdNoon = beijingNoonJd(d);
            int k = (int) Math.floor(solarLambda(jdNoon) / 15.0) % 24;
            if (beijingDateOfJd(solarCrossingJd((k + 1) * 15.0, jdNoon)).equals(d)) {
                k = (k + 1) % 24;
            }
            LocalDate next = beijingDateOfJd(solarCrossingJd((k + 1) * 15.0, jdNoon));
            return new TermHint(TERM_NAMES[k], (int) (next.toEpochDay() - d.toEpochDay()), TERM_TIPS[k]);
        } catch (Exception e) {
            return new TermHint(null, -1, "节气计算失败：" + e.getClass().getSimpleName());
        }
    }

    /**
     * ⑤ 节日规则引擎：配置驱动
     * 验收：新增销售节点不用改一行代码
     *
     * 数据来源：项目根目录 market-festivals.json。
     * 新增一个销售节日 = 往 festivals 数组加一个对象，不改任何 Java 代码。
     * 返回查询日期当天及未来的节日，daysTo = 节日日期 - 查询日期；
     * 当年已过的节日不返回（不滚到明年）。
     * 文件缺失/损坏 → 返回空列表并打警告，绝不抛异常（铁律 4）。
     */
    public static List<MarketHint> getMarketHint(String date, String cropId) {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (Exception e) {
            return List.of();
        }
        List<MarketHint> hints = new ArrayList<>();
        for (FestivalRule f : loadFestivals()) {
            if (!f.appliesTo(cropId)) continue;
            LocalDate festivalDate = f.resolve(d);
            if (festivalDate == null || festivalDate.isBefore(d)) continue;
            hints.add(new MarketHint(f.name(), (int) (festivalDate.toEpochDay() - d.toEpochDay()), f.tip()));
        }
        hints.sort(Comparator.comparingInt(MarketHint::daysTo));
        return hints;
    }

    /**
     * ⑥ 拍照识别：只出候选，不做决定
     * 验收：置信度普遍偏低时由前端回落搜索框
     *
     * 乙的 module6（CropRecognizeAdapter）不做图片识别，只做后处理：
     *   · 按置信度降序排序
     *   · 置信度低于阈值 → 回落搜索框
     *   · 品种不在规程表 → 编辑距离找相近品种 top3
     * 图片识别 API 调用是甲的职责，当前无外部识别服务可用，返回 stub 候选。
     * 接入识别 API 后：调 API 拿原始候选 → sortByConfidenceDesc 排序 → 返回。
     * 铁律：识别只缩小范围，最后一步永远由人确认。
     */
    public static List<Candidate> identifyCrop(byte[] image) {
        return sortByConfidenceDesc(List.of(
                new Candidate("dry_garlic", "山东金乡大蒜", 0.92),
                new Candidate("white_garlic", "普通白皮蒜", 0.61),
                new Candidate("garlic_sprout", "蒜苗", 0.34)
        ));
    }

    /** 按置信度降序排序候选列表（从乙的 module6 提取）。 */
    private static List<Candidate> sortByConfidenceDesc(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(Candidate::confidence).reversed());
        return sorted;
    }

    /** A6: 常见作物别名映射（西红柿→番茄、黄豆→大豆等） */
    private static final Map<String, String> CROP_ALIAS = Map.of(
            "西红柿", "番茄", "土豆", "马铃薯", "地瓜", "红薯",
            "黄豆", "大豆", "蒜", "大蒜", "麦子", "小麦",
            "稻子", "水稻", "苞谷", "玉米", "玉蜀黍", "玉米"
    );

    /**
     * A6: 按名称匹配作物候选（别名归一化 + 编辑距离回落）。
     * 识别接口在图片识别失败时，用文本输入走这条链路。
     * 验收：输入"西红柿" → 返回"番茄"候选
     */
    public static List<Candidate> matchCropByName(String name) {
        if (name == null || name.isBlank()) return List.of();

        String canonical = CROP_ALIAS.getOrDefault(name, name);
        List<Candidate> result = new ArrayList<>();
        Map<String, List<RawNode>> presets = loadProtocols();

        for (var entry : presets.entrySet()) {
            String cropId = entry.getKey();
            int dist = levenshteinDistance(canonical, cropId);
            double confidence = 1.0 / (1.0 + dist);
            result.add(new Candidate(cropId, cropId, confidence));
        }

        if (CROP_ALIAS.containsKey(name) && !presets.containsKey(canonical)) {
            result.add(new Candidate(canonical, canonical, 0.95));
        }
        if (!presets.containsKey(name) && !CROP_ALIAS.containsKey(name)) {
            result.add(new Candidate(name, name, 0.8));
        }

        return sortByConfidenceDesc(result);
    }

    /**
     * 编辑距离（Levenshtein Distance）：将 s1 转换为 s2 所需的最少单字符编辑操作次数。
     * 从乙的 module6 提取，用于品种校验时找最相近的已支持作物。
     */
    private static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[len1][len2];
    }

    // ============================================================
    // 规程表加载：进程内只读一次文件，与 AppConfig 同风格
    // ============================================================

    /** 铁律 5：opType 枚举固定，配置里出现别的值一律跳过该节点 */
    private static final Set<String> ALLOWED_OPS =
            Set.of("sowing", "irrigation", "fertilize", "pesticide", "harvest");

    private static volatile Map<String, List<RawNode>> PROTOCOLS;

    private static final ConcurrentHashMap<String, List<RawNode>> LLM_PROC_CACHE = new ConcurrentHashMap<>();

    private static Map<String, List<RawNode>> loadProtocols() {
        Map<String, List<RawNode>> cached = PROTOCOLS;
        if (cached != null) return cached;
        synchronized (ComputeStub.class) {
            if (PROTOCOLS == null) {
                PROTOCOLS = readProtocols("crop-protocols.json");
            }
            return PROTOCOLS;
        }
    }

    private static Map<String, List<RawNode>> readProtocols(String path) {
        Map<String, List<RawNode>> out = new LinkedHashMap<>();
        try {
            String text = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("crops") instanceof List<?> crops)) {
                System.err.println("[compute] 规程表 " + path + " 结构不对（根节点应为对象且含 crops 数组），按空规程处理");
                return out;
            }
            for (Object c : crops) {
                if (!(c instanceof Map<?, ?> crop)) continue;
                String id = nonBlank(crop.get("cropId"));
                if (id == null) {
                    System.err.println("[compute] 规程表有 crop 缺 cropId，已跳过");
                    continue;
                }
                if (!(crop.get("nodes") instanceof List<?> list)) {
                    System.err.println("[compute] 规程表 crop " + id + " 缺 nodes 数组，已跳过");
                    continue;
                }
                List<RawNode> nodes = new ArrayList<>();
                for (Object n : list) {
                    if (!(n instanceof Map<?, ?> node)) continue;
                    String op = nonBlank(node.get("opType"));
                    String title = nonBlank(node.get("title"));
                    Double day = dayOffsetOf(node.get("dayOffset"));
                    if (op == null || title == null || day == null) {
                        System.err.println("[compute] 规程表 crop " + id + " 有节点缺 opType/dayOffset/title，已跳过");
                        continue;
                    }
                    if (!ALLOWED_OPS.contains(op)) {
                        System.err.println("[compute] 规程表 crop " + id + " 节点 opType 非法：" + op + "，已跳过（铁律 5）");
                        continue;
                    }
                    nodes.add(new RawNode(id, op, (int) Math.rint(day), title));
                }
                nodes.sort(Comparator.comparingInt(RawNode::dayOffset));
                out.put(id, List.copyOf(nodes));
            }
        } catch (Exception e) {
            System.err.println("[compute] 读取规程表 " + path + " 失败，按空规程处理（不抛异常）：" + e.getMessage());
            return new LinkedHashMap<>();
        }
        return out;
    }

    private static String nonBlank(Object v) {
        return v instanceof String s && !s.isBlank() ? s : null;
    }

    // ============================================================
    // 节日规则表加载：进程内只读一次文件，与规程表同风格
    // ============================================================

    private static volatile List<FestivalRule> FESTIVALS;

    private static List<FestivalRule> loadFestivals() {
        List<FestivalRule> cached = FESTIVALS;
        if (cached != null) return cached;
        synchronized (ComputeStub.class) {
            if (FESTIVALS == null) {
                FESTIVALS = readFestivals("market-festivals.json");
            }
            return FESTIVALS;
        }
    }

    private static List<FestivalRule> readFestivals(String path) {
        List<FestivalRule> out = new ArrayList<>();
        try {
            String text = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("festivals") instanceof List<?> list)) {
                System.err.println("[compute] 节日规则表 " + path + " 结构不对（根节点应为对象且含 festivals 数组），按空规则处理");
                return out;
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String name = nonBlank(m.get("name"));
                String type = nonBlank(m.get("anchorType"));
                String tip = nonBlank(m.get("tip"));
                if (name == null || type == null) {
                    System.err.println("[compute] 节日规则缺 name/anchorType，已跳过");
                    continue;
                }
                Map<String, String> dates = new LinkedHashMap<>();
                if (m.get("dates") instanceof Map<?, ?> dm) {
                    for (Map.Entry<?, ?> e : dm.entrySet()) {
                        if (e.getValue() instanceof String v) {
                            dates.put(String.valueOf(e.getKey()), v);
                        }
                    }
                }
                List<String> crops = m.get("crops") instanceof List<?> cl
                        ? cl.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                        : null;
                out.add(new FestivalRule(name, type, nonBlank(m.get("anchor")), dates, tip, crops));
            }
        } catch (Exception e) {
            System.err.println("[compute] 读取节日规则表 " + path + " 失败，按空规则处理（不抛异常）：" + e.getMessage());
            return new ArrayList<>();
        }
        return out;
    }

    /**
     * 节日规则：anchorType=fixed（每年固定公历 MM-dd）或 perYear（按年指定 MM-dd）。
     * crops 为 null/空表示对全部作物生效，否则只对列表内作物生效。
     */
    private record FestivalRule(String name, String anchorType, String anchor,
                                Map<String, String> dates, String tip, List<String> crops) {

        /** 解析出该规则在查询日期视角下的节日日期；取不到返回 null */
        LocalDate resolve(LocalDate query) {
            String mmdd = "fixed".equals(anchorType) ? anchor
                    : "perYear".equals(anchorType) && dates != null ? dates.get(String.valueOf(query.getYear()))
                    : null;
            if (mmdd == null || mmdd.length() != 5) return null;
            try {
                return LocalDate.parse(query.getYear() + "-" + mmdd);
            } catch (Exception e) {
                return null;
            }
        }

        boolean appliesTo(String cropId) {
            return crops == null || crops.isEmpty() || (cropId != null && crops.contains(cropId));
        }
    }

    private static Double dayOffsetOf(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }

    // ============================================================
    // 规则打分：从乙的 module3 提取（4 个规则包 × 12 条阈值）
    // 阈值来源：乙的 init.sql job_sensitivity_threshold 表
    // 风力已从蒲福风级转换为 m/s（5级→8.0m/s, 4级→5.5m/s）
    // ============================================================

    private static final double SPRAY_RAIN_RED = 10.0;
    private static final double SPRAY_RAIN_YELLOW = 5.0;
    private static final double SPRAY_WIND_RED = 8.0;
    private static final double SPRAY_WIND_YELLOW = 5.5;
    private static final double SPRAY_TEMP_RED = 35.0;

    private static final double FERT_RAIN_CUM_RED = 50.0;
    private static final double FERT_RAIN_CUM_YELLOW = 25.0;
    private static final double FERT_TEMP_LOW_RED = 10.0;

    private static final double IRRI_RAIN_RED = 10.0;
    private static final double IRRI_RAIN_YELLOW = 3.0;

    private static final double HARV_RAIN_RED = 10.0;
    private static final double HARV_RAIN_YELLOW = 3.0;

    /** 打药规则包：怕降雨、大风、高温。逐日检查，命中任意 RED 即 RED。 */
    private static ScoreResult scoreSpray(WeatherWindow window) {
        boolean red = false;
        boolean yellow = false;
        List<String> reasons = new ArrayList<>();

        for (Map.Entry<String, WeatherWindow.Daily> e : window.byDate().entrySet()) {
            String date = e.getKey();
            WeatherWindow.Daily d = e.getValue();
            double rain = d.rainMm();
            double wind = d.windMs();
            double temp = (d.minTempC() + d.maxTempC()) / 2.0;

            if (rain >= SPRAY_RAIN_RED) {
                red = true;
                reasons.add(date + " 降雨量 " + fmt(rain) + "mm 超过打药阈值");
            }
            if (wind >= SPRAY_WIND_RED) {
                red = true;
                reasons.add(date + " 风速 " + fmt(wind) + "m/s 超过阈值");
            }
            if (temp >= SPRAY_TEMP_RED) {
                red = true;
                reasons.add(date + " 高温 " + fmt(temp) + "℃ 超过阈值");
            }
            if (rain >= SPRAY_RAIN_YELLOW) yellow = true;
            if (wind >= SPRAY_WIND_YELLOW) yellow = true;
        }

        if (red) {
            return ScoreResult.of(ScoreResult.POSTPONE, "建议推迟", String.join("；", reasons));
        }
        if (yellow) {
            return ScoreResult.of(ScoreResult.CAUTION, "谨慎作业", "风速或降雨接近阈值，注意药液飘移和冲刷");
        }
        return ScoreResult.of(ScoreResult.OK, "窗口期适合作业", "无降雨、风速平稳、气温适宜");
    }

    /** 施肥规则包：怕累计暴雨冲刷、低温土壤吸收差。 */
    private static ScoreResult scoreFertilize(WeatherWindow window) {
        double cumulativeRain = 0;
        double minTemp = Double.POSITIVE_INFINITY;

        for (WeatherWindow.Daily d : window.byDate().values()) {
            cumulativeRain += d.rainMm();
            double avgTemp = (d.minTempC() + d.maxTempC()) / 2.0;
            if (avgTemp < minTemp) minTemp = avgTemp;
        }

        List<String> reasons = new ArrayList<>();
        boolean red = false;
        boolean yellow = false;

        if (cumulativeRain >= FERT_RAIN_CUM_RED) {
            red = true;
            reasons.add("窗口累计降雨量 " + fmt(cumulativeRain) + "mm 超过冲刷阈值，肥料易流失");
        }
        if (cumulativeRain >= FERT_RAIN_CUM_YELLOW) yellow = true;
        if (minTemp <= FERT_TEMP_LOW_RED) {
            red = true;
            reasons.add("窗口最低日均温 " + fmt(minTemp) + "℃，土壤吸收差");
        }

        if (red) {
            return ScoreResult.of(ScoreResult.POSTPONE, "建议推迟", String.join("；", reasons));
        }
        if (yellow) {
            return ScoreResult.of(ScoreResult.CAUTION, "谨慎作业", "累计降雨偏多，注意肥料流失风险");
        }
        return ScoreResult.of(ScoreResult.OK, "窗口期适合作业", "土壤墒情利于养分吸收");
    }

    /** 灌溉规则包：检测降雨。近期有降雨→暂缓；无降雨→适宜。 */
    private static ScoreResult scoreIrrigate(WeatherWindow window) {
        double maxRain = window.maxRainMm();

        if (maxRain >= IRRI_RAIN_RED) {
            return ScoreResult.of(ScoreResult.POSTPONE, "建议延后",
                    "近期有降雨 " + fmt(maxRain) + "mm，建议暂缓灌溉");
        }
        if (maxRain >= IRRI_RAIN_YELLOW) {
            return ScoreResult.of(ScoreResult.CAUTION, "谨慎作业",
                    "近期少量降雨 " + fmt(maxRain) + "mm，谨慎灌溉");
        }
        return ScoreResult.of(ScoreResult.OK, "窗口期适合作业", "近期无有效降雨，可以补水");
    }

    /** 采收规则包：遇降雨风险→建议提前抢收；无降雨→适宜。 */
    private static ScoreResult scoreHarvest(WeatherWindow window) {
        for (Map.Entry<String, WeatherWindow.Daily> e : window.byDate().entrySet()) {
            if (e.getValue().rainMm() >= HARV_RAIN_RED) {
                return ScoreResult.of(ScoreResult.POSTPONE, "建议提前抢收",
                        "窗口内 " + e.getKey() + " 存在降雨风险，建议提前采收");
            }
        }

        double maxRain = window.maxRainMm();
        if (maxRain >= HARV_RAIN_YELLOW) {
            return ScoreResult.of(ScoreResult.CAUTION, "关注采收时机",
                    "少量降雨 " + fmt(maxRain) + "mm，关注采收时机");
        }
        return ScoreResult.of(ScoreResult.OK, "窗口期适合作业", "天气利于采收与晾晒");
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    private ComputeStub() {
    }

    // ============================================================
    // 二十四节气天文算法（本地计算，断网可用，不查任何固定日期表）
    // ============================================================

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final double JD_2000 = 2451545.0;

    /** 二十四节气名（索引 = 黄经 15° 的倍数序号，0 = 春分 0°） */
    private static final String[] TERM_NAMES = {
            "春分", "清明", "谷雨", "立夏", "小满", "芒种",
            "夏至", "小暑", "大暑", "立秋", "处暑", "白露",
            "秋分", "寒露", "霜降", "立冬", "小雪", "大雪",
            "冬至", "小寒", "大寒", "立春", "雨水", "惊蛰"
    };

    /**
     * 各节气通用农事提示。当前为农谚级通用文案，正式文案由成员1 按作物规程复核
     * （铁律 8：推测内容不许冒充确定信息）。
     */
    private static final String[] TERM_TIPS = {
            "春分麦起身，田间管理莫放松",           // 春分
            "清明前后，种瓜点豆",                   // 清明
            "雨生百谷，播种插秧正当忙",             // 谷雨
            "立夏看夏，夏收作物进入生长后期",       // 立夏
            "小满未满，防干热风保灌浆",             // 小满
            "芒种忙种，收麦插秧两头忙",             // 芒种
            "夏至伏天到，中耕排涝并重",             // 夏至
            "小暑入三伏，防涝防虫要上心",           // 小暑
            "大暑热正盛，双抢大忙时节",             // 大暑
            "立秋秋管始，秋收作物产量形成期",       // 立秋
            "处暑天渐凉，秋收准备趁早",             // 处暑
            "白露身不露，警惕寒露风为害",           // 白露
            "秋分收秋种秋，抢收抢种两头忙",         // 秋分
            "寒露百草枯，秋收扫尾冬播始",           // 寒露
            "霜降杀百草，防初霜冻害",               // 霜降
            "立冬万物藏，冬灌保温保墒",             // 立冬
            "小雪封地，窖藏防冻",                   // 小雪
            "大雪封河，农田保温防冻害",             // 大雪
            "冬至进数九，越冬作物防冻盖被",         // 冬至
            "小寒大寒冻作团，保温是关键",           // 小寒
            "大寒岁末，备好春耕农资",               // 大寒
            "立春一年始，备耕整地先行",             // 立春
            "雨水润物，春灌保墒正当时",             // 雨水
            "惊蛰春雷动，春耕全面启动"              // 惊蛰
    };

    /** 北京时间当天 12:00 的儒略日（JD，UT 尺度） */
    private static double beijingNoonJd(LocalDate d) {
        return d.toEpochDay() + 2440587.5 + 4.0 / 24.0;
    }

    /** 太阳视黄经（度，0-360），Meeus 低精度公式 */
    private static double solarLambda(double jd) {
        double t = (jd - JD_2000) / 36525.0;
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t;
        double mr = Math.toRadians(m);
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(mr)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * mr)
                + 0.000289 * Math.sin(3 * mr);
        double omega = 125.04 - 1934.136 * t;
        double lambda = l0 + c - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
        lambda %= 360.0;
        return lambda < 0 ? lambda + 360.0 : lambda;
    }

    /** 太阳黄经到达 target 的时刻（JD），从 jdStart 起迭代逼近 */
    private static double solarCrossingJd(double target, double jdStart) {
        double jd = jdStart;
        for (int i = 0; i < 40; i++) {
            double diff = target - solarLambda(jd);
            while (diff > 180) diff -= 360;
            while (diff < -180) diff += 360;
            if (Math.abs(diff) < 1e-7) break;
            jd += diff / 0.98565;
        }
        return jd;
    }

    /** JD 时刻 → 北京时间所在日期 */
    private static LocalDate beijingDateOfJd(double jd) {
        long epochSec = (long) Math.floor((jd - 2440587.5) * 86400.0);
        return Instant.ofEpochSecond(epochSec).atZone(SHANGHAI).toLocalDate();
    }

    /**
     * 人为延迟，用来演练降级分支。
     * 用 MOCK_UPSTREAM_DELAY_MS=3000 配上 UPSTREAM_TIMEOUT_MS=800，
     * 就能稳定复现「上游超时 → 返回缓存 → 缓存也没有就明说取不到」这条链路。
     * 乙接入真实天气源之后，这个延迟自然失效。
     */
    private static void maybeDelay() {
        int delay = AppConfig.mockUpstreamDelayMs();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
