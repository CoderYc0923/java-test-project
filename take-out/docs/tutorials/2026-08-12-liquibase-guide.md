# Liquibase 接入教程（take-out / Spring Boot 4.1）

日期：2026-08-12  
适用：本仓库 `take-out`（Spring Boot **4.1.0**、MySQL 8、库 `take_out`）  
文档性质：**教学对照稿**——按本文可自行接入；**默认不假定你已改完 pom**。  
前置：已会用 Docker MySQL（端口 `3307`）、知道现有 `sky.sql` + `docs/sql/*.sql` 手工迁移方式。

---

## 0. 一句话

**Liquibase = 给数据库变更编号、记账、按序自动执行的工具。**  
应用启动（或 CI）时对照「changelog 清单」和库里的 `DATABASECHANGELOG` 表，只跑还没跑过的变更。

| 以前（本仓库） | 接入 Liquibase 后 |
|----------------|-------------------|
| `sky.sql` 只在空卷初始化 | 仍可作「空库种子」；**增量以 Liquibase 为准** |
| `docs/sql/*.sql` 人手 `SOURCE` | 改成 changelog，启动自动跑 |
| 新人漏跑脚本 → 缺表 | 启动失败或自动补齐，环境更一致 |

Java 圈另一常见选择是 **Flyway**；二者都是 Migration。本教程只讲 Liquibase。

---

## 1. 核心概念（先记这 5 个）

```text
changelog（变更清单）
  └─ changeSet（一次原子变更，有 id + author）
        └─ 里面写 SQL 或 Liquibase 结构化标签（createTable 等）

DATABASECHANGELOG      ← Liquibase 自动建的「已执行账本」
DATABASECHANGELOGLOCK  ← 防止多实例同时跑迁移
```

| 概念 | 含义 |
|------|------|
| **Master changelog** | 总入口，一般只 `include` 别的文件 |
| **changeSet** | 最小执行单位；`id` + `author` + `filename` 唯一 |
| **checksum** | 已执行 changeSet 内容被改会报错（防偷偷改历史） |
| **baseline** | 已有库「认账」：告诉 Liquibase 以前的表已经有了，别再从头建 |
| **contexts / labels** | 按环境过滤（如只在 `dev` 跑种子数据） |

**铁律：已经合进主干、别人可能跑过的 changeSet，不要改内容；要改结构就再写一个新 changeSet。**

---

## 2. 和你们现状怎么共存

推荐策略（学习项目最稳）：

```text
空库第一次：
  Docker init 仍可执行 sky.sql（有全量表 + 种子）
  然后应用启动 Liquibase：
    → 用 changelogSync / baseline 把「当前已有结构」记进 DATABASECHANGELOG
    → 之后只追加新 changeSet（如 pay_attempt）

已有开发库（卷里已有数据）：
  不要 down -v
  接入 Liquibase 后做一次 baseline / changelogSync
  再追加新表的 changeSet
```

更「纯 Liquibase」的远期目标（可选）：

- Docker 只建库 + 用户，**不**挂 `sky.sql`
- `V1` / `001` 用 Liquibase 建全表 + 种子  
- 本教程**不强制**一步到位，避免一次改炸本地数据。

---

## 3. 模块放哪里

启动模块是 `take-out-admin`，Liquibase 跟 **会连业务库并启动的那个应用** 走：

```text
take-out-admin/
  pom.xml                          ← 加 spring-boot-starter-liquibase
  src/main/resources/
    application.yml                ← spring.liquibase.*
    db/changelog/
      db.changelog-master.yaml     ← 总入口
      changes/
        001-baseline-marker.yaml   ← 已有库认账（见 §6）
        002-create-pay-attempt.yaml
        002-create-pay-attempt.sql ← 可选：SQL 形式
```

`take-out-mock-wechat` 若自有库，另说；本期只管 `take_out`。

账号：`application.yml` 里 `takeout_rw` 对 `take_out.*` 已是 `ALL PRIVILEGES`，足够建表和写 `DATABASECHANGELOG`。

---

## 4. 加依赖（Spring Boot 4 注意）

Boot 3 常写 `liquibase-core`；**Boot 4 必须用 starter**，否则可能启动时根本不跑迁移：

```xml
<!-- take-out-admin/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-liquibase</artifactId>
</dependency>
```

版本由父 POM `spring-boot-starter-parent` **4.1.0** 管理，不必手写 version。

---

## 5. 配置

在 `take-out-admin/src/main/resources/application.yml` 增加：

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
    # 默认用 spring.datasource；一般不用再写 url/user/password
    # 本地若想暂时关掉迁移：
    # enabled: false
```

本地覆盖可在 `application-local.yml`：

```yaml
spring:
  liquibase:
    enabled: true   # 或 false 临时跳过
