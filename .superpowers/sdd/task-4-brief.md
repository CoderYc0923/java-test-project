### Task 4: 鏁版嵁婧愰厤缃笌 EmployeeController锛堝惈 WebMvc 娴嬭瘯锛?

**Files:**
- Modify: `take-out-admin/src/main/resources/application.yml`
- Create: `take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java`
- Test: `take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java`

**Interfaces:**
- Consumes: `EmployeeService#getById(Long)`锛沗Employee`锛沗EmployeeVO`锛沗Result`
- Produces: `GET /api/employees/{id}` 鈫?`Result<EmployeeVO>`锛堟棤 password锛?

- [ ] **Step 1: 鍏堝啓澶辫触鐨?`EmployeeControllerTest`锛堜笉杩炲簱锛?*

```java
package com.sky.takeout.admin.controller;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.framework.web.GlobalExceptionHandler;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmployeeController.class)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void getById_returnsEmployeeWithoutPassword() throws Exception {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("绠＄悊鍛?);
        employee.setUsername("admin");
        employee.setPassword("123456");
        employee.setPhone("13812312312");
        employee.setSex("1");
        employee.setIdNumber("110101199001010047");
        employee.setStatus(1);
        employee.setCreateTime(LocalDateTime.of(2022, 2, 15, 15, 51, 20));
        employee.setUpdateTime(LocalDateTime.of(2022, 2, 17, 9, 16, 20));
        employee.setCreateUser(10L);
        employee.setUpdateUser(1L);
        when(employeeService.getById(1L)).thenReturn(employee);

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void getById_whenMissing_returnsBusinessError() throws Exception {
        when(employeeService.getById(99999L))
                .thenThrow(new BusinessException(ErrorCode.ERROR, "鍛樺伐涓嶅瓨鍦?));

        mockMvc.perform(get("/api/employees/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR))
                .andExpect(jsonPath("$.msg").value("鍛樺伐涓嶅瓨鍦?));
    }
}
```

- [ ] **Step 2: 杩愯娴嬭瘯锛岀‘璁ゅ洜缂哄皯 Controller 鑰屽け璐?*

Run:

```powershell
.\mvnw.cmd -pl take-out-admin -am test -Dtest=EmployeeControllerTest
```

Expected: FAIL锛堟壘涓嶅埌 `EmployeeController` 鎴栦笂涓嬫枃鏃犳硶鍔犺浇璇ョ被锛夈€?

- [ ] **Step 3: 瀹炵幇 `EmployeeController.java`**

```java
package com.sky.takeout.admin.controller;

import com.sky.takeout.common.result.Result;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.pojo.vo.EmployeeVO;
import com.sky.takeout.system.service.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/{id}")
    public Result<EmployeeVO> getById(@PathVariable Long id) {
        Employee employee = employeeService.getById(id);
        return Result.success(toVO(employee));
    }

    private static EmployeeVO toVO(Employee employee) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setUsername(employee.getUsername());
        vo.setPhone(employee.getPhone());
        vo.setSex(employee.getSex());
        vo.setIdNumber(employee.getIdNumber());
        vo.setStatus(employee.getStatus());
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        vo.setCreateUser(employee.getCreateUser());
        vo.setUpdateUser(employee.getUpdateUser());
        return vo;
    }
}
```

- [ ] **Step 4: 鏇存柊 `application.yml`**

瀹屾暣鏂囦欢鍐呭锛?

```yaml
spring:
  application:
    name: take-out-admin
  datasource:
    url: jdbc:mysql://127.0.0.1:3307/take_out?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: takeout_rw
    password: TakeoutRw@123
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

- [ ] **Step 5: 鍐嶈窇 `EmployeeControllerTest`锛屾湡鏈涢€氳繃**

Run:

```powershell
.\mvnw.cmd -pl take-out-admin -am test -Dtest=EmployeeControllerTest
```

Expected: Tests run: 2, Failures: 0, Errors: 0銆?

- [ ] **Step 6: 纭繚 Docker MySQL 宸插惎鍔ㄥ悗璺?admin 鍏ㄩ噺娴嬭瘯**

Run:

```powershell
docker compose ps
.\mvnw.cmd clean test -pl take-out-admin -am
```

Expected: `take-out-mysql` 涓?healthy锛涘叏閮ㄦ祴璇?PASS锛堝惈鏃㈡湁 `DemoControllerTest` / `contextLoads`锛夈€?

- [ ] **Step 7: 鎵嬪伐楠岃瘉锛堝彲閫変絾鎺ㄨ崘锛?*

```powershell
.\mvnw.cmd spring-boot:run -pl take-out-admin
```

鍙﹀紑缁堢锛?

```powershell
curl http://localhost:8080/api/employees/1
curl http://localhost:8080/api/employees/99999
```

Expected: 鍓嶈€?JSON 鍚?`"username":"admin"` 涓旀棤 `password`锛涘悗鑰?`code` 涓洪敊璇爜銆乣msg` 涓恒€屽憳宸ヤ笉瀛樺湪銆嶃€?

- [ ] **Step 8: Commit锛堜粎褰撶敤鎴疯姹傦級**

```bash
git add take-out-admin/src/main/resources/application.yml take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java
git commit -m "feat: add employee read API with MyBatis-Plus datasource"
```

---

## Spec Coverage Checklist

| Spec 瑕佹眰 | 瀵瑰簲 Task |
|-----------|-----------|
| MyBatis-Plus Boot4 starter 3.5.17 | Task 1 |
| Lombok | Task 1鈥? |
| Employee + EmployeeVO锛堟棤 password锛?| Task 2 |
| Mapper / Service / 鏌ユ棤鎶?BusinessException | Task 3 |
| MapperScan 鍦?framework | Task 3 |
| admin yml 鏁版嵁婧?| Task 4 |
| GET /api/employees/{id} | Task 4 |
| EmployeeControllerTest mock銆佷笉杩炲簱 | Task 4 |
| 鍏ㄩ噺娴嬭瘯绾﹀畾闇€ Docker | Task 4 Step 6 |
| 闈炵洰鏍囷紙鍒嗛〉/CRUD/閴存潈绛夛級 | 鏈垪鍏ヤ换浣?Task |

---

## Self-Review Notes

- 鏃?TBD/鍗犱綅姝ラ锛涚鍚嶇粺涓€涓?`Employee getById(Long id)`锛孋ontroller 鍐呮墜宸ユ槧灏?VO銆?
- `@MapperScan` 浣跨敤 `com.sky.takeout.system.mapper`锛堟瘮 spec 涓殑 `**` 鏇寸ǔ濡ワ紝璇箟绛変环浜庡綋鍓嶈寖鍥达級銆?
- `pojo` 浠呬緷璧?`mybatis-plus-annotation`锛岄伩鍏嶆妸 starter 鎷夎繘妯″瀷灞傘€?
