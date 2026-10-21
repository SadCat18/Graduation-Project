# 社区读竞态独立复审

基线PR6合并提交759bba5f905a2cbbdf6e18286cb56c8c73b0edc1。独立核对前端4文件和687源清单，业务变更仅CommunityPage读列表代次、不可变category/sort/keyword/page/token/role及success/catch/finally、分页回退和身份/卸载防护；原写入函数、装备页及后端main源码不变。

独立复跑16项正确行为组件回归通过，覆盖初始全部慢/分类快、A→B→A、旧成功/错误/finally、当前失败和重试、page/sort/keyword、推荐/普通、身份和卸载。不是旧缺陷表征，也不与开发角色155项重复相加。

新增CommunityReadRaceHttpReviewTest通过实际监听HTTP、安全链、服务和H2仓储获取真实响应：全量页6条、独立本地fixture类别2条。显式浏览器将旧全量响应交付延迟到新分类2条已显示之后，再原样交付旧6条，DOM/总数/选中分类保持2条；不等待初始加载完成规避竞态。API无伪造回包，外部媒体用明确本地替身；2条新业务行是隔离合成fixture，不能视为生产内容或来源证明。

本地Windows/JDK21目标17/Node20/Edge/H2证据与远端准确head完整CI、生产无mock复验分别记录。原155组件、39契约、2mockEdge/构建为开发交付，正式CI门禁和严格transport诊断不降低。共享工具缓存偏差已记录并隔离，不进行无关清理。

复验：Node20及锁对应依赖、Chromium默认或Windows PLAYWRIGHT_CHANNEL=msedge。

```text
./mvnw -Dtest=CommunityReadRaceHttpReviewTest test
./mvnw -Dtest=CommunityReadRaceHttpReviewTest -Dcommunity.race.browser=true test
```

Windows使用mvnw.cmd。显式浏览器配置playwright.community-http.config.cjs只选tests/http/community-race.spec.cjs，不混入默认mock E2E。后续PR完整CI、expected-head合并及仅前端部署结果另记发布报告；线上后端/Redis/CDB/env、HTTPS/Origin/443/308和地图构建输入应保持。
