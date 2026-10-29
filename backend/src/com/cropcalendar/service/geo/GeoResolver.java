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
        return parsePhoton(getJson(url), lat, lon);
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
        Map<String, Object> root = getJson(url);
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

    private static Map<String, Object> getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(UPSTREAM_TIMEOUT_MS))
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
