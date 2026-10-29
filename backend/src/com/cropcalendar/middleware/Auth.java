package com.cropcalendar.middleware;

import com.cropcalendar.model.User;
import com.cropcalendar.service.user.UserStore;
import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 会话：无状态签名令牌，塞在 HttpOnly Cookie 里。
 *
 * 为什么不用内存 Session？后端重启就全体掉线，演示时很致命；
 * 无状态令牌重启后仍然有效，代价只是「不能主动踢人」——
 * 对现在的场景（几十个演示账号）这个取舍划算。
 *
 * 令牌形如 base64url(userId|expireAt).base64url(HMAC-SHA256)，
 * 密钥存在 users.json 里，跟着账号库一起备份。
 */
public final class Auth {

    public static final String COOKIE = "agritime_token";
    /** 7 天，够覆盖整个演示周期 */
    private static final long TTL_SECONDS = 7 * 24 * 3600;

    public static String issue(User u) {
        long exp = System.currentTimeMillis() / 1000 + TTL_SECONDS;
        String payload = u.id() + "|" + exp;
        String p = b64(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + b64(sign(payload));
    }

    /** 取当前登录用户；未登录、令牌过期、签名不对都返回 null */
    public static User current(HttpExchange ex) {
        String token = readToken(ex);
        if (token == null) return null;

        int dot = token.indexOf('.');
        if (dot <= 0) return null;
        String p = token.substring(0, dot);
        String sig = token.substring(dot + 1);

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(p), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(sig))) {
            return null;
        }

        int bar = payload.lastIndexOf('|');
        if (bar <= 0) return null;
        String id = payload.substring(0, bar);
        long exp;
        try {
            exp = Long.parseLong(payload.substring(bar + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (exp < System.currentTimeMillis() / 1000) return null;

        User u = UserStore.findById(id);
        // 封号即时生效：不等令牌过期，下一次请求就登不进来
        if (u == null || u.banned()) return null;
        return u;
    }

    public static void setCookie(HttpExchange ex, String token) {
        ex.getResponseHeaders().add("Set-Cookie",
                COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + TTL_SECONDS);
    }

    public static void clearCookie(HttpExchange ex) {
        ex.getResponseHeaders().add("Set-Cookie",
                COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
    }

    private static String readToken(HttpExchange ex) {
        // 优先 Cookie（浏览器同源），其次 Authorization（脚本/调试用）
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || cookies.isBlank()) return null;
        for (String part : cookies.split(";")) {
            String s = part.trim();
            if (s.startsWith(COOKIE + "=")) {
                String v = s.substring(COOKIE.length() + 1).trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    private static byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(UserStore.secret(), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("令牌签名失败", e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private Auth() {
    }
}
