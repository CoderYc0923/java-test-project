# 订单模块需求分析与需求文档

日期：2026-08-07  
来源：`project-rjwm-admin-vue-ts` 订单页 + `sky.sql` + 当前后端进度  
建议顺序：**在套餐之后做订单；且先做「管理端履约」，再考虑「用户端下单」**

---

## 0. 和前面模块的本质差别

| 维度 | 员工/分类/菜品/套餐 | 订单 |
|------|---------------------|------|
| 业务形态 | 主数据 CRUD | **状态机流转**（多状态、有合法/非法跳转） |
| 数据入口 | 管理端自己增删改 | 订单通常由 **C 端用户**下单产生；管理端主要接单/派送/取消 |
| 关联表 | 1～2 张 | `orders` + `order_detail`，还牵涉 `user`、`address_book`、`shopping_cart` |
| 实时性 | 低 | 来单提醒、超时未支付取消（可选） |
| 支付 | 无 | 微信/支付宝（学习项目建议 **Mock**） |

你们仓库里目前只有 **管理端前端**，没有用户端小程序工程。因此订单不能只做成「再写一个 CRUD」，要分清 **谁创建订单、谁处理订单**。

---

## 1. 推荐落地节奏（两期）

```text
一期（建议先做）：管理端订单履约  ← 直接驱动现有「订单明细」页面
二期（可选）：用户端下单链路      ← 用户/地址/购物车/支付 Mock / 用户端 API
```

**一期就能把管理端页面跑通**：用 SQL 种子或「管理端模拟下单接口」造待接单数据，不必先上完整微信登录与支付。

**二期**再补 C 端能力；不上线也可以本地 Postman/临时接口模拟用户下单。

---

## 2. 订单状态（核心约定）

表注释：

| status | 含义 | 管理端 Tab（前端） |
|--------|------|-------------------|
| 1 | 待付款 | 一般不单独强调（支付前） |
| 2 | 待接单 | Tab「待接单」`toBeConfirmed` |
| 3 | 已接单（待派送） | Tab「待派送」`confirmed` |
| 4 | 派送中 | Tab「派送中」`deliveryInProgress` |
| 5 | 已完成 | Tab「已完成」 |
| 6 | 已取消 | Tab「已取消」 |
| 7 | 退款 | 表有定义；管理端 Tab 未单独拆，可并入取消/完成扩展 |

支付状态 `pay_status`：`0` 未支付 / `1` 已支付 / `2` 退款。

### 2.1 一期合法流转（管理端）

```text
待接单(2) --接单--> 待派送(3) --派送--> 派送中(4) --完成--> 已完成(5)
   |                   |
   +--拒单-------------+----> 已取消(6)
待接单/待派送/派送中等 --取消--> 已取消(6)（规则按前端可操作状态收紧）
```

实现时用枚举 + 校验：**禁止随意跳状态**（这是订单相对菜品最大的新点）。

---

## 3. 一期：管理端功能清单（对齐现有前端）

前端 API：`src/api/order.ts`；页面：`views/orderDetails`。

| 序号 | 功能 | 前端接口 | 说明 |
|------|------|----------|------|
| O1 | 条件分页搜索 | `GET /order/conditionSearch` | 订单号、手机号、下单时间范围、status（Tab） |
| O2 | 各状态数量统计 | `GET /order/statistics` | `toBeConfirmed` / `confirmed` / `deliveryInProgress` |
| O3 | 订单详情 | `GET /order/details/{orderId}` | 含明细菜品、金额、地址等 |
| O4 | 接单 | `PUT /order/confirm` | body 含订单 id |
| O5 | 拒单 | `PUT /order/rejection` | 需 `rejectionReason` |
| O6 | 派送 | `PUT /order/delivery/{id}` | 2→3 后的下一步：3→4 |
| O7 | 完成 | `PUT /order/complete/{id}` | 4→5 |
| O8 | 取消 | `PUT /order/cancel` | 需 `cancelReason` |

列表字段前端会用到（按 Tab 不同列）：`number`、`orderDishes`（菜品摘要字符串）、`status`、`consignee`、`phone`、`address`、`orderTime`、`amount`、`remark`、`estimatedDeliveryTime`、`tablewareNumber`、`cancelTime`、`cancelReason`、`deliveryTime` 等。

后端现状：`OrderController` 仅对 `conditionSearch` / `statistics` 返回空数据占位。

### 3.1 造数策略（一期没有用户端时）

任选其一即可验收管理端：

