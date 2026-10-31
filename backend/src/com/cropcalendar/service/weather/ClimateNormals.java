package com.cropcalendar.service.weather;

import com.cropcalendar.util.HttpJson;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 气候常年值（近三年同日聚合）。
 *
 * 为什么需要它：日历里三个月后的节点，任何"那天有雨、不宜喷药"的结论都是假精度。
 * 人能接受的说法是"9 月中下旬，常年这个时候日均 22℃、约四成日子有雨，注意排水"，
 * 而不是"9 月 17 日小雨转多云"。
 *
 * 所以远期不再逐日套去年同一天的天气，而是给出**时段气候概览**：
 * 近三年同一日历窗口的平均气温、最高/最低、降水日占比。
 *
 * 一次请求取回近三年逐日数据，按「一年中的第几天」聚合，之后任意窗口都能查。
 */
public final class ClimateNormals {

    private static final DateTimeFormatter D = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long TTL_MS = 24L * 3600 * 1000;

    /** 每个地点一份聚合结果，避免同一页面多次生成日历时重复打归档接口。 */
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(Map<Integer, DayStat> byDoy, long at) {
    }

    /** 某一天（不分年份）的统计量。 */
    public static final class DayStat {
        double tempSum;
        double tmaxSum;
        double tminSum;
        int rainDays;
        int n;

        void add(double t, double tmax, double tmin, double rain) {
            if (!Double.isNaN(t)) tempSum += t;
            if (!Double.isNaN(tmax)) tmaxSum += tmax;
            if (!Double.isNaN(tmin)) tminSum += tmin;
            if (!Double.isNaN(rain) && rain > 0.1) rainDays++;
            n++;
        }
    }

    /** 一个时段的气候概览。 */
    public record Outlook(double meanTempC, double maxTempC, double minTempC,
                          double rainDayRatio, int years, boolean available) {
        public static Outlook unavailable() {
            return new Outlook(0, 0, 0, 0, 0, false);
        }
    }

    private ClimateNormals() {
    }

    /**
     * 取 center 前后各 halfWindow 天的气候概览。
     * 数据不可用时返回 unavailable，调用方必须如实降级，不许编数字。
     */
    public static Outlook window(double lat, double lon, LocalDate center, int halfWindow) {
        Map<Integer, DayStat> byDoy = load(lat, lon);
        if (byDoy == null || byDoy.isEmpty()) return Outlook.unavailable();

        double tSum = 0, tmaxSum = 0, tminSum = 0;
        int days = 0, rainDays = 0, n = 0;
        for (int i = -halfWindow; i <= halfWindow; i++) {
            LocalDate d = center.plusDays(i);
            DayStat s = byDoy.get(d.getDayOfYear());
            if (s == null || s.n == 0) continue;
            tSum += s.tempSum / s.n;
            tmaxSum += s.tmaxSum / s.n;
            tminSum += s.tminSum / s.n;
            days++;
            // 某天"有雨的比例"而非"有雨的天数"，跨年可比
            rainDays += s.rainDays;
            n += s.n;
        }
        if (days == 0) return Outlook.unavailable();
        int years = Math.max(1, n / days);
        return new Outlook(
                round1(tSum / days),
                round1(tmaxSum / days),
                round1(tminSum / days),
                n > 0 ? round2((double) rainDays / n) : 0,
                years,
                true);
    }

    // ------------------------------------------------------------------ 抓取

    private static Map<Integer, DayStat> load(double lat, double lon) {
        String key = String.format("%.2f,%.2f", lat, lon);
        CacheEntry e = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (e != null && now - e.at() < TTL_MS) return e.byDoy();

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusYears(3);
        LocalDate to = today.minusDays(2);
        String url = String.format("https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f"
                        + "&start_date=%s&end_date=%s"
                        + "&daily=temperature_2m_mean,temperature_2m_max,temperature_2m_min,precipitation_sum"
                        + "&timezone=auto",
                lat, lon, from.format(D), to.format(D));

        Map<?, ?> root = HttpJson.getMap(url, 15);
        if (root == null) {
            CACHE.put(key, new CacheEntry(new HashMap<>(), now));
            return null;
        }
        Object dObj = root.get("daily");
        if (!(dObj instanceof Map<?, ?> daily)) return null;
        List<Double> tmean = HttpJson.list(root, "daily", "temperature_2m_mean");
        List<Double> tmax = HttpJson.list(root, "daily", "temperature_2m_max");
        List<Double> tmin = HttpJson.list(root, "daily", "temperature_2m_min");
        List<Double> rain = HttpJson.list(root, "daily", "precipitation_sum");
        List<?> timeList = daily.get("time") instanceof List<?> tl ? tl : List.of();

        Map<Integer, DayStat> byDoy = new HashMap<>();
        for (int i = 0; i < timeList.size(); i++) {
            int doy;
            try {
                doy = LocalDate.parse(String.valueOf(timeList.get(i))).getDayOfYear();
            } catch (Exception ex) {
                continue;
            }
            double t = i < tmean.size() ? tmean.get(i) : Double.NaN;
            double tx = i < tmax.size() ? tmax.get(i) : Double.NaN;
            double tn = i < tmin.size() ? tmin.get(i) : Double.NaN;
            double r = i < rain.size() ? rain.get(i) : Double.NaN;
            if (Double.isNaN(t) && Double.isNaN(r)) continue;
            byDoy.computeIfAbsent(doy, k -> new DayStat()).add(t, tx, tn, r);
        }
        CACHE.put(key, new CacheEntry(byDoy, now));
        return byDoy;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
