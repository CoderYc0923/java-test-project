## 重新安装模块，若改动多个包
mvn clean install -DskipTests

## 启动入口包
mvn spring-boot:run -pl take-out-admin