1. **SQL 插入**若干 `orders` + `order_detail`（status=2 待接单）  
2. 提供 **调试接口** `POST /admin/order/mock`（仅 dev profile）：按菜品/套餐组装一笔待接单订单  

---

## 4. 二期：用户端下单（完整外卖链路，知识点更多）

涉及表：`user`、`address_book`、`shopping_cart`、`orders`、`order_detail`。

典型流程：

```text
登录(微信 openid) → 浏览菜品/套餐 → 加购 → 选地址
→ 提交订单(清购物车、写 orders+detail) → 支付(Mock/微信)
→ status: 待付款(1) → 已支付后待接单(2)
→ （可选）超时未支付自动取消
```

二期接口（用户端，路径通常 `/user/...`，本仓库尚无用户端工程）：

- 购物车增删改查  
- 地址簿 CRUD  
- 下单、支付回调/模拟支付、用户侧订单列表与取消  

**没有用户端前端时，二期可用 Postman 验收。**

---

## 5. 数据模型摘要

### 5.1 `orders`

| 字段 | 说明 |
|------|------|
| number | 订单号（业务唯一，需生成规则） |
| status / pay_status / pay_method | 订单态、支付态、支付方式 |
| user_id / address_book_id | 用户与地址 |
| order_time / checkout_time | 下单、结账 |
| amount / pack_amount | 实收、打包费 |
| phone / address / consignee / user_name | 冗余快照（下单时拷贝，防地址后改） |
| cancel_reason / rejection_reason / cancel_time | 取消/拒单 |
| estimated_delivery_time / delivery_time / delivery_status | 配送相关 |
| tableware_number / tableware_status / remark | 餐具与备注 |

### 5.2 `order_detail`

| 字段 | 说明 |
|------|------|
| order_id | 所属订单 |
| dish_id / setmeal_id | 菜或套餐（二选一或按业务） |
| name / image / dish_flavor / number / amount | 明细快照与小计 |

下单时把当时名称、图片、口味、价格**快照**进明细，避免日后改菜品影响历史订单。

---

## 6. 一期接口约定（管理端）

路径均为 `/admin/order/...`（前端代理后为 `/order/...`）。

### 6.1 分页搜索

- **GET** `/admin/order/conditionSearch`  
- Query：`page`、`pageSize`、`number?`、`phone?`、`beginTime?`、`endTime?`、`status?`（0 表示全部，前端 Tab 会传）  
- 返回：`{ records, total }`；待接单等列表建议带 `orderDishes`（明细名称拼串，如「宫保鸡丁*1;米饭*1」）

### 6.2 统计

- **GET** `/admin/order/statistics`  
- 返回示例：

```json
{
  "toBeConfirmed": 3,
  "confirmed": 1,
  "deliveryInProgress": 2
}
```

对应 status = 2 / 3 / 4 的 count。

### 6.3 详情

- **GET** `/admin/order/details/{id}`  
- 订单头 + `orderDetailList`（或前端约定字段名，实现时对照详情弹窗）。

### 6.4 接单 / 拒单 / 派送 / 完成 / 取消

| 操作 | Method | Path | 要点 |
|------|--------|------|------|
| 接单 | PUT | `/admin/order/confirm` | body: `{ id }`；仅 status=2 → 3 |
| 拒单 | PUT | `/admin/order/rejection` | body: `{ id, rejectionReason }`；2 → 6 |
| 派送 | PUT | `/admin/order/delivery/{id}` | 3 → 4 |
| 完成 | PUT | `/admin/order/complete/{id}` | 4 → 5；可写 deliveryTime |
| 取消 | PUT | `/admin/order/cancel` | body: `{ id, cancelReason }`；写 cancelTime |

全部建议 `@Transactional`，并校验当前状态是否允许该操作。

---

## 7. 你可能还没系统做过的知识点

按「一期必学 / 二期再学 / 可延后」分层。

### 7.1 一期就会碰到（建议认真学）

1. **订单状态机**  
   枚举 `OrderStatus` + 方法内校验「当前状态能否执行某动作」。比 `SaleStatus` 启停复杂一档。

2. **多表读写与快照**  
   列表拼 `orderDishes`、详情查明细；取消/拒单写原因字段。

3. **条件分页更复杂**  
   时间范围、`status=0` 表示全部、手机号/订单号组合。

4. **聚合统计**  
   多个 `selectCount` 或一条分组 SQL，供 Tab 角标。

5. **订单号生成**  
   如时间戳 + 随机/雪花/号段；保证可读且尽量唯一。

