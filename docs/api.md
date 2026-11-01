# API 契约 v0.4

## 当前可用范围

已实现分类、模板、示例资源、演示会话、素材上传、准备检查与异步任务接口。请求经前端同源 /api 转发到 Java。成功对象/数组直接返回，错误统一为：

```json
{
  "code": "NOT_FOUND",
  "message": "模板不存在",
  "fieldErrors": {},
  "requestId": "server-generated-uuid"
}
```

资源不存在返回 404；未预期异常返回 500 以及可理解文案，完整堆栈仅写服务器日志。所有 /api 响应 no-store。

### GET /api/categories

200：[{id, name, sortOrder}]。当前 travel / daily / creative 三个分类。

### GET /api/templates

可选 categoryId；空值相当于全部；未知分类返回 []。按 sortOrder、id 排序，当前规模不分页。

### GET /api/templates/{id}

200 模板对象；404 NOT_FOUND。列表与详情使用同一字段结构：

```json
{
  "id": "coastal-postcard",
  "categoryId": "travel",
  "title": "海岸来信",
  "description": "用两张照片留住海边的光与风，模拟生成旅行明信片。",
  "coverUrl": "/api/demo-assets/coast-cover.jpg",
  "sampleUrl": "/api/demo-assets/coast-result.png",
  "requiredImageCount": 2,
  "acceptedMimeTypes": ["image/jpeg", "image/png"],
  "maxFileSizeBytes": 5242880
}
```

### GET /api/demo-assets/{name}

仅文件名、匹配 [a-z0-9-]+ 的 JPEG/PNG，并且必须由数据库种子引用。classpath demo 根目录内读取，返回 image/jpeg 或 image/png；不接受用户提供的磁盘路径。不存在返回 404。此公开资源接口仅供种子示例，不能复用为未来个人素材权限入口。

### GET /actuator/health/readiness

Java 内部 200/503 就绪信号，包含数据库连接。前端 /api rewrite 不暴露 /actuator。

## 阶段二会话与素材接口

修改接口统一要求 `X-Studio-Request: 1`；未提供返回403 REQUEST_HEADER_REQUIRED。应用不允许跨域CORS，以自定义头配合SameSite Cookie阻止跨站表单操作。

- `GET /api/demo/users`：返回 `[{id,displayName}]`，DEMO_MODE=false时404。
- `POST /api/session`：JSON `{userId}`，返回200 `{id,displayName}`，设置studio_session Cookie（HttpOnly、SameSite=Lax、Path=/、默认7天）；撤销请求携带的旧会话。未知用户404，缺失参数400。DEMO_MODE=false时404。
- `GET /api/session`：返回当前用户；无效/过期Cookie返回401 SESSION_REQUIRED。
- `POST /api/assets`：multipart字段templateId、file，另需`X-Studio-User: 当前用户ID`。服务端根据Cookie确定所有者，额外核对该请求头；不一致返回409 SESSION_CHANGED。返回201 `{id,previewUrl,mimeType,sizeBytes,width,height}`，不返回内部路径。
- `GET /api/assets/{id}`：返回当前身份素材的同一元数据对象；非本人/不存在404，未登录401。
- `GET /api/assets/{id}/content`：根据Cookie校验归属，返回真实JPEG/PNG内容，no-store、nosniff；非本人/文件缺失404，未登录401。
- `POST /api/assets/validate`：JSON `{templateId,assetIds}`，同样需Cookie和X-Studio-User。成功200 `{ready:true,assetCount:2}`。严格校验模板要求数量、ID唯一性、归属、模板绑定、类型/大小和文件可读性。此接口不创建任务、不锁定素材，阶段三受理仍需重复校验。

真实文件校验：JPEG/PNG声明必须与解码器识别内容一致；5MiB单文件上限，6MiB multipart总上限；边长≤8192，总像素≤20,000,000。限制检查先于像素解码。字段丢失/JSON错误400；空图片400 EMPTY_FILE；内容无法解码400 INVALID_IMAGE或415 UNSUPPORTED_IMAGE；过大413 FILE_TOO_LARGE；像素超限400 IMAGE_DIMENSIONS。准备检查不足/过量400 IMAGE_COUNT；重复ID400 DUPLICATE_ASSET；其他模板素材400 ASSET_MISMATCH。

上传文件名仅作元数据保存，不参与存储路径。替换/移除没有删除端点；服务端已有素材保留。不提供任意磁盘文件访问。关闭演示入口不撤销已签发会话，会话继续按有效期工作。

## 任务接口（阶段三实现并通过运行验证，阶段四页面已接入）

