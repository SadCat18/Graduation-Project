# 双翘滑板器材数据集（中国大陆市场）

> 采集日期：2026-10-09 ｜ 用途：「根据预算、用途和经验水平推荐整套配置」功能的数据底座
> 范围：仅双翘（popsicle）滑板器材；预算币种为人民币（CNY）

## 当前使用方式：参考估价推荐（2026-10-10）

用户已明确采用原采集资料作大致预算推荐，不要求实时价格、库存或含运税到手价核实。原始资料经过规范转换后，正式使用 `normalized-equipment.reference.v1.json`，保留原金额、来源链接与采集日期，不将 RECORDED 标成 VERIFIED。旧规范 v1 的精确采购门槛与下文历史采集说明不阻止本参考模式使用。

正式参考目录已上线并导入：50产品、114规格、60报价、39规则、3模板，共266条记录；21条国内数值参考报价可用于估价。600元预算已在公网接口和真实浏览器验证，可显示323元、446元、580元整板；100元预算显示本次搜索最低323元及223元差额。组装缺价时总价为空，只显示已知小计和缺项，不把未知价格补为零。最低方案仅代表本次搜索范围。

入口：[选板助手](http://47.114.55.75/board-assistant)。页面说明：按已采集资料估算，实际价格和库存请通过来源自行确认。

参考契约见 `../superpowers/plans/2026-10-10-equipment-reference-pricing-contract.md`；本PR的验证范围与复验命令见 `../pr-verification-20261019.md`；未纳入已停止的DATA-01工作文件。

## 一、文件清单

| 文件 | 说明 |
|---|---|
| `equipment.json` | 原始采集数据，50 条记录（来源URL、国内外展示价格、原始规格；不直接作为有效推荐库） |
| `equipment.csv` | 由 JSON 导出的平铺汇总表（UTF-8 BOM，Excel 可直接打开） |
| `sources.md` | 来源日志：每个来源的访问状态、类型、限制说明 |
| `db-seed-skate-equipment.sql` | 可选：映射到项目 `tb_skate_equipment` 表的种子数据 |
| `validate_and_export.py` | JSON 校验 + CSV 导出脚本（修改 JSON 后重跑即可） |
| `normalized-equipment.reference.v1.json` | 当前已上线的正式参考目录；原数值价格用于 REFERENCE 估价 |
| `build_reference_candidate.py` / `test_reference_candidate.py` | 原资料参考转换与定向验证，不采集实时价格或库存 |

**记录构成**：板面 11 ｜ 桥 7 ｜ 轮子 10 ｜ 轴承 8 ｜ 砂纸 4 ｜ 桥螺丝 3 ｜ 垫片/避震垫 3 ｜ 整板 4 = **50 条**
（实测 26 条含国内参考价，其中 22 条 amount 为数值、1 条区间、3 条“起”价；34 条含海外参考价。上述统计仅证明文件内容，不证明价格有效、SKU映射或可推荐性。）

## 二、字段字典

> 注：原始需求文档在"通用字段：唯一记录编号"之后截断，以下字段体系为按需求要点推断整理，如与你的设计不一致可直接增删。

**通用字段（每条记录）**

| 字段 | 说明 |
|---|---|
| `record_id` | 唯一记录编号，格式 `类别-序号`（DECK/TRUCK/WHEEL/BRG/GRIP/HW/RSR/SET） |
| `category` | 板面 / 桥 / 轮子 / 轴承 / 砂纸 / 桥螺丝 / 垫片避震垫 / 整板 |
| `brand` / `brand_country` | 品牌与品牌所在国 |
| `model` | 型号名（同一型号的不同尺寸在 `spec` 内作为规格列出，不拆成多条记录） |
| `spec` | 类别相关规格对象（见下） |
| `summary_zh` | ≤50 字客观摘要（非商品文案） |
| `suitability_tags` | 用途标签：街式 / 滑板场 / 碗池 / 刷街 / 通勤 / 日常滑行 / 初学 / 综合 等 |
| `level_tags` | 经验水平标签：初学可 / 进阶 / 专业 / 通用 |
| `cn_price` | **国内展示参考价**（人民币），不能直接参与精确推荐计算。`null` 表示原记录没有价格，原因见 `notes` 或 `sources.md` |
| `overseas_price` | **海外价**（原币：USD/EUR），仅参考，**不得换算后充当国内价** |
| `spec_source` | 规格来源 `{url, page_title, fetched_date}`，全部为品牌官方渠道或品牌官方商城 |
| `image_source` | 图片来源链接与版权说明（**仅记录链接，未下载，默认无使用授权**） |
| `notes` | 补充事实与缺失原因 |

**`cn_price` / `overseas_price` 对象**：`{amount, currency, price_type, sales_region, source_url, collected_date, note}`。
`price_type` 取值：品牌官方商城零售价 / 京东自营旗舰店零售价 / 京东第三方滑板店零售价 / 官方商城零售价（海外）等；到手价均标注了划线原价与时效性提示。

**各类别 `spec` 要点**：板面=宽度/长度/轴距/结构；桥=各尺寸的轴宽、桥高、官方推荐板面宽度区间；轮子=直径/硬度/轮宽/接触面/配方；轴承=标准、材质、一套数量、是否含隔套垫圈；砂纸=单张尺寸与特性；螺丝=长度/头型/数量/适用场景；垫片=高度/材质/每套数量。

## 三、原始采集说明（历史记录，T01未独立复核）

以下为原采集者的说明，保留来源历史；“核实/验证”用语不能将规范库字段从 RECORDED 自动升级为 VERIFIED。来源日志记录过账号登录和验证码人工处理，本轮没有重复这些操作，也不继承历史账户授权。

1. 全部规格来自品牌官网、官方尺寸表/安装说明或品牌官方直营商城（NHS Skate Direct、Skate One、Crailtap、各品牌官网）。
2. 国内价格只采用：品牌官方商城（DBH dobyheart.com、Crailtap 人民币显示价）、京东自营旗舰店、京东在售滑板店（DBH店、1985滑板店、迪卡侬）。
3. 海外价单独存放于 `overseas_price`，不参与国内人民币推荐。
4. 缺失信息一律 `null`，缺失原因写入 `notes`；无法访问/需登录的渠道记入 `sources.md` 与限制清单，未绕过任何登录、验证码或风控。
5. 采集当日全部经过实际页面抓取验证；关键数据已抽查复核（Independent 官方尺寸表、Thunder 官方尺寸图 143/145/161 三档、Bones Reds 产品页均与数据集一致）。

## 四、桥 ↔ 板面宽度官方对照表（推荐功能核心）

下表保留原始采集对照（URL 见对应记录 `spec_source`），不是本站已核实的执行规则。实际程序应读取规范库草稿字段和缺口；未知端点、桥结构与来源范围不能默认通过：

| 板面宽度 (in) | Independent | Thunder | Venture | Ace AF1 | Royal |
|---|---|---|---|---|---|
| 7.5 | 129 (7.6轴) | 143 (7.125轴) | — | 22 (7.75轴) | 127 (7.5轴) |
| 7.75 | 129 | 145 (7.62轴) | 5.0 (7.62轴) | 22 | 139 (8.0轴)* |
| 8.0 | **139 (8.0轴)** | **147 (8.0轴)** | **5.2 (8.0轴)** | **33 (8.0轴)** | 139 |
| 8.25 | 144 (8.25轴) | 148 (8.25轴) | 5.6 (8.25轴) | 44 (8.25轴) | 144 (8.25轴) |
| 8.5 | 149 (8.5轴) | 149 (8.5轴) | 5.8 (8.5轴) | 55 (8.5轴) | 149 (8.5轴) |
| 8.75 | 159 (8.75轴) | 151 (8.75轴) | 6.1 (8.75轴) | 60 (8.75轴) | 159 (8.75轴) |

\* 各品牌区间边界略有重叠（如 Royal 139 覆盖 7.75-8.25），精确区间见各记录 `spec.size_variants[].deck_range_in`。
注意命名体系不同：Independent 编号≈毫米轴距；Thunder 同轴宽编号约大 8；Venture 小数=轴宽英寸；Ace 偏小一档（44=8.25"轴）；Krux 整数=轴宽英寸；Bullet 用毫米命名且无官方对照。
Thunder/Venture/Krux/Bullet 官方未公布的部分字段（hanger 宽度、桥高毫米数等）为 `null`，官方尺寸表为 PNG 图片者已在采集时逐图解析并抽查复核。

## 五、价格使用规则与推荐逻辑建议

1. **不得直接用原始 `cn_price` 参与精确预算计算**。只有绑定具体规格、EXACT/CNY/CN、已核实包装数量/单位、国内费用与有效期且未过期的 VERIFIED 启用报价可计价；海外价只展示，不换算。
2. 原始记录的国内展示价格锚点（2026-10-09，CNY；本轮未独立复核，并非有效精确报价）：
   - 整板：DBH 蓝象8.0 ¥323（京东自营）｜BOILING 入门 ¥446｜BOILING 进阶 ¥720｜DBH PRO套装 ¥580（官方商城）
   - 板面：DBH 板面 ¥240-270（官方商城）｜Element Seal 8.0 ¥269（淘宝清仓）｜Girl/Chocolate ¥552（官方商城人民币显示）
   - 桥：Independent Stage139 ¥430｜Thunder 147 ¥385（淘宝）｜Krux K5 ¥322｜Venture 5.0H/5.2H ¥385（淘宝授权店）/锻造5.0H ¥538（京东）｜Royal Raw ¥345｜迪卡侬 Fury EVO2 ¥99.9
   - 轮子：DBH 硬轮52mm ¥80｜DBH GRIND全能轮 ¥180｜Spitfire F4 ¥300起（淘宝）｜Bones STF/SPF ¥386.3（淘宝）
   - 轴承：Bones REDS ¥100.1｜SUPER REDS ¥176.4｜BIG BALLS ¥160｜RACE ¥210.2｜CERAMIC ¥411.4｜Mini Logo ¥76.5（均55滑板店，授权渠道）
   - 砂纸：Jessup ¥38.7｜MOB ¥59.5起（55滑板店）｜DBH ¥34.65起（1985滑板店）
3. 当前规范库没有有效精确报价，组装和成品完整可推荐样本均为0套，不能输出已验证总价或将数据不足解释为预算不足。
4. 螺丝和垫片没有国内参考价；轴承和砂纸已有部分国内参考价，但费用、有效期及具体包装仍待核实。桥报价尤其需要逐SKU确认单只/一对。
5. TRUCK-001 的 ¥430 为139 Mid金色实心，原规格为Stage11 Standard，保持不绑定；不得复制到Standard各尺寸。三种入门模板只有禁用草稿，无来源物理阈值不执行。
6. 促销“2026-10-12 24点”未确认时区，`validUntil=null`；不自行添加30天期限。人民币显示价不自动等于大陆含运税到手价。

## 六、限制清单（如实记录，未绕过任何访问控制）

| 限制 | 影响 |
|---|---|
| 淘宝/天猫商品页需登录 | 已由用户提供本人账号扫码登录后补充采集（见 sources.md 三之二）；连续访问触发过2次淘宝验证码，均未绕过，受影响条目（MOB/Venture/DBH砂纸）以搜索卡片价记录并注明 |
| 京东搜索页触发风控验证（cfe.m.jd.com） | 改用"直接打开商品页"方式，只采集到已获得商品URL的条目 |
| 京东频道页（jiage/chanpin/xinkuan）价格JS懒加载 | Element/Spitfire 等第三方店铺的京东渠道价未能核实（Spitfire/Bones 已改由淘宝授权店核实） |
| 淘宝存在疑似假货低价（如¥99-150的"Spitfire F4"） | 未采用；国内价优先取自标注授权书的 25 年老店 55滑板店 |
| elementbrand.com 主站 403；Tensor 官网停机维护；Royal 旧域名失效 | Element 用官方卢森堡区域站（EUR）；Tensor 未收录 |
| Spitfire/Thunder/Venture 官网为目录站不直销 | 这三个品牌无官方USD价 |
| web.archive.org 在采集环境不可达 | 无法做网页存档交叉验证 |
| NHS 官方店多数商品标"Sold Out"（标价可见） | 海外价为官方标价，非可下单状态 |
| 部分京东进口桥商品页未明示"单只/一对" | 推荐功能上线前建议向店铺核实数量口径 |
| Girl/Chocolate/Royal 的 Crailtap 人民币价按大陆IP显示 | 是否含税、大陆配送运费未验证 |

## 七、与 `tb_skate_equipment` 表的映射

项目现有表：`equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url`。映射方案：

| 数据集字段 | 表字段 |
|---|---|
| brand + model | `equipment_name`（如 "Independent Stage 11 滑板桥 139"） |
| category | `category` |
| brand | `brand` |
| （暂无图，不上传未授权图片） | `cover_url` = NULL |
| cn_price 区间文本，如 "¥430（到手价）"；缺失写 "暂无核实国内价（海外参考 $27.95）" | `price_range` |
| `level_tags` 拼接 | `suitable_level` |
| `spec` JSON 序列化 + 来源URL | `spec_params` |
| `summary_zh` + notes | `description` |
| `cn_price.source_url`（优先国内购买链接） | `buy_url` |

`db-seed-skate-equipment.sql` 仅保留历史展示映射，T01不执行SQL、不写旧表。规范库与旧记录数据库ID没有真实匹配，`legacyEquipmentId=null`；后续导入需另行派发并使用隔离开发环境。

## 八、图片版权说明

所有 `image_source` 仅记录官网产品图的来源链接，**未下载任何图片，默认不具有使用授权**。网站如需展示图片，请：使用自己拍摄/自制素材，或获得品牌方授权，或仅做文字参数展示。

## 九、T01规范v1与只读校验（2026-10-09）

| 文件 | 用途 |
|---|---|
| `normalized-equipment.v1.json` | 50产品、114草稿规格、60展示报价、39草稿规则、3禁用模板、50映射、299缺口；全部未启用 |
| `normalization-report.md` | 来源边界、拆分依据、统计、输入hash、验证结果及真实样本不足原因 |
| `validate_normalized.py` | Python标准库纯读取校验，不导出CSV、不联网、不写库；错误退出1，结构有效退出0 |
| `test_validate_normalized.py` | 内存合成校验样例，与真实规范库分开，不产生虚构器材记录 |

在仓库根目录执行：

```powershell
python -X utf8 docs/equipment-data/validate_normalized.py --as-of 2026-10-09T22:00:00+08:00
python -X utf8 -B docs/equipment-data/test_validate_normalized.py
```

不传 `--as-of` 时用执行时UTC时间检查报价过期；`--previous <文件>` 检查同版本内容冲突/旧版本，`--details` 输出全部缺口。默认核对相邻原始equipment.json及记录摘要；外部输入可用 `--original <文件>` 指定来源。

`valid=true` 只表示文件结构及引用符合校验，并不证明来源真实性、可推荐性、整套兼容或三套实际样本。规范库规格/报价/规则/模板及来源均为 RECORDED；已有采集日志不能代替字段证据。所有图片仅保留链接且默认无授权，本轮没有下载。
