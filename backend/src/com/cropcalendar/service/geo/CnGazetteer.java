package com.cropcalendar.service.geo;

import com.cropcalendar.util.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 离线地名库：经纬度 → 「省 + 市 + 区县」，不依赖任何外部服务。
 *
 * 为什么需要它？
 *   地图标点后要立刻给用户一个可信的地名。上游（腾讯/高德要 key，Photon 免 key
 *   但从国内访问要 5~7 秒）都靠不住时，界面只能显示「该位置暂无法识别地名」——
 *   这就是用户看到的那个提示。地名是静态信息，没必要每次都问外部服务。
 *
 * 数据（data/geo/places-cn.json，约 100 KB）由 tools/build-gazetteer 生成：
 *   · GeoNames（CC BY 4.0）提供中文地名与坐标
 *   · china-area-data 提供省/市/区县三级中文名，用来把散点组织成行政层级
 *
 * 精度取舍：地级市一定给对；区县只在「25 公里内且归属同一个市」时才写进结果，
 *   因为 GeoNames 里同名村庄很多，放宽条件会把「广州天河」标成「郴州嘉禾」。
 */
public final class CnGazetteer {

    private CnGazetteer() {
    }

    /** 地级市 / 直辖市：name 是市名，province 是省名（直辖市两者相同） */
    private record City(String name, String province, double lat, double lon) {
    }

    /** 区县：city 是所属地级市在 cities 里的下标 */
    private record County(String name, int city, double lat, double lon) {
    }

    /** 和服务内其他数据文件一样，兼容从 backend/、仓库根、线上绝对路径三种工作目录启动 */
    private static final String[] PATHS = {
            "../data/geo/places-cn.json",
            "data/geo/places-cn.json",
            "/agritime/data/geo/places-cn.json"
    };

    /** 区县名只在标点附近才可信，太远就只用市名，避免张冠李戴 */
    private static final double COUNTY_MAX_KM = 25;
    /**
     * 离市中心 25 公里以内算城区，只报「省 + 市」。
     * 城区里一个区只有十几公里宽，而 GeoNames 对市辖区的覆盖并不全
     * （广州 11 个区只收录了 5 个），硬套区县会把「天河」标成「番禺」。
     * 农村点反而要靠区县才能说清位置，所以只在离市中心较远时才细化。
     */
    private static final double CITY_CORE_KM = 25;
    /** 离市中心超过这个距离，说明标点在人烟稀少处，措辞加上「附近」 */
    private static final double CITY_NEAR_KM = 60;
    /** 离所有市中心都超过这个距离，判定为境外或远海，交给上游服务 */
    private static final double OUT_OF_COVERAGE_KM = 400;

    private static volatile boolean loaded;
    private static volatile City[] cities = new City[0];
    private static volatile County[] counties = new County[0];

    /**
     * 查最近的地名。库没加载成功或标点不在国内时返回 null，由调用方降级。
     */
    public static GeoResolver.Place resolve(double lat, double lon) {
        if (!ensureLoaded()) return null;

        City[] cs = cities;
        int ci = -1;
        double cd = Double.MAX_VALUE;
        for (int i = 0; i < cs.length; i++) {
            double d = distKm(lat, lon, cs[i].lat(), cs[i].lon());
            if (d < cd) {
                cd = d;
                ci = i;
            }
        }
        if (ci < 0 || cd > OUT_OF_COVERAGE_KM) return null;

        City city = cs[ci];
        County[] ks = counties;
        County near = null;
        double kd = Double.MAX_VALUE;
        for (County k : ks) {
            if (k.city() != ci) continue;
            double d = distKm(lat, lon, k.lat(), k.lon());
            if (d < kd) {
                kd = d;
                near = k;
            }
        }

        String province = city.province();
        boolean rural = cd >= CITY_CORE_KM;
        // 城区里只有当区县驻地就贴在脚下（8 公里内）才敢报，否则宁可只报市；
        // 农村视野大，25 公里内的区县基本就是它所属的那个
        boolean useCounty = near != null
                && (kd <= 8 || (rural && kd <= COUNTY_MAX_KM));
        String district = useCounty ? near.name() : "";
        // 直辖市的省名就是市名，拼一次即可，避免「北京市北京市」
        StringBuilder sb = new StringBuilder(province);
        if (!province.equals(city.name())) sb.append(city.name());
        if (!district.isEmpty() && !district.equals(city.name())) sb.append(district);
        // 只报了市名又离得很远，才用「附近」说明颗粒度，避免看起来像精确地址
        if (district.isEmpty() && cd > CITY_NEAR_KM) sb.append(" 附近");

        String name = district.isEmpty() ? city.name() : district;
        return new GeoResolver.Place(
                sb.toString(), name, "",
                district, city.name(), province,
                district.isEmpty() ? "city" : "district", "local");
    }

