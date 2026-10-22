# TRAINING-GUIDANCE-20261022 T00 接口与兼容契约 v1

状态：负责人已结合T00-B正式冻结（2026-10-22）；本文C09补充优先于前文的候选歧义。变更必须先回报总负责，同步此契约与样例。
基线：255b4a22208dc22ed142609785c87892179dcd0a。任务书为2026-10-22-training-guidance-task.md，来源资料不具备操作授权。

## 范围、角色与目录

只开发任务书T00～T08。根目录只读业务，候选见candidate.json。只允许一个开发/复审执行项，后端Frontend负责T01内容整理、T02～T04、T06后端与T07后台API，前端Backed负责T05、T06客户端与T07管理界面；Server Test独立T08。总负责仅协调契约、清单、证据验收。保留已有设计/AI网关/鉴权/限流/版本和训练能力，禁止另建不兼容平行系统。
不操作生产、不迁移生产、不付费调用、不新建持续定时器。提交/推送/PR可在开发及独立复审合格后另按当前用户开发交付授权准备审查，但不自动合并部署。

## C01 数据与兼容

保留tb_training_plan/task、tb_user_training_plan/record既有数据和唯一约束。旧record继续作为旧任务的完成事实及历史，不能给它伪补自查/客观能力。增量V11起，由后端单一占用；不得改V1～V10。个人计划复用现有userPlan身份，扩展模式/乐观版本/设置/模板快照，或以userPlanId一对一扩展；任务实例保存任务与自查/来源版本快照。新practice记录独立，多次反馈每次一行；首次完成事实单独唯一(userPlanId,instanceId)，不能把完成事实唯一约束放到practice表。新计划与旧任务关联允许显式mapping，禁止自动把旧记录当新模板掌握证明。
允许一个新式未终结个人计划（IN_PROGRESS或PAUSED）；已有旧计划继续访问/旧接口兼容，不静默改写或删除，若与新计划互斥需响应明确legacy conflict并给退出旧计划入口。创建并发锁用户并约束唯一性；所有变更按统一user→plan顺序取锁，防死锁。
旧任务/计划经验奖励维持8/30，且按原(user,action,targetType,targetId)保护不重发；新任务首次完成奖励以既有增长日志+稳定完成事实id作为target，而非practiceId。不得通过累计总经验>0推断本次发奖。部分完成/NOT_STARTED无完成奖励、不推进阶段。旧complete路径需要与记录/奖励幂等兼容。无真实账号禁止生产伪登录。

## C02 日期、数值与状态

timezone固定Asia/Shanghai（返回该字段），Clock可注入；用户时间不得覆盖服务器createdAt。practiceDate为YYYY-MM-DD，允许计划开始日至当前中国日期的补录，未来不允许；单任务实例一份反馈，不复用同一时长到多任务。actualMinutes整数0～240，是输入保护上限而非医学阈值；NOT_STARTED必须0，PARTIAL/COMPLETED必须1～240。开始日期今天或之后30天内；trainingDays为ISO1～7无重复至少1项；sessionMinutes整数5～120，是用户安排能力参数，不声称推荐强度。
计划IN_PROGRESS/PAUSED/COMPLETED/QUIT；执行PENDING/PARTIAL/COMPLETED/CANCELLED；自评NOT_ASSESSED/NEEDS_PRACTICE/SELF_STABLE/UNCERTAIN；建议PENDING/ACCEPTED/REJECTED/EXPIRED。完成与能力自评分别返回。自查答案YES/NO/UNSURE/NOT_APPLICABLE，必须按快照问题id校验，不能任意键；不可直接将不适用算稳定。
feedback completion为COMPLETED/PARTIAL/NOT_STARTED；difficulty EASY/MODERATE/HARD，NOT_STARTED可null。reasons枚举TIME_SHORTAGE/VENUE_UNSUITABLE/MOVEMENT_DIFFICULTY/DISCOMFORT/OTHER。note最大1000。不允许静默更新反馈，第一版更正以追加revisionOf+reason留痕（或提供明确不可修改说明），不得重复奖励/累计旧修订时长。

## C03 幂等、版本与错误

