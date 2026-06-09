# SE1 期末复习提纲

适用范围：
- 结合你提供的课件主题进行复习
- 结合当前项目 `SE1` 的真实实现进行分析
- 重点面向老师期末提问、答辩式口头检查

说明：
- 你给的几份 PDF 主题很清晰，但当前环境缺少可直接抽取 PDF 正文的本地组件，所以这份提纲采用“课件主题 + 项目代码实证”的方式整理。
- 其中“部门管理 / 岗位管理 / 员工管理”属于课堂上的通用后台 CRUD 模板，你当前项目不是 HR 系统，所以我会把它们提炼成分页、模糊查询、增删改查的通用答题方法，再映射到你项目中的后台管理、活动管理、内容管理模块。

---

## 1. 先把项目整体讲清楚

老师很可能先问：
- 你的项目是什么技术栈？
- 前后端怎么分工？
- 数据怎么存？
- 登录鉴权怎么做？

你可以先这样回答：

> 我的项目是一个基于 Spring Boot + Vue 的前后端分离项目，后端主要负责业务接口、数据库访问、登录鉴权、文件上传，前端负责页面展示、路由跳转、表单提交和后台管理。数据库使用 MySQL，登录验证码缓存使用 Redis，接口认证采用 JWT，管理员和普通用户有不同权限。

### 1.1 技术栈

- 后端：Spring Boot 3.3.5
- 前端：Vue
- ORM/数据访问：Spring Data JPA
- 安全：Spring Security + JWT
- 数据库：MySQL
- 缓存：Redis
- 构建工具：Maven

代码依据：
- [pom.xml](/D:/College/Graduation%20Project/SE1/pom.xml:1)
- [application.yml](/D:/College/Graduation%20Project/SE1/src/main/resources/application.yml:1)

### 1.2 项目结构

- 后端入口：`src/main/java/com/skatehub`
- 前端目录：`frontend`
- 数据库脚本：`database/init.sql`
- 上传目录：`upload`

你可以这样答：

> 后端使用 Controller 接收请求，Service 处理业务，Repository 访问数据库；前端通过 axios 请求 `/api` 接口，路由由 vue-router 管理，登录后把 token 存到浏览器本地。

---

## 2. Maven 建 Spring Boot 项目

对应课件：
- `Maven建Spring Boot项目.pdf`

### 2.1 你要会说的核心点

- Maven 是项目管理和构建工具
- `pom.xml` 是核心配置文件
- Spring Boot 项目一般通过父工程统一依赖版本
- 依赖写在 `<dependencies>` 中
- 打包、运行、测试都可通过 Maven 完成

### 2.2 你项目里是怎么体现的

- 通过 `spring-boot-starter-parent` 统一 Spring Boot 版本
- 依赖了 Web、Validation、JPA、Security、Redis、MySQL、JWT、Test 等组件

重点位置：
- [pom.xml](/D:/College/Graduation%20Project/SE1/pom.xml:5)

### 2.3 老师可能会问

1. Maven 有什么作用？
2. `pom.xml` 有什么用？
3. 为什么要用 `spring-boot-starter-parent`？
4. 你的项目依赖了哪些核心 starter？

### 2.4 标准回答

- Maven 的作用是统一管理依赖、项目构建、打包和测试。
- `pom.xml` 用来定义项目坐标、JDK 版本、依赖、插件等。
- `spring-boot-starter-parent` 可以帮我们统一 Spring Boot 相关依赖版本，减少版本冲突。
- 我项目主要用了 `spring-boot-starter-web`、`validation`、`data-jpa`、`security`、`data-redis`。

---

## 3. 创建 Vue 前端项目

对应课件：
- `六、创建Vue前端项目.pdf`

### 3.1 你要会说的核心点

- Vue 用来构建前端页面
- 前端通过组件化方式开发
- Vue Router 用于单页应用路由切换
- axios 用于请求后端接口

### 3.2 你项目里是怎么体现的

- 前端主入口和页面都在 `frontend/src`
- 路由集中定义在 `router/index.js`
- 请求封装在 `api/http.js` 和 `api/index.js`