```

MyBatis-Plus **不要**指望它建表；表结构以 Liquibase（或现有 sky.sql）为准。

---

## 6. 已有库：第一次接入最关键（baseline）

你们库里已经有 `orders`、`employee` 等（来自 `sky.sql`）。  
若 changelog 里再写一遍 `CREATE TABLE orders`，启动会炸。

### 6.1 推荐做法：空的「认账」changeSet + changelogSync

**Master：**

```yaml
# db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-baseline-marker.yaml
  - include:
      file: db/changelog/changes/002-create-pay-attempt.yaml
```

**001：只作标记，不执行 DDL**（表示「到此为止的基线已由 sky.sql 建好」）：

```yaml
# db/changelog/changes/001-baseline-marker.yaml
databaseChangeLog:
  - changeSet:
      id: 001-baseline-from-sky-sql
      author: take-out
      comment: 现网表结构来自 sky.sql；本 changeSet 仅作基线标记，无 DDL
      changes:
        - tagDatabase:
            tag: baseline-sky-sql
```

`tagDatabase` 几乎无害，用来打标签，方便以后 rollback 到某点（教学够用）。

### 6.2 在「已有表」的库上同步账本（任选一种）

**方式 A — Maven 插件 changelogSync（推荐理解）**

含义：把 changelog 里**当前已有**的 changeSet 全部记为「已执行」，**但不真跑 SQL**。  
适合：001 只有标记、002 还没加进 master 之前；或你确信 001～00N 对应的结构库里都已有。

```xml
<!-- 可选：父模块或 admin 的 build/plugins，仅本地/CI 使用 -->
<plugin>
  <groupId>org.liquibase</groupId>
  <artifactId>liquibase-maven-plugin</artifactId>
  <configuration>
    <changeLogFile>src/main/resources/db/changelog/db.changelog-master.yaml</changeLogFile>
    <url>jdbc:mysql://127.0.0.1:3307/take_out?useUnicode=true&amp;characterEncoding=utf-8&amp;serverTimezone=Asia/Shanghai</url>
    <username>takeout_rw</username>
    <password>TakeoutRw@123</password>
  </configuration>
</plugin>
```

在 `take-out-admin` 目录：

```bash
# 只记账、不执行（首次接入已有库时用）
mvn liquibase:changelogSync
```

然后把 **002 新表** 加进 master，再正常启动应用 → 只会跑 002。

**方式 B — 启动时用 context 跳过基线 DDL**

基线 changeSet 加 `context: legacy`，本地 `spring.liquibase.contexts: !legacy`……对新手易绕，**本教程以方式 A 为主**。

**方式 C — 空库从零**

`docker compose down -v` 清空后只留用户/库、不挂 sky.sql，让 Liquibase 从 001 建全表——改动大，以后再做。

### 6.3 怎么确认 baseline 成功

连上 MySQL：

```sql
USE take_out;
SHOW TABLES LIKE 'DATABASECHANGELOG%';
SELECT id, author, filename, dateexecuted, exectype, tag
FROM DATABASECHANGELOG
ORDER BY dateexecuted;
```

应能看到 `001-baseline-from-sky-sql`，`exectype` 多为 `EXECUTED` 或 `MARK_RAN`（视命令而定）。

---

## 7. 加新表完整示例：`pay_attempt`

设计稿见：`docs/superpowers/specs/2026-08-11-pay-attempt-single-active-design.md` §5.1。

### 7.1 用 YAML 结构化变更（可移植、可自动 rollback 部分类型）

```yaml
# db/changelog/changes/002-create-pay-attempt.yaml
databaseChangeLog:
  - changeSet:
      id: 002-create-pay-attempt
      author: take-out
      comment: 支付尝试表，一笔「去支付」一行
      changes:
        - createTable:
            tableName: pay_attempt
            remarks: 支付尝试/支付单
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: order_id
                  type: BIGINT
                  remarks: 业务单 orders.id
                  constraints:
                    nullable: false
              - column:
                  name: order_number
                  type: VARCHAR(64)
                  remarks: 业务单号
                  constraints:
                    nullable: false
              - column:
                  name: out_trade_no
                  type: VARCHAR(64)
                  remarks: 渠道商户单号
                  constraints:
                    nullable: false
              - column:
                  name: channel
                  type: VARCHAR(32)
                  defaultValue: WECHAT
                  remarks: 渠道
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: VARCHAR(32)
                  remarks: PAYING/SUCCESS/CLOSED/REFUNDING/REFUNDED
                  constraints:
                    nullable: false
              - column:
                  name: amount
                  type: DECIMAL(10,2)
                  constraints:
                    nullable: false
              - column:
                  name: prepay_id
                  type: VARCHAR(128)
              - column:
                  name: paying_flag
                  type: TINYINT
                  remarks: 1=进行中，否则 NULL；配合唯一索引保证同单最多一条 PAYING
              - column:
                  name: created_at
                  type: DATETIME
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: DATETIME
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
        - addUniqueConstraint:
            tableName: pay_attempt
            columnNames: out_trade_no
            constraintName: uk_out_trade_no
        - addUniqueConstraint:
            tableName: pay_attempt
            columnNames: order_id, paying_flag
            constraintName: uk_order_paying
        - createIndex:
            tableName: pay_attempt
            indexName: idx_order_id
            columns:
              - column:
                  name: order_id
