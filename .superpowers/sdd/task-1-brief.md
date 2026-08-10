### Task 1: 鑴氭墜鏋?鈥?妯″潡鍙惎鍔?

**Files:**
- Modify: `take-out/pom.xml`
- Create: `take-out-mock-wechat/pom.xml`
- Create: `take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/MockWechatApplication.java`
- Create: `take-out-mock-wechat/src/main/resources/application.yml`

**Interfaces:**
- Produces: 鍙墽琛屾ā鍧?`take-out-mock-wechat`锛屼富绫?`MockWechatApplication`

- [ ] **Step 1: 鐖?POM 澧炲姞 module**

鍦?`take-out/pom.xml` 鐨?`<modules>` 涓拷鍔狅細

```xml
<module>take-out-mock-wechat</module>
```

锛堝彲閫夛級鍦?`<dependencyManagement>` 澧炲姞锛?

```xml
<dependency>
    <groupId>com.sky</groupId>
    <artifactId>take-out-mock-wechat</artifactId>
    <version>${take-out.version}</version>
</dependency>
```

- [ ] **Step 2: 鍐?`take-out-mock-wechat/pom.xml`**

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
    <artifactId>take-out-mock-wechat</artifactId>
    <name>take-out-mock-wechat</name>
    <description>鍋囧井淇?V3 娌欑锛堟暀瀛︼級</description>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
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

鑻?`mockwebserver` 鐗堟湰闇€鏄惧紡鎸囧畾锛屼笌 Spring Boot BOM 绠＄悊鍐茬獊鏃跺啀鍦ㄧ埗 POM 鎴栨湰妯″潡鍔?version锛涗紭鍏堣 BOM 绠＄悊銆?

- [ ] **Step 3: 鍚姩绫讳笌閰嶇疆**

`MockWechatApplication.java`:

```java
package com.sky.takeout.mockwechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockWechatApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockWechatApplication.class, args);
    }
}
```

`application.yml`:

```yaml
server:
  port: 9090

mock-wechat:
  merchant-notify-secret: change-me
  notify-max-retries: 2
  notify-retry-delay-ms: 500
```

- [ ] **Step 4: 缂栬瘧楠岃瘉鍙В鏋愭ā鍧?*

Run:

```bash
mvn -pl take-out-mock-wechat -am -DskipTests package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add take-out/pom.xml take-out-mock-wechat/pom.xml \
  take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/MockWechatApplication.java \
  take-out-mock-wechat/src/main/resources/application.yml
git commit -m "chore: scaffold take-out-mock-wechat module"
```

---

