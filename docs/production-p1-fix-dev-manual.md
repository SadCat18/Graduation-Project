# SkateHub P1 上线工程化修复开发手册

## 1. 目标

本手册用于修复 SkateHub 距离企业级正式上线仍存在的 P1 工程化差距。P1 的定义是：不一定阻止本地演示，但会影响生产安全、稳定性、可维护性和可运营性。

当前 P1 项：

1. 生产安全配置需要收紧。
2. 默认管理员初始化方式需要改造。
3. 数据库迁移缺少版本化管理。
4. Docker 构建与 CI 质量门禁需要明确边界。
5. Nginx/部署层缺少 HTTPS 和安全响应头方案。
6. 运行保障能力不足：备份、回滚、限流、日志和监控需要补齐。

## 2. 修复原则

- 先做可控配置化，再做架构升级。
- 不把真实密钥、数据库地址、API key 写入仓库。
- 不破坏现有本地开发体验。
- 每个 P1 项都要有验收命令或人工检查点。
- 对生产行为的变更必须能通过环境变量回滚。

## 3. 推荐修复顺序

1. 生产 CORS 白名单与安全配置。
2. 默认管理员初始化改造。
3. 数据库迁移工具引入。
4. Docker 构建策略与镜像可重复性。
5. Nginx HTTPS/安全响应头/上传体积一致性。
6. 备份、回滚、限流、日志监控。

## 4. P1-1 收紧生产安全配置

### 问题

当前 `SecurityConfig` 中 CORS 允许所有来源：

```java
config.setAllowedOrigins(List.of("*"));
```

这适合本地开发，不适合生产。

### 推荐修改

新增配置项：

```yaml
app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

在 `SecurityConfig` 中读取该配置，并按逗号分隔为白名单：

- 开发环境可配置 `http://localhost:5173`
- 生产环境配置真实域名，如 `https://skatehub.example.com`

### 修改点

- `src/main/resources/application.yml`
- `src/main/java/com/skatehub/config/SecurityConfig.java`
- 可选：新增 `AppSecurityProperties`

### 验收

本地：

```powershell
mvn test
```

人工检查：

- 未配置生产域名时不应默认放开所有来源。
- 生产 `.env` 或服务器环境变量中必须设置 `CORS_ALLOWED_ORIGINS`。
- 登录、注册、图片上传、AI 请求在允许域名下正常。

## 5. P1-2 改造默认管理员初始化

### 问题

当前启动时通过 `CommandLineRunner` 自动执行：

```java
authService.ensureDefaultAdmin();
```

同时 `AuthService` 内有默认密码：

```java
@Value("${admin.default-password:SkateHub@2026}")
```

这会造成生产初始化不可控。

### 推荐方案

改成显式启用：

```yaml
admin:
  bootstrap:
    enabled: ${ADMIN_BOOTSTRAP_ENABLED:false}
  default-password: ${ADMIN_DEFAULT_PASSWORD:}
```

启动逻辑：

1. 默认不创建管理员。
2. 只有 `ADMIN_BOOTSTRAP_ENABLED=true` 时才允许创建。
3. `ADMIN_DEFAULT_PASSWORD` 必须显式提供。
4. 生产环境禁止使用 `SkateHub@2026`。
5. 创建成功后记录日志，但不输出密码。

### 修改点

- `src/main/java/com/skatehub/config/DataInitConfig.java`
- `src/main/java/com/skatehub/service/AuthService.java`
- `src/main/resources/application.yml`
- `deploy/docker/.env.example`
- `docs/tencent-cloud-deploy.md`

### 验收

```powershell
mvn test
```

人工检查：

- 默认配置启动不会自动创建 admin。
- 显式设置 `ADMIN_BOOTSTRAP_ENABLED=true` 且提供强密码时可以初始化。
- 初始化后建议关闭该开关。

## 6. P1-3 引入数据库版本化迁移

### 问题

当前 `database/` 下有 `init.sql` 和多个增量脚本，但没有统一版本表、执行顺序和回滚策略。生产环境无法可靠判断当前数据库处于哪个版本。

### 推荐方案

使用 Flyway。Spring Boot 集成成本低，适合当前项目。

### 推荐改造

1. 在 `pom.xml` 增加 Flyway 依赖：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

2. 新增目录：

```text
src/main/resources/db/migration
```

3. 将数据库脚本整理为版本文件：

```text
V1__init_schema.sql
V2__news_ai_fields.sql
V3__growth_achievement_system.sql
V4__community_utility_modules.sql
V5__p2_productization.sql
```

4. 生产配置：

```yaml
spring:
  flyway:
    enabled: ${FLYWAY_ENABLED:true}
    baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:true}
  jpa:
    hibernate:
      ddl-auto: none
```

### 注意事项

- 已有生产库接入 Flyway 时，需要先做备份。
- 对已有库首次接入，可能需要 `baseline-on-migrate=true`，执行后再关闭。
- 不要在生产使用 `ddl-auto: update`。
- seed 数据与结构迁移分开，避免每次迁移污染生产数据。

### 验收

本地空库：

```powershell
mvn test
```

