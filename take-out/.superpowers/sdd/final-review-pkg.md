# Final whole-branch review package
Branch: feat/mybatis-plus-persistence
Base: 1ffdab1
Scope: MyBatis-Plus persistence (exclude unrelated docker dirty files)

## Status
## feat/mybatis-plus-persistence
 M ../.superpowers/sdd/progress.md
 M ../.superpowers/sdd/task-1-brief.md
 M ../.superpowers/sdd/task-1-report.md
 M ../.superpowers/sdd/task-2-brief.md
 M ../.superpowers/sdd/task-2-report.md
 M ../.superpowers/sdd/task-3-brief.md
 M ../.superpowers/sdd/task-3-report.md
 M ../.superpowers/sdd/task-4-brief.md
 M ../.superpowers/sdd/task-4-report.md
 M docker-command.md
 M docker-compose.yml
 M pom.xml
 M take-out-admin/src/main/resources/application.yml
 M take-out-pojo/pom.xml
 M take-out-system/pom.xml
?? ../.superpowers/sdd/task-1-review-pkg.md
?? ../.superpowers/sdd/task-2-review-pkg.md
?? ../.superpowers/sdd/task-3-review-pkg.md
?? ../.superpowers/sdd/task-4-review-pkg.md
?? ../.vscode/
?? .superpowers/
?? docker/
?? docs/superpowers/plans/2026-08-03-mybatis-plus-persistence.md
?? take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java
?? take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java
?? take-out-framework/src/main/java/com/sky/takeout/framework/config/MybatisPlusConfig.java
?? take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java
?? take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/EmployeeVO.java
?? take-out-system/src/main/java/com/sky/takeout/system/mapper/EmployeeMapper.java
?? take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java
?? take-out-system/src/main/java/com/sky/takeout/system/service/impl/

## Diff stat

 take-out/pom.xml                                           |  7 +++++++
 take-out/take-out-admin/src/main/resources/application.yml | 12 ++++++++++++
 take-out/take-out-pojo/pom.xml                             |  9 +++++++++
 take-out/take-out-system/pom.xml                           |  9 +++++++++
 4 files changed, 37 insertions(+)

## Untracked

docs/superpowers/plans/2026-08-03-mybatis-plus-persistence.md
take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java
take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java
take-out-framework/src/main/java/com/sky/takeout/framework/config/MybatisPlusConfig.java
take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java
take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/EmployeeVO.java
take-out-system/src/main/java/com/sky/takeout/system/mapper/EmployeeMapper.java
take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java
take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java

## Diffs

diff --git a/take-out/pom.xml b/take-out/pom.xml
index 805f4e6..d42c6e0 100644
--- a/take-out/pom.xml
+++ b/take-out/pom.xml
@@ -30,10 +30,17 @@
         <take-out.version>0.0.1-SNAPSHOT</take-out.version>
     </properties>
 
     <dependencyManagement>
         <dependencies>
+            <dependency>
+                <groupId>com.baomidou</groupId>
+                <artifactId>mybatis-plus-bom</artifactId>
+                <version>3.5.17</version>
+                <type>pom</type>
+                <scope>import</scope>
+            </dependency>
             <dependency>
                 <groupId>com.sky</groupId>
                 <artifactId>take-out-common</artifactId>
                 <version>${take-out.version}</version>
             </dependency>
diff --git a/take-out/take-out-admin/src/main/resources/application.yml b/take-out/take-out-admin/src/main/resources/application.yml
index 56782a9..6ad3232 100644
--- a/take-out/take-out-admin/src/main/resources/application.yml
+++ b/take-out/take-out-admin/src/main/resources/application.yml
@@ -1,3 +1,15 @@
 spring:
   application:
     name: take-out-admin
+  datasource:
+    url: jdbc:mysql://127.0.0.1:3307/take_out?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
+    username: takeout_rw
+    password: TakeoutRw@123
+    driver-class-name: com.mysql.cj.jdbc.Driver
+
+mybatis-plus:
+  configuration:
+    map-underscore-to-camel-case: true
+  global-config:
+    db-config:
+      id-type: auto
diff --git a/take-out/take-out-pojo/pom.xml b/take-out/take-out-pojo/pom.xml
index 1586925..b5bb437 100644
--- a/take-out/take-out-pojo/pom.xml
+++ b/take-out/take-out-pojo/pom.xml
@@ -13,7 +13,16 @@
     <dependencies>
         <dependency>
             <groupId>com.sky</groupId>
             <artifactId>take-out-common</artifactId>
         </dependency>
+        <dependency>
+            <groupId>org.projectlombok</groupId>
+            <artifactId>lombok</artifactId>
+            <optional>true</optional>
+        </dependency>
+        <dependency>
+            <groupId>com.baomidou</groupId>
+            <artifactId>mybatis-plus-annotation</artifactId>
+        </dependency>
     </dependencies>
 </project>
diff --git a/take-out/take-out-system/pom.xml b/take-out/take-out-system/pom.xml
index 438de64..14a5d01 100644
--- a/take-out/take-out-system/pom.xml
+++ b/take-out/take-out-system/pom.xml
@@ -13,7 +13,16 @@
     <dependencies>
         <dependency>
             <groupId>com.sky</groupId>
             <artifactId>take-out-pojo</artifactId>
         </dependency>
+        <dependency>
+            <groupId>com.baomidou</groupId>
+            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
+        </dependency>
+        <dependency>
+            <groupId>com.mysql</groupId>
+            <artifactId>mysql-connector-j</artifactId>
+            <scope>runtime</scope>
+        </dependency>
     </dependencies>
 </project>

===== NEW FILE: take-out-pojo\src\main\java\com\sky\takeout\pojo\entity\Employee.java =====

package com.sky.takeout.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("employee")
public class Employee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String username;
    private String password;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}

===== NEW FILE: take-out-pojo\src\main\java\com\sky\takeout\pojo\vo\EmployeeVO.java =====

package com.sky.takeout.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmployeeVO {
    private Long id;
    private String name;
    private String username;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}

===== NEW FILE: take-out-system\src\main\java\com\sky\takeout\system\mapper\EmployeeMapper.java =====

package com.sky.takeout.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.takeout.pojo.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}

===== NEW FILE: take-out-system\src\main\java\com\sky\takeout\system\service\EmployeeService.java =====

package com.sky.takeout.system.service;

import com.sky.takeout.pojo.entity.Employee;

public interface EmployeeService {
    Employee getById(Long id);
}

===== NEW FILE: take-out-system\src\main\java\com\sky\takeout\system\service\impl\EmployeeServiceImpl.java =====

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
            throw new BusinessException(ErrorCode.ERROR, "员工不存在");
        }
        return employee;
    }
}

===== NEW FILE: take-out-framework\src\main\java\com\sky\takeout\framework\config\MybatisPlusConfig.java =====

package com.sky.takeout.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sky.takeout.system.mapper")
public class MybatisPlusConfig {
}

===== NEW FILE: take-out-admin\src\main\java\com\sky\takeout\admin\controller\EmployeeController.java =====

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

===== NEW FILE: take-out-admin\src\test\java\com\sky\takeout\admin\controller\EmployeeControllerTest.java =====

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
        employee.setName("管理员");
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
                .thenThrow(new BusinessException(ErrorCode.ERROR, "员工不存在"));

        mockMvc.perform(get("/api/employees/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR))
                .andExpect(jsonPath("$.msg").value("员工不存在"));
    }
}
