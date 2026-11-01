# 拾影 · 素材工作室

基于 Next.js App Router + Java/Spring Boot + MyBatis + MySQL 的素材制作项目。完整设计见 [开发文档](./开发文档.md)。

**最终交付：五阶段开发与交付整理完成。** 模板详情支持上传并提交 Java 后台任务；右侧制作记录和独立 `/history` 页面显示多个任务、真实阶段、失败原因和成功图片。支持刷新恢复、历史分页、查询中断后手动恢复、身份切换隔离及结果大图预览。

[![Container checks](https://github.com/niu16zy/template-studio-xiaoying/actions/workflows/ci.yml/badge.svg)](https://github.com/niu16zy/template-studio-xiaoying/actions/workflows/ci.yml)

源码仓库：[niu16zy/template-studio-xiaoying](https://github.com/niu16zy/template-studio-xiaoying)。验收版本：`v1.0.0` 标签指向的提交，使用 `git rev-parse v1.0.0^{commit}` 核对；发布回复提供完整 commit 链接。需求逐项状态、复现与范围见 [交付验收矩阵](docs/delivery.md)。这次交付为源码发布，不包含公网应用部署。

**本地验证（2026-11-01）：前端 lint、类型检查、22 项测试和生产构建通过，真实 Chromium 页面流程及 390px 窄屏验收通过；Java verify 的24项单测和11项真实MySQL集成测试通过。** 阶段五从Git源码快照构建独立Compose项目，使用全新数据库/素材卷，验证初始化、上传、三阶段任务、多任务及Java重建后持久化。详细证据见[验收记录](docs/acceptance.md)。远程CI的实际状态以本commit对应的[Actions运行记录](https://github.com/niu16zy/template-studio-xiaoying/actions)为准，不用本地结果代替远程结论；环境记录见[Docker配置记录](docs/docker-environment.md)。

## 技术版本

| 部分 | 固定版本 |
| --- | --- |
| Node.js | 22.23.0，Debian bookworm slim |
| Next.js / React / React DOM | 16.4.3 / 19.3.1 / 19.3.1 |
| TypeScript / ESLint | 5.9.3 / 9.39.1 |
| Vitest / Testing Library React / jsdom | 4.1.12 / 16.3.3 / 26.1.0 |
| 可选浏览器验收 Playwright | 1.64.1，独立依赖与 noble 镜像 |
| Java | Eclipse Temurin 21.0.9+10，Ubuntu jammy |
| Spring Boot | 3.5.16 |
| MyBatis Spring Boot Starter | 3.0.5 |
| Maven / Maven Wrapper | 3.9.11 / 3.3.4（官方 only-script） |
| MySQL | 8.4.7 |
| JDBC / Flyway / JUnit / Mockito | 由固定 Spring Boot parent 管理，不额外覆盖 |

前端全部直接依赖精确锁定，完整间接依赖与 integrity 见 package-lock.json。Java 使用固定 parent/BOM 和 Wrapper；基础镜像使用补丁标签，不使用 latest，已实际拉取并构建运行。标签仍可能由上游重发，正式交付阶段可进一步固定 digest。

兼容依据：[Next.js 系统要求](https://nextjs.org/docs/app/getting-started/installation)、[Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis Starter 兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。依赖可解析不等于实际构建已通过。

## 目录

- frontend：App Router 页面、模板数据请求、基础契约、测试和 Dockerfile。
- backend：Java API、MyBatis、Flyway SQL、示例图片、测试和 Dockerfile。
- samples/uploads：3 个真实 JPEG/PNG 文件，可供后续上传演示。
- docs/api.md：当前接口和后续接口契约，明确区分是否实现。
- docs/ui-reference.md：截图参考和界面约定。
- docs/acceptance.md：阶段验收清单及真实验证状态。
- docs/assets-sources.md：素材、脚手架和复用来源。
- docs/worklog.md：实际投入记录、范围与取舍。
- docs/delivery.md：R-01～R-14最终矩阵、验收版本与复现入口。
- compose.yaml / compose.test.yaml：运行环境和独立测试环境。
- browser-check / compose.browser.yaml：可选真实 Chromium 容器验收、独立锁文件，不进入应用镜像。
- .github/workflows/ci.yml：push / pull_request 检查。

## 容器启动

需要 Docker Engine/Desktop（Linux containers）以及支持 `run --build`、`up --wait` 的 Docker Compose v2（建议 2.24+）。不需要宿主机安装 Node、Java、Maven 或 MySQL。以下命令应在项目根目录运行；依赖安装、项目构建、测试和应用均发生在容器中。

```sh
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
```

访问 [本地页面](http://localhost:3000)。也可按题目使用 `docker compose up --build` 查看前台日志。首次构建需要访问 npm、Maven Central 和镜像仓库；运行时无需第三方图片或 AI 服务。

默认内置的数据库口令只用于本地演示，首次启动无需创建 .env。自定义时复制 .env.example 为 .env 并修改；不要提交真实 .env。

```sh
docker compose logs backend
docker compose down
```

down 保留数据库和文件命名卷；不要在普通停止/回滚中添加 -v。3306 和 8080 不映射到宿主机，浏览器只访问前端 3000。

### 连接与配置

| 配置 | 默认值 / 用途 |
| --- | --- |
| FRONTEND_PORT | 3000，宿主机浏览器入口 |
| MYSQL_DATABASE | material_app |
| MYSQL_USER | material_app，应用数据库用户 |
| MYSQL_PASSWORD | local_demo_password，仅本地演示 |
| MYSQL_ROOT_PASSWORD | local_demo_root_password，仅初始化使用 |
| JAVA_API_BASE_URL | http://backend:8080，Next 构建时生成同源转发规则 |
| SPRING_DATASOURCE_URL | Compose 组装为 jdbc:mysql://mysql:3306/material_app，UTC 连接 |
| SPRING_DATASOURCE_USERNAME/PASSWORD | Compose 从 MYSQL_USER/PASSWORD 注入 Java |

Java 数据源可直接通过对应 Spring 环境变量覆盖。JAVA_API_BASE_URL 在当前 Next rewrite 方案中是**构建时配置**，修改后需重新构建前端；只改变运行容器的环境变量无效。不会将内部服务名作为浏览器地址。

MySQL 的初始化账号环境变量只在首次创建数据目录时生效，修改 .env 不会自动修改已有库的账号密码；已有卷需要显式数据库账号迁移。

请求：浏览器 /api/* → Next 同源转发 → backend:8080 → MyBatis → mysql:3306。前端构建不访问 Java；页面挂载后从 Java 获取模板。Java 接口 no-store；图片也由 Java 从打包资源读取。

上传写入 /app/data/uploads 命名卷，MySQL 保存素材元数据。文件使用随机 UUID 命名，先写临时文件再移动；数据库写入/提交失败会尝试删除本次文件。图片以真实内容解码检查，拒绝 MIME 伪装、空文件、超过5MiB、边长超过8192或总像素超过2000万的文件。崩溃时文件与数据库无法形成跨资源原子事务，可能留下孤立文件，尚未实现自动清理。

身份/文件配置：DEMO_MODE=true（演示身份入口）、SESSION_TTL_DAYS=7（1～30天）、COOKIE_SECURE=false（本地HTTP；HTTPS设true）、STORAGE_ROOT=/app/data（Compose固定卷内目录）。关闭DEMO_MODE禁止获取身份列表和创建新演示会话，已存在会话仍按有效期工作；当前未提供其他登录方式。

阶段三配置：SIMULATION_ENABLED=true允许任务级模拟覆盖；SIM_CHECK_MS=1500、SIM_GEN_MS=6000、SIM_FINALIZE_MS=1500是默认耗时，各限0～30000ms；QUEUE_POLL_MS=500（50～10000）是后台领取队列的间隔。改.env后重建后端容器生效，已受理任务继续使用数据库中的配置快照。关闭SIMULATION_ENABLED仍按默认耗时成功模拟，显式传入覆盖对象返回400。内部TASK_WORKER_ENABLED=false仅用于控制测试，普通Compose不设置该值，不能用它替代正常运行。

## 数据库初始化与素材

Java 启动时自动执行 Flyway：

1. V1__schema.sql：8 张表、外键、索引、状态 CHECK 约束。
2. V2__demo_seed.sql：2 个演示身份、3 个分类、6 个模板。
3. Flyway 记录版本和校验和，再次启动不重新插入相同种子；已应用迁移不得就地修改，应追加 V3 等迁移。

模板覆盖 1、2、3 张图片要求，均允许 JPEG/PNG，单张上限 5 MiB。数据库只保存约束与本地资源文件名；不是前端硬编码列表。

6 张封面 JPEG + 6 张模拟结果 PNG 随 JAR 打包；均为本项目创作的 720×1280 几何插画。结果图显式带有 SIMULATED RESULT 字样。3 个上传样本在 samples/uploads。不会把题目截图中的人物、宠物或品牌剪裁为业务素材。

## 已实现接口

| 接口 | 行为 |
| --- | --- |
| GET /api/categories | 读取分类 |
| GET /api/templates?categoryId=travel | 分类筛选，不传返回全部；未知分类返回空数组 |
| GET /api/templates/{id} | 模板详情 JSON，不存在返回 404 |
| GET /api/demo-assets/{name} | 仅允许种子引用的 JPEG/PNG 文件名 |
| GET /actuator/health/readiness | Java 内部就绪检查，包括数据库 |
| GET /api/demo/users | 演示身份A/B列表 |
| POST /api/session | 切换身份并签发HttpOnly Cookie，旧会话撤销 |
| GET /api/session | 根据Cookie恢复当前身份，未登录/过期返回401 |
| POST /api/assets | multipart上传、检查真实图片并落盘，返回201和素材ID |
| GET /api/assets/{id} | 当前身份的素材元数据 |
| GET /api/assets/{id}/content | 校验所有者后返回图片；非本人返回404 |
| POST /api/assets/validate | 校验数量、去重、模板绑定、归属、要求和文件可读性；不创建任务 |
| POST /api/tasks | 必要校验及事务提交后返回202、任务快照和Location |
| GET /api/tasks/{id} | 当前身份的任务、步骤、失败或结果快照 |
| GET /api/tasks?cursor=...&limit=20 | 按创建时间和ID倒序分页的个人历史 |
| GET /api/tasks/active | 当前身份全部非终态任务，不受历史分页影响 |
| GET /api/tasks/{id}/result | 成功结果PNG；校验所有者，未成功409，文件缺失404 |

详情字段及错误格式见 [API 文档](docs/api.md)。列表卡片可进入详情页。详情按附件采用左侧模板/上传、右侧预置结果示例布局；示例明确不是当前用户的任务结果。所有修改接口要求 X-Studio-Request: 1；上传和准备检查还要求 X-Studio-User 与服务端会话身份一致，防止跨标签切换时旧草稿归到新身份。此请求头不替代Cookie认证。前端自动携带，手工调用需显式设置。未开放跨域CORS。

## 阶段二演示

1. 启动后在右上角选择“演示用户 A”；进入任意模板。
2. 从 samples/uploads 选择对应数量图片，观察每个位置的预览、上传中、已上传；可替换、移除和失败重试。
3. 全部READY后点击“检查素材准备”，由Java再次校验；按钮不会启动后台制作。检查中锁定草稿，避免重复请求。
4. 查看上传响应中的id/previewUrl；同一身份刷新后再次访问这个地址，服务端素材仍可读取。刷新只清空浏览器草稿，不删除文件。
5. 切换B后用同一素材地址访问应返回404；切回A后恢复可读。所有者来自Java会话，客户端不能指定。
6. 上传中立即替换或移除，晚到响应不会恢复旧图片；切换身份立即清空草稿并取消请求。
7. 选择错误类型/大文件观察客户端校验；篡改Content-Type、引用其他身份素材等应由后端独立拒绝。
8. 网络中断可看到上传失败并重试；模板不存在显示明确404界面；身份请求失败提供重新确认入口。

会话有效期默认7天，服务端存随机令牌的SHA-256摘要；Cookie使用HttpOnly、SameSite=Lax、Path=/，刷新由Java恢复。浏览器标签共享Cookie，窗口重新激活或收到其他标签切换通知时重新核对身份；同一身份核对不会清空草稿，身份变化则清空。切换请求未确认时重新查询会话，不直接假定成功。

A/B是任何访问者均可选择的公开演示身份，不能作为生产私人账户使用。删除/移除上传位只移除草稿引用，已落盘素材保留；未引用文件和过期会话尚无定时清理。

## 测试和 CI

先验证配置，再分别执行前后端容器检查：

```sh
docker compose config --quiet
docker compose -f compose.test.yaml config --quiet
docker compose -f compose.test.yaml run --build --rm frontend-check
docker compose -f compose.test.yaml run --build --rm backend-check
docker compose -f compose.test.yaml down
```

前端检查容器运行：npm ci（镜像阶段）→ ESLint → tsc --noEmit → Vitest → next build。后端运行 Maven verify，包含 Surefire 单测、Failsafe 真实 MySQL 集成测试和 JAR 构建，不使用 H2。

当前基础测试覆盖API请求、模板/MyBatis、迁移、资源及会话/上传。后端包括TaskPipelineTest（顺序、三阶段注入失败、实际IO错误、线程中断、失败落库异常）、SimulationSettingsTest（严格参数与快照）、TaskWorkerTest（数据库故障停止受理）、TaskWorkflowIT（真实MySQL受理/执行分离、读查询不推进、多任务、阶段记录、历史隔离与分页、终态及重启处理）。阶段四前端测试覆盖独立任务状态、503/超时保留原状态、恢复查询、分页外活跃任务、乱序版本、旧身份响应丢弃、终态停查、卸载清理、受理后解除提交、不确定提交不重发以及预览焦点。这些测试均已在容器中通过。

compose.test.yaml 使用独立服务名、数据库账号与 tmpfs 测试库；不读取或清空运行环境命名卷。为了重新使用全新测试库，可停止测试服务后重新运行。Maven 下载使用测试命名缓存卷，生产镜像构建使用 BuildKit 缓存；缓存不承载业务数据，不影响从干净环境构建。

CI 在push/pull_request时运行两个独立检查job；通过后运行生产容器冒烟，检查模板/上传/身份，再执行成功、三种失败和多任务场景。重建Java容器后检查素材及既有成功结果仍可读取，未完成任务变为中断失败。临时检查点只保存资源ID，不存Cookie。项目脚本都在容器内执行，失败日志/Java测试报告上传为CI artifact。运行镜像构建中的package跳过测试只用于组装，不能替代verify job。

启动后的基础冒烟入口：

```sh
docker compose exec -T frontend node scripts/smoke.mjs
```

## 阶段三执行方式与演示

提交请求重新校验素材和模拟参数，在一个短事务内创建QUEUED任务、素材关联和三条PENDING步骤，提交成功后返回202；不调用生成器。Java单工作线程按创建顺序从MySQL领取已提交任务。领取使用FOR UPDATE SKIP LOCKED和条件更新，但整个应用仍只支持**单Java实例**，不支持多副本部署。

整体状态为QUEUED/RUNNING/SUCCEEDED/FAILED。领取后短暂为RUNNING/PENDING，随后顺序进入ASSET_CHECK → GENERATE → FINALIZE。每阶段开始、结束分别短事务落库，sleep与文件操作不占用这些事务。素材检查再次验证文件；模拟生成复制本地预置PNG到work目录；结果整理验证并移动到results目录，同一事务记录最终步骤成功与任务成功。每次状态变化递增version。历史和任务查询在只读一致性事务中读取，不领取、推进或修改任务。

任一步失败保留failureStage与可理解原因，当前步骤FAILED，后续SKIPPED；终态不回退。结果地址只在成功时返回，接口继续校验会话归属。数据库故障若连失败记录都无法写入，工作线程停止，健康检查转为不可用并拒绝新受理；修复数据库后重启服务，不承诺自动重试或已保存失败。

下面的演示脚本在前端容器里模拟HTTP客户端，Java独立执行。脚本创建/切换独立演示会话，不使用浏览器Cookie；不会改变浏览器当前选择。已实际运行 verify（涵盖成功和三个失败阶段）、multi、submit-only/inspect 和 verify-existing。

```sh
docker compose exec -T frontend node scripts/tasks-demo.mjs success
docker compose exec -T frontend node scripts/tasks-demo.mjs fail-check
docker compose exec -T frontend node scripts/tasks-demo.mjs fail-generate
docker compose exec -T frontend node scripts/tasks-demo.mjs fail-finalize
docker compose exec -T frontend node scripts/tasks-demo.mjs multi
```

脚本自动上传样本、受理任务并输出阶段；失败模式检查FAILED及后续SKIPPED。普通脚本耗时为300/1200/300ms，便于验证；后台默认仍为1500/6000/1500ms。multi连续创建三个任务，其中一个指定GENERATE失败，检查结果互不串扰。

证明关闭客户端不终止后台任务：

```sh
docker compose exec -T frontend node scripts/tasks-demo.mjs submit-only
# 上一条返回任务ID后立即退出，不再查询。约20秒后（有其他排队任务时更久），替换下面的TASK_ID：
docker compose exec -T frontend node scripts/tasks-demo.mjs inspect TASK_ID
```

submit-only使用1000/15000/1000ms；inspect重新创建A身份会话，从Java读取原任务。刷新详情或历史页后会读取Java记录并继续跟踪未结束任务。详细请求JSON和字段见docs/api.md。

服务启动先把上次遗留QUEUED/RUNNING任务标记FAILED/SERVICE_RESTARTED，再开启受理与工作线程。正常停止时先停止受理，执行中的线程被中断则保存WORKER_INTERRUPTED；来不及保存时由下一次启动收敛。既有成功/失败任务不改动。**这不是续跑或自动重试**。部署应提前停止新提交、等active为空后重启；当前未提供管理员暂停接口，可由入口维护窗口控制。

边界：无受理幂等，提交超时不要自动重发，应先查询历史核对；单实例，无分布式执行；文件与数据库无法跨资源原子提交，极端崩溃可能留临时/孤立文件；结果文件丢失返回请求404，但不篡改已成功历史。等待队列无背压，active接口暂不分页，面向笔试演示规模。原种子模板不支持编辑，因此生成器读取预置结果来源，模拟耗时/失败参数则按每任务冻结。

完整源码和交付材料已整理，原始参考附件、日志、运行数据及依赖缓存不进入仓库。未实施题外的生产认证、公网部署、真实AI、自动重试或重启续跑；全面辅助技术及跨浏览器认证未执行。远程CI结果与具体commit关联，见上方运行入口。

## 阶段四页面演示与查询行为

1. 打开首页，选择右上角“演示用户 A”，进入“午后光影”；上传 `samples/uploads/sample-01.jpg`，待已上传后点击“开始制作”。
2. 接口返回 202 后右侧立即显示任务，按钮解除提交状态并清空上传位，可上传下一张图片创建第二个任务。后台按单线程排队，页面显示真实的等待/素材检查/模拟生成/结果整理状态。
3. 可离开详情继续浏览，或关闭页面；重新进入“我的记录”会从 Java 恢复历史与全部未完成任务。成功卡片点击“查看大图”，Escape 关闭并恢复按钮焦点。
4. 用上一节 `fail-generate` 等命令构造 A 身份的失败任务，在页面点击“刷新记录”查看明确的失败阶段与原因。模拟耗时和失败继续由 Java 配置/接口控制，正式页面不增加开发控制项。
5. A 提交后切换 B，页面立即卸载 A 的草稿与查询，B 只读取自己的记录；切回 A 会重新恢复 A 的记录。A/B 为公开演示身份，任何使用者都能选择，不能当私人账户。
6. 网络工具阻断某条 `/api/tasks/{id}` 查询，卡片保留上次状态，显示“状态更新中断”，点击“恢复查询”重试；实际后台 FAILED 仍按制作失败独立显示。不要通过关闭 Java 模拟单次网络波动，重启 Java 有独立的任务中断语义。

前端每个页面会话作用域持有独立 TaskStore，通过 Context + useSyncExternalStore 订阅。按任务 ID 合并服务端快照，只接受不低于已有 version 的记录，终态不能回退。详情与历史页读取 `GET /api/tasks?limit=12` 及 `GET /api/tasks/active`；后者确保历史第一页之外的未结束任务也会被跟踪。加载更多使用后端不透明游标并去重；刷新保留已有卡片，重新合并首页和 active。

每个未完成任务在上次请求结束后等待 2 秒再查询，15 秒超时；没有未完成任务时不建立周期查询。查询异常暂停该任务的自动跟踪，保留数据并提供手动恢复；其他任务继续。离页/切换身份/重新核对会话时取消请求并清除计时器，响应通过请求对象及版本检查拒绝过期写入。回到同一身份的页面重新读取记录，不依赖 localStorage 保存任务。

提交请求有同步互斥与 15 秒超时。网络中断、超时或服务端 5xx 时不确定是否已受理，**不会自动重发**；页面尝试刷新历史并锁定再次提交，用户核对后可显式解除，仍可能重复，后端未实现幂等。草稿不做刷新恢复；已受理任务与结果存储在后端。

可选真实浏览器验收（只访问本地测试应用，产生演示任务，截图不包含 Cookie）：

```sh
docker compose up --build -d --wait --wait-timeout 180
docker compose -f compose.yaml -f compose.browser.yaml run --build --rm browser-check
```

脚本使用独立 Chromium 检查分类、页面上传与提交、多任务成功/失败、预览与 Escape 焦点、查询中断恢复、刷新恢复、离页停查、身份隔离和 390px 窄屏；截图写入 `artifacts/browser`。失败时保留 failure.png 并返回非零。浏览器检查是补充验收，不强制进入原本的 push/PR 必需检查；其镜像较大，首次拉取需要额外时间。版本与使用方式参照 [Playwright 官方容器文档](https://playwright.dev/docs/docker)。

2026-10-31 依赖审计：生产依赖 0 项已知漏洞；完整依赖仍有 16 项 moderate，集中在 ESLint 工具链及其间接依赖，high/critical 均为 0。已将存在 critical 告警的 Vitest 4.0.18 升至 4.1.12 并复测；未执行会改动主要版本的强制修复，开发依赖剩余告警留待工具链升级处理。Flyway 启动时提示其 MySQL 测试支持范围较旧，本项目 MySQL 8.4.7 上的首次迁移、重复迁移及集成测试已实际通过，仍需在未来升级数据库时复验。

## 部署与回滚基础

当前可审阅阶段一至四实现，不是完整生产系统。部署时固定源码版本与镜像，备份MySQL/文件卷，配置环境后启动，验证就绪、模板、身份、上传、任务及归属隔离。

回滚先停止新请求，保留卷，切换上一个通过验证的源码或镜像后重启。若迁移与旧版本不兼容，需要维护窗口内恢复匹配的数据库和文件备份；不能仅回滚镜像。不要删除数据卷。本次为源码仓库发布，未进行公网部署。

## 实际投入、AI 与来源

实际投入记录见 [worklog](docs/worklog.md)，与开发文档的44～60h全项目预算分开。阶段一至三容器验收约42.4分钟。阶段四可核实区间为2026-10-31 23:09:52～23:20:33及2026-11-01 11:59:44～12:14:23，合计约25.3分钟，含编码、构建等待、磁盘故障处理、浏览器验收及文档整理，不含跨夜用户暂停间隔和此后收尾，不等同人工纯编码工时。

此前阶段一约27.8分钟、阶段二18.9分钟、阶段三66分钟，另有Docker环境准备20.7分钟。阶段五截至首次发布前已核实区间为2026-11-01 13:03:15～13:12:17，约9分钟，包含清单审阅、独立源码构建/运行验证和整理；后续GitHub上传、CI及发布等待不混算为上述本地开发时间。方案编制未留完整计时，不编造人工有效工时。

本项目使用 AI 辅助拆分任务、生成工程与迁移、核对依赖、执行容器验证和补齐文档。上下文为题目、开发文档、用户截图、当前文件与真实日志；修改以实际文件和工具结果为依据。先前实际例子：lint发现会话测试语法及清理依赖告警，修正后复跑完整检查；升级Vitest后在干净容器修复锁文件。阶段四先实现任务查询作用域和版本合并，再用假计时器/延迟响应验证清理及错误隔离；浏览器拦截一次503，验证卡片保留原状态且可恢复，最后检查桌面/窄屏截图。根据检查完善不确定提交的草稿锁定及长历史列表下左侧面板布局，修改后再次通过完整前端检查与浏览器验收。

工程文件为本次编写，未整体复制第三方应用或脚手架；Maven Wrapper 使用 Apache 官方 3.3.4 脚本。依赖和素材来源见 [sources](docs/assets-sources.md)。用户截图仅作内部布局参考，未用于应用图片。
