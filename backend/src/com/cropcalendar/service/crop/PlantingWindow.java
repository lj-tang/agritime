package com.cropcalendar.service.crop;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 播种/定植窗口（V5.3）：用户只给作物和地点，由这里回答"什么时候种、最晚什么时候、什么时候能收"。
 *
 * 为什么要有这个类：原先"推荐播期"走节气表（春=2~4 月），"播期校验"走作物属性的精确月份，
 * 两套规则各说各话 —— 北京番茄会出现"推荐 2 月 4 日立春播种"（此时还在冻土期）却又能通过校验。
 * 现在统一成一处：窗口、推荐日、最晚日、预计收获都从同一份月份集合推出来。
 *
 * 精度分两档，如实标注，不把推导值说成实测：
 *   · researched —— 作物属性里有手工校核的 sowMonths（目前十几种）
 *   · season-rule —— 其余作物按 suitableSeason 推导，只有月级精度
 *
 * 三条硬约束（都来自实测踩过的坑）：
 *   1. 推荐日绝不早于今天（曾给 11 月 7 日查出"推荐 10 月 31 日播种"）
 *   2. 窗口已过的，直接看下一个窗口，不拿"宽限期"当理由给过去的日期
 *   3. 一年生作物若本窗口收获会撞霜，先试后面的窗口；全都撞霜才如实说"本地难成熟"
 */
public final class PlantingWindow {

    public record Window(
            List<Integer> months,
            String monthsText,
            String startDate,
            String endDate,
            String action,
            String groupLabel,
            String precision,
            String basis,
            String recommendedDate,
            int daysFromToday,
            String latestDate,
            String latestBasis,
            String harvestDate,
            boolean canMatureBeforeFrost,
            boolean perennial,
            int totalDays,
            String note
    ) {
    }

    private record Candidate(LocalDate start, LocalDate end, LocalDate recommended,
                             LocalDate latest, String latestBasis,
                             LocalDate harvest, boolean canMature, String note) {
    }

    private PlantingWindow() {
    }

    /**
     * 该作物允许播种的月份。
     * 优先用手工校核的 sowMonths；没有则按适宜季节推导（春季=3~4 月，不是笼统的 3~5 月，
     * 后者会把"五月播春茬"也放行，而这在多熟制地区已经是夏茬了）。
     */
    public static Set<Integer> monthsFor(CropProperties.Props p) {
        return monthsFor(p, 35.0);
    }

    /**
     * 带纬度的窗口推导。
     *
     * 手工校核过的 sowMonths 直接用，不做纬度修正（那是逐品种校核过的值）。
     * 其余按季节推导的，必须做一层粗调：同样是"春播"，华北的窗口到 5 月，
     * 华南 2 月就能开始 —— 不加这一层，北京会得到"3~4 月播种"，而北京终霜在 4 月底，
     * 整段窗口都落在霜期里，推荐日和校验必然打架。
     */
    public static Set<Integer> monthsFor(CropProperties.Props p, double lat) {
        Set<Integer> out = new LinkedHashSet<>();
        if (p == null) return out;
        if (p.hasSowMonths()) {
            out.addAll(new TreeSet<>(p.sowMonths()));
            return out;
        }
        String s = p.suitableSeason() == null ? "" : p.suitableSeason();
        if (s.contains("全年") || s.contains("四季")) {
            for (int i = 1; i <= 12; i++) out.add(i);
            return out;
        }
        boolean autumnOnly = s.contains("秋") && !s.contains("春") && !s.contains("夏");
        if (s.contains("春")) {
            out.add(3);
            out.add(4);
            if (s.contains("茬")) out.add(4);
            if (lat >= 35) out.add(5);      // 华北北部/东北：春季窗口延到 5 月
            if (lat < 28) out.add(2);       // 华南：2 月即可开始
        }
        if (s.contains("夏")) {
            out.add(6);
            out.add(7);
        }
        if (s.contains("秋")) {
            if (autumnOnly || s.contains("播")) {
                out.add(9);
                out.add(10);
            } else {
                out.add(8);
                out.add(9);
            }
        }
        if (s.contains("冬")) {
            out.add(11);
            out.add(12);
        }
        // 多年生秋植（如草莓）：定植窗口在 9~10 月，不在春季
        if (p.totalDays() >= 300 && "perennial".equals(p.lifespan()) && s.contains("秋")) {
            out.clear();
            out.add(9);
            out.add(10);
        }
        if (out.isEmpty()) {
            // 季节词一个都没命中：给一个保守的春季窗口，调用方会标注这是推导值
            out.add(3);
            out.add(4);
        }
        return out;
    }

