# 18 项审查意见核实与整改复验

验证日期：2026-10-20（Asia/Shanghai）。基线：`1230398141842130c1cb10d1f21073d3b85f2d69`。

## 结论与更正

原意见共 18 项：9 项确认、5 项部分成立、3 项误报、1 项当前调用域未复现。14 项完成相应范围整改；4 项以反证或现有输入契约解释关闭。原编号仅用于追踪，并不表示存在已经证实的生产 P0 宕机。

必须更正：限流配置原已存在（应用默认关闭，Compose 示例默认开启，真实部署状态未读取）；报价计算原已按 key 去重，问题在 SQL 加载历史行；管理编辑不持续写导入指纹；稳定 key 有数据库唯一约束；正常 Flyway 不会重复执行已应用迁移；原全局异常协议是 HTTP 200/业务码 500。没有为这些错误推断增加缓存、归档、静默合并或改写旧迁移。

| 原 ID | 核实结论 | 整改或解释关闭 | 主要可执行证据 |
| --- | --- | --- | --- |
| P0-1 | 确认，修正为 P1 | 公共目录短 SERIALIZABLE 只读快照先 meta 后 rows；CPU 计算移出事务。写入、保存保留 meta 锁直到提交。 | `BoardReadRemediationTest` 并行公共读及并发提交一致性；原保存冲突/回滚测试 |
| P0-2 | 部分成立，P1 | 四条精确公开计算 POST 共用可配置 IP 预算，保持原启用/Redis 故障策略。 | `BoardRateLimitTest`；`BoardRateLimitHttpReviewTest` 真实 Spring HTTP 四路第 3 次 429 |
| P1-1 | 部分成立，P2 | 数据库取每 key 最新报价，包含最新 inactive，保留历史记录。 | `latestInactiveDoesNotResurrectHistoryAndReadQueriesUseLatestOnly` |
| P1-2 | 确认，P1 | 默认只信 socket peer；可信代理按链从右向左解析；仓库外层 Nginx 覆盖 XFF。 | `BoardRateLimitTest` 畸形 IP/链及配置控制；真实 HTTP 伪造头、共享桶、可信链/IPv6 控制 |
| P1-3 | 确认，P1 | 三图表跟随当前 DOM 释放/重建实例和 observer；补注册已有 graphic 消费者。 | `ChartLifecycle.spec.js`；真实 ECharts `chart-lifecycle.spec.cjs` |
| P2-1 | 确认，P2 | 会话分页成功后提交页码，重试失败页，append 去重。 | `MessagesPage.spec.js`；浏览器断言 1→2 失败→2 成功→3 |
| P2-2 | 确认，P2 | 捕获会话 ID/epoch/请求序号，校验迟到 success/error/finally/read/list/older/poll/send 及卸载。 | 消息组件竞态回归；真实浏览器慢 A/快 B、B 已读及旧消息游标 |
| P2-3 | 误报关闭 | 仅 IMPORT 持久化 dataset fingerprint，普通管理动作不写入；未引入清理器。 | 原独立三次管理编辑反证；`BoardCatalogService.writeDataset` 的 IMPORT 条件保持 |
| P2-4 | 确认，P2 | 五组资源 DB 分页/count，稳定 key 顺序，报价计逻辑 key 最新版本且保留草稿。 | `administratorPagesUseSqlTotalsKeyOrderingAndIncludeDrafts` |
| P2-5 | 部分成立，P3 | 缺 meta 记录内部诊断；对外安全中文和既有 board 局部 HTTP/业务 503，其他模块协议不变。 | `missingMetadataHasInternalDiagnosticButSafeLocal503Envelope`；其他模块 HTTP 200/业务 500 控制 |
| P2-6 | 误报关闭 | 产品/规格唯一约束存在；不为假设坏库加静默 toMap merge。 | 原独立真实 H2 duplicate key 负控；实体/已应用 V9 唯一约束字节保护 |
| P2-7 | 部分成立，P3 | 复用 ObjectMapper 写错误 JSON；原固定消息无已证实输入注入路径。 | 引号、反斜杠、中文序列化控制及真实 HTTP 429/503 信封 |
| P2-8 | 误报关闭 | 正常 Flyway 只执行未应用迁移；V9/V10 不改 checksum，不用 IF NOT EXISTS 掩盖漂移。 | 原独立 migrate 再次执行 0；迁移文件保持原字节 |
| P3-1 | 部分成立，P3 | 三处统一已知 Mid/Standard 错型理由，保留原精确 key 拦截和新合法报价路径。 | `namedMismatchPolicyDoesNotRejectASeparateExplicitSyntheticQuote`；原参考价回归 |
| P3-2 | 确认，P3 | 请求与数据集共用 ASCII stableKey，拒绝冒号，保持 200 长度边界。 | `stableKeysHaveSharedAsciiAndLengthBoundaries` |
| P3-3 | 确认，P3 | 局部展开资格判断、规则/枚举及选板模板，保持 2000 上限和保存/连带确认协议。 | 原计算/边界测试、选板组件与合同、T07 真实 HTTP/浏览器 |
| P3-4 | 当前域未复现 | clone 调用对象均为纯 JSON，仅注释输入契约；未替换实现或加依赖。 | `state.spec.js` / `referencePricing.spec.js` 和当前源码调用核对 |
| P3-5 | 确认，P3 | 每段连续轮询失败仅一次固定脱敏 warning，成功恢复后重置，手动恢复保留。 | 消息组件连续失败/恢复/旧 A 失败和手动刷新控制 |

