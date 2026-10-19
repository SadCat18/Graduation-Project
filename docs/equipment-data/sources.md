# 来源日志（采集日期：2026-10-09）

> 类型说明：官网=品牌官方网站/尺寸表；官方商城=品牌方直营在线商店；电商平台=京东等。
> 状态：✅=成功采集并验证；⚠️=部分可用；❌=无法访问或受限（未绕过登录/验证码/风控）。

## 一、规格与海外价格来源（全部为品牌官方渠道）

| 来源 | 类型 | 状态 | 用途 |
|---|---|---|---|
| powell-peralta.com（Flight 板面产品页） | 官网/官方商城 | ⚠️ 反爬（WebFetch 401、curl reCAPTCHA），渲染抓取成功；Flight 分类页网络错误放弃 | DECK-001 |
| nhsskatedirect.com（NHS 官方店；nhsfunfactory.com 已 301 至此） | 官方商城 | ✅（部分集合页JS渲染无商品，改用产品详情页；高频直连限流429） | DECK-002, TRUCK-001/006, WHEEL-004/006/007, BRG-004, GRIP-001/003, HW-002/003, RSR-002/003 |
| independenttrucks.com/trucks（官方 Size Chart） | 官网 | ✅ 已二次复核：139→8.0"/7.8-8.2，144→8.25"/8.2-8.375，149→8.5"/8.375-8.6，Stage11 高55mm | TRUCK-001 |
| thundertrucks.com/sizing/（官方尺寸表PNG） | 官网 | ✅ 图表逐图视觉解析；已二次复核 143/145/161 三档完全一致；另存在181档（10.0"轴） | TRUCK-002 |
| venturetrucks.net/sizes/（venturetrucks.com 当日宕机，此为 DLX 官网指向的官方域名） | 官网 | ✅ 尺寸图PNG解析 | TRUCK-003 |
| acetrucks.com/sizing-chart + mesaskatesupply.com（Ace 官方店） | 官网/官方商城 | ✅ | TRUCK-004 |
| royalskateboardtrucks.com（royaltruckco.com 域名已失效） | 官网 | ✅ 官方 Size Finder 表 | TRUCK-005 |
| bulletskateboards.com（NHS 官方 Bullet 店） | 官方商城 | ✅ | TRUCK-007 |
| crailtap.com（Girl/Chocolate/Royal 官方商城） | 官方商城 | ✅ 按中国大陆IP自动显示人民币价（¥552板面/¥345桥） | DECK-003/004, TRUCK-005国内价 |
| spitfirewheels.com/wheel-shapes/（官方目录） | 官网 | ✅ 目录站无商城，无USD价 | WHEEL-001 |
| skateone.com（Skate One Corp 官方直营商城，bones.com/powell-peralta.com 页脚官方指向） | 官方商城 | ⚠️ WebFetch 401，web_reader 抓取成功；已二次复核 Bones Reds 页 | WHEEL-002/003/005/008, BRG-001/002/003 |
| ojwheels.com/urethane（配方说明）+ NHS 官方店（价格） | 官网/官方商城 | ✅ | WHEEL-004/006 |
| zeroskateboards.com / toymachine.com / primitiveskate.com / bakerskateboards.com / realskateboards.com（Shape Guide） | 官网/官方商城 | ✅（Real 目录页JS渲染不可抓，用官方尺寸表页） | DECK-006~010 |
| elementbrand.lu（elementbrand.com 主站403，官方区域站） | 官方商城 | ✅ EUR 标价 | DECK-005 |
| dobyheart.com（DBH 滑板官网+官方商城） | 官网/官方商城 | ✅ 浏览器与抓取双通道验证，整板/板面/轮子人民币价齐全 | DECK-011, WHEEL-009/010, SET-001 |
| shortysinc.com（Shorty's 官方店） | 官方商城 | ✅ | HW-001, RSR-001 |
| jessupmfg.com（Jessup Manufacturing 官方厂商店） | 官方商城 | ⚠️ B2B模式标价$7.10需登录确认实际零售价；jessupgrip.com/jessup.com 域名均不可用 | GRIP-002 |

## 二、国内人民币价格来源（京东，浏览器实测打开商品页）

