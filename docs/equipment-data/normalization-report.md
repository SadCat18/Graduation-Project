# T01 装备规范化交付报告

日期：2026-10-09。角色 backend，TASK_ID=equipment-recommendation，当前仅交付 T01，待总负责及 Server Test 审查。工作目录 `<repository-root>`。

## 结论及证据层级

已完成纯文件规范化和校验器，保留原始50记录及全部原始规格/价格/链接。结构校验通过不表示商品字段、报价或兼容性已核实。真实可推荐完整组装 **0套**，真实有效精确报价成品 **0套**，各距三套目标仍缺3套；本轮没有生成或称验收通过的真实方案。

来源仅为 `equipment.json` 与 `sources.md` 中已有采集资料，以及冻结任务书/契约中的项目要求。本轮未联网复核厂商页面、电商SKU、库存、授权书或费用，未登录、操作账号、下载图片或执行SQL。全部规格、报价、规则、模板及通用来源使用 **RECORDED**；没有 VERIFIED 对象或来源。图片使用状态为 UNAUTHORIZED。旧库真实ID未知，`legacyEquipmentId=null`。

## 输出与统计

| 对象 | 数量 | 启用/有效 | 状态说明 |
|---|---:|---:|---|
| 产品 | 50 | 启用0 | 原record_id稳定key，全覆盖 |
| 规格 | 114 | 启用0/VERIFIED 0 | 转写明确尺寸条目或未解析购买规格草稿 |
| 报价 | 60 | 启用0/有效精确国内0 | 国内26、海外34；不复制到各规格 |
| 规则 | 39 | 启用0/VERIFIED 0 | 38条桥尺寸范围草稿+1条项目完整性要求 |
| 模板 | 3 | 启用0/VERIFIED 0 | ACTION_BEGINNER/CRUISE_BEGINNER/BALANCED_BEGINNER |
| 原记录映射 | 50 | 全覆盖 | 原路径、记录摘要、拆分依据、产品/规格/报价/规则key |
| 缺口条目 | 299 | 逐条保留 | JSON内gaps，包含重复影响不同对象的同类缺口 |

原始分类：DECK 11、TRUCK 7、WHEEL 10、BEARING 8、GRIP 4、HARDWARE 3、RISER 3、COMPLETE 4。规格分类：DECK 21、TRUCK 38、WHEEL 31、BEARING 8、GRIP 4、HARDWARE 3、RISER 5、COMPLETE 4。

299缺口构成：规格未核实114、未知规格字段55、报价不满足门槛60、无国内价24、桥规则范围未核实38、Mid/Standard错配1、模板条件缺失3、其余四类物理/用途规则来源待补各1。17条国内价有候选规格绑定，仍没有一条达到有效报价门槛。

报价 priceKind：EXACT 56（国内22、海外34）、RANGE 1、FROM 3。“EXACT”仅说明原amount是单一数值，并不满足启用门槛。所有人民币报价都缺已独立核实的运税/有效期等证据，`landedPriceConfirmed=false`。唯一从报价文字转写包装数量的是 Jessup 单张 `packageQuantity=1, packageUnit=SHEET`，仍为 RECORDED；不把厂商spec.quantity_per_set自动当作对应零售报价包装。

## 拆分与保留依据

- 原record_id作为Product.key，尺寸/结构/原SKU信息构成Variant.key；数组位置只用于sourcePath，不作为稳定key。每条mapping保留`equipment.json#/records/N`及原记录canonical SHA256，原记录全字段可由指针回溯。
- 板面按available_sizes逐项转写。Real两款8.5宽板面用长度/轴距区分，未把同宽误合并。DBH板面没有尺寸，只留未知购买规格；不从整板反推板面SKU。
- 桥按原size_code转写；Thunder/Venture混合底座/高度未拆成假购买组合，construction、heightMm保留null及原文。Ace只保留已列AF1 53mm线，不构造Low/Hollow规格。TRUCK-006通用7.9–8.25范围未证明分别适用8.0和8.25规格，规范deckMin/deckMax设null，原值仍在rawSpec。
- **TRUCK-001 ¥430为139 Mid金色实心，原规格Stage11 Standard。variantKey=null并单列MODEL_MISMATCH；不创建没有独立参数依据的Mid SKU，不绑定Standard 139，也不复制报价。**
- 轮子按原尺寸/硬度/明确series或SKU条目转写；A/B硬度分字段，不执行84B=104A换算。无法判定轮型时shape=null，而非从产品名混合描述猜测。
- Shorty's螺丝的3长度×2头型无逐组合证据，只保留未解析购买规格，不构造6个SKU。Independent两个单长度/单头型条目分别转写，所需长度规则仍不启用。
- 垫片原英寸值使用精确 `1 inch=25.4 mm`：1/8=3.175mm、1/4=6.35mm、1/2=12.7mm；保留原始“3.2/6.4/12.7mm”文本。硬件1/1.25英寸转25.4/31.75mm；测试覆盖分数解析，避免把1/8误当1英寸。
- 每条原国内/海外价格只产生一条Quote，稳定key=`record_id--cn-price/overseas-price`，版本1。单规格记录可作RECORDED候选绑定，Element Seal国内价只候选绑定原说明的8.0规格；多规格记录价格不复制、不按低价推广，variantKey可为null，均禁用。
- 数值价格通过`Decimal(str(amount))*100`转整数分，100.1→10010；区间和“起”保持displayPrice且amountCents=null。未换算外币、未把零费用作为默认值。
- 采集日保留collectedDate；采集时刻/时区未知则collectedAt=null。促销“2026-10-12 24点”保留rawPrice；时区未证实则validUntil=null，未自设30天有效期。
- 原材料/结构/安装文字在rawSpec、Product.notes及原记录保留；不会因拆字段丢失，也不供程序解析成默认兼容结论。

