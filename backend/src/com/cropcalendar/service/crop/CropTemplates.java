package com.cropcalendar.service.crop;

import com.cropcalendar.compute.ComputeStub.RawNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 类别通用规程（V5.3）：给没有实测规程的作物兜一份"能照着做"的日历。
 *
 * 背景：作物属性库有 400 种，但人工校核过的规程只有十几种。其余作物原先走大模型，
 * 既慢（30~40 秒）又容易给出不合农艺的结果 —— 实测"苹果"被排成
 * "3 月播种 → 9 月收获"，而苹果是多年生果树，根本不存在当年播种当年收。
 *
 * 这里按**作物类别**给模板，用 totalDays 缩放节点位置：
 *   · 一年生：播期到收获，节点按生育期比例铺开
 *   · 多年生：按自然年铺开（萌芽/开花/坐果/采收/采后/越冬），不出现"播种"
 *
 * 模板是**推算值**，不是实测物候。调用方必须如实标注 dataSource=template，
 * 不能和十几种校核过的规程混为一谈。
 */
public final class CropTemplates {

    private CropTemplates() {
    }

    /** 作物类别。用于选模板，也用于前端文案（"适宜定植期" vs "适宜播期"）。 */
    public enum Group {
        CEREAL("禾谷类", "播种"),
        OIL("油料", "播种"),
        LEGUME("豆类", "播种"),
        LEAFY("叶菜", "播种"),
        FRUIT_VEG("果菜", "播种"),
        ROOT("根茎类", "播种"),
        ALLIUM("葱蒜类", "播种"),
        CASH("经济作物", "播种"),
        PERENNIAL_FRUIT("多年生果树", "定植"),
        TEA("茶", "定植"),
        MUSHROOM("食用菌", "接种"),
        MEDICINAL("药材", "播种"),
        FLOWER("花卉", "播种或定植"),
        FORAGE("饲草", "播种"),
        AQUATIC("水生作物", "定植"),
        SPICE("香料", "定植"),
        GENERIC("通用", "播种");

        private final String label;
        private final String action;

        Group(String label, String action) {
            this.label = label;
            this.action = action;
        }

        public String label() {
            return label;
        }

        /** 该类别对应的"种下去"这个动作叫什么。 */
        public String action() {
            return action;
        }

        public boolean perennial() {
            return this == PERENNIAL_FRUIT || this == TEA || this == SPICE;
        }
    }

    private static final String[] MUSHROOM_NAMES = {
            "香菇", "平菇", "金针菇", "杏鲍菇", "白灵菇", "茶树菇", "猴头菇", "竹荪",
            "木耳", "银耳", "松茸", "羊肚菌", "鸡枞", "灰树花", "双孢菇", "灵芝"
    };

    private static final String[] SPICE_NAMES = {
            "花椒", "八角", "桂皮", "丁香", "胡椒", "肉豆蔻", "草果", "砂仁", "白蔻",
            "孜然", "小茴香", "大茴香", "香叶", "咖喱叶", "迷迭香", "百里香", "牛至",
            "鼠尾草", "番红花", "紫苏", "罗勒"
    };

    private static final String[] MEDICINAL_NAMES = {
            "枸杞", "金银花", "黄芪", "人参", "当归", "三七", "板蓝根", "薄荷", "薰衣草",
            "桔梗", "麦冬", "柴胡", "白术", "白芍", "甘草", "苦参", "黄连", "黄柏", "杜仲",
            "厚朴", "肉桂", "五味子", "决明子", "薏苡仁", "车前子", "菟丝子", "蛇床子",
            "山茱萸", "连翘", "丹参", "玄参", "生地", "白芷", "藿香", "荆芥", "防风",
            "羌活", "独活", "前胡", "党参", "艾草", "益母草", "车前草", "鱼腥草", "穿心莲",
            "蒲公英", "马齿苋"
    };

    private static final String[] FLOWER_NAMES = {
            "玫瑰", "月季", "牡丹", "芍药", "菊花", "康乃馨", "百合", "郁金香", "茉莉",
            "桂花", "栀子", "杜鹃", "茶花", "梅花", "兰花", "水仙", "荷花", "睡莲", "鸢尾",
            "大丽花", "一串红", "万寿菊", "孔雀草", "三色堇", "矮牵牛", "金鱼草", "石竹",
            "满天星", "薰衣草"
    };

