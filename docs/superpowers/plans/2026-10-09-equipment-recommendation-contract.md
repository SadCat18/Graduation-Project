# 选板助手第一版冻结契约 v1

日期：2026-10-09。负责人：coordinator。状态：T00 已冻结开发契约；不表示数据已核实、功能已实现或获准上线。

来源任务书：`2026-10-09-equipment-recommendation-task.md`。用户已明确要求登记、冻结接口契约并派发开发。用户的串行协调要求优先于任务书中 T01/T02 可并行的建议。

## 1. 已核对基线与选择

- 仓库：`<repository-root>`，master，HEAD `5731c24e084e731285c92914b46ee5215729c42a`；存在前后端、迁移和部署配置的既有未提交修改，详见 T00 证据文件，不清理或覆盖。
- 旧器材模型 `SkateEquipment` 将价格和规格作为展示文本；`/api/skate/equipments`、详情及收藏接口保留现有响应，原 AI 教练继续读取旧模型。
- 新模块使用独立结构化表、现有 JPA/Jackson/ApiResponse/JWT/Vue/axios，不新增规则引擎、向量库、付费 AI 或电商抓取系统。
- `equipment.json.records` 实测 50 条：板面 11、桥 7、轮子 10、轴承 8、砂纸 4、螺丝 3、垫片 3、整板 4；26 条含 cn_price，22 条 amount 为 JSON 数值。不能据此断言有 22 条可用报价。
- 输入文件 SHA256：`4D9A61CB207D5CDD416CC86BA2AA1F6AAEB36EFA50556680304E08BDCD530016`。任务书 SHA256：`FED61DCB8B706F4E1D57A88B00F984F0E82F6A0BCBE7133E16D5B1C7BEA411AE`。
- 本次未重新联网验证真实性、价格、库存或授权。来源日志中的历史登录授权不能视为本次账户操作授权。

## 2. 版本、单位、来源与规范数据

规范文件为 `docs/equipment-data/normalized-equipment.v1.json`：顶层 `schemaVersion:1, datasetVersion, products, variants, quotes, rules, templates, mapping, gaps`。原 equipment.json、CSV、种子 SQL 保留，不自动执行种子 SQL。

引用 ID 均为稳定字符串 key；数据库内部使用 Long ID。API 对外统一使用 key。产品 key 可复用 record_id；规格 key 应明确尺寸/结构/SKU，不能仅用易漂移的数组下标。映射保留原 record_id、原路径和拆分依据。

通用来源结构：`{url,title,collectedAt,verificationStatus,evidenceRef,note}`，状态 `RECORDED|VERIFIED|INSUFFICIENT`；本地已有资料的转写使用 RECORDED，必须有可复核证据才能提升为 VERIFIED。未知值 null，有缺口说明；不以 0 或默认通过替代。

所有金额为整数分（Long，字段后缀 Cents），CNY 为推荐唯一币种；输入预算为正整数分。使用 BigDecimal 精确解析原始十进制金额，禁止浮点累计。尺寸使用十进制数且字段标单位：宽/长/轴宽 Inch，高度/直径/螺丝长 Mm；1 inch = 25.4 mm 精确换算，保留原始规格和单位。硬度拆 `hardnessValue,hardnessScale`，A/B 不擅自等同。时间为含时区 ISO8601，采集日独立保留 YYYY-MM-DD。

| 对象 | 必须字段及含义 |
| --- | --- |
| Product | key, legacyEquipmentId(nullable), sourceRecordId, category, brand, model, active, description, suitabilityTags[], levelTags[], sources[], imageSource(nullable), imageLicenseStatus；分类 DECK/TRUCK/WHEEL/BEARING/GRIP/HARDWARE/RISER/COMPLETE |
| Variant | key, productKey, label, active, specs, rawSpec, sources[], verificationStatus；一条对应具体可购买规格；不把多个桥结构、轮型或尺寸混为一个规格 |
| Quote | key, variantKey(nullable only for display), version, currency, amountCents(nullable), displayPrice, priceKind(EXACT/RANGE/FROM/UNKNOWN), market(CN/OVERSEAS/UNKNOWN), purchaseScope, packageQuantity(nullable), packageUnit, shippingCents(nullable), taxCents(nullable), landedPriceConfirmed, collectedAt, validUntil(nullable), verificationStatus, active, sources[] |
| Rule | key, version, type, severity(HARD/ADVICE), scope, conditions, outcome, explanation, sources[], verificationStatus, active；只有白名单类型与结构化条件，禁止 eval/脚本表达式 |
| Template | key, version, purpose, requiredCategories, selectionConditions, reasonCodes[], sources[], verificationStatus, active；类型 ACTION_BEGINNER/CRUISE_BEGINNER/BALANCED_BEGINNER |
| Snapshot | key, ownerId, name, note, demand, selection, calculationVersion, catalogVersion, templateVersions, ruleVersions, quoteSnapshots, items, totals, checks, createdAt；完整 JSON 快照不可随报价刷新覆盖 |

