package com.cropcalendar.service.crop;

import com.cropcalendar.service.weather.ClimateNormals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 积温播期建议（V5 T20）。
 *
 * 节气给的是"祖辈的经验窗口"，积温回答的是"当年热量够不够"。两者常常差一两周，
 * 所以不替用户拍板：两个日期都摆出来、各带一句依据，由用户选。
 *
 * 所需积温取自 data/crop-base/properties.json 的 tempRequirement
 * （如"≥10℃积温需 2500℃以上"），这是人工校准字段；没有标积温的作物不硬算，
 * 直接说"品种未标注所需积温"。
 *
 * 逐日气温用 ClimateNormals 的近三年同日聚合值；取不到就返回不可用，
 * 不用默认温度凑一个看起来精确的日期。
 */
public final class GddAdvisor {

    private static final DateTimeFormatter D = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 只认 3~5 位数的温度，避开 "≥10℃" 里的 10 */
    private static final Pattern GDD_RANGE = Pattern.compile("(\\d{3,5})\\s*[-~至]\\s*(\\d{3,5})\\s*℃");
    private static final Pattern GDD_SINGLE = Pattern.compile("(\\d{3,5})\\s*℃");
    /** 生长下限温度："≥10℃积温" 里的 10 */
    private static final Pattern BASE_PATTERN = Pattern.compile("≥\\s*(\\d{1,2})\\s*℃");

    /** 活动积温的默认起算温度：作物属性里写的就是 "≥10℃积温" */
    public static final double BASE_TEMP_C = 10.0;
    private static final int STABLE_RUN = 5;
    private static final int MAX_DAYS = 240;
    /** 缺测超过这么多天，结论就不可信了 */
    private static final int MAX_MISSING = 60;

    public record Suggestion(String sowDate, int daysFromStart, double thresholdC,
                             double targetGdd, String maturityDate,
                             boolean available, String basis, String note) {
        public static Suggestion unavailable(String note) {
            return new Suggestion(null, 0, BASE_TEMP_C, 0, null, false, null, note);
        }
    }

    public record Latest(String date, String basis) {
    }

    record Acc(int day, double sum, int missing) {
    }

    private GddAdvisor() {
    }

    /**
     * 积温口径的播期建议。
     *
     * 春播作物：从下一个 2 月 1 日起，常年日均温连续 5 天稳定通过生长下限温度
     * （作物属性里写的 ≥10℃）的第一天。
     * 秋播作物（冬小麦等）：从下一个 8 月 15 日起，日均温连续 5 天降到 15℃
     * （下限 +5℃）以下的第一天，作为秋播适期。
     *
     * 为什么不是"累计到 2500℃ 的那天"：那个日期是**成熟期**，不是播期；从 11 月
     * 起算会跨过整个冬天，永远累计不够，拿它当播期建议会把用户带到沟里。
     * 所以这里用"热量条件稳定达标"定播期，再用积温反推预计成熟期写进依据，
     * 让用户能判断"现在播，霜前能不能熟"。
     */
    public static Suggestion earliest(double lat, double lon, LocalDate start, CropProperties.Props props) {
        if (props == null) return Suggestion.unavailable("作物属性未知，无法判断热量条件");
        boolean autumn = isAutumnSown(props.suitableSeason());
        double threshold = parseThreshold(props.tempRequirement());
        double target = parseTargetGdd(props.tempRequirement());
        // 搜索从一个明确的窗口起点开始，避免秋季"今天已通过 10℃"被当成春播日
        LocalDate searchStart = autumn ? nextWindow(start, 8, 15) : nextWindow(start, 2, 1);

        List<Double> means = new ArrayList<>(MAX_DAYS);
        for (int i = 0; i < MAX_DAYS; i++) {
            means.add(ClimateNormals.meanTempOn(lat, lon, searchStart.plusDays(i)));
        }
        int missing = 0;
        for (Double m : means) if (m == null || m.isNaN()) missing++;
        if (missing >= MAX_MISSING) {
            return Suggestion.unavailable("常年气温数据不足，本次只给节气建议");
        }

        double ceiling = threshold + 5;
        int day = autumn
                ? stableBelow(means, ceiling, STABLE_RUN)
                : stableAbove(means, threshold, STABLE_RUN);
        if (day < 0) {
            return Suggestion.unavailable("按常年气温推算，240 天内没有连续 " + STABLE_RUN + " 天"
                    + (autumn ? "降至 " + fmt(ceiling) + "℃ 以下" : "稳定通过 " + fmt(threshold) + "℃"));
        }
        LocalDate date = searchStart.plusDays(day);

        String maturity = null;
        if (target > 0 && day < means.size()) {
            Acc acc = accumulate(means.subList(day, means.size()), threshold, target);
            if (acc.day() >= 0) maturity = date.plusDays(acc.day()).format(D);
        }

        String seasonNote = searchStart.getYear() > start.getYear() ? "（今年窗口已过，按下一季算）" : "";
        String basis = seasonNote + (autumn
                ? "按常年日均温，秋季连续 " + STABLE_RUN + " 天降至 " + fmt(ceiling) + "℃ 以下的第一天"
                : "按常年日均温，春季连续 " + STABLE_RUN + " 天稳定通过 " + fmt(threshold) + "℃ 的第一天")
                + (target <= 0
                ? "；品种未标注所需积温，未估算成熟期"
                : maturity != null
                ? "；按 " + fmt(target) + "℃ 活动积温预计 " + maturity + " 成熟"
                : "；按 " + fmt(target) + "℃ 积温，240 天内未达成熟期");
        return new Suggestion(date.format(D), day, threshold, target, maturity, true, basis, null);
    }

