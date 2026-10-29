package com.cropcalendar.api;

import com.cropcalendar.user.Auth;
import com.cropcalendar.user.User;
import com.cropcalendar.user.UserStore;
import com.cropcalendar.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /api/auth/* —— 注册、登录、退出、当前用户（T10）
 *
 *   POST /api/auth/register  {username, password, nickname?}
 *   POST /api/auth/login     {username, password}
 *   POST /api/auth/logout
 *   GET  /api/auth/me
 *
 * 会话走 HttpOnly Cookie（见 user/Auth），前端拿不到令牌，
 * 也就没有「把 token 存 localStorage 被 XSS 偷走」这条路径。
 */
public class AuthHandler implements HttpHandler {

    /** 同一用户名的连续失败次数：5 次锁 60 秒，挡一下弱口令撞库 */
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 60_000;
    private static final Map<String, int[]> FAILS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOCK_UNTIL = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpSupport.cors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/register")) {
            register(ex);
        } else if (path.endsWith("/login")) {
            login(ex);
        } else if (path.endsWith("/logout")) {
            logout(ex);
        } else if (path.endsWith("/me")) {
            me(ex);
        } else {
            HttpSupport.write(ex, 404, HttpSupport.error(404, "接口不存在"));
        }
    }

    private void register(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        if (!com.cropcalendar.config.AppConfig.authAllowRegister()) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "当前未开放注册，请联系管理员"));
            return;
        }

        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String username = HttpSupport.field(body, "username");
        String password = HttpSupport.field(body, "password");
        String nickname = HttpSupport.field(body, "nickname");

        String bad = validate(username, password);
        if (bad != null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, bad));
            return;
        }

        User u = UserStore.create(username, password, nickname, "user");
        if (u == null) {
            HttpSupport.write(ex, 409, HttpSupport.error(409, "该用户名已被注册"));
            return;
        }

        Auth.setCookie(ex, Auth.issue(u));
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("user", u.toJson())));
    }

    private void login(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }

        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String username = HttpSupport.field(body, "username");
        String password = HttpSupport.field(body, "password");
        if (username.isEmpty() || password.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请输入用户名和密码"));
            return;
        }

        String key = username.toLowerCase();
        Long until = LOCK_UNTIL.get(key);
        if (until != null && until > System.currentTimeMillis()) {
            long left = (until - System.currentTimeMillis()) / 1000 + 1;
            HttpSupport.write(ex, 429, HttpSupport.error(429, "密码错误次数过多，" + left + " 秒后再试"));
            return;
        }

        User u = UserStore.findByName(username);
        if (u == null || !UserStore.verify(u, password)) {
            int[] fails = FAILS.computeIfAbsent(key, k -> new int[1]);
            fails[0]++;
            if (fails[0] >= MAX_FAILS) {
                LOCK_UNTIL.put(key, System.currentTimeMillis() + LOCK_MS);
                FAILS.remove(key);
                HttpSupport.write(ex, 429, HttpSupport.error(429, "密码错误次数过多，请 60 秒后再试"));
                return;
            }
            // 不区分「用户不存在」和「密码错误」，避免枚举账号
            HttpSupport.write(ex, 401, HttpSupport.error(401, "用户名或密码不正确"));
            return;
        }

        FAILS.remove(key);
        LOCK_UNTIL.remove(key);

        User updated = UserStore.touchLogin(u);
        Auth.setCookie(ex, Auth.issue(updated));
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("user", updated.toJson())));
    }

    private void logout(HttpExchange ex) throws IOException {
        Auth.clearCookie(ex);
        HttpSupport.write(ex, 200, HttpSupport.ok("null"));
    }

    private void me(HttpExchange ex) throws IOException {
        User u = Auth.current(ex);
        if (u == null) {
            HttpSupport.write(ex, 401, HttpSupport.error(401, "未登录"));
            return;
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj("user", u.toJson())));
    }

    /** 用户名 3~20 位（中英文数字下划线），密码至少 6 位 */
    private static String validate(String username, String password) {
        if (username.isEmpty()) return "请输入用户名";
        if (username.length() < 3 || username.length() > 20) return "用户名需 3~20 个字符";
        if (!username.matches("[\\w\\u4e00-\\u9fa5]+")) return "用户名只能包含中英文、数字和下划线";
        if (password.length() < 6) return "密码至少 6 位";
        if (password.length() > 64) return "密码过长";
        return null;
    }
}