Variant.specs 的类别字段：DECK={widthInch,lengthInch,wheelbaseInch}；TRUCK={sizeCode,construction,axleWidthInch,heightMm,deckMinInch,deckMaxInch}；WHEEL={diameterMm,hardnessValue,hardnessScale,widthMm,contactPatchMm,shape}；BEARING={standard}；GRIP={widthInch,lengthInch}；HARDWARE={lengthMm,headType,nutsIncluded}；RISER={heightMm}；COMPLETE={deckWidthInch,manufacturerComplete:true/false/unknown}。非关键展示字段可额外保留，但不能供程序猜测规则。

## 3. 价格与规则启用政策

- 可参与精确预算计算的报价必须同时：绑定具体规格、EXACT、CNY、CN、数量/单位明确、VERIFIED、active、已确认国内购买费用口径、有有效期且未过期。报价额可表示商品价，shipping/tax 明确数值；零费用也须有依据。`landedPriceConfirmed=false` 不宣称国内到手总价。
- 不能把型号通用价复制给每种规格；尤其 TRUCK-001 来源为 139 Mid，而规格表为 Stage11 Standard；必须分开，尚未证明映射则停用报价。
- RANGE/FROM、海外价、CN 口径未确认、包装数量不明、validUntil=null、过期促销，保留展示但排除精确推荐。null 有效期不自动赋予“30天有效”等期限。
- 非促销价由管理员核实后明确设置 review-validUntil，并保存政策依据；促销价不超过来源截止时间，优先明确来源时区。`2026-10-12 24点` 若能证实北京时间，规范为 `2026-10-13T00:00:00+08:00`；未经证实保留待核实。
- 完整组装需求为板面1、桥2、轮4、轴承8、砂纸1、螺丝/螺母8组；条件性垫片按两桥实际所需量。`packageCount=ceil(requiredQuantity/packageQuantity)`，line 商品额=packageCount*amountCents，再加按该购买项明确的运税额。不可按所耗件数分摊包装价冒充购买价。
- 必需件缺报价/数量时 totalCents=null，仅可提供已知小计 knownSubtotalCents 与 missing[]，不可称完整预算满足。
- Rule.type 白名单：DECK_TRUCK_FIT、WHEEL_TRUCK_RISER、RISER_HARDWARE_LENGTH、GRIP_COVERAGE、COMPLETENESS、USAGE_ADVICE。具体条件必须明确涉及产品/规格/结构和来源适用范围，不能将某厂家安装表当作所有桥的通用阈值。
- 规格范围尚待 T01 核对，T00 不冻结无来源的数值阈值。缺范围/规则时返回 INSUFFICIENT_DATA，不能默认为 PASS；T01 交付后由负责人发布来源范围补充记录，再启用相关规则/模板。
- deck/truck 以经核实官方区间判定；端点按原来源包含性，未知端点不假装无限范围。砂纸必须同时覆盖板面宽和长。轮/桥/垫片/螺丝缺关键来源则组装不进入 VERIFIED，仍提示咬轮会受到松紧和使用影响。
- 成品按厂家完整商品事实筛选，`validationState=MANUFACTURER_COMPLETE`，不臆造内部配件和本站兼容通过结论；未知内部尺寸展示资料不足但不以此强行排除完整成品商品。

## 4. 推荐与计算结构

需求 Demand：`{budgetCents,purpose,preference,recommendationType,shoeSize}`。
purpose=`ACTION|CRUISE|BALANCED`；preference=`EASY_START|COMFORT|VALUE|UNSURE`；recommendationType=`COMPLETE|ASSEMBLY|BOTH`；shoeSize=null 或 `{value,system:CN|EU|US|UK,note}`，不参与排序。