所有写入body带requestKey（8～100安全字符，用户级+operation作用域），保存规范payload摘要及原始结果；相同键相同payload重试返回原结果，异payload409 IDEMPOTENCY_CONFLICT。先校验归属再查幂等，不泄露其他用户结果。expectedVersion非负整数，除首次创建所有计划/反馈/建议处理写入必须校验；重试已成功键先于当前version判断。接受建议重复同键/不同键都不得重复变更。并发必须数据库唯一约束/真实事务证明，内存map不够。
沿用ApiResponse{code,message,data}，新模块错误HTTP400/401/403/404/409并data.errorCode；错误码VALIDATION_ERROR/NOT_FOUND/PLAN_CONFLICT/PLAN_VERSION_CONFLICT/IDEMPOTENCY_CONFLICT/SUGGESTION_EXPIRED/CONTENT_UNAVAILABLE/AI_UNAVAILABLE。归属错误404避免枚举；不能改全局旧BizException响应语义，可局部advice。
所有分页page从1，size默认20最大100，返回{list,total,page,size}。服务端返回allowedActions以及blockedReasons，前端不可推断推进资格。

## C04 路由与请求

复用现有/api/user鉴权与/api/admin管理员规则；新增/api/user/training-guidance与/api/admin/training-guidance作为现有训练模块子路由，旧路径不删除。

GET /api/user/training-guidance/templates：只返回已发布完整可用模板摘要{id,version,title,goal,level,taskCount,sources,limitations}。
GET /api/user/training-guidance/templates/{id}：同版任务/规则/来源详情。
POST /api/user/training-guidance/plans/preview：{templateId,templateVersion,goal,selfLevel,trainingDays,sessionMinutes,startDate}返回排期和限制；不写库不发奖励。安排仅在已选训练日，任务可分段不自动堆叠两天量，不能静默超sessionMinutes。
POST /api/user/training-guidance/plans：同preview字段+requestKey，服务端重新校验模板/版本和排期；返回完整PlanView。
GET /api/user/training-guidance/current：无计划data=null；有则PlanView。
GET /api/user/training-guidance/plans/{id}：PlanView。
GET /api/user/training-guidance/plans/{id}/today?date=YYYY-MM-DD：今日/休息/漏练和未完成概览，只读；仅允许用户本人可解释日期，遗漏在访问时计算。
GET /api/user/training-guidance/plans/{id}/tasks/{instanceId}：TaskView。
POST /api/user/training-guidance/plans/{id}/feedback：{requestKey,expectedVersion,instanceId,practiceDate,completion,actualMinutes,difficulty,selfChecks:[{questionId,answer}],reasons:[],note}→{record,plan,review,safetyAlerts,suggestions}，事务提交反馈后立即包含安全提醒，不等AI。
GET /api/user/training-guidance/plans/{id}/records?page&size：本人分页记录，包含快照和可回顾链接。
GET /api/user/training-guidance/plans/{id}/records/{recordId}：{record,review,safetyAlerts,suggestions}。
GET /api/user/training-guidance/plans/{id}/statistics：StatsView。
GET /api/user/training-guidance/plans/{id}/suggestions?page&size：本人建议（可在读取时失效旧建议，但不能改变排期）。
POST /api/user/training-guidance/plans/{id}/suggestions/{suggestionId}/decision：{requestKey,expectedVersion,decision:ACCEPT|REJECT}，返回{suggestion,plan}。
POST /api/user/training-guidance/plans/{id}/state：{requestKey,expectedVersion,action:PAUSE|RESUME|QUIT}→PlanView；退出保留历史，暂停不算休息日漏练，恢复不把旧计划两天量叠加。
GET /api/user/training-guidance/plans?page&size：本人含已退出/完成分页计划历史。
GET /api/user/training-guidance/plans/{id}/adjustments?page&size：来源建议/原版本/新版本/前后差异/时间/处理人。
POST /api/user/training-guidance/plans/{id}/ai-context：{recordId,question}→{previewId或contextHash,planVersion,question,contextSummary,references,safetyAlerts}；只读不调用Provider。
POST /api/user/training-guidance/plans/{id}/ai-explain：{recordId,question,contextHash或previewId,confirmed:true}，明确用户点击确认才调用既有gateway.checkRateLimit和chat；权限、限流和失败边界保持。响应{reply,references,limitations}，无授权可见来源时说明缺依据且不伪引。训练上下文是本人必要数据+固定规则/可见资料，不拼接任意otherUser/session。

