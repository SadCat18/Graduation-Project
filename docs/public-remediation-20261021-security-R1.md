# PUBLIC-REMEDIATION FIX-R1：生产依赖单节点修补

仅将锁内`node_modules/follow-redirects`从1.16.0修补到1.16.1，更换version/resolved/integrity三个值；Axios保持1.20.0、package.json及其他锁节点/应用源码/CI门槛不变。官方registry确认Axios约束`^1.16.0`兼容目标；官方tarball的SHA-512与registry integrity一致。

- GHSA-q3jh-wrc6-jgq8：https://github.com/advisories/GHSA-q3jh-wrc6-jgq8
- GHSA-8r9p-f939-6h3c：https://github.com/advisories/GHSA-8r9p-f939-6h3c
- 修复包：https://registry.npmjs.org/follow-redirects/1.16.1

新版本规范化array-form headers，并对自定义sensitiveHeaders中的正则元字符作完整转义。回归使用纯127.0.0.1随机端口及dummy标记，涵盖Axios正常GET/POST、同源保留、跨源剥离、307保留POST、302转GET、禁重定向/循环上限，以及两个公告的同源正控和跨源负控，确保普通同名前缀头不被误删。不做外部请求、真实凭据或生产利用。

## 复验

在frontend目录、Node20/npm10执行：

```
npm ci --registry=https://registry.npmjs.org --no-audit --no-fund
npm audit --omit=dev --registry=https://registry.npmjs.org --json
npm audit --registry=https://registry.npmjs.org --json
node tests/security/axios-redirects.cjs
node node_modules/vitest/vitest.mjs run --pool=forks --maxWorkers=1 --minWorkers=1
node node_modules/@vue/cli-service/bin/vue-cli-service.js build
```

39个既有`tests/*.test.cjs`逐个执行；安全回归在tests/security，独立运行，不混入39计数。本轮无后端/无关浏览器重复复测。

前端角色实际使用全新目录及全新npm缓存完成冷npm ci（956包、exit0），安装前缓存不存在；与实际候选新锁逐字节一致，实际Axios1.20.0/follow-redirects1.16.1。共享旧node_modules没有执行安装；候选仅替换联接，改向该独立新安装，以免后续误用1.16.0。独立复审也可根据新锁再次干净安装。

结果：生产audit0/exit0；完整audit45项（5critical/23high/17moderate，exit1），为仍存开发依赖范围，不与生产相加、不宣称全依赖清零。旧审查生产1high/完整46为修补前记录。139组件/16文件、39合同、12loopback均通过；生产构建57b82cd4ec70f92b通过。构建在独立新安装目录完成，新产物归档R1证据，不替换原候选dist的98个已审查资产。

B24/F39、review新增3测试及原目录/迁移/备案/锁其余节点/双方索引与HEAD均需在R1交付保护清单中复核。前端不提交/推送/PR/合并/部署，后续Server Test独立复审继续PR及准确head全CI。本地loopback与组件检查不代替真实HTTP/MySQL/远端CI或生产验收。