Selection：`{kind:COMPLETE|ASSEMBLY,templateKey,items:[{category,variantKey}]}`；同类别不得重复，complete 只能含一 COMPLETE 商品；数量由服务端计算而非信任前端。Evaluation 输入为 `{demand,selection,expectedCalculationVersion?}`。

Evaluation 输出：`{selection,demand,calculationVersion,catalogVersion,kind,templateKey,templateVersion,items,totalCents,knownSubtotalCents,budgetDeltaCents,priceComplete,validationState,checks,reasons,missing,requiresConfirmation,proposedAdjustments}`。
items 为 `{category,variantKey,productKey,label,specs,requiredQuantity,packageQuantity,packageCount,quoteKey,quoteVersion,amountCents,lineTotalCents,shippingCents,taxCents,sources}`。
checks 为 `{ruleKey,ruleVersion,state:PASS|INCOMPATIBLE|ADVICE|INSUFFICIENT_DATA,explanation,sources}`；validationState=`VERIFIED|MANUFACTURER_COMPLETE|INCOMPATIBLE|INSUFFICIENT_DATA`；预算在 total 有效时 `budgetDeltaCents=budgetCents-totalCents`，否则 null。

calculationVersion 为服务端按规范化 demand/selection、所用规格/报价/规则/模板版本的确定性摘要；客户端只回传，不自造。catalogVersion 为当前启用目录内容摘要；已保存方案的 key 是数据库生成的独立标识。

Recommend 输出：`{demand,catalogVersion,complete:Bucket,assembly:Bucket}`。Bucket=`{state:OK|BUDGET_INSUFFICIENT|DATA_INSUFFICIENT|NO_CANDIDATE|NOT_REQUESTED,plans:[Evaluation],missing,excludedCounts,minimumFeasiblePlan,shortfallCents}`。每类最多3，最低可行方案只有完整且有效时返回，不能用资料缺失小计证明预算不足；两类状态独立。

推荐先完整性/规则及报价 → 预算 → 用途 → 偏好 → 条件相同时价低及资料完整 → key 稳定打破平局。任何价格高本身不是加分。EASY_START 优先厂家整板的安装便利性/有依据的入门标签；COMFORT 仅按有来源的用途属性/模板规则排序，不自造硬度阈值；VALUE 在满足用途后按完整总价升序；UNSURE 不额外打分。

T03实施补充：偏好匹配采用rule-schema中USAGE_ADVICE可选preference字段，非null需明确来源；缺省仅贡献用途建议。文案message不供程序猜测事实；字段缺口保持数据不足或无额外偏好排序，不编造数值阈值。

差异按实质规格、用途、轮特征及安装类型确定；同型号仅换图案不算差异。每类候选规格最多200（按稳定 key 排序并告知截断），每个模板最多评估2000个完整组合；有界遍历且给出 searchTruncated，不把被截断库的最低价称全库最低。参数单位范围校验用于防溢出，业务适配阈值必须有来源。

替换只更改用户选定项并重算全套；若需联动修改则返回 proposedAdjustments 和 requiresConfirmation=true，不自动替换。用户确认后提交整个新 Selection，再 evaluate，报价变化也需再次确认。不能以“确认”跳过真实兼容检查。

## 5. API v1（沿用 ApiResponse）

成功为 `{code:200,message:"success",data:...}`。业务状态包括无结果和报价冲突，放入 data，不靠解析中文 message 分支；沿用 HTTP401/403 的认证权限行为。新模块业务冲突仍返回200信封，data 内 `errorCode` 及相关 `evaluation`，确保现有 http.js 不丢掉结构化信息。参数错误 HTTP400，未找到资源 HTTP404；未授权用户访问他人方案统一404，避免枚举。