    /**
     * 最晚播期：初霜日倒推全生育期，保证成熟前不被霜打。
     * 今年的初霜已过、或倒推出来早于今天，就看明年。
     */
    public static Latest latestSowDate(double lat, CropProperties.Props props, LocalDate today) {
        if (props == null || props.totalDays() <= 0) return null;
        // 秋播作物看的是越冬前壮苗，不是"霜前成熟"，用初霜倒推会给出春播日期，干脆不给
        if (isAutumnSown(props.suitableSeason())) return null;
        int days = props.totalDays();
        for (int year = today.getYear(); year <= today.getYear() + 1; year++) {
            LocalDate frost = AgroZone.firstAutumnFrost(year, lat);
            LocalDate latest = frost.minusDays(days);
            if (!latest.isBefore(today)) {
                return new Latest(latest.format(D),
                        "留足 " + days + " 天生育期，赶在常年初霜 " + frost.format(D) + " 前成熟");
            }
        }
        return null;
    }

    /** 秋播作物：季节里没有"春"、但有"秋/冬"，才按秋播窗口算。 */
    static boolean isAutumnSown(String season) {
        if (season == null) return false;
        if (season.contains("春")) return false;
        return season.contains("秋") || season.contains("冬");
    }

    /** 下一个 month-day（含今天）；今年的已过就取明年。 */
    static LocalDate nextWindow(LocalDate today, int month, int day) {
        LocalDate d = LocalDate.of(today.getYear(), month, day);
        return d.isBefore(today) ? d.plusYears(1) : d;
    }

    /** 连续 run 天 ≤ ceiling 的第一天（从 0 起）；缺测会打断连续段。 */
    static int stableBelow(List<Double> means, double ceiling, int run) {
        int count = 0;
        for (int i = 0; i < means.size(); i++) {
            Double m = means.get(i);
            if (m == null || m.isNaN()) { count = 0; continue; }
            if (m <= ceiling) {
                count++;
                if (count >= run) return i - run + 1;
            } else {
                count = 0;
            }
        }
        return -1;
    }

    /** 连续 run 天 ≥ threshold 的第一天（从 0 起）；缺测会打断连续段。 */
    static int stableAbove(List<Double> means, double threshold, int run) {
        int count = 0;
        for (int i = 0; i < means.size(); i++) {
            Double m = means.get(i);
            if (m == null || m.isNaN()) { count = 0; continue; }
            if (m >= threshold) {
                count++;
                if (count >= run) return i - run + 1;
            } else {
                count = 0;
            }
        }
        return -1;
    }

    /** 从 "≥10℃积温" 里取生长下限温度；没有就按 10℃。 */
    static double parseThreshold(String tempRequirement) {
        if (tempRequirement == null) return BASE_TEMP_C;
        Matcher m = BASE_PATTERN.matcher(tempRequirement);
        if (m.find()) {
            double v = Double.parseDouble(m.group(1));
            if (v >= 0 && v <= 20) return v;
        }
        return BASE_TEMP_C;
    }

    /** 纯计算，单测直接调：缺测日跳过，达标返回第几天（从 0 起）。 */
    static Acc accumulate(List<Double> means, double base, double target) {
        double sum = 0;
        int missing = 0;
        for (int i = 0; i < means.size(); i++) {
            Double m = means.get(i);
            if (m == null || m.isNaN()) { missing++; continue; }
            sum += Math.max(0, m - base);
            if (sum >= target) return new Acc(i, sum, missing);
        }
        return new Acc(-1, sum, missing);
    }

    /**
     * 从 "喜温，≥10℃积温需 2500℃以上" 里取 2500；区间 "1800~2200℃" 取下限 1800
     * （下限才是"能不能熟"的门槛，拿上限会把播期推得过于保守）。没有就返回 -1。
     */
    static double parseTargetGdd(String tempRequirement) {
        if (tempRequirement == null) return -1;
        Matcher range = GDD_RANGE.matcher(tempRequirement);
        if (range.find()) {
            double low = Double.parseDouble(range.group(1));
            if (low >= 1000) return low;
        }
        Matcher m = GDD_SINGLE.matcher(tempRequirement);
        while (m.find()) {
            double v = Double.parseDouble(m.group(1));
            if (v >= 1000) return v;
        }
        return -1;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(round1(v));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
