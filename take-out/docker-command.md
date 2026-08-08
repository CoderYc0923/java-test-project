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

### 常用查看 / 调试

```bash
# 进入交互
docker exec -it take-out-redis redis-cli

# 下面在 redis-cli 里执行，或写成：
# docker exec -it take-out-redis redis-cli KEYS 'order:*'

KEYS *
KEYS order:*
GET order:idempotent:你的-requestId
TTL order:idempotent:你的-requestId

# 清空当前 DB（学习环境可用；别在生产乱 FLUSH）
FLUSHDB

# 退出
exit
```

一行命令示例：

```bash
docker exec -it take-out-redis redis-cli KEYS "order:*"
docker exec -it take-out-redis redis-cli GET "order:pay:lock:1001"
```

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
