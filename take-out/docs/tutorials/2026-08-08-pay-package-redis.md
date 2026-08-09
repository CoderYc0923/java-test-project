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

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 幂等 / 锁 小工具。
 *
 * 支付中心里主要做两件事：
 * 1. SET NX + TTL：占坑（幂等键、分布式锁）
 * 2. 按 token 解锁：只删自己加的锁，避免误删别人的锁
 *
 * 不关心业务字段，只操作 String key/value。
 */
@Component
public class RedisIdempotentHelper {

    private final StringRedisTemplate redis;

    public RedisIdempotentHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * SET key value NX + 过期时间。
     * 只有 key 不存在时才写入；成功 true，已被占用 false。
     *
     * 用途：幂等占坑、抢锁底层实现。
     * opsForValue：操作 Redis 字符串类型。
     * setIfAbsent：对应 SET NX。
     */
    public boolean trySetNx(String key, String value, Long ttlSeconds) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
        // setIfAbsent 可能返回 null，只认 TRUE
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 覆盖写入，并设置过期时间（秒）。
     * 使用 Duration 重载（Spring Data Redis 4.1 起，TimeUnit 那个 set 已弃用）。
     */
    public void set(String key, String value, Long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    /** 读；不存在返回 null */
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    /** 删 key */
    public void delete(String key) {
        redis.delete(key);
    }

    /**
     * 尝试加锁。
     *
     * @return 拿到锁返回随机 token（解锁要用）；没拿到返回 null
     *
     * token 作用：锁过期后别人可能又加了锁；
     * 解锁时对比 value，避免误删别人的新锁。
     */
    public String tryLock(String key, Long ttlSeconds) {
        String token = UUID.randomUUID().toString();
        return trySetNx(key, token, ttlSeconds) ? token : null;
    }

    /**
     * 释放锁：仅当当前 value == 自己的 token 时才删。
     * （学习版；生产更稳妥会用 Lua 保证 get+del 原子）
     */
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

/**
 * 模拟支付网关（学习用「支付中心」入口）。
 *
 * 职责：
 * 1. 校验订单是否可支付
 * 2. 用 Redis 锁防同一订单并发重复点支付
 * 3. 通过 OrderPayPort 做 DB CAS：待付款+未支付 → 待接单+已支付
 *
 * 注意：本类不写 SQL、不直接依赖 Mapper；改库只走端口（system 实现）。
 */
@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    /** Redis 锁 key 前缀，完整 key = order:pay:lock:{orderId} */
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

    /**
     * 模拟支付成功。
     *
     * 流程概览：
     * 查单 → 已付则直接返回（业务幂等）
     *      → 抢 Redis 锁（防连点）
     *      → 锁内再查一次 + 状态校验
     *      → CAS 更新支付状态
     *      → finally 释放锁
     */
    @Transactional(rollbackFor = Exception.class)
    public Order pay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单 id 不能为空");
        }

        // 1) 第一次查库：已支付就直接返回，少打 Redis
        Order order = requireOrder(orderId);
        if (isPaid(order)) {
            log.info("支付幂等命中（已支付）orderId={}", orderId);
            return order;
        }

        // 2) 抢支付锁：同一订单同一时刻只允许一个线程进「真正改库」
        String lockKey = PAY_LOCK_PREFIX + orderId;
        Long ttl = payProperties.getPayLockTtlSeconds();
        if (ttl == null || ttl <= 0) {
            ttl = 10L; // yml 没配时兜底，避免 NPE
        }
        String lockToken = redisHelper.tryLock(lockKey, ttl);
        if (lockToken == null) {
            // 别人正在付：告诉前端别连点（429）
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请勿重复点击");
        }

        try {
            // 3) 锁内再查一次：拿到锁前可能已被别人付成功
            order = requireOrder(orderId);
            if (isPaid(order)) {
                return order;
            }

            // 4) 状态机：只有「待付款 + 未支付」才能付
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            // 5) CAS 改库：UPDATE ... WHERE status=待付款 AND pay_status=未支付
            //    rows=1 成功；rows=0 说明并发下状态已变
            int rows = orderPayPort.casMarkPaid(orderId);
            if (rows == 0) {
                Order latest = requireOrder(orderId);
                if (isPaid(latest)) {
                    // 别人抢先付成功了 → 对本请求也算成功（幂等）
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }

            // 6) 返回最新订单（待接单 + 已支付）
            return requireOrder(orderId);
        } finally {
            // 7) 无论成功失败都放锁；用 token 防止误删别人的锁
            redisHelper.unlock(lockKey, lockToken);
        }
    }

