package com.cropcalendar.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件后端：与 V5 现有实现完全一致 —— 一个用户一个文件，原子替换写入。
 * 作为默认后端，行为不变，保证切换 MySQL 失败时能原样回落。
 */
public final class FileBlobStore implements BlobStore {

    private final Path dir;

    public FileBlobStore(String dir) {
        this.dir = Path.of(dir == null || dir.isBlank() ? "var/userdata" : dir);
    }

    @Override
    public String backend() {
        return "file";
    }

    private Path pathOf(String key) {
        String safe = key == null ? "anonymous" : key.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return dir.resolve(safe + ".json");
    }

    @Override
    public String read(String key) {
        Path p = pathOf(key);
        try {
            if (!Files.exists(p)) return "{}";
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[store:file] 读取失败 " + key + "：" + e.getMessage());
            return "{}";
        }
    }

    @Override
    public void write(String key, String json) throws IOException {
        Path p = pathOf(key);
        Path parent = p.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(pathOf(key));
    }

    @Override
    public java.util.List<String> listKeys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            if (!Files.isDirectory(dir)) return out;
            try (java.util.stream.Stream<Path> s = Files.list(dir)) {
                for (Path p : s.toList()) {
                    String fn = p.getFileName().toString();
                    if (fn.endsWith(".json")) out.add(fn.substring(0, fn.length() - ".json".length()));
                }
            }
        } catch (IOException e) {
            System.err.println("[store:file] 枚举键失败：" + e.getMessage());
        }
        return out;
    }
}
