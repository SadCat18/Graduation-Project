# SkateHub P0 上线阻断修复开发手册

## 1. 目标

本手册用于修复当前阻止 SkateHub 进入企业级正式上线流程的 P0 问题。P0 的定义是：不修复就不允许进入生产发布、公开演示环境或商业试运行。

当前 P0 项：

1. 后端主测试不绿。
2. 前端生产依赖存在高危漏洞。
3. 缺少 CI 自动质量门禁。
4. 部署文档引用了不存在的部署脚本。

完成后，项目应至少满足：

- `mvn test` 通过。
- `npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org` 无 high/critical。
- 前端生产构建通过。
- 前端契约测试通过。
- CI 能自动执行上述检查。
- 部署文档中的命令在仓库中真实存在。

## 2. 当前证据

### 2.1 后端测试失败

命令：

```powershell
mvn test
```

当前结果：

- Tests run: 218
- Failures: 0
- Errors: 1

失败位置：

- `src/test/java/com/skatehub/service/GrowthActionEventPublishingTest.java`
- `postCreatePublishesAfterSaveAndGrowthGrant`
- 测试中调用 `service.create(CURRENT_USER, new PostCreateRequest())`
- 当前业务规则会通过 `ModerationRuleService.assertPublishable` 拦截空内容，抛出 `内容过短，请补充更具体的信息`

### 2.2 前端依赖审计失败

命令：

```powershell
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
```

当前结果：

- `axios` high severity advisories
- `form-data` high severity advisory
- audit 建议：`npm audit fix`

注意：本地默认 registry 当前是 `https://registry.npmmirror.com/`，该 registry 不支持 npm audit 接口。审计命令必须显式指定官方 registry，或在 CI 中使用官方 registry。

### 2.3 缺少 CI

当前仓库没有 `.github/workflows` 目录。企业级上线必须把测试、构建、依赖审计变成自动门禁，不能依赖人工记忆。

### 2.4 部署脚本缺失

文档中引用：

```bash
scripts/deploy-tencent.sh
```

但仓库当前没有 `scripts/` 目录。需要补齐脚本或修改文档为真实命令。推荐补齐脚本，因为文档、README 和部署说明已经围绕该入口编写。

## 3. 修复顺序

按以下顺序实施：

1. 修复后端测试。
2. 修复前端生产依赖漏洞。
3. 补齐部署脚本。
4. 增加 CI 工作流。
5. 本地完整验收。

不要先做大规模重构。P0 的目标是恢复上线门禁可信度。

## 4. P0-1 修复后端测试

### 问题

`GrowthActionEventPublishingTest.postCreatePublishesAfterSaveAndGrowthGrant` 使用空 `PostCreateRequest`，但生产代码已经增加了发布前内容审核，空内容不再是合法输入。

### 推荐修改

修改测试数据，而不是绕过业务校验。

建议在测试中构造合法发帖请求：

```java
PostCreateRequest request = new PostCreateRequest();
request.setTitle("一次稳定的滑板训练复盘");
request.setContent("今天练习了 Ollie 起跳和落地稳定性，重点记录重心控制、肩膀方向和下次训练计划。");
request.setCategory("训练复盘");

service.create(CURRENT_USER, request);
```

如果 `PostCreateRequest` 的字段名与上例不一致，以实际类定义为准。

### 修改点

- `src/test/java/com/skatehub/service/GrowthActionEventPublishingTest.java`

### 验收

```powershell
mvn test
```

验收标准：

- 全部后端测试通过。
- 不降低 `ModerationRuleService` 的生产规则。
- 不为了测试关闭内容审核。

## 5. P0-2 修复前端高危依赖

### 问题

生产依赖中 `axios` 与其传递依赖 `form-data` 有高危 advisories。

### 推荐修改

先执行自动修复：

```powershell
npm --prefix frontend audit fix --registry=https://registry.npmjs.org
```

然后检查：

```powershell
git diff -- frontend/package.json frontend/package-lock.json
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
npm run build:web
```

### 注意事项

- 只接受兼容升级，不做破坏性 major 升级，除非 audit 明确要求且已完成回归测试。
- 修复后重点检查 `frontend/src/api/http.js` 的 axios 拦截器行为：
  - token 仍能附加到 `Authorization`。
  - 401/403 仍会清理登录态并跳转登录页。
  - timeout 错误提示仍正常。

### 验收

```powershell
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
npm run build:web
Get-ChildItem -LiteralPath 'frontend\tests' -Filter '*.test.cjs' | Sort-Object Name | ForEach-Object { node $_.FullName }
```

验收标准：

- audit 无 high/critical。
- 前端构建成功。
- 38 个前端契约测试全部通过。

## 6. P0-3 补齐部署脚本

### 问题

部署文档和 README 都引用 `scripts/deploy-tencent.sh`，但脚本不存在。

### 推荐新增文件

- `scripts/deploy-tencent.sh`

### 脚本职责

脚本应保持简单、可重复执行：

1. 定位仓库根目录。
2. 检查 Docker 与 Docker Compose 是否可用。
3. 如果 `deploy/docker/.env` 不存在，从 `deploy/docker/.env.example` 复制生成。
4. 首次生成 `.env` 后提示用户补齐密钥，并退出。
5. `.env` 存在时执行：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml up -d --build
```

6. 输出服务状态：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml ps
```

### 安全要求

- 不在脚本中生成弱密码。
- 不把 `.env` 打印到日志。
- 不覆盖已有 `.env`。
- 失败时使用非 0 exit code。

### 验收

在 Linux 或 WSL 中执行：

```bash
bash scripts/deploy-tencent.sh
```

验收标准：

- 首次执行能生成 `deploy/docker/.env` 并提示补齐配置。
- 第二次执行能调用 Docker Compose。
- 文档中的命令与真实脚本路径一致。

## 7. P0-4 增加 CI 门禁

### 推荐新增文件

- `.github/workflows/ci.yml`

### 推荐检查项

CI 至少包含：

1. Java 17 + Maven 后端测试。
2. Node 20 前端安装。
3. 前端生产依赖审计。
4. 前端生产构建。
5. 前端契约测试。

### 推荐 workflow 结构

```yaml
name: CI

on:
  push:
    branches: [ master, main ]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: mvn test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
        working-directory: frontend
      - run: npm audit --omit=dev --registry=https://registry.npmjs.org
        working-directory: frontend
      - run: npm run build
        working-directory: frontend
      - name: Frontend contract tests
        shell: bash
        run: |
          for test in frontend/tests/*.test.cjs; do
            node "$test"
          done
```

### 注意事项

- 如果 `mvn test` 依赖本地 `application-local.yml`，必须修复测试配置，不能在 CI 中提交真实密钥。
- CI 中不要使用 `npm install`，使用 `npm ci` 保证 lockfile 可重复。
- CI 失败必须阻止合并。

## 8. P0 完整验收清单

本地执行：

```powershell
mvn test
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
npm run build:web
Get-ChildItem -LiteralPath 'frontend\tests' -Filter '*.test.cjs' | Sort-Object Name | ForEach-Object { node $_.FullName }
```

代码检查：

- `.github/workflows/ci.yml` 存在。
- `scripts/deploy-tencent.sh` 存在。
- README 和 `docs/tencent-cloud-deploy.md` 中的部署命令真实可用。
- `frontend/package-lock.json` 已随依赖修复更新。

发布前结论：

- 以上全部通过，P0 才能关闭。