重点位置：
- [frontend/src/router/index.js](/D:/College/Graduation%20Project/SE1/frontend/src/router/index.js:1)
- [frontend/src/api/http.js](/D:/College/Graduation%20Project/SE1/frontend/src/api/http.js:1)
- [frontend/src/api/index.js](/D:/College/Graduation%20Project/SE1/frontend/src/api/index.js:1)

### 3.3 老师可能会问

1. 前后端分离是什么意思？
2. Vue 路由是干什么的？
3. 前端如何调用后端接口？
4. 为什么要封装 axios？

### 3.4 标准回答

- 前后端分离就是前端负责界面和交互，后端只提供数据接口。
- Vue Router 负责根据不同 URL 切换不同页面组件。
- 前端通过 axios 向后端发送 GET、POST、PUT、DELETE 请求。
- 封装 axios 可以统一处理 token、错误信息、超时和 401 未登录跳转。

---

## 4. 拦截器 / 鉴权 / 权限控制

对应课件：
- `九、拦截器.pdf`

注意：
- 你这套项目里没有用传统 `HandlerInterceptor` 做登录校验
- 而是用了更完整的 `Spring Security + JWT 过滤器`
- 前端还配合了 `vue-router` 路由守卫

所以老师问“拦截器”时，你可以这样说：

> 课堂上拦截器的作用是对请求做统一预处理，比如登录校验。我的项目里采用的是 Spring Security 过滤器链 + JWT 认证，作用和登录拦截器类似，但更适合前后端分离项目。

### 4.1 后端鉴权逻辑

- 关闭 CSRF
- 开启跨域
- 使用无状态会话
- 放行 `/api/auth/**`、`/api/public/**`、`/upload/**`
- 其他请求必须认证
- JWT 过滤器在用户名密码过滤器前执行

重点位置：
- [SecurityConfig.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/config/SecurityConfig.java:28)
- [JwtAuthenticationFilter.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/util/JwtAuthenticationFilter.java:15)

### 4.2 管理员权限控制

- 管理接口统一在 `/api/admin`
- 类上使用 `@PreAuthorize("hasRole('ADMIN')")`

重点位置：
- [AdminController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/AdminController.java:26)

### 4.3 前端路由守卫

- 未登录不能进需要认证的页面
- 非管理员不能进后台页

重点位置：
- [frontend/src/router/index.js](/D:/College/Graduation%20Project/SE1/frontend/src/router/index.js:40)

### 4.4 老师可能会问

1. 拦截器的作用是什么？
2. 你项目是怎么做登录校验的？
3. 为什么前后端分离适合用 JWT？
4. 普通用户和管理员权限怎么区分？

### 4.5 标准回答

- 拦截器或过滤器的作用是对请求做统一处理，比如登录校验、权限判断、日志记录。
- 我项目后端使用 Spring Security 和 JWT 过滤器解析请求头里的 token，前端路由守卫负责页面级限制。
- JWT 适合前后端分离，因为服务端不用保存会话状态，客户端只需要携带 token。
- 管理员接口统一加了角色限制，只有 `ADMIN` 角色才能访问。

---

## 5. 验证码登录

对应课件：
- `十、验证码登录.pdf`

这是你项目和课件最贴近的一块，必须重点背。

### 5.1 实现思路

后端：
- 先生成验证码图片
- 把验证码内容缓存到 Redis
- 前端登录时提交 `captchaId + captchaCode`
- 后端校验成功后删除 Redis 中验证码，防止重复使用

前端：
- 页面加载时生成 `captchaId`
- `<img>` 标签请求验证码图片
- 登录失败后刷新验证码

### 5.2 后端代码落点

验证码生成：
- [CaptchaController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/CaptchaController.java:29)

登录前校验验证码：
- [AuthController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/AuthController.java:25)

验证码 Redis 校验与一次性消费：
- [AuthService.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/service/AuthService.java:59)

Redis 配置：
- [application.yml](/D:/College/Graduation%20Project/SE1/src/main/resources/application.yml:11)

### 5.3 前端代码落点

验证码图片地址生成、刷新：
- [LoginPage.vue](/D:/College/Graduation%20Project/SE1/frontend/src/views/LoginPage.vue:43)

