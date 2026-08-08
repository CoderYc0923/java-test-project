# 支付中心模块 take-out-pay + Redis 完整教程

日期：2026-08-08（修订：独立 Maven 模块）  
适用项目：`take-out` 多模块 + 管理端「模拟下单 / 模拟支付」  
目标：新建 **`take-out-pay` 支付中心模块**（学习版网关），用 **真 Redis** 做防连点下单与防并发支付；供手敲与日后回顾。

> 本文档只作教程。请按 Checklist **自己创建模块与代码**，不要依赖仓库里预先生成的骨架。

---

## 0. 你将得到什么

| 能力 | 实现要点 |
|------|----------|
| 独立模块 `take-out-pay` | 支付网关职责与订单履约分离 |
| 防循环依赖 | pay **不依赖** system；通过 `OrderPayPort` 回写订单 |
| 防重复下单 | Redis `SET NX EX` + `requestId` |
| 防并发支付 | Redis 锁 + MySQL 条件 UPDATE（CAS） |
| 对接前端 | `POST /admin/order/mock`、`PUT /admin/order/mockPay/{id}` |

本课暂不强制验签回调体（见文末进阶）。

---

## 1. 概念（面试口述）

### 1.1 支付中心 / 支付网关

```text
订单（take-out-system）：快照下单、接单/派送履约
        ↓ 调用
支付中心（take-out-pay）：幂等、锁、模拟/真实支付
        ↓ 真项目再调
微信 / 支付宝 …
```

- **现在**：独立 **Maven 模块**，仍打进 admin **同一进程**（`scanBasePackages = com.sky.takeout`）  
- **以后**：可把 `take-out-pay` 再拆成独立 Spring Boot 服务部署  

### 1.2 为何不能 pay → system

若 `pay` 依赖 `system`（用 OrderMapper），而 `system` 又依赖 `pay`（调网关）→ **循环依赖**。  
解法：pay 只依赖抽象端口 `OrderPayPort`，**system 实现该接口**。

### 1.3 Redis 与 CAS

| 手段 | 作用 |
|------|------|
| Redis `SET NX` | 防连点下单 / 分布式锁 |
| DB `UPDATE WHERE status=待付款 AND pay=未支付` | CAS：最终以数据库为准 |
| 已支付再回调 | 幂等直接成功返回 |

---

## 2. 目标依赖方向

```text
take-out-admin
    → take-out-framework
        → take-out-system
            → take-out-pay      ← 新增
            → take-out-pojo
                → take-out-common

take-out-pay
    → take-out-pojo
    → take-out-common
    → spring-boot-starter-data-redis
    ✗ 禁止依赖 take-out-system
```

---

## 3. 模块目录（手敲时创建）

```text
take-out-pay/
├── pom.xml
└── src/main/java/com/sky/takeout/pay/
    ├── package-info.java
    ├── config/
    │   ├── PayProperties.java
    │   └── PayAutoConfiguration.java
    ├── redis/
    │   └── RedisIdempotentHelper.java
    ├── port/
    │   └── OrderPayPort.java              # 接口：查单 + CAS 更新
    └── gateway/
        └── MockPaymentGateway.java        # 支付入口

take-out-system/.../system/pay/
└── OrderPayPortImpl.java                  # 实现 Port，持有 OrderMapper
```

---

## 4. 工程接入步骤（POM）

### 4.1 父工程 `take-out/pom.xml`

`<modules>` 增加（建议放在 pojo 与 system 之间）：

```xml
        <module>take-out-pay</module>
```

`<dependencyManagement>` 增加：

```xml
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-pay</artifactId>
                <version>${take-out.version}</version>
            </dependency>
```

### 4.2 新建 `take-out-pay/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-pay</artifactId>
    <name>take-out-pay</name>
    <description>支付中心：幂等、锁、模拟支付；经 Port 回写订单</description>

    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-pojo</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 4.3 `take-out-system/pom.xml` 依赖 pay

