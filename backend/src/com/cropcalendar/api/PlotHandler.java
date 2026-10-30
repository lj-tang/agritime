package com.cropcalendar.api;

import com.cropcalendar.orchestration.CacheStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /api/plots/environment —— 地块环境感知（T06）
 *
 * 根据经纬度返回实时气象、土壤类型、周边河流与水质等级，
 * 鱼塘类额外返回水质指标，全部纳入农事适宜度因子。
 *
 * 零依赖：JDK 自带 HttpClient，Open-Meteo 免费接口无 key。
 */
public class PlotHandler implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final double EARTH_R = 6371.0;

    /**
     * 环境数据缓存：气温/湿度这类数据几十秒内不会变，
     * 而这个接口每次都要实时打 Open-Meteo（实测 2.3 秒，超过 p95<2s 的验收线），
     * 不缓存的话用户每点一次地块详情都要等两秒多。
     * 5 分钟对"实时监控"够用，对上游也友好。
     */
    private static final CacheStore CACHE = new CacheStore();
    private static final int CACHE_TTL_SECONDS = 300;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        Map<String, String> q = HttpSupport.query(ex.getRequestURI().getRawQuery());
        double lat = HttpSupport.parseDouble(q.get("lat"), 35.07);
        double lon = HttpSupport.parseDouble(q.get("lon"), 116.31);
        String type = HttpSupport.trimToNull(q.get("type"));
        if (type == null) type = "farmland";
        // 地块建模参数（T06）：倾角/坡度/梯田参与适宜度评分
        double angle = HttpSupport.parseDouble(q.get("angle"), 0);
        double slope = HttpSupport.parseDouble(q.get("slope"), 0);
        boolean terrace = "1".equals(q.get("terrace")) || "true".equalsIgnoreCase(q.get("terrace"));

        // 坐标取到小数点后三位（约 100 米）再拼 key，附近地块能共用一次上游请求；
        // 地形参数也要进 key，否则调整坡度后会拿到旧评分
        String cacheKey = "plot:" + Math.round(lat * 1000) + ":" + Math.round(lon * 1000) + ":" + type
                + ":" + Math.round(angle) + ":" + Math.round(slope) + ":" + (terrace ? 1 : 0);
        String cached = CACHE.get(cacheKey, String.class);
        if (cached != null) {
            HttpSupport.write(ex, 200, cached);
            return;
        }

        try {
            WeatherCurrent wc = fetchCurrentWeather(lat, lon);
            String soilType = inferSoilType(lat, lon);
            String soilDesc = soilDescription(soilType);
            List<RiverInfo> rivers = findNearbyRivers(lat, lon);
            // 水质等级只认最近水体的实测等级；没有邻近水体就返回 null，
            // 不再用坐标算一个"看起来很像真的"等级糊弄用户。
            String waterGrade = rivers.isEmpty() ? null : rivers.get(0).grade();
            List<String> factors = buildFactors(wc, soilType, waterGrade, type, rivers);

            List<String> parts = new ArrayList<>();
            parts.add("\"temperature\":" + (wc.temp != null ? wc.temp : "null"));
            parts.add("\"humidity\":" + (wc.humidity != null ? wc.humidity : "null"));
            parts.add("\"windSpeed\":" + (wc.wind != null ? wc.wind : "null"));
            parts.add("\"weatherCode\":" + (wc.code != null ? wc.code : "null"));
            parts.add("\"soilType\":" + Json.str(soilType));
            parts.add("\"soilDescription\":" + Json.str(soilDesc));

            // 真实土壤数据（Open-Meteo 土壤模型）：地温与表层含水率，逐小时更新
            parts.add("\"soil\":" + Json.obj(
                    "temperature0cm", wc.soilTemp0() != null ? String.valueOf(wc.soilTemp0()) : "null",
                    "temperature6cm", wc.soilTemp6() != null ? String.valueOf(wc.soilTemp6()) : "null",
                    "moisture0to1cm", wc.soilMoist0to1() != null ? String.valueOf(wc.soilMoist0to1()) : "null",
                    "moisturePct", wc.soilMoist0to1() != null ? String.valueOf(Math.round(wc.soilMoist0to1() * 100)) : "null",
                    "status", wc.soilMoist0to1() != null ? Json.str(moistureLabel(wc.soilMoist0to1())) : "null",
                    "source", Json.str("open-meteo.soil")));
            // 数据来源标注：哪些是实测/模型数据、哪些是规则推断，避免演示时说不清
            parts.add("\"dataSources\":" + Json.obj(
                    "weather", Json.str("open-meteo.forecast"),
                    "soil", Json.str(wc.soilMoist0to1() != null || wc.soilTemp0() != null
                            ? "open-meteo.soil" : "region-rule"),
                    "soilType", Json.str("region-rule"),
                    "waterQuality", Json.str(waterGrade != null ? "river-reference" : "none")));

            List<String> riverJson = new ArrayList<>();
            for (RiverInfo r : rivers) {
                riverJson.add(Json.obj(
                        "name", Json.str(r.name),
                        "distance", String.valueOf(r.distance),
                        "waterGrade", Json.str(r.grade)));
            }
            parts.add("\"nearbyRivers\":" + Json.arr(riverJson));
            parts.add("\"waterQualityGrade\":" + Json.str(waterGrade));

            if ("fishpond".equals(type)) {
                // 没有任何水质传感器接入，这三项是按经纬度推的估算值，
                // 所以显式带上 estimated=true，前端会标"估算"，别当实测数据讲。
                double ph = 6.5 + ((lon * 7 + lat * 3) % 20) / 10.0;
                double dox = 5.0 + ((lat * 5 + lon * 2) % 50) / 10.0;
                double turb = 10 + ((lat * 3 + lon * 4) % 40);
                parts.add("\"waterQuality\":" + Json.obj(
                        "grade", Json.str(waterGrade),
                        "ph", String.format("%.1f", ph),
                        "dissolvedOxygen", String.format("%.1f", dox),
                        "turbidity", String.format("%.0f", turb),
                        "estimated", "true"));
            } else {
                parts.add("\"waterQuality\":null");
            }

            List<String> factorJson = new ArrayList<>();
            for (String f : factors) factorJson.add(Json.str(f));
            parts.add("\"factors\":" + Json.arr(factorJson));
            // 农事适宜度：土壤 + 水质 + 气候 + 地形 + 地块类型 加权（T06 验收第 5 条）
            parts.add("\"farmingSuitability\":" + farmingSuitability(wc, soilType, waterGrade, type, angle, slope, terrace));

            // parts 里每一项已经是拼好的 "\"key\":value" 片段，直接拼接成对象。
            // 不能再交给 Json.obj()：它接收的是「键, 值」交替参数，
            // 传片段数组会把每个片段当成键，导致整段键值错位、返回非法 JSON。
            String body = "{" + String.join(",", parts) + "}";
            CACHE.put(cacheKey, body, CACHE_TTL_SECONDS);
            HttpSupport.write(ex, 200, body);
        } catch (Exception e) {
            HttpSupport.write(ex, 500, Json.obj("error", Json.str("INTERNAL_ERROR"), "message", Json.str(String.valueOf(e.getMessage()))));
        }
    }

    private record WeatherCurrent(Double temp, Integer humidity, Double wind, Integer code,
                               Double soilTemp0, Double soilTemp6, Double soilMoist0to1) {}

    private WeatherCurrent fetchCurrentWeather(double lat, double lon) {
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                        + ",soil_temperature_0cm,soil_temperature_6cm,soil_moisture_0_to_1cm",
                lat, lon);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return new WeatherCurrent(null, null, null, null, null, null, null);
            String body = resp.body();
            return parseCurrent(body);
        } catch (Exception e) {
            return new WeatherCurrent(null, null, null, null, null, null, null);
        }
    }

    private WeatherCurrent parseCurrent(String body) {
        Double temp = null; Integer humidity = null; Double wind = null; Integer code = null;
        int curIdx = body.indexOf("\"current\":");
        if (curIdx < 0) return new WeatherCurrent(null, null, null, null, null, null, null);
        String cur = body.substring(curIdx);
        temp = extractDouble(cur, "\"temperature_2m\":");
        humidity = extractInt(cur, "\"relative_humidity_2m\":");
        wind = extractDouble(cur, "\"wind_speed_10m\":");
        code = extractInt(cur, "\"weather_code\":");
        // 真实土壤温湿度（Open-Meteo 土壤模型，0cm/6cm 地温与 0-1cm 体积含水率）
        Double st0 = extractDouble(cur, "\"soil_temperature_0cm\":");
        Double st6 = extractDouble(cur, "\"soil_temperature_6cm\":");
        Double sm01 = extractDouble(cur, "\"soil_moisture_0_to_1cm\":");
        return new WeatherCurrent(temp, humidity, wind, code, st0, st6, sm01);
    }

    private Double extractDouble(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        i += key.length();
        int end = i;
        while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.' || s.charAt(end) == '-')) end++;
        try { return Double.parseDouble(s.substring(i, end)); } catch (Exception e) { return null; }
    }

    private Integer extractInt(String s, String key) {
        Double d = extractDouble(s, key);
        return d != null ? d.intValue() : null;
    }

    private String inferSoilType(double lat, double lon) {
        if (lat >= 42 && lon >= 120) return "黑土";
        if (lat >= 35 && lat < 42 && lon >= 110 && lon < 122) return "潮土";
        if (lat >= 20 && lat < 35 && lon >= 105 && lon <= 122) return "红壤";
        if (lat >= 35 && lon >= 75 && lon < 110) return "黄绵土";
        if (lat >= 20 && lat < 35 && lon >= 90 && lon < 105) return "紫色土";
        if (lat >= 25 && lon >= 100 && lon < 110) return "黄壤";
        return "褐土";
    }

    private String soilDescription(String type) {
        return switch (type) {
            case "黑土" -> "有机质丰富，保水保肥力强，宜种大豆玉米";
            case "潮土" -> "土层深厚，肥力中等，宜种小麦玉米大蒜";
            case "红壤" -> "酸性偏强，需改良，宜种茶果薯类";
            case "黄绵土" -> "土质疏松，易侵蚀，需保水措施";
            case "紫色土" -> "含磷钾丰富，宜种水稻果蔬";
            case "黄壤" -> "湿润肥沃，宜种水稻烟草";
            default -> "土层较厚，适种性广";
        };
    }

    private record RiverInfo(String name, double distance, String grade) {}

    private static final String[] RIVER_NAMES = {
            "黄河中游", "黄河下游", "长江中游", "长江下游", "淮河",
            "珠江", "松花江", "海河", "辽河", "钱塘江"
    };
    private static final double[] RIVER_LAT = {36.0, 35.2, 30.5, 32.0, 33.0, 23.1, 46.0, 39.0, 42.0, 30.0};
    private static final double[] RIVER_LON = {110.5, 115.0, 112.3, 118.8, 116.3, 113.3, 126.5, 117.2, 123.5, 120.5};
    private static final String[] RIVER_GRADES = {"Ⅲ类", "Ⅲ类", "Ⅱ类", "Ⅱ类", "Ⅳ类", "Ⅲ类", "Ⅲ类", "Ⅳ类", "Ⅳ类", "Ⅱ类"};

    private List<RiverInfo> findNearbyRivers(double lat, double lon) {
        List<RiverInfo> result = new ArrayList<>();
        for (int i = 0; i < RIVER_NAMES.length; i++) {
            double dist = haversine(lat, lon, RIVER_LAT[i], RIVER_LON[i]);
            if (dist <= 200) {
                result.add(new RiverInfo(RIVER_NAMES[i], Math.round(dist * 10) / 10.0, RIVER_GRADES[i]));
            }
        }
        result.sort((a, b) -> Double.compare(a.distance, b.distance));
        return result.size() > 4 ? result.subList(0, 4) : result;
    }

    /**
     * 农事适宜度（T06 验收第 5 条）：土壤 / 水质 / 气候 / 地形 / 地块类型 加权评分。
     *
     * 缺数据的一项不参与（例如附近无监测水体、天气取不到），权重按比例重新归一，
     * 不用默认值假装有数据 —— 否则"没数据"会被算成一个中间分，等于编造结论。
     */
    private String farmingSuitability(WeatherCurrent wc, String soil, String waterGrade,
                                      String type, double angle, double slope, boolean terrace) {
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        Double climate = climateScore(wc);
        if (climate != null) {
            keys.add("climate");
            labels.add("气候");
            scores.add(climate);
            weights.add(0.30);
            notes.add("气温 " + wc.temp + "℃、湿度 " + wc.humidity + "%");
        }

        keys.add("soil");
        labels.add("土壤");
        scores.add(soilScore(wc, soil));
        weights.add(0.30);
        notes.add(soilNote(wc, soil));

        Double water = waterScore(waterGrade);
        if (water != null) {
            keys.add("water");
            labels.add("水质");
            scores.add(water);
            weights.add(0.20);
            notes.add("最近水体水质 " + waterGrade);
        }

        keys.add("terrain");
        labels.add("地形");
        scores.add(terrainScore(angle, slope, terrace));
        weights.add(0.10);
        notes.add(terrainNote(angle, slope, terrace));

        keys.add("type");
        labels.add("地块类型");
        scores.add(typeScore(type, waterGrade));
        weights.add(0.10);
        notes.add(typeLabel(type) + (isWaterType(type) && waterGrade == null ? "（附近无水质数据）" : ""));

        double wsum = 0;
        for (double w : weights) wsum += w;
        double total = 0;
        for (int i = 0; i < scores.size(); i++) total += scores.get(i) * weights.get(i) / wsum;

        String level;
        if (total >= 85) level = "适宜";
        else if (total >= 70) level = "较适宜";
        else if (total >= 55) level = "一般";
        else level = "需注意";

        List<String> fj = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            fj.add(Json.obj(
                    "key", Json.str(keys.get(i)),
                    "label", Json.str(labels.get(i)),
                    "score", Json.num((double) Math.round(scores.get(i))),
                    "weight", Json.num(Math.round(weights.get(i) / wsum * 100) / 100.0),
                    "note", Json.str(notes.get(i))));
        }

        return Json.obj(
                "score", String.valueOf(Math.round(total)),
                "level", Json.str(level),
                "summary", Json.str(suitabilitySummary(level, soil, waterGrade, slope)),
                "factors", Json.arr(fj));
    }

    private String suitabilitySummary(String level, String soil, String waterGrade, double slope) {
        StringBuilder sb = new StringBuilder();
        sb.append("综合土壤、").append(waterGrade != null ? "水质、" : "").append("气候与地形，当前评定为").append(level);
        if ("红壤".equals(soil) || "黄绵土".equals(soil)) {
            sb.append("；该土壤需改良（").append("红壤".equals(soil) ? "偏酸" : "易侵蚀").append("）");
        }
        if (slope > 15) sb.append("；坡度较大，建议等高种植或修梯田");
        return sb.toString();
    }

    private Double climateScore(WeatherCurrent wc) {
        if (wc.temp == null && wc.humidity == null) return null;
        double sum = 0;
        int n = 0;
        if (wc.temp != null) {
            double t = wc.temp;
            double s;
            if (t >= 15 && t <= 30) s = 95;
            else if (t >= 8 && t < 15) s = 75;
            else if (t > 30 && t <= 35) s = 70;
            else if (t >= 0 && t < 8) s = 60;
            else if (t > 35) s = 45;
            else s = 40;
            sum += s;
            n++;
        }
        if (wc.humidity != null) {
            double h = wc.humidity;
            double s;
            if (h >= 40 && h <= 75) s = 95;
            else if (h > 75 && h <= 85) s = 80;
            else if (h > 85) s = 60;
            else if (h >= 25) s = 70;
            else s = 55;
            sum += s;
            n++;
        }
        return n == 0 ? null : sum / n;
    }

    /**
     * 土壤分：优先用真实土壤数据（0-1cm 体积含水率 + 0cm 地温，Open-Meteo 土壤模型），
     * 两项都没有时才降级到区域规则推断的土壤类型。
     */
    private double soilScore(WeatherCurrent wc, String soil) {
        double sum = 0;
        int n = 0;
        if (wc.soilMoist0to1() != null) {
            double m = wc.soilMoist0to1();
            double v;
            if (m < 0.10) v = 45;        // 严重偏干
            else if (m < 0.18) v = 65;   // 偏干
            else if (m <= 0.32) v = 95;  // 墒情适宜
            else if (m <= 0.42) v = 72;  // 偏湿
            else v = 50;                 // 过湿，易沤根
            sum += v;
            n++;
        }
        if (wc.soilTemp0() != null) {
            double t = wc.soilTemp0();
            double v;
            if (t < 0) v = 40;
            else if (t < 10) v = 65;
            else if (t <= 25) v = 95;
            else if (t <= 32) v = 80;
            else v = 60;
            sum += v;
            n++;
        }
        if (n == 0) return soilTypeScore(soil); // 三档降级：没有实测就用区域规则
        return sum / n;
    }

    private String soilNote(WeatherCurrent wc, String soil) {
        StringBuilder sb = new StringBuilder();
        if (wc.soilMoist0to1() != null) {
            sb.append("0-1cm 含水率 ").append(Math.round(wc.soilMoist0to1() * 100))
              .append("%（").append(moistureLabel(wc.soilMoist0to1())).append("）");
        }
        if (wc.soilTemp0() != null) {
            if (sb.length() > 0) sb.append("、");
            sb.append("地表温度 ").append(wc.soilTemp0()).append("℃");
        }
        if (sb.length() == 0) {
            sb.append("无实测土壤数据，按区域规则推断为").append(soil);
        } else {
            sb.append("；土壤类型（区域规则）：").append(soil);
        }
        return sb.toString();
    }

    private String moistureLabel(double m) {
        if (m < 0.10) return "严重偏干";
        if (m < 0.18) return "偏干";
        if (m <= 0.32) return "墒情适宜";
        if (m <= 0.42) return "偏湿";
        return "过湿";
    }

    /** 区域规则推断的土壤类型基准分，仅在无实测数据时兜底 */
    private double soilTypeScore(String soil) {
        return switch (soil) {
            case "黑土" -> 95;
            case "潮土" -> 88;
            case "紫色土" -> 85;
            case "黄壤" -> 80;
            case "褐土" -> 76;
            case "红壤" -> 64;   // 酸性偏强，需改良
            case "黄绵土" -> 58; // 土质疏松易侵蚀
            default -> 70;
        };
    }

    private Double waterScore(String grade) {
        if (grade == null) return null;
        // 注意：switch 表达式里有 null 分支时整体类型会推断成 Double，
        // 各分支必须统一返回 double 字面量，否则 int 无法隐式转 Double
        double v = switch (grade) {
            case "Ⅰ类" -> 95;
            case "Ⅱ类" -> 88;
            case "Ⅲ类" -> 74;
            case "Ⅳ类" -> 55;
            case "Ⅴ类" -> 35;
            default -> -1;
        };
        return v < 0 ? null : v;
    }

    private double terrainScore(double angle, double slope, boolean terrace) {
        double worst = Math.max(Math.abs(angle), Math.abs(slope));
        double base;
        if (worst <= 3) base = 95;
        else if (worst <= 8) base = 85;
        else if (worst <= 15) base = 72;
        else if (worst <= 25) base = 55;
        else base = 38;
        if (terrace) base = Math.min(100, base + 8); // 梯田能显著缓解坡度影响
        return base;
    }

    private String terrainNote(double angle, double slope, boolean terrace) {
        String s = "倾角 " + fmt(angle) + "°、坡度 " + fmt(slope) + "°";
        if (terrace) s += "，已修梯田（缓解水土流失）";
        else if (Math.max(Math.abs(angle), Math.abs(slope)) > 15) s += "，坡度偏大建议修梯田";
        return s;
    }

    private String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    private boolean isWaterType(String type) {
        return "fishpond".equals(type) || "paddy".equals(type);
    }

    private double typeScore(String type, String waterGrade) {
        return switch (type) {
            case "farmland" -> 95;
            case "economic" -> 90;
            case "greenhouse" -> 88;
            case "orchard" -> 88;
            case "paddy" -> waterGrade == null ? 72 : 90; // 水稻田依赖水源
            case "fishpond" -> waterGrade == null ? 55 : 92; // 鱼塘没有水质数据就是硬伤
            default -> 85;
        };
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "farmland" -> "耕田";
            case "economic" -> "经济作物田";
            case "greenhouse" -> "大棚";
            case "orchard" -> "果园";
            case "paddy" -> "水稻田";
            case "fishpond" -> "鱼塘";
            default -> "其他";
        };
    }

    private List<String> buildFactors(WeatherCurrent wc, String soil, String waterGrade, String type, List<RiverInfo> rivers) {
        List<String> f = new ArrayList<>();
        f.add("土壤：" + soil);
        if (wc.temp != null) {
            if (wc.temp >= 15 && wc.temp <= 30) f.add("气温适宜（" + wc.temp + "°C）");
            else if (wc.temp < 5) f.add("气温偏低，注意防冻");
            else if (wc.temp > 35) f.add("高温预警，注意灌溉");
            else f.add("气温一般（" + wc.temp + "°C）");
        }
        if (wc.humidity != null) {
            if (wc.humidity >= 40 && wc.humidity <= 75) f.add("湿度适中（" + wc.humidity + "%）");
            else if (wc.humidity > 80) f.add("湿度过高，防病害");
            else f.add("湿度偏低（" + wc.humidity + "%）");
        }
        if (waterGrade != null) {
            f.add("水质等级：" + waterGrade + "（参考" + rivers.get(0).name + "）");
        } else {
            f.add("水质：附近无可参考监测水体");
        }
        if ("fishpond".equals(type)) f.add("鱼塘地块：关注溶氧与pH");
        if ("greenhouse".equals(type)) f.add("大棚地块：关注通风控温");
        if ("paddy".equals(type)) f.add("水稻田：保持水位管理");
        if (!rivers.isEmpty()) f.add("近" + rivers.get(0).name + "（" + rivers.get(0).distance + "km）");
        return f;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_R * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}