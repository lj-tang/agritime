package com.cropcalendar.service.user;

import com.cropcalendar.config.AppConfig;
import com.cropcalendar.model.User;
import com.cropcalendar.util.Json;
import com.cropcalendar.util.JsonParser;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号存储 —— 单个 JSON 文件，零依赖。
 *
 * 为什么不上数据库？项目铁律是「javac 编完就能跑」，装 MySQL 会打破这条。
 * 账号量级是比赛演示的几十上百个，单文件 + 全量重写完全够用；
 * 真上量时只需要把 persist() 换成数据库写入，调用方一行都不用改。
 *
 * 并发：所有读写都走 synchronized。写盘用「临时文件 + 原子 rename」，
 * 避免进程被杀时留下半截 JSON 把整站账号锁死。
 */
public final class UserStore {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PBKDF2_ITER = 120_000;

    private static final Object LOCK = new Object();
    private static Path file;
    private static byte[] secret;
    private static final Map<String, User> BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> ID_BY_NAME = new LinkedHashMap<>();
    private static boolean loaded = false;

    // ---------------- 对外接口 ----------------

    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (LOCK) {
            if (loaded) return;
            load();
            seedAdmin();
            loaded = true;
        }
    }

    /** 用户名不区分大小写：农民不会记得自己注册时有没有大写 */
    public static User findByName(String username) {
        ensureLoaded();
        synchronized (LOCK) {
            String id = ID_BY_NAME.get(norm(username));
            return id == null ? null : BY_ID.get(id);
        }
    }

    public static User findById(String id) {
        ensureLoaded();
        synchronized (LOCK) {
            return BY_ID.get(id);
        }
    }

    public static List<User> listAll() {
        ensureLoaded();
        synchronized (LOCK) {
            return new ArrayList<>(BY_ID.values());
        }
    }

    /**
     * 注册。用户名已存在时返回 null，由调用方回 409。
     */
    public static User create(String username, String password, String nickname, String role) {
        ensureLoaded();
        String name = norm(username);
        synchronized (LOCK) {
            if (ID_BY_NAME.containsKey(name)) return null;
            String id = "u_" + Long.toString(System.currentTimeMillis(), 36)
                    + Integer.toString(RANDOM.nextInt(1296), 36);
            User u = new User(id, username.trim(), nickname == null ? "" : nickname.trim(),
                    hash(password), role, now(), "", 0, false);
            BY_ID.put(id, u);
            ID_BY_NAME.put(name, id);
            persist();
            return u;
        }
    }

    public static boolean verify(User u, String password) {
        if (u == null || password == null || password.isEmpty()) return false;
        String[] parts = u.passwordHash().split("\\$");
        if (parts.length != 4) return false;
        int iter;
        try {
            iter = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expect = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(password, salt, iter, expect.length);
        // 定长比较，避免按字节提前返回泄露信息
        return MessageDigest.isEqual(expect, actual);
    }

    public static User touchLogin(User u) {
        synchronized (LOCK) {
            User updated = u.withLogin(now());
            BY_ID.put(updated.id(), updated);
            persist();
            return updated;
        }
    }

    /** 封号 / 解封。超管自己不能被封，否则后台就进不去了 */
    public static boolean setBanned(String userId, boolean banned) {
        ensureLoaded();
        synchronized (LOCK) {
            User u = BY_ID.get(userId);
            if (u == null || u.isAdmin()) return false;
            User updated = u.withBanned(banned);
            BY_ID.put(userId, updated);
            persist();
            return true;
        }
    }

    /** 升/降管理员。role 只接受 admin / user */
    public static boolean setRole(String userId, String role) {
        ensureLoaded();
        if (!"admin".equals(role) && !"user".equals(role)) return false;
        synchronized (LOCK) {
            User u = BY_ID.get(userId);
            if (u == null) return false;
            BY_ID.put(userId, new User(u.id(), u.username(), u.nickname(), u.passwordHash(),
                    role, u.createdAt(), u.lastLoginAt(), u.loginCount(), u.banned()));
            persist();
            return true;
        }
    }

    /** 超管重置密码：不需要旧密码，用于用户忘记密码 */
    public static boolean resetPassword(String userId, String password) {
        ensureLoaded();
        if (password == null || password.length() < 6) return false;
        synchronized (LOCK) {
            User u = BY_ID.get(userId);
            if (u == null) return false;
            BY_ID.put(userId, new User(u.id(), u.username(), u.nickname(), hash(password),
                    u.role(), u.createdAt(), u.lastLoginAt(), u.loginCount(), u.banned()));
            persist();
            return true;
        }
    }

    /** 删号。连同这个人的业务数据一起清掉，避免留下孤儿文件 */
    public static boolean delete(String userId) {
        ensureLoaded();
        synchronized (LOCK) {
            User u = BY_ID.get(userId);
            if (u == null) return false;
            BY_ID.remove(userId);
            ID_BY_NAME.remove(norm(u.username()));
            persist();
            try {
                com.cropcalendar.service.user.UserDataStore.delete(userId);
            } catch (Exception e) {
                System.err.println("[user] 清理用户数据失败：" + e.getMessage());
            }
            return true;
        }
    }

    public static int count() {
        ensureLoaded();
        synchronized (LOCK) {
            return BY_ID.size();
        }
    }

    public static int countCreatedSince(String isoPrefix) {
        ensureLoaded();
        synchronized (LOCK) {
            int n = 0;
            for (User u : BY_ID.values()) {
                if (u.createdAt().compareTo(isoPrefix) >= 0) n++;
            }
            return n;
        }
    }

    public static int countActiveSince(String isoPrefix) {
        ensureLoaded();
        synchronized (LOCK) {
            int n = 0;
            for (User u : BY_ID.values()) {
                if (!u.lastLoginAt().isBlank() && u.lastLoginAt().compareTo(isoPrefix) >= 0) n++;
            }
            return n;
        }
    }

    /** 给令牌签名用的密钥，首次启动随机生成并落盘，重启后已发的令牌仍有效 */
    public static byte[] secret() {
        ensureLoaded();
        return secret;
    }

    public static String now() {
        return LocalDateTime.now().format(TS);
    }

    // ---------------- 内部实现 ----------------

    private static String norm(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, PBKDF2_ITER, 32);
        return "pbkdf2$" + PBKDF2_ITER + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(dk);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iter, int len) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iter, len * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码摘要计算失败", e);
        }
    }

    private static Path path() {
        if (file != null) return file;
        String configured = AppConfig.authStorePath();
        file = Path.of(configured.isBlank() ? "var/users.json" : configured);
        return file;
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        Path p = path();
        try {
            if (!Files.exists(p)) {
                secret = randomSecret();
                return;
            }
            String text = Files.readString(p, StandardCharsets.UTF_8);
            Object root = JsonParser.parse(text);
            if (!(root instanceof Map<?, ?> m)) {
                System.err.println("[user] users.json 结构异常，按空库启动");
                secret = randomSecret();
                return;
            }
            Map<String, Object> rm = (Map<String, Object>) m;
            Object sec = rm.get("secret");
            secret = sec instanceof String s ? Base64.getDecoder().decode(s) : randomSecret();

            Object users = rm.get("users");
            if (users instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> um)) continue;
                    Map<String, Object> u = (Map<String, Object>) um;
                    User user = new User(
                            str(u, "id"), str(u, "username"), str(u, "nickname"),
                            str(u, "passwordHash"), str(u, "role"),
                            str(u, "createdAt"), str(u, "lastLoginAt"),
                            (int) num(u, "loginCount"), bool(u, "banned"));
                    BY_ID.put(user.id(), user);
                    ID_BY_NAME.put(norm(user.username()), user.id());
                }
            }
            System.out.println("[user] 已加载 " + BY_ID.size() + " 个账号");
        } catch (Exception e) {
            System.err.println("[user] 读取 users.json 失败：" + e.getMessage() + "，按空库启动");
            secret = randomSecret();
        }
    }

    /** 预置超管：没有它，后台永远进不去 */
    private static void seedAdmin() {
        if (!BY_ID.isEmpty()) return;
        String pwd = AppConfig.authAdminPassword();
        if (pwd == null || pwd.isBlank()) {
            pwd = "agritime@2026";
        }
        User admin = new User("u_super_admin", "super_admin", "超级管理员",
                hash(pwd), "admin", now(), "", 0, false);
        BY_ID.put(admin.id(), admin);
        ID_BY_NAME.put("super_admin", admin.id());
        persist();
        System.out.println("[user] 已创建预置超管 super_admin（密码见 auth.admin.password）");
    }

    private static void persist() {
        Path p = path();
        try {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            List<String> items = new ArrayList<>();
            for (User u : BY_ID.values()) {
                items.add(Json.obj(
                        "id", Json.str(u.id()),
                        "username", Json.str(u.username()),
                        "nickname", Json.str(u.nickname()),
                        "passwordHash", Json.str(u.passwordHash()),
                        "role", Json.str(u.role()),
                        "createdAt", Json.str(u.createdAt()),
                        "lastLoginAt", Json.str(u.lastLoginAt()),
                        "loginCount", String.valueOf(u.loginCount()),
                        "banned", String.valueOf(u.banned())));
            }
            String body = Json.obj(
                    "version", "1",
                    "secret", Json.str(Base64.getEncoder().encodeToString(secret)),
                    "users", Json.arr(items));

            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[user] 写 users.json 失败：" + e.getMessage());
        }
    }

    private static byte[] randomSecret() {
        byte[] s = new byte[32];
        RANDOM.nextBytes(s);
        return s;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : "";
    }

    private static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s.trim());
        return false;
    }

    private static double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private UserStore() {
    }
}
