# Review package Task 6
状态：已批准并实现  

## Spec head
```markdown
# take-out 混合多模块骨架设计

日期：2026-08-01  
状态：已批准并实现  

## 背景

当前 `take-out` 为单模块 Spring Boot 工程（启动类 + `DemoController`）。目标是采用「若依式底座 + 业务域可扩展」的混合多模块结构，先搭可运行的最小底座，业务域模块（菜品、订单等）与 API 入口后续再加。

## 目标

- 将工程改为 Maven 多模块父工程。
- 底座模块：`common`、`pojo`、`system`、`framework`、`admin`。
- 轻量通用能力：统一返回、业务异常、全局异常处理、Web 配置占位。
- 仅一个可启动入口：`take-out-admin`。
- 保持后续可平滑增加 `take-out-api`、`module-dish` 等，而不改核心依赖方向。

## 非目标（本次不做）

- 不创建 `take-out-api`。
```