## 本地复验与原数据保护

后端交付：一次 clean 全套 408 项，再增补 2 项并重跑受影响套件，当前去重 XML 410 项；不是一次全套 410 调用。前端交付：Node 20，84 单测、39 合同、构建、27 Edge 浏览器测试及生产依赖 audit 0。

最终审查独立复验 8 个 Java 套件共 113 项（其中新增真实 Spring HTTP 限流 4 项）、前端定向 32 单测、真实 ECharts/消息浏览器 3 项，以及 Java 集成测试中启动的 T07 主流程/连带调整/原参考库浏览器各 1 项，全部通过。另做新生产构建（hash `2be8060625f0161c`）。次数按本轮实际重跑的不同套件计算，不将交付与审查多轮重复相加；其余未变测试留待最新 head 完整 CI。Redis 故障/元数据缺行的预期错误日志来自合成负控。

原资料、正式 reference v1、既有迁移、依赖锁、CI 门禁及两份既有视觉测试的精确备案图 mock/内联头像保持原字节。原 21 条参考价格、0 条满足核验精确价门槛、RECORDED 来源与日期不变；600 元完整参考方案 323/446/580 元、100 元不足差额 223 元、缺价总额 null/已知小计 786.20 元均保留。不可变历史、权限隔离、版本冲突和连带调整 `apply(selection, null)` 保留。

旧 audit 中确认缺陷存在的 characterization 测试留在外部证据归档，不作为修复通过门禁，也不提交原工作区未跟踪材料、运行环境、日志、截图或密钥。

## 发布前仍需核实

- 当前本地 Java 21 编译目标 17、H2、Windows/Edge 结果分别记录；Linux Java 17/Chromium 及两镜像构建以该 PR 最新 head 完整 CI 为准。CI 成功只更新 PR/外部审查记录，不为记录结果再制造提交。
- SERIALIZABLE 在 MySQL/InnoDB 可能取得短期共享锁；不声称完全无锁。实际 MySQL 并发、死锁、池容量和吞吐尚未验收。
- 可信代理默认空：代理后的请求可能按同一 socket peer 聚合。上线前须核定实际直接代理 IP/CIDR、多层 LB 和 Nginx 运行配置；示例 30 次/分钟不是容量实测结论。不可盲信宽泛私网或客户端 XFF。详见 [代理边界说明](rate-limit-proxy-boundary.md)。
- Redis 在测试中使用合成存储；真实 Redis 故障与生产容量没有验收。已有默认拒绝 503、显式配置才放行的策略保留。
- 生产依赖 audit 0 不等于全依赖 0；前轮完整开发工具链仍有 44 项（5 critical/22 high/17 moderate），本轮未升级工具链或降低门禁。
- 本轮不合并、不部署、不读写真实 env、不操作生产数据库或 DNS，也不调用付费 Provider。
