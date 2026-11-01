package com.cropcalendar.api;

import com.cropcalendar.middleware.Auth;
import com.cropcalendar.model.User;
import com.cropcalendar.service.user.UserStore;
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
 *   POST /api/auth/password  {oldPassword, newPassword}   自助改密
 *   POST /api/auth/profile   {nickname}                    改昵称
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
    /** 「是否仍在用初始密码」的结果缓存，key 带密码哈希指纹 */
    private static final Map<String, Boolean> INIT_PWD = new ConcurrentHashMap<>();

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
        } else if (path.endsWith("/password")) {
            changePassword(ex);
        } else if (path.endsWith("/profile")) {
            updateProfile(ex);
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
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "user", u.toJson(),
                "mustChangePassword", "false")));
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

        if (u.banned()) {
            HttpSupport.write(ex, 403, HttpSupport.error(403, "该账号已被封禁，请联系管理员"));
            return;
        }

        FAILS.remove(key);
        LOCK_UNTIL.remove(key);

        User updated = UserStore.touchLogin(u);
        Auth.setCookie(ex, Auth.issue(updated));
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "user", updated.toJson(),
                "mustChangePassword", String.valueOf(mustChangePassword(updated)))));
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
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "user", u.toJson(),
                "mustChangePassword", String.valueOf(mustChangePassword(u)))));
    }

    /** 改密：先验旧密码，再按强度规则校验新密码；成功后重签令牌，当前会话不断 */
    private void changePassword(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        User u = Auth.current(ex);
        if (u == null) {
            HttpSupport.write(ex, 401, HttpSupport.error(401, "请先登录"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String oldPwd = HttpSupport.field(body, "oldPassword");
        String newPwd = HttpSupport.field(body, "newPassword");
        if (oldPwd.isEmpty()) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, "请输入当前密码"));
            return;
        }
        String err = UserStore.changePassword(u, oldPwd, newPwd);
        if (err != null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, err));
            return;
        }
        // 密码变了，旧令牌的指纹全部失效；当前这台设备要重新签一个，否则会被自己踢掉
        User fresh = UserStore.findById(u.id());
        Auth.setCookie(ex, Auth.issue(fresh));
        INIT_PWD.clear();
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "user", fresh.toJson(),
                "message", Json.str("密码已更新，其他设备需重新登录"))));
    }

    /** 改昵称：只改展示名，登录名不变 */
    private void updateProfile(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpSupport.write(ex, 405, HttpSupport.error(405, "只支持 POST"));
            return;
        }
        User u = Auth.current(ex);
        if (u == null) {
            HttpSupport.write(ex, 401, HttpSupport.error(401, "请先登录"));
            return;
        }
        Map<String, Object> body = HttpSupport.jsonBody(ex);
        String nickname = HttpSupport.field(body, "nickname");
        String err = UserStore.updateNickname(u, nickname);
        if (err != null) {
            HttpSupport.write(ex, 400, HttpSupport.error(400, err));
            return;
        }
        HttpSupport.write(ex, 200, HttpSupport.ok(Json.obj(
                "user", UserStore.findById(u.id()).toJson(),
                "message", Json.str("昵称已更新"))));
    }

    /**
     * 是否必须改密：管理员还在用预置初始密码时为 true。
     * PBKDF2 一次要 120ms 左右，me 又是高频接口，所以按「用户 + 密码哈希」缓存结果，
     * 改密后哈希变了缓存自然失效。
     */
    private static boolean mustChangePassword(User u) {
        if (u == null || !u.isAdmin()) return false;
        String key = u.id() + "@" + Integer.toHexString(u.passwordHash().hashCode());
        Boolean cached = INIT_PWD.get(key);
        if (cached != null) return cached;
        boolean v = UserStore.isInitialPassword(u);
        INIT_PWD.put(key, v);
        return v;
    }

    /** 用户名 3~20 位（中英文数字下划线），密码至少 6 位 */
    private static String validate(String username, String password) {
        if (username.isEmpty()) return "请输入用户名";
        if (username.length() < 3 || username.length() > 20) return "用户名需 3~20 个字符";
        if (!username.matches("[\\w\\u4e00-\\u9fa5]+")) return "用户名只能包含中英文、数字和下划线";
        // 与改密共用一套规则，避免「注册能过、改密不让过」
        String pwdProblem = UserStore.passwordProblem(password);
        if (pwdProblem != null) return pwdProblem.replace("新密码", "密码");
        return null;
    }
}
