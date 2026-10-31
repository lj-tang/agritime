package com.cropcalendar.service.crop;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 农业区划与物候常识：把"这块地在哪个区、什么时候有霜、这个作物什么时候能播"
 * 收敛成一份判断，避免每个接口各写一套经纬度分区。
 *
 * 所有结论都是**常年估算**，用于拦截明显不合理的输入，不用于替代实地踏勘。
 */
public final class AgroZone {

    private AgroZone() {
    }

    /** 粗略农业区划。分界是经验值，够用来做"热带作物别种到东北"这种量级的判断。 */
    public static String regionOf(double lat, double lon) {
        if (lat >= 27 && lat <= 37 && lon >= 78 && lon <= 103) return "青藏高原";
        if (lon < 103 && lat >= 34) return "西北";
        if (lat >= 41.5 && lon >= 119) return "东北";
        if (lat >= 34 && lat <= 42 && lon >= 110) return "华北";
        if (lat >= 28 && lat < 34 && lon >= 105) return "长江流域";
        if (lat < 25 && lon >= 105) return "华南";
        if (lat >= 22 && lat < 34 && lon >= 97 && lon < 110) return "西南";
        if (lon < 110) return "西北";
        return "华北";
    }

    public static String soilTypeOf(String region) {
        return switch (region) {
            case "华南" -> "红壤";
            case "长江流域" -> "水稻土";
            case "西南" -> "黄壤";
            case "东北" -> "黑土";
            case "西北" -> "黄绵土";
            case "青藏高原" -> "高山草甸土";
            default -> "褐土";
        };
    }

    /**
     * 终霜日（春季最后一次霜）。用纬度线性近似，华南直接判无霜。
     * 华北 35°N 落在 4 月上旬，与实际的"清明断雪、谷雨断霜"基本对齐。
     */
    public static LocalDate lastSpringFrost(int year, double lat) {
        if (lat < 24) return LocalDate.of(year, 1, 1);
        int doy = (int) Math.round(75 + (lat - 30) * 4.5);
        doy = Math.max(1, Math.min(160, doy));
        return LocalDate.ofYearDay(year, doy);
    }

    /** 初霜日（秋季第一次霜）。 */
    public static LocalDate firstAutumnFrost(int year, double lat) {
        if (lat < 24) return LocalDate.of(year, 12, 31);
        int doy = (int) Math.round(305 - (lat - 30) * 4.0);
        doy = Math.max(240, Math.min(360, doy));
        return LocalDate.ofYearDay(year, doy);
    }

    /** 无霜期天数（常年估算）。 */
    public static int frostFreeDays(double lat) {
        if (lat < 24) return 365;
        LocalDate lf = lastSpringFrost(2001, lat);
        LocalDate ff = firstAutumnFrost(2001, lat);
        return (int) (ff.toEpochDay() - lf.toEpochDay());
    }

    // ------------------------------------------------------------------ 播季

    /** 从 "春播/秋播" 这类描述里解析出允许播种的月份。解析不出返回空集合。 */
    public static Set<Integer> sowMonths(String suitableSeason) {
        Set<Integer> months = new LinkedHashSet<>();
        if (suitableSeason == null || suitableSeason.isBlank()) return months;
        String s = suitableSeason;
        if (s.contains("全年") || s.contains("四季")) {
            for (int i = 1; i <= 12; i++) months.add(i);
            return months;
        }
        if (s.contains("春")) addRange(months, 3, 5);
        if (s.contains("夏")) addRange(months, 6, 8);
        if (s.contains("秋")) addRange(months, 9, 11);
        if (s.contains("冬")) addRange(months, 12, 2);
        return months;
    }

    private static void addRange(Set<Integer> set, int from, int to) {
        if (from <= to) {
            for (int i = from; i <= to; i++) set.add(i);
        } else {
            for (int i = from; i <= 12; i++) set.add(i);
            for (int i = 1; i <= to; i++) set.add(i);
        }
    }