```

> MySQL 对「`paying_flag` 为 NULL 时唯一索引可多行」的语义依赖 InnoDB；与设计稿一致即可。

### 7.2 或用 SQL changeSet（更直观，和你们 `docs/sql` 习惯接近）

```yaml
# db/changelog/changes/002-create-pay-attempt.yaml
databaseChangeLog:
  - changeSet:
      id: 002-create-pay-attempt
      author: take-out
      comment: 支付尝试表（SQL 形式）
      changes:
        - sqlFile:
            path: db/changelog/changes/002-create-pay-attempt.sql
            relativeToChangelogFile: false
            splitStatements: true
            stripComments: true
```

```sql
-- db/changelog/changes/002-create-pay-attempt.sql
CREATE TABLE IF NOT EXISTS pay_attempt (
  id            BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT       NOT NULL COMMENT '业务单 orders.id',
  order_number  VARCHAR(64)  NOT NULL COMMENT '业务单号 ORD...',
  out_trade_no  VARCHAR(64)  NOT NULL COMMENT '渠道商户单号，回调定位用',
  channel       VARCHAR(32)  NOT NULL DEFAULT 'WECHAT' COMMENT '教学期仅 WECHAT',
  status        VARCHAR(32)  NOT NULL COMMENT 'PAYING/SUCCESS/CLOSED/REFUNDING/REFUNDED',
  amount        DECIMAL(10,2) NOT NULL,
  prepay_id     VARCHAR(128) NULL,
  paying_flag   TINYINT      NULL COMMENT '1=进行中，否则 NULL',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  UNIQUE KEY uk_order_paying (order_id, paying_flag),
  KEY idx_order_id (order_id)
) COMMENT='支付尝试/支付单';
```

教学建议：**新表用 SQL 文件也完全可以**，团队熟悉、和 Navicat 导出 DDL 也好对。

### 7.3 改 master 顺序

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-baseline-marker.yaml
  - include:
      file: db/changelog/changes/002-create-pay-attempt.yaml
```

**只追加 include，不要重排已发布文件的顺序。**

### 7.4 启动验证

```bash
# 在 take-out 根或 admin 模块
mvn -pl take-out-admin -am spring-boot:run
```

日志中应出现 Liquibase 执行 `002-create-pay-attempt` 的信息。然后：

```sql
SHOW CREATE TABLE pay_attempt\G
SELECT id, filename FROM DATABASECHANGELOG WHERE id LIKE '002%';
```

---

## 8. 日常加表 / 加列标准流程（接入后）

```text
1. 新建 changes/00N-xxx.yaml（或 .sql）
2. 在 db.changelog-master.yaml 末尾 include
3. 本地启动 admin → 自动迁移
4. 写实体 / Mapper / 业务
5. 提交：changelog + Java 同一 PR
6. 不要改已经跑过的 00N 文件内容
```

加列示例：

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-pay-attempt-add-fail-reason
      author: take-out
      changes:
        - addColumn:
            tableName: pay_attempt
            columns:
              - column:
                  name: fail_reason
                  type: VARCHAR(255)
                  remarks: 失败原因
```

---

## 9. 常用命令（Maven 插件）

在配置好 `liquibase-maven-plugin` 后（见 §6.2）：

| 目标 | 作用 |
|------|------|
| `mvn liquibase:update` | 执行未跑的 changeSet（类似启动时） |
| `mvn liquibase:status` | 看还有哪些没跑 |
| `mvn liquibase:changelogSync` | 全部标记为已跑、不执行 SQL（已有库认账） |
| `mvn liquibase:rollback -Dliquibase.rollbackCount=1` | 回滚最近 1 个（需 rollback 段或结构化变更） |
| `mvn liquibase:history` | 看执行历史 |

Spring Boot 启动 ≈ 自动 `update`（在 DataSource 就绪后）。

---

## 10. rollback 怎么写（SQL 形式必补）

结构化 `createTable` 常能自动生成 rollback；**`sqlFile` / 裸 SQL 不会**，要手写：

```yaml
- changeSet:
    id: 002-create-pay-attempt
    author: take-out
    changes:
      - sqlFile:
          path: db/changelog/changes/002-create-pay-attempt.sql
          relativeToChangelogFile: false
    rollback:
      - sql: DROP TABLE IF EXISTS pay_attempt;
