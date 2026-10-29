-- ============================================================
-- 知农时 后端乙 计算与规则层 建表 SQL
-- 数据库: MySQL 8.x  字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS zhinongshi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zhinongshi;

-- 表1: crop_procedure 作物农事规程表
DROP TABLE IF EXISTS crop_procedure;
CREATE TABLE crop_procedure (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    crop_id         VARCHAR(16)  NOT NULL COMMENT '作物品种标识',
    crop_name       VARCHAR(32)  NOT NULL COMMENT '作物名称',
    job_name        VARCHAR(64)  NOT NULL COMMENT '作业名称',
    job_type        VARCHAR(16)  NOT NULL COMMENT '作业类型: SPRAY/FERTILIZE/IRRIGATE/HARVEST',
    days_after_sowing INT        NOT NULL COMMENT '播种后相对天数',
    description     VARCHAR(255) NULL     COMMENT '作业描述',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_crop_id (crop_id),
    KEY idx_crop_type (crop_id, job_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作物农事规程表';

-- 表2: job_sensitivity_threshold 作业敏感度阈值配置表
DROP TABLE IF EXISTS job_sensitivity_threshold;
CREATE TABLE job_sensitivity_threshold (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_type        VARCHAR(16)  NOT NULL COMMENT '作业类型',
    weather_factor  VARCHAR(32)  NOT NULL COMMENT '气象因子: rainfall/temperature/windSpeed/cumulativeRainfall',
    threshold_min   DECIMAL(6,2) NULL     COMMENT '阈值下限(含)',
    threshold_max   DECIMAL(6,2) NULL     COMMENT '阈值上限(含)',
    risk_level      VARCHAR(8)   NOT NULL COMMENT '风险等级: GREEN/YELLOW/RED',
    description     VARCHAR(255) NULL     COMMENT '阈值说明',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_job_factor (job_type, weather_factor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业敏感度阈值配置表';

-- 表3: sales_festival 销售节日配置表
DROP TABLE IF EXISTS sales_festival;
CREATE TABLE sales_festival (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    festival_name   VARCHAR(32)  NOT NULL COMMENT '节日名称',
    festival_month  TINYINT      NOT NULL COMMENT '公历月(1-12)',
    festival_day    TINYINT      NOT NULL COMMENT '公历日(1-31)',
    remark          VARCHAR(255) NULL     COMMENT '备注',
    enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用: 1是 0否',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_month_day (festival_month, festival_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售节日配置表';

-- ============================================================
-- 种子数据
-- ============================================================

-- 作物1: 水稻 C001
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C001','水稻','分蘖期病虫害防治','SPRAY',30,'防治二化螟、稻飞虱'),
('C001','水稻','分蘖期追肥','FERTILIZE',35,'促进分蘖'),
('C001','水稻','拔节期追肥','FERTILIZE',45,'促进茎秆生长'),
('C001','水稻','孕穗期打药','SPRAY',60,'防治穗颈瘟'),
('C001','水稻','抽穗期灌溉','IRRIGATE',75,'保持田间水分'),
('C001','水稻','灌浆期追肥','FERTILIZE',85,'叶面肥促灌浆'),
('C001','水稻','乳熟期排水','IRRIGATE',100,'适度排水促成熟'),
('C001','水稻','成熟期采收','HARVEST',115,'适时收割');

-- 作物2: 小麦 C002
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C002','小麦','出苗期查苗补苗','FERTILIZE',15,'查苗补苗'),
('C002','小麦','分蘖期追肥','FERTILIZE',30,'促进分蘖'),
('C002','小麦','越冬前灌溉','IRRIGATE',45,'浇越冬水'),
('C002','小麦','返青期追肥','FERTILIZE',120,'返青肥'),
('C002','小麦','拔节期打药','SPRAY',135,'防治蚜虫'),
('C002','小麦','抽穗期打药','SPRAY',150,'防治赤霉病'),
('C002','小麦','灌浆期灌溉','IRRIGATE',160,'保障灌浆水分'),
('C002','小麦','成熟期采收','HARVEST',175,'适时收割');

-- 作物3: 玉米 C003
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C003','玉米','苗期病虫害防治','SPRAY',20,'防治地老虎'),
('C003','玉米','拔节期追肥','FERTILIZE',35,'促进生长'),
('C003','玉米','大喇叭口期打药','SPRAY',50,'防治玉米螟'),
('C003','玉米','大喇叭口期追肥','FERTILIZE',55,'重施穗肥'),
('C003','玉米','抽雄期灌溉','IRRIGATE',65,'保障水分'),
('C003','玉米','灌浆期灌溉','IRRIGATE',80,'促进灌浆'),
('C003','玉米','乳熟期排水','IRRIGATE',95,'排水防涝'),
('C003','玉米','成熟期采收','HARVEST',110,'适时收获');

-- 作物4: 大豆 C004
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C004','大豆','苗期除草','SPRAY',15,'苗后除草剂'),
('C004','大豆','分枝期追肥','FERTILIZE',30,'促进分枝'),
('C004','大豆','开花期灌溉','IRRIGATE',45,'保障水分'),
('C004','大豆','结荚期打药','SPRAY',55,'防治豆荚螟'),
('C004','大豆','鼓粒期追肥','FERTILIZE',65,'叶面肥'),
('C004','大豆','鼓粒期灌溉','IRRIGATE',70,'促进鼓粒'),
('C004','大豆','成熟前排水','IRRIGATE',90,'排水促熟'),
('C004','大豆','成熟期采收','HARVEST',100,'适时收获');

-- 作物5: 棉花 C005
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C005','棉花','苗期病虫害防治','SPRAY',20,'防治蚜虫'),
('C005','棉花','蕾期追肥','FERTILIZE',40,'促进现蕾'),
('C005','棉花','花铃期打药','SPRAY',60,'防治棉铃虫'),
('C005','棉花','花铃期追肥','FERTILIZE',65,'重施花铃肥'),
('C005','棉花','花铃期灌溉','IRRIGATE',70,'保障水分'),
('C005','棉花','吐絮期打药','SPRAY',100,'防治后期害虫'),
('C005','棉花','吐絮期排水','IRRIGATE',110,'控水促吐絮'),
('C005','棉花','成熟期采收','HARVEST',125,'分批采收');

-- 作物6: 油菜 C006
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C006','油菜','苗期追肥','FERTILIZE',20,'促苗壮长'),
('C006','油菜','苗期除草','SPRAY',35,'防除杂草'),
('C006','油菜','越冬前灌溉','IRRIGATE',50,'浇越冬水防冻'),
('C006','油菜','返青期追肥','FERTILIZE',130,'返青促发'),
('C006','油菜','蕾苔期打药','SPRAY',145,'防治菌核病'),
('C006','油菜','花期追肥','FERTILIZE',160,'硼肥促结实'),
('C006','油菜','角果期排水','IRRIGATE',180,'排水防渍'),
('C006','油菜','成熟期采收','HARVEST',210,'适时收割');

-- 作物7: 花生 C007
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C007','花生','苗期除草','SPRAY',15,'苗后除草剂'),
('C007','花生','初花期追肥','FERTILIZE',30,'促花针下扎'),
('C007','花生','开花期灌溉','IRRIGATE',45,'保障水分'),
('C007','花生','下针期追肥','FERTILIZE',55,'钙肥促结荚'),
('C007','花生','结荚期打药','SPRAY',70,'防治叶斑病'),
('C007','花生','饱果期灌溉','IRRIGATE',85,'促进饱果'),
('C007','花生','成熟前控水','IRRIGATE',105,'控水促熟'),
('C007','花生','成熟期采收','HARVEST',120,'适时收获');

-- 作物8: 芝麻 C008
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C008','芝麻','苗期除草','SPRAY',10,'及时间苗除草'),
('C008','芝麻','初花期追肥','FERTILIZE',25,'促花芽分化'),
('C008','芝麻','花期打药','SPRAY',40,'防治蚜虫'),
('C008','芝麻','蒴果期追肥','FERTILIZE',55,'叶面肥促灌浆'),
('C008','芝麻','蒴果期灌溉','IRRIGATE',65,'保障水分'),
('C008','芝麻','成熟前排水','IRRIGATE',80,'排水防涝'),
('C008','芝麻','打顶促熟','SPRAY',90,'打顶促成熟一致'),
('C008','芝麻','成熟期采收','HARVEST',100,'分段采收');

-- 作物9: 向日葵 C009
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C009','向日葵','苗期除草','SPRAY',15,'防除杂草'),
('C009','向日葵','现蕾期追肥','FERTILIZE',35,'促进花盘发育'),
('C009','向日葵','现蕾期打药','SPRAY',50,'防治螟虫'),
('C009','向日葵','开花期灌溉','IRRIGATE',60,'保障开花需水'),
('C009','向日葵','灌浆期追肥','FERTILIZE',75,'叶面肥促灌浆'),
('C009','向日葵','灌浆期灌溉','IRRIGATE',85,'促进籽粒饱满'),
('C009','向日葵','成熟前控水','IRRIGATE',100,'控水促熟'),
('C009','向日葵','成熟期采收','HARVEST',110,'适时收获');

-- 作物10: 高粱 C010
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C010','高粱','苗期除草','SPRAY',15,'防除杂草'),
('C010','高粱','拔节期追肥','FERTILIZE',30,'促进生长'),
('C010','高粱','拔节期打药','SPRAY',45,'防治蚜虫'),
('C010','高粱','孕穗期追肥','FERTILIZE',55,'穗肥促大穗'),
('C010','高粱','抽穗期灌溉','IRRIGATE',70,'保障抽穗水分'),
('C010','高粱','灌浆期打药','SPRAY',85,'防治螟虫'),
('C010','高粱','成熟前排水','IRRIGATE',100,'排水促熟'),
('C010','高粱','成熟期采收','HARVEST',115,'适时收获');

-- 作物11: 谷子 C011
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C011','谷子','苗期除草','SPRAY',10,'及时间苗'),
('C011','谷子','拔节期追肥','FERTILIZE',25,'促进分蘖'),
('C011','谷子','拔节期打药','SPRAY',40,'防治粟灰螟'),
('C011','谷子','孕穗期灌溉','IRRIGATE',55,'保障孕穗水分'),
('C011','谷子','抽穗期追肥','FERTILIZE',65,'穗肥促粒重'),
('C011','谷子','灌浆期打药','SPRAY',80,'防治穗螟'),
('C011','谷子','成熟前排水','IRRIGATE',95,'排水防倒伏'),
('C011','谷子','成熟期采收','HARVEST',110,'适时收获');

-- 作物12: 红薯 C012
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C012','红薯','缓苗期追肥','FERTILIZE',15,'促苗早发'),
('C012','红薯','缓苗期灌溉','IRRIGATE',20,'促缓苗扎根'),
('C012','红薯','分枝期除草','SPRAY',30,'中耕除草'),
('C012','红薯','薯块膨大期追肥','FERTILIZE',50,'钾肥促膨大'),
('C012','红薯','薯块膨大期灌溉','IRRIGATE',65,'保障膨大需水'),
('C012','红薯','薯块膨大期控水','IRRIGATE',90,'控水防裂'),
('C012','红薯','成熟前控水','IRRIGATE',105,'控水促淀粉积累'),
('C012','红薯','成熟期采收','HARVEST',120,'适时收获');

-- 作物13: 马铃薯 C013
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C013','马铃薯','苗期追肥','FERTILIZE',15,'促苗壮长'),
('C013','马铃薯','苗期灌溉','IRRIGATE',25,'保障出苗水分'),
('C013','马铃薯','现蕾期追肥','FERTILIZE',35,'促薯块形成'),
('C013','马铃薯','现蕾期打药','SPRAY',45,'防治晚疫病'),
('C013','马铃薯','开花期灌溉','IRRIGATE',55,'保障结薯水分'),
('C013','马铃薯','薯块膨大期追肥','FERTILIZE',65,'钾肥促膨大'),
('C013','马铃薯','成熟前控水','IRRIGATE',80,'控水促成熟'),
('C013','马铃薯','成熟期采收','HARVEST',90,'适时收获');

-- 作物14: 甘蔗 C014
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C014','甘蔗','苗期追肥','FERTILIZE',20,'促苗壮长'),
('C014','甘蔗','分蘖期灌溉','IRRIGATE',40,'促进分蘖'),
('C014','甘蔗','分蘖期追肥','FERTILIZE',60,'促蘖成茎'),
('C014','甘蔗','拔节期追肥','FERTILIZE',90,'重施攻茎肥'),
('C014','甘蔗','拔节期打药','SPRAY',110,'防治螟虫'),
('C014','甘蔗','拔节期灌溉','IRRIGATE',130,'保障拔节需水'),
('C014','甘蔗','成熟前控水','IRRIGATE',180,'控水促糖分积累'),
('C014','甘蔗','成熟期采收','HARVEST',240,'适时砍收');

-- 作物15: 甜菜 C015
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C015','甜菜','苗期除草','SPRAY',15,'防除杂草'),
('C015','甜菜','定苗期追肥','FERTILIZE',25,'促苗生长'),
('C015','甜菜','叶丛期灌溉','IRRIGATE',40,'保障叶丛生长'),
('C015','甜菜','块根膨大期追肥','FERTILIZE',55,'促块根膨大'),
('C015','甜菜','块根膨大期灌溉','IRRIGATE',70,'保障膨大需水'),
('C015','甜菜','块根膨大期打药','SPRAY',85,'防治褐斑病'),
('C015','甜菜','成熟前控水','IRRIGATE',100,'控水促糖分'),
('C015','甜菜','成熟期采收','HARVEST',120,'适时收获');

-- 作物16: 烟草 C016
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C016','烟草','缓苗期灌溉','IRRIGATE',10,'促缓苗成活'),
('C016','烟草','团棵期追肥','FERTILIZE',20,'促团棵壮苗'),
('C016','烟草','团棵期打药','SPRAY',30,'防治烟蚜'),
('C016','烟草','旺长期追肥','FERTILIZE',40,'重施旺长肥'),
('C016','烟草','旺长期打药','SPRAY',50,'防治赤星病'),
('C016','烟草','现蕾期打顶','SPRAY',60,'打顶抹杈'),
('C016','烟草','脚叶采收','HARVEST',70,'分次采叶'),
('C016','烟草','顶叶采收','HARVEST',80,'采收完毕');

-- 作物17: 茶 C017
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C017','茶','春茶前施肥','FERTILIZE',15,'催芽肥'),
('C017','茶','春茶修剪','SPRAY',25,'轻修剪整形'),
('C017','茶','春茶采摘','HARVEST',35,'采一芽一叶'),
('C017','茶','夏茶前追肥','FERTILIZE',60,'夏茶催芽肥'),
('C017','茶','夏茶病虫害防治','SPRAY',75,'防治茶小绿叶蝉'),
('C017','茶','夏茶采摘','HARVEST',90,'采夏茶'),
('C017','茶','秋茶前追肥','FERTILIZE',120,'秋茶追肥'),
('C017','茶','秋茶采摘','HARVEST',140,'采秋茶');

-- 作物18: 桑 C018
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C018','桑','春季施肥','FERTILIZE',10,'催芽促叶'),
('C018','桑','春季修剪','SPRAY',20,'剪枝整形'),
('C018','桑','春叶采收','HARVEST',35,'采叶养蚕'),
('C018','桑','夏伐后施肥','FERTILIZE',50,'夏伐促发新枝'),
('C018','桑','病虫害防治','SPRAY',65,'防治桑螟'),
('C018','桑','夏叶采收','HARVEST',80,'采夏叶养蚕'),
('C018','桑','秋季施肥','FERTILIZE',100,'秋肥养树'),
('C018','桑','秋叶采收','HARVEST',120,'采秋叶养蚕');

-- 作物19: 苎麻 C019
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C019','苎麻','头麻期追肥','FERTILIZE',15,'促头麻生长'),
('C019','苎麻','头麻期打药','SPRAY',30,'防治苎麻夜蛾'),
('C019','苎麻','头麻采收','HARVEST',60,'收头麻'),
('C019','苎麻','二麻期追肥','FERTILIZE',75,'促二麻生长'),
('C019','苎麻','二麻期打药','SPRAY',90,'防治天牛'),
('C019','苎麻','二麻采收','HARVEST',120,'收二麻'),
('C019','苎麻','三麻期追肥','FERTILIZE',135,'促三麻生长'),
('C019','苎麻','三麻采收','HARVEST',180,'收三麻');

-- 作物20: 亚麻 C020
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C020','亚麻','苗期除草','SPRAY',10,'及时间苗'),
('C020','亚麻','枞形期追肥','FERTILIZE',20,'促苗健壮'),
('C020','亚麻','快速生长期灌溉','IRRIGATE',35,'保障生长需水'),
('C020','亚麻','现蕾期打药','SPRAY',50,'防治亚麻枯萎病'),
('C020','亚麻','开花期控水','IRRIGATE',65,'控水促纤维品质'),
('C020','亚麻','后期排水','IRRIGATE',75,'排水防倒'),
('C020','亚麻','纤维采收','HARVEST',80,'适期拔麻'),
('C020','亚麻','种子采收','HARVEST',90,'收种');

-- ============================================================
-- 作物21-30: 蔬菜-叶菜类
-- ============================================================

-- 作物21: 大白菜 C021
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C021','大白菜','苗期间苗','SPRAY',7,'及时间苗除草'),
('C021','大白菜','莲座期追肥','FERTILIZE',20,'促莲座叶生长'),
('C021','大白菜','莲座期灌溉','IRRIGATE',28,'保障水分'),
('C021','大白菜','结球期追肥','FERTILIZE',40,'重施结球肥'),
('C021','大白菜','结球期打药','SPRAY',45,'防治软腐病'),
('C021','大白菜','结球期灌溉','IRRIGATE',55,'促叶球紧实'),
('C021','大白菜','采收前控水','IRRIGATE',68,'控水促品质'),
('C021','大白菜','成熟期采收','HARVEST',75,'适时采收');

-- 作物22: 小白菜 C022
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C022','小白菜','苗期除草','SPRAY',7,'清除杂草'),
('C022','小白菜','幼苗期追肥','FERTILIZE',12,'促幼苗生长'),
('C022','小白菜','生长期灌溉','IRRIGATE',18,'保持土壤湿润'),
('C022','小白菜','生长期打药','SPRAY',22,'防治菜青虫'),
('C022','小白菜','旺盛期追肥','FERTILIZE',28,'促快速生长'),
('C022','小白菜','旺盛期灌溉','IRRIGATE',32,'保障水分'),
('C022','小白菜','采收前打药','SPRAY',38,'防治蚜虫'),
('C022','小白菜','成熟期采收','HARVEST',45,'分批采收');

-- 作物23: 菠菜 C023
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C023','菠菜','苗期除草','SPRAY',7,'及时间苗'),
('C023','菠菜','幼苗期追肥','FERTILIZE',15,'促叶片生长'),
('C023','菠菜','生长期灌溉','IRRIGATE',22,'保持湿润'),
('C023','菠菜','生长期打药','SPRAY',28,'防治霜霉病'),
('C023','菠菜','旺盛期追肥','FERTILIZE',35,'氮肥促叶'),
('C023','菠菜','旺盛期灌溉','IRRIGATE',40,'保障生长'),
('C023','菠菜','采收前控水','IRRIGATE',48,'控水提质'),
('C023','菠菜','成熟期采收','HARVEST',55,'分批采收');

-- 作物24: 芹菜 C024
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C024','芹菜','缓苗期灌溉','IRRIGATE',10,'促缓苗成活'),
('C024','芹菜','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C024','芹菜','生长期打药','SPRAY',30,'防治斑枯病'),
('C024','芹菜','旺盛期追肥','FERTILIZE',45,'重施氮肥'),
('C024','芹菜','旺盛期灌溉','IRRIGATE',55,'保障水分充足'),
('C024','芹菜','旺盛期打药','SPRAY',65,'防治蚜虫'),
('C024','芹菜','采收前控水','IRRIGATE',75,'控水促品质'),
('C024','芹菜','成熟期采收','HARVEST',85,'分批采收');

-- 作物25: 生菜 C025
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C025','生菜','缓苗期灌溉','IRRIGATE',8,'促缓苗'),
('C025','生菜','幼苗期追肥','FERTILIZE',15,'促叶生长'),
('C025','生菜','生长期灌溉','IRRIGATE',22,'保持湿润'),
('C025','生菜','生长期打药','SPRAY',28,'防治蚜虫'),
('C025','生菜','旺盛期追肥','FERTILIZE',35,'氮肥促叶'),
('C025','生菜','旺盛期灌溉','IRRIGATE',42,'保障水分'),
('C025','生菜','采收前控水','IRRIGATE',50,'控水提质'),
('C025','生菜','成熟期采收','HARVEST',60,'适时采收');

-- 作物26: 空心菜 C026
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C026','空心菜','苗期除草','SPRAY',7,'清除杂草'),
('C026','空心菜','幼苗期追肥','FERTILIZE',12,'促苗生长'),
('C026','空心菜','生长期灌溉','IRRIGATE',18,'保持充足水分'),
('C026','空心菜','生长期打药','SPRAY',22,'防治菜青虫'),
('C026','空心菜','旺盛期追肥','FERTILIZE',28,'氮肥促生长'),
('C026','空心菜','旺盛期灌溉','IRRIGATE',35,'喜湿保障水分'),
('C026','空心菜','采收期追肥','FERTILIZE',42,'采后追肥促侧枝'),
('C026','空心菜','成熟期采收','HARVEST',50,'分次采收');

-- 作物27: 韭菜 C027
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C027','韭菜','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C027','韭菜','幼苗期追肥','FERTILIZE',20,'促根叶生长'),
('C027','韭菜','生长期打药','SPRAY',30,'防治韭蛆'),
('C027','韭菜','第一茬采收','HARVEST',45,'采第一茬'),
('C027','韭菜','采后追肥','FERTILIZE',50,'促二次生长'),
('C027','韭菜','采后灌溉','IRRIGATE',55,'保障水分'),
('C027','韭菜','第二茬打药','SPRAY',65,'防治灰霉病'),
('C027','韭菜','第二茬采收','HARVEST',75,'采第二茬');

-- 作物28: 蒜苗 C028
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C028','蒜苗','出苗期灌溉','IRRIGATE',7,'促出苗整齐'),
('C028','蒜苗','幼苗期追肥','FERTILIZE',15,'促苗生长'),
('C028','蒜苗','生长期除草','SPRAY',22,'清除杂草'),
('C028','蒜苗','旺盛期追肥','FERTILIZE',30,'氮肥促叶'),
('C028','蒜苗','旺盛期灌溉','IRRIGATE',38,'保障水分'),
('C028','蒜苗','采收前打药','SPRAY',45,'防治蒜蛆'),
('C028','蒜苗','采收前控水','IRRIGATE',52,'控水提质'),
('C028','蒜苗','成熟期采收','HARVEST',60,'适时采收');

-- 作物29: 花椰菜 C029
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C029','花椰菜','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C029','花椰菜','莲座期追肥','FERTILIZE',20,'促莲座叶'),
('C029','花椰菜','莲座期打药','SPRAY',30,'防治菜青虫'),
('C029','花椰菜','现蕾期追肥','FERTILIZE',40,'重施花球肥'),
('C029','花椰菜','现蕾期灌溉','IRRIGATE',48,'保障水分'),
('C029','花椰菜','花球期打药','SPRAY',55,'防治黑腐病'),
('C029','花椰菜','采收前控水','IRRIGATE',65,'控水保花球品质'),
('C029','花椰菜','成熟期采收','HARVEST',75,'适时采收');

-- 作物30: 西兰花 C030
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C030','西兰花','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C030','西兰花','莲座期追肥','FERTILIZE',20,'促叶片生长'),
('C030','西兰花','莲座期打药','SPRAY',30,'防治虫害'),
('C030','西兰花','现蕾期追肥','FERTILIZE',40,'促花球形成'),
('C030','西兰花','现蕾期灌溉','IRRIGATE',48,'保障水分'),
('C030','西兰花','花球期打药','SPRAY',55,'防治霜霉病'),
('C030','西兰花','采收前控水','IRRIGATE',65,'控水提质'),
('C030','西兰花','成熟期采收','HARVEST',72,'适期采收');

-- ============================================================
-- 作物31-40: 蔬菜-茄果/瓜类
-- ============================================================

-- 作物31: 番茄 C031
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C031','番茄','定植后灌溉','IRRIGATE',15,'促缓苗成活'),
('C031','番茄','开花期追肥','FERTILIZE',30,'促开花坐果'),
('C031','番茄','开花期打药','SPRAY',35,'防治早疫病'),
('C031','番茄','结果期追肥','FERTILIZE',50,'重施果肥'),
('C031','番茄','结果期灌溉','IRRIGATE',60,'保障果实膨大'),
('C031','番茄','盛果期打药','SPRAY',70,'防治晚疫病'),
('C031','番茄','盛果期追肥','FERTILIZE',80,'叶面肥保果'),
('C031','番茄','成熟期采收','HARVEST',90,'分批采收');

-- 作物32: 茄子 C032
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C032','茄子','定植后灌溉','IRRIGATE',15,'促缓苗'),
('C032','茄子','开花期追肥','FERTILIZE',30,'促花果发育'),
('C032','茄子','开花期打药','SPRAY',35,'防治绵疫病'),
('C032','茄子','结果期追肥','FERTILIZE',50,'重施果肥'),
('C032','茄子','结果期灌溉','IRRIGATE',60,'保障膨大'),
('C032','茄子','盛果期打药','SPRAY',75,'防治红蜘蛛'),
('C032','茄子','盛果期追肥','FERTILIZE',85,'保果肥'),
('C032','茄子','成熟期采收','HARVEST',95,'分批采收');

-- 作物33: 辣椒 C033
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C033','辣椒','定植后灌溉','IRRIGATE',15,'促缓苗'),
('C033','辣椒','开花期追肥','FERTILIZE',30,'促花芽分化'),
('C033','辣椒','开花期打药','SPRAY',35,'防治炭疽病'),
('C033','辣椒','结果期追肥','FERTILIZE',50,'重施果肥'),
('C033','辣椒','结果期灌溉','IRRIGATE',60,'保障水分'),
('C033','辣椒','盛果期打药','SPRAY',75,'防治病毒病'),
('C033','辣椒','盛果期追肥','FERTILIZE',85,'钾肥提质'),
('C033','辣椒','成熟期采收','HARVEST',95,'分批采收');

-- 作物34: 黄瓜 C034
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C034','黄瓜','定植后灌溉','IRRIGATE',10,'促缓苗'),
('C034','黄瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C034','黄瓜','开花期打药','SPRAY',30,'防治霜霉病'),
('C034','黄瓜','坐果期追肥','FERTILIZE',35,'重施果肥'),
('C034','黄瓜','膨大期灌溉','IRRIGATE',42,'保障水分'),
('C034','黄瓜','盛果期打药','SPRAY',50,'防治白粉病'),
('C034','黄瓜','盛果期追肥','FERTILIZE',55,'保果肥'),
('C034','黄瓜','成熟期采收','HARVEST',60,'分批采收');

-- 作物35: 冬瓜 C035
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C035','冬瓜','苗期除草','SPRAY',10,'清除杂草'),
('C035','冬瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C035','冬瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C035','冬瓜','开花期打药','SPRAY',40,'防治瓜绢螟'),
('C035','冬瓜','坐果期追肥','FERTILIZE',50,'重施果肥'),
('C035','冬瓜','膨大期灌溉','IRRIGATE',65,'保障膨大'),
('C035','冬瓜','成熟前控水','IRRIGATE',90,'控水促熟'),
('C035','冬瓜','成熟期采收','HARVEST',100,'适时采收');

-- 作物36: 南瓜 C036
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C036','南瓜','苗期除草','SPRAY',10,'清除杂草'),
('C036','南瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C036','南瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C036','南瓜','开花期打药','SPRAY',40,'防治白粉病'),
('C036','南瓜','坐果期追肥','FERTILIZE',50,'促果实发育'),
('C036','南瓜','膨大期灌溉','IRRIGATE',65,'保障膨大'),
('C036','南瓜','成熟前控水','IRRIGATE',90,'控水促熟'),
('C036','南瓜','成熟期采收','HARVEST',105,'适时采收');

-- 作物37: 丝瓜 C037
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C037','丝瓜','苗期除草','SPRAY',10,'清除杂草'),
('C037','丝瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C037','丝瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C037','丝瓜','开花期打药','SPRAY',35,'防治瓜实蝇'),
('C037','丝瓜','坐果期追肥','FERTILIZE',45,'促果生长'),
('C037','丝瓜','膨大期灌溉','IRRIGATE',55,'保障水分'),
('C037','丝瓜','采收期追肥','FERTILIZE',65,'保果肥'),
('C037','丝瓜','成熟期采收','HARVEST',70,'分批采收');

-- 作物38: 苦瓜 C038
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C038','苦瓜','苗期除草','SPRAY',10,'清除杂草'),
('C038','苦瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C038','苦瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C038','苦瓜','开花期打药','SPRAY',35,'防治瓜实蝇'),
('C038','苦瓜','坐果期追肥','FERTILIZE',45,'促果发育'),
('C038','苦瓜','膨大期灌溉','IRRIGATE',55,'保障膨大'),
('C038','苦瓜','采收期打药','SPRAY',65,'防治白粉病'),
('C038','苦瓜','成熟期采收','HARVEST',70,'分批采收');

-- 作物39: 西葫芦 C039
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C039','西葫芦','定植后灌溉','IRRIGATE',10,'促缓苗'),
('C039','西葫芦','开花期追肥','FERTILIZE',20,'促坐果'),
('C039','西葫芦','开花期打药','SPRAY',25,'防治白粉病'),
('C039','西葫芦','坐果期追肥','FERTILIZE',35,'重施果肥'),
('C039','西葫芦','膨大期灌溉','IRRIGATE',42,'保障水分'),
('C039','西葫芦','盛果期打药','SPRAY',50,'防治灰霉病'),
('C039','西葫芦','盛果期追肥','FERTILIZE',55,'保果肥'),
('C039','西葫芦','成熟期采收','HARVEST',60,'分批采收');

-- 作物40: 西瓜 C040
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C040','西瓜','苗期除草','SPRAY',10,'清除杂草'),
('C040','西瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C040','西瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C040','西瓜','开花期打药','SPRAY',35,'防治蚜虫'),
('C040','西瓜','坐果期追肥','FERTILIZE',45,'重施果肥'),
('C040','西瓜','膨大期灌溉','IRRIGATE',55,'保障膨大'),
('C040','西瓜','成熟前控水','IRRIGATE',70,'控水促糖分'),
('C040','西瓜','成熟期采收','HARVEST',80,'适时采收');

-- ============================================================
-- 作物41-50: 蔬菜-豆类/葱蒜类
-- ============================================================

-- 作物41: 豆角 C041
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C041','豆角','苗期除草','SPRAY',10,'及时间苗'),
('C041','豆角','抽蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C041','豆角','开花期灌溉','IRRIGATE',30,'保障水分'),
('C041','豆角','开花期打药','SPRAY',35,'防治豆荚螟'),
('C041','豆角','结荚期追肥','FERTILIZE',45,'重施荚肥'),
('C041','豆角','结荚期灌溉','IRRIGATE',55,'保障结荚'),
('C041','豆角','盛荚期打药','SPRAY',65,'防治锈病'),
('C041','豆角','成熟期采收','HARVEST',70,'分批采收');

-- 作物42: 豇豆 C042
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C042','豇豆','苗期除草','SPRAY',10,'及时间苗'),
('C042','豇豆','抽蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C042','豇豆','开花期灌溉','IRRIGATE',30,'保障水分'),
('C042','豇豆','开花期打药','SPRAY',35,'防治蚜虫'),
('C042','豇豆','结荚期追肥','FERTILIZE',45,'促荚生长'),
('C042','豇豆','结荚期灌溉','IRRIGATE',55,'保障结荚'),
('C042','豇豆','盛荚期打药','SPRAY',65,'防治煤霉病'),
('C042','豇豆','成熟期采收','HARVEST',70,'分批采收');

-- 作物43: 豌豆 C043
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C043','豌豆','苗期除草','SPRAY',10,'清除杂草'),
('C043','豌豆','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C043','豌豆','抽蔓期灌溉','IRRIGATE',35,'保障水分'),
('C043','豌豆','开花期打药','SPRAY',45,'防治潜叶蝇'),
('C043','豌豆','结荚期追肥','FERTILIZE',55,'促荚发育'),
('C043','豌豆','结荚期灌溉','IRRIGATE',65,'保障结荚'),
('C043','豌豆','鼓粒期控水','IRRIGATE',80,'控水促熟'),
('C043','豌豆','成熟期采收','HARVEST',90,'适时采收');

-- 作物44: 蚕豆 C044
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C044','蚕豆','苗期除草','SPRAY',15,'清除杂草'),
('C044','蚕豆','幼苗期追肥','FERTILIZE',25,'促苗生长'),
('C044','蚕豆','越冬前灌溉','IRRIGATE',40,'浇越冬水'),
('C044','蚕豆','开花期打药','SPRAY',100,'防治蚜虫'),
('C044','蚕豆','结荚期追肥','FERTILIZE',115,'促荚发育'),
('C044','蚕豆','结荚期灌溉','IRRIGATE',125,'保障结荚'),
('C044','蚕豆','鼓粒期控水','IRRIGATE',145,'控水促熟'),
('C044','蚕豆','成熟期采收','HARVEST',160,'适时采收');

-- 作物45: 葱 C045
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C045','葱','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C045','葱','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C045','葱','生长期培土','SPRAY',35,'培土促葱白'),
('C045','葱','旺盛期追肥','FERTILIZE',50,'重施氮肥'),
('C045','葱','旺盛期灌溉','IRRIGATE',60,'保障水分'),
('C045','葱','旺盛期打药','SPRAY',70,'防治葱蓟马'),
('C045','葱','采收前控水','IRRIGATE',85,'控水提质'),
('C045','葱','成熟期采收','HARVEST',95,'适时采收');

-- 作物46: 洋葱 C046
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C046','洋葱','缓苗期灌溉','IRRIGATE',15,'促缓苗'),
('C046','洋葱','幼苗期追肥','FERTILIZE',30,'促苗生长'),
('C046','洋葱','生长期打药','SPRAY',50,'防治霜霉病'),
('C046','洋葱','鳞茎膨大期追肥','FERTILIZE',70,'促鳞茎膨大'),
('C046','洋葱','鳞茎膨大期灌溉','IRRIGATE',80,'保障水分'),
('C046','洋葱','膨大期打药','SPRAY',90,'防治紫斑病'),
('C046','洋葱','采收前控水','IRRIGATE',105,'控水促成熟'),
('C046','洋葱','成熟期采收','HARVEST',115,'适时采收');

-- 作物47: 大蒜 C047
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C047','大蒜','出苗期灌溉','IRRIGATE',10,'促出苗'),
('C047','大蒜','幼苗期追肥','FERTILIZE',25,'促苗生长'),
('C047','大蒜','越冬前灌溉','IRRIGATE',40,'浇越冬水'),
('C047','大蒜','返青期追肥','FERTILIZE',100,'返青促长'),
('C047','大蒜','抽薹期打药','SPRAY',115,'防治蒜蛆'),
('C047','大蒜','鳞茎膨大期追肥','FERTILIZE',130,'促蒜头膨大'),
('C047','大蒜','采收前控水','IRRIGATE',145,'控水促成熟'),
('C047','大蒜','成熟期采收','HARVEST',155,'适时采收');

-- 作物48: 韭菜 C048
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C048','甘蓝','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C048','甘蓝','莲座期追肥','FERTILIZE',20,'促莲座叶'),
('C048','甘蓝','莲座期打药','SPRAY',30,'防治菜青虫'),
('C048','甘蓝','结球期追肥','FERTILIZE',45,'重施结球肥'),
('C048','甘蓝','结球期灌溉','IRRIGATE',55,'保障水分'),
('C048','甘蓝','结球期打药','SPRAY',65,'防治软腐病'),
('C048','甘蓝','采收前控水','IRRIGATE',75,'控水提质'),
('C048','甘蓝','成熟期采收','HARVEST',85,'适时采收');

-- 作物49: 苋菜 C049
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C049','苋菜','苗期除草','SPRAY',7,'清除杂草'),
('C049','苋菜','幼苗期追肥','FERTILIZE',12,'促苗生长'),
('C049','苋菜','生长期灌溉','IRRIGATE',18,'保持湿润'),
('C049','苋菜','生长期打药','SPRAY',22,'防治蚜虫'),
('C049','苋菜','旺盛期追肥','FERTILIZE',28,'氮肥促叶'),
('C049','苋菜','旺盛期灌溉','IRRIGATE',32,'保障水分'),
('C049','苋菜','采收前控水','IRRIGATE',38,'控水提质'),
('C049','苋菜','成熟期采收','HARVEST',45,'分批采收');

-- 作物50: 茼蒿 C050
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C050','茼蒿','苗期除草','SPRAY',7,'清除杂草'),
('C050','茼蒿','幼苗期追肥','FERTILIZE',12,'促苗生长'),
('C050','茼蒿','生长期灌溉','IRRIGATE',18,'保持湿润'),
('C050','茼蒿','生长期打药','SPRAY',22,'防治蚜虫'),
('C050','茼蒿','旺盛期追肥','FERTILIZE',28,'氮肥促叶'),
('C050','茼蒿','旺盛期灌溉','IRRIGATE',32,'保障水分'),
('C050','茼蒿','采收前控水','IRRIGATE',38,'控水提质'),
('C050','茼蒿','成熟期采收','HARVEST',45,'分批采收');

-- ============================================================
-- 作物51-58: 根茎菜类
-- ============================================================

-- 作物51: 萝卜 C051
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C051','萝卜','苗期间苗','SPRAY',10,'及时间苗'),
('C051','萝卜','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C051','萝卜','破肚期灌溉','IRRIGATE',30,'保障水分'),
('C051','萝卜','肉质根膨大期追肥','FERTILIZE',40,'钾肥促膨大'),
('C051','萝卜','膨大期打药','SPRAY',50,'防治菜青虫'),
('C051','萝卜','膨大期灌溉','IRRIGATE',60,'保障膨大'),
('C051','萝卜','采收前控水','IRRIGATE',75,'控水提质'),
('C051','萝卜','成熟期采收','HARVEST',85,'适时采收');

-- 作物52: 胡萝卜 C052
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C052','胡萝卜','苗期间苗','SPRAY',15,'及时间苗'),
('C052','胡萝卜','幼苗期追肥','FERTILIZE',25,'促苗生长'),
('C052','胡萝卜','生长期除草','SPRAY',40,'清除杂草'),
('C052','胡萝卜','肉质根膨大期追肥','FERTILIZE',55,'钾肥促膨大'),
('C052','胡萝卜','膨大期灌溉','IRRIGATE',65,'保障膨大'),
('C052','胡萝卜','膨大期打药','SPRAY',75,'防治蚜虫'),
('C052','胡萝卜','采收前控水','IRRIGATE',90,'控水提质'),
('C052','胡萝卜','成熟期采收','HARVEST',100,'适时采收');

-- 作物53: 莲藕 C053
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C053','莲藕','立叶期追肥','FERTILIZE',20,'促立叶生长'),
('C053','莲藕','立叶期灌溉','IRRIGATE',30,'保持水位'),
('C053','莲藕','旺盛期追肥','FERTILIZE',45,'重施生长肥'),
('C053','莲藕','旺盛期打药','SPRAY',55,'防治莲藕腐败病'),
('C053','莲藕','结藕期追肥','FERTILIZE',75,'促藕节膨大'),
('C053','莲藕','结藕期灌溉','IRRIGATE',85,'保持浅水'),
('C053','莲藕','成熟前控水','IRRIGATE',110,'排水促熟'),
('C053','莲藕','成熟期采收','HARVEST',120,'适时采挖');

-- 作物54: 茭白 C054
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C054','茭白','缓苗期灌溉','IRRIGATE',15,'保持水位'),
('C054','茭白','分蘖期追肥','FERTILIZE',25,'促分蘖'),
('C054','茭白','分蘖期打药','SPRAY',35,'防治纹枯病'),
('C054','茭白','孕茭期追肥','FERTILIZE',50,'促孕茭'),
('C054','茭白','孕茭期灌溉','IRRIGATE',60,'保持深水'),
('C054','茭白','孕茭期打药','SPRAY',70,'防治胡麻斑病'),
('C054','茭白','采收前控水','IRRIGATE',85,'排水促采'),
('C054','茭白','成熟期采收','HARVEST',95,'分批采收');

-- 作物55: 芋头 C055
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C055','芋头','出苗期灌溉','IRRIGATE',15,'保持土壤湿润'),
('C055','芋头','幼苗期追肥','FERTILIZE',25,'促苗生长'),
('C055','芋头','生长期打药','SPRAY',40,'防治芋疫病'),
('C055','芋头','旺盛期追肥','FERTILIZE',60,'重施钾肥'),
('C055','芋头','旺盛期灌溉','IRRIGATE',75,'保持充足水分'),
('C055','芋头','芋球膨大期追肥','FERTILIZE',90,'促芋球膨大'),
('C055','芋头','成熟前控水','IRRIGATE',110,'排水促熟'),
('C055','芋头','成熟期采收','HARVEST',120,'适时采收');

-- 作物56: 山药 C056
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C056','山药','出苗期搭架','SPRAY',15,'搭架引蔓'),
('C056','山药','幼苗期追肥','FERTILIZE',25,'促苗生长'),
('C056','山药','生长期灌溉','IRRIGATE',40,'保障水分'),
('C056','山药','旺盛期追肥','FERTILIZE',60,'重施钾肥'),
('C056','山药','旺盛期打药','SPRAY',75,'防治炭疽病'),
('C056','山药','块茎膨大期灌溉','IRRIGATE',90,'保障膨大'),
('C056','山药','成熟前控水','IRRIGATE',120,'控水促熟'),
('C056','山药','成熟期采收','HARVEST',140,'适时采挖');

-- 作物57: 生姜 C057
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C057','生姜','出苗期遮阴','SPRAY',20,'搭棚遮阴'),
('C057','生姜','幼苗期追肥','FERTILIZE',30,'促苗生长'),
('C057','生姜','生长期灌溉','IRRIGATE',45,'保持湿润'),
('C057','生姜','旺盛期追肥','FERTILIZE',60,'重施钾肥'),
('C057','生姜','旺盛期打药','SPRAY',75,'防治姜瘟病'),
('C057','生姜','根茎膨大期灌溉','IRRIGATE',90,'保障膨大'),
('C057','生姜','成熟前控水','IRRIGATE',120,'控水促熟'),
('C057','生姜','成熟期采收','HARVEST',140,'适时采收');

-- 作物58: 莴笋 C058
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C058','莴笋','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C058','莴笋','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C058','莴笋','莲座期打药','SPRAY',30,'防治霜霉病'),
('C058','莴笋','茎膨大期追肥','FERTILIZE',40,'重施钾肥'),
('C058','莴笋','茎膨大期灌溉','IRRIGATE',50,'保障水分'),
('C058','莴笋','茎膨大期打药','SPRAY',60,'防治菌核病'),
('C058','莴笋','采收前控水','IRRIGATE',70,'控水提质'),
('C058','莴笋','成熟期采收','HARVEST',80,'适时采收');

-- ============================================================
-- 作物59-78: 水果类
-- ============================================================

-- 作物59: 苹果 C059
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C059','苹果','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C059','苹果','花前打药','SPRAY',30,'防治腐烂病'),
('C059','苹果','花后追肥','FERTILIZE',45,'促果发育'),
('C059','苹果','果实膨大期灌溉','IRRIGATE',60,'保障水分'),
('C059','苹果','果实膨大期打药','SPRAY',75,'防治食心虫'),
('C059','苹果','着色期追肥','FERTILIZE',90,'钾肥促着色'),
('C059','苹果','采收前控水','IRRIGATE',105,'控水提质'),
('C059','苹果','成熟期采收','HARVEST',120,'分批采收');

-- 作物60: 梨 C060
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C060','梨','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C060','梨','花前打药','SPRAY',30,'防治黑星病'),
('C060','梨','花后追肥','FERTILIZE',45,'促果发育'),
('C060','梨','果实膨大期灌溉','IRRIGATE',60,'保障水分'),
('C060','梨','果实膨大期打药','SPRAY',75,'防治梨木虱'),
('C060','梨','着色期追肥','FERTILIZE',90,'钾肥提质'),
('C060','梨','采收前控水','IRRIGATE',105,'控水提质'),
('C060','梨','成熟期采收','HARVEST',120,'适时采收');

-- 作物61: 桃 C061
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C061','桃','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C061','桃','花前打药','SPRAY',25,'防治缩叶病'),
('C061','桃','花后追肥','FERTILIZE',35,'促果发育'),
('C061','桃','硬核期灌溉','IRRIGATE',50,'保障水分'),
('C061','桃','果实膨大期打药','SPRAY',65,'防治食心虫'),
('C061','桃','着色期追肥','FERTILIZE',75,'钾肥促着色'),
('C061','桃','采收前控水','IRRIGATE',85,'控水提质'),
('C061','桃','成熟期采收','HARVEST',95,'分批采收');

-- 作物62: 李子 C062
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C062','李子','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C062','李子','花前打药','SPRAY',25,'防治褐腐病'),
('C062','李子','花后追肥','FERTILIZE',35,'促果发育'),
('C062','李子','硬核期灌溉','IRRIGATE',50,'保障水分'),
('C062','李子','果实膨大期打药','SPRAY',65,'防治蚜虫'),
('C062','李子','着色期追肥','FERTILIZE',75,'钾肥提质'),
('C062','李子','采收前控水','IRRIGATE',85,'控水提质'),
('C062','李子','成熟期采收','HARVEST',95,'适时采收');

-- 作物63: 杏 C063
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C063','杏','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C063','杏','花前打药','SPRAY',25,'防治疮痂病'),
('C063','杏','花后追肥','FERTILIZE',35,'促果发育'),
('C063','杏','硬核期灌溉','IRRIGATE',45,'保障水分'),
('C063','杏','果实膨大期打药','SPRAY',55,'防治食心虫'),
('C063','杏','着色期追肥','FERTILIZE',65,'钾肥提质'),
('C063','杏','采收前控水','IRRIGATE',75,'控水提质'),
('C063','杏','成熟期采收','HARVEST',85,'适时采收');

-- 作物64: 樱桃 C064
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C064','樱桃','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C064','樱桃','花前打药','SPRAY',25,'防治流胶病'),
('C064','樱桃','花后追肥','FERTILIZE',35,'促果发育'),
('C064','樱桃','硬核期灌溉','IRRIGATE',45,'保障水分防裂果'),
('C064','樱桃','果实膨大期打药','SPRAY',50,'防治果蝇'),
('C064','樱桃','着色期追肥','FERTILIZE',55,'钾肥促着色'),
('C064','樱桃','采收前控水','IRRIGATE',60,'控水防裂'),
('C064','樱桃','成熟期采收','HARVEST',65,'适时采收');

-- 作物65: 草莓 C065
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C065','草莓','缓苗期灌溉','IRRIGATE',10,'促缓苗'),
('C065','草莓','缓苗期追肥','FERTILIZE',15,'促苗生长'),
('C065','草莓','开花期打药','SPRAY',25,'防治灰霉病'),
('C065','草莓','结果期追肥','FERTILIZE',35,'促果发育'),
('C065','草莓','结果期灌溉','IRRIGATE',40,'保障水分'),
('C065','草莓','盛果期打药','SPRAY',50,'防治白粉病'),
('C065','草莓','盛果期追肥','FERTILIZE',55,'叶面肥保果'),
('C065','草莓','成熟期采收','HARVEST',60,'分批采收');

-- 作物66: 葡萄 C066
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C066','葡萄','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C066','葡萄','花前打药','SPRAY',30,'防治黑痘病'),
('C066','葡萄','花后追肥','FERTILIZE',40,'促果发育'),
('C066','葡萄','果实膨大期灌溉','IRRIGATE',55,'保障水分'),
('C066','葡萄','果实膨大期打药','SPRAY',65,'防治霜霉病'),
('C066','葡萄','着色期追肥','FERTILIZE',80,'钾肥促着色'),
('C066','葡萄','采收前控水','IRRIGATE',95,'控水促糖分'),
('C066','葡萄','成熟期采收','HARVEST',110,'适时采收');

-- 作物67: 柑橘 C067
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C067','柑橘','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C067','柑橘','花前打药','SPRAY',35,'防治红蜘蛛'),
('C067','柑橘','花后追肥','FERTILIZE',50,'促果发育'),
('C067','柑橘','果实膨大期灌溉','IRRIGATE',65,'保障水分'),
('C067','柑橘','果实膨大期打药','SPRAY',80,'防治潜叶蛾'),
('C067','柑橘','着色期追肥','FERTILIZE',100,'钾肥提质'),
('C067','柑橘','采收前控水','IRRIGATE',120,'控水促糖分'),
('C067','柑橘','成熟期采收','HARVEST',140,'适时采收');

-- 作物68: 橙子 C068
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C068','橙子','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C068','橙子','花前打药','SPRAY',35,'防治溃疡病'),
('C068','橙子','花后追肥','FERTILIZE',50,'促果发育'),
('C068','橙子','果实膨大期灌溉','IRRIGATE',65,'保障水分'),
('C068','橙子','果实膨大期打药','SPRAY',80,'防治红蜘蛛'),
('C068','橙子','着色期追肥','FERTILIZE',100,'钾肥提质'),
('C068','橙子','采收前控水','IRRIGATE',120,'控水促糖分'),
('C068','橙子','成熟期采收','HARVEST',140,'适时采收');

-- 作物69: 柚子 C069
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C069','柚子','萌芽前施肥','FERTILIZE',25,'催芽肥'),
('C069','柚子','花前打药','SPRAY',40,'防治红蜘蛛'),
('C069','柚子','花后追肥','FERTILIZE',55,'促果发育'),
('C069','柚子','果实膨大期灌溉','IRRIGATE',75,'保障水分'),
('C069','柚子','果实膨大期打药','SPRAY',90,'防治潜叶蛾'),
('C069','柚子','着色期追肥','FERTILIZE',110,'钾肥提质'),
('C069','柚子','采收前控水','IRRIGATE',130,'控水促糖分'),
('C069','柚子','成熟期采收','HARVEST',150,'适时采收');

-- 作物70: 柠檬 C070
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C070','柠檬','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C070','柠檬','花前打药','SPRAY',35,'防治疮痂病'),
('C070','柠檬','花后追肥','FERTILIZE',50,'促果发育'),
('C070','柠檬','果实膨大期灌溉','IRRIGATE',65,'保障水分'),
('C070','柠檬','果实膨大期打药','SPRAY',80,'防治红蜘蛛'),
('C070','柠檬','着色期追肥','FERTILIZE',100,'钾肥提质'),
('C070','柠檬','采收前控水','IRRIGATE',120,'控水提质'),
('C070','柠檬','成熟期采收','HARVEST',140,'分批采收');

-- 作物71: 甜瓜 C071
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C071','甜瓜','苗期除草','SPRAY',10,'清除杂草'),
('C071','甜瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C071','甜瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C071','甜瓜','开花期打药','SPRAY',35,'防治白粉病'),
('C071','甜瓜','坐果期追肥','FERTILIZE',45,'重施果肥'),
('C071','甜瓜','膨大期灌溉','IRRIGATE',55,'保障膨大'),
('C071','甜瓜','成熟前控水','IRRIGATE',65,'控水促糖分'),
('C071','甜瓜','成熟期采收','HARVEST',75,'适时采收');

-- 作物72: 哈密瓜 C072
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C072','哈密瓜','苗期除草','SPRAY',10,'清除杂草'),
('C072','哈密瓜','伸蔓期追肥','FERTILIZE',20,'促蔓生长'),
('C072','哈密瓜','伸蔓期灌溉','IRRIGATE',30,'保障水分'),
('C072','哈密瓜','开花期打药','SPRAY',35,'防治蔓枯病'),
('C072','哈密瓜','坐果期追肥','FERTILIZE',45,'重施果肥'),
('C072','哈密瓜','膨大期灌溉','IRRIGATE',55,'保障膨大'),
('C072','哈密瓜','成熟前控水','IRRIGATE',65,'控水促糖分'),
('C072','哈密瓜','成熟期采收','HARVEST',75,'适时采收');

-- 作物73: 猕猴桃 C073
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C073','猕猴桃','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C073','猕猴桃','花前打药','SPRAY',35,'防治溃疡病'),
('C073','猕猴桃','花后追肥','FERTILIZE',50,'促果发育'),
('C073','猕猴桃','果实膨大期灌溉','IRRIGATE',65,'保障水分'),
('C073','猕猴桃','果实膨大期打药','SPRAY',80,'防治灰霉病'),
('C073','猕猴桃','着色期追肥','FERTILIZE',100,'钾肥提质'),
('C073','猕猴桃','采收前控水','IRRIGATE',120,'控水提质'),
('C073','猕猴桃','成熟期采收','HARVEST',140,'适时采收');

-- 作物74: 石榴 C074
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C074','石榴','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C074','石榴','花前打药','SPRAY',30,'防治干腐病'),
('C074','石榴','花后追肥','FERTILIZE',45,'促果发育'),
('C074','石榴','果实膨大期灌溉','IRRIGATE',60,'保障水分'),
('C074','石榴','果实膨大期打药','SPRAY',75,'防治蚜虫'),
('C074','石榴','着色期追肥','FERTILIZE',90,'钾肥促着色'),
('C074','石榴','采收前控水','IRRIGATE',105,'控水促糖分'),
('C074','石榴','成熟期采收','HARVEST',120,'适时采收');

-- 作物75: 柿子 C075
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C075','柿子','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C075','柿子','花前打药','SPRAY',35,'防治炭疽病'),
('C075','柿子','花后追肥','FERTILIZE',50,'促果发育'),
('C075','柿子','果实膨大期灌溉','IRRIGATE',65,'保障水分'),
('C075','柿子','果实膨大期打药','SPRAY',80,'防治柿蒂虫'),
('C075','柿子','着色期追肥','FERTILIZE',100,'钾肥提质'),
('C075','柿子','采收前控水','IRRIGATE',120,'控水提质'),
('C075','柿子','成熟期采收','HARVEST',140,'适时采收');

-- 作物76: 枣 C076
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C076','枣','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C076','枣','花前打药','SPRAY',30,'防治枣尺蠖'),
('C076','枣','花后追肥','FERTILIZE',45,'促果发育'),
('C076','枣','果实膨大期灌溉','IRRIGATE',60,'保障水分'),
('C076','枣','果实膨大期打药','SPRAY',75,'防治枣锈病'),
('C076','枣','着色期追肥','FERTILIZE',90,'钾肥提质'),
('C076','枣','采收前控水','IRRIGATE',105,'控水促糖分'),
('C076','枣','成熟期采收','HARVEST',120,'适时采收');

-- 作物77: 芒果 C077
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C077','芒果','花前施肥','FERTILIZE',30,'催花肥'),
('C077','芒果','花前打药','SPRAY',40,'防治炭疽病'),
('C077','芒果','花后追肥','FERTILIZE',55,'促果发育'),
('C077','芒果','果实膨大期灌溉','IRRIGATE',70,'保障水分'),
('C077','芒果','果实膨大期打药','SPRAY',85,'防治蒂腐病'),
('C077','芒果','着色期追肥','FERTILIZE',100,'钾肥促着色'),
('C077','芒果','采收前控水','IRRIGATE',115,'控水促糖分'),
('C077','芒果','成熟期采收','HARVEST',130,'适时采收');

-- 作物78: 百香果 C078
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C078','百香果','缓苗期灌溉','IRRIGATE',15,'促缓苗'),
('C078','百香果','抽蔓期追肥','FERTILIZE',25,'促蔓生长'),
('C078','百香果','开花期打药','SPRAY',40,'防治茎基腐病'),
('C078','百香果','结果期追肥','FERTILIZE',55,'促果发育'),
('C078','百香果','结果期灌溉','IRRIGATE',65,'保障水分'),
('C078','百香果','盛果期打药','SPRAY',80,'防治疫病'),
('C078','百香果','盛果期追肥','FERTILIZE',90,'钾肥提质'),
('C078','百香果','成熟期采收','HARVEST',100,'分批采收');

-- ============================================================
-- 作物79-90: 经济/药用作物
-- ============================================================

-- 作物79: 橡胶 C079
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C079','橡胶','幼苗期施肥','FERTILIZE',30,'促苗生长'),
('C079','橡胶','幼苗期除草','SPRAY',45,'清除杂草'),
('C079','橡胶','生长期施肥','FERTILIZE',90,'促树生长'),
('C079','橡胶','生长期打药','SPRAY',120,'防治白粉病'),
('C079','橡胶','开割前施肥','FERTILIZE',200,'养树促割'),
('C079','橡胶','开割期防病','SPRAY',250,'防治割面病'),
('C079','橡胶','割胶期养护','FERTILIZE',300,'施肥养树'),
('C079','橡胶','成熟期割胶','HARVEST',365,'开始割胶');

-- 作物80: 咖啡 C080
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C080','咖啡','幼苗期遮阴','SPRAY',30,'搭遮阴棚'),
('C080','咖啡','幼苗期施肥','FERTILIZE',60,'促苗生长'),
('C080','咖啡','生长期打药','SPRAY',120,'防治锈病'),
('C080','咖啡','开花期施肥','FERTILIZE',180,'促花果发育'),
('C080','咖啡','结果期灌溉','IRRIGATE',220,'保障水分'),
('C080','咖啡','果实膨大期追肥','FERTILIZE',260,'促果膨大'),
('C080','咖啡','成熟前控水','IRRIGATE',300,'控水促熟'),
('C080','咖啡','成熟期采收','HARVEST',330,'分批采收');

-- 作物81: 可可 C081
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C081','可可','幼苗期遮阴','SPRAY',30,'搭遮阴棚'),
('C081','可可','幼苗期施肥','FERTILIZE',60,'促苗生长'),
('C081','可可','生长期打药','SPRAY',120,'防治黑果病'),
('C081','可可','开花期施肥','FERTILIZE',180,'促花果发育'),
('C081','可可','结果期灌溉','IRRIGATE',220,'保障水分'),
('C081','可可','果实膨大期追肥','FERTILIZE',260,'促果膨大'),
('C081','可可','成熟前控水','IRRIGATE',300,'控水促熟'),
('C081','可可','成熟期采收','HARVEST',330,'分批采收');

-- 作物82: 枸杞 C082
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C082','枸杞','萌芽前施肥','FERTILIZE',20,'催芽肥'),
('C082','枸杞','花前打药','SPRAY',35,'防治蚜虫'),
('C082','枸杞','花后追肥','FERTILIZE',50,'促果发育'),
('C082','枸杞','果实膨大期灌溉','IRRIGATE',65,'保障水分'),
('C082','枸杞','果实膨大期打药','SPRAY',75,'防治瘿螨'),
('C082','枸杞','盛果期追肥','FERTILIZE',85,'保果肥'),
('C082','枸杞','采收前控水','IRRIGATE',95,'控水提质'),
('C082','枸杞','成熟期采收','HARVEST',105,'分批采收');

-- 作物83: 金银花 C083
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C083','金银花','萌芽前施肥','FERTILIZE',15,'催芽肥'),
('C083','金银花','生长期打药','SPRAY',30,'防治蚜虫'),
('C083','金银花','花前追肥','FERTILIZE',40,'促花芽分化'),
('C083','金银花','开花期灌溉','IRRIGATE',50,'保障水分'),
('C083','金银花','第一茬采收','HARVEST',60,'采花蕾'),
('C083','金银花','采后追肥','FERTILIZE',65,'促二次开花'),
('C083','金银花','第二茬打药','SPRAY',80,'防治白粉病'),
('C083','金银花','第二茬采收','HARVEST',90,'采第二茬');

-- 作物84: 黄芪 C084
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C084','黄芪','苗期除草','SPRAY',15,'清除杂草'),
('C084','黄芪','幼苗期追肥','FERTILIZE',30,'促苗生长'),
('C084','黄芪','生长期灌溉','IRRIGATE',60,'保障水分'),
('C084','黄芪','旺盛期追肥','FERTILIZE',90,'促根生长'),
('C084','黄芪','旺盛期打药','SPRAY',120,'防治白粉病'),
('C084','黄芪','根膨大期灌溉','IRRIGATE',150,'保障膨大'),
('C084','黄芪','成熟前控水','IRRIGATE',200,'控水促熟'),
('C084','黄芪','成熟期采收','HARVEST',220,'适时采挖');

-- 作物85: 人参 C085
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C085','人参','苗期遮阴','SPRAY',30,'搭参棚遮阴'),
('C085','人参','幼苗期追肥','FERTILIZE',60,'促苗生长'),
('C085','人参','生长期打药','SPRAY',100,'防治黑斑病'),
('C085','人参','生长期灌溉','IRRIGATE',150,'保持土壤湿润'),
('C085','人参','根膨大期追肥','FERTILIZE',200,'促根膨大'),
('C085','人参','根膨大期打药','SPRAY',250,'防治锈腐病'),
('C085','人参','成熟前控水','IRRIGATE',300,'控水促熟'),
('C085','人参','成熟期采收','HARVEST',360,'适时采挖');

-- 作物86: 当归 C086
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C086','当归','苗期除草','SPRAY',20,'清除杂草'),
('C086','当归','幼苗期追肥','FERTILIZE',40,'促苗生长'),
('C086','当归','生长期灌溉','IRRIGATE',70,'保障水分'),
('C086','当归','旺盛期追肥','FERTILIZE',100,'促根生长'),
('C086','当归','旺盛期打药','SPRAY',130,'防治根腐病'),
('C086','当归','根膨大期灌溉','IRRIGATE',160,'保障膨大'),
('C086','当归','成熟前控水','IRRIGATE',200,'控水促熟'),
('C086','当归','成熟期采收','HARVEST',220,'适时采挖');

-- 作物87: 三七 C087
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C087','三七','苗期遮阴','SPRAY',30,'搭棚遮阴'),
('C087','三七','幼苗期追肥','FERTILIZE',60,'促苗生长'),
('C087','三七','生长期打药','SPRAY',100,'防治黑斑病'),
('C087','三七','生长期灌溉','IRRIGATE',140,'保持湿润'),
('C087','三七','根膨大期追肥','FERTILIZE',180,'促根膨大'),
('C087','三七','根膨大期打药','SPRAY',220,'防治根腐病'),
('C087','三七','成熟前控水','IRRIGATE',260,'控水促熟'),
('C087','三七','成熟期采收','HARVEST',300,'适时采挖');

-- 作物88: 板蓝根 C088
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C088','板蓝根','苗期除草','SPRAY',15,'清除杂草'),
('C088','板蓝根','幼苗期追肥','FERTILIZE',25,'促苗生长'),
('C088','板蓝根','生长期灌溉','IRRIGATE',40,'保障水分'),
('C088','板蓝根','叶采收期打药','SPRAY',55,'防治霜霉病'),
('C088','板蓝根','第一次采叶','HARVEST',65,'采收大青叶'),
('C088','板蓝根','采后追肥','FERTILIZE',70,'促根生长'),
('C088','板蓝根','根膨大期灌溉','IRRIGATE',90,'保障膨大'),
('C088','板蓝根','成熟期采收','HARVEST',110,'挖根采收');

-- 作物89: 薄荷 C089
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C089','薄荷','苗期除草','SPRAY',10,'清除杂草'),
('C089','薄荷','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C089','薄荷','生长期灌溉','IRRIGATE',30,'保持湿润'),
('C089','薄荷','生长期打药','SPRAY',40,'防治锈病'),
('C089','薄荷','第一茬采收','HARVEST',55,'采收茎叶'),
('C089','薄荷','采后追肥','FERTILIZE',60,'促再生'),
('C089','薄荷','第二茬打药','SPRAY',75,'防治蚜虫'),
('C089','薄荷','第二茬采收','HARVEST',90,'采收第二茬');

-- 作物90: 薰衣草 C090
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C090','薰衣草','幼苗期追肥','FERTILIZE',30,'促苗生长'),
('C090','薰衣草','生长期灌溉','IRRIGATE',50,'保障水分'),
('C090','薰衣草','生长期打药','SPRAY',70,'防治根腐病'),
('C090','薰衣草','花前追肥','FERTILIZE',90,'促花芽分化'),
('C090','薰衣草','开花期控水','IRRIGATE',100,'控水促花'),
('C090','薰衣草','盛花期打药','SPRAY',110,'防治蚜虫'),
('C090','薰衣草','采收前控水','IRRIGATE',120,'控水提质'),
('C090','薰衣草','成熟期采收','HARVEST',130,'适时采收');

-- ============================================================
-- 作物91-100: 杂粮类
-- ============================================================

-- 作物91: 荞麦 C091
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C091','荞麦','苗期除草','SPRAY',10,'清除杂草'),
('C091','荞麦','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C091','荞麦','生长期灌溉','IRRIGATE',30,'保障水分'),
('C091','荞麦','开花期打药','SPRAY',40,'防治蚜虫'),
('C091','荞麦','灌浆期追肥','FERTILIZE',50,'叶面肥促灌浆'),
('C091','荞麦','灌浆期灌溉','IRRIGATE',55,'保障灌浆'),
('C091','荞麦','成熟前控水','IRRIGATE',65,'控水促熟'),
('C091','荞麦','成熟期采收','HARVEST',75,'适时收获');

-- 作物92: 燕麦 C092
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C092','燕麦','苗期除草','SPRAY',15,'清除杂草'),
('C092','燕麦','分蘖期追肥','FERTILIZE',30,'促分蘖'),
('C092','燕麦','拔节期灌溉','IRRIGATE',45,'保障水分'),
('C092','燕麦','拔节期打药','SPRAY',55,'防治蚜虫'),
('C092','燕麦','抽穗期追肥','FERTILIZE',65,'穗肥促粒重'),
('C092','燕麦','灌浆期灌溉','IRRIGATE',75,'保障灌浆'),
('C092','燕麦','成熟前控水','IRRIGATE',90,'控水促熟'),
('C092','燕麦','成熟期采收','HARVEST',100,'适时收获');

-- 作物93: 大麦 C093
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C093','大麦','苗期除草','SPRAY',15,'清除杂草'),
('C093','大麦','分蘖期追肥','FERTILIZE',30,'促分蘖'),
('C093','大麦','越冬前灌溉','IRRIGATE',45,'浇越冬水'),
('C093','大麦','返青期追肥','FERTILIZE',100,'返青肥'),
('C093','大麦','拔节期打药','SPRAY',115,'防治蚜虫'),
('C093','大麦','抽穗期打药','SPRAY',130,'防治赤霉病'),
('C093','大麦','灌浆期灌溉','IRRIGATE',140,'保障灌浆'),
('C093','大麦','成熟期采收','HARVEST',155,'适时收获');

-- 作物94: 黑豆 C094
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C094','黑豆','苗期除草','SPRAY',15,'苗后除草剂'),
('C094','黑豆','分枝期追肥','FERTILIZE',30,'促进分枝'),
('C094','黑豆','开花期灌溉','IRRIGATE',45,'保障水分'),
('C094','黑豆','结荚期打药','SPRAY',55,'防治豆荚螟'),
('C094','黑豆','鼓粒期追肥','FERTILIZE',65,'叶面肥'),
('C094','黑豆','鼓粒期灌溉','IRRIGATE',70,'促进鼓粒'),
('C094','黑豆','成熟前排水','IRRIGATE',90,'排水促熟'),
('C094','黑豆','成熟期采收','HARVEST',100,'适时收获');

-- 作物95: 绿豆 C095
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C095','绿豆','苗期除草','SPRAY',10,'清除杂草'),
('C095','绿豆','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C095','绿豆','开花期灌溉','IRRIGATE',30,'保障水分'),
('C095','绿豆','结荚期打药','SPRAY',40,'防治蚜虫'),
('C095','绿豆','鼓粒期追肥','FERTILIZE',50,'叶面肥'),
('C095','绿豆','鼓粒期灌溉','IRRIGATE',55,'促进鼓粒'),
('C095','绿豆','成熟前控水','IRRIGATE',65,'控水促熟'),
('C095','绿豆','成熟期采收','HARVEST',75,'分批采收');

-- 作物96: 红小豆 C096
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C096','红小豆','苗期除草','SPRAY',10,'清除杂草'),
('C096','红小豆','幼苗期追肥','FERTILIZE',20,'促苗生长'),
('C096','红小豆','开花期灌溉','IRRIGATE',35,'保障水分'),
('C096','红小豆','结荚期打药','SPRAY',45,'防治蚜虫'),
('C096','红小豆','鼓粒期追肥','FERTILIZE',55,'叶面肥'),
('C096','红小豆','鼓粒期灌溉','IRRIGATE',60,'促进鼓粒'),
('C096','红小豆','成熟前控水','IRRIGATE',75,'控水促熟'),
('C096','红小豆','成熟期采收','HARVEST',85,'适时收获');

-- 作物97: 薏米 C097
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C097','薏米','苗期除草','SPRAY',15,'清除杂草'),
('C097','薏米','分蘖期追肥','FERTILIZE',30,'促分蘖'),
('C097','薏米','拔节期灌溉','IRRIGATE',45,'保障水分'),
('C097','薏米','拔节期打药','SPRAY',60,'防治玉米螟'),
('C097','薏米','抽穗期追肥','FERTILIZE',75,'穗肥促粒重'),
('C097','薏米','灌浆期灌溉','IRRIGATE',90,'保障灌浆'),
('C097','薏米','成熟前控水','IRRIGATE',110,'控水促熟'),
('C097','薏米','成熟期采收','HARVEST',120,'适时收获');

-- 作物98: 黍子 C098
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C098','黍子','苗期除草','SPRAY',10,'及时间苗'),
('C098','黍子','分蘖期追肥','FERTILIZE',25,'促分蘖'),
('C098','黍子','拔节期灌溉','IRRIGATE',40,'保障水分'),
('C098','黍子','拔节期打药','SPRAY',50,'防治粟灰螟'),
('C098','黍子','抽穗期追肥','FERTILIZE',60,'穗肥促粒重'),
('C098','黍子','灌浆期打药','SPRAY',70,'防治穗螟'),
('C098','黍子','成熟前排水','IRRIGATE',80,'排水防倒伏'),
('C098','黍子','成熟期采收','HARVEST',90,'适时收获');

-- 作物99: 糜子 C099
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C099','糜子','苗期除草','SPRAY',10,'及时间苗'),
('C099','糜子','分蘖期追肥','FERTILIZE',25,'促分蘖'),
('C099','糜子','拔节期灌溉','IRRIGATE',40,'保障水分'),
('C099','糜子','拔节期打药','SPRAY',50,'防治蚜虫'),
('C099','糜子','抽穗期追肥','FERTILIZE',60,'穗肥促粒重'),
('C099','糜子','灌浆期灌溉','IRRIGATE',70,'保障灌浆'),
('C099','糜子','成熟前控水','IRRIGATE',80,'控水促熟'),
('C099','糜子','成熟期采收','HARVEST',90,'适时收获');

-- 作物100: 青稞 C100
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C100','青稞','苗期除草','SPRAY',15,'清除杂草'),
('C100','青稞','分蘖期追肥','FERTILIZE',30,'促分蘖'),
('C100','青稞','拔节期灌溉','IRRIGATE',45,'保障水分'),
('C100','青稞','拔节期打药','SPRAY',55,'防治蚜虫'),
('C100','青稞','抽穗期追肥','FERTILIZE',65,'穗肥促粒重'),
('C100','青稞','灌浆期打药','SPRAY',75,'防治赤霉病'),
('C100','青稞','成熟前控水','IRRIGATE',85,'控水促熟'),
('C100','青稞','成熟期采收','HARVEST',95,'适时收获');

-- 作物101-140: 蔬菜品种扩展
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C101','大葱','苗期除草','SPRAY',7,'苗期除草'),
('C101','大葱','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C101','大葱','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C101','大葱','生长期打药','SPRAY',30,'生长期打药'),
('C101','大葱','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C101','大葱','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C101','大葱','采收前控水','IRRIGATE',60,'采收前控水'),
('C101','大葱','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C102','分葱','苗期除草','SPRAY',7,'苗期除草'),
('C102','分葱','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C102','分葱','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C102','分葱','生长期打药','SPRAY',30,'生长期打药'),
('C102','分葱','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C102','分葱','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C102','分葱','采收前控水','IRRIGATE',60,'采收前控水'),
('C102','分葱','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C103','香葱','苗期除草','SPRAY',7,'苗期除草'),
('C103','香葱','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C103','香葱','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C103','香葱','生长期打药','SPRAY',30,'生长期打药'),
('C103','香葱','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C103','香葱','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C103','香葱','采收前控水','IRRIGATE',60,'采收前控水'),
('C103','香葱','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C104','韭黄','苗期除草','SPRAY',7,'苗期除草'),
('C104','韭黄','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C104','韭黄','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C104','韭黄','生长期打药','SPRAY',30,'生长期打药'),
('C104','韭黄','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C104','韭黄','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C104','韭黄','采收前控水','IRRIGATE',60,'采收前控水'),
('C104','韭黄','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C105','蒜薹','苗期除草','SPRAY',7,'苗期除草'),
('C105','蒜薹','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C105','蒜薹','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C105','蒜薹','生长期打药','SPRAY',30,'生长期打药'),
('C105','蒜薹','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C105','蒜薹','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C105','蒜薹','采收前控水','IRRIGATE',60,'采收前控水'),
('C105','蒜薹','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C106','芥菜','苗期除草','SPRAY',7,'苗期除草'),
('C106','芥菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C106','芥菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C106','芥菜','生长期打药','SPRAY',30,'生长期打药'),
('C106','芥菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C106','芥菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C106','芥菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C106','芥菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C107','雪里蕻','苗期除草','SPRAY',7,'苗期除草'),
('C107','雪里蕻','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C107','雪里蕻','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C107','雪里蕻','生长期打药','SPRAY',30,'生长期打药'),
('C107','雪里蕻','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C107','雪里蕻','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C107','雪里蕻','采收前控水','IRRIGATE',60,'采收前控水'),
('C107','雪里蕻','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C108','榨菜','苗期除草','SPRAY',7,'苗期除草'),
('C108','榨菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C108','榨菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C108','榨菜','生长期打药','SPRAY',30,'生长期打药'),
('C108','榨菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C108','榨菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C108','榨菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C108','榨菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C109','大头菜','苗期除草','SPRAY',7,'苗期除草'),
('C109','大头菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C109','大头菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C109','大头菜','生长期打药','SPRAY',30,'生长期打药'),
('C109','大头菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C109','大头菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C109','大头菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C109','大头菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C110','儿菜','苗期除草','SPRAY',7,'苗期除草'),
('C110','儿菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C110','儿菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C110','儿菜','生长期打药','SPRAY',30,'生长期打药'),
('C110','儿菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C110','儿菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C110','儿菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C110','儿菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C111','抱子甘蓝','苗期除草','SPRAY',7,'苗期除草'),
('C111','抱子甘蓝','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C111','抱子甘蓝','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C111','抱子甘蓝','生长期打药','SPRAY',30,'生长期打药'),
('C111','抱子甘蓝','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C111','抱子甘蓝','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C111','抱子甘蓝','采收前控水','IRRIGATE',60,'采收前控水'),
('C111','抱子甘蓝','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C112','羽衣甘蓝','苗期除草','SPRAY',7,'苗期除草'),
('C112','羽衣甘蓝','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C112','羽衣甘蓝','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C112','羽衣甘蓝','生长期打药','SPRAY',30,'生长期打药'),
('C112','羽衣甘蓝','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C112','羽衣甘蓝','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C112','羽衣甘蓝','采收前控水','IRRIGATE',60,'采收前控水'),
('C112','羽衣甘蓝','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C113','紫甘蓝','苗期除草','SPRAY',7,'苗期除草'),
('C113','紫甘蓝','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C113','紫甘蓝','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C113','紫甘蓝','生长期打药','SPRAY',30,'生长期打药'),
('C113','紫甘蓝','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C113','紫甘蓝','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C113','紫甘蓝','采收前控水','IRRIGATE',60,'采收前控水'),
('C113','紫甘蓝','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C114','芥蓝','苗期除草','SPRAY',7,'苗期除草'),
('C114','芥蓝','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C114','芥蓝','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C114','芥蓝','生长期打药','SPRAY',30,'生长期打药'),
('C114','芥蓝','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C114','芥蓝','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C114','芥蓝','采收前控水','IRRIGATE',60,'采收前控水'),
('C114','芥蓝','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C115','菜心','苗期除草','SPRAY',7,'苗期除草'),
('C115','菜心','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C115','菜心','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C115','菜心','生长期打药','SPRAY',30,'生长期打药'),
('C115','菜心','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C115','菜心','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C115','菜心','采收前控水','IRRIGATE',60,'采收前控水'),
('C115','菜心','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C116','奶白菜','苗期除草','SPRAY',7,'苗期除草'),
('C116','奶白菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C116','奶白菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C116','奶白菜','生长期打药','SPRAY',30,'生长期打药'),
('C116','奶白菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C116','奶白菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C116','奶白菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C116','奶白菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C117','乌塌菜','苗期除草','SPRAY',7,'苗期除草'),
('C117','乌塌菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C117','乌塌菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C117','乌塌菜','生长期打药','SPRAY',30,'生长期打药'),
('C117','乌塌菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C117','乌塌菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C117','乌塌菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C117','乌塌菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C118','木耳菜','苗期除草','SPRAY',7,'苗期除草'),
('C118','木耳菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C118','木耳菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C118','木耳菜','生长期打药','SPRAY',30,'生长期打药'),
('C118','木耳菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C118','木耳菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C118','木耳菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C118','木耳菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C119','秋葵','苗期除草','SPRAY',7,'苗期除草'),
('C119','秋葵','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C119','秋葵','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C119','秋葵','生长期打药','SPRAY',30,'生长期打药'),
('C119','秋葵','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C119','秋葵','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C119','秋葵','采收前控水','IRRIGATE',60,'采收前控水'),
('C119','秋葵','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C120','黄秋葵','苗期除草','SPRAY',7,'苗期除草'),
('C120','黄秋葵','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C120','黄秋葵','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C120','黄秋葵','生长期打药','SPRAY',30,'生长期打药'),
('C120','黄秋葵','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C120','黄秋葵','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C120','黄秋葵','采收前控水','IRRIGATE',60,'采收前控水'),
('C120','黄秋葵','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C121','红秋葵','苗期除草','SPRAY',7,'苗期除草'),
('C121','红秋葵','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C121','红秋葵','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C121','红秋葵','生长期打药','SPRAY',30,'生长期打药'),
('C121','红秋葵','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C121','红秋葵','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C121','红秋葵','采收前控水','IRRIGATE',60,'采收前控水'),
('C121','红秋葵','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C122','娃娃菜','苗期除草','SPRAY',7,'苗期除草'),
('C122','娃娃菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C122','娃娃菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C122','娃娃菜','生长期打药','SPRAY',30,'生长期打药'),
('C122','娃娃菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C122','娃娃菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C122','娃娃菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C122','娃娃菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C123','快菜','苗期除草','SPRAY',7,'苗期除草'),
('C123','快菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C123','快菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C123','快菜','生长期打药','SPRAY',30,'生长期打药'),
('C123','快菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C123','快菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C123','快菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C123','快菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C124','鸡毛菜','苗期除草','SPRAY',7,'苗期除草'),
('C124','鸡毛菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C124','鸡毛菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C124','鸡毛菜','生长期打药','SPRAY',30,'生长期打药'),
('C124','鸡毛菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C124','鸡毛菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C124','鸡毛菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C124','鸡毛菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C125','油麦菜','苗期除草','SPRAY',7,'苗期除草'),
('C125','油麦菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C125','油麦菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C125','油麦菜','生长期打药','SPRAY',30,'生长期打药'),
('C125','油麦菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C125','油麦菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C125','油麦菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C125','油麦菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C126','苦菊','苗期除草','SPRAY',7,'苗期除草'),
('C126','苦菊','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C126','苦菊','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C126','苦菊','生长期打药','SPRAY',30,'生长期打药'),
('C126','苦菊','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C126','苦菊','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C126','苦菊','采收前控水','IRRIGATE',60,'采收前控水'),
('C126','苦菊','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C127','芝麻菜','苗期除草','SPRAY',7,'苗期除草'),
('C127','芝麻菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C127','芝麻菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C127','芝麻菜','生长期打药','SPRAY',30,'生长期打药'),
('C127','芝麻菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C127','芝麻菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C127','芝麻菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C127','芝麻菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C128','穿心莲','苗期除草','SPRAY',7,'苗期除草'),
('C128','穿心莲','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C128','穿心莲','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C128','穿心莲','生长期打药','SPRAY',30,'生长期打药'),
('C128','穿心莲','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C128','穿心莲','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C128','穿心莲','采收前控水','IRRIGATE',60,'采收前控水'),
('C128','穿心莲','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C129','紫苏','苗期除草','SPRAY',7,'苗期除草'),
('C129','紫苏','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C129','紫苏','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C129','紫苏','生长期打药','SPRAY',30,'生长期打药'),
('C129','紫苏','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C129','紫苏','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C129','紫苏','采收前控水','IRRIGATE',60,'采收前控水'),
('C129','紫苏','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C130','罗勒','苗期除草','SPRAY',7,'苗期除草'),
('C130','罗勒','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C130','罗勒','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C130','罗勒','生长期打药','SPRAY',30,'生长期打药'),
('C130','罗勒','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C130','罗勒','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C130','罗勒','采收前控水','IRRIGATE',60,'采收前控水'),
('C130','罗勒','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C131','香椿','苗期除草','SPRAY',7,'苗期除草'),
('C131','香椿','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C131','香椿','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C131','香椿','生长期打药','SPRAY',30,'生长期打药'),
('C131','香椿','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C131','香椿','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C131','香椿','采收前控水','IRRIGATE',60,'采收前控水'),
('C131','香椿','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C132','蕨菜','苗期除草','SPRAY',7,'苗期除草'),
('C132','蕨菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C132','蕨菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C132','蕨菜','生长期打药','SPRAY',30,'生长期打药'),
('C132','蕨菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C132','蕨菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C132','蕨菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C132','蕨菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C133','马齿苋','苗期除草','SPRAY',7,'苗期除草'),
('C133','马齿苋','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C133','马齿苋','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C133','马齿苋','生长期打药','SPRAY',30,'生长期打药'),
('C133','马齿苋','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C133','马齿苋','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C133','马齿苋','采收前控水','IRRIGATE',60,'采收前控水'),
('C133','马齿苋','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C134','蒲公英','苗期除草','SPRAY',7,'苗期除草'),
('C134','蒲公英','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C134','蒲公英','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C134','蒲公英','生长期打药','SPRAY',30,'生长期打药'),
('C134','蒲公英','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C134','蒲公英','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C134','蒲公英','采收前控水','IRRIGATE',60,'采收前控水'),
('C134','蒲公英','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C135','鱼腥草','苗期除草','SPRAY',7,'苗期除草'),
('C135','鱼腥草','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C135','鱼腥草','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C135','鱼腥草','生长期打药','SPRAY',30,'生长期打药'),
('C135','鱼腥草','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C135','鱼腥草','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C135','鱼腥草','采收前控水','IRRIGATE',60,'采收前控水'),
('C135','鱼腥草','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C136','益母草','苗期除草','SPRAY',7,'苗期除草'),
('C136','益母草','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C136','益母草','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C136','益母草','生长期打药','SPRAY',30,'生长期打药'),
('C136','益母草','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C136','益母草','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C136','益母草','采收前控水','IRRIGATE',60,'采收前控水'),
('C136','益母草','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C137','车前草','苗期除草','SPRAY',7,'苗期除草'),
('C137','车前草','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C137','车前草','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C137','车前草','生长期打药','SPRAY',30,'生长期打药'),
('C137','车前草','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C137','车前草','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C137','车前草','采收前控水','IRRIGATE',60,'采收前控水'),
('C137','车前草','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C138','紫背天葵','苗期除草','SPRAY',7,'苗期除草'),
('C138','紫背天葵','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C138','紫背天葵','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C138','紫背天葵','生长期打药','SPRAY',30,'生长期打药'),
('C138','紫背天葵','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C138','紫背天葵','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C138','紫背天葵','采收前控水','IRRIGATE',60,'采收前控水'),
('C138','紫背天葵','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C139','雪莲果','苗期除草','SPRAY',7,'苗期除草'),
('C139','雪莲果','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C139','雪莲果','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C139','雪莲果','生长期打药','SPRAY',30,'生长期打药'),
('C139','雪莲果','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C139','雪莲果','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C139','雪莲果','采收前控水','IRRIGATE',60,'采收前控水'),
('C139','雪莲果','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C140','佛手瓜','苗期除草','SPRAY',7,'苗期除草'),
('C140','佛手瓜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C140','佛手瓜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C140','佛手瓜','生长期打药','SPRAY',30,'生长期打药'),
('C140','佛手瓜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C140','佛手瓜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C140','佛手瓜','采收前控水','IRRIGATE',60,'采收前控水'),
('C140','佛手瓜','成熟期采收','HARVEST',70,'成熟期采收');

-- 作物141-180: 水果品种扩展
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C141','红富士','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C141','红富士','花前打药','FERTILIZE',35,'花前打药'),
('C141','红富士','花后追肥','IRRIGATE',50,'花后追肥'),
('C141','红富士','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C141','红富士','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C141','红富士','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C141','红富士','采收前控水','IRRIGATE',120,'采收前控水'),
('C141','红富士','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C142','嘎啦果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C142','嘎啦果','花前打药','FERTILIZE',35,'花前打药'),
('C142','嘎啦果','花后追肥','IRRIGATE',50,'花后追肥'),
('C142','嘎啦果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C142','嘎啦果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C142','嘎啦果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C142','嘎啦果','采收前控水','IRRIGATE',120,'采收前控水'),
('C142','嘎啦果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C143','国光苹果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C143','国光苹果','花前打药','FERTILIZE',35,'花前打药'),
('C143','国光苹果','花后追肥','IRRIGATE',50,'花后追肥'),
('C143','国光苹果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C143','国光苹果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C143','国光苹果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C143','国光苹果','采收前控水','IRRIGATE',120,'采收前控水'),
('C143','国光苹果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C144','鸭梨','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C144','鸭梨','花前打药','FERTILIZE',35,'花前打药'),
('C144','鸭梨','花后追肥','IRRIGATE',50,'花后追肥'),
('C144','鸭梨','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C144','鸭梨','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C144','鸭梨','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C144','鸭梨','采收前控水','IRRIGATE',120,'采收前控水'),
('C144','鸭梨','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C145','雪花梨','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C145','雪花梨','花前打药','FERTILIZE',35,'花前打药'),
('C145','雪花梨','花后追肥','IRRIGATE',50,'花后追肥'),
('C145','雪花梨','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C145','雪花梨','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C145','雪花梨','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C145','雪花梨','采收前控水','IRRIGATE',120,'采收前控水'),
('C145','雪花梨','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C146','砀山梨','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C146','砀山梨','花前打药','FERTILIZE',35,'花前打药'),
('C146','砀山梨','花后追肥','IRRIGATE',50,'花后追肥'),
('C146','砀山梨','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C146','砀山梨','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C146','砀山梨','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C146','砀山梨','采收前控水','IRRIGATE',120,'采收前控水'),
('C146','砀山梨','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C147','库尔勒梨','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C147','库尔勒梨','花前打药','FERTILIZE',35,'花前打药'),
('C147','库尔勒梨','花后追肥','IRRIGATE',50,'花后追肥'),
('C147','库尔勒梨','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C147','库尔勒梨','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C147','库尔勒梨','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C147','库尔勒梨','采收前控水','IRRIGATE',120,'采收前控水'),
('C147','库尔勒梨','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C148','水蜜桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C148','水蜜桃','花前打药','FERTILIZE',35,'花前打药'),
('C148','水蜜桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C148','水蜜桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C148','水蜜桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C148','水蜜桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C148','水蜜桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C148','水蜜桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C149','油桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C149','油桃','花前打药','FERTILIZE',35,'花前打药'),
('C149','油桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C149','油桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C149','油桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C149','油桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C149','油桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C149','油桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C150','蟠桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C150','蟠桃','花前打药','FERTILIZE',35,'花前打药'),
('C150','蟠桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C150','蟠桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C150','蟠桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C150','蟠桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C150','蟠桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C150','蟠桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C151','黄桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C151','黄桃','花前打药','FERTILIZE',35,'花前打药'),
('C151','黄桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C151','黄桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C151','黄桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C151','黄桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C151','黄桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C151','黄桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C152','冬枣','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C152','冬枣','花前打药','FERTILIZE',35,'花前打药'),
('C152','冬枣','花后追肥','IRRIGATE',50,'花后追肥'),
('C152','冬枣','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C152','冬枣','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C152','冬枣','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C152','冬枣','采收前控水','IRRIGATE',120,'采收前控水'),
('C152','冬枣','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C153','灰枣','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C153','灰枣','花前打药','FERTILIZE',35,'花前打药'),
('C153','灰枣','花后追肥','IRRIGATE',50,'花后追肥'),
('C153','灰枣','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C153','灰枣','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C153','灰枣','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C153','灰枣','采收前控水','IRRIGATE',120,'采收前控水'),
('C153','灰枣','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C154','骏枣','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C154','骏枣','花前打药','FERTILIZE',35,'花前打药'),
('C154','骏枣','花后追肥','IRRIGATE',50,'花后追肥'),
('C154','骏枣','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C154','骏枣','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C154','骏枣','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C154','骏枣','采收前控水','IRRIGATE',120,'采收前控水'),
('C154','骏枣','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C155','板栗','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C155','板栗','花前打药','FERTILIZE',35,'花前打药'),
('C155','板栗','花后追肥','IRRIGATE',50,'花后追肥'),
('C155','板栗','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C155','板栗','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C155','板栗','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C155','板栗','采收前控水','IRRIGATE',120,'采收前控水'),
('C155','板栗','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C156','核桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C156','核桃','花前打药','FERTILIZE',35,'花前打药'),
('C156','核桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C156','核桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C156','核桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C156','核桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C156','核桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C156','核桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C157','巴旦木','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C157','巴旦木','花前打药','FERTILIZE',35,'花前打药'),
('C157','巴旦木','花后追肥','IRRIGATE',50,'花后追肥'),
('C157','巴旦木','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C157','巴旦木','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C157','巴旦木','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C157','巴旦木','采收前控水','IRRIGATE',120,'采收前控水'),
('C157','巴旦木','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C158','开心果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C158','开心果','花前打药','FERTILIZE',35,'花前打药'),
('C158','开心果','花后追肥','IRRIGATE',50,'花后追肥'),
('C158','开心果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C158','开心果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C158','开心果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C158','开心果','采收前控水','IRRIGATE',120,'采收前控水'),
('C158','开心果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C159','碧根果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C159','碧根果','花前打药','FERTILIZE',35,'花前打药'),
('C159','碧根果','花后追肥','IRRIGATE',50,'花后追肥'),
('C159','碧根果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C159','碧根果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C159','碧根果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C159','碧根果','采收前控水','IRRIGATE',120,'采收前控水'),
('C159','碧根果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C160','夏威夷果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C160','夏威夷果','花前打药','FERTILIZE',35,'花前打药'),
('C160','夏威夷果','花后追肥','IRRIGATE',50,'花后追肥'),
('C160','夏威夷果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C160','夏威夷果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C160','夏威夷果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C160','夏威夷果','采收前控水','IRRIGATE',120,'采收前控水'),
('C160','夏威夷果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C161','腰果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C161','腰果','花前打药','FERTILIZE',35,'花前打药'),
('C161','腰果','花后追肥','IRRIGATE',50,'花后追肥'),
('C161','腰果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C161','腰果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C161','腰果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C161','腰果','采收前控水','IRRIGATE',120,'采收前控水'),
('C161','腰果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C162','榛子','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C162','榛子','花前打药','FERTILIZE',35,'花前打药'),
('C162','榛子','花后追肥','IRRIGATE',50,'花后追肥'),
('C162','榛子','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C162','榛子','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C162','榛子','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C162','榛子','采收前控水','IRRIGATE',120,'采收前控水'),
('C162','榛子','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C163','松子','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C163','松子','花前打药','FERTILIZE',35,'花前打药'),
('C163','松子','花后追肥','IRRIGATE',50,'花后追肥'),
('C163','松子','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C163','松子','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C163','松子','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C163','松子','采收前控水','IRRIGATE',120,'采收前控水'),
('C163','松子','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C164','莲子','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C164','莲子','花前打药','FERTILIZE',35,'花前打药'),
('C164','莲子','花后追肥','IRRIGATE',50,'花后追肥'),
('C164','莲子','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C164','莲子','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C164','莲子','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C164','莲子','采收前控水','IRRIGATE',120,'采收前控水'),
('C164','莲子','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C165','桂圆','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C165','桂圆','花前打药','FERTILIZE',35,'花前打药'),
('C165','桂圆','花后追肥','IRRIGATE',50,'花后追肥'),
('C165','桂圆','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C165','桂圆','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C165','桂圆','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C165','桂圆','采收前控水','IRRIGATE',120,'采收前控水'),
('C165','桂圆','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C166','荔枝','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C166','荔枝','花前打药','FERTILIZE',35,'花前打药'),
('C166','荔枝','花后追肥','IRRIGATE',50,'花后追肥'),
('C166','荔枝','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C166','荔枝','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C166','荔枝','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C166','荔枝','采收前控水','IRRIGATE',120,'采收前控水'),
('C166','荔枝','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C167','龙眼','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C167','龙眼','花前打药','FERTILIZE',35,'花前打药'),
('C167','龙眼','花后追肥','IRRIGATE',50,'花后追肥'),
('C167','龙眼','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C167','龙眼','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C167','龙眼','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C167','龙眼','采收前控水','IRRIGATE',120,'采收前控水'),
('C167','龙眼','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C168','山竹','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C168','山竹','花前打药','FERTILIZE',35,'花前打药'),
('C168','山竹','花后追肥','IRRIGATE',50,'花后追肥'),
('C168','山竹','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C168','山竹','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C168','山竹','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C168','山竹','采收前控水','IRRIGATE',120,'采收前控水'),
('C168','山竹','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C169','榴莲','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C169','榴莲','花前打药','FERTILIZE',35,'花前打药'),
('C169','榴莲','花后追肥','IRRIGATE',50,'花后追肥'),
('C169','榴莲','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C169','榴莲','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C169','榴莲','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C169','榴莲','采收前控水','IRRIGATE',120,'采收前控水'),
('C169','榴莲','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C170','菠萝','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C170','菠萝','花前打药','FERTILIZE',35,'花前打药'),
('C170','菠萝','花后追肥','IRRIGATE',50,'花后追肥'),
('C170','菠萝','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C170','菠萝','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C170','菠萝','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C170','菠萝','采收前控水','IRRIGATE',120,'采收前控水'),
('C170','菠萝','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C171','凤梨','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C171','凤梨','花前打药','FERTILIZE',35,'花前打药'),
('C171','凤梨','花后追肥','IRRIGATE',50,'花后追肥'),
('C171','凤梨','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C171','凤梨','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C171','凤梨','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C171','凤梨','采收前控水','IRRIGATE',120,'采收前控水'),
('C171','凤梨','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C172','椰子','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C172','椰子','花前打药','FERTILIZE',35,'花前打药'),
('C172','椰子','花后追肥','IRRIGATE',50,'花后追肥'),
('C172','椰子','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C172','椰子','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C172','椰子','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C172','椰子','采收前控水','IRRIGATE',120,'采收前控水'),
('C172','椰子','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C173','芭乐','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C173','芭乐','花前打药','FERTILIZE',35,'花前打药'),
('C173','芭乐','花后追肥','IRRIGATE',50,'花后追肥'),
('C173','芭乐','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C173','芭乐','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C173','芭乐','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C173','芭乐','采收前控水','IRRIGATE',120,'采收前控水'),
('C173','芭乐','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C174','番石榴','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C174','番石榴','花前打药','FERTILIZE',35,'花前打药'),
('C174','番石榴','花后追肥','IRRIGATE',50,'花后追肥'),
('C174','番石榴','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C174','番石榴','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C174','番石榴','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C174','番石榴','采收前控水','IRRIGATE',120,'采收前控水'),
('C174','番石榴','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C175','莲雾','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C175','莲雾','花前打药','FERTILIZE',35,'花前打药'),
('C175','莲雾','花后追肥','IRRIGATE',50,'花后追肥'),
('C175','莲雾','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C175','莲雾','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C175','莲雾','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C175','莲雾','采收前控水','IRRIGATE',120,'采收前控水'),
('C175','莲雾','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C176','杨桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C176','杨桃','花前打药','FERTILIZE',35,'花前打药'),
('C176','杨桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C176','杨桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C176','杨桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C176','杨桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C176','杨桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C176','杨桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C177','释迦果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C177','释迦果','花前打药','FERTILIZE',35,'花前打药'),
('C177','释迦果','花后追肥','IRRIGATE',50,'花后追肥'),
('C177','释迦果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C177','释迦果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C177','释迦果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C177','释迦果','采收前控水','IRRIGATE',120,'采收前控水'),
('C177','释迦果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C178','无花果','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C178','无花果','花前打药','FERTILIZE',35,'花前打药'),
('C178','无花果','花后追肥','IRRIGATE',50,'花后追肥'),
('C178','无花果','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C178','无花果','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C178','无花果','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C178','无花果','采收前控水','IRRIGATE',120,'采收前控水'),
('C178','无花果','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C179','桑葚','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C179','桑葚','花前打药','FERTILIZE',35,'花前打药'),
('C179','桑葚','花后追肥','IRRIGATE',50,'花后追肥'),
('C179','桑葚','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C179','桑葚','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C179','桑葚','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C179','桑葚','采收前控水','IRRIGATE',120,'采收前控水'),
('C179','桑葚','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C180','蓝莓','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C180','蓝莓','花前打药','FERTILIZE',35,'花前打药'),
('C180','蓝莓','花后追肥','IRRIGATE',50,'花后追肥'),
('C180','蓝莓','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C180','蓝莓','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C180','蓝莓','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C180','蓝莓','采收前控水','IRRIGATE',120,'采收前控水'),
('C180','蓝莓','成熟期采收','HARVEST',140,'成熟期采收');

-- 作物181-200: 食用菌+水生
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C181','香菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C181','香菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C181','香菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C181','香菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C181','香菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C181','香菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C181','香菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C181','香菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C182','平菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C182','平菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C182','平菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C182','平菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C182','平菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C182','平菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C182','平菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C182','平菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C183','金针菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C183','金针菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C183','金针菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C183','金针菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C183','金针菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C183','金针菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C183','金针菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C183','金针菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C184','杏鲍菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C184','杏鲍菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C184','杏鲍菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C184','杏鲍菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C184','杏鲍菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C184','杏鲍菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C184','杏鲍菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C184','杏鲍菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C185','白灵菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C185','白灵菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C185','白灵菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C185','白灵菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C185','白灵菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C185','白灵菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C185','白灵菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C185','白灵菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C186','茶树菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C186','茶树菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C186','茶树菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C186','茶树菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C186','茶树菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C186','茶树菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C186','茶树菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C186','茶树菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C187','猴头菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C187','猴头菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C187','猴头菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C187','猴头菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C187','猴头菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C187','猴头菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C187','猴头菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C187','猴头菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C188','竹荪','发菌期控温','SPRAY',10,'发菌期控温'),
('C188','竹荪','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C188','竹荪','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C188','竹荪','出菇期打药','SPRAY',40,'出菇期打药'),
('C188','竹荪','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C188','竹荪','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C188','竹荪','二茬打药','IRRIGATE',60,'二茬打药'),
('C188','竹荪','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C189','木耳菌','发菌期控温','SPRAY',10,'发菌期控温'),
('C189','木耳菌','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C189','木耳菌','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C189','木耳菌','出菇期打药','SPRAY',40,'出菇期打药'),
('C189','木耳菌','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C189','木耳菌','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C189','木耳菌','二茬打药','IRRIGATE',60,'二茬打药'),
('C189','木耳菌','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C190','银耳','发菌期控温','SPRAY',10,'发菌期控温'),
('C190','银耳','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C190','银耳','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C190','银耳','出菇期打药','SPRAY',40,'出菇期打药'),
('C190','银耳','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C190','银耳','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C190','银耳','二茬打药','IRRIGATE',60,'二茬打药'),
('C190','银耳','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C191','松茸','发菌期控温','SPRAY',10,'发菌期控温'),
('C191','松茸','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C191','松茸','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C191','松茸','出菇期打药','SPRAY',40,'出菇期打药'),
('C191','松茸','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C191','松茸','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C191','松茸','二茬打药','IRRIGATE',60,'二茬打药'),
('C191','松茸','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C192','羊肚菌','发菌期控温','SPRAY',10,'发菌期控温'),
('C192','羊肚菌','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C192','羊肚菌','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C192','羊肚菌','出菇期打药','SPRAY',40,'出菇期打药'),
('C192','羊肚菌','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C192','羊肚菌','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C192','羊肚菌','二茬打药','IRRIGATE',60,'二茬打药'),
('C192','羊肚菌','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C193','鸡枞菌','发菌期控温','SPRAY',10,'发菌期控温'),
('C193','鸡枞菌','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C193','鸡枞菌','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C193','鸡枞菌','出菇期打药','SPRAY',40,'出菇期打药'),
('C193','鸡枞菌','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C193','鸡枞菌','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C193','鸡枞菌','二茬打药','IRRIGATE',60,'二茬打药'),
('C193','鸡枞菌','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C194','灰树花','发菌期控温','SPRAY',10,'发菌期控温'),
('C194','灰树花','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C194','灰树花','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C194','灰树花','出菇期打药','SPRAY',40,'出菇期打药'),
('C194','灰树花','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C194','灰树花','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C194','灰树花','二茬打药','IRRIGATE',60,'二茬打药'),
('C194','灰树花','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C195','双孢菇','发菌期控温','SPRAY',10,'发菌期控温'),
('C195','双孢菇','催蕾期追肥','FERTILIZE',20,'催蕾期追肥'),
('C195','双孢菇','出菇期灌溉','IRRIGATE',30,'出菇期灌溉'),
('C195','双孢菇','出菇期打药','SPRAY',40,'出菇期打药'),
('C195','双孢菇','采收后追肥','FERTILIZE',50,'采收后追肥'),
('C195','双孢菇','二茬灌溉','IRRIGATE',55,'二茬灌溉'),
('C195','双孢菇','二茬打药','IRRIGATE',60,'二茬打药'),
('C195','双孢菇','二茬采收','HARVEST',65,'二茬采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C196','水芹','缓苗期灌溉','SPRAY',15,'缓苗期灌溉'),
('C196','水芹','生长期追肥','FERTILIZE',25,'生长期追肥'),
('C196','水芹','生长期灌溉','IRRIGATE',40,'生长期灌溉'),
('C196','水芹','生长期打药','SPRAY',55,'生长期打药'),
('C196','水芹','旺盛期追肥','FERTILIZE',65,'旺盛期追肥'),
('C196','水芹','旺盛期灌溉','IRRIGATE',80,'旺盛期灌溉'),
('C196','水芹','成熟前控水','IRRIGATE',95,'成熟前控水'),
('C196','水芹','成熟期采收','HARVEST',110,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C197','莼菜','缓苗期灌溉','SPRAY',15,'缓苗期灌溉'),
('C197','莼菜','生长期追肥','FERTILIZE',25,'生长期追肥'),
('C197','莼菜','生长期灌溉','IRRIGATE',40,'生长期灌溉'),
('C197','莼菜','生长期打药','SPRAY',55,'生长期打药'),
('C197','莼菜','旺盛期追肥','FERTILIZE',65,'旺盛期追肥'),
('C197','莼菜','旺盛期灌溉','IRRIGATE',80,'旺盛期灌溉'),
('C197','莼菜','成熟前控水','IRRIGATE',95,'成熟前控水'),
('C197','莼菜','成熟期采收','HARVEST',110,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C198','蒲菜','缓苗期灌溉','SPRAY',15,'缓苗期灌溉'),
('C198','蒲菜','生长期追肥','FERTILIZE',25,'生长期追肥'),
('C198','蒲菜','生长期灌溉','IRRIGATE',40,'生长期灌溉'),
('C198','蒲菜','生长期打药','SPRAY',55,'生长期打药'),
('C198','蒲菜','旺盛期追肥','FERTILIZE',65,'旺盛期追肥'),
('C198','蒲菜','旺盛期灌溉','IRRIGATE',80,'旺盛期灌溉'),
('C198','蒲菜','成熟前控水','IRRIGATE',95,'成熟前控水'),
('C198','蒲菜','成熟期采收','HARVEST',110,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C199','菱角','缓苗期灌溉','SPRAY',15,'缓苗期灌溉'),
('C199','菱角','生长期追肥','FERTILIZE',25,'生长期追肥'),
('C199','菱角','生长期灌溉','IRRIGATE',40,'生长期灌溉'),
('C199','菱角','生长期打药','SPRAY',55,'生长期打药'),
('C199','菱角','旺盛期追肥','FERTILIZE',65,'旺盛期追肥'),
('C199','菱角','旺盛期灌溉','IRRIGATE',80,'旺盛期灌溉'),
('C199','菱角','成熟前控水','IRRIGATE',95,'成熟前控水'),
('C199','菱角','成熟期采收','HARVEST',110,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C200','芡实','缓苗期灌溉','SPRAY',15,'缓苗期灌溉'),
('C200','芡实','生长期追肥','FERTILIZE',25,'生长期追肥'),
('C200','芡实','生长期灌溉','IRRIGATE',40,'生长期灌溉'),
('C200','芡实','生长期打药','SPRAY',55,'生长期打药'),
('C200','芡实','旺盛期追肥','FERTILIZE',65,'旺盛期追肥'),
('C200','芡实','旺盛期灌溉','IRRIGATE',80,'旺盛期灌溉'),
('C200','芡实','成熟前控水','IRRIGATE',95,'成熟前控水'),
('C200','芡实','成熟期采收','HARVEST',110,'成熟期采收');

-- 作物201-230: 药材扩展
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C201','桔梗','苗期除草','SPRAY',15,'苗期除草'),
('C201','桔梗','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C201','桔梗','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C201','桔梗','生长期打药','SPRAY',70,'生长期打药'),
('C201','桔梗','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C201','桔梗','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C201','桔梗','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C201','桔梗','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C202','麦冬','苗期除草','SPRAY',15,'苗期除草'),
('C202','麦冬','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C202','麦冬','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C202','麦冬','生长期打药','SPRAY',70,'生长期打药'),
('C202','麦冬','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C202','麦冬','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C202','麦冬','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C202','麦冬','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C203','柴胡','苗期除草','SPRAY',15,'苗期除草'),
('C203','柴胡','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C203','柴胡','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C203','柴胡','生长期打药','SPRAY',70,'生长期打药'),
('C203','柴胡','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C203','柴胡','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C203','柴胡','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C203','柴胡','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C204','白术','苗期除草','SPRAY',15,'苗期除草'),
('C204','白术','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C204','白术','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C204','白术','生长期打药','SPRAY',70,'生长期打药'),
('C204','白术','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C204','白术','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C204','白术','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C204','白术','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C205','白芍','苗期除草','SPRAY',15,'苗期除草'),
('C205','白芍','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C205','白芍','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C205','白芍','生长期打药','SPRAY',70,'生长期打药'),
('C205','白芍','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C205','白芍','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C205','白芍','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C205','白芍','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C206','甘草','苗期除草','SPRAY',15,'苗期除草'),
('C206','甘草','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C206','甘草','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C206','甘草','生长期打药','SPRAY',70,'生长期打药'),
('C206','甘草','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C206','甘草','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C206','甘草','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C206','甘草','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C207','苦参','苗期除草','SPRAY',15,'苗期除草'),
('C207','苦参','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C207','苦参','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C207','苦参','生长期打药','SPRAY',70,'生长期打药'),
('C207','苦参','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C207','苦参','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C207','苦参','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C207','苦参','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C208','黄连','苗期除草','SPRAY',15,'苗期除草'),
('C208','黄连','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C208','黄连','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C208','黄连','生长期打药','SPRAY',70,'生长期打药'),
('C208','黄连','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C208','黄连','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C208','黄连','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C208','黄连','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C209','黄柏','苗期除草','SPRAY',15,'苗期除草'),
('C209','黄柏','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C209','黄柏','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C209','黄柏','生长期打药','SPRAY',70,'生长期打药'),
('C209','黄柏','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C209','黄柏','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C209','黄柏','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C209','黄柏','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C210','杜仲','苗期除草','SPRAY',15,'苗期除草'),
('C210','杜仲','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C210','杜仲','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C210','杜仲','生长期打药','SPRAY',70,'生长期打药'),
('C210','杜仲','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C210','杜仲','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C210','杜仲','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C210','杜仲','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C211','厚朴','苗期除草','SPRAY',15,'苗期除草'),
('C211','厚朴','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C211','厚朴','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C211','厚朴','生长期打药','SPRAY',70,'生长期打药'),
('C211','厚朴','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C211','厚朴','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C211','厚朴','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C211','厚朴','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C212','肉桂','苗期除草','SPRAY',15,'苗期除草'),
('C212','肉桂','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C212','肉桂','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C212','肉桂','生长期打药','SPRAY',70,'生长期打药'),
('C212','肉桂','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C212','肉桂','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C212','肉桂','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C212','肉桂','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C213','五味子','苗期除草','SPRAY',15,'苗期除草'),
('C213','五味子','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C213','五味子','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C213','五味子','生长期打药','SPRAY',70,'生长期打药'),
('C213','五味子','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C213','五味子','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C213','五味子','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C213','五味子','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C214','决明子','苗期除草','SPRAY',15,'苗期除草'),
('C214','决明子','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C214','决明子','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C214','决明子','生长期打药','SPRAY',70,'生长期打药'),
('C214','决明子','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C214','决明子','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C214','决明子','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C214','决明子','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C215','薏苡仁','苗期除草','SPRAY',15,'苗期除草'),
('C215','薏苡仁','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C215','薏苡仁','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C215','薏苡仁','生长期打药','SPRAY',70,'生长期打药'),
('C215','薏苡仁','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C215','薏苡仁','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C215','薏苡仁','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C215','薏苡仁','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C216','车前子','苗期除草','SPRAY',15,'苗期除草'),
('C216','车前子','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C216','车前子','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C216','车前子','生长期打药','SPRAY',70,'生长期打药'),
('C216','车前子','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C216','车前子','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C216','车前子','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C216','车前子','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C217','菟丝子','苗期除草','SPRAY',15,'苗期除草'),
('C217','菟丝子','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C217','菟丝子','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C217','菟丝子','生长期打药','SPRAY',70,'生长期打药'),
('C217','菟丝子','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C217','菟丝子','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C217','菟丝子','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C217','菟丝子','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C218','蛇床子','苗期除草','SPRAY',15,'苗期除草'),
('C218','蛇床子','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C218','蛇床子','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C218','蛇床子','生长期打药','SPRAY',70,'生长期打药'),
('C218','蛇床子','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C218','蛇床子','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C218','蛇床子','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C218','蛇床子','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C219','山茱萸','苗期除草','SPRAY',15,'苗期除草'),
('C219','山茱萸','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C219','山茱萸','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C219','山茱萸','生长期打药','SPRAY',70,'生长期打药'),
('C219','山茱萸','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C219','山茱萸','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C219','山茱萸','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C219','山茱萸','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C220','连翘','苗期除草','SPRAY',15,'苗期除草'),
('C220','连翘','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C220','连翘','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C220','连翘','生长期打药','SPRAY',70,'生长期打药'),
('C220','连翘','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C220','连翘','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C220','连翘','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C220','连翘','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C221','丹参','苗期除草','SPRAY',15,'苗期除草'),
('C221','丹参','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C221','丹参','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C221','丹参','生长期打药','SPRAY',70,'生长期打药'),
('C221','丹参','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C221','丹参','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C221','丹参','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C221','丹参','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C222','玄参','苗期除草','SPRAY',15,'苗期除草'),
('C222','玄参','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C222','玄参','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C222','玄参','生长期打药','SPRAY',70,'生长期打药'),
('C222','玄参','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C222','玄参','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C222','玄参','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C222','玄参','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C223','生地','苗期除草','SPRAY',15,'苗期除草'),
('C223','生地','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C223','生地','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C223','生地','生长期打药','SPRAY',70,'生长期打药'),
('C223','生地','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C223','生地','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C223','生地','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C223','生地','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C224','白芷','苗期除草','SPRAY',15,'苗期除草'),
('C224','白芷','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C224','白芷','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C224','白芷','生长期打药','SPRAY',70,'生长期打药'),
('C224','白芷','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C224','白芷','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C224','白芷','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C224','白芷','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C225','藿香','苗期除草','SPRAY',15,'苗期除草'),
('C225','藿香','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C225','藿香','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C225','藿香','生长期打药','SPRAY',70,'生长期打药'),
('C225','藿香','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C225','藿香','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C225','藿香','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C225','藿香','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C226','荆芥','苗期除草','SPRAY',15,'苗期除草'),
('C226','荆芥','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C226','荆芥','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C226','荆芥','生长期打药','SPRAY',70,'生长期打药'),
('C226','荆芥','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C226','荆芥','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C226','荆芥','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C226','荆芥','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C227','防风','苗期除草','SPRAY',15,'苗期除草'),
('C227','防风','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C227','防风','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C227','防风','生长期打药','SPRAY',70,'生长期打药'),
('C227','防风','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C227','防风','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C227','防风','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C227','防风','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C228','羌活','苗期除草','SPRAY',15,'苗期除草'),
('C228','羌活','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C228','羌活','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C228','羌活','生长期打药','SPRAY',70,'生长期打药'),
('C228','羌活','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C228','羌活','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C228','羌活','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C228','羌活','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C229','独活','苗期除草','SPRAY',15,'苗期除草'),
('C229','独活','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C229','独活','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C229','独活','生长期打药','SPRAY',70,'生长期打药'),
('C229','独活','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C229','独活','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C229','独活','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C229','独活','成熟期采收','HARVEST',180,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C230','前胡','苗期除草','SPRAY',15,'苗期除草'),
('C230','前胡','幼苗期追肥','FERTILIZE',30,'幼苗期追肥'),
('C230','前胡','生长期灌溉','IRRIGATE',50,'生长期灌溉'),
('C230','前胡','生长期打药','SPRAY',70,'生长期打药'),
('C230','前胡','旺盛期追肥','FERTILIZE',90,'旺盛期追肥'),
('C230','前胡','根膨大期灌溉','IRRIGATE',120,'根膨大期灌溉'),
('C230','前胡','成熟前控水','IRRIGATE',150,'成熟前控水'),
('C230','前胡','成熟期采收','HARVEST',180,'成熟期采收');

-- 作物231-260: 花卉观赏
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C231','玫瑰','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C231','玫瑰','生长期打药','FERTILIZE',35,'生长期打药'),
('C231','玫瑰','花前追肥','IRRIGATE',50,'花前追肥'),
('C231','玫瑰','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C231','玫瑰','花期打药','FERTILIZE',80,'花期打药'),
('C231','玫瑰','花后追肥','IRRIGATE',95,'花后追肥'),
('C231','玫瑰','采收前控水','IRRIGATE',110,'采收前控水'),
('C231','玫瑰','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C232','月季','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C232','月季','生长期打药','FERTILIZE',35,'生长期打药'),
('C232','月季','花前追肥','IRRIGATE',50,'花前追肥'),
('C232','月季','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C232','月季','花期打药','FERTILIZE',80,'花期打药'),
('C232','月季','花后追肥','IRRIGATE',95,'花后追肥'),
('C232','月季','采收前控水','IRRIGATE',110,'采收前控水'),
('C232','月季','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C233','牡丹','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C233','牡丹','生长期打药','FERTILIZE',35,'生长期打药'),
('C233','牡丹','花前追肥','IRRIGATE',50,'花前追肥'),
('C233','牡丹','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C233','牡丹','花期打药','FERTILIZE',80,'花期打药'),
('C233','牡丹','花后追肥','IRRIGATE',95,'花后追肥'),
('C233','牡丹','采收前控水','IRRIGATE',110,'采收前控水'),
('C233','牡丹','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C234','芍药花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C234','芍药花','生长期打药','FERTILIZE',35,'生长期打药'),
('C234','芍药花','花前追肥','IRRIGATE',50,'花前追肥'),
('C234','芍药花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C234','芍药花','花期打药','FERTILIZE',80,'花期打药'),
('C234','芍药花','花后追肥','IRRIGATE',95,'花后追肥'),
('C234','芍药花','采收前控水','IRRIGATE',110,'采收前控水'),
('C234','芍药花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C235','菊花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C235','菊花','生长期打药','FERTILIZE',35,'生长期打药'),
('C235','菊花','花前追肥','IRRIGATE',50,'花前追肥'),
('C235','菊花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C235','菊花','花期打药','FERTILIZE',80,'花期打药'),
('C235','菊花','花后追肥','IRRIGATE',95,'花后追肥'),
('C235','菊花','采收前控水','IRRIGATE',110,'采收前控水'),
('C235','菊花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C236','康乃馨','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C236','康乃馨','生长期打药','FERTILIZE',35,'生长期打药'),
('C236','康乃馨','花前追肥','IRRIGATE',50,'花前追肥'),
('C236','康乃馨','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C236','康乃馨','花期打药','FERTILIZE',80,'花期打药'),
('C236','康乃馨','花后追肥','IRRIGATE',95,'花后追肥'),
('C236','康乃馨','采收前控水','IRRIGATE',110,'采收前控水'),
('C236','康乃馨','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C237','百合花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C237','百合花','生长期打药','FERTILIZE',35,'生长期打药'),
('C237','百合花','花前追肥','IRRIGATE',50,'花前追肥'),
('C237','百合花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C237','百合花','花期打药','FERTILIZE',80,'花期打药'),
('C237','百合花','花后追肥','IRRIGATE',95,'花后追肥'),
('C237','百合花','采收前控水','IRRIGATE',110,'采收前控水'),
('C237','百合花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C238','郁金香','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C238','郁金香','生长期打药','FERTILIZE',35,'生长期打药'),
('C238','郁金香','花前追肥','IRRIGATE',50,'花前追肥'),
('C238','郁金香','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C238','郁金香','花期打药','FERTILIZE',80,'花期打药'),
('C238','郁金香','花后追肥','IRRIGATE',95,'花后追肥'),
('C238','郁金香','采收前控水','IRRIGATE',110,'采收前控水'),
('C238','郁金香','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C239','茉莉花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C239','茉莉花','生长期打药','FERTILIZE',35,'生长期打药'),
('C239','茉莉花','花前追肥','IRRIGATE',50,'花前追肥'),
('C239','茉莉花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C239','茉莉花','花期打药','FERTILIZE',80,'花期打药'),
('C239','茉莉花','花后追肥','IRRIGATE',95,'花后追肥'),
('C239','茉莉花','采收前控水','IRRIGATE',110,'采收前控水'),
('C239','茉莉花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C240','桂花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C240','桂花','生长期打药','FERTILIZE',35,'生长期打药'),
('C240','桂花','花前追肥','IRRIGATE',50,'花前追肥'),
('C240','桂花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C240','桂花','花期打药','FERTILIZE',80,'花期打药'),
('C240','桂花','花后追肥','IRRIGATE',95,'花后追肥'),
('C240','桂花','采收前控水','IRRIGATE',110,'采收前控水'),
('C240','桂花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C241','栀子花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C241','栀子花','生长期打药','FERTILIZE',35,'生长期打药'),
('C241','栀子花','花前追肥','IRRIGATE',50,'花前追肥'),
('C241','栀子花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C241','栀子花','花期打药','FERTILIZE',80,'花期打药'),
('C241','栀子花','花后追肥','IRRIGATE',95,'花后追肥'),
('C241','栀子花','采收前控水','IRRIGATE',110,'采收前控水'),
('C241','栀子花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C242','杜鹃花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C242','杜鹃花','生长期打药','FERTILIZE',35,'生长期打药'),
('C242','杜鹃花','花前追肥','IRRIGATE',50,'花前追肥'),
('C242','杜鹃花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C242','杜鹃花','花期打药','FERTILIZE',80,'花期打药'),
('C242','杜鹃花','花后追肥','IRRIGATE',95,'花后追肥'),
('C242','杜鹃花','采收前控水','IRRIGATE',110,'采收前控水'),
('C242','杜鹃花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C243','茶花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C243','茶花','生长期打药','FERTILIZE',35,'生长期打药'),
('C243','茶花','花前追肥','IRRIGATE',50,'花前追肥'),
('C243','茶花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C243','茶花','花期打药','FERTILIZE',80,'花期打药'),
('C243','茶花','花后追肥','IRRIGATE',95,'花后追肥'),
('C243','茶花','采收前控水','IRRIGATE',110,'采收前控水'),
('C243','茶花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C244','梅花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C244','梅花','生长期打药','FERTILIZE',35,'生长期打药'),
('C244','梅花','花前追肥','IRRIGATE',50,'花前追肥'),
('C244','梅花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C244','梅花','花期打药','FERTILIZE',80,'花期打药'),
('C244','梅花','花后追肥','IRRIGATE',95,'花后追肥'),
('C244','梅花','采收前控水','IRRIGATE',110,'采收前控水'),
('C244','梅花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C245','兰花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C245','兰花','生长期打药','FERTILIZE',35,'生长期打药'),
('C245','兰花','花前追肥','IRRIGATE',50,'花前追肥'),
('C245','兰花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C245','兰花','花期打药','FERTILIZE',80,'花期打药'),
('C245','兰花','花后追肥','IRRIGATE',95,'花后追肥'),
('C245','兰花','采收前控水','IRRIGATE',110,'采收前控水'),
('C245','兰花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C246','水仙花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C246','水仙花','生长期打药','FERTILIZE',35,'生长期打药'),
('C246','水仙花','花前追肥','IRRIGATE',50,'花前追肥'),
('C246','水仙花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C246','水仙花','花期打药','FERTILIZE',80,'花期打药'),
('C246','水仙花','花后追肥','IRRIGATE',95,'花后追肥'),
('C246','水仙花','采收前控水','IRRIGATE',110,'采收前控水'),
('C246','水仙花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C247','荷花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C247','荷花','生长期打药','FERTILIZE',35,'生长期打药'),
('C247','荷花','花前追肥','IRRIGATE',50,'花前追肥'),
('C247','荷花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C247','荷花','花期打药','FERTILIZE',80,'花期打药'),
('C247','荷花','花后追肥','IRRIGATE',95,'花后追肥'),
('C247','荷花','采收前控水','IRRIGATE',110,'采收前控水'),
('C247','荷花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C248','睡莲','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C248','睡莲','生长期打药','FERTILIZE',35,'生长期打药'),
('C248','睡莲','花前追肥','IRRIGATE',50,'花前追肥'),
('C248','睡莲','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C248','睡莲','花期打药','FERTILIZE',80,'花期打药'),
('C248','睡莲','花后追肥','IRRIGATE',95,'花后追肥'),
('C248','睡莲','采收前控水','IRRIGATE',110,'采收前控水'),
('C248','睡莲','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C249','鸢尾','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C249','鸢尾','生长期打药','FERTILIZE',35,'生长期打药'),
('C249','鸢尾','花前追肥','IRRIGATE',50,'花前追肥'),
('C249','鸢尾','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C249','鸢尾','花期打药','FERTILIZE',80,'花期打药'),
('C249','鸢尾','花后追肥','IRRIGATE',95,'花后追肥'),
('C249','鸢尾','采收前控水','IRRIGATE',110,'采收前控水'),
('C249','鸢尾','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C250','大丽花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C250','大丽花','生长期打药','FERTILIZE',35,'生长期打药'),
('C250','大丽花','花前追肥','IRRIGATE',50,'花前追肥'),
('C250','大丽花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C250','大丽花','花期打药','FERTILIZE',80,'花期打药'),
('C250','大丽花','花后追肥','IRRIGATE',95,'花后追肥'),
('C250','大丽花','采收前控水','IRRIGATE',110,'采收前控水'),
('C250','大丽花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C251','一串红','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C251','一串红','生长期打药','FERTILIZE',35,'生长期打药'),
('C251','一串红','花前追肥','IRRIGATE',50,'花前追肥'),
('C251','一串红','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C251','一串红','花期打药','FERTILIZE',80,'花期打药'),
('C251','一串红','花后追肥','IRRIGATE',95,'花后追肥'),
('C251','一串红','采收前控水','IRRIGATE',110,'采收前控水'),
('C251','一串红','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C252','万寿菊','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C252','万寿菊','生长期打药','FERTILIZE',35,'生长期打药'),
('C252','万寿菊','花前追肥','IRRIGATE',50,'花前追肥'),
('C252','万寿菊','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C252','万寿菊','花期打药','FERTILIZE',80,'花期打药'),
('C252','万寿菊','花后追肥','IRRIGATE',95,'花后追肥'),
('C252','万寿菊','采收前控水','IRRIGATE',110,'采收前控水'),
('C252','万寿菊','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C253','孔雀草','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C253','孔雀草','生长期打药','FERTILIZE',35,'生长期打药'),
('C253','孔雀草','花前追肥','IRRIGATE',50,'花前追肥'),
('C253','孔雀草','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C253','孔雀草','花期打药','FERTILIZE',80,'花期打药'),
('C253','孔雀草','花后追肥','IRRIGATE',95,'花后追肥'),
('C253','孔雀草','采收前控水','IRRIGATE',110,'采收前控水'),
('C253','孔雀草','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C254','三色堇','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C254','三色堇','生长期打药','FERTILIZE',35,'生长期打药'),
('C254','三色堇','花前追肥','IRRIGATE',50,'花前追肥'),
('C254','三色堇','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C254','三色堇','花期打药','FERTILIZE',80,'花期打药'),
('C254','三色堇','花后追肥','IRRIGATE',95,'花后追肥'),
('C254','三色堇','采收前控水','IRRIGATE',110,'采收前控水'),
('C254','三色堇','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C255','矮牵牛','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C255','矮牵牛','生长期打药','FERTILIZE',35,'生长期打药'),
('C255','矮牵牛','花前追肥','IRRIGATE',50,'花前追肥'),
('C255','矮牵牛','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C255','矮牵牛','花期打药','FERTILIZE',80,'花期打药'),
('C255','矮牵牛','花后追肥','IRRIGATE',95,'花后追肥'),
('C255','矮牵牛','采收前控水','IRRIGATE',110,'采收前控水'),
('C255','矮牵牛','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C256','金鱼草','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C256','金鱼草','生长期打药','FERTILIZE',35,'生长期打药'),
('C256','金鱼草','花前追肥','IRRIGATE',50,'花前追肥'),
('C256','金鱼草','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C256','金鱼草','花期打药','FERTILIZE',80,'花期打药'),
('C256','金鱼草','花后追肥','IRRIGATE',95,'花后追肥'),
('C256','金鱼草','采收前控水','IRRIGATE',110,'采收前控水'),
('C256','金鱼草','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C257','石竹','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C257','石竹','生长期打药','FERTILIZE',35,'生长期打药'),
('C257','石竹','花前追肥','IRRIGATE',50,'花前追肥'),
('C257','石竹','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C257','石竹','花期打药','FERTILIZE',80,'花期打药'),
('C257','石竹','花后追肥','IRRIGATE',95,'花后追肥'),
('C257','石竹','采收前控水','IRRIGATE',110,'采收前控水'),
('C257','石竹','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C258','满天星','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C258','满天星','生长期打药','FERTILIZE',35,'生长期打药'),
('C258','满天星','花前追肥','IRRIGATE',50,'花前追肥'),
('C258','满天星','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C258','满天星','花期打药','FERTILIZE',80,'花期打药'),
('C258','满天星','花后追肥','IRRIGATE',95,'花后追肥'),
('C258','满天星','采收前控水','IRRIGATE',110,'采收前控水'),
('C258','满天星','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C259','葵花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C259','葵花','生长期打药','FERTILIZE',35,'生长期打药'),
('C259','葵花','花前追肥','IRRIGATE',50,'花前追肥'),
('C259','葵花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C259','葵花','花期打药','FERTILIZE',80,'花期打药'),
('C259','葵花','花后追肥','IRRIGATE',95,'花后追肥'),
('C259','葵花','采收前控水','IRRIGATE',110,'采收前控水'),
('C259','葵花','成熟期采收','HARVEST',125,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C260','油菜花','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C260','油菜花','生长期打药','FERTILIZE',35,'生长期打药'),
('C260','油菜花','花前追肥','IRRIGATE',50,'花前追肥'),
('C260','油菜花','开花期灌溉','SPRAY',65,'开花期灌溉'),
('C260','油菜花','花期打药','FERTILIZE',80,'花期打药'),
('C260','油菜花','花后追肥','IRRIGATE',95,'花后追肥'),
('C260','油菜花','采收前控水','IRRIGATE',110,'采收前控水'),
('C260','油菜花','成熟期采收','HARVEST',125,'成熟期采收');

-- 作物261-300: 芽苗菜/特色蔬菜
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C261','紫菜苔','苗期除草','SPRAY',7,'苗期除草'),
('C261','紫菜苔','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C261','紫菜苔','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C261','紫菜苔','生长期打药','SPRAY',30,'生长期打药'),
('C261','紫菜苔','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C261','紫菜苔','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C261','紫菜苔','采收前控水','IRRIGATE',60,'采收前控水'),
('C261','紫菜苔','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C262','红菜苔','苗期除草','SPRAY',7,'苗期除草'),
('C262','红菜苔','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C262','红菜苔','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C262','红菜苔','生长期打药','SPRAY',30,'生长期打药'),
('C262','红菜苔','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C262','红菜苔','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C262','红菜苔','采收前控水','IRRIGATE',60,'采收前控水'),
('C262','红菜苔','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C263','洪山菜苔','苗期除草','SPRAY',7,'苗期除草'),
('C263','洪山菜苔','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C263','洪山菜苔','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C263','洪山菜苔','生长期打药','SPRAY',30,'生长期打药'),
('C263','洪山菜苔','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C263','洪山菜苔','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C263','洪山菜苔','采收前控水','IRRIGATE',60,'采收前控水'),
('C263','洪山菜苔','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C264','有机花菜','苗期除草','SPRAY',7,'苗期除草'),
('C264','有机花菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C264','有机花菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C264','有机花菜','生长期打药','SPRAY',30,'生长期打药'),
('C264','有机花菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C264','有机花菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C264','有机花菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C264','有机花菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C265','松花菜','苗期除草','SPRAY',7,'苗期除草'),
('C265','松花菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C265','松花菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C265','松花菜','生长期打药','SPRAY',30,'生长期打药'),
('C265','松花菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C265','松花菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C265','松花菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C265','松花菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C266','白菜花','苗期除草','SPRAY',7,'苗期除草'),
('C266','白菜花','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C266','白菜花','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C266','白菜花','生长期打药','SPRAY',30,'生长期打药'),
('C266','白菜花','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C266','白菜花','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C266','白菜花','采收前控水','IRRIGATE',60,'采收前控水'),
('C266','白菜花','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C267','黄花菜','苗期除草','SPRAY',7,'苗期除草'),
('C267','黄花菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C267','黄花菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C267','黄花菜','生长期打药','SPRAY',30,'生长期打药'),
('C267','黄花菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C267','黄花菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C267','黄花菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C267','黄花菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C268','百合球','苗期除草','SPRAY',7,'苗期除草'),
('C268','百合球','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C268','百合球','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C268','百合球','生长期打药','SPRAY',30,'生长期打药'),
('C268','百合球','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C268','百合球','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C268','百合球','采收前控水','IRRIGATE',60,'采收前控水'),
('C268','百合球','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C269','芦笋','苗期除草','SPRAY',7,'苗期除草'),
('C269','芦笋','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C269','芦笋','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C269','芦笋','生长期打药','SPRAY',30,'生长期打药'),
('C269','芦笋','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C269','芦笋','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C269','芦笋','采收前控水','IRRIGATE',60,'采收前控水'),
('C269','芦笋','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C270','竹笋','苗期除草','SPRAY',7,'苗期除草'),
('C270','竹笋','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C270','竹笋','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C270','竹笋','生长期打药','SPRAY',30,'生长期打药'),
('C270','竹笋','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C270','竹笋','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C270','竹笋','采收前控水','IRRIGATE',60,'采收前控水'),
('C270','竹笋','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C271','荠菜','苗期除草','SPRAY',7,'苗期除草'),
('C271','荠菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C271','荠菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C271','荠菜','生长期打药','SPRAY',30,'生长期打药'),
('C271','荠菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C271','荠菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C271','荠菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C271','荠菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C272','马兰头','苗期除草','SPRAY',7,'苗期除草'),
('C272','马兰头','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C272','马兰头','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C272','马兰头','生长期打药','SPRAY',30,'生长期打药'),
('C272','马兰头','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C272','马兰头','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C272','马兰头','采收前控水','IRRIGATE',60,'采收前控水'),
('C272','马兰头','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C273','艾草','苗期除草','SPRAY',7,'苗期除草'),
('C273','艾草','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C273','艾草','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C273','艾草','生长期打药','SPRAY',30,'生长期打药'),
('C273','艾草','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C273','艾草','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C273','艾草','采收前控水','IRRIGATE',60,'采收前控水'),
('C273','艾草','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C274','灰灰菜','苗期除草','SPRAY',7,'苗期除草'),
('C274','灰灰菜','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C274','灰灰菜','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C274','灰灰菜','生长期打药','SPRAY',30,'生长期打药'),
('C274','灰灰菜','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C274','灰灰菜','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C274','灰灰菜','采收前控水','IRRIGATE',60,'采收前控水'),
('C274','灰灰菜','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C275','苜蓿','苗期除草','SPRAY',7,'苗期除草'),
('C275','苜蓿','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C275','苜蓿','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C275','苜蓿','生长期打药','SPRAY',30,'生长期打药'),
('C275','苜蓿','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C275','苜蓿','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C275','苜蓿','采收前控水','IRRIGATE',60,'采收前控水'),
('C275','苜蓿','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C276','豌豆尖','苗期除草','SPRAY',7,'苗期除草'),
('C276','豌豆尖','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C276','豌豆尖','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C276','豌豆尖','生长期打药','SPRAY',30,'生长期打药'),
('C276','豌豆尖','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C276','豌豆尖','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C276','豌豆尖','采收前控水','IRRIGATE',60,'采收前控水'),
('C276','豌豆尖','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C277','红薯叶','苗期除草','SPRAY',7,'苗期除草'),
('C277','红薯叶','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C277','红薯叶','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C277','红薯叶','生长期打药','SPRAY',30,'生长期打药'),
('C277','红薯叶','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C277','红薯叶','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C277','红薯叶','采收前控水','IRRIGATE',60,'采收前控水'),
('C277','红薯叶','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C278','南瓜藤','苗期除草','SPRAY',7,'苗期除草'),
('C278','南瓜藤','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C278','南瓜藤','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C278','南瓜藤','生长期打药','SPRAY',30,'生长期打药'),
('C278','南瓜藤','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C278','南瓜藤','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C278','南瓜藤','采收前控水','IRRIGATE',60,'采收前控水'),
('C278','南瓜藤','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C279','丝瓜尖','苗期除草','SPRAY',7,'苗期除草'),
('C279','丝瓜尖','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C279','丝瓜尖','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C279','丝瓜尖','生长期打药','SPRAY',30,'生长期打药'),
('C279','丝瓜尖','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C279','丝瓜尖','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C279','丝瓜尖','采收前控水','IRRIGATE',60,'采收前控水'),
('C279','丝瓜尖','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C280','辣椒叶','苗期除草','SPRAY',7,'苗期除草'),
('C280','辣椒叶','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C280','辣椒叶','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C280','辣椒叶','生长期打药','SPRAY',30,'生长期打药'),
('C280','辣椒叶','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C280','辣椒叶','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C280','辣椒叶','采收前控水','IRRIGATE',60,'采收前控水'),
('C280','辣椒叶','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C281','芝麻叶','苗期除草','SPRAY',7,'苗期除草'),
('C281','芝麻叶','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C281','芝麻叶','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C281','芝麻叶','生长期打药','SPRAY',30,'生长期打药'),
('C281','芝麻叶','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C281','芝麻叶','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C281','芝麻叶','采收前控水','IRRIGATE',60,'采收前控水'),
('C281','芝麻叶','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C282','花椒芽','苗期除草','SPRAY',7,'苗期除草'),
('C282','花椒芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C282','花椒芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C282','花椒芽','生长期打药','SPRAY',30,'生长期打药'),
('C282','花椒芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C282','花椒芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C282','花椒芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C282','花椒芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C283','香椿苗','苗期除草','SPRAY',7,'苗期除草'),
('C283','香椿苗','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C283','香椿苗','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C283','香椿苗','生长期打药','SPRAY',30,'生长期打药'),
('C283','香椿苗','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C283','香椿苗','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C283','香椿苗','采收前控水','IRRIGATE',60,'采收前控水'),
('C283','香椿苗','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C284','萝卜苗','苗期除草','SPRAY',7,'苗期除草'),
('C284','萝卜苗','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C284','萝卜苗','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C284','萝卜苗','生长期打药','SPRAY',30,'生长期打药'),
('C284','萝卜苗','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C284','萝卜苗','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C284','萝卜苗','采收前控水','IRRIGATE',60,'采收前控水'),
('C284','萝卜苗','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C285','黄豆芽','苗期除草','SPRAY',7,'苗期除草'),
('C285','黄豆芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C285','黄豆芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C285','黄豆芽','生长期打药','SPRAY',30,'生长期打药'),
('C285','黄豆芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C285','黄豆芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C285','黄豆芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C285','黄豆芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C286','绿豆芽','苗期除草','SPRAY',7,'苗期除草'),
('C286','绿豆芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C286','绿豆芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C286','绿豆芽','生长期打药','SPRAY',30,'生长期打药'),
('C286','绿豆芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C286','绿豆芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C286','绿豆芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C286','绿豆芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C287','黑豆芽','苗期除草','SPRAY',7,'苗期除草'),
('C287','黑豆芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C287','黑豆芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C287','黑豆芽','生长期打药','SPRAY',30,'生长期打药'),
('C287','黑豆芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C287','黑豆芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C287','黑豆芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C287','黑豆芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C288','萝卜芽','苗期除草','SPRAY',7,'苗期除草'),
('C288','萝卜芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C288','萝卜芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C288','萝卜芽','生长期打药','SPRAY',30,'生长期打药'),
('C288','萝卜芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C288','萝卜芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C288','萝卜芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C288','萝卜芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C289','荞麦芽','苗期除草','SPRAY',7,'苗期除草'),
('C289','荞麦芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C289','荞麦芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C289','荞麦芽','生长期打药','SPRAY',30,'生长期打药'),
('C289','荞麦芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C289','荞麦芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C289','荞麦芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C289','荞麦芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C290','苜蓿芽','苗期除草','SPRAY',7,'苗期除草'),
('C290','苜蓿芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C290','苜蓿芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C290','苜蓿芽','生长期打药','SPRAY',30,'生长期打药'),
('C290','苜蓿芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C290','苜蓿芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C290','苜蓿芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C290','苜蓿芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C291','豌豆苗','苗期除草','SPRAY',7,'苗期除草'),
('C291','豌豆苗','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C291','豌豆苗','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C291','豌豆苗','生长期打药','SPRAY',30,'生长期打药'),
('C291','豌豆苗','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C291','豌豆苗','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C291','豌豆苗','采收前控水','IRRIGATE',60,'采收前控水'),
('C291','豌豆苗','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C292','香椿芽','苗期除草','SPRAY',7,'苗期除草'),
('C292','香椿芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C292','香椿芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C292','香椿芽','生长期打药','SPRAY',30,'生长期打药'),
('C292','香椿芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C292','香椿芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C292','香椿芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C292','香椿芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C293','蒜黄','苗期除草','SPRAY',7,'苗期除草'),
('C293','蒜黄','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C293','蒜黄','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C293','蒜黄','生长期打药','SPRAY',30,'生长期打药'),
('C293','蒜黄','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C293','蒜黄','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C293','蒜黄','采收前控水','IRRIGATE',60,'采收前控水'),
('C293','蒜黄','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C294','韭黄苗','苗期除草','SPRAY',7,'苗期除草'),
('C294','韭黄苗','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C294','韭黄苗','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C294','韭黄苗','生长期打药','SPRAY',30,'生长期打药'),
('C294','韭黄苗','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C294','韭黄苗','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C294','韭黄苗','采收前控水','IRRIGATE',60,'采收前控水'),
('C294','韭黄苗','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C295','姜芽','苗期除草','SPRAY',7,'苗期除草'),
('C295','姜芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C295','姜芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C295','姜芽','生长期打药','SPRAY',30,'生长期打药'),
('C295','姜芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C295','姜芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C295','姜芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C295','姜芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C296','葱芽','苗期除草','SPRAY',7,'苗期除草'),
('C296','葱芽','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C296','葱芽','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C296','葱芽','生长期打药','SPRAY',30,'生长期打药'),
('C296','葱芽','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C296','葱芽','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C296','葱芽','采收前控水','IRRIGATE',60,'采收前控水'),
('C296','葱芽','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C297','彩椒','定植后灌溉','SPRAY',15,'定植后灌溉'),
('C297','彩椒','开花期追肥','FERTILIZE',30,'开花期追肥'),
('C297','彩椒','结果期灌溉','IRRIGATE',45,'结果期灌溉'),
('C297','彩椒','结果期打药','SPRAY',55,'结果期打药'),
('C297','彩椒','盛果期追肥','FERTILIZE',65,'盛果期追肥'),
('C297','彩椒','盛果期灌溉','IRRIGATE',75,'盛果期灌溉'),
('C297','彩椒','采收前控水','IRRIGATE',85,'采收前控水'),
('C297','彩椒','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C298','尖椒','定植后灌溉','SPRAY',15,'定植后灌溉'),
('C298','尖椒','开花期追肥','FERTILIZE',30,'开花期追肥'),
('C298','尖椒','结果期灌溉','IRRIGATE',45,'结果期灌溉'),
('C298','尖椒','结果期打药','SPRAY',55,'结果期打药'),
('C298','尖椒','盛果期追肥','FERTILIZE',65,'盛果期追肥'),
('C298','尖椒','盛果期灌溉','IRRIGATE',75,'盛果期灌溉'),
('C298','尖椒','采收前控水','IRRIGATE',85,'采收前控水'),
('C298','尖椒','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C299','朝天椒','定植后灌溉','SPRAY',15,'定植后灌溉'),
('C299','朝天椒','开花期追肥','FERTILIZE',30,'开花期追肥'),
('C299','朝天椒','结果期灌溉','IRRIGATE',45,'结果期灌溉'),
('C299','朝天椒','结果期打药','SPRAY',55,'结果期打药'),
('C299','朝天椒','盛果期追肥','FERTILIZE',65,'盛果期追肥'),
('C299','朝天椒','盛果期灌溉','IRRIGATE',75,'盛果期灌溉'),
('C299','朝天椒','采收前控水','IRRIGATE',85,'采收前控水'),
('C299','朝天椒','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C300','小米椒','定植后灌溉','SPRAY',15,'定植后灌溉'),
('C300','小米椒','开花期追肥','FERTILIZE',30,'开花期追肥'),
('C300','小米椒','结果期灌溉','IRRIGATE',45,'结果期灌溉'),
('C300','小米椒','结果期打药','SPRAY',55,'结果期打药'),
('C300','小米椒','盛果期追肥','FERTILIZE',65,'盛果期追肥'),
('C300','小米椒','盛果期灌溉','IRRIGATE',75,'盛果期灌溉'),
('C300','小米椒','采收前控水','IRRIGATE',85,'采收前控水'),
('C300','小米椒','成熟期采收','HARVEST',95,'成熟期采收');

-- 作物301-340: 杂粮豆类扩展
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C301','藜麦','苗期除草','SPRAY',15,'苗期除草'),
('C301','藜麦','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C301','藜麦','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C301','藜麦','拔节期打药','SPRAY',55,'拔节期打药'),
('C301','藜麦','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C301','藜麦','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C301','藜麦','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C301','藜麦','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C302','籽粒苋','苗期除草','SPRAY',15,'苗期除草'),
('C302','籽粒苋','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C302','籽粒苋','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C302','籽粒苋','拔节期打药','SPRAY',55,'拔节期打药'),
('C302','籽粒苋','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C302','籽粒苋','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C302','籽粒苋','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C302','籽粒苋','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C303','莜麦','苗期除草','SPRAY',15,'苗期除草'),
('C303','莜麦','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C303','莜麦','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C303','莜麦','拔节期打药','SPRAY',55,'拔节期打药'),
('C303','莜麦','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C303','莜麦','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C303','莜麦','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C303','莜麦','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C304','黑麦','苗期除草','SPRAY',15,'苗期除草'),
('C304','黑麦','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C304','黑麦','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C304','黑麦','拔节期打药','SPRAY',55,'拔节期打药'),
('C304','黑麦','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C304','黑麦','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C304','黑麦','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C304','黑麦','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C305','红米','苗期除草','SPRAY',15,'苗期除草'),
('C305','红米','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C305','红米','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C305','红米','拔节期打药','SPRAY',55,'拔节期打药'),
('C305','红米','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C305','红米','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C305','红米','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C305','红米','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C306','黑米','苗期除草','SPRAY',15,'苗期除草'),
('C306','黑米','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C306','黑米','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C306','黑米','拔节期打药','SPRAY',55,'拔节期打药'),
('C306','黑米','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C306','黑米','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C306','黑米','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C306','黑米','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C307','紫米','苗期除草','SPRAY',15,'苗期除草'),
('C307','紫米','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C307','紫米','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C307','紫米','拔节期打药','SPRAY',55,'拔节期打药'),
('C307','紫米','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C307','紫米','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C307','紫米','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C307','紫米','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C308','糯米稻','苗期除草','SPRAY',15,'苗期除草'),
('C308','糯米稻','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C308','糯米稻','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C308','糯米稻','拔节期打药','SPRAY',55,'拔节期打药'),
('C308','糯米稻','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C308','糯米稻','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C308','糯米稻','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C308','糯米稻','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C309','粳稻','苗期除草','SPRAY',15,'苗期除草'),
('C309','粳稻','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C309','粳稻','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C309','粳稻','拔节期打药','SPRAY',55,'拔节期打药'),
('C309','粳稻','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C309','粳稻','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C309','粳稻','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C309','粳稻','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C310','籼稻','苗期除草','SPRAY',15,'苗期除草'),
('C310','籼稻','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C310','籼稻','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C310','籼稻','拔节期打药','SPRAY',55,'拔节期打药'),
('C310','籼稻','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C310','籼稻','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C310','籼稻','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C310','籼稻','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C311','旱稻','苗期除草','SPRAY',15,'苗期除草'),
('C311','旱稻','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C311','旱稻','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C311','旱稻','拔节期打药','SPRAY',55,'拔节期打药'),
('C311','旱稻','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C311','旱稻','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C311','旱稻','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C311','旱稻','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C312','陆稻','苗期除草','SPRAY',15,'苗期除草'),
('C312','陆稻','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C312','陆稻','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C312','陆稻','拔节期打药','SPRAY',55,'拔节期打药'),
('C312','陆稻','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C312','陆稻','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C312','陆稻','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C312','陆稻','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C313','鹰嘴豆','苗期除草','SPRAY',15,'苗期除草'),
('C313','鹰嘴豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C313','鹰嘴豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C313','鹰嘴豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C313','鹰嘴豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C313','鹰嘴豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C313','鹰嘴豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C313','鹰嘴豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C314','小扁豆','苗期除草','SPRAY',15,'苗期除草'),
('C314','小扁豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C314','小扁豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C314','小扁豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C314','小扁豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C314','小扁豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C314','小扁豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C314','小扁豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C315','木豆','苗期除草','SPRAY',15,'苗期除草'),
('C315','木豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C315','木豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C315','木豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C315','木豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C315','木豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C315','木豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C315','木豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C316','饭豆','苗期除草','SPRAY',15,'苗期除草'),
('C316','饭豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C316','饭豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C316','饭豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C316','饭豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C316','饭豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C316','饭豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C316','饭豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C317','眉豆','苗期除草','SPRAY',15,'苗期除草'),
('C317','眉豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C317','眉豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C317','眉豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C317','眉豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C317','眉豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C317','眉豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C317','眉豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C318','龙爪豆','苗期除草','SPRAY',15,'苗期除草'),
('C318','龙爪豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C318','龙爪豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C318','龙爪豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C318','龙爪豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C318','龙爪豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C318','龙爪豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C318','龙爪豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C319','草豌豆','苗期除草','SPRAY',15,'苗期除草'),
('C319','草豌豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C319','草豌豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C319','草豌豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C319','草豌豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C319','草豌豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C319','草豌豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C319','草豌豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C320','刀豆','苗期除草','SPRAY',15,'苗期除草'),
('C320','刀豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C320','刀豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C320','刀豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C320','刀豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C320','刀豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C320','刀豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C320','刀豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C321','扁豆','苗期除草','SPRAY',15,'苗期除草'),
('C321','扁豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C321','扁豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C321','扁豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C321','扁豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C321','扁豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C321','扁豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C321','扁豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C322','菜豆','苗期除草','SPRAY',15,'苗期除草'),
('C322','菜豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C322','菜豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C322','菜豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C322','菜豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C322','菜豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C322','菜豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C322','菜豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C323','芸豆','苗期除草','SPRAY',15,'苗期除草'),
('C323','芸豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C323','芸豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C323','芸豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C323','芸豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C323','芸豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C323','芸豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C323','芸豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C324','红芸豆','苗期除草','SPRAY',15,'苗期除草'),
('C324','红芸豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C324','红芸豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C324','红芸豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C324','红芸豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C324','红芸豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C324','红芸豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C324','红芸豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C325','白芸豆','苗期除草','SPRAY',15,'苗期除草'),
('C325','白芸豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C325','白芸豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C325','白芸豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C325','白芸豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C325','白芸豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C325','白芸豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C325','白芸豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C326','黑芸豆','苗期除草','SPRAY',15,'苗期除草'),
('C326','黑芸豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C326','黑芸豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C326','黑芸豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C326','黑芸豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C326','黑芸豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C326','黑芸豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C326','黑芸豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C327','花芸豆','苗期除草','SPRAY',15,'苗期除草'),
('C327','花芸豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C327','花芸豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C327','花芸豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C327','花芸豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C327','花芸豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C327','花芸豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C327','花芸豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C328','绿豆种','苗期除草','SPRAY',15,'苗期除草'),
('C328','绿豆种','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C328','绿豆种','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C328','绿豆种','结荚期打药','SPRAY',55,'结荚期打药'),
('C328','绿豆种','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C328','绿豆种','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C328','绿豆种','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C328','绿豆种','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C329','赤小豆','苗期除草','SPRAY',15,'苗期除草'),
('C329','赤小豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C329','赤小豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C329','赤小豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C329','赤小豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C329','赤小豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C329','赤小豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C329','赤小豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C330','赤豆','苗期除草','SPRAY',15,'苗期除草'),
('C330','赤豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C330','赤豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C330','赤豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C330','赤豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C330','赤豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C330','赤豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C330','赤豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C331','四季豆','苗期除草','SPRAY',15,'苗期除草'),
('C331','四季豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C331','四季豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C331','四季豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C331','四季豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C331','四季豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C331','四季豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C331','四季豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C332','蔓生豆','苗期除草','SPRAY',15,'苗期除草'),
('C332','蔓生豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C332','蔓生豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C332','蔓生豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C332','蔓生豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C332','蔓生豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C332','蔓生豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C332','蔓生豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C333','矮生豆','苗期除草','SPRAY',15,'苗期除草'),
('C333','矮生豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C333','矮生豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C333','矮生豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C333','矮生豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C333','矮生豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C333','矮生豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C333','矮生豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C334','红花菜豆','苗期除草','SPRAY',15,'苗期除草'),
('C334','红花菜豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C334','红花菜豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C334','红花菜豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C334','红花菜豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C334','红花菜豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C334','红花菜豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C334','红花菜豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C335','龙芽豆','苗期除草','SPRAY',15,'苗期除草'),
('C335','龙芽豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C335','龙芽豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C335','龙芽豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C335','龙芽豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C335','龙芽豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C335','龙芽豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C335','龙芽豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C336','甜豌豆','苗期除草','SPRAY',15,'苗期除草'),
('C336','甜豌豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C336','甜豌豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C336','甜豌豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C336','甜豌豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C336','甜豌豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C336','甜豌豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C336','甜豌豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C337','食荚豌豆','苗期除草','SPRAY',15,'苗期除草'),
('C337','食荚豌豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C337','食荚豌豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C337','食荚豌豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C337','食荚豌豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C337','食荚豌豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C337','食荚豌豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C337','食荚豌豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C338','皱粒豌豆','苗期除草','SPRAY',15,'苗期除草'),
('C338','皱粒豌豆','分枝期追肥','FERTILIZE',30,'分枝期追肥'),
('C338','皱粒豌豆','开花期灌溉','IRRIGATE',45,'开花期灌溉'),
('C338','皱粒豌豆','结荚期打药','SPRAY',55,'结荚期打药'),
('C338','皱粒豌豆','鼓粒期追肥','FERTILIZE',65,'鼓粒期追肥'),
('C338','皱粒豌豆','鼓粒期灌溉','IRRIGATE',70,'鼓粒期灌溉'),
('C338','皱粒豌豆','成熟前排水','IRRIGATE',90,'成熟前排水'),
('C338','皱粒豌豆','成熟期采收','HARVEST',100,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C339','甜玉米','苗期除草','SPRAY',15,'苗期除草'),
('C339','甜玉米','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C339','甜玉米','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C339','甜玉米','拔节期打药','SPRAY',55,'拔节期打药'),
('C339','甜玉米','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C339','甜玉米','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C339','甜玉米','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C339','甜玉米','成熟期采收','HARVEST',95,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C340','糯玉米','苗期除草','SPRAY',15,'苗期除草'),
('C340','糯玉米','分蘖期追肥','FERTILIZE',30,'分蘖期追肥'),
('C340','糯玉米','拔节期灌溉','IRRIGATE',45,'拔节期灌溉'),
('C340','糯玉米','拔节期打药','SPRAY',55,'拔节期打药'),
('C340','糯玉米','抽穗期追肥','FERTILIZE',65,'抽穗期追肥'),
('C340','糯玉米','灌浆期灌溉','IRRIGATE',75,'灌浆期灌溉'),
('C340','糯玉米','成熟前控水','IRRIGATE',85,'成熟前控水'),
('C340','糯玉米','成熟期采收','HARVEST',95,'成熟期采收');

-- 作物341-380: 水果坚果扩展
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C341','黑莓','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C341','黑莓','花前打药','FERTILIZE',35,'花前打药'),
('C341','黑莓','花后追肥','IRRIGATE',50,'花后追肥'),
('C341','黑莓','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C341','黑莓','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C341','黑莓','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C341','黑莓','采收前控水','IRRIGATE',120,'采收前控水'),
('C341','黑莓','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C342','树莓','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C342','树莓','花前打药','FERTILIZE',35,'花前打药'),
('C342','树莓','花后追肥','IRRIGATE',50,'花后追肥'),
('C342','树莓','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C342','树莓','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C342','树莓','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C342','树莓','采收前控水','IRRIGATE',120,'采收前控水'),
('C342','树莓','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C343','蔓越莓','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C343','蔓越莓','花前打药','FERTILIZE',35,'花前打药'),
('C343','蔓越莓','花后追肥','IRRIGATE',50,'花后追肥'),
('C343','蔓越莓','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C343','蔓越莓','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C343','蔓越莓','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C343','蔓越莓','采收前控水','IRRIGATE',120,'采收前控水'),
('C343','蔓越莓','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C344','鹅莓','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C344','鹅莓','花前打药','FERTILIZE',35,'花前打药'),
('C344','鹅莓','花后追肥','IRRIGATE',50,'花后追肥'),
('C344','鹅莓','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C344','鹅莓','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C344','鹅莓','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C344','鹅莓','采收前控水','IRRIGATE',120,'采收前控水'),
('C344','鹅莓','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C345','金桔','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C345','金桔','花前打药','FERTILIZE',35,'花前打药'),
('C345','金桔','花后追肥','IRRIGATE',50,'花后追肥'),
('C345','金桔','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C345','金桔','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C345','金桔','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C345','金桔','采收前控水','IRRIGATE',120,'采收前控水'),
('C345','金桔','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C346','砂糖橘','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C346','砂糖橘','花前打药','FERTILIZE',35,'花前打药'),
('C346','砂糖橘','花后追肥','IRRIGATE',50,'花后追肥'),
('C346','砂糖橘','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C346','砂糖橘','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C346','砂糖橘','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C346','砂糖橘','采收前控水','IRRIGATE',120,'采收前控水'),
('C346','砂糖橘','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C347','蜜橘','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C347','蜜橘','花前打药','FERTILIZE',35,'花前打药'),
('C347','蜜橘','花后追肥','IRRIGATE',50,'花后追肥'),
('C347','蜜橘','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C347','蜜橘','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C347','蜜橘','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C347','蜜橘','采收前控水','IRRIGATE',120,'采收前控水'),
('C347','蜜橘','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C348','血橙','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C348','血橙','花前打药','FERTILIZE',35,'花前打药'),
('C348','血橙','花后追肥','IRRIGATE',50,'花后追肥'),
('C348','血橙','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C348','血橙','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C348','血橙','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C348','血橙','采收前控水','IRRIGATE',120,'采收前控水'),
('C348','血橙','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C349','脐橙','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C349','脐橙','花前打药','FERTILIZE',35,'花前打药'),
('C349','脐橙','花后追肥','IRRIGATE',50,'花后追肥'),
('C349','脐橙','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C349','脐橙','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C349','脐橙','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C349','脐橙','采收前控水','IRRIGATE',120,'采收前控水'),
('C349','脐橙','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C350','冰糖橙','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C350','冰糖橙','花前打药','FERTILIZE',35,'花前打药'),
('C350','冰糖橙','花后追肥','IRRIGATE',50,'花后追肥'),
('C350','冰糖橙','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C350','冰糖橙','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C350','冰糖橙','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C350','冰糖橙','采收前控水','IRRIGATE',120,'采收前控水'),
('C350','冰糖橙','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C351','丑橘','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C351','丑橘','花前打药','FERTILIZE',35,'花前打药'),
('C351','丑橘','花后追肥','IRRIGATE',50,'花后追肥'),
('C351','丑橘','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C351','丑橘','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C351','丑橘','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C351','丑橘','采收前控水','IRRIGATE',120,'采收前控水'),
('C351','丑橘','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C352','沃柑','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C352','沃柑','花前打药','FERTILIZE',35,'花前打药'),
('C352','沃柑','花后追肥','IRRIGATE',50,'花后追肥'),
('C352','沃柑','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C352','沃柑','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C352','沃柑','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C352','沃柑','采收前控水','IRRIGATE',120,'采收前控水'),
('C352','沃柑','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C353','皇帝柑','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C353','皇帝柑','花前打药','FERTILIZE',35,'花前打药'),
('C353','皇帝柑','花后追肥','IRRIGATE',50,'花后追肥'),
('C353','皇帝柑','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C353','皇帝柑','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C353','皇帝柑','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C353','皇帝柑','采收前控水','IRRIGATE',120,'采收前控水'),
('C353','皇帝柑','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C354','红心柚','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C354','红心柚','花前打药','FERTILIZE',35,'花前打药'),
('C354','红心柚','花后追肥','IRRIGATE',50,'花后追肥'),
('C354','红心柚','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C354','红心柚','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C354','红心柚','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C354','红心柚','采收前控水','IRRIGATE',120,'采收前控水'),
('C354','红心柚','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C355','葡萄柚','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C355','葡萄柚','花前打药','FERTILIZE',35,'花前打药'),
('C355','葡萄柚','花后追肥','IRRIGATE',50,'花后追肥'),
('C355','葡萄柚','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C355','葡萄柚','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C355','葡萄柚','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C355','葡萄柚','采收前控水','IRRIGATE',120,'采收前控水'),
('C355','葡萄柚','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C356','青柠','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C356','青柠','花前打药','FERTILIZE',35,'花前打药'),
('C356','青柠','花后追肥','IRRIGATE',50,'花后追肥'),
('C356','青柠','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C356','青柠','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C356','青柠','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C356','青柠','采收前控水','IRRIGATE',120,'采收前控水'),
('C356','青柠','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C357','丑柠檬','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C357','丑柠檬','花前打药','FERTILIZE',35,'花前打药'),
('C357','丑柠檬','花后追肥','IRRIGATE',50,'花后追肥'),
('C357','丑柠檬','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C357','丑柠檬','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C357','丑柠檬','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C357','丑柠檬','采收前控水','IRRIGATE',120,'采收前控水'),
('C357','丑柠檬','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C358','佛手柑','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C358','佛手柑','花前打药','FERTILIZE',35,'花前打药'),
('C358','佛手柑','花后追肥','IRRIGATE',50,'花后追肥'),
('C358','佛手柑','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C358','佛手柑','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C358','佛手柑','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C358','佛手柑','采收前控水','IRRIGATE',120,'采收前控水'),
('C358','佛手柑','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C359','金枣','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C359','金枣','花前打药','FERTILIZE',35,'花前打药'),
('C359','金枣','花后追肥','IRRIGATE',50,'花后追肥'),
('C359','金枣','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C359','金枣','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C359','金枣','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C359','金枣','采收前控水','IRRIGATE',120,'采收前控水'),
('C359','金枣','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C360','酸枣','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C360','酸枣','花前打药','FERTILIZE',35,'花前打药'),
('C360','酸枣','花后追肥','IRRIGATE',50,'花后追肥'),
('C360','酸枣','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C360','酸枣','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C360','酸枣','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C360','酸枣','采收前控水','IRRIGATE',120,'采收前控水'),
('C360','酸枣','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C361','蜜枣','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C361','蜜枣','花前打药','FERTILIZE',35,'花前打药'),
('C361','蜜枣','花后追肥','IRRIGATE',50,'花后追肥'),
('C361','蜜枣','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C361','蜜枣','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C361','蜜枣','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C361','蜜枣','采收前控水','IRRIGATE',120,'采收前控水'),
('C361','蜜枣','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C362','毛樱桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C362','毛樱桃','花前打药','FERTILIZE',35,'花前打药'),
('C362','毛樱桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C362','毛樱桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C362','毛樱桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C362','毛樱桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C362','毛樱桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C362','毛樱桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C363','酸樱桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C363','酸樱桃','花前打药','FERTILIZE',35,'花前打药'),
('C363','酸樱桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C363','酸樱桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C363','酸樱桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C363','酸樱桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C363','酸樱桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C363','酸樱桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C364','甜樱桃种','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C364','甜樱桃种','花前打药','FERTILIZE',35,'花前打药'),
('C364','甜樱桃种','花后追肥','IRRIGATE',50,'花后追肥'),
('C364','甜樱桃种','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C364','甜樱桃种','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C364','甜樱桃种','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C364','甜樱桃种','采收前控水','IRRIGATE',120,'采收前控水'),
('C364','甜樱桃种','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C365','大樱桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C365','大樱桃','花前打药','FERTILIZE',35,'花前打药'),
('C365','大樱桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C365','大樱桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C365','大樱桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C365','大樱桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C365','大樱桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C365','大樱桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C366','杨梅','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C366','杨梅','花前打药','FERTILIZE',35,'花前打药'),
('C366','杨梅','花后追肥','IRRIGATE',50,'花后追肥'),
('C366','杨梅','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C366','杨梅','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C366','杨梅','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C366','杨梅','采收前控水','IRRIGATE',120,'采收前控水'),
('C366','杨梅','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C367','青梅','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C367','青梅','花前打药','FERTILIZE',35,'花前打药'),
('C367','青梅','花后追肥','IRRIGATE',50,'花后追肥'),
('C367','青梅','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C367','青梅','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C367','青梅','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C367','青梅','采收前控水','IRRIGATE',120,'采收前控水'),
('C367','青梅','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C368','乌梅','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C368','乌梅','花前打药','FERTILIZE',35,'花前打药'),
('C368','乌梅','花后追肥','IRRIGATE',50,'花后追肥'),
('C368','乌梅','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C368','乌梅','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C368','乌梅','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C368','乌梅','采收前控水','IRRIGATE',120,'采收前控水'),
('C368','乌梅','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C369','黄梅','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C369','黄梅','花前打药','FERTILIZE',35,'花前打药'),
('C369','黄梅','花后追肥','IRRIGATE',50,'花后追肥'),
('C369','黄梅','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C369','黄梅','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C369','黄梅','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C369','黄梅','采收前控水','IRRIGATE',120,'采收前控水'),
('C369','黄梅','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C370','黑李','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C370','黑李','花前打药','FERTILIZE',35,'花前打药'),
('C370','黑李','花后追肥','IRRIGATE',50,'花后追肥'),
('C370','黑李','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C370','黑李','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C370','黑李','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C370','黑李','采收前控水','IRRIGATE',120,'采收前控水'),
('C370','黑李','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C371','红李','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C371','红李','花前打药','FERTILIZE',35,'花前打药'),
('C371','红李','花后追肥','IRRIGATE',50,'花后追肥'),
('C371','红李','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C371','红李','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C371','红李','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C371','红李','采收前控水','IRRIGATE',120,'采收前控水'),
('C371','红李','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C372','黄李','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C372','黄李','花前打药','FERTILIZE',35,'花前打药'),
('C372','黄李','花后追肥','IRRIGATE',50,'花后追肥'),
('C372','黄李','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C372','黄李','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C372','黄李','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C372','黄李','采收前控水','IRRIGATE',120,'采收前控水'),
('C372','黄李','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C373','西梅','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C373','西梅','花前打药','FERTILIZE',35,'花前打药'),
('C373','西梅','花后追肥','IRRIGATE',50,'花后追肥'),
('C373','西梅','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C373','西梅','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C373','西梅','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C373','西梅','采收前控水','IRRIGATE',120,'采收前控水'),
('C373','西梅','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C374','布朗','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C374','布朗','花前打药','FERTILIZE',35,'花前打药'),
('C374','布朗','花后追肥','IRRIGATE',50,'花后追肥'),
('C374','布朗','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C374','布朗','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C374','布朗','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C374','布朗','采收前控水','IRRIGATE',120,'采收前控水'),
('C374','布朗','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C375','油奈','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C375','油奈','花前打药','FERTILIZE',35,'花前打药'),
('C375','油奈','花后追肥','IRRIGATE',50,'花后追肥'),
('C375','油奈','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C375','油奈','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C375','油奈','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C375','油奈','采收前控水','IRRIGATE',120,'采收前控水'),
('C375','油奈','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C376','寿桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C376','寿桃','花前打药','FERTILIZE',35,'花前打药'),
('C376','寿桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C376','寿桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C376','寿桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C376','寿桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C376','寿桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C376','寿桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C377','蟠桃王','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C377','蟠桃王','花前打药','FERTILIZE',35,'花前打药'),
('C377','蟠桃王','花后追肥','IRRIGATE',50,'花后追肥'),
('C377','蟠桃王','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C377','蟠桃王','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C377','蟠桃王','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C377','蟠桃王','采收前控水','IRRIGATE',120,'采收前控水'),
('C377','蟠桃王','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C378','黄蟠桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C378','黄蟠桃','花前打药','FERTILIZE',35,'花前打药'),
('C378','黄蟠桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C378','黄蟠桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C378','黄蟠桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C378','黄蟠桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C378','黄蟠桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C378','黄蟠桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C379','油蟠桃','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C379','油蟠桃','花前打药','FERTILIZE',35,'花前打药'),
('C379','油蟠桃','花后追肥','IRRIGATE',50,'花后追肥'),
('C379','油蟠桃','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C379','油蟠桃','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C379','油蟠桃','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C379','油蟠桃','采收前控水','IRRIGATE',120,'采收前控水'),
('C379','油蟠桃','成熟期采收','HARVEST',140,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C380','蟠桃种','萌芽前施肥','SPRAY',20,'萌芽前施肥'),
('C380','蟠桃种','花前打药','FERTILIZE',35,'花前打药'),
('C380','蟠桃种','花后追肥','IRRIGATE',50,'花后追肥'),
('C380','蟠桃种','果实膨大期灌溉','SPRAY',65,'果实膨大期灌溉'),
('C380','蟠桃种','果实膨大期打药','FERTILIZE',80,'果实膨大期打药'),
('C380','蟠桃种','着色期追肥','IRRIGATE',100,'着色期追肥'),
('C380','蟠桃种','采收前控水','IRRIGATE',120,'采收前控水'),
('C380','蟠桃种','成熟期采收','HARVEST',140,'成熟期采收');

-- 作物381-400: 香辛料
INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C381','花椒','苗期除草','SPRAY',7,'苗期除草'),
('C381','花椒','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C381','花椒','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C381','花椒','生长期打药','SPRAY',30,'生长期打药'),
('C381','花椒','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C381','花椒','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C381','花椒','采收前控水','IRRIGATE',60,'采收前控水'),
('C381','花椒','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C382','八角','苗期除草','SPRAY',7,'苗期除草'),
('C382','八角','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C382','八角','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C382','八角','生长期打药','SPRAY',30,'生长期打药'),
('C382','八角','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C382','八角','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C382','八角','采收前控水','IRRIGATE',60,'采收前控水'),
('C382','八角','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C383','桂皮','苗期除草','SPRAY',7,'苗期除草'),
('C383','桂皮','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C383','桂皮','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C383','桂皮','生长期打药','SPRAY',30,'生长期打药'),
('C383','桂皮','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C383','桂皮','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C383','桂皮','采收前控水','IRRIGATE',60,'采收前控水'),
('C383','桂皮','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C384','丁香','苗期除草','SPRAY',7,'苗期除草'),
('C384','丁香','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C384','丁香','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C384','丁香','生长期打药','SPRAY',30,'生长期打药'),
('C384','丁香','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C384','丁香','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C384','丁香','采收前控水','IRRIGATE',60,'采收前控水'),
('C384','丁香','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C385','胡椒','苗期除草','SPRAY',7,'苗期除草'),
('C385','胡椒','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C385','胡椒','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C385','胡椒','生长期打药','SPRAY',30,'生长期打药'),
('C385','胡椒','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C385','胡椒','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C385','胡椒','采收前控水','IRRIGATE',60,'采收前控水'),
('C385','胡椒','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C386','白胡椒','苗期除草','SPRAY',7,'苗期除草'),
('C386','白胡椒','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C386','白胡椒','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C386','白胡椒','生长期打药','SPRAY',30,'生长期打药'),
('C386','白胡椒','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C386','白胡椒','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C386','白胡椒','采收前控水','IRRIGATE',60,'采收前控水'),
('C386','白胡椒','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C387','肉豆蔻','苗期除草','SPRAY',7,'苗期除草'),
('C387','肉豆蔻','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C387','肉豆蔻','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C387','肉豆蔻','生长期打药','SPRAY',30,'生长期打药'),
('C387','肉豆蔻','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C387','肉豆蔻','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C387','肉豆蔻','采收前控水','IRRIGATE',60,'采收前控水'),
('C387','肉豆蔻','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C388','草果','苗期除草','SPRAY',7,'苗期除草'),
('C388','草果','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C388','草果','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C388','草果','生长期打药','SPRAY',30,'生长期打药'),
('C388','草果','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C388','草果','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C388','草果','采收前控水','IRRIGATE',60,'采收前控水'),
('C388','草果','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C389','砂仁','苗期除草','SPRAY',7,'苗期除草'),
('C389','砂仁','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C389','砂仁','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C389','砂仁','生长期打药','SPRAY',30,'生长期打药'),
('C389','砂仁','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C389','砂仁','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C389','砂仁','采收前控水','IRRIGATE',60,'采收前控水'),
('C389','砂仁','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C390','白蔻','苗期除草','SPRAY',7,'苗期除草'),
('C390','白蔻','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C390','白蔻','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C390','白蔻','生长期打药','SPRAY',30,'生长期打药'),
('C390','白蔻','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C390','白蔻','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C390','白蔻','采收前控水','IRRIGATE',60,'采收前控水'),
('C390','白蔻','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C391','孜然','苗期除草','SPRAY',7,'苗期除草'),
('C391','孜然','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C391','孜然','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C391','孜然','生长期打药','SPRAY',30,'生长期打药'),
('C391','孜然','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C391','孜然','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C391','孜然','采收前控水','IRRIGATE',60,'采收前控水'),
('C391','孜然','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C392','小茴香','苗期除草','SPRAY',7,'苗期除草'),
('C392','小茴香','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C392','小茴香','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C392','小茴香','生长期打药','SPRAY',30,'生长期打药'),
('C392','小茴香','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C392','小茴香','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C392','小茴香','采收前控水','IRRIGATE',60,'采收前控水'),
('C392','小茴香','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C393','大茴香','苗期除草','SPRAY',7,'苗期除草'),
('C393','大茴香','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C393','大茴香','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C393','大茴香','生长期打药','SPRAY',30,'生长期打药'),
('C393','大茴香','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C393','大茴香','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C393','大茴香','采收前控水','IRRIGATE',60,'采收前控水'),
('C393','大茴香','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C394','香叶','苗期除草','SPRAY',7,'苗期除草'),
('C394','香叶','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C394','香叶','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C394','香叶','生长期打药','SPRAY',30,'生长期打药'),
('C394','香叶','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C394','香叶','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C394','香叶','采收前控水','IRRIGATE',60,'采收前控水'),
('C394','香叶','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C395','咖喱叶','苗期除草','SPRAY',7,'苗期除草'),
('C395','咖喱叶','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C395','咖喱叶','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C395','咖喱叶','生长期打药','SPRAY',30,'生长期打药'),
('C395','咖喱叶','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C395','咖喱叶','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C395','咖喱叶','采收前控水','IRRIGATE',60,'采收前控水'),
('C395','咖喱叶','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C396','迷迭香','苗期除草','SPRAY',7,'苗期除草'),
('C396','迷迭香','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C396','迷迭香','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C396','迷迭香','生长期打药','SPRAY',30,'生长期打药'),
('C396','迷迭香','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C396','迷迭香','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C396','迷迭香','采收前控水','IRRIGATE',60,'采收前控水'),
('C396','迷迭香','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C397','百里香','苗期除草','SPRAY',7,'苗期除草'),
('C397','百里香','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C397','百里香','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C397','百里香','生长期打药','SPRAY',30,'生长期打药'),
('C397','百里香','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C397','百里香','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C397','百里香','采收前控水','IRRIGATE',60,'采收前控水'),
('C397','百里香','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C398','牛至','苗期除草','SPRAY',7,'苗期除草'),
('C398','牛至','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C398','牛至','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C398','牛至','生长期打药','SPRAY',30,'生长期打药'),
('C398','牛至','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C398','牛至','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C398','牛至','采收前控水','IRRIGATE',60,'采收前控水'),
('C398','牛至','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C399','鼠尾草','苗期除草','SPRAY',7,'苗期除草'),
('C399','鼠尾草','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C399','鼠尾草','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C399','鼠尾草','生长期打药','SPRAY',30,'生长期打药'),
('C399','鼠尾草','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C399','鼠尾草','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C399','鼠尾草','采收前控水','IRRIGATE',60,'采收前控水'),
('C399','鼠尾草','成熟期采收','HARVEST',70,'成熟期采收');

INSERT INTO crop_procedure (crop_id, crop_name, job_name, job_type, days_after_sowing, description) VALUES
('C400','番红花','苗期除草','SPRAY',7,'苗期除草'),
('C400','番红花','幼苗追肥','FERTILIZE',15,'幼苗追肥'),
('C400','番红花','生长期灌溉','IRRIGATE',22,'生长期灌溉'),
('C400','番红花','生长期打药','SPRAY',30,'生长期打药'),
('C400','番红花','旺盛期追肥','FERTILIZE',40,'旺盛期追肥'),
('C400','番红花','旺盛期灌溉','IRRIGATE',50,'旺盛期灌溉'),
('C400','番红花','采收前控水','IRRIGATE',60,'采收前控水'),
('C400','番红花','成熟期采收','HARVEST',70,'成熟期采收');

-- 作业敏感度阈值配置
INSERT INTO job_sensitivity_threshold (job_type, weather_factor, threshold_min, threshold_max, risk_level, description) VALUES
('SPRAY','rainfall',10.00,9999.00,'RED','日降雨量超过10mm不宜打药'),
('SPRAY','rainfall',5.00,10.00,'YELLOW','日降雨量5-10mm谨慎打药'),
('SPRAY','windSpeed',5.0,99.0,'RED','风力超过5级不宜打药'),
('SPRAY','windSpeed',4.0,5.0,'YELLOW','风力4-5级谨慎打药'),
('SPRAY','temperature',35.0,99.0,'RED','高温超过35度不宜打药'),
('FERTILIZE','cumulativeRainfall',50.00,9999.00,'RED','窗口累计降雨超50mm肥料易冲刷'),
('FERTILIZE','cumulativeRainfall',25.00,50.00,'YELLOW','累计降雨25-50mm谨慎施肥'),
('FERTILIZE','temperature',0.0,10.0,'RED','日均温度低于10度土壤吸收差'),
('IRRIGATE','rainfall',10.00,9999.00,'RED','近期有降雨暂缓灌溉'),
('IRRIGATE','rainfall',3.00,10.00,'YELLOW','近期少量降雨谨慎灌溉'),
('HARVEST','rainfall',10.00,9999.00,'RED','遇降雨建议提前抢收'),
('HARVEST','rainfall',3.00,10.00,'YELLOW','少量降雨关注采收时机');

-- 销售节日配置
INSERT INTO sales_festival (festival_name, festival_month, festival_day, remark, enabled) VALUES
('春节',1,25,'年货销售最高峰',1),
('元宵节',2,15,'汤圆销售节点',1),
('清明节',4,5,'时令农产品销售',1),
('端午节',6,19,'粽子叶销售节点',1),
('中秋节',9,25,'礼盒销售旺季',1),
('国庆节',10,1,'假日农产品促销',1),
('冬至',12,22,'进补农产品销售',1);