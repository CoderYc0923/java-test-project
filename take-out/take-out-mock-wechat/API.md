# 假微信 V3 沙箱 — API 文档

| 项 | 说明 |
|----|------|
| 产品 | take-out-mock-wechat（教学用模拟微信支付） |
| 文档版本 | 1.0.0 |
| 更新日期 | 2026-08-10 |
| Base URL | `http://127.0.0.1:9090`（默认） |
| 数据格式 | JSON，`Content-Type: application/json` |
| 字符编码 | UTF-8 |

> **说明：** 路径与字段名**形似**微信支付 APIv3，签名为教学用 HMAC，**不是**官方 RSA/平台证书方案。仅供本地联调。

相关文档：[README.md](./README.md)（启动与冒烟）

---

## 1. 接入须知

### 1.1 调用方角色

| 角色 | 说明 |
|------|------|
| 商户系统 | 外卖 `take-out-admin` / 未来的支付 Client：调用「统一下单」「查单」 |
| 渠道沙箱 | 本服务：收单、存交易、在「确认支付」后回调商户 |
| 操作员 | 用 Postman 调「确认支付」（模拟用户付款成功） |

### 1.2 鉴权

本期**不校验**微信风格的 `Authorization` 头。生产微信 V3 需商户私钥签名；本沙箱跳过，便于教学。

支付结果通知使用共享密钥 HMAC，见 [§5 支付结果通知](#5-支付结果通知商户回调)。

### 1.3 金额约定

- 单位：**元**（`BigDecimal`），与现有外卖 Mock 回调一致。  
- 签名时使用 `amount.toPlainString()`（例如 `10.00` 与 `10.0` 明文不同，须与商户订单金额一致）。

### 1.4 交易状态 `trade_state`

| 值 | 含义 |
|----|------|
| `NOTPAY` | 未支付 |
| `SUCCESS` | 支付成功 |

内存存储，进程重启后交易清空。

### 1.5 统一错误体

业务错误时响应形如：

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "out_trade_no not found"
}
```

| HTTP | 典型 code | 场景 |
|------|-----------|------|
| 400 | `NOTIFY_URL_BLANK` | confirm 时 notify_url 为空 |
| 404 | `ORDER_NOT_FOUND` | 查单 / confirm 订单不存在 |
| 409 | `ORDER_PAID` | 已支付订单再次统一下单 |
| 400 | （校验失败） | 缺必填字段时可能为框架默认校验响应 |

---

## 2. 统一下单（Native）

模拟：商户向微信发起 Native/扫码类下单，获取 `prepay_id`。

### 基本信息

| 项 | 值 |
|----|-----|
| Method | `POST` |
| Path | `/v3/pay/transactions/native` |
| 成功状态码 | `200` |

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `out_trade_no` | string | 是 | 商户订单号，对应外卖 `orders.number` |
| `description` | string | 是 | 商品描述 |
| `notify_url` | string | 是 | 支付成功后本服务 POST 回调的完整 URL |
| `amount` | number | 是 | 订单金额，**元** |

请求示例：

```http
POST /v3/pay/transactions/native HTTP/1.1
Host: 127.0.0.1:9090
Content-Type: application/json

{
  "out_trade_no": "ORD20260810120001",
  "description": "外卖订单",
  "notify_url": "http://127.0.0.1:8080/admin/order/mockPay/notify",
  "amount": 62.00
}
```

### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `out_trade_no` | string | 商户订单号 |
| `prepay_id` | string | 预支付会话标识，形如 `wx_prepay_{uuid}` |
| `description` | string | 描述 |
| `amount` | number | 金额（元） |
| `currency` | string | 固定 `CNY` |
| `trade_state` | string | 新建为 `NOTPAY` |

成功响应示例：

```json
{
  "out_trade_no": "ORD20260810120001",
  "prepay_id": "wx_prepay_a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "外卖订单",
  "amount": 62.00,
  "currency": "CNY",
  "trade_state": "NOTPAY"
}
```

### 幂等与冲突

| 场景 | 行为 |
|------|------|
| 同一 `out_trade_no` 且仍为 `NOTPAY` | **幂等**：返回原交易（含原 `prepay_id`），HTTP 200 |
| 同一 `out_trade_no` 且已为 `SUCCESS` | HTTP **409**，`code=ORDER_PAID` |

---

## 3. 查询订单

模拟：按商户订单号查询微信支付订单。

### 基本信息

| 项 | 值 |
|----|-----|
| Method | `GET` |
| Path | `/v3/pay/transactions/out-trade-no/{out_trade_no}` |
| 成功状态码 | `200` |

### 路径参数

| 参数 | 说明 |
|------|------|
| `out_trade_no` | 商户订单号 |

### 响应参数

与统一下单成功体相同（`out_trade_no`、`prepay_id`、`description`、`amount`、`currency`、`trade_state`）。

不存在时：HTTP **404**，`code=ORDER_NOT_FOUND`。

请求示例：

```http
GET /v3/pay/transactions/out-trade-no/ORD20260810120001 HTTP/1.1
Host: 127.0.0.1:9090
```

---

## 4. 确认支付（教学接口）

> **非微信官方 API。** 用于人工模拟「用户已付款」，触发本服务向商户 `notify_url` 发送结果通知。

### 基本信息

| 项 | 值 |
|----|-----|
| Method | `POST` |
| Path | `/mock/pay/confirm` |
| 成功状态码 | `200` |

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `out_trade_no` | string | 是 | 商户订单号 |

请求示例：

```http
POST /mock/pay/confirm HTTP/1.1
Host: 127.0.0.1:9090
Content-Type: application/json

