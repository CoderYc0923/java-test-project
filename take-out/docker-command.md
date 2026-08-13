# Docker 常用命令（MySQL + Redis + RocketMQ）

在项目根目录 `take-out/` 下执行。

---

## 启动 / 停止（整体）

```bash
# 后台启动全部服务（mysql + redis + rocketmq-namesrv + rocketmq-broker）
docker compose up -d

# 只起某一个 / 某一组
docker compose up -d mysql
docker compose up -d redis
docker compose up -d rocketmq-namesrv rocketmq-broker

# 看状态
docker compose ps
docker compose logs -f mysql
docker compose logs -f redis
docker compose logs -f rocketmq-namesrv
docker compose logs -f rocketmq-broker

# 停容器（mysql/redis/rocketmq-broker 的 volume 数据还在，除非 down -v）
docker compose stop

# 删容器但保留已声明的 volume（mysql/redis/mq store 数据还在）
docker compose down

# 连数据卷一起删（MySQL / Redis / RocketMQ 消息存储都清空）
docker compose down -v
```

业务表与种子**不再**由 Docker 挂载 `sky.sql` 初始化，而由 Liquibase 管理（`take-out-admin` 启动或 `mvn liquibase:update`）。  
空卷 up 后库是空的，需再跑一次迁移才会有表。详见 `liquibase-command.md`。  
RocketMQ 教学见 `docs/tutorials/2026-08-13-rocketmq-p0-outbox-handson.md`。

---

## MySQL

### 启停与日志

```bash
docker compose up -d mysql
docker compose logs -f mysql
```

首次空卷会自动：建库 `take_out`、建 `takeout_rw` / `takeout_ro`（见 `docker/mysql/init/`）。  
**表结构与种子**靠 Liquibase，不是再挂 `sky.sql`。

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

## RocketMQ

### 生产怎么部署（你要按这个理解）

线上 MQ **不是**可有可无的玩具，和 MySQL / Redis 一样是独立中间件：

| 组件 | 生产习惯 | 本地 compose 怎么对齐 |
|------|----------|------------------------|
| **Broker** | 消息落盘；多台主从/多副本；磁盘独立监控 | 挂 volume：`store`（消息）+ `logs` |
| **NameServer** | 多实例无状态路由；一般不存消息 | 可不挂卷 |
| Topic | **预先创建**，关自动建 Topic | 本地仍开 `autoCreateTopicEnable` 图省事；生产应关掉 |
| 接入 | VPC 内网地址 / 云托管 | `name-server: 127.0.0.1:9876` |
| 可靠性 | Broker 持久化 **+** 业务 Outbox | 两者都要：MQ 存消息，Outbox 防「库成了没发出」 |

**数据卷在生产的意义：**  
容器/进程重启后，未消费完的消息还在盘上，消费者还能继续拉。  
不挂卷 = 删容器消息没了——生产不可接受，所以本地也按生产习惯挂上。

**和 Outbox 的分工（别混）：**

```text
MySQL Outbox     → 保证「业务已提交」一定会被尝试投递（发送侧）
Broker 磁盘/卷   → 保证「已进入 MQ 的消息」重启不丢（中间件侧）
消费幂等         → 保证重复投递不重复干活（消费侧）
```

三层都要，不是「有 Outbox 就可以不落盘」。

### 本项目 compose 服务

| 服务名 | 容器名 | 作用 | 数据卷 |
|--------|--------|------|--------|
| `rocketmq-namesrv` | `take-out-rocketmq-namesrv` | 路由（NameServer） | 无（可接受） |
| `rocketmq-broker-init` | （一次性） | `chown` store/logs 给 `rocketmq` | 同上两卷 |
| `rocketmq-broker` | `take-out-rocketmq-broker` | 存消息、投递 | `take-out-rocketmq-broker-store` / `-logs` |

Broker 配置：`docker/rocketmq/broker.conf`（挂载进容器；勿用 YAML `>` + `echo` 拼配置）。

- `brokerIP1=127.0.0.1`：宿主机连本地；生产改成内网 IP/域名  
- `storePathRootDir=/home/rocketmq/store`：消息目录（已挂卷）  
- `autoCreateTopicEnable=true`：仅本地方便；**生产应 false**  
- `JAVA_OPT_EXT=-Xms512m -Xmx512m`：Broker 比 NameServer 更吃内存

**Broker 秒退常见原因（本仓库已踩过）：**

