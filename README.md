# SE1 滑板社区系统（Spring Boot + Vue）

## 项目结构

- 后端：`src/main/java/com/javademo1`
- 前端：`frontend`
- 数据库脚本：`database/init.sql`
- 高德安全代理（Nginx 示例）：`deploy/nginx/amap-security.conf.example`

## 后端启动

1. 执行建库建表与初始化脚本：`database/init.sql`
2. 配置数据库连接：`src/main/resources/application.yml`
3. 启动后端：

```bash
mvn spring-boot:run
```

如果希望通过环境变量覆盖数据库配置（PowerShell）：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/skate_exchange?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
mvn spring-boot:run
```

后端默认地址：`http://localhost:8080`

## 前端启动（Vue CLI）

```bash
cd frontend
npm install
npm run serve
```

前端默认地址：`http://localhost:5173`

## 高德地图 JS API 安全接入（官方代理模式）

当前项目使用：

- Key：`6003345b78ed25b126a76cb9caf08a7b`
- securityJsCode：`04038403ad1a9bbe9320ee87939b86f2`

### 1）配置 Nginx 反向代理

参考示例配置文件：

- `deploy/nginx/amap-security.conf.example`

修改并保存 Nginx 配置后重载：

```bash
nginx -s reload
```

### 2）配置前端环境变量

将 `frontend/.env.development.example` 复制为 `frontend/.env.development`，按需修改：

- `VUE_APP_AMAP_KEY`：高德 JS Key
- `VUE_APP_AMAP_SERVICE_HOST`：必须以 `/_AMapService` 结尾
- `VUE_APP_AMAP_NGINX`：Vue 开发代理目标（`/_AMapService`）

### 3）关键加载顺序

项目中 `frontend/src/utils/amap.js` 已按官方要求处理：

1. 先设置 `window._AMapSecurityConfig.serviceHost`
2. 再加载 `https://webapi.amap.com/maps?...`

即：必须先注入安全配置，再加载高德 JS API 脚本。

## 模块入口

- 同城约板地图页：`/activities`
- 社区快讯列表页：`/bulletins`
- 社区快讯发布页：`/bulletins/publish`

## 社区快讯模块说明（已完善）

- 快讯分类固定为：
  - 活动通知
  - 赛事快讯
  - 同城动态
  - 场地通知
  - 路线推荐
  - 安全提醒
  - 官方公告
  - 经验分享
- 前端发布页使用固定分类下拉选择。
- 后端发布接口对分类做白名单校验，非法分类会拒绝提交。
- 管理端支持按“分类 + 审核状态（全部/待审核/已通过/已驳回）”组合筛选。
- 管理端提供分类统计接口与展示。

## 更新记录（2026-04-30）

- 新增 Redis 依赖：`spring-boot-starter-data-redis`（`pom.xml`）。
- 在 `src/main/resources/application.yml` 新增 Redis 配置：
  - `spring.data.redis.host: 127.0.0.1`
  - `spring.data.redis.port: 6379`
  - `spring.data.redis.timeout: 3000ms`
- 登录验证码由 `HttpSession` 迁移到 Redis（TTL 300 秒，使用后即失效）：
  - `GET /api/auth/captcha?captchaId=xxx`
  - `POST /api/auth/login/user`
  - `POST /api/auth/login/admin`
- 登录请求需提交 `captchaId` 与 `captchaCode`。
- 前端 `frontend/src/views/LoginPage.vue` 已支持生成并提交 `captchaId`。

Redis 连通性检查命令：

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 6379
```