登录时提交验证码：
- [LoginPage.vue](/D:/College/Graduation%20Project/SE1/frontend/src/views/LoginPage.vue:79)

### 5.4 老师很爱问的点

1. 验证码为什么要加？
2. 为什么不用 session 存验证码？
3. 为什么验证码校验成功后要删除？
4. `captchaId` 有什么作用？

### 5.5 标准回答

- 验证码主要防止暴力破解、机器批量登录和恶意刷接口。
- 前后端分离项目更适合把验证码放 Redis，而不是强依赖 session。
- 校验成功后删除验证码，是为了防止验证码被重复使用。
- `captchaId` 用来区分不同验证码实例，前端用它请求图片，后端用它作为 Redis key 的一部分。

---

## 6. 用户信息修改：修改密码、修改头像、修改资料

对应课件：
- `十一、用户信息修改（修改密码）.pdf`
- `十一、用户信息修改（修改头像）.pdf`

### 6.1 后端接口

- 查询个人资料：`GET /api/user/profile`
- 修改个人资料：`PUT /api/user/profile`
- 修改密码：`PUT /api/user/password`

重点位置：
- [UserController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/UserController.java:25)
- [UserService.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/service/UserService.java:29)

### 6.2 修改密码逻辑

- 先根据当前登录用户查数据库
- 验证旧密码是否正确
- 新密码使用 `BCryptPasswordEncoder` 加密后再保存

重点位置：
- [UserService.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/service/UserService.java:51)
- [SecurityConfig.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/config/SecurityConfig.java:41)

### 6.3 修改头像逻辑

- 前端先选择本地图片
- 调用文件上传接口上传头像
- 返回图片访问路径
- 再把头像 URL 保存到个人资料中

前端位置：
- [ProfilePage.vue](/D:/College/Graduation%20Project/SE1/frontend/src/views/ProfilePage.vue:74)
- [ProfilePage.vue](/D:/College/Graduation%20Project/SE1/frontend/src/views/ProfilePage.vue:131)

后端上传接口位置：
- [FileController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/FileController.java:27)

### 6.4 文件上传你要会讲

- 用 `MultipartFile` 接收文件
- 校验空文件
- 校验大小
- 校验扩展名和类型
- 按日期分目录保存
- 用 UUID 防止重名

### 6.5 老师可能会问

1. 修改密码为什么不能明文保存？
2. 修改头像为什么通常分两步？
3. 文件上传要做哪些校验？
4. 为什么要用 UUID 文件名？

### 6.6 标准回答

- 密码必须加密保存，防止数据库泄露后明文暴露。
- 头像修改通常先上传文件，再把返回的 URL 保存到用户资料表。
- 文件上传要校验文件是否为空、文件大小、文件类型、文件后缀。
- UUID 能避免文件重名覆盖，提高安全性和稳定性。

---

## 7. 前后端合并部署

对应课件：
- `十二、前后端合并部署.pdf`

### 7.1 课堂常见答法

所谓前后端合并部署，通常有两种思路：

1. 前端打包后放到 Spring Boot 的静态资源目录，由后端统一提供访问
2. 前端和后端分开部署，再通过 Nginx 做反向代理

### 7.2 你项目更适合怎么回答

你这个项目开发时是分离的：
- Vue 前端单独跑
- Spring Boot 后端单独跑

但部署时可以：
- 前端先打包
- 再交给 Nginx 或 Spring Boot 静态资源统一对外提供

项目里已经能看到部署思路：
- [README.md](/D:/College/Graduation%20Project/SE1/README.md:1)
- [deploy/nginx/amap-security.conf.example](/D:/College/Graduation%20Project/SE1/deploy/nginx/amap-security.conf.example:1)

### 7.3 老师可能会问

1. 什么是前后端合并部署？
2. 开发环境和部署环境有什么区别？
3. Nginx 在这里起什么作用？

### 7.4 标准回答

- 合并部署就是把前端页面和后端接口统一部署到一个对外访问入口。
- 开发环境通常前后端分开跑，便于调试；部署环境更强调统一访问、跨域处理和性能。
- Nginx 可以做静态资源托管、反向代理、路径转发，也能解决部分跨域和安全代理问题。