| 方法/路径 | 输入/输出 | 权限及所属任务 |
| --- | --- | --- |
| GET /api/skate/board-assistant/catalog | category?, page>=1,size<=50 → PageResult<Product+variants+displayQuotes>，按现有分页结构；不暴露后台敏感信息 | 匿名，T02 |
| GET /api/skate/board-assistant/variants/{key} | Variant+Product+displayQuotes+eligibility+sources | 匿名，T02 |
| POST /api/skate/board-assistant/recommend | Demand → Recommend | 匿名，T03 |
| POST /api/skate/board-assistant/evaluate | Evaluation输入 → Evaluation | 匿名，T03 |
| POST /api/skate/board-assistant/replacements | `{demand,selection,category,page,size}` →候选分页及排除原因；每次 evaluate 重算 | 匿名，T03 |
| POST /api/skate/board-assistant/compare | `{plans:[{demand,selection}]}` 1～3 → 当前重新计算的 evaluations、对比字段及资料缺口 | 匿名，T03 |
| GET /api/user/board-plans | page,size → 我的快照摘要 PageResult | 登录，T05B |
| POST /api/user/board-plans | `{name,note,demand,selection,expectedCalculationVersion}` → Snapshot 或冲突 | 登录，T05B |
| GET /api/user/board-plans/{key} | 自己 Snapshot；最新状态另附 latestEvaluation，不覆盖快照 | 登录且本人，T05B |
| PATCH /api/user/board-plans/{key} | `{name,note}` → 只改名称备注 | 登录且本人，T05B |
| POST /api/user/board-plans/{key}/copy | `{name?,note?}` → 独立历史快照复制，新key/时间，保留原价格并注明复制来源；不是按最新价保存 | 登录且本人，T05B |
| DELETE /api/user/board-plans/{key} | 删除本人快照 → success | 登录且本人，T05B |
| GET /api/admin/board-assistant/{resource} | resource=products/variants/quotes/rules/templates，page,size →分页 | ADMIN，T06B |
| POST /api/admin/board-assistant/{resource} | 类型对应字段 →保存草稿，不能传 ownerId/客户端version | ADMIN，T06B |
| PATCH /api/admin/board-assistant/{resource}/{key} | 类型对应字段+expectedVersion →新版本；报价/规则旧版本保留来源快照 | ADMIN，T06B |
| POST /api/admin/board-assistant/{resource}/{key}/activation | `{active,expectedVersion}` →校验后启用/停用；活动报价须满足有效期等条件 | ADMIN，T06B |
| POST /api/admin/board-assistant/trial | Demand → Recommend + 筛除计数/原因 | ADMIN，T06B |
| POST /api/admin/board-assistant/import/validate | 规范v1内容 →只读校验报告，不写库 | ADMIN，T02 |
| POST /api/admin/board-assistant/import | `{dataset,expectedCatalogVersion}` →事务导入草稿/明确已核实启用项，返回计数和缺口 | ADMIN，T02；仅获准的隔离开发环境 |

产品/规格/规则/模板、保存字段的 key、长度和数量均需后端校验；page size 限50，name≤80，note≤1000；导入内容上限1MiB/1000产品/5000规格，超限拒绝。管理员操作沿用已有审计日志机制，不允许任意URL服务器抓取或执行规则脚本。

errorCode 白名单：`PRICE_CHANGED|CATALOG_CHANGED|SELECTION_INVALID|INCOMPATIBLE|DATA_INSUFFICIENT|ADJUSTMENT_REQUIRED|VERSION_CONFLICT`。过期报价引起保存失效返回新 evaluation/缺口，不能直接保存；PRICE_CHANGED/CATALOG_CHANGED 要求展示新版本并再次点击确认。未登录不创建方案，禁止使用客户端 ownerId。客户端伪造总价/检查状态字段拒绝或忽略后服务端完整重算。

POST save 必须事务内一致读取版本，写入前检查 expectedCalculationVersion；数据变化返回冲突且不落库。允许保存资料不足的个人草稿，但 snapshot 状态必须清楚为 INSUFFICIENT_DATA、total=null且已有报价版本一致；不能把不兼容/缺数据方案标成已验证。copy 如实复制历史，不重新宣称有效报价。

匿名POST只精确放行 recommend/evaluate/replacements/compare，不用 broad permitAll 放开 user/admin 路径；认证沿用现有JWT。无付费调用。

## 6. 持久化与导入

T02 单一后端写入者新增 Flyway `V9__board_assistant_catalog.sql`（派发时复核最高版本，若冲突先报告），只建新表：`tb_board_product,tb_board_variant,tb_board_quote,tb_board_rule,tb_board_template`。T05B 新增 `V10__board_plan_snapshot.sql` 与 `tb_board_plan_snapshot`。不得编辑已存在 V1～V8 或现有用户数据。

