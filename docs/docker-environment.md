# 本机 Docker 环境配置与验收续接

最新状态（2026-11-01）：阶段四前端和可选浏览器验收均通过，正式三服务健康运行于 http://localhost:3000；详细结果见 [验收记录](acceptance.md)。以下安装过程保留2026-10-30的历史状态。

本轮完整Playwright镜像展开时D盘剩余约15MB，Docker引擎因文件系统只读退出。先清除本项目可再生成的`.cache/npm`，再启动引擎；核实BuildKit缓存ID和父子引用后，仅删除六个旧项目构建缓存项，释放容器内约1.35GB，未删除运行镜像或MySQL/素材卷。精确清理后正式服务恢复并通过浏览器验收。按时间批量清理全部旧缓存的请求被自动审批拒绝，未执行。当前D盘宿主可用空间仍约200MB；虚拟磁盘内部释放空间不一定立即缩小宿主VHDX，后续大镜像构建前应准备磁盘空间，不能通过删除业务卷解决。

本轮启动 Docker Desktop 后确认 Engine 29.9.2、Compose 5.6.1。首次 BuildKit 认证请求超时，使用 daemon 直接拉取项目声明的 Node、Java JDK/JRE 和 MySQL 镜像后成功构建；首次 Maven 镜像构建依赖下载超时，缓存重试后通过。未把个人代理或任何真实凭据写入工程，未删除运行数据卷。

## 已确认的条件

- Windows 11 家庭中文版，64位，build 22631。
- 约16GB内存；BIOS虚拟化和SLAT已开启。
- 配置前WSL、VirtualMachinePlatform均为Disabled，Windows hypervisor未运行。
- 已通过管理员流程启用WSL与VirtualMachinePlatform，两个组件都返回RestartNeeded=true；没有自动重启。
- C盘可用空间约7GB，因此Docker已安装到D:\DockerDesktop，安装参数指定WSL容器数据放D:\DockerData。

## 安装进度

管理员安装已完成：Docker Desktop 4.97.0、WSL 2.7.14.0，两个安装进程均返回0。Docker CLI 29.9.2、Compose v5.6.1版本查询成功。普通权限安装WSL曾返回0x80073d28，改用管理员安装后成功。

Docker安装包SHA-256为2668273F82B342A058A28F9AB53EA7BDDDE10BB6D51307CE2EC54C2D36459744，与winget官方来源元数据一致；Authenticode签名Valid，签发主体Docker Inc。下载中断点续传后再校验，未运行不完整安装包。

新建C:\Users\admin\.wslconfig（此前不存在），限制WSL内存为8GB、swap为2GB，swapFile=D:\\DockerData\\wsl-swap.vhdx，避免默认交换文件继续占用C盘。该配置会在WSL虚拟机启动时生效。

已实际执行：docker compose config --quiet和docker compose -f compose.test.yaml config --quiet，均退出0。docker version确认客户端可用，但引擎管道尚未建立。系统组件返回RestartNeeded=true，当前等待用户保存工作并重启Windows；没有自动重启。

尚未执行：依赖镜像构建、前端lint/typecheck/test/build、Java verify、Compose应用启动与任务冒烟。安装成功不等于这些检查通过。

操作日志放在被Git忽略的.cache目录。安装包、安装日志不进入源码交付。

## 重启后的验收顺序

启动Docker Desktop并等待Linux容器引擎就绪后，先执行docker version和docker compose version，再在项目根目录执行：

```powershell
docker compose config --quiet
docker compose -f compose.test.yaml config --quiet
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

如新进程PATH尚未生效，可使用D:\DockerDesktop\resources\bin\docker.exe完整路径。应用入口为http://localhost:3000。应用代码安装、编译、测试与执行保持在容器内。

出现编译、测试或运行错误时应修复对应问题并复验，再更新docs/acceptance.md；不能仅凭安装Docker成功就把项目标为验收通过。

## 参考

- [Docker官方Windows安装说明](https://docs.docker.com/desktop/setup/install/windows-install/)
- [Microsoft WSL安装说明与重启要求](https://learn.microsoft.com/en-us/windows/wsl/install)
