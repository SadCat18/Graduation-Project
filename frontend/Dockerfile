FROM node:20-alpine AS builder
WORKDIR /app

COPY frontend/package*.json ./
RUN npm install

COPY frontend .

ARG VUE_APP_AMAP_KEY=
ARG VUE_APP_AMAP_SERVICE_HOST=
ARG VUE_APP_AMAP_SECURITY_JS_CODE=

ENV VUE_APP_AMAP_KEY=${VUE_APP_AMAP_KEY}
ENV VUE_APP_AMAP_SERVICE_HOST=${VUE_APP_AMAP_SERVICE_HOST}
ENV VUE_APP_AMAP_SECURITY_JS_CODE=${VUE_APP_AMAP_SECURITY_JS_CODE}

RUN npm run build

FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY deploy/docker/nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
