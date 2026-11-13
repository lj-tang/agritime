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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 两个"可选因子"的真实数据源（V5 T16）：土壤质地、水源可达性、交通可达性。
 *
 * 为什么单独抽出来：这两类查询都慢（SoilGrids 常年 8~12 秒，Overpass 3~8 秒），
 * 而且随时可能超时。地块评分不能为了凑因子数去等它们，更不能取不到时用默认值顶替 ——
 * 所以调用方拿到的要么是真实结果，要么是 null，null 时该因子直接退出、权重重算。
 *
 * 缓存按坐标取两位小数（约 1 公里）分桶：同一片地反复分析不会反复打外部接口。
 */
public final class GeoFactors {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(AppConfig.httpConnectTimeoutMs()))
            .build();

    private static final long SOIL_TTL_MS = 24L * 3600 * 1000;
    private static final long ACCESS_TTL_MS = 6L * 3600 * 1000;
    private static final int MAX_CACHE = 300;

    private static final Map<String, Cached<Soil>> SOIL = new ConcurrentHashMap<>();
    private static final Map<String, Cached<Access>> ACCESS = new ConcurrentHashMap<>();

    private record Cached<T>(long at, T value) {
    }

    private GeoFactors() {
    }

    /**
     * 土壤质地。
     *
     * @param texture 质地分类（黏土/黏壤/壤土/砂壤/砂土）
     * @param clayPct 黏粒占比（%）
     * @param sandPct 砂粒占比（%）
     */
    public record Soil(String texture, double clayPct, double sandPct) {
    }

    /**
     * 可达性。
     *
     * @param waterKm   到最近水体/河渠的距离（公里）
     * @param roadKm    到最近可通车道路的距离（公里）
     * @param roadClass 最近道路等级（primary/secondary/tertiary…）
     */
    public record Access(double waterKm, double roadKm, String roadClass,
                         boolean urban, boolean farmland, String landUse) {
    }

    /** 取不到返回 null —— 调用方据此让因子退出，不要编默认值。 */
    public static Soil soilTexture(double lat, double lon) {
        String key = bucket(lat, lon);
        Cached<Soil> hit = SOIL.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.at() < SOIL_TTL_MS) return hit.value();

        Soil soil = fetchSoil(lat, lon);
        if (soil != null) {
            trim(SOIL);
            SOIL.put(key, new Cached<>(now, soil));
        }
        return soil;
    }

    /** 取不到返回 null。 */
    public static Access accessibility(double lat, double lon) {
        String key = bucket(lat, lon);
        Cached<Access> hit = ACCESS.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.at() < ACCESS_TTL_MS) return hit.value();

        Access access = fetchAccess(lat, lon);
        if (access != null) {
            trim(ACCESS);
            ACCESS.put(key, new Cached<>(now, access));
        }
        return access;
    }

    // ---------------- SoilGrids ----------------

    private static Soil fetchSoil(double lat, double lon) {
        String url = "https://rest.isric.org/soilgrids/v2.0/properties/query?lon=" + fmt(lon)
                + "&lat=" + fmt(lat)
                + "&property=clay&property=sand&depth=0-5cm&value=mean";
        String body = get(url, AppConfig.soilGridsTimeoutMs());
        if (body == null) return null;
        try {
            Object root = JsonParser.parse(body);
            if (!(root instanceof Map<?, ?> m)
                    || !(m.get("properties") instanceof Map<?, ?> props)
                    || !(props.get("layers") instanceof List<?> layers)) {
                return null;
            }
            Double clay = null;
            Double sand = null;
            for (Object o : layers) {
                if (!(o instanceof Map<?, ?> layer)) continue;
                String name = String.valueOf(layer.get("name"));
                String unit = layer.get("unitMeasure") == null ? "" : String.valueOf(layer.get("unitMeasure"));
                Double v = firstMean(layer);
                if (v == null) continue;
                // SoilGrids 惯用 %×10 表示（clay=225 即 22.5%），单位是 % 或 g/kg 时都要归一
                if (v > 100) v = v / 10;
                if ("clay".equals(name)) clay = v;
                if ("sand".equals(name)) sand = v;
            }
            if (clay == null && sand == null) return null;
            double c = clay == null ? 0 : clay;
            double s = sand == null ? 0 : sand;
            return new Soil(classify(c, s), round1(c), round1(s));
        } catch (Exception e) {
            System.err.println("[geo] 土壤质地解析失败：" + e.getMessage());
            return null;
        }
    }

    private static Double firstMean(Map<?, ?> layer) {
        if (!(layer.get("depths") instanceof List<?> depths) || depths.isEmpty()) return null;
        Object d = depths.get(0);
        if (!(d instanceof Map<?, ?> depth) || !(depth.get("values") instanceof Map<?, ?> values)) return null;
        Object mean = values.get("mean");
        if (mean instanceof Number n) return n.doubleValue();
        try {
            return mean == null ? null : Double.parseDouble(String.valueOf(mean));
        } catch (Exception e) {
            return null;
        }
    }

    /** 简化质地分类：农业上关心的是"保水保肥 vs 透气"，不必上完整三角图。缺测（0/0）落壤土。 */
    static String classify(double clay, double sand) {
        if (clay >= 40) return "黏土";
        if (clay >= 27) return "黏壤";
        if (sand >= 70) return "砂土";
        if (sand >= 50) return "砂壤";
        return "壤土";
    }

    // ---------------- Overpass ----------------

    /**
     * 一次查询同时取水体与道路，省一半往返。
     * 半径 1.5 公里：超出这个范围，对地块本身的意义已经不大，也没必要拉更多数据。
     */
    private static Access fetchAccess(double lat, double lon) {
        String q = "[out:json][timeout:" + Math.max(1, AppConfig.overpassTimeoutMs() / 1000) + "];"
                + "("
                + "way(around:1500," + fmt(lat) + "," + fmt(lon) + ")[natural=water];"
                + "way(around:1500," + fmt(lat) + "," + fmt(lon) + ")[waterway~\"river|canal|stream|ditch\"];"
                + "way(around:1500," + fmt(lat) + "," + fmt(lon)
                + ")[highway~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$\"];"
                // 土地利用：判断这个点是不是落在城镇建成区里（城区不该给种地建议）
                + "way(around:1500," + fmt(lat) + "," + fmt(lon)
                + ")[landuse~\"^(residential|commercial|industrial|retail|railway|construction"
                + "|farmland|orchard|vineyard|allotments|meadow|farmyard|plant_nursery)$\"];"
                + ");"
                + "out geom 120;";
        String url = "https://overpass-api.de/api/interpreter?data="
                + URLEncoder.encode(q, StandardCharsets.UTF_8);
        String body = get(url, AppConfig.overpassTimeoutMs());
        if (body == null) return null;
        try {
            Object root = JsonParser.parse(body);
            if (!(root instanceof Map<?, ?> m) || !(m.get("elements") instanceof List<?> elements)) return null;

            double water = Double.MAX_VALUE;
            double road = Double.MAX_VALUE;
            String roadClass = null;
            boolean any = false;
            boolean urban = false;
            boolean farmland = false;
            String landUse = null;

            for (Object o : elements) {
                if (!(o instanceof Map<?, ?> el)) continue;
                if (!(el.get("geometry") instanceof List<?> geom)) continue;
                Map<?, ?> tags = el.get("tags") instanceof Map<?, ?> t ? t : Map.of();
                // 土地利用优先判：点落在多边形内才算，不做"附近有小区就算城区"的推测
                Object lu = tags.get("landuse");
                if (lu != null && pointInRing(lat, lon, geom)) {
                    String kind = String.valueOf(lu);
                    if (isUrbanLandUse(kind)) {
                        urban = true;
                        landUse = kind;
                    } else if (isFarmLandUse(kind)) {
                        farmland = true;
                        landUse = kind;
                    }
                }
                boolean isWater = tags.get("natural") != null || tags.get("waterway") != null;
                String highway = tags.get("highway") == null ? null : String.valueOf(tags.get("highway"));
                if (!isWater && highway == null) continue;

                double d = nearestKm(lat, lon, geom);
                if (d < 0) continue;
                any = true;
                if (isWater && d < water) water = d;
                if (highway != null && d < road) {
                    road = d;
                    roadClass = highway;
                }
            }
            // 只有水体和道路都被 Overpass 漏掉时才认为整体失败；
            // 单独拿到 landuse 也算有结果（城区判定不依赖道路）
            if (!any && !urban && !farmland) return null;
            return new Access(
                    water == Double.MAX_VALUE ? -1 : round2(water),
                    road == Double.MAX_VALUE ? -1 : round2(road),
                    roadClass, urban, farmland, landUse);
        } catch (Exception e) {
            System.err.println("[geo] 可达性解析失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 坐标离城镇建成区有多近。
     *
     * 为什么用行政区划距离而不是 OSM landuse：这台服务器访问 overpass-api.de 与
     * nominatim 全部超时（实测 25s 无响应）、高德又没配 key，靠外部数据只会得到
     * "一直 unknown"，等于没做。
     *
     * 但"离政府驻地几公里"和"是不是农用地"不是一回事，所以这里分三档而不是一刀切：
     *   core  距市中心 ≤2.5 公里（或紧贴区驻地且本就在主城范围内）—— 基本是建成区核心
     *   edge  距市中心 ≤6 公里，或距市辖区驻地 ≤3 公里 —— 城郊结合部，可能仍是菜地
     *   rural 其余
     *
     * 第一版把 core 定在 5 公里 / 3 公里，实测把县城边上的大棚、城郊菜地全判成了城区
     * （县级市 GeoNames 坐标本身还有几公里误差），等于在真正种地的人身上设卡。
     * 现在的原则是：只有 core 才拦，edge 只提示，且前端永远留"仍按这里生成"的口子。
     */
    public record Urban(String level, String label, String hint, double cityKm, double districtKm) {
        /** 兼容旧调用：只有 core 才算"不能作为农用地"。 */
        public boolean urban() {
            return "core".equals(level);
        }
    }

    /** core：建成区核心，默认拦。只认真正贴着市中心的点 */
    private static final double CORE_CITY_KM = 2.5;
    /** 区驻地单独不足以判核心：杨凌这类"区中心就是农田"的地方会被误伤，所以还要求不离主城 */
    private static final double CORE_DISTRICT_KM = 1.0;
    private static final double CORE_MAX_CITY_KM = 15.0;
    /** edge：靠近城区，只提示不拦 */
    private static final double EDGE_CITY_KM = 6.0;
    private static final double EDGE_DISTRICT_KM = 3.0;
    private static final double EDGE_MAX_CITY_KM = 20.0;

    public static Urban urbanCheck(double lat, double lon) {
        CnGazetteer.Nearest n = CnGazetteer.nearest(lat, lon);
        if (n == null) return null;
        String city = n.city() == null ? "" : n.city();
        String dist = n.district() == null ? "" : n.district();
        boolean hasD = n.urbanDistrict() && n.districtKm() >= 0;

        // 哪条距离更近就用哪条来措辞，避免说"距区驻地 0.4 公里"却其实是市中心
        boolean coreCity = n.cityKm() <= CORE_CITY_KM;
        boolean coreDist = hasD && n.districtKm() <= CORE_DISTRICT_KM
                && n.cityKm() <= CORE_MAX_CITY_KM;
        if (coreCity || coreDist) {
            String label = (coreDist && n.districtKm() < n.cityKm())
                    ? dist + "驻地 " + fmtKm(n.districtKm()) + " 公里内"
                    : city + "市中心 " + fmtKm(n.cityKm()) + " 公里内";
            return new Urban("core", label,
                    "这里是城镇建成区核心，通常不是农用地；阳台、屋顶等非耕地种植请忽略此提示",
                    n.cityKm(), n.districtKm());
        }
        boolean edgeCity = n.cityKm() <= EDGE_CITY_KM;
        boolean edgeDist = hasD && n.districtKm() <= EDGE_DISTRICT_KM
                && n.cityKm() <= EDGE_MAX_CITY_KM;
        if (edgeCity || edgeDist) {
            String label = (edgeDist && n.districtKm() < n.cityKm())
                    ? "距" + dist + "驻地 " + fmtKm(n.districtKm()) + " 公里"
                    : "距" + city + "市中心 " + fmtKm(n.cityKm()) + " 公里";
            return new Urban("edge", label,
                    "靠近城镇建成区，若这里是城郊菜地、大棚或庭院，可以照常生成",
                    n.cityKm(), n.districtKm());
        }
        return new Urban("rural", "", "", n.cityKm(), n.districtKm());
    }

    private static String fmtKm(double km) {
        return km < 10 ? String.format(java.util.Locale.ROOT, "%.1f", km)
                : String.valueOf(Math.round(km));
    }

    /** 土地利用代码 → 中文（两处接口共用，避免各写一套）。 */
    public static String landUseLabel(String kind) {
        if (kind == null || kind.isBlank()) return "";
        return switch (kind) {
            case "residential" -> "居民区";
            case "commercial" -> "商业区";
            case "industrial" -> "工业区";
            case "retail" -> "商业零售区";
            case "railway" -> "铁路用地";
            case "construction" -> "在建工地";
            case "farmland" -> "农田";
            case "orchard" -> "果园";
            case "vineyard" -> "葡萄园";
            case "allotments" -> "小块菜地";
            case "meadow" -> "草地";
            case "farmyard" -> "农家场院";
            case "plant_nursery" -> "苗圃";
            default -> kind;
        };
    }

    /** 建成区类土地利用：这些地块上不该排农事。 */
    private static boolean isUrbanLandUse(String k) {
        return switch (k) {
            case "residential", "commercial", "industrial", "retail",
                 "railway", "construction" -> true;
            default -> false;
        };
    }

    /** 农业类土地利用：命中说明这里确实是农用地。 */
    private static boolean isFarmLandUse(String k) {
        return switch (k) {
            case "farmland", "orchard", "vineyard", "allotments",
                 "meadow", "farmyard", "plant_nursery" -> true;
            default -> false;
        };
    }

    /**
     * 射线法判断点是否落在多边形内（经纬度平面，1.5 公里尺度误差可忽略）。
     * 只判 way 的首尾闭合，Overpass 的 out geom 已按顺序给出节点。
     */
    static boolean pointInRing(double lat, double lon, List<?> geometry) {
        int n = geometry.size();
        if (n < 4) return false;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if (!(geometry.get(i) instanceof Map<?, ?> pi) || !(geometry.get(j) instanceof Map<?, ?> pj)) continue;
            Double yi = num(pi.get("lat")), xi = num(pi.get("lon"));
            Double yj = num(pj.get("lat")), xj = num(pj.get("lon"));
            if (yi == null || xi == null || yj == null || xj == null) continue;
            boolean cross = (yi > lat) != (yj > lat)
                    && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi;
            if (cross) inside = !inside;
        }
        return inside;
    }

    /** 点到折线的最短距离（公里），用等距圆柱近似，1.5 公里尺度足够。 */
    private static double nearestKm(double lat, double lon, List<?> geometry) {
        double best = -1;
        for (Object g : geometry) {
            if (!(g instanceof Map<?, ?> p)) continue;
            Double plat = num(p.get("lat"));
            Double plon = num(p.get("lon"));
            if (plat == null || plon == null) continue;
            double d = haversineKm(lat, lon, plat, plon);
            if (best < 0 || d < best) best = d;
        }
        return best;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    // ---------------- 工具 ----------------

    private static String get(String url, int timeoutMs) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                    .header("User-Agent", "agritime/1.0 (plot analysis)")
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                // 把状态码记下来：403/429 与"超时"是两类问题，排查时不能混为一谈
                System.err.println("[geo] " + url.split("\\?")[0] + " 返回 HTTP " + resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            System.err.println("[geo] 请求失败 " + url.split("\\?")[0] + "：" + e.getMessage());
            return null;
        }
    }

    private static String bucket(double lat, double lon) {
        return String.format("%.2f,%.2f", lat, lon);
    }

    private static void trim(Map<String, ?> cache) {
        if (cache.size() <= MAX_CACHE) return;
        List<String> keys = new ArrayList<>(cache.keySet());
        for (int i = 0; i < keys.size() - MAX_CACHE; i++) cache.remove(keys.get(i));
    }

    private static Double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? null : Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 1e5) / 1e5);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
