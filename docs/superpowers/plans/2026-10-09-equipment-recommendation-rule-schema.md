# 选板助手 v1 有限规则条件结构补充

负责人：coordinator。日期：2026-10-09。依赖冻结契约v1。仅冻结执行数据的结构，不认可现有任何报价或物理阈值已经核实。

## 共同边界

`scope={productKeys:[],variantKeys:[],construction:null|string}`。产品/规格引用必须真实存在；约束所有命中的指定桥结构或组合，不能以一个空scope把品牌规则推广整个库。规则版本及来源均沿用契约。原T01全部规则/模板为禁用草稿；新增结构由责任后端在后续任务维护，任何激活需完整类型条件及来源校验。

2026-10-10范围匹配细化：通过实际目录索引解析scope引用类别；同类别候选是OR，不同被限定类别之间是AND。规则指定桥A与螺丝B时，选择桥A与螺丝C不满足scope，不能应用该安装结论。来源未覆盖当前选择时返回缺适用规则/INSUFFICIENT_DATA。construction若已限定则必须匹配对应实际结构。

conditions只能使用下列固定字段，禁止额外表达式/脚本。所有物理数值从有效来源取得；null代表未知，不可作为0或无穷范围。数值使用Decimal/BigDecimal，端点包含性必须明确。正数尺寸、范围下限≤上限；已证实的无垫片高度可为0。

| type | conditions | 判定及限制 |
| --- | --- | --- |
| DECK_TRUCK_FIT | deckMinInch,deckMaxInch,minInclusive,maxInclusive | T01已有结构；明确匹配TRUCK具体规格/结构，用板面widthInch对官方区间检查 |
| WHEEL_TRUCK_RISER | wheelMinMm,wheelMaxMm,minInclusive,maxInclusive,riserMode(REQUIRED/OPTIONAL/NONE),riserMinMm(nullable) | scope须指定适用桥规格/结构；轮直径命中区间后按来源要求判定垫片，REQUIRED时高度不能null；NONE仅限来源明确无需，不是风险不存在保证 |
| RISER_HARDWARE_LENGTH | riserMinMm,riserMaxMm,minInclusive,maxInclusive,hardwareMinMm,hardwareMaxMm | scope须限定适用桥/螺丝安装结构，来源确定垫片高度区间与螺丝长度区间；无垫片height=0是明确选择，不是缺字段推测 |
| GRIP_COVERAGE | compareWidth:true,compareLength:true | 以当前实际规格分别检查砂纸宽/长≥板面宽/长；来源包括规格证据和本项目覆盖要求，无需编造冗余余量阈值 |
| COMPLETENESS | requiredQuantities:{DECK:1,TRUCK:2,WHEEL:4,BEARING:8,GRIP:1,HARDWARE:8},hardwareUnit:BOLT_NUT_PAIR,riserRequirement:SOURCE_DEPENDENT | 项目明确需求；条件性垫片按已匹配规则决定，不用假厂家来源包装项目数量 |
| USAGE_ADVICE | purpose(ACTION/CRUISE/BALANCED),preference(nullable/可省略，EASY_START/COMFORT/VALUE/UNSURE),variantKeys:[],message | 由来源明确的规格/用途/偏好事实形成软性建议；不把价格/硬度或未核实标签猜成适配，message仅展示 |

outcome为 `{state:PASS|INCOMPATIBLE|ADVICE|INSUFFICIENT_DATA,message}` 或旧T01已转写草稿内容；执行逻辑按type和已知属性计算真实state，不信任存储outcome的PASS声明。任何必需spec缺失或无适用规则均产生INSUFFICIENT_DATA。规则已声明硬件和轮区间但当前输入不落任何范围，不自动证明不兼容：没有覆盖依据时是资料不足；明确禁止范围才判不兼容。

ASSEMBLY已验证必须完整计价及COMPLETENESS、DECK_TRUCK_FIT、WHEEL_TRUCK_RISER、RISER_HARDWARE_LENGTH、GRIP_COVERAGE全部获得有范围的有效检查。ADVICE不代替硬检查。多个匹配规则若相互矛盾，返回INSUFFICIENT_DATA和RULE_CONFLICT原因，不选择较宽阈值。厂商成品不伪造这些内部PASS。

## 模板有限结构

沿用T01：`selectionConditions={kind:ASSEMBLY,eligibleVariantKeys:[],ruleKeys:[],sourceScopePending:boolean}`；kind只能ASSEMBLY，所有引用存在且来源可核验。eligibleVariantKeys按类别分组，必需类别都须有候选。sourceScopePending=true或关键来源不足不能启用。requiredCategories为DECK/TRUCK/WHEEL/BEARING/GRIP/HARDWARE，RISER由已匹配规则决定。

purpose为ACTION_BEGINNER/CRUISE_BEGINNER/BALANCED_BEGINNER，对应用户purpose ACTION/CRUISE/BALANCED。符合用途的有效模板优先，其他有效模板只有用途来源明确允许时才能作为带理由的替代；不能偷偷换用途。未指定偏好不额外加分，COMFORT等仅按实际资料排序。reasonCodes引用规则/事实，不向用户输出内部模型细节或无依据百分比。

## 激活与校验执行职责

2026-10-10 T03实施补充：USAGE_ADVICE新增可选preference是为冻结可执行偏好事实，兼容已有缺省字段的用途规则。非null时必须白名单且有来源支持；缺省规则只能贡献用途建议，不能猜测偏好。排序可按匹配的用途/偏好规则计数，稳定价低/key作为后续条件；message文本不得供程序解析或反推阈值。不修改现有真实草稿来制造偏好依据。

- T02导入储存草稿并严格验证基础字段；T03补齐有限执行结构、代表性负例和完整状态计算。
- T06后台保存草稿允许缺失，但启用必须运行相同校验器，不能绕过价格/来源/模板门槛。
- 对现有 RECORDED 数据暂不启用，只冻结模板/规则结构供开发和合成测试使用。
- 推荐/保存/后台试算复用同一个计算与版本核对路径，禁止多套规则各自结论。
- 若T01审查指出已有结构问题，负责人只更改受影响结构并记录，不允许开发者静默改接口。