```

学习项目可以先不回滚；上生产前重要变更要有 rollback 或「向前修」的下一个 changeSet。

---

## 11. 校验和（checksum）报错怎么办

症状：改了已经执行过的 changeSet 文件，启动报 `Validation Failed: ... checksum ...`

| 做法 | 何时用 |
|------|--------|
| **正确**：还原文件，再写新 changeSet | 默认 |
| `liquibase clearCheckSums` 后再 update | 仅本地折腾、确认安全时 |
| `validCheckSum` 写进 changeSet | 极少数迁移工具升级场景 |

**不要在共用开发库上随便 clearCheckSums。**

---

## 12. 多实例 / 锁

多副本同时启动时，Liquibase 用 `DATABASECHANGELOGLOCK` 互斥，一般一个跑迁移、别的等。  
若进程被 kill 可能导致锁残留：

```sql
SELECT * FROM DATABASECHANGELOGLOCK;
-- 确认无迁移在跑后再清（慎用）
UPDATE DATABASECHANGELOGLOCK SET LOCKED=0, LOCKGRANTED=NULL, LOCKEDBY=NULL WHERE ID=1;
```

---

## 13. 和 Docker `sky.sql` 的长期分工

本仓库已落地「理想态」（方案 A，见 `docs/superpowers/specs/2026-08-12-liquibase-full-versioning-design.md`）：

| 层 | 现状 |
|----|------|
| Docker init | 仅 `00-charset` + `01-users`；**不再挂** `sky.sql` |
| Liquibase `001` | `001-baseline-schema-and-seed` = 原 sky.sql 全量结构+种子 |
| Liquibase `002+` | 增量（如 `pay_attempt`） |
| 根目录 `sky.sql` | 仅归档对照，不是运行时真相源 |

新环境：`docker compose up -d mysql` → 启 admin 或 `mvn liquibase:update`。  
已有卷：先 `changelogSync`，或 `down -v` 后按新环境重来。

---

## 14. 完整目录树（目标形态）

```text
take-out-admin/
  pom.xml                          # + spring-boot-starter-liquibase
                                   # 可选 + liquibase-maven-plugin
  src/main/resources/
    application.yml                # spring.liquibase.change-log=...
    db/changelog/
      db.changelog-master.yaml
      changes/
        001-baseline-marker.yaml
        002-create-pay-attempt.yaml
        002-create-pay-attempt.sql   # 若用 sqlFile
```

### Master 完整示例

```yaml
# classpath:db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-baseline-marker.yaml
  - include:
      file: db/changelog/changes/002-create-pay-attempt.yaml
```

### application.yml 片段

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3307/take_out?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: takeout_rw
    password: TakeoutRw@123
    driver-class-name: com.mysql.cj.jdbc.Driver
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

---

## 15. 接入检查清单（按顺序勾）

- [ ] `take-out-admin` 已加 `spring-boot-starter-liquibase`（Boot 4 用 starter，不要只加 core）
- [ ] 建好 `db.changelog-master.yaml` + `001-baseline-marker`
- [ ] **已有数据的库**先 `changelogSync`（或等价认账），再把「真正的新 DDL」放进 002+
- [ ] 启动日志出现 Liquibase，且 `DATABASECHANGELOG` 有记录
- [ ] 新表 `SHOW CREATE TABLE` 符合预期
- [ ] 约定：已发布 changeSet 不改内容；`sky.sql` 与增量如何同步写进团队习惯
- [ ] （可选）SQL changeSet 补上 `rollback`
- [ ] （可选）CI 里对空库跑一遍 `update` 做冒烟

---

## 16. 和 Flyway 对比（面试用）

| | Liquibase | Flyway |
|--|-----------|--------|
| 脚本形式 | YAML/XML/JSON/SQL 均可 | 主要是版本化 SQL（也有 Java） |
| 回滚 | 一等公民（Commercial 更强；开源也可手写 rollback） | 社区版偏「向前修」 |
| Spring Boot 4 | `spring-boot-starter-liquibase` | `spring-boot-starter-flyway` |
| 账本表 | `DATABASECHANGELOG` | `flyway_schema_history` |

选哪个不如**用起来规范**重要；本仓库按你的选择走 Liquibase。

---

## 17. 一句话

**Boot 4 加 `spring-boot-starter-liquibase` → master changelog → 已有库先认账（baseline/sync）→ 以后加表只追加新 changeSet → 启动自动迁移；历史文件只增不改。**

下一步若要「真正改仓库」，建议顺序：pom + master + 001 认账 → 本地 sync → 再提 002 `pay_attempt`。需要我直接在项目里落地时，说一声即可。