## 规则/模板的来源范围及待冻结结构

38条DECK_TRUCK_FIT草稿每条限定productKeys/variantKeys/construction；条件转写deckMinInch/deckMaxInch，minInclusive/maxInclusive未知为null。来源evidenceRef对应原size_variants条目，只证明转写路径，没有本轮独立字段证据。未知范围端点不是无限范围。

COMPLETENESS草稿来自契约§3，requiredQuantities={DECK:1,TRUCK:2,WHEEL:4,BEARING:8,GRIP:1,HARDWARE:8}，hardwareUnit=BOLT_NUT_PAIR，riserRequirement=SOURCE_DEPENDENT。它是项目需求数量，不是厂家物理兼容证据，等待总负责来源补充审查后决定启用。

三模板requiredCategories按任务要求记录，selectionConditions={kind:ASSEMBLY,eligibleVariantKeys:[],ruleKeys:[],sourceScopePending:true}；reasonCodes为空。没有编造用途硬度/尺寸阈值、默认商品组合或最佳匹配分数。

校验器对上述两种Rule.conditions及Template.selectionConditions采用这次交付的有限结构白名单；**这些字段细化是T01交付建议，未修改冻结契约，需总负责审查后形成来源/结构附录**。其他白名单规则类型 WHEEL_TRUCK_RISER、RISER_HARDWARE_LENGTH、GRIP_COVERAGE、USAGE_ADVICE 的活动执行结构待附录，不能直接启用。脚本/eval/表达式条件拒绝。

## 真实三套样本缺口与依赖影响

- 国内螺丝/螺母及垫片参考报价均缺失；需具体SKU、购买包装和价格证据。
- 桥缺对应零售包装单只/一对及结构映射；139 Mid/Standard错配；其他多规格通用价无法确定逐SKU。
- 所有国内价格尚缺完整运税口径、可复核有效期、采集时刻证据；多个成品标题或说明虽记作整板，厂家完整商品事实仍待独立核实，manufacturerComplete=null，不虚构内部配件。
- 桥端点包含性、轮/桥/垫片/螺丝安装适用范围、砂纸与板面覆盖来源、用途模板条件未形成可执行核实规则；不把原安装建议直接推广所有品牌。
- 当前缺口属于DATA_INSUFFICIENT，不能用已知部件小计证明预算不足；不凑三套，不提供假总价。

以上不妨碍负责人在T01审查闭环后独立派发T02结构化存储与草稿读取；T03真实推荐与最终三套业务验收仍依赖来源/规则/报价补全。T01没有实施Java API、数据库导入、迁移或推荐计算；测试中的一个虚构成品仅用于校验正对照，未写入真实文件或称为实际样本。

## 可复核命令与实际结果

环境Python 3.11.9。仓库根目录执行，固定时间仅用于重复过期判断，不代表实际采集时刻：

```powershell
python -X utf8 docs/equipment-data/validate_normalized.py --as-of 2026-10-09T22:00:00+08:00
python -X utf8 -B docs/equipment-data/test_validate_normalized.py
python -X utf8 docs/equipment-data/validate_normalized.py --previous docs/equipment-data/normalized-equipment.v1.json --as-of 2026-10-09T22:00:00+08:00
```

- 第一条退出0：valid=true，errors=[]，gaps=299，eligibleExactCnQuotes=0，全部active计数0。
- 第二条退出0：5组正对照、44个负例正确拒绝；inputHashesUnchanged=true，syntheticOnly=true。覆盖重复key/悬空引用/非法分金额/区间与起价误启用/包装或有效期缺失/过期或无时区/费用或来源缺失/同版本不同内容/旧版本/原始价格或映射篡改/139 Mid错配/脚本规则/模板来源缺失；没有使用业务后端或真实数据库。
- 第三条退出0：相同版本相同内容有效。冲突版本测试在内存进行，不持久化篡改样例。
- `python -X utf8 docs/equipment-data/validate_normalized.py --as-of invalid` 实测退出1，valid=false/INPUT_ERROR，证明CLI错误出口；未修改输入文件。
- 六个交付文件逐行尾空白检查通过；两个Python文件AST检查通过，没有__pycache__目录。
- 两个Python文件AST解析通过，不生成pycache。校验器默认纯读取，可用--details输出全部缺口；JSON加载拒绝重复字段、非有限数、超1MiB输入，导入计数限制1000产品/5000规格。
- 初轮校验发现分数英寸解析错误引发重复垫片key；修正分数解析优先级后重生成，仅规范输出改变，追加精确换算测试并通过。Windows默认GBK输出曾遇¥编码错误，所有正式命令使用-X utf8。未把失败结果删作通过历史。

