# SkateHub 下一阶段优化操作手册

## 1. 文档目的

本文档用于指导 SkateHub 在现有功能基础上继续进行安全、测试、性能、架构和产品能力优化。

本轮优化遵循以下原则：

1. 先保护和固化现有成果，再开始新的重构。
2. 先处理凭证、备份和运行时文件等安全风险。
3. 每项优化都必须有基线、验收命令和回滚方式。
4. 不在同一个提交中混合安全修复、功能开发和大规模界面重构。
5. 数据库结构变更必须通过新的 Flyway 迁移完成，禁止直接修改已经执行过的迁移文件。

## 2. 当前质量基线

基线日期：2026-07-15。

已验证项目：

| 检查项 | 当前结果 |
| --- | --- |
| 后端测试 | 220 项通过，0 失败，0 错误 |
| 前端契约测试 | 39 个测试文件执行成功 |
| 前端生产构建 | 构建成功，有 1 个入口体积警告 |
| 前端生产依赖审计 | 0 个漏洞 |
| 前端入口体积 | 281 KiB，超过构建工具建议的 244 KiB |
| Docker 本地验证 | 当前开发机未安装 Docker，需由 CI 或有 Docker 的环境验证 |

基线验证命令：

```powershell
mvn test
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
npm --prefix frontend run build
Get-ChildItem -LiteralPath 'frontend\tests' -Filter '*.test.cjs' |
  Sort-Object Name |
  ForEach-Object {
    node $_.FullName
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
```

所有阶段开始前和完成后都应执行一次上述命令。

## 3. 推荐实施顺序

| 阶段 | 内容 | 优先级 | 预计工作量 |
| --- | --- | --- | --- |
| 0 | 固化工作区、安全与仓库治理 | P0 | 0.5～1 天 |
| 1 | 建立真实前端与集成测试 | P1 | 1～2 天 |
| 2 | 前端体积、组件和后端查询优化 | P1 | 2～4 天 |
| 3 | 私信会话状态与社区治理 | P1 | 2～3 天 |
| 4 | 监控、AI 评测和答辩增强 | P2 | 2～5 天 |

不要同时启动所有阶段。每个阶段通过验收后再进入下一阶段。

## 4. 阶段 0：固化工作区与安全治理

### 4.1 保存当前工作成果

当前工作区包含较多已修改和未跟踪文件。开始新优化前，先检查变更范围：

```powershell
git status --short --branch
git diff --stat
git diff --check
```

建议将现有改动拆成以下提交：

1. 生产安全、Flyway、CI 和 Docker 部署。
2. 私信分页、DTO、轮询和发送体验。
3. 页面性能与视觉体验。
4. 文档和测试。

提交前要求：

- 每个提交都能独立说明目的。
- 不提交 `.env`、数据库备份、运行日志或真实用户上传文件。
- 每个业务提交至少执行对应测试；最后一个提交执行完整质量门禁。

### 4.2 处理高德地图配置

涉及文件：

- `frontend/src/utils/amap.js`
- `frontend/.env.development`
- `frontend/.env.development.example`
- `frontend/Dockerfile`
- `deploy/docker/docker-compose.yml`
- `deploy/docker/.env.example`

操作步骤：

1. 在高德开放平台重新生成或轮换当前使用的 Key 和安全配置。
2. 为浏览器端 Key 配置允许访问的域名，不使用无限制 Key。
3. 从 `frontend/src/utils/amap.js` 删除硬编码 Key 回退值。
4. 生产环境统一使用 `window._AMapSecurityConfig.serviceHost` 代理模式。
5. 不再把 `securityJsCode` 注入前端构建产物。
6. 停止跟踪 `frontend/.env.development`，仅保留 `.env.development.example`。
7. 本地真实值放入被 `.gitignore` 忽略的环境文件。

注意：仅从最新提交删除旧配置不能消除 Git 历史中的值，因此轮换凭证是必需步骤。

建议检查命令：

```powershell
git ls-files | Select-String -Pattern '\.env|application-local'
rg -n --hidden -g '!frontend/node_modules/**' -g '!target/**' -g '!.git/**' `
  'securityJsCode|VUE_APP_AMAP_SECURITY_JS_CODE|DEFAULT_KEY' .
