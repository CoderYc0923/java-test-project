### Task 1: 父 POM 与五子模块脚手架

**Files:**
- Modify: `pom.xml`
- Create: `take-out-common/pom.xml`
- Create: `take-out-pojo/pom.xml`
- Create: `take-out-system/pom.xml`
- Create: `take-out-framework/pom.xml`
- Create: `take-out-admin/pom.xml`

**Interfaces:**
- Consumes: 无
- Produces: 可解析的多模块 Maven 反应堆；内部坐标 `com.sky:take-out-*:0.0.1-SNAPSHOT`

- [ ] **Step 1: 重写根 `pom.xml` 为父工程**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.sky</groupId>
    <artifactId>take-out</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>take-out</name>

    <modules>
        <module>take-out-common</module>
        <module>take-out-pojo</module>
        <module>take-out-system</module>
        <module>take-out-framework</module>
        <module>take-out-admin</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <take-out.version>0.0.1-SNAPSHOT</take-out.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-common</artifactId>
                <version>${take-out.version}</version>
            </dependency>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-pojo</artifactId>
                <version>${take-out.version}</version>
            </dependency>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-system</artifactId>
                <version>${take-out.version}</version>
            </dependency>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-framework</artifactId>
                <version>${take-out.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: 创建 `take-out-common/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-common</artifactId>
    <name>take-out-common</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 创建 `take-out-pojo/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-pojo</artifactId>
    <name>take-out-pojo</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-common</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建 `take-out-system/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-system</artifactId>
    <name>take-out-system</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-pojo</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: 创建 `take-out-framework/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-framework</artifactId>
    <name>take-out-framework</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-system</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 6: 创建 `take-out-admin/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-admin</artifactId>
    <name>take-out-admin</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-framework</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 7: 校验反应堆可解析**

Run: `mvn -q -N validate` 然后 `mvn -q validate`  
Expected: BUILD SUCCESS（子模块尚无源码也可 validate）

- [ ] **Step 8: Commit（仅用户授权时）**

```bash
git add pom.xml take-out-common/pom.xml take-out-pojo/pom.xml take-out-system/pom.xml take-out-framework/pom.xml take-out-admin/pom.xml
git commit -m "build: scaffold hybrid multi-module parent and child POMs"
```

---