校验器只能检查声明的证据字段结构和引用，不联网证明证据真实性；VERIFIED状态的真实性仍须负责人/审核者复核。原记录或来源日志本身不能单独充当VERIFIED evidenceRef。业务规则计算、整套计价、幂等数据库事务、真实联调和线上验收属于后续任务，未验证。

## 原始文件与规范文件指纹

执行前后原始文件SHA256完全相同：

| 文件 | SHA256 |
|---|---|
| equipment.json | 4D9A61CB207D5CDD416CC86BA2AA1F6AAEB36EFA50556680304E08BDCD530016 |
| equipment.csv | 224FDDBED14CD5A172E4CFD7D35AE5885ED878EF49C5E43711789A004C4CA6BE |
| db-seed-skate-equipment.sql | 0135E6E39C4D3755E08B8D5FF3348B4E6AE6F59F7F6FAC193C6065101D67C070 |
| sources.md | 1B1F1133F0FD3F1ECD3B76A6BC4141C3D3FF65B6A1E07429DD7086A13F873C16 |
| normalized-equipment.v1.json | B2C9D3B9166DEBC3CBCE633257F574FD0E3FE8FA6F0110E557C70EFD17BAD064 |

规范JSON为605393字节，低于1MiB上限。可用`Get-FileHash -Algorithm SHA256`独立复核，实际值以当前文件为准。

## 精确改动文件与唯一下一步

1. docs/equipment-data/normalized-equipment.v1.json（新增规范库）。
2. docs/equipment-data/validate_normalized.py（新增只读校验器）。
3. docs/equipment-data/test_validate_normalized.py（新增内存正/负样例）。
4. docs/equipment-data/normalization-report.md（新增本报告）。
5. docs/equipment-data/README.md（修正统计和价格可用性，保留原来源历史并区分RECORDED）。
6. .codex/progress/backend-equipment-recommendation.md（本角色独立进度）。

未改动仓库根README、Java、Vue、迁移、部署配置、原JSON/CSV/SQL/日志、coordinator或其他角色进度；没有提交/推送/部署/重启/账户操作。唯一下一步：等待总负责核对并串行派发T01 REVIEW，收到具体返工项或下一任务前保持待命。

## T01-FIX-01 限定返工（2026-10-09）

独立T01-REVIEW发现报价来源归属门禁缺陷：交换DECK-001/DECK-002的mapping.quoteKeys、篡改未绑定报价DECK-003--cn-price的sourceProductKey，原校验均未拒绝。实际规范数据无此错配。本次只修复校验与测试，不重生成规范JSON，不修改datasetVersion/各对象version，不修订规则或扩展到T02。

修复门禁：

- mapping.quoteKeys每个报价的sourceProductKey必须等于映射productKey；带variant的报价同时校验该规格所属产品，否则MAPPING_QUOTE_MISMATCH。
- 提供original时，每个mapping.quoteKeys须完整匹配该原记录的国内/海外价格key集合，否则MAPPING_QUOTE_COVERAGE。
- 每条原报价的sourceProductKey及已绑定规格的产品sourceRecordId须匹配原记录身份，否则SOURCE_QUOTE_MISMATCH；未绑定展示报价不能借variantKey=null逃过来源核对。

新增6个内存负例：跨产品交换报价映射；报价映射偏离原记录；未绑定展示报价错归产品；未绑定展示报价原记录身份错配；已绑定报价换为跨产品规格；展示报价删除来源产品身份。分别覆盖不传original和传original的门禁，不将篡改样例写到真实规范库。

在本报告前述相同命令重新验证：默认固定时间校验退出0，valid=true/errors=[]/gaps=299；测试退出0，**positiveControls=5/rejectedNegativeCases=50/inputHashesUnchanged=true/syntheticOnly=true**；--previous同版本同内容校验退出0。上节44个负例是首次交付历史结果，本次新增6个后当前总数为50。原始JSON/CSV/SQL/来源日志和规范JSON指纹保持与上表一致。

本次精确修改4个文件：validate_normalized.py、test_validate_normalized.py、normalization-report.md、.codex/progress/backend-equipment-recommendation.md。README、实际数据及其他角色文件未改。后续影响：关闭已复现的跨产品报价归属漏检；不改变真实有效报价0、完整方案0和299项数据缺口。唯一下一步：等待总负责串行派发T01-FIX-01限定复审，停止业务工作，不进入T02。