容器环境：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml up -d --build
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml logs backend
```

人工检查：

- 数据库中存在 `flyway_schema_history`。
- 后端启动不依赖手工执行一堆 SQL。
- 重复启动不会重复插入结构或基础数据。

## 7. P1-4 明确 Docker 构建与质量门禁边界

### 问题

当前后端 Dockerfile 使用：

```dockerfile
RUN mvn -q -DskipTests package
```

跳过测试本身不是绝对错误，但必须保证镜像来自 CI 绿色产物。否则失败代码也能被打包。

### 推荐方案

短期：

- 保留 Dockerfile 中 `-DskipTests`，但 CI 必须先执行 `mvn test`。
- 在发布流程中只允许从 CI 通过的 commit 构建镜像。

中期：

- 增加镜像构建 CI job。
- 给镜像打 commit sha 标签。
- 生产只部署不可变镜像标签，不直接在服务器上拉源码构建。

### 修改点

- `.github/workflows/ci.yml`
- 可选：新增 `.github/workflows/release.yml`
- `Dockerfile`
- `frontend/Dockerfile`
- `docs/tencent-cloud-deploy.md`

### 验收

- PR 不能绕过 CI 合并。
- Docker 构建失败会阻止发布。
- 发布记录能追踪到 commit sha。

## 8. P1-5 补齐 Nginx HTTPS 与安全响应头

### 问题

当前 Nginx 配置只监听 80：

```nginx
listen 80;
```

并且缺少常见安全响应头。

另外，后端允许上传视频到 300MB，但 Nginx 当前：

```nginx
client_max_body_size 20m;
```

这会导致大视频上传在网关层失败。

### 推荐修改

#### 8.1 上传体积一致

如果业务保留 300MB 视频上传：

```nginx
client_max_body_size 300m;
```

如果商业上线暂不支持大视频直传，则反向收紧后端配置，而不是前后不一致。

#### 8.2 安全响应头

在 Nginx 中增加：

```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "SAMEORIGIN" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(self), camera=(), microphone=()" always;
```

HTTPS 环境再增加：

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

#### 8.3 HTTPS

推荐两种方案：

1. 使用云厂商负载均衡或 CDN 托管证书，容器内继续 HTTP。
2. 在 Nginx 容器内挂载证书并监听 443。

对腾讯云 CVM 初期部署，推荐方案 1，证书更新和 TLS 配置更稳定。

### 修改点

- `deploy/docker/nginx/default.conf.template`
- `deploy/docker/docker-compose.yml`
- `docs/tencent-cloud-deploy.md`

### 验收

```bash
curl -I https://your-domain.example
```

检查：

- HTTPS 可访问。
- 安全响应头存在。
- `/api/actuator/health` 或后端 health 代理路径按预期可达。
- 图片和视频上传限制与业务策略一致。

## 9. P1-6 增加限流与滥用防护

### 问题

当前有验证码，但缺少统一限流。登录、注册、AI、上传、发帖等接口都有被刷风险。

### 推荐方案

使用 Redis 做轻量限流。

优先保护：

1. 登录接口。
2. 注册接口。
3. AI 相关接口。
4. 文件上传接口。
5. 发帖、评论、私信接口。

### 推荐规则

- 登录：同 IP 每分钟 10 次，同账号每 5 分钟 10 次。
- 注册：同 IP 每小时 20 次。
- AI：普通用户每分钟 10 次，管理员调试接口单独限制。
- 上传：同用户每分钟 5 次。
- 发帖/评论/私信：同用户每分钟 10 次。

### 修改点

- 新增 `RateLimitService`
- 新增 `RateLimitFilter` 或在关键 service/controller 中显式调用
- `src/main/resources/application.yml`
- 相关安全测试

### 验收

- 超限返回 429。
- 返回信息不泄露内部 key。
- Redis 不可用时采用保守策略：关键接口拒绝或降级，公开读接口不受影响。

## 10. P1-7 补齐备份、回滚、日志和监控

### 10.1 数据备份

最低要求：

- MySQL 每日备份。
- Redis AOF 已启用，但仍需定期备份数据卷。
- `upload/` 目录定期备份。

建议新增文档：

- `docs/backup-and-restore.md`

内容包括：

- `mysqldump` 命令。
- 恢复命令。
- 上传目录打包。
- 备份保留周期。
- 恢复演练步骤。

### 10.2 回滚

最低要求：

- 每次发布记录 commit sha。
- 保留上一版镜像或上一版源码包。
- 数据库迁移前必须备份。
- 破坏性迁移需要单独回滚方案。

### 10.3 日志

建议：

- 后端日志输出 JSON 或至少统一格式。
- AI 调用记录耗时、模型、scene、成功/失败，不记录 API key。
- 上传失败、鉴权失败、限流命中应有可检索日志。

### 10.4 监控

当前已有 Actuator health。P1 建议增加：

- Prometheus metrics endpoint。
- JVM 内存、GC、线程。
- HTTP 请求耗时和错误率。
- Redis/MySQL 健康。
- AI 调用耗时和失败率。
- 上传失败率。

注意：metrics endpoint 不要公开暴露到公网。

## 11. P1 完整验收清单

代码验收：

- CORS 已配置为白名单。
- 默认管理员不会默认自动创建。
- 数据库迁移有版本记录。
- Docker 发布流程依赖 CI 绿色结果。
- Nginx 上传限制与业务一致。
- 安全响应头已配置。
- 关键写接口有限流。

命令验收：

```powershell
mvn test
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
npm run build:web
Get-ChildItem -LiteralPath 'frontend\tests' -Filter '*.test.cjs' | Sort-Object Name | ForEach-Object { node $_.FullName }
```

部署验收：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml up -d --build
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml ps
curl -I https://your-domain.example
```

运维验收：

- 能备份数据库。
- 能恢复数据库到测试环境。
- 能回滚到上一版本。
- 能查看后端错误日志。
- 能确认 health 和 metrics 状态。

P1 全部完成后，项目才适合进入小流量试运行。
