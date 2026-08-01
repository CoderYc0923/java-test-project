### Task 3: pojo / system 包占位

**Files:**
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/package-info.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/package-info.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/package-info.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/service/package-info.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/mapper/package-info.java`

**Interfaces:**
- Consumes: Task 1 模块坐标
- Produces: 可编译的空包占位（Git 可跟踪）

- [ ] **Step 1: 写入 package-info 文件**

每个文件内容分别为：

```java
package com.sky.takeout.pojo.entity;
```

```java
package com.sky.takeout.pojo.dto;
```

```java
package com.sky.takeout.pojo.vo;
```

```java
package com.sky.takeout.system.service;
```

```java
package com.sky.takeout.system.mapper;
```

- [ ] **Step 2: 编译 pojo 与 system**

Run: `mvn -pl take-out-pojo,take-out-system -am compile`  
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit（仅用户授权时）**

```bash
git add take-out-pojo take-out-system
git commit -m "chore: add pojo and system package placeholders"
```

---
