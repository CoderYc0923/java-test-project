# 假微信 V3 沙箱服务设计（take-out-mock-wechat）

日期：2026-08-10  
状态：已确认（brainstorming）  
范围：**仅新建**同仓模块 `take-out-mock-wechat`；**不改** `take-out-pay` / 订单业务 / 前端（由作者后续自行改造 Client）。

---

## 1. 目标与非目标

### 目标

- 独立 Spring Boot 进程，模拟微信支付 **V3 形似** API。
- 本期能力闭环：
  1. 统一下单（生成 `prepay_id`，状态 `NOTPAY`）
  2. 按商户订单号查单
  3. **手动**确认支付（教学接口，非微信官方）
  4. 确认后 **HTTP POST** 商户 `notify_url`（真实远程回调，而非进程内调用）
- 签名采用现有教学方案 **HMAC-SHA256**（与 `HmacPaySignUtil` 算法一致），字段路径形似 V3，不做商户 RSA / 平台证书。

### 非目标

- 修改 `take-out-pay`、`OrderServiceImpl`、管理端前端。
- 真微信证书、退款、关单、平台证书轮换、加密 `resource` 密文。
- 持久化数据库（重启丢交易可接受）。

---

## 2. 架构

```text
（后续作者改造）take-out-admin :8080     （本期）take-out-mock-wechat :9090
        |                                      |
        |  POST /v3/pay/transactions/native -->|  存交易 NOTPAY + prepay_id
        |  GET  /v3/pay/transactions/...    -->|  查单
        |                                      |
        |  Postman: POST /mock/pay/confirm ---->|  NOTPAY → SUCCESS
        |                                      |
        |<---- HTTP POST notify_url ------------|  body = 现有 MockPayNotifyDTO 五字段
        |  /admin/order/mockPay/notify         |
```

假微信 **不**依赖 `take-out-system` Mapper，与外卖业务库解耦。

---

## 3. HTTP API 约定

默认端口：`9090`。

### 3.1 统一下单

`POST /v3/pay/transactions/native`

请求：

| 字段 | 类型 | 说明 |
|------|------|------|
| `out_trade_no` | string | 商户订单号（对应 `orders.number`） |
| `description` | string | 描述，可空或必填（实现时建议 `@NotBlank` 描述可放宽为可选） |
| `notify_url` | string | 付成功后回调地址 |
| `amount` | decimal | **元**，`BigDecimal`，签名用 `toPlainString()`，与现网 HMAC 一致 |
| `currency` | string | 可选，默认 `CNY` |

响应：

| 字段 | 说明 |
|------|------|
| `prepay_id` | 假微信生成，如 `wx_prepay_{uuid}` |
| `out_trade_no` | 回显 |
| `trade_state` | `NOTPAY` |

幂等：同一 `out_trade_no` 若仍为 `NOTPAY`，返回原 `prepay_id`；若已 `SUCCESS`，返回业务冲突（HTTP 409 或统一错误体）。

### 3.2 查单

`GET /v3/pay/transactions/out-trade-no/{out_trade_no}`

响应：`out_trade_no`、`prepay_id`、`trade_state`（`NOTPAY` \| `SUCCESS`）、`amount`、`notify_url`（可选是否回显）。

不存在：HTTP 404。

### 3.3 确认支付（教学）

`POST /mock/pay/confirm`

```json
{ "out_trade_no": "ORD..." }
```

（实现时可额外支持 `prepay_id`，至少支持 `out_trade_no`。）

行为：

1. 交易不存在 → 404  
2. 已是 `SUCCESS` → 幂等成功，**不再重复** POST notify  
3. `NOTPAY` → 置 `SUCCESS`，按 `notify_url` 发起回调  
4. `notify_url` 为空 → 失败并提示  

### 3.4 回调商户（出站）

URL：下单时传入的 `notify_url`。  
Method：`POST`，`Content-Type: application/json`。

Body（**兼容现有** `MockPayNotifyDTO`，便于作者暂不改验签逻辑）：

| 字段 | 说明 |
|------|------|
| `orderNumber` | = `out_trade_no` |
| `amount` | 与下单金额一致 |
| `timestamp` | 秒级 Unix 时间戳 |
| `nonce` | 随机串 |
| `sign` | HMAC-SHA256 十六进制 |

