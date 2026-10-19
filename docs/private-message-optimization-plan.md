# SkateHub 私信功能优化方案

## 1. 当前状态

当前私信功能已经具备基础闭环：

- 用户主页可以发起私信，并跳转到 `/messages/:conversationId`。
- 私信页可以加载会话列表、打开指定会话、发送文本消息、标记已读。
- 后端有 `PrivateMessageController`、`PrivateMessageService`、`PrivateConversation`、`PrivateMessage`，数据库已有 `tb_private_conversation` 与 `tb_private_message`。
- `/api/messages` 写接口已经被通用限流覆盖，具备最基础的滥用防护。

但当前形态仍偏“最小可用”，距离完整商业项目常见的私信体验还有明显差距：消息无分页、会话无搜索/归档/置顶/免打扰、缺少实时刷新、缺少拉黑举报、前后端契约仍用 `Map`、未读统计存在 N+1 查询风险、前端发送体验没有失败重试和乐观展示。

## 2. 优化目标

私信功能应从“能发消息”升级为“可长期使用、可治理、可扩展”的站内通信能力。

- 完整性：支持分页、未读、搜索、会话管理、消息状态、异常重试。
- 体验：消息发送即时反馈，打开会话自动定位最新消息，切换会话不卡顿。
- 安全：支持拉黑、举报、敏感内容拦截、频率控制。
- 性能：避免一次性拉全量消息，避免会话列表逐条查未读。
- 可维护：后端返回明确 DTO，前端 API 契约稳定，便于测试覆盖。

## 3. 主要缺口

| 优先级 | 缺口 | 当前表现 | 风险 |
| --- | --- | --- | --- |
| P0 | 消息无分页 | `listMessages` 一次返回会话全部消息 | 老会话会越来越慢，移动端滚动卡顿 |
| P0 | 会话列表无分页 | 当前用户会话一次性全部返回 | 用户会话多时接口响应变慢 |
| P0 | 前后端长度不一致 | 前端 `maxlength=500`，后端允许 1000 | 用户体验和错误提示不一致 |
| P0 | API 返回 `Map` | 会话和消息视图靠字符串 key 约定 | 契约脆弱，测试和重构成本高 |
| P0 | 发送无乐观态/失败重试 | 点击后只能等待接口完成 | 弱网下用户不确定是否发送成功 |
| P1 | 无实时刷新 | 需要手动刷新或重新进页面 | 私信像留言板，不像聊天 |
| P1 | 未读统计有 N+1 风险 | 每个会话单独 count 未读 | 会话数量增加后数据库压力上升 |
| P1 | 无会话管理 | 无置顶、归档、免打扰、搜索 | 用户难以整理长期会话 |
| P1 | 无拉黑/举报 | 收到骚扰后没有治理手段 | 社区安全风险 |
| P2 | 无附件能力 | `messageType` 字段存在，但只支持文本 | 无法发图片、视频、场地截图等内容 |
| P2 | 无在线/输入中状态 | 没有 presence/typing | 聊天即时感不足 |

## 4. 分阶段路线

### 阶段 A：补齐基础可用性，建议优先做

目标：先解决性能、契约和发送体验，避免后续功能建立在脆弱接口上。

- 后端新增 DTO：
  - `PrivateConversationView`
  - `PrivateMessageView`
  - `PrivateMessagePageResponse`
  - `PrivateMessageSendResponse`
- 消息列表改为游标分页：
  - `GET /api/messages/conversations/{conversationId}/messages?beforeMessageId=&size=30`
  - 返回 `list`、`nextBeforeMessageId`、`hasMore`。
- 会话列表改为分页：
  - `GET /api/messages/conversations?page=1&size=20&keyword=&unreadOnly=false`
- 前端私信页支持：
  - 向上滚动加载更早消息。
  - 发送消息乐观展示。
  - 发送失败显示失败态，并支持重试。
  - 输入框改为多行 `textarea`，长度统一 1000。
  - 空会话、加载中、失败状态用页面内状态替代 `alert`。
