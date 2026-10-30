package com.cropcalendar.service.geo;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.util.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 逆地理编码：经纬度 → 中文地名（目标精度到小区/村）。
 *
 * 为什么放在后端？
 *   1. 前端直连 nominatim.openstreetmap.org 在国内基本不可达（实测 10s 超时），
 *      所以用户此前只能看到一串经纬度，看不到地名；
 *   2. 密钥不能落到浏览器里；
 *   3. 经纬度是高精度值（6 位小数 ≈ 0.1 米），由后端统一收口、缓存、参与后续计算。
 *
 * 服务商按可用性排序，谁配了 key 用谁：
 *   · 腾讯位置服务（formatted_addresses.recommend 直接就是小区级地址）
 *   · 高德 Web 服务（neighborhood / aois / pois 能拿到小区与楼宇）
 *   · 都没有 → 返回 level=unknown，由前端提示「该位置暂无法识别」
 *
 * 零依赖：JDK 自带 HttpClient + 项目内 JsonParser。
 */
public final class GeoResolver {

    /** 解析结果。lat/lon 不在这里返回给前端展示，只做后端计算用。 */
    public record Place(String displayName, String name, String street,
                        String district, String city, String province,
                        String level, String source) {

        static Place unknown() {
            return new Place("", "", "", "", "", "", "unknown", "none");
        }

        boolean known() {
            return !"unknown".equals(level);
        }
    }

    /** 搜索联想结果。前端点选后按 lat/lon 落标点，title 只用于展示。 */
    public record Suggestion(String title, String address, double lat, double lon, String source) {
    }

    private record Entry(Place place, long expireAtMs) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    /** 地名基本不变，缓存一天；坐标按 6 位小数（约 0.1 米）做 key，不损失精度 */
    private static final int TTL_SECONDS = 86_400;
    /** 失败结果也缓存，避免同一个点被反复重试拖慢交互 */
    private static final int NEGATIVE_TTL_SECONDS = 600;

    /** 单次上游超时。超过就降级，不阻塞用户确认标点。 */
    private static final int UPSTREAM_TIMEOUT_MS = 2_500;

    public static Place resolve(double lat, double lon) {
        String key = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon);
        Entry hit = CACHE.get(key);
        if (hit != null && hit.expireAtMs() > System.currentTimeMillis()) {
            return hit.place();
        }

        Place place = null;
        String qqKey = AppConfig.geoQqKey();
        if (!qqKey.isBlank()) {
            place = tencent(lat, lon, qqKey);
        }
        if ((place == null || !place.known()) && !AppConfig.geoAmapKey().isBlank()) {
            Place amap = amap(lat, lon, AppConfig.geoAmapKey());
            if (amap != null && amap.known()) place = amap;
        }
        // 没有 key 也必须有地名：Photon 基于 OpenStreetMap，免注册、免 key，
        // 实测国内可直连（Nominatim 不行，Photon 可以）。
        if (place == null || !place.known()) {
            Place photon = photon(lat, lon);
            if (photon != null && photon.known()) place = photon;
        }
        if (place == null) place = Place.unknown();

