# 资源与复用来源

## 自有示例

backend/src/main/resources/demo 下6张封面 JPEG 与6张结果 PNG：本项目为笔试演示绘制的几何海报，720×1280，使用圆形、曲线、矩形和标题排版，无网络摄影、公司素材或付费素材依赖。结果标有 SIMULATED RESULT，不代表真实 AI 输出。

samples/uploads 下3张图片来自上述示例，分别覆盖 JPEG/PNG，均小于5MiB。可以直接交付并用于本项目演示；无需重新运行素材生成工具。

内容系列：coast（海岸）、mountain（山野）、light（光影）、garden（花园）、orbit（星际）、color（色彩）。

## 用户提供的参考

本机 docs/reference 下四张图片为用户随题提供的界面参考，用于布局核对并由 Git 忽略，不进入公开仓库/源码包；不裁剪人物/品牌作为模板素材。相关权利属于原权利人，不能据此推断可商业复用。应用运行不会读取这些截图。docs/screenshots仅包含本项目原创素材和演示身份的实际界面截图。

## 工程与依赖

- 本次手工组织 Next.js 与 Spring Boot 工程，没有从其他业务仓库复制实现，也未运行 create-next-app/Spring Initializr。
- Maven Wrapper：Apache Maven Wrapper 3.3.4 官方 only-script 发行包，复制 mvnw/mvnw.cmd，保留脚本许可证头；来源 https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-only-script.zip 。
- Wrapper 使用 Apache-2.0，许可证正文见 [Apache-2.0](licenses/Apache-2.0.txt)。这份第三方许可证不自动为本项目所有原创代码授予同样许可。
- Next.js、React、TypeScript、ESLint、Vitest、Testing Library、Spring Boot、MyBatis、Flyway、MySQL 等仅作为依赖使用；具体版本见 README、package-lock.json 和 pom.xml，遵循各项目发行许可证。
- 应用采用系统字体，不请求远程字体服务。图形标题为静态栅格文本，不打包字体文件。
- 阶段四可选浏览器验收使用 Playwright 1.64.1（Apache-2.0）和官方 `mcr.microsoft.com/playwright:v1.64.1-noble` 镜像，独立于应用依赖；来源与容器说明：https://playwright.dev/docs/docker 。验收脚本为本项目编写，仅访问本地应用和原创样本。
