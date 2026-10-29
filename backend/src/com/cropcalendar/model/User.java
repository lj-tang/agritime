package com.cropcalendar.model;

/**
 * 一个账号。字段刻意保持扁平 —— 这份数据要落盘成 JSON，
 * 嵌套结构在零依赖手写序列化里只会增加出错面。
 *
 * passwordHash 的格式见 UserStore#hash：pbkdf2$迭代次数$盐$摘要，
 * 迭代次数写在串里，将来调高参数时老密码仍然能验证。
 */
public record User(
        String id,
        String username,
        String nickname,
        String passwordHash,
        String role,          // "admin" | "user"
        String createdAt,     // ISO-8601 秒级
        String lastLoginAt,
        int loginCount) {

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /** 对外输出时用：绝不带 passwordHash */
    public String toJson() {
        return com.cropcalendar.util.Json.obj(
                "id", com.cropcalendar.util.Json.str(id),
                "username", com.cropcalendar.util.Json.str(username),
                "nickname", com.cropcalendar.util.Json.str(nickname),
                "role", com.cropcalendar.util.Json.str(role),
                "createdAt", com.cropcalendar.util.Json.str(createdAt),
                "lastLoginAt", com.cropcalendar.util.Json.str(lastLoginAt),
                "loginCount", String.valueOf(loginCount));
    }

    public User withLogin(String at) {
        return new User(id, username, nickname, passwordHash, role, createdAt, at, loginCount + 1);
    }
}