| 方法 / 路径 | 输入 | 响应约定 |
| --- | --- | --- |
| POST /api/tasks | templateId、assetIds、simulation 可选 | 202，task 快照；不等待制作 |
| GET /api/tasks/{id} | Cookie | 服务端任务快照 |
| GET /api/tasks | cursor、limit 默认20/最大50 | items、nextCursor |
| GET /api/tasks/active | Cookie | 全部非终态任务 |
| GET /api/tasks/{id}/result | Cookie | 成功任务结果图片 |

POST任务沿用Cookie、X-Studio-Request: 1、X-Studio-User三项约束。后台重查所有素材，拒绝跨身份、模板不匹配、重复ID、错误数量或不存在文件；失败不会创建任务。创建事务同时写任务/关联/步骤，提交后才返回202。返回体就是任务快照，不另包task字段；Location为/api/tasks/{id}。每次成功受理初始快照为QUEUED/PENDING、version=0、三个PENDING步骤；后台可能在客户端收到响应时已经开始。

任务快照：id、templateId、templateTitle、status、currentStage、steps、resultUrl、failureStage、errorCode、errorMessage、createdAt、startedAt、finishedAt、version。无值字段返回null；时间统一ISO 8601 UTC。steps中含stage/status/startedAt/finishedAt/errorCode/errorMessage。不会暴露userId、simulationJson或磁盘路径。

提交示例（assetIds替换为当前身份上传响应的ID）：

```json
{
  "templateId": "afternoon-light",
  "assetIds": ["实际上传素材ID"],
  "simulation": {
    "failStage": "GENERATE",
    "delaysMs": { "ASSET_CHECK": 1500, "GENERATE": 15000, "FINALIZE": 1500 }
  }
}
```

省略simulation使用后端默认；省略其部分字段则补齐默认值并冻结到该任务。failStage=NONE表示成功路径。明确拒绝未知字段、未知阶段、负数、超限值、字符串数字与小数；SIMULATION_ENABLED=false时拒绝任何非null覆盖对象。错误码为400 INVALID_SIMULATION。

历史返回`{items:[任务快照],nextCursor:字符串或null}`。默认limit=20，允许1～50；cursor为服务端返回的Base64URL不透明游标，原样传回，不自行构造。按createdAt与id共同倒序定位，无下一页时null。非法游标/范围返回400 INVALID_PAGINATION；非数字limit返回400 INVALID_REQUEST。active返回当前身份所有QUEUED/RUNNING快照数组，不受历史分页影响。所有查询使用只读一致性事务，状态与steps来自同一个数据库快照。

成功结果返回image/png及no-store/nosniff，未成功409 RESULT_NOT_READY；他人任务和结果404；成功后文件丢失404但任务仍保持SUCCEEDED。未登录401。未完成启动、停止受理或工作线程数据库故障时POST返回503 WORKER_UNAVAILABLE，GET仍读取可访问的服务端记录。

simulation：failStage=NONE/ASSET_CHECK/GENERATE/FINALIZE，delaysMs 包含 ASSET_CHECK/GENERATE/FINALIZE 的整数毫秒（各0～30000），后端解析完整配置并冻结到任务。

状态约束：

- QUEUED/PENDING → RUNNING/PENDING（领取完成）→ RUNNING/ASSET_CHECK → RUNNING/GENERATE → RUNNING/FINALIZE → SUCCEEDED/FINALIZE。
- 任一步失败则任务 FAILED，保留 failureStage、原因，后续步骤 SKIPPED。
- task_step 为 PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED。
- 失败/成功均为终态；查询不能推进任务或回退终态。
- 网络查询异常属于客户端 queryError，不能写成后台 FAILED。
- 身份来自服务端 Cookie；个人查询同时按 userId 与资源 ID 查找；他人资源返回404。
- SIMULATED_FAILURE是指定阶段失败；文件问题为ASSET_UNAVAILABLE/RESULT_IO_ERROR等；WORKER_INTERRUPTED为正常停止中断；SERVICE_RESTARTED为启动收敛旧任务。failureStage保留实际已持久化阶段，未开始任务可能为PENDING。
- 详情页及 /history 已接入任务提交/历史/active/结果接口。前端每任务2秒查询间隔，15秒超时，终态停查；查询异常保留已有状态并暂停该任务查询，提供恢复按钮，不能等同后台 FAILED。
- 页面历史每页12条，合并active及已显示记录，以id去重并拒绝较旧version。受理不确定时不自动重发，先提示核对历史。新增页面没有修改Java请求/响应契约。