| 商品 | 价格（到手价） | 页面 | 店铺 |
|---|---|---|---|
| DBH 蓝象 8.0" 双翘整板 | ¥323（划线¥400，2万+评价） | item.jd.com/100019623506.html | DBH滑板京东自营旗舰店 |
| DBH PRO套装（官网商城多图案） | ¥580；另有 FLIP¥350/TREFLIP¥430/Agan¥460 | dobyheart.com | DBH 官方商城 |
| DBH 板面（单板） | ¥240-270（按图案） | dobyheart.com/product.html | DBH 官方商城 |
| DBH 专业硬轮 52mm/100A | ¥80 | dobyheart.com/product.html | DBH 官方商城 |
| DBH GRIND 全能轮 | ¥180 | dobyheart.com/product.html | DBH 官方商城 |
| Independent Stage 139（Mid金色实心） | ¥430（划线¥500，54+评价） | item.jd.com/10095337527533.html | DBH 京东店（进口） |
| Independent 锻双空 149 | ¥530（划线¥540，8+评价） | item.jd.com/10084607160447.html | 1985滑板 京东店 |
| Venture 锻造单空高桥 5.0H | ¥538（划线¥620，4评价） | item.jd.com/10138425732776.html | DBH 京东店（进口） |
| Krux K5 原色实心 | ¥322（划线¥380，8评价） | item.jd.com/10095340017492.html | DBH 京东店（进口） |
| 迪卡侬 Fury EVO2 桥 144 | ¥99.9（18评价，门店自提） | item.jd.com/10102421445401.html | 迪卡侬京东店 |
| BOILING 播零 入门板 1979白 | ¥446（划线¥476，7评价） | item.jd.com/10036039740943.html | 京东 |
| BOILING 播零 进阶板 半切割 | ¥720（划线¥760，1评价） | item.jd.com/10036039788481.html | 京东 |
| Girl/Chocolate 板面、Royal 桥 | ¥552 / ¥552 / ¥345 | crailtap.com | Crailtap 官方商城（人民币显示） |

## 三、无法访问或受限的来源（记录为限制，未绕过）

| 来源 | 状态 | 处理 |
|---|---|---|
| taobao.com / tmall.com 商品页 | ❌ 需登录 | 国产与进口器材淘宝/天猫旗舰店价格未采集 |
| search.jd.com | ❌ 触发风控验证页（cfe.m.jd.com risk_handler） | 改用已知名直接打开商品页；未绕过验证 |
| jd.com/jiage、/chanpin、/xinkuan 频道页 | ⚠️ 价格与商品卡JS懒加载，静态抓取为空 | 仅用作商品线索；价格以浏览器打开的 item 页为准 |
| 京东 Bones 轴承在售店铺（上海易飞等） | ⚠️ 第三方，无法核实品牌授权 | 不作为国内参考价（Bones 假货风险高） |
| bones.com / powell-peralta.com 深层页 | ❌ JS人机验证（HTTP 467/401） | 改用官方商城 skateone.com |
| elementbrand.com 主站 | ❌ 403 | 改用官方卢森堡区域站（EUR） |
| tensortrucks.com | ❌ 停机维护页（"WE'LL BE BACK SOON"） | Tensor 桥未收录 |
| royaltruckco.com / jessupgrip.com / jessup.com / mobgrip.co / baker-skateboards.com / tymach.com | ❌ DNS失效/证书过期/停放页/疑似仿冒 | 弃用；Royal/Jessup/Baker/Toy Machine 改用现官方域名 |
| antiheroskateboards.com、realskateboards.com 商品目录 | ⚠️ JS渲染无法读取 | Real 改用官方 Shape Guide；Anti-Hero 放弃 |
| grizzlygriptape.com | ⚠️ 在线但砂纸单品全部不可购买 | 弃用，改收 MOB Clear |
| bronsonspeed.com | ❌ "Coming Soon" 占位页 | Bronson G3 经 NHS 官方店验证 |
| web.archive.org | ❌ 采集环境连接超时 | 无存档交叉验证 |
| bakerskateboardsglobal.shop | ⚠️ 疑似仿冒/假货站（异常折扣） | 未采用 |

## 三之二、补充采集：淘宝渠道（2026-10-09，用户提供扫码登录的本人会话）

> 用户授权使用其本人淘宝账号扫码登录后采集；仅只读浏览搜索结果与商品页，未下单、未改动账户数据。

