package com.cropcalendar.geo;

import com.cropcalendar.util.JsonParser;

import java.util.Map;

/** 离线解析回归：用固定报文验证高德/腾讯字段抽取，不依赖网络与 key。 */
public class GeoParseTest {

    static int fail = 0;

    public static void main(String[] args) {
        // 1. 高德：neighborhood 命中（商务住宅）
        check("amap-neighborhood",
                parseAmap("{\"status\":\"1\",\"regeocode\":{\"addressComponent\":{" +
                        "\"province\":\"北京市\",\"city\":\"北京市\",\"district\":\"朝阳区\",\"township\":\"望京街道\"," +
                        "\"neighborhood\":{\"name\":\"望京SOHO\",\"type\":\"商务住宅\"}," +
                        "\"building\":{\"name\":\"望京SOHO\"},\"streetNumber\":{\"street\":\"阜通东大街\"}}," +
                        "\"aois\":[{\"name\":\"望京SOHO\"}],\"pois\":[{\"name\":\"望京SOHO\"}]}}"),
                "望京SOHO（望京街道 · 朝阳区 · 北京市）", "community", "望京SOHO");

        // 2. 高德：住宅小区 + 省市区镇四级
        check("amap-community",
                parseAmap("{\"status\":\"1\",\"regeocode\":{\"addressComponent\":{" +
                        "\"province\":\"山东省\",\"city\":\"济宁市\",\"district\":\"金乡县\",\"township\":\"金乡街道\"," +
                        "\"neighborhood\":{\"name\":\"金乡县锦绣家园\"},\"building\":{},\"streetNumber\":{}}," +
                        "\"aois\":[],\"pois\":[]}}"),
                "金乡县锦绣家园（金乡街道 · 金乡县 · 济宁市 · 山东省）", "community", "金乡县锦绣家园");

        // 3. 高德：直辖市 city 返回空数组，且 neighborhood 为空 → 落到 aois
        check("amap-aois-fallback",
                parseAmap("{\"status\":\"1\",\"regeocode\":{\"addressComponent\":{" +
                        "\"province\":\"上海市\",\"city\":[],\"district\":\"浦东新区\",\"township\":\"张江镇\"," +
                        "\"neighborhood\":[],\"building\":[],\"streetNumber\":[]}," +
                        "\"aois\":[{\"name\":\"张江汤臣豪园\"}],\"pois\":[]}}"),
                "张江汤臣豪园（张江镇 · 浦东新区 · 上海市）", "community", "张江汤臣豪园");

        // 4. 腾讯：status 是数字 0，recommend 直接给小区级
        check("tencent-recommend",
                parseTencent("{\"status\":0,\"message\":\"query ok\",\"result\":{" +
                        "\"formatted_addresses\":{\"recommend\":\"望京SOHO\",\"rough\":\"北京市朝阳区\"}," +
                        "\"address_component\":{\"province\":\"北京市\",\"city\":\"北京市\",\"district\":\"朝阳区\",\"street\":\"望京街道\"}," +
                        "\"address_reference\":{\"landmark_l2\":{\"title\":\"望京SOHO\"}}," +
                        "\"pois\":[{\"title\":\"望京SOHO\"}]}}"),
                "望京SOHO", "community", "望京SOHO");

        // 5. 上游报错时返回 null，由调用方降级
        expectNull("amap-error", parseAmap("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\"}"));
        expectNull("tencent-error", parseTencent("{\"status\":311,\"message\":\"key格式错误\"}"));
        expectNull("amap-null", GeoResolver.parseAmap(null));

        System.out.println(fail == 0 ? "ALL PASS" : (fail + " FAILED"));
        if (fail > 0) System.exit(1);
    }

    @SuppressWarnings("unchecked")
    private static GeoResolver.Place parseAmap(String json) {
        return GeoResolver.parseAmap((Map<String, Object>) JsonParser.parse(json));
    }

    @SuppressWarnings("unchecked")
    private static GeoResolver.Place parseTencent(String json) {
        return GeoResolver.parseTencent((Map<String, Object>) JsonParser.parse(json));
    }

    private static void check(String name, GeoResolver.Place p, String display, String level, String poi) {
        if (p == null) {
            fail++;
            System.out.println("FAIL " + name + " -> null");
            return;
        }
        boolean ok = display.equals(p.displayName()) && level.equals(p.level()) && poi.equals(p.name());
        if (!ok) fail++;
        System.out.println((ok ? "ok   " : "FAIL ") + name + " -> " + p.displayName()
                + " | level=" + p.level() + " | name=" + p.name());
    }

    private static void expectNull(String name, GeoResolver.Place p) {
        boolean ok = p == null;
        if (!ok) fail++;
        System.out.println((ok ? "ok   " : "FAIL ") + name + " -> " + (p == null ? "null" : p.displayName()));
    }
}
