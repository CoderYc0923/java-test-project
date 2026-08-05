# 分类管理模块需求文档

日期：2026-08-05  
来源：前端工程 `project-rjwm-admin-vue-ts`（分类管理页 + 菜品/套餐对分类下拉的依赖）  
后端路径前缀：经代理 `/api` → `/admin`，即 **`/admin/category`**  
统一响应：`Result`（成功 `code = 1`，失败 `code != 1` + `msg`）

---

## 1. 模块概述

分类用于给**菜品**和**套餐**分组。管理端「分类管理」页维护分类；菜品/套餐新增编辑页通过下拉选择分类。

| 类型 type | 含义 | 使用场景 |
|-----------|------|----------|
| `1` | 菜品分类 | 菜品管理筛选/新增时下拉 |
| `2` | 套餐分类 | 套餐管理筛选/新增时下拉 |

状态：`0` 禁用，`1` 启用。

---

## 2. 功能点清单

| 序号 | 功能 | 说明 | 对应前端入口 |
|------|------|------|----------------|
| F1 | 分页条件查询 | 按名称、类型筛选，分页展示 | 分类管理页查询/分页 |
| F2 | 新增菜品分类 | type 固定为 1 | 「+ 新增菜品分类」 |
| F3 | 新增套餐分类 | type 固定为 2 | 「+ 新增套餐分类」 |
| F4 | 修改分类 | 改名称、排序（不改 type） | 行内「修改」 |
| F5 | 删除分类 | 按 id 删除 | 行内「删除」 |
| F6 | 启用/禁用 | 切换 status | 行内「启用/禁用」 |
| F7 | 分类列表（下拉） | 按 type 查列表，供菜品/套餐页使用 | 非本页，其它模块调用 |

本期管理端页面本身不调用 F7，但菜品/套餐模块已依赖，**建议与分类 CRUD 同期实现**。

---

## 3. 数据模型（对齐 `sky.sql` 表 `category`）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK，自增 | 主键 |
| type | int | | 1 菜品分类 / 2 套餐分类 |
| name | varchar(32) | NOT NULL，**唯一** `idx_category_name` | 分类名称 |
| sort | int | NOT NULL，默认 0 | 排序，越小/越大按产品约定展示（前端只存数字） |
| status | int | | 0 禁用 / 1 启用 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |
| create_user | bigint | | 创建人 |
| update_user | bigint | | 修改人 |

**列表展示字段（前端表格）：** `name`、`type`、`sort`、`status`、`updateTime`（另需 `id` 做操作）。

---

## 4. 接口详细说明

> 下列路径均为后端真实路径（已含 `/admin`）。前端写 `/category/...`，由 `vue.config.js` 代理改写。

### 4.1 F1 分页查询

- **方法/路径：** `GET /admin/category/page`
- **鉴权：** 需要登录 JWT
- **Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | number | 是 | 页码，从 1 起，默认前端传 1 |
| pageSize | number | 是 | 每页条数，可选 10/20/30/40 |
| name | string | 否 | 分类名称，有值则模糊查询 |
| type | number | 否 | `1` 或 `2`；不传则查全部类型 |

- **成功响应 `data` 结构：**

```json
{
  "records": [
    {
      "id": 11,
      "type": 1,
      "name": "酒水饮料",
      "sort": 10,
      "status": 1,
      "createTime": "2022-06-09 22:09:18",
      "updateTime": "2022-06-09 22:09:18",
      "createUser": 1,
      "updateUser": 1
    }
  ],
  "total": 11
}
```

（与员工分页一致：MyBatis-Plus `IPage` 的 `records` + `total` 即可。）

- **前端成功判断：** `code === 1`，取 `data.records`、`data.total`。

---

### 4.2 F2 / F3 新增分类

- **方法/路径：** `POST /admin/category`
- **鉴权：** 需要登录 JWT
- **Body（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 分类名称，2～20 字符；前端限制中文/字母（后端建议再校验长度、唯一） |
| type | number/string | 是 | 新增菜品分类传 `"1"`/`1`；套餐分类传 `"2"`/`2` |
| sort | number/string | 是 | 排序，0～99 整数 |

- **服务端补充字段（前端不传）：**
  - `status`：默认 **1（启用）**
  - `createTime` / `updateTime` / `createUser` / `updateUser`：审计填充

- **成功：** `code = 1`（可不返回 data）
- **失败示例：**
  - 名称已存在（唯一索引）→ 业务错误，如「分类名称已存在」
  - 参数非法 → 400 / 业务错误

- **前端行为：** 成功提示后刷新列表；支持「保存并继续添加」（不关弹窗，再次新增）。

---

### 4.3 F4 修改分类

- **方法/路径：** `PUT /admin/category`
- **鉴权：** 需要登录 JWT
- **Body（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | number | 是 | 分类 id |
| name | string | 是 | 新名称 |
| sort | number/string | 是 | 新排序 |

- **注意：** 前端**不传 type**，修改时**不要改类型**。
- **服务端：** 校验存在；名称唯一（排除自身）；更新审计字段。

---