{
  "out_trade_no": "ORD20260810120001"
}
```

### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `out_trade_no` | string | 商户订单号 |
| `trade_state` | string | 成功处理后为 `SUCCESS` |

### 行为说明

1. 订单不存在 → **404** `ORDER_NOT_FOUND`  
2. 已是 `SUCCESS` → **幂等成功**，**不再**向商户发送通知  
3. `NOTPAY` → 置为 `SUCCESS`，再对下单时的 `notify_url` 发起 HTTP POST（见 §5）  
4. `notify_url` 为空 → **400** `NOTIFY_URL_BLANK`  
5. 回调失败（非 2xx / 网络错误）：渠道侧仍保持 `SUCCESS`（简化对账）；按配置重试后记日志  

---

## 5. 支付结果通知（商户回调）

本服务在确认支付成功后，作为「渠道」主动请求商户。

### 基本信息

| 项 | 值 |
|----|-----|
| Method | `POST` |
| URL | 统一下单时传入的 `notify_url` |
| Content-Type | `application/json` |
| 成功判定 | 商户返回 **HTTP 2xx** |

对接外卖管理端时，`notify_url` 一般为：

```text
http://127.0.0.1:8080/admin/order/mockPay/notify
```

（须与外卖侧白名单、验签配置一致。）

### 通知报文（Body）

为兼容现有 `MockPayNotifyDTO`，本期为**扁平五字段**（非官方 V3 加密 `resource` 包）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `orderNumber` | string | 商户订单号（= `out_trade_no`） |
| `amount` | number | 实付金额（元），须与商户订单一致 |
| `timestamp` | number | Unix **秒**级时间戳 |
| `nonce` | string | 随机串，防重放 |
| `sign` | string | HMAC-SHA256 签名（小写十六进制） |

示例：

```json
{
  "orderNumber": "ORD20260810120001",
  "amount": 62.00,
  "timestamp": 1723280000,
  "nonce": "a1b2c3d4e5f6...",
  "sign": "abcdef0123456789..."
}
```

### 签名算法

与 `take-out-pay` 的 `HmacPaySignUtil` 一致。

1. 拼接待签名字符串（字段顺序固定）：

```text
amount={amount.toPlainString()}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}
```

2. 使用密钥对明文做 **HMAC-SHA256**，输出 **hex**（小写）。  
3. 密钥：本服务 `mock-wechat.merchant-notify-secret`，必须与外卖 `pay.mock-secret` **相同**。

### 重试策略

| 配置项 | 默认 | 含义 |
|--------|------|------|
| `mock-wechat.notify-max-retries` | `2` | **额外**重试次数（不含首次）；总尝试 = `1 + notify-max-retries` |
| `mock-wechat.notify-retry-delay-ms` | `500` | 重试间隔（毫秒） |

出站 HTTP：连接超时约 3s，读超时约 5s。

---

## 6. 推荐联调时序

```text
商户                         假微信 :9090                      商户 notify
  |---- POST /v3/.../native ---->|                                |
  |<--- prepay_id, NOTPAY -------|                                |
  |---- GET  .../out-trade-no -->|                                |
  |                              |                                |
  |  (Postman) POST /mock/pay/confirm                             |
  |----------------------------->|                                |
  |                              |---- POST notify_url (HMAC) --->|
  |                              |<--- 2xx -----------------------|
  |---- GET  .../out-trade-no -->|                                |
  |<--- trade_state=SUCCESS -----|                                |
```

---

## 7. 接口一览

| 分类 | Method | Path | 说明 |
|------|--------|------|------|
| 渠道 API | POST | `/v3/pay/transactions/native` | 统一下单 |
| 渠道 API | GET | `/v3/pay/transactions/out-trade-no/{out_trade_no}` | 查单 |
| 教学 API | POST | `/mock/pay/confirm` | 确认支付并回调 |
| 出站 | POST | `{notify_url}` | 支付结果通知（本服务发起） |

---

## 8. 与真实微信 V3 的差异（摘要）

| 项 | 本沙箱 | 真实微信 V3 |
|----|--------|-------------|
| 鉴权 | 无 | 商户私钥 RSA + Authorization 头 |
| 金额 | 元 + BigDecimal | 多为「分」整数 `amount.total` |
| 回调 | 扁平五字段 + HMAC | 通知验签 + 解密 `resource` |
| 用户支付 | `/mock/pay/confirm` 手动 | 用户在微信侧完成支付 |
| 部署 | 本地 9090 | 微信云 |

商户侧后续改造建议：将原进程内 Mock Client 改为对本 Base URL 发起 §2 / §3；用户「去支付」改为调用 §4 或引导操作员 confirm。

手敲改造教程（完整代码 + 注解）：  
[`docs/tutorials/2026-08-10-refactor-pay-http-mock-wechat.md`](../docs/tutorials/2026-08-10-refactor-pay-http-mock-wechat.md)
