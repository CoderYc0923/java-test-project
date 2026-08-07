# 订单一期课堂讲义：管理端履约（宏观 → 微观）

日期：2026-08-07  
对应需求：`docs/requirements/2026-08-07-order-module.md`  
种子数据：`docs/sql/2026-08-07-seed-pending-orders.sql`  
前置：员工 / 分类 / 菜品 / 套餐已通；本期**只做管理端**，不写用户端下单与真支付。

---

## 第 0 课：今天学什么、不学什么

### 学什么（一期）

1. 订单**状态机**（比启售停售多几个合法跳转）  
2. `orders` + `order_detail` 读写  
3. 管理端：分页、统计角标、详情、接单/拒单/派送/完成/取消  

### 刻意不学（留给二期）

- 微信登录、购物车、真支付回调  
- Redis 超时关单、MQ、WebSocket 来单推送  

### 课堂节奏

```text
第1课 宏观地图与状态机
第2课 数据模型与种子数据
第3课 接口清单与返回形状
第4课 实现顺序（你按步手敲）
第5课 状态流转怎么写（微观模板）
第6课 自测清单与常见坑
```

---

## 第 1 课：宏观地图

### 1.1 角色

```text
（二期）用户下单并支付 ──产生──► 待接单订单
                                      │
管理端员工 ◄── 查询 / 接单 / 派送 / 完成 / 取消 ──┘
```

一期没有用户端，所以用 **SQL 种子**假装「用户已经付完钱，订单停在待接单」。

### 1.2 和菜品模块对比（建立直觉）

| | 菜品 | 订单（一期） |
|--|------|----------------|
| 主操作 | 增删改查 | **查 + 改状态**（几乎不「新建业务订单」） |
| 关键 | 字段校验 | **当前状态能不能执行这个动作** |
| 子表 | 口味 | 明细菜品（只读展示为主） |

### 1.3 状态机（请背熟）

```text
2 待接单 ──confirm──► 3 待派送 ──delivery──► 4 派送中 ──complete──► 5 已完成
    │                    │
    └──rejection─────────┴──► 6 已取消
    └──cancel（部分状态）────► 6 已取消
```

数字与前端 Tab：

| 值 | 含义 | 前端 Tab |
|----|------|----------|
| 2 | 待接单 | 待接单 |
| 3 | 待派送 | 待派送 |
| 4 | 派送中 | 派送中 |
| 5 | 已完成 | 已完成 |
| 6 | 已取消 | 已取消 |
| 0 | （查询用）全部 | 全部 |

**课堂金句：** 订单接口的第一句话永远是——「当前是什么状态？这个动作允许吗？」

---

## 第 2 课：数据模型（微观表结构）

### 2.1 两张核心表

**`orders`（订单头）**  
谁、寄哪、多少钱、什么状态、什么时候下的单。地址/电话会**冗余快照**在订单上（防止用户改地址后历史订单跟着变）。

**`order_detail`（订单行）**  
每道菜/套餐一行：名称、数量、金额、口味、dish_id/setmeal_id。同样是**下单时快照**。

### 2.2 一期还要认识、但先不写业务的表

- `user`：下单用户  
- `address_book`：地址  

种子脚本会插入测试用户 `id=1001` 和地址 `id=1001`，订单挂在它们上面。

### 2.3 种子数据说明

脚本造了 **3 笔待接单（status=2）**：

| 订单 id | 订单号 | 金额 | 明细 |
|---------|--------|------|------|
| 1001 | ORD20260807120001 | 62 | 酸菜鱼+米饭+王老吉 |
| 1002 | ORD20260807120002 | 10 | 北冰洋+米饭×2 |
| 1003 | ORD20260807120003 | 6 | 王老吉 |

导入后，统计接口做完时应有：`toBeConfirmed = 3`。

---

## 第 3 课：接口地图（对齐前端）

前端：`project-rjwm-admin-vue-ts/src/api/order.ts`  
后端真实路径加前缀 `/admin`。

| 课节目标 | Method | Path | 你要返回/改变什么 |
|----------|--------|------|-------------------|
| 列表 | GET | `/admin/order/conditionSearch` | 分页；可按 number/phone/时间/status |
| 角标 | GET | `/admin/order/statistics` | 2/3/4 三个 count |
| 详情 | GET | `/admin/order/details/{id}` | 头 + 明细列表 |
| 接单 | PUT | `/admin/order/confirm` | 仅 2→3 |
| 拒单 | PUT | `/admin/order/rejection` | 2→6 + rejectionReason |
| 派送 | PUT | `/admin/order/delivery/{id}` | 3→4 |
| 完成 | PUT | `/admin/order/complete/{id}` | 4→5 + deliveryTime |
| 取消 | PUT | `/admin/order/cancel` | 允许的状态→6 + cancelReason |

列表里待接单等 Tab 需要 **`orderDishes`**：把明细拼成可读字符串，例如：

`老坛酸菜鱼*1;米饭*1;王老吉*1`

（实现时可在 Service 里查明细后 `String.join`。）

现有 `OrderController` 只有空列表占位，下一课按顺序替换为真逻辑。

---

