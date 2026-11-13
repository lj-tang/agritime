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
            String region,
            String basis,
            String recommendedDate,
            int daysFromToday,
            String latestDate,
            String latestBasis,
            String harvestDate,
            boolean canMatureBeforeFrost,
            boolean perennial,
            int totalDays,
            /** 本地露地是否能种成：false 时 recommendedDate/harvestDate/latestDate 全部为 null。 */
            boolean suitable,
            /** 不宜种植时给出的参考日（窗口内最早可行日），仅供"仍要看参考日历"使用。 */
            String fallbackDate,
            String note
    ) {
    }

    private record Candidate(LocalDate start, LocalDate end, LocalDate recommended,
                             LocalDate latest, String latestBasis,
                             LocalDate harvest, boolean canMature, String note) {
    }

    private PlantingWindow() {
    }

    /** 喜冷凉/耐寒作物能耐轻霜，收获窗口比初霜日宽这么多天。 */
    private static final int FROST_GRACE_DAYS = 15;

    /**
     * 南方窗口的分界纬度。低于它就用 sowsMonthsSouth（华南/长江以南）。
     * 26.5°N 大致是福州—桂林—昆明一线：广州 23.1、南宁 22.8、昆明 25.0 走南方窗口，
     * 长沙 28.2、武汉 30.6 仍用华中窗口。
     */
    private static final double SOUTH_LAT = 26.5;

    /** 窗口地域：华北基准 / 华南 / 西南。 */
    public enum Region {
        NORTH("north"), SOUTH("south"), SOUTHWEST("southwest");

        private final String json;

        Region(String json) {
            this.json = json;
        }

        public String json() {
            return json;
        }
    }

    /**
     * 该地块该用哪套窗口。
     * 西南（川渝黔滇）有专门窗口就用，没有就退回华北基准并说明；
     * 华南看纬度（26.5°N 以南）。
     */
    public static Region regionFor(CropProperties.Props p, double lat, double lon) {
        if (p == null) return Region.NORTH;
        if ("西南".equals(AgroZone.regionOf(lat, lon))) {
            // 西南有专门窗口就用；没有则退回华中基准 —— 不能因为昆明纬度低就套华南的热带窗口
            return p.hasSouthwest() ? Region.SOUTHWEST : Region.NORTH;
        }
        if (p.hasSouthMonths() && lat < SOUTH_LAT) {
            return Region.SOUTH;
        }
        return Region.NORTH;
    }

    /** 兼容旧调用：是否走华南窗口。 */
    public static boolean useSouth(CropProperties.Props p, double lat) {
        return p != null && p.hasSouthMonths() && lat < SOUTH_LAT;
    }

    /** 是否喜冷凉（能耐轻霜）。 */
    static boolean coolLoving(String tempRequirement) {
        if (tempRequirement == null) return false;
        return tempRequirement.contains("冷凉") || tempRequirement.contains("耐寒");
    }

    /** 该作物收获期可比初霜日宽限几天（0 = 一天都不能让）。 */
    public static int frostGraceDays(String tempRequirement) {
        if (tempRequirement == null || tempRequirement.isBlank()) return FROST_GRACE_DAYS;
        // 喜温/热带作物一天霜都不能碰；冷凉、耐寒，以及属性表里没有温度信息的目录作物，
        // 都按"能耐轻霜"给余量 —— 绝大多数目录作物是温带蔬菜，一刀切按喜温算会大面积误判。
        if (tempRequirement.contains("喜温") || tempRequirement.contains("喜热")
                || tempRequirement.contains("耐热") || tempRequirement.contains("热带")) {
            return 0;
        }
        return FROST_GRACE_DAYS;
    }

    /**
     * 收获期略晚于常年初霜的容差：覆盖、熏烟、选向阳地都能救回来，
     * 属于"要留神的农事风险"，不是"种不熟"。超过这个天数才判不宜露地种植。
     */
    private static final int MATURITY_TOLERANCE_DAYS = 20;

    /**
     * 该作物允许播种的月份。
     * 优先用手工校核的 sowMonths；没有则按适宜季节推导（春季=3~4 月，不是笼统的 3~5 月，
     * 后者会把"五月播春茬"也放行，而这在多熟制地区已经是夏茬了）。
     */
    public static Set<Integer> monthsFor(CropProperties.Props p) {
        return monthsFor(p, 35.0, 116.31);
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
        return monthsFor(p, lat, 116.31);
    }

    /** 带经纬度的窗口推导：西南判定要用经度，只给纬度会退化成华北基准。 */
    public static Set<Integer> monthsFor(CropProperties.Props p, double lat, double lon) {
        Set<Integer> out = new LinkedHashSet<>();
        if (p == null) return out;
        if (p.hasSowMonths()) {
            // 西南优先西南窗口，其次华南窗口，最后华北基准
            Region region = regionFor(p, lat, lon);
            if (region == Region.SOUTHWEST) {
                out.addAll(new TreeSet<>(p.sowMonthsSouthwest()));
            } else if (region == Region.SOUTH) {
                out.addAll(new TreeSet<>(p.sowMonthsSouth()));
            } else {
                out.addAll(new TreeSet<>(p.sowMonths()));
            }
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
        Set<Integer> months = monthsFor(p, lat, lon);
        if (months.isEmpty()) return null;
        Region region = regionFor(p, lat, lon);
        boolean south = region == Region.SOUTH;
        // 各区域一季生育期不同（华南冬麦约 130 天、西南约 200 天、华北 245 天）
        int totalDays = Math.max(1, p.daysFor(region.json()));

        List<LocalDate[]> wins = windows(months, today);
        if (wins.isEmpty()) return null;

        boolean perennial = "perennial".equals(p.lifespan());
        boolean autumnSown = isAutumnSown(p, months, south);
        // 多年生定植看的是休眠期与地温，不看当季积温，免得拿到下一季的日期
        LocalDate gddDate = perennial ? null : gddDate(p, lat, lon, today);

        Candidate first = null;
        Candidate chosen = null;
        for (LocalDate[] w : wins) {
            Candidate c = evaluate(p, w[0], w[1], lat, today, perennial, autumnSown, gddDate, totalDays);
            if (first == null) first = c;
            if (c.canMature()) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) chosen = first;

        // 野生/共生菌（松茸靠菌根、鸡枞靠白蚁巢）没有"播期"这回事：
        // 给日期等于骗人，直接说清"不可人工按播期栽培"。
        boolean wild = "wild".equals(p.precision());
        // 全部窗口都赶不上霜前成熟：不要再给"建议日"。给了就等于承诺，用户照着种却收不了。
        // 这里只保留一个参考日（窗口内最早可行日）+ 结论，前端据此显示"本地不宜露地种植"。
        boolean suitable = chosen.canMature() && !wild;
        String note = chosen.note();
        if (wild) {
            note = join(p.sowNote().isBlank() ? "野生/共生菌，无法按播期人工栽培" : p.sowNote(),
                    "本条目仅作采收季参考，不提供播种建议");
        } else if (!suitable) {
            String reason = "本地无霜期约 " + AgroZone.frostFreeDays(lat) + " 天，短于该作物 "
                    + totalDays + " 天的生育期，露地种不熟；建议改保护地栽培、换早熟品种，或改种其他作物";
            // 结论放最前：用户第一眼要看到"为什么不能种"，日期细节退到后面
            note = join(reason, note);
        }

        String precision = p.hasSowMonths() ? p.precision() : "season-rule";
        String basis;
        if (p.hasSowMonths()) {
            String head = switch (region) {
                case SOUTHWEST -> "西南（川渝黔滇）播期（";
                case SOUTH -> "南方（华南）播期（";
                case NORTH -> switch (p.precision()) {
                    case "derived" -> "按近缘作物推定的播期（";
                    case "inherited" -> "按母本品种继承的已校核播期（";
                    default -> "作物属性库中已校核的播期（";
                };
            };
            basis = head + monthsText(months) + "）"
                    + (p.sowNote().isBlank() ? "" : "；" + p.sowNote());
        } else {
            basis = "按适宜季节「" + safe(p.suitableSeason()) + "」并按本地纬度粗调（" + monthsText(months)
                    + "），未逐品种校核，仅作参考";
        }
        // 西南没有专门窗口时，明确告知是按华中基准给的
        if (region == Region.NORTH && "西南".equals(AgroZone.regionOf(lat, lon))) {
            basis += "；西南地区暂无专门窗口，按华中基准给出，请结合海拔调整";
        }

        CropTemplates.Group group = CropTemplates.groupOf(p);
        return new Window(
                new ArrayList<>(new TreeSet<>(months)),
                monthsText(months),
                chosen.start().toString(),
                chosen.end().toString(),
                group.action(),
                group.label(),
                precision,
                region.json(),
                basis,
                suitable ? chosen.recommended().toString() : null,
                suitable ? (int) (chosen.recommended().toEpochDay() - today.toEpochDay()) : -1,
                suitable ? chosen.latest().toString() : null,
                suitable ? chosen.latestBasis() : null,
                suitable ? (chosen.harvest() == null ? null : chosen.harvest().toString()) : null,
                chosen.canMature(),
                perennial,
                totalDays,
                suitable,
                chosen.recommended().toString(),
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
                                      boolean autumnSown, LocalDate gddDate, int total) {
        LocalDate floor = today.isAfter(start) ? today : start;
        if (floor.isAfter(end)) floor = end;

        LocalDate recommended = floor;
        String note = null;
        boolean gddUsed = false;
        if (gddDate != null) {
            long gap = gddDate.toEpochDay() - end.toEpochDay();
            if (gap <= 0 && !gddDate.isBefore(floor)) {
                recommended = gddDate;
                gddUsed = true;
            }
            // 积温日明显晚于窗口（gap>0）就忽略：秋播"赶早不赶晚"，
            // 按积温硬拖到窗口末，往往已经来不及在霜前成熟（大白菜曾因此被判"熟不了"）。
        }
        // 秋播窗口（7 月及以后）没有可用积温日时，取窗口起点后 7 天：
        // 立秋前后正是华北秋菜的下种节点，比"窗口第一天"更贴合实际。
        if (!gddUsed && start.getMonthValue() >= 7) {
            LocalDate d = start.plusDays(7);
            if (d.isAfter(end)) d = end;
            if (d.isBefore(floor)) d = floor;
            recommended = d;
        }

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
            // totalDays 对多年生只是"寿命占位值"（果树 3650、宿根 1825），
            // 拿它换算"几年进入盛产期"会把黄花菜说成 5 年、薰衣草说成 5 年，都是假的。
            // 木本才谈得上盛产期，草本宿根只说实话：连续采收多年。
            String perNote = total >= 3650
                    ? "多年生木本，定植后约 3–5 年进入盛产期"
                    : "多年生，定植成活后按年管理，可连续采收多年";
            return new Candidate(start, end, recommended, end,
                    "定植窗口末（" + monthsText(monthsOf(start, end)) + "）",
                    null, true,
                    join(note, perNote));
        }
        if (autumnSown) {
            return new Candidate(start, end, recommended, end,
                    "越冬作物以窗口末为限（" + monthsText(monthsOf(start, end)) + "）",
                    recommended.plusDays(total), true,
                    join(note, "越冬作物，跨年收获，播期以越冬前长到壮苗为准"));
        }

        LocalDate frost = AgroZone.firstAutumnFrost(recommended.getYear(), lat);
        // 喜冷凉作物能耐轻霜，收获窗口比初霜日宽 15 天；喜温作物一天都不能让
        int grace = frostGraceDays(p.tempRequirement());
        LocalDate safeFrost = frost.plusDays(grace);
        LocalDate latestSafe = safeFrost.minusDays(total);
        if (recommended.isAfter(latestSafe)) {
            if (!latestSafe.isBefore(floor)) {
                recommended = latestSafe;
                note = join(note, "已按" + "霜前成熟回退到 " + latestSafe);
            }
        }
        boolean canMature = !recommended.plusDays(total).isAfter(safeFrost);
        LocalDate harvestDate = recommended.plusDays(total);
        // 三种情况不该判"种不熟"：
        //   1) 全年无霜（华南热带）：年份边界没有意义；
        //   2) 秋植/越冬作物：跨年收获本来就是正常农事（白芍、郁金香、越冬叶菜）；
        //   3) 收获只比初霜晚一点点：属于要留神的农事风险，不是种不熟。
        boolean noFrost = AgroZone.frostFreeDays(lat) >= 350;
        boolean warmLoving = AgroZone.isWarmLoving(p.tempRequirement())
                && !AgroZone.isColdHardy(p.tempRequirement());
        boolean overwinter = harvestDate.getYear() > recommended.getYear() && !warmLoving;
        if (noFrost) {
            canMature = true;
        } else if (overwinter) {
            canMature = true;
            note = join(note, "跨年收获：越冬后约 " + harvestDate + " 成熟，冬季需保证存活");
        } else if (!canMature) {
            canMature = !harvestDate.isAfter(safeFrost.plusDays(MATURITY_TOLERANCE_DAYS));
            if (canMature) {
                note = join(note, "收获期贴近初霜（约 " + harvestDate + "），需关注天气，必要时覆盖保温");
            }
        }

        LocalDate latest;
        String latestBasis;
        if (latestSafe.isAfter(end) || latestSafe.equals(end)) {
            latest = end;
            latestBasis = "适宜播期窗口末（" + monthsText(monthsOf(start, end)) + "）";
        } else if (!latestSafe.isBefore(floor)) {
            latest = latestSafe;
            latestBasis = "留足 " + total + " 天生育期，"
                    + (grace > 0 ? "可在初霜后 " + grace + " 天内（耐轻霜）成熟" : "赶在初霜 " + frost + " 前成熟");
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
    private static boolean isAutumnSown(CropProperties.Props p, Set<Integer> months, boolean south) {
        boolean allAutumn = true;
        for (int m : months) {
            // 8 月算秋播起点：油菜、洋葱等秋播越冬作物就是 8~9 月下种
            if (m >= 3 && m <= 7) {
                allAutumn = false;
                break;
            }
        }
        if (!allAutumn) return false;
        String t = p.tempRequirement() == null ? "" : p.tempRequirement();
        return AgroZone.isColdHardy(t) || p.daysFor(south) >= 200;
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
