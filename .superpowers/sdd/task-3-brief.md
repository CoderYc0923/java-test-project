### Task 3: Mapper銆丼ervice 涓?MapperScan

**Files:**
- Create: `take-out-system/src/main/java/com/sky/takeout/system/mapper/EmployeeMapper.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java`
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/config/MybatisPlusConfig.java`

**Interfaces:**
- Consumes: `Employee`锛沗BaseMapper`锛沗BusinessException` / `ErrorCode`
- Produces:
  - `EmployeeMapper extends BaseMapper<Employee>`
  - `EmployeeService#Employee getById(Long id)` 鈥?涓嶅瓨鍦ㄦ椂鎶?`BusinessException(ErrorCode.ERROR, "鍛樺伐涓嶅瓨鍦?)`
  - `MybatisPlusConfig` 甯?`@MapperScan("com.sky.takeout.system.mapper")`

- [ ] **Step 1: 鍒涘缓 `EmployeeMapper.java`**

```java
package com.sky.takeout.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.takeout.pojo.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
```

- [ ] **Step 2: 鍒涘缓 `EmployeeService.java`**

```java
package com.sky.takeout.system.service;

import com.sky.takeout.pojo.entity.Employee;

public interface EmployeeService {
    Employee getById(Long id);
}
```

- [ ] **Step 3: 鍒涘缓 `EmployeeServiceImpl.java`**

```java
package com.sky.takeout.system.service.impl;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.service.EmployeeService;
import org.springframework.stereotype.Service;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.ERROR, "鍛樺伐涓嶅瓨鍦?);
        }
        return employee;
    }
}
```

璇存槑锛歚system` 妯″潡闇€鑳界紪璇戝埌 Spring 鐨?`@Service`銆傝嫢褰撳墠 `take-out-system/pom.xml` 灏氭棤 spring-context锛岄€氳繃 `mybatis-plus-spring-boot4-starter` 浼犻€掍緷璧栭€氬父宸茶冻澶燂紱鑻ョ紪璇戞姤鎵句笉鍒?`@Service`锛屽湪 system 鐨?pom 澧炲姞锛?

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
```

- [ ] **Step 4: 鍒涘缓 `MybatisPlusConfig.java`**

```java
package com.sky.takeout.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sky.takeout.system.mapper")
public class MybatisPlusConfig {
}
```

璇存槑锛氬寘璺緞鐢ㄦ槑纭寘鍚嶏紙涓嶇敤 `**`锛夛紝鍚庣画涓氬姟妯″潡鍐嶅線 `@MapperScan` 鐨?`value` 鏁扮粍杩藉姞銆?

- [ ] **Step 5: 缂栬瘧 system + framework**

Run:

```powershell
.\mvnw.cmd -q -pl take-out-framework -am compile
```

Expected: BUILD SUCCESS銆?

- [ ] **Step 6: Commit锛堜粎褰撶敤鎴疯姹傦級**

```bash
git add take-out-system/src/main/java/com/sky/takeout/system/mapper/EmployeeMapper.java take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java take-out-framework/src/main/java/com/sky/takeout/framework/config/MybatisPlusConfig.java take-out-system/pom.xml
git commit -m "feat: add EmployeeMapper, EmployeeService, and MapperScan"
```

---

