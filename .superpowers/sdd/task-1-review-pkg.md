# Task 1 review package
BASE: 1ffdab1 (pre-task, no commits — unstaged/working tree diffs)
HEAD: working tree

## Files changed
 take-out/pom.xml                 | 7 +++++++
 take-out/take-out-pojo/pom.xml   | 9 +++++++++
 take-out/take-out-system/pom.xml | 9 +++++++++
 3 files changed, 25 insertions(+)

## Full diff

diff --git a/take-out/pom.xml b/take-out/pom.xml
index 805f4e6..d42c6e0 100644
--- a/take-out/pom.xml
+++ b/take-out/pom.xml
@@ -25,20 +25,27 @@
         <module>take-out-admin</module>
     </modules>
 
     <properties>
         <java.version>17</java.version>
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
             <dependency>
                 <groupId>com.sky</groupId>
                 <artifactId>take-out-pojo</artifactId>
                 <version>${take-out.version}</version>
             </dependency>
diff --git a/take-out/take-out-pojo/pom.xml b/take-out/take-out-pojo/pom.xml
index 1586925..b5bb437 100644
--- a/take-out/take-out-pojo/pom.xml
+++ b/take-out/take-out-pojo/pom.xml
@@ -8,12 +8,21 @@
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
@@ -8,12 +8,21 @@
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
