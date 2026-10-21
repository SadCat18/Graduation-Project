# T08 独立本地验收（2026-10-22）

候选base `255b4a22208dc22ed142609785c87892179dcd0a`；业务源码只读。详细原日志、请求响应、逐项矩阵与清单在根工作区 `.codex/evidence/training-guidance-20261022/review/`。本次不提交、推送、部署、连接生产或调用付费Provider。

## 已验证的层次

- 新增独立 MySQL/HTTP 集成用例共10个不同场景，按需分组执行，不能将过程重跑次数相加。MySQL Community 8.0.45 / InnoDB，全新随机端口、127.0.0.1绑定、独立临时数据目录，全部实例正常停止并保留数据目录；未操作共享MySQL或腾讯云CDB。
- 实际Spring Boot监听、真实SecurityConfig/JWT账户校验和方法权限、JPA/JDBC事务。测试签名身份只属于隔离数据库；管理员ID 910001/920001明确不存在于tb_user。没有绕过生产登录或生成生产JWT。
- ProviderRegistry只返回显式本地Provider替身，使用真实AiGatewayService场景解析及30次/分钟限流；成功、异常、503降级、31次429及上下文绑定可复核。验证码Redis存储使用本地替身，未声称真实Redis或真实外部模型验证。
- 实际浏览器使用开发者最终106产物（逐个hash相符），代理至上述真实HTTP/MySQL，不替换API响应体。独立6种浏览器场景：完整用户流程、拒绝不改安排、管理员版本发布和安全停用、真实旧任务响应后到、服务端发布校验错误与键盘、已提交反馈响应丢失后的原样恢复。外部图片使用本地占位，不调用地图或其他外部服务。
- 竞态仅控制真实响应的送达顺序；未知结果仅人为丢弃一次已提交的真实反馈响应，明确记录该注入失败，其他核心API传输失败仍判失败。双击提交仅一次发送，恢复重试相同key/body/version且仍一条练习，没有假成功。
- 独立复跑58项相关后端/H2/MockMvc/单元回归及38项训练客户端组件测试，均0失败/错误/跳过。原开发全套452后端、193前端、39契约、3mock浏览器与构建结果另列为原证据，不与独立结果叠加。

## MySQL迁移的精确边界

真实执行原V1–V10全部10个Flyway迁移；其后在代表性V10运行基线上插入旧计划/旧完成记录，再执行原V11–V13三项增量。旧行逐字段保留，旧计划新增mode=LEGACY；再次migrate执行0项。模式CHECK、唯一完成事实、活动槽、请求幂等与行锁并发经过真实MySQL验证。

历史V1–V10 SQL没有创建现有User实体所需的`exp`、`token_version`两列。本地夹具在V10后显式补入：

```sql
ALTER TABLE tb_user ADD COLUMN exp INT DEFAULT 0;
ALTER TABLE tb_user ADD COLUMN token_version INT DEFAULT 0;
```

依据是本项目已有User实体、t07-http测试基线及既有运行环境约定；这两列不是本次新增迁移，也没有修改旧SQL。首次未补列的失败日志保留为attempt01。故本结论是“既有两列的V10运行基线能够增量升级”，不是“空库只跑迁移即可启动全部业务”。全量Flyway SQL本身执行成功和应用运行schema完整性是两个结论。

没有DROP库、repair、清空共享数据或回退旧数据。MySQL DDL不能保证整批事务回滚。GUIDED允许plan_id为空，旧二进制不理解新行；沿用后端migration-runbook的向前修复/另审兼容回退边界。

## 复现入口

`GuidanceMysqlHttpReviewIT`使用IT后缀，避免默认Surefire测试在没有隔离MySQL时误启动数据库集成；本批未修改CI或默认测试发现规则。显式选择它并指定隔离JDBC URL方可执行。浏览器另外需要`-Dreview.browser=true`，否则该项按Assumption明确跳过，不冒称已运行。

根证据的`run-mysql.py`记录初始化、随机端口、进程启动、测试命令与shutdown；Windows工具为MySQL 8.0、JDK21、Node20/Edge。设置`T08_BROWSER=1`运行浏览器，`T08_TEST=GuidanceMysqlHttpReviewIT#方法名`可选择必要场景，`T08_BROWSER_GREP`可选择单个浏览器用例。该辅助器只建立新的隔离实例，不连接已有3306，也不读取生产数据库配置。

日期按本次2026-10-22固定Clock；浏览器创建计划时须与该测试日期保持一致。示例数据全部为本地合成，不作为生产内容、真实账号或专业教学认证。

## 已知限制

未验证生产、真实付费AI、真实Redis、腾讯云CDB差异、负载与长期历史体量；目录锁的全局串行吞吐上限保留。教学仅四类已维护文字，来源弹层展示摘录/版本/定位，非视频理解、教练或医学认证。反馈不可修改/删除的第一版约定保持。候选仍未提交，后续PR/生产发布须按负责人独立核验及授权流程继续。
