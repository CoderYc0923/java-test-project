# Docker MySQL 常用命令

在项目根目录 `take-out/` 下执行。

## 启动 / 停止

```bash
# 后台启动（首次会自动建库、建用户、导入 sky.sql）
docker compose up -d

# 看状态
docker compose ps
docker compose logs -f mysql

# 停容器（数据还在 volume 里）
docker compose stop

# 删容器但保留数据
docker compose down

# 连数据卷一起删（库清空，下次 up 会重新执行 init + 导入 sky.sql）
docker compose down -v
```

## 跨机器同步数据（通过 GitHub）

改表 / 改数据后：

1. **导出**当前库到 `sky.sql`
2. **commit + push** `sky.sql`
3. 别的机器 `git pull` 后按下面「导入」或「空卷重建」

### 导出（本机 → sky.sql）

PowerShell / bash 通用（避免重定向乱码）：

```bash
docker exec take-out-mysql mysqldump -uroot -proot --databases take_out --default-character-set=utf8mb4 -r /tmp/sky.sql
docker cp take-out-mysql:/tmp/sky.sql ./sky.sql
```

然后把 `sky.sql` 提交到 GitHub。

### 导入（sky.sql → 本机，覆盖当前库内容）

```bash
docker cp ./sky.sql take-out-mysql:/tmp/sky.sql
docker exec -i take-out-mysql mysql -uroot -proot -e "SOURCE /tmp/sky.sql"
```

### 新机器 / 想完全按仓库重建

```bash
git pull
docker compose down -v
docker compose up -d
```

空数据卷时会自动执行：

1. `01-users.sql` → 创建 `takeout_rw` / `takeout_ro`
2. `sky.sql` → 建表并导入数据

## 连接信息

| 项 | 值 |
|----|-----|
| Host | `127.0.0.1` |
| Port | `3307`（不是 3306） |
| Database | `take_out` |
| 读写 | `takeout_rw` / `TakeoutRw@123` |
| 只读 | `takeout_ro` / `TakeoutRo@123` |
| root | `root` / `root` |

```text
jdbc:mysql://127.0.0.1:3307/take_out
```
