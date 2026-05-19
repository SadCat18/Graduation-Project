# javademo1 (Spring Boot + Vue)

## Project Structure

- Backend: `src/main/java/com/javademo1`
- Frontend: `frontend`
- SQL: `database/init.sql`
- Nginx (AMap proxy example): `deploy/nginx/amap-security.conf.example`

## Backend Run

1. Execute SQL: `database/init.sql`
2. Configure DB: `src/main/resources/application.yml`
3. Start backend:

```bash
mvn spring-boot:run
```

Or start with explicit database credentials (PowerShell):

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/skate_exchange?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
mvn spring-boot:run
```

Backend URL: `http://localhost:8080`

## Frontend Run (Vue CLI)

```bash
cd frontend
npm install
npm run serve
```

Frontend URL: `http://localhost:5173`

## AMap JS API Security (Official Proxy Mode)

Current integration uses:

- Key: `6003345b78ed25b126a76cb9caf08a7b`
- securityJsCode: `04038403ad1a9bbe9320ee87939b86f2`

### 1) Configure Nginx reverse proxy

Use the example file:

- `deploy/nginx/amap-security.conf.example`

After saving your nginx config, reload:

```bash
nginx -s reload
```

### 2) Configure frontend environment

Copy `frontend/.env.development.example` to `frontend/.env.development` and edit if needed.

Key fields:

- `VUE_APP_AMAP_KEY`: AMap JS key
- `VUE_APP_AMAP_SERVICE_HOST`: must end with `/_AMapService`
- `VUE_APP_AMAP_NGINX`: Vue dev-server proxy target for `/_AMapService`

### 3) Important loading order

In this project, `frontend/src/utils/amap.js` already ensures:

1. Set `window._AMapSecurityConfig.serviceHost`
2. Then load `https://webapi.amap.com/maps?...`

This follows official requirement: security config must be set before JS API script loading.

## Module Entry

- 同城约板地图页: `/activities`

## Change Record (2026-04-30)

- Added Redis dependency: `spring-boot-starter-data-redis` (`pom.xml`).
- Added Redis config in `src/main/resources/application.yml`:
  - `spring.data.redis.host: 127.0.0.1`
  - `spring.data.redis.port: 6379`
  - `spring.data.redis.timeout: 3000ms`
- Migrated login captcha from `HttpSession` to Redis (300s TTL, one-time consume):
  - `GET /api/auth/captcha?captchaId=xxx`
  - `POST /api/auth/login/user`
  - `POST /api/auth/login/admin`
- Login API request now requires `captchaId` and `captchaCode`.
- Frontend `frontend/src/views/LoginPage.vue` now generates and submits `captchaId`.

Redis connectivity check used:

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 6379
```