```xml
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-pay</artifactId>
        </dependency>
```

然后：

```bash
mvn -pl take-out-pay,take-out-system -am install -DskipTests
```

IDE 对父工程 **Maven Reload**。

---

## 5. Docker Redis

`docker-compose.yml` 的 `services` 下增加：

```yaml
  redis:
    image: redis:7.2
    container_name: take-out-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - take-out-redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
```

`volumes:` 增加：`take-out-redis-data:`

```bash
docker compose up -d redis
docker exec -it take-out-redis redis-cli ping
```

---

## 6. 配置 `application.yml`

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      timeout: 3s

takeout:
  pay:
    mock-secret: takeout-mock-pay-secret-change-me
    order-idempotent-ttl-seconds: 300
    pay-lock-ttl-seconds: 10
```

admin 启动类已有 `scanBasePackages = "com.sky.takeout"`，会扫到 `com.sky.takeout.pay`。

---

## 7. 完整代码（带注释）

### 7.1 `OrderPayPort.java`

```java
package com.sky.takeout.pay.port;

import com.sky.takeout.pojo.entity.Order;

/**
 * 支付中心访问订单的端口（由 take-out-system 实现）。
 */
public interface OrderPayPort {

    Order findById(Long orderId);

    /**
     * CAS：仅待付款+未支付 → 待接单+已支付。
     * @return 影响行数，1=成功，0=状态已变
     */
    int casMarkPaid(Long orderId);
}
```

### 7.2 `PayProperties.java`

```java
package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "takeout.pay")
public class PayProperties {

    private String mockSecret = "takeout-mock-pay-secret-change-me";
    private long orderIdempotentTtlSeconds = 300;
    private long payLockTtlSeconds = 10;

    public String getMockSecret() { return mockSecret; }
    public void setMockSecret(String mockSecret) { this.mockSecret = mockSecret; }
    public long getOrderIdempotentTtlSeconds() { return orderIdempotentTtlSeconds; }
    public void setOrderIdempotentTtlSeconds(long v) { this.orderIdempotentTtlSeconds = v; }
    public long getPayLockTtlSeconds() { return payLockTtlSeconds; }
    public void setPayLockTtlSeconds(long v) { this.payLockTtlSeconds = v; }
}
```

### 7.3 `PayAutoConfiguration.java`

```java
package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayProperties.class)
public class PayAutoConfiguration {
}
```

### 7.4 `RedisIdempotentHelper.java`

```java
package com.sky.takeout.pay.redis;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisIdempotentHelper {

    private final StringRedisTemplate redis;

    public RedisIdempotentHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean trySetNx(String key, String value, long ttlSeconds) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    public void set(String key, String value, long ttlSeconds) {
        redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public void delete(String key) {
        redis.delete(key);
    }

    public String tryLock(String key, long ttlSeconds) {
        String token = UUID.randomUUID().toString();
        return trySetNx(key, token, ttlSeconds) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        String current = redis.opsForValue().get(key);
        if (token.equals(current)) {
            redis.delete(key);
        }
    }
}
```

### 7.5 `MockPaymentGateway.java`

```java
package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";

    private final OrderPayPort orderPayPort;
    private final RedisIdempotentHelper redisHelper;
    private final PayProperties payProperties;

    public MockPaymentGateway(OrderPayPort orderPayPort,
                              RedisIdempotentHelper redisHelper,
                              PayProperties payProperties) {
        this.orderPayPort = orderPayPort;
        this.redisHelper = redisHelper;
        this.payProperties = payProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order pay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单 id 不能为空");
        }

        Order order = requireOrder(orderId);
        if (isPaid(order)) {
            log.info("支付幂等命中 orderId={}", orderId);
            return order;
        }

        String lockKey = PAY_LOCK_PREFIX + orderId;
        String lockToken = redisHelper.tryLock(lockKey, payProperties.getPayLockTtlSeconds());
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请勿重复点击");
        }

        try {
            order = requireOrder(orderId);
            if (isPaid(order)) {
                return order;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            int rows = orderPayPort.casMarkPaid(orderId);
            if (rows == 0) {
                Order latest = requireOrder(orderId);
                if (isPaid(latest)) {
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }
            return requireOrder(orderId);
        } finally {
            redisHelper.unlock(lockKey, lockToken);
        }
    }

    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.TO_BE_CONFIRMED
                && order.getPayStatus() == PayStatus.PAID;
    }

    private Order requireOrder(Long id) {
        Order order = orderPayPort.findById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }
}
```

### 7.6 system：`OrderPayPortImpl.java`

```java
package com.sky.takeout.system.pay;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;
import com.sky.takeout.system.mapper.OrderMapper;