1. **卷属主是 root**：named volume 默认 `root:root`，进程用户是 `rocketmq`，写不了 store → 启动失败；控制台常只看到 shutdown 时的 `NullPointerException`（次生错误）。由 `rocketmq-broker-init` 先 `chown`。  
2. **YAML `>` 折行**：`command: >` 会把换行变成空格，生成的 conf 变成一行无效配置。

### 启停与日志

```bash
# 建议一起起（会先跑 broker-init 再起 broker）
docker compose up -d rocketmq-namesrv rocketmq-broker

docker compose ps
docker compose logs -f rocketmq-namesrv
docker compose logs -f rocketmq-broker

# 只停 MQ（volume 里消息还在；再 up 还能接着消费）
docker compose stop rocketmq-broker rocketmq-namesrv
docker compose up -d rocketmq-namesrv rocketmq-broker

# 重启 Broker
docker compose restart rocketmq-broker
```

镜像拉取慢时：

```bash
docker compose up -d rocketmq-namesrv rocketmq-broker --pull never
```

**清空 MQ 消息（相当于 Redis FLUSH，仅本地排障）：**

```bash
docker compose stop rocketmq-broker
docker volume rm take-out_take-out-rocketmq-broker-store
# 若提示 in use：先 docker compose down，再 volume rm，再 up
docker compose up -d rocketmq-namesrv rocketmq-broker
```

或直接：

```bash
docker compose down -v   # 注意：会连 MySQL/Redis 卷一起删！
```

### 连接信息（给 Spring）

| 项 | 值 |
|----|-----|
| NameServer（应用配置） | `127.0.0.1:9876` |
| Broker 对外端口 | `10909` / `10911` / `10912`（一般只需配 NameServer） |

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: takeout-pay-producer
```

教学 Topic / Group 约定（见 P0 教程）：

| 项 | 建议值 |
|----|--------|
| Topic | `takeout-order-paid` |
| Tag | `ORDER_PAID` |
| Consumer Group | `takeout-kitchen-consumer` |

### 容器内排障（常用）

```bash
# 看 Broker 进程是否在跑
docker exec take-out-rocketmq-broker sh -c "ps aux | head"

# 进 Broker 容器
docker exec -it take-out-rocketmq-broker sh

# 确认 store 目录有数据（发过消息后应有 commitlog 等）
docker exec take-out-rocketmq-broker sh -c "ls -la /home/rocketmq/store"

# mqadmin：集群 / Topic（NameServer 用容器网络名）
docker exec take-out-rocketmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876
docker exec take-out-rocketmq-broker sh mqadmin topicList -n rocketmq-namesrv:9876
docker exec take-out-rocketmq-broker sh mqadmin topicRoute -n rocketmq-namesrv:9876 -t takeout-order-paid
docker exec take-out-rocketmq-broker sh mqadmin brokerStatus -n rocketmq-namesrv:9876 -b 127.0.0.1:10911
```

> `mqadmin` 随版本略有差异；连不上时查 NameServer、`brokerIP1`、端口与日志。

### 应用侧自检

```bash
Test-NetConnection 127.0.0.1 -Port 9876
docker compose logs --tail=100 rocketmq-broker
docker compose logs --tail=100 rocketmq-namesrv
```

付一笔成功后：Outbox `SENT` + 消费者日志；再 `docker compose restart rocketmq-broker`，未消费消息应仍在（有卷的前提下）。

### 换机器 / 要不要「导出 MQ」

| | MySQL | RocketMQ |
|--|-------|----------|
| 业务真相 | 是 | 否（真相在业务库 + Outbox） |
| 生产 | 备份/主从 | Broker 磁盘 + 多副本 / 云托管 |
| 换电脑学习 | Liquibase | 重新 up；未发送靠 Outbox；已在 MQ 未消费的本地消息通常不迁 |

云消息队列时：你不自己挂 Docker volume，**持久化由云厂商做**；你只配接入点。本地挂卷是为了养成和生产一致的「Broker 必须落盘」习惯。

---

## 新机器最小步骤

```bash
git pull
docker compose up -d
# MySQL：空卷只建库+用户；再跑 Liquibase / 启 admin
# Redis：直接用，无需导入
# RocketMQ：namesrv + broker Up 即可；Topic 可自动创建
docker exec -it take-out-redis redis-cli ping
docker compose ps
# 期望看到 take-out-mysql / redis / rocketmq-namesrv / rocketmq-broker
```
