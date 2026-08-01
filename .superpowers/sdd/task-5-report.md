# Task 5 Report: admin 启动模块迁移与 `/api/hello`

## Status

**DONE_WITH_CONCERNS** — 构建与测试通过，未提交。

## Concerns

Spring Boot 4.1.0 将 `@AutoConfigureMockMvc` 从 `org.springframework.boot.test.autoconfigure.web.servlet` 迁移至 `org.springframework.boot.webmvc.test.autoconfigure`。brief 中的 import 无法编译；已在 `DemoControllerTest` 中使用新包路径，功能与 brief 预期一致。

## TDD 流程

1. **Red** — 先创建 `DemoControllerTest`（brief 原始 import），编译失败（包不存在 + 无启动类/Controller）。
2. **Green** — 实现 `TakeOutAdminApplication`、`DemoController`、`application.yml`、`TakeOutAdminApplicationTests`；修正 SB4 import 后测试通过。
3. **Refactor** — 无额外重构；删除根目录旧 `src/` 树。

## 新增文件

| 文件 | 说明 |
|------|------|
| `take-out-admin/src/main/java/com/sky/takeout/admin/TakeOutAdminApplication.java` | 启动类，`scanBasePackages = "com.sky.takeout"` |
| `take-out-admin/src/main/java/com/sky/takeout/admin/controller/DemoController.java` | `GET /api/hello` → `Result.success("Hello, World!")` |
| `take-out-admin/src/main/resources/application.yml` | `spring.application.name: take-out-admin` |
| `take-out-admin/src/test/java/com/sky/takeout/admin/TakeOutAdminApplicationTests.java` | 上下文加载测试 |
| `take-out-admin/src/test/java/com/sky/takeout/admin/controller/DemoControllerTest.java` | MockMvc 集成测试 |

## 删除文件

根目录旧单模块遗留（整棵 `take-out/src/` 已移除）：

- `src/main/java/com/sky/take_out/TakeOutApplication.java`
- `src/main/java/com/sky/take_out/controller/DemoController.java`
- `src/main/resources/application.properties`
- `src/test/java/com/sky/take_out/TakeOutApplicationTests.java`

`take-out-*/src` 未动。

## 接口行为

- **Endpoint:** `GET /api/hello`
- **Response:** `{"code":1,"msg":"success","data":"Hello, World!"}`
- **依赖:** `Result.success(T)` from `take-out-common`；framework 组件（`GlobalExceptionHandler`、`WebMvcConfig`）经 `scanBasePackages = "com.sky.takeout"` 被扫描注册。

## 验证

```bash
.\mvnw.cmd clean package -pl take-out-admin -am
```

**结果:** BUILD SUCCESS

| 测试类 | 结果 |
|--------|------|
| `DemoControllerTest.hello_returnsUnifiedResult` | PASS |
| `TakeOutAdminApplicationTests.contextLoads` | PASS |
| `ResultTest`（common，-am 连带） | 3 PASS |

**产物:** `take-out-admin/target/take-out-admin-0.0.1-SNAPSHOT.jar`

## 未执行

- Git commit（按指令跳过 Step 8）
