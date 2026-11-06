#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T30 一次性迁移脚本：文件存储 → MySQL。

把 var/userdata/*.json 迁移到 MySQL 的 userdata 表。
用 REPLACE INTO 保证可重复执行（重复跑不会产生重复记录）。

用法：
    python3 deploy/migrate-to-mysql.py                 # 只打印 SQL，不落库
    python3 deploy/migrate-to-mysql.py | mysql -h127.0.0.1 -uroot -p agritime

依赖：无（标准库）。表结构先执行 data/store-mysql.sql。
"""
import json
import os
import sys

USERDATA_DIR = "var/userdata"


def main():
    if not os.path.isdir(USERDATA_DIR):
        print("-- 没有 var/userdata 目录，无需迁移", file=sys.stderr)
        return 0

    files = [f for f in os.listdir(USERDATA_DIR) if f.endswith(".json")]
    if not files:
        print("-- var/userdata 为空，无需迁移", file=sys.stderr)
        return 0

    print("-- 生成于 migrate-to-mysql.py，共 %d 个用户" % len(files))
    for f in sorted(files):
        key = f[:-5]  # 去掉 .json，就是 userId 的清洗值（主键 k）
        with open(os.path.join(USERDATA_DIR, f), "r", encoding="utf-8") as fh:
            data = fh.read()
        # 单引号转义；REPLACE INTO 幂等，可重复执行
        escaped = data.replace("\\", "\\\\").replace("'", "\\'")
        print("REPLACE INTO userdata (k, data, updated_at) VALUES ('%s', '%s', NOW());"
              % (key, escaped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