```

验收标准：

- 仓库中不再出现真实 `securityJsCode`。
- `amap.js` 不包含可用 Key 的硬编码回退。
- 地图在本地和部署环境中均通过 `/_AMapService` 正常加载。
- 高德控制台中的旧凭证已失效。

### 4.3 隔离备份、上传和日志文件

在 `.gitignore` 中补充：

```gitignore
backups/
upload/
frontend/.env.development
```

如果某些演示图片必须进入仓库，应移动到 `frontend/public/demo/` 等明确的静态资源目录，不要继续放在运行时上传目录中。

对于已经被 Git 跟踪的运行时文件，先确认它们不是必须的演示资源，再从 Git 索引移除。不要删除服务器或本地磁盘上的原始文件。

验收标准：

- `git status` 不再显示数据库备份和本地运行文件。
- 新上传的图片、视频不会进入 Git 状态。
- 数据库备份存放在仓库外，并设置访问权限和保留周期。

### 4.4 阶段 0 回滚

- 配置修改失败时，恢复上一提交并继续使用旧代理配置。
- 不要恢复已经确认泄露或进入历史记录的旧凭证，应生成新的凭证。
- Git 索引调整不得替代真实备份；操作前保留仓库外副本。

## 5. 阶段 1：建立真实测试体系

### 5.1 当前问题

现有前端契约测试主要读取源码并检查字符串是否存在。这类测试适合保护入口和约定，但不能验证组件是否能真实渲染、点击、请求和处理异常。

本阶段保留现有契约测试，同时增加运行时测试。

### 5.2 引入 Vitest 和 Vue Test Utils

建议安装：

```powershell
npm --prefix frontend install --save-dev vitest @vue/test-utils jsdom
```

建议优先覆盖以下组件和逻辑：

1. `MessagesPage.vue`
   - 首次加载会话。
   - 游标加载更早消息。
   - 乐观发送成功、失败和重试。
   - 页面隐藏时停止轮询。
2. `LoginPage.vue`
   - 验证码加载和刷新。
   - 登录失败提示。
   - 成功后保存登录态并跳转。
3. `ActivityDetailPage.vue`
   - 报名、取消报名和签到状态。
4. `AiCoachPage.vue`
   - 请求失败、引用卡片和长消息溢出。

测试不调用真实后端和真实 AI 服务，统一模拟 API 层。

### 5.3 引入 Playwright 端到端测试

建议安装：

```powershell
npm --prefix frontend install --save-dev @playwright/test
npm --prefix frontend exec -- playwright install chromium
```

第一批只建立 3 条关键路径：

1. 用户登录后进入首页。
2. 用户打开私信、发送消息并看到发送成功状态。
3. 用户打开活动详情并完成报名。

端到端测试使用独立测试账号和独立数据库，不使用生产数据。

### 5.4 后端覆盖率和真实依赖测试

后端建议增加：

- JaCoCo：输出覆盖率报告并在 CI 设置最低门槛。
- Testcontainers MySQL：验证 Flyway 和 MySQL 方言查询。
- Testcontainers Redis：验证验证码、限流和 Redis 故障策略。

第一阶段不追求过高覆盖率，建议先设置：

- 行覆盖率不低于 60%。
- 核心服务 `AuthService`、`PrivateMessageService`、`RecommendationService` 不低于 75%。
- 所有新增缺陷必须先有失败测试，再提交修复。

### 5.5 阶段 1 验收

- 原有 39 个前端契约测试继续通过。
- 至少有 10 个真实组件测试。
- 至少有 3 条关键端到端流程。
- CI 能生成后端覆盖率报告。
- 测试不依赖个人电脑上的本地密钥和生产数据库。

## 6. 阶段 2：性能与代码结构优化

### 6.1 建立前端体积报告

生成构建报告：

```powershell
npm --prefix frontend run build -- --report
```

建议设置以下预算：

- 初始页面资源总 Gzip 体积不超过 100 KiB。
- 单个普通业务路由 Gzip 体积不超过 30 KiB。
- 管理端等复杂路由 Gzip 体积不超过 40 KiB。
- 任一提交导致单路由体积增加超过 10%，必须在评审中说明原因。

### 6.2 拆分大型 Vue 文件

优先拆分顺序：

1. `frontend/src/views/AdminPage.vue`
2. `frontend/src/views/AiCoachPage.vue`
3. `frontend/src/views/ProfilePage.vue`
4. `frontend/src/views/HomePage.vue`
5. `frontend/src/views/MessagesPage.vue`

`AdminPage.vue` 建议拆分为：

```text
frontend/src/views/admin/
  AdminWorkbenchPanel.vue
  AdminReviewPanel.vue
  AdminUserPanel.vue
  AdminContentPanel.vue
  AdminAnalyticsPanel.vue
  AdminSettingsPanel.vue
