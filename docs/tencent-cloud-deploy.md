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
- `AMAP_KEY`
- `AMAP_SECURITY_JS_CODE`

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

- 数据库初始化脚本来自 `database/init.sql`，仅在 MySQL 数据卷首次创建时执行。
- 上传文件持久化到仓库根目录 `upload/`。
- 前端通过 Nginx 反向代理 `/api`、`/upload` 和 `/_AMapService`，浏览器侧不需要再写死后端地址。
- 如果你准备绑定域名，记得在腾讯云安全组开放 `80` 端口；如果以后要上 HTTPS，建议再接一个证书版 Nginx 或腾讯云负载均衡。
