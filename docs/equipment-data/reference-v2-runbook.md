# 参考目录 v2：生成、核对和导入方案

这是隔离候选及后续运行说明，**没有生产导入**。价格仍为用户接受的参考估价，不恢复实时库存、税运费、采购核价或30天TTL。

## 来源和身份

原equipment.json、normalized-equipment.v1.json、normalized-equipment.reference.v1.json及mapping.sourceRecordId/sourceRecordSha256不覆盖。新v2是v1的窄小overlay；官方核验沿用2026-10-23审计快照（URL/独立checkedAt/响应sha/适用范围），不是后端本轮新请求，见reference-v2-field-ledger.json。

- DBH PRO原聚合键保留active=false/historicalOnly=true/replacements四项，不自动选择图案。新四图案身份仅来自原采集文字，manufacturerSku及板宽未知；官网403不升级独立核验。580元明确为产品族通用参考，不冒称每图案独立核价。
- Allen三个官方列明长度分别1/1.125/1.25英寸（25.4/28.575/31.75mm）；每包8螺栓/8低头螺母是包装/螺母属性。Phillips独立页TLS失败，仅保留头型身份、长度未知，不构造Phillips×长度笛卡尔积。旧未解析键只读，替代需显式选择。
- Dooks三厚度3.175/6.35/12.7mm保留，包内片数和国内价未知，5 Set Box不等于每Set5片。
- Krux8.0只补size-guide明确轴宽8.0和厂家建议7.75–8.25（ADVICE、hardBoundary=false）；规格/硬规则deckMin/Max仍未知。55mm只在产品fieldEvidence登记网页默认7.60作用范围；8.0高度/331g不转用。海外每只销售包装不覆盖国内322元覆盖一对的通用参考假设。
- DBH轮52/100A保留RECORDED，轮宽/接地面未知。逐字段VERIFIED不把整行、报价或兼容结论升级。

## 离线生成

在候选项目目录运行 `python docs/equipment-data/build_reference_v2.py`，只读原v1及已保存ledger，生成normalized-equipment.reference.v2.json和reference-v2-import-diff.json；不访问网络/数据库。`python docs/equipment-data/test_reference_v2.py`核对可重现性、字段作用域与原资料hash不变。Java真实validator/H2导入另见后端测试证据，Python检查不替代它。

本次变化：50产品不增删，新增8变体（4图案、3 Allen长度、1 Phillips长度未定），总122；更新1产品、7旧变体、4报价、3模板，0规则变更，0删除。新行version1，只有内容变化的旧行升一版，未变行保留版本。导入diff明确列键；datasetVersion=2026-10-24-reference-02-v2。

## 日期和历史

两个促销保留amount/rawPrice/来源，新增promotion={endDate,precision:DATE,timezone:null,sourceText,evidenceRef}。validUntil仍null，不能编造截止时区。服务端兼容既有v1原价类型/来源标题中明确“活动至YYYY-MM-DD”事实；只有明确日期才判，不按采集年龄淘汰普通参考。

未知时区采用全球UTC+14至UTC-12的结束可能窗口作比较边界，不把它写成来源时区；结束日在所有可能时区均过去才EXPIRED，边界内BOUNDARY_UNCERTAIN不作为当前可用价，边界前CURRENT；无到期UNBOUNDED。确切validUntil沿用精确时间语义。展示历史报价必须看referencePriceEligible/referenceTemporalStatus/historicalOnly，不把历史金额当当前价。

同一目录版本不变、仅Clock跨结束也会改变当前计算摘要，旧expectedCalculationVersion保存会冲突，最新评估按当前资格重算。**明确historicalCopy的“复制历史方案”保持原快照字节和旧价，继续标明非当前；它不是新计算。**从历史方案发起普通新保存必须使用最新评估和确认摘要。BoardPlanService保持基线，没有新增copy确认协议。

## 后续隔离/授权环境导入流程（本批仅H2测试执行）

1. 核对目标环境明确授权、当前catalogVersion、数据备份/产品和变体/报价计数；记录候选JSON及ledger/diff sha。生产不执行。
2. 使用既有管理员 `/api/admin/board-assistant/import/validate` 传v2文档；确认valid/errors/gaps、导入diff和历史身份策略。
3. 经环境授权后，使用既有import端点传 `{expectedCatalogVersion,dataset:<v2>}`。版本冲突先重新验证，不能覆盖较新目录或放宽EXACT门槛。import-enabled保持原配置，不为本批改生产开关。
4. 校验未变化行unchanged，原聚合键仍在但只读，新身份明确；相同dataset重导幂等，原quote旧版本与不可变方案快照仍可读。
5. 以当前Clock验证到期促销不参与当前计算、排序、eligible/display；固定原选择剔除两条后67100分/60000至少超7100、完整总价null，重新推荐可选不同组合。历史有效78620夹具只在明确有效历史时点成立。
6. 由独立Server Test验证本地MySQL/HTTP/JWT、权限、本人方案/历史、版本冲突与浏览器。H2、Mock或源码不代替这些层。

## 回退边界

本轮没有schema迁移，无旧记录删除。目录行按不可变报价版本/快照保存；需要回退时在另行批准环境用新的更高版本向前修正，不覆盖旧dataset fingerprint或强改旧快照。不要把reference.v1原文件改写成“新v1”，也不要把旧聚合键静默别名到某一新图案。代码回退与目录回退须分别核对当前资格和版本摘要，过期促销不能借回退重现为当前价。
