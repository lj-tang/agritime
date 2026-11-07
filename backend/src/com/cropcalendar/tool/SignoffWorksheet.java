package com.cropcalendar.tool;

import com.cropcalendar.service.geo.TerrainService;
import com.cropcalendar.util.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T27 地理抽检「签字工作表」生成器（一次性运维工具，非线上接口）。
 *
 * 读 data/geo-baseline.json 的 20 个已知地块，逐个拉 90m DEM（Open-Meteo Elevation），
 * 用 {@link TerrainService#fitPlane} 算出系统视角的坡度/坡向，生成一张可人工核对的表格，
 * 并落一份 data/geo-baseline-computed.json。
 *
 * 用途：把「人工核对坡度/坡向」这一步做成可执行的动作 —— 运维拿着这张表对照地形图
 * （或实地经验）逐条核，把认可的值回填进 geo-baseline.json 的
 * referenceSlopeDeg / referenceAspectDeg，之后 GeoBaselineTest 会自动算一致率。
 *
 * 用法（在项目根，需已编译后端）：
 *   java -cp backend/build/classes com.cropcalendar.tool.SignoffWorksheet
 *
 * 注意：这是「系统算出来供人核对」的值，不是最终签字值；DEM 是 90m 粗分辨率，
 * 对小块坡地会有误差，所以必须人工把关。
 */
public final class SignoffWorksheet {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(4000))
            .build();

    public static void main(String[] args) throws Exception {
        Path base = Path.of("data", "geo-baseline.json");
        String text = Files.readString(base, StandardCharsets.UTF_8);
        Object root = JsonParser.parse(text);
        if (!(root instanceof Map<?, ?> m) || !(m.get("plots") instanceof List<?> list)) {
            System.err.println("基线结构不对：应为 {plots:[...]}");
            System.exit(1);
        }

        StringBuilder md = new StringBuilder();
        md.append("| # | 地块 | 面积(亩) | 计算坡度(°) | 计算坡向(°) | 坡向标签 | 人工核对坡度 | 人工核对坡向 |\n");
        md.append("|---|------|---------|------------|------------|---------|------------|------------|\n");

        List<Map<String, Object>> computed = new ArrayList<>();
        int i = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> pm)) continue;
            String name = String.valueOf(pm.get("name"));
            double expectedMu = Double.parseDouble(String.valueOf(pm.get("expectedAreaMu")));

            List<double[]> ring = new ArrayList<>();
            if (pm.get("ring") instanceof List<?> rl) {
                for (Object c : rl) {
                    if (c instanceof List<?> pt && pt.size() >= 2) {
                        ring.add(new double[]{
                                Double.parseDouble(String.valueOf(pt.get(0))),
                                Double.parseDouble(String.valueOf(pt.get(1)))});
                    }
                }
            }

            String slopeStr = "—", aspectStr = "—", labelStr = "—";
            double slope = Double.NaN, aspect = Double.NaN;
            if (ring.size() >= 3) {
                List<double[]> samples = samples(ring);
                double[] z = elevations(samples);
                if (z != null && z.length >= 3) {
                    double[] lat = new double[samples.size()];
                    double[] lon = new double[samples.size()];
                    double clat = 0, clon = 0;
                    for (int k = 0; k < samples.size(); k++) { clat += samples.get(k)[0]; clon += samples.get(k)[1]; }
                    clat /= samples.size(); clon /= samples.size();
                    double kx = 111320.0 * Math.cos(Math.toRadians(clat));
                    double ky = 110540.0;
                    double[] xs = new double[samples.size()], ys = new double[samples.size()];
                    for (int k = 0; k < samples.size(); k++) {
                        xs[k] = (samples.get(k)[1] - clon) * kx;   // 东向米
                        ys[k] = (samples.get(k)[0] - clat) * ky;   // 北向米
                    }
                    TerrainService.Slope s = TerrainService.fitPlane(xs, ys, z);
                    slope = Math.round(s.slopeDeg() * 10) / 10.0;
                    aspect = Double.isNaN(s.aspectDeg()) ? Double.NaN : Math.round(s.aspectDeg());
                    slopeStr = String.valueOf(slope);
                    aspectStr = Double.isNaN(aspect) ? "—" : String.valueOf((int) aspect);
                    labelStr = aspectLabel(aspect, slope);
                }
            }

            md.append("| ").append(i++).append(" | ").append(name).append(" | ").append(expectedMu)
                    .append(" | ").append(slopeStr).append(" | ").append(aspectStr)
                    .append(" | ").append(labelStr).append(" |   |   |\n");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("expectedAreaMu", expectedMu);
            row.put("computedSlopeDeg", Double.isNaN(slope) ? null : slope);
            row.put("computedAspectDeg", Double.isNaN(aspect) ? null : aspect);
            row.put("computedAspectLabel", labelStr);
            row.put("referenceSlopeDeg", null);
            row.put("referenceAspectDeg", null);
            computed.add(row);
        }

        System.out.println(md);
        System.out.println();
        System.out.println("说明：以上坡度/坡向是系统按 90m DEM 拟合的结果，供人工对照地形图核对。");
        System.out.println("认可后把值填进 data/geo-baseline.json 的 referenceSlopeDeg / referenceAspectDeg，");
        System.out.println("再跑 GeoBaselineTest 即自动计算一致率（验收 ≥90%）。");

        Files.writeString(Path.of("data", "geo-baseline-computed.json"),
                com.cropcalendar.util.Json.stringify(computed), StandardCharsets.UTF_8);
        System.out.println("\n已写 data/geo-baseline-computed.json（" + computed.size() + " 个地块）");
    }

    /** 采样点：4 个顶点 + 质心，共 5 个点，足够拟合平面（90m DEM 下再多也提升有限）。 */
    private static List<double[]> samples(List<double[]> ring) {
        List<double[]> out = new ArrayList<>(ring);
        double la = 0, lo = 0;
        for (double[] p : ring) { la += p[0]; lo += p[1]; }
        out.add(new double[]{ la / ring.size(), lo / ring.size() });
        return out;
    }

    /** 一次请求批量取高程。DEM 偶发返回 null，这里只保留有效值；全失败返回 null。 */
    private static double[] elevations(List<double[]> pts) {
        StringBuilder la = new StringBuilder(), lo = new StringBuilder();
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) { la.append(','); lo.append(','); }
            la.append(String.format("%.5f", pts.get(i)[0]));
            lo.append(String.format("%.5f", pts.get(i)[1]));
        }
        String url = "https://api.open-meteo.com/v1/elevation?latitude=" + la + "&longitude=" + lo;
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofMillis(8000))
                    .header("User-Agent", "agritime/1.0 (signoff-worksheet)")
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            Object o = JsonParser.parse(resp.body());
            if (!(o instanceof Map<?, ?> m) || !(m.get("elevation") instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            List<Double> z = new ArrayList<>();
            for (int i = 0; i < pts.size() && i < list.size(); i++) {
                if (list.get(i) instanceof Number n) z.add(n.doubleValue());
            }
            if (z.size() < 3) return null;
            double[] arr = new double[z.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = z.get(i);
            return arr;
        } catch (Exception e) {
            System.err.println("[worksheet] 高程获取失败：" + e.getMessage());
            return null;
        }
    }

    private static String aspectLabel(double deg, double slope) {
        if (Double.isNaN(deg) || slope < 1.0) return "平地";
        String[] names = {"北（阴坡）", "东北", "东", "东南", "南（阳坡）", "西南", "西", "西北", "北（阴坡）"};
        int idx = (int) Math.round(((deg % 360) / 45.0));
        if (idx > 8) idx = 8;
        return names[idx];
    }
}