frontend/src/composables/admin/
  useAdminReview.js
  useAdminUsers.js
  useAdminAnalytics.js
```

拆分要求：

- 路由和现有 API 地址保持不变。
- 每拆出一个模块就补对应组件测试。
- 管理端子面板按当前标签页懒加载。
- 公共颜色、间距和按钮样式继续使用现有全局设计变量。

### 6.3 优化推荐服务查询

重点文件：

- `src/main/java/com/skatehub/service/RecommendationService.java`
- `src/main/java/com/skatehub/dao/PostRepository.java`
- `src/main/java/com/skatehub/dao/ActivityRepository.java`
- `src/main/java/com/skatehub/dao/CommentRepository.java`

操作顺序：

1. 用有限结果集查询替换业务请求中的全表 `findAll()`。
2. 将可见状态、时间范围、城市和分类条件下推到数据库。
3. 使用投影 DTO，只读取推荐计算和卡片展示需要的字段。
4. 将循环中的 `findById()` 改为一次批量查询。
5. 对用户推荐结果增加 5～10 分钟 Redis 缓存。
6. 用户点赞、收藏、训练完成后删除对应推荐缓存。
7. 对新查询执行 `EXPLAIN`，根据执行计划增加组合索引。

建议性能目标：

- 推荐接口 P95 响应时间低于 300 ms，不包含外部 AI 调用。
- 单次推荐请求数据库查询数固定，不随候选内容数量线性增加。
- 推荐请求不加载完整帖子、评论和活动表。

### 6.4 API DTO 化

项目中仍有较多 `Map<String, Object>` 响应。建议按照访问频率逐步迁移：

1. 首页与公开搜索。
2. 活动和帖子详情。
3. 关注、通知和用户中心。
4. 管理端统计。

每个 DTO 应明确：

- 字段名与类型。
- 可空字段。
- 时间格式。
- 分页结构。
- 前端对应使用位置。

不要一次性全量替换；每次迁移一个 API，并保持接口回归测试通过。

### 6.5 阶段 2 验收

- 前端构建不再出现入口体积警告，或已记录可接受原因。
- `AdminPage.vue` 不再承担所有管理端逻辑。
- 推荐接口不再执行候选表全量加载。
- 新增查询有数据库执行计划记录。
- 所有改动通过完整质量门禁。

## 7. 阶段 3：私信二期优化

### 7.1 当前已完成能力

- 会话分页和关键词搜索。
- 消息游标分页。
- 未读数量。
- 定时轮询和页面隐藏暂停。
- 乐观发送、失败状态和重试。
- DTO 响应和有限长度输入。

本阶段不重复实现上述功能。

### 7.2 新增会话用户状态表

通过新的 Flyway 文件新增，例如：

```text
src/main/resources/db/migration/V7__private_conversation_user_state.sql
```

建议字段：

```text
conversation_id
user_id
last_read_message_id
unread_count
pinned
muted
archived
deleted_at
create_time
update_time
```

建议唯一约束：

```text
(conversation_id, user_id)
```

建议索引：

```text
(user_id, archived, pinned, update_time)
(user_id, unread_count)
```

写入消息时，在同一事务中更新接收方未读数和发送方会话状态。标记已读时只处理 `lastReadMessageId` 之前的消息。

### 7.3 会话管理接口

建议增加：

```text
POST   /api/messages/conversations/{id}/pin
DELETE /api/messages/conversations/{id}/pin
POST   /api/messages/conversations/{id}/archive
DELETE /api/messages/conversations/{id}/archive
POST   /api/messages/conversations/{id}/mute
DELETE /api/messages/conversations/{id}/mute
```

前端提供置顶、归档、免打扰入口，并为归档会话提供单独筛选。

### 7.4 拉黑、举报和限流

推荐顺序：

1. 用户拉黑与解除拉黑。
2. 拉黑后禁止建立新会话和发送消息。
3. 单条消息和整个会话举报。
4. 接入现有管理端举报处理流程。
5. 对陌生人首条私信、重复内容和短时间多目标发送设置更严格限流。

在治理能力完成前，不建议优先开发图片附件、输入中状态或在线状态。

### 7.5 实时通信升级条件

当前轮询可以继续使用。满足以下任一条件后再考虑 SSE 或 WebSocket：

- 活跃用户规模导致轮询明显增加服务器压力。
- 产品明确需要输入中、在线状态或秒级消息到达。
- 已具备连接鉴权、断线重连、消息补偿和监控能力。

### 7.6 阶段 3 验收

- 会话支持置顶、归档和免打扰。
- 未读数查询不随会话数量增加而线性增加数据库查询。
- 拉黑关系下不能发起或继续私信。
- 用户可以举报消息，管理员可以处理。
- 已读标记不会误处理用户尚未看到的新消息。

## 8. 阶段 4：监控、AI 评测与答辩增强

### 8.1 运行监控

项目已经提供 Actuator 和 Prometheus 指标。下一步建议接入 Grafana，并建立：

- API 请求量、P50、P95、P99 延迟。
- HTTP 4xx、5xx 错误率。
- 数据库连接池使用率。
- Redis 连接失败和限流次数。
- AI 调用耗时、失败率和各场景调用量。
- 文件上传失败率。

为每个请求增加 `requestId`，在 Nginx、后端日志和异常响应中保持一致。

### 8.2 备份恢复演练

至少完成一次完整演练：

1. 备份数据库和上传文件。
2. 创建新的空数据库。
3. 恢复备份。
4. 启动后端并检查 Flyway 历史。
5. 验证登录、帖子、活动、私信和 AI 会话。
6. 记录恢复时间和发现的问题。

恢复成功的标准不是“备份文件存在”，而是系统能够在新环境中正常运行。

### 8.3 AI Coach 评测

建议建立固定问题集，覆盖：

- 基础动作学习。
- 装备选择。
- 安全和受伤风险。
- 与滑板无关的问题。
- 诱导模型编造站内内容的问题。

记录以下指标：

- 引用是否真实存在。
- 引用与回答是否相关。
- 安全问题是否给出合理边界。
- 无资料时是否明确表达不确定性。
- 推荐内容的点击率和训练任务完成率。

答辩展示时应同时展示“功能效果”和“可量化评测”，不要只展示一段成功对话。

### 8.4 检索增强

在现有关键词检索稳定后，可以增加关键词与向量的混合检索：

1. 关键词召回保证专有名词和精确匹配。
2. 向量召回处理同义表达和自然语言问题。
3. 合并结果后进行重排。
4. 只把通过权限和公开状态检查的内容交给模型。
5. 所有回答继续返回可点击引用。

## 9. 每阶段统一交付要求

每个阶段完成后提交以下材料：

1. 变更说明。
2. 涉及文件列表。
3. 数据库迁移说明（如有）。
4. 测试命令和结果。
5. 性能前后对比（如有）。
6. 风险和回滚方法。
7. 页面截图或接口示例（影响用户体验时）。

推荐提交前检查：

```powershell
git status --short
git diff --check
mvn test
npm --prefix frontend audit --omit=dev --registry=https://registry.npmjs.org
npm --prefix frontend run build
Get-ChildItem -LiteralPath 'frontend\tests' -Filter '*.test.cjs' |
  Sort-Object Name |
  ForEach-Object {
    node $_.FullName
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
```

在安装 Docker 的环境中额外执行：

```powershell
docker build -t skatehub-backend:verify .
docker build -f frontend/Dockerfile -t skatehub-frontend:verify .
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.yml config
```

## 10. 推荐的第一轮实施范围

如果本轮时间有限，只实施以下内容：

- [ ] 轮换并清理地图配置。
- [ ] 忽略数据库备份、上传目录和本地环境文件。
- [ ] 整理并提交当前工作区改动。
- [ ] 为私信页面增加真实组件测试。
- [ ] 建立 1 条登录到私信发送的端到端测试。
- [ ] 优化 `RecommendationService` 的全表和循环查询。
- [ ] 将 `AdminPage.vue` 的审核模块拆成独立组件。
- [ ] 重新执行完整质量门禁并记录结果。

以上内容完成后，项目将从“功能较完整”进一步提升到“安全边界清晰、质量可验证、性能可持续优化”的状态。