    /**
     * 关键字搜地点。上游（腾讯/高德）没配 key、Photon 又慢到超时时，
     * 至少能按省/市/区县名字搜到——用户输的多半就是自己所在的县市。
     */
    public static java.util.List<GeoResolver.Suggestion> search(String keyword, int limit) {
        if (!ensureLoaded()) return java.util.List.of();
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() < 2) return java.util.List.of();
        String qc = core(q);

        java.util.List<GeoResolver.Suggestion> exact = new java.util.ArrayList<>();
        java.util.List<GeoResolver.Suggestion> loose = new java.util.ArrayList<>();

        City[] cs = cities;
        for (City c : cs) {
            String cn = core(c.name());
            if (!cn.equals(qc) && !c.name().contains(q)) continue;
            boolean hit = cn.equals(qc) || qc.length() >= 2 && cn.startsWith(qc);
            (hit ? exact : loose).add(new GeoResolver.Suggestion(
                    c.name(), c.province() + c.name(), c.lat(), c.lon(), "local"));
        }
        for (County k : counties) {
            String cn = core(k.name());
            if (!cn.equals(qc) && !k.name().contains(q)) continue;
            City c = cs[k.city()];
            boolean hit = cn.equals(qc) || qc.length() >= 2 && cn.startsWith(qc);
            (hit ? exact : loose).add(new GeoResolver.Suggestion(
                    k.name(), c.province() + c.name() + k.name(), k.lat(), k.lon(), "local"));
        }

        java.util.List<GeoResolver.Suggestion> out = new java.util.ArrayList<>(limit);
        out.addAll(exact);
        out.addAll(loose);
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /** 去掉行政区划后缀，用于「武汉市」和「武汉」互相匹配 */
    private static String core(String name) {
        for (String s : new String[]{"特别行政区", "自治州", "自治县", "自治旗", "地区", "省", "市", "县", "区", "盟", "旗"}) {
            if (name.endsWith(s) && name.length() > s.length() + 1) {
                return name.substring(0, name.length() - s.length());
            }
        }
        return name;
    }

    private static boolean ensureLoaded() {
        if (loaded) return !(cities.length == 0);
        synchronized (CnGazetteer.class) {
            if (loaded) return !(cities.length == 0);
            load();
            loaded = true;
            return !(cities.length == 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        Path file = null;
        for (String p : PATHS) {
            Path cand = Paths.get(p);
            if (Files.isReadable(cand)) {
                file = cand;
                break;
            }
        }
        if (file == null) return;

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> rm)) return;
            Map<String, Object> m = (Map<String, Object>) rm;

            List<Object> prov = (List<Object>) m.get("provinces");
            List<Object> cityList = (List<Object>) m.get("cities");
            List<Object> countyList = (List<Object>) m.get("counties");
            if (prov == null || cityList == null || countyList == null) return;

            City[] cs = new City[cityList.size()];
            for (int i = 0; i < cityList.size(); i++) {
                List<Object> row = (List<Object>) cityList.get(i);
                int pi = (int) Math.round((Double) row.get(1));
                cs[i] = new City((String) row.get(0), (String) prov.get(pi),
                        (Double) row.get(2), (Double) row.get(3));
            }

            County[] ks = new County[countyList.size()];
            for (int i = 0; i < countyList.size(); i++) {
                List<Object> row = (List<Object>) countyList.get(i);
                ks[i] = new County((String) row.get(0),
                        (int) Math.round((Double) row.get(1)),
                        (Double) row.get(2), (Double) row.get(3));
            }

            counties = ks;
            cities = cs;
        } catch (Exception e) {
            // 数据文件损坏不影响主流程：cities 保持空数组，调用方会降级到上游
            cities = new City[0];
            counties = new County[0];
        }
    }

    /** 球面距离，公里 */
    private static double distKm(double a1, double o1, double a2, double o2) {
        double r = 6371.0;
        double p1 = Math.toRadians(a1);
        double p2 = Math.toRadians(a2);
        double dp = p2 - p1;
        double dl = Math.toRadians(o2 - o1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
