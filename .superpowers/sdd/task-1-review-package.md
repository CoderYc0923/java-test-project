# Review package Task 1
Base: (no commits — working tree)
Head: working tree after Task 1

## Stat
A pom.xml
A take-out-common/pom.xml
A take-out-pojo/pom.xml
A take-out-system/pom.xml
A take-out-framework/pom.xml
A take-out-admin/pom.xml

## File: pom.xml
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

## File: take-out-common/pom.xml
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

## File: take-out-pojo/pom.xml
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

## File: take-out-system/pom.xml
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

## File: take-out-framework/pom.xml
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

## File: take-out-admin/pom.xml
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


