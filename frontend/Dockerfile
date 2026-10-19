FROM node:20-alpine AS builder
WORKDIR /app

COPY frontend/package*.json ./
RUN npm ci

COPY frontend .

ARG VUE_APP_AMAP_KEY=
ARG VUE_APP_AMAP_SERVICE_HOST=

ENV VUE_APP_AMAP_KEY=${VUE_APP_AMAP_KEY}
ENV VUE_APP_AMAP_SERVICE_HOST=${VUE_APP_AMAP_SERVICE_HOST}

RUN npm run build

FROM nginx:1.27-alpine

ARG VCS_REF=unknown
LABEL org.opencontainers.image.revision=$VCS_REF

COPY --from=builder /app/dist /usr/share/nginx/html
COPY deploy/docker/nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
