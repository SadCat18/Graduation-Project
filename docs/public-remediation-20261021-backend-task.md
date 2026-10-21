# PUBLIC-REMEDIATION-20261021-B 实施任务与复验入口

本后端阶段已按总负责正式串行派发执行，依据同目录public-remediation-20261021-contract.md（冻结原文，末尾补充优先）。基线5afdd33e93e3466b4799ce48a3f9eb73248fbf9b，branch codex/public-remediation-20261021；只交候选，不提交/推送/合并/部署。

## 责任与最小实现

1. F03：TrainingPlanService.detail只读增加scheduleCoverage，声明和实际任务保持；缺日/异常诊断，不造游客解锁。
2. F04：PlaceReviewRepository同真实公开评价集合一次GROUP BY；PlaceReviewService共享只读摘要；PublicPlaceView用JsonUnwrapped保留旧Place JSON字段，公开列表与详情同源。列表按本站摘要评分降序、无评价最后、再按创建时间/ID稳定排序。搜索读取同排序的公开列表；活动页消费同一公开列表。管理员旧历史数值维护和既有评论写路径不改。
3. F05：PostRepository完整公共类别投影与PostCategoryOptions既有五项发布建议合并，精确原键、排序/去重、无alias；PostService/AiPostAssistantService被动共享原建议常量，提示词内容等价，不新增Provider调用或自由输入写约束。
4. F06：SkateEquipmentRepository全库status=1类别投影，独立于列表筛选/分页；匿名只读categories路径复用已有权限匹配，不改全局SecurityConfig。
5. D01：离线核对正式资料，逐299缺口关联实体/字段/来源/日期/适用规格/状态并分区。HW002/003已在当前正式规范库转写，交review-only补充事实候选，不伪造新增目录变更。

原迁移、原始资料、正式v1、参考报价、计算算法和前端不改。F01/02/07/08/09/UX由后续前端处理；前端完整/待补分区不等于资料已补齐。

## 本地复验

```powershell
.\mvnw.cmd -s .mvn/settings.xml -q '-Dlogging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR' '-Dtest=PublicReadContractTest,TrainingPlanServiceTest,PublicContentServiceTest,EquipmentServiceTest,PostServiceReplyTest,AiSecurityTest,SecurityBoundaryTest,BoardReferenceStorageTest,BoardCalculationTest,BoardDatasetValidatorTest' test
python -X utf8 -B docs/equipment-data/test_public_supplement.py
```

H2测试自行create-drop隔离库，非MySQL或迁移验收；公开路径示例写入target/test-evidence/public-remediation/backend-contract-examples.json（MockMvc+真实服务/H2，全部业务记录为合成fixture）。不把旧AUDIT12表征测试当修复门禁。原始真实参考目录测试保持323/322.99/100元边界、60000/78620小计、total/delta=null及型号错配/2000预算保护。

API字段、旧消费者兼容和失败语义见public-remediation-20261021-api.md；资料核对候选、缺口与不能宣称的结论见equipment-data/public-remediation-20261021/README.md。后端交付后idle，由协调者派前端，再独立复测和PR；不合并部署。