T02实施补充：允许同一V9增加最小 `tb_board_catalog_meta` 元信息表，用于跨重启持久化datasetVersion/内容指纹/catalogVersion，并串行锁定导入的共同目录状态，防止并发幂等检查被绕过。不改已有数据表；该变更是满足原事务/版本契约的常规技术选择。

表需 Long 主键、唯一稳定key、版本、active及时间；variant 外键product，quote 外键variant；金额用BIGINT，关键筛选字段/类别/有效期/归属独立列，来源、规格、规则条件、模板选择条件及快照用 TEXT JSON + Jackson校验。无需引入数据库专属JSON查询。快照 ownerId 与现有真实用户主键关联并建 owner/time 索引。

报价按 `(key,version)` 唯一保存不可变历史，修改创建新version并停用旧版；API普通读取选当前版，计算/快照同时携带key和version。规则/模板可更新当前行及version，但快照必须保存当时全部内容和来源；不得只存外键再读实时内容。

导入以 key 幂等：同版本同内容 no-op，同版本不同内容拒绝，旧版本拒绝；新版本更新草稿/报价历史版本，引用完整性先校验，整个导入事务失败则无部分写入。原始资料允许以草稿及缺口导入，不能将未核实项变为启用推荐项。导入报告 `{created,updated,unchanged,rejected,errors,gaps}`；硬错误拒绝全部，缺口可存草稿但不得作为通过证据。

旧器材展示不自动大批量导入/重写；legacyEquipmentId 只有真实已匹配的旧记录才关联。必要展示映射由负责人审查后单独派发。没有本地隔离数据库时保留校验与测试证据，不转用线上CDB试迁移。

## 7. 页面与文件归属

- 一级入口 `/board-assistant`；需求/结果/详情/替换/最多3套对比可在该页面按明确状态组织，无需为每状态重复页面。
- `/profile/board-plans` 登录用户“我的方案”，个人中心增加入口；方案保存前匿名 draft 仅存在 sessionStorage，登录后返回同页面、恢复 demand/selection，再服务端重算后明确确认保存。
- 仅允许站内白名单 returnTo，禁止开放重定向；401恢复上下文，403显示权限问题。若改共享http.js必须回归旧流程并保留结构化错误信息；新API优先独立 adapter。
- 前端统一拥有 App.vue/router/index.js/个人中心/LoginPage.vue/旧器材浏览页面及admin页面的入口合并；先核对这些文件既有diff。后台新增独立 BoardAssistantPanel 复用AdminPage框架，不重构其他后台模块。
- 后端统一拥有新board模型/DAO/service/controller/模块测试/SecurityConfig精确权限项/新迁移；既有接口默认只读。禁止无关重构AdminService或pom依赖。
- coordinator 只维护契约、任务登记、自己进度与验收材料；Server Test 维护自身审查记录和测试证据，业务代码只读；任何修复回派责任角色。

## 8. 测试边界与契约变更

覆盖任务书11场景：包装数量与整数分、预算与资料不足区分、用途偏好事实排序、连续替换、联动确认、失效报价、每类独立状态和跨类对比上限、本人权限、防伪造及竞态保存、历史价格和复制删除、登录续接、旧模块回归。

人工构造的完整有效组合用于业务单元测试时必须标明 synthetic fixture；不能把它导入真实规范器材库或称已验证推荐样本。实际数据不能支撑三套时报告差额和来源缺口。构建/单测、mock UI、真实后端数据库联调、服务器/线上业务验收分别登记。

接口结构 v1 已冻结；实现者发现契约问题先在角色checkpoint报告，由负责人串行修订并记录受影响任务。不要求用户批准常规技术选择。物理阈值、引用报价、模板实际条件在T01来源审查后补充，不允许实现者私设。

## B01审查后实施补充：请求级搜索预算（2026-10-10）

- 推荐整次请求共享最多2000次候选评估预算；模板遍历顺序稳定，每类规格200和结果最多3的门槛保留。不得因模板增加重新分配无限个独立2000预算。
- 超出预算必须明确searchTruncated，并让受影响结果桶可识别截断。最低可行价格只能表示本次实际搜索范围，不保证全目录最低。
- 这是有界搜索实施约束，未证明真实并发吞吐、专用限流或服务器负载能力；这些仍为公开发布前验证事项。
