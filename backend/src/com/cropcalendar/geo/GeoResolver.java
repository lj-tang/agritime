package com.cropcalendar.geo;

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
            java.util.List<Suggestion> r = tencentSearch(kw, lat, lon, n, qqKey);
            if (!r.isEmpty()) return r;
        }
        String amapKey = AppConfig.geoAmapKey();
        if (!amapKey.isBlank()) {
            java.util.List<Suggestion> r = amapSearch(kw, n, amapKey);
            if (!r.isEmpty()) return r;
        }
        return java.util.List.of();
    }

    private static java.util.List<Suggestion> tencentSearch(String kw, double lat, double lon, int n, String key) {
        String url = "https://apis.map.qq.com/ws/place/v1/suggestion/?keyword=" + enc(kw)
                + "&location=" + String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
                + "&page_size=" + n + "&key=" + enc(key);
        Map<String, Object> root = getJson(url);
        java.util.List<Suggestion> out = new java.util.ArrayList<>();
        if (root == null || !"0".equals(str(root, "status"))) return out;
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
        if (root == null || !"1".equals(str(root, "status"))) return out;
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
        Map<String, Object> root = getJson(url);
        if (root == null) return null;
        if (!"0".equals(str(root, "status"))) return null;

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
        Map<String, Object> root = getJson(url);
        if (root == null) return null;
        if (!"1".equals(str(root, "status"))) return null;

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
