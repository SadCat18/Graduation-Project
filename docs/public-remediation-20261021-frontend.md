# PUBLIC-REMEDIATION-20261021-F 前端复验说明

范围为已冻结F01-09、D01展示分区、UX01/02；契约见public-remediation-20261021-contract.md和public-remediation-20261021-api.md，末尾总负责补充优先。没有改服务端、数据目录、迁移、锁或CI门禁，没有提交/推送/合并/部署。

## 实现与限制

|项|实现与可复验入口|
|---|---|
|F01|ActivitiesPage地区独立请求随包MIT快照，省仅用于选市，原city/district/keyword/expired查询参数不变；各级loading/error/ready/retry、父级立即清空、序号和父值双守卫。地图独立按需加载，失败/超时后可显式重试，地区重试不初始化地图。ActivityPublishPage既有独立POI/高德地区流程只读核对，未改发布或鉴权；共享amap仅修失败缓存及超时。|
|F02|预算与鞋码字段分别提示/关联/编辑清错；预算全为元文案，传输仍为分安全整数，鞋码仅记录。|
|F03|详情根scheduleCoverage显示声明、实际覆盖天、缺日和异常，保留真实tasks全量；未知不称完整，登录不补齐缺日，失败可重试。|
|F04|活动推荐、本站评分/计数、排序/热度/高分标签及详情统一localReviewSummary；null/0为真实空，缺字段/错误不是0分；评价列表独立错误重试。当前没有PlacesPage.vue或/places列表路由，实际公开列表消费者是ActivitiesPage推荐区，未新增虚假页面。|
|F05|全库filterOptions与createOptions同契约、精确原键；装备讨论/分享分开，历史未知值可筛；发布仍自由输入，建议失败独立重试。|
|F06|装备类别从全库匿名接口获得，不从30条当前页推导；含工具/砂纸，空过滤恢复全部；分类和列表错误独立重试。|
|F07|个人头像本地中性SVG回退一次，再失败隐藏；备案图回退透明装饰SVG，再失败隐藏，官方链接/编号/文本逐字保留，不伪造官方图章。0粉丝暂无，1/多显示实际计数，无本人头像充当粉丝。|
|F08|详情/比较共享类别规格格式化；垫片厚度12.7/3.175/0保留，null/undefined未知；螺丝长度等已有字段照实展示，不推断兼容。|
|F09|REFERENCE且total未知、budget/known为有效非负分安全整数时推导超预算下界；78620/60000显示至少超186.20元，总价/delta不回填；等额/低于/缺预算不报超限或完整可行。|
|D01|priceComplete/有效总价/状态/missing/checks严格判断分区；完整参考、精确/厂家成品、待补、明确不兼容分开。预算超限独立常显。真实资料仍无完整组装，空完整参考区明确说明；所有待补候选、来源、缺口及searchTruncated保留。|
|UX01|先结论/预算/差异，规格/来源/规则可展开；按解释分组只影响展示，全部原检查及type/key/version/state/scope/source完整保留；缺口/不兼容/安全建议/预算下界常显。|
|UX02|快讯绝对日期与时效待确认，旧相对时间通知标历史活动；商品URL按实际路径区分首页/平台/来源并保持安全链接；已知场地tag和安全提示只在展示层翻译，原搜索键不变；空态给可执行建议，匿名打卡填写前登录预告，轮播圆点名称/当前状态。|

## 地区资料与查询证据

public/regions/README.md及LICENSE.txt保存来源、版本5.0.1、MIT、原字节hash和覆盖说明。34个省级条目和包内下级是固定快照，不宣称2026最新行政区全覆盖。重庆市辖区/县合并为真实区县；北京/天津/上海等城市值用原省级地名；省直辖县级分组展开济源/仙桃/石河子等实际城市，真实无下级为[]；港澳保留原名称字形。没有伪造分组名作为城市或填造缺失地区。未覆盖地区明确可用关键词查找。

前端阶段匿名GET观察：未过期活动为空；成都市+郫都区+expired=true有2条，武侯区有1条，名称与快照一致。这仅证明既有公开查询及名称控制，不代表候选已部署或新的后端HTTP验收。单测及本地浏览器另用合成业务数据验证参数/时序，不能替代真实HTTP/DB验收。

## 独立复验

使用锁对应已安装依赖和Node20。不要对共享node_modules联接执行安装。

- 前端目录：`node node_modules/vitest/vitest.mjs run --pool=forks --maxWorkers=1 --minWorkers=1`。
- 前端目录：逐个`node tests/*.test.cjs`，39个脚本分别运行（不要将通配表达式作为单个Node脚本）。
- 前端目录：`node node_modules/@playwright/test/cli.js test e2e/public-remediation.spec.cjs --workers=1`。本地Windows设PLAYWRIGHT_CHANNEL=msedge，独立复核可用安装的Chromium。
- 前端目录：`node node_modules/@vue/cli-service/bin/vue-cli-service.js build`。

新稳定fixture tests/fixtures/public-remediation-20261021.json是后端MockMvc/H2合成记录的原信封。reference-600.json仍为既有记录形状，预算控制明确在浏览器测试内构造。所有新浏览器测试不截图、无真实登录写入/付费Provider。原board-assistant浏览器测试仅增加一次展开来源操作，保留原来源可见性断言及诊断，未降低阈值。原AUDIT缺陷表征测试不在门禁中。

验证范围为前端本地组件、契约脚本、实际浏览器UI和生产构建；不宣称实际后端HTTP/MySQL/远端LinuxCI或生产验收。完整候选独立复测/PR交后续Server Test。