    private static final String[] CEREAL_NAMES = {
            "稻", "麦", "玉米", "高粱", "谷子", "黍子", "糜子", "青稞", "燕麦", "莜麦",
            "黑麦", "荞麦", "藜麦", "薏米", "薏苡仁", "红米", "黑米", "紫米", "糯米",
            "粳稻", "籼稻", "旱稻", "陆稻"
    };

    private static final String[] OIL_NAMES = {"油菜", "花生", "芝麻", "向日葵", "葵花", "红花"};

    private static final String[] CASH_NAMES = {
            "棉花", "甘蔗", "甜菜", "烟草", "烤烟", "桑", "苎麻", "亚麻", "橡胶", "咖啡", "可可"
    };

    private static final String[] AQUATIC_NAMES = {
            "莲藕", "茭白", "莼菜", "蒲菜", "菱角", "芡实", "水芹", "莲子"
    };

    private static final String[] LEGUME_NAMES = {
            "大豆", "黑豆", "绿豆", "红小豆", "赤小豆", "赤豆", "豇豆", "豌豆", "蚕豆",
            "扁豆", "芸豆", "四季豆", "菜豆", "刀豆", "鹰嘴豆", "小扁豆", "木豆", "饭豆",
            "眉豆", "龙爪豆", "草豌豆", "红花菜豆", "龙芽豆", "甜豌豆", "食荚豌豆", "皱粒豌豆"
    };

    private static final String[] ROOT_NAMES = {
            "萝卜", "胡萝卜", "芋头", "山药", "生姜", "莴笋", "雪莲果", "红薯", "马铃薯",
            "大头菜", "甜菜"
    };

    private static final String[] ALLIUM_NAMES = {
            "大蒜", "大葱", "洋葱", "香葱", "分葱", "韭菜", "蒜苗", "蒜薹", "蒜黄", "韭黄",
            "姜芽", "葱芽"
    };

    private static final String[] FRUIT_VEG_NAMES = {
            "番茄", "茄子", "辣椒", "黄瓜", "冬瓜", "南瓜", "丝瓜", "苦瓜", "西葫芦", "西瓜",
            "甜瓜", "哈密瓜", "豆角", "秋葵", "佛手瓜", "彩椒", "尖椒", "朝天椒", "小米椒",
            "菜椒", "青椒"
    };

    private static final String[] LEAFY_NAMES = {
            "白菜", "菠菜", "芹菜", "生菜", "空心菜", "苋菜", "茼蒿", "甘蓝", "芥菜",
            "雪里蕻", "榨菜", "儿菜", "菜心", "菜薹", "菜苔", "娃娃菜", "快菜", "鸡毛菜",
            "油麦菜", "苦菊", "芝麻菜", "香椿", "蕨菜", "紫背天葵", "抱子甘蓝", "羽衣甘蓝",
            "紫甘蓝", "芥蓝", "奶白菜", "乌塌菜", "木耳菜", "荠菜", "马兰头", "灰灰菜",
            "花菜", "花椰菜", "西兰花", "黄花菜", "竹笋", "芦笋", "莼菜"
    };

    private static final String[] FRUIT_TREE_NAMES = {
            "苹果", "梨", "桃", "李", "杏", "樱桃", "葡萄", "柑橘", "橙", "柚", "柠檬",
            "猕猴桃", "石榴", "柿", "枣", "芒果", "百香果", "蓝莓", "草莓", "杨梅", "梅",
            "板栗", "核桃", "巴旦木", "开心果", "碧根果", "夏威夷果", "腰果", "榛子",
            "松子", "桂圆", "荔枝", "龙眼", "山竹", "榴莲", "菠萝", "凤梨", "椰子", "芭乐",
            "番石榴", "莲雾", "杨桃", "释迦", "无花果", "桑葚", "黑莓", "树莓", "蔓越莓",
            "鹅莓", "金桔", "橘", "柑", "金枣", "酸枣", "油奈", "布朗", "西梅"
    };