@Component
public class OrderPayPortImpl implements OrderPayPort {

    private final OrderMapper orderMapper;

    public OrderPayPortImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Order findById(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    @Override
    public int casMarkPaid(Long orderId) {
        Order patch = new Order();
        patch.setStatus(OrderStatus.TO_BE_CONFIRMED);
        patch.setPayStatus(PayStatus.PAID);
        patch.setCheckoutTime(LocalDateTime.now());
        return orderMapper.update(patch, new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT)
                .eq(Order::getPayStatus, PayStatus.UNPAID));
    }
}
```

---

## 8. 订单侧改造（system）

### 8.1 `OrderMockDTO` 增加

```java
@Schema(description = "客户端幂等键，建议 UUID")
private String requestId;
```

### 8.2 `OrderServiceImpl`

注入：`MockPaymentGateway`、`RedisIdempotentHelper`、`PayProperties`。

- 现有 `mock` 建单逻辑 → 抽成 `doMockCreate`
- `mock` 外壳：有 `requestId` 时用 Redis 幂等（key 前缀建议 `order:idempotent:`）
- `mockPay(id)`：

```java
@Override
public OrderMockVO mockPay(Long id) {
    return toMockVO(mockPaymentGateway.pay(id));
}
```

幂等下单逻辑与原先教程相同：`trySetNx(PROCESSING)` → 成功后 `set(orderId)`；失败 `delete`。

### 8.3 Controller

```java
@PutMapping("/mockPay/{id}")
public Result<OrderMockVO> mockPay(@PathVariable Long id) {
    return Result.success(orderService.mockPay(id));
}
```

---

## 9. 手敲 Checklist

- [ ] Docker 起 Redis，`PONG`
- [ ] 父 POM 注册 `take-out-pay` + dependencyManagement
- [ ] 新建 `take-out-pay/pom.xml` 与包内类
- [ ] system 依赖 pay，实现 `OrderPayPortImpl`
- [ ] yml 配 redis + `takeout.pay`
- [ ] `OrderMockDTO.requestId` + `mock` 幂等 + `mockPay` 委托网关
- [ ] Controller `PUT /mockPay/{id}`
- [ ] `mvn install`，联调前端模拟下单 → 模拟支付

---

## 10. 验收

| # | 操作 | 期望 |
|---|------|------|
| 1 | 同 `requestId` 连点下单 | 仅一笔待付款 |
| 2 | 模拟支付成功 | 待接单 + 已支付 |
| 3 | 再点支付 | 幂等成功 |
| 4 | `KEYS order:*` | 可见幂等键 / 锁键（锁通常已释放） |

---

## 11. 进阶

- 回调体 + HMAC 验签 + 核金额 + nonce 去重  
- Lua 解锁  
- 将来 `take-out-pay` 独立 Boot 启动类 + HTTP，system 改远程调用  

---

## 12. 相关路径

| 资源 | 路径 |
|------|------|
| 本教程 | `docs/tutorials/2026-08-08-pay-package-redis.md` |
| 工程手册 | `README.md` §3 目录总览 |
| 订单需求 | `docs/requirements/2026-08-07-order-module.md` |
| 模拟支付页 | `project-rjwm-admin-vue-ts/src/views/orderDetails/mockPay.vue` |