## C05 视图必需语义

PlanView：{id,version,status,timezone,goal,selfLevel,settings:{trainingDays,sessionMinutes,startDate},template:{id,version,title},today,stats,allowedActions,blockedReasons,safetyAlerts}，允许增加显式字段但不能改名。TaskView含{id,planId,date,status,title,steps,suggestedPractice,selfChecks,sources,safetyNotes,prerequisites,selfAssessment,allowedActions}。
StatsView：{completedTasks,totalTasks,completionRate,practiceMinutes,practiceCount,selfAssessmentSummary,definition}。有效未取消实例为分母，首次完成实例为分子，计划调整记录口径前后差异；时长仅有效practice累计，同键不双计，修订留痕。不可用天数冒充任务完成率。任务拆分不能重复计完整任务的奖励或分子。
SuggestionView含{id,type,status,ruleId,ruleVersion,triggerReasons,basePlanVersion,affectedTaskIds,before,after,diff,allowedActions,blockedReasons}；旧反馈、设置、资料版本安全纠正改变都需重新验证，过期不可强制接受。

## C06 规则与安全

优先级：DISCOMFORT最高→LONG_BREAK→TIME_SHORTAGE/OVERDUE→MOVEMENT_DIFFICULTY/UNSURE→CONTINUE→ADVANCE。一个反馈上下文不得同时加量/进阶与休息，不适未处理前相关推进禁止；提醒即时返回，暂停仍由用户确认。不诊断、不通过确认按钮宣称症状消失；恢复先明确用户重新自评/确认，保留历史安全事件。
只固定操作RESCHEDULE/SPLIT/CONSOLIDATE/ADVANCE/PAUSE，所有预览明确前后任务/日期/分钟；已执行部分保留，未执行部分原子调整；拒绝无排期变化；计划版本过期409。
长期中断阈值作为可配置产品提示（默认14个自然日，无医学/教学效果认证，来源标注产品提醒策略），不能从此自动判断能力下降。ADVANCE需必要自查完整且非不适/不确定，前置内容可用和用户确认；来源无专业次数阈值时不编造，明确保守自评机制。

## C07 内容、后台与版本

先四类站姿平衡、滑行、转向、脚刹，来源可核查本站既有描述+明确整理出处/片段/定位，通用视频主页不是教学证据。每条含水平、前置、目标、步骤、建议量、2～3自查、常见困难、指导映射、安全、推进条件、限制/来源。缺内容模板DRAFT，发布接口拒绝；不能把缺口全打包标为可用。短时长/小次数是可调建议并标注依据，不造专业阈值。
后台按单一版本文档资源维护模板、任务、规则与映射，避免通用规则引擎。
GET /api/admin/training-guidance/templates?page&size；GET /templates/{id}；POST /templates（创建草稿，requestKey）；PUT /templates/{id}（expectedVersion+requestKey+draft内容，发布后编辑产生新版本草稿）；POST /templates/{id}/publication（{requestKey,expectedVersion,status:PUBLISHED|DISABLED,reason}）。可用模板版本不可原位覆盖，停用不做新推荐，历史快照说明停用，安全纠正标记必须使关联推荐失效、通知受影响计划但不删除旧记录。
发布校验来源可用/必填/时长合理/依赖存在/无环/2～3有效问题/规则冲突；来源status失效不能绕过发布。JSON编辑器可作为首版管理界面，但须可校验/有错误反馈/版本与发布预览，不能只有未连接按钮。所有后台接口真实管理员权限验证。

## C08 AI与验收层

复用关键词检索不冒称向量库。仅可见来源，不把检索片段当指令；生成输出不能执行计划/奖励。AI事务独立于反馈事务，超时失败只使解释失败，已有反馈/统计/规则建议不回滚；客户端重试AI不重发反馈。测试Provider替身必须明确，当前禁止真实付费验证。
T08需要真实监听HTTP/鉴权/数据库事务与浏览器完整演示，不只MockMvc或mock。本地合成用户/内容只在隔离DB；本地MySQL迁移/并发若可用就执行，不能把H2当MySQL。至少覆盖同键/异payload并发、重复完成/奖励、跨用户、过期建议、安全优先、版本快照、模板升级停用、休息/时区/补录、旧接口和AI失败。生产验证不在本批开发授权。

