# D01 离线资料治理与仍缺项

本批没有新增采购采集、价格换算、物理阈值或生产目录导入。equipment.json、原SQL/CSV/sources、normalized-equipment.v1.json、normalized-equipment.reference.v1.json保持原字节。

## 对HW-002/003审查建议的更正

实际基线正式库已经含HW-002的25.4mm、Phillips十字、nutsIncluded=true，以及HW-003的31.75mm/相同头型/带母；原文均8钉8母、单一长度。不能把这些说成本轮新补齐。recorded-spec-supplement.candidate.json逐8条事实保存实体键/字段/原文/来源URL/采集日期/适用规格/RECORDED及依据，明确proposedCatalogChanges=[]、autoImport=false、noCatalogMutationRequired=true。

这是**仅供审查的补充事实候选，不是导入数据集**；现库无必需字段变更，不重复行、不增版本、不自动替代正式目录。HW-001仍含多个长度/头型，不能替用户选择。8钉8母的记录不证明国内报价或跨型号安装兼容。

## 全299历史缺口分区

gap-source-ledger.json逐条保留原gap key/实体/code/fields/note，并关联现有sources（URL/日期/evidenceRef/status）、适用规格、当前已知/未知字段、分区理由与下一步依据。

| 分区 | 数量 | 含义 |
| --- | ---: | --- |
| REFERENCE_BLOCKING | 120 | 国内价缺失、明确错型、关键未知规格/适用规则/模板条件，仍可能阻止相应组装完整性。不是说每条缺口都影响所有候选。 |
| EXACT_PURCHASE_ONLY | 60 | 历史QUOTE_NOT_ELIGIBLE中的核验/包装/运税/有效期等精确采购门槛，不因参考模式免核而伪造完成。 |
| RECORDED_PENDING | 119 | 已记录但未独立核验的事实/用途及整板可选内部规格，或已转写但历史gap尚在；保留真实来源和未知。 |

未删除或清零任何原gap。正式库仍50产品/114规格/60报价/39规则/3模板；21国内参考价、0独立核验精确价保持。原299缺口是历史记录，分区是治理说明，不改变计算/启用门槛。

## 仍无真实完整组装

- 螺丝HW-001/002/003与垫片等无国内参考价，不把USD2.95换算、不填0。
- WHEEL_TRUCK_RISER、RISER_HARDWARE_LENGTH、GRIP_COVERAGE没有适用来源规则；已有DECK_TRUCK_FIT范围为RECORDED，未知端点/结构仍待确认。
- TRUCK-001的139 Mid价格不能套Standard；模板sourceScopePending不伪清除。
- 当前600元组装候选已知小计786.20元，完整total/delta仍null。资料治理已形成可追溯清单，不等于新增完整方案；前端展示完整/待补分区另按冻结契约实施。

## 可复现命令

```powershell
python -X utf8 -B docs/equipment-data/build_public_supplement.py
python -X utf8 -B docs/equipment-data/test_public_supplement.py
```

纯标准库、离线读取原资料；只生成本目录两个新增审查产物，不读生产配置、不访问网页、不写数据库。测试保护原六份资料hash、8条事实与299条唯一gap覆盖/分区。后续有新价格/安装来源，应单独审查并递增正式候选版本，不能用本文件授权导入。
