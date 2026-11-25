package com.cropcalendar.service.disease;

import com.cropcalendar.util.AppTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 病害 → 打药 → 安全间隔 → 采收后移的纯计算（15天冲刺 F02）。
 *
 * 只做计算，不读文件、不取天气：天气由 Handler 取好传进来，
 * 这样这段规则能脱离网络做回归测试（见 SprayPlannerTest）。
 *
 * 口径（与规划书 F02 一致）：
 *   · 扫描诊断后 5 天天气，打分：雨≤阈值 +40，风≤阈值 +20，日均温在区间 +40；
 *   · 同分选最早的一天（施药越早越好，别无谓拖到周末）；
 *   · 缺某个维度的天气就不给该项加分，不拿默认值冒充实测；
 *   · 整段都缺数时给 0 分并标记 noWeather=true，由上层明确告知"临近再看预报"；
 *   · earliestHarvestDate = sprayDate + safetyIntervalDays；
 *   · 所有早于该日的 harvest 节点后移到该日，已晚于等于的不动。
 */
public final class SprayPlanner {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private SprayPlanner() {
    }

    /** 某日天气；null 字段表示该项缺测，打分时不给分也不当 0。 */
    public record DayWeather(String date, Double rainMm, Double windMs, Double meanTempC) {
    }

    /** 选出的打药日与打分依据。 */
    public record SprayDay(String date, int score, String reason, boolean noWeather) {
    }

    /** 单个采收节点的后移结果。 */
    public record HarvestShift(String originalDate, String newDate, int days) {
    }

    /**
     * 从候选天气里选打分最高的打药日。
     *
     * @param days      诊断后 N 天的天气（按日期升序）
     * @param rainMaxMm 雨量阈值，当天降雨≤该值 +40
     * @param windMaxMs 风速阈值，当天风速≤该值 +20
     * @param tempLo    日均温下限（含）
     * @param tempHi    日均温上限（含）
     */
    public static SprayDay pickSprayDay(List<DayWeather> days, double rainMaxMm,
                                        double windMaxMs, double tempLo, double tempHi) {
        if (days == null || days.isEmpty()) {
            return new SprayDay(AppTime.today().plusDays(1).format(DATE), 0,
                    "诊断后 5 天的天气数据暂不可用；按最早可施药日排期，临近 7 天再据实调整", true);
        }
        Scored best = null;
        for (DayWeather d : days) {
            if (d == null || d.date() == null || d.date().isBlank()) continue;
            int score = 0;
            StringBuilder why = new StringBuilder();
            if (d.rainMm() != null) {
                if (d.rainMm() <= rainMaxMm) {
                    score += 40;
                    why.append("雨 ").append(fmt(d.rainMm())).append("mm≤").append(fmt(rainMaxMm)).append("mm；");
                } else {
                    why.append("雨 ").append(fmt(d.rainMm())).append("mm 偏大；");
                }
            }
            if (d.windMs() != null) {
                if (d.windMs() <= windMaxMs) {
                    score += 20;
                    why.append("风 ").append(fmt(d.windMs())).append("m/s≤").append(fmt(windMaxMs)).append("m/s；");
                } else {
                    why.append("风 ").append(fmt(d.windMs())).append("m/s 偏大；");
                }
            }
            if (d.meanTempC() != null) {
                if (d.meanTempC() >= tempLo && d.meanTempC() <= tempHi) {
                    score += 40;
                    why.append("日均温 ").append(fmt(d.meanTempC())).append("℃ 适宜；");
                } else {
                    why.append("日均温 ").append(fmt(d.meanTempC())).append("℃ 不在适宜区间；");
                }
            }
            Scored cur = new Scored(d.date(), score, why.toString().replaceAll("；$", ""));
            // 同分选更早的日期；日期非法时跳过，避免把坏数据排进日历
            if (validDate(cur.date()) && (best == null || cur.score > best.score
                    || (cur.score == best.score && cur.date.compareTo(best.date) < 0))) {
                best = cur;
            }
        }
        if (best == null) {
            return new SprayDay(AppTime.today().plusDays(1).format(DATE), 0,
                    "候选日期都不可用；按最早可施药日排期，临近再看预报", true);
        }
        boolean noWeather = best.score == 0;
        String reason = noWeather
                ? "诊断后 5 天的天气数据暂不可用；按最早可施药日排期，临近 7 天再据实调整"
                : "打药日打分 " + best.score + "/100（" + best.why + "）";
        return new SprayDay(best.date, best.score, reason, noWeather);
    }

    /** earliestHarvestDate = sprayDate + safetyIntervalDays */
    public static String earliestHarvest(String sprayDate, int safetyDays) {
        return LocalDate.parse(sprayDate, DATE).plusDays(Math.max(0, safetyDays)).format(DATE);
    }

    /**
     * 把早于 earliest 的采收节点后移到 earliest。
     *
     * 已晚于等于 earliest 的节点原样保留；多个采收节点逐一检查，
     * 返回实际发生移动的节点（没有移动返回空列表）。
     */
    public static List<HarvestShift> shiftHarvests(List<String> harvestDates, String earliest) {
        List<HarvestShift> shifts = new ArrayList<>();
        if (harvestDates == null || harvestDates.isEmpty()) return shifts;
        LocalDate floor = LocalDate.parse(earliest, DATE);
        List<String> sorted = new ArrayList<>(harvestDates.stream().filter(SprayPlanner::validDate).toList());
        sorted.sort(Comparator.naturalOrder());
        for (String raw : sorted) {
            LocalDate d = LocalDate.parse(raw, DATE);
            if (d.isBefore(floor)) {
                shifts.add(new HarvestShift(raw, floor.format(DATE),
                        (int) ChronoUnit.DAYS.between(d, floor)));
            }
        }
        return shifts;
    }

    /** 诊断日起未来 N 天的日期序列（含诊断日次日），传给天气接口。 */
    public static List<String> nextDays(String fromDate, int count) {
        LocalDate start = fromDate == null || !validDate(fromDate)
                ? AppTime.today() : LocalDate.parse(fromDate, DATE);
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) out.add(start.plusDays(i).format(DATE));
        return out;
    }

    private record Scored(String date, int score, String why) {
    }

    private static boolean validDate(String s) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            LocalDate.parse(s, DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
