# 最终交付与需求验收矩阵

交付日期：2026-11-01（Asia/Shanghai）。交付目标：[niu16zy/template-studio-xiaoying](https://github.com/niu16zy/template-studio-xiaoying)。源码包含 Next.js、Java/Spring Boot/MyBatis、MySQL 迁移与种子、原创图片、测试、锁文件、CI 和容器入口。

验收版本以仓库 `v1.0.0` 标签所指 commit 为准；收到任务的聊天同时提供完整提交链接。可用 `git rev-parse v1.0.0^{commit}` 核对。发布不包含公网部署，应用本地入口仍为 http://localhost:3000。

## 需求矩阵

| 要求 | 实现与验证证据 | 状态 / 限制 |
| --- | --- | --- |
| R-01 技术栈职责 | App Router/React/TS；Spring Boot/MyBatis/真实MySQL；请求同源转发 | 已实现且容器验证，Next不执行业务生成和持久化 |
| R-02 模板列表 | Java分类/模板接口，Flyway种子3分类6模板、1/2/3张素材；12张本地图片 | API及浏览器分类筛选通过 |
| R-03 上传与校验 | 每槽UploadQueue、预览/替换/移除/重试、数量/MIME/大小及后端内容/像素校验 | 单测、MySQL集成和浏览器上传通过；未提交草稿不跨刷新保存 |
| R-04 异步受理 | POST短事务返回202，单工作线程领取数据库队列 | 独立执行/锁存器集成测试、退出客户端和浏览器离页完成通过 |
| R-05 多步骤持久化 | ASSET_CHECK→GENERATE→FINALIZE，task/step短事务、只读查询、终态保护 | 顺序/阶段/版本/终态测试及接口运行通过 |
| R-06 失败与耗时 | 每任务冻结模拟配置，指定阶段失败，后续SKIPPED | 三阶段失败及参数严格性测试通过，README提供演示 |
| R-07 状态与异常 | 上传/提交/任务独立；queryError保留旧快照；受理后解除提交态 | 503/超时、业务失败区别、双击及不确定提交测试通过 |
| R-08 多任务生命周期 | 按身份和任务ID隔离，2秒独立查询、15秒超时，终态/离页清理 | 单测乱序/清理及浏览器混合结果/请求计数通过 |
| R-09 恢复与记录 | /history、时间/状态/结果/原因、游标分页、active合并、预览dialog | 页外活跃任务及分页单测、刷新/预览浏览器通过 |
| R-10 用户身份 | Java随机会话HttpOnly Cookie，公开演示A/B，查询/素材/结果归属校验 | A/B隔离与切换通过；不是私有账户或生产认证 |
| R-11 布局与可用性 | 暗色列表、左制作右历史、独立记录页、390px竖排、语义标签和焦点 | 桌面/窄屏截图、无横向溢出、Escape焦点通过；未做全面屏幕阅读器/所有浏览器审计 |
| R-12 CI与关键测试 | push/PR：lint/tsc/Vitest/build、Java verify、真实MySQL、Compose smoke及重建 | 本地前端22项/后端35项已通过；远程结果以本commit的Actions运行记录为准 |
| R-13 交付材料 | README、开发方案、API、worklog、来源、许可证、部署回滚、源码锁文件 | 已整理；精确提交由发布结果提供，不编造未执行验证 |
| R-14 容器交付 | 标准Compose三服务、Dockerfile、健康检查、命名卷、示例配置 | 一键启动/重建持久化已通过，另有独立测试Compose和可选Chromium验收 |

## 复现入口

克隆仓库后在根目录执行，宿主机仅需要 Git、Docker/Compose；应用依赖安装、构建、测试与运行都在容器中：

```sh
git checkout v1.0.0
docker compose config --quiet
docker compose -f compose.test.yaml run --build --rm frontend-check
docker compose -f compose.test.yaml run --build --rm backend-check
docker compose -f compose.test.yaml down
docker compose up --build -d --wait --wait-timeout 180
docker compose exec -T frontend node scripts/smoke.mjs
docker compose exec -T frontend node scripts/tasks-demo.mjs verify
docker compose up -d --wait --wait-timeout 180 --force-recreate backend
docker compose exec -T frontend node scripts/smoke.mjs
docker compose exec -T frontend node scripts/tasks-demo.mjs verify-existing
```

可选浏览器：`docker compose -f compose.yaml -f compose.browser.yaml run --build --rm browser-check`。首次构建需访问公共镜像、npm、Maven Central；运行时无付费/内部服务依赖。完整浏览器镜像较大，应预留镜像下载和展开空间。

正常停止使用 `docker compose down`，保留MySQL与素材卷。升级先等待active为空并备份匹配的数据库/文件卷，再切换通过检查的源码版本重建。回滚恢复旧镜像或源码；如数据库迁移不兼容，必须同时恢复匹配的数据库/文件备份。不要使用 `down -v` 作为普通停止或回滚。

## 范围和已知限制

单Java实例/单线程排队；不自动恢复执行，重启将遗留任务保存为中断失败；无幂等受理、自动重试、断点续跑、队列背压或孤立文件清理。查询异常不改任务终态；结果文件丢失也不把历史成功改成失败。提交超时提示核对记录，显式再次提交仍可能重复。演示用户任何人可选择，不适合放真实隐私素材。

前端已跟踪任务自动更新，其他标签或脚本新建任务通过刷新记录/重新进入页面发现。未做公网部署、生产认证、全面安全审计或跨浏览器兼容认证。2026-10-31应用生产依赖审计0项，开发工具链16项moderate仍披露于README，不通过跨版本强制修复隐藏取舍。

## 发布内容

源码包通过Git归档生成，与验收commit一致；保留隐藏工程配置、Wrapper、迁移、种子、样本、CI及锁文件。排除 `.git`、node_modules、.next、target、缓存、运行日志、真实.env、业务卷及本机原始参考附件。公开截图仅为本项目自有素材和演示身份。没有私有聊天、登录凭据或用户上传文件。

历史验证记录见 [acceptance](acceptance.md)，实际投入与AI工作流见 [worklog](worklog.md)，素材和第三方复用见 [assets-sources](assets-sources.md)。源码能证明的实现、已经执行的验证和未验证边界分别记录。
