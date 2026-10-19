# 腾讯云 CVM 一键部署说明

## 适用范围

- 服务器类型：腾讯云 CVM（Ubuntu 22.04+/Debian 12+ 优先）
- 部署方式：Docker Compose
- 暴露服务：前端 Nginx、后端 Spring Boot、MySQL、Redis

## 第一次部署

1. 把仓库上传到服务器，例如：

```bash
git clone <你的仓库地址> se1
cd se1
```

2. 安装 Docker 与 Compose。

3. 首次执行部署脚本：

```bash
chmod +x scripts/deploy-tencent.sh
./scripts/deploy-tencent.sh
```

第一次执行会自动生成 `deploy/docker/.env`，并提示你先补齐配置。

4. 编辑 `deploy/docker/.env`，至少检查这些项：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `JWT_SECRET`
- `SERVER_NAME`
- `APP_PORT`
- `CORS_ALLOWED_ORIGINS`
- `ADMIN_BOOTSTRAP_ENABLED`
- `ADMIN_DEFAULT_PASSWORD`
- `FLYWAY_ENABLED`
- `RATE_LIMIT_ENABLED`
- `AMAP_KEY`
- `AMAP_SECURITY_JS_CODE`

生产环境必须把 `CORS_ALLOWED_ORIGINS` 设置为真实站点域名，例如 `https://skatehub.example.com`，不要使用 `*`。

如需首次创建默认管理员，只在第一次部署时临时设置：

```env
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_DEFAULT_PASSWORD=一段满足管理员强度要求的临时强密码
```

管理员创建完成后，把 `ADMIN_BOOTSTRAP_ENABLED` 改回 `false` 并重新部署。不要使用旧默认密码 `SkateHub@2026`。

5. 再执行一次：

```bash
./scripts/deploy-tencent.sh
```

## 常用命令

启动或更新：

```bash
./scripts/deploy-tencent.sh
```

查看状态：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml ps
```

查看日志：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml logs -f
```

停止服务：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml down
```

## 说明

- 数据库结构由 Flyway 迁移管理，迁移文件位于 `src/main/resources/db/migration`。
- 新空库默认执行 Flyway；已有生产库首次接入前必须先备份。`FLYWAY_BASELINE_ON_MIGRATE=true` 用于让 Flyway 接管已有非空数据库并创建 `flyway_schema_history` 基线。
- `JPA_DDL_AUTO` 在生产中保持 `none`，不要使用 `update` 自动改表。
- 上传文件持久化到仓库根目录 `upload/`。
- 前端通过 Nginx 反向代理 `/api`、`/upload` 和 `/_AMapService`，浏览器侧不需要再写死后端地址。
- Nginx 已设置常见安全响应头，并把上传体积限制调整为 300MB，与后端视频上传策略一致。
- 推荐使用腾讯云负载均衡或 CDN 托管 HTTPS 证书，容器内继续 HTTP。HTTPS 上线后用 `curl -I https://你的域名` 检查安全响应头。
- 后端启用 `/actuator/health` 和 Prometheus metrics。不要把 `/actuator/prometheus` 暴露到公网。
- Redis 限流默认在 Docker 环境启用，覆盖登录、注册、AI、上传和主要写接口；Redis 不可用时关键接口会保守拒绝。
- 备份和回滚流程见 `docs/backup-and-restore.md`。