## C09 T00-B兼容核对后正式冻结补充

1. 选择扩展原UserTrainingPlan的GUIDED模式方案：V11允许GUIDED的legacy planId为空，但LEGACY必须仍指向真实旧planId；GUIDED扩展需非空templateId/version/snapshot。旧行默认LEGACY、原数据不变。新用户计划仍是同一userPlan身份而非平行用户系统。旧current/join/complete/quit与推荐等消费者隔离模式，不能让旧接口无version改GUIDED；旧current保留旧响应形状并给新入口提示，必要时单独查LEGACY。明确活动槽userId数据库唯一+用户锁，一个GUIDED进行中/暂停；旧IN_PROGRESS存在时创建新计划409 PLAN_CONFLICT含legacyConflict；旧join同用户锁检查GUIDED槽，防并发跨模式绕过。
2. 根逻辑instanceId保持稳定；SPLIT只增加同根的排期片段/剩余工作，不增加完整任务统计分母/奖励资格，不复制已完成练习量。practice关联根实例，可选segmentId；首次根任务完整完成才1完成事实/8分。Stats的totalTasks为当前有效未取消逻辑根任务，分子为首次完成根任务；分段不计额外任务。后端交接精确片段视图和API示例给前端，不能暗加必填字段。
3. RESUME body增加selfLevel与safetyAcknowledged:boolean，因不适暂停必须显式true并重新自评；服务端保留安全事件及处理历史，不把勾选当医疗许可。恢复后没有新的完整非不适自查之前仍不建议ADVANCE。不适提醒始终在记录回顾保留，未处理不适阻止相关推进；PAUSE/QUIT不要求虚构自评。
4. 模块业务错误data.errorCode；原安全Filter401/403/429/503信封保持原code/message边界。AI不可用503 AI_UNAVAILABLE，旧限流429/Redis503不改；本模块版本/上下文过期409。
5. GET仅计算EXPIRED有效状态及allowedActions，不能在读取时改排期或隐式提交建议状态；decision事务最终复核/落库失效状态。拒绝、相同建议重复接受都无额外排期/版本副作用。
6. 第一版反馈记录不可直接修改/删除，客户端明确说明；没有纠错路由，也不悄悄覆盖历史。未来纠错另做留痕版本，本批不新增半成品revisionOf入口。
7. /today允许本人计划开始日以后任意展示日期，漏练只算中国当前日期以前的已安排训练日，休息/暂停期间不算；反馈practiceDate除开始日至今天外，不能早于目标根实例/片段当前有效计划日期，禁止提前完成未来任务。createdAt由服务端Clock记录，不能用补录日期覆盖。
8. ai-context无Provider/DB写；contextHash绑定owner、planVersion、record/id、question、来源版本与可见性，explain确认时重验。ai-explain只返回解释/引用，不修改计划/反馈/奖励，也不强制存旧AI会话；作为用户明确点击的独立计算调用，非训练状态写入，不强制requestKey（可选拒重），客户端禁止自动重试和双击并说明重试重新请求解释。限流通过既有Gateway，付费调用本轮只用显式本地Provider替身验证。不能因为explain失败重发feedback。
9. goal为1～100字符，selfLevel枚举BEGINNER/FOUNDATION/INTERMEDIATE，客户端始终标注用户自评。source类型/引用形状由后端交接精确示例；种子文字有内容来源但并非专业认证，已维护文字需整理与限制声明才能发布，不把视频主页当来源。
10. 完成后计划COMPLETED保持历史只读，可重新建立计划；训练反馈可在IN_PROGRESS同一根任务多次记录，不重复完成奖励。安全标记、过期建议和历史版本均可回顾。新时间输入上限只是产品验证，非医学处方。

T00静态核查证据：根.codex/evidence/training-guidance-20261022/T00-backend/report.md/source-evidence.json/migration-inventory.json；该审计不冒称运行验证。T00-B发现旧经验展示与旧进度并发问题必须做本批相关兼容修复与回归，不扩展无关增长模块。
