# Liquibase 常用命令（take-out-admin）

前置：MySQL 已在 `127.0.0.1:3307` 就绪；在 `take-out-admin` 目录执行。

## 新环境（空库，推荐）

```text
docker compose up -d mysql redis          # 不挂 sky.sql；仅空库+账号
# 等 healthy 后：
mvn liquibase:update                      # 跑 001 基线 + 002+
# 或直接启动 admin（Spring 会自动 update）
```

## 已有数据的旧库（表已在，换 changelog 后）

若库里已有与 001 相同的表，**不要**再 `update` 跑 DROP/CREATE：

```text
mvn liquibase:changelogSync               # 把当前 changelog 全部记为已执行
# 之后只对「库里还没有」的新 changeSet 用 update
```

若曾用过旧的 `001-baseline-from-sky-sql` 标记，清账本或换库后再 sync（学习环境常用 `down -v` 重来）。

## 命令速查

| 命令 | 作用 |
|------|------|
| `mvn liquibase:update` | 执行未跑的 changeSet |
| `mvn liquibase:status` | 还有哪些没跑 |
| `mvn liquibase:changelogSync` | 全部标记已跑、不执行 SQL |
| `mvn liquibase:history` | 执行历史 |
| `mvn liquibase:rollback -Dliquibase.rollbackCount=1` | 回滚最近 1 个（需 rollback） |

Spring Boot 启动 ≈ 自动 `update`。

详情：`docs/tutorials/2026-08-12-liquibase-guide.md`  
设计：`docs/superpowers/specs/2026-08-12-liquibase-full-versioning-design.md`