| 商品 | 国内价（CNY） | 店铺/来源 | 状态 |
|---|---|---|---|
| Bones REDS（黑盒） | ¥100.1（原¥130） | item.taobao.com/item.htm?id=595928553385 ｜ 55滑板店（25年老店，页面标注"授权书 正品保证"） | ✅ 商品页SKU点选核实，已售900+ |
| Bones SUPER REDS（红盒） | ¥176.4（原¥220） | 同上 | ✅ 同链接SKU |
| Bones BIG BALLS（金盒） | ¥160（原¥200） | 同上 | ✅ 同链接SKU |
| Bones CERAMIC REDS（白盒） | ¥411.4（原¥520） | 同上 | ✅ 同链接SKU |
| Bones RACE REDS（蓝盒） | ¥210.2（原¥260） | 同上 | ✅ 同链接SKU |
| Mini Logo 轴承 | ¥76.5（原¥90） | 同上 | ✅ 同链接SKU |
| Jessup 原版砂纸（全黑单张） | ¥38.7（原¥45） | item.taobao.com/item.htm?id=890046872883 ｜ 55滑板店 | ✅ 商品页核实，已售100+ |
| MOB 滑板砂（多款） | ¥59.5 | item.taobao.com/item.htm?id=611155191401 ｜ 55滑板店 | ⚠️ 搜索卡片价；商品页核验时遇验证码拦截（未绕过） |
| Spitfire F4 动作轮 | ¥300起（原¥400起） | item.taobao.com/item.htm?id=596067916282 ｜ 55滑板店 | ✅ 商品页核实，已售200+（¥99-150同名低价商品疑假货，未采用） |
| Bones STF/SPF/ATF 轮 | ¥386.3（原¥490） | item.taobao.com/item.htm?id=596946795846 ｜ 55滑板店 | ✅ 商品页核实，已售100+ |
| Thunder 147（实心/锻造中空） | ¥385（原¥500起） | item.taobao.com/item.htm?id=757207011202 ｜ 雷桥专售页（送工具+板钉） | ✅ 商品页SKU点选核实，已售97 |
| Venture V桥（进口） | ¥356-420（卡片价区间） | 周末滑板店¥420 / 进口高低桥款¥385 / 中空双空¥356.2 | ⚠️ 搜索卡片价；商品页核验遇验证码拦截（未绕过） |
| DBH 专业砂纸 | ¥34.65 | item.taobao.com/item.htm?id=538665243932 ｜ 1985滑板店 | ⚠️ 搜索卡片价 |

说明：淘宝平台补贴价活动多至 2026-10-12 24 点，有时效；55滑板店商品页明确标注 Bones/Spitfire 等品牌授权书与正品保证。

## 三之三、淘宝补充复核（2026-10-09，用户手动通过验证码后）

| 商品 | 国内价（CNY） | 店铺/来源 | 状态 |
|---|---|---|---|
| Venture 桥 5.0H/5.2H 实心 | ¥385（原¥500） | item.taobao.com/item.htm?id=836505351414 ｜ Weyoung滑板（正品授权） | ✅ 商品页SKU点选核实，已售69（替换此前卡片价区间） |
| MOB 砂纸（全黑/透明/纯色多款） | ¥59.5起（原¥70起） | item.taobao.com/item.htm?id=611155191401 ｜ 55滑板店 | ✅ 商品页复核，已售200+（卡片价准确，撤卡片价标注） |
| DBH 专业砂纸 | ¥34.65起（原¥45起） | item.taobao.com/item.htm?id=538665243932 ｜ 1985滑板店 | ✅ 商品页复核，已售900+ |
| Element Seal 8.0 板面 | ¥269（原¥480，清仓） | item.taobao.com/item.htm?id=740317741959 ｜ BLOW滑板店（7年老店，假一赔十） | ✅ 商品页"Seal 8.0【单板面】"SKU核实；同页¥174-395其他Element款未逐一验真未采用 |

## 四、抽查复核记录（2026-10-09）

1. **Independent 官方尺寸表**（independenttrucks.com/trucks）：数据集 139/144/149 轴宽与板面区间、Stage 11 高度 55mm —— 完全一致。
2. **Thunder 官方尺寸图**（thundertrucks.com/sizing/，浏览器截图逐档核对）：143→7.125"轴/≤7.4"板/Team底座50mm；145→7.62"/7.4-7.9"/50/49mm；161→9.125"/8.9-9.5" —— 与数据集一致。
3. **Bones Reds 产品页**（skateone.com，web_reader）：$24.95/8粒/Skate Rated/不含隔套垫圈/尼龙保持架+Speed Cream —— 与数据集一致。
