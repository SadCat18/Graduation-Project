# Frontend (Vue CLI)

## Install

```bash
npm install
```

## Run (dev)

```bash
npm run serve
```

## Build

```bash
npm run build
```

## AMap Security Proxy

1. Create `frontend/.env.development` (you can copy from `.env.development.example`).
2. Ensure these variables are set:

```bash
VUE_APP_AMAP_KEY=YOUR_AMAP_KEY
VUE_APP_AMAP_SERVICE_HOST=http://localhost:5173/_AMapService
VUE_APP_AMAP_NGINX=http://127.0.0.1:80
```

3. Configure the security code only in nginx and start the proxy (see root `deploy/nginx/amap-security.conf.example`). Never inject the security code into the browser bundle.
