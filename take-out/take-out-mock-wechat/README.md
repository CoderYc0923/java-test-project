# take-out-mock-wechat

本地 Mock 微信支付 V3 服务，用于开发/联调：**Native 下单**、**按商户订单号查询**、**手动确认支付**、**向商户 `notify_url` 发送 HMAC 回调**。

> **不修改 `take-out-pay`。** 联调时请在业务侧自行将微信 Client 指向本服务（例如 `http://127.0.0.1:9090`），或通过网关/配置切换。

## 启动

在 `take-out` 根目录执行：

```bash
mvn -pl take-out-mock-wechat spring-boot:run
```

默认端口 **9090**（见 `src/main/resources/application.yml`）。

## 配置：`merchant-notify-secret`

`mock-wechat.merchant-notify-secret` **必须与** 外卖工程 `pay.mock-secret` **一致**，否则管理端验签会失败。

| 模块 | 配置项 | 示例（take-out-admin） |
|------|--------|-------------------------|
| 本服务 | `mock-wechat.merchant-notify-secret` | `change-me`（请改为与下表相同） |
| take-out-admin | `pay.mock-secret` | `takeout_admin_pay_secret_key_cyrus` |

修改方式：编辑 `application.yml`，或启动时覆盖：

```bash
mvn -pl take-out-mock-wechat spring-boot:run -Dspring-boot.run.arguments="--mock-wechat.merchant-notify-secret=takeout_admin_pay_secret_key_cyrus"
```

## Postman / 手工联调三步

1. **Native 下单** — `POST /v3/pay/transactions/native`  
2. **查询** — `GET /v3/pay/transactions/out-trade-no/{out_trade_no}`（此时 `trade_state` 一般为 `NOTPAY`）  
3. **确认支付** — `POST /mock/pay/confirm`（之后再次查询应为 `SUCCESS`）

### `notify_url` 示例

对接真实管理端回调时：

```
http://127.0.0.1:8080/admin/order/mockPay/notify
```

前提：**take-out-admin 已启动**，且该路径已在安全白名单中放行。确认支付后，本服务会向该 URL POST 五字段 JSON（`orderNumber`、`amount`、`timestamp`、`nonce`、`sign`），签名算法与 `take-out-pay` Mock 网关一致。

仅测 Mock 服务本身时，`notify_url` 可使用公网 echo 地址（如 `https://httpbin.org/post`）或任意可达 URL；**即使回调失败，确认后本地订单状态仍会变为 `SUCCESS`**。

## 命令行冒烟（curl / PowerShell）

另开终端，在 Mock 服务运行后执行。

**Bash (curl):**

```bash
curl -s -X POST http://127.0.0.1:9090/v3/pay/transactions/native \
  -H "Content-Type: application/json" \
  -d "{\"out_trade_no\":\"ORD_SMOKE_1\",\"description\":\"smoke\",\"notify_url\":\"https://httpbin.org/post\",\"amount\":1.00}"

curl -s http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1

curl -s -X POST http://127.0.0.1:9090/mock/pay/confirm \
  -H "Content-Type: application/json" \
  -d "{\"out_trade_no\":\"ORD_SMOKE_1\"}"

curl -s http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1
```

**PowerShell:**

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:9090/v3/pay/transactions/native `
  -ContentType application/json `
  -Body '{"out_trade_no":"ORD_SMOKE_1","description":"smoke","notify_url":"https://httpbin.org/post","amount":1.00}'

Invoke-RestMethod -Uri http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1

Invoke-RestMethod -Method Post -Uri http://127.0.0.1:9090/mock/pay/confirm `
  -ContentType application/json `
  -Body '{"out_trade_no":"ORD_SMOKE_1"}'

Invoke-RestMethod -Uri http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1
```

**预期：** 最后一次查询 JSON 中 `trade_state` 为 `"SUCCESS"`；confirm 过程会向 `notify_url` 发起 POST（httpbin 可在响应中看到 echo body）。

若 httpbin 不可达，将 `notify_url` 改为无效地址仍可验证 **native → confirm → query SUCCESS** 本地链路。

## API 速查

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v3/pay/transactions/native` | 创建 Native 订单 |
| GET | `/v3/pay/transactions/out-trade-no/{out_trade_no}` | 查询订单 |
| POST | `/mock/pay/confirm` | 手动确认并触发商户回调 |