## 第 4 课：推荐实现顺序（上课进度表）

按这个顺序手敲，每做完一项就用种子数据验一次。

### Step A — 地基

1. 枚举 `OrderStatus`（code + 中文名，`@EnumValue` / `@JsonValue`，可参考 `SaleStatus`）  
2. 可选：`PayStatus`  
3. Entity：`Orders`（注意表名 `orders`）、`OrderDetail`  
4. Mapper：`OrdersMapper`、`OrderDetailMapper`  

### Step B — 只读能力（先让页面有数据）

5. `OrderService.page` / `statistics` / `getById`  
6. Controller 接到真 Service（替换空 Map）  
7. 启动后端 + 前端：订单页应能看到 3 条待接单，角标为 3  

### Step C — 状态流转（一期灵魂）

8. 抽私有方法：`Orders requireOrder(Long id)`  
9. 抽私有方法：`void assertStatus(Orders order, OrderStatus expected, String actionName)`  
10. 依次实现 confirm / rejection / delivery / complete / cancel  
11. 每个方法：`@Transactional` + 改字段 + `updateById`  

### Step D — 打磨

12. 取消/拒单原因写入对应字段与 `cancel_time`  
13. 非法跳转抛 `BusinessException`  
14. （可选）补 `orderDishes` 拼装性能：按本页 orderId 批量查明细再分组  

---

## 第 5 课：微观写法模板（状态流转）

下面是「上课板书」，不是要你复制粘贴完事，而是理解结构。

### 5.1 接单（2 → 3）

```text
1. 按 id 查订单，没有 → 业务异常
2. 若 status != 待接单 → 「当前订单不能接单」
3. setStatus(待派送)
4. updateById
```

### 5.2 拒单（2 → 6）

```text
1. 校验待接单
2. status = 已取消
3. rejectionReason = 入参
4. cancelTime = now（若表字段用于取消时间，拒单也可记）
5. update
```

### 5.3 派送 / 完成

```text
派送：仅 待派送(3) → 派送中(4)
完成：仅 派送中(4) → 已完成(5)，deliveryTime = now
```

### 5.4 为什么不要「直接 setStatus 成任意值」？

因为前端按钮是按状态显示的，但接口可能被 Postman 乱调。  
**服务端必须自己守门**，这就是状态机在业务代码里的落地。

### 5.5 和 `SaleStatus` 启停的相似点

```text
启停：读当前 → 改 0/1 → update
订单：读当前 → 断言合法 → 改到下一状态 → update（可能多写几个原因字段）
```

你会发现：**事务、Mapper、Exception 都是旧武器**，新的是「断言当前状态」。

---

## 第 6 课：分页查询微观要点

### 6.1 status 参数

前端 Tab：

- 传 `2/3/4/5/6`：只查该状态  
- 传 `0` 或空：查全部（注意别写成 `eq(status, 0)`）

### 6.2 时间范围

前端 `daterange` → `beginTime` / `endTime`（以实际前端参数名为准，联调时看 Network）。  
Wrapper：`ge(orderTime, begin)`、`le(orderTime, end)`。

### 6.3 statistics

三次 `selectCount`，条件分别是 status=2、3、4 即可（清晰优先于一条 SQL 炫技）。

---

## 第 7 课：自测剧本（当作业）

导入种子后：

1. 打开管理端「订单明细」→ 待接单应约 3 条  
2. 点统计角标数字是否对  
3. 点详情，能看到酸菜鱼等明细  
4. 对 1001 **接单** → 出现在待派送  
5. **派送** → 派送中；**完成** → 已完成  
6. 对另一笔 **拒单**，填原因 → 已取消且原因可见  
7. Postman 对已完成订单再接单 → 应失败  

---

## 第 8 课：常见坑（提前剧透）

| 坑 | 说明 |
|----|------|
| 表名 `orders` | SQL 关键字，MP `@TableName("orders")` 必须写对 |
| status=0 | 表示全部，不是数据库里的状态值 |
| 只改 Controller 不改 Service | 状态校验应落在 Service |
| 明细图片签名 | 一期明细 `image` 可能为空；有 objectKey 再签，种子可先空 |
| 路由顺序 | 若有 `/{id}`，静态路径如 `/statistics` 要写在前面（你们已有经验） |

---

## 附录：种子数据如何导入

在 `take-out/` 下（容器名用 `docker compose ps` 查看）：

```bash
docker cp docs/sql/2026-08-07-seed-pending-orders.sql take-out-mysql:/tmp/seed-orders.sql
docker exec -i take-out-mysql mysql -uroot -proot --default-character-set=utf8mb4 take_out -e "SOURCE /tmp/seed-orders.sql"
```

验证：

```sql
SELECT id, number, status, amount FROM orders WHERE status = 2;
SELECT order_id, name, number, amount FROM order_detail WHERE order_id IN (1001,1002,1003);
```

---

## 下一堂课预告

你读完本文并导入种子后，课堂进入 **Step A**：一起写 `OrderStatus` + Entity + Mapper。  
仍然是「我讲结构与步骤，你手敲」；需要我改成直接代写时再说。

二期（用户下单 / Mock 支付）等一期页面跑通再开课。
