-- 会看天的农事日历 · MySQL 后端表结构（T30）
--
-- 每个「集合」一张同构表：k = userId 的清洗值（主键），data = 整段不透明 JSON。
-- 中文用 utf8mb4；updated_at 用于多端同步时按"最后修改时间"合并。
--
-- 已接入：userdata（我的作物/地块）、reminders（提醒档案）、plot_history（地块历史）。
-- 传感器 sensors 因需要「按用户枚举设备目录」给后台调度器用，仍走文件（var/sensors/），
-- 若后续要给 MysqlBlobStore 加 key 枚举能力再切。

CREATE TABLE IF NOT EXISTS userdata (
  k          VARCHAR(128) NOT NULL,
  data       LONGTEXT     NOT NULL,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reminders (
  k          VARCHAR(128) NOT NULL,
  data       LONGTEXT     NOT NULL,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS plot_history (
  k          VARCHAR(128) NOT NULL,
  data       LONGTEXT     NOT NULL,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