### 4.4 F5 删除分类

- **方法/路径：** `DELETE /admin/category`
- **鉴权：** 需要登录 JWT
- **Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | number/string | 是 | 分类 id（前端 `params: { id }`） |

- **成功：** `code = 1`
- **建议业务规则（前端未写死，后端应补）：**
  - 若该分类下仍有菜品或套餐引用，**禁止删除**，返回明确文案（如「当前分类下关联了菜品/套餐，不能删除」）。

---

### 4.5 F6 启用 / 禁用

- **方法/路径（前端现状）：** `POST /admin/category/status/{status}`
- **Query：** `id` = 分类 id
- **Path：** `status` = 目标状态 `0` 或 `1`

示例：`POST /admin/category/status/0?id=11`

- **说明：** 与「改前」员工接口风格一致。若希望与员工模块新约定对齐，可后续改为：

  `POST /admin/category/{id}/status` + Body `{ "status": 0 }`  

  并同步改 `src/api/category.ts`（**当前前端未改，后端先按现状实现即可**）。

---

### 4.6 F7 按类型查询分类列表（下拉）

- **方法/路径：** `GET /admin/category/list`
- **鉴权：** 需要登录 JWT
- **Query：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | number | 建议必填 | `1` 菜品分类 / `2` 套餐分类 |
| page / pageSize | — | 否 | 个别调用会带，**可忽略**，本接口返回**全部匹配列表**即可 |

- **成功响应 `data`：** **数组**（不是分页对象），例如：

```json
[
  { "id": 11, "type": 1, "name": "酒水饮料", "sort": 10, "status": 1, ... }
]
```

- **建议过滤：** 下拉一般只返回 **启用**（`status = 1`）的分类；若前端未区分，至少按 `type` + `sort` 排序返回。
- **调用方：**
  - 菜品：`type=1`
  - 套餐：`type=2`

---

## 5. 前端校验规则（后端建议对齐）

| 字段 | 规则 |
|------|------|
| name | 必填；长度 2～20；字符为中文或字母（前端正则 `^[A-Za-z\u4e00-\u9fa5]+$`） |
| sort | 必填；非负整数；0～99 |
| type | 新增时必为 1 或 2 |
| name 唯一 | 库表唯一索引；冲突时友好提示 |

---

## 6. 页面交互摘要

1. 进入「分类管理」→ 调 F1 加载第一页。  
2. 名称/类型筛选 → 再调 F1。  
3. 新增菜品/套餐分类 → 弹窗填 name、sort → F2/F3。  
4. 修改 → 弹窗回填 name、sort（带 id）→ F4。  
5. 删除 → 确认框 → F5。  
6. 启用/禁用 → 确认框 → F6 → 刷新列表。  
7. 分页：`counts > 10` 才显示分页条（实现接口时仍应正确返回 total）。

---

## 7. 建议后端实现顺序（对照员工模块）

1. Entity / Mapper（`category` 表）+ 审计字段填充复用  
2. F1 分页（复用已有分页插件）  
3. F2/F3 新增、F4 修改（唯一名校验）  
4. F6 启停  
5. F5 删除（含关联校验，若 dish/setmeal 表已存在）  
6. F7 list 下拉  

模块边界建议与员工一致：

- Controller：`take-out-admin`  
- Service/Mapper：`take-out-system`  
- DTO/VO/Entity：`take-out-pojo`  

---

## 8. 接口一览表

| 功能 | Method | Path | 主要入参 | 出参要点 |
|------|--------|------|----------|----------|
| 分页 | GET | `/admin/category/page` | page, pageSize, name?, type? | data.records, data.total |
| 新增 | POST | `/admin/category` | name, type, sort | code=1 |
| 修改 | PUT | `/admin/category` | id, name, sort | code=1 |
| 删除 | DELETE | `/admin/category` | query id | code=1 |
| 启停 | POST | `/admin/category/status/{status}` | path status, query id | code=1 |
| 列表 | GET | `/admin/category/list` | type | data 为数组 |

---

## 9. 非本期 / 注意

- 分类管理页**没有**单独「按 id 查询详情」接口；修改靠列表行数据回填。  
- 前端启停 API 函数名误写为 `enableOrDisableEmployee`，不影响路径。  
- 删除关联校验依赖菜品/套餐表；若表尚未接入，可先做物理删除，后续补校验。  
- 路由注意：`/page`、`/list`、`/status/{status}` 与 `/{id}` 并存时，字面路径优先注册，避免被 path variable 误匹配（本模块前端未使用 `GET /category/{id}`）。

---

## 10. 验收清单

- [ ] 分页：无条件、按 name、按 type、组合条件正确  
- [ ] 新增菜品/套餐分类成功，默认启用，列表可见  
- [ ] 名称重复有明确错误提示  
- [ ] 修改只变更 name/sort，type 不变  
- [ ] 启停后列表状态文案正确  
- [ ] 删除成功；有关联数据时拒绝（若已实现校验）  
- [ ] `GET /list?type=1` / `type=2` 返回数组，菜品/套餐下拉可用  
