# 阶段验收记录

最新日期：2026-11-01。五阶段实现和交付整理已完成。下面记录各轮实际结果，历史段落中的“未完成”保留当时状态，不代表最新结论。最终需求矩阵见[delivery](delivery.md)，远程CI以发布commit的Actions结果为准。

## 阶段五：源码快照与独立环境验收

发布前从Git暂存树归档到独立目录，不携带node_modules、.next、target、真实.env、日志、原始参考截图或既有业务数据。使用`studio-release-check`项目名、3001端口、全新MySQL/素材卷运行`docker compose up --build -d --wait`，三服务均healthy。Docker依赖层允许使用缓存；这是干净源码和全新业务数据验收，不声称清除了整个Docker镜像缓存。

基础smoke通过6模板/3分类/12张图、同源转发、Cookie、multipart上传和归属隔离；tasks-demo verify通过成功、三个指定阶段失败、多任务及步骤记录；重建Java后再次smoke与verify-existing通过，原素材与成功图片仍在，未完成任务明确中断失败。证据：`artifacts/stage5-clean-start.log`、`stage5-clean-smoke.log`、`stage5-clean-tasks.log`、`stage5-clean-recreate.log`、`stage5-clean-persistence.log`。这些是本机日志，不作为源码运行依赖或上传业务数据。

发布清单完成路径核查、常见令牌/私钥特征检查及Git whitespace检查；未发现真实凭据。内置数据库口令为明确标识的本地演示值。第三方许可证沿用docs/licenses并统一链接；公开截图为本项目界面。原始参考附件仅本地保留。CI保留push/PR全部必需检查，增加workflow_dispatch手动入口。

