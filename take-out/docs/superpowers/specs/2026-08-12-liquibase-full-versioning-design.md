# Liquibase 全量版本化（方案 A）

日期：2026-08-12  
状态：已落地对照  
相关：`docs/tutorials/2026-08-12-liquibase-guide.md`

## 目标

Docker **不挂业务 DDL**；表结构与种子数据全部由 Liquibase changelog 版本化。

## 分工

| 层 | 职责 |
|----|------|
| Docker init | 仅 charset + 业务账号；`MYSQL_DATABASE=take_out` 建空库 |
| Liquibase `001` | 原 `sky.sql` 全量建表 + 种子（单文件） |
| Liquibase `002+` | 增量（如 `pay_attempt`） |

## Changelog

- `db.changelog-master.yaml` → include `001`、`002`…
- `001-baseline-schema-and-seed.sql`：自 `sky.sql` 去掉 `CREATE DATABASE` / `USE`
- 空库：`compose up` → 启 admin / `liquibase:update` 建全库
- 已有数据卷：对 001（及已存在对象）先 `changelogSync`，勿重复执行 DROP/CREATE

## 非目标

- 不按表拆成几十个 changeSet（方案 B）
- 本期不拆「结构 / 种子」双文件（方案 C）
