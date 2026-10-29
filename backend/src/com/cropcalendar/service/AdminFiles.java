package com.cropcalendar.service;

import com.cropcalendar.config.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 超管后台的文件管理（类似资源管理器）。
 *
 * 安全边界只有一条，但必须守住：**所有路径都被限制在 admin.fs.root 之内**。
 * 每次操作都把传入路径 normalize 成绝对路径，再判断是否以 root 开头，
 * 这样 ../ 穿越、绝对路径、软链跳到外面都会被挡住。
 *
 * 默认根目录是 /agritime（整站代码与数据）。想放开到整机就改
 * ADMIN_FS_ROOT=/，但那样等于把 root 权限的读写接口暴露在公网上，
 * 只有在内网/演示机上才建议这么做。
 */
public final class AdminFiles {

    /** 单文件读写上限 2MB —— 文本编辑再大就不该用网页了 */
    public static final long MAX_EDIT_BYTES = 2 * 1024 * 1024;

    private static final DateTimeFormatter MTIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public record Entry(String name, String path, boolean dir, long size, String mtime) {
    }

    public static Path root() {
        String configured = AppConfig.adminFsRoot();
        Path r = Path.of(configured.isBlank() ? "/agritime" : configured).toAbsolutePath().normalize();
        return r;
    }

    /** 把前端传来的相对路径解析成绝对路径，并确认没跑出根目录 */
    public static Path resolve(String rel) throws IOException {
        Path r = root();
        String clean = rel == null ? "" : rel.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        Path p = r.resolve(clean).normalize();
        if (!p.startsWith(r)) {
            throw new IOException("路径越界：" + rel);
        }
        return p;
    }

    /** 相对根目录的展示路径，统一用 / 开头 */
    public static String rel(Path p) {
        Path r = root();
        String s = r.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
        return s.isEmpty() ? "/" : "/" + s;
    }

    public static List<Entry> list(String relPath) throws IOException {
        Path dir = resolve(relPath);
        if (!Files.exists(dir)) throw new IOException("目录不存在");
        if (!Files.isDirectory(dir)) throw new IOException("不是目录");

        List<Entry> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> {
                try {
                    boolean isDir = Files.isDirectory(p);
                    long size = isDir ? 0 : Files.size(p);
                    out.add(new Entry(p.getFileName().toString(), rel(p), isDir, size,
                            MTIME.format(Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()))));
                } catch (IOException ignored) {
                    // 单个文件读不到属性就跳过，不让整个列表失败
                }
            });
        }
        // 目录在前，同类按名字排
        out.sort(Comparator.comparing(Entry::dir).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static String read(String relPath) throws IOException {
        Path p = resolve(relPath);
        if (!Files.isRegularFile(p)) throw new IOException("文件不存在");
        long size = Files.size(p);
        if (size > MAX_EDIT_BYTES) throw new IOException("文件过大（" + size + " 字节），请用命令行处理");
        byte[] bytes = Files.readAllBytes(p);
        for (byte b : bytes) {
            if (b == 0) throw new IOException("二进制文件，不支持在线编辑");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void write(String relPath, String content) throws IOException {
        Path p = resolve(relPath);
        if (Files.isDirectory(p)) throw new IOException("这是目录，不能当文件写");
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_EDIT_BYTES) {
            throw new IOException("内容过大");
        }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp-edit");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void create(String relPath, boolean dir) throws IOException {
        Path p = resolve(relPath);
        if (Files.exists(p)) throw new IOException("同名文件或目录已存在");
        if (dir) {
            Files.createDirectories(p);
        } else {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.createFile(p);
        }
    }

    /** 删除文件或空目录。非空目录不递归删 —— 误点一下删掉整棵源码树代价太大 */
    public static void delete(String relPath) throws IOException {
        Path p = resolve(relPath);
        if (p.equals(root())) throw new IOException("不能删除根目录");
        if (!Files.exists(p)) throw new IOException("文件不存在");
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.list(p)) {
                if (s.findAny().isPresent()) {
                    throw new IOException("目录非空，请先清空里面的内容");
                }
            }
            Files.delete(p);
        } else {
            Files.delete(p);
        }
    }

    private AdminFiles() {
    }
}
