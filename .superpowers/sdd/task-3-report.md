# Task 3 Report: Mapper、Service 与 MapperScan

## Status

**完成** — Steps 1–5 已执行，Step 6（Commit）已跳过。

## 创建的文件

| 文件 | 说明 |
|------|------|
| `take-out-system/.../mapper/EmployeeMapper.java` | `BaseMapper<Employee>` + `@Mapper` |
| `take-out-system/.../service/EmployeeService.java` | 接口 `Employee getById(Long id)` |
| `take-out-system/.../service/impl/EmployeeServiceImpl.java` | 不存在时抛 `BusinessException(ErrorCode.ERROR, "员工不存在")` |
| `take-out-framework/.../config/MybatisPlusConfig.java` | `@MapperScan("com.sky.takeout.system.mapper")` |

## 编译

```powershell
.\mvnw.cmd -q -pl take-out-framework -am compile
```

**结果:** BUILD SUCCESS（exit code 0）

## 依赖说明

- `@Service` 编译通过，**未**向 `take-out-system/pom.xml` 额外添加 `spring-context`；`mybatis-plus-spring-boot4-starter` 传递依赖已足够。
- `BusinessException` / `ErrorCode` 经 `take-out-pojo → take-out-common` 传递可用。

## Commits

无（按指令跳过 Step 6）。

## Concerns

无阻塞项。后续业务模块新增 Mapper 时，需在 `MybatisPlusConfig` 的 `@MapperScan` 中追加包路径（brief 建议使用 `value` 数组）。
