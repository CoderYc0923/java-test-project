# Docker 常用命令（MySQL + Redis）

在项目根目录 `take-out/` 下执行。

---

## 启动 / 停止（整体）

```bash
# 后台启动全部服务（mysql + redis）
docker compose up -d

# 只起某一个
docker compose up -d mysql
docker compose up -d redis

# 看状态
docker compose ps
docker compose logs -f mysql
docker compose logs -f redis

# 停容器（数据还在 volume 里）
docker compose stop

# 删容器但保留数据卷
docker compose down

# 连数据卷一起删（MySQL / Redis 数据都清空）
docker compose down -v
```

---

## MySQL

### 启停与日志

```bash
docker compose up -d mysql
docker compose logs -f mysql
```

首次空卷会自动：建库、建用户、导入 `sky.sql`。

### 跨机器同步数据（通过 GitHub）

改表 / 改业务数据后：

1. **导出**当前库到 `sky.sql`
2. **commit + push** `sky.sql`
3. 别的机器 `git pull` 后按下面「导入」或「空卷重建」

#### 导出（本机 → sky.sql）

```bash
docker exec take-out-mysql mysqldump -uroot -proot --databases take_out --default-character-set=utf8mb4 -r /tmp/sky.sql
docker cp take-out-mysql:/tmp/sky.sql ./sky.sql
```

#### 导入（sky.sql → 本机）

```bash
docker cp ./sky.sql take-out-mysql:/tmp/sky.sql
docker exec -i take-out-mysql mysql -uroot -proot -e "SOURCE /tmp/sky.sql"
```

#### 新机器 / 想完全按仓库重建

```bash
git pull
docker compose down -v
docker compose up -d
```

### MySQL 连接信息

| 项 | 值 |
|----|-----|
| Host | `127.0.0.1` |
| Port | `3307`（不是 3306） |
| Database | `take_out` |
| 读写 | `takeout_rw` / `TakeoutRw@123` |
| 只读 | `takeout_ro` / `TakeoutRo@123` |
| root | `root` / `root` |

```text
jdbc:mysql://127.0.0.1:3307/take_out
```

---

## Redis

### 启停与自检

```bash
docker compose up -d redis
docker compose logs -f redis

# 容器内 ping（期望 PONG）
docker exec -it take-out-redis redis-cli ping
```

### 进入 redis-cli

```bash
docker exec -it take-out-redis redis-cli
# 退出：exit 或 Ctrl+D
```

下面命令可在交互里敲，也可一行：`docker exec -it take-out-redis redis-cli <命令>`。

### 本项目 key 约定

| 用途 | key 格式 | value 含义 |
|------|----------|------------|
| 防重复下单 | `order:idempotent:{requestId}` | `PROCESSING`=建单中；数字=已成功的 orderId |
| 支付锁 | `order:pay:lock:{orderId}` | 随机 token（谁加的锁） |

### 支付 / 下单排障常用

```bash
# ----- 查有哪些业务 key -----
KEYS order:*
KEYS order:idempotent:*
KEYS order:pay:lock:*

# ----- 下单幂等 -----
# 看某个 requestId 当前状态
GET order:idempotent:你的-requestId
# 剩余存活秒数（-1 永不过期，-2 key 不存在）
TTL order:idempotent:你的-requestId

# ----- 支付锁 -----
GET order:pay:lock:1005
TTL order:pay:lock:1005

# ----- 读 / 写 / 删（调试用）-----
SET demo:key hello EX 60          # 写入，60 秒过期
GET demo:key
DEL demo:key                      # 删单个
DEL order:pay:lock:1005           # 手动清死锁（学习环境；等 TTL 过期也行）

# ----- 占坑测试（对应代码里的 SET NX）-----
SET order:pay:lock:999 test-token NX EX 10
# 再执行一次：应返回 (nil)，说明抢锁失败

# ----- 危险：清空当前库（仅学习）-----
FLUSHDB
```

一行示例：

```bash
docker exec -it take-out-redis redis-cli KEYS "order:*"
docker exec -it take-out-redis redis-cli GET "order:pay:lock:1005"
docker exec -it take-out-redis redis-cli TTL "order:pay:lock:1005"
docker exec -it take-out-redis redis-cli DEL "order:pay:lock:1005"
```

### 其它偶尔用到的

```bash
PING                 # 期望 PONG
DBSIZE               # 当前库 key 数量
TYPE order:pay:lock:1005   # 类型，一般是 string
EXISTS order:pay:lock:1005 # 1=存在 0=不存在
INFO keyspace        # 各库大概有多少 key
MONITOR              # 实时看所有命令（很吵，Ctrl+C 停；别长期开）
```

> 生产环境少用 `KEYS *`（会扫全库），应用 `SCAN`；学习项目数据少，用 `KEYS` 没问题。

### Redis 连接信息（给 Spring）

| 项 | 值 |
|----|-----|
| Host | `127.0.0.1` |
| Port | `6379` |
| Password | 无（当前 compose 未设密码） |
| database | `0`（默认） |

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

### Redis 要不要像 MySQL 一样导出 / 换机导入？

**学习项目里：一般不需要。**

| | MySQL | Redis（你们用法） |
|--|-------|-------------------|
| 存什么 | 表结构 + 业务数据（员工、菜、订单…） | 幂等键、支付锁等**临时状态** |
| 换机器要同步吗 | **要**——靠 `sky.sql` 进 Git | **通常不要**——新机器 `docker compose up -d redis` 空库即可 |
| 丢了会怎样 | 业务数据没了 | 最多锁/幂等键没了，重新下单支付即可 |

原因：

- Redis 在本项目里是**缓存 / 锁 / 幂等**，不是订单真相来源（真相在 MySQL）。
- 锁带 TTL，幂等键也会过期；空 Redis 不影响表数据。
- 换电脑：拉代码 + `docker compose up -d`，MySQL 用 `sky.sql`，Redis 空着就能跑。

什么时候才要「备份 Redis」？

- 真把购物车、Session 等**必须持久**的数据只放在 Redis 里时，才会考虑 RDB/AOF 备份或迁移。
- 当前支付锁 / `requestId` 幂等：**不必**导出进 Git。

（compose 里开了 `--appendonly yes`，只是容器/本机 volume 重启后键还在；**不是**用来跨机器同步的。）

---

## 新机器最小步骤

```bash
git pull
docker compose up -d
# MySQL：空卷会自动导入 sky.sql；若卷已存在且要覆盖，用上面「导入」或 down -v
# Redis：直接用，无需导入
docker exec -it take-out-redis redis-cli ping
```
