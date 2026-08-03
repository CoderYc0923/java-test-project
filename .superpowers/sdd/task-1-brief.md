### Task 1: 鐖?POM 涓庡瓙妯″潡渚濊禆

**Files:**
- Modify: `pom.xml`
- Modify: `take-out-pojo/pom.xml`
- Modify: `take-out-system/pom.xml`

**Interfaces:**
- Consumes: 鏃?
- Produces: 鍙嶅簲鍫嗗彲瑙ｆ瀽 `mybatis-plus-spring-boot4-starter:3.5.17`銆乣mybatis-plus-annotation`銆丩ombok銆乣mysql-connector-j`

- [ ] **Step 1: 鍦ㄧ埗 `pom.xml` 鐨?`dependencyManagement` 澧炲姞 MyBatis-Plus**

鍦ㄧ幇鏈?`<dependencyManagement><dependencies>` 鍐呰拷鍔狅細

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-bom</artifactId>
    <version>3.5.17</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

璇存槑锛氱敤 BOM 缁熶竴 Plus 鐩稿叧鏋勪欢鐗堟湰锛涘瓙妯″潡寮曞叆 starter / annotation 鏃跺彲涓嶅啓 version銆?

- [ ] **Step 2: 鏇存柊 `take-out-pojo/pom.xml` 渚濊禆**

鍦ㄧ幇鏈?`take-out-common` 渚濊禆鏃佽拷鍔狅細

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-annotation</artifactId>
</dependency>
```

- [ ] **Step 3: 鏇存柊 `take-out-system/pom.xml` 渚濊禆**

鍦ㄧ幇鏈?`take-out-pojo` 渚濊禆鏃佽拷鍔狅細

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 4: 楠岃瘉渚濊禆鍙В鏋?*

Run:

```powershell
.\mvnw.cmd -q dependency:resolve -pl take-out-system -am
```

Expected: exit code `0`锛屾棤 unresolved dependency 閿欒銆?

- [ ] **Step 5: Commit锛堜粎褰撶敤鎴疯姹傦級**

```bash
git add pom.xml take-out-pojo/pom.xml take-out-system/pom.xml
git commit -m "build: add MyBatis-Plus and Lombok dependencies"
```

---