源码目标：[GitHub仓库](https://github.com/niu16zy/template-studio-xiaoying)。验收版本为v1.0.0标签解析的commit，发布聊天提供固定提交链接；远程执行结果应查看对应SHA的[Actions](https://github.com/niu16zy/template-studio-xiaoying/actions)，不能从本地通过推断远程已通过。最终源码包由该commit归档，不含构建缓存。

## 2026-11-01 阶段四：页面闭环与异步管理

新增：详情页真实 POST /api/tasks、按身份隔离的 TaskStore、独立任务轮询、/history、历史游标分页与 active 合并、结果预览、移动布局、恢复查询和不确定提交保护。未修改 Java 接口、数据库结构或业务实现。

| 验收点 | 结果 / 证据 |
| --- | --- |
| 前端完整质量检查 | 6 文件 22 测试全部通过，lint/typecheck/Next 生产构建通过；`artifacts/stage4-frontend-final.log` |
| 多任务独立、三种状态同时存在 | TaskStore 单测同时验证成功/失败/处理中，浏览器连续提交成功与失败任务后分别展示；`task-store.test.ts`、`task-history.test.tsx`、浏览器日志 |
| 查询错误与制作失败分离 | 单测覆盖503和15秒超时；真实浏览器拦截单任务查询返回503，原状态保留、手动恢复后继续完成 |
| 恢复/分页/乱序 | 单测覆盖历史第一页之外的active、游标编码及去重、重新挂载恢复、低version拒绝、终态不回退；浏览器刷新后恢复任务 |
| 轮询生命周期 | 每任务2秒间隔、无重叠请求；单测验证终态/空列表/卸载清理，浏览器统计请求确认终态与离页不再查询 |
| 身份切换 | 单测晚响应不污染新作用域；浏览器A创建→切B不可见→切回A恢复 |
| 提交状态与重复保护 | 未上传不可提交；双击仅一请求，202后清空草稿并解除提交态；不确定提交不自动重发、锁定草稿并引导核对历史；卸载后受理响应不写入旧作用域 |
| 真实页面制作流程 | Playwright 1.64.1 Chromium在容器内完成分类筛选→上传→202→阶段更新→结果预览；混合成功/失败、离页后台完成；无pageerror；`artifacts/stage4-browser-final.log` |
| 预览可访问性 | 命名dialog、Escape关闭、焦点回到触发按钮，单测及真实浏览器均通过；不等同完整辅助技术审计 |
| 桌面/窄屏 | 1440×1000桌面、390×844视口检查；390px制作/历史无水平溢出，截图人工查看布局与状态；`artifacts/browser/*.png` |
| 真实数据库/Java | 沿用阶段三已通过的35项测试；本轮使用真实Java/MySQL进行浏览器验收，没有改动后端，未重复宣称新一轮Java verify |
| CI | 原push/PR job自动包含新增Vitest测试及/history构建；浏览器入口为可选补充，未触发远程Actions |

浏览器首次运行未完成：D盘剩余约15MB，完整浏览器镜像展开触发Docker文件系统只读和引擎退出。清除本项目`.cache/npm`可再下载缓存，恢复引擎后只按已核实ID及依赖关系清理旧项目构建缓存约1.35GB；没有删除数据库/素材卷，没有修改系统安全设置。按时间批量清理缓存的请求被自动审批拒绝，未执行。精确清理后容器恢复、浏览器验收成功，日志 `artifacts/stage4-browser-retry.log`；本机磁盘仍紧张，后续大镜像构建需先准备空间。

本轮用隔离Linux Chromium完成页面验证，补齐之前Windows浏览器控制工具未能验证的缺口；没有绕过或继续该Windows工具的受限操作。完整键盘Tab遍历、所有浏览器/视口组合及屏幕阅读器未全面验收。初版方案的MSW改为现有fetch替身+fake timers，避免为单一接口测试引入额外应用依赖；另以真实浏览器网络拦截补充503验证。

已知范围：任务草稿不跨刷新保存；无自动重试/幂等提交；没有新任务推送，其他标签或脚本提交的记录通过刷新按钮/重新进入页面发现；已经跟踪的任务自动更新。演示用户不是生产登录。阶段五的最终需求矩阵、干净交付包及远程CI仍未完成。

## 2026-10-31 容器验收结果

环境：Windows + Docker Desktop 4.97.0 / Linux WSL2，Docker Engine 29.9.2、Compose 5.6.1；Node 22.23.0、Java 21.0.9、MySQL 8.4.7。所有项目依赖安装、编译、测试和运行均在容器内执行，未在宿主机运行项目脚本。

| 检查 | 实际结果与本地证据 |
| --- | --- |
| 两份 Compose 配置 | `docker compose config --quiet` 及测试配置检查退出 0 |
| 前端完整检查 | `frontend-check` 退出 0；lint、tsc、Vitest 4 文件 10 测试、Next 生产构建全部通过；`artifacts/frontend-check.log` |
| Java 编译、测试和打包 | Maven verify BUILD SUCCESS；24 单元测试 + 11 MySQL 集成测试，失败/错误/跳过均为 0；`artifacts/backend-check.log` 和 `artifacts/backend/{surefire-reports,failsafe-reports}` |
| 正式 Compose 启动 | `up --build -d --wait --wait-timeout 180` 退出 0，frontend/backend/mysql 均 healthy；`artifacts/compose-start.log` |
| 模板、分类、示例资源 | 基础 smoke 通过：6 模板、3 分类、12 张本地图片；`artifacts/runtime-smoke.log` |
| 会话、上传、归属 | 同源 Cookie 转发、multipart、准备检查、其他身份访问隔离通过；同上 |
| 三阶段与失败中断 | `tasks-demo.mjs verify` 退出 0，成功及 ASSET_CHECK/GENERATE/FINALIZE 三种失败通过，失败后步骤 SKIPPED，成功结果可读取；`artifacts/task-runtime.log` |
| 多任务独立 | verify 的三个连续成功任务及 multi 的成功/GENERATE失败/成功混合任务通过；`artifacts/task-multi-mixed.log` |
| 不查询也能完成 | submit-only 受理后客户端退出，间隔后单次 inspect 返回 SUCCEEDED，三个步骤时间及状态完整；`artifacts/task-submit-only.log`、`artifacts/task-no-query-result.log` |
| 重建后持久化 | `--force-recreate backend` 后再次 smoke 和 verify-existing 均退出 0；已有上传/成功图片仍可访问，未完成任务为明确中断失败；`artifacts/runtime-smoke-after-recreate.log`、`artifacts/task-after-recreate.log` |
| 初始化重复性 | 新库 Flyway V1/V2 成功，CatalogIT 重复 migrate 及正式 Java 重建无重复种子/迁移错误 |
| 依赖审计 | 生产依赖 0 项；完整依赖 16 项 moderate，0 high/critical；`artifacts/npm-audit-production.json`、`artifacts/npm-audit-current.log` |
| 远程 GitHub Actions | 未触发；本地运行与流水线同一检查/验收入口，不声称远程 CI 已通过 |
| 浏览器视觉、交互、键盘、窄屏 | 未验证；浏览器控制工具无法可靠识别当前网址并停止操作，本轮未继续自动操作浏览器 |

运行日志是本机可复核的验证证据，位于被 Git 忽略的 artifacts，不作为源码必备依赖或提交用户数据。Java 故障单测中的异常日志是注入场景，测试报告确认零失败。当前应用入口为 http://localhost:3000。

### 本轮修复与边界

- 修复 session-provider.test.tsx 最外层测试闭合语法；将会话 effect 清理中的请求失效操作提取为稳定回调，消除 exhaustive-deps 告警，保留旧响应防覆盖逻辑。
- Vitest 4.0.18 升至 4.1.12，处理原 critical 审计告警。初次锁更新遗漏 Linux 可选依赖；在干净容器用 npm 11.6.2 生成锁文件，再用镜像自带 npm 10.9.8 执行 npm ci 验证，通过后才写回。正式镜像仍使用自带 npm。
- 移除 Dockerfile 中 `dependency:go-offline` 的全插件预取，改为实际 verify/package 按需解析，避免下载与当前构建无关的依赖；为后续构建添加 Maven 缓存。首次 Maven 镜像构建遇网络超时，缓存重试后成功，未更换依赖来源或跳过要求的测试。
- 首次 Docker Hub 的 BuildKit 认证连接超时，通过 Docker daemon 预拉取同一组基础镜像后构建成功；不需要把本机代理配置写入项目。
- Flyway 对 MySQL 8.4 打印支持范围提示；本轮实际验证初始化、重复迁移、所有集成测试成功。完整 npm 审计的 16 项 moderate 属开发工具依赖，未做跨大版本强制升级。
- 当前 UI 仍是阶段二上传/检查素材页面，不能在页面提交制作任务或查看历史；任务接口已通过验收。R-07/08、R-09 前端恢复与历史、相关前端多任务测试和最终浏览器验收仍归阶段四/五。

## 历史记录：2026-10-30

## 已执行

| 检查 | 实际结果 |
| --- | --- |
| 用户四张截图 | 已查看并保存到 docs/reference，更新布局约定 |
| npm 官方依赖元数据 | 成功解析并生成 package-lock.json v3，546个包条目（含根条目） |
| 直接依赖与锁文件 | 已核对精确版本；未生成 frontend/node_modules |
| Maven pom.xml | XML 解析成功；不代表 Java 编译通过 |
| 两份 Compose、CI、application.yml | YAML 静态解析成功；不是 docker compose config 的替代 |
| 固定 Docker 镜像标签 | 官方 Docker Hub 查询确认存在；未拉取/运行 |
| 本地示例图片 | 12个文件，JPEG/PNG，720×1280，全部可解码且小于5MiB |
| 种子引用与上传样本 | 文件引用存在，3个样本已附带；封面进行了视觉抽查 |

锁文件生成只读取元数据，没有安装项目依赖或运行 npm lifecycle。该操作使用环境原有 Node 20，产生项目要求 Node22 的 engine 提示；实际运行入口固定为 Node22 容器，尚未验证。未在宿主机运行前端/Java构建或应用测试。

## 待容器执行

| 阶段一标准 | 已提供内容 | 当前状态 |
| --- | --- | --- |
| 全新数据库初始化 | Flyway V1/V2，MySQL健康检查 | 未验证 |
| 重复启动不重复插入 | CatalogIT + CI重建冒烟 | 测试已写，未执行 |
| Java连接真实MySQL | MyBatis mapper、Spring集成测试、专用测试库 | 未验证 |
| 前端不依赖后端完成构建 | 客户端请求；独立frontend-check容器 | 未验证 |
| lint / typecheck / test / build | npm check、Docker checks target | 未执行 |
| Java tests / verify / package | Wrapper、Surefire/Failsafe、checks target | 未执行 |
| 同源转发与资源 | CI smoke 检查Java接口与12张图 | 未执行 |
| 实际页面视觉/键盘/窄屏 | CSS、语义结构、状态组件 | 未运行浏览器验收 |

原因：当前 Windows 环境无 docker/podman 命令、无常见 Docker Desktop 安装，wsl --list 未提供可用分发版。未擅自安装 Docker、开启系统虚拟化或重启电脑。

完成上述容器检查后，才能将阶段一标记为“验收通过”。源码中可审阅的实现与运行效果分别记录。

## 未完成的整体题目要求

截至阶段二：已实现R-03上传/准备校验、R-10会话和素材隔离、R-11详情布局；当时R-04～R-09任务闭环尚未实现。阶段三新增的后台实现及仍待完成的前端范围见末节。

## 阶段二：实现与验证分开记录

本轮再次检查docker/podman，仍无可执行命令。未在宿主机安装依赖、编译、运行项目测试或应用；依赖未变化，无需重新生成锁文件。静态核对覆盖YAML/XML/JSON解析、源文件/路由引用、原迁移保持不变及样本存在性，不替代编译和运行检查。

| 行为 | 已提供证据 | 运行状态 |
| --- | --- | --- |
| 会话恢复、轮换、过期与HttpOnly Cookie | SessionService/Controller、AssetSessionIT | 未执行 |
| 上传真实落盘，失败清理文件 | AssetService/Storage、AssetRollbackTest | 未执行 |
| MIME内容、大小、像素限制 | ImageValidator、ImageValidatorTest、AssetSessionIT | 未执行 |
| 精确数量、重复ID、模板和归属检查 | validateSelection、AssetSessionIT | 未执行 |
| 其他身份不可读取素材 | 带userId条件的Mapper、AssetSessionIT | 未执行 |
| 上传替换/移除防旧响应覆盖 | 每槽版本号和AbortController、upload-queue.test | 未执行 |
| 切换用户取消上传、恢复会话 | 带身份epoch的组件key、session-provider.test | 未执行 |
| Cookie/multipart通过Next代理 | 扩展容器smoke脚本 | 未执行 |
| 重建Java后素材记录和文件仍在 | CI第二次smoke读取上次素材ID；不存Cookie | 未执行 |
| 详情404、上传错误、键盘和小屏操作 | TemplateDetail、UploadWorkspace、dialog及CSS | 未运行浏览器验收 |

容器可用后按README运行两组checks和Compose smoke。人工补查：身份A上传→替换/移除→准备检查→保存previewUrl→刷新后同身份访问→切B访问404→切回A可读。上传中切换身份，观察旧草稿立即消失；人为限速后替换图片，观察旧响应不覆盖新槽位。

阶段二代码已交付，但不能标记“测试通过”或“容器验收通过”；完整项目仍有后续阶段。

## 阶段三：任务后台

实现范围：R-04事务受理与后台领取、R-05独立三步骤和持久化状态、R-06指定阶段失败及耗时、R-09个人历史/active/结果API、R-10任务与结果归属、R-12后端核心测试。R-07/08及R-09的前端自动跟踪/刷新体验仍在阶段四。

| 验收点 | 对应代码/测试 | 运行状态 |
| --- | --- | --- |
| 返回202不等待完成 | TaskSubmissionService；TaskWorkflowIT锁存器测试 | 未执行 |
| 无查询也能继续执行 | 独立TaskWorker；submit-only容器脚本 | 未执行 |
| 查询不推进且快照一致 | 只读TaskQueryService；重复GET/version不变测试 | 未执行 |
| 三阶段顺序、边界、时间 | TaskTransitions/TaskPipeline；顺序单测与MySQL步骤断言 | 未执行 |
| 每阶段注入失败停止后续 | 参数化单测及MySQL FAILED/SKIPPED断言 | 未执行 |
| 多任务配置与结果独立 | 任务级simulation快照；多任务集成测试 | 未执行 |
| 历史分页、个人隔离、终态不回退 | TaskWorkflowIT，结果权限及版本断言 | 未执行 |
| 重启不续跑、保留成功结果 | interruptPreviousRun测试；CI重建前后checkpoint | 未执行 |
| 真正的IO/数据库故障 | IO失败/线程中断/落库失败/worker停止单测 | 未执行 |
| 参数严格性 | SimulationSettingsTest及受理失败不留记录测试 | 未执行 |

本次新增任务代码、测试及演示脚本，无新依赖、无V1/V2迁移改写。测试profile关闭自动worker，避免不同集成测试上下文的后台线程互相干扰；受理/执行分离测试显式在另一个线程触发worker并阻塞阶段边界。正式应用默认启用worker。

CI顺序扩展为：前后端checks → Compose启动 → 基础smoke → tasks-demo verify（成功、三阶段失败、多任务）→ 重建Java → 验证原上传和成功结果仍在、未完成任务为中断失败。脚本尚未执行，不提供虚构的通过日志。当前环境依然没有docker/podman可用命令，未在宿主机执行构建/应用测试。

本轮静态检查已通过：配置YAML/XML/JSON、依赖锁一致性、Java包路径、前端本地导入、MyBatis动态SQL的XML结构、查询代码不依赖写状态方法、测试禁用自动worker及CI脚本路径。首次检查因工具脚本使用系统GBK读取UTF-8源码失败，显式指定UTF-8后通过；没有据此改动应用编码。这些检查不验证SQL在真实MySQL的运行结果，也不等同Java编译通过。

## Docker环境配置进展

2026-10-30已完成Docker Desktop 4.97.0、WSL 2.7.14.0安装。Docker CLI 29.9.2和Compose v5.6.1可调用；运行与测试两份Compose文件均通过真实docker compose config检查（退出0）。

WSL与VirtualMachinePlatform首次启用均要求重启，当前尚未重启；Docker引擎未运行。因此应用编译、自动化测试、数据库启动和HTTP运行验收仍为未验证。重启后按docs/docker-environment.md中的顺序继续，不在宿主机绕过容器执行项目构建。
