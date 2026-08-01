# Task 4 Report: framework — 全局异常与 WebMvc 占位

## Status

**完成** — 编译通过，未提交。

## 新增文件

| 文件 | 说明 |
|------|------|
| `take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java` | `@RestControllerAdvice` 全局异常处理 |
| `take-out-framework/src/main/java/com/sky/takeout/framework/config/WebMvcConfig.java` | `WebMvcConfigurer` 占位配置 |

## 实现要点

### GlobalExceptionHandler

- `BusinessException` → `Result.error(ex.getCode(), ex.getMessage())`
- 通用 `Exception` → `Result.error("系统异常，请稍后重试")`
- 依赖 `take-out-common` 中的 `Result`、`BusinessException`（经 `take-out-system` 传递）

### WebMvcConfig

- `@Configuration` + 实现 `WebMvcConfigurer`，空方法体，供后续扩展拦截器/CORS 等

## 验证

```bash
mvn -pl take-out-framework -am compile
```

**结果:** BUILD SUCCESS（编译 2 个源文件）

## 未执行

- Git commit（按 brief 要求，仅用户授权时提交）