---

## 8. 分页查询

对应课件：
- `十三、部门管理（分页查询）.pdf`
- `十四、岗位管理（分页查询、模糊查询）.pdf`

你项目虽然不是“部门/岗位”，但分页是实打实做了的。

### 8.1 分页原理

- 前端传 `page` 和 `size`
- 后端把页码转成从 0 开始的下标
- 数据库按页返回
- 再把 `total + list` 返回给前端

### 8.2 你项目里的后端实现

管理员分页：
- 用户分页
- 帖子分页
- 评论分页
- 活动分页

重点位置：
- [AdminController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/AdminController.java:37)
- [AdminService.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/service/AdminService.java:42)
- [PageResult.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/util/PageResult.java:1)

### 8.3 你项目里的前端实现

- 统一定义每页大小 `ADMIN_PAGE_SIZE = 10`
- 根据 `total` 计算总页数
- 点击“上一页/下一页”重新请求接口
- 删除最后一条数据时自动退页，防止空白页

重点位置：
- [frontend/src/views/AdminPage.vue](/D:/College/Graduation%20Project/SE1/frontend/src/views/AdminPage.vue:7)
- [docs/ADMIN_PAGINATION_README.md](/D:/College/Graduation%20Project/SE1/docs/ADMIN_PAGINATION_README.md:1)

### 8.4 老师可能会问

1. 为什么分页？
2. 后端分页和前端假分页有什么区别？
3. `page` 为什么常常从 1 传，但数据库分页从 0 开始？
4. 你的返回结果为什么要封装成 `PageResult`？

### 8.5 标准回答

- 分页是为了减少一次性加载的数据量，提高性能和用户体验。
- 后端分页是真正只查一页数据；前端假分页通常是一次查全部，再本地切片。
- 前端更适合从第 1 页开始给用户看，后端框架很多是 0 基下标，所以要转换。
- `PageResult` 把总条数和当前页列表统一返回，前端更容易直接渲染分页组件。

---

## 9. 模糊查询

对应课件：
- `十三、部门管理（模糊查询）.pdf`
- `十四、岗位管理（分页查询、模糊查询）.pdf`

### 9.1 课堂标准知识点

- 模糊查询一般对应 SQL 的 `LIKE`
- 常见写法：`%关键字%`
- 可按名称、内容、地址等字段进行匹配

### 9.2 你项目里的真实实现

最典型的是活动列表查询：
- 按城市模糊查
- 按区域模糊查
- 按关键字模糊查标题、内容、地点、地址等

重点位置：
- [ActivityController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/ActivityController.java:27)
- [ActivityService.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/service/ActivityService.java:40)

### 9.3 你可以这样答

> 虽然我的项目不是部门管理系统，但模糊查询思路一样。我在活动列表中支持按城市、区域和关键字查询，本质上就是把用户输入转成 `like %keyword%` 条件，匹配多个字段。

### 9.4 老师可能会问

1. 模糊查询和精确查询区别是什么？
2. 模糊查询为什么要做关键字判空？
3. 为什么要把多个字段一起查？

### 9.5 标准回答

- 精确查询要求完全相等，模糊查询支持部分匹配。
- 关键字为空时不应拼接 `LIKE %%`，否则无意义还可能影响效率。
- 多字段查询更贴近用户使用习惯，比如一个关键字可能想搜标题、内容或地点。

---

## 10. 增删改查

对应课件：
- `十三、部门管理（增、改、删）.pdf`
- `十四、岗位管理（增、改、删）.pdf`
- `十五、员工管理.pdf`

### 10.1 课堂常规问法

老师会围绕这几个词问你：
- 新增怎么做？
- 修改怎么做？
- 删除怎么做？
- 查询怎么做？
- 前后端如何配合？

### 10.2 你项目里最适合举例的 CRUD 模块

建议优先举这些：
- 公告管理
- 资讯管理
- 场地管理
- 轮播图管理
- 用户管理
- 活动管理

因为这些都集中在管理员后台里，结构清晰，好答。

