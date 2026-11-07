#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T30 一次性迁移脚本：文件存储 → MySQL。

把以下文件目录迁移到对应的 MySQL 表（key = userId 的清洗值，data = 整段 JSON）：
    backend/var/userdata     -> userdata
    backend/var/reminders    -> reminders
    backend/var/plot-history -> plot_history
用 REPLACE INTO 保证可重复执行（重复跑不会产生重复记录）。

传感器 var/sensors 是「用户/地块」两级目录、且后台调度器要按目录枚举设备，
目前仍走文件，不在此迁移范围内。

用法：
    python3 deploy/migrate-to-mysql.py                 # 只打印 SQL，不落库
    python3 deploy/migrate-to-mysql.py | mysql -h127.0.0.1 -uroot -p agritime

依赖：无（标准库）。表结构先执行 data/store-mysql.sql。
"""
import os
import sys

# (文件目录, 表名)
COLLECTIONS = [
    ("backend/var/userdata", "userdata"),
    ("backend/var/reminders", "reminders"),
    ("backend/var/plot-history", "plot_history"),
]


def main():
    total = 0
    print("-- 生成于 migrate-to-mysql.py")
    for directory, table in COLLECTIONS:
        if not os.path.isdir(directory):
            print("-- 没有 %s 目录，跳过 %s 表" % (directory, table), file=sys.stderr)
            continue
        files = [f for f in os.listdir(directory) if f.endswith(".json")]
        if not files:
            print("-- %s 为空，跳过 %s 表" % (directory, table), file=sys.stderr)
            continue
        for f in sorted(files):
            key = f[:-5]  # 去掉 .json，就是 userId 的清洗值（主键 k）
            with open(os.path.join(directory, f), "r", encoding="utf-8") as fh:
                data = fh.read()
            # 单引号转义；REPLACE INTO 幂等，可重复执行
            escaped = data.replace("\\", "\\\\").replace("'", "\\'")
            print("REPLACE INTO %s (k, data, updated_at) VALUES ('%s', '%s', NOW());"
                  % (table, key, escaped))
            total += 1
    if total == 0:
        print("-- 没有可迁移的数据", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