    /** 判断作物类别。先按明确的名字表，再按属性里的类别文案，最后按生育期兜底。 */
    public static Group groupOf(CropProperties.Props p) {
        if (p == null) return Group.GENERIC;
        String name = p.displayName() == null ? "" : p.displayName();
        String temp = p.tempRequirement() == null ? "" : p.tempRequirement();
        boolean perennial = "perennial".equals(p.lifespan());

        if (containsAny(name, MUSHROOM_NAMES) || temp.contains("喜湿避光")) return Group.MUSHROOM;
        // 茶树菇是菌、茶花是花，都要在"茶"之前判掉
        if (containsAny(name, AQUATIC_NAMES)) return Group.AQUATIC;
        if (containsAny(name, FLOWER_NAMES)) return Group.FLOWER;
        if (name.contains("茶") && !name.contains("菜")) return Group.TEA;
        if (containsAny(name, SPICE_NAMES)) return Group.SPICE;
        if (containsAny(name, MEDICINAL_NAMES) || temp.contains("药用作物")) return Group.MEDICINAL;
        if (containsAny(name, CEREAL_NAMES)) return Group.CEREAL;
        if (containsAny(name, OIL_NAMES)) return Group.OIL;
        if (containsAny(name, CASH_NAMES)) return Group.CASH;
        if (containsAny(name, ROOT_NAMES)) return Group.ROOT;
        if (containsAny(name, ALLIUM_NAMES)) return Group.ALLIUM;
        if (containsAny(name, FRUIT_VEG_NAMES)) return Group.FRUIT_VEG;
        if (containsAny(name, LEGUME_NAMES)) return Group.LEGUME;
        if (containsAny(name, LEAFY_NAMES)) return Group.LEAFY;
        if (containsAny(name, FRUIT_TREE_NAMES)) return Group.PERENNIAL_FRUIT;

        if (temp.contains("禾谷")) return Group.CEREAL;
        if (temp.contains("观赏")) return Group.FLOWER;

        if (perennial) {
            // 多年生但识别不出具体种类：按果树管理最稳妥（都不会"当年播种当年收"）
            return Group.PERENNIAL_FRUIT;
        }
        if (p.totalDays() <= 90) return Group.LEAFY;
        if (p.totalDays() <= 150) return Group.FRUIT_VEG;
        return Group.GENERIC;
    }