- 测试补充：
  - 后端分页、越权访问、发送校验、已读标记。
  - 前端保留 `/messages/:conversationId` 直达能力，新增加载更多和失败重试契约测试。

### 阶段 B：提升聊天体验和会话管理

目标：让私信像真正的消息中心，而不是静态消息列表。

- 增加低成本刷新机制：
  - 第一版可用前端轮询：页面可见时每 8 到 12 秒刷新当前会话与未读数。
  - 页面隐藏时暂停轮询。
  - 后续再升级到 SSE 或 WebSocket。
- 增加会话搜索：
  - 按对方用户名和最近消息内容搜索。
  - 前端在会话列表顶部放搜索框和“未读”筛选。
- 增加会话管理：
  - 置顶、归档、免打扰。
  - 会话列表默认隐藏归档，提供归档入口。
- 已读能力升级：
  - `POST /api/messages/conversations/{conversationId}/read` 支持 `lastReadMessageId`。
  - 避免打开会话时误把未来新消息全部标记已读。
- 未读数优化：
  - 建议新增会话用户状态表，维护每个用户在每个会话下的未读数与最后已读消息。

### 阶段 C：补齐社区安全治理

目标：让私信适合开放社区上线。

- 拉黑：
  - `POST /api/messages/users/{targetUserId}/block`
  - `DELETE /api/messages/users/{targetUserId}/block`
  - 被拉黑后禁止发起新会话和发送消息。
- 举报：
  - 支持举报单条消息或整个会话。
  - 举报进入现有管理端审核体系。
- 内容治理：
  - 发送前检查空白、重复刷屏、链接/联系方式风险词。
  - 接入已有 AI 风控能力时，可对高风险内容进行拦截或进入待审核。
- 频率控制：
  - 保留通用 `/api/messages` 限流。
  - 增加私信专属策略：新用户、陌生人首次私信、重复内容、短时间多目标发送应更严格。

### 阶段 D：商业化增强能力

目标：增强社区互动效率，但放在基础稳定之后。

- 图片/附件消息：
  - 利用现有文件上传能力，扩展 `messageType=image/file`。
  - 限制大小、类型、数量，图片走安全预览。
- 输入中、在线状态：
  - WebSocket 建立后再做 typing/presence。
- 消息撤回：
  - 限定 2 分钟内可撤回。
  - 后端保留审计字段，前端显示“消息已撤回”。
- 通知联动：
  - 新私信进入站内通知中心。
  - 顶部导航显示私信未读徽标。

## 5. 后端设计建议

### 5.1 API 契约

建议保留当前路径语义，但增加新分页接口，旧接口可以短期兼容。

```text
POST   /api/messages/conversations
GET    /api/messages/conversations?page=1&size=20&keyword=&unreadOnly=false
GET    /api/messages/conversations/{conversationId}/messages?beforeMessageId=&size=30
POST   /api/messages/conversations/{conversationId}/send
POST   /api/messages/conversations/{conversationId}/read
POST   /api/messages/conversations/{conversationId}/pin
DELETE /api/messages/conversations/{conversationId}/pin
POST   /api/messages/conversations/{conversationId}/archive
DELETE /api/messages/conversations/{conversationId}/archive
POST   /api/messages/conversations/{conversationId}/mute
DELETE /api/messages/conversations/{conversationId}/mute
POST   /api/messages/messages/{messageId}/report
POST   /api/messages/users/{targetUserId}/block
DELETE /api/messages/users/{targetUserId}/block
GET    /api/messages/unread-count
```

### 5.2 数据模型

建议新增 `tb_private_conversation_user_state`，把“会话本身”和“某个用户对会话的状态”拆开。

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

建议给 `tb_private_message` 增加：

```text
client_msg_id
status
deleted_by_sender
recalled_at
extra_json
```

建议索引：

