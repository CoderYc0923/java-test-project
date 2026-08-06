# 下一步开发建议与菜品管理需求文档

日期：2026-08-06  
来源：`project-rjwm-admin-vue-ts` + 当前后端进度  
建议下一模块：**菜品管理（Dish）**

---

## 0. 为什么接下来做菜品，而不是套餐/订单？

### 0.1 当前后端进度（管理端）

| 模块 | 状态 |
|------|------|
| 员工 Employee | 已完成（登录、CRUD、分页、启停） |
| 分类 Category | 基本完成（依赖 `category/list`） |
| 店铺 Shop | 内存占位 |
| 订单 Order | 空列表占位 |
| 菜品 / 套餐 / 统计 / 通知 | 未做 |

### 0.2 前端模块依赖关系

```text
分类 ──► 菜品 ──► 套餐
              │
              └──►（套餐选菜依赖 dish/list）

订单 / 工作台 / 统计 ──► 依赖菜品、套餐、订单数据
```

- **套餐**新增时要选「套餐分类」+ 勾选**菜品**，没有菜品模块无法完整做套餐。  
- **订单 / 统计**更靠后，数据与业务更重。  
- 因此业界/课程常见顺序是：员工 → 分类 → **菜品** → 套餐 → 订单 → 统计。

**结论：下一块做「菜品管理」。**

---

## 1. 菜品模块概述

维护门店可售菜品：名称、分类、价格、图片、描述、口味、启售/停售。  
依赖已实现的 **`GET /admin/category/list?type=1`**。

状态约定：`0` 停售，`1` 启售。

---

## 2. 功能点清单

| 序号 | 功能 | 说明 | 前端入口 |
|------|------|------|----------|
| D1 | 分页条件查询 | 按名称、分类、售卖状态 | 菜品列表页 |
| D2 | 新增菜品 | 含口味、图片 | 「+ 新建菜品」 |
| D3 | 按 id 查询详情 | 编辑回显（含 flavors） | 修改页 |
| D4 | 修改菜品 | 含口味覆盖更新 | 行内「修改」 |
| D5 | 删除 / 批量删除 | query `ids`，逗号分隔 | 单删 / 批量删除 |
| D6 | 启售 / 停售 | 支持单个；接口形态可顺带支持批量 id | 行内启售停售 |
| D7 | 按分类查菜品列表 | 套餐选菜用 | 套餐模块调用 |
| D8 | 文件上传 | 菜品图片 | `ImgUpload` → `/common/upload` |

D8 属于**公共能力**，建议与菜品同期做（否则无法完整走通新增）。

---

## 3. 数据模型

### 3.1 表 `dish`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| name | varchar(32) UNIQUE | 菜品名称 |
| category_id | bigint | 菜品分类 id |
| price | decimal(10,2) | 价格 |
| image | varchar(255) | 图片 URL 或文件标识 |
| description | varchar(255) | 描述 |
| status | int | 0 停售 / 1 启售 |
| create_time / update_time | datetime | 审计 |
| create_user / update_user | bigint | 审计 |

> 前端表单里有 `code`（商品码）校验，**当前 `sky.sql` 的 dish 表无此列**。建议：**本期忽略/不落库**，或单独加列（非必须）。

### 3.2 表 `dish_flavor`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| dish_id | bigint | 菜品 id |
| name | varchar(32) | 口味名，如「甜味」「辣度」 |
| value | varchar(255) | 口味选项 JSON 字符串，如 `["无糖","少糖"]` |

前端提交时会把 `value` **JSON.stringify** 成字符串再传给后端。

---

## 4. 接口详细说明

路径均为后端真实路径（前端 `/dish` → 代理 `/admin/dish`）。  
统一响应：`Result`，成功 `code = 1`。

### 4.1 D1 分页查询

- **GET** `/admin/dish/page`
- **Query：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |
| name | string | 否 | 菜品名模糊 |
| categoryId | number | 否 | 分类 id |
| status | number | 否 | 0/1；前端可能传空字符串，后端按「无条件」处理 |

- **响应 `data`：**

```json
{
  "records": [
    {
      "id": 46,
      "name": "王老吉",
      "categoryId": 11,
      "categoryName": "酒水饮料",
      "price": 6.00,
      "image": "https://...",
      "description": "",
      "status": 1,
      "updateTime": "2022-06-09 22:40:47"
    }
  ],
  "total": 24
}
```

**注意：** 列表需要 **`categoryName`**（联表或二次查询分类名），前端表格直接展示该字段。

---

### 4.2 D2 新增菜品

- **POST** `/admin/dish`
- **Body 示例：**