签名明文（与 `take-out-pay` 的 `HmacPaySignUtil` 一致）：

```text
amount={plain}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}
```

商户返回 HTTP 2xx 视为成功；否则打 warn 日志，**最多再重试 2 次**（短间隔），仍失败只记日志不回滚渠道侧 `SUCCESS`（贴近「渠道已成功、商户自行对账」的简化模型）。

---

## 4. 配置

`application.yml` 示例：

```yaml
server:
  port: 9090

mock-wechat:
  # 必须与 take-out 的 pay.mock-secret 一致，否则商户验签失败
  merchant-notify-secret: change-me
  notify-max-retries: 2
  notify-retry-delay-ms: 500
```

---

## 5. 模块结构

```text
take-out-mock-wechat/
  pom.xml
  src/main/java/com/sky/takeout/mockwechat/
    MockWechatApplication.java
    api/                 # Controller
    domain/              # Trade、TradeState
    store/               # ConcurrentHashMap 仓库
    notify/              # RestClient/WebClient 出站回调
    sign/                # 与 HmacPaySignUtil 同算法的本地副本（避免本期改 pay / 强耦合）
    config/              # MockWechatProperties
  src/main/resources/application.yml
```

父 `take-out/pom.xml`：`modules` 增加 `take-out-mock-wechat`。  
依赖：Spring Boot Web、Validation、（可选）Lombok；**不**依赖 `take-out-system`。  
签名类：默认在模块内复制算法并注释「与 pay 模块对齐」；不强制上移 common（减少本期扩散）。

---

## 6. 存储与并发

- `ConcurrentHashMap<String, Trade>`，key = `out_trade_no`。  
- `prepay_id` → `out_trade_no` 的反向索引可选（若支持用 `prepay_id` confirm）。  
- 确认支付与状态变更用 `compute` / 同步块保证「只通知一次」。

---

## 7. 错误与响应风格

- 渠道 API：HTTP 状态码 + JSON（成功直接返回业务字段；错误体建议 `{ "code", "message" }`）。  
- 不必套用外卖 `Result`（假微信是外部渠道语义）。  
- Bean Validation 负责必填校验。

---

## 8. 验收剧本（本期）

1. 仅启动 `take-out-mock-wechat`。  
2. `POST /v3/pay/transactions/native` 成功，得到 `prepay_id`，`trade_state=NOTPAY`。  
3. `GET .../out-trade-no/{no}` 一致。  
4. `POST /mock/pay/confirm` 后查单为 `SUCCESS`。  
5. 日志或 mock HTTP 服务能看到对 `notify_url` 的 POST（可先指到 https://httpbin.org/post 或本地任意接收端）；若 8080 已配置相同 secret 且存在对应未支付订单，则可顺便看到商户侧入账（**非本期必达**）。  
6. 再次 `confirm` 同单：成功且不重复刷通知（或仅一次通知）。

作者后续改造 `take-out-pay`：将进程内 `MockWeChatPayClient` 改为 HTTP 调用本服务统一下单；「点支付」改为打开/调用假微信 confirm，或保留管理端按钮转调假微信 confirm。

---

## 9. 测试建议

- 单元：签名明文拼装、幂等下单、重复 confirm 不二次 notify（可用 MockWebServer 断言出站次数）。  
- 本期可不强制写满测试；实现计划里再列最小用例。

---

## 10. 实现顺序（供后续 plan）

1. 父 POM 挂模块 + 空 Boot 应用可启动  
2. Trade 模型 + 内存 Store  
3. 统一下单 + 查单  
4. 签名工具副本 + NotifyClient  
5. confirm + 重试  
6. 验收剧本与 README 片段（如何启动、Postman 示例）

---

## 11. 决策摘要

| 项 | 选择 |
|----|------|
| 部署形态 | 同仓独立 Boot 模块，端口 9090 |
| API 保真 | V3 路径/字段形似 |
| 付成功触发 | 手动 `/mock/pay/confirm` |
| 签名 | HMAC，与现网一致 |
| 回调 body | 扁平 MockPayNotifyDTO 五字段 |
| 改 pay 模块 | 否（作者自改） |