```text
tb_private_message(conversation_id, message_id)
tb_private_message(receiver_id, read_status)
tb_private_conversation_user_state(user_id, archived, pinned, update_time)
tb_private_conversation_user_state(user_id, unread_count)
```

### 5.3 服务层

- `sendMessage` 使用事务：
  - 校验会话参与者。
  - 校验双方拉黑关系。
  - 保存消息。
  - 更新会话最后消息。
  - 增加接收方未读数。
  - 清除发送方归档状态。
- `markRead` 使用 `lastReadMessageId`：
  - 只标记当前已展示到的消息。
  - 同步更新 `last_read_message_id` 与 `unread_count`。
- `listConversations` 不再逐条 count 未读：
  - 直接读取 user state 表中的 `unread_count`。

## 6. 前端体验方案

### 6.1 会话列表

- 顶部增加搜索框、未读筛选、刷新按钮。
- 每个会话展示头像、昵称、最近消息、时间、未读数。
- 支持置顶、归档、免打扰状态。
- 会话为空时给出明确动作：去社区、去用户主页发起私信。

### 6.2 消息区

- 进入会话后默认定位最新消息。
- 向上滚动加载更早消息。
- 新消息进入时，如果用户在底部则自动滚到底部；如果用户在看历史消息，则显示“有新消息”提示。
- 自己发送的消息展示：
  - sending
  - sent
  - failed
  - read
- 失败消息支持重试，不直接丢失输入内容。

### 6.3 输入区

- 单行 input 改为 textarea。
- 长度统一 1000，显示剩余字数。
- 支持 Ctrl/Cmd + Enter 发送。
- 发送按钮在空内容、超长、发送中时禁用。

### 6.4 刷新策略

第一版不必马上上 WebSocket，可先做更稳的轮询：

- 页面可见：每 8 到 12 秒拉取当前会话新消息和会话列表摘要。
- 页面隐藏：暂停轮询。
- 发送成功后立即刷新当前会话摘要。
- 后续若用户量增长，再升级 SSE 或 WebSocket。

## 7. 验收标准

### P0 验收

- 会话列表和消息列表都支持分页。
- 打开有 1000 条历史消息的会话不会一次性加载全部数据。
- 前端输入长度与后端一致，均为 1000。
- 发送消息在弱网下有 sending 状态，失败后可重试。
- 后端不再用 `Map<String, Object>` 作为主要响应模型。
- `/messages/:conversationId` 直达会话能力保持不变。

### P1 验收

- 私信页停留时可以自动看到新消息。
- 顶部导航或消息入口能显示私信未读总数。
- 会话可以搜索、置顶、归档、免打扰。
- 已读标记不会误标记未展示的新消息。
- 未读数查询不随会话数量线性增加数据库 count 次数。

### P2 验收

- 用户可以拉黑、取消拉黑、举报消息。
- 被拉黑关系下不能继续发送私信。
- 高频骚扰和重复内容会被限流或拦截。
- 管理端能处理私信举报。
- 支持图片消息时，有文件类型、大小和安全预览限制。

## 8. 建议开发顺序

1. 先做 DTO 和分页接口，补后端测试。
2. 改造 `MessagesPage.vue`：分页加载、textarea、乐观发送、失败重试。
3. 新增会话用户状态表，优化未读数和已读逻辑。
4. 加会话搜索、置顶、归档、免打扰。
5. 加拉黑、举报和私信专属限流策略。
6. 做轮询刷新；稳定后再考虑 SSE/WebSocket。
7. 最后扩展图片/附件、输入中、在线状态等增强功能。

## 9. 推荐优先落地范围

如果只做一轮开发，建议先完成以下内容：

- 后端 DTO 化。
- 消息游标分页。
- 会话分页。
- 前端 textarea + 1000 字限制。
- 乐观发送 + 失败重试。
- 私信未读总数接入导航。
- 后端补 5 到 8 个核心单元测试。

这组改动投入可控，但能显著提升完整度、性能边界和商业上线可信度。
