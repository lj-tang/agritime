-- 会看天的农事日历 · MySQL 后端表结构（T30）
--
-- 每个「集合」一张同构表：k = userId 的清洗值（主键），data = 整段不透明 JSON。
-- 中文用 utf8mb4；updated_at 用于多端同步时按"最后修改时间"合并。
--
-- 当前后端只接入了用户业务数据集合 userdata（我的作物/地块，经 /api/me/data 同步）。
-- 提醒 / 传感器 / 地块历史 若也要切 MySQL，按同样的结构再建 reminders / sensors / plot_history 表即可。

CREATE TABLE IF NOT EXISTS userdata (
  k          VARCHAR(128) NOT NULL,
  data       LONGTEXT     NOT NULL,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
