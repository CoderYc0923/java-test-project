# 后台启动
docker compose up -d

# 看状态
docker compose ps
docker compose logs -f mysql

# 停容器（数据还在 volume 里）
docker compose stop

# 删容器但保留数据
docker compose down

# 连数据卷一起删（库清空，慎用）
docker compose down -v