    /** 业务上的「已支付」：待接单 + 已支付 */
    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.TO_BE_CONFIRMED
                && order.getPayStatus() == PayStatus.PAID;
    }

    /** 查单，不存在直接业务异常 */
    private Order requireOrder(Long id) {
        Order order = orderPayPort.findOrderById(id);
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

```java
package com.sky.takeout.system.service.impl;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.gateway.MockPaymentGateway;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.order.OrderCancelDTO;
import com.sky.takeout.pojo.dto.order.OrderConfirmDTO;
import com.sky.takeout.pojo.dto.order.OrderMockDTO;
import com.sky.takeout.pojo.dto.order.OrderMockItemDTO;
import com.sky.takeout.pojo.dto.order.OrderQueryDTO;
import com.sky.takeout.pojo.dto.order.OrderRejectionDTO;
import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.entity.OrderDetail;
import com.sky.takeout.pojo.entity.Setmeal;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;
import com.sky.takeout.pojo.enums.SaleStatus;
import com.sky.takeout.pojo.vo.order.OrderDetailVO;
import com.sky.takeout.pojo.vo.order.OrderMockVO;
import com.sky.takeout.pojo.vo.order.OrderStatisticsVO;
import com.sky.takeout.pojo.vo.order.OrderVO;
import com.sky.takeout.system.mapper.DishMapper;
import com.sky.takeout.system.mapper.OrderDetailMapper;
import com.sky.takeout.system.mapper.OrderMapper;
import com.sky.takeout.system.mapper.SetmealMapper;
import com.sky.takeout.system.service.OrderService;

import lombok.Data;

@Data
class MockUser {
    private Long id = 1001L;
    private Long addressBookId = 1L;
    private String name = "张三";
    private String phone = "13800138000";
    private String address = "北京市海淀区";
    private String consignee = "张三";
    private String consigneePhone = "13800138000";
    private String consigneeAddress = "北京市海淀区";
    private String consigneeEmail = "zhangsan@example.com";
    private String consigneePassword = "123456";
}

@Service
public class OrderServiceImpl implements OrderService {

    /** 防连点下单：order:idempotent:{requestId} */
    private static final String IDEMPOTENT_KEY_PREFIX = "order:idempotent:";
    /** 占坑中：同 requestId 正在建单 */
    private static final String PROCESSING = "PROCESSING";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final MockUser MOCK_USER = new MockUser();

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;
    private final MockPaymentGateway mockPaymentGateway;
    private final RedisIdempotentHelper redisIdempotentHelper;
    private final PayProperties payProperties;

    public OrderServiceImpl(OrderMapper orderMapper,
                            OrderDetailMapper orderDetailMapper,
                            DishMapper dishMapper,
                            SetmealMapper setmealMapper,
                            MockPaymentGateway mockPaymentGateway,
                            RedisIdempotentHelper redisIdempotentHelper,
                            PayProperties payProperties) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
        this.mockPaymentGateway = mockPaymentGateway;
        this.redisIdempotentHelper = redisIdempotentHelper;
        this.payProperties = payProperties;
    }

    @Override
    public IPage<OrderVO> page(OrderQueryDTO queryDTO) {
        Integer pageNum = queryDTO.getPage() == null ? 1 : queryDTO.getPage();
        Integer pageSize = queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();
        Page<Order> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(queryDTO.getNumber()), Order::getNumber, queryDTO.getNumber());
        queryWrapper.like(StringUtils.hasText(queryDTO.getPhone()), Order::getPhone, queryDTO.getPhone());

        LocalDateTime beginTime = parseDateTime(queryDTO.getBeginTime());
        LocalDateTime endTime = parseDateTime(queryDTO.getEndTime());
        queryWrapper.ge(beginTime != null, Order::getOrderTime, beginTime);
        queryWrapper.le(endTime != null, Order::getOrderTime, endTime);

        Integer statusCode = queryDTO.getStatus();
        if (statusCode != null && statusCode != 0) {
            queryWrapper.eq(Order::getStatus, OrderStatus.fromCode(statusCode));
        }
        queryWrapper.orderByDesc(Order::getOrderTime);

        IPage<Order> orderPage = orderMapper.selectPage(page, queryWrapper);
        List<Order> records = orderPage.getRecords();
        if (records.isEmpty()) {
            return orderPage.convert(order -> toVO(order, null, false));
        }

        List<Long> orderIds = records.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderDetail>> detailMap = getDetailMap(orderIds);
        return orderPage.convert(order -> toVO(order, detailMap.get(order.getId()), false));
    }

    @Override
    public OrderVO getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        Map<Long, List<OrderDetail>> detailMap = getDetailMap(List.of(order.getId()));
        return toVO(order, detailMap.get(order.getId()), true);
    }

    @Override
    public OrderStatisticsVO statistics() {
        Long toBeConfirmed = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.TO_BE_CONFIRMED));
        Long confirmed = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.CONFIRMED));
        Long deliveryInProgress = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.DELIVERY_IN_PROGRESS));

        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(toBeConfirmed == null ? 0 : toBeConfirmed.intValue());
        vo.setConfirmed(confirmed == null ? 0 : confirmed.intValue());
        vo.setDeliveryInProgress(deliveryInProgress == null ? 0 : deliveryInProgress.intValue());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(OrderConfirmDTO confirmDTO) {
        Order order = getOrder(confirmDTO.getId());
        assertOrderStatus(order, OrderStatus.TO_BE_CONFIRMED, "只有待接单订单才能接单");
        order.setStatus(OrderStatus.CONFIRMED);
        orderMapper.updateById(order);
        log.info("订单{}状态改为已接单", order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrderRejectionDTO rejectionDTO) {
        Order order = getOrder(rejectionDTO.getId());
        assertOrderStatus(order, OrderStatus.TO_BE_CONFIRMED, "只有待接单订单才能拒单");
        order.setStatus(OrderStatus.CANCELLED);
        order.setRejectionReason(rejectionDTO.getRejectionReason().trim());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单{}已拒单", order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delivery(Long id) {
        Order order = getOrder(id);
        assertOrderStatus(order, OrderStatus.CONFIRMED, "只有待派送订单才能派送");
        order.setStatus(OrderStatus.DELIVERY_IN_PROGRESS);
        orderMapper.updateById(order);
        log.info("订单{}状态改为派送中", order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        Order order = getOrder(id);
        assertOrderStatus(order, OrderStatus.DELIVERY_IN_PROGRESS, "只有派送中订单才能完成");
        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单{}状态改为已完成", order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrderCancelDTO cancelDTO) {
        Order order = getOrder(cancelDTO.getId());
        OrderStatus currentStatus = order.getStatus();
        List<OrderStatus> cancelableStatuses = List.of(
                OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED, OrderStatus.DELIVERY_IN_PROGRESS);
        if (!cancelableStatuses.contains(currentStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "当前订单状态不允许取消：" + (currentStatus == null ? "null" : currentStatus.getMessage()));
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelDTO.getCancelReason().trim());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单{}状态改为已取消", order.getId());
    }

    /**
     * 模拟下单（强制 requestId + Redis 幂等）。
     *
     * trySetNx(PROCESSING) → doMockCreate → set(orderId)
     * 失败 delete，允许同 requestId 重试
     * 重复请求：PROCESSING=处理中；已是 orderId=返回原单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderMockVO mock(OrderMockDTO mockDTO) {
        // 强制幂等键：没有就不建单
        if (!StringUtils.hasText(mockDTO.getRequestId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestId 不能为空");
        }

        String requestId = mockDTO.getRequestId().trim();
        String key = IDEMPOTENT_KEY_PREFIX + requestId;

        Long ttl = payProperties.getOrderIdempotentTtlSeconds();
        if (ttl == null || ttl <= 0) {
            ttl = 300L;
        }

        boolean first = redisIdempotentHelper.trySetNx(key, PROCESSING, ttl);
        if (!first) {
            String cached = redisIdempotentHelper.get(key);
            if (PROCESSING.equals(cached)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "下单处理中，请勿重复点击");
            }
            if (StringUtils.hasText(cached)) {
                Order order = orderMapper.selectById(Long.valueOf(cached));
                if (order != null) {
                    log.info("下单幂等命中 requestId={}, orderId={}", requestId, order.getId());
                    return toMockVO(order);
                }
            }
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "重复提交");
        }

        try {
            OrderMockVO vo = doMockCreate(mockDTO);
            redisIdempotentHelper.set(key, String.valueOf(vo.getId()), ttl);
            return vo;
        } catch (RuntimeException ex) {
            redisIdempotentHelper.delete(key);
            throw ex;
        }
    }

    /** 真正建单：待付款 + 未支付 */
    private OrderMockVO doMockCreate(OrderMockDTO mockDTO) {
        List<OrderDetail> orderDetails = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderMockItemDTO item : mockDTO.getItems()) {
            boolean isDish = item.getDishId() != null;
            boolean isSetmeal = item.getSetmealId() != null;
            if (isDish == isSetmeal) {
                throw new BusinessException(ErrorCode.CONFLICT, "菜品和套餐只能选择一个");
            }

            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setNumber(item.getNumber());

            if (isDish) {
                Dish dish = dishMapper.selectById(item.getDishId());
                if (dish == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, "菜品不存在");
                }
                if (dish.getStatus() != SaleStatus.ENABLE) {
                    throw new BusinessException(ErrorCode.CONFLICT, "菜品已下架");
                }
                orderDetail.setDishId(dish.getId());
                orderDetail.setName(dish.getName());
                orderDetail.setImage(dish.getImage());
                orderDetail.setAmount(dish.getPrice());
                orderDetail.setDishFlavor(
                        StringUtils.hasText(item.getDishFlavor()) ? item.getDishFlavor().trim() : null);
            } else if (isSetmeal) {
                Setmeal setmeal = setmealMapper.selectById(item.getSetmealId());
                if (setmeal == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, "套餐不存在");
                }
                if (setmeal.getStatus() != SaleStatus.ENABLE) {
                    throw new BusinessException(ErrorCode.CONFLICT, "套餐已下架");
                }
                orderDetail.setSetmealId(setmeal.getId());
                orderDetail.setName(setmeal.getName());
                orderDetail.setImage(setmeal.getImage());
                orderDetail.setAmount(setmeal.getPrice());
            }

            BigDecimal mount = orderDetail.getAmount().multiply(BigDecimal.valueOf(orderDetail.getNumber()));
            totalAmount = totalAmount.add(mount);
            orderDetails.add(orderDetail);
        }

        int packAmount = 0;
        BigDecimal orderAmount = totalAmount.add(BigDecimal.valueOf(packAmount));
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPayStatus(PayStatus.UNPAID);
        order.setCheckoutTime(null);

        order.setUserId(MOCK_USER.getId());
        order.setAddressBookId(MOCK_USER.getAddressBookId());
        order.setPhone(MOCK_USER.getPhone());
        order.setAddress(MOCK_USER.getAddress());
        order.setUserName(MOCK_USER.getName());
        order.setConsignee(MOCK_USER.getConsignee());

        order.setOrderTime(now);
        order.setPayMethod(1);
        order.setAmount(orderAmount);
        order.setRemark(mockDTO.getRemark());
        order.setEstimatedDeliveryTime(now.plusMinutes(45));
        order.setDeliveryStatus(1);
        order.setPackAmount(packAmount);
        order.setTablewareNumber(1);
        order.setTablewareStatus(1);

        orderMapper.insert(order);
        for (OrderDetail orderDetail : orderDetails) {
            orderDetail.setOrderId(order.getId());
            orderDetailMapper.insert(orderDetail);
        }

        log.info("订单{}下单成功", order.getId());
        return toMockVO(order);
    }

    @Override
    public OrderMockVO mockPay(Long id) {
        return toMockVO(mockPaymentGateway.pay(id));
    }

    private String generateOrderNumber() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "ORD" + time + rnd;
    }

    private OrderMockVO toMockVO(Order order) {
        OrderMockVO vo = new OrderMockVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    private Order getOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }

    private void assertOrderStatus(Order order, OrderStatus expectedStatus, String message) {
        if (order.getStatus() != expectedStatus) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    private Map<Long, List<OrderDetail>> getDetailMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return null;
        }
        List<OrderDetail> allDetails = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().in(OrderDetail::getOrderId, orderIds));
        return allDetails.stream().collect(Collectors.groupingBy(OrderDetail::getOrderId));
    }

    private OrderVO toVO(Order order, List<OrderDetail> details, Boolean withDetails) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setOrderDishes(buildOrderDishes(details));
        if (withDetails) {
            vo.setOrderDetailList(details.stream().map(d -> {
                OrderDetailVO detailVO = new OrderDetailVO();
                BeanUtils.copyProperties(d, detailVO);
                return detailVO;
            }).collect(Collectors.toList()));
        }
        return vo;
    }

    private String buildOrderDishes(List<OrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        return details.stream().map(d -> d.getName() + "*" + d.getNumber()).collect(Collectors.joining(";"));
    }

    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.ERROR, "时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
```



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

#### 11.1 Lua 解锁  

# Lua 解锁完整教程（手敲）

只改 `RedisIdempotentHelper.unlock`；`MockPaymentGateway` / `tryLock` 不用动。

------

## 1. 为啥要改

现在：

```t
GET key  →  看是不是我的 token  →  DEL key
```

中间有缝：

```tex
你：GET 发现还是自己的 token
        （此时锁 TTL 刚好过期）
别人：SET NX 抢到同一把锁（新 token）
你：DEL  →  误删了别人的锁
```



Lua 在 Redis 里一条脚本跑完，中间不会插别的命令。

------

## 2. Lua 脚本本身

```java
-- KEYS[1] = 锁的 key
-- ARGV[1] = 加锁时留下的 token
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])   -- 1=删成功
else
  return 0                            -- 不是自己的锁 / key 已没了
end
```



------

## 3. 完整 `RedisIdempotentHelper.java`（带注释）

直接整文件覆盖手打：

```java
package com.sky.takeout.pay.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 幂等 / 锁 小工具。
 *
 * 1. SET NX + TTL：占坑、加锁
 * 2. 解锁：Lua 原子「相等才删」，避免误删别人的锁
 */
@Component
public class RedisIdempotentHelper {

    /**
     * 解锁脚本（类加载时初始化一次即可）。
     * 返回 Long：1=删了自己的锁，0=没删（token 不匹配或 key 不存在）。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        // 注意：字符串里的空格/换行不影响 Lua；写成一行也行
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) "
                        + "else return 0 end"
        );
    }

    private final StringRedisTemplate redis;

    public RedisIdempotentHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * SET key value NX + 过期时间。
     * 只有 key 不存在才写入；成功 true，已被占用 false。
     */
    public boolean trySetNx(String key, String value, Long ttlSeconds) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    /** 覆盖写入 + TTL（秒）。用 Duration，避免 Spring Data Redis 4.1 弃用的 TimeUnit 重载。 */
    public void set(String key, String value, Long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public void delete(String key) {
        redis.delete(key);
    }

    /**
     * 加锁：value 用随机 token，解锁时要带上。
     * @return token；抢不到返回 null
     */
    public String tryLock(String key, Long ttlSeconds) {
        String token = UUID.randomUUID().toString();
        return trySetNx(key, token, ttlSeconds) ? token : null;
    }

    /**
     * Lua 原子解锁：仅当当前 value == token 时才 DEL。
     *
     * 对比旧写法：
     *   get → equals → delete   （两步，有竞态）
     * 现在：
     *   EVAL 脚本一次完成 get+比较+del（原子）
     */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        // KEYS 列表、ARGV 列表（这里 ARGV 只有 token）
        List<String> keys = Collections.singletonList(key);
        Long result = redis.execute(UNLOCK_SCRIPT, keys, token);
        // result == 1 删成功；0 表示不是自己的锁（可打日志，一般可忽略）
        // if (result != null && result == 1L) { ... }
    }
}
```



------

## 4. 编译重启

mvn -pl take-out-pay -am install -DskipTests

*# 再重启 take-out-admin*

`MockPaymentGateway` 仍是 `tryLock` / `unlock`，不用改调用方。

------

## 5. 怎么自己验一下（可选）

docker exec -it take-out-redis redis-cli

*# 模拟一把锁*

SET order:pay:lock:999 my-token EX 30

*# 在 redis-cli 里直接跑脚本（和 Java 同一逻辑）*

EVAL "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 order:pay:lock:999 wrong-token

*# 应返回 0，key 还在*

EVAL "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 order:pay:lock:999 my-token

*# 应返回 1，key 被删*

业务上：对未支付订单连点 `mockPay`，仍应只成功一次。

------

## 6. 面试一句话

加锁：`SET key token NX EX ttl`；解锁：Lua 判断 value 等于自己的 token 再删，保证不误删他人锁。

#### 11.2 回调体 + HMAC 验签 + 核金额 + nonce 去重

[take-out/docs/tutorials/2026-08-09-mock-wechat-pay-notify.md](vscode-file://vscode-app/d:/cursor/resources/app/out/vs/code/electron-sandbox/workbench/take-out/docs/tutorials/2026-08-09-mock-wechat-pay-notify.md)

#### 11.3 将来 `take-out-pay` 独立 Boot 启动类 + HTTP，system 改远程调用  

---

## 12. 相关路径

| 资源 | 路径 |
|------|------|
| 本教程 | `docs/tutorials/2026-08-08-pay-package-redis.md` |
| 工程手册 | `README.md` §3 目录总览 |
| 订单需求 | `docs/requirements/2026-08-07-order-module.md` |
| 模拟支付页 | `project-rjwm-admin-vue-ts/src/views/orderDetails/mockPay.vue` |