### 7.2 二期才会硬碰（现在可先知道名字）

| 知识点 | 说明 | 学习项目建议 |
|--------|------|--------------|
| **用户端鉴权** | 微信登录 openid → `user` 表 | 可 Mock 固定 userId |
| **购物车** | `shopping_cart` 会话级数据 | 常规 CRUD |
| **下单事务** | 读购物车 → 写订单+明细 → 清空购物车 | 你已熟悉 `@Transactional` |
| **支付** | 微信统一下单、回调验签、幂等 | **强烈建议先 Mock「已支付」** |
| **支付超时关单** | 30 分钟未支付自动取消 | 见下节 Redis/延迟任务 |
| **来单提醒** | 管理端弹窗/角标推送 | WebSocket / SSE / 轮询 |
| **幂等与并发** | 重复提交订单、重复回调 | 二期进阶 |

### 7.3 和「不上线」的关系

- 状态机、管理端履约、Mock 下单：**本地启动就能学完**  
- 真微信支付、公网回调：**不上线很难完整做**，用 Mock 替代即可  
- WebSocket 来单提醒：本地开着后端+前端可以做；关掉进程就没有推送（与定时任务同理）

---

## 8. Redis / MQ / WebSocket 要不要？

### 8.1 一期（管理端履约）结论

| 组件 | 是否需要 | 说明 |
|------|----------|------|
| **Redis** | **不必须** | 列表/接单/统计用 MySQL 足够 |
| **MQ** | **不需要** | 无异步削峰、无多服务解耦 |
| **WebSocket** | **可选** | 没有则前端继续调 `/order/statistics` 或刷新列表；有则来单更「像线上」 |

一期专注：**表结构 + 状态机 + 管理端 API**，不要为订单强行上中间件。

### 8.2 二期及以后再考虑

```text
支付超时自动取消     → Redis 过期监听 / 延迟队列 / 定时扫表（扫表最简单，且不上线也能在启动时跑）
购物车、验证码       → Redis（可选）
下单高峰削峰         → MQ（学习项目通常不必）
来单实时通知         → WebSocket（或短轮询）
支付回调解耦         → MQ（有真实支付再谈）
```

**不上线、进程不常驻时：** 依赖「一直挂着的定时任务 / 延迟队列」不可靠；二期超时关单可用 **「用户查询订单时顺便检查是否超时」** 或 **手动/调试接口关单**，比硬上 Redis 更务实。

---

## 9. 建议实现顺序

### 一期

1. Entity：`Orders`、`OrderDetail` + `OrderStatus` / `PayStatus` 枚举  
2. O1 分页 + O2 统计（先用 SQL 种子数据）  
3. O3 详情  
4. O4～O8 状态流转  
5. （可选）dev 环境 Mock 下单，方便联调  
6. （可选）管理端短轮询统计，代替 WebSocket  

### 二期

1. User / AddressBook / ShoppingCart  
2. 用户端下单 + Mock 支付  
3. 再评估超时关单与来单提醒  

---

## 10. 验收清单（一期）

- [ ] Tab 切换带 status 查询正确；全部(0) 能查  
- [ ] 统计三个数字与列表一致  
- [ ] 详情展示明细与金额  
- [ ] 接单/拒单/派送/完成/取消后状态与原因字段正确  
- [ ] 非法状态跳转被拒绝并提示  
- [ ] 未引入 Redis/MQ（除非主动选做 WebSocket）

---

## 11. 接口一览（一期）

| 功能 | Method | Path |
|------|--------|------|
| 条件分页 | GET | `/admin/order/conditionSearch` |
| 状态数量 | GET | `/admin/order/statistics` |
| 详情 | GET | `/admin/order/details/{id}` |
| 接单 | PUT | `/admin/order/confirm` |
| 拒单 | PUT | `/admin/order/rejection` |
| 派送 | PUT | `/admin/order/delivery/{id}` |
| 完成 | PUT | `/admin/order/complete/{id}` |
| 取消 | PUT | `/admin/order/cancel` |

---

## 12. 总结

- **下一模块是订单**；相对套餐，新增核心是 **状态机 + 履约操作 +（完整链路时的）用户/支付**。  
- **一期只做管理端**就能学到订单最有价值的部分，且匹配现有前端。  
- **Redis/MQ 一期不需要**；支付用 Mock；推送可选。  
- 仓库无用户端时，二期用接口/种子造数即可，不必纠结上线与真微信回调。

确认一期范围后，可再出实现计划或从 Entity/枚举/状态机骨架开始手敲。
