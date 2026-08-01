### Task 6: 规格状态回写与最终核对

**Files:**
- Modify: `docs/superpowers/specs/2026-08-01-take-out-hybrid-modules-design.md`（状态改为已实现/已批准落地）

- [ ] **Step 1: 将设计文档状态从「待用户审阅」改为「已批准并实现」**

- [ ] **Step 2: 最终命令复核**

Run: `mvn clean test -pl take-out-admin -am`  
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit docs（仅用户授权时）**

```bash
git add docs/superpowers/specs/2026-08-01-take-out-hybrid-modules-design.md
git commit -m "docs: mark hybrid modules design as implemented"
```

---

## Spec Coverage Checklist

| 规格要求 | 对应任务 |
|----------|----------|
| 父工程 + 五子模块 | Task 1 |
| Result / ErrorCode / BusinessException | Task 2 |
| pojo entity/dto/vo 包 | Task 3 |
| system service/mapper 占位 | Task 3 |
| GlobalExceptionHandler + WebMvcConfig | Task 4 |
| admin 启动 + DemoController + yml | Task 5 |
| 删除根 `src/` | Task 5 |
| `mvn clean package -pl take-out-admin -am` | Task 5 / 6 |
| 不引入 MyBatis/Redis/Security/api/业务域 | 全任务遵守 |

## Self-Review Notes

- 无 TBD/占位步骤；类签名在 Task 2/4/5 一致（`ErrorCode.SUCCESS=1`、`Result.success/error`、`BusinessException.getCode()`）。
- `@SpringBootApplication(scanBasePackages = "com.sky.takeout")` 确保 framework 中 `@RestControllerAdvice` / `@Configuration` 生效。
- common 使用 `spring-boot-starter-test`（非 webmvc-test），避免无意义 Web 测试依赖。