    private static boolean containsAny(String text, String[] keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    /**
     * 生成该类别的通用规程。
     *
     * @param cropId 作物 id（写进 RawNode）
     * @param p      作物属性
     */
    public static List<RawNode> build(String cropId, CropProperties.Props p) {
        if (cropId == null || cropId.isBlank()) return List.of();
        Group g = groupOf(p);
        // 多年生按自然年铺，一年生的按生育期铺
        int span = g.perennial() ? 365 : Math.max(45, p == null ? 120 : p.totalDays());

        List<double[]> plan = planFor(g);
        List<RawNode> out = new ArrayList<>();
        int last = -1;
        for (double[] step : plan) {
            int day = (int) Math.round(span * step[0]);
            if (day <= last) day = last + 1;   // 严格递增，比例算不准时也不倒序
            last = day;
            out.add(new RawNode(cropId, opType(step[1]), day, title(step[1], g)));
        }
        return out;
    }

    /**
     * 每个类别的时间轴：{ 位置比例, 动作码 }。
     * 位置比例是"占全生育期（或自然年）的比例"，不是具体天数。
     */
    private static List<double[]> planFor(Group g) {
        List<double[]> p = new ArrayList<>();
        switch (g) {
            case CEREAL -> {
                add(p, 0.00, 1); add(p, 0.08, 2); add(p, 0.22, 4); add(p, 0.38, 3);
                add(p, 0.52, 4); add(p, 0.64, 5); add(p, 0.78, 3); add(p, 0.95, 9);
            }
            case OIL -> {
                add(p, 0.00, 1); add(p, 0.10, 2); add(p, 0.28, 4); add(p, 0.50, 5);
                add(p, 0.72, 3); add(p, 0.92, 9);
            }
            case LEGUME -> {
                add(p, 0.00, 1); add(p, 0.08, 2); add(p, 0.25, 4); add(p, 0.50, 5);
                add(p, 0.75, 3); add(p, 0.92, 9);
            }
            case LEAFY -> {
                add(p, 0.00, 1); add(p, 0.12, 6); add(p, 0.30, 4); add(p, 0.52, 3);
                add(p, 0.66, 5); add(p, 0.86, 9);
            }
            case FRUIT_VEG -> {
                add(p, 0.00, 1); add(p, 0.28, 7); add(p, 0.42, 4); add(p, 0.58, 5);
                add(p, 0.76, 8); add(p, 0.95, 9);
            }
            case ROOT -> {
                add(p, 0.00, 1); add(p, 0.15, 6); add(p, 0.35, 4); add(p, 0.55, 10);
                add(p, 0.75, 3); add(p, 0.92, 9);
            }
            case ALLIUM -> {
                add(p, 0.00, 1); add(p, 0.15, 2); add(p, 0.40, 4); add(p, 0.62, 3);
                add(p, 0.78, 5); add(p, 0.93, 9);
            }
            case CASH -> {
                add(p, 0.00, 1); add(p, 0.12, 2); add(p, 0.30, 4); add(p, 0.52, 5);
                add(p, 0.70, 3); add(p, 0.94, 9);
            }
            case PERENNIAL_FRUIT -> {
                add(p, 0.08, 11); add(p, 0.20, 12); add(p, 0.30, 13); add(p, 0.42, 4);
                add(p, 0.55, 3); add(p, 0.72, 9); add(p, 0.85, 4); add(p, 0.95, 14);
            }
            case TEA -> {
                add(p, 0.10, 4); add(p, 0.22, 8); add(p, 0.38, 8); add(p, 0.55, 8);
                add(p, 0.68, 15); add(p, 0.82, 4); add(p, 0.94, 14);
            }
            case MUSHROOM -> {
                add(p, 0.00, 16); add(p, 0.18, 17); add(p, 0.45, 18); add(p, 0.62, 8);
                add(p, 0.82, 3); add(p, 0.95, 19);
            }
            case MEDICINAL -> {
                add(p, 0.00, 1); add(p, 0.20, 6); add(p, 0.42, 4); add(p, 0.62, 15);
                add(p, 0.88, 9);
            }
            case FLOWER -> {
                add(p, 0.00, 1); add(p, 0.20, 7); add(p, 0.38, 13); add(p, 0.55, 4);
                add(p, 0.75, 8); add(p, 0.92, 14);
            }
            case FORAGE -> {
                add(p, 0.00, 1); add(p, 0.15, 2); add(p, 0.40, 20); add(p, 0.65, 20);
                add(p, 0.88, 14);
            }
            case AQUATIC -> {
                add(p, 0.00, 7); add(p, 0.20, 3); add(p, 0.40, 4); add(p, 0.65, 5);
                add(p, 0.90, 9);
            }
            case SPICE -> {
                add(p, 0.15, 12); add(p, 0.35, 13); add(p, 0.70, 9); add(p, 0.90, 15);
            }
            default -> {
                add(p, 0.00, 1); add(p, 0.15, 2); add(p, 0.35, 4); add(p, 0.60, 5);
                add(p, 0.90, 9);
            }
        }
        return p;
    }

    private static void add(List<double[]> list, double at, int code) {
        list.add(new double[]{at, code});
    }

    /**
     * 动作码 → opType。
     *
     * 只用系统既有枚举（sowing/emergence/irrigation/fertilize/pesticide/harvest/other）
     * 加协议库里已有的 overwinter/prune —— 下游的天气打分、作业指导、图标都按这几个值分派，
     * 自造新枚举会让节点落进"无指导"的空档。语义靠标题表达，不靠新枚举。
     */
    private static String opType(double code) {
        return switch ((int) code) {
            case 1, 16 -> "sowing";        // 播种 / 接种（食用菌的"种下去"）
            case 2 -> "emergence";
            case 3 -> "irrigation";
            case 4 -> "fertilize";
            case 5 -> "pesticide";
            case 8, 9, 20 -> "harvest";    // 采收 / 末茬 / 刈割
            case 11 -> "prune";
            case 14 -> "overwinter";
            default -> "other";            // 间苗、移栽、培土、疏果、除草等田间作业
        };
    }

    /** 动作码 → 节点标题。带类别语境的文案，避免"苹果：播种"这类荒谬表达。 */
    private static String title(double code, Group g) {
        return switch ((int) code) {
            case 1 -> g.perennial() ? "定植" : "播种";
            case 2 -> "出苗 / 齐苗";
            case 3 -> "灌溉 / 排涝";
            case 4 -> "追肥";
            case 5 -> "病虫害防治";
            case 6 -> "间苗 / 定苗";
            case 7 -> g.perennial() ? "移栽定植" : "移栽定植";
            case 8 -> g == Group.TEA ? "采摘" : "采收";
            case 9 -> g == Group.TEA ? "采摘收尾" : "采收（末茬）";
            case 10 -> "中耕培土";
            case 11 -> "萌芽前修剪 / 清园";
            case 12 -> "萌芽开花期管理";
            case 13 -> "疏花疏果 / 保果";
            case 14 -> "越冬防寒";
            case 15 -> "中耕除草 / 修剪";
            case 16 -> "配料装袋 / 接种";
            case 17 -> "发菌期管理";
            case 18 -> "催蕾 / 出菇";
            case 19 -> "清棚消毒";
            case 20 -> "刈割";
            default -> "田间管理";
        };
    }
}