重点位置：
- [AdminController.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/controller/AdminController.java:110)
- [AdminService.java](/D:/College/Graduation%20Project/SE1/src/main/java/com/skatehub/service/AdminService.java:80)
- [frontend/src/views/AdminPage.vue](/D:/College/Graduation%20Project/SE1/frontend/src/views/AdminPage.vue:149)

### 10.3 你答 CRUD 的万能模板

新增：
- 前端填表单
- axios 发 POST
- 后端 Controller 接收 DTO
- Service 做校验和业务处理
- Repository 保存到数据库

修改：
- 前端回显旧数据
- 修改后发 PUT
- 后端根据 id 查原对象
- 更新字段再保存

删除：
- 前端拿到 id 发 DELETE
- 后端根据 id 删除
- 删除后前端刷新列表

查询：
- 普通查询用 GET
- 分页查询加 `page size`
- 条件查询加关键字或状态参数

### 10.4 老师可能会问

1. 为什么新增一般用 POST，修改用 PUT，删除用 DELETE？
2. 为什么业务逻辑不直接写在 Controller？
3. 为什么有的模块要先根据 id 查询再更新？
4. 前端怎么知道操作成功了？

### 10.5 标准回答

- 这是 REST 风格接口设计，语义更清晰。
- Controller 负责接收请求，Service 负责业务逻辑，分层更清楚。
- 更新前先查原数据，便于判断对象是否存在，也便于做业务校验。
- 后端统一返回响应结构，前端根据返回码和消息判断是否成功。

---

## 11. 统一响应、前后端接口约定

这个虽然不一定是 PDF 标题，但老师特别容易顺着问。

### 11.1 你项目里的统一返回

- 所有接口统一返回 `ApiResponse`
- 分页接口数据体中再套 `PageResult`

你可以这样答：

> 我项目里后端不是直接返回实体对象，而是统一封装成响应体，例如成功码、消息、数据。这样前端处理更统一，错误也更容易提示。

前端处理位置：
- [frontend/src/api/http.js](/D:/College/Graduation%20Project/SE1/frontend/src/api/http.js:17)

---

## 12. 登录态保存、退出登录、前端鉴权

虽然课件名字里未必单独写出来，但和拦截器、验证码、Vue 路由是连着问的。

### 12.1 你项目里的做法

- 登录成功后把 `token / role / name` 存到 `localStorage`
- axios 请求拦截器统一加 `Authorization: Bearer token`
- 路由守卫阻止未登录访问受限页面
- 401/403 时清理本地认证信息并跳转登录页

重点位置：
- [frontend/src/utils/auth.js](/D:/College/Graduation%20Project/SE1/frontend/src/utils/auth.js:12)
- [frontend/src/api/http.js](/D:/College/Graduation%20Project/SE1/frontend/src/api/http.js:9)
- [frontend/src/router/index.js](/D:/College/Graduation%20Project/SE1/frontend/src/router/index.js:40)

### 12.2 老师可能会问

1. token 存在哪里？
2. 请求头怎么带 token？
3. 如果 token 失效怎么办？

### 12.3 标准回答

- token 存在浏览器本地存储中。
- 每次请求前由 axios 请求拦截器自动加到 `Authorization` 请求头。
- 如果后端返回 401 或 403，前端会清理本地登录态并跳回登录页。

---

## 13. 你项目里最值得重点背的 8 个实现点

这 8 个最容易被问到，也最能体现你是自己做过项目的。

1. `pom.xml` 中引入了 Web、JPA、Security、Redis、MySQL、JWT 等依赖。
2. `SecurityConfig` 配置了无状态认证和接口放行规则。
3. `JwtAuthenticationFilter` 负责从请求头读取并解析 token。
4. 登录验证码不是放 session，而是放 Redis，并且校验成功后立即删除。
5. 用户密码使用 `BCryptPasswordEncoder` 加密保存。
6. 头像上传和视频上传都使用 `MultipartFile`，并校验文件类型、大小、扩展名。
7. 后台管理中的用户、帖子、评论、活动都实现了分页查询。
8. 前端使用 axios 封装和 Vue Router 路由守卫完成登录态控制。

---

## 14. 期末高频问答速记

### 14.1 什么是前后端分离？

