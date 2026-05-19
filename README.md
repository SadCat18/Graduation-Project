# 毕业设计项目

一个基于 Spring Boot 和 Vue 的前后端分离毕业设计项目。

## 技术栈
- 后端：Java、Spring Boot、Maven
- 前端：Vue、Vue Router
- 数据库：MySQL

## 项目结构
- `src/`：Spring Boot 后端源码
- `frontend/`：Vue 前端源码
- `database/`：SQL 脚本
- `deploy/`：部署相关文件

## 本地开发

### 后端启动
```bash
mvn spring-boot:run
```

### 前端启动
```bash
cd frontend
npm install
npm run serve
```

## 构建

### 后端打包
```bash
mvn -DskipTests package
```

### 前端打包
```bash
cd frontend
npm run build
```

## 说明
- 首次运行前，请先在后端配置文件中设置数据库连接信息。
- 如有需要，可执行 `database/` 目录下的脚本初始化表结构和基础数据。
