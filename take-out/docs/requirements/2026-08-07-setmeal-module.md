# 下一步：套餐管理需求文档

日期：2026-08-07  
来源：`project-rjwm-admin-vue-ts` + 当前后端进度  
建议下一模块：**套餐管理（Setmeal）**

---

## 0. 为什么接下来做套餐？

### 0.1 当前进度

| 模块 | 状态 |
|------|------|
| 员工 Employee | 已完成 |
| 分类 Category | 已完成 |
| 菜品 Dish | 已完成（含分页/CRUD/启停/口味、OSS 上传与签名 URL） |
| 通用上传 `/common/upload` | 已完成 |
| 套餐 Setmeal | Entity / Mapper 已有，**业务未做** |
| 订单 / 统计 | 更后 |

### 0.2 依赖顺序（与此前约定一致）

```text
员工 → 分类 → 菜品 → 【套餐】 → 订单 → 统计
```

套餐新增依赖：

- `GET /admin/category/list?type=2`（套餐分类）
- `GET /admin/dish/list?categoryId=`（选菜，只要启售菜品）
- `POST /admin/common/upload`（套餐图，`type=setmeal`）

**结论：下一块做「套餐管理」。**

---

## 1. 模块概述

维护门店套餐：名称、套餐分类、价格、图片、描述、包含哪些菜品及份数、启售/停售。

状态：复用 `SaleStatus`（`0` 停售 / `1` 启售）。

图片约定与菜品对齐：

| 场景 | 字段 |
|------|------|
| 入库 / 请求体 | `imageOssPath`（objectKey） |
| 列表展示 | `imageUrl`（签名 URL） |
| 详情回显 | `imageOssPath` + `imageUrl` |
| 上传 | `{ objectKey, url }`，表单只存 key |

前端套餐页目前仍用 `image`，实现期需按菜品同样改造（列表 `imageUrl`，表单 `imageOssPath`，`ImgUpload` 传 `bizType=setmeal`）。

启停接口建议与菜品一致：`POST /admin/setmeal/{id}/status` + body `{ status }`（前端现为 `/setmeal/status/{status}?id=`，实现时改前端对齐）。

---

## 2. 功能点清单

| 序号 | 功能 | 说明 | 前端入口 |
|------|------|------|----------|
| S1 | 分页条件查询 | 名称、套餐分类、售卖状态；含 `categoryName`、`imageUrl` | 套餐列表 |
| S2 | 新增套餐 | 主表 + `setmeal_dish` 多菜；图片 | 「+ 新建套餐」 |
| S3 | 按 id 查详情 | 含 `setmealDishes`；图片双字段 | 修改页 |
| S4 | 修改套餐 | 主表更新；菜品关系先删后插 | 行内「修改」 |
| S5 | 删除 / 批量删除 | query `ids` 逗号分隔 | 单删 / 批量 |
| S6 | 启售 / 停售 | 单个（可顺带支持批量 ids） | 行内启停 |
| S7 | （可选）套餐 list | 若 C 端/订单后续需要再补 | — |

选菜弹窗依赖已有 **菜品 list**，本期不必新接口。

---

## 3. 数据模型

### 3.1 表 `setmeal`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| category_id | bigint | 套餐分类（`category.type = 2`） |
| name | varchar(32) UNIQUE | 套餐名称 |
| price | decimal(10,2) | 套餐价格 |
| status | int | 0 停售 / 1 启售 |
| description | varchar(255) | 描述 |
| image | varchar(255) | **存 objectKey**（与菜品一致） |
| create_time / update_time | datetime | 审计 |
| create_user / update_user | bigint | 审计 |

Entity 已有；建议 `status` 改为 `SaleStatus` 枚举（与菜品一致）。

### 3.2 表 `setmeal_dish`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| setmeal_id | bigint | 套餐 id |
| dish_id | bigint | 菜品 id |
| name | varchar(32) | 菜品名冗余 |
| price | decimal(10,2) | 菜品单价冗余 |
| copies | int | 份数 |

Entity / Mapper 已有。

---

## 4. 接口说明

路径：前端 `/setmeal` → 代理 `/admin/setmeal`。  
统一 `Result`，成功 `code = 1`。

### 4.1 S1 分页

- **GET** `/admin/setmeal/page`
- Query：`page`、`pageSize`、`name?`、`categoryId?`、`status?`
- `data`：`{ records, total }`
- 记录字段建议：

```json
{
  "id": 1,
  "name": "商务套餐A",
  "categoryId": 12,
  "categoryName": "商务套餐",
  "price": 68.00,
  "imageOssPath": "setmeal/2026/08/07/xxx.png",
  "imageUrl": "https://...?Expires=...&Signature=...",
  "status": 1,
  "updateTime": "2026-08-07 12:00:00"
}
```

分类名：本页 `categoryId` 去重 → `selectByIds` → Map（与菜品相同）。

### 4.2 S2 新增

