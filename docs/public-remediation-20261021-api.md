# 公开只读契约 API 与前端交接

沿用ApiResponse：成功HTTP200/code200/data；错误必须检查业务code，现有通用错误可能HTTP200/code500且不含data。本轮不改变全站错误协议。全部以下GET匿名可读，路径无需登录；写入鉴权未变。

## F03 训练日程

`GET /api/skate/training-plans/{planId}` 保留data.plan、data.tasks，新增 **data.scheduleCoverage**：

```json
{"declaredDays":7,"availableDays":[1,2,3],"missingDays":[4,5,6,7],"complete":false}
```

availableDays为1..declaredDays有实际任务的去重升序集合；同一天多个任务不增加覆盖天数。声明null/非正、任务dayNo空/非正/越界时新增diagnostics（code、任务id/dayNo等），complete=false；原任务仍在tasks中，没有修改训练/用户进度。完整声明下全部天有任务且无异常才complete=true；空任务不伪造内容。列表形状不变，未增加列表覆盖字段，避免N+1。

前端显示“计划7天，已有3天任务，剩余资料待补充”，列缺日/异常；不能解释为游客预览或登录解锁。

## F04 本站评价同源摘要

`GET /api/public/places` 每行新增localReviewSummary；原Place所有序列化字段保留（ID/name/address/intro/score/reviewCount/审核及时间字段等），不增加place嵌套层。

`GET /api/public/places/{id}` 在旧详情字段外增加同一摘要。`GET /api/public/places/{id}/reviews` 原评价列表不变。摘要集合与该列表同为tb_place_review的对应场地记录，用户名失效不改变评价计数。

```json
{"score":null,"count":0,"source":"LOCAL_REVIEWS"}
```

多评价score是JSON数字，按现有一位小数HALF_UP；count是非负整数。列表一次批量聚合，不逐场地查评价。列表服务端按本地score降序/null最后，再按创建时间倒序/ID升序稳定排序；搜索也从同公开列表派生。活动场地推荐、列表和详情前端应统一用摘要，不能重新按旧score排序。

旧score/reviewCount仅兼容保留，不作为本站评分显示/排名后备；没有证据说明它们来自高德等外部来源。新读摘要不会保存Place，不触发历史重算。原评论创建/删除维护旧值的已有路径未改。

聚合异常保持业务失败，不回退null/0；data缺失、接口报错和未包含新字段时显示“评分资料暂不可用”，不假装0条。正常null/0才是暂无本站评价。异步点评列表失败单独提示/重试。

## F05 社区类别

`GET /api/public/posts/categories`：

```json
{"filterOptions":[{"key":"装备讨论","label":"装备讨论"}],"createOptions":[{"key":"技巧交流","label":"技巧交流"}],"aliasMap":{}}
```

filterOptions为整个公开帖子集合的distinct非空原始类别与五项既有发布建议的并集，不受页码/关键字/当前筛选影响，原键稳定排序。createOptions完整固定为：技巧交流、装备讨论、路线分享、活动讨论、经验分享；示例只展示形状，非完整列表。历史任意值不会成为新增发布建议，原自由输入发布仍可用。装备讨论/装备分享是两个精确键，不归并；aliasMap始终空。

客户端“全部”发送空category，不能作为服务端新增类别。沿用`GET /api/public/posts?category=...`精确筛选。类别失败可重试，不能取当前页帖子猜全库类别。

## F06 装备类别

`GET /api/skate/equipments/categories` data是数组：

```json
[{"key":"工具","label":"工具"},{"key":"砂纸","label":"砂纸"}]
```

来自全库status=1，与列表可见性一致；去空、去重、稳定原字符串排序，不受列表筛选。空库返回[]，失败走错误信封。装备“全部分类”发送空category，沿用现有装备分页/过滤路径。工具/砂纸不应由硬编码白名单隐藏。

## 不扩展的契约与证据边界

对比厚度、参考预算下界和完整/待补分区均由前端按冻结现有字段处理，没有新选板字段或算法修改。价钱仍是参考估价；total=null不补0，knownSubtotal=78620在60000预算下的18620分下界不能当完整方案总价。D01仍缺国内螺丝/垫片价格和安装规则。

PublicReadContractTest提供真实服务与H2的MockMvc请求响应；这不是监听端口HTTP、真实浏览器或生产证据。后续独立角色须复测真实匿名页面/网络与权限。资料中的真实来源事实与测试合成业务记录分开，不把fixture评价/训练/类别当现网数据。
