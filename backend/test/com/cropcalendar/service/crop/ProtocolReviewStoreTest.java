package com.cropcalendar.service.crop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 后台「规程复核」存储单测：写入 / 覆盖 / 撤回 / 落盘可读。
 *
 * 复核结论是运维数据，写在 var/protocol-reviews.json（相对后端工作目录），
 * 不能被重新生成 crop-protocols.json 冲掉，所以这里单独验证持久化链路。
 */
public class ProtocolReviewStoreTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final Path FILE = Path.of("var/protocol-reviews.json");

    public static void main(String[] args) throws Exception {
        Files.deleteIfExists(FILE);

        check(ProtocolReviewStore.get("C366") == null, "初始没有复核记录");

        ProtocolReviewStore.Review r = ProtocolReviewStore.apply("C366", "reviewed", "super_admin", "人工核对通过");
        eq("reviewed", r.status(), "写入后返回通过状态");
        eq("super_admin", r.reviewer(), "记录复核人");
        eq(1, ProtocolReviewStore.all().size(), "复核记录数 1");
        eq("人工核对通过", ProtocolReviewStore.get("C366").note(), "备注原样保存");

        // 备注里带引号/换行，落盘必须是合法 JSON 且回读不丢内容
        String tricky = "他说\"挺好\"\n第二行\t制表符";
        ProtocolReviewStore.apply("C059", "rejected", "agritime-ops", tricky);
        eq(tricky, ProtocolReviewStore.get("C059").note(), "特殊字符备注回读一致");
        check(Files.size(FILE) > 0, "复核记录已写盘");
        String raw = Files.readString(FILE, StandardCharsets.UTF_8);
        check(raw.contains("\"reviews\""), "落盘结构包含 reviews");
        check(raw.contains("\\n"), "换行已转义为 \\n");

        // 通过 → 驳回 → 撤回（pending 等于删除覆盖）
        ProtocolReviewStore.apply("C366", "rejected", "super_admin", "节点日期不合理");
        eq("rejected", ProtocolReviewStore.get("C366").status(), "可以改判为驳回");
        ProtocolReviewStore.apply("C366", "pending", "super_admin", "");
        check(ProtocolReviewStore.get("C366") == null, "撤回后不再有覆盖记录");
        check(ProtocolReviewStore.get("C059") != null, "撤回只影响目标作物");
        eq(1, ProtocolReviewStore.all().size(), "撤回后记录数 1");

        // 非法状态必须拒绝，且不能写坏已有记录
        boolean threw = false;
        try {
            ProtocolReviewStore.apply("C366", "approved", "super_admin", "");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "非法状态应抛 IllegalArgumentException");
        eq(1, ProtocolReviewStore.all().size(), "非法写入不影响已有记录");

        Files.deleteIfExists(FILE);
        System.out.println("ProtocolReviewStoreTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + msg);
        }
    }

    private static void eq(Object expected, Object actual, String msg) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failed++;
            System.err.println("FAIL: " + msg + "（期望 " + expected + "，实际 " + actual + "）");
        } else {
            passed++;
        }
    }
}
