# PR6 社区读列表竞态热修复

快速切换筛选时，新分类先返回、初始全部后返回，会让当前分类被旧列表/总数覆盖。本补丁只修改CommunityPage.vue的loadPosts读状态，不改API、类别键、推荐/排序算法或发布/点赞/收藏/评论写入，不修改装备页。

每次读取递增代次，冻结category/sort/keyword/page/token/role上下文。普通分页参数与推荐分支的本地过滤/切片都使用同一快照。success/catch/finally与越界页回退只接受当前代次和身份；旧A不能在A→B→A时因字符串相同复活。分类/排序watch同步启动新代次；当前越界页仍按当前总数读取最后有效页，新读取拥有自己的loading。当前错误保留且刷新按当前条件重试。

身份变更事件使旧读取失效并结束旧loading；卸载使在途请求失效并移除事件监听。仅在闭包比较身份，不记录token到日志/证据，不改变鉴权。关键词仍按搜索按钮应用，推荐响应处理使用请求时关键词，不读取后来尚未提交的输入。

永久正确行为回归位于frontend/src/views/CommunityReadRace.spec.js：初始慢/分类快、A→B→A、旧成功/失败/finally、当前失败刷新、旧total不能回退新页、当前越界页重读、关键词/排序、普通/推荐切换、推荐错误/重试、身份切换和卸载。与归档旧缺陷表征分开。

在frontend目录使用已有Node20依赖执行：

```
node node_modules/vitest/vitest.mjs run --pool=forks --maxWorkers=1 --minWorkers=1
node node_modules/@playwright/test/cli.js test e2e/community-read-race.spec.cjs --workers=1
node node_modules/@vue/cli-service/bin/vue-cli-service.js build
```

Windows本地浏览器可设PLAYWRIGHT_CHANNEL=msedge；既有39个tests/*.test.cjs逐个运行。新浏览器仅在localhost以明确合成API控制返回顺序，不等待初始完成再点击、不访问生产、不截图；保留真实浏览器请求/HTTP状态/DOM计数与console/pageerror/API requestfailed严格断言。它证明前端状态守卫，不替代后续真实HTTP/生产验收。未改原生产浏览器诊断、依赖锁、CI或后端。