前端负责页面和交互，后端负责接口和业务逻辑，通过 HTTP/JSON 通信。

### 14.2 为什么使用 Spring Boot？

开发效率高，自动配置能力强，适合快速搭建 Java Web 项目。

### 14.3 为什么使用 Maven？

为了管理依赖、统一构建、打包和测试流程。

### 14.4 为什么用 JWT？

适合前后端分离，无状态，前端只需携带 token 即可访问受保护接口。

### 14.5 为什么用 Redis 存验证码？

读写快，支持过期时间，适合短时验证码缓存。

### 14.6 为什么密码要加密？

保护用户隐私，防止数据库泄露后明文密码暴露。

### 14.7 什么是分页？

把大量数据分批展示，减少单次查询压力，提高性能和体验。

### 14.8 什么是模糊查询？

按关键字部分匹配数据，一般使用 `LIKE %关键字%`。

### 14.9 为什么要统一返回结果？

方便前端统一处理成功和失败逻辑，也有利于接口规范化。

### 14.10 文件上传为什么不能直接原名保存？

因为会重名、覆盖，也不安全，所以一般会改成 UUID 文件名。

---

## 15. 如果老师让你“结合项目演示一个完整流程”，推荐讲这条线

最推荐讲：

`验证码登录 -> token 保存 -> 路由鉴权 -> 进入个人中心 -> 上传头像 -> 修改资料 -> 修改密码 -> 管理员后台分页管理`

这条线的优点：
- 覆盖课件最多
- 包含前后端联动
- 有 Redis、有 Security、有文件上传、有分页
- 一条线就能把你学过的大多数知识串起来

你可以按这个顺序讲：

1. 用户进入登录页，前端先生成 `captchaId` 并请求验证码图片。
2. 用户输入账号、密码、验证码后提交登录。
3. 后端先去 Redis 校验验证码，再校验账号密码。
4. 登录成功后返回 JWT，前端保存到本地。
5. 后续 axios 请求自动携带 token。
6. 如果访问个人中心，后端通过 JWT 识别当前用户。
7. 修改头像时，前端先上传图片，后端保存文件并返回图片路径。
8. 修改密码时，后端先验证旧密码，再保存新加密密码。
9. 管理员进入后台后，用户、帖子、评论、活动列表都是分页加载的。

---

## 16. 临考前 10 分钟只背这些

如果时间很紧，只背下面这些句子：

1. 我的项目是 Spring Boot + Vue 前后端分离项目，MySQL 存业务数据，Redis 存验证码，JWT 做登录鉴权。
2. Maven 通过 `pom.xml` 管理依赖和构建，Spring Boot 通过 starter 快速集成各模块。
3. 前端使用 Vue Router 管理页面，axios 统一请求后端接口。
4. 后端使用 Spring Security + JWT 过滤器做登录校验，管理员接口通过角色权限控制。
5. 验证码生成后存入 Redis，登录时提交 `captchaId` 和 `captchaCode`，校验成功立即删除。
6. 用户密码使用 BCrypt 加密保存，不能明文入库。
7. 头像上传通过 `MultipartFile` 实现，校验文件类型、大小和后缀，再按日期目录和 UUID 文件名保存。
8. 后台管理中的用户、帖子、评论、活动实现了分页查询，接口返回 `total + list`。
9. 模糊查询本质是 `LIKE %关键字%`，我在活动列表里按城市、区域、关键字做过类似实现。
10. 前后端合并部署可以通过前端打包 + Nginx 或 Spring Boot 静态资源统一发布实现。

---

## 17. 我给你的复习建议

第一遍：
- 先通读这份提纲，理解每个模块在项目里的位置

第二遍：
- 打开文中链接的代码位置，对着看 10 到 15 分钟

第三遍：
- 自己不看稿，口头回答下面 6 个问题

建议练这 6 个：
- 你的项目技术栈是什么？
- 登录验证码是怎么实现的？
- JWT 和 session 有什么区别？
- 修改头像和修改密码分别怎么做？
- 分页查询是怎么实现的？
- 你的管理员后台都实现了哪些 CRUD 功能？

如果这 6 个你能顺着讲下来，老师大概率问不倒你。