        int ttl = place.known() ? TTL_SECONDS : NEGATIVE_TTL_SECONDS;
        CACHE.put(key, new Entry(place, System.currentTimeMillis() + ttl * 1000L));
        return place;
    }

    /** 清空缓存（调试用） */
    public static int cacheSize() {
        return CACHE.size();
    }

    // ---------------- 关键字搜索 ----------------

    /**
     * 地点搜索。同样走国内服务商 —— 前端原来直连 Nominatim，
     * 在国内就是「搜索服务暂不可用」，等于搜索框是坏的。
     */
    public static java.util.List<Suggestion> search(String keyword, double lat, double lon, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return java.util.List.of();
        int n = Math.max(1, Math.min(limit, 10));

        String qqKey = AppConfig.geoQqKey();
        if (!qqKey.isBlank()) {
            java.util.List<Suggestion> r = dedupe(tencentSearch(kw, lat, lon, n, qqKey));
            if (!r.isEmpty()) return r;
        }
        String amapKey = AppConfig.geoAmapKey();
        if (!amapKey.isBlank()) {
            java.util.List<Suggestion> r = dedupe(amapSearch(kw, n, amapKey));
            if (!r.isEmpty()) return r;
        }
        return dedupe(photonSearch(kw, lat, lon, n));
    }

    /** 同名同址的结果只留第一条：OSM 里一个「望京」能挂着五六个地铁站，不合并会刷屏 */
    private static java.util.List<Suggestion> dedupe(java.util.List<Suggestion> raw) {
        java.util.Map<String, Suggestion> seen = new java.util.LinkedHashMap<>();
        for (Suggestion s : raw) {
            String key = s.title() + "|" + s.address();
            seen.putIfAbsent(key, s);
        }
        return new java.util.ArrayList<>(seen.values());
    }

    private static java.util.List<Suggestion> tencentSearch(String kw, double lat, double lon, int n, String key) {
        String url = "https://apis.map.qq.com/ws/place/v1/suggestion/?keyword=" + enc(kw)
                + "&location=" + String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
                + "&page_size=" + n + "&key=" + enc(key);
        Map<String, Object> root = getJson(url);
        java.util.List<Suggestion> out = new java.util.ArrayList<>();
        if (root == null || !codeIs(root, "status", "0")) return out;
        List<?> data = list(root, "data");
        if (data == null) return out;
        for (Object o : data) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> sm = cast(m);
            Map<String, Object> loc = map(sm, "location");
            if (loc == null) continue;
            double la = dbl(loc, "lat");
            double lo = dbl(loc, "lng");
            if (!valid(la, lo)) continue;
            out.add(new Suggestion(str(sm, "title"), str(sm, "address"), la, lo, "tencent"));
        }
        return out;
    }

    private static java.util.List<Suggestion> amapSearch(String kw, int n, String key) {
        String url = "https://restapi.amap.com/v3/place/text?keywords=" + enc(kw)
                + "&key=" + enc(key) + "&offset=" + n + "&page=1&extensions=base";
        Map<String, Object> root = getJson(url);
        java.util.List<Suggestion> out = new java.util.ArrayList<>();
        if (root == null || !codeIs(root, "status", "1")) return out;
        List<?> pois = list(root, "pois");
        if (pois == null) return out;
        for (Object o : pois) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> pm = cast(m);
            String[] parts = str(pm, "location").split(",");   // 高德是「经度,纬度」
            if (parts.length != 2) continue;
            try {
                double lo = Double.parseDouble(parts[0].trim());
                double la = Double.parseDouble(parts[1].trim());
                if (!valid(la, lo)) continue;
                String addr = str(pm, "pname") + str(pm, "cityname") + str(pm, "adname") + str(pm, "address");
                out.add(new Suggestion(str(pm, "name"), addr, la, lo, "amap"));
            } catch (NumberFormatException ignored) {
                // 单个点位坐标异常就跳过，不影响其它结果
            }
        }
        return out;
    }

    private static boolean valid(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /**
     * 状态码比较。腾讯返回的是数字 0，高德返回的是字符串 "1"，
     * 用 str() 统一取字符串会把数字吃掉，所以单独判一次。
     */
    private static boolean codeIs(Map<String, Object> m, String key, String expected) {
        if (m == null) return false;
        Object v = m.get(key);
        if (v instanceof String s) return expected.equals(s.trim());
        if (v instanceof Number n) return expected.equals(String.valueOf(n.intValue()));
        return false;
    }

    private static double dbl(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number num) return num.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    // ---------------- 腾讯位置服务 ----------------

    private static Place tencent(double lat, double lon, String key) {
        String url = "https://apis.map.qq.com/ws/geocoder/v1/?location="
                + String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
                + "&key=" + enc(key) + "&get_poi=1";
        return parseTencent(getJson(url));
    }

    /** 解析腾讯返回体。单独抽出来，便于用固定报文做回归（见 /tmp 下的解析自测） */
    static Place parseTencent(Map<String, Object> root) {
        if (root == null) return null;
        if (!codeIs(root, "status", "0")) return null;

        Map<String, Object> result = map(root, "result");
        if (result == null) return null;

        Map<String, Object> comp = map(result, "address_component");
        String province = str(comp, "province");
        String city = str(comp, "city");
        String district = str(comp, "district");
        String street = str(comp, "street");

        Map<String, Object> fa = map(result, "formatted_addresses");
        String recommend = str(fa, "recommend");

        String name = "";
        Map<String, Object> ref = map(result, "address_reference");
        Map<String, Object> landmark = map(ref, "landmark_l2");
        if (landmark != null) name = str(landmark, "title");
        if (name.isBlank()) {
            List<?> pois = list(result, "pois");
            if (pois != null && !pois.isEmpty() && pois.get(0) instanceof Map<?, ?> p0) {
                name = str(cast(p0), "title");
            }
        }

        String level = name.isBlank() ? (street.isBlank() ? "district" : "street") : "community";
        String display = recommend.isBlank() ? compose(name, street, district, city, province) : recommend;
        if (display.isBlank()) return null;
        return new Place(display, name, street, district, city, province, level, "tencent");
    }

    // ---------------- 高德 Web 服务 ----------------

    private static Place amap(double lat, double lon, String key) {
        String url = "https://restapi.amap.com/v3/geocode/regeo?key=" + enc(key)
                + "&location=" + String.format(Locale.ROOT, "%.6f,%.6f", lon, lat)
                + "&extensions=all&radius=300&roadlevel=0";
        return parseAmap(getJson(url));
    }

    /** 解析高德返回体。单独抽出来，便于用固定报文做回归 */
    static Place parseAmap(Map<String, Object> root) {
        if (root == null) return null;
        if (!codeIs(root, "status", "1")) return null;

        Map<String, Object> regeo = map(root, "regeocode");
        if (regeo == null) return null;
        Map<String, Object> comp = map(regeo, "addressComponent");
        if (comp == null) return null;

        String province = str(comp, "province");
        String city = str(comp, "city");
        String district = str(comp, "district");
        String township = str(comp, "township");

        // 小区名：高德把社区/小区放在 neighborhood，楼宇放在 building
        String name = "";
        String level = "district";
        Map<String, Object> nb = map(comp, "neighborhood");
        if (nb != null && !str(nb, "name").isBlank()) {
            name = str(nb, "name");
            level = "community";
        }
        if (name.isBlank()) {
            List<?> aois = list(regeo, "aois");
            if (aois != null) {
                for (Object o : aois) {
                    if (!(o instanceof Map<?, ?> a)) continue;
                    Map<String, Object> am = cast(a);
                    String aoiName = str(am, "name");
                    if (aoiName.isBlank()) continue;
                    name = aoiName;
                    // 住宅区/楼宇都算小区级
                    level = "community";
                    break;
                }
            }
        }
        if (name.isBlank()) {
            Map<String, Object> building = map(comp, "building");
            if (building != null && !str(building, "name").isBlank()) {
                name = str(building, "name");
                level = "building";
            }
        }
        if (name.isBlank()) {
            List<?> pois = list(regeo, "pois");
            if (pois != null && !pois.isEmpty() && pois.get(0) instanceof Map<?, ?> p0) {
                String poiName = str(cast(p0), "name");
                if (!poiName.isBlank()) {
                    name = poiName;
                    level = "poi";
                }
            }
        }
        if (name.isBlank() && !township.isBlank()) {
            level = "street";
        }

        String display = compose(name, township, district, city, province);
        if (display.isBlank()) return null;
        return new Place(display, name, township, district, city, province, level, "amap");
    }

    // ---------------- Photon（免 key，OpenStreetMap 数据） ----------------

    /** 候选地名：得分越高越像「用户想看到的那个地名」 */
    private record Cand(String name, int score, String level) {
    }

    private static final java.util.Set<String> POI_KEYS = java.util.Set.of(
            "amenity", "shop", "tourism", "leisure", "office", "landuse", "railway", "man_made", "historic");

    /**
     * Photon 是 Komoot 提供的免费 OSM 检索服务，不需要注册、不需要 key。
     * 实测国内可直连（Nominatim 同源但被墙），这是目前唯一「零门槛」的中文地名来源。
     * 数据是 OSM 众包的：城市里小区覆盖尚可，偏远农田常常只有路名，
     * 所以按「小区 > 片区 > 楼宇 > POI > 道路」打分，再按距离扣分。
     */
    private static Place photon(double lat, double lon) {
        String url = "https://photon.komoot.io/reverse?lat=" + fmt6(lat) + "&lon=" + fmt6(lon)
                + "&limit=8&radius=2";
        return parsePhoton(photonGet(url, UPSTREAM_TIMEOUT_MS), lat, lon);
    }

    static Place parsePhoton(Map<String, Object> root, double lat, double lon) {
        List<?> features = list(root, "features");
        if (features == null || features.isEmpty()) return null;

        Cand best = null;
        Map<String, Object> bestProps = null;
        int bestScore = 0;

        for (Object o : features) {
            if (!(o instanceof Map<?, ?> f)) continue;
            Map<String, Object> fm = cast(f);
            Map<String, Object> p = map(fm, "properties");
            if (p == null) continue;

            Cand c = candidate(p);
            if (c == null) continue;

            // 距离惩罚：2 公里内每公里扣 8 分，免得为了一个远处的「小区」
            // 把脚底下这条路的名字丢掉
            int score = c.score() - (int) Math.round(Math.min(distanceKm(fm, lat, lon), 2.0) * 8);
            if (score > bestScore) {
                bestScore = score;
                best = c;
                bestProps = p;
            }
        }
        if (best == null) return null;

        String display = compose(best.name(),
                str(bestProps, "county"), str(bestProps, "district"),
                str(bestProps, "city"), str(bestProps, "state"));
        if (display.isBlank()) return null;
        return new Place(display, best.name(), str(bestProps, "street"),
                str(bestProps, "district"), str(bestProps, "city"), str(bestProps, "state"),
                best.level(), "photon");
    }

    private static Cand candidate(Map<String, Object> p) {
        String name = str(p, "name");
        String locality = str(p, "locality");
        String key = str(p, "osm_key");
        String val = str(p, "osm_value");

        if ("landuse".equals(key) && "residential".equals(val) && !name.isBlank()) {
            return new Cand(name, 100, "community");      // 住宅区，最贴近「小区」
        }
        if ("place".equals(key) && !name.isBlank()) {
            return new Cand(name, 95, "community");       // neighbourhood / village 等
        }
        if (!locality.isBlank()) {
            return new Cand(locality, 90, "community");   // 要素所属片区，往往是小区名
        }
        if ("building".equals(key) && !name.isBlank()) {
            return new Cand(name, 60, "building");
        }
        if (!name.isBlank() && POI_KEYS.contains(key)) {
            return new Cand(name, 55, "poi");
        }
        if ("highway".equals(key) && !name.isBlank()) {
            return new Cand(name, 20, "street");
        }
        if (!name.isBlank()) {
            return new Cand(name, 40, "poi");
        }
        return null;
    }

    private static double distanceKm(Map<String, Object> feature, double lat, double lon) {
        Map<String, Object> geo = map(feature, "geometry");
        List<?> coords = list(geo, "coordinates");
        if (coords == null || coords.size() < 2) return 0;
        double flon = numAt(coords.get(0));
        double flat = numAt(coords.get(1));
        if (Double.isNaN(flat) || Double.isNaN(flon)) return 0;
        double x = Math.toRadians(flon - lon) * Math.cos(Math.toRadians((lat + flat) / 2));
        double y = Math.toRadians(flat - lat);
        return Math.sqrt(x * x + y * y) * 6371.0;
    }

    private static java.util.List<Suggestion> photonSearch(String kw, double lat, double lon, int n) {
        String url = "https://photon.komoot.io/api/?q=" + enc(kw) + "&limit=" + n;
        if (lat != 0 || lon != 0) {
            url += "&lat=" + fmt6(lat) + "&lon=" + fmt6(lon);
        }
        Map<String, Object> root = photonGet(url, UPSTREAM_TIMEOUT_MS);
        java.util.List<Suggestion> out = new java.util.ArrayList<>();
        List<?> features = list(root, "features");
        if (features == null) return out;

        for (Object o : features) {
            if (!(o instanceof Map<?, ?> f)) continue;
            Map<String, Object> fm = cast(f);
            Map<String, Object> p = map(fm, "properties");
            if (p == null) continue;
            String title = str(p, "name");
            if (title.isBlank()) title = str(p, "locality");
            if (title.isBlank()) continue;

            List<?> c = list(map(fm, "geometry"), "coordinates");
            if (c == null || c.size() < 2) continue;
            double lo = numAt(c.get(0));
            double la = numAt(c.get(1));
            if (!valid(la, lo)) continue;

            String addr = compose("", str(p, "county"), str(p, "district"), str(p, "city"), str(p, "state"));
            out.add(new Suggestion(title, addr, la, lo, "photon"));
        }
        return out;
    }

    private static String fmt6(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private static double numAt(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    // ---------------- 地块可种植性分类 ----------------

    /** 地块土地类型分类。前端据此提示「不适合种植 / 谨慎 / 适宜」。 */
    public enum Category { FARMLAND, GREEN, URBAN, WATER, UNKNOWN }

    /** 单点分类结果。category 为小写枚举名，label 为中文说明。 */
    public record Classification(String category, String osmKey, String osmValue, String label, String source) {
    }

    /** classify 结果缓存（坐标按 3 位小数 ≈ 111m 聚合，土地类型在此尺度下不会突变） */
    private record ClassEntry(Classification c, long expireAtMs) {
    }

    private static final Map<String, ClassEntry> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final long CLASS_TTL_MS = 3_600_000L;

    /**
     * 判断某个点是否适合作为农事地块。
     * 优先级：OSM 面状土地用途（landuse/leisure/natural，最可靠）
     *         > 中文地名关键字（OSM 没有 landuse 时兜底，能抓「中央商务区/CBD」）。
     * 命中「村/庄/屯/乡/农场」等地名时，即使 OSM 把它标成 residential，
     * 也降级为 unknown，避免误伤农村。
     */
    public static Classification classify(double lat, double lon) {
        String key = String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);
        ClassEntry cached = CLASS_CACHE.get(key);
        if (cached != null && cached.expireAtMs() > System.currentTimeMillis()) return cached.c();

        // 面状土地用途优先：OSM 明确标了公园/水体/农田时，附近一个「医院」之类的
        // POI 名字不应该把整块地判成城市建成区。有定论就不再打第二次 Photon。
        Classification osm = photonClassify(lat, lon);
        Category osmCat = osm != null ? parseCat(osm.category()) : null;

        Place p = null;
        Category finalCat;
        if (osmCat != null && osmCat != Category.UNKNOWN) {
            finalCat = osmCat;
        } else {
            // 没有面状要素时才看地名：中央商务区/CBD 这类核心区、以及公园绿地
            p = resolve(lat, lon);
            String hay = (p.displayName() + " " + p.name() + " " + p.district() + " " + p.city())
                    .toLowerCase(Locale.ROOT);
            Category nameCat = nameCategory(hay);
            finalCat = (nameCat != null && nameCat != Category.UNKNOWN) ? nameCat : Category.UNKNOWN;
        }

        Classification result = new Classification(
                finalCat.name().toLowerCase(Locale.ROOT),
                osm != null ? osm.osmKey() : "",
                osm != null ? osm.osmValue() : "",
                labelFor(finalCat, osm),
                osm != null ? osm.source() : (p != null && p.known() ? "name" : "none"));
        // 只缓存确定结果；unknown 往往是上游超时/限流导致的，缓存会把偶发失败钉死
        if (finalCat != Category.UNKNOWN) {
            CLASS_CACHE.put(key, new ClassEntry(result, System.currentTimeMillis() + CLASS_TTL_MS));
        }
        return result;
    }

    private static Category parseCat(String s) {
        try { return Category.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static Category nameCategory(String hay) {
        for (String k : URBAN_NAME) {
            if (hay.contains(k.toLowerCase(Locale.ROOT))) return Category.URBAN;
        }
        for (String k : GREEN_NAME) {
            if (hay.contains(k.toLowerCase(Locale.ROOT))) return Category.GREEN;
        }
        return null;
    }

    /**
     * 只保留「片区级」的核心区关键词。像「医院」「广场」「商场」「体育馆」这类
     * 单个 POI 的名字太容易误伤旁边的公园/农田（逆地理编码经常把最近的 POI 当名字），
     * 交给 OSM 的 landuse 去判断更可靠。
     */
    private static final java.util.Set<String> URBAN_NAME = java.util.Set.of(
            "中央商务区", "商务区", "商业区", "商业中心", "市中心", "金融街", "金融中心",
            "行政区", "工业区", "高新区", "科技园", "开发区", "商圈", "步行街", "cbd");

    private static final java.util.Set<String> GREEN_NAME = java.util.Set.of(
            "公园", "绿地", "植物园", "苗圃", "绿化", "湿地", "郊野公园",
            "森林公园", "园林", "草坪", "公园绿地");

    /** Photon 反向查询（大半径），取最近的、能判定土地类型的要素 */
    private static Classification photonClassify(double lat, double lon) {
        String url = "https://photon.komoot.io/reverse?lat=" + fmt6(lat) + "&lon=" + fmt6(lon)
                + "&limit=20&radius=800";
        // 土地判定比地名更依赖上游完整性，给 4s；超时仍降级为 unknown，不阻塞确认
        Map<String, Object> root = photonGet(url, 4000);
        List<?> features = list(root, "features");
        if (features == null || features.isEmpty()) return null;

        // 第一轮：面状土地用途（landuse/leisure/natural/waterway）优先。
        // 这些才真正描述「这块地是什么」，而 place=neighbourhood 之类的点状要素
        // 只是大范围片区的近似中心，若参与最近距离比较，会把公园误判成城市。
        Classification best = nearestClassified(features, lat, lon, true);
        // 第二轮：没有面状要素时，才看 POI / 建筑 / 片区点
        if (best == null) best = nearestClassified(features, lat, lon, false);
        if (best == null) return null;

        // 农村地名兜底：OSM 常把村庄居民点标成 residential，不能据此把农村判成城市。
        // 这里直接看 Photon 返回要素的 name/district/city/county，省掉一次 resolve 请求。
        if ("urban".equals(best.category()) && hasRuralHint(features)) {
            return new Classification("unknown", best.osmKey(), best.osmValue(), "", "photon");
        }
        return best;
    }

    private static boolean hasRuralHint(List<?> features) {
        for (Object o : features) {
            if (!(o instanceof Map<?, ?> f)) continue;
            Map<String, Object> props = map(cast(f), "properties");
            if (props == null) continue;
            String h = str(props, "name") + str(props, "district") + str(props, "city")
                    + str(props, "county") + str(props, "locality");
            if (h.contains("村") || h.contains("庄") || h.contains("屯") || h.contains("乡")
                    || h.contains("农场") || h.contains("大队") || h.contains("寨")) {
                return true;
            }
        }
        return false;
    }

    private static Classification nearestClassified(List<?> features, double lat, double lon, boolean areaOnly) {
        Classification best = null;
        double bestDist = Double.MAX_VALUE;
        for (Object o : features) {
            if (!(o instanceof Map<?, ?> f)) continue;
            Map<String, Object> fm = cast(f);
            Map<String, Object> props = map(fm, "properties");
            if (props == null) continue;
            String key = str(props, "osm_key");
            String val = str(props, "osm_value");
            if (isAreaKey(key) != areaOnly) continue;
            Category c = mapCategory(key, val);
            if (c == null || c == Category.UNKNOWN) continue;
            double d = distanceKm(fm, lat, lon);
            if (d < bestDist) {
                bestDist = d;
                best = new Classification(c.name().toLowerCase(Locale.ROOT), key, val, "", "photon");
            }
        }
        return best;
    }

    /** 面状土地用途的 osm_key */
    private static boolean isAreaKey(String key) {
        return "landuse".equals(key) || "leisure".equals(key)
                || "natural".equals(key) || "waterway".equals(key);
    }

    /**
     * OSM 标签 → 土地类型。返回 null 表示该要素不参与判定（忽略）。
     *
     * 只认「面状土地用途」和明确的城市设施 POI。道路(highway)、电力线(power)、
     * 行政边界(boundary)、铁路线(railway)、滑雪索道(aerialway) 在农村也大量存在，
     * 若一并判成城市，会把正常农田误杀，所以一律忽略。
     */
    static Category mapCategory(String key, String val) {
        if (key.isBlank() || val.isBlank()) return null;
        String k = key.toLowerCase(Locale.ROOT);
        String v = val.toLowerCase(Locale.ROOT);
        switch (k) {
            case "landuse":
                if (FARM_LANDUSE.contains(v)) return Category.FARMLAND;
                if (GREEN_LANDUSE.contains(v)) return Category.GREEN;
                if (URBAN_LANDUSE.contains(v)) return Category.URBAN;
                if ("forest".equals(v) || "wood".equals(v)) return Category.FARMLAND;
                return null;
            case "natural":
                if ("water".equals(v) || "wetland".equals(v) || "bay".equals(v) || "strait".equals(v)) return Category.WATER;
                if ("wood".equals(v) || "tree_row".equals(v) || "scrub".equals(v)
                        || "grassland".equals(v) || "fell".equals(v) || "heath".equals(v)) return Category.FARMLAND;
                return null;
            case "waterway":
                return Category.WATER;
            case "leisure":
                if (GREEN_LEISURE.contains(v)) return Category.GREEN;
                if (URBAN_LEISURE.contains(v)) return Category.URBAN;
                return null;   // 长椅、健身点等小设施不参与判定
            case "amenity":
                return URBAN_AMENITY.contains(v) ? Category.URBAN : null;
            case "place":
                if ("city".equals(v) || "suburb".equals(v) || "borough".equals(v)
                        || "quarter".equals(v) || "neighbourhood".equals(v) || "town".equals(v)) return Category.URBAN;
                if ("village".equals(v) || "hamlet".equals(v) || "isolated_dwelling".equals(v)) return Category.FARMLAND;
                return null;
            case "building":
            case "office":
            case "shop":
                return Category.URBAN;
            case "man_made":
                return "works".equals(v) ? Category.URBAN : null;
            case "tourism":
                return URBAN_TOURISM.contains(v) ? Category.URBAN : null;
            default:
                return null;
        }
    }

    private static final java.util.Set<String> FARM_LANDUSE = java.util.Set.of(
            "farmland", "farm", "farmyard", "orchard", "vineyard", "plant_nursery",
            "allotments", "meadow", "grassland", "grass", "pasture", "arable",
            "greenhouse_horticulture");
    private static final java.util.Set<String> GREEN_LANDUSE = java.util.Set.of(
            "recreation_ground", "village_green");
    private static final java.util.Set<String> URBAN_LANDUSE = java.util.Set.of(
            "residential", "commercial", "industrial", "retail", "construction",
            "brownfield", "railway", "harbour", "port", "quarry", "landfill", "cemetery",
            "military", "depot", "garages");
    private static final java.util.Set<String> GREEN_LEISURE = java.util.Set.of(
            "park", "garden", "pitch", "golf_course", "nature_reserve", "playground",
            "dog_park", "mini_golf", "common", "picnic_table");
    private static final java.util.Set<String> URBAN_LEISURE = java.util.Set.of(
            "swimming_pool", "sports_centre", "stadium", "water_park",
            "fitness_station", "track", "ice_rink", "bowling_alley");
    private static final java.util.Set<String> URBAN_AMENITY = java.util.Set.of(
            "hospital", "clinic", "doctors", "dentist", "pharmacy", "school", "university",
            "college", "kindergarten", "bank", "atm", "restaurant", "fast_food", "cafe",
            "bar", "pub", "food_court", "fuel", "parking", "marketplace", "townhall",
            "courthouse", "police", "fire_station", "post_office", "prison", "bus_station",
            "theatre", "cinema", "library", "community_centre", "conference_centre",
            "exhibition_centre", "place_of_worship", "social_facility", "nursing_home",
            "childcare", "driving_school", "car_rental", "car_wash", "charging_station");
    private static final java.util.Set<String> URBAN_TOURISM = java.util.Set.of(
            "attraction", "theme_park", "zoo", "museum", "aquarium", "hotel", "motel",
            "hostel", "guest_house", "apartment", "chalet", "resort", "information");

    private static String labelFor(Category c, Classification osm) {
        String extra = (osm != null && !osm.osmValue().isBlank()) ? "（" + osm.osmValue() + "）" : "";
        switch (c) {
            case FARMLAND: return "农田/耕地/林地" + extra + "，适宜农业种植";
            case GREEN:    return "城市绿地" + extra + "，可用于种植但需确认合法使用权限";
            case URBAN:    return "城市建成区/核心区" + extra + "，不建议农业种植";
            case WATER:    return "水体" + extra + "，适合鱼塘等水产养殖";
            default:       return "未知土地类型，请自行判断是否符合种植与用地规定";
        }
    }

    // ---------------- 球面多边形工具（面积 / 质心） ----------------

    /** 球面多边形面积（平方米）。ring 为 [lat,lon] 数组，至少 3 个点；自动闭合。 */
    public static double polygonAreaSqm(java.util.List<double[]> ring) {
        int n = ring.size();
        if (n < 3) return 0;
        double R = 6_378_137.0;
        double area = 0;
        for (int i = 0; i < n; i++) {
            double[] p1 = ring.get(i);
            double[] p2 = ring.get((i + 1) % n);
            double lat1 = Math.toRadians(p1[0]);
            double lon1 = Math.toRadians(p1[1]);
            double lat2 = Math.toRadians(p2[0]);
            double lon2 = Math.toRadians(p2[1]);
            area += (lon2 - lon1) * (2 + Math.sin(lat1) + Math.sin(lat2));
        }
        area = area * R * R / 2.0;
        return Math.abs(area);
    }

    /** 多边形顶点平均质心 [lat,lon]，用于采样与回填中心点。 */
    public static double[] ringCentroid(java.util.List<double[]> ring) {
        double la = 0, lo = 0;
        for (double[] p : ring) { la += p[0]; lo += p[1]; }
        int n = Math.max(1, ring.size());
        return new double[]{ la / n, lo / n };
    }

    // ---------------- 展示名拼装 ----------------

    /**
     * 「小区名（街道 · 区 · 市 · 省）」，逐级去重。
     * 直辖市的 province 与 city 都是「北京市」，只保留一个。
     */
    private static String compose(String name, String street, String district, String city, String province) {
        LinkedHashSet<String> tail = new LinkedHashSet<>();
        addTail(tail, street);
        addTail(tail, district);
        addTail(tail, city);
        addTail(tail, province);

        String n = name == null ? "" : name.trim();
        // 地名与行政区同名时不重复（例如「金乡县」本身就是县名）
        if (!n.isEmpty()) tail.remove(n);

        String joined = String.join(" · ", tail);
        if (n.isEmpty()) return joined;
        return joined.isEmpty() ? n : n + "（" + joined + "）";
    }

    private static void addTail(LinkedHashSet<String> set, String v) {
        if (v == null) return;
        String t = v.trim();
        if (!t.isEmpty()) set.add(t);
    }

    // ---------------- HTTP / JSON 小工具 ----------------

    /**
     * Photon 公共实例的官方限制是「每秒最多 1 次」，并发打过去会被限流/封 IP
     * （实测直接 429 或长时间超时）。这里做两件事：
     *   · 全局串行闸门，保证两次请求至少间隔 1.1s；
     *   · 连续失败 3 次后熔断 60s，避免上游挂了还把每个确认请求拖到超时。
     */
    private static final Object PHOTON_GATE = new Object();
    private static long photonLastAt = 0;
    private static final long PHOTON_MIN_INTERVAL_MS = 1100;
    private static final AtomicInteger PHOTON_FAILS = new AtomicInteger();
    private static volatile long photonBlockedUntil = 0;

    private static Map<String, Object> photonGet(String url, int timeoutMs) {
        if (System.currentTimeMillis() < photonBlockedUntil) return null;
        synchronized (PHOTON_GATE) {
            long wait = photonLastAt + PHOTON_MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try { Thread.sleep(wait); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            photonLastAt = System.currentTimeMillis();
        }
        Map<String, Object> root = getJson(url, timeoutMs);
        if (root == null) {
            if (PHOTON_FAILS.incrementAndGet() >= 3) {
                photonBlockedUntil = System.currentTimeMillis() + 60_000L;
                PHOTON_FAILS.set(0);
            }
        } else {
            PHOTON_FAILS.set(0);
        }
        return root;
    }

    private static Map<String, Object> getJson(String url) {
        return getJson(url, UPSTREAM_TIMEOUT_MS);
    }

    private static Map<String, Object> getJson(String url, int timeoutMs) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "agritime/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            Object root = JsonParser.parse(resp.body());
            return root instanceof Map<?, ?> m ? cast(m) : null;
        } catch (Exception e) {
            // 超时、DNS、限流、结构异常一律降级，由调用方决定返回什么
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static Map<String, Object> map(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v instanceof Map<?, ?> mm ? cast(mm) : null;
    }

    private static List<?> list(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v instanceof List<?> l ? l : null;
    }

    /** 高德在直辖市/无城市时会把 city 返回成空数组，这里统一成空串 */
    private static String str(Map<String, Object> m, String key) {
        if (m == null) return "";
        Object v = m.get(key);
        if (v instanceof String s) return s.trim();
        if (v instanceof List<?> l && l.isEmpty()) return "";
        return "";
    }

    private GeoResolver() {
    }
}