    /** 把月份集合折成连续区间，例如 {3,4,8,9} → [3,4] [8,9] */
    public static List<int[]> ranges(Set<Integer> months) {
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(months));
        List<int[]> out = new ArrayList<>();
        int start = -1, prev = -1;
        for (int m : sorted) {
            if (start < 0) {
                start = prev = m;
                continue;
            }
            if (m == prev + 1) {
                prev = m;
                continue;
            }
            out.add(new int[]{start, prev});
            start = prev = m;
        }
        if (start > 0) out.add(new int[]{start, prev});
        return out;
    }

    /**
     * 推算窗口。作物没有属性时返回 null（调用方降级到节气建议）。
     *
     * @param p     作物属性
     * @param lat   纬度
     * @param lon   经度（积温推算要用本地气象归档）
     * @param today 今天
     */
    public static Window of(CropProperties.Props p, double lat, double lon, LocalDate today) {
        if (p == null || today == null) return null;
        Set<Integer> months = monthsFor(p, lat);
        if (months.isEmpty()) return null;

        List<LocalDate[]> wins = windows(months, today);
        if (wins.isEmpty()) return null;

        boolean perennial = "perennial".equals(p.lifespan());
        boolean autumnSown = isAutumnSown(p, months);
        LocalDate gddDate = gddDate(p, lat, lon, today);

        Candidate first = null;
        Candidate chosen = null;
        for (LocalDate[] w : wins) {
            Candidate c = evaluate(p, w[0], w[1], lat, today, perennial, autumnSown, gddDate);
            if (first == null) first = c;
            if (c.canMature()) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) chosen = first;

        String note = chosen.note();
        if (!chosen.canMature()) {
            note = join(note, "本地无霜期约 " + AgroZone.frostFreeDays(lat) + " 天，短于该作物 "
                    + Math.max(1, p.totalDays()) + " 天的生育期，露地难以正常成熟，建议改早熟品种或保护地栽培");
        }

        String precision = p.hasSowMonths() ? "researched" : "season-rule";
        String basis = p.hasSowMonths()
                ? "作物属性库中已校核的播期（" + monthsText(months) + "）"
                : "按适宜季节「" + safe(p.suitableSeason()) + "」并按本地纬度粗调（" + monthsText(months)
                        + "），未逐品种校核，仅作参考";

        CropTemplates.Group group = CropTemplates.groupOf(p);
        return new Window(
                new ArrayList<>(new TreeSet<>(months)),
                monthsText(months),
                chosen.start().toString(),
                chosen.end().toString(),
                group.action(),
                group.label(),
                precision,
                basis,
                chosen.recommended().toString(),
                (int) (chosen.recommended().toEpochDay() - today.toEpochDay()),
                chosen.latest().toString(),
                chosen.latestBasis(),
                chosen.harvest() == null ? null : chosen.harvest().toString(),
                chosen.canMature(),
                perennial,
                Math.max(1, p.totalDays()),
                note);
    }

    /** 从今天起、未来 14 个月内的窗口，按时间排序；已结束的窗口不入列。 */
    private static List<LocalDate[]> windows(Set<Integer> months, LocalDate today) {
        List<LocalDate[]> out = new ArrayList<>();
        List<int[]> rs = ranges(months);
        for (int y = today.getYear(); y <= today.getYear() + 1; y++) {
            for (int[] r : rs) {
                LocalDate s = LocalDate.of(y, r[0], 1);
                LocalDate e = YearMonth.of(y, r[1]).atEndOfMonth();
                if (e.isBefore(today)) continue;   // 窗口已过：看下一个，不给过去的日期
                out.add(new LocalDate[]{s, e});
            }
        }
        out.sort((a, b) -> a[0].compareTo(b[0]));
        if (out.size() > 4) return out.subList(0, 4);
        return out;
    }

    private static Candidate evaluate(CropProperties.Props p, LocalDate start, LocalDate end,
                                      double lat, LocalDate today, boolean perennial,
                                      boolean autumnSown, LocalDate gddDate) {
        LocalDate floor = today.isAfter(start) ? today : start;
        if (floor.isAfter(end)) floor = end;

        LocalDate recommended = floor;
        String note = null;
        if (gddDate != null) {
            if (gddDate.isAfter(end)) {
                recommended = end;
                note = "积温建议日 " + gddDate + " 已超出本窗口，按窗口末 " + end + " 安排";
            } else if (!gddDate.isBefore(floor)) {
                recommended = gddDate;
            }
        }

        int total = Math.max(1, p.totalDays());
        // 终霜修正：不耐寒的作物，推荐日不能落在终霜前（华北 3 月上旬还是冻土期）。
        // 只在终霜日本身仍在窗口内时才前移 —— 像番茄 1~3 月是保护地育苗，
        // 终霜（4 月中）已在窗口之外，此时保留原推荐日，由播期校验按"育苗"口径放行。
        if (!AgroZone.isColdHardy(p.tempRequirement())) {
            LocalDate lf = AgroZone.lastSpringFrost(recommended.getYear(), lat);
            if (recommended.isBefore(lf) && !lf.isAfter(end)) {
                recommended = lf;
                note = join(note, "已避开终霜，按终霜后 " + lf + " 起算");
            }
        }
        if (perennial) {
            int years = Math.max(1, (int) Math.round(total / 365.0));
            return new Candidate(start, end, recommended, end,
                    "定植窗口末（" + monthsText(monthsOf(start, end)) + "）",
                    null, true,
                    join(note, "多年生，定植后约 " + years + " 年进入盛产期"));
        }
        if (autumnSown) {
            return new Candidate(start, end, recommended, end,
                    "越冬作物以窗口末为限（" + monthsText(monthsOf(start, end)) + "）",
                    recommended.plusDays(total), true,
                    join(note, "越冬作物，跨年收获，播期以越冬前长到壮苗为准"));
        }

        LocalDate frost = AgroZone.firstAutumnFrost(recommended.getYear(), lat);
        LocalDate latestSafe = frost.minusDays(total);
        if (recommended.isAfter(latestSafe)) {
            if (!latestSafe.isBefore(floor)) {
                recommended = latestSafe;
                note = join(note, "已按" + "霜前成熟回退到 " + latestSafe);
            }
        }
        boolean canMature = !recommended.plusDays(total).isAfter(frost);

        LocalDate latest;
        String latestBasis;
        if (latestSafe.isAfter(end) || latestSafe.equals(end)) {
            latest = end;
            latestBasis = "适宜播期窗口末（" + monthsText(monthsOf(start, end)) + "）";
        } else if (!latestSafe.isBefore(floor)) {
            latest = latestSafe;
            latestBasis = "留足 " + total + " 天生育期，赶在初霜 " + frost + " 前成熟";
        } else {
            latest = end;
            latestBasis = "适宜播期窗口末（" + monthsText(monthsOf(start, end)) + "）";
        }
        return new Candidate(start, end, recommended, latest, latestBasis,
                recommended.plusDays(total), canMature, note);
    }

    private static Set<Integer> monthsOf(LocalDate start, LocalDate end) {
        Set<Integer> s = new LinkedHashSet<>();
        LocalDate d = start.withDayOfMonth(1);
        while (!d.isAfter(end)) {
            s.add(d.getMonthValue());
            d = d.plusMonths(1);
        }
        return s;
    }

    /** 积温建议日：取不到（气象归档缺失）就返回 null，不影响窗口本身。 */
    private static LocalDate gddDate(CropProperties.Props p, double lat, double lon, LocalDate today) {
        try {
            GddAdvisor.Suggestion g = GddAdvisor.earliest(lat, lon, today, p);
            if (g != null && g.available() && g.sowDate() != null) {
                return LocalDate.parse(g.sowDate());
            }
        } catch (Exception e) {
            // 气象归档不可用：只少一层修正，不阻断
        }
        return null;
    }

    /**
     * 是否越冬作物：窗口全在秋冬、且属性里强调耐寒/越冬、或生育期长到必须跨年。
     * 只看"有没有秋"不够 —— 番茄春秋两茬，秋天播也是当年收，不是越冬。
     */
    private static boolean isAutumnSown(CropProperties.Props p, Set<Integer> months) {
        boolean allAutumn = true;
        for (int m : months) {
            if (m >= 2 && m <= 8) {
                allAutumn = false;
                break;
            }
        }
        if (!allAutumn) return false;
        String t = p.tempRequirement() == null ? "" : p.tempRequirement();
        return AgroZone.isColdHardy(t) || p.totalDays() >= 200;
    }

    public static String monthsText(Set<Integer> months) {
        return AgroZone.monthsText(months);
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "未标注" : s;
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + "；" + b;
    }
}