    /** 月份的可读名，用于错误提示。 */
    public static String monthsText(Set<Integer> months) {
        if (months.isEmpty()) return "未限定";
        List<Integer> sorted = new ArrayList<>(months);
        sorted.sort(Integer::compareTo);
        StringBuilder sb = new StringBuilder();
        int start = sorted.get(0), prev = start;
        for (int i = 1; i <= sorted.size(); i++) {
            int cur = i < sorted.size() ? sorted.get(i) : -1;
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            if (sb.length() > 0) sb.append("、");
            sb.append(start == prev ? start + " 月" : start + "–" + prev + " 月");
            start = prev = cur;
        }
        return sb.toString();
    }

    /**
     * 每个大区在作物属性库里可能被写成的别名。
     * 没有这张表就会出现"小麦主产区写的是黄淮海平原，济宁却被判成不适种"这种误伤 ——
     * 属性库是人工整理的，用词不统一，判断方必须把同义词收全。
     */
    private static String[] tokensOf(String region) {
        return switch (region == null ? "" : region) {
            case "华北" -> new String[]{"华北", "黄淮海", "海河", "黄河中下游", "山东", "河南", "河北", "皖北", "苏北", "京津"};
            case "东北" -> new String[]{"东北", "松嫩", "三江平原", "辽宁", "吉林", "黑龙江", "内蒙古东部"};
            case "西北" -> new String[]{"西北", "黄土高原", "河西", "新疆", "陕西", "甘肃", "宁夏", "内蒙古", "青海东部"};
            case "长江流域" -> new String[]{"长江", "江淮", "江南", "华中", "江苏", "安徽", "湖北", "湖南", "江西", "浙江", "四川盆地"};
            case "华南" -> new String[]{"华南", "两广", "海南", "福建", "广西", "广东", "闽", "粤"};
            case "西南" -> new String[]{"西南", "四川", "云南", "贵州", "重庆", "攀西", "滇", "黔"};
            case "青藏高原" -> new String[]{"青藏", "西藏", "青海", "高原"};
            default -> new String[]{region == null ? "" : region};
        };
    }

    /** 作物适宜区域里是否覆盖这个地块所在的区划。 */
    public static boolean regionMatches(String suitableRegion, String region) {
        if (suitableRegion == null || suitableRegion.isBlank()) return true;
        if (suitableRegion.contains("全国") || suitableRegion.contains("广泛") || suitableRegion.contains("各地")) return true;
        if (suitableRegion.contains(region)) return true;
        for (String t : tokensOf(region)) {
            if (!t.isBlank() && suitableRegion.contains(t)) return true;
        }
        return false;
    }

    /** 作物是否喜温（决定要不要防霜冻）。 */
    public static boolean isWarmLoving(String tempRequirement) {
        if (tempRequirement == null) return false;
        return tempRequirement.contains("喜温") || tempRequirement.contains("喜热")
                || tempRequirement.contains("耐热") || tempRequirement.contains("热带");
    }

    public static boolean isColdHardy(String tempRequirement) {
        return tempRequirement != null && (tempRequirement.contains("耐寒") || tempRequirement.contains("越冬"));
    }

    /** 判断某天是否落在常年霜期内（用于"播期会不会赶上霜冻"的判断）。 */
    public static boolean inFrostSeason(LocalDate d, double lat) {
        if (lat < 24) return false;
        LocalDate lf = lastSpringFrost(d.getYear(), lat);
        LocalDate ff = firstAutumnFrost(d.getYear(), lat);
        return d.isBefore(lf) || d.isAfter(ff);
    }

    /** 播种到收获之间是否会经历霜冻期。 */
    public static LocalDate firstFrostWithin(LocalDate sow, int days, double lat) {
        LocalDate end = sow.plusDays(Math.max(1, days));
        for (LocalDate d = sow; !d.isAfter(end); d = d.plusDays(7)) {
            if (inFrostSeason(d, lat)) return d;
        }
        return null;
    }

    /** 供前端显示的季节名。 */
    public static String seasonName(Month m) {
        int v = m.getValue();
        if (v >= 3 && v <= 5) return "春";
        if (v >= 6 && v <= 8) return "夏";
        if (v >= 9 && v <= 11) return "秋";
        return "冬";
    }
}