```json
{
  "name": "宫保鸡丁",
  "categoryId": 18,
  "price": 38.00,
  "image": "https://xxx/xxx.png",
  "description": "微辣",
  "status": 0,
  "flavors": [
    { "name": "辣度", "value": "[\"不辣\",\"微辣\",\"中辣\"]" }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| name | 是 | 2～20 字符；唯一 |
| categoryId | 是 | 必须是菜品分类（type=1） |
| price | 是 | >0，最多两位小数 |
| image | 是 | 上传接口返回的地址 |
| description | 否 | |
| status | 否 | 前端新增默认传 **0（停售）** |
| flavors | 否 | 口味列表；先插 dish，再批量插 dish_flavor |

事务：菜品 + 口味应在同一事务中。

---

### 4.3 D3 详情

- **GET** `/admin/dish/{id}`
- **响应 `data`：** 菜品字段 + `flavors` 数组（编辑页回显）。

`value` 建议仍返回字符串；前端会 `map` 后使用。若返回已解析数组，需与前端约定（当前前端按对象使用，新增时 stringify）。

---

### 4.4 D4 修改

- **PUT** `/admin/dish`
- **Body：** 含 `id` + 与新增类似字段 + `flavors`
- **口味策略（推荐）：** 先删该菜品旧 flavors，再插入新 flavors（简单可靠）。

---

### 4.5 D5 删除

- **DELETE** `/admin/dish?ids=1,2,3`
- 支持单个或批量（逗号分隔）。
- **建议业务规则：** 若菜品已关联套餐（`setmeal_dish`），禁止删除并提示。

---

### 4.6 D6 启售 / 停售

- **前端现状：** `POST /admin/dish/status/{status}?id=1` 或 `id=1,2,3`
- 与分类改前风格一致；也可后续改成 `POST /admin/dish/{id}/status` + body（需改前端）。

本期对齐前端：

```text
POST /admin/dish/status/{status}?id={id或逗号分隔ids}
```

---

### 4.7 D7 菜品列表（套餐用）

- **GET** `/admin/dish/list?categoryId=11`
- **响应 `data`：** 数组（通常只要启售菜品即可）。

---

### 4.8 D8 通用上传

- **POST** `/admin/common/upload`（multipart）
- Header：`Authorization`
- **成功响应：** `Result`，`data` 为图片可访问 URL（或文件名；前端 `ImgUpload` 直接把 `response.data` 当图片地址用）

```json
{ "code": 1, "data": "https://xxx/abc.png", "msg": "success" }
```

存储方案见第 6 节：**本期不必上 MQ；文件存储可选本地或 OSS**。

（前端还有 `/common/download`，若上传直接返回完整 URL，下载接口可暂缓。）

---

## 5. 建议实现顺序

1. 补全 `Dish` / `DishFlavor` Entity、Mapper（可扩展已有精简 Dish）  
2. D1 分页（含 categoryName）  
3. D8 图片上传（本地目录即可先跑通）  
4. D2 / D3 / D4（事务 + flavors）  
5. D6 启停、D5 删除  
6. D7 list（给后续套餐用）

---

## 6. 后端要不要上 MQ / Redis？

### 6.1 结论（针对「菜品」这一期）

| 组件 | 本期菜品是否需要 | 说明 |
|------|------------------|------|
| **MQ（RabbitMQ 等）** | **不需要** | 菜品是同步 CRUD，无异步削峰、无跨服务通知硬需求 |
| **Redis** | **不必须** | 可后置；店铺营业状态、热点分类/菜品缓存、C 端菜单缓存时再加 |
| **对象存储 OSS** | **建议规划，可先本地** | 图片上传必需存储方案；本地磁盘可联调，生产再换 OSS |
| **WebSocket** | **不需要** | 订单来单提醒才常用 |

### 6.2 各组件在本项目中的合理时机

```text
现在（菜品）     ：MySQL + 本地上传或 OSS
稍后（店铺状态） ：Redis 存营业状态（ShopController 注释已提到）
C 端 / 高并发菜单：Redis 缓存菜品分类
来单提醒        ：WebSocket（或 SSE）
下单高峰 / 解耦  ：MQ（订单、支付回调等）——订单模块再考虑
```

**不要为了「看起来正规」在菜品模块强行加 MQ/Redis**，会增加运维与心智负担，收益很小。

### 6.3 图片存储推荐路径

**阶段 A（学习联调）：**  
`POST /admin/common/upload` → 保存到本地目录（如 `uploads/`）→ 返回可访问 URL（静态资源映射或 Nginx）。

**阶段 B（贴近生产）：**  
接入阿里云 OSS / MinIO，上传后返回公网 URL（与种子数据里的 OSS 地址形态一致）。

---

## 7. 与后续模块的衔接

| 做完菜品后 | 下一优先 |
|------------|----------|
| 套餐 Setmeal | 依赖分类 type=2 + dish/list |
| 完善店铺 Shop | 可用 Redis 替换内存状态 |
| 订单 | 再评估 WebSocket / MQ |
| 统计 Report | 依赖订单与菜品数据 |

---

## 8. 验收清单（菜品）

- [ ] 分页：名称 / 分类 / 状态筛选正确，含 `categoryName`
- [ ] 上传图片成功，新增/编辑能带上 image
- [ ] 新增含口味；详情回显口味；修改后口味正确
- [ ] 启售停售单个生效
- [ ] 删除单个与批量；有套餐关联时拒绝（若已实现）
- [ ] `GET /dish/list?categoryId=` 返回数组

---

## 9. 接口一览

| 功能 | Method | Path | 主要入参 |
|------|--------|------|----------|
| 分页 | GET | `/admin/dish/page` | page, pageSize, name?, categoryId?, status? |
| 新增 | POST | `/admin/dish` | name, categoryId, price, image, status?, flavors? |
| 详情 | GET | `/admin/dish/{id}` | path id |
| 修改 | PUT | `/admin/dish` | id + 同新增字段 |
| 删除 | DELETE | `/admin/dish` | query ids |
| 启停 | POST | `/admin/dish/status/{status}` | query id |
| 列表 | GET | `/admin/dish/list` | categoryId? |
| 上传 | POST | `/admin/common/upload` | multipart file |
