# Task 4 Report: 数据源配置与 EmployeeController（含 WebMvc 测试）

## Status

**完成** — Steps 1–6 已执行；Step 7（手动 curl）跳过；Step 8（Commit）按指令跳过。

## 变更文件

| 文件 | 说明 |
|------|------|
| `take-out-admin/.../controller/EmployeeController.java` | `GET /api/employees/{id}` → `Result<EmployeeVO>`（无 password） |
| `take-out-admin/.../controller/EmployeeControllerTest.java` | `@WebMvcTest` + `@MockitoBean EmployeeService` + `@Import(GlobalExceptionHandler)` |
| `take-out-admin/src/main/resources/application.yml` | MySQL 数据源（127.0.0.1:3307 / takeout_rw）+ MyBatis-Plus 配置 |

## TDD Evidence

### RED（Step 1–2）

先写 `EmployeeControllerTest`，再跑：

```powershell
.\mvnw.cmd -pl take-out-admin -am test "-Dtest=EmployeeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

**结果:** BUILD FAILURE — `testCompile` 报错：找不到符号 `EmployeeController`（符合预期：Controller 尚不存在）。

### GREEN（Step 3–5）

实现 `EmployeeController` + 更新 `application.yml` 后重跑同一命令（先 `clean` 以避免陈旧 pojo 产物）：

```powershell
.\mvnw.cmd clean test -pl take-out-admin -am "-Dtest=EmployeeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

**结果:** Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS。

## Step 6: 全量测试

- `docker compose ps`：`take-out-mysql` **healthy**（`0.0.0.0:3307->3306`）
- `.\mvnw.cmd clean test -pl take-out-admin -am` → BUILD SUCCESS
  - `take-out-common` ResultTest: 3
  - `take-out-admin`: DemoControllerTest 1 + EmployeeControllerTest 2 + TakeOutAdminApplicationTests 1 = **4**（Failures/Errors: 0）

## Step 7

未执行 `spring-boot:run` / curl（按 brief：可选；WebMvcTest + 全量 mvn test 已覆盖）。

## Commits

无（跳过 Step 8）。

## Concerns

1. PowerShell 下 `-Dsurefire.failIfNoSpecifiedTests=false` 需加引号，否则会被拆成非法 lifecycle phase。
2. 偶发 `无法访问 Employee / 找不到类文件`：对 reactor 做 `clean` 后恢复（陈旧/不完整 `take-out-pojo` 产物）。
3. Mockito inline agent 警告（JDK 未来行为）；不影响本次测试通过。