- **POST** `/admin/setmeal`
- Body 示例：

```json
{
  "name": "商务套餐A",
  "categoryId": 12,
  "price": 68.00,
  "imageOssPath": "setmeal/2026/08/07/xxx.png",
  "description": "一荤一素",
  "status": 0,
  "setmealDishes": [
    {
      "dishId": 46,
      "name": "王老吉",
      "price": 6.00,
      "copies": 1
    }
  ]
}
```

说明：

- 前端表单里分类可能叫 `idType`，提交时映射为 `categoryId`。
- 菜品列表前端可能叫 `dishList` / `checkList`，后端统一收 **`setmealDishes`**（与详情回显字段一致，减少两套名字）。
- 校验：名称唯一；分类存在且 `type=SETMEAL`；至少一道菜；`imageOssPath` 非空；价格 > 0。
- 事务：插 `setmeal` → 批量插 `setmeal_dish`（可写冗余 name/price，或按 dishId 再查一遍写入）。
- 新增默认停售：与前端一致时 `status=0`。

### 4.3 S3 详情

- **GET** `/admin/setmeal/{id}`
- 含 `setmealDishes` 数组；`imageOssPath` + `imageUrl`。

### 4.4 S4 修改

- **PUT** `/admin/setmeal`
- Body：含 `id` + 同新增字段。
- 口味策略类比：对 `setmeal_dish` **先删后插**。

### 4.5 S5 删除

- **DELETE** `/admin/setmeal?ids=1,2,3`
- 建议：启售中禁止删；先删 `setmeal_dish` 再删 `setmeal`；`@Transactional`。
- （若订单已引用套餐，后期加校验；本期可无订单表则不做。）

### 4.6 S6 启停

- 推荐：**POST** `/admin/setmeal/{id}/status`，body：`{ "status": 0|1 }`（`SaleStatus`）
- 前端 `setMeal.ts` / 列表页按菜品方式改造。

---

## 5. 建议实现顺序

1. 占位：Controller / Service / DTO / VO（对齐菜品包结构 `dto.setmeal` / `vo.setmeal`）  
2. Entity：`status` → `SaleStatus`；图片字段语义明确为 OSS path  
3. S1 分页（含 categoryName + 签名 imageUrl）  
4. 前端套餐图片字段对齐 `imageOssPath` / `imageUrl`；启停对齐 REST  
5. S2 / S3 / S4（事务 + setmealDishes）  
6. S5 / S6  

---

## 6. 是否需要 Redis / MQ？

### 6.1 结论（套餐这一期）

| 组件 | 是否需要 | 说明 |
|------|----------|------|
| **Redis** | **不必须** | 套餐仍是管理端同步 CRUD；无高并发读缓存硬需求 |
| **MQ** | **不需要** | 无异步解耦、削峰、跨服务通知 |
| **OSS** | **已有即可** | 复用 `/common/upload`，`type=setmeal` |
| **WebSocket** | **不需要** | 订单来单再考虑 |

### 6.2 以后可能用到的时机（本模块不做）

```text
C 端菜单 / 高并发浏览  → Redis 缓存分类+菜品+套餐
店铺营业状态           → Redis（Shop 已预留）
下单、支付、出餐通知   → MQ / WebSocket（订单模块）
```

**不要为套餐模块单独引入 Redis/MQ。**

---

## 7. 与菜品模块的衔接注意

1. 删除菜品时已校验 `setmeal_dish`；套餐维护菜品关系后该校验才会真正拦得住。  
2. 选菜用 `dish/list`（仅启售）；停售菜品不应再被新套餐勾选（详情里历史关联可仍展示）。  
3. 图片：套餐与菜品同一套 OSS 约定，避免再存签名 URL。

---

## 8. 验收清单

- [ ] 分页：名称 / 分类 / 状态筛选；有 `categoryName`、`imageUrl`
- [ ] 新增：选套餐分类 + 多道菜 + 份数 + 图片；库中 `image` 为 objectKey
- [ ] 详情回显菜品列表与图片；修改后关系正确
- [ ] 删除单个/批量；启售中拒绝（若实现该规则）
- [ ] 启停单个生效
- [ ] 未引入 Redis/MQ

---

## 9. 接口一览

| 功能 | Method | Path | 主要入参 |
|------|--------|------|----------|
| 分页 | GET | `/admin/setmeal/page` | page, pageSize, name?, categoryId?, status? |
| 新增 | POST | `/admin/setmeal` | name, categoryId, price, imageOssPath, setmealDishes, … |
| 详情 | GET | `/admin/setmeal/{id}` | path id |
| 修改 | PUT | `/admin/setmeal` | id + 同新增 |
| 删除 | DELETE | `/admin/setmeal` | query ids |
| 启停 | POST | `/admin/setmeal/{id}/status` | body status |
| 上传 | POST | `/admin/common/upload` | file, type=setmeal（已有） |
